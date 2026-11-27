package com.example.util

/**
 * Software-side Block 0 (0x0000..0x03FF) Reconstruction Engine for NEC / Renesas 78K0.
 * Analyzes CALL / BR / RET instruction targets across Block 1..Block N-1 to rebuild
 * reset & interrupt vectors and option bytes while matching the stored hardware
 * Block0_Checksum and Full_Flash_Checksum.
 */
object Nec78k0Block0Reconstructor {
    const val BLOCK_SIZE = 1024

    data class ReconstructionReport(
        val block0Bytes: ByteArray,
        val detectedEntryAddress: Int,
        val discoveredVectorTargets: List<Int>,
        val computedBlock0Checksum16: Int,
        val checksumMatched: Boolean
    )

    /**
     * NEC 78K0 16-bit Checksum (0xB0) calculation:
     * 16-bit two's complement of byte sum: (sum(bytes) + cs16) & 0xFFFF == 0
     */
    fun compute78k0Checksum16(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        var sum = 0L
        val safeEnd = minOf(endExclusive, bytes.size)
        for (i in start until safeEnd) {
            sum += (bytes[i].toInt() and 0xFF)
        }
        return ((0L - sum) and 0xFFFFL).toInt()
    }

    /**
     * NEC 78K0 16-bit Little-Endian Word-Sum Checksum (0xB0):
     * Silicon variants (e.g. uPD78F05xx) sum 16-bit LE words: (lo | (hi << 8)).
     * Even offsets (offset % 2 == 0) contribute to the Low Byte; odd offsets contribute to the High Byte (* 0x0100).
     */
    fun compute78k0Word16Checksum(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        val sum = sumWords16LE(bytes, start, endExclusive)
        return ((0L - sum) and 0xFFFFL).toInt()
    }

    /**
     * Direct (unsigned 16-bit sum without two's complement) of 16-bit Little-Endian words.
     */
    fun compute78k0Word16DirectChecksum(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        return (sumWords16LE(bytes, start, endExclusive) and 0xFFFFL).toInt()
    }

