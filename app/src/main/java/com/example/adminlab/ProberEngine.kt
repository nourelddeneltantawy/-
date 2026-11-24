package com.example.adminlab

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class ProberEngine(context: Context) {

    companion object {
        private const val SOH = 0x01
        private const val STX = 0x02
        private const val ETX = 0x03
        private const val ETB = 0x17
        private const val ACK_NEC = 0x06
        private const val ACK_STM32 = 0x79
        private const val NACK_STM32 = 0x1F

        private const val STS_CMD_ERROR = 0x04
        private const val STS_PARAM_ERROR = 0x05
        private const val STS_CHECKSUM_ERROR = 0x07
        private const val STS_VERIFY_ERROR = 0x0F
        private const val STS_PROTECT_ERROR = 0x10
        private const val STS_NACK = 0x15
        private const val STS_ERASE_ERROR = 0x1A
        private const val STS_BLANK_CHECK_ERROR = 0x1B
        private const val STS_VERIFY_ERROR_1C = 0x1C

        // Commands that MUST NEVER be sent with 0 parameters or real flash addresses during Stage 3 sweep
        private val ZERO_ARG_DANGER_CMDS = setOf(0x20, 0x21, 0x82, 0x92, 0xA0, 0xA7)
        private val WRITE_ERASE_GUARD_CMDS = setOf(0x20, 0x21, 0x22, 0x31, 0x40, 0x41, 0x43, 0x44, 0x82, 0x92, 0xA0, 0xA7, 0xF5)

        private val HARD_REJECT_STATUS = setOf(
            STS_CMD_ERROR, STS_PARAM_ERROR, STS_CHECKSUM_ERROR, STS_VERIFY_ERROR,
            STS_PROTECT_ERROR, STS_NACK, STS_ERASE_ERROR, STS_BLANK_CHECK_ERROR,
            STS_VERIFY_ERROR_1C, NACK_STM32
        )
    }

    private data class SyncLock(
        val baudRate: Int,
        val flmd0PinState: String,
        val resetPinIdleState: String,
        val resetPinPulsePolarity: String,
        val dtrPolarity: String,
        val rtsPolarity: String,
        val inverted: Boolean,
        val useResetToggle: Boolean,
        val resetPulseMs: Int,
        val postResetDelayMs: Int,
        val syncByteGapMs: Int,
        val syncPulseHex: String,
        val framing: DetectedFraming,
        val firstReplyHex: String,
        val latencySampleMs: Int
    )

    private data class SignatureProbeCapture(
        val step: ChecklistStepItem,
        val rawC0Payload: ByteArray
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ProberUiState())
    val state: StateFlow<ProberUiState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var logSeq = 0L

    @Volatile
    private var activeJob: Job? = null
    @Volatile
    private var lastSyncLock: SyncLock? = null
    @Volatile
    private var pendingConfirmDeferred: CompletableDeferred<Boolean>? = null

    private val transport = ProberUsbTransport(
        context = context,
        onDeviceStatusChanged = { status ->
            _state.value = _state.value.copy(usbStatus = status)
        },
        onUnexpectedDetach = {
            if (_state.value.isBusy) {
                stopImmediately("تم فصل المبرمجة عن منفذ OTG أثناء الفحص.")
            }
        },
        onLog = { dir, msg -> appendLog(dir, msg) }
    )

    init {
        refreshUsbStatus()
    }

    fun refreshUsbStatus(): ProberUsbStatus = transport.scanStatus()

    fun setTargetFamily(family: ProberTargetFamily) {
        _state.value = _state.value.copy(selectedFamily = family)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun requestUsbPermission() {
        scope.launch {
            val ok = transport.requestPermissionOnly()
            appendLog(
                if (ok) ProberLogDirection.SUCCESS else ProberLogDirection.ERROR,
                if (ok) "تم منح صلاحية USB بنجاح." else "فشل أو رُفضت صلاحية USB."
            )
        }
    }

    /**
     * User clicked RED "استكمال" button inside the Stage 5 blocking modal dialog.
     */
    fun confirmPendingStage5Step() {
        val def = pendingConfirmDeferred
        _state.value = _state.value.copy(pendingConfirmDialog = null)
        def?.complete(true)
    }

    /**
     * User clicked "إلغاء / توقف إلى هنا" inside the Stage 5 blocking modal dialog.
     */
    fun cancelPendingStage5AndFinish() {
        val def = pendingConfirmDeferred
        _state.value = _state.value.copy(pendingConfirmDialog = null)
        def?.complete(false)
    }

    /**
     * One-tap comprehensive probe executing Stages 1 -> 4 safely, then sequential Stage 5 modal confirmations.
     */
    fun startFullSafeProtocolScan() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        lastSyncLock = null

        activeJob = scope.launch {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "جاري تنفيذ المرحلة 1: هندسة التوصيل والتزامن والقطبيات...",
                checklist = defaultChecklist(),
                blueprintSummary = ProberBlueprintSummary(),
                blueprintText = "",
                pendingConfirmDialog = null,
                canBypassSync = false
            )
            appendLog(ProberLogDirection.INFO, "══ بدء الفحص الشامل واستخراج البروتوكول الديناميكي (${family.labelAr}) ══")

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG. تأكد من توصيل المبرمجة ومنح الصلاحية.", allowBypass = false)
                    return@launch
                }

                // STAGE 1
                updateStep("stage1_handshake", ChecklistStepStatus.RUNNING, "تهيئة FT232R (9600 8N1) وفحص FLMD0/RESET + نبضات 0x00")
                val sync = runStage1HardwareHandshake(family)
                if (sync == null) {
                    updateStep("stage1_handshake", ChecklistStepStatus.FAILED, "لم يرد المعالج على أي نبضة تزامن — يمكنك الضغط على «تجاوز التزامن»")
                    failPipeline(
                        "فشل التزامن في المرحلة 1. تأكد من TX/RX و FLMD0 و RESET أو استخدم زر «تجاوز التزامن واختبار الأوامر مباشرة».",
                        allowBypass = true
                    )
                    return@launch
                }
                lastSyncLock = sync
                updateStep(
                    "stage1_handshake",
                    ChecklistStepStatus.PASSED,
                    "${sync.baudRate} bps | FLMD0: ${sync.flmd0PinState} | ${sync.framing.label}"
                )

                executeStages2To5(sync)
            } catch (_: CancellationException) {
                stopImmediately("تم إيقاف الفحص فوراً بناءً على طلب المشرف.")
            } catch (t: Throwable) {
                failPipeline("خطأ أثناء التنفيذ: ${t.message ?: t::class.java.simpleName}", allowBypass = (lastSyncLock == null))
            } finally {
                pendingConfirmDeferred?.complete(false)
                pendingConfirmDeferred = null
                _state.value = _state.value.copy(pendingConfirmDialog = null)
                transport.enterSafeIdleAndClose()
            }
        }
    }

    /**
     * Bypasses Stage 1 Sync lock and runs Stages 2.1 -> 4 (+ Stage 5 sequential modal prompts) directly.
     */
    fun bypassSyncAndRunStages2To4() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily

        activeJob = scope.launch {
            val defaultBaud = when (family) {
                ProberTargetFamily.STM32_UART -> 115200
                else -> 9600
            }
            val defaultFraming = when (family) {
                ProberTargetFamily.STM32_UART -> DetectedFraming.STM32_ACK
                ProberTargetFamily.RENESAS_R8C_RL78 -> DetectedFraming.RENESAS_RAW
                else -> DetectedFraming.NEC_STX_ETX
            }

            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "تجاوز المرحلة 1 — اختبار الأوامر مباشرة ($defaultBaud bps)...",
                checklist = defaultChecklist(),
                blueprintSummary = ProberBlueprintSummary(),
                blueprintText = "",
                pendingConfirmDialog = null,
                canBypassSync = false
            )
            appendLog(
                ProberLogDirection.WARN,
                "⚠️ تم تجاوز التزامن يدوياً — فتح المنفذ على $defaultBaud bps (${defaultFraming.label}) وبدء المراحل 2.1 إلى 4 مباشرة..."
            )

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG لتجاوز التزامن.", allowBypass = true)
                    return@launch
                }
                transport.configureUart(defaultBaud)
                transport.setControlLines(dtr = false, rts = false)
                delay(40)
                transport.flushRx()

                val bypassSync = SyncLock(
                    baudRate = defaultBaud,
                    flmd0PinState = "HIGH (+5V) / Bypassed (DTR=false)",
                    resetPinIdleState = "HIGH (+5V) / Static (RTS=false)",
                    resetPinPulsePolarity = "Bypassed (No Reset Toggle)",
                    dtrPolarity = "تجاوز يدوي (DTR=false -> FLMD0=+5V)",
                    rtsPolarity = "Static (RTS=false)",
                    inverted = false,
                    useResetToggle = false,
                    resetPulseMs = 100,
                    postResetDelayMs = 150,
                    syncByteGapMs = 10,
                    syncPulseHex = "Bypassed (Direct Mode)",
                    framing = defaultFraming,
                    firstReplyHex = "Bypassed",
                    latencySampleMs = 25
                )
                lastSyncLock = bypassSync
                updateStep(
                    "stage1_handshake",
                    ChecklistStepStatus.BYPASSED,
                    "تم التجاوز يدوياً • $defaultBaud bps • ${defaultFraming.label}"
                )

                executeStages2To5(bypassSync)
            } catch (_: CancellationException) {
                stopImmediately("تم إيقاف الفحص فوراً بناءً على طلب المشرف.")
            } catch (t: Throwable) {
                failPipeline("خطأ أثناء التنفيذ بعد التجاوز: ${t.message ?: t::class.java.simpleName}", allowBypass = true)
            } finally {
                pendingConfirmDeferred?.complete(false)
                pendingConfirmDeferred = null
                _state.value = _state.value.copy(pendingConfirmDialog = null)
                transport.enterSafeIdleAndClose()
            }
        }
    }

    private suspend fun executeStages2To5(sync: SyncLock) {
        var summary = ProberBlueprintSummary(
            baudRate = sync.baudRate,
            maxBaudTested = sync.baudRate,
            supportedBaudRates = listOf(sync.baudRate),
            flmd0PinState = sync.flmd0PinState,
            resetPinIdleState = sync.resetPinIdleState,
            resetPinPulsePolarity = sync.resetPinPulsePolarity,
            dtrPolarity = sync.dtrPolarity,
            rtsPolarity = sync.rtsPolarity,
            echoCancellation = transport.echoLoopbackObserved,
            syncPulseHex = sync.syncPulseHex,
            framing = sync.framing,
            safeDelayMs = (sync.latencySampleMs + 15).coerceIn(15, 250),
            timingMap = TimingMapMs(
                resetPulseMs = sync.resetPulseMs,
                postResetDelayMs = sync.postResetDelayMs,
                syncByteGapMs = sync.syncByteGapMs
            )
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 2: 5 Safe Read Schools + 0xFF vs 0x00 Lock Detection
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 2: فحص مدارس السحب الآمنة وكشف القفل...")

        val r1 = testDirectMemoryRead(sync)
        resyncLight(sync)
        val r2 = testChecksum1ByteAttack(sync)
        resyncLight(sync)
        val (r3, verifyDiffStr) = testVerify0x13Probe(sync)
        resyncLight(sync)
        val sigCapture = testSignatureRegisterRead(sync)
        val r4 = sigCapture.step
        resyncLight(sync)
        val r5 = testRamStubInjection(sync)

        // Only Direct Read (2.1), Checksum 1B (2.2), and RAM Stub (2.5) unlock safe reading.
        // Verify 0x13 (2.3) or Signature 0xC0 (2.4) alone means the MCU is still Locked!
        val safeOpenReads = listOf(r1, r2, r5).filter { it.status == ChecklistStepStatus.PASSED }
        val isMcuOpenForSafeRead = safeOpenReads.isNotEmpty()
        val auxNotes = listOf(r3, r4)
            .filter { it.status == ChecklistStepStatus.PASSED }
            .joinToString(" + ") { it.title }

        val winningReadDesc = if (isMcuOpenForSafeRead) {
            safeOpenReads.joinToString(" + ") { it.title }
        } else {
            if (auxNotes.isNotBlank()) {
                "Locked Bootloader (محمي ومقفل — نجح فقط: $auxNotes) — يتطلب بوابة المرحلة 5"
            } else {
                "Locked Bootloader (محمي ومقفل) — يتطلب بوابة المرحلة 5"
            }
        }
        summary = summary.copy(
            verify0x13DiffResult = verifyDiffStr,
            winningReadMethod = winningReadDesc,
            chipSignature = r4.takeIf { it.status == ChecklistStepStatus.PASSED }?.detail ?: ""
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 3: Full 0x00..0xFF Command & LEN 0..8 Surgical Sweep (Strict Guard Active)
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 3: المسح الجراحي للأوامر 0x00..0xFF وأطوال LEN 0..8...")
        updateStep("stage3_cmd_sweep", ChecklistStepStatus.RUNNING, "مسح 0x00..0xFF مع قائمة الحماية الصارمة...")
        resyncLight(sync)
        val discoveredCmds = runStage3SurgicalCommandSweep(sync)
        updateStep(
            "stage3_cmd_sweep",
            if (discoveredCmds.isNotEmpty()) ChecklistStepStatus.PASSED else ChecklistStepStatus.FAILED,
            "تم اكتشاف ${discoveredCmds.size} أمر مع أطوال المعاملات LEN"
        )
        summary = summary.copy(discoveredCommands = discoveredCmds)
        updateSummaryAndBlueprint(summary)

        // STAGE 4: Family-Wide Dynamic Algorithms (0xC0+0xB0 Flash Size, 0x90 Osc, 0x9A Baud State Machine, STX & 6-Point Timing)
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 4: الخوارزميات الديناميكية للعائلة وخريطة التوقيت...")
        updateStep("stage4_timing_bounds", ChecklistStepStatus.RUNNING, "فحص Flash End (0xC0/0xB0)، المذبذب 0x90، سرعات 0x9A، إطارات STX، و6 أزمنة")
        resyncLight(sync)
        summary = runStage4DynamicFamilyAlgorithms(sync, summary, sigCapture.rawC0Payload)
        updateStep(
            "stage4_timing_bounds",
            ChecklistStepStatus.PASSED,
            "End=${summary.flashEndAddressHex} | Osc=${summary.oscillatorProbeResults} | Bauds=${summary.supportedBaudRates} | STX=${summary.supportedStxPageSizes}"
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 5: Interactive Modal Warning Dialog Gate (No Hot-Swap!)
        if (isMcuOpenForSafeRead) {
            updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, "المعالج مفتوح للسحب الآمن — لا حاجة لاختبارات تعديلية خطرة")
            val finalSummary = summary.copy(
                advancedSchoolsNotes = "المعالج مفتوح للسحب الآمن ($winningReadDesc) — اكتملت المراحل 1..4 دون لمس الفلاش."
            )
            updateSummaryAndBlueprint(finalSummary)
            _state.value = _state.value.copy(
                phase = ProberStagePhase.COMPLETED,
                stageBanner = "اكتمل الفحص الشامل واستخراج البروتوكول الديناميكي بنجاح (المعالج مفتوح للسحب الآمن)"
            )
        } else {
            appendLog(
                ProberLogDirection.WARN,
                "⚠️ المعالج محمي ومقفل (فشلت القراءة المباشرة و1-بايت Checksum وRAM Stub). الانتقال إلى بوابة الأمان التفاعلية في المرحلة 5..."
            )
            runStage5InteractiveDestructiveProbes(sync, summary)
        }
    }

    fun stopImmediately(reason: String = "تم إيقاف الفحص فوراً وإغلاق المنفذ بأمان.") {
        pendingConfirmDeferred?.complete(false)
        pendingConfirmDeferred = null
        val job = activeJob
        activeJob = null
        job?.cancel()
        transport.enterSafeIdleAndClose()
        val wasBusy = _state.value.isBusy
        _state.value = _state.value.copy(
            pendingConfirmDialog = null,
            phase = if (wasBusy) ProberStagePhase.STOPPED else _state.value.phase,
            stageBanner = if (wasBusy) reason else _state.value.stageBanner
        )
        if (wasBusy) {
            appendLog(ProberLogDirection.WARN, reason)
        }
    }

    fun release() {
        stopImmediately()
        transport.release()
    }

    // ==================== STAGE 1: PIN LOGIC & HANDSHAKE ====================

    private suspend fun tryLiteralNec78k0Handshake(
        baud: Int,
        polDesc: String,
        inverted: Boolean,
        useResetToggle: Boolean
    ): SyncLock? {
        currentCoroutineContext().ensureActive()
        transport.configureUart(baud)
        transport.flushRx()

        val resetPulseMs = 100
        val postResetMs = 150
        val syncGapMs = 10

        val dtrFlmd0High = inverted
        val rtsResetLow = !inverted
        val rtsResetHigh = inverted

        val flmd0StateDesc = if (useResetToggle) {
            "HIGH (+5V) via DTR=$dtrFlmd0High"
        } else {
            "HIGH (+5V) Static via DTR=false"
        }
        val resetIdleDesc = if (useResetToggle) {
            "HIGH (+5V) via RTS=$rtsResetHigh"
        } else {
            "HIGH (+5V) Static via RTS=false"
        }
        val resetPulseDesc = if (useResetToggle) {
            if (inverted) "Active-High Pulse (RTS=$rtsResetLow->RTS=$rtsResetHigh, ${resetPulseMs}ms)"
            else "Active-Low Pulse (0V RTS=$rtsResetLow for ${resetPulseMs}ms -> +5V RTS=$rtsResetHigh)"
        } else {
            "Static (No Toggle)"
        }

        if (useResetToggle) {
            appendLog(
                ProberLogDirection.INFO,
                "تهيئة 78K0 ($baud bps 8N1 | $polDesc): FLMD0=+5V(DTR=$dtrFlmd0High), RESET=0V(RTS=$rtsResetLow) -> ${resetPulseMs}ms -> RESET=+5V(RTS=$rtsResetHigh) -> ${postResetMs}ms"
            )
            transport.setDtr(dtrFlmd0High)
            transport.setRts(rtsResetLow)
            delay(resetPulseMs.toLong())
            transport.setDtr(dtrFlmd0High)
            transport.setRts(rtsResetHigh)
            delay(postResetMs.toLong())
            transport.flushRx()
        } else {
            appendLog(ProberLogDirection.INFO, "تهيئة 78K0 ($baud bps 8N1 | بدون نبضة Reset): setDTR(false), setRTS(false)")
            transport.setControlLines(dtr = false, rts = false)
            delay(50)
            transport.flushRx()
        }

        // Literal 78K0 two-pulse sync: 0x00 -> 10ms -> 0x00 -> 50ms
        appendLog(ProberLogDirection.TX, "78K0 Sync Pulse #1 @ $baud -> 00")
        transport.writeRawBytes(byteArrayOf(0x00))?.let { err ->
            appendLog(ProberLogDirection.ERROR, "TX Error (Sync #1): $err")
            return null
        }
        delay(syncGapMs.toLong())

        appendLog(ProberLogDirection.TX, "78K0 Sync Pulse #2 @ $baud -> 00")
        transport.writeRawBytes(byteArrayOf(0x00))?.let { err ->
            appendLog(ProberLogDirection.ERROR, "TX Error (Sync #2): $err")
            return null
        }
        delay(50)

        val pulseRaw = transport.drainAvailableRaw(25)
        if (pulseRaw.isNotEmpty()) {
            if (pulseRaw.size == 2 && pulseRaw[0] == 0x00.toByte() && pulseRaw[1] == 0x00.toByte()) {
                transport.echoLoopbackObserved = true
                appendLog(ProberLogDirection.RX, "صدى نبضة المزامنة (Diode Echo): ${pulseRaw.toHexStr()}")
            } else {
                appendLog(ProberLogDirection.RX, "Raw بعد نبضة 0x00: ${pulseRaw.toHexStr()}")
            }
        }
        transport.flushRx()

        val resetCmd = buildNecCommandFrame(0x00) // 01 01 00 FF 03
        appendLog(ProberLogDirection.TX, "NEC Reset 0x00 ($polDesc • 1500ms) -> ${resetCmd.toHexStr()}")
        val tNec = System.currentTimeMillis()
        transport.writeRawBytes(resetCmd)?.let { err ->
            appendLog(ProberLogDirection.ERROR, "TX Error (Reset 0x00): $err")
            return null
        }

        val reply = transport.readNecHandshakeOrRaw(
            timeoutMs = 1500,
            echoCandidate = resetCmd,
            onRawChunk = { chunk ->
                appendLog(ProberLogDirection.RX, "Live Raw Chunk: ${chunk.toHexStr()}")
            }
        )
        val dtNec = (System.currentTimeMillis() - tNec).toInt().coerceAtLeast(5)

        if (reply.isEmpty()) {
            appendLog(ProberLogDirection.RX, "(Timeout 1500ms بعد Reset 0x00 @ $baud | $polDesc)")
            return null
        }

        appendLog(ProberLogDirection.RX, "Clean Reply: ${reply.toHexStr()}")
        if (reply.any { (it.toInt() and 0xFF) == STX }) {
            appendLog(ProberLogDirection.SUCCESS, "✅ نجح التزامن الحرفي 78K0 ($baud bps | $polDesc): ${reply.toHexStr()}")
            return SyncLock(
                baudRate = baud,
                flmd0PinState = flmd0StateDesc,
                resetPinIdleState = resetIdleDesc,
                resetPinPulsePolarity = resetPulseDesc,
                dtrPolarity = polDesc,
                rtsPolarity = resetPulseDesc,
                inverted = inverted,
                useResetToggle = useResetToggle,
                resetPulseMs = resetPulseMs,
                postResetDelayMs = postResetMs,
                syncByteGapMs = syncGapMs,
                syncPulseHex = "0x00 (${syncGapMs}ms) 0x00 + 01 01 00 FF 03",
                framing = DetectedFraming.NEC_STX_ETX,
                firstReplyHex = reply.toHexStr(),
                latencySampleMs = dtNec
            )
        }
        return null
    }

    private suspend fun runStage1HardwareHandshake(family: ProberTargetFamily): SyncLock? {
        if (family == ProberTargetFamily.NEC_78K0 ||
            family == ProberTargetFamily.AUTO_DETECT ||
            family == ProberTargetFamily.GENERIC_UART
        ) {
            appendLog(ProberLogDirection.INFO, "── المرحلة 1: التهيئة الحرفية لـ FT232R + 78K0 (9600 bps, 8N1) ──")
            tryLiteralNec78k0Handshake(9600, "Active-Low (DTR=FLMD0, RTS=RESET)", inverted = false, useResetToggle = true)?.let { return it }

            appendLog(ProberLogDirection.WARN, "لم يستجب بالقطبية الأولى — إعادة المحاولة بعكس قطبية DTR/RTS...")
            tryLiteralNec78k0Handshake(9600, "Active-High / عكس القطبية", inverted = true, useResetToggle = true)?.let { return it }

            tryLiteralNec78k0Handshake(9600, "بدون نبضة خطوط (Direct 9600)", inverted = false, useResetToggle = false)?.let { return it }

            if (family == ProberTargetFamily.NEC_78K0) {
                for (extraBaud in listOf(19200, 38400)) {
                    tryLiteralNec78k0Handshake(extraBaud, "Active-Low (DTR=FLMD0, RTS=RESET)", inverted = false, useResetToggle = true)?.let { return it }
                }
                return null
            }
        }

        val bauds = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(115200, 57600, 38400, 19200, 9600)
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(9600, 38400, 57600, 19200, 115200)
            else -> listOf(115200, 38400, 19200, 57600, 9600)
        }

        val configs = listOf(
            Triple("Active-Low (DTR=FLMD0, RTS=RESET)", false, true),
            Triple("Active-High / معكوسة", true, true),
            Triple("بدون تحكم خطوط (Direct)", false, false)
        )

        val pulses: List<Pair<String, ByteArray>> = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(
                "0x7F (STM32 Sync)" to byteArrayOf(0x7F),
                "0x00" to byteArrayOf(0x00),
                "0x55" to byteArrayOf(0x55)
            )
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(
                "0x00" to byteArrayOf(0x00),
                "0xB0" to byteArrayOf(0xB0.toByte()),
                "0x55" to byteArrayOf(0x55),
                "0xA0" to byteArrayOf(0xA0.toByte())
            )
            else -> listOf(
                "0x7F (STM32 Boot Sync)" to byteArrayOf(0x7F),
                "0x55 (AutoBaud)" to byteArrayOf(0x55),
                "0xA0 (Boot Pulse)" to byteArrayOf(0xA0.toByte()),
                "0xB0 (Renesas Status)" to byteArrayOf(0xB0.toByte())
            )
        }

        for (baud in bauds) {
            currentCoroutineContext().ensureActive()
            transport.configureUart(baud)
            appendLog(ProberLogDirection.INFO, "── المرحلة 1: اختبار السرعة $baud bps ──")

            for ((polDesc, inverted, doToggle) in configs) {
                currentCoroutineContext().ensureActive()
                if (doToggle) {
                    transport.pulseResetSequence(inverted, holdResetMs = 100L, settleMs = 150L)
                } else {
                    transport.setControlLines(dtr = false, rts = false)
                    delay(50)
                }
                transport.flushRx()

                for ((pulseName, pulseBytes) in pulses) {
                    currentCoroutineContext().ensureActive()
                    val t0 = System.currentTimeMillis()
                    val rx = transport.transceive(
                        txBytes = pulseBytes,
                        expectedMax = 24,
                        timeoutMs = 260,
                        label = "Sync [$pulseName @ $baud | $polDesc]"
                    )
                    val dt = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(5)
                    if (rx.isEmpty()) continue

                    val flmd0State = if (doToggle) "HIGH (+5V) via DTR=$inverted" else "Static (DTR=false)"
                    val resetIdle = if (doToggle) "HIGH (+5V) via RTS=$inverted" else "Static (RTS=false)"
                    val pulsePol = if (doToggle) (if (inverted) "Active-High Pulse (100ms)" else "Active-Low Pulse (100ms)") else "Static"
                    return SyncLock(
                        baudRate = baud,
                        flmd0PinState = flmd0State,
                        resetPinIdleState = resetIdle,
                        resetPinPulsePolarity = pulsePol,
                        dtrPolarity = polDesc,
                        rtsPolarity = pulsePol,
                        inverted = inverted,
                        useResetToggle = doToggle,
                        resetPulseMs = 100,
                        postResetDelayMs = 150,
                        syncByteGapMs = 10,
                        syncPulseHex = pulseBytes.toHexStr(),
                        framing = classifyFraming(rx, pulseBytes),
                        firstReplyHex = rx.toHexStr(),
                        latencySampleMs = dt
                    )
                }
            }
        }
        return null
    }

    private suspend fun resyncLight(sync: SyncLock) {
        currentCoroutineContext().ensureActive()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            transport.transceive(
                txBytes = buildNecCommandFrame(0x00),
                expectedMax = 12,
                timeoutMs = 180,
                label = "Resync [0x00]",
                stopOnCompleteNecFrame = true,
                logTxRx = false
            )
        } else {
            delay(15)
            transport.flushRx()
        }
    }

    // ==================== STAGE 2: 5 SAFE READ SCHOOLS + LOCK DETECTION ====================

    private suspend fun testDirectMemoryRead(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_direct_read", ChecklistStepStatus.RUNNING, "اختبار 0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF (16B)...")
        val cmds = listOf(0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF)

        if (sync.framing == DetectedFraming.STM32_ACK) {
            val a1 = transport.transceive(byteArrayOf(0x11, 0xEE.toByte()), 4, 240, "STM32 Read [0x11]")
            if (a1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val a2 = transport.transceive(byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x08), 4, 240, "STM32 Addr 0x08000000")
                if (a2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val data = transport.transceive(byteArrayOf(0x0F, 0xF0.toByte()), 24, 380, "STM32 16B Read")
                    if (data.size >= 17 && (data[0].toInt() and 0xFF) == ACK_STM32) {
                        return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "الأمر [0x11] -> ${data.copyOfRange(1, 17).toHexStr()}")
                    }
                }
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "الأمر 0x11 مرفوض (RDP محمي)")
        }

        for (cmd in cmds) {
            currentCoroutineContext().ensureActive()
            val pkts = when (sync.framing) {
                DetectedFraming.NEC_STX_ETX -> listOf(
                    buildNecCommandFrame(cmd, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0F)),
                    buildNecCommandFrame(cmd, byteArrayOf(0x00, 0x00, 0x00, 0x10))
                )
                DetectedFraming.RENESAS_RAW -> listOf(
                    byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x10),
                    byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x00, 0x10)
                )
                else -> listOf(
                    byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x00, 0x10),
                    buildNecCommandFrame(cmd, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0F))
                )
            }
            for (pkt in pkts) {
                val rx = transport.transceive(pkt, 32, 260, "Direct Read [0x%02X]".format(cmd))
                val real16 = extract16Bytes(rx, sync.framing)
                if (real16 != null) {
                    return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "الأمر [0x%02X] -> ${real16.toHexStr()}".format(cmd))
                }
            }
        }
        return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "فشلت جميع أوامر القراءة المباشرة")
    }

    private suspend fun testChecksum1ByteAttack(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_checksum_1b", ChecklistStepStatus.RUNNING, "اختبار 0xB0 على 1 بايت ثم 2 بايت...")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, "غير مدعوم في بروتوكول STM32")
        }

        val c1 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        val rx1 = transport.transceive(c1, 16, 300, "Checksum 1B [0xB0]", stopOnCompleteNecFrame = true)
        val cs1 = parseChecksum16(rx1) ?: run {
            if (parseNecStatus(rx1) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 280, true)) else null
        }

        val c2 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
        val rx2 = transport.transceive(c2, 16, 300, "Checksum 2B [0xB0]", stopOnCompleteNecFrame = true)
        val cs2 = parseChecksum16(rx2) ?: run {
            if (parseNecStatus(rx2) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 280, true)) else null
        }

        return if (cs1 != null && cs2 != null) {
            updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.PASSED, "الأمر [0xB0] -> CS(1B)=0x%04X | CS(2B)=0x%04X".format(cs1, cs2))
        } else {
            updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, "فشل نطاق 1B/2B في 0xB0")
        }
    }

    /**
     * Stage 2.3: Verify 0x13 differential lock detector:
     * Sends BOTH a 0xFF page AND a 0x00 page to 0x13 and compares responses.
     */
    private suspend fun testVerify0x13Probe(sync: SyncLock): Pair<ChecklistStepItem, String> {
        updateStep("stage2_verify_0x13", ChecklistStepStatus.RUNNING, "اختبار 0x13 على صفحة 0xFF ثم صفحة 0x00 لكشف القفل...")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "غير منطبق على STM32")
            return item to "N/A (STM32)"
        }

        // Determine supported Verify window: try 256B first, fallback 128B on 0x05
        var pageLen = 256
        var rangeEndByte = 0xFF.toByte()
        var hdr = buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, rangeEndByte))
        var hdrRx = transport.transceive(hdr, 12, 320, "Verify 0xFF Header [0x13]", stopOnCompleteNecFrame = true)
        if (parseNecStatus(hdrRx) == STS_PARAM_ERROR) {
            pageLen = 128
            rangeEndByte = 0x7F.toByte()
            hdr = buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, rangeEndByte))
            hdrRx = transport.transceive(hdr, 12, 320, "Verify 128B Fallback [0x13]", stopOnCompleteNecFrame = true)
        }

        if (parseNecStatus(hdrRx) != ACK_NEC) {
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "رفض المعالج ترويسة 0x13 (${formatByte(parseNecStatus(hdrRx))})")
            return item to "0x13 Header Rejected (${formatByte(parseNecStatus(hdrRx))})"
        }

        val ffPage = ByteArray(pageLen) { 0xFF.toByte() }
        val ffRx = transport.transceive(
            buildNecDataFrame(ffPage, isLast = true),
            16, 520, "Verify ${pageLen}x0xFF [0x13]",
            stopOnCompleteNecFrame = true,
            paced64B = pageLen > 64
        )
        val stsFF = effectiveNecStatus(ffRx)

        // Second Verify pass with all-0x00 page to prove whether MCU differentiates content
        delay(15)
        val hdr2 = buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, rangeEndByte))
        val hdr2Rx = transport.transceive(hdr2, 12, 320, "Verify 0x00 Header [0x13]", stopOnCompleteNecFrame = true)
        var sts00: Int? = null
        if (parseNecStatus(hdr2Rx) == ACK_NEC) {
            val zeroPage = ByteArray(pageLen) { 0x00.toByte() }
            val zeroRx = transport.transceive(
                buildNecDataFrame(zeroPage, isLast = true),
                16, 520, "Verify ${pageLen}x0x00 [0x13]",
                stopOnCompleteNecFrame = true,
                paced64B = pageLen > 64
            )
            sts00 = effectiveNecStatus(zeroRx)
        }

        val differentiates = (stsFF != null && sts00 != null && stsFF != sts00)
        val diffSummary = "Page0xFF(${pageLen}B)->${formatByte(stsFF)} | Page0x00(${pageLen}B)->${formatByte(sts00)} | " +
            if (differentiates) "يميّز الفرق" else "رد متطابق/حماية قفل"

        val validSts = setOf(ACK_NEC, STS_VERIFY_ERROR, STS_VERIFY_ERROR_1C)
        return if ((stsFF in validSts) || (sts00 in validSts)) {
            val item = updateAndReturn(
                "stage2_verify_0x13",
                ChecklistStepStatus.PASSED,
                "$diffSummary (لا يكفي لفك القفل للسحب المباشر)"
            )
            item to diffSummary
        } else {
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "فشل إطار البيانات في 0x13 ($diffSummary)")
            item to diffSummary
        }
    }

    private suspend fun testSignatureRegisterRead(sync: SyncLock): SignatureProbeCapture {
        updateStep("stage2_signature", ChecklistStepStatus.RUNNING, "اختبار 0xC0, 0xC5, 0x01, 0x02...")
        val cmds = listOf(0xC0, 0xC5, 0x01, 0x02)
        for (cmd in cmds) {
            currentCoroutineContext().ensureActive()
            val tx = when (sync.framing) {
                DetectedFraming.STM32_ACK -> byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte())
                DetectedFraming.NEC_STX_ETX -> buildNecCommandFrame(cmd)
                else -> byteArrayOf(cmd.toByte())
            }
            val rx = transport.transceive(tx, 64, 300, "Signature [0x%02X]".format(cmd))
            if (rx.isEmpty()) continue
            if (sync.framing == DetectedFraming.STM32_ACK && (rx[0].toInt() and 0xFF) == ACK_STM32) {
                val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
                return SignatureProbeCapture(item, rx)
            } else if (sync.framing == DetectedFraming.NEC_STX_ETX && parseNecStatus(rx) == ACK_NEC) {
                val extra = if (!rx.drop(4).any { (it.toInt() and 0xFF) == STX }) transport.readFollowUp(64, 320, true) else ByteArray(0)
                val all = rx + extra
                val payload = extractNecSecondFramePayload(all) ?: all
                val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                val label = if (ascii.isNotBlank()) "[0x%02X] $ascii | Raw=${payload.toHexStr()}".format(cmd)
                else "[0x%02X] ${payload.take(24).toByteArray().toHexStr()}".format(cmd)
                val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, label)
                return SignatureProbeCapture(item, payload)
            } else if (sync.framing != DetectedFraming.NEC_STX_ETX && sync.framing != DetectedFraming.STM32_ACK) {
                val b0 = rx[0].toInt() and 0xFF
                if (b0 !in HARD_REJECT_STATUS) {
                    val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
                    return SignatureProbeCapture(item, rx)
                }
            }
        }
        val item = updateAndReturn("stage2_signature", ChecklistStepStatus.FAILED, "لم تستجب أوامر التوقيع")
        return SignatureProbeCapture(item, ByteArray(0))
    }

    private suspend fun testRamStubInjection(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_ram_stub", ChecklistStepStatus.RUNNING, "اختبار عنوان RAM مؤقت دون لمس الفلاش...")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            val a1 = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 4, 240, "STM32 RAM Cmd [0x31]")
            if (a1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val a2 = transport.transceive(byteArrayOf(0x20, 0x00, 0x10, 0x00, 0x30), 4, 240, "STM32 SRAM 0x20001000")
                if (a2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    return updateAndReturn("stage2_ram_stub", ChecklistStepStatus.PASSED, "[0x31] قبل عنوان SRAM 0x20001000")
                }
            }
            return updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "مرفوض على RAM")
        }

        val probes = listOf(
            0x31 to buildNecCommandFrame(0x31, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte())),
            0x40 to buildNecCommandFrame(0x40, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte()))
        )
        for ((cmd, frame) in probes) {
            val rx = transport.transceive(frame, 12, 260, "RAM Stub [0x%02X @ 0x00FEF0]".format(cmd), stopOnCompleteNecFrame = true)
            if (parseNecStatus(rx) == ACK_NEC) {
                return updateAndReturn("stage2_ram_stub", ChecklistStepStatus.PASSED, "[0x%02X] ACK على RAM 0x00FEF0".format(cmd))
            }
        }
        return updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "لا يقبل تحميل كود RAM مؤقت")
    }

    // ==================== STAGE 3: FULL 0x00..0xFF SURGICAL SWEEP ====================

    private suspend fun runStage3SurgicalCommandSweep(sync: SyncLock): List<DiscoveredCommandEntry> {
        val discovered = mutableListOf<DiscoveredCommandEntry>()

        if (sync.framing == DetectedFraming.STM32_ACK) {
            val getRx = transport.transceive(byteArrayOf(0x00, 0xFF.toByte()), 32, 320, "STM32 Get Table [0x00]")
            if (getRx.size >= 3 && (getRx[0].toInt() and 0xFF) == ACK_STM32) {
                val count = (getRx[1].toInt() and 0xFF) + 1
                for (i in 2 until min(getRx.size, 2 + count)) {
                    val b = getRx[i].toInt() and 0xFF
                    discovered.add(
                        DiscoveredCommandEntry(
                            cmdHex = "0x%02X".format(b),
                            cmdByte = b,
                            replyStatusHex = "0x79",
                            exactParamLen = guessStm32ParamLen(b),
                            roleGuess = guessCommandRole(b),
                            isSafeReadOnly = b !in WRITE_ERASE_GUARD_CMDS
                        )
                    )
                }
                return discovered
            }
        }

        val candidateBytes = mutableListOf<Pair<Int, Int>>()
        for (cmd in 0x00..0xFF) {
            currentCoroutineContext().ensureActive()
            if (cmd % 32 == 0) {
                updateStep("stage3_cmd_sweep", ChecklistStepStatus.RUNNING, "مسح البايتات: 0x%02X / 0xFF (مكتشف: ${candidateBytes.size})".format(cmd))
            }

            if (sync.framing == DetectedFraming.STM32_ACK) {
                if (cmd in ZERO_ARG_DANGER_CMDS) continue
                val rx = transport.transceive(
                    txBytes = byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte()),
                    expectedMax = 8,
                    timeoutMs = 55,
                    label = "Sweep 0x%02X".format(cmd),
                    logTxRx = false
                )
                val b0 = rx.firstOrNull()?.let { it.toInt() and 0xFF }
                if (b0 == ACK_STM32) {
                    candidateBytes.add(cmd to ACK_STM32)
                    appendLog(ProberLogDirection.SUCCESS, "رصد أمر [0x%02X] -> ACK 0x79".format(cmd))
                }
                continue
            }

            // Guard dangerous 0-arg opcodes by sending impossible 6x0xFF args
            val tx = if (cmd in ZERO_ARG_DANGER_CMDS) {
                buildNecCommandFrame(cmd, byteArrayOf(-1, -1, -1, -1, -1, -1))
            } else {
                buildNecCommandFrame(cmd)
            }

            val rx = transport.transceive(
                txBytes = tx,
                expectedMax = 12,
                timeoutMs = 60,
                label = "Sweep 0x%02X".format(cmd),
                stopOnCompleteNecFrame = true,
                logTxRx = false
            )
            if (rx.isEmpty()) continue

            val sts = if (sync.framing == DetectedFraming.NEC_STX_ETX) parseNecStatus(rx) else (rx[0].toInt() and 0xFF)
            if (sts == ACK_NEC || sts == STS_PARAM_ERROR || sts == STS_BLANK_CHECK_ERROR || sts == STS_PROTECT_ERROR) {
                candidateBytes.add(cmd to sts)
                appendLog(ProberLogDirection.SUCCESS, "رصد أمر [0x%02X] -> Status 0x%02X (${guessCommandRole(cmd)})".format(cmd, sts))
            }
        }

        // Phase B: Probe parameter length LEN = 0..8 safely for every discovered command
        for ((cmd, initialSts) in candidateBytes) {
            currentCoroutineContext().ensureActive()
            val detectedLen = probeExactParamLen(sync, cmd, initialSts)
            discovered.add(
                DiscoveredCommandEntry(
                    cmdHex = "0x%02X".format(cmd),
                    cmdByte = cmd,
                    replyStatusHex = "0x%02X".format(initialSts),
                    exactParamLen = detectedLen,
                    roleGuess = guessCommandRole(cmd),
                    isSafeReadOnly = cmd !in WRITE_ERASE_GUARD_CMDS
                )
            )
        }
        return discovered
    }

    private suspend fun probeExactParamLen(sync: SyncLock, cmd: Int, initialSts: Int): Int? {
        if (sync.framing == DetectedFraming.STM32_ACK) return guessStm32ParamLen(cmd)
        if (initialSts == ACK_NEC && cmd !in WRITE_ERASE_GUARD_CMDS && cmd != 0x00) return 0

        val isGuarded = cmd in WRITE_ERASE_GUARD_CMDS
        val fillByte: Byte = if (isGuarded) 0xFF.toByte() else 0x00

        for (lenArgs in 0..8) {
            currentCoroutineContext().ensureActive()
            if (isGuarded && lenArgs == 0) continue

            val args = when {
                !isGuarded && lenArgs == 6 && (cmd == 0xB0 || cmd == 0x32 || cmd == 0x30 || cmd == 0x13) -> {
                    // Valid non-destructive 256B page range: 0x000000..0x0000FF
                    byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())
                }
                cmd == 0x9A && lenArgs == 1 -> byteArrayOf(0x00) // 9600 safe keep-baud
                cmd == 0x90 && lenArgs == 1 -> byteArrayOf(0x00) // default osc code
                else -> ByteArray(lenArgs) { fillByte }
            }

            val rx = transport.transceive(
                txBytes = buildNecCommandFrame(cmd, args),
                expectedMax = 12,
                timeoutMs = 110,
                label = "LEN=$lenArgs [0x%02X]".format(cmd),
                stopOnCompleteNecFrame = true,
                logTxRx = false
            )
            val sts = parseNecStatus(rx)
            if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                if (cmd == 0x13 && sts == ACK_NEC) {
                    // Cancel pending STX data phase cleanly
                    resyncLight(sync)
                }
                return lenArgs
            }
        }
        return when (cmd) {
            0x00, 0xC0, 0xC5 -> 0
            0x90, 0x9A, 0xA0 -> 1
            0x13, 0x22, 0x30, 0x32, 0x40, 0xB0 -> 6
            0x20 -> 0
            else -> null
        }
    }

    // ==================== STAGE 4: DYNAMIC FAMILY ALGORITHMS & 6-POINT TIMING ====================

    private suspend fun runStage4DynamicFamilyAlgorithms(
        sync: SyncLock,
        base: ProberBlueprintSummary,
        rawC0Payload: ByteArray
    ): ProberBlueprintSummary {
        var maxBaud = sync.baudRate
        val supportedBauds = mutableListOf(sync.baudRate)
        var pageSize = 256
        var blockSize = 1024
        var flashEndHex = "0x007FFF (32 KB)"
        var flashRule = "Static Fallback"
        var oscRule = "0x90 not tested / default internal oscillator"
        var oscProbeStr = "--"
        var baudRule = "Fixed at ${sync.baudRate} bps"
        val supportedStxPages = mutableListOf<Int>()

        val rttSamples = mutableListOf(sync.latencySampleMs)
        var measuredBaudSwitchMs = 25
        var measuredCmdToDataMs = 12
        var measuredFrameToFrameMs = 18

        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            // 4.1: Dynamic Flash Size Detection (0xC0 Signature End_Addr + 0xB0 Checksum Boundary Scan)
            val c0ParsedEnd = parseNecSignatureEndAddress(rawC0Payload)
            val endCandidates = listOf(
                0x03FFFF to "0x03FFFF (256 KB)",
                0x01FFFF to "0x01FFFF (128 KB)",
                0x00FFFF to "0x00FFFF (64 KB)",
                0x007FFF to "0x007FFF (32 KB)",
                0x003FFF to "0x003FFF (16 KB)",
                0x001FFF to "0x001FFF (8 KB)"
            )
            var b0EndLabel: String? = null
            for ((endAddr, label) in endCandidates) {
                currentCoroutineContext().ensureActive()
                val t0 = System.currentTimeMillis()
                val csCmd = buildNecCommandFrame(0xB0, addrRangeBytes(0x000000, endAddr))
                val rx = transport.transceive(csCmd, 16, 280, "4.1 Boundary 0xB0 [$label]", stopOnCompleteNecFrame = true)
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || parseChecksum16(rx) != null) {
                    rttSamples.add((System.currentTimeMillis() - t0).toInt())
                    b0EndLabel = label
                    break
                }
            }
            if (b0EndLabel != null) {
                flashEndHex = b0EndLabel
            }
            flashRule = buildString {
                append("Read 0xC0 Silicon Signature")
                if (c0ParsedEnd != null) {
                    append(" (0xC0 End=0x%06X)".format(c0ParsedEnd))
                } else if (rawC0Payload.isNotEmpty()) {
                    append(" (0xC0 ${rawC0Payload.size}B raw)")
                }
                append(" then binary/stepped 0xB0 Checksum boundary verify (256KB->8KB) -> Confirmed $flashEndHex")
            }

            // Block Size Boundary Probe via Read-Only 0x32 Blank Check
            for (bSize in listOf(1024, 512, 2048, 4096)) {
                currentCoroutineContext().ensureActive()
                val endOff = bSize - 1
                val rx = transport.transceive(
                    buildNecCommandFrame(0x32, byteArrayOf(0x00, 0x00, 0x00, 0x00, ((endOff ushr 8) and 0xFF).toByte(), (endOff and 0xFF).toByte())),
                    12, 220, "4.1 Block Probe 0x32 [${bSize}B]", stopOnCompleteNecFrame = true
                )
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                    blockSize = bSize
                    break
                }
            }

            // 4.2: Dynamic Oscillator Negotiation (0x90: 0x00, 0x08, 0x10, 0x14 + 4B fallback)
            val oscCodes = listOf(0x00 to "Internal/Default", 0x08 to "8MHz", 0x10 to "16MHz", 0x14 to "20MHz")
            val oscSummaryParts = mutableListOf<String>()
            var any1ByteOscAck = false
            for ((code, name) in oscCodes) {
                currentCoroutineContext().ensureActive()
                val rx = transport.transceive(
                    buildNecCommandFrame(0x90, byteArrayOf(code.toByte())),
                    12, 200, "4.2 Osc Probe 0x90 [0x%02X=$name]".format(code), stopOnCompleteNecFrame = true
                )
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC) any1ByteOscAck = true
                oscSummaryParts.add("0x%02X($name)=%s".format(code, formatByte(sts)))
            }
            var fourByteOscAck = false
            if (!any1ByteOscAck) {
                val rx4b = transport.transceive(
                    buildNecCommandFrame(0x90, byteArrayOf(0x10, 0x00, 0x00, 0x08)),
                    12, 220, "4.2 Osc 4B Fallback [0x90 10 00 00 08]", stopOnCompleteNecFrame = true
                )
                val sts4b = parseNecStatus(rx4b)
                fourByteOscAck = (sts4b == ACK_NEC)
                oscSummaryParts.add("4B(10 00 00 08)=${formatByte(sts4b)}")
            }
            oscProbeStr = oscSummaryParts.joinToString(" | ")
            oscRule = when {
                any1ByteOscAck -> "Send 0x90 with 1-byte oscillator code (0x00=Internal 8MHz / 0x08..0x14=External Crystal MHz) before 0x9A baud switch; fallback to 0x00 if crystal absent."
                fourByteOscAck -> "Send 0x90 with 4-byte Renesas frequency payload [freqMantissa, exp, ...] (e.g., 10 00 00 08) before baud switch."
                else -> "0x90 rejected/unsupported on this silicon ($oscProbeStr) — use default internal boot clock."
            }

            // 4.3: Multi-Stage Baud Rate State Machine (0x9A across 19200..115200 + 0x00 Ping verify)
            val baudTargets = listOf(19200 to 0x01, 38400 to 0x02, 57600 to 0x03, 115200 to 0x04)
            val switchSamples = mutableListOf<Int>()
            for ((targetBaud, code) in baudTargets) {
                currentCoroutineContext().ensureActive()
                val tStart = System.currentTimeMillis()
                val swRx = transport.transceive(
                    buildNecCommandFrame(0x9A, byteArrayOf(code.toByte())),
                    12, 220, "4.3 Baud 0x9A [$targetBaud code=0x%02X]".format(code), stopOnCompleteNecFrame = true
                )
                if (parseNecStatus(swRx) == ACK_NEC) {
                    transport.configureUart(targetBaud)
                    delay(18)
                    val ping = transport.transceive(
                        buildNecCommandFrame(0x00),
                        12, 220, "4.3 Ping 0x00 @ $targetBaud bps", stopOnCompleteNecFrame = true
                    )
                    val dtSwitch = (System.currentTimeMillis() - tStart).toInt().coerceAtLeast(10)
                    if (parseNecStatus(ping) == ACK_NEC) {
                        supportedBauds.add(targetBaud)
                        if (targetBaud > maxBaud) maxBaud = targetBaud
                        switchSamples.add(dtSwitch)
                        appendLog(ProberLogDirection.SUCCESS, "✅ تأكيد سرعة $targetBaud bps عبر 0x9A + Ping 0x00!")
                    }
                    // Restore back to sync.baudRate via hardware reset so next baud test starts deterministically
                    transport.configureUart(sync.baudRate)
                    if (sync.useResetToggle) {
                        transport.pulseResetSequence(sync.inverted, holdResetMs = sync.resetPulseMs.toLong(), settleMs = sync.postResetDelayMs.toLong())
                        resyncHandshakeQuiet(sync)
                    } else {
                        resyncLight(sync)
                    }
                }
            }
            if (switchSamples.isNotEmpty()) {
                measuredBaudSwitchMs = switchSamples.average().toInt().coerceIn(10, 120)
            }
            baudRule = "Connect & sync at ${sync.baudRate} bps -> negotiate 0x90 Osc -> send 0x9A [code] -> wait ${measuredBaudSwitchMs}ms after ACK -> reconfigure host UART -> confirm with 0x00 Ping -> Supported: ${supportedBauds.distinct().sorted().joinToString("/")} bps"

            // 4.4: STX (0x02) Frame Probe (256B LEN=0x00 & 128B LEN=0x80 with ETB/ETX) + 6-Point Timing Map
            // Probe 256B STX (LEN=0x00)
            val tCmd256 = System.currentTimeMillis()
            val h256 = transport.transceive(
                buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())),
                12, 240, "4.4 STX 256B Header [0x13]", stopOnCompleteNecFrame = true
            )
            if (parseNecStatus(h256) == ACK_NEC) {
                val cmd2DataDt = (System.currentTimeMillis() - tCmd256).toInt().coerceIn(5, 80)
                measuredCmdToDataMs = cmd2DataDt
                delay(5)
                val tStx = System.currentTimeMillis()
                val rx256 = transport.transceive(
                    buildNecDataFrame(ByteArray(256) { 0xFF.toByte() }, isLast = true),
                    16, 480, "4.4 STX 256B Frame (LEN=0x00, ETX=0x03)", stopOnCompleteNecFrame = true, paced64B = true
                )
                val sts256 = effectiveNecStatus(rx256)
                if (sts256 == ACK_NEC || sts256 == STS_VERIFY_ERROR || sts256 == STS_VERIFY_ERROR_1C) {
                    supportedStxPages.add(256)
                    pageSize = 256
                    measuredFrameToFrameMs = (System.currentTimeMillis() - tStx).toInt().coerceIn(8, 120)
                }
            }

            // Probe 128B STX multi-frame (ETB 0x17 + ETX 0x03 on 256B window)
            val h128 = transport.transceive(
                buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x7F.toByte())),
                12, 240, "4.4 STX 128B Header [0x13]", stopOnCompleteNecFrame = true
            )
            if (parseNecStatus(h128) == ACK_NEC) {
                val rx128 = transport.transceive(
                    buildNecDataFrame(ByteArray(128) { 0xFF.toByte() }, isLast = true),
                    16, 380, "4.4 STX 128B Frame (LEN=0x80, ETX=0x03)", stopOnCompleteNecFrame = true, paced64B = true
                )
                val sts128 = effectiveNecStatus(rx128)
                if (sts128 == ACK_NEC || sts128 == STS_VERIFY_ERROR || sts128 == STS_VERIFY_ERROR_1C) {
                    supportedStxPages.add(128)
                    if (256 !in supportedStxPages) pageSize = 128
                }
            } else if (parseNecStatus(h128) == STS_PARAM_ERROR) {
                // Some bootloaders align Verify range to 256B but allow two 128B ETB+ETX frames
                val h256b = transport.transceive(
                    buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())),
                    12, 240, "4.4 STX Dual-128B ETB+ETX Header [0x13]", stopOnCompleteNecFrame = true
                )
                if (parseNecStatus(h256b) == ACK_NEC) {
                    val rxEtb = transport.transceive(
                        buildNecDataFrame(ByteArray(128) { 0xFF.toByte() }, isLast = false),
                        12, 320, "4.4 STX 128B #1 (ETB=0x17)", stopOnCompleteNecFrame = true, paced64B = true
                    )
                    if (parseNecStatus(rxEtb) == ACK_NEC) {
                        val rxEtx = transport.transceive(
                            buildNecDataFrame(ByteArray(128) { 0xFF.toByte() }, isLast = true),
                            16, 360, "4.4 STX 128B #2 (ETX=0x03)", stopOnCompleteNecFrame = true, paced64B = true
                        )
                        val stsLast = effectiveNecStatus(rxEtx)
                        if (stsLast == ACK_NEC || stsLast == STS_VERIFY_ERROR || stsLast == STS_VERIFY_ERROR_1C) {
                            supportedStxPages.add(128)
                        }
                    } else {
                        resyncLight(sync)
                    }
                }
            }
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            flashRule = "STM32 GetID (0x02) -> map PID to Flash Size register/table; Memory Read/Write 256B chunks"
            oscRule = "STM32 internal HSI 8MHz auto-detected by 0x7F sync byte"
            baudRule = "Auto-baud locked on 0x7F at ${sync.baudRate} bps"
            flashEndHex = "0x0801FFFF (128 KB Typical)"
            pageSize = 256
            blockSize = 2048
            supportedStxPages.add(256)
        }

        val avgDelay = ((rttSamples.average() + 12.0).toInt()).coerceIn(15, 250)
        val interByteMs = ((11_000.0 / sync.baudRate.coerceAtLeast(300)).toInt()).coerceIn(1, 5)
        val finalTiming = base.timingMap.copy(
            resetPulseMs = sync.resetPulseMs,
            postResetDelayMs = sync.postResetDelayMs,
            syncByteGapMs = sync.syncByteGapMs,
            interByteDelayMs = interByteMs,
            commandToDataDelayMs = measuredCmdToDataMs,
            frameToFrameDelayMs = measuredFrameToFrameMs,
            baudSwitchDelayMs = measuredBaudSwitchMs
        )

        return base.copy(
            maxBaudTested = maxBaud,
            supportedBaudRates = supportedBauds.distinct().sorted(),
            flashSizeDetectionRule = flashRule,
            oscillatorNegotiationRule = oscRule,
            oscillatorProbeResults = oscProbeStr,
            baudStateMachineRule = baudRule,
            supportedStxPageSizes = supportedStxPages.distinct().sorted(),
            pageSizeBytes = pageSize,
            blockSizeBytes = blockSize,
            flashEndAddressHex = flashEndHex,
            safeDelayMs = avgDelay,
            timingMap = finalTiming
        )
    }

    // ==================== STAGE 5: SEQUENTIAL INTERACTIVE MODAL DIALOG PROBES ====================

    private suspend fun requestStepConfirmation(prompt: Stage5ConfirmPrompt): Boolean {
        currentCoroutineContext().ensureActive()
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmDeferred = deferred
        _state.value = _state.value.copy(
            phase = ProberStagePhase.AWAITING_STEP_CONFIRMATION,
            pendingConfirmDialog = prompt,
            stageBanner = "⚠️ بانتظار موافقة المشرف (${prompt.stepIndex}/${prompt.totalSteps}): ${prompt.stepTitle}"
        )
        updateStep(
            "stage5_safety_gate",
            ChecklistStepStatus.AWAITING_SAFETY,
            "بانتظار موافقة المشرف على: ${prompt.stepTitle}"
        )
        return try {
            deferred.await()
        } finally {
            pendingConfirmDeferred = null
            _state.value = _state.value.copy(pendingConfirmDialog = null)
        }
    }

    private suspend fun runStage5InteractiveDestructiveProbes(
        sync: SyncLock,
        initialSummary: ProberBlueprintSummary
    ) {
        var workingSummary = initialSummary
        val results = mutableListOf<Stage5StepResult>()
        val notesBuilder = StringBuilder()

        fun finalizeAndComplete(bannerMsg: String, stepBadge: ChecklistStepStatus, stepDetail: String) {
            workingSummary = workingSummary.copy(
                stage5Results = results.toList(),
                advancedSchoolsNotes = notesBuilder.toString().trim().ifBlank { stepDetail }
            )
            updateStep("stage5_safety_gate", stepBadge, stepDetail)
            updateSummaryAndBlueprint(workingSummary)
            _state.value = _state.value.copy(
                phase = ProberStagePhase.COMPLETED,
                stageBanner = bannerMsg,
                pendingConfirmDialog = null
            )
        }

        // STEP 5.1: Block-0 Zeroing & Checksum Diff (0x22 + 0xB0)
        val ok1 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 1,
                totalSteps = 3,
                stepTitle = "تصفير Block 0 وحساب فرق البصمة (0x22 + 0xB0)",
                impactDescription = "سيتم قياس بصمة Block 0 (0x000000..0x0003FF) عبر الأمر 0xB0، ثم إرسال أمر مسح البلوك 0x22 وقياس البصمة والزمن الفعلي بعد المسح.\n\n⚠️ هذا الإجراء يمسح أول 1KB من ذاكرة الفلاش على الكارتة المتصلة مباشرة."
            )
        )
        if (!ok1) {
            results.add(Stage5StepResult("5.1 Block-0 Zeroing (0x22 + 0xB0)", passed = false, executed = false, durationMs = 0, detail = "أوقف المشرف الاختبار قبل التنفيذ"))
            notesBuilder.appendLine("توقف المشرف بأمان قبل الخطوة 5.1 — تم حفظ كامل تقرير المراحل 1..4.")
            finalizeAndComplete(
                bannerMsg = "تم التوقف بأمان عند بوابة المرحلة 5 وحفظ كامل التقرير الديناميكي",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "تم التوقف بأمان والاكتفاء بنتائج المراحل 1..4"
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.1: تصفير Block 0 وحساب فرق البصمة..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.1: قراءة البصمة 0xB0 ثم مسح Block 0...")
        resyncLight(sync)

        val step1Result = executeStage5Step1(sync)
        results.add(step1Result)
        notesBuilder.appendLine(step1Result.detail)
        appendLog(if (step1Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step1Result.detail)
        workingSummary = workingSummary.copy(
            stage5Results = results.toList(),
            advancedSchoolsNotes = notesBuilder.toString().trim()
        )
        updateSummaryAndBlueprint(workingSummary)

        // STEP 5.2: Partial Bit Overwrite (0x40 + STX 0xFE)
        val ok2 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 2,
                totalSteps = 3,
                stepTitle = "الكتابة الجزئية / تحويل البتات (0x40 + STX 0xFE)",
                impactDescription = "سيتم اختبار أمر البرمجة 0x40 مع إرسال إطار STX (0x02) بقيمة 0xFE لاختبار الكتابة الجزئية وقياس زمن استجابة الفلاش.\n\n⚠️ هذا الإجراء يعدّل بيانات الفلاش في العنوان المستهدف."
            )
        )
        if (!ok2) {
            results.add(Stage5StepResult("5.2 Partial Bit Overwrite (0x40 STX 0xFE)", passed = false, executed = false, durationMs = 0, detail = "توقف المشرف بعد الخطوة 5.1"))
            notesBuilder.appendLine("توقف المشرف بأمان قبل الخطوة 5.2.")
            finalizeAndComplete(
                bannerMsg = "تم إكمال الخطوة 5.1 والتوقف بأمان قبل 5.2 مع حفظ التقرير الكامل",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "اكتملت 5.1 (${step1Result.durationMs}ms) وتوقف المشرف قبل 5.2"
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.2: اختبار الكتابة الجزئية 0x40 STX..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.2: الكتابة الجزئية 0x40 + STX 0xFE...")
        resyncLight(sync)

        val step2Result = executeStage5Step2(sync)
        results.add(step2Result)
        notesBuilder.appendLine(step2Result.detail)
        appendLog(if (step2Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step2Result.detail)
        workingSummary = workingSummary.copy(
            stage5Results = results.toList(),
            advancedSchoolsNotes = notesBuilder.toString().trim()
        )
        updateSummaryAndBlueprint(workingSummary)

        // STEP 5.3: Full Chip Erase (0x20) / Security Bypass
        val ok3 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 3,
                totalSteps = 3,
                stepTitle = "المسح الكلي للرقاقة وفك قفل الحماية (0x20 Chip Erase / Security Bypass)",
                impactDescription = "⚠️ تحذير شديد: سيتم إرسال أمر المسح الكلي (0x20 Chip Erase أو 0x92/0xF5 حسب العائلة) وقياس الزمن الفعلي للمسح الكامل.\n\nسيؤدي هذا إلى مسح السوفت وير بالكامل على الكارتة المتصلة!"
            )
        )
        if (!ok3) {
            results.add(Stage5StepResult("5.3 Full Chip Erase 0x20 / Security Bypass", passed = false, executed = false, durationMs = 0, detail = "توقف المشرف بعد الخطوة 5.2"))
            notesBuilder.appendLine("توقف المشرف بأمان قبل الخطوة 5.3 (تم حماية باقي الفلاش من المسح الكلي).")
            finalizeAndComplete(
                bannerMsg = "تم إكمال 5.1 و 5.2 والتوقف بأمان قبل المسح الكلي 5.3",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "اكتملت 5.1 و 5.2 وتوقف المشرف بأمان قبل المسح الكلي 0x20"
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.3: المسح الكلي للرقاقة 0x20 وفك القفل..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.3: المسح الكلي 0x20 وقياس eraseWriteBusyWaitMs...")
        resyncLight(sync)

        val step3Result = executeStage5Step3(sync)
        results.add(step3Result)
        notesBuilder.appendLine(step3Result.detail)
        appendLog(if (step3Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step3Result.detail)

        if (step3Result.passed && step3Result.durationMs > 0) {
            workingSummary = workingSummary.copy(
                timingMap = workingSummary.timingMap.copy(
                    eraseWriteBusyWaitMs = step3Result.durationMs.toInt().coerceAtLeast(30)
                )
            )
        }

        val passedCount = results.count { it.passed }
        finalizeAndComplete(
            bannerMsg = "اكتملت جميع المراحل (1 إلى 5) واستخراج البروتوكول الديناميكي الكامل!",
            stepBadge = if (passedCount > 0) ChecklistStepStatus.PASSED else ChecklistStepStatus.FAILED,
            stepDetail = "تم تنفيذ 3 خطوات تفاعلية بالمرحلة 5 (نجح $passedCount/3) | EraseWait=${workingSummary.timingMap.eraseWriteBusyWaitMs}ms"
        )
    }

    private suspend fun executeStage5Step1(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val b0Range = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())
            val csBeforeRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS Before [0xB0]", stopOnCompleteNecFrame = true)
            val csBefore = parseChecksum16(csBeforeRx) ?: run {
                if (parseNecStatus(csBeforeRx) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 300, true)) else null
            }

            val eraseRx = transport.transceive(buildNecCommandFrame(0x22, b0Range), 12, 1300, "5.1 Block-0 Erase [0x22]", stopOnCompleteNecFrame = true)
            val eraseSts = parseNecStatus(eraseRx)
            var csAfter: Int? = null
            if (eraseSts == ACK_NEC) {
                delay(20)
                val csAfterRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS After [0xB0]", stopOnCompleteNecFrame = true)
                csAfter = parseChecksum16(csAfterRx) ?: run {
                    if (parseNecStatus(csAfterRx) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 300, true)) else null
                }
            }
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val passed = (eraseSts == ACK_NEC) && (csBefore != null || csAfter != null)
            val detail = if (passed) {
                "✅ 5.1 تصفير Block-0 (0x22+0xB0): نجح في ${dt}ms (CS قبل=${csBefore?.let { "0x%04X".format(it) } ?: "--"} -> بعد=${csAfter?.let { "0x%04X".format(it) } ?: "--"})"
            } else {
                "❌ 5.1 تصفير Block-0 (0x22+0xB0): فشل (${dt}ms, 0x22=${formatByte(eraseSts)})"
            }
            return Stage5StepResult("5.1 Block-0 Zeroing (0x22 + 0xB0)", passed, true, dt, detail)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val e0 = transport.transceive(byteArrayOf(0x43, 0xBC.toByte()), 6, 450, "5.1 STM32 Erase [0x43]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = e0.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.1 مسح STM32 Sector-0: نجح (${dt}ms)" else "❌ 5.1 مسح STM32 Sector-0: فشل (RDP محمي)"
            return Stage5StepResult("5.1 Sector-0 Erase (0x43)", ok, true, dt, detail)
        } else {
            val b0Erase = transport.transceive(byteArrayOf(0x20, 0x00, 0x00, 0xD0.toByte()), 8, 550, "5.1 R8C Block Erase [0x20 D0]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = b0Erase.isNotEmpty()
            val detail = if (ok) "✅ 5.1 مسح R8C Block-0: نجح في ${dt}ms (${b0Erase.toHexStr()})" else "❌ 5.1 مسح R8C Block-0: فشل"
            return Stage5StepResult("5.1 R8C Block Erase", ok, true, dt, detail)
        }
    }

    private suspend fun executeStage5Step2(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val b0Range = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())
            val partialRange = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x03)
            var partHdrRx = transport.transceive(buildNecCommandFrame(0x40, partialRange), 12, 420, "5.2 Partial Write 4B [0x40]", stopOnCompleteNecFrame = true)
            var partRangeUsed = "4B (0x0000..0x0003)"
            var partSts = parseNecStatus(partHdrRx)
            var partFrameSize = 4
            if (partSts != ACK_NEC) {
                partHdrRx = transport.transceive(buildNecCommandFrame(0x40, b0Range), 12, 420, "5.2 Partial Write Block-0 [0x40]", stopOnCompleteNecFrame = true)
                partRangeUsed = "1KB Block-0 (0x0000..0x03FF)"
                partSts = parseNecStatus(partHdrRx)
                partFrameSize = 256
            }
            var dataOverwriteAck = false
            if (partSts == ACK_NEC) {
                val testSlice = ByteArray(partFrameSize) { 0xFF.toByte() }.also { it[0] = 0xFE.toByte() }
                val stxFrame = buildNecDataFrame(testSlice, isLast = true)
                val dataRx = transport.transceive(
                    stxFrame,
                    12, 700, "5.2 STX Bit-Clear 0xFE [0x40]",
                    stopOnCompleteNecFrame = true,
                    paced64B = stxFrame.size > 64
                )
                val dSts1 = parseNecStatus(dataRx)
                val dSts2 = parseNecSecondStatus(dataRx)
                dataOverwriteAck = (dSts1 == ACK_NEC && (dSts2 == null || dSts2 == ACK_NEC))
            }
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val passed = (partSts == ACK_NEC && dataOverwriteAck)
            val detail = when {
                passed -> "✅ 5.2 الكتابة الجزئية (0x40 + STX 0xFE): نجحت في ${dt}ms ($partRangeUsed)"
                partSts == ACK_NEC -> "❌ 5.2 الكتابة الجزئية: قبل 0x40 لكن رُفض إطار STX ($partRangeUsed, ${dt}ms)"
                else -> "❌ 5.2 الكتابة الجزئية: فشلت (0x40=${formatByte(partSts)}, ${dt}ms)"
            }
            return Stage5StepResult("5.2 Partial Bit Overwrite (0x40 STX 0xFE)", passed, true, dt, detail)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val wr = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 6, 400, "5.2 STM32 Write [0x31]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wr.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.2 STM32 Write 0x31: ACK في ${dt}ms" else "❌ 5.2 STM32 Write 0x31: مرفوض"
            return Stage5StepResult("5.2 STM32 Write 0x31", ok, true, dt, detail)
        } else {
            val wrHdr = transport.transceive(byteArrayOf(0x41, 0x00, 0x40, 0x00), 8, 350, "5.2 R8C Page Write [0x41]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wrHdr.isNotEmpty()
            val detail = if (ok) "✅ 5.2 R8C Page Write 0x41: نجح في ${dt}ms" else "❌ 5.2 R8C Page Write 0x41: فشل"
            return Stage5StepResult("5.2 R8C Page Write 0x41", ok, true, dt, detail)
        }
    }

    private suspend fun executeStage5Step3(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val c5Rx = transport.transceive(buildNecCommandFrame(0xC5), 16, 300, "5.3 Security Query [0xC5]", stopOnCompleteNecFrame = true)
            val c5Ok = parseNecStatus(c5Rx) == ACK_NEC
            val tErase = System.currentTimeMillis()
            val chipEraseRx = transport.transceive(buildNecCommandFrame(0x20), 12, 2000, "5.3 Full Chip Erase [0x20]", stopOnCompleteNecFrame = true)
            val eraseDt = (System.currentTimeMillis() - tErase).coerceAtLeast(1L)
            val ceSts = parseNecStatus(chipEraseRx)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val passed = (ceSts == ACK_NEC)
            val detail = if (passed) {
                "✅ 5.3 المسح الكلي 0x20 وفك الحماية: نجح في ${eraseDt}ms (0x20=ACK 0x06${if (c5Ok) ", 0xC5=ACK" else ""})"
            } else {
                "❌ 5.3 المسح الكلي 0x20: فشل (0x20=${formatByte(ceSts)}${if (c5Ok) ", 0xC5=ACK" else ""}, ${dt}ms)"
            }
            return Stage5StepResult("5.3 Full Chip Erase 0x20 / Security Bypass", passed, true, eraseDt, detail)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val rx = transport.transceive(byteArrayOf(0x92.toByte(), 0x6D), 6, 1500, "5.3 STM32 Unprotect [0x92]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = rx.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.3 STM32 Readout Unprotect 0x92: نجح في ${dt}ms" else "❌ 5.3 STM32 Unprotect 0x92: فشل"
            return Stage5StepResult("5.3 STM32 Readout Unprotect 0x92", ok, true, dt, detail)
        } else {
            val ffId = byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xDF.toByte(), 0x00, 0x07, -1, -1, -1, -1, -1, -1, -1)
            transport.transceive(ffId, 8, 320, "5.3 R8C ID Probe [0xF5]")
            val sr = transport.transceive(byteArrayOf(0x70), 6, 300, "5.3 R8C SRD [0x70]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = sr.size >= 2 && ((sr[1].toInt() and 0x0C) != 0)
            val detail = if (ok) "✅ 5.3 R8C ID Bypass: نجح في ${dt}ms (SRD=${sr.toHexStr()})" else "❌ 5.3 R8C ID Bypass: مقفول بالـ ID"
            return Stage5StepResult("5.3 R8C ID Bypass", ok, true, dt, detail)
        }
    }

    private suspend fun resyncHandshakeQuiet(sync: SyncLock) {
        if (sync.framing != DetectedFraming.NEC_STX_ETX) return
        transport.writeRawBytes(byteArrayOf(0x00))
        delay(sync.syncByteGapMs.toLong())
        transport.writeRawBytes(byteArrayOf(0x00))
        delay(50)
        transport.drainAvailableRaw(20)
        transport.flushRx()
        transport.transceive(
            buildNecCommandFrame(0x00),
            12, 350, "Quiet Resync [0x00]", stopOnCompleteNecFrame = true, logTxRx = false
        )
    }

    // ==================== HELPERS ====================

    private fun updateStep(id: String, status: ChecklistStepStatus, detail: String) {
        _state.value = _state.value.copy(
            checklist = _state.value.checklist.map {
                if (it.id == id) it.copy(status = status, detail = detail) else it
            }
        )
    }

    private fun updateAndReturn(id: String, status: ChecklistStepStatus, detail: String): ChecklistStepItem {
        updateStep(id, status, detail)
        return _state.value.checklist.first { it.id == id }
    }

    private fun updateSummaryAndBlueprint(summary: ProberBlueprintSummary) {
        val next = _state.value.copy(blueprintSummary = summary)
        _state.value = next.copy(blueprintText = formatAiReadyBlueprintJson(next))
    }

    private fun failPipeline(reason: String, allowBypass: Boolean = false) {
        appendLog(ProberLogDirection.ERROR, reason)
        _state.value = _state.value.copy(
            phase = ProberStagePhase.ERROR,
            stageBanner = reason,
            pendingConfirmDialog = null,
            canBypassSync = allowBypass
        )
    }

    private fun classifyFraming(reply: ByteArray, pulse: ByteArray): DetectedFraming {
        if (reply.any { (it.toInt() and 0xFF) == STX } && reply.any { (it.toInt() and 0xFF) == ETX }) {
            return DetectedFraming.NEC_STX_ETX
        }
        val first = reply[0].toInt() and 0xFF
        val p0 = pulse.firstOrNull()?.let { it.toInt() and 0xFF } ?: -1
        if (p0 == 0x7F && (first == ACK_STM32 || first == NACK_STM32)) return DetectedFraming.STM32_ACK
        if (first == ACK_NEC || first == 0x80 || first == 0xB0 || first == 0xC1 || first == 0xF0) return DetectedFraming.RENESAS_RAW
        return DetectedFraming.CUSTOM_RAW
    }

    private fun extract16Bytes(rx: ByteArray, framing: DetectedFraming): ByteArray? {
        if (rx.isEmpty()) return null
        if (framing == DetectedFraming.NEC_STX_ETX) {
            val sts = parseNecStatus(rx)
            if (sts != null && sts in HARD_REJECT_STATUS) return null
            val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
            if (stxIdx >= 0 && rx.size >= stxIdx + 20) {
                val len = rx[stxIdx + 1].toInt() and 0xFF
                if (len >= 16 && rx.size >= stxIdx + 2 + len + 2) {
                    return rx.copyOfRange(stxIdx + 2, stxIdx + 18)
                }
            }
            return null
        }
        return if (rx.size >= 16) rx.copyOfRange(0, 16) else null
    }

    private fun extractNecSecondFramePayload(rx: ByteArray): ByteArray? {
        val firstStx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (firstStx < 0) return null
        val len1 = rx.getOrNull(firstStx + 1)?.toInt()?.and(0xFF) ?: return null
        val afterFirst = firstStx + 2 + len1 + 2
        if (rx.size <= afterFirst) return null
        val secondStxRel = rx.copyOfRange(afterFirst, rx.size).indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (secondStxRel < 0) return null
        val sIdx = afterFirst + secondStxRel
        val len2 = rx.getOrNull(sIdx + 1)?.toInt()?.and(0xFF) ?: return null
        val dataLen = if (len2 == 0) 256 else len2
        if (rx.size < sIdx + 2 + dataLen) return null
        return rx.copyOfRange(sIdx + 2, sIdx + 2 + dataLen)
    }

    private fun parseNecSignatureEndAddress(payload: ByteArray): Int? {
        // In NEC 78K0 0xC0 signature payload, bytes [3..5] or [13..15] often encode 24-bit flash end address (0x007FFF, 0x00FFFF, etc.)
        if (payload.size < 6) return null
        for (offset in 0..(payload.size - 3)) {
            val b0 = payload[offset].toInt() and 0xFF
            val b1 = payload[offset + 1].toInt() and 0xFF
            val b2 = payload[offset + 2].toInt() and 0xFF
            val leAddr = (b2 shl 16) or (b1 shl 8) or b0
            val beAddr = (b0 shl 16) or (b1 shl 8) or b2
            val validEnds = setOf(0x001FFF, 0x003FFF, 0x007FFF, 0x00FFFF, 0x01FFFF, 0x03FFFF)
            if (leAddr in validEnds) return leAddr
            if (beAddr in validEnds) return beAddr
        }
        return null
    }

    private fun parseChecksum16(rx: ByteArray): Int? {
        val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (stxIdx < 0 || rx.size < stxIdx + 6) return null
        val len = rx[stxIdx + 1].toInt() and 0xFF
        if (len != 2) return null
        val b0 = rx[stxIdx + 2].toInt() and 0xFF
        val b1 = rx[stxIdx + 3].toInt() and 0xFF
        if (b0 == ACK_NEC && b1 in HARD_REJECT_STATUS) return null
        return (b1 shl 8) or b0
    }

    private fun parseNecStatus(rx: ByteArray): Int? {
        val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (stxIdx < 0 || rx.size < stxIdx + 5) return null
        val len = rx[stxIdx + 1].toInt() and 0xFF
        if (len <= 0 || rx.size < stxIdx + 3) return null
        return rx[stxIdx + 2].toInt() and 0xFF
    }

    private fun parseNecSecondStatus(rx: ByteArray): Int? {
        val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (stxIdx < 0 || rx.size < stxIdx + 6) return null
        val len = rx[stxIdx + 1].toInt() and 0xFF
        if (len < 2 || rx.size < stxIdx + 4) return null
        return rx[stxIdx + 3].toInt() and 0xFF
    }

    private fun effectiveNecStatus(rx: ByteArray): Int? {
        val s1 = parseNecStatus(rx)
        val s2 = parseNecSecondStatus(rx)
        return if (s1 == ACK_NEC && s2 != null && s2 != ACK_NEC) s2 else s1
    }

    private fun buildNecCommandFrame(cmd: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val body = ByteArray(1 + data.size)
        body[0] = (cmd and 0xFF).toByte()
        if (data.isNotEmpty()) System.arraycopy(data, 0, body, 1, data.size)
        val len = body.size and 0xFF
        var sum = len
        for (b in body) sum += (b.toInt() and 0xFF)
        val cs = (0 - sum) and 0xFF
        return byteArrayOf(SOH.toByte(), len.toByte()) + body + byteArrayOf(cs.toByte(), ETX.toByte())
    }

    private fun buildNecDataFrame(data: ByteArray, isLast: Boolean): ByteArray {
        val lenByte = data.size and 0xFF
        var sum = lenByte
        for (b in data) sum += (b.toInt() and 0xFF)
        val cs = (0 - sum) and 0xFF
        val endByte = if (isLast) ETX else ETB
        return byteArrayOf(STX.toByte(), lenByte.toByte()) + data + byteArrayOf(cs.toByte(), endByte.toByte())
    }

    private fun addrRangeBytes(start: Int, end: Int): ByteArray = byteArrayOf(
        ((start ushr 16) and 0xFF).toByte(),
        ((start ushr 8) and 0xFF).toByte(),
        (start and 0xFF).toByte(),
        ((end ushr 16) and 0xFF).toByte(),
        ((end ushr 8) and 0xFF).toByte(),
        (end and 0xFF).toByte()
    )

    private fun guessCommandRole(cmd: Int): String = when (cmd) {
        0x00 -> "Reset / Get"
        0x01 -> "Get Version / Read Protect"
        0x02 -> "Get Chip ID"
        0x11 -> "Read Memory"
        0x13 -> "Verify / Compare"
        0x18 -> "Read Memory (78K0)"
        0x20 -> "Chip Erase"
        0x21 -> "Go / Execute"
        0x22 -> "Block Erase"
        0x30, 0x32 -> "Blank Check"
        0x31 -> "Write Memory"
        0x40 -> "Program / Write Flash"
        0x41 -> "Page Program"
        0x43 -> "Erase Memory"
        0x44 -> "Extended Erase"
        0x82 -> "Write Unprotect"
        0x90 -> "Oscillation Frequency Set"
        0x92 -> "Readout Unprotect"
        0x9A -> "Baud Rate Set"
        0xA0 -> "Security Flag Set"
        0xB0 -> "Checksum"
        0xC0 -> "Silicon Signature"
        0xC5 -> "Version / Status"
        else -> "Custom / Vendor Cmd"
    }

    private fun guessStm32ParamLen(cmd: Int): Int? = when (cmd) {
        0x00, 0x01, 0x02 -> 0
        0x11, 0x31 -> 5
        0x43, 0x44 -> 2
        else -> null
    }

    private fun appendLog(dir: ProberLogDirection, msg: String) {
        val entry = ProberLogEntry(++logSeq, timeFormat.format(Date()), dir, msg)
        val updated = (_state.value.logs + entry).takeLast(350)
        _state.value = _state.value.copy(logs = updated)
    }

    private fun ByteArray.toHexStr(): String = joinToString(" ") { "%02X".format(it) }

    private fun formatByte(b: Int?): String = if (b == null) "بدون رد" else "0x%02X".format(b and 0xFF)
}
