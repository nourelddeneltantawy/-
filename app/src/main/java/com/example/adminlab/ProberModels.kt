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
    AWAITING_SAFETY("⚠️ بانتظار موافقة الأمان"),
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

enum class DetectedFraming(
    val label: String,
    val sohHex: String,
    val stxHex: String,
    val etxHex: String,
    val checksumFormula: String
) {
    NEC_STX_ETX(
        label = "NEC / Renesas 78K0 (SOH/STX/ETX)",
        sohHex = "0x01",
        stxHex = "0x02",
        etxHex = "0x03",
        checksumFormula = "8-bit Two's Complement: (0 - (LEN + sum(BODY))) & 0xFF"
    ),
    STM32_ACK(
        label = "STM32 USART Bootloader (0x79 ACK)",
        sohHex = "N/A (2-Byte [CMD, ~CMD])",
        stxHex = "N/A",
        etxHex = "0x79 ACK / 0x1F NACK",
        checksumFormula = "8-bit XOR Checksum over payload/address bytes"
    ),
    RENESAS_RAW(
        label = "Renesas R8C / RL78 Status Framing",
        sohHex = "Single-Byte Command",
        stxHex = "256B Raw Page",
        etxHex = "Status SRD1/SRD2",
        checksumFormula = "Direct Single-Wire UART Framing"
    ),
    CUSTOM_RAW(
        label = "إطار خام / UART مخصص",
        sohHex = "Raw",
        stxHex = "Raw",
        etxHex = "Timeout/Delim",
        checksumFormula = "Unknown / Raw UART"
    )
}

enum class ProberStagePhase {
    IDLE,
    RUNNING_SAFE_PIPELINE,
    AWAITING_HOT_SWAP_GATE,
    HOT_SWAP_DISCONNECTED_READY,
    RUNNING_ADVANCED_AFTER_SWAP,
    RUNNING_EMERGENCY_RESTORE,
    COMPLETED,
    STOPPED,
    ERROR
}

data class ProberBlueprintSummary(
    val baudRate: Int? = null,
    val maxBaudTested: Int? = null,
    val dtrPolarity: String = "--",
    val rtsPolarity: String = "--",
    val echoCancellation: Boolean = false,
    val syncPulseHex: String = "--",
    val framing: DetectedFraming? = null,
    val winningReadMethod: String = "--",
    val chipSignature: String = "",
    val pageSizeBytes: Int? = null,
    val blockSizeBytes: Int? = null,
    val flashEndAddressHex: String = "--",
    val safeDelayMs: Int? = null,
    val discoveredCommands: List<DiscoveredCommandEntry> = emptyList(),
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
    val restoreProgressText: String = "",
    val canBypassSync: Boolean = false,
    val logs: List<ProberLogEntry> = emptyList()
) {
    val isBusy: Boolean
        get() = phase == ProberStagePhase.RUNNING_SAFE_PIPELINE ||
            phase == ProberStagePhase.RUNNING_ADVANCED_AFTER_SWAP ||
            phase == ProberStagePhase.RUNNING_EMERGENCY_RESTORE

    val isWaitingAtSafetyGate: Boolean
        get() = phase == ProberStagePhase.AWAITING_HOT_SWAP_GATE ||
            phase == ProberStagePhase.HOT_SWAP_DISCONNECTED_READY
}

fun defaultChecklist(): List<ChecklistStepItem> = listOf(
    ChecklistStepItem("stage1_handshake", "المرحلة 1", "هندسة التوصيل والتزامن (DTR/RTS، السرعات، صدى الدايود، الإطار)"),
    ChecklistStepItem("stage2_direct_read", "المرحلة 2.1", "مدرسة القراءة المباشرة (0x11, 0x03, 0x18, 0x74, 0xBA, 0xD0, 0xFF)"),
    ChecklistStepItem("stage2_checksum_1b", "المرحلة 2.2", "مدرسة السحب عبر بصمة بايت واحد (0xB0 على 1B ثم 2B)"),
    ChecklistStepItem("stage2_verify_0x13", "المرحلة 2.3", "مدرسة التحقق التدريجي (Verify 0x13 على صفحة 0xFF وإطار STX 0x02)"),
    ChecklistStepItem("stage2_signature", "المرحلة 2.4", "مدرسة قراءة التوقيع والذاكرة المفتوحة (0xC0, 0xC5, 0x01, 0x02)"),
    ChecklistStepItem("stage2_ram_stub", "المرحلة 2.5", "مدرسة حقن الكود في الرام (RAM Stub في عنوان مؤقت آمن)"),
    ChecklistStepItem("stage3_cmd_sweep", "المرحلة 3", "المسح الجراحي الشامل للأوامر 0x00..0xFF وأطوال المعاملات LEN 0..8"),
    ChecklistStepItem("stage4_timing_bounds", "المرحلة 4", "استخراج الحدود والسرعة (0x9A، Page Size، Block Size، Safe Delay)"),
    ChecklistStepItem("stage5_safety_gate", "المرحلة 5", "بوابة الأمان، تبديل الكارتة (Hot-Swap)، والمدارس المتقدمة / الاستعادة")
)

