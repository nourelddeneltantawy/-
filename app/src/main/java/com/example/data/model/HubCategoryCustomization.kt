package com.example.data.model

data class HubCategoryCustomization(
    val id: String = "",
    val customTitle: String = "",
    val customSubtitle: String = "",
    val customImageUrl: String = "",
    val customIconName: String = "", // e.g. "contact", "memory", "cable", "lock", "hub", "wash", "cleaning", "settings", "build"
    val isLocked: Boolean = false,
    val orderIndex: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "customTitle" to customTitle,
            "customSubtitle" to customSubtitle,
            "customImageUrl" to customImageUrl,
            "customIconName" to customIconName,
            "isLocked" to isLocked,
            "orderIndex" to orderIndex
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): HubCategoryCustomization {
            return HubCategoryCustomization(
                id = id,
                customTitle = map["customTitle"] as? String ?: "",
                customSubtitle = map["customSubtitle"] as? String ?: "",
                customImageUrl = map["customImageUrl"] as? String ?: "",
                customIconName = map["customIconName"] as? String ?: "",
                isLocked = (map["isLocked"] as? Boolean) ?: false,
                orderIndex = (map["orderIndex"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

