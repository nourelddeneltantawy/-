package com.example.util

import com.example.data.model.ChipConfig
import com.example.data.model.FirmwareFileData
import java.util.Locale
import kotlin.math.min

object FirmwareParser {

    fun createEmptyBuffer(sizeBytes: Int): ByteArray {
        return ByteArray(sizeBytes.coerceAtLeast(1024)) { 0xFF.toByte() }
    }

    fun parseFirmware(
        rawBytes: ByteArray,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
    ): FirmwareFileData {
        val lower = fileName.lowercase()
        val textPreview = try {
            rawBytes.take(256).toByteArray().toString(Charsets.US_ASCII).trimStart()
        } catch (_: Throwable) {
            ""
        }

        return when {
            lower.endsWith(".hex") || textPreview.startsWith(":") -> {
                parseIntelHex(
                    rawBytes.toString(Charsets.US_ASCII),
                    fileName,
                    targetFlashSizeBytes,
                    preferredBaseAddress,
                    dataFlashStartAddress,
                    dataFlashSizeBytes
                )
            }
            lower.endsWith(".mot") || lower.endsWith(".srec") || lower.endsWith(".s19") ||
                lower.endsWith(".s28") || lower.endsWith(".s37") || textPreview.startsWith("S0") ||
                textPreview.startsWith("S1") || textPreview.startsWith("S2") || textPreview.startsWith("S3") -> {
                parseMotorolaSRecord(
                    rawBytes.toString(Charsets.US_ASCII),
                    fileName,
                    targetFlashSizeBytes,
                    preferredBaseAddress,
                    dataFlashStartAddress,
                    dataFlashSizeBytes
                )
            }
            else -> {
                parseRawBinary(
                    rawBytes,
                    fileName,
                    targetFlashSizeBytes,
                    preferredBaseAddress,
                    dataFlashStartAddress,
                    dataFlashSizeBytes
                )
            }
        }
    }

    private fun parseRawBinary(
        rawBytes: ByteArray,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
    ): FirmwareFileData {
        val buffer = createEmptyBuffer(targetFlashSizeBytes)
        var dfBuffer: ByteArray? = null
        var dfStart = 0
        val count: Int

        // Detect Full Physical Memory Map .bin (0x00000..programEndAddress, e.g. 81,920 bytes from R8CTool.EXE)
        if (preferredBaseAddress > 0 && rawBytes.size >= preferredBaseAddress + targetFlashSizeBytes) {
            val progAvailable = min(buffer.size, rawBytes.size - preferredBaseAddress)
            System.arraycopy(rawBytes, preferredBaseAddress, buffer, 0, progAvailable)
            count = buffer.count { it != 0xFF.toByte() }

            if (dataFlashStartAddress != null && dataFlashSizeBytes > 0 && rawBytes.size > dataFlashStartAddress) {
                val dfAvailable = min(dataFlashSizeBytes, rawBytes.size - dataFlashStartAddress)
                if (dfAvailable > 0) {
                    dfBuffer = ByteArray(dataFlashSizeBytes) { 0xFF.toByte() }
                    System.arraycopy(rawBytes, dataFlashStartAddress, dfBuffer, 0, dfAvailable)
                    dfStart = dataFlashStartAddress
                }
            }
        } else {
            count = min(rawBytes.size, buffer.size)
            System.arraycopy(rawBytes, 0, buffer, 0, count)

            if (dataFlashStartAddress != null && dataFlashSizeBytes > 0 && rawBytes.size > targetFlashSizeBytes) {
                val extraAvailable = min(dataFlashSizeBytes, rawBytes.size - targetFlashSizeBytes)
                if (extraAvailable > 0) {
                    dfBuffer = ByteArray(dataFlashSizeBytes) { 0xFF.toByte() }
                    System.arraycopy(rawBytes, targetFlashSizeBytes, dfBuffer, 0, extraAvailable)
                    dfStart = dataFlashStartAddress
                }
            }
        }

        return FirmwareFileData(
            fileName = fileName,
            format = "BIN",
            bytes = buffer,
            nonEmptyBytesCount = count,
            startAddress = preferredBaseAddress,
            dataFlashBytes = dfBuffer,
            dataFlashStartAddress = dfStart
        )
    }

    private data class AddressChunk(val address: Long, val data: ByteArray)

    private fun parseIntelHex(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
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

        return materializeChunks(
            chunks = chunks,
            fileName = fileName,
            format = "Intel HEX",
            targetFlashSizeBytes = targetFlashSizeBytes,
            preferredBaseAddress = preferredBaseAddress,
            dataFlashStartAddress = dataFlashStartAddress,
            dataFlashSizeBytes = dataFlashSizeBytes
        )
    }

    private fun parseMotorolaSRecord(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
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

        return materializeChunks(
            chunks = chunks,
            fileName = fileName,
            format = "Motorola S-Record",
            targetFlashSizeBytes = targetFlashSizeBytes,
            preferredBaseAddress = preferredBaseAddress,
            dataFlashStartAddress = dataFlashStartAddress,
            dataFlashSizeBytes = dataFlashSizeBytes
        )
    }

