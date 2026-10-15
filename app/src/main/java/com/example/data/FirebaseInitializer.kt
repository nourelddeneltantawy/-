package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    // Fallback constants directly sourced from google-services.json to guarantee 100% initialization
    private const val FALLBACK_APP_ID = "1:194875724444:android:091200d9ad9ec60ccb9359"
    private const val FALLBACK_API_KEY = "AIzaSyDq4pnWEFlWa7bVWCf0r-T2lNMQKZRH3hc"
    private const val FALLBACK_PROJECT_ID = "gen-lang-client-0547794844"
    private const val FALLBACK_STORAGE_BUCKET = "gen-lang-client-0547794844.firebasestorage.app"
    private const val FALLBACK_GCM_SENDER_ID = "194875724444"

    fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
        val appContext = context.applicationContext ?: context

        // 1. Check if already initialized
        try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                return FirebaseApp.getInstance()
            }
        } catch (_: Throwable) {}

        // 2. Try default auto-initialization from resources
        try {
            val app = FirebaseApp.initializeApp(appContext)
            if (app != null) {
                Log.d(TAG, "Firebase initialized via default provider: ${app.name}")
                return app
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Standard FirebaseApp.initializeApp returned error: ${e.message}")
        }

        // 3. Fallback: Build explicit FirebaseOptions from resources or constants
        return try {
            val appId = getStringResource(appContext, "google_app_id") ?: FALLBACK_APP_ID
            val apiKey = getStringResource(appContext, "google_api_key") ?: FALLBACK_API_KEY
            val projectId = getStringResource(appContext, "project_id") ?: FALLBACK_PROJECT_ID
            val storageBucket = getStringResource(appContext, "google_storage_bucket") ?: FALLBACK_STORAGE_BUCKET
            val gcmSenderId = getStringResource(appContext, "gcm_defaultSenderId") ?: FALLBACK_GCM_SENDER_ID

            val options = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .setGcmSenderId(gcmSenderId)
                .build()

            val app = FirebaseApp.initializeApp(appContext, options)
            Log.d(TAG, "Firebase initialized with explicit options: ${app.name}")
            app
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing Firebase with explicit options: ${t.message}", t)
            try {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    FirebaseApp.getInstance()
                } else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun getStringResource(context: Context, resName: String): String? {
        return try {
            val resId = context.resources.getIdentifier(resName, "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        } catch (_: Throwable) {
            null
        }
    }
}
