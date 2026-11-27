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
    private const val CANONICAL_RESCUE_JSON = "78k0_rescue.json"
    private const val CANONICAL_PARTIAL_BIN = "78k0_partial.bin"

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
        val partialBlocksPath: String,
        val block0Touched: Boolean = false,
        val block0OriginalExtracted: Boolean = false,
        val block0DiffNextIndex: Int = 0,
        val block0DiffPrevCs: Int = -1,
        val block0Restored: Boolean = false,
        val activeDiffBlockIndex: Int = -1,
        val activeDiffByteIndex: Int = 0,
        val activeDiffBlockOrigCs: Int = -1
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

    fun sessionFile(context: Context, @Suppress("UNUSED_PARAMETER") chipName: String): File {
        return File(getRecoveryDir(context), CANONICAL_RESCUE_JSON)
    }

    fun partialBlocksFile(context: Context, @Suppress("UNUSED_PARAMETER") chipName: String): File {
        return File(getRecoveryDir(context), CANONICAL_PARTIAL_BIN)
    }

    private fun legacySessionFile(context: Context, chipName: String): File {
        return File(getRecoveryDir(context), "recovery_78k0_${sanitizeName(chipName)}.json")
    }

    private fun legacyPartialBlocksFile(context: Context, chipName: String): File {
        return File(getRecoveryDir(context), "partial_blocks_${sanitizeName(chipName)}.bin")
    }

    fun saveSessionCheckpoint(context: Context, session: Nec78k0RecoverySession): File {
        val target = sessionFile(context, session.chipName)
        val canonicalPartial = partialBlocksFile(context, session.chipName).absolutePath
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
            put("partialBlocksPath", canonicalPartial)
            put("block0Touched", session.block0Touched || session.stage != RecoveryStage.DETECTED || session.lastCompletedBlock >= 0)
            put("block0OriginalExtracted", session.block0OriginalExtracted)
            put("block0DiffNextIndex", session.block0DiffNextIndex)
            put("block0DiffPrevCs", session.block0DiffPrevCs)
            put("block0Restored", session.block0Restored)
            put("activeDiffBlockIndex", session.activeDiffBlockIndex)
            put("activeDiffByteIndex", session.activeDiffByteIndex)
            put("activeDiffBlockOrigCs", session.activeDiffBlockOrigCs)
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
        val canonical = partialBlocksFile(context, chipName)
        val file = when {
            canonical.exists() -> canonical
            legacyPartialBlocksFile(context, chipName).exists() -> legacyPartialBlocksFile(context, chipName)
            else -> return null
        }
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
        return loadAllUncompletedSessions(context)
            .filter { it.lastCompletedBlock >= 0 || it.block0Touched || it.stage != RecoveryStage.DETECTED }
            .distinctBy { normalizeChipKey(it.chipName) }
            .sortedByDescending { it.updatedAtMs }
    }

    fun findAnyPendingSession(context: Context, preferredChipName: String? = null): Nec78k0RecoverySession? {
        val all = loadAllUncompletedSessions(context).sortedByDescending { it.updatedAtMs }
        if (!preferredChipName.isNullOrBlank()) {
            all.firstOrNull { chipsMatch(it.chipName, preferredChipName) }?.let { return it }
        }
        return all.firstOrNull { it.lastCompletedBlock >= 0 || it.block0Touched || it.stage != RecoveryStage.DETECTED }
            ?: all.firstOrNull()
    }

    private fun loadAllUncompletedSessions(context: Context): List<Nec78k0RecoverySession> {
        val dir = getRecoveryDir(context)
        val files = dir.listFiles { _, name ->
            name == CANONICAL_RESCUE_JSON || (name.startsWith("recovery_78k0_") && name.endsWith(".json"))
        } ?: return emptyList()

        return files.mapNotNull { parseSessionFile(it) }
            .filter { it.stage != RecoveryStage.COMPLETED }
    }

    fun findSessionForChip(context: Context, chipName: String): Nec78k0RecoverySession? {
        val canonical = parseSessionFile(sessionFile(context, chipName))
        if (canonical != null && canonical.stage != RecoveryStage.COMPLETED &&
            chipsMatch(canonical.chipName, chipName)
        ) {
            return canonical
        }

        return loadAllUncompletedSessions(context)
            .sortedByDescending { it.updatedAtMs }
            .firstOrNull { chipsMatch(it.chipName, chipName) }
    }

    fun resetBlockOffsetForCleanResume(
        context: Context,
        session: Nec78k0RecoverySession
    ): Nec78k0RecoverySession {
        val resetStage = if (session.lastCompletedBlock >= 0) {
            RecoveryStage.READING_BLOCKS
        } else {
            RecoveryStage.DETECTED
        }
        val updated = session.copy(
            stage = resetStage,
            updatedAtMs = System.currentTimeMillis(),
            block0OriginalExtracted = if (session.lastCompletedBlock >= 0) session.block0OriginalExtracted else false,
            block0DiffNextIndex = 0,
            block0DiffPrevCs = -1,
            activeDiffBlockIndex = -1,
            activeDiffByteIndex = 0,
            activeDiffBlockOrigCs = -1
        )
        saveSessionCheckpoint(context, updated)
        return updated
    }

    fun markCompletedAndCleanup(context: Context, chipName: String) {
        listOf(
            sessionFile(context, chipName),
            partialBlocksFile(context, chipName),
            legacySessionFile(context, chipName),
            legacyPartialBlocksFile(context, chipName)
        ).forEach { f ->
            try {
                if (f.exists()) f.delete()
            } catch (_: Throwable) {}
        }
    }

    private fun parseSessionFile(file: File): Nec78k0RecoverySession? {
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val stage = RecoveryStage.fromWireName(obj.optString("stage", "DETECTED"))
            val lastDone = obj.optInt("lastCompletedBlock", -1)
                val legacyB0Next = obj.optInt("block0DiffNextIndex", 0)
                val activeBlk = obj.optInt("activeDiffBlockIndex", if (legacyB0Next > 0) 0 else -1)
                val activeByte = obj.optInt("activeDiffByteIndex", if (activeBlk == 0) legacyB0Next else 0)
                Nec78k0RecoverySession(
                    chipName = obj.getString("chipName"),
                    subFamily = obj.optString("subFamily", "78K0"),
                    lastAddress = obj.getInt("lastAddress"),
                    blockCount = obj.getInt("blockCount"),
                    hasDataFlash = obj.optBoolean("hasDataFlash", false),
                    fullFlashChecksum = obj.getInt("fullFlashChecksum"),
                    block0Checksum = obj.getInt("block0Checksum"),
                    signatureHex = obj.optString("signatureHex", ""),
                    stage = stage,
                    lastCompletedBlock = lastDone,
                    updatedAtMs = obj.optLong("updatedAtMs", file.lastModified()),
                    partialBlocksPath = obj.optString("partialBlocksPath", ""),
                    block0Touched = obj.optBoolean("block0Touched", stage != RecoveryStage.DETECTED || lastDone >= 0),
                    block0OriginalExtracted = obj.optBoolean("block0OriginalExtracted", false),
                    block0DiffNextIndex = legacyB0Next,
                    block0DiffPrevCs = obj.optInt("block0DiffPrevCs", -1),
                    block0Restored = obj.optBoolean("block0Restored", false),
                    activeDiffBlockIndex = activeBlk,
                    activeDiffByteIndex = activeByte,
                    activeDiffBlockOrigCs = obj.optInt("activeDiffBlockOrigCs", -1)
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

    private fun chipsMatch(a: String, b: String): Boolean {
        val na = normalizeChipKey(a)
        val nb = normalizeChipKey(b)
        if (na == nb) return true
        val ta = na.trimEnd('A', 'B', 'C', 'D')
        val tb = nb.trimEnd('A', 'B', 'C', 'D')
        if (ta.isNotEmpty() && ta == tb) return true
        val coreRegex = Regex("""78F[0-9]{4}""")
        val ca = coreRegex.find(na)?.value
        val cb = coreRegex.find(nb)?.value
        return ca != null && ca == cb
    }

    private fun normalizeChipKey(name: String): String {
        return name.trim().uppercase()
            .replace("UPD", "D")
            .replace(Regex("[^A-Z0-9]"), "")
    }
}
