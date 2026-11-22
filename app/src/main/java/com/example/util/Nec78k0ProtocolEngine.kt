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
    const val CMD_OSC_FREQ_SET: Int = 0x90
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
    const val RESET_TIMEOUT_MS = 1500
    const val HEADER_ACK_TIMEOUT_MS = 1500
    const val PAGE_ACK_TIMEOUT_MS = 3000
    const val FRAME_PACKET_WRITE_TIMEOUT_MS = 1000
    const val MAX_TIMEOUT_RETRIES = 3

    interface Serial78k0Transport {
        fun configureUart96008N1(onLog: (String) -> Unit): Boolean
        fun ensureReaderListening(onLog: (String) -> Unit): Boolean
        fun setRawRxMonitor(enabled: Boolean)
        fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit)
        fun setDtr(enabled: Boolean, onLog: (String) -> Unit)
        fun setRts(enabled: Boolean, onLog: (String) -> Unit)
        fun getInvertDtrRtsPolarity(): Boolean
        fun setInvertDtrRtsPolarity(inverted: Boolean)
        fun flushInputBuffer()
        fun isDeviceAttached(): Boolean
        suspend fun writeRaw(bytes: ByteArray): String?
        suspend fun writeFrameBytes(
            bytes: ByteArray,
            timeoutMs: Int = FRAME_PACKET_WRITE_TIMEOUT_MS,
            onLog: (String) -> Unit = {}
        ): String?
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
        invertControlPolarity: Boolean = transport.getInvertDtrRtsPolarity(),
        onLog: (String) -> Unit
    ): Boolean {
        if (!transport.ensureReaderListening(onLog)) return false
        onLog("1) تهيئة UART على 9600 bps, 8N1 (بدون تحكم تدفق وبدون إلغاء صدى) وتفريغ المخزن...")
        if (!transport.configureUart96008N1(onLog)) return false
        transport.flushInputBuffer()

        // FT232RL Active-Low hardware mapping:
        // setDTR(false) -> +5V (HIGH) on pin | setDTR(true) -> 0V (LOW) on pin
        // setRTS(false) -> +5V (HIGH) on pin | setRTS(true) -> 0V (LOW) on pin
        val dtrFlmd0High = invertControlPolarity
        val rtsResetLow = !invertControlPolarity
        val rtsResetHigh = invertControlPolarity
        val polarityTag = if (invertControlPolarity) "قطبية معكوسة في الخلفية" else "قطبية FT232 Active-Low"

        onLog("2) ضبط خطوط التحكم ($polarityTag): setDTR($dtrFlmd0High) [FLMD0=+5V] + setRTS($rtsResetLow) [RESET=0V] -> انتظار 100ms...")
        transport.setDtr(dtrFlmd0High, onLog)
        transport.setRts(rtsResetLow, onLog)
        delay(100)

        onLog("3) تحرير الريسيت: setRTS($rtsResetHigh) [RESET=+5V] مع إبقاء setDTR($dtrFlmd0High) [FLMD0=+5V] -> انتظار 150ms لاستقرار المذبذب...")
        transport.setDtr(dtrFlmd0High, onLog)
        transport.setRts(rtsResetHigh, onLog)
        delay(150)
        transport.flushInputBuffer()

        if (!transport.isDeviceAttached()) {
            onLog("Watchdog Alert: انقطع اتصال USB OTG قبل إرسال نبضة المزامنة!")
            return false
        }

        onLog("4) إرسال نبضة المزامنة [0x00, 0x00] بفاصل 10ms على 9600 bps (8N1) ثم انتظار 50ms...")
        val err1 = transport.writeRaw(byteArrayOf(0x00))
        if (err1 != null) {
            onLog("TX Error (Sync #1): $err1")
            return false
        }
        delay(10)

        val err2 = transport.writeRaw(byteArrayOf(0x00))
        if (err2 != null) {
            onLog("TX Error (Sync #2): $err2")
            return false
        }
        delay(50)
        transport.flushInputBuffer()
        return true
    }

    suspend fun handshakeAndSendResetCommand(
        transport: Serial78k0Transport,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        val primaryInvert = transport.getInvertDtrRtsPolarity()
        val resetFrame = buildCommandFrame(CMD_RESET)
        transport.setRawRxMonitor(true)
        try {
            if (!performHardwareHandshake(transport, invertControlPolarity = primaryInvert, onLog = onLog)) {
                return Nec78k0TransceiveResult.DisconnectedOrTimeout(
                    "فشلت المزامنة التلقائية (FT232 DTR/RTS + Baud Sync 9600) مع معالج 78K0.",
                    isUsbDetached = !transport.isDeviceAttached()
                )
            }

            onLog("TX (Reset 0x00 • مهلة ${RESET_TIMEOUT_MS}ms): ${toHex(resetFrame)}")
            val firstTry = transceiveSingleFrame(transport, resetFrame, RESET_TIMEOUT_MS, onLog)
            if (firstTry is Nec78k0TransceiveResult.Success) {
                transport.setInvertDtrRtsPolarity(primaryInvert)
                onLog("RX (Reset ACK): ${toHex(firstTry.frame.rawFrame)}")
                return firstTry
            }
            if (firstTry is Nec78k0TransceiveResult.ProtocolError) {
                transport.setInvertDtrRtsPolarity(primaryInvert)
                return firstTry
            }
            if ((firstTry as Nec78k0TransceiveResult.DisconnectedOrTimeout).isUsbDetached) {
                return firstTry
            }

            // Automatic background DTR/RTS polarity inversion fallback
            val altInvert = !primaryInvert
            onLog("لم يستجب المعالج بالقطبية الأولى (${firstTry.reason}) — إعادة المحاولة في الخلفية بعكس قطبية DTR/RTS...")
            if (!performHardwareHandshake(transport, invertControlPolarity = altInvert, onLog = onLog)) {
                return firstTry
            }
            onLog("TX (Reset 0x00 - عكس القطبية • مهلة ${RESET_TIMEOUT_MS}ms): ${toHex(resetFrame)}")
            val secondTry = transceiveSingleFrame(transport, resetFrame, RESET_TIMEOUT_MS, onLog)
            return when (secondTry) {
                is Nec78k0TransceiveResult.Success -> {
                    transport.setInvertDtrRtsPolarity(altInvert)
                    onLog("نجح الاتصال بعد عكس قطبية DTR/RTS في الخلفية! RX (Reset ACK): ${toHex(secondTry.frame.rawFrame)}")
                    secondTry
                }
                is Nec78k0TransceiveResult.ProtocolError -> {
                    transport.setInvertDtrRtsPolarity(altInvert)
                    secondTry
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> secondTry
            }
        } finally {
            transport.setRawRxMonitor(false)
        }
    }

    suspend fun autoDetectAndInspect(
        context: Context,
        transport: Serial78k0Transport,
        clockFreqMhzText: String,
        preferredChip: McuProcessor?,
        catalog: List<McuProcessor>,
        onLog: (String) -> Unit
    ): Pair<Nec78k0DetectedInfo?, String?> {
        when (val resetRes = handshakeAndSendResetCommand(transport, onLog)) {
            is Nec78k0TransceiveResult.Success -> Unit
            is Nec78k0TransceiveResult.ProtocolError -> {
                return null to "رفض المعالج أمر Reset 0x00: ${resetRes.statusLabel}"
            }
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                return null to resetRes.reason
            }
        }

        when (val oscRes = sendOscillationFrequencySetCommand(transport, onLog)) {
            is Nec78k0TransceiveResult.Success -> Unit
            is Nec78k0TransceiveResult.ProtocolError -> {
                return null to "رفض المعالج أمر ضبط تردد المذبذب (0x90): ${oscRes.statusLabel}"
            }
            is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                return null to oscRes.reason
            }
        }

        // Step 3: Silicon Signature Command (0xC0) after Reset 0x00 + Osc Freq 0x90
        val sigCmd = buildCommandFrame(CMD_SILICON_SIGNATURE)
        onLog("TX (Silicon Signature 0xC0): ${toHex(sigCmd)}")
        transport.flushInputBuffer()
        transport.setRawRxMonitor(true)
        var ackFrame: Nec78k0RxFrame? = null
        var sigDataFrame: Nec78k0RxFrame? = null
        try {
            val sigTxErr = transport.writeRaw(sigCmd)
            if (sigTxErr != null) {
                return null to "انقطع الإرسال عند أمر التوقيع 0xC0: $sigTxErr"
            }
            transport.discardEcho(sigCmd.size, 9600)

            ackFrame = readOneRxFrame(transport, RESET_TIMEOUT_MS) { stray ->
                onLog("RX Raw Live (0xC0 ACK): $stray")
            } ?: return null to "لم يرد المعالج بإطار ACK لتوقيع السليكون 0xC0 (تأكد من 9600 baud وتوصيل TX/RX/FLMD0)."
            if (!ackFrame.checksumValid || ackFrame.sts1 != STS_ACK) {
                return null to "خطأ في تأكيد أمر 0xC0: ${statusDescription(ackFrame.sts1)}"
            }

            sigDataFrame = readOneRxFrame(transport, 1500) { stray ->
                onLog("RX Raw Live (0xC0 Sig): $stray")
            } ?: return null to "استلمنا ACK لأمر 0xC0 لكن إطار بيانات التوقيع لم يصل."
        } finally {
            transport.setRawRxMonitor(false)
        }
        val rawSigHex = toHex(sigDataFrame.rawFrame)
        onLog("RX (Signature Frame): $rawSigHex")

        val parsed = parseSiliconSignature(
            sigFrame = sigDataFrame,
            fallbackChip = preferredChip,
            catalog = catalog
        )
        onLog(
            "تعرّف 0xC0: الشريحة المقروءة=${parsed.deviceName} • قاعدة البيانات=${parsed.matchedProcessor?.partNumber ?: "غير مطابقة"} (${parsed.flashSizeBytes / 1024}KB • ${parsed.blockCount} بلوك)"
        )

        // Preserve any existing uncompleted rescue session checksums so an already-erased Block 0 never overwrites FullCS / Block0CS
        val existingRescue = Nec78k0RecoveryManager.findSessionForChip(context, parsed.deviceName)
            ?: preferredChip?.let { Nec78k0RecoveryManager.findSessionForChip(context, it.partNumber) }
        if (existingRescue != null &&
            existingRescue.stage != Nec78k0RecoveryManager.RecoveryStage.COMPLETED &&
            (existingRescue.fullFlashChecksum != 0 || existingRescue.block0Checksum != 0)
        ) {
            onLog(
                "تم العثور على بصمة أصلية محفوظة مسبقاً (${existingRescue.chipName}: FullCS=${existingRescue.fullChecksumHex} • Block0CS=${existingRescue.block0ChecksumHex}) — الحفاظ على البصمة وتخطي 0xB0."
            )
            // Probe Block 0 blank state non-destructively (0x32) if not yet flagged touched
            if (!existingRescue.block0Touched) {
                val b0BlankRes = transceiveSingleFrame(
                    transport,
                    buildAddressRangeFrame(CMD_BLANK_CHECK, 0x000000, BLOCK_SIZE - 1),
                    HEADER_ACK_TIMEOUT_MS,
                    onLog
                )
                if (b0BlankRes is Nec78k0TransceiveResult.Success) {
                    Nec78k0RecoveryManager.saveSessionCheckpoint(
                        context,
                        existingRescue.copy(block0Touched = true, updatedAtMs = System.currentTimeMillis())
                    )
                }
            }
            val useExistingGeometry = existingRescue.flashSizeBytes >= parsed.flashSizeBytes
            val preservedInfo = parsed.copy(
                lastAddress = if (useExistingGeometry) existingRescue.lastAddress else parsed.lastAddress,
                flashSizeBytes = if (useExistingGeometry) existingRescue.flashSizeBytes else parsed.flashSizeBytes,
                blockCount = if (useExistingGeometry) existingRescue.blockCount else parsed.blockCount,
                fullFlashChecksum16 = existingRescue.fullFlashChecksum,
                block0Checksum16 = existingRescue.block0Checksum,
                rawSignatureHex = rawSigHex
            )
            return preservedInfo to null
        }

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
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, finalInfo.deviceName).absolutePath,
                block0Touched = false
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
            ?: Nec78k0RecoveryManager.findSessionForChip(context, selectedChip.partNumber)

        if (session != null && session.lastCompletedBlock >= 0) {
            onLog("تفعيل وضع الاستكمال / الإنقاذ التلقائي للجلسة المحفوظة (${session.chipName} • FullCS=${session.fullChecksumHex} • B0CS=${session.block0ChecksumHex}) من المرحلة: ${session.stage.labelAr}...")
            val resetRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (resetRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}${session.lastCompletedBlock}:${resetRes.reason}")
                } else {
                    FlashOperationResult.Failure("انتهت مهلة استجابة المعالج (Timeout) أثناء المزامنة: ${resetRes.reason} — منفذ USB لا يزال متصلاً.")
                }
            } else if (resetRes is Nec78k0TransceiveResult.ProtocolError) {
                return FlashOperationResult.Failure("رفض المعالج أمر Reset 0x00: ${resetRes.statusLabel}")
            }

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
            val resetRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (resetRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${resetRes.reason}")
                } else {
                    FlashOperationResult.Failure("انتهت مهلة استجابة المعالج (Timeout) أثناء المزامنة: ${resetRes.reason} — منفذ USB لا يزال متصلاً.")
                }
            } else if (resetRes is Nec78k0TransceiveResult.ProtocolError) {
                return FlashOperationResult.Failure("رفض المعالج أمر Reset 0x00: ${resetRes.statusLabel}")
            }
            val savedSession = Nec78k0RecoveryManager.findSessionForChip(context, preDetectedInfo.deviceName)
            session = savedSession ?: session
            detectedInfo = if (savedSession != null && (savedSession.fullFlashChecksum != 0 || savedSession.block0Checksum != 0)) {
                preDetectedInfo.copy(
                    fullFlashChecksum16 = savedSession.fullFlashChecksum,
                    block0Checksum16 = savedSession.block0Checksum
                )
            } else {
                preDetectedInfo
            }
            onDetectedInfo(detectedInfo)
        } else if (session != null && (session.fullFlashChecksum != 0 || session.block0Checksum != 0)) {
            onLog(
                "استرجاع البصمة الأصلية المحفوظة (${session.chipName} • FullCS=${session.fullChecksumHex} • B0CS=${session.block0ChecksumHex}) بدون إعادة قراءة Block 0..."
            )
            val resetRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (resetRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${resetRes.reason}")
                } else {
                    FlashOperationResult.Failure("انتهت مهلة استجابة المعالج (Timeout) أثناء المزامنة: ${resetRes.reason} — منفذ USB لا يزال متصلاً.")
                }
            } else if (resetRes is Nec78k0TransceiveResult.ProtocolError) {
                return FlashOperationResult.Failure("رفض المعالج أمر Reset 0x00: ${resetRes.statusLabel}")
            }
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
            // Check whether Block 0 was already erased (e.g., resuming after an interrupted 0x40 write)
            val b0CheckCmd = buildAddressRangeFrame(CMD_BLANK_CHECK, 0x000000, BLOCK_SIZE - 1)
            val b0BlankRes = transceiveWithTimeoutRetry(
                transport = transport,
                txFrame = b0CheckCmd,
                timeoutMs = HEADER_ACK_TIMEOUT_MS,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "فحص فراغ Block 0 (0x32)",
                onLog = onLog
            )
            if (b0BlankRes is Nec78k0TransceiveResult.Success) {
                onLog("المرحلة 2: Block 0 ممسوح مسبقاً (0x32 Blank ACK) — الاحتفاظ بالبصمة الأصلية (FullCS=${detectedInfo.fullFlashChecksumHex} • Block0CS=${detectedInfo.block0ChecksumHex}) والمتابعة لكتابة الأصفار...")
                val touchedSession = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
                    chipName = detectedInfo.deviceName,
                    subFamily = detectedInfo.familyGroup,
                    lastAddress = detectedInfo.lastAddress,
                    blockCount = blockCount,
                    hasDataFlash = detectedInfo.hasDataFlash,
                    fullFlashChecksum = detectedInfo.fullFlashChecksum16,
                    block0Checksum = detectedInfo.block0Checksum16,
                    signatureHex = detectedInfo.rawSignatureHex,
                    stage = Nec78k0RecoveryManager.RecoveryStage.DETECTED,
                    lastCompletedBlock = -1,
                    updatedAtMs = System.currentTimeMillis(),
                    partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                    block0Touched = true
                )).copy(block0Touched = true, updatedAtMs = System.currentTimeMillis())
                session = touchedSession
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, touchedSession)
            } else if (b0BlankRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (b0BlankRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${b0BlankRes.reason}")
                } else {
                    FlashOperationResult.Failure("انتهت مهلة استجابة المعالج أثناء فحص Block 0 (0x32) بعد 3 محاولات إعادة مزامنة (منفذ USB متصلاً): ${b0BlankRes.reason}")
                }
            } else {
                onLog("المرحلة 2: مسح Block 0 (0x000000..0x0003FF) بأمر 0x22...")
                val eraseB0Cmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1)
                when (val eraseRes = transceiveWithTimeoutRetry(transport, eraseB0Cmd, HEADER_ACK_TIMEOUT_MS, clockFreqMhzText, "مسح Block 0 (0x22)", onLog)) {
                    is Nec78k0TransceiveResult.Success -> {
                        onLog("RX (Block 0 Erase ACK): ${toHex(eraseRes.frame.rawFrame)}")
                        val touchedSession = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
                            chipName = detectedInfo.deviceName,
                            subFamily = detectedInfo.familyGroup,
                            lastAddress = detectedInfo.lastAddress,
                            blockCount = blockCount,
                            hasDataFlash = detectedInfo.hasDataFlash,
                            fullFlashChecksum = detectedInfo.fullFlashChecksum16,
                            block0Checksum = detectedInfo.block0Checksum16,
                            signatureHex = detectedInfo.rawSignatureHex,
                            stage = Nec78k0RecoveryManager.RecoveryStage.DETECTED,
                            lastCompletedBlock = -1,
                            updatedAtMs = System.currentTimeMillis(),
                            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                            block0Touched = true
                        )).copy(block0Touched = true, updatedAtMs = System.currentTimeMillis())
                        session = touchedSession
                        Nec78k0RecoveryManager.saveSessionCheckpoint(context, touchedSession)
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
                        return if (eraseRes.isUsbDetached) {
                            FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${eraseRes.reason}")
                        } else {
                            FlashOperationResult.Failure("انتهت مهلة مسح Block 0 (0x22) بعد 3 محاولات إعادة مزامنة (منفذ USB متصلاً): ${eraseRes.reason}")
                        }
                    }
                }
                delay(20)
            }

            onLog("المرحلة 2: كتابة أصفار في Block 0 (Header 0x40 ثم 4×260B دفعة واحدة عبر FIFO) لفتح السحب...")
            when (val zeroOutcome = programBlockFromBuffer(
                transport = transport,
                startAddr = 0x000000,
                endAddr = BLOCK_SIZE - 1,
                blockData = ByteArray(BLOCK_SIZE) { 0x00 },
                clockFreqMhzText = clockFreqMhzText,
                eraseBlockBeforeRetry = true,
                onLog = onLog
            )) {
                is ProgramOutcome.Success -> Unit
                is ProgramOutcome.ProtocolError -> {
                    if (zeroOutcome.isProtected) {
                        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                    }
                    return FlashOperationResult.Failure(zeroOutcome.message)
                }
                is ProgramOutcome.TransportFailure -> {
                    return if (zeroOutcome.isUsbDetached) {
                        FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${zeroOutcome.reason}")
                    } else {
                        FlashOperationResult.Failure(
                            "انتهت مهلة استجابة المعالج لحزمة 0x40 بعد 3 محاولات إعادة مزامنة (منفذ USB لا يزال مفتوحاً ومتصلاً): ${zeroOutcome.reason}"
                        )
                    }
                }
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
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                block0Touched = true
            )
            Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)
            onLog("تم تصفير Block 0 وتثبيت نقطة الاسترجاع الذرية بنجاح.")

            // Fast Re-sync after Block 0 Zeroing to stabilize UART boot state for Block 1..N-1 extraction
            onLog("إعادة مزامنة سريعة قبل سحب الكتل Block 1..${blockCount - 1}...")
            delay(20)
            val reSyncRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (reSyncRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (reSyncRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:انقطع اتصال USB OTG بعد تصفير Block 0 — ثبّت المبرمجة واضغط إنقاذ المعالج.")
                } else {
                    FlashOperationResult.Failure("انتهت مهلة المزامنة بعد تصفير Block 0 (منفذ USB لا يزال متصلاً): ${reSyncRes.reason}")
                }
            }
        }

        // Step 3: Pull & Verify Block 1 .. Block N-1
        val startBlock = (session?.lastCompletedBlock ?: 0).coerceAtLeast(0) + 1
        onLog("المرحلة 3: سحب ومطابقة الكتل من Block $startBlock إلى Block ${blockCount - 1}...")

        for (blockIdx in startBlock until blockCount) {
            if (!transport.isDeviceAttached()) {
                return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, "فُصل كابل USB OTG عند Block $blockIdx", isUsbDetached = true)
            }

            val blockStart = blockIdx * BLOCK_SIZE
            val blockEnd = blockStart + BLOCK_SIZE - 1
            val blankCmd = buildAddressRangeFrame(CMD_BLANK_CHECK, blockStart, blockEnd)

            when (val blankRes = transceiveWithTimeoutRetry(transport, blankCmd, 600, clockFreqMhzText, "فحص Block $blockIdx (0x32)", onLog)) {
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
                        return pauseWithWatchdog(
                            context,
                            session,
                            detectedInfo,
                            fullBuffer,
                            blockIdx - 1,
                            blockCount,
                            "استلمنا ERR_PROTECT أثناء قراءة Block $blockIdx",
                            isUsbDetached = false
                        )
                    }
                    // Non-blank block: verify 4x260B data frames using 0x13 Verify Command (single-bulk 260B TX + up to 3 re-sync retries on timeout)
                    val candidateBlock = fullBuffer.copyOfRange(blockStart, blockStart + BLOCK_SIZE)
                    val verifyResult = verifyBlockVia0x13(transport, blockStart, blockEnd, candidateBlock, clockFreqMhzText, onLog)
                    when (verifyResult) {
                        is VerifyOutcome.Match -> {
                            System.arraycopy(candidateBlock, 0, fullBuffer, blockStart, BLOCK_SIZE)
                        }
                        is VerifyOutcome.Mismatch -> {
                            onLog("تنبيه (Block $blockIdx): غير فارغ (0x32 -> ${blankRes.statusLabel}) والبيانات غير مطابقة لـ Verify (0x13 -> ${verifyResult.detail}).")
                        }
                        is VerifyOutcome.TransportLost -> {
                            return pauseWithWatchdog(
                                context,
                                session,
                                detectedInfo,
                                fullBuffer,
                                blockIdx - 1,
                                blockCount,
                                verifyResult.reason,
                                isUsbDetached = verifyResult.isUsbDetached
                            )
                        }
                    }
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    return pauseWithWatchdog(
                        context,
                        session,
                        detectedInfo,
                        fullBuffer,
                        blockIdx - 1,
                        blockCount,
                        blankRes.reason,
                        isUsbDetached = blankRes.isUsbDetached
                    )
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
                partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                block0Touched = true
            )).copy(
                stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
                lastCompletedBlock = blockIdx,
                updatedAtMs = System.currentTimeMillis(),
                block0Touched = true
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
        val reEraseRes = transceiveWithTimeoutRetry(
            transport = transport,
            txFrame = buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1),
            timeoutMs = HEADER_ACK_TIMEOUT_MS,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = "مسح Block 0 قبل الاسترجاع (0x22)",
            onLog = onLog
        )
        if (reEraseRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
            return pauseWithWatchdog(
                context,
                session,
                detectedInfo,
                fullBuffer,
                blockCount - 1,
                blockCount,
                reEraseRes.reason,
                isUsbDetached = reEraseRes.isUsbDetached
            )
        } else if (reEraseRes is Nec78k0TransceiveResult.ProtocolError) {
            return pauseWithWatchdog(
                context,
                session,
                detectedInfo,
                fullBuffer,
                blockCount - 1,
                blockCount,
                "فشل مسح Block 0 قبل الاسترجاع: ${reEraseRes.statusLabel}",
                isUsbDetached = false
            )
        }

        when (val restoreOutcome = programBlockFromBuffer(
            transport = transport,
            startAddr = 0x000000,
            endAddr = BLOCK_SIZE - 1,
            blockData = report.block0Bytes,
            clockFreqMhzText = clockFreqMhzText,
            eraseBlockBeforeRetry = true,
            onLog = onLog
        )) {
            is ProgramOutcome.Success -> Unit
            is ProgramOutcome.ProtocolError -> {
                return pauseWithWatchdog(
                    context,
                    session,
                    detectedInfo,
                    fullBuffer,
                    blockCount - 1,
                    blockCount,
                    restoreOutcome.message,
                    isUsbDetached = false
                )
            }
            is ProgramOutcome.TransportFailure -> {
                return pauseWithWatchdog(
                    context,
                    session,
                    detectedInfo,
                    fullBuffer,
                    blockCount - 1,
                    blockCount,
                    restoreOutcome.reason,
                    isUsbDetached = restoreOutcome.isUsbDetached
                )
            }
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
        reason: String,
        isUsbDetached: Boolean
    ): FlashOperationResult.Failure {
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, info.deviceName, partialBuffer)
        val nextStage = if (lastGoodBlock >= blockCount - 1) {
            Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED
        } else {
            Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS
        }
        val saved = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
            chipName = info.deviceName,
            subFamily = info.familyGroup,
            lastAddress = info.lastAddress,
            blockCount = blockCount,
            hasDataFlash = info.hasDataFlash,
            fullFlashChecksum = info.fullFlashChecksum16,
            block0Checksum = info.block0Checksum16,
            signatureHex = info.rawSignatureHex,
            stage = nextStage,
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(0),
            updatedAtMs = System.currentTimeMillis(),
            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, info.deviceName).absolutePath,
            block0Touched = true
        )).copy(
            stage = nextStage,
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(0),
            updatedAtMs = System.currentTimeMillis(),
            block0Touched = true
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, saved)
        return if (isUsbDetached) {
            FlashOperationResult.Failure(
                "${WATCHDOG_PREFIX}${saved.lastCompletedBlock}:انقطع اتصال USB OTG عند الكتلة ${saved.lastCompletedBlock + 1}/$blockCount ($reason) — تم حفظ التقدم على الذاكرة، ثبّت المبرمجة للاستكمال."
            )
        } else {
            FlashOperationResult.Failure(
                "انتهت مهلة استجابة المعالج (Timeout) عند الكتلة ${saved.lastCompletedBlock + 1}/$blockCount ($reason) — منفذ USB لا يزال مفتوحاً ومتصلاً، وتم حفظ التقدم بأمان على الذاكرة."
            )
        }
    }

    internal sealed class VerifyOutcome {
        data object Match : VerifyOutcome()
        data class Mismatch(val detail: String, val errorCode: Int = STS_VERIFY_ERROR, val failedPage: Int = 0) : VerifyOutcome()
        data class TransportLost(val reason: String, val failedPage: Int = 0, val isUsbDetached: Boolean = false) : VerifyOutcome()
    }

    internal sealed class ProgramOutcome {
        data object Success : ProgramOutcome()
        data class ProtocolError(val message: String, val isProtected: Boolean = false) : ProgramOutcome()
        data class TransportFailure(val reason: String, val isUsbDetached: Boolean = false) : ProgramOutcome()
    }

    private sealed class FrameAckOutcome {
        data class AckSuccess(val frame: Nec78k0RxFrame) : FrameAckOutcome()
        data class FrameError(val errorCode: Int, val description: String, val frame: Nec78k0RxFrame) : FrameAckOutcome()
        data class Timeout(val reason: String) : FrameAckOutcome()
        data class UsbDetached(val reason: String) : FrameAckOutcome()
    }

    internal fun isPhysicalDisconnect(transport: Serial78k0Transport, ioError: String? = null): Boolean {
        if (!transport.isDeviceAttached()) return true
        if (ioError != null && (
                ioError.contains("USB_DETACHED", ignoreCase = true) ||
                ioError.contains("SERIAL_IO_EXCEPTION", ignoreCase = true) ||
                ioError.contains("المنفذ مغلق") ||
                ioError.contains("attached=false", ignoreCase = true) ||
                ioError.contains("IOException", ignoreCase = true)
            )
        ) {
            return true
        }
        return false
    }

    internal suspend fun sendOscillationFrequencySetCommand(
        transport: Serial78k0Transport,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        val candidates = listOf(
            "8.00 MHz" to byteArrayOf(0x08, 0x00, 0x00, 0x06),
            "4.00 MHz" to byteArrayOf(0x04, 0x00, 0x00, 0x06),
            "10.00 MHz" to byteArrayOf(0x10, 0x00, 0x00, 0x06),
            "5.00 MHz" to byteArrayOf(0x05, 0x00, 0x00, 0x06)
        )

        var lastError: Nec78k0TransceiveResult? = null
        transport.setRawRxMonitor(true)
        try {
            for ((idx, candidate) in candidates.withIndex()) {
                val (label, payloadBytes) = candidate
                val oscFrame = buildCommandFrame(CMD_OSC_FREQ_SET, payloadBytes)
                onLog("TX (Osc Freq 0x90 • $label): ${toHex(oscFrame)}")
                when (val res = transceiveSingleFrame(transport, oscFrame, HEADER_ACK_TIMEOUT_MS, onLog)) {
                    is Nec78k0TransceiveResult.Success -> {
                        onLog("RX (0x90 $label ACK): ${toHex(res.frame.rawFrame)}")
                        return res
                    }
                    is Nec78k0TransceiveResult.ProtocolError -> {
                        if (res.statusCode == STS_PARAMETER_ERROR && idx < candidates.lastIndex) {
                            onLog("RX (0x90 $label -> 0x05 Parameter Error) — تجربة التردد البديل التالي...")
                            lastError = res
                            continue
                        }
                        onLog("RX Error (0x90 $label): ${res.statusLabel}")
                        return res
                    }
                    is Nec78k0TransceiveResult.DisconnectedOrTimeout -> return res
                }
            }
        } finally {
            transport.setRawRxMonitor(false)
        }
        return lastError ?: Nec78k0TransceiveResult.ProtocolError(STS_PARAMETER_ERROR, statusDescription(STS_PARAMETER_ERROR), null)
    }

    internal suspend fun resync78k0Bootloader(
        transport: Serial78k0Transport,
        @Suppress("UNUSED_PARAMETER") clockFreqMhzText: String,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        val resetRes = handshakeAndSendResetCommand(transport, onLog)
        if (resetRes !is Nec78k0TransceiveResult.Success) {
            return resetRes
        }
        return sendOscillationFrequencySetCommand(transport, onLog)
    }

    internal suspend fun transceiveWithTimeoutRetry(
        transport: Serial78k0Transport,
        txFrame: ByteArray,
        timeoutMs: Int,
        clockFreqMhzText: String,
        opLabel: String,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        var lastTimeout: Nec78k0TransceiveResult.DisconnectedOrTimeout? = null
        for (attempt in 0..MAX_TIMEOUT_RETRIES) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return Nec78k0TransceiveResult.DisconnectedOrTimeout("انقطع اتصال USB OTG عن الهاتف", isUsbDetached = true)
                }
                onLog("انتهت المهلة أثناء $opLabel (منفذ USB متصل) — إعادة المزامنة ثم المحاولة $attempt/$MAX_TIMEOUT_RETRIES...")
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) return resync
                    lastTimeout = resync
                    continue
                } else if (resync is Nec78k0TransceiveResult.ProtocolError) {
                    return resync
                }
            }
            when (val res = transceiveSingleFrame(transport, txFrame, timeoutMs, onLog)) {
                is Nec78k0TransceiveResult.Success,
                is Nec78k0TransceiveResult.ProtocolError -> return res
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (res.isUsbDetached) return res
                    lastTimeout = res
                }
            }
        }
        return lastTimeout ?: Nec78k0TransceiveResult.DisconnectedOrTimeout(
            "انتهت مهلة $opLabel (${timeoutMs}ms) بعد $MAX_TIMEOUT_RETRIES محاولات إعادة مزامنة",
            isUsbDetached = false
        )
    }

    /**
     * Sends an intermediate 260-byte data frame (pages 1..3 ending in 0x17 ETB) via Paced 64B/65ms
     * USB loop, waits 20ms only, and returns immediately WITHOUT waiting for an ACK frame because
     * 0x17 (ETB) instructs the 78K0 bootloader to expect the remaining pages of the 1024-byte block.
     */
    private suspend fun sendIntermediateDataFrame(
        transport: Serial78k0Transport,
        dataFrame: ByteArray,
        label: String,
        onLog: (String) -> Unit
    ): FrameAckOutcome? {
        if (!transport.isDeviceAttached()) {
            return FrameAckOutcome.UsbDetached("انقطع كابل USB OTG قبل إرسال $label")
        }
        val csByte = dataFrame[dataFrame.size - 2].toInt() and 0xFF
        val endByte = dataFrame[dataFrame.size - 1].toInt() and 0xFF
        val headPreview = toHex(dataFrame.copyOfRange(0, minOf(6, dataFrame.size)))
        onLog(String.format(Locale.US, "TX 260B ($label • Paced 64B/65ms • END=%02X ETB): [%s ... CS=%02X END=%02X] -> انتظار 20ms", endByte, headPreview, csByte, endByte))

        val txErr = transport.writeFrameBytes(dataFrame, FRAME_PACKET_WRITE_TIMEOUT_MS, onLog)
        if (txErr != null) {
            return if (isPhysicalDisconnect(transport, txErr)) {
                FrameAckOutcome.UsbDetached("انقطاع USB/IO أثناء إرسال $label: $txErr")
            } else {
                FrameAckOutcome.Timeout("تعثر إرسال حزمة 260B ($label): $txErr")
            }
        }
        transport.discardEcho(dataFrame.size, 9600)
        delay(20L)
        return null
    }

    /**
     * Sends the 4th and final 260-byte SOH data frame ([0x01, 0x00, ...256B, CS, 0x03 ETX]) via
     * Paced 64B/65ms USB loop, waits 50ms after the loop completes, then reads the final block
     * STX..ETX response ([02 02 06 06 F2 03] or [02 01 06 F9 03]) with timeout = 3000ms.
     */
    private suspend fun sendFinalBlockFrameAndAwaitAck(
        transport: Serial78k0Transport,
        dataFrame: ByteArray,
        timeoutMs: Int,
        label: String,
        onLog: (String) -> Unit
    ): FrameAckOutcome {
        if (!transport.isDeviceAttached()) {
            return FrameAckOutcome.UsbDetached("انقطع كابل USB OTG قبل إرسال $label")
        }
        val csByte = dataFrame[dataFrame.size - 2].toInt() and 0xFF
        val endByte = dataFrame[dataFrame.size - 1].toInt() and 0xFF
        val headPreview = toHex(dataFrame.copyOfRange(0, minOf(6, dataFrame.size)))
        onLog(String.format(Locale.US, "TX 260B ($label • Paced 64B/65ms • END=%02X ETX): [%s ... CS=%02X END=%02X] • انتظار الرد النهائي %dms", endByte, headPreview, csByte, endByte, timeoutMs))

        transport.setRawRxMonitor(true)
        try {
            val txErr = transport.writeFrameBytes(dataFrame, FRAME_PACKET_WRITE_TIMEOUT_MS, onLog)
            if (txErr != null) {
                return if (isPhysicalDisconnect(transport, txErr)) {
                    FrameAckOutcome.UsbDetached("انقطاع USB/IO أثناء إرسال $label: $txErr")
                } else {
                    FrameAckOutcome.Timeout("تعثر إرسال حزمة 260B ($label): $txErr")
                }
            }

            delay(50L)
            transport.discardEcho(dataFrame.size, 9600)
            val ack = readOneRxFrame(transport, timeoutMs) { stray ->
                onLog("RX Raw Live ($label): $stray")
            } ?: return if (!transport.isDeviceAttached()) {
                FrameAckOutcome.UsbDetached("انقطع كابل USB OTG أثناء انتظار رد $label")
            } else {
                FrameAckOutcome.Timeout("انتهت مهلة انتظار رد المعالج ($label خلال ${timeoutMs}ms)")
            }

            return if (ack.isAckSuccess) {
                FrameAckOutcome.AckSuccess(ack)
            } else {
                val errCode = ack.effectiveErrorCode
                FrameAckOutcome.FrameError(errCode, statusDescription(errCode), ack)
            }
        } finally {
            transport.setRawRxMonitor(false)
        }
    }

    /**
     * Two-Stage 0x13 Verify Handshake (4 x 260B Pages per 1024B Block):
     * Stage A: Send 11-byte 0x13 Address Frame ONLY -> wait strictly for 5-byte ACK [02 01 06 F9 03] (1500ms).
     * Stage B: Pages 1..3 (END=0x17 ETB): Paced 64B/65ms TX -> 20ms pause -> immediately send next page (NO ACK wait).
     *          Page 4 (END=0x03 ETX): Paced 64B/65ms TX -> 50ms pause -> wait for final block ACK (3000ms).
     */
    internal suspend fun verifyBlockVia0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockBytes: ByteArray,
        clockFreqMhzText: String = "8 MHz",
        onLog: (String) -> Unit
    ): VerifyOutcome {
        require(blockBytes.size == BLOCK_SIZE) { "78K0 verify block must be 1024 bytes" }
        val verifyCmd = buildAddressRangeFrame(CMD_VERIFY, startAddr, endAddr)
        var lastTimeoutReason = "انتهت مهلة Verify 0x13"
        var lastTimeoutPage = 0

        for (attempt in 0..MAX_TIMEOUT_RETRIES) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return VerifyOutcome.TransportLost("انقطع اتصال USB OTG أثناء Verify 0x13", lastTimeoutPage, isUsbDetached = true)
                }
                onLog(String.format(Locale.US, "انتهت مهلة 0x13 عند 0x%06X (USB متصل) — إعادة المزامنة ثم المحاولة %d/%d...", startAddr, attempt, MAX_TIMEOUT_RETRIES))
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) {
                        return VerifyOutcome.TransportLost(resync.reason, lastTimeoutPage, isUsbDetached = true)
                    }
                    lastTimeoutReason = resync.reason
                    continue
                }
            }

            when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                is Nec78k0TransceiveResult.Success -> Unit
                is Nec78k0TransceiveResult.ProtocolError -> return VerifyOutcome.Mismatch(cmdRes.statusLabel, cmdRes.statusCode, 0)
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (cmdRes.isUsbDetached) {
                        return VerifyOutcome.TransportLost(cmdRes.reason, 0, isUsbDetached = true)
                    }
                    lastTimeoutReason = cmdRes.reason
                    lastTimeoutPage = 0
                    continue
                }
            }

            delay(10)
            transport.flushInputBuffer()

            var pageTimedOut = false
            for (page in 0 until 4) {
                val offset = page * PAGE_SIZE
                val slice = blockBytes.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == 3)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                if (!isLast) {
                    val failOutcome = sendIntermediateDataFrame(transport, dataFrame, "0x13 صفحة ${page + 1}/4", onLog)
                    if (failOutcome is FrameAckOutcome.UsbDetached) {
                        return VerifyOutcome.TransportLost(failOutcome.reason, page + 1, isUsbDetached = true)
                    } else if (failOutcome is FrameAckOutcome.Timeout) {
                        lastTimeoutReason = failOutcome.reason
                        lastTimeoutPage = page + 1
                        pageTimedOut = true
                        break
                    }
                } else {
                    when (val ackRes = sendFinalBlockFrameAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x13 صفحة 4/4", onLog)) {
                        is FrameAckOutcome.AckSuccess -> Unit
                        is FrameAckOutcome.FrameError -> {
                            return VerifyOutcome.Mismatch(ackRes.description, ackRes.errorCode, 4)
                        }
                        is FrameAckOutcome.UsbDetached -> {
                            return VerifyOutcome.TransportLost(ackRes.reason, 4, isUsbDetached = true)
                        }
                        is FrameAckOutcome.Timeout -> {
                            lastTimeoutReason = ackRes.reason
                            lastTimeoutPage = 4
                            pageTimedOut = true
                        }
                    }
                }
            }
            if (!pageTimedOut) {
                return VerifyOutcome.Match
            }
        }
        return VerifyOutcome.TransportLost(
            "$lastTimeoutReason (بعد $MAX_TIMEOUT_RETRIES محاولات إعادة مزامنة)",
            lastTimeoutPage,
            isUsbDetached = false
        )
    }

    /**
     * Two-Stage 0x40 Program Handshake (4 x 260B Pages per 1024B Block):
     * Stage A: Send ONLY 11-byte 0x40 Address Frame -> wait strictly for 5-byte ACK [02 01 06 F9 03] (1500ms).
     * Stage B: Pages 1..3 (END=0x17 ETB): Paced 64B/65ms TX -> 20ms pause -> immediately send next page (NO ACK wait).
     *          Page 4 (END=0x03 ETX): Paced 64B/65ms TX -> 50ms pause -> wait for final block ACK [02 02 06 06 F2 03] / [02 01 06 F9 03] (3000ms).
     */
    internal suspend fun programBlockFromBuffer(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockData: ByteArray,
        clockFreqMhzText: String = "8 MHz",
        eraseBlockBeforeRetry: Boolean = true,
        onLog: (String) -> Unit
    ): ProgramOutcome {
        require(blockData.size == BLOCK_SIZE) { "78K0 program block must be 1024 bytes" }
        val progCmd = buildAddressRangeFrame(CMD_PROGRAM, startAddr, endAddr)
        var lastTimeoutReason = "انتهت مهلة حزمة 0x40"

        for (attempt in 0..MAX_TIMEOUT_RETRIES) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return ProgramOutcome.TransportFailure("انقطع كابل USB OTG قبل إعادة محاولة 0x40", isUsbDetached = true)
                }
                onLog(String.format(Locale.US, "انتهت مهلة 0x40 عند 0x%06X (USB متصل) — إعادة المزامنة ثم المحاولة %d/%d...", startAddr, attempt, MAX_TIMEOUT_RETRIES))
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) {
                        return ProgramOutcome.TransportFailure(resync.reason, isUsbDetached = true)
                    }
                    lastTimeoutReason = resync.reason
                    continue
                } else if (resync is Nec78k0TransceiveResult.ProtocolError) {
                    return ProgramOutcome.ProtocolError("رفض المعالج إعادة المزامنة قبل 0x40: ${resync.statusLabel}")
                }
                if (eraseBlockBeforeRetry) {
                    val eraseCmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, startAddr, endAddr)
                    when (val eraseRes = transceiveSingleFrame(transport, eraseCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                        is Nec78k0TransceiveResult.Success -> delay(15)
                        is Nec78k0TransceiveResult.ProtocolError -> {
                            if (eraseRes.statusCode == STS_PROTECT_ERROR) {
                                return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): أمر البرمجة مرفوض.", isProtected = true)
                            }
                        }
                        is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                            if (eraseRes.isUsbDetached) {
                                return ProgramOutcome.TransportFailure(eraseRes.reason, isUsbDetached = true)
                            }
                            lastTimeoutReason = eraseRes.reason
                            continue
                        }
                    }
                }
            }

            onLog("TX (0x40 Header 11B): ${toHex(progCmd)}")
            when (val res = transceiveSingleFrame(transport, progCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                is Nec78k0TransceiveResult.Success -> {
                    onLog("RX (0x40 Header ACK): ${toHex(res.frame.rawFrame)}")
                }
                is Nec78k0TransceiveResult.ProtocolError -> {
                    if (res.statusCode == STS_PROTECT_ERROR) {
                        return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): أمر البرمجة 0x40 مرفوض.", isProtected = true)
                    }
                    return ProgramOutcome.ProtocolError("رفض المعالج أمر الكتابة 0x40: ${res.statusLabel}")
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (res.isUsbDetached) {
                        return ProgramOutcome.TransportFailure(res.reason, isUsbDetached = true)
                    }
                    lastTimeoutReason = res.reason
                    continue
                }
            }

            // Allow 78K0 bootloader to switch cleanly from command header mode to 260-byte data reception mode
            delay(10)
            transport.flushInputBuffer()

            var pageTimedOut = false
            for (page in 0 until 4) {
                val offset = page * PAGE_SIZE
                val slice = blockData.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == 3)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                if (!isLast) {
                    val failOutcome = sendIntermediateDataFrame(transport, dataFrame, "0x40 صفحة ${page + 1}/4", onLog)
                    if (failOutcome is FrameAckOutcome.UsbDetached) {
                        return ProgramOutcome.TransportFailure(failOutcome.reason, isUsbDetached = true)
                    } else if (failOutcome is FrameAckOutcome.Timeout) {
                        lastTimeoutReason = failOutcome.reason
                        pageTimedOut = true
                        break
                    }
                } else {
                    when (val ackRes = sendFinalBlockFrameAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x40 صفحة 4/4", onLog)) {
                        is FrameAckOutcome.AckSuccess -> {
                            onLog("RX (0x40 Block ACK النهائي): ${toHex(ackRes.frame.rawFrame)}")
                        }
                        is FrameAckOutcome.UsbDetached -> {
                            return ProgramOutcome.TransportFailure(ackRes.reason, isUsbDetached = true)
                        }
                        is FrameAckOutcome.Timeout -> {
                            lastTimeoutReason = ackRes.reason
                            pageTimedOut = true
                        }
                        is FrameAckOutcome.FrameError -> {
                            if (ackRes.errorCode == STS_PROTECT_ERROR) {
                                return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): تم إيقاف الكتابة عند البلوك.", isProtected = true)
                            }
                            return ProgramOutcome.ProtocolError("فشل حرق البلوك (0x40): ${ackRes.description}")
                        }
                    }
                }
            }
            if (!pageTimedOut) {
                return ProgramOutcome.Success
            }
        }
        return ProgramOutcome.TransportFailure(
            "$lastTimeoutReason (بعد $MAX_TIMEOUT_RETRIES محاولات إعادة مزامنة)",
            isUsbDetached = false
        )
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

    private fun parseSiliconSignature(
        sigFrame: Nec78k0RxFrame,
        fallbackChip: McuProcessor?,
        catalog: List<McuProcessor>
    ): Nec78k0DetectedInfo {
        val payload = sigFrame.payload
        val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        val deviceMatch = Regex("""(?:uPD|D)?78F[0-9]{4}[A-Z0-9]{0,3}""", RegexOption.IGNORE_CASE).find(ascii)
        val rawDeviceName = deviceMatch?.value?.trim()?.uppercase()
            ?: fallbackChip?.partNumber?.uppercase()
            ?: "D78F0513A"

        val matched = findMatchingCatalogProcessor(rawDeviceName, catalog) ?: fallbackChip
        val canonicalDeviceName = matched?.partNumber ?: rawDeviceName

        // Parse 3-byte Last Code Flash Address from Silicon Signature payload if valid
        val parsedEndAddress = extractEndAddressFromSignature(payload)
            ?: ((matched?.flashSizeBytes ?: (32 * 1024)) - 1)
        val alignedFlashBytes = (matched?.flashSizeBytes ?: (parsedEndAddress + 1)).coerceAtLeast(BLOCK_SIZE)
        val blockCount = alignedFlashBytes / BLOCK_SIZE
        val familyGroup = infer78k0SubFamily(canonicalDeviceName, matched)
        val hasDf = matched?.chipConfig?.hasDataFlash ?: false

        return Nec78k0DetectedInfo(
            deviceName = canonicalDeviceName,
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
        val validM = setOf(0x1F, 0x3F, 0x5F, 0x7F, 0xBF, 0xEF, 0xFF)
        for (i in 0..payload.size - 3) {
            val b0 = payload[i].toInt() and 0xFF
            val b1 = payload[i + 1].toInt() and 0xFF
            val b2 = payload[i + 2].toInt() and 0xFF
            // Big-endian [H, M, L] e.g. 00 7F FF
            if (b2 == 0xFF && b0 in 0x00..0x01 && b1 in validM) {
                val candidate = (b0 shl 16) or (b1 shl 8) or b2
                if (candidate >= 0x1FFF && ((candidate + 1) % BLOCK_SIZE == 0)) {
                    return candidate
                }
            }
            // Little-endian [L, M, H] e.g. FF 7F 00
            if (b0 == 0xFF && b2 in 0x00..0x01 && b1 in validM) {
                val candidate = (b2 shl 16) or (b1 shl 8) or b0
                if (candidate >= 0x1FFF && ((candidate + 1) % BLOCK_SIZE == 0)) {
                    return candidate
                }
            }
        }
        return null
    }

    internal fun findMatchingCatalogProcessor(deviceName: String, catalog: List<McuProcessor>): McuProcessor? {
        val norm = normalize78k0Part(deviceName)
        catalog.firstOrNull { normalize78k0Part(it.partNumber) == norm }?.let { return it }
        val trimmedRev = norm.trimEnd('A', 'B', 'C', 'D')
        catalog.firstOrNull { normalize78k0Part(it.partNumber) == trimmedRev }?.let { return it }
        val coreRegex = Regex("""78F[0-9]{4}""")
        val coreTarget = coreRegex.find(norm)?.value
        if (coreTarget != null) {
            catalog.firstOrNull {
                coreRegex.find(normalize78k0Part(it.partNumber))?.value == coreTarget
            }?.let { return it }
        }
        return catalog.firstOrNull {
            val p = normalize78k0Part(it.partNumber)
            p.length >= 8 && trimmedRev.length >= 8 &&
                (p.startsWith(trimmedRev.take(8)) || trimmedRev.startsWith(p.take(8)))
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
            return Nec78k0TransceiveResult.DisconnectedOrTimeout(
                "خطأ إرسال UART: $txErr",
                isUsbDetached = isPhysicalDisconnect(transport, txErr)
            )
        }
        transport.discardEcho(txFrame.size, 9600)
        val rx = readOneRxFrame(transport, timeoutMs) { stray ->
            onLog("RX Raw Live (استجابة غير مكتملة): $stray")
        }
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
        timeoutMs: Int,
        onUnexpectedRx: ((String) -> Unit)? = null
    ): Nec78k0RxFrame? {
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        var foundStx = false
        val preStx = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val b = transport.readBytesWithTimeout(1, (deadline - System.currentTimeMillis()).toInt().coerceIn(20, 150))
            if (b.isEmpty()) continue
            val valByte = b[0].toInt() and 0xFF
            if (valByte == STX) {
                foundStx = true
                break
            } else {
                preStx.write(valByte)
            }
        }
        if (!foundStx) {
            if (preStx.size() > 0) {
                onUnexpectedRx?.invoke(toHex(preStx.toByteArray()))
            }
            return null
        } else if (preStx.size() > 0) {
            onUnexpectedRx?.invoke("قبل STX: ${toHex(preStx.toByteArray())}")
        }

        val lenArr = transport.readBytesWithTimeout(1, (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100))
        if (lenArr.isEmpty()) {
            onUnexpectedRx?.invoke("02 (انقطع بعد STX)")
            return null
        }
        val lenByte = lenArr[0].toInt() and 0xFF
        val dataLen = if (lenByte == 0) PAGE_SIZE else lenByte
        val tailNeeded = dataLen + 2 // DATA + CS + ETX -> total (LEN + 4) bytes
        val tail = readExactCount(transport, tailNeeded, (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(250))
        if (tail == null) {
            onUnexpectedRx?.invoke(String.format(Locale.US, "02 %02X (نقص باقي الإطار)", lenByte))
            return null
        }

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
