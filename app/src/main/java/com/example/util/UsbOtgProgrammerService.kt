package com.example.util

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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.McuProcessor
import com.example.data.model.ProgrammerChipType
import com.example.data.model.ProtocolFamilyType
import com.example.data.model.UsbSerialDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.min

sealed class UsbConnectResult {
    data class Success(val deviceInfo: UsbSerialDeviceInfo, val baudRate: Int) : UsbConnectResult()
    data class Failure(val reason: String) : UsbConnectResult()
}

sealed class ChipAutoDetectResult {
    data class Matched(
        val rawSignatureHex: String,
        val matchedProcessor: McuProcessor?,
        val diagnosticMessage: String
    ) : ChipAutoDetectResult()
    data class Failure(val reason: String) : ChipAutoDetectResult()
}

sealed class FlashOperationResult {
    data class Success(val message: String, val bytesTransferred: ByteArray? = null) : FlashOperationResult()
    data class Failure(val reason: String) : FlashOperationResult()
}

class UsbOtgProgrammerService(private val context: Context) {
    companion object {
        private const val TAG = "UsbOtgProgrammer"
        private const val ACTION_USB_PERMISSION = "com.example.USB_PROGRAMMER_PERMISSION"
    }

    private val appContext = context.applicationContext
    private val usbManager: UsbManager? = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private var activeDevice: UsbDevice? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var activeChipType: ProgrammerChipType? = null

    fun scanAttachedDevices(): List<UsbSerialDeviceInfo> {
        val manager = usbManager ?: return emptyList()
        return manager.deviceList.values.map { dev ->
            UsbSerialDeviceInfo(
                deviceId = dev.deviceId,
                deviceName = dev.deviceName,
                productName = dev.productName ?: "USB Serial Adapter",
                manufacturerName = dev.manufacturerName ?: "USB Host",
                vendorId = dev.vendorId,
                productId = dev.productId,
                chipType = ProgrammerChipType.fromVendorId(dev.vendorId),
                hasPermission = manager.hasPermission(dev)
            )
        }
    }

    fun isConnected(): Boolean = activeConnection != null && inEndpoint != null && outEndpoint != null

    fun disconnect() {
        try {
            claimedInterface?.let { iface -> activeConnection?.releaseInterface(iface) }
        } catch (_: Throwable) {}
        try {
            activeConnection?.close()
        } catch (_: Throwable) {}
        claimedInterface = null
        inEndpoint = null
        outEndpoint = null
        activeConnection = null
        activeDevice = null
        activeChipType = null
    }

    suspend fun connect(
        preferredChip: ProgrammerChipType?,
        baudRate: Int
    ): UsbConnectResult = withContext(Dispatchers.IO) {
        val manager = usbManager ?: return@withContext UsbConnectResult.Failure(
            "نظام USB Host غير مدعوم على هذا الجهاز."
        )
        val devices = manager.deviceList.values.toList()
        if (devices.isEmpty()) {
            return@withContext UsbConnectResult.Failure(
                "لم يتم اكتشاف أي مبرمجة USB OTG موصولة بالهاتف. تأكد من توصيل كابل OTG والمبرمجة وتفعيل OTG في إعدادات الهاتف."
            )
        }

        val targetDevice = devices.firstOrNull {
            preferredChip != null && it.vendorId in preferredChip.vendorIds
        } ?: devices.firstOrNull {
            ProgrammerChipType.fromVendorId(it.vendorId) != null
        } ?: devices.first()

        if (!manager.hasPermission(targetDevice)) {
            val granted = requestUsbPermission(manager, targetDevice)
            if (!granted) {
                return@withContext UsbConnectResult.Failure(
                    "لم يتم منح صلاحية الوصول لمنفذ USB للمبرمجة (${targetDevice.productName ?: targetDevice.deviceName})."
                )
            }
        }

        disconnect()
        val conn = manager.openDevice(targetDevice) ?: return@withContext UsbConnectResult.Failure(
            "تعذر فتح اتصال USB مع المبرمجة. افصل الكابل وأعد توصيله ثم حاول مرة أخرى."
        )

        val endpoints = findSerialInterfaceAndEndpoints(targetDevice, conn)
        if (endpoints == null) {
            conn.close()
            return@withContext UsbConnectResult.Failure(
                "الجهاز المتصل (${targetDevice.productName ?: targetDevice.deviceName}) لا يحتوي على نقاط اتصال Bulk Serial UART صالحة."
            )
        }

        activeDevice = targetDevice
        activeConnection = conn
        claimedInterface = endpoints.first
        inEndpoint = endpoints.second
        outEndpoint = endpoints.third
        activeChipType = ProgrammerChipType.fromVendorId(targetDevice.vendorId)

        configureUartHardware(conn, targetDevice, activeChipType, baudRate)

        val info = UsbSerialDeviceInfo(
            deviceId = targetDevice.deviceId,
            deviceName = targetDevice.deviceName,
            productName = targetDevice.productName ?: (activeChipType?.displayName ?: "USB-UART"),
            manufacturerName = targetDevice.manufacturerName ?: "USB",
            vendorId = targetDevice.vendorId,
            productId = targetDevice.productId,
            chipType = activeChipType,
            hasPermission = true
        )
        UsbConnectResult.Success(info, baudRate)
    }

