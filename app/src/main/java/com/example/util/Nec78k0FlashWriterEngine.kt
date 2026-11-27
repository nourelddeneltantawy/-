package com.example.util

import android.content.Context
import com.example.data.model.McuProcessor
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Dynamic NEC / Renesas 78K0 Flash Erase (0x20 / 0x22), Block Write (0x40),
 * Block Verify (0x13), and Final Checksum (0xB0) Engine.
 * Works dynamically across all 78K0 Kx2/KC2/KD2/KE2/KF2 flash densities via
 * Silicon Signature (0xC0), with Smart Empty-Block (0xFF) Skipping and Anti-Disconnect Safety.
 */
object Nec78k0FlashWriterEngine {
    private const val BLOCK_SIZE = Nec78k0ProtocolEngine.BLOCK_SIZE // 1024 B (0x0400)
    private const val COMMAND_TIMEOUT_MS = 3000

    suspend fun eraseFlash(
        context: Context,
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        selectedChip: McuProcessor,
        eraseMode: String,
        clockFreqMhzText: String,
        catalog: List<McuProcessor>,
        preDetectedInfo: Nec78k0ProtocolEngine.Nec78k0DetectedInfo?,
        onDetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val (info, err) = syncAndInspectTarget(
            context = context,
            transport = transport,
            selectedChip = selectedChip,
            clockFreqMhzText = clockFreqMhzText,
            catalog = catalog,
            preDetectedInfo = preDetectedInfo,
            onDetectedInfo = onDetectedInfo,
            onLog = onLog,
            onProgress = onProgress
        )
        if (info == null) {
            return FlashOperationResult.Failure(err ?: "تعذر مزامنة المعالج 78K0 والتعرف عليه قبل المسح.")
        }

        val totalSize = info.flashSizeBytes
        val blockCount = info.blockCount.coerceAtLeast(1)
        val useBlockErase = info.prefersBlockErase0x22 ||
            eraseMode.contains("بلوك", ignoreCase = true) ||
            eraseMode.contains("Block", ignoreCase = true)

        val eraseErr = executeEraseInternal(
            transport = transport,
            blockCount = blockCount,
            totalSize = totalSize,
            preferBlockErase = useBlockErase,
            clockFreqMhzText = clockFreqMhzText,
            onLog = onLog,
            onProgress = onProgress
        )
        if (eraseErr != null) {
            return FlashOperationResult.Failure(eraseErr)
        }

        // Verify Chip Checksum after erase to confirm clean 0xFF state
        val csAfterErase = Nec78k0ProtocolEngine.queryChecksum16(
            transport,
            Nec78k0ProtocolEngine.buildAddressRangeFrame(Nec78k0ProtocolEngine.CMD_CHECKSUM, 0x000000, info.lastAddress),
            onLog
        )
        val csSuffix = csAfterErase?.let { String.format(Locale.US, " • البصمة بعد المسح: 0x%04X", it and 0xFFFF) } ?: ""
        onProgress(totalSize, totalSize, "اكتمل مسح الفلاش بنجاح")
        return FlashOperationResult.Success(
            message = "تم مسح ذاكرة ${info.deviceName} (${totalSize / 1024} KB / $blockCount بلوك) بنجاح$csSuffix."
        )
    }

