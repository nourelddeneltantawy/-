package com.example.adminlab

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProberTargetFamily(val labelAr: String) {
    AUTO_DETECT("اكتشاف شامل تلقائي لأي مبرمجة وأي شركة (Multi-Programmer Auto-ID)"),
    NEC_78K0("NEC / Renesas 78K0 / RL78 / V850 / RX"),
    FREESCALE_HCS08("Freescale / NXP HCS08 / HCS12 / ColdFire / RS08 / Kinetis"),
    SAMSUNG_S3F("Samsung S3F8 / S3F9 / S3P ISP"),
    RENESAS_R8C_RL78("Renesas R8C / M16C / H8 / SH"),
    STM32_UART("STM32 / STM8 (ST-LINK SWD/SWIM & UART 0x7F)"),
    GENERIC_UART("AVR / PIC / USBasp / TI / Infineon / NXP / ESP / مخصص")
}

enum class ProberUsbChip(
    val displayName: String,
    val categoryLabelAr: String,
    val vendorIds: List<Int>,
    val productIds: List<Int> = emptyList()
) {
    USBDM("USBDM BDM/SWD/JTAG Programmer (Multi-Arch)", "مبرمجة USBDM متعددة المعماريات", listOf(0x16D0), listOf(0x0567)),
    ST_LINK("STMicroelectronics ST-LINK (V2 / V2-1 / V3)", "مبرمجة ST-LINK SWD/SWIM", listOf(0x0483), listOf(0x3744, 0x3748, 0x374A, 0x374B, 0x374D, 0x374E, 0x374F, 0x3752, 0x3753, 0x3754)),
    USBASP("USBasp AVR ISP Programmer (EP0 Control)", "مبرمجة USBasp (AVR SPI)", listOf(0x16C0), listOf(0x05DC)),
    ATMEL_AVR("Atmel / Microchip ISP & CMSIS-DAP (AVRISP / Atmel-ICE)", "مبرمجة Atmel / AVR / CMSIS-DAP", listOf(0x03EB)),
    MICROCHIP_PICKIT("Microchip PICkit / SNAP ICSP Programmer", "مبرمجة Microchip PICkit", listOf(0x04D8)),
    FT232R("FTDI FT232R / FT232RL (Red UART)", "محول UART (FT232)", listOf(0x0403)),
    CH340G("WCH CH340G / CH341", "محول UART (CH340)", listOf(0x1A86)),
    CP2102("Silicon Labs CP2102 / CP210x", "محول UART (CP2102)", listOf(0x10C4)),
    PL2303("Prolific PL2303", "محول UART (PL2303)", listOf(0x067B)),
    CDC_ACM("USB CDC-ACM / Virtual Serial UART", "محول UART قياسي CDC-ACM", emptyList()),
    UNKNOWN_USB("مبرمجة USB غير مسجلة / مخصصة (Unknown USB Adapter)", "مبرمجة USB غير مسجلة", emptyList());

    val isSpecializedProgrammer: Boolean
        get() = this == USBDM || this == ST_LINK || this == USBASP || this == ATMEL_AVR || this == MICROCHIP_PICKIT

    companion object {
        fun fromDevice(vid: Int, pid: Int, hasCdcInterface: Boolean = true): ProberUsbChip {
            if (vid == 0x16D0 && pid == 0x0567) return USBDM
            if (vid == 0x16C0 && pid == 0x05DC) return USBASP
            if (vid == 0x0483 && (pid in ST_LINK.productIds || (pid ushr 8) == 0x37)) return ST_LINK
            val vidMatch = entries.firstOrNull { it.productIds.isEmpty() && vid in it.vendorIds }
            if (vidMatch != null) return vidMatch
            return if (hasCdcInterface) CDC_ACM else UNKNOWN_USB
        }

        fun fromVendorId(vid: Int): ProberUsbChip =
            entries.firstOrNull { it.productIds.isEmpty() && vid in it.vendorIds } ?: CDC_ACM
    }
}

