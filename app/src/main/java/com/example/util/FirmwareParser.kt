package com.example.util

import com.example.data.model.ChipConfig
import com.example.data.model.FirmwareFileData
import java.util.Locale
import kotlin.math.min

enum class FirmwareExportFormat(
    val extension: String,
    val label: String,
    val mimeType: String
) {
    BIN("bin", ".bin", "application/octet-stream"),
    S19("s19", ".s19", "text/plain"),
    HEX("hex", ".hex", "text/plain")
}

class FirmwareValidationException(message: String) : IllegalArgumentException(message)

object FirmwareParser {

    const val INVALID_NON_FIRMWARE_FILE_MESSAGE =
        "ملف غير صالح: لا يمكن إضافة الصور أو الملفات غير البرمجية. اختر ملف .bin أو .s19 أو .hex فقط"

    private val ALLOWED_EXTENSIONS = setOf(
        "bin", "s19", "mot", "srec", "s28", "s37", "hex", "ihx"
    )

    fun createEmptyBuffer(sizeBytes: Int): ByteArray {
        return ByteArray(sizeBytes.coerceAtLeast(1024)) { 0xFF.toByte() }
    }

    /**
     * Stage 1 Gate: rejects image/video/audio/archive/document MIME types and unsupported extensions before reading bytes.
     */
    fun validateFileMetadata(fileName: String, mimeType: String?) {
        val cleanMime = mimeType?.trim()?.lowercase().orEmpty()
        if (cleanMime.startsWith("image/") || cleanMime.startsWith("video/") || cleanMime.startsWith("audio/") ||
            cleanMime == "application/pdf" || cleanMime == "application/zip" ||
            cleanMime == "application/x-rar-compressed" || cleanMime == "application/vnd.rar" ||
            cleanMime == "application/x-7z-compressed" ||
            cleanMime == "application/vnd.android.package-archive"
        ) {
            throw FirmwareValidationException(
                "$INVALID_NON_FIRMWARE_FILE_MESSAGE ($cleanMime)"
            )
        }
        val ext = fileName.substringAfterLast('.', "").trim().lowercase()
        if (ext.isNotEmpty() && ext !in ALLOWED_EXTENSIONS) {
            throw FirmwareValidationException(
                "$INVALID_NON_FIRMWARE_FILE_MESSAGE (.$ext)"
            )
        }
    }

