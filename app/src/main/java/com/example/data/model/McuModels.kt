package com.example.data.model

enum class ProtocolFamilyType {
    R8C_M16C,
    RL78_RX,
    SEVEN_EIGHT_K,
    GENERIC
}

data class McuPinMapping(
    val programmerPin: String,
    val mcuPinName: String,
    val mcuPinNumber: String,
    val signalDirection: String = "ثنائي الاتجاه",
    val notes: String = ""
)

data class McuProcessor(
    val id: String,
    val manufacturer: String,
    val mainFamily: String,
    val subFamily: String,
    val partNumber: String,
    val pins: Int,
    val flashKb: Int,
    val displayLabel: String,
    val recommendedVoltage: String,
    val protocolName: String,
    val protocolType: ProtocolFamilyType,
    val sourceFile: String = "",
    val customPinout: List<McuPinMapping>? = null
) {
    fun effectivePinout(): List<McuPinMapping> {
        if (!customPinout.isNullOrEmpty()) return customPinout
        return McuProtocolDefaults.defaultPinoutFor(this)
    }

    val flashSizeBytes: Int
        get() = (flashKb.coerceAtLeast(1)) * 1024
}

enum class ProgrammerChipType(
    val id: String,
    val displayName: String,
    val chipModel: String,
    val vendorIds: List<Int>,
    val subtitle: String,
    val voltageLogic: String
) {
    FT232RL(
        id = "ft232rl",
        displayName = "FTDI FT232RL USB-UART",
        chipModel = "FT232RL",
        vendorIds = listOf(0x0403),
        subtitle = "الأكثر استقراراً لبرمجة Renesas R8C / M16C / RL78 عبر OTG",
        voltageLogic = "3.3V / 5V (Jumper)"
    ),
    CH340G(
        id = "ch340g",
        displayName = "WCH CH340G / CH341 TTL",
        chipModel = "CH340G",
        vendorIds = listOf(0x1A86),
        subtitle = "مبرمجة TTL اقتصادية متوافقة مع UART Mode و TOOL0",
        voltageLogic = "3.3V / 5V"
    ),
    CP2102(
        id = "cp2102",
        displayName = "Silicon Labs CP2102",
        chipModel = "CP2102",
        vendorIds = listOf(0x10C4),
        subtitle = "دعم ممتاز لمعدلات الباود العالية وبروتوكول RX Boot Mode",
        voltageLogic = "3.3V TTL"
    ),
    PL2303(
        id = "pl2303",
        displayName = "Prolific PL2303 Serial",
        chipModel = "PL2303",
        vendorIds = listOf(0x067B),
        subtitle = "محول USB-to-Serial قياسي للكروت الصناعية والغسالات",
        voltageLogic = "3.3V / 5V"
    );

    companion object {
        fun fromVendorId(vid: Int): ProgrammerChipType? {
            return entries.firstOrNull { vid in it.vendorIds }
        }
    }
}

data class UsbSerialDeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val chipType: ProgrammerChipType?,
    val hasPermission: Boolean
) {
    val hexVidPid: String
        get() = "VID:%04X PID:%04X".format(vendorId, productId)
}

