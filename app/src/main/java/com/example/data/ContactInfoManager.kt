package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ContactInfo
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ContactInfoManager {
    private const val PREFS_NAME = "albaraa_contact_prefs"
    private const val KEY_NAME = "engineer_name"
    private const val KEY_SUBTITLE = "title_subtitle"
    private const val KEY_CENTER = "center_name"
    private const val KEY_ADDRESS = "address"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_WHATSAPP = "whatsapp_number"
    private const val KEY_LOCATION = "location_url"

    private val _contactInfo = MutableStateFlow(ContactInfo())
    val contactInfo: StateFlow<ContactInfo> = _contactInfo.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = ContactInfo(
            engineerName = prefs.getString(KEY_NAME, "م / نور الدين مصطفى الطنطاوي") ?: "م / نور الدين مصطفى الطنطاوي",
            titleSubtitle = prefs.getString(KEY_SUBTITLE, "مهندس صيانة وبرمجة كروت ومواتير الغسالات") ?: "مهندس صيانة وبرمجة كروت ومواتير الغسالات",
            centerName = prefs.getString(KEY_CENTER, "مركز البراء إلكترونيك للصيانة المتطورة") ?: "مركز البراء إلكترونيك للصيانة المتطورة",
            address = prefs.getString(KEY_ADDRESS, "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم") ?: "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم",
            phoneNumber = prefs.getString(KEY_PHONE, "01018717141") ?: "01018717141",
            whatsappNumber = prefs.getString(KEY_WHATSAPP, "201018717141") ?: "201018717141",
            locationUrl = prefs.getString(KEY_LOCATION, "https://maps.google.com/?q=30.7865,31.0004") ?: "https://maps.google.com/?q=30.7865,31.0004"
        )
        _contactInfo.value = loaded

        // Listen to Firestore for remote sync
        try {
            Firebase.firestore.collection("app_settings").document("contact_info")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        val data = snapshot.data
                        if (data != null) {
                            val remote = ContactInfo.fromMap(data)
                            _contactInfo.value = remote
                            saveToPrefs(context, remote)
                        }
                    }
                }
        } catch (_: Throwable) {}
    }

    fun updateContactInfo(context: Context, newInfo: ContactInfo, onComplete: ((Boolean) -> Unit)? = null) {
        _contactInfo.value = newInfo
        saveToPrefs(context, newInfo)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Firebase.firestore.collection("app_settings").document("contact_info")
                    .set(newInfo.toMap())
                    .addOnCompleteListener { task ->
                        onComplete?.invoke(task.isSuccessful)
                    }
            } catch (_: Throwable) {
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
            .apply()
    }
}
