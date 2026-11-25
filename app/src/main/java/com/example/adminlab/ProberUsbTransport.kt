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
    private var lastEndpointsSummary: String = "--"
    @Volatile
    private var lastInterfaceInfo: String = "--"
    @Volatile
    var isControlOnlySession: Boolean = false
        private set
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
    private var isBitBangActive: Boolean = false

    @Volatile
    private var readerJob: Job? = null
    private var attachReceiver: BroadcastReceiver? = null

    init {
        registerAttachDetachReceiver()
    }

    fun currentChip(): ProberUsbChip = activeChip
    fun isUsbdmSession(): Boolean = activeChip == ProberUsbChip.USBDM
    fun currentEndpointsSummary(): String = lastEndpointsSummary
    fun currentInterfaceInfo(): String = lastInterfaceInfo
    fun hasDataEndpoints(): Boolean = (inEndpoint != null && outEndpoint != null)

    private data class UsbInspection(
        val chip: ProberUsbChip,
        val endpointsSummary: String,
        val interfaceInfo: String,
        val bulkIn: UsbEndpoint?,
        val bulkOut: UsbEndpoint?,
        val intIn: UsbEndpoint?,
        val intOut: UsbEndpoint?,
        val controlIfaceIdx: Int
    )

    private fun inspectDevice(dev: UsbDevice): UsbInspection {
        var hasCdc = false
        var ctrlIdx = 0
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        var intIn: UsbEndpoint? = null
        var intOut: UsbEndpoint? = null
        val epParts = mutableListOf<String>()
        val ifParts = mutableListOf<String>()

        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM || iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                hasCdc = true
            }
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM || i == 0) {
                ctrlIdx = iface.id
            }
            ifParts.add("IF${iface.id}(cls=0x%02X/sub=0x%02X/p=0x%02X)".format(iface.interfaceClass, iface.interfaceSubclass, iface.interfaceProtocol))
            for (epIdx in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(epIdx)
                val typeStr = when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "INT"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOC"
                    else -> "CTRL"
                }
                val dirStr = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                epParts.add("0x%02X($typeStr/$dirStr/${ep.maxPacketSize}B)".format(ep.address))
            }
        }

        val chip = ProberUsbChip.fromDevice(dev.vendorId, dev.productId, hasCdc)
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            for (epIdx in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(epIdx)
                val epNum = ep.address and 0x0F
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        if (chip == ProberUsbChip.USBDM && epNum == 2) {
                            bulkIn = ep
                        } else if (chip == ProberUsbChip.ST_LINK) {
                            // Prefer EP1 IN (0x81); avoid trace endpoint EP3 IN (0x83)
                            if (epNum == 1 || (bulkIn == null && epNum != 3)) bulkIn = ep
                        } else if (bulkIn == null) {
                            bulkIn = ep
                        }
                    } else {
                        if (chip == ProberUsbChip.USBDM && epNum == 1) {
                            bulkOut = ep
                        } else if (chip == ProberUsbChip.ST_LINK) {
                            if (epNum == 2 || epNum == 1 || bulkOut == null) {
                                if (bulkOut == null || epNum == 1 || epNum == 2) bulkOut = ep
                            }
                        } else if (bulkOut == null) {
                            bulkOut = ep
                        }
                    }
                } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && intIn == null) intIn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT && intOut == null) intOut = ep
                }
            }
        }

        val epSummary = if (epParts.isEmpty()) "EP0 Control Only (No Bulk/Int EPs)" else epParts.joinToString(", ")
        val ifaceSummary = ifParts.joinToString(", ").ifBlank { "No Interfaces" }
        return UsbInspection(chip, epSummary, ifaceSummary, bulkIn, bulkOut, intIn, intOut, ctrlIdx)
    }

    fun scanStatus(): ProberUsbStatus {
        val mgr = usbManager
        val devices = mgr?.deviceList?.values?.toList().orEmpty()
        val chosen = pickBestSerialDevice(devices)
        val status = if (chosen == null) {
            ProberUsbStatus(
                isConnected = false,
                adapterName = "لا توجد مبرمجة USB OTG موصولة",
                chipModel = "غير متصل",
                programmerCategory = "غير متصل",
                vidPidHex = "--",
                endpointsSummary = "--",
                usbInterfaceInfo = "--",
                hasPermission = false,
                deviceCount = 0,
                isUsbdm = false,
                chip = ProberUsbChip.CDC_ACM
            )
        } else {
            val info = inspectDevice(chosen)
            val chip = info.chip
            val perm = mgr?.hasPermission(chosen) == true
            ProberUsbStatus(
                isConnected = true,
                adapterName = chosen.productName?.takeIf { it.isNotBlank() } ?: chip.displayName,
                chipModel = chip.displayName,
                programmerCategory = chip.categoryLabelAr,
                vidPidHex = "VID:%04X PID:%04X".format(chosen.vendorId, chosen.productId),
                endpointsSummary = info.endpointsSummary,
                usbInterfaceInfo = info.interfaceInfo,
                hasPermission = perm,
                deviceCount = devices.size,
                isUsbdm = (chip == ProberUsbChip.USBDM),
                chip = chip
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

        val inspection = inspectDevice(dev)
        val detectedChip = inspection.chip
        controlInterfaceIndex = inspection.controlIfaceIdx
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (conn.claimInterface(iface, true)) {
                claimedInterfaces.add(iface)
            }
        }

        val chosenIn = inspection.bulkIn ?: inspection.intIn
        val chosenOut = inspection.bulkOut ?: inspection.intOut
        val ctrlOnly = (chosenIn == null || chosenOut == null)

        if (ctrlOnly && detectedChip != ProberUsbChip.USBASP && detectedChip != ProberUsbChip.UNKNOWN_USB &&
            detectedChip != ProberUsbChip.ATMEL_AVR && detectedChip != ProberUsbChip.MICROCHIP_PICKIT
        ) {
            conn.close()
            onLog(ProberLogDirection.ERROR, "الجهاز المتصل لا يحتوي على نقاط Bulk/Interrupt/Control صالحة (${inspection.endpointsSummary}).")
            return false
        }

        synchronized(sessionLock) {
            activeDevice = dev
            activeConnection = conn
            inEndpoint = chosenIn
            outEndpoint = chosenOut
            activeChip = detectedChip
            isControlOnlySession = ctrlOnly
            lastEndpointsSummary = inspection.endpointsSummary
            lastInterfaceInfo = inspection.interfaceInfo
            echoLoopbackObserved = false
        }
        scanStatus()
        if (chosenIn != null) {
            startReaderLoop(conn, chosenIn)
        }
        onLog(
            ProberLogDirection.INFO,
            "منفذ USB مفتوح: ${activeChip.displayName} (VID:%04X PID:%04X) | IF=[${inspection.interfaceInfo}] | EP=[${inspection.endpointsSummary}]".format(
                dev.vendorId, dev.productId
            )
        )
        return true
    }

    /**
     * Puts lines into Safe Idle and releases USB handle so technician can
     * safely unplug / swap boards without losing session results.
     */
    fun enterSafeIdleAndClose() {
        try {
            if (activeConnection != null) {
                when (activeChip) {
                    ProberUsbChip.USBDM -> {
                        writeBytesRaw(byteArrayOf(0x03, 0x02, 0x00))
                    }
                    ProberUsbChip.ST_LINK -> {
                        writeBytesRaw(byteArrayOf(0xF2.toByte(), 0x21))
                    }
                    ProberUsbChip.USBASP -> {
                        activeConnection?.controlTransfer(0xC0, 0x02, 0, 0, ByteArray(4), 4, 250)
                    }
                    ProberUsbChip.FT232R, ProberUsbChip.CH340G, ProberUsbChip.CP2102, ProberUsbChip.PL2303, ProberUsbChip.CDC_ACM -> {
                        if (isBitBangActive) exitFtdiBitBangMode()
                        setControlLines(dtr = false, rts = false)
                    }
                    else -> {}
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
            isControlOnlySession = false
        }
    }

    fun configureUart(baudRate: Int, evenParity: Boolean = false) {
        currentBaudRate = baudRate
        val conn = activeConnection ?: return
        if (activeChip.isSpecializedProgrammer || activeChip == ProberUsbChip.UNKNOWN_USB) return
        when (activeChip) {
            ProberUsbChip.USBDM, ProberUsbChip.ST_LINK, ProberUsbChip.USBASP, ProberUsbChip.ATMEL_AVR, ProberUsbChip.MICROCHIP_PICKIT, ProberUsbChip.UNKNOWN_USB -> return
            ProberUsbChip.FT232R -> {
                if (isBitBangActive) exitFtdiBitBangMode()
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
        if (activeChip.isSpecializedProgrammer || activeChip == ProberUsbChip.UNKNOWN_USB) return
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

    fun setDtr(enabled: Boolean) {
        currentDtrState = enabled
        val conn = activeConnection ?: return
        if (activeChip.isSpecializedProgrammer || activeChip == ProberUsbChip.UNKNOWN_USB) return
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
        if (activeChip.isSpecializedProgrammer || activeChip == ProberUsbChip.UNKNOWN_USB) return
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

    // ==================== FT232R BIT-BANG & SYNCHRONOUS SCLK/SDAT ====================

    private fun setFtdiBitBangDirection(dirMask: Int): Boolean {
        val conn = activeConnection ?: return false
        if (activeChip != ProberUsbChip.FT232R) return false
        val value = (0x01 shl 8) or (dirMask and 0xFF)
        val res = conn.controlTransfer(0x40, 0x0B, value, 0, null, 0, 250)
        isBitBangActive = (res >= 0)
        return isBitBangActive
    }

    fun exitFtdiBitBangMode() {
        val conn = activeConnection
        if (conn != null && activeChip == ProberUsbChip.FT232R) {
            try {
                conn.controlTransfer(0x40, 0x0B, 0x0000, 0, null, 0, 250)
            } catch (_: Throwable) {}
        }
        isBitBangActive = false
    }

    private fun readFtdiInstantPins(): Int {
        val conn = activeConnection ?: return -1
        if (activeChip != ProberUsbChip.FT232R) return -1
        val buf = ByteArray(1)
        val n = conn.controlTransfer(0xC0, 0x0C, 0, 0, buf, 1, 200)
        return if (n >= 1) (buf[0].toInt() and 0xFF) else -1
    }

    /**
     * Samsung S3F8 / S3F9 Serial Programming Mode Entry:
     * 1) Hold RESET = LOW (0V) + TEST/VPP active, emit 16 initial SCLK pulses.
     * 2) Raise RESET = HIGH (+5V) while maintaining programming mode.
     */
    suspend fun runSamsungBitBangResetEntry(
        sclkOnDtr: Boolean = true,
        resetActiveLow: Boolean = true,
        initialClocks: Int = 16
    ): Boolean {
        currentCoroutineContext().ensureActive()
        val conn = activeConnection ?: return false
        if (activeChip == ProberUsbChip.FT232R) {
            if (!isBitBangActive) {
                configureUart(38400, evenParity = false)
            }
            val txdMask = 0x01 // SDAT out (idle HIGH via isolation diode)
            val sclkMask = if (sclkOnDtr) 0x10 else 0x04
            val resetMask = if (sclkOnDtr) 0x04 else 0x10
            val dirMask = txdMask or sclkMask or resetMask
            if (!setFtdiBitBangDirection(dirMask)) return false

            val resetLowState = if (resetActiveLow) 0x00 else resetMask
            val resetHighState = if (resetActiveLow) resetMask else 0x00

            val seq = ByteArrayOutputStream()
            // Step 1: RESET = LOW (0V), SDAT/TEST HIGH, emit 16 initial SCLK pulses
            seq.write(resetLowState or txdMask)
            for (i in 0 until initialClocks) {
                seq.write(resetLowState or txdMask or sclkMask)
                seq.write(resetLowState or txdMask)
            }
            writeBytesRaw(seq.toByteArray())
            delay(25)

            // Step 2: Raise RESET = HIGH (+5V) while keeping TEST/programming mode steady
            writeBytesRaw(byteArrayOf((resetHighState or txdMask).toByte()))
            delay(20)
            flushRx()
            return true
        }

        // Non-FT232R fallback (CH340G / CP2102 / PL2303) using control lines
        val resetLow = resetActiveLow
        val resetHigh = !resetActiveLow
        if (sclkOnDtr) {
            setControlLines(dtr = false, rts = resetLow)
        } else {
            setControlLines(dtr = resetLow, rts = false)
        }
        delay(25)
        for (i in 0 until initialClocks) {
            currentCoroutineContext().ensureActive()
            if (sclkOnDtr) setDtr(true) else setRts(true)
            delay(1)
            if (sclkOnDtr) setDtr(false) else setRts(false)
            delay(1)
        }
        if (sclkOnDtr) {
            setControlLines(dtr = false, rts = resetHigh)
        } else {
            setControlLines(dtr = resetHigh, rts = false)
        }
        delay(20)
        flushRx()
        return true
    }

    /**
     * Synchronous Clocked Bit-by-Bit Transceiver (FT232R Bit-Bang SCLK/SDAT + non-FT232 fallback):
     * Clocks txBytes MSB-first on TXD (SDAT) with SCLK edges, then switches TXD to High-Z input
     * and samples rxLen bytes bit-by-bit from RXD (SDAT) on clock edges via SIO_READ_PINS (0x0C).
     */
    suspend fun syncBitBangTransceive(
        txBytes: ByteArray,
        rxLen: Int,
        sclkOnDtr: Boolean = true,
        resetHighState: Boolean = true,
        clkHalfPeriodMs: Long = 1L,
        sampleOnFallingEdge: Boolean = false
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        if (activeConnection == null) return ByteArray(0)
        if (activeChip == ProberUsbChip.FT232R) {
            val txdMask = 0x01
            val rxdMask = 0x02
            val sclkMask = if (sclkOnDtr) 0x10 else 0x04
            val resetMask = if (sclkOnDtr) 0x04 else 0x10
            val baseResetBit = if (resetHighState) resetMask else 0x00

            if (txBytes.isNotEmpty()) {
                val txDirMask = txdMask or sclkMask or resetMask
                if (!setFtdiBitBangDirection(txDirMask)) return ByteArray(0)
                val txWave = ByteArrayOutputStream()
                for (b in txBytes) {
                    val v = b.toInt() and 0xFF
                    for (bit in 7 downTo 0) {
                        val dBit = if (((v ushr bit) and 0x01) != 0) txdMask else 0x00
                        txWave.write(baseResetBit or dBit)
                        txWave.write(baseResetBit or dBit or sclkMask)
                        txWave.write(baseResetBit or dBit)
                    }
                }
                // Release SDAT idle HIGH (isolation diode safe) with SCLK LOW
                txWave.write(baseResetBit or txdMask)
                val err = writeBytesRaw(txWave.toByteArray())
                if (err != null) return ByteArray(0)
                delay((txBytes.size * 2L).coerceIn(2L, 12L))
            }

            if (rxLen <= 0) return ByteArray(0)

            // Switch TXD (bit 0) to input so MCU can drive SDAT (RXD bit 1) cleanly without contention
            val rxDirMask = sclkMask or resetMask
            if (!setFtdiBitBangDirection(rxDirMask)) return ByteArray(0)

            val rxOut = ByteArray(rxLen)
            for (byteIdx in 0 until rxLen) {
                currentCoroutineContext().ensureActive()
                var acc = 0
                for (bit in 7 downTo 0) {
                    if (!sampleOnFallingEdge) {
                        writeBytesRaw(byteArrayOf((baseResetBit or sclkMask).toByte()))
                        if (clkHalfPeriodMs > 0) delay(clkHalfPeriodMs)
                        val pins = readFtdiInstantPins()
                        val bitVal = if (pins >= 0 && (pins and rxdMask) != 0) 1 else 0
                        acc = (acc shl 1) or bitVal
                        writeBytesRaw(byteArrayOf(baseResetBit.toByte()))
                    } else {
                        writeBytesRaw(byteArrayOf((baseResetBit or sclkMask).toByte(), baseResetBit.toByte()))
                        if (clkHalfPeriodMs > 0) delay(clkHalfPeriodMs)
                        val pins = readFtdiInstantPins()
                        val bitVal = if (pins >= 0 && (pins and rxdMask) != 0) 1 else 0
                        acc = (acc shl 1) or bitVal
                    }
                }
                rxOut[byteIdx] = acc.toByte()
            }
            return rxOut
        }

        // Non-FT232R fallback
        flushRx()
        if (txBytes.isNotEmpty()) {
            writeBytesRaw(txBytes)
            for (i in 0 until (txBytes.size * 8)) {
                currentCoroutineContext().ensureActive()
                if (sclkOnDtr) setDtr(true) else setRts(true)
                delay(1)
                if (sclkOnDtr) setDtr(false) else setRts(false)
                delay(1)
            }
        }
        return if (rxLen > 0) drainAvailableRaw(waitMs = 60L) else ByteArray(0)
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

    suspend fun usbControlTransferIn(
        requestType: Int = 0xC0,
        request: Int,
        value: Int = 0,
        index: Int = 0,
        length: Int = 8,
        timeoutMs: Int = 450,
        label: String = "CTRL IN",
        logTxRx: Boolean = true
    ): ByteArray {
        currentCoroutineContext().ensureActive()
        val conn = activeConnection ?: return ByteArray(0)
        if (logTxRx) {
            onLog(ProberLogDirection.TX, "$label [reqType=0x%02X req=0x%02X val=0x%04X idx=0x%04X len=%d]".format(requestType, request, value, index, length))
        }
        val buf = ByteArray(length.coerceAtLeast(1))
        val n = conn.controlTransfer(requestType, request, value, index, buf, buf.size, timeoutMs)
        val rx = if (n > 0) buf.copyOf(n) else ByteArray(0)
        if (logTxRx) {
            if (rx.isEmpty()) {
                onLog(ProberLogDirection.RX, "(CTRL Timeout/Empty n=$n)")
            } else {
                onLog(ProberLogDirection.RX, rx.toHexStr())
            }
        }
        return rx
    }

    suspend fun usbControlTransferOut(
        requestType: Int = 0x40,
        request: Int,
        value: Int = 0,
        index: Int = 0,
        data: ByteArray? = null,
        timeoutMs: Int = 450,
        label: String = "CTRL OUT",
        logTxRx: Boolean = true
    ): Int {
        currentCoroutineContext().ensureActive()
        val conn = activeConnection ?: return -1
        if (logTxRx) {
            onLog(ProberLogDirection.TX, "$label [reqType=0x%02X req=0x%02X val=0x%04X idx=0x%04X] ${data?.toHexStr().orEmpty()}".trim())
        }
        return conn.controlTransfer(requestType, request, value, index, data, data?.size ?: 0, timeoutMs)
    }

    suspend fun rawPacketTransceive(
        packet: ByteArray,
        expectedMax: Int = 64,
        timeoutMs: Int = 450,
        label: String = "USB RAW",
        logTxRx: Boolean = true
    ): ByteArray = usbdmTransceive(packet, expectedMax, timeoutMs, label, logTxRx)

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
        return devices.firstOrNull { inspectDevice(it).chip == ProberUsbChip.USBDM }
            ?: devices.firstOrNull { inspectDevice(it).chip.isSpecializedProgrammer }
            ?: devices.firstOrNull {
                val c = inspectDevice(it).chip
                c != ProberUsbChip.CDC_ACM && c != ProberUsbChip.UNKNOWN_USB
            }
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
