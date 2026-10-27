package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

enum class CanvasTouchMode {
    MOVE_SELECTED,
    ADD_NEW
}

/**
 * Visual Board Pin Marker Composable.
 * Allows an admin to:
 * 1. Add/change the board image directly (gallery, camera, sample LG diagram, or direct link).
 * 2. Tap anywhere on the board image to position the selected pin instantly.
 * 3. Touch and DRAG existing pins to move them freely to their exact positions!
 * 4. Use directional nudge buttons (Up, Down, Left, Right) for precision alignment.
 * 5. Edit pin properties (number, label, component, motor presets or custom colors).
 */
@Composable
fun VisualBoardPinMarker(
    boardImageUrl: String,
    pins: List<HarnessPin>,
    onUpdateBoardImageUrl: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onAddPinAtPosition: (xPercent: Float, yPercent: Float) -> Unit,
    onUpdatePin: (index: Int, updated: HarnessPin) -> Unit,
    onDeletePin: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingPinIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPinIndex by remember { mutableStateOf<Int?>(pins.indices.firstOrNull()) }
    var showImageUrlField by remember { mutableStateOf(false) }
    var touchMode by remember { mutableStateOf(if (pins.isNotEmpty()) CanvasTouchMode.MOVE_SELECTED else CanvasTouchMode.ADD_NEW) }

    // Keep selectedPinIndex valid
    if (selectedPinIndex != null && selectedPinIndex !in pins.indices) {
        selectedPinIndex = pins.indices.firstOrNull()
    }

    val selectedPin = selectedPinIndex?.let { idx -> pins.getOrNull(idx) }

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
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "صورة الكارتة وتوزيع السوكيتات",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "انقر على السوكت في الكارتة لنقل الطرف إليه أو اسحبه بإصبعك",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Board Image Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickImageClick,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اختيار صورة الكارتة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        // Load high-resolution bundled LG sample diagram
                        onUpdateBoardImageUrl("sample_lg_diagram")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مخطط LG جاهز", fontSize = 12.sp)
                }
            }

            // Image URL toggle or clear button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showImageUrlField = !showImageUrlField }) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showImageUrlField) "إخفاء رابط الصورة" else "أو كتابة رابط/مسار صورة مباشرة",
                        fontSize = 11.sp
                    )
                }

                if (boardImageUrl.isNotBlank()) {
                    TextButton(onClick = { onUpdateBoardImageUrl("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف الصورة", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showImageUrlField) {
                OutlinedTextField(
                    value = boardImageUrl,
                    onValueChange = onUpdateBoardImageUrl,
                    label = { Text("مسار أو رابط صورة الكارتة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pin Selection Quick Chips Bar (before canvas so user picks which pin to place)
            if (pins.isNotEmpty()) {
                Text(
                    text = "اختر الطرف الذي تريد وضعه أو تحريكه:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pins.forEachIndexed { idx, pin ->
                        val isSelected = selectedPinIndex == idx
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable {
                                selectedPinIndex = idx
                                touchMode = CanvasTouchMode.MOVE_SELECTED
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 12.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "#${pin.pinNumber} ${pin.label.take(12)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Mode Selector Bar: Move selected pin OR Add new pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = touchMode == CanvasTouchMode.MOVE_SELECTED,
                    onClick = { touchMode = CanvasTouchMode.MOVE_SELECTED },
                    label = {
                        Text(
                            text = "🖐️ انقر على الكارتة لنقل الطرف المحدد",
                            fontWeight = if (touchMode == CanvasTouchMode.MOVE_SELECTED) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(1.2f)
                )

                FilterChip(
                    selected = touchMode == CanvasTouchMode.ADD_NEW,
                    onClick = { touchMode = CanvasTouchMode.ADD_NEW },
                    label = {
                        Text(
                            text = "➕ طرف جديد باللمس",
                            fontWeight = if (touchMode == CanvasTouchMode.ADD_NEW) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(0.9f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Image Canvas with Direct Touch Placement & Drag Gestures
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .pointerInput(touchMode, selectedPinIndex, pins.size) {
                        detectTapGestures { offset ->
                            val xPercent = ((offset.x / size.width) * 100f).coerceIn(2f, 98f)
                            val yPercent = ((offset.y / size.height) * 100f).coerceIn(2f, 98f)
                            if (touchMode == CanvasTouchMode.MOVE_SELECTED && selectedPinIndex != null && selectedPinIndex in pins.indices) {
                                val current = pins[selectedPinIndex!!]
                                onUpdatePin(selectedPinIndex!!, current.copy(pinX = xPercent, pinY = yPercent))
                            } else {
                                onAddPinAtPosition(xPercent, yPercent)
                                selectedPinIndex = pins.size
                                touchMode = CanvasTouchMode.MOVE_SELECTED
                            }
                        }
                    }
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight

                // Board image (if provided) or schematic PCB background with direct add buttons
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
                    // Aesthetic circuit board placeholder with prominent call-to-action buttons
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لم يتم اختيار صورة كارتة حتى الآن",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "اضغط على أحد الزرين بالأسفل لإظهار صورة الكارتة وتحديد السوكيتات عليها:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = onPickImageClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اختيار صورة من الهاتف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onUpdateBoardImageUrl("sample_lg_diagram") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مخطط كارتة LG الجاهز", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Instructions Overlay Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    val promptText = if (touchMode == CanvasTouchMode.MOVE_SELECTED && selectedPin != null) {
                        "🎯 انقر على السوكت لنقل الطرف #${selectedPin.pinNumber} (${selectedPin.label}) إليه"
                    } else if (touchMode == CanvasTouchMode.ADD_NEW) {
                        "➕ انقر في أي مكان لإضافة طرف جديد هنا"
                    } else {
                        "👆 انقر لوضع الطرف • اسحب أي طرف لتحريكه"
                    }
                    Text(
                        text = promptText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // Render Pin Markers with Real-time Drag and Tap Gestures!
                pins.forEachIndexed { index, pin ->
                    val posX = (pin.pinX / 100f) * boxWidth.value
                    val posY = (pin.pinY / 100f) * boxHeight.value
                    val pinColor = parseWireColorToComposeColor(pin.colorHex)
                    val isSelected = selectedPinIndex == index

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (posX.dp.toPx() - 19.dp.toPx()).toInt(),
                                    (posY.dp.toPx() - 19.dp.toPx()).toInt()
                                )
                            }
                            .size(38.dp)
                            .shadow(if (isSelected) 12.dp else 4.dp, CircleShape)
                            .background(if (isSelected) Color.White else pinColor, CircleShape)
                            .border(
                                width = if (isSelected) 3.5.dp else 2.dp,
                                color = if (isSelected) pinColor else Color.White,
                                shape = CircleShape
                            )
                            // 1. TAP GESTURE: Single tap selects, double tap opens edit dialog
                            .pointerInput(index, pin) {
                                detectTapGestures(
                                    onTap = {
                                        selectedPinIndex = index
                                        touchMode = CanvasTouchMode.MOVE_SELECTED
                                    },
                                    onDoubleTap = {
                                        selectedPinIndex = index
                                        editingPinIndex = index
                                    }
                                )
                            }
                            // 2. DRAG GESTURE: Dragging moves the pin smoothly across the board image!
                            .pointerInput(index, pins) {
                                detectDragGestures(
                                    onDragStart = {
                                        selectedPinIndex = index
                                        touchMode = CanvasTouchMode.MOVE_SELECTED
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val parentWidthPx = boxWidth.toPx()
                                        val parentHeightPx = boxHeight.toPx()
                                        if (parentWidthPx > 0 && parentHeightPx > 0) {
                                            val deltaXPercent = (dragAmount.x / parentWidthPx) * 100f
                                            val deltaYPercent = (dragAmount.y / parentHeightPx) * 100f
                                            val current = pins.getOrNull(index) ?: pin
                                            val newX = (current.pinX + deltaXPercent).coerceIn(2f, 98f)
                                            val newY = (current.pinY + deltaYPercent).coerceIn(2f, 98f)
                                            onUpdatePin(index, current.copy(pinX = newX, pinY = newY))
                                        }
                                    }
                                )
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

            // Selected Pin Precision Directional Movement Controls (D-Pad)
            if (selectedPin != null && selectedPinIndex != null) {
                val idx = selectedPinIndex!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WireColorBadge(colorHex = selectedPin.colorHex, secondaryColorHex = selectedPin.secondaryColorHex, size = 14.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الطرف المختار: #${selectedPin.pinNumber} (${selectedPin.label})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "موقعه: X=${selectedPin.pinX.toInt()}% ، Y=${selectedPin.pinY.toInt()}% (انقر على الكارتة لنقله فوراً)",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        // Precision Directional Buttons (أزرار التحريك الدقيق)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Up
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = {
                                    val newY = (selectedPin.pinY - 1.5f).coerceIn(2f, 98f)
                                    onUpdatePin(idx, selectedPin.copy(pinY = newY))
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "أعلى", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            // Down
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = {
                                    val newY = (selectedPin.pinY + 1.5f).coerceIn(2f, 98f)
                                    onUpdatePin(idx, selectedPin.copy(pinY = newY))
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "أسفل", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            // Left
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = {
                                    val newX = (selectedPin.pinX - 1.5f).coerceIn(2f, 98f)
                                    onUpdatePin(idx, selectedPin.copy(pinX = newX))
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "يسار", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            // Right
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = {
                                    val newX = (selectedPin.pinX + 1.5f).coerceIn(2f, 98f)
                                    onUpdatePin(idx, selectedPin.copy(pinX = newX))
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "يمين", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Edit pin button
                            IconButton(onClick = { editingPinIndex = idx }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                            }
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
 * Dialog for editing an individual pin on the board, including location sliders.
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
    var pinX by remember { mutableFloatStateOf(pin.pinX) }
    var pinY by remember { mutableFloatStateOf(pin.pinY) }

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

                // Location Sliders for X and Y positioning
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "موقع الطرف على الكارتة: (أفقي: ${pinX.toInt()}%, رأسي: ${pinY.toInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("الموقع الأفقي X:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Slider(
                        value = pinX,
                        onValueChange = { pinX = it },
                        valueRange = 2f..98f
                    )
                    Text("الموقع الرأسي Y:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Slider(
                        value = pinY,
                        onValueChange = { pinY = it },
                        valueRange = 2f..98f
                    )
                }

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

                // If Connected Component is "ماتور" -> Show Fixed 9 Motor Presets
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
                    // Other Components: Flexible Color Palette (Safe horizontal scroll)
                    Text(
                        text = "اختر لون سلك الطرف:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STANDARD_WIRE_COLORS.forEach { (cName, hex) ->
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
                            isBlank = isBlank,
                            pinX = pinX,
                            pinY = pinY
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