enum class UsbdmTargetArch(
    val targetCode: Int,
    val archName: String,
    val memSpace: Int,
    val idRegisterAddress: Long,
    val idByteLength: Int,
    val idRegisterName: String,
    val defaultRamAddress: Long,
    val flashBaseCandidates: List<Long>,
    val isArmOr32Bit: Boolean,
    val eraseWriteSpec: String
) {
    HCS08(
        targetCode = 0x00,
        archName = "Freescale / NXP HCS08",
        memSpace = 0x01,
        idRegisterAddress = 0x1806L,
        idByteLength = 2,
        idRegisterName = "SDIDH/SDIDL (0x1806..0x1807)",
        defaultRamAddress = 0x0080L,
        flashBaseCandidates = listOf(0xC000L, 0x8000L, 0xE000L),
        isArmOr32Bit = false,
        eraseWriteSpec = "HCS08 Flash Regs: FCLKDIV = 0x1820, FSTAT = 0x1825, FCMD = 0x1826 (0x41 Mass Erase, 0x40 Page Erase, 0x20 Byte Program)"
    ),
    HCS12(
        targetCode = 0x01,
        archName = "Freescale / NXP HCS12 (9S12)",
        memSpace = 0x01,
        idRegisterAddress = 0x001AL,
        idByteLength = 2,
        idRegisterName = "PARTIDH/PARTIDL (0x001A..0x001B)",
        defaultRamAddress = 0x1000L,
        flashBaseCandidates = listOf(0xC000L, 0x8000L, 0x4000L),
        isArmOr32Bit = false,
        eraseWriteSpec = "HCS12 FCLKDIV (0x0100) + FCMD (0x0106): 0x41 Mass Erase / 0x40 Sector Erase / 0x20 Word Program"
    ),
    COLDFIRE_V1(
        targetCode = 0x02,
        archName = "Freescale / NXP ColdFire V1 (MCF51)",
        memSpace = 0x04,
        idRegisterAddress = 0xFFFF8006L,
        idByteLength = 4,
        idRegisterName = "SDID (0xFFFF8006)",
        defaultRamAddress = 0x00800000L,
        flashBaseCandidates = listOf(0x00000000L),
        isArmOr32Bit = true,
        eraseWriteSpec = "ColdFire V1 FCMD (0xFFFF82E6): 0x41 Mass Erase / 0x40 Sector Erase / 0x20 Longword Program"
    ),
    RS08(
        targetCode = 0x04,
        archName = "Freescale RS08",
        memSpace = 0x01,
        idRegisterAddress = 0x0206L,
        idByteLength = 2,
        idRegisterName = "SDIDH/SDIDL (0x0206..0x0207)",
        defaultRamAddress = 0x0020L,
        flashBaseCandidates = listOf(0x3800L, 0x3C00L, 0x3E00L),
        isArmOr32Bit = false,
        eraseWriteSpec = "RS08 NVFCMD: 0x41 Mass Erase / 0x40 Page Erase / 0x20 Byte Program (14-bit address space up to 0x3FFF)"
    ),
    ARM_SWD(
        targetCode = 0x07,
        archName = "ARM Cortex-M SWD (NXP Kinetis / STM32 / Non-Freescale)",
        memSpace = 0x04,
        idRegisterAddress = 0x40048024L,
        idByteLength = 4,
        idRegisterName = "SIM_SDID (0x40048024) / DBGMCU_IDCODE (0xE0042000) / CPUID (0xE000ED00)",
        defaultRamAddress = 0x20000000L,
        flashBaseCandidates = listOf(0x00000000L, 0x08000000L),
        isArmOr32Bit = true,
        eraseWriteSpec = "ARM SWD FTFL/FTFA (Kinetis 0x40020000) or STM32 FLASH_CR (0x40022000) Sector Erase & Word/Halfword Program"
    ),
    COLDFIRE_V2_4(
        targetCode = 0x03,
        archName = "Freescale ColdFire V2/V3/V4 (BDM/JTAG)",
        memSpace = 0x04,
        idRegisterAddress = 0x40110010L,
        idByteLength = 4,
        idRegisterName = "CIR/JTAG IDCODE",
        defaultRamAddress = 0x20000000L,
        flashBaseCandidates = listOf(0x00000000L),
        isArmOr32Bit = true,
        eraseWriteSpec = "ColdFire V2-4 CFM (ColdFire Flash Module) Sector/Mass Erase & 32-bit Program"
    ),
    DSC_JTAG(
        targetCode = 0x08,
        archName = "Freescale 56800E DSC (JTAG)",
        memSpace = 0x04,
        idRegisterAddress = 0x00000000L,
        idByteLength = 4,
        idRegisterName = "DSC JTAG IDCODE",
        defaultRamAddress = 0x00000000L,
        flashBaseCandidates = listOf(0x00000000L),
        isArmOr32Bit = true,
        eraseWriteSpec = "56800E DSC HFM (Host Flash Module) Mass/Page Erase & Word Program via JTAG"
    );

    companion object {
        val scanPriorityOrder: List<UsbdmTargetArch> = listOf(
            HCS08, HCS12, COLDFIRE_V1, RS08, ARM_SWD, COLDFIRE_V2_4, DSC_JTAG
        )
    }
}

