package com.example.adminlab

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProberTargetFamily(val labelAr: String) {
    AUTO_DETECT("اكتشاف شامل تلقائي لأي شركة (USBDM + FT232 Auto-ID)"),
    NEC_78K0("NEC / Renesas 78K0 / RL78 / V850 / RX"),
    FREESCALE_HCS08("Freescale / NXP HCS08 / S08 (USBDM / 1.5Mbps BDM)"),
    SAMSUNG_S3F("Samsung S3F8 / S3F9 / S3P ISP"),
    RENESAS_R8C_RL78("Renesas R8C / M16C / H8 / SH"),
    STM32_UART("STM32 / STM8 / Nuvoton / PIC Auto-Baud"),
    GENERIC_UART("TI / Infineon / NXP / AVR / ESP / مخصص")
}

enum class ProberUsbChip(
    val displayName: String,
    val vendorIds: List<Int>,
    val productIds: List<Int> = emptyList()
) {
    USBDM("USBDM BDM Programmer (Freescale / NXP)", listOf(0x16D0), listOf(0x0567)),
    FT232R("FTDI FT232R / FT232RL (Red UART)", listOf(0x0403)),
    CH340G("WCH CH340G / CH341", listOf(0x1A86)),
    CP2102("Silicon Labs CP2102 / CP210x", listOf(0x10C4)),
    PL2303("Prolific PL2303", listOf(0x067B)),
    CDC_ACM("USB CDC-ACM / Generic UART", emptyList());

    companion object {
        fun fromDevice(vid: Int, pid: Int): ProberUsbChip {
            if (vid == 0x16D0 && pid == 0x0567) return USBDM
            return entries.firstOrNull { it.productIds.isEmpty() && vid in it.vendorIds } ?: CDC_ACM
        }

        fun fromVendorId(vid: Int): ProberUsbChip =
            entries.firstOrNull { it.productIds.isEmpty() && vid in it.vendorIds } ?: CDC_ACM
    }
}

data class ProberUsbStatus(
    val isConnected: Boolean = false,
    val adapterName: String = "لا توجد مبرمجة متصلة",
    val chipModel: String = "غير محدد",
    val vidPidHex: String = "--",
    val hasPermission: Boolean = false,
    val deviceCount: Int = 0,
    val isUsbdm: Boolean = false
)

enum class ProberLogDirection {
    INFO,
    TX,
    RX,
    SUCCESS,
    WARN,
    ERROR
}

data class ProberLogEntry(
    val id: Long,
    val timestamp: String,
    val direction: ProberLogDirection,
    val message: String
)

enum class ChecklistStepStatus(val badgeText: String) {
    PENDING("بانتظار التنفيذ"),
    RUNNING("⏳ جاري"),
    PASSED("✅ نجح"),
    FAILED("❌ فشل"),
    AWAITING_SAFETY("⚠️ بانتظار الموافقة"),
    BYPASSED("⚠️ تم التجاوز")
}

data class ChecklistStepItem(
    val id: String,
    val stageTag: String,
    val title: String,
    val status: ChecklistStepStatus = ChecklistStepStatus.PENDING,
    val detail: String = ""
)

data class DiscoveredCommandEntry(
    val cmdHex: String,
    val cmdByte: Int,
    val replyStatusHex: String,
    val exactParamLen: Int?,
    val roleGuess: String,
    val isSafeReadOnly: Boolean
)

data class Stage5ConfirmPrompt(
    val stepIndex: Int,
    val totalSteps: Int,
    val stepTitle: String,
    val impactDescription: String
)

data class Stage5StepResult(
    val stepTitle: String,
    val passed: Boolean,
    val executed: Boolean,
    val durationMs: Long,
    val detail: String
)

data class TimingMapMs(
    val resetPulseMs: Int = 100,
    val postResetDelayMs: Int = 150,
    val syncByteGapMs: Int = 10,
    val interByteDelayMs: Int = 2,
    val commandToDataDelayMs: Int = 12,
    val frameToFrameDelayMs: Int = 18,
    val baudSwitchDelayMs: Int = 25,
    val eraseWriteBusyWaitMs: Int = 450
)

