package com.example.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.data.model.ConnectionPinItem
import com.example.data.model.FirmwareDump
import com.example.data.model.FirmwareFolder
import com.example.data.model.LicenseKeyRecord
import com.example.data.model.ProgrammerConnection
import com.example.data.model.ProgrammerSoftware
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

object ProgrammingRepository {
    private const val TAG = "ProgrammingRepo"
    private const val PREFS_NAME = "albaraa_programming_prefs"
    private const val KEY_USER_ACTIVATED_LICENSES = "user_activated_licenses"
    private const val KEY_CACHED_SOFTWARES = "cached_softwares"
    private const val KEY_CACHED_FOLDERS = "cached_folders"
    private const val KEY_CACHED_DUMPS = "cached_dumps"
    private const val KEY_CACHED_CONNECTIONS = "cached_connections"
    private const val KEY_CACHED_KEYS = "cached_keys"

    private val _softwares = MutableStateFlow<List<ProgrammerSoftware>>(emptyList())
    val softwares: StateFlow<List<ProgrammerSoftware>> = _softwares.asStateFlow()

    private val _folders = MutableStateFlow<List<FirmwareFolder>>(emptyList())
    val folders: StateFlow<List<FirmwareFolder>> = _folders.asStateFlow()

    private val _dumps = MutableStateFlow<List<FirmwareDump>>(emptyList())
    val dumps: StateFlow<List<FirmwareDump>> = _dumps.asStateFlow()

    private val _connections = MutableStateFlow<List<ProgrammerConnection>>(emptyList())
    val connections: StateFlow<List<ProgrammerConnection>> = _connections.asStateFlow()

    private val _licenseKeys = MutableStateFlow<List<LicenseKeyRecord>>(emptyList())
    val licenseKeys: StateFlow<List<LicenseKeyRecord>> = _licenseKeys.asStateFlow()

    private val _unlockedScopes = MutableStateFlow<Set<String>>(emptySet())
    val unlockedScopes: StateFlow<Set<String>> = _unlockedScopes.asStateFlow()

    private var listeners = mutableListOf<ListenerRegistration>()
    private var isInitialized = false

    private var activeEmail: String? = null

    fun init(context: Context, userEmail: String? = null) {
        val appContext = context.applicationContext ?: context
        activeEmail = userEmail
        if (!isInitialized) {
            isInitialized = true
            loadLocalCache(appContext)

            if (_folders.value.isEmpty()) {
                _folders.value = getInitialFolders()
                saveFoldersToPrefs(appContext, _folders.value)
            }
            if (_softwares.value.isEmpty()) {
                _softwares.value = getInitialSoftwares()
                saveSoftwaresToPrefs(appContext, _softwares.value)
            }
            if (_connections.value.isEmpty()) {
                _connections.value = getInitialConnections()
                saveConnectionsToPrefs(appContext, _connections.value)
            }

            bootstrapInitialDataToCloud(appContext)
            startRealtimeSync(appContext)
        }

        loadLocalLicenses(appContext, userEmail)
        if (!userEmail.isNullOrBlank()) {
            syncUserCloudLicenses(appContext, userEmail)
        }
    }

    private fun bootstrapInitialDataToCloud(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                for (s in getInitialSoftwares()) {
                    val doc = db.collection("programming_software").document(s.id).get().await()
                    if (!doc.exists()) {
                        db.collection("programming_software").document(s.id).set(s.toMap()).await()
                    }
                }
                for (f in getInitialFolders()) {
                    val doc = db.collection("programming_folders").document(f.id).get().await()
                    if (!doc.exists()) {
                        db.collection("programming_folders").document(f.id).set(f.toMap()).await()
                    }
                }
                for (c in getInitialConnections()) {
                    val doc = db.collection("programming_connections").document(c.id).get().await()
                    if (!doc.exists()) {
                        db.collection("programming_connections").document(c.id).set(c.toMap()).await()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Bootstrap data to cloud non-fatal error: ${e.message}")
            }
        }
    }

    fun loadLocalLicenses(context: Context, userEmail: String? = null) {
        activeEmail = userEmail
        val cleanEmail = userEmail?.trim()?.lowercase() ?: "guest"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("${KEY_USER_ACTIVATED_LICENSES}_$cleanEmail", emptySet()) ?: emptySet()
        _unlockedScopes.value = set
    }