fun formatAiReadyBlueprintJson(state: ProberUiState): String {
    val s = state.blueprintSummary
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val safeExtractSchoolIds = setOf("stage2_direct_read", "stage2_checksum_1b", "stage2_ram_stub")
    val readPasses = state.checklist.filter { it.id in safeExtractSchoolIds && it.status == ChecklistStepStatus.PASSED }
    val framing = s.framing

    fun esc(v: String): String = v
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    return buildString {
        appendLine("{")
        appendLine("  \"blueprintVersion\": \"admin-lab-prober-v3\",")
        appendLine("  \"generatedAt\": \"$now\",")
        appendLine("  \"adapter\": \"${esc(state.usbStatus.adapterName)} (${esc(state.usbStatus.vidPidHex)})\",")
        appendLine("  \"targetFamily\": \"${esc(state.selectedFamily.labelAr)}\",")
        appendLine("  \"hardwareWiring\": {")
        appendLine("    \"dtrFlmd0Polarity\": \"${esc(s.dtrPolarity)}\",")
        appendLine("    \"rtsResetPolarity\": \"${esc(s.rtsPolarity)}\",")
        appendLine("    \"echoCancellationRequired\": ${s.echoCancellation},")
        appendLine("    \"syncPulseHex\": \"${esc(s.syncPulseHex)}\",")
        appendLine("    \"baudRate\": ${s.baudRate ?: 0},")
        appendLine("    \"maxBaudSupported\": ${s.maxBaudTested ?: (s.baudRate ?: 0)}")
        appendLine("  },")
        appendLine("  \"frameFormat\": {")
        appendLine("    \"protocol\": \"${esc(framing?.label ?: "--")}\",")
        appendLine("    \"commandSoh\": \"${esc(framing?.sohHex ?: "--")}\",")
        appendLine("    \"dataStx\": \"${esc(framing?.stxHex ?: "--")}\",")
        appendLine("    \"endEtx\": \"${esc(framing?.etxHex ?: "--")}\",")
        appendLine("    \"checksumFormula\": \"${esc(framing?.checksumFormula ?: "--")}\"")
        appendLine("  },")
        appendLine("  \"extractionMethod\": {")
        appendLine("    \"isLockedForSafeRead\": ${readPasses.isEmpty()},")
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
        appendLine("  \"flashTimingAndBoundaries\": {")
        appendLine("    \"pageSizeBytes\": ${s.pageSizeBytes ?: 256},")
        appendLine("    \"blockSizeBytes\": ${s.blockSizeBytes ?: 1024},")
        appendLine("    \"flashEndAddress\": \"${esc(s.flashEndAddressHex)}\",")
        appendLine("    \"safeDelayMs\": ${s.safeDelayMs ?: 25},")
        appendLine("    \"datasheetVerificationReminder\": \"Verify Page Size & Block Size against official MCU datasheet before writing.\"")
        appendLine("  },")
        appendLine("  \"discoveredCommandsTable\": [")
        s.discoveredCommands.forEachIndexed { idx, cmd ->
            val comma = if (idx < s.discoveredCommands.lastIndex) "," else ""
            appendLine(
                "    { \"cmd\": \"${cmd.cmdHex}\", \"lenArgs\": ${cmd.exactParamLen ?: -1}, \"reply\": \"${cmd.replyStatusHex}\", \"safeReadOnly\": ${cmd.isSafeReadOnly}, \"role\": \"${esc(cmd.roleGuess)}\" }$comma"
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
