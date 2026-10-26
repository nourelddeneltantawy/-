package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Harness
import com.example.data.model.HarnessPin

@Composable
fun InteractiveHarnessMap(
    harness: Harness,
    modifier: Modifier = Modifier
) {
    var selectedPinNumber by remember { mutableStateOf<Int?>(null) }

    val activeBoardPin = harness.boardPins.find { it.pinNumber == selectedPinNumber }
    val activeBottomPin = harness.bottomPins.find { 
        activeBoardPin != null && it.connectedComponent == activeBoardPin.connectedComponent && it.pinNumber == activeBoardPin.targetPinNumber
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
                .padding(16.dp)
        ) {
            // Header
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
                            text = "اضغط على أي طرف في الكارتة أو القطعة لإظهار مسار السلك المتصل",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TOP SECTION: Control Board Header & Pins
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "سوكت كارتة التحكم (Control Board)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Text(
                            text = "${harness.boardPins.size} أطراف",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal scrollable Board Pins row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        harness.boardPins.forEach { pin ->
                            val isSelected = selectedPinNumber == pin.pinNumber
                            val pinColor = parseWireColorToComposeColor(pin.colorHex)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedPinNumber = if (isSelected) null else pin.pinNumber
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Pin Number badge
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${pin.pinNumber}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Wire color indicator dot/striped
                                    WireColorBadge(
                                        colorHex = pin.colorHex,
                                        secondaryColorHex = pin.secondaryColorHex,
                                        size = 14.dp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = pin.label.take(12),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

            Spacer(modifier = Modifier.height(12.dp))

            // MIDDLE SECTION: Wire Harness Visualizer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                    val width = size.width
                    val height = size.height

                    val boardCount = harness.boardPins.size.coerceAtLeast(1)
                    val bottomCount = harness.bottomPins.size.coerceAtLeast(1)

                    harness.boardPins.forEachIndexed { bIndex, bPin ->
                        val isPinSelected = selectedPinNumber == bPin.pinNumber
                        val startX = (bIndex + 0.5f) * (width / boardCount)
                        val startY = 8f

                        if (bPin.isBlankPin) {
                            // Empty terminal slot with no wire connection
                            drawCircle(
                                color = Color(0xFF64748B),
                                radius = if (isPinSelected) 5f else 3f,
                                center = Offset(startX, startY),
                                style = Stroke(width = 1.5f)
                            )
                            return@forEachIndexed
                        }

                        // Find matching bottom pin
                        val targetIndex = harness.bottomPins.indexOfFirst {
                            it.connectedComponent == bPin.connectedComponent && it.pinNumber == bPin.targetPinNumber
                        }.takeIf { it >= 0 } ?: (bIndex % bottomCount)

                        val endX = (targetIndex + 0.5f) * (width / bottomCount)
                        val endY = height - 8f

                        val wirePath = Path().apply {
                            moveTo(startX, startY)
                            cubicTo(
                                startX, startY + (height * 0.45f),
                                endX, endY - (height * 0.45f),
                                endX, endY
                            )
                        }

                        val primaryColor = parseWireColorToComposeColor(bPin.colorHex)
                        val strokeWidth = if (isPinSelected) 5f else 2.5f
                        val wireAlpha = if (selectedPinNumber == null || isPinSelected) 1f else 0.25f

                        // Draw primary wire
                        drawPath(
                            path = wirePath,
                            color = primaryColor.copy(alpha = wireAlpha),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // If striped, overlay secondary dashed wire
                        if (bPin.secondaryColorHex != null) {
                            val secColor = parseWireColorToComposeColor(bPin.secondaryColorHex)
                            drawPath(
                                path = wirePath,
                                color = secColor.copy(alpha = wireAlpha),
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                )
                            )
                        }

                        // Terminal dots
                        drawCircle(
                            color = if (isPinSelected) Color.White else primaryColor,
                            radius = if (isPinSelected) 6f else 3.5f,
                            center = Offset(startX, startY)
                        )
                        drawCircle(
                            color = if (isPinSelected) Color.White else primaryColor,
                            radius = if (isPinSelected) 6f else 3.5f,
                            center = Offset(endX, endY)
                        )
                    }
                }

                // Subtitle in canvas
                Text(
                    text = if (selectedPinNumber != null) "مسار السلك المحدد: الطرف $selectedPinNumber" else "خريطة مسارات الأسلاك والربط الداخلي",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM SECTION: Connected Appliance Components & Pins
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "أطراف الأجزاء السفلية (الماتور، اللوك، الليفل، الطلمبة)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        Text(
                            text = "${harness.bottomPins.size} أطراف",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal scrollable Bottom Pins row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        harness.bottomPins.forEach { pin ->
                            val isConnectedToSelected = activeBoardPin != null &&
                                    (pin.connectedComponent == activeBoardPin.connectedComponent && pin.pinNumber == activeBoardPin.targetPinNumber)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isConnectedToSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isConnectedToSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Component name tag
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isConnectedToSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = pin.connectedComponent,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConnectedToSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    // Pin number
                                    Text(
                                        text = "طرف ${pin.pinNumber}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Color badge
                                    WireColorBadge(
                                        colorHex = pin.colorHex,
                                        secondaryColorHex = pin.secondaryColorHex,
                                        size = 14.dp
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = pin.label.take(12),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pin Detail Inspector (shows when a pin is selected)
            AnimatedVisibility(
                visible = activeBoardPin != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeBoardPin?.let { bPin ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "بيانات الطرف المحدد (طرف الكارتة #${bPin.pinNumber})",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                WireColorBadge(
                                    colorHex = bPin.colorHex,
                                    secondaryColorHex = bPin.secondaryColorHex,
                                    size = 16.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• الوظيفة عند الكارتة: ${bPin.label}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• لون السلك: ${bPin.wireColor}${if (bPin.secondaryColorName != null) " مع خط ${bPin.secondaryColorName}" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• الطرف المقابل بالأسفل: يتصل بـ [${bPin.connectedComponent}] - الطرف رقم #${bPin.targetPinNumber ?: 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (bPin.signalType.isNotBlank()) {
                                Text(
                                    text = "• نوع الإشارة: ${bPin.signalType}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
