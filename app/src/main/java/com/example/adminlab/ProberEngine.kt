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
import kotlinx.coroutines.withContext
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

enum class ProberOutcomeKind {
    IDLE,
    RUNNING,
    SUCCESS_SCHOOL,
    LOCKED_BOOTLOADER,
    NO_SYNC,
    ADVANCED_DIAGNOSTIC,
    STOPPED,
    ERROR
}

data class ProberResult(
    val kind: ProberOutcomeKind = ProberOutcomeKind.IDLE,
    val headline: String = "",
    val detail: String = "",
    val schoolName: String = "",
    val commandHex: String = "--",
    val baudRate: Int? = null,
    val framingType: String = "--",
    val detectedChipSignature: String = "",
    val sampleRxHex: String = ""
)

data class ProberUiState(
    val usbStatus: ProberUsbStatus = ProberUsbStatus(),
    val selectedFamily: ProberTargetFamily = ProberTargetFamily.AUTO_DETECT,
    val isProbing: Boolean = false,
    val stageLabel: String = "جاهز للفحص الآمن",
    val result: ProberResult = ProberResult(),
    val logs: List<ProberLogEntry> = emptyList()
)

/**
 * Isolated Admin Protocol Auto-Prober Engine.
 * Zero shared mutable state with the production programmer service or protocol engines.
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

        private val NEC_ERROR_STATUS_CODES = setOf(
            0x04, // Command Error
            0x05, // Parameter Error
            0x07, // Checksum Error
            0x0F, // Verify Error
            0x10, // Protect Error
            0x15, // NACK
            0x1A, // Erase Error
            0x1B, // Blank Check / Checksum State Error
            0x1C, // Verify Mismatch
            0x1F  // STM32 NACK
        )

        // Forbidden destructive commands in Stage 2 safe probing
        private val FORBIDDEN_STAGE2_COMMANDS = setOf(0x20, 0x22)
    }

    private enum class DetectedFraming(val label: String) {
        NEC_STX_ETX("NEC / Renesas 78K0 (STX/ETX)"),
        STM32_ACK("STM32 USART Bootloader (0x79 ACK)"),
        RENESAS_RAW("Renesas R8C / RL78 Status Framing"),
        CUSTOM_RAW("إطار خام / مخصص (Raw UART)")
    }

    private data class SyncLockResult(
        val baudRate: Int,
        val polarityName: String,
        val syncPulseHex: String,
        val framing: DetectedFraming,
        val firstReplyHex: String,
        val echoLoopbackDetected: Boolean,
        val signatureText: String = ""
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

    private val rxLock = Any()
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
            val mgr = usbManager
            if (mgr == null) {
                appendLog(ProberLogDirection.ERROR, "نظام USB Host غير مدعوم على هذا الهاتف.")
                return@launch
            }
            val devices = mgr.deviceList.values.toList()
            val dev = pickBestSerialDevice(devices)
            if (dev == null) {
                refreshUsbStatus()
                appendLog(ProberLogDirection.WARN, "لم يتم العثور على أي محول USB UART متصل.")
                return@launch
            }
            if (mgr.hasPermission(dev)) {
                refreshUsbStatus()
                appendLog(ProberLogDirection.SUCCESS, "صلاحية USB ممنوحة بالفعل للمحول: ${dev.productName ?: dev.deviceName}")
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

    fun startSafeProbe() {
        if (activeProbeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeProbeJob = scope.launch {
            _state.value = _state.value.copy(
                isProbing = true,
                stageLabel = "المرحلة 1: فحص وتهيئة منفذ USB...",
                result = ProberResult(
                    kind = ProberOutcomeKind.RUNNING,
                    headline = "جاري الفحص المتدرج الآمن...",
                    detail = "يتم اختبار التزامن ثم مدارس السحب غير التدميرية بالتتابع."
                )
            )
            appendLog(ProberLogDirection.INFO, "══ بدء جلسة الاكتشاف الآمن (${family.labelAr}) ══")

            try {
                if (!openUsbSession()) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل الاتصال بمنفذ USB",
                        result = ProberResult(
                            kind = ProberOutcomeKind.ERROR,
                            headline = "تعذر فتح منفذ USB OTG",
                            detail = "تأكد من توصيل محول USB-TTL ومنح صلاحية USB ثم أعد المحاولة."
                        )
                    )
                    return@launch
                }

                // STAGE 1: Handshake & Sync
                _state.value = _state.value.copy(stageLabel = "المرحلة 1: اختبار النبضات والتزامن (Sync)...")
                val sync = performStage1Handshake(family)
                if (sync == null) {
                    appendLog(ProberLogDirection.WARN, "انتهت المرحلة 1 بدون استجابة تزامن صالحة من المعالج.")
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "لم يستجب المعالج",
                        result = ProberResult(
                            kind = ProberOutcomeKind.NO_SYNC,
                            headline = "لم يستجب المعالج لنبضات التزامن",
                            detail = "تأكد من توصيل TX/RX وخط FLMD0/BOOT0 والريسيت، أو اختر العائلة المتوقعة يدوياً وأعد الفحص."
                        )
                    )
                    return@launch
                }

                appendLog(
                    ProberLogDirection.SUCCESS,
                    "نجح التزامن: Baud=${sync.baudRate} | Polarity=${sync.polarityName} | Framing=${sync.framing.label}"
                )

                // STAGE 2: Non-destructive Safe Schools (Stop immediately on first win)
                _state.value = _state.value.copy(stageLabel = "المرحلة 2: اختبار القراءة المباشرة (16B)...")
                val school1Result = testSchool1DirectRead(sync)
                if (school1Result != null) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "تم اكتشاف طريقة السحب بنجاح",
                        result = school1Result
                    )
                    return@launch
                }

                _state.value = _state.value.copy(stageLabel = "المرحلة 2: اختبار السحب عبر فرق البصمة (0xB0)...")
                val school2Result = testSchool2ChecksumDifferential(sync)
                if (school2Result != null) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "تم اكتشاف طريقة السحب بنجاح",
                        result = school2Result
                    )
                    return@launch
                }

                _state.value = _state.value.copy(stageLabel = "المرحلة 2: فحص قابلية حقن كود في الرام (RAM Stub)...")
                val school3Result = testSchool3RamStubCheck(sync)
                if (school3Result != null) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "تم اكتشاف طريقة السحب بنجاح",
                        result = school3Result
                    )
                    return@launch
                }

                // STAGE 3: Locked Bootloader Alert (safe stop — no flash modification)
                val lockedMsg = "المعالج متصل ولكنه محمي ضد القراءة المباشرة (Locked Bootloader). يتطلب مدرسة متقدمة (مثل تصفير واستعادة بلوك الحماية Block-0 أو كود ID). لم يتم لمس ذاكرة المعالج حفاظاً على سلامتها."
                appendLog(ProberLogDirection.WARN, lockedMsg)
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "المعالج متصل ولكنه محمي (Locked)",
                    result = ProberResult(
                        kind = ProberOutcomeKind.LOCKED_BOOTLOADER,
                        headline = "المعالج محمي ضد القراءة المباشرة (Locked Bootloader)",
                        detail = lockedMsg,
                        schoolName = "Locked Bootloader — يتطلب مدرسة متقدمة",
                        commandHex = sync.syncPulseHex,
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        detectedChipSignature = sync.signatureText,
                        sampleRxHex = sync.firstReplyHex
                    )
                )
            } catch (_: CancellationException) {
                appendLog(ProberLogDirection.WARN, "تم إيقاف الفحص فوراً بناءً على طلب المشرف.")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "تم الإيقاف الفوري",
                    result = ProberResult(
                        kind = ProberOutcomeKind.STOPPED,
                        headline = "تم إيقاف الفحص فوراً",
                        detail = "تم تحرير منفذ USB وإلغاء جميع الأوامر الجارية بأمان."
                    )
                )
            } catch (t: Throwable) {
                appendLog(ProberLogDirection.ERROR, "خطأ غير متوقع أثناء الفحص: ${t.message ?: t::class.java.simpleName}")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "خطأ أثناء الفحص",
                    result = ProberResult(
                        kind = ProberOutcomeKind.ERROR,
                        headline = "حدث خطأ أثناء الفحص",
                        detail = t.message ?: t::class.java.simpleName
                    )
                )
            } finally {
                closeUsbSession()
            }
        }
    }

    /**
     * Triggered ONLY after double-confirmation dialogs in UI for test boards with backup.
     */
    fun startAdvancedDiagnosticProbe() {
        if (activeProbeJob?.isActive == true) return
        val family = _state.value.selectedFamily
        activeProbeJob = scope.launch {
            _state.value = _state.value.copy(
                isProbing = true,
                stageLabel = "اختبار المدارس المتقدمة (فحص الحماية و Block-0)...",
                result = ProberResult(
                    kind = ProberOutcomeKind.RUNNING,
                    headline = "جاري فحص المدارس المتقدمة...",
                    detail = "فحص حالة الأقفال الأمنية، فحص خلو Block-0 (0x32)، واختبار مصادقة ID."
                )
            )
            appendLog(ProberLogDirection.WARN, "══ بدء فحص المدارس المتقدمة (بتأكيد مزدوج من المشرف) ══")

            try {
                if (!openUsbSession()) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل الاتصال بمنفذ USB",
                        result = ProberResult(
                            kind = ProberOutcomeKind.ERROR,
                            headline = "تعذر فتح منفذ USB OTG",
                            detail = "أعد توصيل المبرمجة وتأكد من الصلاحية."
                        )
                    )
                    return@launch
                }

                val sync = performStage1Handshake(family)
                if (sync == null) {
                    _state.value = _state.value.copy(
                        isProbing = false,
                        stageLabel = "فشل التزامن المتقدم",
                        result = ProberResult(
                            kind = ProberOutcomeKind.NO_SYNC,
                            headline = "تعذر تزامن المعالج للفحص المتقدم",
                            detail = "لم يرد المعالج على نبضات التزامن."
                        )
                    )
                    return@launch
                }

                val findings = mutableListOf<String>()
                var recommendedCmd = "--"

                if (sync.framing == DetectedFraming.NEC_STX_ETX || family == ProberTargetFamily.NEC_78K0) {
                    // 1. Blank Check 0x32 on Block 0 (0x000000..0x0003FF)
                    val blankCmd = buildNecCommandFrame(0x32, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte()))
                    val blankRx = transceiveAndLog(blankCmd, expectedMax = 12, timeoutMs = 450, label = "NEC BlankCheck Block-0 [0x32]")
                    val sts = parseNecStatus(blankRx)
                    when (sts) {
                        0x06 -> {
                            findings.add("• Block-0 فارغ (Blank / Erased 0xFF) — جاهز للبرمجة مباشرة.")
                            recommendedCmd = "0x32 [Blank]"
                        }
                        0x1B -> {
                            findings.add("• Block-0 مبرمج وغير فارغ (0x1B Not Blank) — مناسب لمدرسة تصفير واستعادة Block-0 عبر فرق البصمة (0xB0 + 0x40) ثم استرداد البلوك.")
                            recommendedCmd = "0xB0 + 0x40 (Block-0 Zeroing)"
                        }
                        0x10 -> {
                            findings.add("• رُصدت حماية قراءة/كتابة (0x10 ERR_PROTECT) — يتطلب ضبط Security Flag أو مسح كلي حسب صلاحية البوت لودر.")
                            recommendedCmd = "0xA0 / 0x20 [Protected]"
                        }
                        else -> findings.add("• رد فحص Blank Check (0x32): ${formatByte(sts)}")
                    }

                    // 2. Check Checksum Read (0xB0) on Block 0 & Full 32KB range
                    val b0CsCmd = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03, 0xFF.toByte()))
                    val b0CsRx = transceiveAndLog(b0CsCmd, expectedMax = 16, timeoutMs = 450, label = "NEC Checksum Block-0 [0xB0]")
                    if (parseNecStatus(b0CsRx) == 0x06) {
                        val b0Data = transceiveWaitReply(expectedMax = 12, timeoutMs = 450, label = "RX Block-0 Checksum Payload")
                        findings.add("• أمر البصمة 0xB0 مدعوم على Block-0 (الرد: ${toHex(b0Data)}).")
                    }
                } else if (sync.framing == DetectedFraming.STM32_ACK) {
                    // STM32 Get Command (0x00) & Get ID (0x02) & Readout Unprotect diagnostic note
                    val getCmd = byteArrayOf(0x00, 0xFF.toByte())
                    val getRx = transceiveAndLog(getCmd, expectedMax = 24, timeoutMs = 450, label = "STM32 Get Supported Commands [0x00]")
                    if (getRx.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                        findings.add("• استجاب البوت لودر لقائمة الأوامر المدعومة [0x00]: ${toHex(getRx)}")
                        recommendedCmd = "0x00 -> ${toHex(getRx.take(6).toByteArray())}"
                    } else {
                        findings.add("• المعالج في وضع حماية القراءة RDP Level 1 (يرفض 0x11 بـ 0x1F) — يتطلب Readout Unprotect (0x92) مع مسح كامل الذاكرة.")
                        recommendedCmd = "0x92 (Readout Unprotect)"
                    }
                } else {
                    // Renesas R8C / RL78 Status Register 0x70 + Default ID Probe (FFx7)
                    val srRx = transceiveAndLog(byteArrayOf(0x70), expectedMax = 6, timeoutMs = 350, label = "R8C Read Status [0x70]")
                    if (srRx.isNotEmpty()) {
                        findings.add("• سجل الحالة SRD (0x70): ${toHex(srRx)}")
                    }
                    val ffId = byteArrayOf(
                        0xF5.toByte(), 0xFF.toByte(), 0xDF.toByte(), 0x00, 0x07,
                        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                    )
                    transceiveAndLog(ffId, expectedMax = 8, timeoutMs = 350, label = "R8C Default ID Probe (FFx7) [0xF5]")
                    val srAfter = transceiveAndLog(byteArrayOf(0x70), expectedMax = 6, timeoutMs = 350, label = "R8C Post-ID Status [0x70]")
                    val unlocked = srAfter.size >= 2 && ((srAfter[1].toInt() and 0x0C) != 0)
                    if (unlocked) {
                        findings.add("• تم فك قفل ID بنجاح بالكود الافتراضي (FF..FF)! المعالج يقبل القراءة المباشرة عبر 0xFF.")
                        recommendedCmd = "0xF5 (FFx7) + 0xFF"
                    } else {
                        findings.add("• ID Code غير افتراضي (مقفل بكلمة سر في الفلاش 0xFFDF..0xFFFF) — يتطلب معرفة كود ID المكون من 7 بايت.")
                        recommendedCmd = "0xF5 (Custom 7-Byte ID)"
                    }
                }

                val summary = findings.joinToString("\n").ifBlank { "لم تتوفر استجابة إضافية من السجلات المتقدمة." }
                appendLog(ProberLogDirection.SUCCESS, "اكتمل التشخيص المتقدم:\n$summary")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "اكتمل التشخيص المتقدم",
                    result = ProberResult(
                        kind = ProberOutcomeKind.ADVANCED_DIAGNOSTIC,
                        headline = "تقرير التشخيص المتقدم للمدرسة المناسبة",
                        detail = summary,
                        schoolName = "تشخيص المدرسة المتقدمة",
                        commandHex = recommendedCmd,
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        detectedChipSignature = sync.signatureText,
                        sampleRxHex = sync.firstReplyHex
                    )
                )
            } catch (_: CancellationException) {
                appendLog(ProberLogDirection.WARN, "تم إيقاف الفحص المتقدم فوراً.")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "تم الإيقاف الفوري",
                    result = ProberResult(
                        kind = ProberOutcomeKind.STOPPED,
                        headline = "تم إيقاف الفحص المتقدم",
                        detail = "تم إلغاء الفحص وإغلاق الاتصال بأمان."
                    )
                )
            } catch (t: Throwable) {
                appendLog(ProberLogDirection.ERROR, "خطأ في الفحص المتقدم: ${t.message ?: t::class.java.simpleName}")
                _state.value = _state.value.copy(
                    isProbing = false,
                    stageLabel = "خطأ في الفحص المتقدم",
                    result = ProberResult(
                        kind = ProberOutcomeKind.ERROR,
                        headline = "فشل الفحص المتقدم",
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
                    detail = "تم قطع الإرسال والاستقبال وتحرير منفذ USB."
                )
            )
        }
    }

    fun buildTechnicalReport(): String {
        val s = _state.value
        return buildString {
            appendLine("=== تقرير مختبر المشرفين: مكتشف البروتوكول التلقائي ===")
            appendLine("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("المبرمجة المتصلة: ${s.usbStatus.adapterName} (${s.usbStatus.chipModel}) [${s.usbStatus.vidPidHex}]")
            appendLine("العائلة المختارة: ${s.selectedFamily.labelAr}")
            appendLine("النتيجة: ${s.result.headline}")
            if (s.result.schoolName.isNotBlank()) appendLine("المدرسة: ${s.result.schoolName}")
            if (s.result.commandHex.isNotBlank()) appendLine("الأمر الناجح: ${s.result.commandHex}")
            if (s.result.baudRate != null) appendLine("السرعة (Baud): ${s.result.baudRate}")
            if (s.result.framingType.isNotBlank()) appendLine("تنسيق الإطار: ${s.result.framingType}")
            if (s.result.detectedChipSignature.isNotBlank()) appendLine("توقيع الشريحة (Signature): ${s.result.detectedChipSignature}")
            if (s.result.sampleRxHex.isNotBlank()) appendLine("عينة الاستجابة (RX): ${s.result.sampleRxHex}")
            appendLine("التفاصيل: ${s.result.detail}")
            appendLine("--- آخر سجل الحزم (${s.logs.size}) ---")
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

    // ==================== STAGE 1: HANDSHAKE & SYNC ====================

    private suspend fun performStage1Handshake(family: ProberTargetFamily): SyncLockResult? {
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
                "0x01 0x01 0x00 FF 03 (NEC Frame Reset)" to buildNecCommandFrame(0x00),
                "0x55 (Baud Sync)" to byteArrayOf(0x55)
            )
            ProberTargetFamily.STM32_UART -> listOf(
                "0x7F (STM32 Sync)" to byteArrayOf(0x7F),
                "0x00" to byteArrayOf(0x00),
                "0x55" to byteArrayOf(0x55)
            )
            ProberTargetFamily.RENESAS_R8C_RL78 -> listOf(
                "0x00 (R8C/RL78 Sync)" to byteArrayOf(0x00),
                "0xB0 (R8C/78K0R Baud)" to byteArrayOf(0xB0.toByte()),
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
            appendLog(ProberLogDirection.INFO, "── اختبار سرعة $baud bps ──")

            for ((polLabel, inverted, doResetToggle) in polarities) {
                currentCoroutineContext().ensureActive()
                if (doResetToggle) {
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
                        timeoutMs = 240,
                        label = "Sync [$pulseName @ $baud | $polLabel]"
                    )

                    if (rx.isEmpty()) continue

                    // Check if reply is pure TX loopback echo from a single-wire 1N4148 diode
                    val isPureEcho = rx.contentEquals(pulseBytes)
                    if (isPureEcho) {
                        echoLoopbackObserved = true
                        appendLog(ProberLogDirection.INFO, "ملاحظة: رُصد صدى سلك واحد (Single-Wire Echo) — محاولة إرسال إطار Reset لفحص استجابة المعالج خلف الصدى...")
                        // Send NEC Reset frame and see if extra bytes follow echo
                        val necReset = buildNecCommandFrame(0x00)
                        val combined = transceiveRawWithEchoStrip(necReset, expectedExtra = 8, timeoutMs = 280, label = "NEC Reset Frame خلف الصدى")
                        if (combined.isNotEmpty()) {
                            val framing = classifyFraming(combined, pulseBytes)
                            val sig = if (framing == DetectedFraming.NEC_STX_ETX) probeOptionalSignature() else ""
                            return SyncLockResult(
                                baudRate = baud,
                                polarityName = "$polLabel + Single-Wire Echo",
                                syncPulseHex = "0x00 -> NEC Reset",
                                framing = framing,
                                firstReplyHex = toHex(combined),
                                echoLoopbackDetected = true,
                                signatureText = sig
                            )
                        }
                        continue
                    }

                    // If echo prefix matches pulseBytes, strip it
                    val genuineReply = if (rx.size > pulseBytes.size && rx.copyOfRange(0, pulseBytes.size).contentEquals(pulseBytes)) {
                        echoLoopbackObserved = true
                        rx.copyOfRange(pulseBytes.size, rx.size)
                    } else {
                        rx
                    }

                    if (genuineReply.isEmpty()) continue

                    // If pulse was 0x00 and no NEC frame yet, check if NEC Reset frame gets 02 01 06 F9 03
                    val pulseFirst = pulseBytes[0].toInt() and 0xFF
                    if (pulseFirst == 0x00 && genuineReply.none { (it.toInt() and 0xFF) == STX }) {
                        delay(25)
                        val necAck = transceiveAndLog(
                            buildNecCommandFrame(0x00),
                            expectedMax = 16,
                            timeoutMs = 300,
                            label = "NEC Frame Reset Check [0x00]"
                        )
                        if (necAck.any { (it.toInt() and 0xFF) == STX }) {
                            val sig = probeOptionalSignature()
                            return SyncLockResult(
                                baudRate = baud,
                                polarityName = polLabel,
                                syncPulseHex = "0x00 + Frame Reset",
                                framing = DetectedFraming.NEC_STX_ETX,
                                firstReplyHex = toHex(necAck),
                                echoLoopbackDetected = echoLoopbackObserved,
                                signatureText = sig
                            )
                        }
                    }

                    val framing = classifyFraming(genuineReply, pulseBytes)
                    val sig = if (framing == DetectedFraming.NEC_STX_ETX) probeOptionalSignature() else ""
                    return SyncLockResult(
                        baudRate = baud,
                        polarityName = polLabel,
                        syncPulseHex = toHex(pulseBytes),
                        framing = framing,
                        firstReplyHex = toHex(genuineReply),
                        echoLoopbackDetected = echoLoopbackObserved,
                        signatureText = sig
                    )
                }
            }
        }
        return null
    }

    private suspend fun probeOptionalSignature(): String {
        val sigCmd = buildNecCommandFrame(0xC0)
        val rx = transceiveAndLog(sigCmd, expectedMax = 48, timeoutMs = 400, label = "NEC Silicon Signature [0xC0]")
        if (rx.isEmpty()) return ""
        val extra = if (parseNecStatus(rx) == 0x06 && !rx.drop(4).any { (it.toInt() and 0xFF) == STX }) {
            transceiveWaitReply(expectedMax = 48, timeoutMs = 400, label = "RX Silicon Signature Payload")
        } else {
            ByteArray(0)
        }
        val combined = rx + extra
        val ascii = combined.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        return if (ascii.isNotBlank()) "$ascii (${toHex(combined.take(20).toByteArray())})" else toHex(combined.take(20).toByteArray())
    }

    // ==================== STAGE 2: SAFE NON-DESTRUCTIVE SCHOOLS ====================

    private suspend fun testSchool1DirectRead(sync: SyncLockResult): ProberResult? {
        appendLog(ProberLogDirection.INFO, "── المدرسة 1: اختبار القراءة المباشرة لأول 16 بايت (0x000000) ──")
        val readCommands = listOf(0x11, 0x03, 0x18, 0x74, 0xBA, 0xFF)

        if (sync.framing == DetectedFraming.STM32_ACK) {
            // STM32 Read Memory [0x11, 0xEE] -> 0x79 -> Addr 0x08000000 + CS -> 0x79 -> Len-1 (0x0F, 0xF0) -> 0x79 + 16B
            val cmd = byteArrayOf(0x11, 0xEE.toByte())
            val ack1 = transceiveAndLog(cmd, expectedMax = 4, timeoutMs = 300, label = "STM32 Read Cmd [0x11]")
            if (ack1.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val addr = byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x08)
                val ack2 = transceiveAndLog(addr, expectedMax = 4, timeoutMs = 300, label = "STM32 Read Addr 0x08000000")
                if (ack2.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val lenFrame = byteArrayOf(0x0F, 0xF0.toByte())
                    val dataRx = transceiveAndLog(lenFrame, expectedMax = 24, timeoutMs = 450, label = "STM32 Read 16B [0x0F]")
                    if (dataRx.size >= 17 && (dataRx[0].toInt() and 0xFF) == ACK_STM32) {
                        val payload = dataRx.copyOfRange(1, 17)
                        val msg = "المدرسة الناجحة: القراءة المباشرة (Direct Read) - الأمر [0x11] - السرعة [${sync.baudRate}]"
                        appendLog(ProberLogDirection.SUCCESS, "$msg | RX=${toHex(payload)}")
                        return ProberResult(
                            kind = ProberOutcomeKind.SUCCESS_SCHOOL,
                            headline = msg,
                            detail = "تمت قراءة أول 16 بايت من الفلاش مباشرة بدون أي تعديل على الذاكرة.",
                            schoolName = "القراءة المباشرة (Direct Read)",
                            commandHex = "[0x11]",
                            baudRate = sync.baudRate,
                            framingType = sync.framing.label,
                            detectedChipSignature = sync.signatureText,
                            sampleRxHex = toHex(payload)
                        )
                    }
                }
            }
            return null
        }

        for (cmd in readCommands) {
            currentCoroutineContext().ensureActive()
            assertSafeStage2Command(cmd, addressHint = 0x000000)

            val packetsToTry = when (sync.framing) {
                DetectedFraming.NEC_STX_ETX -> listOf(
                    "NEC 6-Arg [0x%02X]".format(cmd) to buildNecCommandFrame(
                        cmd,
                        byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0F)
                    ),
                    "NEC 4-Arg [0x%02X]".format(cmd) to buildNecCommandFrame(
                        cmd,
                        byteArrayOf(0x00, 0x00, 0x00, 0x10)
                    )
                )
                DetectedFraming.RENESAS_RAW -> listOf(
                    "Renesas Page Read [0x%02X]".format(cmd) to byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x10),
                    "Renesas 24-bit Read [0x%02X]".format(cmd) to byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x00, 0x10)
                )
                DetectedFraming.CUSTOM_RAW -> listOf(
                    "Raw Read [0x%02X]".format(cmd) to byteArrayOf(cmd.toByte(), 0x00, 0x00, 0x00, 0x10),
                    "NEC Framed [0x%02X]".format(cmd) to buildNecCommandFrame(
                        cmd,
                        byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0F)
                    )
                )
                else -> emptyList()
            }

            for ((pktLabel, pkt) in packetsToTry) {
                currentCoroutineContext().ensureActive()
                val rx = transceiveAndLog(pkt, expectedMax = 32, timeoutMs = 380, label = pktLabel)
                if (rx.isEmpty()) continue

                val extracted = extractReal16ByteReadPayload(rx, sync.framing)
                if (extracted != null) {
                    val cmdHex = "[0x%02X]".format(cmd)
                    val msg = "المدرسة الناجحة: القراءة المباشرة (Direct Read) - الأمر $cmdHex - السرعة [${sync.baudRate}]"
                    appendLog(ProberLogDirection.SUCCESS, "$msg | 16B=${toHex(extracted)}")
                    return ProberResult(
                        kind = ProberOutcomeKind.SUCCESS_SCHOOL,
                        headline = msg,
                        detail = "نجح المعالج في إرجاع أول 16 بايت من العنوان 0x000000 بأمان تام دون لمس الفلاش.",
                        schoolName = "القراءة المباشرة (Direct Read)",
                        commandHex = cmdHex,
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        detectedChipSignature = sync.signatureText,
                        sampleRxHex = toHex(extracted)
                    )
                }
            }
        }
        return null
    }

    private suspend fun testSchool2ChecksumDifferential(sync: SyncLockResult): ProberResult? {
        appendLog(ProberLogDirection.INFO, "── المدرسة 2: اختبار السحب عبر فرق البصمة (0xB0 Checksum Range) ──")
        if (sync.framing == DetectedFraming.STM32_ACK) return null

        assertSafeStage2Command(0xB0, addressHint = 0x000000)

        // Probe 1 byte range: 0x000000 .. 0x000000
        val cmd1 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        val rx1 = transceiveAndLog(cmd1, expectedMax = 16, timeoutMs = 400, label = "Checksum 1B (0x000000..0x000000) [0xB0]")
        val cs1 = parseChecksum16FromResponse(rx1) ?: run {
            if (parseNecStatus(rx1) == ACK_NEC) {
                val follow = transceiveWaitReply(expectedMax = 12, timeoutMs = 350, label = "RX Checksum 1B Payload")
                parseChecksum16FromResponse(follow)
            } else null
        }

        if (cs1 == null) {
            appendLog(ProberLogDirection.INFO, "الأمر 0xB0 لنطاق بايت واحد لم يرجع بصمة صالحة.")
            return null
        }

        // Probe 2 byte range: 0x000000 .. 0x000001
        val cmd2 = buildNecCommandFrame(0xB0, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
        val rx2 = transceiveAndLog(cmd2, expectedMax = 16, timeoutMs = 400, label = "Checksum 2B (0x000000..0x000001) [0xB0]")
        val cs2 = parseChecksum16FromResponse(rx2) ?: run {
            if (parseNecStatus(rx2) == ACK_NEC) {
                val follow = transceiveWaitReply(expectedMax = 12, timeoutMs = 350, label = "RX Checksum 2B Payload")
                parseChecksum16FromResponse(follow)
            } else null
        }

        if (cs2 != null) {
            val msg = "المدرسة الناجحة: السحب عبر فرق البصمة (Checksum Byte-Extraction)"
            val sample = "CS(1B)=0x%04X | CS(2B)=0x%04X".format(cs1, cs2)
            appendLog(ProberLogDirection.SUCCESS, "$msg | $sample")
            return ProberResult(
                kind = ProberOutcomeKind.SUCCESS_SCHOOL,
                headline = msg,
                detail = "قبل المعالج حساب Checksum لنطاق بايت واحد وبايتين عبر الأمر [0xB0]، مما يثبت إمكانية السحب التفاضلي بدقة.",
                schoolName = "السحب عبر فرق البصمة (Checksum Byte-Extraction)",
                commandHex = "[0xB0]",
                baudRate = sync.baudRate,
                framingType = sync.framing.label,
                detectedChipSignature = sync.signatureText,
                sampleRxHex = sample
            )
        }
        return null
    }

    private suspend fun testSchool3RamStubCheck(sync: SyncLockResult): ProberResult? {
        appendLog(ProberLogDirection.INFO, "── المدرسة 3: فحص قابلية حقن كود قراءة في الرام (RAM Stub) ──")

        if (sync.framing == DetectedFraming.STM32_ACK) {
            // Write Memory [0x31, 0xCE] targeting safe SRAM 0x20001000 (NEVER Flash 0x0800xxxx)
            val cmd = byteArrayOf(0x31, 0xCE.toByte())
            val ack = transceiveAndLog(cmd, expectedMax = 4, timeoutMs = 320, label = "STM32 RAM Write Cmd [0x31]")
            if (ack.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                val ramAddr = byteArrayOf(0x20, 0x00, 0x10, 0x00, 0x30) // CS = 0x20^0x00^0x10^0x00 = 0x30
                val addrAck = transceiveAndLog(ramAddr, expectedMax = 4, timeoutMs = 320, label = "STM32 Safe SRAM Addr 0x20001000")
                if (addrAck.any { (it.toInt() and 0xFF) == ACK_STM32 }) {
                    val msg = "المدرسة الناجحة: حقن كود قراءة في الرام (RAM Bootloader Stub)"
                    appendLog(ProberLogDirection.SUCCESS, "$msg | Command=[0x31]")
                    return ProberResult(
                        kind = ProberOutcomeKind.SUCCESS_SCHOOL,
                        headline = msg,
                        detail = "وافق المعالج على الكتابة في عنوان الرام الآمن (0x20001000) عبر الأمر [0x31] دون أي تعديل على الفلاش.",
                        schoolName = "حقن كود قراءة في الرام (RAM Bootloader Stub)",
                        commandHex = "[0x31]",
                        baudRate = sync.baudRate,
                        framingType = sync.framing.label,
                        detectedChipSignature = sync.signatureText,
                        sampleRxHex = toHex(addrAck)
                    )
                }
            }
            return null
        }

        // For NEC / Renesas: probe command 0x31 or 0x40 strictly targeting high RAM (0x00FEF0..0x00FEF3), NEVER Flash (< 0x00F800)
        // Note: on 78K0/Kx2 internal high-speed RAM starts at 0xFEF0..0xFEEF; boot firmware rejects Flash write with 0x05/0x10
        // but we only inspect header acceptance and never commit Flash erase/program.
        val ramProbes = listOf(
            0x31 to buildNecCommandFrame(0x31, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte())),
            0x40 to buildNecCommandFrame(0x40, byteArrayOf(0x00, 0xFE.toByte(), 0xF0.toByte(), 0x00, 0xFE.toByte(), 0xF3.toByte()))
        )

        for ((cmd, frame) in ramProbes) {
            currentCoroutineContext().ensureActive()
            assertSafeStage2Command(cmd, addressHint = 0x00FEF0)
            val rx = transceiveAndLog(frame, expectedMax = 12, timeoutMs = 350, label = "RAM Stub Header Probe [0x%02X @ 0x00FEF0]".format(cmd))
            if (parseNecStatus(rx) == ACK_NEC) {
                val cmdHex = "[0x%02X]".format(cmd)
                val msg = "المدرسة الناجحة: حقن كود قراءة في الرام (RAM Bootloader Stub)"
                appendLog(ProberLogDirection.SUCCESS, "$msg | Command=$cmdHex")
                return ProberResult(
                    kind = ProberOutcomeKind.SUCCESS_SCHOOL,
                    headline = msg,
                    detail = "استجاب المعالج بـ ACK للأمر $cmdHex على نطاق الرام الآمن (0x00FEF0..0x00FEF3)، مما يتيح تحميل أداة قراءة مؤقتة بالرام.",
                    schoolName = "حقن كود قراءة في الرام (RAM Bootloader Stub)",
                    commandHex = cmdHex,
                    baudRate = sync.baudRate,
                    framingType = sync.framing.label,
                    detectedChipSignature = sync.signatureText,
                    sampleRxHex = toHex(rx)
                )
            }
        }
        return null
    }

    // ==================== SAFETY GUARD & PACKET PARSING ====================

    private fun assertSafeStage2Command(cmd: Int, addressHint: Int) {
        val c = cmd and 0xFF
        require(c !in FORBIDDEN_STAGE2_COMMANDS) {
            "SAFETY BLOCK: Command 0x%02X (Erase) is strictly forbidden during Stage 2 safe probe!".format(c)
        }
        if (c == 0x40) {
            require(addressHint >= 0x00F800) {
                "SAFETY BLOCK: 0x40 targeting Flash address 0x%06X is forbidden in Stage 2! Only safe RAM >= 0x00F800 allowed.".format(addressHint)
            }
        }
    }

    private fun classifyFraming(reply: ByteArray, pulse: ByteArray): DetectedFraming {
        val first = reply[0].toInt() and 0xFF
        if (reply.any { (it.toInt() and 0xFF) == STX } && reply.any { (it.toInt() and 0xFF) == ETX }) {
            return DetectedFraming.NEC_STX_ETX
        }
        val pulse0 = pulse.firstOrNull()?.let { it.toInt() and 0xFF } ?: -1
        if (pulse0 == 0x7F && (first == ACK_STM32 || first == NACK_STM32)) {
            return DetectedFraming.STM32_ACK
        }
        if (first == ACK_NEC || first == 0x80 || first == 0xB0 || first == 0xC1 || first == 0xF0) {
            return DetectedFraming.RENESAS_RAW
        }
        return DetectedFraming.CUSTOM_RAW
    }

    private fun extractReal16ByteReadPayload(rx: ByteArray, framing: DetectedFraming): ByteArray? {
        if (rx.isEmpty()) return null
        if (framing == DetectedFraming.NEC_STX_ETX) {
            // Reject single-byte/short error frames (e.g., 02 01 04 FA 03)
            val sts = parseNecStatus(rx)
            if (sts != null && sts in NEC_ERROR_STATUS_CODES) return null

            // Check if frame contains STX, LEN (>=16 or 0x10), DATA[16], CS, ETX
            val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
            if (stxIdx >= 0 && rx.size >= stxIdx + 20) {
                val len = rx[stxIdx + 1].toInt() and 0xFF
                if (len >= 16 && rx.size >= stxIdx + 2 + len + 2) {
                    return rx.copyOfRange(stxIdx + 2, stxIdx + 18)
                }
            }
            return null
        }
        // Raw / Renesas response: must be >= 16 bytes and not solely an error code
        if (rx.size >= 16) {
            return rx.copyOfRange(0, 16)
        }
        return null
    }

    private fun parseChecksum16FromResponse(rx: ByteArray): Int? {
        if (rx.isEmpty()) return null
        val stxIdx = rx.indexOfFirst { (it.toInt() and 0xFF) == STX }
        if (stxIdx >= 0 && rx.size >= stxIdx + 6) {
            val len = rx[stxIdx + 1].toInt() and 0xFF
            if (len == 1) {
                val sts = rx[stxIdx + 2].toInt() and 0xFF
                if (sts in NEC_ERROR_STATUS_CODES) return null
            }
            if (len == 2 && rx.size >= stxIdx + 6) {
                val b0 = rx[stxIdx + 2].toInt() and 0xFF
                val b1 = rx[stxIdx + 3].toInt() and 0xFF
                // Make sure it isn't a 2-byte status error [0x06, error]
                if (b0 == ACK_NEC && b1 in NEC_ERROR_STATUS_CODES) return null
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
            appendLog(ProberLogDirection.INFO, "طلب صلاحية USB OTG من النظام...")
            val granted = requestPermissionSuspend(mgr, dev)
            refreshUsbStatus()
            if (!granted) {
                appendLog(ProberLogDirection.ERROR, "لم يتم منح صلاحية USB.")
                return false
            }
        }

        closeUsbSession()
        val conn = mgr.openDevice(dev) ?: run {
            appendLog(ProberLogDirection.ERROR, "تعذر فتح اتصال USB مع الجهاز.")
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

        activeDevice = dev
        activeConnection = conn
        inEndpoint = bulkIn
        outEndpoint = bulkOut
        activeChip = ProberUsbChip.fromVendorId(dev.vendorId)
        echoLoopbackObserved = false
        refreshUsbStatus()

        startReaderLoop(conn, bulkIn)
        appendLog(
            ProberLogDirection.INFO,
            "تم فتح منفذ USB بنجاح: ${activeChip.displayName} (VID:%04X PID:%04X)".format(dev.vendorId, dev.productId)
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
                        appendLog(ProberLogDirection.ERROR, "تم فصل كابل USB OTG أثناء العملية.")
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
                conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 400) // 8N1
                conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 400) // Flow OFF
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
        delay(65)
        setControlLines(dtr = flmd0High, rts = resetHigh)
        delay(110)
    }

    private fun flushRx() {
        synchronized(rxQueue) { rxQueue.clear() }
    }

    private suspend fun transceiveAndLog(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        label: String
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        appendLog(ProberLogDirection.TX, "$label -> ${toHex(txBytes)}")
        val err = writeBytes(txBytes)
        if (err != null) {
            appendLog(ProberLogDirection.ERROR, "TX Error: $err")
            return ByteArray(0)
        }
        val rx = readRx(expectedMax = expectedMax, timeoutMs = timeoutMs)
        if (rx.isEmpty()) {
            appendLog(ProberLogDirection.RX, "(لا توجد استجابة / ${timeoutMs}ms)")
        } else {
            appendLog(ProberLogDirection.RX, toHex(rx))
        }
        return rx
    }

    private suspend fun transceiveRawWithEchoStrip(
        txBytes: ByteArray,
        expectedExtra: Int,
        timeoutMs: Int,
        label: String
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        appendLog(ProberLogDirection.TX, "$label -> ${toHex(txBytes)}")
        val err = writeBytes(txBytes)
        if (err != null) {
            appendLog(ProberLogDirection.ERROR, "TX Error: $err")
            return ByteArray(0)
        }
        val raw = readRx(expectedMax = txBytes.size + expectedExtra, timeoutMs = timeoutMs)
        if (raw.size > txBytes.size && raw.copyOfRange(0, txBytes.size).contentEquals(txBytes)) {
            val clean = raw.copyOfRange(txBytes.size, raw.size)
            appendLog(ProberLogDirection.RX, "(بدون صدى) ${toHex(clean)}")
            return clean
        }
        if (raw.isNotEmpty()) appendLog(ProberLogDirection.RX, toHex(raw))
        return raw
    }

    private suspend fun transceiveWaitReply(expectedMax: Int, timeoutMs: Int, label: String): ByteArray {
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
