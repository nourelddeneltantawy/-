package com.example.util

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AppThemeMode(val title: String, val subtitle: String) {
    SYSTEM("تلقائي (حسب النظام)", "يتبع وضع الهاتف الفاتح أو الداكن"),
    DARK("الوضع الداكن", "مظهر داكن مريح للعين"),
    LIGHT("الوضع الفاتح", "مظهر فاتح واضح ومشرق")
}

enum class AppUiStyle(val id: String, val title: String, val subtitle: String) {
    STANDARD("standard", "القياسي (Material 3)", "تصميم عصري قياسي متوازن"),
    CLAY("clay", "كلايمورفيك ناعم (Claymorphism)", "مجسم ناعم ببطاقات بارزة وأزرار حبة دواء"),
    SOFT_NEUMORPHISM("soft_neumorphism", "ناعم مجسم (Soft Neumorphism)", "أبعاد ثلاثية ناعمة بظلال مجسمة وأزرار حبة دواء"),
    LIQUID_GLASS("liquid_glass", "الزجاج السائل (Liquid Glass)", "ألواح زجاجية شفافة بحواف مضيئة وأزرار كبسولية")
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
    private const val TAG = "ThemeManager"
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_UI_STYLE = "ui_style"
    private const val KEY_ADMIN_DEFAULT_UI_STYLE = "admin_default_ui_style"
    private const val KEY_USER_CHOSE_CUSTOM_STYLE = "user_chose_custom_ui_style"
    private const val KEY_COLOR = "color_theme"
    private const val DOC_COLLECTION = "app_settings"
    private const val DOC_ID = "ui_style_config"

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _adminDefaultUiStyle = MutableStateFlow(AppUiStyle.STANDARD)
    val adminDefaultUiStyle: StateFlow<AppUiStyle> = _adminDefaultUiStyle.asStateFlow()

    private val _uiStyle = MutableStateFlow(AppUiStyle.STANDARD)
    val uiStyle: StateFlow<AppUiStyle> = _uiStyle.asStateFlow()

    private val _colorTheme = MutableStateFlow(AppColorTheme.BLUE)
    val colorTheme: StateFlow<AppColorTheme> = _colorTheme.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    private fun parseUiStyle(raw: String?): AppUiStyle? {
        if (raw.isNullOrBlank()) return null
        return AppUiStyle.entries.firstOrNull {
            it.name.equals(raw.trim(), ignoreCase = true) ||
                it.id.equals(raw.trim(), ignoreCase = true)
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val savedAdminDefault = parseUiStyle(prefs.getString(KEY_ADMIN_DEFAULT_UI_STYLE, null)) ?: AppUiStyle.STANDARD
        _adminDefaultUiStyle.value = savedAdminDefault

        val savedUiStyle = prefs.getString(KEY_UI_STYLE, null)
        val userChoseCustom = prefs.getBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, false)
        val savedColor = prefs.getString(KEY_COLOR, AppColorTheme.BLUE.id) ?: AppColorTheme.BLUE.id

        // Seamless migration if user previously selected AppThemeMode.CLAY
        if (savedMode == "CLAY") {
            _themeMode.value = AppThemeMode.LIGHT
            _uiStyle.value = AppUiStyle.CLAY
            prefs.edit()
                .putString(KEY_THEME, AppThemeMode.LIGHT.name)
                .putString(KEY_UI_STYLE, AppUiStyle.CLAY.name)
                .putBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, true)
                .apply()
        } else {
            _themeMode.value = try {
                AppThemeMode.valueOf(savedMode)
            } catch (_: Exception) {
                AppThemeMode.SYSTEM
            }
            _uiStyle.value = if (userChoseCustom && savedUiStyle != null) {
                parseUiStyle(savedUiStyle) ?: savedAdminDefault
            } else {
                savedAdminDefault
            }
        }

        _colorTheme.value = AppColorTheme.entries.find { it.id == savedColor } ?: AppColorTheme.BLUE

        startRemoteDefaultStyleSync(appContext)
    }

    private fun startRemoteDefaultStyleSync(appContext: Context) {
        if (listenerRegistration != null) return
        try {
            val db = FirebaseInitializer.getFirestore(appContext)
            listenerRegistration = db.collection(DOC_COLLECTION).document(DOC_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "UI style listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val remoteStyle = parseUiStyle(snapshot.getString("defaultUiStyle"))
                        if (remoteStyle != null) {
                            applyRemoteAdminDefault(appContext, remoteStyle)
                        }
                    }
                }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snap = try {
                        db.collection(DOC_COLLECTION).document(DOC_ID).get(Source.SERVER).await()
                    } catch (_: Throwable) {
                        db.collection(DOC_COLLECTION).document(DOC_ID).get().await()
                    }
                    if (snap.exists()) {
                        val remoteStyle = parseUiStyle(snap.getString("defaultUiStyle"))
                        if (remoteStyle != null) {
                            applyRemoteAdminDefault(appContext, remoteStyle)
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start UI style Firestore sync: ${e.message}")
        }
    }

    private fun applyRemoteAdminDefault(appContext: Context, remoteDefault: AppUiStyle) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevDefault = _adminDefaultUiStyle.value
        _adminDefaultUiStyle.value = remoteDefault
        prefs.edit().putString(KEY_ADMIN_DEFAULT_UI_STYLE, remoteDefault.name).apply()

        val userChoseCustom = prefs.getBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, false)
        if (!userChoseCustom || prevDefault != remoteDefault && !prefs.contains(KEY_UI_STYLE)) {
            _uiStyle.value = remoteDefault
            prefs.edit().putString(KEY_UI_STYLE, remoteDefault.name).apply()
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }

    fun setUiStyle(context: Context, style: AppUiStyle) {
        _uiStyle.value = style
        val isFollowingDefault = (style == _adminDefaultUiStyle.value)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UI_STYLE, style.name)
            .putBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, !isFollowingDefault)
            .apply()
    }

    fun resetToAdminDefaultStyle(context: Context) {
        val def = _adminDefaultUiStyle.value
        _uiStyle.value = def
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UI_STYLE, def.name)
            .putBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, false)
            .apply()
    }

    /**
     * Admin-only: designate the primary default UI style for the entire app (synced via Firestore).
     * Other styles remain available as optional user add-ons in ThemeSettingsDialog.
     */
    fun setAdminDefaultUiStyle(
        context: Context,
        style: AppUiStyle,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        _adminDefaultUiStyle.value = style
        _uiStyle.value = style
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADMIN_DEFAULT_UI_STYLE, style.name)
            .putString(KEY_UI_STYLE, style.name)
            .putBoolean(KEY_USER_CHOSE_CUSTOM_STYLE, false)
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val payload = mapOf(
                    "defaultUiStyle" to style.name,
                    "defaultUiStyleId" to style.id,
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection(DOC_COLLECTION).document(DOC_ID)
                    .set(payload, SetOptions.merge())
                    .addOnCompleteListener { task ->
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (_: Throwable) {
                onComplete?.invoke(false)
            }
        }
    }

    fun setColorTheme(context: Context, colorTheme: AppColorTheme) {
        _colorTheme.value = colorTheme
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR, colorTheme.id)
            .apply()
    }
}

