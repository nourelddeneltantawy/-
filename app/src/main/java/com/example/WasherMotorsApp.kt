package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class WasherMotorsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Guarantee FirebaseApp is safely initialized before any component uses it
            val app = FirebaseApp.initializeApp(this)
            Log.d("WasherMotorsApp", "Firebase initialized successfully: ${app?.name}")
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
