package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val city: String = "",
    val profession: String = "فني صيانة غسالات", // فني صيانة غسالات، مهندس إلكترونيات، تاجر قطع غيار، مركز صيانة
    val bio: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "email" to email,
            "displayName" to displayName,
            "phone" to phone,
            "avatarUrl" to avatarUrl,
            "city" to city,
            "profession" to profession,
            "bio" to bio,
            "updatedAt" to (updatedAt ?: Timestamp.now())
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): UserProfile {
            return UserProfile(
                userId = map["userId"] as? String ?: id,
                email = map["email"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                avatarUrl = map["avatarUrl"] as? String ?: "",
                city = map["city"] as? String ?: "",
                profession = map["profession"] as? String ?: "فني صيانة غسالات",
                bio = map["bio"] as? String ?: "",
                createdAt = map["createdAt"] as? Timestamp,
                updatedAt = map["updatedAt"] as? Timestamp
            )
        }
    }
}
