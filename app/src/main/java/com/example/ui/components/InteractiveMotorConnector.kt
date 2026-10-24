package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Pinout

@Composable
fun InteractiveMotorConnector(
    pinouts: List<Pinout>,
    modifier: Modifier = Modifier,
    initialSelectedPinIndex: Int = 0
) {
    if (pinouts.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Cable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "لا توجد أطراف مضافة لهذا الماتور بعد",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
        return
    }

    var selectedIndex by remember(pinouts) {
        mutableStateOf(if (initialSelectedPinIndex in pinouts.indices) initialSelectedPinIndex else 0)
    }

    val selectedPin = pinouts.getOrNull(selectedIndex) ?: pinouts.first()
    val (selectedPrimary, selectedSecondary) = resolveWireColors(
        colorHex = selectedPin.colorHex,
        wireColorName = selectedPin.wireColor,
        secondaryColorHex = selectedPin.secondaryColorHex,
        secondaryColorName = selectedPin.secondaryColorName
    )
    val selectedBrush = getWireBrush(selectedPrimary, selectedSecondary)

    Card(
        modifier = modifier
            .testTag("interactive_motor_connector")
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131722) // High-tech dark connector socket chassis
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Interactive Hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cable,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فيشة أطراف الماتور التفاعلية",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "المس أي طرف للفحص",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Realistic Terminal Housing / Connector Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E2530),
                                Color(0xFF0F131A)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                // Scrollable Terminal Pins Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pinouts.forEachIndexed { index, pin ->
                        val isSelected = index == selectedIndex
                        val (pColor, sColor) = resolveWireColors(
                            colorHex = pin.colorHex,
                            wireColorName = pin.wireColor,
                            secondaryColorHex = pin.secondaryColorHex,
                            secondaryColorName = pin.secondaryColorName
                        )
                        val wireBrush = getWireBrush(pColor, sColor)

                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pin_scale"
                        )
                        val glowColor by animateColorAsState(
                            targetValue = if (isSelected) pColor else Color.Transparent,
                            label = "pin_glow"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .scale(scale)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedIndex = index
                                }
                        ) {
                            // Wire stub extending into socket (supports striped pattern)
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(wireBrush)
                            )

                            // Pin Terminal Head (Metallic / Plastic Socket)
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 54.dp)
                                    .shadow(
                                        elevation = if (isSelected) 8.dp else 2.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = glowColor,
                                        spotColor = glowColor
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isSelected) {
                                                listOf(
                                                    pColor.copy(alpha = 0.35f),
                                                    Color(0xFF1E293B)
                                                )
                                            } else {
                                                listOf(
                                                    Color(0xFF2D3748),
                                                    Color(0xFF1A202C)
                                                )
                                            }
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) pColor else Color(0xFF4A5568),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Terminal Number
                                    Text(
                                        text = "${pin.pinNumber}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Color Dot indicator (Striped brush if dual color)
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(wireBrush)
                                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Wire color label under pin
                            Text(
                                text = pin.wireColor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) pColor else Color(0xFF94A3B8)
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inspector Card: Details of the currently selected pin
            AnimatedContent(
                targetState = selectedPin,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pin_details_content"
            ) { pin ->
                val (pColor, sColor) = resolveWireColors(
                    colorHex = pin.colorHex,
                    wireColorName = pin.wireColor,
                    secondaryColorHex = pin.secondaryColorHex,
                    secondaryColorName = pin.secondaryColorName
                )
                val wireBrush = getWireBrush(pColor, sColor)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pin badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = pColor.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, pColor)
                                ) {
                                    Text(
                                        text = "الطرف #${pin.pinNumber}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = pColor
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Wire color swatch & name
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(wireBrush)
                                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pin.wireColor,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                if (sColor != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF334155)
                                    ) {
                                        Text(
                                            text = "سلك مخطط",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = Color(0xFF38BDF8)
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Role / Function indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = pColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = pin.name.ifBlank { "طرف توصيل" },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        }

                        // Detailed Description / Ohm resistance specifications
                        if (pin.functionDesc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pin.functionDesc,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 18.sp
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
