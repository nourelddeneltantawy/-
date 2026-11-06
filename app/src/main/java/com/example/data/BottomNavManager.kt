package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.BottomNavCustomization
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object BottomNavManager {
    private const val TAG = "BottomNavManager"
    private const val PREFS_NAME = "albaraa_bottom_nav_prefs"
    private const val KEY_CONFIG_JSON = "bottom_nav_json"

    const val TAB_HOME = "home"
    const val TAB_DASHBOARD = "dashboard"
    const val TAB_MARKETPLACE = "marketplace"

    private val defaultTabs = mapOf(
        TAB_HOME to BottomNavCustomization(id = TAB_HOME, title = "شاشة البدء", isHidden = false, isLocked = false),
        TAB_DASHBOARD to BottomNavCustomization(id = TAB_DASHBOARD, title = "الأقسام", isHidden = false, isLocked = false),
        TAB_MARKETPLACE to BottomNavCustomization(id = TAB_MARKETPLACE, title = "سوق الكروت والصيانة (البيع والشراء)", isHidden = false, isLocked = false)
    )

    private val _tabs = MutableStateFlow<Map<String, BottomNavCustomization>>(defaultTabs)
    val tabs: StateFlow<Map<String, BottomNavCustomization>> = _tabs.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context

        // 1. Load from local cache for instant UI rendering
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(jsonStr)
                val map = defaultTabs.toMutableMap()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = json.getJSONObject(key)
                    map[key] = BottomNavCustomization(
                        id = key,
                        title = obj.optString("title", defaultTabs[key]?.title ?: key),
                        isHidden = obj.optBoolean("isHidden", false),
                        isLocked = obj.optBoolean("isLocked", false)
                    )
                }
                _tabs.value = map
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse local bottom nav cache: ${e.message}")
            }
        }

        // 2. Fetch remote directly
        fetchRemote(appContext)

        // 3. Attach real-time snapshot listener on Firestore
        if (listenerRegistration == null) {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                listenerRegistration = db.collection("app_settings").document("bottom_nav")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "SnapshotListener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val data = snapshot.data
                            if (data != null) {
                                val map = parseFirestoreData(data)
                                _tabs.value = map
                                saveToPrefs(appContext, map)
                                Log.i(TAG, "Bottom nav synced in real-time")
                            }
                        }
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to attach real-time bottom nav listener", e)
            }
        }
        isInitialized = true
    }

    fun isTabVisible(tabId: String, isAdmin: Boolean): Boolean {
        if (isAdmin) return true // Admins always see tabs so they can access and manage them
        val config = _tabs.value[tabId] ?: return true
        return !config.isHidden
    }

    fun isTabLocked(tabId: String, isAdmin: Boolean): Boolean {
        if (isAdmin) return false
        val config = _tabs.value[tabId] ?: return false
        return config.isLocked
    }

    fun isTabHiddenForUsers(tabId: String): Boolean {
        return _tabs.value[tabId]?.isHidden ?: false
    }

    fun fetchRemote(context: Context) {
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val snapshot = try {
                    db.collection("app_settings").document("bottom_nav").get(Source.SERVER).await()
                } catch (_: Throwable) {
                    db.collection("app_settings").document("bottom_nav").get().await()
                }

                if (snapshot.exists()) {
                    val data = snapshot.data
                    if (data != null) {
                        val map = parseFirestoreData(data)
                        _tabs.value = map
                        saveToPrefs(appContext, map)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to fetch remote bottom nav: ${e.message}")
            }
        }
    }

    fun toggleTabVisibility(
        context: Context,
        tabId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _tabs.value.toMutableMap()
        val existing = current[tabId] ?: defaultTabs[tabId] ?: BottomNavCustomization(id = tabId)
        val updated = existing.copy(isHidden = !existing.isHidden)
        current[tabId] = updated
        _tabs.value = current
        saveToPrefs(appContext, current)
        syncToFirestore(appContext, current, onComplete)
    }

    fun setTabVisibility(
        context: Context,
        tabId: String,
        isHidden: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _tabs.value.toMutableMap()
        val existing = current[tabId] ?: defaultTabs[tabId] ?: BottomNavCustomization(id = tabId)
        val updated = existing.copy(isHidden = isHidden)
        current[tabId] = updated
        _tabs.value = current
        saveToPrefs(appContext, current)
        syncToFirestore(appContext, current, onComplete)
    }

    fun toggleTabLock(
        context: Context,
        tabId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _tabs.value.toMutableMap()
        val existing = current[tabId] ?: defaultTabs[tabId] ?: BottomNavCustomization(id = tabId)
        val updated = existing.copy(isLocked = !existing.isLocked)
        current[tabId] = updated
        _tabs.value = current
        saveToPrefs(appContext, current)
        syncToFirestore(appContext, current, onComplete)
    }

    private fun syncToFirestore(
        context: Context,
        tabsMap: Map<String, BottomNavCustomization>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val remotePayload = mutableMapOf<String, Any>()
                tabsMap.forEach { (tabId, item) ->
                    remotePayload[tabId] = item.toMap()
                }

                db.collection("app_settings").document("bottom_nav")
                    .set(remotePayload)
                    .addOnCompleteListener { task ->
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception syncing bottom nav to Firestore", e)
                onComplete?.invoke(false)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreData(data: Map<String, Any>): Map<String, BottomNavCustomization> {
        val result = defaultTabs.toMutableMap()
        for ((key, value) in data) {
            if (value is Map<*, *>) {
                val strMap = value as Map<String, Any?>
                result[key] = BottomNavCustomization.fromMap(key, strMap)
            }
        }
        return result
    }

    private fun saveToPrefs(context: Context, map: Map<String, BottomNavCustomization>) {
        try {
            val json = JSONObject()
            map.forEach { (id, item) ->
                val obj = JSONObject()
                obj.put("title", item.title)
                obj.put("isHidden", item.isHidden)
                obj.put("isLocked", item.isLocked)
                json.put(id, obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CONFIG_JSON, json.toString()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write bottom nav to prefs", e)
        }
    }
}
