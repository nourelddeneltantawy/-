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

        private val UNIVERSAL_ID_CMDS = listOf(0xC0, 0xC5, 0xFB, 0x01, 0x02, 0x00, 0x9F, 0xAF)
    }

    private data class SyncLock(
        val baudRate: Int,
        val evenParity: Boolean,
        val parityMode: String,
        val wakeUpPatternUsed: String,
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

    private data class UniversalIdCapture(
        val autoIdRule: String,
        val manufacturerGuess: String,
        val inferredModel: String,
        val rawSignatureSummary: String,
        val rawC0Payload: ByteArray
    )

    private data class SignatureProbeCapture(
        val step: ChecklistStepItem,
        val rawC0Payload: ByteArray,
        val manufacturerGuess: String,
        val inferredModel: String
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
    private var activeWinningOscPayload: ByteArray? = null
    @Volatile
    private var pendingConfirmDeferred: CompletableDeferred<Boolean>? = null
    @Volatile
    private var detectedUsbdmCaps: String = ""
    @Volatile
    private var detectedUsbdmSpeedHz: Int = 0
    @Volatile
    private var detectedUsbdmSyncBytes: String = ""
    @Volatile
    private var detectedSdidHex: String = ""
    @Volatile
    private var detectedFreescaleModel: String = ""
    @Volatile
    private var detectedSamsungIdHex: String = ""

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

    fun confirmPendingStage5Step() {
        val def = pendingConfirmDeferred
        _state.value = _state.value.copy(pendingConfirmDialog = null)
        def?.complete(true)
    }

    fun cancelPendingStage5AndFinish() {
        val def = pendingConfirmDeferred
        _state.value = _state.value.copy(pendingConfirmDialog = null)
        def?.complete(false)
    }

    fun startFullSafeProtocolScan() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        lastSyncLock = null
        activeWinningOscPayload = null

        activeJob = scope.launch {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "جاري تنفيذ المرحلة 1: التزامن الأعمى عبر 8N1/8E1 والأنماط الخمسة...",
                checklist = defaultChecklist(),
                blueprintSummary = ProberBlueprintSummary(),
                readBlueprintText = "",
                writeBlueprintText = "",
                humanGuideText = "",
                humanGuideJsonText = "",
                blueprintText = "",
                pendingConfirmDialog = null,
                canBypassSync = false
            )
            appendLog(ProberLogDirection.INFO, "══ بدء الفحص العالمي الشامل لأي معالج (${family.labelAr}) ══")

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG. تأكد من توصيل المبرمجة ومنح الصلاحية.", allowBypass = false)
                    return@launch
                }

                // STAGE 1: Universal Blind Sync + Auto-ID
                updateStep("stage1_handshake", ChecklistStepStatus.RUNNING, "اختبار 8N1 و 8E1 (4800..115200) + 5 أنماط استيقاظ عالمية...")
                val sync = runStage1UniversalBlindHandshake(family)
                if (sync == null) {
                    updateStep("stage1_handshake", ChecklistStepStatus.FAILED, "لم يرد المعالج على أي نمط عالمي — يمكنك الضغط على «تجاوز التزامن»")
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
                    "${sync.baudRate} ${sync.parityMode} | ${sync.wakeUpPatternUsed} | ${sync.framing.label}"
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

    fun bypassSyncAndRunStages2To4() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        lastSyncLock = null
        activeWinningOscPayload = null

        activeJob = scope.launch {
            val usbChip = ProberUsbChip.fromDevice(0, 0) // resolved after openSession
            val defaultBaud = when (family) {
                ProberTargetFamily.STM32_UART -> 115200
                ProberTargetFamily.FREESCALE_HCS08 -> 1_500_000
                else -> 9600
            }
            val defaultParityEven = (family == ProberTargetFamily.STM32_UART)
            val preFraming = when (family) {
                ProberTargetFamily.STM32_UART -> DetectedFraming.STM32_ACK
                ProberTargetFamily.RENESAS_R8C_RL78 -> DetectedFraming.RENESAS_RAW
                ProberTargetFamily.FREESCALE_HCS08 -> DetectedFraming.FREESCALE_SINGLE_WIRE_BDM
                ProberTargetFamily.SAMSUNG_S3F -> DetectedFraming.SAMSUNG_ISP
                else -> DetectedFraming.NEC_STX_ETX
            }

            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "تجاوز المرحلة 1 — اختبار الأوامر مباشرة ($defaultBaud ${if (defaultParityEven) "8E1" else "8N1"})...",
                checklist = defaultChecklist(),
                blueprintSummary = ProberBlueprintSummary(),
                readBlueprintText = "",
                writeBlueprintText = "",
                humanGuideText = "",
                humanGuideJsonText = "",
                blueprintText = "",
                pendingConfirmDialog = null,
                canBypassSync = false
            )

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG لتجاوز التزامن.", allowBypass = true)
                    return@launch
                }
                val isUsbdm = transport.isUsbdmSession()
                val defaultFraming = if (isUsbdm) DetectedFraming.USBDM_BDM else preFraming
                val effectiveBaud = if (isUsbdm) 0 else defaultBaud
                appendLog(
                    ProberLogDirection.WARN,
                    "⚠️ تم تجاوز التزامن يدوياً — فتح المنفذ (${defaultFraming.label})..."
                )
                if (!isUsbdm) {
                    transport.configureUart(defaultBaud, evenParity = defaultParityEven)
                    transport.setControlLines(dtr = false, rts = false)
                    delay(40)
                    transport.flushRx()
                } else {
                    transport.usbdmTransceive(byteArrayOf(0x03, 0x06, 0x02), 8, 250, "USBDM Bypass SET_VDD 5V [03 06 02]")
                    delay(100)
                    transport.usbdmTransceive(byteArrayOf(0x03, 0x02, 0x00), 8, 250, "USBDM Bypass SET_TARGET HCS08 [03 02 00]")
                    transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 8, 250, "USBDM Bypass CONNECT [02 0F]")
                    transport.usbdmTransceive(byteArrayOf(0x02, 0x10), 8, 250, "USBDM Bypass SYNC [02 10]")
                    transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM Bypass HALT [02 14]")
                }

                val bypassSync = SyncLock(
                    baudRate = effectiveBaud.coerceAtLeast(if (isUsbdm) 1 else 9600),
                    evenParity = defaultParityEven,
                    parityMode = if (isUsbdm) "USB Bulk (EP1 OUT / EP2 IN)" else if (defaultParityEven) "8E1" else "8N1",
                    wakeUpPatternUsed = "Bypassed (Direct Mode)",
                    flmd0PinState = if (isUsbdm) "USBDM VDD=+5V / BKGD Direct" else "HIGH (+5V) / Bypassed (DTR=false)",
                    resetPinIdleState = if (isUsbdm) "USBDM Controlled" else "HIGH (+5V) / Static (RTS=false)",
                    resetPinPulsePolarity = "Bypassed (No Reset Toggle)",
                    dtrPolarity = if (isUsbdm) "USBDM Bulk Direct" else "تجاوز يدوي (DTR=false -> FLMD0=+5V)",
                    rtsPolarity = if (isUsbdm) "USBDM RESET" else "Static (RTS=false)",
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
                    "تم التجاوز يدوياً • $defaultBaud ${bypassSync.parityMode} • ${defaultFraming.label}"
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
        // Universal Auto-ID Capture right after Stage 1 Handshake
        val autoId = extractUniversalAutoId(sync)
        var summary = ProberBlueprintSummary(
            baudRate = sync.baudRate,
            parityMode = sync.parityMode,
            maxBaudTested = sync.baudRate,
            supportedBaudRates = listOf(sync.baudRate),
            wakeUpPatternUsed = sync.wakeUpPatternUsed,
            flmd0PinState = sync.flmd0PinState,
            resetPinIdleState = sync.resetPinIdleState,
            resetPinPulsePolarity = sync.resetPinPulsePolarity,
            dtrPolarity = sync.dtrPolarity,
            rtsPolarity = sync.rtsPolarity,
            echoCancellation = transport.echoLoopbackObserved,
            syncPulseHex = sync.syncPulseHex,
            framing = sync.framing,
            universalAutoIdRule = autoId.autoIdRule,
            manufacturerGuess = autoId.manufacturerGuess,
            inferredModel = autoId.inferredModel,
            chipSignature = autoId.rawSignatureSummary,
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
        val sigCapture = testSignatureRegisterRead(sync, autoId)
        val r4 = sigCapture.step
        resyncLight(sync)
        val r5 = testRamStubInjection(sync)

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

        val combinedSig = r4.takeIf { it.status == ChecklistStepStatus.PASSED }?.detail?.takeIf { it.isNotBlank() }
            ?: autoId.rawSignatureSummary
        summary = summary.copy(
            verify0x13DiffResult = verifyDiffStr,
            winningReadMethod = winningReadDesc,
            chipSignature = combinedSig,
            manufacturerGuess = sigCapture.manufacturerGuess.ifBlank { summary.manufacturerGuess },
            inferredModel = sigCapture.inferredModel.ifBlank { summary.inferredModel }
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 3: Full 0x00..0xFF Command & LEN 0..8 Surgical Sweep
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

        // STAGE 4: Crystal Matrix (0x90), Flash Bounds (8KB..512KB), Baud State Machine, STX, 6-Point Timing
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 4: مصفوفة الكريستالات 0x90، حدود الفلاش، والسرعات...")
        updateStep("stage4_timing_bounds", ChecklistStepStatus.RUNNING, "اختبار مصفوفة الكريستالات 0x90 وحدود 8KB..512KB وتبديل السرعات...")
        resyncLight(sync)
        val c0Bytes = if (sigCapture.rawC0Payload.isNotEmpty()) sigCapture.rawC0Payload else autoId.rawC0Payload
        val (stage4Summary, winningOscCmdPayload) = runStage4DynamicFamilyAlgorithms(sync, summary, c0Bytes)
        summary = stage4Summary
        activeWinningOscPayload = winningOscCmdPayload
        updateStep(
            "stage4_timing_bounds",
            ChecklistStepStatus.PASSED,
            "End=${summary.flashEndAddressHex} | Osc=${summary.winningOscillatorPayload} | Bauds=${summary.supportedBaudRates} | STX=${summary.supportedStxPageSizes}"
        )
        updateSummaryAndBlueprint(summary)

        // Lock winning oscillator frequency into the session before Stage 5!
        if (winningOscCmdPayload != null && sync.framing == DetectedFraming.NEC_STX_ETX) {
            ensureBootloaderReady(sync, "تثبيت التردد الفائز 0x90 قبل المرحلة 5", relockOsc = false)
            var lockRx = transport.transceive(
                buildNecCommandFrame(0x90, winningOscCmdPayload),
                12, 220, "Lock Crystal Matrix [0x90 -> ${winningOscCmdPayload.toHexStr()}]", stopOnCompleteNecFrame = true
            )
            if (lockRx.isEmpty()) {
                hardwareResetAndResync(sync, "إعادة تزامن لتثبيت 0x90", relockOsc = false)
                lockRx = transport.transceive(
                    buildNecCommandFrame(0x90, winningOscCmdPayload),
                    12, 220, "Lock Crystal Matrix Retry [0x90 -> ${winningOscCmdPayload.toHexStr()}]", stopOnCompleteNecFrame = true
                )
            }
        }

        // STAGE 5: Sequential Modal Warning Dialog (No Hot-Swap!)
        if (isMcuOpenForSafeRead) {
            updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, "المعالج مفتوح للسحب الآمن — لا حاجة لاختبارات تعديلية خطرة")
            var finalSummary = summary.copy(
                advancedSchoolsNotes = "المعالج مفتوح للسحب الآمن ($winningReadDesc) — اكتملت المراحل 1..4 دون لمس الفلاش."
            )
            updateSummaryAndBlueprint(finalSummary)
            ensureBootloaderReady(sync, "تهيئة المرحلة 6", relockOsc = true)
            finalSummary = runStage6UniversalLiveReadProof(sync, finalSummary, userCancelledStage5 = false)
            updateSummaryAndBlueprint(finalSummary)
            _state.value = _state.value.copy(
                phase = ProberStagePhase.COMPLETED,
                stageBanner = "اكتمل الفحص الشامل والمرحلة 6 وتوليد البطاقات الثلاث بنجاح (المعالج مفتوح للسحب الآمن)"
            )
        } else {
            appendLog(
                ProberLogDirection.WARN,
                "⚠️ المعالج محمي ومقفل (فشلت القراءة المباشرة و1-بايت Checksum وRAM Stub). الانتقال إلى بوابة التحذير المنبثقة بالمرحلة 5..."
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

    // ==================== STAGE 1: UNIVERSAL BLIND SYNC (5 PATTERNS + 8N1/8E1 + 4800..115200) ====================

    private suspend fun tryLiteralNec78k0Handshake(
        baud: Int,
        evenParity: Boolean,
        polDesc: String,
        inverted: Boolean,
        useResetToggle: Boolean
    ): SyncLock? {
        currentCoroutineContext().ensureActive()
        val parityStr = if (evenParity) "8E1" else "8N1"
        transport.configureUart(baud, evenParity = evenParity)
        transport.flushRx()

        val resetPulseMs = 100
        val postResetMs = 150
        val syncGapMs = 10

        val dtrFlmd0High = inverted
        val rtsResetLow = !inverted
        val rtsResetHigh = inverted

        val flmd0StateDesc = if (useResetToggle) "HIGH (+5V) via DTR=$dtrFlmd0High" else "HIGH (+5V) Static via DTR=false"
        val resetIdleDesc = if (useResetToggle) "HIGH (+5V) via RTS=$rtsResetHigh" else "HIGH (+5V) Static via RTS=false"
        val resetPulseDesc = if (useResetToggle) {
            if (inverted) "Active-High Pulse (RTS=$rtsResetLow->RTS=$rtsResetHigh, ${resetPulseMs}ms)"
            else "Active-Low Pulse (0V RTS=$rtsResetLow for ${resetPulseMs}ms -> +5V RTS=$rtsResetHigh)"
        } else "Static (No Toggle)"

        if (useResetToggle) {
            appendLog(
                ProberLogDirection.INFO,
                "تهيئة 78K0/NEC ($baud $parityStr | $polDesc): DTR=$dtrFlmd0High, RTS=$rtsResetLow -> ${resetPulseMs}ms -> RTS=$rtsResetHigh -> ${postResetMs}ms"
            )
            transport.setDtr(dtrFlmd0High)
            transport.setRts(rtsResetLow)
            delay(resetPulseMs.toLong())
            transport.setDtr(dtrFlmd0High)
            transport.setRts(rtsResetHigh)
            delay(postResetMs.toLong())
            transport.flushRx()
        } else {
            transport.setControlLines(dtr = false, rts = false)
            delay(50)
            transport.flushRx()
        }

        // Pattern 1: Literal 78K0 two-pulse sync (0x00 -> 10ms -> 0x00 -> 50ms) + 0x00 Reset Frame
        appendLog(ProberLogDirection.TX, "Pattern 1 (NEC/78K0) Sync Pulse #1 @ $baud $parityStr -> 00")
        if (transport.writeRawBytes(byteArrayOf(0x00)) != null) return null
        delay(syncGapMs.toLong())
        appendLog(ProberLogDirection.TX, "Pattern 1 (NEC/78K0) Sync Pulse #2 @ $baud $parityStr -> 00")
        if (transport.writeRawBytes(byteArrayOf(0x00)) != null) return null
        delay(50)

        val pulseRaw = transport.drainAvailableRaw(25)
        if (pulseRaw.isNotEmpty() && pulseRaw.size == 2 && pulseRaw[0] == 0x00.toByte() && pulseRaw[1] == 0x00.toByte()) {
            transport.echoLoopbackObserved = true
            appendLog(ProberLogDirection.RX, "صدى نبضة المزامنة (Diode Echo): ${pulseRaw.toHexStr()}")
        }
        transport.flushRx()

        val resetCmd = buildNecCommandFrame(0x00) // 01 01 00 FF 03
        appendLog(ProberLogDirection.TX, "NEC Reset 0x00 ($polDesc • 1500ms) -> ${resetCmd.toHexStr()}")
        val tNec = System.currentTimeMillis()
        if (transport.writeRawBytes(resetCmd) != null) return null

        val reply = transport.readNecHandshakeOrRaw(
            timeoutMs = 1500,
            echoCandidate = resetCmd,
            onRawChunk = { chunk -> appendLog(ProberLogDirection.RX, "Live Raw Chunk: ${chunk.toHexStr()}") }
        )
        val dtNec = (System.currentTimeMillis() - tNec).toInt().coerceAtLeast(5)
        if (reply.isNotEmpty() && reply.any { (it.toInt() and 0xFF) == STX }) {
            appendLog(ProberLogDirection.SUCCESS, "✅ نجح النمط 1 (NEC/Renesas Framed @ $baud $parityStr): ${reply.toHexStr()}")
            return SyncLock(
                baudRate = baud,
                evenParity = evenParity,
                parityMode = parityStr,
                wakeUpPatternUsed = "Pattern 1: Framed (NEC/Renesas 78K0/RL78: 0x00+0x00 -> SOH Reset 0x00)",
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

    private suspend fun tryUsbdmHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedUsbdmCaps = ""
        detectedUsbdmSpeedHz = 0
        detectedUsbdmSyncBytes = ""
        detectedSdidHex = ""
        detectedFreescaleModel = ""
        appendLog(ProberLogDirection.INFO, "── تم اكتشاف مبرمجة USBDM (VID:16D0 PID:0567): بدء البروتوكول القياسي (USB Bulk) ──")
        val t0 = System.currentTimeMillis()

        // 1. CMD_USBDM_GET_CAPABILITIES ([0x02, 0x01])
        var caps = transport.usbdmTransceive(byteArrayOf(0x02, 0x01), 32, 400, "USBDM GET_CAPABILITIES [02 01]")
        if (caps.isEmpty()) {
            caps = transport.usbdmControlIn(0x01, 0, 0, 16)
            if (caps.isNotEmpty()) appendLog(ProberLogDirection.RX, "USBDM Control Caps: ${caps.toHexStr()}")
        }
        if (caps.isEmpty()) {
            appendLog(ProberLogDirection.ERROR, "❌ لم تستجب USBDM لأمر GET_CAPABILITIES.")
            return null
        }
        detectedUsbdmCaps = caps.toHexStr()
        appendLog(ProberLogDirection.SUCCESS, "✅ استجابة USBDM Capabilities: $detectedUsbdmCaps")

        // 2. CMD_USBDM_SET_VDD = 0x06 ([0x03, 0x06, 0x02] = 5V, wait for 0x00 + 100ms stabilization)
        val vddRx = transport.usbdmTransceive(byteArrayOf(0x03, 0x06, 0x02), 8, 350, "USBDM SET_VDD 5V [03 06 02]")
        val vddOk = vddRx.isNotEmpty() && (vddRx[0].toInt() and 0xFF) == 0x00
        if (vddOk) {
            appendLog(ProberLogDirection.SUCCESS, "✅ تغذية الكارتة 5V عبر CMD_USBDM_SET_VDD (0x06) -> BDM_RC_OK (0x00)")
        } else {
            appendLog(ProberLogDirection.WARN, "⚠️ رد CMD_USBDM_SET_VDD (0x06): ${vddRx.toHexStr().ifBlank { "Timeout" }}")
        }
        delay(100)

        // 3. CMD_USBDM_SET_TARGET_TYPE = 0x02 ([0x03, 0x02, 0x00] = T_HCS08)
        val tgtRx = transport.usbdmTransceive(byteArrayOf(0x03, 0x02, 0x00), 8, 350, "USBDM SET_TARGET_TYPE HCS08 [03 02 00]")
        val tgtOk = tgtRx.isNotEmpty() && (tgtRx[0].toInt() and 0xFF) == 0x00
        if (tgtOk) {
            appendLog(ProberLogDirection.SUCCESS, "✅ تهيئة معالج HCS08 (BKGD/RESET) عبر CMD_USBDM_SET_TARGET_TYPE (0x02) -> 0x00")
        } else {
            appendLog(ProberLogDirection.WARN, "⚠️ رد CMD_USBDM_SET_TARGET_TYPE (0x02): ${tgtRx.toHexStr().ifBlank { "Timeout" }}")
        }

        // 4. CMD_USBDM_CONNECT = 0x0F then CMD_USBDM_SYNC = 0x10
        val connRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 12, 450, "USBDM CONNECT [02 0F]")
        var syncRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x10), 12, 450, "USBDM SYNC [02 10]")
        val connOk = connRx.isNotEmpty() && (connRx[0].toInt() and 0xFF) == 0x00
        var syncOk = syncRx.isNotEmpty() && (syncRx[0].toInt() and 0xFF) == 0x00
        if (connOk && !syncOk) {
            delay(40)
            syncRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x10), 12, 450, "USBDM SYNC Retry [02 10]")
            syncOk = syncRx.isNotEmpty() && (syncRx[0].toInt() and 0xFF) == 0x00
        }

        if (!connOk && !syncOk) {
            val rxHint = syncRx.toHexStr().ifBlank { connRx.toHexStr() }.ifBlank { "Timeout" }
            appendLog(ProberLogDirection.WARN, "⚠️ USBDM جاهزة لكن لا يوجد تزامن BDM ($rxHint) — تأكد من توصيل BKGD/RESET/VDD على الكارتة.")
            return null
        }

        if (syncOk && syncRx.size >= 3) {
            val b1 = syncRx[1].toInt() and 0xFF
            val b2 = syncRx[2].toInt() and 0xFF
            val syncVal = (b1 shl 8) or b2
            detectedUsbdmSyncBytes = "%02X %02X".format(b1, b2)
            if (syncVal > 0) detectedUsbdmSpeedHz = syncVal * 1000
            appendLog(
                ProberLogDirection.SUCCESS,
                "✅ تزامن BDM SYNC (0x10) نجح! سرعة/تردد الهدف [reply[1], reply[2]] = $detectedUsbdmSyncBytes ($syncVal)"
            )
        }

        // 5. CMD_USBDM_TARGET_HALT = 0x14 + Read SDIDH/SDIDL (0x1806) via CMD_USBDM_READ_MEM = 0x21
        val haltRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 350, "USBDM TARGET_HALT [02 14]")
        if (haltRx.isNotEmpty() && (haltRx[0].toInt() and 0xFF) == 0x00) {
            appendLog(ProberLogDirection.SUCCESS, "✅ إيقاف المعالج عبر CMD_USBDM_TARGET_HALT (0x14) -> 0x00")
        }

        val sdidRx = transport.usbdmTransceive(
            byteArrayOf(0x08, 0x21, 0x01, 0x02, 0x00, 0x00, 0x18, 0x06),
            16, 400, "USBDM READ_MEM SDIDH/L [0x21 @ 0x1806]"
        )
        if (sdidRx.size >= 3 && (sdidRx[0].toInt() and 0xFF) == 0x00) {
            val sdid16 = ((sdidRx[1].toInt() and 0xFF) shl 8) or (sdidRx[2].toInt() and 0xFF)
            detectedSdidHex = "0x%04X".format(sdid16)
            val modelName = lookupHcs08ModelBySdid(sdid16)
            detectedFreescaleModel = "Freescale / NXP HCS08 — $modelName (SDID=$detectedSdidHex)"
            appendLog(ProberLogDirection.SUCCESS, "✅ قراءة هوية Freescale / NXP HCS08: SDIDH/L=$detectedSdidHex -> $detectedFreescaleModel")
        }

        val dt = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
        val speedNote = when {
            detectedUsbdmSyncBytes.isNotBlank() -> "SYNC=$detectedUsbdmSyncBytes (${detectedUsbdmSpeedHz / 1000} kHz)"
            detectedUsbdmSpeedHz > 0 -> "${detectedUsbdmSpeedHz / 1000} kHz BDM Bus"
            else -> "Auto BDM Bus"
        }
        return SyncLock(
            baudRate = if (detectedUsbdmSpeedHz > 0) detectedUsbdmSpeedHz else 1_500_000,
            evenParity = false,
            parityMode = "USB Bulk Native (EP1/EP2)",
            wakeUpPatternUsed = "USBDM Standard HCS08 BDM ($speedNote)",
            flmd0PinState = "USBDM VDD=+5V (0x06) • BKGD Native",
            resetPinIdleState = "HIGH (+5V) via USBDM RESET",
            resetPinPulsePolarity = "USBDM Hardware BKGD/RESET Sync (0x0F/0x10/0x14)",
            dtrPolarity = "USBDM Native USB Bulk",
            rtsPolarity = "USBDM Hardware RESET",
            inverted = false,
            useResetToggle = true,
            resetPulseMs = 50,
            postResetDelayMs = 100,
            syncByteGapMs = 5,
            syncPulseHex = "02 01 -> 03 06 02 -> 03 02 00 -> 02 0F -> 02 10 -> 02 14",
            framing = DetectedFraming.USBDM_BDM,
            firstReplyHex = (sdidRx.takeIf { it.isNotEmpty() } ?: syncRx).toHexStr(),
            latencySampleMs = dt
        )
    }

    private suspend fun tryFreescaleSingleWireBdm1_5Mbps(): SyncLock? {
        currentCoroutineContext().ensureActive()
        appendLog(ProberLogDirection.INFO, "── اختبار Freescale / NXP HCS08 Single-Wire BDM على 1,500,000 bps (1.5 Mbps) ──")
        transport.configureUart(1_500_000, evenParity = false)
        detectedSdidHex = ""
        detectedFreescaleModel = ""

        val configs = listOf(
            Triple("Single-Wire BDM Active-Low (RTS=RESET)", false, true),
            Triple("Single-Wire BDM Active-High", true, true),
            Triple("Single-Wire BDM Direct 1.5Mbps", false, false)
        )
        for ((polDesc, inverted, doToggle) in configs) {
            currentCoroutineContext().ensureActive()
            if (doToggle) {
                transport.pulseResetSequence(inverted, holdResetMs = 50L, settleMs = 40L)
            } else {
                transport.setControlLines(dtr = false, rts = false)
                delay(25)
            }
            transport.flushRx()
            val t0 = System.currentTimeMillis()
            // Send BDM Sync pulses (0xFE / 0xC0) & read SDID (0x1806..0x1807)
            val syncFe = transport.transceive(byteArrayOf(0xFE.toByte()), 12, 120, "Freescale BDM Sync [0xFE @ 1.5Mbps]")
            val syncC0 = if (syncFe.isEmpty()) transport.transceive(byteArrayOf(0xC0.toByte()), 12, 120, "Freescale BDM Sync [0xC0 @ 1.5Mbps]") else syncFe
            val sdidH = transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x06), 8, 120, "Freescale BDM Read SDIDH [0x1806]")
            val sdidL = transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x07), 8, 120, "Freescale BDM Read SDIDL [0x1807]")

            val validSdid = sdidH.any { it != 0x00.toByte() } || sdidL.any { it != 0x00.toByte() }
            val validSync = syncC0.any { it != 0x00.toByte() && it != 0xFF.toByte() }
            if (validSdid || validSync) {
                val rawCombined = sdidH + sdidL
                val combined = if (rawCombined.isNotEmpty()) rawCombined else syncC0
                if (sdidH.isNotEmpty() && sdidL.isNotEmpty()) {
                    val sdid16 = ((sdidH[0].toInt() and 0xFF) shl 8) or (sdidL[0].toInt() and 0xFF)
                    detectedSdidHex = "0x%04X".format(sdid16)
                    detectedFreescaleModel = lookupHcs08ModelBySdid(sdid16)
                }
                appendLog(ProberLogDirection.SUCCESS, "✅ استجابة Freescale 1.5Mbps BDM: ${combined.toHexStr()}")
                return SyncLock(
                    baudRate = 1_500_000,
                    evenParity = false,
                    parityMode = "8N1 (1.5 Mbps Single-Wire)",
                    wakeUpPatternUsed = "Freescale HCS08 FT232 Single-Wire BDM (0xFE/0xC0 @ 1.5Mbps)",
                    flmd0PinState = "BKGD (TX+1kΩ+RX) @ 1,500,000 bps",
                    resetPinIdleState = "HIGH (+5V) via RTS",
                    resetPinPulsePolarity = polDesc,
                    dtrPolarity = polDesc,
                    rtsPolarity = polDesc,
                    inverted = inverted,
                    useResetToggle = doToggle,
                    resetPulseMs = 50,
                    postResetDelayMs = 40,
                    syncByteGapMs = 2,
                    syncPulseHex = "0xFE / 0xC0 + 0xE8 18 06",
                    framing = DetectedFraming.FREESCALE_SINGLE_WIRE_BDM,
                    firstReplyHex = combined.toHexStr(),
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(5)
                )
            }
        }
        return null
    }

    private suspend fun trySamsungIspHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        appendLog(ProberLogDirection.INFO, "── اختبار Samsung S3F8 / S3F9 / S3P ISP (9600 & 38400 bps) ──")
        detectedSamsungIdHex = ""
        val bauds = listOf(9600, 38400)
        val pols = listOf(
            Triple("Samsung Active-Low Reset (RTS)", false, true),
            Triple("Samsung Active-High Reset", true, true),
            Triple("Samsung Direct UART", false, false)
        )
        for (baud in bauds) {
            transport.configureUart(baud, evenParity = false)
            for ((polDesc, inverted, doToggle) in pols) {
                currentCoroutineContext().ensureActive()
                if (doToggle) {
                    transport.pulseResetSequence(inverted, holdResetMs = 80L, settleMs = 60L)
                } else {
                    transport.setControlLines(dtr = false, rts = false)
                    delay(30)
                }
                transport.flushRx()
                val t0 = System.currentTimeMillis()
                val r1 = transport.transceive(byteArrayOf(0xAA.toByte(), 0x55), 16, 180, "Samsung ISP Sync [AA 55 @ $baud]")
                val r2 = if (r1.isEmpty()) transport.transceive(byteArrayOf(0x80.toByte()), 16, 180, "Samsung ISP Sync [80 @ $baud]") else r1
                if (r2.isNotEmpty() && !r2.all { (it.toInt() and 0xFF) == 0xFF || (it.toInt() and 0xFF) == 0x00 }) {
                    val id1 = transport.transceive(byteArrayOf(0x01), 16, 180, "Samsung ID Query [0x01]")
                    val id2 = if (id1.isEmpty()) transport.transceive(byteArrayOf(0x02), 16, 180, "Samsung ID Query [0x02]") else id1
                    val id3 = if (id2.isEmpty()) transport.transceive(byteArrayOf(0xC0.toByte()), 16, 180, "Samsung ID Query [0xC0]") else id2
                    val idBytes = if (id3.isNotEmpty()) id3 else r2
                    detectedSamsungIdHex = idBytes.toHexStr()
                    appendLog(ProberLogDirection.SUCCESS, "✅ استجابة Samsung ISP @ $baud bps: ${idBytes.toHexStr()}")
                    return SyncLock(
                        baudRate = baud,
                        evenParity = false,
                        parityMode = "8N1",
                        wakeUpPatternUsed = "Samsung S3F/S3P ISP Sync (0xAA 0x55 / 0x80 @ $baud bps)",
                        flmd0PinState = "TOOL0/SDAT + RESET ($polDesc)",
                        resetPinIdleState = "HIGH (+5V)",
                        resetPinPulsePolarity = polDesc,
                        dtrPolarity = polDesc,
                        rtsPolarity = polDesc,
                        inverted = inverted,
                        useResetToggle = doToggle,
                        resetPulseMs = 80,
                        postResetDelayMs = 60,
                        syncByteGapMs = 10,
                        syncPulseHex = if (r1.isNotEmpty()) "AA 55" else "80",
                        framing = DetectedFraming.SAMSUNG_ISP,
                        firstReplyHex = idBytes.toHexStr(),
                        latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                    )
                }
            }
        }
        return null
    }

    private fun lookupHcs08ModelBySdid(sdid16: Int): String = when (sdid16 and 0x0FFF) {
        0x001 -> "MC9S08GB60 / GT60"
        0x008 -> "MC9S08QD4 / QD2"
        0x009 -> "MC9S08QG8 / QG4"
        0x00A -> "MC9S08AW60 / AW32 / AW16"
        0x00E -> "MC9S08LC60 / LC36"
        0x012 -> "MC9S08EL32 / SL16"
        0x013 -> "MC9S08DZ128 / DZ96"
        0x014 -> "MC9S08SH8 / SH4"
        0x015 -> "MC9S08QE128 / QE96"
        0x016 -> "MC9S08JM16 / JM8"
        0x019 -> "MC9S08DZ60 / DZ32"
        0x01A -> "MC9S08SH32 / SH16"
        0x01D -> "MC9S08AC60 / AC48 / AC32"
        0x01E -> "MC9S08JM60 / JM32"
        0x01F -> "MC9S08QE8 / QE4"
        0x021 -> "MC9S08LL16 / LL8"
        0x026 -> "MC9S08FL16 / FL8"
        0x027 -> "MC9S08PA16 / PT16"
        0x02B -> "MC9S08SC4"
        else -> "Freescale/NXP HCS08 (SDID=0x%03X)".format(sdid16 and 0x0FFF)
    }

    private suspend fun runStage1UniversalBlindHandshake(family: ProberTargetFamily): SyncLock? {
        // 0. If native USBDM programmer is attached, run dedicated USB Bulk USBDM flow immediately
        if (transport.isUsbdmSession()) {
            return tryUsbdmHandshake()
        }

        // 1. NEC / Renesas (78K0 / RL78) at 115200 & 9600 bps (8N1)
        if (family == ProberTargetFamily.NEC_78K0 || family == ProberTargetFamily.AUTO_DETECT) {
            appendLog(ProberLogDirection.INFO, "── [1/4] فحص NEC / Renesas 78K0 / RL78 على 115200 و 9600 bps (8N1) ──")
            for (necBaud in listOf(9600, 115200)) {
                tryLiteralNec78k0Handshake(necBaud, false, "Active-Low (DTR=FLMD0, RTS=RESET)", inverted = false, useResetToggle = true)?.let { return it }
                tryLiteralNec78k0Handshake(necBaud, false, "Active-High / عكس القطبية", inverted = true, useResetToggle = true)?.let { return it }
                tryLiteralNec78k0Handshake(necBaud, false, "بدون نبضة خطوط (Direct $necBaud)", inverted = false, useResetToggle = false)?.let { return it }
            }
        }

        // 2. Freescale / NXP HCS08 Single-Wire BDM via FT232 @ 1.5 Mbps
        if (family == ProberTargetFamily.FREESCALE_HCS08 || family == ProberTargetFamily.AUTO_DETECT) {
            tryFreescaleSingleWireBdm1_5Mbps()?.let { return it }
        }

        // 3. Samsung S3F8 / S3F9 / S3P ISP (9600 & 38400 bps)
        if (family == ProberTargetFamily.SAMSUNG_S3F || family == ProberTargetFamily.AUTO_DETECT) {
            trySamsungIspHandshake()?.let { return it }
        }

        // 4. Universal Blind Sync matrix across 4800..115200, 8N1 & 8E1, and 5 global wake-up patterns (incl. R8C 16x0x00 & STM32 8E1 0x7F)
        val baudRates = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(115200, 57600, 38400, 19200, 9600, 4800)
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(9600, 19200, 38400, 57600, 115200, 4800)
            else -> listOf(9600, 115200, 38400, 19200, 57600, 4800)
        }
        val parityOptions = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(true to "8E1", false to "8N1")
            ProberTargetFamily.NEC_78K0 -> listOf(false to "8N1", true to "8E1")
            else -> listOf(false to "8N1", true to "8E1")
        }
        val lineConfigs = listOf(
            Triple("Active-Low (DTR=FLMD0, RTS=RESET)", false, true),
            Triple("Active-High / معكوسة", true, true),
            Triple("بدون تحكم خطوط (Direct)", false, false)
        )

        for (baud in baudRates) {
            for ((evenParity, parityLabel) in parityOptions) {
                currentCoroutineContext().ensureActive()
                transport.configureUart(baud, evenParity = evenParity)
                appendLog(ProberLogDirection.INFO, "── فحص السرعة $baud bps ($parityLabel) بالأنماط الخمسة ──")

                for ((polDesc, inverted, doToggle) in lineConfigs) {
                    currentCoroutineContext().ensureActive()
                    if (doToggle) {
                        transport.pulseResetSequence(inverted, holdResetMs = 100L, settleMs = 150L)
                    } else {
                        transport.setControlLines(dtr = false, rts = false)
                        delay(50)
                    }
                    transport.flushRx()

                    val flmd0State = if (doToggle) "HIGH (+5V) via DTR=$inverted" else "Static (DTR=false)"
                    val resetIdle = if (doToggle) "HIGH (+5V) via RTS=$inverted" else "Static (RTS=false)"
                    val pulsePol = if (doToggle) (if (inverted) "Active-High Pulse (100ms)" else "Active-Low Pulse (100ms)") else "Static"

                    // 1. Pattern 1: Framed (NEC / Renesas RL78/RX/V850 / TI MSP430 / Infineon)
                    if (!(baud == 9600 && !evenParity && (family == ProberTargetFamily.NEC_78K0 || family == ProberTargetFamily.AUTO_DETECT))) {
                        val necFrame = buildNecCommandFrame(0x00)
                        val t0 = System.currentTimeMillis()
                        transport.writeRawBytes(byteArrayOf(0x00))
                        delay(10)
                        transport.writeRawBytes(byteArrayOf(0x00))
                        delay(40)
                        transport.flushRx()
                        val rxNec = transport.transceive(necFrame, 16, 280, "P1 Framed NEC/RL78 [$baud $parityLabel]", stopOnCompleteNecFrame = true)
                        if (rxNec.any { (it.toInt() and 0xFF) == STX }) {
                            return SyncLock(
                                baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                wakeUpPatternUsed = "Pattern 1: Framed (NEC / Renesas 78K0 / RL78)",
                                flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                syncPulseHex = "0x00 (10ms) 0x00 + 01 01 00 FF 03",
                                framing = DetectedFraming.NEC_STX_ETX,
                                firstReplyHex = rxNec.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                            )
                        }
                    }

                    // RL78 0x3A / TI MSP430 BSL (0x80) / Infineon (0x00/0x6C)
                    val pattern1Extra = listOf(
                        "Pattern 1: RL78 Single-Wire (0x3A Reset)" to byteArrayOf(0x3A, 0x01, 0x00, 0xFF.toByte(), 0x03),
                        "Pattern 1: TI MSP430 BSL Sync (0x80)" to byteArrayOf(0x80.toByte(), 0x01, 0x00, 0x19, 0xE8.toByte(), 0x62),
                        "Pattern 1: Infineon BSL Header (0x00/0x6C)" to byteArrayOf(0x00, 0x6C)
                    )
                    for ((pName, pBytes) in pattern1Extra) {
                        val t0 = System.currentTimeMillis()
                        val rx = transport.transceive(pBytes, 20, 200, "$pName [$baud $parityLabel]")
                        if (rx.isNotEmpty()) {
                            return SyncLock(
                                baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                wakeUpPatternUsed = pName,
                                flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                syncPulseHex = pBytes.toHexStr(),
                                framing = classifyFraming(rx, pBytes),
                                firstReplyHex = rx.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                            )
                        }
                    }

                    // 2. Pattern 2: Renesas Direct Commands (R8C/M16C/H8/SH: 16x 0x00 @ 20ms then 0xB0 & 0xFB)
                    if (family == ProberTargetFamily.RENESAS_R8C_RL78 || family == ProberTargetFamily.AUTO_DETECT) {
                        val t0 = System.currentTimeMillis()
                        appendLog(ProberLogDirection.TX, "Pattern 2 (R8C/M16C/H8/SH): 16x 0x00 @ 20ms [$baud $parityLabel]")
                        for (i in 0 until 16) {
                            currentCoroutineContext().ensureActive()
                            transport.writeRawBytes(byteArrayOf(0x00))
                            delay(20)
                        }
                        transport.flushRx()
                        val rxB0 = transport.transceive(byteArrayOf(0xB0.toByte()), 16, 220, "P2 Direct 0xB0 [$baud $parityLabel]")
                        val rxFB = if (rxB0.isEmpty()) transport.transceive(byteArrayOf(0xFB.toByte()), 24, 240, "P2 Direct 0xFB [$baud $parityLabel]") else ByteArray(0)
                        val rxDirect = if (rxB0.isNotEmpty()) rxB0 else rxFB
                        if (rxDirect.isNotEmpty()) {
                            return SyncLock(
                                baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                wakeUpPatternUsed = "Pattern 2: Direct Commands (R8C/M16C/H8/SH: 16x0x00 @ 20ms + 0xB0/0xFB)",
                                flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                resetPulseMs = 100, postResetDelayMs = 20, syncByteGapMs = 20,
                                syncPulseHex = "16x 0x00 (20ms) + ${if (rxB0.isNotEmpty()) "0xB0" else "0xFB"}",
                                framing = DetectedFraming.RENESAS_RAW,
                                firstReplyHex = rxDirect.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(15)
                            )
                        }
                    }

                    // 3. Pattern 3: Auto-Baud (STM32 0x7F + 0x02 0xFD, Nuvoton, PIC, Holtek)
                    val autoBaudCandidates = listOf(
                        "Pattern 3: Auto-Baud STM32 (0x7F -> 0x02 0xFD)" to byteArrayOf(0x7F),
                        "Pattern 3: Auto-Baud General (0x55)" to byteArrayOf(0x55),
                        "Pattern 3: Nuvoton/Holtek Sync (0xA5 / 0x5A)" to byteArrayOf(0xA5.toByte(), 0x5A)
                    )
                    for ((pName, pBytes) in autoBaudCandidates) {
                        val t0 = System.currentTimeMillis()
                        val rx = transport.transceive(pBytes, 16, 200, "$pName [$baud $parityLabel]")
                        if (rx.isNotEmpty()) {
                            val b0 = rx[0].toInt() and 0xFF
                            if (pBytes[0] == 0x7F.toByte() && (b0 == ACK_STM32 || b0 == NACK_STM32)) {
                                // Verify STM32 Get ID 0x02 0xFD
                                val idRx = transport.transceive(byteArrayOf(0x02, 0xFD.toByte()), 16, 220, "STM32 Verify [0x02 0xFD]")
                                val firstCombined = (rx + idRx).toHexStr()
                                return SyncLock(
                                    baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                    wakeUpPatternUsed = pName,
                                    flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                    dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                    resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                    syncPulseHex = "0x7F -> 0x02 0xFD",
                                    framing = DetectedFraming.STM32_ACK,
                                    firstReplyHex = firstCombined,
                                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                                )
                            } else {
                                return SyncLock(
                                    baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                    wakeUpPatternUsed = pName,
                                    flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                    dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                    resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                    syncPulseHex = pBytes.toHexStr(),
                                    framing = classifyFraming(rx, pBytes),
                                    firstReplyHex = rx.toHexStr(),
                                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                                )
                            }
                        }
                    }

                    // 4. Pattern 4: Standard & Text Patterns (NXP '?', AVR 0x30 0x20, ESP SLIP)
                    val stdPatterns = listOf(
                        "Pattern 4: NXP ISP Autobaud ('?' 0x3F)" to byteArrayOf(0x3F),
                        "Pattern 4: AVR STK500 GET_SYNC (0x30 0x20)" to byteArrayOf(0x30, 0x20),
                        "Pattern 4: ESP ROM SLIP Sync" to byteArrayOf(
                            0xC0.toByte(), 0x00, 0x08, 0x24, 0x00, 0x00, 0x00, 0x00,
                            0x07, 0x07, 0x12, 0x20, 0x55, 0x55, 0xC0.toByte()
                        )
                    )
                    for ((pName, pBytes) in stdPatterns) {
                        val t0 = System.currentTimeMillis()
                        val rx = transport.transceive(pBytes, 24, 200, "$pName [$baud $parityLabel]")
                        if (rx.isNotEmpty()) {
                            return SyncLock(
                                baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                wakeUpPatternUsed = pName,
                                flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                syncPulseHex = pBytes.toHexStr(),
                                framing = DetectedFraming.CUSTOM_RAW,
                                firstReplyHex = rx.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                            )
                        }
                    }

                    // 5. Pattern 5: Blind Exploration for Unknown MCUs (0x00, 0xFF, 0xAA, 0x55, 0x01, 0x05)
                    val blindBytes = listOf(0xFF, 0xAA, 0x01, 0x05)
                    for (b in blindBytes) {
                        val t0 = System.currentTimeMillis()
                        val pkt = byteArrayOf(b.toByte())
                        val rx = transport.transceive(pkt, 20, 160, "P5 Blind Probe [0x%02X @ $baud $parityLabel]".format(b))
                        if (rx.isNotEmpty()) {
                            return SyncLock(
                                baudRate = baud, evenParity = evenParity, parityMode = parityLabel,
                                wakeUpPatternUsed = "Pattern 5: Blind Unknown MCU Probe (0x%02X)".format(b),
                                flmd0PinState = flmd0State, resetPinIdleState = resetIdle, resetPinPulsePolarity = pulsePol,
                                dtrPolarity = polDesc, rtsPolarity = pulsePol, inverted = inverted, useResetToggle = doToggle,
                                resetPulseMs = 100, postResetDelayMs = 150, syncByteGapMs = 10,
                                syncPulseHex = pkt.toHexStr(),
                                framing = classifyFraming(rx, pkt),
                                firstReplyHex = rx.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Queries Universal Auto-ID opcodes (0xC0, 0xC5, 0xFB, 0x01, 0x02, 0x00, 0x9F, 0xAF)
     * and generates `universalAutoIdRule`, manufacturer, and inferred model.
     */
    private suspend fun extractUniversalAutoId(sync: SyncLock): UniversalIdCapture {
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            if (detectedSdidHex.isBlank()) {
                transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 300, "Auto-ID USBDM HALT [02 14]")
                val sdidRx = transport.usbdmTransceive(
                    byteArrayOf(0x08, 0x21, 0x01, 0x02, 0x00, 0x00, 0x18, 0x06),
                    16, 350, "Auto-ID USBDM SDID [0x21 @ 0x1806]"
                )
                if (sdidRx.size >= 3 && (sdidRx[0].toInt() and 0xFF) == 0x00) {
                    val sdid16 = ((sdidRx[1].toInt() and 0xFF) shl 8) or (sdidRx[2].toInt() and 0xFF)
                    detectedSdidHex = "0x%04X".format(sdid16)
                    detectedFreescaleModel = "Freescale / NXP HCS08 — ${lookupHcs08ModelBySdid(sdid16)} (SDID=$detectedSdidHex)"
                }
            }
            val model = detectedFreescaleModel.ifBlank { "Freescale / NXP HCS08 Target" }
            val sig = buildString {
                if (detectedSdidHex.isNotBlank()) append("SDIDH/L=$detectedSdidHex")
                if (detectedUsbdmSyncBytes.isNotBlank()) append(" | SYNC=$detectedUsbdmSyncBytes")
                if (detectedUsbdmCaps.isNotBlank()) append(" | USBDM Caps=$detectedUsbdmCaps")
            }.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = "USBDM GET_CAPABILITIES (0x01) -> SET_VDD 5V (0x06) -> SET_TARGET HCS08 (0x02) -> CONNECT (0x0F) + SYNC (0x10) -> TARGET_HALT (0x14) -> READ_MEM 0x21 (0x1806..0x1807)",
                manufacturerGuess = "Freescale / NXP Semiconductors",
                inferredModel = model,
                rawSignatureSummary = sig,
                rawC0Payload = ByteArray(0)
            )
        }
        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val model = detectedFreescaleModel.ifBlank { "Freescale / NXP HCS08 (1.5Mbps BDM)" }
            val sig = if (detectedSdidHex.isNotBlank()) "SDID=$detectedSdidHex | ${sync.firstReplyHex}" else sync.firstReplyHex
            return UniversalIdCapture(
                autoIdRule = "FT232 @ 1,500,000 bps (8N1) Single-Wire BKGD -> BDM Sync (0xFE/0xC0) -> Read SDID (0x1806..0x1807)",
                manufacturerGuess = "Freescale / NXP Semiconductors",
                inferredModel = model,
                rawSignatureSummary = sig,
                rawC0Payload = ByteArray(0)
            )
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            val sig = detectedSamsungIdHex.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = "RESET Pulse -> Samsung ISP Sync (0xAA 0x55 / 0x80 @ ${sync.baudRate} bps) -> ID Query [0x01/0x02/0xC0]",
                manufacturerGuess = "Samsung Electronics",
                inferredModel = "Samsung S3F8 / S3F9 / S3P Series ($sig)",
                rawSignatureSummary = sig,
                rawC0Payload = ByteArray(0)
            )
        }

        val respondedParts = mutableListOf<String>()
        var c0Raw = ByteArray(0)
        var allAscii = ""
        val allHexPreview = StringBuilder()

        for (cmd in UNIVERSAL_ID_CMDS) {
            currentCoroutineContext().ensureActive()
            val tx = when (sync.framing) {
                DetectedFraming.NEC_STX_ETX -> buildNecCommandFrame(cmd)
                DetectedFraming.STM32_ACK -> byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte())
                else -> byteArrayOf(cmd.toByte())
            }
            val rx = transport.transceive(tx, 64, 240, "Universal Auto-ID [0x%02X]".format(cmd), logTxRx = false)
            if (rx.isEmpty()) continue

            val accepted = when (sync.framing) {
                DetectedFraming.NEC_STX_ETX -> parseNecStatus(rx) == ACK_NEC
                DetectedFraming.STM32_ACK -> (rx[0].toInt() and 0xFF) == ACK_STM32
                else -> (rx[0].toInt() and 0xFF) !in HARD_REJECT_STATUS
            }
            if (!accepted) continue

            var fullPayload = rx
            if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                val extra = if (!rx.drop(4).any { (it.toInt() and 0xFF) == STX }) transport.readFollowUp(64, 260, true) else ByteArray(0)
                fullPayload = extractNecSecondFramePayload(rx + extra) ?: (rx + extra)
                if (cmd == 0xC0 && c0Raw.isEmpty()) c0Raw = fullPayload
            }
            val ascii = fullPayload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            if (ascii.length >= 2) allAscii += "$ascii "
            val preview = fullPayload.take(16).toByteArray().toHexStr()
            respondedParts.add("0x%02X->${if (ascii.length >= 3) ascii else preview}".format(cmd))
            if (allHexPreview.isEmpty()) allHexPreview.append("0x%02X: $preview".format(cmd))
        }

        val (mfg, model) = inferManufacturerAndModel(sync, allAscii, respondedParts.joinToString(" | "))
        val rule = if (respondedParts.isNotEmpty()) {
            "Wake via [${sync.wakeUpPatternUsed}] @ ${sync.baudRate} ${sync.parityMode} -> query universal ID opcodes [0xC0,0xC5,0xFB,0x01,0x02,0x00,0x9F,0xAF] -> matched: ${respondedParts.joinToString(", ")}"
        } else {
            "Wake via [${sync.wakeUpPatternUsed}] @ ${sync.baudRate} ${sync.parityMode} -> no explicit ID opcode replied (rely on sync reply: ${sync.firstReplyHex})"
        }
        return UniversalIdCapture(
            autoIdRule = rule,
            manufacturerGuess = mfg,
            inferredModel = model,
            rawSignatureSummary = respondedParts.joinToString(" | ").ifBlank { sync.firstReplyHex },
            rawC0Payload = c0Raw
        )
    }

    private fun inferManufacturerAndModel(sync: SyncLock, ascii: String, sigText: String): Pair<String, String> {
        if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            return "Freescale / NXP Semiconductors" to detectedFreescaleModel.ifBlank { "MC9S08 / HCS08 Family" }
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            return "Samsung Electronics" to "Samsung S3F8 / S3F9 / S3P (${detectedSamsungIdHex.ifBlank { sigText }})"
        }
        val combined = "$ascii $sigText ${sync.firstReplyHex}".uppercase(Locale.US)
        return when {
            "78F" in combined || "UPD" in combined -> "NEC / Renesas Electronics" to (Regex("78F[0-9A-Z]+").find(combined)?.value ?: "NEC 78K0 Series (UPD78F0xxx)")
            "R5F" in combined || "RL78" in combined -> "Renesas Electronics" to (Regex("R5F[0-9A-Z]+").find(combined)?.value ?: "Renesas RL78 / R8C / RX")
            "VER." in combined || sync.framing == DetectedFraming.RENESAS_RAW -> "Renesas Electronics" to "Renesas R8C / M16C / H8 / SH (${ascii.trim().ifBlank { "Bootloader" }})"
            sync.framing == DetectedFraming.NEC_STX_ETX -> "NEC / Renesas Electronics" to (ascii.trim().ifBlank { "NEC 78K0 / RL78 Bootloader" })
            sync.framing == DetectedFraming.STM32_ACK -> "STMicroelectronics" to "STM32 UART System Bootloader (${ascii.trim().ifBlank { sigText }})"
            "MSP" in combined -> "Texas Instruments" to "TI MSP430 BSL"
            "LPC" in combined || "NXP" in combined -> "NXP Semiconductors" to "NXP LPC ISP Bootloader"
            "ESP" in combined -> "Espressif Systems" to "ESP32 / ESP8266 ROM Bootloader"
            else -> "شركة غير محددة / مخصص" to (ascii.trim().ifBlank { "UART Target (${sync.baudRate} ${sync.parityMode})" })
        }
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
        } else if (sync.framing == DetectedFraming.USBDM_BDM) {
            transport.flushRx()
            delay(10)
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

        if (sync.framing == DetectedFraming.USBDM_BDM) {
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT before Direct Read [02 14]", logTxRx = false)
            for (addr in listOf(0xC000, 0x8000, 0xE000)) {
                val read16 = transport.usbdmTransceive(
                    byteArrayOf(
                        0x08, 0x21, 0x01, 0x10,
                        0x00, 0x00, ((addr ushr 8) and 0xFF).toByte(), (addr and 0xFF).toByte()
                    ),
                    32, 350, "USBDM Direct Read [0x21 @ 0x%04X..0x%04X]".format(addr, addr + 15)
                )
                if (read16.size >= 17 && (read16[0].toInt() and 0xFF) == 0x00) {
                    val payload = read16.copyOfRange(1, 17)
                    return updateAndReturn(
                        "stage2_direct_read",
                        ChecklistStepStatus.PASSED,
                        "USBDM READ_MEM [0x21 @ 0x%04X] -> ${payload.toHexStr()}".format(addr)
                    )
                }
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "USBDM READ_MEM (0x21) مرفوض (الرقاقة محمية FSEC Secured)")
        }

        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val bdmRead16 = transport.transceive(byteArrayOf(0xE8.toByte(), 0xE0.toByte(), 0x00), 16, 180, "Freescale 1.5Mbps BDM Read [0xE000]")
            if (bdmRead16.isNotEmpty() && !bdmRead16.all { it == 0x00.toByte() }) {
                return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "BDM Single-Wire [0xE8 @ 0xE000] -> ${bdmRead16.toHexStr()}")
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "فشلت القراءة المباشرة عبر Single-Wire BDM")
        }

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            for (cmd in listOf(0x03, 0x11, 0xD0, 0xA0)) {
                val rx = transport.transceive(byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x10), 24, 240, "Samsung Read [0x%02X]".format(cmd))
                val real16 = extract16Bytes(rx, sync.framing)
                if (real16 != null) {
                    return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "Samsung [0x%02X] -> ${real16.toHexStr()}".format(cmd))
                }
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "فشلت أوامر القراءة المباشرة في Samsung ISP")
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
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, "غير مدعوم / غير مطلوب في ${sync.framing.label}")
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

    private suspend fun testVerify0x13Probe(sync: SyncLock): Pair<ChecklistStepItem, String> {
        updateStep("stage2_verify_0x13", ChecklistStepStatus.RUNNING, "اختبار 0x13 على صفحة 0xFF ثم صفحة 0x00 لكشف القفل...")
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "غير منطبق على ${sync.framing.label}")
            return item to "N/A (${sync.framing.name})"
        }

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
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.PASSED, "$diffSummary (لا يكفي لفك القفل للسحب المباشر)")
            item to diffSummary
        } else {
            val item = updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "فشل إطار البيانات في 0x13 ($diffSummary)")
            item to diffSummary
        }
    }

    private suspend fun testSignatureRegisterRead(
        sync: SyncLock,
        autoId: UniversalIdCapture
    ): SignatureProbeCapture {
        updateStep("stage2_signature", ChecklistStepStatus.RUNNING, "اختبار 0xC0, 0xC5, 0xFB, SDIDH/L, 0x01, 0x02...")
        if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val sigText = autoId.rawSignatureSummary
            val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "${autoId.inferredModel} | $sigText")
            return SignatureProbeCapture(item, ByteArray(0), autoId.manufacturerGuess, autoId.inferredModel)
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "Samsung ID: ${autoId.rawSignatureSummary}")
            return SignatureProbeCapture(item, ByteArray(0), autoId.manufacturerGuess, autoId.inferredModel)
        }

        val cmds = listOf(0xC0, 0xC5, 0xFB, 0x01, 0x02, 0x9F, 0xAF)
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
                val (mfg, mdl) = inferManufacturerAndModel(sync, "", rx.toHexStr())
                val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
                return SignatureProbeCapture(item, rx, mfg, mdl)
            } else if (sync.framing == DetectedFraming.NEC_STX_ETX && parseNecStatus(rx) == ACK_NEC) {
                val extra = if (!rx.drop(4).any { (it.toInt() and 0xFF) == STX }) transport.readFollowUp(64, 320, true) else ByteArray(0)
                val payload = extractNecSecondFramePayload(rx + extra) ?: (rx + extra)
                val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                val (mfg, mdl) = inferManufacturerAndModel(sync, ascii, payload.toHexStr())
                val label = if (ascii.isNotBlank()) "[0x%02X] $ascii | Raw=${payload.toHexStr()}".format(cmd)
                else "[0x%02X] ${payload.take(24).toByteArray().toHexStr()}".format(cmd)
                val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, label)
                return SignatureProbeCapture(item, payload, mfg, mdl)
            } else if (sync.framing != DetectedFraming.NEC_STX_ETX && sync.framing != DetectedFraming.STM32_ACK) {
                val b0 = rx[0].toInt() and 0xFF
                if (b0 !in HARD_REJECT_STATUS) {
                    val ascii = rx.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                    val (mfg, mdl) = inferManufacturerAndModel(sync, ascii, rx.toHexStr())
                    val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
                    return SignatureProbeCapture(item, rx, mfg, mdl)
                }
            }
        }
        val item = if (autoId.rawC0Payload.isNotEmpty()) {
            updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, autoId.rawSignatureSummary)
        } else {
            updateAndReturn("stage2_signature", ChecklistStepStatus.FAILED, "لم تستجب أوامر التوقيع العالمية")
        }
        return SignatureProbeCapture(item, autoId.rawC0Payload, autoId.manufacturerGuess, autoId.inferredModel)
    }

    private suspend fun testRamStubInjection(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_ram_stub", ChecklistStepStatus.RUNNING, "اختبار عنوان RAM مؤقت دون لمس الفلاش...")
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val ramRx = transport.usbdmTransceive(
                byteArrayOf(0x08, 0x21, 0x01, 0x04, 0x00, 0x00, 0x00, 0x80.toByte()),
                12, 300, "USBDM RAM Access [0x21 @ 0x0080]"
            )
            return if (ramRx.size >= 5 && (ramRx[0].toInt() and 0xFF) == 0x00) {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.PASSED, "USBDM RAM 0x0080 مفتوح -> ${ramRx.copyOfRange(1, 5).toHexStr()}")
            } else {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "RAM مقفل via USBDM")
            }
        }
        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM || sync.framing == DetectedFraming.SAMSUNG_ISP) {
            return updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "غير منطبق في ${sync.framing.label}")
        }
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

        if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val isNativeUsbdm = (sync.framing == DetectedFraming.USBDM_BDM)
            if (isNativeUsbdm) {
                val stsRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x0D), 8, 250, "USBDM GET_BDM_STATUS [02 0D]")
                val stsHex = if (stsRx.isNotEmpty()) "0x%02X".format(stsRx[0].toInt() and 0xFF) else "0x00"
                discovered.add(DiscoveredCommandEntry("0x01", 0x01, stsHex, 0, "USBDM Get Capabilities", true))
                discovered.add(DiscoveredCommandEntry("0x06", 0x06, "0x00", 1, "USBDM Set Target VDD (5V=0x02)", true))
                discovered.add(DiscoveredCommandEntry("0x02", 0x02, "0x00", 1, "USBDM Set Target Type (HCS08=0x00)", true))
                discovered.add(DiscoveredCommandEntry("0x0F", 0x0F, "0x00", 0, "USBDM Connect", true))
                discovered.add(DiscoveredCommandEntry("0x10", 0x10, "0x00", 0, "USBDM BDM Sync", true))
                discovered.add(DiscoveredCommandEntry("0x14", 0x14, "0x00", 0, "USBDM Target Halt", true))
                discovered.add(DiscoveredCommandEntry("0x21", 0x21, "0x00", 6, "USBDM Read Memory (SDID/Flash)", true))
                discovered.add(DiscoveredCommandEntry("0x20", 0x20, "0x00", 6, "USBDM Write Memory (FCMD/RAM)", false))
            } else {
                discovered.add(DiscoveredCommandEntry("0xFE", 0xFE, "SYNC", 0, "Single-Wire BDM Sync (1.5Mbps)", true))
                discovered.add(DiscoveredCommandEntry("0xE8", 0xE8, "BDM", 2, "HCS08 BDM Read Byte", true))
                discovered.add(DiscoveredCommandEntry("0xC8", 0xC8, "BDM", 3, "HCS08 BDM Write Byte", false))
            }
            discovered.add(DiscoveredCommandEntry("0x20", 0x20, "FCMD", 2, "HCS08 FCMD Byte Program", false))
            discovered.add(DiscoveredCommandEntry("0x40", 0x40, "FCMD", 2, "HCS08 FCMD Page Erase", false))
            discovered.add(DiscoveredCommandEntry("0x41", 0x41, "FCMD", 0, "HCS08 FCMD Mass Erase (Unsecure)", false))
            return discovered
        }

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            for (cmd in listOf(0x01, 0x02, 0x03, 0x11, 0xC0, 0xD0, 0xA0)) {
                if (cmd in WRITE_ERASE_GUARD_CMDS) continue
                val rx = transport.transceive(byteArrayOf(cmd.toByte()), 16, 120, "Samsung Sweep [0x%02X]".format(cmd), logTxRx = false)
                if (rx.isNotEmpty() && (rx[0].toInt() and 0xFF) !in HARD_REJECT_STATUS) {
                    discovered.add(DiscoveredCommandEntry("0x%02X".format(cmd), cmd, "0x%02X".format(rx[0].toInt() and 0xFF), 1, guessCommandRole(cmd), true))
                }
            }
            discovered.add(DiscoveredCommandEntry("0x20", 0x20, "ISP", 0, "Samsung Chip Erase", false))
            discovered.add(DiscoveredCommandEntry("0x41", 0x41, "ISP", 4, "Samsung Page Program", false))
            return discovered
        }

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
        var consecutiveNoReply = 0
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

            val tx = if (cmd in ZERO_ARG_DANGER_CMDS) {
                buildNecCommandFrame(cmd, byteArrayOf(-1, -1, -1, -1, -1, -1))
            } else {
                buildNecCommandFrame(cmd)
            }

            var rx = transport.transceive(
                txBytes = tx,
                expectedMax = 12,
                timeoutMs = 60,
                label = "Sweep 0x%02X".format(cmd),
                stopOnCompleteNecFrame = true,
                logTxRx = false
            )
            if (rx.isEmpty() && sync.framing == DetectedFraming.NEC_STX_ETX) {
                consecutiveNoReply++
                if (consecutiveNoReply >= 3 || cmd == 0x90 || cmd == 0x9A || cmd == 0xA0) {
                    consecutiveNoReply = 0
                    val ping = transport.transceive(buildNecCommandFrame(0x00), 12, 140, "Sweep Ping", stopOnCompleteNecFrame = true, logTxRx = false)
                    if (parseNecStatus(ping) != ACK_NEC) {
                        hardwareResetAndResync(sync, "استعادة التزامن في المرحلة 3 عند الأمر 0x%02X".format(cmd), relockOsc = false)
                        rx = transport.transceive(
                            txBytes = tx,
                            expectedMax = 12,
                            timeoutMs = 70,
                            label = "Sweep Retry 0x%02X".format(cmd),
                            stopOnCompleteNecFrame = true,
                            logTxRx = false
                        )
                    }
                }
            } else {
                consecutiveNoReply = 0
            }
            if (rx.isEmpty()) continue

            val sts = if (sync.framing == DetectedFraming.NEC_STX_ETX) parseNecStatus(rx) else (rx[0].toInt() and 0xFF)
            if (sts == ACK_NEC || sts == STS_PARAM_ERROR || sts == STS_BLANK_CHECK_ERROR || sts == STS_PROTECT_ERROR) {
                candidateBytes.add(cmd to sts)
                appendLog(ProberLogDirection.SUCCESS, "رصد أمر [0x%02X] -> Status 0x%02X (${guessCommandRole(cmd)})".format(cmd, sts))
            }
        }

        ensureBootloaderReady(sync, "تأكيد الجاهزية قبل فحص أطوال LEN", relockOsc = false)
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
                    byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())
                }
                cmd == 0x9A && lenArgs == 1 -> byteArrayOf(0x00)
                cmd == 0x90 && lenArgs == 1 -> byteArrayOf(0x00)
                cmd == 0x90 && lenArgs == 4 -> byteArrayOf(0x80.toByte(), 0x00, 0x06, 0x05)
                else -> ByteArray(lenArgs) { fillByte }
            }

            var rx = transport.transceive(
                txBytes = buildNecCommandFrame(cmd, args),
                expectedMax = 12,
                timeoutMs = 110,
                label = "LEN=$lenArgs [0x%02X]".format(cmd),
                stopOnCompleteNecFrame = true,
                logTxRx = false
            )
            if (rx.isEmpty() && sync.framing == DetectedFraming.NEC_STX_ETX) {
                hardwareResetAndResync(sync, "انقطاع الاستجابة في فحص LEN=$lenArgs للأمر 0x%02X".format(cmd), relockOsc = false)
                rx = transport.transceive(
                    txBytes = buildNecCommandFrame(cmd, args),
                    expectedMax = 12,
                    timeoutMs = 120,
                    label = "LEN=$lenArgs Retry [0x%02X]".format(cmd),
                    stopOnCompleteNecFrame = true,
                    logTxRx = false
                )
            }
            val sts = parseNecStatus(rx)
            if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                if (cmd == 0x13 && sts == ACK_NEC) ensureBootloaderReady(sync, "تنظيف بعد 0x13", relockOsc = false)
                return lenArgs
            }
        }
        return when (cmd) {
            0x00, 0xC0, 0xC5, 0xFB -> 0
            0x9A, 0xA0 -> 1
            0x90 -> 4
            0x13, 0x22, 0x30, 0x32, 0x40, 0xB0 -> 6
            0x20 -> 0
            else -> null
        }
    }

    // ==================== STAGE 4: CRYSTAL MATRIX (0x90), BOUNDS (8KB..512KB), BAUD & TIMING ====================

    private suspend fun runStage4DynamicFamilyAlgorithms(
        sync: SyncLock,
        base: ProberBlueprintSummary,
        rawC0Payload: ByteArray
    ): Pair<ProberBlueprintSummary, ByteArray?> {
        var maxBaud = sync.baudRate
        val supportedBauds = mutableListOf(sync.baudRate)
        var pageSize = 256
        var blockSize = 1024
        var flashEndHex = "0x007FFF (32 KB)"
        var flashRule = "Static Fallback"
        var oscRule = "0x90 غير مدعوم — التردد الافتراضي الداخلي"
        var oscProbeStr = "--"
        var winningOscText = "--"
        var detectedCrystal = "غير محدد"
        var winningOscPayload: ByteArray? = null
        var baudRule = "Fixed at ${sync.baudRate} bps"
        val supportedStxPages = mutableListOf<Int>()

        val rttSamples = mutableListOf(sync.latencySampleMs)
        var measuredBaudSwitchMs = 25
        var measuredCmdToDataMs = 12
        var measuredFrameToFrameMs = 18

        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            ensureBootloaderReady(sync, "بداية المرحلة 4", relockOsc = false)

            // 4.1: Dynamic Flash Size Boundary Probe (8KB -> 512KB)
            val c0ParsedEnd = parseNecSignatureEndAddress(rawC0Payload)
            val endCandidates = listOf(
                0x07FFFF to "0x07FFFF (512 KB)",
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
                var rx = transport.transceive(csCmd, 16, 280, "4.1 Boundary 0xB0 [$label]", stopOnCompleteNecFrame = true)
                if (rx.isEmpty()) {
                    hardwareResetAndResync(sync, "4.1 Boundary 0xB0 Timeout [$label]", relockOsc = false)
                    rx = transport.transceive(csCmd, 16, 280, "4.1 Boundary 0xB0 Retry [$label]", stopOnCompleteNecFrame = true)
                }
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || parseChecksum16(rx) != null) {
                    rttSamples.add((System.currentTimeMillis() - t0).toInt())
                    b0EndLabel = label
                    break
                }
            }
            if (b0EndLabel != null) flashEndHex = b0EndLabel
            flashRule = buildString {
                append("Read Signature (0xC0/0xFB)")
                if (c0ParsedEnd != null) append(" -> C0 End=0x%06X".format(c0ParsedEnd))
                append(" + Boundary 0xB0 Probe (512KB->8KB) -> Confirmed $flashEndHex")
            }

            // Block Size Boundary Probe via Read-Only 0x32 Blank Check
            for (bSize in listOf(1024, 512, 2048, 4096)) {
                currentCoroutineContext().ensureActive()
                val endOff = bSize - 1
                val cmd32 = buildNecCommandFrame(0x32, byteArrayOf(0x00, 0x00, 0x00, 0x00, ((endOff ushr 8) and 0xFF).toByte(), (endOff and 0xFF).toByte()))
                var rx = transport.transceive(
                    cmd32,
                    12, 220, "4.1 Block Probe 0x32 [${bSize}B]", stopOnCompleteNecFrame = true
                )
                if (rx.isEmpty()) {
                    hardwareResetAndResync(sync, "4.1 Block Probe 0x32 Timeout [${bSize}B]", relockOsc = false)
                    rx = transport.transceive(
                        cmd32,
                        12, 220, "4.1 Block Probe 0x32 Retry [${bSize}B]", stopOnCompleteNecFrame = true
                    )
                }
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                    blockSize = bSize
                    break
                }
            }

            // 4.2: COMPLETE CRYSTAL MATRIX FOR 0x90 (Official NEC 78K0 Mantissa+Exponent+Voltage first, then legacy 4B & 1B)
            ensureBootloaderReady(sync, "تهيئة مصفوفة التردد 0x90", relockOsc = false)
            val matrix4B = listOf(
                // Official NEC 78K0 Datasheet Format: [Freq_BCD, Freq_Dec, PowerOf10, Voltage_V] @ 5.0V (0x05)
                Triple("8.00 MHz (NEC Official 5.0V)", byteArrayOf(0x80.toByte(), 0x00, 0x06, 0x05), "8.00 MHz (5.0V)"),
                Triple("4.00 MHz (NEC Official 5.0V)", byteArrayOf(0x40, 0x00, 0x06, 0x05), "4.00 MHz (5.0V)"),
                Triple("5.00 MHz (NEC Official 5.0V)", byteArrayOf(0x50, 0x00, 0x06, 0x05), "5.00 MHz (5.0V)"),
                Triple("10.00 MHz (NEC Official 5.0V)", byteArrayOf(0x10, 0x00, 0x07, 0x05), "10.00 MHz (5.0V)"),
                Triple("12.00 MHz (NEC Official 5.0V)", byteArrayOf(0x12, 0x00, 0x07, 0x05), "12.00 MHz (5.0V)"),
                Triple("16.00 MHz (NEC Official 5.0V)", byteArrayOf(0x16, 0x00, 0x07, 0x05), "16.00 MHz (5.0V)"),
                Triple("20.00 MHz (NEC Official 5.0V)", byteArrayOf(0x20, 0x00, 0x07, 0x05), "20.00 MHz (5.0V)"),
                // Official NEC 78K0 Datasheet Format @ 3.3V (0x03)
                Triple("8.00 MHz (NEC Official 3.3V)", byteArrayOf(0x80.toByte(), 0x00, 0x06, 0x03), "8.00 MHz (3.3V)"),
                Triple("4.00 MHz (NEC Official 3.3V)", byteArrayOf(0x40, 0x00, 0x06, 0x03), "4.00 MHz (3.3V)"),
                Triple("5.00 MHz (NEC Official 3.3V)", byteArrayOf(0x50, 0x00, 0x06, 0x03), "5.00 MHz (3.3V)"),
                Triple("10.00 MHz (NEC Official 3.3V)", byteArrayOf(0x10, 0x00, 0x07, 0x03), "10.00 MHz (3.3V)"),
                Triple("12.00 MHz (NEC Official 3.3V)", byteArrayOf(0x12, 0x00, 0x07, 0x03), "12.00 MHz (3.3V)"),
                Triple("16.00 MHz (NEC Official 3.3V)", byteArrayOf(0x16, 0x00, 0x07, 0x03), "16.00 MHz (3.3V)"),
                Triple("20.00 MHz (NEC Official 3.3V)", byteArrayOf(0x20, 0x00, 0x07, 0x03), "20.00 MHz (3.3V)"),
                // Legacy fallback entries
                Triple("8 MHz (5V)", byteArrayOf(0x08, 0x00, 0x00, 0x06), "8 MHz (5V)"),
                Triple("8 MHz (Default)", byteArrayOf(0x08, 0x00, 0x00, 0x00), "8 MHz (Default)"),
                Triple("4 MHz (5V)", byteArrayOf(0x04, 0x00, 0x00, 0x06), "4 MHz (5V)"),
                Triple("5 MHz (5V)", byteArrayOf(0x05, 0x00, 0x00, 0x06), "5 MHz (5V)"),
                Triple("6 MHz (5V)", byteArrayOf(0x06, 0x00, 0x00, 0x06), "6 MHz (5V)"),
                Triple("10/16 MHz (0x10 5V)", byteArrayOf(0x10, 0x00, 0x00, 0x06), "16 MHz / 0x10 (5V)"),
                Triple("10 MHz (0x0A 5V)", byteArrayOf(0x0A, 0x00, 0x00, 0x06), "10 MHz (5V)"),
                Triple("12/18 MHz (0x12 5V)", byteArrayOf(0x12, 0x00, 0x00, 0x06), "12/18 MHz (5V)"),
                Triple("12 MHz (0x0C 5V)", byteArrayOf(0x0C, 0x00, 0x00, 0x06), "12 MHz (5V)"),
                Triple("16/22 MHz (0x16 5V)", byteArrayOf(0x16, 0x00, 0x00, 0x06), "16/22 MHz (5V)"),
                Triple("20 MHz (0x20 5V)", byteArrayOf(0x20, 0x00, 0x00, 0x06), "20 MHz (5V)"),
                // 3.3V legacy variants (0x03)
                Triple("8 MHz (3.3V)", byteArrayOf(0x08, 0x00, 0x00, 0x03), "8 MHz (3.3V)"),
                Triple("4 MHz (3.3V)", byteArrayOf(0x04, 0x00, 0x00, 0x03), "4 MHz (3.3V)"),
                Triple("5 MHz (3.3V)", byteArrayOf(0x05, 0x00, 0x00, 0x03), "5 MHz (3.3V)"),
                Triple("6 MHz (3.3V)", byteArrayOf(0x06, 0x00, 0x00, 0x03), "6 MHz (3.3V)"),
                Triple("10/16 MHz (0x10 3.3V)", byteArrayOf(0x10, 0x00, 0x00, 0x03), "16 MHz / 0x10 (3.3V)"),
                Triple("10 MHz (0x0A 3.3V)", byteArrayOf(0x0A, 0x00, 0x00, 0x03), "10 MHz (3.3V)"),
                Triple("12/18 MHz (0x12 3.3V)", byteArrayOf(0x12, 0x00, 0x00, 0x03), "12/18 MHz (3.3V)"),
                Triple("12 MHz (0x0C 3.3V)", byteArrayOf(0x0C, 0x00, 0x00, 0x03), "12 MHz (3.3V)"),
                Triple("16/22 MHz (0x16 3.3V)", byteArrayOf(0x16, 0x00, 0x00, 0x03), "16/22 MHz (3.3V)"),
                Triple("20 MHz (0x20 3.3V)", byteArrayOf(0x20, 0x00, 0x00, 0x03), "20 MHz (3.3V)")
            )

            val matrixLogs = mutableListOf<String>()
            for ((label, payload, mhzLabel) in matrix4B) {
                currentCoroutineContext().ensureActive()
                var rx = transport.transceive(
                    buildNecCommandFrame(0x90, payload),
                    12, 200, "4.2 Crystal 4B [$label -> ${payload.toHexStr()}]", stopOnCompleteNecFrame = true
                )
                var sts = parseNecStatus(rx)
                if (rx.isEmpty() || sts == null) {
                    hardwareResetAndResync(sync, "0x90 بدون رد بعد ${payload.toHexStr()}", relockOsc = false)
                    rx = transport.transceive(
                        buildNecCommandFrame(0x90, payload),
                        12, 200, "4.2 Crystal 4B Retry [${payload.toHexStr()}]", stopOnCompleteNecFrame = true
                    )
                    sts = parseNecStatus(rx)
                }
                matrixLogs.add("${payload.toHexStr()}=${formatByte(sts)}")
                if (sts == ACK_NEC) {
                    winningOscPayload = payload
                    activeWinningOscPayload = payload
                    winningOscText = "0x90 [${payload.toHexStr()}] -> ACK 0x06"
                    detectedCrystal = mhzLabel
                    appendLog(ProberLogDirection.SUCCESS, "✅ مصفوفة الكريستالات: نجح التردد $mhzLabel (${payload.toHexStr()}) بـ ACK 0x06!")
                    break
                }
            }

            if (winningOscPayload == null) {
                ensureBootloaderReady(sync, "استئناف مصفوفة 0x90 (1-بايت)", relockOsc = false)
                val matrix1B = listOf(
                    0x00 to "Internal Oscillator (0x00)",
                    0x04 to "4 MHz (1B)",
                    0x05 to "5 MHz (1B)",
                    0x06 to "6 MHz (1B)",
                    0x08 to "8 MHz (1B)",
                    0x0A to "10 MHz (1B)",
                    0x0C to "12 MHz (1B)",
                    0x10 to "16 MHz (1B)",
                    0x12 to "18 MHz (1B)",
                    0x14 to "20 MHz (1B)"
                )
                for ((code, mhzLabel) in matrix1B) {
                    currentCoroutineContext().ensureActive()
                    val payload = byteArrayOf(code.toByte())
                    var rx = transport.transceive(
                        buildNecCommandFrame(0x90, payload),
                        12, 200, "4.2 Crystal 1B [0x90 0x%02X]".format(code), stopOnCompleteNecFrame = true
                    )
                    var sts = parseNecStatus(rx)
                    if (rx.isEmpty() || sts == null) {
                        hardwareResetAndResync(sync, "0x90 (1B) بدون رد بعد 0x%02X".format(code), relockOsc = false)
                        rx = transport.transceive(
                            buildNecCommandFrame(0x90, payload),
                            12, 200, "4.2 Crystal 1B Retry [0x90 0x%02X]".format(code), stopOnCompleteNecFrame = true
                        )
                        sts = parseNecStatus(rx)
                    }
                    matrixLogs.add("0x%02X=%s".format(code, formatByte(sts)))
                    if (sts == ACK_NEC) {
                        winningOscPayload = payload
                        activeWinningOscPayload = payload
                        winningOscText = "0x90 [0x%02X] -> ACK 0x06".format(code)
                        detectedCrystal = mhzLabel
                        appendLog(ProberLogDirection.SUCCESS, "✅ مصفوفة الكريستالات (1-بايت): نجح $mhzLabel بـ ACK 0x06!")
                        break
                    }
                }
            }

            oscProbeStr = matrixLogs.joinToString(" | ")
            oscRule = if (winningOscPayload != null) {
                "Send 0x90 with payload [${winningOscPayload.toHexStr()}] ($detectedCrystal) before 0x9A baud switch or Flash Erase/Write; MCU confirmed ACK 0x06."
            } else {
                "0x90 crystal matrix probed (${matrixLogs.size} entries) without 0x06 ACK — target uses locked internal clock."
            }

            // 4.3: Multi-Stage Baud Rate State Machine (0x9A across 19200..115200 + 0x00 Ping verify)
            ensureBootloaderReady(sync, "تهيئة تبديل السرعة 0x9A", relockOsc = true)
            val baudTargets = listOf(19200 to 0x01, 38400 to 0x02, 57600 to 0x03, 115200 to 0x04)
            val switchSamples = mutableListOf<Int>()
            for ((targetBaud, code) in baudTargets) {
                currentCoroutineContext().ensureActive()
                val tStart = System.currentTimeMillis()
                var swRx = transport.transceive(
                    buildNecCommandFrame(0x9A, byteArrayOf(code.toByte())),
                    12, 220, "4.3 Baud 0x9A [$targetBaud code=0x%02X]".format(code), stopOnCompleteNecFrame = true
                )
                if (swRx.isEmpty()) {
                    hardwareResetAndResync(sync, "4.3 Baud 0x9A Timeout [$targetBaud]", relockOsc = true)
                    swRx = transport.transceive(
                        buildNecCommandFrame(0x9A, byteArrayOf(code.toByte())),
                        12, 220, "4.3 Baud 0x9A Retry [$targetBaud code=0x%02X]".format(code), stopOnCompleteNecFrame = true
                    )
                }
                if (parseNecStatus(swRx) == ACK_NEC) {
                    transport.configureUart(targetBaud, evenParity = sync.evenParity)
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
                    hardwareResetAndResync(sync, "العودة للسرعة الأساسية بعد اختبار $targetBaud bps", relockOsc = true)
                }
            }
            if (switchSamples.isNotEmpty()) measuredBaudSwitchMs = switchSamples.average().toInt().coerceIn(10, 120)
            baudRule = "Connect at ${sync.baudRate} ${sync.parityMode} -> lock 0x90 ($winningOscText) -> send 0x9A [code] -> wait ${measuredBaudSwitchMs}ms -> reconfigure host UART -> confirm 0x00 Ping -> Supported: ${supportedBauds.distinct().sorted().joinToString("/")} bps"

            // 4.4: STX (0x02) Frame Probe (256B LEN=0x00 & 128B LEN=0x80 with ETB/ETX) + 6-Point Timing Map
            ensureBootloaderReady(sync, "تهيئة اختبار إطارات STX", relockOsc = true)
            val tCmd256 = System.currentTimeMillis()
            var h256 = transport.transceive(
                buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())),
                12, 240, "4.4 STX 256B Header [0x13]", stopOnCompleteNecFrame = true
            )
            if (h256.isEmpty()) {
                hardwareResetAndResync(sync, "4.4 STX 256B Header Timeout", relockOsc = true)
                h256 = transport.transceive(
                    buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())),
                    12, 240, "4.4 STX 256B Header Retry [0x13]", stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(h256) == ACK_NEC) {
                measuredCmdToDataMs = (System.currentTimeMillis() - tCmd256).toInt().coerceIn(5, 80)
                delay(5)
                val tStx = System.currentTimeMillis()
                val rx256 = transport.transceive(
                    buildNecDataFrame(ByteArray(256) { 0xFF.toByte() }, isLast = true),
                    16, 480, "4.4 STX 256B Frame (LEN=0x00, ETX=0x03)", stopOnCompleteNecFrame = true, paced64B = true
                )
                if (rx256.isEmpty()) {
                    hardwareResetAndResync(sync, "4.4 STX 256B Data Timeout", relockOsc = true)
                }
                val sts256 = effectiveNecStatus(rx256)
                if (sts256 == ACK_NEC || sts256 == STS_VERIFY_ERROR || sts256 == STS_VERIFY_ERROR_1C) {
                    supportedStxPages.add(256)
                    pageSize = 256
                    measuredFrameToFrameMs = (System.currentTimeMillis() - tStx).toInt().coerceIn(8, 120)
                }
            }

            var h128 = transport.transceive(
                buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x7F.toByte())),
                12, 240, "4.4 STX 128B Header [0x13]", stopOnCompleteNecFrame = true
            )
            if (h128.isEmpty()) {
                hardwareResetAndResync(sync, "4.4 STX 128B Header Timeout", relockOsc = true)
                h128 = transport.transceive(
                    buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x7F.toByte())),
                    12, 240, "4.4 STX 128B Header Retry [0x13]", stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(h128) == ACK_NEC) {
                val rx128 = transport.transceive(
                    buildNecDataFrame(ByteArray(128) { 0xFF.toByte() }, isLast = true),
                    16, 380, "4.4 STX 128B Frame (LEN=0x80, ETX=0x03)", stopOnCompleteNecFrame = true, paced64B = true
                )
                if (rx128.isEmpty()) {
                    hardwareResetAndResync(sync, "4.4 STX 128B Data Timeout", relockOsc = true)
                }
                val sts128 = effectiveNecStatus(rx128)
                if (sts128 == ACK_NEC || sts128 == STS_VERIFY_ERROR || sts128 == STS_VERIFY_ERROR_1C) {
                    supportedStxPages.add(128)
                    if (256 !in supportedStxPages) pageSize = 128
                }
            } else if (parseNecStatus(h128) == STS_PARAM_ERROR) {
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
                        if (rxEtx.isEmpty()) {
                            hardwareResetAndResync(sync, "4.4 STX Dual-128B Timeout", relockOsc = true)
                        }
                        val stsLast = effectiveNecStatus(rxEtx)
                        if (stsLast == ACK_NEC || stsLast == STS_VERIFY_ERROR || stsLast == STS_VERIFY_ERROR_1C) {
                            supportedStxPages.add(128)
                        }
                    } else {
                        hardwareResetAndResync(sync, "4.4 STX ETB Recovery", relockOsc = true)
                    }
                }
            }
        } else if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val isUsbdm = (sync.framing == DetectedFraming.USBDM_BDM)
            flashRule = "HCS08 SDIDH/SDIDL (0x1806..0x1807 via READ_MEM 0x21) -> $detectedSdidHex (${base.inferredModel}); HCS08 Flash via FCMD 0x41 (Mass Erase) / 0x20 (Byte Program)"
            oscRule = if (isUsbdm) {
                val speedDesc = if (detectedUsbdmSpeedHz > 0) "${detectedUsbdmSpeedHz / 1000} kHz" else "Auto"
                "USBDM CONNECT [0x0F] + SYNC [0x10] auto-measured BDM bus clock (SYNC=$detectedUsbdmSyncBytes -> $speedDesc)."
            } else {
                "Single-Wire BDM auto-timed at 1,500,000 bps (8N1) on BKGD."
            }
            detectedCrystal = if (isUsbdm) {
                if (detectedUsbdmSyncBytes.isNotBlank()) "SYNC=$detectedUsbdmSyncBytes (${detectedUsbdmSpeedHz / 1000} kHz)"
                else if (detectedUsbdmSpeedHz > 0) "${detectedUsbdmSpeedHz / 1000} kHz BDM Bus"
                else "USBDM Auto BDM"
            } else "1.5 Mbps BDM Sync"
            winningOscText = if (isUsbdm) "USBDM Sync [0x10]" else "1.5 Mbps BKGD Sync"
            baudRule = if (isUsbdm) "Native USBDM USB Bulk (EP1 OUT / EP2 IN)" else "FT232 Single-Wire BDM locked at 1,500,000 bps (8N1)"
            flashEndHex = when {
                "60" in base.inferredModel -> "0x00FFFF (60 KB HCS08)"
                "32" in base.inferredModel -> "0x00FFFF (32 KB HCS08)"
                "16" in base.inferredModel -> "0x00FFFF (16 KB HCS08)"
                else -> "0x00FFFF (HCS08 Flash)"
            }
            pageSize = 256
            blockSize = 512
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            flashRule = "Samsung S3F/S3P ID Query -> ${detectedSamsungIdHex.ifBlank { sync.firstReplyHex }}; Flash 0x0000..0x7FFF"
            oscRule = "Samsung ISP uses target crystal/internal RC synced on 0xAA 0x55 / 0x80."
            detectedCrystal = "Internal / Target XTAL"
            winningOscText = "Samsung ISP Sync"
            baudRule = "Locked at ${sync.baudRate} ${sync.parityMode} (9600/38400 bps)"
            flashEndHex = "0x007FFF (32 KB Typical)"
            pageSize = 256
            blockSize = 1024
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            flashRule = "STM32 GetID (0x02) -> map PID to Flash Size register/table; Memory Read/Write 256B chunks"
            oscRule = "STM32 internal HSI 8MHz auto-detected by 0x7F sync byte"
            detectedCrystal = "Internal HSI 8 MHz"
            winningOscText = "Auto-Baud 0x7F"
            baudRule = "Auto-baud locked on 0x7F at ${sync.baudRate} ${sync.parityMode}"
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
            winningOscillatorPayload = winningOscText,
            detectedCrystalMhz = detectedCrystal,
            baudStateMachineRule = baudRule,
            supportedStxPageSizes = supportedStxPages.distinct().sorted(),
            pageSizeBytes = pageSize,
            blockSizeBytes = blockSize,
            flashEndAddressHex = flashEndHex,
            safeDelayMs = avgDelay,
            timingMap = finalTiming
        ) to winningOscPayload
    }

    // ==================== STAGE 5: SEQUENTIAL INTERACTIVE MODAL DIALOG PROBES ====================

    private suspend fun requestStepConfirmation(
        prompt: Stage5ConfirmPrompt,
        stepId: String = "stage5_safety_gate"
    ): Boolean {
        currentCoroutineContext().ensureActive()
        val deferred = CompletableDeferred<Boolean>()
        pendingConfirmDeferred = deferred
        _state.value = _state.value.copy(
            phase = ProberStagePhase.AWAITING_STEP_CONFIRMATION,
            pendingConfirmDialog = prompt,
            stageBanner = "⚠️ بانتظار موافقة المشرف (${prompt.stepIndex}/${prompt.totalSteps}): ${prompt.stepTitle}"
        )
        updateStep(
            stepId,
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

        suspend fun finalizeAndComplete(
            bannerMsg: String,
            stepBadge: ChecklistStepStatus,
            stepDetail: String,
            userCancelled: Boolean
        ) {
            workingSummary = workingSummary.copy(
                stage5Results = results.toList(),
                advancedSchoolsNotes = notesBuilder.toString().trim().ifBlank { stepDetail }
            )
            updateStep("stage5_safety_gate", stepBadge, stepDetail)
            updateSummaryAndBlueprint(workingSummary)
            hardwareResetAndResync(sync, "تهيئة وإعادة تزامن للمرحلة 6 بعد المرحلة 5", relockOsc = true)
            workingSummary = runStage6UniversalLiveReadProof(sync, workingSummary, userCancelledStage5 = userCancelled)
            updateSummaryAndBlueprint(workingSummary)
            val finalBanner = if (workingSummary.liveReadProofPassed) {
                "$bannerMsg | ✅ المرحلة 6: ${workingSummary.liveReadProofFirst32Hex.take(23)}..."
            } else bannerMsg
            _state.value = _state.value.copy(
                phase = ProberStagePhase.COMPLETED,
                stageBanner = finalBanner,
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
            notesBuilder.appendLine("توقف المشرف بأمان قبل الخطوة 5.1 — تم حفظ البطاقات الثلاث كاملة.")
            finalizeAndComplete(
                bannerMsg = "تم التوقف بأمان عند بوابة المرحلة 5 وإكمال المرحلة 6",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "تم التوقف بأمان والاكتفاء بنتائج المراحل 1..4",
                userCancelled = true
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.1: تصفير Block 0 وحساب فرق البصمة..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.1: قراءة البصمة 0xB0 ثم مسح Block 0...")
        ensureBootloaderReady(sync, "تهيئة الخطوة 5.1", relockOsc = true)

        val step1Result = executeStage5Step1(sync)
        results.add(step1Result)
        notesBuilder.appendLine(step1Result.detail)
        appendLog(if (step1Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step1Result.detail)
        workingSummary = workingSummary.copy(stage5Results = results.toList(), advancedSchoolsNotes = notesBuilder.toString().trim())
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
                bannerMsg = "تم إكمال الخطوة 5.1 والتوقف بأمان قبل 5.2 وإكمال المرحلة 6",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "اكتملت 5.1 (${step1Result.durationMs}ms) وتوقف المشرف قبل 5.2",
                userCancelled = true
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.2: اختبار الكتابة الجزئية 0x40 STX..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.2: الكتابة الجزئية 0x40 + STX 0xFE...")
        ensureBootloaderReady(sync, "تهيئة الخطوة 5.2", relockOsc = true)

        val step2Result = executeStage5Step2(sync)
        results.add(step2Result)
        notesBuilder.appendLine(step2Result.detail)
        appendLog(if (step2Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step2Result.detail)
        workingSummary = workingSummary.copy(stage5Results = results.toList(), advancedSchoolsNotes = notesBuilder.toString().trim())
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
            notesBuilder.appendLine("توقف المشرف بأمان قبل الخطوة 5.3.")
            finalizeAndComplete(
                bannerMsg = "تم إكمال 5.1 و 5.2 والتوقف بأمان قبل المسح الكلي 5.3 وإكمال المرحلة 6",
                stepBadge = ChecklistStepStatus.PASSED,
                stepDetail = "اكتملت 5.1 و 5.2 وتوقف المشرف بأمان قبل المسح الكلي 0x20",
                userCancelled = true
            )
            return
        }

        _state.value = _state.value.copy(
            phase = ProberStagePhase.RUNNING_STAGE5_STEP,
            stageBanner = "جاري تنفيذ الخطوة 5.3: المسح الكلي للرقاقة 0x20 وفك القفل..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "جاري تنفيذ 5.3: المسح الكلي 0x20 وقياس eraseWriteBusyWaitMs...")
        ensureBootloaderReady(sync, "تهيئة الخطوة 5.3", relockOsc = true)

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
            bannerMsg = "اكتملت جميع المراحل (1 إلى 6) وتوليد البطاقات الثلاث بنجاح!",
            stepBadge = if (passedCount > 0) ChecklistStepStatus.PASSED else ChecklistStepStatus.FAILED,
            stepDetail = "تم تنفيذ 3 خطوات تفاعلية بالمرحلة 5 (نجح $passedCount/3) | EraseWait=${workingSummary.timingMap.eraseWriteBusyWaitMs}ms",
            userCancelled = false
        )
    }

    private suspend fun executeStage5Step1(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val blockEnd = ((_state.value.blueprintSummary.blockSizeBytes ?: 1024) - 1).coerceAtLeast(0x3FF)
            val b0Range = addrRangeBytes(0x000000, blockEnd)
            var csBeforeRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS Before [0xB0]", stopOnCompleteNecFrame = true)
            if (csBeforeRx.isEmpty()) {
                hardwareResetAndResync(sync, "5.1 0xB0 Before Timeout", relockOsc = true)
                csBeforeRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS Before Retry [0xB0]", stopOnCompleteNecFrame = true)
            }
            val csBefore = parseChecksum16(csBeforeRx) ?: run {
                if (parseNecStatus(csBeforeRx) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 300, true)) else null
            }

            var eraseRx = transport.transceive(buildNecCommandFrame(0x22, b0Range), 12, 1300, "5.1 Block-0 Erase [0x22]", stopOnCompleteNecFrame = true)
            if (eraseRx.isEmpty()) {
                hardwareResetAndResync(sync, "5.1 0x22 Erase Timeout", relockOsc = true)
                eraseRx = transport.transceive(buildNecCommandFrame(0x22, b0Range), 12, 1300, "5.1 Block-0 Erase Retry [0x22]", stopOnCompleteNecFrame = true)
            }
            val eraseSts = parseNecStatus(eraseRx)
            var csAfter: Int? = null
            if (eraseSts == ACK_NEC) {
                delay(20)
                var csAfterRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS After [0xB0]", stopOnCompleteNecFrame = true)
                if (csAfterRx.isEmpty()) {
                    hardwareResetAndResync(sync, "5.1 0xB0 After Timeout", relockOsc = true)
                    csAfterRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS After Retry [0xB0]", stopOnCompleteNecFrame = true)
                }
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
        } else if (sync.framing == DetectedFraming.USBDM_BDM) {
            // HCS08 Page Erase via FSTAT (0x1825) & FCMD (0x1826 = 0x40 Page Erase) at 0xC000
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT [02 14]", logTxRx = false)
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 250, "USBDM Clear FSTAT [0x20]")
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0xC0.toByte(), 0x00, 0xFF.toByte()), 8, 250, "USBDM Dummy Write [0xC000]")
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x40), 8, 250, "USBDM FCMD Page Erase [0x40]")
            val launch = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 350, "USBDM Launch Erase")
            delay(35)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = launch.isNotEmpty() && (launch[0].toInt() and 0xFF) == 0x00
            val detail = if (ok) "✅ 5.1 HCS08 Page Erase (FCMD 0x40): نجح في ${dt}ms" else "❌ 5.1 HCS08 Page Erase: فشل (FSEC محمي)"
            return Stage5StepResult("5.1 HCS08 FCMD Page Erase (0x40)", ok, true, dt, detail)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val e0 = transport.transceive(byteArrayOf(0x43, 0xBC.toByte()), 6, 450, "5.1 STM32 Erase [0x43]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = e0.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.1 مسح STM32 Sector-0: نجح (${dt}ms)" else "❌ 5.1 مسح STM32 Sector-0: فشل (RDP محمي)"
            return Stage5StepResult("5.1 Sector-0 Erase (0x43)", ok, true, dt, detail)
        } else {
            val b0Erase = transport.transceive(byteArrayOf(0x20, 0x00, 0x00, 0xD0.toByte()), 8, 550, "5.1 Block Erase [0x20]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = b0Erase.isNotEmpty()
            val detail = if (ok) "✅ 5.1 مسح Block-0: نجح في ${dt}ms (${b0Erase.toHexStr()})" else "❌ 5.1 مسح Block-0: فشل"
            return Stage5StepResult("5.1 Block Erase", ok, true, dt, detail)
        }
    }

    private suspend fun executeStage5Step2(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val blockSize = (_state.value.blueprintSummary.blockSizeBytes ?: 1024).coerceAtLeast(256)
            val pageSize = (_state.value.blueprintSummary.pageSizeBytes ?: 256).coerceIn(128, 256)
            val b0Range = addrRangeBytes(0x000000, blockSize - 1)
            val partialRange = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x03)

            var partHdrRx = transport.transceive(buildNecCommandFrame(0x40, partialRange), 12, 420, "5.2 Partial Write 4B [0x40]", stopOnCompleteNecFrame = true)
            if (partHdrRx.isEmpty()) {
                hardwareResetAndResync(sync, "5.2 0x40 4B Header Timeout", relockOsc = true)
                partHdrRx = transport.transceive(buildNecCommandFrame(0x40, partialRange), 12, 420, "5.2 Partial Write 4B Retry [0x40]", stopOnCompleteNecFrame = true)
            }
            var partRangeUsed = "4B (0x0000..0x0003)"
            var partSts = parseNecStatus(partHdrRx)
            var dataOverwriteAck = false

            if (partSts == ACK_NEC) {
                val testSlice = ByteArray(4) { 0xFF.toByte() }.also { it[0] = 0xFE.toByte() }
                val dataRx = transport.transceive(
                    buildNecDataFrame(testSlice, isLast = true),
                    12, 700, "5.2 STX 4B Bit-Clear 0xFE [0x40]",
                    stopOnCompleteNecFrame = true
                )
                if (dataRx.isEmpty()) {
                    hardwareResetAndResync(sync, "5.2 0x40 4B STX Timeout", relockOsc = true)
                } else {
                    val dSts1 = parseNecStatus(dataRx)
                    val dSts2 = parseNecSecondStatus(dataRx)
                    dataOverwriteAck = (dSts1 == ACK_NEC && (dSts2 == null || dSts2 == ACK_NEC))
                }
            }

            if (!dataOverwriteAck) {
                if (partSts == ACK_NEC) {
                    hardwareResetAndResync(sync, "5.2 التحول لكتابة Block-0 الكامل 0x40", relockOsc = true)
                }
                partHdrRx = transport.transceive(buildNecCommandFrame(0x40, b0Range), 12, 450, "5.2 Write Block-0 [0x40]", stopOnCompleteNecFrame = true)
                if (partHdrRx.isEmpty()) {
                    hardwareResetAndResync(sync, "5.2 0x40 Block-0 Header Timeout", relockOsc = true)
                    partHdrRx = transport.transceive(buildNecCommandFrame(0x40, b0Range), 12, 450, "5.2 Write Block-0 Retry [0x40]", stopOnCompleteNecFrame = true)
                }
                partRangeUsed = "${blockSize}B Block-0 (0x0000..0x%04X)".format(blockSize - 1)
                partSts = parseNecStatus(partHdrRx)
                if (partSts == ACK_NEC) {
                    val pageCount = (blockSize / pageSize).coerceAtLeast(1)
                    var allOk = true
                    for (p in 0 until pageCount) {
                        val isLast = (p == pageCount - 1)
                        val slice = ByteArray(pageSize) { 0xFF.toByte() }.also { if (p == 0) it[0] = 0xFE.toByte() }
                        val stxFrame = buildNecDataFrame(slice, isLast = isLast)
                        val dataRx = transport.transceive(
                            stxFrame,
                            12, if (isLast) 800 else 450,
                            "5.2 STX Page $p/${pageCount - 1} [0x40]",
                            stopOnCompleteNecFrame = true,
                            paced64B = stxFrame.size > 64
                        )
                        if (dataRx.isEmpty()) {
                            hardwareResetAndResync(sync, "5.2 STX Page $p Timeout", relockOsc = true)
                            allOk = false
                            break
                        }
                        val dSts1 = parseNecStatus(dataRx)
                        val dSts2 = parseNecSecondStatus(dataRx)
                        if (dSts1 != ACK_NEC || (dSts2 != null && dSts2 != ACK_NEC)) {
                            allOk = false
                            break
                        }
                    }
                    dataOverwriteAck = allOk
                }
            }

            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val passed = (partSts == ACK_NEC && dataOverwriteAck)
            val detail = when {
                passed -> "✅ 5.2 الكتابة الجزئية (0x40 + STX 0xFE): نجحت في ${dt}ms ($partRangeUsed)"
                partSts == ACK_NEC -> "❌ 5.2 الكتابة الجزئية: قبل 0x40 لكن رُفض إطار STX ($partRangeUsed, ${dt}ms)"
                else -> "❌ 5.2 الكتابة الجزئية: فشلت (0x40=${formatByte(partSts)}, ${dt}ms)"
            }
            return Stage5StepResult("5.2 Partial Bit Overwrite (0x40 STX 0xFE)", passed, true, dt, detail)
        } else if (sync.framing == DetectedFraming.USBDM_BDM) {
            val ok = usbdmProgramBytes(0x00FF00, byteArrayOf(0xFE.toByte()))
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val detail = if (ok) "✅ 5.2 HCS08 Byte Program (FCMD 0x20): نجح في ${dt}ms" else "❌ 5.2 HCS08 Byte Program: فشل"
            return Stage5StepResult("5.2 HCS08 FCMD Byte Program (0x20)", ok, true, dt, detail)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val wr = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 6, 400, "5.2 STM32 Write [0x31]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wr.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.2 STM32 Write 0x31: ACK في ${dt}ms" else "❌ 5.2 STM32 Write 0x31: مرفوض"
            return Stage5StepResult("5.2 STM32 Write 0x31", ok, true, dt, detail)
        } else {
            val wrHdr = transport.transceive(byteArrayOf(0x41, 0x00, 0x40, 0x00), 8, 350, "5.2 Page Write [0x41]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wrHdr.isNotEmpty()
            val detail = if (ok) "✅ 5.2 Page Write 0x41: نجح في ${dt}ms" else "❌ 5.2 Page Write 0x41: فشل"
            return Stage5StepResult("5.2 Page Write 0x41", ok, true, dt, detail)
        }
    }

    private suspend fun executeStage5Step3(sync: SyncLock): Stage5StepResult {
        val t0 = System.currentTimeMillis()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            var c5Rx = transport.transceive(buildNecCommandFrame(0xC5), 16, 300, "5.3 Security Query [0xC5]", stopOnCompleteNecFrame = true)
            if (c5Rx.isEmpty()) {
                hardwareResetAndResync(sync, "5.3 0xC5 Timeout", relockOsc = true)
                c5Rx = transport.transceive(buildNecCommandFrame(0xC5), 16, 300, "5.3 Security Query Retry [0xC5]", stopOnCompleteNecFrame = true)
            }
            val c5Ok = parseNecStatus(c5Rx) == ACK_NEC
            var tErase = System.currentTimeMillis()
            var chipEraseRx = transport.transceive(buildNecCommandFrame(0x20), 12, 2200, "5.3 Full Chip Erase [0x20]", stopOnCompleteNecFrame = true)
            if (chipEraseRx.isEmpty()) {
                hardwareResetAndResync(sync, "5.3 0x20 Chip Erase Timeout", relockOsc = true)
                tErase = System.currentTimeMillis()
                chipEraseRx = transport.transceive(buildNecCommandFrame(0x20), 12, 2200, "5.3 Full Chip Erase Retry [0x20]", stopOnCompleteNecFrame = true)
            }
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
        } else if (sync.framing == DetectedFraming.USBDM_BDM) {
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT [02 14]", logTxRx = false)
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 250, "USBDM Mass Erase FSTAT [0x20]")
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0xC0.toByte(), 0x00, 0xFF.toByte()), 8, 250, "USBDM Mass Erase Latch")
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x41), 8, 250, "USBDM FCMD Mass Erase [0x41]")
            val launch = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 600, "USBDM Launch Mass Erase")
            delay(220)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = launch.isNotEmpty() && (launch[0].toInt() and 0xFF) == 0x00
            val detail = if (ok) "✅ 5.3 HCS08 Mass Erase (FCMD 0x41) وفك الحماية: نجح في ${dt}ms" else "❌ 5.3 HCS08 Mass Erase: فشل"
            return Stage5StepResult("5.3 HCS08 Mass Erase (FCMD 0x41)", ok, true, dt, detail)
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
            val detail = if (ok) "✅ 5.3 ID Bypass: نجح في ${dt}ms (SRD=${sr.toHexStr()})" else "❌ 5.3 ID Bypass: مقفول بالـ ID"
            return Stage5StepResult("5.3 ID Bypass", ok, true, dt, detail)
        }
    }

    // ==================== STAGE 6: UNIVERSAL LIVE READ PROOF (DATA & BLANK 0xFF) ====================

    private suspend fun runStage6UniversalLiveReadProof(
        sync: SyncLock,
        summary: ProberBlueprintSummary,
        userCancelledStage5: Boolean
    ): ProberBlueprintSummary {
        _state.value = _state.value.copy(
            stageBanner = "جاري تنفيذ المرحلة 6: إثبات صحة السحب الفعلي (Universal Live Read Proof)..."
        )
        updateStep(
            "stage6_live_read_proof",
            ChecklistStepStatus.RUNNING,
            "فحص Page 0 (256B) والتحقق من عدم وجود Echo ثم مطابقة الذاكرة..."
        )
        ensureBootloaderReady(sync, "تهيئة المرحلة 6", relockOsc = true)

        val pageSize = if (sync.framing == DetectedFraming.USBDM_BDM) 256 else (summary.pageSizeBytes ?: 256).coerceIn(64, 256)
        val blockSize = (summary.blockSizeBytes ?: 1024).coerceAtLeast(pageSize)
        var page0Start = when (sync.framing) {
            DetectedFraming.STM32_ACK -> 0x08000000
            DetectedFraming.USBDM_BDM, DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> when {
                "60" in summary.inferredModel || "32" in summary.inferredModel -> 0x008000
                "8" in summary.inferredModel && "16" !in summary.inferredModel && "128" !in summary.inferredModel -> 0x00E000
                else -> 0x00C000
            }
            else -> 0x000000
        }
        val flashEndAddr = parseFlashEndAddress(summary, sync)

        // Try extracting Page 0 using the discovered Read method (USBDM 0x21 256B / Direct Read / STM32 0x11 / 1B 0xB0)
        var (page0Bytes, readMethodUsed, txEchoSample) = readFlashPageForProof(sync, summary, page0Start, pageSize)
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val needsAltAddr = page0Bytes == null || page0Bytes.isEmpty() ||
                page0Bytes.all { (it.toInt() and 0xFF) == 0xFF } ||
                page0Bytes.all { (it.toInt() and 0xFF) == 0x00 }
            if (needsAltAddr) {
                for (altAddr in listOf(0x00C000, 0x008000, 0x00E000)) {
                    if (altAddr == page0Start) continue
                    val (altBytes, altMethod, altEcho) = readFlashPageForProof(sync, summary, altAddr, pageSize)
                    if (altBytes != null && altBytes.size == pageSize) {
                        val altBlank = altBytes.all { (it.toInt() and 0xFF) == 0xFF } || altBytes.all { (it.toInt() and 0xFF) == 0x00 }
                        if (!altBlank || page0Bytes == null) {
                            page0Start = altAddr
                            page0Bytes = altBytes
                            readMethodUsed = altMethod
                            txEchoSample = altEcho
                            if (!altBlank) break
                        }
                    }
                }
            }
        }

        var isChipBlankOrZero = false
        var detectedHasRealData = false

        if (page0Bytes != null && page0Bytes.isNotEmpty()) {
            // Confirm extracted bytes are NOT UART Echo of TX command frame
            if (isEchoPayload(page0Bytes, txEchoSample)) {
                val msg = "❌ فشل إثبات السحب (Echo Loopback): البايتات المستقبلة مطابقة لإطار الإرسال TX وليست بيانات المعالج."
                appendLog(ProberLogDirection.ERROR, msg)
                updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, msg)
                return summary.copy(
                    liveReadProofPassed = false,
                    liveReadProofMode = "Echo Rejection Failed ($readMethodUsed)",
                    liveReadProofStatus = msg,
                    liveReadProofFirst32Hex = page0Bytes.take(32).toByteArray().toHexStr()
                )
            }

            val ffCount = page0Bytes.count { (it.toInt() and 0xFF) == 0xFF }
            val zeroCount = page0Bytes.count { (it.toInt() and 0xFF) == 0x00 }
            val dummyThreshold = (page0Bytes.size * 99) / 100
            if (ffCount >= dummyThreshold || zeroCount >= dummyThreshold) {
                isChipBlankOrZero = true
                appendLog(
                    ProberLogDirection.WARN,
                    "⚠️ المرحلة 6: العينة المسحوبة من Page 0 (${page0Bytes.size}B) فارغة/متشابهة (${if (ffCount >= dummyThreshold) "0xFF" else "0x00"} >= 99%). الانتقال للحالة الثانية (الحلقة المغلقة Write-Read Proof)..."
                )
            } else {
                detectedHasRealData = true
            }
        } else if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            // Locked bootloader (no direct Read / no 1B 0xB0): probe blank vs non-blank safely using 0x32 & 0x13
            var b0Check = transport.transceive(
                buildNecCommandFrame(0x32, addrRangeBytes(0x000000, pageSize - 1)),
                12, 280, "6.0 Blank Check [0x32 Page 0]", stopOnCompleteNecFrame = true
            )
            if (b0Check.isEmpty()) {
                hardwareResetAndResync(sync, "6.0 Blank Check 0x32 Timeout", relockOsc = true)
                b0Check = transport.transceive(
                    buildNecCommandFrame(0x32, addrRangeBytes(0x000000, pageSize - 1)),
                    12, 280, "6.0 Blank Check Retry [0x32 Page 0]", stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(b0Check) == STS_PARAM_ERROR) {
                b0Check = transport.transceive(
                    buildNecCommandFrame(0x32, addrRangeBytes(0x000000, blockSize - 1)),
                    12, 280, "6.0 Blank Check Block-0 [0x32]", stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(b0Check) == ACK_NEC) {
                isChipBlankOrZero = true
            } else {
                var hdr = transport.transceive(
                    buildNecCommandFrame(0x13, addrRangeBytes(0x000000, pageSize - 1)),
                    12, 280, "6.0 Verify 0xFF Probe [0x13 Page 0]", stopOnCompleteNecFrame = true
                )
                if (hdr.isEmpty()) {
                    hardwareResetAndResync(sync, "6.0 Verify 0x13 Header Timeout", relockOsc = true)
                    hdr = transport.transceive(
                        buildNecCommandFrame(0x13, addrRangeBytes(0x000000, pageSize - 1)),
                        12, 280, "6.0 Verify 0xFF Probe Retry [0x13 Page 0]", stopOnCompleteNecFrame = true
                    )
                }
                if (parseNecStatus(hdr) == ACK_NEC) {
                    val vRx = transport.transceive(
                        buildNecDataFrame(ByteArray(pageSize) { 0xFF.toByte() }, isLast = true),
                        16, 480, "6.0 Verify All-0xFF [0x13]", stopOnCompleteNecFrame = true, paced64B = true
                    )
                    if (vRx.isEmpty()) {
                        hardwareResetAndResync(sync, "6.0 Verify All-0xFF STX Timeout", relockOsc = true)
                    } else if (effectiveNecStatus(vRx) == ACK_NEC) {
                        isChipBlankOrZero = true
                    }
                }
            }
        }

        // -------------------- CASE 1: CHIP HAS REAL DATA --------------------
        if (detectedHasRealData && page0Bytes != null) {
            val first32Hex = page0Bytes.take(32).toByteArray().toHexStr()
            val (verifySupported, verifyPassed, verifyDetail) = verifyExtractedPageOnChip(sync, page0Start, page0Bytes)

            if (verifySupported && verifyPassed) {
                val reportMsg = "✅ إثبات السحب الحقيقي 100%: تم سحب ${page0Bytes.size}B وتأكيد تطابقها مع ذاكرة المعالج داخلياً"
                val fullDetail = "$reportMsg ($verifyDetail) | أول 32B: $first32Hex"
                appendLog(ProberLogDirection.SUCCESS, fullDetail)
                updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, fullDetail)
                return summary.copy(
                    liveReadProofPassed = true,
                    liveReadProofMode = "Case 1: Live Page 0 Extraction ($readMethodUsed) + Internal Verify (ACK + 1B Mismatch)",
                    liveReadProofStatus = reportMsg,
                    liveReadProofFirst32Hex = first32Hex
                )
            } else if (!verifySupported) {
                // Family has no separate Verify command (e.g., STM32 0x11): double-read & stability check
                val (secondRead, _, _) = readFlashPageForProof(sync, summary, page0Start, pageSize)
                if (secondRead != null && secondRead.contentEquals(page0Bytes)) {
                    val reportMsg = "✅ إثبات السحب الحقيقي 100%: تم سحب ${page0Bytes.size}B وتأكيد تطابق قراءتين متتاليتين مع عدم وجود Echo أو 0xFF"
                    val fullDetail = "$reportMsg | أول 32B: $first32Hex"
                    appendLog(ProberLogDirection.SUCCESS, fullDetail)
                    updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, fullDetail)
                    return summary.copy(
                        liveReadProofPassed = true,
                        liveReadProofMode = "Case 1: Dual Live Read Consistency ($readMethodUsed)",
                        liveReadProofStatus = reportMsg,
                        liveReadProofFirst32Hex = first32Hex
                    )
                }
            }

            val failMsg = "❌ تم سحب ${page0Bytes.size}B ($readMethodUsed) لكن فشل إثبات المطابقة الداخلية ($verifyDetail) | أول 32B: $first32Hex"
            appendLog(ProberLogDirection.WARN, failMsg)
            updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failMsg)
            return summary.copy(
                liveReadProofPassed = false,
                liveReadProofMode = "Case 1 Failed ($readMethodUsed)",
                liveReadProofStatus = failMsg,
                liveReadProofFirst32Hex = first32Hex
            )
        }

        // -------------------- CASE 2: BLANK (0xFF) / CORRUPTED (0x00) CHIP -> CLOSED-LOOP WRITE-READ PROOF --------------------
        if (isChipBlankOrZero) {
            val baseStart = when (sync.framing) {
                DetectedFraming.STM32_ACK -> 0x08000000
                DetectedFraming.USBDM_BDM, DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> 0x00C000
                else -> 0
            }
            val lastPageStart = (flashEndAddr - pageSize + 1).coerceAtLeast(baseStart)
            val lastPageEnd = lastPageStart + pageSize - 1
            val rangeHex = "0x%06X..0x%06X".format(lastPageStart, lastPageEnd)

            val confirmed = requestStepConfirmation(
                prompt = Stage5ConfirmPrompt(
                    stepIndex = 1,
                    totalSteps = 1,
                    stepTitle = "الحلقة المغلقة (Write-Read Proof) على شريحة فارغة 0xFF",
                    impactDescription = "تم اكتشاف أن الشريحة فارغة تماماً (0xFF) أو ممسوحة (0x00) عند بداية الفلاش.\nلإثبات صحة السحب والكتابة بنسبة 100% عملياً:\n• سيتم كتابة نمط الاختبار [55 AA 12 34 56 78 9A BC DE F0 11 22 33 44 55 66] في آخر صفحة من الفلاش ($rangeHex).\n• ثم تنفيذ أمر السحب المكتشف على نفس العنوان ومقارنة البيانات 100%.\n\n⚠️ تحذير: هذا الإجراء سيكتب نمط الاختبار في آخر صفحة من الفلاش${if (userCancelledStage5) " (يمكنك الضغط على إلغاء إن أردت الحفاظ على الشريحة فارغة)" else ""}."
                ),
                stepId = "stage6_live_read_proof"
            )

            if (!confirmed) {
                val skipMsg = "⚠️ تم تجاوز اختبار الحلقة المغلقة (الحالة 2 - شريحة فارغة 0xFF) بناءً على طلب المشرف للحفاظ على الذاكرة."
                appendLog(ProberLogDirection.WARN, skipMsg)
                updateStep("stage6_live_read_proof", ChecklistStepStatus.BYPASSED, skipMsg)
                return summary.copy(
                    liveReadProofPassed = false,
                    liveReadProofMode = "Case 2 Bypassed by Admin (Blank 0xFF Chip)",
                    liveReadProofStatus = skipMsg,
                    liveReadProofFirst32Hex = page0Bytes?.take(32)?.toByteArray()?.toHexStr() ?: "FF ".repeat(32).trim()
                )
            }

            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_STAGE5_STEP,
                stageBanner = "جاري تنفيذ المرحلة 6 (الحلقة المغلقة): كتابة 55 AA 12 34... في آخر صفحة ثم استرجاعها..."
            )
            updateStep(
                "stage6_live_read_proof",
                ChecklistStepStatus.RUNNING,
                "كتابة البصمة 55 AA 12 34... في $rangeHex ثم السحب والمطابقة..."
            )
            resyncLight(sync)

            val pattern16 = byteArrayOf(
                0x55, 0xAA.toByte(), 0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(),
                0xDE.toByte(), 0xF0.toByte(), 0x11, 0x22, 0x33, 0x44, 0x55, 0x66
            )
            val writePage = ByteArray(pageSize) { 0xFF.toByte() }
            System.arraycopy(pattern16, 0, writePage, 0, pattern16.size)

            val (writeOk, writeDetail) = writeFlashLastPage(sync, summary, lastPageStart, lastPageEnd, writePage)
            if (!writeOk) {
                val failMsg = "❌ فشل كتابة بصمة الاختبار في آخر صفحة ($rangeHex): $writeDetail"
                appendLog(ProberLogDirection.ERROR, failMsg)
                updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failMsg)
                return summary.copy(
                    liveReadProofPassed = false,
                    liveReadProofMode = "Case 2 Write Failed ($rangeHex)",
                    liveReadProofStatus = failMsg,
                    liveReadProofFirst32Hex = "--"
                )
            }

            delay(35)
            ensureBootloaderReady(sync, "تجهيز القراءة/المطابقة بعد الكتابة بالمرحلة 6", relockOsc = true)

            // Execute discovered Read command on the exact same address (lastPageStart)
            val (readBack, rbMethod, rbEchoSample) = readFlashPageForProof(sync, summary, lastPageStart, pageSize)
            if (readBack != null && !isEchoPayload(readBack, rbEchoSample)) {
                val patternMatch = readBack.size >= pattern16.size &&
                    readBack.copyOfRange(0, pattern16.size).contentEquals(pattern16)
                val fullMatch = readBack.size == writePage.size && readBack.contentEquals(writePage)
                val readFirst32 = readBack.take(32).toByteArray().toHexStr()

                if (patternMatch && fullMatch) {
                    val reportMsg = "✅ إثبات السحب والكتابة 100% على شريحة فارغة: تم استرجاع البصمة المكتوبة (55 AA 12 34...) بدقة تامة"
                    val fullDetail = "$reportMsg | العنوان $rangeHex ($rbMethod) | أول 32B: $readFirst32"
                    appendLog(ProberLogDirection.SUCCESS, fullDetail)
                    updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, fullDetail)
                    return summary.copy(
                        liveReadProofPassed = true,
                        liveReadProofMode = "Case 2: Closed-Loop Write-Read Proof ($rbMethod @ $rangeHex)",
                        liveReadProofStatus = reportMsg,
                        liveReadProofFirst32Hex = readFirst32
                    )
                } else {
                    val failMsg = "❌ عدم تطابق بعد الكتابة في $rangeHex: المكتوب=${pattern16.toHexStr()} | المسحوب=${readBack.take(16).toByteArray().toHexStr()}"
                    appendLog(ProberLogDirection.ERROR, failMsg)
                    updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failMsg)
                    return summary.copy(
                        liveReadProofPassed = false,
                        liveReadProofMode = "Case 2 Read-Back Mismatch",
                        liveReadProofStatus = failMsg,
                        liveReadProofFirst32Hex = readFirst32
                    )
                }
            }

            // Locked bootloader fallback (when direct Read isn't exposed by ROM): verify via 0x13 (ACK vs 1B-Mutated Mismatch) + 0xB0 Checksum
            val (vSup, vPass, vDet) = verifyExtractedPageOnChip(sync, lastPageStart, writePage)
            var csMatched = false
            if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                var csRx = transport.transceive(
                    buildNecCommandFrame(0xB0, addrRangeBytes(lastPageStart, lastPageEnd)),
                    16, 360, "6.2 Checksum Verify [0xB0 $rangeHex]", stopOnCompleteNecFrame = true
                )
                if (csRx.isEmpty()) {
                    hardwareResetAndResync(sync, "6.2 Checksum Verify 0xB0 Timeout", relockOsc = true)
                    csRx = transport.transceive(
                        buildNecCommandFrame(0xB0, addrRangeBytes(lastPageStart, lastPageEnd)),
                        16, 360, "6.2 Checksum Verify Retry [0xB0 $rangeHex]", stopOnCompleteNecFrame = true
                    )
                }
                val csHw = parseChecksum16(csRx) ?: run {
                    if (parseNecStatus(csRx) == ACK_NEC) parseChecksum16(transport.readFollowUp(12, 280, true)) else null
                }
                val expectedCs16 = (0 - writePage.sumOf { it.toInt() and 0xFF }) and 0xFFFF
                csMatched = (csHw != null && (csHw == expectedCs16 || (((csHw and 0xFF) shl 8) or (csHw ushr 8)) == expectedCs16))
            }

            if (vSup && vPass) {
                val reportMsg = "✅ إثبات السحب والكتابة 100% على شريحة فارغة: تم استرجاع البصمة المكتوبة (55 AA 12 34...) بدقة تامة"
                val first32Expected = writePage.take(32).toByteArray().toHexStr()
                val detailMsg = "$reportMsg (تأكيد داخلي عبر Verify 0x13${if (csMatched) " + 0xB0 Checksum" else ""}: $vDet @ $rangeHex)"
                appendLog(ProberLogDirection.SUCCESS, detailMsg)
                updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, detailMsg)
                return summary.copy(
                    liveReadProofPassed = true,
                    liveReadProofMode = "Case 2: Closed-Loop Write+Verify+Checksum Proof (Locked ROM @ $rangeHex)",
                    liveReadProofStatus = reportMsg,
                    liveReadProofFirst32Hex = first32Expected
                )
            }

            val failMsg = "❌ تعذر قراءة أو تأكيد البصمة المكتوبة في $rangeHex ($writeDetail | $vDet)"
            appendLog(ProberLogDirection.ERROR, failMsg)
            updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failMsg)
            return summary.copy(
                liveReadProofPassed = false,
                liveReadProofMode = "Case 2 Verification Failed",
                liveReadProofStatus = failMsg,
                liveReadProofFirst32Hex = "--"
            )
        }

        // Neither direct extraction succeeded nor chip could be verified blank (e.g. locked MCU with non-blank data and Stage 5 skipped)
        val lockedMsg = "⚠️ المعالج مقفل ويحتوي على بيانات محمية (تعذر القراءة المباشرة لـ Page 0 دون إتمام فك القفل أو تصفير Block 0 بالمرحلة 5)"
        appendLog(ProberLogDirection.WARN, lockedMsg)
        updateStep("stage6_live_read_proof", ChecklistStepStatus.BYPASSED, lockedMsg)
        return summary.copy(
            liveReadProofPassed = false,
            liveReadProofMode = "Locked Protected Data (Requires Block-0 Zeroing / Unlock)",
            liveReadProofStatus = lockedMsg,
            liveReadProofFirst32Hex = "--"
        )
    }

    private fun parseFlashEndAddress(summary: ProberBlueprintSummary, sync: SyncLock): Int {
        val token = summary.flashEndAddressHex.trim().substringBefore(" ")
        val parsed = if (token.startsWith("0x", ignoreCase = true)) {
            token.substring(2).toIntOrNull(16)
        } else null
        return parsed ?: if (sync.framing == DetectedFraming.STM32_ACK) 0x0801FFFF else 0x007FFF
    }

    private data class PageReadOutcome(
        val bytes: ByteArray?,
        val methodLabel: String,
        val txFrameSample: ByteArray
    )

    private suspend fun readFlashPageForProof(
        sync: SyncLock,
        summary: ProberBlueprintSummary,
        startAddr: Int,
        length: Int
    ): PageReadOutcome {
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "Stage6 USBDM HALT [02 14]", logTxRx = false)
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            var lastCmd = ByteArray(0)
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(32, length - offset)
                val addr = startAddr + offset
                val pkt = byteArrayOf(
                    0x08, 0x21, 0x01, chunkLen.toByte(),
                    ((addr ushr 24) and 0xFF).toByte(),
                    ((addr ushr 16) and 0xFF).toByte(),
                    ((addr ushr 8) and 0xFF).toByte(),
                    (addr and 0xFF).toByte()
                )
                lastCmd = pkt
                val rx = transport.usbdmTransceive(pkt, chunkLen + 8, 350, "Stage6 USBDM READ_MEM [0x21 @ 0x%04X +%d]".format(addr, chunkLen), logTxRx = false)
                if (rx.size < chunkLen + 1 || (rx[0].toInt() and 0xFF) != 0x00) {
                    return PageReadOutcome(null, "USBDM READ_MEM (0x21) Failed at 0x%04X".format(addr), pkt)
                }
                out.write(rx, 1, chunkLen)
                offset += chunkLen
            }
            return PageReadOutcome(out.toByteArray(), "USBDM READ_MEM (0x21 @ 0x%04X)".format(startAddr), lastCmd)
        }

        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val pkt = byteArrayOf(0xE8.toByte(), ((startAddr ushr 8) and 0xFF).toByte(), (startAddr and 0xFF).toByte())
            val rx = transport.transceive(pkt, length + 8, 350, "Stage6 1.5Mbps BDM Read [0x%04X]".format(startAddr))
            val extracted = if (rx.size >= length) rx.copyOfRange(0, length) else null
            return if (extracted != null) {
                PageReadOutcome(extracted, "Single-Wire BDM Read @ 1.5Mbps", pkt)
            } else {
                PageReadOutcome(null, "Single-Wire BDM Read Failed", pkt)
            }
        }

        if (sync.framing == DetectedFraming.STM32_ACK) {
            val out = java.io.ByteArrayOutputStream()
            val tx = byteArrayOf(
                ((startAddr ushr 24) and 0xFF).toByte(),
                ((startAddr ushr 16) and 0xFF).toByte(),
                ((startAddr ushr 8) and 0xFF).toByte(),
                (startAddr and 0xFF).toByte()
            )
            val csAddr = ((tx[0].toInt() xor tx[1].toInt() xor tx[2].toInt() xor tx[3].toInt()) and 0xFF).toByte()
            val a1 = transport.transceive(byteArrayOf(0x11, 0xEE.toByte()), 4, 260, "Stage6 STM32 Read [0x11]")
            if (a1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val a2 = transport.transceive(tx + csAddr, 4, 260, "Stage6 STM32 Addr [0x%08X]".format(startAddr))
                if (a2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val countByte = (length - 1) and 0xFF
                    val cmdLen = byteArrayOf(countByte.toByte(), (countByte xor 0xFF).toByte())
                    val rx = transport.transceive(cmdLen, length + 8, 950, "Stage6 STM32 Read ${length}B")
                    if (rx.size >= length + 1 && (rx[0].toInt() and 0xFF) == ACK_STM32) {
                        out.write(rx, 1, length)
                        return PageReadOutcome(out.toByteArray(), "STM32 Direct Read 0x11", tx + cmdLen)
                    }
                }
            }
            return PageReadOutcome(null, "STM32 Read 0x11 Failed", tx)
        }

        // Collect candidate direct read opcodes (prioritize any command that succeeded in Stage 2.1 or Stage 3)
        val stage2DirectDetail = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.detail.orEmpty()
        val winningMatch = Regex("0x([0-9A-Fa-f]{2})").find(stage2DirectDetail)?.groupValues?.get(1)?.toIntOrNull(16)
        val candidateCmds = buildList {
            if (winningMatch != null) add(winningMatch)
            listOf(0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF).forEach { if (it !in this) add(it) }
        }

        for (cmd in candidateCmds) {
            currentCoroutineContext().ensureActive()
            val endAddr = startAddr + length - 1
            val pkts = when (sync.framing) {
                DetectedFraming.NEC_STX_ETX -> listOf(
                    buildNecCommandFrame(cmd, addrRangeBytes(startAddr, endAddr)),
                    buildNecCommandFrame(
                        cmd,
                        byteArrayOf(
                            ((startAddr ushr 16) and 0xFF).toByte(),
                            ((startAddr ushr 8) and 0xFF).toByte(),
                            (startAddr and 0xFF).toByte(),
                            (length and 0xFF).toByte()
                        )
                    )
                )
                DetectedFraming.RENESAS_RAW -> listOf(
                    byteArrayOf(cmd.toByte(), ((startAddr ushr 16) and 0xFF).toByte(), ((startAddr ushr 8) and 0xFF).toByte(), (startAddr and 0xFF).toByte()),
                    byteArrayOf(cmd.toByte(), ((startAddr ushr 8) and 0xFF).toByte(), (startAddr and 0xFF).toByte(), (length and 0xFF).toByte())
                )
                else -> listOf(
                    byteArrayOf(cmd.toByte(), ((startAddr ushr 16) and 0xFF).toByte(), ((startAddr ushr 8) and 0xFF).toByte(), (startAddr and 0xFF).toByte(), (length and 0xFF).toByte()),
                    buildNecCommandFrame(cmd, addrRangeBytes(startAddr, endAddr))
                )
            }

            for (pkt in pkts) {
                val rx = transport.transceive(pkt, length + 24, 650, "Stage6 Direct Read [0x%02X @ 0x%06X]".format(cmd, startAddr))
                val full = if (sync.framing == DetectedFraming.NEC_STX_ETX && rx.size in 1..20 && parseNecStatus(rx) == ACK_NEC) {
                    rx + transport.readFollowUp(length + 16, 700, stopOnCompleteNecFrame = true)
                } else rx
                val extracted = extractVariablePayload(full, sync.framing, length)
                if (extracted != null && extracted.size >= length) {
                    return PageReadOutcome(extracted.copyOfRange(0, length), "Direct Read [0x%02X]".format(cmd), pkt)
                }
            }

            // Chunked 16-byte fallback if single 256B read wasn't accepted by this opcode
            if (winningMatch == cmd && sync.framing == DetectedFraming.NEC_STX_ETX) {
                val out = java.io.ByteArrayOutputStream()
                var lastPkt = ByteArray(0)
                var chunkOk = true
                var off = 0
                while (off < length) {
                    currentCoroutineContext().ensureActive()
                    val cStart = startAddr + off
                    val cEnd = cStart + 15
                    val pkt = buildNecCommandFrame(cmd, addrRangeBytes(cStart, cEnd))
                    lastPkt = pkt
                    val rx = transport.transceive(pkt, 32, 220, "Stage6 Chunk [0x%02X +%d]".format(cmd, off), logTxRx = false)
                    val full = if (rx.size in 1..12 && parseNecStatus(rx) == ACK_NEC) {
                        rx + transport.readFollowUp(24, 220, true)
                    } else rx
                    val chunk16 = extractVariablePayload(full, sync.framing, 16)
                    if (chunk16 == null || chunk16.size < 16) {
                        chunkOk = false
                        break
                    }
                    out.write(chunk16, 0, 16)
                    off += 16
                }
                if (chunkOk && out.size() >= length) {
                    return PageReadOutcome(out.toByteArray().copyOfRange(0, length), "Chunked Direct Read [0x%02X]".format(cmd), lastPkt)
                }
            }
        }

        // If Direct Read is blocked, check if 1-Byte Checksum (0xB0) School passed in Stage 2.2
        val cs1bPassed = _state.value.checklist.any { it.id == "stage2_checksum_1b" && it.status == ChecklistStepStatus.PASSED }
        if (cs1bPassed && sync.framing == DetectedFraming.NEC_STX_ETX) {
            appendLog(ProberLogDirection.INFO, "المرحلة 6: استخدام مدرسة 1-Byte Checksum (0xB0) لسحب $length بايت من 0x%06X...".format(startAddr))
            val out = java.io.ByteArrayOutputStream()
            var lastPkt = ByteArray(0)
            for (i in 0 until length) {
                currentCoroutineContext().ensureActive()
                val addr = startAddr + i
                val pkt = buildNecCommandFrame(0xB0, addrRangeBytes(addr, addr))
                lastPkt = pkt
                val rx = transport.transceive(pkt, 14, 160, "6.B0 Byte $i", stopOnCompleteNecFrame = true, logTxRx = false)
                val full = if (parseChecksum16(rx) == null && parseNecStatus(rx) == ACK_NEC) {
                    transport.readFollowUp(10, 150, true)
                } else rx
                val stxIdx = full.indexOfFirst { (it.toInt() and 0xFF) == STX }
                if (stxIdx < 0 || full.size < stxIdx + 6 || (full[stxIdx + 1].toInt() and 0xFF) != 2) {
                    return PageReadOutcome(null, "1B 0xB0 Extraction Failed at +$i", lastPkt)
                }
                val b0 = full[stxIdx + 2].toInt() and 0xFF
                val b1 = full[stxIdx + 3].toInt() and 0xFF
                val lowByte = if (b1 == 0xFF) b0 else if (b0 == 0xFF) b1 else b0
                val dataByte = (0 - lowByte) and 0xFF
                out.write(dataByte)
            }
            if (out.size() == length) {
                return PageReadOutcome(out.toByteArray(), "1-Byte 0xB0 Checksum School", lastPkt)
            }
        }

        return PageReadOutcome(null, "No Open Read School", ByteArray(0))
    }

    private suspend fun verifyExtractedPageOnChip(
        sync: SyncLock,
        startAddr: Int,
        pageData: ByteArray
    ): Triple<Boolean, Boolean, String> {
        val verifySupportedInDiscovery = summaryHasVerifyCommand()
        if (sync.framing != DetectedFraming.NEC_STX_ETX && !verifySupportedInDiscovery) {
            return Triple(false, false, "لا يوجد أمر Verify داخلي في هذا البروتوكول")
        }

        val endAddr = startAddr + pageData.size - 1
        var hdr1 = transport.transceive(
            buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)),
            12, 320, "Stage6 Verify Orig Header [0x13]", stopOnCompleteNecFrame = true
        )
        if (hdr1.isEmpty()) {
            hardwareResetAndResync(sync, "Stage6 Verify Orig Header Timeout", relockOsc = true)
            hdr1 = transport.transceive(
                buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)),
                12, 320, "Stage6 Verify Orig Header Retry [0x13]", stopOnCompleteNecFrame = true
            )
        }
        if (parseNecStatus(hdr1) != ACK_NEC) {
            return Triple(false, false, "أمر 0x13 غير مدعوم (${formatByte(parseNecStatus(hdr1))})")
        }
        var origRx = transport.transceive(
            buildNecDataFrame(pageData, isLast = true),
            16, 620, "Stage6 Verify Original ${pageData.size}B",
            stopOnCompleteNecFrame = true,
            paced64B = pageData.size > 64
        )
        if (origRx.isEmpty()) {
            hardwareResetAndResync(sync, "Stage6 Verify Orig Data Timeout", relockOsc = true)
            val hRetry = transport.transceive(buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)), 12, 320, "Stage6 Verify Orig Header #2", stopOnCompleteNecFrame = true)
            if (parseNecStatus(hRetry) == ACK_NEC) {
                origRx = transport.transceive(buildNecDataFrame(pageData, isLast = true), 16, 620, "Stage6 Verify Original Retry", stopOnCompleteNecFrame = true, paced64B = pageData.size > 64)
            }
        }
        val origSts = effectiveNecStatus(origRx)
        delay(15)

        val mutated = pageData.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        var hdr2 = transport.transceive(
            buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)),
            12, 320, "Stage6 Verify Mutated Header [0x13]", stopOnCompleteNecFrame = true
        )
        if (hdr2.isEmpty()) {
            hardwareResetAndResync(sync, "Stage6 Verify Mutated Header Timeout", relockOsc = true)
            hdr2 = transport.transceive(
                buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)),
                12, 320, "Stage6 Verify Mutated Header Retry [0x13]", stopOnCompleteNecFrame = true
            )
        }
        var mutSts: Int? = null
        if (parseNecStatus(hdr2) == ACK_NEC) {
            var mutRx = transport.transceive(
                buildNecDataFrame(mutated, isLast = true),
                16, 620, "Stage6 Verify 1B-Mutated ${pageData.size}B",
                stopOnCompleteNecFrame = true,
                paced64B = pageData.size > 64
            )
            if (mutRx.isEmpty()) {
                hardwareResetAndResync(sync, "Stage6 Verify Mutated Data Timeout", relockOsc = true)
                val h2Retry = transport.transceive(buildNecCommandFrame(0x13, addrRangeBytes(startAddr, endAddr)), 12, 320, "Stage6 Verify Mutated Header #2", stopOnCompleteNecFrame = true)
                if (parseNecStatus(h2Retry) == ACK_NEC) {
                    mutRx = transport.transceive(buildNecDataFrame(mutated, isLast = true), 16, 620, "Stage6 Verify 1B-Mutated Retry", stopOnCompleteNecFrame = true, paced64B = pageData.size > 64)
                }
            }
            mutSts = effectiveNecStatus(mutRx)
        }

        val origOk = (origSts == ACK_NEC)
        val mutMismatch = (mutSts != null && mutSts != ACK_NEC && (mutSts in HARD_REJECT_STATUS || mutSts == STS_VERIFY_ERROR || mutSts == STS_VERIFY_ERROR_1C))
        return Triple(
            true,
            origOk && mutMismatch,
            "Orig=${formatByte(origSts)}, Mutated1B=${formatByte(mutSts)}"
        )
    }

    private suspend fun writeFlashLastPage(
        sync: SyncLock,
        summary: ProberBlueprintSummary,
        lastPageStart: Int,
        lastPageEnd: Int,
        payloadPage: ByteArray
    ): Pair<Boolean, String> {
        val busyWait = summary.timingMap.eraseWriteBusyWaitMs.coerceAtLeast(350)
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val ok = usbdmProgramBytes(lastPageStart, payloadPage)
            return ok to if (ok) "HCS08 FCMD 0x20 Byte Program OK" else "HCS08 FCMD 0x20 Write Failed"
        }
        if (sync.framing == DetectedFraming.STM32_ACK) {
            val w1 = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 6, 300, "Stage6 STM32 Write [0x31]")
            if (!w1.any { (it.toInt() and 0xFF) == ACK_STM32 }) return false to "0x31 rejected"
            val addr = byteArrayOf(
                ((lastPageStart ushr 24) and 0xFF).toByte(),
                ((lastPageStart ushr 16) and 0xFF).toByte(),
                ((lastPageStart ushr 8) and 0xFF).toByte(),
                (lastPageStart and 0xFF).toByte()
            )
            val csAddr = ((addr[0].toInt() xor addr[1].toInt() xor addr[2].toInt() xor addr[3].toInt()) and 0xFF).toByte()
            val w2 = transport.transceive(addr + csAddr, 6, 300, "Stage6 STM32 Write Addr")
            if (!w2.any { (it.toInt() and 0xFF) == ACK_STM32 }) return false to "Address rejected"
            val lenByte = ((payloadPage.size - 1) and 0xFF).toByte()
            var csData = lenByte.toInt() and 0xFF
            for (b in payloadPage) csData = csData xor (b.toInt() and 0xFF)
            val w3 = transport.transceive(
                byteArrayOf(lenByte) + payloadPage + byteArrayOf(csData.toByte()),
                8, busyWait + 350, "Stage6 STM32 Write Payload", paced64B = true
            )
            val ok = w3.any { (it.toInt() and 0xFF) == ACK_STM32 }
            return ok to if (ok) "STM32 Write ACK" else "STM32 Write NACK"
        }

        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            var hdr = transport.transceive(
                buildNecCommandFrame(0x40, addrRangeBytes(lastPageStart, lastPageEnd)),
                12, 420, "Stage6 Write Header [0x40 0x%06X..0x%06X]".format(lastPageStart, lastPageEnd),
                stopOnCompleteNecFrame = true
            )
            if (hdr.isEmpty()) {
                hardwareResetAndResync(sync, "Stage6 0x40 Single-Page Header Timeout", relockOsc = true)
                hdr = transport.transceive(
                    buildNecCommandFrame(0x40, addrRangeBytes(lastPageStart, lastPageEnd)),
                    12, 420, "Stage6 Write Header Retry [0x40 0x%06X..0x%06X]".format(lastPageStart, lastPageEnd),
                    stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(hdr) == ACK_NEC) {
                val frame = buildNecDataFrame(payloadPage, isLast = true)
                val rx = transport.transceive(frame, 12, busyWait + 300, "Stage6 Write STX Payload", stopOnCompleteNecFrame = true, paced64B = true)
                val s1 = parseNecStatus(rx)
                val s2 = parseNecSecondStatus(rx)
                val ok = (s1 == ACK_NEC && (s2 == null || s2 == ACK_NEC))
                if (ok) {
                    return true to "0x40 Single-Page (${formatByte(s1)}/${formatByte(s2)})"
                }
                hardwareResetAndResync(sync, "التبديل إلى الكتابة المتوافقة مع البلوك 0x22+0x40", relockOsc = true)
            }

            // Block-aligned fallback (e.g. NEC 78K0 requiring 1KB block alignment for 0x40)
            val blockSize = (summary.blockSizeBytes ?: 1024).coerceAtLeast(payloadPage.size)
            val blockStart = (lastPageEnd + 1 - blockSize).coerceAtLeast(0)
            var eRx = transport.transceive(
                buildNecCommandFrame(0x22, addrRangeBytes(blockStart, lastPageEnd)),
                12, 1200, "Stage6 Block Erase Last Block [0x22]", stopOnCompleteNecFrame = true
            )
            if (eRx.isEmpty()) {
                hardwareResetAndResync(sync, "Stage6 0x22 Block Erase Timeout", relockOsc = true)
                eRx = transport.transceive(
                    buildNecCommandFrame(0x22, addrRangeBytes(blockStart, lastPageEnd)),
                    12, 1200, "Stage6 Block Erase Retry [0x22]", stopOnCompleteNecFrame = true
                )
            }
            var bHdr = transport.transceive(
                buildNecCommandFrame(0x40, addrRangeBytes(blockStart, lastPageEnd)),
                12, 450, "Stage6 Write Block Header [0x40 0x%06X..0x%06X]".format(blockStart, lastPageEnd),
                stopOnCompleteNecFrame = true
            )
            if (bHdr.isEmpty()) {
                hardwareResetAndResync(sync, "Stage6 0x40 Block Header Timeout", relockOsc = true)
                bHdr = transport.transceive(
                    buildNecCommandFrame(0x40, addrRangeBytes(blockStart, lastPageEnd)),
                    12, 450, "Stage6 Write Block Header Retry [0x40 0x%06X..0x%06X]".format(blockStart, lastPageEnd),
                    stopOnCompleteNecFrame = true
                )
            }
            if (parseNecStatus(bHdr) != ACK_NEC) {
                return false to "0x40 Header Rejected (${formatByte(parseNecStatus(bHdr))})"
            }
            val pageCount = (blockSize / payloadPage.size).coerceAtLeast(1)
            val blankFiller = ByteArray(payloadPage.size) { 0xFF.toByte() }
            for (p in 0 until pageCount) {
                val isLast = (p == pageCount - 1)
                val slice = if (isLast) payloadPage else blankFiller
                val rx = transport.transceive(
                    buildNecDataFrame(slice, isLast = isLast),
                    12, if (isLast) busyWait + 350 else 500,
                    "Stage6 Write Page $p/${pageCount - 1}", stopOnCompleteNecFrame = true, paced64B = true
                )
                if (rx.isEmpty()) {
                    hardwareResetAndResync(sync, "Stage6 Write Page $p Timeout", relockOsc = true)
                    return false to "Page $p Timeout"
                }
                val s1 = parseNecStatus(rx)
                val s2 = parseNecSecondStatus(rx)
                if (s1 != ACK_NEC || (s2 != null && s2 != ACK_NEC)) {
                    return false to "Page $p failed (${formatByte(s1)}/${formatByte(s2)})"
                }
            }
            return true to "0x40 Block-Aligned Write ACK"
        }

        // Generic / Renesas Raw Page Write 0x41
        val rawPkt = byteArrayOf(
            0x41,
            ((lastPageStart ushr 16) and 0xFF).toByte(),
            ((lastPageStart ushr 8) and 0xFF).toByte(),
            (lastPageStart and 0xFF).toByte()
        ) + payloadPage
        val rx = transport.transceive(rawPkt, 16, busyWait + 350, "Stage6 Raw Write [0x41]", paced64B = true)
        return rx.isNotEmpty() to if (rx.isNotEmpty()) "0x41 ACK (${rx.toHexStr()})" else "0x41 Timeout"
    }

    private fun extractVariablePayload(rx: ByteArray, framing: DetectedFraming, expectedLen: Int): ByteArray? {
        if (rx.isEmpty()) return null
        if (framing == DetectedFraming.NEC_STX_ETX) {
            val sts = parseNecStatus(rx)
            if (sts != null && sts in HARD_REJECT_STATUS) return null
            // Check second frame (after ACK frame) first, then single data frame
            val second = extractNecSecondFramePayload(rx)
            if (second != null && second.size >= expectedLen) return second
            val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
            if (stxIdx >= 0 && rx.size >= stxIdx + 4) {
                val lenByte = rx[stxIdx + 1].toInt() and 0xFF
                val dataLen = if (lenByte == 0) 256 else lenByte
                if (dataLen >= expectedLen && rx.size >= stxIdx + 2 + dataLen + 2) {
                    return rx.copyOfRange(stxIdx + 2, stxIdx + 2 + dataLen)
                }
            }
            return null
        }
        return if (rx.size >= expectedLen) rx.copyOfRange(0, expectedLen) else null
    }

    private fun isEchoPayload(extracted: ByteArray, txSample: ByteArray): Boolean {
        if (extracted.isEmpty() || txSample.isEmpty()) return false
        val cmpLen = min(extracted.size, txSample.size).coerceAtLeast(4)
        if (extracted.size >= cmpLen && txSample.size >= cmpLen &&
            extracted.copyOfRange(0, cmpLen).contentEquals(txSample.copyOfRange(0, cmpLen))
        ) {
            return true
        }
        // Also detect repeating pattern of exact TX header across the page
        if (txSample.size >= 5 && extracted.size >= txSample.size * 2) {
            val secondOffset = txSample.size
            if (extracted.copyOfRange(0, txSample.size).contentEquals(txSample) &&
                extracted.copyOfRange(secondOffset, secondOffset + txSample.size).contentEquals(txSample)
            ) {
                return true
            }
        }
        return false
    }

    private fun summaryHasVerifyCommand(): Boolean =
        _state.value.checklist.any { it.id == "stage2_verify_0x13" && it.status == ChecklistStepStatus.PASSED } ||
            _state.value.blueprintSummary.discoveredCommands.any { it.cmdByte == 0x13 }

    private suspend fun ensureBootloaderReady(sync: SyncLock, reason: String, relockOsc: Boolean = true) {
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            resyncLight(sync)
            return
        }
        val ping = transport.transceive(
            buildNecCommandFrame(0x00),
            12, 180, "Ping [0x00]", stopOnCompleteNecFrame = true, logTxRx = false
        )
        if (parseNecStatus(ping) != ACK_NEC) {
            hardwareResetAndResync(sync, reason, relockOsc = relockOsc)
        }
    }

    private suspend fun usbdmProgramBytes(startAddr: Int, data: ByteArray): Boolean {
        transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 200, "USBDM HALT [02 14]", logTxRx = false)
        for (i in data.indices) {
            currentCoroutineContext().ensureActive()
            val b = data[i].toInt() and 0xFF
            if (b == 0xFF) continue
            val addr = startAddr + i
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 120, "FSTAT Clear", logTxRx = false)
            val wr = transport.usbdmTransceive(
                byteArrayOf(
                    0x09, 0x20, 0x01, 0x01,
                    ((addr ushr 24) and 0xFF).toByte(),
                    ((addr ushr 16) and 0xFF).toByte(),
                    ((addr ushr 8) and 0xFF).toByte(),
                    (addr and 0xFF).toByte(),
                    data[i]
                ),
                8, 150, "BDM Program Byte +$i", logTxRx = false
            )
            if (wr.isEmpty() || (wr[0].toInt() and 0xFF) != 0x00) return false
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x20), 8, 120, "FCMD ByteProg", logTxRx = false)
            val go = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 150, "FSTAT Launch", logTxRx = false)
            if (go.isEmpty() || (go[0].toInt() and 0xFF) != 0x00) return false
            delay(2)
        }
        return true
    }

    private suspend fun hardwareResetAndResync(
        sync: SyncLock,
        reason: String,
        relockOsc: Boolean = true
    ): Boolean {
        currentCoroutineContext().ensureActive()
        appendLog(
            ProberLogDirection.WARN,
            "🔄 استعادة التزامن التلقائية (Auto Re-Sync): $reason"
        )
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 8, 250, "USBDM Reconnect [02 0F]", logTxRx = false)
            val sy = transport.usbdmTransceive(byteArrayOf(0x02, 0x10), 8, 250, "USBDM Re-Sync [02 10]", logTxRx = false)
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM Re-Halt [02 14]", logTxRx = false)
            return sy.isNotEmpty() && (sy[0].toInt() and 0xFF) == 0x00
        }
        transport.configureUart(sync.baudRate, evenParity = sync.evenParity)
        if (sync.useResetToggle) {
            transport.pulseResetSequence(
                invertedPolarity = sync.inverted,
                holdResetMs = sync.resetPulseMs.toLong(),
                settleMs = sync.postResetDelayMs.toLong()
            )
        } else {
            transport.setControlLines(dtr = false, rts = false)
            delay(80)
        }
        transport.flushRx()
        val ok = resyncHandshakeQuiet(sync)
        if (ok && relockOsc && sync.framing == DetectedFraming.NEC_STX_ETX) {
            val osc = activeWinningOscPayload
            if (osc != null) {
                transport.transceive(
                    buildNecCommandFrame(0x90, osc),
                    12, 220, "Auto Re-Lock [0x90 -> ${osc.toHexStr()}]",
                    stopOnCompleteNecFrame = true,
                    logTxRx = false
                )
            }
        }
        return ok
    }

    private suspend fun resyncHandshakeQuiet(sync: SyncLock): Boolean {
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            delay(25)
            transport.flushRx()
            return true
        }
        transport.writeRawBytes(byteArrayOf(0x00))
        delay(sync.syncByteGapMs.toLong())
        transport.writeRawBytes(byteArrayOf(0x00))
        delay(50)
        transport.drainAvailableRaw(20)
        transport.flushRx()
        val rx = transport.transceive(
            buildNecCommandFrame(0x00),
            12, 350, "Quiet Resync [0x00]", stopOnCompleteNecFrame = true, logTxRx = false
        )
        return parseNecStatus(rx) == ACK_NEC
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
        _state.value = next.copy(
            readBlueprintText = formatReadBlueprintJson(next),
            writeBlueprintText = formatWriteBlueprintJson(next),
            humanGuideText = formatHumanGuideText(next),
            humanGuideJsonText = formatHumanGuideJson(next),
            blueprintText = formatAiReadyBlueprintJson(next)
        )
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
        if (payload.size < 6) return null
        val validEnds = setOf(0x001FFF, 0x003FFF, 0x007FFF, 0x00FFFF, 0x01FFFF, 0x03FFFF, 0x07FFFF)
        for (offset in 0..(payload.size - 3)) {
            val b0 = payload[offset].toInt() and 0xFF
            val b1 = payload[offset + 1].toInt() and 0xFF
            val b2 = payload[offset + 2].toInt() and 0xFF
            val leAddr = (b2 shl 16) or (b1 shl 8) or b0
            val beAddr = (b0 shl 16) or (b1 shl 8) or b2
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
        0x9F, 0xAF -> "Universal ID / Boot Info"
        0xA0 -> "Security Flag Set"
        0xB0 -> "Checksum"
        0xC0 -> "Silicon Signature"
        0xC5 -> "Version / Status"
        0xFB -> "Renesas Boot Version"
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
