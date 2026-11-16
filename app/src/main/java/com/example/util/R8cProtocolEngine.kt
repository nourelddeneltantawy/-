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
        fun setControlLines(dtr: Boolean, rts: Boolean, onLog: (String) -> Unit)
        fun flushInputBuffer()
        suspend fun writeRaw(bytes: ByteArray): String?
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
     * 2. Diode-Echo-Filtered Exchange Helper:
     * Sends `txBytes` (length N), waits until `N + expectedRxLength` bytes accumulate in rxQueue,
     * discards the first N echoed bytes, and returns only the last M genuine MCU reply bytes.
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
     * 1. Mandatory 100% Automatic Hardware Boot Entry:
     *    - Open 9600 bps (8N1)
     *    - Step A (Pull MODE LOW): set RTS = true, DTR = false -> wait 30ms
     *    - Step B (RESET Pulse):   keep RTS = true, set DTR = true (RESET=LOW) -> wait 50ms -> set DTR = false (RESET=HIGH)
     *    - Step C (Boot Capture):  wait 60ms while MCU samples MODE=LOW -> set RTS = false to release MODE/RX
     */
    suspend fun startAndConnect(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("R8C Boot Entry: فتح المنفذ على سرعة 9600 bps (8N1)...")
        if (!transport.configureUart(9600, onLog)) {
            return false
        }

        // الخطوة أ: خفض MODE عبر دايود RTS
        onLog("الخطوة أ (خفض MODE): RTS=true (سحب MODE=LOW) -> انتظار 30ms...")
        transport.setControlLines(dtr = false, rts = true, onLog = onLog)
        delay(30)

        // الخطوة ب: نبضة RESET عبر DTR مع إبقاء RTS=true
        onLog("الخطوة ب (نبضة RESET): DTR=true (RESET=LOW) مع إبقاء RTS=true لمدة 50ms ثم DTR=false (RESET=HIGH)...")
        transport.setControlLines(dtr = true, rts = true, onLog = onLog)
        delay(50)
        transport.setControlLines(dtr = false, rts = true, onLog = onLog)

        // الخطوة ج: انتظار 60ms لدخول البوتلودر ثم تحرير خط MODE/RX
        onLog("الخطوة ج (التقاط البوتلودر): انتظار 60ms ثم تحرير RTS=false لفتح خط MODE/RX...")
        delay(60)
        transport.setControlLines(dtr = false, rts = false, onLog = onLog)
        delay(10)
        transport.flushInputBuffer()

        return establishConnection(transport, onLog)
    }

    /**
     * 3a. connection():
     *     - Send 0x00 x 16 with 20ms interval
     *     - Wait 30ms and clear rxQueue
     *     - Send [0xB0] (N=1, M=1), drop first echo byte, verify second byte == 0xB0
     */
    suspend fun establishConnection(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("TX: 00 x16 (فاصل 20ms بين كل بايت)")
        val zero = byteArrayOf(0x00)
        for (i in 0 until 16) {
            val err = transport.writeRaw(zero)
            if (err != null) {
                onLog("TX ERROR (connection 0x00 #${i + 1}): $err")
                return false
            }
            delay(20)
        }

        delay(30)
        transport.flushInputBuffer()

        onLog("TX: B0 (connection: N=1 صدى + M=1 تأكيد 0xB0)")
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
        if (raw.isEmpty()) {
            onLog("RX: (0 بايت خلال 1000ms) — لا يوجد أي بايت مرتد على RX.")
            return false
        }
        val reply = res.replyBytes
        if (reply.isEmpty()) {
            onLog("RX: \u2066${toHex(raw)}\u2069 (تم استلام الصدى فقط بدون رد المعالج 1/2 بايت — تأكد من البوتلودر وتغذية البوردة).")
            return false
        }
        onLog("RX: \u2066${toHex(raw)}\u2069 ➔ حذف الصدى، رد المعالج: \u2066${toHex(reply)}\u2069")
        val ack = reply[0].toInt() and 0xFF
        if (ack != 0xB0) {
            onLog("connection() Error: استجاب بـ 0x%02X بدلاً من 0xB0.".format(ack))
            return false
        }
        onLog("connection() OK (0xB0).")
        return true
    }

    /**
     * 3b. change_speed():
     *     - Send [0xB0] a second time for 9600 bps (or 0xB1..0xB4), N=1, M=1
     *     - Drop echo byte and verify second byte == cmd
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
        onLog("TX: \u2066${toHex(tx)}\u2069 (change_speed $targetBaudRate bps: N=1 صدى + M=1 تأكيد)")
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
        val reply = res.replyBytes
        if (reply.isEmpty()) {
            onLog("RX: (Timeout 1000ms أثناء change_speed — الخام \u2066${toHex(res.rawBytes)}\u2069)")
            return false
        }
        onLog("RX: \u2066${toHex(res.rawBytes)}\u2069 ➔ \u2066${toHex(reply)}\u2069")
        val ack = reply[0].toInt() and 0xFF
        if (ack != cmd) {
            onLog("change_speed Error: استلم 0x%02X بدلاً من 0x%02X.".format(ack, cmd))
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
     *     - Send [0xFB] (N=1, M=8), discard byte 0 echo, display genuine 8-byte ASCII version (e.g. "VER.2.10")
     */
    suspend fun getVersion(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): String? {
        onLog("TX: FB (get_version: N=1 صدى + M=8 بايت)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0xFB.toByte()),
            expectedRxLength = 8,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xFB): ${res.txError}")
            return null
        }
        val reply = res.replyBytes
        if (reply.size < 8) {
            onLog("RX: \u2066${toHex(res.rawBytes)}\u2069 (استُلم ${reply.size}/8 بايت حقيقي لـ 0xFB)")
            return null
        }
        val ascii = reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("").trim()
        onLog("RX: \u2066${toHex(reply)}\u2069 (\"$ascii\")")
        return ascii.ifBlank { toHex(reply) }
    }

    /**
     * get_status():
     * - Send [0x70] (N=1, M=2), drop echo byte, read [SRD, SRD1]
     */
    suspend fun getStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = false
    ): R8cStatus? {
        if (!quiet) onLog("TX: 70 (get_status: N=1 صدى + M=2 بايت [SRD, SRD1])")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0x70),
            expectedRxLength = 2,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0x70): ${res.txError}")
            return null
        }
        val reply = res.replyBytes
        if (reply.size < 2) {
            onLog("RX: \u2066${toHex(res.rawBytes)}\u2069 (استُلم ${reply.size}/2 بايت حقيقي من 0x70)")
            return null
        }
        val st = R8cStatus(
            srd = reply[0].toInt() and 0xFF,
            srd1 = reply[1].toInt() and 0xFF
        )
        if (!quiet) {
            onLog("RX: \u2066${toHex(reply)}\u2069 [SRD=0x%02X, SRD1=0x%02X]".format(st.srd, st.srd1))
        }
        return st
    }

    /**
     * clear_status(): Send [0x50] (N=1, M=0) and drop echo
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
            timeoutMs = 500,
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
     *     - Send [0xF5, 0xDF, 0xFF, 0x00, 0x07, id0..id6] (N=12, M=0) -> discard 12 echo bytes
     *     - Send [0x70] (N=1, M=2) -> discard byte 0 echo -> check (SRD1 >> 2) & 3 == 3
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

        onLog("TX (12B): \u2066${toHex(frame)}\u2069 (id_inspection: حذف 12 بايت صدى)")
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
     *     - Send [0xFF, (address >> 8) & 0xFF, (address >> 16) & 0xFF] (N=3, M=256)
     *     - Discard first 3 echo bytes and return 256 genuine flash bytes
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
            onLog("TX: \u2066${toHex(cmd)}\u2069 (read_page 0x%05X: N=3 صدى + M=256 بايت)".format(address))
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
        val page = res.replyBytes
        if (page.size < 256) {
            onLog("RX Error (read_page 0x%05X): استُلم ${page.size}/256 بايت حقيقي (الخام=${res.totalRawReceived}/259).".format(address))
            return null
        }
        if (verboseFrameLog) {
            onLog("RX (256B @ 0x%05X): \u2066${toHex(page.take(16).toByteArray())}\u2069 ...".format(address))
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

        for (pageIdx in 0 until totalPages) {
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

        for (pageIdx in 0 until totalPages) {
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
}
