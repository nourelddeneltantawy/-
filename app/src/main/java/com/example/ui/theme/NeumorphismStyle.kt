package com.example.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NeuTokens {
    // Light Soft Neumorphism Palette
    val LightBg = Color(0xFFEAECEF)
    val LightSurface = Color(0xFFEDF0F3)
    val LightWell = Color(0xFFE2E5E9)
    val LightShadowDark = Color(0x59A3B1C6)   // rgba(163, 177, 198, 0.35)
    val LightShadowLight = Color(0xD9FFFFFF)  // rgba(255, 255, 255, 0.85)
    val LightBevelBorder = Color(0xBFFFFFFF)  // rgba(255, 255, 255, 0.75)
    val LightTextPrimary = Color(0xFF1E2229)
    val LightTextSecondary = Color(0xFF475569)

    // Dark Soft Neumorphism Palette
    val DarkBg = Color(0xFF1C1E22)
    val DarkSurface = Color(0xFF202328)
    val DarkWell = Color(0xFF16181B)
    val DarkShadowDark = Color(0xA60A0B0D)    // rgba(10, 11, 13, 0.65)
    val DarkShadowLight = Color(0x0AFFFFFF)   // rgba(255, 255, 255, 0.04)
    val DarkBevelBorder = Color(0x0FFFFFFF)   // rgba(255, 255, 255, 0.06)
    val DarkTextPrimary = Color(0xFFF1F3F5)
    val DarkTextSecondary = Color(0xFF94A3B8)

    fun bg(isDark: Boolean): Color = if (isDark) DarkBg else LightBg
    fun surface(isDark: Boolean): Color = if (isDark) DarkSurface else LightSurface
    fun well(isDark: Boolean): Color = if (isDark) DarkWell else LightWell
    fun shadowDark(isDark: Boolean): Color = if (isDark) DarkShadowDark else LightShadowDark
    fun shadowLight(isDark: Boolean): Color = if (isDark) DarkShadowLight else LightShadowLight
    fun bevelBorder(isDark: Boolean): Color = if (isDark) DarkBevelBorder else LightBevelBorder
    fun textPrimary(isDark: Boolean): Color = if (isDark) DarkTextPrimary else LightTextPrimary
    fun textSecondary(isDark: Boolean): Color = if (isDark) DarkTextSecondary else LightTextSecondary
}

/**
 * Extruded Soft Neumorphism surface with subtle bottom-right drop shadow, top-left light bloom,
 * and 1.dp bevel border. Works identically across GPU hardware canvases (minSdk 24+).
 */
