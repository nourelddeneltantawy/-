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
    val imageUrls: List<String> = emptyList(),
    val pinouts: List<Pinout> = emptyList(),
    val orderIndex: Int = 0,
    val category: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun getAllImages(): List<String> {
        val list = mutableListOf<String>()
        list.addAll(imageUrls.filter { it.isNotBlank() })
        if (imageUrl.isNotBlank() && !list.contains(imageUrl)) {
            list.add(0, imageUrl)
        }
        return list.distinct()
    }

    fun toMap(): Map<String, Any> {
        val allImages = getAllImages()
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "userId" to userId,
            "name" to name,
            "brand" to brand,
            "model" to model,
            "motorType" to motorType,
            "notes" to notes,
            "imageUrl" to (allImages.firstOrNull() ?: imageUrl),
            "imageUrls" to allImages,
            "orderIndex" to orderIndex,
            "category" to category,
            "pinouts" to pinouts.map { it.toMap() }
        )
        return map
    }
}
