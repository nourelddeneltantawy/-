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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

/**
 * Singleton USB OTG Serial Manager.
 * Keeps the open UsbDeviceConnection and continuous RX inputStream reader coroutine alive
 * across Step 1, Step 2, Step 3 and navigation changes until explicitly disconnected.
 */
object UsbOtgProgrammerService {
    private const val TAG = "UsbOtgProgrammer"
    private const val ACTION_USB_PERMISSION = "com.example.USB_PROGRAMMER_PERMISSION"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var usbManager: UsbManager? = null
    @Volatile
    private var detachReceiverRegistered = false

    @Volatile
    private var activeDevice: UsbDevice? = null
    @Volatile
    private var activeConnection: UsbDeviceConnection? = null
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    @Volatile
    private var controlInterfaceIndex: Int = 0
    @Volatile
    private var inEndpoint: UsbEndpoint? = null
    @Volatile
    private var outEndpoint: UsbEndpoint? = null
    @Volatile
    private var activeChipType: ProgrammerChipType? = null

    // Continuous RX input buffer filled by background readerJob
    private val rxLock = Any()
    private val rxBuffer = ArrayDeque<Byte>(4096)
    private var readerJob: Job? = null
    private var externalLogSink: ((String) -> Unit)? = null

    private val _isConnectedFlow = MutableStateFlow(false)
    val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

    private val _connectedLabelFlow = MutableStateFlow("")
    val connectedLabelFlow: StateFlow<String> = _connectedLabelFlow.asStateFlow()

    private fun ensureContext(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (usbManager == null) {
            usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
        }
        if (!detachReceiverRegistered) {
            detachReceiverRegistered = true
            try {
                val detachReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                            val detached = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            if (detached != null && detached.deviceId == activeDevice?.deviceId) {
                                externalLogSink?.invoke("USB Event: تم فصل المبرمجة (${detached.productName ?: detached.deviceName}) من منفذ OTG.")
                                disconnect()
                            }
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    ctx,
                    detachReceiver,
                    IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Detach receiver warning: ${e.toString()}")
            }
        }
    }

    fun scanAttachedDevices(context: Context): List<UsbSerialDeviceInfo> {
        ensureContext(context)
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
        readerJob?.cancel()
        readerJob = null
        synchronized(rxLock) {
            rxBuffer.clear()
        }
        claimedInterfaces.forEach { iface ->
            try {
                activeConnection?.releaseInterface(iface)
            } catch (_: Throwable) {}
        }
        claimedInterfaces.clear()
        try {
            activeConnection?.close()
        } catch (_: Throwable) {}
        inEndpoint = null
        outEndpoint = null
        activeConnection = null
        activeDevice = null
        activeChipType = null
        controlInterfaceIndex = 0
        _isConnectedFlow.value = false
        _connectedLabelFlow.value = ""
    }

    fun applyIdleControlLines(context: Context, enableBootHold: Boolean, onLog: (String) -> Unit) {
        ensureContext(context)
        val conn = activeConnection ?: return
        val dev = activeDevice ?: return
        // When DTR/RTS Reset is disabled, release both lines (false = HIGH/inactive) so the chip isn't held in reset
        setControlLines(conn, dev, activeChipType, dtr = false, rts = enableBootHold, onLog = onLog)
    }

