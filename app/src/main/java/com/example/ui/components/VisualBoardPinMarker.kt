package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.HarnessPin
import com.example.data.model.MotorPinPresets
import com.example.util.ImageHelper

val APPLIANCE_COMPONENTS = listOf(
    "ماتور",
    "لوك الباب",
    "ليفل الماء",
    "سخان وحساس حرارة",
    "طلمبة الطرد",
    "صمام الدخول",
    "شداد / كلتش",
    "تغذية 220V",
    "شاشة العرض"
)

val STANDARD_WIRE_COLORS = listOf(
    Pair("أحمر", "#EF4444"),
    Pair("أزرق", "#2563EB"),
    Pair("أصفر", "#EAB308"),
    Pair("أسود", "#1E293B"),
    Pair("أبيض", "#F8FAFC"),
    Pair("بني", "#854D0E"),
    Pair("برتقالي", "#F97316"),
    Pair("أخضر", "#16A34A"),
    Pair("بنفسجي", "#9333EA"),
    Pair("زهري / وردي", "#EC4899"),
    Pair("رمادي", "#64748B"),
    Pair("أصفر / أخضر (أرضي)", "#EAB308"),
    Pair("فارغ (بدون سلك)", "#94A3B8")
)

/**
 * Visual Board Pin Marker Composable.
 * Allows an admin to view the uploaded Control Board image,
 * tap on sockets to place pin points, and edit their properties interactively.
 */
