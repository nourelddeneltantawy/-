package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.SmartWallet
import com.example.data.model.WalletRouteResult
import com.example.util.FirebaseInitializer
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

object WalletRoutingRepository {
    private const val TAG = "WalletRoutingRepo"
    private const val PREFS_NAME = "albaraa_wallets_prefs"
    private const val KEY_CACHED_WALLETS = "cached_wallets_json"

    private val _wallets = MutableStateFlow<List<SmartWallet>>(emptyList())
    val wallets: StateFlow<List<SmartWallet>> = _wallets.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var isInitialized = false
    private var activeUserId: String? = null
    private var activeUserEmail: String? = null

    private fun getPrefsName(): String {
        val uid = activeUserId?.trim()
        return if (!uid.isNullOrBlank()) "albaraa_wallets_prefs_${uid.replace("[^a-zA-Z0-9_]".toRegex(), "_")}" else PREFS_NAME
    }

    private fun getWalletsCollectionPath(): String {
        val uid = activeUserId?.trim()
        return if (!uid.isNullOrBlank()) "users/$uid/wallets" else "wallets"
    }

    fun setUser(context: Context, userId: String?, userEmail: String?) {
        val cleanId = userId?.trim()
        val cleanEmail = userEmail?.trim()
        if (cleanId == activeUserId && cleanEmail == activeUserEmail && isInitialized) return
        activeUserId = cleanId
        activeUserEmail = cleanEmail
        listenerRegistration?.remove()
        listenerRegistration = null
        isInitialized = false
        _wallets.value = emptyList()
        init(context)
    }

    private val defaultSeedWallets = listOf(
        SmartWallet(
            id = "wallet_vf_1",
            phoneNumber = "01012345678",
            name = "محفظة فودافون كاش 1 (الرئيسية)",
            provider = "VODAFONE_CASH",
            monthlyReceiveLimit = 200000.0,
            monthlySendLimit = 200000.0,
            maxHoldingBalance = 100000.0,
            currentBalance = 12000.0,
            currentReceivedThisMonth = 15000.0,
            safetyMargin = 3000.0,
            priority = 1,
            status = "ACTIVE",
            notes = "شريحة المحفظة الأولى الأساسية"
        ),
        SmartWallet(
            id = "wallet_vf_2",
            phoneNumber = "01098765432",
            name = "محفظة فودافون كاش 2 (الاحتياطية)",
            provider = "VODAFONE_CASH",
            monthlyReceiveLimit = 200000.0,
            monthlySendLimit = 200000.0,
            maxHoldingBalance = 100000.0,
            currentBalance = 0.0,
            currentReceivedThisMonth = 0.0,
            safetyMargin = 3000.0,
            priority = 2,
            status = "ACTIVE",
            notes = "شريحة المحفظة الثانية الاحتياطية"
        ),
        SmartWallet(
            id = "wallet_instapay_1",
            phoneNumber = "01012345678",
            name = "حساب إنستاباي الرئيسي (InstaPay)",
            provider = "INSTAPAY",
            monthlyReceiveLimit = 500000.0,
            monthlySendLimit = 500000.0,
            maxHoldingBalance = 500000.0,
            currentBalance = 20000.0,
            currentReceivedThisMonth = 20000.0,
            safetyMargin = 5000.0,
            priority = 1,
            status = "ACTIVE",
            notes = "معرف إنستاباي للتحويل الفوري المباشر"
        )
    )

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context
        loadCachedWallets(appContext)

        if (_wallets.value.isEmpty()) {
            _wallets.value = defaultSeedWallets
            saveCachedWallets(appContext, defaultSeedWallets)
        }

