package com.example.util

import com.example.data.model.DataFlashExportMode
import com.example.data.model.McuProcessor
import kotlinx.coroutines.delay
import java.util.Locale
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

    data class BootSessionResult(
        val connected: Boolean,
        val dtrIdleHigh: Boolean = false
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
        suspend fun waitForByteInRxQueue(expectedByte: Byte, timeoutMs: Int): Boolean
        suspend fun sendAndReceiveR8C(
            txBytes: ByteArray,
            expectedRxLength: Int,
            timeoutMs: Int = 500,
            onLog: (String) -> Unit
        ): R8cExchangeResult
    }

    fun resolveDeviceLayout(processor: McuProcessor): R8cDeviceLayout {
        val cfg = processor.chipConfig
        val dataAreas = if (cfg.hasDataFlash && cfg.dataFlashStartAddress != null && cfg.dataFlashEndAddress != null) {
            listOf(R8cArea(cfg.dataFlashStartAddress, cfg.dataFlashEndAddress))
        } else {
            emptyList()
        }
        return R8cDeviceLayout(
            romArea = R8cArea(cfg.programStartAddress, cfg.programEndAddress),
            dataAreas = dataAreas
        )
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
     * 1. Literal R8CTool.EXE Hardware Boot Sequence + Android CP2102 DTR-Inversion Fallback:
     *    - Open 9600 bps (8N1) & attach continuous RX listener
     *    - Attempt 1 (Standard):
     *        A: setRTS(true) + setDTR(true) -> wait 250ms
     *        B: setDTR(false) -> wait 150ms
     *        C: setRTS(false) -> wait 100ms -> rxQueue.clear()
     *        Then run Byte-by-Byte Sync (16x 0x00) + 0xB0 (expect [0xB0, 0xB0]).
     *    - Attempt 2 (Automatic CP2102 DTR Inversion Fallback if Attempt 1's 0xB0 fails):
     *        A: setRTS(true) + setDTR(false) -> wait 250ms
     *        B: setDTR(true) -> wait 150ms
     *        C: setRTS(false) -> wait 100ms -> rxQueue.clear()
     *        Then re-run Byte-by-Byte Sync (16x 0x00) + 0xB0 (expect [0xB0, 0xB0]).
     */
    suspend fun startAndConnect(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): BootSessionResult {
        onLog("1) فتح المنفذ على سرعة 9600 bps (8N1)...")
        if (!transport.configureUart(9600, onLog)) {
            return BootSessionResult(connected = false)
        }

        onLog("2) ربط مستمع الاستقبال (RX Listener -> rxQueue + Raw RX Monitor)...")
        transport.setRawRxMonitor(true)
        if (!transport.ensureReaderListening(onLog)) {
            return BootSessionResult(connected = false)
        }

        // Attempt 1: Standard R8CTool.EXE DTR polarity (setDTR(true) LOW -> setDTR(false) HIGH)
        onLog("المحاولة 1 (تتابع R8CTool.EXE القياسي):")
        runBootResetSequence(transport = transport, invertDtr = false, onLog = onLog)
        if (establishConnection(transport, onLog)) {
            return BootSessionResult(connected = true, dtrIdleHigh = false)
        }

        // Attempt 2: Automatic Android CP2102 fallback inverting DTR ONLY
        onLog("فشلت المزامنة 0xB0 بالقطبية الأولى — المحاولة 2 (عكس حالة DTR فقط لتوافق CP2102):")
        runBootResetSequence(transport = transport, invertDtr = true, onLog = onLog)
        if (establishConnection(transport, onLog)) {
            return BootSessionResult(connected = true, dtrIdleHigh = true)
        }

        return BootSessionResult(connected = false)
    }

    private suspend fun runBootResetSequence(
        transport: SerialTransport,
        invertDtr: Boolean,
        onLog: (String) -> Unit
    ) {
        val dtrResetLow = !invertDtr
        val dtrResetHigh = invertDtr

        onLog("التسلسل أ: setRTS(true) و setDTR($dtrResetLow) [MODE=LOW و RESET=LOW] -> انتظار 250ms...")
        transport.setRts(enabled = true, onLog = onLog)
        transport.setDtr(enabled = dtrResetLow, onLog = onLog)
        delay(250)

        onLog("التسلسل ب: setDTR($dtrResetHigh) [RESET=HIGH مع بقاء RTS=true يمسك MODE=LOW] -> انتظار 150ms...")
        transport.setDtr(enabled = dtrResetHigh, onLog = onLog)
        delay(150)

        onLog("التسلسل ج: setRTS(false) [تحرير MODE/RX] -> انتظار 100ms -> rxQueue.clear()...")
        transport.setRts(enabled = false, onLog = onLog)
        delay(100)
        transport.flushInputBuffer()
    }

    /**
     * 2 & 3a. Byte-by-Byte Sync (16 x 0x00) + Single 0xB0 Connection Check:
     *         - Loop 16 times: write [0x00], wait up to 50ms (or until 0x00 echo arrives in rxQueue)
     *         - Wait 50ms after 16th byte -> rxQueue.clear()
     *         - Send [0xB0] ONCE ONLY (N=1), wait strictly for 2 bytes [0xB0, 0xB0] (echo + MCU ACK)
     */
    suspend fun establishConnection(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("TX: 00 x16 (بايت-ببايت: انتظار الصدى 0x00 أو 50ms لكل بايت)")
        transport.flushInputBuffer()
        val zero = byteArrayOf(0x00)
        var echoesSeen = 0
        for (i in 0 until 16) {
            val err = transport.writeRaw(zero)
            if (err != null) {
                onLog("TX ERROR (0x00 #${i + 1}): $err")
                return false
            }
            if (transport.waitForByteInRxQueue(0x00, 50)) {
                echoesSeen++
            }
        }
        onLog("اكتمل إرسال 16×0x00 (استُلم $echoesSeen/16 صدى) — انتظار 50ms ثم rxQueue.clear()...")
        delay(50)
        transport.flushInputBuffer()

        onLog("TX: B0 (أمر 0xB0 مرة واحدة: انتظار 1 + 1 = 2 بايت [B0, B0])")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0xB0.toByte()),
            expectedRxLength = 1,
            timeoutMs = 1000,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xB0): ${res.txError}")
            return false
        }

        val raw = res.rawBytes
        onLog("RX Raw (0xB0): [${toHexComma(raw)}]")
        if (raw.isEmpty()) {
            onLog("0xB0 Failed: لا يوجد أي بايت مستلم (0/2 بايت) خلال 1000ms.")
            return false
        }
        if (raw.size < 2 || res.replyBytes.isEmpty()) {
            onLog("0xB0 Failed: استُلم بايت واحد فقط [${toHexComma(raw)}] (صدى بدون رد المعالج — المعالج لم يدخل وضع البوتلودر).")
            return false
        }

        val ack = res.replyBytes[0].toInt() and 0xFF
        if (ack != 0xB0) {
            onLog("0xB0 Error: البايت الثاني هو 0x%02X بدلاً من 0xB0.".format(ack))
            return false
        }
        onLog("0xB0 OK: [B0, B0] (الأول صدى + الثاني تأكيد المعالج الحقيقي).")
        return true
    }

    /**
     * change_speed(): Only called when targetBaudRate > 9600 (since 9600 uses single 0xB0 above).
     */
    suspend fun changeSpeed(
        transport: SerialTransport,
        targetBaudRate: Int,
        dtrIdleHigh: Boolean,
        onLog: (String) -> Unit
    ): Boolean {
        val cmd: Int = when (targetBaudRate) {
            9600 -> return true
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
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = tx,
            expectedRxLength = 1,
            timeoutMs = 1000,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (change_speed): ${res.txError}")
            return false
        }
        onLog("RX Raw (change_speed): [${toHexComma(res.rawBytes)}]")
        val reply = res.replyBytes
        if (reply.isEmpty() || (reply[0].toInt() and 0xFF) != cmd) {
            onLog("change_speed Error: لم يستلم التأكيد الحقيقي 0x%02X.".format(cmd))
            return false
        }

        if (!transport.configureUart(targetBaudRate, onLog)) {
            return false
        }
        transport.setControlLines(dtr = dtrIdleHigh, rts = false, onLog = onLog)
        delay(15)
        transport.flushInputBuffer()
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
     * clear_status(): Mandatory 0x50 step before ID inspection (and after erase/write).
     * Sends [0x50] (N=1), waits for the 1 echo byte [0x50] and discards it.
     */
    suspend fun clearStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = true
    ): Boolean {
        if (!quiet) onLog("TX: 50 (clear_status: N=1 صدى [0x50])")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0x50),
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0x50): ${res.txError}")
            return false
        }
        if (!quiet && res.rawBytes.isNotEmpty()) {
            onLog("RX Raw (0x50): [${toHexComma(res.rawBytes)}] (تم حذف الصدى)")
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
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            if (verboseFrameLog && attempt == 1) {
                onLog("TX: \u2066${toHex(cmd)}\u2069 (read_page ${fmtAddr(address)})")
            }
            val res = sendAndReceiveR8C(
                transport = transport,
                txBytes = cmd,
                expectedRxLength = 256,
                timeoutMs = 1500,
                onLog = onLog
            )
            if (res.txError == null && res.replyBytes.size >= 256) {
                val page = res.replyBytes
                if (verboseFrameLog) {
                    onLog("RX Flash (256B @ ${fmtAddr(address)}): \u2066${toHex(page.take(16).toByteArray())}\u2069 ...")
                }
                return page
            }

            if (attempt < maxAttempts) {
                transport.flushInputBuffer()
                delay(50)
            } else {
                if (res.txError != null) {
                    onLog("TX ERROR (read_page ${fmtAddr(address)} بعد $maxAttempts محاولات): ${res.txError}")
                } else {
                    onLog("RX End/Timeout (read_page ${fmtAddr(address)} بعد $maxAttempts محاولات): استُلم ${res.replyBytes.size}/256 بايت حقيقي (الخام=${res.totalRawReceived}).")
                }
            }
        }
        return null
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
        onLog("TX: \u2066${toHex(cmd)}\u2069 (erase_page ${fmtAddr(address)}: حذف 4 بايت صدى)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = cmd,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (erase_page ${fmtAddr(address)}): ${res.txError}")
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
            onLog("erase_page Error @ ${fmtAddr(address)}: ((SRD >> 5) & 1) != 0 [SRD=${fmtByte(finalSt.srd)}]")
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
            val headerHex = "%02X %02X %02X".format(
                Locale.US,
                frame[0].toInt() and 0xFF,
                frame[1].toInt() and 0xFF,
                frame[2].toInt() and 0xFF
            )
            onLog("TX: \u2066$headerHex\u2069 + 256B (write_page ${fmtAddr(address)}: حذف 259 بايت صدى)")
        }
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = frame,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (write_page ${fmtAddr(address)}): ${res.txError}")
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
            onLog("write_page Error @ ${fmtAddr(address)}: ((SRD >> 4) & 1) != 0 [SRD=${fmtByte(finalSt.srd)}]")
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    suspend fun readMemory(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        idBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = readFlash(
        transport = transport,
        selectedChip = processor,
        baudRate = baudRate,
        idBytes = idBytes,
        dataFlashMode = dataFlashMode,
        onLog = onLog,
        onProgress = onProgress
    )

    suspend fun readFlash(
        transport: SerialTransport,
        selectedChip: McuProcessor,
        baudRate: Int,
        idBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val chipConfig = selectedChip.chipConfig
        val programStartAddress = chipConfig.programStartAddress
        val programEndAddress = chipConfig.programEndAddress
        val flashSizeBytes = chipConfig.flashSizeBytes
        val pageSize = chipConfig.pageSizeBytes.coerceAtLeast(256)

        val includeDataFlash = dataFlashMode != DataFlashExportMode.EXCLUDE && chipConfig.hasDataFlash
        val dfStart = chipConfig.dataFlashStartAddress ?: 0
        val dfEnd = chipConfig.dataFlashEndAddress ?: -1
        val dfSize = if (includeDataFlash) chipConfig.dataFlashSizeBytes else 0
        val overallTotalBytes = (flashSizeBytes + dfSize).coerceAtLeast(pageSize)

        onProgress(0, overallTotalBytes, "تهيئة البوت التلقائي (RTS/DTR) والمزامنة...")
        val session = startAndConnect(transport, onLog)
        if (!session.connected) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C (16×0x00 + 0xB0 [B0, B0]). راجع سجل TX/RX.")
        }
        if (!changeSpeed(transport, baudRate, session.dtrIdleHigh, onLog)) {
            return FlashOperationResult.Failure("فشل تغيير سرعة الباود ($baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لأمر قراءة الإصدار 0xFB (1+8=9 بايت).")
        }
        val dfInfo = if (includeDataFlash) " | Data Flash: ${fmtAddr(dfStart)}..${fmtAddr(dfEnd)} ($dfSize B)" else ""
        onLog("R8C Version: \"$ver\" | نطاق Program Flash: ${fmtAddr(programStartAddress)}..${fmtAddr(programEndAddress)} (${flashSizeBytes / 1024} KB)$dfInfo")

        if (!clearStatus(transport, onLog, quiet = false)) {
            return FlashOperationResult.Failure("فشل أمر مسح سجل الحالة 0x50 قبل فحص المفتاح.")
        }

        onProgress(0, overallTotalBytes, "التحقق من مفتاح الحماية 7-Byte ID (0xF5)...")
        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        // Allocate dump buffer to exact selectedChip.flashSizeBytes initialized with 0xFF
        val resultBuffer = FirmwareParser.createEmptyBuffer(flashSizeBytes)
        val totalPages = ((programEndAddress - programStartAddress + pageSize) / pageSize).coerceAtLeast(1)
        var actualBytesRead = 0
        var totalDoneBytes = 0
        var lastSuccessEndAddr = programStartAddress - 1
        var stoppedAtChipEnd = false

        transport.setRawRxMonitor(false)
        onProgress(0, overallTotalBytes, "قراءة ${selectedChip.partNumber}: 0% (${fmtAddr(programStartAddress)})...")

        var dataFlashBuffer: ByteArray? = null
        var dfBytesRead = 0

        try {
            var addr = programStartAddress
            var pageIdx = 0
            while (addr <= programEndAddress) {
                val physicalAddr = addr and 0xFFFFFF00.toInt()
                val chunk = readPage(
                    transport = transport,
                    address = physicalAddr,
                    onLog = onLog,
                    verboseFrameLog = false
                )
                if (chunk == null) {
                    if (actualBytesRead > 0) {
                        stoppedAtChipEnd = true
                        break
                    } else {
                        return FlashOperationResult.Failure(
                            "فشلت قراءة الصفحة الأولى عند العنوان ${fmtAddr(physicalAddr)} بعد 3 محاولات."
                        )
                    }
                }

                val fileOffset = addr - programStartAddress
                if (fileOffset in 0 until resultBuffer.size) {
                    val copyLen = min(pageSize, min(chunk.size, resultBuffer.size - fileOffset))
                    System.arraycopy(chunk, 0, resultBuffer, fileOffset, copyLen)
                    actualBytesRead += copyLen
                    totalDoneBytes += copyLen
                    lastSuccessEndAddr = addr + copyLen - 1
                }

                val pct = ((totalDoneBytes * 100L) / overallTotalBytes.coerceAtLeast(1)).toInt()
                onProgress(
                    totalDoneBytes,
                    overallTotalBytes,
                    "قراءة ${selectedChip.partNumber}: $pct% (${fmtAddr(physicalAddr)})"
                )
                if (pageIdx == 0 || (pageIdx + 1) % 8 == 0 || addr + pageSize > programEndAddress) {
                    onLog("قراءة الفلاش: صفحة ${pageIdx + 1}/$totalPages (${fmtAddr(physicalAddr)}) — $pct% (${actualBytesRead / 1024} KB)")
                }

                addr += pageSize
                pageIdx++
            }

            // Optional Data Flash read (0x02400..0x02BFF or 0x03000..0x033FF)
            if (includeDataFlash && !stoppedAtChipEnd) {
                val dfBuf = ByteArray(dfSize) { 0xFF.toByte() }
                var dfAddr = dfStart
                var dfPageIdx = 0
                val totalDfPages = ((dfEnd - dfStart + pageSize) / pageSize).coerceAtLeast(1)
                onLog("بدء قراءة Data Flash: ${fmtAddr(dfStart)}..${fmtAddr(dfEnd)} ($dfSize بايت)...")

                while (dfAddr <= dfEnd) {
                    val physicalDfAddr = dfAddr and 0xFFFFFF00.toInt()
                    val chunk = readPage(
                        transport = transport,
                        address = physicalDfAddr,
                        onLog = onLog,
                        verboseFrameLog = false
                    )
                    if (chunk == null) {
                        onLog("تنبيه: توقف Data Flash عند العنوان ${fmtAddr(physicalDfAddr)} — تم استلام $dfBytesRead/$dfSize بايت.")
                        break
                    }
                    val dfOffset = dfAddr - dfStart
                    if (dfOffset in 0 until dfBuf.size) {
                        val copyLen = min(pageSize, min(chunk.size, dfBuf.size - dfOffset))
                        System.arraycopy(chunk, 0, dfBuf, dfOffset, copyLen)
                        dfBytesRead += copyLen
                        totalDoneBytes += copyLen
                    }
                    val pct = ((totalDoneBytes * 100L) / overallTotalBytes.coerceAtLeast(1)).toInt()
                    onProgress(
                        totalDoneBytes,
                        overallTotalBytes,
                        "قراءة Data Flash: $pct% (${fmtAddr(physicalDfAddr)})"
                    )
                    if (dfPageIdx == 0 || dfPageIdx == totalDfPages - 1) {
                        onLog("قراءة Data Flash: صفحة ${dfPageIdx + 1}/$totalDfPages (${fmtAddr(physicalDfAddr)})")
                    }
                    dfAddr += pageSize
                    dfPageIdx++
                }

                if (dfBytesRead > 0) {
                    dataFlashBuffer = dfBuf
                }
            }
        } catch (t: Throwable) {
            onLog("Read Flash Exception: ${t.toString()}")
            return FlashOperationResult.Failure("استثناء أثناء قراءة الفلاش: ${t.toString()}")
        } finally {
            transport.setRawRxMonitor(false)
        }

        val actualEndAddr = if (lastSuccessEndAddr >= programStartAddress) lastSuccessEndAddr else programEndAddress
        val dfSuffix = if (dataFlashBuffer != null) " + Data Flash ($dfBytesRead بايت)" else ""
        val completionMsg = "اكتملت قراءة الفلاش المتاح بنجاح حتى العنوان ${fmtAddr(actualEndAddr)}$dfSuffix"
        onProgress(overallTotalBytes, overallTotalBytes, "$completionMsg (100%)")
        onLog("$completionMsg • حجم مصفوفة الحفظ: ${resultBuffer.size} بايت (${resultBuffer.size / 1024} KB)")

        return FlashOperationResult.Success(
            message = "$completionMsg (${actualBytesRead / 1024} KB مقروءة • حجم الملف ${resultBuffer.size / 1024} KB).",
            bytesTransferred = resultBuffer,
            baseAddress = programStartAddress,
            dataFlashBytes = dataFlashBuffer,
            dataFlashBaseAddress = if (dataFlashBuffer != null) dfStart else 0
        )
    }

    suspend fun eraseFlash(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        idBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val chipConfig = processor.chipConfig
        val includeDataFlash = dataFlashMode != DataFlashExportMode.EXCLUDE && chipConfig.hasDataFlash
        val totalSpan = (chipConfig.flashSizeBytes + if (includeDataFlash) chipConfig.dataFlashSizeBytes else 0)
            .coerceAtLeast(1024)

        onProgress(0, totalSpan, "تهيئة البوت التلقائي (RTS/DTR) قبل المسح...")
        val session = startAndConnect(transport, onLog)
        if (!session.connected) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل المسح.")
        }
        if (!changeSpeed(transport, baudRate, session.dtrIdleHigh, onLog)) {
            return FlashOperationResult.Failure("فشل تغيير سرعة الباود ($baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver != null) {
            onLog("R8C Version: \"$ver\"")
        }
        if (!clearStatus(transport, onLog, quiet = false)) {
            return FlashOperationResult.Failure("فشل أمر مسح سجل الحالة 0x50 قبل فحص المفتاح.")
        }
        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        transport.setRawRxMonitor(false)
        val blocks = buildEraseBlocks(chipConfig.programStartAddress, chipConfig.programEndAddress).toMutableList()
        if (includeDataFlash && chipConfig.dataFlashStartAddress != null && chipConfig.dataFlashEndAddress != null) {
            blocks.addAll(buildEraseBlocks(chipConfig.dataFlashStartAddress, chipConfig.dataFlashEndAddress))
        }

        for ((idx, blockAddr) in blocks.withIndex()) {
            onProgress(
                ((idx.toFloat() / blocks.size.coerceAtLeast(1)) * totalSpan).toInt(),
                totalSpan,
                "مسح بلوك ${idx + 1}/${blocks.size} (${fmtAddr(blockAddr)})..."
            )
            if (!erasePage(transport, blockAddr, onLog)) {
                return FlashOperationResult.Failure("فشل مسح البلوك عند العنوان ${fmtAddr(blockAddr)}.")
            }
        }

        onProgress(totalSpan, totalSpan, "اكتمل مسح جميع البلوكات")
        return FlashOperationResult.Success(
            "تم مسح ذاكرة ${processor.partNumber} (${blocks.size} بلوك بدءاً من ${fmtAddr(chipConfig.programStartAddress)}) بنجاح."
        )
    }

    suspend fun writeFlash(
        transport: SerialTransport,
        selectedChip: McuProcessor,
        baudRate: Int,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        idBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        dataFlashBytes: ByteArray? = null,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = programOrVerifyFlash(
        transport = transport,
        processor = selectedChip,
        baudRate = baudRate,
        firmwareBytes = firmwareBytes,
        firmwareStartAddress = firmwareStartAddress,
        idBytes = idBytes,
        verifyOnly = false,
        dataFlashMode = dataFlashMode,
        dataFlashBytes = dataFlashBytes,
        onLog = onLog,
        onProgress = onProgress
    )

    suspend fun verifyFlash(
        transport: SerialTransport,
        selectedChip: McuProcessor,
        baudRate: Int,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        idBytes: ByteArray,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        dataFlashBytes: ByteArray? = null,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult = programOrVerifyFlash(
        transport = transport,
        processor = selectedChip,
        baudRate = baudRate,
        firmwareBytes = firmwareBytes,
        firmwareStartAddress = firmwareStartAddress,
        idBytes = idBytes,
        verifyOnly = true,
        dataFlashMode = dataFlashMode,
        dataFlashBytes = dataFlashBytes,
        onLog = onLog,
        onProgress = onProgress
    )

    suspend fun programOrVerifyFlash(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        idBytes: ByteArray,
        verifyOnly: Boolean,
        dataFlashMode: DataFlashExportMode = DataFlashExportMode.EXCLUDE,
        dataFlashBytes: ByteArray? = null,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val chipConfig = processor.chipConfig
        val programStartAddress = if (firmwareStartAddress > 0) firmwareStartAddress else chipConfig.programStartAddress
        val pageSize = chipConfig.pageSizeBytes.coerceAtLeast(256)
        val effectiveProgSize = min(firmwareBytes.size, chipConfig.flashSizeBytes)
        val programEndAddress = min(chipConfig.programEndAddress, programStartAddress + effectiveProgSize - 1)

        val includeDataFlash = dataFlashMode != DataFlashExportMode.EXCLUDE &&
            chipConfig.hasDataFlash &&
            dataFlashBytes != null &&
            dataFlashBytes.isNotEmpty()
        val dfStart = chipConfig.dataFlashStartAddress ?: 0
        val dfEnd = chipConfig.dataFlashEndAddress ?: -1
        val dfSize = if (includeDataFlash) min(chipConfig.dataFlashSizeBytes, dataFlashBytes?.size ?: 0) else 0
        val totalSize = (effectiveProgSize + dfSize).coerceAtLeast(pageSize)

        onProgress(0, totalSize, "تهيئة البوت التلقائي (RTS/DTR) والمزامنة...")
        val session = startAndConnect(transport, onLog)
        if (!session.connected) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل العملية.")
        }
        if (!changeSpeed(transport, baudRate, session.dtrIdleHigh, onLog)) {
            return FlashOperationResult.Failure("فشل تغيير سرعة الباود ($baudRate bps).")
        }
        val ver = getVersion(transport, onLog)
        if (ver == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لأمر قراءة الإصدار 0xFB (1+8=9 بايت).")
        }
        onLog("R8C Version: \"$ver\" | النطاق: ${fmtAddr(programStartAddress)}..${fmtAddr(programEndAddress)} (${effectiveProgSize / 1024} KB)")

        if (!clearStatus(transport, onLog, quiet = false)) {
            return FlashOperationResult.Failure("فشل أمر مسح سجل الحالة 0x50 قبل فحص المفتاح.")
        }

        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure(LOCKED_CHIP_MESSAGE)
        }

        val totalProgPages = ((programEndAddress - programStartAddress + pageSize) / pageSize).coerceAtLeast(1)
        val erasedBlocks = HashSet<Int>()
        var cumulativeDone = 0

        transport.setRawRxMonitor(false)
        try {
            var addr = programStartAddress
            var pageIdx = 0
            while (addr <= programEndAddress) {
                val fileOffset = addr - programStartAddress
                if (fileOffset !in firmwareBytes.indices) break
                val len = min(pageSize, firmwareBytes.size - fileOffset)
                val slice = firmwareBytes.copyOfRange(fileOffset, fileOffset + len)
                val physicalAddr = addr and 0xFFFFFF00.toInt()
                cumulativeDone = min(fileOffset + len, effectiveProgSize)

                if (slice.all { it == 0xFF.toByte() }) {
                    onProgress(cumulativeDone, totalSize, "تخطي صفحة فارغة ${fmtAddr(physicalAddr)}")
                    addr += pageSize
                    pageIdx++
                    continue
                }

                if (verifyOnly) {
                    val chipPage = readPage(
                        transport = transport,
                        address = physicalAddr,
                        onLog = onLog,
                        verboseFrameLog = (pageIdx == 0)
                    ) ?: return FlashOperationResult.Failure("تعذر قراءة الصفحة ${fmtAddr(physicalAddr)} للمطابقة.")

                    for (i in 0 until len) {
                        if (chipPage[i] != slice[i]) {
                            val mismatchMsg = "اختلاف المطابقة عند ${fmtAddr(physicalAddr + i)}: المتوقع ${fmtByte(slice[i].toInt() and 0xFF)} والمقروء ${fmtByte(chipPage[i].toInt() and 0xFF)}."
                            onLog("Verify Mismatch: $mismatchMsg")
                            return FlashOperationResult.Failure(mismatchMsg)
                        }
                    }
                    onProgress(
                        cumulativeDone,
                        totalSize,
                        "مطابقة صفحة ${pageIdx + 1}/$totalProgPages (${fmtAddr(physicalAddr)})"
                    )
                } else {
                    val blockSize = if (physicalAddr < 0x8000) 1024 else 4096
                    val blockBase = physicalAddr and (blockSize - 1).inv()
                    if (erasedBlocks.add(blockBase)) {
                        onProgress(
                            cumulativeDone.coerceAtLeast(1) - 1,
                            totalSize,
                            "مسح بلوك ${fmtAddr(blockBase)} قبل الكتابة..."
                        )
                        if (!erasePage(transport, blockBase, onLog)) {
                            return FlashOperationResult.Failure("فشل مسح البلوك ${fmtAddr(blockBase)} قبل البرمجة.")
                        }
                    }

                    val ok = writePage(
                        transport = transport,
                        address = physicalAddr,
                        data256 = slice,
                        onLog = onLog,
                        verboseFrameLog = (pageIdx == 0 || pageIdx == totalProgPages - 1)
                    )
                    if (!ok) {
                        return FlashOperationResult.Failure("فشلت كتابة الصفحة عند ${fmtAddr(physicalAddr)}.")
                    }
                    val pct = ((cumulativeDone * 100L) / totalSize.coerceAtLeast(1)).toInt()
                    onProgress(
                        cumulativeDone,
                        totalSize,
                        "كتابة ${processor.partNumber}: $pct% (${fmtAddr(physicalAddr)})"
                    )
                }

                addr += pageSize
                pageIdx++
            }

            if (includeDataFlash && dataFlashBytes != null) {
                var dfAddr = dfStart
                var dfIdx = 0
                val totalDfPages = ((dfEnd - dfStart + pageSize) / pageSize).coerceAtLeast(1)
                while (dfAddr <= dfEnd) {
                    val dfOffset = dfAddr - dfStart
                    if (dfOffset !in dataFlashBytes.indices) break
                    val len = min(pageSize, dataFlashBytes.size - dfOffset)
                    val slice = dataFlashBytes.copyOfRange(dfOffset, dfOffset + len)
                    val physicalAddr = dfAddr and 0xFFFFFF00.toInt()
                    val overallDone = min(effectiveProgSize + dfOffset + len, totalSize)

                    if (slice.all { it == 0xFF.toByte() }) {
                        onProgress(overallDone, totalSize, "تخطي Data Flash فارغ ${fmtAddr(physicalAddr)}")
                        dfAddr += pageSize
                        dfIdx++
                        continue
                    }

                    if (verifyOnly) {
                        val chipPage = readPage(
                            transport = transport,
                            address = physicalAddr,
                            onLog = onLog,
                            verboseFrameLog = (dfIdx == 0)
                        ) ?: return FlashOperationResult.Failure("تعذر قراءة Data Flash عند ${fmtAddr(physicalAddr)} للمطابقة.")

                        for (i in 0 until len) {
                            if (chipPage[i] != slice[i]) {
                                val mismatchMsg = "اختلاف Data Flash عند ${fmtAddr(physicalAddr + i)}: المتوقع ${fmtByte(slice[i].toInt() and 0xFF)} والمقروء ${fmtByte(chipPage[i].toInt() and 0xFF)}."
                                onLog("Verify Data Flash Mismatch: $mismatchMsg")
                                return FlashOperationResult.Failure(mismatchMsg)
                            }
                        }
                        onProgress(overallDone, totalSize, "مطابقة Data Flash ${dfIdx + 1}/$totalDfPages (${fmtAddr(physicalAddr)})")
                    } else {
                        val blockBase = physicalAddr and 0xFFFFFC00.toInt()
                        if (erasedBlocks.add(blockBase)) {
                            if (!erasePage(transport, blockBase, onLog)) {
                                return FlashOperationResult.Failure("فشل مسح بلوك Data Flash عند ${fmtAddr(blockBase)}.")
                            }
                        }
                        val ok = writePage(
                            transport = transport,
                            address = physicalAddr,
                            data256 = slice,
                            onLog = onLog,
                            verboseFrameLog = (dfIdx == 0)
                        )
                        if (!ok) {
                            return FlashOperationResult.Failure("فشلت كتابة Data Flash عند ${fmtAddr(physicalAddr)}.")
                        }
                        onProgress(overallDone, totalSize, "كتابة Data Flash ${dfIdx + 1}/$totalDfPages (${fmtAddr(physicalAddr)})")
                    }

                    dfAddr += pageSize
                    dfIdx++
                }
            }
        } catch (t: Throwable) {
            onLog("Program/Verify Exception: ${t.toString()}")
            return FlashOperationResult.Failure("استثناء أثناء العملية: ${t.toString()}")
        } finally {
            transport.setRawRxMonitor(false)
        }

        val doneMsg = if (verifyOnly) {
            "تمت مطابقة (Verify) جميع البيانات في ${processor.partNumber} بنسبة 100%."
        } else {
            "تمت برمجة (Program) الفلاش بنجاح على ${processor.partNumber} بدءاً من ${fmtAddr(programStartAddress, 4)}."
        }
        return FlashOperationResult.Success(doneMsg, baseAddress = programStartAddress)
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

    private fun fmtAddr(value: Int, width: Int = 5): String =
        String.format(Locale.US, "0x%0${width}X", value)

    private fun fmtByte(value: Int): String =
        String.format(Locale.US, "0x%02X", value and 0xFF)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }

    private fun toHexComma(bytes: ByteArray): String =
        bytes.joinToString(", ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
}