enum class DetectedFraming(
    val label: String,
    val sohHex: String,
    val stxHex: String,
    val etbHex: String,
    val etxHex: String,
    val ackHex: String,
    val errCmdHex: String,
    val errParamHex: String,
    val endianness: String,
    val checksumFormula: String
) {
    NEC_STX_ETX(
        label = "NEC / Renesas 78K0 / RL78 Framed (SOH/STX/ETB/ETX)",
        sohHex = "0x01",
        stxHex = "0x02",
        etbHex = "0x17",
        etxHex = "0x03",
        ackHex = "0x06",
        errCmdHex = "0x04",
        errParamHex = "0x05",
        endianness = "Big-Endian (24-bit: Addr_H, Addr_M, Addr_L)",
        checksumFormula = "8-bit Two's Complement: (0 - (LEN + sum(BODY))) & 0xFF"
    ),
    STM32_ACK(
        label = "Auto-Baud Sync Bootloader (STM32 / Nuvoton / PIC 0x79 ACK)",
        sohHex = "N/A (2-Byte [CMD, ~CMD])",
        stxHex = "N/A",
        etbHex = "N/A",
        etxHex = "0x79 ACK / 0x1F NACK",
        ackHex = "0x79",
        errCmdHex = "0x1F",
        errParamHex = "0x1F",
        endianness = "Big-Endian (32-bit MSB first)",
        checksumFormula = "8-bit XOR Checksum over payload/address bytes"
    ),
    RENESAS_RAW(
        label = "Renesas R8C / M16C / H8 / SH Direct Status Framing",
        sohHex = "Single-Byte Command (16x0x00 Wake)",
        stxHex = "256B Raw Page",
        etbHex = "N/A",
        etxHex = "Status SRD1/SRD2",
        ackHex = "0x06 / SRD1",
        errCmdHex = "0xFF",
        errParamHex = "SRD ERR",
        endianness = "Little-Endian (20-bit/24-bit: A0, A1, A2)",
        checksumFormula = "Direct Single-Wire / 16x0x00 + 0xB0/0xFB UART Framing"
    ),
    USBDM_BDM(
        label = "USBDM Native Bulk BDM (Freescale / NXP HCS08)",
        sohHex = "USBDM [LEN, CMD]",
        stxHex = "BDM Memory Block",
        etbHex = "N/A",
        etxHex = "0x00 (BDM_RC_OK)",
        ackHex = "0x00 (BDM_RC_OK)",
        errCmdHex = "0x01+ (BDM_RC_ERR)",
        errParamHex = "0x03 (ILLEGAL_PARAMS)",
        endianness = "Big-Endian (16-bit / 32-bit Addr)",
        checksumFormula = "Native USB Bulk Framing + HCS08 FCMD (0x41/0x20)"
    ),
    FREESCALE_SINGLE_WIRE_BDM(
        label = "Freescale / NXP Single-Wire BDM via FT232 @ 1.5 Mbps",
        sohHex = "BDM Sync (0xFE / 0xC0)",
        stxHex = "BKGD Single-Wire Stream",
        etbHex = "N/A",
        etxHex = "1.5 Mbps BKGD",
        ackHex = "SDID / BDM SYNC",
        errCmdHex = "Timeout",
        errParamHex = "No Sync",
        endianness = "Big-Endian (16-bit Addr)",
        checksumFormula = "FT232 TX+1kΩ+RX -> BKGD Single-Wire @ 1,500,000 bps (8N1)"
    ),
    SAMSUNG_ISP(
        label = "Samsung S3F8 / S3F9 / S3P ISP Bootloader",
        sohHex = "0xAA 0x55 / 0x80 Sync",
        stxHex = "Samsung ISP Page",
        etbHex = "N/A",
        etxHex = "ACK / CS",
        ackHex = "0xAA / 0x06",
        errCmdHex = "0x15 / Timeout",
        errParamHex = "0xFF",
        endianness = "Big-Endian (16-bit)",
        checksumFormula = "8-bit Sum Checksum (Samsung ISP)"
    ),
    CUSTOM_RAW(
        label = "بروتوكول نصي / قياسي / UART مخصص (NXP / AVR / ESP / TI / Unknown)",
        sohHex = "Raw / ASCII / SLIP",
        stxHex = "Raw",
        etbHex = "Raw",
        etxHex = "CR/LF or Delim",
        ackHex = "Raw / OK / 0x14 0x10",
        errCmdHex = "Raw",
        errParamHex = "Raw",
        endianness = "Vendor-Specific",
        checksumFormula = "Vendor-Specific / Raw UART"
    )
}

enum class ProberStagePhase {
    IDLE,
    RUNNING_SAFE_PIPELINE,
    AWAITING_STEP_CONFIRMATION,
    RUNNING_STAGE5_STEP,
    COMPLETED,
    STOPPED,
    ERROR
}

data class ProberBlueprintSummary(
    val baudRate: Int? = null,
    val parityMode: String = "8N1",
    val maxBaudTested: Int? = null,
    val supportedBaudRates: List<Int> = emptyList(),
    val wakeUpPatternUsed: String = "--",
    val flmd0PinState: String = "--",
    val resetPinIdleState: String = "--",
    val resetPinPulsePolarity: String = "--",
    val dtrPolarity: String = "--",
    val rtsPolarity: String = "--",
    val echoCancellation: Boolean = false,
    val syncPulseHex: String = "--",
    val framing: DetectedFraming? = null,
    val universalAutoIdRule: String = "--",
    val manufacturerGuess: String = "غير محدد بعد",
    val inferredModel: String = "غير محدد بعد",
    val verify0x13DiffResult: String = "--",
    val winningReadMethod: String = "--",
    val chipSignature: String = "",
    val flashSizeDetectionRule: String = "--",
    val oscillatorNegotiationRule: String = "--",
    val oscillatorProbeResults: String = "--",
    val winningOscillatorPayload: String = "--",
    val detectedCrystalMhz: String = "غير محدد",
    val baudStateMachineRule: String = "--",
    val supportedStxPageSizes: List<Int> = emptyList(),
    val pageSizeBytes: Int? = null,
    val blockSizeBytes: Int? = null,
    val flashEndAddressHex: String = "--",
    val safeDelayMs: Int? = null,
    val timingMap: TimingMapMs = TimingMapMs(),
    val discoveredCommands: List<DiscoveredCommandEntry> = emptyList(),
    val stage5Results: List<Stage5StepResult> = emptyList(),
    val advancedSchoolsNotes: String = "",
    val liveReadProofPassed: Boolean = false,
    val liveReadProofMode: String = "--",
    val liveReadProofStatus: String = "لم يُختبر بعد",
    val liveReadProofFirst32Hex: String = "--"
)

data class ProberUiState(
    val usbStatus: ProberUsbStatus = ProberUsbStatus(),
    val selectedFamily: ProberTargetFamily = ProberTargetFamily.AUTO_DETECT,
    val phase: ProberStagePhase = ProberStagePhase.IDLE,
    val stageBanner: String = "جاهز لبدء الفحص العالمي الشامل لأي معالج",
    val checklist: List<ChecklistStepItem> = defaultChecklist(),
    val blueprintSummary: ProberBlueprintSummary = ProberBlueprintSummary(),
    val readBlueprintText: String = "",
    val writeBlueprintText: String = "",
    val humanGuideText: String = "",
    val humanGuideJsonText: String = "",
    val blueprintText: String = "",
    val pendingConfirmDialog: Stage5ConfirmPrompt? = null,
    val canBypassSync: Boolean = false,
    val logs: List<ProberLogEntry> = emptyList()
) {
    val isBusy: Boolean
        get() = phase == ProberStagePhase.RUNNING_SAFE_PIPELINE ||
            phase == ProberStagePhase.AWAITING_STEP_CONFIRMATION ||
            phase == ProberStagePhase.RUNNING_STAGE5_STEP
}