fun Modifier.neuCard(
    cornerRadius: Dp = 20.dp,
    isDark: Boolean = false,
    isPressed: Boolean = false,
    backgroundColor: Color = NeuTokens.surface(isDark),
    accentGlow: Color? = null
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val cr = CornerRadius(radiusPx, radiusPx)
            if (!isPressed) {
                val darkCol = NeuTokens.shadowDark(isDark)
                val lightCol = NeuTokens.shadowLight(isDark)
                // Bottom-right soft ambient layers
                for (step in 3 downTo 1) {
                    val spread = step * 2.dp.toPx()
                    val offset = step * 2.2.dp.toPx()
                    val alphaMul = (4 - step) * 0.28f
                    drawRoundRect(
                        color = darkCol.copy(alpha = darkCol.alpha * alphaMul),
                        topLeft = Offset(offset - spread * 0.25f, offset - spread * 0.25f),
                        size = Size(size.width + spread * 0.5f, size.height + spread * 0.5f),
                        cornerRadius = CornerRadius(radiusPx + spread * 0.3f, radiusPx + spread * 0.3f)
                    )
                    drawRoundRect(
                        color = lightCol.copy(alpha = lightCol.alpha * alphaMul),
                        topLeft = Offset(-offset, -offset),
                        size = Size(size.width + spread * 0.35f, size.height + spread * 0.35f),
                        cornerRadius = CornerRadius(radiusPx + spread * 0.25f, radiusPx + spread * 0.25f)
                    )
                }
                if (accentGlow != null) {
                    drawRoundRect(
                        color = accentGlow.copy(alpha = if (isDark) 0.16f else 0.12f),
                        topLeft = Offset(0f, 3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = cr
                    )
                }
            }
        }
        .clip(shape)
        .background(
            if (isPressed) NeuTokens.well(isDark) else backgroundColor,
            shape
        )
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val cr = CornerRadius(radiusPx, radiusPx)
            if (isPressed) {
                // Concave inner shadow when pressed
                val insetDark = NeuTokens.shadowDark(isDark)
                val insetLight = NeuTokens.shadowLight(isDark)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            insetDark.copy(alpha = insetDark.alpha * 0.85f),
                            Color.Transparent,
                            insetLight.copy(alpha = insetLight.alpha * 0.8f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                // Top-left 1.dp specular bevel border
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuTokens.bevelBorder(isDark),
                            Color.Transparent,
                            NeuTokens.shadowDark(isDark).copy(alpha = if (isDark) 0.35f else 0.18f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
}

/**
 * Recessed Soft Neumorphism well (used for inputs, slider tracks, active nav indicator, logs).
 */
fun Modifier.neuRecessed(
    cornerRadius: Dp = 14.dp,
    isDark: Boolean = false,
    fillColor: Color = NeuTokens.well(isDark),
    accentBorder: Color? = null
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(fillColor, shape)
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val cr = CornerRadius(radiusPx, radiusPx)
            val topShadow = NeuTokens.shadowDark(isDark)
            val bottomHighlight = NeuTokens.shadowLight(isDark)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        topShadow.copy(alpha = topShadow.alpha * 0.9f),
                        Color.Transparent,
                        bottomHighlight.copy(alpha = if (isDark) 0.08f else 0.75f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = cr,
                style = Stroke(width = 2.5.dp.toPx())
            )
            if (accentBorder != null) {
                drawRoundRect(
                    color = accentBorder.copy(alpha = 0.55f),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
}

/**
 * Soft Neumorphism pill button that extrudes at rest and switches to a concave well with scale(0.98f) on press.
 */
@Composable
fun NeuPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    isDark: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "neu_pill_scale"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .scale(scale)
            .neuCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = (pressed || isActive) && enabled,
                backgroundColor = NeuTokens.surface(isDark),
                accentGlow = if (isActive) accentColor else null
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) accentColor.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun NeuBadge(
    text: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .neuRecessed(
                cornerRadius = 50.dp,
                isDark = isDark,
                accentBorder = accentColor.copy(alpha = 0.4f)
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    isDark: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = NeuTokens.surface(isDark),
            activeTrackColor = accentColor,
            inactiveTrackColor = NeuTokens.well(isDark)
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .neuCard(cornerRadius = 9.dp, isDark = isDark, accentGlow = accentColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        },
        track = { sliderState ->
            val fraction = ((sliderState.value - sliderState.valueRange.start) /
                (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(0.0001f))
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .neuRecessed(cornerRadius = 3.dp, isDark = isDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor)
                )
            }
        }
    )
}

/**
 * Extruded soft pill for secondary/outlined/admin actions (e.g., تعديل القوائم، ترتيب الشرائح، إضافة قائمة، مشرف).
 * Uses background NeuTokens.bg(isDark) (#EAECEF / #1C1E22) with zero colored outer border.
 */
@Composable
fun NeuActionPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDark: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    minHeight: Dp = 36.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "neu_action_pill_scale"
    )

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .scale(scale)
            .neuCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                backgroundColor = NeuTokens.bg(isDark),
                accentGlow = accentColor.copy(alpha = 0.25f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Extruded circular soft icon button (for top Back buttons, Edit pencil buttons, and Social icons).
 */
@Composable
fun NeuCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    enabled: Boolean = true,
    isDark: Boolean = false,
    backgroundColor: Color = NeuTokens.bg(isDark),
    accentGlow: Color? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "neu_circle_btn_scale"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .scale(scale)
            .neuCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                backgroundColor = backgroundColor,
                accentGlow = accentGlow
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Primary CTA Pill Button in Soft Neumorphism: extruded soft pill (#EDF0F3 / #202328) with subtle inner accent rim
 * and accent-colored text & icon instead of a solid blue gradient block.
 */
@Composable
fun NeuPrimaryCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDark: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "neu_primary_cta_scale"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .scale(scale)
            .neuCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                backgroundColor = NeuTokens.surface(isDark),
                accentGlow = accentColor
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f),
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Recessed (inset box-shadow) well pill for stats & status badges (users count, rating, open/locked, counts).
 */
@Composable
fun NeuStatusWellPill(
    isDark: Boolean = false,
    accentBorder: Color? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .neuRecessed(
                cornerRadius = 50.dp,
                isDark = isDark,
                accentBorder = accentBorder
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

