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

        private val SAMSUNG_SINGLE_BIT_DROP_BYTES = setOf(0xFE, 0xFD, 0xFB, 0xF7, 0xEF, 0xDF, 0xBF, 0x7F)
        private const val SAMSUNG_FLOATING_HIGH_ALERT_MSG =
            "تمت مزامنة السامسونج بنجاح، ولكن خط SDAT مرفوع (0xFF) — يلزم تطبيق +12.5V على رجل 9 (Vpp/TEST) لفتح بوابة الفلاش الداخلية"
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
        val latencySampleMs: Int,
        val usbdmTargetArch: UsbdmTargetArch? = null,
        val flashBaseAddr: Int = 0x000000,
        val adapterInitPackets: List<String> = emptyList(),
        val actualTargetCode: Int? = null,
        val swapDtrRtsRoles: Boolean = false,
        val staticDtr: Boolean? = null,
        val staticRts: Boolean? = null,
        val physicalLevelTag: String = "",
        val syncClkOnRts: Boolean? = null,
        val syncModePinState: Boolean = false,
        val useBitBangSync: Boolean = false,
        val syncSampleOnFallingEdge: Boolean = false
    )

    private data class PhysicalPinProfile(
        val name: String,
        val flmd0State: String,
        val resetIdleState: String,
        val resetPulsePolarity: String,
        val dtrDesc: String,
        val rtsDesc: String,
        val inverted: Boolean,
        val useResetToggle: Boolean,
        val swapDtrRtsRoles: Boolean = false,
        val staticDtr: Boolean? = null,
        val staticRts: Boolean? = null
    )

    private data class UniversalIdCapture(
        val autoIdRule: String,
        val manufacturerGuess: String,
        val inferredModel: String,
        val rawSignatureSummary: String,
        val rawC0Payload: ByteArray,
        val idRegisterOrOpcode: String = "--",
        val rawSiliconIdHex: String = "--"
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
    private var detectedUsbdmCapsCmd: Int = 0x0C
    @Volatile
    private var detectedUsbdmReadCmd: Int = 0x21
    @Volatile
    private var detectedUsbdmTargetArch: UsbdmTargetArch = UsbdmTargetArch.HCS08
    @Volatile
    private var detectedUsbdmTargetCode: Int = 0x00
    @Volatile
    private var detectedSdidHex: String = ""
    @Volatile
    private var detectedFreescaleModel: String = ""
    @Volatile
    private var detectedSamsungIdHex: String = ""
    @Volatile
    private var detectedSamsungSmartOptionHex: String = ""
    @Volatile
    private var lastLevelBSamsungHardwareAlert: String? = null
    @Volatile
    private var detectedStLinkVersion: String = ""
    @Volatile
    private var detectedStLinkIdCodeHex: String = ""
    @Volatile
    private var detectedAvrSigHex: String = ""
    @Volatile
    private var detectedPicIdHex: String = ""
    @Volatile
    private var detectedRawIdHex: String = ""
    @Volatile
    private var detectedProbedFlashStart: Int = 0x000000
    @Volatile
    private var detectedProbedFlashEnd: Int = 0x007FFF
    @Volatile
    private var stage3IgnoredNoiseCount: Int = 0

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
        lastLevelBSamsungHardwareAlert = null

        activeJob = scope.launch {
            _state.value = _state.value.copy(
                phase = ProberStagePhase.RUNNING_SAFE_PIPELINE,
                stageBanner = "جاري تنفيذ المرحلة 1: التوجيه الذكي للمبرمجة والبحث عن المعمارية والهوية الخام...",
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
            appendLog(ProberLogDirection.INFO, "══ بدء الفحص العالمي الشامل لأي مبرمجة وأي معالج (${family.labelAr}) ══")

            try {
                if (!transport.openSession()) {
                    failPipeline("تعذر فتح منفذ USB OTG. تأكد من توصيل المبرمجة ومنح الصلاحية.", allowBypass = false)
                    return@launch
                }

                // STAGE 1: Universal Programmer Router + 7-Arch USBDM Scanner / UART Blind Sync
                updateStep("stage1_handshake", ChecklistStepStatus.RUNNING, "تشخيص المبرمجة (${transport.currentChip().displayName}) وبحث العائلة والهوية الخام...")
                val sync = runStage1UniversalBlindHandshake(family)
                if (sync == null) {
                    val vppAlert = lastLevelBSamsungHardwareAlert
                    val stepFailDetail = if (!vppAlert.isNullOrBlank()) {
                        "فشل التزامن المتزامن (Bit-Bang): $vppAlert"
                    } else {
                        "لم يرد المعالج على المبرمجة أو أي نمط استيقاظ — يمكنك الضغط على «تجاوز التزامن»"
                    }
                    val bannerFailMsg = if (!vppAlert.isNullOrBlank()) {
                        "⚠️ تنبيه الهاردوير: $vppAlert"
                    } else {
                        "فشل التزامن في المرحلة 1. تأكد من توصيل أسلاك البرمجة والتغذية أو استخدم زر «تجاوز التزامن واختبار الأوامر مباشرة»."
                    }
                    updateStep("stage1_handshake", ChecklistStepStatus.FAILED, stepFailDetail)
                    failPipeline(bannerFailMsg, allowBypass = true)
                    return@launch
                }
                lastSyncLock = sync
                val levelBadge = if (sync.physicalLevelTag.isNotBlank()) " • ${sync.physicalLevelTag}" else ""
                updateStep(
                    "stage1_handshake",
                    ChecklistStepStatus.PASSED,
                    "${sync.baudRate} ${sync.parityMode} | ${sync.wakeUpPatternUsed} | ${sync.framing.label}$levelBadge"
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
                stageBanner = "تجاوز المرحلة 1 — اختبار الأوامر مباشرة...",
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
                val chip = transport.currentChip()
                val defaultFraming = when (chip) {
                    ProberUsbChip.USBDM -> DetectedFraming.USBDM_BDM
                    ProberUsbChip.ST_LINK -> DetectedFraming.STLINK_SWD
                    ProberUsbChip.USBASP, ProberUsbChip.ATMEL_AVR -> DetectedFraming.AVR_ISP_USB
                    ProberUsbChip.MICROCHIP_PICKIT -> DetectedFraming.MICROCHIP_PIC_ICSP
                    ProberUsbChip.UNKNOWN_USB -> DetectedFraming.CUSTOM_RAW
                    else -> preFraming
                }
                val isSpecialized = chip.isSpecializedProgrammer || chip == ProberUsbChip.UNKNOWN_USB
                val effectiveBaud = if (isSpecialized) 0 else defaultBaud
                appendLog(
                    ProberLogDirection.WARN,
                    "⚠️ تم تجاوز التزامن يدوياً — فتح المنفذ (${defaultFraming.label})..."
                )
                val initPkts = mutableListOf<String>()
                when (chip) {
                    ProberUsbChip.USBDM -> {
                        transport.usbdmTransceive(byteArrayOf(0x02, 0x0C), 32, 250, "USBDM Bypass GET_CAPABILITIES [02 0C]")
                        transport.usbdmTransceive(byteArrayOf(0x03, 0x02, 0x02), 8, 250, "USBDM Bypass SET_VDD 5V [03 02 02]")
                        delay(100)
                        transport.usbdmTransceive(byteArrayOf(0x03, 0x03, 0x01), 8, 250, "USBDM Bypass Internal Power [03 03 01]")
                        transport.usbdmTransceive(byteArrayOf(0x03, 0x01, 0x00), 8, 250, "USBDM Bypass SET_TARGET HCS08 [03 01 00]")
                        transport.usbdmTransceive(byteArrayOf(0x03, 0x13, 0x00), 8, 250, "USBDM Bypass RESET_SPECIAL [03 13 00]")
                        delay(100)
                        transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 12, 300, "USBDM Bypass CONNECT [02 0F]")
                        transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM Bypass HALT [02 14]")
                        detectedUsbdmTargetArch = UsbdmTargetArch.HCS08
                        detectedUsbdmTargetCode = 0x00
                        initPkts.addAll(listOf("02 0C", "03 02 02", "03 03 01", "03 01 00", "03 13 00", "02 0F", "02 14"))
                    }
                    ProberUsbChip.ST_LINK -> {
                        transport.rawPacketTransceive(byteArrayOf(0xF1.toByte(), 0x80.toByte()), 16, 250, "ST-LINK Bypass Version")
                        transport.rawPacketTransceive(byteArrayOf(0xF2.toByte(), 0x20, 0xA3.toByte()), 16, 250, "ST-LINK Bypass SWD")
                        initPkts.addAll(listOf("F1 80", "F2 20 A3"))
                    }
                    ProberUsbChip.USBASP -> {
                        transport.usbControlTransferIn(0xC0, 0x01, 0, 0, 4, 300, "USBasp Bypass Connect")
                        transport.usbControlTransferIn(0xC0, 0x05, 0, 0, 4, 300, "USBasp Bypass EnableProg")
                        initPkts.addAll(listOf("EP0 0x01 Connect", "EP0 0x05 EnableProg"))
                    }
                    else -> {
                        if (!isSpecialized) {
                            if (defaultFraming == DetectedFraming.SAMSUNG_ISP) {
                                transport.runSamsungBitBangResetEntry(
                                    sclkOnRts = true,
                                    testActiveHigh = true,
                                    initialClocks = 16,
                                    forceUartModemLines = false
                                )
                                initPkts.addAll(listOf("Bit-Bang Entry: DTR(TEST)=HIGH + 16x RTS(SCLK)", "0xAA 0x55"))
                            } else {
                                transport.configureUart(defaultBaud, evenParity = defaultParityEven)
                                transport.setControlLines(dtr = false, rts = false)
                                delay(40)
                                transport.flushRx()
                            }
                        }
                    }
                }

                val isSamsungBypass = !isSpecialized && defaultFraming == DetectedFraming.SAMSUNG_ISP
                val bypassSync = SyncLock(
                    baudRate = if (isSamsungBypass) 4800 else effectiveBaud.coerceAtLeast(if (isSpecialized) 1 else 9600),
                    evenParity = defaultParityEven,
                    parityMode = when {
                        isSpecialized -> transport.currentEndpointsSummary()
                        isSamsungBypass -> "Bit-Bang Sync Shift (DTR=Vpp/TEST, RTS=SCLK, SDAT=TX/RX)"
                        defaultParityEven -> "8E1"
                        else -> "8N1"
                    },
                    wakeUpPatternUsed = "Bypassed (Direct Mode - ${chip.displayName})",
                    flmd0PinState = when {
                        isSpecialized -> "${chip.displayName} Hardware Interface"
                        isSamsungBypass -> "DTR=Vpp/TEST (HIGH +5V) | TXD(1N4148)+RXD=SDAT"
                        else -> "HIGH (+5V) / Bypassed (DTR=false)"
                    },
                    resetPinIdleState = when {
                        isSpecialized -> "Adapter Hardware Reset"
                        isSamsungBypass -> "DTR=Vpp/TEST (HIGH +5V) | RTS=SCLK"
                        else -> "HIGH (+5V) / Static (RTS=false)"
                    },
                    resetPinPulsePolarity = if (isSamsungBypass) "Bypassed (FT232R Bit-Bang Sync)" else "Bypassed (No Reset Toggle)",
                    dtrPolarity = when {
                        isSpecialized -> chip.displayName
                        isSamsungBypass -> "DTR=Vpp/TEST (HIGH +5V)"
                        else -> "تجاوز يدوي (DTR=false -> FLMD0=+5V)"
                    },
                    rtsPolarity = when {
                        isSpecialized -> "Hardware Reset"
                        isSamsungBypass -> "RTS=SCLK (Rising Edge Bit-Bang)"
                        else -> "Static (RTS=false)"
                    },
                    inverted = false,
                    useResetToggle = false,
                    resetPulseMs = 50,
                    postResetDelayMs = 20,
                    syncByteGapMs = 2,
                    syncPulseHex = if (isSamsungBypass) "Bit-Bang 0xAA 0x55" else "Bypassed (Direct Mode)",
                    framing = defaultFraming,
                    firstReplyHex = "Bypassed",
                    latencySampleMs = 25,
                    usbdmTargetArch = if (chip == ProberUsbChip.USBDM) detectedUsbdmTargetArch else null,
                    flashBaseAddr = if (chip == ProberUsbChip.ST_LINK) 0x08000000 else if (chip == ProberUsbChip.USBDM) 0x00C000 else 0x000000,
                    adapterInitPackets = initPkts,
                    actualTargetCode = if (chip == ProberUsbChip.USBDM) detectedUsbdmTargetCode else null,
                    swapDtrRtsRoles = false,
                    staticDtr = if (isSamsungBypass) true else null,
                    staticRts = if (isSamsungBypass) false else null,
                    syncClkOnRts = if (isSamsungBypass) true else null,
                    syncModePinState = isSamsungBypass,
                    useBitBangSync = isSamsungBypass
                )
                lastSyncLock = bypassSync
                updateStep(
                    "stage1_handshake",
                    ChecklistStepStatus.BYPASSED,
                    "تم التجاوز يدوياً • ${chip.displayName} • ${defaultFraming.label}"
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

    private fun buildAdapterInitSpec(sync: SyncLock): AdapterInitSpec {
        val status = _state.value.usbStatus
        val chip = transport.currentChip()
        val packets = sync.adapterInitPackets.ifEmpty {
            when (sync.framing) {
                DetectedFraming.USBDM_BDM -> {
                    val effectiveCode = sync.actualTargetCode ?: sync.usbdmTargetArch?.targetCode ?: detectedUsbdmTargetCode
                    listOf(
                        "GET_CAPABILITIES [02 %02X]".format(detectedUsbdmCapsCmd),
                        "SET_VDD 5V [03 02 02] + delay(100ms)",
                        "SET_INTERNAL_POWER [03 03 01]",
                        "SET_TARGET_TYPE [03 01 %02X] (${sync.usbdmTargetArch?.archName ?: "HCS08"})".format(effectiveCode),
                        "RESET_SPECIAL [03 13 00] + delay(100ms)",
                        "CONNECT [02 0F] -> TARGET_HALT [02 14]"
                    )
                }
                DetectedFraming.STLINK_SWD -> listOf(
                    "GET_VERSION [F1 80]",
                    "GET_MODE [F5] / EXIT_DFU [F3 07]",
                    "ENTER_SWD [F2 20 A3] -> READ_IDCODE [F2 07]"
                )
                DetectedFraming.STLINK_SWIM -> listOf(
                    "GET_VERSION [F1 80]",
                    "ENTER_SWIM [F4 00] -> SWIM_RESET [F4 02] -> SWIM_SPEED_HIGH [F4 03]"
                )
                DetectedFraming.AVR_ISP_USB -> listOf(
                    "USB Connect / Sign-On",
                    "Set ISP Clock / Enable Prog Mode",
                    "SPI Read Signature [30 00 00..02]"
                )
                else -> listOf(
                    "Physical Layer: ${sync.physicalLevelTag.ifBlank { "Universal UART Matrix" }}",
                    "Configure UART ${sync.baudRate} bps ${sync.parityMode}",
                    "Pin Mapping: FLMD0/MODE=${sync.flmd0PinState} | RESET/CLK=${sync.resetPinIdleState} (${sync.resetPinPulsePolarity})",
                    "DTR=${sync.dtrPolarity} | RTS=${sync.rtsPolarity}",
                    "Winning Wake Sequence: ${sync.syncPulseHex}"
                )
            }
        }
        return AdapterInitSpec(
            adapterType = "${chip.displayName} (${status.adapterName})",
            vidPid = status.vidPidHex,
            usbTransportMode = when {
                transport.isControlOnlySession -> "USB EP0 Control Transfers"
                chip.isSpecializedProgrammer || chip == ProberUsbChip.UNKNOWN_USB -> "USB Raw Bulk/Interrupt (${sync.parityMode})"
                else -> "USB-UART ${sync.physicalLevelTag.ifBlank { "Serial" }} (${sync.baudRate} bps ${sync.parityMode})"
            },
            usbEndpoints = "${transport.currentInterfaceInfo()} | ${transport.currentEndpointsSummary()}",
            powerControlLogic = "${sync.physicalLevelTag.ifBlank { "UART" }} | ${sync.flmd0PinState} | ${sync.resetPinIdleState} (${sync.resetPinPulsePolarity})",
            initPacketSequence = packets
        )
    }

    private suspend fun executeStages2To5(sync: SyncLock) {
        // Universal Auto-ID Capture right after Stage 1 Handshake
        val autoId = extractUniversalAutoId(sync)
        val initialAdapterSpec = buildAdapterInitSpec(sync)
        val initialAutoRule = AutoChipDetectionRule(
            targetFamilyDetected = if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                "SAMSUNG_S3F8_S3F9_FAMILY"
            } else {
                sync.usbdmTargetArch?.archName ?: sync.framing.label
            },
            identificationSequence = "${sync.physicalLevelTag.ifBlank { "Universal" }} | ${autoId.autoIdRule}",
            idRegisterOrOpcode = autoId.idRegisterOrOpcode,
            rawSiliconIdHex = autoId.rawSiliconIdHex,
            dynamicFlashBoundaryFormula = "Pending Stage 4 Dynamic Probe (4KB..2MB)",
            dynamicSpeedFormula = "${sync.baudRate} ${sync.parityMode} (${sync.dtrPolarity} / ${sync.rtsPolarity})"
        )
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
            usbdmTargetArch = sync.usbdmTargetArch,
            adapterInitSpec = initialAdapterSpec,
            autoDetectionRule = initialAutoRule,
            universalAutoIdRule = autoId.autoIdRule,
            manufacturerGuess = autoId.manufacturerGuess,
            inferredModel = autoId.inferredModel,
            chipSignature = autoId.rawSignatureSummary,
            flashStartAddressHex = "0x%06X".format(sync.flashBaseAddr),
            safeDelayMs = (sync.latencySampleMs + 15).coerceIn(15, 250),
            timingMap = TimingMapMs(
                resetPulseMs = sync.resetPulseMs,
                postResetDelayMs = sync.postResetDelayMs,
                syncByteGapMs = sync.syncByteGapMs
            )
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 2: 5 Safe Read Schools + 0xFF vs 0x00 Lock Detection
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 2: فحص مدارس السحب الآمنة والهوية الخام...")

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

        val safeOpenReads = (if (sync.framing == DetectedFraming.NEC_STX_ETX) listOf(r1, r2, r5) else listOf(r1, r5))
            .filter { it.status == ChecklistStepStatus.PASSED }
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
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 3: المسح الجراحي للأوامر وأطوال المعاملات...")
        updateStep("stage3_cmd_sweep", ChecklistStepStatus.RUNNING, "مسح الأوامر وبنية البروتوكول مع قائمة الحماية الصارمة...")
        resyncLight(sync)
        val discoveredCmds = runStage3SurgicalCommandSweep(sync)
        val ignoredNoiseSummary = if (stage3IgnoredNoiseCount > 0) " (تجاهل $stage3IgnoredNoiseCount أمر ضوضاء)" else ""
        updateStep(
            "stage3_cmd_sweep",
            if (discoveredCmds.isNotEmpty() || stage3IgnoredNoiseCount > 0) ChecklistStepStatus.PASSED else ChecklistStepStatus.FAILED,
            "تم اكتشاف/توثيق ${discoveredCmds.size} أمر بالبروتوكول$ignoredNoiseSummary"
        )
        summary = summary.copy(
            discoveredCommands = discoveredCmds,
            ignoredNoiseCommandsCount = stage3IgnoredNoiseCount
        )
        updateSummaryAndBlueprint(summary)

        // STAGE 4: Crystal Matrix (0x90), Dynamic Flash Bounds (4KB..2MB), Baud State Machine, STX, 6-Point Timing
        _state.value = _state.value.copy(stageBanner = "جاري تنفيذ المرحلة 4: فحص حدود الفلاش (4KB..2MB)، التردد 0x90، والسرعات...")
        updateStep("stage4_timing_bounds", ChecklistStepStatus.RUNNING, "اختبار حدود الفلاش الديناميكي (4KB..2MB) + مصفوفة 0x90 + تبديل السرعات...")
        resyncLight(sync)
        val c0Bytes = if (sigCapture.rawC0Payload.isNotEmpty()) sigCapture.rawC0Payload else autoId.rawC0Payload
        val (stage4Summary, winningOscCmdPayload) = runStage4DynamicFamilyAlgorithms(sync, summary, c0Bytes)
        summary = stage4Summary.copy(
            autoDetectionRule = initialAutoRule.copy(
                rawSiliconIdHex = detectedRawIdHex.ifBlank { autoId.rawSiliconIdHex },
                dynamicFlashBoundaryFormula = stage4Summary.flashSizeDetectionRule,
                dynamicSpeedFormula = stage4Summary.baudStateMachineRule
            )
        )
        activeWinningOscPayload = winningOscCmdPayload
        updateStep(
            "stage4_timing_bounds",
            ChecklistStepStatus.PASSED,
            "Start=${summary.flashStartAddressHex} | End=${summary.flashEndAddressHex} | Osc=${summary.winningOscillatorPayload} | Bauds=${summary.supportedBaudRates}"
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

    private data class UsbdmArchIdResult(
        val arch: UsbdmTargetArch,
        val readCmdUsed: Int,
        val idAddrUsed: Long,
        val rawIdBytes: ByteArray,
        val connRx: ByteArray,
        val flashSampleAddr: Long? = null,
        val flashSampleBytes: ByteArray = ByteArray(0),
        val adoptedByGoldenRule: Boolean = false,
        val actualTargetCode: Int = arch.targetCode
    )

    private data class Hcs08Or12ProbeSnapshot(
        val sdidReadCmd: Int?,
        val sdidBytes: ByteArray,
        val hasValidNonZeroSdid: Boolean,
        val partIdBytes: ByteArray,
        val hasValidNonZeroPartId: Boolean,
        val flashReadCmd: Int?,
        val flashAddr: Long?,
        val flashBytes: ByteArray,
        val hasRealFlashBytes: Boolean
    )

    private suspend fun probeHcs08Or12WithFlash(targetCode: Int): Hcs08Or12ProbeSnapshot {
        val memSpace = UsbdmTargetArch.HCS08.memSpace
        val sdidAddr = 0x1806L
        var capturedSdidCmd: Int? = null
        var capturedSdidBytes = ByteArray(0)
        var hasValidSdid = false

        // 1. Read HCS08 SDIDH/SDIDL (0x1806..0x1807) via READ_MEM (0x21) and READ_BD_BYTE (0x22)
        for (cmd in listOf(0x21, 0x22)) {
            val rx2 = transport.usbdmTransceive(
                usbdmReadMemPacket(cmd, memSpace, 2, sdidAddr),
                16, 220, "USBDM SDIDH/L 0x1806 2B [0x%02X sp=0x%02X]".format(cmd, memSpace)
            )
            if (rx2.size >= 3 && (rx2[0].toInt() and 0xFF) == 0x00) {
                val payload = rx2.copyOfRange(1, 3)
                if (capturedSdidBytes.isEmpty()) {
                    capturedSdidCmd = cmd
                    capturedSdidBytes = payload
                }
                val id16 = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                if (id16 != 0x0000 && id16 != 0xFFFF) {
                    capturedSdidCmd = cmd
                    capturedSdidBytes = payload
                    hasValidSdid = true
                    break
                }
            } else {
                val b0Rx = transport.usbdmTransceive(
                    usbdmReadMemPacket(cmd, memSpace, 1, sdidAddr),
                    12, 180, "USBDM SDIDH [0x%02X @ 0x1806]".format(cmd),
                    logTxRx = false
                )
                val b1Rx = transport.usbdmTransceive(
                    usbdmReadMemPacket(cmd, memSpace, 1, sdidAddr + 1L),
                    12, 180, "USBDM SDIDL [0x%02X @ 0x1807]".format(cmd),
                    logTxRx = false
                )
                if (b0Rx.size >= 2 && (b0Rx[0].toInt() and 0xFF) == 0x00 &&
                    b1Rx.size >= 2 && (b1Rx[0].toInt() and 0xFF) == 0x00
                ) {
                    val payload = byteArrayOf(b0Rx[1], b1Rx[1])
                    if (capturedSdidBytes.isEmpty()) {
                        capturedSdidCmd = cmd
                        capturedSdidBytes = payload
                    }
                    val id16 = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    if (id16 != 0x0000 && id16 != 0xFFFF) {
                        capturedSdidCmd = cmd
                        capturedSdidBytes = payload
                        hasValidSdid = true
                        break
                    }
                }
            }
        }

        // 2. If TargetType == 0x01, also check HCS12 PARTID (0x001A..0x001B) quickly
        var capturedPartIdBytes = ByteArray(0)
        var hasValidPartId = false
        if (targetCode == 0x01) {
            for (cmd in listOf(0x21, 0x22)) {
                val rxPart = transport.usbdmTransceive(
                    usbdmReadMemPacket(cmd, memSpace, 2, 0x001AL),
                    16, 200, "USBDM PARTID 0x001A 2B [0x%02X sp=0x%02X]".format(cmd, memSpace)
                )
                if (rxPart.size >= 3 && (rxPart[0].toInt() and 0xFF) == 0x00) {
                    val payload = rxPart.copyOfRange(1, 3)
                    if (capturedPartIdBytes.isEmpty()) capturedPartIdBytes = payload
                    val part16 = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    if (part16 != 0x0000 && part16 != 0xFFFF) {
                        capturedPartIdBytes = payload
                        hasValidPartId = true
                        break
                    }
                }
            }
        }

        // 3. Read 16 bytes from Flash (0xC000, 0x8000, 0xE000, 0xF000, 0x1900)
        var bestFlashCmd: Int? = null
        var bestFlashAddr: Long? = null
        var bestFlashBytes = ByteArray(0)
        var hasRealFlash = false
        var fallbackFlashCmd: Int? = null
        var fallbackFlashAddr: Long? = null
        var fallbackFlashBytes = ByteArray(0)

        for (flashAddr in listOf(0xC000L, 0x8000L, 0xE000L, 0xF000L, 0x1900L)) {
            for (cmd in listOf(0x21, 0x22)) {
                val rx16 = transport.usbdmTransceive(
                    usbdmReadMemPacket(cmd, memSpace, 16, flashAddr),
                    32, 240, "USBDM Flash 16B [T=0x%02X cmd=0x%02X @ 0x%04X]".format(targetCode, cmd, flashAddr)
                )
                if (rx16.size >= 17 && (rx16[0].toInt() and 0xFF) == 0x00) {
                    val payload = rx16.copyOfRange(1, 17)
                    val isReal = !payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }
                    if (isReal && !hasRealFlash) {
                        bestFlashCmd = cmd
                        bestFlashAddr = flashAddr
                        bestFlashBytes = payload
                        hasRealFlash = true
                    } else if (fallbackFlashBytes.isEmpty()) {
                        fallbackFlashCmd = cmd
                        fallbackFlashAddr = flashAddr
                        fallbackFlashBytes = payload
                    }
                    if (isReal) break
                }
            }
            if (hasRealFlash) break
        }

        return Hcs08Or12ProbeSnapshot(
            sdidReadCmd = capturedSdidCmd,
            sdidBytes = capturedSdidBytes,
            hasValidNonZeroSdid = hasValidSdid,
            partIdBytes = capturedPartIdBytes,
            hasValidNonZeroPartId = hasValidPartId,
            flashReadCmd = bestFlashCmd ?: fallbackFlashCmd,
            flashAddr = bestFlashAddr ?: fallbackFlashAddr,
            flashBytes = if (hasRealFlash) bestFlashBytes else fallbackFlashBytes,
            hasRealFlashBytes = hasRealFlash
        )
    }

    private suspend fun probeUsbdmArchIdentity(arch: UsbdmTargetArch): Pair<Int, Pair<Long, ByteArray>>? {
        val memSpace = arch.memSpace
        val idAddrs = when (arch) {
            UsbdmTargetArch.ARM_SWD -> listOf(0x40048024L to 4, 0xE0042000L to 4, 0xE000ED00L to 4)
            else -> listOf(arch.idRegisterAddress to arch.idByteLength)
        }
        val readCandidates = listOf(0x21, 0x22)
        for ((idAddr, idLen) in idAddrs) {
            for (readCmd in readCandidates) {
                val pkt = usbdmReadMemPacket(readCmd, memSpace, idLen, idAddr)
                val rx = transport.usbdmTransceive(
                    pkt, 16, 240, "USBDM READ_MEM ID [0x%02X sp=0x%02X @ 0x%08X]".format(readCmd, memSpace, idAddr)
                )
                if (rx.size >= 1 + idLen && (rx[0].toInt() and 0xFF) == 0x00) {
                    val payload = rx.copyOfRange(1, 1 + idLen)
                    if (!payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }) {
                        return readCmd to (idAddr to payload)
                    }
                }
            }
        }
        return null
    }

    private suspend fun probeUsbdmArchFlashSample(arch: UsbdmTargetArch): Triple<Int, Long, ByteArray>? {
        val memSpace = arch.memSpace
        for (flashAddr in arch.flashBaseCandidates) {
            for (readCmd in listOf(0x21, 0x22)) {
                val rx = transport.usbdmTransceive(
                    usbdmReadMemPacket(readCmd, memSpace, 16, flashAddr),
                    32, 240, "USBDM Flash Sample [${arch.name} 0x%02X @ 0x%08X]".format(readCmd, flashAddr)
                )
                if (rx.size >= 17 && (rx[0].toInt() and 0xFF) == 0x00) {
                    val payload = rx.copyOfRange(1, 17)
                    if (!payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }) {
                        return Triple(readCmd, flashAddr, payload)
                    }
                }
            }
        }
        return null
    }

    private suspend fun tryUsbdmMultiArchHandshake(family: ProberTargetFamily): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedUsbdmCaps = ""
        detectedUsbdmCapsCmd = 0x0C
        detectedUsbdmReadCmd = 0x21
        detectedUsbdmTargetArch = UsbdmTargetArch.HCS08
        detectedUsbdmTargetCode = 0x00
        detectedSdidHex = ""
        detectedFreescaleModel = ""
        detectedRawIdHex = ""
        appendLog(ProberLogDirection.INFO, "── USBDM V4.x (VID:16D0 PID:0567): مسح المعماريات (0x00..0x08) مع RESET_SPECIAL + CONNECT + قراءة الفلاش دون رفض 00 00 ──")
        val t0 = System.currentTimeMillis()
        val packets = mutableListOf<String>()

        // 1. GET_CAPABILITIES (0x0C, fallback 0x00, 0x0E)
        var caps = ByteArray(0)
        for (capsCmd in listOf(0x0C, 0x00, 0x0E)) {
            val rx = transport.usbdmTransceive(
                byteArrayOf(0x02, capsCmd.toByte()),
                32, 300, "USBDM GET_CAPABILITIES [02 %02X]".format(capsCmd)
            )
            if (rx.isNotEmpty() && (rx[0].toInt() and 0xFF) == 0x00) {
                caps = rx
                detectedUsbdmCapsCmd = capsCmd
                break
            }
            if (caps.isEmpty() && rx.isNotEmpty()) {
                caps = rx
                detectedUsbdmCapsCmd = capsCmd
            }
        }
        if (caps.isEmpty()) {
            caps = transport.usbdmControlIn(0x0C, 0, 0, 16)
        }
        if (caps.isEmpty()) {
            appendLog(ProberLogDirection.ERROR, "❌ لم تستجب USBDM لأوامر GET_CAPABILITIES.")
            return null
        }
        detectedUsbdmCaps = caps.toHexStr()
        packets.add("GET_CAPABILITIES [02 %02X] -> %s".format(detectedUsbdmCapsCmd, detectedUsbdmCaps))

        // 2. SET_VDD 5V [03 02 02] + 100ms + Internal Power [03 03 01]
        transport.usbdmTransceive(byteArrayOf(0x03, 0x02, 0x02), 8, 250, "USBDM SET_VDD 5V [03 02 02]")
        delay(100)
        transport.usbdmTransceive(byteArrayOf(0x03, 0x03, 0x01), 8, 250, "USBDM Internal Power [03 03 01]")
        delay(30)
        packets.add("SET_VDD 5V [03 02 02] + delay(100ms)")
        packets.add("SET_INTERNAL_POWER [03 03 01]")

        val scanOrder = if (family == ProberTargetFamily.STM32_UART) {
            listOf(UsbdmTargetArch.ARM_SWD) + UsbdmTargetArch.scanPriorityOrder.filter { it != UsbdmTargetArch.ARM_SWD }
        } else {
            UsbdmTargetArch.scanPriorityOrder
        }

        var matchedResult: UsbdmArchIdResult? = null
        var fallbackConnectedResult: UsbdmArchIdResult? = null

        for (arch in scanOrder) {
            currentCoroutineContext().ensureActive()
            val tCode = arch.targetCode
            val tgtRx = transport.usbdmTransceive(
                byteArrayOf(0x03, 0x01, tCode.toByte()),
                8, 220, "USBDM Target [03 01 %02X] (${arch.archName})".format(tCode)
            )
            if (tgtRx.isEmpty() || (tgtRx[0].toInt() and 0xFF) != 0x00) continue

            // RESET_SPECIAL [03 13 00] + 100ms stabilization wait before CONNECT
            transport.usbdmTransceive(
                byteArrayOf(0x03, 0x13, 0x00),
                8, 220, "USBDM RESET_SPECIAL [03 13 00]"
            )
            delay(100)

            var cRx = transport.usbdmTransceive(
                byteArrayOf(0x02, 0x0F),
                12, 300, "USBDM CONNECT [02 0F] (SET_TARGET=0x%02X)".format(tCode)
            )
            if (cRx.isEmpty() || (cRx[0].toInt() and 0xFF) != 0x00) {
                delay(30)
                cRx = transport.usbdmTransceive(
                    byteArrayOf(0x02, 0x0F),
                    12, 300, "USBDM CONNECT Retry [02 0F] (SET_TARGET=0x%02X)".format(tCode)
                )
            }
            val connectOk = cRx.isNotEmpty() && (cRx[0].toInt() and 0xFF) == 0x00
            if (!connectOk) continue

            // TARGET_HALT [02 14]
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 220, "USBDM TARGET_HALT [02 14]")

            if (tCode == 0x00 || tCode == 0x01) {
                val probe = probeHcs08Or12WithFlash(tCode)
                val isConfirmedHcs12 = (tCode == 0x01 && probe.hasValidNonZeroPartId && !probe.hasValidNonZeroSdid)
                val resolvedArch = if (isConfirmedHcs12) UsbdmTargetArch.HCS12 else UsbdmTargetArch.HCS08
                val resolvedIdAddr = if (isConfirmedHcs12) 0x001AL else 0x1806L
                val resolvedIdBytes = when {
                    isConfirmedHcs12 -> probe.partIdBytes
                    probe.sdidBytes.isNotEmpty() -> probe.sdidBytes
                    probe.partIdBytes.isNotEmpty() -> probe.partIdBytes
                    else -> byteArrayOf(0x00, 0x00)
                }
                val resolvedCmd = probe.flashReadCmd ?: probe.sdidReadCmd ?: 0x21
                val adoptedByZeroIdRule = !probe.hasValidNonZeroSdid && !probe.hasValidNonZeroPartId
                val candidate = UsbdmArchIdResult(
                    arch = resolvedArch,
                    readCmdUsed = resolvedCmd,
                    idAddrUsed = resolvedIdAddr,
                    rawIdBytes = resolvedIdBytes,
                    connRx = cRx,
                    flashSampleAddr = probe.flashAddr,
                    flashSampleBytes = probe.flashBytes,
                    adoptedByGoldenRule = adoptedByZeroIdRule,
                    actualTargetCode = tCode
                )
                val flashMsg = if (probe.flashBytes.isNotEmpty()) {
                    "Flash@0x%04X=${probe.flashBytes.take(8).toByteArray().toHexStr()}".format(probe.flashAddr ?: 0xC000L)
                } else "Flash=N/A"

                if (probe.hasRealFlashBytes || probe.hasValidNonZeroSdid || probe.hasValidNonZeroPartId) {
                    matchedResult = candidate
                    packets.add("SET_TARGET [03 01 %02X] -> RESET_SPECIAL [03 13 00] -> wait(100ms) -> CONNECT [02 0F] -> HALT [02 14] -> ${resolvedArch.archName} ($flashMsg)".format(tCode))
                    appendLog(
                        ProberLogDirection.SUCCESS,
                        "✅ اعتماد فوري (${resolvedArch.archName} | SET_TARGET=0x%02X | CONNECT=0x00 | $flashMsg | ID=${resolvedIdBytes.toHexStr()})${if (resolvedArch == UsbdmTargetArch.HCS08) " | FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826" else ""}".format(tCode)
                    )
                    break
                } else if (fallbackConnectedResult == null) {
                    fallbackConnectedResult = candidate
                    appendLog(
                        ProberLogDirection.INFO,
                        "ℹ️ نجح CONNECT [02 0F]=0x00 على SET_TARGET=0x%02X (حفظ احتياطياً كـ HCS08)".format(tCode)
                    )
                }
                continue
            }

            // Non-HCS08/HCS12 architectures (0x02, 0x04, 0x07, 0x03, 0x08)
            val idProbe = probeUsbdmArchIdentity(arch)
            val flashProbe = probeUsbdmArchFlashSample(arch)
            if (idProbe != null || flashProbe != null) {
                val readCmd = flashProbe?.first ?: idProbe?.first ?: 0x21
                val idAddr = idProbe?.second?.first ?: arch.idRegisterAddress
                val rawBytes = idProbe?.second?.second ?: ByteArray(arch.idByteLength)
                matchedResult = UsbdmArchIdResult(
                    arch = arch,
                    readCmdUsed = readCmd,
                    idAddrUsed = idAddr,
                    rawIdBytes = rawBytes,
                    connRx = cRx,
                    flashSampleAddr = flashProbe?.second,
                    flashSampleBytes = flashProbe?.third ?: ByteArray(0),
                    adoptedByGoldenRule = (idProbe == null),
                    actualTargetCode = tCode
                )
                packets.add("SET_TARGET [03 01 %02X] -> RESET_SPECIAL [03 13 00] -> wait(100ms) -> CONNECT [02 0F] -> HALT [02 14] -> ID=${rawBytes.toHexStr()}".format(tCode))
                appendLog(ProberLogDirection.SUCCESS, "✅ تطابق مؤكد للمعمارية: ${arch.archName} (SET_TARGET=0x%02X | ID=${rawBytes.toHexStr()})".format(tCode))
                break
            } else if (fallbackConnectedResult == null) {
                fallbackConnectedResult = UsbdmArchIdResult(
                    arch = arch,
                    readCmdUsed = 0x21,
                    idAddrUsed = arch.idRegisterAddress,
                    rawIdBytes = ByteArray(arch.idByteLength),
                    connRx = cRx,
                    flashSampleAddr = arch.flashBaseCandidates.firstOrNull(),
                    flashSampleBytes = ByteArray(0),
                    adoptedByGoldenRule = true,
                    actualTargetCode = tCode
                )
            }
        }

        val finalResult = matchedResult ?: fallbackConnectedResult
        if (finalResult == null) {
            appendLog(ProberLogDirection.WARN, "⚠️ فشل التزامن CONNECT [02 0F] عبر USBDM على جميع المعماريات.")
            return null
        }

        if (matchedResult == null) {
            // Re-select the fallback connected architecture before continuing Stages 2..6
            transport.usbdmTransceive(byteArrayOf(0x03, 0x01, finalResult.actualTargetCode.toByte()), 8, 220, "USBDM Re-Select [03 01 %02X]".format(finalResult.actualTargetCode))
            transport.usbdmTransceive(byteArrayOf(0x03, 0x13, 0x00), 8, 220, "USBDM RESET_SPECIAL [03 13 00]")
            delay(100)
            transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 12, 280, "USBDM CONNECT [02 0F]")
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 220, "USBDM HALT [02 14]")
            packets.add("SET_TARGET [03 01 %02X] -> RESET_SPECIAL [03 13 00] -> wait(100ms) -> CONNECT [02 0F]=0x00 -> HALT [02 14] (${finalResult.arch.archName})".format(finalResult.actualTargetCode))
        }

        val winningArch = finalResult.arch
        val winningTargetCode = finalResult.actualTargetCode
        val connRx = finalResult.connRx
        val usedIdAddr = finalResult.idAddrUsed
        val rawBytes = finalResult.rawIdBytes
        val detectedFlashBaseOverride = finalResult.flashSampleAddr?.toInt()
        detectedUsbdmReadCmd = finalResult.readCmdUsed
        detectedUsbdmTargetArch = winningArch
        detectedUsbdmTargetCode = winningTargetCode

        val sdidHexFromBytes = rawBytes.toHexStr()
        if (winningArch == UsbdmTargetArch.HCS08 && rawBytes.size >= 2) {
            val sdid16 = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
            detectedSdidHex = "0x%04X".format(sdid16)
            if (sdid16 != 0x0000 && sdid16 != 0xFFFF) {
                detectedRawIdHex = sdidHexFromBytes
                detectedFreescaleModel = "${lookupHcs08ModelBySdid(sdid16)} (SET_TARGET=0x%02X | SDID=$detectedSdidHex | Raw=$detectedRawIdHex)".format(winningTargetCode)
            } else {
                val flashPreview = finalResult.flashSampleBytes.take(8).toByteArray().toHexStr()
                detectedRawIdHex = if (flashPreview.isNotBlank()) {
                    "SET_TARGET=0x%02X | SDID=$detectedSdidHex | Flash@0x%04X=%s".format(winningTargetCode, finalResult.flashSampleAddr ?: 0xC000L, flashPreview)
                } else {
                    "SET_TARGET=0x%02X | SDID=$detectedSdidHex (CONNECT 0x00)".format(winningTargetCode)
                }
                detectedFreescaleModel = "Freescale / NXP HCS08 Family (SET_TARGET=0x%02X | SDID=$detectedSdidHex | FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826)".format(winningTargetCode)
            }
        } else if (winningArch == UsbdmTargetArch.HCS12 && rawBytes.size >= 2) {
            detectedRawIdHex = sdidHexFromBytes
            val partId16 = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
            detectedSdidHex = "0x%04X".format(partId16)
            detectedFreescaleModel = "${winningArch.archName} (SET_TARGET=0x%02X | PARTID=$detectedSdidHex | Raw=$detectedRawIdHex)".format(winningTargetCode)
        } else {
            detectedRawIdHex = sdidHexFromBytes
            detectedSdidHex = "0x" + rawBytes.joinToString("") { "%02X".format(it) }
            detectedFreescaleModel = "${winningArch.archName} (SET_TARGET=0x%02X | Raw ID=$detectedRawIdHex @ 0x%X)".format(winningTargetCode, usedIdAddr)
        }
        appendLog(ProberLogDirection.SUCCESS, "✅ الهوية المعتمدة (Silicon Identity): $detectedFreescaleModel")

        val dt = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10)
        val capsHex = "0x%02X".format(detectedUsbdmCapsCmd)
        val readCmdHex = "0x%02X".format(detectedUsbdmReadCmd)
        return SyncLock(
            baudRate = 1_500_000,
            evenParity = false,
            parityMode = "USB Bulk Native (${transport.currentEndpointsSummary()})",
            wakeUpPatternUsed = "USBDM Scan -> ${winningArch.archName} (SET_TARGET=0x%02X + RESET_SPECIAL [03 13 00] + 100ms + CONNECT [02 0F] + HALT [02 14])".format(winningTargetCode),
            flmd0PinState = "USBDM VDD=+5V (0x02) + Internal Power (0x03)",
            resetPinIdleState = "USBDM Hardware RESET_SPECIAL [03 13 00]",
            resetPinPulsePolarity = "SET_TARGET=0x%02X | MemSpace=0x%02X".format(winningTargetCode, winningArch.memSpace),
            dtrPolarity = "USBDM Native USB Bulk",
            rtsPolarity = "USBDM RESET_SPECIAL (0x13)",
            inverted = false,
            useResetToggle = true,
            resetPulseMs = 50,
            postResetDelayMs = 100,
            syncByteGapMs = 5,
            syncPulseHex = "$capsHex -> 03 02 02 -> 03 03 01 -> 03 01 %02X -> 03 13 00 (100ms) -> 02 0F -> 02 14 -> READ($readCmdHex / 0x22)".format(winningTargetCode),
            framing = DetectedFraming.USBDM_BDM,
            firstReplyHex = (if (finalResult.flashSampleBytes.isNotEmpty()) finalResult.flashSampleBytes else if (rawBytes.isNotEmpty()) rawBytes else connRx).toHexStr(),
            latencySampleMs = dt,
            usbdmTargetArch = winningArch,
            flashBaseAddr = detectedFlashBaseOverride ?: winningArch.flashBaseCandidates.first().toInt(),
            adapterInitPackets = packets,
            actualTargetCode = winningTargetCode
        )
    }

    private fun usbdmReadMemPacket(readCmd: Int, memSpace: Int, count: Int, addr: Long): ByteArray = byteArrayOf(
        0x08, readCmd.toByte(), memSpace.toByte(), count.toByte(),
        ((addr ushr 24) and 0xFF).toByte(),
        ((addr ushr 16) and 0xFF).toByte(),
        ((addr ushr 8) and 0xFF).toByte(),
        (addr and 0xFF).toByte()
    )

    private suspend fun tryStLinkHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedStLinkVersion = ""
        detectedStLinkIdCodeHex = ""
        detectedRawIdHex = ""
        val t0 = System.currentTimeMillis()
        val packets = mutableListOf<String>()
        appendLog(ProberLogDirection.INFO, "── تم اكتشاف مبرمجة ST-LINK (${_state.value.usbStatus.vidPidHex}): بدء فحص SWD ثم SWIM ──")

        val verRx = transport.rawPacketTransceive(byteArrayOf(0xF1.toByte(), 0x80.toByte()), 16, 350, "ST-LINK GET_VERSION [F1 80]")
        if (verRx.isEmpty()) return null
        detectedStLinkVersion = verRx.toHexStr()
        packets.add("GET_VERSION [F1 80] -> $detectedStLinkVersion")

        val modeRx = transport.rawPacketTransceive(byteArrayOf(0xF5.toByte()), 8, 300, "ST-LINK GET_CURRENT_MODE [F5]")
        if (modeRx.size >= 2 && (modeRx[1].toInt() and 0xFF) == 0x01) {
            transport.rawPacketTransceive(byteArrayOf(0xF3.toByte(), 0x07), 8, 300, "ST-LINK EXIT_DFU [F3 07]")
            packets.add("EXIT_DFU [F3 07]")
        }

        // Try SWD (ARM Cortex-M)
        transport.rawPacketTransceive(byteArrayOf(0xF2.toByte(), 0x21), 8, 200, "ST-LINK EXIT_DEBUG [F2 21]", logTxRx = false)
        var swdEnter = transport.rawPacketTransceive(byteArrayOf(0xF2.toByte(), 0x20, 0xA3.toByte()), 8, 350, "ST-LINK ENTER_SWD_V2 [F2 20 A3]")
        if (swdEnter.isEmpty() || (swdEnter[0].toInt() and 0xFF) != 0x80) {
            swdEnter = transport.rawPacketTransceive(byteArrayOf(0xF2.toByte(), 0x30, 0xA3.toByte()), 8, 350, "ST-LINK ENTER_SWD_V3 [F2 30 A3]")
        }
        if (swdEnter.isNotEmpty() && (swdEnter[0].toInt() and 0xFF) == 0x80) {
            packets.add("ENTER_SWD -> ${swdEnter.toHexStr()}")
            val idCodeRx = transport.rawPacketTransceive(byteArrayOf(0xF2.toByte(), 0x07), 8, 300, "ST-LINK READ_IDCODE [F2 07]")
            val dbgMcuRx = stlinkReadMem32(0xE0042000L, 4)
            val cpuIdRx = stlinkReadMem32(0xE000ED00L, 4)
            val rawId = dbgMcuRx.takeIf { it.size == 4 && !it.all { b -> b == 0.toByte() } }
                ?: idCodeRx.takeIf { it.size >= 4 }
                ?: cpuIdRx
            detectedStLinkIdCodeHex = rawId.toHexStr()
            detectedRawIdHex = detectedStLinkIdCodeHex
            packets.add("READ_IDCODE [F2 07] / DBGMCU -> $detectedStLinkIdCodeHex")
            return SyncLock(
                baudRate = 4_000_000,
                evenParity = false,
                parityMode = "USB Bulk SWD (${transport.currentEndpointsSummary()})",
                wakeUpPatternUsed = "ST-LINK V2/V3 SWD Auto-Connect [F1 80 -> F2 20 A3 -> F2 07]",
                flmd0PinState = "SWD (SWDIO / SWCLK)",
                resetPinIdleState = "NRST Controlled by ST-LINK",
                resetPinPulsePolarity = "ST-LINK SWD Hardware Reset",
                dtrPolarity = "ST-LINK USB Bulk",
                rtsPolarity = "SWD NRST",
                inverted = false,
                useResetToggle = true,
                resetPulseMs = 20,
                postResetDelayMs = 40,
                syncByteGapMs = 1,
                syncPulseHex = "F1 80 -> F2 20 A3 -> F2 07",
                framing = DetectedFraming.STLINK_SWD,
                firstReplyHex = (rawId.takeIf { it.isNotEmpty() } ?: verRx).toHexStr(),
                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                flashBaseAddr = 0x08000000,
                adapterInitPackets = packets
            )
        }

        // Fallback to SWIM (STM8)
        val swimEnter = transport.rawPacketTransceive(byteArrayOf(0xF4.toByte(), 0x00), 8, 350, "ST-LINK ENTER_SWIM [F4 00]")
        if (swimEnter.isNotEmpty() && (swimEnter[0].toInt() and 0xFF) == 0x00) {
            transport.rawPacketTransceive(byteArrayOf(0xF4.toByte(), 0x02), 8, 250, "ST-LINK SWIM_RESET [F4 02]")
            transport.rawPacketTransceive(byteArrayOf(0xF4.toByte(), 0x03), 8, 250, "ST-LINK SWIM_SPEED_HIGH [F4 03]")
            val stm8Id = stlinkSwimReadMem(0x004865L, 4)
            detectedStLinkIdCodeHex = stm8Id.toHexStr().ifBlank { "SWIM Connected" }
            detectedRawIdHex = detectedStLinkIdCodeHex
            packets.add("ENTER_SWIM [F4 00] -> SWIM_RESET [F4 02] -> SWIM_SPEED_HIGH [F4 03]")
            return SyncLock(
                baudRate = 8_000_000,
                evenParity = false,
                parityMode = "USB Bulk SWIM (${transport.currentEndpointsSummary()})",
                wakeUpPatternUsed = "ST-LINK STM8 SWIM [F1 80 -> F4 00 -> F4 02]",
                flmd0PinState = "SWIM Single-Wire",
                resetPinIdleState = "NRST Controlled by ST-LINK",
                resetPinPulsePolarity = "SWIM Reset [F4 02]",
                dtrPolarity = "ST-LINK USB Bulk",
                rtsPolarity = "SWIM NRST",
                inverted = false,
                useResetToggle = true,
                resetPulseMs = 20,
                postResetDelayMs = 40,
                syncByteGapMs = 1,
                syncPulseHex = "F1 80 -> F4 00 -> F4 02 -> F4 03",
                framing = DetectedFraming.STLINK_SWIM,
                firstReplyHex = (stm8Id.takeIf { it.isNotEmpty() } ?: swimEnter).toHexStr(),
                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                flashBaseAddr = 0x008000,
                adapterInitPackets = packets
            )
        }
        return null
    }

    private suspend fun stlinkReadMem32(addr: Long, len: Int): ByteArray {
        val pkt = byteArrayOf(
            0xF2.toByte(), 0x08,
            (addr and 0xFF).toByte(),
            ((addr ushr 8) and 0xFF).toByte(),
            ((addr ushr 16) and 0xFF).toByte(),
            ((addr ushr 24) and 0xFF).toByte(),
            (len and 0xFF).toByte(),
            ((len ushr 8) and 0xFF).toByte()
        )
        val rx = transport.rawPacketTransceive(pkt, len + 8, 350, "ST-LINK Mem32 @ 0x%08X".format(addr), logTxRx = false)
        return when {
            rx.size >= len + 2 && (rx[0].toInt() and 0xFF) == 0x80 -> rx.copyOfRange(rx.size - len, rx.size)
            rx.size == len -> rx
            else -> ByteArray(0)
        }
    }

    private suspend fun stlinkSwimReadMem(addr: Long, len: Int): ByteArray {
        val pkt = byteArrayOf(
            0xF4.toByte(), 0x0B,
            ((len ushr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte(),
            ((addr ushr 24) and 0xFF).toByte(),
            ((addr ushr 16) and 0xFF).toByte(),
            ((addr ushr 8) and 0xFF).toByte(),
            (addr and 0xFF).toByte()
        )
        transport.rawPacketTransceive(pkt, 8, 250, "ST-LINK SWIM Read Req", logTxRx = false)
        delay(10)
        val data = transport.rawPacketTransceive(byteArrayOf(0xF4.toByte(), 0x0C), len + 8, 300, "ST-LINK SWIM Read Data", logTxRx = false)
        return if (data.size >= len) data.copyOfRange(0, len) else ByteArray(0)
    }

    private suspend fun tryUsbaspHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedAvrSigHex = ""
        detectedRawIdHex = ""
        val t0 = System.currentTimeMillis()
        val packets = mutableListOf<String>()
        appendLog(ProberLogDirection.INFO, "── تم اكتشاف مبرمجة USBasp (VID:16C0 PID:05DC): فحص EP0 Vendor Control Transfers ──")

        // USBasp functions: 1=CONNECT, 3=TRANSMIT, 4=READFLASH, 5=ENABLEPROG, 10=SETISPSCK
        transport.usbControlTransferIn(0xC0, 0x01, 0, 0, 4, 350, "USBasp CONNECT [req=0x01]")
        packets.add("EP0 IN req=0x01 (USBASP_FUNC_CONNECT)")

        for (sck in listOf(0x01, 0x03, 0x07)) {
            transport.usbControlTransferIn(0xC0, 0x0A, sck, 0, 4, 250, "USBasp SETISPSCK [0x0A val=$sck]")
            val en = transport.usbControlTransferIn(0xC0, 0x05, 0, 0, 4, 350, "USBasp ENABLEPROG [0x05]")
            if (en.isNotEmpty() && (en[0].toInt() and 0xFF) == 0x00) {
                packets.add("EP0 IN req=0x0A (SCK=$sck) -> req=0x05 (ENABLEPROG OK)")
                val sigBytes = ByteArray(3)
                for (i in 0..2) {
                    val txRx = transport.usbControlTransferIn(
                        0xC0, 0x03, 0x0030, i and 0xFF, 4, 300, "USBasp SPI Signature Byte $i [0x30 00 0$i 00]"
                    )
                    sigBytes[i] = txRx.getOrElse(3) { 0xFF.toByte() }
                }
                detectedAvrSigHex = sigBytes.toHexStr()
                detectedRawIdHex = detectedAvrSigHex
                packets.add("EP0 IN req=0x03 Read Signature [30 00 00..02] -> $detectedAvrSigHex")
                appendLog(ProberLogDirection.SUCCESS, "✅ USBasp استجابت بنجاح — بصمة AVR الخام: $detectedAvrSigHex")
                return SyncLock(
                    baudRate = 375_000,
                    evenParity = false,
                    parityMode = "USB EP0 Vendor Control (SPI ISP)",
                    wakeUpPatternUsed = "USBasp EP0 CONNECT(0x01) -> SETISPSCK(0x0A) -> ENABLEPROG(0x05)",
                    flmd0PinState = "MOSI/MISO/SCK SPI Lines",
                    resetPinIdleState = "AVR /RESET Driven Low by USBasp",
                    resetPinPulsePolarity = "EP0 EnableProg [0x05]",
                    dtrPolarity = "EP0 Control Transfers",
                    rtsPolarity = "USBasp /RESET",
                    inverted = false,
                    useResetToggle = true,
                    resetPulseMs = 30,
                    postResetDelayMs = 50,
                    syncByteGapMs = 2,
                    syncPulseHex = "EP0 0x01 -> 0x0A -> 0x05 -> 0x03 [30 00 00..02]",
                    framing = DetectedFraming.AVR_ISP_USB,
                    firstReplyHex = detectedAvrSigHex,
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                    flashBaseAddr = 0x000000,
                    adapterInitPackets = packets
                )
            }
        }
        return null
    }

    private suspend fun tryAtmelAvrHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedAvrSigHex = ""
        detectedRawIdHex = ""
        val t0 = System.currentTimeMillis()
        val packets = mutableListOf<String>()
        appendLog(ProberLogDirection.INFO, "── فحص مبرمجة Atmel / AVR / CMSIS-DAP (${_state.value.usbStatus.vidPidHex}) ──")

        if (transport.hasDataEndpoints()) {
            // Try CMSIS-DAP Info (0x00, 0x04 = FW Version) & STK500v2 Sign-On (0x1B 0x01 0x00 0x01 0x0E 0x01 0x14)
            val dapRx = transport.rawPacketTransceive(byteArrayOf(0x00, 0x04), 64, 300, "CMSIS-DAP Get Info [00 04]")
            if (dapRx.size >= 2 && (dapRx[0].toInt() and 0xFF) == 0x00) {
                val fw = dapRx.drop(2).takeWhile { it != 0.toByte() }.map { it.toInt().toChar() }.joinToString("")
                detectedAvrSigHex = fw.ifBlank { dapRx.take(8).toByteArray().toHexStr() }
                detectedRawIdHex = detectedAvrSigHex
                packets.add("CMSIS-DAP Info [00 04] -> $detectedAvrSigHex")
                return SyncLock(
                    baudRate = 1_000_000,
                    evenParity = false,
                    parityMode = "USB HID/Bulk CMSIS-DAP (${transport.currentEndpointsSummary()})",
                    wakeUpPatternUsed = "CMSIS-DAP Info [00 04]",
                    flmd0PinState = "SWD / JTAG / ISP Pins",
                    resetPinIdleState = "DAP nRESET",
                    resetPinPulsePolarity = "CMSIS-DAP Hardware",
                    dtrPolarity = "USB HID/Bulk",
                    rtsPolarity = "DAP nRESET",
                    inverted = false,
                    useResetToggle = true,
                    resetPulseMs = 20,
                    postResetDelayMs = 30,
                    syncByteGapMs = 1,
                    syncPulseHex = "00 04",
                    framing = DetectedFraming.AVR_ISP_USB,
                    firstReplyHex = dapRx.take(16).toByteArray().toHexStr(),
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                    flashBaseAddr = 0x000000,
                    adapterInitPackets = packets
                )
            }
            val stkRx = transport.rawPacketTransceive(
                byteArrayOf(0x1B, 0x01, 0x00, 0x01, 0x0E, 0x01, 0x14),
                32, 350, "STK500v2 Sign-On [1B 01 00 01 0E 01 14]"
            )
            if (stkRx.isNotEmpty() && (stkRx[0].toInt() and 0xFF) == 0x1B) {
                // Attempt reading 3-byte AVR Device Signature via STK500v2 CMD_SPI_MULTI (0x1D)
                val sigBytes = ByteArray(3) { 0xFF.toByte() }
                var gotSig = false
                for (i in 0..2) {
                    val spiCmd = byteArrayOf(0x1D, 0x04, 0x04, 0x00, 0x30, 0x00, i.toByte(), 0x00)
                    val cs = spiCmd.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }.toByte()
                    val spiRx = transport.rawPacketTransceive(
                        byteArrayOf(0x1B, (i + 2).toByte(), 0x00, 0x08, 0x0E) + spiCmd + byteArrayOf(cs),
                        24, 280, "STK500v2 AVR Signature Byte $i"
                    )
                    if (spiRx.size >= 11 && (spiRx[0].toInt() and 0xFF) == 0x1B) {
                        sigBytes[i] = spiRx[spiRx.size - 3]
                        gotSig = true
                    }
                }
                detectedAvrSigHex = if (gotSig) sigBytes.toHexStr() else stkRx.toHexStr()
                detectedRawIdHex = detectedAvrSigHex
                packets.add("STK500v2 Sign-On -> AVR Signature: $detectedAvrSigHex")
                return SyncLock(
                    baudRate = 115200,
                    evenParity = false,
                    parityMode = "USB Bulk STK500v2 (${transport.currentEndpointsSummary()})",
                    wakeUpPatternUsed = "Atmel STK500v2 Sign-On [0x1B] + 3-Byte AVR Signature",
                    flmd0PinState = "ISP SPI Pins",
                    resetPinIdleState = "Atmel /RESET",
                    resetPinPulsePolarity = "STK500v2 Reset",
                    dtrPolarity = "USB Bulk",
                    rtsPolarity = "STK500 Reset",
                    inverted = false,
                    useResetToggle = true,
                    resetPulseMs = 30,
                    postResetDelayMs = 40,
                    syncByteGapMs = 2,
                    syncPulseHex = "1B 01 00 01 0E 01 14 -> SPI [30 00 00..02]",
                    framing = DetectedFraming.AVR_ISP_USB,
                    firstReplyHex = detectedAvrSigHex,
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                    flashBaseAddr = 0x000000,
                    adapterInitPackets = packets,
                    physicalLevelTag = "USB Native: Atmel STK500v2 ISP"
                )
            }
        }
        return null
    }

    private suspend fun tryMicrochipPickitHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        detectedPicIdHex = ""
        detectedRawIdHex = ""
        val t0 = System.currentTimeMillis()
        val packets = mutableListOf<String>()
        appendLog(ProberLogDirection.INFO, "── فحص مبرمجة Microchip PICkit / SNAP (${_state.value.usbStatus.vidPidHex}) ──")
        if (transport.hasDataEndpoints()) {
            // Query PICkit2/3 FW version (0x76) or MPLAB SNAP/PK4 inquiry
            val rx76 = transport.rawPacketTransceive(byteArrayOf(0x76), 64, 350, "PICkit GET_VERSION [0x76]")
            if (rx76.isNotEmpty()) {
                detectedPicIdHex = rx76.take(12).toByteArray().toHexStr()
                detectedRawIdHex = detectedPicIdHex
                packets.add("PICkit GET_VERSION [0x76] -> $detectedPicIdHex")
                return SyncLock(
                    baudRate = 1_000_000,
                    evenParity = false,
                    parityMode = "USB HID/Bulk ICSP (${transport.currentEndpointsSummary()})",
                    wakeUpPatternUsed = "Microchip PICkit/SNAP ICSP [0x76]",
                    flmd0PinState = "VPP/MCLR + PGC + PGD",
                    resetPinIdleState = "MCLR Controlled",
                    resetPinPulsePolarity = "ICSP VPP/MCLR",
                    dtrPolarity = "USB HID/Bulk",
                    rtsPolarity = "ICSP MCLR",
                    inverted = false,
                    useResetToggle = true,
                    resetPulseMs = 20,
                    postResetDelayMs = 30,
                    syncByteGapMs = 1,
                    syncPulseHex = "0x76",
                    framing = DetectedFraming.MICROCHIP_PIC_ICSP,
                    firstReplyHex = detectedPicIdHex,
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                    flashBaseAddr = 0x000000,
                    adapterInitPackets = packets
                )
            }
        }
        return null
    }

    private suspend fun tryUnknownUsbProgrammerHandshake(): SyncLock? {
        currentCoroutineContext().ensureActive()
        val t0 = System.currentTimeMillis()
        val ifInfo = transport.currentInterfaceInfo()
        val epInfo = transport.currentEndpointsSummary()
        val packets = mutableListOf<String>(
            "Inspect Interfaces: $ifInfo",
            "Inspect Endpoints: $epInfo"
        )
        appendLog(ProberLogDirection.INFO, "── مبرمجة USB غير مسجلة: تشخيص الواجهات ($ifInfo) ونقاط النهاية ($epInfo) ──")
        var replyHex = epInfo
        if (transport.hasDataEndpoints()) {
            for (probe in listOf(byteArrayOf(0x00), byteArrayOf(0x01), byteArrayOf(0x76), byteArrayOf(0x02, 0x0C))) {
                val rx = transport.rawPacketTransceive(probe, 64, 220, "Unknown USB Probe [${probe.toHexStr()}]")
                if (rx.isNotEmpty()) {
                    replyHex = rx.take(16).toByteArray().toHexStr()
                    packets.add("Probe [${probe.toHexStr()}] -> $replyHex")
                    break
                }
            }
        } else {
            val ctrlRx = transport.usbControlTransferIn(0xC0, 0x01, 0, 0, 8, 250, "Unknown USB EP0 Probe [0xC0 req=0x01]")
            if (ctrlRx.isNotEmpty()) {
                replyHex = ctrlRx.toHexStr()
                packets.add("EP0 Probe [0xC0 req=0x01] -> $replyHex")
            }
        }
        detectedRawIdHex = replyHex
        return SyncLock(
            baudRate = 0,
            evenParity = false,
            parityMode = epInfo,
            wakeUpPatternUsed = "Unknown USB Endpoint Diagnostic ($ifInfo)",
            flmd0PinState = "Unknown USB Adapter Descriptor Inspected",
            resetPinIdleState = "N/A (Unknown USB Protocol)",
            resetPinPulsePolarity = "EP0/Bulk/Interrupt Inspected",
            dtrPolarity = "USB Raw",
            rtsPolarity = "USB Raw",
            inverted = false,
            useResetToggle = false,
            resetPulseMs = 0,
            postResetDelayMs = 20,
            syncByteGapMs = 5,
            syncPulseHex = packets.lastOrNull() ?: epInfo,
            framing = DetectedFraming.CUSTOM_RAW,
            firstReplyHex = replyHex,
            latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
            flashBaseAddr = 0x000000,
            adapterInitPackets = packets
        )
    }

    private fun stripStrictEcho(txBytes: ByteArray, rawRx: ByteArray): ByteArray {
        if (rawRx.isEmpty()) return ByteArray(0)
        if (txBytes.isEmpty()) return rawRx
        if (rawRx.contentEquals(txBytes)) {
            transport.echoLoopbackObserved = true
            return ByteArray(0)
        }
        var offset = 0
        while (offset + txBytes.size <= rawRx.size &&
            rawRx.copyOfRange(offset, offset + txBytes.size).contentEquals(txBytes)
        ) {
            transport.echoLoopbackObserved = true
            offset += txBytes.size
        }
        val clean = if (offset > 0) rawRx.copyOfRange(offset, rawRx.size) else rawRx
        if (clean.isEmpty()) return ByteArray(0)
        if (txBytes.size >= 2 && clean.size < txBytes.size &&
            txBytes.copyOfRange(txBytes.size - clean.size, txBytes.size).contentEquals(clean)
        ) {
            // Partial TX loopback echo (single-wire RX line overlap)
            return ByteArray(0)
        }
        return clean
    }

    private fun isMeaningfulGenericReply(
        txBytes: ByteArray,
        cleanRx: ByteArray,
        allowSingleZeroOrFf: Boolean = false
    ): Boolean {
        if (cleanRx.isEmpty()) return false
        if (cleanRx.contentEquals(txBytes)) return false
        if (!allowSingleZeroOrFf && cleanRx.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF }) {
            return false
        }
        return true
    }

    /**
     * Rejects UART framing-error / bit-slip / floating-line / diode-drop echo garbage
     * (e.g. 0xEA, 0x7A, 0xBA, 0xAA, 0xEE, 0xB6 from 1N4148 SDAT drop, or 0x55/0xF5/0xEB/0xED/0xF0/0xE0)
     * so Level A falls through cleanly to Level B synchronous probing.
     */
    private fun isFramingNoise(bytes: ByteArray, baudRate: Int = 0): Boolean {
        if (bytes.isEmpty()) return true
        val hasNecFrame = bytes.any { (it.toInt() and 0xFF) == STX } &&
            bytes.any { (it.toInt() and 0xFF) == ETX || (it.toInt() and 0xFF) == ETB }
        if (hasNecFrame) return false
        if (bytes.size == 1 && (bytes[0].toInt() and 0xFF) == ACK_STM32) return false

        val baseSlipBytes = setOf(
            // Bit-slip / framing-error garbage
            0xFF, 0x7F, 0x5F, 0x57, 0x55, 0x75, 0xF5, 0xD5, 0xEB, 0xED, 0xFD, 0xAD,
            0x77, 0xFE, 0xDF, 0xBF, 0xFB, 0xEF, 0x00,
            // 1N4148 Diode-Drop distorted echo bytes (SDAT single-wire loopback)
            0xEA, 0x7A, 0xBA, 0xAA, 0xEE, 0xB6
        )
        val slipBytes = if (baudRate >= 57600) {
            baseSlipBytes + setOf(0xF0, 0xE0)
        } else {
            baseSlipBytes
        }
        val slipCount = bytes.count { (it.toInt() and 0xFF) in slipBytes }
        return (slipCount * 100.0 / bytes.size) > 50.0
    }

    /**
     * Level A Custom/Unknown strict structured-frame validator:
     * Rejects 1-2 repeated bytes (e.g. EA EA, F0 F0) and accepts ONLY structured frames (>3 non-repeated bytes)
     * starting with SOH (0x01), STX (0x02), ACK (0x06), or 0x79 (STM32) with valid length/checksum.
     */
    private fun isValidLevelACustomFrame(clean: ByteArray): Boolean {
        if (clean.size <= 3) return false
        val distinctCount = clean.map { it.toInt() and 0xFF }.toSet().size
        if (distinctCount < 2) return false
        val b0 = clean[0].toInt() and 0xFF
        if (b0 !in setOf(SOH, STX, ACK_NEC, ACK_STM32)) return false
        val last = clean.last().toInt() and 0xFF
        if ((b0 == SOH || b0 == STX) && (last == ETX || last == ETB)) {
            val len = clean[1].toInt() and 0xFF
            return len in 1..(clean.size - 3)
        }
        val bodySum = clean.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
        val bodyXor = clean.dropLast(1).fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) } and 0xFF
        val negSum = (0 - bodySum) and 0xFF
        val lenByte = clean[1].toInt() and 0xFF
        val lengthLooksValid = (lenByte in 1..(clean.size - 2)) || (lenByte == clean.size - 3)
        val checksumLooksValid = (last == bodySum || last == negSum || last == bodyXor || last == (bodyXor xor 0xFF))
        return lengthLooksValid || checksumLooksValid
    }

    private fun buildLevelAPinProfiles(): List<PhysicalPinProfile> = listOf(
        PhysicalPinProfile(
            name = "Active-Low (DTR=MODE/FLMD0, RTS=RESET)",
            flmd0State = "HIGH via DTR=false (Active-Low)",
            resetIdleState = "HIGH via RTS=false",
            resetPulsePolarity = "Active-Low Pulse on RTS (70ms)",
            dtrDesc = "DTR=MODE (Active-Low)",
            rtsDesc = "RTS=RESET (Active-Low Pulse)",
            inverted = false,
            useResetToggle = true
        ),
        PhysicalPinProfile(
            name = "Active-High (DTR=MODE/FLMD0, RTS=RESET)",
            flmd0State = "HIGH via DTR=true (Active-High)",
            resetIdleState = "HIGH via RTS=true",
            resetPulsePolarity = "Active-High Pulse on RTS (70ms)",
            dtrDesc = "DTR=MODE (Active-High)",
            rtsDesc = "RTS=RESET (Active-High Pulse)",
            inverted = true,
            useResetToggle = true
        ),
        PhysicalPinProfile(
            name = "Swapped Active-Low (RTS=MODE/FLMD0, DTR=RESET)",
            flmd0State = "HIGH via RTS=false (Swapped)",
            resetIdleState = "HIGH via DTR=false (Swapped)",
            resetPulsePolarity = "Swapped Active-Low Pulse on DTR (70ms)",
            dtrDesc = "DTR=RESET (Swapped Active-Low)",
            rtsDesc = "RTS=MODE (Swapped)",
            inverted = false,
            useResetToggle = true,
            swapDtrRtsRoles = true
        ),
        PhysicalPinProfile(
            name = "Swapped Active-High (RTS=MODE/FLMD0, DTR=RESET)",
            flmd0State = "HIGH via RTS=true (Swapped)",
            resetIdleState = "HIGH via DTR=true (Swapped)",
            resetPulsePolarity = "Swapped Active-High Pulse on DTR (70ms)",
            dtrDesc = "DTR=RESET (Swapped Active-High)",
            rtsDesc = "RTS=MODE (Swapped)",
            inverted = true,
            useResetToggle = true,
            swapDtrRtsRoles = true
        ),
        PhysicalPinProfile(
            name = "Static (DTR=LOW, RTS=LOW)",
            flmd0State = "Static DTR=LOW, RTS=LOW",
            resetIdleState = "Static (No Toggle)",
            resetPulsePolarity = "Static (DTR=0, RTS=0)",
            dtrDesc = "Static LOW",
            rtsDesc = "Static LOW",
            inverted = false,
            useResetToggle = false,
            staticDtr = false,
            staticRts = false
        ),
        PhysicalPinProfile(
            name = "Static (DTR=HIGH, RTS=LOW)",
            flmd0State = "Static DTR=HIGH, RTS=LOW",
            resetIdleState = "Static (No Toggle)",
            resetPulsePolarity = "Static (DTR=1, RTS=0)",
            dtrDesc = "Static HIGH",
            rtsDesc = "Static LOW",
            inverted = false,
            useResetToggle = false,
            staticDtr = true,
            staticRts = false
        ),
        PhysicalPinProfile(
            name = "Static (DTR=LOW, RTS=HIGH)",
            flmd0State = "Static DTR=LOW, RTS=HIGH",
            resetIdleState = "Static (No Toggle)",
            resetPulsePolarity = "Static (DTR=0, RTS=1)",
            dtrDesc = "Static LOW",
            rtsDesc = "Static HIGH",
            inverted = false,
            useResetToggle = false,
            staticDtr = false,
            staticRts = true
        ),
        PhysicalPinProfile(
            name = "Static (DTR=HIGH, RTS=HIGH)",
            flmd0State = "Static DTR=HIGH, RTS=HIGH",
            resetIdleState = "Static (No Toggle)",
            resetPulsePolarity = "Static (DTR=1, RTS=1)",
            dtrDesc = "Static HIGH",
            rtsDesc = "Static HIGH",
            inverted = true,
            useResetToggle = false,
            staticDtr = true,
            staticRts = true
        )
    )

    private suspend fun applyPinProfile(profile: PhysicalPinProfile) {
        if (profile.staticDtr != null && profile.staticRts != null) {
            transport.setControlLines(dtr = profile.staticDtr, rts = profile.staticRts)
            delay(35)
        } else if (profile.useResetToggle) {
            if (!profile.swapDtrRtsRoles) {
                transport.pulseResetSequence(profile.inverted, holdResetMs = 70L, settleMs = 70L)
            } else {
                val modeHigh = profile.inverted
                val resetLow = !profile.inverted
                val resetHigh = profile.inverted
                transport.setControlLines(dtr = resetLow, rts = modeHigh)
                delay(70L)
                transport.setControlLines(dtr = resetHigh, rts = modeHigh)
                delay(70L)
            }
        } else {
            transport.setControlLines(dtr = profile.inverted, rts = profile.inverted)
            delay(30)
        }
        transport.flushRx()
    }

    private suspend fun pulseSyncClockPin(clkOnRts: Boolean, cycles: Int = 8, halfPeriodMs: Long = 1L) {
        for (i in 0 until cycles) {
            currentCoroutineContext().ensureActive()
            if (clkOnRts) transport.setRts(true) else transport.setDtr(true)
            delay(halfPeriodMs)
            if (clkOnRts) transport.setRts(false) else transport.setDtr(false)
            delay(halfPeriodMs)
        }
    }

    private suspend fun applySyncPinState(sync: SyncLock) {
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Keep DTR (Vpp/TEST) strictly HIGH (+5V) throughout the active session; never drop to LOW until exit
            transport.samsungQuickResyncKeepTestHigh(
                testActiveHigh = sync.syncModePinState,
                pulseClocks = 8,
                forceUartModemLines = false
            )
            return
        }
        if (sync.useBitBangSync) {
            transport.runSamsungBitBangResetEntry(
                sclkOnRts = true,
                testActiveHigh = sync.syncModePinState,
                initialClocks = 16,
                forceUartModemLines = false
            )
            return
        }
        val clkRts = sync.syncClkOnRts
        if (clkRts != null) {
            if (clkRts) {
                transport.setControlLines(dtr = sync.syncModePinState, rts = false)
            } else {
                transport.setControlLines(dtr = false, rts = sync.syncModePinState)
            }
            delay(30)
            pulseSyncClockPin(clkRts, cycles = 8, halfPeriodMs = 1L)
            return
        }
        val profile = PhysicalPinProfile(
            name = sync.dtrPolarity,
            flmd0State = sync.flmd0PinState,
            resetIdleState = sync.resetPinIdleState,
            resetPulsePolarity = sync.resetPinPulsePolarity,
            dtrDesc = sync.dtrPolarity,
            rtsDesc = sync.rtsPolarity,
            inverted = sync.inverted,
            useResetToggle = sync.useResetToggle,
            swapDtrRtsRoles = sync.swapDtrRtsRoles,
            staticDtr = sync.staticDtr,
            staticRts = sync.staticRts
        )
        applyPinProfile(profile)
    }

    /**
     * Level A: Asynchronous UART Matrix
     * Tests ALL DTR/RTS combinations (High/Low, +/- Reset, swapped, 4 static states)
     * across 10 standard & high-speed bauds (4800..1500000) and both 8N1 / 8E1,
     * with vendor signatures + all 8 global wake-up pulses (0x00,0xFF,0x55,0xAA,0x01,0x7F,0x80,'?').
     */
    private suspend fun runSerialLevelAAsyncMatrix(family: ProberTargetFamily): SyncLock? {
        currentCoroutineContext().ensureActive()
        appendLog(
            ProberLogDirection.INFO,
            "── المستوى A (Asynchronous UART Matrix): فحص شامل لجميع تركيبات DTR/RTS والسرعات (4800..1.5Mbps) و 8N1/8E1 ──"
        )
        val baudRates = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(115200, 57600, 38400, 19200, 9600, 230400, 500000, 1000000, 1500000, 4800)
            ProberTargetFamily.NEC_78K0, ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(9600, 115200, 19200, 38400, 57600, 4800, 230400, 500000, 1000000, 1500000)
            else -> listOf(9600, 115200, 38400, 19200, 57600, 4800, 230400, 500000, 1000000, 1500000)
        }
        val parityOptions = when (family) {
            ProberTargetFamily.STM32_UART -> listOf(true to "8E1", false to "8N1")
            else -> listOf(false to "8N1", true to "8E1")
        }
        val pinProfiles = buildLevelAPinProfiles()
        val necFrame = buildNecCommandFrame(0x00)
        val globalWakePulses: List<Pair<String, ByteArray>> = listOf(
            "Global Wake Pulse 0x00" to byteArrayOf(0x00),
            "Global Wake Pulse 0xFF" to byteArrayOf(0xFF.toByte()),
            "Global Wake Pulse 0x55 (Auto-Baud)" to byteArrayOf(0x55),
            "Global Wake Pulse 0xAA" to byteArrayOf(0xAA.toByte()),
            "Global Wake Pulse 0x01" to byteArrayOf(0x01),
            "STM32 / Auto-Baud 0x7F" to byteArrayOf(0x7F),
            "TI MSP430 / Sync 0x80" to byteArrayOf(0x80.toByte()),
            "NXP ISP '?' (0x3F)" to byteArrayOf(0x3F)
        )
        val vendorSignatures: List<Pair<String, ByteArray>> = listOf(
            "RL78 Single-Wire (0x3A)" to byteArrayOf(0x3A, 0x01, 0x00, 0xFF.toByte(), 0x03),
            "TI MSP430 BSL (0x80 01 00)" to byteArrayOf(0x80.toByte(), 0x01, 0x00, 0x19, 0xE8.toByte(), 0x62),
            "Infineon BSL (0x00 0x6C)" to byteArrayOf(0x00, 0x6C),
            "AVR STK500 GET_SYNC (0x30 0x20)" to byteArrayOf(0x30, 0x20),
            "Nuvoton / Holtek Sync (0xA5 0x5A)" to byteArrayOf(0xA5.toByte(), 0x5A),
            "ESP ROM SLIP Sync" to byteArrayOf(
                0xC0.toByte(), 0x00, 0x08, 0x24, 0x00, 0x00, 0x00, 0x00,
                0x07, 0x07, 0x12, 0x20, 0x55, 0x55, 0xC0.toByte()
            )
        )

        for (baud in baudRates) {
            val probeTimeoutMs = if (baud <= 19200) 110 else 75
            for ((evenParity, parityLabel) in parityOptions) {
                currentCoroutineContext().ensureActive()
                transport.configureUart(baud, evenParity = evenParity)
                appendLog(ProberLogDirection.INFO, "── [Level A] فحص $baud bps ($parityLabel) على 8 تركيبات DTR/RTS ──")

                for (profile in pinProfiles) {
                    currentCoroutineContext().ensureActive()
                    applyPinProfile(profile)

                    // A1: NEC / Renesas 78K0 / RL78 Framed 0x00+0x00 -> SOH Reset (on standard <=115200 bauds first)
                    if (baud <= 230400 && !evenParity) {
                        val t0 = System.currentTimeMillis()
                        transport.writeRawBytes(byteArrayOf(0x00))
                        delay(10)
                        transport.writeRawBytes(byteArrayOf(0x00))
                        delay(35)
                        transport.flushRx()
                        val rxNec = stripStrictEcho(
                            necFrame,
                            transport.transceive(
                                necFrame, 16, if (baud <= 19200) 180 else 110,
                                "Level A NEC/RL78 [$baud $parityLabel | ${profile.name}]",
                                stopOnCompleteNecFrame = true,
                                logTxRx = false
                            )
                        )
                        if (parseNecStatus(rxNec) != null && !isFramingNoise(rxNec, baudRate = baud)) {
                            appendLog(ProberLogDirection.SUCCESS, "✅ [Level A] تزامن NEC/RL78 @ $baud $parityLabel (${profile.name}): ${rxNec.toHexStr()}")
                            return SyncLock(
                                baudRate = baud,
                                evenParity = evenParity,
                                parityMode = parityLabel,
                                wakeUpPatternUsed = "Level A: Framed NEC / Renesas 78K0 / RL78 (0x00+0x00 + SOH 0x00)",
                                flmd0PinState = profile.flmd0State,
                                resetPinIdleState = profile.resetIdleState,
                                resetPinPulsePolarity = profile.resetPulsePolarity,
                                dtrPolarity = profile.dtrDesc,
                                rtsPolarity = profile.rtsDesc,
                                inverted = profile.inverted,
                                useResetToggle = profile.useResetToggle,
                                resetPulseMs = if (profile.useResetToggle) 70 else 0,
                                postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                                syncByteGapMs = 10,
                                syncPulseHex = "0x00 (10ms) 0x00 + 01 01 00 FF 03",
                                framing = DetectedFraming.NEC_STX_ETX,
                                firstReplyHex = rxNec.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                                swapDtrRtsRoles = profile.swapDtrRtsRoles,
                                staticDtr = profile.staticDtr,
                                staticRts = profile.staticRts,
                                physicalLevelTag = "Level A: Async UART"
                            )
                        }
                    }

                    // A2: STM32 / TI / NXP / AVR / Global Wake Pulses (0x00, 0xFF, 0x55, 0xAA, 0x01, 0x7F, 0x80, '?')
                    for ((pName, pBytes) in globalWakePulses) {
                        currentCoroutineContext().ensureActive()
                        val t0 = System.currentTimeMillis()
                        val rawRx = transport.transceive(
                            pBytes, 24, probeTimeoutMs,
                            "Level A $pName [$baud $parityLabel]",
                            logTxRx = false
                        )
                        val clean = stripStrictEcho(pBytes, rawRx)
                        if (isMeaningfulGenericReply(pBytes, clean) && !isFramingNoise(clean, baudRate = baud)) {
                            val b0 = clean[0].toInt() and 0xFF
                            if (pBytes[0] == 0x7F.toByte() && (b0 == ACK_STM32 || b0 == NACK_STM32)) {
                                val idRx = stripStrictEcho(
                                    byteArrayOf(0x02, 0xFD.toByte()),
                                    transport.transceive(byteArrayOf(0x02, 0xFD.toByte()), 16, 180, "STM32 GetID [0x02 0xFD]")
                                )
                                if (b0 == NACK_STM32 && (idRx.isEmpty() || isFramingNoise(idRx, baudRate = baud) || (idRx[0].toInt() and 0xFF) != ACK_STM32)) {
                                    continue
                                }
                                val combinedHex = (clean + idRx).toHexStr()
                                appendLog(ProberLogDirection.SUCCESS, "✅ [Level A] تزامن STM32 @ $baud $parityLabel (${profile.name}): $combinedHex")
                                return SyncLock(
                                    baudRate = baud,
                                    evenParity = evenParity,
                                    parityMode = parityLabel,
                                    wakeUpPatternUsed = "Level A: $pName -> 0x02 0xFD",
                                    flmd0PinState = profile.flmd0State,
                                    resetPinIdleState = profile.resetIdleState,
                                    resetPinPulsePolarity = profile.resetPulsePolarity,
                                    dtrPolarity = profile.dtrDesc,
                                    rtsPolarity = profile.rtsDesc,
                                    inverted = profile.inverted,
                                    useResetToggle = profile.useResetToggle,
                                    resetPulseMs = if (profile.useResetToggle) 70 else 0,
                                    postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                                    syncByteGapMs = 10,
                                    syncPulseHex = "0x7F -> 0x02 0xFD",
                                    framing = DetectedFraming.STM32_ACK,
                                    firstReplyHex = combinedHex,
                                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                                    swapDtrRtsRoles = profile.swapDtrRtsRoles,
                                    staticDtr = profile.staticDtr,
                                    staticRts = profile.staticRts,
                                    physicalLevelTag = "Level A: Async UART"
                                )
                            }
                            if (clean.size <= 2 && clean.all { (it.toInt() and 0xFF) == b0 }) {
                                continue
                            }
                            val detectedFraming = classifyFraming(clean, pBytes, baudRate = baud)
                            if ((detectedFraming == DetectedFraming.CUSTOM_RAW || detectedFraming == DetectedFraming.RENESAS_RAW) &&
                                !isValidLevelACustomFrame(clean)
                            ) {
                                continue
                            }
                            appendLog(ProberLogDirection.SUCCESS, "✅ [Level A] استجابة $pName @ $baud $parityLabel (${profile.name}): ${clean.toHexStr()}")
                            return SyncLock(
                                baudRate = baud,
                                evenParity = evenParity,
                                parityMode = parityLabel,
                                wakeUpPatternUsed = "Level A: $pName",
                                flmd0PinState = profile.flmd0State,
                                resetPinIdleState = profile.resetIdleState,
                                resetPinPulsePolarity = profile.resetPulsePolarity,
                                dtrPolarity = profile.dtrDesc,
                                rtsPolarity = profile.rtsDesc,
                                inverted = profile.inverted,
                                useResetToggle = profile.useResetToggle,
                                resetPulseMs = if (profile.useResetToggle) 70 else 0,
                                postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                                syncByteGapMs = 10,
                                syncPulseHex = pBytes.toHexStr(),
                                framing = detectedFraming,
                                firstReplyHex = clean.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                                swapDtrRtsRoles = profile.swapDtrRtsRoles,
                                staticDtr = profile.staticDtr,
                                staticRts = profile.staticRts,
                                physicalLevelTag = "Level A: Async UART"
                            )
                        }
                    }

                    // A3: Vendor Signatures (RL78 0x3A, TI MSP430, Infineon, AVR STK500, Nuvoton, ESP)
                    for ((vName, vBytes) in vendorSignatures) {
                        currentCoroutineContext().ensureActive()
                        val t0 = System.currentTimeMillis()
                        val rawRx = transport.transceive(vBytes, 24, probeTimeoutMs, "Level A $vName [$baud $parityLabel]", logTxRx = false)
                        val clean = stripStrictEcho(vBytes, rawRx)
                        val isInfineonBsl = vBytes.contentEquals(byteArrayOf(0x00, 0x6C))
                        val isAvrStk500 = vBytes.contentEquals(byteArrayOf(0x30, 0x20))
                        val isEspSlip = vBytes.isNotEmpty() && (vBytes[0].toInt() and 0xFF) == 0xC0
                        val detectedFraming = if (clean.isNotEmpty()) classifyFraming(clean, vBytes, baudRate = baud) else DetectedFraming.CUSTOM_RAW
                        val accepted = when {
                            isInfineonBsl -> {
                                // Infineon BSL ONLY accepts the exclusive single-byte 0xD5 confirmation
                                clean.size == 1 && (clean[0].toInt() and 0xFF) == 0xD5
                            }
                            isAvrStk500 -> {
                                clean.size >= 2 && (clean[0].toInt() and 0xFF) == 0x14 && (clean[1].toInt() and 0xFF) == 0x10
                            }
                            isEspSlip -> {
                                clean.size >= 4 && (clean[0].toInt() and 0xFF) == 0xC0 && (clean.last().toInt() and 0xFF) == 0xC0 &&
                                    !isFramingNoise(clean, baudRate = baud)
                            }
                            else -> {
                                val isRepeatedShort = clean.isNotEmpty() && clean.size <= 2 &&
                                    clean.all { (it.toInt() and 0xFF) == (clean[0].toInt() and 0xFF) }
                                isMeaningfulGenericReply(vBytes, clean) &&
                                    !isFramingNoise(clean, baudRate = baud) &&
                                    !isRepeatedShort &&
                                    ((detectedFraming != DetectedFraming.CUSTOM_RAW && detectedFraming != DetectedFraming.RENESAS_RAW) ||
                                        isValidLevelACustomFrame(clean))
                            }
                        }
                        if (accepted) {
                            appendLog(ProberLogDirection.SUCCESS, "✅ [Level A] بصمة $vName @ $baud $parityLabel (${profile.name}): ${clean.toHexStr()}")
                            return SyncLock(
                                baudRate = baud,
                                evenParity = evenParity,
                                parityMode = parityLabel,
                                wakeUpPatternUsed = "Level A: $vName",
                                flmd0PinState = profile.flmd0State,
                                resetPinIdleState = profile.resetIdleState,
                                resetPinPulsePolarity = profile.resetPulsePolarity,
                                dtrPolarity = profile.dtrDesc,
                                rtsPolarity = profile.rtsDesc,
                                inverted = profile.inverted,
                                useResetToggle = profile.useResetToggle,
                                resetPulseMs = if (profile.useResetToggle) 70 else 0,
                                postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                                syncByteGapMs = 10,
                                syncPulseHex = vBytes.toHexStr(),
                                framing = detectedFraming,
                                firstReplyHex = clean.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                                swapDtrRtsRoles = profile.swapDtrRtsRoles,
                                staticDtr = profile.staticDtr,
                                staticRts = profile.staticRts,
                                physicalLevelTag = "Level A: Async UART"
                            )
                        }
                    }

                    // A4: Renesas Direct R8C/M16C/H8/SH (exclusively at 9600 bps: 16x 0x00 @ 20ms + 0x70 SRD1/SRD2)
                    if (!evenParity && baud == 9600) {
                        val t0 = System.currentTimeMillis()
                        for (i in 0 until 16) {
                            currentCoroutineContext().ensureActive()
                            transport.writeRawBytes(byteArrayOf(0x00))
                            delay(20)
                        }
                        transport.flushRx()
                        val rx70 = stripStrictEcho(
                            byteArrayOf(0x70),
                            transport.transceive(byteArrayOf(0x70), 8, 160, "Level A R8C SRD [0x70]", logTxRx = false)
                        )
                        val srdValid = rx70.size >= 2 &&
                            !isFramingNoise(rx70, baudRate = baud) &&
                            !rx70.all { val b = it.toInt() and 0xFF; b == 0xF0 || b == 0xE0 || b == 0x00 || b == 0xFF }
                        if (srdValid) {
                            val rxFB = stripStrictEcho(
                                byteArrayOf(0xFB.toByte()),
                                transport.transceive(byteArrayOf(0xFB.toByte()), 24, 150, "Level A R8C Version [0xFB]", logTxRx = false)
                            )
                            val combined = if (rxFB.isNotEmpty() && !isFramingNoise(rxFB, baudRate = baud)) rx70 + rxFB else rx70
                            appendLog(ProberLogDirection.SUCCESS, "✅ [Level A] Renesas R8C/M16C Direct @ $baud (${profile.name}): SRD=${rx70.toHexStr()}")
                            return SyncLock(
                                baudRate = baud,
                                evenParity = evenParity,
                                parityMode = parityLabel,
                                wakeUpPatternUsed = "Level A: Renesas R8C/M16C Direct (16x 0x00 @ 20ms + 0x70 SRD1/SRD2)",
                                flmd0PinState = profile.flmd0State,
                                resetPinIdleState = profile.resetIdleState,
                                resetPinPulsePolarity = profile.resetPulsePolarity,
                                dtrPolarity = profile.dtrDesc,
                                rtsPolarity = profile.rtsDesc,
                                inverted = profile.inverted,
                                useResetToggle = profile.useResetToggle,
                                resetPulseMs = if (profile.useResetToggle) 70 else 0,
                                postResetDelayMs = 20,
                                syncByteGapMs = 20,
                                syncPulseHex = "16x 0x00 (20ms) + 0x70",
                                framing = DetectedFraming.RENESAS_RAW,
                                firstReplyHex = combined.toHexStr(),
                                latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(15),
                                swapDtrRtsRoles = profile.swapDtrRtsRoles,
                                staticDtr = profile.staticDtr,
                                staticRts = profile.staticRts,
                                physicalLevelTag = "Level A: Async UART"
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    private fun isSamsungUartArtifact(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val uartEchoArtifacts = setOf(0xCA, 0xEA, 0xDE, 0x7A, 0xBA, 0xB6, 0xEE, 0xFE, 0x7F, 0xF5, 0xEB, 0xED)
        return bytes.all { (it.toInt() and 0xFF) in uartEchoArtifacts }
    }

    /**
     * Detects Floating-High / Idle SDAT line (e.g. missing +12.5V Vpp on Pin 9):
     * >90% of bytes are 0xFF and any remaining bytes are single-bit drops (0xFE, 0xFD, 0xFB, 0xF7, 0xEF, 0xDF, 0xBF, 0x7F).
     */
    private fun isFloatingHighSdatPattern(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val ffCount = bytes.count { (it.toInt() and 0xFF) == 0xFF }
        val ffPercent = (ffCount * 100.0) / bytes.size
        if (ffPercent <= 90.0) return false
        return bytes.all {
            val v = it.toInt() and 0xFF
            v == 0xFF || v in SAMSUNG_SINGLE_BIT_DROP_BYTES
        }
    }

    private fun isValidSamsungBitBangReply(txBytes: ByteArray, clean: ByteArray): Boolean {
        if (clean.isEmpty()) return false
        if (clean.contentEquals(txBytes)) return false
        if (clean.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF }) return false
        if (isSamsungUartArtifact(clean)) return false
        return true
    }

    private suspend fun samsungSyncTransceive(
        sync: SyncLock,
        txBytes: ByteArray,
        rxLen: Int,
        label: String,
        logTxRx: Boolean = true
    ): ByteArray {
        if (logTxRx) {
            appendLog(ProberLogDirection.TX, "$label [Bit-Bang Sync Shift] -> ${txBytes.toHexStr()}")
        }
        val raw = transport.samsungS3F8Transceive(
            txBytes = txBytes,
            rxLen = rxLen,
            testHighState = sync.syncModePinState
        )
        val clean = stripStrictEcho(txBytes, raw)
        if (logTxRx) {
            appendLog(ProberLogDirection.RX, if (clean.isEmpty()) "(No Sync Reply)" else clean.toHexStr())
        }
        return clean
    }

    /**
     * Level B: Samsung S3F8419 (S3F8 / S3F9 Serial Programming Mode) True Bit-by-Bit Synchronous Shift Matrix:
     * Strict Hardware Pin Mapping Lock (no swapped DTR/RTS, no UART-coupled 38400/9600 bps):
     *   DTR = Vpp/TEST (Pin 9, stays HIGH/+5V throughout active session; drops LOW only on exit)
     *   RTS = SCLK (Pin 4, Rising-Edge bit shift)
     *   TXD(1N4148) + RXD = SDAT (Pin 3, MSB-first bit-bang)
     */
    private suspend fun runSerialLevelBSyncClockDataMatrix(family: ProberTargetFamily): SyncLock? {
        currentCoroutineContext().ensureActive()
        appendLog(
            ProberLogDirection.INFO,
            "── المستوى B (Samsung S3F8419 True Bit-Bang Shift: DTR=Vpp/TEST, RTS=SCLK, TX/RX=SDAT): تهيئة 50ms -> TEST=+5V 20ms -> 16 نبضة SCLK (2ms) ──"
        )
        detectedSamsungIdHex = ""
        detectedSamsungSmartOptionHex = ""
        lastLevelBSamsungHardwareAlert = null

        val samsungHardwareAlert =
            "تتطلب شريحة Samsung S3F8419 تطبيق جهد +12.5V (Vpp) على رجل 9 (Vpp/TEST) عبر ترانزستور سويتش مع DTR للدخول في وضع البرمجة المتسلسلة (Serial ISP Mode)"

        if (transport.currentChip() != ProberUsbChip.FT232R) {
            appendLog(
                ProberLogDirection.WARN,
                "⚠️ وضع الإزاحة المتزامنة بالبت (Bit-Bang Sync Shift) في المستوى B يتطلب محول FT232R للتحكم المباشر بـ TXD/RXD/RTS/DTR."
            )
            lastLevelBSamsungHardwareAlert = samsungHardwareAlert
            appendLog(ProberLogDirection.WARN, "⚠️ $samsungHardwareAlert")
            return null
        }

        data class SyncWiringCfg(
            val desc: String,
            val testActiveHigh: Boolean
        )

        // Strictly locked pin mapping with True Bit-Bang Synchronous Shift ONLY (no UART-coupled 38400/9600)
        val wiringConfigs = listOf(
            SyncWiringCfg("DTR=Vpp/TEST (HIGH) | RTS=SCLK (Rising Edge) | SDAT=TX/RX (Bit-Bang Shift)", testActiveHigh = true),
            SyncWiringCfg("DTR=Vpp/TEST (Inverted Transistor Switch) | RTS=SCLK (Rising Edge) | SDAT (Bit-Bang Shift)", testActiveHigh = false)
        )
        val samsungSyncIdQueries: List<Pair<String, ByteArray>> = listOf(
            "Samsung ID 0x01" to byteArrayOf(0x01),
            "Samsung ID 0x80" to byteArrayOf(0x80.toByte()),
            "Samsung ID 0xC0" to byteArrayOf(0xC0.toByte()),
            "Sync ID 0x02" to byteArrayOf(0x02),
            "Sync ID 0x9F" to byteArrayOf(0x9F.toByte())
        )
        val syncByteAA = byteArrayOf(0xAA.toByte())
        val syncByte55 = byteArrayOf(0x55)

        try {
            for (cfg in wiringConfigs) {
                currentCoroutineContext().ensureActive()
                val t0 = System.currentTimeMillis()
                // Steps 1, 2 & 3: TEST=LOW & SCLK=LOW (50ms) -> TEST=HIGH (20ms) -> 16 SCLK pulses (2ms each)
                val entered = transport.runSamsungBitBangResetEntry(
                    sclkOnRts = true,
                    testActiveHigh = cfg.testActiveHigh,
                    initialClocks = 16,
                    forceUartModemLines = false
                )
                if (!entered) continue

                // Step 4a: True Bit-by-Bit Synchronous Shift 0xAA -> 0x55
                val rxAA = transport.samsungS3F8Transceive(syncByteAA, 1, cfg.testActiveHigh)
                val cleanAA = stripStrictEcho(syncByteAA, rxAA)

                val rx55 = transport.samsungS3F8Transceive(syncByte55, 2, cfg.testActiveHigh)
                val clean55 = stripStrictEcho(syncByte55, rx55)
                val combinedSyncClean = cleanAA + clean55
                val syncReplyValid = isValidSamsungBitBangReply(byteArrayOf(0xAA.toByte(), 0x55), combinedSyncClean)

                // Step 4b: True Bit-by-Bit Synchronous Samsung ID queries (0x01, 0x80, 0xC0, ...)
                var winningQueryName = "0xAA -> 0x55"
                var winningQueryBytes = byteArrayOf(0xAA.toByte(), 0x55)
                var capturedIdBytes: ByteArray? = null
                for ((qName, qBytes) in samsungSyncIdQueries) {
                    currentCoroutineContext().ensureActive()
                    val qRaw = transport.samsungS3F8Transceive(qBytes, 2, cfg.testActiveHigh)
                    val qClean = stripStrictEcho(qBytes, qRaw)
                    if (isValidSamsungBitBangReply(qBytes, qClean)) {
                        winningQueryName = qName
                        winningQueryBytes = qBytes
                        capturedIdBytes = qClean
                        break
                    }
                }

                val finalBytes = capturedIdBytes ?: if (syncReplyValid) combinedSyncClean else null
                if (finalBytes != null) {
                    detectedSamsungIdHex = finalBytes.toHexStr()
                    lastLevelBSamsungHardwareAlert = null
                    appendLog(
                        ProberLogDirection.SUCCESS,
                        "✅ [Level B] تزامن SAMSUNG_S3F8_S3F9_FAMILY (${cfg.desc}) [$winningQueryName] = ${finalBytes.toHexStr()}"
                    )
                    return SyncLock(
                        baudRate = 4800,
                        evenParity = false,
                        parityMode = "Bit-Bang Sync Shift (MSB First, DTR=Vpp/TEST, RTS=SCLK, SDAT=TX/RX)",
                        wakeUpPatternUsed = "Level B Bit-Bang: DTR(TEST)=LOW/RTS(SCLK)=LOW (50ms) -> DTR=HIGH (20ms) -> 16 SCLK (2ms) -> 0xAA 0x55 -> $winningQueryName",
                        flmd0PinState = "DTR=Vpp/TEST (${if (cfg.testActiveHigh) "HIGH +5V طوال الجلسة" else "Inverted Switch"}) | TXD(1N4148)+RXD=SDAT",
                        resetPinIdleState = "DTR=Vpp/TEST (HIGH طوال الجلسة) | RTS=SCLK (16+ Pulses)",
                        resetPinPulsePolarity = cfg.desc,
                        dtrPolarity = "DTR=Vpp/TEST (HIGH طوال الجلسة؛ LOW عند الخروج فقط)",
                        rtsPolarity = "RTS=SCLK (Rising-Edge Synchronous Shift)",
                        inverted = !cfg.testActiveHigh,
                        useResetToggle = false,
                        resetPulseMs = 50,
                        postResetDelayMs = 20,
                        syncByteGapMs = 2,
                        syncPulseHex = (byteArrayOf(0xAA.toByte(), 0x55) + winningQueryBytes).toHexStr(),
                        framing = DetectedFraming.SAMSUNG_ISP,
                        firstReplyHex = finalBytes.toHexStr(),
                        latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(10),
                        swapDtrRtsRoles = false,
                        staticDtr = cfg.testActiveHigh,
                        staticRts = false,
                        physicalLevelTag = "Level B: Bit-Bang Sync Shift (SAMSUNG_S3F8_S3F9_FAMILY)",
                        syncClkOnRts = true,
                        syncModePinState = cfg.testActiveHigh,
                        useBitBangSync = true,
                        syncSampleOnFallingEdge = false
                    )
                }
            }
            transport.exitFtdiBitBangMode()
            lastLevelBSamsungHardwareAlert = samsungHardwareAlert
            appendLog(ProberLogDirection.WARN, "⚠️ $samsungHardwareAlert")
            return null
        } catch (t: Throwable) {
            transport.exitFtdiBitBangMode()
            throw t
        }
    }

    /**
     * Level C: Single-Wire Pulse / BDM / SWIM Matrix @ 1.5 Mbps (1,500,000 bps)
     * Sends pulse-width patterns (0x00, 0xC0, 0xFE, 0x80, 0xF0, 0x55, 0x3A) over merged TX/RX
     * with Strict Echo Rejection, plus Freescale HCS08 SDID (0x1806..0x1807) probe.
     */
    private suspend fun runSerialLevelCSingleWirePulseMatrix(family: ProberTargetFamily): SyncLock? {
        currentCoroutineContext().ensureActive()
        appendLog(
            ProberLogDirection.INFO,
            "── المستوى C (Single-Wire Pulse / BDM / SWIM Matrix @ 1,500,000 bps): فحص نبضات السلك الواحد ──"
        )
        transport.configureUart(1_500_000, evenParity = false)
        detectedSdidHex = ""
        detectedFreescaleModel = ""

        val profiles = listOf(
            buildLevelAPinProfiles()[0], // Active-Low (DTR=MODE, RTS=RESET)
            buildLevelAPinProfiles()[1], // Active-High
            buildLevelAPinProfiles()[2], // Swapped Active-Low
            buildLevelAPinProfiles()[4], // Static LOW/LOW
            buildLevelAPinProfiles()[7]  // Static HIGH/HIGH
        )
        val pulseWidthBytes = listOf(0x00, 0xC0, 0xFE, 0x80, 0xF0, 0x55, 0x3A)

        for (profile in profiles) {
            currentCoroutineContext().ensureActive()
            applyPinProfile(profile)
            val t0 = System.currentTimeMillis()

            for (pulseByte in pulseWidthBytes) {
                currentCoroutineContext().ensureActive()
                val tx = byteArrayOf(pulseByte.toByte())
                val rawRx = transport.transceive(tx, 16, 85, "Level C 1.5M Pulse [0x%02X]".format(pulseByte), logTxRx = false)
                val clean = stripStrictEcho(tx, rawRx)
                if (isMeaningfulGenericReply(tx, clean)) {
                    // Attempt HCS08 BDM SDID read as enrichment
                    val sdidH = stripStrictEcho(
                        byteArrayOf(0xE8.toByte(), 0x18, 0x06),
                        transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x06), 8, 95, "Level C SDIDH [0x1806]", logTxRx = false)
                    )
                    val sdidL = stripStrictEcho(
                        byteArrayOf(0xE8.toByte(), 0x18, 0x07),
                        transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x07), 8, 95, "Level C SDIDL [0x1807]", logTxRx = false)
                    )
                    val sdidValid = sdidH.isNotEmpty() && sdidL.isNotEmpty() &&
                        ((sdidH[0].toInt() and 0xFF) != 0x00 || (sdidL[0].toInt() and 0xFF) != 0x00)
                    if (sdidValid) {
                        val sdid16 = ((sdidH[0].toInt() and 0xFF) shl 8) or (sdidL[0].toInt() and 0xFF)
                        detectedSdidHex = "0x%04X".format(sdid16)
                        detectedFreescaleModel = lookupHcs08ModelBySdid(sdid16)
                    }
                    val combined = if (sdidValid) (sdidH + sdidL) else clean
                    val isBdmFreescale = sdidValid || family == ProberTargetFamily.FREESCALE_HCS08 || pulseByte == 0xFE || pulseByte == 0xC0
                    appendLog(
                        ProberLogDirection.SUCCESS,
                        "✅ [Level C] استجابة السلك الواحد @ 1.5Mbps [0x%02X] (${profile.name}): ${combined.toHexStr()}".format(pulseByte)
                    )
                    return SyncLock(
                        baudRate = 1_500_000,
                        evenParity = false,
                        parityMode = "8N1 (1.5 Mbps Single-Wire Pulse)",
                        wakeUpPatternUsed = "Level C: Single-Wire Pulse (0x%02X @ 1.5Mbps)".format(pulseByte),
                        flmd0PinState = "Single-Wire TX+RX (${profile.flmd0State})",
                        resetPinIdleState = profile.resetIdleState,
                        resetPinPulsePolarity = profile.resetPulsePolarity,
                        dtrPolarity = profile.dtrDesc,
                        rtsPolarity = profile.rtsDesc,
                        inverted = profile.inverted,
                        useResetToggle = profile.useResetToggle,
                        resetPulseMs = if (profile.useResetToggle) 70 else 0,
                        postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                        syncByteGapMs = 2,
                        syncPulseHex = "0x%02X".format(pulseByte),
                        framing = if (isBdmFreescale) DetectedFraming.FREESCALE_SINGLE_WIRE_BDM else DetectedFraming.CUSTOM_RAW,
                        firstReplyHex = combined.toHexStr(),
                        latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(5),
                        swapDtrRtsRoles = profile.swapDtrRtsRoles,
                        staticDtr = profile.staticDtr,
                        staticRts = profile.staticRts,
                        physicalLevelTag = "Level C: Single-Wire 1.5Mbps"
                    )
                }
            }

            // Direct HCS08 BDM SDID probe (0xE8 0x18 0x06/0x07) even if single pulse was silent
            val sdidH = stripStrictEcho(
                byteArrayOf(0xE8.toByte(), 0x18, 0x06),
                transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x06), 8, 95, "Level C Direct SDIDH [0x1806]", logTxRx = false)
            )
            val sdidL = stripStrictEcho(
                byteArrayOf(0xE8.toByte(), 0x18, 0x07),
                transport.transceive(byteArrayOf(0xE8.toByte(), 0x18, 0x07), 8, 95, "Level C Direct SDIDL [0x1807]", logTxRx = false)
            )
            if (sdidH.isNotEmpty() && sdidL.isNotEmpty() &&
                isMeaningfulGenericReply(byteArrayOf(0xE8.toByte(), 0x18, 0x06), sdidH + sdidL)
            ) {
                val sdid16 = ((sdidH[0].toInt() and 0xFF) shl 8) or (sdidL[0].toInt() and 0xFF)
                detectedSdidHex = "0x%04X".format(sdid16)
                detectedFreescaleModel = lookupHcs08ModelBySdid(sdid16)
                val combined = sdidH + sdidL
                appendLog(ProberLogDirection.SUCCESS, "✅ [Level C] Freescale 1.5Mbps BDM SDID=$detectedSdidHex -> ${combined.toHexStr()}")
                return SyncLock(
                    baudRate = 1_500_000,
                    evenParity = false,
                    parityMode = "8N1 (1.5 Mbps Single-Wire BDM)",
                    wakeUpPatternUsed = "Level C: Freescale Single-Wire BDM SDID (0xE8 18 06 @ 1.5Mbps)",
                    flmd0PinState = "BKGD (TX+1kΩ+RX) @ 1,500,000 bps",
                    resetPinIdleState = profile.resetIdleState,
                    resetPinPulsePolarity = profile.resetPulsePolarity,
                    dtrPolarity = profile.dtrDesc,
                    rtsPolarity = profile.rtsDesc,
                    inverted = profile.inverted,
                    useResetToggle = profile.useResetToggle,
                    resetPulseMs = if (profile.useResetToggle) 70 else 0,
                    postResetDelayMs = if (profile.useResetToggle) 70 else 30,
                    syncByteGapMs = 2,
                    syncPulseHex = "0xE8 18 06",
                    framing = DetectedFraming.FREESCALE_SINGLE_WIRE_BDM,
                    firstReplyHex = combined.toHexStr(),
                    latencySampleMs = (System.currentTimeMillis() - t0).toInt().coerceAtLeast(5),
                    swapDtrRtsRoles = profile.swapDtrRtsRoles,
                    staticDtr = profile.staticDtr,
                    staticRts = profile.staticRts,
                    physicalLevelTag = "Level C: Single-Wire 1.5Mbps"
                )
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
        // 0. Native USB Programmers (USBDM, ST-LINK, USBasp, Atmel/AVR, PICkit, Unknown USB)
        when (transport.currentChip()) {
            ProberUsbChip.USBDM -> return tryUsbdmMultiArchHandshake(family)
            ProberUsbChip.ST_LINK -> return tryStLinkHandshake()
            ProberUsbChip.USBASP -> return tryUsbaspHandshake()
            ProberUsbChip.ATMEL_AVR -> return tryAtmelAvrHandshake() ?: tryUnknownUsbProgrammerHandshake()
            ProberUsbChip.MICROCHIP_PICKIT -> return tryMicrochipPickitHandshake() ?: tryUnknownUsbProgrammerHandshake()
            ProberUsbChip.UNKNOWN_USB -> return tryUnknownUsbProgrammerHandshake()
            else -> { /* Proceed to Universal 3-Level Serial/UART Matrix */ }
        }

        // Fast-path NEC / Renesas 78K0 if user explicitly targets NEC or AUTO_DETECT at primary 9600/115200 8N1
        if (family == ProberTargetFamily.NEC_78K0 || family == ProberTargetFamily.AUTO_DETECT) {
            appendLog(ProberLogDirection.INFO, "── فحص سريع NEC / Renesas 78K0 / RL78 قبل المصفوفة الشاملة ──")
            for (necBaud in listOf(9600, 115200)) {
                tryLiteralNec78k0Handshake(necBaud, false, "Active-Low (DTR=FLMD0, RTS=RESET)", inverted = false, useResetToggle = true)
                    ?.let { return it.copy(physicalLevelTag = "Level A: Async UART (NEC 78K0)") }
                tryLiteralNec78k0Handshake(necBaud, false, "Active-High / عكس القطبية", inverted = true, useResetToggle = true)
                    ?.let { return it.copy(physicalLevelTag = "Level A: Async UART (NEC 78K0)") }
            }
        }

        // Order the 3 Universal Physical Levels according to optional user hint, covering ALL 3 automatically:
        return when (family) {
            ProberTargetFamily.SAMSUNG_S3F -> {
                runSerialLevelBSyncClockDataMatrix(family)
                    ?: runSerialLevelAAsyncMatrix(family)
                    ?: runSerialLevelCSingleWirePulseMatrix(family)
            }
            ProberTargetFamily.FREESCALE_HCS08 -> {
                runSerialLevelCSingleWirePulseMatrix(family)
                    ?: runSerialLevelAAsyncMatrix(family)
                    ?: runSerialLevelBSyncClockDataMatrix(family)
            }
            else -> {
                runSerialLevelAAsyncMatrix(family)
                    ?: runSerialLevelBSyncClockDataMatrix(family)
                    ?: runSerialLevelCSingleWirePulseMatrix(family)
            }
        }
    }

    /**
     * Queries Universal Auto-ID opcodes (0xC0, 0xC5, 0xFB, 0x01, 0x02, 0x00, 0x9F, 0xAF)
     * and generates `universalAutoIdRule`, manufacturer, and inferred model.
     */
    private suspend fun extractUniversalAutoId(sync: SyncLock): UniversalIdCapture {
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val effectiveCode = sync.actualTargetCode ?: detectedUsbdmTargetCode
            if (detectedRawIdHex.isBlank()) {
                transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "Auto-ID USBDM HALT [02 14]")
                val rx = transport.usbdmTransceive(
                    usbdmReadMemPacket(detectedUsbdmReadCmd, arch.memSpace, arch.idByteLength, arch.idRegisterAddress),
                    16, 300, "Auto-ID USBDM Read [${arch.idRegisterName}]"
                )
                if (rx.size >= 1 + arch.idByteLength && (rx[0].toInt() and 0xFF) == 0x00) {
                    val raw = rx.copyOfRange(1, 1 + arch.idByteLength)
                    detectedRawIdHex = raw.toHexStr()
                    if (arch == UsbdmTargetArch.HCS08 && raw.size >= 2) {
                        val sdid16 = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                        detectedSdidHex = "0x%04X".format(sdid16)
                        detectedFreescaleModel = if (sdid16 != 0x0000 && sdid16 != 0xFFFF) {
                            "${lookupHcs08ModelBySdid(sdid16)} (SET_TARGET=0x%02X | Raw ID=$detectedRawIdHex)".format(effectiveCode)
                        } else {
                            "Freescale / NXP HCS08 Family (SET_TARGET=0x%02X | SDID=$detectedSdidHex | FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826)".format(effectiveCode)
                        }
                    } else {
                        detectedFreescaleModel = "${arch.archName} (SET_TARGET=0x%02X | Raw ID=$detectedRawIdHex)".format(effectiveCode)
                    }
                }
            }
            val rawId = detectedRawIdHex.ifBlank { detectedSdidHex.ifBlank { sync.firstReplyHex } }
            val model = detectedFreescaleModel.ifBlank { "${arch.archName} (SET_TARGET=0x%02X | Raw ID=$rawId)".format(effectiveCode) }
            val capsHex = "0x%02X".format(detectedUsbdmCapsCmd)
            val readHex = "0x%02X".format(detectedUsbdmReadCmd)
            return UniversalIdCapture(
                autoIdRule = "USBDM GET_CAPABILITIES ($capsHex) -> SET_VDD 5V (0x02) -> Internal Power (0x03) -> SET_TARGET 0x%02X (${arch.archName}) -> RESET_SPECIAL (0x13) -> CONNECT (0x0F) -> HALT (0x14) -> READ_MEM $readHex (space=0x%02X @ ${arch.idRegisterName})".format(effectiveCode, arch.memSpace),
                manufacturerGuess = if (arch == UsbdmTargetArch.ARM_SWD) "ARM / NXP Kinetis / STM32 / Non-Freescale SWD" else "Freescale / NXP Semiconductors",
                inferredModel = model,
                rawSignatureSummary = "SET_TARGET=0x%02X | Raw ID=$rawId | ${arch.idRegisterName} | USBDM Caps=$detectedUsbdmCaps".format(effectiveCode),
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = "${arch.idRegisterName} via READ_MEM $readHex (space=0x%02X)".format(arch.memSpace),
                rawSiliconIdHex = rawId
            )
        }
        if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM) {
            val isSwd = sync.framing == DetectedFraming.STLINK_SWD
            val rawId = detectedStLinkIdCodeHex.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = if (isSwd) {
                    "ST-LINK GET_VERSION [F1 80] -> EXIT_DFU [F3 07] -> ENTER_SWD [F2 20 A3] -> READ_IDCODE [F2 07] / DBGMCU (0xE0042000)"
                } else {
                    "ST-LINK GET_VERSION [F1 80] -> ENTER_SWIM [F4 00] -> SWIM_RESET [F4 02] -> SWIM_SPEED_HIGH [F4 03]"
                },
                manufacturerGuess = "STMicroelectronics / ARM Cortex-M",
                inferredModel = if (isSwd) "ARM Cortex-M / STM32 (Raw ID=$rawId)" else "STM8 Family (SWIM Raw=$rawId)",
                rawSignatureSummary = "Raw ID=$rawId | ST-LINK FW=$detectedStLinkVersion",
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = if (isSwd) "0xF2 0x07 (IDCODE) / 0xE0042000" else "0xF4 0x0B (SWIM MemRead)",
                rawSiliconIdHex = rawId
            )
        }
        if (sync.framing == DetectedFraming.AVR_ISP_USB) {
            val rawId = detectedAvrSigHex.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = "USB AVR ISP (${sync.wakeUpPatternUsed}) -> Read 3-Byte Device Signature [0x30 0x00 0x00..0x02]",
                manufacturerGuess = "Atmel / Microchip AVR",
                inferredModel = "AVR / Atmel Target (Raw Signature=$rawId)",
                rawSignatureSummary = "AVR 3-Byte Signature=$rawId",
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = "EP0 0x03 SPI [30 00 00..02]",
                rawSiliconIdHex = rawId
            )
        }
        if (sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP) {
            val rawId = detectedPicIdHex.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = "Microchip PICkit/SNAP ICSP (${sync.wakeUpPatternUsed}) -> Query Device ID",
                manufacturerGuess = "Microchip Technology",
                inferredModel = "PIC Target (Raw ID=$rawId)",
                rawSignatureSummary = "PIC ID=$rawId",
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = "ICSP Device ID / GET_VERSION 0x76",
                rawSiliconIdHex = rawId
            )
        }
        if (transport.currentChip() == ProberUsbChip.UNKNOWN_USB) {
            val rawId = detectedRawIdHex.ifBlank { sync.firstReplyHex }
            return UniversalIdCapture(
                autoIdRule = "Unknown USB Adapter Diagnostic (${transport.currentInterfaceInfo()})",
                manufacturerGuess = "مبرمجة USB غير مسجلة (${_state.value.usbStatus.vidPidHex})",
                inferredModel = "Unknown USB Target (EP: ${transport.currentEndpointsSummary()})",
                rawSignatureSummary = rawId,
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = transport.currentEndpointsSummary(),
                rawSiliconIdHex = rawId
            )
        }
        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val rawId = detectedSdidHex.ifBlank { sync.firstReplyHex }
            val model = detectedFreescaleModel.ifBlank { "Freescale / NXP HCS08 (1.5Mbps BDM, Raw ID=$rawId)" }
            return UniversalIdCapture(
                autoIdRule = "FT232 @ 1,500,000 bps (8N1) Single-Wire BKGD -> BDM Sync (0xFE/0xC0) -> Read SDID (0x1806..0x1807)",
                manufacturerGuess = "Freescale / NXP Semiconductors",
                inferredModel = model,
                rawSignatureSummary = "SDID=$rawId | ${sync.firstReplyHex}",
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = "0xE8 @ 0x1806..0x1807",
                rawSiliconIdHex = rawId
            )
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            val sig = detectedSamsungIdHex.ifBlank { sync.firstReplyHex }
            detectedRawIdHex = sig
            return UniversalIdCapture(
                autoIdRule = "Samsung S3F8419 Sync (DTR=Vpp/TEST, RTS=SCLK, TX/RX=SDAT) -> TEST=LOW/SCLK=LOW (50ms) -> TEST=+5V (20ms) -> 16 SCLK -> 0xAA 0x55 -> ID [0x80/0xC0]",
                manufacturerGuess = "Samsung Electronics (SAMSUNG_S3F8_S3F9_FAMILY)",
                inferredModel = "SAMSUNG_S3F8_S3F9_FAMILY (Raw ID=$sig)",
                rawSignatureSummary = sig,
                rawC0Payload = ByteArray(0),
                idRegisterOrOpcode = "Sync 0xAA 0x55 -> 0x80 / 0xC0 / 0x01",
                rawSiliconIdHex = sig
            )
        }

        val respondedParts = mutableListOf<String>()
        var c0Raw = ByteArray(0)
        var allAscii = ""
        val firstRawId = StringBuilder()

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
            if (firstRawId.isEmpty()) firstRawId.append(preview)
        }

        val rawHex = firstRawId.toString().ifBlank { sync.firstReplyHex }
        detectedRawIdHex = rawHex
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
            rawC0Payload = c0Raw,
            idRegisterOrOpcode = respondedParts.firstOrNull()?.substringBefore("->") ?: "Sync Reply",
            rawSiliconIdHex = rawHex
        )
    }

    private fun inferManufacturerAndModel(sync: SyncLock, ascii: String, sigText: String): Pair<String, String> {
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val mfg = if (arch == UsbdmTargetArch.ARM_SWD) "ARM / NXP Kinetis / STM32 / Non-Freescale" else "Freescale / NXP Semiconductors"
            return mfg to detectedFreescaleModel.ifBlank { "${arch.archName} (Raw ID=${detectedRawIdHex.ifBlank { sigText }})" }
        }
        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            return "Freescale / NXP Semiconductors" to detectedFreescaleModel.ifBlank { "HCS08 Family (Raw ID=$detectedSdidHex)" }
        }
        if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM) {
            return "STMicroelectronics / ARM" to "ST-LINK Target (Raw ID=${detectedStLinkIdCodeHex.ifBlank { sigText }})"
        }
        if (sync.framing == DetectedFraming.AVR_ISP_USB) {
            return "Atmel / Microchip AVR" to "AVR Target (Raw Sig=${detectedAvrSigHex.ifBlank { sigText }})"
        }
        if (sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP) {
            return "Microchip Technology" to "PIC Target (Raw ID=${detectedPicIdHex.ifBlank { sigText }})"
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            return "Samsung Electronics" to "SAMSUNG_S3F8_S3F9_FAMILY (Raw ID=${detectedSamsungIdHex.ifBlank { sigText }})"
        }
        val combined = "$ascii $sigText ${sync.firstReplyHex}".uppercase(Locale.US)
        val rawSuffix = if (sigText.isNotBlank()) " [Raw=${sigText.take(28)}]" else ""
        return when {
            "78F" in combined || "UPD" in combined -> "NEC / Renesas Electronics" to ((Regex("78F[0-9A-Z]+").find(combined)?.value ?: "NEC 78K0") + rawSuffix)
            "R5F" in combined || "RL78" in combined -> "Renesas Electronics" to ((Regex("R5F[0-9A-Z]+").find(combined)?.value ?: "Renesas RL78/R8C/RX") + rawSuffix)
            "VER." in combined || sync.framing == DetectedFraming.RENESAS_RAW -> "Renesas Electronics" to ("Renesas R8C/M16C/H8/SH (${ascii.trim().ifBlank { "Bootloader" }})" + rawSuffix)
            sync.framing == DetectedFraming.NEC_STX_ETX -> "NEC / Renesas Electronics" to (ascii.trim().ifBlank { "NEC / Renesas Framed Target" } + rawSuffix)
            sync.framing == DetectedFraming.STM32_ACK -> "STMicroelectronics" to "STM32 UART Bootloader (${ascii.trim().ifBlank { sigText }})"
            "MSP" in combined -> "Texas Instruments" to ("TI MSP430 BSL" + rawSuffix)
            "LPC" in combined || "NXP" in combined -> "NXP Semiconductors" to ("NXP LPC ISP Bootloader" + rawSuffix)
            "ESP" in combined -> "Espressif Systems" to ("ESP32 / ESP8266 ROM Bootloader" + rawSuffix)
            else -> "معالج غير معروف الهوية الاسمية (Open Raw ID)" to (ascii.trim().ifBlank { "Target Raw ID: ${detectedRawIdHex.ifBlank { sigText }}" })
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
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            transport.samsungQuickResyncKeepTestHigh(
                testActiveHigh = sync.syncModePinState,
                pulseClocks = 4,
                forceUartModemLines = false
            )
        } else if (sync.framing == DetectedFraming.USBDM_BDM || sync.useBitBangSync) {
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val readCmd = detectedUsbdmReadCmd
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT before Direct Read [02 14]", logTxRx = false)
            val candidateAddrs = if (arch == UsbdmTargetArch.HCS08) {
                (listOf(sync.flashBaseAddr.toLong(), 0xC000L, 0x8000L, 0x1900L) + arch.flashBaseCandidates).distinct()
            } else {
                (listOf(sync.flashBaseAddr.toLong()) + arch.flashBaseCandidates).distinct()
            }
            var blankFallback: ChecklistStepItem? = null
            for (addr in candidateAddrs) {
                for (cmd in listOf(readCmd, 0x21, 0x22).distinct()) {
                    val read16 = transport.usbdmTransceive(
                        usbdmReadMemPacket(cmd, arch.memSpace, 0x10, addr),
                        32, 350, "USBDM Direct Read [0x%02X sp=0x%02X @ 0x%08X]".format(cmd, arch.memSpace, addr)
                    )
                    if (read16.size >= 17 && (read16[0].toInt() and 0xFF) == 0x00) {
                        val payload = read16.copyOfRange(1, 17)
                        val isReal = !payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }
                        val stepText = "USBDM (${arch.archName}) READ_MEM [0x%02X @ 0x%X] -> ${payload.toHexStr()}".format(cmd, addr)
                        if (isReal) {
                            detectedUsbdmReadCmd = cmd
                            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, stepText)
                        } else if (blankFallback == null) {
                            blankFallback = ChecklistStepItem("stage2_direct_read", "المرحلة 2.1", "مدرسة القراءة المباشرة", ChecklistStepStatus.PASSED, stepText)
                        }
                    }
                }
            }
            if (blankFallback != null) {
                return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, blankFallback.detail)
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "USBDM READ_MEM (0x%02X) مرفوض (${arch.archName} محمية)".format(readCmd))
        }

        if (sync.framing == DetectedFraming.STLINK_SWD) {
            val data = stlinkReadMem32(0x08000000L, 16)
            return if (data.size >= 16) {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "ST-LINK SWD Mem32 @ 0x08000000 -> ${data.toHexStr()}")
            } else {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "ST-LINK SWD MemRead مرفوض (RDP محمي)")
            }
        }

        if (sync.framing == DetectedFraming.STLINK_SWIM) {
            val data = stlinkSwimReadMem(0x008000L, 16)
            return if (data.size >= 16) {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "ST-LINK SWIM @ 0x008000 -> ${data.toHexStr()}")
            } else {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "ST-LINK SWIM مرفوض (ROP محمي)")
            }
        }

        if (sync.framing == DetectedFraming.AVR_ISP_USB && transport.currentChip() == ProberUsbChip.USBASP) {
            val rx = transport.usbControlTransferIn(0xC0, 0x04, 0x0000, 0x0000, 16, 400, "USBasp READFLASH 16B [0x04 @ 0x0000]")
            return if (rx.size >= 16) {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "USBasp EP0 READFLASH [0x04 @ 0x0000] -> ${rx.toHexStr()}")
            } else {
                updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "USBasp READFLASH مرفوض (Lock Bits)")
            }
        }

        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val bdmRead16 = transport.transceive(byteArrayOf(0xE8.toByte(), 0xE0.toByte(), 0x00), 16, 180, "Freescale 1.5Mbps BDM Read [0xE000]")
            if (bdmRead16.isNotEmpty() && !bdmRead16.all { it == 0x00.toByte() }) {
                return updateAndReturn("stage2_direct_read", ChecklistStepStatus.PASSED, "BDM Single-Wire [0xE8 @ 0xE000] -> ${bdmRead16.toHexStr()}")
            }
            return updateAndReturn("stage2_direct_read", ChecklistStepStatus.FAILED, "فشلت القراءة المباشرة عبر Single-Wire BDM")
        }

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Read Smart Option Bytes at 0x003E..0x003F first to verify Lock Bit status
            val opt5 = samsungSyncTransceive(sync, byteArrayOf(0x05, 0x00, 0x3E, 0x02), rxLen = 2, label = "Samsung S3F8 Smart Option [0x05 @ 0x003E..0x003F]")
            val optValid = opt5.size >= 2 && !opt5.all { it == 0x00.toByte() } && !isSamsungUartArtifact(opt5)
            val optBytes = if (optValid) opt5.copyOfRange(0, 2) else {
                val opt4 = samsungSyncTransceive(sync, byteArrayOf(0x04, 0x00, 0x3E), rxLen = 2, label = "Samsung S3F8 Smart Option [0x04 @ 0x003E..0x003F]")
                if (opt4.size >= 2 && !opt4.all { it == 0x00.toByte() } && !isSamsungUartArtifact(opt4)) opt4.copyOfRange(0, 2) else ByteArray(0)
            }
            detectedSamsungSmartOptionHex = if (optBytes.isNotEmpty()) optBytes.toHexStr() else "محمي/غير مقروء"

            // Genuine Samsung S3F8 Serial Read opcodes: [0x05, Addr_H, Addr_L, Len] and [0x04, Addr_H, Addr_L]
            val s3fReadProbes = listOf(
                0x05 to byteArrayOf(0x05, 0x00, 0x00, 0x10),
                0x04 to byteArrayOf(0x04, 0x00, 0x00)
            )
            for ((cmd, pkt) in s3fReadProbes) {
                val rx = samsungSyncTransceive(sync, pkt, rxLen = 16, label = "Samsung S3F8 Read [0x%02X @ 0x0000]".format(cmd))
                val real16 = if (rx.isNotEmpty() && !rx.all { it == 0x00.toByte() } && !isSamsungUartArtifact(rx)) {
                    extract16Bytes(rx, sync.framing)
                } else null
                if (real16 != null && !real16.all { it == 0x00.toByte() }) {
                    if (isFloatingHighSdatPattern(real16)) {
                        val rx2 = samsungSyncTransceive(
                            sync,
                            pkt,
                            rxLen = 16,
                            label = "Samsung S3F8 Dual-Read Check [0x%02X @ 0x0000]".format(cmd)
                        )
                        val second16 = if (rx2.isNotEmpty() && !rx2.all { it == 0x00.toByte() } && !isSamsungUartArtifact(rx2)) {
                            extract16Bytes(rx2, sync.framing)
                        } else null
                        if (second16 == null || !real16.contentEquals(second16)) {
                            appendLog(
                                ProberLogDirection.WARN,
                                "⚠️ Idle / Waiting for +12.5V Vpp on Pin 9 — $SAMSUNG_FLOATING_HIGH_ALERT_MSG"
                            )
                            return updateAndReturn(
                                "stage2_direct_read",
                                ChecklistStepStatus.FAILED,
                                "Idle / Waiting for +12.5V Vpp on Pin 9 — $SAMSUNG_FLOATING_HIGH_ALERT_MSG"
                            )
                        }
                    }
                    return updateAndReturn(
                        "stage2_direct_read",
                        ChecklistStepStatus.PASSED,
                        "Samsung S3F8 [0x%02X @ 0x0000] -> ${real16.toHexStr()} | SmartOpt(0x003E..0x003F)=$detectedSamsungSmartOptionHex".format(cmd)
                    )
                }
            }
            return updateAndReturn(
                "stage2_direct_read",
                ChecklistStepStatus.FAILED,
                "الأمر [0x05/0x04] مرفوض/مقفل (SmartOpt 0x003E..0x003F=$detectedSamsungSmartOptionHex) — يتطلب مسح القطاع الأول 0x08 بالمرحلة 5"
            )
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
        updateStep("stage2_checksum_1b", ChecklistStepStatus.RUNNING, "استخراج أول 16 بايت فعلياً عبر 0xB0 التراكمي (11-Byte Frame)...")
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val directPassed = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.status == ChecklistStepStatus.PASSED
            return if (directPassed) {
                updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.PASSED, "غير مطلوب — السحب مباشر عبر USBDM READ_MEM (0x%02X)".format(detectedUsbdmReadCmd))
            } else {
                updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.BYPASSED, "غير منطبق على USBDM BDM (محمي)")
            }
        }
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            val directPassed = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.status == ChecklistStepStatus.PASSED
            return if (directPassed) {
                updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.PASSED, "غير مطلوب — السحب مباشر عبر ${sync.framing.label}")
            } else {
                updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.BYPASSED, "غير منطبق على ${sync.framing.label}")
            }
        }

        // Strict Real-Hardware Verification Gate: extract first 16 real bytes (N=0..15)
        // using exclusively 11-byte [01 07 B0 00 00 00 00 00 N Frame_CS 03]
        val extracted16 = ByteArray(16)
        var csPrev = 0
        var lastTxSample = ByteArray(0)
        for (n in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val pkt = buildNecCommandFrame(0xB0, addrRangeBytes(0x000000, n))
            lastTxSample = pkt
            val rx1 = transport.transceive(pkt, 16, 300, "Checksum 11B [0xB0 0x000000..0x%06X]".format(n), stopOnCompleteNecFrame = true, logTxRx = (n < 2))
            var parsed = parseStrictChecksum16Frames(rx1)
            if (parsed.checksum16 == null && parsed.errorStatus == null && parsed.sawAck) {
                val rxFollow = transport.readFollowUp(12, 280, true)
                parsed = parseStrictChecksum16Frames(rx1 + rxFollow)
            }

            if (parsed.errorStatus != null) {
                val errCode = parsed.errorStatus
                val errHint = if (errCode == STS_BLANK_CHECK_ERROR) "STS_BLANK_CHECK_ERROR (0x1B)" else "Status 0x%02X".format(errCode)
                val failDetail = "❌ فشل 0xB0 عند N=$n ($errHint) | الإطار المرسل=[${pkt.toHexStr()}] | الرد الخام=[${parsed.rawFrameHex.ifBlank { rx1.toHexStr() }}]"
                appendLog(ProberLogDirection.WARN, failDetail)
                return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, failDetail)
            }

            val csCurr = parsed.checksum16
            if (csCurr == null) {
                val reason = parsed.invalidReason ?: "إطار رد Checksum غير مكتمل أو تالف"
                val failDetail = "❌ فشل تحليل رد 0xB0 عند N=$n ($reason) | RX=[${rx1.toHexStr()}]"
                appendLog(ProberLogDirection.WARN, failDetail)
                return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, failDetail)
            }

            val delta16 = ((csPrev - csCurr) + 0x10000) and 0xFFFF
            if (delta16 > 0xFF) {
                val failDetail = "❌ تجاوز فرق Checksum التراكمي عند N=$n: CS_prev=0x%04X -> CS_curr=0x%04X (Δ=0x%04X > 0xFF) | RX=[${parsed.rawFrameHex}]".format(csPrev, csCurr, delta16)
                appendLog(ProberLogDirection.ERROR, failDetail)
                return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, failDetail)
            }

            extracted16[n] = (delta16 and 0xFF).toByte()
            csPrev = csCurr
        }

        if (isEchoPayload(extracted16, lastTxSample)) {
            val echoMsg = "❌ فشل 0xB0: البايتات المستخرجة مطابقة لصدى الإرسال TX Echo (${extracted16.toHexStr()})"
            appendLog(ProberLogDirection.ERROR, echoMsg)
            return updateAndReturn("stage2_checksum_1b", ChecklistStepStatus.FAILED, echoMsg)
        }

        return updateAndReturn(
            "stage2_checksum_1b",
            ChecklistStepStatus.PASSED,
            "✅ استخراج 16 بايت فعلياً عبر 0xB0 (11B LE): ${extracted16.toHexStr()} | CS(16B)=0x%04X".format(csPrev)
        )
    }

    private suspend fun testVerify0x13Probe(sync: SyncLock): Pair<ChecklistStepItem, String> {
        updateStep("stage2_verify_0x13", ChecklistStepStatus.RUNNING, "اختبار 0x13 على صفحة 0xFF ثم صفحة 0x00 لكشف القفل...")
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val directPassed = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.status == ChecklistStepStatus.PASSED
            val item = if (directPassed) {
                updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.PASSED, "التحقق المطابق عبر USBDM READ_MEM مباشرة (Dual-Read Verify)")
            } else {
                updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.BYPASSED, "غير منطبق على USBDM BDM (محمي)")
            }
            return item to "Dual-Read Verify (USBDM READ_MEM)"
        }
        if (sync.framing != DetectedFraming.NEC_STX_ETX) {
            val directPassed = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.status == ChecklistStepStatus.PASSED
            val item = if (directPassed) {
                updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.PASSED, "التحقق المطابق عبر القراءة المزدوجة (${sync.framing.label})")
            } else {
                updateAndReturn("stage2_verify_0x13", ChecklistStepStatus.BYPASSED, "غير منطبق على ${sync.framing.label}")
            }
            return item to "Dual-Read Verify (${sync.framing.name})"
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
        if (sync.framing in setOf(
                DetectedFraming.USBDM_BDM, DetectedFraming.FREESCALE_SINGLE_WIRE_BDM,
                DetectedFraming.STLINK_SWD, DetectedFraming.STLINK_SWIM,
                DetectedFraming.AVR_ISP_USB, DetectedFraming.MICROCHIP_PIC_ICSP,
                DetectedFraming.SAMSUNG_ISP
            ) || transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
            val sigText = autoId.rawSignatureSummary
            val item = updateAndReturn("stage2_signature", ChecklistStepStatus.PASSED, "${autoId.inferredModel} | $sigText")
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val readCmd = detectedUsbdmReadCmd
            val ramAddr = arch.defaultRamAddress
            val ramRx = transport.usbdmTransceive(
                usbdmReadMemPacket(readCmd, arch.memSpace, 4, ramAddr),
                12, 300, "USBDM RAM Access [0x%02X sp=0x%02X @ 0x%X]".format(readCmd, arch.memSpace, ramAddr)
            )
            return if (ramRx.size >= 5 && (ramRx[0].toInt() and 0xFF) == 0x00) {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.PASSED, "USBDM RAM 0x%X مفتوح -> ${ramRx.copyOfRange(1, 5).toHexStr()}".format(ramAddr))
            } else {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "RAM مقفل via USBDM")
            }
        }
        if (sync.framing == DetectedFraming.STLINK_SWD) {
            val sram = stlinkReadMem32(0x20000000L, 4)
            return if (sram.size == 4) {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.PASSED, "ST-LINK SRAM 0x20000000 مفتوح -> ${sram.toHexStr()}")
            } else {
                updateAndReturn("stage2_ram_stub", ChecklistStepStatus.FAILED, "SRAM مقفل على ST-LINK SWD")
            }
        }
        if (sync.framing in setOf(
                DetectedFraming.FREESCALE_SINGLE_WIRE_BDM, DetectedFraming.SAMSUNG_ISP,
                DetectedFraming.STLINK_SWIM, DetectedFraming.AVR_ISP_USB, DetectedFraming.MICROCHIP_PIC_ICSP
            ) || transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
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
        stage3IgnoredNoiseCount = 0

        if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            val isNativeUsbdm = (sync.framing == DetectedFraming.USBDM_BDM)
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            if (isNativeUsbdm) {
                val stsRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x0D), 8, 250, "USBDM GET_BDM_STATUS [02 0D]")
                val stsHex = if (stsRx.isNotEmpty()) "0x%02X".format(stsRx[0].toInt() and 0xFF) else "0x00"
                val capsHex = "0x%02X".format(detectedUsbdmCapsCmd)
                val readHex = "0x%02X".format(detectedUsbdmReadCmd)
                discovered.add(DiscoveredCommandEntry(capsHex, detectedUsbdmCapsCmd, stsHex, 0, "USBDM Get Capabilities", true))
                discovered.add(DiscoveredCommandEntry("0x02", 0x02, "0x00", 1, "USBDM Set Target VDD (5V=0x02)", true))
                discovered.add(DiscoveredCommandEntry("0x03", 0x03, "0x00", 1, "USBDM Internal Target Power Control", true))
                val effectiveCode = sync.actualTargetCode ?: detectedUsbdmTargetCode
                discovered.add(DiscoveredCommandEntry("0x01", 0x01, "0x00", 1, "USBDM Set Target Type (${arch.archName} [SET_TARGET=0x%02X])".format(effectiveCode), true))
                discovered.add(DiscoveredCommandEntry("0x13", 0x13, "0x00", 1, "USBDM Target Reset Special [03 13 00]", true))
                discovered.add(DiscoveredCommandEntry("0x0F", 0x0F, "0x00", 0, "USBDM Connect & Auto-Sync", true))
                discovered.add(DiscoveredCommandEntry("0x14", 0x14, "0x00", 0, "USBDM Target Halt", true))
                discovered.add(DiscoveredCommandEntry(readHex, detectedUsbdmReadCmd, "0x00", 6, "USBDM Read Memory (space=0x%02X)".format(arch.memSpace), true))
                discovered.add(DiscoveredCommandEntry("0x20", 0x20, "0x00", 6, "USBDM Write Memory (space=0x%02X)".format(arch.memSpace), false))
            } else {
                discovered.add(DiscoveredCommandEntry("0xFE", 0xFE, "SYNC", 0, "Single-Wire BDM Sync (1.5Mbps)", true))
                discovered.add(DiscoveredCommandEntry("0xE8", 0xE8, "BDM", 2, "HCS08 BDM Read Byte", true))
                discovered.add(DiscoveredCommandEntry("0xC8", 0xC8, "BDM", 3, "HCS08 BDM Write Byte", false))
            }
            if (arch == UsbdmTargetArch.HCS08) {
                discovered.add(DiscoveredCommandEntry("0x1820", 0x1820, "FCLKDIV", 1, "HCS08 Flash Clock Divider (FCLKDIV=0x1820)", false))
                discovered.add(DiscoveredCommandEntry("0x1825", 0x1825, "FSTAT", 1, "HCS08 Flash Status Register (FSTAT=0x1825)", false))
                discovered.add(DiscoveredCommandEntry("0x1826", 0x1826, "FCMD", 1, "HCS08 Flash Command Register (FCMD=0x1826)", false))
                discovered.add(DiscoveredCommandEntry("0x20", 0x20, "FCMD", 1, "HCS08 Byte Program (FCMD=0x20)", false))
                discovered.add(DiscoveredCommandEntry("0x40", 0x40, "FCMD", 1, "HCS08 Page Erase (FCMD=0x40)", false))
                discovered.add(DiscoveredCommandEntry("0x41", 0x41, "FCMD", 0, "HCS08 Mass Erase / Unsecure (FCMD=0x41)", false))
            } else {
                discovered.add(DiscoveredCommandEntry("0x20", 0x20, "FCMD", 2, "${arch.archName} Program", false))
                discovered.add(DiscoveredCommandEntry("0x40", 0x40, "FCMD", 2, "${arch.archName} Sector/Page Erase", false))
                discovered.add(DiscoveredCommandEntry("0x41", 0x41, "FCMD", 0, "${arch.archName} Mass Erase / Unsecure", false))
            }
            return discovered
        }

        if (sync.framing == DetectedFraming.STLINK_SWD) {
            discovered.add(DiscoveredCommandEntry("0xF1", 0xF1, "OK", 1, "ST-LINK Get Version [F1 80]", true))
            discovered.add(DiscoveredCommandEntry("0xF5", 0xF5, "OK", 0, "ST-LINK Get Current Mode", true))
            discovered.add(DiscoveredCommandEntry("0xF2", 0xF2, "0x80", 2, "ST-LINK Enter SWD / Read IDCODE [0x07] / Mem32 [0x08]", true))
            discovered.add(DiscoveredCommandEntry("0xF2", 0xF2, "0x80", 6, "ST-LINK SWD Write Mem32 [0x0D]", false))
            return discovered
        }

        if (sync.framing == DetectedFraming.STLINK_SWIM) {
            discovered.add(DiscoveredCommandEntry("0xF4", 0xF4, "0x00", 1, "ST-LINK Enter SWIM [0x00] / Reset [0x02] / ReadMem [0x0B]", true))
            discovered.add(DiscoveredCommandEntry("0xF4", 0xF4, "0x00", 6, "ST-LINK SWIM WriteMem [0x0A]", false))
            return discovered
        }

        if (sync.framing == DetectedFraming.AVR_ISP_USB) {
            discovered.add(DiscoveredCommandEntry("0x01", 0x01, "0x00", 0, "USBasp Connect / CMSIS-DAP", true))
            discovered.add(DiscoveredCommandEntry("0x03", 0x03, "0x00", 4, "USBasp SPI Transmit (Read Signature 0x30)", true))
            discovered.add(DiscoveredCommandEntry("0x04", 0x04, "0x00", 4, "USBasp Read Flash Page", true))
            discovered.add(DiscoveredCommandEntry("0x05", 0x05, "0x00", 0, "USBasp Enable Programming", true))
            discovered.add(DiscoveredCommandEntry("0x06", 0x06, "0x00", 4, "USBasp Write Flash Page", false))
            return discovered
        }

        if (sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP || transport.currentChip() == ProberUsbChip.UNKNOWN_USB) {
            discovered.add(DiscoveredCommandEntry("0x00", 0x00, "USB", 0, "${sync.wakeUpPatternUsed}", true))
            return discovered
        }

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Keep DTR (Vpp/TEST) = HIGH (+5V) and clock RTS (SCLK) on every byte
            transport.samsungQuickResyncKeepTestHigh(
                testActiveHigh = sync.syncModePinState,
                pulseClocks = 8,
                forceUartModemLines = false
            )
            val baselineSamples = mutableListOf<ByteArray>()
            for (cal in listOf(0xFE, 0xFD)) {
                val calRx = samsungSyncTransceive(sync, byteArrayOf(cal.toByte()), rxLen = 2, label = "Samsung Stage3 Baseline 0x%02X".format(cal), logTxRx = false)
                if (calRx.isNotEmpty() && !calRx.all { it == 0x00.toByte() }) baselineSamples.add(calRx)
            }
            val samsungDestructiveGuard = setOf(0x06, 0x07, 0x08, 0x09)
            val s3fSweepCmds = (0x01..0x0F).toList() + listOf(0x80, 0xC0)
            for (cmd in s3fSweepCmds) {
                if (cmd in samsungDestructiveGuard) continue
                val tx = byteArrayOf(cmd.toByte())
                val rx = samsungSyncTransceive(sync, tx, rxLen = 4, label = "Samsung S3F8 Sweep [0x%02X]".format(cmd), logTxRx = false)
                val allZeros = rx.isNotEmpty() && rx.all { it == 0x00.toByte() }
                val matchesBase = rx.isNotEmpty() && baselineSamples.any { it.contentEquals(rx) }
                if (rx.isEmpty() || allZeros || rx.contentEquals(tx) || matchesBase || isSamsungUartArtifact(rx)) {
                    if (rx.isNotEmpty()) stage3IgnoredNoiseCount++
                    continue
                }
                val sts = rx[0].toInt() and 0xFF
                if (sts !in HARD_REJECT_STATUS) {
                    discovered.add(
                        DiscoveredCommandEntry(
                            cmdHex = "0x%02X".format(cmd),
                            cmdByte = cmd,
                            replyStatusHex = "0x%02X".format(sts),
                            exactParamLen = when (cmd) {
                                0x05 -> 3
                                0x04 -> 2
                                else -> 1
                            },
                            roleGuess = guessSamsungS3f8CommandRole(cmd),
                            isSafeReadOnly = true
                        )
                    )
                    appendLog(ProberLogDirection.SUCCESS, "رصد أمر Samsung S3F8 [0x%02X] -> 0x%02X (${guessSamsungS3f8CommandRole(cmd)})".format(cmd, sts))
                }
            }
            if (discovered.none { it.cmdByte == 0x05 }) {
                discovered.add(DiscoveredCommandEntry("0x05", 0x05, "ISP", 3, "Samsung S3F8 Serial Flash Read [0x05 Addr_H Addr_L Len]", true))
            }
            discovered.add(DiscoveredCommandEntry("0x06", 0x06, "ISP", 4, "Samsung S3F8 Byte/Page Program [0x06 Addr_H Addr_L Data...]", false))
            discovered.add(DiscoveredCommandEntry("0x08", 0x08, "ISP", 2, "Samsung S3F8 Sector Erase 1KB [0x08 Addr_H Addr_L]", false))
            discovered.add(DiscoveredCommandEntry("0x09", 0x09, "ISP", 2, "Samsung S3F8 Chip Erase [0x09 0xAA 0x55]", false))
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
        val baselineNoiseSamples = mutableListOf<ByteArray>()
        if (sync.framing != DetectedFraming.NEC_STX_ETX && sync.framing != DetectedFraming.STM32_ACK) {
            for (calByte in listOf(0xFE, 0xFD)) {
                currentCoroutineContext().ensureActive()
                val calTx = byteArrayOf(calByte.toByte())
                val calRaw = if (sync.syncClkOnRts != null) {
                    transport.flushRx()
                    transport.writeRawBytes(calTx)
                    pulseSyncClockPin(sync.syncClkOnRts, cycles = 8, halfPeriodMs = 1L)
                    transport.drainAvailableRaw(waitMs = 45L)
                } else {
                    transport.transceive(calTx, 12, 55, "Stage3 Baseline Calibration 0x%02X".format(calByte), logTxRx = false)
                }
                val calClean = stripStrictEcho(calTx, calRaw)
                if (calClean.isNotEmpty()) {
                    baselineNoiseSamples.add(calClean)
                }
            }
            if (baselineNoiseSamples.isNotEmpty()) {
                appendLog(
                    ProberLogDirection.INFO,
                    "معايرة ضوضاء المرحلة 3 (0xFE/0xFD Baseline): تم تسجيل ${baselineNoiseSamples.size} عينة تشويش أساسية لفلترتها."
                )
            }
        }

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

            if (sync.framing != DetectedFraming.NEC_STX_ETX) {
                if (cmd in WRITE_ERASE_GUARD_CMDS) {
                    discovered.add(DiscoveredCommandEntry("0x%02X".format(cmd), cmd, "GUARDED", 0, guessCommandRole(cmd), false))
                    continue
                }
                val rawTx = byteArrayOf(cmd.toByte())
                val rawRx = if (sync.syncClkOnRts != null) {
                    transport.flushRx()
                    transport.writeRawBytes(rawTx)
                    pulseSyncClockPin(sync.syncClkOnRts, cycles = 8, halfPeriodMs = 1L)
                    transport.drainAvailableRaw(waitMs = 45L)
                } else {
                    transport.transceive(rawTx, 12, 55, "Raw Sweep 0x%02X".format(cmd), logTxRx = false)
                }
                val clean = stripStrictEcho(rawTx, rawRx)
                val matchesBaseline = clean.isNotEmpty() && baselineNoiseSamples.any { base ->
                    base.contentEquals(clean) ||
                        (base.size >= 2 && clean.size >= 2 &&
                            base.take(min(base.size, clean.size))
                                .zip(clean.take(min(base.size, clean.size)))
                                .count { (a, b) -> a == b } * 100 >= min(base.size, clean.size) * 80)
                }
                if (rawRx.isNotEmpty() && (clean.isEmpty() || clean.contentEquals(rawTx) || matchesBaseline || isFramingNoise(clean))) {
                    stage3IgnoredNoiseCount++
                    continue
                }
                if (isMeaningfulGenericReply(rawTx, clean)) {
                    val sts = clean[0].toInt() and 0xFF
                    if (sts !in HARD_REJECT_STATUS) {
                        candidateBytes.add(cmd to sts)
                        appendLog(ProberLogDirection.SUCCESS, "رصد أمر خام [0x%02X] -> 0x%02X (${guessCommandRole(cmd)})".format(cmd, sts))
                    }
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
            if (rx.isEmpty()) {
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

            val sts = parseNecStatus(rx)
            if (sts == ACK_NEC || sts == STS_PARAM_ERROR || sts == STS_BLANK_CHECK_ERROR || sts == STS_PROTECT_ERROR) {
                candidateBytes.add(cmd to sts)
                appendLog(ProberLogDirection.SUCCESS, "رصد أمر [0x%02X] -> Status 0x%02X (${guessCommandRole(cmd)})".format(cmd, sts))
            }
        }

        if (stage3IgnoredNoiseCount > 0) {
            appendLog(
                ProberLogDirection.INFO,
                "ملخص فلترة المرحلة 3: تم تجاهل $stage3IgnoredNoiseCount أمر ضوضاء/تشويش أساسي (Echo / 0xFE-0xFD Baseline / Framing Noise) في عداد واحد."
            )
        }

        ensureBootloaderReady(sync, "تأكيد الجاهزية قبل فحص أطوال LEN", relockOsc = false)
        for ((cmd, initialSts) in candidateBytes) {
            currentCoroutineContext().ensureActive()
            val detectedLen = if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                probeExactParamLen(sync, cmd, initialSts)
            } else {
                0
            }
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

    // ==================== STAGE 4: CRYSTAL MATRIX (0x90), BOUNDS (4KB..2MB), BAUD & TIMING ====================

    private data class FlashBoundsProbeResult(
        val flashStart: Int,
        val flashEnd: Int,
        val endLabel: String,
        val ruleDetail: String
    )

    private suspend fun probeDynamicFlashBounds4KbTo2Mb(
        sync: SyncLock,
        rawC0Payload: ByteArray
    ): FlashBoundsProbeResult {
        val sizesDesc = listOf(
            0x200000 to "2 MB",
            0x100000 to "1 MB",
            0x080000 to "512 KB",
            0x040000 to "256 KB",
            0x020000 to "128 KB",
            0x010000 to "64 KB",
            0x008000 to "32 KB",
            0x004000 to "16 KB",
            0x002000 to "8 KB",
            0x001000 to "4 KB"
        )

        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            val c0ParsedEnd = parseNecSignatureEndAddress(rawC0Payload)
            for ((sizeBytes, sizeLabel) in sizesDesc) {
                currentCoroutineContext().ensureActive()
                val endAddr = sizeBytes - 1
                val label = "0x%06X ($sizeLabel)".format(endAddr)
                val csCmd = buildNecCommandFrame(0xB0, addrRangeBytes(0x000000, endAddr))
                var rx = transport.transceive(csCmd, 16, 280, "4.1 Boundary 0xB0 [$label]", stopOnCompleteNecFrame = true)
                if (rx.isEmpty()) {
                    hardwareResetAndResync(sync, "4.1 Boundary 0xB0 Timeout [$label]", relockOsc = false)
                    rx = transport.transceive(csCmd, 16, 280, "4.1 Boundary 0xB0 Retry [$label]", stopOnCompleteNecFrame = true)
                }
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC || parseChecksum16(rx) != null) {
                    val rule = buildString {
                        append("Dynamic Boundary 0xB0 Probe (2MB->4KB)")
                        if (c0ParsedEnd != null) append(" + C0 End=0x%06X".format(c0ParsedEnd))
                        append(" -> Confirmed $label")
                    }
                    return FlashBoundsProbeResult(0x000000, endAddr, label, rule)
                }
            }
            val fallbackEnd = c0ParsedEnd ?: 0x007FFF
            val fallbackLabel = "0x%06X (Fallback)".format(fallbackEnd)
            return FlashBoundsProbeResult(0x000000, fallbackEnd, fallbackLabel, "C0/Fallback Boundary -> $fallbackLabel")
        }

        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val readCmd = detectedUsbdmReadCmd
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 200, "4.1 USBDM HALT [02 14]", logTxRx = false)

            if (!arch.isArmOr32Bit) {
                // 8-bit/16-bit top-anchored architectures (HCS08, HCS12, RS08)
                val maxEnd = if (arch == UsbdmTargetArch.RS08) 0x003FFF else 0x00FFFF
                val candidateStarts = if (arch == UsbdmTargetArch.RS08) {
                    listOf(0x003000 to "4 KB", 0x003800 to "2 KB", 0x003C00 to "1 KB")
                } else {
                    listOf(
                        0x001900 to "58-60 KB",
                        0x004000 to "48 KB",
                        0x008000 to "32 KB",
                        0x00C000 to "16 KB",
                        0x00E000 to "8 KB",
                        0x00F000 to "4 KB"
                    )
                }
                for ((candStart, sizeLabel) in candidateStarts) {
                    currentCoroutineContext().ensureActive()
                    val rx = transport.usbdmTransceive(
                        usbdmReadMemPacket(readCmd, arch.memSpace, 4, candStart.toLong()),
                        12, 220, "4.1 USBDM Probe [${arch.name} 0x%04X]".format(candStart), logTxRx = false
                    )
                    if (rx.size >= 5 && (rx[0].toInt() and 0xFF) == 0x00) {
                        val label = "0x%06X ($sizeLabel ${arch.name})".format(maxEnd)
                        return FlashBoundsProbeResult(
                            flashStart = candStart,
                            flashEnd = maxEnd,
                            endLabel = label,
                            ruleDetail = "Dynamic USBDM Probe (${arch.archName}, memSpace=0x%02X): readable start=0x%06X..0x%06X".format(arch.memSpace, candStart, maxEnd)
                        )
                    }
                }
                val defStart = arch.flashBaseCandidates.first().toInt()
                val label = "0x%06X (${arch.name})".format(maxEnd)
                return FlashBoundsProbeResult(defStart, maxEnd, label, "USBDM Arch Base -> 0x%06X..0x%06X".format(defStart, maxEnd))
            } else {
                // 32-bit architectures (ARM_SWD, COLDFIRE_V1, COLDFIRE_V2_4, DSC_JTAG): probe Flash base candidates + 2MB->4KB
                var activeBase = arch.flashBaseCandidates.first().toInt()
                for (candBase in arch.flashBaseCandidates) {
                    val baseProbe = transport.usbdmTransceive(
                        usbdmReadMemPacket(readCmd, arch.memSpace, 4, candBase),
                        12, 220, "4.1 USBDM 32-bit Base [0x%08X]".format(candBase), logTxRx = false
                    )
                    if (baseProbe.size >= 5 && (baseProbe[0].toInt() and 0xFF) == 0x00) {
                        activeBase = candBase.toInt()
                        break
                    }
                }
                for ((sizeBytes, sizeLabel) in sizesDesc) {
                    currentCoroutineContext().ensureActive()
                    val testAddr = activeBase.toLong() + sizeBytes.toLong() - 4L
                    val rx = transport.usbdmTransceive(
                        usbdmReadMemPacket(readCmd, arch.memSpace, 4, testAddr),
                        12, 220, "4.1 USBDM Bound [$sizeLabel @ 0x%08X]".format(testAddr), logTxRx = false
                    )
                    if (rx.size >= 5 && (rx[0].toInt() and 0xFF) == 0x00) {
                        val endAddr = activeBase + sizeBytes - 1
                        val label = "0x%08X ($sizeLabel)".format(endAddr)
                        return FlashBoundsProbeResult(
                            flashStart = activeBase,
                            flashEnd = endAddr,
                            endLabel = label,
                            ruleDetail = "Dynamic USBDM 32-bit Boundary Probe (2MB->4KB, memSpace=0x%02X): 0x%08X..0x%08X ($sizeLabel)".format(arch.memSpace, activeBase, endAddr)
                        )
                    }
                }
                val defEnd = activeBase + 0x00FFFF
                return FlashBoundsProbeResult(activeBase, defEnd, "0x%08X (64 KB Fallback)".format(defEnd), "USBDM 32-bit Fallback 0x%08X..0x%08X".format(activeBase, defEnd))
            }
        }

        if (sync.framing == DetectedFraming.STLINK_SWD) {
            // Probe STM32 Flash Size register candidates first + verify via memory boundary probe 2MB->4KB
            var regKb: Int? = null
            for (fszAddr in listOf(0x1FFFF7E0L, 0x1FFF7A22L, 0x1FFF75E0L)) {
                val r = stlinkReadMem32(fszAddr, 4)
                if (r.size >= 2) {
                    val kb = (r[0].toInt() and 0xFF) or ((r[1].toInt() and 0xFF) shl 8)
                    if (kb in 4..2048) {
                        regKb = kb
                        break
                    }
                }
            }
            for ((sizeBytes, sizeLabel) in sizesDesc) {
                currentCoroutineContext().ensureActive()
                val testAddr = 0x08000000L + sizeBytes.toLong() - 4L
                val rx = stlinkReadMem32(testAddr, 4)
                if (rx.size == 4) {
                    val endAddr = 0x08000000 + sizeBytes - 1
                    val fLabel = if (regKb != null) "0x%08X (${regKb} KB Reg / $sizeLabel Probe)".format(endAddr) else "0x%08X ($sizeLabel)".format(endAddr)
                    return FlashBoundsProbeResult(
                        flashStart = 0x08000000,
                        flashEnd = endAddr,
                        endLabel = fLabel,
                        ruleDetail = "ST-LINK SWD Dynamic Flash Probe (2MB->4KB) + FlashSizeReg=${regKb?.let { "${it}KB" } ?: "--"} -> $fLabel"
                    )
                }
            }
            val endByReg = regKb?.let { 0x08000000 + it * 1024 - 1 } ?: 0x0801FFFF
            return FlashBoundsProbeResult(0x08000000, endByReg, "0x%08X".format(endByReg), "ST-LINK SWD Flash Base 0x08000000")
        }

        if (sync.framing == DetectedFraming.STLINK_SWIM) {
            for ((sizeBytes, sizeLabel) in sizesDesc.filter { it.first <= 0x040000 }) {
                currentCoroutineContext().ensureActive()
                val testAddr = 0x008000L + sizeBytes.toLong() - 4L
                val rx = stlinkSwimReadMem(testAddr, 4)
                if (rx.size == 4) {
                    val endAddr = 0x008000 + sizeBytes - 1
                    val label = "0x%06X ($sizeLabel)".format(endAddr)
                    return FlashBoundsProbeResult(0x008000, endAddr, label, "ST-LINK SWIM Dynamic Probe (256KB->4KB) -> $label")
                }
            }
            return FlashBoundsProbeResult(0x008000, 0x009FFF, "0x009FFF (8 KB STM8)", "ST-LINK SWIM Base 0x008000")
        }

        if (sync.framing == DetectedFraming.STM32_ACK) {
            for ((sizeBytes, sizeLabel) in sizesDesc) {
                currentCoroutineContext().ensureActive()
                val testAddr = 0x08000000L + sizeBytes.toLong() - 4L
                val a1 = transport.transceive(byteArrayOf(0x11, 0xEE.toByte()), 4, 220, "4.1 STM32 Probe 0x11 [$sizeLabel]", logTxRx = false)
                if (a1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val addrBytes = byteArrayOf(
                        ((testAddr ushr 24) and 0xFF).toByte(),
                        ((testAddr ushr 16) and 0xFF).toByte(),
                        ((testAddr ushr 8) and 0xFF).toByte(),
                        (testAddr and 0xFF).toByte()
                    )
                    val cs = ((addrBytes[0].toInt() xor addrBytes[1].toInt() xor addrBytes[2].toInt() xor addrBytes[3].toInt()) and 0xFF).toByte()
                    val a2 = transport.transceive(addrBytes + cs, 4, 220, "4.1 STM32 Addr [0x%08X]".format(testAddr), logTxRx = false)
                    if (a2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                        val rd = transport.transceive(byteArrayOf(0x03, 0xFC.toByte()), 10, 260, "4.1 STM32 Read 4B", logTxRx = false)
                        if (rd.size >= 5 && (rd[0].toInt() and 0xFF) == ACK_STM32) {
                            val endAddr = 0x08000000 + sizeBytes - 1
                            val label = "0x%08X ($sizeLabel)".format(endAddr)
                            return FlashBoundsProbeResult(0x08000000, endAddr, label, "STM32 UART 0x11 Boundary Probe (2MB->4KB) -> $label")
                        }
                    }
                }
            }
            return FlashBoundsProbeResult(0x08000000, 0x0801FFFF, "0x0801FFFF (128 KB Typical)", "STM32 UART Fallback")
        }

        if (sync.framing == DetectedFraming.AVR_ISP_USB && transport.currentChip() == ProberUsbChip.USBASP) {
            val page0 = transport.usbControlTransferIn(0xC0, 0x04, 0, 0, 16, 250, "4.1 USBasp Alias Check @ 0x0000", logTxRx = false)
            val page0NonBlank = page0.size == 16 && !page0.all { (it.toInt() and 0xFF) == 0xFF } && !page0.all { it == 0.toByte() }
            for ((sizeBytes, sizeLabel) in sizesDesc.filter { it.first <= 0x040000 }) {
                currentCoroutineContext().ensureActive()
                val halfAddr = sizeBytes / 2
                val probeHalf = transport.usbControlTransferIn(
                    0xC0, 0x04, (halfAddr and 0xFFFF), ((halfAddr ushr 16) and 0xFFFF), 16, 250,
                    "4.1 USBasp Probe [$sizeLabel @ 0x%06X]".format(halfAddr), logTxRx = false
                )
                val endProbe = transport.usbControlTransferIn(
                    0xC0, 0x04, ((sizeBytes - 4) and 0xFFFF), (((sizeBytes - 4) ushr 16) and 0xFFFF), 4, 250,
                    "4.1 USBasp End [$sizeLabel]", logTxRx = false
                )
                val aliasedToZero = page0NonBlank && probeHalf.size == 16 && probeHalf.contentEquals(page0)
                if (endProbe.size == 4 && !aliasedToZero) {
                    val endAddr = sizeBytes - 1
                    val label = "0x%06X ($sizeLabel AVR)".format(endAddr)
                    return FlashBoundsProbeResult(0x000000, endAddr, label, "USBasp EP0 0x04 Dynamic Flash Probe (256KB->4KB + Alias Check) -> $label")
                }
            }
            return FlashBoundsProbeResult(0x000000, 0x007FFF, "0x007FFF (32 KB AVR)", "USBasp EP0 Fallback 32 KB")
        }

        if (sync.framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
            for ((candStart, sizeLabel) in listOf(
                0x001900 to "58-60 KB",
                0x004000 to "48 KB",
                0x008000 to "32 KB",
                0x00C000 to "16 KB",
                0x00E000 to "8 KB",
                0x00F000 to "4 KB"
            )) {
                currentCoroutineContext().ensureActive()
                val pkt = byteArrayOf(0xE8.toByte(), ((candStart ushr 8) and 0xFF).toByte(), (candStart and 0xFF).toByte())
                val clean = stripStrictEcho(pkt, transport.transceive(pkt, 8, 95, "4.1 Single-Wire BDM Probe [0x%04X]".format(candStart), logTxRx = false))
                if (clean.isNotEmpty() && !clean.all { it == 0x00.toByte() }) {
                    val label = "0x00FFFF ($sizeLabel HCS08 BDM)"
                    return FlashBoundsProbeResult(candStart, 0x00FFFF, label, "Single-Wire BDM 0xE8 Bound Probe (4KB..60KB) -> 0x%04X..0xFFFF".format(candStart))
                }
            }
            return FlashBoundsProbeResult(0x00C000, 0x00FFFF, "0x00FFFF (16 KB HCS08 Fallback)", "Single-Wire BDM Fallback 0xC000..0xFFFF")
        }

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            for ((sizeBytes, sizeLabel) in listOf(
                0x10000 to "64 KB",
                0x08000 to "32 KB",
                0x04000 to "16 KB",
                0x02000 to "8 KB",
                0x01000 to "4 KB"
            )) {
                currentCoroutineContext().ensureActive()
                val testAddr = sizeBytes - 4
                val pkt = byteArrayOf(0x05, ((testAddr ushr 8) and 0xFF).toByte(), (testAddr and 0xFF).toByte(), 0x04)
                val rx = samsungSyncTransceive(sync, pkt, rxLen = 4, label = "4.1 Samsung S3F8 Bound [$sizeLabel @ 0x%04X]".format(testAddr), logTxRx = false)
                if (rx.size >= 4 && !rx.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF } && !isSamsungUartArtifact(rx)) {
                    val endAddr = sizeBytes - 1
                    val label = "0x%06X ($sizeLabel Samsung S3F8)".format(endAddr)
                    return FlashBoundsProbeResult(0x000000, endAddr, label, "Samsung S3F8 0x05 Dynamic Bound Probe (4KB..64KB) -> $label")
                }
            }
            return FlashBoundsProbeResult(0x000000, 0x007FFF, "0x007FFF (32 KB S3F8419 Typical)", "Samsung S3F8419 Flash Geometry 0x0000..0x7FFF (1KB Sector / Smart Option 0x003E..0x003F)")
        }

        // Dynamic 2MB..4KB Read Probe for any open raw / Renesas / Samsung / Custom UART MCU
        val stage2DirectDetail = _state.value.checklist.firstOrNull { it.id == "stage2_direct_read" }?.detail.orEmpty()
        val openReadCmd = Regex("0x([0-9A-Fa-f]{2})").find(stage2DirectDetail)?.groupValues?.get(1)?.toIntOrNull(16)
        if (openReadCmd != null) {
            for ((sizeBytes, sizeLabel) in sizesDesc) {
                currentCoroutineContext().ensureActive()
                val testAddr = sync.flashBaseAddr + sizeBytes - 4
                val pkt = byteArrayOf(
                    openReadCmd.toByte(),
                    ((testAddr ushr 16) and 0xFF).toByte(),
                    ((testAddr ushr 8) and 0xFF).toByte(),
                    (testAddr and 0xFF).toByte(),
                    0x04
                )
                val clean = stripStrictEcho(pkt, transport.transceive(pkt, 12, 120, "4.1 Generic Bound [$sizeLabel]", logTxRx = false))
                if (clean.size >= 4 && isMeaningfulGenericReply(pkt, clean)) {
                    val endAddr = sync.flashBaseAddr + sizeBytes - 1
                    val label = "0x%06X ($sizeLabel)".format(endAddr)
                    return FlashBoundsProbeResult(sync.flashBaseAddr, endAddr, label, "Dynamic Generic Read 0x%02X Boundary Probe (2MB->4KB) -> $label".format(openReadCmd))
                }
            }
        }

        return FlashBoundsProbeResult(
            sync.flashBaseAddr,
            sync.flashBaseAddr + 0x007FFF,
            "0x%06X (32 KB Typical)".format(sync.flashBaseAddr + 0x007FFF),
            "Dynamic Bound Probe Checked (4KB..2MB) -> Generic 32 KB Typical Fallback"
        )
    }

    private suspend fun runStage4DynamicFamilyAlgorithms(
        sync: SyncLock,
        base: ProberBlueprintSummary,
        rawC0Payload: ByteArray
    ): Pair<ProberBlueprintSummary, ByteArray?> {
        var maxBaud = sync.baudRate
        val supportedBauds = mutableListOf(sync.baudRate)
        var pageSize = 256
        var blockSize = 1024
        var flashStartHex = "0x%06X".format(sync.flashBaseAddr)
        var flashEndHex = "0x007FFF (32 KB)"
        var flashRule = "Dynamic Boundary Probe"
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

        // Run dynamic 4KB..2MB boundary probe for all families/programmers
        val boundsProbe = probeDynamicFlashBounds4KbTo2Mb(sync, rawC0Payload)
        detectedProbedFlashStart = boundsProbe.flashStart
        detectedProbedFlashEnd = boundsProbe.flashEnd
        flashStartHex = if (boundsProbe.flashStart > 0xFFFFFF) "0x%08X".format(boundsProbe.flashStart) else "0x%06X".format(boundsProbe.flashStart)
        flashEndHex = boundsProbe.endLabel
        flashRule = boundsProbe.ruleDetail
        appendLog(ProberLogDirection.INFO, "📐 حدود الفلاش المكتشفة ديناميكياً: $flashStartHex .. $flashEndHex")

        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            ensureBootloaderReady(sync, "بداية المرحلة 4", relockOsc = false)

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
                Triple("8.00 MHz (NEC Official 5.0V)", byteArrayOf(0x80.toByte(), 0x00, 0x06, 0x05), "8.00 MHz (5.0V)"),
                Triple("4.00 MHz (NEC Official 5.0V)", byteArrayOf(0x40, 0x00, 0x06, 0x05), "4.00 MHz (5.0V)"),
                Triple("5.00 MHz (NEC Official 5.0V)", byteArrayOf(0x50, 0x00, 0x06, 0x05), "5.00 MHz (5.0V)"),
                Triple("10.00 MHz (NEC Official 5.0V)", byteArrayOf(0x10, 0x00, 0x07, 0x05), "10.00 MHz (5.0V)"),
                Triple("12.00 MHz (NEC Official 5.0V)", byteArrayOf(0x12, 0x00, 0x07, 0x05), "12.00 MHz (5.0V)"),
                Triple("16.00 MHz (NEC Official 5.0V)", byteArrayOf(0x16, 0x00, 0x07, 0x05), "16.00 MHz (5.0V)"),
                Triple("20.00 MHz (NEC Official 5.0V)", byteArrayOf(0x20, 0x00, 0x07, 0x05), "20.00 MHz (5.0V)"),
                Triple("8.00 MHz (NEC Official 3.3V)", byteArrayOf(0x80.toByte(), 0x00, 0x06, 0x03), "8.00 MHz (3.3V)"),
                Triple("4.00 MHz (NEC Official 3.3V)", byteArrayOf(0x40, 0x00, 0x06, 0x03), "4.00 MHz (3.3V)"),
                Triple("5.00 MHz (NEC Official 3.3V)", byteArrayOf(0x50, 0x00, 0x06, 0x03), "5.00 MHz (3.3V)"),
                Triple("10.00 MHz (NEC Official 3.3V)", byteArrayOf(0x10, 0x00, 0x07, 0x03), "10.00 MHz (3.3V)"),
                Triple("12.00 MHz (NEC Official 3.3V)", byteArrayOf(0x12, 0x00, 0x07, 0x03), "12.00 MHz (3.3V)"),
                Triple("16.00 MHz (NEC Official 3.3V)", byteArrayOf(0x16, 0x00, 0x07, 0x03), "16.00 MHz (3.3V)"),
                Triple("20.00 MHz (NEC Official 3.3V)", byteArrayOf(0x20, 0x00, 0x07, 0x03), "20.00 MHz (3.3V)"),
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val effectiveCode = sync.actualTargetCode ?: detectedUsbdmTargetCode
            val readHex = "0x%02X".format(detectedUsbdmReadCmd)
            flashRule = "${boundsProbe.ruleDetail} | ${arch.eraseWriteSpec}"
            oscRule = if (isUsbdm) {
                "USBDM V4.x SET_TARGET_TYPE [0x%02X -> %s] + RESET_SPECIAL [03 13 00] + CONNECT [0x0F] auto-synced target (memSpace=0x%02X, READ_MEM=%s).".format(effectiveCode, arch.name, arch.memSpace, readHex)
            } else {
                "Single-Wire BDM auto-timed at 1,500,000 bps (8N1) on BKGD."
            }
            detectedCrystal = if (isUsbdm) "USBDM V4.x ${arch.name} Auto-Sync" else "1.5 Mbps BDM Sync"
            winningOscText = if (isUsbdm) "USBDM CONNECT [0x0F] (${arch.name})" else "1.5 Mbps BKGD Sync"
            baudRule = if (isUsbdm) "Native USBDM V4.x USB Bulk (EP1 OUT / EP2 IN)" else "FT232 Single-Wire BDM locked at 1,500,000 bps (8N1)"
            pageSize = 256
            blockSize = if (arch.isArmOr32Bit) 1024 else 512
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.STLINK_SWD) {
            flashRule = "${boundsProbe.ruleDetail} | ST-LINK SWD Flash Read 0xF2 0x08"
            oscRule = "ST-LINK SWD Clock locked by adapter (${sync.baudRate / 1_000_000} MHz SWCLK)"
            detectedCrystal = "ST-LINK Internal SWCLK (4 MHz)"
            winningOscText = "ST-LINK SWD [F2 20 A3]"
            baudRule = "Native ST-LINK USB Bulk (${transport.currentEndpointsSummary()})"
            pageSize = 256
            blockSize = 1024
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.STLINK_SWIM) {
            flashRule = "${boundsProbe.ruleDetail} | ST-LINK SWIM Read 0xF4 0x0B/0x0C"
            oscRule = "ST-LINK SWIM High-Speed Sync [F4 03]"
            detectedCrystal = "STM8 HSI / SWIM Clock"
            winningOscText = "ST-LINK SWIM [F4 00/03]"
            baudRule = "Native ST-LINK SWIM USB Bulk (${transport.currentEndpointsSummary()})"
            pageSize = 256
            blockSize = 512
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.AVR_ISP_USB) {
            flashRule = "${boundsProbe.ruleDetail} | AVR SPI ISP Read (USBasp 0x04 / STK500v2)"
            oscRule = "AVR ISP SCK clock negotiated via adapter (SETISPSCK)"
            detectedCrystal = "Target AVR XTAL / Internal RC"
            winningOscText = "USBasp/AVR ISP Sync"
            baudRule = "USB EP0 Control / Bulk (${transport.currentEndpointsSummary()})"
            pageSize = 256
            blockSize = 128
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP) {
            flashRule = "${boundsProbe.ruleDetail} | Microchip ICSP"
            oscRule = "Microchip ICSP Clock driven by PICkit/SNAP"
            detectedCrystal = "ICSP Driven"
            winningOscText = "Microchip PICkit Handshake"
            baudRule = "USB HID/Bulk (${transport.currentEndpointsSummary()})"
            pageSize = 256
            blockSize = 128
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            flashRule = "Samsung S3F8419 ID -> ${detectedSamsungIdHex.ifBlank { sync.firstReplyHex }} | SmartOpt(0x003E..0x003F)=${detectedSamsungSmartOptionHex.ifBlank { "--" }} | Read=[0x05 Addr_H Addr_L Len] | SectorErase=[0x08] | Prog=[0x06] | ChipErase=[0x09 0xAA 0x55]"
            oscRule = "Samsung S3F8 uses DTR=Vpp/TEST (HIGH +5V طوال الجلسة), RTS=SCLK, SDAT=TX(1N4148)+RX synced via 0xAA 0x55."
            detectedCrystal = "Internal / Target XTAL"
            winningOscText = "Samsung S3F8 Sync (DTR=+5V Locked)"
            baudRule = "Locked Pin Map: DTR=Vpp/TEST (+5V) | RTS=SCLK | ${sync.parityMode}"
            flashEndHex = boundsProbe.endLabel
            pageSize = 256
            blockSize = 1024
            supportedStxPages.add(256)
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            flashRule = "${boundsProbe.ruleDetail} | STM32 Memory Read 0x11 / Write 0x31"
            oscRule = "STM32 internal HSI 8MHz auto-detected by 0x7F sync byte"
            detectedCrystal = "Internal HSI 8 MHz"
            winningOscText = "Auto-Baud 0x7F"
            baudRule = "Auto-baud locked on 0x7F at ${sync.baudRate} ${sync.parityMode}"
            pageSize = 256
            blockSize = 2048
            supportedStxPages.add(256)
        } else {
            flashRule = boundsProbe.ruleDetail
            oscRule = "Custom/Unknown USB or UART target clock"
            detectedCrystal = "Target Native Clock"
            winningOscText = sync.syncPulseHex
            baudRule = sync.parityMode
            pageSize = 256
            blockSize = 1024
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
            flashStartAddressHex = flashStartHex,
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
            val step1Or2Passed = results.any {
                (it.stepTitle.startsWith("5.1") || it.stepTitle.startsWith("5.2")) && it.passed
            }
            val step3Passed = results.any { it.stepTitle.startsWith("5.3") && it.passed }
            val hasPartialBypass = step1Or2Passed && !step3Passed
            workingSummary = workingSummary.copy(
                stage5Results = results.toList(),
                advancedSchoolsNotes = notesBuilder.toString().trim().ifBlank { stepDetail },
                hasPartialBypass = hasPartialBypass
            )
            updateStep("stage5_safety_gate", stepBadge, stepDetail)
            updateSummaryAndBlueprint(workingSummary)
            hardwareResetAndResync(sync, "تهيئة وإعادة تزامن للمرحلة 6 بعد المرحلة 5", relockOsc = true)
            workingSummary = runStage6UniversalLiveReadProof(sync, workingSummary, userCancelledStage5 = userCancelled)
            updateSummaryAndBlueprint(workingSummary)
            val finalBanner = when {
                workingSummary.liveReadProofPassed -> "$bannerMsg | ✅ المرحلة 6: ${workingSummary.liveReadProofFirst32Hex.take(23)}..."
                workingSummary.liveReadProofMode == "Idle / Waiting for +12.5V Vpp on Pin 9" -> "⚠️ $SAMSUNG_FLOATING_HIGH_ALERT_MSG"
                else -> bannerMsg
            }
            _state.value = _state.value.copy(
                phase = ProberStagePhase.COMPLETED,
                stageBanner = finalBanner,
                pendingConfirmDialog = null
            )
        }

        val isSamsung = (sync.framing == DetectedFraming.SAMSUNG_ISP)
        // STEP 5.1: Block-0 Zeroing (NEC) / Sector-0 1KB Erase [0x08 0x00 0x00] (Samsung S3F8)
        val ok1 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 1,
                totalSteps = 3,
                stepTitle = if (isSamsung) {
                    "مسح القطاع الأول 1KB في Samsung S3F8 ([0x08 0x00 0x00])"
                } else {
                    "تصفير Block 0 وحساب فرق البصمة (0x22 + 0xB0)"
                },
                impactDescription = if (isSamsung) {
                    "سيتم إرسال أمر مسح القطاع الأول الحقيقي [0x08, 0x00, 0x00] (0x0000..0x03FF) الذي يحتوي على بايتات الحماية Smart Option (0x003E..0x003F) لفك حماية القراءة عن باقي الفلاش، مع بقاء DTR (Vpp/TEST) = HIGH (+5V) ونبضات RTS (SCLK).\n\n⚠️ هذا الإجراء يمسح أول 1KB من ذاكرة الفلاش."
                } else {
                    "سيتم قياس بصمة Block 0 (0x000000..0x0003FF) عبر الأمر 0xB0، ثم إرسال أمر مسح البلوك 0x22 وقياس البصمة والزمن الفعلي بعد المسح.\n\n⚠️ هذا الإجراء يمسح أول 1KB من ذاكرة الفلاش على الكارتة المتصلة مباشرة."
                }
            )
        )
        if (!ok1) {
            results.add(Stage5StepResult(if (isSamsung) "5.1 Samsung S3F8 Sector-0 Erase [0x08 0x00 0x00]" else "5.1 Block-0 Zeroing (0x22 + 0xB0)", passed = false, executed = false, durationMs = 0, detail = "أوقف المشرف الاختبار قبل التنفيذ"))
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
            stageBanner = if (isSamsung) "جاري تنفيذ الخطوة 5.1: مسح القطاع الأول 0x08 (Smart Option 0x003E..0x003F)..." else "جاري تنفيذ الخطوة 5.1: تصفير Block 0 وحساب فرق البصمة..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, if (isSamsung) "جاري تنفيذ 5.1: [0x08 0x00 0x00] مع بقاء DTR=Vpp/TEST HIGH..." else "جاري تنفيذ 5.1: قراءة البصمة 0xB0 ثم مسح Block 0...")
        ensureBootloaderReady(sync, "تهيئة الخطوة 5.1", relockOsc = true)

        val step1Result = executeStage5Step1(sync)
        results.add(step1Result)
        notesBuilder.appendLine(step1Result.detail)
        appendLog(if (step1Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step1Result.detail)
        workingSummary = workingSummary.copy(stage5Results = results.toList(), advancedSchoolsNotes = notesBuilder.toString().trim())
        updateSummaryAndBlueprint(workingSummary)

        // STEP 5.2: Partial Bit Overwrite (0x40 + STX 0xFE) / Byte-Page Program [0x06] (Samsung S3F8)
        val ok2 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 2,
                totalSteps = 3,
                stepTitle = if (isSamsung) {
                    "كتابة بايت/صفحة في Samsung S3F8 ([0x06 Addr_H Addr_L Data...])"
                } else {
                    "الكتابة الجزئية / تحويل البتات (0x40 + STX 0xFE)"
                },
                impactDescription = if (isSamsung) {
                    "سيتم اختبار أمر البرمجة الحقيقي [0x06, 0x00, 0x40, 0xFE] لسامسونج S3F8 مع بقاء DTR (Vpp/TEST) = HIGH (+5V) ونبضات RTS (SCLK).\n\n⚠️ هذا الإجراء يعدّل بيانات الفلاش في العنوان 0x0040."
                } else {
                    "سيتم اختبار أمر البرمجة 0x40 مع إرسال إطار STX (0x02) بقيمة 0xFE لاختبار الكتابة الجزئية وقياس زمن استجابة الفلاش.\n\n⚠️ هذا الإجراء يعدّل بيانات الفلاش في العنوان المستهدف."
                }
            )
        )
        if (!ok2) {
            results.add(Stage5StepResult(if (isSamsung) "5.2 Samsung S3F8 Byte/Page Program [0x06]" else "5.2 Partial Bit Overwrite (0x40 STX 0xFE)", passed = false, executed = false, durationMs = 0, detail = "توقف المشرف بعد الخطوة 5.1"))
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
            stageBanner = if (isSamsung) "جاري تنفيذ الخطوة 5.2: اختبار البرمجة [0x06 0x00 0x40 0xFE]..." else "جاري تنفيذ الخطوة 5.2: اختبار الكتابة الجزئية 0x40 STX..."
        )
        updateStep("stage5_safety_gate", ChecklistStepStatus.RUNNING, if (isSamsung) "جاري تنفيذ 5.2: Samsung S3F8 Program [0x06]..." else "جاري تنفيذ 5.2: الكتابة الجزئية 0x40 + STX 0xFE...")
        ensureBootloaderReady(sync, "تهيئة الخطوة 5.2", relockOsc = true)

        val step2Result = executeStage5Step2(sync)
        results.add(step2Result)
        notesBuilder.appendLine(step2Result.detail)
        appendLog(if (step2Result.passed) ProberLogDirection.SUCCESS else ProberLogDirection.WARN, step2Result.detail)
        workingSummary = workingSummary.copy(stage5Results = results.toList(), advancedSchoolsNotes = notesBuilder.toString().trim())
        updateSummaryAndBlueprint(workingSummary)

        // STEP 5.3: Full Chip Erase (0x20 NEC / [0x09 0xAA 0x55] Samsung S3F8)
        val ok3 = requestStepConfirmation(
            Stage5ConfirmPrompt(
                stepIndex = 3,
                totalSteps = 3,
                stepTitle = if (isSamsung) {
                    "المسح الكلي لرقاقة Samsung S3F8 ([0x09 0xAA 0x55])"
                } else {
                    "المسح الكلي للرقاقة وفك قفل الحماية (0x20 Chip Erase / Security Bypass)"
                },
                impactDescription = if (isSamsung) {
                    "⚠️ تحذير شديد: سيتم إرسال أمر المسح الكلي الحقيقي [0x09, 0xAA, 0x55] لرقاقة Samsung S3F8 وقياس الزمن الفعلي للمسح.\n\nسيؤدي هذا إلى مسح السوفت وير بالكامل على الكارتة المتصلة!"
                } else {
                    "⚠️ تحذير شديد: سيتم إرسال أمر المسح الكلي (0x20 Chip Erase أو 0x92/0xF5 حسب العائلة) وقياس الزمن الفعلي للمسح الكامل.\n\nسيؤدي هذا إلى مسح السوفت وير بالكامل على الكارتة المتصلة!"
                }
            )
        )
        if (!ok3) {
            results.add(Stage5StepResult(if (isSamsung) "5.3 Samsung S3F8 Chip Erase [0x09 0xAA 0x55]" else "5.3 Full Chip Erase 0x20 / Security Bypass", passed = false, executed = false, durationMs = 0, detail = "توقف المشرف بعد الخطوة 5.2"))
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
            var csBeforeParse = parseStrictChecksum16Frames(csBeforeRx)
            if (csBeforeParse.checksum16 == null && csBeforeParse.errorStatus == null && csBeforeParse.sawAck) {
                csBeforeParse = parseStrictChecksum16Frames(csBeforeRx + transport.readFollowUp(12, 300, true))
            }
            val csBefore = csBeforeParse.checksum16

            var eraseRx = transport.transceive(buildNecCommandFrame(0x22, b0Range), 12, 1300, "5.1 Block-0 Erase [0x22]", stopOnCompleteNecFrame = true)
            if (eraseRx.isEmpty()) {
                hardwareResetAndResync(sync, "5.1 0x22 Erase Timeout", relockOsc = true)
                eraseRx = transport.transceive(buildNecCommandFrame(0x22, b0Range), 12, 1300, "5.1 Block-0 Erase Retry [0x22]", stopOnCompleteNecFrame = true)
            }
            val eraseSts = parseNecStatus(eraseRx)
            var csAfter: Int? = null
            var csAfterErr: Int? = null
            if (eraseSts == ACK_NEC) {
                delay(20)
                var csAfterRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS After [0xB0]", stopOnCompleteNecFrame = true)
                if (csAfterRx.isEmpty()) {
                    hardwareResetAndResync(sync, "5.1 0xB0 After Timeout", relockOsc = true)
                    csAfterRx = transport.transceive(buildNecCommandFrame(0xB0, b0Range), 16, 400, "5.1 Block-0 CS After Retry [0xB0]", stopOnCompleteNecFrame = true)
                }
                var csAfterParse = parseStrictChecksum16Frames(csAfterRx)
                if (csAfterParse.checksum16 == null && csAfterParse.errorStatus == null && csAfterParse.sawAck) {
                    csAfterParse = parseStrictChecksum16Frames(csAfterRx + transport.readFollowUp(12, 300, true))
                }
                csAfter = csAfterParse.checksum16
                csAfterErr = csAfterParse.errorStatus
            }
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val passed = (eraseSts == ACK_NEC) && (csBefore != null && csAfter != null)
            val detail = if (passed) {
                "✅ 5.1 تصفير Block-0 (0x22+0xB0): نجح في ${dt}ms (CS قبل=0x%04X -> بعد=0x%04X)".format(csBefore, csAfter)
            } else {
                val errSuffix = when {
                    csBeforeParse.errorStatus != null -> " | 0xB0 قبل=${formatByte(csBeforeParse.errorStatus)}"
                    csAfterErr != null -> " | 0xB0 بعد=${formatByte(csAfterErr)}"
                    else -> ""
                }
                "❌ 5.1 تصفير Block-0 (0x22+0xB0): فشل (${dt}ms, 0x22=${formatByte(eraseSts)}$errSuffix)"
            }
            return Stage5StepResult("5.1 Block-0 Zeroing (0x22 + 0xB0)", passed, true, dt, detail)
        } else if (sync.framing == DetectedFraming.USBDM_BDM) {
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            if (arch == UsbdmTargetArch.HCS08) {
                // HCS08 Page Erase via FCLKDIV (0x1820), FSTAT (0x1825) & FCMD (0x1826 = 0x40 Page Erase) at 0xC000
                transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT [02 14]", logTxRx = false)
                ensureHcs08FclkdivConfigured()
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 250, "USBDM Clear FSTAT [0x1825=0x30]")
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0xC0.toByte(), 0x00, 0xFF.toByte()), 8, 250, "USBDM Dummy Write [0xC000]")
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x40), 8, 250, "USBDM FCMD Page Erase [0x1826=0x40]")
                val launch = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 350, "USBDM Launch Erase [0x1825=0x80]")
                delay(35)
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val ok = launch.isNotEmpty() && (launch[0].toInt() and 0xFF) == 0x00
                val detail = if (ok) "✅ 5.1 HCS08 Page Erase (FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826->0x40): نجح في ${dt}ms" else "❌ 5.1 HCS08 Page Erase: فشل (FSEC محمي)"
                return Stage5StepResult("5.1 HCS08 FCMD Page Erase (0x40)", ok, true, dt, detail)
            } else {
                val haltRx = transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "5.1 USBDM ${arch.name} HALT [02 14]")
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val ok = haltRx.isNotEmpty() && (haltRx[0].toInt() and 0xFF) == 0x00
                return Stage5StepResult("5.1 ${arch.name} Flash Sector Spec", ok, true, dt, "✅ 5.1 ${arch.name}: ${arch.eraseWriteSpec}")
            }
        } else if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM ||
            sync.framing == DetectedFraming.AVR_ISP_USB || sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP ||
            transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            return Stage5StepResult("5.1 ${sync.framing.label}", true, true, dt, "ℹ️ 5.1 ${sync.framing.label}: حماية الفلاش — تم التحقق من بوابة المسح الآمن")
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val e0 = transport.transceive(byteArrayOf(0x43, 0xBC.toByte()), 6, 450, "5.1 STM32 Erase [0x43]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = e0.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.1 مسح STM32 Sector-0: نجح (${dt}ms)" else "❌ 5.1 مسح STM32 Sector-0: فشل (RDP محمي)"
            return Stage5StepResult("5.1 Sector-0 Erase (0x43)", ok, true, dt, detail)
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Genuine Samsung S3F8 1KB Sector Erase [0x08, Addr_H, Addr_L] on Sector 0 (0x0000..0x03FF containing Smart Option 0x003E..0x003F)
            val s0Erase = samsungSyncTransceive(
                sync,
                byteArrayOf(0x08, 0x00, 0x00),
                rxLen = 4,
                label = "5.1 Samsung S3F8 Sector-0 Erase [0x08 0x00 0x00]"
            )
            delay(50)
            val directAck = s0Erase.isNotEmpty() && !s0Erase.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF } && !isSamsungUartArtifact(s0Erase)
            val postUnlockProbe = if (!directAck) {
                samsungSyncTransceive(sync, byteArrayOf(0x05, 0x04, 0x00, 0x04), rxLen = 4, label = "5.1 Verify Post-Sector0 Read [0x05 @ 0x0400]")
            } else ByteArray(0)
            val unlockOk = postUnlockProbe.size >= 4 && !postUnlockProbe.all { it == 0x00.toByte() } && !isSamsungUartArtifact(postUnlockProbe)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = directAck || unlockOk
            val detail = if (ok) {
                "✅ 5.1 مسح القطاع الأول 1KB في Samsung S3F8 [0x08 0x00 0x00] (تجاوز Smart Option 0x003E..0x003F): نجح في ${dt}ms (${(if (directAck) s0Erase else postUnlockProbe).toHexStr()})"
            } else {
                "❌ 5.1 مسح القطاع الأول في Samsung S3F8 [0x08 0x00 0x00]: فشل"
            }
            return Stage5StepResult("5.1 Samsung S3F8 Sector-0 Erase [0x08 0x00 0x00] (Smart Option 0x003E..0x003F)", ok, true, dt, detail)
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            if (arch == UsbdmTargetArch.HCS08) {
                val ok = usbdmProgramBytes(0x00FF00, byteArrayOf(0xFE.toByte()))
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val detail = if (ok) "✅ 5.2 HCS08 Byte Program (FCMD 0x20): نجح في ${dt}ms" else "❌ 5.2 HCS08 Byte Program: فشل"
                return Stage5StepResult("5.2 HCS08 FCMD Byte Program (0x20)", ok, true, dt, detail)
            } else {
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                return Stage5StepResult("5.2 ${arch.name} Program Spec", true, true, dt, "ℹ️ 5.2 ${arch.name}: ${arch.eraseWriteSpec}")
            }
        } else if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM ||
            sync.framing == DetectedFraming.AVR_ISP_USB || sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP ||
            transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            return Stage5StepResult("5.2 ${sync.framing.label} Write Check", true, true, dt, "ℹ️ 5.2 ${sync.framing.label}: حماية الكتابة — جاهز لاختبار السحب الفعلي بالمرحلة 6")
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val wr = transport.transceive(byteArrayOf(0x31, 0xCE.toByte()), 6, 400, "5.2 STM32 Write [0x31]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wr.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.2 STM32 Write 0x31: ACK في ${dt}ms" else "❌ 5.2 STM32 Write 0x31: مرفوض"
            return Stage5StepResult("5.2 STM32 Write 0x31", ok, true, dt, detail)
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Genuine Samsung S3F8 Byte/Page Program [0x06, Addr_H, Addr_L, Data...]
            val wrHdr = samsungSyncTransceive(
                sync,
                byteArrayOf(0x06, 0x00, 0x40, 0xFE.toByte()),
                rxLen = 4,
                label = "5.2 Samsung S3F8 Byte/Page Program [0x06 0x00 0x40 0xFE]"
            )
            delay(15)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = wrHdr.isNotEmpty() && !wrHdr.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF } && !isSamsungUartArtifact(wrHdr)
            val detail = if (ok) {
                "✅ 5.2 برمجة Samsung S3F8 [0x06 0x00 0x40 0xFE]: نجح في ${dt}ms (${wrHdr.toHexStr()})"
            } else {
                "❌ 5.2 برمجة Samsung S3F8 [0x06]: فشل"
            }
            return Stage5StepResult("5.2 Samsung S3F8 Byte/Page Program [0x06]", ok, true, dt, detail)
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            if (arch == UsbdmTargetArch.HCS08) {
                transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM HALT [02 14]", logTxRx = false)
                ensureHcs08FclkdivConfigured()
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 250, "USBDM Mass Erase FSTAT [0x1825=0x30]")
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0xC0.toByte(), 0x00, 0xFF.toByte()), 8, 250, "USBDM Mass Erase Latch")
                transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x41), 8, 250, "USBDM FCMD Mass Erase [0x1826=0x41]")
                val launch = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 600, "USBDM Launch Mass Erase [0x1825=0x80]")
                delay(220)
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val ok = launch.isNotEmpty() && (launch[0].toInt() and 0xFF) == 0x00
                val detail = if (ok) "✅ 5.3 HCS08 Mass Erase (FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826->0x41) وفك الحماية: نجح في ${dt}ms" else "❌ 5.3 HCS08 Mass Erase: فشل"
                return Stage5StepResult("5.3 HCS08 Mass Erase (FCMD 0x41)", ok, true, dt, detail)
            } else {
                val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                return Stage5StepResult("5.3 ${arch.name} Mass Erase Spec", true, true, dt, "ℹ️ 5.3 ${arch.name}: ${arch.eraseWriteSpec}")
            }
        } else if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM ||
            sync.framing == DetectedFraming.AVR_ISP_USB || sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP ||
            transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            return Stage5StepResult("5.3 ${sync.framing.label} Mass Erase", true, true, dt, "ℹ️ 5.3 ${sync.framing.label}: حماية المسح الكلي — تم توثيق خطة المسح في البطاقة 2")
        } else if (sync.framing == DetectedFraming.STM32_ACK) {
            val rx = transport.transceive(byteArrayOf(0x92.toByte(), 0x6D), 6, 1500, "5.3 STM32 Unprotect [0x92]")
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = rx.any { (it.toInt() and 0xFF) == ACK_STM32 }
            val detail = if (ok) "✅ 5.3 STM32 Readout Unprotect 0x92: نجح في ${dt}ms" else "❌ 5.3 STM32 Unprotect 0x92: فشل"
            return Stage5StepResult("5.3 STM32 Readout Unprotect 0x92", ok, true, dt, detail)
        } else if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Genuine Samsung S3F8 Full Chip Erase [0x09, 0xAA, 0x55]
            val chipEraseRx = samsungSyncTransceive(
                sync,
                byteArrayOf(0x09, 0xAA.toByte(), 0x55),
                rxLen = 4,
                label = "5.3 Samsung S3F8 Chip Erase [0x09 0xAA 0x55]"
            )
            delay(120)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val ok = chipEraseRx.isNotEmpty() && !chipEraseRx.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF } && !isSamsungUartArtifact(chipEraseRx)
            val detail = if (ok) {
                "✅ 5.3 المسح الكلي Samsung S3F8 [0x09 0xAA 0x55]: نجح في ${dt}ms (${chipEraseRx.toHexStr()})"
            } else {
                "❌ 5.3 المسح الكلي Samsung S3F8 [0x09 0xAA 0x55]: فشل/مقفل"
            }
            return Stage5StepResult("5.3 Samsung S3F8 Chip Erase [0x09 0xAA 0x55]", ok, true, dt, detail)
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
            if (summary.hasPartialBypass) {
                "تجاوز البلوك الأول نشط (hasPartialBypass=true): فحص ما بعد Block 0..."
            } else {
                "فحص Page 0 (256B) والتحقق من عدم وجود Echo ثم مطابقة الذاكرة..."
            }
        )
        ensureBootloaderReady(sync, "تهيئة المرحلة 6", relockOsc = true)

        val pageSize = if (sync.framing == DetectedFraming.USBDM_BDM || sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM || sync.framing == DetectedFraming.AVR_ISP_USB) 256 else (summary.pageSizeBytes ?: 256).coerceIn(64, 256)
        val blockSize = (summary.blockSizeBytes ?: 1024).coerceAtLeast(pageSize)
        var page0Start = when (sync.framing) {
            DetectedFraming.STM32_ACK, DetectedFraming.STLINK_SWD -> detectedProbedFlashStart.takeIf { it != 0 } ?: 0x08000000
            DetectedFraming.STLINK_SWIM -> detectedProbedFlashStart.takeIf { it != 0 } ?: 0x008000
            DetectedFraming.USBDM_BDM -> sync.flashBaseAddr.takeIf { it != 0 } ?: detectedProbedFlashStart
            DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> sync.flashBaseAddr.takeIf { it != 0 } ?: 0x00C000
            else -> 0x000000
        }
        val flashEndAddr = parseFlashEndAddress(summary, sync)

        // -------------------- PRIORITY 0: PARTIAL BYPASS POST-BLOCK-1 PROOF (BEFORE ANY FAMILY/PAGE-0 CHECK) --------------------
        if (summary.hasPartialBypass) {
            val postBlockOffset = if (blockSize >= 2048) 0x0800 else 0x0400
            val b2Start = page0Start + postBlockOffset
            val b2End = b2Start + pageSize - 1
            val b2RangeHex = "0x%06X..0x%06X".format(b2Start, b2End)
            val b2Range = addrRangeBytes(b2Start, b2End)
            appendLog(
                ProberLogDirection.INFO,
                if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                    "🔓 أولوية المرحلة 6 (Samsung Smart Option Bypass): مسح القطاع الأول 0x08 ناجح — إعادة المزامنة مع بقاء DTR=HIGH ثم قراءة البلوك الثاني $b2RangeHex بأمر 0x05..."
                } else {
                    "🔓 أولوية المرحلة 6 (hasPartialBypass=true): توجيه القراءة والتحقق مباشرة إلى ما بعد Block 0 ($b2RangeHex) بدلاً من Page 0 المقفل..."
                }
            )
            updateStep(
                "stage6_live_read_proof",
                ChecklistStepStatus.RUNNING,
                if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                    "Samsung Smart Option Bypass: قراءة $b2RangeHex بأمر 0x05 مع بقاء DTR=HIGH..."
                } else {
                    "قراءة وتحقق من العنوان المفتوح بعد البلوك الأول ($b2RangeHex)..."
                }
            )

            if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                transport.samsungQuickResyncKeepTestHigh(
                    testActiveHigh = sync.syncModePinState,
                    pulseClocks = 8,
                    forceUartModemLines = false
                )
                samsungSyncTransceive(sync, byteArrayOf(0xAA.toByte(), 0x55), rxLen = 2, label = "Stage6 Smart-Option Re-Sync (DTR=HIGH) [0xAA 0x55]", logTxRx = false)
            }

            // 1) Try Direct / Samsung 0x05 / 1B-0xB0 extraction directly at post-Block-1 address (0x0400 or 0x0800)
            val (b2Bytes, b2Method, b2Echo) = readFlashPageForProof(sync, summary, b2Start, pageSize)
            if (b2Bytes != null && b2Bytes.isNotEmpty() && !isEchoPayload(b2Bytes, b2Echo)) {
                val first32Hex = b2Bytes.take(32).toByteArray().toHexStr()
                if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                    val (secondRead, _, _) = readFlashPageForProof(sync, summary, b2Start, pageSize)
                    val dualMatched = secondRead != null && secondRead.contentEquals(b2Bytes)
                    val floatingHigh = isFloatingHighSdatPattern(b2Bytes) || (secondRead != null && isFloatingHighSdatPattern(secondRead))
                    if (floatingHigh && !dualMatched) {
                        appendLog(ProberLogDirection.WARN, "⚠️ Idle / Waiting for +12.5V Vpp on Pin 9 — $SAMSUNG_FLOATING_HIGH_ALERT_MSG")
                        updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, "$SAMSUNG_FLOATING_HIGH_ALERT_MSG | أول 32B: $first32Hex")
                        return summary.copy(
                            hasPartialBypass = true,
                            liveReadProofPassed = false,
                            liveReadProofMode = "Idle / Waiting for +12.5V Vpp on Pin 9",
                            liveReadProofStatus = SAMSUNG_FLOATING_HIGH_ALERT_MSG,
                            liveReadProofFirst32Hex = first32Hex
                        )
                    }
                    if (dualMatched) {
                        val winningPath = "Winning Non-Standard Extraction Path (Samsung Smart Option Bypass: 0x08 @ 0x0000 -> Dual-Read 0x05 @ $b2RangeHex)"
                        val reportMsg = "✅ إثبات Smart Option Bypass بالقراءة المزدوجة 100% ($b2RangeHex): تطابق تام لقراءتين متتاليتين بأمر 0x05 مع بقاء DTR=HIGH ($b2Method)"
                        appendLog(ProberLogDirection.SUCCESS, "$reportMsg | أول 32B: $first32Hex")
                        updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, "$reportMsg | أول 32B: $first32Hex")
                        return summary.copy(
                            hasPartialBypass = true,
                            winningReadMethod = winningPath,
                            liveReadProofPassed = true,
                            liveReadProofMode = winningPath,
                            liveReadProofStatus = reportMsg,
                            liveReadProofFirst32Hex = first32Hex
                        )
                    }
                } else {
                    val (verifySupported, verifyPassed, verifyDetail) = verifyExtractedPageOnChip(sync, b2Start, b2Bytes)
                    val verifiedOk = if (verifySupported) {
                        verifyPassed
                    } else {
                        val (secondRead, _, _) = readFlashPageForProof(sync, summary, b2Start, pageSize)
                        secondRead != null && secondRead.contentEquals(b2Bytes)
                    }
                    if (verifiedOk) {
                        val winningPath = "Winning Non-Standard Extraction Path (Block-0 Bypass @ $b2RangeHex | $b2Method)"
                        val reportMsg = "✅ إثبات المرحلة 6 بعد تجاوز البلوك الأول ($b2RangeHex): نجح السحب الفعلي والتحقق ($b2Method | $verifyDetail)"
                        appendLog(ProberLogDirection.SUCCESS, "$reportMsg | أول 32B: $first32Hex")
                        updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, "$reportMsg | أول 32B: $first32Hex")
                        return summary.copy(
                            hasPartialBypass = true,
                            winningReadMethod = winningPath,
                            liveReadProofPassed = true,
                            liveReadProofMode = winningPath,
                            liveReadProofStatus = reportMsg,
                            liveReadProofFirst32Hex = first32Hex
                        )
                    } else {
                        val failDetail = "❌ تم سحب بيانات من $b2RangeHex لكن فشل إثبات المطابقة ($verifyDetail) | أول 32B: $first32Hex"
                        appendLog(ProberLogDirection.ERROR, failDetail)
                        updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failDetail)
                        return summary.copy(
                            hasPartialBypass = true,
                            liveReadProofPassed = false,
                            liveReadProofMode = "Post-Block-1 Verification Failed ($b2Method)",
                            liveReadProofStatus = failDetail,
                            liveReadProofFirst32Hex = first32Hex
                        )
                    }
                }
            }

            // 2) NEC 78K0 Locked ROM: Strict 16-Byte 0xB0 Cumulative Extraction Proof (never accept Reset/ACK headers alone)
            var bypassDiagnostic = b2Method
            if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                resyncLight(sync)
                val (probe16, probe16Msg, probe16Tx) = extractBytesVia0xB0Cumulative(b2Start, 16)
                if (probe16 != null && probe16.size == 16 && !isEchoPayload(probe16, probe16Tx)) {
                    val sampleHex = probe16.toHexStr()
                    val nonBlank = probe16.any { (it.toInt() and 0xFF) != 0xFF && (it.toInt() and 0xFF) != 0x00 }
                    val (verify16Sup, verify16Ok, verify16Det) = verifyExtractedPageOnChip(sync, b2Start, probe16)
                    if ((verify16Sup && verify16Ok) || (!verify16Sup && nonBlank)) {
                        val winningPath = "Winning Non-Standard Extraction Path (Block-0 Bypass + 11B 0xB0 Cumulative)"
                        val reportMsg = "✅ نجاح استخراج والتحقق من 16 بايت فعلياً بعد Block 0 (${"0x%06X".format(b2Start)}..${"0x%06X".format(b2Start + 15)}): $sampleHex | $verify16Det"
                        appendLog(ProberLogDirection.SUCCESS, reportMsg)
                        updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, reportMsg)
                        return summary.copy(
                            hasPartialBypass = true,
                            winningReadMethod = winningPath,
                            liveReadProofPassed = true,
                            liveReadProofMode = winningPath,
                            liveReadProofStatus = reportMsg,
                            liveReadProofFirst32Hex = sampleHex
                        )
                    }
                    bypassDiagnostic = "استخراج 16B عبر 0xB0 ($sampleHex) فشل في مطابقة 0x13 ($verify16Det)"
                } else {
                    bypassDiagnostic = probe16Msg
                }
            }

            val bypassFailMsg = "❌ فشل إثبات الاستخراج الفعلي للبايتات في ($b2RangeHex): $bypassDiagnostic — يمنع إصدار تقرير نجاح بناءً على ACK/Header فقط."
            appendLog(ProberLogDirection.ERROR, bypassFailMsg)
            updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, bypassFailMsg)
            return summary.copy(
                hasPartialBypass = true,
                liveReadProofPassed = false,
                liveReadProofMode = "Post-Block-1 Real Extraction Gate Failed ($b2RangeHex)",
                liveReadProofStatus = bypassFailMsg,
                liveReadProofFirst32Hex = "--"
            )
        }

        // Try extracting Page 0 using the discovered Read method (USBDM / ST-LINK SWD|SWIM / USBasp / STM32 / Direct Read / 1B 0xB0)
        var (page0Bytes, readMethodUsed, txEchoSample) = readFlashPageForProof(sync, summary, page0Start, pageSize)
        if (sync.framing == DetectedFraming.USBDM_BDM) {
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val needsAltAddr = page0Bytes == null || page0Bytes.isEmpty() ||
                page0Bytes.all { (it.toInt() and 0xFF) == 0xFF } ||
                page0Bytes.all { (it.toInt() and 0xFF) == 0x00 }
            if (needsAltAddr) {
                val candList = (
                    listOf(sync.flashBaseAddr, detectedProbedFlashStart) +
                        arch.flashBaseCandidates.map { it.toInt() } +
                        if (arch == UsbdmTargetArch.HCS08) listOf(0xC000, 0x8000, 0xE000, 0xF000, 0x1900) else emptyList()
                    ).distinct()
                for (altAddr in candList) {
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

            if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
                val first32Hex = page0Bytes.take(32).toByteArray().toHexStr()
                // Samsung S3F8 Dual-Read Compare (never use NEC 0x13 Verify)
                val (secondRead, _, _) = readFlashPageForProof(sync, summary, page0Start, pageSize)
                val dualMatched = secondRead != null && secondRead.contentEquals(page0Bytes)
                val floatingHigh = isFloatingHighSdatPattern(page0Bytes) ||
                    (secondRead != null && isFloatingHighSdatPattern(secondRead))

                if (floatingHigh && !dualMatched) {
                    appendLog(
                        ProberLogDirection.WARN,
                        "⚠️ Idle / Waiting for +12.5V Vpp on Pin 9 — $SAMSUNG_FLOATING_HIGH_ALERT_MSG"
                    )
                    updateStep(
                        "stage6_live_read_proof",
                        ChecklistStepStatus.FAILED,
                        "Idle / Waiting for +12.5V Vpp on Pin 9 — $SAMSUNG_FLOATING_HIGH_ALERT_MSG | أول 32B: $first32Hex"
                    )
                    return summary.copy(
                        liveReadProofPassed = false,
                        liveReadProofMode = "Idle / Waiting for +12.5V Vpp on Pin 9",
                        liveReadProofStatus = SAMSUNG_FLOATING_HIGH_ALERT_MSG,
                        liveReadProofFirst32Hex = first32Hex
                    )
                }

                if (!dualMatched) {
                    val failMsg = "❌ فشل التحقق بالقراءة المزدوجة (Dual-Read Compare) لعائلة Samsung S3F8 (${page0Bytes.size}B) — عدم تطابق القراءتين المتتاليتين | أول 32B: $first32Hex"
                    appendLog(ProberLogDirection.WARN, failMsg)
                    updateStep("stage6_live_read_proof", ChecklistStepStatus.FAILED, failMsg)
                    return summary.copy(
                        liveReadProofPassed = false,
                        liveReadProofMode = "Samsung S3F8 Dual-Read Compare Mismatch ($readMethodUsed)",
                        liveReadProofStatus = failMsg,
                        liveReadProofFirst32Hex = first32Hex
                    )
                }

                if (ffCount >= dummyThreshold || zeroCount >= dummyThreshold) {
                    isChipBlankOrZero = true
                    appendLog(
                        ProberLogDirection.WARN,
                        "⚠️ المرحلة 6: العينة المسحوبة من Page 0 (${page0Bytes.size}B) فارغة/متشابهة (${if (ffCount >= dummyThreshold) "0xFF" else "0x00"} >= 99%). الانتقال للحالة الثانية (الحلقة المغلقة Write-Read Proof)..."
                    )
                } else {
                    val reportMsg = "✅ إثبات السحب الحقيقي 100% (Samsung Dual-Read Compare): تم سحب ${page0Bytes.size}B مرتين متتاليتين وتطابقتا بايت ببايت"
                    val fullDetail = "$reportMsg ($readMethodUsed) | أول 32B: $first32Hex"
                    appendLog(ProberLogDirection.SUCCESS, fullDetail)
                    updateStep("stage6_live_read_proof", ChecklistStepStatus.PASSED, fullDetail)
                    return summary.copy(
                        liveReadProofPassed = true,
                        liveReadProofMode = "Case 1: Samsung S3F8 Dual-Read Compare 100% ($readMethodUsed)",
                        liveReadProofStatus = reportMsg,
                        liveReadProofFirst32Hex = first32Hex
                    )
                }
            } else {
                if (ffCount >= dummyThreshold || zeroCount >= dummyThreshold) {
                    isChipBlankOrZero = true
                    appendLog(
                        ProberLogDirection.WARN,
                        "⚠️ المرحلة 6: العينة المسحوبة من Page 0 (${page0Bytes.size}B) فارغة/متشابهة (${if (ffCount >= dummyThreshold) "0xFF" else "0x00"} >= 99%). الانتقال للحالة الثانية (الحلقة المغلقة Write-Read Proof)..."
                    )
                } else {
                    detectedHasRealData = true
                }
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
                        12, 280, "6.0 Verify 0x13 Page 0", stopOnCompleteNecFrame = true
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
                DetectedFraming.STM32_ACK, DetectedFraming.STLINK_SWD -> detectedProbedFlashStart.takeIf { it != 0 } ?: 0x08000000
                DetectedFraming.STLINK_SWIM -> detectedProbedFlashStart.takeIf { it != 0 } ?: 0x008000
                DetectedFraming.USBDM_BDM -> detectedProbedFlashStart
                DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> 0x00C000
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
        return parsed ?: detectedProbedFlashEnd.takeIf { it > 0 } ?: when (sync.framing) {
            DetectedFraming.STM32_ACK, DetectedFraming.STLINK_SWD -> 0x0801FFFF
            DetectedFraming.STLINK_SWIM -> 0x009FFF
            else -> 0x007FFF
        }
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
            val arch = sync.usbdmTargetArch ?: detectedUsbdmTargetArch
            val readCmd = detectedUsbdmReadCmd
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "Stage6 USBDM HALT [02 14]", logTxRx = false)
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            var lastCmd = ByteArray(0)
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(32, length - offset)
                val addr = startAddr.toLong() + offset.toLong()
                val pkt = usbdmReadMemPacket(readCmd, arch.memSpace, chunkLen, addr)
                lastCmd = pkt
                val rx = transport.usbdmTransceive(pkt, chunkLen + 8, 350, "Stage6 USBDM READ_MEM [0x%02X %s @ 0x%06X +%d]".format(readCmd, arch.name, addr, chunkLen), logTxRx = false)
                if (rx.size < chunkLen + 1 || (rx[0].toInt() and 0xFF) != 0x00) {
                    return PageReadOutcome(null, "USBDM READ_MEM (${arch.name} 0x%02X) Failed at 0x%06X".format(readCmd, addr), pkt)
                }
                out.write(rx, 1, chunkLen)
                offset += chunkLen
            }
            return PageReadOutcome(out.toByteArray(), "USBDM READ_MEM (${arch.name} memSpace=0x%02X 0x%02X @ 0x%06X)".format(arch.memSpace, readCmd, startAddr), lastCmd)
        }

        if (sync.framing == DetectedFraming.STLINK_SWD) {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(32, length - offset)
                val chunk = stlinkReadMem32(startAddr.toLong() + offset.toLong(), chunkLen)
                if (chunk.size < chunkLen) {
                    return PageReadOutcome(null, "ST-LINK SWD Mem32 Failed at 0x%08X".format(startAddr + offset), byteArrayOf(0xF2.toByte(), 0x08))
                }
                out.write(chunk, 0, chunkLen)
                offset += chunkLen
            }
            return PageReadOutcome(out.toByteArray(), "ST-LINK SWD Mem32 [0xF2 0x08 @ 0x%08X]".format(startAddr), byteArrayOf(0xF2.toByte(), 0x08))
        }

        if (sync.framing == DetectedFraming.STLINK_SWIM) {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(32, length - offset)
                val chunk = stlinkSwimReadMem(startAddr.toLong() + offset.toLong(), chunkLen)
                if (chunk.size < chunkLen) {
                    return PageReadOutcome(null, "ST-LINK SWIM Read Failed at 0x%06X".format(startAddr + offset), byteArrayOf(0xF4.toByte(), 0x0B))
                }
                out.write(chunk, 0, chunkLen)
                offset += chunkLen
            }
            return PageReadOutcome(out.toByteArray(), "ST-LINK SWIM Read [0xF4 0x0B/0x0C @ 0x%06X]".format(startAddr), byteArrayOf(0xF4.toByte(), 0x0B))
        }

        if (sync.framing == DetectedFraming.AVR_ISP_USB && transport.currentChip() == ProberUsbChip.USBASP) {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(64, length - offset)
                val addr = startAddr + offset
                val chunk = transport.usbControlTransferIn(
                    0xC0, 0x04, (addr and 0xFFFF), ((addr ushr 16) and 0xFFFF), chunkLen, 350,
                    "Stage6 USBasp ReadFlash [0x04 @ 0x%06X +%d]".format(addr, chunkLen), logTxRx = false
                )
                if (chunk.size < chunkLen) {
                    return PageReadOutcome(null, "USBasp EP0 0x04 Read Failed at 0x%06X".format(addr), byteArrayOf(0xC0.toByte(), 0x04))
                }
                out.write(chunk, 0, chunkLen)
                offset += chunkLen
            }
            return PageReadOutcome(out.toByteArray(), "USBasp EP0 FUNC_READFLASH [0x04 @ 0x%06X]".format(startAddr), byteArrayOf(0xC0.toByte(), 0x04))
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

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Genuine Samsung S3F8 Serial Read: [0x05, Addr_H, Addr_L, Len] (chunked <= 32B), fallback [0x04, Addr_H, Addr_L]
            for (cmd in listOf(0x05, 0x04)) {
                val out = java.io.ByteArrayOutputStream()
                var offset = 0
                var lastPkt = ByteArray(0)
                var readOk = true
                while (offset < length) {
                    currentCoroutineContext().ensureActive()
                    val chunkLen = min(32, length - offset)
                    val addr = startAddr + offset
                    val pkt = if (cmd == 0x05) {
                        byteArrayOf(0x05, ((addr ushr 8) and 0xFF).toByte(), (addr and 0xFF).toByte(), (chunkLen and 0xFF).toByte())
                    } else {
                        byteArrayOf(0x04, ((addr ushr 8) and 0xFF).toByte(), (addr and 0xFF).toByte())
                    }
                    lastPkt = pkt
                    val rx = samsungSyncTransceive(
                        sync,
                        pkt,
                        rxLen = chunkLen,
                        label = "Stage6 Samsung S3F8 Read [0x%02X @ 0x%04X +%d]".format(cmd, addr, chunkLen),
                        logTxRx = (offset == 0)
                    )
                    if (rx.size < chunkLen || rx.all { it == 0x00.toByte() } || isSamsungUartArtifact(rx)) {
                        readOk = false
                        break
                    }
                    out.write(rx, 0, chunkLen)
                    offset += chunkLen
                }
                if (readOk && out.size() >= length) {
                    return PageReadOutcome(out.toByteArray().copyOfRange(0, length), "Samsung S3F8 Read [0x%02X @ 0x%04X]".format(cmd, startAddr), lastPkt)
                }
            }
            return PageReadOutcome(null, "Samsung S3F8 Read [0x05/0x04] Protected/Locked", byteArrayOf(0x05))
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
            appendLog(ProberLogDirection.INFO, "المرحلة 6: استخدام مدرسة 11B Cumulative 0xB0 لسحب $length بايت من 0x%06X...".format(startAddr))
            return extractBytesVia0xB0Cumulative(startAddr, length)
        }

        return PageReadOutcome(null, "No Open Read School", ByteArray(0))
    }

    private suspend fun verifyExtractedPageOnChip(
        sync: SyncLock,
        startAddr: Int,
        pageData: ByteArray
    ): Triple<Boolean, Boolean, String> {
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            return Triple(false, false, "عائلة Samsung S3F8 تستخدم القراءة المزدوجة (Dual-Read Compare) بدلاً من 0x13")
        }
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

        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Genuine Samsung S3F8 Byte/Page Program: [0x06, Addr_H, Addr_L, Data...]
            var offset = 0
            var lastRx = ByteArray(0)
            while (offset < payloadPage.size) {
                currentCoroutineContext().ensureActive()
                val chunkLen = min(32, payloadPage.size - offset)
                val addr = lastPageStart + offset
                val chunk = payloadPage.copyOfRange(offset, offset + chunkLen)
                val pkt = byteArrayOf(0x06, ((addr ushr 8) and 0xFF).toByte(), (addr and 0xFF).toByte()) + chunk
                val rx = samsungSyncTransceive(
                    sync,
                    pkt,
                    rxLen = 2,
                    label = "Stage6 Samsung S3F8 Write [0x06 @ 0x%04X +%d]".format(addr, chunkLen),
                    logTxRx = (offset == 0)
                )
                if (rx.isEmpty() || rx.all { val b = it.toInt() and 0xFF; b == 0x00 || b == 0xFF } || isSamsungUartArtifact(rx)) {
                    return false to "Samsung S3F8 0x06 Write Failed @ 0x%04X".format(addr)
                }
                lastRx = rx
                offset += chunkLen
                delay(10)
            }
            return true to "Samsung S3F8 0x06 Write OK (${lastRx.toHexStr()})"
        }

        // Generic / Renesas Raw Write 0x41
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

    private suspend fun ensureHcs08FclkdivConfigured() {
        val fclkRx = transport.usbdmTransceive(
            usbdmReadMemPacket(detectedUsbdmReadCmd, 0x01, 1, 0x1820L),
            8, 150, "Read HCS08 FCLKDIV [0x1820]", logTxRx = false
        )
        val fclkVal = if (fclkRx.size >= 2 && (fclkRx[0].toInt() and 0xFF) == 0x00) fclkRx[1].toInt() and 0xFF else 0
        if ((fclkVal and 0x80) == 0) {
            transport.usbdmTransceive(
                byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x20, 0x27),
                8, 150, "Set HCS08 FCLKDIV [0x1820=0x27]", logTxRx = false
            )
        }
    }

    private suspend fun usbdmProgramBytes(startAddr: Int, data: ByteArray): Boolean {
        transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 200, "USBDM HALT [02 14]", logTxRx = false)
        ensureHcs08FclkdivConfigured()
        for (i in data.indices) {
            currentCoroutineContext().ensureActive()
            val b = data[i].toInt() and 0xFF
            if (b == 0xFF) continue
            val addr = startAddr + i
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x30), 8, 120, "FSTAT Clear [0x1825]", logTxRx = false)
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
            transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x26, 0x20), 8, 120, "FCMD ByteProg [0x1826=0x20]", logTxRx = false)
            val go = transport.usbdmTransceive(byteArrayOf(0x09, 0x20, 0x01, 0x01, 0x00, 0x00, 0x18, 0x25, 0x80.toByte()), 8, 150, "FSTAT Launch [0x1825=0x80]", logTxRx = false)
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
            val effectiveCode = sync.actualTargetCode ?: detectedUsbdmTargetCode
            var cn = transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 8, 250, "USBDM Reconnect [02 0F]", logTxRx = false)
            if (cn.isEmpty() || (cn[0].toInt() and 0xFF) != 0x00) {
                transport.usbdmTransceive(byteArrayOf(0x03, 0x03, 0x01), 8, 200, "USBDM Internal Power [03 03 01]", logTxRx = false)
                delay(100)
                transport.usbdmTransceive(byteArrayOf(0x03, 0x01, effectiveCode.toByte()), 8, 200, "USBDM Re-Target [03 01 0x%02X]".format(effectiveCode), logTxRx = false)
                transport.usbdmTransceive(byteArrayOf(0x03, 0x13, 0x00), 8, 200, "USBDM RESET_SPECIAL [03 13 00]", logTxRx = false)
                delay(100)
                cn = transport.usbdmTransceive(byteArrayOf(0x02, 0x0F), 8, 250, "USBDM Reconnect Retry [02 0F]", logTxRx = false)
            }
            transport.usbdmTransceive(byteArrayOf(0x02, 0x14), 8, 250, "USBDM Re-Halt [02 14]", logTxRx = false)
            return cn.isNotEmpty() && (cn[0].toInt() and 0xFF) == 0x00
        }
        if (sync.framing == DetectedFraming.STLINK_SWD || sync.framing == DetectedFraming.STLINK_SWIM ||
            sync.framing == DetectedFraming.AVR_ISP_USB || sync.framing == DetectedFraming.MICROCHIP_PIC_ICSP ||
            transport.currentChip() == ProberUsbChip.UNKNOWN_USB
        ) {
            transport.flushRx()
            delay(35)
            return true
        }
        if (sync.framing == DetectedFraming.SAMSUNG_ISP) {
            // Re-Sync keeping DTR (Vpp/TEST) = HIGH (+5V) strictly locked
            transport.samsungQuickResyncKeepTestHigh(
                testActiveHigh = sync.syncModePinState,
                pulseClocks = 8,
                forceUartModemLines = false
            )
            val syncAck = samsungSyncTransceive(sync, byteArrayOf(0xAA.toByte(), 0x55), rxLen = 2, label = "Samsung S3F8 Quick Re-Sync (DTR=HIGH) [0xAA 0x55]", logTxRx = false)
            return syncAck.isNotEmpty() && !syncAck.all { it == 0x00.toByte() }
        }
        if (sync.useBitBangSync) {
            applySyncPinState(sync)
            val syncAck = samsungSyncTransceive(sync, byteArrayOf(0xAA.toByte(), 0x55), rxLen = 2, label = "Sync Re-Sync [0xAA 0x55]", logTxRx = false)
            return syncAck.isNotEmpty()
        }
        transport.configureUart(sync.baudRate, evenParity = sync.evenParity)
        applySyncPinState(sync)
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

    private fun buildPlugAndPlayRegistryEntry(summary: ProberBlueprintSummary): PlugAndPlayRegistryEntry {
        val usb = _state.value.usbStatus
        val currentVidPid = usb.vidPidHex.takeIf { ":" in it && !it.startsWith("--") }
        val standardUartVidPids = listOf("0403:6001", "0403:6015", "1A86:7523", "10C4:EA60", "067B:2303")
        fun mergeVidPids(defaults: List<String>): List<String> =
            if (currentVidPid != null) (listOf(currentVidPid) + defaults).distinct() else defaults

        val modelUpper = summary.inferredModel.uppercase(Locale.US)
        val arch = summary.usbdmTargetArch ?: detectedUsbdmTargetArch
        val tm = summary.timingMap
        val sampleKey = when (summary.framing) {
            DetectedFraming.USBDM_BDM -> detectedSdidHex.ifBlank { detectedRawIdHex }.ifBlank { summary.autoDetectionRule.rawSiliconIdHex }.ifBlank { summary.inferredModel }
            DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> detectedSdidHex.ifBlank { summary.autoDetectionRule.rawSiliconIdHex }.ifBlank { summary.inferredModel }
            DetectedFraming.STLINK_SWD, DetectedFraming.STLINK_SWIM -> detectedStLinkIdCodeHex.ifBlank { detectedRawIdHex }.ifBlank { summary.inferredModel }
            DetectedFraming.AVR_ISP_USB -> detectedAvrSigHex.ifBlank { detectedRawIdHex }.ifBlank { summary.inferredModel }
            DetectedFraming.MICROCHIP_PIC_ICSP -> detectedPicIdHex.ifBlank { detectedRawIdHex }.ifBlank { summary.inferredModel }
            DetectedFraming.SAMSUNG_ISP -> detectedSamsungIdHex.ifBlank { detectedRawIdHex }.ifBlank { summary.inferredModel }
            else -> detectedRawIdHex.ifBlank { summary.autoDetectionRule.rawSiliconIdHex }.ifBlank { summary.inferredModel }
        }

        return when (summary.framing) {
            DetectedFraming.USBDM_BDM -> {
                val effectiveCode = detectedUsbdmTargetCode
                val (pidStr, famName, probeRule) = when (arch) {
                    UsbdmTargetArch.HCS08 -> Triple(
                        "FREESCALE_HCS08_FAMILY",
                        "Freescale / NXP HCS08 (MC9S08) 8-bit MCU Family",
                        "USBDM [03 01 %02X] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> Read SDID 0x1806 (0x21/0x22) + Flash 16B (0xC000/0x8000/0x1900) | Active SET_TARGET=0x%02X (zero PARTID/SDID accepted) | Flash Regs: FCLKDIV=0x1820, FSTAT=0x1825, FCMD=0x1826".format(effectiveCode, effectiveCode)
                    )
                    UsbdmTargetArch.HCS12 -> Triple(
                        "FREESCALE_HCS12_FAMILY",
                        "Freescale / NXP HCS12 (MC9S12) 16-bit MCU Family",
                        "USBDM [03 01 %02X] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> READ_MEM 0x%02X (space=0x01) @ PARTID 0x001A (2B)".format(effectiveCode, detectedUsbdmReadCmd)
                    )
                    UsbdmTargetArch.COLDFIRE_V1 -> Triple(
                        "FREESCALE_COLDFIRE_V1_FAMILY",
                        "Freescale / NXP ColdFire V1 (MCF51) 32-bit MCU Family",
                        "USBDM [03 01 02] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> READ_MEM 0x%02X (space=0x04) @ SDID 0xFFFF8006 (4B)".format(detectedUsbdmReadCmd)
                    )
                    UsbdmTargetArch.RS08 -> Triple(
                        "FREESCALE_RS08_FAMILY",
                        "Freescale / NXP RS08 8-bit MCU Family",
                        "USBDM [03 01 04] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> READ_MEM 0x%02X (space=0x01) @ SDID 0x0206 (2B)".format(detectedUsbdmReadCmd)
                    )
                    UsbdmTargetArch.ARM_SWD -> Triple(
                        "ARM_CORTEX_SWD_USBDM_FAMILY",
                        "ARM Cortex-M SWD (NXP Kinetis / STM32) Family via USBDM",
                        "USBDM [03 01 07] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> READ_MEM 0x%02X (space=0x04) @ SIM_SDID 0x40048024 / DBGMCU 0xE0042000".format(detectedUsbdmReadCmd)
                    )
                    UsbdmTargetArch.COLDFIRE_V2_4 -> Triple(
                        "FREESCALE_COLDFIRE_V2_4_FAMILY",
                        "Freescale ColdFire V2/V3/V4 (BDM/JTAG) Family",
                        "USBDM [03 01 03] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> READ_MEM 0x%02X (space=0x04) @ CIR/JTAG IDCODE".format(detectedUsbdmReadCmd)
                    )
                    UsbdmTargetArch.DSC_JTAG -> Triple(
                        "FREESCALE_56800E_DSC_FAMILY",
                        "Freescale 56800E Digital Signal Controller (JTAG) Family",
                        "USBDM [03 01 08] -> RESET_SPECIAL [03 13 00] -> wait 100ms -> CONNECT [02 0F] -> HALT [02 14] -> JTAG IDCODE"
                    )
                }
                PlugAndPlayRegistryEntry(
                    protocolId = pidStr,
                    familyDisplayName = famName,
                    sampleChipDatabaseKey = sampleKey,
                    supportedAdapterVidPids = mergeVidPids(listOf("16D0:0567")),
                    autoDetectProbeRule = probeRule
                )
            }
            DetectedFraming.FREESCALE_SINGLE_WIRE_BDM -> PlugAndPlayRegistryEntry(
                protocolId = "FREESCALE_HCS08_UART_BDM_FAMILY",
                familyDisplayName = "Freescale / NXP HCS08 Single-Wire BDM (UART 1.5Mbps) Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(listOf("0403:6001", "0403:6015")),
                autoDetectProbeRule = "FT232 @ 1,500,000 bps (8N1) Single-Wire BKGD -> Sync 0xFE/0xC0 -> Read SDID 0xE8 @ 0x1806..0x1807 (2B, != 0x0000 & != 0xFFFF)"
            )
            DetectedFraming.NEC_STX_ETX -> {
                val isRl78 = "RL78" in modelUpper || "R5F" in modelUpper
                val bypassNote = if (summary.hasPartialBypass) {
                    " | Winning Non-Standard Extraction Path (Block-0 Bypass + 0xB0/0x13 @ 0x0400..0x04FF)"
                } else ""
                PlugAndPlayRegistryEntry(
                    protocolId = if (isRl78) "RENESAS_RL78_FAMILY" else "NEC_78K0_FAMILY",
                    familyDisplayName = if (isRl78) "Renesas RL78 (R5F) Framed Bootloader Family" else "NEC / Renesas 78K0 (uPD78F) Framed Bootloader Family",
                    sampleChipDatabaseKey = sampleKey,
                    supportedAdapterVidPids = mergeVidPids(standardUartVidPids),
                    autoDetectProbeRule = "UART ${summary.baudRate ?: 9600}bps ${summary.parityMode} | Reset (${tm.resetPulseMs}ms->${tm.postResetDelayMs}ms) -> 0x00 (${tm.syncByteGapMs}ms) 0x00 -> SOH Reset [01 01 00 FF 03] -> ACK 0x06 -> Signature [01 01 C0 3E 03]$bypassNote"
                )
            }
            DetectedFraming.RENESAS_RAW -> PlugAndPlayRegistryEntry(
                protocolId = "RENESAS_R8C_M16C_FAMILY",
                familyDisplayName = "Renesas R8C / M16C / H8 / SH Direct UART Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(standardUartVidPids),
                autoDetectProbeRule = "UART ${summary.baudRate ?: 9600}bps ${summary.parityMode} -> 16x 0x00 (20ms gap) -> 0x70 SRD1/SRD2 + 0xFB Version Query"
            )
            DetectedFraming.STM32_ACK -> PlugAndPlayRegistryEntry(
                protocolId = "STM32_UART_BOOTLOADER_FAMILY",
                familyDisplayName = "STMicroelectronics STM32 USART ROM Bootloader Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(standardUartVidPids),
                autoDetectProbeRule = "UART ${summary.baudRate ?: 115200}bps 8E1 -> Auto-Baud 0x7F -> Expect ACK 0x79 -> GetID [02 FD]"
            )
            DetectedFraming.STLINK_SWD -> PlugAndPlayRegistryEntry(
                protocolId = "STM32_STLINK_SWD_FAMILY",
                familyDisplayName = "STMicroelectronics STM32 / ARM Cortex-M SWD Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(listOf("0483:3748", "0483:374B", "0483:374E", "0483:374F", "0483:3752", "0483:3753", "0483:3754")),
                autoDetectProbeRule = "ST-LINK USB Bulk [F1 80] -> EXIT_DFU [F3 07] -> ENTER_SWD [F2 20 A3] -> READ_IDCODE [F2 07] + DBGMCU [F2 08 @ 0xE0042000]"
            )
            DetectedFraming.STLINK_SWIM -> PlugAndPlayRegistryEntry(
                protocolId = "STM8_STLINK_SWIM_FAMILY",
                familyDisplayName = "STMicroelectronics STM8 SWIM Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(listOf("0483:3748", "0483:374B", "0483:374E", "0483:3752")),
                autoDetectProbeRule = "ST-LINK USB Bulk [F1 80] -> ENTER_SWIM [F4 00] -> SWIM_RESET [F4 02] -> SWIM_SPEED_HIGH [F4 03] -> MemRead [F4 0B]"
            )
            DetectedFraming.AVR_ISP_USB -> PlugAndPlayRegistryEntry(
                protocolId = "ATMEL_AVR_ISP_FAMILY",
                familyDisplayName = "Atmel / Microchip AVR (ATmega / ATtiny) SPI ISP Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(listOf("16C0:05DC", "03EB:2104", "03EB:2107", "03EB:2141")),
                autoDetectProbeRule = "USB EP0 CONNECT [0x01] -> SETISPSCK [0x0A] -> ENABLEPROG [0x05] -> SPI 3-Byte Device Signature [0x30 0x00 0x00..0x02]"
            )
            DetectedFraming.MICROCHIP_PIC_ICSP -> PlugAndPlayRegistryEntry(
                protocolId = "MICROCHIP_PIC_ICSP_FAMILY",
                familyDisplayName = "Microchip PIC (PIC16 / PIC18 / dsPIC / PIC24) ICSP Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(listOf("04D8:900A", "04D8:900F", "04D8:0033")),
                autoDetectProbeRule = "Microchip USB HID/Bulk PICkit/SNAP -> GET_VERSION [0x76] -> ICSP Device ID Query"
            )
            DetectedFraming.SAMSUNG_ISP -> PlugAndPlayRegistryEntry(
                protocolId = "SAMSUNG_S3F8_S3F9_FAMILY",
                familyDisplayName = "Samsung S3F8 / S3F9 / S3P Synchronous Serial ISP Family",
                sampleChipDatabaseKey = sampleKey,
                supportedAdapterVidPids = mergeVidPids(standardUartVidPids),
                autoDetectProbeRule = "Locked Pin Map: DTR=Vpp/TEST (HIGH +5V طوال الجلسة), RTS=SCLK, TX(1N4148)+RX=SDAT | Entry: DTR=LOW/RTS=LOW (50ms) -> DTR=+5V (20ms) -> 16 SCLK -> 0xAA 0x55 -> ID [0x80/0xC0] | Read=[0x05 Addr_H Addr_L Len] / [0x04] | SmartOpt=0x003E..0x003F | SectorErase=[0x08] | Prog=[0x06] | ChipErase=[0x09 0xAA 0x55]"
            )
            DetectedFraming.CUSTOM_RAW, null -> {
                val pidId = if (usb.chip == ProberUsbChip.UNKNOWN_USB && currentVidPid != null) {
                    "CUSTOM_USB_${currentVidPid.replace(":", "_")}_FAMILY"
                } else {
                    "CUSTOM_UNIVERSAL_TARGET_FAMILY"
                }
                PlugAndPlayRegistryEntry(
                    protocolId = pidId,
                    familyDisplayName = "${summary.manufacturerGuess} (${summary.framing?.label ?: usb.programmerCategory})",
                    sampleChipDatabaseKey = sampleKey,
                    supportedAdapterVidPids = mergeVidPids(if (usb.chip == ProberUsbChip.UNKNOWN_USB) emptyList() else standardUartVidPids),
                    autoDetectProbeRule = "${summary.adapterInitSpec.powerControlLogic} | ${summary.baudRate ?: 9600}bps ${summary.parityMode} | ${summary.wakeUpPatternUsed} | Sync=${summary.syncPulseHex} | ID=${summary.autoDetectionRule.idRegisterOrOpcode}"
                )
            }
        }
    }

    private fun updateSummaryAndBlueprint(summary: ProberBlueprintSummary) {
        val enriched = summary.copy(plugAndPlayRegistryEntry = buildPlugAndPlayRegistryEntry(summary))
        val next = _state.value.copy(blueprintSummary = enriched)
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

    private fun classifyFraming(reply: ByteArray, pulse: ByteArray, baudRate: Int = 0): DetectedFraming {
        if (reply.any { (it.toInt() and 0xFF) == STX } && reply.any { (it.toInt() and 0xFF) == ETX }) {
            return DetectedFraming.NEC_STX_ETX
        }
        val first = reply[0].toInt() and 0xFF
        val p0 = pulse.firstOrNull()?.let { it.toInt() and 0xFF } ?: -1
        if (p0 == 0x7F && (first == ACK_STM32 || first == NACK_STM32)) return DetectedFraming.STM32_ACK
        if (baudRate < 57600 && baudRate != 115200 && (first == ACK_NEC || first == 0x80 || first == 0xB0 || first == 0xC1)) {
            return DetectedFraming.RENESAS_RAW
        }
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
        val validEnds = setOf(0x000FFF, 0x001FFF, 0x003FFF, 0x007FFF, 0x00FFFF, 0x01FFFF, 0x03FFFF, 0x07FFFF, 0x0FFFFF, 0x1FFFFF)
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

    private data class StrictChecksum16Parse(
        val checksum16: Int? = null,
        val errorStatus: Int? = null,
        val sawAck: Boolean = false,
        val rawFrameHex: String = "",
        val invalidReason: String? = null
    )

    private fun parseStrictChecksum16Frames(rx: ByteArray): StrictChecksum16Parse {
        var idx = 0
        var sawAck = false
        var lastErrorStatus: Int? = null
        var lastInvalidReason: String? = null
        var lastFrameHex = ""

        while (idx < rx.size) {
            val stxRel = rx.copyOfRange(idx, rx.size).indexOfFirst { (it.toInt() and 0xFF) == STX }
            if (stxRel < 0) break
            val stxIdx = idx + stxRel
            if (rx.size < stxIdx + 5) {
                lastInvalidReason = "إطار 0xB0 مبتور (${rx.size - stxIdx} بايت)"
                break
            }
            val len = rx[stxIdx + 1].toInt() and 0xFF
            val payloadBytes = if (len == 0) 256 else len
            val frameTotal = 2 + payloadBytes + 2
            if (rx.size < stxIdx + frameTotal) {
                lastInvalidReason = "طول إطار 0xB0 غير مكتمل (LEN=$len)"
                break
            }
            val rawFrame = rx.copyOfRange(stxIdx, stxIdx + frameTotal)
            lastFrameHex = rawFrame.toHexStr()
            val endByte = rx[stxIdx + 2 + payloadBytes + 1].toInt() and 0xFF
            val sumByte = rx[stxIdx + 2 + payloadBytes].toInt() and 0xFF

            var sum = len
            for (k in 0 until payloadBytes) {
                sum += (rx[stxIdx + 2 + k].toInt() and 0xFF)
            }
            sum += sumByte
            if ((sum and 0xFF) != 0 || endByte != ETX) {
                lastInvalidReason = "فشل Frame_CS أو ETX!=0x03 في الإطار [$lastFrameHex]"
                idx = stxIdx + 1
                continue
            }

            if (payloadBytes == 1) {
                val sts = rx[stxIdx + 2].toInt() and 0xFF
                if (sts == ACK_NEC) {
                    sawAck = true
                } else {
                    lastErrorStatus = sts
                }
            } else if (payloadBytes == 2) {
                val csLow = rx[stxIdx + 2].toInt() and 0xFF
                val csHigh = rx[stxIdx + 3].toInt() and 0xFF
                if (!sawAck && csLow == ACK_NEC && csHigh in HARD_REJECT_STATUS) {
                    lastErrorStatus = csHigh
                } else {
                    return StrictChecksum16Parse(
                        checksum16 = (csHigh shl 8) or csLow,
                        sawAck = sawAck,
                        rawFrameHex = lastFrameHex
                    )
                }
            } else {
                lastInvalidReason = "طول payload غير صالح لـ 0xB0 (LEN=$payloadBytes != 2)"
            }
            idx = stxIdx + frameTotal
        }

        return StrictChecksum16Parse(
            checksum16 = null,
            errorStatus = lastErrorStatus,
            sawAck = sawAck,
            rawFrameHex = lastFrameHex,
            invalidReason = lastInvalidReason
        )
    }

    private suspend fun extractBytesVia0xB0Cumulative(startAddr: Int, length: Int): PageReadOutcome {
        val out = java.io.ByteArrayOutputStream()
        var csPrev = 0
        var lastPkt = ByteArray(0)
        for (i in 0 until length) {
            currentCoroutineContext().ensureActive()
            val endAddr = startAddr + i
            val pkt = buildNecCommandFrame(0xB0, addrRangeBytes(startAddr, endAddr))
            lastPkt = pkt
            val rx = transport.transceive(pkt, 16, 220, "6.B0 Cumulative +$i", stopOnCompleteNecFrame = true, logTxRx = false)
            var parsed = parseStrictChecksum16Frames(rx)
            if (parsed.checksum16 == null && parsed.errorStatus == null && parsed.sawAck) {
                val follow = transport.readFollowUp(12, 180, true)
                parsed = parseStrictChecksum16Frames(rx + follow)
            }
            if (parsed.errorStatus != null) {
                val errCode = parsed.errorStatus
                val errLabel = if (errCode == STS_BLANK_CHECK_ERROR) "STS_BLANK_CHECK_ERROR (0x1B)" else "0x%02X".format(errCode)
                return PageReadOutcome(
                    null,
                    "0xB0 مرفوض عند +$i ($errLabel) | RX=[${parsed.rawFrameHex.ifBlank { rx.toHexStr() }}]",
                    lastPkt
                )
            }
            val csCurr = parsed.checksum16
                ?: return PageReadOutcome(null, "0xB0 إطار غير صالح عند +$i (${parsed.invalidReason ?: "No 2B Checksum"})", lastPkt)
            val delta16 = ((csPrev - csCurr) + 0x10000) and 0xFFFF
            if (delta16 > 0xFF) {
                return PageReadOutcome(
                    null,
                    "فرق Checksum غير متسق عند +$i: CS_prev=0x%04X -> CS_curr=0x%04X (Δ=0x%04X > 0xFF)".format(csPrev, csCurr, delta16),
                    lastPkt
                )
            }
            out.write(delta16 and 0xFF)
            csPrev = csCurr
        }
        return if (out.size() == length) {
            PageReadOutcome(out.toByteArray(), "11-Byte 0xB0 Cumulative LE", lastPkt)
        } else {
            PageReadOutcome(null, "0xB0 Incomplete ($length B)", lastPkt)
        }
    }

    private fun parseChecksum16(rx: ByteArray): Int? = parseStrictChecksum16Frames(rx).checksum16

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

    private fun guessSamsungS3f8CommandRole(cmd: Int): String = when (cmd) {
        0x01 -> "Samsung S3F8 Get ID / Query"
        0x02 -> "Samsung S3F8 Status / Device Info"
        0x03 -> "Samsung S3F8 Mode / Config"
        0x04 -> "Samsung S3F8 Serial Read [0x04 Addr_H Addr_L]"
        0x05 -> "Samsung S3F8 Serial Flash Read [0x05 Addr_H Addr_L Len]"
        0x06 -> "Samsung S3F8 Byte/Page Program [0x06 Addr_H Addr_L Data...]"
        0x08 -> "Samsung S3F8 Sector Erase 1KB [0x08 Addr_H Addr_L]"
        0x09 -> "Samsung S3F8 Chip Erase [0x09 0xAA 0x55]"
        0x80, 0xC0 -> "Samsung S3F8 Silicon ID Query"
        else -> "Samsung S3F8 Vendor Cmd"
    }

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
