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
    private const val KEY_CATEGORIES_ORDER = "categories_order"

    private val _customizations = MutableStateFlow<Map<String, HubCategoryCustomization>>(emptyMap())
    val customizations: StateFlow<Map<String, HubCategoryCustomization>> = _customizations.asStateFlow()

    private val _categoryOrder = MutableStateFlow<List<String>>(emptyList())
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context

        // 1. Load from local cache for instant UI rendering
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CATEGORIES_JSON, null)
        val orderStr = prefs.getString(KEY_CATEGORIES_ORDER, null)
        if (!orderStr.isNullOrBlank()) {
            _categoryOrder.value = orderStr.split(",").filter { it.isNotBlank() }
        }

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
                        customIconName = obj.optString("customIconName", ""),
                        isLocked = obj.optBoolean("isLocked", false),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                }
                _customizations.value = map
                if (_categoryOrder.value.isEmpty()) {
                    val sorted = map.values.sortedBy { it.orderIndex }.map { it.id }
                    if (sorted.isNotEmpty()) _categoryOrder.value = sorted
                }
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
                                val order = parseFirestoreOrder(data, map)
                                if (order.isNotEmpty()) {
                                    _categoryOrder.value = order
                                }
                                saveToPrefs(appContext, map, _categoryOrder.value)
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
                        val order = parseFirestoreOrder(data, map)
                        if (order.isNotEmpty()) {
                            _categoryOrder.value = order
                        }
                        saveToPrefs(appContext, map, _categoryOrder.value)
                        Log.i(TAG, "Fetched remote categories: ${map.size} items, order: ${_categoryOrder.value.size}")
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
        val existing = current[id] ?: HubCategoryCustomization(id = id)
        val updatedItem = existing.copy(
            customTitle = customTitle.trim(),
            customSubtitle = customSubtitle.trim(),
            customImageUrl = customImageUrl.trim(),
            customIconName = customIconName.trim()
        )
        current[id] = updatedItem
        _customizations.value = current
        saveToPrefs(appContext, current, _categoryOrder.value)

        syncToFirestore(appContext, current, _categoryOrder.value, onComplete)
    }

    fun reorderCategories(
        context: Context,
        newOrder: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        _categoryOrder.value = newOrder
        val current = _customizations.value.toMutableMap()
        newOrder.forEachIndexed { index, catId ->
            val existing = current[catId] ?: HubCategoryCustomization(id = catId)
            current[catId] = existing.copy(orderIndex = index)
        }
        _customizations.value = current
        saveToPrefs(appContext, current, newOrder)
        syncToFirestore(appContext, current, newOrder, onComplete)
    }

    fun moveCategory(
        context: Context,
        categoryId: String,
        delta: Int,
        allCategories: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentOrder = if (_categoryOrder.value.isNotEmpty()) _categoryOrder.value.toMutableList() else allCategories.toMutableList()
        // Ensure all categories exist in the list
        allCategories.forEach { if (!currentOrder.contains(it)) currentOrder.add(it) }

        val idx = currentOrder.indexOf(categoryId)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, currentOrder.size - 1)
        if (newIdx == idx) return

        currentOrder.removeAt(idx)
        currentOrder.add(newIdx, categoryId)
        reorderCategories(context, currentOrder, onComplete)
    }

    fun toggleCategoryLock(
        context: Context,
        id: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _customizations.value.toMutableMap()
        val existing = current[id] ?: HubCategoryCustomization(id = id)
        val updated = existing.copy(isLocked = !existing.isLocked)
        current[id] = updated
        _customizations.value = current
        saveToPrefs(appContext, current, _categoryOrder.value)

        syncToFirestore(appContext, current, _categoryOrder.value, onComplete)
    }

    private fun syncToFirestore(
        context: Context,
        customizationsMap: Map<String, HubCategoryCustomization>,
        orderList: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val remotePayload = mutableMapOf<String, Any>()
                customizationsMap.forEach { (catId, cust) ->
                    remotePayload[catId] = cust.toMap()
                }
                if (orderList.isNotEmpty()) {
                    remotePayload["order_list"] = orderList
                }

                db.collection("app_settings").document("hub_categories")
                    .set(remotePayload)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Synced hub categories & order to Firestore successfully")
                        } else {
                            Log.e(TAG, "Failed to sync to Firestore: ${task.exception?.message}")
                        }
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception syncing to Firestore", e)
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
            if (key == "order_list") continue
            if (value is Map<*, *>) {
                val strMap = value as Map<String, Any?>
                result[key] = HubCategoryCustomization.fromMap(key, strMap)
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreOrder(data: Map<String, Any>, map: Map<String, HubCategoryCustomization>): List<String> {
        val explicitOrder = data["order_list"] as? List<String>
        if (!explicitOrder.isNullOrEmpty()) {
            return explicitOrder.filter { it.isNotBlank() }
        }
        val byOrderIndex = map.values.sortedBy { it.orderIndex }.map { it.id }
        return byOrderIndex
    }

    private fun saveToPrefs(context: Context, map: Map<String, HubCategoryCustomization>, orderList: List<String>) {
        try {
            val json = JSONObject()
            map.forEach { (id, item) ->
                val obj = JSONObject()
                obj.put("customTitle", item.customTitle)
                obj.put("customSubtitle", item.customSubtitle)
                obj.put("customImageUrl", item.customImageUrl)
                obj.put("customIconName", item.customIconName)
                obj.put("isLocked", item.isLocked)
                obj.put("orderIndex", item.orderIndex)
                json.put(id, obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CATEGORIES_JSON, json.toString())
                .putString(KEY_CATEGORIES_ORDER, orderList.joinToString(","))
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write hub categories to prefs", e)
        }
    }
}