data class ProberUsbStatus(
    val isConnected: Boolean = false,
    val adapterName: String = "لا توجد مبرمجة متصلة",
    val chipModel: String = "غير محدد",
    val programmerCategory: String = "غير محدد",
    val vidPidHex: String = "--",
    val endpointsSummary: String = "--",
    val usbInterfaceInfo: String = "--",
    val hasPermission: Boolean = false,
    val deviceCount: Int = 0,
    val isUsbdm: Boolean = false,
    val chip: ProberUsbChip = ProberUsbChip.CDC_ACM
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

data class AdapterInitSpec(
    val adapterType: String = "--",
    val vidPid: String = "--",
    val usbTransportMode: String = "--",
    val usbEndpoints: String = "--",
    val powerControlLogic: String = "--",
    val initPacketSequence: List<String> = emptyList()
)

data class AutoChipDetectionRule(
    val targetFamilyDetected: String = "--",
    val identificationSequence: String = "--",
    val idRegisterOrOpcode: String = "--",
    val rawSiliconIdHex: String = "--",
    val dynamicFlashBoundaryFormula: String = "--",
    val dynamicSpeedFormula: String = "--"
)

data class PlugAndPlayRegistryEntry(
    val protocolId: String = "--",
    val familyDisplayName: String = "--",
    val sampleChipDatabaseKey: String = "--",
    val supportedAdapterVidPids: List<String> = emptyList(),
    val autoDetectProbeRule: String = "--"
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
        label = "USBDM V4.x Native Multi-Arch (HCS08/HCS12/ColdFire/RS08/ARM SWD)",
        sohHex = "USBDM [LEN, CMD]",
        stxHex = "BDM / SWD Memory Block",
        etbHex = "N/A",
        etxHex = "0x00 (BDM_RC_OK)",
        ackHex = "0x00 (BDM_RC_OK)",
        errCmdHex = "0x01+ (BDM_RC_ERR)",
        errParamHex = "0x12 (NO_TX_RX -> RESET_SPECIAL [03 13 00])",
        endianness = "Arch-Dependent (Big-Endian HCS08/12/CF | Little-Endian ARM SWD)",
        checksumFormula = "Native USB Bulk Framing + Target Flash Controller"
    ),
    STLINK_SWD(
        label = "STMicroelectronics ST-LINK SWD (ARM Cortex-M)",
        sohHex = "ST-LINK [0xF2, CMD]",
        stxHex = "SWD 32-bit Mem Block",
        etbHex = "N/A",
        etxHex = "0x80 (STLINK_OK)",
        ackHex = "0x80 (STLINK_DEBUG_ERR_OK)",
        errCmdHex = "0x81+",
        errParamHex = "SWD FAULT / WAIT",
        endianness = "Little-Endian (32-bit ARM Cortex-M)",
        checksumFormula = "Native ST-LINK USB Bulk Packets (EP OUT -> EP IN 0x81)"
    ),
    STLINK_SWIM(
        label = "STMicroelectronics ST-LINK SWIM (STM8 Single-Wire)",
        sohHex = "ST-LINK [0xF4, SWIM_CMD]",
        stxHex = "SWIM Memory Stream",
        etbHex = "N/A",
        etxHex = "0x00 (SWIM_OK)",
        ackHex = "0x00 (SWIM_OK)",
        errCmdHex = "0x01+",
        errParamHex = "SWIM NACK",
        endianness = "Big-Endian (STM8 24-bit Addr)",
        checksumFormula = "Native ST-LINK SWIM USB Bulk Framing"
    ),
    AVR_ISP_USB(
        label = "AVR / Atmel USB ISP (USBasp EP0 / STK500v2 / CMSIS-DAP)",
        sohHex = "USB Control / STK500 0x1B / DAP",
        stxHex = "SPI 4-Byte Prog Commands",
        etbHex = "N/A",
        etxHex = "0x00 / STATUS_CMD_OK",
        ackHex = "0x53 (SPI Echo) / 0x00",
        errCmdHex = "0x01+",
        errParamHex = "Sync Mismatch",
        endianness = "Big-Endian Word/Byte Addressing",
        checksumFormula = "AVR 4-Byte SPI Program/Read + 3-Byte Device Signature (0x30)"
    ),
    MICROCHIP_PIC_ICSP(
        label = "Microchip PICkit / SNAP ICSP Protocol",
        sohHex = "HID/Bulk Packet",
        stxHex = "ICSP Script Payload",
        etbHex = "N/A",
        etxHex = "Status Byte",
        ackHex = "0x00 / FW Version",
        errCmdHex = "0xFF",
        errParamHex = "ICSP Timeout",
        endianness = "Little-Endian (14/16/24-bit Flash Word)",
        checksumFormula = "Microchip ICSP Serial Command Stream"
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
        label = "بروتوكول قياسي / نصي / مبرمجة غير مسجلة (Custom / Unknown)",
        sohHex = "Raw / USB / ASCII / SLIP",
        stxHex = "Raw",
        etbHex = "Raw",
        etxHex = "CR/LF or Delim",
        ackHex = "Raw / OK / 0x14 0x10",
        errCmdHex = "Raw",
        errParamHex = "Raw",
        endianness = "Vendor-Specific",
        checksumFormula = "Vendor-Specific / Dynamic Endpoint Probe"
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
    val usbdmTargetArch: UsbdmTargetArch? = null,
    val adapterInitSpec: AdapterInitSpec = AdapterInitSpec(),
    val autoDetectionRule: AutoChipDetectionRule = AutoChipDetectionRule(),
    val plugAndPlayRegistryEntry: PlugAndPlayRegistryEntry = PlugAndPlayRegistryEntry(),
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
    val flashStartAddressHex: String = "0x000000",
    val safeDelayMs: Int? = null,
    val timingMap: TimingMapMs = TimingMapMs(),
    val discoveredCommands: List<DiscoveredCommandEntry> = emptyList(),
    val ignoredNoiseCommandsCount: Int = 0,
    val stage5Results: List<Stage5StepResult> = emptyList(),
    val advancedSchoolsNotes: String = "",
    val hasPartialBypass: Boolean = false,
    val liveReadProofPassed: Boolean = false,
    val liveReadProofMode: String = "--",
    val liveReadProofStatus: String = "لم يُختبر بعد",
    val liveReadProofFirst32Hex: String = "--"
)

data class ProberUiState(
    val usbStatus: ProberUsbStatus = ProberUsbStatus(),
    val selectedFamily: ProberTargetFamily = ProberTargetFamily.AUTO_DETECT,
    val phase: ProberStagePhase = ProberStagePhase.IDLE,
    val stageBanner: String = "جاهز لبدء الفحص العالمي الشامل لأي مبرمجة وأي معالج",
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
    ChecklistStepItem("stage1_handshake", "المرحلة 1", "موجه المبرمجات الشامل + ماسح USBDM لـ 7 معماريات + الهوية الخام (Raw ID)"),
    ChecklistStepItem("stage2_direct_read", "المرحلة 2.1", "مدرسة القراءة المباشرة (0x11, 0x03, 0x18, 0x74, USBDM, ST-LINK, USBasp)"),
    ChecklistStepItem("stage2_checksum_1b", "المرحلة 2.2", "مدرسة السحب عبر بصمة بايت واحد (0xB0 على 1B ثم 2B)"),
    ChecklistStepItem("stage2_verify_0x13", "المرحلة 2.3", "مدرسة التحقق التدريجي (Verify 0x13: مقارنة صفحة 0xFF مع 0x00 لكشف القفل)"),
    ChecklistStepItem("stage2_signature", "المرحلة 2.4", "مدرسة قراءة الهوية الخام بدون قوائم مغلقة (SDID / PARTID / IDCODE / Signature)"),
    ChecklistStepItem("stage2_ram_stub", "المرحلة 2.5", "مدرسة حقن/فحص الرام (RAM Stub في عنوان مؤقت آمن)"),
    ChecklistStepItem("stage3_cmd_sweep", "المرحلة 3", "المسح الجراحي الشامل للأوامر أو أوامر المبرمجة وأطوال المعاملات"),
    ChecklistStepItem("stage4_timing_bounds", "المرحلة 4", "فحص حدود الفلاش الديناميكي (4KB..2MB) + التردد 0x90 + السرعات + 6 أزمنة"),
    ChecklistStepItem("stage5_safety_gate", "المرحلة 5", "بوابة الأمان التفاعلية (تصفير Block 0 / Mass Erase / الكتابة الجزئية)"),
    ChecklistStepItem("stage6_live_read_proof", "المرحلة 6", "إثبات صحة السحب الفعلي (Universal Live Read Proof — عينة مليئة أو فارغة 0xFF)")
)

private fun jsonEsc(v: String): String = v
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "\\n")

