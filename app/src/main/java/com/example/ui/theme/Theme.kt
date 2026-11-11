package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.util.AppThemeMode
import com.example.util.ThemeManager

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val colorTheme by ThemeManager.colorTheme.collectAsState()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> darkTheme
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
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
        background = ClayLightBg,
        onBackground = Color(0xFF0F172A),
        surface = ClayLightSurface,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = ClayLightSurfaceVariant,
        onSurfaceVariant = Color(0xFF475569),
        outline = ClayLightOutline,
        outlineVariant = Color(0xFFD6DFEA)
    )

    val colorScheme = if (isDark) dynamicDark else dynamicLight

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
