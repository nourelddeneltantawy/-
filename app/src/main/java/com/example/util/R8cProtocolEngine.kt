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

    data class R8cStatus(
        val srd: Int,
        val srd1: Int
    ) {
        val idState: Int get() = (srd1 shr 2) and 0x03
        val sr4ProgramError: Boolean get() = ((srd shr 4) and 0x01) != 0
        val sr5EraseError: Boolean get() = ((srd shr 5) and 0x01) != 0
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

    interface SerialTransport {
        fun configureUart(baudRate: Int, onLog: (String) -> Unit): Boolean
        fun setRts(activeLow: Boolean, onLog: (String) -> Unit)
        fun setDtr(activeLow: Boolean, onLog: (String) -> Unit)
        fun setTxBreak(enabled: Boolean, onLog: (String) -> Unit): Boolean
        fun flushInputBuffer()
        suspend fun writeBytes(bytes: ByteArray, clearStaleRx: Boolean, onLog: (String) -> Unit): String?
        suspend fun readExactBytes(expectedLen: Int, timeoutMs: Int, onLog: (String) -> Unit): ByteArray
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
     * Port start + Bootloader Hardware Reset Flow + initial 9600 bps sync (Mandatory Echo Filter = ON):
     * 1) RTS = true (RESET = LOW) + TX/MODE hold LOW for 50ms.
     * 2) RTS = false (RESET = HIGH), then wait 100ms for internal oscillator stabilization before 16x 0x00.
     * 3) connection(): 16 x 0x00 (20ms gap) + 0xB0 -> discard TX echoes -> verify real 0xB0 response.
     */
    suspend fun startAndConnect(
        transport: SerialTransport,
        enableResetFlow: Boolean,
        b0TimeoutMs: Int = 400,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("R8C Start: فتح المنفذ على سرعة 9600 bps (8N1) • تصفية الصدى (Echo Filter=ON)...")
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
                    transport.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00), clearStaleRx = false, onLog = onLog)
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
        transport.flushInputBuffer()

        return establishConnection(transport, b0TimeoutMs = b0TimeoutMs, onLog = onLog)
    }

    /**
     * Equivalent to r8c::protocol::connection()
     */
    suspend fun establishConnection(
        transport: SerialTransport,
        b0TimeoutMs: Int = 400,
        onLog: (String) -> Unit
    ): Boolean {
        onLog("TX: 00 x16 (فاصل 20ms • مع حذف صدى الدايود)")
        val zeroByte = byteArrayOf(0x00)
        for (i in 0 until 16) {
            val err = transport.writeBytes(zeroByte, clearStaleRx = (i == 0), onLog = onLog)
            if (err != null) {
                onLog("TX ERROR (R8C Sync #${i + 1}): $err")
                return false
            }
            delay(20)
        }

        delay(20)
        onLog("TX: B0 (طلب تأكيد اتصال R8C مع تجاهل الصدى)")
        val b0Err = transport.writeBytes(byteArrayOf(0xB0.toByte()), clearStaleRx = true, onLog = onLog)
        if (b0Err != null) {
            onLog("TX ERROR (0xB0): $b0Err")
            return false
        }

        val reply = transport.readExactBytes(expectedLen = 1, timeoutMs = b0TimeoutMs, onLog = onLog)
        if (reply.isEmpty()) {
            onLog("RX: (لا يوجد رد / Timeout ${b0TimeoutMs}ms لـ 0xB0 بعد حذف الصدى)")
            return false
        }
        onLog("RX: \u2066${toHex(reply)}\u2069")
        val b0 = reply[0].toInt() and 0xFF
        if (b0 != 0xB0) {
            onLog("R8C Sync Error: استجاب بـ 0x%02X بدلاً من 0xB0.".format(b0))
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
        onLog("TX: ${toHex(tx)} (تغيير السرعة إلى $targetBaudRate bps)")
        val err = transport.writeBytes(tx, clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (change_speed): $err")
            return false
        }
        val reply = transport.readExactBytes(expectedLen = 1, timeoutMs = 350, onLog = onLog)
        if (reply.isEmpty()) {
            onLog("RX: (Timeout أثناء تغيير السرعة إلى $targetBaudRate)")
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
            onLog("Change speed OK: $targetBaudRate [bps]")
        }
        return ok
    }

    /**
     * Equivalent to r8c::protocol::get_version() -> 0xFB reads 8 ASCII bytes (e.g. "VER.1.00", "VER.2.00", "VER.2.10")
     * Note: TX 0xFB echo is discarded automatically by SerialTransport before reading the 8-byte MCU reply.
     */
    suspend fun getVersion(
        transport: SerialTransport,
        onLog: (String) -> Unit
    ): String? {
        onLog("TX: FB (قراءة إصدار البوتلودر مع تجاهل صدى TX)")
        val err = transport.writeBytes(byteArrayOf(0xFB.toByte()), clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (0xFB): $err")
            return null
        }
        val reply = transport.readExactBytes(expectedLen = 8, timeoutMs = 600, onLog = onLog)
        if (reply.isEmpty()) {
            onLog("RX: (لا يوجد رد / Timeout 600ms لـ 0xFB بعد حذف الصدى)")
            return null
        }
        val ascii = reply.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("").trim()
        onLog("RX: \u2066${toHex(reply)}\u2069 (\"$ascii\")")
        return ascii.ifBlank { toHex(reply) }
    }

    /**
     * Equivalent to r8c::protocol::get_status(status& st) -> 0x70 reads [SRD, SRD1]
     * Discards 1-byte 0x70 TX echo, then uses a 300ms RX buffer loop to collect [SRD, SRD1].
     */
    suspend fun getStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = false
    ): R8cStatus? {
        if (!quiet) onLog("TX: 70 (قراءة سجل الحالة SRD/SRD1 بعد حذف الصدى - انتظار 300ms)")
        val err = transport.writeBytes(byteArrayOf(0x70), clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (0x70): $err")
            return null
        }
        val reply = transport.readExactBytes(expectedLen = 2, timeoutMs = 300, onLog = onLog)
        if (reply.size < 2) {
            val partialHex = if (reply.isEmpty()) "(فارغ)" else "\u2066${toHex(reply)}\u2069"
            onLog("RX: $partialHex (نقص بايتات الحالة 0x70: استُلم ${reply.size}/2 خلال 300ms)")
            return null
        }
        val st = R8cStatus(
            srd = reply[0].toInt() and 0xFF,
            srd1 = reply[1].toInt() and 0xFF
        )
        if (!quiet) {
            onLog("RX: \u2066${toHex(reply)}\u2069 [SRD=0x%02X, SRD1=0x%02X, ID_State=${st.idState}]".format(st.srd, st.srd1))
        }
        return st
    }

    /**
     * Equivalent to r8c::protocol::clear_status() -> 0x50
     */
    suspend fun clearStatus(
        transport: SerialTransport,
        onLog: (String) -> Unit,
        quiet: Boolean = true
    ): Boolean {
        if (!quiet) onLog("TX: 50 (مسح سجل الحالة Clear Status)")
        val err = transport.writeBytes(byteArrayOf(0x50), clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (0x50): $err")
            return false
        }
        return true
    }

    /**
     * Equivalent to r8c::protocol::id_inspection(const id_t& t)
     * Exact 12-byte frame: [0xF5, 0xFF, 0xDF, 0x00, 0x07, ID0..ID6] (Little-Endian ID address 0x00DFFF),
     * then verifies get_status().get_id_state() == 3 via a 300ms buffer read for [SRD, SRD1].
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

        onLog("TX (12B): \u2066${toHex(frame)}\u2069 (إرسال مفتاح 7-Byte ID للعنوان FF DF 00)")
        val err = transport.writeBytes(frame, clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (0xF5 ID): $err")
            return false to null
        }

        delay(25)
        val st = getStatus(transport, onLog, quiet = false) ?: return false to null
        if (!st.isIdVerified()) {
            onLog("ID Verification Failed: id_state=${st.idState} (المتوقع 3). تحقق من كود الحماية [SRD1=0x%02X].".format(st.srd1))
            return false to st
        }
        onLog("ID OK: تم فك قفل المعالج بنجاح (id_state == 3).")
        return true to st
    }

    /**
     * Equivalent to r8c::protocol::read_page(uint32_t address, uint8_t* dst)
     * Sends 3 bytes: [0xFF, (address >> 8) & 0xFF, (address >> 16) & 0xFF], reads 256 bytes.
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
            onLog("TX: ${toHex(cmd)} (قراءة صفحة 0x%06X)".format(address))
        }
        val err = transport.writeBytes(cmd, clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (read_page 0x%06X): $err".format(address))
            return null
        }
        val page = transport.readExactBytes(expectedLen = 256, timeoutMs = 600, onLog = onLog)
        if (page.size < 256) {
            onLog("RX Error (read_page 0x%06X): استُلم ${page.size}/256 بايت فقط.".format(address))
            return null
        }
        if (verboseFrameLog) {
            onLog("RX: [256B @ 0x%06X] ${toHex(page.take(16).toByteArray())} ...".format(address))
        }
        return page
    }

    /**
     * Equivalent to r8c::protocol::write_page(uint32_t address, const uint8_t* src)
     * Sends 3-byte header [0x41, A15..A8, A23..A16] + 256 data bytes -> get_status() SR4 == 0 -> clear_status().
     */
    suspend fun writePage(
        transport: SerialTransport,
        address: Int,
        data256: ByteArray,
        onLog: (String) -> Unit,
        verboseFrameLog: Boolean = false
    ): Boolean {
        val header = byteArrayOf(
            0x41.toByte(),
            ((address ushr 8) and 0xFF).toByte(),
            ((address ushr 16) and 0xFF).toByte()
        )
        val payload = if (data256.size == 256) data256 else {
            ByteArray(256) { 0xFF.toByte() }.also {
                System.arraycopy(data256, 0, it, 0, min(256, data256.size))
            }
        }
        if (verboseFrameLog) {
            onLog("TX: ${toHex(header)} + 256B (كتابة صفحة 0x%06X)".format(address))
        }
        val errHdr = transport.writeBytes(header, clearStaleRx = true, onLog = onLog)
        if (errHdr != null) {
            onLog("TX ERROR (write_page header 0x%06X): $errHdr".format(address))
            return false
        }
        val errData = transport.writeBytes(payload, clearStaleRx = true, onLog = onLog)
        if (errData != null) {
            onLog("TX ERROR (write_page data 0x%06X): $errData".format(address))
            return false
        }

        val st = getStatus(transport, onLog, quiet = !verboseFrameLog) ?: return false
        if (st.sr4ProgramError) {
            onLog("Write Error @ 0x%06X: مؤشر SR4=1 (فشل كتابة الفلاش؛ تأكد من المسح المسبق).".format(address))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    /**
     * Equivalent to r8c::protocol::erase_page(uint32_t address)
     * Sends 4 bytes [0x20, A15..A8, A23..A16, 0xD0] -> get_status() SR5 == 0 -> clear_status().
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
        onLog("TX: ${toHex(cmd)} (مسح بلوك 0x%06X)".format(address))
        val err = transport.writeBytes(cmd, clearStaleRx = true, onLog = onLog)
        if (err != null) {
            onLog("TX ERROR (erase_page 0x%06X): $err".format(address))
            return false
        }

        delay(25)
        val st = getStatus(transport, onLog, quiet = true) ?: return false
        if (st.sr5EraseError) {
            onLog("Erase Error @ 0x%06X: مؤشر SR5=1 (فشل مسح البلوك). [SRD=0x%02X]".format(address, st.srd))
            return false
        }
        return clearStatus(transport, onLog, quiet = true)
    }

    /**
     * Auto-Detect & Bootloader Probe for R8C (with 400ms 0xB0 timeout for fast Universal Auto-Detect).
     */
    suspend fun probeAndIdentify(
        transport: SerialTransport,
        enableResetFlow: Boolean,
        idBytes: ByteArray,
        b0TimeoutMs: Int = 400,
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
     * Executes Locked-Chip Full Erase via R8C All-Block Erase command sequence (0xA7 then 0xD0)
     * when ID code is unknown / locked (id_state != 3).
     */
    suspend fun eraseAllViaA7D0(
        transport: SerialTransport,
        totalSpan: Int,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        onProgress((totalSpan * 0.25f).toInt(), totalSpan, "إرسال أمر مسح الشريحة وفك القفل (0xA7 ثم 0xD0)...")
        clearStatus(transport, onLog, quiet = false)
        delay(15)

        onLog("TX: A7 (طلب مسح كامل للشريحة المقفلة All-Block Erase)")
        val errA7 = transport.writeBytes(byteArrayOf(0xA7.toByte()), clearStaleRx = true, onLog = onLog)
        if (errA7 != null) {
            onLog("TX ERROR (0xA7): $errA7")
            return FlashOperationResult.Failure("فشل إرسال أمر 0xA7: $errA7")
        }
        delay(15)

        onLog("TX: D0 (تأكيد تنفيذ المسح الكلي Erase All Confirm)")
        val errD0 = transport.writeBytes(byteArrayOf(0xD0.toByte()), clearStaleRx = true, onLog = onLog)
        if (errD0 != null) {
            onLog("TX ERROR (0xD0): $errD0")
            return FlashOperationResult.Failure("فشل إرسال أمر 0xD0: $errD0")
        }

        var finalStatus: R8cStatus? = null
        for (attempt in 1..25) {
            delay(120)
            val frac = (0.30f + 0.65f * (attempt / 25f)).coerceAtMost(0.95f)
            onProgress((totalSpan * frac).toInt(), totalSpan, "انتظار اكتمال المسح الكلي (0xA7 + 0xD0)...")
            val st = getStatus(transport, onLog, quiet = (attempt > 1))
            if (st != null) {
                finalStatus = st
                val isReady = (st.srd and 0x80) != 0
                if (isReady) {
                    onLog("RX (0xA7/0xD0 Status): SRD=0x%02X, SRD1=0x%02X".format(st.srd, st.srd1))
                    break
                }
            }
        }

        if (finalStatus == null) {
            return FlashOperationResult.Failure("لم يستجب المعالج لحالة المسح الكلي بعد إرسال (0xA7 ثم 0xD0).")
        }
        if (finalStatus.sr5EraseError) {
            return FlashOperationResult.Failure(
                "فشل المسح الكلي عبر (0xA7 ثم 0xD0): مؤشر SR5=1 [SRD=0x%02X] — قد تكون حماية ROM مفعلة."
                    .format(finalStatus.srd)
            )
        }
        clearStatus(transport, onLog, quiet = true)
        onProgress(totalSpan, totalSpan, "اكتمل المسح الكلي وفك القفل")
        return FlashOperationResult.Success(
            "تم مسح الشريحة بالكامل وفك قفل الـ ID Code عبر الأمر (0xA7 ثم 0xD0) بنجاح — يمكنك الآن القراءة أو الكتابة بكود FF."
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
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 800, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C (16×0x00 + 0xB0). راجع سجل TX/RX.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        val ver = getVersion(transport, onLog)
            ?: return FlashOperationResult.Failure("تعذر قراءة إصدار البوتلودر (0xFB).")
        onLog("R8C Bootloader Version: \"$ver\" | نطاق القراءة: 0x%06X..0x%06X".format(layout.romArea.org, layout.romArea.end))

        onProgress(0, totalSize, "التحقق من مفتاح الحماية 7-Byte ID (0xF5)...")
        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure("فشل التحقق من مفتاح الحماية 7-Byte ID (id_state != 3). يمكنك استخدام زر المسح (Erase) لمسح الشريحة عبر 0xA7 + 0xD0.")
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

            onProgress(
                bytesRead,
                totalSize,
                "قراءة R8C: 0x%06X (${pageIdx + 1}/$totalPages)".format(physicalAddr)
            )
        }

        return FlashOperationResult.Success(
            message = "تمت قراءة ${bytesRead / 1024} KB من ${processor.partNumber} (0x%04X..0x%04X) عبر بروتوكول R8C بنجاح."
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
        val areasToErase = mutableListOf(layout.romArea).apply {
            if (!eraseMode.contains("ROM فقط")) {
                addAll(layout.dataAreas)
            }
        }
        val totalSpan = areasToErase.sumOf { it.sizeBytes }.coerceAtLeast(1024)

        onProgress(0, totalSpan, "مزامنة R8C قبل المسح...")
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 800, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل المسح.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        getVersion(transport, onLog)
        val (idOk, st) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            onLog("تنبيه: الشريحة مقفلة بمفتاح ID خاص (id_state=${st?.idState ?: "?"} != 3) — تنفيذ المسح الكلي عبر (0xA7 ثم 0xD0)...")
            return eraseAllViaA7D0(
                transport = transport,
                totalSpan = totalSpan,
                onLog = onLog,
                onProgress = onProgress
            )
        }

        // Collect unique erase block base addresses (1KB for < 0x8000, 4KB for >= 0x8000 as in r8c_prog.hpp)
        val blockAddresses = LinkedHashSet<Int>()
        for (area in areasToErase) {
            var addr = area.org and 0xFFFFFF00.toInt()
            while (addr <= area.end) {
                val blockSize = if (addr < 0x8000) 1024 else 4096
                val alignedBase = addr and (blockSize - 1).inv()
                blockAddresses.add(alignedBase)
                addr += 256
            }
        }

        val totalBlocks = blockAddresses.size.coerceAtLeast(1)
        blockAddresses.forEachIndexed { idx, blockAddr ->
            val ok = erasePage(transport, blockAddr, onLog)
            if (!ok) {
                return FlashOperationResult.Failure("فشل مسح البلوك 0x%06X.".format(blockAddr))
            }
            val doneBytes = ((idx + 1).toLong() * totalSpan / totalBlocks).toInt()
            onProgress(
                doneBytes,
                totalSpan,
                "مسح بلوك ${idx + 1}/$totalBlocks (0x%06X)".format(blockAddr)
            )
        }

        onProgress(totalSpan, totalSpan, "اكتمل مسح الفلاش")
        return FlashOperationResult.Success(
            message = "تم مسح $totalBlocks بلوك (${layout.romArea.org.toString(16).uppercase()}..${layout.romArea.end.toString(16).uppercase()}) بنجاح."
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
        if (!startAndConnect(transport, enableResetFlow, b0TimeoutMs = 800, onLog = onLog)) {
            return FlashOperationResult.Failure("فشلت المزامنة التسلسلية R8C قبل العملية.")
        }
        if (!changeSpeed(transport, baudRate, onLog)) {
            return FlashOperationResult.Failure("فشل تحويل سرعة الباود إلى $baudRate bps.")
        }
        val ver = getVersion(transport, onLog)
            ?: return FlashOperationResult.Failure("تعذر قراءة إصدار البوتلودر (0xFB).")
        onLog("R8C Bootloader: \"$ver\" | قاعدة العنوان: 0x%06X".format(baseAddr))

        val (idOk, _) = idInspection(transport, idBytes, onLog)
        if (!idOk) {
            return FlashOperationResult.Failure("فشل التحقق من 7-Byte ID Code (0xF5).")
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
                    "تخطي صفحة فارغة 0x%06X".format(physicalAddr)
                )
                continue
            }

            if (verifyOnly) {
                val chipPage = readPage(
                    transport = transport,
                    address = physicalAddr,
                    onLog = onLog,
                    verboseFrameLog = (pageIdx == 0)
                ) ?: return FlashOperationResult.Failure("تعذر قراءة الصفحة 0x%06X للمطابقة.".format(physicalAddr))

                for (i in 0 until len) {
                    if (chipPage[i] != slice[i]) {
                        val mismatchMsg = "اختلاف المطابقة عند 0x%06X: المتوقع 0x%02X والمقروء 0x%02X."
                            .format(physicalAddr + i, slice[i].toInt() and 0xFF, chipPage[i].toInt() and 0xFF)
                        onLog("Verify Mismatch: $mismatchMsg")
                        return FlashOperationResult.Failure(mismatchMsg)
                    }
                }
                onProgress(
                    bytesDone,
                    totalSize,
                    "مطابقة صفحة ${pageIdx + 1}/$totalPages (0x%06X)".format(physicalAddr)
                )
            } else {
                // Erase enclosing 1KB (< 0x8000) or 4KB (>= 0x8000) block once before writing its pages
                val blockSize = if (physicalAddr < 0x8000) 1024 else 4096
                val blockBase = physicalAddr and (blockSize - 1).inv()
                if (erasedBlocks.add(blockBase)) {
                    onProgress(
                        bytesDone.coerceAtLeast(1) - 1,
                        totalSize,
                        "مسح بلوك 0x%06X قبل الكتابة...".format(blockBase)
                    )
                    if (!erasePage(transport, blockBase, onLog)) {
                        return FlashOperationResult.Failure("فشل مسح البلوك 0x%06X قبل البرمجة.".format(blockBase))
                    }
                }

                val ok = writePage(
                    transport = transport,
                    address = physicalAddr,
                    data256 = slice,
                    onLog = onLog,
                    verboseFrameLog = (pageIdx == 0)
                )
                if (!ok) {
                    return FlashOperationResult.Failure("فشلت كتابة الصفحة عند 0x%06X.".format(physicalAddr))
                }
                onProgress(
                    bytesDone,
                    totalSize,
                    "كتابة صفحة ${pageIdx + 1}/$totalPages (0x%06X)".format(physicalAddr)
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