fun defaultChecklist(): List<ChecklistStepItem> = listOf(
    ChecklistStepItem("stage1_handshake", "المرحلة 1", "التعرف التلقائي الشامل على المبرمجة والبروسيسور (USBDM + FT232 Multi-Vendor Auto-ID)"),
    ChecklistStepItem("stage2_direct_read", "المرحلة 2.1", "مدرسة القراءة المباشرة (0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, BDM Read)"),
    ChecklistStepItem("stage2_checksum_1b", "المرحلة 2.2", "مدرسة السحب عبر بصمة بايت واحد (0xB0 على 1B ثم 2B)"),
    ChecklistStepItem("stage2_verify_0x13", "المرحلة 2.3", "مدرسة التحقق التدريجي (Verify 0x13: مقارنة صفحة 0xFF مع 0x00 لكشف القفل)"),
    ChecklistStepItem("stage2_signature", "المرحلة 2.4", "مدرسة قراءة التوقيع والهوية (0xC0, 0xC5, 0xFB, SDIDH/L, Samsung/STM32 ID)"),
    ChecklistStepItem("stage2_ram_stub", "المرحلة 2.5", "مدرسة حقن الكود في الرام (RAM Stub في عنوان مؤقت آمن)"),
    ChecklistStepItem("stage3_cmd_sweep", "المرحلة 3", "المسح الجراحي الشامل للأوامر 0x00..0xFF وأطوال المعاملات LEN 0..8"),
    ChecklistStepItem("stage4_timing_bounds", "المرحلة 4", "تكييف البروتوكول والسرعة: مصفوفة 0x90 + حدود الفلاش + تبديل السرعة + 6 أزمنة"),
    ChecklistStepItem("stage5_safety_gate", "المرحلة 5", "بوابة الأمان التفاعلية (تصفير Block 0 / Mass Erase / الكتابة الجزئية)"),
    ChecklistStepItem("stage6_live_read_proof", "المرحلة 6", "إثبات صحة السحب الفعلي (Universal Live Read Proof — عينة مليئة أو فارغة 0xFF)")
)

private fun jsonEsc(v: String): String = v
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "\\n")

private fun buildWorkflows(state: ProberUiState): Pair<List<String>, List<String>> {
    val s = state.blueprintSummary
    val framing = s.framing
    val tm = s.timingMap
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }

    if (framing == DetectedFraming.USBDM_BDM || framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
        val readWf = listOf(
            "1. Adapter Setup: ${if (framing == DetectedFraming.USBDM_BDM) "Open USBDM USB Bulk (EP1 OUT / EP2 IN), CMD_USBDM_GET_CAPABILITIES [0x02,0x01], SET_VDD 5V, SET_TARGET HCS08, CONNECT + SYNC" else "Open FT232 at 1,500,000 bps (8N1) Single-Wire BKGD (TX+1kΩ+RX) and send BDM Sync (0xFE/0xC0)"}.",
            "2. Silicon Auto-ID: Read HCS08 SDIDH/SDIDL (0x1806..0x1807) -> ${s.inferredModel} (${s.chipSignature}).",
            if (!isLocked) {
                "3. Safe BDM Memory Read: Read Flash up to ${s.flashEndAddressHex} via ${s.winningReadMethod} in ${s.pageSizeBytes ?: 256}B chunks."
            } else {
                "3. Target Flash is SECURED (FSEC locked): Cannot read directly without Mass Erase (FCMD 0x41) security unlock."
            }
        )
        val writeWf = listOf(
            "1. Connect via ${framing.label} and sync bus clock (${s.detectedCrystalMhz}).",
            "2. Erase Flash via HCS08 FSTAT/FCMD 0x41 (Mass Erase) or 0x40 (Page Erase), waiting ${tm.eraseWriteBusyWaitMs}ms.",
            "3. Program Flash via FCMD 0x20 (Byte Program) / 0x25 (Burst Program) in ${s.pageSizeBytes ?: 64}B blocks and verify read-back."
        )
        return readWf to writeWf
    }

    if (framing == DetectedFraming.SAMSUNG_ISP) {
        val readWf = listOf(
            "1. Pulse RESET and open UART at ${s.baudRate ?: 9600} bps ${s.parityMode}; send Samsung ISP Sync (0xAA 0x55 / 0x80).",
            "2. Query Samsung Chip ID -> ${s.inferredModel} (${s.chipSignature}).",
            if (!isLocked) {
                "3. Extract Flash up to ${s.flashEndAddressHex} via ${s.winningReadMethod} in ${s.pageSizeBytes ?: 256}B pages."
            } else {
                "3. Target Readout Protected: Execute Samsung ISP Erase/Unprotect before reprogramming."
            }
        )
        val writeWf = listOf(
            "1. Sync Samsung ISP at ${s.baudRate ?: 9600} bps (${s.syncPulseHex}).",
            "2. Erase Sectors/Chip via Samsung ISP Erase command (wait ${tm.eraseWriteBusyWaitMs}ms).",
            "3. Stream ${s.pageSizeBytes ?: 256}B pages with 8-bit checksum and verify against Flash."
        )
        return readWf to writeWf
    }

    val readWorkflow = buildList {
        add("1. Apply Pin Logic: FLMD0/MODE=${s.flmd0PinState}, RESET Idle=${s.resetPinIdleState}, Pulse=${s.resetPinPulsePolarity} (${tm.resetPulseMs}ms -> wait ${tm.postResetDelayMs}ms).")
        add("2. Open UART at ${s.baudRate ?: 9600} bps ${s.parityMode} (echoCancellation=${s.echoCancellation}); run wake-up pattern [${s.wakeUpPatternUsed}] (${s.syncPulseHex}).")
        add("3. Run Universal Auto-ID (${s.universalAutoIdRule}) to identify ${s.manufacturerGuess} / ${s.inferredModel} and Flash End (${s.flashSizeDetectionRule}).")
        add("4. Crystal/Oscillator Lock: ${s.oscillatorNegotiationRule}")
        add("5. Baud Rate Transition: ${s.baudStateMachineRule} (wait ${tm.baudSwitchDelayMs}ms after ACK).")
        if (!isLocked) {
            add("6. Safe Direct Extraction succeeded via ${s.winningReadMethod}: read 0x000000..${s.flashEndAddressHex} in ${s.pageSizeBytes ?: 256}B pages (${tm.frameToFrameDelayMs}ms frame gap).")
        } else {
            add("6. Target is LOCKED (${s.verify0x13DiffResult}): capture Full Flash & Block-0 Checksums via 0xB0 before any destructive step.")
            add("7. Zero Block-0 (0x22) -> extract Block-0 via 0xB0 differential if supported -> extract remaining blocks -> restore Block-0 (0x40) -> verify Full Flash Checksum (0xB0).")
        }
    }

    val writeWorkflow = buildList {
        add("1. Apply Pin Logic: FLMD0/MODE=${s.flmd0PinState}, RESET Pulse=${tm.resetPulseMs}ms, settle=${tm.postResetDelayMs}ms.")
        add("2. Open UART at ${s.baudRate ?: 9600} bps ${s.parityMode}; execute wake-up (${s.syncPulseHex}) and confirm ACK (${framing?.ackHex ?: "0x06"}).")
        add("3. Auto-ID & Geometry: ${s.universalAutoIdRule} -> End=${s.flashEndAddressHex}, Block=${s.blockSizeBytes ?: 1024}B, Page=${s.pageSizeBytes ?: 256}B.")
        add("4. Lock Oscillator & Speed: ${s.oscillatorNegotiationRule} (winning=${s.winningOscillatorPayload}) | ${s.baudStateMachineRule}.")
        add("5. Blank Check / Erase: send 0x32 (Blank Check) or Erase via 0x20 (Chip Erase) / 0x22 (Block Erase) and wait ${tm.eraseWriteBusyWaitMs}ms busy timeout.")
        add("6. Program Flash (0x40): send 6-byte Start..End (${framing?.endianness ?: "Big-Endian"}), wait ${tm.commandToDataDelayMs}ms, stream STX (0x02) frames of ${s.pageSizeBytes ?: 256}B ending with ETB (0x17) on intermediate pages and ETX (0x03) on final page (${tm.frameToFrameDelayMs}ms gap).")
        add("7. Verify & Sign-off: verify written data via 0x13 (Verify) and confirm Full-Flash 0xB0 Checksum.")
    }
    return readWorkflow to writeWorkflow
}