    suspend fun probeChipId(
        preferredChip: ProgrammerChipType?,
        catalog: List<McuProcessor>,
        onLog: (String) -> Unit
    ): ChipAutoDetectResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            onLog("جاري البحث عن مبرمجة USB OTG متصلة...")
            when (val res = connect(preferredChip, 9600)) {
                is UsbConnectResult.Failure -> return@withContext ChipAutoDetectResult.Failure(res.reason)
                is UsbConnectResult.Success -> onLog("تم فتح المنفذ: ${res.deviceInfo.productName} (${res.deviceInfo.hexVidPid})")
            }
        }

        val conn = activeConnection ?: return@withContext ChipAutoDetectResult.Failure("لا يوجد اتصال USB نشط.")
        val dev = activeDevice ?: return@withContext ChipAutoDetectResult.Failure("لا يوجد جهاز USB نشط.")

        // Hardware reset / boot entry pulse via DTR/RTS
        onLog("إرسال نبضة إقلاع BOOT عبر خطوط DTR / RTS...")
        setControlLines(conn, dev, activeChipType, dtr = false, rts = true)
        delay(60)
        setControlLines(conn, dev, activeChipType, dtr = true, rts = false)
        delay(80)
        drainInputBuffer(conn)

        // Probe 1: Renesas R8C / M16C sync & version inquiry (0x00 x16 then 0xFB)
        onLog("إرسال تزامن UART (0x00) واستعلام ID Code / Silicon Signature...")
        val syncPulse = ByteArray(16) { 0x00 }
        writeBytes(syncPulse)
        delay(40)
        writeBytes(byteArrayOf(0xFB.toByte())) // R8C/M16C Version / Signature read
        val r8cReply = readBytes(expectedMax = 32, timeoutMs = 350)

        if (r8cReply.isNotEmpty()) {
            val hexStr = r8cReply.joinToString(" ") { "%02X".format(it) }
            val ascii = r8cReply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            val matched = matchCatalogBySignature(ascii, hexStr, catalog)
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = hexStr,
                matchedProcessor = matched,
                diagnosticMessage = if (matched != null) {
                    "تم التعرف على المعالج تلقائياً: ${matched.partNumber} ($hexStr)"
                } else {
                    "تم استلام توقيع المعالج [$hexStr / $ascii]، اختر الموديل المطابق من القائمة."
                }
            )
        }

        // Probe 2: Renesas RL78 1-Wire TOOL0 Silicon Signature frame (SOH 0x01, Len 0x01, Cmd 0xC0 Silicon Signature)
        onLog("لم يستجب كـ R8C، جاري فحص بروتوكول RL78 / RX Silicon Signature (0xC0)...")
        writeBytes(byteArrayOf(0x3A)) // RL78 TOOL0 single-wire baud sync
        delay(30)
        writeBytes(byteArrayOf(0x01, 0x01, 0xC0.toByte(), 0x3F, 0x03))
        val rl78Reply = readBytes(expectedMax = 32, timeoutMs = 350)

        if (rl78Reply.isNotEmpty()) {
            val hexStr = rl78Reply.joinToString(" ") { "%02X".format(it) }
            val ascii = rl78Reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            val matched = matchCatalogBySignature(ascii, hexStr, catalog)
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = hexStr,
                matchedProcessor = matched,
                diagnosticMessage = if (matched != null) {
                    "تم التعرف على المعالج تلقائياً: ${matched.partNumber}"
                } else {
                    "تم استلام استجابة Silicon Signature [$hexStr]"
                }
            )
        }

        ChipAutoDetectResult.Failure(
            "تم فتح منفذ المبرمجة بنجاح ولكن المعالج المستهدف لم يرد على نبضات الفحص. تأكد من توصيل أطراف TXD/RXD و GND و VCC وطرف MODE/TOOL0 وإعادة تشغيل تغذية الكارتة."
        )
    }

    suspend fun executeReadFlash(
        processor: McuProcessor,
        idCodeBytes: ByteArray,
        onProgress: (Float, String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }

        val totalSize = processor.flashSizeBytes
        val resultBuffer = FirmwareParser.createEmptyBuffer(totalSize)

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
            if (idCodeBytes.size != 7) {
                return@withContext FlashOperationResult.Failure("كود ID Code يجب أن يتكون من 7 بايت بالضبط.")
            }
            onProgress(0.05f, "إرسال 7-Byte ID Code لفك قفل قراءة الذاكرة...")
            val unlockOk = sendR8cUnlockFrame(idCodeBytes)
            if (!unlockOk) {
                return@withContext FlashOperationResult.Failure(
                    "فشل استجابة المعالج لأمر فك القفل (ID Code Verify). تحقق من صحة الـ 7 بايت أو توصيل UART."
                )
            }
        }

        val blockSize = 256
        val totalBlocks = (totalSize + blockSize - 1) / blockSize
        var bytesReadFromChip = 0

        for (blockIdx in 0 until totalBlocks) {
            val addr = blockIdx * blockSize
            val cmd = byteArrayOf(
                0xFF.toByte(),
                (addr and 0xFF).toByte(),
                ((addr ushr 8) and 0xFF).toByte(),
                ((addr ushr 16) and 0xFF).toByte()
            )
            val sent = writeBytes(cmd)
            if (!sent) {
                return@withContext FlashOperationResult.Failure("انقطع الاتصال بالمنفذ التسلسلي عند العنوان 0x%06X.".format(addr))
            }
            val chunk = readBytes(expectedMax = blockSize, timeoutMs = 250)
            if (chunk.isEmpty()) {
                return@withContext FlashOperationResult.Failure(
                    "توقف المعالج عن إرسال البيانات عند العنوان 0x%06X (لم يتم استلام بايتات رد). تأكد من أن المعالج في وضع Boot Mode."
                        .format(addr)
                )
            }
            val copyLen = min(chunk.size, totalSize - addr)
            System.arraycopy(chunk, 0, resultBuffer, addr, copyLen)
            bytesReadFromChip += copyLen
            onProgress(
                (blockIdx + 1).toFloat() / totalBlocks.toFloat(),
                "قراءة بلوك ${blockIdx + 1}/$totalBlocks (0x%06X)".format(addr)
            )
        }

        FlashOperationResult.Success(
            message = "تمت قراءة وسحب $bytesReadFromChip بايت من المعالج ${processor.partNumber} بنجاح.",
            bytesTransferred = resultBuffer
        )
    }

    suspend fun executeErase(
        processor: McuProcessor,
        eraseMode: String,
        idCodeBytes: ByteArray,
        onProgress: (Float, String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        if (processor.protocolType == ProtocolFamilyType.R8C_M16C && !sendR8cUnlockFrame(idCodeBytes)) {
            return@withContext FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code قبل المسح.")
        }

        onProgress(0.3f, "إرسال أمر المسح ($eraseMode) إلى ${processor.partNumber}...")
        val eraseCmd = when (processor.protocolType) {
            ProtocolFamilyType.R8C_M16C -> byteArrayOf(0xA7.toByte(), 0xD0.toByte())
            ProtocolFamilyType.RL78_RX -> byteArrayOf(0x01, 0x04, 0x22, 0x00, 0x00, 0x00, 0xD9.toByte(), 0x03)
            else -> byteArrayOf(0x20, 0xD0.toByte())
        }
        if (!writeBytes(eraseCmd)) {
            return@withContext FlashOperationResult.Failure("فشل إرسال أمر المسح عبر USB.")
        }
        val ack = readBytes(expectedMax = 8, timeoutMs = 1500)
        if (ack.isEmpty()) {
            return@withContext FlashOperationResult.Failure("لم يرد المعالج بتأكيد إتمام المسح (Timeout). تحقق من الجهد ووضع البوت.")
        }
        onProgress(1f, "اكتمل المسح")
        FlashOperationResult.Success("تم مسح ذاكرة الفلاش بنجاح (${ack.joinToString(" ") { "%02X".format(it) }}).")
    }

    suspend fun executeWriteAndVerify(
        processor: McuProcessor,
        firmwareBytes: ByteArray,
        idCodeBytes: ByteArray,
        verifyOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        if (processor.protocolType == ProtocolFamilyType.R8C_M16C && !sendR8cUnlockFrame(idCodeBytes)) {
            return@withContext FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code قبل العملية.")
        }

        val pageSize = 256
        val totalPages = (firmwareBytes.size + pageSize - 1) / pageSize
        for (page in 0 until totalPages) {
            val offset = page * pageSize
            val len = min(pageSize, firmwareBytes.size - offset)
            val slice = firmwareBytes.copyOfRange(offset, offset + len)

            // Skip completely blank 0xFF pages during write to maximize flashing speed
            if (!verifyOnly && slice.all { it == 0xFF.toByte() }) {
                onProgress((page + 1).toFloat() / totalPages.toFloat(), "تخطي صفحة فارغة 0x%06X".format(offset))
                continue
            }

            if (verifyOnly) {
                val readCmd = byteArrayOf(
                    0xFF.toByte(),
                    (offset and 0xFF).toByte(),
                    ((offset ushr 8) and 0xFF).toByte(),
                    ((offset ushr 16) and 0xFF).toByte()
                )
                if (!writeBytes(readCmd)) {
                    return@withContext FlashOperationResult.Failure("فشل إرسال طلب المطابقة عند 0x%06X.".format(offset))
                }
                val chipBytes = readBytes(expectedMax = len, timeoutMs = 300)
                if (chipBytes.size < len) {
                    return@withContext FlashOperationResult.Failure("تعذر استلام كامل الصفحة من المعالج عند 0x%06X أثناء Verify.".format(offset))
                }
                for (i in 0 until len) {
                    if (chipBytes[i] != slice[i]) {
                        return@withContext FlashOperationResult.Failure(
                            "اختلاف في المطابقة عند العنوان 0x%06X: المتوقع %02X والمقروء %02X."
                                .format(offset + i, slice[i], chipBytes[i])
                        )
                    }
                }
            } else {
                val header = byteArrayOf(
                    0x41, // Page Program
                    (offset and 0xFF).toByte(),
                    ((offset ushr 8) and 0xFF).toByte(),
                    ((offset ushr 16) and 0xFF).toByte()
                )
                if (!writeBytes(header + slice)) {
                    return@withContext FlashOperationResult.Failure("فشل إرسال بيانات الصفحة 0x%06X عبر USB.".format(offset))
                }
                val ack = readBytes(expectedMax = 4, timeoutMs = 400)
                if (ack.isEmpty()) {
                    return@withContext FlashOperationResult.Failure("لم يستجب المعالج بتأكيد كتابة الصفحة عند 0x%06X.".format(offset))
                }
            }

            onProgress(
                (page + 1).toFloat() / totalPages.toFloat(),
                "${if (verifyOnly) "مطابقة" else "كتابة"} الصفحة ${page + 1}/$totalPages (0x%06X)".format(offset)
            )
        }

        val doneMsg = if (verifyOnly) {
            "تمت مطابقة (Verify) كامل ملف السوفت مع ذاكرة المعالج بنسبة 100%."
        } else {
            "تم شحن (Write) السوفت وير بنجاح إلى المعالج ${processor.partNumber}."
        }
        FlashOperationResult.Success(doneMsg)
    }

    private fun sendR8cUnlockFrame(idCodeBytes: ByteArray): Boolean {
        val frame = byteArrayOf(0xF5.toByte(), 0x00, 0x00, 0x00, 0x07) + idCodeBytes
        if (!writeBytes(frame)) return false
        val ack = readBytes(expectedMax = 4, timeoutMs = 300)
        return ack.isNotEmpty()
    }

    private fun matchCatalogBySignature(
        ascii: String,
        hexStr: String,
        catalog: List<McuProcessor>
    ): McuProcessor? {
        if (ascii.length >= 4) {
            val cleanAscii = ascii.replace(Regex("[^A-Za-z0-9]"), "")
            catalog.firstOrNull {
                cleanAscii.contains(it.partNumber, ignoreCase = true) ||
                    (it.partNumber.length >= 5 && cleanAscii.contains(it.partNumber.take(6), ignoreCase = true))
            }?.let { return it }
        }
        return catalog.firstOrNull {
            hexStr.contains(it.partNumber.take(4), ignoreCase = true)
        }
    }

    private suspend fun requestUsbPermission(manager: UsbManager, device: UsbDevice): Boolean {
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Throwable) {}
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(appContext.packageName)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(appContext, 0, explicitIntent, flags)

            try {
                manager.requestPermission(device, pendingIntent)
            } catch (e: Throwable) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Throwable) {}
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun findSerialInterfaceAndEndpoints(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): Triple<UsbInterface, UsbEndpoint, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (epIdx in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(epIdx)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT) bulkOut = ep
                }
            }
            if (bulkIn != null && bulkOut != null) {
                if (connection.claimInterface(iface, true)) {
                    return Triple(iface, bulkIn, bulkOut)
                }
            }
        }
        return null
    }

    private fun configureUartHardware(
        conn: UsbDeviceConnection,
        device: UsbDevice,
        chipType: ProgrammerChipType?,
        baudRate: Int
    ) {
        try {
            when (chipType) {
                ProgrammerChipType.FT232RL -> {
                    conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500) // Reset
                    val divisor = 3_000_000 / baudRate.coerceAtLeast(300)
                    conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500) // Baudrate
                    conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500) // 8N1
                }
                ProgrammerChipType.CP2102 -> {
                    conn.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500) // IFC_ENABLE
                    val baudBytes = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate ushr 8) and 0xFF).toByte(),
                        ((baudRate ushr 16) and 0xFF).toByte(),
                        ((baudRate ushr 24) and 0xFF).toByte()
                    )
                    conn.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, baudBytes.size, 500)
                    conn.controlTransfer(0x41, 0x03, 0x0800, 0, null, 0, 500) // 8N1
                }
                ProgrammerChipType.CH340G -> {
                    conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500) // 8N1
                    val prescalerAndDiv = when (baudRate) {
                        115200 -> 0xCC03 to 0x0008
                        57600 -> 0x9803 to 0x0008
                        38400 -> 0x6403 to 0x0008
                        19200 -> 0xD902 to 0x0008
                        else -> 0xB202 to 0x0008 // 9600
                    }
                    conn.controlTransfer(0x40, 0x9A, 0x1312, prescalerAndDiv.first, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x0F2C, prescalerAndDiv.second, null, 0, 500)
                }
                else -> {
                    // Standard USB CDC-ACM SET_LINE_CODING (8N1)
                    val lineCoding = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate ushr 8) and 0xFF).toByte(),
                        ((baudRate ushr 16) and 0xFF).toByte(),
                        ((baudRate ushr 24) and 0xFF).toByte(),
                        0x00, 0x00, 0x08
                    )
                    conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 500)
                    conn.controlTransfer(0x21, 0x22, 0x0003, 0, null, 0, 500)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "UART config warning for ${device.deviceName}: ${e.message}")
        }
    }

    private fun setControlLines(
        conn: UsbDeviceConnection,
        device: UsbDevice,
        chipType: ProgrammerChipType?,
        dtr: Boolean,
        rts: Boolean
    ) {
        try {
            when (chipType) {
                ProgrammerChipType.FT232RL -> {
                    val value = (if (dtr) 0x0101 else 0x0100) or (if (rts) 0x0202 else 0x0200)
                    conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
                }
                ProgrammerChipType.CP2102 -> {
                    val value = 0x0300 or (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                    conn.controlTransfer(0x41, 0x07, value, 0, null, 0, 300)
                }
                ProgrammerChipType.CH340G -> {
                    val bits = (if (!dtr) 0x20 else 0) or (if (!rts) 0x40 else 0)
                    conn.controlTransfer(0x40, 0xA4, bits.inv() and 0xFF, 0, null, 0, 300)
                }
                else -> {
                    val valMask = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                    conn.controlTransfer(0x21, 0x22, valMask, 0, null, 0, 300)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Control line warning (${device.deviceName}): ${e.message}")
        }
    }

    private fun writeBytes(data: ByteArray): Boolean {
        val conn = activeConnection ?: return false
        val out = outEndpoint ?: return false
        val written = conn.bulkTransfer(out, data, data.size, 800)
        return written >= 0
    }

    private fun readBytes(expectedMax: Int, timeoutMs: Int): ByteArray {
        val conn = activeConnection ?: return ByteArray(0)
        val epIn = inEndpoint ?: return ByteArray(0)
        val buf = ByteArray(maxOf(expectedMax, epIn.maxPacketSize))
        val readLen = conn.bulkTransfer(epIn, buf, buf.size, timeoutMs)
        if (readLen <= 0) return ByteArray(0)
        // FTDI chips prefix every USB IN packet with 2 modem-status bytes
        if (activeChipType == ProgrammerChipType.FT232RL) {
            if (readLen <= 2) return ByteArray(0)
            return buf.copyOfRange(2, min(readLen, expectedMax + 2))
        }
        return buf.copyOfRange(0, min(readLen, expectedMax))
    }

    private fun drainInputBuffer(conn: UsbDeviceConnection) {
        val epIn = inEndpoint ?: return
        val buf = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
        repeat(3) {
            val n = conn.bulkTransfer(epIn, buf, buf.size, 30)
            if (n <= 0) return
        }
    }
}
