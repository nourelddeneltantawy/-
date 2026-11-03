package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AppRatingItem(
    val userEmail: String = "",
    val userName: String = "",
    val rating: Int = 5,
    val review: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AppStatsData(
    val totalUsersCount: Long = 1L, // Real users who opened the app
    val averageRating: Double = 5.0, // Real average of submitted ratings
    val totalRatingsCount: Long = 0L, // Real count of submitted reviews
    val currentUserRating: Int = 0,
    val currentUserReview: String = ""
)

object AppStatsAndRatingManager {
    private const val TAG = "AppStatsAndRating"
    private const val PREFS_NAME = "albaraa_app_stats_prefs"
    private const val KEY_TOTAL_USERS = "real_total_users"
    private const val KEY_AVG_RATING = "real_avg_rating"
    private const val KEY_TOTAL_RATINGS = "real_total_ratings"
    private const val KEY_USER_RATING = "my_rating"
    private const val KEY_USER_REVIEW = "my_review"
    private const val KEY_HAS_RECORDED_VISIT = "has_recorded_visit_real_v2"

    private val _stats = MutableStateFlow(AppStatsData())
    val stats: StateFlow<AppStatsData> = _stats.asStateFlow()

    private var ratingsListenerRegistration: ListenerRegistration? = null
    private var statsDocListenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context, currentUserEmail: String?) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Load from cache for immediate UI rendering
        val cachedUsers = prefs.getLong(KEY_TOTAL_USERS, 1L)
        val cachedAvg = prefs.getFloat(KEY_AVG_RATING, 5.0f).toDouble()
        val cachedRatingsCount = prefs.getLong(KEY_TOTAL_RATINGS, 0L)
        val cachedUserRating = prefs.getInt(KEY_USER_RATING, 0)
        val cachedUserReview = prefs.getString(KEY_USER_REVIEW, "") ?: ""

        _stats.value = AppStatsData(
            totalUsersCount = cachedUsers,
            averageRating = cachedAvg,
            totalRatingsCount = cachedRatingsCount,
            currentUserRating = cachedUserRating,
            currentUserReview = cachedUserReview
        )

        // 2. Record this visit in Firestore once per device
        recordVisitOnce(appContext, currentUserEmail, prefs)

        // 3. Attach real-time snapshot listener on real ratings and stats
        if (!isInitialized) {
            attachFirestoreListener(appContext, currentUserEmail)
            isInitialized = true
        }
    }

    private fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "dev_${UUID.randomUUID().toString().take(8)}"
        } catch (_: Throwable) {
            "dev_${UUID.randomUUID().toString().take(8)}"
        }
    }

    private fun recordVisitOnce(context: Context, userEmail: String?, prefs: SharedPreferences) {
        val hasRecorded = prefs.getBoolean(KEY_HAS_RECORDED_VISIT, false)
        val deviceId = getDeviceId(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                // Record in visitors collection
                db.collection("app_visitors").document(deviceId)
                    .set(
                        mapOf(
                            "deviceId" to deviceId,
                            "userEmail" to (userEmail ?: ""),
                            "lastActive" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()

                if (!hasRecorded) {
                    db.collection("app_stats").document("usage")
                        .set(
                            mapOf("totalUsersCount" to FieldValue.increment(1)),
                            SetOptions.merge()
                        ).await()
                    prefs.edit().putBoolean(KEY_HAS_RECORDED_VISIT, true).apply()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to record visitor: ${e.message}")
            }
        }
    }

    private fun attachFirestoreListener(context: Context, currentUserEmail: String?) {
        try {
            val db = FirebaseInitializer.getFirestore(context)

            // Listen to total user counts in stats doc
            statsDocListenerRegistration?.remove()
            statsDocListenerRegistration = db.collection("app_stats").document("usage")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Stats snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val count = snapshot.getLong("totalUsersCount") ?: 1L
                        _stats.value = _stats.value.copy(
                            totalUsersCount = maxOf(count, 1L)
                        )
                        saveCache(context)
                    }
                }

            // Real-time listener on real submitted ratings collection
            ratingsListenerRegistration?.remove()
            ratingsListenerRegistration = db.collection("app_ratings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Ratings listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val count = snapshot.size().toLong()
                        val ratingsList = snapshot.documents.mapNotNull { it.getLong("rating")?.toDouble() }
                        val avg = if (ratingsList.isNotEmpty()) {
                            val rawAvg = ratingsList.average()
                            (Math.round(rawAvg * 10.0) / 10.0).coerceIn(1.0, 5.0)
                        } else {
                            5.0
                        }

                        _stats.value = _stats.value.copy(
                            totalRatingsCount = count,
                            averageRating = avg
                        )
                        saveCache(context)
                    }
                }

            // Check current user's rating if logged in
            if (!currentUserEmail.isNullOrBlank()) {
                val cleanEmailKey = currentUserEmail.replace(".", "_").replace("@", "_at_")
                db.collection("app_ratings").document(cleanEmailKey).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val r = doc.getLong("rating")?.toInt() ?: 0
                            val rev = doc.getString("review") ?: ""
                            _stats.value = _stats.value.copy(
                                currentUserRating = r,
                                currentUserReview = rev
                            )
                            saveCache(context)
                        }
                    }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting up stats listener", e)
        }
    }

    fun submitRating(
        context: Context,
        userEmail: String,
        userName: String,
        rating: Int,
        review: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val safeEmail = if (userEmail.isBlank()) "guest_user" else userEmail.trim()
        val cleanEmailKey = safeEmail.replace(".", "_")
            .replace("@", "_at_")
            .replace("/", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")

        _stats.value = _stats.value.copy(
            currentUserRating = rating,
            currentUserReview = review
        )
        saveCache(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val ratingDoc = mapOf(
                    "userEmail" to safeEmail,
                    "userName" to userName.ifBlank { safeEmail.substringBefore("@") },
                    "rating" to rating.coerceIn(1, 5),
                    "review" to review,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("app_ratings").document(cleanEmailKey).set(ratingDoc).await()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    try {
                        onComplete?.invoke(true)
                    } catch (_: Throwable) {}
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to submit rating", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    try {
                        onComplete?.invoke(false)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun saveCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_TOTAL_USERS, _stats.value.totalUsersCount)
                .putFloat(KEY_AVG_RATING, _stats.value.averageRating.toFloat())
                .putLong(KEY_TOTAL_RATINGS, _stats.value.totalRatingsCount)
                .putInt(KEY_USER_RATING, _stats.value.currentUserRating)
                .putString(KEY_USER_REVIEW, _stats.value.currentUserReview)
                .apply()
        } catch (_: Throwable) {}
    }
}
