package com.example.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String, val subtitle: String) {
    SYSTEM("تلقائي (حسب النظام)", "يتبع وضع الهاتف الفاتح أو الداكن"),
    DARK("الوضع الداكن", "مظهر داكن مريح للعين"),
    LIGHT("الوضع الفاتح", "مظهر فاتح واضح ومشرق")
}

enum class AppUiStyle(val id: String, val title: String, val subtitle: String) {
    STANDARD("standard", "القياسي (Material 3)", "تصميم عصري قياسي متوازن"),
    CLAY("clay", "كلايمورفيك ناعم (Claymorphism)", "مجسم ناعم ببطاقات بارزة وأزرار حبة دواء"),
    SOFT_NEUMORPHISM("soft_neumorphism", "ناعم مجسم (Soft Neumorphism)", "أبعاد ثلاثية ناعمة بظلال مجسمة وأزرار حبة دواء")
}

enum class AppColorTheme(
    val id: String,
    val title: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    BLUE(
        id = "blue",
        title = "الأزرق الهندسي",
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF0369A1),
        tertiary = Color(0xFF38BDF8)
    ),
    CLAY_AZURE(
        id = "clay_azure",
        title = "أزرق سماوي هادئ",
        primary = Color(0xFF2563EB),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFF60A5FA)
    ),
    EMERALD(
        id = "emerald",
        title = "الأخضر الزمردي",
        primary = Color(0xFF059669),
        secondary = Color(0xFF10B981),
        tertiary = Color(0xFF34D399)
    ),
    AMBER(
        id = "amber",
        title = "الذهبي الكهرماني",
        primary = Color(0xFFD97706),
        secondary = Color(0xFFF59E0B),
        tertiary = Color(0xFFFBBF24)
    ),
    PURPLE(
        id = "purple",
        title = "البنفسجي الملكي",
        primary = Color(0xFF7C3AED),
        secondary = Color(0xFF8B5CF6),
        tertiary = Color(0xFFA78BFA)
    ),
    CRIMSON(
        id = "crimson",
        title = "الأحمر الهندسي",
        primary = Color(0xFFDC2626),
        secondary = Color(0xFFEF4444),
        tertiary = Color(0xFFF87171)
    ),
    TEAL(
        id = "teal",
        title = "الأزرق الفيروزي",
        primary = Color(0xFF0D9488),
        secondary = Color(0xFF14B8A6),
        tertiary = Color(0xFF2DD4BF)
    )
}

object ThemeManager {
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_UI_STYLE = "ui_style"
    private const val KEY_COLOR = "color_theme"

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _uiStyle = MutableStateFlow(AppUiStyle.STANDARD)
    val uiStyle: StateFlow<AppUiStyle> = _uiStyle.asStateFlow()

    private val _colorTheme = MutableStateFlow(AppColorTheme.BLUE)
    val colorTheme: StateFlow<AppColorTheme> = _colorTheme.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val savedUiStyle = prefs.getString(KEY_UI_STYLE, null)
        val savedColor = prefs.getString(KEY_COLOR, AppColorTheme.BLUE.id) ?: AppColorTheme.BLUE.id

        // Seamless migration if user previously selected AppThemeMode.CLAY
        if (savedMode == "CLAY") {
            _themeMode.value = AppThemeMode.LIGHT
            _uiStyle.value = AppUiStyle.CLAY
            prefs.edit()
                .putString(KEY_THEME, AppThemeMode.LIGHT.name)
                .putString(KEY_UI_STYLE, AppUiStyle.CLAY.name)
                .apply()
        } else {
            _themeMode.value = try {
                AppThemeMode.valueOf(savedMode)
            } catch (_: Exception) {
                AppThemeMode.SYSTEM
            }
            _uiStyle.value = if (savedUiStyle != null) {
                try {
                    AppUiStyle.valueOf(savedUiStyle)
                } catch (_: Exception) {
                    AppUiStyle.STANDARD
                }
            } else {
                AppUiStyle.STANDARD
            }
        }

        _colorTheme.value = AppColorTheme.entries.find { it.id == savedColor } ?: AppColorTheme.BLUE
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }

    fun setUiStyle(context: Context, style: AppUiStyle) {
        _uiStyle.value = style
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UI_STYLE, style.name)
            .apply()
    }

    fun setColorTheme(context: Context, colorTheme: AppColorTheme) {
        _colorTheme.value = colorTheme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR, colorTheme.id)
            .apply()
    }
}
