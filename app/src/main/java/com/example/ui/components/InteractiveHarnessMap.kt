package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.util.ImageHelper

@Composable
fun InteractiveHarnessMap(
    harness: Harness,
    modifier: Modifier = Modifier
) {
    var selectedPinNumber by remember { mutableStateOf<Int?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("visual") } // "visual" or "table"

    val activeBoardPin = harness.boardPins.find { it.pinNumber == selectedPinNumber }
    val activeBottomPin = harness.bottomPins.find {
        activeBoardPin != null && it.connectedComponent == activeBoardPin.connectedComponent && it.pinNumber == activeBoardPin.targetPinNumber
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = harness.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "مخطط الضفيرة التفاعلي الكامل - يمكنك التحريك والتكبير باللمس",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }

                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(imageVector = Icons.Default.FullscreenExit, contentDescription = "خروج من ملء الشاشة")
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        HarnessInteractiveCanvas(
                            harness = harness,
                            selectedPinNumber = selectedPinNumber,
                            onSelectPin = { selectedPinNumber = if (selectedPinNumber == it) null else it }
                        )
                    }

                    // Bottom info bar in fullscreen
                    activeBoardPin?.let { pin ->
                        HarnessPinInspectorBanner(boardPin = pin, bottomPin = activeBottomPin)
                    }
                }
            }
        }
    }

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
            // Header with toggle & fullscreen
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
                                imageVector = Icons.Default.Cable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "خريطة توصيل الضفيرة التفاعلية",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "تقريب وتحريك وتتبع مسارات الأسلاك بالصورة",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isFullscreen = true }) {
                        Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "عرض بملء الشاشة", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View Mode Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == "visual",
                    onClick = { viewMode = "visual" },
                    label = { Text("المخطط التفاعلي المصور (Zoom)", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = viewMode == "table",
                    onClick = { viewMode = "table" },
                    label = { Text("جدول بيانات الأطراف", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (viewMode == "visual") {
                // Interactive Canvas with Zoom & Pan
                HarnessInteractiveCanvas(
                    harness = harness,
                    selectedPinNumber = selectedPinNumber,
                    onSelectPin = { selectedPinNumber = if (selectedPinNumber == it) null else it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                // Table View of All Wires & Terminals
                HarnessTableView(
                    harness = harness,
                    selectedPinNumber = selectedPinNumber,
                    onSelectPin = { selectedPinNumber = if (selectedPinNumber == it) null else it }
                )
            }

            // Pin Inspector Details when a pin is selected
            activeBoardPin?.let { pin ->
                Spacer(modifier = Modifier.height(10.dp))
                HarnessPinInspectorBanner(boardPin = pin, bottomPin = activeBottomPin)
            }
        }
    }
}

/**
 * Interactive Canvas featuring Pan, Pinch-to-Zoom, Board Image with Hotspot Pins,
 * Connected Appliance Component Cards, and Smooth Physical Color Wires.
 */
@Composable
fun HarnessInteractiveCanvas(
    harness: Harness,
    selectedPinNumber: Int?,
    onSelectPin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Group bottom pins by appliance component
    val componentGroups = remember(harness.bottomPins) {
        if (harness.bottomPins.isNotEmpty()) {
            harness.bottomPins.groupBy { it.connectedComponent }
        } else {
            // Default groups from board pins target
            harness.boardPins.filter { !it.isBlankPin }.groupBy { it.connectedComponent }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0A0F1D))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.6f, 3.5f)
                    offset = Offset(offset.x + pan.x, offset.y + pan.y)
                }
            }
    ) {
        // Zoom & Pan Contained Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // TOP: The Control Board (الكارتة)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7))
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val boardWidth = maxWidth
                            val boardHeight = maxHeight

                            // Board photo or PCB graphic
                            if (harness.boardImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ImageHelper.getCoilData(harness.boardImageUrl))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "كارتة الغسالة",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // High-tech PCB Graphic Background
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF0369A1).copy(alpha = 0.2f), Color(0xFF0F172A))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "كارتة التحكم الرئيسية (Main PCB Board)",
                                            style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            // Render Socket Outlines on the Board
                            val distinctSockets = harness.boardPins.map { it.socketName.ifBlank { "سوكيت 1" } }.distinct()
                            distinctSockets.forEach { sockName ->
                                val sockPins = harness.boardPins.filter { (it.socketName.ifBlank { "سوكيت 1" }) == sockName }
                                if (sockPins.size > 1) {
                                    val minX = sockPins.minOf { if (it.pinX > 0f) it.pinX else 10f }
                                    val maxX = sockPins.maxOf { if (it.pinX > 0f) it.pinX else 90f }
                                    val minY = sockPins.minOf { if (it.pinY > 0f) it.pinY else 40f }
                                    val maxY = sockPins.maxOf { if (it.pinY > 0f) it.pinY else 75f }

                                    val sX = ((minX / 100f) * boardWidth.value) - 18
                                    val sY = ((minY / 100f) * boardHeight.value) - 22
                                    val sW = (((maxX - minX) / 100f) * boardWidth.value) + 36
                                    val sH = (((maxY - minY) / 100f) * boardHeight.value) + 36

                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    sX.coerceAtLeast(2f).dp.toPx().toInt(),
                                                    sY.coerceAtLeast(2f).dp.toPx().toInt()
                                                )
                                            }
                                            .width(sW.coerceAtLeast(50f).dp)
                                            .height(sH.coerceAtLeast(34f).dp)
                                            .background(Color(0xFF000000).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Text(
                                            text = sockName,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }

                            // Overlay Board Pins Hotspots
                            harness.boardPins.forEachIndexed { idx, pin ->
                                // If pinX/pinY set, use them; otherwise distribute evenly in socket row
                                val posX = if (pin.pinX > 0f) (pin.pinX / 100f) * boardWidth.value else (12f + (idx * (boardWidth.value - 24f) / harness.boardPins.size.coerceAtLeast(1)))
                                val posY = if (pin.pinY > 0f) (pin.pinY / 100f) * boardHeight.value else (boardHeight.value * 0.75f)

                                val isPinSelected = selectedPinNumber == pin.pinNumber
                                val isBlank = pin.isBlankPin
                                val pinColor = if (isBlank) Color(0xFF334155) else parseWireColorToComposeColor(pin.colorHex)

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            val bWidthPx = boardWidth.toPx()
                                            val bHeightPx = boardHeight.toPx()
                                            val pX = (posX.dp.toPx() - 14.dp.toPx()).coerceIn(4f, (bWidthPx - 32.dp.toPx()).coerceAtLeast(4f)).toInt()
                                            val pY = (posY.dp.toPx() - 14.dp.toPx()).coerceIn(4f, (bHeightPx - 32.dp.toPx()).coerceAtLeast(4f)).toInt()
                                            IntOffset(pX, pY)
                                        }
                                        .size(28.dp)
                                        .shadow(if (isPinSelected) 10.dp else 4.dp, CircleShape)
                                        .background(if (isPinSelected) Color.White else pinColor, CircleShape)
                                        .border(
                                            width = if (isPinSelected) 2.5.dp else 1.5.dp,
                                            color = if (isBlank) Color(0xFF94A3B8) else if (isPinSelected) pinColor else Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { onSelectPin(pin.pinNumber) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBlank) "NC" else "${pin.pinNumber}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBlank) Color(0xFFCBD5E1) else if (isPinSelected) pinColor else if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                            fontSize = if (isBlank) 9.sp else 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                // MIDDLE: Visual Wires Canvas with Smooth Curves
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val boardCount = harness.boardPins.size.coerceAtLeast(1)
                        val bottomCount = harness.bottomPins.size.coerceAtLeast(1)

                        harness.boardPins.forEachIndexed { bIndex, bPin ->
                            val isPinSelected = selectedPinNumber == bPin.pinNumber

                            // Calculate start position
                            val startX = if (bPin.pinX > 0f) (bPin.pinX / 100f) * canvasWidth else (bIndex + 0.5f) * (canvasWidth / boardCount)
                            val startY = 0f

                            if (bPin.isBlankPin) {
                                // Empty terminal dot
                                drawCircle(
                                    color = Color(0xFF64748B),
                                    radius = if (isPinSelected) 5f else 2.5f,
                                    center = Offset(startX, startY),
                                    style = Stroke(width = 1.5f)
                                )
                                return@forEachIndexed
                            }

                            // Calculate end position on corresponding bottom module
                            val targetIndex = harness.bottomPins.indexOfFirst {
                                it.connectedComponent == bPin.connectedComponent && it.pinNumber == bPin.targetPinNumber
                            }.takeIf { it >= 0 } ?: (bIndex % bottomCount)

                            val endX = (targetIndex + 0.5f) * (canvasWidth / bottomCount)
                            val endY = canvasHeight

                            val wirePath = Path().apply {
                                moveTo(startX, startY)
                                cubicTo(
                                    startX, startY + (canvasHeight * 0.5f),
                                    endX, endY - (canvasHeight * 0.5f),
                                    endX, endY
                                )
                            }

                            val primaryColor = parseWireColorToComposeColor(bPin.colorHex)
                            val wireWidth = if (isPinSelected) 6f else 2.8f
                            val wireAlpha = if (selectedPinNumber == null || isPinSelected) 1f else 0.18f

                            // Draw main wire
                            drawPath(
                                path = wirePath,
                                color = primaryColor.copy(alpha = wireAlpha),
                                style = Stroke(width = wireWidth, cap = StrokeCap.Round)
                            )

                            // Striped overlay if secondary color exists
                            if (bPin.secondaryColorHex != null) {
                                val secColor = parseWireColorToComposeColor(bPin.secondaryColorHex)
                                drawPath(
                                    path = wirePath,
                                    color = secColor.copy(alpha = wireAlpha),
                                    style = Stroke(
                                        width = wireWidth,
                                        cap = StrokeCap.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f)
                                    )
                                )
                            }
                        }
                    }
                }

                // BOTTOM: Connected Appliance Components (الماتور، لوك، ليفل، طلمبة، صمامات، سخان)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    componentGroups.forEach { (compName, compPins) ->
                        ApplianceModuleCard(
                            componentName = compName,
                            pins = compPins,
                            selectedPinNumber = selectedPinNumber,
                            onSelectPin = onSelectPin
                        )
                    }
                }
            }
        }
    }

        // Floating Zoom & Reset Controls in Top-End Corner
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3.5f) }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(0.6f) }) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "تصغير", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "إعادة ضبط", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Zoom & Drag Hint in Bottom-Start Corner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "🔍 اسحب للتحرك • باعد بإصبعين للتكبير (${String.format("%.1f", scale)}x)",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Module Card representing a physical washing machine component (Motor, Lock, Level Sensor, etc.)
 */
