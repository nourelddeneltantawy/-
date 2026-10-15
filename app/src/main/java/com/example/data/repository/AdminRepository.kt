package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.AdminRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseFirestore?,
    private val auth: FirebaseAuth? = null
) {

    constructor(context: Context) : this(
        MotorRepository.createFirestore(context),
        try {
            val app = com.example.data.FirebaseInitializer.ensureFirebaseInitialized(context)
            if (app != null) {
                FirebaseAuth.getInstance(app)
            } else {
                Firebase.auth
            }
        } catch (_: Throwable) {
            try {
                FirebaseAuth.getInstance()
            } catch (_: Throwable) {
                null
            }
        }
    )

    companion object {
        val SUPER_ADMIN_EMAILS = setOf(
            "nwnwraldynaltantawy@gmail.com".lowercase(),
            "nwraldynmstfymhmd@gmail.com".lowercase()
        )

        fun isSuperAdminEmail(email: String?): Boolean {
            if (email == null) return false
            return SUPER_ADMIN_EMAILS.contains(email.trim().lowercase())
        }
    }

    fun isCurrentUserSuperAdmin(): Boolean {
        val email = try { auth?.currentUser?.email } catch (_: Throwable) { null }
        return isSuperAdminEmail(email)
    }

    suspend fun checkIsAdmin(email: String?): Boolean {
        if (email == null) return false
        val cleanEmail = email.trim().lowercase()
        if (SUPER_ADMIN_EMAILS.contains(cleanEmail)) return true
        val database = db ?: return false

        return try {
            val doc = database.collection("admins").document(cleanEmail).get().await()
            doc.exists()
        } catch (_: Exception) {
            false
        }
    }

    fun observeAdmins(): Flow<List<AdminRecord>> = flow {
        val path = "admins"
        val database = db
        if (!isCurrentUserSuperAdmin() || database == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            database.collection(path)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AdminRecord::class.java)
                    }
                }
                .catch { error ->
                    emit(emptyList())
                }
        )
    }

    suspend fun addAdmin(rawEmail: String, addedBy: String): Result<Unit> {
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val cleanEmail = rawEmail.trim().lowercase()
        val path = "admins/$cleanEmail"
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("يرجى إدخال بريد إلكتروني صالح"))
        }

        return try {
            val isSuper = SUPER_ADMIN_EMAILS.contains(cleanEmail)
            val payload = mapOf(
                "email" to cleanEmail,
                "addedBy" to addedBy,
                "role" to if (isSuper) "super_admin" else "admin",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            database.collection("admins").document(cleanEmail).set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeAdmin(rawEmail: String): Result<Unit> {
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val cleanEmail = rawEmail.trim().lowercase()
        val path = "admins/$cleanEmail"

        if (SUPER_ADMIN_EMAILS.contains(cleanEmail)) {
            return Result.failure(IllegalStateException("لا يمكن إزالة أو تعديل رتبة المشرف الرئيسي (Super Admin)"))
        }

        return try {
            database.collection("admins").document(cleanEmail).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun bootstrapSuperAdminsIfMissing() {
        val database = db ?: return
        if (!isCurrentUserSuperAdmin()) return

        for (superEmail in SUPER_ADMIN_EMAILS) {
            try {
                val doc = database.collection("admins").document(superEmail).get().await()
                if (!doc.exists()) {
                    val payload = mapOf(
                        "email" to superEmail,
                        "addedBy" to "System / Super Admin",
                        "role" to "super_admin",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    database.collection("admins").document(superEmail).set(payload).await()
                }
            } catch (_: Exception) {}
        }
    }
}
