package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    const val APPLICATION_ID = "1:194875724444:android:091200d9ad9ec60ccb9359"
    const val API_KEY = "AIzaSyDq4pnWEFlWa7bVWCf0r-T2lNMQKZRH3hc"
    const val PROJECT_ID = "gen-lang-client-0547794844"
    const val STORAGE_BUCKET = "gen-lang-client-0547794844.firebasestorage.app"
    const val GCM_SENDER_ID = "194875724444"

    @Synchronized
    fun ensureInitialized(context: Context): FirebaseApp {
        val appContext = context.applicationContext ?: context

        // 1. Check if already initialized and active
        try {
            return FirebaseApp.getInstance()
        } catch (_: Throwable) {}

        // 2. Check getApps list
        try {
            val apps = FirebaseApp.getApps(appContext)
            if (apps.isNotEmpty()) {
                return apps.first()
            }
        } catch (_: Throwable) {}

        // 3. Try standard resource-based initialization
        try {
            val app = FirebaseApp.initializeApp(appContext)
            if (app != null) {
                Log.d(TAG, "Initialized FirebaseApp from resources successfully: ${app.name}")
                return app
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Resource-based FirebaseApp.initializeApp failed: ${e.message}")
        }

        // 4. Fallback: Initialize with explicit FirebaseOptions
        return try {
            val options = FirebaseOptions.Builder()
                .setApplicationId(APPLICATION_ID)
                .setApiKey(API_KEY)
                .setProjectId(PROJECT_ID)
                .setStorageBucket(STORAGE_BUCKET)
                .setGcmSenderId(GCM_SENDER_ID)
                .build()

            val app = FirebaseApp.initializeApp(appContext, options)
                ?: FirebaseApp.getInstance()
            Log.d(TAG, "Initialized FirebaseApp with explicit options successfully: ${app.name}")
            app
        } catch (t: Throwable) {
            Log.e(TAG, "Explicit FirebaseApp initialization failed", t)
            try {
                FirebaseApp.getInstance()
            } catch (_: Throwable) {
                throw t
            }
        }
    }
}
