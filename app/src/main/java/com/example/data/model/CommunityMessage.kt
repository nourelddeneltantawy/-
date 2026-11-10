package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Model representing a message in the Technicians Group Chat / Community Forum.
 */
data class CommunityMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val senderEmail: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val messageText: String = "",
    val imageUrl: String = "",
    val replyToId: String? = null,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> list of user emails
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "senderId" to senderId,
            "senderEmail" to senderEmail.trim().lowercase(),
            "senderName" to senderName,
            "senderAvatar" to senderAvatar,
            "messageText" to messageText,
            "imageUrl" to imageUrl,
            "replyToId" to replyToId,
            "replyToName" to replyToName,
            "replyToText" to replyToText,
            "reactions" to reactions,
            "createdAt" to createdAt
        )
    }

    val formattedTime: String
        get() {
            return try {
                val sdf = SimpleDateFormat("hh:mm a", Locale("ar"))
                sdf.format(Date(createdAt))
            } catch (_: Throwable) {
                ""
            }
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): CommunityMessage {
            val reactionsRaw = map["reactions"] as? Map<String, List<*>>
            val reactionsClean = mutableMapOf<String, List<String>>()
            reactionsRaw?.forEach { (emoji, list) ->
                reactionsClean[emoji] = list.filterIsInstance<String>()
            }

            return CommunityMessage(
                id = id,
                senderId = (map["senderId"] as? String) ?: "",
                senderEmail = (map["senderEmail"] as? String) ?: "",
                senderName = (map["senderName"] as? String) ?: "مهندس صيانة",
                senderAvatar = (map["senderAvatar"] as? String) ?: "",
                messageText = (map["messageText"] as? String) ?: "",
                imageUrl = (map["imageUrl"] as? String) ?: "",
                replyToId = map["replyToId"] as? String,
                replyToName = map["replyToName"] as? String,
                replyToText = map["replyToText"] as? String,
                reactions = reactionsClean,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