private fun appendAdapterInitJson(sb: StringBuilder, s: ProberBlueprintSummary, indent: String = "  ") {
    val a = s.adapterInitSpec
    sb.appendLine("$indent\"adapterInitializationSpec\": {")
    sb.appendLine("$indent  \"adapterType\": \"${jsonEsc(a.adapterType)}\",")
    sb.appendLine("$indent  \"vidPid\": \"${jsonEsc(a.vidPid)}\",")
    sb.appendLine("$indent  \"usbTransportMode\": \"${jsonEsc(a.usbTransportMode)}\",")
    sb.appendLine("$indent  \"usbEndpoints\": \"${jsonEsc(a.usbEndpoints)}\",")
    sb.appendLine("$indent  \"powerControlLogic\": \"${jsonEsc(a.powerControlLogic)}\",")
    sb.appendLine("$indent  \"initPacketSequence\": [")
    a.initPacketSequence.forEachIndexed { idx, p ->
        val comma = if (idx < a.initPacketSequence.lastIndex) "," else ""
        sb.appendLine("$indent    \"${jsonEsc(p)}\"$comma")
    }
    sb.appendLine("$indent  ]")
    sb.appendLine("$indent},")
}

private fun appendAutoDetectionRuleJson(sb: StringBuilder, s: ProberBlueprintSummary, indent: String = "  ") {
    val r = s.autoDetectionRule
    sb.appendLine("$indent\"automaticChipDetectionRule\": {")
    sb.appendLine("$indent  \"targetFamilyDetected\": \"${jsonEsc(r.targetFamilyDetected)}\",")
    sb.appendLine("$indent  \"identificationSequence\": \"${jsonEsc(r.identificationSequence)}\",")
    sb.appendLine("$indent  \"idRegisterOrOpcode\": \"${jsonEsc(r.idRegisterOrOpcode)}\",")
    sb.appendLine("$indent  \"rawSiliconIdHex\": \"${jsonEsc(r.rawSiliconIdHex)}\",")
    sb.appendLine("$indent  \"dynamicFlashBoundaryFormula\": \"${jsonEsc(r.dynamicFlashBoundaryFormula)}\",")
    sb.appendLine("$indent  \"dynamicSpeedFormula\": \"${jsonEsc(r.dynamicSpeedFormula)}\"")
    sb.appendLine("$indent},")
}

private fun appendPlugAndPlayRegistryJson(sb: StringBuilder, s: ProberBlueprintSummary, indent: String = "  ") {
    val p = s.plugAndPlayRegistryEntry
    sb.appendLine("$indent\"plugAndPlayRegistryEntry\": {")
    sb.appendLine("$indent  \"protocolId\": \"${jsonEsc(p.protocolId)}\",")
    sb.appendLine("$indent  \"familyDisplayName\": \"${jsonEsc(p.familyDisplayName)}\",")
    sb.appendLine("$indent  \"sampleChipDatabaseKey\": \"${jsonEsc(p.sampleChipDatabaseKey)}\",")
    sb.appendLine("$indent  \"supportedAdapterVidPids\": [${p.supportedAdapterVidPids.joinToString(", ") { "\"${jsonEsc(it)}\"" }}],")
    sb.appendLine("$indent  \"autoDetectProbeRule\": \"${jsonEsc(p.autoDetectProbeRule)}\"")
    sb.appendLine("$indent},")
}