data class FirmwareFileData(
    val fileName: String,
    val format: String,
    val bytes: ByteArray,
    val nonEmptyBytesCount: Int,
    val startAddress: Int
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class ProgrammerLogEntry(
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

object McuProtocolDefaults {
    fun resolveProtocolType(mainFamily: String): ProtocolFamilyType {
        val fam = mainFamily.trim().uppercase()
        return when {
            fam.contains("R8C") || fam.contains("M16C") -> ProtocolFamilyType.R8C_M16C
            fam.contains("RL78") || fam == "RX" || fam.startsWith("RX") -> ProtocolFamilyType.RL78_RX
            fam.contains("78K") -> ProtocolFamilyType.SEVEN_EIGHT_K
            else -> ProtocolFamilyType.GENERIC
        }
    }

    fun defaultVoltage(mainFamily: String): String {
        val fam = mainFamily.trim().uppercase()
        return when {
            fam.contains("RL78") -> "1.6V - 5.5V (موصى به 3.3V / 5.0V)"
            fam.startsWith("RX") -> "2.7V - 3.6V (3.3V فقط - احذر 5V)"
            fam.contains("R8C") -> "2.7V - 5.5V (موصى به 5.0V للبرمجة)"
            fam.contains("M16C") -> "3.0V - 5.5V (موصى به 5.0V)"
            fam.contains("78K0R") -> "1.8V - 5.5V (موصى به 3.3V / 5.0V)"
            fam.contains("78K0") -> "2.7V - 5.5V (موصى به 5.0V)"
            else -> "3.3V - 5.0V (راجع ورقة البيانات)"
        }
    }

    fun defaultProtocolName(mainFamily: String, subFamily: String): String {
        val fam = mainFamily.trim().uppercase()
        return when {
            fam.contains("R8C") -> "Renesas Standard Serial I/O Mode 2 (UART + MODE + 7-Byte ID)"
            fam.contains("M16C") -> "M16C Bootloader UART Mode (CNVss + RESET + 7-Byte ID)"
            fam.contains("RL78") -> "RL78 Single-Wire TOOL0 / 2-Wire UART Programming"
            fam.startsWith("RX") -> "RX SCI Boot Mode (MD + RESET + TXD/RXD)"
            fam.contains("78K0R") -> "78K0R Serial Programming (FLMD0 + TOOL0/UART)"
            fam.contains("78K0") -> "78K0 UART6 Serial Flash Programming (FLMD0 + X1/Clock)"
            else -> "Universal Serial Bootloader ($subFamily)"
        }
    }

    fun defaultPinoutFor(mcu: McuProcessor): List<McuPinMapping> {
        val fam = mcu.mainFamily.trim().uppercase()
        val pkgNote = "حزمة ${mcu.pins}-Pin (حدد رقم البن حسب Datasheet إذا لم يُدرج في JSON)"
        return when {
            fam.contains("R8C") -> listOf(
                McuPinMapping("TXD (خرج المبرمجة)", "RXD1 / P3_7 (또는 P1_5)", "حسب الباكج (${mcu.pins}P)", "UART إلى المعالج", "طرف استقبال البيانات في وضع Boot ($pkgNote)"),
                McuPinMapping("RXD (دخل المبرمجة)", "TXD1 / P3_4 (أو P1_4)", "حسب الباكج (${mcu.pins}P)", "UART من المعالج", "طرف إرسال البيانات من المعالج"),
                McuPinMapping("GND (الأرضي)", "VSS", "مرجع مشترك", "أرضي مشترك", "يجب ربطه مباشرة بأرضي البوردة"),
                McuPinMapping("VCC (التغذية)", "VCC / AVCC", "تغذية 5V / 3.3V", "تغذية", "تغذية مستقرة ${mcu.recommendedVoltage}"),
                McuPinMapping("DTR / RTS (التحكم)", "RESET + MODE", "LOW أثناء الإقلاع", "تحكم بالبوت", "توصيل MODE للأرضي عبر مقاومة 1kΩ - 4.7kΩ عند التشغيل")
            )
            fam.contains("M16C") -> listOf(
                McuPinMapping("TXD (خرج المبرمجة)", "RXD1 (P6_6)", "حسب الباكج (${mcu.pins}P)", "UART إلى المعالج", pkgNote),
                McuPinMapping("RXD (دخل المبرمجة)", "TXD1 (P6_7)", "حسب الباكج (${mcu.pins}P)", "UART من المعالج", "خرج البيانات التسلسلي"),
                McuPinMapping("DTR / RTS", "RESET + CNVss (HIGH)", "تفعيل Boot Mode", "تحكم بالبوت", "رفع CNVss إلى VCC وسحب RESET ثم إطلاقه"),
                McuPinMapping("VCC", "VCC1 / VCC2", "3.3V - 5.0V", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS", "أرضي", "أرضي مشترك", "أرضي الدائرة المشترك")
            )
            fam.contains("RL78") -> listOf(
                McuPinMapping("TXD + RXD (عبر مقاومة 1kΩ)", "TOOL0 (P4_0)", "طرف أحادي السلك", "ثنائي الاتجاه (1-Wire)", "اربط TXD بـ RXD عبر دايود شوتكي أو مقاومة 1kΩ إلى TOOL0"),
                McuPinMapping("DTR / RTS", "RESET", "سحب مؤقت للأرضي", "إعادة ضبط", "نبضة Low على RESET مع بقاء TOOL0 منخفضاً لدخول البرمجة"),
                McuPinMapping("VCC", "VDD / EVDD", "1.6V - 5.5V", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS / EVSS", "أرضي + ربط REGC بمكثف", "أرضي مشترك", "تأكد من وجود مكثف 0.47µF على طرف REGC")
            )
            fam.startsWith("RX") -> listOf(
                McuPinMapping("TXD (3.3V فقط)", "RXD1 (SCI Boot)", "حسب الباكج (${mcu.pins}P)", "UART إلى المعالج", "مستوى الجهد 3.3V حصراً"),
                McuPinMapping("RXD (3.3V فقط)", "TXD1 (SCI Boot)", "حسب الباكج (${mcu.pins}P)", "UART من المعالج", "مستوى الجهد 3.3V حصراً"),
                McuPinMapping("DTR / RTS", "RES# + MD (LOW)", "تفعيل SCI Boot", "تحكم بالبوت", "ضع طرف MD على LOW عند نبضة RES#"),
                McuPinMapping("VCC (3.3V)", "VCC / AVCC0", "3.3V حصراً", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS / AVSS0", "أرضي", "أرضي مشترك", "أرضي مشترك مستقر")
            )
            else -> listOf(
                McuPinMapping("TXD (خرج المبرمجة)", "RXD6 / TOOLRxD", "حسب الباكج (${mcu.pins}P)", "UART إلى المعالج", pkgNote),
                McuPinMapping("RXD (دخل المبرمجة)", "TXD6 / TOOLTxD", "حسب الباكج (${mcu.pins}P)", "UART من المعالج", "استقبال ردود المعالج"),
                McuPinMapping("DTR / RTS", "FLMD0 (HIGH) + RESET", "تفعيل البرمجة", "تحكم بالبوت", "رفع FLMD0 إلى VDD ثم نبضة RESET"),
                McuPinMapping("VCC", "VDD", "تغذية", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS", "أرضي", "أرضي مشترك", "ربط الأرضي المشترك")
            )
        }
    }
}
