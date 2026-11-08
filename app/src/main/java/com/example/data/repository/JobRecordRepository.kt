package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.TechnicianJobRecord
import com.example.util.FirebaseInitializer
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
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
 * Repository managing client work log records.
 * Ensures data isolation per technician email:
 * "لكل مستخدم (بالبريد الالكتروني) الأداة خاصة بيه"
 */
object JobRecordRepository {
    private const val TAG = "JobRecordRepository"
    private const val PREFS_NAME = "albaraa_work_logs_prefs"
    private const val KEY_JOBS_PREFIX = "user_jobs_"

    private val _jobs = MutableStateFlow<List<TechnicianJobRecord>>(emptyList())
    val jobs: StateFlow<List<TechnicianJobRecord>> = _jobs.asStateFlow()

    private var currentEmail: String = ""
    private var currentUserId: String = ""
    private var listenerRegistration: ListenerRegistration? = null

    fun init(context: Context, email: String?, userId: String?) {
        val safeEmail = (email ?: "guest").trim().lowercase()
        val safeId = userId ?: "guest"
        if (safeEmail != currentEmail || currentEmail.isEmpty()) {
            currentEmail = safeEmail
            currentUserId = safeId
            switchUser(context, safeEmail, safeId)
        }
    }

    fun switchUser(context: Context, email: String?, userId: String?) {
        val appContext = context.applicationContext ?: context
        val safeEmail = (email ?: "guest").trim().lowercase()
        val safeId = userId ?: "guest"
        currentEmail = safeEmail
        currentUserId = safeId

        listenerRegistration?.remove()
        listenerRegistration = null

        // 1. Load from local cache for instant zero-lag rendering
        val cached = loadCachedJobs(appContext, safeEmail)
        if (cached.isNotEmpty()) {
            _jobs.value = cached
        } else {
            // Seed with sample data from user's Excel sheet if brand new
            val seeded = getInitialExcelSampleData(safeEmail, safeId)
            _jobs.value = seeded
            saveCachedJobs(appContext, safeEmail, seeded)
        }

        // 2. Attach Firestore listener for real-time cloud sync if logged in
        if (safeEmail.isNotBlank() && safeEmail != "guest") {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                listenerRegistration = db.collection("technician_work_logs")
                    .whereEqualTo("userEmail", safeEmail)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Snapshot error for user $safeEmail: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                TechnicianJobRecord.fromMap(doc.id, data)
                            }.sortedByDescending { it.createdAt }

                            if (list.isNotEmpty()) {
                                _jobs.value = list
                                saveCachedJobs(appContext, safeEmail, list)
                            }
                        }
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to attach real-time Firestore listener for work logs", e)
            }
        }
    }

    fun saveJob(
        context: Context,
        job: TechnicianJobRecord,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val email = if (job.userEmail.isNotBlank()) job.userEmail.trim().lowercase() else currentEmail
        val preparedJob = job.copy(
            userEmail = email,
            userId = if (job.userId.isNotBlank()) job.userId else currentUserId,
            whatsappUrl = TechnicianJobRecord.formatWhatsAppUrl(job.clientPhone),
            dayOfWeek = if (job.dayOfWeek.isBlank()) TechnicianJobRecord.getDayOfWeekArabic(job.date) else job.dayOfWeek,
            updatedAt = System.currentTimeMillis()
        )

        val currentList = _jobs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == preparedJob.id }
        if (index >= 0) {
            currentList[index] = preparedJob
        } else {
            currentList.add(0, preparedJob)
        }
        _jobs.value = currentList
        saveCachedJobs(appContext, email, currentList)

        // Sync to cloud if user is authenticated
        if (email.isNotBlank() && email != "guest") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = FirebaseInitializer.getFirestore(appContext)
                    db.collection("technician_work_logs")
                        .document(preparedJob.id)
                        .set(preparedJob.toMap())
                        .await()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error saving work log to Firestore: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true) // local is already saved
                    }
                }
            }
        } else {
            onComplete?.invoke(true)
        }
    }

    fun deleteJob(
        context: Context,
        jobId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val email = currentEmail
        val currentList = _jobs.value.toMutableList()
        currentList.removeAll { it.id == jobId }
        _jobs.value = currentList
        saveCachedJobs(appContext, email, currentList)

        if (email.isNotBlank() && email != "guest") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = FirebaseInitializer.getFirestore(appContext)
                    db.collection("technician_work_logs")
                        .document(jobId)
                        .delete()
                        .await()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error deleting work log from Firestore: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true)
                    }
                }
            }
        } else {
            onComplete?.invoke(true)
        }
    }

    private fun loadCachedJobs(context: Context, email: String): List<TechnicianJobRecord> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("$KEY_JOBS_PREFIX$email", null) ?: return emptyList()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TechnicianJobRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "customFields") {
                        val cfObj = obj.optJSONObject(k)
                        val cfMap = mutableMapOf<String, String>()
                        cfObj?.keys()?.forEach { cfKey ->
                            cfMap[cfKey] = cfObj.optString(cfKey, "")
                        }
                        map[k] = cfMap
                    } else {
                        map[k] = obj.opt(k)
                    }
                }
                list.add(TechnicianJobRecord.fromMap(obj.optString("id"), map))
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed reading cached work logs: ${e.message}")
            emptyList()
        }
    }

    private fun saveCachedJobs(context: Context, email: String, list: List<TechnicianJobRecord>) {
        try {
            val array = JSONArray()
            list.forEach { job ->
                val obj = JSONObject()
                obj.put("id", job.id)
                obj.put("userEmail", job.userEmail)
                obj.put("userId", job.userId)
                obj.put("date", job.date)
                obj.put("dayOfWeek", job.dayOfWeek)
                obj.put("clientPhone", job.clientPhone)
                obj.put("whatsappUrl", job.whatsappUrl)
                obj.put("deviceType", job.deviceType)
                obj.put("faultDescription", job.faultDescription)
                obj.put("cost", job.cost)
                obj.put("replacedParts", job.replacedParts)
                obj.put("clientLocation", job.clientLocation)
                obj.put("otherDetails", job.otherDetails)
                obj.put("imageUrl", job.imageUrl)
                obj.put("status", job.status)
                val cfObj = JSONObject()
                job.customFields.forEach { (k, v) -> cfObj.put(k, v) }
                obj.put("customFields", cfObj)
                obj.put("createdAt", job.createdAt)
                obj.put("updatedAt", job.updatedAt)
                array.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("$KEY_JOBS_PREFIX$email", array.toString()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed saving cached work logs: ${e.message}")
        }
    }

    /**
     * Seeds initial records matching the exact entries from the user's Excel sheet!
     */
    private fun getInitialExcelSampleData(email: String, userId: String): List<TechnicianJobRecord> {
        val now = System.currentTimeMillis()
        return listOf(
            TechnicianJobRecord(
                id = "sample_job_1",
                userEmail = email,
                userId = userId,
                date = "2026/09/16",
                dayOfWeek = "الأربعاء",
                clientPhone = "01120555875",
                whatsappUrl = "https://wa.me/201120555875",
                deviceType = "غسالة سامسونج",
                faultDescription = "طرمبة",
                cost = 250.0,
                replacedParts = "ترياك acs1086",
                clientLocation = "شارع الجزيرة / المرج",
                otherDetails = "صيانة دائرة الطرمبة وتغيير ترياك",
                status = "تم التسليم والتحصيل",
                createdAt = now - 10000000
            ),
            TechnicianJobRecord(
                id = "sample_job_2",
                userEmail = email,
                userId = userId,
                date = "2026/09/16",
                dayOfWeek = "الأربعاء",
                clientPhone = "01020416489",
                whatsappUrl = "https://wa.me/201020416489",
                deviceType = "ثلاجة",
                faultDescription = "باور",
                cost = 450.0,
                replacedParts = "لحام وإعادة بناء مسارات",
                clientLocation = "طوخ / القليوبية",
                otherDetails = "كارتة باور ثلاجة فاصلة كهرباء",
                status = "تم التسليم والتحصيل",
                createdAt = now - 9000000
            ),
            TechnicianJobRecord(
                id = "sample_job_3",
                userEmail = email,
                userId = userId,
                date = "2026/09/21",
                dayOfWeek = "الاثنين",
                clientPhone = "01003605830",
                whatsappUrl = "https://wa.me/201003605830",
                deviceType = "غسالة توشيبا 13kg",
                faultDescription = "رطوبة وتمليح بوردة",
                cost = 200.0,
                replacedParts = "تنظيف بالموجات وسولدر ماسك عازل",
                clientLocation = "المرج",
                otherDetails = "إزالة التمليح واختبار كامل",
                status = "تم التسليم والتحصيل",
                createdAt = now - 8000000
            ),
            TechnicianJobRecord(
                id = "sample_job_4",
                userEmail = email,
                userId = userId,
                date = "2026/09/22",
                dayOfWeek = "الثلاثاء",
                clientPhone = "01026502636",
                whatsappUrl = "https://wa.me/201026502636",
                deviceType = "ثلاجة LG",
                faultDescription = "باور / تفحم",
                cost = 650.0,
                replacedParts = "سوكيت / سولدر ماسك ومكثفات",
                clientLocation = "محمد نجيب",
                otherDetails = "إصلاح تفحم بوردة الباور للثلاجة",
                status = "تم التسليم والتحصيل",
                createdAt = now - 7000000
            ),
            TechnicianJobRecord(
                id = "sample_job_5",
                userEmail = email,
                userId = userId,
                date = "2026/09/24",
                dayOfWeek = "الخميس",
                clientPhone = "01200608158",
                whatsappUrl = "https://wa.me/201200608158",
                deviceType = "غسالة فكتوريا 1100",
                faultDescription = "عطل EH0",
                cost = 300.0,
                replacedParts = "مقاومة 1kΩ",
                clientLocation = "البركة",
                otherDetails = "حل رمز عطل EH0 بتغيير مقاومة الإحساس",
                status = "تم التسليم والتحصيل",
                createdAt = now - 6000000
            ),
            TechnicianJobRecord(
                id = "sample_job_6",
                userEmail = email,
                userId = userId,
                date = "2026/09/24",
                dayOfWeek = "الخميس",
                clientPhone = "01147104642",
                whatsappUrl = "https://wa.me/201147104642",
                deviceType = "ثلاجة يونيون اير",
                faultDescription = "باور",
                cost = 250.0,
                replacedParts = "2 مكثف 1000μF و 1 مكثف 10μF",
                clientLocation = "شارع الأنابيب",
                otherDetails = "تغيير مكثفات الباور المنتفخة",
                status = "تم التسليم والتحصيل",
                createdAt = now - 5000000
            ),
            TechnicianJobRecord(
                id = "sample_job_7",
                userEmail = email,
                userId = userId,
                date = "2026/09/30",
                dayOfWeek = "الأربعاء",
                clientPhone = "01090770101",
                whatsappUrl = "https://wa.me/201090770101",
                deviceType = "إيكو بابل 7kg",
                faultDescription = "لا يعمل الماتور",
                cost = 350.0,
                replacedParts = "رطوبة ولحام آي سي الانفرتر",
                clientLocation = "السويس",
                otherDetails = "إعادة لحام وتأمين خطوط الماتور",
                status = "تم التسليم والتحصيل",
                createdAt = now - 4000000
            ),
            TechnicianJobRecord(
                id = "sample_job_8",
                userEmail = email,
                userId = userId,
                date = "2026/10/01",
                dayOfWeek = "الخميس",
                clientPhone = "011112949900",
                whatsappUrl = "https://wa.me/201112949900",
                deviceType = "غسالة بيكو",
                faultDescription = "تلف بروسيسور",
                cost = 3500.0,
                replacedParts = "كارتة أصلية 2000ج",
                clientLocation = "حلمية الزيتون",
                otherDetails = "تم بيعه كارت خلع بيكو أصلي مع البرمجة",
                status = "تم التسليم والتحصيل",
                createdAt = now - 3000000
            ),
            TechnicianJobRecord(
                id = "sample_job_9",
                userEmail = email,
                userId = userId,
                date = "2026/10/09",
                dayOfWeek = "الجمعة",
                clientPhone = "01224676169",
                whatsappUrl = "https://wa.me/201224676169",
                deviceType = "ديب فريزر كريازي",
                faultDescription = "باور",
                cost = 400.0,
                replacedParts = "مكثف 220μF/25V وطقم دايودات توحيد ومكثفين 4.7μF",
                clientLocation = "عين شمس",
                otherDetails = "إصلاح مرحلة التغذية 12 فولت",
                status = "تم التسليم والتحصيل",
                createdAt = now - 2000000
            ),
            TechnicianJobRecord(
                id = "sample_job_10",
                userEmail = email,
                userId = userId,
                date = "2026/10/13",
                dayOfWeek = "الثلاثاء",
                clientPhone = "01147104642",
                whatsappUrl = "https://wa.me/201147104642",
                deviceType = "ميكروويف Black & Decker",
                faultDescription = "باور فاصل تماماً",
                cost = 300.0,
                replacedParts = "تعديل دائرة باور ومحول",
                clientLocation = "شارع الأنابيب",
                otherDetails = "صيانة بوردة التحكم والميكروسويتش",
                status = "تم التسليم والتحصيل",
                createdAt = now - 1000000
            )
        )
    }
}
