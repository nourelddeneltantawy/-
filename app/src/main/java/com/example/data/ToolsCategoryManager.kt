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

/**
 * Manages Tools Categories customized per individual user email:
 * "قائمة الأدوات ديت تكون خاصة بكل واحد يسجل بالبريد الإلكتروني بتاعه وكل بريد إلكتروني بياناته منفصلة عن أي بريد آخر"
 */
object ToolsCategoryManager {
    private const val TAG = "ToolsCategoryManager"
    private const val PREFS_NAME = "albaraa_tools_categories_prefs"
    private const val KEY_CATEGORIES_JSON_PREFIX = "tools_categories_json_"
    private const val KEY_CATEGORIES_ORDER_PREFIX = "tools_categories_order_"

    private val _customizations = MutableStateFlow<Map<String, HubCategoryCustomization>>(emptyMap())
    val customizations: StateFlow<Map<String, HubCategoryCustomization>> = _customizations.asStateFlow()

    private val _categoryOrder = MutableStateFlow<List<String>>(emptyList())
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    private var currentEmail: String = ""
    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context, email: String? = null) {
        val safeEmail = (email ?: "guest").trim().lowercase()
        switchUser(context, safeEmail)
    }

    fun switchUser(context: Context, email: String?) {
        val appContext = context.applicationContext ?: context
        val safeEmail = (email ?: "guest").trim().lowercase()
        if (safeEmail == currentEmail && isInitialized) return

        currentEmail = safeEmail
        listenerRegistration?.remove()
        listenerRegistration = null

        // 1. Load from local cache keyed by user email
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("$KEY_CATEGORIES_JSON_PREFIX$safeEmail", null)
        val orderStr = prefs.getString("$KEY_CATEGORIES_ORDER_PREFIX$safeEmail", null)

        _categoryOrder.value = if (!orderStr.isNullOrBlank()) {
            orderStr.split(",").filter { it.isNotBlank() }
        } else {
            emptyList()
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
                        isHidden = obj.optBoolean("isHidden", false),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                }
                _customizations.value = map
                if (_categoryOrder.value.isEmpty()) {
                    val sorted = map.values.sortedBy { it.orderIndex }.map { it.id }
                    if (sorted.isNotEmpty()) _categoryOrder.value = sorted
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed parsing local cache for $safeEmail: ${e.message}")
            }
        } else {
            _customizations.value = emptyMap()
        }

        // 2. Fetch remote and attach real-time listener if authenticated
        if (safeEmail.isNotBlank() && safeEmail != "guest") {
            fetchRemote(appContext, safeEmail)
            attachListener(appContext, safeEmail)
        }
        isInitialized = true
    }

    private fun attachListener(context: Context, safeEmail: String) {
        try {
            val db = FirebaseInitializer.getFirestore(context)
            listenerRegistration = db.collection("user_tools_settings").document(safeEmail)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Tools categories listener error for $safeEmail: ${error.message}")
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
                            saveToPrefs(context, safeEmail, map, _categoryOrder.value)
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to attach real-time listener for $safeEmail", e)
        }
    }

    fun fetchRemote(context: Context, safeEmail: String) {
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val snapshot = try {
                    db.collection("user_tools_settings").document(safeEmail).get(Source.SERVER).await()
                } catch (_: Throwable) {
                    db.collection("user_tools_settings").document(safeEmail).get().await()
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
                        saveToPrefs(appContext, safeEmail, map, _categoryOrder.value)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed remote fetch for $safeEmail: ${e.message}")
            }
        }
    }

    fun toggleCategoryLock(context: Context, categoryId: String, onComplete: ((Boolean) -> Unit)? = null) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        val existing = current[categoryId] ?: HubCategoryCustomization(id = categoryId)
        val updated = existing.copy(isLocked = !existing.isLocked)
        current[categoryId] = updated
        _customizations.value = current
        saveToPrefs(context, email, current, _categoryOrder.value)
        syncToFirestore(context, email, current, _categoryOrder.value, onComplete)
    }

    fun toggleCategoryVisibility(context: Context, categoryId: String, onComplete: ((Boolean) -> Unit)? = null) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        val existing = current[categoryId] ?: HubCategoryCustomization(id = categoryId)
        val updated = existing.copy(isHidden = !existing.isHidden)
        current[categoryId] = updated
        _customizations.value = current
        saveToPrefs(context, email, current, _categoryOrder.value)
        syncToFirestore(context, email, current, _categoryOrder.value, onComplete)
    }

    fun setCategoryVisibility(
        context: Context,
        id: String,
        isHidden: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        val existing = current[id] ?: HubCategoryCustomization(id = id)
        current[id] = existing.copy(isHidden = isHidden)
        _customizations.value = current
        saveToPrefs(context, email, current, _categoryOrder.value)
        syncToFirestore(context, email, current, _categoryOrder.value, onComplete)
    }

    fun addCategory(
        context: Context,
        id: String,
        title: String,
        subtitle: String,
        iconName: String = "build",
        imageUrl: String = "",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        val newCat = HubCategoryCustomization(
            id = id,
            customTitle = title.trim(),
            customSubtitle = subtitle.trim(),
            customIconName = iconName.trim(),
            customImageUrl = imageUrl.trim(),
            orderIndex = _categoryOrder.value.size
        )
        current[id] = newCat
        _customizations.value = current

        val order = _categoryOrder.value.toMutableList()
        if (!order.contains(id)) {
            order.add(id)
            _categoryOrder.value = order
        }

        saveToPrefs(context, email, current, order)
        syncToFirestore(context, email, current, order, onComplete)
    }

    fun updateCategory(
        context: Context,
        categoryId: String,
        title: String,
        subtitle: String,
        imageUrl: String = "",
        iconName: String = "",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        val existing = current[categoryId] ?: HubCategoryCustomization(id = categoryId)
        val updated = existing.copy(
            customTitle = title.trim(),
            customSubtitle = subtitle.trim(),
            customImageUrl = imageUrl.trim(),
            customIconName = iconName.trim()
        )
        current[categoryId] = updated
        _customizations.value = current

        val order = _categoryOrder.value.toMutableList()
        if (!order.contains(categoryId)) {
            order.add(categoryId)
            _categoryOrder.value = order
        }

        saveToPrefs(context, email, current, order)
        syncToFirestore(context, email, current, order, onComplete)
    }

    fun deleteCategory(
        context: Context,
        id: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        current.remove(id)
        _customizations.value = current

        val order = _categoryOrder.value.toMutableList()
        order.remove(id)
        _categoryOrder.value = order

        saveToPrefs(context, email, current, order)
        syncToFirestore(context, email, current, order, onComplete)
    }

    fun resetCategory(
        context: Context,
        id: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val email = currentEmail
        val current = _customizations.value.toMutableMap()
        current.remove(id)
        _customizations.value = current
        saveToPrefs(context, email, current, _categoryOrder.value)
        syncToFirestore(context, email, current, _categoryOrder.value, onComplete)
    }

    fun moveCategory(
        context: Context,
        categoryId: String,
        delta: Int,
        allCategories: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentOrder = if (_categoryOrder.value.isNotEmpty()) _categoryOrder.value.toMutableList() else allCategories.toMutableList()
        allCategories.forEach { if (!currentOrder.contains(it)) currentOrder.add(it) }

        val idx = currentOrder.indexOf(categoryId)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, currentOrder.size - 1)
        if (newIdx == idx) return

        currentOrder.removeAt(idx)
        currentOrder.add(newIdx, categoryId)
        reorderCategories(context, currentOrder, onComplete)
    }

    fun reorderCategories(context: Context, newOrder: List<String>, onComplete: ((Boolean) -> Unit)? = null) {
        val email = currentEmail
        _categoryOrder.value = newOrder
        val current = _customizations.value.toMutableMap()
        newOrder.forEachIndexed { index, catId ->
            val existing = current[catId] ?: HubCategoryCustomization(id = catId)
            current[catId] = existing.copy(orderIndex = index)
        }
        _customizations.value = current
        saveToPrefs(context, email, current, newOrder)
        syncToFirestore(context, email, current, newOrder, onComplete)
    }

    private fun syncToFirestore(
        context: Context,
        email: String,
        map: Map<String, HubCategoryCustomization>,
        order: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (email.isBlank() || email == "guest") {
            onComplete?.invoke(true)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val payload = mutableMapOf<String, Any>()
                map.forEach { (k, v) -> payload[k] = v.toMap() }
                payload["__order__"] = order

                db.collection("user_tools_settings").document(email)
                    .set(payload)
                    .addOnCompleteListener { task ->
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Error syncing tools categories to Firestore for $email", e)
                onComplete?.invoke(false)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreData(data: Map<String, Any>): Map<String, HubCategoryCustomization> {
        val result = mutableMapOf<String, HubCategoryCustomization>()
        for ((key, value) in data) {
            if (key == "__order__") continue
            if (value is Map<*, *>) {
                val strMap = value as Map<String, Any?>
                result[key] = HubCategoryCustomization.fromMap(key, strMap)
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreOrder(data: Map<String, Any>, map: Map<String, HubCategoryCustomization>): List<String> {
        val list = data["__order__"] as? List<*>
        if (list != null) {
            return list.filterIsInstance<String>()
        }
        return map.values.sortedBy { it.orderIndex }.map { it.id }
    }

    private fun saveToPrefs(context: Context, email: String, map: Map<String, HubCategoryCustomization>, order: List<String>) {
        try {
            val json = JSONObject()
            map.forEach { (id, item) ->
                val obj = JSONObject()
                obj.put("customTitle", item.customTitle)
                obj.put("customSubtitle", item.customSubtitle)
                obj.put("customImageUrl", item.customImageUrl)
                obj.put("customIconName", item.customIconName)
                obj.put("isLocked", item.isLocked)
                obj.put("isHidden", item.isHidden)
                obj.put("orderIndex", item.orderIndex)
                json.put(id, obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("$KEY_CATEGORIES_JSON_PREFIX$email", json.toString())
                .putString("$KEY_CATEGORIES_ORDER_PREFIX$email", order.joinToString(","))
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed saving tools categories to prefs for $email: ${e.message}")
        }
    }
}
