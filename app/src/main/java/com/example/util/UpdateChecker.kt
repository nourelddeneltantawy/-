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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_UPDATE_URL = "custom_update_url"

    // Default raw GitHub JSON endpoint
    const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/nouraldeenaltantawy/washer-motors-app/main/version.json"

    fun getUpdateUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_UPDATE_URL
    }

    fun setCustomUpdateUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    fun resetUpdateUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_UPDATE_URL).apply()
    }

    suspend fun checkUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val updateUrl = getUpdateUrl(context)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(updateUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WasherMotorsApp/${BuildConfig.VERSION_NAME}")
                useCaches = false
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val json = JSONObject(response)

                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "1.0")
                val downloadUrl = json.optString("downloadUrl", "")
                val releaseNotes = json.optString("releaseNotes", "")
                val forceUpdate = json.optBoolean("forceUpdate", false)

                val currentCode = BuildConfig.VERSION_CODE
                val currentName = BuildConfig.VERSION_NAME

                val hasUpdate = remoteVersionCode > currentCode

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
                Log.w(TAG, "Update check failed with HTTP code: $responseCode")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    fun downloadAndInstallUpdate(context: Context, updateInfo: AppUpdateInfo) {
        val downloadUrl = updateInfo.downloadUrl.trim()
        if (downloadUrl.isBlank()) {
            Toast.makeText(context, "رابط التحميل غير متوفر في بيانات التحديث", Toast.LENGTH_SHORT).show()
            return
        }

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

        // 2. If it's a direct APK file link, also trigger Android DownloadManager for convenient direct notification download
        if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
            try {
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setTitle("دليل مواتير الغسالات v${updateInfo.remoteVersionName}")
                    setDescription("جاري تحميل ملف التحديث APK...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "WasherMotors_v${updateInfo.remoteVersionName}.apk"
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
