package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
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

        /**
         * Safely parse a Motor from Firestore DocumentSnapshot.
         * Explicitly avoids POJO deserializer bugs (such as HashMap to Pinout ClassCastException,
         * or Long to Int conversion issues) which cause app crashes on save.
         */
        fun parseMotorFromSnapshot(doc: DocumentSnapshot): Motor? {
            return try {
                val data = doc.data ?: return null
                val id = doc.id
                val name = data["name"] as? String ?: ""
                val brand = data["brand"] as? String ?: ""
                val model = data["model"] as? String ?: ""
                val motorType = data["motorType"] as? String ?: "Universal"
                val notes = data["notes"] as? String ?: ""
                val imageUrl = data["imageUrl"] as? String ?: ""
                val category = data["category"] as? String ?: ""
                val orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0
                val createdAt = data["createdAt"] as? Timestamp
                val updatedAt = data["updatedAt"] as? Timestamp
                val userId = data["userId"] as? String ?: ""

                val rawPinouts = data["pinouts"] as? List<*> ?: emptyList<Any>()
                val pinoutsList = rawPinouts.mapNotNull { item ->
                    when (item) {
                        is Pinout -> item
                        is Map<*, *> -> {
                            Pinout(
                                pinNumber = (item["pinNumber"] as? Number)?.toInt() ?: 1,
                                name = item["name"] as? String ?: "",
                                wireColor = item["wireColor"] as? String ?: "",
                                colorHex = item["colorHex"] as? String ?: "#3B82F6",
                                secondaryColorHex = item["secondaryColorHex"] as? String,
                                secondaryColorName = item["secondaryColorName"] as? String,
                                functionDesc = item["functionDesc"] as? String ?: ""
                            )
                        }
                        else -> null
                    }
                }

                Motor(
                    id = id,
                    userId = userId,
                    name = name,
                    brand = brand,
                    model = model,
                    motorType = motorType,
                    notes = notes,
                    imageUrl = imageUrl,
                    pinouts = pinoutsList,
                    orderIndex = orderIndex,
                    category = category,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            } catch (e: Exception) {
                Log.e("MotorRepository", "Error parsing motor document ${doc.id}", e)
                null
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
                            parseMotorFromSnapshot(doc)
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
                        Log.w("MotorRepository", "Firestore observeMotors error: ${error.message}")
                        if (error is Exception) {
                            handleFirestoreError(error, OperationType.LIST, path)
                        }
                        emit(emptyList())
                    }
            )
        } catch (e: Throwable) {
            Log.e("MotorRepository", "observeMotors exception", e)
            emit(emptyList())
        }
    }

    suspend fun getMotorById(motorId: String): Result<Motor> {
        val path = "motors/$motorId"
        return try {
            val doc = db.collection("motors").document(motorId).get().await()
            val motor = parseMotorFromSnapshot(doc)
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
            val isNew = motor.createdAt == null || motor.id.isBlank()
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

    fun observeCategories(): Flow<List<com.example.data.model.MotorCategory>> = flow {
        val path = "categories"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                com.example.data.model.MotorCategory(
                                    id = doc.id,
                                    name = data["name"] as? String ?: "",
                                    orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0,
                                    createdAt = data["createdAt"] as? com.google.firebase.Timestamp
                                )
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        list.sortedBy { it.orderIndex }
                    }
                    .catch {
                        emit(emptyList())
                    }
            )
        } catch (_: Throwable) {
            emit(emptyList())
        }
    }

    suspend fun saveCategory(category: com.example.data.model.MotorCategory): Result<String> {
        val catId = if (category.id.isNotBlank()) category.id else UUID.randomUUID().toString()
        val path = "categories/$catId"
        return try {
            val payload = category.toMap().toMutableMap()
            payload["id"] = catId
            payload["createdAt"] = FieldValue.serverTimestamp()
            db.collection("categories").document(catId).set(payload).await()
            Result.success(catId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        val path = "categories/$categoryId"
        return try {
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Harness Management (ضفائر وتوصيلات الغسالة) ---

    private fun parseHarnessFromSnapshot(doc: DocumentSnapshot): com.example.data.model.Harness? {
        val data = doc.data ?: return null
        val id = doc.id
        val name = data["name"] as? String ?: ""
        val brand = data["brand"] as? String ?: ""
        val model = data["model"] as? String ?: ""
        val notes = data["notes"] as? String ?: ""
        val boardImageUrl = data["boardImageUrl"] as? String ?: ""
        val bottomImageUrl = data["bottomImageUrl"] as? String ?: ""
        val orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0
        val createdAt = data["createdAt"] as? Timestamp
        val updatedAt = data["updatedAt"] as? Timestamp

        fun parsePin(item: Any?): com.example.data.model.HarnessPin? {
            if (item !is Map<*, *>) return null
            return com.example.data.model.HarnessPin(
                pinNumber = (item["pinNumber"] as? Number)?.toInt() ?: 1,
                label = item["label"] as? String ?: "",
                socketName = item["socketName"] as? String ?: "سوكيت 1",
                wireColor = item["wireColor"] as? String ?: "",
                colorHex = item["colorHex"] as? String ?: "#3B82F6",
                secondaryColorHex = item["secondaryColorHex"] as? String,
                secondaryColorName = item["secondaryColorName"] as? String,
                connectedComponent = item["connectedComponent"] as? String ?: "ماتور",
                targetPinNumber = (item["targetPinNumber"] as? Number)?.toInt(),
                signalType = item["signalType"] as? String ?: "",
                isBlank = (item["isBlank"] as? Boolean) ?: false,
                pinX = (item["pinX"] as? Number)?.toFloat() ?: 0f,
                pinY = (item["pinY"] as? Number)?.toFloat() ?: 0f
            )
        }

        val rawBoardPins = data["boardPins"] as? List<*> ?: emptyList<Any>()
        val boardPins = rawBoardPins.mapNotNull { parsePin(it) }

        val rawBottomPins = data["bottomPins"] as? List<*> ?: emptyList<Any>()
        val bottomPins = rawBottomPins.mapNotNull { parsePin(it) }

        return com.example.data.model.Harness(
            id = id,
            name = name,
            brand = brand,
            model = model,
            notes = notes,
            boardImageUrl = boardImageUrl,
            bottomImageUrl = bottomImageUrl,
            boardPins = boardPins,
            bottomPins = bottomPins,
            orderIndex = orderIndex,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun observeHarnesses(): Flow<List<com.example.data.model.Harness>> = flow {
        val path = "harnesses"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        val remoteList = snapshot.documents.mapNotNull { doc ->
                            try {
                                parseHarnessFromSnapshot(doc)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (remoteList.isNotEmpty()) {
                            remoteList.sortedBy { it.orderIndex }
                        } else {
                            com.example.data.DefaultHarnessData.sampleHarnesses
                        }
                    }
                    .catch {
                        emit(com.example.data.DefaultHarnessData.sampleHarnesses)
                    }
            )
        } catch (_: Throwable) {
            emit(com.example.data.DefaultHarnessData.sampleHarnesses)
        }
    }

    suspend fun saveHarness(harness: com.example.data.model.Harness): Result<String> {
        val id = if (harness.id.isNotBlank()) harness.id else UUID.randomUUID().toString()
        val path = "harnesses/$id"
        return try {
            val payload = harness.toMap().toMutableMap()
            payload["id"] = id
            payload["updatedAt"] = FieldValue.serverTimestamp()
            if (harness.createdAt == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            db.collection("harnesses").document(id).set(payload).await()
            Result.success(id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteHarness(harnessId: String): Result<Unit> {
        val path = "harnesses/$harnessId"
        return try {
            db.collection("harnesses").document(harnessId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Appliance Parts Management (لوكات الباب، ليفلات الماء، طلمبات) ---

    private fun parsePartFromSnapshot(doc: DocumentSnapshot): com.example.data.model.AppliancePart? {
        val data = doc.data ?: return null
        val id = doc.id
        val name = data["name"] as? String ?: ""
        val partType = data["partType"] as? String ?: "لوك باب"
        val brand = data["brand"] as? String ?: ""
        val model = data["model"] as? String ?: ""
        val resistanceValue = data["resistanceValue"] as? String ?: ""
        val operatingVoltage = data["operatingVoltage"] as? String ?: "220V AC"
        val testMethod = data["testMethod"] as? String ?: ""
        val notes = data["notes"] as? String ?: ""
        val orderIndex = (data["orderIndex"] as? Number)?.toInt() ?: 0
        val createdAt = data["createdAt"] as? Timestamp
        val updatedAt = data["updatedAt"] as? Timestamp

        val rawPinouts = data["pinouts"] as? List<*> ?: emptyList<Any>()
        val pinoutsList = rawPinouts.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            Pinout(
                pinNumber = (item["pinNumber"] as? Number)?.toInt() ?: 1,
                name = item["name"] as? String ?: "",
                wireColor = item["wireColor"] as? String ?: "",
                colorHex = item["colorHex"] as? String ?: "#3B82F6",
                secondaryColorHex = item["secondaryColorHex"] as? String,
                secondaryColorName = item["secondaryColorName"] as? String,
                functionDesc = item["functionDesc"] as? String ?: ""
            )
        }

        return com.example.data.model.AppliancePart(
            id = id,
            name = name,
            partType = partType,
            brand = brand,
            model = model,
            pinouts = pinoutsList,
            resistanceValue = resistanceValue,
            operatingVoltage = operatingVoltage,
            testMethod = testMethod,
            notes = notes,
            orderIndex = orderIndex,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun observeParts(): Flow<List<com.example.data.model.AppliancePart>> = flow {
        val path = "appliance_parts"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        val remoteList = snapshot.documents.mapNotNull { doc ->
                            try {
                                parsePartFromSnapshot(doc)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (remoteList.isNotEmpty()) {
                            remoteList.sortedBy { it.orderIndex }
                        } else {
                            com.example.data.DefaultHarnessData.sampleParts
                        }
                    }
                    .catch {
                        emit(com.example.data.DefaultHarnessData.sampleParts)
                    }
            )
        } catch (_: Throwable) {
            emit(com.example.data.DefaultHarnessData.sampleParts)
        }
    }

    suspend fun savePart(part: com.example.data.model.AppliancePart): Result<String> {
        val id = if (part.id.isNotBlank()) part.id else UUID.randomUUID().toString()
        val path = "appliance_parts/$id"
        return try {
            val payload = part.toMap().toMutableMap()
            payload["id"] = id
            payload["updatedAt"] = FieldValue.serverTimestamp()
            if (part.createdAt == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            db.collection("appliance_parts").document(id).set(payload).await()
            Result.success(id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePart(partId: String): Result<Unit> {
        val path = "appliance_parts/$partId"
        return try {
            db.collection("appliance_parts").document(partId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}

