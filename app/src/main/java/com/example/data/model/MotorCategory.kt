package com.example.data.model

import com.google.firebase.Timestamp

data class MotorCategory(
    val id: String = "",
    val name: String = "",
    val orderIndex: Int = 0,
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "orderIndex" to orderIndex
        )
    }
}
