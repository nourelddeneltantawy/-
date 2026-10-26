package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageHelper {

    /**
     * Copies an image from a content URI (e.g., from Android PhotoPicker)
     * to the app's internal private storage directory.
     * This guarantees permanent, permission-free access across all screens and app restarts,
     * preventing SecurityException and missing image bugs.
     */
    fun copyUriToLocalStorage(context: Context, uri: Uri): String {
        return try {
            val imagesDir = File(context.filesDir, "motor_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val fileName = "motor_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return uri.toString()

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("ImageHelper", "Failed to save picked image to internal storage", e)
            uri.toString()
        }
    }

    /**
     * Formats an image path or URL for Coil loading.
     */
    fun getCoilData(pathOrUrl: String): Any {
        val trimmed = pathOrUrl.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed == "sample_lg_diagram") {
            return com.example.R.drawable.sample_lg_diagram
        }
        if (trimmed.startsWith("/") && File(trimmed).exists()) {
            return File(trimmed)
        }
        return trimmed
    }
}
