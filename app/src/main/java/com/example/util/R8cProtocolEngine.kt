package com.example.util

import com.example.data.model.McuProcessor
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Direct Kotlin translation of the standard Renesas R8C Serial Programming Protocol
 * (r8c_protocol.hpp / r8c_prog.hpp / r8c_prog.conf by Kunihito Hiramatsu).
 *
 * Commands implemented:
 * - Sync Handshake: 16 x 0x00 (20ms spacing) + 0xB0 -> expects 0xB0
 * - Baud Switch:    0xB0 (9600), 0xB1 (19200), 0xB2 (38400), 0xB3 (57600), 0xB4 (115200)
 * - Get Version:    0xFB -> reads 8 ASCII bytes (e.g. "VER.2.00")
 * - Get Status:     0x70 -> reads 2 bytes [SRD, SRD1]
 * - Clear Status:   0x50
 * - ID Inspection:  0xF5, 0xFF, 0xDF, 0x00, 0x07, ID[0..6] -> checks get_id_state() == 3
 * - Read Page:      0xFF, (addr >> 8) & 0xFF, (addr >> 16) & 0xFF -> reads 256 bytes
 * - Write Page:     0x41, (addr >> 8) & 0xFF, (addr >> 16) & 0xFF + 256 bytes -> SR4 == 0 + 0x50
 * - Erase Page:     0x20, (addr >> 8) & 0xFF, (addr >> 16) & 0xFF, 0xD0 -> SR5 == 0 + 0x50
 */
object R8cProtocolEngine {

    val DEFAULT_ID_CODE: ByteArray = ByteArray(7) { 0xFF.toByte() }

    const val LOCKED_CHIP_MESSAGE =
        "الشريحة مقفلة بمفتاح ID خاص؛ أدخل المفتاح الصحيح للقراءة، أو اضغط مسح (Erase) لتهيئتها للبرمجة"

    data class R8cStatus(
        val srd: Int,
        val srd1: Int
    ) {
        val idState: Int get() = (srd1 shr 2) and 0x03
        val sr4ProgramError: Boolean get() = ((srd shr 4) and 0x01) != 0
        val sr5EraseError: Boolean get() = ((srd shr 5) and 0x01) != 0
        fun isIdVerified(): Boolean = ((srd1 and 0x04) != 0) || ((srd1 and 0x0C) == 0x0C)
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
        fun setRts(activeLow: Boolean, onLog: (String) -> Unit)
        fun setDtr(activeLow: Boolean, onLog: (String) -> Unit)
        fun setTxBreak(enabled: Boolean, onLog: (String) -> Unit): Boolean
        fun flushInputBuffer()
        suspend fun writeRawOnly(bytes: ByteArray): String?
        suspend fun sendAndReceiveR8C(
            txBytes: ByteArray,
            expectedRxLength: Int,
            timeoutMs: Int = 500,
            onLog: (String) -> Unit
        ): R8cExchangeResult
    }

    data class ProbeOutput(
        val version: String,
        val idVerified: Boolean,
        val rawHex: String,
        val status: R8cStatus?
    )

