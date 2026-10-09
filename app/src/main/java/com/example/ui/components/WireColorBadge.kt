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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

fun parseWireColorToComposeColor(wireColorName: String, fallbackHex: String? = null): Color {
    val lower = wireColorName.trim().lowercase()
    if (fallbackHex != null && fallbackHex.startsWith("#") && fallbackHex.length in listOf(7, 9)) {
        try {
            val colorLong = java.lang.Long.parseLong(fallbackHex.removePrefix("#"), 16)
            return if (fallbackHex.length == 7) Color(colorLong or 0xFF000000) else Color(colorLong)
        } catch (_: Exception) {}
    }

    return when {
        lower.contains("أحمر") || lower.contains("red") -> WireRed
        lower.contains("أزرق") || lower.contains("blue") -> WireBlue
        lower.contains("أصفر") || lower.contains("yellow") -> WireYellow
        lower.contains("أخضر") || lower.contains("green") -> WireGreen
        lower.contains("برتقالي") || lower.contains("orange") -> WireOrange
        lower.contains("بنفسجي") || lower.contains("purple") -> WirePurple
        lower.contains("بني") || lower.contains("brown") -> WireBrown
        lower.contains("أبيض") || lower.contains("white") -> Color(0xFFF8FAFC)
        lower.contains("أسود") || lower.contains("black") -> WireBlack
        lower.contains("رمادي") || lower.contains("gray") || lower.contains("grey") -> WireGray
        else -> TechElectricBlue
    }
}

fun parseSecondaryColor(wireColorName: String): Color? {
    val lower = wireColorName.trim().lowercase()
    if (!lower.contains("+") && !lower.contains("/")) return null

    val parts = lower.split("+", "/")
    if (parts.size >= 2) {
        val second = parts[1].trim()
        return when {
            second.contains("بنفسجي") || second.contains("purple") -> WirePurple
            second.contains("أبيض") || second.contains("white") -> Color(0xFFF8FAFC)
            second.contains("أسود") || second.contains("black") -> WireBlack
            second.contains("أخضر") || second.contains("green") -> WireGreen
            second.contains("أزرق") || second.contains("blue") -> WireBlue
            second.contains("أحمر") || second.contains("red") -> WireRed
            second.contains("أصفر") || second.contains("yellow") -> WireYellow
            else -> null
        }
    }
    return null
}

@Composable
fun WireColorBadge(
    wireColor: String,
    modifier: Modifier = Modifier,
    fallbackHex: String? = null
) {
    val primaryColor = parseWireColorToComposeColor(wireColor, fallbackHex)
    val secondaryColor = parseSecondaryColor(wireColor)

    Row(
        modifier = modifier
            .testTag("wire_color_badge")
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dual color wire indicator if compound color, e.g. "Orange + Purple"
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .size(16.dp)
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
        ) {
            if (secondaryColor != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(16.dp)
                        .background(primaryColor)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(16.dp)
                        .background(secondaryColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(primaryColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = wireColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
