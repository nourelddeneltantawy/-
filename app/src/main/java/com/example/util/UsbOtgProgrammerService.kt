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
import com.example.data.model.DataFlashExportMode
import com.example.data.model.McuProcessor
import com.example.data.model.ProgrammerChipType
import com.example.data.model.ProtocolFamilyType
import com.example.data.model.UsbSerialDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        val diagnosticMessage: String,
        val nec78k0Info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo? = null
    ) : ChipAutoDetectResult()
    data class Failure(val reason: String) : ChipAutoDetectResult()
}

sealed class FlashOperationResult {
    data class Success(
        val message: String,
        val bytesTransferred: ByteArray? = null,
        val baseAddress: Int = 0,
        val dataFlashBytes: ByteArray? = null,
        val dataFlashBaseAddress: Int = 0,
        val isMcuVerified: Boolean = false,
        val fullCsMatched: Boolean = true,
        val actualFullCs16: Int? = null,
        val expectedFullCs16: Int? = null
    ) : FlashOperationResult()
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
    private val usbStateLock = Any()
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    @Volatile
    private var controlInterfaceIndex: Int = 0
    @Volatile
    private var inEndpoint: UsbEndpoint? = null
    @Volatile
    private var outEndpoint: UsbEndpoint? = null
    @Volatile
    private var activeChipType: ProgrammerChipType? = null
    @Volatile
    private var currentBaudRate: Int = 9600
    @Volatile
    private var isUartLineConfigured: Boolean = false
    @Volatile
    private var echoCancellationEnabled: Boolean = true
    @Volatile
    private var rawRxMonitorEnabled: Boolean = false
    @Volatile
    private var currentDtrState: Boolean = false
    @Volatile
    private var currentRtsState: Boolean = false
    @Volatile
    private var nec78k0InvertDtrRtsPolarity: Boolean = false

    private enum class BootSyncMode {
        SINGLE_WIRE_RL78,
        SINGLE_WIRE_R8C_78K0R,
        FLMD0_78K0
    }

    // Continuous RX input buffer filled by background readerJob
    private val rxLock = Any()
    private val rxBuffer = ArrayDeque<Byte>(4096)
    @Volatile
    private var readerJob: Job? = null
    @Volatile
    private var externalLogSink: ((String) -> Unit)? = null

    private fun safeLog(message: String) {
        try {
            externalLogSink?.invoke(message)
        } catch (e: Throwable) {
            Log.w(TAG, "safeLog suppressed exception: ${e.message}")
        }
    }

    private val _isConnectedFlow = MutableStateFlow(false)
    val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

    private val _connectedLabelFlow = MutableStateFlow("")
    val connectedLabelFlow: StateFlow<String> = _connectedLabelFlow.asStateFlow()

    private val _usbAttachedFlow = MutableSharedFlow<UsbSerialDeviceInfo>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val usbAttachedFlow: SharedFlow<UsbSerialDeviceInfo> = _usbAttachedFlow.asSharedFlow()

    fun setEchoCancellation(enabled: Boolean) {
        echoCancellationEnabled = enabled
    }

    fun is78k0InvertDtrRtsPolarity(): Boolean = nec78k0InvertDtrRtsPolarity

    fun set78k0InvertDtrRtsPolarity(inverted: Boolean) {
        nec78k0InvertDtrRtsPolarity = inverted
    }