    private fun materializeChunks(
        chunks: List<AddressChunk>,
        fileName: String,
        format: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
    ): FirmwareFileData {
        val buffer = createEmptyBuffer(targetFlashSizeBytes)
        if (chunks.isEmpty()) {
            return FirmwareFileData(fileName, format, buffer, 0, preferredBaseAddress)
        }

        val minAddr = chunks.minOf { it.address }
        val maxAddr = chunks.maxOf { it.address + it.data.size }
        val prefBase = preferredBaseAddress.toLong().coerceAtLeast(0L)
        val progEndExclusive = prefBase + buffer.size.toLong()

        val dfStart = dataFlashStartAddress?.toLong() ?: -1L
        val dfEndExclusive = if (dfStart >= 0L && dataFlashSizeBytes > 0) dfStart + dataFlashSizeBytes.toLong() else -1L

        val hasChunksInPreferredProgramRange = prefBase > 0L && chunks.any { c ->
            val cEnd = c.address + c.data.size
            cEnd > prefBase && c.address < progEndExclusive
        }

        val baseOffset = when {
            hasChunksInPreferredProgramRange -> prefBase
            prefBase > 0L && minAddr >= prefBase && maxAddr <= progEndExclusive -> prefBase
            maxAddr <= targetFlashSizeBytes.toLong() && prefBase == 0L -> 0L
            minAddr < prefBase && dfStart >= 0L && chunks.any { it.address in dfStart until dfEndExclusive } -> prefBase
            else -> (minAddr and 0xFFFFFF00L)
        }

        var writtenBytes = 0
        var dfBuffer: ByteArray? = null
        var writtenDfBytes = 0

        for (chunk in chunks) {
            if (dfStart >= 0L && dataFlashSizeBytes > 0) {
                for (i in chunk.data.indices) {
                    val byteAddr = chunk.address + i
                    if (byteAddr in dfStart until dfEndExclusive) {
                        if (dfBuffer == null) {
                            dfBuffer = ByteArray(dataFlashSizeBytes) { 0xFF.toByte() }
                        }
                        val dfOffset = (byteAddr - dfStart).toInt()
                        if (dfOffset in dfBuffer.indices) {
                            dfBuffer[dfOffset] = chunk.data[i]
                            writtenDfBytes++
                        }
                    }
                }
            }

            val relAddr = (chunk.address - baseOffset).toInt()
            if (relAddr + chunk.data.size <= 0 || relAddr >= buffer.size) continue
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
            startAddress = baseOffset.toInt(),
            dataFlashBytes = if (writtenDfBytes > 0) dfBuffer else null,
            dataFlashStartAddress = if (writtenDfBytes > 0 && dfStart >= 0L) dfStart.toInt() else 0
        )
    }

    /**
     * Builds the complete physical memory map from 0x00000 to chipConfig.programEndAddress
     * (e.g., 0x13FFF + 1 = 0x14000 = 81,920 bytes = 80 KiB for R5F21258),
     * placing Data Flash and Program Flash at their exact physical chip offsets.
     */
    fun buildMergedMemoryMap(
        chipConfig: ChipConfig,
        programFlashBytes: ByteArray,
        dataFlashBytes: ByteArray?
    ): ByteArray {
        val fullSize = (chipConfig.programEndAddress + 1)
            .coerceAtLeast(chipConfig.programStartAddress + programFlashBytes.size)
        val merged = ByteArray(fullSize) { 0xFF.toByte() }

        val dfStart = chipConfig.dataFlashStartAddress
        if (dfStart != null && dfStart >= 0 && dataFlashBytes != null && dataFlashBytes.isNotEmpty()) {
            val dfMaxCopy = min(dataFlashBytes.size, (fullSize - dfStart).coerceAtLeast(0))
            if (dfMaxCopy > 0) {
                System.arraycopy(dataFlashBytes, 0, merged, dfStart, dfMaxCopy)
            }
        }

        val progStart = chipConfig.programStartAddress.coerceAtLeast(0)
        val progMaxCopy = min(programFlashBytes.size, (fullSize - progStart).coerceAtLeast(0))
        if (progMaxCopy > 0) {
            System.arraycopy(programFlashBytes, 0, merged, progStart, progMaxCopy)
        }

        return merged
    }

    fun toIntelHex(bytes: ByteArray, baseAddress: Int = 0): String {
        return toIntelHexMultiSegment(listOf(baseAddress to bytes))
    }

    fun toIntelHexMultiSegment(segments: List<Pair<Int, ByteArray>>): String {
        val sb = StringBuilder()
        var currentUpper = -1
        for ((baseAddress, bytes) in segments.sortedBy { it.first }) {
            var offset = 0
            while (offset < bytes.size) {
                val len = min(16, bytes.size - offset)
                val slice = bytes.copyOfRange(offset, offset + len)
                // Skip completely 0xFF records to keep exported HEX compact and standard
                if (slice.any { it != 0xFF.toByte() }) {
                    val physicalAddr = baseAddress + offset
                    val upper = (physicalAddr ushr 16) and 0xFFFF
                    if (upper != currentUpper) {
                        currentUpper = upper
                        val hi = (upper ushr 8) and 0xFF
                        val lo = upper and 0xFF
                        val sum = (2 + 0 + 0 + 4 + hi + lo) and 0xFF
                        val chk = ((0x100 - sum) and 0xFF)
                        sb.append(String.format(Locale.US, ":02000004%04X%02X\r\n", upper, chk))
                    }
                    val lowAddr = physicalAddr and 0xFFFF
                    var sum = len + ((lowAddr ushr 8) and 0xFF) + (lowAddr and 0xFF)
                    sb.append(String.format(Locale.US, ":%02X%04X00", len, lowAddr))
                    for (b in slice) {
                        val v = b.toInt() and 0xFF
                        sum += v
                        sb.append(String.format(Locale.US, "%02X", v))
                    }
                    val chk = ((0x100 - (sum and 0xFF)) and 0xFF)
                    sb.append(String.format(Locale.US, "%02X\r\n", chk))
                }
                offset += len
            }
        }
        sb.append(":00000001FF\r\n")
        return sb.toString()
    }
}

