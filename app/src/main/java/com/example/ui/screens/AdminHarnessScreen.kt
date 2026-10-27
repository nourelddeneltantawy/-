package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.data.model.MotorPinPresets
import com.example.ui.components.APPLIANCE_COMPONENTS
import com.example.ui.components.STANDARD_WIRE_COLORS
import com.example.ui.components.VisualBoardPinMarker
import com.example.ui.components.WireColorBadge
import com.example.util.ImageHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHarnessScreen(
    initialHarness: Harness?,
    onSaveSuccess: (Harness) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(initialHarness?.name ?: "") }
    var brand by remember { mutableStateOf(initialHarness?.brand ?: "") }
    var model by remember { mutableStateOf(initialHarness?.model ?: "") }
    var notes by remember { mutableStateOf(initialHarness?.notes ?: "") }
    var boardImageUrl by remember { mutableStateOf(initialHarness?.boardImageUrl ?: "") }

    val boardPins = remember { mutableStateListOf<HarnessPin>().apply { addAll(initialHarness?.boardPins ?: emptyList()) } }
    val bottomPins = remember { mutableStateListOf<HarnessPin>().apply { addAll(initialHarness?.bottomPins ?: emptyList()) } }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val localPath = ImageHelper.copyUriToLocalStorage(context, uri)
                    boardImageUrl = localPath
                    snackbarHostState.showSnackbar("تم حفظ صورة الكارتة بنجاح")
                } catch (e: Exception) {
                    boardImageUrl = uri.toString()
                    snackbarHostState.showSnackbar("تم تحديد الصورة")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("admin_harness_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (initialHarness == null) "إضافة خريطة ضفيرة جديدة" else "تعديل خريطة الضفيرة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: General Details Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بيانات الضفيرة الأساسية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم الضفيرة (مثال: ضفيرة غسالة إل جي انفرتر)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("الماركة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("الموديل") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات فنية عن الضفيرة وتوصيلاتها") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Section 2: Interactive Visual Pin Placement on Board Image
            item {
                VisualBoardPinMarker(
                    boardImageUrl = boardImageUrl,
                    pins = boardPins,
                    onUpdateBoardImageUrl = { boardImageUrl = it },
                    onPickImageClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAddPinAtPosition = { xPercent, yPercent ->
                        val nextNum = boardPins.size + 1
                        val defaultPreset = MotorPinPresets.PRESETS.firstOrNull { !it.isBlank } ?: MotorPinPresets.PRESETS.first()
                        boardPins.add(
                            HarnessPin(
                                pinNumber = nextNum,
                                label = "طرف $nextNum",
                                wireColor = defaultPreset.colorName,
                                colorHex = defaultPreset.colorHex,
                                secondaryColorHex = null,
                                secondaryColorName = null,
                                connectedComponent = "ماتور",
                                targetPinNumber = nextNum,
                                pinX = xPercent,
                                pinY = yPercent
                            )
                        )
                    },
                    onUpdatePin = { index, updated ->
                        if (index in boardPins.indices) {
                            boardPins[index] = updated
                        }
                    },
                    onDeletePin = { index ->
                        if (index in boardPins.indices) {
                            boardPins.removeAt(index)
                        }
                    },
                    onAddSocket = { socketName, pinCount, component ->
                        val existingCount = boardPins.size
                        val startX = 20f
                        val stepX = (60f / pinCount.coerceAtLeast(1)).coerceAtMost(10f)
                        for (i in 1..pinCount) {
                            val nextNum = existingCount + i
                            val isMotor = (component == "ماتور")
                            val preset = if (isMotor && (i - 1) in MotorPinPresets.PRESETS.indices) {
                                MotorPinPresets.PRESETS[i - 1]
                            } else null

                            boardPins.add(
                                HarnessPin(
                                    pinNumber = nextNum,
                                    label = preset?.functionName ?: "طرف $i ($socketName)",
                                    wireColor = preset?.colorName ?: "أزرق",
                                    colorHex = preset?.colorHex ?: "#2563EB",
                                    secondaryColorHex = null,
                                    secondaryColorName = null,
                                    connectedComponent = component,
                                    targetPinNumber = nextNum,
                                    socketName = socketName,
                                    pinX = (startX + (i - 1) * stepX).coerceIn(4f, 96f),
                                    pinY = 50f
                                )
                            )
                        }
                    },
                    onMoveSocket = { socketName, deltaX, deltaY ->
                        boardPins.indices.forEach { i ->
                            val p = boardPins[i]
                            if (p.socketName == socketName) {
                                boardPins[i] = p.copy(
                                    pinX = (p.pinX + deltaX).coerceIn(2f, 98f),
                                    pinY = (p.pinY + deltaY).coerceIn(2f, 98f)
                                )
                            }
                        }
                    }
                )
            }

            // Section 4: Board Pins List Header & Fast Buttons
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1. قائمة أطراف الكارتة (${boardPins.size} أطراف)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val nextPin = boardPins.size + 1
                                val defaultPreset = MotorPinPresets.PRESETS.firstOrNull { !it.isBlank } ?: MotorPinPresets.PRESETS.first()
                                boardPins.add(
                                    HarnessPin(
                                        pinNumber = nextPin,
                                        label = "طرف $nextPin",
                                        wireColor = defaultPreset.colorName,
                                        colorHex = defaultPreset.colorHex,
                                        secondaryColorHex = null,
                                        secondaryColorName = null,
                                        connectedComponent = "ماتور",
                                        targetPinNumber = nextPin,
                                        pinX = 50f,
                                        pinY = 50f
                                    )
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة طرف سلك +", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val nextPin = boardPins.size + 1
                                boardPins.add(
                                    HarnessPin(
                                        pinNumber = nextPin,
                                        label = "مكان فاضي بدون سلك (NC)",
                                        wireColor = "فارغ (بدون سلك)",
                                        colorHex = "#94A3B8",
                                        connectedComponent = "فارغ",
                                        targetPinNumber = nextPin,
                                        isBlank = true,
                                        pinX = 50f,
                                        pinY = 50f
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("طرف فارغ (فاضي) +", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Board Pins Individual Cards
            itemsIndexed(boardPins) { index, pin ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("طرف الكارتة #${pin.pinNumber}", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0284C7).copy(alpha = 0.2f)) {
                                    Text(
                                        text = pin.socketName.ifBlank { "سوكيت 1" },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7)),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (pin.isBlankPin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface) {
                                        Text(
                                            text = "طرف فارغ (NC)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B)),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { boardPins.removeAt(index) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = pin.label,
                            onValueChange = { newLabel -> boardPins[index] = pin.copy(label = newLabel) },
                            label = { Text("وظيفة الطرف في الكارتة (مثال: تغذية ماتور U / تاكو)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Wire color dropdown
                            var showColorDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showColorDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 14.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(pin.wireColor, maxLines = 1)
                                }
                                DropdownMenu(expanded = showColorDropdown, onDismissRequest = { showColorDropdown = false }) {
                                    STANDARD_WIRE_COLORS.forEach { (cName, hex) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    WireColorBadge(colorHex = hex, size = 16.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(cName)
                                                }
                                            },
                                            onClick = {
                                                boardPins[index] = pin.copy(
                                                    wireColor = cName,
                                                    colorHex = hex,
                                                    secondaryColorHex = if (cName.contains("/")) "#16A34A" else null,
                                                    secondaryColorName = if (cName.contains("/")) "أخضر" else null
                                                )
                                                showColorDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Component Selector
                            var showCompDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showCompDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(pin.connectedComponent, maxLines = 1)
                                }
                                DropdownMenu(expanded = showCompDropdown, onDismissRequest = { showCompDropdown = false }) {
                                    APPLIANCE_COMPONENTS.forEach { comp ->
                                        DropdownMenuItem(
                                            text = { Text(comp) },
                                            onClick = {
                                                boardPins[index] = pin.copy(connectedComponent = comp)
                                                showCompDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Bottom Appliance Components & Pins
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "2. أطراف الماتور والأجزاء السفلية (الماتور، اللوك، الليفل، الطلمبة، السخان، الشداد)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val nextPin = bottomPins.size + 1
                                bottomPins.add(
                                    HarnessPin(
                                        pinNumber = nextPin,
                                        label = "طرف $nextPin",
                                        wireColor = "أزرق",
                                        colorHex = "#2563EB",
                                        connectedComponent = "ماتور",
                                        targetPinNumber = nextPin
                                    )
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة طرف سلك +", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val nextPin = bottomPins.size + 1
                                bottomPins.add(
                                    HarnessPin(
                                        pinNumber = nextPin,
                                        label = "مكان فاضي بدون سلك (NC)",
                                        wireColor = "فارغ (بدون سلك)",
                                        colorHex = "#94A3B8",
                                        connectedComponent = "فارغ",
                                        targetPinNumber = nextPin,
                                        isBlank = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("طرف فارغ (فاضي) +", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Bottom Pins Individual Cards
            itemsIndexed(bottomPins) { index, pin ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${pin.connectedComponent} - الطرف #${pin.pinNumber}", fontWeight = FontWeight.Bold)
                                if (pin.isBlankPin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface) {
                                        Text(
                                            text = "طرف فارغ (NC)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B)),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { bottomPins.removeAt(index) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = pin.label,
                            onValueChange = { newLabel -> bottomPins[index] = pin.copy(label = newLabel) },
                            label = { Text("اسم الطرف بالقطعة (مثال: شربون / تاكوجينيراتور)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Component Selector
                            var showCompDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showCompDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(pin.connectedComponent, maxLines = 1)
                                }
                                DropdownMenu(expanded = showCompDropdown, onDismissRequest = { showCompDropdown = false }) {
                                    APPLIANCE_COMPONENTS.forEach { comp ->
                                        DropdownMenuItem(
                                            text = { Text(comp) },
                                            onClick = {
                                                bottomPins[index] = pin.copy(connectedComponent = comp)
                                                showCompDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Color Selector
                            var showColorDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showColorDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 14.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(pin.wireColor, maxLines = 1)
                                }
                                DropdownMenu(expanded = showColorDropdown, onDismissRequest = { showColorDropdown = false }) {
                                    STANDARD_WIRE_COLORS.forEach { (cName, hex) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    WireColorBadge(colorHex = hex, size = 16.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(cName)
                                                }
                                            },
                                            onClick = {
                                                bottomPins[index] = pin.copy(wireColor = cName, colorHex = hex)
                                                showColorDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val newHarness = Harness(
                            id = initialHarness?.id ?: "",
                            name = name.trim().ifBlank { "ضفيرة غسالة جديدة" },
                            brand = brand.trim(),
                            model = model.trim(),
                            notes = notes.trim(),
                            boardImageUrl = boardImageUrl.trim(),
                            boardPins = boardPins.toList(),
                            bottomPins = bottomPins.toList(),
                            orderIndex = initialHarness?.orderIndex ?: 0,
                            createdAt = initialHarness?.createdAt,
                            updatedAt = null
                        )
                        onSaveSuccess(newHarness)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ خريطة الضفيرة في السحابة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