    private fun ensureContext(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (usbManager == null) {
            usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
        }
        if (!detachReceiverRegistered) {
            detachReceiverRegistered = true
            try {
                val usbReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        when (intent?.action) {
                            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                                val dev = device ?: return
                                val mgr = usbManager
                                val info = UsbSerialDeviceInfo(
                                    deviceId = dev.deviceId,
                                    deviceName = dev.deviceName,
                                    productName = dev.productName ?: "USB Serial Adapter",
                                    manufacturerName = dev.manufacturerName ?: "USB Host",
                                    vendorId = dev.vendorId,
                                    productId = dev.productId,
                                    chipType = ProgrammerChipType.fromVendorId(dev.vendorId),
                                    hasPermission = mgr?.hasPermission(dev) == true
                                )
                                _usbAttachedFlow.tryEmit(info)
                            }
                            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                                if (device != null && device.deviceId == activeDevice?.deviceId) {
                                    safeLog("USB Event: تم فصل المبرمجة (${device.productName ?: device.deviceName}) من منفذ OTG.")
                                    disconnect()
                                }
                            }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
                ContextCompat.registerReceiver(
                    ctx,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } catch (e: Throwable) {
                Log.w(TAG, "USB attach/detach receiver warning: ${e.toString()}")
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
        val jobToCancel = readerJob
        readerJob = null
        jobToCancel?.cancel()
        synchronized(rxLock) {
            rxBuffer.clear()
        }
        synchronized(usbStateLock) {
            val conn = activeConnection
            claimedInterfaces.toList().forEach { iface ->
                try {
                    conn?.releaseInterface(iface)
                } catch (_: Throwable) {}
            }
            claimedInterfaces.clear()
            try {
                conn?.close()
            } catch (_: Throwable) {}
            inEndpoint = null
            outEndpoint = null
            activeConnection = null
            activeDevice = null
            activeChipType = null
            controlInterfaceIndex = 0
            isUartLineConfigured = false
            rawRxMonitorEnabled = false
            currentDtrState = false
            currentRtsState = false
        }
        _isConnectedFlow.value = false
        _connectedLabelFlow.value = ""
    }

    fun applyIdleControlLines(context: Context, enableBootHold: Boolean, onLog: (String) -> Unit) {
        ensureContext(context)
        val conn = activeConnection ?: return
        val dev = activeDevice ?: return
        setTxBreak(conn, dev, activeChipType, enabled = false, onLog = onLog)
        setControlLines(conn, dev, activeChipType, dtr = false, rts = false, onLog = onLog)
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
                Nec78k0ProtocolRegistry.isSupportedAdapter(it.vendorId, it.productId)
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

            configureUartHardware(conn, targetDevice, activeChipType, baudRate, onLog, purgeHardwareFifo = true)
            setControlLines(conn, targetDevice, activeChipType, dtr = false, rts = false, onLog = onLog)

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
    @Volatile
    private var lastUsbLoopBeatMs: Long = 0L

    private fun startContinuousReaderLoop(conn: UsbDeviceConnection, epIn: UsbEndpoint) {
        readerJob?.cancel()
        readerJob = serviceScope.launch {
            val pktSize = epIn.maxPacketSize.coerceAtLeast(64)
            val buf = ByteArray(pktSize)
            try {
                while (isActive && activeConnection === conn) {
                    lastUsbLoopBeatMs = System.currentTimeMillis()
                    val n = conn.bulkTransfer(epIn, buf, buf.size, 60)
                    if (n > 0) {
                        val startIdx = if (activeChipType == ProgrammerChipType.FT232RL) 2 else 0
                        if (n > startIdx) {
                            val chunk = buf.copyOfRange(startIdx, n)
                            synchronized(rxLock) {
                                for (b in chunk) {
                                    if (rxBuffer.size >= 65536) {
                                        rxBuffer.removeFirst()
                                    }
                                    rxBuffer.addLast(b)
                                }
                            }
                            if (rawRxMonitorEnabled) {
                                safeLog("RX Raw Live (+${chunk.size}B): [${chunk.joinToString(", ") { "%02X".format(it) }}]")
                            }
                        }
                    } else if (n < 0) {
                        // bulkTransfer returns -1 on normal 60ms idle timeout on Android USB Host;
                        // verify if device is still present in UsbManager
                        val devName = activeDevice?.deviceName
                        if (devName != null && usbManager?.deviceList?.containsKey(devName) == false) {
                            safeLog("RX Listener Error: انقطع اتصال الجهاز ($devName) من منفذ OTG.")
                            disconnect()
                            break
                        }
                    }
                }
            } catch (e: Throwable) {
                if (activeConnection === conn) {
                    safeLog("RX Listener Exception: ${e.message ?: e::class.java.simpleName}")
                    disconnect()
                }
            }
        }
    }

    private fun flushRxBuffer() {
        // Only clear the cumulative rxBuffer queue; never cancel readerJob or send USB control PURGE while stream is open.
        synchronized(rxLock) {
            rxBuffer.clear()
        }
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

    private fun resolveBootSyncMode(mainFamily: String?): BootSyncMode {
        val fam = mainFamily.orEmpty().trim().uppercase()
        return when {
            fam.contains("78K0") && !fam.contains("78K0R") -> BootSyncMode.FLMD0_78K0
            fam.contains("RL78") -> BootSyncMode.SINGLE_WIRE_RL78
            else -> BootSyncMode.SINGLE_WIRE_R8C_78K0R
        }
    }

    private suspend fun performSyncHandshake(
        enableDtrRtsReset: Boolean,
        syncMode: BootSyncMode,
        onLog: (String) -> Unit
    ): Boolean {
        val conn = activeConnection ?: run {
            onLog("Sync Error: لا يوجد مقبض UsbDeviceConnection نشط.")
            return false
        }
        val dev = activeDevice ?: return false

        onLog("1) تهيئة المنفذ: 9600 Baud, 8N1, Flow Control OFF (Echo Cancel=${if (echoCancellationEnabled) "ON" else "OFF"})...")
        if (!isUartLineConfigured || currentBaudRate != 9600) {
            configureUartHardware(conn, dev, activeChipType, 9600, onLog, purgeHardwareFifo = (readerJob?.isActive != true))
        }
        setTxBreak(conn, dev, activeChipType, enabled = false, onLog = onLog)

        if (!enableDtrRtsReset) {
            onLog("2) نبضة DTR/RTS معطلة — تحرير DTR=false و RTS=false (توصيل مباشر)...")
            setControlLines(conn, dev, activeChipType, dtr = false, rts = false, onLog = onLog)
            delay(50)
            flushRxBuffer()
        } else if (syncMode == BootSyncMode.FLMD0_78K0) {
            val dtrFlmd0High = nec78k0InvertDtrRtsPolarity
            val rtsResetLow = !nec78k0InvertDtrRtsPolarity
            val rtsResetHigh = nec78k0InvertDtrRtsPolarity
            onLog("2) تتابع 78K0 (FLMD0): setDTR($dtrFlmd0High) [FLMD0=+5V] + setRTS($rtsResetLow) [RESET=0V] -> انتظار ${Nec78k0ProtocolRegistry.RESET_PULSE_MS}ms...")
            setControlLines(conn, dev, activeChipType, dtr = dtrFlmd0High, rts = rtsResetLow, onLog = onLog)
            delay(Nec78k0ProtocolRegistry.RESET_PULSE_MS)

            onLog("3) تحرير RESET: setRTS($rtsResetHigh) [RESET=+5V] مع إبقاء setDTR($dtrFlmd0High) -> انتظار ${Nec78k0ProtocolRegistry.POST_RESET_DELAY_MS}ms...")
            setControlLines(conn, dev, activeChipType, dtr = dtrFlmd0High, rts = rtsResetHigh, onLog = onLog)
            delay(Nec78k0ProtocolRegistry.POST_RESET_DELAY_MS)
            flushRxBuffer()
        } else {
            onLog("2) سحب RTS=true (RESET=LOW عبر D2) + تفعيل TX Break LOW (سحب TOOL0/MODE=LOW عبر D1) -> 50ms...")
            setControlLines(conn, dev, activeChipType, dtr = false, rts = true, onLog = onLog)
            val breakSupported = setTxBreak(conn, dev, activeChipType, enabled = true, onLog = onLog)
            if (!breakSupported) {
                // Fallback if adapter ignores SET_BREAK: keep TX actively pulled low using 0x00 bytes during 50ms
                repeat(4) {
                    writeRawBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                    delay(12)
                }
            } else {
                delay(50)
            }

            onLog("3) رفع RESET: RTS=false (RESET=HIGH) بينما TOOL0/MODE منخفض عبر Break لالتقاط Boot Mode -> 20ms...")
            setControlLines(conn, dev, activeChipType, dtr = false, rts = false, onLog = onLog)
            delay(20)

            setTxBreak(conn, dev, activeChipType, enabled = false, onLog = onLog)
            delay(8)
            flushRxBuffer()
        }

        return when (syncMode) {
            BootSyncMode.SINGLE_WIRE_RL78 -> {
                onLog("4) إرسال نبضة المزامنة RL78 (0x3A) مع إلغاء صدى الدايود D1...")
                val syncReply = transmitAndLog(byteArrayOf(0x3A), expectedMax = 4, timeoutMs = 200, onLog = onLog)
                if (syncReply.isEmpty()) {
                    onLog("إعادة محاولة المزامنة بنبضة 0x00 (RL78)...")
                    transmitAndLog(byteArrayOf(0x00), expectedMax = 4, timeoutMs = 160, onLog = onLog)
                }
                true
            }
            BootSyncMode.FLMD0_78K0 -> {
                onLog("4) إرسال نبضة المزامنة 78K0 ([0x00] ← ${Nec78k0ProtocolRegistry.SYNC_BYTE_GAP_MS}ms ← [0x00]) ثم انتظار 50ms...")
                val err1 = writeWithEchoCancel(byteArrayOf(0x00), clearStaleRx = true, onLog = onLog)
                if (err1 != null) {
                    onLog("TX ERROR (78K0 Sync #1): $err1")
                    return false
                }
                delay(Nec78k0ProtocolRegistry.SYNC_BYTE_GAP_MS)
                val err2 = writeWithEchoCancel(byteArrayOf(0x00), clearStaleRx = true, onLog = onLog)
                if (err2 != null) {
                    onLog("TX ERROR (78K0 Sync #2): $err2")
                    return false
                }
                delay(50)
                flushRxBuffer()
                true
            }
            BootSyncMode.SINGLE_WIRE_R8C_78K0R -> {
                onLog("4) إرسال 16 بايت 0x00 بفاصل 20ms (R8C / 78K0R) مع إلغاء صدى الدايود D1...")
                val zeroByte = byteArrayOf(0x00)
                for (i in 0 until 16) {
                    val txErr = writeWithEchoCancel(zeroByte, clearStaleRx = (i == 0), onLog = onLog)
                    if (txErr != null) {
                        onLog("TX ERROR (Sync byte #${i + 1}): $txErr")
                        return false
                    }
                    delay(20)
                }
                onLog("TX: 00 x16 (Echo Cancelled)")
                delay(40)

                val b0Reply = transmitAndLog(byteArrayOf(0xB0.toByte()), expectedMax = 8, timeoutMs = 250, onLog = onLog)
                if (b0Reply.isEmpty()) {
                    transmitAndLog(byteArrayOf(0x00), expectedMax = 8, timeoutMs = 200, onLog = onLog)
                }
                true
            }
        }
    }

    private fun createR8cTransport(): R8cProtocolEngine.SerialTransport = object : R8cProtocolEngine.SerialTransport {
        override fun configureUart(baudRate: Int, onLog: (String) -> Unit): Boolean {
            val conn = activeConnection ?: return false
            val dev = activeDevice ?: return false
            configureUartHardware(conn, dev, activeChipType, baudRate, onLog, purgeHardwareFifo = (readerJob?.isActive != true))
            return true
        }

        override fun ensureReaderListening(onLog: (String) -> Unit): Boolean {
            val conn = activeConnection ?: run {
                onLog("RX Listener Error: لا يوجد اتصال USB نشط.")
                return false
            }
            val epIn = inEndpoint ?: run {
                onLog("RX Listener Error: نقطة Bulk IN غير متاحة.")
                return false
            }
            if (readerJob?.isActive != true) {
                startContinuousReaderLoop(conn, epIn)
            }
            return true
        }

        override fun setRawRxMonitor(enabled: Boolean) {
            rawRxMonitorEnabled = enabled
        }

        override fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit) {
            val conn = activeConnection ?: return
            val dev = activeDevice ?: return
            this@UsbOtgProgrammerService.setControlLines(conn, dev, activeChipType, dtr = dtr, rts = rts, onLog = onLog)
        }

        override fun setDtr(enabled: Boolean, onLog: (String) -> Unit) {
            setSingleDtr(enabled, onLog)
        }

        override fun setRts(enabled: Boolean, onLog: (String) -> Unit) {
            setSingleRts(enabled, onLog)
        }

        override fun flushInputBuffer() {
            flushRxBuffer()
        }

        override fun isDeviceAttached(): Boolean = isDeviceCurrentlyAttached()

        override suspend fun writeRaw(bytes: ByteArray): String? {
            return writeRawBytes(bytes)
        }

        override suspend fun waitForByteInRxQueue(expectedByte: Byte, timeoutMs: Int): Boolean {
            return awaitSingleByteInRxQueue(expectedByte, timeoutMs)
        }

        override suspend fun sendAndReceiveR8C(
            txBytes: ByteArray,
            expectedRxLength: Int,
            timeoutMs: Int,
            onLog: (String) -> Unit
        ): R8cProtocolEngine.R8cExchangeResult {
            return executeStrictR8cExchange(
                txBytes = txBytes,
                expectedRxLength = expectedRxLength,
                timeoutMs = timeoutMs,
                onLog = onLog
            )
        }
    }

    private fun isDeviceCurrentlyAttached(): Boolean {
        val dev = activeDevice ?: return false
        val mgr = usbManager ?: return false
        return isConnected() && mgr.deviceList.containsKey(dev.deviceName)
    }

    private fun create78k0Transport(): Nec78k0ProtocolEngine.Serial78k0Transport =
        object : Nec78k0ProtocolEngine.Serial78k0Transport {
            override fun configureUart96008N1(onLog: (String) -> Unit): Boolean {
                val conn = activeConnection ?: return false
                val dev = activeDevice ?: return false
                configureUartHardware(
                    conn = conn,
                    device = dev,
                    chipType = activeChipType,
                    baudRate = 9600,
                    onLog = onLog,
                    purgeHardwareFifo = (readerJob?.isActive != true)
                )
                return true
            }

            override fun ensureReaderListening(onLog: (String) -> Unit): Boolean {
                val conn = activeConnection ?: return false
                val epIn = inEndpoint ?: return false
                if (readerJob?.isActive != true) {
                    startContinuousReaderLoop(conn, epIn)
                }
                return true
            }

            override fun setRawRxMonitor(enabled: Boolean) {
                rawRxMonitorEnabled = enabled
            }

            override fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit) {
                val conn = activeConnection ?: return
                val dev = activeDevice ?: return
                this@UsbOtgProgrammerService.setControlLines(conn, dev, activeChipType, dtr = dtr, rts = rts, onLog = onLog)
            }

            override fun setDtr(enabled: Boolean, onLog: (String) -> Unit) {
                setSingleDtr(enabled, onLog)
            }

            override fun setRts(enabled: Boolean, onLog: (String) -> Unit) {
                setSingleRts(enabled, onLog)
            }

            override fun getInvertDtrRtsPolarity(): Boolean = nec78k0InvertDtrRtsPolarity

            override fun setInvertDtrRtsPolarity(inverted: Boolean) {
                nec78k0InvertDtrRtsPolarity = inverted
            }

            override fun flushInputBuffer() {
                flushRxBuffer()
            }

            override fun isDeviceAttached(): Boolean = isDeviceCurrentlyAttached()

            override suspend fun writeRaw(bytes: ByteArray): String? = writeRawBytes(bytes)

            override suspend fun writeFrameBytes(
                bytes: ByteArray,
                timeoutMs: Int,
                onLog: (String) -> Unit
            ): String? = writeFrame64BytePackets(bytes, timeoutMs, onLog)

            override suspend fun discardEcho(expectedCount: Int, baudRate: Int): Int {
                return if (echoCancellationEnabled) discardEchoBytes(expectedCount) else 0
            }

            override suspend fun readBytesWithTimeout(maxBytes: Int, timeoutMs: Int): ByteArray {
                val out = ByteArrayOutputStream(maxBytes.coerceAtLeast(1))
                val deadline = System.currentTimeMillis() + timeoutMs.toLong()
                while (out.size() < maxBytes && System.currentTimeMillis() < deadline) {
                    var drained = false
                    synchronized(rxLock) {
                        while (rxBuffer.isNotEmpty() && out.size() < maxBytes) {
                            out.write(rxBuffer.removeFirst().toInt() and 0xFF)
                            drained = true
                        }
                    }
                    if (out.size() >= maxBytes || !isDeviceCurrentlyAttached()) break
                    delay(if (drained) 2L else 3L)
                }
                return out.toByteArray()
            }
        }

    suspend fun probeChipId(
        context: Context,
        preferredChip: ProgrammerChipType?,
        enableDtrRtsReset: Boolean,
        targetMainFamily: String? = null,
        preferredPartNumber: String? = null,
        idCodeBytes: ByteArray = R8cProtocolEngine.DEFAULT_ID_CODE.copyOf(),
        catalog: List<McuProcessor>,
        clockFrequency: String = "8 MHz",
        onLog: (String) -> Unit
    ): ChipAutoDetectResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        val famUpper = targetMainFamily.orEmpty().trim().uppercase()
        val is78k0Target = famUpper.contains("78K0") && !famUpper.contains("78K0R")
        val effDtrRts = if (is78k0Target) true else enableDtrRtsReset

        ProgrammerForegroundService.acquireSessionLock(context, "جاري فحص ومزامنة المعالج...")
        try {
            if (!ensureConnectionHealthy(context, preferredChip, 9600, effDtrRts, onLog)) {
                return@withContext ChipAutoDetectResult.Failure("تعذر فتح منفذ USB OTG للفحص التلقائي.")
            }

            val isR8cFamily = famUpper.isBlank() || famUpper.contains("R8C") || famUpper.contains("M16C")
            if (isR8cFamily) {
                return@withContext ChipAutoDetectResult.Failure(
                    "تم إيقاف ميزة التعرف التلقائي وحذف الصدى في عائلة Renesas R8C. اختر المعالج يدوياً ثم انتقل لتنفيذ القراءة والكتابة والمسح مباشرة."
                )
            }

            if (is78k0Target) {
                // Separate TX/RX lines without diode: NEVER drop RX bytes as echo!
                setEchoCancellation(false)
                val fallbackChip = preferredPartNumber?.let { part ->
                    catalog.firstOrNull { it.partNumber.equals(part, ignoreCase = true) }
                }
                val (info, err) = Nec78k0ProtocolEngine.autoDetectAndInspect(
                    context = context,
                    transport = create78k0Transport(),
                    clockFreqMhzText = clockFrequency,
                    preferredChip = fallbackChip,
                    catalog = catalog,
                    onLog = onLog
                )
                return@withContext if (info != null) {
                    ChipAutoDetectResult.Matched(
                        rawSignatureHex = info.rawSignatureHex,
                        matchedProcessor = info.matchedProcessor,
                        diagnosticMessage = "تم التعرف على ${info.deviceName} (${info.familyGroup}) • المساحة ${info.flashSizeBytes / 1024} KB (${info.blockCount} بلوك) • FullCS=${info.fullFlashChecksumHex} • B0CS=${info.block0ChecksumHex}",
                        nec78k0Info = info
                    )
                } else {
                    ChipAutoDetectResult.Failure(err ?: "فشل التعرف التلقائي على معالج NEC/Renesas 78K0.")
                }
            }

            val initialMode = resolveBootSyncMode(targetMainFamily)
            setEchoCancellation(true)
            if (!performSyncHandshake(effDtrRts, initialMode, onLog)) {
                return@withContext ChipAutoDetectResult.Failure("فشل إرسال نبضة المزامنة عبر USB. راجع تفاصيل الخطأ في السجل.")
            }
            val rl78Result = probeRl78Or78k0Sequence(catalog, onLog)
            if (rl78Result != null) return@withContext rl78Result

            ChipAutoDetectResult.Failure(
                "تم فتح المنفذ وتنفيذ تتابع RTS/TX Break وإلغاء صدى الدايود D1، ولكن المعالج لم يرد بأي بايتات. تأكد من دايود D1/D2 والتغذية والجهد."
            )
        } finally {
            ProgrammerForegroundService.releaseSessionLock(context)
        }
    }

    private suspend fun probeRl78Or78k0Sequence(
        catalog: List<McuProcessor>,
        onLog: (String) -> Unit
    ): ChipAutoDetectResult.Matched? {
        onLog("إرسال إطار طلب توقيع السليكون RL78/78K0 (01 01 C0 3F 03)...")
        var reply = transmitAndLog(
            byteArrayOf(0x01, 0x01, 0xC0.toByte(), 0x3F, 0x03),
            expectedMax = 48,
            timeoutMs = 400,
            onLog = onLog
        )
        // RL78 sends ACK (e.g. 02 01 06 F9 03) followed by Silicon Signature Data Frame
        if (reply.isNotEmpty() && reply.size <= 6 && !toPrintableAscii(reply).contains("R5F", ignoreCase = true)) {
            val sigFollowUp = readBufferedBytes(expectedMax = 48, timeoutMs = 450, onLog = onLog)
            if (sigFollowUp.isNotEmpty()) {
                onLog("RX (Signature Frame): ${toHex(sigFollowUp)}")
                reply += sigFollowUp
            }
        }
        if (reply.isEmpty()) {
            reply = transmitAndLog(byteArrayOf(0xC0.toByte()), expectedMax = 48, timeoutMs = 350, onLog = onLog)
        }
        if (reply.isEmpty()) return null

        val hexStr = toHex(reply)
        val ascii = toPrintableAscii(reply)
        val matched = matchCatalogBySignature(ascii, hexStr, catalog)
        return ChipAutoDetectResult.Matched(
            rawSignatureHex = hexStr,
            matchedProcessor = matched,
            diagnosticMessage = if (matched != null) {
                "تم التعرف على المعالج تلقائياً: ${matched.partNumber} (${matched.mainFamily} / ${matched.subFamily}) • الفلاش: ${matched.flashKb} KB"
            } else {
                "استجاب المعالج بالبيانات [$ascii | $hexStr]"
            }
        )
    }

    suspend fun executeReadFlash(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        idCodeBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        clockFrequency: String = "8 MHz",
        catalog: List<McuProcessor> = emptyList(),
        resumeSession: Nec78k0RecoveryManager.Nec78k0RecoverySession? = null,
        resumeCheckpoint: UniversalCheckpointManager.UniversalCheckpoint? = null,
        preDetected78k0Info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo? = null,
        autoRestoreChip: Boolean = true,
        on78k0DetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit = {},
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        val is78k0 = processor.is78k0Flmd0Mode()
        val effDtrRts = if (is78k0) true else enableDtrRtsReset
        val initialBaud = if (processor.protocolType == ProtocolFamilyType.R8C_M16C || is78k0) 9600 else baudRate

        ProgrammerForegroundService.acquireSessionLock(context, "جاري قراءة ذاكرة ${processor.partNumber}...", 0)
        val trackedProgress = wrapProgressWithFgs(
            context = context,
            defaultActionLabel = "جاري قراءة ${processor.partNumber}...",
            onProgress = onProgress
        )
        try {
            if (!ensureConnectionHealthy(context, preferredChip, initialBaud, effDtrRts, onLog)) {
                return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
            }

            if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
                setEchoCancellation(true)
                return@withContext R8cProtocolEngine.readFlash(
                    transport = createR8cTransport(),
                    selectedChip = processor,
                    baudRate = baudRate,
                    idBytes = idCodeBytes,
                    dataFlashMode = dataFlashMode,
                    context = context,
                    resumeFromCheckpoint = resumeCheckpoint,
                    onLog = onLog,
                    onProgress = trackedProgress
                )
            }

            if (is78k0) {
                setEchoCancellation(false)
                return@withContext Nec78k0ProtocolEngine.readOrRescueFlash(
                    context = context,
                    transport = create78k0Transport(),
                    selectedChip = processor,
                    clockFreqMhzText = clockFrequency,
                    catalog = catalog,
                    existingResumeSession = resumeSession,
                    preDetectedInfo = preDetected78k0Info,
                    autoRestoreChip = autoRestoreChip,
                    onDetectedInfo = on78k0DetectedInfo,
                    onLog = onLog,
                    onProgress = trackedProgress
                )
            }

            val totalSize = processor.flashSizeBytes
            trackedProgress(0, totalSize, "مزامنة المعالج...")
            val syncMode = resolveBootSyncMode(processor.mainFamily)
            if (!performSyncHandshake(effDtrRts, syncMode, onLog)) {
                return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة (راجع سجل الأحداث).")
            }

            val blockSize = 256
            val totalBlocks = (totalSize + blockSize - 1) / blockSize
            val preloaded = if (resumeCheckpoint != null &&
                resumeCheckpoint.operation == UniversalCheckpointManager.OperationKind.READ &&
                resumeCheckpoint.partNumber == processor.partNumber
            ) {
                UniversalCheckpointManager.loadBuffer(context, processor.partNumber, totalSize)
            } else null
            val resultBuffer = preloaded ?: FirmwareParser.createEmptyBuffer(totalSize)
            val startBlock = if (preloaded != null && resumeCheckpoint != null) {
                resumeCheckpoint.blockOrPageIndex.coerceIn(0, (totalBlocks - 1).coerceAtLeast(0))
            } else 0
            var bytesReadFromChip = (startBlock * blockSize).coerceAtMost(totalSize)
            if (startBlock > 0) {
                onLog("استكمال القراءة من البلوك ${startBlock + 1}/$totalBlocks (${bytesReadFromChip / 1024} KB محفوظة)...")
            }

            for (blockIdx in startBlock until totalBlocks) {
                ProgrammerForegroundService.awaitIfPaused()
                if (!isDeviceCurrentlyAttached()) {
                    UniversalCheckpointManager.saveCheckpoint(
                        context,
                        UniversalCheckpointManager.UniversalCheckpoint(
                            partNumber = processor.partNumber,
                            mainFamily = processor.mainFamily,
                            protocolTypeName = processor.protocolType.name,
                            operation = UniversalCheckpointManager.OperationKind.READ,
                            completedBytes = bytesReadFromChip,
                            totalBytes = totalSize,
                            blockOrPageIndex = blockIdx,
                            totalBlocksOrPages = totalBlocks,
                            fileName = "Dump_${processor.partNumber}.bin",
                            baudRate = baudRate,
                            flashBaseAddress = 0,
                            updatedAtMs = System.currentTimeMillis()
                        ),
                        resultBuffer
                    )
                    return@withContext FlashOperationResult.Failure("انقطع كابل USB OTG عند البلوك ${blockIdx + 1}/$totalBlocks — تم حفظ نقطة استكمال.")
                }

                val addr = blockIdx * blockSize
                val cmd = byteArrayOf(
                    0xFF.toByte(),
                    ((addr ushr 8) and 0xFF).toByte(),
                    ((addr ushr 16) and 0xFF).toByte()
                )
                val txErr = writeWithEchoCancel(cmd, clearStaleRx = true, onLog = onLog)
                if (txErr != null) {
                    UniversalCheckpointManager.saveCheckpoint(
                        context,
                        UniversalCheckpointManager.UniversalCheckpoint(
                            partNumber = processor.partNumber,
                            mainFamily = processor.mainFamily,
                            protocolTypeName = processor.protocolType.name,
                            operation = UniversalCheckpointManager.OperationKind.READ,
                            completedBytes = bytesReadFromChip,
                            totalBytes = totalSize,
                            blockOrPageIndex = blockIdx,
                            totalBlocksOrPages = totalBlocks,
                            fileName = "Dump_${processor.partNumber}.bin",
                            baudRate = baudRate,
                            flashBaseAddress = 0,
                            updatedAtMs = System.currentTimeMillis()
                        ),
                        resultBuffer
                    )
                    onLog("TX ERROR (Read 0x%06X): $txErr".format(addr))
                    return@withContext FlashOperationResult.Failure("فشل الإرسال عند 0x%06X: $txErr".format(addr))
                }
                val chunk = readExactFromBuffer(expectedLen = blockSize, timeoutMs = 550, onLog = onLog)
                if (chunk.isEmpty()) {
                    UniversalCheckpointManager.saveCheckpoint(
                        context,
                        UniversalCheckpointManager.UniversalCheckpoint(
                            partNumber = processor.partNumber,
                            mainFamily = processor.mainFamily,
                            protocolTypeName = processor.protocolType.name,
                            operation = UniversalCheckpointManager.OperationKind.READ,
                            completedBytes = bytesReadFromChip,
                            totalBytes = totalSize,
                            blockOrPageIndex = blockIdx,
                            totalBlocksOrPages = totalBlocks,
                            fileName = "Dump_${processor.partNumber}.bin",
                            baudRate = baudRate,
                            flashBaseAddress = 0,
                            updatedAtMs = System.currentTimeMillis()
                        ),
                        resultBuffer
                    )
                    return@withContext FlashOperationResult.Failure(
                        "توقف المعالج عن إرسال البيانات عند العنوان 0x%06X (RX فارغ بعد حذف الصدى)."
                            .format(addr)
                    )
                }
                val copyLen = min(chunk.size, totalSize - addr)
                System.arraycopy(chunk, 0, resultBuffer, addr, copyLen)
                bytesReadFromChip += copyLen
                if ((blockIdx + 1) % 8 == 0 && blockIdx + 1 < totalBlocks) {
                    UniversalCheckpointManager.saveCheckpoint(
                        context,
                        UniversalCheckpointManager.UniversalCheckpoint(
                            partNumber = processor.partNumber,
                            mainFamily = processor.mainFamily,
                            protocolTypeName = processor.protocolType.name,
                            operation = UniversalCheckpointManager.OperationKind.READ,
                            completedBytes = bytesReadFromChip,
                            totalBytes = totalSize,
                            blockOrPageIndex = blockIdx + 1,
                            totalBlocksOrPages = totalBlocks,
                            fileName = "Dump_${processor.partNumber}.bin",
                            baudRate = baudRate,
                            flashBaseAddress = 0,
                            updatedAtMs = System.currentTimeMillis()
                        ),
                        resultBuffer
                    )
                }
                trackedProgress(
                    bytesReadFromChip,
                    totalSize,
                    "قراءة بلوك ${blockIdx + 1}/$totalBlocks (0x%06X)".format(addr)
                )
            }
            UniversalCheckpointManager.clearCheckpoint(context, processor.partNumber)

            FlashOperationResult.Success(
                message = "تمت قراءة وسحب ${bytesReadFromChip / 1024} KB ($bytesReadFromChip بايت) من ${processor.partNumber} بنجاح.",
                bytesTransferred = resultBuffer
            )
        } finally {
            ProgrammerForegroundService.releaseSessionLock(context)
        }
    }

