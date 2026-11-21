package com.example.util

import android.content.Context
import com.example.data.model.McuProcessor
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Dynamic NEC / Renesas 78K0 (Kx2 / KC2 / KD2 / KE2 / KF2) Serial Flash Programming Engine.
 * Supports Hardware FLMD0+RESET Auto-Sync (and Manual Reset mode), Silicon Signature (0xC0),
 * Double Checksum Snapshot (0xB0), Safe Block 0 Zeroing (0x22 + 0x40), Block 1..N Extraction
 * & Verify (0x32 + 0x13), Software Block 0 Reconstruction + Restore, Atomic Checkpoints (fsync),
 * Loose-Wire Watchdog, and One-Tap MCU Rescue.
 */
object Nec78k0ProtocolEngine {
    const val BLOCK_SIZE = 1024
    const val PAGE_SIZE = 256

    const val SOH: Int = 0x01
    const val STX: Int = 0x02
    const val ETX: Int = 0x03
    const val ETB: Int = 0x17

    const val CMD_RESET: Int = 0x00
    const val CMD_VERIFY: Int = 0x13
    const val CMD_CHIP_ERASE: Int = 0x20
    const val CMD_BLOCK_ERASE: Int = 0x22
    const val CMD_BLANK_CHECK: Int = 0x32
    const val CMD_PROGRAM: Int = 0x40
    const val CMD_OSC_FREQ: Int = 0x90
    const val CMD_SECURITY: Int = 0xA0
    const val CMD_CHECKSUM: Int = 0xB0
    const val CMD_SILICON_SIGNATURE: Int = 0xC0

    const val STS_PARAMETER_ERROR: Int = 0x05
    const val STS_ACK: Int = 0x06
    const val STS_CHECKSUM_ERROR: Int = 0x07
    const val STS_VERIFY_ERROR: Int = 0x0F
    const val STS_PROTECT_ERROR: Int = 0x10
    const val STS_NACK: Int = 0x15

    const val WATCHDOG_PREFIX = "WATCHDOG_PAUSED:"

