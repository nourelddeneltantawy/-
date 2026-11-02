package com.example.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String, val subtitle: String) {
    SYSTEM("تلقائي (حسب النظام)", "يتبع إعدادات ومظهر الهاتف تلقائياً 📱"),
    DARK("الوضع الداكن (Dark)", "خلفيات كحلية داكنة مريحة للعين 🌙"),
    LIGHT("الوضع الفاتح (Light)", "خلفيات بيضاء وسماوية ناصعة ☀️")
}

enum class AppColorTheme(
    val id: String,
    val title: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val hexCode: String
) {
    BLUE(
        id = "blue",
        title = "أزرق سماوي كلاسيكي",
        primary = Color(0xFF0284C7),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF0369A1),
        secondary = Color(0xFF38BDF8),
        hexCode = "#0284C7"
    ),
    CYAN(
        id = "cyan",
        title = "تركواز بحري عصري",
        primary = Color(0xFF06B6D4),
        onPrimary = Color(0xFF0F172A),
        primaryContainer = Color(0xFF0E7490),
        secondary = Color(0xFF22D3EE),
        hexCode = "#06B6D4"
    ),
    TEAL(
        id = "teal",
        title = "أزرق بترولي متطور",
        primary = Color(0xFF0F766E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF115E59),
        secondary = Color(0xFF2DD4BF),
        hexCode = "#0F766E"
    ),
    EMERALD(
        id = "emerald",
        title = "أخضر زمردي إلكتروني",
        primary = Color(0xFF10B981),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF047857),
        secondary = Color(0xFF34D399),
        hexCode = "#10B981"
    ),
    PURPLE(
        id = "purple",
        title = "بنفسجي تكنولوجي رويال",
        primary = Color(0xFF8B5CF6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF6D28D9),
        secondary = Color(0xFFA78BFA),
        hexCode = "#8B5CF6"
    ),
    AMBER(
        id = "amber",
        title = "ذهبي كهرماني مميز",
        primary = Color(0xFFD97706),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB45309),
        secondary = Color(0xFFFBBF24),
        hexCode = "#D97706"
    ),
    ROSE(
        id = "rose",
        title = "أحمر قرمزي ناري",
        primary = Color(0xFFE11D48),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFBE123C),
        secondary = Color(0xFFFB7185),
        hexCode = "#E11D48"
    )
}

object ThemeManager {
    private const val PREFS_NAME = "albaraa_theme_prefs"
    private const val KEY_THEME = "app_theme_mode"
    private const val KEY_COLOR = "app_color_theme"

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(AppColorTheme.BLUE)
    val colorTheme: StateFlow<AppColorTheme> = _colorTheme.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedMode = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        _themeMode.value = try {
            AppThemeMode.valueOf(savedMode)
        } catch (_: Throwable) {
            AppThemeMode.SYSTEM
        }

        val savedColor = prefs.getString(KEY_COLOR, AppColorTheme.BLUE.name) ?: AppColorTheme.BLUE.name
        _colorTheme.value = try {
            AppColorTheme.valueOf(savedColor)
        } catch (_: Throwable) {
            AppColorTheme.BLUE
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setColorTheme(context: Context, color: AppColorTheme) {
        _colorTheme.value = color
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR, color.name).apply()
    }
}
