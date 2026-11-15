package com.example.ui.screens.programmer

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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Power
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.model.LogLevel
import com.example.data.model.ProtocolFamilyType
import com.example.ui.viewmodel.McuProgrammerUiState
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgrammerStep3Flasher(
    state: McuProgrammerUiState,
    onToggleConnection: () -> Unit,
    onBaudRateChange: (Int) -> Unit,
    onIdCodeByteChange: (Int, String) -> Unit,
    onResetIdCode: () -> Unit,
    onClockChange: (String) -> Unit,
    onEraseModeChange: (String) -> Unit,
    onJumpToFirstData: () -> Unit,
    onLoadFirmwareUri: (android.net.Uri) -> Unit,
    onSaveDumpUri: (android.net.Uri, Boolean) -> Unit,
    onReadFlash: () -> Unit,
    onWriteFlash: () -> Unit,
    onEraseFlash: () -> Unit,
    onVerifyFlash: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mcu = state.selectedProcessor ?: return
    val scrollState = rememberScrollState()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Target MCU & USB Connection Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleConnection,
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isConnected) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("usb_connect_toggle_button")
                    ) {
                        Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isConnected) "قطع الاتصال" else "اتصال USB OTG",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    BaudRateSelector(
                        selectedBaud = state.baudRate,
                        enabled = !state.isConnected && !state.isBusy,
                        onSelect = onBaudRateChange,
                        modifier = Modifier.weight(0.85f)
                    )
                }
            }
        }

        // 2. Dynamic Protocol Section (7 Separate Hex Byte Boxes with Auto-Focus for R8C/M16C)
        when (mcu.protocolType) {
            ProtocolFamilyType.R8C_M16C -> {
                IdCodeSevenBoxesCard(
                    idCodeBytesHex = state.idCodeBytesHex,
                    onByteChange = onIdCodeByteChange,
                    onResetFf = onResetIdCode
                )
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
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
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
                            .heightIn(min = 46.dp)
                            .testTag("save_bin_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ BIN", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { saveHexLauncher.launch("${mcu.partNumber}_dump.hex") },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("save_hex_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير HEX", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Text(
                    text = if (state.loadedFileName != null) {
                        "الملف النشط: ${state.loadedFileName} (${state.loadedFormat}) • ${state.loadedNonEmptyCount} بايت بيانات"
                    } else {
                        "الذاكرة مهيأة على 0xFF بحجم ${mcu.flashKb} KB (${mcu.flashSizeBytes} بايت)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 4. Professional PC-Style Hex View (Address : 16 Hex Bytes | ASCII + Fast Scrollbar)
        PcStyleHexViewerCard(
            buffer = state.flashBuffer,
            firstNonEmptyRowIndex = state.firstNonEmptyRowIndex,
            jumpSequence = state.hexJumpSequence,
            onJumpToDataClick = onJumpToFirstData
        )

        // 5. Flash Operations Grid + Linear Progress Bar with Percentage & KB Counter
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
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
                                    maxLines = 1,
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
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "مسح السجل",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.logs.reversed().forEach { entry ->
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
private fun PcStyleHexViewerCard(
    buffer: ByteArray,
    firstNonEmptyRowIndex: Int,
    jumpSequence: Int,
    onJumpToDataClick: () -> Unit
) {
    val totalRows = ((buffer.size + 15) / 16).coerceAtLeast(1)
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(jumpSequence) {
        if (jumpSequence > 0 && firstNonEmptyRowIndex in 0 until totalRows) {
            listState.scrollToItem(firstNonEmptyRowIndex)
        }
    }

    val firstVisibleRow by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val currentAddrHex = remember(firstVisibleRow) { "0x%08X".format(firstVisibleRow * 16) }

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
                Column {
                    Text(
                        text = "عارض السوفت وير الاحترافي (PC Hex View)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الموقع الحالي: $currentAddrHex • الحجم الكلي: ${buffer.size / 1024} KB",
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
                            text = "أول بيانات (0x%06X)".format(firstNonEmptyRowIndex * 16),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                                    val rowLen = min(16, buffer.size - rowAddr)
                                    val lineStr = remember(buffer, rowAddr, rowLen) {
                                        buildPcHexLine(buffer, rowAddr, rowLen)
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

private fun buildPcHexLine(buffer: ByteArray, addr: Int, len: Int): String {
    val sb = StringBuilder(82)
    sb.append("%08X: ".format(addr))
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
