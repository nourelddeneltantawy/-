package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun parseHexToColor(hex: String, fallback: Color = Color(0xFF3B82F6)): Color {
    val cleanHex = hex.trim()
    if (cleanHex.startsWith("#")) {
        try {
            val hexStr = cleanHex.substring(1)
            val colorLong = when (hexStr.length) {
                6 -> ("FF$hexStr").toLong(16)
                8 -> hexStr.toLong(16)
                3 -> {
                    val r = hexStr[0]
                    val g = hexStr[1]
                    val b = hexStr[2]
                    ("FF$r$r$g$g$b$b").toLong(16)
                }
                else -> fallback.value.toLong()
            }
            return Color(colorLong)
        } catch (_: Exception) {}
    }
    return fallback
}

fun parseWireColorNameToColor(name: String, fallback: Color = Color(0xFF3B82F6)): Color {
    val lower = name.lowercase().trim()
    return when {
        lower.contains("أحمر") || lower.contains("red") -> Color(0xFFEF4444)
        lower.contains("أزرق") || lower.contains("كحلي") || lower.contains("blue") -> {
            if (lower.contains("كحلي") || lower.contains("داكن")) Color(0xFF1E3A8A) else Color(0xFF2563EB)
        }
        lower.contains("سماوي") || lower.contains("cyan") -> Color(0xFF06B6D4)
        lower.contains("أصفر") || lower.contains("yellow") -> Color(0xFFEAB308)
        lower.contains("أخضر") || lower.contains("green") -> Color(0xFF16A34A)
        lower.contains("برتقالي") || lower.contains("orange") -> Color(0xFFF97316)
        lower.contains("أبيض") || lower.contains("white") -> Color(0xFFF8FAFC)
        lower.contains("أسود") || lower.contains("black") -> Color(0xFF1E293B)
        lower.contains("رمادي") || lower.contains("رصاصي") || lower.contains("gray") || lower.contains("grey") -> Color(0xFF64748B)
        lower.contains("بني") || lower.contains("brown") -> Color(0xFF854D0E)
        lower.contains("بنفسجي") || lower.contains("purple") -> Color(0xFF9333EA)
        lower.contains("زهري") || lower.contains("وردي") || lower.contains("بمبي") || lower.contains("pink") -> Color(0xFFEC4899)
        lower.contains("فارغ") || lower.contains("فاضي") || lower.contains("بدون") || lower.contains("nc") -> Color(0xFF94A3B8)
        lower.contains("بيج") || lower.contains("beige") -> Color(0xFFD97706)
        else -> fallback
    }
}

/**
 * Resolves both primary and optional secondary (striped) colors for a wire.
 * Handles both explicit secondary hex and compound names like "أصفر مع أخضر" or "أصفر/أخضر".
 */
fun resolveWireColors(
    colorHex: String,
    wireColorName: String,
    secondaryColorHex: String? = null,
    secondaryColorName: String? = null
): Pair<Color, Color?> {
    val primaryColor = parseHexToColor(colorHex, parseWireColorNameToColor(wireColorName))

    // 1. Explicit secondary color provided
    if (!secondaryColorHex.isNullOrBlank()) {
        val sec = parseHexToColor(secondaryColorHex, parseWireColorNameToColor(secondaryColorName ?: ""))
        return Pair(primaryColor, sec)
    }

    // 2. Compound text detection (e.g. "أصفر مع أخضر", "أصفر / أخضر", "أزرق مع أبيض")
    val text = (wireColorName + " " + (secondaryColorName ?: "")).trim()
    val isYellowGreen = (text.contains("أصفر") || text.contains("yellow", true)) &&
            (text.contains("أخضر") || text.contains("green", true))
    if (isYellowGreen) {
        return Pair(Color(0xFFEAB308), Color(0xFF16A34A))
    }

    val isBlueWhite = (text.contains("أزرق") || text.contains("blue", true)) &&
            (text.contains("أبيض") || text.contains("white", true))
    if (isBlueWhite) {
        return Pair(Color(0xFF2563EB), Color(0xFFF8FAFC))
    }

    val isRedBlack = (text.contains("أحمر") || text.contains("red", true)) &&
            (text.contains("أسود") || text.contains("black", true))
    if (isRedBlack) {
        return Pair(Color(0xFFEF4444), Color(0xFF1E293B))
    }

    val isWhiteBlack = (text.contains("أبيض") || text.contains("white", true)) &&
            (text.contains("أسود") || text.contains("black", true))
    if (isWhiteBlack) {
        return Pair(Color(0xFFF8FAFC), Color(0xFF1E293B))
    }

    val isYellowRed = (text.contains("أصفر") || text.contains("yellow", true)) &&
            (text.contains("أحمر") || text.contains("red", true))
    if (isYellowRed) {
        return Pair(Color(0xFFEAB308), Color(0xFFEF4444))
    }

    val isOrangeWhite = (text.contains("برتقالي") || text.contains("orange", true)) &&
            (text.contains("أبيض") || text.contains("white", true))
    if (isOrangeWhite) {
        return Pair(Color(0xFFF97316), Color(0xFFF8FAFC))
    }

    val isBrownWhite = (text.contains("بني") || text.contains("brown", true)) &&
            (text.contains("أبيض") || text.contains("white", true))
    if (isBrownWhite) {
        return Pair(Color(0xFF854D0E), Color(0xFFF8FAFC))
    }

    val isGrayWhite = (text.contains("رمادي") || text.contains("gray", true)) &&
            (text.contains("أبيض") || text.contains("white", true))
    if (isGrayWhite) {
        return Pair(Color(0xFF64748B), Color(0xFFF8FAFC))
    }

    val isBlueRed = (text.contains("أزرق") || text.contains("blue", true)) &&
            (text.contains("أحمر") || text.contains("red", true))
    if (isBlueRed) {
        return Pair(Color(0xFF2563EB), Color(0xFFEF4444))
    }

    return Pair(primaryColor, null)
}

/**
 * Creates an authentic diagonal striped brush for wires with 2 colors (e.g. Ground yellow/green).
 */
fun getWireBrush(primaryColor: Color, secondaryColor: Color?): Brush {
    if (secondaryColor == null || secondaryColor == primaryColor) {
        return Brush.linearGradient(listOf(primaryColor, primaryColor))
    }
    // Repeating diagonal stripes: Primary - Secondary - Primary - Secondary
    return Brush.linearGradient(
        0.0f to primaryColor,
        0.28f to primaryColor,
        0.28f to secondaryColor,
        0.52f to secondaryColor,
        0.52f to primaryColor,
        0.78f to primaryColor,
        0.78f to secondaryColor,
        1.0f to secondaryColor
    )
}

fun parseWireColorToComposeColor(colorHex: String?, wireColorName: String = ""): Color {
    val (primary, _) = resolveWireColors(colorHex ?: "#3B82F6", wireColorName)
    return primary
}

@Composable
fun WireColorBadge(
    wireColor: String = "",
    colorHex: String = "#3B82F6",
    secondaryColorHex: String? = null,
    secondaryColorName: String? = null,
    size: androidx.compose.ui.unit.Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val (primaryColor, secondaryColor) = resolveWireColors(
        colorHex = colorHex,
        wireColorName = wireColor,
        secondaryColorHex = secondaryColorHex,
        secondaryColorName = secondaryColorName
    )
    val wireBrush = getWireBrush(primaryColor, secondaryColor)

    if (wireColor.isBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(wireBrush)
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        )
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(wireBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = wireColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
