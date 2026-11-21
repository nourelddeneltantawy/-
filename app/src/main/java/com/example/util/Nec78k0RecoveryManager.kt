package com.example.util

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Crash-Safe & Atomic Checkpoint Manager for NEC / Renesas 78K0 Extraction.
 * Persists critical metadata (full-flash & block-0 checksums, signature, progress) and
 * partial_blocks.bin with FileOutputStream.fd.sync() so an interrupted extraction can
 * always rescue the MCU via One-Tap Rescue without data loss.
 */
object Nec78k0RecoveryManager {
    private const val RECOVERY_DIR_NAME = "78k0_recovery"

    enum class RecoveryStage(val wireName: String, val labelAr: String) {
        DETECTED("DETECTED", "تم حفظ بصمة المعالج و Checksum قبل التعديل"),
        BLOCK0_ZEROED("BLOCK0_ZEROED", "تم تصفير Block 0 وجاهز لسحب الكتل المتبقية"),
        READING_BLOCKS("READING_BLOCKS", "جاري سحب كتل الذاكرة (متوقف مؤقتاً)"),
        BLOCKS_EXTRACTED("BLOCKS_EXTRACTED", "تم سحب Block 1..N وجاهز لاسترجاع Block 0"),
        COMPLETED("COMPLETED", "مكتمل");

        companion object {
            fun fromWireName(value: String): RecoveryStage {
                return entries.firstOrNull { it.wireName == value } ?: DETECTED
            }
        }
    }

    data class Nec78k0RecoverySession(
        val chipName: String,
        val subFamily: String,
        val lastAddress: Int,
        val blockCount: Int,
        val hasDataFlash: Boolean,
        val fullFlashChecksum: Int,
        val block0Checksum: Int,
        val signatureHex: String,
        val stage: RecoveryStage,
        val lastCompletedBlock: Int,
        val updatedAtMs: Long,
        val partialBlocksPath: String
    ) {
        val flashSizeBytes: Int
            get() = lastAddress + 1

        val fullChecksumHex: String
            get() = "%04X".format(fullFlashChecksum and 0xFFFF)

        val block0ChecksumHex: String
            get() = "%04X".format(block0Checksum and 0xFFFF)
    }

    private fun getRecoveryDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, RECOVERY_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeName(chipName: String): String {
        val cleaned = chipName.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "_")
        return cleaned.ifBlank { "UNKNOWN_78K0" }
    }

    fun sessionFile(context: Context, chipName: String): File {
        return File(getRecoveryDir(context), "recovery_78k0_${sanitizeName(chipName)}.json")
    }

    fun partialBlocksFile(context: Context, chipName: String): File {
        return File(getRecoveryDir(context), "partial_blocks_${sanitizeName(chipName)}.bin")
    }

    fun saveSessionCheckpoint(context: Context, session: Nec78k0RecoverySession): File {
        val target = sessionFile(context, session.chipName)
        val json = JSONObject().apply {
            put("chipName", session.chipName)
            put("subFamily", session.subFamily)
            put("lastAddress", session.lastAddress)
            put("blockCount", session.blockCount)
            put("hasDataFlash", session.hasDataFlash)
            put("fullFlashChecksum", session.fullFlashChecksum and 0xFFFF)
            put("block0Checksum", session.block0Checksum and 0xFFFF)
            put("signatureHex", session.signatureHex)
            put("stage", session.stage.wireName)
            put("lastCompletedBlock", session.lastCompletedBlock)
            put("updatedAtMs", session.updatedAtMs)
            put("partialBlocksPath", session.partialBlocksPath)
        }
        writeBytesAtomically(target, json.toString(2).toByteArray(Charsets.UTF_8))
        return target
    }

    fun savePartialBlocksAtomically(context: Context, chipName: String, buffer: ByteArray): File {
        val target = partialBlocksFile(context, chipName)
        writeBytesAtomically(target, buffer)
        return target
    }

    fun loadPartialBlocks(context: Context, chipName: String, expectedSize: Int): ByteArray? {
        val file = partialBlocksFile(context, chipName)
        if (!file.exists()) return null
        return try {
            val raw = file.readBytes()
            if (raw.size == expectedSize) {
                raw
            } else {
                ByteArray(expectedSize) { 0xFF.toByte() }.also { resized ->
                    raw.copyInto(resized, destinationOffset = 0, startIndex = 0, endIndex = minOf(raw.size, expectedSize))
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun findActiveRescueSessions(context: Context): List<Nec78k0RecoverySession> {
        val dir = getRecoveryDir(context)
        val files = dir.listFiles { _, name ->
            name.startsWith("recovery_78k0_") && name.endsWith(".json")
        } ?: return emptyList()

        return files.mapNotNull { parseSessionFile(it) }
            .filter { it.stage != RecoveryStage.COMPLETED && it.lastCompletedBlock >= 0 }
            .sortedByDescending { it.updatedAtMs }
    }

    fun findSessionForChip(context: Context, chipName: String): Nec78k0RecoverySession? {
        val direct = parseSessionFile(sessionFile(context, chipName))
        if (direct != null && direct.stage != RecoveryStage.COMPLETED) return direct

        val normTarget = normalizeChipKey(chipName)
        return findActiveRescueSessions(context).firstOrNull {
            normalizeChipKey(it.chipName) == normTarget
        }
    }

    fun markCompletedAndCleanup(context: Context, chipName: String) {
        val target = sessionFile(context, chipName)
        val partial = partialBlocksFile(context, chipName)
        try {
            if (target.exists()) target.delete()
        } catch (_: Throwable) {}
        try {
            if (partial.exists()) partial.delete()
        } catch (_: Throwable) {}
    }

    private fun parseSessionFile(file: File): Nec78k0RecoverySession? {
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            Nec78k0RecoverySession(
                chipName = obj.getString("chipName"),
                subFamily = obj.optString("subFamily", "78K0"),
                lastAddress = obj.getInt("lastAddress"),
                blockCount = obj.getInt("blockCount"),
                hasDataFlash = obj.optBoolean("hasDataFlash", false),
                fullFlashChecksum = obj.getInt("fullFlashChecksum"),
                block0Checksum = obj.getInt("block0Checksum"),
                signatureHex = obj.optString("signatureHex", ""),
                stage = RecoveryStage.fromWireName(obj.optString("stage", "DETECTED")),
                lastCompletedBlock = obj.optInt("lastCompletedBlock", -1),
                updatedAtMs = obj.optLong("updatedAtMs", file.lastModified()),
                partialBlocksPath = obj.optString("partialBlocksPath", "")
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeBytesAtomically(target: File, data: ByteArray) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tmpFile = File(parent, "${target.name}.tmp")
        FileOutputStream(tmpFile).use { fos ->
            fos.write(data)
            fos.flush()
            fos.fd.sync()
        }
        if (!tmpFile.renameTo(target)) {
            if (target.exists()) {
                target.delete()
            }
            if (!tmpFile.renameTo(target)) {
                tmpFile.copyTo(target, overwrite = true)
                tmpFile.delete()
            }
        }
    }

    private fun normalizeChipKey(name: String): String {
        return name.trim().uppercase()
            .replace("UPD", "D")
            .replace(Regex("[^A-Z0-9]"), "")
    }
}