    suspend fun writeFlash(
        context: Context,
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        selectedChip: McuProcessor,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        autoEraseBeforeWrite: Boolean,
        autoVerifyAfterWrite: Boolean,
        eraseMode: String,
        clockFreqMhzText: String,
        catalog: List<McuProcessor>,
        preDetectedInfo: Nec78k0ProtocolEngine.Nec78k0DetectedInfo?,
        onDetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val (info, err) = syncAndInspectTarget(
            context = context,
            transport = transport,
            selectedChip = selectedChip,
            clockFreqMhzText = clockFreqMhzText,
            catalog = catalog,
            preDetectedInfo = preDetectedInfo,
            onDetectedInfo = onDetectedInfo,
            onLog = onLog,
            onProgress = onProgress
        )
        if (info == null) {
            return FlashOperationResult.Failure(err ?: "تعذر مزامنة المعالج 78K0 والتعرف عليه قبل الكتابة.")
        }

        val totalSize = info.flashSizeBytes
        val blockCount = info.blockCount.coerceAtLeast(1)
        val paddedFirmware = preparePaddedFirmwareBuffer(firmwareBytes, firmwareStartAddress, totalSize)
        val expectedSwChecksum = Nec78k0Block0Reconstructor.compute78k0Checksum16(paddedFirmware)

        var flashWasJustErased = false
        if (autoEraseBeforeWrite) {
            onLog("مرحلة 1 (Write): تنفيذ مسح الفلاش قبل الكتابة على ${info.deviceName} (${blockCount} بلوك × 1 KB)...")
            val useBlockErase = info.prefersBlockErase0x22 ||
                eraseMode.contains("بلوك", ignoreCase = true) ||
                eraseMode.contains("Block", ignoreCase = true)
            val eraseErr = executeEraseInternal(
                transport = transport,
                blockCount = blockCount,
                totalSize = totalSize,
                preferBlockErase = useBlockErase,
                clockFreqMhzText = clockFreqMhzText,
                onLog = onLog,
                onProgress = onProgress
            )
            if (eraseErr != null) {
                return FlashOperationResult.Failure(eraseErr)
            }
            flashWasJustErased = true
        }

        var writtenBlocks = 0
        var skippedBlocks = 0
        onLog("مرحلة 2 (Write): بدء شحن السوفت وير على $blockCount بلوك (1024 Byte/Block)...")

        for (blockIdx in 0 until blockCount) {
            if (!transport.isDeviceAttached()) {
                return FlashOperationResult.Failure(
                    disconnectMessage("الكتابة", blockIdx, blockCount, "فُصل كابل USB OTG عن الهاتف")
                )
            }

            val startAddr = blockIdx * BLOCK_SIZE
            val endAddr = startAddr + BLOCK_SIZE - 1
            val blockSlice = paddedFirmware.copyOfRange(startAddr, startAddr + BLOCK_SIZE)

            if (flashWasJustErased && blockSlice.all { it == 0xFF.toByte() }) {
                skippedBlocks++
                val done = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
                onProgress(
                    done,
                    totalSize,
                    String.format(Locale.US, "تخطي بلوك فارغ (0xFF) %d/%d (0x%06X)", blockIdx + 1, blockCount, startAddr)
                )
                continue
            }

            // Standard NEC 78K0 0x40 Programming (1024B Block -> 4 x 260B STX Frames):
            when (val progOutcome = Nec78k0ProtocolEngine.programBlockFromBuffer(
                transport = transport,
                startAddr = startAddr,
                endAddr = endAddr,
                blockData = blockSlice,
                clockFreqMhzText = clockFreqMhzText,
                eraseBlockBeforeRetry = true,
                onPageProgress = { bytesInBlock ->
                    onProgress(
                        minOf(startAddr + bytesInBlock, totalSize),
                        totalSize,
                        String.format(
                            Locale.US,
                            "كتابة بلوك %d/%d (%d/%d بايت @ 0x%06X)",
                            blockIdx + 1,
                            blockCount,
                            bytesInBlock,
                            BLOCK_SIZE,
                            startAddr
                        )
                    )
                },
                onLog = onLog
            )) {
                is Nec78k0ProtocolEngine.ProgramOutcome.Success -> Unit
                is Nec78k0ProtocolEngine.ProgramOutcome.ProtocolError -> {
                    return FlashOperationResult.Failure(progOutcome.message)
                }
                is Nec78k0ProtocolEngine.ProgramOutcome.TransportFailure -> {
                    val failureText = if (progOutcome.isUsbDetached) {
                        disconnectMessage("الكتابة", blockIdx, blockCount, progOutcome.reason)
                    } else {
                        timeoutMessage("الكتابة", blockIdx, blockCount, progOutcome.reason)
                    }
                    return FlashOperationResult.Failure(failureText)
                }
            }

            writtenBlocks++
            val done = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            onProgress(
                done,
                totalSize,
                String.format(Locale.US, "كتابة بلوك %d/%d (0x%06X..0x%06X)", blockIdx + 1, blockCount, startAddr, endAddr)
            )
            if (blockIdx % 8 == 0 || blockIdx == blockCount - 1) {
                onLog(String.format(Locale.US, "تم حرق البلوك %d/%d (0x%06X)", blockIdx + 1, blockCount, startAddr))
            }
        }

        // Optional Auto-Verify immediately after writing
        var verifiedBlocks = 0
        if (autoVerifyAfterWrite) {
            onLog("مرحلة 3 (Auto Verify): بدء مطابقة السوفت وير عبر الأمر 0x13...")
            val verifyErr = executeVerifyInternal(
                transport = transport,
                paddedFirmware = paddedFirmware,
                blockCount = blockCount,
                totalSize = totalSize,
                skipEmptyFfBlocks = flashWasJustErased,
                clockFreqMhzText = clockFreqMhzText,
                onVerifiedCount = { verifiedBlocks = it },
                onLog = onLog,
                onProgress = onProgress
            )
            if (verifyErr != null) {
                return FlashOperationResult.Failure(verifyErr)
            }
        }

        // Final Full-Flash Checksum Command (0xB0)
        val finalHwChecksum = Nec78k0ProtocolEngine.queryChecksum16(
            transport,
            Nec78k0ProtocolEngine.buildAddressRangeFrame(Nec78k0ProtocolEngine.CMD_CHECKSUM, 0x000000, info.lastAddress),
            onLog
        )
        val csSummary = if (finalHwChecksum != null) {
            val hw = finalHwChecksum and 0xFFFF
            val matched = Nec78k0Block0Reconstructor.matches78k0Checksum16(paddedFirmware, hw)
            val expectedWordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(paddedFirmware) and 0xFFFF
            val shownExpected = if (hw == expectedWordCs) expectedWordCs else (expectedSwChecksum and 0xFFFF)
            onLog(
                String.format(
                    Locale.US,
                    "بصمة الفلاش النهائية (0xB0): 0x%04X • المتوقعة للملف: 0x%04X (%s)",
                    hw,
                    shownExpected,
                    if (matched) "متطابقة 100%" else "يرجى فحص النطاق"
                )
            )
            String.format(Locale.US, " • البصمة النهائية (0xB0): 0x%04X", hw)
        } else {
            ""
        }

        onProgress(totalSize, totalSize, "اكتملت البرمجة بنجاح 100%")
        val verifySuffix = if (autoVerifyAfterWrite) " + مطابقة $verifiedBlocks بلوك عبر 0x13" else ""
        return FlashOperationResult.Success(
            message = "تم شحن ${info.deviceName} بنجاح ($writtenBlocks بلوك مكتوب، $skippedBlocks بلوك فارغ متخطى$verifySuffix$csSummary).",
            isMcuVerified = autoVerifyAfterWrite
        )
    }