@Composable
fun VisualBoardPinMarker(
    boardImageUrl: String,
    pins: List<HarnessPin>,
    onAddPinAtPosition: (xPercent: Float, yPercent: Float) -> Unit,
    onUpdatePin: (index: Int, updated: HarnessPin) -> Unit,
    onDeletePin: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingPinIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPinIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "تحديد أماكن الأطراف على صورة الكارتة باللمس",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "انقر على أماكن السوكيتات لوضع نقاط الأطراف وتسميتها",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Image Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .pointerInput(pins.size) {
                        detectTapGestures { offset ->
                            val xPercent = ((offset.x / size.width) * 100f).coerceIn(2f, 98f)
                            val yPercent = ((offset.y / size.height) * 100f).coerceIn(2f, 98f)
                            onAddPinAtPosition(xPercent, yPercent)
                        }
                    }
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight

                // Board image (if provided) or schematic PCB background
                if (boardImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageHelper.getCoilData(boardImageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = "صورة كارتة الغسالة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Aesthetic circuit board placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF064E3B), Color(0xFF022C22))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color(0xFF34D399).copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "يمكنك رفع صورة كارتة الغسالة من الزر أعلاه",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                            Text(
                                text = "أو انقر هنا مباشرة لتوزيع أطراف السوكيتات",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF34D399))
                            )
                        }
                    }
                }

                // Instructions Overlay Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "👆 انقر على أي مكان لوضع طرف جديد",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Render Pin Markers on the image
                pins.forEachIndexed { index, pin ->
                    val posX = (pin.pinX / 100f) * boxWidth.value
                    val posY = (pin.pinY / 100f) * boxHeight.value
                    val pinColor = parseWireColorToComposeColor(pin.colorHex)
                    val isSelected = selectedPinIndex == index

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (posX.dp.toPx() - 18.dp.toPx()).toInt(),
                                    (posY.dp.toPx() - 18.dp.toPx()).toInt()
                                )
                            }
                            .size(36.dp)
                            .shadow(if (isSelected) 8.dp else 4.dp, CircleShape)
                            .background(if (isSelected) Color.White else pinColor, CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 2.dp,
                                color = if (isSelected) pinColor else Color.White,
                                shape = CircleShape
                            )
                            .clickable {
                                selectedPinIndex = index
                                editingPinIndex = index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${pin.pinNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) pinColor else if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pin List Quick Bar
            Text(
                text = "الأطراف المحددة على الكارتة (${pins.size} طرف) - انقر على أي طرف لتعديل بياناته:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), enabled = false),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pins.take(8).forEachIndexed { idx, pin ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { editingPinIndex = idx }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 12.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "#${pin.pinNumber} ${pin.label.take(8)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Pin Editor Dialog
    editingPinIndex?.let { index ->
        val currentPin = pins.getOrNull(index)
        if (currentPin != null) {
            EditBoardPinDialog(
                pin = currentPin,
                onDismiss = { editingPinIndex = null },
                onSave = { updated ->
                    onUpdatePin(index, updated)
                    editingPinIndex = null
                },
                onDelete = {
                    onDeletePin(index)
                    editingPinIndex = null
                    selectedPinIndex = null
                }
            )
        }
    }
}

/**
 * Dialog for editing an individual pin on the board.
 */
@Composable
fun EditBoardPinDialog(
    pin: HarnessPin,
    onDismiss: () -> Unit,
    onSave: (HarnessPin) -> Unit,
    onDelete: () -> Unit
) {
    var pinNumber by remember { mutableStateOf(pin.pinNumber.toString()) }
    var label by remember { mutableStateOf(pin.label) }
    var connectedComponent by remember { mutableStateOf(pin.connectedComponent) }
    var targetPinNumber by remember { mutableStateOf((pin.targetPinNumber ?: 1).toString()) }
    var wireColor by remember { mutableStateOf(pin.wireColor) }
    var colorHex by remember { mutableStateOf(pin.colorHex) }
    var secondaryColorHex by remember { mutableStateOf(pin.secondaryColorHex) }
    var secondaryColorName by remember { mutableStateOf(pin.secondaryColorName) }
    var isBlank by remember { mutableStateOf(pin.isBlank) }

    var showComponentDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تعديل طرف الكارتة #${pin.pinNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الطرف",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pin Number & Target Pin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pinNumber,
                        onValueChange = { pinNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الطرف في الكارتة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = targetPinNumber,
                        onValueChange = { targetPinNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقمه بالقطعة المقابلة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Connected Component Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showComponentDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("القطعة المتصل بها: $connectedComponent", fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showComponentDropdown,
                        onDismissRequest = { showComponentDropdown = false }
                    ) {
                        APPLIANCE_COMPONENTS.forEach { comp ->
                            DropdownMenuItem(
                                text = { Text(comp) },
                                onClick = {
                                    connectedComponent = comp
                                    showComponentDropdown = false
                                }
                            )
                        }
                    }
                }

                // Pin Label / Function
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("وظيفة الطرف (مثال: تاكو، شربون، تغذية لوك)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Blank pin toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("طرف فارغ (مكان فاضي بدون سلك NC)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isBlank,
                        onCheckedChange = {
                            isBlank = it
                            if (it) {
                                wireColor = "فارغ (بدون سلك)"
                                colorHex = "#94A3B8"
                                secondaryColorHex = null
                                secondaryColorName = null
                            }
                        }
                    )
                }

                // If Connected Component is "ماتور" -> Show Fixed 9 Motor Presets for 1-Click Linkage!
                if (!isBlank && connectedComponent == "ماتور") {
                    Text(
                        text = "الألوان والوظائف الثابتة لأطراف الماتور (نقرة واحدة للربط):",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MotorPinPresets.PRESETS.filter { !it.isBlank }.forEach { preset ->
                            val isChosen = colorHex.equals(preset.colorHex, ignoreCase = true) &&
                                    label.contains(preset.functionName)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isChosen) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        label = preset.functionName
                                        wireColor = preset.colorName
                                        colorHex = preset.colorHex
                                        secondaryColorHex = null
                                        secondaryColorName = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    WireColorBadge(
                                        colorHex = preset.colorHex,
                                        secondaryColorHex = null,
                                        size = 14.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${preset.functionName} (${preset.colorName})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = preset.description.take(20),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else if (!isBlank) {
                    // Other Components: Flexible Color Palette
                    Text(
                        text = "اختر لون سلك الطرف:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState(), enabled = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STANDARD_WIRE_COLORS.take(6).forEach { (cName, hex) ->
                            FilterChip(
                                selected = colorHex == hex,
                                onClick = {
                                    wireColor = cName
                                    colorHex = hex
                                    secondaryColorHex = if (cName.contains("/")) "#16A34A" else null
                                    secondaryColorName = if (cName.contains("/")) "أخضر" else null
                                },
                                label = { Text(cName, fontSize = 11.sp) },
                                leadingIcon = { WireColorBadge(colorHex = hex, size = 12.dp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pNum = pinNumber.toIntOrNull() ?: pin.pinNumber
                    val tNum = targetPinNumber.toIntOrNull() ?: pin.targetPinNumber
                    onSave(
                        pin.copy(
                            pinNumber = pNum,
                            label = label.trim().ifBlank { "طرف $pNum" },
                            connectedComponent = connectedComponent,
                            targetPinNumber = tNum,
                            wireColor = wireColor,
                            colorHex = colorHex,
                            secondaryColorHex = secondaryColorHex,
                            secondaryColorName = secondaryColorName,
                            isBlank = isBlank
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ بيانات الطرف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
