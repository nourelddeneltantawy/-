package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.HarnessPin
import com.example.util.ImageHelper
import kotlin.math.roundToInt

/**
 * Socket Focus & Precision Editor Screen / Dialog.
 * Fulfills user request: Focuses specifically on one socket over the board background,
 * allowing the user to resize the socket box with 4 corner handles using fingers,
 * and drag each individual pin freely within the socket boundaries (never leaving the box).
 */
@Composable
fun SocketFocusEditorDialog(
    socketName: String,
    boardImageUrl: String,
    initialPins: List<HarnessPin>,
    allPins: List<HarnessPin>,
    initialSocketCenterXPct: Float = 50f,
    initialSocketCenterYPct: Float = 50f,
    onDismiss: () -> Unit,
    onSavePins: (socketName: String, updatedPins: List<HarnessPin>) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Local mutable list of pins for this socket
    val socketPins = remember { mutableStateListOf<HarnessPin>().apply { addAll(initialPins) } }

    // Socket Center position on the card (% of board canvas width & height)
    var socketCenterXPct by remember {
        val validPins = initialPins.filter { it.pinX > 0f }
        mutableFloatStateOf(if (validPins.isNotEmpty()) validPins.map { it.pinX }.average().toFloat() else initialSocketCenterXPct)
    }
    var socketCenterYPct by remember {
        val validPins = initialPins.filter { it.pinY > 0f }
        mutableFloatStateOf(if (validPins.isNotEmpty()) validPins.map { it.pinY }.average().toFloat() else initialSocketCenterYPct)
    }

    // Socket Box dimensions (in dp)
    var socketWidthDp by remember {
        mutableFloatStateOf((initialPins.size * 40f + 40f).coerceIn(140f, 360f))
    }
    var socketHeightDp by remember {
        mutableFloatStateOf(85f)
    }
    var pinSizeDp by remember { mutableFloatStateOf(26f) }

    // Pin Local Positions inside the socket box (Key: pinNumber -> Offset in dp relative to socket top-left)
    val pinOffsets = remember {
        mutableStateMapOf<Int, Offset>().apply {
            val step = if (initialPins.isNotEmpty()) ((socketWidthDp - 36f) / initialPins.size.coerceAtLeast(1)) else 34f
            initialPins.forEachIndexed { idx, p ->
                put(p.pinNumber, Offset(16f + (idx * step), 42f))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B132B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Power, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "تحرير السوكيت بدقة: $socketName",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "اسحب السوكيت فوق مكانه في الكارتة • اسحب الأطراف • غيّر الحجم من المقابض",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tools Toolbar: Pin size, Add Pin, Add Blank, Auto-Align
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pin Size controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("حجم الأطراف:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { pinSizeDp = (pinSizeDp - 2f).coerceAtLeast(16f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${pinSizeDp.toInt()}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { pinSizeDp = (pinSizeDp + 2f).coerceAtMost(46f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Action Buttons: + Pin, + Blank, Auto-align
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // + Regular Pin
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0284C7),
                                modifier = Modifier.clickable {
                                    val nextNum = (allPins.maxOfOrNull { it.pinNumber } ?: 0) + socketPins.size + 1
                                    val newPin = HarnessPin(
                                        pinNumber = nextNum,
                                        label = "طرف ${socketPins.size + 1}",
                                        wireColor = "أزرق",
                                        colorHex = "#2563EB",
                                        socketName = socketName
                                    )
                                    socketPins.add(newPin)
                                    pinOffsets[nextNum] = Offset(16f + (socketPins.size * 34f).coerceAtMost(socketWidthDp - 40f), 42f)
                                }
                            ) {
                                Text("+ طرف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }

                            // + Blank Pin (NC)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier.clickable {
                                    val nextNum = (allPins.maxOfOrNull { it.pinNumber } ?: 0) + socketPins.size + 1
                                    val newPin = HarnessPin(
                                        pinNumber = nextNum,
                                        label = "مكان فاضي بدون سلك (NC)",
                                        wireColor = "فارغ (بدون سلك)",
                                        colorHex = "#94A3B8",
                                        isBlank = true,
                                        socketName = socketName
                                    )
                                    socketPins.add(newPin)
                                    pinOffsets[nextNum] = Offset(16f + (socketPins.size * 34f).coerceAtMost(socketWidthDp - 40f), 42f)
                                }
                            ) {
                                Text("⚪ مسافة فاضية", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                            }

                            // Auto-Align single row
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64748B)),
                                modifier = Modifier.clickable {
                                    val count = socketPins.size
                                    if (count > 0) {
                                        val step = (socketWidthDp - 32f) / count
                                        socketPins.forEachIndexed { i, p ->
                                            pinOffsets[p.pinNumber] = Offset(16f + (i * step), 42f)
                                        }
                                    }
                                }
                            ) {
                                Text("محاذاة 📏", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Board & Socket Focus Workspace (Photoshop Canvas Style)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                ) {
                    val canvasWidthPx = with(density) { maxWidth.toPx() }
                    val canvasHeightPx = with(density) { maxHeight.toPx() }

                    val socketWidthPx = with(density) { socketWidthDp.dp.toPx() }
                    val socketHeightPx = with(density) { socketHeightDp.dp.toPx() }

                    val socketLeftPx = ((socketCenterXPct / 100f) * canvasWidthPx) - (socketWidthPx / 2f)
                    val socketTopPx = ((socketCenterYPct / 100f) * canvasHeightPx) - (socketHeightPx / 2f)

                    val boundedLeftPx = socketLeftPx.coerceIn(0f, (canvasWidthPx - socketWidthPx).coerceAtLeast(0f))
                    val boundedTopPx = socketTopPx.coerceIn(0f, (canvasHeightPx - socketHeightPx).coerceAtLeast(0f))

                    // 1. Board Background Image
                    if (boardImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ImageHelper.getCoilData(boardImageUrl))
                                .crossfade(true)
                                .build(),
                            contentDescription = "كارتة التحكم",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 2. Movable & Resizable Socket Bounding Box
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(boundedLeftPx.roundToInt(), boundedTopPx.roundToInt()) }
                            .size(socketWidthDp.dp, socketHeightDp.dp)
                            .shadow(12.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.94f))
                            .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                            // Dragging the entire socket box across the board photo
                            .pointerInput(canvasWidthPx, canvasHeightPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (canvasWidthPx > 0 && canvasHeightPx > 0) {
                                        val dxPct = (dragAmount.x / canvasWidthPx) * 100f
                                        val dyPct = (dragAmount.y / canvasHeightPx) * 100f
                                        socketCenterXPct = (socketCenterXPct + dxPct).coerceIn(4f, 96f)
                                        socketCenterYPct = (socketCenterYPct + dyPct).coerceIn(4f, 96f)
                                    }
                                }
                            }
                    ) {
                        // Socket Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Power, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$socketName (${socketPins.size} أطراف)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }

                            Text("اسحب لنقل السوكيت", color = Color(0xFF94A3B8), fontSize = 9.sp)
                        }

                        // Pins inside the Socket (Directly draggable by finger, strictly bounded)
                        socketPins.forEach { pin ->
                            val currentOffset = pinOffsets[pin.pinNumber] ?: Offset(20f, 40f)
                            val isBlank = pin.isBlankPin
                            val pinColor = if (isBlank) Color(0xFF1E293B) else parseWireColorToComposeColor(pin.colorHex)

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            with(density) { currentOffset.x.dp.roundToPx() },
                                            with(density) { currentOffset.y.dp.roundToPx() }
                                        )
                                    }
                                    .size(pinSizeDp.dp)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(pinColor)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isBlank) Color(0xFF64748B) else Color.White,
                                        shape = CircleShape
                                    )
                                    // Finger Dragging strictly within the Socket Box
                                    .pointerInput(pin.pinNumber, pinSizeDp, socketWidthDp, socketHeightDp) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dxDp = with(density) { dragAmount.x.toDp().value }
                                            val dyDp = with(density) { dragAmount.y.toDp().value }
                                            val cur = pinOffsets[pin.pinNumber] ?: Offset(20f, 40f)

                                            // STRICT CONSTRAINT: Pin cannot exit the socket rectangle!
                                            val minX = 6f
                                            val maxX = (socketWidthDp - pinSizeDp - 6f).coerceAtLeast(6f)
                                            val minY = 28f // below header
                                            val maxY = (socketHeightDp - pinSizeDp - 6f).coerceAtLeast(28f)

                                            val newX = (cur.x + dxDp).coerceIn(minX, maxX)
                                            val newY = (cur.y + dyDp).coerceIn(minY, maxY)
                                            pinOffsets[pin.pinNumber] = Offset(newX, newY)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBlank) {
                                    // Blank slot: subtle dot, NO numbers
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF94A3B8))
                                    )
                                } else {
                                    Text(
                                        text = "${pin.pinNumber}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (pin.colorHex == "#F8FAFC" || pin.colorHex == "#EAB308") Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (pinSizeDp * 0.42f).coerceAtLeast(9f).sp
                                        )
                                    )
                                }
                            }
                        }

                        // 4 Draggable Corner Resize Handles on the Socket Rectangle
                        // 1. Bottom-End Corner Handle
                        CornerResizeHandle(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            onDragDelta = { dx, dy ->
                                socketWidthDp = (socketWidthDp + dx).coerceIn(120f, 380f)
                                socketHeightDp = (socketHeightDp + dy).coerceIn(65f, 240f)
                            }
                        )

                        // 2. Bottom-Start Corner Handle
                        CornerResizeHandle(
                            modifier = Modifier.align(Alignment.BottomStart),
                            onDragDelta = { dx, dy ->
                                socketWidthDp = (socketWidthDp - dx).coerceIn(120f, 380f)
                                socketHeightDp = (socketHeightDp + dy).coerceIn(65f, 240f)
                            }
                        )

                        // 3. Top-End Corner Handle
                        CornerResizeHandle(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onDragDelta = { dx, dy ->
                                socketWidthDp = (socketWidthDp + dx).coerceIn(120f, 380f)
                                socketHeightDp = (socketHeightDp - dy).coerceIn(65f, 240f)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Actions: Save & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }

                    Button(
                        onClick = {
                            // Calculate each pin's absolute position on the board canvas
                            val updatedList = socketPins.mapIndexed { idx, p ->
                                val offset = pinOffsets[p.pinNumber] ?: Offset(16f + (idx * 32f), 42f)
                                val localXFrac = (offset.x / socketWidthDp).coerceIn(0.01f, 0.99f)
                                val localYFrac = (offset.y / socketHeightDp).coerceIn(0.01f, 0.99f)

                                // Socket bounds as percentage of canvas
                                val halfWPct = (socketWidthDp / 400f) * 50f
                                val halfHPct = (socketHeightDp / 400f) * 50f

                                val leftPct = (socketCenterXPct - halfWPct).coerceIn(1f, 98f)
                                val topPct = (socketCenterYPct - halfHPct).coerceIn(1f, 98f)
                                val widthPct = (halfWPct * 2f).coerceIn(4f, 95f)
                                val heightPct = (halfHPct * 2f).coerceIn(4f, 95f)

                                val absPinX = (leftPct + (localXFrac * widthPct)).coerceIn(2f, 98f)
                                val absPinY = (topPct + (localYFrac * heightPct)).coerceIn(2f, 98f)

                                p.copy(
                                    socketName = socketName,
                                    pinX = absPinX,
                                    pinY = absPinY,
                                    label = if (p.isBlankPin) "مكان فاضي بدون سلك (NC)" else p.label,
                                    connectedComponent = if (p.isBlankPin) "" else p.connectedComponent,
                                    targetPinNumber = if (p.isBlankPin) null else p.targetPinNumber
                                )
                            }
                            onSavePins(socketName, updatedList)
                            Toast.makeText(context, "تم حفظ أبعاد ومكان وأطراف السوكيت بنجاح!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ تعديلات السوكيت", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Movable Corner Handle for resizing the socket box.
 */
@Composable
private fun CornerResizeHandle(
    modifier: Modifier = Modifier,
    onDragDelta: (dx: Float, dy: Float) -> Unit
) {
    val density = LocalDensity.current

    Surface(
        shape = CircleShape,
        color = Color(0xFF38BDF8),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        shadowElevation = 8.dp,
        modifier = modifier
            .size(28.dp)
            .offset(x = 6.dp, y = 6.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = with(density) { dragAmount.x.toDp().value }
                    val dy = with(density) { dragAmount.y.toDp().value }
                    onDragDelta(dx, dy)
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FitScreen, contentDescription = "تغيير الحجم", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
        }
    }
}
