package com.example.util

import com.example.data.model.McuProcessor
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 100% Automatic Hardware Boot Entry + Single-Wire UART Mode 3 (Diode Echo Filter)
 * combined with the official Renesas R8C Serial Programming Protocol
 * (r8c_protocol.hpp / r8c_prog.hpp by Kunihito Hiramatsu).
 *
 * Physical Circuit:
 * - DTR -> RESET (direct: DTR=true pulls RESET LOW, DTR=false releases RESET HIGH)
 * - RTS -> MODE/RX via diode (cathode on RTS, anode on RX/MODE: RTS=true pulls MODE LOW)
 * - TX  -> MODE/RX via diode (cathode on TX, anode on RX/MODE)
 * - RX  -> MODE (direct: sees every N TX bytes echoed before M MCU reply bytes)
 */
object R8cProtocolEngine {

    val DEFAULT_ID_CODE: ByteArray = ByteArray(7) { 0xFF.toByte() }

    const val LOCKED_CHIP_MESSAGE =
        "الشريحة مقفلة بمفتاح ID خاص؛ أدخل المفتاح الصحيح للقراءة، أو اضغط مسح (Erase) لتهيئتها للبرمجة"

    data class R8cStatus(
        val srd: Int,
        val srd1: Int
    ) {
        val idState: Int get() = (srd1 ushr 2) and 0x03
        val sr4ProgramError: Boolean get() = ((srd ushr 4) and 0x01) != 0
        val sr5EraseError: Boolean get() = ((srd ushr 5) and 0x01) != 0
        fun isIdVerified(): Boolean = idState == 3
    }

    data class R8cArea(
        val org: Int,
        val end: Int
    ) {
        val sizeBytes: Int get() = (end - org + 1).coerceAtLeast(0)
    }

    data class R8cDeviceLayout(
        val romArea: R8cArea,
        val dataAreas: List<R8cArea>
    )

    data class R8cExchangeResult(
        val txError: String? = null,
        val totalRawReceived: Int = 0,
        val echoedCount: Int = 0,
        val replyBytes: ByteArray = ByteArray(0),
        val rawBytes: ByteArray = ByteArray(0)
    )

