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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
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

    var showWires by remember { mutableStateOf(true) }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
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

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Wire toggle in fullscreen
                            FilterChip(
                                selected = showWires,
                                onClick = { showWires = !showWires },
                                label = { Text(if (showWires) "إخفاء الأسلاك" else "إظهار الأسلاك", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showWires) Icons.Default.Cable else Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )

                            IconButton(onClick = { isFullscreen = false }) {
                                Icon(imageVector = Icons.Default.FullscreenExit, contentDescription = "خروج من ملء الشاشة")
                            }
                        }
                    }

                    // Top info bar in fullscreen - prominently displayed under top bar
                    activeBoardPin?.let { pin ->
                        HarnessPinInspectorBanner(
                            boardPin = pin,
                            bottomPin = activeBottomPin,
                            onDismiss = { selectedPinNumber = null }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        HarnessInteractiveCanvas(
                            harness = harness,
                            selectedPinNumber = selectedPinNumber,
                            showWires = showWires,
                            onSelectPin = { selectedPinNumber = if (selectedPinNumber == it) null else it }
                        )
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

            // View Mode Chips & Wire Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = viewMode == "visual",
                    onClick = { viewMode = "visual" },
                    label = { Text("المخطط التفاعلي", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = viewMode == "table",
                    onClick = { viewMode = "table" },
                    label = { Text("جدول الأطراف", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (viewMode == "visual") {
                    FilterChip(
                        selected = showWires,
                        onClick = { showWires = !showWires },
                        label = { Text(if (showWires) "الأسلاك: ظاهرة 🔌" else "الأسلاك: مخفية 👁️", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showWires) Icons.Default.Cable else Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Pin Inspector Details when a pin is selected (Prominently displayed at TOP!)
            activeBoardPin?.let { pin ->
                Spacer(modifier = Modifier.height(10.dp))
                HarnessPinInspectorBanner(
                    boardPin = pin,
                    bottomPin = activeBottomPin,
                    onDismiss = { selectedPinNumber = null }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (viewMode == "visual") {
                // Interactive Canvas with Zoom & Pan
                HarnessInteractiveCanvas(
                    harness = harness,
                    selectedPinNumber = selectedPinNumber,
                    showWires = showWires,
                    onSelectPin = { selectedPinNumber = if (selectedPinNumber == it) null else it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
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
    showWires: Boolean = true,
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

    val density = LocalDensity.current

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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // TOP: The Control Board (الكارتة)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7))
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val boardWidth = maxWidth
                            val boardHeight = maxHeight
                            val bWidthPx = with(density) { boardWidth.toPx() }
                            val bHeightPx = with(density) { boardHeight.toPx() }

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

                            // Render Sockets on the Board with unified, solid connector blocks (identical to Editor)
                            val socketGroups = harness.boardPins.groupBy { it.socketName.ifBlank { "سوكيت 1" } }
                            socketGroups.entries.forEachIndexed { sockIndex, (sockName, sPins) ->
                                val defaultX = 20f + ((sockIndex % 3) * 28f)
                                val defaultY = 25f + ((sockIndex / 3) * 30f)
                                val avgX = sPins.map { if (it.pinX > 0f) it.pinX else defaultX }.average().toFloat()
                                val avgY = sPins.map { if (it.pinY > 0f) it.pinY else defaultY }.average().toFloat()

                                val socketWidthPx = with(density) { (sPins.size * 34 + 20).dp.toPx() }
                                val socketHeightPx = with(density) { 62.dp.toPx() }

                                val rawLeftPx = ((avgX / 100f) * bWidthPx) - (socketWidthPx / 2f)
                                val rawTopPx = ((avgY / 100f) * bHeightPx) - (socketHeightPx / 2f)
                                val leftPx = rawLeftPx.coerceIn(4f, (bWidthPx - socketWidthPx - 4f).coerceAtLeast(4f))
                                val topPx = rawTopPx.coerceIn(4f, (bHeightPx - socketHeightPx - 4f).coerceAtLeast(4f))

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(leftPx.toInt(), topPx.toInt()) }
                                        .shadow(4.dp, RoundedCornerShape(10.dp))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                                        .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "$sockName (${sPins.size} أطراف)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            sPins.forEach { pin ->
                                                val isPinSelected = selectedPinNumber == pin.pinNumber
                                                val isBlank = pin.isBlankPin
                                                val pinColor = if (isBlank) Color(0xFF1E293B) else parseWireColorToComposeColor(pin.colorHex)

                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .shadow(if (isPinSelected) 8.dp else 2.dp, CircleShape)
                                                        .clip(CircleShape)
                                                        .background(if (isPinSelected) Color.White else pinColor)
                                                        .border(
                                                            width = if (isPinSelected) 2.5.dp else 1.2.dp,
                                                            color = if (isBlank) Color(0xFF64748B).copy(alpha = 0.7f) else if (isPinSelected) pinColor else Color.White,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { onSelectPin(pin.pinNumber) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isBlank) {
                                                        // Empty space slot without number
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF64748B).copy(alpha = 0.6f))
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "${pin.pinNumber}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isPinSelected) pinColor else if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                                                fontSize = 10.sp
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

                // MIDDLE: Visual Wires Canvas with Smooth Curves (Toggleable)
                    if (showWires) {
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
                                (bPin.targetPinNumber != null && it.pinNumber == bPin.targetPinNumber && (bPin.connectedComponent.isBlank() || it.connectedComponent == bPin.connectedComponent)) ||
                                (it.targetPinNumber != null && it.targetPinNumber == bPin.pinNumber)
                            }

                            if (targetIndex < 0) {
                                // No matching target terminal exists on the bottom modules
                                // (e.g. board has 4 pins, but motor only has 3 pins).
                                // Do not draw a bogus wire connecting to pin 1!
                                drawCircle(
                                    color = parseWireColorToComposeColor(bPin.colorHex),
                                    radius = if (isPinSelected) 5f else 3f,
                                    center = Offset(startX, startY)
                                )
                                return@forEachIndexed
                            }

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
            } else {
                Spacer(modifier = Modifier.height(16.dp))
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
 * Detailed Wire Table for Multimeter and Diagnostics with smooth scrolling.
 */
@Composable
fun HarnessTableView(
    harness: Harness,
    selectedPinNumber: Int?,
    onSelectPin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${bPin.pinNumber}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
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
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header Row: Pin number + wire color badge + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WireColorBadge(
                        colorHex = boardPin.colorHex,
                        secondaryColorHex = boardPin.secondaryColorHex,
                        size = 22.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "طرف الكارتة #${boardPin.pinNumber} (${boardPin.label.ifBlank { "طرف ${boardPin.pinNumber}" }})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = parseWireColorToComposeColor(boardPin.colorHex)
                    ) {
                        Text(
                            text = boardPin.wireColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (boardPin.colorHex == "#F8FAFC" || boardPin.colorHex == "#EAB308") Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Direct Connection Details
            if (!boardPin.isBlankPin && boardPin.targetPinNumber != null && bottomPin != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔗 متصل بـ: ",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${boardPin.connectedComponent} - طرف رقم #${boardPin.targetPinNumber} (${bottomPin.label})",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                }
                val functionText = bottomPin.signalType.ifBlank { boardPin.signalType.ifBlank { bottomPin.label } }
                if (functionText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ وظيفة الطرف: ",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = functionText,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF1F5F9))
                        )
                    }
                }
            } else if (boardPin.isBlankPin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚪ الحالة: ",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "مسافة فاضية / طرف فارغ بدون سلك (NC)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠️ الحالة: ",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "طرف حر (غير متصل بأي موديول أو قطعة)",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                }
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
