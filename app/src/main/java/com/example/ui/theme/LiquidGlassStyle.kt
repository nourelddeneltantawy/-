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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

object GlassTokens {
    // Light Liquid Glass Palette
    val LightBg = Color(0xFFE6E8EC)
    val LightSurface = Color(0x9EFFFFFF)        // rgba(255, 255, 255, 0.62)
    val LightSurfaceStrong = Color(0xCCFFFFFF)  // rgba(255, 255, 255, 0.80)
    val LightRecessed = Color(0x73D8DEE9)       // soft frosted inner tray
    val LightSpecularRim = Color(0xE6FFFFFF)    // bright specular top-left rim
    val LightBottomRim = Color(0x52CBD5E1)
    val LightShadow = Color(0x1C0F172A)         // subtle glass shadow
    val LightTextPrimary = Color(0xFF0F172A)
    val LightTextSecondary = Color(0xFF475569)

    // Dark Liquid Glass Palette
    val DarkBg = Color(0xFF0D0F12)
    val DarkSurface = Color(0x9E1A1E26)         // rgba(26, 30, 38, 0.62)
    val DarkSurfaceStrong = Color(0xCC1E232D)   // rgba(30, 35, 45, 0.80)
    val DarkRecessed = Color(0xA611141A)
    val DarkSpecularRim = Color(0x38FFFFFF)     // rgba(255, 255, 255, 0.22)
    val DarkBottomRim = Color(0x14FFFFFF)
    val DarkShadow = Color(0x66000000)
    val DarkTextPrimary = Color(0xFFF1F5F9)
    val DarkTextSecondary = Color(0xFFCBD5E1)

    fun bg(isDark: Boolean): Color = if (isDark) DarkBg else LightBg
    fun surface(isDark: Boolean): Color = if (isDark) DarkSurface else LightSurface
    fun surfaceStrong(isDark: Boolean): Color = if (isDark) DarkSurfaceStrong else LightSurfaceStrong
    fun recessed(isDark: Boolean): Color = if (isDark) DarkRecessed else LightRecessed
    fun specularRim(isDark: Boolean): Color = if (isDark) DarkSpecularRim else LightSpecularRim
    fun bottomRim(isDark: Boolean): Color = if (isDark) DarkBottomRim else LightBottomRim
    fun shadow(isDark: Boolean): Color = if (isDark) DarkShadow else LightShadow
    fun textPrimary(isDark: Boolean): Color = if (isDark) DarkTextPrimary else LightTextPrimary
    fun textSecondary(isDark: Boolean): Color = if (isDark) DarkTextSecondary else LightTextSecondary
}

/**
 * Ambient liquid glass canvas backdrop with soft diagonal/radial color orbs driven by the active Accent theme.
 */
fun Modifier.glassCanvasBackground(
    isDark: Boolean,
    accentColor: Color,
    secondaryColor: Color = accentColor
): Modifier {
    val baseBg = GlassTokens.bg(isDark)
    return this
        .background(baseBg)
        .drawBehind {
            val w = size.width
            val h = size.height
            val topOrbCenter = Offset(w * 0.82f, h * 0.12f)
            val midOrbCenter = Offset(w * 0.14f, h * 0.44f)
            val botOrbCenter = Offset(w * 0.72f, h * 0.84f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isDark) 0.22f else 0.18f),
                        Color.Transparent
                    ),
                    center = topOrbCenter,
                    radius = w * 0.65f
                ),
                center = topOrbCenter,
                radius = w * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = if (isDark) 0.16f else 0.14f),
                        Color.Transparent
                    ),
                    center = midOrbCenter,
                    radius = w * 0.58f
                ),
                center = midOrbCenter,
                radius = w * 0.58f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (isDark) 0.14f else 0.11f),
                        Color.Transparent
                    ),
                    center = botOrbCenter,
                    radius = w * 0.55f
                ),
                center = botOrbCenter,
                radius = w * 0.55f
            )
        }
}

