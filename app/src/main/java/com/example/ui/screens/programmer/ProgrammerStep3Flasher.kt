package com.example.ui.screens.programmer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LogLevel
import com.example.data.model.ProtocolFamilyType
import com.example.ui.viewmodel.McuProgrammerUiState
import kotlin.math.min

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
    onSelectBlock: (Int) -> Unit,
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
                    // Connect / Disconnect USB Button
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
                            fontSize = 13.sp
                        )
                    }

                    // Baud Rate Dropdown
                    BaudRateSelector(
                        selectedBaud = state.baudRate,
                        enabled = !state.isConnected && !state.isBusy,
                        onSelect = onBaudRateChange,
                        modifier = Modifier.weight(0.85f)
                    )
                }
            }
        }

        // 2. Dynamic Protocol Section (Adapts by MCU Family!)
        when (mcu.protocolType) {
            ProtocolFamilyType.R8C_M16C -> {
                IdCodeSecurityCard(
                    idCodeBytesHex = state.idCodeBytesHex,
                    onByteChange = onIdCodeByteChange,
                    onResetDefault = onResetIdCode
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

        // 3. Firmware File Loader & Save Dump
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openFirmwareLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("load_firmware_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح سوفت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { saveBinLauncher.launch("${mcu.partNumber}_dump.bin") },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp)
                            .testTag("save_bin_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ BIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { saveHexLauncher.launch("${mcu.partNumber}_dump.hex") },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp)
                            .testTag("save_hex_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير HEX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = if (state.loadedFileName != null) {
                        "الملف النشط: ${state.loadedFileName} (${state.loadedFormat}) • ${state.loadedNonEmptyCount} بايت"
                    } else {
                        "الذاكرة مهيأة بقيمة 0xFF بحجم ${mcu.flashKb}KB (${mcu.flashSizeBytes} بايت)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 4. Hex & Block Viewer
        HexBlockViewerCard(
            buffer = state.flashBuffer,
            selectedBlockIndex = state.selectedBlockIndex,
            onSelectBlock = onSelectBlock
        )

        // 5. Flash Operations Grid (Read / Write / Erase / Verify)
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

                if (state.isBusy || state.progress > 0f) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.progressLabel,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.logs.reversed().forEach { entry ->
                            val logColor = when (entry.level) {
                                LogLevel.SUCCESS -> Color(0xFF34D399)
                                LogLevel.WARNING -> Color(0xFFFBBF24)
                                LogLevel.ERROR -> Color(0xFFF87171)
                                LogLevel.INFO -> Color(0xFFCBD5E1)
                            }
                            Text(
                                text = "[${entry.timestamp}] ${entry.message}",
                                fontSize = 11.sp,
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
private fun IdCodeSecurityCard(
    idCodeBytesHex: List<String>,
    onByteChange: (Int, String) -> Unit,
    onResetDefault: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "كود الأمان (7-Byte ID Code Hex)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onResetDefault) {
                    Text("الافتراضي FF", fontSize = 11.sp)
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    idCodeBytesHex.forEachIndexed { idx, hex ->
                        OutlinedTextField(
                            value = hex,
                            onValueChange = { onByteChange(idx, it) },
                            singleLine = true,
                            label = { Text("B${idx + 1}", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(56.dp)
                                .testTag("id_code_byte_$idx")
                        )
                    }
                }
            }
        }
    }
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
            value = "$selectedBaud bps",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Baud Rate", fontSize = 11.sp) },
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
private fun HexBlockViewerCard(
    buffer: ByteArray,
    selectedBlockIndex: Int,
    onSelectBlock: (Int) -> Unit
) {
    val blockSize = 1024 // 1KB per selectable memory block
    val totalBlocks = ((buffer.size + blockSize - 1) / blockSize).coerceAtLeast(1)
    val safeBlockIdx = selectedBlockIndex.coerceIn(0, totalBlocks - 1)
    val blockStart = safeBlockIdx * blockSize
    val blockEnd = min(buffer.size, blockStart + blockSize)
    val rowCount = ((blockEnd - blockStart) + 15) / 16

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "عارض الذاكرة (Hex & Block Viewer)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Block $safeBlockIdx / ${totalBlocks - 1}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Memory Block Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items((0 until totalBlocks).toList()) { idx ->
                    val offsetHex = "0x%05X".format(idx * blockSize)
                    FilterChip(
                        selected = idx == safeBlockIdx,
                        onClick = { onSelectBlock(idx) },
                        label = {
                            Text(
                                text = "B$idx ($offsetHex)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                }
            }

            // LTR Monospace Hex Grid
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF090E1A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        items(rowCount) { rowIdx ->
                            val rowAddr = blockStart + rowIdx * 16
                            val rowLen = min(16, blockEnd - rowAddr)
                            val lineStr = remember(buffer, rowAddr, rowLen) {
                                buildHexLine(buffer, rowAddr, rowLen)
                            }
                            val hasData = remember(buffer, rowAddr, rowLen) {
                                (0 until rowLen).any { buffer[rowAddr + it] != 0xFF.toByte() }
                            }
                            Text(
                                text = lineStr,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (hasData) Color(0xFF38BDF8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildHexLine(buffer: ByteArray, addr: Int, len: Int): String {
    val sb = StringBuilder(78)
    sb.append("%06X: ".format(addr))
    for (i in 0 until 16) {
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
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
