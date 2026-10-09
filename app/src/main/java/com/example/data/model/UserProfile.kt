package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "technician",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