private fun buildWorkflows(state: ProberUiState): Pair<List<String>, List<String>> {
    val s = state.blueprintSummary
    val framing = s.framing
    val tm = s.timingMap
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }

    if (framing == DetectedFraming.USBDM_BDM) {
        val arch = s.usbdmTargetArch ?: UsbdmTargetArch.HCS08
        val readWf = listOf(
            "1. Adapter Setup: Open USBDM V4.x USB Bulk (EP1 OUT / EP2 IN), GET_CAPABILITIES, SET_VDD 5V [03 02 02] + delay(100), Internal Power [03 03 01].",
            "2. 7-Arch Scan & RESET_SPECIAL: SET_TARGET_TYPE [03 01 %02X] (${arch.archName}) -> RESET_SPECIAL [03 13 00] -> delay(50) -> CONNECT [02 0F] -> TARGET_HALT [02 14].".format(arch.targetCode),
            "3. Raw Silicon ID & 4KB..2MB Bounds: Read ${arch.idRegisterName} (space=0x%02X) -> RawID=${s.chipSignature} | Probed Range=${s.flashStartAddressHex}..${s.flashEndAddressHex}.".format(arch.memSpace),
            if (!isLocked) {
                "4. Safe Read: Read Flash ${s.flashStartAddressHex}..${s.flashEndAddressHex} via ${s.winningReadMethod} in ${s.pageSizeBytes ?: 256}B chunks."
            } else {
                "4. Target Flash is SECURED/PROTECTED: Requires Mass Erase / Security Unlock before clean extraction."
            }
        )
        val writeWf = listOf(
            "1. Connect USBDM target ${arch.archName} (0x%02X) using RESET_SPECIAL [03 13 00] + CONNECT [02 0F] + HALT [02 14].".format(arch.targetCode),
            "2. Erase Flash (${arch.archName}): ${arch.eraseWriteSpec}, waiting ${tm.eraseWriteBusyWaitMs}ms.",
            "3. Program Flash from ${s.flashStartAddressHex}..${s.flashEndAddressHex} in ${s.pageSizeBytes ?: 256}B blocks and verify read-back."
        )
        return readWf to writeWf
    }

    if (framing == DetectedFraming.STLINK_SWD || framing == DetectedFraming.STLINK_SWIM) {
        val mode = if (framing == DetectedFraming.STLINK_SWD) "SWD (ARM Cortex-M)" else "SWIM (STM8)"
        val readWf = listOf(
            "1. Adapter Setup: Open ST-LINK USB Bulk (EP OUT -> EP IN 0x81), query Version [0xF1 0x80], exit DFU [0xF3 0x07], enter $mode.",
            "2. Raw Silicon ID & 4KB..2MB Bounds: Read IDCODE/SWIM ID -> ${s.chipSignature} | Range=${s.flashStartAddressHex}..${s.flashEndAddressHex}.",
            if (!isLocked) {
                "3. Safe Read: Read Flash ${s.flashStartAddressHex}..${s.flashEndAddressHex} via ST-LINK MemRead in ${s.pageSizeBytes ?: 256}B blocks."
            } else {
                "3. Target RDP/ROP Locked: Readout protection active — requires Option Byte Mass Erase unprotect."
            }
        )
        val writeWf = listOf(
            "1. Initialize ST-LINK in $mode and halt target core.",
            "2. Unlock FLASH_CR / OPT and erase sectors or Mass Erase (wait ${tm.eraseWriteBusyWaitMs}ms).",
            "3. Write ${s.pageSizeBytes ?: 256}B blocks from ${s.flashStartAddressHex} and verify read-back."
        )
        return readWf to writeWf
    }

    if (framing == DetectedFraming.AVR_ISP_USB || framing == DetectedFraming.MICROCHIP_PIC_ICSP) {
        val readWf = listOf(
            "1. Adapter Setup: ${s.adapterInitSpec.adapterType} (${s.adapterInitSpec.usbTransportMode}) -> ${s.adapterInitSpec.initPacketSequence.joinToString(" -> ")}.",
            "2. Raw Signature & 4KB..2MB Bounds: Read 3-Byte / Device ID -> ${s.chipSignature} | Probed Range=${s.flashStartAddressHex}..${s.flashEndAddressHex}.",
            if (!isLocked) {
                "3. Read Flash Memory ${s.flashStartAddressHex}..${s.flashEndAddressHex} in ${s.pageSizeBytes ?: 128}B pages."
            } else {
                "3. Lock/Fuse Bits Active: Read blocked — requires Chip Erase to clear lock bits."
            }
        )
        val writeWf = listOf(
            "1. Enter ISP/ICSP programming mode.",
            "2. Execute Chip Erase (wait ${tm.eraseWriteBusyWaitMs}ms).",
            "3. Page-load and program ${s.pageSizeBytes ?: 128}B pages from ${s.flashStartAddressHex}, then verify Flash & Fuses."
        )
        return readWf to writeWf
    }

    if (framing == DetectedFraming.FREESCALE_SINGLE_WIRE_BDM) {
        val readWf = listOf(
            "1. Open FT232 at 1,500,000 bps (8N1) Single-Wire BKGD (TX+1kΩ+RX) and send BDM Sync (0xFE/0xC0).",
            "2. Read Raw HCS08 SDID (0x1806..0x1807) -> ${s.chipSignature} | Range=${s.flashStartAddressHex}..${s.flashEndAddressHex}.",
            if (!isLocked) {
                "3. Safe BDM Memory Read: Read Flash up to ${s.flashEndAddressHex} via ${s.winningReadMethod} in ${s.pageSizeBytes ?: 256}B chunks."
            } else {
                "3. Target Flash is SECURED (FSEC locked): Cannot read directly without Mass Erase (FCMD 0x41) security unlock."
            }
        )
        val writeWf = listOf(
            "1. Connect via ${framing.label} and sync bus clock (${s.detectedCrystalMhz}).",
            "2. Erase Flash via HCS08 FCLKDIV = 0x1820, FSTAT = 0x1825, FCMD = 0x1826: 0x41 (Mass Erase) or 0x40 (Page Erase), waiting ${tm.eraseWriteBusyWaitMs}ms.",
            "3. Program Flash via FCMD = 0x20 (Byte Program) in ${s.pageSizeBytes ?: 64}B blocks and verify read-back."
        )
        return readWf to writeWf
    }

    if (framing == DetectedFraming.SAMSUNG_ISP) {
        val readWf = listOf(
            "1. Hold DTR (Vpp/TEST)=LOW & RTS (SCLK)=LOW (50ms) -> Raise DTR=+5V (20ms) -> 16 SCLK pulses (2ms) -> Send 0xAA 0x55 over SDAT (TX+1N4148+RX).",
            "2. Query Samsung Raw Chip ID (0x80 / 0xC0 / 0x01) -> ${s.chipSignature} (${s.inferredModel}).",
            if (!isLocked) {
                "3. Extract Flash ${s.flashStartAddressHex}..${s.flashEndAddressHex} via ${s.winningReadMethod} in ${s.pageSizeBytes ?: 256}B pages."
            } else {
                "3. Target Readout Protected: Execute Samsung S3F8/S3F9 Erase/Unprotect before reprogramming."
            }
        )
        val writeWf = listOf(
            "1. Enter Samsung S3F8/S3F9 Sync ISP (DTR=Vpp/TEST, RTS=SCLK, SDAT=TX/RX) & confirm ${s.syncPulseHex}.",
            "2. Erase Sectors/Chip via Samsung ISP Erase command (wait ${tm.eraseWriteBusyWaitMs}ms).",
            "3. Stream ${s.pageSizeBytes ?: 256}B pages with SCLK sync and verify against Flash."
        )
        return readWf to writeWf
    }

    val readWorkflow = buildList {
        add("1. Apply Pin Logic: FLMD0/MODE=${s.flmd0PinState}, RESET Idle=${s.resetPinIdleState}, Pulse=${s.resetPinPulsePolarity} (${tm.resetPulseMs}ms -> wait ${tm.postResetDelayMs}ms).")
        add("2. Open Adapter/UART (${s.baudRate ?: 9600} bps ${s.parityMode}, echoCancellation=${s.echoCancellation}); run wake-up pattern [${s.wakeUpPatternUsed}] (${s.syncPulseHex}).")
        add("3. Run Auto-ID (${s.universalAutoIdRule}) -> RawID=${s.chipSignature} & Dynamic Flash Probe 4KB..2MB -> ${s.flashStartAddressHex}..${s.flashEndAddressHex}.")
        add("4. Crystal/Oscillator Lock: ${s.oscillatorNegotiationRule}")
        add("5. Speed Transition: ${s.baudStateMachineRule} (wait ${tm.baudSwitchDelayMs}ms after ACK).")
        if (!isLocked) {
            add("6. Safe Direct Extraction succeeded via ${s.winningReadMethod}: read ${s.flashStartAddressHex}..${s.flashEndAddressHex} in ${s.pageSizeBytes ?: 256}B pages (${tm.frameToFrameDelayMs}ms frame gap).")
        } else if (s.hasPartialBypass) {
            add("6. Winning Non-Standard Extraction Path (Block-0 Bypass + 0xB0/0x13): Block-0 zeroed/partially bypassed (${s.verify0x13DiffResult}).")
            add("7. Extract remaining protected Flash blocks starting at Block 2 (0x0400..${s.flashEndAddressHex}) via 0xB0 Checksum + 0x13 Verify probes.")
        } else {
            add("6. Target is LOCKED (${s.verify0x13DiffResult}): capture Full Flash & Block-0 Checksums via 0xB0 before any destructive step.")
            add("7. Zero Block-0 (0x22) -> extract Block-0 via 0xB0 differential if supported -> extract remaining blocks -> restore Block-0 (0x40) -> verify Full Flash Checksum (0xB0).")
        }
    }

    val writeWorkflow = buildList {
        add("1. Apply Pin Logic: FLMD0/MODE=${s.flmd0PinState}, RESET Pulse=${tm.resetPulseMs}ms, settle=${tm.postResetDelayMs}ms.")
        add("2. Connect & Wake: ${s.baudRate ?: 9600} bps ${s.parityMode}; execute wake-up (${s.syncPulseHex}) and confirm ACK (${framing?.ackHex ?: "0x06"}).")
        add("3. Auto-ID & Geometry: ${s.universalAutoIdRule} -> Range=${s.flashStartAddressHex}..${s.flashEndAddressHex}, Block=${s.blockSizeBytes ?: 1024}B, Page=${s.pageSizeBytes ?: 256}B.")
        add("4. Lock Oscillator & Speed: ${s.oscillatorNegotiationRule} (winning=${s.winningOscillatorPayload}) | ${s.baudStateMachineRule}.")
        add("5. Blank Check / Erase: send 0x32 (Blank Check) or Erase via 0x20 (Chip Erase) / 0x22 (Block Erase) and wait ${tm.eraseWriteBusyWaitMs}ms busy timeout.")
        add("6. Program Flash (0x40): send Start..End (${framing?.endianness ?: "Big-Endian"}), wait ${tm.commandToDataDelayMs}ms, stream STX (0x02) frames of ${s.pageSizeBytes ?: 256}B ending with ETB (0x17) on intermediate pages and ETX (0x03) on final page (${tm.frameToFrameDelayMs}ms gap).")
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
        appendAdapterInitJson(this, s)
        appendAutoDetectionRuleJson(this, s)
        appendPlugAndPlayRegistryJson(this, s)
        appendLine("  \"targetIdentity\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"usbdmArchitecture\": \"${jsonEsc(s.usbdmTargetArch?.archName ?: "--")}\",")
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
        appendLine("    \"flashStartAddress\": \"${jsonEsc(s.flashStartAddressHex)}\",")
        appendLine("    \"flashEndAddress\": \"${jsonEsc(s.flashEndAddressHex)}\",")
        appendLine("    \"flashSizeDetectionRule\": \"${jsonEsc(s.flashSizeDetectionRule)}\",")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256},")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024}")
        appendLine("  },")
        appendLine("  \"readExtractionStatus\": {")
        appendLine("    \"isLockedForSafeRead\": $isLocked,")
        appendLine("    \"hasPartialBypass\": ${s.hasPartialBypass},")
        appendLine("    \"ignoredNoiseCommandsCount\": ${s.ignoredNoiseCommandsCount},")
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
        appendAdapterInitJson(this, s)
        appendAutoDetectionRuleJson(this, s)
        appendPlugAndPlayRegistryJson(this, s)
        appendLine("  \"targetIdentity\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"usbdmArchitecture\": \"${jsonEsc(s.usbdmTargetArch?.archName ?: "--")}\",")
        appendLine("    \"flashStartAddress\": \"${jsonEsc(s.flashStartAddressHex)}\",")
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
        appendLine("  \"hasPartialBypass\": ${s.hasPartialBypass},")
        appendLine("  \"ignoredNoiseCommandsCount\": ${s.ignoredNoiseCommandsCount},")
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
    val a = s.adapterInitSpec
    val r = s.autoDetectionRule
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val isLocked = state.checklist.none { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val lockStatusAr = when {
        !isLocked -> "🔓 مفتوح للسحب الآمن (${s.winningReadMethod})"
        s.hasPartialBypass -> "🔓 مسار تجاوز جزئي نشط (Block-0 Bypass + 0xB0/0x13 @ 0x0400)"
        else -> "🔒 محمي ومقفل (Locked Bootloader / Secured — لا يسمح بالسحب المباشر الآمن)"
    }

    val stage2List = state.checklist.filter { it.id.startsWith("stage2_") }
    val extraCmds = s.discoveredCommands.joinToString("\n") { cmd ->
        val safeTag = if (cmd.isSafeReadOnly) "قراءة/استعلام آمن" else "كتابة/مسح/تعديل"
        "  • الأمر ${cmd.cmdHex} (معاملات LEN=${cmd.exactParamLen ?: "?"} | الرد=${cmd.replyStatusHex}) -> ${cmd.roleGuess} [$safeTag]"
    }.ifBlank { "  • لم يتم رصد أوامر إضافية حتى الآن." }

    return buildString {
        appendLine("══════════════════════════════════════════════")
        appendLine("0️⃣ مواصفات تهيئة المبرمجة وقاعدة التعرف التلقائي (Header Spec)")
        appendLine("══════════════════════════════════════════════")
        appendLine("▶ adapterInitializationSpec (تهيئة المبرمجة):")
        appendLine("  • نوع المبرمجة: ${a.adapterType} (${a.vidPid})")
        appendLine("  • وضع النقل والـ Endpoints: ${a.usbTransportMode} | ${a.usbEndpoints}")
        appendLine("  • منطق التغذية والأطراف: ${a.powerControlLogic}")
        a.initPacketSequence.forEachIndexed { idx, pkt ->
            appendLine("    ${idx + 1}. $pkt")
        }
        appendLine("▶ automaticChipDetectionRule (قاعدة الاكتشاف التلقائي للشريحة):")
        appendLine("  • العائلة المكتشفة: ${r.targetFamilyDetected}")
        appendLine("  • تسلسل التعرف: ${r.identificationSequence}")
        appendLine("  • عنوان/أمر الهوية: ${r.idRegisterOrOpcode}")
        appendLine("  • البصمة الخام (Raw Silicon ID): ${r.rawSiliconIdHex}")
        appendLine("  • معادلة حجم الفلاش (4KB..2MB): ${r.dynamicFlashBoundaryFormula}")
        appendLine("  • معادلة السرعة المثلى: ${r.dynamicSpeedFormula}")
        val pnp = s.plugAndPlayRegistryEntry
        appendLine("▶ plugAndPlayRegistryEntry (وحدة الإدراج التلقائي Plug & Play):")
        appendLine("  • protocolId: ${pnp.protocolId}")
        appendLine("  • familyDisplayName: ${pnp.familyDisplayName}")
        appendLine("  • sampleChipDatabaseKey: ${pnp.sampleChipDatabaseKey}")
        appendLine("  • supportedAdapterVidPids: ${pnp.supportedAdapterVidPids.joinToString(", ")}")
        appendLine("  • autoDetectProbeRule: ${pnp.autoDetectProbeRule}")
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("1️⃣ بطاقة هوية البروسيسور (MCU Identity Card)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• الشركة المصنعة: ${s.manufacturerGuess}")
        appendLine("• العائلة / البروتوكول: ${s.framing?.label ?: state.selectedFamily.labelAr}")
        if (s.usbdmTargetArch != null) {
            appendLine("• معمارية USBDM المكتشفة: ${s.usbdmTargetArch.archName} (Code=0x%02X | MemSpace=0x%02X)".format(s.usbdmTargetArch.targetCode, s.usbdmTargetArch.memSpace))
        }
        appendLine("• الموديل / البصمة المستخرجة: ${s.inferredModel}")
        appendLine("• التوقيع الخام (Raw Signature / ID): ${s.chipSignature.ifBlank { "لم يرد توقيع" }}")
        appendLine("• نطاق الفلاش المفحوص ديناميكياً (4KB..2MB): ${s.flashStartAddressHex} .. ${s.flashEndAddressHex}")
        appendLine("• حجم الصفحة (Page): ${s.pageSizeBytes ?: 256} بايت | حجم البلوك (Block): ${s.blockSizeBytes ?: 1024} بايت")
        appendLine("• التردد / الكريستالة المكتشفة: ${s.detectedCrystalMhz} (${s.winningOscillatorPayload})")
        appendLine("• حالة القفل والأمان: $lockStatusAr")
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("2️⃣ شرح خطوات السحب خطوة بخطوة (Read Workflow)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• تهيئة المبرمجة والخطوط: ${s.flmd0PinState} | RESET=${s.resetPinIdleState} (${s.resetPinPulsePolarity})")
        appendLine("• الاتصال والتزامن: ${s.parityMode} | السرعة=${s.baudRate ?: 9600} | نمط الاستيقاظ=${s.wakeUpPatternUsed}")
        appendLine("• التعرف التلقائي (Auto-ID): ${s.universalAutoIdRule}")
        appendLine("• نتائج اختبار مدارس السحب الخمس:")
        stage2List.forEach { step ->
            appendLine("  - ${step.title}: [${step.status.badgeText}] ${step.detail}")
        }
        appendLine("• الخلاصة العملية للسحب:")
        if (!isLocked) {
            appendLine("  يمكن سحب السوفت وير مباشرة وأمان 100% عبر (${s.winningReadMethod}) من ${s.flashStartAddressHex} حتى ${s.flashEndAddressHex}.")
        } else {
            appendLine("  المعالج مقفل/محمي (${s.verify0x13DiffResult}). يتطلب فتح القفل أو مسح البلوك/الرقاقة وفق مواصفة الكتابة والمسح.")
        }
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("3️⃣ شرح خطوات الكتابة والمسح والتحقق (Write & Erase)")
        appendLine("══════════════════════════════════════════════")
        appendLine("• ضبط التردد والمذبذب: ${s.oscillatorNegotiationRule}")
        appendLine("• تبديل السرعة: ${s.baudStateMachineRule}")
        appendLine("• المسح والكتابة والتحقق: ${s.usbdmTargetArch?.eraseWriteSpec ?: s.flashSizeDetectionRule} | زمن الانتظار=${tm.eraseWriteBusyWaitMs}ms")
        if (s.stage5Results.isNotEmpty()) {
            appendLine("• نتائج اختبارات المرحلة 5 التفاعلية:")
            s.stage5Results.forEach { res ->
                appendLine("  - ${res.stepTitle}: ${res.detail}")
            }
        }
        appendLine()

        appendLine("══════════════════════════════════════════════")
        appendLine("4️⃣ ملاحظات هندسية إضافية وأكواد الأخطاء")
        appendLine("══════════════════════════════════════════════")
        appendLine("• معادلة Checksum / Frame: ${s.framing?.checksumFormula ?: "غير محددة"}")
        appendLine("• أكواد الاستجابة: ACK=${s.framing?.ackHex ?: "0x06"} | ErrCmd=${s.framing?.errCmdHex ?: "0x04"} | ErrParam=${s.framing?.errParamHex ?: "0x05"}")
        appendLine("• جدول الأوامر المكتشفة (${s.discoveredCommands.size} أمر | أوامر الضوضاء المتجاهلة: ${s.ignoredNoiseCommandsCount}):")
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
        appendAdapterInitJson(this, s)
        appendAutoDetectionRuleJson(this, s)
        appendPlugAndPlayRegistryJson(this, s)
        appendLine("  \"processorIdentityCard\": {")
        appendLine("    \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("    \"family\": \"${jsonEsc(s.framing?.label ?: state.selectedFamily.labelAr)}\",")
        appendLine("    \"usbdmArchitecture\": \"${jsonEsc(s.usbdmTargetArch?.archName ?: "--")}\",")
        appendLine("    \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("    \"rawSignature\": \"${jsonEsc(s.chipSignature)}\",")
        appendLine("    \"flashStartAddress\": \"${jsonEsc(s.flashStartAddressHex)}\",")
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
        appendLine("  \"hasPartialBypass\": ${s.hasPartialBypass},")
        appendLine("  \"ignoredNoiseCommandsCount\": ${s.ignoredNoiseCommandsCount},")
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
        appendLine("  \"blueprintVersion\": \"admin-lab-universal-v7\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendAdapterInitJson(this, s)
        appendAutoDetectionRuleJson(this, s)
        appendPlugAndPlayRegistryJson(this, s)
        appendLine("  \"manufacturer\": \"${jsonEsc(s.manufacturerGuess)}\",")
        appendLine("  \"inferredModel\": \"${jsonEsc(s.inferredModel)}\",")
        appendLine("  \"universalAutoIdRule\": \"${jsonEsc(s.universalAutoIdRule)}\",")
        appendLine("  \"parityMode\": \"${jsonEsc(s.parityMode)}\",")
        appendLine("  \"wakeUpPatternUsed\": \"${jsonEsc(s.wakeUpPatternUsed)}\",")
        appendLine("  \"isLockedForSafeRead\": $isLocked,")
        appendLine("  \"hasPartialBypass\": ${s.hasPartialBypass},")
        appendLine("  \"ignoredNoiseCommandsCount\": ${s.ignoredNoiseCommandsCount},")
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