    /**
     * Stage 2 Gate + Strict Parser: inspects first 16 magic bytes (PNG/JPEG/GIF/BMP/WEBP/HEIC/PDF/ZIP/RAR/7Z/ELF),
     * validates S-Record & Intel HEX line checksums, and enforces target flash capacity rules.
     */
    fun validateAndParseFirmware(
        rawBytes: ByteArray,
        fileName: String,
        mimeType: String? = null,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0,
        fullPhysicalMapEndAddress: Int? = null
    ): FirmwareFileData {
        validateFileMetadata(fileName, mimeType)

        if (rawBytes.isEmpty()) {
            throw FirmwareValidationException("الملف المختار فارغ (0 بايت).")
        }

        detectSpoofedMagicHeader(rawBytes)?.let { fakeKind ->
            throw FirmwareValidationException(
                "$INVALID_NON_FIRMWARE_FILE_MESSAGE (تم رصد ترويسة $fakeKind)"
            )
        }

        val lower = fileName.lowercase(Locale.US)
        val ext = lower.substringAfterLast('.', "")
        val textPreview = try {
            rawBytes.take(256).toByteArray().toString(Charsets.US_ASCII).trimStart()
        } catch (_: Throwable) {
            ""
        }

        val isHex = ext == "hex" || ext == "ihx" || textPreview.startsWith(":")
        val isSrec = ext in setOf("s19", "mot", "srec", "s28", "s37") ||
            textPreview.startsWith("S0") || textPreview.startsWith("S1") ||
            textPreview.startsWith("S2") || textPreview.startsWith("S3")

        return when {
            isHex -> parseIntelHex(
                content = rawBytes.toString(Charsets.US_ASCII),
                fileName = fileName,
                targetFlashSizeBytes = targetFlashSizeBytes,
                preferredBaseAddress = preferredBaseAddress,
                dataFlashStartAddress = dataFlashStartAddress,
                dataFlashSizeBytes = dataFlashSizeBytes,
                strictValidation = true
            )
            isSrec -> parseMotorolaSRecord(
                content = rawBytes.toString(Charsets.US_ASCII),
                fileName = fileName,
                targetFlashSizeBytes = targetFlashSizeBytes,
                preferredBaseAddress = preferredBaseAddress,
                dataFlashStartAddress = dataFlashStartAddress,
                dataFlashSizeBytes = dataFlashSizeBytes,
                strictValidation = true
            )
            else -> {
                val maxAllowedBinSize = if (preferredBaseAddress > 0 && fullPhysicalMapEndAddress != null && fullPhysicalMapEndAddress >= preferredBaseAddress) {
                    (fullPhysicalMapEndAddress + 1).coerceAtLeast(preferredBaseAddress + targetFlashSizeBytes)
                } else {
                    targetFlashSizeBytes
                }
                if (rawBytes.size > maxAllowedBinSize) {
                    throw FirmwareValidationException(
                        String.format(
                            Locale.US,
                            "حجم الملف (%,d بايت) يتجاوز سعة الفلاش للمعالج المختار (%,d بايت / %d KB). تم رفض التحميل لحماية الذاكرة.",
                            rawBytes.size,
                            maxAllowedBinSize,
                            maxAllowedBinSize / 1024
                        )
                    )
                }
                parseRawBinary(
                    rawBytes = rawBytes,
                    fileName = fileName,
                    targetFlashSizeBytes = targetFlashSizeBytes,
                    preferredBaseAddress = preferredBaseAddress,
                    dataFlashStartAddress = dataFlashStartAddress,
                    dataFlashSizeBytes = dataFlashSizeBytes
                )
            }
        }
    }

    private fun detectSpoofedMagicHeader(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        val headLen = min(16, bytes.size)
        fun b(idx: Int): Int = if (idx < headLen) (bytes[idx].toInt() and 0xFF) else -1

        val b0 = b(0)
        val b1 = b(1)
        val b2 = b(2)
        val b3 = b(3)

        return when {
            headLen >= 8 && b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47 &&
                b(4) == 0x0D && b(5) == 0x0A && b(6) == 0x1A && b(7) == 0x0A -> "صورة PNG"
            headLen >= 4 && b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47 -> "صورة PNG"
            headLen >= 3 && b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF -> "صورة JPEG"
            headLen >= 6 && b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38 &&
                (b(4) == 0x37 || b(4) == 0x39) && b(5) == 0x61 -> "صورة GIF"
            headLen >= 4 && b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38 -> "صورة GIF"
            headLen >= 2 && b0 == 0x42 && b1 == 0x4D && bytes.size > 14 -> "صورة BMP"
            headLen >= 12 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> "صورة WEBP"
            headLen >= 4 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 -> "ملف وسائط RIFF"
            headLen >= 4 && ((b0 == 0x49 && b1 == 0x49 && b2 == 0x2A && b3 == 0x00) ||
                (b0 == 0x4D && b1 == 0x4D && b2 == 0x00 && b3 == 0x2A)) -> "صورة TIFF"
            headLen >= 12 && b(4) == 0x66 && b(5) == 0x74 && b(6) == 0x79 && b(7) == 0x70 -> "ملف وسائط HEIC/AVIF/MP4"
            headLen >= 4 && b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46 -> "مستند PDF"
            headLen >= 4 && b0 == 0x50 && b1 == 0x4B && (b2 == 0x03 || b2 == 0x05 || b2 == 0x07) -> "أرشيف ZIP/APK"
            headLen >= 6 && b0 == 0x52 && b1 == 0x61 && b2 == 0x72 && b3 == 0x21 &&
                b(4) == 0x1A && b(5) == 0x07 -> "أرشيف RAR"
            headLen >= 6 && b0 == 0x37 && b1 == 0x7A && b2 == 0xBC && b3 == 0xAF &&
                b(4) == 0x27 && b(5) == 0x1C -> "أرشيف 7Z"
            headLen >= 4 && b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46 -> "ملف تنفيذي ELF"
            else -> null
        }
    }

