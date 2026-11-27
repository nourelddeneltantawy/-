package com.example.util

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Universal Hot-Plug Checkpoint & Safe Resume Manager for ALL MCU families (R8C, RL78, 78K0, generic UART)
 * across both Read and Write operations. Writes partial binary + JSON metadata atomically via fd.sync().
 */
object UniversalCheckpointManager {
    private const val DIR_NAME = "mcu_checkpoints"

    enum class OperationKind {
        READ,
        WRITE
    }

    data class UniversalCheckpoint(
        val partNumber: String,
        val mainFamily: String,
        val protocolTypeName: String,
        val operation: OperationKind,
        val completedBytes: Int,
        val totalBytes: Int,
        val blockOrPageIndex: Int,
        val totalBlocksOrPages: Int,
        val fileName: String,
        val baudRate: Int,
        val flashBaseAddress: Int,
        val updatedAtMs: Long
    ) {
        val progressFraction: Float
            get() = if (totalBytes > 0) (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

        val progressPercent: Int
            get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
    }

    private fun dirFor(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun safeKey(partNumber: String): String =
        partNumber.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun metaFile(context: Context, partNumber: String): File =
        File(dirFor(context), "${safeKey(partNumber)}.json")

    private fun binFile(context: Context, partNumber: String): File =
        File(dirFor(context), "${safeKey(partNumber)}.bin")

    fun saveCheckpoint(
        context: Context,
        checkpoint: UniversalCheckpoint,
        bufferBytes: ByteArray?
    ) {
        try {
            val dir = dirFor(context)
            val key = safeKey(checkpoint.partNumber)

            if (bufferBytes != null && bufferBytes.isNotEmpty()) {
                val tmpBin = File(dir, "$key.bin.tmp")
                FileOutputStream(tmpBin).use { fos ->
                    fos.write(bufferBytes)
                    fos.flush()
                    fos.fd.sync()
                }
                val dstBin = binFile(context, checkpoint.partNumber)
                if (dstBin.exists()) dstBin.delete()
                tmpBin.renameTo(dstBin)
            }

            val json = JSONObject().apply {
                put("partNumber", checkpoint.partNumber)
                put("mainFamily", checkpoint.mainFamily)
                put("protocolTypeName", checkpoint.protocolTypeName)
                put("operation", checkpoint.operation.name)
                put("completedBytes", checkpoint.completedBytes)
                put("totalBytes", checkpoint.totalBytes)
                put("blockOrPageIndex", checkpoint.blockOrPageIndex)
                put("totalBlocksOrPages", checkpoint.totalBlocksOrPages)
                put("fileName", checkpoint.fileName)
                put("baudRate", checkpoint.baudRate)
                put("flashBaseAddress", checkpoint.flashBaseAddress)
                put("updatedAtMs", checkpoint.updatedAtMs)
            }

            val tmpMeta = File(dir, "$key.json.tmp")
            FileOutputStream(tmpMeta).use { fos ->
                fos.write(json.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            val dstMeta = metaFile(context, checkpoint.partNumber)
            if (dstMeta.exists()) dstMeta.delete()
            tmpMeta.renameTo(dstMeta)
        } catch (_: Throwable) {}
    }

    fun loadCheckpoint(context: Context, partNumber: String): UniversalCheckpoint? {
        return try {
            val file = metaFile(context, partNumber)
            if (!file.exists()) return null
            parseMeta(file)
        } catch (_: Throwable) {
            null
        }
    }

    fun findLatestCheckpoint(context: Context, preferredPartNumber: String? = null): UniversalCheckpoint? {
        if (!preferredPartNumber.isNullOrBlank()) {
            loadCheckpoint(context, preferredPartNumber)?.let { return it }
        }
        return listCheckpoints(context).firstOrNull()
    }

    fun listCheckpoints(context: Context): List<UniversalCheckpoint> {
        return try {
            val files = dirFor(context).listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
            files.mapNotNull { parseMeta(it) }
                .filter { it.completedBytes > 0 && it.totalBytes > 0 && it.completedBytes < it.totalBytes }
                .sortedByDescending { it.updatedAtMs }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun loadBuffer(context: Context, partNumber: String, expectedSize: Int): ByteArray? {
        return try {
            val f = binFile(context, partNumber)
            if (!f.exists()) return null
            val raw = f.readBytes()
            if (raw.size == expectedSize) raw
            else ByteArray(expectedSize) { 0xFF.toByte() }.also { out ->
                raw.copyInto(out, 0, 0, minOf(raw.size, expectedSize))
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun clearCheckpoint(context: Context, partNumber: String) {
        try {
            metaFile(context, partNumber).delete()
            binFile(context, partNumber).delete()
        } catch (_: Throwable) {}
    }

    private fun parseMeta(file: File): UniversalCheckpoint? {
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val opStr = obj.optString("operation", OperationKind.READ.name)
            val op = try {
                OperationKind.valueOf(opStr)
            } catch (_: Throwable) {
                OperationKind.READ
            }
            UniversalCheckpoint(
                partNumber = obj.optString("partNumber", ""),
                mainFamily = obj.optString("mainFamily", ""),
                protocolTypeName = obj.optString("protocolTypeName", ""),
                operation = op,
                completedBytes = obj.optInt("completedBytes", 0),
                totalBytes = obj.optInt("totalBytes", 0),
                blockOrPageIndex = obj.optInt("blockOrPageIndex", 0),
                totalBlocksOrPages = obj.optInt("totalBlocksOrPages", 0),
                fileName = obj.optString("fileName", ""),
                baudRate = obj.optInt("baudRate", 9600),
                flashBaseAddress = obj.optInt("flashBaseAddress", 0),
                updatedAtMs = obj.optLong("updatedAtMs", 0L)
            ).takeIf { it.partNumber.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
