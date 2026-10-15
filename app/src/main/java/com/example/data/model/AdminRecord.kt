package com.example.data.model

import com.google.firebase.Timestamp

data class AdminRecord(
    val email: String = "",
    val addedBy: String = "",
    val role: String = "admin",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isProtectedSuperAdmin: Boolean
        get() = email.equals("nwnwraldynaltantawy@gmail.com", ignoreCase = true) ||
                email.equals("nwraldynmstfymhmd@gmail.com", ignoreCase = true)

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "email" to email,
            "addedBy" to addedBy,
            "role" to role
        )
        return map
    }
}
