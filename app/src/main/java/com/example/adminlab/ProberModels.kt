package com.example.adminlab

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProberTargetFamily(val labelAr: String) {
    AUTO_DETECT("اكتشاف شامل تلقائي (Auto-Detect)"),
    NEC_78K0("NEC / Renesas 78K0 (UPD78F0xxx)"),
    RENESAS_R8C_RL78("Renesas R8C / M16C / RL78"),
    STM32_UART("STM32 UART System Bootloader"),
    GENERIC_UART("بروتوكول مخصص / UART عام")
}

enum class ProberUsbChip(val displayName: String, val vendorIds: List<Int>) {
    FT232R("FTDI FT232R / FT232RL", listOf(0x0403)),
    CH340G("WCH CH340G / CH341", listOf(0x1A86)),
    CP2102("Silicon Labs CP2102 / CP210x", listOf(0x10C4)),
    PL2303("Prolific PL2303", listOf(0x067B)),
    CDC_ACM("USB CDC-ACM / Generic UART", emptyList());

    companion object {
        fun fromVendorId(vid: Int): ProberUsbChip =
            entries.firstOrNull { vid in it.vendorIds } ?: CDC_ACM
    }
}

data class ProberUsbStatus(
    val isConnected: Boolean = false,
    val adapterName: String = "لا توجد مبرمجة متصلة",
    val chipModel: String = "غير محدد",
    val vidPidHex: String = "--",
    val hasPermission: Boolean = false,
    val deviceCount: Int = 0
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
        label = "NEC / Renesas 78K0 (SOH/STX/ETB/ETX)",
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
        label = "STM32 USART Bootloader (0x79 ACK)",
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
        label = "Renesas R8C / RL78 Status Framing",
        sohHex = "Single-Byte Command",
        stxHex = "256B Raw Page",
        etbHex = "N/A",
        etxHex = "Status SRD1/SRD2",
        ackHex = "0x06 / SRD1",
        errCmdHex = "0xFF",
        errParamHex = "SRD ERR",
        endianness = "Little-Endian (20-bit/24-bit: A0, A1, A2)",
        checksumFormula = "Direct Single-Wire UART Framing"
    ),
    CUSTOM_RAW(
        label = "إطار خام / UART مخصص",
        sohHex = "Raw",
        stxHex = "Raw",
        etbHex = "Raw",
        etxHex = "Timeout/Delim",
        ackHex = "Raw",
        errCmdHex = "Raw",
        errParamHex = "Raw",
        endianness = "Unknown",
        checksumFormula = "Unknown / Raw UART"
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
    val maxBaudTested: Int? = null,
    val supportedBaudRates: List<Int> = emptyList(),
    val flmd0PinState: String = "--",
    val resetPinIdleState: String = "--",
    val resetPinPulsePolarity: String = "--",
    val dtrPolarity: String = "--",
    val rtsPolarity: String = "--",
    val echoCancellation: Boolean = false,
    val syncPulseHex: String = "--",
    val framing: DetectedFraming? = null,
    val verify0x13DiffResult: String = "--",
    val winningReadMethod: String = "--",
    val chipSignature: String = "",
    val flashSizeDetectionRule: String = "--",
    val oscillatorNegotiationRule: String = "--",
    val oscillatorProbeResults: String = "--",
    val baudStateMachineRule: String = "--",
    val supportedStxPageSizes: List<Int> = emptyList(),
    val pageSizeBytes: Int? = null,
    val blockSizeBytes: Int? = null,
    val flashEndAddressHex: String = "--",
    val safeDelayMs: Int? = null,
    val timingMap: TimingMapMs = TimingMapMs(),
    val discoveredCommands: List<DiscoveredCommandEntry> = emptyList(),
    val stage5Results: List<Stage5StepResult> = emptyList(),
    val advancedSchoolsNotes: String = ""
)