    suspend fun connect(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean = true,
        onLog: ((String) -> Unit)? = null
    ): UsbConnectResult = withContext(Dispatchers.IO) {
        ensureContext(context)
        externalLogSink = onLog
        val manager = usbManager ?: return@withContext UsbConnectResult.Failure(
            "نظام USB Host غير مدعوم على هذا الجهاز."
        )

        try {
            val devices = manager.deviceList.values.toList()
            if (devices.isEmpty()) {
                return@withContext UsbConnectResult.Failure(
                    "لم يتم اكتشاف أي مبرمجة USB OTG موصولة بالهاتف. تأكد من توصيل كابل OTG والمبرمجة وتفعيل OTG."
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
                "تعذر فتح اتصال USB مع المبرمجة (${targetDevice.deviceName}). أعد توصيل كابل OTG."
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
            inEndpoint = endpoints.first
            outEndpoint = endpoints.second
            activeChipType = ProgrammerChipType.fromVendorId(targetDevice.vendorId)

            configureUartHardware(conn, targetDevice, activeChipType, baudRate, onLog)
            if (!enableDtrRtsReset) {
                setControlLines(conn, targetDevice, activeChipType, dtr = false, rts = false, onLog = onLog)
            }

            startContinuousReaderLoop(conn, endpoints.first)

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
            val label = "${info.productName} (${info.hexVidPid}) @ $baudRate"
            _isConnectedFlow.value = true
            _connectedLabelFlow.value = label
            onLog?.invoke(
                "USB Open OK: ${info.productName} (${info.hexVidPid}), iface=$controlInterfaceIndex, epIn=0x%02X, epOut=0x%02X"
                    .format(endpoints.first.address, endpoints.second.address)
            )
            UsbConnectResult.Success(info, baudRate)
        } catch (e: Throwable) {
            disconnect()
            UsbConnectResult.Failure("استثناء أثناء الاتصال بـ USB: ${e.toString()}")
        }
    }

    /**
     * Continuous background inputStream listener.
     * Captures every incoming byte from CP2102 / FT232RL / CH340G immediately into rxBuffer
     * BEFORE commands are transmitted, preventing fast UART responses from ever being missed.
     */
    private fun startContinuousReaderLoop(conn: UsbDeviceConnection, epIn: UsbEndpoint) {
        readerJob?.cancel()
        readerJob = serviceScope.launch {
            val pktSize = epIn.maxPacketSize.coerceAtLeast(64)
            val buf = ByteArray(pktSize)
            try {
                while (isActive && activeConnection === conn) {
                    val n = conn.bulkTransfer(epIn, buf, buf.size, 60)
                    if (n > 0) {
                        val startIdx = if (activeChipType == ProgrammerChipType.FT232RL) 2 else 0
                        if (n > startIdx) {
                            synchronized(rxLock) {
                                for (i in startIdx until n) {
                                    rxBuffer.addLast(buf[i])
                                }
                            }
                        }
                    } else if (n < 0) {
                        // bulkTransfer returns -1 on normal 60ms idle timeout on Android USB Host;
                        // verify if device is still present in UsbManager
                        val devName = activeDevice?.deviceName
                        if (devName != null && usbManager?.deviceList?.containsKey(devName) == false) {
                            externalLogSink?.invoke("RX Listener Error: انقطع اتصال الجهاز ($devName) من منفذ OTG.")
                            disconnect()
                            break
                        }
                    }
                }
            } catch (e: Throwable) {
                if (activeConnection === conn) {
                    externalLogSink?.invoke("RX Listener Exception: ${e.toString()}")
                    disconnect()
                }
            }
        }
    }

    private fun flushRxBuffer() {
        synchronized(rxLock) {
            rxBuffer.clear()
        }
        // Also purge CP2102 hardware FIFO if applicable
        try {
            val conn = activeConnection ?: return
            if (activeChipType == ProgrammerChipType.CP2102) {
                conn.controlTransfer(0x41, 0x12, 0x000F, controlInterfaceIndex, null, 0, 200)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Ensures port is still physically valid before TX; reconnects automatically if Android dropped an unused handle.
     */
    private suspend fun ensureConnectionHealthy(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        onLog: (String) -> Unit
    ): Boolean {
        ensureContext(context)
        val dev = activeDevice
        val mgr = usbManager
        val stillAttached = dev != null && mgr?.deviceList?.containsKey(dev.deviceName) == true
        if (isConnected() && stillAttached) {
            return true
        }
        if (isConnected() && !stillAttached) {
            onLog("تنبيه: تغير مقبض USB OTG، جاري إعادة فتح المنفذ تلقائياً...")
            disconnect()
        }
        return when (val res = connect(context, preferredChip, baudRate, enableDtrRtsReset, onLog)) {
            is UsbConnectResult.Success -> true
            is UsbConnectResult.Failure -> {
                onLog(res.reason)
                false
            }
        }
    }

    private suspend fun performSyncHandshake(
        enableDtrRtsReset: Boolean,
        onLog: (String) -> Unit
    ): Boolean {
        val conn = activeConnection ?: run {
            onLog("Sync Error: لا يوجد مقبض UsbDeviceConnection نشط.")
            return false
        }
        val dev = activeDevice ?: return false

        onLog("1) تهيئة المنفذ: 9600 Baud, 8N1, Flow Control OFF...")
        configureUartHardware(conn, dev, activeChipType, 9600, onLog)

        if (enableDtrRtsReset) {
            onLog("2) تتابع Reset: DTR=true (RESET=LOW) و RTS=true (MODE=LOW) -> انتظار 100ms...")
            setControlLines(conn, dev, activeChipType, dtr = true, rts = true, onLog = onLog)
            delay(100)

            onLog("3) رفع RESET: DTR=false (RESET=HIGH) مع إبقاء RTS=true (MODE=LOW) -> انتظار 150ms...")
            setControlLines(conn, dev, activeChipType, dtr = false, rts = true, onLog = onLog)
            delay(150)
        } else {
            onLog("2) نبضة DTR/RTS Reset معطلة — تحرير DTR=false و RTS=false (وضع التوصيل المباشر)...")
            setControlLines(conn, dev, activeChipType, dtr = false, rts = false, onLog = onLog)
            delay(60)
        }

        onLog("4) تفريغ Input Buffer (Flush) قبل بدء المزامنة...")
        flushRxBuffer()

        onLog("5) إرسال 16 بايت 0x00 بفاصل 25ms بين كل بايت...")
        val zeroByte = byteArrayOf(0x00)
        for (i in 0 until 16) {
            val txErr = writeRawBytes(zeroByte)
            if (txErr != null) {
                onLog("TX ERROR (Sync byte #${i + 1}): $txErr")
                return false
            }
            delay(25)
        }
        onLog("TX: 00 x16 (25ms interval)")

        delay(50)

        val b0Reply = transmitAndLog(byteArrayOf(0xB0.toByte()), expectedMax = 8, timeoutMs = 250, onLog = onLog)
        if (b0Reply.isEmpty()) {
            transmitAndLog(byteArrayOf(0x00), expectedMax = 8, timeoutMs = 200, onLog = onLog)
        }
        return true
    }

    suspend fun probeChipId(
        context: Context,
        preferredChip: ProgrammerChipType?,
        enableDtrRtsReset: Boolean,
        catalog: List<McuProcessor>,
        onLog: (String) -> Unit
    ): ChipAutoDetectResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        if (!ensureConnectionHealthy(context, preferredChip, 9600, enableDtrRtsReset, onLog)) {
            return@withContext ChipAutoDetectResult.Failure("تعذر فتح منفذ USB OTG للفحص التلقائي.")
        }

        if (!performSyncHandshake(enableDtrRtsReset, onLog)) {
            return@withContext ChipAutoDetectResult.Failure("فشل إرسال نبضة المزامنة عبر USB. راجع تفاصيل الخطأ في السجل.")
        }

        onLog("إرسال طلب معلومات البوت لودر 0xFB...")
        val versionReply = transmitAndLog(byteArrayOf(0xFB.toByte()), expectedMax = 16, timeoutMs = 450, onLog = onLog)
        val versionAscii = toPrintableAscii(versionReply)
        if (versionReply.isNotEmpty()) {
            onLog("إصدار البوت لودر: \"$versionAscii\" [${toHex(versionReply)}]")
        }

        var matchedMcu: McuProcessor? = matchCatalogBySignature(versionAscii, toHex(versionReply), catalog)
        val combinedSignatureData = ByteArrayOutputStream()
        combinedSignatureData.write(versionReply)

        onLog("إرسال فك القفل الافتراضي 0xF5 (DF FF 00 07 + 7×FF)...")
        val unlockFrame = byteArrayOf(
            0xF5.toByte(), 0xDF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x07.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        transmitAndLog(unlockFrame, expectedMax = 8, timeoutMs = 250, onLog = onLog)
        delay(20)
        val statusReply = transmitAndLog(byteArrayOf(0x70), expectedMax = 4, timeoutMs = 300, onLog = onLog)
        if (statusReply.isNotEmpty()) {
            combinedSignatureData.write(statusReply)
        }

        if (matchedMcu == null && (versionReply.isNotEmpty() || statusReply.isNotEmpty())) {
            onLog("قراءة صفحة المعلومات 0xFF للعنوان 0x00FE00...")
            val pageFe = transmitAndLog(
                byteArrayOf(0xFF.toByte(), 0x00.toByte(), 0xFE.toByte(), 0x00.toByte()),
                expectedMax = 256,
                timeoutMs = 500,
                onLog = onLog
            )
            if (pageFe.isNotEmpty()) {
                combinedSignatureData.write(pageFe)
                matchedMcu = matchCatalogBySignature(toPrintableAscii(pageFe), toHex(pageFe), catalog)
            }

            if (matchedMcu == null) {
                onLog("قراءة صفحة المعلومات 0xFF للعنوان 0x00FF00...")
                val pageFf = transmitAndLog(
                    byteArrayOf(0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(), 0x00.toByte()),
                    expectedMax = 256,
                    timeoutMs = 500,
                    onLog = onLog
                )
                if (pageFf.isNotEmpty()) {
                    combinedSignatureData.write(pageFf)
                    matchedMcu = matchCatalogBySignature(toPrintableAscii(pageFf), toHex(pageFf), catalog)
                }
            }
        }

        val allBytes = combinedSignatureData.toByteArray()
        if (allBytes.isNotEmpty()) {
            val allHex = toHex(allBytes.take(32).toByteArray())
            val msg = when {
                matchedMcu != null -> "تم التعرف على المعالج بنجاح: ${matchedMcu.partNumber} ($versionAscii)"
                versionAscii.isNotBlank() -> "استجاب بوت لودر R8C بنجاح ($versionAscii | RX: $allHex)."
                else -> "تم استلام رد من المعالج [RX: $allHex]."
            }
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = allHex,
                matchedProcessor = matchedMcu,
                diagnosticMessage = msg
            )
        }

        onLog("تجربة تسلسل RL78 (0x00 ثم 0xC0)...")
        transmitAndLog(byteArrayOf(0x00), expectedMax = 8, timeoutMs = 150, onLog = onLog)
        delay(20)
        var rl78Reply = transmitAndLog(byteArrayOf(0xC0.toByte()), expectedMax = 32, timeoutMs = 350, onLog = onLog)
        if (rl78Reply.isEmpty()) {
            rl78Reply = transmitAndLog(
                byteArrayOf(0x01, 0x01, 0xC0.toByte(), 0x3F, 0x03),
                expectedMax = 32,
                timeoutMs = 350,
                onLog = onLog
            )
        }

        if (rl78Reply.isNotEmpty()) {
            val hexStr = toHex(rl78Reply)
            val ascii = toPrintableAscii(rl78Reply)
            val matched = matchCatalogBySignature(ascii, hexStr, catalog)
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = hexStr,
                matchedProcessor = matched,
                diagnosticMessage = if (matched != null) {
                    "تم التعرف على معالج RL78 تلقائياً: ${matched.partNumber} ($ascii)"
                } else {
                    "استجاب معالج RL78 بالبيانات [$ascii | $hexStr]"
                }
            )
        }

        ChipAutoDetectResult.Failure(
            "تم فتح المنفذ والاستماع المستمر للبيانات ولكن RX لم يستلم أي بايتات من المعالج. جرّب تبديل وضع (DTR/RTS Reset) أو عكس أطراف TXD/RXD وتأكد من الجهد 5V/3.3V."
        )
    }

    suspend fun executeReadFlash(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        if (!ensureConnectionHealthy(context, preferredChip, baudRate, enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }

        val totalSize = processor.flashSizeBytes
        onProgress(0, totalSize, "مزامنة المعالج...")
        if (!performSyncHandshake(enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة (راجع سجل الأحداث).")
        }

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
            if (idCodeBytes.size != 7) {
                return@withContext FlashOperationResult.Failure("كود ID Code يجب أن يتكون من 7 بايت (FF..FF).")
            }
            onProgress(0, totalSize, "إرسال أمر فك القفل 0xF5...")
            val unlockOk = sendR8cUnlockFrame(idCodeBytes, onLog)
            if (!unlockOk) {
                return@withContext FlashOperationResult.Failure(
                    "فشل التحقق من فك القفل (0xF5). راجع سجل TX/RX وتفاصيل الاستثناء أعلاه."
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
            val txErr = writeRawBytes(cmd)
            if (txErr != null) {
                onLog("TX ERROR (Read 0x%06X): $txErr".format(addr))
                return@withContext FlashOperationResult.Failure("فشل الإرسال عند 0x%06X: $txErr".format(addr))
            }
            val chunk = readBufferedBytes(expectedMax = blockSize, timeoutMs = 450, onLog = onLog)
            if (chunk.isEmpty()) {
                return@withContext FlashOperationResult.Failure(
                    "توقف المعالج عن إرسال البيانات عند العنوان 0x%06X (RX فارغ)."
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
            message = "تمت قراءة وسحب ${bytesReadFromChip / 1024} KB ($bytesReadFromChip بايت) من ${processor.partNumber} بنجاح.",
            bytesTransferred = resultBuffer
        )
    }

    suspend fun executeErase(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        eraseMode: String,
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        if (!ensureConnectionHealthy(context, preferredChip, baudRate, enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        val totalSize = processor.flashSizeBytes
        if (!performSyncHandshake(enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة.")
        }

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C && !sendR8cUnlockFrame(idCodeBytes, onLog)) {
            return@withContext FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code (0xF5) قبل المسح.")
        }

        onProgress(totalSize / 3, totalSize, "إرسال أمر المسح ($eraseMode)...")
        val eraseCmd = when (processor.protocolType) {
            ProtocolFamilyType.R8C_M16C -> byteArrayOf(0xA7.toByte(), 0xD0.toByte())
            ProtocolFamilyType.RL78_RX -> byteArrayOf(0x01, 0x04, 0x22, 0x00, 0x00, 0x00, 0xD9.toByte(), 0x03)
            else -> byteArrayOf(0x20, 0xD0.toByte())
        }
        val ack = transmitAndLog(eraseCmd, expectedMax = 8, timeoutMs = 1500, onLog = onLog)
        if (ack.isEmpty()) {
            return@withContext FlashOperationResult.Failure("لم يرد المعالج بتأكيد إتمام المسح (Timeout 1500ms).")
        }
        onProgress(totalSize, totalSize, "اكتمل المسح")
        FlashOperationResult.Success("تم مسح ذاكرة الفلاش بنجاح (${toHex(ack)}).")
    }

    suspend fun executeWriteAndVerify(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        firmwareBytes: ByteArray,
        idCodeBytes: ByteArray,
        verifyOnly: Boolean,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        if (!ensureConnectionHealthy(context, preferredChip, baudRate, enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
        }
        val totalSize = firmwareBytes.size
        if (!performSyncHandshake(enableDtrRtsReset, onLog)) {
            return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة.")
        }

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
                val txErr = writeRawBytes(readCmd)
                if (txErr != null) {
                    onLog("TX ERROR (Verify 0x%06X): $txErr".format(offset))
                    return@withContext FlashOperationResult.Failure("فشل الإرسال أثناء Verify عند 0x%06X: $txErr".format(offset))
                }
                val chipBytes = readBufferedBytes(expectedMax = len, timeoutMs = 450, onLog = onLog)
                if (chipBytes.size < len) {
                    return@withContext FlashOperationResult.Failure("تعذر استلام الصفحة عند 0x%06X أثناء Verify (استلم ${chipBytes.size}/$len بايت).".format(offset))
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
                val txErr = writeRawBytes(header + slice)
                if (txErr != null) {
                    onLog("TX ERROR (Write 0x%06X): $txErr".format(offset))
                    return@withContext FlashOperationResult.Failure("فشل إرسال صفحة 0x%06X: $txErr".format(offset))
                }
                val ack = readBufferedBytes(expectedMax = 4, timeoutMs = 450, onLog = onLog)
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

    private suspend fun sendR8cUnlockFrame(
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit
    ): Boolean {
        val frame = byteArrayOf(
            0xF5.toByte(),
            0xDF.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0x07.toByte()
        ) + idCodeBytes

        var ack = transmitAndLog(frame, expectedMax = 8, timeoutMs = 350, onLog = onLog)
        if (ack.isEmpty()) {
            delay(20)
            ack = transmitAndLog(byteArrayOf(0x70), expectedMax = 4, timeoutMs = 350, onLog = onLog)
        }

        if (ack.isEmpty()) {
            onLog("RX: (فارغ) لم يستلم رد Status Byte بعد 0xF5 و 0x70.")
            return false
        }

        if (ack.size >= 2 && (ack[1].toInt() and 0x0C) == 0x00) {
            onLog("تنبيه: Status Register يشير إلى عدم تطابق ID Code [SRD1/2: ${toHex(ack)}].")
            return false
        }
        return true
    }

    private suspend fun transmitAndLog(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): ByteArray {
        onLog("TX: ${toHex(txBytes)}")
        val txErr = writeRawBytes(txBytes)
        if (txErr != null) {
            onLog("TX ERROR: $txErr")
            return ByteArray(0)
        }
        val rx = readBufferedBytes(expectedMax = expectedMax, timeoutMs = timeoutMs, onLog = onLog)
        onLog(if (rx.isEmpty()) "RX: (لا يوجد رد / Timeout ${timeoutMs}ms)" else "RX: ${toHex(rx)}")
        return rx
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
        val ctx = appContext ?: return false
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        try {
                            ctx.unregisterReceiver(this)
                        } catch (_: Throwable) {}
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(ctx.packageName)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(ctx, 0, explicitIntent, flags)

            try {
                manager.requestPermission(device, pendingIntent)
            } catch (_: Throwable) {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Throwable) {}
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun findSerialInterfaceAndEndpoints(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): Pair<UsbEndpoint, UsbEndpoint>? {
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        controlInterfaceIndex = 0

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM || i == 0) {
                controlInterfaceIndex = iface.id
            }
            if (connection.claimInterface(iface, true)) {
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
        return if (bulkIn != null && bulkOut != null) Pair(bulkIn, bulkOut) else null
    }

    private fun configureUartHardware(
        conn: UsbDeviceConnection,
        device: UsbDevice,
        chipType: ProgrammerChipType?,
        baudRate: Int,
        onLog: ((String) -> Unit)?
    ) {
        try {
            when (chipType) {
                ProgrammerChipType.FT232RL -> {
                    conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x00, 0x0001, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x00, 0x0002, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x09, 0x0002, 0, null, 0, 500)
                    val divisor = if (baudRate == 9600) 0x4138 else (3_000_000 / baudRate.coerceAtLeast(300))
                    conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500)
                    conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 500)
                }
                ProgrammerChipType.CP2102 -> {
                    val idx = controlInterfaceIndex
                    val rEnable = conn.controlTransfer(0x41, 0x00, 0x0001, idx, null, 0, 500) // IFC_ENABLE
                    val baudBytes = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate ushr 8) and 0xFF).toByte(),
                        ((baudRate ushr 16) and 0xFF).toByte(),
                        ((baudRate ushr 24) and 0xFF).toByte()
                    )
                    var rBaud = conn.controlTransfer(0x41, 0x1E, 0, idx, baudBytes, baudBytes.size, 500)
                    if (rBaud < 0) {
                        val div = (3686400 / baudRate.coerceAtLeast(300)) and 0xFFFF
                        rBaud = conn.controlTransfer(0x41, 0x01, div, idx, null, 0, 500) // Legacy SET_BAUDDIV fallback
                    }
                    val rLine = conn.controlTransfer(0x41, 0x03, 0x0800, idx, null, 0, 500) // 8N1
                    val rPurge = conn.controlTransfer(0x41, 0x12, 0x000F, idx, null, 0, 500) // Purge RX/TX
                    if (rEnable < 0 || rBaud < 0 || rLine < 0) {
                        onLog?.invoke("CP2102 Config Warning: en=$rEnable, baud=$rBaud, line=$rLine, purge=$rPurge, iface=$idx")
                    }
                }
                ProgrammerChipType.CH340G -> {
                    conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500)
                    var factor = 1532620800L / baudRate.coerceAtLeast(300)
                    var divisor = 3
                    while (factor > 0xFFF0L && divisor > 0) {
                        factor = factor shr 3
                        divisor--
                    }
                    factor = 0x10000L - factor
                    val regA = ((factor.toInt() and 0xFF00) or divisor or 0x80)
                    val regB = (factor.toInt() and 0xFF)
                    conn.controlTransfer(0x40, 0x9A, 0x1312, regA, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x0F2C, regB, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x2727, 0x0000, null, 0, 500)
                }
                else -> {
                    val lineCoding = byteArrayOf(
                        (baudRate and 0xFF).toByte(),
                        ((baudRate ushr 8) and 0xFF).toByte(),
                        ((baudRate ushr 16) and 0xFF).toByte(),
                        ((baudRate ushr 24) and 0xFF).toByte(),
                        0x00, 0x00, 0x08
                    )
                    conn.controlTransfer(0x21, 0x20, 0, controlInterfaceIndex, lineCoding, lineCoding.size, 500)
                    conn.controlTransfer(0x21, 0x22, 0x0003, controlInterfaceIndex, null, 0, 500)
                }
            }
        } catch (e: Throwable) {
            onLog?.invoke("UART Config Exception (${device.deviceName}): ${e.toString()}")
        }
    }

    private fun setControlLines(
        conn: UsbDeviceConnection,
        device: UsbDevice,
        chipType: ProgrammerChipType?,
        dtr: Boolean,
        rts: Boolean,
        onLog: ((String) -> Unit)? = null
    ) {
        try {
            val ret = when (chipType) {
                ProgrammerChipType.FT232RL -> {
                    val value = (if (dtr) 0x0101 else 0x0100) or (if (rts) 0x0202 else 0x0200)
                    conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
                }
                ProgrammerChipType.CP2102 -> {
                    val value = 0x0300 or (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                    conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
                }
                ProgrammerChipType.CH340G -> {
                    val mask = (if (dtr) 0x20 else 0) or (if (rts) 0x40 else 0)
                    conn.controlTransfer(0x40, 0xA4, mask.inv() and 0xFF, 0, null, 0, 300)
                }
                else -> {
                    val valMask = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                    conn.controlTransfer(0x21, 0x22, valMask, controlInterfaceIndex, null, 0, 300)
                }
            }
            if (ret < 0) {
                onLog?.invoke("DTR/RTS Warning ($chipType): controlTransfer returned $ret")
            }
        } catch (e: Throwable) {
            onLog?.invoke("DTR/RTS Exception (${device.deviceName}): ${e.toString()}")
        }
    }

    /**
     * Returns null on success, or the exact technical error string (including Exception e.toString()) on failure.
     */
    private fun writeRawBytes(data: ByteArray): String? {
        return try {
            val conn = activeConnection ?: return "UsbDeviceConnection is null (المنفذ مغلق)"
            val out = outEndpoint ?: return "Bulk OUT endpoint is null"
            var offset = 0
            while (offset < data.size) {
                val chunkLen = min(data.size - offset, out.maxPacketSize.coerceAtLeast(64))
                val slice = if (offset == 0 && chunkLen == data.size) data
                else data.copyOfRange(offset, offset + chunkLen)
                val written = conn.bulkTransfer(out, slice, slice.size, 800)
                if (written <= 0) {
                    val attached = activeDevice?.deviceName?.let { usbManager?.deviceList?.containsKey(it) } ?: false
                    return "bulkTransfer(OUT) returned $written (len=${slice.size}, ep=0x%02X, attached=$attached, chip=$activeChipType)"
                        .format(out.address)
                }
                offset += written
            }
            null
        } catch (e: Throwable) {
            "Write Exception: ${e.toString()}"
        }
    }

    /**
     * Reads up to expectedMax bytes from the continuous rxBuffer populated by startContinuousReaderLoop().
     */
    private suspend fun readBufferedBytes(
        expectedMax: Int,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        var lastByteAt = 0L

        try {
            while (out.size() < expectedMax) {
                var drainedAny = false
                synchronized(rxLock) {
                    while (rxBuffer.isNotEmpty() && out.size() < expectedMax) {
                        out.write(rxBuffer.removeFirst().toInt() and 0xFF)
                        drainedAny = true
                    }
                }
                val now = System.currentTimeMillis()
                if (drainedAny) {
                    lastByteAt = now
                    if (out.size() >= expectedMax) break
                }
                if (now >= deadline) break
                // If we already captured bytes and 45ms elapsed with no further bytes, return packet promptly
                if (lastByteAt > 0L && (now - lastByteAt) >= 45L) break
                delay(4)
            }
        } catch (e: Throwable) {
            onLog("Read Exception: ${e.toString()}")
        }
        return out.toByteArray()
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun toPrintableAscii(bytes: ByteArray): String =
        bytes.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
}
