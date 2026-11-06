package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.MarketplacePost
import com.example.data.model.UserProfile
import com.example.util.FirebaseInitializer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import java.util.UUID

object MarketplaceRepository {
    private const val TAG = "MarketplaceRepo"
    private const val PREFS_NAME = "marketplace_prefs"
    private const val KEY_CACHED_POSTS = "cached_posts"
    private const val KEY_USER_PROFILE = "user_profile_data"

    // Auto-cleanup threshold for completed items (e.g. 3 days)
    private const val COMPLETED_EXPIRY_MS = 3L * 24 * 60 * 60 * 1000

    private val _posts = MutableStateFlow<List<MarketplacePost>>(emptyList())
    val posts: StateFlow<List<MarketplacePost>> = _posts.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private fun getProfilePrefKey(email: String?): String {
        val clean = email?.trim()?.lowercase() ?: "anonymous"
        return "user_profile_${clean.replace(".", "_")}"
    }

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context, currentUserEmail: String?, currentUserId: String?) {
        val appContext = context.applicationContext ?: context
        loadCachedPosts(appContext)

        if (!isInitialized) {
            isInitialized = true
            startRealtimePostsSync(appContext)
        }

        switchUser(appContext, currentUserEmail, currentUserId)
    }

    fun switchUser(context: Context, currentUserEmail: String?, currentUserId: String?) {
        val appContext = context.applicationContext ?: context
        val email = currentUserEmail?.trim()?.lowercase()
        if (email.isNullOrBlank()) {
            _currentUserProfile.value = null
            return
        }

        // 1. Immediately load local cache for THIS specific email
        loadCachedProfileForEmail(appContext, email)

        // 2. Fetch fresh profile from cloud
        loadUserProfileFromCloud(appContext, email, currentUserId)
    }

    private fun startRealtimePostsSync(context: Context) {
        try {
            val db = FirebaseInitializer.getFirestore(context)
            listenerRegistration = db.collection("marketplace_posts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Marketplace posts listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val now = System.currentTimeMillis()
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                MarketplacePost.fromMap(doc.id, doc.data ?: emptyMap())
                            } catch (_: Throwable) {
                                null
                            }
                        }

                        // Filter out old completed posts (older than 3 days) and auto-delete them in background
                        val validList = list.filter { post ->
                            if (post.isCompleted && post.completedTimestamp != null) {
                                val age = now - post.completedTimestamp
                                if (age > COMPLETED_EXPIRY_MS) {
                                    // Trigger delete in background
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            db.collection("marketplace_posts").document(post.id).delete()
                                        } catch (_: Throwable) {}
                                    }
                                    return@filter false
                                }
                            }
                            true
                        }.sortedByDescending { it.createdTimestamp }

                        _posts.value = validList
                        saveCachedPosts(context, validList)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start posts realtime sync", e)
        }
    }

    fun loadUserProfileFromCloud(context: Context, email: String?, userId: String?) {
        val key = email?.trim()?.lowercase() ?: userId ?: return
        if (key.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val doc = db.collection("user_profiles").document(key).get().await()
                if (doc.exists()) {
                    val p = UserProfile.fromMap(doc.id, doc.data ?: emptyMap())
                    _currentUserProfile.value = p
                    saveCachedProfile(context, p)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Cloud profile fetch error: ${e.message}")
            }
        }
    }

    suspend fun fetchAuthorProfile(context: Context, key: String): UserProfile? {
        if (key.isBlank()) return null
        return try {
            val db = FirebaseInitializer.getFirestore(context)
            val doc = db.collection("user_profiles").document(key.trim().lowercase()).get().await()
            if (doc.exists()) {
                UserProfile.fromMap(doc.id, doc.data ?: emptyMap())
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun fetchAllProfiles(context: Context): List<UserProfile> {
        return try {
            val db = FirebaseInitializer.getFirestore(context)
            val snapshot = db.collection("user_profiles").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    UserProfile.fromMap(doc.id, doc.data ?: emptyMap())
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch all profiles", e)
            emptyList()
        }
    }

    fun saveUserProfile(
        context: Context,
        profile: UserProfile,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        _currentUserProfile.value = profile
        saveCachedProfile(appContext, profile)

        val key = profile.email.ifBlank { profile.userId }.trim().lowercase()
        if (key.isBlank()) {
            onComplete?.invoke(true)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("user_profiles").document(key)
                    .set(profile.toMap())
                    .await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to save user profile", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun savePost(
        context: Context,
        post: MarketplacePost,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _posts.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == post.id }
        if (existingIdx != -1) {
            current[existingIdx] = post
        } else {
            current.add(0, post)
        }
        _posts.value = current
        saveCachedPosts(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("marketplace_posts").document(post.id)
                    .set(post.toMap())
                    .await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving marketplace post", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun togglePostCompleted(
        context: Context,
        postId: String,
        isCompleted: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        val updated = post.copy(
            isCompleted = isCompleted,
            completedTimestamp = if (isCompleted) System.currentTimeMillis() else null
        )
        savePost(appContext, updated, onComplete)
    }

    fun deletePost(
        context: Context,
        postId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val current = _posts.value.toMutableList()
        current.removeAll { it.id == postId }
        _posts.value = current
        saveCachedPosts(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("marketplace_posts").document(postId).delete().await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting marketplace post", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private fun saveCachedPosts(context: Context, list: List<MarketplacePost>) {
        try {
            val arr = JSONArray()
            list.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("userId", p.userId)
                obj.put("userEmail", p.userEmail)
                obj.put("userName", p.userName)
                obj.put("userPhone", p.userPhone)
                obj.put("userAvatarUrl", p.userAvatarUrl)
                obj.put("userCity", p.userCity)
                obj.put("userProfession", p.userProfession)
                obj.put("postType", p.postType)
                obj.put("title", p.title)
                obj.put("description", p.description)
                obj.put("price", p.price)
                obj.put("mediaUrl", p.mediaUrl)
                obj.put("mediaType", p.mediaType)
                obj.put("isCompleted", p.isCompleted)
                obj.put("createdTimestamp", p.createdTimestamp)
                p.completedTimestamp?.let { obj.put("completedTimestamp", it) }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_POSTS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedPosts(context: Context) {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_POSTS, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MarketplacePost(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    userId = obj.optString("userId", ""),
                    userEmail = obj.optString("userEmail", ""),
                    userName = obj.optString("userName", ""),
                    userPhone = obj.optString("userPhone", ""),
                    userAvatarUrl = obj.optString("userAvatarUrl", ""),
                    userCity = obj.optString("userCity", ""),
                    userProfession = obj.optString("userProfession", ""),
                    postType = obj.optString("postType", "SELL"),
                    title = obj.optString("title", ""),
                    description = obj.optString("description", ""),
                    price = obj.optString("price", ""),
                    mediaUrl = obj.optString("mediaUrl", ""),
                    mediaType = obj.optString("mediaType", "IMAGE"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis()),
                    completedTimestamp = if (obj.has("completedTimestamp")) obj.optLong("completedTimestamp") else null
                )
            }
            if (list.isNotEmpty()) _posts.value = list
        } catch (_: Throwable) {}
    }

    private fun saveCachedProfile(context: Context, profile: UserProfile) {
        val key = getProfilePrefKey(profile.email.ifBlank { profile.userId })
        try {
            val obj = JSONObject()
            obj.put("userId", profile.userId)
            obj.put("email", profile.email)
            obj.put("displayName", profile.displayName)
            obj.put("phone", profile.phone)
            obj.put("avatarUrl", profile.avatarUrl)
            obj.put("city", profile.city)
            obj.put("profession", profile.profession)
            obj.put("bio", profile.bio)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, obj.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedProfileForEmail(context: Context, email: String) {
        val key = getProfilePrefKey(email)
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
        if (json.isNullOrBlank()) {
            _currentUserProfile.value = null
            return
        }
        try {
            val obj = JSONObject(json)
            val p = UserProfile(
                userId = obj.optString("userId", ""),
                email = obj.optString("email", email),
                displayName = obj.optString("displayName", ""),
                phone = obj.optString("phone", ""),
                avatarUrl = obj.optString("avatarUrl", ""),
                city = obj.optString("city", ""),
                profession = obj.optString("profession", "فني صيانة غسالات"),
                bio = obj.optString("bio", "")
            )
            // Ensure the cached profile really belongs to this email
            if (p.email.equals(email, ignoreCase = true) || p.email.isBlank()) {
                _currentUserProfile.value = p
            } else {
                _currentUserProfile.value = null
            }
        } catch (_: Throwable) {
            _currentUserProfile.value = null
        }
    }
}
