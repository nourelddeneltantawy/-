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
    private const val PREFS_NAME = "albaraa_update_prefs"
    private const val KEY_ACK_VERSION = "acknowledged_version_code"
    private const val KEY_ACK_NAME = "acknowledged_version_name"
    private const val KEY_ACK_TIME = "acknowledged_update_time"

    fun normalizeVersion(v: String): String {
        return v.trim()
            .lowercase()
            .removePrefix("v")
            .removePrefix("version")
            .trim()
    }

    fun getAcknowledgedVersionCode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACK_VERSION, 0)
    }

    fun getAcknowledgedVersionName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACK_NAME, "") ?: ""
    }

    fun setAcknowledgedVersion(context: Context, versionCode: Int, versionName: String, updatedAt: Long = 0L) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_ACK_VERSION, versionCode)
            .putString(KEY_ACK_NAME, normalizeVersion(versionName))
            .putLong(KEY_ACK_TIME, updatedAt)
            .apply()
    }

    suspend fun checkUpdate(context: Context, ignoreAcknowledged: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME
        val currentNorm = normalizeVersion(currentName)

        try {
            // 1. Primary source: Firebase Firestore
            val db = FirebaseInitializer.getFirestore(context)
            val docSnapshot = db
                .collection(COLLECTION_SETTINGS)
                .document(DOC_UPDATE)
                .get()
                .await()

            if (docSnapshot.exists()) {
                val remoteVersionCode = (docSnapshot.getLong("versionCode") ?: (docSnapshot.get("versionCode") as? Number)?.toLong() ?: 1L).toInt()
                val remoteVersionName = docSnapshot.getString("versionName") ?: currentName
                val remoteNorm = normalizeVersion(remoteVersionName)
                val downloadUrl = docSnapshot.getString("downloadUrl") ?: ""
                val releaseNotes = docSnapshot.getString("releaseNotes") ?: ""
                val forceUpdate = docSnapshot.getBoolean("forceUpdate") ?: false
                val updatedAt = docSnapshot.getLong("updatedAt") ?: 0L

                val isCodeNewer = remoteVersionCode > currentCode
                val isNameDifferent = remoteNorm != currentNorm && remoteNorm.isNotBlank()
                val isActuallyNewer = (isCodeNewer || (remoteVersionCode >= currentCode && isNameDifferent)) && downloadUrl.isNotBlank()

                val ackCode = getAcknowledgedVersionCode(context)
                val ackName = getAcknowledgedVersionName(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val ackTime = prefs.getLong(KEY_ACK_TIME, 0L)

                // If user dismissed this version or timestamp, don't show on startup
                val isAcknowledged = (remoteVersionCode <= ackCode && (remoteNorm == ackName || ackName.isNotBlank())) ||
                                     (updatedAt > 0L && ackTime >= updatedAt)

                // When user manually taps check update: ignoreAcknowledged = true
                // On auto launch: show only if there is an actual newer version and it hasn't been dismissed
                val hasUpdate = isActuallyNewer && (ignoreAcknowledged || !isAcknowledged)

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
                // No update document in Firestore: App is up to date!
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
        adminEmail: String,
        context: Context? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val finalUrl = normalizeDownloadUrl(downloadUrl)
            val now = System.currentTimeMillis()
            val data = hashMapOf<String, Any>(
                "versionCode" to versionCode,
                "versionName" to versionName.trim(),
                "downloadUrl" to finalUrl,
                "releaseNotes" to releaseNotes.trim(),
                "forceUpdate" to forceUpdate,
                "updatedAt" to now,
                "updatedBy" to adminEmail
            )

            // Acknowledge this version for the current device so it doesn't prompt the admin
            context?.let { ctx ->
                setAcknowledgedVersion(ctx, versionCode, versionName, now)
            }

            // Quick network timeout (3.5s). Firestore persists to disk in <10ms and syncs in background.
            try {
                kotlinx.coroutines.withTimeout(3500L) {
                    val db = if (context != null) FirebaseInitializer.getFirestore(context) else FirebaseInitializer.getFirestore(com.google.firebase.FirebaseApp.getInstance().applicationContext)
                    db.collection(COLLECTION_SETTINGS)
                        .document(DOC_UPDATE)
                        .set(data)
                        .await()
                }
            } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "Server ack timeout, but Firestore persisted update locally and will sync.")
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Error publishing update to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun installLocalApk(context: Context, uri: Uri): Boolean {
        return try {
            val cacheFile = java.io.File(context.cacheDir, "install_pending.apk")
            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            installDownloadedApkFile(context, cacheFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Error installing local APK via cache copy: ${e.message}", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Throwable) {
                false
            }
        }
    }

    fun installDownloadedApkFile(context: Context, file: java.io.File): Boolean {
        return try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error installing APK via FileProvider: ${e.message}", e)
            false
        }
    }

    suspend fun downloadApkToFile(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(downloadUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("استجابة السيرفر: ${connection.responseCode}"))
            }

            val fileLength = connection.contentLength
            val cacheFile = java.io.File(context.cacheDir, "update_downloaded.apk")
            if (cacheFile.exists()) cacheFile.delete()

            connection.inputStream.use { input ->
                java.io.FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val percent = ((total * 100) / fileLength).toInt()
                            onProgress(percent.coerceIn(0, 100))
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            onProgress(100)
            Result.success(cacheFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Direct download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun downloadAndInstallUpdate(context: Context, updateInfo: AppUpdateInfo) {
        val rawUrl = updateInfo.downloadUrl.trim()
        val isWebLink = rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)

        // Mark as acknowledged on this device
        setAcknowledgedVersion(context, updateInfo.remoteVersionCode, updateInfo.remoteVersionName)

        if (!isWebLink) {
            Toast.makeText(context, "الرابط غير متاح حالياً", Toast.LENGTH_SHORT).show()
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
