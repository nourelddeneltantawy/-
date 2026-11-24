package com.example.adminlab

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.min

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
        fun fromVendorId(vid: Int): ProberUsbChip {
            return entries.firstOrNull { vid in it.vendorIds } ?: CDC_ACM
        }
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

enum class ProbeCheckStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED
}

data class SchoolProbeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: ProbeCheckStatus = ProbeCheckStatus.PENDING,
    val commandHex: String = "--",
    val detail: String = ""
)

enum class DangerousSchoolType(
    val title: String,
    val warningText: String
) {
    BLOCK0_ZEROING(
        title = "مدرسة تصفير بلوك الحماية (Block-0 Zeroing)",
        warningText = "تحذير: تعدّل في أول بلوك وتعيده؛ انقطاع السلك يسبب تلف السوفت وير."
    ),
    PARTIAL_BIT_OVERWRITE(
        title = "مدرسة الكتابة الجزئية للبتات (Partial Bit Overwrite)",
        warningText = "تحذير: تغيّر في بايتات الفلاش وتُتلف السوفت وير الأصلي."
    ),
    SECURITY_AUTO_ERASE(
        title = "مدرسة تجاوز قفل الحماية (Security Auto-Erase)",
        warningText = "خطر شديد: قد يمسح المعالج ذاكرته تلقائياً."
    )
}

enum class ProberOutcomeKind {
    IDLE,
    RUNNING,
    COMPLETED,
    LOCKED_BOOTLOADER,
    NO_SYNC,
    DANGEROUS_MANUAL_RESULT,
    STOPPED,
    ERROR
}

data class ProberResult(
    val kind: ProberOutcomeKind = ProberOutcomeKind.IDLE,
    val headline: String = "",
    val detail: String = "",
    val baudRate: Int? = null,
    val framingType: String = "--",
    val polaritySummary: String = "--",
    val detectedSignature: String = "",
    val winningReadMethod: String = "--",
    val supportedWriteCmd: String = "--",
    val supportedEraseCmd: String = "--",
    val supportedBlankCheckCmd: String = "--",
    val sampleRxHex: String = ""
)

data class ProberUiState(
    val usbStatus: ProberUsbStatus = ProberUsbStatus(),
    val selectedFamily: ProberTargetFamily = ProberTargetFamily.AUTO_DETECT,
    val isProbing: Boolean = false,
    val stageLabel: String = "جاهز للفحص الشامل الآمن",
    val readSchools: List<SchoolProbeItem> = defaultReadSchools(),
    val writeEraseProbes: List<SchoolProbeItem> = defaultWriteEraseProbes(),
    val result: ProberResult = ProberResult(),
    val logs: List<ProberLogEntry> = emptyList()
)

private fun defaultReadSchools(): List<SchoolProbeItem> = listOf(
    SchoolProbeItem(
        id = "direct_read",
        title = "1. مدرسة القراءة المباشرة (Direct Read)",
        subtitle = "اختبار الأوامر (0x11, 0x03, 0x18, 0x74, 0xBA, 0xFF) لقراءة 16 بايت"
    ),
    SchoolProbeItem(
        id = "checksum_1b",
        title = "2. مدرسة السحب عبر بصمة بايت واحد (1-Byte Checksum)",
        subtitle = "اختبار أمر 0xB0 على 1 بايت ثم 2 بايت بدون لمس الفلاش"
    ),
    SchoolProbeItem(
        id = "verify_0x13",
        title = "3. مدرسة التحقق التدريجي (Verify Probe 0x13)",
        subtitle = "اختبار استجابة المعالج لأمر التحقق 0x13 على صفحة فارغة"
    ),
    SchoolProbeItem(
        id = "signature_window",
        title = "4. مدرسة قراءة نافذة السجلات والتوقيع (Signature/Window Read)",
        subtitle = "اختبار أوامر التوقيع والسجلات (0xC0, 0xC5, 0x01, 0x02)"
    ),
    SchoolProbeItem(
        id = "ram_stub",
        title = "5. مدرسة حقن الكود في الرام (RAM Stub Injection)",
        subtitle = "اختبار عنوان RAM مؤقت فقط دون لمس عناوين الفلاش"
    )
)

private fun defaultWriteEraseProbes(): List<SchoolProbeItem> = listOf(
    SchoolProbeItem(
        id = "write_cmd_probe",
        title = "1. اكتشاف أمر الكتابة (Write Command Probe)",
        subtitle = "فحص ترويسة الأوامر (0x40, 0x31, 0x41, 0x50) بدون بيانات كتابة"
    ),
    SchoolProbeItem(
        id = "erase_cmd_probe",
        title = "2. اكتشاف أمر المسح (Erase Command Probe)",
        subtitle = "فحص ترويسة الأوامر (0x20, 0x22, 0x43, 0x44) بدون عنوان بلوك للتنفيذ"
    ),
    SchoolProbeItem(
        id = "blank_check_probe",
        title = "3. اكتشاف فحص الفراغ (Blank Check Probe)",
        subtitle = "فحص أوامر التأكد من خلو الذاكرة (0x30, 0x32, 0xD0) الآمنة 100%"
    )
)

/**
 * Isolated Admin Protocol Auto-Prober Engine.
 * Zero shared state with the production programmer service or existing protocol engines.
 */
class ProberEngine(context: Context) {

    companion object {
        private const val ACTION_PROBER_USB_PERMISSION = "com.example.adminlab.USB_PERMISSION"

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
        private const val STS_BLANK_CHECK_ERROR = 0x1B
        private const val STS_VERIFY_ERROR_1C = 0x1C

        private val NEC_HARD_REJECT_CODES = setOf(
            STS_CMD_ERROR,
            STS_PARAM_ERROR,
            STS_CHECKSUM_ERROR,
            STS_VERIFY_ERROR,
            STS_PROTECT_ERROR,
            STS_NACK,
            0x1A,
            STS_BLANK_CHECK_ERROR,
            STS_VERIFY_ERROR_1C,
            NACK_STM32
        )

        private const val DATA_SHEET_REMINDER =
            "⚠️ تذكير فني إلزامي: يجب مطابقة حجم الصفحة (Page Size) وحجم البلوك (Block Size) من الداتا شيت (Datasheet) الخاصة بالمعالج قبل تنفيذ أي تنزيل فعلي."
    }

    private enum class DetectedFraming(val label: String) {
        NEC_STX_ETX("NEC / Renesas 78K0 (STX/ETX)"),
        STM32_ACK("STM32 USART Bootloader (0x79 ACK)"),
        RENESAS_RAW("Renesas R8C / RL78 Status Framing"),
        CUSTOM_RAW("إطار خام / UART مخصص")
    }

    private data class SyncLockResult(
        val baudRate: Int,
        val polarityName: String,
        val invertedPolarity: Boolean,
        val useResetToggle: Boolean,
        val syncPulseHex: String,
        val framing: DetectedFraming,
        val firstReplyHex: String,
        val echoLoopbackDetected: Boolean
    )

