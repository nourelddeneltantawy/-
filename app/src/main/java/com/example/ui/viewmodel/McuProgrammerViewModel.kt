package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DataFlashExportMode
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
import com.example.util.Nec78k0ProtocolEngine
import com.example.util.Nec78k0ProtocolRegistry
import com.example.util.Nec78k0RecoveryManager
import com.example.util.R8cProtocolEngine
import com.example.util.UsbConnectResult
import com.example.util.UsbOtgProgrammerService
import kotlinx.coroutines.CancellationException
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
    val dataFlashMode: DataFlashExportMode = DataFlashExportMode.MERGED,
    val dataFlashBuffer: ByteArray? = null,
    val dataFlashBaseAddress: Int = 0,
    val viewingDataFlashTab: Boolean = false,
    val autoEraseBeforeWrite: Boolean = true,
    val autoVerifyAfterWrite: Boolean = false,
    val loadedFileName: String? = null,
    val loadedFormat: String? = null,
    val loadedNonEmptyCount: Int = 0,
    val firstNonEmptyRowIndex: Int = 0,
    val hexJumpSequence: Int = 0,
    val logs: List<ProgrammerLogEntry> = emptyList(),

    // NEC / Renesas 78K0 Engine State
    val invert78k0DtrRtsPolarity: Boolean = false,
    val autoRestoreChip: Boolean = true,
    val nec78k0DetectedInfo: Nec78k0ProtocolEngine.Nec78k0DetectedInfo? = null,
    val pendingRescueSessions: List<Nec78k0RecoveryManager.Nec78k0RecoverySession> = emptyList(),
    val watchdogAlertMessage: String? = null,
    val watchdogPausedBlock: Int? = null,
    val isCurrentDumpMcuVerified: Boolean = false
)

class McuProgrammerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private var allProcessors: List<McuProcessor> = emptyList()
    private val timeFormatLock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _uiState = MutableStateFlow(McuProgrammerUiState())
    val uiState: StateFlow<McuProgrammerUiState> = _uiState.asStateFlow()

    init {
        loadDatabase()
        refreshUsbDevices()
        refresh78k0RescueSessions()
        viewModelScope.launch {
            UsbOtgProgrammerService.isConnectedFlow.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) {
                    refresh78k0RescueSessions()
                }
            }
        }
        viewModelScope.launch {
            UsbOtgProgrammerService.connectedLabelFlow.collect { label ->
                _uiState.update { it.copy(connectedDeviceLabel = label) }
            }
        }
        viewModelScope.launch {
            UsbOtgProgrammerService.usbAttachedFlow.collect { attachedInfo ->
                refreshUsbDevices()
                attachedInfo.chipType?.let { chip ->
                    _uiState.update { it.copy(selectedProgrammer = chip) }
                }
                val current = _uiState.value
                if (isSelectedTarget78k0(current) && !current.isBusy && !UsbOtgProgrammerService.isConnected()) {
                    addLog(
                        "تم رصد توصيل ${attachedInfo.productName} — تشغيل التهيئة التلقائية لمعالج 78K0 (DTR/RTS + 0xC0 / 0xB0)...",
                        LogLevel.INFO
                    )
                    connectAndAutoInitUsb(triggeredByUsbAttach = true)
                }
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
            val forceDtr = firstPart?.is78k0Flmd0Mode() == true
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
                    enableDtrRtsReset = if (forceDtr) true else state.enableDtrRtsReset,
                    enableEchoCancellation = defaultEcho,
                    flashBuffer = buffer,
                    flashBaseAddress = defaultBaseAddressFor(firstPart),
                    dataFlashBuffer = null,
                    dataFlashBaseAddress = firstPart?.chipConfig?.dataFlashStartAddress ?: 0,
                    viewingDataFlashTab = false,
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
        markUserSelected: Boolean = true,
        preserveLoadedFirmware: Boolean = false
    ) {
        val families = allProcessors.filter { it.manufacturer == mcu.manufacturer }.map { it.mainFamily }.distinct().sorted()
        val subs = allProcessors.filter { it.manufacturer == mcu.manufacturer && it.mainFamily == mcu.mainFamily }
            .map { it.subFamily }.distinct().sorted()
        val parts = allProcessors.filter {
            it.manufacturer == mcu.manufacturer && it.mainFamily == mcu.mainFamily && it.subFamily == mcu.subFamily
        }.sortedBy { it.partNumber }

        val defaultEcho = defaultEchoCancelFor(mcu)
        val forceDtr = mcu.is78k0Flmd0Mode()
        UsbOtgProgrammerService.setEchoCancellation(defaultEcho)
        _uiState.update { state ->
            val hasLoaded = state.loadedFileName != null
            val shouldResizePreserve = preserveLoadedFirmware && hasLoaded && state.flashBuffer.size != mcu.flashSizeBytes
            val resetNeeded = !hasLoaded || (!preserveLoadedFirmware && state.flashBuffer.size != mcu.flashSizeBytes)
            val newBuffer = when {
                shouldResizePreserve -> ByteArray(mcu.flashSizeBytes) { 0xFF.toByte() }.also { out ->
                    state.flashBuffer.copyInto(out, 0, 0, minOf(state.flashBuffer.size, out.size))
                }
                resetNeeded -> FirmwareParser.createEmptyBuffer(mcu.flashSizeBytes)
                else -> state.flashBuffer
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
                enableDtrRtsReset = if (forceDtr) true else state.enableDtrRtsReset,
                enableEchoCancellation = defaultEcho,
                baudRate = if (forceDtr) 9600 else state.baudRate,
                flashBuffer = newBuffer,
                flashBaseAddress = if (resetNeeded) defaultBaseAddressFor(mcu) else state.flashBaseAddress,
                dataFlashBuffer = if (resetNeeded) null else state.dataFlashBuffer,
                dataFlashBaseAddress = if (resetNeeded) (mcu.chipConfig.dataFlashStartAddress ?: 0) else state.dataFlashBaseAddress,
                viewingDataFlashTab = if (resetNeeded) false else state.viewingDataFlashTab,
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
        val forceDtr = processor.is78k0Flmd0Mode()
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
                enableDtrRtsReset = if (forceDtr) true else state.enableDtrRtsReset,
                enableEchoCancellation = defaultEcho,
                baudRate = if (forceDtr) 9600 else state.baudRate,
                flashBuffer = resetBuf,
                flashBaseAddress = if (state.loadedFileName == null) defaultBaseAddressFor(processor) else state.flashBaseAddress,
                dataFlashBuffer = if (state.loadedFileName == null) null else state.dataFlashBuffer,
                dataFlashBaseAddress = if (state.loadedFileName == null) (processor.chipConfig.dataFlashStartAddress ?: 0) else state.dataFlashBaseAddress,
                viewingDataFlashTab = if (state.loadedFileName == null) false else state.viewingDataFlashTab,
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
        val forceDtr = selectedPart?.is78k0Flmd0Mode() == true ||
            (selectedFamily.contains("78K0", ignoreCase = true) && !selectedFamily.contains("78K0R", ignoreCase = true))
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
                enableDtrRtsReset = if (forceDtr) true else state.enableDtrRtsReset,
                enableEchoCancellation = defaultEcho,
                baudRate = if (forceDtr) 9600 else state.baudRate,
                flashBuffer = buf,
                flashBaseAddress = if (state.loadedFileName == null) defaultBaseAddressFor(selectedPart) else state.flashBaseAddress,
                dataFlashBuffer = if (state.loadedFileName == null) null else state.dataFlashBuffer,
                dataFlashBaseAddress = if (state.loadedFileName == null) (selectedPart?.chipConfig?.dataFlashStartAddress ?: 0) else state.dataFlashBaseAddress,
                viewingDataFlashTab = if (state.loadedFileName == null) false else state.viewingDataFlashTab,
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
                        flashBaseAddress = defaultBaseAddressFor(mcu),
                        dataFlashBuffer = null,
                        dataFlashBaseAddress = mcu.chipConfig.dataFlashStartAddress ?: 0,
                        viewingDataFlashTab = false,
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
        if (isSelectedTarget78k0()) {
            _uiState.update { it.copy(enableDtrRtsReset = true) }
            addLog("نبضة DTR/RTS Reset مفعّلة إجبارياً لعائلة 78K0 للتحكم التلقائي في FLMD0 و RESET.", LogLevel.INFO)
            return
        }
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
        if (isSelectedTarget78k0()) {
            _uiState.update { it.copy(enableEchoCancellation = false) }
            UsbOtgProgrammerService.setEchoCancellation(false)
            addLog("إلغاء الصدى مُعطّل تلقائياً (false) لعائلة 78K0 لأن خطي TX و RX منفصلان بدون دايود.", LogLevel.INFO)
            return
        }
        _uiState.update { it.copy(enableEchoCancellation = enabled) }
        UsbOtgProgrammerService.setEchoCancellation(enabled)
        addLog(
            if (enabled) "تم تفعيل إلغاء صدى الدايود D1 (Echo Cancellation)." else "تم إيقاف إلغاء صدى الدايود D1 (وضع سلكين منفصلين).",
            LogLevel.INFO
        )
    }

    fun dismissWatchdogAlert() {
        _uiState.update { it.copy(watchdogAlertMessage = null, watchdogPausedBlock = null) }
    }

    fun refresh78k0RescueSessions() {
        val sessions = Nec78k0RecoveryManager.findActiveRescueSessions(appContext)
        _uiState.update { it.copy(pendingRescueSessions = sessions) }
    }

    fun dismiss78k0RescueSession(session: Nec78k0RecoveryManager.Nec78k0RecoverySession) {
        Nec78k0RecoveryManager.markCompletedAndCleanup(appContext, session.chipName)
        refresh78k0RescueSessions()
        addLog("تم إلغاء وإخفاء وضع إنقاذ المعالج لـ ${session.chipName}.", LogLevel.INFO)
    }

    private fun isSelectedTarget78k0(state: McuProgrammerUiState = _uiState.value): Boolean {
        val mcu = state.selectedProcessor
        if (mcu != null) return mcu.is78k0Flmd0Mode()
        return state.selectedMainFamily.contains("78K0", ignoreCase = true) &&
            !state.selectedMainFamily.contains("78K0R", ignoreCase = true)
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
            val is78k0 = isSelectedTarget78k0()
            val familyHint = _uiState.value.selectedProcessor?.mainFamily ?: _uiState.value.selectedMainFamily
            _uiState.update {
                it.copy(
                    isAutoDetecting = true,
                    enableDtrRtsReset = if (is78k0) true else it.enableDtrRtsReset,
                    enableEchoCancellation = if (is78k0) false else it.enableEchoCancellation,
                    autoDetectStatus = "جاري فحص $familyHint عبر USB OTG (${if (is78k0) "FT232 DTR/RTS 100ms/150ms -> 0xC0 / 0xB0" else "مزامنة الدايود"})...",
                    autoDetectIsSuccess = false
                )
            }
            if (is78k0) UsbOtgProgrammerService.setEchoCancellation(false)
            addLog("بدء التعرف التلقائي للعائلة ($familyHint)...", LogLevel.INFO)

            val idBytes = parseIdCodeBytes() ?: R8cProtocolEngine.DEFAULT_ID_CODE.copyOf()
            try {
                when (val res = UsbOtgProgrammerService.probeChipId(
                    context = appContext,
                    preferredChip = _uiState.value.selectedProgrammer,
                    enableDtrRtsReset = if (is78k0) true else _uiState.value.enableDtrRtsReset,
                    targetMainFamily = familyHint,
                    preferredPartNumber = _uiState.value.selectedProcessor?.partNumber,
                    idCodeBytes = idBytes,
                    catalog = allProcessors,
                    clockFrequency = _uiState.value.clockFrequency
                ) { msg ->
                    addLog(msg, LogLevel.INFO)
                }) {
                    is ChipAutoDetectResult.Matched -> {
                        res.matchedProcessor?.let { matched ->
                            applySelectedMcuHierarchy(matched, clearSearch = true, markUserSelected = true, preserveLoadedFirmware = true)
                        }
                        _uiState.update {
                            it.copy(
                                isAutoDetecting = false,
                                isConnected = UsbOtgProgrammerService.isConnected(),
                                autoDetectStatus = res.diagnosticMessage,
                                autoDetectIsSuccess = true,
                                nec78k0DetectedInfo = res.nec78k0Info ?: it.nec78k0DetectedInfo
                            )
                        }
                        refresh78k0RescueSessions()
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
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val errMsg = "خطأ غير متوقع أثناء الفحص التلقائي: ${t.toString()}"
                _uiState.update {
                    it.copy(
                        isAutoDetecting = false,
                        isConnected = UsbOtgProgrammerService.isConnected(),
                        autoDetectStatus = errMsg,
                        autoDetectIsSuccess = false
                    )
                }
                addLog(errMsg, LogLevel.ERROR)
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

    fun setDataFlashMode(mode: DataFlashExportMode) {
        _uiState.update { state ->
            state.copy(
                dataFlashMode = mode,
                viewingDataFlashTab = if (mode == DataFlashExportMode.EXCLUDE) false else state.viewingDataFlashTab
            )
        }
        addLog("تم ضبط وضع Data Flash: ${mode.titleAr} — ${mode.subtitleAr}", LogLevel.INFO)
    }

    fun setViewingDataFlashTab(viewingDataFlash: Boolean) {
        _uiState.update { state ->
            val canView = viewingDataFlash && state.dataFlashBuffer != null
            state.copy(viewingDataFlashTab = canView)
        }
    }

    fun setAutoEraseBeforeWrite(enabled: Boolean) {
        _uiState.update { it.copy(autoEraseBeforeWrite = enabled) }
        addLog(
            if (enabled) "تم تفعيل المسح الشامل التلقائي (0xA7 0xD0) قبل الكتابة."
            else "تم إيقاف المسح التلقائي قبل الكتابة (تأكد أن المعالج ممسوح مسبقاً).",
            LogLevel.INFO
        )
    }

    fun setAutoVerifyAfterWrite(enabled: Boolean) {
        _uiState.update { it.copy(autoVerifyAfterWrite = enabled) }
        addLog(
            if (enabled) "تم تفعيل المطابقة التلقائية (Auto Verify) بعد الكتابة مباشرة."
            else "تم إيقاف المطابقة التلقائية بعد الكتابة.",
            LogLevel.INFO
        )
    }

    fun setAutoRestoreChip(enabled: Boolean) {
        _uiState.update { it.copy(autoRestoreChip = enabled) }
        addLog(
            if (enabled) "تم تفعيل الاستعادة التلقائية الفورية للمعالج (Block-by-Block 0x22 + 0x40 + 0x13 + 0xB0)."
            else "تم إيقاف الاستعادة التلقائية للمعالج بعد السحب (سيتم حفظ الملف على الهاتف فقط).",
            LogLevel.INFO
        )
    }

    fun triggerJumpToFirstData() {
        _uiState.update { it.copy(hexJumpSequence = it.hexJumpSequence + 1) }
    }

    fun toggleUsbConnection() {
        if (_uiState.value.isBusy) return
        if (UsbOtgProgrammerService.isConnected()) {
            UsbOtgProgrammerService.disconnect()
            _uiState.update {
                it.copy(
                    isConnected = false,
                    connectedDeviceLabel = "",
                    nec78k0DetectedInfo = null
                )
            }
            addLog("تم فصل المبرمجة USB OTG.", LogLevel.INFO)
            refreshUsbDevices()
            return
        }
        connectAndAutoInitUsb(triggeredByUsbAttach = false)
    }

    private fun connectAndAutoInitUsb(triggeredByUsbAttach: Boolean) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            val is78k0Target = isSelectedTarget78k0()
            val effectiveBaud = if (is78k0Target) 9600 else _uiState.value.baudRate
            val effectiveDtrRts = if (is78k0Target) true else _uiState.value.enableDtrRtsReset
            if (is78k0Target) {
                UsbOtgProgrammerService.setEchoCancellation(false)
            }

            _uiState.update {
                it.copy(
                    isBusy = true,
                    enableDtrRtsReset = effectiveDtrRts,
                    enableEchoCancellation = if (is78k0Target) false else it.enableEchoCancellation,
                    baudRate = effectiveBaud,
                    progress = 0.2f,
                    progressBytesDone = 0,
                    progressTotalBytes = 0,
                    progressLabel = if (is78k0Target) {
                        "فتح منفذ FT232 وتهيئة 78K0 تلقائياً (9600 8N1)..."
                    } else {
                        "جاري فتح منفذ USB OTG على سرعة $effectiveBaud..."
                    }
                )
            }
            try {
                when (val res = UsbOtgProgrammerService.connect(
                    context = appContext,
                    preferredChip = _uiState.value.selectedProgrammer,
                    baudRate = effectiveBaud,
                    enableDtrRtsReset = effectiveDtrRts,
                    onLog = { msg -> addLog(msg, LogLevel.INFO) }
                )) {
                    is UsbConnectResult.Success -> {
                        val label = "${res.deviceInfo.productName} (${res.deviceInfo.hexVidPid}) @ $effectiveBaud"
                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                connectedDeviceLabel = label,
                                nec78k0DetectedInfo = null
                            )
                        }
                        addLog(
                            if (triggeredByUsbAttach) "اتصال USB تلقائي عند التركيب: $label" else "متصل بالمنفذ: $label",
                            LogLevel.SUCCESS
                        )

                        val stateAfterOpen = _uiState.value
                        val selectedMcu = stateAfterOpen.selectedProcessor
                        if (isSelectedTarget78k0(stateAfterOpen)) {
                            _uiState.update {
                                it.copy(
                                    progress = 0.65f,
                                    progressLabel = "تهيئة تلقائية 78K0: FT232 DTR/RTS (100ms/150ms) -> 0x00,0x00 -> 0xC0 / 0xB0..."
                                )
                            }
                            addLog("Zero-Touch Auto-Init (78K0): setDTR(false) [FLMD0=+5V] + setRTS(true) [RESET=0V] (100ms) -> setRTS(false) (150ms) -> [0x00, 10ms, 0x00] -> 0xC0 + 0xB0...", LogLevel.INFO)
                            val idBytes = parseIdCodeBytes() ?: R8cProtocolEngine.DEFAULT_ID_CODE.copyOf()
                            when (val probeRes = UsbOtgProgrammerService.probeChipId(
                                context = appContext,
                                preferredChip = stateAfterOpen.selectedProgrammer,
                                enableDtrRtsReset = true,
                                targetMainFamily = selectedMcu?.mainFamily ?: stateAfterOpen.selectedMainFamily,
                                preferredPartNumber = selectedMcu?.partNumber,
                                idCodeBytes = idBytes,
                                catalog = allProcessors,
                                clockFrequency = stateAfterOpen.clockFrequency
                            ) { msg ->
                                addLog(msg, LogLevel.INFO)
                            }) {
                                is ChipAutoDetectResult.Matched -> {
                                    probeRes.matchedProcessor?.let { matched ->
                                        applySelectedMcuHierarchy(
                                            mcu = matched,
                                            clearSearch = true,
                                            markUserSelected = true,
                                            preserveLoadedFirmware = true
                                        )
                                    }
                                    _uiState.update {
                                        it.copy(
                                            isBusy = false,
                                            isConnected = UsbOtgProgrammerService.isConnected(),
                                            progress = 0f,
                                            progressLabel = "",
                                            autoDetectStatus = probeRes.diagnosticMessage,
                                            autoDetectIsSuccess = true,
                                            invert78k0DtrRtsPolarity = UsbOtgProgrammerService.is78k0InvertDtrRtsPolarity(),
                                            nec78k0DetectedInfo = probeRes.nec78k0Info ?: it.nec78k0DetectedInfo
                                        )
                                    }
                                    refresh78k0RescueSessions()
                                    addLog("اكتمل التعرف وتخزين البصمة المرجعية (0xC0 / 0xB0) — جاهز للقراءة/الكتابة فوراً.", LogLevel.SUCCESS)
                                }
                                is ChipAutoDetectResult.Failure -> {
                                    _uiState.update {
                                        it.copy(
                                            isBusy = false,
                                            isConnected = UsbOtgProgrammerService.isConnected(),
                                            progress = 0f,
                                            progressLabel = "",
                                            autoDetectStatus = probeRes.reason,
                                            autoDetectIsSuccess = false
                                        )
                                    }
                                    addLog("تنبيه التهيئة التلقائية 78K0 عند الاتصال: ${probeRes.reason}", LogLevel.WARNING)
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    progress = 0f,
                                    progressLabel = ""
                                )
                            }
                        }
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
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isConnected = UsbOtgProgrammerService.isConnected(),
                        progress = 0f,
                        progressLabel = ""
                    )
                }
                addLog("خطأ أثناء فتح منفذ USB: ${t.toString()}", LogLevel.ERROR)
            }
            refreshUsbDevices()
        }
    }

    fun loadFirmwareFromUri(uri: Uri) {
        val mcu = _uiState.value.selectedProcessor ?: return
        val cfg = mcu.chipConfig
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.4f,
                    progressBytesDone = 0,
                    progressTotalBytes = cfg.flashSizeBytes,
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
                        targetFlashSizeBytes = cfg.flashSizeBytes,
                        preferredBaseAddress = defaultBaseAddressFor(mcu),
                        dataFlashStartAddress = cfg.dataFlashStartAddress,
                        dataFlashSizeBytes = cfg.dataFlashSizeBytes
                    )
                }

                val firstDataRow = computeFirstDataRow(parsed.bytes)
                val resolvedBase = parsed.startAddress
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 1f,
                        progressBytesDone = cfg.flashSizeBytes,
                        progressTotalBytes = cfg.flashSizeBytes,
                        progressLabel = "تم تحميل الملف بنجاح",
                        flashBuffer = parsed.bytes,
                        flashBaseAddress = resolvedBase,
                        dataFlashBuffer = parsed.dataFlashBytes,
                        dataFlashBaseAddress = parsed.dataFlashStartAddress,
                        viewingDataFlashTab = false,
                        loadedFileName = parsed.fileName,
                        loadedFormat = parsed.format,
                        loadedNonEmptyCount = parsed.nonEmptyBytesCount,
                        firstNonEmptyRowIndex = firstDataRow,
                        hexJumpSequence = it.hexJumpSequence + 1,
                        isCurrentDumpMcuVerified = false
                    )
                }
                val startHex = String.format(Locale.US, "0x%08X", resolvedBase + firstDataRow * 16)
                val dfNote = if (parsed.dataFlashBytes != null) {
                    " + Data Flash (${String.format(Locale.US, "0x%05X", parsed.dataFlashStartAddress)})"
                } else ""
                addLog(
                    "تم عرض الملف ${parsed.fileName} (${parsed.format})$dfNote - بداية البيانات عند $startHex",
                    LogLevel.SUCCESS
                )
            } catch (e: Throwable) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, progressLabel = "") }
                addLog("فشل قراءة الملف: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun saveDumpToUri(uri: Uri, asIntelHex: Boolean) {
        val state = _uiState.value
        val buffer = state.flashBuffer
        val baseAddr = state.flashBaseAddress
        val dfBuffer = state.dataFlashBuffer
        val dfBase = state.dataFlashBaseAddress
        val chipConfig = state.selectedProcessor?.chipConfig
        val isMergedMode = state.dataFlashMode == DataFlashExportMode.MERGED

        // Anti-Dummy Rule 1: Never allow saving >= 99% 0x00/0xFF extracted dumps without on-chip MCU Verify confirmation
        if (state.loadedFormat == "Chip Read" &&
            Nec78k0ProtocolRegistry.isDummyAllZeroOrFf(buffer) &&
            !state.isCurrentDumpMcuVerified
        ) {
            addLog(
                "تم رفض حفظ السوفت المسحوب: نسبة بايتات 0x00/0xFF >= 99% ولم يتم اجتياز اختبار Verify الحقيقي داخل المعالج.",
                LogLevel.ERROR
            )
            return
        }

        viewModelScope.launch {
            try {
                var savedByteCount = buffer.size
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        if (asIntelHex) {
                            val hexContent = if (isMergedMode && dfBuffer != null) {
                                FirmwareParser.toIntelHexMultiSegment(
                                    listOf(
                                        dfBase to dfBuffer,
                                        baseAddr to buffer
                                    )
                                )
                            } else {
                                FirmwareParser.toIntelHex(buffer, baseAddr)
                            }
                            out.write(hexContent.toByteArray(Charsets.US_ASCII))
                        } else {
                            val payload = if (isMergedMode && chipConfig != null) {
                                FirmwareParser.buildMergedMemoryMap(
                                    chipConfig = chipConfig,
                                    programFlashBytes = buffer,
                                    dataFlashBytes = dfBuffer
                                )
                            } else {
                                buffer
                            }
                            savedByteCount = payload.size
                            out.write(payload)
                        }
                    } ?: throw IllegalStateException("تعذر الكتابة إلى المسار المختار.")
                }

                val mergeInfo = if (!asIntelHex && isMergedMode && chipConfig != null) {
                    val endHex = String.format(Locale.US, "0x%05X", chipConfig.programEndAddress)
                    val formattedBytes = String.format(Locale.US, "%,d", savedByteCount)
                    val dfLabel = if (dfBuffer != null && chipConfig.dataFlashStartAddress != null) {
                        "شامل Data Flash @ ${String.format(Locale.US, "0x%05X", chipConfig.dataFlashStartAddress)}"
                    } else {
                        "Program @ ${String.format(Locale.US, "0x%05X", chipConfig.programStartAddress)}"
                    }
                    " [مدمج خريطة كاملة 0x00000..$endHex • $formattedBytes بايت / ${savedByteCount / 1024} KiB • $dfLabel]"
                } else if (asIntelHex && isMergedMode && dfBuffer != null) {
                    val mapSize = (chipConfig?.programEndAddress ?: 0) + 1
                    " [مدمج Intel HEX: Program + Data Flash @ ${String.format(Locale.US, "0x%05X", dfBase)} (نطاق ${String.format(Locale.US, "%,d", mapSize)} بايت / ${mapSize / 1024} KiB)]"
                } else {
                    " [Program Flash ${String.format(Locale.US, "%,d", buffer.size)} بايت / ${buffer.size / 1024} KiB]"
                }
                addLog(
                    "تم حفظ السوفت المسحوب بنجاح (${if (asIntelHex) "Intel HEX" else "BIN"})$mergeInfo",
                    LogLevel.SUCCESS
                )
            } catch (e: Throwable) {
                addLog("خطأ أثناء حفظ الملف: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun saveDataFlashToUri(uri: Uri, asIntelHex: Boolean) {
        val state = _uiState.value
        val dfBuffer = state.dataFlashBuffer ?: run {
            addLog("لا توجد بيانات Data Flash مقروءة للحفظ بعد.", LogLevel.WARNING)
            return
        }
        val dfBase = state.dataFlashBaseAddress
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        if (asIntelHex) {
                            out.write(FirmwareParser.toIntelHex(dfBuffer, dfBase).toByteArray(Charsets.US_ASCII))
                        } else {
                            out.write(dfBuffer)
                        }
                    } ?: throw IllegalStateException("تعذر الكتابة إلى المسار المختار.")
                }
                addLog(
                    "تم حفظ Data Flash (${dfBuffer.size} بايت @ ${String.format(Locale.US, "0x%05X", dfBase)}) بصيغة ${if (asIntelHex) "Intel HEX" else "BIN"}.",
                    LogLevel.SUCCESS
                )
            } catch (e: Throwable) {
                addLog("خطأ أثناء حفظ Data Flash: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun readChipFlash() = execute78k0OrStandardRead(resumeSession = null)

    fun rescueOrResume78k0Flash(session: Nec78k0RecoveryManager.Nec78k0RecoverySession? = null) {
        val selectedPart = _uiState.value.selectedProcessor?.partNumber
        val target = session
            ?: selectedPart?.let { Nec78k0RecoveryManager.findSessionForChip(appContext, it) }
            ?: _uiState.value.pendingRescueSessions.firstOrNull()
            ?: Nec78k0RecoveryManager.findAnyPendingSession(appContext, selectedPart)
        if (target == null) {
            addLog("لا توجد جلسة إنقاذ 78K0 غير مكتملة حالياً.", LogLevel.INFO)
            return
        }
        val matchedProc = Nec78k0ProtocolEngine.findMatchingCatalogProcessor(target.chipName, allProcessors)
        if (matchedProc != null) {
            applySelectedMcuHierarchy(matchedProc, clearSearch = true, markUserSelected = true)
        }
        _uiState.update { it.copy(currentStep = 3, watchdogAlertMessage = null) }
        execute78k0OrStandardRead(resumeSession = target)
    }

    fun reset78k0BlockOffsetAndResume(session: Nec78k0RecoveryManager.Nec78k0RecoverySession? = null) {
        if (_uiState.value.isBusy) return
        val selectedPart = _uiState.value.selectedProcessor?.partNumber
        val target = session
            ?: selectedPart?.let { Nec78k0RecoveryManager.findSessionForChip(appContext, it) }
            ?: _uiState.value.pendingRescueSessions.firstOrNull()
            ?: Nec78k0RecoveryManager.findAnyPendingSession(appContext, selectedPart)

        if (target == null) {
            _uiState.update { it.copy(currentStep = 3, watchdogAlertMessage = null) }
            addLog("لا توجد جلسة محفوظة — بدء قراءة نظيفة من بداية الكتلة (Offset=0)...", LogLevel.INFO)
            execute78k0OrStandardRead(resumeSession = null)
            return
        }

        val resetSession = Nec78k0RecoveryManager.resetBlockOffsetForCleanResume(appContext, target)
        refresh78k0RescueSessions()
        val matchedProc = Nec78k0ProtocolEngine.findMatchingCatalogProcessor(resetSession.chipName, allProcessors)
        if (matchedProc != null) {
            applySelectedMcuHierarchy(matchedProc, clearSearch = true, markUserSelected = true)
        }
        _uiState.update { it.copy(currentStep = 3, watchdogAlertMessage = null, watchdogPausedBlock = null) }
        addLog(
            "تصفير إزاحة البايت للكتلة (Byte Offset -> 0) مع الاحتفاظ بـ FullCS=${resetSession.fullChecksumHex} و Block0CS=${resetSession.block0ChecksumHex} والكتل المنجزة (${resetSession.lastCompletedBlock.coerceAtLeast(0)})...",
            LogLevel.INFO
        )
        execute78k0OrStandardRead(resumeSession = resetSession)
    }

    private fun execute78k0OrStandardRead(resumeSession: Nec78k0RecoveryManager.Nec78k0RecoverySession?) {
        val mcu = _uiState.value.selectedProcessor ?: return
        if (_uiState.value.isBusy) return
        val idBytes = parseIdCodeBytes() ?: run {
            addLog("كود ID Code غير مكتمل: أدخل 14 حرف Hex بالضبط (7 بايت).", LogLevel.ERROR)
            return
        }
        val dfMode = _uiState.value.dataFlashMode
        val activeResume = when {
            resumeSession != null -> resumeSession
            mcu.is78k0Flmd0Mode() -> Nec78k0RecoveryManager.findSessionForChip(appContext, mcu.partNumber)
            else -> null
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0.01f,
                    progressBytesDone = 0,
                    progressTotalBytes = mcu.flashSizeBytes,
                    progressLabel = if (activeResume != null) "استئناف إنقاذ 78K0..." else "بدء قراءة الفلاش...",
                    watchdogAlertMessage = null
                )
            }
            addLog(
                when {
                    activeResume != null && activeResume.lastCompletedBlock >= 0 ->
                        "بدء إنقاذ واستكمال سحب ${activeResume.chipName} من الكتلة ${activeResume.lastCompletedBlock + 1} (FullCS=${activeResume.fullChecksumHex} • Block0CS=${activeResume.block0ChecksumHex})..."
                    activeResume != null ->
                        "استرجاع البصمة المحفوظة لـ ${activeResume.chipName} (FullCS=${activeResume.fullChecksumHex} • Block0CS=${activeResume.block0ChecksumHex}) ومتابعة السحب..."
                    else -> "بدء قراءة ذاكرة ${mcu.partNumber} (${mcu.flashKb}KB)..."
                },
                LogLevel.INFO
            )

            try {
                when (val res = UsbOtgProgrammerService.executeReadFlash(
                    context = appContext,
                    preferredChip = _uiState.value.selectedProgrammer,
                    baudRate = if (mcu.is78k0Flmd0Mode()) 9600 else _uiState.value.baudRate,
                    enableDtrRtsReset = if (mcu.is78k0Flmd0Mode()) true else _uiState.value.enableDtrRtsReset,
                    processor = mcu,
                    idCodeBytes = idBytes,
                    dataFlashMode = dfMode,
                    clockFrequency = _uiState.value.clockFrequency,
                    catalog = allProcessors,
                    resumeSession = activeResume,
                    preDetected78k0Info = _uiState.value.nec78k0DetectedInfo,
                    autoRestoreChip = _uiState.value.autoRestoreChip,
                    on78k0DetectedInfo = { info ->
                        info.matchedProcessor?.let { matched ->
                            if (_uiState.value.selectedProcessor?.partNumber != matched.partNumber) {
                                applySelectedMcuHierarchy(matched, clearSearch = true, markUserSelected = true, preserveLoadedFirmware = true)
                            }
                        }
                        _uiState.update { it.copy(nec78k0DetectedInfo = info) }
                    },
                    onLog = { msg -> addLog(msg, LogLevel.INFO) },
                    onProgress = { done, total, msg ->
                        val frac = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
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
                                dataFlashBuffer = res.dataFlashBytes,
                                dataFlashBaseAddress = res.dataFlashBaseAddress,
                                viewingDataFlashTab = false,
                                loadedFileName = "Dump_${mcu.partNumber}.bin",
                                loadedFormat = "Chip Read",
                                loadedNonEmptyCount = data.count { b -> b != 0xFF.toByte() },
                                firstNonEmptyRowIndex = firstDataRow,
                                hexJumpSequence = it.hexJumpSequence + 1,
                                watchdogAlertMessage = null,
                                watchdogPausedBlock = null,
                                isCurrentDumpMcuVerified = res.isMcuVerified
                            )
                        }
                        refresh78k0RescueSessions()
                        addLog(res.message, LogLevel.SUCCESS)
                    }
                    is FlashOperationResult.Failure -> {
                        val rawReason = res.reason
                        if (rawReason.startsWith(Nec78k0ProtocolEngine.WATCHDOG_PREFIX)) {
                            val payload = rawReason.removePrefix(Nec78k0ProtocolEngine.WATCHDOG_PREFIX)
                            val colonIdx = payload.indexOf(':')
                            val pausedIdx = if (colonIdx > 0) payload.substring(0, colonIdx).toIntOrNull() ?: 0 else 0
                            val cleanMsg = if (colonIdx >= 0) payload.substring(colonIdx + 1) else payload
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    watchdogAlertMessage = cleanMsg,
                                    watchdogPausedBlock = pausedIdx
                                )
                            }
                            refresh78k0RescueSessions()
                            addLog("Watchdog Pause: $cleanMsg", LogLevel.WARNING)
                        } else {
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    progress = 0f,
                                    progressBytesDone = 0,
                                    progressTotalBytes = 0,
                                    progressLabel = ""
                                )
                            }
                            refresh78k0RescueSessions()
                            addLog(rawReason, LogLevel.ERROR)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, progressLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 0f,
                        progressBytesDone = 0,
                        progressTotalBytes = 0,
                        progressLabel = ""
                    )
                }
                addLog("خطأ غير متوقع أثناء القراءة: ${t.toString()}", LogLevel.ERROR)
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
        val dfMode = _uiState.value.dataFlashMode

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
            try {
                when (val res = UsbOtgProgrammerService.executeErase(
                    context = appContext,
                    preferredChip = _uiState.value.selectedProgrammer,
                    baudRate = if (mcu.is78k0Flmd0Mode()) 9600 else _uiState.value.baudRate,
                    enableDtrRtsReset = if (mcu.is78k0Flmd0Mode()) true else _uiState.value.enableDtrRtsReset,
                    processor = mcu,
                    eraseMode = _uiState.value.eraseMode,
                    idCodeBytes = idBytes,
                    dataFlashMode = dfMode,
                    clockFrequency = _uiState.value.clockFrequency,
                    catalog = allProcessors,
                    preDetected78k0Info = _uiState.value.nec78k0DetectedInfo,
                    on78k0DetectedInfo = { info ->
                        info.matchedProcessor?.let { matched ->
                            if (_uiState.value.selectedProcessor?.partNumber != matched.partNumber) {
                                applySelectedMcuHierarchy(matched, clearSearch = true, markUserSelected = true, preserveLoadedFirmware = true)
                            }
                        }
                        _uiState.update { it.copy(nec78k0DetectedInfo = info) }
                    },
                    onLog = { msg -> addLog(msg, LogLevel.INFO) },
                    onProgress = { done, total, msg ->
                        val frac = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
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
                                flashBaseAddress = defaultBaseAddressFor(mcu),
                                dataFlashBuffer = if (mcu.protocolType == ProtocolFamilyType.R8C_M16C || dfMode != DataFlashExportMode.EXCLUDE) null else it.dataFlashBuffer,
                                viewingDataFlashTab = false,
                                idCodeBytesHex = if (mcu.protocolType == ProtocolFamilyType.R8C_M16C) {
                                    List(7) { "FF" }
                                } else {
                                    it.idCodeBytesHex
                                },
                                loadedNonEmptyCount = 0,
                                firstNonEmptyRowIndex = 0,
                                isCurrentDumpMcuVerified = false
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
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, progressLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isBusy = false, progress = 0f, progressBytesDone = 0, progressTotalBytes = 0, progressLabel = "")
                }
                addLog("خطأ غير متوقع أثناء المسح: ${t.toString()}", LogLevel.ERROR)
            }
        }
    }

    fun writeChipFlash() = runWriteOrVerify(verifyOnly = false)
    fun verifyChipFlash() = runWriteOrVerify(verifyOnly = true)

    private fun runWriteOrVerify(verifyOnly: Boolean) {
        val mcu = _uiState.value.selectedProcessor ?: return
        if (_uiState.value.isBusy) return
        val idBytes = parseIdCodeBytes() ?: if (mcu.is78k0Flmd0Mode()) {
            ByteArray(7) { 0xFF.toByte() }
        } else {
            addLog("كود ID Code غير مكتمل: أدخل حرفين Hex في كل خانة.", LogLevel.ERROR)
            return
        }
        val dfMode = _uiState.value.dataFlashMode
        val dfBytes = _uiState.value.dataFlashBuffer
        val autoErase = _uiState.value.autoEraseBeforeWrite
        val autoVerify = _uiState.value.autoVerifyAfterWrite

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

            try {
                when (val res = UsbOtgProgrammerService.executeWriteAndVerify(
                    context = appContext,
                    preferredChip = _uiState.value.selectedProgrammer,
                    baudRate = if (mcu.is78k0Flmd0Mode()) 9600 else _uiState.value.baudRate,
                    enableDtrRtsReset = if (mcu.is78k0Flmd0Mode()) true else _uiState.value.enableDtrRtsReset,
                    processor = mcu,
                    firmwareBytes = _uiState.value.flashBuffer,
                    firmwareStartAddress = _uiState.value.flashBaseAddress,
                    idCodeBytes = idBytes,
                    verifyOnly = verifyOnly,
                    dataFlashMode = dfMode,
                    dataFlashBytes = dfBytes,
                    autoEraseBeforeWrite = autoErase,
                    autoVerifyAfterWrite = autoVerify,
                    eraseMode = _uiState.value.eraseMode,
                    clockFrequency = _uiState.value.clockFrequency,
                    catalog = allProcessors,
                    preDetected78k0Info = _uiState.value.nec78k0DetectedInfo,
                    on78k0DetectedInfo = { info ->
                        info.matchedProcessor?.let { matched ->
                            if (_uiState.value.selectedProcessor?.partNumber != matched.partNumber) {
                                applySelectedMcuHierarchy(matched, clearSearch = true, markUserSelected = true, preserveLoadedFirmware = true)
                            }
                        }
                        _uiState.update { it.copy(nec78k0DetectedInfo = info) }
                    },
                    onLog = { msg -> addLog(msg, LogLevel.INFO) },
                    onProgress = { done, total, msg ->
                        val frac = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
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
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, progressLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isBusy = false, progress = 0f, progressBytesDone = 0, progressTotalBytes = 0, progressLabel = "")
                }
                addLog("خطأ غير متوقع أثناء $actionName: ${t.toString()}", LogLevel.ERROR)
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
            mcu.programStartAddress
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
        val effectiveLevel = if (level == LogLevel.INFO) {
            when {
                message.contains("[WARN]") || message.contains("⚠️") -> LogLevel.WARNING
                message.contains("[ERR]") || message.contains("❌") -> LogLevel.ERROR
                else -> level
            }
        } else {
            level
        }
        val ts = synchronized(timeFormatLock) {
            timeFormat.format(Date())
        }
        _uiState.update { state ->
            val updated = (state.logs + ProgrammerLogEntry(ts, message, effectiveLevel)).takeLast(300)
            state.copy(logs = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Keep Singleton UsbOtgProgrammerService open across screen/tab navigation
    }
}
