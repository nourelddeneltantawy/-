package com.example.data

import android.content.Context
import android.content.SharedPreferences
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

data class AppRatingItem(
    val userEmail: String = "",
    val userName: String = "",
    val rating: Int = 5,
    val review: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AppStatsData(
    val totalUsersCount: Long = 1480L, // Baseline starter count
    val averageRating: Double = 4.9,
    val totalRatingsCount: Long = 342L,
    val currentUserRating: Int = 0,
    val currentUserReview: String = ""
)

object AppStatsAndRatingManager {
    private const val TAG = "AppStatsAndRating"
    private const val PREFS_NAME = "albaraa_app_stats_prefs"
    private const val KEY_TOTAL_USERS = "total_users"
    private const val KEY_AVG_RATING = "avg_rating"
    private const val KEY_TOTAL_RATINGS = "total_ratings"
    private const val KEY_USER_RATING = "my_rating"
    private const val KEY_USER_REVIEW = "my_review"
    private const val KEY_HAS_RECORDED_VISIT = "has_recorded_visit"

    private val _stats = MutableStateFlow(AppStatsData())
    val stats: StateFlow<AppStatsData> = _stats.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false

    fun init(context: Context, currentUserEmail: String?) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Load from cache for immediate UI rendering
        val cachedUsers = prefs.getLong(KEY_TOTAL_USERS, 1480L)
        val cachedAvg = prefs.getFloat(KEY_AVG_RATING, 4.9f).toDouble()
        val cachedRatingsCount = prefs.getLong(KEY_TOTAL_RATINGS, 342L)
        val cachedUserRating = prefs.getInt(KEY_USER_RATING, 0)
        val cachedUserReview = prefs.getString(KEY_USER_REVIEW, "") ?: ""

        _stats.value = AppStatsData(
            totalUsersCount = cachedUsers,
            averageRating = cachedAvg,
            totalRatingsCount = cachedRatingsCount,
            currentUserRating = cachedUserRating,
            currentUserReview = cachedUserReview
        )

        // 2. Record this visit/user in Firestore once per install
        recordVisitOnce(appContext, currentUserEmail, prefs)

        // 3. Attach real-time snapshot listener on stats document
        if (!isInitialized) {
            attachFirestoreListener(appContext, currentUserEmail)
            isInitialized = true
        }
    }

    private fun recordVisitOnce(context: Context, userEmail: String?, prefs: SharedPreferences) {
        val hasRecorded = prefs.getBoolean(KEY_HAS_RECORDED_VISIT, false)
        if (!hasRecorded) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = FirebaseInitializer.getFirestore(context)
                    db.collection("app_stats").document("usage")
                        .set(
                            mapOf("totalUsersCount" to FieldValue.increment(1)),
                            SetOptions.merge()
                        ).await()
                    prefs.edit().putBoolean(KEY_HAS_RECORDED_VISIT, true).apply()
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to increment user count: ${e.message}")
                }
            }
        }
    }

    private fun attachFirestoreListener(context: Context, currentUserEmail: String?) {
        try {
            val db = FirebaseInitializer.getFirestore(context)
            listenerRegistration?.remove()
            listenerRegistration = db.collection("app_stats").document("usage")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Stats snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val count = snapshot.getLong("totalUsersCount") ?: 1480L
                        val avg = snapshot.getDouble("averageRating") ?: 4.9
                        val ratingsCount = snapshot.getLong("totalRatingsCount") ?: 342L

                        _stats.value = _stats.value.copy(
                            totalUsersCount = maxOf(count, 1480L),
                            averageRating = avg,
                            totalRatingsCount = maxOf(ratingsCount, 342L)
                        )
                        saveCache(context)
                    }
                }

            // Also check current user's rating if logged in
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
        val cleanEmailKey = userEmail.replace(".", "_").replace("@", "_at_")

        _stats.value = _stats.value.copy(
            currentUserRating = rating,
            currentUserReview = review
        )
        saveCache(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                val ratingDoc = mapOf(
                    "userEmail" to userEmail,
                    "userName" to userName,
                    "rating" to rating,
                    "review" to review,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("app_ratings").document(cleanEmailKey).set(ratingDoc).await()

                // Recalculate or increment stats
                val newRatingsCount = _stats.value.totalRatingsCount + 1
                val newAvg = ((_stats.value.averageRating * _stats.value.totalRatingsCount) + rating) / newRatingsCount
                val roundedAvg = (Math.round(newAvg * 10.0) / 10.0).coerceIn(1.0, 5.0)

                db.collection("app_stats").document("usage").set(
                    mapOf(
                        "averageRating" to roundedAvg,
                        "totalRatingsCount" to FieldValue.increment(1)
                    ),
                    SetOptions.merge()
                ).await()

                _stats.value = _stats.value.copy(
                    averageRating = roundedAvg,
                    totalRatingsCount = newRatingsCount
                )
                saveCache(appContext)
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to submit rating", e)
                onComplete?.invoke(false)
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
