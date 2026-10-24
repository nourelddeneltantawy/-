package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.DefaultMotorsData
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.google.firebase.Firebase
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
import java.util.UUID

class MotorRepository(private val db: FirebaseFirestore) {

    companion object {
        fun createFirestore(context: Context): FirebaseFirestore {
            val app = com.example.util.FirebaseInitializer.ensureInitialized(context)
            val dbId = try {
                context.applicationContext.getString(R.string.firestore_database_id)
            } catch (_: Throwable) {
                ""
            }
            return try {
                if (app != null) {
                    if (dbId.isNotBlank()) FirebaseFirestore.getInstance(app, dbId) else FirebaseFirestore.getInstance(app)
                } else {
                    if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
                }
            } catch (_: Throwable) {
                if (app != null) FirebaseFirestore.getInstance(app) else FirebaseFirestore.getInstance()
            }
        }
    }

    constructor(context: Context) : this(createFirestore(context))

    private val auth by lazy {
        try {
            Firebase.auth
        } catch (_: Throwable) {
            com.google.firebase.auth.FirebaseAuth.getInstance()
        }
    }

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeMotors(): Flow<List<Motor>> = flow {
        val path = "motors"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        val remoteMotors = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(
                                    Motor::class.java,
                                    com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )?.copy(id = doc.id)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (remoteMotors.isNotEmpty()) {
                            remoteMotors.sortedWith(
                                compareBy<Motor> { it.orderIndex }
                                    .thenByDescending { it.createdAt ?: it.updatedAt }
                            )
                        } else {
                            emptyList()
                        }
                    }
                    .catch { error ->
                        android.util.Log.w("MotorRepository", "Firestore observeMotors error: ${error.message}")
                        if (error is Exception) {
                            handleFirestoreError(error, OperationType.LIST, path)
                        }
                        emit(emptyList())
                    }
            )
        } catch (e: Throwable) {
            android.util.Log.e("MotorRepository", "observeMotors exception", e)
            emit(emptyList())
        }
    }

    suspend fun getMotorById(motorId: String): Result<Motor> {
        val path = "motors/$motorId"
        return try {
            val doc = db.collection("motors").document(motorId).get().await()
            val motor = doc.toObject(Motor::class.java)?.copy(id = doc.id)
            if (motor != null) {
                Result.success(motor)
            } else {
                Result.failure(NoSuchElementException("Motor not found with id $motorId"))
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveMotor(motor: Motor): Result<String> {
        val uid = requireUserId()
        val motorId = if (motor.id.isNotBlank()) motor.id else UUID.randomUUID().toString()
        val docRef = db.collection("motors").document(motorId)
        val path = "motors/$motorId"

        return try {
            val isNew = motor.createdAt == null
            val payload = motor.toMap().toMutableMap()
            payload["id"] = motorId
            payload["userId"] = uid
            payload["updatedAt"] = FieldValue.serverTimestamp()

            if (isNew) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to motor.name,
                        "brand" to motor.brand,
                        "model" to motor.model,
                        "motorType" to motor.motorType,
                        "category" to motor.category,
                        "notes" to motor.notes,
                        "imageUrl" to motor.imageUrl,
                        "orderIndex" to motor.orderIndex,
                        "pinouts" to motor.pinouts.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(motorId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMotorsOrder(orderedMotors: List<Motor>): Result<Unit> {
        val path = "motors"
        return try {
            val batch = db.batch()
            orderedMotors.forEachIndexed { index, motor ->
                if (motor.id.isNotBlank()) {
                    val docRef = db.collection(path).document(motor.id)
                    batch.update(docRef, "orderIndex", index)
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMotor(motorId: String): Result<Unit> {
        val path = "motors/$motorId"
        return try {
            db.collection("motors").document(motorId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // Categories Management
    fun observeCategories(): Flow<List<com.example.data.model.MotorCategory>> = flow {
        val path = "categories"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                val cat = doc.toObject(com.example.data.model.MotorCategory::class.java)
                                cat?.copy(id = doc.id)
                            } catch (_: Throwable) {
                                null
                            }
                        }.sortedWith(compareBy<com.example.data.model.MotorCategory> { it.orderIndex }.thenBy { it.name })
                    }
                    .catch { error ->
                        android.util.Log.w("MotorRepository", "Categories observe error: ${error.message}")
                        emit(emptyList())
                    }
            )
        } catch (e: Throwable) {
            emit(emptyList())
        }
    }

    suspend fun saveCategory(category: com.example.data.model.MotorCategory): Result<String> {
        val catId = if (category.id.isNotBlank()) category.id else UUID.randomUUID().toString()
        val docRef = db.collection("categories").document(catId)
        return try {
            val payload = category.toMap().toMutableMap()
            payload["id"] = catId
            payload["createdAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
            Result.success(catId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedDefaultMotorsIfEmpty(): Result<Int> {
        val uid = requireUserId()
        val path = "motors"
        return try {
            val existingDocs = db.collection(path)
                .limit(1)
                .get()
                .await()

            if (existingDocs.isEmpty) {
                val defaults = DefaultMotorsData.getDefaultMotors(uid)
                for (motor in defaults) {
                    val motorId = motor.id
                    val docRef = db.collection("motors").document(motorId)
                    val payload = motor.toMap().toMutableMap()
                    payload["id"] = motorId
                    payload["userId"] = uid
                    payload["createdAt"] = FieldValue.serverTimestamp()
                    payload["updatedAt"] = FieldValue.serverTimestamp()
                    docRef.set(payload).await()
                }
                Result.success(defaults.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
