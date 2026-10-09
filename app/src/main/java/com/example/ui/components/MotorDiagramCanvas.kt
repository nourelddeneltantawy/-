package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Pinout

@Composable
fun MotorDiagramCanvas(
    pinouts: List<Pinout>,
    selectedPinIndex: Int?,
    onSelectPin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 3.5f)
        offset += panChange
    }

    Card(
        modifier = modifier
            .testTag("motor_diagram_canvas_card")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A) // Technical dark blue canvas background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            // Interactive schematic canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformState)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw technical grid lines
                val gridSpacing = 40f
                for (x in 0..(canvasWidth / gridSpacing).toInt()) {
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(x * gridSpacing, 0f),
                        end = Offset(x * gridSpacing, canvasHeight),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(canvasHeight / gridSpacing).toInt()) {
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, y * gridSpacing),
                        end = Offset(canvasWidth, y * gridSpacing),
                        strokeWidth = 1f
                    )
                }

                // Motor Housing Body Representation
                val motorCenterX = canvasWidth * 0.28f
                val motorCenterY = canvasHeight * 0.5f
                val motorRadius = 85f

                // Outer motor stator ring
                drawCircle(
                    color = Color(0xFF334155),
                    radius = motorRadius,
                    center = Offset(motorCenterX, motorCenterY),
                    style = Stroke(width = 8f)
                )

                // Copper winding coils glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD97706).copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(motorCenterX, motorCenterY),
                        radius = motorRadius * 0.85f
                    ),
                    radius = motorRadius * 0.85f,
                    center = Offset(motorCenterX, motorCenterY)
                )

                // Motor Rotor Shaft
                drawCircle(
                    color = Color(0xFF64748B),
                    radius = 32f,
                    center = Offset(motorCenterX, motorCenterY)
                )
                drawCircle(
                    color = Color(0xFF0EA5E9),
                    radius = 12f,
                    center = Offset(motorCenterX, motorCenterY)
                )

                // Terminal Connector Block on Right
                val connectorLeft = canvasWidth * 0.65f
                val connectorTop = 30f
                val connectorWidth = 90f
                val connectorHeight = canvasHeight - 60f

                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(connectorLeft, connectorTop),
                    size = Size(connectorWidth, connectorHeight),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = 3f)
                )

                val count = pinouts.size.coerceAtLeast(1)
                val pinSpacing = connectorHeight / (count + 1)

                // Draw wire traces from Motor Stator to Connector Pins
                pinouts.forEachIndexed { index, pin ->
                    val pinY = connectorTop + pinSpacing * (index + 1)
                    val wireColor = parseWireColorToComposeColor(pin.wireColor, pin.colorHex)
                    val isSelected = selectedPinIndex == index

                    // Stator wire exit point
                    val startAngle = -60f + (120f / (count.coerceAtLeast(2) - 1).coerceAtLeast(1)) * index
                    val rad = Math.toRadians(startAngle.toDouble())
                    val wireStartX = (motorCenterX + motorRadius * Math.cos(rad)).toFloat()
                    val wireStartY = (motorCenterY + motorRadius * Math.sin(rad)).toFloat()

                    val pinX = connectorLeft + 25f

                    // Curved wire path
                    val wirePath = Path().apply {
                        moveTo(wireStartX, wireStartY)
                        cubicTo(
                            wireStartX + 60f, wireStartY,
                            pinX - 50f, pinY,
                            pinX, pinY
                        )
                    }

                    // Draw wire shadow/glow if selected
                    if (isSelected) {
                        drawPath(
                            path = wirePath,
                            color = wireColor.copy(alpha = 0.4f),
                            style = Stroke(width = 12f, cap = StrokeCap.Round)
                        )
                    }

                    // Main wire line
                    drawPath(
                        path = wirePath,
                        color = wireColor,
                        style = Stroke(width = if (isSelected) 6f else 3.5f, cap = StrokeCap.Round)
                    )

                    // Pin terminal dot
                    drawCircle(
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        radius = if (isSelected) 8f else 5.5f,
                        center = Offset(pinX, pinY)
                    )
                }
            }

            // Quick Pin Selector list on the right side of the canvas
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pinouts.take(8).forEachIndexed { index, pin ->
                    val isSelected = selectedPinIndex == index
                    val color = parseWireColorToComposeColor(pin.wireColor, pin.colorHex)

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) color.copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.75f),
                        border = Stroke(width = 1f).let {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) color else Color.Transparent
                            )
                        },
                        modifier = Modifier
                            .testTag("diagram_pin_chip_${pin.pinNumber}")
                            .clickable { onSelectPin(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "#${pin.pinNumber}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Zoom In/Out & Reset Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { scale = (scale + 0.3f).coerceAtMost(3.5f) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1E293B).copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "تكبير المخطط",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1E293B).copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "إعادة ضبط الحجم",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Interactive diagram hint badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مخطط تفاعلي (اسحب للتكبير أو اختر طرفاً)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
