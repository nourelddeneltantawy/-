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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * Features:
 * 1. UNIFIED PHYSICAL SOCKET BLOCKS ON BOARD:
 *    - Each socket is rendered as a distinct physical block that ALWAYS appears visibly on the board.
 *    - Pins are housed side-by-side inside the socket rectangle.
 *    - Moving the socket moves all its pins together.
 * 2. INTERACTION:
 *    - Pinch-to-zoom (0.6x to 4.0x) & Pan.
 *    - Zoom toolbar: (+), (-), (100%), (Fullscreen).
 *    - D-Pad movement arrows (▲, ▼, ◄, ►, 🎯) for mm precision.
 */
@Composable
fun VisualBoardPinMarker(
    boardImageUrl: String,
    pins: List<HarnessPin>,
    onUpdateBoardImageUrl: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onAddSocket: (socketName: String, pinCount: Int) -> Unit = { _, _ -> },
    onMoveSocket: (socketName: String, deltaX: Float, deltaY: Float) -> Unit = { _, _, _ -> },
    onDeleteSocket: (socketName: String) -> Unit = {},
    selectedSocketName: String?,
    onSelectSocket: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    var moveStepPercent by remember { mutableFloatStateOf(3f) }
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

    // Auto select first socket if none selected
    val activeSocketName = selectedSocketName?.takeIf { it in distinctSockets }
        ?: distinctSockets.firstOrNull()

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
                            text = "لوحة ومخطط سوكيتات الكارتة التفاعلية",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "يظهر كل سوكيت كمستطيل متماسك يجمع أطرافه ويتحرك على الكارتة كقطعة واحدة",
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
            // ZOOM & VISUAL TOOLBAR
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Zoom In/Out, Reset, Fullscreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.6f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "تصغير", modifier = Modifier.size(18.dp))
                            }

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

                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "تكبير", modifier = Modifier.size(18.dp))
                            }

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

                        Button(
                            onClick = { isFullscreenCanvas = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⛶ ملء الشاشة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Sizing & Height
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

            // Sockets Selector Chips
            if (distinctSockets.isNotEmpty()) {
                Text(
                    text = "السوكيتات المتوفرة على الكارتة (انقر لتحديد السوكيت وتحريكه بالأسهم):",
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
                        val isSelected = activeSocketName == sockName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)) else null,
                            modifier = Modifier.clickable { onSelectSocket(sockName) }
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

            // D-Pad Movement Arrows for Active Socket
            activeSocketName?.let { currentSocket ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تحريك: $currentSocket",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        )

                        // Arrow Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onMoveSocket(currentSocket, moveStepPercent, 0f) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "يمين", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveSocket(currentSocket, 0f, -moveStepPercent) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.size(28.dp).clickable {
                                    val sPins = socketGroups[currentSocket] ?: emptyList()
                                    if (sPins.isNotEmpty()) {
                                        val avgX = sPins.map { it.pinX }.average().toFloat()
                                        val avgY = sPins.map { it.pinY }.average().toFloat()
                                        onMoveSocket(currentSocket, 50f - avgX, 50f - avgY)
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "المنتصف", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            IconButton(onClick = { onMoveSocket(currentSocket, 0f, moveStepPercent) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveSocket(currentSocket, -moveStepPercent, 0f) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "يسار", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================================================================
            // INTERACTIVE BOARD CANVAS (GUARANTEED VISIBLE SOCKET BOXES)
            // =========================================================================
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.6f, 4.0f)
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-1200f, 1200f)
                                panOffsetY = (panOffsetY + pan.y).coerceIn(-1200f, 1200f)
                            }
                        }
                ) {
                    val density = LocalDensity.current
                    val boxWidthPx = with(density) { maxWidth.toPx() }
                    val boxHeightPx = with(density) { maxHeight.toPx() }

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
                        // 1. Board Background image or PCB circuit gradient
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

                        // 2. RENDER EACH SOCKET AS A SOLID PHYSICAL CONNECTOR BLOCK
                        distinctSockets.forEachIndexed { sockIndex, socketName ->
                            val socketPins = socketGroups[socketName] ?: emptyList()
                            if (socketPins.isNotEmpty()) {
                                val validPins = socketPins.filter { it.pinX > 0f && it.pinY > 0f }
                                val defaultX = 20f + ((sockIndex % 2) * 45f)
                                val defaultY = 20f + ((sockIndex / 2) * 35f)
                                val avgX = if (validPins.isNotEmpty()) validPins.map { it.pinX }.average().toFloat() else defaultX
                                val avgY = if (validPins.isNotEmpty()) validPins.map { it.pinY }.average().toFloat() else defaultY

                                val socketWidthPx = with(density) { (socketPins.size * (pinSizeDp + 8) + 24).dp.toPx() }
                                val socketHeightPx = with(density) { 64.dp.toPx() }

                                val rawLeftPx = ((avgX / 100f) * boxWidthPx) - (socketWidthPx / 2f)
                                val rawTopPx = ((avgY / 100f) * boxHeightPx) - (socketHeightPx / 2f)

                                // Clamped so it NEVER disappears off screen!
                                val leftPx = rawLeftPx.coerceIn(8f, (boxWidthPx - socketWidthPx - 8f).coerceAtLeast(8f))
                                val topPx = rawTopPx.coerceIn(8f, (boxHeightPx - socketHeightPx - 8f).coerceAtLeast(8f))

                                val isSocketSelected = activeSocketName == socketName

                                // Solid Physical Socket Box
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(leftPx.toInt(), topPx.toInt()) }
                                        .shadow(if (isSocketSelected) 12.dp else 4.dp, RoundedCornerShape(10.dp))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                                        .border(
                                            width = if (isSocketSelected) 2.5.dp else 1.2.dp,
                                            color = if (isSocketSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8).copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        // Dragging updates the socket position
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
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Socket Header
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.clickable { onSelectSocket(socketName) }
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
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        // Row of Pins inside the Socket
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            socketPins.forEach { pin ->
                                                val pinColor = parseWireColorToComposeColor(pin.colorHex)

                                                Box(
                                                    modifier = Modifier
                                                        .size(pinSizeDp.dp)
                                                        .clip(CircleShape)
                                                        .background(if (pin.isBlankPin) Color(0xFF334155) else pinColor)
                                                        .border(
                                                            width = 1.2.dp,
                                                            color = if (pin.isBlankPin) Color(0xFF64748B) else Color.White,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { onSelectSocket(socketName) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (pin.isBlankPin) {
                                                        Text("NC", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Text(
                                                            text = "${pin.pinNumber}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
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

                    // Floating Hint
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            text = "💡 اسحب أي سوكيت أو حدده وحركه بالأسهم • استخدم إصبعين للتكبير والتصغير",
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
        }
    }

    // =========================================================================
    // FULLSCREEN WORKSPACE DIALOG
    // =========================================================================
    if (isFullscreenCanvas) {
        Dialog(
            onDismissRequest = { isFullscreenCanvas = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
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
                            Text("ورشة الكارتة (شاشة كاملة)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.6f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "تصغير", tint = Color.White)
                            }
                            Text("${(zoomScale * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
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

                    // Canvas
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
                        val fsDensity = LocalDensity.current
                        val fsWidth = with(fsDensity) { maxWidth.toPx() }
                        val fsHeight = with(fsDensity) { maxHeight.toPx() }

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

                            // Sockets in Fullscreen
                            distinctSockets.forEachIndexed { sockIndex, socketName ->
                                val socketPins = socketGroups[socketName] ?: emptyList()
                                if (socketPins.isNotEmpty()) {
                                    val validPins = socketPins.filter { it.pinX > 0f && it.pinY > 0f }
                                    val defaultX = 20f + ((sockIndex % 2) * 45f)
                                    val defaultY = 20f + ((sockIndex / 2) * 35f)
                                    val avgX = if (validPins.isNotEmpty()) validPins.map { it.pinX }.average().toFloat() else defaultX
                                    val avgY = if (validPins.isNotEmpty()) validPins.map { it.pinY }.average().toFloat() else defaultY

                                    val socketWidthPx = with(fsDensity) { (socketPins.size * (pinSizeDp + 8) + 24).dp.toPx() }
                                    val socketHeightPx = with(fsDensity) { 64.dp.toPx() }

                                    val rawLeftPx = ((avgX / 100f) * fsWidth) - (socketWidthPx / 2f)
                                    val rawTopPx = ((avgY / 100f) * fsHeight) - (socketHeightPx / 2f)

                                    val leftPx = rawLeftPx.coerceIn(8f, (fsWidth - socketWidthPx - 8f).coerceAtLeast(8f))
                                    val topPx = rawTopPx.coerceIn(8f, (fsHeight - socketHeightPx - 8f).coerceAtLeast(8f))

                                    val isSocketSelected = activeSocketName == socketName

                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(leftPx.toInt(), topPx.toInt()) }
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                                            .border(
                                                width = if (isSocketSelected) 2.5.dp else 1.2.dp,
                                                color = if (isSocketSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8).copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .pointerInput(socketName, zoomScale) {
                                                detectDragGestures(
                                                    onDragStart = { onSelectSocket(socketName) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (fsWidth > 0 && fsHeight > 0) {
                                                            val deltaXPercent = ((dragAmount.x / zoomScale) / fsWidth) * 100f
                                                            val deltaYPercent = ((dragAmount.y / zoomScale) / fsHeight) * 100f
                                                            onMoveSocket(socketName, deltaXPercent, deltaYPercent)
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "$socketName (${socketPins.size})",
                                                color = if (isSocketSelected) Color(0xFF38BDF8) else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.clickable { onSelectSocket(socketName) }
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                socketPins.forEach { pin ->
                                                    val pinColor = parseWireColorToComposeColor(pin.colorHex)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(pinSizeDp.dp)
                                                            .clip(CircleShape)
                                                            .background(if (pin.isBlankPin) Color(0xFF334155) else pinColor)
                                                            .border(1.2.dp, if (pin.isBlankPin) Color(0xFF64748B) else Color.White, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (pin.isBlankPin) {
                                                            Text("NC", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        } else {
                                                            Text(
                                                                text = "${pin.pinNumber}",
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
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

                    // Bottom bar in Fullscreen
                    activeSocketName?.let { curSock ->
                        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1E293B)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
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
                        label = { Text("عدد الأطراف (اكتب أي عدد)") },
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

                Text("خيارات سريعة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 20).forEach { count ->
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

