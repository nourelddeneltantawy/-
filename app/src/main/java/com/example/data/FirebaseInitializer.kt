package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    fun ensureInitialized(context: Context): FirebaseApp? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                val app = FirebaseApp.initializeApp(context)
                if (app != null) {
                    Log.d(TAG, "Firebase initialized via default context.")
                    app
                } else {
                    initializeWithOptions(context)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Default initializeApp failed, attempting manual options fallback: ${e.message}")
            try {
                initializeWithOptions(context)
            } catch (fallbackError: Throwable) {
                Log.e(TAG, "Critical: Both default and fallback Firebase initialization failed", fallbackError)
                null
            }
        }
    }

    private fun initializeWithOptions(context: Context): FirebaseApp? {
        return try {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:194875724444:android:091200d9ad9ec60ccb9359")
                .setApiKey("AIzaSyDq4pnWEFlWa7bVWCf0r-T2lNMQKZRH3hc")
                .setProjectId("gen-lang-client-0547794844")
                .setStorageBucket("gen-lang-client-0547794844.firebasestorage.app")
                .build()
            val app = FirebaseApp.initializeApp(context, options)
            Log.d(TAG, "Firebase initialized with explicit options successfully: ${app.name}")
            app
        } catch (e: Throwable) {
            Log.e(TAG, "Failed initializing with options: ${e.message}", e)
            null
        }
    }
}