    fun parseFirmware(
        rawBytes: ByteArray,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0
    ): FirmwareFileData {
        val lower = fileName.lowercase(Locale.US)
        val textPreview = try {
            rawBytes.take(256).toByteArray().toString(Charsets.US_ASCII).trimStart()
        } catch (_: Throwable) {
            ""
        }

        return when {
            lower.endsWith(".hex") || lower.endsWith(".ihx") || textPreview.startsWith(":") -> {
                parseIntelHex(
                    rawBytes.toString(Charsets.US_ASCII),
                    fileName,
                    targetFlashSizeBytes,
                    preferredBaseAddress,
                    dataFlashStartAddress,
                    dataFlashSizeBytes,
                    strictValidation = false
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
                    dataFlashSizeBytes,
                    strictValidation = false
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
            val copyLen = min(rawBytes.size, buffer.size)
            System.arraycopy(rawBytes, 0, buffer, 0, copyLen)
            count = buffer.count { it != 0xFF.toByte() }

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
            dataFlashStartAddress = dfStart,
            rawSizeBytes = rawBytes.size
        )
    }

    private data class AddressChunk(val address: Long, val data: ByteArray)

    private fun parseIntelHex(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0,
        strictValidation: Boolean
    ): FirmwareFileData {
        val chunks = mutableListOf<AddressChunk>()
        var extendedSegmentAddress = 0L
        var extendedLinearAddress = 0L
        var lineNum = 0

        content.lineSequence().forEach { rawLine ->
            lineNum++
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (!line.startsWith(":") || line.length < 11) {
                if (strictValidation) {
                    throw FirmwareValidationException("سطر Intel HEX غير صالح في السطر $lineNum.")
                }
                return@forEach
            }

            val byteCount = line.substring(1, 3).toIntOrNull(16)
                ?: if (strictValidation) throw FirmwareValidationException("عدد بايتات Intel HEX تالف في السطر $lineNum.") else return@forEach
            val address = line.substring(3, 7).toIntOrNull(16)
                ?: if (strictValidation) throw FirmwareValidationException("عنوان Intel HEX غير صالح في السطر $lineNum.") else return@forEach
            val recordType = line.substring(7, 9).toIntOrNull(16)
                ?: if (strictValidation) throw FirmwareValidationException("نوع سجل Intel HEX غير صالح في السطر $lineNum.") else return@forEach

            val expectedLen = 11 + byteCount * 2
            if (line.length < expectedLen || (strictValidation && line.length != expectedLen)) {
                if (strictValidation) {
                    throw FirmwareValidationException("طول سطر Intel HEX غير مطابق في السطر $lineNum.")
                }
                if (line.length < expectedLen) return@forEach
            }

            if (strictValidation) {
                var sum = 0
                val totalRecordBytes = 4 + byteCount + 1
                for (i in 0 until totalRecordBytes) {
                    val b = line.substring(1 + i * 2, 3 + i * 2).toIntOrNull(16)
                        ?: throw FirmwareValidationException("محرف سداسي عشري تالف في السطر $lineNum.")
                    sum += b
                }
                if ((sum and 0xFF) != 0) {
                    throw FirmwareValidationException("فشل Checksum لملف Intel HEX في السطر $lineNum.")
                }
            }

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

        if (strictValidation && chunks.isEmpty()) {
            throw FirmwareValidationException("ملف Intel HEX لا يحتوي على أي بيانات برمجية صالحة.")
        }

        return materializeChunks(
            chunks = chunks,
            fileName = fileName,
            format = "Intel HEX",
            targetFlashSizeBytes = targetFlashSizeBytes,
            preferredBaseAddress = preferredBaseAddress,
            dataFlashStartAddress = dataFlashStartAddress,
            dataFlashSizeBytes = dataFlashSizeBytes,
            strictSizeCheck = strictValidation
        )
    }

    private fun parseMotorolaSRecord(
        content: String,
        fileName: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0,
        strictValidation: Boolean
    ): FirmwareFileData {
        val chunks = mutableListOf<AddressChunk>()
        var lineNum = 0

        content.lineSequence().forEach { rawLine ->
            lineNum++
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (!line.startsWith("S") || line.length < 10) {
                if (strictValidation) {
                    throw FirmwareValidationException("سطر Motorola S-Record غير صالح في السطر $lineNum.")
                }
                return@forEach
            }
            val type = line[1]
            if (type !in '0'..'9') {
                if (strictValidation) {
                    throw FirmwareValidationException("نوع سجل S-Record غير معروف (S$type) في السطر $lineNum.")
                }
                return@forEach
            }
            val byteCount = line.substring(2, 4).toIntOrNull(16)
                ?: if (strictValidation) throw FirmwareValidationException("طول سجل S-Record تالف في السطر $lineNum.") else return@forEach

            val expectedTotalChars = 4 + byteCount * 2
            if (strictValidation && line.length != expectedTotalChars) {
                throw FirmwareValidationException("طول سطر S-Record غير متطابق في السطر $lineNum.")
            }

            if (strictValidation) {
                var sum = byteCount
                for (i in 0 until byteCount - 1) {
                    val idx = 4 + i * 2
                    val b = line.substring(idx, idx + 2).toIntOrNull(16)
                        ?: throw FirmwareValidationException("محرف تالف في S-Record بالسطر $lineNum.")
                    sum += b
                }
                val fileChk = line.substring(4 + (byteCount - 1) * 2, expectedTotalChars).toIntOrNull(16)
                    ?: throw FirmwareValidationException("Checksum مفقود في S-Record بالسطر $lineNum.")
                val expectedChk = (sum and 0xFF) xor 0xFF
                if (fileChk != expectedChk) {
                    throw FirmwareValidationException("فشل Checksum لملف S-Record في السطر $lineNum.")
                }
            }

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

        if (strictValidation && chunks.isEmpty()) {
            throw FirmwareValidationException("ملف S-Record لا يحتوي على أي بيانات برمجية (S1/S2/S3).")
        }

        return materializeChunks(
            chunks = chunks,
            fileName = fileName,
            format = "Motorola S-Record",
            targetFlashSizeBytes = targetFlashSizeBytes,
            preferredBaseAddress = preferredBaseAddress,
            dataFlashStartAddress = dataFlashStartAddress,
            dataFlashSizeBytes = dataFlashSizeBytes,
            strictSizeCheck = strictValidation
        )
    }

    private fun materializeChunks(
        chunks: List<AddressChunk>,
        fileName: String,
        format: String,
        targetFlashSizeBytes: Int,
        preferredBaseAddress: Int = 0,
        dataFlashStartAddress: Int? = null,
        dataFlashSizeBytes: Int = 0,
        strictSizeCheck: Boolean = false
    ): FirmwareFileData {
        val buffer = createEmptyBuffer(targetFlashSizeBytes)
        if (chunks.isEmpty()) {
            return FirmwareFileData(fileName, format, buffer, 0, preferredBaseAddress, rawSizeBytes = 0)
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

        if (strictSizeCheck) {
            val outOfRangeChunk = chunks.firstOrNull { chunk ->
                val cStart = chunk.address
                val cEnd = cStart + chunk.data.size
                val insideDf = dfStart >= 0L && dfEndExclusive > dfStart && cStart >= dfStart && cEnd <= dfEndExclusive
                val insideProg = cStart >= baseOffset && cEnd <= baseOffset + buffer.size.toLong()
                !insideDf && !insideProg
            }
            val spanBytes = (maxAddr - minAddr).coerceAtLeast(0L)
            val totalAddressableCap = (targetFlashSizeBytes + dataFlashSizeBytes).toLong()
            val exceedsPhysicalEnd = outOfRangeChunk != null &&
                maxAddr > maxOf(progEndExclusive, dfEndExclusive, targetFlashSizeBytes.toLong()) &&
                outOfRangeChunk.address >= baseOffset
            if ((outOfRangeChunk != null && spanBytes > totalAddressableCap) || exceedsPhysicalEnd) {
                throw FirmwareValidationException(
                    String.format(
                        Locale.US,
                        "نطاق عناوين الملف (%,d بايت) يتجاوز سعة الفلاش المسموحة للمعالج (%d KB).",
                        spanBytes,
                        targetFlashSizeBytes / 1024
                    )
                )
            }
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

        val totalLogicalBytes = (writtenBytes + writtenDfBytes).coerceAtLeast(1)
        return FirmwareFileData(
            fileName = fileName,
            format = format,
            bytes = buffer,
            nonEmptyBytesCount = writtenBytes,
            startAddress = baseOffset.toInt(),
            dataFlashBytes = if (writtenDfBytes > 0) dfBuffer else null,
            dataFlashStartAddress = if (writtenDfBytes > 0 && dfStart >= 0L) dfStart.toInt() else 0,
            rawSizeBytes = totalLogicalBytes
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

    fun toMotorolaSRecord(
        bytes: ByteArray,
        baseAddress: Int = 0,
        headerLabel: String = "BARAA"
    ): String {
        return toMotorolaSRecordMultiSegment(listOf(baseAddress to bytes), headerLabel)
    }

    fun toMotorolaSRecordMultiSegment(
        segments: List<Pair<Int, ByteArray>>,
        headerLabel: String = "BARAA"
    ): String {
        val sb = StringBuilder()
        val cleanHeader = headerLabel.take(20).toByteArray(Charsets.US_ASCII)
        val s0Count = 2 + cleanHeader.size + 1
        var s0Sum = s0Count
        sb.append(String.format(Locale.US, "S0%02X0000", s0Count))
        for (b in cleanHeader) {
            val v = b.toInt() and 0xFF
            s0Sum += v
            sb.append(String.format(Locale.US, "%02X", v))
        }
        val s0Chk = (s0Sum and 0xFF) xor 0xFF
        sb.append(String.format(Locale.US, "%02X\r\n", s0Chk))

        val sorted = segments.sortedBy { it.first }
        var firstDataAddr = sorted.firstOrNull()?.first ?: 0
        var recordedFirstAddr = false

        for ((baseAddress, bytes) in sorted) {
            var offset = 0
            while (offset < bytes.size) {
                val len = min(16, bytes.size - offset)
                val slice = bytes.copyOfRange(offset, offset + len)
                if (slice.any { it != 0xFF.toByte() }) {
                    val physicalAddr = baseAddress + offset
                    if (!recordedFirstAddr) {
                        firstDataAddr = physicalAddr
                        recordedFirstAddr = true
                    }
                    val byteCount = 4 + len + 1
                    val a3 = (physicalAddr ushr 24) and 0xFF
                    val a2 = (physicalAddr ushr 16) and 0xFF
                    val a1 = (physicalAddr ushr 8) and 0xFF
                    val a0 = physicalAddr and 0xFF
                    var sum = byteCount + a3 + a2 + a1 + a0
                    sb.append(String.format(Locale.US, "S3%02X%08X", byteCount, physicalAddr))
                    for (b in slice) {
                        val v = b.toInt() and 0xFF
                        sum += v
                        sb.append(String.format(Locale.US, "%02X", v))
                    }
                    val chk = (sum and 0xFF) xor 0xFF
                    sb.append(String.format(Locale.US, "%02X\r\n", chk))
                }
                offset += len
            }
        }

        // S7 32-bit termination record
        val s7Count = 5
        val e3 = (firstDataAddr ushr 24) and 0xFF
        val e2 = (firstDataAddr ushr 16) and 0xFF
        val e1 = (firstDataAddr ushr 8) and 0xFF
        val e0 = firstDataAddr and 0xFF
        val s7Chk = ((s7Count + e3 + e2 + e1 + e0) and 0xFF) xor 0xFF
        sb.append(String.format(Locale.US, "S705%08X%02X\r\n", firstDataAddr, s7Chk))
        return sb.toString()
    }
}