    private val appContext = context.applicationContext
    private val usbManager: UsbManager? = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ProberUiState())
    val state: StateFlow<ProberUiState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var logSeq = 0L

    @Volatile
    private var activeProbeJob: Job? = null

    @Volatile
    private var activeDevice: UsbDevice? = null
    @Volatile
    private var activeConnection: UsbDeviceConnection? = null
    @Volatile
    private var inEndpoint: UsbEndpoint? = null
    @Volatile
    private var outEndpoint: UsbEndpoint? = null
    @Volatile
    private var activeChip: ProberUsbChip = ProberUsbChip.CDC_ACM
    @Volatile
    private var controlInterfaceIndex: Int = 0
    @Volatile
    private var currentBaud: Int = 9600
    @Volatile
    private var echoLoopbackObserved: Boolean = false
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private val usbSessionLock = Any()

    private val rxQueue = ArrayDeque<Byte>(8192)
    @Volatile
    private var readerJob: Job? = null

    private var usbDetachReceiver: BroadcastReceiver? = null

    init {
        registerDetachReceiver()
        refreshUsbStatus()
    }

    fun setTargetFamily(family: ProberTargetFamily) {
        _state.value = _state.value.copy(selectedFamily = family)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun refreshUsbStatus(): ProberUsbStatus {
        val mgr = usbManager
        val devices = mgr?.deviceList?.values?.toList().orEmpty()
        val chosen = pickBestSerialDevice(devices)
        val status = if (chosen == null) {
            ProberUsbStatus(
                isConnected = false,
                adapterName = "لا توجد مبرمجة USB OTG موصولة",
                chipModel = "غير متصل",
                vidPidHex = "--",
                hasPermission = false,
                deviceCount = 0
            )
        } else {
            val chip = ProberUsbChip.fromVendorId(chosen.vendorId)
            val perm = mgr?.hasPermission(chosen) == true
            val prod = chosen.productName?.takeIf { it.isNotBlank() } ?: chip.displayName
            ProberUsbStatus(
                isConnected = true,
                adapterName = prod,
                chipModel = chip.displayName,
                vidPidHex = "VID:%04X PID:%04X".format(chosen.vendorId, chosen.productId),
                hasPermission = perm,
                deviceCount = devices.size
            )
        }
        _state.value = _state.value.copy(usbStatus = status)
        return status
    }

    fun requestUsbPermissionIfNeeded() {
        scope.launch {
            val mgr = usbManager ?: run {
                appendLog(ProberLogDirection.ERROR, "نظام USB Host غير مدعوم على هذا الهاتف.")
                return@launch
            }
            val dev = pickBestSerialDevice(mgr.deviceList.values.toList()) ?: run {
                refreshUsbStatus()
                appendLog(ProberLogDirection.WARN, "لم يتم العثور على أي محول USB UART متصل.")
                return@launch
            }
            if (mgr.hasPermission(dev)) {
                refreshUsbStatus()
                appendLog(ProberLogDirection.SUCCESS, "صلاحية USB ممنوحة بالفعل: ${dev.productName ?: dev.deviceName}")
                return@launch
            }
            appendLog(ProberLogDirection.INFO, "جاري طلب صلاحية USB للمحول (${dev.productName ?: dev.deviceName})...")
            val granted = requestPermissionSuspend(mgr, dev)
            refreshUsbStatus()
            if (granted) {
                appendLog(ProberLogDirection.SUCCESS, "تم منح صلاحية USB بنجاح.")
            } else {
                appendLog(ProberLogDirection.ERROR, "رفض المستخدم صلاحية الوصول لمنفذ USB.")
            }
        }
    }

    /**
     * Full Safe Auto-Probe:
     * 1) Silent Handshake
     * 2) All 5 Safe Read Extraction Schools
     * 3) All 3 Safe Write/Erase/BlankCheck Command Probes (header-only, zero flash modification)
     */
    fun startSafeComprehensiveProbe() {
        if (activeProbeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeProbeJob = scope.launch {
            _state.value = _state.value.copy(
                isProbing = true,
                stageLabel = "1) خطوة التمهيد الصامتة (Silent Handshake)...",
                readSchools = defaultReadSchools(),
                writeEraseProbes = defaultWriteEraseProbes(),
                result = ProberResult(
                    kind = ProberOutcomeKind.RUNNING,
                    headline = "جاري الفحص الآمن الشامل...",
                    detail = "1) التمهيد الصامت -> 2) مدارس السحب الـ 5 -> 3) أوامر الكتابة والمسح الآمنة."
                )
            )
            appendLog(ProberLogDirection.INFO, "══ بدء الفحص الآمن الشامل (${family.labelAr}) ══")

            try {
                if (!openUsbSession()) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل فتح منفذ USB",
                        result = ProberResult(
                            kind = ProberOutcomeKind.ERROR,
                            headline = "تعذر فتح منفذ USB OTG",
                            detail = "تأكد من توصيل محول USB-TTL ومنح صلاحية USB ثم أعد المحاولة."
                        )
                    )
                    return@launch
                }

                // 1. SILENT HANDSHAKE
                val sync = performSilentHandshake(family)
                if (sync == null) {
                    appendLog(ProberLogDirection.WARN, "انتهى التمهيد الصامت بدون استجابة ACK صالحة من المعالج.")
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "لم يستجب المعالج للتمهيد الصامت",
                        result = ProberResult(
                            kind = ProberOutcomeKind.NO_SYNC,
                            headline = "لم يستجب المعالج لنبضات التزامن",
                            detail = "تأكد من توصيل TX/RX و FLMD0/BOOT0 والريسيت بشكل سليم، أو حدد العائلة يدوياً."
                        )
                    )
                    return@launch
                }

                appendLog(
                    ProberLogDirection.SUCCESS,
                    "تم التمهيد الصامت بنجاح: ${sync.baudRate} bps | ${sync.framing.label} | ${sync.polarityName}"
                )

                // 2. SECTION 1: ALL 5 SAFE READ EXTRACTION SCHOOLS
                _state.value = _state.value.copy(stageLabel = "2) فحص مدارس السحب الآمنة (5 مدارس)...")

                val s1 = probeSchool1DirectRead(sync)
                updateReadSchool(s1)

                resyncLight(sync)
                val s2 = probeSchool2Checksum1B(sync)
                updateReadSchool(s2)

                resyncLight(sync)
                val s3 = probeSchool3Verify0x13(sync)
                updateReadSchool(s3)

                resyncLight(sync)
                val s4 = probeSchool4SignatureWindow(sync)
                updateReadSchool(s4)

                resyncLight(sync)
                val s5 = probeSchool5RamStub(sync)
                updateReadSchool(s5)

                // 3. SECTION 2: SAFE WRITE / ERASE / BLANK CHECK PROTOCOL PROBES
                _state.value = _state.value.copy(stageLabel = "3) فحص أوامر الكتابة والمسح الآمنة (Header-Only)...")

                resyncLight(sync)
                val wProbe = probeSafeWriteCommandHeader(sync)
                updateWriteEraseProbe(wProbe)

                resyncLight(sync)
                val eProbe = probeSafeEraseCommandHeader(sync)
                updateWriteEraseProbe(eProbe)

                resyncLight(sync)
                val bProbe = probeSafeBlankCheck(sync)
                updateWriteEraseProbe(bProbe)

                val currentRead = _state.value.readSchools
                val passedReads = currentRead.filter { it.status == ProbeCheckStatus.PASSED }
                val winningRead = if (passedReads.isNotEmpty()) {
                    passedReads.joinToString(" + ") { "${it.title.substringAfter(". ")} ${it.commandHex}" }
                } else {
                    "مقفل (Locked Bootloader — لم تنجح أي مدرسة سحب آمنة)"
                }

                val sigText = s4.takeIf { it.status == ProbeCheckStatus.PASSED }?.detail ?: ""
                val sampleHex = passedReads.firstOrNull()?.detail?.take(120) ?: sync.firstReplyHex

                val outcomeKind = if (passedReads.isEmpty()) {
                    ProberOutcomeKind.LOCKED_BOOTLOADER
                } else {
                    ProberOutcomeKind.COMPLETED
                }

                val headline = if (passedReads.isNotEmpty()) {
                    "اكتمل الاكتشاف الآمن: نجاح ${passedReads.size}/5 للسحب"
                } else {
                    "المعالج متصل ولكنه محمي ضد السحب الآمن (Locked Bootloader)"
                }

                val writeSummary = if (wProbe.status == ProbeCheckStatus.PASSED) {
                    "أمر الكتابة المدعوم: ${wProbe.commandHex}"
                } else {
                    "أمر الكتابة: غير مدعوم / محمي"
                }
                val eraseSummary = if (eProbe.status == ProbeCheckStatus.PASSED) {
                    "أمر المسح المدعوم: ${eProbe.commandHex}"
                } else {
                    "أمر المسح: غير مدعوم / محمي"
                }
                val blankSummary = if (bProbe.status == ProbeCheckStatus.PASSED) {
                    "فحص الفراغ: ${bProbe.commandHex}"
                } else {
                    "فحص الفراغ: غير متاح"
                }

                val detailText = buildString {
                    appendLine("• السحب: $winningRead")
                    appendLine("• $writeSummary")
                    appendLine("• $eraseSummary")
                    appendLine("• $blankSummary")
                    append(DATA_SHEET_REMINDER)
                }

                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "اكتمل الفحص الآمن بنجاح",
                    result = ProberResult(
                        kind = outcomeKind,
                        headline = headline,
                        detail = detailText,
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        polaritySummary = sync.polarityName,
                        detectedSignature = sigText,
                        winningReadMethod = winningRead,
                        supportedWriteCmd = if (wProbe.status == ProbeCheckStatus.PASSED) wProbe.commandHex else "غير مكتشف ❌",
                        supportedEraseCmd = if (eProbe.status == ProbeCheckStatus.PASSED) eProbe.commandHex else "غير مكتشف ❌",
                        supportedBlankCheckCmd = if (bProbe.status == ProbeCheckStatus.PASSED) bProbe.commandHex else "غير مكتشف ❌",
                        sampleRxHex = sampleHex
                    )
                )
                appendLog(ProberLogDirection.SUCCESS, "══ اكتمل الفحص الشامل بدون أي تعديل على الفلاش ══")
            } catch (_: CancellationException) {
                appendLog(ProberLogDirection.WARN, "تم إيقاف الفحص فوراً بناءً على طلب المشرف.")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "تم الإيقاف الفوري",
                    result = ProberResult(
                        kind = ProberOutcomeKind.STOPPED,
                        headline = "تم إيقاف الفحص فوراً",
                        detail = "تم تحرير منفذ USB وإيقاف جميع الحزم بأمان."
                    )
                )
            } catch (t: Throwable) {
                appendLog(ProberLogDirection.ERROR, "خطأ غير متوقع: ${t.message ?: t::class.java.simpleName}")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "حدث خطأ أثناء الفحص",
                    result = ProberResult(
                        kind = ProberOutcomeKind.ERROR,
                        headline = "فشل الفحص بسبب خطأ تقني",
                        detail = t.message ?: t::class.java.simpleName
                    )
                )
            } finally {
                closeUsbSession()
            }
        }
    }

    /**
     * Section 4: Dangerous / Modification Schools — strictly manual & gated by double confirmation UI.
     */
    fun executeDangerousSchoolManual(school: DangerousSchoolType) {
        if (activeProbeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeProbeJob = scope.launch {
            _state.value = _state.value.copy(
                isProbing = true,
                stageLabel = "تجربة يدوية محفوفة بالمخاطر: ${school.title}",
                result = ProberResult(
                    kind = ProberOutcomeKind.RUNNING,
                    headline = "جاري تنفيذ: ${school.title}",
                    detail = school.warningText
                )
            )
            appendLog(ProberLogDirection.WARN, "⚠️ بدء التجربة اليدوية بتأكيد مزدوج: ${school.title}")

            try {
                if (!openUsbSession()) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل فتح منفذ USB",
                        result = ProberResult(
                            kind = ProberOutcomeKind.ERROR,
                            headline = "تعذر فتح منفذ USB OTG",
                            detail = "تأكد من توصيل المبرمجة ومنح الصلاحية."
                        )
                    )
                    return@launch
                }

                val sync = performSilentHandshake(family)
                if (sync == null) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل التزامن في التجربة اليدوية",
                        result = ProberResult(
                            kind = ProberOutcomeKind.NO_SYNC,
                            headline = "لم يستجب المعالج في التجربة اليدوية",
                            detail = "تعذر تزامن المعالج قبل تنفيذ ${school.title}."
                        )
                    )
                    return@launch
                }

                val reportDetail = when (school) {
                    DangerousSchoolType.BLOCK0_ZEROING -> runManualBlock0ZeroingCheck(sync)
                    DangerousSchoolType.PARTIAL_BIT_OVERWRITE -> runManualPartialBitOverwriteCheck(sync)
                    DangerousSchoolType.SECURITY_AUTO_ERASE -> runManualSecurityAutoEraseCheck(sync)
                }

                appendLog(ProberLogDirection.WARN, "نتيجة التجربة اليدوية:\n$reportDetail")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "انتهت التجربة اليدوية",
                    result = ProberResult(
                        kind = ProberOutcomeKind.DANGEROUS_MANUAL_RESULT,
                        headline = "نتيجة التجربة اليدوية: ${school.title}",
                        detail = "$reportDetail\n\n$DATA_SHEET_REMINDER",
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        polaritySummary = sync.polarityName
                    )
                )
            } catch (_: CancellationException) {
                appendLog(ProberLogDirection.WARN, "تم إيقاف التجربة اليدوية فوراً.")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "تم الإيقاف الفوري",
                    result = ProberResult(
                        kind = ProberOutcomeKind.STOPPED,
                        headline = "تم إيقاف التجربة اليدوية",
                        detail = "تم فصل المنفذ فوراً."
                    )
                )
            } catch (t: Throwable) {
                appendLog(ProberLogDirection.ERROR, "خطأ في التجربة اليدوية: ${t.message}")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "خطأ في التجربة اليدوية",
                    result = ProberResult(
                        kind = ProberOutcomeKind.ERROR,
                        headline = "فشل التجربة اليدوية",
                        detail = t.message ?: t::class.java.simpleName
                    )
                )
            } finally {
                closeUsbSession()
            }
        }
    }

    fun stopImmediately() {
        val job = activeProbeJob
        activeProbeJob = null
        job?.cancel()
        closeUsbSession()
        if (_state.value.isProbing) {
            _state.value = _state.value.copy(
                isProbing = false,
                stageLabel = "تم الإيقاف الفوري",
                result = ProberResult(
                    kind = ProberOutcomeKind.STOPPED,
                    headline = "تم إيقاف الفحص فوراً",
                    detail = "تم قطع الاتصال وتحرير منفذ USB."
                )
            )
        }
    }

    /**
     * Section 5: Structured Technical Report for copying & sharing.
     */
    fun buildTechnicalReport(): String {
        val s = _state.value
        return buildString {
            appendLine("══════════════════════════════════════")
            appendLine("التقرير الفني — مختبر المشرفين (مكتشف البروتوكول)")
            appendLine("══════════════════════════════════════")
            appendLine("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("المبرمجة: ${s.usbStatus.adapterName} (${s.usbStatus.chipModel}) [${s.usbStatus.vidPidHex}]")
            appendLine("العائلة المختارة: ${s.selectedFamily.labelAr}")
            appendLine("سرعة الاتصال المكتشفة: ${s.result.baudRate?.let { "$it bps" } ?: "--"}")
            appendLine("نوع الإطار المكتشف: ${s.result.framingType}")
            appendLine("قطبية خطوط التحكم: ${s.result.polaritySummary}")
            if (s.result.detectedSignature.isNotBlank()) {
                appendLine("توقيع الشريحة: ${s.result.detectedSignature}")
            }
            appendLine("")
            appendLine("── 1) حالة مدارس السحب الآمنة ──")
            s.readSchools.forEach { item ->
                val mark = when (item.status) {
                    ProbeCheckStatus.PASSED -> "✅ نجحت"
                    ProbeCheckStatus.FAILED -> "❌ فشلت"
                    ProbeCheckStatus.RUNNING -> "⏳ جاري"
                    ProbeCheckStatus.PENDING -> "• لم يُفحص"
                }
                appendLine("$mark | ${item.title} [${item.commandHex}]")
                if (item.detail.isNotBlank()) appendLine("   ↳ ${item.detail}")
            }
            appendLine("الخلاصة (السحب): ${s.result.winningReadMethod}")
            appendLine("")
            appendLine("── 2) أوامر الكتابة والمسح الآمنة ──")
            s.writeEraseProbes.forEach { item ->
                val mark = when (item.status) {
                    ProbeCheckStatus.PASSED -> "✅ مدعوم"
                    ProbeCheckStatus.FAILED -> "❌ غير مدعوم"
                    ProbeCheckStatus.RUNNING -> "⏳ جاري"
                    ProbeCheckStatus.PENDING -> "• لم يُفحص"
                }
                appendLine("$mark | ${item.title} [${item.commandHex}]")
                if (item.detail.isNotBlank()) appendLine("   ↳ ${item.detail}")
            }
            appendLine("أمر الكتابة المكتشف: ${s.result.supportedWriteCmd}")
            appendLine("أمر المسح المكتشف: ${s.result.supportedEraseCmd}")
            appendLine("أمر فحص الفراغ: ${s.result.supportedBlankCheckCmd}")
            appendLine("")
            appendLine(DATA_SHEET_REMINDER)
            appendLine("")
            appendLine("── آخر سجل حزم TX/RX (${s.logs.size}) ──")
            s.logs.takeLast(80).forEach { entry ->
                appendLine("[${entry.timestamp}] [${entry.direction.name}] ${entry.message}")
            }
        }
    }

    fun release() {
        stopImmediately()
        try {
            usbDetachReceiver?.let { appContext.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        usbDetachReceiver = null
    }

    // ==================== 1. SILENT HANDSHAKE ====================

    private suspend fun performSilentHandshake(family: ProberTargetFamily): SyncLockResult? {
        val bauds = when (family) {
            ProberTargetFamily.NEC_78K0 -> listOf(9600, 19200, 38400, 115200)
            ProberTargetFamily.STM32_UART -> listOf(115200, 38400, 19200, 9600)
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(9600, 38400, 19200, 115200)
            else -> listOf(9600, 19200, 38400, 115200)
        }

        val polarities = listOf(
            Triple("عادي (FLMD0+RESET)", false, true),
            Triple("معكوس (Inverted DTR/RTS)", true, true),
            Triple("مباشر بدون Reset", false, false)
        )

        val pulses: List<Pair<String, ByteArray>> = when (family) {
            ProberTargetFamily.NEC_78K0 -> listOf(
                "0x00 (NEC Reset Sync)" to byteArrayOf(0x00, 0x00),
                "NEC Frame Reset [0x00]" to buildNecCommandFrame(0x00),
                "0x55" to byteArrayOf(0x55)
            )
            ProberTargetFamily.STM32_UART -> listOf(
                "0x7F (STM32 Sync)" to byteArrayOf(0x7F),
                "0x00" to byteArrayOf(0x00),
                "0x55" to byteArrayOf(0x55)
            )
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(
                "0x00 (R8C/RL78 Sync)" to byteArrayOf(0x00),
                "0xB0" to byteArrayOf(0xB0.toByte()),
                "0x55" to byteArrayOf(0x55),
                "0xA0" to byteArrayOf(0xA0.toByte())
            )
            else -> listOf(
                "0x00 (Reset/Sync)" to byteArrayOf(0x00, 0x00),
                "0x7F (STM32/Boot)" to byteArrayOf(0x7F),
                "0x55 (Auto-Baud)" to byteArrayOf(0x55),
                "0xA0 (Boot Pulse)" to byteArrayOf(0xA0.toByte())
            )
        }

        for (baud in bauds) {
            currentCoroutineContext().ensureActive()
            configureUart(baud)
            appendLog(ProberLogDirection.INFO, "── تمهيد صامت: سرعة $baud bps ──")

            for ((polLabel, inverted, doReset) in polarities) {
                currentCoroutineContext().ensureActive()
                if (doReset) {
                    pulseResetSequence(inverted)
                } else {
                    setControlLines(dtr = false, rts = false)
                    delay(40)
                }
                flushRx()

                for ((pulseName, pulseBytes) in pulses) {
                    currentCoroutineContext().ensureActive()
                    val rx = transceiveAndLog(
                        txBytes = pulseBytes,
                        expectedMax = 24,
                        timeoutMs = 220,
                        label = "SilentSync [$pulseName @ $baud | $polLabel]"
                    )
                    if (rx.isEmpty()) continue

                    if (rx.contentEquals(pulseBytes)) {
                        echoLoopbackObserved = true
                        val necReset = buildNecCommandFrame(0x00)
                        val genuine = transceiveWithEchoCancel(necReset, expectedExtra = 8, timeoutMs = 260, label = "NEC Reset خلف الصدى")
                        if (genuine.isNotEmpty()) {
                            return SyncLockResult(
                                baudRate = baud,
                                polarityName = "$polLabel + Single-Wire Echo",
                                invertedPolarity = inverted,
                                useResetToggle = doReset,
                                syncPulseHex = "0x00 -> NEC Reset",
                                framing = classifyFraming(genuine, pulseBytes),
                                firstReplyHex = toHex(genuine),
                                echoLoopbackDetected = true
                            )
                        }
                        continue
                    }

                    val genuine = stripEchoPrefix(pulseBytes, rx)
                    if (genuine.isEmpty()) continue

                    val p0 = pulseBytes[0].toInt() and 0xFF
                    if (p0 == 0x00 && genuine.none { (it.toInt() and 0xFF) == STX }) {
                        delay(20)
                        val necAck = transceiveAutoEcho(
                            buildNecCommandFrame(0x00),
                            expectedMax = 16,
                            timeoutMs = 280,
                            label = "NEC Reset Frame [0x00]"
                        )
                        if (necAck.any { (it.toInt() and 0xFF) == STX }) {
                            return SyncLockResult(
                                baudRate = baud,
                                polarityName = polLabel,
                                invertedPolarity = inverted,
                                useResetToggle = doReset,
                                syncPulseHex = "0x00 + Frame Reset",
                                framing = DetectedFraming.NEC_STX_ETX,
                                firstReplyHex = toHex(necAck),
                                echoLoopbackDetected = echoLoopbackObserved
                            )
                        }
                    }

                    return SyncLockResult(
                        baudRate = baud,
                        polarityName = polLabel,
                        invertedPolarity = inverted,
                        useResetToggle = doReset,
                        syncPulseHex = toHex(pulseBytes),
                        framing = classifyFraming(genuine, pulseBytes),
                        firstReplyHex = toHex(genuine),
                        echoLoopbackDetected = echoLoopbackObserved
                    )
                }
            }
        }
        return null
    }

    private suspend fun resyncLight(sync: SyncLockResult) {
        currentCoroutineContext().ensureActive()
        if (sync.framing == DetectedFraming.NEC_STX_ETX) {
            transceiveAutoEcho(buildNecCommandFrame(0x00), expectedMax = 12, timeoutMs = 200, label = "Light Resync [0x00]")
        } else {
            delay(20)
            flushRx()
        }
    }

    // ==================== 2. SECTION 1: 5 SAFE READ SCHOOLS ====================

    private suspend fun probeSchool1DirectRead(sync: SyncLockResult): SchoolProbeItem {
        markReadSchoolRunning("direct_read")
        appendLog(ProberLogDirection.INFO, "── فحص 1: القراءة المباشرة (0x11, 0x03, 0x18, 0x74, 0xBA, 0xFF) ──")
        val cmds = listOf(0x11, 0x03, 0x18, 0x74, 0xBA, 0xFF)

        if (sync.framing == DetectedFraming.STM32_ACK) {
            val ack1 = transceiveAutoEcho(byteArrayOf(0x11, 0xEE.toByte()), expectedMax = 4, timeoutMs = 280, label = "STM32 Read [0x11]")
            if (ack1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val ack2 = transceiveAutoEcho(byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x08), expectedMax = 4, timeoutMs = 280, label = "STM32 Addr 0x08000000")
                if (ack2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val data = transceiveAutoEcho(byteArrayOf(0x0F, 0xF0.toByte()), expectedMax = 24, timeoutMs = 420, label = "STM32 Read 16B")
                    if (data.size >= 17 && (data[0].toInt() and 0xFF) == ACK_STM32) {
                        val slice = data.copyOfRange(1, 17)
                        return SchoolProbeItem(
                            id = "direct_read",
                            title = "1. مدرسة القراءة المباشرة (Direct Read)",
                            subtitle = "نجحت قراءة أول 16 بايت مباشرة",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = "0x11",
                            detail = "16B: ${toHex(slice)}"
                        )
                    }
                }
            }
            return SchoolProbeItem(
                id = "direct_read",
                title = "1. مدرسة القراءة المباشرة (Direct Read)",
                subtitle = "الأمر 0x11 مرفوض (حماية RDP أو NACK)",
                status = ProbeCheckStatus.FAILED,
                commandHex = "0x11",
                detail = "لم يُسمح بسحب 16 بايت"
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
                val rx = transceiveAutoEcho(pkt, expectedMax = 32, timeoutMs = 340, label = "Direct Read [0x%02X]".format(cmd))
                val payload16 = extract16BytePayload(rx, sync.framing)
                if (payload16 != null) {
                    val hex = "0x%02X".format(cmd)
                    return SchoolProbeItem(
                        id = "direct_read",
                        title = "1. مدرسة القراءة المباشرة (Direct Read)",
                        subtitle = "استجاب المعالج وأرجع 16 بايت حقيقية",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = hex,
                        detail = "16B: ${toHex(payload16)}"
                    )
                }
            }
        }

        return SchoolProbeItem(
            id = "direct_read",
            title = "1. مدرسة القراءة المباشرة (Direct Read)",
            subtitle = "فشلت — المعالج يرفض القراءة المباشرة",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "رفضت جميع أوامر القراءة المباشرة (0x11..0xFF)"
        )
    }

    private suspend fun probeSchool2Checksum1B(sync: SyncLockResult): SchoolProbeItem {
        markReadSchoolRunning("checksum_1b")
        appendLog(ProberLogDirection.INFO, "── فحص 2: السحب عبر بصمة بايت واحد (0xB0 Checksum) ──")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            return SchoolProbeItem(
                id = "checksum_1b",
                title = "2. مدرسة السحب عبر بصمة بايت واحد (1-Byte Checksum)",
                subtitle = "غير منطبق على STM32 UART",
                status = ProbeCheckStatus.FAILED,
                commandHex = "0xB0",
                detail = "بروتوكول STM32 لا يستخدم 0xB0"
            )
        }

        val cmd1 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        val rx1 = transceiveAutoEcho(cmd1, expectedMax = 16, timeoutMs = 360, label = "Checksum 1B [0xB0]")
        val cs1 = parseChecksum16(rx1) ?: run {
            if (parseNecStatus(rx1) == ACK_NEC) {
                parseChecksum16(readReplyWithLog(12, 320, "RX CS 1B Payload"))
            } else null
        }

        val cmd2 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
        val rx2 = transceiveAutoEcho(cmd2, expectedMax = 16, timeoutMs = 360, label = "Checksum 2B [0xB0]")
        val cs2 = parseChecksum16(rx2) ?: run {
            if (parseNecStatus(rx2) == ACK_NEC) {
                parseChecksum16(readReplyWithLog(12, 320, "RX CS 2B Payload"))
            } else null
        }

        return if (cs1 != null && cs2 != null) {
            SchoolProbeItem(
                id = "checksum_1b",
                title = "2. مدرسة السحب عبر بصمة بايت واحد (1-Byte Checksum)",
                subtitle = "يدعم حساب Checksum لبايت واحد وبايتين بدقة",
                status = ProbeCheckStatus.PASSED,
                commandHex = "0xB0",
                detail = "CS(1B)=0x%04X | CS(2B)=0x%04X".format(cs1, cs2)
            )
        } else {
            SchoolProbeItem(
                id = "checksum_1b",
                title = "2. مدرسة السحب عبر بصمة بايت واحد (1-Byte Checksum)",
                subtitle = "فشلت — المعالج لم يقبل نطاق 1B/2B في 0xB0",
                status = ProbeCheckStatus.FAILED,
                commandHex = "0xB0",
                detail = "لم يرجع المعالج بصمتين صالحتين"
            )
        }
    }

    private suspend fun probeSchool3Verify0x13(sync: SyncLockResult): SchoolProbeItem {
        markReadSchoolRunning("verify_0x13")
        appendLog(ProberLogDirection.INFO, "── فحص 3: التحقق التدريجي (Verify Probe 0x13) على صفحة فارغة ──")
        if (sync.framing == DetectedFraming.STM32_ACK) {
            return SchoolProbeItem(
                id = "verify_0x13",
                title = "3. مدرسة التحقق التدريجي (Verify Probe 0x13)",
                subtitle = "غير مدعوم في STM32 Bootloader",
                status = ProbeCheckStatus.FAILED,
                commandHex = "0x13",
                detail = "STM32 لا يدعم أمر 0x13"
            )
        }

        // Send 0x13 Verify header on page 0 (0x000000..0x0000FF) and compare with 256 x 0xFF (100% read-only compare!)
        val verifyHdr = buildNecCommandFrame(0x13, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()))
        val hdrRx = transceiveAutoEcho(verifyHdr, expectedMax = 12, timeoutMs = 360, label = "Verify Header [0x13 Page 0]")
        val hdrSts = parseNecStatus(hdrRx)

        if (hdrSts == ACK_NEC) {
            val ff256 = ByteArray(256) { 0xFF.toByte() }
            val dataFrame = buildNecDataFrame(ff256, isLast = true)
            val dataRx = transceiveAutoEcho(dataFrame, expectedMax = 16, timeoutMs = 550, label = "Verify Compare 256x0xFF [0x13]")
            val sts = parseNecStatus(dataRx)
            val secondSts = parseNecSecondStatus(dataRx)
            val effective = if (sts == ACK_NEC && secondSts != null && secondSts != ACK_NEC) secondSts else sts

            if (effective == ACK_NEC || effective == STS_VERIFY_ERROR || effective == STS_VERIFY_ERROR_1C) {
                val meaning = if (effective == ACK_NEC) "تطابقت الصفحة (فارغة 0xFF)" else "رد Compare Mismatch (0x%02X) — الأمر 0x13 يعمل!".format(effective)
                return SchoolProbeItem(
                    id = "verify_0x13",
                    title = "3. مدرسة التحقق التدريجي (Verify Probe 0x13)",
                    subtitle = "أمر 0x13 فعال ويقارن الصفحات بدون كتابة",
                    status = ProbeCheckStatus.PASSED,
                    commandHex = "0x13",
                    detail = meaning
                )
            }
        }

        return SchoolProbeItem(
            id = "verify_0x13",
            title = "3. مدرسة التحقق التدريجي (Verify Probe 0x13)",
            subtitle = "فشلت — لم يقبل المعالج أمر التحقق 0x13",
            status = ProbeCheckStatus.FAILED,
            commandHex = "0x13",
            detail = "الرد: ${formatByte(hdrSts)}"
        )
    }

    private suspend fun probeSchool4SignatureWindow(sync: SyncLockResult): SchoolProbeItem {
        markReadSchoolRunning("signature_window")
        appendLog(ProberLogDirection.INFO, "── فحص 4: نافذة السجلات والتوقيع (0xC0, 0xC5, 0x01, 0x02) ──")
        val cmds = listOf(0xC0, 0xC5, 0x01, 0x02)

        for (cmd in cmds) {
            currentCoroutineContext().ensureActive()
            val tx = when (sync.framing) {
                DetectedFraming.STM32_ACK -> byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte())
                DetectedFraming.NEC_STX_ETX -> buildNecCommandFrame(cmd)
                else -> byteArrayOf(cmd.toByte())
            }
            val rx = transceiveAutoEcho(tx, expectedMax = 48, timeoutMs = 340, label = "Signature/Reg Probe [0x%02X]".format(cmd))
            if (rx.isEmpty()) continue

            if (sync.framing == DetectedFraming.STM32_ACK) {
                if (rx.size >= 2 && (rx[0].toInt() and 0xFF) == ACK_STM32) {
                    return SchoolProbeItem(
                        id = "signature_window",
                        title = "4. مدرسة قراءة نافذة السجلات والتوقيع (Signature/Window Read)",
                        subtitle = "استجاب المعالج لقراءة معرف الشريحة/الإصدار",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = "0x%02X".format(cmd),
                        detail = "RX: ${toHex(rx)}"
                    )
                }
            } else if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                val sts = parseNecStatus(rx)
                if (sts == ACK_NEC) {
                    val extra = if (!rx.drop(4).any { (it.toInt() and 0xFF) == STX }) {
                        readReplyWithLog(48, 360, "RX Signature Payload [0x%02X]".format(cmd))
                    } else ByteArray(0)
                    val combined = rx + extra
                    val ascii = combined.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
                    val info = if (ascii.isNotBlank()) "$ascii | ${toHex(combined.take(24).toByteArray())}" else toHex(combined.take(24).toByteArray())
                    return SchoolProbeItem(
                        id = "signature_window",
                        title = "4. مدرسة قراءة نافذة السجلات والتوقيع (Signature/Window Read)",
                        subtitle = "نجحت قراءة التوقيع/السجلات بأمان",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = "0x%02X".format(cmd),
                        detail = info
                    )
                }
            } else {
                val first = rx[0].toInt() and 0xFF
                if (first != STS_CMD_ERROR && first != NACK_STM32 && first != STS_NACK) {
                    return SchoolProbeItem(
                        id = "signature_window",
                        title = "4. مدرسة قراءة نافذة السجلات والتوقيع (Signature/Window Read)",
                        subtitle = "استجاب المعالج لنافذة التوقيع/السجلات",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = "0x%02X".format(cmd),
                        detail = "RX: ${toHex(rx)}"
                    )
                }
            }
        }

        return SchoolProbeItem(
            id = "signature_window",
            title = "4. مدرسة قراءة نافذة السجلات والتوقيع (Signature/Window Read)",
            subtitle = "فشلت — لم تتوفر استجابة توقيع صالحة",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "رفضت الأوامر (0xC0, 0xC5, 0x01, 0x02)"
        )
    }

    private suspend fun probeSchool5RamStub(sync: SyncLockResult): SchoolProbeItem {
        markReadSchoolRunning("ram_stub")
        appendLog(ProberLogDirection.INFO, "── فحص 5: حقن الكود في الرام المؤقت (RAM Stub Injection) ──")

        if (sync.framing == DetectedFraming.STM32_ACK) {
            val ack1 = transceiveAutoEcho(byteArrayOf(0x31, 0xCE.toByte()), expectedMax = 4, timeoutMs = 280, label = "STM32 RAM Write [0x31]")
            if (ack1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val addrAck = transceiveAutoEcho(byteArrayOf(0x20, 0x00, 0x10, 0x00, 0x30), expectedMax = 4, timeoutMs = 280, label = "STM32 SRAM 0x20001000")
                if (addrAck.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    return SchoolProbeItem(
                        id = "ram_stub",
                        title = "5. مدرسة حقن الكود في الرام (RAM Stub Injection)",
                        subtitle = "قبل المعالج عنوان الرام المؤقت 0x20001000",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = "0x31",
                        detail = "استجابة ACK على عنوان SRAM بدون لمس الفلاش"
                    )
                }
            }
            return SchoolProbeItem(
                id = "ram_stub",
                title = "5. مدرسة حقن الكود في الرام (RAM Stub Injection)",
                subtitle = "فشلت — رفض المعالج الكتابة في الرام",
                status = ProbeCheckStatus.FAILED,
                commandHex = "0x31",
                detail = "NACK / RDP Protected"
            )
        }

        // Strict RAM-only address >= 0x00FEF0 (never Flash!)
        val ramProbes = listOf(
            0x31 to buildNecCommandFrame(0x31, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte())),
            0x40 to buildNecCommandFrame(0x40, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte()))
        )
        for ((cmd, frame) in ramProbes) {
            val rx = transceiveAutoEcho(frame, expectedMax = 12, timeoutMs = 300, label = "RAM Address Probe [0x%02X @ 0x00FEF0]".format(cmd))
            if (parseNecStatus(rx) == ACK_NEC) {
                val hex = "0x%02X".format(cmd)
                return SchoolProbeItem(
                    id = "ram_stub",
                    title = "5. مدرسة حقن الكود في الرام (RAM Stub Injection)",
                    subtitle = "استجاب بـ ACK لعنوان الرام 0x00FEF0 دون لمس الفلاش",
                    status = ProbeCheckStatus.PASSED,
                    commandHex = hex,
                    detail = "ACK على نطاق الرام الآمن"
                )
            }
        }

        return SchoolProbeItem(
            id = "ram_stub",
            title = "5. مدرسة حقن الكود في الرام (RAM Stub Injection)",
            subtitle = "فشلت — لا يدعم البوت لودر تحميل RAM Stub",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "رفض عناوين الرام المؤقتة"
        )
    }

    // ==================== 3. SECTION 2: SAFE WRITE / ERASE / BLANK CHECK PROBES ====================

    /**
     * Strictly header-only Write Command recognition probe:
     * Tests (0x40, 0x31, 0x41, 0x50) WITHOUT writing any flash payload or flash block!
     */
    private suspend fun probeSafeWriteCommandHeader(sync: SyncLockResult): SchoolProbeItem {
        markWriteProbeRunning("write_cmd_probe")
        appendLog(ProberLogDirection.INFO, "── فحص الكتابة الآمن (Header-Only): 0x40, 0x31, 0x41, 0x50 ──")
        val writeCmds = listOf(0x40, 0x31, 0x41, 0x50)

        for (cmd in writeCmds) {
            currentCoroutineContext().ensureActive()
            val hex = "0x%02X".format(cmd)

            when (sync.framing) {
                DetectedFraming.STM32_ACK -> {
                    // Send ONLY 2-byte command header [cmd, cmd^0xFF], no address/data -> 100% safe
                    val rx = transceiveAutoEcho(byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte()), expectedMax = 4, timeoutMs = 260, label = "STM32 Write Header [$hex]")
                    if (rx.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                        // Resync/reset out of waiting state cleanly
                        pulseResetSequence(sync.invertedPolarity)
                        return SchoolProbeItem(
                            id = "write_cmd_probe",
                            title = "1. اكتشاف أمر الكتابة (Write Command Probe)",
                            subtitle = "أمر الكتابة المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = "رد المعالج بـ 0x79 ACK على ترويسة الأمر فقط (بدون كتابة أي بايت)"
                        )
                    }
                }
                DetectedFraming.NEC_STX_ETX -> {
                    // Send parameter-less command header [SOH, 0x01, cmd, cs, ETX] (NO address, NO data!)
                    // 0x04 = Command Error (unknown). 0x05 (Parameter Error) or 0x06 (ACK) or 0x10 (Protect) = Command Recognized!
                    val hdrOnly = buildNecCommandFrame(cmd)
                    val rx = transceiveAutoEcho(hdrOnly, expectedMax = 12, timeoutMs = 280, label = "NEC Write Header-Only [$hex]")
                    val sts = parseNecStatus(rx)
                    if (sts == ACK_NEC || sts == STS_PARAM_ERROR) {
                        val reason = if (sts == ACK_NEC) "ACK (جاهز لاستقبال العنوان والبيانات)" else "Parameter Error 0x05 (المعالج تعرّف على الأمر وطلب المعاملات)"
                        return SchoolProbeItem(
                            id = "write_cmd_probe",
                            title = "1. اكتشاف أمر الكتابة (Write Command Probe)",
                            subtitle = "أمر الكتابة المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = "$reason — فحص ترويسة آمن 100%"
                        )
                    }
                }
                else -> {
                    val rx = transceiveAutoEcho(byteArrayOf(cmd.toByte()), expectedMax = 6, timeoutMs = 220, label = "Raw Write Header [$hex]")
                    val first = rx.firstOrNull()?.let { it.toInt() and 0xFF }
                    if (first != null && first != STS_CMD_ERROR && first != NACK_STM32 && first != STS_NACK) {
                        return SchoolProbeItem(
                            id = "write_cmd_probe",
                            title = "1. اكتشاف أمر الكتابة (Write Command Probe)",
                            subtitle = "أمر الكتابة المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = "رد المعالج بدون رفض أمر مجهول (RX: ${toHex(rx)})"
                        )
                    }
                }
            }
        }

        return SchoolProbeItem(
            id = "write_cmd_probe",
            title = "1. اكتشاف أمر الكتابة (Write Command Probe)",
            subtitle = "لم يتعرف المعالج على أوامر الكتابة أو محمي",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "فشلت ترويسات (0x40, 0x31, 0x41, 0x50)"
        )
    }

    /**
     * Strictly safe Erase Command recognition probe:
     * Tests (0x20, 0x22, 0x43, 0x44) WITHOUT real flash block addresses so nothing can ever be erased!
     */
    private suspend fun probeSafeEraseCommandHeader(sync: SyncLockResult): SchoolProbeItem {
        markWriteProbeRunning("erase_cmd_probe")
        appendLog(ProberLogDirection.INFO, "── فحص المسح الآمن (Header-Only): 0x20, 0x22, 0x43, 0x44 ──")
        val eraseCmds = listOf(0x20, 0x22, 0x43, 0x44)

        for (cmd in eraseCmds) {
            currentCoroutineContext().ensureActive()
            val hex = "0x%02X".format(cmd)

            when (sync.framing) {
                DetectedFraming.STM32_ACK -> {
                    // Send ONLY 2-byte command header [cmd, cmd^0xFF], no page count -> 100% safe
                    val rx = transceiveAutoEcho(byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte()), expectedMax = 4, timeoutMs = 260, label = "STM32 Erase Header [$hex]")
                    if (rx.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                        pulseResetSequence(sync.invertedPolarity)
                        return SchoolProbeItem(
                            id = "erase_cmd_probe",
                            title = "2. اكتشاف أمر المسح (Erase Command Probe)",
                            subtitle = "أمر المسح المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = "رد المعالج بـ 0x79 ACK على ترويسة المسح بدون تنفيذ مسح فعلي"
                        )
                    }
                }
                DetectedFraming.NEC_STX_ETX -> {
                    // SAFETY RULE: NEVER send 1-byte 0x20 (LEN=1) in case a legacy 78K0 variant treats 0x20 without args as Chip Erase!
                    // Instead:
                    // - For 0x22/0x43/0x44: send LEN=1 header-only [SOH, 0x01, cmd, cs, ETX] -> no block address => 0x05 Parameter Error if recognized.
                    // - For 0x20: send impossible out-of-range dummy params (0xFFFFFF..0xFFFFFF) => safe 0x05 Parameter Error if recognized, 0x04 if unknown!
                    val safeProbeFrame = if (cmd == 0x20) {
                        buildNecCommandFrame(
                            0x20,
                            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
                        )
                    } else {
                        buildNecCommandFrame(cmd)
                    }

                    val rx = transceiveAutoEcho(safeProbeFrame, expectedMax = 12, timeoutMs = 280, label = "NEC Safe Erase Header [$hex]")
                    val sts = parseNecStatus(rx)
                    if (sts == STS_PARAM_ERROR || sts == ACK_NEC) {
                        val reason = if (sts == STS_PARAM_ERROR) {
                            "تعرّف المعالج على الأمر (0x05 طلب معاملات صحيحة) بدون مسح أي بلوك"
                        } else {
                            "استجابة ACK على فحص الترويسة بدون عنوان مسح"
                        }
                        return SchoolProbeItem(
                            id = "erase_cmd_probe",
                            title = "2. اكتشاف أمر المسح (Erase Command Probe)",
                            subtitle = "أمر المسح المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = reason
                        )
                    }
                }
                else -> {
                    // For Renesas/Raw, test safe header-only byte (skip raw standalone 0x20 without sub-args unless framed)
                    if (cmd == 0x20) continue
                    val rx = transceiveAutoEcho(byteArrayOf(cmd.toByte()), expectedMax = 6, timeoutMs = 220, label = "Raw Erase Header [$hex]")
                    val first = rx.firstOrNull()?.let { it.toInt() and 0xFF }
                    if (first != null && first != STS_CMD_ERROR && first != NACK_STM32 && first != STS_NACK) {
                        return SchoolProbeItem(
                            id = "erase_cmd_probe",
                            title = "2. اكتشاف أمر المسح (Erase Command Probe)",
                            subtitle = "أمر المسح المدعوم: [$hex]",
                            status = ProbeCheckStatus.PASSED,
                            commandHex = hex,
                            detail = "رد المعالج على ترويسة المسح: ${toHex(rx)}"
                        )
                    }
                }
            }
        }

        return SchoolProbeItem(
            id = "erase_cmd_probe",
            title = "2. اكتشاف أمر المسح (Erase Command Probe)",
            subtitle = "لم يتعرف المعالج على أمر مسح أو محمي",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "فشلت ترويسات (0x20, 0x22, 0x43, 0x44)"
        )
    }

    /**
     * 100% read-only Blank Check command recognition (0x30, 0x32, 0xD0).
     */
    private suspend fun probeSafeBlankCheck(sync: SyncLockResult): SchoolProbeItem {
        markWriteProbeRunning("blank_check_probe")
        appendLog(ProberLogDirection.INFO, "── فحص الفراغ الآمن (Blank Check): 0x30, 0x32, 0xD0 ──")
        val blankCmds = listOf(0x30, 0x32, 0xD0)

        for (cmd in blankCmds) {
            currentCoroutineContext().ensureActive()
            val hex = "0x%02X".format(cmd)
            val tx = when (sync.framing) {
                DetectedFraming.STM32_ACK -> byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte())
                DetectedFraming.NEC_STX_ETX -> buildNecCommandFrame(cmd, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte()))
                else -> byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())
            }
            val rx = transceiveAutoEcho(tx, expectedMax = 12, timeoutMs = 300, label = "Blank Check Probe [$hex]")
            if (rx.isEmpty()) continue

            if (sync.framing == DetectedFraming.STM32_ACK) {
                if (rx.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    return SchoolProbeItem(
                        id = "blank_check_probe",
                        title = "3. اكتشاف فحص الفراغ (Blank Check Probe)",
                        subtitle = "أمر فحص الفراغ المدعوم: [$hex]",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = hex,
                        detail = "ACK 0x79"
                    )
                }
            } else if (sync.framing == DetectedFraming.NEC_STX_ETX) {
                val sts = parseNecStatus(rx)
                // Both 0x06 (Blank) and 0x1B (Not Blank) prove Blank Check command is active!
                if (sts == ACK_NEC || sts == STS_BLANK_CHECK_ERROR) {
                    val stateLabel = if (sts == ACK_NEC) "0x06 (Block 0 فارغ)" else "0x1B (Not Blank — الفلاش يحتوي على برنامج)"
                    return SchoolProbeItem(
                        id = "blank_check_probe",
                        title = "3. اكتشاف فحص الفراغ (Blank Check Probe)",
                        subtitle = "أمر فحص الفراغ المدعوم: [$hex]",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = hex,
                        detail = "رد المعالج: $stateLabel"
                    )
                }
            } else {
                val first = rx[0].toInt() and 0xFF
                if (first != STS_CMD_ERROR && first != NACK_STM32 && first != STS_NACK) {
                    return SchoolProbeItem(
                        id = "blank_check_probe",
                        title = "3. اكتشاف فحص الفراغ (Blank Check Probe)",
                        subtitle = "أمر فحص الفراغ المدعوم: [$hex]",
                        status = ProbeCheckStatus.PASSED,
                        commandHex = hex,
                        detail = "RX: ${toHex(rx)}"
                    )
                }
            }
        }

        return SchoolProbeItem(
            id = "blank_check_probe",
            title = "3. اكتشاف فحص الفراغ (Blank Check Probe)",
            subtitle = "فشل — أوامر (0x30, 0x32, 0xD0) غير مدعومة",
            status = ProbeCheckStatus.FAILED,
            commandHex = "--",
            detail = "لم يتعرف المعالج على أمر فحص الفراغ"
        )
    }

    // ==================== 4. MANUAL DANGEROUS SCHOOLS (DOUBLE-CONFIRMED ONLY) ====================

    private suspend fun runManualBlock0ZeroingCheck(sync: SyncLockResult): String {
        val sb = StringBuilder()
        val csBefore = transceiveAutoEcho(
            buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())),
            expectedMax = 16,
            timeoutMs = 380,
            label = "Snapshot Block-0 Checksum [0xB0]"
        )
        sb.appendLine("• بصمة Block-0 المبدئية: ${toHex(csBefore)}")

        val blank = transceiveAutoEcho(
            buildNecCommandFrame(0x32, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())),
            expectedMax = 12,
            timeoutMs = 380,
            label = "BlankCheck Block-0 [0x32]"
        )
        val blankSts = parseNecStatus(blank)
        sb.appendLine("• حالة Block-0 (0x32): ${formatByte(blankSts)}")

        // Verify whether 0x22 Block Erase is accepted for Block 0 on test board
        val eraseHdr = transceiveAutoEcho(
            buildNecCommandFrame(0x22, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte())),
            expectedMax = 12,
            timeoutMs = 900,
            label = "Manual Block-0 Erase [0x22]"
        )
        val eraseSts = parseNecStatus(eraseHdr)
        sb.appendLine("• استجابة مسح Block-0 (0x22): ${formatByte(eraseSts)}")
        if (eraseSts == ACK_NEC) {
            sb.append("• النتيجة: المعالج يقبل مسح وتصفير Block-0 (أعد شحن السوفت وير الأصلي عند الحاجة).")
        } else {
            sb.append("• النتيجة: رفض المعالج مسح Block-0 (${formatByte(eraseSts)}) — حماية كتابة/مسح مفعّلة.")
        }
        return sb.toString()
    }

    private suspend fun runManualPartialBitOverwriteCheck(sync: SyncLockResult): String {
        val sb = StringBuilder()
        val csBefore = transceiveAutoEcho(
            buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
            expectedMax = 16,
            timeoutMs = 350,
            label = "Byte-0 Checksum Before [0xB0]"
        )
        sb.appendLine("• بصمة أول بايت قبل التجربة: ${toHex(csBefore)}")

        val progHdr = transceiveAutoEcho(
            buildNecCommandFrame(0x40, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
            expectedMax = 12,
            timeoutMs = 380,
            label = "Partial Overwrite Header [0x40 @ 0x000000]"
        )
        val hdrSts = parseNecStatus(progHdr)
        sb.appendLine("• رد ترويسة الكتابة الجزئية (0x40): ${formatByte(hdrSts)}")
        if (hdrSts == ACK_NEC) {
            val zeroFrame = buildNecDataFrame(byteArrayOf(0x00), isLast = true)
            val dataRx = transceiveAutoEcho(zeroFrame, expectedMax = 12, timeoutMs = 450, label = "Write 0x00 @ 0x000000")
            sb.appendLine("• رد كتابة البايت 0x00: ${toHex(dataRx)}")
        }
        return sb.toString().trim()
    }

    private suspend fun runManualSecurityAutoEraseCheck(sync: SyncLockResult): String {
        return when (sync.framing) {
            DetectedFraming.STM32_ACK -> {
                val rx = transceiveAutoEcho(byteArrayOf(0x92.toByte(), 0x6D), expectedMax = 6, timeoutMs = 800, label = "STM32 Readout Unprotect [0x92]")
                "• استجابة STM32 لأمر 0x92 (Readout Unprotect): ${toHex(rx)}"
            }
            DetectedFraming.NEC_STX_ETX -> {
                val rx = transceiveAutoEcho(buildNecCommandFrame(0xA0), expectedMax = 12, timeoutMs = 450, label = "NEC Security Command [0xA0]")
                "• استجابة 78K0 لأمر الحماية (0xA0): ${formatByte(parseNecStatus(rx))} | Raw: ${toHex(rx)}"
            }
            else -> {
                val ffId = byteArrayOf(
                    0xF5.toByte(), 0xFF.toByte(), 0xDF.toByte(), 0x00, 0x07,
                    0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                )
                val rx1 = transceiveAutoEcho(ffId, expectedMax = 8, timeoutMs = 350, label = "R8C ID Unlock FFx7 [0xF5]")
                val sr = transceiveAutoEcho(byteArrayOf(0x70), expectedMax = 6, timeoutMs = 350, label = "R8C SRD [0x70]")
                "• استجابة 0xF5: ${toHex(rx1)}\n• سجل الحالة SRD (0x70): ${toHex(sr)}"
            }
        }
    }

    // ==================== HELPERS & STATE UPDATERS ====================

    private fun markReadSchoolRunning(id: String) {
        _state.value = _state.value.copy(
            readSchools = _state.value.readSchools.map {
                if (it.id == id) it.copy(status = ProbeCheckStatus.RUNNING) else it
            }
        )
    }

    private fun updateReadSchool(updated: SchoolProbeItem) {
        _state.value = _state.value.copy(
            readSchools = _state.value.readSchools.map { if (it.id == updated.id) updated else it }
        )
    }

    private fun markWriteProbeRunning(id: String) {
        _state.value = _state.value.copy(
            writeEraseProbes = _state.value.writeEraseProbes.map {
                if (it.id == id) it.copy(status = ProbeCheckStatus.RUNNING) else it
            }
        )
    }

    private fun updateWriteEraseProbe(updated: SchoolProbeItem) {
        _state.value = _state.value.copy(
            writeEraseProbes = _state.value.writeEraseProbes.map { if (it.id == updated.id) updated else it }
        )
    }

    private fun classifyFraming(reply: ByteArray, pulse: ByteArray): DetectedFraming {
        val first = reply[0].toInt() and 0xFF
        if (reply.any { (it.toInt() and 0xFF) == STX } && reply.any { (it.toInt() and 0xFF) == ETX }) {
            return DetectedFraming.NEC_STX_ETX
        }
        val p0 = pulse.firstOrNull()?.let { it.toInt() and 0xFF } ?: -1
        if (p0 == 0x7F && (first == ACK_STM32 || first == NACK_STM32)) {
            return DetectedFraming.STM32_ACK
        }
        if (first == ACK_NEC || first == 0x80 || first == 0xB0 || first == 0xC1 || first == 0xF0) {
            return DetectedFraming.RENESAS_RAW
        }
        return DetectedFraming.CUSTOM_RAW
    }

    private fun extract16BytePayload(rx: ByteArray, framing: DetectedFraming): ByteArray? {
        if (rx.isEmpty()) return null
        if (framing == DetectedFraming.NEC_STX_ETX) {
            val sts = parseNecStatus(rx)
            if (sts != null && sts in NEC_HARD_REJECT_CODES) return null
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
        if (rx.isEmpty()) return null
        val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (stxIdx >= 0 && rx.size >= stxIdx + 6) {
            val len = rx[stxIdx + 1].toInt() and 0xFF
            if (len == 1) {
                val sts = rx[stxIdx + 2].toInt() and 0xFF
                if (sts in NEC_HARD_REJECT_CODES) return null
            }
            if (len == 2 && rx.size >= stxIdx + 6) {
                val b0 = rx[stxIdx + 2].toInt() and 0xFF
                val b1 = rx[stxIdx + 3].toInt() and 0xFF
                if (b0 == ACK_NEC && b1 in NEC_HARD_REJECT_CODES) return null
                return (b1 shl 8) or b0
            }
        }
        return null
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
        val lenByte = data.size and 0xFF // 256 -> 0x00
        var sum = lenByte
        for (b in data) sum += (b.toInt() and 0xFF)
        val cs = (0 - sum) and 0xFF
        val endByte = if (isLast) ETX else 0x17
        return byteArrayOf(STX.toByte(), lenByte.toByte()) + data + byteArrayOf(cs.toByte(), endByte.toByte())
    }

    // ==================== SELF-CONTAINED USB OTG SERIAL TRANSPORT ====================

    private fun pickBestSerialDevice(devices: List<UsbDevice>): UsbDevice? {
        if (devices.isEmpty()) return null
        return devices.firstOrNull { ProberUsbChip.fromVendorId(it.vendorId) != ProberUsbChip.CDC_ACM }
            ?: devices.first()
    }

    private suspend fun openUsbSession(): Boolean {
        val mgr = usbManager ?: run {
            appendLog(ProberLogDirection.ERROR, "USB Host غير متوفر.")
            return false
        }
        val dev = pickBestSerialDevice(mgr.deviceList.values.toList()) ?: run {
            refreshUsbStatus()
            appendLog(ProberLogDirection.ERROR, "لا توجد مبرمجة USB موصولة.")
            return false
        }
        if (!mgr.hasPermission(dev)) {
            appendLog(ProberLogDirection.INFO, "طلب صلاحية USB OTG...")
            val granted = requestPermissionSuspend(mgr, dev)
            refreshUsbStatus()
            if (!granted) {
                appendLog(ProberLogDirection.ERROR, "لم يتم منح صلاحية USB.")
                return false
            }
        }

        closeUsbSession()
        val conn = mgr.openDevice(dev) ?: run {
            appendLog(ProberLogDirection.ERROR, "تعذر فتح اتصال USB.")
            return false
        }

        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        controlInterfaceIndex = 0
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM || i == 0) {
                controlInterfaceIndex = iface.id
            }
            if (conn.claimInterface(iface, true)) {
                claimedInterfaces.add(iface)
            }
            for (epIdx in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(epIdx)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && bulkIn == null) bulkIn = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) bulkOut = ep
                }
            }
        }

        if (bulkIn == null || bulkOut == null) {
            conn.close()
            appendLog(ProberLogDirection.ERROR, "الجهاز المتصل لا يحتوي على قنوات Serial Bulk صالحة.")
            return false
        }

        synchronized(usbSessionLock) {
            activeDevice = dev
            activeConnection = conn
            inEndpoint = bulkIn
            outEndpoint = bulkOut
            activeChip = ProberUsbChip.fromVendorId(dev.vendorId)
            echoLoopbackObserved = false
        }
        refreshUsbStatus()
        startReaderLoop(conn, bulkIn)
        appendLog(
            ProberLogDirection.INFO,
            "منفذ USB جاهز: ${activeChip.displayName} (VID:%04X PID:%04X)".format(dev.vendorId, dev.productId)
        )
        return true
    }

    private fun closeUsbSession() {
        val job = readerJob
        readerJob = null
        job?.cancel()
        synchronized(rxQueue) { rxQueue.clear() }
        synchronized(usbSessionLock) {
            val conn = activeConnection
            claimedInterfaces.toList().forEach { iface ->
                try { conn?.releaseInterface(iface) } catch (_: Throwable) {}
            }
            claimedInterfaces.clear()
            try { conn?.close() } catch (_: Throwable) {}
            activeConnection = null
            activeDevice = null
            inEndpoint = null
            outEndpoint = null
        }
    }

    private fun startReaderLoop(conn: UsbDeviceConnection, epIn: UsbEndpoint) {
        readerJob?.cancel()
        readerJob = scope.launch {
            val buf = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
            while (isActive && activeConnection === conn) {
                val n = conn.bulkTransfer(epIn, buf, buf.size, 50)
                if (n > 0) {
                    val startIdx = if (activeChip == ProberUsbChip.FT232R) 2 else 0
                    if (n > startIdx) {
                        synchronized(rxQueue) {
                            for (i in startIdx until n) {
                                if (rxQueue.size >= 32768) rxQueue.removeFirst()
                                rxQueue.addLast(buf[i])
                            }
                        }
                    }
                } else if (n < 0) {
                    val name = activeDevice?.deviceName
                    if (name != null && usbManager?.deviceList?.containsKey(name) == false) {
                        appendLog(ProberLogDirection.ERROR, "انفصل كابل USB OTG أثناء الفحص.")
                        break
                    }
                }
            }
        }
    }

    private fun configureUart(baudRate: Int) {
        currentBaud = baudRate
        val conn = activeConnection ?: return
        when (activeChip) {
            ProberUsbChip.FT232R -> {
                conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 400)
                conn.controlTransfer(0x40, 0x09, 0x0002, 0, null, 0, 400)
                val divisor = if (baudRate == 9600) 0x4138 else (3_000_000 / baudRate.coerceAtLeast(300))
                conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 400)
                conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 400)
                conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 400)
            }
            ProberUsbChip.CP2102 -> {
                val idx = controlInterfaceIndex
                conn.controlTransfer(0x41, 0x00, 0x0001, idx, null, 0, 400)
                val baudBytes = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate ushr 8) and 0xFF).toByte(),
                    ((baudRate ushr 16) and 0xFF).toByte(),
                    ((baudRate ushr 24) and 0xFF).toByte()
                )
                val rBaud = conn.controlTransfer(0x41, 0x1E, 0, idx, baudBytes, baudBytes.size, 400)
                if (rBaud < 0) {
                    val div = (3686400 / baudRate.coerceAtLeast(300)) and 0xFFFF
                    conn.controlTransfer(0x41, 0x01, div, idx, null, 0, 400)
                }
                conn.controlTransfer(0x41, 0x03, 0x0800, idx, null, 0, 400)
            }
            ProberUsbChip.CH340G -> {
                conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 400)
                var factor = 1532620800L / baudRate.coerceAtLeast(300)
                var divisor = 3
                while (factor > 0xFFF0L && divisor > 0) {
                    factor = factor shr 3
                    divisor--
                }
                factor = 0x10000L - factor
                val regA = ((factor.toInt() and 0xFF00) or divisor or 0x80)
                val regB = (factor.toInt() and 0xFF)
                conn.controlTransfer(0x40, 0x9A, 0x1312, regA, null, 0, 400)
                conn.controlTransfer(0x40, 0x9A, 0x0F2C, regB, null, 0, 400)
                conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 400)
                conn.controlTransfer(0x40, 0x9A, 0x2727, 0x0000, null, 0, 400)
            }
            else -> {
                val lineCoding = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate ushr 8) and 0xFF).toByte(),
                    ((baudRate ushr 16) and 0xFF).toByte(),
                    ((baudRate ushr 24) and 0xFF).toByte(),
                    0x00, 0x00, 0x08
                )
                conn.controlTransfer(0x21, 0x20, 0, controlInterfaceIndex, lineCoding, lineCoding.size, 400)
                conn.controlTransfer(0x21, 0x22, 0x0003, controlInterfaceIndex, null, 0, 400)
            }
        }
    }

    private fun setControlLines(dtr: Boolean, rts: Boolean) {
        val conn = activeConnection ?: return
        when (activeChip) {
            ProberUsbChip.FT232R -> {
                val value = (if (dtr) 0x0101 else 0x0100) or (if (rts) 0x0202 else 0x0200)
                conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
            }
            ProberUsbChip.CP2102 -> {
                val value = 0x0300 or (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
            }
            ProberUsbChip.CH340G -> {
                val mask = (if (dtr) 0x20 else 0) or (if (rts) 0x40 else 0)
                conn.controlTransfer(0x40, 0xA4, mask.inv() and 0xFF, 0, null, 0, 300)
            }
            else -> {
                val valMask = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                conn.controlTransfer(0x21, 0x22, valMask, controlInterfaceIndex, null, 0, 300)
            }
        }
    }

    private suspend fun pulseResetSequence(inverted: Boolean) {
        val flmd0High = inverted
        val resetLow = !inverted
        val resetHigh = inverted
        setControlLines(dtr = flmd0High, rts = resetLow)
        delay(60)
        setControlLines(dtr = flmd0High, rts = resetHigh)
        delay(100)
    }

    private fun flushRx() {
        synchronized(rxQueue) { rxQueue.clear() }
    }

    private suspend fun transceiveAutoEcho(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        label: String
    ): ByteArray {
        return if (echoLoopbackObserved) {
            transceiveWithEchoCancel(txBytes, expectedExtra = expectedMax, timeoutMs = timeoutMs, label = label)
        } else {
            val rx = transceiveAndLog(txBytes, expectedMax = expectedMax, timeoutMs = timeoutMs, label = label)
            stripEchoPrefix(txBytes, rx)
        }
    }

    private fun stripEchoPrefix(txBytes: ByteArray, rx: ByteArray): ByteArray {
        if (rx.size >= txBytes.size && txBytes.isNotEmpty() && rx.copyOfRange(0, txBytes.size).contentEquals(txBytes)) {
            echoLoopbackObserved = true
            return if (rx.size > txBytes.size) rx.copyOfRange(txBytes.size, rx.size) else ByteArray(0)
        }
        return rx
    }

    private suspend fun transceiveAndLog(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        label: String
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        val preview = if (txBytes.size > 24) "${toHex(txBytes.copyOfRange(0, 12))} ... (${txBytes.size}B)" else toHex(txBytes)
        appendLog(ProberLogDirection.TX, "$label -> $preview")
        val err = writeBytes(txBytes)
        if (err != null) {
            appendLog(ProberLogDirection.ERROR, "TX Error: $err")
            return ByteArray(0)
        }
        val rx = readRx(expectedMax = expectedMax, timeoutMs = timeoutMs)
        if (rx.isEmpty()) {
            appendLog(ProberLogDirection.RX, "(Timeout ${timeoutMs}ms)")
        } else {
            appendLog(ProberLogDirection.RX, toHex(rx))
        }
        return rx
    }

    private suspend fun transceiveWithEchoCancel(
        txBytes: ByteArray,
        expectedExtra: Int,
        timeoutMs: Int,
        label: String
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        val preview = if (txBytes.size > 24) "${toHex(txBytes.copyOfRange(0, 12))} ... (${txBytes.size}B)" else toHex(txBytes)
        appendLog(ProberLogDirection.TX, "$label -> $preview")
        val err = writeBytes(txBytes)
        if (err != null) {
            appendLog(ProberLogDirection.ERROR, "TX Error: $err")
            return ByteArray(0)
        }
        val wireWait = ((txBytes.size * 11_000L) / currentBaud.coerceAtLeast(300)).toInt() + timeoutMs
        val raw = readRx(expectedMax = txBytes.size + expectedExtra, timeoutMs = wireWait)
        val clean = stripEchoPrefix(txBytes, raw)
        if (clean.isEmpty()) {
            appendLog(ProberLogDirection.RX, "(بدون رد خلف الصدى)")
        } else {
            appendLog(ProberLogDirection.RX, toHex(clean))
        }
        return clean
    }

    private suspend fun readReplyWithLog(expectedMax: Int, timeoutMs: Int, label: String): ByteArray {
        val rx = readRx(expectedMax = expectedMax, timeoutMs = timeoutMs)
        if (rx.isNotEmpty()) {
            appendLog(ProberLogDirection.RX, "$label -> ${toHex(rx)}")
        }
        return rx
    }

    private fun writeBytes(data: ByteArray): String? {
        val conn = activeConnection ?: return "منفذ USB مغلق"
        val out = outEndpoint ?: return "Bulk OUT غير متاح"
        var offset = 0
        while (offset < data.size) {
            val len = min(data.size - offset, out.maxPacketSize.coerceAtLeast(64))
            val slice = if (offset == 0 && len == data.size) data else data.copyOfRange(offset, offset + len)
            val written = conn.bulkTransfer(out, slice, slice.size, 1500)
            if (written <= 0) {
                return "فشل الإرسال (bulkTransfer=$written)"
            }
            offset += written
        }
        return null
    }

    private suspend fun readRx(expectedMax: Int, timeoutMs: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        var lastByteAt = 0L

        while (out.size() < expectedMax) {
            currentCoroutineContext().ensureActive()
            var tookAny = false
            synchronized(rxQueue) {
                while (rxQueue.isNotEmpty() && out.size() < expectedMax) {
                    out.write(rxQueue.removeFirst().toInt() and 0xFF)
                    tookAny = true
                }
            }
            val now = System.currentTimeMillis()
            if (tookAny) {
                lastByteAt = now
                if (out.size() >= expectedMax) break
            }
            if (now >= deadline) break
            if (lastByteAt > 0L && (now - lastByteAt) >= 40L) break
            delay(4)
        }
        return out.toByteArray()
    }

    private suspend fun requestPermissionSuspend(manager: UsbManager, device: UsbDevice): Boolean {
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_PROBER_USB_PERMISSION) {
                        try { appContext.unregisterReceiver(this) } catch (_: Throwable) {}
                        val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_PROBER_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val intent = Intent(ACTION_PROBER_USB_PERMISSION).apply { setPackage(appContext.packageName) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(appContext, 9101, intent, flags)
            try {
                manager.requestPermission(device, pending)
            } catch (_: Throwable) {
                try { appContext.unregisterReceiver(receiver) } catch (_: Throwable) {}
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation {
                try { appContext.unregisterReceiver(receiver) } catch (_: Throwable) {}
            }
        }
    }

    private fun registerDetachReceiver() {
        if (usbDetachReceiver != null) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> refreshUsbStatus()
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (dev != null && dev.deviceId == activeDevice?.deviceId) {
                            stopImmediately()
                        }
                        refreshUsbStatus()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            ContextCompat.registerReceiver(appContext, rx, filter, ContextCompat.RECEIVER_EXPORTED)
            usbDetachReceiver = rx
        } catch (_: Throwable) {}
    }

    private fun appendLog(dir: ProberLogDirection, msg: String) {
        val entry = ProberLogEntry(
            id = ++logSeq,
            timestamp = timeFormat.format(Date()),
            direction = dir,
            message = msg
        )
        val current = _state.value.logs
        val updated = (current + entry).takeLast(300)
        _state.value = _state.value.copy(logs = updated)
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun formatByte(b: Int?): String = if (b == null) "بدون رد" else "0x%02X".format(b and 0xFF)
}