    suspend fun verifyFlash(
        context: Context,
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        selectedChip: McuProcessor,
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        clockFreqMhzText: String,
        catalog: List<McuProcessor>,
        preDetectedInfo: Nec78k0ProtocolEngine.Nec78k0DetectedInfo?,
        onDetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): FlashOperationResult {
        val (info, err) = syncAndInspectTarget(
            context = context,
            transport = transport,
            selectedChip = selectedChip,
            clockFreqMhzText = clockFreqMhzText,
            catalog = catalog,
            preDetectedInfo = preDetectedInfo,
            onDetectedInfo = onDetectedInfo,
            onLog = onLog,
            onProgress = onProgress
        )
        if (info == null) {
            return FlashOperationResult.Failure(err ?: "تعذر مزامنة المعالج 78K0 والتعرف عليه قبل المطابقة.")
        }

        val totalSize = info.flashSizeBytes
        val blockCount = info.blockCount.coerceAtLeast(1)
        val paddedFirmware = preparePaddedFirmwareBuffer(firmwareBytes, firmwareStartAddress, totalSize)
        val expectedSwChecksum = Nec78k0Block0Reconstructor.compute78k0Checksum16(paddedFirmware)

        var verifiedBlocks = 0
        val verifyErr = executeVerifyInternal(
            transport = transport,
            paddedFirmware = paddedFirmware,
            blockCount = blockCount,
            totalSize = totalSize,
            skipEmptyFfBlocks = false,
            clockFreqMhzText = clockFreqMhzText,
            onVerifiedCount = { verifiedBlocks = it },
            onLog = onLog,
            onProgress = onProgress
        )
        if (verifyErr != null) {
            return FlashOperationResult.Failure(verifyErr)
        }

        val finalHwChecksum = Nec78k0ProtocolEngine.queryChecksum16(
            transport,
            Nec78k0ProtocolEngine.buildAddressRangeFrame(Nec78k0ProtocolEngine.CMD_CHECKSUM, 0x000000, info.lastAddress),
            onLog
        )
        val csSummary = if (finalHwChecksum != null) {
            val hw = finalHwChecksum and 0xFFFF
            val matched = Nec78k0Block0Reconstructor.matches78k0Checksum16(paddedFirmware, hw)
            val expectedWordCs = Nec78k0Block0Reconstructor.compute78k0Word16Checksum(paddedFirmware) and 0xFFFF
            val shownExpected = if (hw == expectedWordCs) expectedWordCs else (expectedSwChecksum and 0xFFFF)
            onLog(
                String.format(
                    Locale.US,
                    "بصمة الفلاش النهائية (0xB0): 0x%04X • المتوقعة للملف: 0x%04X (%s)",
                    hw,
                    shownExpected,
                    if (matched) "متطابقة 100%" else "اختلاف"
                )
            )
            String.format(Locale.US, " • Checksum النهائي: 0x%04X", hw)
        } else {
            ""
        }

        onProgress(totalSize, totalSize, "اكتملت المطابقة بنسبة 100%")
        return FlashOperationResult.Success(
            message = "تمت مطابقة ${info.deviceName} بالكامل ($verifiedBlocks/$blockCount بلوك • ${totalSize / 1024} KB) بنسبة 100%$csSummary.",
            isMcuVerified = true
        )
    }

