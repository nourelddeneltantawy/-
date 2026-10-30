package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * Overhauled to provide:
 * 1. Clean, vast interactive canvas with pinch-to-zoom (0.4x - 4.0x) & smooth two-finger pan.
 * 2. Direct 1-finger drag on any socket to reposition it across the board with mm precision.
 * 3. Modern Speed Dial Floating Action Button (FAB) menu matching user screenshot, eliminating top button clutter.
 * 4. High-tech PCB aesthetic with unified socket blocks identical to the interactive display map.
 */
@Composable
fun VisualBoardPinMarker(
    boardImageUrl: String,
    pins: List<HarnessPin>,
    bottomPins: List<HarnessPin> = emptyList(),
    onUpdateBoardImageUrl: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onAddSocket: (socketName: String, pinCount: Int) -> Unit = { _, _ -> },
    onMoveSocket: (socketName: String, deltaX: Float, deltaY: Float) -> Unit = { _, _, _ -> },
    onResizeSocket: (socketName: String, scaleFactor: Float) -> Unit = { _, _ -> },
    onDeleteSocket: (socketName: String) -> Unit = {},
    selectedSocketName: String?,
    onSelectSocket: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Speed Dial Menu State
    var isSpeedDialOpen by remember { mutableStateOf(false) }

    // Dialog States
    var showAddSocketDialog by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showEditSocketDialog by remember { mutableStateOf(false) }
    var socketToDelete by remember { mutableStateOf<String?>(null) }
    var showWires by remember { mutableStateOf(true) }

    // Group pins into Sockets
    val socketGroups = remember(pins) {
        pins.groupBy { it.socketName.ifBlank { "سوكيت 1" }.trim() }
    }
    val distinctSockets = remember(pins) {
        pins.map { it.socketName.ifBlank { "سوكيت 1" }.trim() }.distinct()
    }

    val activeSocket = selectedSocketName ?: distinctSockets.firstOrNull()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Minimal clean title + Socket count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مخطط كارتة الغسالة التفاعلي",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${distinctSockets.size} سوكيت",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "اسحب السوكيت بإصبعك لتغيير مكانه، أو استخدم القائمة الدائرية للأدوات",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =========================================================================
            // MAIN EXPANSIVE INTERACTIVE CANVAS WITH FLOATING SPEED DIAL
            // =========================================================================
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0F1D))
                        .border(1.5.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.0f)
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-1400f, 1400f)
                                panOffsetY = (panOffsetY + pan.y).coerceIn(-1400f, 1400f)
                            }
                        }
                ) {
                    val boxWidthPx = with(density) { maxWidth.toPx() }
                    val boxHeightPx = with(density) { maxHeight.toPx() }

                    // Zoomable & Pannable Virtual Board
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
                        // 1. Board Background Image or High-Tech PCB Graphic
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF0369A1).copy(alpha = 0.25f), Color(0xFF0B192C))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "كارتة التحكم الرئيسية (Main PCB Board)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // 2. Physical Wire Connections between Sockets and Bottom Terminals (if enabled)
                        if (showWires && bottomPins.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val bottomCount = bottomPins.size.coerceAtLeast(1)

                                pins.forEachIndexed { bIndex, bPin ->
                                    if (bPin.isBlankPin) return@forEachIndexed

                                    val targetIndex = bottomPins.indexOfFirst {
                                        (bPin.targetPinNumber != null && it.pinNumber == bPin.targetPinNumber && (bPin.connectedComponent.isBlank() || it.connectedComponent == bPin.connectedComponent)) ||
                                        (it.targetPinNumber != null && it.targetPinNumber == bPin.pinNumber)
                                    }

                                    if (targetIndex >= 0) {
                                        val startX = if (bPin.pinX > 0f) (bPin.pinX / 100f) * canvasWidth else (bIndex + 0.5f) * (canvasWidth / pins.size.coerceAtLeast(1))
                                        val startY = if (bPin.pinY > 0f) (bPin.pinY / 100f) * canvasHeight else 30f
                                        val endX = (targetIndex + 0.5f) * (canvasWidth / bottomCount)
                                        val endY = canvasHeight - 20f

                                        val wirePath = Path().apply {
                                            moveTo(startX, startY)
                                            cubicTo(
                                                startX, startY + (canvasHeight * 0.4f),
                                                endX, endY - (canvasHeight * 0.4f),
                                                endX, endY
                                            )
                                        }

                                        drawPath(
                                            path = wirePath,
                                            color = parseWireColorToComposeColor(bPin.colorHex).copy(alpha = 0.75f),
                                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Render Every Socket as a Solid Interactive Connector Block
                        distinctSockets.forEachIndexed { sockIndex, socketName ->
                            val socketPins = socketGroups[socketName] ?: emptyList()
                            if (socketPins.isNotEmpty()) {
                                val validPins = socketPins.filter { it.pinX > 0f && it.pinY > 0f }
                                val defaultX = 20f + ((sockIndex % 3) * 30f)
                                val defaultY = 25f + ((sockIndex / 3) * 30f)
                                val avgX = if (validPins.isNotEmpty()) validPins.map { it.pinX }.average().toFloat() else defaultX
                                val avgY = if (validPins.isNotEmpty()) validPins.map { it.pinY }.average().toFloat() else defaultY

                                val socketWidthPx = with(density) { (socketPins.size * 34 + 24).dp.toPx() }
                                val socketHeightPx = with(density) { 62.dp.toPx() }

                                val rawLeftPx = ((avgX / 100f) * boxWidthPx) - (socketWidthPx / 2f)
                                val rawTopPx = ((avgY / 100f) * boxHeightPx) - (socketHeightPx / 2f)

                                val leftPx = rawLeftPx.coerceIn(4f, (boxWidthPx - socketWidthPx - 4f).coerceAtLeast(4f))
                                val topPx = rawTopPx.coerceIn(4f, (boxHeightPx - socketHeightPx - 4f).coerceAtLeast(4f))

                                val isSelected = activeSocket == socketName

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(leftPx.toInt(), topPx.toInt()) }
                                        .shadow(if (isSelected) 10.dp else 4.dp, RoundedCornerShape(10.dp))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.2.dp,
                                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B).copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .pointerInput(socketName, zoomScale) {
                                            detectDragGestures(
                                                onDragStart = { onSelectSocket(socketName) },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (boxWidthPx > 0 && boxHeightPx > 0) {
                                                        val deltaXPercent = ((dragAmount.x / zoomScale) / boxWidthPx) * 100f
                                                        val deltaYPercent = ((dragAmount.y / zoomScale) / boxHeightPx) * 100f
                                                        onMoveSocket(socketName, deltaXPercent, deltaYPercent)
                                                    }
                                                }
                                            )
                                        }
                                        .clickable { onSelectSocket(socketName) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Socket Header
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Power,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$socketName (${socketPins.size} أطراف)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        // Horizontal row of pin badges
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            socketPins.forEach { pin ->
                                                val pColor = parseWireColorToComposeColor(pin.colorHex)
                                                Surface(
                                                    shape = CircleShape,
                                                    color = pColor,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${pin.pinNumber}",
                                                            color = if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
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

                    // 4. Floating Selected Socket Inspector Bar at TOP-CENTER of Canvas
                    activeSocket?.let { sock ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A).copy(alpha = 0.95f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.Power, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Text(
                                    text = sock,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Scale up socket (+)
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { onResizeSocket(sock, 1.15f) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Scale down socket (-)
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { onResizeSocket(sock, 0.85f) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Delete socket
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { socketToDelete = sock }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف السوكيت", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Deselect
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF334155),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable { onSelectSocket(null) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 5. Floating Zoom & Reset Chips at TOP-END of Canvas
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة ضبط", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    // 6. SPEED DIAL FLOATING ACTION BUTTON (MATCHING USER SCREENSHOT)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Expanding Speed Dial Menu Items
                            AnimatedVisibility(
                                visible = isSpeedDialOpen,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Item 1: Add New Socket
                                    SpeedDialItem(
                                        label = "إضافة سوكيت جديد",
                                        icon = Icons.Default.Add,
                                        iconTint = Color.White,
                                        iconBgColor = Color(0xFF0284C7),
                                        onClick = {
                                            isSpeedDialOpen = false
                                            showAddSocketDialog = true
                                        }
                                    )

                                    // Item 2: Change Board Image
                                    SpeedDialItem(
                                        label = "صورة الكارتة",
                                        icon = Icons.Default.Image,
                                        iconTint = Color.White,
                                        iconBgColor = Color(0xFFD97706),
                                        onClick = {
                                            isSpeedDialOpen = false
                                            showImageDialog = true
                                        }
                                    )

                                    // Item 3: Edit Selected Socket
                                    SpeedDialItem(
                                        label = if (activeSocket != null) "تعديل: $activeSocket" else "تعديل سوكيت",
                                        icon = Icons.Default.Edit,
                                        iconTint = Color.White,
                                        iconBgColor = Color(0xFF8B5CF6),
                                        onClick = {
                                            isSpeedDialOpen = false
                                            if (activeSocket != null) {
                                                showEditSocketDialog = true
                                            } else {
                                                Toast.makeText(context, "الرجاء النقر على سوكيت أولاً لتعديله", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )

                                    // Item 4: Toggle Wires
                                    if (bottomPins.isNotEmpty()) {
                                        SpeedDialItem(
                                            label = if (showWires) "إخفاء مسار الأسلاك" else "إظهار مسار الأسلاك",
                                            icon = if (showWires) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            iconTint = Color.White,
                                            iconBgColor = Color(0xFF10B981),
                                            onClick = {
                                                showWires = !showWires
                                                isSpeedDialOpen = false
                                            }
                                        )
                                    }

                                    // Item 5: Center View
                                    SpeedDialItem(
                                        label = "توسيط الكارتة",
                                        icon = Icons.Default.CenterFocusStrong,
                                        iconTint = Color.White,
                                        iconBgColor = Color(0xFF334155),
                                        onClick = {
                                            zoomScale = 1f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                            isSpeedDialOpen = false
                                        }
                                    )
                                }
                            }

                            // Main Speed Dial Trigger FAB (Teal / Cyan)
                            FloatingActionButton(
                                onClick = { isSpeedDialOpen = !isSpeedDialOpen },
                                containerColor = if (isSpeedDialOpen) Color(0xFFEF4444) else Color(0xFF0D9488),
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeedDialOpen) Icons.Default.Close else Icons.Default.Tune,
                                    contentDescription = "أدوات التعديل السريعة",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS
    // =========================================================================

    // Add Socket Dialog
    if (showAddSocketDialog) {
        AddSocketDialog(
            nextSocketNumber = distinctSockets.size + 1,
            onDismiss = { showAddSocketDialog = false },
            onConfirm = { socketName, pinCount ->
                onAddSocket(socketName, pinCount)
                onSelectSocket(socketName)
                showAddSocketDialog = false
            }
        )
    }

    // Board Image Dialog
    if (showImageDialog) {
        BoardImagePickerOrUrlDialog(
            currentUrl = boardImageUrl,
            onPickFromGallery = {
                showImageDialog = false
                onPickImageClick()
            },
            onSaveUrl = { url ->
                onUpdateBoardImageUrl(url)
                showImageDialog = false
            },
            onDismiss = { showImageDialog = false }
        )
    }

    // Edit Selected Socket Dialog
    if (showEditSocketDialog && activeSocket != null) {
        val sPins = socketGroups[activeSocket] ?: emptyList()
        EditSocketDialog(
            socketName = activeSocket,
            pinsCount = sPins.size,
            onDismiss = { showEditSocketDialog = false },
            onResize = { scale -> onResizeSocket(activeSocket, scale) },
            onDelete = {
                socketToDelete = activeSocket
                showEditSocketDialog = false
            }
        )
    }

    // Confirm Delete Socket Dialog
    socketToDelete?.let { sockName ->
        AlertDialog(
            onDismissRequest = { socketToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("حذف $sockName؟", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف هذا السوكيت بالكامل مع جميع أطرافه؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSocket(sockName)
                        socketToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("نعم، حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { socketToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * Individual item inside the Speed Dial column matching user mockup.
 */
@Composable
fun SpeedDialItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier.clickable { onClick() }
    ) {
        // Label pill
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Circular Icon button
        Surface(
            shape = CircleShape,
            color = iconBgColor,
            shadowElevation = 6.dp,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BoardImagePickerOrUrlDialog(
    currentUrl: String,
    onPickFromGallery: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تغيير صورة الكارتة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "يمكنك اختيار صورة عالية الجودة من هاتفك أو إدخال رابط صورة مباشرة للكارتة:",
                    fontSize = 13.sp
                )

                // Pick from phone gallery button
                Button(
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختيار صورة من المعرض (Gallery)", fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(" أو كتابة رابط مباشر ", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("رابط أو مسار الصورة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveUrl(urlInput.trim()) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun EditSocketDialog(
    socketName: String,
    pinsCount: Int,
    onDismiss: () -> Unit,
    onResize: (Float) -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("خيارات: $socketName", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("يحتوي هذا السوكيت حالياً على $pinsCount أطراف.", fontSize = 13.sp)

                Text("تغيير الحجم والمسافة بين الأطراف:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onResize(1.15f) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تكبير (+)")
                    }

                    Button(
                        onClick = { onResize(0.85f) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصغير (-)")
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حذف هذا السوكيت بالكامل")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("تم")
            }
        }
    )
}

@Composable
fun AddSocketDialog(
    nextSocketNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (socketName: String, pinCount: Int) -> Unit
) {
    var socketName by remember { mutableStateOf("سوكيت $nextSocketNumber") }
    var pinCountText by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة سوكيت جديد للكارتة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("حدد اسم السوكيت وعدد الأطراف التي تريدها بكل حرية:", fontSize = 13.sp)
                OutlinedTextField(
                    value = socketName,
                    onValueChange = { socketName = it },
                    label = { Text("اسم السوكيت (مثل CN1 أو سوكيت الموتور)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Editable Pin Count Input with Stepper Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val cur = pinCountText.toIntOrNull() ?: 4
                            if (cur > 1) pinCountText = (cur - 1).toString()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = pinCountText,
                        onValueChange = { input ->
                            pinCountText = input.filter { it.isDigit() }.take(2)
                        },
                        label = { Text("عدد الأطراف") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )

                    IconButton(
                        onClick = {
                            val cur = pinCountText.toIntOrNull() ?: 4
                            if (cur < 50) pinCountText = (cur + 1).toString()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text("خيارات سريعة لعدد الأطراف:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16).forEach { count ->
                        val isSelected = pinCountText == count.toString()
                        Button(
                            onClick = { pinCountText = count.toString() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$count")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = pinCountText.toIntOrNull()?.coerceIn(1, 50) ?: 4
                    val finalName = socketName.trim().ifEmpty { "سوكيت $nextSocketNumber" }
                    onConfirm(finalName, count)
                }
            ) {
                Text("إضافة السوكيت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
