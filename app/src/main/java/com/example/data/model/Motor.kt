package com.example.data.model

import com.google.firebase.Timestamp

data class Motor(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val motorType: String = "Universal",
    val notes: String = "",
    val imageUrl: String = "",
    val pinouts: List<Pinout> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "userId" to userId,
            "name" to name,
            "brand" to brand,
            "model" to model,
            "motorType" to motorType,
            "notes" to notes,
            "imageUrl" to imageUrl,
            "pinouts" to pinouts.map { it.toMap() }
        )
        return map
    }
}
