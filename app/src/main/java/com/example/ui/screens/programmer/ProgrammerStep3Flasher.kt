package com.example.ui.screens.programmer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataFlashExportMode
import com.example.data.model.LogLevel
import com.example.data.model.ProtocolFamilyType
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.NeuBadge
import com.example.ui.theme.NeuPillButton
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed
import com.example.ui.viewmodel.McuProgrammerUiState
import com.example.util.UniversalCheckpointManager
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgrammerStep3Flasher(
    state: McuProgrammerUiState,
    onToggleConnection: () -> Unit,
    onBaudRateChange: (Int) -> Unit,
    onToggleDtrRtsReset: (Boolean) -> Unit,
    onToggleEchoCancel: (Boolean) -> Unit,
    onIdCodeByteChange: (Int, String) -> Unit,
    onResetIdCode: () -> Unit,
    onClockChange: (String) -> Unit,
    onEraseModeChange: (String) -> Unit,
    onDataFlashModeChange: (DataFlashExportMode) -> Unit,
    onToggleViewingDataFlash: (Boolean) -> Unit,
    onToggleAutoEraseBeforeWrite: (Boolean) -> Unit,
    onToggleAutoVerifyAfterWrite: (Boolean) -> Unit,
    onToggleAutoRestoreChip: (Boolean) -> Unit = {},
    onJumpToFirstData: () -> Unit,
    onLoadFirmwareUri: (android.net.Uri) -> Unit,
    onSaveDumpUri: (android.net.Uri, Boolean) -> Unit,
    onSaveDataFlashUri: (android.net.Uri, Boolean) -> Unit,
    onReadFlash: () -> Unit,
    onWriteFlash: () -> Unit,
    onEraseFlash: () -> Unit,
    onVerifyFlash: () -> Unit,
    onTogglePauseResume: () -> Unit = {},
    onClearLogs: () -> Unit,
    onRescue78k0Session: (com.example.util.Nec78k0RecoveryManager.Nec78k0RecoverySession) -> Unit = {},
    onCleanResume78k0Session: (com.example.util.Nec78k0RecoveryManager.Nec78k0RecoverySession) -> Unit = {},
    onDismiss78k0Session: (com.example.util.Nec78k0RecoveryManager.Nec78k0RecoverySession) -> Unit = {},
    onResumeFromWatchdog: () -> Unit = {},
    onCleanResumeFromWatchdog: () -> Unit = {},
    onDismissWatchdog: () -> Unit = {},
    onResumeUniversalCheckpoint: (UniversalCheckpointManager.UniversalCheckpoint) -> Unit = {},
    onDismissUniversalCheckpoint: (UniversalCheckpointManager.UniversalCheckpoint) -> Unit = {},
    keepScreenOnEnabled: Boolean = true,
    onToggleKeepScreenOn: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mcu = state.selectedProcessor ?: return
    val chipConfig = mcu.chipConfig
    val is78k0 = mcu.is78k0Flmd0Mode()
    val scrollState = rememberScrollState()
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isDark = LocalIsDarkTheme.current

    val openFirmwareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onLoadFirmwareUri(uri)
    }
    val saveBinLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) onSaveDumpUri(uri, false)
    }
    val saveHexLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) onSaveDumpUri(uri, true)
    }
    val saveDataFlashBinLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) onSaveDataFlashUri(uri, false)
    }
    val saveDataFlashHexLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) onSaveDataFlashUri(uri, true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Target MCU & USB Connection Card
        Card(
            shape = RoundedCornerShape(if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = if (isNeu) Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark) else Modifier
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${mcu.partNumber} • ${mcu.flashKb}KB Flash",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${mcu.mainFamily} / ${mcu.subFamily} • ${state.selectedProgrammer.chipModel}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (mcu.protocolType == ProtocolFamilyType.R8C_M16C) {
                            val progRange = String.format(
                                Locale.US,
                                "Program: 0x%05X..0x%05X (%d KB)",
                                chipConfig.programStartAddress,
                                chipConfig.programEndAddress,
                                chipConfig.flashSizeBytes / 1024
                            )
                            val dfRange = if (chipConfig.hasDataFlash && chipConfig.dataFlashStartAddress != null && chipConfig.dataFlashEndAddress != null) {
                                String.format(
                                    Locale.US,
                                    " • Data: 0x%05X..0x%05X",
                                    chipConfig.dataFlashStartAddress,
                                    chipConfig.dataFlashEndAddress
                                )
                            } else ""
                            Text(
                                text = progRange + dfRange,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.isConnected) Color(0xFF10B981).copy(alpha = 0.16f)
                        else Color(0xFF64748B).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (state.isConnected) "متصل OTG" else "غير متصل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (state.connectedDeviceLabel.isNotBlank()) {
                    Text(
                        text = state.connectedDeviceLabel,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNeu) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isNeu) Modifier.neuRecessed(cornerRadius = 14.dp, isDark = isDark) else Modifier)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اتصال USB OTG",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (state.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = state.isConnected,
                                onCheckedChange = { onToggleConnection() },
                                enabled = !state.isBusy,
                                modifier = Modifier.testTag("usb_connect_toggle_switch")
                            )
                        }
                    }

                    BaudRateSelector(
                        selectedBaud = state.baudRate,
                        enabled = !state.isConnected && !state.isBusy && !is78k0,
                        onSelect = onBaudRateChange,
                        modifier = Modifier.weight(0.95f)
                    )
                }

                if (!is78k0) {
                    // DTR / RTS Reset Control Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تفعيل نبضة DTR/RTS Reset",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.enableDtrRtsReset) "إرسال نبضة Reset قبل المزامنة" else "تعطيل DTR/RTS (توصيل مباشر 4 أسلاك)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.enableDtrRtsReset,
                                onCheckedChange = onToggleDtrRtsReset,
                                enabled = !state.isBusy,
                                modifier = Modifier.testTag("step3_dtr_rts_switch")
                            )
                        }
                    }

                    // D1 Diode Echo Cancellation Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إلغاء صدى الدايود D1 (Echo Cancellation)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.enableEchoCancellation) "حذف بايتات الصدى المارة عبر D1 (TX↔RX) تلقائياً" else "توصيل بسلكين منفصلين بدون دايود D1",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.enableEchoCancellation,
                                onCheckedChange = onToggleEchoCancel,
                                enabled = !state.isBusy,
                                modifier = Modifier.testTag("step3_echo_cancel_switch")
                            )
                        }
                    }
                }

                // Keep Screen On During Long Operations Switch (with Background FGS fallback)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إبقاء الشاشة مضاءة أثناء البرمجة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "تستمر الخدمة في الخلفية مع إشعار النسبة (%) عند إغلاق الشاشة",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepScreenOnEnabled,
                            onCheckedChange = onToggleKeepScreenOn,
                            modifier = Modifier.testTag("step3_keep_screen_on_switch")
                        )
                    }
                }
            }
        }

        // 2. Dynamic Protocol Section (7 Separate Hex Byte Boxes + Data Flash option for R8C/M16C)
        when (mcu.protocolType) {
            ProtocolFamilyType.R8C_M16C -> {
                IdCodeSevenBoxesCard(
                    idCodeBytesHex = state.idCodeBytesHex,
                    onByteChange = onIdCodeByteChange,
                    onResetFf = onResetIdCode
                )
                if (chipConfig.hasDataFlash && chipConfig.dataFlashStartAddress != null && chipConfig.dataFlashEndAddress != null) {
                    DataFlashModeCard(
                        programEndAddress = chipConfig.programEndAddress,
                        dataFlashStart = chipConfig.dataFlashStartAddress,
                        dataFlashEnd = chipConfig.dataFlashEndAddress,
                        dataFlashSizeBytes = chipConfig.dataFlashSizeBytes,
                        selectedMode = state.dataFlashMode,
                        enabled = !state.isBusy,
                        onSelectMode = onDataFlashModeChange
                    )
                }
            }
            ProtocolFamilyType.RL78_RX -> {
                ClockAndEraseSettingsCard(
                    selectedClock = state.clockFrequency,
                    selectedEraseMode = state.eraseMode,
                    onClockChange = onClockChange,
                    onEraseModeChange = onEraseModeChange
                )
            }
            else -> Unit
        }

        // 3. Firmware File Loader & Export Card (Clean non-wrapping button rows)
        Card(
            shape = RoundedCornerShape(if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = if (isNeu) Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark) else Modifier
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "إدارة ملف السوفت وير (.bin / .hex / .mot)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = { openFirmwareLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("load_firmware_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فتح ملف سوفت وير (.bin / .hex / .mot)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                val isMergedMode = state.dataFlashMode == DataFlashExportMode.MERGED &&
                    mcu.protocolType == ProtocolFamilyType.R8C_M16C
                val fullMergedSizeBytes = chipConfig.programEndAddress + 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { saveBinLauncher.launch("${mcu.partNumber}_dump.bin") },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_bin_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMergedMode) "حفظ BIN (${fullMergedSizeBytes / 1024} KiB)" else "حفظ BIN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { saveHexLauncher.launch("${mcu.partNumber}_dump.hex") },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_hex_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMergedMode && state.dataFlashBuffer != null) "تصدير HEX (مدمج)" else "تصدير HEX",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                if (state.dataFlashMode == DataFlashExportMode.SEPARATE_FILE && state.dataFlashBuffer != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveDataFlashBinLauncher.launch("${mcu.partNumber}_dataflash.bin") },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_dataflash_bin_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ Data BIN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { saveDataFlashHexLauncher.launch("${mcu.partNumber}_dataflash.hex") },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_dataflash_hex_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير Data HEX", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                val dfLoadedSummary = state.dataFlashBuffer?.let { df ->
                    " + Data Flash (${df.size} B)"
                } ?: ""
                val mergedSummary = if (isMergedMode) {
                    String.format(
                        Locale.US,
                        " • حفظ BIN الكامل: 0x00000..0x%05X (%,d بايت / %d KiB)",
                        chipConfig.programEndAddress,
                        fullMergedSizeBytes,
                        fullMergedSizeBytes / 1024
                    )
                } else ""
                Text(
                    text = if (state.loadedFileName != null) {
                        "الملف النشط: ${state.loadedFileName} (${state.loadedFormat}) • ${state.loadedNonEmptyCount} بايت بيانات$dfLoadedSummary$mergedSummary"
                    } else {
                        "الذاكرة مهيأة على 0xFF بحجم ${chipConfig.flashSizeBytes / 1024} KB (${chipConfig.flashSizeBytes} بايت)$mergedSummary"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 4. Professional PC-Style Hex View (Program Flash / Data Flash tabs + Fast Scrollbar)
        val activeHexBuffer = if (state.viewingDataFlashTab && state.dataFlashBuffer != null) {
            state.dataFlashBuffer
        } else {
            state.flashBuffer
        }
        val activeHexBase = if (state.viewingDataFlashTab && state.dataFlashBuffer != null) {
            state.dataFlashBaseAddress
        } else {
            state.flashBaseAddress
        }
        val activeFirstRow = remember(activeHexBuffer, state.viewingDataFlashTab, state.firstNonEmptyRowIndex) {
            if (!state.viewingDataFlashTab) {
                state.firstNonEmptyRowIndex
            } else {
                var row = 0
                for (i in activeHexBuffer.indices) {
                    if (activeHexBuffer[i] != 0xFF.toByte()) {
                        row = i / 16
                        break
                    }
                }
                row
            }
        }
        PcStyleHexViewerCard(
            buffer = activeHexBuffer,
            baseAddress = activeHexBase,
            firstNonEmptyRowIndex = activeFirstRow,
            jumpSequence = state.hexJumpSequence,
            hasDataFlashBuffer = state.dataFlashBuffer != null,
            viewingDataFlashTab = state.viewingDataFlashTab,
            onToggleViewingDataFlash = onToggleViewingDataFlash,
            onJumpToDataClick = onJumpToFirstData
        )

        // 5. Flash Operations Grid + Linear Progress Bar with Percentage, KB Counter, ETA & Checkpoint Banner
        Card(
            shape = RoundedCornerShape(if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = if (isNeu) Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark) else Modifier
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "عمليات البرمجة والتحكم بالذاكرة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlashActionButton(
                        title = "قراءة (Read)",
                        icon = Icons.Default.CloudDownload,
                        color = Color(0xFF0284C7),
                        enabled = !state.isBusy,
                        onClick = onReadFlash,
                        testTag = "flash_read_button",
                        modifier = Modifier.weight(1f)
                    )
                    FlashActionButton(
                        title = "كتابة (Write)",
                        icon = Icons.Default.Upload,
                        color = Color(0xFF10B981),
                        enabled = !state.isBusy,
                        onClick = onWriteFlash,
                        testTag = "flash_write_button",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlashActionButton(
                        title = "مسح (Erase)",
                        icon = Icons.Default.CleaningServices,
                        color = Color(0xFFEF4444),
                        enabled = !state.isBusy,
                        onClick = onEraseFlash,
                        testTag = "flash_erase_button",
                        modifier = Modifier.weight(1f)
                    )
                    FlashActionButton(
                        title = "مطابقة (Verify)",
                        icon = Icons.Default.Verified,
                        color = Color(0xFFF59E0B),
                        enabled = !state.isBusy,
                        onClick = onVerifyFlash,
                        testTag = "flash_verify_button",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.isBusy) {
                    val pauseColor = if (state.isPaused) Color(0xFF10B981) else Color(0xFFF59E0B)
                    if (isNeu) {
                        NeuPillButton(
                            onClick = onTogglePauseResume,
                            isActive = state.isPaused,
                            isDark = isDark,
                            accentColor = pauseColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("flash_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = pauseColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isPaused) "استكمال العملية (Resume)" else "إيقاف مؤقت (Pause)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = pauseColor
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTogglePauseResume,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, pauseColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = pauseColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("flash_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isPaused) "استكمال العملية (Resume)" else "إيقاف مؤقت (Pause)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (mcu.protocolType == ProtocolFamilyType.R8C_M16C || is78k0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (is78k0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val chipEraseSelected = !state.eraseMode.contains("بلوك", ignoreCase = true) &&
                                        !state.eraseMode.contains("Block", ignoreCase = true)
                                    FilterChip(
                                        selected = chipEraseSelected,
                                        onClick = { onEraseModeChange("مسح كلي (Chip Erase)") },
                                        enabled = !state.isBusy,
                                        label = {
                                            Text("مسح كلي 0x20", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("nec78k0_chip_erase_chip")
                                    )
                                    FilterChip(
                                        selected = !chipEraseSelected,
                                        onClick = { onEraseModeChange("مسح بالبلوكات (Block Erase)") },
                                        enabled = !state.isBusy,
                                        label = {
                                            Text("مسح بالبلوكات 0x22", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("nec78k0_block_erase_chip")
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (is78k0) "مسح تلقائي قبل الكتابة (0x20 / 0x22)" else "مسح تلقائي قبل الكتابة (0xA7 D0)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (is78k0) "مسح الفلاش وتخطي بلوكات 0xFF الفارغة تلقائياً" else "مسح المعالج بالكامل وتخطي صفحات 0xFF تلقائياً",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.autoEraseBeforeWrite,
                                    onCheckedChange = onToggleAutoEraseBeforeWrite,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.testTag("auto_erase_before_write_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "التحقق التلقائي بعد الكتابة (Auto Verify)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (is78k0) "مطابقة البلوكات عبر 0x13 وفحص البصمة 0xB0" else "مطابقة الصفحات المكتوبة بايت ببايت عبر 0xFF",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.autoVerifyAfterWrite,
                                    onCheckedChange = onToggleAutoVerifyAfterWrite,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.testTag("auto_verify_after_write_switch")
                                )
                            }

                            if (is78k0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "الاستعادة التلقائية الفورية للمعالج (Auto-Restore)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "إعادة كتابة وتوثيق كل بلوك (0x22+0x40+0x13+0xB0) فور سحبه لضمان مطابقة 100%",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.autoRestoreChip,
                                        onCheckedChange = onToggleAutoRestoreChip,
                                        enabled = !state.isBusy,
                                        modifier = Modifier.testTag("nec78k0_auto_restore_switch")
                                    )
                                }
                            }
                        }
                    }
                }

                // Linear Progress Bar + Percentage + Done KB / Total KB
                val showProgress = state.isBusy || state.progress > 0f
                val pct = (state.progress.coerceIn(0f, 1f) * 100f).roundToInt()
                val totalKb = if (state.progressTotalBytes > 0) (state.progressTotalBytes / 1024) else mcu.flashKb
                val doneKb = if (state.progressTotalBytes > 0) (state.progressBytesDone / 1024) else 0

                if (showProgress) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flash_progress_container")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.progressLabel.ifBlank { "جاري التنفيذ..." },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$doneKb KB / $totalKb KB  •  $pct%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (pct >= 100) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("flash_progress_percentage")
                                )
                            }

                            LinearProgressIndicator(
                                progress = { state.progress.coerceIn(0f, 1f) },
                                color = if (pct >= 100) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .testTag("flash_linear_progress_bar")
                            )

                            if (state.isBusy && state.etaRemainingText.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (state.isPaused) "الحالة: متوقف مؤقتاً" else "جاري العمل في الخلفية...",
                                        fontSize = 10.5.sp,
                                        color = if (state.isPaused) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "الوقت المتبقي: ${state.etaRemainingText}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("flash_eta_text")
                                    )
                                }
                            }

                            if (!state.isBusy && state.progress >= 1f && state.loadedFormat == "Chip Read") {
                                Button(
                                    onClick = { saveBinLauncher.launch("${mcu.partNumber}_dump.bin") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(top = 4.dp)
                                        .testTag("progress_save_bin_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "حفظ الملف (.bin) — جاهز 100%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact Checkpoint / Disconnect Resume Banner directly below Progress Bar
                val activeCp = state.activeUniversalCheckpoint
                val active78k0Session = state.pendingRescueSessions.firstOrNull()
                val hasWatchdogAlert = !state.watchdogAlertMessage.isNullOrBlank()
                if (!state.isBusy && (activeCp != null || active78k0Session != null || hasWatchdogAlert)) {
                    val bannerTitle: String
                    val bannerDetail: String
                    val onResumeAction: () -> Unit
                    val onDismissAction: () -> Unit
                    if (activeCp != null) {
                        val opAr = if (activeCp.operation == UniversalCheckpointManager.OperationKind.READ) "القراءة" else "الكتابة"
                        bannerTitle = "تم حفظ نقطة استكمال ($opAr ${activeCp.partNumber})"
                        bannerDetail = "توقف عند ${activeCp.progressPercent}% (كتلة/صفحة ${activeCp.blockOrPageIndex}/${activeCp.totalBlocksOrPages}) — استكمال آمن دون مسح ما سبق"
                        onResumeAction = { onResumeUniversalCheckpoint(activeCp) }
                        onDismissAction = { onDismissUniversalCheckpoint(activeCp) }
                    } else if (active78k0Session != null) {
                        bannerTitle = "تم حفظ نقطة استكمال 78K0 (${active78k0Session.chipName})"
                        bannerDetail = "الكتل المكتملة: ${active78k0Session.lastCompletedBlock.coerceAtLeast(0)}/${active78k0Session.blockCount} • FullCS=${active78k0Session.fullChecksumHex}"
                        onResumeAction = { onRescue78k0Session(active78k0Session) }
                        onDismissAction = { onDismiss78k0Session(active78k0Session) }
                    } else {
                        bannerTitle = "توقف مؤقت آمن (يمكن الاستكمال)"
                        bannerDetail = state.watchdogAlertMessage.orEmpty()
                        onResumeAction = onResumeFromWatchdog
                        onDismissAction = onDismissWatchdog
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkpoint_resume_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bannerTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                    Text(
                                        text = bannerDetail,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                                IconButton(
                                    onClick = onDismissAction,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "تجاهل وحذف نقطة الاستكمال",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onResumeAction,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("resume_checkpoint_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استكمال العملية", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                if (hasWatchdogAlert || active78k0Session != null) {
                                    OutlinedButton(
                                        onClick = {
                                            if (active78k0Session != null) onCleanResume78k0Session(active78k0Session)
                                            else onCleanResumeFromWatchdog()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("من بداية الكتلة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Live Event Log Console
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "سجل الأحداث المباشر (Event Log)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                    val logContext = LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val fullLogText = if (state.logs.isEmpty()) {
                                    "جاهز للعمل — قم بتوصيل المبرمجة USB OTG للبدء."
                                } else {
                                    state.logs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                                }
                                val clipboard = logContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Event Log", fullLogText))
                                Toast.makeText(logContext, "تم نسخ سجل الأحداث بالكامل", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("copy_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "نسخ السجل",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "مسح السجل",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                if (state.logs.isEmpty()) {
                    Text(
                        text = "جاهز للعمل — قم بتوصيل المبرمجة USB OTG للبدء.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    val reversedLogs = remember(state.logs) { state.logs.asReversed() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 190.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(count = reversedLogs.size) { idx ->
                            val entry = reversedLogs[idx]
                            val isTx = entry.message.startsWith("TX:")
                            val isRxData = entry.message.startsWith("RX:") && !entry.message.contains("لا يوجد")
                            val logColor = when {
                                isRxData -> Color(0xFF34D399)
                                isTx -> Color(0xFFFBBF24)
                                entry.level == LogLevel.SUCCESS -> Color(0xFF34D399)
                                entry.level == LogLevel.WARNING -> Color(0xFFFBBF24)
                                entry.level == LogLevel.ERROR -> Color(0xFFF87171)
                                else -> Color(0xFFCBD5E1)
                            }
                            Text(
                                text = "[${entry.timestamp}] ${entry.message}",
                                fontSize = 11.sp,
                                fontFamily = if (isTx || entry.message.startsWith("RX:")) FontFamily.Monospace else FontFamily.Default,
                                color = logColor,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun IdCodeSevenBoxesCard(
    idCodeBytesHex: List<String>,
    onByteChange: (Int, String) -> Unit,
    onResetFf: () -> Unit
) {
    val focusRequesters = remember { List(7) { FocusRequester() } }
    val allValid = idCodeBytesHex.size == 7 && idCodeBytesHex.all { it.length == 2 }
    val combinedHex = idCodeBytesHex.joinToString(" ") { it.ifBlank { "--" } }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "كود الحماية (7-Byte ID Code Hex)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Byte 1 .. Byte 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (idx in 0..3) {
                            IdByteInputBox(
                                index = idx,
                                value = idCodeBytesHex.getOrElse(idx) { "FF" },
                                focusRequester = focusRequesters[idx],
                                imeAction = ImeAction.Next,
                                onValueChanged = { newVal ->
                                    val prevVal = idCodeBytesHex.getOrElse(idx) { "FF" }
                                    onByteChange(idx, newVal)
                                    val cleanNew = newVal.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
                                    val completedLen = when {
                                        prevVal.length == 2 && cleanNew.length == 3 -> 1
                                        else -> cleanNew.take(2).length
                                    }
                                    if (completedLen == 2 && idx < 6) {
                                        focusRequesters[idx + 1].requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 2: Byte 5 .. Byte 7 + Fill All FF Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        for (idx in 4..6) {
                            IdByteInputBox(
                                index = idx,
                                value = idCodeBytesHex.getOrElse(idx) { "FF" },
                                focusRequester = focusRequesters[idx],
                                imeAction = if (idx < 6) ImeAction.Next else ImeAction.Done,
                                onValueChanged = { newVal ->
                                    val prevVal = idCodeBytesHex.getOrElse(idx) { "FF" }
                                    onByteChange(idx, newVal)
                                    val cleanNew = newVal.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
                                    val completedLen = when {
                                        prevVal.length == 2 && cleanNew.length == 3 -> 1
                                        else -> cleanNew.take(2).length
                                    }
                                    if (completedLen == 2 && idx < 6) {
                                        focusRequesters[idx + 1].requestFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedButton(
                            onClick = onResetFf,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(54.dp)
                                .testTag("id_code_fill_ff_button")
                        ) {
                            Text(
                                text = "تعبئة الكل FF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allValid) "الكود الجاهز للإرسال (0xF5): $combinedHex" else "يرجى إكمال الحرفين في كل خانة (00 - FF)",
                    fontSize = 11.sp,
                    fontFamily = if (allValid) FontFamily.Monospace else FontFamily.Default,
                    color = if (allValid) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
            }
        }
    }
}

@Composable
private fun IdByteInputBox(
    index: Int,
    value: String,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 15.sp
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("id_code_byte_$index")
        )
        Text(
            text = "Byte ${index + 1}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DataFlashModeCard(
    programEndAddress: Int,
    dataFlashStart: Int,
    dataFlashEnd: Int,
    dataFlashSizeBytes: Int,
    selectedMode: DataFlashExportMode,
    enabled: Boolean,
    onSelectMode: (DataFlashExportMode) -> Unit
) {
    val sizeLabel = if (dataFlashSizeBytes >= 1024 && dataFlashSizeBytes % 1024 == 0) {
        "${dataFlashSizeBytes / 1024} KB"
    } else {
        "$dataFlashSizeBytes B"
    }
    val rangeText = String.format(Locale.US, "0x%05X..0x%05X • %s", dataFlashStart, dataFlashEnd, sizeLabel)
    val fullMapBytes = programEndAddress + 1

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ذاكرة Data Flash (اختياري)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = rangeText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DataFlashExportMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        enabled = enabled,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = mode.titleAr,
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("data_flash_mode_${mode.name.lowercase()}")
                    )
                }
            }

            val detailLine = if (selectedMode == DataFlashExportMode.MERGED) {
                String.format(
                    Locale.US,
                    "%s — الحجم الناتج: 0x00000..0x%05X (%,d بايت / %d KiB)",
                    selectedMode.subtitleAr,
                    programEndAddress,
                    fullMapBytes,
                    fullMapBytes / 1024
                )
            } else {
                selectedMode.subtitleAr
            }
            Text(
                text = detailLine,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PcStyleHexViewerCard(
    buffer: ByteArray,
    baseAddress: Int,
    firstNonEmptyRowIndex: Int,
    jumpSequence: Int,
    hasDataFlashBuffer: Boolean,
    viewingDataFlashTab: Boolean,
    onToggleViewingDataFlash: (Boolean) -> Unit,
    onJumpToDataClick: () -> Unit
) {
    val totalRows = ((buffer.size + 15) / 16).coerceAtLeast(1)
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(jumpSequence, viewingDataFlashTab) {
        if (firstNonEmptyRowIndex in 0 until totalRows && (jumpSequence > 0 || viewingDataFlashTab)) {
            listState.scrollToItem(firstNonEmptyRowIndex)
        }
    }

    val firstVisibleRow by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val currentAddrHex = remember(firstVisibleRow, baseAddress) {
        String.format(Locale.US, "0x%08X", baseAddress + firstVisibleRow * 16)
    }
    val sizeText = if (buffer.size >= 1024 && buffer.size % 1024 == 0) {
        "${buffer.size / 1024} KB"
    } else {
        "${buffer.size} B"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "عارض السوفت وير الاحترافي (PC Hex View)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الموقع الحالي: $currentAddrHex • الحجم: $sizeText",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (firstNonEmptyRowIndex > 0) {
                    TextButton(
                        onClick = onJumpToDataClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "أول بيانات (0x%06X)", baseAddress + firstNonEmptyRowIndex * 16),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (hasDataFlashBuffer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !viewingDataFlashTab,
                        onClick = { onToggleViewingDataFlash(false) },
                        label = { Text("Program Flash", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("hex_tab_program_flash")
                    )
                    FilterChip(
                        selected = viewingDataFlashTab,
                        onClick = { onToggleViewingDataFlash(true) },
                        label = { Text("Data Flash", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("hex_tab_data_flash")
                    )
                }
            }

            // Full PC-style Dark Console with Address + 16 Hex Bytes + ASCII + Draggable Fast Scrollbar
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF070B14),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .testTag("pc_hex_viewer_surface")
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left scrollable table (Header + Rows)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScrollState)
                                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            // Column Header
                            Text(
                                text = "Address   00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            HorizontalDivider(
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(totalRows) { rowIdx ->
                                    val rowAddr = rowIdx * 16
                                    val rowLen = min(16, (buffer.size - rowAddr).coerceAtLeast(0))
                                    val lineStr = remember(buffer, rowAddr, rowLen, baseAddress) {
                                        buildPcHexLine(buffer, rowAddr, rowLen, baseAddress)
                                    }
                                    val hasData = remember(buffer, rowAddr, rowLen) {
                                        (0 until rowLen).any { buffer[rowAddr + it] != 0xFF.toByte() }
                                    }
                                    Text(
                                        text = lineStr,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = if (hasData) Color(0xFF38BDF8) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Fast Interactive Vertical Scrollbar Track (PC-style thumb drag & tap)
                        BoxWithConstraints(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            val trackHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                            val thumbHeightDp = 36.dp
                            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
                            val usableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

                            val scrollFraction = if (totalRows <= 1) 0f
                            else (firstVisibleRow.toFloat() / (totalRows - 1).toFloat()).coerceIn(0f, 1f)
                            val thumbOffsetPx = (scrollFraction * usableTrackPx).roundToInt()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(totalRows, usableTrackPx) {
                                        detectTapGestures { offset ->
                                            val frac = ((offset.y - thumbHeightPx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                            val targetRow = (frac * (totalRows - 1)).roundToInt().coerceIn(0, totalRows - 1)
                                            coroutineScope.launch { listState.scrollToItem(targetRow) }
                                        }
                                    }
                                    .pointerInput(totalRows, usableTrackPx) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            val frac = ((change.position.y - thumbHeightPx / 2f) / usableTrackPx).coerceIn(0f, 1f)
                                            val targetRow = (frac * (totalRows - 1)).roundToInt().coerceIn(0, totalRows - 1)
                                            coroutineScope.launch { listState.scrollToItem(targetRow) }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, thumbOffsetPx) }
                                        .fillMaxWidth()
                                        .height(thumbHeightDp)
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildPcHexLine(buffer: ByteArray, addr: Int, len: Int, baseAddress: Int): String {
    val sb = StringBuilder(82)
    sb.append("%08X: ".format(baseAddress + addr))
    for (i in 0 until 16) {
        if (i == 8) sb.append(' ')
        if (i < len) {
            sb.append("%02X ".format(buffer[addr + i].toInt() and 0xFF))
        } else {
            sb.append("   ")
        }
    }
    sb.append("| ")
    for (i in 0 until len) {
        val c = buffer[addr + i].toInt() and 0xFF
        sb.append(if (c in 32..126) c.toChar() else '.')
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockAndEraseSettingsCard(
    selectedClock: String,
    selectedEraseMode: String,
    onClockChange: (String) -> Unit,
    onEraseModeChange: (String) -> Unit
) {
    val clocks = listOf("4 MHz", "8 MHz", "16 MHz", "20 MHz", "32 MHz")
    val eraseModes = listOf("مسح كلي (Chip Erase)", "مسح قطاعي (Block Erase)")
    var clockExpanded by remember { mutableStateOf(false) }
    var eraseExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "إعدادات التردد والمسح (RL78 / RX)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = clockExpanded,
                    onExpandedChange = { clockExpanded = !clockExpanded },
                    modifier = Modifier.weight(0.9f)
                ) {
                    OutlinedTextField(
                        value = selectedClock,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("تردد الكريستال", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clockExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = clockExpanded, onDismissRequest = { clockExpanded = false }) {
                        clocks.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    clockExpanded = false
                                    onClockChange(c)
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = eraseExpanded,
                    onExpandedChange = { eraseExpanded = !eraseExpanded },
                    modifier = Modifier.weight(1.1f)
                ) {
                    OutlinedTextField(
                        value = selectedEraseMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("وضع المسح", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eraseExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = eraseExpanded, onDismissRequest = { eraseExpanded = false }) {
                        eraseModes.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, fontSize = 12.sp) },
                                onClick = {
                                    eraseExpanded = false
                                    onEraseModeChange(m)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaudRateSelector(
    selectedBaud: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rates = listOf(9600, 19200, 38400, 57600, 115200)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = "$selectedBaud",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text("Baud Rate (bps)", fontSize = 11.sp, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            rates.forEach { rate ->
                DropdownMenuItem(
                    text = { Text("$rate bps") },
                    onClick = {
                        expanded = false
                        onSelect(rate)
                    }
                )
            }
        }
    }
}

@Composable
private fun FlashActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isDark = LocalIsDarkTheme.current
    if (isNeu) {
        NeuPillButton(
            onClick = onClick,
            enabled = enabled,
            isDark = isDark,
            accentColor = color,
            modifier = modifier
                .heightIn(min = 50.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else color.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (enabled) color else color.copy(alpha = 0.45f),
                maxLines = 1
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White
            ),
            modifier = modifier
                .heightIn(min = 50.dp)
                .testTag(testTag)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}
