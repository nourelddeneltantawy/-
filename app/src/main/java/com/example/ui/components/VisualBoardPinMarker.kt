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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
 * Features:
 * 1. UNIFIED SOCKETS SYSTEM:
 *    - Pins are housed inside solid physical socket rectangles (مستطيل السوكيت).
 *    - Dragging the socket rectangle moves the ENTIRE socket and ALL its pins together as one unit!
 *    - Pins never separate from each other.
 *    - Each socket is independent and can be moved anywhere on the board.
 * 2. In-socket customization:
 *    - Change spacing between pins inside the socket (ضيق / وسط / واسع).
 *    - Toggle any pin inside the socket to be "طرف فارغ (NC)" or wire pin.
 *    - Click any pin to configure its color, motor preset, or label.
 *    - Add or remove pins directly inside the socket.
 * 3. Board Image controls: Fit vs Crop, canvas height, and custom pin sizing.
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
    onAddSocket: (socketName: String, pinCount: Int, component: String) -> Unit = { _, _, _ -> },
    onMoveSocket: (socketName: String, deltaX: Float, deltaY: Float) -> Unit = { _, _, _ -> },
    onAddPinToSocket: (socketName: String, component: String) -> Unit = { _, _ -> },
    onDeleteSocket: (socketName: String) -> Unit = {},
    onUpdateSocketSpacing: (socketName: String, spacingFactor: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingPinIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPinIndex by remember { mutableStateOf<Int?>(pins.indices.firstOrNull()) }
    var selectedSocketName by remember { mutableStateOf<String?>(null) }
    var showImageUrlField by remember { mutableStateOf(false) }
    var showAddSocketDialog by remember { mutableStateOf(false) }

    // Visual Customizations
    var pinSizeDp by remember { mutableIntStateOf(26) } // 20, 26, 34
    var canvasHeightDp by remember { mutableIntStateOf(360) } // 320, 440, 560
    var isCropScale by remember { mutableStateOf(false) }

    // Group pins into distinct sockets
    val socketGroups = remember(pins) {
        if (pins.isEmpty()) {
            emptyMap()
        } else {
            pins.groupBy { it.socketName.ifBlank { "سوكيت 1" } }
        }
    }

    val distinctSockets = remember(socketGroups) { socketGroups.keys.toList() }

    // Auto select first socket if none selected
    if (selectedSocketName == null || selectedSocketName !in distinctSockets) {
        selectedSocketName = distinctSockets.firstOrNull()
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
                            text = "مخطط سوكيتات الكارتة (تجميع الأطراف)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "السوكيت مستطيل متماسك يجمع أطرافه معاً ويتحرك كقطعة واحدة",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Sockets Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddSocketDialog = true },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(imageVector = Icons.Default.Power, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🔌 + إضافة سوكيت جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onPickImageClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("صورة الكارتة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onUpdateBoardImageUrl("sample_lg_diagram") },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("مخطط LG", fontSize = 11.sp)
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

            // Canvas & Pin Visual Controls Toolbar (Pin Size, Image Fit/Crop, Canvas Height)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Row 1: Pin size controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حجم الأطراف:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                Pair(20, "صغير 20"),
                                Pair(26, "وسط 26"),
                                Pair(34, "كبير 34")
                            ).forEach { (size, label) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (pinSizeDp == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = if (pinSizeDp == size) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.clickable { pinSizeDp = size }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (pinSizeDp == size) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (pinSizeDp == size) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Image Fit / Crop & Canvas Height controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fit vs Crop toggle
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (!isCropScale) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { isCropScale = false }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.FitScreen, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("احتواء Fit", fontSize = 10.sp, fontWeight = if (!isCropScale) FontWeight.Bold else FontWeight.Normal)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCropScale) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { isCropScale = true }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("قص/ملء Crop", fontSize = 10.sp, fontWeight = if (isCropScale) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        // Height selector
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Height, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            listOf(
                                Pair(320, "320"),
                                Pair(440, "440"),
                                Pair(560, "560")
                            ).forEach { (h, label) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (canvasHeightDp == h) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { canvasHeightDp = h }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (canvasHeightDp == h) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (canvasHeightDp == h) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sockets Selector & Controls Bar
            if (distinctSockets.isNotEmpty()) {
                Text(
                    text = "السوكيتات المتوفرة على الكارتة (انقر لاختيار السوكيت وتحريكه أو تعديل مسافاته):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    distinctSockets.forEach { sockName ->
                        val sockPins = socketGroups[sockName] ?: emptyList()
                        val isSelected = selectedSocketName == sockName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)) else null,
                            modifier = Modifier.clickable { selectedSocketName = sockName }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Power,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF0284C7),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$sockName (${sockPins.size} طرف)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Selected Socket Quick Actions Bar (Spacing, Add Pin, Delete Socket)
            selectedSocketName?.let { currentSocket ->
                val socketPins = socketGroups[currentSocket] ?: emptyList()
                val comp = socketPins.firstOrNull()?.connectedComponent ?: "ماتور"

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "تحكم في: $currentSocket ($comp)",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            )
                            // Pin Spacing selector
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("مسافة الأطراف:", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp))
                                listOf(Pair(4f, "ضيق"), Pair(7f, "وسط"), Pair(11f, "واسع")).forEach { (factor, label) ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.clickable { onUpdateSocketSpacing(currentSocket, factor) }
                                    ) {
                                        Text(label, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Add pin to this socket
                            Button(
                                onClick = { onAddPinToSocket(currentSocket, comp) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+ طرف", fontSize = 10.sp)
                            }

                            // Delete socket
                            IconButton(
                                onClick = { onDeleteSocket(currentSocket) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف السوكيت", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================================================================
            // INTERACTIVE BOARD CANVAS WITH SOLID PHYSICAL SOCKETS HOUSING
            // =========================================================================
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    val boxWidth = maxWidth
                    val boxHeight = maxHeight

                    // 1. Board image (if provided)
                    if (boardImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ImageHelper.getCoilData(boardImageUrl))
                                .crossfade(true)
                                .build(),
                            contentDescription = "صورة كارتة الغسالة",
                            contentScale = if (isCropScale) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 2. High-Tech PCB Electronic Circuit Canvas (No image required!)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF064E3B), Color(0xFF022C22))
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("لوحة الكارتة الإلكترونية PCB", fontSize = 10.sp, color = Color(0xFF34D399))
                            }
                        }
                    }

                    // Instructions Overlay Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 اسحب أي سوكيت لتحريكه بجميع أطرافه معاً • انقر على أي طرف لتعديله",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    // 3. RENDER EACH SOCKET AS A UNIFIED PHYSICAL RECTANGLE HOUSING (مستطيل السوكيت المتماسك)
                    socketGroups.forEach { (socketName, socketPins) ->
                        if (socketPins.isNotEmpty()) {
                            // Calculate socket bounding anchor on board
                            val avgX = socketPins.map { it.pinX }.average().toFloat()
                            val avgY = socketPins.map { it.pinY }.average().toFloat()

                            val posX = (avgX / 100f) * boxWidth.value
                            val posY = (avgY / 100f) * boxHeight.value

                            val isSocketSelected = selectedSocketName == socketName
                            val comp = socketPins.firstOrNull()?.connectedComponent ?: "ماتور"

                            // Unified Socket Housing Box (moves as ONE single solid component!)
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (posX.dp.toPx() - (socketPins.size * (pinSizeDp + 6) / 2).dp.toPx()).toInt(),
                                            (posY.dp.toPx() - 32.dp.toPx()).toInt()
                                        )
                                    }
                                    .shadow(if (isSocketSelected) 12.dp else 4.dp, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B).copy(alpha = 0.94f))
                                    .border(
                                        width = if (isSocketSelected) 2.2.dp else 1.2.dp,
                                        color = if (isSocketSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8).copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    // DRAGGING MOVES ENTIRE SOCKET AND ALL ITS PINS TOGETHER!
                                    .pointerInput(socketName, socketPins) {
                                        detectDragGestures(
                                            onDragStart = { selectedSocketName = socketName },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val parentWidthPx = boxWidth.toPx()
                                                val parentHeightPx = boxHeight.toPx()
                                                if (parentWidthPx > 0 && parentHeightPx > 0) {
                                                    val deltaXPercent = (dragAmount.x / parentWidthPx) * 100f
                                                    val deltaYPercent = (dragAmount.y / parentHeightPx) * 100f
                                                    onMoveSocket(socketName, deltaXPercent, deltaYPercent)
                                                }
                                            }
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Socket Header Label
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.clickable { selectedSocketName = socketName }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Power,
                                            contentDescription = null,
                                            tint = if (isSocketSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$socketName (${socketPins.size}) - $comp",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSocketSelected) Color(0xFF38BDF8) else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    // Locked Row of Pins inside the Socket Housing!
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        socketPins.forEach { pin ->
                                            val realIndex = pins.indexOf(pin)
                                            val isPinSelected = selectedPinIndex == realIndex
                                            val pinColor = parseWireColorToComposeColor(pin.colorHex)

                                            // Pin Slot inside Socket
                                            Box(
                                                modifier = Modifier
                                                    .size(pinSizeDp.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (pin.isBlankPin) Color(0xFF334155)
                                                        else if (isPinSelected) Color.White
                                                        else pinColor
                                                    )
                                                    .border(
                                                        width = if (isPinSelected) 2.5.dp else 1.2.dp,
                                                        color = if (pin.isBlankPin) Color(0xFF64748B) else if (isPinSelected) pinColor else Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        selectedPinIndex = realIndex
                                                        selectedSocketName = socketName
                                                        editingPinIndex = realIndex
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (pin.isBlankPin) {
                                                    Text(
                                                        text = "NC",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                } else {
                                                    Text(
                                                        text = "${pin.pinNumber}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isPinSelected) pinColor else if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                                            fontSize = if (pinSizeDp <= 20) 9.sp else 11.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Pin Inspector / D-Pad
            if (selectedPin != null && selectedPinIndex != null) {
                val idx = selectedPinIndex!!
                val sock = selectedPin.socketName.ifBlank { "سوكيت 1" }
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
                                    text = "الطرف #${selectedPin.pinNumber} (${selectedPin.label}) • $sock",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = if (selectedPin.isBlankPin) "طرف فارغ بدون سلك (NC)" else "لون السلك: ${selectedPin.wireColor}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        // Precision Directional Buttons for whole socket
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = { onMoveSocket(sock, 0f, -2f) }) {
                                    Icon(Icons.Default.ArrowUpward, "أعلى", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = { onMoveSocket(sock, 0f, 2f) }) {
                                    Icon(Icons.Default.ArrowDownward, "أسفل", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = { onMoveSocket(sock, -2f, 0f) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "يسار", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                                IconButton(onClick = { onMoveSocket(sock, 2f, 0f) }) {
                                    Icon(Icons.Default.ArrowForward, "يمين", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Edit pin button
                            IconButton(onClick = { editingPinIndex = idx }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل الطرف", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Socket Dialog
    if (showAddSocketDialog) {
        AddSocketDialog(
            nextSocketNumber = distinctSockets.size + 1,
            onDismiss = { showAddSocketDialog = false },
            onConfirm = { socketName, pinCount, component ->
                onAddSocket(socketName, pinCount, component)
                showAddSocketDialog = false
            }
        )
    }

    // Pin Editor Dialog
    editingPinIndex?.let { index ->
        val currentPin = pins.getOrNull(index)
        if (currentPin != null) {
            EditBoardPinDialog(
                pin = currentPin,
                existingSockets = distinctSockets,
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
 * Dialog to add a full socket with custom number of pins (2, 3, 5, etc.) and component.
 */
@Composable
fun AddSocketDialog(
    nextSocketNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (socketName: String, pinCount: Int, component: String) -> Unit
) {
    var socketNameInput by remember { mutableStateOf("سوكيت $nextSocketNumber") }
    var pinCountInput by remember { mutableStateOf("5") }
    var componentInput by remember { mutableStateOf("ماتور") }
    var showComponentDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Power, contentDescription = null, tint = Color(0xFF0284C7))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة سوكيت جديد للكارتة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "حدد اسم السوكيت وعدد أطرافه (مثال: سوكيت 1 بـ 5 أطراف، وسوكيت 2 بـ 3 أطراف):",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                OutlinedTextField(
                    value = socketNameInput,
                    onValueChange = { socketNameInput = it },
                    label = { Text("اسم السوكيت (مثال: سوكيت 1 - الماتور)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Quick Pin Count Selector
                Text("عدد أطراف السوكيت:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2", "3", "4", "5", "6", "8").forEach { count ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (pinCountInput == count) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { pinCountInput = count }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = count,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pinCountInput == count) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pinCountInput,
                    onValueChange = { pinCountInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("أو أدخل عدد أطراف يدوي") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Connected Appliance Component
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
                            Text("القطعة المتصل بها: $componentInput", fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = showComponentDropdown, onDismissRequest = { showComponentDropdown = false }) {
                        APPLIANCE_COMPONENTS.forEach { comp ->
                            DropdownMenuItem(
                                text = { Text(comp) },
                                onClick = {
                                    componentInput = comp
                                    showComponentDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = pinCountInput.toIntOrNull()?.coerceIn(1, 24) ?: 5
                    val sName = socketNameInput.trim().ifBlank { "سوكيت $nextSocketNumber" }
                    onConfirm(sName, count, componentInput)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تأكيد وإنشاء السوكيت")
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

/**
 * Dialog for editing an individual pin on the board.
 */
@Composable
fun EditBoardPinDialog(
    pin: HarnessPin,
    existingSockets: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (HarnessPin) -> Unit,
    onDelete: () -> Unit
) {
    var pinNumber by remember { mutableStateOf(pin.pinNumber.toString()) }
    var label by remember { mutableStateOf(pin.label) }
    var socketName by remember { mutableStateOf(pin.socketName.ifBlank { "سوكيت 1" }) }
    var connectedComponent by remember { mutableStateOf(pin.connectedComponent) }
    var targetPinNumber by remember { mutableStateOf((pin.targetPinNumber ?: 1).toString()) }
    var wireColor by remember { mutableStateOf(pin.wireColor) }
    var colorHex by remember { mutableStateOf(pin.colorHex) }
    var secondaryColorHex by remember { mutableStateOf(pin.secondaryColorHex) }
    var secondaryColorName by remember { mutableStateOf(pin.secondaryColorName) }
    var isBlank by remember { mutableStateOf(pin.isBlankPin) }
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
                // Blank pin toggle (PROMINENT AT TOP)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isBlank) Color(0xFF334155) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isBlank) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF94A3B8)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("طرف فارغ (مكان فاضي بدون سلك NC)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("مكان بن فارغ في السوكيت ليس به سلك", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isBlank,
                            onCheckedChange = {
                                isBlank = it
                                if (it) {
                                    wireColor = "فارغ (بدون سلك)"
                                    colorHex = "#94A3B8"
                                    label = "مكان فاضي بدون سلك (NC)"
                                }
                            }
                        )
                    }
                }

                // Pin Number & Target Pin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pinNumber,
                        onValueChange = { pinNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الطرف") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = targetPinNumber,
                        onValueChange = { targetPinNumber = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقمه بالقطعة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Socket Name Field
                OutlinedTextField(
                    value = socketName,
                    onValueChange = { socketName = it },
                    label = { Text("اسم السوكيت التابع له (مثال: سوكيت 1)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

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
                    // Other Components: Flexible Color Palette
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
                            socketName = socketName.trim().ifBlank { "سوكيت 1" },
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
