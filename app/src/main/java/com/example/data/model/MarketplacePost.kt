package com.example.data.model

data class MarketplacePost(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userAvatarUrl: String = "",
    val userCity: String = "",
    val userProfession: String = "",
    val isUserVerified: Boolean = false,
    val postType: String = "SELL", // "SELL" (بيع كارتة) or "BUY" (طلب شراء)
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "IMAGE", // "IMAGE", "VIDEO", "NONE"
    val isCompleted: Boolean = false, // false = متاح, true = تم البيع / تم الشراء
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "userEmail" to userEmail,
            "userName" to userName,
            "userPhone" to userPhone,
            "userAvatarUrl" to userAvatarUrl,
            "userCity" to userCity,
            "userProfession" to userProfession,
            "isUserVerified" to isUserVerified,
            "postType" to postType,
            "title" to title,
            "description" to description,
            "price" to price,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "isCompleted" to isCompleted,
            "createdTimestamp" to createdTimestamp,
            "completedTimestamp" to completedTimestamp
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): MarketplacePost {
            return MarketplacePost(
                id = id,
                userId = map["userId"] as? String ?: "",
                userEmail = map["userEmail"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                userPhone = map["userPhone"] as? String ?: "",
                userAvatarUrl = map["userAvatarUrl"] as? String ?: "",
                userCity = map["city"] as? String ?: (map["userCity"] as? String ?: ""),
                userProfession = map["profession"] as? String ?: (map["userProfession"] as? String ?: ""),
                isUserVerified = (map["isUserVerified"] as? Boolean) ?: false,
                postType = map["postType"] as? String ?: "SELL",
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                price = map["price"] as? String ?: "",
                mediaUrl = map["mediaUrl"] as? String ?: "",
                mediaType = map["mediaType"] as? String ?: "IMAGE",
                isCompleted = (map["isCompleted"] as? Boolean) ?: false,
                createdTimestamp = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                completedTimestamp = (map["completedTimestamp"] as? Number)?.toLong()
            )
        }
    }
}
