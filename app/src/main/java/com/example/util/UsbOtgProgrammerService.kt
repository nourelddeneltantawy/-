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

class UsbOtgProgrammerService(private val context: Context) {
    companion object {
        private const val TAG = "UsbOtgProgrammer"
        private const val ACTION_USB_PERMISSION = "com.example.USB_PROGRAMMER_PERMISSION"
    }

    private val appContext = context.applicationContext
    private val usbManager: UsbManager? = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private var activeDevice: UsbDevice? = null
    private var activeConnection: UsbDeviceConnection? = null
    private val claimedInterfaces = mutableListOf<UsbInterface>()
    private var controlInterfaceIndex: Int = 0
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var activeChipType: ProgrammerChipType? = null

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
        inEndpoint = endpoints.first
        outEndpoint = endpoints.second
        activeChipType = ProgrammerChipType.fromVendorId(targetDevice.vendorId)

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
     * Exact PC-grade Renesas FDT / R8C Flasher Reset & Bit-Rate Sync Algorithm:
     * 1. Configure 9600 Baud, 8N1, Flow Control OFF.
     * 2. Assert DTR=true & RTS=true (RESET=LOW, MODE=LOW) -> Wait 100ms.
     * 3. De-assert DTR=false while keeping RTS=true (RESET=HIGH, MODE=LOW) -> Wait 150ms.
     * 4. Flush Input Buffer.
     * 5. Send 16 bytes of 0x00 with 25ms interval between each byte -> Wait 50ms.
     * 6. Send confirmation 0xB0 (and fallback 0x00) and inspect RX ACK (0xB0 / 0x06 / 0x80).
     */
    private suspend fun performPcExactSyncHandshake(
        onLog: (String) -> Unit
    ): Boolean {
        val conn = activeConnection ?: return false
        val dev = activeDevice ?: return false

        onLog("1) تهيئة المنفذ: 9600 Baud, 8N1, بدون Flow Control...")
        configureUartHardware(conn, dev, activeChipType, 9600)

        onLog("2) تتابع Reset: وضع DTR=true (RESET=LOW) و RTS=true (MODE=LOW) ثم انتظار 100ms...")
        setControlLines(conn, dev, activeChipType, dtr = true, rts = true)
        delay(100)

        onLog("3) رفع RESET: وضع DTR=false (RESET=HIGH) مع إبقاء RTS=true (MODE=LOW) ثم انتظار 150ms...")
        setControlLines(conn, dev, activeChipType, dtr = false, rts = true)
        delay(150)

        onLog("4) تفريغ Input Buffer من أي بيانات قديمة...")
        drainInputBuffer(conn)

        onLog("5) إرسال المزامنة: 16 بايت 0x00 بفاصل 25ms بين كل بايت...")
        val zeroByte = byteArrayOf(0x00)
        for (i in 0 until 16) {
            if (!writeRawBytes(zeroByte)) {
                onLog("TX ERROR: تعذر إرسال بايت المزامنة #${i + 1}")
                return false
            }
            delay(25)
        }
        onLog("TX: 00 x16 (25ms interval)")

        delay(50)

        // Send baud-rate confirmation 0xB0 and read ACK
        val b0Reply = transmitAndLog(byteArrayOf(0xB0.toByte()), expectedMax = 8, timeoutMs = 250, onLog = onLog)
        if (b0Reply.isEmpty()) {
            // Secondary sync nudge 0x00 in case bootloader needed 17th edge
            transmitAndLog(byteArrayOf(0x00), expectedMax = 8, timeoutMs = 200, onLog = onLog)
        }
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

        performPcExactSyncHandshake(onLog)

        // Step 3a: Request R8C Bootloader Version (0xFB -> expect 8 bytes e.g. VER.2.00)
        onLog("إرسال طلب معلومات البوت لودر 0xFB (قراءة 8 بايت Version String)...")
        val versionReply = transmitAndLog(byteArrayOf(0xFB.toByte()), expectedMax = 16, timeoutMs = 450, onLog = onLog)
        val versionAscii = toPrintableAscii(versionReply)
        if (versionReply.isNotEmpty()) {
            onLog("إصدار البوت لودر: \"$versionAscii\" [${toHex(versionReply)}]")
        }

        // Step 3b: Attempt R8C default 0xFF ID unlock + status 0x70 + read info pages (0x00FE00 / 0x00FF00)
        var matchedMcu: McuProcessor? = matchCatalogBySignature(versionAscii, toHex(versionReply), catalog)
        val combinedSignatureData = ByteArrayOutputStream()
        combinedSignatureData.write(versionReply)

        onLog("محاولة فك القفل الافتراضي 0xF5 (0xDF 0xFF 0x00 0x07 + 7×FF) لقراءة صفحة الهوية...")
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
            // Read page 0x00FE00 (0x00, 0xFE, 0x00)
            onLog("قراءة صفحة المعلومات 0xFF للعنوان 0x00FE00...")
            val pageFe = transmitAndLog(
                byteArrayOf(0xFF.toByte(), 0x00.toByte(), 0xFE.toByte(), 0x00.toByte()),
                expectedMax = 256,
                timeoutMs = 500,
                onLog = onLog
            )
            if (pageFe.isNotEmpty()) {
                combinedSignatureData.write(pageFe)
                val asciiFe = toPrintableAscii(pageFe)
                matchedMcu = matchCatalogBySignature(asciiFe, toHex(pageFe), catalog)
            }

            // Read page 0x00FF00 (0x00, 0xFF, 0x00) if still unmatched
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
                    val asciiFf = toPrintableAscii(pageFf)
                    matchedMcu = matchCatalogBySignature(asciiFf, toHex(pageFf), catalog)
                }
            }
        }

        val allBytes = combinedSignatureData.toByteArray()
        if (allBytes.isNotEmpty()) {
            val allHex = toHex(allBytes.take(32).toByteArray())
            val msg = when {
                matchedMcu != null -> "تم التعرف على المعالج بنجاح: ${matchedMcu.partNumber} ($versionAscii)"
                versionAscii.isNotBlank() -> "استجاب بوت لودر R8C بنجاح ($versionAscii | RX: $allHex). اختر رقم المعالج من القائمة أو تابع للخطوة التالية."
                else -> "تم استلام رد من المعالج [RX: $allHex]."
            }
            return@withContext ChipAutoDetectResult.Matched(
                rawSignatureHex = allHex,
                matchedProcessor = matchedMcu,
                diagnosticMessage = msg
            )
        }

        // Step 3c: RL78 fallback inquiry (0x00 then 0xC0 + Silicon Signature frame)
        onLog("لم يرد على أوامر R8C، تجربة تسلسل RL78 (0x00 ثم 0xC0)...")
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
            "تم فتح المنفذ وتنفيذ Reset (DTR/RTS) والمزامنة (16×0x00 @ 25ms) ولكن RX لم يستلم أي بايتات. راجع سجل TX/RX وتأكد من أن المبرمجة في وضع Boot Mode وتوصيل TXD↔RXD."
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
        onProgress(0, totalSize, "مزامنة المعالج (Reset + 16×0x00)...")
        performPcExactSyncHandshake(onLog)

        if (processor.protocolType == ProtocolFamilyType.R8C_M16C) {
            if (idCodeBytes.size != 7) {
                return@withContext FlashOperationResult.Failure("كود ID Code يجب أن يتكون من 14 حرف Hex (7 بايت).")
            }
            onProgress(0, totalSize, "إرسال أمر فك القفل 0xF5...")
            val unlockOk = sendR8cUnlockFrame(idCodeBytes, onLog)
            if (!unlockOk) {
                return@withContext FlashOperationResult.Failure(
                    "فشل استجابة المعالج لأمر فك القفل (0xF5 ID Code Verify). راجع سجل TX/RX وتأكد من ID Code وتوصيل TXD/RXD/DTR/RTS."
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
            if (!writeRawBytes(cmd)) {
                return@withContext FlashOperationResult.Failure("انقطع الاتصال بالمنفذ عند العنوان 0x%06X.".format(addr))
            }
            val chunk = readBytesWithTimeout(expectedMax = blockSize, timeoutMs = 450)
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
        performPcExactSyncHandshake(onLog)

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
            return@withContext FlashOperationResult.Failure("لم يرد المعالج بتأكيد إتمام المسح (Timeout).")
        }
        onProgress(totalSize, totalSize, "اكتمل المسح")
        FlashOperationResult.Success("تم مسح ذاكرة الفلاش بنجاح (${toHex(ack)}).")
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
        performPcExactSyncHandshake(onLog)

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
                if (!writeRawBytes(readCmd)) {
                    return@withContext FlashOperationResult.Failure("فشل إرسال طلب المطابقة عند 0x%06X.".format(offset))
                }
                val chipBytes = readBytesWithTimeout(expectedMax = len, timeoutMs = 450)
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
                if (!writeRawBytes(header + slice)) {
                    return@withContext FlashOperationResult.Failure("فشل إرسال بيانات الصفحة 0x%06X عبر USB.".format(offset))
                }
                val ack = readBytesWithTimeout(expectedMax = 4, timeoutMs = 450)
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

    private fun transmitAndLog(
        txBytes: ByteArray,
        expectedMax: Int,
        timeoutMs: Int,
        onLog: (String) -> Unit
    ): ByteArray {
        onLog("TX: ${toHex(txBytes)}")
        if (!writeRawBytes(txBytes)) {
            onLog("TX ERROR: فشل الإرسال عبر USB")
            return ByteArray(0)
        }
        val rx = readBytesWithTimeout(expectedMax = expectedMax, timeoutMs = timeoutMs)
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
    ): Pair<UsbEndpoint, UsbEndpoint>? {
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        controlInterfaceIndex = 0

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
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
        baudRate: Int
    ) {
        try {
            when (chipType) {
                ProgrammerChipType.FT232RL -> {
                    conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500) // Reset SIO
                    conn.controlTransfer(0x40, 0x00, 0x0001, 0, null, 0, 500) // Purge RX
                    conn.controlTransfer(0x40, 0x00, 0x0002, 0, null, 0, 500) // Purge TX
                    conn.controlTransfer(0x40, 0x09, 0x0002, 0, null, 0, 500) // Latency timer = 2ms
                    val divisor = if (baudRate == 9600) 0x4138 else (3_000_000 / baudRate.coerceAtLeast(300))
                    conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500) // Baudrate
                    conn.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 500) // 8N1
                    conn.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 500) // Flow Control OFF
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
                    val flowNone = ByteArray(16)
                    conn.controlTransfer(0x41, 0x13, 0, 0, flowNone, flowNone.size, 500) // Flow OFF
                }
                ProgrammerChipType.CH340G -> {
                    conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500) // Init
                    // Exact Ch34xSerialDriver baud formula (1532620800 / baud)
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
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500) // 8N1 + TX/RX enable
                    conn.controlTransfer(0x40, 0x9A, 0x2727, 0x0000, null, 0, 500) // Flow OFF
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
                    // Matches Ch34xSerialDriver: ~((dtr ? 0x20 : 0) | (rts ? 0x40 : 0)) & 0xFF
                    val mask = (if (dtr) 0x20 else 0) or (if (rts) 0x40 else 0)
                    conn.controlTransfer(0x40, 0xA4, mask.inv() and 0xFF, 0, null, 0, 300)
                }
                else -> {
                    val valMask = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
                    conn.controlTransfer(0x21, 0x22, valMask, controlInterfaceIndex, null, 0, 300)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Control line warning (${device.deviceName}): ${e.message}")
        }
    }

    private fun writeRawBytes(data: ByteArray): Boolean {
        val conn = activeConnection ?: return false
        val out = outEndpoint ?: return false
        var offset = 0
        while (offset < data.size) {
            val chunkLen = min(data.size - offset, out.maxPacketSize.coerceAtLeast(64))
            val slice = if (offset == 0 && chunkLen == data.size) data
            else data.copyOfRange(offset, offset + chunkLen)
            val written = conn.bulkTransfer(out, slice, slice.size, 800)
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    /**
     * Loops until expectedMax bytes arrive OR timeout expires.
     * Critically ignores FTDI 2-byte status-only USB packets so we don't return prematurely!
     */
    private fun readBytesWithTimeout(expectedMax: Int, timeoutMs: Int): ByteArray {
        val conn = activeConnection ?: return ByteArray(0)
        val epIn = inEndpoint ?: return ByteArray(0)
        val packetSize = epIn.maxPacketSize.coerceAtLeast(64)
        val pktBuf = ByteArray(packetSize)
        val acc = ByteArrayOutputStream()
        val start = System.currentTimeMillis()
        val hardDeadline = start + timeoutMs.toLong()
        var lastDataTime = 0L

        while (acc.size() < expectedMax) {
            val now = System.currentTimeMillis()
            val remaining = (hardDeadline - now).toInt()
            if (remaining <= 0) break

            // Once we started receiving payload bytes, cap idle wait to 50ms to finish variable-length frames fast
            val perCallTimeout = if (lastDataTime > 0L) min(50, remaining) else min(80, remaining)
            val readLen = conn.bulkTransfer(epIn, pktBuf, pktBuf.size, perCallTimeout)

            if (readLen > 0) {
                if (activeChipType == ProgrammerChipType.FT232RL) {
                    if (readLen > 2) {
                        val payloadLen = min(readLen - 2, expectedMax - acc.size())
                        acc.write(pktBuf, 2, payloadLen)
                        lastDataTime = System.currentTimeMillis()
                    }
                    // If readLen <= 2 on FTDI, it's ONLY the 2 modem-status bytes; keep waiting!
                } else {
                    val payloadLen = min(readLen, expectedMax - acc.size())
                    acc.write(pktBuf, 0, payloadLen)
                    lastDataTime = System.currentTimeMillis()
                }
            } else if (lastDataTime > 0L && (System.currentTimeMillis() - lastDataTime) >= 45L) {
                break
            }
        }
        return acc.toByteArray()
    }

    private fun drainInputBuffer(conn: UsbDeviceConnection) {
        val epIn = inEndpoint ?: return
        val buf = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
        repeat(5) {
            val n = conn.bulkTransfer(epIn, buf, buf.size, 15)
            if (n <= 0) return
            if (activeChipType == ProgrammerChipType.FT232RL && n <= 2) return
        }
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun toPrintableAscii(bytes: ByteArray): String =
        bytes.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
}
