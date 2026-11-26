package com.example.util

import java.util.Locale

/**
 * Plug & Play Protocol Registry for NEC / Renesas 78K0 (uPD78F) Framed Bootloader Family.
 * Derived strictly from UNIVERSAL_READ_PROTOCOL_V1 and UNIVERSAL_WRITE_ERASE_VERIFY_V1 specifications.
 */
data class PlugAndPlayProtocolEntry(
    val protocolId: String,
    val familyDisplayName: String,
    val sampleChipDatabaseKey: String,
    val sampleChipPartNumber: String,
    val supportedAdapterVidPids: List<String>,
    val flashStartAddress: Int,
    val flashEndAddress: Int,
    val pageSizeBytes: Int,
    val blockSizeBytes: Int,
    val initialBaudRate: Int,
    val resetPulseMs: Long,
    val postResetDelayMs: Long,
    val syncByteGapMs: Long,
    val interByteDelayMs: Long,
    val commandToDataDelayMs: Long,
    val frameToFrameDelayMs: Long,
    val baudSwitchDelayMs: Long,
    val eraseWriteBusyWaitMs: Long
)

object Nec78k0ProtocolRegistry {
    const val PROTOCOL_ID = "NEC_78K0_FAMILY"
    const val FAMILY_DISPLAY_NAME = "NEC / Renesas 78K0 (uPD78F) Framed Bootloader Family"
    const val SAMPLE_CHIP_DATABASE_KEY = "10 7F 04 7C 7F 7F 01 C4 37 38 46 B0 B5 31 B3 C1"
    const val SAMPLE_CHIP_PART_NUMBER = "uPD78F0513A"

    val SUPPORTED_ADAPTER_VID_PIDS: List<String> = listOf(
        "0403:6001",
        "0403:6015",
        "1A86:7523",
        "10C4:EA60",
        "067B:2303"
    )

    val SUPPORTED_VID_PID_PAIRS: List<Pair<Int, Int>> = listOf(
        0x0403 to 0x6001,
        0x0403 to 0x6015,
        0x1A86 to 0x7523,
        0x10C4 to 0xEA60,
        0x067B to 0x2303
    )

    val SAMPLE_SILICON_ID_BYTES: ByteArray = byteArrayOf(
        0x10, 0x7F, 0x04, 0x7C, 0x7F, 0x7F, 0x01, 0xC4.toByte(),
        0x37, 0x38, 0x46, 0xB0.toByte(), 0xB5.toByte(), 0x31, 0xB3.toByte(), 0xC1.toByte()
    )

    const val FLASH_START_ADDRESS = 0x000000
    const val FLASH_END_ADDRESS = 0x007FFF
    const val FLASH_SIZE_BYTES = 32 * 1024
    const val PAGE_SIZE_BYTES = 256
    const val BLOCK_SIZE_BYTES = 1024

    const val INITIAL_BAUD_RATE = 9600
    const val RESET_PULSE_MS = 100L
    const val POST_RESET_DELAY_MS = 150L
    const val SYNC_BYTE_GAP_MS = 10L
    const val INTER_BYTE_DELAY_MS = 1L
    const val COMMAND_TO_DATA_DELAY_MS = 23L
    const val FRAME_TO_FRAME_DELAY_MS = 120L
    const val BAUD_SWITCH_DELAY_MS = 25L
    const val ERASE_WRITE_BUSY_WAIT_MS = 450L

    /**
     * Closed-loop blank-chip proof test vector specified by antiDummyValidationRules.
     */
    val CLOSED_LOOP_PROOF_VECTOR: ByteArray = byteArrayOf(
        0x55, 0xAA.toByte(), 0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(),
        0xDE.toByte(), 0xF0.toByte(), 0x11, 0x22, 0x33, 0x44, 0x55, 0x66
    )

    val DEFAULT_ENTRY = PlugAndPlayProtocolEntry(
        protocolId = PROTOCOL_ID,
        familyDisplayName = FAMILY_DISPLAY_NAME,
        sampleChipDatabaseKey = SAMPLE_CHIP_DATABASE_KEY,
        sampleChipPartNumber = SAMPLE_CHIP_PART_NUMBER,
        supportedAdapterVidPids = SUPPORTED_ADAPTER_VID_PIDS,
        flashStartAddress = FLASH_START_ADDRESS,
        flashEndAddress = FLASH_END_ADDRESS,
        pageSizeBytes = PAGE_SIZE_BYTES,
        blockSizeBytes = BLOCK_SIZE_BYTES,
        initialBaudRate = INITIAL_BAUD_RATE,
        resetPulseMs = RESET_PULSE_MS,
        postResetDelayMs = POST_RESET_DELAY_MS,
        syncByteGapMs = SYNC_BYTE_GAP_MS,
        interByteDelayMs = INTER_BYTE_DELAY_MS,
        commandToDataDelayMs = COMMAND_TO_DATA_DELAY_MS,
        frameToFrameDelayMs = FRAME_TO_FRAME_DELAY_MS,
        baudSwitchDelayMs = BAUD_SWITCH_DELAY_MS,
        eraseWriteBusyWaitMs = ERASE_WRITE_BUSY_WAIT_MS
    )

    fun isSupportedAdapter(vendorId: Int, productId: Int): Boolean {
        return SUPPORTED_VID_PID_PAIRS.any { it.first == vendorId && it.second == productId }
    }

    fun formatVidPid(vendorId: Int, productId: Int): String {
        return String.format(Locale.US, "%04X:%04X", vendorId and 0xFFFF, productId and 0xFFFF)
    }

    /**
     * NEC 78K0 Silicon Signature encodes ASCII device strings using 7-bit ASCII with an optional
     * odd-parity MSB in bit 7 (e.g. 0xB0='0', 0xB5='5', 0xB3='3', 0xC1='A' -> "78F0513A").
     */
    fun decodeParityAsciiSignature(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val sevenBit = b.toInt() and 0x7F
            if (sevenBit in 32..126) {
                sb.append(sevenBit.toChar())
            }
        }
        return sb.toString()
    }

    fun matchesSampleDatabaseKey(bytes: ByteArray): Boolean {
        if (bytes.size < SAMPLE_SILICON_ID_BYTES.size) return false
        for (start in 0..(bytes.size - SAMPLE_SILICON_ID_BYTES.size)) {
            var allMatch = true
            for (i in SAMPLE_SILICON_ID_BYTES.indices) {
                if (bytes[start + i] != SAMPLE_SILICON_ID_BYTES[i]) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) return true
        }
        return false
    }

    /**
     * Anti-Dummy Rule 1: returns true if >= 99% of bytes are 0x00 or 0xFF.
     */
    fun isDummyAllZeroOrFf(bytes: ByteArray, threshold: Double = 0.99): Boolean {
        if (bytes.isEmpty()) return true
        var dummyCount = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0x00 || v == 0xFF) {
                dummyCount++
            }
        }
        return (dummyCount.toDouble() / bytes.size.toDouble()) >= threshold
    }

    fun closedLoopTestPageStartAddress(
        flashEndAddress: Int = FLASH_END_ADDRESS,
        pageSizeBytes: Int = PAGE_SIZE_BYTES
    ): Int {
        return (flashEndAddress - pageSizeBytes + 1).coerceAtLeast(FLASH_START_ADDRESS)
    }
}
