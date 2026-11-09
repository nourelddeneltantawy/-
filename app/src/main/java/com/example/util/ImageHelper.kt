package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageHelper {
    private const val TAG = "ImageHelper"
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val COMPRESS_QUALITY = 88

    /**
     * Permanently saves a picked image to internal storage AND returns either:
     * 1. A public Firebase Storage URL (if online and storage bucket is ready)
     * 2. An optimized Base64 data URI (self-contained, indestructible across updates/reinstalls!)
     * 3. A permanent local internal storage path as fallback.
     */
    suspend fun saveAndPreparePermanentImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            // 1. Read and decode bitmap with proper scaling
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                return@withContext copyUriToLocalStorage(context, uri)
            }

            // Scale down smoothly if larger than 1280px to optimize size and keep crystal clarity
            val scaledBitmap = scaleBitmapDown(originalBitmap, MAX_IMAGE_DIMENSION)

            // Compress to JPEG bytes
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, baos)
            val imageBytes = baos.toByteArray()

            // 2. Save locally to permanent directory
            val persistentDir = File(context.filesDir, "persistent_images")
            if (!persistentDir.exists()) persistentDir.mkdirs()
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val localFile = File(persistentDir, fileName)
            FileOutputStream(localFile).use { fos ->
                fos.write(imageBytes)
                fos.flush()
            }

            // 3. Try uploading to Firebase Storage for cloud resilience
            try {
                val storage = Firebase.storage
                val storageRef = storage.reference.child("app_images/$fileName")
                storageRef.putBytes(imageBytes).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                if (downloadUrl.isNotBlank()) {
                    Log.i(TAG, "Uploaded image to Firebase Storage: $downloadUrl")
                    return@withContext downloadUrl
                }
            } catch (storageErr: Throwable) {
                Log.w(TAG, "Storage upload skipped/failed (${storageErr.message}), falling back to Base64 data URI")
            }

            // 4. Fallback to Base64 Data URI if imageBytes < 600KB, or local path
            if (imageBytes.size < 600 * 1024) {
                val base64Str = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                return@withContext "data:image/jpeg;base64,$base64Str"
            }

            return@withContext localFile.absolutePath
        } catch (e: Throwable) {
            Log.e(TAG, "Failed preparing permanent image", e)
            copyUriToLocalStorage(context, uri)
        }
    }

    /**
     * Copies an image from a content URI to the app's internal private storage directory.
     */
    fun copyUriToLocalStorage(context: Context, uri: Uri): String {
        return try {
            val imagesDir = File(context.filesDir, "persistent_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val fileName = "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return uri.toString()

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving image to local storage", e)
            uri.toString()
        }
    }

    /**
     * Formats an image path or URL for Coil loading with full Base64, Cloud, and Local support.
     */
    fun getCoilData(pathOrUrl: String): Any {
        val trimmed = pathOrUrl.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed == "sample_lg_diagram") {
            return com.example.R.drawable.sample_lg_diagram
        }
        // Base64 Data URI -> Decode into ByteArray for ultra-fast Coil rendering
        if (trimmed.startsWith("data:image/") && trimmed.contains(";base64,")) {
            try {
                val base64Data = trimmed.substringAfter(";base64,")
                return Base64.decode(base64Data, Base64.DEFAULT)
            } catch (_: Throwable) {}
        }
        // Local File
        if (trimmed.startsWith("/") && File(trimmed).exists()) {
            return File(trimmed)
        }
        return trimmed
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = ((resizedHeight.toFloat() / originalHeight.toFloat()) * originalWidth).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = ((resizedWidth.toFloat() / originalWidth.toFloat()) * originalHeight).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        }
    }
}
