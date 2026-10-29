package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.ContactInfo
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

object ContactInfoManager {
    private const val TAG = "ContactInfoManager"
    private const val PREFS_NAME = "albaraa_contact_prefs"
    private const val KEY_NAME = "engineer_name"
    private const val KEY_SUBTITLE = "title_subtitle"
    private const val KEY_CENTER = "center_name"
    private const val KEY_ADDRESS = "address"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_WHATSAPP = "whatsapp_number"
    private const val KEY_LOCATION = "location_url"
    private const val KEY_FACEBOOK = "facebook_url"

    private val _contactInfo = MutableStateFlow(ContactInfo())
    val contactInfo: StateFlow<ContactInfo> = _contactInfo.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context

        // 1. Load from local cache for instant zero-latency UI display
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = ContactInfo(
            engineerName = prefs.getString(KEY_NAME, "م / نور الدين مصطفى الطنطاوي") ?: "م / نور الدين مصطفى الطنطاوي",
            titleSubtitle = prefs.getString(KEY_SUBTITLE, "مهندس صيانة وبرمجة كروت ومواتير الغسالات") ?: "مهندس صيانة وبرمجة كروت ومواتير الغسالات",
            centerName = prefs.getString(KEY_CENTER, "مركز البراء إلكترونيك للصيانة المتطورة") ?: "مركز البراء إلكترونيك للصيانة المتطورة",
            address = prefs.getString(KEY_ADDRESS, "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم") ?: "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم",
            phoneNumber = prefs.getString(KEY_PHONE, "01018717141") ?: "01018717141",
            whatsappNumber = prefs.getString(KEY_WHATSAPP, "201018717141") ?: "201018717141",
            locationUrl = prefs.getString(KEY_LOCATION, "https://maps.google.com/?q=30.7865,31.0004") ?: "https://maps.google.com/?q=30.7865,31.0004",
            facebookUrl = prefs.getString(KEY_FACEBOOK, "https://facebook.com/AlBaraa01018717141Electronic") ?: "https://facebook.com/AlBaraa01018717141Electronic"
        )
        _contactInfo.value = loaded

        // 2. Direct remote fetch to force fresh server data immediately
        fetchRemote(appContext)

        // 3. Attach real-time snapshot listener on the correct named Firestore database
        if (listenerRegistration == null) {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                listenerRegistration = db.collection("app_settings").document("contact_info")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "SnapshotListener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val data = snapshot.data
                            if (data != null) {
                                val remote = ContactInfo.fromMap(data)
                                _contactInfo.value = remote
                                saveToPrefs(appContext, remote)
                                Log.i(TAG, "Contact info synced in real-time from server: ${remote.centerName}")
                            }
                        }
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to attach real-time contact listener", e)
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
                    db.collection("app_settings").document("contact_info")
                        .get(Source.SERVER).await()
                } catch (_: Throwable) {
                    db.collection("app_settings").document("contact_info")
                        .get().await()
                }

                if (snapshot.exists()) {
                    val data = snapshot.data
                    if (data != null) {
                        val remote = ContactInfo.fromMap(data)
                        _contactInfo.value = remote
                        saveToPrefs(appContext, remote)
                        Log.i(TAG, "Fetched remote contact info: ${remote.centerName}")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to fetch remote contact info: ${e.message}")
            }
        }
    }

    fun updateContactInfo(context: Context, newInfo: ContactInfo, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        _contactInfo.value = newInfo
        saveToPrefs(appContext, newInfo)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("app_settings").document("contact_info")
                    .set(newInfo.toMap())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Successfully updated contact info in Firestore")
                        } else {
                            Log.e(TAG, "Failed to update contact info in Firestore: ${task.exception?.message}")
                        }
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception updating contact info in Firestore", e)
                onComplete?.invoke(false)
            }
        }
    }

    private fun saveToPrefs(context: Context, info: ContactInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NAME, info.engineerName)
            .putString(KEY_SUBTITLE, info.titleSubtitle)
            .putString(KEY_CENTER, info.centerName)
            .putString(KEY_ADDRESS, info.address)
            .putString(KEY_PHONE, info.phoneNumber)
            .putString(KEY_WHATSAPP, info.whatsappNumber)
            .putString(KEY_LOCATION, info.locationUrl)
            .putString(KEY_FACEBOOK, info.facebookUrl)
            .apply()
    }
}