    interface SerialTransport {
        fun configureUart(baudRate: Int, onLog: (String) -> Unit): Boolean
        fun ensureReaderListening(onLog: (String) -> Unit): Boolean
        fun setRawRxMonitor(enabled: Boolean)
        fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit)
        fun setDtr(enabled: Boolean, onLog: (String) -> Unit)
        fun setRts(enabled: Boolean, onLog: (String) -> Unit)
        fun flushInputBuffer()
        suspend fun writeRaw(bytes: ByteArray): String?
        suspend fun sendAndCollectRawWindow(
            txBytes: ByteArray,
            minExpectedToShortCircuit: Int,
            timeoutMs: Int,
            onLog: (String) -> Unit
        ): R8cExchangeResult
        suspend fun sendAndReceiveR8C(
            txBytes: ByteArray,
            expectedRxLength: Int,
            timeoutMs: Int = 500,
            onLog: (String) -> Unit
        ): R8cExchangeResult
    }

    fun resolveDeviceLayout(processor: McuProcessor): R8cDeviceLayout {
        val part = processor.partNumber.trim().uppercase()
        val sub = processor.subFamily.trim().uppercase()
        val flashBytes = processor.flashSizeBytes

        return when {
            part.startsWith("R5F21262") -> R8cDeviceLayout(
                romArea = R8cArea(0xE000, 0xFFFF),
                dataAreas = emptyList()
            )
            part.startsWith("R5F21294") -> R8cDeviceLayout(
                romArea = R8cArea(0xC000, 0xFFFF),
                dataAreas = listOf(R8cArea(0x2400, 0x2BFF))
            )
            part.startsWith("R5F21354") -> R8cDeviceLayout(
                romArea = R8cArea(0xC000, 0xFFFF),
                dataAreas = listOf(R8cArea(0x3000, 0x3FFF))
            )
            part.startsWith("R5F21246") || part.startsWith("R5F21266") -> R8cDeviceLayout(
                romArea = R8cArea(0x8000, 0xFFFF),
                dataAreas = emptyList()
            )
            part.startsWith("R5F21256") || part.startsWith("R5F21276") -> R8cDeviceLayout(
                romArea = R8cArea(0x8000, 0xFFFF),
                dataAreas = listOf(R8cArea(0x2400, 0x2BFF))
            )
            part.startsWith("R5F212AA") -> R8cDeviceLayout(
                romArea = R8cArea(0x4000, 0x1BFFF),
                dataAreas = emptyList()
            )
            part.startsWith("R5F212BA") -> R8cDeviceLayout(
                romArea = R8cArea(0x4000, 0x1BFFF),
                dataAreas = listOf(R8cArea(0x2400, 0x2BFF))
            )
            part.startsWith("R5F212DC") -> R8cDeviceLayout(
                romArea = R8cArea(0x4000, 0x23FFF),
                dataAreas = listOf(R8cArea(0x2400, 0x2BFF))
            )
            part.startsWith("R5F2M110") || part.startsWith("R5F2M120") -> R8cDeviceLayout(
                romArea = R8cArea(0x8000, 0x8000 + flashBytes - 1),
                dataAreas = listOf(R8cArea(0x3000, 0x37FF))
            )
            else -> {
                val romStart = when {
                    sub.contains("M11") || sub.contains("M12") -> {
                        if (flashBytes >= 32 * 1024) 0x8000 else (0x10000 - flashBytes).coerceAtLeast(0x4000)
                    }
                    flashBytes <= 32 * 1024 -> (0x10000 - flashBytes).coerceAtLeast(0x4000)
                    else -> 0x4000
                }
                val dataAreas = if (sub.contains("R8C/25") || sub.contains("R8C/27") ||
                    sub.contains("R8C/29") || sub.contains("R8C/2B") || sub.contains("R8C/2D")
                ) {
                    listOf(R8cArea(0x2400, 0x2BFF))
                } else {
                    emptyList()
                }
                R8cDeviceLayout(
                    romArea = R8cArea(romStart, romStart + flashBytes - 1),
                    dataAreas = dataAreas
                )
            }
        }
    }

    /**
     * Smart Echo-Stripping Exchange Helper:
     * Sends `txBytes` (length N), collects RX bytes into rxQueue, and if the leading bytes
     * match `txBytes` (diode echo), removes them automatically and returns the remaining MCU reply.
     */
    suspend fun sendAndReceiveR8C(
        transport: SerialTransport,
        txBytes: ByteArray,
        expectedRxLength: Int,
        timeoutMs: Int = 500,
        onLog: (String) -> Unit
    ): R8cExchangeResult {
        return transport.sendAndReceiveR8C(
            txBytes = txBytes,
            expectedRxLength = expectedRxLength,
            timeoutMs = timeoutMs,
            onLog = onLog
        )
    }

    /**
     * 1. Exact Windows Renesas R8C Tool (Table View) Hardware Boot Entry Sequence:
     *    - Open port on 9600 bps (8N1) & attach continuous RX listener (rxQueue + Raw RX Monitor)
     *    - Step 1 (SET_RTS & SET_DTR): setRTS(true) then setDTR(true) (MODE=LOW & RESET=LOW) -> wait 200ms
     *    - Step 2 (CLR_DTR):           setDTR(false) (RESET=HIGH while RTS=true holds MODE=LOW) -> wait 100ms
     *    - Step 3 (CLR_RTS & PURGE):   setRTS(false) (release MODE/RX) -> wait 50ms -> rxQueue.clear()
     */
    suspend fun startAndConnect(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("1) فتح المنفذ على سرعة 9600 bps (8N1)...")
        if (!transport.configureUart(9600, onLog)) {
            return false
        }

        onLog("2) ربط مستمع الاستقبال (RX Listener -> rxQueue + Raw RX Monitor)...")
        transport.setRawRxMonitor(true)
        if (!transport.ensureReaderListening(onLog)) {
            return false
        }

        onLog("الخطوة 1 (SET_RTS & SET_DTR): setRTS(true) ثم setDTR(true) [MODE=LOW و RESET=LOW] -> انتظار 200ms...")
        transport.setRts(enabled = true, onLog = onLog)
        transport.setDtr(enabled = true, onLog = onLog)
        delay(200)

        onLog("الخطوة 2 (CLR_DTR): setDTR(false) [RESET=HIGH أثناء بقاء RTS=true يمسك MODE=LOW] -> انتظار 100ms...")
        transport.setDtr(enabled = false, onLog = onLog)
        delay(100)

        onLog("الخطوة 3 (CLR_RTS & PURGE): setRTS(false) [تحرير MODE/RX] -> انتظار 50ms -> rxQueue.clear()...")
        transport.setRts(enabled = false, onLog = onLog)
        delay(50)
        transport.flushInputBuffer()

        return establishConnection(transport, onLog)
    }

    /**
     * 2. Sync & Baud Lock (9600 bps) from Windows Table View log:
     *    - Send 0x00 x 16 with 20ms interval
     *    - Wait 40ms then rxQueue.clear()
     *    - Send [0xB0] (N=1), expect [0xB0, 0xB0] (or [0xB0] if no echo)
     */
    suspend fun establishConnection(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("TX: 00 x16 (فاصل 20ms بين كل بايت ثم انتظار 40ms وتفريغ rxQueue)")
        val zero = byteArrayOf(0x00)
        for (i in 0 until 16) {
            val err = transport.writeRaw(zero)
            if (err != null) {
                onLog("TX ERROR (connection 0x00 #${i + 1}): $err")
                return false
            }
            delay(20)
        }

        delay(40)
        transport.flushInputBuffer()
        onLog("rxQueue.clear() بعد 40ms — إرسال 0xB0 الأولى (1000ms)...")

        onLog("TX: B0")
        val res = transport.sendAndCollectRawWindow(
            txBytes = byteArrayOf(0xB0.toByte()),
            minExpectedToShortCircuit = 2,
            timeoutMs = 1000,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xB0): ${res.txError}")
            return false
        }

        val raw = res.rawBytes
        onLog("RX Raw: [${toHexComma(raw)}]")
        if (raw.isEmpty()) {
            onLog("connection() Failed: لا توجد أي بايتات مستلمة (RX Raw: []) خلال 1000ms.")
            return false
        }

        val hasB0 = raw.any { (it.toInt() and 0xFF) == 0xB0 }
        if (!hasB0) {
            onLog("connection() Error: البايتات الخام [${toHexComma(raw)}] لا تحتوي على 0xB0.")
            return false
        }

        val finalAck = raw.last().toInt() and 0xFF
        onLog("Connection OK (0x%02X — استُلم ${raw.size} بايت خام). إرسال 0xB0 الثانية (change_speed)...".format(finalAck))
        return true
    }

    /**
     * 3b. change_speed():
     *     - Send [0xB0] a second time for 9600 bps (or 0xB1..0xB4 for higher rates)
     *     - Collect Raw RX during 1000ms, log RX Raw: [...], accept if contains target cmd byte
     */
    suspend fun changeSpeed(
        transport: SerialTransport,
        targetBaudRate: Int,
        onLog: (String) -> Unit
    ): Boolean {
        val cmd: Int = when (targetBaudRate) {
            9600 -> 0xB0
            19200 -> 0xB1
            38400 -> 0xB2
            57600 -> 0xB3
            115200 -> 0xB4
            else -> {
                onLog("change_speed Error: السرعة $targetBaudRate غير مدعومة (9600..115200).")
                return false
            }
        }

        val tx = byteArrayOf(cmd.toByte())
        onLog("TX: \u2066${toHex(tx)}\u2069 (change_speed $targetBaudRate bps)")
        val res = transport.sendAndCollectRawWindow(
            txBytes = tx,
            minExpectedToShortCircuit = 2,
            timeoutMs = 1000,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (change_speed): ${res.txError}")
            return false
        }

        val raw = res.rawBytes
        onLog("RX Raw: [${toHexComma(raw)}]")
        if (!raw.any { (it.toInt() and 0xFF) == cmd }) {
            onLog("change_speed Error: لم يحتوي RX Raw على 0x%02X خلال 1000ms.".format(cmd))
            return false
        }

        if (targetBaudRate != 9600) {
            if (!transport.configureUart(targetBaudRate, onLog)) {
                return false
            }
            transport.setControlLines(dtr = false, rts = false, onLog = onLog)
            delay(15)
            transport.flushInputBuffer()
        }
        onLog("change_speed() OK: $targetBaudRate bps.")
        return true
    }

    /**
     * 3c. get_version():
     *     - Send [0xFB], print RX Raw: [...], strip leading echo [0xFB] if present, decode 8 ASCII chars
     */
    suspend fun getVersion(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): String? {
        onLog("TX: FB (get_version)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0xFB.toByte()),
            expectedRxLength = 8,
            timeoutMs = 700,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xFB): ${res.txError}")
            return null
        }
        onLog("RX Raw (0xFB): [${toHexComma(res.rawBytes)}]")
        val reply = res.replyBytes
        if (res.echoedCount > 0) {
            onLog("تصفية الصدى (0xFB): حذف ${res.echoedCount} بايت صدى ➔ [${toHexComma(reply)}]")
        }
        if (reply.size < 8) {
            onLog("get_version Error: استُلم ${reply.size}/8 بايت حقيقي بعد التصفية.")
            return null
        }
        val ascii = reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("").trim()
        onLog("R8C Bootloader Version: \"$ascii\" (\u2066${toHex(reply)}\u2069)")
        return ascii.ifBlank { toHex(reply) }
    }

    /**
     * get_status():
     * - Send [0x70], auto-strip echo [0x70] if present, read [SRD, SRD1]
     */
    suspend fun getStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = false
    ): R8cStatus? {
        if (!quiet) onLog("TX: 70 (get_status)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0x70),
            expectedRxLength = 2,
            timeoutMs = 600,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0x70): ${res.txError}")
            return null
        }
        if (!quiet) {
            onLog("RX Raw (0x70): [${toHexComma(res.rawBytes)}]")
        }
        val reply = res.replyBytes
        if (reply.size < 2) {
            onLog("get_status Error: استُلم ${reply.size}/2 بايت حقيقي [Raw=${toHexComma(res.rawBytes)}]")
            return null
        }
        val st = R8cStatus(
            srd = reply[0].toInt() and 0xFF,
            srd1 = reply[1].toInt() and 0xFF
        )
        if (!quiet) {
            onLog("RX Status: [${toHexComma(reply)}] -> SRD=0x%02X, SRD1=0x%02X".format(st.srd, st.srd1))
        }
        return st
    }

    /**
     * clear_status(): Send [0x50] and auto-strip echo if present
     */
    suspend fun clearStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = true
    ): Boolean {
        if (!quiet) onLog("TX: 50 (clear_status)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0x50),
            expectedRxLength = 0,
            timeoutMs = 400,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0x50): ${res.txError}")
            return false
        }
        return true
    }

    /**
     * 3d. id_inspection():
     *     - Send [0xF5, 0xDF, 0xFF, 0x00, 0x07, id0..id6], strip echo if received
     *     - Send [0x70] and check ((SRD1 >> 2) & 3) == 3
     */
    suspend fun idInspection(
        transport: SerialTransport,
        idBytes: ByteArray,
        onLog: (String) -> Unit
    ): Pair<Boolean, R8cStatus?> {
        val effectiveId = if (idBytes.size == 7) idBytes else DEFAULT_ID_CODE.copyOf()
        val frame = ByteArray(12).apply {
            this[0] = 0xF5.toByte()
            this[1] = 0xDF.toByte()
            this[2] = 0xFF.toByte()
            this[3] = 0x00.toByte()
            this[4] = 0x07.toByte()
            for (i in 0 until 7) {
                this[5 + i] = effectiveId[i]
            }
        }

        onLog("TX (12B): \u2066${toHex(frame)}\u2069 (id_inspection 0xF5)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = frame,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xF5): ${res.txError}")
            return false to null
        }
        if (res.rawBytes.isNotEmpty()) {
            onLog("RX Raw (0xF5): [${toHexComma(res.rawBytes)}] (صدى=${res.echoedCount}B)")
        }

        val st = getStatus(transport, onLog, quiet = false) ?: return false to null
        onLog("id_state = ((0x%02X >> 2) & 3) = %d".format(st.srd1, st.idState))
        if (!st.isIdVerified()) {
            onLog("$LOCKED_CHIP_MESSAGE [id_state=${st.idState}]")
            return false to st
        }
        onLog("id_inspection OK: تم فتح القفل بنجاح (id_state == 3).")
        return true to st
    }

    /**
     * 3e. read_page():
     *     - Send [0xFF, (address >> 8) & 0xFF, (address >> 16) & 0xFF]
     *     - Auto-strip leading echo if present and return 256 genuine flash bytes
     */
    suspend fun readPage(
        transport: SerialTransport,
        address: Int,
        onLog: (String) -> Unit,
        verboseFrameLog: Boolean = false
    ): ByteArray? {
        val cmd = byteArrayOf(
            0xFF.toByte(),
            ((address ushr 8) and 0xFF).toByte(),
            ((address ushr 16) and 0xFF).toByte()
        )
        if (verboseFrameLog) {
            onLog("TX: \u2066${toHex(cmd)}\u2069 (read_page 0x%05X)".format(address))
        }
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = cmd,
            expectedRxLength = 256,
            timeoutMs = 1000,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (read_page 0x%05X): ${res.txError}".format(address))
            return null
        }
        if (verboseFrameLog && res.rawBytes.isNotEmpty()) {
            val preview = res.rawBytes.take(16).toByteArray()
            onLog("RX Raw (0xFF @ 0x%05X, ${res.rawBytes.size}B, صدى=${res.echoedCount}B): [${toHexComma(preview)} ...]".format(address))
        }
        val page = res.replyBytes
        if (page.size < 256) {
            onLog("RX Error (read_page 0x%05X): استُلم ${page.size}/256 بايت حقيقي (الخام=${res.totalRawReceived}).".format(address))
            return null
        }
        if (verboseFrameLog) {
            onLog("RX Flash (256B @ 0x%05X): \u2066${toHex(page.take(16).toByteArray())}\u2069 ...".format(address))
        }
        return page
    }

    /**
     * erase_page():
     * - Send [0x20, (address >> 8) & 0xFF, (address >> 16) & 0xFF, 0xD0] (N=4, M=0)
     * - Poll get_status() (0x70, N=1, M=2), verify ((SRD >> 5) & 1) == 0, then send 0x50 (clear_status)
     */
    suspend fun erasePage(
        transport: SerialTransport,
        address: Int,
        onLog: (String) -> Unit
    ): Boolean {
        val cmd = byteArrayOf(
            0x20.toByte(),
            ((address ushr 8) and 0xFF).toByte(),
            ((address ushr 16) and 0xFF).toByte(),
            0xD0.toByte()
        )
        onLog("TX: \u2066${toHex(cmd)}\u2069 (erase_page 0x%05X: حذف 4 بايت صدى)".format(address))
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = cmd,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (erase_page 0x%05X): ${res.txError}".format(address))
            return false
        }

        delay(45)
        var st: R8cStatus? = null
        for (attempt in 1..15) {
            val cur = getStatus(transport, onLog, quiet = attempt > 1)
            if (cur != null) {
                st = cur
                if ((cur.srd and 0x80) != 0) break
            }
            delay(35)
        }
        val finalSt = st ?: return false
        if (finalSt.sr5EraseError) {
            onLog("erase_page Error @ 0x%05X: ((SRD >> 5) & 1) != 0 [SRD=0x%02X]".format(address, finalSt.srd))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    /**
     * write_page():
     * - Send [0x41, (address >> 8) & 0xFF, (address >> 16) & 0xFF, ...256 bytes] (N=259, M=0)
     * - Poll get_status() (0x70, N=1, M=2), verify ((SRD >> 4) & 1) == 0, then send 0x50 (clear_status)
     */
    suspend fun writePage(
        transport: SerialTransport,
        address: Int,
        data256: ByteArray,
        onLog: (String) -> Unit,
        verboseFrameLog: Boolean = false
    ): Boolean {
        val frame = ByteArray(259).apply {
            this[0] = 0x41.toByte()
            this[1] = ((address ushr 8) and 0xFF).toByte()
            this[2] = ((address ushr 16) and 0xFF).toByte()
            for (i in 0 until 256) {
                this[3 + i] = if (i < data256.size) data256[i] else 0xFF.toByte()
            }
        }
        if (verboseFrameLog) {
            onLog("TX: \u2066%02X %02X %02X\u2069 + 256B (write_page 0x%05X: حذف 259 بايت صدى)".format(
                frame[0].toInt() and 0xFF,
                frame[1].toInt() and 0xFF,
                frame[2].toInt() and 0xFF,
                address
            ))
        }
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = frame,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (write_page 0x%05X): ${res.txError}".format(address))
            return false
        }

        delay(18)
        var st: R8cStatus? = null
        for (attempt in 1..10) {
            val cur = getStatus(transport, onLog, quiet = !verboseFrameLog || attempt > 1)
            if (cur != null) {
                st = cur
                if ((cur.srd and 0x80) != 0) break
            }
            delay(20)
        }
        val finalSt = st ?: return false
        if (finalSt.sr4ProgramError) {
            onLog("write_page Error @ 0x%05X: ((SRD >> 4) & 1) != 0 [SRD=0x%02X]".format(address, finalSt.srd))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    suspend fun readMemory(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        idBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val layout = resolveDeviceLayout(processor)
        val totalSize = layout.romArea.sizeBytes
        val startAddr = layout.romArea.org

        onProgress(0, totalSize, "تهيئة البوت التلقائي (RTS/DTR) والمزامنة...")
        if (!startAndConnect(transport, onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C (16×0x00 + 0xB0). راجع سجل TX/RX.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تثبيت/تغيير سرعة الباود (change_speed $baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لأمر قراءة الإصدار 0xFB.")
        }
        onLog("R8C Version: \"$ver\" | نطاق القراءة: 0x%05X..0x%05X".format(layout.romArea.org, layout.romArea.end))

        onProgress(0, totalSize, "التحقق من مفتاح الحماية 7-Byte ID (0xF5)...")
        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        val resultBuffer = FirmwareParser.createEmptyBuffer(totalSize)
        val totalPages = (totalSize + 255) / 256
        var bytesRead = 0

        try {
            for (pageIdx in 0 until totalPages) {
                transport.setRawRxMonitor(pageIdx == 0)
                val physicalAddr = (startAddr + pageIdx * 256) and 0xFFFFFF00.toInt()
                val chunk = readPage(
                    transport = transport,
                    address = physicalAddr,
                    onLog = onLog,
                    verboseFrameLog = (pageIdx == 0 || pageIdx == totalPages - 1)
                ) ?: return FlashOperationResult.Failure("فشلت قراءة الصفحة عند العنوان 0x%06X.".format(physicalAddr))

                val dstOffset = pageIdx * 256
                val copyLen = min(256, totalSize - dstOffset)
                System.arraycopy(chunk, 0, resultBuffer, dstOffset, copyLen)
                bytesRead += copyLen

                val pct = ((bytesRead * 100L) / totalSize.coerceAtLeast(1)).toInt()
                onProgress(
                    bytesRead,
                    totalSize,
                    "قراءة ${processor.partNumber}: $pct% (0x%05X)".format(physicalAddr)
                )
            }
        } finally {
            transport.setRawRxMonitor(false)
        }

        return FlashOperationResult.Success(
            message = "تمت قراءة ${bytesRead / 1024} KB من ${processor.partNumber} (0x%05X..0x%05X) بنجاح."
                .format(layout.romArea.org, layout.romArea.end),
            bytesTransferred = resultBuffer,
            baseAddress = startAddr
        )
    }

    suspend fun eraseFlash(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        idBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val layout = resolveDeviceLayout(processor)
        val totalSpan = layout.romArea.sizeBytes.coerceAtLeast(1024)

        onProgress(0, totalSpan, "تهيئة البوت التلقائي (RTS/DTR) قبل المسح...")
        if (!startAndConnect(transport, onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل المسح.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تثبيت/تغيير سرعة الباود (change_speed $baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver != null) {
            onLog("R8C Version: \"$ver\"")
        }
        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        transport.setRawRxMonitor(false)
        val blocks = buildEraseBlocks(layout.romArea.org, layout.romArea.end)
        for ((idx, blockAddr) in blocks.withIndex()) {
            onProgress(
                ((idx.toFloat() / blocks.size.coerceAtLeast(1)) * totalSpan).toInt(),
                totalSpan,
                "مسح بلوك ${idx + 1}/${blocks.size} (0x%05X)...".format(blockAddr)
            )
            if (!erasePage(transport, blockAddr, onLog)) {
                return FlashOperationResult.Failure("فشل مسح البلوك عند العنوان 0x%05X.".format(blockAddr))
            }
        }

        onProgress(totalSpan, totalSpan, "اكتمل مسح جميع البلوكات")
        return FlashOperationResult.Success(
            "تم مسح ذاكرة ${processor.partNumber} (${blocks.size} بلوك عبر erase_page 0x20..0xD0) بنجاح."
        )
    }

    suspend fun programOrVerifyFlash(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        idBytes: ByteArray,
        verifyOnly: Boolean,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val layout = resolveDeviceLayout(processor)
        val baseAddr = if (firmwareStartAddress > 0) firmwareStartAddress else layout.romArea.org
        val totalSize = firmwareBytes.size

        onProgress(0, totalSize, "تهيئة البوت التلقائي (RTS/DTR) والمزامنة...")
        if (!startAndConnect(transport, onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل العملية.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تثبيت/تغيير سرعة الباود (change_speed $baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لأمر قراءة الإصدار 0xFB.")
        }
        onLog("R8C Version: \"$ver\" | قاعدة العنوان: 0x%05X".format(baseAddr))

        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        val totalPages = (totalSize + 255) / 256
        val erasedBlocks = HashSet<Int>()

        try {
            for (pageIdx in 0 until totalPages) {
                transport.setRawRxMonitor(pageIdx == 0)
                val relOffset = pageIdx * 256
                val len = min(256, totalSize - relOffset)
                val bytesDone = min(relOffset + len, totalSize)
                val slice = firmwareBytes.copyOfRange(relOffset, relOffset + len)
                val physicalAddr = (baseAddr + relOffset) and 0xFFFFFF00.toInt()

                if (slice.all { it == 0xFF.toByte() }) {
                    onProgress(bytesDone, totalSize, "تخطي صفحة فارغة 0x%05X".format(physicalAddr))
                    continue
                }

                if (verifyOnly) {
                    val chipPage = readPage(
                        transport = transport,
                        address = physicalAddr,
                        onLog = onLog,
                        verboseFrameLog = (pageIdx == 0)
                    ) ?: return FlashOperationResult.Failure("تعذر قراءة الصفحة 0x%05X للمطابقة.".format(physicalAddr))

                    for (i in 0 until len) {
                        if (chipPage[i] != slice[i]) {
                            val mismatchMsg = "اختلاف المطابقة عند 0x%05X: المتوقع 0x%02X والمقروء 0x%02X."
                                .format(physicalAddr + i, slice[i].toInt() and 0xFF, chipPage[i].toInt() and 0xFF)
                            onLog("Verify Mismatch: $mismatchMsg")
                            return FlashOperationResult.Failure(mismatchMsg)
                        }
                    }
                    onProgress(
                        bytesDone,
                        totalSize,
                        "مطابقة صفحة ${pageIdx + 1}/$totalPages (0x%05X)".format(physicalAddr)
                    )
                } else {
                    val blockSize = if (physicalAddr < 0x8000) 1024 else 4096
                    val blockBase = physicalAddr and (blockSize - 1).inv()
                    if (erasedBlocks.add(blockBase)) {
                        onProgress(
                            bytesDone.coerceAtLeast(1) - 1,
                            totalSize,
                            "مسح بلوك 0x%05X قبل الكتابة...".format(blockBase)
                        )
                        if (!erasePage(transport, blockBase, onLog)) {
                            return FlashOperationResult.Failure("فشل مسح البلوك 0x%05X قبل البرمجة.".format(blockBase))
                        }
                    }

                    val ok = writePage(
                        transport = transport,
                        address = physicalAddr,
                        data256 = slice,
                        onLog = onLog,
                        verboseFrameLog = (pageIdx == 0 || pageIdx == totalPages - 1)
                    )
                    if (!ok) {
                        return FlashOperationResult.Failure("فشلت كتابة الصفحة عند 0x%05X.".format(physicalAddr))
                    }
                    val pct = ((bytesDone * 100L) / totalSize.coerceAtLeast(1)).toInt()
                    onProgress(
                        bytesDone,
                        totalSize,
                        "كتابة ${processor.partNumber}: $pct% (0x%05X)".format(physicalAddr)
                    )
                }
            }
        } finally {
            transport.setRawRxMonitor(false)
        }

        val doneMsg = if (verifyOnly) {
            "تمت مطابقة (Verify) جميع البيانات في ${processor.partNumber} بنسبة 100%."
        } else {
            "تمت برمجة (Program) الفلاش بنجاح على ${processor.partNumber} بدءاً من 0x%04X.".format(baseAddr)
        }
        return FlashOperationResult.Success(doneMsg, baseAddress = baseAddr)
    }

    private fun buildEraseBlocks(org: Int, end: Int): List<Int> {
        val list = mutableListOf<Int>()
        var addr = org
        while (addr <= end) {
            val step = if (addr < 0x8000) 1024 else 4096
            list.add(addr)
            addr += step
        }
        return list
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun toHexComma(bytes: ByteArray): String = bytes.joinToString(", ") { "%02X".format(it) }
}
