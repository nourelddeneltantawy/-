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
    private var isSyncedSession: Boolean = false

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
        isSyncedSession = false
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
        isSyncedSession = false

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

    /**
     * Mandatory Renesas UART Sync Handshake:
     * 1. Configure 9600 Baud, 8 Data bits, No Parity, 1 Stop bit (8N1)
     * 2. Toggle DTR/RTS to pulse Reset and wait 50ms
     * 3. Send 16 consecutive 0x00 bytes with 20ms interval between each byte
     */
    private suspend fun performSyncHandshake(
        onLog: (String) -> Unit
    ): Boolean {
        val conn = activeConnection ?: return false
        val dev = activeDevice ?: return false

        onLog("تهيئة المنفذ: 9600 Baud (8N1) وتفعيل DTR/RTS لإعادة ضبط المعالج (Reset)...")
        configureUartHardware(conn, dev, activeChipType, 9600)
        setControlLines(conn, dev, activeChipType, dtr = true, rts = true)
        delay(40)
        setControlLines(conn, dev, activeChipType, dtr = false, rts = true)
        delay(50) // Mandatory 50ms settle after reset release
        drainInputBuffer(conn)

        onLog("إرسال نبضة المزامنة الإجبارية (16 × 0x00 بفاصل 20ms)...")
        val zeroByte = byteArrayOf(0x00)
        for (i in 0 until 16) {
            if (!writeBytes(zeroByte)) {
                onLog("فشل إرسال بايت المزامنة رقم ${i + 1}.")
                return false
            }
            delay(20)
        }

        // Send Renesas 9600 baud-rate confirmation command (0xB0) and collect any boot ACK
        writeBytes(byteArrayOf(0xB0.toByte()))
        val syncAck = readBytes(expectedMax = 8, timeoutMs = 150)
        if (syncAck.isNotEmpty()) {
            onLog("رد المزامنة من المعالج: ${syncAck.joinToString(" ") { "%02X".format(it) }}")
        }
        isSyncedSession = true
        return true
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

        performSyncHandshake(onLog)

        // Probe 1: Renesas R8C / M16C Boot Version & Identity request (0xFB)
        onLog("إرسال أمر طلب الهوية 0xFB (عائلة R8C / M16C)...")
        writeBytes(byteArrayOf(0xFB.toByte()))
        delay(30)
        val r8cReply = readBytes(expectedMax = 64, timeoutMs = 450)

        if (r8cReply.isNotEmpty()) {
            val hexStr = r8cReply.joinToString(" ") { "%02X".format(it) }
            val ascii = r8cReply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            onLog("الرد المستلم [$hexStr] ASCII: \"$ascii\"")
            val matched = matchCatalogBySignature(ascii, hexStr, catalog)
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = hexStr,
                matchedProcessor = matched,
                diagnosticMessage = if (matched != null) {
                    "تم التعرف على المعالج تلقائياً: ${matched.partNumber} ($ascii | $hexStr)"
                } else {
                    "استجاب المعالج بالبيانات [$ascii | $hexStr]، اختر الموديل المطابق من القائمة."
                }
            )
        }

        // Probe 2: Renesas RL78 Identity sequence (0x00 then 0xC0 + Silicon Signature frame)
        onLog("لم يرد على 0xFB، إرسال 0x00 ثم 0xC0 لفحص عائلة RL78...")
        writeBytes(byteArrayOf(0x00))
        delay(20)
        writeBytes(byteArrayOf(0xC0.toByte()))
        delay(25)
        var rl78Reply = readBytes(expectedMax = 64, timeoutMs = 400)

        if (rl78Reply.isEmpty()) {
            // Standard RL78 framed Silicon Signature packet
            writeBytes(byteArrayOf(0x01, 0x01, 0xC0.toByte(), 0x3F, 0x03))
            rl78Reply = readBytes(expectedMax = 64, timeoutMs = 400)
        }

        if (rl78Reply.isNotEmpty()) {
            val hexStr = rl78Reply.joinToString(" ") { "%02X".format(it) }
            val ascii = rl78Reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            onLog("رد RL78 المستلم [$hexStr] ASCII: \"$ascii\"")
            val matched = matchCatalogBySignature(ascii, hexStr, catalog)
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = hexStr,
                matchedProcessor = matched,
                diagnosticMessage = if (matched != null) {
                    "تم التعرف على المعالج تلقائياً: ${matched.partNumber} ($ascii)"
                } else {
                    "استجاب معالج RL78 بالبيانات [$ascii | $hexStr]"
                }
            )
        }

        ChipAutoDetectResult.Failure(
            "تم فتح المنفذ وإرسال المزامنة (16×0x00 بفاصل 20ms) وأوامر الهوية (0xFB / 0xC0) ولكن المعالج لم يرد. تأكد من وضع Boot Mode وتوصيل TXD↔RXD و GND و VCC."
        )
    }

    suspend fun executeReadFlash(
        processor: McuProcessor,
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }

        val totalSize = processor.flashSizeBytes
        onProgress(0, totalSize, "مزامنة المعالج (Sync Handshake)...")
        performSyncHandshake(onLog)

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
            if (idCodeBytes.size != 7) {
                return@withContext FlashOperationResult.Failure("كود ID Code يجب أن يتكون من 7 بايت (14 حرف Hex).")
            }
            onProgress(0, totalSize, "إرسال أمر فك القفل 0xF5...")
            val unlockOk = sendR8cUnlockFrame(idCodeBytes, onLog)
            if (!unlockOk) {
                return@withContext FlashOperationResult.Failure(
                    "فشل استجابة المعالج لأمر فك القفل (0xF5 ID Code Verify). تحقق من الـ 7 بايت وتوصيل UART ووضع Boot Mode."
                )
            }
        }

        val resultBuffer = FirmwareParser.createEmptyBuffer(totalSize)
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
            if (!writeBytes(cmd)) {
                return@withContext FlashOperationResult.Failure("انقطع الاتصال بالمنفذ عند العنوان 0x%06X.".format(addr))
            }
            val chunk = readBytes(expectedMax = blockSize, timeoutMs = 300)
            if (chunk.isEmpty()) {
                return@withContext FlashOperationResult.Failure(
                    "توقف المعالج عن إرسال البيانات عند العنوان 0x%06X (لم يتم استلام رد)."
                        .format(addr)
                )
            }
            val copyLen = min(chunk.size, totalSize - addr)
            System.arraycopy(chunk, 0, resultBuffer, addr, copyLen)
            bytesReadFromChip += copyLen
            onProgress(
                bytesReadFromChip,
                totalSize,
                "قراءة بلوك ${blockIdx + 1}/$totalBlocks (0x%06X)".format(addr)
            )
        }

        FlashOperationResult.Success(
            message = "تمت قراءة وسحب ${bytesReadFromChip / 1024} KB (${bytesReadFromChip} بايت) من ${processor.partNumber} بنجاح.",
            bytesTransferred = resultBuffer
        )
    }

    suspend fun executeErase(
        processor: McuProcessor,
        eraseMode: String,
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        val totalSize = processor.flashSizeBytes
        performSyncHandshake(onLog)

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C && !sendR8cUnlockFrame(idCodeBytes, onLog)) {
            return@withContext FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code (0xF5) قبل المسح.")
        }

        onProgress(totalSize / 3, totalSize, "إرسال أمر المسح ($eraseMode)...")
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
            return@withContext FlashOperationResult.Failure("لم يرد المعالج بتأكيد إتمام المسح (Timeout).")
        }
        onProgress(totalSize, totalSize, "اكتمل المسح")
        FlashOperationResult.Success("تم مسح ذاكرة الفلاش بنجاح (${ack.joinToString(" ") { "%02X".format(it) }}).")
    }

    suspend fun executeWriteAndVerify(
        processor: McuProcessor,
        firmwareBytes: ByteArray,
        idCodeBytes: ByteArray,
        verifyOnly: Boolean,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        val totalSize = firmwareBytes.size
        performSyncHandshake(onLog)

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C && !sendR8cUnlockFrame(idCodeBytes, onLog)) {
            return@withContext FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code (0xF5) قبل العملية.")
        }

        val pageSize = 256
        val totalPages = (totalSize + pageSize - 1) / pageSize
        for (page in 0 until totalPages) {
            val offset = page * pageSize
            val len = min(pageSize, totalSize - offset)
            val bytesDone = min(offset + len, totalSize)
            val slice = firmwareBytes.copyOfRange(offset, offset + len)

            if (!verifyOnly && slice.all { it == 0xFF.toByte() }) {
                onProgress(bytesDone, totalSize, "تخطي صفحة فارغة 0x%06X".format(offset))
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
                    return@withContext FlashOperationResult.Failure("تعذر استلام الصفحة من المعالج عند 0x%06X أثناء Verify.".format(offset))
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
                    0x41,
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
                bytesDone,
                totalSize,
                "${if (verifyOnly) "مطابقة" else "كتابة"} صفحة ${page + 1}/$totalPages (0x%06X)".format(offset)
            )
        }

        val doneMsg = if (verifyOnly) {
            "تمت مطابقة (Verify) كامل الملف (${totalSize / 1024} KB) بنسبة 100%."
        } else {
            "تم شحن (Write) السوفت وير بنجاح إلى ${processor.partNumber}."
        }
        FlashOperationResult.Success(doneMsg)
    }

    /**
     * Sends Renesas R8C ID Code Check command:
     * 0xF5 + ID Start Address (0xDF, 0xFF, 0x00) + Length (0x07) + 7 ID bytes,
     * then awaits confirmation byte (or queries 0x70 status registers SRD1/SRD2).
     */
    private suspend fun sendR8cUnlockFrame(
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit
    ): Boolean {
        val hexId = idCodeBytes.joinToString("") { "%02X".format(it) }
        onLog("إرسال أمر فك القفل 0xF5 بالعنوان 00FFDF + ID [$hexId]...")
        val frame = byteArrayOf(
            0xF5.toByte(),
            0xDF.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x07.toByte()
        ) + idCodeBytes

        if (!writeBytes(frame)) {
            onLog("فشل كتابة حزمة 0xF5 عبر USB.")
            return false
        }
        delay(35)
        var ack = readBytes(expectedMax = 8, timeoutMs = 450)
        if (ack.isEmpty()) {
            // Query Renesas Standard Serial I/O Mode 2 Status Register (0x70)
            onLog("استعلام سجل الحالة 0x70 للتحقق من Status Byte...")
            writeBytes(byteArrayOf(0x70))
            delay(25)
            ack = readBytes(expectedMax = 4, timeoutMs = 350)
        }

        if (ack.isEmpty()) {
            onLog("لم يستلم رد Status Byte من المعالج بعد 0xF5.")
            return false
        }

        val statusHex = ack.joinToString(" ") { "%02X".format(it) }
        onLog("رد تأكيد فك القفل (Status Byte): [$statusHex]")
        // On R8C SRD2: bit 2&3 = 11 (0x0C) indicates ID verified; if 2 bytes SRD1,SRD2 returned and bitmask fails, warn explicitly
        if (ack.size >= 2 && (ack[1].toInt() and 0x0C) == 0x00) {
            onLog("تنبيه: سجل SRD2 يشير إلى عدم تطابق كود الحماية ID Code.")
            return false
        }
        return true
    }

    private fun matchCatalogBySignature(
        ascii: String,
        hexStr: String,
        catalog: List<McuProcessor>
    ): McuProcessor? {
        val regex = Regex("""R[45]F\d{4,6}|M3\d{4,6}|uPD78F\d{4,5}""", RegexOption.IGNORE_CASE)
        regex.find(ascii)?.value?.let { extracted ->
            catalog.firstOrNull {
                it.partNumber.equals(extracted, ignoreCase = true) ||
                    it.partNumber.contains(extracted, ignoreCase = true) ||
                    extracted.contains(it.partNumber, ignoreCase = true)
            }?.let { return it }
        }
        val cleanAscii = ascii.replace(Regex("[^A-Za-z0-9]"), "")
        if (cleanAscii.length >= 4) {
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
            } catch (_: Throwable) {
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
                    val divisor = if (baudRate == 9600) 0x4138 else (3_000_000 / baudRate.coerceAtLeast(300))
                    conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500) // Baudrate
                    conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500) // 8 Data bits, No Parity, 1 Stop bit
                    conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 500) // Flow control None
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
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500) // 8N1 LCR
                    val baudReg = when (baudRate) {
                        115200 -> 0xCC83
                        57600 -> 0x9883
                        38400 -> 0x6483
                        19200 -> 0xD982
                        else -> 0xB282 // Exact 9600 Baud with divisor-enable bit 0x80
                    }
                    conn.controlTransfer(0x40, 0x9A, 0x1312, baudReg, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x0F2C, 0x0008, null, 0, 500)
                }
                else -> {
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
                    // Active-low modem bits on CH340 (bit5 = DTR, bit6 = RTS)
                    val value = (if (dtr) 0 else 0x20) or (if (rts) 0 else 0x40)
                    conn.controlTransfer(0x40, 0xA4, value, 0, null, 0, 300)
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
            val n = conn.bulkTransfer(epIn, buf, buf.size, 25)
            if (n <= 0) return
        }
    }
}