/**
 * Translucent Liquid Glass card panel with soft drop shadow, vertical sheen, and specular top-left rim.
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 24.dp,
    isDark: Boolean = false,
    isPressed: Boolean = false,
    accentGlow: Color? = null,
    strongSurface: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val fillTop = when {
        isPressed -> GlassTokens.recessed(isDark)
        strongSurface -> GlassTokens.surfaceStrong(isDark)
        else -> GlassTokens.surface(isDark)
    }
    val fillBottom = when {
        isPressed -> GlassTokens.recessed(isDark)
        else -> fillTop.copy(alpha = (fillTop.alpha - 0.12f).coerceAtLeast(0.45f))
    }

    return this
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val shadowCol = GlassTokens.shadow(isDark)
            if (!isPressed) {
                for (step in 3 downTo 1) {
                    val spread = step * 2.2.dp.toPx()
                    val yOffset = step * 2.5.dp.toPx()
                    val alphaMul = (4 - step) * 0.28f
                    drawRoundRect(
                        color = shadowCol.copy(alpha = shadowCol.alpha * alphaMul),
                        topLeft = Offset(-spread * 0.15f, yOffset),
                        size = Size(size.width + spread * 0.3f, size.height + spread * 0.3f),
                        cornerRadius = CornerRadius(radiusPx + spread * 0.25f, radiusPx + spread * 0.25f)
                    )
                }
                if (accentGlow != null) {
                    drawRoundRect(
                        color = accentGlow.copy(alpha = if (isDark) 0.20f else 0.14f),
                        topLeft = Offset(0f, 3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )
                }
            }
        }
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(colors = listOf(fillTop, fillBottom)),
            shape = shape
        )
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val cr = CornerRadius(radiusPx, radiusPx)
            val rimTop = accentGlow?.copy(alpha = if (isDark) 0.55f else 0.45f) ?: GlassTokens.specularRim(isDark)
            val rimBot = GlassTokens.bottomRim(isDark)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(rimTop, rimBot, rimTop.copy(alpha = rimTop.alpha * 0.35f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = cr,
                style = Stroke(width = if (isPressed) 1.5.dp.toPx() else 1.2.dp.toPx())
            )
        }
}

/**
 * Frosted recessed Liquid Glass well for search inputs, status chips, active pills, and embedded trays.
 */
fun Modifier.glassRecessed(
    cornerRadius: Dp = 16.dp,
    isDark: Boolean = false,
    accentBorder: Color? = null
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(GlassTokens.recessed(isDark), shape)
        .drawBehind {
            val radiusPx = cornerRadius.toPx()
            val cr = CornerRadius(radiusPx, radiusPx)
            val borderCol = accentBorder ?: GlassTokens.specularRim(isDark).copy(alpha = if (isDark) 0.16f else 0.65f)
            drawRoundRect(
                color = borderCol,
                cornerRadius = cr,
                style = Stroke(width = 1.1.dp.toPx())
            )
        }
}

@Composable
fun GlassPillButton(
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
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "glass_pill_scale"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .scale(scale)
            .glassCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                accentGlow = if (isActive) accentColor else null,
                strongSurface = isActive
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) accentColor.copy(alpha = 0.45f) else Color.Transparent,
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
fun GlassPrimaryCtaButton(
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
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "glass_primary_cta_scale"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .scale(scale)
            .glassCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                accentGlow = accentColor,
                strongSurface = true
            )
            .border(
                width = 1.2.dp,
                color = accentColor.copy(alpha = if (isDark) 0.55f else 0.42f),
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
fun GlassActionPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDark: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    minHeight: Dp = 38.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "glass_action_pill_scale"
    )

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .scale(scale)
            .glassCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
                accentGlow = accentColor.copy(alpha = 0.3f)
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
fun GlassCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    enabled: Boolean = true,
    isDark: Boolean = false,
    accentGlow: Color? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "glass_circle_btn_scale"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .scale(scale)
            .glassCard(
                cornerRadius = 50.dp,
                isDark = isDark,
                isPressed = pressed && enabled,
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

@Composable
fun GlassBadge(
    text: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .glassRecessed(
                cornerRadius = 50.dp,
                isDark = isDark,
                accentBorder = accentColor.copy(alpha = 0.48f)
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.bidiIsolateMixed(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1
        )
    }
}

@Composable
fun GlassStatusWellPill(
    isDark: Boolean = false,
    accentBorder: Color? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .glassRecessed(
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
