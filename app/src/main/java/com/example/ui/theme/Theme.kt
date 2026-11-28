package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.util.AppThemeMode
import com.example.util.AppUiStyle
import com.example.util.ThemeManager

val LocalIsClayTheme = compositionLocalOf { false }
val LocalIsSoftNeumorphismTheme = compositionLocalOf { false }
val LocalIsLiquidGlassTheme = compositionLocalOf { false }
val LocalUiStyle = compositionLocalOf { AppUiStyle.STANDARD }
val LocalIsDarkTheme = compositionLocalOf { false }
val LocalAccentColor = compositionLocalOf { Color(0xFF0284C7) }
val LocalSecondaryAccentColor = compositionLocalOf { Color(0xFF0369A1) }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val uiStyle by ThemeManager.uiStyle.collectAsState()
    val colorTheme by ThemeManager.colorTheme.collectAsState()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> darkTheme
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val isClay = uiStyle == AppUiStyle.CLAY
    val isSoftNeumorphism = uiStyle == AppUiStyle.SOFT_NEUMORPHISM
    val isLiquidGlass = uiStyle == AppUiStyle.LIQUID_GLASS

    val colorScheme = when {
        isLiquidGlass && isDark -> darkColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.24f),
            onPrimaryContainer = colorTheme.tertiary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.24f),
            onSecondaryContainer = colorTheme.tertiary,
            tertiary = colorTheme.tertiary,
            background = GlassTokens.DarkBg,
            onBackground = GlassTokens.DarkTextPrimary,
            surface = Color(0xFF161A22),
            onSurface = GlassTokens.DarkTextPrimary,
            surfaceVariant = Color(0xFF1F2530),
            onSurfaceVariant = GlassTokens.DarkTextSecondary,
            outline = Color(0xFF333B4A),
            outlineVariant = Color(0xFF262D3B)
        )
        isLiquidGlass && !isDark -> lightColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.16f),
            onPrimaryContainer = colorTheme.primary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.16f),
            onSecondaryContainer = colorTheme.secondary,
            tertiary = colorTheme.tertiary,
            background = GlassTokens.LightBg,
            onBackground = GlassTokens.LightTextPrimary,
            surface = Color(0xFFF6F8FB),
            onSurface = GlassTokens.LightTextPrimary,
            surfaceVariant = Color(0xFFDCE2EC),
            onSurfaceVariant = GlassTokens.LightTextSecondary,
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFCBD5E1)
        )
        isSoftNeumorphism && isDark -> darkColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.22f),
            onPrimaryContainer = colorTheme.tertiary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.22f),
            onSecondaryContainer = colorTheme.tertiary,
            tertiary = colorTheme.tertiary,
            background = NeuTokens.DarkBg,
            onBackground = NeuTokens.DarkTextPrimary,
            surface = NeuTokens.DarkSurface,
            onSurface = NeuTokens.DarkTextPrimary,
            surfaceVariant = NeuTokens.DarkWell,
            onSurfaceVariant = NeuTokens.DarkTextSecondary,
            outline = Color(0xFF2F343D),
            outlineVariant = Color(0xFF272B33)
        )
        isSoftNeumorphism && !isDark -> lightColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.14f),
            onPrimaryContainer = colorTheme.primary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = colorTheme.secondary,
            tertiary = colorTheme.tertiary,
            background = NeuTokens.LightBg,
            onBackground = NeuTokens.LightTextPrimary,
            surface = NeuTokens.LightSurface,
            onSurface = NeuTokens.LightTextPrimary,
            surfaceVariant = NeuTokens.LightWell,
            onSurfaceVariant = NeuTokens.LightTextSecondary,
            outline = Color(0xFFBFC8D4),
            outlineVariant = Color(0xFFD5DCE6)
        )
        isClay && !isDark -> lightColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.14f),
            onPrimaryContainer = colorTheme.primary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.16f),
            onSecondaryContainer = colorTheme.secondary,
            tertiary = colorTheme.tertiary,
            background = Color(0xFFEFF2F6),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFD6DFEA)
        )
        isDark -> darkColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.25f),
            onPrimaryContainer = colorTheme.tertiary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.25f),
            onSecondaryContainer = colorTheme.tertiary,
            tertiary = colorTheme.tertiary,
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF334155)
        )
        else -> lightColorScheme(
            primary = colorTheme.primary,
            onPrimary = Color.White,
            primaryContainer = colorTheme.primary.copy(alpha = 0.15f),
            onPrimaryContainer = colorTheme.primary,
            secondary = colorTheme.secondary,
            onSecondary = Color.White,
            secondaryContainer = colorTheme.secondary.copy(alpha = 0.15f),
            onSecondaryContainer = colorTheme.secondary,
            tertiary = colorTheme.tertiary,
            background = Color(0xFFF1F5F9),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
    }

    CompositionLocalProvider(
        LocalIsClayTheme provides isClay,
        LocalIsSoftNeumorphismTheme provides isSoftNeumorphism,
        LocalIsLiquidGlassTheme provides isLiquidGlass,
        LocalUiStyle provides uiStyle,
        LocalIsDarkTheme provides isDark,
        LocalAccentColor provides colorTheme.primary,
        LocalSecondaryAccentColor provides colorTheme.secondary
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
