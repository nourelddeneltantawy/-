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
    const val CMD_READ: Int = 0x18
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
    const val MAX_STX_PAGE_RETRIES = 5
    const val STX_RETRY_WAIT_MS = 50L
    const val POST_BLOCK_PROGRAM_SETTLE_MS = 30L

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
        val matchedProcessor: McuProcessor?,
        val protocolId: String = Nec78k0ProtocolRegistry.PROTOCOL_ID,
        val prefersBlockErase0x22: Boolean = false
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
        val checksumValid: Boolean,
        val endByte: Int = ETX
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

        onLog("2) ضبط خطوط التحكم ($polarityTag): setDTR($dtrFlmd0High) [FLMD0=+5V] + setRTS($rtsResetLow) [RESET=0V] -> انتظار ${Nec78k0ProtocolRegistry.RESET_PULSE_MS}ms...")
        transport.setDtr(dtrFlmd0High, onLog)
        transport.setRts(rtsResetLow, onLog)
        delay(Nec78k0ProtocolRegistry.RESET_PULSE_MS)

        onLog("3) تحرير الريسيت: setRTS($rtsResetHigh) [RESET=+5V] مع إبقاء setDTR($dtrFlmd0High) [FLMD0=+5V] -> انتظار ${Nec78k0ProtocolRegistry.POST_RESET_DELAY_MS}ms لاستقرار المذبذب...")
        transport.setDtr(dtrFlmd0High, onLog)
        transport.setRts(rtsResetHigh, onLog)
        delay(Nec78k0ProtocolRegistry.POST_RESET_DELAY_MS)
        transport.flushInputBuffer()

        if (!transport.isDeviceAttached()) {
            onLog("Watchdog Alert: انقطع اتصال USB OTG قبل إرسال نبضة المزامنة!")
            return false
        }

        onLog("4) إرسال نبضة المزامنة [0x00, 0x00] بفاصل ${Nec78k0ProtocolRegistry.SYNC_BYTE_GAP_MS}ms على ${Nec78k0ProtocolRegistry.INITIAL_BAUD_RATE} bps (8N1) ثم انتظار 50ms...")
        val err1 = transport.writeRaw(byteArrayOf(0x00))
        if (err1 != null) {
            onLog("TX Error (Sync #1): $err1")
            return false
        }
        delay(Nec78k0ProtocolRegistry.SYNC_BYTE_GAP_MS)

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
        autoRestoreChip: Boolean = true,
        onDetectedInfo: (Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val detectedInfo: Nec78k0DetectedInfo
        var session = existingResumeSession
            ?: Nec78k0RecoveryManager.findSessionForChip(context, selectedChip.partNumber)

        if (session != null && (session.lastCompletedBlock >= 0 || session.activeDiffBlockIndex >= 0)) {
            val resumeBlockHint = if (session.activeDiffBlockIndex >= 0) {
                " • الكتلة النشطة ${session.activeDiffBlockIndex} عند البايت ${session.activeDiffByteIndex}/1024"
            } else ""
            onLog("تفعيل وضع الاستكمال / الإنقاذ التلقائي للجلسة المحفوظة (${session.chipName} • FullCS=${session.fullChecksumHex} • B0CS=${session.block0ChecksumHex}$resumeBlockHint) من المرحلة: ${session.stage.labelAr}...")
            val resetRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (resetRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return if (resetRes.isUsbDetached) {
                    FlashOperationResult.Failure("${WATCHDOG_PREFIX}${session.lastCompletedBlock.coerceAtLeast(0)}:${resetRes.reason}")
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
        var hadBlockChecksumWarning = false

        // المرحلة 2: استخراج Block 0 عبر فرق Checksum (0x40 صفر + 0xB0) ثم مسحه وتصفيره
        if (session == null || session.lastCompletedBlock < 0) {
            onProgress(0, totalSize, "المرحلة 2: فحص واستخراج Block 0 عبر فرق 0xB0...")

            // Check if resuming a legacy session where Block 0 was already erased without differential capture
            var alreadyBlankLegacy = false
            if (session?.block0Touched == true && !session.block0OriginalExtracted && session.block0DiffNextIndex == 0) {
                val b0BlankRes = transceiveWithTimeoutRetry(
                    transport = transport,
                    txFrame = buildAddressRangeFrame(CMD_BLANK_CHECK, 0x000000, BLOCK_SIZE - 1),
                    timeoutMs = HEADER_ACK_TIMEOUT_MS,
                    clockFreqMhzText = clockFreqMhzText,
                    opLabel = "فحص حالة Block 0 (0x32)",
                    onLog = onLog
                )
                if (b0BlankRes is Nec78k0TransceiveResult.Success) {
                    alreadyBlankLegacy = true
                } else if (b0BlankRes is Nec78k0TransceiveResult.ProtocolError && b0BlankRes.statusCode == STS_PROTECT_ERROR) {
                    Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                    return FlashOperationResult.Failure(
                        "المعالج محمي برمجياً ضد القراءة/المسح (ERR_PROTECT 0x10). تم إيقاف العملية فوراً لحماية بياناتك."
                    )
                } else if (b0BlankRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    return if (b0BlankRes.isUsbDetached) {
                        FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${b0BlankRes.reason}")
                    } else {
                        FlashOperationResult.Failure("انتهت مهلة فحص Block 0 (0x32): ${b0BlankRes.reason}")
                    }
                }
            }

            if (!alreadyBlankLegacy && session?.block0OriginalExtracted != true) {
                val startByteIdx = when {
                    session?.activeDiffBlockIndex == 0 -> session.activeDiffByteIndex.coerceIn(0, BLOCK_SIZE)
                    else -> (session?.block0DiffNextIndex ?: 0).coerceIn(0, BLOCK_SIZE)
                }
                var b0ExtractedNonDestructively = false
                var b0FastFillByte: Byte? = null

                // Fast-Fill rule for uniform Block 0 (CS_orig == 0x0004 -> 0x00, 0xFC04 -> 0xFF) in < 1s
                if (startByteIdx == 0 && session?.block0Touched != true) {
                    val b0QuickCs = detectedInfo.block0Checksum16 and 0xFFFF
                    val fastB0Fill = Nec78k0Block0Reconstructor.fastFillByteForKnownChecksum(b0QuickCs)
                    if (fastB0Fill != null) {
                        java.util.Arrays.fill(fullBuffer, 0, BLOCK_SIZE, fastB0Fill)
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
                            stage = Nec78k0RecoveryManager.RecoveryStage.DETECTED,
                            lastCompletedBlock = -1,
                            updatedAtMs = System.currentTimeMillis(),
                            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                            block0Touched = false
                        )).copy(
                            updatedAtMs = System.currentTimeMillis(),
                            block0OriginalExtracted = true,
                            block0DiffNextIndex = BLOCK_SIZE,
                            block0DiffPrevCs = b0QuickCs,
                            activeDiffBlockIndex = -1,
                            activeDiffByteIndex = 0
                        )
                        Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)
                        onProgress(BLOCK_SIZE, totalSize, "تعبئة سريعة Block 0 (0x%02X)".format(fastB0Fill.toInt() and 0xFF))
                        onLog(
                            String.format(
                                Locale.US,
                                "⚡ تعبئة سريعة فورية لـ Block 0 بالقيمة 0x%02X بدون مسح بايت تلو الآخر (CS_orig=%04X < 1s).",
                                fastB0Fill.toInt() and 0xFF, b0QuickCs
                            )
                        )
                        b0ExtractedNonDestructively = true
                        b0FastFillByte = fastB0Fill
                    }
                }

                if (!b0ExtractedNonDestructively && startByteIdx == 0 && session?.block0Touched != true) {
                    val cumOutcome = tryExtractBlock0ViaCumulative0xB0(
                        context = context,
                        transport = transport,
                        detectedInfo = detectedInfo,
                        fullBuffer = fullBuffer,
                        blockCount = blockCount,
                        clockFreqMhzText = clockFreqMhzText,
                        existingSession = session,
                        onProgress = onProgress,
                        onLog = onLog
                    )
                    when (cumOutcome) {
                        is Cumulative0xB0Outcome.Extracted -> {
                            session = cumOutcome.updatedSession
                            b0ExtractedNonDestructively = true
                        }
                        is Cumulative0xB0Outcome.UnsupportedFallback -> {
                            onLog(cumOutcome.reason)
                        }
                        is Cumulative0xB0Outcome.Paused -> return cumOutcome.failure
                        is Cumulative0xB0Outcome.Fatal -> return FlashOperationResult.Failure(cumOutcome.reason)
                    }
                }

                var b0AlreadyAllZeroedOnChip = false
                if (!b0ExtractedNonDestructively && startByteIdx < BLOCK_SIZE) {
                    onLog(
                        if (startByteIdx == 0) {
                            "المرحلة 2: استخراج Original_Byte[0..1023] في Block 0 عبر فرق Checksum التراكمي (0x40 PageBuffer + 0xB0)..."
                        } else {
                            "المرحلة 2: استئناف استخراج Block 0 تفاضلياً من البايت $startByteIdx/1023 مع فحص CS_prev الحي..."
                        }
                    )

                    val initialCsRes = queryChecksum16WithRetry(
                        transport = transport,
                        startAddr = 0x000000,
                        endAddr = BLOCK_SIZE - 1,
                        clockFreqMhzText = clockFreqMhzText,
                        opLabel = "قراءة CS_prev للكتلة Block 0 (0xB0)",
                        maxRetries = 2,
                        verboseLog = true,
                        onLog = onLog
                    )
                    var csPrev = when (initialCsRes) {
                        is ChecksumQueryResult.Success -> initialCsRes.checksum16 and 0xFFFF
                        is ChecksumQueryResult.TransportLost -> {
                            return pauseWithWatchdog(
                                context, session, detectedInfo, fullBuffer, -1, blockCount,
                                initialCsRes.reason, isUsbDetached = initialCsRes.isUsbDetached,
                                block0OriginalExtracted = false, block0DiffNextIndex = startByteIdx, block0DiffPrevCs = -1,
                                activeDiffBlockIndex = 0, activeDiffByteIndex = startByteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                            )
                        }
                        is ChecksumQueryResult.ProtocolError -> {
                            if (initialCsRes.statusCode == STS_PROTECT_ERROR) {
                                Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                                return FlashOperationResult.Failure("المعالج محمي برمجياً (ERR_PROTECT 0x10).")
                            }
                            return FlashOperationResult.Failure("فشل قراءة CS_prev لـ Block 0 (0xB0): ${initialCsRes.statusLabel}")
                        }
                        is ChecksumQueryResult.ParameterError -> {
                            return FlashOperationResult.Failure("خطأ معاملات (0x05) في فحص 0xB0 لـ Block 0.")
                        }
                    }

                    val stage2StartMs = System.currentTimeMillis()
                    var writeMode: ZeroWriteGranularity? = null
                    val b0Working = fullBuffer.copyOfRange(0, BLOCK_SIZE)
                    val b0LiveFastFill = if (startByteIdx == 0 && session?.block0Touched != true) {
                        Nec78k0Block0Reconstructor.fastFillByteForKnownChecksum(csPrev)
                    } else null

                    if (b0LiveFastFill != null) {
                        java.util.Arrays.fill(b0Working, b0LiveFastFill)
                        System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                        b0ExtractedNonDestructively = true
                        b0FastFillByte = b0LiveFastFill
                        onProgress(BLOCK_SIZE, totalSize, "تعبئة سريعة Block 0 (0x%02X)".format(b0LiveFastFill.toInt() and 0xFF))
                        onLog(
                            String.format(
                                Locale.US,
                                "⚡ تعبئة سريعة فورية لـ Block 0 بالقيمة 0x%02X (CS_prev=%04X < 1s).",
                                b0LiveFastFill.toInt() and 0xFF, csPrev
                            )
                        )
                    } else {
                        // Cumulative 256-byte PageBuffer: keeps 0..(pageOffset-1) at 0x00 and (pageOffset+1)..255 at 0xFF
                        val pageBuffer = ByteArray(PAGE_SIZE) { 0xFF.toByte() }
                        val resumePageOff = startByteIdx % PAGE_SIZE
                        for (p in 0 until resumePageOff) {
                            pageBuffer[p] = 0x00
                        }

                        for (byteIdx in startByteIdx until BLOCK_SIZE) {
                            currentCoroutineContext().ensureActive()
                            ProgrammerForegroundService.awaitIfPaused()
                            if (!transport.isDeviceAttached()) {
                                System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                return pauseWithWatchdog(
                                    context, session, detectedInfo, fullBuffer, -1, blockCount,
                                    "انقطع كابل USB OTG أثناء استخراج Block 0 عند البايت $byteIdx/1023",
                                    isUsbDetached = true,
                                    block0OriginalExtracted = false,
                                    block0DiffNextIndex = byteIdx,
                                    block0DiffPrevCs = csPrev,
                                    activeDiffBlockIndex = 0,
                                    activeDiffByteIndex = byteIdx,
                                    activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                )
                            }

                            val pageOffset = byteIdx % PAGE_SIZE
                            if (pageOffset == 0) {
                                java.util.Arrays.fill(pageBuffer, 0xFF.toByte())
                            }
                            pageBuffer[pageOffset] = 0x00

                            val (modeUsed, zeroRes) = writeSingleByteZeroWithAutoGranularity(
                                transport = transport,
                                byteIdx = byteIdx,
                                currentMode = writeMode,
                                clockFreqMhzText = clockFreqMhzText,
                                onLog = onLog,
                                blockStart = 0x000000,
                                cumulativePageBuffer = pageBuffer
                            )
                            writeMode = modeUsed

                            when (zeroRes) {
                                is DiffWriteOutcome.ProceedToChecksum -> Unit
                                is DiffWriteOutcome.Protected -> {
                                    if (byteIdx == 0 && session?.block0Touched != true) {
                                        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                                    }
                                    return FlashOperationResult.Failure(zeroRes.reason)
                                }
                                is DiffWriteOutcome.ProtocolError -> {
                                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                    return pauseWithWatchdog(
                                        context, session, detectedInfo, fullBuffer, -1, blockCount,
                                        zeroRes.reason, isUsbDetached = false,
                                        block0OriginalExtracted = false, block0DiffNextIndex = byteIdx, block0DiffPrevCs = csPrev,
                                        activeDiffBlockIndex = 0, activeDiffByteIndex = byteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                    )
                                }
                                is DiffWriteOutcome.TransportLost -> {
                                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                    return pauseWithWatchdog(
                                        context, session, detectedInfo, fullBuffer, -1, blockCount,
                                        zeroRes.reason, isUsbDetached = zeroRes.isUsbDetached,
                                        block0OriginalExtracted = false, block0DiffNextIndex = byteIdx, block0DiffPrevCs = csPrev,
                                        activeDiffBlockIndex = 0, activeDiffByteIndex = byteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                    )
                                }
                            }

                            // Query new Checksum CS_curr after zeroing byte[byteIdx]
                            val currCsRes = queryChecksum16WithRetry(
                                transport = transport,
                                startAddr = 0x000000,
                                endAddr = BLOCK_SIZE - 1,
                                clockFreqMhzText = clockFreqMhzText,
                                opLabel = "0xB0 تفاضلي Block 0 [$byteIdx]",
                                maxRetries = 2,
                                verboseLog = false,
                                onLog = onLog
                            )
                            val csCurr = when (currCsRes) {
                                is ChecksumQueryResult.Success -> currCsRes.checksum16 and 0xFFFF
                                is ChecksumQueryResult.TransportLost -> {
                                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                    return pauseWithWatchdog(
                                        context, session, detectedInfo, fullBuffer, -1, blockCount,
                                        currCsRes.reason, isUsbDetached = currCsRes.isUsbDetached,
                                        block0OriginalExtracted = false, block0DiffNextIndex = byteIdx, block0DiffPrevCs = csPrev,
                                        activeDiffBlockIndex = 0, activeDiffByteIndex = byteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                    )
                                }
                                is ChecksumQueryResult.ProtocolError -> {
                                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                    return pauseWithWatchdog(
                                        context, session, detectedInfo, fullBuffer, -1, blockCount,
                                        "فشل 0xB0 عند البايت $byteIdx من Block 0: ${currCsRes.statusLabel}",
                                        isUsbDetached = false,
                                        block0OriginalExtracted = false, block0DiffNextIndex = byteIdx, block0DiffPrevCs = csPrev,
                                        activeDiffBlockIndex = 0, activeDiffByteIndex = byteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                    )
                                }
                                is ChecksumQueryResult.ParameterError -> {
                                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                    return pauseWithWatchdog(
                                        context, session, detectedInfo, fullBuffer, -1, blockCount,
                                        "خطأ معاملات (0x05) في 0xB0 عند البايت $byteIdx",
                                        isUsbDetached = false,
                                        block0OriginalExtracted = false, block0DiffNextIndex = byteIdx, block0DiffPrevCs = csPrev,
                                        activeDiffBlockIndex = 0, activeDiffByteIndex = byteIdx, activeDiffBlockOrigCs = detectedInfo.block0Checksum16
                                    )
                                }
                            }

                            val extractedByte = Nec78k0Block0Reconstructor.extractDifferentialByteFromChecksumDelta(
                                csPrev = csPrev,
                                csCurr = csCurr,
                                offset = byteIdx
                            )
                            b0Working[byteIdx] = extractedByte.toByte()
                            csPrev = csCurr

                            if (byteIdx % 8 == 0 || byteIdx == BLOCK_SIZE - 1) {
                                val etaB0 = formatEtaSuffix(byteIdx + 1, startByteIdx, BLOCK_SIZE, stage2StartMs)
                                val labelB0 = String.format(
                                    Locale.US,
                                    "استخراج Block 0 تفاضلياً: %d/1024 (0x%02X)%s",
                                    byteIdx + 1, extractedByte, etaB0
                                )
                                onProgress(byteIdx + 1, totalSize, labelB0)
                                ProgrammerForegroundService.renewSessionLock(context, labelB0)
                            }

                            if (byteIdx % 16 == 15 || byteIdx == BLOCK_SIZE - 1) {
                                System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                                val nextIdx = byteIdx + 1
                                val b0Done = (byteIdx == BLOCK_SIZE - 1)
                                val interimSession = (session ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
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
                                )).copy(
                                    updatedAtMs = System.currentTimeMillis(),
                                    block0Touched = true,
                                    block0OriginalExtracted = b0Done,
                                    block0DiffNextIndex = nextIdx,
                                    block0DiffPrevCs = csPrev,
                                    activeDiffBlockIndex = if (b0Done) -1 else 0,
                                    activeDiffByteIndex = if (b0Done) 0 else nextIdx,
                                    activeDiffBlockOrigCs = if (b0Done) -1 else detectedInfo.block0Checksum16
                                )
                                session = interimSession
                                Nec78k0RecoveryManager.saveSessionCheckpoint(context, interimSession)
                            }
                        }
                        b0AlreadyAllZeroedOnChip = (csPrev == 0x0004)
                    }

                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                    val verifiedB0ByteCs = Nec78k0Block0Reconstructor.compute78k0Checksum16(b0Working)
                    val verifiedB0WordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(b0Working)
                    val verifiedB0DirectCs = Nec78k0Block0Reconstructor.compute78k0Word16DirectChecksum(b0Working)
                    val targetB0Cs = detectedInfo.block0Checksum16 and 0xFFFF
                    if (targetB0Cs != 0 && !Nec78k0Block0Reconstructor.matches78k0Checksum16(b0Working, targetB0Cs)) {
                        hadBlockChecksumWarning = true
                        onLog(
                            String.format(
                                Locale.US,
                                "[WARN] اكتمل البلوك 0 (1024/1024B) — متابعة للبلوك التالي (المحسوب Byte=%04X / Word16=%04X / Direct=%04X != الأصلي=%04X)",
                                verifiedB0ByteCs, verifiedB0WordCs, verifiedB0DirectCs, targetB0Cs
                            )
                        )
                    } else {
                        val matchedB0Cs = when {
                            verifiedB0WordCs == targetB0Cs || Nec78k0Block0Reconstructor.swap16(verifiedB0WordCs) == targetB0Cs -> verifiedB0WordCs
                            verifiedB0DirectCs == targetB0Cs || Nec78k0Block0Reconstructor.swap16(verifiedB0DirectCs) == targetB0Cs -> verifiedB0DirectCs
                            else -> verifiedB0ByteCs
                        }
                        onLog(
                            String.format(
                                Locale.US,
                                "تم استخراج Original_Byte[0..1023] كاملاً وتطابقت بصمته مع Block0_Checksum الأصلي (%04X) بنسبة 100%%.",
                                matchedB0Cs
                            )
                        )
                    }
                }

                // Only erase+zero Block 0 if not already zeroed on silicon AND not fast-filled uniform Block 0
                val skipZeroWriteOnB0 = b0AlreadyAllZeroedOnChip || (b0ExtractedNonDestructively && b0FastFillByte != null)
                val b0WasZeroed = if (!skipZeroWriteOnB0) {
                    onLog("المرحلة 2: مسح Block 0 (0x000000..0x0003FF) بأمر 0x22 ثم كتابة 1024 بايت من الأصفار (0x00) بأمر 0x40 لفتح القراءة...")
                    val eraseB0Cmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1)
                    when (val eraseRes = transceiveWithTimeoutRetry(transport, eraseB0Cmd, HEADER_ACK_TIMEOUT_MS, clockFreqMhzText, "مسح Block 0 (0x22)", onLog)) {
                        is Nec78k0TransceiveResult.Success -> {
                            onLog("RX (Block 0 Erase ACK): ${toHex(eraseRes.frame.rawFrame)}")
                        }
                        is Nec78k0TransceiveResult.ProtocolError -> {
                            if (eraseRes.statusCode == STS_PROTECT_ERROR) {
                                Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                                return FlashOperationResult.Failure(
                                    "المعالج محمي برمجياً ضد القراءة/المسح (ERR_PROTECT 0x10)."
                                )
                            }
                            return FlashOperationResult.Failure("فشل مسح Block 0 (0x22): ${eraseRes.statusLabel}")
                        }
                        is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                            return if (eraseRes.isUsbDetached) {
                                FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:${eraseRes.reason}")
                            } else {
                                FlashOperationResult.Failure("انتهت مهلة مسح Block 0 (0x22): ${eraseRes.reason}")
                            }
                        }
                    }
                    delay(20)

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
                                    bytesInBlock, BLOCK_SIZE, bytesInBlock / PAGE_SIZE, PAGES_PER_BLOCK
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
                                FlashOperationResult.Failure("انتهت مهلة تصفير Block 0 (0x40): ${zeroOutcome.reason}")
                            }
                        }
                    }
                    true
                } else {
                    !b0ExtractedNonDestructively // true if differential extraction just zeroed Block 0
                }

                val b0UntouchedOnChip = b0ExtractedNonDestructively && b0FastFillByte != null
                val b0ExtractedFlag = !alreadyBlankLegacy
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
                    block0Touched = !b0UntouchedOnChip && (b0WasZeroed || !b0ExtractedNonDestructively),
                    block0OriginalExtracted = b0ExtractedFlag,
                    block0DiffNextIndex = BLOCK_SIZE,
                    block0DiffPrevCs = 0,
                    block0Restored = b0UntouchedOnChip,
                    activeDiffBlockIndex = -1,
                    activeDiffByteIndex = 0,
                    activeDiffBlockOrigCs = -1
                )
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)
                onLog(
                    if (b0UntouchedOnChip) {
                        "تم حفظ Block 0 السريع (${"%02X".format(b0FastFillByte!!.toInt() and 0xFF)}) دون تعديل الشريحة."
                    } else {
                        "تم حفظ نسخة Original_Byte[0..1023] بأمان (Block 0 جاهز للمرحلة التالية)."
                    }
                )
            } else {
                // Already extracted or blank legacy: keep checkpoint synced
                val b0ExtractedFlag = !alreadyBlankLegacy
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
                    stage = Nec78k0RecoveryManager.RecoveryStage.BLOCK0_ZEROED,
                    lastCompletedBlock = 0,
                    updatedAtMs = System.currentTimeMillis(),
                    partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                    block0Touched = true,
                    block0OriginalExtracted = b0ExtractedFlag
                )).copy(
                    stage = Nec78k0RecoveryManager.RecoveryStage.BLOCK0_ZEROED,
                    lastCompletedBlock = 0,
                    updatedAtMs = System.currentTimeMillis(),
                    block0OriginalExtracted = b0ExtractedFlag,
                    activeDiffBlockIndex = -1,
                    activeDiffByteIndex = 0,
                    activeDiffBlockOrigCs = -1
                )
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)
            }

            if (blockCount > 1) {
                delay(20)
                val reSyncRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (reSyncRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    return if (reSyncRes.isUsbDetached) {
                        FlashOperationResult.Failure("${WATCHDOG_PREFIX}0:انقطع اتصال USB OTG بعد المرحلة 2 — ثبّت المبرمجة واضغط إنقاذ المعالج.")
                    } else {
                        FlashOperationResult.Failure("انتهت مهلة المزامنة بعد المرحلة 2: ${reSyncRes.reason}")
                    }
                }
            }
        }

        // المرحلة 3: قراءة واستخراج البلوكات 1 إلى N-1 (أمر 0x18 أو الاستخراج التفاضلي + الاستعادة الفورية Block-by-Block)
        val startBlock = (session?.lastCompletedBlock ?: 0).coerceAtLeast(0) + 1
        if (startBlock < blockCount) {
            onLog("المرحلة 3: معالجة البلوكات من Block $startBlock إلى Block ${blockCount - 1} (الاستعادة التلقائية الفورية للمعالج: ${if (autoRestoreChip) "مفعّلة" else "موقفة"})...")
        }

        val extractionStartMs = System.currentTimeMillis()
        val initialDoneBytes = minOf(startBlock * BLOCK_SIZE, totalSize)
        var supports1024Read: Boolean? = null
        var directRead0x18Unsupported = (session?.activeDiffBlockIndex ?: -1) >= startBlock
        var twoVectorVerifyPassedInExtraction = false

        for (blockIdx in startBlock until blockCount) {
            currentCoroutineContext().ensureActive()
            ProgrammerForegroundService.awaitIfPaused()
            if (!transport.isDeviceAttached()) {
                return pauseWithWatchdog(
                    context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount,
                    "فُصل كابل USB OTG عند Block $blockIdx", isUsbDetached = true
                )
            }

            val blockStart = blockIdx * BLOCK_SIZE
            val blockEnd = blockStart + BLOCK_SIZE - 1
            val etaNow = formatEtaSuffix(blockStart, initialDoneBytes, totalSize, extractionStartMs)
            ProgrammerForegroundService.renewSessionLock(context, "قراءة 0x18: Block $blockIdx/${blockCount - 1}$etaNow")
            onProgress(blockStart, totalSize, "قراءة Block $blockIdx/${blockCount - 1} (أمر 0x18)$etaNow")

            var verifiedBlockBytes: ByteArray? = null
            var lastCandidateReadBytes: ByteArray? = null
            var lastFailureWasChecksumMismatch = false
            var blockFailureReason = "فشل قراءة وتوثيق Block $blockIdx"
            var blockFailureIsUsb = false

            var pausedDiffByteIdx = -1
            var pausedDiffOrigCs = -1

            // When resuming mid-block inside Block K, immediately restore Block 0 first if touched and skip 0x18
            val resumingThisBlockMidDiff = (session?.activeDiffBlockIndex == blockIdx && (session?.activeDiffByteIndex ?: 0) > 0)
            if ((directRead0x18Unsupported || resumingThisBlockMidDiff) &&
                autoRestoreChip && session?.block0Touched == true && session?.block0Restored != true
            ) {
                onLog("الاستعادة التلقائية الفورية: إرجاع Block 0 الأصلي وتوثيقه (0x22 + 0x40 + 0x13 + 0xB0) قبل متابعة البلوكات التفاضلية...")
                when (val b0RestoreRes = restoreAndVerifyBlockAtomically(
                    transport = transport,
                    blockIdx = 0,
                    blockStart = 0x000000,
                    blockEnd = BLOCK_SIZE - 1,
                    blockData = fullBuffer.copyOfRange(0, BLOCK_SIZE),
                    expectedCs16 = detectedInfo.block0Checksum16.takeIf { it != 0 },
                    clockFreqMhzText = clockFreqMhzText,
                    onLog = onLog
                )) {
                    is RestoreBlockOutcome.Verified -> {
                        if (b0RestoreRes.warningNote != null) hadBlockChecksumWarning = true
                        session = session?.copy(block0Restored = true, updatedAtMs = System.currentTimeMillis())
                        session?.let { Nec78k0RecoveryManager.saveSessionCheckpoint(context, it) }
                    }
                    is RestoreBlockOutcome.Protected -> {
                        return FlashOperationResult.Failure(b0RestoreRes.reason)
                    }
                    is RestoreBlockOutcome.TransportLost -> {
                        return pauseWithWatchdog(
                            context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount,
                            b0RestoreRes.reason, isUsbDetached = b0RestoreRes.isUsbDetached
                        )
                    }
                    is RestoreBlockOutcome.Failed -> {
                        hadBlockChecksumWarning = true
                        onLog("[WARN] ${b0RestoreRes.reason}")
                    }
                }
            }

            for (attempt in 0..2) {
                if (attempt > 0) {
                    if (!transport.isDeviceAttached()) {
                        blockFailureIsUsb = true
                        blockFailureReason = "انقطع اتصال USB OTG أثناء إعادة محاولة Block $blockIdx"
                        break
                    }
                    onLog("إعادة المحاولة لـ Block $blockIdx بعد إعادة المزامنة ($attempt/2)...")
                    val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                    if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                        blockFailureIsUsb = rs.isUsbDetached
                        blockFailureReason = rs.reason
                        if (rs.isUsbDetached) break
                    }
                }

                val readBytes = ByteArray(BLOCK_SIZE)
                var readFailed = false

                if (!directRead0x18Unsupported && supports1024Read != false) {
                    when (val fullReadRes = readRangeVia0x18(
                        transport = transport,
                        startAddr = blockStart,
                        endAddr = blockEnd,
                        expectedLength = BLOCK_SIZE,
                        clockFreqMhzText = clockFreqMhzText,
                        label = "قراءة Block $blockIdx (0x18)",
                        onBytesReceived = { rxSoFar ->
                            val liveDone = minOf(blockStart + rxSoFar, totalSize)
                            val liveEta = formatEtaSuffix(liveDone, initialDoneBytes, totalSize, extractionStartMs)
                            onProgress(liveDone, totalSize, "قراءة Block $blockIdx/${blockCount - 1} ($rxSoFar/1024 B)$liveEta")
                        },
                        onLog = onLog
                    )) {
                        is Read0x18Result.Success -> {
                            supports1024Read = true
                            System.arraycopy(fullReadRes.bytes, 0, readBytes, 0, BLOCK_SIZE)
                        }
                        is Read0x18Result.ParameterUnsupported -> {
                            supports1024Read = false
                            onLog("ملاحظة: أمر 0x18 بنطاق 1024B أرجع 0x05 — التبديل إلى قراءة 4 صفحات × 256B بأمر 0x18.")
                        }
                        is Read0x18Result.CommandUnsupported -> {
                            directRead0x18Unsupported = true
                            onLog("ملاحظة: أمر القراءة المباشر 0x18 غير مدعوم (${fullReadRes.statusLabel}) — التحويل إلى مسار 0x32/0xB0/0x13 والاستخراج التفاضلي Block-by-Block...")
                        }
                        is Read0x18Result.TransportLost -> {
                            blockFailureIsUsb = fullReadRes.isUsbDetached
                            blockFailureReason = fullReadRes.reason
                            readFailed = true
                            if (fullReadRes.isUsbDetached) break
                        }
                        is Read0x18Result.ProtocolError -> {
                            if (fullReadRes.statusCode == STS_PROTECT_ERROR) {
                                return pauseWithWatchdog(
                                    context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount,
                                    "استلمنا ERR_PROTECT (0x10) عند قراءة Block $blockIdx عبر 0x18", isUsbDetached = false
                                )
                            }
                            blockFailureReason = "رفض المعالج أمر 0x18 لـ Block $blockIdx: ${fullReadRes.statusLabel}"
                            readFailed = true
                        }
                    }
                }

                if (!directRead0x18Unsupported && !readFailed && supports1024Read == false) {
                    for (pageIdx in 0 until PAGES_PER_BLOCK) {
                        val pStart = blockStart + (pageIdx * PAGE_SIZE)
                        val pEnd = pStart + PAGE_SIZE - 1
                        when (val pageReadRes = readRangeVia0x18(
                            transport = transport,
                            startAddr = pStart,
                            endAddr = pEnd,
                            expectedLength = PAGE_SIZE,
                            clockFreqMhzText = clockFreqMhzText,
                            label = "قراءة Block $blockIdx صفحة ${pageIdx + 1}/4 (0x18)",
                            onBytesReceived = { rxSoFar ->
                                val totalInBlock = pageIdx * PAGE_SIZE + rxSoFar
                                val liveDone = minOf(blockStart + totalInBlock, totalSize)
                                val liveEta = formatEtaSuffix(liveDone, initialDoneBytes, totalSize, extractionStartMs)
                                onProgress(liveDone, totalSize, "قراءة Block $blockIdx/${blockCount - 1} ($totalInBlock/1024 B)$liveEta")
                            },
                            onLog = onLog
                        )) {
                            is Read0x18Result.Success -> {
                                System.arraycopy(pageReadRes.bytes, 0, readBytes, pageIdx * PAGE_SIZE, PAGE_SIZE)
                            }
                            is Read0x18Result.ParameterUnsupported,
                            is Read0x18Result.CommandUnsupported -> {
                                directRead0x18Unsupported = true
                                onLog("ملاحظة: أمر القراءة 0x18 مرفوض على 256B أيضاً — التحويل إلى مسار 0x32 Blank Check + 0xB0 Checksum + الاستخراج التفاضلي Block-by-Block...")
                                break
                            }
                            is Read0x18Result.TransportLost -> {
                                blockFailureIsUsb = pageReadRes.isUsbDetached
                                blockFailureReason = pageReadRes.reason
                                readFailed = true
                                break
                            }
                            is Read0x18Result.ProtocolError -> {
                                blockFailureReason = "خطأ في أمر القراءة 0x18 لـ Block $blockIdx صفحة ${pageIdx + 1}: ${pageReadRes.statusLabel}"
                                readFailed = true
                                break
                            }
                        }
                    }
                }

                // If 0x18 was just discovered to be unsupported, restore Block 0 immediately before touching Block K
                if (directRead0x18Unsupported && autoRestoreChip && session?.block0Touched == true && session?.block0Restored != true) {
                    onLog("الاستعادة التلقائية الفورية: إرجاع Block 0 الأصلي وتوثيقه (0x22 + 0x40 + 0x13 + 0xB0) قبل متابعة Block $blockIdx...")
                    when (val b0RestoreRes = restoreAndVerifyBlockAtomically(
                        transport = transport,
                        blockIdx = 0,
                        blockStart = 0x000000,
                        blockEnd = BLOCK_SIZE - 1,
                        blockData = fullBuffer.copyOfRange(0, BLOCK_SIZE),
                        expectedCs16 = detectedInfo.block0Checksum16.takeIf { it != 0 },
                        clockFreqMhzText = clockFreqMhzText,
                        onLog = onLog
                    )) {
                        is RestoreBlockOutcome.Verified -> {
                            if (b0RestoreRes.warningNote != null) hadBlockChecksumWarning = true
                            session = session?.copy(block0Restored = true, updatedAtMs = System.currentTimeMillis())
                            session?.let { Nec78k0RecoveryManager.saveSessionCheckpoint(context, it) }
                        }
                        is RestoreBlockOutcome.Protected -> return FlashOperationResult.Failure(b0RestoreRes.reason)
                        is RestoreBlockOutcome.TransportLost -> {
                            return pauseWithWatchdog(
                                context, session, detectedInfo, fullBuffer, blockIdx - 1, blockCount,
                                b0RestoreRes.reason, isUsbDetached = b0RestoreRes.isUsbDetached
                            )
                        }
                        is RestoreBlockOutcome.Failed -> {
                            hadBlockChecksumWarning = true
                            onLog("[WARN] ${b0RestoreRes.reason}")
                        }
                    }
                }

                var blockAlreadyHandledWithWarning = false
                if (directRead0x18Unsupported) {
                    val resumeByteOffset = if (session?.activeDiffBlockIndex == blockIdx) {
                        session.activeDiffByteIndex.coerceIn(0, BLOCK_SIZE)
                    } else 0
                    val resumeOrigCs = if (session?.activeDiffBlockIndex == blockIdx) {
                        session.activeDiffBlockOrigCs
                    } else -1

                    val bypassOutcome = if (resumeByteOffset > 0) {
                        NonStandardBlockOutcome.LockedNonUniform("استئناف الاستخراج التفاضلي للكتلة $blockIdx من البايت $resumeByteOffset/1024")
                    } else {
                        tryExtractUniformBlockVia0x32And0x13(
                            transport = transport,
                            startAddr = blockStart,
                            endAddr = blockEnd,
                            clockFreqMhzText = clockFreqMhzText,
                            requireTwoVectorVerify = !twoVectorVerifyPassedInExtraction,
                            onLog = onLog
                        )
                    }

                    when (bypassOutcome) {
                        is NonStandardBlockOutcome.VerifiedBytes -> {
                            System.arraycopy(bypassOutcome.bytes, 0, readBytes, 0, BLOCK_SIZE)
                            if (bypassOutcome.twoVectorConfirmed) {
                                twoVectorVerifyPassedInExtraction = true
                            }
                            if (bypassOutcome.checksumWarning) {
                                hadBlockChecksumWarning = true
                                blockAlreadyHandledWithWarning = true
                            }
                        }
                        is NonStandardBlockOutcome.LockedNonUniform -> {
                            if (resumeByteOffset == 0) {
                                onLog("الكتلة Block $blockIdx (0x%06X..0x%06X) محمية على 0x18 — تفعيل الاستخراج التفاضلي التراكمي Block-by-Block (0x40 PageBuffer + 0xB0)...".format(Locale.US, blockStart, blockEnd))
                            }
                            when (val diffBlkRes = extractAndRestoreBlockViaDifferential0x40And0xB0(
                                context = context,
                                transport = transport,
                                sessionRef = session,
                                detectedInfo = detectedInfo,
                                fullBuffer = fullBuffer,
                                blockIdx = blockIdx,
                                blockCount = blockCount,
                                blockStart = blockStart,
                                blockEnd = blockEnd,
                                totalSize = totalSize,
                                resumeByteIdx = resumeByteOffset,
                                cachedOriginalCs = resumeOrigCs,
                                autoRestoreChip = autoRestoreChip,
                                clockFreqMhzText = clockFreqMhzText,
                                onSessionUpdated = { updated -> session = updated },
                                onProgress = onProgress,
                                onLog = onLog
                            )) {
                                is NonStandardBlockOutcome.VerifiedBytes -> {
                                    System.arraycopy(diffBlkRes.bytes, 0, readBytes, 0, BLOCK_SIZE)
                                    blockAlreadyHandledWithWarning = diffBlkRes.alreadyVerified || diffBlkRes.checksumWarning
                                    if (diffBlkRes.checksumWarning) {
                                        hadBlockChecksumWarning = true
                                    }
                                }
                                is NonStandardBlockOutcome.TransportLost -> {
                                    blockFailureIsUsb = diffBlkRes.isUsbDetached
                                    blockFailureReason = diffBlkRes.reason
                                    pausedDiffByteIdx = diffBlkRes.pausedByteIdx
                                    pausedDiffOrigCs = diffBlkRes.pausedOrigCs
                                    readFailed = true
                                }
                                is NonStandardBlockOutcome.LockedNonUniform -> {
                                    onLog("تنبيه أمني: تعذر استخراج Block $blockIdx تفاضلياً (${diffBlkRes.reason}) — استعادة Block 0 الأصلي إن لزم...")
                                    if (autoRestoreChip && session?.block0Touched == true && session?.block0Restored != true) {
                                        restoreOriginalBlock0Safely(
                                            transport = transport,
                                            block0Bytes = fullBuffer.copyOfRange(0, BLOCK_SIZE),
                                            clockFreqMhzText = clockFreqMhzText,
                                            onLog = onLog
                                        )
                                    }
                                    Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                                    return FlashOperationResult.Failure(diffBlkRes.reason)
                                }
                            }
                        }
                        is NonStandardBlockOutcome.TransportLost -> {
                            blockFailureIsUsb = bypassOutcome.isUsbDetached
                            blockFailureReason = bypassOutcome.reason
                            readFailed = true
                        }
                    }
                }

                if (readFailed) {
                    if (blockFailureIsUsb) break
                    continue
                }

                if (blockAlreadyHandledWithWarning) {
                    verifiedBlockBytes = readBytes
                    break
                }

                // Mandatory Checksum Verification for the read block via 0xB0 (Byte-Sum, Word-16-Sum, Word-16-Direct + swap16)
                val localBlockByteCs = Nec78k0Block0Reconstructor.compute78k0Checksum16(readBytes)
                val localBlockWordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(readBytes)
                val localBlockDirectCs = Nec78k0Block0Reconstructor.compute78k0Word16DirectChecksum(readBytes)
                when (val hwCsRes = queryChecksum16WithRetry(
                    transport = transport,
                    startAddr = blockStart,
                    endAddr = blockEnd,
                    clockFreqMhzText = clockFreqMhzText,
                    opLabel = "تحقق بصمة Block $blockIdx (0xB0)",
                    maxRetries = 2,
                    verboseLog = false,
                    onLog = onLog
                )) {
                    is ChecksumQueryResult.Success -> {
                        val hwCs = hwCsRes.checksum16 and 0xFFFF
                        if (Nec78k0Block0Reconstructor.matches78k0Checksum16(readBytes, hwCs)) {
                            verifiedBlockBytes = readBytes
                            break
                        } else {
                            lastCandidateReadBytes = readBytes.copyOf()
                            lastFailureWasChecksumMismatch = true
                            blockFailureReason = String.format(
                                Locale.US,
                                "عدم تطابق Checksum (0xB0) لـ Block %d: المحسوب=(%04X/%04X/%04X) != المعالج=%04X",
                                blockIdx,
                                localBlockByteCs and 0xFFFF,
                                localBlockWordCs and 0xFFFF,
                                localBlockDirectCs and 0xFFFF,
                                hwCs
                            )
                            onLog("[WARN] $blockFailureReason")
                        }
                    }
                    is ChecksumQueryResult.ParameterError -> {
                        blockFailureReason = "خطأ معاملات (0x05) عند فحص بصمة Block $blockIdx بـ 0xB0"
                        onLog(blockFailureReason)
                    }
                    is ChecksumQueryResult.ProtocolError -> {
                        blockFailureReason = "فشل فحص بصمة Block $blockIdx (0xB0): ${hwCsRes.statusLabel}"
                        onLog(blockFailureReason)
                    }
                    is ChecksumQueryResult.TransportLost -> {
                        blockFailureIsUsb = hwCsRes.isUsbDetached
                        blockFailureReason = hwCsRes.reason
                        if (hwCsRes.isUsbDetached) break
                    }
                }
            }

            if (verifiedBlockBytes == null) {
                if (lastFailureWasChecksumMismatch && lastCandidateReadBytes != null && !blockFailureIsUsb) {
                    hadBlockChecksumWarning = true
                    onLog("[WARN] اكتمل البلوك $blockIdx (1024/1024B) — متابعة للبلوك التالي ($blockFailureReason)")
                    verifiedBlockBytes = lastCandidateReadBytes
                } else {
                    return pauseWithWatchdog(
                        context = context,
                        session = session,
                        info = detectedInfo,
                        partialBuffer = fullBuffer,
                        lastGoodBlock = blockIdx - 1,
                        blockCount = blockCount,
                        reason = blockFailureReason,
                        isUsbDetached = blockFailureIsUsb,
                        activeDiffBlockIndex = if (pausedDiffByteIdx >= 0) blockIdx else -1,
                        activeDiffByteIndex = if (pausedDiffByteIdx >= 0) pausedDiffByteIdx else 0,
                        activeDiffBlockOrigCs = if (pausedDiffByteIdx >= 0) pausedDiffOrigCs else -1
                    )
                }
            }

            System.arraycopy(verifiedBlockBytes, 0, fullBuffer, blockStart, BLOCK_SIZE)

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
                block0Touched = true,
                block0OriginalExtracted = true
            )).copy(
                stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
                lastCompletedBlock = blockIdx,
                updatedAtMs = System.currentTimeMillis(),
                activeDiffBlockIndex = -1,
                activeDiffByteIndex = 0,
                activeDiffBlockOrigCs = -1
            )
            session = updatedSession

            if (blockIdx % 4 == 0 || blockIdx == blockCount - 1) {
                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, updatedSession)
            }

            val bytesDone = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            val etaLabel = formatEtaSuffix(bytesDone, initialDoneBytes, totalSize, extractionStartMs)
            onProgress(
                bytesDone,
                totalSize,
                String.format(Locale.US, "تم قراءة وتوثيق Block %d/%d (0x%06X)%s", blockIdx, blockCount - 1, blockStart, etaLabel)
            )
            ProgrammerForegroundService.renewSessionLock(context, "قراءة Block $blockIdx/${blockCount - 1}$etaLabel")
            if (blockIdx % 4 == 0 || blockIdx == blockCount - 1) {
                onLog(
                    String.format(
                        Locale.US,
                        "تمت قراءة وتوثيق Block %d/%d (%d/%d KB • 0xB0=%04X)%s",
                        blockIdx, blockCount - 1, bytesDone / 1024, totalSize / 1024,
                        Nec78k0Block0Reconstructor.compute78k0Checksum16(verifiedBlockBytes),
                        etaLabel
                    )
                )
            }
        }

        // Persist BLOCKS_EXTRACTED checkpoint before Step 4 Restore
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
            block0Touched = true,
            block0OriginalExtracted = true
        )).copy(
            stage = Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED,
            lastCompletedBlock = blockCount - 1,
            updatedAtMs = System.currentTimeMillis(),
            block0Touched = true
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, session)

        // المرحلة 4: إعادة كتابة Original_Byte[0..1023] المحفوظ إلى Block 0 (إن لم يُستعد مسبقاً) + التحقق من Full Flash Checksum 0xB0
        if (autoRestoreChip && session.block0Touched && !session.block0Restored) {
            onProgress(totalSize - BLOCK_SIZE, totalSize, "المرحلة 4: استعادة Block 0 الأصلي وتوثيقه (0x22 + 0x40 + 0x13 + 0xB0)...")
            ProgrammerForegroundService.renewSessionLock(context, "استعادة Block 0 الأصلي ومطابقة 0x13/0xB0...")

            val savedBlock0Bytes = fullBuffer.copyOfRange(0, BLOCK_SIZE)
            val savedBlock0Cs = Nec78k0Block0Reconstructor.compute78k0Checksum16(savedBlock0Bytes)
            val hasExactOriginalBlock0 = (session.block0OriginalExtracted) ||
                (detectedInfo.block0Checksum16 != 0 && Nec78k0Block0Reconstructor.matches78k0Checksum16(savedBlock0Bytes, detectedInfo.block0Checksum16))

            val block0ToRestore = if (hasExactOriginalBlock0) {
                onLog(
                    String.format(
                        Locale.US,
                        "المرحلة 4: إعادة مسح Block 0 (0x22) وكتابة النسخة الأصلية Original_Byte[0..1023] (CS=%04X) عبر 0x40 ثم التحقق عبر 0x13 + 0xB0...",
                        savedBlock0Cs
                    )
                )
                savedBlock0Bytes
            } else {
                onLog("المرحلة 4 (إنقاذ احتياطي): لا تتوفر نسخة Original_Byte[0..1023] من جلسة قديمة — إعادة بناء Block 0 برمجياً...")
                val report = Nec78k0Block0Reconstructor.reconstructBlock0(
                    fullFlashBuffer = fullBuffer,
                    targetBlock0Checksum16 = detectedInfo.block0Checksum16,
                    targetFullChecksum16 = detectedInfo.fullFlashChecksum16
                )
                System.arraycopy(report.block0Bytes, 0, fullBuffer, 0, BLOCK_SIZE)
                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                report.block0Bytes
            }

            when (val b0Restore = restoreAndVerifyBlockAtomically(
                transport = transport,
                blockIdx = 0,
                blockStart = 0x000000,
                blockEnd = BLOCK_SIZE - 1,
                blockData = block0ToRestore,
                expectedCs16 = detectedInfo.block0Checksum16.takeIf { it != 0 },
                clockFreqMhzText = clockFreqMhzText,
                onPageProgress = { bytesInBlock ->
                    onProgress(
                        minOf((totalSize - BLOCK_SIZE) + bytesInBlock, totalSize),
                        totalSize,
                        String.format(Locale.US, "استعادة Block 0 الأصلي: %d/%d بايت", bytesInBlock, BLOCK_SIZE)
                    )
                },
                onLog = onLog
            )) {
                is RestoreBlockOutcome.Verified -> {
                    if (b0Restore.warningNote != null) hadBlockChecksumWarning = true
                    session = session?.copy(block0Restored = true, updatedAtMs = System.currentTimeMillis())
                    session?.let { Nec78k0RecoveryManager.saveSessionCheckpoint(context, it) }
                }
                is RestoreBlockOutcome.Protected -> {
                    return FlashOperationResult.Failure(b0Restore.reason)
                }
                is RestoreBlockOutcome.TransportLost -> {
                    return pauseWithWatchdog(
                        context, session, detectedInfo, fullBuffer, blockCount - 1, blockCount,
                        b0Restore.reason, isUsbDetached = b0Restore.isUsbDetached
                    )
                }
                is RestoreBlockOutcome.Failed -> {
                    return pauseWithWatchdog(
                        context, session, detectedInfo, fullBuffer, blockCount - 1, blockCount,
                        b0Restore.reason, isUsbDetached = false
                    )
                }
            }
        } else if (!autoRestoreChip) {
            onLog("ملاحظة: خيار «الاستعادة التلقائية الفورية للمعالج» متوقف — تم حفظ السوفت المسحوب على ذاكرة الهاتف بدون إعادة كتابته على الشريحة.")
        } else {
            onLog("المرحلة 4: تم التحقق مسبقاً من استعادة Block 0 وجميع البلوكات 100% — بدء فحص Full_Flash_Checksum النهائي (0xB0)...")
        }

        // Resync cleanly before Full Flash Checksum via 0xB0 against Stage 1
        delay(35)
        val finalSyncRes = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
        if (finalSyncRes is Nec78k0TransceiveResult.DisconnectedOrTimeout && finalSyncRes.isUsbDetached) {
            return pauseWithWatchdog(
                context, session, detectedInfo, fullBuffer, blockCount - 1, blockCount,
                finalSyncRes.reason, isUsbDetached = true
            )
        }

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

        val expectedFullCs = detectedInfo.fullFlashChecksum16 and 0xFFFF
        val actualFinalCs = finalCs and 0xFFFF
        val matchedOriginal = (expectedFullCs == 0) ||
            (actualFinalCs == expectedFullCs) ||
            (Nec78k0Block0Reconstructor.swap16(actualFinalCs) == expectedFullCs) ||
            Nec78k0Block0Reconstructor.matches78k0Checksum16(fullBuffer, expectedFullCs)

        onLog(
            String.format(
                Locale.US,
                "%sفحص Checksum النهائي (0xB0): الحالي=%04X / الأصلي=%04X (%s)",
                if (matchedOriginal) "" else "[WARN] ",
                actualFinalCs,
                expectedFullCs,
                if (matchedOriginal) "مطابق 100%" else "اختلاف — متابعة لإتمام السحب 100% وتفعيل الحفظ"
            )
        )

        var dummyVerified = true
        // Anti-Dummy Rule 1 & 3: Check Two-Vector 0x13 Verify if dump is >= 99% uniform 0x00/0xFF
        if (Nec78k0ProtocolRegistry.isDummyAllZeroOrFf(fullBuffer) && !twoVectorVerifyPassedInExtraction) {
            val probeBlockIdx = if (blockCount > 1) 1 else 0
            val probeStart = probeBlockIdx * BLOCK_SIZE
            val probeEnd = probeStart + BLOCK_SIZE - 1
            val probeSlice = fullBuffer.copyOfRange(probeStart, probeStart + BLOCK_SIZE)
            onLog(String.format(Locale.US, "فحص ضد البيانات الوهمية (Two-Vector 0x13) على Block %d (0x%06X..0x%06X)...", probeBlockIdx, probeStart, probeEnd))
            val twoVecOk = verifyTwoVector0x13Block(
                transport = transport,
                startAddr = probeStart,
                endAddr = probeEnd,
                expectedBlock = probeSlice,
                clockFreqMhzText = clockFreqMhzText,
                onLog = onLog
            )
            if (!twoVecOk) {
                if (!matchedOriginal && (expectedFullCs != 0xFC04 && expectedFullCs != 0x0004)) {
                    dummyVerified = false
                    onLog("[WARN] تنبيه: نسبة 0x00/0xFF >= 99% ولم يستجب المعالج لاختبار 0x13 — يرجى مراجعة البيانات المسحوبة قبل الحفظ.")
                }
            }
        }

        // Clean up checkpoint files once all blocks 0..N-1 reach 100%
        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
        onProgress(totalSize, totalSize, "اكتمل السحب 100% — جاهز لحفظ ملف .bin")

        val finishMsg = if (matchedOriginal && !hadBlockChecksumWarning) {
            "تم سحب السوفت وير واستعادة المعالج الأصلي بنجاح 100% (FullCS=${detectedInfo.fullFlashChecksumHex})"
        } else {
            "اكتمل سحب السوفت وير 100% (FullCS=${detectedInfo.fullFlashChecksumHex} • يرجى مراجعة تنبيهات Checksum في السجل) — جاهز لحفظ ملف .bin"
        }
        return FlashOperationResult.Success(
            message = finishMsg,
            bytesTransferred = fullBuffer,
            baseAddress = 0,
            isMcuVerified = dummyVerified
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
        isUsbDetached: Boolean,
        block0OriginalExtracted: Boolean = session?.block0OriginalExtracted ?: false,
        block0DiffNextIndex: Int = session?.block0DiffNextIndex ?: 0,
        block0DiffPrevCs: Int = session?.block0DiffPrevCs ?: -1,
        block0Restored: Boolean = session?.block0Restored ?: false,
        activeDiffBlockIndex: Int = session?.activeDiffBlockIndex ?: -1,
        activeDiffByteIndex: Int = session?.activeDiffByteIndex ?: 0,
        activeDiffBlockOrigCs: Int = session?.activeDiffBlockOrigCs ?: -1
    ): FlashOperationResult.Failure {
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, info.deviceName, partialBuffer)
        val nextStage = when {
            lastGoodBlock >= blockCount - 1 -> Nec78k0RecoveryManager.RecoveryStage.BLOCKS_EXTRACTED
            lastGoodBlock >= 0 -> Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS
            else -> Nec78k0RecoveryManager.RecoveryStage.DETECTED
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
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(-1),
            updatedAtMs = System.currentTimeMillis(),
            partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, info.deviceName).absolutePath,
            block0Touched = true,
            block0OriginalExtracted = block0OriginalExtracted,
            block0DiffNextIndex = block0DiffNextIndex,
            block0DiffPrevCs = block0DiffPrevCs,
            block0Restored = block0Restored,
            activeDiffBlockIndex = activeDiffBlockIndex,
            activeDiffByteIndex = activeDiffByteIndex,
            activeDiffBlockOrigCs = activeDiffBlockOrigCs
        )).copy(
            stage = nextStage,
            lastCompletedBlock = lastGoodBlock.coerceAtLeast(-1),
            updatedAtMs = System.currentTimeMillis(),
            block0Touched = session?.block0Touched ?: true,
            block0OriginalExtracted = block0OriginalExtracted,
            block0DiffNextIndex = block0DiffNextIndex,
            block0DiffPrevCs = block0DiffPrevCs,
            block0Restored = block0Restored,
            activeDiffBlockIndex = activeDiffBlockIndex,
            activeDiffByteIndex = activeDiffByteIndex,
            activeDiffBlockOrigCs = activeDiffBlockOrigCs
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, saved)
        val blockDisplay = if (activeDiffBlockIndex >= 0) {
            "$activeDiffBlockIndex (بايت $activeDiffByteIndex/1024)"
        } else {
            "${(saved.lastCompletedBlock + 1).coerceAtLeast(0)}"
        }
        return if (isUsbDetached) {
            FlashOperationResult.Failure(
                "${WATCHDOG_PREFIX}${saved.lastCompletedBlock.coerceAtLeast(0)}:انقطع اتصال USB OTG عند الكتلة $blockDisplay/$blockCount ($reason) — تم حفظ التقدم على الذاكرة، ثبّت المبرمجة للاستكمال."
            )
        } else {
            FlashOperationResult.Failure(
                "توقفت العملية عند الكتلة $blockDisplay/$blockCount ($reason) — منفذ USB لا يزال متصلاً، وتم حفظ التقدم بأمان على الذاكرة."
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

    private enum class ZeroWriteGranularity {
        ONE_BYTE,
        FOUR_BYTES,
        PAGE_256_BYTES,
        BLOCK_1024_BYTES
    }

    private sealed class DiffWriteOutcome {
        data object ProceedToChecksum : DiffWriteOutcome()
        data class Protected(val reason: String) : DiffWriteOutcome()
        data class ProtocolError(val reason: String) : DiffWriteOutcome()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : DiffWriteOutcome()
    }

    private sealed class Read0x18Result {
        data class Success(val bytes: ByteArray) : Read0x18Result()
        data object ParameterUnsupported : Read0x18Result()
        data class CommandUnsupported(val statusCode: Int, val statusLabel: String) : Read0x18Result()
        data class ProtocolError(val statusCode: Int, val statusLabel: String) : Read0x18Result()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : Read0x18Result()
    }

    private sealed class NonStandardBlockOutcome {
        data class VerifiedBytes(
            val bytes: ByteArray,
            val twoVectorConfirmed: Boolean,
            val checksumWarning: Boolean = false,
            val alreadyVerified: Boolean = false
        ) : NonStandardBlockOutcome()
        data class LockedNonUniform(val reason: String) : NonStandardBlockOutcome()
        data class TransportLost(
            val reason: String,
            val isUsbDetached: Boolean,
            val pausedByteIdx: Int = -1,
            val pausedOrigCs: Int = -1
        ) : NonStandardBlockOutcome()
    }

    private sealed class RestoreBlockOutcome {
        data class Verified(val warningNote: String? = null) : RestoreBlockOutcome()
        data class Protected(val reason: String) : RestoreBlockOutcome()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : RestoreBlockOutcome()
        data class Failed(val reason: String) : RestoreBlockOutcome()
    }

    internal sealed class ChecksumQueryResult {
        data class Success(val checksum16: Int) : ChecksumQueryResult()
        data object ParameterError : ChecksumQueryResult()
        data class ProtocolError(val statusCode: Int, val statusLabel: String) : ChecksumQueryResult()
        data class TransportLost(val reason: String, val isUsbDetached: Boolean) : ChecksumQueryResult()
    }

    private fun formatEtaSuffix(
        bytesDone: Int,
        initialDoneBytes: Int,
        totalBytes: Int,
        startTimeMs: Long
    ): String {
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        val delta = bytesDone - initialDoneBytes
        if (elapsedMs < 1500L || delta < 8 || bytesDone >= totalBytes) return ""
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

    /**
     * Writes 0x00 at [byteIdx] within [blockStart]..[blockStart + BLOCK_SIZE - 1]
     * while strictly keeping all earlier zeroed bytes in the same 256B page (`0 until pageOffset`) at 0x00
     * via [cumulativePageBuffer] (and unprogrammed bytes `pageOffset+1..255` at 0xFF).
     * Never overwrites already-zeroed bytes in the same page with 0xFF.
     */
    private suspend fun writeSingleByteZeroWithAutoGranularity(
        transport: Serial78k0Transport,
        byteIdx: Int,
        currentMode: ZeroWriteGranularity?,
        clockFreqMhzText: String,
        onLog: (String) -> Unit,
        blockStart: Int = 0x000000,
        cumulativePageBuffer: ByteArray? = null
    ): Pair<ZeroWriteGranularity, DiffWriteOutcome> {
        val byteAddr = blockStart + byteIdx
        val order = if (currentMode != null) {
            listOf(currentMode)
        } else {
            listOf(
                ZeroWriteGranularity.PAGE_256_BYTES,
                ZeroWriteGranularity.ONE_BYTE,
                ZeroWriteGranularity.FOUR_BYTES,
                ZeroWriteGranularity.BLOCK_1024_BYTES
            )
        }

        for (mode in order) {
            val (startAddr, endAddr, pages) = when (mode) {
                ZeroWriteGranularity.ONE_BYTE -> Triple(byteAddr, byteAddr, listOf(byteArrayOf(0x00)))
                ZeroWriteGranularity.FOUR_BYTES -> {
                    val wStart = byteAddr and 0xFFFFFC
                    val wEnd = wStart + 3
                    val offsetInWord = byteAddr - wStart
                    val buf = ByteArray(4) { idx -> if (idx <= offsetInWord) 0x00 else 0xFF.toByte() }
                    Triple(wStart, wEnd, listOf(buf))
                }
                ZeroWriteGranularity.PAGE_256_BYTES -> {
                    val pStart = byteAddr and 0xFFFF00
                    val pEnd = pStart + PAGE_SIZE - 1
                    val offsetInPage = byteAddr - pStart
                    val buf = if (cumulativePageBuffer != null && cumulativePageBuffer.size == PAGE_SIZE) {
                        cumulativePageBuffer.copyOf()
                    } else {
                        ByteArray(PAGE_SIZE) { idx -> if (idx <= offsetInPage) 0x00 else 0xFF.toByte() }
                    }
                    Triple(pStart, pEnd, listOf(buf))
                }
                ZeroWriteGranularity.BLOCK_1024_BYTES -> {
                    val list = (0 until PAGES_PER_BLOCK).map { page ->
                        ByteArray(PAGE_SIZE) { pOff ->
                            val globalIdx = page * PAGE_SIZE + pOff
                            if (globalIdx <= byteIdx) 0x00 else 0xFF.toByte()
                        }
                    }
                    Triple(blockStart, blockStart + BLOCK_SIZE - 1, list)
                }
            }

            var paramUnsupported = false
            var outcome: DiffWriteOutcome = DiffWriteOutcome.ProceedToChecksum
            val progCmd = buildAddressRangeFrame(CMD_PROGRAM, startAddr, endAddr)
            var attempt = 1
            var headerResyncNeeded = false

            while (attempt <= MAX_STX_PAGE_RETRIES) {
                if (headerResyncNeeded) {
                    if (!transport.isDeviceAttached()) {
                        return mode to DiffWriteOutcome.TransportLost("انقطع اتصال USB OTG أثناء كتابة 0x00 عند بايت $byteIdx", true)
                    }
                    onLog("إعادة المزامنة أثناء تصفير البايت $byteIdx (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                    val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                    if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                        if (rs.isUsbDetached) {
                            return mode to DiffWriteOutcome.TransportLost(rs.reason, true)
                        }
                        outcome = DiffWriteOutcome.TransportLost(rs.reason, false)
                        if (attempt >= MAX_STX_PAGE_RETRIES) break
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        attempt++
                        continue
                    }
                    headerResyncNeeded = false
                }

                when (val hdrRes = transceiveSingleFrame(transport, progCmd, HEADER_ACK_TIMEOUT_MS, onLog, verboseLog = false)) {
                    is Nec78k0TransceiveResult.Success -> Unit
                    is Nec78k0TransceiveResult.ProtocolError -> {
                        if (hdrRes.statusCode == STS_PARAMETER_ERROR && currentMode == null) {
                            paramUnsupported = true
                            break
                        }
                        if (hdrRes.statusCode == STS_PROTECT_ERROR) {
                            return mode to DiffWriteOutcome.Protected("الشريحة محمية (ERR_PROTECT 0x10): أمر 0x40 مرفوض عند البايت $byteIdx.")
                        }
                        if (hdrRes.statusCode == STS_CHECKSUM_ERROR && attempt < MAX_STX_PAGE_RETRIES) {
                            outcome = DiffWriteOutcome.ProtocolError("رفض المعالج أمر 0x40 للبايت $byteIdx: ${hdrRes.statusLabel}")
                            transport.flushInputBuffer()
                            delay(STX_RETRY_WAIT_MS)
                            headerResyncNeeded = (attempt >= 2)
                            attempt++
                            continue
                        }
                        return mode to DiffWriteOutcome.ProtocolError("رفض المعالج أمر 0x40 للبايت $byteIdx: ${hdrRes.statusLabel}")
                    }
                    is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                        if (hdrRes.isUsbDetached) {
                            return mode to DiffWriteOutcome.TransportLost(hdrRes.reason, true)
                        }
                        outcome = DiffWriteOutcome.TransportLost(hdrRes.reason, false)
                        if (attempt >= MAX_STX_PAGE_RETRIES) break
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        headerResyncNeeded = true
                        attempt++
                        continue
                    }
                }

                delay(if (mode == ZeroWriteGranularity.ONE_BYTE || mode == ZeroWriteGranularity.FOUR_BYTES) 5L else 10L)
                transport.flushInputBuffer()

                var needHeaderRestart = false
                var stopPageLoop = false
                for (pIdx in pages.indices) {
                    val isLast = (pIdx == pages.lastIndex)
                    val frame = buildVariableDataFrame(pages[pIdx], isLast = isLast)
                    var directStxRetried = false

                    while (true) {
                        when (val ack = sendStxDataPageAndAwaitAck(transport, frame, PAGE_ACK_TIMEOUT_MS, "0x40 B0[$byteIdx]", onLog, verboseLog = false)) {
                            is FrameAckOutcome.AckSuccess -> {
                                outcome = DiffWriteOutcome.ProceedToChecksum
                                break
                            }
                            is FrameAckOutcome.FrameError -> {
                                if (ack.errorCode == STS_PROTECT_ERROR) {
                                    return mode to DiffWriteOutcome.Protected("الشريحة محمية (ERR_PROTECT 0x10) عند بايت $byteIdx.")
                                }
                                if (ack.errorCode == STS_PARAMETER_ERROR && currentMode == null) {
                                    paramUnsupported = true
                                    break
                                }
                                // In multi-byte mask writes (0xFF over existing code bytes), internal post-write verify may return 0x1C after programming 0x00.
                                // If aborted before sending the final ETX page, resync the bootloader so subsequent 0xB0 does not hit an incomplete 0x40 stream.
                                if (isVerifyMismatchCode(ack.errorCode) && mode != ZeroWriteGranularity.ONE_BYTE) {
                                    if (pIdx < pages.lastIndex) {
                                        resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                                    }
                                    outcome = DiffWriteOutcome.ProceedToChecksum
                                    needHeaderRestart = false
                                    stopPageLoop = true
                                    break
                                }
                                if (ack.errorCode == STS_CHECKSUM_ERROR) {
                                    outcome = DiffWriteOutcome.ProtocolError("فشل كتابة 0x00 للبايت $byteIdx: ${ack.description}")
                                    if (attempt >= MAX_STX_PAGE_RETRIES) {
                                        return mode to outcome
                                    }
                                    transport.flushInputBuffer()
                                    delay(STX_RETRY_WAIT_MS)
                                    attempt++
                                    if (!directStxRetried && attempt == 2) {
                                        directStxRetried = true
                                        onLog("خطأ نقل 0x07 (Checksum Error) أثناء تصفير البايت $byteIdx — تفريغ RX والانتظار 50ms ثم إعادة إرسال STX (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                        continue
                                    } else {
                                        onLog("استمر خطأ 0x07 أثناء تصفير البايت $byteIdx — إعادة إرسال ترويسة 0x40 ثم الصفحة (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                        needHeaderRestart = true
                                        headerResyncNeeded = false
                                        break
                                    }
                                }
                                return mode to DiffWriteOutcome.ProtocolError("فشل كتابة 0x00 للبايت $byteIdx: ${ack.description}")
                            }
                            is FrameAckOutcome.UsbDetached -> {
                                return mode to DiffWriteOutcome.TransportLost(ack.reason, true)
                            }
                            is FrameAckOutcome.Timeout -> {
                                outcome = DiffWriteOutcome.TransportLost(ack.reason, false)
                                if (attempt >= MAX_STX_PAGE_RETRIES) {
                                    needHeaderRestart = true
                                    attempt = MAX_STX_PAGE_RETRIES + 1
                                    break
                                }
                                transport.flushInputBuffer()
                                delay(STX_RETRY_WAIT_MS)
                                attempt++
                                if (!directStxRetried && attempt == 2) {
                                    directStxRetried = true
                                    onLog("مهلة رد STX أثناء تصفير البايت $byteIdx — تفريغ RX والانتظار 50ms ثم إعادة إرسال STX (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                    continue
                                } else {
                                    onLog("استمرت مهلة STX أثناء تصفير البايت $byteIdx — إعادة المزامنة وترويسة 0x40 (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                    needHeaderRestart = true
                                    headerResyncNeeded = true
                                    break
                                }
                            }
                        }
                    }
                    if (paramUnsupported || needHeaderRestart || stopPageLoop) break
                }
                if (paramUnsupported) break
                if (!needHeaderRestart) {
                    if (currentMode == null && byteIdx == 0) {
                        onLog("نمط الكتابة التفاضلية النشط لـ Block 0 (0x40): ${mode.name}")
                    }
                    return mode to outcome
                }
            }
            if (!paramUnsupported) {
                return mode to outcome
            }
        }
        return ZeroWriteGranularity.BLOCK_1024_BYTES to DiffWriteOutcome.ProtocolError("رفض المعالج جميع أحجام إطار الكتابة 0x40 بـ 0x05.")
    }

    /**
     * Reads [expectedLength] bytes (1024B block or 256B page) from the unlocked 78K0 flash
     * using Command 0x18 ([01 07 18 Start(3B) End(3B) CS 03]) and receives STX (0x02) data frames.
     */
    private suspend fun readRangeVia0x18(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        expectedLength: Int,
        clockFreqMhzText: String,
        label: String,
        onBytesReceived: (Int) -> Unit,
        onLog: (String) -> Unit
    ): Read0x18Result {
        val readCmd = buildAddressRangeFrame(CMD_READ, startAddr, endAddr)
        var lastTimeoutReason = "انتهت مهلة القراءة 0x18"

        for (attempt in 0..MAX_TIMEOUT_RETRIES) {
            if (attempt > 0) {
                if (!transport.isDeviceAttached()) {
                    return Read0x18Result.TransportLost("انقطع اتصال USB OTG أثناء $label", isUsbDetached = true)
                }
                onLog("إعادة المحاولة لـ $label بعد المزامنة ($attempt/$MAX_TIMEOUT_RETRIES)...")
                val rs = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (rs is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (rs.isUsbDetached) return Read0x18Result.TransportLost(rs.reason, isUsbDetached = true)
                    lastTimeoutReason = rs.reason
                    continue
                }
            }

            if (attempt == 0) {
                onLog("TX ($label): ${toHex(readCmd)}")
            }
            when (val cmdRes = transceiveSingleFrame(transport, readCmd, HEADER_ACK_TIMEOUT_MS, onLog, verboseLog = true)) {
                is Nec78k0TransceiveResult.Success -> Unit
                is Nec78k0TransceiveResult.ProtocolError -> {
                    return when (cmdRes.statusCode) {
                        STS_PARAMETER_ERROR -> Read0x18Result.ParameterUnsupported
                        STS_COMMAND_ERROR -> Read0x18Result.CommandUnsupported(cmdRes.statusCode, cmdRes.statusLabel)
                        else -> Read0x18Result.ProtocolError(cmdRes.statusCode, cmdRes.statusLabel)
                    }
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (cmdRes.isUsbDetached) return Read0x18Result.TransportLost(cmdRes.reason, isUsbDetached = true)
                    lastTimeoutReason = cmdRes.reason
                    continue
                }
            }

            // Read incoming STX (0x02) data frames for this 0x18 command
            val out = ByteArrayOutputStream(expectedLength)
            val hostAckFrame = buildCommandFrame(STS_ACK)
            var frameTimedOut = false
            var frameBadCs = false
            var lastEndByte = ETB

            transport.setRawRxMonitor(false)
            while (out.size() < expectedLength) {
                currentCoroutineContext().ensureActive()
                if (!transport.isDeviceAttached()) {
                    return Read0x18Result.TransportLost("انقطع اتصال USB OTG أثناء استلام بيانات $label", isUsbDetached = true)
                }

                // Wait first 650ms silently for back-to-back STX data frames; if ETB pauses, send host ACK [01 01 06 F9 03]
                var rxFrame = readOneRxFrame(transport, if (out.size() == 0) PAGE_ACK_TIMEOUT_MS else 650) { stray ->
                    onLog("RX Raw Live ($label): $stray")
                }
                if (rxFrame == null && out.size() > 0 && lastEndByte == ETB && transport.isDeviceAttached()) {
                    transport.writeRaw(hostAckFrame)
                    transport.discardEcho(hostAckFrame.size, 9600)
                    rxFrame = readOneRxFrame(transport, PAGE_ACK_TIMEOUT_MS) { stray ->
                        onLog("RX Raw Live ($label بعد ACK): $stray")
                    }
                }

                if (rxFrame == null) {
                    lastTimeoutReason = "انتهت مهلة استلام إطار STX (0x02) في $label (تم استلام ${out.size()}/$expectedLength بايت)"
                    frameTimedOut = true
                    break
                }
                if (!rxFrame.checksumValid) {
                    onLog("خطأ Checksum في إطار بيانات $label [${toHex(rxFrame.rawFrame.copyOfRange(0, minOf(12, rxFrame.rawFrame.size)))}...]")
                    lastTimeoutReason = "خطأ Checksum أثناء استقبال $label"
                    frameBadCs = true
                    break
                }

                // Anti-Dummy Rule 2: Reject RX payload that echoes back TX command frame bytes
                if (rxFrame.rawFrame.contentEquals(readCmd) ||
                    (rxFrame.payload.size >= readCmd.size && rxFrame.payload.copyOfRange(0, readCmd.size).contentEquals(readCmd))
                ) {
                    onLog("تحذير (Echo Rejection): تم رفض إطار استقبال مطابق لإطار الإرسال TX في $label.")
                    lastTimeoutReason = "صدى إرسال مرفوض (TX Echo Rejection) في $label"
                    frameBadCs = true
                    break
                }

                // Handle trailing short protocol error frame if MCU aborts mid-read
                if (rxFrame.payload.size <= 2 && rxFrame.endByte == ETX && out.size() + rxFrame.payload.size < expectedLength) {
                    val errCode = rxFrame.sts1
                    return when (errCode) {
                        STS_PARAMETER_ERROR -> Read0x18Result.ParameterUnsupported
                        STS_COMMAND_ERROR -> Read0x18Result.CommandUnsupported(errCode, statusDescription(errCode))
                        else -> Read0x18Result.ProtocolError(errCode, statusDescription(errCode))
                    }
                }

                out.write(rxFrame.payload)
                lastEndByte = rxFrame.endByte
                onBytesReceived(minOf(out.size(), expectedLength))
                if (rxFrame.endByte == ETX && out.size() >= expectedLength) {
                    break
                }
            }

            if (!frameTimedOut && !frameBadCs && out.size() >= expectedLength) {
                val full = out.toByteArray()
                val sliced = if (full.size == expectedLength) full else full.copyOfRange(0, expectedLength)
                if (sliced.size >= readCmd.size && sliced.copyOfRange(0, readCmd.size).contentEquals(readCmd)) {
                    lastTimeoutReason = "تم رفض البيانات لأنها تطابق بايتات أمر الإرسال (Echo Rejection)"
                    continue
                }
                return Read0x18Result.Success(sliced)
            }
        }

        return Read0x18Result.TransportLost(
            "$lastTimeoutReason (بعد $MAX_TIMEOUT_RETRIES محاولات)",
            isUsbDetached = !transport.isDeviceAttached()
        )
    }

    /**
     * Sends an STX data frame ([0x02, LEN, ...DATA..., CS_Data, ETB/ETX]) via Paced 64B/15ms
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
                    "TX %dB (%s • STX=0x02 LEN=0x%02X • Paced 64B/15ms • END=%02X %s): [%s ... CS=%02X END=%02X] • مهلة الرد %dms",
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
     *         Pages 1..3 END=0x17 (ETB), Page 4 END=0x03 (ETX), Paced 64B/15ms -> await ACK (2500ms) after each page.
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
        var attempt = 1
        var needResync = false

        while (attempt <= MAX_STX_PAGE_RETRIES) {
            if (!transport.isDeviceAttached()) {
                return VerifyOutcome.TransportLost("انقطع اتصال USB OTG أثناء Verify 0x13", lastTimeoutPage, isUsbDetached = true)
            }
            if (needResync) {
                onLog(String.format(Locale.US, "إعادة المزامنة لـ 0x13 عند 0x%06X (محاولة %d/%d)...", startAddr, attempt, MAX_STX_PAGE_RETRIES))
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) {
                        return VerifyOutcome.TransportLost(resync.reason, lastTimeoutPage, isUsbDetached = true)
                    }
                    lastTimeoutReason = resync.reason
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    attempt++
                    continue
                }
                needResync = false
            }

            onLog("TX (0x13 Header 11B): ${toHex(verifyCmd)}")
            when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                is Nec78k0TransceiveResult.Success -> {
                    onLog("RX (0x13 Header ACK): ${toHex(cmdRes.frame.rawFrame)}")
                }
                is Nec78k0TransceiveResult.ProtocolError -> {
                    if (cmdRes.statusCode == STS_CHECKSUM_ERROR && attempt < MAX_STX_PAGE_RETRIES) {
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        needResync = (attempt >= 2)
                        attempt++
                        continue
                    }
                    return VerifyOutcome.Mismatch(cmdRes.statusLabel, cmdRes.statusCode, 0)
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (cmdRes.isUsbDetached) {
                        return VerifyOutcome.TransportLost(cmdRes.reason, 0, isUsbDetached = true)
                    }
                    lastTimeoutReason = cmdRes.reason
                    lastTimeoutPage = 0
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    needResync = true
                    attempt++
                    continue
                }
            }

            delay(Nec78k0ProtocolRegistry.COMMAND_TO_DATA_DELAY_MS)
            transport.flushInputBuffer()

            var restartHeader = false
            for (page in 0 until PAGES_PER_BLOCK) {
                val offset = page * PAGE_SIZE
                val slice = blockBytes.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == PAGES_PER_BLOCK - 1)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                var directStxRetried = false

                while (true) {
                    when (val ackRes = sendStxDataPageAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x13 صفحة ${page + 1}/$PAGES_PER_BLOCK", onLog)) {
                        is FrameAckOutcome.AckSuccess -> {
                            onPageProgress?.invoke((page + 1) * PAGE_SIZE)
                            if (!isLast) {
                                delay(Nec78k0ProtocolRegistry.FRAME_TO_FRAME_DELAY_MS)
                            }
                            break
                        }
                        is FrameAckOutcome.FrameError -> {
                            if (ackRes.errorCode == STS_CHECKSUM_ERROR) {
                                lastTimeoutReason = ackRes.description
                                lastTimeoutPage = page + 1
                                if (attempt >= MAX_STX_PAGE_RETRIES) {
                                    return VerifyOutcome.TransportLost("${ackRes.description} (بعد $MAX_STX_PAGE_RETRIES محاولات)", page + 1, isUsbDetached = false)
                                }
                                transport.flushInputBuffer()
                                delay(STX_RETRY_WAIT_MS)
                                attempt++
                                if (!directStxRetried && attempt == 2) {
                                    directStxRetried = true
                                    onLog("خطأ نقل 0x07 (Checksum Error) في 0x13 صفحة ${page + 1}/$PAGES_PER_BLOCK — إعادة إرسال STX (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                    continue
                                } else {
                                    restartHeader = true
                                    needResync = false
                                    break
                                }
                            }
                            return VerifyOutcome.Mismatch(ackRes.description, ackRes.errorCode, page + 1)
                        }
                        is FrameAckOutcome.UsbDetached -> {
                            return VerifyOutcome.TransportLost(ackRes.reason, page + 1, isUsbDetached = true)
                        }
                        is FrameAckOutcome.Timeout -> {
                            lastTimeoutReason = ackRes.reason
                            lastTimeoutPage = page + 1
                            if (attempt >= MAX_STX_PAGE_RETRIES) {
                                return VerifyOutcome.TransportLost("$lastTimeoutReason (بعد $MAX_STX_PAGE_RETRIES محاولات)", lastTimeoutPage, isUsbDetached = false)
                            }
                            transport.flushInputBuffer()
                            delay(STX_RETRY_WAIT_MS)
                            attempt++
                            if (!directStxRetried && attempt == 2) {
                                directStxRetried = true
                                continue
                            } else {
                                restartHeader = true
                                needResync = true
                                break
                            }
                        }
                    }
                }
                if (restartHeader) break
            }
            if (!restartHeader) {
                return VerifyOutcome.Match
            }
        }
        return VerifyOutcome.TransportLost(
            "$lastTimeoutReason (بعد $MAX_STX_PAGE_RETRIES محاولات)",
            lastTimeoutPage,
            isUsbDetached = false
        )
    }

    internal suspend fun verifyPageVia0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        pageBytes: ByteArray,
        clockFreqMhzText: String = "8 MHz",
        onLog: (String) -> Unit
    ): VerifyOutcome {
        require(pageBytes.size == PAGE_SIZE) { "78K0 verify page must be 256 bytes" }
        val verifyCmd = buildAddressRangeFrame(CMD_VERIFY, startAddr, endAddr)
        var lastTimeoutReason = "انتهت مهلة Verify 0x13 (صفحة)"
        var attempt = 1
        var needResync = false

        while (attempt <= MAX_STX_PAGE_RETRIES) {
            if (!transport.isDeviceAttached()) {
                return VerifyOutcome.TransportLost("انقطع اتصال USB OTG أثناء Verify 0x13", 1, isUsbDetached = true)
            }
            if (needResync) {
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) return VerifyOutcome.TransportLost(resync.reason, 1, isUsbDetached = true)
                    lastTimeoutReason = resync.reason
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    attempt++
                    continue
                }
                needResync = false
            }
            when (val cmdRes = transceiveSingleFrame(transport, verifyCmd, HEADER_ACK_TIMEOUT_MS, onLog, verboseLog = false)) {
                is Nec78k0TransceiveResult.Success -> Unit
                is Nec78k0TransceiveResult.ProtocolError -> {
                    if (cmdRes.statusCode == STS_CHECKSUM_ERROR && attempt < MAX_STX_PAGE_RETRIES) {
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        needResync = (attempt >= 2)
                        attempt++
                        continue
                    }
                    return VerifyOutcome.Mismatch(cmdRes.statusLabel, cmdRes.statusCode, 0)
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (cmdRes.isUsbDetached) return VerifyOutcome.TransportLost(cmdRes.reason, 0, isUsbDetached = true)
                    lastTimeoutReason = cmdRes.reason
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    needResync = true
                    attempt++
                    continue
                }
            }
            delay(Nec78k0ProtocolRegistry.COMMAND_TO_DATA_DELAY_MS)
            transport.flushInputBuffer()
            val dataFrame = buildDataFrame(pageBytes, isLast = true)
            var directStxRetried = false
            var restartHeader = false
            while (true) {
                when (val ackRes = sendStxDataPageAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x13 صفحة مفردة", onLog, verboseLog = false)) {
                    is FrameAckOutcome.AckSuccess -> return VerifyOutcome.Match
                    is FrameAckOutcome.FrameError -> {
                        if (ackRes.errorCode == STS_CHECKSUM_ERROR) {
                            lastTimeoutReason = ackRes.description
                            if (attempt >= MAX_STX_PAGE_RETRIES) {
                                return VerifyOutcome.TransportLost("${ackRes.description} (بعد $MAX_STX_PAGE_RETRIES محاولات)", 1, isUsbDetached = false)
                            }
                            transport.flushInputBuffer()
                            delay(STX_RETRY_WAIT_MS)
                            attempt++
                            if (!directStxRetried && attempt == 2) {
                                directStxRetried = true
                                continue
                            } else {
                                restartHeader = true
                                needResync = false
                                break
                            }
                        }
                        return VerifyOutcome.Mismatch(ackRes.description, ackRes.errorCode, 1)
                    }
                    is FrameAckOutcome.UsbDetached -> return VerifyOutcome.TransportLost(ackRes.reason, 1, isUsbDetached = true)
                    is FrameAckOutcome.Timeout -> {
                        lastTimeoutReason = ackRes.reason
                        if (attempt >= MAX_STX_PAGE_RETRIES) {
                            return VerifyOutcome.TransportLost("$lastTimeoutReason (بعد $MAX_STX_PAGE_RETRIES محاولات)", 1, isUsbDetached = false)
                        }
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        attempt++
                        if (!directStxRetried && attempt == 2) {
                            directStxRetried = true
                            continue
                        } else {
                            restartHeader = true
                            needResync = true
                            break
                        }
                    }
                }
            }
            if (!restartHeader) break
        }
        return VerifyOutcome.TransportLost(lastTimeoutReason, 1, isUsbDetached = false)
    }

    /**
     * Anti-Dummy Rule 3: Two-vector 0x13 Verify validation.
     * Unmodified bytes MUST return ACK (0x06), and a 1-byte mutated copy MUST return Mismatch (0x0F/0x1C).
     */
    internal suspend fun verifyTwoVector0x13Block(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        expectedBlock: ByteArray,
        clockFreqMhzText: String,
        onLog: (String) -> Unit
    ): Boolean {
        val unmodifiedOutcome = verifyBlockVia0x13(
            transport = transport,
            startAddr = startAddr,
            endAddr = endAddr,
            blockBytes = expectedBlock,
            clockFreqMhzText = clockFreqMhzText,
            onLog = onLog
        )
        if (unmodifiedOutcome !is VerifyOutcome.Match) {
            onLog("فشل التحقق ثنائي المتجهات (Vector 1: البيانات الأصلية لم ترجع ACK 0x06).")
            return false
        }
        val mutatedBlock = expectedBlock.copyOf()
        mutatedBlock[0] = (mutatedBlock[0].toInt() xor 0x01).toByte()
        val mutatedOutcome = verifyBlockVia0x13(
            transport = transport,
            startAddr = startAddr,
            endAddr = endAddr,
            blockBytes = mutatedBlock,
            clockFreqMhzText = clockFreqMhzText,
            onLog = onLog
        )
        return when (mutatedOutcome) {
            is VerifyOutcome.Mismatch -> {
                onLog("نجح التحقق ثنائي المتجهات (0x13 Two-Vector): الأصل=0x06 ACK • المعدّل 1B=${mutatedOutcome.detail}")
                true
            }
            is VerifyOutcome.Match -> {
                onLog("تحذير أمني: المعالج أرجع ACK لبايت معدل — فشل اختبار Two-Vector 0x13!")
                false
            }
            is VerifyOutcome.TransportLost -> {
                onLog("انقطاع/مهلة أثناء المتجه الثاني 0x13: ${mutatedOutcome.reason}")
                false
            }
        }
    }

    private suspend fun tryExtractUniformBlockVia0x32And0x13(
        transport: Serial78k0Transport,
        startAddr: Int,
        endAddr: Int,
        clockFreqMhzText: String,
        requireTwoVectorVerify: Boolean,
        onLog: (String) -> Unit
    ): NonStandardBlockOutcome {
        val csRes = queryChecksum16WithRetry(
            transport = transport,
            startAddr = startAddr,
            endAddr = endAddr,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = String.format(Locale.US, "0xB0 مسار بديل (0x%06X)", startAddr),
            maxRetries = 2,
            verboseLog = false,
            onLog = onLog
        )
        val hwCs = when (csRes) {
            is ChecksumQueryResult.Success -> csRes.checksum16 and 0xFFFF
            is ChecksumQueryResult.TransportLost -> return NonStandardBlockOutcome.TransportLost(csRes.reason, csRes.isUsbDetached)
            is ChecksumQueryResult.ProtocolError -> return NonStandardBlockOutcome.LockedNonUniform("فشل 0xB0 عند 0x%06X: ${csRes.statusLabel}".format(Locale.US, startAddr))
            is ChecksumQueryResult.ParameterError -> return NonStandardBlockOutcome.LockedNonUniform("خطأ معاملات 0xB0 عند 0x%06X".format(Locale.US, startAddr))
        }

        // Fast-Fill Rule for Uniform Blocks (< 1s without byte-by-byte zeroing):
        // CS_orig == 0x0004 -> fill 0x00 | CS_orig == 0xFC04 -> fill 0xFF
        Nec78k0Block0Reconstructor.fastFillByteForKnownChecksum(hwCs)?.let { fastFillByte ->
            onLog(
                String.format(
                    Locale.US,
                    "⚡ تعبئة سريعة فورية للكتلة 0x%06X..0x%06X بالقيمة 0x%02X (< 1s • 0xB0=%04X).",
                    startAddr, endAddr, fastFillByte.toInt() and 0xFF, hwCs
                )
            )
            return NonStandardBlockOutcome.VerifiedBytes(
                bytes = ByteArray(BLOCK_SIZE) { fastFillByte },
                twoVectorConfirmed = false
            )
        }

        val blankRes = transceiveWithTimeoutRetry(
            transport = transport,
            txFrame = buildAddressRangeFrame(CMD_BLANK_CHECK, startAddr, endAddr),
            timeoutMs = HEADER_ACK_TIMEOUT_MS,
            clockFreqMhzText = clockFreqMhzText,
            opLabel = String.format(Locale.US, "فحص الفراغ 0x32 (0x%06X)", startAddr),
            onLog = onLog
        )
        val isBlankAck = blankRes is Nec78k0TransceiveResult.Success
        val allFf = ByteArray(BLOCK_SIZE) { 0xFF.toByte() }
        val allZero = ByteArray(BLOCK_SIZE) { 0x00 }

        val candidate = when {
            isBlankAck && Nec78k0Block0Reconstructor.matches78k0Checksum16(allFf, hwCs) -> allFf
            Nec78k0Block0Reconstructor.matches78k0Checksum16(allZero, hwCs) -> allZero
            else -> null
        }

        if (candidate != null) {
            val verified = if (requireTwoVectorVerify) {
                verifyTwoVector0x13Block(transport, startAddr, endAddr, candidate, clockFreqMhzText, onLog)
            } else {
                verifyBlockVia0x13(transport, startAddr, endAddr, candidate, clockFreqMhzText, onLog = onLog) is VerifyOutcome.Match
            }
            if (verified) {
                return NonStandardBlockOutcome.VerifiedBytes(candidate, twoVectorConfirmed = requireTwoVectorVerify)
            }
        }

        return NonStandardBlockOutcome.LockedNonUniform(
            String.format(
                Locale.US,
                "الشريحة محمية ومقفولة عن القراءة المباشرة (أمر 0x18 مرفوض والكتلة 0x%06X..0x%06X مبرمجة ببصمة 0xB0=%04X). تم الحفاظ على Block 0 واستعادته بأمان.",
                startAddr, endAddr, hwCs
            )
        )
    }

    private suspend fun extractAndRestoreBlockViaDifferential0x40And0xB0(
        context: Context,
        transport: Serial78k0Transport,
        sessionRef: Nec78k0RecoveryManager.Nec78k0RecoverySession?,
        detectedInfo: Nec78k0DetectedInfo,
        fullBuffer: ByteArray,
        blockIdx: Int,
        blockCount: Int,
        blockStart: Int,
        blockEnd: Int,
        totalSize: Int,
        resumeByteIdx: Int = 0,
        cachedOriginalCs: Int = -1,
        autoRestoreChip: Boolean = true,
        clockFreqMhzText: String,
        onSessionUpdated: (Nec78k0RecoveryManager.Nec78k0RecoverySession) -> Unit,
        onProgress: (Int, Int, String) -> Unit,
        onLog: (String) -> Unit
    ): NonStandardBlockOutcome {
        val safeResumeIdx = resumeByteIdx.coerceIn(0, BLOCK_SIZE)
        var initialBlockCs = cachedOriginalCs
        var csPrev: Int

        if (safeResumeIdx == 0) {
            val initialCsRes = queryChecksum16WithRetry(
                transport = transport,
                startAddr = blockStart,
                endAddr = blockEnd,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = String.format(Locale.US, "قراءة CS_prev للكتلة Block %d (0xB0)", blockIdx),
                maxRetries = 2,
                verboseLog = true,
                onLog = onLog
            )
            initialBlockCs = when (initialCsRes) {
                is ChecksumQueryResult.Success -> initialCsRes.checksum16 and 0xFFFF
                is ChecksumQueryResult.TransportLost -> return NonStandardBlockOutcome.TransportLost(initialCsRes.reason, initialCsRes.isUsbDetached)
                is ChecksumQueryResult.ProtocolError -> return NonStandardBlockOutcome.LockedNonUniform("فشل 0xB0 لـ Block $blockIdx: ${initialCsRes.statusLabel}")
                is ChecksumQueryResult.ParameterError -> return NonStandardBlockOutcome.LockedNonUniform("خطأ معاملات (0x05) في 0xB0 لـ Block $blockIdx")
            }

            // Fast-Fill Rule (< 1s): 0x0004 -> 0x00 | 0xFC04 -> 0xFF
            Nec78k0Block0Reconstructor.fastFillByteForKnownChecksum(initialBlockCs)?.let { fillByte ->
                val filled = ByteArray(BLOCK_SIZE) { fillByte }
                val done = minOf(blockStart + BLOCK_SIZE, totalSize)
                onProgress(done, totalSize, "تعبئة سريعة Block $blockIdx/${blockCount - 1} (0x%02X)".format(fillByte.toInt() and 0xFF))
                onLog(
                    String.format(
                        Locale.US,
                        "⚡ تعبئة سريعة فورية للكتلة Block %d/%d بالقيمة 0x%02X (< 1s • CS_orig=%04X).",
                        blockIdx, blockCount - 1, fillByte.toInt() and 0xFF, initialBlockCs
                    )
                )
                return NonStandardBlockOutcome.VerifiedBytes(filled, twoVectorConfirmed = false, alreadyVerified = true)
            }
            csPrev = initialBlockCs
        } else if (safeResumeIdx < BLOCK_SIZE) {
            onLog(
                String.format(
                    Locale.US,
                    "استئناف استخراج Block %d تفاضلياً من البايت %d/1023 مع فحص CS_prev الحي عبر 0xB0...",
                    blockIdx, safeResumeIdx
                )
            )
            val liveCsRes = queryChecksum16WithRetry(
                transport = transport,
                startAddr = blockStart,
                endAddr = blockEnd,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = String.format(Locale.US, "قراءة CS_prev الحي لـ Block %d عند %d (0xB0)", blockIdx, safeResumeIdx),
                maxRetries = 2,
                verboseLog = true,
                onLog = onLog
            )
            csPrev = when (liveCsRes) {
                is ChecksumQueryResult.Success -> liveCsRes.checksum16 and 0xFFFF
                is ChecksumQueryResult.TransportLost -> return NonStandardBlockOutcome.TransportLost(
                    liveCsRes.reason, liveCsRes.isUsbDetached, pausedByteIdx = safeResumeIdx, pausedOrigCs = initialBlockCs
                )
                is ChecksumQueryResult.ProtocolError -> return NonStandardBlockOutcome.LockedNonUniform("فشل 0xB0 لـ Block $blockIdx: ${liveCsRes.statusLabel}")
                is ChecksumQueryResult.ParameterError -> return NonStandardBlockOutcome.LockedNonUniform("خطأ معاملات (0x05) في 0xB0 لـ Block $blockIdx")
            }
        } else {
            // Disconnected after extracting all 1024B of Block K, right before atomic restore completed
            csPrev = 0x0004
        }

        val blockBytes = fullBuffer.copyOfRange(blockStart, blockStart + BLOCK_SIZE)
        var writeMode: ZeroWriteGranularity? = null
        val pageBuffer = ByteArray(PAGE_SIZE) { 0xFF.toByte() }
        val resumePageOff = safeResumeIdx % PAGE_SIZE
        for (p in 0 until resumePageOff) {
            pageBuffer[p] = 0x00
        }

        var workingSession = sessionRef
        for (byteIdx in safeResumeIdx until BLOCK_SIZE) {
            currentCoroutineContext().ensureActive()
            ProgrammerForegroundService.awaitIfPaused()
            if (!transport.isDeviceAttached()) {
                System.arraycopy(blockBytes, 0, fullBuffer, blockStart, BLOCK_SIZE)
                return NonStandardBlockOutcome.TransportLost(
                    "انقطع اتصال USB OTG أثناء استخراج Block $blockIdx تفاضلياً عند البايت $byteIdx/1023",
                    isUsbDetached = true,
                    pausedByteIdx = byteIdx,
                    pausedOrigCs = initialBlockCs
                )
            }

            val pageOffset = byteIdx % PAGE_SIZE
            if (pageOffset == 0) {
                java.util.Arrays.fill(pageBuffer, 0xFF.toByte())
            }
            pageBuffer[pageOffset] = 0x00

            val (modeUsed, zeroRes) = writeSingleByteZeroWithAutoGranularity(
                transport = transport,
                byteIdx = byteIdx,
                currentMode = writeMode,
                clockFreqMhzText = clockFreqMhzText,
                onLog = onLog,
                blockStart = blockStart,
                cumulativePageBuffer = pageBuffer
            )
            writeMode = modeUsed
            when (zeroRes) {
                is DiffWriteOutcome.ProceedToChecksum -> Unit
                is DiffWriteOutcome.Protected -> return NonStandardBlockOutcome.LockedNonUniform(zeroRes.reason)
                is DiffWriteOutcome.ProtocolError -> return NonStandardBlockOutcome.LockedNonUniform(zeroRes.reason)
                is DiffWriteOutcome.TransportLost -> {
                    System.arraycopy(blockBytes, 0, fullBuffer, blockStart, BLOCK_SIZE)
                    return NonStandardBlockOutcome.TransportLost(
                        zeroRes.reason, zeroRes.isUsbDetached, pausedByteIdx = byteIdx, pausedOrigCs = initialBlockCs
                    )
                }
            }

            val currCsRes = queryChecksum16WithRetry(
                transport = transport,
                startAddr = blockStart,
                endAddr = blockEnd,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "0xB0 تفاضلي Block $blockIdx [$byteIdx]",
                maxRetries = 2,
                verboseLog = false,
                onLog = onLog
            )
            val csCurr = when (currCsRes) {
                is ChecksumQueryResult.Success -> currCsRes.checksum16 and 0xFFFF
                is ChecksumQueryResult.TransportLost -> {
                    System.arraycopy(blockBytes, 0, fullBuffer, blockStart, BLOCK_SIZE)
                    return NonStandardBlockOutcome.TransportLost(
                        currCsRes.reason, currCsRes.isUsbDetached, pausedByteIdx = byteIdx, pausedOrigCs = initialBlockCs
                    )
                }
                is ChecksumQueryResult.ProtocolError -> return NonStandardBlockOutcome.LockedNonUniform("فشل 0xB0 عند Block $blockIdx[$byteIdx]: ${currCsRes.statusLabel}")
                is ChecksumQueryResult.ParameterError -> return NonStandardBlockOutcome.LockedNonUniform("خطأ معاملات 0xB0 عند Block $blockIdx[$byteIdx]")
            }

            val extractedByte = Nec78k0Block0Reconstructor.extractDifferentialByteFromChecksumDelta(
                csPrev = csPrev,
                csCurr = csCurr,
                offset = byteIdx
            )
            blockBytes[byteIdx] = extractedByte.toByte()
            fullBuffer[blockStart + byteIdx] = extractedByte.toByte()
            csPrev = csCurr

            if (byteIdx % 16 == 15 || byteIdx == BLOCK_SIZE - 1) {
                Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
                val updated = (workingSession ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
                    chipName = detectedInfo.deviceName,
                    subFamily = detectedInfo.familyGroup,
                    lastAddress = detectedInfo.lastAddress,
                    blockCount = blockCount,
                    hasDataFlash = detectedInfo.hasDataFlash,
                    fullFlashChecksum = detectedInfo.fullFlashChecksum16,
                    block0Checksum = detectedInfo.block0Checksum16,
                    signatureHex = detectedInfo.rawSignatureHex,
                    stage = Nec78k0RecoveryManager.RecoveryStage.READING_BLOCKS,
                    lastCompletedBlock = blockIdx - 1,
                    updatedAtMs = System.currentTimeMillis(),
                    partialBlocksPath = Nec78k0RecoveryManager.partialBlocksFile(context, detectedInfo.deviceName).absolutePath,
                    block0Touched = true,
                    block0OriginalExtracted = true
                )).copy(
                    updatedAtMs = System.currentTimeMillis(),
                    activeDiffBlockIndex = blockIdx,
                    activeDiffByteIndex = byteIdx + 1,
                    activeDiffBlockOrigCs = initialBlockCs
                )
                workingSession = updated
                onSessionUpdated(updated)
                Nec78k0RecoveryManager.saveSessionCheckpoint(context, updated)
            }

            if (byteIdx % 16 == 0 || byteIdx == BLOCK_SIZE - 1) {
                val done = minOf(blockStart + byteIdx + 1, totalSize)
                val label = String.format(Locale.US, "استخراج تفاضلي Block %d/%d: %d/1024", blockIdx, blockCount - 1, byteIdx + 1)
                onProgress(done, totalSize, label)
                ProgrammerForegroundService.renewSessionLock(context, label)
            }
        }

        val verifiedByteCs = Nec78k0Block0Reconstructor.compute78k0Checksum16(blockBytes) and 0xFFFF
        val verifiedWordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(blockBytes) and 0xFFFF
        val verifiedDirectCs = Nec78k0Block0Reconstructor.compute78k0Word16DirectChecksum(blockBytes) and 0xFFFF
        var csWarning = if (initialBlockCs >= 0) {
            !Nec78k0Block0Reconstructor.matches78k0Checksum16(blockBytes, initialBlockCs)
        } else false

        if (csWarning) {
            onLog(
                String.format(
                    Locale.US,
                    "[WARN] اكتمل استخراج البلوك %d (1024/1024B) — Byte=%04X / Word16=%04X / Direct=%04X != الأصلي=%04X",
                    blockIdx, verifiedByteCs, verifiedWordCs, verifiedDirectCs, initialBlockCs
                )
            )
        }

        var restoredAndVerified = false
        if (autoRestoreChip) {
            onProgress(
                minOf(blockStart + BLOCK_SIZE, totalSize),
                totalSize,
                "استعادة وتوثيق Block $blockIdx/${blockCount - 1} (0x22 + 0x40 + 0x13 + 0xB0)..."
            )
            when (val restoreRes = restoreAndVerifyBlockAtomically(
                transport = transport,
                blockIdx = blockIdx,
                blockStart = blockStart,
                blockEnd = blockEnd,
                blockData = blockBytes,
                expectedCs16 = initialBlockCs.takeIf { it >= 0 },
                clockFreqMhzText = clockFreqMhzText,
                onLog = onLog
            )) {
                is RestoreBlockOutcome.Verified -> {
                    restoredAndVerified = true
                    if (restoreRes.warningNote != null) {
                        csWarning = true
                    }
                }
                is RestoreBlockOutcome.Protected -> return NonStandardBlockOutcome.LockedNonUniform(restoreRes.reason)
                is RestoreBlockOutcome.TransportLost -> {
                    return NonStandardBlockOutcome.TransportLost(
                        restoreRes.reason, restoreRes.isUsbDetached, pausedByteIdx = BLOCK_SIZE, pausedOrigCs = initialBlockCs
                    )
                }
                is RestoreBlockOutcome.Failed -> {
                    csWarning = true
                    onLog("[WARN] ${restoreRes.reason}")
                }
            }
        }

        return NonStandardBlockOutcome.VerifiedBytes(
            bytes = blockBytes,
            twoVectorConfirmed = false,
            checksumWarning = csWarning,
            alreadyVerified = restoredAndVerified
        )
    }

    /**
     * Atomic PC-grade per-block restore:
     * 1) CMD_BLOCK_ERASE (0x22) on [startAddr..endAddr] ONLY
     * 2) CMD_PROGRAM (0x40) of 4 x 256B STX pages from [blockData]
     * 3) CMD_VERIFY (0x13) + CMD_CHECKSUM (0xB0) verification before advancing to next block.
     */
    private suspend fun restoreAndVerifyBlockAtomically(
        transport: Serial78k0Transport,
        blockIdx: Int,
        blockStart: Int,
        blockEnd: Int,
        blockData: ByteArray,
        expectedCs16: Int?,
        clockFreqMhzText: String,
        onPageProgress: ((bytesInBlock: Int) -> Unit)? = null,
        onLog: (String) -> Unit
    ): RestoreBlockOutcome {
        for (restoreAttempt in 0..1) {
            if (restoreAttempt > 0) {
                onLog("إعادة محاولة استعادة وتوثيق Block $blockIdx (0x22 + 0x40 + 0x13)...")
            }
            val eraseRes = transceiveWithTimeoutRetry(
                transport = transport,
                txFrame = buildAddressRangeFrame(CMD_BLOCK_ERASE, blockStart, blockEnd),
                timeoutMs = HEADER_ACK_TIMEOUT_MS,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "مسح Block $blockIdx قبل الاستعادة (0x22)",
                onLog = onLog
            )
            if (eraseRes is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                return RestoreBlockOutcome.TransportLost(eraseRes.reason, eraseRes.isUsbDetached)
            } else if (eraseRes is Nec78k0TransceiveResult.ProtocolError) {
                return if (eraseRes.statusCode == STS_PROTECT_ERROR) {
                    RestoreBlockOutcome.Protected("الشريحة محمية (ERR_PROTECT 0x10) أثناء مسح Block $blockIdx للاستعادة.")
                } else {
                    RestoreBlockOutcome.Failed("فشل مسح Block $blockIdx للاستعادة: ${eraseRes.statusLabel}")
                }
            }
            delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)

            when (val progRes = programBlockFromBuffer(
                transport = transport,
                startAddr = blockStart,
                endAddr = blockEnd,
                blockData = blockData,
                clockFreqMhzText = clockFreqMhzText,
                eraseBlockBeforeRetry = true,
                onPageProgress = onPageProgress,
                onLog = onLog
            )) {
                is ProgramOutcome.Success -> Unit
                is ProgramOutcome.ProtocolError -> {
                    return if (progRes.isProtected) {
                        RestoreBlockOutcome.Protected(progRes.message)
                    } else {
                        RestoreBlockOutcome.Failed(progRes.message)
                    }
                }
                is ProgramOutcome.TransportFailure -> {
                    return RestoreBlockOutcome.TransportLost(progRes.reason, progRes.isUsbDetached)
                }
            }

            delay(20)
            when (val vRes = verifyBlockVia0x13(
                transport = transport,
                startAddr = blockStart,
                endAddr = blockEnd,
                blockBytes = blockData,
                clockFreqMhzText = clockFreqMhzText,
                onLog = onLog
            )) {
                is VerifyOutcome.Match -> {
                    val csCheck = queryChecksum16WithRetry(
                        transport = transport,
                        startAddr = blockStart,
                        endAddr = blockEnd,
                        clockFreqMhzText = clockFreqMhzText,
                        opLabel = "تحقق 0xB0 بعد استعادة Block $blockIdx",
                        maxRetries = 2,
                        verboseLog = false,
                        onLog = onLog
                    )
                    return when (csCheck) {
                        is ChecksumQueryResult.Success -> {
                            val hwCs = csCheck.checksum16 and 0xFFFF
                            val matchesLocal = Nec78k0Block0Reconstructor.matches78k0Checksum16(blockData, hwCs)
                            val matchesOrig = expectedCs16 == null ||
                                hwCs == (expectedCs16 and 0xFFFF) ||
                                Nec78k0Block0Reconstructor.swap16(hwCs) == (expectedCs16 and 0xFFFF)
                            if (matchesLocal && matchesOrig) {
                                onLog(
                                    String.format(
                                        Locale.US,
                                        "تمت استعادة وتوثيق Block %d (0x22 Erase + 0x40 Write + 0x13 Verify + 0xB0=%04X) بنسبة 100%%.",
                                        blockIdx, hwCs
                                    )
                                )
                                RestoreBlockOutcome.Verified()
                            } else {
                                val note = String.format(
                                    Locale.US,
                                    "Block %d اجتاز 0x13 لكن Checksum 0xB0=%04X يختلف عن المتوقع (%s)",
                                    blockIdx, hwCs, expectedCs16?.let { "%04X".format(it and 0xFFFF) } ?: "--"
                                )
                                onLog("[WARN] $note")
                                RestoreBlockOutcome.Verified(warningNote = note)
                            }
                        }
                        is ChecksumQueryResult.TransportLost -> RestoreBlockOutcome.TransportLost(csCheck.reason, csCheck.isUsbDetached)
                        else -> RestoreBlockOutcome.Verified(warningNote = "تعذر قراءة 0xB0 بعد 0x13 للكتلة $blockIdx")
                    }
                }
                is VerifyOutcome.Mismatch -> {
                    if (restoreAttempt == 0) {
                        onLog("[WARN] فشل تحقق 0x13 بعد استعادة Block $blockIdx (${vRes.detail}) — إعادة المحاولة...")
                        continue
                    }
                    return RestoreBlockOutcome.Failed("فشل مطابقة 0x13 بعد استعادة Block $blockIdx: ${vRes.detail}")
                }
                is VerifyOutcome.TransportLost -> {
                    return RestoreBlockOutcome.TransportLost(vRes.reason, vRes.isUsbDetached)
                }
            }
        }
        return RestoreBlockOutcome.Failed("فشل التحقق من استعادة Block $blockIdx")
    }

    private suspend fun restoreOriginalBlock0Safely(
        transport: Serial78k0Transport,
        block0Bytes: ByteArray,
        clockFreqMhzText: String,
        onLog: (String) -> Unit
    ) {
        if (!transport.isDeviceAttached()) return
        val eraseCmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, 0x000000, BLOCK_SIZE - 1)
        val eraseRes = transceiveWithTimeoutRetry(transport, eraseCmd, HEADER_ACK_TIMEOUT_MS, clockFreqMhzText, "استعادة Block 0 (0x22)", onLog)
        if (eraseRes is Nec78k0TransceiveResult.Success) {
            delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)
            programBlockFromBuffer(
                transport = transport,
                startAddr = 0x000000,
                endAddr = BLOCK_SIZE - 1,
                blockData = block0Bytes,
                clockFreqMhzText = clockFreqMhzText,
                eraseBlockBeforeRetry = true,
                onLog = onLog
            )
        }
    }

    /**
     * Standard NEC 78K0 Two-Stage 0x40 Program (1024B Block = 4 x 260B STX Frames):
     * Step 1: Send 11-byte 0x40 Header starting with SOH (0x01) -> await [02 01 06 F9 03].
     * Step 2: Send 4 Data Frames of 260B starting with STX (0x02), LEN=0x00:
     *         Pages 1..3 END=0x17 (ETB), Page 4 END=0x03 (ETX), Paced 64B/15ms -> await ACK (2500ms) after each page.
     *         Auto-retries up to 5 times on 0x07 (Checksum Error) or Timeout (flush RX + 50ms + resend 260B STX,
     *         and re-sends 0x40 block header on attempts 3..5), followed by a 30ms post-block settle delay after ETX.
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
        var lastOutcome: ProgramOutcome = ProgramOutcome.TransportFailure("انتهت مهلة حزمة 0x40", isUsbDetached = false)
        var attempt = 1
        var needResync = false
        var anyPageAckedInAttempt = false

        while (attempt <= MAX_STX_PAGE_RETRIES) {
            if (!transport.isDeviceAttached()) {
                return ProgramOutcome.TransportFailure("انقطع كابل USB OTG قبل إرسال 0x40", isUsbDetached = true)
            }
            if (needResync) {
                onLog(String.format(Locale.US, "إعادة المزامنة لـ 0x40 عند 0x%06X (محاولة %d/%d)...", startAddr, attempt, MAX_STX_PAGE_RETRIES))
                val resync = resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                if (resync is Nec78k0TransceiveResult.DisconnectedOrTimeout) {
                    if (resync.isUsbDetached) {
                        return ProgramOutcome.TransportFailure(resync.reason, isUsbDetached = true)
                    }
                    lastOutcome = ProgramOutcome.TransportFailure(resync.reason, isUsbDetached = false)
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    attempt++
                    continue
                } else if (resync is Nec78k0TransceiveResult.ProtocolError) {
                    return ProgramOutcome.ProtocolError("رفض المعالج إعادة المزامنة قبل 0x40: ${resync.statusLabel}")
                }
                needResync = false
            }

            if (anyPageAckedInAttempt && eraseBlockBeforeRetry) {
                val eraseCmd = buildAddressRangeFrame(CMD_BLOCK_ERASE, startAddr, endAddr)
                when (val eraseRes = transceiveSingleFrame(transport, eraseCmd, HEADER_ACK_TIMEOUT_MS, onLog)) {
                    is Nec78k0TransceiveResult.Success -> delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)
                    is Nec78k0TransceiveResult.ProtocolError -> {
                        if (eraseRes.statusCode == STS_PROTECT_ERROR) {
                            return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): أمر البرمجة مرفوض.", isProtected = true)
                        }
                    }
                    is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                        if (eraseRes.isUsbDetached) {
                            return ProgramOutcome.TransportFailure(eraseRes.reason, isUsbDetached = true)
                        }
                        lastOutcome = ProgramOutcome.TransportFailure(eraseRes.reason, isUsbDetached = false)
                        if (attempt >= MAX_STX_PAGE_RETRIES) break
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        needResync = true
                        attempt++
                        continue
                    }
                }
                anyPageAckedInAttempt = false
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
                    if (res.statusCode == STS_CHECKSUM_ERROR && attempt < MAX_STX_PAGE_RETRIES) {
                        lastOutcome = ProgramOutcome.ProtocolError("رفض المعالج أمر الكتابة 0x40: ${res.statusLabel}")
                        transport.flushInputBuffer()
                        delay(STX_RETRY_WAIT_MS)
                        needResync = (attempt >= 2)
                        attempt++
                        continue
                    }
                    return ProgramOutcome.ProtocolError("رفض المعالج أمر الكتابة 0x40: ${res.statusLabel}")
                }
                is Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (res.isUsbDetached) {
                        return ProgramOutcome.TransportFailure(res.reason, isUsbDetached = true)
                    }
                    lastOutcome = ProgramOutcome.TransportFailure(res.reason, isUsbDetached = false)
                    if (attempt >= MAX_STX_PAGE_RETRIES) break
                    transport.flushInputBuffer()
                    delay(STX_RETRY_WAIT_MS)
                    needResync = true
                    attempt++
                    continue
                }
            }

            // Allow 78K0 bootloader to switch cleanly from SOH command mode to STX data frame reception mode (commandToDataDelayMs = 23ms)
            delay(Nec78k0ProtocolRegistry.COMMAND_TO_DATA_DELAY_MS)
            transport.flushInputBuffer()

            var restartBlockHeader = false
            for (page in 0 until PAGES_PER_BLOCK) {
                val offset = page * PAGE_SIZE
                val slice = blockData.copyOfRange(offset, offset + PAGE_SIZE)
                val isLast = (page == PAGES_PER_BLOCK - 1)
                val dataFrame = buildDataFrame(slice, isLast = isLast)
                var directPageRetried = false

                while (true) {
                    when (val ackRes = sendStxDataPageAndAwaitAck(transport, dataFrame, PAGE_ACK_TIMEOUT_MS, "0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK", onLog)) {
                        is FrameAckOutcome.AckSuccess -> {
                            anyPageAckedInAttempt = true
                            onPageProgress?.invoke((page + 1) * PAGE_SIZE)
                            if (!isLast) {
                                delay(Nec78k0ProtocolRegistry.FRAME_TO_FRAME_DELAY_MS)
                            }
                            break
                        }
                        is FrameAckOutcome.UsbDetached -> {
                            return ProgramOutcome.TransportFailure(ackRes.reason, isUsbDetached = true)
                        }
                        is FrameAckOutcome.Timeout -> {
                            lastOutcome = ProgramOutcome.TransportFailure(
                                "${ackRes.reason} (بعد $MAX_STX_PAGE_RETRIES محاولات)",
                                isUsbDetached = false
                            )
                            if (attempt >= MAX_STX_PAGE_RETRIES) {
                                return lastOutcome
                            }
                            transport.flushInputBuffer()
                            delay(STX_RETRY_WAIT_MS)
                            attempt++
                            if (!directPageRetried && attempt == 2) {
                                directPageRetried = true
                                onLog("انتهت المهلة في 0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK — تفريغ RX والانتظار 50ms ثم إعادة إرسال 260B STX (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                continue
                            } else {
                                onLog("استمرت المهلة في 0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK — إعادة المزامنة وترويسة 0x40 للبلوك (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                restartBlockHeader = true
                                needResync = true
                                break
                            }
                        }
                        is FrameAckOutcome.FrameError -> {
                            if (ackRes.errorCode == STS_PROTECT_ERROR) {
                                return ProgramOutcome.ProtocolError("الشريحة محمية (ERR_PROTECT 0x10): تم إيقاف الكتابة عند البلوك.", isProtected = true)
                            }
                            if (ackRes.errorCode == STS_CHECKSUM_ERROR) {
                                lastOutcome = ProgramOutcome.ProtocolError(
                                    "فشل حرق البلوك (0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK) بعد $MAX_STX_PAGE_RETRIES محاولات: ${ackRes.description}"
                                )
                                if (attempt >= MAX_STX_PAGE_RETRIES) {
                                    return lastOutcome
                                }
                                transport.flushInputBuffer()
                                delay(STX_RETRY_WAIT_MS)
                                attempt++
                                if (!directPageRetried && attempt == 2) {
                                    directPageRetried = true
                                    onLog("خطأ نقل 0x07 (Checksum Error) في 0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK — تفريغ RX والانتظار 50ms ثم إعادة إرسال 260B STX (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                    continue
                                } else {
                                    onLog("استمر خطأ 0x07 في 0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK — إعادة إرسال ترويسة 0x40 للبلوك ثم الصفحات (محاولة $attempt/$MAX_STX_PAGE_RETRIES)...")
                                    restartBlockHeader = true
                                    needResync = false
                                    break
                                }
                            }
                            return ProgramOutcome.ProtocolError("فشل حرق البلوك (0x40 صفحة ${page + 1}/$PAGES_PER_BLOCK): ${ackRes.description}")
                        }
                    }
                }
                if (restartBlockHeader) break
            }
            if (!restartBlockHeader) {
                // 30ms inter-block settle delay after END=03 ETX before sending next block's 0x40 header
                delay(POST_BLOCK_PROGRAM_SETTLE_MS)
                return ProgramOutcome.Success
            }
        }
        return lastOutcome
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
            // Support direct 6-byte [02 02 CS_Low CS_High SUM 03] response without leading ACK frame
            if (firstFrame.payload.size == 2 && firstFrame.endByte == ETX) {
                val hardErrSet = setOf(STS_COMMAND_ERROR, STS_PARAMETER_ERROR, STS_CHECKSUM_ERROR, STS_PROTECT_ERROR, 0x15, STS_BLANK_CHECK_ERROR)
                if (firstFrame.sts1 == STS_ACK && firstFrame.sts2 != null && firstFrame.sts2 in hardErrSet) {
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
                return ChecksumQueryResult.Success(Nec78k0Block0Reconstructor.decodeChecksumBytes(firstFrame.payload))
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
            if (firstFrame.payload.size == 3 && firstFrame.endByte == ETX) {
                return ChecksumQueryResult.Success(
                    Nec78k0Block0Reconstructor.decodeChecksumBytes(firstFrame.payload.copyOfRange(1, 3))
                )
            }
            if (firstFrame.payload.size != 1 || firstFrame.endByte != ETX) {
                onLog("Checksum 0xB0 Header Frame Invalid: LEN=${firstFrame.payload.size}, END=0x%02X [${toHex(firstFrame.rawFrame)}]".format(firstFrame.endByte))
                return ChecksumQueryResult.ProtocolError(STS_CHECKSUM_ERROR, "إطار استجابة 0xB0 غير صالح")
            }
            val dataFrame = readOneRxFrame(transport, 1500) { stray ->
                if (verboseLog) onLog("RX Raw Live (0xB0 Data): $stray")
            } ?: return ChecksumQueryResult.TransportLost(
                "وصل ACK لأمر 0xB0 لكن إطار بيانات البصمة لم يصل.",
                isUsbDetached = !transport.isDeviceAttached()
            )
            if (!dataFrame.checksumValid) {
                onLog("Checksum 0xB0 Data Frame CS Invalid: [${toHex(dataFrame.rawFrame)}]")
                return ChecksumQueryResult.ProtocolError(STS_CHECKSUM_ERROR, "خطأ Checksum في إطار بيانات 0xB0")
            }
            if (dataFrame.payload.size == 1 && dataFrame.sts1 != STS_ACK) {
                val desc = statusDescription(dataFrame.sts1)
                if (verboseLog || dataFrame.sts1 != STS_PARAMETER_ERROR) {
                    onLog("Checksum 0xB0 Status Error: $desc [${toHex(dataFrame.rawFrame)}]")
                }
                return if (dataFrame.sts1 == STS_PARAMETER_ERROR) {
                    ChecksumQueryResult.ParameterError
                } else {
                    ChecksumQueryResult.ProtocolError(dataFrame.sts1, desc)
                }
            }
            if (dataFrame.payload.size != 2 || dataFrame.endByte != ETX) {
                onLog("Checksum 0xB0 Data Frame Invalid: LEN=${dataFrame.payload.size}, END=0x%02X [${toHex(dataFrame.rawFrame)}]".format(dataFrame.endByte))
                return ChecksumQueryResult.ProtocolError(STS_CHECKSUM_ERROR, "بيانات إطار Checksum 0xB0 غير صالحة (LEN=${dataFrame.payload.size})")
            }
            return ChecksumQueryResult.Success(Nec78k0Block0Reconstructor.decodeChecksumBytes(dataFrame.payload))
        } finally {
            transport.setRawRxMonitor(false)
        }
    }

    private sealed class Cumulative0xB0Outcome {
        data class Extracted(val updatedSession: Nec78k0RecoveryManager.Nec78k0RecoverySession) : Cumulative0xB0Outcome()
        data class UnsupportedFallback(val reason: String) : Cumulative0xB0Outcome()
        data class Paused(val failure: FlashOperationResult.Failure) : Cumulative0xB0Outcome()
        data class Fatal(val reason: String) : Cumulative0xB0Outcome()
    }

    /**
     * Attempts non-destructive 11-byte cumulative 0xB0 extraction ([01 07 B0 00 00 00 00 End_M End_L CS 03])
     * across Block 0 (0..1023) before falling back to 0x40 differential zeroing.
     * Byte[N] = ((CS(N-1) - CS(N)) + 0x10000) & 0xFF with CS(-1) = 0 and LE CS16 = (CS_High << 8) | CS_Low.
     */
    private suspend fun tryExtractBlock0ViaCumulative0xB0(
        context: Context,
        transport: Serial78k0Transport,
        detectedInfo: Nec78k0DetectedInfo,
        fullBuffer: ByteArray,
        blockCount: Int,
        clockFreqMhzText: String,
        existingSession: Nec78k0RecoveryManager.Nec78k0RecoverySession?,
        onProgress: (Int, Int, String) -> Unit,
        onLog: (String) -> Unit
    ): Cumulative0xB0Outcome {
        onLog("المرحلة 2: فحص السحب التراكمي غير التدميري عبر 0xB0 (إطار 11 بايت: 0x000000..N)...")
        val totalSize = detectedInfo.flashSizeBytes
        val b0Working = ByteArray(BLOCK_SIZE)
        var csPrev = 0
        val startMs = System.currentTimeMillis()
        var sessionRef = existingSession

        for (n in 0 until BLOCK_SIZE) {
            currentCoroutineContext().ensureActive()
            ProgrammerForegroundService.awaitIfPaused()
            if (!transport.isDeviceAttached()) {
                if (n > 0) {
                    System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
                }
                return Cumulative0xB0Outcome.Paused(
                    pauseWithWatchdog(
                        context, sessionRef, detectedInfo, fullBuffer, -1, blockCount,
                        "انقطع كابل USB OTG أثناء السحب التراكمي 0xB0 عند البايت $n/1023",
                        isUsbDetached = true,
                        block0OriginalExtracted = false,
                        block0DiffNextIndex = 0,
                        block0DiffPrevCs = -1
                    )
                )
            }

            val csRes = queryChecksum16WithRetry(
                transport = transport,
                startAddr = 0x000000,
                endAddr = n,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "0xB0 تراكمي 11B [0..$n]",
                maxRetries = 1,
                verboseLog = (n < 2),
                onLog = onLog
            )

            val csCurr = when (csRes) {
                is ChecksumQueryResult.Success -> csRes.checksum16 and 0xFFFF
                is ChecksumQueryResult.ParameterError -> {
                    return if (n < 16) {
                        Cumulative0xB0Outcome.UnsupportedFallback(
                            "أمر 0xB0 لا يقبل نطاق 1B/N (0x05) عند N=$n — التحويل تلقائياً إلى الاستخراج التفاضلي للبلوك 0 (0x40 + 0xB0)..."
                        )
                    } else {
                        Cumulative0xB0Outcome.Paused(
                            pauseWithWatchdog(
                                context, sessionRef, detectedInfo, fullBuffer, -1, blockCount,
                                "خطأ معاملات (0x05) أثناء 0xB0 التراكمي عند البايت $n",
                                isUsbDetached = false,
                                block0OriginalExtracted = false,
                                block0DiffNextIndex = 0,
                                block0DiffPrevCs = -1
                            )
                        )
                    }
                }
                is ChecksumQueryResult.ProtocolError -> {
                    if (csRes.statusCode == STS_PROTECT_ERROR) {
                        Nec78k0RecoveryManager.markCompletedAndCleanup(context, detectedInfo.deviceName)
                        return Cumulative0xB0Outcome.Fatal("المعالج محمي برمجياً (ERR_PROTECT 0x10).")
                    }
                    if (n < 2 && csRes.statusCode == STS_BLANK_CHECK_ERROR) {
                        return Cumulative0xB0Outcome.UnsupportedFallback(
                            "رفض 0xB0 النطاق الجزئي (0x1B) عند N=$n — التحويل إلى الاستخراج التفاضلي للبلوك 0 (0x40 + 0xB0)..."
                        )
                    }
                    return Cumulative0xB0Outcome.Paused(
                        pauseWithWatchdog(
                            context, sessionRef, detectedInfo, fullBuffer, -1, blockCount,
                            "فشل 0xB0 التراكمي عند البايت $n: ${csRes.statusLabel}",
                            isUsbDetached = false,
                            block0OriginalExtracted = false,
                            block0DiffNextIndex = 0,
                            block0DiffPrevCs = -1
                        )
                    )
                }
                is ChecksumQueryResult.TransportLost -> {
                    return Cumulative0xB0Outcome.Paused(
                        pauseWithWatchdog(
                            context, sessionRef, detectedInfo, fullBuffer, -1, blockCount,
                            csRes.reason,
                            isUsbDetached = csRes.isUsbDetached,
                            block0OriginalExtracted = false,
                            block0DiffNextIndex = 0,
                            block0DiffPrevCs = -1
                        )
                    )
                }
            }

            val delta16 = ((csPrev - csCurr) + 0x10000) and 0xFFFF
            if (delta16 > 0xFF) {
                val msg = String.format(
                    Locale.US,
                    "تجاوز فرق Checksum التراكمي 0xB0 عند N=%d: CS_prev=%04X -> CS_curr=%04X (Δ=%04X > 0xFF).",
                    n, csPrev, csCurr, delta16
                )
                return if (n < 2) {
                    Cumulative0xB0Outcome.UnsupportedFallback("$msg التحويل للاستخراج التفاضلي (0x40 + 0xB0)...")
                } else {
                    Cumulative0xB0Outcome.Paused(
                        pauseWithWatchdog(
                            context, sessionRef, detectedInfo, fullBuffer, -1, blockCount,
                            msg,
                            isUsbDetached = false,
                            block0OriginalExtracted = false,
                            block0DiffNextIndex = 0,
                            block0DiffPrevCs = -1
                        )
                    )
                }
            }

            b0Working[n] = (delta16 and 0xFF).toByte()
            csPrev = csCurr

            if (n == 15) {
                val first16Hex = (0 until 16).joinToString(" ") { "%02X".format(b0Working[it].toInt() and 0xFF) }
                onLog("تم التحقق الفيزيائي من أول 16 بايت عبر 0xB0 (11B LE): $first16Hex")
            }

            if (n % 8 == 0 || n == BLOCK_SIZE - 1) {
                val eta = formatEtaSuffix(n + 1, 0, BLOCK_SIZE, startMs)
                val label = String.format(
                    Locale.US,
                    "سحب Block 0 عبر 0xB0 التراكمي: %d/1024 (0x%02X)%s",
                    n + 1, delta16 and 0xFF, eta
                )
                onProgress(n + 1, totalSize, label)
                ProgrammerForegroundService.renewSessionLock(context, label)
            }
        }

        val verifiedB0ByteCs = Nec78k0Block0Reconstructor.compute78k0Checksum16(b0Working)
        val verifiedB0WordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(b0Working)
        val targetB0Cs = detectedInfo.block0Checksum16 and 0xFFFF
        if (targetB0Cs != 0 && !Nec78k0Block0Reconstructor.matches78k0Checksum16(b0Working, targetB0Cs)) {
            return Cumulative0xB0Outcome.UnsupportedFallback(
                String.format(
                    Locale.US,
                    "مجموع السحب التراكمي 0xB0 (%04X/%04X) لم يطابق Block0_Checksum (%04X) — التحويل إلى الاستخراج التفاضلي...",
                    verifiedB0ByteCs, verifiedB0WordCs, targetB0Cs
                )
            )
        }

        System.arraycopy(b0Working, 0, fullBuffer, 0, BLOCK_SIZE)
        Nec78k0RecoveryManager.savePartialBlocksAtomically(context, detectedInfo.deviceName, fullBuffer)
        val savedSession = (sessionRef ?: Nec78k0RecoveryManager.Nec78k0RecoverySession(
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
            block0Touched = false
        )).copy(
            updatedAtMs = System.currentTimeMillis(),
            block0OriginalExtracted = true,
            block0DiffNextIndex = BLOCK_SIZE,
            block0DiffPrevCs = csPrev
        )
        Nec78k0RecoveryManager.saveSessionCheckpoint(context, savedSession)
        val matchedB0Cs = if (verifiedB0WordCs == targetB0Cs) verifiedB0WordCs else verifiedB0ByteCs
        onLog(
            String.format(
                Locale.US,
                "✅ اكتمل استخراج Block 0 غير التدميري عبر 0xB0 (11B LE) وتطابقت بصمته %04X بنسبة 100%%.",
                matchedB0Cs
            )
        )
        return Cumulative0xB0Outcome.Extracted(savedSession)
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
        val isSampleKeyMatch = Nec78k0ProtocolRegistry.matchesSampleDatabaseKey(sigFrame.rawFrame) ||
            Nec78k0ProtocolRegistry.matchesSampleDatabaseKey(payload)
        val parityAscii = Nec78k0ProtocolRegistry.decodeParityAsciiSignature(payload)
        val rawAscii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        val deviceRegex = Regex("""(?:uPD|D)?78F[0-9]{4}[A-Z0-9]{0,3}""", RegexOption.IGNORE_CASE)
        val deviceMatch = deviceRegex.find(parityAscii) ?: deviceRegex.find(rawAscii)
        val rawDeviceName = deviceMatch?.value?.trim()?.uppercase()
            ?: if (isSampleKeyMatch) Nec78k0ProtocolRegistry.SAMPLE_CHIP_PART_NUMBER.uppercase()
            else fallbackChip?.partNumber?.uppercase()
            ?: "uPD78F0513A"

        val matched = findMatchingCatalogProcessor(rawDeviceName, catalog)
            ?: if (isSampleKeyMatch) findMatchingCatalogProcessor("uPD78F0513", catalog) else null
            ?: fallbackChip
        val canonicalDeviceName = matched?.partNumber ?: rawDeviceName

        // Parse 3-byte Last Code Flash Address from Silicon Signature payload if valid
        val parsedEndAddress = extractEndAddressFromSignature(payload)
            ?: ((matched?.flashSizeBytes ?: Nec78k0ProtocolRegistry.FLASH_SIZE_BYTES) - 1)
        val alignedFlashBytes = (matched?.flashSizeBytes ?: (parsedEndAddress + 1)).coerceAtLeast(BLOCK_SIZE)
        val blockCount = alignedFlashBytes / BLOCK_SIZE
        val familyGroup = infer78k0SubFamily(canonicalDeviceName, matched)
        val hasDf = matched?.chipConfig?.hasDataFlash ?: false
        val prefersBlock0x22 = isSampleKeyMatch ||
            canonicalDeviceName.contains("78F0513", ignoreCase = true) ||
            rawAscii.contains("78F1", ignoreCase = true)

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
            matchedProcessor = matched,
            protocolId = Nec78k0ProtocolRegistry.PROTOCOL_ID,
            prefersBlockErase0x22 = prefersBlock0x22
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
        if (norm == "78F1" || norm == "D78F1") {
            catalog.firstOrNull { normalize78k0Part(it.partNumber) == "D78F0513A" }?.let { return it }
            catalog.firstOrNull { normalize78k0Part(it.partNumber) == "D78F0513" }?.let { return it }
        }
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
            checksumValid = checksumValid,
            endByte = etxByte
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