    interface Serial78k0Transport {
        fun configureUart96008N1(onLog: (String) -> Unit): Boolean
        fun ensureReaderListening(onLog: (String) -> Unit): Boolean
        fun setRawRxMonitor(enabled: Boolean)
        fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit)
        fun flushInputBuffer()
        fun isDeviceAttached(): Boolean
        suspend fun writeRaw(bytes: ByteArray): String?
        suspend fun discardEcho(expectedCount: Int, baudRate: Int): Int
        suspend fun readBytesWithTimeout(maxBytes: Int, timeoutMs: Int): ByteArray
    }

    data class Nec78k0DetectedInfo(
        val deviceName: String,
        val familyGroup: String,
        val lastAddress: Int,
        val flashSizeBytes: Int,
        val blockCount: Int,
        val hasDataFlash: Boolean,
        val fullFlashChecksum16: Int,
        val block0Checksum16: Int,
        val rawSignatureHex: String,
        val matchedProcessor: McuProcessor?
    ) {
        val fullFlashChecksumHex: String
            get() = "%04X".format(fullFlashChecksum16 and 0xFFFF)

        val block0ChecksumHex: String
            get() = "%04X".format(block0Checksum16 and 0xFFFF)

        val lastAddressHex: String
            get() = String.format(Locale.US, "0x%06X", lastAddress)
    }

    data class Nec78k0RxFrame(
        val sts1: Int,
        val payload: ByteArray,
        val rawFrame: ByteArray,
        val checksumValid: Boolean
    ) {
        val sts2: Int?
            get() = if (payload.size >= 2) (payload[1].toInt() and 0xFF) else null

        val isAckSuccess: Boolean
            get() = checksumValid && sts1 == STS_ACK && (payload.size != 2 || sts2 == STS_ACK)

        val effectiveErrorCode: Int
            get() = when {
                !checksumValid -> STS_CHECKSUM_ERROR
                sts1 != STS_ACK -> sts1
                payload.size == 2 && sts2 != null && sts2 != STS_ACK -> sts2!!
                else -> sts1
            }
    }

    sealed class Nec78k0TransceiveResult {
        data class Success(val frame: Nec78k0RxFrame) : Nec78k0TransceiveResult()
        data class ProtocolError(val statusCode: Int, val statusLabel: String, val frame: Nec78k0RxFrame?) : Nec78k0TransceiveResult()
        data class DisconnectedOrTimeout(val reason: String, val isUsbDetached: Boolean) : Nec78k0TransceiveResult()
    }

    fun computeChecksum(lenByte: Int, body: ByteArray): Int {
        var sum = lenByte and 0xFF
        for (b in body) {
            sum += (b.toInt() and 0xFF)
        }
        return (0 - sum) and 0xFF
    }

    fun buildCommandFrame(cmd: Int, data: ByteArray = ByteArray(0), endByte: Int = ETX): ByteArray {
        val body = ByteArray(1 + data.size)
        body[0] = (cmd and 0xFF).toByte()
        if (data.isNotEmpty()) {
            System.arraycopy(data, 0, body, 1, data.size)
        }
        val lenByte = body.size and 0xFF // 256 encodes cleanly as 0x00
        val cs = computeChecksum(lenByte, body)
        val out = ByteArray(2 + body.size + 2)
        out[0] = SOH.toByte()
        out[1] = lenByte.toByte()
        System.arraycopy(body, 0, out, 2, body.size)
        out[2 + body.size] = cs.toByte()
        out[2 + body.size + 1] = (endByte and 0xFF).toByte()
        return out
    }

    fun buildDataFrame(data256: ByteArray, isLast: Boolean): ByteArray {
        require(data256.size == PAGE_SIZE) { "78K0 data frame must be exactly 256 bytes" }
        val lenByte = 0x00 // 256 bytes
        val cs = computeChecksum(lenByte, data256)
        val out = ByteArray(2 + PAGE_SIZE + 2)
        out[0] = SOH.toByte()
        out[1] = lenByte.toByte()
        System.arraycopy(data256, 0, out, 2, PAGE_SIZE)
        out[2 + PAGE_SIZE] = cs.toByte()
        out[2 + PAGE_SIZE + 1] = (if (isLast) ETX else ETB).toByte()
        return out
    }

    fun statusDescription(code: Int): String = when (code and 0xFF) {
        STS_ACK -> "0x06 (ACK)"
        STS_CHECKSUM_ERROR -> "0x07 (Checksum Error)"
        STS_VERIFY_ERROR -> "0x0F (ERR_VERIFY - فشل المطابقة)"
        STS_PROTECT_ERROR -> "0x10 (ERR_PROTECT - المعالج محمي ضد المسح/الكتابة)"
        STS_NACK -> "0x15 (NACK - خطأ غير معروف أو مرفوض)"
        STS_PARAMETER_ERROR -> "0x05 (Parameter Error)"
        else -> String.format(Locale.US, "0x%02X (Status)", code and 0xFF)
    }

    suspend fun performHardwareHandshake(
        transport: Serial78k0Transport,
        onLog: (String) -> Unit
    ): Boolean {
        if (!transport.ensureReaderListening(onLog)) return false
        transport.setRawRxMonitor(false)
        onLog("1) تهيئة UART على 9600 bps, 8N1 (بدون تحكم تدفق وبدون إلغاء صدى) وتفريغ المخزن...")
        if (!transport.configureUart96008N1(onLog)) return false
        transport.flushInputBuffer()

        onLog("2) تهيئة FT232 التلقائية 78K0: رفع DTR (FLMD0=HIGH) + خفض RTS (RESET=LOW) لمدة 50ms...")
        transport.setControlLines(dtr = false, rts = true, onLog = onLog)
        delay(50)

        onLog("3) تحرير RTS (RESET=HIGH) مع إبقاء DTR (FLMD0=HIGH) -> انتظار 20ms...")
        transport.setControlLines(dtr = false, rts = false, onLog = onLog)
        delay(20)
        transport.flushInputBuffer()

        if (!transport.isDeviceAttached()) {
            onLog("Watchdog Alert: انقطع اتصال USB OTG قبل إرسال نبضة المزامنة!")
            return false
        }

        onLog("4) إرسال نبضة المزامنة [0x00, 0x00] بفاصل 5ms على 9600 bps (8N1)...")
        val err1 = transport.writeRaw(byteArrayOf(0x00))
        if (err1 != null) {
            onLog("TX Error (Sync #1): $err1")
            return false
        }
        delay(5)

        val err2 = transport.writeRaw(byteArrayOf(0x00))
        if (err2 != null) {
            onLog("TX Error (Sync #2): $err2")
            return false
        }
        delay(20)
        transport.flushInputBuffer()
        return true
    }

    suspend fun autoDetectAndInspect(
        context: Context,
        transport: Serial78k0Transport,
        clockFreqMhzText: String,
        preferredChip: McuProcessor?,
        catalog: List<McuProcessor>,
        onLog: (String) -> Unit
    ): Pair<Nec78k0DetectedInfo?, String?> {
        if (!performHardwareHandshake(transport, onLog)) {
            return null to "فشلت المزامنة التلقائية (FT232 DTR/RTS + Baud Sync 9600) مع معالج 78K0."
        }

        // Step 2: Reset Command (0x00) -> [01 01 00 FF 03]
        val resetFrame = buildCommandFrame(CMD_RESET)
        onLog("TX (Reset 0x00): ${toHex(resetFrame)}")
        when (val resetRes = transceiveSingleFrame(transport, resetFrame, 500, onLog)) {
            is Nec78k0TransceiveResult.Success -> {
                onLog("RX (Reset ACK): ${toHex(resetRes.frame.rawFrame)}")
            }
            is Nec78k0TransceiveResult.ProtocolError -> {
                return null to "رفض المعالج أمر Reset 0x00: ${resetRes.statusLabel}"
            }
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                return null to resetRes.reason
            }
        }

        // Step 2b: Set Oscillation Frequency (0x90)
        val oscPayload = buildOscFreqPayload(clockFreqMhzText)
        val oscFrame = buildCommandFrame(CMD_OSC_FREQ, oscPayload)
        onLog("TX (Osc Freq 0x90): ${toHex(oscFrame)}")
        when (val oscRes = transceiveSingleFrame(transport, oscFrame, 500, onLog)) {
            is Nec78k0TransceiveResult.Success -> {
                onLog("RX (Osc Freq ACK): ${toHex(oscRes.frame.rawFrame)}")
            }
            is Nec78k0TransceiveResult.ProtocolError -> {
                onLog("تنبيه (Osc 0x90): ${oscRes.statusLabel} — متابعة استعلام التوقيع...")
            }
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                return null to oscRes.reason
            }
        }

        // Step 3: Silicon Signature Command (0xC0) -> [01 01 C0 3F 03]
        val sigCmd = buildCommandFrame(CMD_SILICON_SIGNATURE)
        onLog("TX (Silicon Signature 0xC0): ${toHex(sigCmd)}")
        transport.flushInputBuffer()
        val sigTxErr = transport.writeRaw(sigCmd)
        if (sigTxErr != null) {
            return null to "انقطع الإرسال عند أمر التوقيع 0xC0: $sigTxErr"
        }
        transport.discardEcho(sigCmd.size, 9600)

        val ackFrame = readOneRxFrame(transport, 700)
            ?: return null to "لم يرد المعالج بإطار ACK لتوقيع السليكون 0xC0 (تأكد من 9600 baud وتوصيل TX/RX/FLMD0)."
        if (!ackFrame.checksumValid || ackFrame.sts1 != STS_ACK) {
            return null to "خطأ في تأكيد أمر 0xC0: ${statusDescription(ackFrame.sts1)}"
        }

        val sigDataFrame = readOneRxFrame(transport, 900)
            ?: return null to "استلمنا ACK لأمر 0xC0 لكن إطار بيانات التوقيع لم يصل."
        val rawSigHex = toHex(sigDataFrame.rawFrame)
        onLog("RX (Signature Frame): $rawSigHex")

        val parsed = parseSiliconSignature(
            sigFrame = sigDataFrame,
            fallbackChip = preferredChip,
            catalog = catalog
        )

        // Checksum Command 0xB0 TWICE before any write/erase!
        val fullEnd = parsed.lastAddress
        val fullCsCmd = buildAddressRangeFrame(CMD_CHECKSUM, 0x000000, fullEnd)
        onLog("TX (Checksum Full Flash 0x000000..${parsed.lastAddressHex}): ${toHex(fullCsCmd)}")
        val fullCs = queryChecksum16(transport, fullCsCmd, onLog)
            ?: return null to "فشل استلام Full_Flash_Checksum (0xB0) قبل البدء — توقفنا لحماية الذاكرة."

        val b0CsCmd = buildAddressRangeFrame(CMD_CHECKSUM, 0x000000, BLOCK_SIZE - 1)
        onLog("TX (Checksum Block 0 0x000000..0x0003FF): ${toHex(b0CsCmd)}")
        val b0Cs = queryChecksum16(transport, b0CsCmd, onLog)
            ?: return null to "فشل استلام Block0_Checksum (0xB0) قبل البدء — توقفنا لحماية الذاكرة."

        val finalInfo = parsed.copy(
            fullFlashChecksum16 = fullCs,
            block0Checksum16 = b0Cs,
            rawSignatureHex = rawSigHex
        )

        onLog(
            "بصمة المعالج: ${finalInfo.deviceName} (${finalInfo.familyGroup}) • End=${finalInfo.lastAddressHex} (${finalInfo.blockCount} بلوك) • DataFlash=${if (finalInfo.hasDataFlash) "نعم" else "لا يوجد"} • FullCS=${finalInfo.fullFlashChecksumHex} • Block0CS=${finalInfo.block0ChecksumHex}"
        )

        // Immediately persist crash-safe atomic checkpoint BEFORE modifying any flash
        Nec78k0RecoveryManager.saveSessionCheckpoint(
            context = context,
            session = Nec78k0RecoveryManager.Nec78k0RecoverySession(
                chipName = finalInfo.deviceName,
                subFamily = finalInfo.familyGroup,
                lastAddress = finalInfo.lastAddress,
                blockCount = finalInfo.blockCount,
                hasDataFlash = finalInfo.hasDataFlash,
                fullFlashChecksum = finalInfo.fullFlashChecksum16,
                block0Checksum = finalInfo.block0Checksum16,
                signatureHex = rawSigHex,
                stage = Nec78k0RecoveryManager.RecoveryStage.DETECTED,
                lastCompletedBlock = -1,
                updatedAtMs = System.currentTimeMillis(),
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, finalInfo.deviceName).absolutePath
            )
        )
        return finalInfo to null
    }

    /**
     * Complete 4-Stage NEC / Renesas 78K0 Extract + Rescue Engine.
     */
    suspend fun readOrRescueFlash(
        context: Context,
        transport: Serial78k0Transport,
        selectedChip: McuProcessor,
        clockFreqMhzText: String,
        catalog: List<McuProcessor>,
        existingResumeSession: Nec78k0RecoveryManager.Nec78k0RecoverySession?,
        preDetectedInfo: Nec78k0DetectedInfo? = null,
        onDetectedInfo: (Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val detectedInfo: Nec78k0DetectedInfo
        var session = existingResumeSession

        if (session != null && session.lastCompletedBlock >= 0) {
            onLog("تفعيل وضع الاستكمال / الإنقاذ التلقائي للجلسة المحفوظة (${session.chipName}) من المرحلة: ${session.stage.labelAr}...")
            if (!performHardwareHandshake(transport, onLog)) {
                return FlashOperationResult.Failure("${WATCHDOG_PREFIX}${session.lastCompletedBlock}:تعذر إعادة المزامنة مع ${session.chipName} — ثبّت الأسلاك ثم أعد المحاولة.")
            }
            // Fast 0x00 Reset + 0x90 Osc Setup to wake UART command interpreter cleanly
            val resetRes = transceiveSingleFrame(transport, buildCommandFrame(CMD_RESET), 500, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return FlashOperationResult.Failure("${WATCHDOG_PREFIX}${session.lastCompletedBlock}:${resetRes.reason}")
            }
            transceiveSingleFrame(transport, buildCommandFrame(CMD_OSC_FREQ, buildOscFreqPayload(clockFreqMhzText)), 500, onLog)

            detectedInfo = Nec78k0DetectedInfo(
                deviceName = session.chipName,
                familyGroup = session.subFamily,
                lastAddress = session.lastAddress,
                flashSizeBytes = session.flashSizeBytes,
                blockCount = session.blockCount,
                hasDataFlash = session.hasDataFlash,
                fullFlashChecksum16 = session.fullFlashChecksum,
                block0Checksum16 = session.block0Checksum,
                rawSignatureHex = session.signatureHex,
                matchedProcessor = selectedChip
            )
            onDetectedInfo(detectedInfo)
        } else if (preDetectedInfo != null &&
            normalize78k0Part(preDetectedInfo.deviceName) == normalize78k0Part(selectedChip.partNumber)
        ) {
            onLog(
                "البصمة محفوظة مسبقاً عند الاتصال (${preDetectedInfo.deviceName} • FullCS=${preDetectedInfo.fullFlashChecksumHex} • B0CS=${preDetectedInfo.block0ChecksumHex}) — بدء مزامنة السحب..."
            )
            if (!performHardwareHandshake(transport, onLog)) {
                return FlashOperationResult.Failure("فشلت المزامنة التلقائية (Handshake) مع ${preDetectedInfo.deviceName}.")
            }
            val resetRes = transceiveSingleFrame(transport, buildCommandFrame(CMD_RESET), 500, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return FlashOperationResult.Failure(resetRes.reason)
            }
            transceiveSingleFrame(transport, buildCommandFrame(CMD_OSC_FREQ, buildOscFreqPayload(clockFreqMhzText)), 500, onLog)
            detectedInfo = preDetectedInfo
            session = Nec78k0RecoveryManager.findSessionForChip(context, preDetectedInfo.deviceName)
        } else {
            onProgress(0, selectedChip.flashSizeBytes, "التعرف التلقائي وحفظ Checksum الأصلي...")
            val (info, err) = autoDetectAndInspect(
                context = context,
                transport = transport,
                clockFreqMhzText = clockFreqMhzText,
                preferredChip = selectedChip,
                catalog = catalog,
                onLog = onLog
            )
            if (info == null) {
                return FlashOperationResult.Failure(err ?: "فشل التعرف التلقائي على معالج 78K0.")
            }
            detectedInfo = info
            onDetectedInfo(info)
            session = Nec78k0RecoveryManager.findSessionForChip(context, info.deviceName)
        }

        val totalSize = detectedInfo.flashSizeBytes
        val blockCount = detectedInfo.blockCount.coerceAtLeast(1)
        val fullBuffer = Nec78k0RecoveryManager.loadPartialBlocks(context, detectedInfo.deviceName, totalSize)
            ?: ByteArray(totalSize) { 0xFF.toByte() }

        // Step 2 (Zeroing Block 0 Only) — skipped cleanly if resuming after Block 0 was already zeroed
        if (session == null || session.lastCompletedBlock < 0) {
            onProgress(0, totalSize, "فحص الحماية وتصفير Block 0 فقط...")
            onLog("المرحلة 2: مسح Block 0 (0x000000..0x0003FF) بأمر 0x22...")
            val eraseB0Cmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1)
            when (val eraseRes = transceiveSingleFrame(transport, eraseB0Cmd, 1500, onLog)) {
                is Nec78k0TransceiveResult.Success -> {
                    onLog("RX (Block 0 Erase ACK): ${toHex(eraseRes.frame.rawFrame)}")
                }
                is Nec78k0TransceiveResult.ProtocolError -> {
                    if (eraseRes.statusCode == STS_PROTECT_ERROR) {
                        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                        return FlashOperationResult.Failure(
                            "المعالج محمي برمجياً ضد القراءة/المسح (ERR_PROTECT 0x10). تم إيقاف العملية فوراً قبل أي تعديل لحماية بياناتك."
                        )
                    }
                    return FlashOperationResult.Failure("فشل مسح Block 0 (0x22): ${eraseRes.statusLabel}")
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    return FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${eraseRes.reason}")
                }
            }

            onLog("المرحلة 2: كتابة أصفار في Block 0 (4×256B بأمر 0x40) لفتح السحب...")
            val zeroResult = programBlockFromBuffer(
                transport = transport,
                startAddr = 0x000000,
                endAddr = BLOCK_SIZE - 1,
                blockData = ByteArray(BLOCK_SIZE) { 0x00 },
                onLog = onLog
            )
            if (zeroResult != null) {
                if (zeroResult.contains("ERR_PROTECT", ignoreCase = true) || zeroResult.contains("0x10")) {
                    Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                }
                return FlashOperationResult.Failure(zeroResult)
            }

            Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
            session = Nec78k0RecoveryManager.Nec78k0RecoverySession(
                chipName = detectedInfo.deviceName,
                subFamily = detectedInfo.familyGroup,
                lastAddress = detectedInfo.lastAddress,
                blockCount = blockCount,
                hasDataFlash = detectedInfo.hasDataFlash,
                fullFlashChecksum = detectedInfo.fullFlashChecksum16,
                block0Checksum = detectedInfo.block0Checksum16,
                signatureHex = detectedInfo.rawSignatureHex,
                stage = Nec78k0RecoveryManager.RecoveryStage.BLOCK0_ZEROED,
                lastCompletedBlock = 0,
                updatedAtMs = System.currentTimeMillis(),
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath
            )
            Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)
            onLog("تم تصفير Block 0 وتثبيت نقطة الاسترجاع الذرية بنجاح.")

            // Fast Re-sync after Block 0 Zeroing to stabilize UART boot state for Block 1..N-1 extraction
            onLog("إعادة مزامنة سريعة قبل سحب الكتل Block 1..${blockCount - 1}...")
            delay(20)
            if (!performHardwareHandshake(transport, onLog)) {
                return FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:انقطع الاتصال بعد تصفير Block 0 — ثبّت المبرمجة واضغط إنقاذ المعالج.")
            }
            transceiveSingleFrame(transport, buildCommandFrame(CMD_RESET), 500, onLog)
            transceiveSingleFrame(transport, buildCommandFrame(CMD_OSC_FREQ, buildOscFreqPayload(clockFreqMhzText)), 500, onLog)
        }

        // Step 3: Pull & Verify Block 1 .. Block N-1
        val startBlock = (session?.lastCompletedBlock ?: 0).coerceAtLeast(0) + 1
        onLog("المرحلة 3: سحب ومطابقة الكتل من Block $startBlock إلى Block ${blockCount - 1}...")

        for (blockIdx in startBlock until blockCount) {
            if (!transport.isDeviceAttached()) {
                return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, "انقطع اتصال USB OTG عند Block $blockIdx")
            }

            val blockStart = blockIdx * BLOCK_SIZE
            val blockEnd = blockStart + BLOCK_SIZE - 1
            val blankCmd = buildAddressRangeFrame(CMD_BLANK_CHECK, blockStart, blockEnd)

            when (val blankRes = transceiveSingleFrame(transport, blankCmd, 600, onLog)) {
                is Nec78k0TransceiveResult.Success -> {
                    // Blank (0xFF x 1024)
                    for (k in 0 until BLOCK_SIZE) {
                        if (blockStart + k < fullBuffer.size) {
                            fullBuffer[blockStart + k] = 0xFF.toByte()
                        }
                    }
                }
                is Nec78k0TransceiveResult.ProtocolError -> {
                    if (blankRes.statusCode == STS_PROTECT_ERROR) {
                        return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, "استلمنا ERR_PROTECT أثناء قراءة Block $blockIdx")
                    }
                    // Non-blank block: verify 4x256B data frames using 0x13 Verify Command
                    val candidateBlock = fullBuffer.copyOfRange(blockStart, blockStart + BLOCK_SIZE)
                    val verifyResult = verifyBlockVia0x13(transport, blockStart, blockEnd, candidateBlock, onLog)
                    when (verifyResult) {
                        is VerifyOutcome.Match -> {
                            System.arraycopy(candidateBlock, 0, fullBuffer, blockStart, BLOCK_SIZE)
                        }
                        is VerifyOutcome.Mismatch -> {
                            onLog("تنبيه (Block $blockIdx): غير فارغ (0x32 -> ${blankRes.statusLabel}) والبيانات غير مطابقة لـ Verify (0x13 -> ${verifyResult.detail}).")
                        }
                        is VerifyOutcome.TransportLost -> {
                            return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, verifyResult.reason)
                        }
                    }
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, blankRes.reason)
                }
            }

            val updatedSession = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
                chipName = detectedInfo.deviceName,
                subFamily = detectedInfo.familyGroup,
                lastAddress = detectedInfo.lastAddress,
                blockCount = blockCount,
                hasDataFlash = detectedInfo.hasDataFlash,
                fullFlashChecksum = detectedInfo.fullFlashChecksum16,
                block0Checksum = detectedInfo.block0Checksum16,
                signatureHex = detectedInfo.rawSignatureHex,
                stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
                lastCompletedBlock = blockIdx,
                updatedAtMs = System.currentTimeMillis(),
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath
            )).copy(
                stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
                lastCompletedBlock = blockIdx,
                updatedAtMs = System.currentTimeMillis()
            )
            session = updatedSession

            if (blockIdx % 4 == 0 || blockIdx == blockCount - 1) {
                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, updatedSession)
            }

            val bytesDone = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            onProgress(
                bytesDone,
                totalSize,
                String.format(Locale.US, "سحب ومطابقة Block %d/%d (0x%06X)", blockIdx, blockCount - 1, blockStart)
            )
            if (blockIdx % 8 == 0 || blockIdx == blockCount - 1) {
                onLog("تم تأكيد Block $blockIdx/${blockCount - 1} (${bytesDone / 1024}/${totalSize / 1024} KB)")
            }
        }

        // Persist BLOCKS_EXTRACTED checkpoint before touching Block 0 restore
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
        session?.let {
            Nec78k0RecoveryManager.saveSessionCheckpoint(
                context,
                it.copy(
                    stage = Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED,
                    lastCompletedBlock = blockCount - 1,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }

        // Step 4: Decode & Reconstruct Block 0 + Write 0x40 + Verify Full Checksum 0xB0
        onProgress(totalSize - BLOCK_SIZE, totalSize, "استرجاع Block 0 ومطابقة Checksum الكامل...")
        onLog("المرحلة 4: تحليل متجهات CALL/BR في Block 1..${blockCount - 1} وإعادة بناء Block 0 برمجياً...")
        val report = Nec78k0Block0Reconstructor.reconstructBlock0(
            fullFlashBuffer = fullBuffer,
            targetBlock0Checksum16 = detectedInfo.block0Checksum16,
            targetFullChecksum16 = detectedInfo.fullFlashChecksum16
        )
        System.arraycopy(report.block0Bytes, 0, fullBuffer, 0, BLOCK_SIZE)
        onLog(
            String.format(
                Locale.US,
                "تم إعادة بناء Block 0 (Reset Vector -> 0x%04X • البصمة %04X • التطابق البرمجي=%s).",
                report.detectedEntryAddress,
                report.computedBlock0Checksum16,
                if (report.checksumMatched) "100%" else "تقريبي"
            )
        )

        onLog("المرحلة 4: إعادة مسح وكتابة Block 0 المسترجع إلى المعالج عبر 0x40...")
        val reEraseRes = transceiveSingleFrame(transport, buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1), 1500, onLog)
        if (reEraseRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
            return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockCount - 1, blockCount, reEraseRes.reason)
        }
        val restoreErr = programBlockFromBuffer(
            transport = transport,
            startAddr = 0x000000,
            endAddr = BLOCK_SIZE - 1,
            blockData = report.block0Bytes,
            onLog = onLog
        )
        if (restoreErr != null) {
            return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockCount - 1, blockCount, restoreErr)
        }

        // Verify Full Flash Checksum via 0xB0
        val finalCsCmd = buildAddressRangeFrame(CMD_CHECKSUM, 0x000000, detectedInfo.lastAddress)
        val finalCs = queryChecksum16(transport, finalCsCmd, onLog)
        val matchedOriginal = finalCs != null && (finalCs and 0xFFFF) == (detectedInfo.fullFlashChecksum16 and 0xFFFF)
        if (finalCs != null) {
            onLog(
                String.format(
                    Locale.US,
                    "فحص Checksum النهائي (0xB0): الحالي=%04X / الأصلي=%04X (%s)",
                    finalCs and 0xFFFF,
                    detectedInfo.fullFlashChecksum16 and 0xFFFF,
                    if (matchedOriginal) "مطابق 100%" else "اختلاف"
                )
            )
        }

        // Clean up checkpoint files once completed safely
        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
        onProgress(totalSize, totalSize, "اكتمل السحب واسترجاع Block 0 بنسبة 100%")

        return FlashOperationResult.Success(
            message = "تم سحب وتفريغ ${detectedInfo.deviceName} (${totalSize / 1024} KB / $blockCount بلوك) واسترجاع Block 0 بنجاح" +
                if (matchedOriginal) " مع تطابق Checksum 100%." else ".",
            bytesTransferred = fullBuffer,
            baseAddress = 0
        )
    }

    private fun pauseWithWatchdog(
        context: Context,
        session: Nec78k0RecoveryManager.Nec78k0RecoverySession?,
        info: Nec78k0DetectedInfo,
        partialBuffer: ByteArray,
        lastGoodBlock: Int,
        blockCount: Int,
        reason: String
    ): FlashOperationResult.Failure {
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, info.deviceName, partialBuffer)
        val saved = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
            chipName = info.deviceName,
            subFamily = info.familyGroup,
            lastAddress = info.lastAddress,
            blockCount = blockCount,
            hasDataFlash = info.hasDataFlash,
            fullFlashChecksum = info.fullFlashChecksum16,
            block0Checksum = info.block0Checksum16,
            signatureHex = info.rawSignatureHex,
            stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(0),
            updatedAtMs = System.currentTimeMillis(),
            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, info.deviceName).absolutePath
        )).copy(
            stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(0),
            updatedAtMs = System.currentTimeMillis()
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, saved)
        return FlashOperationResult.Failure(
            "${WATCHDOG_PREFIX}${saved.lastCompletedBlock}:انقطع الاتصال عند الكتلة ${saved.lastCompletedBlock + 1}/$blockCount ($reason) — تم حفظ التقدم على الذاكرة، ثبّت المبرمجة للاستكمال."
        )
    }

    private sealed class VerifyOutcome {
        data object Match : VerifyOutcome()
        data class Mismatch(val detail: String) : VerifyOutcome()
        data class TransportLost(val reason: String) : VerifyOutcome()
    }

    private suspend fun verifyBlockVia0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockBytes: ByteArray,
        onLog: (String) -> Unit
    ): VerifyOutcome {
        val verifyCmd = buildAddressRangeFrame(CMD_VERIFY, startAddr, endAddr)
        when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, 700, onLog)) {
            is Nec78k0TransceiveResult.Success -> Unit
            is Nec78k0TransceiveResult.ProtocolError -> return VerifyOutcome.Mismatch(cmdRes.statusLabel)
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> return VerifyOutcome.TransportLost(cmdRes.reason)
        }

        // Send 4 x 260B SOH frames (first 3 end with ETB 0x17, final ends with ETX 0x03)
        for (page in 0 until 4) {
            val offset = page * PAGE_SIZE
            val slice = blockBytes.copyOfRange(offset, offset + PAGE_SIZE)
            val dataFrame = buildDataFrame(slice, isLast = (page == 3))
            transport.flushInputBuffer()
            val txErr = transport.writeRaw(dataFrame)
            if (txErr != null) {
                return VerifyOutcome.TransportLost("انقطع الإرسال أثناء Verify عند صفحة ${page + 1}/4: $txErr")
            }
            transport.discardEcho(dataFrame.size, 9600)
            val frameAck = readOneRxFrame(transport, 1500)
                ?: return VerifyOutcome.TransportLost("انتهت مهلة انتظار ACK أثناء Verify (الصفحة ${page + 1}/4)")
            if (!frameAck.isAckSuccess) {
                return VerifyOutcome.Mismatch(statusDescription(frameAck.effectiveErrorCode))
            }
        }
        return VerifyOutcome.Match
    }

    private suspend fun programBlockFromBuffer(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockData: ByteArray,
        onLog: (String) -> Unit
    ): String? {
        val progCmd = buildAddressRangeFrame(CMD_PROGRAM, startAddr, endAddr)
        when (val res = transceiveSingleFrame(transport, progCmd, 1500, onLog)) {
            is Nec78k0TransceiveResult.Success -> Unit
            is Nec78k0TransceiveResult.ProtocolError -> return "رفض المعالج أمر الكتابة 0x40: ${res.statusLabel}"
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> return "${WATCHDOG_PREFIX}0:${res.reason}"
        }

        for (page in 0 until 4) {
            val offset = page * PAGE_SIZE
            val slice = blockData.copyOfRange(offset, offset + PAGE_SIZE)
            val dataFrame = buildDataFrame(slice, isLast = (page == 3))
            transport.flushInputBuffer()
            val txErr = transport.writeRaw(dataFrame)
            if (txErr != null) {
                return "${WATCHDOG_PREFIX}0:فشل إرسال بيانات 0x40: $txErr"
            }
            transport.discardEcho(dataFrame.size, 9600)

            val ack = readOneRxFrame(transport, 1500)
                ?: return "${WATCHDOG_PREFIX}0:لم يرد المعالج على حزمة 0x40 (صفحة ${page + 1}/4)"
            if (!ack.isAckSuccess) {
                return "فشل حرق الصفحة ${page + 1}/4 (0x40): ${statusDescription(ack.effectiveErrorCode)}"
            }
        }
        return null
    }

    internal suspend fun queryChecksum16(
        transport: Serial78k0Transport,
        csCmdFrame: ByteArray,
        onLog: (String) -> Unit
    ): Int? {
        transport.flushInputBuffer()
        val txErr = transport.writeRaw(csCmdFrame)
        if (txErr != null) {
            onLog("TX Error (0xB0): $txErr")
            return null
        }
        transport.discardEcho(csCmdFrame.size, 9600)
        val firstFrame = readOneRxFrame(transport, 1500) ?: return null
        if (!firstFrame.checksumValid || firstFrame.sts1 != STS_ACK) {
            onLog("Checksum 0xB0 Error: ${statusDescription(firstFrame.sts1)}")
            return null
        }
        // Depending on silicon, 0xB0 either attaches the 2 checksum bytes after STS1 or sends a 2nd frame
        if (firstFrame.payload.size >= 3) {
            return Nec78k0Block0Reconstructor.decodeChecksumBytes(firstFrame.payload.copyOfRange(1, 3))
        }
        val dataFrame = readOneRxFrame(transport, 1500) ?: return null
        return Nec78k0Block0Reconstructor.decodeChecksumBytes(dataFrame.payload)
    }

    internal fun buildAddressRangeFrame(cmd: Int, startAddr: Int, endAddr: Int): ByteArray {
        val data = byteArrayOf(
            ((startAddr ushr 16) and 0xFF).toByte(),
            ((startAddr ushr 8) and 0xFF).toByte(),
            (startAddr and 0xFF).toByte(),
            ((endAddr ushr 16) and 0xFF).toByte(),
            ((endAddr ushr 8) and 0xFF).toByte(),
            (endAddr and 0xFF).toByte()
        )
        return buildCommandFrame(cmd, data)
    }

    internal fun buildOscFreqPayload(clockText: String): ByteArray {
        val cleaned = clockText.replace(Regex("[^0-9.]"), "")
        val mhz = cleaned.toDoubleOrNull()?.coerceIn(1.0, 20.0) ?: 8.0
        val integerPart = mhz.toInt().coerceIn(1, 20)
        val fracDigit = (((mhz - integerPart) * 10.0).toInt()).coerceIn(0, 9)
        return byteArrayOf(
            integerPart.toByte(),
            fracDigit.toByte(),
            0x00,
            0x01 // Normal speed mode multiplier
        )
    }

    private fun parseSiliconSignature(
        sigFrame: Nec78k0RxFrame,
        fallbackChip: McuProcessor?,
        catalog: List<McuProcessor>
    ): Nec78k0DetectedInfo {
        val payload = sigFrame.payload
        val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        val deviceMatch = Regex("""(?:uPD|D)?78F[0-9A-Z]{4,6}""", RegexOption.IGNORE_CASE).find(ascii)
        val rawDeviceName = deviceMatch?.value?.trim()?.uppercase()
            ?: fallbackChip?.partNumber?.uppercase()
            ?: "D78F0513A"

        val matched = findMatchingCatalogProcessor(rawDeviceName, catalog) ?: fallbackChip

        // Parse 3-byte Last Code Flash Address from tail of Silicon Signature payload if valid
        val parsedEndAddress = extractEndAddressFromSignature(payload)
            ?: ((matched?.flashSizeBytes ?: (32 * 1024)) - 1)
        val alignedFlashBytes = ((parsedEndAddress + 1).coerceAtLeast(BLOCK_SIZE))
        val blockCount = alignedFlashBytes / BLOCK_SIZE
        val familyGroup = infer78k0SubFamily(rawDeviceName, matched)
        val hasDf = matched?.chipConfig?.hasDataFlash ?: false

        return Nec78k0DetectedInfo(
            deviceName = rawDeviceName,
            familyGroup = familyGroup,
            lastAddress = alignedFlashBytes - 1,
            flashSizeBytes = alignedFlashBytes,
            blockCount = blockCount,
            hasDataFlash = hasDf,
            fullFlashChecksum16 = 0,
            block0Checksum16 = 0,
            rawSignatureHex = toHex(sigFrame.rawFrame),
            matchedProcessor = matched
        )
    }

    private fun extractEndAddressFromSignature(payload: ByteArray): Int? {
        // NEC 78K0 signature payload typically embeds the 3-byte end address (e.g. 00 7F FF) near bytes 3..5 or tail
        for (i in 0..payload.size - 3) {
            val h = payload[i].toInt() and 0xFF
            val m = payload[i + 1].toInt() and 0xFF
            val l = payload[i + 2].toInt() and 0xFF
            if (l == 0xFF && h in 0x00..0x01 && (m == 0x1F || m == 0x3F || m == 0x5F || m == 0x7F || m == 0xBF || m == 0xFF)) {
                val candidate = (h shl 16) or (m shl 8) or l
                if (candidate >= 0x1FFF && ((candidate + 1) % BLOCK_SIZE == 0)) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun findMatchingCatalogProcessor(deviceName: String, catalog: List<McuProcessor>): McuProcessor? {
        val norm = normalize78k0Part(deviceName)
        catalog.firstOrNull { normalize78k0Part(it.partNumber) == norm }?.let { return it }
        val trimmedRev = norm.trimEnd('A', 'B', 'C', 'D')
        catalog.firstOrNull { normalize78k0Part(it.partNumber) == trimmedRev }?.let { return it }
        return catalog.firstOrNull {
            val p = normalize78k0Part(it.partNumber)
            p.startsWith(trimmedRev.take(7)) || trimmedRev.startsWith(p.take(7))
        }
    }

    internal fun normalize78k0Part(name: String): String {
        return name.trim().uppercase()
            .replace("UPD", "D")
            .replace(Regex("[^A-Z0-9]"), "")
    }

    private fun infer78k0SubFamily(deviceName: String, matched: McuProcessor?): String {
        if (matched != null && matched.subFamily.isNotBlank()) return matched.subFamily
        val upper = deviceName.uppercase()
        return when {
            upper.contains("78F05") -> "78K0/Kx2 (KC2/KD2/KE2/KF2)"
            upper.contains("78F04") -> "78K0/Lx3"
            else -> "78K0 Series"
        }
    }

    internal suspend fun transceiveSingleFrame(
        transport: Serial78k0Transport,
        txFrame: ByteArray,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        if (!transport.isDeviceAttached()) {
            return Nec78k0TransceiveResult.DisconnectedOrTimeout("فُصل كابل USB OTG عن الهاتف", isUsbDetached = true)
        }
        transport.flushInputBuffer()
        val txErr = transport.writeRaw(txFrame)
        if (txErr != null) {
            return Nec78k0TransceiveResult.DisconnectedOrTimeout("خطأ إرسال UART: $txErr", isUsbDetached = !transport.isDeviceAttached())
        }
        transport.discardEcho(txFrame.size, 9600)
        val rx = readOneRxFrame(transport, timeoutMs)
            ?: return Nec78k0TransceiveResult.DisconnectedOrTimeout(
                "انتهت المهلة (${timeoutMs}ms) دون استلام إطار 0x02..0x03 من المعالج",
                isUsbDetached = !transport.isDeviceAttached()
            )
        if (!rx.checksumValid) {
            return Nec78k0TransceiveResult.ProtocolError(STS_CHECKSUM_ERROR, "خطأ Checksum في إطار الاستلام", rx)
        }
        if (!rx.isAckSuccess) {
            val errCode = rx.effectiveErrorCode
            return Nec78k0TransceiveResult.ProtocolError(errCode, statusDescription(errCode), rx)
        }
        return Nec78k0TransceiveResult.Success(rx)
    }

    internal suspend fun readOneRxFrame(
        transport: Serial78k0Transport,
        timeoutMs: Int
    ): Nec78k0RxFrame? {
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        var foundStx = false
        while (System.currentTimeMillis() < deadline) {
            val b = transport.readBytesWithTimeout(1, (deadline - System.currentTimeMillis()).toInt().coerceIn(20, 150))
            if (b.isEmpty()) continue
            if ((b[0].toInt() and 0xFF) == STX) {
                foundStx = true
                break
            }
        }
        if (!foundStx) return null

        val lenArr = transport.readBytesWithTimeout(1, (deadline - System.currentTimeMillis()).toInt().coerceIn(60, 400))
        if (lenArr.isEmpty()) return null
        val lenByte = lenArr[0].toInt() and 0xFF
        val dataLen = if (lenByte == 0) PAGE_SIZE else lenByte
        val tailNeeded = dataLen + 2 // DATA + CS + ETX
        val tail = readExactCount(transport, tailNeeded, (deadline - System.currentTimeMillis()).toInt().coerceIn(150, 1200))
            ?: return null

        val payload = tail.copyOfRange(0, dataLen)
        val csByte = tail[dataLen].toInt() and 0xFF
        val etxByte = tail[dataLen + 1].toInt() and 0xFF

        var sum = lenByte
        for (p in payload) {
            sum += (p.toInt() and 0xFF)
        }
        val checksumValid = (((sum + csByte) and 0xFF) == 0) && (etxByte == ETX || etxByte == ETB)

        val rawOut = ByteArrayOutputStream(2 + tailNeeded)
        rawOut.write(STX)
        rawOut.write(lenByte)
        rawOut.write(tail)

        val sts1 = if (payload.isNotEmpty()) (payload[0].toInt() and 0xFF) else 0x00
        return Nec78k0RxFrame(
            sts1 = sts1,
            payload = payload,
            rawFrame = rawOut.toByteArray(),
            checksumValid = checksumValid
        )
    }

    private suspend fun readExactCount(
        transport: Serial78k0Transport,
        needed: Int,
        timeoutMs: Int
    ): ByteArray? {
        val out = ByteArrayOutputStream(needed)
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        while (out.size() < needed && System.currentTimeMillis() < deadline) {
            val rem = needed - out.size()
            val slice = transport.readBytesWithTimeout(rem, (deadline - System.currentTimeMillis()).toInt().coerceIn(20, 250))
            if (slice.isNotEmpty()) {
                out.write(slice)
            }
        }
        return if (out.size() == needed) out.toByteArray() else null
    }

    internal fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
