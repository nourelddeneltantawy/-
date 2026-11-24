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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Isolated USB OTG Serial Transport exclusively for com.example.adminlab.
 * Supports FT232R, CH340G, CP2102, PL2303, CDC-ACM, echo stripping, and Safe Idle Hot-Swap release.
 */
class ProberUsbTransport(
    context: Context,
    private val onDeviceStatusChanged: (ProberUsbStatus) -> Unit,
    private val onUnexpectedDetach: () -> Unit,
    private val onLog: (ProberLogDirection, String) -> Unit
) {
    companion object {
        private const val ACTION_USB_PERM = "com.example.adminlab.USB_PROBER_PERM"
    }

    private val appContext = context.applicationContext
    private val usbManager: UsbManager? = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionLock = Any()
    private val rxQueue = ArrayDeque<Byte>(8192)
    private val claimedInterfaces = mutableListOf<UsbInterface>()

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
    var currentBaudRate: Int = 9600
        private set
    @Volatile
    var echoLoopbackObserved: Boolean = false
    @Volatile
    private var currentDtrState: Boolean = false
    @Volatile
    private var currentRtsState: Boolean = false

    @Volatile
    private var readerJob: Job? = null
    private var attachReceiver: BroadcastReceiver? = null

    init {
        registerAttachDetachReceiver()
    }

    fun currentChip(): ProberUsbChip = activeChip
    fun isUsbdmSession(): Boolean = activeChip == ProberUsbChip.USBDM

    fun scanStatus(): ProberUsbStatus {
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
                deviceCount = 0,
                isUsbdm = false
            )
        } else {
            val chip = ProberUsbChip.fromDevice(chosen.vendorId, chosen.productId)
            val perm = mgr?.hasPermission(chosen) == true
            ProberUsbStatus(
                isConnected = true,
                adapterName = chosen.productName?.takeIf { it.isNotBlank() } ?: chip.displayName,
                chipModel = chip.displayName,
                vidPidHex = "VID:%04X PID:%04X".format(chosen.vendorId, chosen.productId),
                hasPermission = perm,
                deviceCount = devices.size,
                isUsbdm = (chip == ProberUsbChip.USBDM)
            )
        }
        onDeviceStatusChanged(status)
        return status
    }

    suspend fun requestPermissionOnly(): Boolean {
        val mgr = usbManager ?: return false
        val dev = pickBestSerialDevice(mgr.deviceList.values.toList()) ?: return false
        if (mgr.hasPermission(dev)) {
            scanStatus()
            return true
        }
        val granted = requestPermissionSuspend(mgr, dev)
        scanStatus()
        return granted
    }

    suspend fun openSession(): Boolean {
        val mgr = usbManager ?: run {
            onLog(ProberLogDirection.ERROR, "USB Host غير متوفر على هذا الجهاز.")
            return false
        }
        val dev = pickBestSerialDevice(mgr.deviceList.values.toList()) ?: run {
            scanStatus()
            onLog(ProberLogDirection.ERROR, "لا توجد مبرمجة USB موصولة.")
            return false
        }
        if (!mgr.hasPermission(dev)) {
            onLog(ProberLogDirection.INFO, "طلب صلاحية USB OTG...")
            val ok = requestPermissionSuspend(mgr, dev)
            scanStatus()
            if (!ok) {
                onLog(ProberLogDirection.ERROR, "لم يتم منح صلاحية USB.")
                return false
            }
        }

        closeSession()
        val conn = mgr.openDevice(dev) ?: run {
            onLog(ProberLogDirection.ERROR, "تعذر فتح اتصال USB مع المبرمجة.")
            return false
        }

        val detectedChip = ProberUsbChip.fromDevice(dev.vendorId, dev.productId)
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        var preferredUsbdmIn: UsbEndpoint? = null
        var preferredUsbdmOut: UsbEndpoint? = null
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
                    val epNum = ep.address and 0x0F
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        if (bulkIn == null) bulkIn = ep
                        if (detectedChip == ProberUsbChip.USBDM && epNum == 2) preferredUsbdmIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        if (bulkOut == null) bulkOut = ep
                        if (detectedChip == ProberUsbChip.USBDM && epNum == 1) preferredUsbdmOut = ep
                    }
                }
            }
        }
        if (detectedChip == ProberUsbChip.USBDM) {
            if (preferredUsbdmIn != null) bulkIn = preferredUsbdmIn
            if (preferredUsbdmOut != null) bulkOut = preferredUsbdmOut
        }

        if (bulkIn == null || bulkOut == null) {
            conn.close()
            onLog(ProberLogDirection.ERROR, "الجهاز المتصل لا يحتوي على نقاط Serial/USBDM Bulk صالحة.")
            return false
        }

        synchronized(sessionLock) {
            activeDevice = dev
            activeConnection = conn
            inEndpoint = bulkIn
            outEndpoint = bulkOut
            activeChip = detectedChip
            echoLoopbackObserved = false
        }
        scanStatus()
        startReaderLoop(conn, bulkIn)
        onLog(
            ProberLogDirection.INFO,
            "منفذ USB مفتوح: ${activeChip.displayName} (VID:%04X PID:%04X) | EP_OUT=0x%02X EP_IN=0x%02X".format(
                dev.vendorId, dev.productId, bulkOut.address, bulkIn.address
            )
        )
        return true
    }

    /**
     * Puts lines into Safe Idle (DTR=false, RTS=false) and releases USB handle so technician can
     * safely unplug / swap boards without losing session results.
     */
    fun enterSafeIdleAndClose() {
        try {
            if (activeConnection != null) {
                if (activeChip == ProberUsbChip.USBDM) {
                    // Turn target VDD off gently (USBDM V4.x CMD_USBDM_SET_VDD = 0x02, OFF = 0x00) before releasing USBDM
                    writeBytesRaw(byteArrayOf(0x03, 0x02, 0x00))
                } else {
                    setControlLines(dtr = false, rts = false)
                }
            }
        } catch (_: Throwable) {}
        closeSession()
        onLog(ProberLogDirection.INFO, "تم وضع خطوط المبرمجة في وضع الأمان (Idle) وتحرير USB للفصل الآمن.")
    }

    fun closeSession() {
        val job = readerJob
        readerJob = null
        job?.cancel()
        synchronized(rxQueue) { rxQueue.clear() }
        synchronized(sessionLock) {
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

    fun configureUart(baudRate: Int, evenParity: Boolean = false) {
        currentBaudRate = baudRate
        val conn = activeConnection ?: return
        if (activeChip == ProberUsbChip.USBDM) return
        when (activeChip) {
            ProberUsbChip.USBDM -> return
            ProberUsbChip.FT232R -> {
                conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500) // Reset
                conn.controlTransfer(0x40, 0x00, 0x0001, 0, null, 0, 500) // Purge RX FIFO
                conn.controlTransfer(0x40, 0x00, 0x0002, 0, null, 0, 500) // Purge TX FIFO
                conn.controlTransfer(0x40, 0x09, 0x0002, 0, null, 0, 500) // Latency timer 2ms
                val divisor = when (baudRate) {
                    9600 -> 0x4138
                    19200 -> 0x809C
                    38400 -> 0xC04E
                    57600 -> 0x0034
                    115200 -> 0x001A
                    1_500_000 -> 0x0002
                    else -> (3_000_000 / baudRate.coerceAtLeast(300))
                }
                conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500) // Set baud divisor
                // 8 data bits (0x0008), Parity: None (0x0000) or Even (0x0200)
                conn.controlTransfer(0x40, 0x04, if (evenParity) 0x0208 else 0x0008, 0, null, 0, 500)
                conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 500) // Flow control OFF
            }
            ProberUsbChip.CP2102 -> {
                val idx = controlInterfaceIndex
                conn.controlTransfer(0x41, 0x00, 0x0001, idx, null, 0, 500)
                val baudBytes = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate ushr 8) and 0xFF).toByte(),
                    ((baudRate ushr 16) and 0xFF).toByte(),
                    ((baudRate ushr 24) and 0xFF).toByte()
                )
                val r = conn.controlTransfer(0x41, 0x1E, 0, idx, baudBytes, baudBytes.size, 500)
                if (r < 0) {
                    val div = (3686400 / baudRate.coerceAtLeast(300)) and 0xFFFF
                    conn.controlTransfer(0x41, 0x01, div, idx, null, 0, 500)
                }
                // 8 data bits (0x0800), Parity: None (0x0000) or Even (0x0020)
                conn.controlTransfer(0x41, 0x03, if (evenParity) 0x0820 else 0x0800, idx, null, 0, 500)
                conn.controlTransfer(0x41, 0x12, 0x000F, idx, null, 0, 500)
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
                // LCR: 0xC3 (8N1) or 0xDB (8E1: bit3 PARITY_EN + bit4 EVEN_PARITY)
                conn.controlTransfer(0x40, 0x9A, 0x2518, if (evenParity) 0x00DB else 0x00C3, null, 0, 400)
                conn.controlTransfer(0x40, 0x9A, 0x2727, 0x0000, null, 0, 400)
            }
            else -> {
                val lineCoding = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate ushr 8) and 0xFF).toByte(),
                    ((baudRate ushr 16) and 0xFF).toByte(),
                    ((baudRate ushr 24) and 0xFF).toByte(),
                    0x00,
                    if (evenParity) 0x02 else 0x00,
                    0x08
                )
                conn.controlTransfer(0x21, 0x20, 0, controlInterfaceIndex, lineCoding, lineCoding.size, 400)
                conn.controlTransfer(0x21, 0x22, 0x0003, controlInterfaceIndex, null, 0, 400)
            }
        }
    }

    fun setControlLines(dtr: Boolean, rts: Boolean) {
        currentDtrState = dtr
        currentRtsState = rts
        val conn = activeConnection ?: return
        if (activeChip == ProberUsbChip.USBDM) return
        when (activeChip) {
            ProberUsbChip.USBDM -> return
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

    fun setDtr(enabled: Boolean) {
        currentDtrState = enabled
        val conn = activeConnection ?: return
        if (activeChip == ProberUsbChip.USBDM) return
        when (activeChip) {
            ProberUsbChip.FT232R -> {
                val value = if (enabled) 0x0101 else 0x0100
                conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
            }
            ProberUsbChip.CP2102 -> {
                val value = if (enabled) 0x0101 else 0x0100
                conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
            }
            else -> setControlLines(dtr = currentDtrState, rts = currentRtsState)
        }
    }

    fun setRts(enabled: Boolean) {
        currentRtsState = enabled
        val conn = activeConnection ?: return
        if (activeChip == ProberUsbChip.USBDM) return
        when (activeChip) {
            ProberUsbChip.FT232R -> {
                val value = if (enabled) 0x0202 else 0x0200
                conn.controlTransfer(0x40, 0x01, value, 0, null, 0, 300)
            }
            ProberUsbChip.CP2102 -> {
                val value = if (enabled) 0x0202 else 0x0200
                conn.controlTransfer(0x41, 0x07, value, controlInterfaceIndex, null, 0, 300)
            }
            else -> setControlLines(dtr = currentDtrState, rts = currentRtsState)
        }
    }

    suspend fun pulseResetSequence(
        invertedPolarity: Boolean,
        holdResetMs: Long = 100L,
        settleMs: Long = 150L
    ) {
        val flmd0High = invertedPolarity
        val resetLow = !invertedPolarity
        val resetHigh = invertedPolarity
        setDtr(flmd0High)
        setRts(resetLow)
        delay(holdResetMs)
        setDtr(flmd0High)
        setRts(resetHigh)
        delay(settleMs)
    }

    fun flushRx() {
        synchronized(rxQueue) { rxQueue.clear() }
    }

    fun writeRawBytes(data: ByteArray): String? = writeBytesRaw(data)

    suspend fun drainAvailableRaw(waitMs: Long = 20L): ByteArray {
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + waitMs.coerceAtLeast(1L)
        while (true) {
            currentCoroutineContext().ensureActive()
            synchronized(rxQueue) {
                while (rxQueue.isNotEmpty()) {
                    out.write(rxQueue.removeFirst().toInt() and 0xFF)
                }
            }
            if (System.currentTimeMillis() >= deadline) break
            delay(3)
        }
        return out.toByteArray()
    }

    suspend fun readNecHandshakeOrRaw(
        timeoutMs: Int,
        echoCandidate: ByteArray,
        onRawChunk: (ByteArray) -> Unit
    ): ByteArray {
        val rawAccum = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val chunkOut = ByteArrayOutputStream()
            synchronized(rxQueue) {
                while (rxQueue.isNotEmpty()) {
                    val b = rxQueue.removeFirst().toInt() and 0xFF
                    chunkOut.write(b)
                    rawAccum.write(b)
                }
            }
            val chunk = chunkOut.toByteArray()
            if (chunk.isNotEmpty()) {
                onRawChunk(chunk)
                val full = rawAccum.toByteArray()
                val stripped = stripEchoPrefix(echoCandidate, full)
                if (isCompleteNecResponse(stripped)) {
                    return stripped
                }
            }
            delay(4)
        }
        return stripEchoPrefix(echoCandidate, rawAccum.toByteArray())
    }

    suspend fun transceive(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        label: String,
        stopOnCompleteNecFrame: Boolean = false,
        logTxRx: Boolean = true,
        paced64B: Boolean = false
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        if (logTxRx) {
            val preview = if (txBytes.size > 24) {
                "${txBytes.take(10).toByteArray().toHexStr()} ... (${txBytes.size}B)"
            } else txBytes.toHexStr()
            onLog(ProberLogDirection.TX, "$label -> $preview")
        }

        val err = if (paced64B && txBytes.size > 64) {
            writeBytesChunked64(txBytes)
        } else {
            writeBytesRaw(txBytes)
        }
        if (err != null) {
            onLog(ProberLogDirection.ERROR, "TX Error ($label): $err")
            return ByteArray(0)
        }

        val wireExtraMs = if (echoLoopbackObserved) ((txBytes.size * 11_000L) / currentBaudRate.coerceAtLeast(300)).toInt() else 0
        val rxMax = if (echoLoopbackObserved) txBytes.size + expectedMax else expectedMax
        val raw = readRx(
            expectedMax = rxMax,
            timeoutMs = timeoutMs + wireExtraMs,
            stopOnCompleteNecFrame = stopOnCompleteNecFrame,
            skipEchoPrefixLen = if (echoLoopbackObserved) txBytes.size else 0
        )

        val hadEchoPrefix = txBytes.isNotEmpty() && raw.size >= txBytes.size && raw.copyOfRange(0, txBytes.size).contentEquals(txBytes)
        var clean = stripEchoPrefix(txBytes, raw)
        if (hadEchoPrefix && clean.isEmpty() && expectedMax > 0) {
            // First time diode echo was observed: read genuine MCU response after the stripped echo!
            clean = readRx(
                expectedMax = expectedMax,
                timeoutMs = timeoutMs,
                stopOnCompleteNecFrame = stopOnCompleteNecFrame,
                skipEchoPrefixLen = 0
            )
        }
        if (logTxRx) {
            if (clean.isEmpty()) {
                val echoNote = if (hadEchoPrefix) " [Echo Only: ${raw.toHexStr()}]" else ""
                onLog(ProberLogDirection.RX, "(Timeout ${timeoutMs}ms)$echoNote")
            } else {
                onLog(ProberLogDirection.RX, clean.toHexStr())
            }
        }
        return clean
    }

    suspend fun readFollowUp(expectedMax: Int, timeoutMs: Int, stopOnCompleteNecFrame: Boolean = false): ByteArray {
        return readRx(expectedMax, timeoutMs, stopOnCompleteNecFrame, skipEchoPrefixLen = 0)
    }

    suspend fun usbdmTransceive(
        packet: ByteArray,
        expectedMax: Int = 64,
        timeoutMs: Int = 450,
        label: String = "USBDM",
        logTxRx: Boolean = true
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        flushRx()
        if (logTxRx) {
            onLog(ProberLogDirection.TX, "$label -> ${packet.toHexStr()}")
        }
        val err = writeBytesRaw(packet)
        if (err != null) {
            onLog(ProberLogDirection.ERROR, "USBDM TX Error ($label): $err")
            return ByteArray(0)
        }
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs.toLong()
        var lastByteAt = 0L
        while (out.size() < expectedMax) {
            currentCoroutineContext().ensureActive()
            var took = false
            synchronized(rxQueue) {
                while (rxQueue.isNotEmpty() && out.size() < expectedMax) {
                    out.write(rxQueue.removeFirst().toInt() and 0xFF)
                    took = true
                }
            }
            val now = System.currentTimeMillis()
            if (took) {
                lastByteAt = now
                if (out.size() >= expectedMax) break
            }
            if (now >= deadline) break
            if (lastByteAt > 0L && (now - lastByteAt) >= 12L) break
            delay(2)
        }
        val rx = out.toByteArray()
        if (logTxRx) {
            if (rx.isEmpty()) {
                onLog(ProberLogDirection.RX, "(USBDM Timeout ${timeoutMs}ms)")
            } else {
                onLog(ProberLogDirection.RX, rx.toHexStr())
            }
        }
        return rx
    }

    fun usbdmControlIn(request: Int, value: Int = 0, index: Int = 0, length: Int = 16): ByteArray {
        val conn = activeConnection ?: return ByteArray(0)
        val buf = ByteArray(length)
        val n = conn.controlTransfer(0xC0, request, value, index, buf, length, 400)
        return if (n > 0) buf.copyOf(n) else ByteArray(0)
    }

    fun stripEchoPrefix(txBytes: ByteArray, rx: ByteArray): ByteArray {
        if (txBytes.isNotEmpty() && rx.size >= txBytes.size && rx.copyOfRange(0, txBytes.size).contentEquals(txBytes)) {
            echoLoopbackObserved = true
            return if (rx.size > txBytes.size) rx.copyOfRange(txBytes.size, rx.size) else ByteArray(0)
        }
        return rx
    }

    private fun writeBytesRaw(data: ByteArray): String? {
        val conn = activeConnection ?: return "منفذ USB مغلق"
        val out = outEndpoint ?: return "Bulk OUT غير متاح"
        var offset = 0
        while (offset < data.size) {
            val len = min(data.size - offset, out.maxPacketSize.coerceAtLeast(64))
            val slice = if (offset == 0 && len == data.size) data else data.copyOfRange(offset, offset + len)
            val written = conn.bulkTransfer(out, slice, slice.size, 1500)
            if (written <= 0) return "فشل الإرسال (bulkTransfer=$written)"
            offset += written
        }
        return null
    }

    private suspend fun writeBytesChunked64(data: ByteArray): String? {
        val conn = activeConnection ?: return "منفذ USB مغلق"
        val out = outEndpoint ?: return "Bulk OUT غير متاح"
        var offset = 0
        while (offset < data.size) {
            currentCoroutineContext().ensureActive()
            val len = min(64, data.size - offset)
            val slice = data.copyOfRange(offset, offset + len)
            val written = conn.bulkTransfer(out, slice, slice.size, 1000)
            if (written <= 0) return "فشل الإرسال المقطعي ($written)"
            offset += written
            if (offset < data.size) {
                val delayMs = ((64L * 11_000L) / currentBaudRate.coerceAtLeast(300)).coerceIn(8L, 70L)
                delay(delayMs)
            }
        }
        return null
    }

    private suspend fun readRx(
        expectedMax: Int,
        timeoutMs: Int,
        stopOnCompleteNecFrame: Boolean,
        skipEchoPrefixLen: Int
    ): ByteArray {
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
                if (out.size() > skipEchoPrefixLen) {
                    lastByteAt = now
                }
                if (stopOnCompleteNecFrame) {
                    val bytes = out.toByteArray()
                    val payloadStart = if (bytes.size > skipEchoPrefixLen) skipEchoPrefixLen else 0
                    val sub = if (payloadStart > 0) bytes.copyOfRange(payloadStart, bytes.size) else bytes
                    if (isCompleteNecResponse(sub)) break
                }
                if (out.size() >= expectedMax) break
            }
            if (now >= deadline) break
            if (lastByteAt > 0L && (now - lastByteAt) >= 45L) break
            delay(3)
        }
        return out.toByteArray()
    }

    private fun isCompleteNecResponse(buf: ByteArray): Boolean {
        val stxIdx = buf.indexOfFirst { (it.toInt() and 0xFF) == 0x02 }
        if (stxIdx < 0 || buf.size < stxIdx + 5) return false
        val len = buf[stxIdx + 1].toInt() and 0xFF
        val dataLen = if (len == 0) 256 else len
        val totalFrame = stxIdx + 2 + dataLen + 2
        return buf.size >= totalFrame && (buf[totalFrame - 1].toInt() and 0xFF) == 0x03
    }

    private fun startReaderLoop(conn: UsbDeviceConnection, epIn: UsbEndpoint) {
        readerJob?.cancel()
        readerJob = ioScope.launch {
            val buf = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
            while (isActive && activeConnection === conn) {
                val n = conn.bulkTransfer(epIn, buf, buf.size, 40)
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
                        onLog(ProberLogDirection.WARN, "انفصل كابل USB OTG عن الهاتف.")
                        onUnexpectedDetach()
                        break
                    }
                }
            }
        }
    }

    private fun pickBestSerialDevice(devices: List<UsbDevice>): UsbDevice? {
        if (devices.isEmpty()) return null
        return devices.firstOrNull { ProberUsbChip.fromDevice(it.vendorId, it.productId) == ProberUsbChip.USBDM }
            ?: devices.firstOrNull { ProberUsbChip.fromDevice(it.vendorId, it.productId) != ProberUsbChip.CDC_ACM }
            ?: devices.first()
    }

    private suspend fun requestPermissionSuspend(manager: UsbManager, device: UsbDevice): Boolean {
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERM) {
                        try { appContext.unregisterReceiver(this) } catch (_: Throwable) {}
                        val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_USB_PERM),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val intent = Intent(ACTION_USB_PERM).apply { setPackage(appContext.packageName) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(appContext, 9102, intent, flags)
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

    private fun registerAttachDetachReceiver() {
        if (attachReceiver != null) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> scanStatus()
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (dev != null && dev.deviceId == activeDevice?.deviceId) {
                            onUnexpectedDetach()
                        }
                        scanStatus()
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
            attachReceiver = rx
        } catch (_: Throwable) {}
    }

    fun release() {
        closeSession()
        try { attachReceiver?.let { appContext.unregisterReceiver(it) } } catch (_: Throwable) {}
        attachReceiver = null
    }

    private fun ByteArray.toHexStr(): String = joinToString(" ") { "%02X".format(it) }
}
