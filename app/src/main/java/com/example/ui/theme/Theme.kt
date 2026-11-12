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
import com.example.util.ThemeManager

val LocalIsClayTheme = compositionLocalOf { false }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val colorTheme by ThemeManager.colorTheme.collectAsState()

    val isClay = themeMode == AppThemeMode.CLAY
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> darkTheme
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.CLAY -> false
    }

    val dynamicDark = darkColorScheme(
        primary = colorTheme.primary,
        onPrimary = colorTheme.onPrimary,
        primaryContainer = colorTheme.primaryContainer,
        onPrimaryContainer = Color.White,
        secondary = colorTheme.secondary,
        onSecondary = Color(0xFF0F172A),
        background = ClayDarkBg,
        onBackground = Color(0xFFF8FAFC),
        surface = ClayDarkSurface,
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = ClayDarkSurfaceVariant,
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = ClayDarkOutline,
        outlineVariant = Color(0xFF243046)
    )

    val dynamicLight = lightColorScheme(
        primary = colorTheme.primary,
        onPrimary = Color.White,
        primaryContainer = colorTheme.primary.copy(alpha = 0.15f),
        onPrimaryContainer = colorTheme.primaryContainer,
        secondary = colorTheme.secondary,
        onSecondary = Color.White,
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0)
    )

    val clayColorScheme = lightColorScheme(
        primary = colorTheme.primary,
        onPrimary = Color.White,
        primaryContainer = colorTheme.primary.copy(alpha = 0.15f),
        onPrimaryContainer = colorTheme.primary,
        secondary = colorTheme.secondary,
        onSecondary = Color.White,
        background = Color(0xFFEFF2F6), // Soft clay background
        onBackground = Color(0xFF1E293B),
        surface = Color(0xFFFFFFFF), // Pure crisp raised white clay cards
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE4E9F0), // Recessed indented clay tray
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFD6DFEA),
        outlineVariant = Color(0xFFE2E8F0)
    )

    val colorScheme = when {
        isClay -> clayColorScheme
        isDark -> dynamicDark
        else -> dynamicLight
    }

    CompositionLocalProvider(LocalIsClayTheme provides isClay) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