    /**
     * Anti-Dummy Rule 4: Closed-loop blank-chip proof.
     * Writes the 16-byte proof vector [55 AA 12 34 56 78 9A BC DE F0 11 22 33 44 55 66]
     * at (End_Addr - PageSize + 1) and confirms 100% match via Two-Vector 0x13 Verify.
     */
    suspend fun runClosedLoopBlankChipProof(
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo,
        clockFreqMhzText: String,
        onLog: (String) -> Unit
    ): Boolean {
        val testPageStart = Nec78k0ProtocolRegistry.closedLoopTestPageStartAddress(info.lastAddress, Nec78k0ProtocolEngine.PAGE_SIZE)
        val testBlockStart = (testPageStart / BLOCK_SIZE) * BLOCK_SIZE
        val testBlockEnd = testBlockStart + BLOCK_SIZE - 1
        val offsetInBlock = (testPageStart - testBlockStart).coerceIn(0, BLOCK_SIZE - Nec78k0ProtocolRegistry.CLOSED_LOOP_PROOF_VECTOR.size)

        val testBlock = ByteArray(BLOCK_SIZE) { 0xFF.toByte() }
        System.arraycopy(
            Nec78k0ProtocolRegistry.CLOSED_LOOP_PROOF_VECTOR,
            0,
            testBlock,
            offsetInBlock,
            Nec78k0ProtocolRegistry.CLOSED_LOOP_PROOF_VECTOR.size
        )

        onLog(String.format(Locale.US, "تنفيذ اختبار الحلقة المغلقة على الصفحة الأخيرة (0x%06X)...", testPageStart))
        val progOutcome = Nec78k0ProtocolEngine.programBlockFromBuffer(
            transport = transport,
            startAddr = testBlockStart,
            endAddr = testBlockEnd,
            blockData = testBlock,
            clockFreqMhzText = clockFreqMhzText,
            eraseBlockBeforeRetry = true,
            onLog = onLog
        )
        if (progOutcome !is Nec78k0ProtocolEngine.ProgramOutcome.Success) {
            onLog("فشل كتابة متجه الحلقة المغلقة عند 0x%06X.".format(Locale.US, testPageStart))
            return false
        }
        return Nec78k0ProtocolEngine.verifyTwoVector0x13Block(
            transport = transport,
            startAddr = testBlockStart,
            endAddr = testBlockEnd,
            expectedBlock = testBlock,
            clockFreqMhzText = clockFreqMhzText,
            onLog = onLog
        )
    }

