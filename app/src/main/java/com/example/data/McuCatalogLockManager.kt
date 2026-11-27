package com.example.data

import android.content.Context
import com.example.data.model.McuProcessor
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time Admin lock/unlock manager for MCU manufacturers and main families in Step 1.
 * Syncs with Firestore (app_settings/mcu_catalog_locks) + local SharedPreferences cache.
 */
object McuCatalogLockManager {
    private const val PREFS_NAME = "mcu_catalog_locks_prefs"
    private const val KEY_LOCKED_MANUFACTURERS = "locked_manufacturers"
    private const val KEY_LOCKED_FAMILIES = "locked_families"
    private const val DOC_COLLECTION = "app_settings"
    private const val DOC_ID = "mcu_catalog_locks"

    private val _lockedManufacturers = MutableStateFlow<Set<String>>(emptySet())
    val lockedManufacturers: StateFlow<Set<String>> = _lockedManufacturers.asStateFlow()

    private val _lockedMainFamilies = MutableStateFlow<Set<String>>(emptySet())
    val lockedMainFamilies: StateFlow<Set<String>> = _lockedMainFamilies.asStateFlow()

    private var listenerStarted = false

    fun familyKey(manufacturer: String, mainFamily: String): String =
        "${manufacturer.trim()}||${mainFamily.trim()}"

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lockedManufacturers.value = prefs.getStringSet(KEY_LOCKED_MANUFACTURERS, emptySet())?.toSet() ?: emptySet()
        _lockedMainFamilies.value = prefs.getStringSet(KEY_LOCKED_FAMILIES, emptySet())?.toSet() ?: emptySet()

        if (!listenerStarted) {
            listenerStarted = true
            try {
                FirebaseInitializer.ensureInitialized(appCtx)
                val db = FirebaseFirestore.getInstance()
                db.collection(DOC_COLLECTION).document(DOC_ID)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        val mans = (snapshot.get("lockedManufacturers") as? List<*>)
                            ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                            ?.toSet() ?: emptySet()
                        val fams = (snapshot.get("lockedMainFamilies") as? List<*>)
                            ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                            ?.toSet() ?: emptySet()
                        _lockedManufacturers.value = mans
                        _lockedMainFamilies.value = fams
                        saveLocal(appCtx, mans, fams)
                    }
            } catch (_: Throwable) {}
        }
    }

    fun isManufacturerLocked(manufacturer: String): Boolean =
        manufacturer.trim() in _lockedManufacturers.value

    fun isMainFamilyLocked(manufacturer: String, mainFamily: String): Boolean {
        if (isManufacturerLocked(manufacturer)) return true
        return familyKey(manufacturer, mainFamily) in _lockedMainFamilies.value
    }

    fun isProcessorLocked(processor: McuProcessor): Boolean =
        isMainFamilyLocked(processor.manufacturer, processor.mainFamily)

    fun toggleManufacturerLock(context: Context, manufacturer: String) {
        val clean = manufacturer.trim()
        val updated = _lockedManufacturers.value.toMutableSet().apply {
            if (contains(clean)) remove(clean) else add(clean)
        }.toSet()
        _lockedManufacturers.value = updated
        persist(context, updated, _lockedMainFamilies.value)
    }

    fun toggleMainFamilyLock(context: Context, manufacturer: String, mainFamily: String) {
        val key = familyKey(manufacturer, mainFamily)
        val updated = _lockedMainFamilies.value.toMutableSet().apply {
            if (contains(key)) remove(key) else add(key)
        }.toSet()
        _lockedMainFamilies.value = updated
        persist(context, _lockedManufacturers.value, updated)
    }

    private fun saveLocal(context: Context, mans: Set<String>, fams: Set<String>) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCKED_MANUFACTURERS, mans)
            .putStringSet(KEY_LOCKED_FAMILIES, fams)
            .apply()
    }

    private fun persist(context: Context, mans: Set<String>, fams: Set<String>) {
        saveLocal(context, mans, fams)
        try {
            FirebaseInitializer.ensureInitialized(context.applicationContext)
            val data = mapOf(
                "lockedManufacturers" to mans.toList().sorted(),
                "lockedMainFamilies" to fams.toList().sorted(),
                "updatedAt" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance()
                .collection(DOC_COLLECTION)
                .document(DOC_ID)
                .set(data, SetOptions.merge())
        } catch (_: Throwable) {}
    }
}
