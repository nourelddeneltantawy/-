package com.example.data.model

import com.google.firebase.Timestamp

data class AdminRecord(
    val email: String = "",
    val addedBy: String = "",
    val role: String = "admin",
    val permissions: List<String> = listOf("all"), // "all", "motors", "harnesses", "parts", "programming", "settings"
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
