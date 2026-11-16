package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LogLevel
import com.example.data.model.McuProcessor
import com.example.data.model.ProgrammerChipType
import com.example.data.model.ProgrammerLogEntry
import com.example.data.model.ProtocolFamilyType
import com.example.data.model.UsbSerialDeviceInfo
import com.example.data.repository.McuDatabaseRepository
import com.example.util.ChipAutoDetectResult
import com.example.util.FirmwareParser
import com.example.util.FlashOperationResult
import com.example.util.R8cProtocolEngine
import com.example.util.UsbConnectResult
import com.example.util.UsbOtgProgrammerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class McuProgrammerUiState(
    val isLoadingDb: Boolean = true,
    val dbError: String? = null,
    val loadedDbFiles: List<String> = emptyList(),
    val totalMcuCount: Int = 0,
    val currentStep: Int = 1,

    // Step 1: Search & Cascading Dropdowns
    val searchQuery: String = "",
    val searchMatches: List<McuProcessor> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val selectedManufacturer: String = "",
    val mainFamilies: List<String> = emptyList(),
    val selectedMainFamily: String = "",
    val subFamilies: List<String> = emptyList(),
    val selectedSubFamily: String = "",
    val partNumbers: List<McuProcessor> = emptyList(),
    val selectedProcessor: McuProcessor? = null,
    val hasUserSelectedFamily: Boolean = false,
    val isAutoDetecting: Boolean = false,
    val autoDetectStatus: String? = null,
    val autoDetectIsSuccess: Boolean = false,

    // Step 2: Programmer & USB OTG
    val selectedProgrammer: ProgrammerChipType = ProgrammerChipType.FT232RL,
    val attachedUsbDevices: List<UsbSerialDeviceInfo> = emptyList(),

    // Step 3: Dynamic Flasher Controls & PC-Style Hex Viewer
    val baudRate: Int = 9600,
    val enableDtrRtsReset: Boolean = true,
    val enableEchoCancellation: Boolean = true,
    val idCodeBytesHex: List<String> = List(7) { "FF" },
    val clockFrequency: String = "20 MHz",
    val eraseMode: String = "مسح كلي (Chip Erase)",
    val isConnected: Boolean = UsbOtgProgrammerService.isConnected(),
    val connectedDeviceLabel: String = UsbOtgProgrammerService.connectedLabelFlow.value,
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val progressBytesDone: Int = 0,
    val progressTotalBytes: Int = 0,
    val progressLabel: String = "",
    val flashBuffer: ByteArray = ByteArray(1024) { 0xFF.toByte() },
    val flashBaseAddress: Int = 0,
    val loadedFileName: String? = null,
    val loadedFormat: String? = null,
    val loadedNonEmptyCount: Int = 0,
    val firstNonEmptyRowIndex: Int = 0,
    val hexJumpSequence: Int = 0,
    val logs: List<ProgrammerLogEntry> = emptyList()
)

class McuProgrammerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private var allProcessors: List<McuProcessor> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _uiState = MutableStateFlow(McuProgrammerUiState())
    val uiState: StateFlow<McuProgrammerUiState> = _uiState.asStateFlow()

    init {
        loadDatabase()
        refreshUsbDevices()
        viewModelScope.launch {
            UsbOtgProgrammerService.isConnectedFlow.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }
        viewModelScope.launch {
            UsbOtgProgrammerService.connectedLabelFlow.collect { label ->
                _uiState.update { it.copy(connectedDeviceLabel = label) }
            }
        }
    }

    fun loadDatabase(forceReload: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDb = true, dbError = null) }
            val res = McuDatabaseRepository.loadAllDatabases(appContext, forceReload)
            allProcessors = res.processors

            if (allProcessors.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoadingDb = false,
                        loadedDbFiles = res.loadedFileNames,
                        totalMcuCount = 0,
                        dbError = if (res.errors.isNotEmpty()) res.errors.joinToString("\n")
                        else "لم يتم العثور على ملفات قواعد بيانات في assets/databases/"
                    )
                }
                return@launch
            }

            val manufacturers = allProcessors.map { it.manufacturer }.distinct().sorted()
            val firstMan = manufacturers.firstOrNull().orEmpty()
            val families = allProcessors.filter { it.manufacturer == firstMan }.map { it.mainFamily }.distinct().sorted()
            val firstFam = families.firstOrNull { it.equals("R8C", ignoreCase = true) }
                ?: families.firstOrNull().orEmpty()
            val subs = allProcessors.filter { it.manufacturer == firstMan && it.mainFamily == firstFam }
                .map { it.subFamily }.distinct().sorted()
            val firstSub = subs.firstOrNull { it.equals("R8C/25", ignoreCase = true) }
                ?: subs.firstOrNull().orEmpty()
            val parts = allProcessors.filter {
                it.manufacturer == firstMan && it.mainFamily == firstFam && it.subFamily == firstSub
            }.sortedBy { it.partNumber }
            val firstPart = parts.firstOrNull { it.partNumber.equals("R5F21258", ignoreCase = true) }
                ?: parts.firstOrNull()

            val defaultEcho = defaultEchoCancelFor(firstPart)
            UsbOtgProgrammerService.setEchoCancellation(defaultEcho)
            _uiState.update { state ->
                val buffer = firstPart?.let { FirmwareParser.createEmptyBuffer(it.flashSizeBytes) }
                    ?: FirmwareParser.createEmptyBuffer(64 * 1024)
                state.copy(
                    isLoadingDb = false,
                    loadedDbFiles = res.loadedFileNames,
                    totalMcuCount = allProcessors.size,
                    manufacturers = manufacturers,
                    selectedManufacturer = firstMan,
                    mainFamilies = families,
                    selectedMainFamily = firstFam,
                    subFamilies = subs,
                    selectedSubFamily = firstSub,
                    partNumbers = parts,
                    selectedProcessor = firstPart,
                    hasUserSelectedFamily = false,
                    enableEchoCancellation = defaultEcho,
                    flashBuffer = buffer,
                    flashBaseAddress = defaultBaseAddressFor(firstPart),
                    firstNonEmptyRowIndex = 0
                )
            }
            addLog(
                "تم تحميل ${allProcessors.size} معالج من (${res.loadedFileNames.joinToString(", ")})",
                LogLevel.SUCCESS
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val trimmed = query.trim()
        val matches = if (trimmed.length < 2) {
            emptyList()
        } else {
            val tokens = trimmed.lowercase().split(" ").filter { it.isNotBlank() }
            allProcessors.asSequence().filter { mcu ->
                val haystack = "${mcu.partNumber} ${mcu.mainFamily} ${mcu.subFamily} ${mcu.manufacturer} ${mcu.displayLabel}".lowercase()
                tokens.all { token -> haystack.contains(token) }
            }.take(45).toList()
        }
        _uiState.update { it.copy(searchQuery = query, searchMatches = matches) }
    }

    fun selectSearchMatch(mcu: McuProcessor) {
        applySelectedMcuHierarchy(mcu, clearSearch = true, markUserSelected = true)
    }

    private fun applySelectedMcuHierarchy(
        mcu: McuProcessor,
        clearSearch: Boolean = false,
        markUserSelected: Boolean = true
    ) {
        val families = allProcessors.filter { it.manufacturer == mcu.manufacturer }.map { it.mainFamily }.distinct().sorted()
        val subs = allProcessors.filter { it.manufacturer == mcu.manufacturer && it.mainFamily == mcu.mainFamily }
            .map { it.subFamily }.distinct().sorted()
        val parts = allProcessors.filter {
            it.manufacturer == mcu.manufacturer && it.mainFamily == mcu.mainFamily && it.subFamily == mcu.subFamily
        }.sortedBy { it.partNumber }

        val defaultEcho = defaultEchoCancelFor(mcu)
        UsbOtgProgrammerService.setEchoCancellation(defaultEcho)
        _uiState.update { state ->
            val resetNeeded = state.loadedFileName == null || state.flashBuffer.size != mcu.flashSizeBytes
            val newBuffer = if (resetNeeded) {
                FirmwareParser.createEmptyBuffer(mcu.flashSizeBytes)
            } else {
                state.flashBuffer
            }
            state.copy(
                searchQuery = if (clearSearch) "" else state.searchQuery,
                searchMatches = if (clearSearch) emptyList() else state.searchMatches,
                selectedManufacturer = mcu.manufacturer,
                mainFamilies = families,
                selectedMainFamily = mcu.mainFamily,
                subFamilies = subs,
                selectedSubFamily = mcu.subFamily,
                partNumbers = parts,
                selectedProcessor = mcu,
                hasUserSelectedFamily = if (markUserSelected) true else state.hasUserSelectedFamily,
                enableEchoCancellation = defaultEcho,
                flashBuffer = newBuffer,
                flashBaseAddress = if (resetNeeded) defaultBaseAddressFor(mcu) else state.flashBaseAddress,
                firstNonEmptyRowIndex = computeFirstDataRow(newBuffer)
            )
        }
    }

    fun selectManufacturer(manufacturer: String) {
        val families = allProcessors.filter { it.manufacturer == manufacturer }.map { it.mainFamily }.distinct().sorted()
        val firstFam = families.firstOrNull().orEmpty()
        val subs = allProcessors.filter { it.manufacturer == manufacturer && it.mainFamily == firstFam }
            .map { it.subFamily }.distinct().sorted()
        val firstSub = subs.firstOrNull().orEmpty()
        val parts = allProcessors.filter {
            it.manufacturer == manufacturer && it.mainFamily == firstFam && it.subFamily == firstSub
        }.sortedBy { it.partNumber }
        updateCascadingState(manufacturer, families, firstFam, subs, firstSub, parts, parts.firstOrNull())
    }

    fun selectMainFamily(mainFamily: String) {
        val man = _uiState.value.selectedManufacturer
        val subs = allProcessors.filter { it.manufacturer == man && it.mainFamily == mainFamily }
            .map { it.subFamily }.distinct().sorted()
        val firstSub = subs.firstOrNull().orEmpty()
        val parts = allProcessors.filter {
            it.manufacturer == man && it.mainFamily == mainFamily && it.subFamily == firstSub
        }.sortedBy { it.partNumber }
        updateCascadingState(man, _uiState.value.mainFamilies, mainFamily, subs, firstSub, parts, parts.firstOrNull())
    }

    fun selectSubFamily(subFamily: String) {
        val man = _uiState.value.selectedManufacturer
        val fam = _uiState.value.selectedMainFamily
        val parts = allProcessors.filter {
            it.manufacturer == man && it.mainFamily == fam && it.subFamily == subFamily
        }.sortedBy { it.partNumber }
        updateCascadingState(man, _uiState.value.mainFamilies, fam, _uiState.value.subFamilies, subFamily, parts, parts.firstOrNull())
    }

    fun selectPartNumber(processor: McuProcessor) {
        val defaultEcho = defaultEchoCancelFor(processor)
        UsbOtgProgrammerService.setEchoCancellation(defaultEcho)
        _uiState.update { state ->
            val resetBuf = if (state.loadedFileName == null) {
                FirmwareParser.createEmptyBuffer(processor.flashSizeBytes)
            } else {
                state.flashBuffer
            }
            state.copy(
                selectedProcessor = processor,
                hasUserSelectedFamily = true,
                enableEchoCancellation = defaultEcho,
                flashBuffer = resetBuf,
                flashBaseAddress = if (state.loadedFileName == null) defaultBaseAddressFor(processor) else state.flashBaseAddress,
                firstNonEmptyRowIndex = computeFirstDataRow(resetBuf)
            )
        }
    }

    fun clearFamilySelectionForUniversalAutoDetect() {
        _uiState.update { it.copy(hasUserSelectedFamily = false) }
        addLog("تم تفعيل الفحص الشامل التلقائي (R8C أولاً خلال 400ms ← ثم RL78).", LogLevel.INFO)
    }

    private fun updateCascadingState(
        manufacturer: String,
        families: List<String>,
        selectedFamily: String,
        subs: List<String>,
        selectedSub: String,
        parts: List<McuProcessor>,
        selectedPart: McuProcessor?
    ) {
        val defaultEcho = defaultEchoCancelFor(selectedPart)
        UsbOtgProgrammerService.setEchoCancellation(defaultEcho)
        _uiState.update { state ->
            val buf = selectedPart?.let {
                if (state.loadedFileName == null) FirmwareParser.createEmptyBuffer(it.flashSizeBytes) else state.flashBuffer
            } ?: state.flashBuffer
            state.copy(
                selectedManufacturer = manufacturer,
                mainFamilies = families,
                selectedMainFamily = selectedFamily,
                subFamilies = subs,
                selectedSubFamily = selectedSub,
                partNumbers = parts,
                selectedProcessor = selectedPart,
                hasUserSelectedFamily = true,
                enableEchoCancellation = defaultEcho,
                flashBuffer = buf,
                flashBaseAddress = if (state.loadedFileName == null) defaultBaseAddressFor(selectedPart) else state.flashBaseAddress,
                firstNonEmptyRowIndex = computeFirstDataRow(buf)
            )
        }
    }

    fun goToStep(step: Int) {
        refreshUsbDevices()
        val target = step.coerceIn(1, 3)
        if (target == 3) {
            val mcu = _uiState.value.selectedProcessor
            if (mcu != null && _uiState.value.loadedFileName == null && _uiState.value.flashBuffer.size != mcu.flashSizeBytes) {
                val buf = FirmwareParser.createEmptyBuffer(mcu.flashSizeBytes)
                _uiState.update {
                    it.copy(
                        currentStep = target,
                        flashBuffer = buf,
                        firstNonEmptyRowIndex = 0
                    )
                }
                return
            }
        }
        _uiState.update { it.copy(currentStep = target) }
    }

    fun selectProgrammer(chip: ProgrammerChipType) {
        _uiState.update { it.copy(selectedProgrammer = chip) }
        refreshUsbDevices()
    }

    fun refreshUsbDevices() {
        val devices = UsbOtgProgrammerService.scanAttachedDevices(appContext)
        _uiState.update { it.copy(attachedUsbDevices = devices) }
    }

    fun setEnableDtrRtsReset(enabled: Boolean) {
        _uiState.update { it.copy(enableDtrRtsReset = enabled) }
        if (UsbOtgProgrammerService.isConnected()) {
            UsbOtgProgrammerService.applyIdleControlLines(appContext, enableBootHold = enabled) { msg ->
                addLog(msg, LogLevel.INFO)
            }
        }
        addLog(
            if (enabled) "تم تفعيل نبضة DTR/RTS Reset التلقائية." else "تم إيقاف نبضة DTR/RTS Reset (توصيل مباشر بدون Reset).",
            LogLevel.INFO
        )
    }

    fun setEnableEchoCancellation(enabled: Boolean) {
        _uiState.update { it.copy(enableEchoCancellation = enabled) }
        UsbOtgProgrammerService.setEchoCancellation(enabled)
        addLog(
            if (enabled) "تم تفعيل إلغاء صدى الدايود D1 (Echo Cancellation)." else "تم إيقاف إلغاء صدى الدايود D1 (وضع سلكين منفصلين).",
            LogLevel.INFO
        )
    }

    fun autoDetectChipId() {
        val current = _uiState.value.selectedProcessor
        val isR8c = current?.protocolType == ProtocolFamilyType.R8C_M16C ||
            _uiState.value.selectedMainFamily.contains("R8C", ignoreCase = true) ||
            _uiState.value.selectedMainFamily.contains("M16C", ignoreCase = true)
        if (isR8c) {
            _uiState.update {
                it.copy(
                    autoDetectStatus = "تم إيقاف الفحص التلقائي لعائلة R8C — اختر المعالج يدوياً ثم انتقل للتوصيل والبرمجة.",
                    autoDetectIsSuccess = false
                )
            }
            addLog("ملاحظة: ميزة الفحص التلقائي موقفة لعائلة R8C؛ استخدم الاختيار اليدوي.", LogLevel.INFO)
            return
        }
        if (_uiState.value.isAutoDetecting || _uiState.value.isBusy) return
        viewModelScope.launch {
            val familyHint = _uiState.value.selectedProcessor?.mainFamily ?: _uiState.value.selectedMainFamily
            _uiState.update {
                it.copy(
                    isAutoDetecting = true,
                    autoDetectStatus = "جاري فحص $familyHint عبر USB OTG مع تتابع RTS/TX Break وتصفية صدى D1...",
                    autoDetectIsSuccess = false
                )
            }
            addLog("بدء التعرف التلقائي للعائلة ($familyHint)...", LogLevel.INFO)

            val idBytes = parseIdCodeBytes() ?: R8cProtocolEngine.DEFAULT_ID_CODE.copyOf()
            when (val res = UsbOtgProgrammerService.probeChipId(
                context = appContext,
                preferredChip = _uiState.value.selectedProgrammer,
                enableDtrRtsReset = _uiState.value.enableDtrRtsReset,
                targetMainFamily = familyHint,
                preferredPartNumber = _uiState.value.selectedProcessor?.partNumber,
                idCodeBytes = idBytes,
                catalog = allProcessors
            ) { msg ->
                addLog(msg, LogLevel.INFO)
            }) {
                is ChipAutoDetectResult.Matched -> {
                    res.matchedProcessor?.let { matched ->
                        applySelectedMcuHierarchy(matched, clearSearch = true, markUserSelected = true)
                    }
                    _uiState.update {
                        it.copy(
                            isAutoDetecting = false,
                            isConnected = UsbOtgProgrammerService.isConnected(),
                            autoDetectStatus = res.diagnosticMessage,
                            autoDetectIsSuccess = true
                        )
                    }
                    addLog(res.diagnosticMessage, LogLevel.SUCCESS)
                }
                is ChipAutoDetectResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isAutoDetecting = false,
                            isConnected = UsbOtgProgrammerService.isConnected(),
                            autoDetectStatus = res.reason,
                            autoDetectIsSuccess = false
                        )
                    }
                    addLog(res.reason, LogLevel.WARNING)
                }
            }
            refreshUsbDevices()
        }
    }

    fun setBaudRate(baud: Int) {
        _uiState.update { it.copy(baudRate = baud) }
    }

    fun updateIdCodeByte(index: Int, rawHex: String) {
        if (index !in 0..6) return
        val filtered = rawHex.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val current = _uiState.value.idCodeBytesHex.getOrElse(index) { "FF" }

        // Support pasting a full 14-char hex string across all 7 boxes
        if (filtered.length >= 14) {
            val chunked = (0 until 7).map { i -> filtered.substring(i * 2, i * 2 + 2) }
            _uiState.update { it.copy(idCodeBytesHex = chunked) }
            return
        }

        val resolved = when {
            current.length == 2 && filtered.length == 3 -> {
                // Smoothly overwrite existing "FF" when user types a new hex digit
                val extra = if (filtered.startsWith(current)) filtered.substring(2)
                else if (filtered.endsWith(current)) filtered.substring(0, 1)
                else filtered.takeLast(1)
                extra.take(2)
            }
            else -> filtered.take(2)
        }

        _uiState.update { state ->
            val list = state.idCodeBytesHex.toMutableList()
            while (list.size < 7) list.add("FF")
            list[index] = resolved
            state.copy(idCodeBytesHex = list)
        }
    }

    fun resetIdCodeDefault() {
        _uiState.update { it.copy(idCodeBytesHex = List(7) { "FF" }) }
    }

    fun setClockFrequency(clock: String) {
        _uiState.update { it.copy(clockFrequency = clock) }
    }

    fun setEraseMode(mode: String) {
        _uiState.update { it.copy(eraseMode = mode) }
    }

    fun triggerJumpToFirstData() {
        _uiState.update { it.copy(hexJumpSequence = it.hexJumpSequence + 1) }
    }

    fun toggleUsbConnection() {
        if (_uiState.value.isBusy) return
        if (UsbOtgProgrammerService.isConnected()) {
            UsbOtgProgrammerService.disconnect()
            _uiState.update { it.copy(isConnected = false, connectedDeviceLabel = "") }
            addLog("تم فصل المبرمجة USB OTG.", LogLevel.INFO)
            refreshUsbDevices()
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.2f,
                    progressBytesDone = 0,
                    progressTotalBytes = 0,
                    progressLabel = "جاري فتح منفذ USB OTG على سرعة ${_uiState.value.baudRate}..."
                )
            }
            val baud = _uiState.value.baudRate
            when (val res = UsbOtgProgrammerService.connect(
                context = appContext,
                preferredChip = _uiState.value.selectedProgrammer,
                baudRate = baud,
                enableDtrRtsReset = _uiState.value.enableDtrRtsReset,
                onLog = { msg -> addLog(msg, LogLevel.INFO) }
            )) {
                is UsbConnectResult.Success -> {
                    val label = "${res.deviceInfo.productName} (${res.deviceInfo.hexVidPid}) @ $baud"
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isConnected = true,
                            connectedDeviceLabel = label,
                            progress = 0f,
                            progressLabel = ""
                        )
                    }
                    addLog("متصل بالمنفذ: $label", LogLevel.SUCCESS)
                }
                is UsbConnectResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isConnected = false,
                            connectedDeviceLabel = "",
                            progress = 0f,
                            progressLabel = ""
                        )
                    }
                    addLog(res.reason, LogLevel.ERROR)
                }
            }
            refreshUsbDevices()
        }
    }

    fun loadFirmwareFromUri(uri: Uri) {
        val mcu = _uiState.value.selectedProcessor ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.4f,
                    progressBytesDone = 0,
                    progressTotalBytes = mcu.flashSizeBytes,
                    progressLabel = "جاري قراءة وتحليل ملف السوفت..."
                )
            }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    var fileName = "firmware.bin"
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(idx) ?: fileName
                        }
                    }
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("تعذر فتح الملف المختار.")
                    FirmwareParser.parseFirmware(
                        rawBytes = bytes,
                        fileName = fileName,
                        targetFlashSizeBytes = mcu.flashSizeBytes,
                        preferredBaseAddress = defaultBaseAddressFor(mcu)
                    )
                }

                val firstDataRow = computeFirstDataRow(parsed.bytes)
                val resolvedBase = parsed.startAddress
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 1f,
                        progressBytesDone = mcu.flashSizeBytes,
                        progressTotalBytes = mcu.flashSizeBytes,
                        progressLabel = "تم تحميل الملف بنجاح",
                        flashBuffer = parsed.bytes,
                        flashBaseAddress = resolvedBase,
                        loadedFileName = parsed.fileName,
                        loadedFormat = parsed.format,
                        loadedNonEmptyCount = parsed.nonEmptyBytesCount,
                        firstNonEmptyRowIndex = firstDataRow,
                        hexJumpSequence = it.hexJumpSequence + 1
                    )
                }
                val startHex = "0x%08X".format(resolvedBase + firstDataRow * 16)
                addLog(
                    "تم عرض الملف ${parsed.fileName} (${parsed.format}) - بداية البيانات عند $startHex",
                    LogLevel.SUCCESS
                )
            } catch (e: Throwable) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, progressLabel = "") }
                addLog("فشل قراءة الملف: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun saveDumpToUri(uri: Uri, asIntelHex: Boolean) {
        val buffer = _uiState.value.flashBuffer
        val baseAddr = _uiState.value.flashBaseAddress
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        if (asIntelHex) {
                            out.write(FirmwareParser.toIntelHex(buffer, baseAddr).toByteArray(Charsets.US_ASCII))
                        } else {
                            out.write(buffer)
                        }
                    } ?: throw IllegalStateException("تعذر الكتابة إلى المسار المختار.")
                }
                addLog(
                    "تم حفظ السوفت المسحوب بنجاح (${if (asIntelHex) "Intel HEX" else "BIN"})",
                    LogLevel.SUCCESS
                )
            } catch (e: Throwable) {
                addLog("خطأ أثناء حفظ الملف: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun readChipFlash() {
        val mcu = _uiState.value.selectedProcessor ?: return
        if (_uiState.value.isBusy) return
        val idBytes = parseIdCodeBytes() ?: run {
            addLog("كود ID Code غير مكتمل: أدخل 14 حرف Hex بالضبط (7 بايت).", LogLevel.ERROR)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.01f,
                    progressBytesDone = 0,
                    progressTotalBytes = mcu.flashSizeBytes,
                    progressLabel = "بدء قراءة الفلاش..."
                )
            }
            addLog("بدء قراءة ذاكرة ${mcu.partNumber} (${mcu.flashKb}KB)...", LogLevel.INFO)

            when (val res = UsbOtgProgrammerService.executeReadFlash(
                context = appContext,
                preferredChip = _uiState.value.selectedProgrammer,
                baudRate = _uiState.value.baudRate,
                enableDtrRtsReset = _uiState.value.enableDtrRtsReset,
                processor = mcu,
                idCodeBytes = idBytes,
                onLog = { msg -> addLog(msg, LogLevel.INFO) },
                onProgress = { done, total, msg ->
                    val frac = if (total > 0) done.toFloat() / total.toFloat() else 0f
                    _uiState.update {
                        it.copy(
                            progress = frac,
                            progressBytesDone = done,
                            progressTotalBytes = total,
                            progressLabel = msg
                        )
                    }
                }
            )) {
                is FlashOperationResult.Success -> {
                    val data = res.bytesTransferred ?: _uiState.value.flashBuffer
                    val firstDataRow = computeFirstDataRow(data)
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 1f,
                            progressBytesDone = data.size,
                            progressTotalBytes = data.size,
                            progressLabel = "اكتملت القراءة",
                            flashBuffer = data,
                            flashBaseAddress = res.baseAddress,
                            loadedFileName = "Dump_${mcu.partNumber}.bin",
                            loadedFormat = "Chip Read",
                            loadedNonEmptyCount = data.count { b -> b != 0xFF.toByte() },
                            firstNonEmptyRowIndex = firstDataRow,
                            hexJumpSequence = it.hexJumpSequence + 1
                        )
                    }
                    addLog(res.message, LogLevel.SUCCESS)
                }
                is FlashOperationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 0f,
                            progressBytesDone = 0,
                            progressTotalBytes = 0,
                            progressLabel = ""
                        )
                    }
                    addLog(res.reason, LogLevel.ERROR)
                }
            }
        }
    }

    fun eraseChipFlash() {
        val mcu = _uiState.value.selectedProcessor ?: return
        if (_uiState.value.isBusy) return
        val idBytes = parseIdCodeBytes() ?: if (mcu.protocolType == ProtocolFamilyType.R8C_M16C) {
            R8cProtocolEngine.DEFAULT_ID_CODE.copyOf()
        } else {
            addLog("كود ID Code غير مكتمل: أدخل حرفين Hex في كل خانة.", LogLevel.ERROR)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.05f,
                    progressBytesDone = 0,
                    progressTotalBytes = mcu.flashSizeBytes,
                    progressLabel = "جاري مسح الفلاش..."
                )
            }
            addLog("بدء مسح ذاكرة ${mcu.partNumber} (${_uiState.value.eraseMode})...", LogLevel.INFO)
            when (val res = UsbOtgProgrammerService.executeErase(
                context = appContext,
                preferredChip = _uiState.value.selectedProgrammer,
                baudRate = _uiState.value.baudRate,
                enableDtrRtsReset = _uiState.value.enableDtrRtsReset,
                processor = mcu,
                eraseMode = _uiState.value.eraseMode,
                idCodeBytes = idBytes,
                onLog = { msg -> addLog(msg, LogLevel.INFO) },
                onProgress = { done, total, msg ->
                    val frac = if (total > 0) done.toFloat() / total.toFloat() else 0f
                    _uiState.update {
                        it.copy(
                            progress = frac,
                            progressBytesDone = done,
                            progressTotalBytes = total,
                            progressLabel = msg
                        )
                    }
                }
            )) {
                is FlashOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 1f,
                            progressBytesDone = mcu.flashSizeBytes,
                            progressTotalBytes = mcu.flashSizeBytes,
                            progressLabel = "اكتمل المسح",
                            flashBuffer = FirmwareParser.createEmptyBuffer(mcu.flashSizeBytes),
                            idCodeBytesHex = if (mcu.protocolType == ProtocolFamilyType.R8C_M16C) {
                                List(7) { "FF" }
                            } else {
                                it.idCodeBytesHex
                            },
                            loadedNonEmptyCount = 0,
                            firstNonEmptyRowIndex = 0
                        )
                    }
                    addLog(res.message, LogLevel.SUCCESS)
                }
                is FlashOperationResult.Failure -> {
                    _uiState.update {
                        it.copy(isBusy = false, progress = 0f, progressBytesDone = 0, progressTotalBytes = 0, progressLabel = "")
                    }
                    addLog(res.reason, LogLevel.ERROR)
                }
            }
        }
    }

    fun writeChipFlash() = runWriteOrVerify(verifyOnly = false)
    fun verifyChipFlash() = runWriteOrVerify(verifyOnly = true)

    private fun runWriteOrVerify(verifyOnly: Boolean) {
        val mcu = _uiState.value.selectedProcessor ?: return
        if (_uiState.value.isBusy) return
        val idBytes = parseIdCodeBytes() ?: run {
            addLog("كود ID Code غير مكتمل: أدخل حرفين Hex في كل خانة.", LogLevel.ERROR)
            return
        }

        viewModelScope.launch {
            val actionName = if (verifyOnly) "مطابقة (Verify)" else "كتابة (Write)"
            val totalSize = _uiState.value.flashBuffer.size
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.01f,
                    progressBytesDone = 0,
                    progressTotalBytes = totalSize,
                    progressLabel = "بدء $actionName..."
                )
            }
            addLog("بدء عملية $actionName على ${mcu.partNumber}...", LogLevel.INFO)

            when (val res = UsbOtgProgrammerService.executeWriteAndVerify(
                context = appContext,
                preferredChip = _uiState.value.selectedProgrammer,
                baudRate = _uiState.value.baudRate,
                enableDtrRtsReset = _uiState.value.enableDtrRtsReset,
                processor = mcu,
                firmwareBytes = _uiState.value.flashBuffer,
                firmwareStartAddress = _uiState.value.flashBaseAddress,
                idCodeBytes = idBytes,
                verifyOnly = verifyOnly,
                onLog = { msg -> addLog(msg, LogLevel.INFO) },
                onProgress = { done, total, msg ->
                    val frac = if (total > 0) done.toFloat() / total.toFloat() else 0f
                    _uiState.update {
                        it.copy(
                            progress = frac,
                            progressBytesDone = done,
                            progressTotalBytes = total,
                            progressLabel = msg
                        )
                    }
                }
            )) {
                is FlashOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 1f,
                            progressBytesDone = totalSize,
                            progressTotalBytes = totalSize,
                            progressLabel = "اكتملت العملية 100%"
                        )
                    }
                    addLog(res.message, LogLevel.SUCCESS)
                }
                is FlashOperationResult.Failure -> {
                    _uiState.update {
                        it.copy(isBusy = false, progress = 0f, progressBytesDone = 0, progressTotalBytes = 0, progressLabel = "")
                    }
                    addLog(res.reason, LogLevel.ERROR)
                }
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    private fun parseIdCodeBytes(): ByteArray? {
        val parts = _uiState.value.idCodeBytesHex
        if (parts.size != 7) return null
        val out = ByteArray(7)
        for (i in 0 until 7) {
            val p = parts[i].trim().uppercase()
            if (p.length != 2) return null
            out[i] = (p.toIntOrNull(16) ?: return null).toByte()
        }
        return out
    }

    private fun defaultEchoCancelFor(mcu: McuProcessor?): Boolean {
        if (mcu == null) return false
        return mcu.isDiodeSingleWireSchematicFamily() || mcu.protocolType == ProtocolFamilyType.R8C_M16C
    }

    private fun defaultBaseAddressFor(mcu: McuProcessor?): Int {
        if (mcu == null) return 0
        return if (mcu.protocolType == ProtocolFamilyType.R8C_M16C) {
            R8cProtocolEngine.resolveDeviceLayout(mcu).romArea.org
        } else {
            0
        }
    }

    private fun computeFirstDataRow(buffer: ByteArray): Int {
        for (i in buffer.indices) {
            if (buffer[i] != 0xFF.toByte()) {
                return i / 16
            }
        }
        return 0
    }

    private fun addLog(message: String, level: LogLevel) {
        val ts = timeFormat.format(Date())
        _uiState.update { state ->
            val updated = (state.logs + ProgrammerLogEntry(ts, message, level)).takeLast(120)
            state.copy(logs = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Keep Singleton UsbOtgProgrammerService open across screen/tab navigation
    }
}
