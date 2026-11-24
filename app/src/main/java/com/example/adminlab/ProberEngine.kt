package com.example.adminlab

import android.content.Context
import kotlinx.coroutines.CancellationException
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
        val dtrPolarity: String,
        val rtsPolarity: String,
        val inverted: Boolean,
        val useResetToggle: Boolean,
        val syncPulseHex: String,
        val framing: DetectedFraming,
        val firstReplyHex: String,
        val latencySampleMs: Int
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
     * One-tap comprehensive probe executing Stages 1 -> 4 safely, then Stage 5 Safety Gate if needed.
     */
    fun startFullSafeProtocolScan() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        lastSyncLock = null

        activeJob = scope.launch {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "جاري تنفيذ المرحلة 1: هندسة التوصيل والتزامن...",
                checklist = defaultChecklist(),
                blueprintSummary = ProberBlueprintSummary(),
                blueprintText = "",
                restoreProgressText = ""
            )
            appendLog(ProberLogDirection.INFO, "══ بدء الفحص الشامل واستخراج البروتوكول (${family.labelAr}) ══")

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG. تأكد من توصيل المبرمجة ومنح الصلاحية.")
                    return@launch
                }

                // STAGE 1
                updateStep("stage1_handshake", ChecklistStepStatus.RUNNING, "مسح قطبية DTR/RTS والسرعات 9600..115200")
                val sync = runStage1HardwareHandshake(family)
                if (sync == null) {
                    updateStep("stage1_handshake", ChecklistStepStatus.FAILED, "لم يرد المعالج على أي نبضة تزامن")
                    failPipeline("فشل التزامن في المرحلة 1. تأكد من توصيل TX/RX و FLMD0/BOOT0 و RESET.")
                    return@launch
                }
                lastSyncLock = sync
                updateStep(
                    "stage1_handshake",
                    ChecklistStepStatus.PASSED,
                    "${sync.baudRate} bps | ${sync.framing.label} | ${sync.dtrPolarity}"
                )

                var summary = ProberBlueprintSummary(
                    baudRate = sync.baudRate,
                    maxBaudTested = sync.baudRate,
                    dtrPolarity = sync.dtrPolarity,
                    rtsPolarity = sync.rtsPolarity,
                    echoCancellation = transport.echoLoopbackObserved,
                    syncPulseHex = sync.syncPulseHex,
                    framing = sync.framing,
                    safeDelayMs = (sync.latencySampleMs + 15).coerceIn(15, 250)
                )
                updateSummaryAndBlueprint(summary)

                // STAGE 2: 5 Safe Read Schools
                _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 2: فحص مدارس السحب الآمنة...")

                val r1 = testDirectMemoryRead(sync)
                resyncLight(sync)
                val r2 = testChecksum1ByteAttack(sync)
                resyncLight(sync)
                val r3 = testVerify0x13Probe(sync)
                resyncLight(sync)
                val r4 = testSignatureRegisterRead(sync)
                resyncLight(sync)
                val r5 = testRamStubInjection(sync)

                val passedReads = listOf(r1, r2, r3, r4, r5).filter { it.status == ChecklistStepStatus.PASSED }
                val winningReadDesc = if (passedReads.isNotEmpty()) {
                    passedReads.joinToString(" + ") { it.title }
                } else {
                    "Locked Bootloader — يتطلب بوابة المرحلة 5"
                }
                summary = summary.copy(
                    winningReadMethod = winningReadDesc,
                    chipSignature = r4.takeIf { it.status == ChecklistStepStatus.PASSED }?.detail ?: ""
                )
                updateSummaryAndBlueprint(summary)

                // STAGE 3: Full 0x00..0xFF Command & Length Sweep (Strict Guard Active)
                _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 3: المسح الجراحي للأوامر 0x00..0xFF...")
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

                // STAGE 4: Timing, Boundaries & Speed
                _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 4: فحص السرعة وحدود الصفحة والبلوك...")
                updateStep("stage4_timing_bounds", ChecklistStepStatus.RUNNING, "اختبار 0x9A، Page Size، Block Size، وزمن الاستجابة")
                resyncLight(sync)
                summary = runStage4TimingAndBoundaries(sync, summary)
                updateStep(
                    "stage4_timing_bounds",
                    ChecklistStepStatus.PASSED,
                    "Page=${summary.pageSizeBytes}B | Block=${summary.blockSizeBytes}B | MaxBaud=${summary.maxBaudTested} | Delay=${summary.safeDelayMs}ms"
                )
                updateSummaryAndBlueprint(summary)

                // STAGE 5: Safety Gate & Hot-Swap Decision
                val anyReadSucceeded = passedReads.isNotEmpty()
                if (anyReadSucceeded) {
                    updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, "نجح السحب الآمن — لا حاجة لمدارس تعديلية خطرة")
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.COMPLETED,
                        stageBanner = "اكتمل الفحص الشامل واستخراج البروتوكول بنجاح"
                    )
                    transport.enterSafeIdleAndClose()
                } else {
                    // Put lines into Safe Idle and pause for Hot-Swap / Emergency Restore / Finish
                    transport.enterSafeIdleAndClose()
                    updateStep(
                        "stage5_safety_gate",
                        ChecklistStepStatus.AWAITING_SAFETY,
                        "المعالج مقفول — متوقف بأمان لاختيار تبديل الكارتة أو الاستعادة أو الإنهاء"
                    )
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                        stageBanner = "بوابة الأمان (المرحلة 5): المعالج مقفول وتوقف المحرك بأمان"
                    )
                    appendLog(
                        ProberLogDirection.WARN,
                        "⚠️ توقف المحرك تلقائياً عند المرحلة 5 (المعالج مقفول). يمكنك فصل الكارتة بأمان دون فقدان نتائج المراحل 1..4."
                    )
                }
            } catch (_: CancellationException) {
                stopImmediately("تم إيقاف الفحص فوراً بناءً على طلب المشرف.")
            } catch (t: Throwable) {
                failPipeline("خطأ أثناء التنفيذ: ${t.message ?: t::class.java.simpleName}")
            } finally {
                if (!_state.value.isWaitingAtSafetyGate) {
                    transport.closeSession()
                }
            }
        }
    }

    /**
     * Button 1 inside Stage 5 Gate: prepare for safe physical unplug & board hot-swap.
     */
    fun enterHotSwapStandby() {
        transport.enterSafeIdleAndClose()
        _state.value = _state.value.copy(
            phase = ProberStagePhase.HOT_SWAP_DISCONNECTED_READY,
            stageBanner = "جاهز لتبديل الكارتة — افصل الكارتة الأصلية ووصل كارتة التجارب ثم اضغط الاستكمال"
        )
        appendLog(ProberLogDirection.INFO, "وضع تبديل الكارتة (Hot-Swap) مفعّل: نتائج المراحل 1..4 محفوظة في الجلسة.")
    }

    /**
     * Resumes Stage 5 dangerous/advanced schools AFTER user reconnected test board.
     */
    fun resumeAdvancedAfterHotSwap() {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeJob = scope.launch {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_ADVANCED_AFTER_SWAP,
                stageBanner = "جاري إعادة تهيئة USB وفحص المدارس المتقدمة بعد التبديل..."
            )
            updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "إعادة تهيئة USB والمزامنة وفحص المدارس التعديلية...")

            try {
                if (!transport.openSession()) {
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.HOT_SWAP_DISCONNECTED_READY,
                        stageBanner = "تعذر فتح USB بعد التبديل — تأكد من توصيل الكابل والمحاولة مجدداً"
                    )
                    updateStep("stage5_safety_gate", ChecklistStepStatus.AWAITING_SAFETY, "أعد توصيل USB واضغط الاستكمال")
                    return@launch
                }

                val sync = runStage1HardwareHandshake(family)
                if (sync == null) {
                    updateStep("stage5_safety_gate", ChecklistStepStatus.FAILED, "لم يستجب المعالج البديل بعد إعادة التوصيل")
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                        stageBanner = "فشلت المزامنة بعد التبديل — تحقق من الأسلاك وحاول مجدداً"
                    )
                    transport.enterSafeIdleAndClose()
                    return@launch
                }

                val advNotes = runAdvancedUnlockSchools(sync)
                val updatedSummary = _state.value.blueprintSummary.copy(advancedSchoolsNotes = advNotes)
                updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, advNotes.lines().firstOrNull().orEmpty())
                updateSummaryAndBlueprint(updatedSummary)
                _state.value = _state.value.copy(
                    phase = ProberStagePhase.COMPLETED,
                    stageBanner = "اكتملت جميع المراحل (1 إلى 5) واستخراج البروتوكول الكامل"
                )
            } catch (_: CancellationException) {
                stopImmediately("تم إيقاف فحص المرحلة 5.")
            } catch (t: Throwable) {
                updateStep("stage5_safety_gate", ChecklistStepStatus.FAILED, t.message ?: "خطأ")
                _state.value = _state.value.copy(
                    phase = ProberStagePhase.ERROR,
                    stageBanner = "خطأ في المرحلة 5: ${t.message}"
                )
            } finally {
                transport.enterSafeIdleAndClose()
            }
        }
    }

    /**
     * Button 2 inside Stage 5 Gate: Emergency Flash Restore from a .bin or .hex firmware file.
     */
    fun emergencyRestoreFirmware(fileName: String, rawBytes: ByteArray) {
        if (activeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeJob = scope.launch {
            val parsed = FirmwareImageParser.parse(fileName, rawBytes)
            if (parsed.bytes.isEmpty()) {
                appendLog(ProberLogDirection.ERROR, "فشل تحليل ملف الاستعادة ($fileName): الملف فارغ أو غير صالح.")
                return@launch
            }

            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_EMERGENCY_RESTORE,
                stageBanner = "جاري الاستعادة الفورية للسوفت وير ($fileName)...",
                restoreProgressText = "تحضير ${parsed.bytes.size} بايت (${parsed.formatLabel})..."
            )
            updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, "استعادة السوفت وير الأصلي ($fileName)...")
            appendLog(ProberLogDirection.WARN, "🚑 بدء الاستعادة الفورية للسوفت وير: $fileName (${parsed.bytes.size}B, Base=0x%06X)".format(parsed.baseAddress))

            try {
                if (!transport.openSession()) {
                    updateStep("stage5_safety_gate", ChecklistStepStatus.AWAITING_SAFETY, "فشل فتح USB للاستعادة")
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                        stageBanner = "تأكد من توصيل USB ثم أعد الاستعادة"
                    )
                    return@launch
                }

                val sync = runStage1HardwareHandshake(family)
                if (sync == null) {
                    updateStep("stage5_safety_gate", ChecklistStepStatus.FAILED, "لم يستجب المعالج أثناء الاستعادة")
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                        stageBanner = "فشلت المزامنة للاستعادة — تأكد من أسلاك البوت"
                    )
                    transport.enterSafeIdleAndClose()
                    return@launch
                }

                val ok = performEmergencyFlashWrite(sync, parsed.bytes, parsed.baseAddress)
                if (ok) {
                    val msg = "تمت استعادة السوفت وير بنجاح (${parsed.bytes.size} بايت من $fileName)"
                    appendLog(ProberLogDirection.SUCCESS, "✅ $msg")
                    updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, msg)
                    val s = _state.value.blueprintSummary.copy(advancedSchoolsNotes = msg)
                    updateSummaryAndBlueprint(s)
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.COMPLETED,
                        stageBanner = msg,
                        restoreProgressText = msg
                    )
                } else {
                    val errMsg = "لم تكتمل الاستعادة — راجع سجل الحزم TX/RX"
                    updateStep("stage5_safety_gate", ChecklistStepStatus.FAILED, errMsg)
                    _state.value = _state.value.copy(
                        phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                        stageBanner = errMsg
                    )
                }
            } catch (_: CancellationException) {
                stopImmediately("تم إيقاف الاستعادة الفورية.")
            } catch (t: Throwable) {
                appendLog(ProberLogDirection.ERROR, "خطأ في الاستعادة: ${t.message}")
                updateStep("stage5_safety_gate", ChecklistStepStatus.FAILED, t.message ?: "فشل")
                _state.value = _state.value.copy(
                    phase = ProberStagePhase.AWAITING_HOT_SWAP_GATE,
                    stageBanner = "فشلت الاستعادة: ${t.message}"
                )
            } finally {
                transport.enterSafeIdleAndClose()
            }
        }
    }

    /**
     * Button 3 inside Stage 5 Gate: Finish cleanly and keep safe Stages 1..4 results.
     */
    fun finishWithSafeResultsOnly() {
        transport.enterSafeIdleAndClose()
        updateStep("stage5_safety_gate", ChecklistStepStatus.PASSED, "تم الإنهاء بأمان والاكتفاء بنتائج المراحل 1..4")
        val s = _state.value.blueprintSummary.copy(
            advancedSchoolsNotes = "تم الاكتفاء بالفحص الآمن للمراحل 1..4 دون تنفيذ أي مدارس تعديلية."
        )
        updateSummaryAndBlueprint(s)
        _state.value = _state.value.copy(
            phase = ProberStagePhase.COMPLETED,
            stageBanner = "تم إنهاء الفحص بأمان وتوليد البروتوكول الكامل"
        )
    }

    fun stopImmediately(reason: String = "تم إيقاف الفحص فوراً وإغلاق المنفذ بأمان.") {
        val job = activeJob
        activeJob = null
        job?.cancel()
        transport.enterSafeIdleAndClose()
        if (_state.value.isBusy) {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.STOPPED,
                stageBanner = reason
            )
            appendLog(ProberLogDirection.WARN, reason)
        }
    }

    fun release() {
        stopImmediately()
        transport.release()
    }

    // ==================== STAGE 1 IMPLEMENTATION ====================

    private suspend fun runStage1HardwareHandshake(family: ProberTargetFamily): SyncLock? {
        val bauds = when (family) {
            ProberTargetFamily.NEC_78K0 -> listOf(9600, 19200, 38400, 57600, 115200)
            ProberTargetFamily.STM32_UART -> listOf(115200, 57600, 38400, 19200, 9600)
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(9600, 38400, 57600, 19200, 115200)
            else -> listOf(9600, 19200, 38400, 57600, 115200)
        }

        val configs = listOf(
            Triple("Active-Low (DTR=FLMD0, RTS=RESET)", false, true),
            Triple("Active-High / معكوسة", true, true),
            Triple("بدون تحكم خطوط (Direct)", false, false)
        )

        val pulses: List<Pair<String, ByteArray>> = when (family) {
            ProberTargetFamily.NEC_78K0 -> listOf(
                "0x00 (NEC Reset Sync)" to byteArrayOf(0x00, 0x00),
                "SOH 0x01 Reset [0x00]" to buildNecCommandFrame(0x00),
                "0x55 (Baud Sync)" to byteArrayOf(0x55)
            )
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
                "0x00 (Reset/Sync)" to byteArrayOf(0x00, 0x00),
                "0x7F (Boot Sync)" to byteArrayOf(0x7F),
                "0x55 (AutoBaud)" to byteArrayOf(0x55),
                "0xA0 (Boot Pulse)" to byteArrayOf(0xA0.toByte())
            )
        }

        for (baud in bauds) {
            currentCoroutineContext().ensureActive()
            transport.configureUart(baud)
            appendLog(ProberLogDirection.INFO, "── المرحلة 1: اختبار السرعة $baud bps ──")

            for ((polDesc, inverted, doToggle) in configs) {
                currentCoroutineContext().ensureActive()
                if (doToggle) {
                    transport.pulseResetSequence(inverted, holdResetMs = 65L, settleMs = 105L)
                } else {
                    transport.setControlLines(dtr = false, rts = false)
                    delay(35)
                }
                transport.flushRx()

                for ((pulseName, pulseBytes) in pulses) {
                    currentCoroutineContext().ensureActive()
                    val t0 = System.currentTimeMillis()
                    val rx = transport.transceive(
                        txBytes = pulseBytes,
                        expectedMax = 24,
                        timeoutMs = 200,
                        label = "Sync [$pulseName @ $baud | $polDesc]"
                    )
                    val dt = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(5)
                    if (rx.isEmpty()) continue

                    if (transport.echoLoopbackObserved && rx.isEmpty()) continue

                    val p0 = pulseBytes[0].toInt() and 0xFF
                    if (p0 == 0x00 && rx.none { (it.toInt() and 0xFF) == STX }) {
                        delay(20)
                        val tNec = System.currentTimeMillis()
                        val necAck = transport.transceive(
                            txBytes = buildNecCommandFrame(0x00),
                            expectedMax = 16,
                            timeoutMs = 260,
                            label = "NEC SOH Reset [0x00]",
                            stopOnCompleteNecFrame = true
                        )
                        val dtNec = (System.currentTimeMillis() - tNec).toInt().coerceAtLeast(5)
                        if (necAck.any { (it.toInt() and 0xFF) == STX }) {
                            return SyncLock(
                                baudRate = baud,
                                dtrPolarity = polDesc,
                                rtsPolarity = if (doToggle) (if (inverted) "Active-High Pulse" else "Active-Low Pulse") else "Static",
                                inverted = inverted,
                                useResetToggle = doToggle,
                                syncPulseHex = "0x00 + SOH Reset",
                                framing = DetectedFraming.NEC_STX_ETX,
                                firstReplyHex = necAck.toHexStr(),
                                latencySampleMs = dtNec
                            )
                        }
                    }

                    return SyncLock(
                        baudRate = baud,
                        dtrPolarity = polDesc,
                        rtsPolarity = if (doToggle) (if (inverted) "Active-High Pulse" else "Active-Low Pulse") else "Static",
                        inverted = inverted,
                        useResetToggle = doToggle,
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

    // ==================== STAGE 2: 5 SAFE READ SCHOOLS ====================

    private suspend fun testDirectMemoryRead(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_direct_read", ChecklistStepStatus.RUNNING, "اختبار 0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF...")
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

    private suspend fun testVerify0x13Probe(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_verify_0x13", ChecklistStepStatus.RUNNING, "اختبار 0x13 على صفحة 0xFF وإطار STX 0x02...")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            return updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "غير منطبق على STM32")
        }
        val hdr = buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()))
        val hdrRx = transport.transceive(hdr, 12, 320, "Verify Header [0x13]", stopOnCompleteNecFrame = true)
        if (parseNecStatus(hdrRx) == ACK_NEC) {
            val ffPage = ByteArray(256) { 0xFF.toByte() }
            val stxFrame = buildNecDataFrame(ffPage, isLast = true)
            val dataRx = transport.transceive(stxFrame, 16, 520, "Verify STX 256x0xFF [0x13]", stopOnCompleteNecFrame = true, paced64B = true)
            val s1 = parseNecStatus(dataRx)
            val s2 = parseNecSecondStatus(dataRx)
            val eff = if (s1 == ACK_NEC && s2 != null && s2 != ACK_NEC) s2 else s1
            if (eff == ACK_NEC || eff == STS_VERIFY_ERROR || eff == STS_VERIFY_ERROR_1C) {
                return updateAndReturn(
                    "stage2_verify_0x13",
                    ChecklistStepStatus.PASSED,
                    if (eff == ACK_NEC) "0x13 نجح (الصفحة فارغة 0xFF)" else "0x13 فعّال (رد مطابقة 0x%02X)".format(eff)
                )
            }
        }
        return updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.FAILED, "فشل التحقق عبر 0x13")
    }

    private suspend fun testSignatureRegisterRead(sync: SyncLock): ChecklistStepItem {
        updateStep("stage2_signature", ChecklistStepStatus.RUNNING, "اختبار 0xC0, 0xC5, 0x01, 0x02...")
        val cmds = listOf(0xC0, 0xC5, 0x01, 0x02)
        for (cmd in cmds) {
            currentCoroutineContext().ensureActive()
            val tx = when (sync.framing) {
                DetectedFraming.STM32_ACK -> byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte())
                DetectedFraming.NEC_STX_ETX -> buildNecCommandFrame(cmd)
                else -> byteArrayOf(cmd.toByte())
            }
            val rx = transport.transceive(tx, 48, 280, "Signature [0x%02X]".format(cmd))
            if (rx.isEmpty()) continue
            if (sync.framing == DetectedFraming.STM32_ACK && (rx[0].toInt() and 0xFF) == ACK_STM32) {
                return updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
            } else if (sync.framing == DetectedFraming.NEC_STX_ETX && parseNecStatus(rx) == ACK_NEC) {
                val extra = if (!rx.drop(4).any { (it.toInt() and 0xFF) == STX }) transport.readFollowUp(48, 300, true) else ByteArray(0)
                val all = rx + extra
                val ascii = all.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                return updateAndReturn(
                    "stage2_signature",
                    ChecklistStepStatus.PASSED,
                    if (ascii.isNotBlank()) "[0x%02X] $ascii".format(cmd) else "[0x%02X] ${all.take(18).toByteArray().toHexStr()}".format(cmd)
                )
            } else if (sync.framing != DetectedFraming.NEC_STX_ETX && sync.framing != DetectedFraming.STM32_ACK) {
                val b0 = rx[0].toInt() and 0xFF
                if (b0 !in HARD_REJECT_STATUS) {
                    return updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "[0x%02X] -> ${rx.toHexStr()}".format(cmd))
                }
            }
        }
        return updateAndReturn("stage2_signature", ChecklistStepStatus.FAILED, "لم تستجب أوامر التوقيع")
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
            // Query 0x00 Get Command first (authoritative & 100% safe!)
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

        // Sweep 0x00..0xFF with strict protection against destructive 0-arg execution!
        val candidateBytes = mutableListOf<Pair<Int, Int>>()
        for (cmd in 0x00..0xFF) {
            currentCoroutineContext().ensureActive()
            if (cmd % 32 == 0) {
                updateStep("stage3_cmd_sweep", ChecklistStepStatus.RUNNING, "مسح البايتات: 0x%02X / 0xFF (مكتشف: ${candidateBytes.size})".format(cmd))
            }

            if (sync.framing == DetectedFraming.STM32_ACK) {
                // NEVER send STM32 Readout/Write Unprotect (0x92 / 0x82) during sweep!
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

            // NEC / Generic Framed:
            // For dangerous 0-arg opcodes (0x20, 0x21, 0x82, 0x92, 0xA0, 0xA7), ALWAYS send 6x0xFF impossible args
            // so unknown cmds return 0x04 and valid protected/erased cmds return 0x05 Parameter Error safely!
            val tx = if (cmd in ZERO_ARG_DANGER_CMDS) {
                buildNecCommandFrame(cmd, byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
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
        // Use 0xFF dummy out-of-range args for guarded cmds, 0x00 safe read args for safe cmds
        val fillByte: Byte = if (isGuarded) 0xFF.toByte() else 0x00

        for (lenArgs in 0..8) {
            currentCoroutineContext().ensureActive()
            if (isGuarded && lenArgs == 0) continue // Never send 0-arg guarded command
            val args = ByteArray(lenArgs) { fillByte }
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
                return lenArgs
            }
        }
        // Fallback known NEC 78K0 protocol lengths if guarded with out-of-range 0xFF returned 0x05
        return when (cmd) {
            0x00, 0xC0, 0xC5 -> 0
            0x90, 0x9A, 0xA0 -> 1
            0x13, 0x22, 0x32, 0x40, 0xB0 -> 6
            0x20 -> 0
            else -> null
        }
    }

    // ==================== STAGE 4: TIMING, BOUNDARIES & SPEED ====================

    private suspend fun runStage4TimingAndBoundaries(
        sync: SyncLock,
        base: ProberBlueprintSummary
    ): ProberBlueprintSummary {
        var maxBaud = sync.baudRate
        var pageSize = 256
        var blockSize = 1024
        var flashEndHex = "0x007FFF (32 KB)"
        val rttSamples = mutableListOf(sync.latencySampleMs)

        // 1. Baud Rate Switch Probe (0x9A on NEC)
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val baudTest = transport.transceive(
                txBytes = buildNecCommandFrame(0x9A, byteArrayOf(0x02)), // 38400 bps code in 78K0
                expectedMax = 12,
                timeoutMs = 220,
                label = "Baud Switch Test [0x9A -> 38400]",
                stopOnCompleteNecFrame = true
            )
            if (parseNecStatus(baudTest) == ACK_NEC) {
                transport.configureUart(38400)
                delay(20)
                val ping = transport.transceive(buildNecCommandFrame(0x00), 12, 200, "Ping @ 38400 bps", stopOnCompleteNecFrame = true)
                if (parseNecStatus(ping) == ACK_NEC) {
                    maxBaud = 38400
                    appendLog(ProberLogDirection.SUCCESS, "نجح رفع السرعة عبر 0x9A إلى 38400 bps!")
                }
                // Restore original baud via hardware reset pulse so session remains deterministic
                transport.configureUart(sync.baudRate)
                if (sync.useResetToggle) transport.pulseResetSequence(sync.inverted)
                resyncLight(sync)
            }
        }

        // 2. Non-destructive Flash Boundary Probe via 0xB0 Checksum (128KB -> 64KB -> 32KB -> 16KB)
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val endCandidates = listOf(
                0x01FFFF to "0x01FFFF (128 KB)",
                0x00FFFF to "0x00FFFF (64 KB)",
                0x007FFF to "0x007FFF (32 KB)",
                0x003FFF to "0x003FFF (16 KB)"
            )
            for ((endAddr, label) in endCandidates) {
                currentCoroutineContext().ensureActive()
                val t0 = System.currentTimeMillis()
                val csCmd = buildNecCommandFrame(
                    0xB0,
                    byteArrayOf(
                        0x00, 0x00, 0x00,
                        ((endAddr ushr 16) and 0xFF).toByte(),
                        ((endAddr ushr 8) and 0xFF).toByte(),
                        (endAddr and 0xFF).toByte()
                    )
                )
                val rx = transport.transceive(csCmd, 16, 260, "Boundary Probe 0xB0 [$label]", stopOnCompleteNecFrame = true)
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || parseChecksum16(rx) != null) {
                    rttSamples.add((System.currentTimeMillis() - t0).toInt())
                    flashEndHex = label
                    break
                }
            }

            // 3. Block Size Boundary Probe via Read-Only 0x32 Blank Check (4KB -> 2KB -> 1KB -> 512B)
            val blockCandidates = listOf(1024, 512, 2048, 4096)
            for (bSize in blockCandidates) {
                currentCoroutineContext().ensureActive()
                val endOff = bSize - 1
                val rx = transport.transceive(
                    buildNecCommandFrame(0x32, byteArrayOf(0x00, 0x00, 0x00, 0x00, ((endOff ushr 8) and 0xFF).toByte(), (endOff and 0xFF).toByte())),
                    12,
                    220,
                    "Block Size Probe 0x32 [${bSize}B]",
                    stopOnCompleteNecFrame = true
                )
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                    blockSize = bSize
                    break
                }
            }

            // 4. Page Size Probe via 0x13 Read-Only Verify (256B -> 128B)
            val p128 = transport.transceive(
                buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())),
                12,
                220,
                "Page Size Probe 0x13 [256B]",
                stopOnCompleteNecFrame = true
            )
            pageSize = if (parseNecStatus(p128) == ACK_NEC) 256 else 128
        }

        val avgDelay = ((rttSamples.average() + 12.0).toInt()).coerceIn(15, 250)
        return base.copy(
            maxBaudTested = maxBaud,
            pageSizeBytes = pageSize,
            blockSizeBytes = blockSize,
            flashEndAddressHex = flashEndHex,
            safeDelayMs = avgDelay
        )
    }

    // ==================== STAGE 5: ADVANCED SCHOOLS & EMERGENCY RESTORE ====================

    private suspend fun runAdvancedUnlockSchools(sync: SyncLock): String {
        val sb = StringBuilder()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val csBefore = transport.transceive(
                buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())),
                16, 350, "Hot-Swap Block-0 CS [0xB0]", stopOnCompleteNecFrame = true
            )
            sb.appendLine("• فحص بصمة Block-0: ${if (csBefore.isNotEmpty()) "✅ (${csBefore.toHexStr()})" else "❌"}")

            val eraseB0 = transport.transceive(
                buildNecCommandFrame(0x22, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())),
                12, 900, "Hot-Swap Block-0 Erase [0x22]", stopOnCompleteNecFrame = true
            )
            val eSts = parseNecStatus(eraseB0)
            sb.appendLine("• مدرسة تصفير Block-0 (0x22): ${if (eSts == ACK_NEC) "✅ نجحت (قبل المسح)" else "❌ فشل (${formatByte(eSts)})"}")

            val partHdr = transport.transceive(
                buildNecCommandFrame(0x40, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                12, 350, "Hot-Swap Partial Write [0x40]", stopOnCompleteNecFrame = true
            )
            val pSts = parseNecStatus(partHdr)
            sb.appendLine("• مدرسة الكتابة الجزئية (0x40): ${if (pSts == ACK_NEC) "✅ قبل الترويسة" else "❌ (${formatByte(pSts)})"}")
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val rx = transport.transceive(byteArrayOf(0x92.toByte(), 0x6D), 6, 800, "STM32 Unprotect [0x92]")
            sb.appendLine("• مدرسة Readout Unprotect (0x92): ${if (rx.any { (it.toInt() and 0xFF) == ACK_STM32 }) "✅ نجحت" else "❌"}")
        } else {
            val ffId = byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xDF.toByte(), 0x00, 0x07, -1, -1, -1, -1, -1, -1, -1)
            transport.transceive(ffId, 8, 320, "R8C ID Probe [0xF5]")
            val sr = transport.transceive(byteArrayOf(0x70), 6, 300, "R8C SRD [0x70]")
            val unlocked = sr.size >= 2 && ((sr[1].toInt() and 0x0C) != 0)
            sb.appendLine("• فك قفل ID الافتراضي: ${if (unlocked) "✅ نجح" else "❌ مقفول"}")
        }
        return sb.toString().trim()
    }

    private suspend fun performEmergencyFlashWrite(sync: SyncLock, bytes: ByteArray, baseAddr: Int): Boolean {
        if (sync.framing == DetectedFraming.STM32_ACK) {
            // Mass Erase 0x43/0x44 then Write Memory 0x31 in 256B chunks
            val erase = transport.transceive(byteArrayOf(0x43, 0xBC.toByte()), 4, 400, "STM32 Erase [0x43]")
            if (erase.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                transport.transceive(byteArrayOf(0xFF.toByte(), 0x00), 4, 2500, "STM32 Mass Erase Confirm")
            }
            var offset = 0
            val startAddr = if (baseAddr < 0x08000000) 0x08000000 else baseAddr
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(256, bytes.size - offset)
                val addr = startAddr + offset
                val cmdAck = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 4, 250, "STM32 Write", logTxRx = false)
                if (cmdAck.none { (it.toInt() and 0xFF) == ACK_STM32 }) return false
                val aBytes = byteArrayOf(
                    ((addr ushr 24) and 0xFF).toByte(),
                    ((addr ushr 16) and 0xFF).toByte(),
                    ((addr ushr 8) and 0xFF).toByte(),
                    (addr and 0xFF).toByte()
                )
                val aCs = (aBytes[0].toInt() xor aBytes[1].toInt() xor aBytes[2].toInt() xor aBytes[3].toInt()) and 0xFF
                val addrAck = transport.transceive(aBytes + byteArrayOf(aCs.toByte()), 4, 250, "Addr", logTxRx = false)
                if (addrAck.none { (it.toInt() and 0xFF) == ACK_STM32 }) return false

                val slice = bytes.copyOfRange(offset, offset + chunkLen)
                val nMinus1 = (chunkLen - 1) and 0xFF
                var xorCs = nMinus1
                for (b in slice) xorCs = xorCs xor (b.toInt() and 0xFF)
                val pkt = byteArrayOf(nMinus1.toByte()) + slice + byteArrayOf(xorCs.toByte())
                val wrAck = transport.transceive(pkt, 4, 600, "Write @ 0x%08X".format(addr), logTxRx = false, paced64B = true)
                if (wrAck.none { (it.toInt() and 0xFF) == ACK_STM32 }) return false

                offset += chunkLen
                _state.value = _state.value.copy(restoreProgressText = "تمت كتابة $offset / ${bytes.size} بايت")
            }
            return true
        }

        // NEC 78K0 Emergency Flash Restore: Block Erase (0x22) -> Program (0x40) -> STX Pages (256B)
        val blockSize = _state.value.blueprintSummary.blockSizeBytes ?: 1024
        val pageSize = _state.value.blueprintSummary.pageSizeBytes ?: 256
        val totalBlocks = (bytes.size + blockSize - 1) / blockSize

        for (blk in 0 until totalBlocks) {
            currentCoroutineContext().ensureActive()
            val startAddr = baseAddr + blk * blockSize
            val endAddr = startAddr + blockSize - 1
            val blockBuf = ByteArray(blockSize) { 0xFF.toByte() }
            val copyLen = min(blockSize, bytes.size - blk * blockSize)
            if (copyLen > 0) System.arraycopy(bytes, blk * blockSize, blockBuf, 0, copyLen)

            val eraseRx = transport.transceive(
                buildNecCommandFrame(0x22, addrRangeBytes(startAddr, endAddr)),
                12, 950, "Restore Erase Block $blk [0x22]", stopOnCompleteNecFrame = true
            )
            if (parseNecStatus(eraseRx) != ACK_NEC) return false

            val progRx = transport.transceive(
                buildNecCommandFrame(0x40, addrRangeBytes(startAddr, endAddr)),
                12, 500, "Restore Program Block $blk [0x40]", stopOnCompleteNecFrame = true
            )
            if (parseNecStatus(progRx) != ACK_NEC) return false

            val pagesCount = blockSize / pageSize
            for (p in 0 until pagesCount) {
                currentCoroutineContext().ensureActive()
                val slice = blockBuf.copyOfRange(p * pageSize, (p + 1) * pageSize)
                val frame = buildNecDataFrame(slice, isLast = (p == pagesCount - 1))
                val pRx = transport.transceive(frame, 12, 900, "Restore Page $blk.$p", stopOnCompleteNecFrame = true, logTxRx = false, paced64B = true)
                if (parseNecStatus(pRx) != ACK_NEC) return false
            }
            _state.value = _state.value.copy(restoreProgressText = "استعادة البلوك ${blk + 1} / $totalBlocks")
        }
        return true
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

    private fun failPipeline(reason: String) {
        appendLog(ProberLogDirection.ERROR, reason)
        _state.value = _state.value.copy(
            phase = ProberStagePhase.ERROR,
            stageBanner = reason
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
        val endByte = if (isLast) ETX else 0x17
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
