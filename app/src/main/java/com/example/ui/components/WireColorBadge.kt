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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun parseWireColorToComposeColor(colorHex: String, wireColorName: String): Color {
    val cleanHex = colorHex.trim()
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
                else -> 0xFF3B82F6
            }
            return Color(colorLong)
        } catch (_: Exception) {}
    }

    return when {
        wireColorName.contains("أحمر") || wireColorName.contains("Red", true) -> Color(0xFFEF4444)
        wireColorName.contains("أزرق") || wireColorName.contains("Blue", true) -> Color(0xFF3B82F6)
        wireColorName.contains("أصفر") || wireColorName.contains("Yellow", true) -> Color(0xFFEAB308)
        wireColorName.contains("أخضر") || wireColorName.contains("Green", true) -> Color(0xFF22C55E)
        wireColorName.contains("برتقالي") || wireColorName.contains("Orange", true) -> Color(0xFFF97316)
        wireColorName.contains("أبيض") || wireColorName.contains("White", true) -> Color(0xFFE2E8F0)
        wireColorName.contains("أسود") || wireColorName.contains("Black", true) -> Color(0xFF1F2937)
        wireColorName.contains("رمادي") || wireColorName.contains("Gray", true) -> Color(0xFF6B7280)
        wireColorName.contains("بني") || wireColorName.contains("Brown", true) -> Color(0xFF854D0E)
        wireColorName.contains("بنفسجي") || wireColorName.contains("Purple", true) -> Color(0xFFA855F7)
        else -> Color(0xFF3B82F6)
    }
}

@Composable
fun WireColorBadge(
    wireColor: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = parseWireColorToComposeColor(colorHex, wireColor)

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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
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