    suspend fun executeErase(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        eraseMode: String,
        idCodeBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        clockFrequency: String = "8 MHz",
        catalog: List<McuProcessor> = emptyList(),
        preDetected78k0Info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo? = null,
        on78k0DetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit = {},
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        val is78k0 = processor.is78k0Flmd0Mode()
        val effDtrRts = if (is78k0) true else enableDtrRtsReset
        val initialBaud = if (processor.protocolType == ProtocolFamilyType.R8C_M16C || is78k0) 9600 else baudRate

        ProgrammerForegroundService.acquireSessionLock(context, "جاري مسح ذاكرة ${processor.partNumber}...", 0)
        val trackedProgress = wrapProgressWithFgs(
            context = context,
            defaultActionLabel = "جاري مسح ${processor.partNumber}...",
            onProgress = onProgress
        )
        try {
            if (!ensureConnectionHealthy(context, preferredChip, initialBaud, effDtrRts, onLog)) {
                return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
            }

            if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
                setEchoCancellation(true)
                return@withContext R8cProtocolEngine.eraseChip(
                    transport = createR8cTransport(),
                    selectedChip = processor,
                    baudRate = baudRate,
                    idBytes = idCodeBytes,
                    onLog = onLog,
                    onProgress = trackedProgress
                )
            }

            if (is78k0) {
                setEchoCancellation(false)
                return@withContext Nec78k0FlashWriterEngine.eraseFlash(
                    context = context,
                    transport = create78k0Transport(),
                    selectedChip = processor,
                    eraseMode = eraseMode,
                    clockFreqMhzText = clockFrequency,
                    catalog = catalog,
                    preDetectedInfo = preDetected78k0Info,
                    onDetectedInfo = on78k0DetectedInfo,
                    onLog = onLog,
                    onProgress = trackedProgress
                )
            }

            val totalSize = processor.flashSizeBytes
            val syncMode = resolveBootSyncMode(processor.mainFamily)
            if (!performSyncHandshake(effDtrRts, syncMode, onLog)) {
                return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة.")
            }

            trackedProgress(totalSize / 3, totalSize, "إرسال أمر المسح ($eraseMode)...")
            val eraseCmd = when (processor.protocolType) {
                ProtocolFamilyType.RL78_RX -> byteArrayOf(0x01, 0x04, 0x22, 0x00, 0x00, 0x00, 0xD9.toByte(), 0x03)
                else -> byteArrayOf(0x20, 0xD0.toByte())
            }
            val ack = transmitAndLog(eraseCmd, expectedMax = 8, timeoutMs = 1500, onLog = onLog)
            if (ack.isEmpty()) {
                return@withContext FlashOperationResult.Failure("لم يرد المعالج بتأكيد إتمام المسح (Timeout 1500ms).")
            }
            trackedProgress(totalSize, totalSize, "اكتمل المسح")
            FlashOperationResult.Success("تم مسح ذاكرة الفلاش بنجاح (${toHex(ack)}).")
        } finally {
            ProgrammerForegroundService.releaseSessionLock(context)
        }
    }

    suspend fun executeWriteAndVerify(
        context: Context,
        preferredChip: ProgrammerChipType?,
        baudRate: Int,
        enableDtrRtsReset: Boolean,
        processor: McuProcessor,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int = 0,
        idCodeBytes: ByteArray,
        verifyOnly: Boolean,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        dataFlashBytes: ByteArray? = null,
        autoEraseBeforeWrite: Boolean = true,
        autoVerifyAfterWrite: Boolean = false,
        eraseMode: String = "مسح كلي (Chip Erase)",
        clockFrequency: String = "8 MHz",
        catalog: List<McuProcessor> = emptyList(),
        resumeCheckpoint: UniversalCheckpointManager.UniversalCheckpoint? = null,
        fileName: String = "",
        preDetected78k0Info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo? = null,
        on78k0DetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit = {},
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = withContext(Dispatchers.IO) {
        externalLogSink = onLog
        val is78k0 = processor.is78k0Flmd0Mode()
        val effDtrRts = if (is78k0) true else enableDtrRtsReset
        val initialBaud = if (processor.protocolType == ProtocolFamilyType.R8C_M16C || is78k0) 9600 else baudRate

        val fgsMsg = if (verifyOnly) "جاري مطابقة ${processor.partNumber}..." else "جاري شحن وكتابة ${processor.partNumber}..."
        ProgrammerForegroundService.acquireSessionLock(context, fgsMsg, 0)
        val trackedProgress = wrapProgressWithFgs(
            context = context,
            defaultActionLabel = fgsMsg,
            onProgress = onProgress
        )
        try {
            if (!ensureConnectionHealthy(context, preferredChip, initialBaud, effDtrRts, onLog)) {
                return@withContext FlashOperationResult.Failure("يجب الاتصال بمبرمجة USB OTG أولاً.")
            }

            if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
                setEchoCancellation(true)
                return@withContext if (verifyOnly) {
                    R8cProtocolEngine.verifyFlash(
                        transport = createR8cTransport(),
                        selectedChip = processor,
                        baudRate = baudRate,
                        firmwareBytes = firmwareBytes,
                        firmwareStartAddress = firmwareStartAddress,
                        idBytes = idCodeBytes,
                        dataFlashMode = dataFlashMode,
                        dataFlashBytes = dataFlashBytes,
                        onLog = onLog,
                        onProgress = trackedProgress
                    )
                } else {
                    R8cProtocolEngine.writeFlash(
                        transport = createR8cTransport(),
                        selectedChip = processor,
                        baudRate = baudRate,
                        firmwareBytes = firmwareBytes,
                        firmwareStartAddress = firmwareStartAddress,
                        idBytes = idCodeBytes,
                        dataFlashMode = dataFlashMode,
                        dataFlashBytes = dataFlashBytes,
                        autoEraseBeforeWrite = autoEraseBeforeWrite,
                        autoVerifyAfterWrite = autoVerifyAfterWrite,
                        context = context,
                        resumeFromCheckpoint = resumeCheckpoint,
                        fileName = fileName,
                        onLog = onLog,
                        onProgress = trackedProgress
                    )
                }
            }

            if (is78k0) {
                setEchoCancellation(false)
                val transport78k0 = create78k0Transport()
                val resumeBlock78k0 = if (!verifyOnly &&
                    resumeCheckpoint != null &&
                    resumeCheckpoint.operation == UniversalCheckpointManager.OperationKind.WRITE &&
                    resumeCheckpoint.partNumber == processor.partNumber
                ) resumeCheckpoint.blockOrPageIndex else 0
                return@withContext if (verifyOnly) {
                    Nec78k0FlashWriterEngine.verifyFlash(
                        context = context,
                        transport = transport78k0,
                        selectedChip = processor,
                        firmwareBytes = firmwareBytes,
                        firmwareStartAddress = firmwareStartAddress,
                        clockFreqMhzText = clockFrequency,
                        catalog = catalog,
                        preDetectedInfo = preDetected78k0Info,
                        onDetectedInfo = on78k0DetectedInfo,
                        onLog = onLog,
                        onProgress = trackedProgress
                    )
                } else {
                    Nec78k0FlashWriterEngine.writeFlash(
                        context = context,
                        transport = transport78k0,
                        selectedChip = processor,
                        firmwareBytes = firmwareBytes,
                        firmwareStartAddress = firmwareStartAddress,
                        autoEraseBeforeWrite = autoEraseBeforeWrite,
                        autoVerifyAfterWrite = autoVerifyAfterWrite,
                        eraseMode = eraseMode,
                        clockFreqMhzText = clockFrequency,
                        catalog = catalog,
                        preDetectedInfo = preDetected78k0Info,
                        resumeStartBlock = resumeBlock78k0,
                        fileName = fileName,
                        onDetectedInfo = on78k0DetectedInfo,
                        onLog = onLog,
                        onProgress = trackedProgress
                    )
                }
            }

            val totalSize = firmwareBytes.size
            val syncMode = resolveBootSyncMode(processor.mainFamily)
            if (!performSyncHandshake(enableDtrRtsReset, syncMode, onLog)) {
                return@withContext FlashOperationResult.Failure("فشلت مرحلة الإرسال أثناء المزامنة.")
            }

            val pageSize = 256
            val totalPages = (totalSize + pageSize - 1) / pageSize
            val startPage = if (!verifyOnly &&
                resumeCheckpoint != null &&
                resumeCheckpoint.operation == UniversalCheckpointManager.OperationKind.WRITE &&
                resumeCheckpoint.partNumber == processor.partNumber
            ) {
                resumeCheckpoint.blockOrPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            } else 0

            for (page in startPage until totalPages) {
                ProgrammerForegroundService.awaitIfPaused()
                val offset = page * pageSize
                val len = min(pageSize, totalSize - offset)
                val bytesDone = min(offset + len, totalSize)
                val slice = firmwareBytes.copyOfRange(offset, offset + len)

                if (!isDeviceCurrentlyAttached()) {
                    if (!verifyOnly) {
                        UniversalCheckpointManager.saveCheckpoint(
                            context,
                            UniversalCheckpointManager.UniversalCheckpoint(
                                partNumber = processor.partNumber,
                                mainFamily = processor.mainFamily,
                                protocolTypeName = processor.protocolType.name,
                                operation = UniversalCheckpointManager.OperationKind.WRITE,
                                completedBytes = offset,
                                totalBytes = totalSize,
                                blockOrPageIndex = page,
                                totalBlocksOrPages = totalPages,
                                fileName = fileName,
                                baudRate = baudRate,
                                flashBaseAddress = firmwareStartAddress,
                                updatedAtMs = System.currentTimeMillis()
                            ),
                            firmwareBytes
                        )
                    }
                    return@withContext FlashOperationResult.Failure("انقطع كابل USB OTG عند الصفحة ${page + 1}/$totalPages — تم حفظ التقدم للاستكمال.")
                }

                if (!verifyOnly && slice.all { it == 0xFF.toByte() }) {
                    trackedProgress(bytesDone, totalSize, "تخطي صفحة فارغة 0x%06X".format(offset))
                    continue
                }

                if (verifyOnly) {
                    val readCmd = byteArrayOf(
                        0xFF.toByte(),
                        (offset and 0xFF).toByte(),
                        ((offset ushr 8) and 0xFF).toByte(),
                        ((offset ushr 16) and 0xFF).toByte()
                    )
                    val txErr = writeWithEchoCancel(readCmd, clearStaleRx = true, onLog = onLog)
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
                    val txErr = writeWithEchoCancel(header + slice, clearStaleRx = true, onLog = onLog)
                    if (txErr != null) {
                        UniversalCheckpointManager.saveCheckpoint(
                            context,
                            UniversalCheckpointManager.UniversalCheckpoint(
                                partNumber = processor.partNumber,
                                mainFamily = processor.mainFamily,
                                protocolTypeName = processor.protocolType.name,
                                operation = UniversalCheckpointManager.OperationKind.WRITE,
                                completedBytes = offset,
                                totalBytes = totalSize,
                                blockOrPageIndex = page,
                                totalBlocksOrPages = totalPages,
                                fileName = fileName,
                                baudRate = baudRate,
                                flashBaseAddress = firmwareStartAddress,
                                updatedAtMs = System.currentTimeMillis()
                            ),
                            firmwareBytes
                        )
                        onLog("TX ERROR (Write 0x%06X): $txErr".format(offset))
                        return@withContext FlashOperationResult.Failure("فشل إرسال صفحة 0x%06X: $txErr".format(offset))
                    }
                    val ack = readBufferedBytes(expectedMax = 4, timeoutMs = 450, onLog = onLog)
                    if (ack.isEmpty()) {
                        UniversalCheckpointManager.saveCheckpoint(
                            context,
                            UniversalCheckpointManager.UniversalCheckpoint(
                                partNumber = processor.partNumber,
                                mainFamily = processor.mainFamily,
                                protocolTypeName = processor.protocolType.name,
                                operation = UniversalCheckpointManager.OperationKind.WRITE,
                                completedBytes = offset,
                                totalBytes = totalSize,
                                blockOrPageIndex = page,
                                totalBlocksOrPages = totalPages,
                                fileName = fileName,
                                baudRate = baudRate,
                                flashBaseAddress = firmwareStartAddress,
                                updatedAtMs = System.currentTimeMillis()
                            ),
                            firmwareBytes
                        )
                        return@withContext FlashOperationResult.Failure("لم يستجب المعالج بتأكيد كتابة الصفحة عند 0x%06X.".format(offset))
                    }
                    if ((page + 1) % 8 == 0 && page + 1 < totalPages) {
                        UniversalCheckpointManager.saveCheckpoint(
                            context,
                            UniversalCheckpointManager.UniversalCheckpoint(
                                partNumber = processor.partNumber,
                                mainFamily = processor.mainFamily,
                                protocolTypeName = processor.protocolType.name,
                                operation = UniversalCheckpointManager.OperationKind.WRITE,
                                completedBytes = bytesDone,
                                totalBytes = totalSize,
                                blockOrPageIndex = page + 1,
                                totalBlocksOrPages = totalPages,
                                fileName = fileName,
                                baudRate = baudRate,
                                flashBaseAddress = firmwareStartAddress,
                                updatedAtMs = System.currentTimeMillis()
                            ),
                            firmwareBytes
                        )
                    }
                }

                trackedProgress(
                    bytesDone,
                    totalSize,
                    "${if (verifyOnly) "مطابقة" else "كتابة"} صفحة ${page + 1}/$totalPages (0x%06X)".format(offset)
                )
            }
            if (!verifyOnly) {
                UniversalCheckpointManager.clearCheckpoint(context, processor.partNumber)
            }

            val doneMsg = if (verifyOnly) {
                "تمت مطابقة (Verify) كامل الملف (${totalSize / 1024} KB) بنسبة 100%."
            } else {
                "تم شحن (Write) السوفت وير بنجاح إلى ${processor.partNumber}."
            }
            FlashOperationResult.Success(doneMsg)
        } finally {
            ProgrammerForegroundService.releaseSessionLock(context)
        }
    }

    private fun wrapProgressWithFgs(
        context: Context,
        defaultActionLabel: String,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): (Int, Int, String) -> Unit = { done, total, label ->
        ProgrammerForegroundService.updateThroughputAndEta(done, total)
        val pct = if (total > 0) ((done.toLong() * 100L) / total.toLong()).toInt().coerceIn(0, 100) else -1
        val fgsText = if (label.isNotBlank()) label else defaultActionLabel
        ProgrammerForegroundService.renewSessionLock(context, fgsText, pct)
        onProgress(done, total, label)
    }

    private suspend fun sendR8cUnlockFrame(
        idCodeBytes: ByteArray,
        onLog: (String) -> Unit
    ): Boolean {
        val frame = byteArrayOf(
            0xF5.toByte(),
            0xFF.toByte(),
            0xDF.toByte(),
            0x00.toByte(),
            0x07.toByte()
        ) + idCodeBytes

        var ack = transmitAndLog(frame, expectedMax = 8, timeoutMs = 350, onLog = onLog)
        if (ack.isEmpty()) {
            delay(20)
            ack = transmitAndLog(byteArrayOf(0x70), expectedMax = 4, timeoutMs = 300, onLog = onLog)
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
        val txErr = writeWithEchoCancel(txBytes, clearStaleRx = true, onLog = onLog)
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
        val regex = Regex("""R[45]F[A-Z0-9]{4,10}|M3[A-Z0-9]{4,10}|uPD78F[A-Z0-9]{4,8}""", RegexOption.IGNORE_CASE)
        regex.findAll(ascii).forEach { match ->
            val extracted = match.value.trim()
            catalog.firstOrNull { it.partNumber.equals(extracted, ignoreCase = true) }?.let { return it }
            catalog.filter { extracted.startsWith(it.partNumber, ignoreCase = true) }
                .maxByOrNull { it.partNumber.length }
                ?.let { return it }
            catalog.firstOrNull {
                it.partNumber.startsWith(extracted, ignoreCase = true) ||
                    it.partNumber.contains(extracted, ignoreCase = true)
            }?.let { return it }
        }
        val cleanAscii = ascii.replace(Regex("[^A-Za-z0-9]"), "")
        if (cleanAscii.length >= 4) {
            catalog.filter { cleanAscii.contains(it.partNumber, ignoreCase = true) }
                .maxByOrNull { it.partNumber.length }
                ?.let { return it }
            catalog.firstOrNull {
                it.partNumber.length >= 5 && cleanAscii.contains(it.partNumber.take(6), ignoreCase = true)
            }?.let { return it }
        }
        return null
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
                synchronized(usbStateLock) {
                    claimedInterfaces.add(iface)
                }
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
        onLog: ((String) -> Unit)?,
        purgeHardwareFifo: Boolean = false
    ) {
        currentBaudRate = baudRate
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
                    val rPurge = if (purgeHardwareFifo) {
                        conn.controlTransfer(0x41, 0x12, 0x000F, idx, null, 0, 500)
                    } else 0
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
            isUartLineConfigured = true
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
        currentDtrState = dtr
        currentRtsState = rts
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

    private fun setSingleDtr(enabled: Boolean, onLog: ((String) -> Unit)? = null) {
        currentDtrState = enabled
        val conn = activeConnection ?: return
        val dev = activeDevice ?: return
        try {
            val ret = when (activeChipType) {
                ProgrammerChipType.CP2102 -> {
                    val value = if (enabled) 0x0101 else 0x0100
                    conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
                }
                ProgrammerChipType.FT232RL -> {
                    val value = if (enabled) 0x0101 else 0x0100
                    conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
                }
                else -> {
                    setControlLines(conn, dev, activeChipType, dtr = currentDtrState, rts = currentRtsState, onLog = onLog)
                    0
                }
            }
            if (ret < 0) {
                onLog?.invoke("setDTR($enabled) Warning ($activeChipType): controlTransfer returned $ret")
            }
        } catch (e: Throwable) {
            onLog?.invoke("setDTR($enabled) Exception: ${e.toString()}")
        }
    }

    private fun setSingleRts(enabled: Boolean, onLog: ((String) -> Unit)? = null) {
        currentRtsState = enabled
        val conn = activeConnection ?: return
        val dev = activeDevice ?: return
        try {
            val ret = when (activeChipType) {
                ProgrammerChipType.CP2102 -> {
                    val value = if (enabled) 0x0202 else 0x0200
                    conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
                }
                ProgrammerChipType.FT232RL -> {
                    val value = if (enabled) 0x0202 else 0x0200
                    conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
                }
                else -> {
                    setControlLines(conn, dev, activeChipType, dtr = currentDtrState, rts = currentRtsState, onLog = onLog)
                    0
                }
            }
            if (ret < 0) {
                onLog?.invoke("setRTS($enabled) Warning ($activeChipType): controlTransfer returned $ret")
            }
        } catch (e: Throwable) {
            onLog?.invoke("setRTS($enabled) Exception: ${e.toString()}")
        }
    }

    private fun setTxBreak(
        conn: UsbDeviceConnection,
        device: UsbDevice,
        chipType: ProgrammerChipType?,
        enabled: Boolean,
        onLog: ((String) -> Unit)? = null
    ): Boolean {
        return try {
            val ret = when (chipType) {
                ProgrammerChipType.CP2102 -> {
                    conn.controlTransfer(0x41, 0x05, if (enabled) 0x0001 else 0x0000, controlInterfaceIndex, null, 0, 300)
                }
                ProgrammerChipType.FT232RL -> {
                    conn.controlTransfer(0x40, 0x04, if (enabled) 0x4008 else 0x0008, 0, null, 0, 300)
                }
                ProgrammerChipType.CH340G -> {
                    conn.controlTransfer(0x40, 0x9A, 0x05, if (enabled) 0x0000 else 0x0001, null, 0, 300)
                }
                else -> {
                    conn.controlTransfer(0x21, 0x23, if (enabled) 0xFFFF else 0x0000, controlInterfaceIndex, null, 0, 300)
                }
            }
            if (ret < 0) {
                onLog?.invoke("TX Break Warning ($chipType, enabled=$enabled): controlTransfer returned $ret")
            }
            ret >= 0
        } catch (e: Throwable) {
            onLog?.invoke("TX Break Exception (${device.deviceName}): ${e.toString()}")
            false
        }
    }

    private suspend fun awaitSingleByteInRxQueue(expectedByte: Byte, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        while (true) {
            synchronized(rxLock) {
                if (rxBuffer.isNotEmpty()) {
                    val b = rxBuffer.removeFirst()
                    if (b == expectedByte) return true
                }
            }
            if (System.currentTimeMillis() >= deadline) return false
            delay(2)
        }
    }

    /**
     * Literal R8CTool.EXE Single-Wire Diode-Echo Exchange (N + M):
     * Clears stale rxBuffer, sends [txBytes] (length N), and waits strictly for N + [expectedRxLength] bytes.
     * Drops the first N echoed bytes and returns the remaining M genuine MCU reply bytes.
     */
    private suspend fun executeStrictR8cExchange(
        txBytes: ByteArray,
        expectedRxLength: Int,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): R8cProtocolEngine.R8cExchangeResult {
        val conn = activeConnection
        val epIn = inEndpoint
        if (conn != null && epIn != null && readerJob?.isActive != true) {
            startContinuousReaderLoop(conn, epIn)
        }
        synchronized(rxLock) {
            rxBuffer.clear()
        }
        val txErr = writeRawBytes(txBytes)
        if (txErr != null) {
            return R8cProtocolEngine.R8cExchangeResult(txError = txErr)
        }

        val n = txBytes.size
        val m = expectedRxLength.coerceAtLeast(0)
        val totalNeeded = n + m
        if (totalNeeded <= 0) {
            return R8cProtocolEngine.R8cExchangeResult()
        }

        val baud = currentBaudRate.coerceAtLeast(300)
        val wireBudgetMs = ((totalNeeded * 11_500L) / baud) + 250L
        val effectiveTimeoutMs = maxOf(timeoutMs.toLong(), wireBudgetMs)
        val deadline = System.currentTimeMillis() + effectiveTimeoutMs

        val rawStream = ByteArrayOutputStream(totalNeeded.coerceAtLeast(16))
        try {
            while (rawStream.size() < totalNeeded) {
                var drainedAny = false
                synchronized(rxLock) {
                    while (rxBuffer.isNotEmpty() && rawStream.size() < totalNeeded) {
                        rawStream.write(rxBuffer.removeFirst().toInt() and 0xFF)
                        drainedAny = true
                    }
                }
                if (rawStream.size() >= totalNeeded) break
                if (System.currentTimeMillis() >= deadline) break
                delay(if (drainedAny) 2L else 3L)
            }
        } catch (e: Throwable) {
            onLog("R8C Exchange Exception: ${e.toString()}")
        }

        val rawBytes = rawStream.toByteArray()
        val echoedCount = min(n, rawBytes.size)
        val reply = if (rawBytes.size > n) {
            rawBytes.copyOfRange(n, rawBytes.size)
        } else {
            ByteArray(0)
        }
        return R8cProtocolEngine.R8cExchangeResult(
            txError = null,
            totalRawReceived = rawBytes.size,
            echoedCount = echoedCount,
            replyBytes = reply,
            rawBytes = rawBytes
        )
    }

    /**
     * Transmits txBytes and cancels the 1N4148 diode D1 loopback echo by removing
     * the first txBytes.size bytes arriving in rxBuffer.
     */
    private suspend fun writeWithEchoCancel(
        txBytes: ByteArray,
        clearStaleRx: Boolean = true,
        onLog: ((String) -> Unit)? = null
    ): String? {
        if (clearStaleRx) {
            synchronized(rxLock) {
                rxBuffer.clear()
            }
        }
        val txErr = writeRawBytes(txBytes)
        if (txErr != null) return txErr

        if (echoCancellationEnabled && txBytes.isNotEmpty()) {
            val dropped = discardEchoBytes(txBytes.size)
            if (dropped < txBytes.size && txBytes.size > 1) {
                onLog?.invoke("D1 Echo Notice: حُذف $dropped / ${txBytes.size} بايت صدى")
            }
        }
        return null
    }

    private suspend fun discardEchoBytes(expectedEchoCount: Int): Int {
        if (expectedEchoCount <= 0) return 0
        val baud = currentBaudRate.coerceAtLeast(300)
        val wireWindowMs = ((expectedEchoCount * 11_000L) / baud) + 120L
        val deadline = System.currentTimeMillis() + wireWindowMs
        var discarded = 0

        while (discarded < expectedEchoCount) {
            var tookAny = false
            synchronized(rxLock) {
                while (rxBuffer.isNotEmpty() && discarded < expectedEchoCount) {
                    rxBuffer.removeFirst()
                    discarded++
                    tookAny = true
                }
            }
            if (discarded >= expectedEchoCount) break
            if (System.currentTimeMillis() >= deadline) break
            delay(if (tookAny) 2L else 3L)
        }
        return discarded
    }

    /**
     * Returns null on success, or the exact technical error string (including Exception e.toString()) on failure.
     */
    private fun writeRawBytes(data: ByteArray): String? {
        return try {
            val conn = activeConnection ?: return "USB_DETACHED:UsbDeviceConnection is null (المنفذ مغلق)"
            val out = outEndpoint ?: return "USB_DETACHED:Bulk OUT endpoint is null"
            var offset = 0
            while (offset < data.size) {
                val chunkLen = min(data.size - offset, out.maxPacketSize.coerceAtLeast(64))
                val slice = if (offset == 0 && chunkLen == data.size) data
                else data.copyOfRange(offset, offset + chunkLen)
                val written = conn.bulkTransfer(out, slice, slice.size, 2000)
                if (written <= 0) {
                    val attached = activeDevice?.deviceName?.let { usbManager?.deviceList?.containsKey(it) } ?: false
                    if (!attached) {
                        disconnect()
                        return "USB_DETACHED:انفصل كابل USB OTG عن الهاتف أثناء الإرسال"
                    }
                    return "bulkTransfer(OUT) returned $written (len=${slice.size}, ep=0x%02X, attached=$attached, chip=$activeChipType)"
                        .format(out.address)
                }
                offset += written
            }
            null
        } catch (e: Throwable) {
            val attached = activeDevice?.deviceName?.let { usbManager?.deviceList?.containsKey(it) } ?: false
            if (!attached || e is java.io.IOException) {
                disconnect()
            }
            "SERIAL_IO_EXCEPTION:${e::class.java.simpleName}: ${e.message ?: e.toString()}"
        }
    }

    private suspend fun writeFrame64BytePackets(
        data: ByteArray,
        timeoutMs: Int = 1000,
        onLog: (String) -> Unit
    ): String? {
        return try {
            val conn = activeConnection ?: return "USB_DETACHED:UsbDeviceConnection is null (المنفذ مغلق)"
            val out = outEndpoint ?: return "USB_DETACHED:Bulk OUT endpoint is null"
            var offset = 0
            var totalSent = 0
            while (offset < data.size) {
                val chunkLen = minOf(64, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + chunkLen)
                val written = conn.bulkTransfer(out, chunk, chunk.size, timeoutMs)
                if (written <= 0) {
                    val attached = activeDevice?.deviceName?.let { usbManager?.deviceList?.containsKey(it) } ?: false
                    if (!attached) {
                        disconnect()
                        return "USB_DETACHED:انفصل كابل USB OTG عن الهاتف أثناء الإرسال ($totalSent/${data.size})"
                    }
                    return "bulkTransfer(OUT 64B packet) returned $written at offset=$offset ($totalSent/${data.size}, len=${chunk.size}, ep=0x%02X, attached=$attached, chip=$activeChipType)"
                        .format(out.address)
                }
                totalSent += written
                offset += written
                if (offset < data.size) {
                    delay(15L) // 64B / 15ms safe USB-UART pacing (~4x faster than 32B/20ms without FIFO overflow)
                }
            }
            onLog("تم إرسال $totalSent/${data.size} بايت بنجاح (64B/15ms)")
            null
        } catch (e: Throwable) {
            val attached = activeDevice?.deviceName?.let { usbManager?.deviceList?.containsKey(it) } ?: false
            if (!attached || e is java.io.IOException) {
                disconnect()
            }
            "SERIAL_IO_EXCEPTION:${e::class.java.simpleName}: ${e.message ?: e.toString()}"
        }
    }

    /**
     * Reads up to expectedMax bytes from rxBuffer, stopping early on 45ms inter-byte idle gap.
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
                if (lastByteAt > 0L && (now - lastByteAt) >= 45L) break
                delay(4)
            }
        } catch (e: Throwable) {
            onLog("Read Exception: ${e.toString()}")
        }
        return out.toByteArray()
    }

    /**
     * Waits until exact [expectedLen] bytes arrive in rxBuffer or [timeoutMs] expires
     * (matching C++ rs232c_io::recv(dst, length, tv) for 1/2/8/256-byte R8C frames).
     */
    private suspend fun readExactFromBuffer(
        expectedLen: Int,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val wireBudget = ((expectedLen * 11_000L) / currentBaudRate.coerceAtLeast(300)) + 250L
        val effectiveTimeout = maxOf(timeoutMs.toLong(), wireBudget)
        val deadline = System.currentTimeMillis() + effectiveTimeout

        try {
            while (out.size() < expectedLen) {
                var drainedAny = false
                synchronized(rxLock) {
                    while (rxBuffer.isNotEmpty() && out.size() < expectedLen) {
                        out.write(rxBuffer.removeFirst().toInt() and 0xFF)
                        drainedAny = true
                    }
                }
                if (out.size() >= expectedLen) break
                if (System.currentTimeMillis() >= deadline) break
                delay(if (drainedAny) 2L else 4L)
            }
        } catch (e: Throwable) {
            onLog("ReadExact Exception: ${e.toString()}")
        }
        return out.toByteArray()
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun toPrintableAscii(bytes: ByteArray): String =
        bytes.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
}