/**
 * Card 1 (Right): Read / Extraction Blueprint JSON
 */
fun formatReadBlueprintJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val framing = s.framing
    val tm = s.timingMap
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val (readWorkflow, _) = buildWorkflows(state)
    val readCmds = s.discoveredCommands.filter { it.isSafeReadOnly }

    return buildString {
        appendLine("{")
        appendLine("  \"blueprintType\": \"UNIVERSAL_READ_PROTOCOL_V1\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"adapter\": \"${jsonEsc(state.usbStatus.adapterName)} (${jsonEsc(state.usbStatus.vidPidHex)})\",")
        appendLine("  \"targetIdentity\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"universalAutoIdRule\": \"${jsonEsc(s.universalAutoIdRule)}\",")
        appendLine("    \"rawSignature\": \"${jsonEsc(s.chipSignature)}\"")
        appendLine("  },")
        appendLine("  \"uartAndPinHandshake\": {")
        appendLine("    \"baudRate\": ${s.baudRate ?: 9600},")
        appendLine("    \"parityMode\": \"${jsonEsc(s.parityMode)}\",")
        appendLine("    \"wakeUpPattern\": \"${jsonEsc(s.wakeUpPatternUsed)}\",")
        appendLine("    \"syncPulseHex\": \"${jsonEsc(s.syncPulseHex)}\",")
        appendLine("    \"flmd0ModePin\": \"${jsonEsc(s.flmd0PinState)}\",")
        appendLine("    \"resetPinIdle\": \"${jsonEsc(s.resetPinIdleState)}\",")
        appendLine("    \"resetPinPulsePolarity\": \"${jsonEsc(s.resetPinPulsePolarity)}\",")
        appendLine("    \"echoCancellation\": ${s.echoCancellation}")
        appendLine("  },")
        appendLine("  \"memoryGeometry\": {")
        appendLine("    \"flashEndAddress\": \"${jsonEsc(s.flashEndAddressHex)}\",")
        appendLine("    \"flashSizeDetectionRule\": \"${jsonEsc(s.flashSizeDetectionRule)}\",")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256},")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024}")
        appendLine("  },")
        appendLine("  \"readExtractionStatus\": {")
        appendLine("    \"isLockedForSafeRead\": $isLocked,")
        appendLine("    \"verify0x13DiffResult\": \"${jsonEsc(s.verify0x13DiffResult)}\",")
        appendLine("    \"winningReadSchool\": \"${jsonEsc(s.winningReadMethod)}\",")
        appendLine("    \"safeReadTests\": [")
        val stage2List = state.checklist.filter { it.id.startsWith("stage2_") }
        stage2List.forEachIndexed { idx, item ->
            val comma = if (idx < stage2List.lastIndex) "," else ""
            appendLine("      { \"school\": \"${jsonEsc(item.title)}\", \"passed\": ${item.status == ChecklistStepStatus.PASSED}, \"detail\": \"${jsonEsc(item.detail)}\" }$comma")
        }
        appendLine("    ]")
        appendLine("  },")
        appendLine("  \"safeReadCommands\": [")
        readCmds.forEachIndexed { idx, cmd ->
            val comma = if (idx < readCmds.lastIndex) "," else ""
            appendLine("    { \"cmd\": \"${cmd.cmdHex}\", \"lenArgs\": ${cmd.exactParamLen ?: -1}, \"reply\": \"${cmd.replyStatusHex}\", \"role\": \"${jsonEsc(cmd.roleGuess)}\" }$comma")
        }
        appendLine("  ],")
        appendLine("  \"readTimingMs\": {")
        appendLine("    \"resetPulseMs\": ${tm.resetPulseMs},")
        appendLine("    \"postResetDelayMs\": ${tm.postResetDelayMs},")
        appendLine("    \"syncByteGapMs\": ${tm.syncByteGapMs},")
        appendLine("    \"interByteDelayMs\": ${tm.interByteDelayMs},")
        appendLine("    \"frameToFrameDelayMs\": ${tm.frameToFrameDelayMs},")
        appendLine("    \"baudSwitchDelayMs\": ${tm.baudSwitchDelayMs}")
        appendLine("  },")
        appendLine("  \"frameFormat\": {")
        appendLine("    \"protocol\": \"${jsonEsc(framing?.label ?: "--")}\",")
        appendLine("    \"soh\": \"${jsonEsc(framing?.sohHex ?: "--")}\",")
        appendLine("    \"ack\": \"${jsonEsc(framing?.ackHex ?: "--")}\",")
        appendLine("    \"endianness\": \"${jsonEsc(framing?.endianness ?: "--")}\"")
        appendLine("  },")
        appendLine("  \"exactDynamicReadWorkflow\": [")
        readWorkflow.forEachIndexed { idx, step ->
            val comma = if (idx < readWorkflow.lastIndex) "," else ""
            appendLine("    \"${jsonEsc(step)}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"universalLiveReadProof\": {")
        appendLine("    \"passed\": ${s.liveReadProofPassed},")
        appendLine("    \"mode\": \"${jsonEsc(s.liveReadProofMode)}\",")
        appendLine("    \"statusReport\": \"${jsonEsc(s.liveReadProofStatus)}\",")
        appendLine("    \"first32BytesHex\": \"${jsonEsc(s.liveReadProofFirst32Hex)}\"")
        appendLine("  },")
        appendLine("  \"antiDummyValidationRules\": [")
        appendLine("    \"Reject any extracted firmware file immediately if >= 99% of bytes are 0x00 or 0xFF unless it passes a genuine on-chip Verify command inside the MCU.\",")
        appendLine("    \"Verify extracted RX payload bytes never match TX command frame bytes (strict UART diode-echo rejection).\",")
        appendLine("    \"Validate Verify/Compare commands with two vectors: unmodified extracted bytes MUST return ACK, and 1-byte mutated bytes MUST return Mismatch/Verify Error.\",")
        appendLine("    \"On blank (0xFF) test chips, execute Write-Read closed-loop proof using [55 AA 12 34 56 78 9A BC DE F0 11 22 33 44 55 66] at (End_Addr - PageSize + 1) and confirm 100% read-back equality.\"")
        appendLine("  ]")
        appendLine("}")
    }
}