    /**
     * Restore all purchased/activated licenses for this user email from Firestore!
     * Ensures that when a user logs in on ANY device or reinstalls the app, all their
     * bought keys and unlocked folders/softwares are automatically restored!
     */
    fun syncUserCloudLicenses(context: Context, userEmail: String) {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val snapshot = db.collection("license_keys")
                    .whereEqualTo("clientEmail", cleanEmail)
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val newSet = _unlockedScopes.value.toMutableSet()
                    for (doc in snapshot.documents) {
                        val record = LicenseKeyRecord.fromMap(doc.id, doc.data ?: emptyMap())
                        if (record.isActivated) {
                            if (record.isFullAccess || record.targetScope == "ALL_FIRMWARE") {
                                newSet.add("ALL_FIRMWARE")
                            }
                            if (record.targetScope.isNotBlank()) {
                                newSet.add(record.targetScope)
                            }
                            newSet.addAll(record.allowedFolderIds)
                            newSet.addAll(record.allowedSoftwareIds)
                            newSet.add(record.licenseKey)
                        }
                    }
                    _unlockedScopes.value = newSet
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putStringSet("${KEY_USER_ACTIVATED_LICENSES}_$cleanEmail", newSet).apply()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "User cloud license sync error: ${e.message}")
            }
        }
    }

    fun isUnlocked(
        scope: String = "",
        folderId: String = "",
        isFree: Boolean = false,
        isAdmin: Boolean = false
    ): Boolean {
        if (isAdmin || isFree) return true
        val current = _unlockedScopes.value
        if (current.contains("ALL_FIRMWARE")) return true
        if (scope.isNotBlank() && current.contains(scope)) return true
        if (folderId.isNotBlank() && current.contains(folderId)) return true
        return false
    }

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_${UUID.randomUUID().toString().take(8)}"
        } catch (_: Throwable) {
            "device_${UUID.randomUUID().toString().take(8)}"
        }
    }

    // =========================================================================
    // REAL FILE STORAGE & FREE/LOCK TOGGLES
    // =========================================================================

    fun saveUploadedFile(context: Context, uri: android.net.Uri, originalName: String): Pair<String, Long> {
        return try {
            val dir = java.io.File(context.filesDir, "programming_files")
            if (!dir.exists()) dir.mkdirs()
            val cleanName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_").ifBlank { "firmware_${System.currentTimeMillis()}.bin" }
            val destFile = java.io.File(dir, "${System.currentTimeMillis()}_$cleanName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Pair(destFile.absolutePath, destFile.length())
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving uploaded file", e)
            Pair("", 0L)
        }
    }

    fun toggleFolderFree(context: Context, folderId: String) {
        val folder = _folders.value.firstOrNull { it.id == folderId } ?: return
        val newFree = !folder.isFree
        val updated = folder.copy(isFree = newFree, isLocked = !newFree)
        saveFolder(context, updated)
    }

    fun toggleSoftwareFree(context: Context, softwareId: String) {
        val software = _softwares.value.firstOrNull { it.id == softwareId } ?: return
        val newFree = !software.isFree
        val updated = software.copy(isFree = newFree, isLocked = !newFree, requiresLicense = !newFree)
        saveSoftware(context, updated)
    }

    fun toggleDumpFree(context: Context, dumpId: String) {
        val dump = _dumps.value.firstOrNull { it.id == dumpId } ?: return
        val newFree = !dump.isFree
        val updated = dump.copy(isFree = newFree, requiresLicense = !newFree)
        saveDump(context, updated)
    }

    // =========================================================================
    // LICENSE KEY ACTIVATION & GENERATION
    // =========================================================================

    fun maskEmail(email: String): String {
        val clean = email.trim()
        val atIdx = clean.indexOf('@')
        val name = if (atIdx > 0) clean.substring(0, atIdx) else clean
        val prefix = name.take(3)
        return "$prefix***"
    }

    suspend fun activateLicenseKey(
        context: Context,
        rawKey: String,
        currentUserEmail: String? = null
    ): Result<LicenseKeyRecord> {
        val appContext = context.applicationContext ?: context
        val cleanKey = rawKey.trim().uppercase()
        if (cleanKey.isBlank()) {
            return Result.failure(Exception("يرجى إدخال مفتاح التنشيط أولاً"))
        }

        return try {
            val db = FirebaseInitializer.getFirestore(appContext)
            val docRef = db.collection("license_keys").document(cleanKey)
            val snapshot = docRef.get().await()

            val keyRecord: LicenseKeyRecord = if (!snapshot.exists()) {
                val localKey = _licenseKeys.value.firstOrNull { it.licenseKey.equals(cleanKey, ignoreCase = true) }
                if (localKey != null) {
                    localKey
                } else {
                    return Result.failure(Exception("مفتاح التنشيط غير صحيح أو غير مسجل في النظام"))
                }
            } else {
                LicenseKeyRecord.fromMap(snapshot.id, snapshot.data ?: emptyMap())
            }

            // Check email binding if key specifies an email
            if (keyRecord.clientEmail.isNotBlank()) {
                val userEmail = currentUserEmail?.trim()?.lowercase() ?: ""
                val boundEmail = keyRecord.clientEmail.trim().lowercase()
                if (userEmail.isBlank()) {
                    return Result.failure(Exception("هذا المفتاح مرتبط بحساب إلكتروني (${maskEmail(keyRecord.clientEmail)}). يرجى تسجيل الدخول بنفس الحساب أولاً لتفعيله."))
                }
                if (userEmail != boundEmail) {
                    return Result.failure(Exception("هذا المفتاح مخصص للحساب (${maskEmail(boundEmail)}) ولا يتطابق مع بريدك الحالي (${maskEmail(userEmail)})."))
                }
            }

            val currentDeviceId = getDeviceId(appContext)

            if (keyRecord.isActivated && keyRecord.activatedDeviceId.isNotBlank() && keyRecord.activatedDeviceId != currentDeviceId) {
                return Result.failure(Exception("هذا المفتاح تم تفعيله مسبقاً على جهاز آخر ولا يمكن استخدامه مجدداً"))
            }

            // Mark activated
            val updated = keyRecord.copy(
                isActivated = true,
                activatedDeviceId = currentDeviceId,
                activatedTimestamp = System.currentTimeMillis()
            )
            try {
                docRef.set(updated.toMap()).await()
            } catch (e: Throwable) {
                Log.w(TAG, "Cloud sync on activation: ${e.message}")
            }

            recordActivationSuccess(appContext, updated)
            Result.success(updated)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to activate license key", e)
            Result.failure(Exception("فشل تفعيل المفتاح: ${e.localizedMessage ?: "تأكد من الاتصال بالإنترنت"}"))
        }
    }

    private fun recordActivationSuccess(context: Context, record: LicenseKeyRecord) {
        val set = _unlockedScopes.value.toMutableSet()
        if (record.isFullAccess || record.targetScope == "ALL_FIRMWARE") {
            set.add("ALL_FIRMWARE")
        }
        if (record.targetScope.isNotBlank()) {
            set.add(record.targetScope)
        }
        set.addAll(record.allowedFolderIds)
        set.addAll(record.allowedSoftwareIds)
        set.add(record.licenseKey)
        _unlockedScopes.value = set

        val cleanEmail = (activeEmail ?: record.clientEmail).trim().lowercase().ifBlank { "guest" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("${KEY_USER_ACTIVATED_LICENSES}_$cleanEmail", set).apply()
    }

    fun generateUniqueKey(
        context: Context,
        clientName: String,
        clientPhone: String,
        clientEmail: String = "",
        targetScope: String = "ALL_FIRMWARE",
        targetScopeName: String = "جميع السوفتات والبرامج",
        allowedFolderIds: List<String> = emptyList(),
        allowedSoftwareIds: List<String> = emptyList(),
        isFullAccess: Boolean = true,
        notes: String = "",
        onComplete: (LicenseKeyRecord?) -> Unit
    ) {
        val appContext = context.applicationContext ?: context
        val random = SecureRandom()
        val part1 = (1000 + random.nextInt(9000)).toString()
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part2 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val keyString = "ALB-SOFT-$part1-$part2"

        val record = LicenseKeyRecord(
            id = keyString,
            licenseKey = keyString,
            clientName = clientName.trim(),
            clientPhone = clientPhone.trim(),
            clientEmail = clientEmail.trim().lowercase(),
            targetScope = targetScope,
            targetScopeName = targetScopeName,
            allowedFolderIds = allowedFolderIds,
            allowedSoftwareIds = allowedSoftwareIds,
            isFullAccess = isFullAccess,
            isActivated = false,
            createdTimestamp = System.currentTimeMillis(),
            notes = notes.trim()
        )

        // 1. Save locally IMMEDIATELY so key is never lost
        val current = _licenseKeys.value.toMutableList()
        current.removeAll { it.licenseKey == keyString }
        current.add(0, record)
        _licenseKeys.value = current
        saveKeysToPrefs(appContext, current)

        CoroutineScope(Dispatchers.Main).launch {
            onComplete(record)
        }

        // 2. Sync to Firestore in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("license_keys").document(keyString)
                    .set(record.toMap())
                    .await()
            } catch (e: Throwable) {
                Log.w(TAG, "Key saved locally, cloud sync error: ${e.message}")
            }
        }
    }

    fun updateLicenseKey(context: Context, updatedRecord: LicenseKeyRecord, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _licenseKeys.value.toMutableList()
        val idx = current.indexOfFirst { it.licenseKey == updatedRecord.licenseKey }
        if (idx != -1) {
            current[idx] = updatedRecord
        } else {
            current.add(0, updatedRecord)
        }
        _licenseKeys.value = current
        saveKeysToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("license_keys").document(updatedRecord.licenseKey)
                    .set(updatedRecord.toMap())
                    .await()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error updating license key", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun deleteLicenseKey(context: Context, keyId: String, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("license_keys").document(keyId).delete().await()
                val current = _licenseKeys.value.toMutableList()
                current.removeAll { it.id == keyId || it.licenseKey == keyId }
                _licenseKeys.value = current
                saveKeysToPrefs(appContext, current)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting license key", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    // =========================================================================
    // CRUD FOR SOFTWARES, FOLDERS, DUMPS, CONNECTIONS
    // =========================================================================

    fun saveSoftware(context: Context, item: ProgrammerSoftware, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _softwares.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            current[idx] = item
        } else {
            current.add(0, item)
        }
        _softwares.value = current
        saveSoftwaresToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_software").document(item.id).set(item.toMap()).await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving software", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteSoftware(context: Context, id: String, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _softwares.value.toMutableList()
        current.removeAll { it.id == id }
        _softwares.value = current
        saveSoftwaresToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_software").document(id).delete().await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting software", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun saveFolder(context: Context, item: FirmwareFolder, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _folders.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            current[idx] = item
        } else {
            current.add(item)
        }
        _folders.value = current
        saveFoldersToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_folders").document(item.id).set(item.toMap()).await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving folder", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteFolder(context: Context, id: String, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _folders.value.toMutableList()
        current.removeAll { it.id == id }
        _folders.value = current
        saveFoldersToPrefs(appContext, current)

        // Also delete dumps inside folder
        val curDumps = _dumps.value.toMutableList()
        curDumps.removeAll { it.folderId == id }
        _dumps.value = curDumps
        saveDumpsToPrefs(appContext, curDumps)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_folders").document(id).delete().await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting folder", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun saveDump(context: Context, item: FirmwareDump, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _dumps.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            current[idx] = item
        } else {
            current.add(0, item)
        }
        _dumps.value = current
        saveDumpsToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_dumps").document(item.id).set(item.toMap()).await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving dump", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteDump(context: Context, id: String, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _dumps.value.toMutableList()
        current.removeAll { it.id == id }
        _dumps.value = current
        saveDumpsToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_dumps").document(id).delete().await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting dump", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun saveConnection(context: Context, item: ProgrammerConnection, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _connections.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            current[idx] = item
        } else {
            current.add(0, item)
        }
        _connections.value = current
        saveConnectionsToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_connections").document(item.id).set(item.toMap()).await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving connection diagram", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun deleteConnection(context: Context, id: String, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        val current = _connections.value.toMutableList()
        current.removeAll { it.id == id }
        _connections.value = current
        saveConnectionsToPrefs(appContext, current)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("programming_connections").document(id).delete().await()
                onComplete?.invoke(true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting connection diagram", e)
                onComplete?.invoke(false)
            }
        }
    }

    // =========================================================================
    // REAL-TIME FIRESTORE SYNC
    // =========================================================================

    private fun startRealtimeSync(context: Context) {
        try {
            val db = FirebaseInitializer.getFirestore(context)

            // 1. Softwares
            listeners.add(
                db.collection("programming_software").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.map { doc ->
                            ProgrammerSoftware.fromMap(doc.id, doc.data ?: emptyMap())
                        }
                        if (remoteList.isNotEmpty()) {
                            val current = _softwares.value.toMutableList()
                            for (remote in remoteList) {
                                val idx = current.indexOfFirst { it.id == remote.id }
                                if (idx != -1) {
                                    current[idx] = remote
                                } else {
                                    current.add(remote)
                                }
                            }
                            _softwares.value = current
                            saveSoftwaresToPrefs(context, current)
                        }
                    }
                }
            )

            // 2. Folders
            listeners.add(
                db.collection("programming_folders").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.map { doc ->
                            FirmwareFolder.fromMap(doc.id, doc.data ?: emptyMap())
                        }
                        if (remoteList.isNotEmpty()) {
                            val current = _folders.value.toMutableList()
                            for (remote in remoteList) {
                                val idx = current.indexOfFirst { it.id == remote.id }
                                if (idx != -1) {
                                    current[idx] = remote
                                } else {
                                    current.add(remote)
                                }
                            }
                            val sorted = current.sortedBy { it.orderIndex }
                            _folders.value = sorted
                            saveFoldersToPrefs(context, sorted)
                        }
                    }
                }
            )

            // 3. Dumps
            listeners.add(
                db.collection("programming_dumps").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.map { doc ->
                            FirmwareDump.fromMap(doc.id, doc.data ?: emptyMap())
                        }
                        if (remoteList.isNotEmpty()) {
                            val current = _dumps.value.toMutableList()
                            for (remote in remoteList) {
                                val idx = current.indexOfFirst { it.id == remote.id }
                                if (idx != -1) {
                                    current[idx] = remote
                                } else {
                                    current.add(0, remote)
                                }
                            }
                            _dumps.value = current
                            saveDumpsToPrefs(context, current)
                        }
                    }
                }
            )

            // 4. Connections
            listeners.add(
                db.collection("programming_connections").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.map { doc ->
                            ProgrammerConnection.fromMap(doc.id, doc.data ?: emptyMap())
                        }
                        if (remoteList.isNotEmpty()) {
                            val current = _connections.value.toMutableList()
                            for (remote in remoteList) {
                                val idx = current.indexOfFirst { it.id == remote.id }
                                if (idx != -1) {
                                    current[idx] = remote
                                } else {
                                    current.add(remote)
                                }
                            }
                            _connections.value = current
                            saveConnectionsToPrefs(context, current)
                        }
                    }
                }
            )

            // 5. License Keys (for admin inspection)
            listeners.add(
                db.collection("license_keys").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.map { doc ->
                            LicenseKeyRecord.fromMap(doc.id, doc.data ?: emptyMap())
                        }
                        val merged = (remoteList + _licenseKeys.value)
                            .distinctBy { it.licenseKey }
                            .sortedByDescending { it.createdTimestamp }
                        _licenseKeys.value = merged
                        saveKeysToPrefs(context, merged)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start real-time programming sync", e)
        }
    }

    // =========================================================================
    // LOCAL PREFS PERSISTENCE
    // =========================================================================

    private fun loadLocalCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Softwares
        prefs.getString(KEY_CACHED_SOFTWARES, null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ProgrammerSoftware(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        description = obj.optString("description", ""),
                        version = obj.optString("version", "v1.0"),
                        fileUrl = obj.optString("fileUrl", ""),
                        fileSizeText = obj.optString("fileSizeText", ""),
                        fileType = obj.optString("fileType", "EXE"),
                        iconUrl = obj.optString("iconUrl", ""),
                        requiresLicense = obj.optBoolean("requiresLicense", false),
                        isLocked = obj.optBoolean("isLocked", false)
                    )
                }
                if (list.isNotEmpty()) _softwares.value = list
            } catch (_: Throwable) {}
        }

        // Folders
        prefs.getString(KEY_CACHED_FOLDERS, null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    FirmwareFolder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        description = obj.optString("description", ""),
                        brand = obj.optString("brand", ""),
                        iconName = obj.optString("iconName", "folder"),
                        isLocked = obj.optBoolean("isLocked", true),
                        orderIndex = obj.optInt("orderIndex", i)
                    )
                }
                if (list.isNotEmpty()) _folders.value = list
            } catch (_: Throwable) {}
        }

        // Dumps
        prefs.getString(KEY_CACHED_DUMPS, null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    FirmwareDump(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        folderId = obj.optString("folderId", ""),
                        processorType = obj.optString("processorType", ""),
                        boardPartNumber = obj.optString("boardPartNumber", ""),
                        applianceModel = obj.optString("applianceModel", ""),
                        fileUrl = obj.optString("fileUrl", ""),
                        fileType = obj.optString("fileType", "BIN"),
                        previewImageUrl = obj.optString("previewImageUrl", ""),
                        notes = obj.optString("notes", ""),
                        requiresLicense = obj.optBoolean("requiresLicense", true)
                    )
                }
                if (list.isNotEmpty()) _dumps.value = list
            } catch (_: Throwable) {}
        }

        // Connections
        prefs.getString(KEY_CACHED_CONNECTIONS, null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val pinsArr = obj.optJSONArray("pins") ?: JSONArray()
                    val pList = (0 until pinsArr.length()).map { pi ->
                        val pObj = pinsArr.getJSONObject(pi)
                        ConnectionPinItem(
                            programmerPin = pObj.optString("programmerPin", ""),
                            boardPin = pObj.optString("boardPin", ""),
                            wireColor = pObj.optString("wireColor", "أحمر"),
                            colorHex = pObj.optString("colorHex", "#EF4444"),
                            note = pObj.optString("note", "")
                        )
                    }
                    ProgrammerConnection(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        programmerName = obj.optString("programmerName", ""),
                        boardName = obj.optString("boardName", ""),
                        boardImageUrl = obj.optString("boardImageUrl", ""),
                        programmerImageUrl = obj.optString("programmerImageUrl", ""),
                        wiringInstructions = obj.optString("wiringInstructions", ""),
                        voltageNote = obj.optString("voltageNote", "3.3V DC"),
                        pins = pList
                    )
                }
                if (list.isNotEmpty()) _connections.value = list
            } catch (_: Throwable) {}
        }
        // License Keys
        prefs.getString(KEY_CACHED_KEYS, null)?.let { jsonStr ->
            try {
                val arr = JSONArray(jsonStr)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val fArr = obj.optJSONArray("allowedFolderIds") ?: JSONArray()
                    val fList = (0 until fArr.length()).map { fArr.getString(it) }
                    val sArr = obj.optJSONArray("allowedSoftwareIds") ?: JSONArray()
                    val sList = (0 until sArr.length()).map { sArr.getString(it) }
                    LicenseKeyRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        licenseKey = obj.optString("licenseKey", ""),
                        clientName = obj.optString("clientName", ""),
                        clientPhone = obj.optString("clientPhone", ""),
                        clientEmail = obj.optString("clientEmail", ""),
                        targetScope = obj.optString("targetScope", "ALL_FIRMWARE"),
                        targetScopeName = obj.optString("targetScopeName", "جميع السوفتات والبرامج"),
                        allowedFolderIds = fList,
                        allowedSoftwareIds = sList,
                        isFullAccess = obj.optBoolean("isFullAccess", true),
                        isActivated = obj.optBoolean("isActivated", false),
                        activatedDeviceId = obj.optString("activatedDeviceId", ""),
                        createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis()),
                        activatedTimestamp = obj.optLong("activatedTimestamp", 0L),
                        notes = obj.optString("notes", "")
                    )
                }
                if (list.isNotEmpty()) _licenseKeys.value = list
            } catch (_: Throwable) {}
        }
    }

    private fun saveSoftwaresToPrefs(context: Context, list: List<ProgrammerSoftware>) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("description", item.description)
                obj.put("version", item.version)
                obj.put("fileUrl", item.fileUrl)
                obj.put("fileSizeText", item.fileSizeText)
                obj.put("fileType", item.fileType)
                obj.put("iconUrl", item.iconUrl)
                obj.put("requiresLicense", item.requiresLicense)
                obj.put("isLocked", item.isLocked)
                obj.put("isFree", item.isFree)
                obj.put("localFilePath", item.localFilePath)
                obj.put("fileSizeBytes", item.fileSizeBytes)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CACHED_SOFTWARES, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun saveFoldersToPrefs(context: Context, list: List<FirmwareFolder>) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("description", item.description)
                obj.put("brand", item.brand)
                obj.put("iconName", item.iconName)
                obj.put("isLocked", item.isLocked)
                obj.put("isFree", item.isFree)
                obj.put("parentFolderId", item.parentFolderId)
                obj.put("orderIndex", item.orderIndex)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CACHED_FOLDERS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun saveDumpsToPrefs(context: Context, list: List<FirmwareDump>) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("title", item.title)
                obj.put("folderId", item.folderId)
                obj.put("processorType", item.processorType)
                obj.put("boardPartNumber", item.boardPartNumber)
                obj.put("applianceModel", item.applianceModel)
                obj.put("fileUrl", item.fileUrl)
                obj.put("fileType", item.fileType)
                obj.put("previewImageUrl", item.previewImageUrl)
                obj.put("notes", item.notes)
                obj.put("requiresLicense", item.requiresLicense)
                obj.put("isFree", item.isFree)
                obj.put("localFilePath", item.localFilePath)
                obj.put("fileSizeBytes", item.fileSizeBytes)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CACHED_DUMPS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun saveConnectionsToPrefs(context: Context, list: List<ProgrammerConnection>) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("programmerName", item.programmerName)
                obj.put("boardName", item.boardName)
                obj.put("boardImageUrl", item.boardImageUrl)
                obj.put("programmerImageUrl", item.programmerImageUrl)
                obj.put("wiringInstructions", item.wiringInstructions)
                obj.put("voltageNote", item.voltageNote)
                val pArr = JSONArray()
                item.pins.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("programmerPin", p.programmerPin)
                    pObj.put("boardPin", p.boardPin)
                    pObj.put("wireColor", p.wireColor)
                    pObj.put("colorHex", p.colorHex)
                    pObj.put("note", p.note)
                    pArr.put(pObj)
                }
                obj.put("pins", pArr)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CACHED_CONNECTIONS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun saveKeysToPrefs(context: Context, list: List<LicenseKeyRecord>) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("licenseKey", item.licenseKey)
                obj.put("clientName", item.clientName)
                obj.put("clientPhone", item.clientPhone)
                obj.put("clientEmail", item.clientEmail)
                obj.put("targetScope", item.targetScope)
                obj.put("targetScopeName", item.targetScopeName)
                val fArr = JSONArray()
                item.allowedFolderIds.forEach { fArr.put(it) }
                obj.put("allowedFolderIds", fArr)
                val sArr = JSONArray()
                item.allowedSoftwareIds.forEach { sArr.put(it) }
                obj.put("allowedSoftwareIds", sArr)
                obj.put("isFullAccess", item.isFullAccess)
                obj.put("isActivated", item.isActivated)
                obj.put("activatedDeviceId", item.activatedDeviceId)
                obj.put("createdTimestamp", item.createdTimestamp)
                obj.put("activatedTimestamp", item.activatedTimestamp)
                obj.put("notes", item.notes)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CACHED_KEYS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // INITIAL STARTER DATA
    // =========================================================================

    private fun getInitialFolders(): List<FirmwareFolder> {
        return listOf(
            FirmwareFolder(
                id = "folder_renesas",
                name = "كروت رينيساس (Renesas)",
                description = "سوفتات كروت الغسالات التي تعمل بمعالجات R5F و M16C",
                brand = "Renesas",
                isLocked = true,
                orderIndex = 0
            ),
            FirmwareFolder(
                id = "folder_lg",
                name = "كروت إل جي (LG Inverter & DD)",
                description = "سوفتات غسالات إل جي فوق وأوتوماتيك كامل",
                brand = "LG",
                isLocked = true,
                orderIndex = 1
            ),
            FirmwareFolder(
                id = "folder_samsung",
                name = "كروت سامسونج (Samsung EcoBubble)",
                description = "ملفات فلاشات ومعالجات غسالات سامسونج",
                brand = "Samsung",
                isLocked = true,
                orderIndex = 2
            ),
            FirmwareFolder(
                id = "folder_toshiba",
                name = "كروت توشيبا وتورنيدو (Toshiba / Tornado)",
                description = "دامبات وفلاشات كروت التوشيبا النصف والأوتوماتيك",
                brand = "Toshiba",
                isLocked = true,
                orderIndex = 3
            ),
            FirmwareFolder(
                id = "folder_indesit",
                name = "كروت إنديست وأريستون (Arcadia / EVO)",
                description = "سوفتات بروسيسورات Freescale و Arcadia II",
                brand = "Indesit/Ariston",
                isLocked = true,
                orderIndex = 4
            )
        )
    }

    private fun getInitialSoftwares(): List<ProgrammerSoftware> {
        return listOf(
            ProgrammerSoftware(
                id = "soft_rt809h",
                name = "برنامج مبرمجة RT809H الإصدار الرسمي",
                description = "برنامج تشغيل وقراءة الفلاشات والمعالجات لمبرمجة RT809H للكمبيوتر مع كافة ملفات التعريف.",
                version = "v2024.10",
                fileUrl = "https://www.ifix.net.cn/",
                fileSizeText = "450 MB",
                fileType = "EXE",
                requiresLicense = true,
                isLocked = true
            ),
            ProgrammerSoftware(
                id = "soft_neoprog",
                name = "برنامج NeoProgrammer (CH341A)",
                description = "البرنامج الاحترافي لتشغيل مبرمجة CH341A وقراءة فلاشات SPI و EEPROM 24/25 بسرعة فائقة.",
                version = "v2.2.0.10",
                fileUrl = "https://github.com/",
                fileSizeText = "12 MB",
                fileType = "ZIP",
                requiresLicense = false,
                isLocked = false
            ),
            ProgrammerSoftware(
                id = "soft_renesas_flash",
                name = "برنامج Renesas Flash Programmer (RFP)",
                description = "البرنامج الرسمي من شركة رينيساس لحرق وسحب السوفتات لمعالجات RL78 و RX و RH850.",
                version = "v3.12",
                fileUrl = "https://www.renesas.com/",
                fileSizeText = "180 MB",
                fileType = "EXE",
                requiresLicense = true,
                isLocked = true
            ),
            ProgrammerSoftware(
                id = "soft_stlink",
                name = "برنامج STM32 ST-Link Utility",
                description = "أداة سحب وكتابة السوفت وير لمعالجات STM32 المستخدمة في كروت الغسالات الحديثة.",
                version = "v4.6.0",
                fileUrl = "https://www.st.com/",
                fileSizeText = "40 MB",
                fileType = "EXE",
                requiresLicense = false,
                isLocked = false
            )
        )
    }

    private fun getInitialConnections(): List<ProgrammerConnection> {
        return listOf(
            ProgrammerConnection(
                id = "conn_renesas_toshiba",
                programmerName = "RT809H / Renesas RFP",
                boardName = "كارتة توشيبا فوق أوتوماتيك معالج R5F21334",
                boardImageUrl = "",
                programmerImageUrl = "",
                voltageNote = "3.3V DC (لا تقم بتوصيل 5V نهائياً لتفادي تلف المعالج)",
                wiringInstructions = "يتم لحام الأسلاك على نقاط الاختبار (Test Points) أسفل البورده الموضحة، ويجب رفع المقاومة 10k قبل البدء ثم إعادتها بعد انتهاء البرمجة.",
                pins = listOf(
                    ConnectionPinItem("Pin 1: VCC 3.3V", "نقطة TP1 (تغذية المعالج)", "أحمر", "#EF4444", "تأكد من قياس الجهد قبل التوصيل"),
                    ConnectionPinItem("Pin 2: GND (الأرضي)", "نقطة TP2 (الأرضي العام)", "أسود", "#1E293B", "شاسيه المكثف الرئيسي"),
                    ConnectionPinItem("Pin 3: MODE / RESET", "نقطة TP3 (طرف الريسيت)", "أصفر", "#EAB308", "مربوط بمقاومة شد 4.7K"),
                    ConnectionPinItem("Pin 4: TXD / SO", "نقطة TP4 (إرسال البيانات)", "أزرق", "#2563EB", "طرف Serial Data Out"),
                    ConnectionPinItem("Pin 5: RXD / SI", "نقطة TP5 (استقبال البيانات)", "أخضر", "#16A34A", "طرف Serial Data In")
                )
            ),
            ProgrammerConnection(
                id = "conn_lg_dd",
                programmerName = "CH341A / TL866II Plus",
                boardName = "فلاشة إيبروم غسالة إل جي Direct Drive (24C04)",
                boardImageUrl = "",
                programmerImageUrl = "",
                voltageNote = "5.0V أو 3.3V حسب الفلاشة",
                wiringInstructions = "يمكن القراءة إما باستخدام كليبس SOIC8 مباشرة على البوردة دون فك الفلاشة، أو فك الشريحة وتثبيتها في سوكيت المبرمجة.",
                pins = listOf(
                    ConnectionPinItem("Pin 4: GND", "شريحة الفلاشة طرف 4", "أسود", "#1E293B", "أرضي الفلاشة"),
                    ConnectionPinItem("Pin 8: VCC", "شريحة الفلاشة طرف 8", "أحمر", "#EF4444", "تغذية 5V"),
                    ConnectionPinItem("Pin 5: SDA", "شريحة الفلاشة طرف 5", "أخضر", "#16A34A", "خط البيانات Data"),
                    ConnectionPinItem("Pin 6: SCL", "شريحة الفلاشة طرف 6", "أصفر", "#EAB308", "خط النبضات Clock")
                )
            )
        )
    }
}