        if (!isInitialized) {
            isInitialized = true
            startRealtimeSync(appContext)
        }
    }

    private fun startRealtimeSync(context: Context) {
        try {
            val db = FirebaseInitializer.getFirestore(context)
            val path = getWalletsCollectionPath()
            listenerRegistration = db.collection(path)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Wallets listener error ($path): ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                SmartWallet.fromMap(doc.id, doc.data ?: emptyMap())
                            } catch (_: Throwable) {
                                null
                            }
                        }.sortedWith(compareBy({ it.priority }, { it.phoneNumber }))

                        _wallets.value = list
                        saveCachedWallets(context, list)
                        Log.i(TAG, "Synced ${list.size} wallets from $path")
                    } else if (snapshot != null && snapshot.isEmpty) {
                        seedDefaultWallets(context)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start wallets sync", e)
        }
    }

    private fun seedDefaultWallets(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(context)
                val path = getWalletsCollectionPath()
                for (w in defaultSeedWallets) {
                    db.collection(path).document(w.id).set(w.toMap()).await()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to seed default wallets: ${e.message}")
            }
        }
    }

    /**
     * محرك وخوارزمية التوجيه الذكي (Vodafone Cash Rules):
     * المحفظة تكون صالحة فقط إذا تحققت الشروط التالية معاً:
     * 1. (monthly_receive_limit - safety_margin - current_received_this_month) >= amount
     * 2. (max_holding_balance - current_balance) >= amount
     */
    fun routePayment(amount: Double, paymentMethod: String): WalletRouteResult {
        if (amount <= 0) {
            return WalletRouteResult(
                isSuccess = false,
                errorMessage = "يرجى إدخال مبلغ صحيح أكبر من الصفر"
            )
        }

        val allWallets = _wallets.value
        val methodWallets = when (paymentMethod) {
            "VODAFONE_CASH" -> allWallets.filter { it.provider == "VODAFONE_CASH" }
            "INSTAPAY" -> allWallets.filter { it.provider == "INSTAPAY" }
            else -> allWallets
        }.sortedBy { it.priority }

        // Find first active wallet that satisfies both conditions
        val candidate = methodWallets.firstOrNull { it.canAccept(amount) }

        if (candidate != null) {
            val phone = candidate.phoneNumber.trim()
            val amountFormatted = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

            val ussdCode = "*9*7*$phone*$amountFormatted#"
            val ussdUri = "tel:*9*7*$phone*${amountFormatted}%23"
            val deepLink = if (paymentMethod == "INSTAPAY") "instapay://" else ""

            return WalletRouteResult(
                isSuccess = true,
                wallet = candidate,
                paymentMethod = paymentMethod,
                amount = amount,
                ussdCode = ussdCode,
                ussdDialerUri = ussdUri,
                deepLink = deepLink,
                availableCapacity = candidate.availableCapacity,
                availableMonthlyCapacity = candidate.availableMonthlyCapacity,
                availableHoldingCapacity = candidate.availableHoldingCapacity
            )
        } else {
            // Diagnostic reason for failure
            val maxAvailable = methodWallets.filter { it.status == "ACTIVE" }
                .maxOfOrNull { it.availableCapacity } ?: 0.0

            val hasHoldingMax = methodWallets.any { it.isHoldingMaxReached }
            val hasMonthlyFull = methodWallets.any { it.isFullMonthly }

            val reason = when {
                hasHoldingMax && !hasMonthlyFull -> "المحافظ بلغت الحد الأقصى للرصيد الحجمي (100 ألف ج)، بانتظار سحب الرصيد من قبل الإدارة لتفعيلها فوراً."
                hasMonthlyFull && !hasHoldingMax -> "المحافظ بلغت الحد الشهري المسموح للاستلام."
                else -> "لا توجد محفظة تتسع لكامل المبلغ دفعة واحدة."
            }

            val errorMsg = if (maxAvailable > 0) {
                "عذراً، المبلغ المطلوب (${amount.toLong()} ج.م) أكبر من السعة المتاحة دفعة واحدة.\nأقصى مبلغ متاح للتحويل في محفظة واحدة حالياً هو ${maxAvailable.toLong()} ج.م.\n💡 $reason\nيرجى تجزئة المبلغ للتحويل على دفعات."
            } else {
                "عذراً، جميع المحافظ ممتلئة حالياً وغير متاحة لاستقبال مبالغ جديدة.\n💡 $reason\nيرجى التواصل مع الإدارة لإتاحة محافظ جديدة."
            }

            return WalletRouteResult(
                isSuccess = false,
                paymentMethod = paymentMethod,
                amount = amount,
                availableCapacity = methodWallets.sumOf { it.availableCapacity },
                errorMessage = errorMsg
            )
        }
    }

    /**
     * زر: "تم سحب رصيد المحفظة"
     * يصفر current_balance إلى 0 ويحول الحالة من HOLDING_MAX_REACHED إلى ACTIVE فوراً
     */
    fun withdrawHoldingBalance(
        context: Context,
        walletId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _wallets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == walletId }
        if (index == -1) {
            onComplete?.invoke(false)
            return
        }

        val target = currentList[index]
        val newStatus = if (target.status == "HOLDING_MAX_REACHED" && target.availableMonthlyCapacity > 0) "ACTIVE" else target.status

        val updatedWallet = target.copy(
            currentBalance = 0.0,
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )

        currentList[index] = updatedWallet
        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection(getWalletsCollectionPath()).document(walletId).update(
                    "currentBalance", 0.0,
                    "current_balance", 0.0,
                    "status", newStatus,
                    "updatedAt", System.currentTimeMillis()
                ).await()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error withdrawing holding balance", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    /**
     * تسجيل عملية استلام جديدة (يدوياً أو عبر خلفية الـ SMS تلقائياً):
     * 1. زيادة current_received_this_month
     * 2. زيادة current_balance
     * 3. إذا current_balance >= max_holding_balance -> HOLDING_MAX_REACHED
     * 4. إذا الشهرية ممتلئة -> FULL
     */
    fun recordTransaction(
        context: Context,
        walletId: String,
        amount: Double,
        referenceText: String = "",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _wallets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == walletId }
        if (index == -1) {
            onComplete?.invoke(false)
            return
        }

        val target = currentList[index]
        val updatedReceived = target.currentReceivedThisMonth + amount
        val updatedBalance = target.currentBalance + amount

        val isHoldingFull = (target.maxHoldingBalance - updatedBalance) <= 0.0
        val isMonthlyFull = (target.monthlyReceiveLimit - target.safetyMargin - updatedReceived) <= 0.0

        val newStatus = when {
            isHoldingFull -> "HOLDING_MAX_REACHED"
            isMonthlyFull -> "FULL"
            else -> target.status
        }

        val updatedWallet = target.copy(
            currentReceivedThisMonth = updatedReceived,
            currentBalance = updatedBalance,
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )

        currentList[index] = updatedWallet
        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection(getWalletsCollectionPath()).document(walletId)
                    .update(
                        "currentReceivedThisMonth", updatedReceived,
                        "current_received_this_month", updatedReceived,
                        "currentReceived", updatedReceived,
                        "currentBalance", updatedBalance,
                        "current_balance", updatedBalance,
                        "status", newStatus,
                        "updatedAt", System.currentTimeMillis()
                    ).await()

                val logEntry = mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "walletId" to walletId,
                    "phoneNumber" to target.phoneNumber,
                    "amount" to amount,
                    "reference" to referenceText,
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("wallet_transactions").document(logEntry["id"].toString())
                    .set(logEntry).await()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error recording wallet transaction", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun saveWallet(
        context: Context,
        wallet: SmartWallet,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val id = wallet.id.ifBlank { "wallet_" + UUID.randomUUID().toString().take(8) }
        val readyWallet = wallet.copy(
            id = id,
            updatedAt = System.currentTimeMillis()
        )

        val currentList = _wallets.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == id }
        if (idx != -1) {
            currentList[idx] = readyWallet
        } else {
            currentList.add(readyWallet)
        }
        currentList.sortWith(compareBy({ it.priority }, { it.phoneNumber }))

        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection(getWalletsCollectionPath()).document(id)
                    .set(readyWallet.toMap()).await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error saving wallet", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun deleteWallet(
        context: Context,
        walletId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _wallets.value.toMutableList()
        currentList.removeAll { it.id == walletId }
        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection(getWalletsCollectionPath()).document(walletId).delete().await()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting wallet", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun resetMonthlyBalances(
        context: Context,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _wallets.value.map {
            val newStatus = if (it.status == "FULL") "ACTIVE" else it.status
            it.copy(
                currentReceivedThisMonth = 0.0,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        }

        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                for (w in currentList) {
                    db.collection(getWalletsCollectionPath()).document(w.id).update(
                        "currentReceivedThisMonth", 0.0,
                        "current_received_this_month", 0.0,
                        "currentReceived", 0.0,
                        "status", w.status,
                        "updatedAt", System.currentTimeMillis()
                    ).await()
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error resetting monthly balances", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private fun saveCachedWallets(context: Context, list: List<SmartWallet>) {
        try {
            val arr = JSONArray()
            list.forEach { w ->
                val obj = JSONObject()
                obj.put("id", w.id)
                obj.put("phoneNumber", w.phoneNumber)
                obj.put("name", w.name)
                obj.put("provider", w.provider)
                obj.put("monthlyReceiveLimit", w.monthlyReceiveLimit)
                obj.put("monthlySendLimit", w.monthlySendLimit)
                obj.put("maxHoldingBalance", w.maxHoldingBalance)
                obj.put("currentBalance", w.currentBalance)
                obj.put("currentReceivedThisMonth", w.currentReceivedThisMonth)
                obj.put("safetyMargin", w.safetyMargin)
                obj.put("priority", w.priority)
                obj.put("status", w.status)
                obj.put("notes", w.notes)
                obj.put("updatedAt", w.updatedAt)
                arr.put(obj)
            }
            context.getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_WALLETS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedWallets(context: Context) {
        val json = context.getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE)
            .getString(KEY_CACHED_WALLETS, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SmartWallet(
                    id = obj.optString("id", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    name = obj.optString("name", ""),
                    provider = obj.optString("provider", "VODAFONE_CASH"),
                    monthlyReceiveLimit = obj.optDouble("monthlyReceiveLimit", 200000.0),
                    monthlySendLimit = obj.optDouble("monthlySendLimit", 200000.0),
                    maxHoldingBalance = obj.optDouble("maxHoldingBalance", 100000.0),
                    currentBalance = obj.optDouble("currentBalance", 0.0),
                    currentReceivedThisMonth = obj.optDouble("currentReceivedThisMonth", 0.0),
                    safetyMargin = obj.optDouble("safetyMargin", 3000.0),
                    priority = obj.optInt("priority", 1),
                    status = obj.optString("status", "ACTIVE"),
                    notes = obj.optString("notes", ""),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            if (list.isNotEmpty()) {
                _wallets.value = list
            }
        } catch (_: Throwable) {}
    }
}