/**
 * Card 2 (Left): Write / Erase / Verify Blueprint JSON
 */
fun formatWriteBlueprintJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val framing = s.framing
    val tm = s.timingMap
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val (_, writeWorkflow) = buildWorkflows(state)
    val writeEraseCmds = s.discoveredCommands.filter {
        !it.isSafeReadOnly || it.cmdByte in setOf(0x13, 0x30, 0x32, 0x90, 0x9A, 0xB0)
    }

    return buildString {
        appendLine("{")
        appendLine("  \"blueprintType\": \"UNIVERSAL_WRITE_ERASE_VERIFY_V1\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"targetIdentity\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"flashEndAddress\": \"${jsonEsc(s.flashEndAddressHex)}\",")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024},")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256}")
        appendLine("  },")
        appendLine("  \"oscillatorAndCrystalMatrix\": {")
        appendLine("    \"winningPayload\": \"${jsonEsc(s.winningOscillatorPayload)}\",")
        appendLine("    \"detectedCrystalMhz\": \"${jsonEsc(s.detectedCrystalMhz)}\",")
        appendLine("    \"negotiationRule\": \"${jsonEsc(s.oscillatorNegotiationRule)}\",")
        appendLine("    \"matrixProbeResults\": \"${jsonEsc(s.oscillatorProbeResults)}\"")
        appendLine("  },")
        appendLine("  \"baudRateStateMachine\": {")
        appendLine("    \"initialBaudRate\": ${s.baudRate ?: 9600},")
        appendLine("    \"maxBaudSupported\": ${s.maxBaudTested ?: (s.baudRate ?: 9600)},")
        appendLine("    \"supportedBaudRates\": [${s.supportedBaudRates.joinToString(", ")}],")
        appendLine("    \"rule\": \"${jsonEsc(s.baudStateMachineRule)}\"")
        appendLine("  },")
        appendLine("  \"stxDataFrameSpec\": {")
        appendLine("    \"commandSoh\": \"${jsonEsc(framing?.sohHex ?: "0x01")}\",")
        appendLine("    \"dataStx\": \"${jsonEsc(framing?.stxHex ?: "0x02")}\",")
        appendLine("    \"intermediateEndEtb\": \"${jsonEsc(framing?.etbHex ?: "0x17")}\",")
        appendLine("    \"finalEndEtx\": \"${jsonEsc(framing?.etxHex ?: "0x03")}\",")
        appendLine("    \"ackByte\": \"${jsonEsc(framing?.ackHex ?: "0x06")}\",")
        appendLine("    \"errCmdByte\": \"${jsonEsc(framing?.errCmdHex ?: "0x04")}\",")
        appendLine("    \"errParamByte\": \"${jsonEsc(framing?.errParamHex ?: "0x05")}\",")
        appendLine("    \"supportedStxPageSizes\": [${s.supportedStxPageSizes.joinToString(", ")}],")
        appendLine("    \"checksumFormula\": \"${jsonEsc(framing?.checksumFormula ?: "--")}\"")
        appendLine("  },")
        appendLine("  \"writeEraseTimingMapMs\": {")
        appendLine("    \"resetPulseMs\": ${tm.resetPulseMs},")
        appendLine("    \"postResetDelayMs\": ${tm.postResetDelayMs},")
        appendLine("    \"syncByteGapMs\": ${tm.syncByteGapMs},")
        appendLine("    \"interByteDelayMs\": ${tm.interByteDelayMs},")
        appendLine("    \"commandToDataDelayMs\": ${tm.commandToDataDelayMs},")
        appendLine("    \"frameToFrameDelayMs\": ${tm.frameToFrameDelayMs},")
        appendLine("    \"baudSwitchDelayMs\": ${tm.baudSwitchDelayMs},")
        appendLine("    \"eraseWriteBusyWaitMs\": ${tm.eraseWriteBusyWaitMs}")
        appendLine("  },")
        appendLine("  \"writeEraseVerifyCommands\": [")
        writeEraseCmds.forEachIndexed { idx, cmd ->
            val comma = if (idx < writeEraseCmds.lastIndex) "," else ""
            appendLine("    { \"cmd\": \"${cmd.cmdHex}\", \"lenArgs\": ${cmd.exactParamLen ?: -1}, \"reply\": \"${cmd.replyStatusHex}\", \"role\": \"${jsonEsc(cmd.roleGuess)}\" }$comma")
        }
        appendLine("  ],")
        appendLine("  \"stage5DestructiveTests\": [")
        s.stage5Results.forEachIndexed { idx, r ->
            val comma = if (idx < s.stage5Results.lastIndex) "," else ""
            appendLine("    { \"step\": \"${jsonEsc(r.stepTitle)}\", \"executed\": ${r.executed}, \"passed\": ${r.passed}, \"durationMs\": ${r.durationMs}, \"detail\": \"${jsonEsc(r.detail)}\" }$comma")
        }
        appendLine("  ],")
        appendLine("  \"exactDynamicWriteWorkflow\": [")
        writeWorkflow.forEachIndexed { idx, step ->
            val comma = if (idx < writeWorkflow.lastIndex) "," else ""
            appendLine("    \"${jsonEsc(step)}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"universalLiveReadProof\": {")
        appendLine("    \"passed\": ${s.liveReadProofPassed},")
        appendLine("    \"mode\": \"${jsonEsc(s.liveReadProofMode)}\",")
        appendLine("    \"statusReport\": \"${jsonEsc(s.liveReadProofStatus)}\",")
        appendLine("    \"first32BytesHex\": \"${jsonEsc(s.liveReadProofFirst32Hex)}\"")
        appendLine("  },")
        appendLine("  \"antiDummyValidationRules\": [")
        appendLine("    \"Reject any extracted firmware file if >= 99% of bytes are 0x00 or 0xFF without passing an internal MCU Verify check.\",")
        appendLine("    \"Require exact match for Verify ACK and Mismatch error on 1-byte mutation.\"")
        appendLine("  ]")
        appendLine("}")
    }
}