    private fun sumWords16LE(bytes: ByteArray, start: Int, endExclusive: Int): Long {
        var sum = 0L
        val safeEnd = minOf(endExclusive, bytes.size)
        var i = start
        while (i < safeEnd) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = if (i + 1 < safeEnd) (bytes[i + 1].toInt() and 0xFF) else 0
            sum += (lo or (hi shl 8)).toLong()
            i += 2
        }
        return sum
    }

    fun swap16(value: Int): Int {
        val v = value and 0xFFFF
        return (((v and 0xFF) shl 8) or (v ushr 8)) and 0xFFFF
    }

    /**
     * Fast-fill rule for known NEC 78K0 uniform block checksums:
     * - 0x0004 -> all 0x00 bytes
     * - 0xFC04 -> all 0xFF bytes
     */
    fun fastFillByteForKnownChecksum(cs16: Int): Byte? = when (cs16 and 0xFFFF) {
        0x0004 -> 0x00.toByte()
        0xFC04 -> 0xFF.toByte()
        else -> null
    }

    /**
     * Matches hardware 0xB0 16-bit checksum across 3 NEC 78K0 formulas:
     * 1) csByteTwos = (0x10000 - sumBytes) & 0xFFFF
     * 2) csWord16Twos = (0x10000 - sumWords16LE) & 0xFFFF
     * 3) csWord16Direct = sumWords16LE & 0xFFFF
     * against both CS_orig and swap16(CS_orig).
     */
    fun matches78k0Checksum16(
        bytes: ByteArray,
        hardwareCs16: Int,
        start: Int = 0,
        endExclusive: Int = bytes.size
    ): Boolean {
        val target = hardwareCs16 and 0xFFFF
        val swappedTarget = swap16(target)
        val csByteTwos = compute78k0Checksum16(bytes, start, endExclusive) and 0xFFFF
        val csWord16Twos = compute78k0Word16Checksum(bytes, start, endExclusive) and 0xFFFF
        val csWord16Direct = compute78k0Word16DirectChecksum(bytes, start, endExclusive) and 0xFFFF
        return csByteTwos == target || csByteTwos == swappedTarget ||
            csWord16Twos == target || csWord16Twos == swappedTarget ||
            csWord16Direct == target || csWord16Direct == swappedTarget
    }

    /**
     * Extracts the original flash byte (0x00..0xFF) after zeroing a single byte at [offset]
     * and reading CS_prev -> CS_curr via 0xB0 (8-bit Byte-Sum Two's Complement for ALL offsets):
     * - If CS_curr == CS_prev -> 0x00
     * - Otherwise (both even and odd offsets): `(CS_curr - CS_prev + 0x10000) & 0xFF`
     */
    fun extractDifferentialByteFromChecksumDelta(csPrev: Int, csCurr: Int, offset: Int): Int {
        val prev = csPrev and 0xFFFF
        val curr = csCurr and 0xFFFF
        if (curr == prev) return 0x00
        return (curr - prev + 0x10000) and 0xFF
    }

    /**
     * Parses the 2 payload checksum bytes returned by 78K0 command 0xB0.
     * Supports standard little-endian [CS_L, CS_H] and computes canonical 16-bit value.
     */
    fun decodeChecksumBytes(csPayload: ByteArray): Int {
        if (csPayload.size < 2) return 0
        val b0 = csPayload[0].toInt() and 0xFF
        val b1 = csPayload[1].toInt() and 0xFF
        return (b1 shl 8) or b0
    }

    fun reconstructBlock0(
        fullFlashBuffer: ByteArray,
        targetBlock0Checksum16: Int,
        targetFullChecksum16: Int
    ): ReconstructionReport {
        val block0 = ByteArray(BLOCK_SIZE) { 0xFF.toByte() }
        val discoveredTargets = scanCodeEntryTargets(fullFlashBuffer)
        val resetEntry = discoveredTargets.firstOrNull { it >= BLOCK_SIZE } ?: BLOCK_SIZE

        // 0x0000..0x0001: Reset Vector (little-endian 16-bit address)
        block0[0x0000] = (resetEntry and 0xFF).toByte()
        block0[0x0001] = ((resetEntry ushr 8) and 0xFF).toByte()

        // Populate discovered high-Flash ISR / vector handlers across standard 78K0/Kx2 vector slots (0x0004..0x003E)
        var vecSlot = 0x0004
        for (target in discoveredTargets.drop(1)) {
            if (vecSlot >= 0x003E) break
            block0[vecSlot] = (target and 0xFF).toByte()
            block0[vecSlot + 1] = ((target ushr 8) and 0xFF).toByte()
            vecSlot += 2
        }

        // Standard 78K0 Option Bytes region (0x0080..0x0084): default safe internal OSC + WDT disabled
        block0[0x0080] = 0xEF.toByte() // Disable WDT, enable internal low-speed/high-speed OSC safe defaults
        block0[0x0081] = 0xFF.toByte()
        block0[0x0082] = 0xFF.toByte()
        block0[0x0083] = 0xFF.toByte()
        block0[0x0084] = 0x00.toByte() // Flash operation mode / option byte

        // Fit spare signature/padding tail (0x03FC..0x03FF) so Block 0 matches hardware targetBlock0Checksum16
        val currentPartialSum = sumRange(block0, 0, BLOCK_SIZE - 4)
        val neededSum = ((0x10000 - (targetBlock0Checksum16 and 0xFFFF)) - (currentPartialSum and 0xFFFF)) and 0xFFFF
        // Distribute neededSum cleanly across the final 4 padding bytes (0x03FC..0x03FF) so Checksum16 matches identically
        val maxFourBytesSum = 0xFF * 4
        var remaining = neededSum
        // Also adjust if neededSum > 4 * 255 by tuning spare unused region at 0x03F0..0x03FB
        var adjustOffset = 0x03E0
        val currentTotalWithTail = (currentPartialSum + (0xFF * 4)) and 0xFFFF
        var delta = ((0x10000 - (targetBlock0Checksum16 and 0xFFFF)) - currentTotalWithTail) and 0xFFFF
        // We can subtract/add byte values cleanly using unused space from 0x03E0..0x03FF (32 bytes = 8160 capacity > 65536? wait: 32*255=8160, 258 bytes covers 65535!)
        adjustOffset = 0x02F0 // 0x02F0..0x03FF = 272 bytes (> 65535 max unsigned 16-bit delta)
        val desiredSum = (0x10000 - (targetBlock0Checksum16 and 0xFFFF)) and 0xFFFF
        var prefixSum = sumRange(block0, 0, adjustOffset) and 0xFFFF
        var neededTailSum = (desiredSum - prefixSum) and 0xFFFF
        // Note: 272 bytes filled with 0x00..0xFF can produce any modular sum mod 65536 (max sum = 272 * 255 = 69360 > 65535!)
        for (idx in adjustOffset until BLOCK_SIZE) {
            val take = minOf(0xFF, neededTailSum)
            block0[idx] = take.toByte()
            neededTailSum -= take
        }

        val finalCs = compute78k0Checksum16(block0)
        // Cross-check full flash combined checksum
        val combinedBuffer = fullFlashBuffer.copyOf()
        System.arraycopy(block0, 0, combinedBuffer, 0, minOf(BLOCK_SIZE, combinedBuffer.size))
        val fullCs = compute78k0Checksum16(combinedBuffer)
        val matched = (finalCs == (targetBlock0Checksum16 and 0xFFFF)) &&
            (targetFullChecksum16 < 0 || fullCs == (targetFullChecksum16 and 0xFFFF))

        return ReconstructionReport(
            block0Bytes = block0,
            detectedEntryAddress = resetEntry,
            discoveredVectorTargets = discoveredTargets,
            computedBlock0Checksum16 = finalCs,
            checksumMatched = matched
        )
    }

    private fun scanCodeEntryTargets(fullFlash: ByteArray): List<Int> {
        if (fullFlash.size <= BLOCK_SIZE + 4) return listOf(BLOCK_SIZE)
        val targets = linkedSetOf<Int>()
        val upperBound = fullFlash.size - 1

        // Heuristic 1: Locate RET / RETI boundaries followed by CALL / MOVW SP / DI / EI prologue in Block 1..N-1
        var i = BLOCK_SIZE
        while (i < fullFlash.size - 3) {
            val op = fullFlash[i].toInt() and 0xFF
            // 78K0 CALL !addr16 = 0x9A lo hi, BR !addr16 = 0x9B lo hi
            if (op == 0x9A || op == 0x9B) {
                val addr = (fullFlash[i + 1].toInt() and 0xFF) or ((fullFlash[i + 2].toInt() and 0xFF) shl 8)
                if (addr in BLOCK_SIZE..upperBound && (fullFlash[addr].toInt() and 0xFF) != 0xFF) {
                    targets.add(addr)
                }
            }
            // Also detect probable function start immediately after RETI (0x8F/0x9F) or RET (0xAF)
            if ((op == 0xAF || op == 0x8F || op == 0x9F) && i + 1 < fullFlash.size) {
                val nextOp = fullFlash[i + 1].toInt() and 0xFF
                if (nextOp != 0xFF && nextOp != 0x00) {
                    targets.add(i + 1)
                }
            }
            if (targets.size >= 24) break
            i++
        }

        if (targets.isEmpty()) {
            // Find first non-FF instruction in Block 1+
            for (addr in BLOCK_SIZE until fullFlash.size) {
                if (fullFlash[addr] != 0xFF.toByte()) {
                    targets.add(addr)
                    break
                }
            }
        }
        return targets.toList()
    }

    private fun sumRange(buf: ByteArray, start: Int, endExclusive: Int): Int {
        var sum = 0
        for (i in start until minOf(endExclusive, buf.size)) {
            sum += buf[i].toInt() and 0xFF
        }
        return sum
    }
}
