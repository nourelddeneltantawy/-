package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    "شاشة العرض",
    "فارغ (NC)"
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
 * 1. UNIFIED PHYSICAL SOCKET HOUSINGS:
 *    - Sockets house multiple wires/pins, where each pin can connect to ANY appliance component underneath.
 *    - Sockets are NOT restricted to a single component.
 * 2. FULL INTERACTION & ACCURACY:
 *    - Pinch-to-zoom (0.6x to 4.0x) and two-finger pan.
 *    - Zoom toolbar: Zoom In (+), Zoom Out (-), Reset (100%), and Fullscreen Mode (⛶).
 *    - D-Pad Directional Move Buttons (▲, ▼, ◄, ►, 🎯) for mm-level socket positioning.
 * 3. SOCKET MANAGEMENT:
 *    - Add new sockets with distinct names and auto-selection.
 *    - Delete sockets with confirmation.
 *    - Adjust pin spacing inside the socket.
 *    - Quick pin reassignment to other sockets.
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
    onAddSocket: (socketName: String, pinCount: Int) -> Unit = { _, _ -> },
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
    var socketToDelete by remember { mutableStateOf<String?>(null) }

    // Visual Customizations
    var pinSizeDp by remember { mutableIntStateOf(26) } // 20, 26, 34
    var canvasHeightDp by remember { mutableIntStateOf(380) } // 320, 380, 520
    var isCropScale by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var moveStepPercent by remember { mutableFloatStateOf(2.5f) } // 1%, 2.5%, 5%
    var isFullscreenCanvas by remember { mutableStateOf(false) }

    // Group pins into distinct sockets
    val socketGroups = remember(pins) {
        if (pins.isEmpty()) {
            emptyMap()
        } else {
            pins.groupBy { it.socketName.ifBlank { "سوكيت 1" } }
        }
    }

    val distinctSockets = remember(socketGroups) { socketGroups.keys.toList() }

    // Auto select first socket if none selected or if previous selected socket was deleted
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
                            text = "كل سوكيت عبارة عن مستطيل يحتوي سلوك متعددة متصلة بالقطع المختلفة",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Action Buttons
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

            // =========================================================================
            // ZOOM & CONTROLS TOOLBAR
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Row 1: Zoom In/Out, Reset, Fullscreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Zoom Out (-)
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.6f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "تصغير", modifier = Modifier.size(18.dp))
                            }

                            // Zoom Level Indicator
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            // Zoom In (+)
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "تكبير", modifier = Modifier.size(18.dp))
                            }

                            // Reset Zoom / Center
                            IconButton(
                                onClick = {
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة ضبط", modifier = Modifier.size(16.dp))
                            }
                        }

                        // Fullscreen Mode Button
                        Button(
                            onClick = { isFullscreenCanvas = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⛶ ملء الشاشة للتحكم الحر", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Row 2: Pin size and Height
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pin size
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("حجم الأطراف:", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            listOf(Pair(20, "صغير"), Pair(26, "وسط"), Pair(34, "كبير")).forEach { (sz, lbl) ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (pinSizeDp == sz) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { pinSizeDp = sz }
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 9.sp,
                                        fontWeight = if (pinSizeDp == sz) FontWeight.Bold else FontWeight.Normal,
                                        color = if (pinSizeDp == sz) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Fit vs Crop & Height
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isCropScale) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable { isCropScale = !isCropScale }
                            ) {
                                Text(
                                    text = if (isCropScale) "قص Crop" else "احتواء Fit",
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            listOf(Pair(320, "320"), Pair(400, "400"), Pair(520, "520")).forEach { (h, lbl) ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (canvasHeightDp == h) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { canvasHeightDp = h }
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 9.sp,
                                        fontWeight = if (canvasHeightDp == h) FontWeight.Bold else FontWeight.Normal,
                                        color = if (canvasHeightDp == h) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sockets Selector Bar
            if (distinctSockets.isNotEmpty()) {
                Text(
                    text = "السوكيتات المتوفرة على الكارتة (انقر لاختيار السوكيت وتحريكه بحرية):",
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
                            modifier = Modifier.clickable {
                                selectedSocketName = sockName
                                selectedPinIndex = null
                            }
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

            // Selected Socket Controls & Directional Movement D-Pad
            selectedSocketName?.let { currentSocket ->
                val socketPins = socketGroups[currentSocket] ?: emptyList()

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Title row + quick actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Power, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "التحكم في: $currentSocket (${socketPins.size} أطراف)",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Add pin to this socket
                                Button(
                                    onClick = { onAddPinToSocket(currentSocket, "ماتور") },
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
                                    onClick = { socketToDelete = currentSocket },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف السوكيت", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Spacing & Step Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pin Spacing
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("المسافة بين الأطراف:", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
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

                            // Movement Step
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("سرعة الحركة:", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                listOf(Pair(1f, "دقيق 1%"), Pair(2.5f, "وسط 2.5%"), Pair(6f, "سريع 6%")).forEach { (st, label) ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (moveStepPercent == st) Color(0xFF0284C7) else Color(0xFF1E293B),
                                        modifier = Modifier.clickable { moveStepPercent = st }
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = if (moveStepPercent == st) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // D-Pad Directional Movement Buttons (Smooth mm-precision)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Move Right
                                    IconButton(
                                        onClick = { onMoveSocket(currentSocket, moveStepPercent, 0f) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "يمين", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // Move Up
                                    IconButton(
                                        onClick = { onMoveSocket(currentSocket, 0f, -moveStepPercent) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // Center on Board
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF0284C7),
                                        modifier = Modifier.size(30.dp).clickable {
                                            // Recenter socket
                                            val sPins = socketGroups[currentSocket] ?: emptyList()
                                            if (sPins.isNotEmpty()) {
                                                val avgX = sPins.map { it.pinX }.average().toFloat()
                                                val avgY = sPins.map { it.pinY }.average().toFloat()
                                                onMoveSocket(currentSocket, 50f - avgX, 50f - avgY)
                                            }
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "المنتصف", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = { onMoveSocket(currentSocket, 0f, moveStepPercent) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // Move Left
                                    IconButton(
                                        onClick = { onMoveSocket(currentSocket, -moveStepPercent, 0f) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "يسار", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================================================================
            // INTERACTIVE BOARD CANVAS (PINCH-TO-ZOOM, PAN, AND SOLID SOCKET HOUSINGS)
            // =========================================================================
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        // Multi-touch Zoom & Pan Gestures
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.6f, 4.0f)
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-1200f, 1200f)
                                panOffsetY = (panOffsetY + pan.y).coerceIn(-1200f, 1200f)
                            }
                        }
                ) {
                    val boxWidth = maxWidth
                    val boxHeight = maxHeight

                    // Canvas Transform Container (Zooms and pans cleanly!)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                            }
                    ) {
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
                            // 2. High-Tech PCB Electronic Circuit Canvas
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

                        // 3. RENDER EACH SOCKET AS A UNIFIED SOLID PHYSICAL RECTANGLE HOUSING
                        socketGroups.forEach { (socketName, socketPins) ->
                            if (socketPins.isNotEmpty()) {
                                val avgX = socketPins.map { it.pinX }.average().toFloat()
                                val avgY = socketPins.map { it.pinY }.average().toFloat()

                                val posX = (avgX / 100f) * boxWidth.value
                                val posY = (avgY / 100f) * boxHeight.value

                                val isSocketSelected = selectedSocketName == socketName

                                // Solid Socket Housing Box
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
                                        // DRAGGING MOVES ENTIRE SOCKET AND ALL ITS PINS TOGETHER
                                        .pointerInput(socketName, zoomScale) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    selectedSocketName = socketName
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val parentWidthPx = boxWidth.toPx()
                                                    val parentHeightPx = boxHeight.toPx()
                                                    if (parentWidthPx > 0 && parentHeightPx > 0) {
                                                        val deltaXPercent = ((dragAmount.x / zoomScale) / parentWidthPx) * 100f
                                                        val deltaYPercent = ((dragAmount.y / zoomScale) / parentHeightPx) * 100f
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
                                        // Socket Header
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
                                                text = "$socketName (${socketPins.size} أطراف)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSocketSelected) Color(0xFF38BDF8) else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }

                                        // Locked Row of Pins inside the Socket Housing
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

                    // Instructions Floating Overlay
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 تكبير/تصغير بإصبعين أو بالأزرار • اسحب السوكيت أو حركه بالأسهم",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Pin Quick Summary
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
                            .padding(10.dp),
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
                                text = if (selectedPin.isBlankPin) "طرف فارغ بدون سلك (NC)" else "متصل بـ: ${selectedPin.connectedComponent} • لون السلك: ${selectedPin.wireColor}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Button(
                            onClick = { editingPinIndex = idx },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تعديل", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // FULLSCREEN WORKSPACE DIALOG (For distraction-free positioning & zooming)
    // =========================================================================
    if (isFullscreenCanvas) {
        Dialog(
            onDismissRequest = { isFullscreenCanvas = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen Top Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isFullscreenCanvas = false }) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "خروج", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("ورشة ضبط سوكيتات الكارتة (شاشة كاملة)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("تحكم حر بدون تعارض مع تمرير الصفحة", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.6f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "تصغير", tint = Color.White)
                            }
                            Text("${(zoomScale * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "تكبير", tint = Color.White)
                            }
                            IconButton(onClick = {
                                zoomScale = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة ضبط", tint = Color.White)
                            }
                        }
                    }

                    // Sockets Selector inside Fullscreen
                    if (distinctSockets.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            distinctSockets.forEach { sockName ->
                                val sockPins = socketGroups[sockName] ?: emptyList()
                                val isSelected = selectedSocketName == sockName
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF334155),
                                    modifier = Modifier.clickable { selectedSocketName = sockName }
                                ) {
                                    Text(
                                        text = "$sockName (${sockPins.size})",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Fullscreen Canvas Area
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0A0F1D))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(0.6f, 4.0f)
                                    panOffsetX = (panOffsetX + pan.x).coerceIn(-1500f, 1500f)
                                    panOffsetY = (panOffsetY + pan.y).coerceIn(-1500f, 1500f)
                                }
                            }
                    ) {
                        val fsWidth = maxWidth
                        val fsHeight = maxHeight

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffsetX
                                    translationY = panOffsetY
                                }
                        ) {
                            if (boardImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ImageHelper.getCoilData(boardImageUrl))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "كارتة الغسالة",
                                    contentScale = if (isCropScale) ContentScale.Crop else ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.radialGradient(listOf(Color(0xFF064E3B), Color(0xFF022C22))))
                                )
                            }

                            // Render Sockets inside Fullscreen Canvas
                            socketGroups.forEach { (socketName, socketPins) ->
                                if (socketPins.isNotEmpty()) {
                                    val avgX = socketPins.map { it.pinX }.average().toFloat()
                                    val avgY = socketPins.map { it.pinY }.average().toFloat()
                                    val posX = (avgX / 100f) * fsWidth.value
                                    val posY = (avgY / 100f) * fsHeight.value
                                    val isSocketSelected = selectedSocketName == socketName

                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (posX.dp.toPx() - (socketPins.size * (pinSizeDp + 6) / 2).dp.toPx()).toInt(),
                                                    (posY.dp.toPx() - 32.dp.toPx()).toInt()
                                                )
                                            }
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E293B).copy(alpha = 0.94f))
                                            .border(
                                                width = if (isSocketSelected) 2.5.dp else 1.2.dp,
                                                color = if (isSocketSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8).copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .pointerInput(socketName, zoomScale) {
                                                detectDragGestures(
                                                    onDragStart = { selectedSocketName = socketName },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val parentWidthPx = fsWidth.toPx()
                                                        val parentHeightPx = fsHeight.toPx()
                                                        if (parentWidthPx > 0 && parentHeightPx > 0) {
                                                            val deltaXPercent = ((dragAmount.x / zoomScale) / parentWidthPx) * 100f
                                                            val deltaYPercent = ((dragAmount.y / zoomScale) / parentHeightPx) * 100f
                                                            onMoveSocket(socketName, deltaXPercent, deltaYPercent)
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "$socketName (${socketPins.size})",
                                                color = if (isSocketSelected) Color(0xFF38BDF8) else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.clickable { selectedSocketName = socketName }
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                socketPins.forEach { pin ->
                                                    val realIndex = pins.indexOf(pin)
                                                    val isPinSelected = selectedPinIndex == realIndex
                                                    val pinColor = parseWireColorToComposeColor(pin.colorHex)

                                                    Box(
                                                        modifier = Modifier
                                                            .size(pinSizeDp.dp)
                                                            .clip(CircleShape)
                                                            .background(if (pin.isBlankPin) Color(0xFF334155) else if (isPinSelected) Color.White else pinColor)
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
                                                            Text("NC", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        } else {
                                                            Text(
                                                                text = "${pin.pinNumber}",
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isPinSelected) pinColor else if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                                                fontSize = if (pinSizeDp <= 20) 9.sp else 11.sp
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

                    // Bottom D-Pad bar in Fullscreen
                    selectedSocketName?.let { curSock ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تحكم في: $curSock", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onMoveSocket(curSock, moveStepPercent, 0f) }) {
                                        Icon(Icons.Default.ArrowForward, "يمين", tint = Color.White)
                                    }
                                    IconButton(onClick = { onMoveSocket(curSock, 0f, -moveStepPercent) }) {
                                        Icon(Icons.Default.ArrowUpward, "أعلى", tint = Color.White)
                                    }
                                    IconButton(onClick = { onMoveSocket(curSock, 0f, moveStepPercent) }) {
                                        Icon(Icons.Default.ArrowDownward, "أسفل", tint = Color.White)
                                    }
                                    IconButton(onClick = { onMoveSocket(curSock, -moveStepPercent, 0f) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "يسار", tint = Color.White)
                                    }
                                }

                                Button(
                                    onClick = { isFullscreenCanvas = false },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("تم وإغلاق", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS: ADD SOCKET, CONFIRM DELETE, PIN EDITOR
    // =========================================================================

    // Add Socket Dialog (Generic Multi-Wire Socket!)
    if (showAddSocketDialog) {
        AddSocketDialog(
            nextSocketNumber = distinctSockets.size + 1,
            onDismiss = { showAddSocketDialog = false },
            onConfirm = { socketName, pinCount ->
                onAddSocket(socketName, pinCount)
                selectedSocketName = socketName
                selectedPinIndex = null
                showAddSocketDialog = false
            }
        )
    }

    // Confirm Delete Socket Dialog
    socketToDelete?.let { sockName ->
        val count = socketGroups[sockName]?.size ?: 0
        AlertDialog(
            onDismissRequest = { socketToDelete = null },
            title = {
                Text("حذف $sockName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("هل أنت متأكد من حذف \"$sockName\" بجميع أطرافه ($count طرف) من الكارتة؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSocket(sockName)
                        if (selectedSocketName == sockName) {
                            selectedSocketName = null
                            selectedPinIndex = null
                        }
                        socketToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، حذف السوكيت")
                }
            },
            dismissButton = {
                TextButton(onClick = { socketToDelete = null }) {
                    Text("إلغاء")
                }
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
 * Dialog to add a full socket with custom number of pins (2, 3, 5, etc.).
 * Notice: Sockets are NOT locked to a single appliance component; individual pins can connect to any component!
 */
@Composable
fun AddSocketDialog(
    nextSocketNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (socketName: String, pinCount: Int) -> Unit
) {
    var socketNameInput by remember { mutableStateOf("سوكيت $nextSocketNumber") }
    var pinCountInput by remember { mutableStateOf("5") }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "حدد اسم السوكيت وعدد أطرافه (السوكيت عبارة عن كتلة تجمع عدة أسلاك، وكل سلك يمكن ربطه بأي قطعة لاحقاً):",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                OutlinedTextField(
                    value = socketNameInput,
                    onValueChange = { socketNameInput = it },
                    label = { Text("اسم السوكيت (مثال: سوكيت 1 أو CN1)") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = pinCountInput.toIntOrNull()?.coerceIn(1, 24) ?: 5
                    val sName = socketNameInput.trim().ifBlank { "سوكيت $nextSocketNumber" }
                    onConfirm(sName, count)
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
 * Each pin inside a socket can be assigned to any appliance component, have its own color, or be blank (NC).
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

                // Quick Socket Reassignment Chips
                Text("السوكيت التابع له هذا الطرف:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    existingSockets.forEach { sName ->
                        FilterChip(
                            selected = socketName == sName,
                            onClick = { socketName = sName },
                            label = { Text(sName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = socketName,
                    onValueChange = { socketName = it },
                    label = { Text("أو اكتب اسم سوكيت يدوي") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Connected Component Selector (This individual pin can connect to ANY part!)
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
                            Text("متصل بـ: $connectedComponent", fontWeight = FontWeight.Bold)
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
                    label = { Text("وظيفة الطرف (مثال: تاكو، شربون، تغذية لوك، طلمبة)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Color Selection
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

/**
 * Parses hex color strings safely into Compose Color.
 */
fun parseWireColorToComposeColor(hexString: String): Color {
    return try {
        val clean = hexString.removePrefix("#")
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> Color(0xFF2563EB)
        }
    } catch (_: Exception) {
        Color(0xFF2563EB)
    }
}
