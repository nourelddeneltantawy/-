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

    fun is78k0Flmd0Mode(): Boolean {
        val fam = mainFamily.trim().uppercase()
        return fam.contains("78K0") && !fam.contains("78K0R")
    }

    fun isRl78Family(): Boolean = mainFamily.trim().uppercase().contains("RL78")

    fun isDiodeSingleWireSchematicFamily(): Boolean {
        val fam = mainFamily.trim().uppercase()
        return fam.contains("RL78") || fam.contains("78K0") || fam.contains("R8C") || fam.contains("M16C")
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
            fam.contains("R8C") -> "Renesas R8C Automatic Boot + Single-Wire Mode 3 (DTR=RESET, RTS+TX/RX=MODE)"
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
        val pkgNote = "حزمة ${mcu.pins}-Pin (راجع الداتاشيت لرقم البن الفعلي)"
        return when {
            fam.contains("RL78") -> listOf(
                McuPinMapping("TX + RX (عبر دايود 1N4148 D1)", "TOOL0 / MODE", "طرف أحادي السلك", "ثنائي الاتجاه (D1)", "تجميع TX و RX عبر دايود D1 (1N4148) إلى طرف TOOL0 مع إلغاء الصدى تلقائياً"),
                McuPinMapping("RTS (عبر دايود 1N4148 D2)", "RESET#", "تحكم بالبوت", "خرج Reset", "الكاثود جهة RTS والأنود جهة RESET لتنفيذ نبضة 50ms ثم HIGH"),
                McuPinMapping("VCC (التغذية)", "VDD / EVDD", "1.6V - 5.5V", "تغذية", "تغذية مستقرة ${mcu.recommendedVoltage}"),
                McuPinMapping("GND (الأرضي)", "VSS / EVSS", "أرضي + مكثف REGC", "أرضي مشترك", "ربط الأرضي مباشرة + مكثف 0.47µF على طرف REGC")
            )
            fam.contains("R8C") -> listOf(
                McuPinMapping("DTR (مباشر)", "RESET#", "نبضة Boot التلقائية", "خرج Reset", "DTR=true (80ms LOW) ثم DTR=false (120ms HIGH لاستقرار OTG والمذبذب)"),
                McuPinMapping("RTS (دايود — كاثود على RTS)", "MODE / RX", "خفض MODE للبوت", "تحكم بالبوت", "RTS=true يسحب MODE للـ LOW (50ms) قبل RESET، ثم RTS=false (30ms) لتحرير الخط"),
                McuPinMapping("TX (دايود) + RX (مباشر)", "MODE (Single-Wire)", "Mode 3 أحادي السلك", "ثنائي الاتجاه (صدى D)", "مراقبة RX الخام + حذف الصدى المطابق تلقائياً ($pkgNote)"),
                McuPinMapping("VCC (التغذية)", "VCC / AVCC", "2.7V - 5.5V", "تغذية", "تغذية مستقرة ${mcu.recommendedVoltage}"),
                McuPinMapping("GND (الأرضي)", "VSS", "أرضي مشترك", "أرضي مشترك", "ربط الأرضي المشترك بين المبرمجة والكارتة")
            )
            fam.contains("78K0R") -> listOf(
                McuPinMapping("TX + RX (عبر دايود 1N4148 D1)", "TOOL0 / MODE", "حسب الباكج (${mcu.pins}P)", "ثنائي الاتجاه (D1)", "دمج TX و RX عبر دايود D1 (1N4148) لتكوين خط TOOL0 / MODE المشترك ($pkgNote)"),
                McuPinMapping("RTS (عبر دايود 1N4148 D2)", "RESET#", "تحكم بالبوت", "خرج Reset", "طرف RTS مسؤول عن RESET عبر دايود D2 (نبضة 50ms مع TX LOW)"),
                McuPinMapping("DTR (إذا توفر FLMD0)", "FLMD0", "HIGH (5V/3.3V)", "تفعيل البرمجة", "يُربط في الموديلات المزودة بطرف FLMD0 (يُضبط DTR = false)"),
                McuPinMapping("VCC / GND", "VDD / VSS", "تغذية وأرضي", "تغذية مشتركة", "${mcu.recommendedVoltage} + مكثف REGC")
            )
            fam.contains("78K0") -> listOf(
                McuPinMapping("RTS (عبر دايود 1N4148 D2)", "RESET#", "نبضة Reset 50ms", "خرج Reset", "طرف RTS يتحكم في RESET عبر دايود D2 (50ms LOW ثم HIGH)"),
                McuPinMapping("DTR (تحكم FLMD0)", "FLMD0", "HIGH بجهد 5V", "تفعيل وضع الفلاش", "طرف DTR مسؤول عن FLMD0 (يُضبط DTR = false لإخراج HIGH 5V)"),
                McuPinMapping("TX + RX (عبر دايود 1N4148 D1)", "TOOL0 / UART6 (RXD6/TXD6)", "حسب الباكج (${mcu.pins}P)", "ثنائي الاتجاه (D1)", "دمج TX و RX عبر دايود D1 (1N4148) مع تفعيل إلغاء الصدى تلقائياً"),
                McuPinMapping("VCC / GND", "VDD / VSS", "تغذية 5V وأرضي", "تغذية مشتركة", "${mcu.recommendedVoltage}")
            )
            fam.contains("M16C") -> listOf(
                McuPinMapping("TX + RX (عبر دايود 1N4148 D1)", "TOOL0 / MODE (RXD1/TXD1)", "حسب الباكج (${mcu.pins}P)", "ثنائي الاتجاه (D1)", pkgNote),
                McuPinMapping("RTS (عبر دايود 1N4148 D2)", "RESET#", "تحكم بالبوت", "خرج Reset", "نبضة Reset عبر RTS مع تهيئة CNVss"),
                McuPinMapping("VCC", "VCC1 / VCC2", "3.0V - 5.5V", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS", "أرضي", "أرضي مشترك", "أرضي الدائرة المشترك")
            )
            fam.startsWith("RX") -> listOf(
                McuPinMapping("TXD (3.3V فقط)", "RXD1 (SCI Boot)", "حسب الباكج (${mcu.pins}P)", "UART إلى المعالج", "مستوى الجهد 3.3V حصراً"),
                McuPinMapping("RXD (3.3V فقط)", "TXD1 (SCI Boot)", "حسب الباكج (${mcu.pins}P)", "UART من المعالج", "مستوى الجهد 3.3V حصراً"),
                McuPinMapping("RTS (عبر D2)", "RES# + MD (LOW)", "تفعيل SCI Boot", "تحكم بالبوت", "ضع طرف MD على LOW عند نبضة RES#"),
                McuPinMapping("VCC (3.3V)", "VCC / AVCC0", "3.3V حصراً", "تغذية", mcu.recommendedVoltage),
                McuPinMapping("GND", "VSS / AVSS0", "أرضي", "أرضي مشترك", "أرضي مشترك مستقر")
            )
            else -> listOf(
                McuPinMapping("TX + RX (عبر دايود 1N4148 D1)", "TOOL0 / MODE", "حسب الباكج (${mcu.pins}P)", "ثنائي الاتجاه (D1)", pkgNote),
                McuPinMapping("RTS (عبر دايود 1N4148 D2)", "RESET#", "تحكم بالبوت", "خرج Reset", "طرف RTS مسؤول عن RESET عبر دايود D2"),
                McuPinMapping("DTR", "FLMD0 (عند الحاجة)", "تفعيل البرمجة", "تحكم بالبوت", "رفع FLMD0 إلى HIGH في عائلات 78K0"),
                McuPinMapping("VCC / GND", "VDD / VSS", "تغذية وأرضي", "تغذية مشتركة", mcu.recommendedVoltage)
            )
        }
    }
}
