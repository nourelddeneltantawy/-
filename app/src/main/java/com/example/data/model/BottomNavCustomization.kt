package com.example.data.model

data class BottomNavCustomization(
    val id: String = "",
    val title: String = "",
    val isHidden: Boolean = false, // When true, hidden from non-admin regular users
    val isLocked: Boolean = false  // When true, locked/maintenance mode
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "isHidden" to isHidden,
            "isLocked" to isLocked
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): BottomNavCustomization {
            return BottomNavCustomization(
                id = id,
                title = map["title"] as? String ?: "",
                isHidden = (map["isHidden"] as? Boolean) ?: false,
                isLocked = (map["isLocked"] as? Boolean) ?: false
            )
        }
    }
}