/**
 * Card 3 (Bottom Full-Width): Comprehensive Human-Readable Arabic Guide
 */
fun formatHumanGuideText(state: ProberUiState): String {
    val s = state.blueprintSummary
    val tm = s.timingMap
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val lockStatusAr = if (isLocked) {
        "🔒 محمي ومقفل (Locked Bootloader — لا يسمح بالسحب المباشر الآمن)"
    } else {
        "🔓 مفتوح للسحب الآمن (${s.winningReadMethod})"
    }

    val stage2List = state.checklist.filter { it.id.startsWith("stage2_") }
    val extraCmds = s.discoveredCommands.joinToString("\n") { cmd ->
        val safeTag = if (cmd.isSafeReadOnly) "قراءة/استعلام آمن" else "كتابة/مسح/تعديل"
        "  • الأمر ${cmd.cmdHex} (معاملات LEN=${cmd.exactParamLen ?: "?"} | الرد=${cmd.replyStatusHex}) -> ${cmd.roleGuess} [$safeTag]"
    }.ifBlank { "  • لم يتم رصد أوامر إضافية حتى الآن." }

    return buildString {
        appendLine("══════════════════════════════════════════════")
        appendLine("1️⃣ بطاقة هوية البروسيسور (MCU Identity Card)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• الشركة المصنعة: ${s.manufacturerGuess}")
        appendLine("• العائلة / البروتوكول: ${s.framing?.label ?: state.selectedFamily.labelAr}")
        appendLine("• الموديل المستنتج من التوقيع: ${s.inferredModel}")
        appendLine("• التوقيع الخام (Signature): ${s.chipSignature.ifBlank { "لم يرد توقيع" }}")
        appendLine("• حجم الفلاش / نهاية الذاكرة: ${s.flashEndAddressHex}")
        appendLine("• حجم الصفحة (Page): ${s.pageSizeBytes ?: 256} بايت | حجم البلوك (Block): ${s.blockSizeBytes ?: 1024} بايت")
        appendLine("• التردد / الكريستالة المكتشفة: ${s.detectedCrystalMhz} (${s.winningOscillatorPayload})")
        appendLine("• حالة القفل والأمان: $lockStatusAr")
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("2️⃣ شرح خطوات السحب خطوة بخطوة (Read Workflow)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• الدخول لوضع البرمجة: ضبط خط الوضع FLMD0/MODE على (${s.flmd0PinState})، وتنفيذ نبضة إعادة التشغيل RESET (${s.resetPinPulsePolarity}) لمدة ${tm.resetPulseMs}ms ثم الانتظار ${tm.postResetDelayMs}ms.")
        appendLine("• التزامن الأولي: فتح UART على سرعة ${s.baudRate ?: 9600} bps بصيغة ${s.parityMode}، ثم إرسال نمط الاستيقاظ (${s.wakeUpPatternUsed}) مع تفعيل تجاهل صدى الدايودين (${if (s.echoCancellation) "مفعّل" else "غير مطلوب"}).")
        appendLine("• التعرف التلقائي (Auto-ID): ${s.universalAutoIdRule}")
        appendLine("• نتائج اختبار مدارس السحب الخمس:")
        stage2List.forEach { step ->
            appendLine("  - ${step.title}: [${step.status.badgeText}] ${step.detail}")
        }
        appendLine("• الخلاصة العملية للسحب:")
        if (!isLocked) {
            appendLine("  يمكن سحب السوفت وير مباشرة وأمان 100% عبر (${s.winningReadMethod}) من العنوان 0x000000 حتى ${s.flashEndAddressHex} بفاصل ${tm.frameToFrameDelayMs}ms بين الإطارات.")
        } else {
            appendLine("  المعالج مقفل (${s.verify0x13DiffResult}). السحب الآمن الكامل يتطلب: حفظ بصمات 0xB0 الأصلية أولاً، ثم تصفير Block 0 عبر 0x22 واستخراجه تفاضلياً إن أمكن، ثم سحب باقي البلوكات واستعادة Block 0 عبر 0x40 والتحقق النهائي بالبصمة.")
        }
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("3️⃣ شرح خطوات الكتابة والمسح والتحقق (Write & Erase)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• ضبط التردد والمذبذب (0x90): ${s.oscillatorNegotiationRule}")
        appendLine("• تبديل السرعة (0x9A): ${s.baudStateMachineRule}")
        appendLine("• المسح (Erase): يُستخدم 0x20 لمسح كامل الرقاقة أو 0x22 لمسح بلوك محدد (${s.blockSizeBytes ?: 1024}B)، مع انتظار زمن الانشغال الفعلي ${tm.eraseWriteBusyWaitMs}ms حتى وصول ACK (${s.framing?.ackHex ?: "0x06"}).")
        appendLine("• البرمجة (0x40 + STX): تُرسل ترويسة 0x40 بنطاق البداية والنهاية (${s.framing?.endianness ?: "Big-Endian"})، ثم الانتظار ${tm.commandToDataDelayMs}ms قبل ضخ إطارات البيانات البادئة بـ STX (${s.framing?.stxHex ?: "0x02"}) بحجم ${s.pageSizeBytes ?: 256}B؛ تنتهي الصفحات الوسطى بـ ETB (${s.framing?.etbHex ?: "0x17"}) والأخيرة بـ ETX (${s.framing?.etxHex ?: "0x03"}).")
        appendLine("• التحقق (Verify & Checksum): يُفحص الفراغ عبر 0x32، وتُطابق الصفحات عبر 0x13، وتُؤكد البصمة النهائية عبر 0xB0.")
        if (s.stage5Results.isNotEmpty()) {
            appendLine("• نتائج اختبارات المرحلة 5 التفاعلية:")
            s.stage5Results.forEach { r ->
                appendLine("  - ${r.stepTitle}: ${r.detail}")
            }
        }
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("4️⃣ ملاحظات هندسية إضافية وأكواد الأخطاء")
        appendLine("══════════════════════════════════════════════")
        appendLine("• معادلة Checksum: ${s.framing?.checksumFormula ?: "غير محددة"}")
        appendLine("• دليل رموز الاستجابة: ACK=${s.framing?.ackHex ?: "0x06"} (نجاح) | 0x04=أمر غير مدعوم | 0x05=طول/نطاق معاملات خطأ | 0x07=Checksum خطأ | 0x10=محمي | 0x1B=فشل فحص الفراغ | 0x1C=اختلاف مطابقة Verify.")
        appendLine("• نصيحة التوصيل: تأكد من توصيل GND المشترك، ومقاومة/دايود الفصل عند دمج TX/RX على سلك واحد (Single-Wire) أو توصيل منفصل عند UART الثنائي.")
        appendLine("• جدول الأوامر المكتشفة (${s.discoveredCommands.size} أمر):")
        appendLine(extraCmds)
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("5️⃣ إثبات صحة السحب الفعلي وقواعد رفض الملفات الوهمية (Stage 6)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• نتيجة الإثبات: ${s.liveReadProofStatus}")
        appendLine("• آلية الفحص: ${s.liveReadProofMode}")
        if (s.liveReadProofFirst32Hex != "--" && s.liveReadProofFirst32Hex.isNotBlank()) {
            appendLine("• أول 32 بايت مسحوبة (Hex): ${s.liveReadProofFirst32Hex}")
        }
        appendLine("• قواعد منع السحب الوهمي (antiDummyValidationRules):")
        appendLine("  1. رفض أي ملف سوفت وير مسحوب فوراً إذا كانت نسبة 0xFF أو 0x00 تبلغ >= 99% دون اجتياز اختبار Verify الحقيقي داخل المعالج.")
        appendLine("  2. رفض أي بايتات مطابقة لإطار الإرسال (Echo Rejection).")
        appendLine("  3. للشرائح الفارغة 0xFF: التحقق بحلقة مغلقة (كتابة البصمة 55 AA 12 34 56 78 9A BC DE F0 11 22 33 44 55 66 في آخر صفحة ثم استرجاعها 100%).")
    }
}

