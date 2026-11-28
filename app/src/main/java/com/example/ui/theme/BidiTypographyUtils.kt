package com.example.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Wraps embedded Latin/numeric/bracket tokens (such as `(USB OTG)`, `(0x40 + 0xB0)`, `78K0`)
 * inside Unicode Left-to-Right Isolate markers (\u2066 ... \u2069) so mixed Arabic RTL sentences
 * never mirror parentheses or scramble version/address ordering.
 */
private val MIXED_LTR_TOKEN_REGEX = Regex(
    """(?:\([^)]*[A-Za-z0-9][^)]*\)|0x[0-9A-Fa-f]+(?:\.\.0x[0-9A-Fa-f]+)?|[A-Za-z][A-Za-z0-9_./+\-:#]*(?:\s+[A-Za-z0-9_./+\-:#]+)*)"""
)

fun String.bidiIsolateMixed(): String {
    if (isEmpty()) return this
    var hasArabic = false
    var hasLatin = false
    for (ch in this) {
        when {
            ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> hasArabic = true
            ch in 'A'..'Z' || ch in 'a'..'z' -> hasLatin = true
        }
        if (hasArabic && hasLatin) break
    }
    if (!hasArabic || !hasLatin) return this
    // Avoid double-isolating strings already containing Unicode FSI/LRI isolates
    if (contains('\u2066') || contains('\u2068')) return this
    return MIXED_LTR_TOKEN_REGEX.replace(this) { match ->
        "\u2066${match.value}\u2069"
    }
}

val MonospaceTabularStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum"
)

/**
 * Single-line smart-fitting text that scales fontSize down gently only if needed
 * to prevent ugly mid-word `...` truncation on narrow phones.
 */
@Composable
fun FittedSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    minFontSize: TextUnit = 9.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    applyBidiIsolate: Boolean = true
) {
    val initialSp = when {
        fontSize != TextUnit.Unspecified -> fontSize.value
        style.fontSize != TextUnit.Unspecified -> style.fontSize.value
        else -> 12f
    }
    var currentSp by remember(text, initialSp) { mutableStateOf(initialSp) }
    val minSp = minFontSize.value.coerceAtMost(initialSp)

    Text(
        text = if (applyBidiIsolate) text.bidiIsolateMixed() else text,
        modifier = modifier,
        color = color,
        fontSize = currentSp.sp,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && currentSp > minSp) {
                currentSp = (currentSp - 0.5f).coerceAtLeast(minSp)
            }
        }
    )
}
