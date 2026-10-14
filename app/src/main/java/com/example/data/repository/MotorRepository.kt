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
            try {
                com.example.FirebaseInitializer.ensureInitialized(context)
            } catch (_: Throwable) {}

            return try {
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                if (dbId.isNotBlank()) {
                    FirebaseFirestore.getInstance(dbId)
                } else {
                    FirebaseFirestore.getInstance()
                }
            } catch (_: Throwable) {
                try {
                    FirebaseFirestore.getInstance()
                } catch (_: Throwable) {
                    val app = com.example.FirebaseInitializer.ensureInitialized(context)
                    FirebaseFirestore.getInstance(app)
                }
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
                            remoteMotors.sortedByDescending { it.createdAt ?: it.updatedAt }
                        } else {
                            DefaultMotorsData.getDefaultMotors(auth.currentUser?.uid ?: "guest")
                        }
                    }
                    .catch { error ->
                        android.util.Log.w("MotorRepository", "Firestore observeMotors error, using fallback: ${error.message}")
                        if (error is Exception) {
                            handleFirestoreError(error, OperationType.LIST, path)
                        }
                        emit(DefaultMotorsData.getDefaultMotors(auth.currentUser?.uid ?: "guest"))
                    }
            )
        } catch (e: Throwable) {
            android.util.Log.e("MotorRepository", "observeMotors exception", e)
            emit(DefaultMotorsData.getDefaultMotors(auth.currentUser?.uid ?: "guest"))
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
        val path = "motors/$motorId"
        return try {
            db.collection("motors").document(motorId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
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
