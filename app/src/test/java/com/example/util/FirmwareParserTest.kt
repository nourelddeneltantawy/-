package com.example.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirmwareParserTest {

    private fun assertRejectedAsNonFirmware(bytes: ByteArray, fileName: String, mime: String? = null) {
        try {
            FirmwareParser.validateAndParseFirmware(
                rawBytes = bytes,
                fileName = fileName,
                mimeType = mime,
                targetFlashSizeBytes = 32 * 1024
            )
            fail("Expected FirmwareValidationException for $fileName")
        } catch (ex: FirmwareValidationException) {
            assertTrue(
                "Unexpected validation message: ${ex.message}",
                ex.message?.contains("ملف غير صالح") == true
            )
        }
    }

    @Test
    fun rejectsSpoofedImagesAndNonFirmwareMagicHeadersDisguisedAsBin() {
        val headers = listOf(
            "photo.bin" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52),
            "pic.bin" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10),
            "anim.bin" to byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00),
            "bmp.bin" to ByteArray(20) { idx -> when (idx) { 0 -> 0x42; 1 -> 0x4D; else -> 0x00 } },
            "webp.bin" to byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x12, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20),
            "heic.bin" to byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63),
            "doc.bin" to byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37),
            "archive.bin" to byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00),
            "rar.bin" to byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00),
            "sevenz.bin" to byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0x00, 0x04),
            "elf.bin" to byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00)
        )
        headers.forEach { (name, payload) ->
            assertRejectedAsNonFirmware(payload, name)
        }
    }

    @Test
    fun rejectsImageVideoAudioPdfMimeAndUnsupportedExtensions() {
        val validDummy = ByteArray(16) { 0x12 }
        assertRejectedAsNonFirmware(validDummy, "firmware.bin", "image/jpeg")
        assertRejectedAsNonFirmware(validDummy, "firmware.bin", "video/mp4")
        assertRejectedAsNonFirmware(validDummy, "firmware.bin", "application/pdf")
        assertRejectedAsNonFirmware(validDummy, "photo.jpg", null)
        assertRejectedAsNonFirmware(validDummy, "notes.txt", "text/plain")
    }

    @Test
    fun rejectsBinAndHexExceedingTargetFlashCapacity() {
        val oversizedBin = ByteArray(33 * 1024) { 0x5A }
        try {
            FirmwareParser.validateAndParseFirmware(
                rawBytes = oversizedBin,
                fileName = "too_big.bin",
                targetFlashSizeBytes = 32 * 1024
            )
            fail("Expected flash capacity exception")
        } catch (ex: FirmwareValidationException) {
            assertTrue(ex.message?.contains("يتجاوز سعة الفلاش") == true)
        }
    }

    @Test
    fun roundTripsBinS19AndHexFormatsAccurately() {
        val original = ByteArray(4096) { 0xFF.toByte() }
        for (i in 0 until 256) {
            original[i] = (i xor 0xA5).toByte()
        }
        original[2048] = 0x55
        original[2049] = 0xAA.toByte()

        // 1) BIN
        val binParsed = FirmwareParser.validateAndParseFirmware(
            rawBytes = original,
            fileName = "dump.bin",
            targetFlashSizeBytes = 4096
        )
        assertArrayEquals(original, binParsed.bytes)

        // 2) Motorola S19
        val s19Text = FirmwareParser.toMotorolaSRecord(original, baseAddress = 0)
        val s19Parsed = FirmwareParser.validateAndParseFirmware(
            rawBytes = s19Text.toByteArray(Charsets.US_ASCII),
            fileName = "dump.s19",
            targetFlashSizeBytes = 4096
        )
        assertArrayEquals(original, s19Parsed.bytes)
        assertEquals("Motorola S-Record", s19Parsed.format)

        // 3) Intel HEX
        val hexText = FirmwareParser.toIntelHex(original, baseAddress = 0)
        val hexParsed = FirmwareParser.validateAndParseFirmware(
            rawBytes = hexText.toByteArray(Charsets.US_ASCII),
            fileName = "dump.hex",
            targetFlashSizeBytes = 4096
        )
        assertArrayEquals(original, hexParsed.bytes)
        assertEquals("Intel HEX", hexParsed.format)
    }
}