fun formatHumanGuideJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val tm = s.timingMap
    val guideText = formatHumanGuideText(state)
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
        appendLine("{")
        appendLine("  \"documentType\": \"MCU_HUMAN_GUIDE_AND_IDENTITY_V1\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"processorIdentityCard\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"family\": \"${jsonEsc(s.framing?.label ?: state.selectedFamily.labelAr)}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"rawSignature\": \"${jsonEsc(s.chipSignature)}\",")
        appendLine("    \"flashEndAddress\": \"${jsonEsc(s.flashEndAddressHex)}\",")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256},")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024},")
        appendLine("    \"detectedCrystalMhz\": \"${jsonEsc(s.detectedCrystalMhz)}\",")
        appendLine("    \"winningOscillatorPayload\": \"${jsonEsc(s.winningOscillatorPayload)}\"")
        appendLine("  },")
        appendLine("  \"timingMapMs\": {")
        appendLine("    \"resetPulseMs\": ${tm.resetPulseMs},")
        appendLine("    \"postResetDelayMs\": ${tm.postResetDelayMs},")
        appendLine("    \"syncByteGapMs\": ${tm.syncByteGapMs},")
        appendLine("    \"interByteDelayMs\": ${tm.interByteDelayMs},")
        appendLine("    \"commandToDataDelayMs\": ${tm.commandToDataDelayMs},")
        appendLine("    \"frameToFrameDelayMs\": ${tm.frameToFrameDelayMs},")
        appendLine("    \"baudSwitchDelayMs\": ${tm.baudSwitchDelayMs},")
        appendLine("    \"eraseWriteBusyWaitMs\": ${tm.eraseWriteBusyWaitMs}")
        appendLine("  },")
        appendLine("  \"universalLiveReadProof\": {")
        appendLine("    \"passed\": ${s.liveReadProofPassed},")
        appendLine("    \"mode\": \"${jsonEsc(s.liveReadProofMode)}\",")
        appendLine("    \"statusReport\": \"${jsonEsc(s.liveReadProofStatus)}\",")
        appendLine("    \"first32BytesHex\": \"${jsonEsc(s.liveReadProofFirst32Hex)}\"")
        appendLine("  },")
        appendLine("  \"antiDummyValidationRules\": [")
        appendLine("    \"Reject any extracted firmware file immediately if >= 99% of bytes are 0x00 or 0xFF unless it passes an on-chip Verify command inside the MCU.\",")
        appendLine("    \"Confirm extracted RX payload is not TX diode echo.\"")
        appendLine("  ],")
        appendLine("  \"fullArabicGuide\": \"${jsonEsc(guideText)}\"")
        appendLine("}")
    }
}

fun formatAiReadyBlueprintJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val framing = s.framing
    val tm = s.timingMap
    val (readWorkflow, writeWorkflow) = buildWorkflows(state)

    return buildString {
        appendLine("{")
        appendLine("  \"blueprintVersion\": \"admin-lab-universal-v6\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"adapter\": \"${jsonEsc(state.usbStatus.adapterName)} (${jsonEsc(state.usbStatus.vidPidHex)})\",")
        appendLine("  \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("  \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("  \"universalAutoIdRule\": \"${jsonEsc(s.universalAutoIdRule)}\",")
        appendLine("  \"parityMode\": \"${jsonEsc(s.parityMode)}\",")
        appendLine("  \"wakeUpPatternUsed\": \"${jsonEsc(s.wakeUpPatternUsed)}\",")
        appendLine("  \"isLockedForSafeRead\": $isLocked,")
        appendLine("  \"framing\": \"${jsonEsc(framing?.label ?: "--")}\",")
        appendLine("  \"winningOscillatorPayload\": \"${jsonEsc(s.winningOscillatorPayload)}\",")
        appendLine("  \"eraseWriteBusyWaitMs\": ${tm.eraseWriteBusyWaitMs},")
        appendLine("  \"universalLiveReadProof\": {")
        appendLine("    \"passed\": ${s.liveReadProofPassed},")
        appendLine("    \"mode\": \"${jsonEsc(s.liveReadProofMode)}\",")
        appendLine("    \"statusReport\": \"${jsonEsc(s.liveReadProofStatus)}\",")
        appendLine("    \"first32BytesHex\": \"${jsonEsc(s.liveReadProofFirst32Hex)}\"")
        appendLine("  },")
        appendLine("  \"antiDummyValidationRules\": [")
        appendLine("    \"Reject any extracted firmware file immediately if >= 99% of bytes are 0x00 or 0xFF unless it passes a genuine on-chip Verify command inside the MCU.\",")
        appendLine("    \"Verify RX bytes are not TX Echo and validate Verify ACK + 1-byte mutation Mismatch.\"")
        appendLine("  ],")
        appendLine("  \"exactDynamicReadWorkflow\": [${readWorkflow.joinToString(", ") { "\"${jsonEsc(it)}\"" }}],")
        appendLine("  \"exactDynamicWriteWorkflow\": [${writeWorkflow.joinToString(", ") { "\"${jsonEsc(it)}\"" }}]")
        appendLine("}")
    }
}

object FirmwareImageParser {
    data class ParsedFirmware(
        val bytes: ByteArray,
        val baseAddress: Int,
        val formatLabel: String
    )

    fun parse(fileName: String, rawBytes: ByteArray): ParsedFirmware {
        val lower = fileName.lowercase(Locale.US)
        val textPreview = rawBytes.take(64).toByteArray().decodeToString()
        val isIntelHex = lower.endsWith(".hex") || textPreview.trimStart().startsWith(":")
        if (!isIntelHex) {
            return ParsedFirmware(rawBytes, 0x000000, "Binary (.bin)")
        }

        val lines = rawBytes.decodeToString().lines()
        val sparseMap = sortedMapOf<Int, Byte>()
        var extLinearAddr = 0
        var extSegAddr = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (!line.startsWith(":") || line.length < 11) continue
            val byteCount = line.substring(1, 3).toIntOrNull(16) ?: continue
            val addr16 = line.substring(3, 7).toIntOrNull(16) ?: continue
            val recType = line.substring(7, 9).toIntOrNull(16) ?: continue

            when (recType) {
                0x00 -> {
                    val base = extLinearAddr + extSegAddr + addr16
                    for (i in 0 until byteCount) {
                        val pos = 9 + i * 2
                        if (pos + 2 <= line.length) {
                            val b = line.substring(pos, pos + 2).toIntOrNull(16) ?: 0xFF
                            sparseMap[base + i] = b.toByte()
                        }
                    }
                }
                0x01 -> break
                0x02 -> {
                    val seg = line.substring(9, 13).toIntOrNull(16) ?: 0
                    extSegAddr = seg shl 4
                }
                0x04 -> {
                    val upper = line.substring(9, 13).toIntOrNull(16) ?: 0
                    extLinearAddr = upper shl 16
                }
            }
        }

        if (sparseMap.isEmpty()) {
            return ParsedFirmware(ByteArray(0), 0, "Empty Intel HEX")
        }

        val minAddr = sparseMap.firstKey()
        val maxAddr = sparseMap.lastKey()
        val totalSize = (maxAddr - minAddr + 1).coerceAtMost(2 * 1024 * 1024)
        val out = ByteArrayOutputStream(totalSize)
        for (idx in 0 until totalSize) {
            out.write((sparseMap[minAddr + idx] ?: 0xFF.toByte()).toInt() and 0xFF)
        }
        return ParsedFirmware(out.toByteArray(), minAddr, "Intel HEX (.hex)")
    }
}
