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
        background = DarkNavyBg,
        onBackground = Color(0xFFF8FAFC),
        surface = CardNavy,
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF64748B)
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
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1)
    )

    val colorScheme = if (isDark) dynamicDark else dynamicLight

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
