package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class WasherMotorsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Explicitly ensure FirebaseApp is initialized for the entire app lifecycle
            val app = if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            } else {
                FirebaseApp.getInstance()
            }
            Log.d("WasherMotorsApp", "Firebase initialized successfully: ${app?.name}")
        } catch (e: Throwable) {
            Log.e("WasherMotorsApp", "Firebase initialization caught error: ${e.message}", e)
        }

        // Safe uncaught exception handler to prevent hard crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WasherMotorsApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
