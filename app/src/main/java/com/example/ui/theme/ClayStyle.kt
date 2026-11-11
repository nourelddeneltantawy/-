package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Signature Claymorphic / Soft Neumorphic palette matching user's reference image
val ClayLightBg = Color(0xFFEFF3F8) // Soft warm clay background
val ClayLightSurface = Color(0xFFFFFFFF) // Raised clean white clay card
val ClayLightSurfaceVariant = Color(0xFFE5EBF2) // Recessed indented tray / slider track
val ClayLightOutline = Color(0xFFD5DFEA) // Soft clay border
val ClayLightHighlight = Color(0xFFFFFFFF).copy(alpha = 0.95f) // Top reflection highlight

val ClayDarkBg = Color(0xFF0F172A) // Sleek dark slate clay background
val ClayDarkSurface = Color(0xFF1E293B) // Embossed dark slate card
val ClayDarkSurfaceVariant = Color(0xFF131B2B) // Recessed dark indented tray
val ClayDarkOutline = Color(0xFF334155) // Soft dark border
val ClayDarkHighlight = Color(0xFFFFFFFF).copy(alpha = 0.08f) // Subtle dark top edge highlight

/**
 * Modifier that applies the distinctive Soft Neumorphic / Claymorphic style:
 * - Generous rounded corners
 * - Dual-tone soft ambient + spot drop shadow
 * - Top edge light reflection border for an extruded tactile clay feel
 */
fun Modifier.clayCard(
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 8.dp,
    isDark: Boolean = false,
    shape: Shape = RoundedCornerShape(cornerRadius),
    borderWidth: Dp = 1.2.dp
): Modifier {
    val spotShadowColor = if (isDark) Color(0x38000000) else Color(0x1F000000)
    val ambientShadowColor = if (isDark) Color(0x22000000) else Color(0x12000000)
    val highlightBorder = if (isDark) ClayDarkHighlight else ClayLightHighlight

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            spotColor = spotShadowColor,
            ambientColor = ambientShadowColor,
            clip = false
        )
        .clip(shape)
        .border(
            width = borderWidth,
            color = highlightBorder,
            shape = shape
        )
}

/**
 * Modifier that creates the soft recessed / indented tray effect:
 * (As seen in the sliders, search boxes, and option tags in the reference image)
 */
fun Modifier.clayRecessed(
    cornerRadius: Dp = 16.dp,
    isDark: Boolean = false,
    shape: Shape = RoundedCornerShape(cornerRadius)
): Modifier {
    val bg = if (isDark) ClayDarkSurfaceVariant else ClayLightSurfaceVariant
    val border = if (isDark) Color(0xFF243046) else Color(0xFFD6DFEA)

    return this
        .clip(shape)
        .background(bg, shape)
        .border(
            width = 1.dp,
            color = border,
            shape = shape
        )
}

/**
 * Modifier for soft pill-shaped buttons and tags (e.g. "مجاني", "تحميل", "size الحجم")
 */
fun Modifier.clayPill(
    isDark: Boolean = false,
    cornerRadius: Dp = 50.dp
): Modifier {
    return this.clayCard(
        cornerRadius = cornerRadius,
        elevation = 3.dp,
        isDark = isDark,
        shape = RoundedCornerShape(cornerRadius),
        borderWidth = 1.dp
    )
}
