package com.example.util

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    fun getFirestore(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext ?: context
        val app = ensureInitialized(appContext)
        val dbId = try {
            appContext.getString(R.string.firestore_database_id)
        } catch (_: Throwable) {
            ""
        }
        return try {
            if (app != null) {
                if (dbId.isNotBlank()) FirebaseFirestore.getInstance(app, dbId) else FirebaseFirestore.getInstance(app)
            } else {
                if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
            }
        } catch (_: Throwable) {
            if (app != null) FirebaseFirestore.getInstance(app) else FirebaseFirestore.getInstance()
        }
    }

    // Fallback credentials from google-services.json to guarantee Firebase initializes
    // even if resource resolution fails in process/namespace mismatch environments
    private const val FALLBACK_APP_ID = "1:194875724444:android:091200d9ad9ec60ccb9359"
    private const val FALLBACK_API_KEY = "AIzaSyDq4pnWEFlWa7bVWCf0r-T2lNMQKZRH3hc"
    private const val FALLBACK_PROJECT_ID = "gen-lang-client-0547794844"
    private const val FALLBACK_STORAGE_BUCKET = "gen-lang-client-0547794844.firebasestorage.app"
    private const val FALLBACK_GCM_SENDER_ID = "194875724444"

    @Synchronized
    fun ensureInitialized(context: Context): FirebaseApp? {
        val appContext = context.applicationContext ?: context

        // 1. If already initialized, return the default instance
        try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                return FirebaseApp.getInstance()
            }
        } catch (_: Throwable) {}

        // 2. Try default resource-based initialization
        try {
            val app = FirebaseApp.initializeApp(appContext)
            if (app != null) {
                Log.d(TAG, "Firebase initialized via default initializeApp(context)")
                return app
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Default FirebaseApp.initializeApp failed: ${e.message}")
        }

        // 3. Fallback: Initialize explicitly with FirebaseOptions
        return try {
            val res = appContext.resources

            fun resolveString(key: String, fallback: String): String {
                return try {
                    val id = res.getIdentifier(key, "string", appContext.packageName).takeIf { it != 0 }
                        ?: res.getIdentifier(key, "string", "com.example").takeIf { it != 0 }
                        ?: 0
                    if (id != 0) res.getString(id) else fallback
                } catch (_: Throwable) {
                    fallback
                }
            }

            val appId = resolveString("google_app_id", FALLBACK_APP_ID)
            val apiKey = resolveString("google_api_key", FALLBACK_API_KEY)
            val projectId = resolveString("project_id", FALLBACK_PROJECT_ID)
            val storageBucket = resolveString("google_storage_bucket", FALLBACK_STORAGE_BUCKET)
            val gcmSenderId = resolveString("gcm_defaultSenderId", FALLBACK_GCM_SENDER_ID)

            val options = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .setGcmSenderId(gcmSenderId)
                .build()

            val app = FirebaseApp.initializeApp(appContext, options)
            Log.i(TAG, "Firebase initialized explicitly with options for app: ${app.name}")
            app
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Firebase with explicit options: ${e.message}", e)
            null
        }
    }
}
