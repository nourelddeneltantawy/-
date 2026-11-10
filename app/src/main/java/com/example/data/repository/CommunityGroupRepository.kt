package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CommunityMessage
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository for managing Technicians Group Chat messages and real-time interactions:
 * - Real-time Firestore sync on `community_group_messages`
 * - Local SharedPreferences caching
 * - Send message, reply, image upload, reactions, and delete
 */
object CommunityGroupRepository {
    private const val TAG = "CommunityGroupRepo"
    private const val PREFS_NAME = "albaraa_community_group_prefs"
    private const val KEY_MESSAGES_JSON = "group_messages_json"

    private val _messages = MutableStateFlow<List<CommunityMessage>>(emptyList())
    val messages: StateFlow<List<CommunityMessage>> = _messages.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext ?: context

        // 1. Load from local cache for instant UI rendering
        val cached = loadCachedMessages(appContext)
        if (cached.isNotEmpty()) {
            _messages.value = cached
        } else {
            // Seed a welcome message
            val welcome = listOf(
                CommunityMessage(
                    id = "welcome_msg",
                    senderId = "admin",
                    senderEmail = "nwraldynmstfymhmd@gmail.com",
                    senderName = "م. البراء إلكترونيك",
                    messageText = "أهلاً ومرحباً بجميع الزملاء الفنيين والمهندسين في جروب المحادثة والتواصل! يمكنك هنا طرح استفساراتك الفنية، مشاركة صور الأعطال والبوردات، والتعاون مع زملائك في المهنة 🤝⚡",
                    createdAt = System.currentTimeMillis() - 3600000L
                )
            )
            _messages.value = welcome
            saveCachedMessages(appContext, welcome)
        }

        // 2. Attach real-time snapshot listener on Firestore
        try {
            val db = FirebaseInitializer.getFirestore(appContext)
            listenerRegistration = db.collection("community_group_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            CommunityMessage.fromMap(doc.id, data)
                        }
                        if (list.isNotEmpty()) {
                            _messages.value = list
                            saveCachedMessages(appContext, list)
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to attach real-time Firestore listener for group messages", e)
        }
        isInitialized = true
    }

    fun sendMessage(
        context: Context,
        senderId: String,
        senderEmail: String,
        senderName: String,
        senderAvatar: String,
        messageText: String,
        imageUrl: String = "",
        replyToId: String? = null,
        replyToName: String? = null,
        replyToText: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val newMsg = CommunityMessage(
            senderId = senderId,
            senderEmail = senderEmail.trim().lowercase(),
            senderName = senderName.ifBlank { senderEmail.substringBefore("@") },
            senderAvatar = senderAvatar,
            messageText = messageText.trim(),
            imageUrl = imageUrl.trim(),
            replyToId = replyToId,
            replyToName = replyToName,
            replyToText = replyToText,
            createdAt = System.currentTimeMillis()
        )

        val currentList = _messages.value.toMutableList()
        currentList.add(newMsg)
        _messages.value = currentList
        saveCachedMessages(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("community_group_messages")
                    .document(newMsg.id)
                    .set(newMsg.toMap())
                    .await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error sending group message to Firestore: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true) // local optimistic update already in place
                }
            }
        }
    }

    fun toggleReaction(
        context: Context,
        messageId: String,
        emoji: String,
        userEmail: String
    ) {
        val appContext = context.applicationContext ?: context
        val safeEmail = userEmail.trim().lowercase()
        if (safeEmail.isBlank() || safeEmail == "guest") return

        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index < 0) return

        val msg = currentList[index]
        val mutableReactions = msg.reactions.mapValues { it.value.toMutableList() }.toMutableMap()
        val currentUsers = mutableReactions[emoji] ?: mutableListOf()

        if (currentUsers.contains(safeEmail)) {
            currentUsers.remove(safeEmail)
            if (currentUsers.isEmpty()) {
                mutableReactions.remove(emoji)
            } else {
                mutableReactions[emoji] = currentUsers
            }
        } else {
            currentUsers.add(safeEmail)
            mutableReactions[emoji] = currentUsers
        }

        val updated = msg.copy(reactions = mutableReactions)
        currentList[index] = updated
        _messages.value = currentList
        saveCachedMessages(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("community_group_messages")
                    .document(messageId)
                    .update("reactions", mutableReactions)
                    .await()
            } catch (e: Throwable) {
                Log.e(TAG, "Error updating reactions on Firestore: ${e.message}")
            }
        }
    }

    fun deleteMessage(
        context: Context,
        messageId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _messages.value.toMutableList()
        currentList.removeAll { it.id == messageId }
        _messages.value = currentList
        saveCachedMessages(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("community_group_messages")
                    .document(messageId)
                    .delete()
                    .await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting message from Firestore: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            }
        }
    }

    private fun loadCachedMessages(context: Context): List<CommunityMessage> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CommunityMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "reactions") {
                        val reactObj = obj.optJSONObject(k)
                        val rMap = mutableMapOf<String, List<String>>()
                        reactObj?.keys()?.forEach { emoji ->
                            val userArr = reactObj.optJSONArray(emoji)
                            val userList = mutableListOf<String>()
                            if (userArr != null) {
                                for (j in 0 until userArr.length()) {
                                    userList.add(userArr.optString(j))
                                }
                            }
                            rMap[emoji] = userList
                        }
                        map[k] = rMap
                    } else {
                        map[k] = obj.opt(k)
                    }
                }
                list.add(CommunityMessage.fromMap(obj.optString("id"), map))
            }
            list
        } catch (e: Throwable) {
            Log.e(TAG, "Failed loading cached messages: ${e.message}")
            emptyList()
        }
    }

    private fun saveCachedMessages(context: Context, messages: List<CommunityMessage>) {
        try {
            val array = JSONArray()
            messages.takeLast(50).forEach { msg ->
                val obj = JSONObject()
                obj.put("id", msg.id)
                obj.put("senderId", msg.senderId)
                obj.put("senderEmail", msg.senderEmail)
                obj.put("senderName", msg.senderName)
                obj.put("senderAvatar", msg.senderAvatar)
                obj.put("messageText", msg.messageText)
                obj.put("imageUrl", msg.imageUrl)
                obj.put("replyToId", msg.replyToId ?: "")
                obj.put("replyToName", msg.replyToName ?: "")
                obj.put("replyToText", msg.replyToText ?: "")
                obj.put("createdAt", msg.createdAt)

                val reactObj = JSONObject()
                msg.reactions.forEach { (emoji, users) ->
                    val userArr = JSONArray()
                    users.forEach { userArr.put(it) }
                    reactObj.put(emoji, userArr)
                }
                obj.put("reactions", reactObj)
                array.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_MESSAGES_JSON, array.toString()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed saving messages to prefs: ${e.message}")
        }
    }
}
