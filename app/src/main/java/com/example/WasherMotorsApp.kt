package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

class WasherMotorsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Programmatic Firebase initialization
            val app = FirebaseApp.initializeApp(this)
            Log.d("WasherMotorsApp", "Firebase initialized successfully: ${app?.name}")
        } catch (e: Throwable) {
            Log.e("WasherMotorsApp", "Standard Firebase initialization error: ${e.message}", e)
            try {
                FirebaseInitializer.ensureInitialized(this)
            } catch (t: Throwable) {
                Log.e("WasherMotorsApp", "Fallback Firebase initialization error: ${t.message}", t)
            }
        }

        // Install safe uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WasherMotorsApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
