package com.example.util

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val COLLECTION_SETTINGS = "app_settings"
    private const val DOC_UPDATE = "app_update"

    suspend fun checkUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        try {
            // 1. Primary source: Firebase Firestore (real-time sync across all devices)
            val docSnapshot = Firebase.firestore
                .collection(COLLECTION_SETTINGS)
                .document(DOC_UPDATE)
                .get()
                .await()

            if (docSnapshot.exists()) {
                val remoteVersionCode = (docSnapshot.getLong("versionCode") ?: (docSnapshot.get("versionCode") as? Number)?.toLong() ?: 1L).toInt()
                val remoteVersionName = docSnapshot.getString("versionName") ?: currentName
                val downloadUrl = docSnapshot.getString("downloadUrl") ?: ""
                val releaseNotes = docSnapshot.getString("releaseNotes") ?: ""
                val forceUpdate = docSnapshot.getBoolean("forceUpdate") ?: false

                val hasUpdate = remoteVersionCode > currentCode || 
                    (remoteVersionCode == currentCode && downloadUrl.isNotBlank() && remoteVersionName != currentName && remoteVersionName.isNotBlank())

                AppUpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    remoteVersionCode = remoteVersionCode,
                    remoteVersionName = remoteVersionName,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    forceUpdate = forceUpdate,
                    hasUpdate = hasUpdate
                )
            } else {
                // No update document in Firestore yet: App is up to date!
                AppUpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    remoteVersionCode = currentCode,
                    remoteVersionName = currentName,
                    downloadUrl = "",
                    releaseNotes = "",
                    forceUpdate = false,
                    hasUpdate = false
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking updates from Firestore: ${e.message}", e)
            null
        }
    }

    fun normalizeDownloadUrl(url: String): String {
        val trimmed = url.trim()
        val driveRegex = Regex("""https?://drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
        val match = driveRegex.find(trimmed)
        if (match != null) {
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }
        return trimmed
    }

    suspend fun publishUpdate(
        versionCode: Int,
        versionName: String,
        downloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean,
        adminEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val finalUrl = normalizeDownloadUrl(downloadUrl)
            val data = hashMapOf<String, Any>(
                "versionCode" to versionCode,
                "versionName" to versionName.trim(),
                "downloadUrl" to finalUrl,
                "releaseNotes" to releaseNotes.trim(),
                "forceUpdate" to forceUpdate,
                "updatedAt" to System.currentTimeMillis(),
                "updatedBy" to adminEmail
            )

            // 8-second timeout prevents indefinite hanging
            kotlinx.coroutines.withTimeout(8000L) {
                Firebase.firestore
                    .collection(COLLECTION_SETTINGS)
                    .document(DOC_UPDATE)
                    .set(data)
                    .await()
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Error publishing update to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun installLocalApk(context: Context, uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error installing local APK: ${e.message}", e)
            false
        }
    }

    fun downloadAndInstallUpdate(context: Context, updateInfo: AppUpdateInfo) {
        val rawUrl = updateInfo.downloadUrl.trim()
        val isWebLink = rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)

        if (!isWebLink) {
            // Explain cleanly to user instead of copying text to clipboard
            Toast.makeText(context, "ملف التحديث متوفر من الإدارة عبر واتساب", Toast.LENGTH_LONG).show()
            try {
                // Open WhatsApp to request the file from admin
                val whatsappNumber = "+201015697669"
                val text = "السلام عليكم م/ نور، يتوفر تحديث جديد للتطبيق (v${updateInfo.remoteVersionName})، برجاء إرسال ملف الـ APK لتثبيته."
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$whatsappNumber?text=${Uri.encode(text)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(waIntent)
            } catch (_: Throwable) {}
            return
        }

        val downloadUrl = normalizeDownloadUrl(rawUrl)
        var intentOpened = false

        // 1. Try opening direct download/release page via system browser/installer
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            intentOpened = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error opening download URL: ${e.message}", e)
        }

        // 2. If it's a direct APK file link, also trigger Android DownloadManager
        if (downloadUrl.endsWith(".apk", ignoreCase = true) || downloadUrl.contains("export=download")) {
            try {
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setTitle("تحديث البراء إلكترونيك v${updateInfo.remoteVersionName}")
                    setDescription("جاري تحميل ملف التحديث APK...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "AlbaraaElectronic_v${updateInfo.remoteVersionName}.apk"
                    )
                    setMimeType("application/vnd.android.package-archive")
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.enqueue(request)
                Toast.makeText(context, "بدأ تحميل التحديث في الإشعارات...", Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }

        if (!intentOpened) {
            copyToClipboard(context, downloadUrl)
            Toast.makeText(context, "تم نسخ رابط التحميل إلى الحافظة", Toast.LENGTH_LONG).show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("APK Download Link", text)
            clipboard?.setPrimaryClip(clip)
        } catch (_: Throwable) {}
    }
}
