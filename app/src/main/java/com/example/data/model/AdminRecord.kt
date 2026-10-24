package com.example.data.model

import com.google.firebase.Timestamp

data class AdminRecord(
    val email: String = "",
    val addedBy: String = "",
    val role: String = "admin",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
