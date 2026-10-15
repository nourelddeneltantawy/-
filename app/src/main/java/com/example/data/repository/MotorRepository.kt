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
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MotorRepository(
    private val db: FirebaseFirestore?,
    private val context: Context? = null
) {

    companion object {
        fun createFirestore(context: Context): FirebaseFirestore? {
            return try {
                val app = com.example.data.FirebaseInitializer.ensureFirebaseInitialized(context)
                val dbId = try {
                    context.applicationContext.getString(R.string.firestore_database_id)
                } catch (_: Throwable) { "" }

                if (app != null) {
                    if (dbId.isNotBlank()) {
                        FirebaseFirestore.getInstance(app, dbId)
                    } else {
                        FirebaseFirestore.getInstance(app)
                    }
                } else {
                    if (dbId.isNotBlank()) {
                        FirebaseFirestore.getInstance(dbId)
                    } else {
                        FirebaseFirestore.getInstance()
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MotorRepository", "Could not create Firestore instance: ${t.message}", t)
                null
            }
        }
    }

    constructor(db: FirebaseFirestore) : this(db as FirebaseFirestore?, null)

    constructor(context: Context) : this(createFirestore(context), context)

    private val auth: com.google.firebase.auth.FirebaseAuth? by lazy {
        try {
            val app = context?.let { com.example.data.FirebaseInitializer.ensureFirebaseInitialized(it) }
            if (app != null) {
                com.google.firebase.auth.FirebaseAuth.getInstance(app)
            } else {
                Firebase.auth
            }
        } catch (_: Throwable) {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeMotors(): Flow<List<Motor>> = flow {
        val currentUid = try { auth?.currentUser?.uid ?: "guest" } catch (_: Throwable) { "guest" }
        val database = db
        if (database == null) {
            emit(DefaultMotorsData.getDefaultMotors(currentUid))
            return@flow
        }

        val path = "motors"
        try {
            emitAll(
                database.collection(path)
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
                            remoteMotors.sortedByDescending { it.createdAt ?: it.updatedAt }
                        } else {
                            DefaultMotorsData.getDefaultMotors(currentUid)
                        }
                    }
                    .catch { error ->
                        android.util.Log.w("MotorRepository", "Firestore observeMotors error, using fallback: ${error.message}")
                        if (error is Exception) {
                            handleFirestoreError(error, OperationType.LIST, path)
                        }
                        emit(DefaultMotorsData.getDefaultMotors(currentUid))
                    }
            )
        } catch (e: Throwable) {
            android.util.Log.e("MotorRepository", "observeMotors exception", e)
            emit(DefaultMotorsData.getDefaultMotors(currentUid))
        }
    }

    suspend fun getMotorById(motorId: String): Result<Motor> {
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val path = "motors/$motorId"
        return try {
            val doc = database.collection("motors").document(motorId).get().await()
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
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val uid = requireUserId()
        val motorId = if (motor.id.isNotBlank()) motor.id else UUID.randomUUID().toString()
        val docRef = database.collection("motors").document(motorId)
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
                        "notes" to motor.notes,
                        "imageUrl" to motor.imageUrl,
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

    suspend fun deleteMotor(motorId: String): Result<Unit> {
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val path = "motors/$motorId"
        return try {
            database.collection("motors").document(motorId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultMotorsIfEmpty(): Result<Int> {
        val database = db ?: return Result.failure(IllegalStateException("قاعدة البيانات السحابية غير متوفرة حالياً"))
        val uid = requireUserId()
        val path = "motors"
        return try {
            val existingDocs = database.collection(path)
                .limit(1)
                .get()
                .await()

            if (existingDocs.isEmpty) {
                val defaults = DefaultMotorsData.getDefaultMotors(uid)
                for (motor in defaults) {
                    val motorId = motor.id
                    val docRef = database.collection("motors").document(motorId)
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
