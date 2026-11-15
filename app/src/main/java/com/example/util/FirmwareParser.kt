package com.example.util

import com.example.data.model.FirmwareFileData
import kotlin.math.min

object FirmwareParser {

    fun createEmptyBuffer(sizeBytes: Int): ByteArray {
        return ByteArray(sizeBytes.coerceAtLeast(1024)) { 0xFF.toByte() }
    }

    fun parseFirmware(
        rawBytes: ByteArray,
        fileName: String,
        targetFlashSizeBytes: Int
    ): FirmwareFileData {
        val lower = fileName.lowercase()
        val textPreview = try {
            rawBytes.take(256).toByteArray().toString(Charsets.US_ASCII).trimStart()
        } catch (_: Throwable) {
            ""
        }

        return when {
            lower.endsWith(".hex") || textPreview.startsWith(":") -> {
                parseIntelHex(rawBytes.toString(Charsets.US_ASCII), fileName, targetFlashSizeBytes)
            }
            lower.endsWith(".mot") || lower.endsWith(".srec") || lower.endsWith(".s19") ||
                lower.endsWith(".s28") || lower.endsWith(".s37") || textPreview.startsWith("S0") ||
                textPreview.startsWith("S1") || textPreview.startsWith("S2") || textPreview.startsWith("S3") -> {
                parseMotorolaSRecord(rawBytes.toString(Charsets.US_ASCII), fileName, targetFlashSizeBytes)
            }
            else -> {
                parseRawBinary(rawBytes, fileName, targetFlashSizeBytes)
            }
        }
    }

    private fun parseRawBinary(
        rawBytes: ByteArray,
        fileName: String,
        targetFlashSizeBytes: Int
    ): FirmwareFileData {
        val buffer = createEmptyBuffer(targetFlashSizeBytes)
        val count = min(rawBytes.size, buffer.size)
        System.arraycopy(rawBytes, 0, buffer, 0, count)
        return FirmwareFileData(
            fileName = fileName,
            format = "BIN",
            bytes = buffer,
            nonEmptyBytesCount = count,
            startAddress = 0
        )
    }

    private data class AddressChunk(val address: Long, val data: ByteArray)

    private fun parseIntelHex(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int
    ): FirmwareFileData {
        val chunks = mutableListOf<AddressChunk>()
        var extendedSegmentAddress = 0L
        var extendedLinearAddress = 0L

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith(":") || line.length < 11) return@forEach

            val byteCount = line.substring(1, 3).toIntOrNull(16) ?: return@forEach
            val address = line.substring(3, 7).toIntOrNull(16) ?: return@forEach
            val recordType = line.substring(7, 9).toIntOrNull(16) ?: return@forEach

            val expectedMinLen = 11 + byteCount * 2
            if (line.length < expectedMinLen) return@forEach

