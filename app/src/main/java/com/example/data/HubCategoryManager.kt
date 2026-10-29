package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.HubCategoryCustomization
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

object HubCategoryManager {
    private const val TAG = "HubCategoryManager"
    private const val PREFS_NAME = "albaraa_hub_categories_prefs"
    private const val KEY_CATEGORIES_JSON = "categories_json"

    private val _customizations = MutableStateFlow<Map<String, HubCategoryCustomization>>(emptyMap())
    val customizations: StateFlow<Map<String, HubCategoryCustomization>> = _customizations.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context

        // 1. Load from local cache for instant UI rendering
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CATEGORIES_JSON, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(jsonStr)
                val map = mutableMapOf<String, HubCategoryCustomization>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = json.getJSONObject(key)
                    map[key] = HubCategoryCustomization(
                        id = key,
                        customTitle = obj.optString("customTitle", ""),
                        customSubtitle = obj.optString("customSubtitle", ""),
                        customImageUrl = obj.optString("customImageUrl", ""),
                        customIconName = obj.optString("customIconName", "")
                    )
                }
                _customizations.value = map
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse local categories cache: ${e.message}")
            }
        }

        // 2. Fetch remote directly
        fetchRemote(appContext)

        // 3. Attach real-time snapshot listener on the designated Firestore instance
        if (listenerRegistration == null) {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                listenerRegistration = db.collection("app_settings").document("hub_categories")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "SnapshotListener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val data = snapshot.data
                            if (data != null) {
                                val map = parseFirestoreData(data)
                                _customizations.value = map
                                saveToPrefs(appContext, map)
                                Log.i(TAG, "Hub categories synced in real-time (${map.size} items)")
                            }
                        }
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to attach real-time hub categories listener", e)
            }
        }
        isInitialized = true
    }

    fun fetchRemote(context: Context) {
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val snapshot = try {
                    db.collection("app_settings").document("hub_categories").get(Source.SERVER).await()
                } catch (_: Throwable) {
                    db.collection("app_settings").document("hub_categories").get().await()
                }

                if (snapshot.exists()) {
                    val data = snapshot.data
                    if (data != null) {
                        val map = parseFirestoreData(data)
                        _customizations.value = map
                        saveToPrefs(appContext, map)
                        Log.i(TAG, "Fetched remote categories: ${map.size} items")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to fetch remote hub categories: ${e.message}")
            }
        }
    }

    fun updateCategory(
        context: Context,
        id: String,
        customTitle: String,
        customSubtitle: String,
        customImageUrl: String,
        customIconName: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _customizations.value.toMutableMap()
        val updatedItem = HubCategoryCustomization(
            id = id,
            customTitle = customTitle.trim(),
            customSubtitle = customSubtitle.trim(),
            customImageUrl = customImageUrl.trim(),
            customIconName = customIconName.trim()
        )
        current[id] = updatedItem
        _customizations.value = current
        saveToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val remotePayload = mutableMapOf<String, Any>()
                current.forEach { (catId, cust) ->
                    remotePayload[catId] = cust.toMap()
                }

                db.collection("app_settings").document("hub_categories")
                    .set(remotePayload)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Updated hub category [$id] in Firestore successfully")
                        } else {
                            Log.e(TAG, "Failed to update hub category in Firestore: ${task.exception?.message}")
                        }
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception updating hub category in Firestore", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun resetCategory(
        context: Context,
        id: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        updateCategory(context, id, "", "", "", "", onComplete)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreData(data: Map<String, Any>): Map<String, HubCategoryCustomization> {
        val result = mutableMapOf<String, HubCategoryCustomization>()
        for ((key, value) in data) {
            if (value is Map<*, *>) {
                val strMap = value as Map<String, Any?>
                result[key] = HubCategoryCustomization.fromMap(key, strMap)
            }
        }
        return result
    }

    private fun saveToPrefs(context: Context, map: Map<String, HubCategoryCustomization>) {
        try {
            val json = JSONObject()
            map.forEach { (id, item) ->
                val obj = JSONObject()
                obj.put("customTitle", item.customTitle)
                obj.put("customSubtitle", item.customSubtitle)
                obj.put("customImageUrl", item.customImageUrl)
                obj.put("customIconName", item.customIconName)
                json.put(id, obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CATEGORIES_JSON, json.toString()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write hub categories to prefs", e)
        }
    }
}
