package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object ApkUploader {
    private const val TAG = "ApkUploader"

    suspend fun uploadApkToStorage(
        context: Context,
        uri: Uri,
        versionCode: Int,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val storage = Firebase.storage
            val fileName = "albaraa_update_v${versionCode}_${System.currentTimeMillis()}.apk"
            val storageRef = storage.reference.child("updates/$fileName")

            val uploadTask = storageRef.putFile(uri)
            uploadTask.addOnProgressListener { snapshot ->
                if (snapshot.totalByteCount > 0) {
                    val percent = ((snapshot.bytesTransferred * 100) / snapshot.totalByteCount).toInt()
                    onProgress(percent.coerceIn(0, 99))
                }
            }

            // Wait for upload completion
            uploadTask.await()

            // Fetch public download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(100)
            Result.success(downloadUrl)
        } catch (e: Throwable) {
            Log.e(TAG, "Firebase Storage upload error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