            when (recordType) {
                0x00 -> {
                    val data = ByteArray(byteCount)
                    for (i in 0 until byteCount) {
                        val idx = 9 + i * 2
                        data[i] = (line.substring(idx, idx + 2).toIntOrNull(16) ?: 0xFF).toByte()
                    }
                    val fullAddr = extendedLinearAddress + extendedSegmentAddress + address.toLong()
                    chunks.add(AddressChunk(fullAddr, data))
                }
                0x01 -> return@forEach
                0x02 -> {
                    if (byteCount == 2) {
                        val seg = line.substring(9, 13).toIntOrNull(16) ?: 0
                        extendedSegmentAddress = seg.toLong() shl 4
                        extendedLinearAddress = 0L
                    }
                }
                0x04 -> {
                    if (byteCount == 2) {
                        val upper = line.substring(9, 13).toIntOrNull(16) ?: 0
                        extendedLinearAddress = upper.toLong() shl 16
                        extendedSegmentAddress = 0L
                    }
                }
            }
        }

        return materializeChunks(chunks, fileName, "Intel HEX", targetFlashSizeBytes)
    }

    private fun parseMotorolaSRecord(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int
    ): FirmwareFileData {
        val chunks = mutableListOf<AddressChunk>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("S") || line.length < 10) return@forEach
            val type = line[1]
            val byteCount = line.substring(2, 4).toIntOrNull(16) ?: return@forEach
            val addrBytesCount = when (type) {
                '1' -> 2
                '2' -> 3
                '3' -> 4
                else -> return@forEach
            }
            val addrHexChars = addrBytesCount * 2
            val dataBytesCount = byteCount - addrBytesCount - 1
            if (dataBytesCount <= 0 || line.length < 4 + addrHexChars + dataBytesCount * 2) return@forEach

            val address = line.substring(4, 4 + addrHexChars).toLongOrNull(16) ?: return@forEach
            val dataStart = 4 + addrHexChars
            val data = ByteArray(dataBytesCount)
            for (i in 0 until dataBytesCount) {
                val idx = dataStart + i * 2
                data[i] = (line.substring(idx, idx + 2).toIntOrNull(16) ?: 0xFF).toByte()
            }
            chunks.add(AddressChunk(address, data))
        }

        return materializeChunks(chunks, fileName, "Motorola S-Record", targetFlashSizeBytes)
    }

    private fun materializeChunks(
        chunks: List<AddressChunk>,
        fileName: String,
        format: String,
        targetFlashSizeBytes: Int
    ): FirmwareFileData {
        val buffer = createEmptyBuffer(targetFlashSizeBytes)
        if (chunks.isEmpty()) {
            return FirmwareFileData(fileName, format, buffer, 0, 0)
        }

        val minAddr = chunks.minOf { it.address }
        val maxAddr = chunks.maxOf { it.address + it.data.size }
        // Many MCUs (e.g., R8C / M16C / RX) place flash at high physical addresses (0x04000, 0xE0000, 0xFFF80000).
        // If maxAddr exceeds targetFlashSizeBytes, rebase relative to minAddr (aligned to 16 bytes) so it fits cleanly.
        val baseOffset = if (maxAddr <= targetFlashSizeBytes.toLong()) 0L else (minAddr and 0xFFFFFFF0L)
        var writtenBytes = 0

        for (chunk in chunks) {
            val relAddr = (chunk.address - baseOffset).toInt()
            if (relAddr >= buffer.size) continue
            for (i in chunk.data.indices) {
                val dst = relAddr + i
                if (dst in buffer.indices) {
                    buffer[dst] = chunk.data[i]
                    writtenBytes++
                }
            }
        }

        return FirmwareFileData(
            fileName = fileName,
            format = format,
            bytes = buffer,
            nonEmptyBytesCount = writtenBytes,
            startAddress = baseOffset.toInt()
        )
    }

    fun toIntelHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        var currentUpper = -1
        var addr = 0
        while (addr < bytes.size) {
            val upper = (addr ushr 16) and 0xFFFF
            if (upper != currentUpper) {
                currentUpper = upper
                val hi = (upper ushr 8) and 0xFF
                val lo = upper and 0xFF
                val sum = (2 + 0 + 0 + 4 + hi + lo) and 0xFF
                val chk = ((0x100 - sum) and 0xFF)
                sb.append(":02000004%04X%02X\r\n".format(upper, chk))
            }
            val len = min(16, bytes.size - addr)
            val slice = bytes.copyOfRange(addr, addr + len)
            // Skip completely 0xFF records to keep exported HEX compact and standard
            if (slice.any { it != 0xFF.toByte() }) {
                val lowAddr = addr and 0xFFFF
                var sum = len + ((lowAddr ushr 8) and 0xFF) + (lowAddr and 0xFF)
                sb.append(":%02X%04X00".format(len, lowAddr))
                for (b in slice) {
                    val v = b.toInt() and 0xFF
                    sum += v
                    sb.append("%02X".format(v))
                }
                val chk = ((0x100 - (sum and 0xFF)) and 0xFF)
                sb.append("%02X\r\n".format(chk))
            }
            addr += len
        }
        sb.append(":00000001FF\r\n")
        return sb.toString()
    }
}