data class ProberUiState(
    val usbStatus: ProberUsbStatus = ProberUsbStatus(),
    val selectedFamily: ProberTargetFamily = ProberTargetFamily.AUTO_DETECT,
    val phase: ProberStagePhase = ProberStagePhase.IDLE,
    val stageBanner: String = "جاهز لبدء الفحص الشامل واستخراج البروتوكول",
    val checklist: List<ChecklistStepItem> = defaultChecklist(),
    val blueprintSummary: ProberBlueprintSummary = ProberBlueprintSummary(),
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
    ChecklistStepItem("stage1_handshake", "المرحلة 1", "هندسة التوصيل والتزامن والقطبيات (FLMD0/RESET HIGH/LOW، Echo، الإطار)"),
    ChecklistStepItem("stage2_direct_read", "المرحلة 2.1", "مدرسة القراءة المباشرة (0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF)"),
    ChecklistStepItem("stage2_checksum_1b", "المرحلة 2.2", "مدرسة السحب عبر بصمة بايت واحد (0xB0 على 1B ثم 2B)"),
    ChecklistStepItem("stage2_verify_0x13", "المرحلة 2.3", "مدرسة التحقق التدريجي (Verify 0x13: مقارنة صفحة 0xFF مع 0x00 لكشف القفل)"),
    ChecklistStepItem("stage2_signature", "المرحلة 2.4", "مدرسة قراءة التوقيع والذاكرة المفتوحة (0xC0, 0xC5, 0x01, 0x02)"),
    ChecklistStepItem("stage2_ram_stub", "المرحلة 2.5", "مدرسة حقن الكود في الرام (RAM Stub في عنوان مؤقت آمن)"),
    ChecklistStepItem("stage3_cmd_sweep", "المرحلة 3", "المسح الجراحي الشامل للأوامر 0x00..0xFF وأطوال المعاملات LEN 0..8"),
    ChecklistStepItem("stage4_timing_bounds", "المرحلة 4", "الخوارزميات الديناميكية للعائلة (Flash End، 0x90 Osc، 0x9A Baud، STX، 6-Point Timing)"),
    ChecklistStepItem("stage5_safety_gate", "المرحلة 5", "بوابة الأمان التفاعلية (تصفير Block 0، الكتابة الجزئية، والمسح الكلي 0x20)")
)

fun formatAiReadyBlueprintJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val readPasses = state.checklist.filter { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val isLocked = readPasses.isEmpty()
    val framing = s.framing
    val tm = s.timingMap

    fun esc(v: String): String = v
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    val readWorkflow = buildList {
        add("1. Apply pin logic: FLMD0/MODE=${s.flmd0PinState}, RESET Idle=${s.resetPinIdleState}, Pulse=${s.resetPinPulsePolarity} for ${tm.resetPulseMs}ms, then wait ${tm.postResetDelayMs}ms.")
        add("2. Open UART at ${s.baudRate ?: 9600} bps 8N1 (echoCancellation=${s.echoCancellation}); transmit sync pulse (${s.syncPulseHex}) with ${tm.syncByteGapMs}ms gap and flush echo.")
        add("3. Query Silicon Signature (0xC0) to dynamically parse chip name & End_Addr (${s.flashSizeDetectionRule}).")
        add("4. Oscillator negotiation: ${s.oscillatorNegotiationRule}")
        add("5. Baud rate transition: ${s.baudStateMachineRule} (wait ${tm.baudSwitchDelayMs}ms after ACK before re-framing).")
        if (!isLocked) {
            add("6. Safe direct extraction succeeded via ${s.winningReadMethod}: read 0x000000..${s.flashEndAddressHex} using ${s.pageSizeBytes ?: 256}B pages with ${tm.frameToFrameDelayMs}ms frame delay.")
        } else {
            add("6. Target is LOCKED (${s.verify0x13DiffResult}): capture initial Full Flash & Block-0 Checksums via 0xB0 before modifying flash.")
            add("7. Zero Block-0 (0x22) -> extract Block-0 via 0xB0 differential if supported -> extract Blocks 1..N sequentially -> restore Block-0 (0x40) -> verify Full Flash 0xB0 Checksum.")
        }
    }

    val writeWorkflow = buildList {
        add("1. Apply pin logic: FLMD0/MODE=${s.flmd0PinState}, RESET Pulse=${tm.resetPulseMs}ms, post-reset settle=${tm.postResetDelayMs}ms.")
        add("2. Open UART at ${s.baudRate ?: 9600} bps 8N1; send sync (${s.syncPulseHex}) and Reset ACK check.")
        add("3. Read 0xC0 signature to determine dynamic Flash End_Addr, Block Size (${s.blockSizeBytes ?: 1024}B), and Page Size (${s.pageSizeBytes ?: 256}B).")
        add("4. Oscillator & Baud state machine: ${s.oscillatorNegotiationRule} | ${s.baudStateMachineRule}.")
        add("5. Erase flash via 0x20 (Chip Erase) or per-block 0x22 (Block Erase) and wait ${tm.eraseWriteBusyWaitMs}ms busy window until ACK (${framing?.ackHex ?: "0x06"}).")
        add("6. For each non-blank block: send Program header 0x40 (6-byte Start..End in ${framing?.endianness ?: "Big-Endian"}), wait ${tm.commandToDataDelayMs}ms, then stream STX (0x02) data frames of ${s.pageSizeBytes ?: 256}B ending with ETB (0x17) for intermediate pages and ETX (0x03) for final page (${tm.frameToFrameDelayMs}ms spacing).")
        add("7. Verify every written block with 0x13 (Verify) and confirm final Full-Flash Checksum with 0xB0; optionally step baud back to ${s.baudRate ?: 9600} before hardware reset.")
    }

    return buildString {
        appendLine("{")
        appendLine("  \"blueprintVersion\": \"admin-lab-dynamic-family-v4\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"adapter\": \"${esc(state.usbStatus.adapterName)} (${esc(state.usbStatus.vidPidHex)})\",")
        appendLine("  \"targetFamily\": \"${esc(state.selectedFamily.labelAr)}\",")
        appendLine("  \"hardwareWiring\": {")
        appendLine("    \"flmd0ModePin\": \"${esc(s.flmd0PinState)}\",")
        appendLine("    \"resetPinIdle\": \"${esc(s.resetPinIdleState)}\",")
        appendLine("    \"resetPinPulsePolarity\": \"${esc(s.resetPinPulsePolarity)}\",")
        appendLine("    \"dtrFlmd0Polarity\": \"${esc(s.dtrPolarity)}\",")
        appendLine("    \"rtsResetPolarity\": \"${esc(s.rtsPolarity)}\",")
        appendLine("    \"echoCancellationRequired\": ${s.echoCancellation},")
        appendLine("    \"syncPulseHex\": \"${esc(s.syncPulseHex)}\",")
        appendLine("    \"initialBaudRate\": ${s.baudRate ?: 0},")
        appendLine("    \"maxBaudSupported\": ${s.maxBaudTested ?: (s.baudRate ?: 0)},")
        appendLine("    \"supportedBaudRates\": [${s.supportedBaudRates.joinToString(", ")}]")
        appendLine("  },")
        appendLine("  \"frameFormat\": {")
        appendLine("    \"protocol\": \"${esc(framing?.label ?: "--")}\",")
        appendLine("    \"commandSoh\": \"${esc(framing?.sohHex ?: "--")}\",")
        appendLine("    \"dataStx\": \"${esc(framing?.stxHex ?: "--")}\",")
        appendLine("    \"intermediateEndEtb\": \"${esc(framing?.etbHex ?: "--")}\",")
        appendLine("    \"finalEndEtx\": \"${esc(framing?.etxHex ?: "--")}\",")
        appendLine("    \"ackByte\": \"${esc(framing?.ackHex ?: "--")}\",")
        appendLine("    \"errCmdByte\": \"${esc(framing?.errCmdHex ?: "--")}\",")
        appendLine("    \"errParamByte\": \"${esc(framing?.errParamHex ?: "--")}\",")
        appendLine("    \"addressEndianness\": \"${esc(framing?.endianness ?: "--")}\",")
        appendLine("    \"supportedStxPageSizes\": [${s.supportedStxPageSizes.joinToString(", ")}],")
        appendLine("    \"checksumFormula\": \"${esc(framing?.checksumFormula ?: "--")}\"")
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
        appendLine("  \"dynamicFamilyRules\": {")
        appendLine("    \"flashSizeDetectionRule\": \"${esc(s.flashSizeDetectionRule)}\",")
        appendLine("    \"detectedFlashEndAddress\": \"${esc(s.flashEndAddressHex)}\",")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256},")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024},")
        appendLine("    \"oscillatorNegotiationRule\": \"${esc(s.oscillatorNegotiationRule)}\",")
        appendLine("    \"oscillatorProbeResults\": \"${esc(s.oscillatorProbeResults)}\",")
        appendLine("    \"baudStateMachineRule\": \"${esc(s.baudStateMachineRule)}\"")
        appendLine("  },")
        appendLine("  \"extractionMethod\": {")
        appendLine("    \"isLockedForSafeRead\": $isLocked,")
        appendLine("    \"verify0x13DiffResult\": \"${esc(s.verify0x13DiffResult)}\",")
        appendLine("    \"winningSchool\": \"${esc(s.winningReadMethod)}\",")
        appendLine("    \"chipSignature\": \"${esc(s.chipSignature)}\",")
        appendLine("    \"safeReadResults\": [")
        val stage2List = state.checklist.filter { it.id.startsWith("stage2_") }
        stage2List.forEachIndexed { idx, item ->
            val comma = if (idx < stage2List.lastIndex) "," else ""
            appendLine("      { \"school\": \"${esc(item.title)}\", \"passed\": ${item.status == ChecklistStepStatus.PASSED}, \"detail\": \"${esc(item.detail)}\" }$comma")
        }
        appendLine("    ]")
        appendLine("  },")
        appendLine("  \"discoveredCommandsTable\": [")
        s.discoveredCommands.forEachIndexed { idx, cmd ->
            val comma = if (idx < s.discoveredCommands.lastIndex) "," else ""
            appendLine(
                "    { \"cmd\": \"${cmd.cmdHex}\", \"lenArgs\": ${cmd.exactParamLen ?: -1}, \"reply\": \"${cmd.replyStatusHex}\", \"safeReadOnly\": ${cmd.isSafeReadOnly}, \"role\": \"${esc(cmd.roleGuess)}\" }$comma"
            )
        }
        appendLine("  ],")
        appendLine("  \"exactDynamicReadWorkflow\": [")
        readWorkflow.forEachIndexed { idx, step ->
            val comma = if (idx < readWorkflow.lastIndex) "," else ""
            appendLine("    \"${esc(step)}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"exactDynamicWriteWorkflow\": [")
        writeWorkflow.forEachIndexed { idx, step ->
            val comma = if (idx < writeWorkflow.lastIndex) "," else ""
            appendLine("    \"${esc(step)}\"$comma")
        }
        appendLine("  ],")
        appendLine("  \"stage5DestructiveTests\": [")
        s.stage5Results.forEachIndexed { idx, r ->
            val comma = if (idx < s.stage5Results.lastIndex) "," else ""
            appendLine(
                "    { \"step\": \"${esc(r.stepTitle)}\", \"executed\": ${r.executed}, \"passed\": ${r.passed}, \"durationMs\": ${r.durationMs}, \"detail\": \"${esc(r.detail)}\" }$comma"
            )
        }
        appendLine("  ],")
        appendLine("  \"advancedPostHotSwapNotes\": \"${esc(s.advancedSchoolsNotes)}\"")
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