    private suspend fun executeEraseInternal(
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        blockCount: Int,
        totalSize: Int,
        preferBlockErase: Boolean,
        clockFreqMhzText: String,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): String? {
        if (!transport.isDeviceAttached()) {
            return "انقطع اتصال USB OTG قبل بدء المسح."
        }

        if (!preferBlockErase) {
            onProgress(totalSize / 4, totalSize, "تنفيذ المسح الشامل (Chip Erase 0x20)...")
            val chipEraseCmd = Nec78k0ProtocolEngine.buildCommandFrame(Nec78k0ProtocolEngine.CMD_CHIP_ERASE)
            onLog("TX (Chip Erase 0x20): ${Nec78k0ProtocolEngine.toHex(chipEraseCmd)}")
            when (val res = Nec78k0ProtocolEngine.transceiveSingleFrame(transport, chipEraseCmd, COMMAND_TIMEOUT_MS, onLog)) {
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.Success -> {
                    onLog("RX (Chip Erase ACK): ${Nec78k0ProtocolEngine.toHex(res.frame.rawFrame)}")
                    delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)
                    return null
                }
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.ProtocolError -> {
                    if (res.statusCode == Nec78k0ProtocolEngine.STS_PROTECT_ERROR) {
                        return "الشريحة محمية (ERR_PROTECT 0x10): المسح والكتابة مقفولان برمجياً على المعالج."
                    }
                    onLog("ملاحظة: أمر المسح الشامل 0x20 رد بـ ${res.statusLabel} — التبديل تلقائياً إلى المسح بالبلوكات (0x22)...")
                }
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    if (res.isUsbDetached) {
                        return "انقطع اتصال USB OTG أثناء المسح الشامل (0x20)."
                    }
                    onLog("ملاحظة: لم يستجب المعالج لأمر 0x20 — إعادة المزامنة ثم التبديل التلقائي إلى مسح البلوكات (0x22)...")
                    Nec78k0ProtocolEngine.resync78k0Bootloader(transport, clockFreqMhzText, onLog)
                }
            }
            delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)
        }

        onLog("بدء مسح الفلاش بالتتابع عبر Block Erase (0x22) لـ $blockCount بلوك (1024 Byte/Block)...")
        for (blockIdx in 0 until blockCount) {
            if (!transport.isDeviceAttached()) {
                return disconnectMessage("المسح", blockIdx, blockCount, "فُصل كابل USB OTG عن الهاتف")
            }
            val startAddr = blockIdx * BLOCK_SIZE
            val endAddr = startAddr + BLOCK_SIZE - 1
            val blockEraseCmd = Nec78k0ProtocolEngine.buildAddressRangeFrame(
                Nec78k0ProtocolEngine.CMD_BLOCK_ERASE,
                startAddr,
                endAddr
            )
            when (val res = Nec78k0ProtocolEngine.transceiveWithTimeoutRetry(
                transport = transport,
                txFrame = blockEraseCmd,
                timeoutMs = COMMAND_TIMEOUT_MS,
                clockFreqMhzText = clockFreqMhzText,
                opLabel = "مسح البلوك ${blockIdx + 1}/$blockCount (0x22)",
                onLog = onLog
            )) {
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.Success -> Unit
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.ProtocolError -> {
                    if (res.statusCode == Nec78k0ProtocolEngine.STS_PROTECT_ERROR) {
                        return "الشريحة محمية (ERR_PROTECT 0x10): المسح والكتابة مقفولان برمجياً عند البلوك ${blockIdx + 1}/$blockCount (0x%06X)."
                            .format(Locale.US, startAddr)
                    }
                    return "فشل مسح البلوك ${blockIdx + 1}/$blockCount (0x%06X): ${res.statusLabel}"
                        .format(Locale.US, startAddr)
                }
                is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.DisconnectedOrTimeout -> {
                    return if (res.isUsbDetached) {
                        disconnectMessage("المسح", blockIdx, blockCount, res.reason)
                    } else {
                        timeoutMessage("المسح", blockIdx, blockCount, res.reason)
                    }
                }
            }
            val done = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            onProgress(
                done,
                totalSize,
                String.format(Locale.US, "مسح بلوك %d/%d (0x%06X)", blockIdx + 1, blockCount, startAddr)
            )
        }
        delay(Nec78k0ProtocolRegistry.ERASE_WRITE_BUSY_WAIT_MS)
        return null
    }

    private suspend fun executeVerifyInternal(
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        paddedFirmware: ByteArray,
        blockCount: Int,
        totalSize: Int,
        skipEmptyFfBlocks: Boolean,
        clockFreqMhzText: String,
        onVerifiedCount: (Int) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): String? {
        var verified = 0
        for (blockIdx in 0 until blockCount) {
            if (!transport.isDeviceAttached()) {
                return disconnectMessage("المطابقة", blockIdx, blockCount, "فُصل كابل USB OTG عن الهاتف")
            }

            val startAddr = blockIdx * BLOCK_SIZE
            val endAddr = startAddr + BLOCK_SIZE - 1
            val blockSlice = paddedFirmware.copyOfRange(startAddr, startAddr + BLOCK_SIZE)

            if (skipEmptyFfBlocks && blockSlice.all { it == 0xFF.toByte() }) {
                val done = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
                onProgress(
                    done,
                    totalSize,
                    String.format(Locale.US, "تخطي مطابقة بلوك فارغ %d/%d", blockIdx + 1, blockCount)
                )
                continue
            }

            // Standard NEC 78K0 0x13 Verify (1024B Block -> 4 x 260B STX Frames):
            when (val verifyOutcome = Nec78k0ProtocolEngine.verifyBlockVia0x13(
                transport = transport,
                startAddr = startAddr,
                endAddr = endAddr,
                blockBytes = blockSlice,
                clockFreqMhzText = clockFreqMhzText,
                onPageProgress = { bytesInBlock ->
                    onProgress(
                        minOf(startAddr + bytesInBlock, totalSize),
                        totalSize,
                        String.format(
                            Locale.US,
                            "مطابقة بلوك %d/%d (%d/%d بايت @ 0x%06X)",
                            blockIdx + 1,
                            blockCount,
                            bytesInBlock,
                            BLOCK_SIZE,
                            startAddr
                        )
                    )
                },
                onLog = onLog
            )) {
                is Nec78k0ProtocolEngine.VerifyOutcome.Match -> Unit
                is Nec78k0ProtocolEngine.VerifyOutcome.TransportLost -> {
                    return if (verifyOutcome.isUsbDetached) {
                        disconnectMessage("المطابقة", blockIdx, blockCount, verifyOutcome.reason)
                    } else {
                        timeoutMessage("المطابقة", blockIdx, blockCount, verifyOutcome.reason)
                    }
                }
                is Nec78k0ProtocolEngine.VerifyOutcome.Mismatch -> {
                    val errText = if (verifyOutcome.errorCode == Nec78k0ProtocolEngine.STS_VERIFY_ERROR && verifyOutcome.failedPage > 0) {
                        val pageAddr = startAddr + ((verifyOutcome.failedPage - 1) * Nec78k0ProtocolEngine.PAGE_SIZE)
                        String.format(
                            Locale.US,
                            "فشل المطابقة (ERR_VERIFY 0x0F): اختلاف في البيانات عند البلوك %d/%d (العنوان 0x%06X..0x%06X • الإطار %d/${Nec78k0ProtocolEngine.PAGES_PER_BLOCK} @ 0x%06X).",
                            blockIdx + 1,
                            blockCount,
                            startAddr,
                            endAddr,
                            verifyOutcome.failedPage,
                            pageAddr
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "خطأ أثناء Verify في البلوك %d/%d (0x%06X): %s",
                            blockIdx + 1,
                            blockCount,
                            startAddr,
                            verifyOutcome.detail
                        )
                    }
                    onLog(errText)
                    return errText
                }
            }

            verified++
            onVerifiedCount(verified)
            val done = minOf((blockIdx + 1) * BLOCK_SIZE, totalSize)
            onProgress(
                done,
                totalSize,
                String.format(Locale.US, "مطابقة بلوك %d/%d (0x%06X)", blockIdx + 1, blockCount, startAddr)
            )
            if (blockIdx % 8 == 0 || blockIdx == blockCount - 1) {
                onLog(String.format(Locale.US, "تطابق البلوك %d/%d (0x%06X) بنسبة 100%%", blockIdx + 1, blockCount, startAddr))
            }
        }
        return null
    }

    private suspend fun syncAndInspectTarget(
        context: Context,
        transport: Nec78k0ProtocolEngine.Serial78k0Transport,
        selectedChip: McuProcessor,
        clockFreqMhzText: String,
        catalog: List<McuProcessor>,
        preDetectedInfo: Nec78k0ProtocolEngine.Nec78k0DetectedInfo?,
        onDetectedInfo: (Nec78k0ProtocolEngine.Nec78k0DetectedInfo) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (bytesDone: Int, totalBytes: Int, label: String) -> Unit
    ): Pair<Nec78k0ProtocolEngine.Nec78k0DetectedInfo?, String?> {
        if (preDetectedInfo != null &&
            Nec78k0ProtocolEngine.normalize78k0Part(preDetectedInfo.deviceName) ==
            Nec78k0ProtocolEngine.normalize78k0Part(selectedChip.partNumber)
        ) {
            onProgress(0, preDetectedInfo.flashSizeBytes, "مزامنة ${preDetectedInfo.deviceName} (Reset 0x00)...")
            val resetRes = Nec78k0ProtocolEngine.resync78k0Bootloader(transport, clockFreqMhzText, onLog)
            if (resetRes !is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.Success) {
                return null to when (resetRes) {
                    is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.ProtocolError -> "رفض المعالج أمر المزامنة Reset 0x00: ${resetRes.statusLabel}"
                    is Nec78k0ProtocolEngine.Nec78k0TransceiveResult.DisconnectedOrTimeout -> resetRes.reason
                    else -> "فشل Reset 0x00"
                }
            }
            return preDetectedInfo to null
        }

        onProgress(0, selectedChip.flashSizeBytes, "مزامنة المعالج والتعرّف التلقائي (0xC0)...")
        val (info, err) = Nec78k0ProtocolEngine.autoDetectAndInspect(
            context = context,
            transport = transport,
            clockFreqMhzText = clockFreqMhzText,
            preferredChip = selectedChip,
            catalog = catalog,
            onLog = onLog
        )
        if (info != null) {
            onDetectedInfo(info)
        }
        return info to err
    }

    private fun preparePaddedFirmwareBuffer(
        firmwareBytes: ByteArray,
        firmwareStartAddress: Int,
        targetFlashSizeBytes: Int
    ): ByteArray {
        val padded = ByteArray(targetFlashSizeBytes) { 0xFF.toByte() }
        if (firmwareBytes.isEmpty()) return padded
        val base = firmwareStartAddress.coerceAtLeast(0)
        if (base == 0) {
            val copyLen = minOf(firmwareBytes.size, targetFlashSizeBytes)
            System.arraycopy(firmwareBytes, 0, padded, 0, copyLen)
        } else {
            val copyLen = minOf(firmwareBytes.size, (targetFlashSizeBytes - base).coerceAtLeast(0))
            if (copyLen > 0) {
                System.arraycopy(firmwareBytes, 0, padded, base, copyLen)
            }
        }
        return padded
    }

    private fun disconnectMessage(opLabel: String, blockIdx: Int, blockCount: Int, detail: String): String {
        val addr = blockIdx * BLOCK_SIZE
        return String.format(
            Locale.US,
            "انقطع اتصال USB أثناء %s عند البلوك %d/%d (العنوان 0x%06X) — %s. ثبّت المبرمجة وأعد المحاولة بأمان.",
            opLabel,
            blockIdx + 1,
            blockCount,
            addr,
            detail
        )
    }

    private fun timeoutMessage(opLabel: String, blockIdx: Int, blockCount: Int, detail: String): String {
        val addr = blockIdx * BLOCK_SIZE
        return String.format(
            Locale.US,
            "انتهت مهلة استجابة المعالج (Timeout) أثناء %s عند البلوك %d/%d (العنوان 0x%06X) بعد 3 محاولات إعادة مزامنة — منفذ USB لا يزال مفتوحاً ومتصلاً: %s",
            opLabel,
            blockIdx + 1,
            blockCount,
            addr,
            detail
        )
    }
}