@Composable
fun ApplianceModuleCard(
    componentName: String,
    pins: List<HarnessPin>,
    selectedPinNumber: Int?,
    onSelectPin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = getComponentIconAndColor(componentName)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
        modifier = modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Module Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = componentName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Pins of this component
            pins.forEach { pin ->
                val isSelected = selectedPinNumber == pin.pinNumber
                val pinColor = parseWireColorToComposeColor(pin.colorHex)

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) tint.copy(alpha = 0.25f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onSelectPin(pin.pinNumber) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        WireColorBadge(
                            colorHex = pin.colorHex,
                            secondaryColorHex = pin.secondaryColorHex,
                            size = 10.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "#${pin.pinNumber} ${pin.label}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed Wire Table for Multimeter and Diagnostics.
 */
@Composable
fun HarnessTableView(
    harness: Harness,
    selectedPinNumber: Int?,
    onSelectPin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        harness.boardPins.forEach { bPin ->
            val isSelected = selectedPinNumber == bPin.pinNumber
            val targetPin = harness.bottomPins.find {
                it.connectedComponent == bPin.connectedComponent && it.pinNumber == bPin.targetPinNumber
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPin(bPin.pinNumber) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${bPin.pinNumber}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = bPin.label,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "متصل بـ: ${bPin.connectedComponent} (طرف ${bPin.targetPinNumber ?: '-'})",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WireColorBadge(colorHex = bPin.colorHex, secondaryColorHex = bPin.secondaryColorHex, size = 14.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bPin.wireColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inspector banner showing in-depth details of the selected pin connection.
 */
@Composable
fun HarnessPinInspectorBanner(
    boardPin: HarnessPin,
    bottomPin: HarnessPin?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WireColorBadge(
                        colorHex = boardPin.colorHex,
                        secondaryColorHex = boardPin.secondaryColorHex,
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "طرف الكارتة #${boardPin.pinNumber}: ${boardPin.label}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = boardPin.wireColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "📍 الوجهة: ${boardPin.connectedComponent} - الطرف المقابل #${boardPin.targetPinNumber ?: '1'}" +
                        (if (bottomPin != null && bottomPin.label.isNotBlank()) " (${bottomPin.label})" else ""),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
            )

            if (boardPin.signalType.isNotBlank()) {
                Text(
                    text = "⚡ نوع الإشارة: ${boardPin.signalType}",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                )
            }
        }
    }
}

fun getComponentIconAndColor(name: String): Pair<ImageVector, Color> {
    return when {
        name.contains("ماتور") -> Pair(Icons.Default.Memory, Color(0xFFEF4444))
        name.contains("لوك") -> Pair(Icons.Default.Lock, Color(0xFFD97706))
        name.contains("ليفل") || name.contains("ميزان") -> Pair(Icons.Default.Sensors, Color(0xFF0284C7))
        name.contains("سخان") || name.contains("حرارة") -> Pair(Icons.Default.Thermostat, Color(0xFFDC2626))
        name.contains("طلمبة") -> Pair(Icons.Default.WaterDamage, Color(0xFF9333EA))
        name.contains("صمام") -> Pair(Icons.Default.CleaningServices, Color(0xFF0D9488))
        name.contains("شداد") || name.contains("كلتش") -> Pair(Icons.Default.Tune, Color(0xFFEA580C))
        else -> Pair(Icons.Default.ElectricBolt, Color(0xFF64748B))
    }
}