    /**
     * Resolves ROM & Data Flash physical address ranges based on r8c_prog.conf + Renesas R8C memory rules.
     */
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
     * Strict Single-Wire UART Mode 3 (TX + RX diode-bridged to MODE) exchange:
     * Sends `txBytes` (length N), waits up to `timeoutMs` (or UART wire-time budget)
     * until `N + expectedRxLength` total bytes accumulate in RX, discards the first
     * `N` echo bytes, and returns strictly the `expectedRxLength` genuine MCU bytes.
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
     * Port start + Bootloader Hardware Reset Flow + initial 9600 bps sync (Single-Wire Mode 3):
     * 1) RTS = true (RESET = LOW) + TX/MODE hold LOW for 50ms.
     * 2) RTS = false (RESET = HIGH), then wait 100ms for internal oscillator stabilization before 16x 0x00.
     * 3) connection(): 16 x 0x00 -> flushInputBuffer() -> 0xB0 via sendAndReceiveR8C(N=1, expectedRx=1).
     */
    suspend fun startAndConnect(
        transport: SerialTransport,
        enableResetFlow: Boolean,
        b0TimeoutMs: Int = 600,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("R8C Start: فتح المنفذ على سرعة 9600 bps (8N1) • Single-Wire UART Mode 3 (N + Rx Echo Filter)...")
        if (!transport.configureUart(9600, onLog)) {
            return false
        }
        transport.setTxBreak(false, onLog)

        if (enableResetFlow) {
            onLog("R8C Boot Flow: تفعيل RTS=true (RESET=LOW) + خفض MODE/TX لمدة 50ms...")
            transport.setDtr(false, onLog)
            transport.setRts(true, onLog)
            val breakSupported = transport.setTxBreak(true, onLog)
            if (!breakSupported) {
                repeat(4) {
                    transport.writeRawOnly(byteArrayOf(0x00, 0x00, 0x00, 0x00))
                    delay(12)
                }
            } else {
                delay(50)
            }

            onLog("R8C Boot Flow: رفع RTS=false (RESET=HIGH) -> انتظار 100ms لاستقرار المذبذب الداخلي ودخول Boot Mode...")
            transport.setRts(false, onLog)
            delay(20)
            transport.setTxBreak(false, onLog)
            delay(80) // إجمالي 100ms بعد RESET=HIGH قبل إرسال 16x 0x00
        } else {
            onLog("R8C Boot Flow: نبضة RTS Reset معطلة — تحرير RTS=false و DTR=false -> انتظار 100ms...")
            transport.setDtr(false, onLog)
            transport.setRts(false, onLog)
            delay(100)
        }
        delay(30)
        transport.flushInputBuffer()

        return establishConnection(transport, b0TimeoutMs = b0TimeoutMs, onLog = onLog)
    }

    /**
     * Equivalent to r8c::protocol::connection() for Single-Wire Mode 3:
     * 1) Sends 16 x 0x00 (20ms interval), waits 30ms, then clears rxQueue via flushInputBuffer()
     *    without closing/cancelling the continuous RX listener.
     * 2) Sends [0xB0] and waits up to 600ms for [0xB0, 0xB0]:
     *    - 2 bytes [0xB0, 0xB0] -> discards first echo byte and accepts second byte as MCU ACK.
     *    - 1 byte [0xB0] -> logs echo-only hardware/power/MODE/RESET warning.
     *    - 0 bytes -> logs missing RX loopback wiring warning.
     */
    suspend fun establishConnection(
        transport: SerialTransport,
        b0TimeoutMs: Int = 600,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("TX: 00 x16 (فاصل 20ms بين كل بايت ثم انتظار 30ms وتفريغ rxQueue)")
        val zeroByte = byteArrayOf(0x00)
        for (i in 0 until 16) {
            val err = transport.writeRawOnly(zeroByte)
            if (err != null) {
                onLog("TX ERROR (R8C Sync #${i + 1}): $err")
                return false
            }
            delay(20)
        }

        delay(30)
        transport.flushInputBuffer()

        onLog("TX: B0 (طلب تأكيد اتصال R8C — انتظار 600ms على rxQueue لـ [B0 B0])")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = byteArrayOf(0xB0.toByte()),
            expectedRxLength = 1,
            timeoutMs = b0TimeoutMs,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xB0): ${res.txError}")
            return false
        }

        val raw = res.rawBytes
        if (raw.isEmpty()) {
            onLog("RX: (0 بايت خلال ${b0TimeoutMs}ms) — لا يوجد أي بايت مرتد على RX — تأكد من توصيل أسلاك المبرمجة بالبوردة")
            return false
        }
        if (raw.size == 1) {
            val single = raw[0].toInt() and 0xFF
            if (single == 0xB0) {
                onLog("RX: B0 — تم استلام بايت صدى واحد فقط (0xB0) بدون رد المعالج — تأكد من تغذية البوردة وتوصيل طرفي MODE و RESET")
            } else {
                onLog("RX: \u2066%02X\u2069 — استُلم بايت واحد غير متوقع بدون رد المعالج (المطلوب B0 B0).".format(single))
            }
            return false
        }

        val reply = res.replyBytes
        onLog("RX: \u2066${toHex(raw)}\u2069 ➔ حذف الصدى الأول، رد المعالج: \u2066${toHex(reply)}\u2069")
        val b0 = reply[0].toInt() and 0xFF
        if (b0 != 0xB0) {
            onLog("R8C Sync Error: استجاب المعالج بـ 0x%02X بدلاً من 0xB0.".format(b0))
            return false
        }
        onLog("R8C Connection OK (0xB0).")
        return true
    }

    /**
     * Equivalent to r8c::protocol::change_speed(speed_t brate)
     */
    suspend fun changeSpeed(
        transport: SerialTransport,
        targetBaudRate: Int,
        onLog: (String) -> Unit
    ): Boolean {
        val cmd: Int = when (targetBaudRate) {
            9600 -> return true // Already connected at 9600 bps
            19200 -> 0xB1
            38400 -> 0xB2
            57600 -> 0xB3
            115200 -> 0xB4
            else -> {
                onLog("R8C Baud Error: السرعة $targetBaudRate غير مدعومة (المدعوم: 9600..115200).")
                return false
            }
        }

        val tx = byteArrayOf(cmd.toByte())
        onLog("TX: ${toHex(tx)} (تغيير السرعة إلى $targetBaudRate bps: انتظار 1 صدى + 1 بايت)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = tx,
            expectedRxLength = 1,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (change_speed): ${res.txError}")
            return false
        }
        val reply = res.replyBytes
        if (reply.isEmpty()) {
            onLog("RX: (Timeout أثناء تغيير السرعة إلى $targetBaudRate — الخام ${res.totalRawReceived}/2)")
            return false
        }
        onLog("RX: ${toHex(reply)}")
        if ((reply[0].toInt() and 0xFF) != cmd) {
            onLog("R8C Speed Error: رفض المعالج تغيير السرعة (استجاب بـ ${toHex(reply)}).")
            return false
        }

        val ok = transport.configureUart(targetBaudRate, onLog)
        if (ok) {
            delay(15)
            transport.flushInputBuffer()
            onLog("Change speed OK: $targetBaudRate [bps]")
        }
        return ok
    }

    /**
     * 0xFB (N=1): sends [0xFB], waits for 1 echo byte + 8 ASCII version bytes (e.g. "VER.2.10"),
     * drops byte 0 (echo), and returns the 8-byte genuine MCU bootloader string.
     */
    suspend fun getVersion(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): String? {
        onLog("TX: FB (قراءة إصدار البوتلودر: انتظار 1 صدى + 8 بايت)")
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
        if (reply.isEmpty()) {
            onLog("RX: (لا يوجد رد لـ 0xFB خلال 500ms — الخام المستلم ${res.totalRawReceived}/9 بايت)")
            return null
        }
        val ascii = reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("").trim()
        onLog("RX: \u2066${toHex(reply)}\u2069 (\"$ascii\")")
        return ascii.ifBlank { toHex(reply) }
    }

    /**
     * 0x70 (N=1): waits for 1 echo byte + 2 genuine status bytes [SRD, SRD1] (500ms timeout),
     * drops the first 0x70 echo byte, and returns [SRD, SRD1].
     */
    suspend fun getStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = false
    ): R8cStatus? {
        if (!quiet) onLog("TX: 70 (قراءة الحالة: انتظار 1 صدى + 2 بايت حقيقي SRD/SRD1)")
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
            val partialHex = if (reply.isEmpty()) "(فارغ)" else "\u2066${toHex(reply)}\u2069"
            onLog("RX: $partialHex (نقص بايتات الحالة 0x70: استُلم ${reply.size}/2 حقيقي من إجمالي ${res.totalRawReceived}/3 بايت)")
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
     * 0x50 (N=1): sends Clear Status and drops the 1 echoed byte.
     */
    suspend fun clearStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = true
    ): Boolean {
        if (!quiet) onLog("TX: 50 (مسح سجل الحالة Clear Status)")
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
     * 0xF5 (N=12): sends [0xF5, 0xFF, 0xDF, 0x00, 0x07, ID0..ID6], drops all 12 echoed bytes,
     * then calls 0x70 (1 echo + 2 real bytes [SRD, SRD1]) and verifies:
     * (SRD1 & 0x04) != 0 || (SRD1 & 0x0C) == 0x0C.
     */
    suspend fun idInspection(
        transport: SerialTransport,
        idBytes: ByteArray,
        onLog: (String) -> Unit
    ): Pair<Boolean, R8cStatus?> {
        val effectiveId = if (idBytes.size == 7) idBytes else DEFAULT_ID_CODE.copyOf()

        val frame = ByteArray(12).apply {
            this[0] = 0xF5.toByte()
            this[1] = 0xFF.toByte()
            this[2] = 0xDF.toByte()
            this[3] = 0x00.toByte()
            this[4] = 0x07.toByte()
            for (i in 0 until 7) {
                this[5 + i] = effectiveId[i]
            }
        }

        onLog("TX (12B): \u2066${toHex(frame)}\u2069 (فحص مفتاح الحماية + تجاهل 12 بايت صدى)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = frame,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (0xF5 ID): ${res.txError}")
            return false to null
        }

        delay(20)
        val st = getStatus(transport, onLog, quiet = false) ?: return false to null
        if (!st.isIdVerified()) {
            onLog("$LOCKED_CHIP_MESSAGE [SRD1=0x%02X]".format(st.srd1))
            return false to st
        }
        onLog("ID OK: تم التحقق من مفتاح الحماية وفتح القفل بنجاح [SRD1=0x%02X].".format(st.srd1))
        return true to st
    }

    /**
     * 0xFF (N=3): sends [0xFF, addrMid, addrHigh], waits for 3 echo bytes + 256 data bytes,
     * drops the first 3 echo bytes, and returns the exact 256-byte flash page.
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
            onLog("TX: \u2066${toHex(cmd)}\u2069 (قراءة صفحة 0x%05X: انتظار 3 صدى + 256 بايت)".format(address))
        }
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = cmd,
            expectedRxLength = 256,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (read_page 0x%05X): ${res.txError}".format(address))
            return null
        }
        val page = res.replyBytes
        if (page.size < 256) {
            onLog("RX Error (read_page 0x%05X): استُلم ${page.size}/256 بايت حقيقي فقط (الخام=${res.totalRawReceived}/259).".format(address))
            return null
        }
        if (verboseFrameLog) {
            onLog("RX: [256B @ 0x%05X] \u2066${toHex(page.take(16).toByteArray())}\u2069 ...".format(address))
        }
        return page
    }

    /**
     * 0x41 (N=259): sends [0x41, addrMid, addrHigh, ...pageData256Bytes],
     * waits for all 259 echoed bytes & discards them, then polls 0x70 (1 echo + 2 real [SRD, SRD1]).
     */
    suspend fun writePage(
        transport: SerialTransport,
        address: Int,
        data256: ByteArray,
        baudRate: Int,
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
            onLog("TX: \u2066%02X %02X %02X\u2069 + 256B (كتابة صفحة 0x%05X + تجاهل 259 بايت صدى)".format(
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

        // Brief flash internal page programming settle time before checking 0x70
        delay(18)

        var st: R8cStatus? = null
        for (attempt in 1..6) {
            val cur = getStatus(transport, onLog, quiet = !verboseFrameLog || attempt > 1)
            if (cur != null) {
                st = cur
                if ((cur.srd and 0x80) != 0) break
            }
            delay(25)
        }
        val finalSt = st ?: return false
        if (finalSt.sr4ProgramError) {
            onLog("Write Error @ 0x%05X: مؤشر SR4=1 (فشل كتابة الصفحة؛ تأكد من مسح الفلاش أولاً). [SRD=0x%02X]".format(address, finalSt.srd))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    /**
     * 0x20..0xD0 (N=4): sends [0x20, addrMid, addrHigh, 0xD0], drops 4 echoed bytes,
     * then checks 0x70 SR5 == 0 -> 0x50.
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
        onLog("TX: \u2066${toHex(cmd)}\u2069 (مسح بلوك 0x%05X + تجاهل 4 بايت صدى)".format(address))
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
        for (attempt in 1..8) {
            val cur = getStatus(transport, onLog, quiet = true)
            if (cur != null) {
                st = cur
                if ((cur.srd and 0x80) != 0) break
            }
            delay(35)
        }
        val finalSt = st ?: return false
        if (finalSt.sr5EraseError) {
            onLog("Erase Error @ 0x%05X: مؤشر SR5=1 (فشل مسح البلوك). [SRD=0x%02X]".format(address, finalSt.srd))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    /**
     * Auto-Detect & Bootloader Probe for R8C
     */
    suspend fun probeAndIdentify(
        transport: SerialTransport,
        enableResetFlow: Boolean,
        idBytes: ByteArray,
        b0TimeoutMs: Int = 600,
        onLog: (String) -> Unit
    ): ProbeOutput? {
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = b0TimeoutMs, onLog = onLog)) {
            return null
        }

        val version = getVersion(transport, onLog) ?: return null
        val (verified, st) = idInspection(transport, idBytes, onLog)
        val rawHex = buildString {
            append(version.toByteArray(Charsets.US_ASCII).joinToString(" ") { "%02X".format(it) })
            if (st != null) {
                append(" | %02X %02X".format(st.srd, st.srd1))
            }
        }
        return ProbeOutput(
            version = version,
            idVerified = verified,
            rawHex = rawHex,
            status = st
        )
    }

    /**
     * Executes Full Chip Erase via R8C All-Block Erase command [0xA7, 0xD0] (N=2),
     * drops the 2 echoed bytes, waits 500ms, then checks status 0x70 (1 echo + 2 real bytes).
     */
    suspend fun eraseAllViaA7D0(
        transport: SerialTransport,
        totalSpan: Int,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        onProgress((totalSpan * 0.25f).toInt(), totalSpan, "إرسال أمر المسح الكلي (0xA7 0xD0)...")
        clearStatus(transport, onLog, quiet = false)
        delay(15)

        val eraseAllCmd = byteArrayOf(0xA7.toByte(), 0xD0.toByte())
        onLog("TX: \u2066${toHex(eraseAllCmd)}\u2069 (أمر المسح الكامل + تجاهل 2 بايت صدى)")
        val res = sendAndReceiveR8C(
            transport = transport,
            txBytes = eraseAllCmd,
            expectedRxLength = 0,
            timeoutMs = 500,
            onLog = onLog
        )
        if (res.txError != null) {
            onLog("TX ERROR (A7 D0): ${res.txError}")
            return FlashOperationResult.Failure("فشل إرسال أمر المسح الكلي [A7 D0]: ${res.txError}")
        }

        onProgress((totalSpan * 0.55f).toInt(), totalSpan, "انتظار 500ms لإتمام مسح الشريحة...")
        delay(500)

        var finalStatus: R8cStatus? = null
        for (attempt in 1..10) {
            val st = getStatus(transport, onLog, quiet = (attempt > 1))
            if (st != null) {
                finalStatus = st
                val isReady = (st.srd and 0x80) != 0
                if (isReady) {
                    onLog("RX (0xA7 0xD0 Status): SRD=0x%02X, SRD1=0x%02X".format(st.srd, st.srd1))
                    break
                }
            }
            val frac = (0.60f + 0.35f * (attempt / 10f)).coerceAtMost(0.95f)
            onProgress((totalSpan * frac).toInt(), totalSpan, "متابعة حالة المسح عبر 0x70...")
            delay(150)
        }

        if (finalStatus == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لحالة المسح الكلي عبر 0x70 بعد إرسال [A7 D0].")
        }
        if (finalStatus.sr5EraseError) {
            return FlashOperationResult.Failure(
                "فشل المسح الكلي عبر [A7 D0]: مؤشر SR5=1 [SRD=0x%02X] — قد تكون حماية ROM مفعلة."
                    .format(finalStatus.srd)
            )
        }
        clearStatus(transport, onLog, quiet = true)
        onProgress(totalSpan, totalSpan, "اكتمل المسح الكلي وإعادة تعيين ID إلى FF")
        return FlashOperationResult.Success(
            "تم مسح الشريحة بالكامل عبر [0xA7, 0xD0] وتأكيد الحالة 0x70 بنجاح — تمت إعادة تعيين مفتاح ID إلى FF."
        )
    }

    suspend fun readMemory(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        enableResetFlow: Boolean,
        idBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val layout = resolveDeviceLayout(processor)
        val totalSize = layout.romArea.sizeBytes
        val startAddr = layout.romArea.org

        onProgress(0, totalSize, "مزامنة R8C (16×0x00 + 0xB0)...")
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 600, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C (16×0x00 + 0xB0). راجع سجل TX/RX.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        val ver = getVersion(transport, onLog)
        if (ver != null) {
            onLog("R8C Bootloader: \"$ver\" | نطاق القراءة: 0x%05X..0x%05X".format(layout.romArea.org, layout.romArea.end))
        } else {
            onLog("نطاق قراءة ${processor.partNumber}: 0x%05X..0x%05X (${totalSize / 1024} KB)".format(layout.romArea.org, layout.romArea.end))
        }

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
        enableResetFlow: Boolean,
        eraseMode: String,
        idBytes: ByteArray,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val layout = resolveDeviceLayout(processor)
        val totalSpan = layout.romArea.sizeBytes.coerceAtLeast(1024)

        onProgress(0, totalSpan, "مزامنة R8C قبل المسح الكلي...")
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 600, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل المسح.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        getVersion(transport, onLog)
        idInspection(transport, idBytes, onLog)

        return eraseAllViaA7D0(
            transport = transport,
            totalSpan = totalSpan,
            onLog = onLog,
            onProgress = onProgress
        )
    }

    suspend fun programOrVerifyFlash(
        transport: SerialTransport,
        processor: McuProcessor,
        baudRate: Int,
        enableResetFlow: Boolean,
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

        onProgress(0, totalSize, "مزامنة R8C (16×0x00 + 0xB0)...")
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 600, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل العملية.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        val ver = getVersion(transport, onLog)
        if (ver != null) {
            onLog("R8C Bootloader: \"$ver\" | قاعدة العنوان: 0x%05X".format(baseAddr))
        }

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
                onProgress(
                    bytesDone,
                    totalSize,
                    "تخطي صفحة فارغة 0x%05X".format(physicalAddr)
                )
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
                // Erase enclosing 1KB (< 0x8000) or 4KB (>= 0x8000) block once before writing its pages
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
                    baudRate = baudRate,
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

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
