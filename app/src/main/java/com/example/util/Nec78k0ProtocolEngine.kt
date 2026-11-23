package com.example.util

import android.content.Context
import com.example.data.model.McuProcessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    const val PAGES_PER_BLOCK = BLOCK_SIZE / PAGE_SIZE // 4 pages x 256 bytes
    private const val RX_LEN_ZERO_BYTES = 256

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
    const val CMD_SECURITY: Int = 0xA0
    const val CMD_CHECKSUM: Int = 0xB0
    const val CMD_SILICON_SIGNATURE: Int = 0xC0

    const val STS_COMMAND_ERROR: Int = 0x04
    const val STS_PARAMETER_ERROR: Int = 0x05
    const val STS_ACK: Int = 0x06
    const val STS_CHECKSUM_ERROR: Int = 0x07
    const val STS_VERIFY_ERROR: Int = 0x0F
    const val STS_PROTECT_ERROR: Int = 0x10
    const val STS_NACK: Int = 0x15
    const val STS_ERASE_ERROR: Int = 0x1A
    const val STS_BLANK_CHECK_ERROR: Int = 0x1B
    const val STS_VERIFY_ERROR_1C: Int = 0x1C

    fun isVerifyMismatchCode(code: Int): Boolean {
        val c = code and 0xFF
        return c == STS_VERIFY_ERROR || c == STS_VERIFY_ERROR_1C
    }

    const val WATCHDOG_PREFIX = "WATCHDOG_PAUSED:"
    const val RESET_TIMEOUT_MS = 1500
    const val HEADER_ACK_TIMEOUT_MS = 1500
    const val PAGE_ACK_TIMEOUT_MS = 2500
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
        return buildVariableDataFrame(data256, isLast)
    }

    fun buildVariableDataFrame(data: ByteArray, isLast: Boolean): ByteArray {
        require(data.size in 1..PAGE_SIZE) { "78K0 data frame size must be 1..256 bytes" }
        val lenByte = data.size and 0xFF // 256 encodes cleanly as 0x00
        val cs = computeChecksum(lenByte, data)
        val out = ByteArray(2 + data.size + 2) // [0x02 STX, LEN, ...DATA..., CS, ETB/ETX]
        out[0] = STX.toByte()
        out[1] = lenByte.toByte()
        System.arraycopy(data, 0, out, 2, data.size)
        out[2 + data.size] = cs.toByte()
        out[2 + data.size + 1] = (if (isLast) ETX else ETB).toByte()
        return out
    }

    fun statusDescription(code: Int): String = when (code and 0xFF) {
        STS_COMMAND_ERROR -> "0x04 (Command Error)"
        STS_PARAMETER_ERROR -> "0x05 (Parameter Error)"
        STS_ACK -> "0x06 (ACK)"
        STS_CHECKSUM_ERROR -> "0x07 (Checksum Error)"
        STS_VERIFY_ERROR -> "0x0F (ERR_VERIFY - فشل المطابقة)"
        STS_PROTECT_ERROR -> "0x10 (ERR_PROTECT - المعالج محمي ضد المسح/الكتابة)"
        STS_NACK -> "0x15 (NACK - خطأ غير معروف أو مرفوض)"
        STS_ERASE_ERROR -> "0x1A (Erase Error - خطأ مسح)"
        STS_BLANK_CHECK_ERROR -> "0x1B (Not Blank / Checksum State Error)"
        STS_VERIFY_ERROR_1C -> "0x1C (Verify Error - البيانات غير مطابقة)"
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

        // Step 2: Silicon Signature Command (0xC0) directly after Reset 0x00 (02 01 06 F9 03)
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
        onLog(String.format(Locale.US, "TX (Checksum Full Flash 0x000000..%s): %s", parsed.lastAddressHex, toHex(buildAddressRangeFrame(CMD_CHECKSUM, 0x000000, fullEnd))))
        val fullCsRes = queryChecksum16WithRetry(
            transport = transport,
            startAddr = 0x000000,
            endAddr = fullEnd,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = "Full_Flash_Checksum (0xB0)",
            maxRetries = 2,
            verboseLog = true,
            onLog = onLog
        )
        val fullCs = (fullCsRes as? ChecksumQueryResult.Success)?.checksum16
            ?: return null to "فشل استلام Full_Flash_Checksum (0xB0) قبل البدء — توقفنا لحماية الذاكرة."

        onLog("TX (Checksum Block 0 0x000000..0x0003FF): ${toHex(buildAddressRangeFrame(CMD_CHECKSUM, 0x000000, BLOCK_SIZE - 1))}")
        val b0CsRes = queryChecksum16WithRetry(
            transport = transport,
            startAddr = 0x000000,
            endAddr = BLOCK_SIZE - 1,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = "Block0_Checksum (0xB0)",
            maxRetries = 2,
            verboseLog = true,
            onLog = onLog
        )
        val b0Cs = (b0CsRes as? ChecksumQueryResult.Success)?.checksum16
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

            onLog("المرحلة 2: كتابة أصفار في Block 0 (Header 0x40 للنطاق الكامل ثم 4×260B بدءاً بـ 0x02 STX) لفتح السحب...")
            when (val zeroOutcome = programBlockFromBuffer(
                transport = transport,
                startAddr = 0x000000,
                endAddr = BLOCK_SIZE - 1,
                blockData = ByteArray(BLOCK_SIZE) { 0x00 },
                clockFreqMhzText = clockFreqMhzText,
                eraseBlockBeforeRetry = true,
                onPageProgress = { bytesInBlock ->
                    onProgress(
                        bytesInBlock.coerceAtMost(totalSize),
                        totalSize,
                        String.format(
                            Locale.US,
                            "تصفير Block 0 (%d/%d بايت • صفحة %d/%d)",
                            bytesInBlock,
                            BLOCK_SIZE,
                            bytesInBlock / PAGE_SIZE,
                            PAGES_PER_BLOCK
                        )
                    )
                },
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

        // Step 3: Pull & Verify Block 1 .. Block N-1 (Per-Page 256B Empty Check + Extraction + Mandatory 0xB0 Verification)
        val startBlock = (session?.lastCompletedBlock ?: 0).coerceAtLeast(0) + 1
        onLog("المرحلة 3: سحب ومطابقة الكتل من Block $startBlock إلى Block ${blockCount - 1} (عبر فحص الصفحات 0x13 والتأكد الإلزامي بـ 0xB0)...")

        val extractionStartMs = System.currentTimeMillis()
        val initialDoneBytes = minOf(startBlock * BLOCK_SIZE, totalSize)
        val probeCaps = ExtractionCapabilities()

        for (blockIdx in startBlock until blockCount) {
            currentCoroutineContext().ensureActive()
            if (!transport.isDeviceAttached()) {
                return pauseWithWatchdog(context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount, "فُصل كابل USB OTG عند Block $blockIdx", isUsbDetached = true)
            }

            val blockStart = blockIdx * BLOCK_SIZE
            val blockEnd = blockStart + BLOCK_SIZE - 1
            val existingBlockCandidate = fullBuffer.copyOfRange(blockStart, blockStart + BLOCK_SIZE)

            val etaNow = formatEtaSuffix(blockStart, initialDoneBytes, totalSize, extractionStartMs)
            ProgrammerForegroundService.renewSessionLock(
                context,
                "سحب Block $blockIdx/${blockCount - 1}$etaNow"
            )

            var blockVerifiedBytes: ByteArray? = null
            var blockFailureReason = "فشل استخراج وتوثيق Block $blockIdx"
            var blockFailureIsUsb = false

            for (blockAttempt in 0..1) {
                if (blockAttempt > 0) {
                    if (!transport.isDeviceAttached()) {
                        blockFailureIsUsb = true
                        blockFailureReason = "انقطع اتصال USB OTG أثناء إعادة محاولة Block $blockIdx"
                        break
                    }
                    onLog("إعادة محاولة سحب وتوثيق Block $blockIdx بعد المزامنة ($blockAttempt/1)...")
                    val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                    if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                        blockFailureIsUsb = rs.isUsbDetached
                        blockFailureReason = rs.reason
                        if (rs.isUsbDetached) break
                    }
                }

                val candidateBytes = ByteArray(BLOCK_SIZE) { 0xFF.toByte() }
                val blankCmd = buildAddressRangeFrame(CMD_BLANK_CHECK, blockStart, blockEnd)
                var needPageExtraction = false

                when (val blankRes = transceiveWithTimeoutRetry(transport, blankCmd, 600, clockFreqMhzText, "فحص Block $blockIdx (0x32)", onLog)) {
                    is Nec78k0TransceiveResult.Success -> {
                        // Blank block (0xFF x 1024)
                        onProgress(
                            blockStart,
                            totalSize,
                            "Block $blockIdx/${blockCount - 1}: فارغ (0xFF)${formatEtaSuffix(blockStart, initialDoneBytes, totalSize, extractionStartMs)}"
                        )
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
                                "استلمنا ERR_PROTECT (0x10) أثناء قراءة Block $blockIdx",
                                isUsbDetached = false
                            )
                        }
                        needPageExtraction = true
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

                if (needPageExtraction) {
                    onLog("Block $blockIdx/${blockCount - 1} (0x%06X): غير فارغ — بدء فحص الصفحات (4×256B)...".format(Locale.US, blockStart))
                    var pageExtractFailed = false
                    var useWholeBlockFallback = (probeCaps.supports256PageVerify == false)

                    if (!useWholeBlockFallback) {
                        for (pageIdx in 0 until PAGES_PER_BLOCK) {
                            currentCoroutineContext().ensureActive()
                            val pageStart = blockStart + (pageIdx * PAGE_SIZE)
                            val existingPageSlice = existingBlockCandidate.copyOfRange(
                                pageIdx * PAGE_SIZE,
                                (pageIdx + 1) * PAGE_SIZE
                            )

                            val pageOutcome = extractSinglePage256(
                                context = context,
                                transport = transport,
                                blockIdx = blockIdx,
                                blockCount = blockCount,
                                pageIdx = pageIdx,
                                pageStartAddr = pageStart,
                                existingPageCandidate = existingPageSlice,
                                clockFreqMhzText = clockFreqMhzText,
                                caps = probeCaps,
                                totalSize = totalSize,
                                initialDoneBytes = initialDoneBytes,
                                extractionStartMs = extractionStartMs,
                                onProgress = onProgress,
                                onLog = onLog
                            )

                            when (pageOutcome) {
                                is PageExtractResult.Extracted -> {
                                    System.arraycopy(pageOutcome.bytes, 0, candidateBytes, pageIdx * PAGE_SIZE, PAGE_SIZE)
                                }
                                is PageExtractResult.Requires1024BlockFallback -> {
                                    probeCaps.supports256PageVerify = false
                                    useWholeBlockFallback = true
                                    break
                                }
                                is PageExtractResult.TransportLost -> {
                                    return pauseWithWatchdog(
                                        context,
                                        session,
                                        detectedInfo,
                                        fullBuffer,
                                        blockIdx - 1,
                                        blockCount,
                                        pageOutcome.reason,
                                        isUsbDetached = pageOutcome.isUsbDetached
                                    )
                                }
                                is PageExtractResult.Failed -> {
                                    blockFailureReason = pageOutcome.reason
                                    blockFailureIsUsb = false
                                    pageExtractFailed = true
                                    break
                                }
                            }
                        }
                    }

                    if (useWholeBlockFallback && !pageExtractFailed) {
                        onLog("Block $blockIdx: المعالج لا يقبل 0x13 لنطاق 256B (0x05) — فحص الكتلة كاملة 1024B (4×260B STX)...")
                        val blkVerify = verifyBlockVia0x13(
                            transport = transport,
                            startAddr = blockStart,
                            endAddr = blockEnd,
                            blockBytes = existingBlockCandidate,
                            clockFreqMhzText = clockFreqMhzText,
                            onPageProgress = { bytesInBlock ->
                                val done = minOf(blockStart + bytesInBlock, totalSize)
                                val eta = formatEtaSuffix(done, initialDoneBytes, totalSize, extractionStartMs)
                                onProgress(done, totalSize, "Block $blockIdx/${blockCount - 1} ($bytesInBlock/$BLOCK_SIZE B)$eta")
                            },
                            onLog = onLog
                        )
                        when (blkVerify) {
                            is VerifyOutcome.Match -> {
                                System.arraycopy(existingBlockCandidate, 0, candidateBytes, 0, BLOCK_SIZE)
                            }
                            is VerifyOutcome.Mismatch -> {
                                blockFailureReason = "فشل مطابقة Block $blockIdx عبر 0x13 (${blkVerify.detail}) والكتلة غير قابلة للاستخراج بصفحة 256B."
                                pageExtractFailed = true
                            }
                            is VerifyOutcome.TransportLost -> {
                                return pauseWithWatchdog(
                                    context,
                                    session,
                                    detectedInfo,
                                    fullBuffer,
                                    blockIdx - 1,
                                    blockCount,
                                    blkVerify.reason,
                                    isUsbDetached = blkVerify.isUsbDetached
                                )
                            }
                        }
                    }

                    if (pageExtractFailed) {
                        continue
                    }
                }

                // Mandatory Block Checksum Verification via 0xB0 (for EVERY block 1..N-1)
                val localBlockCs = Nec78k0Block0Reconstructor.compute78k0Checksum16(candidateBytes)
                val hwCsResult = queryChecksum16WithRetry(
                    transport = transport,
                    startAddr = blockStart,
                    endAddr = blockEnd,
                    clockFreqMhzText = clockFreqMhzText,
                    opLabel = "تحقق Block $blockIdx (0xB0)",
                    maxRetries = 2,
                    verboseLog = needPageExtraction,
                    onLog = onLog
                )
                when (hwCsResult) {
                    is ChecksumQueryResult.Success -> {
                        val hwCs = hwCsResult.checksum16 and 0xFFFF
                        if (hwCs == (localBlockCs and 0xFFFF)) {
                            blockVerifiedBytes = candidateBytes
                            if (needPageExtraction) {
                                onLog(
                                    String.format(
                                        Locale.US,
                                        "تم تأكيد Block %d/%d عبر 0xB0 بنجاح (CS=%04X مطابق 100%%).",
                                        blockIdx,
                                        blockCount - 1,
                                        hwCs
                                    )
                                )
                            }
                            break
                        } else {
                            blockFailureReason = String.format(
                                Locale.US,
                                "عدم تطابق Checksum (0xB0) في Block %d: المحلي=%04X != المعالج=%04X",
                                blockIdx,
                                localBlockCs and 0xFFFF,
                                hwCs
                            )
                            onLog(blockFailureReason)
                        }
                    }
                    is ChecksumQueryResult.ParameterError -> {
                        blockFailureReason = "رفض المعالج فحص Checksum (0xB0) للكتلة Block $blockIdx (0x05 Parameter Error)"
                        onLog(blockFailureReason)
                    }
                    is ChecksumQueryResult.ProtocolError -> {
                        blockFailureReason = "خطأ Checksum (0xB0) في Block $blockIdx: ${hwCsResult.statusLabel}"
                        onLog(blockFailureReason)
                    }
                    is ChecksumQueryResult.TransportLost -> {
                        blockFailureIsUsb = hwCsResult.isUsbDetached
                        blockFailureReason = hwCsResult.reason
                        if (hwCsResult.isUsbDetached) break
                    }
                }
            }

            if (blockVerifiedBytes == null) {
                return pauseWithWatchdog(
                    context = context,
                    session = session,
                    info = detectedInfo,
                    partialBuffer = fullBuffer,
                    lastGoodBlock = blockIdx - 1,
                    blockCount = blockCount,
                    reason = blockFailureReason,
                    isUsbDetached = blockFailureIsUsb
                )
            }

            System.arraycopy(blockVerifiedBytes, 0, fullBuffer, blockStart, BLOCK_SIZE)

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

            val hasCodeInBlock = blockVerifiedBytes.any { it != 0xFF.toByte() }
            if (hasCodeInBlock || blockIdx % 4 == 0 || blockIdx == blockCount - 1) {
                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, updatedSession)
            }

            val bytesDone = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            val etaLabel = formatEtaSuffix(bytesDone, initialDoneBytes, totalSize, extractionStartMs)
            onProgress(
                bytesDone,
                totalSize,
                String.format(Locale.US, "تم توثيق Block %d/%d (0x%06X)%s", blockIdx, blockCount - 1, blockStart, etaLabel)
            )
            ProgrammerForegroundService.renewSessionLock(
                context,
                "تم تأكيد Block $blockIdx/${blockCount - 1}$etaLabel"
            )
            if (blockIdx % 8 == 0 || blockIdx == blockCount - 1) {
                onLog("تم تأكيد Block $blockIdx/${blockCount - 1} (${bytesDone / 1024}/${totalSize / 1024} KB)$etaLabel")
            }
        }

        // Persist BLOCKS_EXTRACTED checkpoint before touching Block 0 restore
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
        session = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
            chipName = detectedInfo.deviceName,
            subFamily = detectedInfo.familyGroup,
            lastAddress = detectedInfo.lastAddress,
            blockCount = blockCount,
            hasDataFlash = detectedInfo.hasDataFlash,
            fullFlashChecksum = detectedInfo.fullFlashChecksum16,
            block0Checksum = detectedInfo.block0Checksum16,
            signatureHex = detectedInfo.rawSignatureHex,
            stage = Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED,
            lastCompletedBlock = blockCount - 1,
            updatedAtMs = System.currentTimeMillis(),
            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
            block0Touched = true
        )).copy(
            stage = Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED,
            lastCompletedBlock = blockCount - 1,
            updatedAtMs = System.currentTimeMillis(),
            block0Touched = true
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)

        // Step 4: Decode & Reconstruct Block 0 + Write 0x40 + Verify Full Checksum 0xB0
        onProgress(totalSize - BLOCK_SIZE, totalSize, "استرجاع Block 0 ومطابقة Checksum الكامل...")
        ProgrammerForegroundService.renewSessionLock(context, "جاري استرجاع Block 0 ومطابقة 0xB0...")
        onLog("المرحلة 4: تحليل متجهات CALL/BR في Block 1..${blockCount - 1} وإعادة بناء Block 0 برمجياً...")
        val report = Nec78k0Block0Reconstructor.reconstructBlock0(
            fullFlashBuffer = fullBuffer,
            targetBlock0Checksum16 = detectedInfo.block0Checksum16,
            targetFullChecksum16 = detectedInfo.fullFlashChecksum16
        )
        System.arraycopy(report.block0Bytes, 0, fullBuffer, 0, BLOCK_SIZE)
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
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
            onPageProgress = { bytesInBlock ->
                onProgress(
                    minOf((totalSize - BLOCK_SIZE) + bytesInBlock, totalSize),
                    totalSize,
                    String.format(Locale.US, "استرجاع Block 0: %d/%d بايت", bytesInBlock, BLOCK_SIZE)
                )
            },
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

        // Resync cleanly after Block 0 restore so 0xB0 Full Flash Checksum never receives 0x1B stale state
        delay(35)
        val finalSyncRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
        if (finalSyncRes is Nec78k0TransceiveResult.DisconnectedOrTimeout && finalSyncRes.isUsbDetached) {
            return pauseWithWatchdog(
                context,
                session,
                detectedInfo,
                fullBuffer,
                blockCount - 1,
                blockCount,
                finalSyncRes.reason,
                isUsbDetached = true
            )
        }

        // Verify Full Flash Checksum via 0xB0 strictly against saved original FullCS (e.g. 0xD081)
        val finalCsRes = queryChecksum16WithRetry(
            transport = transport,
            startAddr = 0x000000,
            endAddr = detectedInfo.lastAddress,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = "فحص Checksum النهائي للذاكرة الكاملة (0xB0)",
            maxRetries = 2,
            verboseLog = true,
            onLog = onLog
        )
        val finalCs = (finalCsRes as? ChecksumQueryResult.Success)?.checksum16
        if (finalCs == null) {
            val csErrReason = when (finalCsRes) {
                is ChecksumQueryResult.ProtocolError -> "رفض المعالج فحص Checksum النهائي 0xB0: ${finalCsRes.statusLabel}"
                is ChecksumQueryResult.TransportLost -> finalCsRes.reason
                is ChecksumQueryResult.ParameterError -> "خطأ معاملات (0x05) في 0xB0 النهائي"
                else -> "فشل استلام Checksum النهائي 0xB0"
            }
            return pauseWithWatchdog(
                context = context,
                session = session,
                info = detectedInfo,
                partialBuffer = fullBuffer,
                lastGoodBlock = blockCount - 1,
                blockCount = blockCount,
                reason = "$csErrReason — تم الحفاظ على نقطة الاسترجاع و FullCS=${detectedInfo.fullFlashChecksumHex}.",
                isUsbDetached = (finalCsRes as? ChecksumQueryResult.TransportLost)?.isUsbDetached ?: false
            )
        }

        val matchedOriginal = (detectedInfo.fullFlashChecksum16 == 0) ||
            ((finalCs and 0xFFFF) == (detectedInfo.fullFlashChecksum16 and 0xFFFF))
        onLog(
            String.format(
                Locale.US,
                "فحص Checksum النهائي (0xB0): الحالي=%04X / الأصلي=%04X (%s)",
                finalCs and 0xFFFF,
                detectedInfo.fullFlashChecksum16 and 0xFFFF,
                if (matchedOriginal) "مطابق 100%" else "اختلاف!"
            )
        )

        if (!matchedOriginal) {
            return pauseWithWatchdog(
                context = context,
                session = session,
                info = detectedInfo,
                partialBuffer = fullBuffer,
                lastGoodBlock = blockCount - 1,
                blockCount = blockCount,
                reason = String.format(
                    Locale.US,
                    "عدم تطابق Checksum النهائي (0xB0): الحالي=%04X != الأصلي=%04X — لم تكتمل المطابقة ولن يُحذف ملف الاسترجاع.",
                    finalCs and 0xFFFF,
                    detectedInfo.fullFlashChecksum16 and 0xFFFF
                ),
                isUsbDetached = false
            )
        }

        // Clean up checkpoint files ONLY once full checksum matches 100%
        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
        onProgress(totalSize, totalSize, "اكتملت القراءة")

        return FlashOperationResult.Success(
            message = "اكتملت القراءة وتفريغ ${detectedInfo.deviceName} (${totalSize / 1024} KB / $blockCount بلوك) واسترجاع Block 0 مع تطابق 0xB0 Checksum (${detectedInfo.fullFlashChecksumHex}) بنسبة 100%.",
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

    internal suspend fun resync78k0Bootloader(
        transport: Serial78k0Transport,
        @Suppress("UNUSED_PARAMETER") clockFreqMhzText: String,
        onLog: (String) -> Unit
    ): Nec78k0TransceiveResult {
        return handshakeAndSendResetCommand(transport, onLog)
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

    private data class ExtractionCapabilities(
        var supports256PageVerify: Boolean? = null,
        var supportsCumulativeChecksum: Boolean? = null,
        var supports1ByteVerify: Boolean? = null
    )

    private sealed class PageExtractResult {
        data class Extracted(val bytes: ByteArray) : PageExtractResult()
        data object Requires1024BlockFallback : PageExtractResult()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : PageExtractResult()
        data class Failed(val reason: String) : PageExtractResult()
    }

    private sealed class ProbeExtractResult {
        data class Extracted(val bytes: ByteArray) : ProbeExtractResult()
        data object ProbeUnsupported : ProbeExtractResult()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : ProbeExtractResult()
        data class Failed(val reason: String) : ProbeExtractResult()
    }

    internal sealed class ChecksumQueryResult {
        data class Success(val checksum16: Int) : ChecksumQueryResult()
        data object ParameterError : ChecksumQueryResult()
        data class ProtocolError(val statusCode: Int, val statusLabel: String) : ChecksumQueryResult()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : ChecksumQueryResult()
    }

    private sealed class RangeVerifyOutcome {
        data object Match : RangeVerifyOutcome()
        data class Mismatch(val errorCode: Int, val description: String) : RangeVerifyOutcome()
        data object ParameterUnsupported : RangeVerifyOutcome()
        data class ProtocolError(val errorCode: Int, val description: String) : RangeVerifyOutcome()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : RangeVerifyOutcome()
    }

    private fun formatEtaSuffix(
        bytesDone: Int,
        initialDoneBytes: Int,
        totalBytes: Int,
        startTimeMs: Long
    ): String {
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        val delta = bytesDone - initialDoneBytes
        if (elapsedMs < 1500L || delta < 32 || bytesDone >= totalBytes) return ""
        val remainingBytes = (totalBytes - bytesDone).coerceAtLeast(0)
        val etaSec = ((remainingBytes.toDouble() / delta.toDouble()) * (elapsedMs / 1000.0)).toLong().coerceAtLeast(1L)
        val hours = etaSec / 3600L
        val mins = (etaSec % 3600L) / 60L
        val secs = etaSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, " • متبقي %d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, " • متبقي %02d:%02d", mins, secs)
        }
    }

    private suspend fun extractSinglePage256(
        context: Context,
        transport: Serial78k0Transport,
        blockIdx: Int,
        blockCount: Int,
        pageIdx: Int,
        pageStartAddr: Int,
        existingPageCandidate: ByteArray,
        clockFreqMhzText: String,
        caps: ExtractionCapabilities,
        totalSize: Int,
        initialDoneBytes: Int,
        extractionStartMs: Long,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit,
        onLog: (String) -> Unit
    ): PageExtractResult {
        val pageEndAddr = pageStartAddr + PAGE_SIZE - 1
        val pageNum = pageIdx + 1

        // If partial buffer already contains recovered non-FF bytes for this page, confirm in 1 frame
        if (existingPageCandidate.any { it != 0xFF.toByte() }) {
            when (val existingRes = verifyRangeVia0x13(
                transport = transport,
                startAddr = pageStartAddr,
                endAddr = pageEndAddr,
                data = existingPageCandidate,
                clockFreqMhzText = clockFreqMhzText,
                verboseLog = false,
                label = "0x13 B$blockIdx P$pageNum (Saved)",
                onLog = onLog
            )) {
                is RangeVerifyOutcome.Match -> {
                    caps.supports256PageVerify = true
                    return PageExtractResult.Extracted(existingPageCandidate)
                }
                is RangeVerifyOutcome.ParameterUnsupported -> return PageExtractResult.Requires1024BlockFallback
                is RangeVerifyOutcome.TransportLost -> return PageExtractResult.TransportLost(existingRes.reason, existingRes.isUsbDetached)
                is RangeVerifyOutcome.ProtocolError -> return PageExtractResult.Failed("خطأ بروتوكول أثناء فحص Block $blockIdx صفحة $pageNum: ${existingRes.description}")
                is RangeVerifyOutcome.Mismatch -> Unit
            }
        }

        // Step 1: Empty Page Check (0x13 with 256 x 0xFF)
        val allFfPage = ByteArray(PAGE_SIZE) { 0xFF.toByte() }
        val etaCheck = formatEtaSuffix(pageStartAddr, initialDoneBytes, totalSize, extractionStartMs)
        onProgress(
            pageStartAddr,
            totalSize,
            "فحص Block $blockIdx/${blockCount - 1} • صفحة $pageNum/$PAGES_PER_BLOCK (0xFF)$etaCheck"
        )
        when (val emptyRes = verifyRangeVia0x13(
            transport = transport,
            startAddr = pageStartAddr,
            endAddr = pageEndAddr,
            data = allFfPage,
            clockFreqMhzText = clockFreqMhzText,
            verboseLog = true,
            label = "0x13 فحص فراغ B$blockIdx ص$pageNum",
            onLog = onLog
        )) {
            is RangeVerifyOutcome.Match -> {
                caps.supports256PageVerify = true
                return PageExtractResult.Extracted(allFfPage)
            }
            is RangeVerifyOutcome.ParameterUnsupported -> return PageExtractResult.Requires1024BlockFallback
            is RangeVerifyOutcome.TransportLost -> return PageExtractResult.TransportLost(emptyRes.reason, emptyRes.isUsbDetached)
            is RangeVerifyOutcome.ProtocolError -> return PageExtractResult.Failed("رفض المعالج فحص 0x13 عند Block $blockIdx صفحة $pageNum: ${emptyRes.description}")
            is RangeVerifyOutcome.Mismatch -> {
                caps.supports256PageVerify = true
                onLog(
                    String.format(
                        Locale.US,
                        "Block %d صفحة %d/%d (0x%06X): رُفضت 0xFF بـ %s — تحتوي على بيانات برمجية، بدء الاستخراج...",
                        blockIdx,
                        pageNum,
                        PAGES_PER_BLOCK,
                        pageStartAddr,
                        emptyRes.description
                    )
                )
            }
        }

        // Fast 1-frame check for all-0x00 page
        val allZeroPage = ByteArray(PAGE_SIZE) { 0x00 }
        when (val zeroRes = verifyRangeVia0x13(
            transport = transport,
            startAddr = pageStartAddr,
            endAddr = pageEndAddr,
            data = allZeroPage,
            clockFreqMhzText = clockFreqMhzText,
            verboseLog = false,
            label = "0x13 B$blockIdx ص$pageNum (0x00)",
            onLog = onLog
        )) {
            is RangeVerifyOutcome.Match -> {
                onLog("Block $blockIdx صفحة $pageNum/4: مطابقة 100% كصفحة مصفّرة (0x00).")
                return PageExtractResult.Extracted(allZeroPage)
            }
            is RangeVerifyOutcome.TransportLost -> return PageExtractResult.TransportLost(zeroRes.reason, zeroRes.isUsbDetached)
            else -> Unit
        }

        // Step 2A: Try cumulative 1-byte 0xB0 Checksum extraction if supported by silicon
        if (caps.supportsCumulativeChecksum != false) {
            when (val csExtract = extractPageViaCumulative0xB0(
                context = context,
                transport = transport,
                blockIdx = blockIdx,
                blockCount = blockCount,
                pageIdx = pageIdx,
                pageStartAddr = pageStartAddr,
                clockFreqMhzText = clockFreqMhzText,
                totalSize = totalSize,
                initialDoneBytes = initialDoneBytes,
                extractionStartMs = extractionStartMs,
                onProgress = onProgress,
                onLog = onLog
            )) {
                is ProbeExtractResult.Extracted -> {
                    caps.supportsCumulativeChecksum = true
                    return PageExtractResult.Extracted(csExtract.bytes)
                }
                is ProbeExtractResult.ProbeUnsupported -> {
                    caps.supportsCumulativeChecksum = false
                    onLog("ملاحظة: فحص 0xB0 لبايت واحد غير مدعوم في هذه النسخة (0x05) — التبديل تلقائياً إلى 0x13.")
                }
                is ProbeExtractResult.TransportLost -> return PageExtractResult.TransportLost(csExtract.reason, csExtract.isUsbDetached)
                is ProbeExtractResult.Failed -> {
                    caps.supportsCumulativeChecksum = false
                    onLog("تحذير 0xB0 التراكمي عند Block $blockIdx صفحة $pageNum: ${csExtract.reason}")
                }
            }
        }

        // Step 2B: Try 1-byte 0x13 [addr..addr] extraction if supported by silicon
        if (caps.supports1ByteVerify != false) {
            when (val singleByteExtract = extractPageVia1Byte0x13(
                context = context,
                transport = transport,
                blockIdx = blockIdx,
                blockCount = blockCount,
                pageIdx = pageIdx,
                pageStartAddr = pageStartAddr,
                clockFreqMhzText = clockFreqMhzText,
                totalSize = totalSize,
                initialDoneBytes = initialDoneBytes,
                extractionStartMs = extractionStartMs,
                onProgress = onProgress,
                onLog = onLog
            )) {
                is ProbeExtractResult.Extracted -> {
                    caps.supports1ByteVerify = true
                    return PageExtractResult.Extracted(singleByteExtract.bytes)
                }
                is ProbeExtractResult.ProbeUnsupported -> {
                    caps.supports1ByteVerify = false
                    onLog("ملاحظة: أمر 0x13 لبايت واحد غير مدعوم (0x05) — تشغيل الاستخراج المتسلسل 256B عبر 0x13...")
                }
                is ProbeExtractResult.TransportLost -> return PageExtractResult.TransportLost(singleByteExtract.reason, singleByteExtract.isUsbDetached)
                is ProbeExtractResult.Failed -> return PageExtractResult.Failed(singleByteExtract.reason)
            }
        }

        // Step 2C: User-specified 256B Sequential Extraction via 0x13 (test 0xFF then 0x00..0xFE per byte)
        val pageData = ByteArray(PAGE_SIZE) { 0xFF.toByte() }
        var consecutiveUnresolved = 0

        for (byteIdx in 0 until PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            var matchedFullPage = false
            var matchedByteValue: Int? = null
            val bytesDoneNow = minOf(pageStartAddr + byteIdx, totalSize)
            val eta = formatEtaSuffix(bytesDoneNow, initialDoneBytes, totalSize, extractionStartMs)

            ProgrammerForegroundService.renewSessionLock(
                context,
                "استخراج Block $blockIdx/${blockCount - 1} • ص$pageNum • بايت $byteIdx/255$eta"
            )

            // Test 0xFF first, then 0x00..0xFE
            for (step in 0 until 256) {
                currentCoroutineContext().ensureActive()
                val candVal = if (step == 0) 0xFF else step - 1
                pageData[byteIdx] = candVal.toByte()

                if (step % 16 == 0) {
                    val liveDone = minOf(pageStartAddr + byteIdx, totalSize)
                    val liveEta = formatEtaSuffix(liveDone, initialDoneBytes, totalSize, extractionStartMs)
                    onProgress(
                        liveDone,
                        totalSize,
                        String.format(
                            Locale.US,
                            "سحب B%d/%d • ص%d • بايت %d/255 (0x%02X)%s",
                            blockIdx,
                            blockCount - 1,
                            pageNum,
                            byteIdx,
                            candVal,
                            liveEta
                        )
                    )
                }

                when (val probeRes = verifyRangeVia0x13(
                    transport = transport,
                    startAddr = pageStartAddr,
                    endAddr = pageEndAddr,
                    data = pageData,
                    clockFreqMhzText = clockFreqMhzText,
                    verboseLog = false,
                    label = "0x13 B$blockIdx P$pageNum [$byteIdx]=${"%02X".format(candVal)}",
                    onLog = onLog
                )) {
                    is RangeVerifyOutcome.Match -> {
                        matchedByteValue = candVal
                        matchedFullPage = true
                        break
                    }
                    is RangeVerifyOutcome.Mismatch -> {
                        // Continue scanning candidates (0x1C / 0x0F)
                    }
                    is RangeVerifyOutcome.ParameterUnsupported -> {
                        return PageExtractResult.Requires1024BlockFallback
                    }
                    is RangeVerifyOutcome.ProtocolError -> {
                        return PageExtractResult.Failed("توقف فحص Block $blockIdx صفحة $pageNum عند بايت $byteIdx: ${probeRes.description}")
                    }
                    is RangeVerifyOutcome.TransportLost -> {
                        return PageExtractResult.TransportLost(probeRes.reason, probeRes.isUsbDetached)
                    }
                }
            }

            if (matchedFullPage && matchedByteValue != null) {
                onLog(
                    String.format(
                        Locale.US,
                        "Block %d صفحة %d/4: تطابقت الصفحة بالكامل عند البايت %d/255 (0x%02X -> 0x06 ACK).",
                        blockIdx,
                        pageNum,
                        byteIdx,
                        matchedByteValue
                    )
                )
                return PageExtractResult.Extracted(pageData)
            } else {
                // Keep 0xFF placeholder and check whether subsequent byte resolves
                pageData[byteIdx] = 0xFF.toByte()
                consecutiveUnresolved++
                onLog(
                    String.format(
                        Locale.US,
                        "تحذير (Block %d صفحة %d/4): لم تتطابق أي قيمة (0x00..0xFF) للبايت %d (وجود بايتات غير 0xFF لاحقة بالصفحة).",
                        blockIdx,
                        pageNum,
                        byteIdx
                    )
                )
                if (consecutiveUnresolved >= 2) {
                    return PageExtractResult.Failed(
                        "تعذّر استخراج Block $blockIdx صفحة $pageNum عبر 0x13 فقط: الشريحة رفضت أوامر 1-بايت (0x05) وتحتوي الصفحة على عدة بايتات كود متجاورة غير 0xFF، مما يجعل 0x13 (256B) يرد دائماً بـ 0x1C قبل مطابقة كامل الصفحة."
                    )
                }
            }
        }

        return PageExtractResult.Failed("لم تصل Block $blockIdx صفحة $pageNum إلى ACK (0x06) عبر 0x13.")
    }

    private suspend fun extractPageViaCumulative0xB0(
        context: Context,
        transport: Serial78k0Transport,
        blockIdx: Int,
        blockCount: Int,
        pageIdx: Int,
        pageStartAddr: Int,
        clockFreqMhzText: String,
        totalSize: Int,
        initialDoneBytes: Int,
        extractionStartMs: Long,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit,
        onLog: (String) -> Unit
    ): ProbeExtractResult {
        val pageEndAddr = pageStartAddr + PAGE_SIZE - 1
        val pageNum = pageIdx + 1
        val out = ByteArray(PAGE_SIZE)
        var prevSum = 0

        for (i in 0 until PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val targetEnd = pageStartAddr + i
            val csRes = queryChecksum16WithRetry(
                transport = transport,
                startAddr = pageStartAddr,
                endAddr = targetEnd,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "0xB0 تراكمي B$blockIdx ص$pageNum [$i]",
                maxRetries = 1,
                verboseLog = false,
                onLog = onLog
            )
            when (csRes) {
                is ChecksumQueryResult.ParameterError -> return ProbeExtractResult.ProbeUnsupported
                is ChecksumQueryResult.ProtocolError -> {
                    return if (i == 0) ProbeExtractResult.ProbeUnsupported else ProbeExtractResult.Failed("خطأ 0xB0 عند بايت $i: ${csRes.statusLabel}")
                }
                is ChecksumQueryResult.TransportLost -> return ProbeExtractResult.TransportLost(csRes.reason, csRes.isUsbDetached)
                is ChecksumQueryResult.Success -> {
                    val currSum = (0 - (csRes.checksum16 and 0xFFFF)) and 0xFFFF
                    val byteVal = (currSum - prevSum) and 0xFF
                    out[i] = byteVal.toByte()
                    prevSum = currSum
                }
            }

            if (i % 8 == 0 || i == PAGE_SIZE - 1) {
                val done = minOf(pageStartAddr + i + 1, totalSize)
                val eta = formatEtaSuffix(done, initialDoneBytes, totalSize, extractionStartMs)
                onProgress(done, totalSize, "استخراج 0xB0: B$blockIdx/${blockCount - 1} • ص$pageNum • بايت $i/255$eta")
                ProgrammerForegroundService.renewSessionLock(context, "استخراج B$blockIdx/${blockCount - 1} • بايت $i/255$eta")
            }
        }

        // Cross-check reconstructed 256B page via 0x13 (02 00 ... ETX)
        return when (val verifyRes = verifyRangeVia0x13(
            transport = transport,
            startAddr = pageStartAddr,
            endAddr = pageEndAddr,
            data = out,
            clockFreqMhzText = clockFreqMhzText,
            verboseLog = true,
            label = "0x13 توثيق B$blockIdx ص$pageNum",
            onLog = onLog
        )) {
            is RangeVerifyOutcome.Match -> {
                onLog("Block $blockIdx صفحة $pageNum/4: تم استخراج 256B عبر 0xB0 ومطابقتها بـ 0x13 بنجاح 100%.")
                ProbeExtractResult.Extracted(out)
            }
            is RangeVerifyOutcome.TransportLost -> ProbeExtractResult.TransportLost(verifyRes.reason, verifyRes.isUsbDetached)
            is RangeVerifyOutcome.Mismatch -> ProbeExtractResult.Failed("اختلاف 0x13 بعد استخراج 0xB0 في Block $blockIdx صفحة $pageNum: ${verifyRes.description}")
            is RangeVerifyOutcome.ProtocolError -> ProbeExtractResult.Failed("خطأ 0x13 بعد استخراج 0xB0: ${verifyRes.description}")
            is RangeVerifyOutcome.ParameterUnsupported -> ProbeExtractResult.Extracted(out)
        }
    }

    private suspend fun extractPageVia1Byte0x13(
        context: Context,
        transport: Serial78k0Transport,
        blockIdx: Int,
        blockCount: Int,
        pageIdx: Int,
        pageStartAddr: Int,
        clockFreqMhzText: String,
        totalSize: Int,
        initialDoneBytes: Int,
        extractionStartMs: Long,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit,
        onLog: (String) -> Unit
    ): ProbeExtractResult {
        val pageEndAddr = pageStartAddr + PAGE_SIZE - 1
        val pageNum = pageIdx + 1
        val out = ByteArray(PAGE_SIZE) { 0xFF.toByte() }
        val singleBuf = ByteArray(1)

        for (i in 0 until PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val addr = pageStartAddr + i
            var resolved: Int? = null
            val done = minOf(addr, totalSize)
            val eta = formatEtaSuffix(done, initialDoneBytes, totalSize, extractionStartMs)

            if (i % 4 == 0) {
                ProgrammerForegroundService.renewSessionLock(context, "سحب 1B: B$blockIdx/${blockCount - 1} • ص$pageNum • بايت $i/255$eta")
            }

            for (step in 0 until 256) {
                currentCoroutineContext().ensureActive()
                val cand = if (step == 0) 0xFF else step - 1
                singleBuf[0] = cand.toByte()
                if (step % 32 == 0) {
                    onProgress(done, totalSize, "سحب B$blockIdx/${blockCount - 1} • ص$pageNum • بايت $i/255 (0x${"%02X".format(cand)})$eta")
                }

                when (val res = verifyRangeVia0x13(
                    transport = transport,
                    startAddr = addr,
                    endAddr = addr,
                    data = singleBuf,
                    clockFreqMhzText = clockFreqMhzText,
                    verboseLog = false,
                    label = "0x13 1B 0x${"%06X".format(addr)}=${"%02X".format(cand)}",
                    onLog = onLog
                )) {
                    is RangeVerifyOutcome.Match -> {
                        resolved = cand
                        break
                    }
                    is RangeVerifyOutcome.Mismatch -> Unit
                    is RangeVerifyOutcome.ParameterUnsupported -> {
                        return if (i == 0) ProbeExtractResult.ProbeUnsupported else ProbeExtractResult.Failed("توقف 0x13 1B بـ 0x05 عند البايت $i")
                    }
                    is RangeVerifyOutcome.TransportLost -> return ProbeExtractResult.TransportLost(res.reason, res.isUsbDetached)
                    is RangeVerifyOutcome.ProtocolError -> return ProbeExtractResult.Failed("خطأ 0x13 عند بايت $i: ${res.description}")
                }
            }

            if (resolved == null) {
                return ProbeExtractResult.Failed("لم تتطابق أي قيمة (0x00..0xFF) للبايت $i في Block $blockIdx صفحة $pageNum.")
            }
            out[i] = resolved.toByte()
            if (i % 32 == 31) {
                onLog("Block $blockIdx صفحة $pageNum/4: تم سحب ${i + 1}/256 بايت...")
            }
        }

        // Confirm whole 256B page via standard 0x13 frame
        return when (val pageVerify = verifyRangeVia0x13(
            transport = transport,
            startAddr = pageStartAddr,
            endAddr = pageEndAddr,
            data = out,
            clockFreqMhzText = clockFreqMhzText,
            verboseLog = true,
            label = "0x13 توثيق B$blockIdx ص$pageNum",
            onLog = onLog
        )) {
            is RangeVerifyOutcome.Match -> {
                onLog("Block $blockIdx صفحة $pageNum/4: اكتمل استخراج 256B ومطابقتها بنجاح 100%.")
                ProbeExtractResult.Extracted(out)
            }
            is RangeVerifyOutcome.TransportLost -> ProbeExtractResult.TransportLost(pageVerify.reason, pageVerify.isUsbDetached)
            is RangeVerifyOutcome.Mismatch -> ProbeExtractResult.Failed("اختلاف مطابقة صفحة 256B بعد استخراج البايتات في Block $blockIdx ص$pageNum: ${pageVerify.description}")
            is RangeVerifyOutcome.ProtocolError -> ProbeExtractResult.Failed("خطأ 0x13 عند توثيق Block $blockIdx ص$pageNum: ${pageVerify.description}")
            is RangeVerifyOutcome.ParameterUnsupported -> ProbeExtractResult.Extracted(out)
        }
    }

    private suspend fun verifyRangeVia0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        data: ByteArray,
        clockFreqMhzText: String,
        verboseLog: Boolean,
        label: String,
        onLog: (String) -> Unit
    ): RangeVerifyOutcome {
        val verifyCmd = buildAddressRangeFrame(CMD_VERIFY, startAddr, endAddr)
        val dataFrame = buildVariableDataFrame(data, isLast = true)
        var lastTimeoutReason = "انتهت مهلة Verify 0x13"

        for (attempt in 0..MAX_TIMEOUT_RETRIES) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return RangeVerifyOutcome.TransportLost("انقطع اتصال USB OTG أثناء $label", isUsbDetached = true)
                }
                onLog("انتهت المهلة أثناء $label — إعادة المزامنة ثم المحاولة $attempt/$MAX_TIMEOUT_RETRIES...")
                val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (rs.isUsbDetached) return RangeVerifyOutcome.TransportLost(rs.reason, isUsbDetached = true)
                    lastTimeoutReason = rs.reason
                    continue
                }
            }

            if (verboseLog) onLog("TX ($label Header): ${toHex(verifyCmd)}")
            when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, HEADER_ACK_TIMEOUT_MS, onLog, verboseLog = verboseLog)) {
                is Nec78k0TransceiveResult.Success -> Unit
                is Nec78k0TransceiveResult.ProtocolError -> {
                    return when {
                        cmdRes.statusCode == STS_PARAMETER_ERROR -> RangeVerifyOutcome.ParameterUnsupported
                        isVerifyMismatchCode(cmdRes.statusCode) -> RangeVerifyOutcome.Mismatch(cmdRes.statusCode, cmdRes.statusLabel)
                        else -> RangeVerifyOutcome.ProtocolError(cmdRes.statusCode, cmdRes.statusLabel)
                    }
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (cmdRes.isUsbDetached) return RangeVerifyOutcome.TransportLost(cmdRes.reason, isUsbDetached = true)
                    lastTimeoutReason = cmdRes.reason
                    continue
                }
            }

            delay(if (data.size >= PAGE_SIZE) 8L else 3L)
            transport.flushInputBuffer()

            val ackRes = sendStxDataPageAndAwaitAck(
                transport = transport,
                dataFrame = dataFrame,
                timeoutMs = PAGE_ACK_TIMEOUT_MS,
                label = label,
                onLog = onLog,
                verboseLog = verboseLog
            )
            when (ackRes) {
                is FrameAckOutcome.AckSuccess -> return RangeVerifyOutcome.Match
                is FrameAckOutcome.FrameError -> {
                    return when {
                        isVerifyMismatchCode(ackRes.errorCode) -> RangeVerifyOutcome.Mismatch(ackRes.errorCode, ackRes.description)
                        ackRes.errorCode == STS_PARAMETER_ERROR -> RangeVerifyOutcome.ParameterUnsupported
                        else -> RangeVerifyOutcome.ProtocolError(ackRes.errorCode, ackRes.description)
                    }
                }
                is FrameAckOutcome.UsbDetached -> return RangeVerifyOutcome.TransportLost(ackRes.reason, isUsbDetached = true)
                is FrameAckOutcome.Timeout -> {
                    lastTimeoutReason = ackRes.reason
                }
            }
        }
        return RangeVerifyOutcome.TransportLost(
            "$lastTimeoutReason (بعد $MAX_TIMEOUT_RETRIES محاولات)",
            isUsbDetached = !transport.isDeviceAttached()
        )
    }

    /**
     * Sends an STX data frame ([0x02, LEN, ...DATA..., CS_Data, ETB/ETX]) via Paced 64B/65ms
     * USB loop (or single write for <= 64B frames), keeps RX Raw Live monitoring active, and
     * waits up to [timeoutMs] (2500ms) for ACK/status after EVERY page.
     */
    private suspend fun sendStxDataPageAndAwaitAck(
        transport: Serial78k0Transport,
        dataFrame: ByteArray,
        timeoutMs: Int,
        label: String,
        onLog: (String) -> Unit,
        verboseLog: Boolean = true
    ): FrameAckOutcome {
        if (!transport.isDeviceAttached()) {
            return FrameAckOutcome.UsbDetached("انقطع كابل USB OTG قبل إرسال $label")
        }
        if (verboseLog) {
            val csByte = dataFrame[dataFrame.size - 2].toInt() and 0xFF
            val endByte = dataFrame[dataFrame.size - 1].toInt() and 0xFF
            val endLabel = if (endByte == ETX) "ETX" else "ETB"
            val headPreview = toHex(dataFrame.copyOfRange(0, minOf(6, dataFrame.size)))
            onLog(
                String.format(
                    Locale.US,
                    "TX %dB (%s • STX=0x02 LEN=0x%02X • Paced 64B/65ms • END=%02X %s): [%s ... CS=%02X END=%02X] • مهلة الرد %dms",
                    dataFrame.size,
                    label,
                    dataFrame[1].toInt() and 0xFF,
                    endByte,
                    endLabel,
                    headPreview,
                    csByte,
                    endByte,
                    timeoutMs
                )
            )
        }

        transport.flushInputBuffer()
        transport.setRawRxMonitor(verboseLog)
        try {
            val txErr = if (dataFrame.size <= 64) {
                transport.writeRaw(dataFrame)
            } else {
                transport.writeFrameBytes(dataFrame, FRAME_PACKET_WRITE_TIMEOUT_MS, onLog)
            }
            if (txErr != null) {
                return if (isPhysicalDisconnect(transport, txErr)) {
                    FrameAckOutcome.UsbDetached("انقطاع USB/IO أثناء إرسال $label: $txErr")
                } else {
                    FrameAckOutcome.Timeout("تعثر إرسال حزمة ($label): $txErr")
                }
            }

            transport.discardEcho(dataFrame.size, 9600)
            val ack = readOneRxFrame(transport, timeoutMs) { stray ->
                if (verboseLog) onLog("RX Raw Live ($label): $stray")
            } ?: return if (!transport.isDeviceAttached()) {
                FrameAckOutcome.UsbDetached("انقطع كابل USB OTG أثناء انتظار رد $label")
            } else {
                FrameAckOutcome.Timeout("انتهت مهلة انتظار رد المعالج ($label خلال ${timeoutMs}ms)")
            }

            return if (ack.isAckSuccess) {
                if (verboseLog) onLog("RX ($label ACK): ${toHex(ack.rawFrame)}")
                FrameAckOutcome.AckSuccess(ack)
            } else {
                val errCode = ack.effectiveErrorCode
                val desc = statusDescription(errCode)
                if (verboseLog || !isVerifyMismatchCode(errCode)) {
                    onLog("RX Error ($label): $desc [${toHex(ack.rawFrame)}]")
                }
                FrameAckOutcome.FrameError(errCode, desc, ack)
            }
        } finally {
            transport.setRawRxMonitor(false)
        }
    }

    /**
     * Standard NEC 78K0 Two-Stage 0x13 Verify (1024B Block = 4 x 260B STX Frames):
     * Step 1: Send 11-byte 0x13 Header starting with SOH (0x01) -> await [02 01 06 F9 03].
     * Step 2: Send 4 Data Frames of 260B starting with STX (0x02), LEN=0x00:
     *         Pages 1..3 END=0x17 (ETB), Page 4 END=0x03 (ETX), Paced 64B/65ms -> await ACK (2500ms) after each page.
     */
    internal suspend fun verifyBlockVia0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockBytes: ByteArray,
        clockFreqMhzText: String = "8 MHz",
        onPageProgress: ((bytesInBlock: Int) -> Unit)? = null,
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

            onLog("TX (0x13 Header 11B): ${toHex(verifyCmd)}")
            when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                is Nec78k0TransceiveResult.Success -> {
                    onLog("RX (0x13 Header ACK): ${toHex(cmdRes.frame.rawFrame)}")
                }
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
            for (page in 0 until PAGES_PER_BLOCK) {
                val offset = page * PAGE_SIZE
                val slice = blockBytes.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == PAGES_PER_BLOCK - 1)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                when (val ackRes = sendStxDataPageAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x13 صفحة ${page + 1}/$PAGES_PER_BLOCK", onLog)) {
                    is FrameAckOutcome.AckSuccess -> {
                        onPageProgress?.invoke((page + 1) * PAGE_SIZE)
                    }
                    is FrameAckOutcome.FrameError -> {
                        return VerifyOutcome.Mismatch(ackRes.description, ackRes.errorCode, page + 1)
                    }
                    is FrameAckOutcome.UsbDetached -> {
                        return VerifyOutcome.TransportLost(ackRes.reason, page + 1, isUsbDetached = true)
                    }
                    is FrameAckOutcome.Timeout -> {
                        lastTimeoutReason = ackRes.reason
                        lastTimeoutPage = page + 1
                        pageTimedOut = true
                        break
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
     * Standard NEC 78K0 Two-Stage 0x40 Program (1024B Block = 4 x 260B STX Frames):
     * Step 1: Send 11-byte 0x40 Header starting with SOH (0x01) -> await [02 01 06 F9 03].
     * Step 2: Send 4 Data Frames of 260B starting with STX (0x02), LEN=0x00:
     *         Pages 1..3 END=0x17 (ETB), Page 4 END=0x03 (ETX), Paced 64B/65ms -> await ACK (2500ms) after each page.
     */
    internal suspend fun programBlockFromBuffer(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        blockData: ByteArray,
        clockFreqMhzText: String = "8 MHz",
        eraseBlockBeforeRetry: Boolean = true,
        onPageProgress: ((bytesInBlock: Int) -> Unit)? = null,
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

            // Allow 78K0 bootloader to switch cleanly from SOH command mode to STX data frame reception mode
            delay(10)
            transport.flushInputBuffer()

            var pageTimedOut = false
            for (page in 0 until PAGES_PER_BLOCK) {
                val offset = page * PAGE_SIZE
                val slice = blockData.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == PAGES_PER_BLOCK - 1)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                when (val ackRes = sendStxDataPageAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK", onLog)) {
                    is FrameAckOutcome.AckSuccess -> {
                        onPageProgress?.invoke((page + 1) * PAGE_SIZE)
                    }
                    is FrameAckOutcome.UsbDetached -> {
                        return ProgramOutcome.TransportFailure(ackRes.reason, isUsbDetached = true)
                    }
                    is FrameAckOutcome.Timeout -> {
                        lastTimeoutReason = ackRes.reason
                        pageTimedOut = true
                        break
                    }
                    is FrameAckOutcome.FrameError -> {
                        if (ackRes.errorCode == STS_PROTECT_ERROR) {
                            return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): تم إيقاف الكتابة عند البلوك.", isProtected = true)
                        }
                        return ProgramOutcome.ProtocolError("فشل حرق البلوك (0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK): ${ackRes.description}")
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
        return when (val res = queryChecksum16Once(transport, csCmdFrame, verboseLog = true, onLog = onLog)) {
            is ChecksumQueryResult.Success -> res.checksum16
            else -> null
        }
    }

    internal suspend fun queryChecksum16WithRetry(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        clockFreqMhzText: String,
        opLabel: String,
        maxRetries: Int = 2,
        verboseLog: Boolean = true,
        onLog: (String) -> Unit
    ): ChecksumQueryResult {
        val csCmdFrame = buildAddressRangeFrame(CMD_CHECKSUM, startAddr, endAddr)
        var lastResult: ChecksumQueryResult = ChecksumQueryResult.TransportLost("فشل قراءة Checksum 0xB0", isUsbDetached = !transport.isDeviceAttached())

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return ChecksumQueryResult.TransportLost("فُصل كابل USB OTG أثناء $opLabel", isUsbDetached = true)
                }
                if (verboseLog) {
                    onLog("إعادة محاولة $opLabel بعد المزامنة ($attempt/$maxRetries)...")
                }
                delay(25)
                val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout && rs.isUsbDetached) {
                    return ChecksumQueryResult.TransportLost(rs.reason, isUsbDetached = true)
                }
            }

            when (val res = queryChecksum16Once(transport, csCmdFrame, verboseLog = verboseLog, onLog = onLog)) {
                is ChecksumQueryResult.Success -> return res
                is ChecksumQueryResult.ParameterError -> return res
                is ChecksumQueryResult.ProtocolError -> {
                    lastResult = res
                    if (res.statusCode != STS_BLANK_CHECK_ERROR && res.statusCode != STS_CHECKSUM_ERROR && res.statusCode != STS_NACK) {
                        return res
                    }
                }
                is ChecksumQueryResult.TransportLost -> {
                    lastResult = res
                    if (res.isUsbDetached) return res
                }
            }
        }
        return lastResult
    }

    private suspend fun queryChecksum16Once(
        transport: Serial78k0Transport,
        csCmdFrame: ByteArray,
        verboseLog: Boolean,
        onLog: (String) -> Unit
    ): ChecksumQueryResult {
        if (!transport.isDeviceAttached()) {
            return ChecksumQueryResult.TransportLost("فُصل كابل USB OTG قبل إرسال 0xB0", isUsbDetached = true)
        }
        transport.flushInputBuffer()
        transport.setRawRxMonitor(verboseLog)
        try {
            val txErr = transport.writeRaw(csCmdFrame)
            if (txErr != null) {
                onLog("TX Error (0xB0): $txErr")
                return ChecksumQueryResult.TransportLost("خطأ إرسال 0xB0: $txErr", isUsbDetached = isPhysicalDisconnect(transport, txErr))
            }
            transport.discardEcho(csCmdFrame.size, 9600)
            val firstFrame = readOneRxFrame(transport, 1500) { stray ->
                if (verboseLog) onLog("RX Raw Live (0xB0): $stray")
            } ?: return ChecksumQueryResult.TransportLost(
                "انتهت مهلة انتظار رد 0xB0 من المعالج",
                isUsbDetached = !transport.isDeviceAttached()
            )

            if (!firstFrame.checksumValid) {
                onLog("Checksum 0xB0 Error: خطأ Checksum في إطار الاستقبال [${toHex(firstFrame.rawFrame)}]")
                return ChecksumQueryResult.ProtocolError(STS_CHECKSUM_ERROR, "خطأ Checksum في إطار الرد")
            }
            if (firstFrame.sts1 != STS_ACK) {
                val desc = statusDescription(firstFrame.sts1)
                if (verboseLog || firstFrame.sts1 != STS_PARAMETER_ERROR) {
                    onLog("Checksum 0xB0 Error: $desc [${toHex(firstFrame.rawFrame)}]")
                }
                return if (firstFrame.sts1 == STS_PARAMETER_ERROR) {
                    ChecksumQueryResult.ParameterError
                } else {
                    ChecksumQueryResult.ProtocolError(firstFrame.sts1, desc)
                }
            }
            if (firstFrame.payload.size == 2 && firstFrame.sts2 != null && firstFrame.sts2 != STS_ACK) {
                val err = firstFrame.sts2!!
                val desc = statusDescription(err)
                if (verboseLog || err != STS_PARAMETER_ERROR) {
                    onLog("Checksum 0xB0 Error: $desc [${toHex(firstFrame.rawFrame)}]")
                }
                return if (err == STS_PARAMETER_ERROR) {
                    ChecksumQueryResult.ParameterError
                } else {
                    ChecksumQueryResult.ProtocolError(err, desc)
                }
            }

            // Depending on silicon, 0xB0 either attaches the 2 checksum bytes after STS1 or sends a 2nd frame
            if (firstFrame.payload.size >= 3) {
                return ChecksumQueryResult.Success(
                    Nec78k0Block0Reconstructor.decodeChecksumBytes(firstFrame.payload.copyOfRange(1, 3))
                )
            }
            val dataFrame = readOneRxFrame(transport, 1500) { stray ->
                if (verboseLog) onLog("RX Raw Live (0xB0 Data): $stray")
            } ?: return ChecksumQueryResult.TransportLost(
                "وصل ACK لأمر 0xB0 لكن إطار بيانات البصمة لم يصل.",
                isUsbDetached = !transport.isDeviceAttached()
            )
            if (!dataFrame.checksumValid || dataFrame.payload.size < 2) {
                onLog("Checksum 0xB0 Data Frame Invalid: [${toHex(dataFrame.rawFrame)}]")
                return ChecksumQueryResult.ProtocolError(STS_CHECKSUM_ERROR, "بيانات إطار Checksum 0xB0 غير مكتملة")
            }
            return ChecksumQueryResult.Success(Nec78k0Block0Reconstructor.decodeChecksumBytes(dataFrame.payload))
        } finally {
            transport.setRawRxMonitor(false)
        }
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
        onLog: (String) -> Unit,
        verboseLog: Boolean = true
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
            if (verboseLog) onLog("RX Raw Live (استجابة غير مكتملة): $stray")
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
        val dataLen = if (lenByte == 0) RX_LEN_ZERO_BYTES else lenByte
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
