package com.example.data.model

data class AppUpdateInfo(
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "1.0",
    val remoteVersionCode: Int = 1,
    val remoteVersionName: String = "1.0",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false,
    val hasUpdate: Boolean = false
)
