package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class WasherMotorsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Guarantee FirebaseApp is safely initialized with fallback options before any component uses it
            val app = com.example.data.FirebaseInitializer.ensureFirebaseInitialized(this)
            Log.d("WasherMotorsApp", "Firebase initialized safely: ${app?.name}")
        } catch (e: Throwable) {
            Log.e("WasherMotorsApp", "Firebase initialization caught error: ${e.message}", e)
        }

        // Install safe uncaught exception handler to prevent hard crashes and log issues cleanly
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WasherMotorsApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
