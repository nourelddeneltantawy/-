package com.example.data.repository

import android.content.Context
import android.net.Uri
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

    private val defaultSeedWallets = listOf(
        SmartWallet(
            id = "wallet_vf_1",
            phoneNumber = "01012345678",
            name = "محفظة فودافون كاش 1 (الرئيسية)",
            provider = "VODAFONE_CASH",
            monthlyLimit = 200000.0,
            safetyMargin = 3000.0,
            currentReceived = 15000.0,
            priority = 1,
            status = "ACTIVE",
            notes = "شريحة المحفظة الأولى"
        ),
        SmartWallet(
            id = "wallet_vf_2",
            phoneNumber = "01098765432",
            name = "محفظة فودافون كاش 2 (الاحتياطية)",
            provider = "VODAFONE_CASH",
            monthlyLimit = 200000.0,
            safetyMargin = 3000.0,
            currentReceived = 0.0,
            priority = 2,
            status = "ACTIVE",
            notes = "شريحة المحفظة الثانية"
        ),
        SmartWallet(
            id = "wallet_instapay_1",
            phoneNumber = "01012345678",
            name = "حساب إنستاباي الرئيسي (InstaPay)",
            provider = "INSTAPAY",
            monthlyLimit = 500000.0,
            safetyMargin = 5000.0,
            currentReceived = 20000.0,
            priority = 1,
            status = "ACTIVE",
            notes = "معرف إنستاباي للتحويل الفوري"
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
            listenerRegistration = db.collection("wallets")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Wallets listener error: ${error.message}")
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
                        Log.i(TAG, "Synced ${list.size} wallets from Firestore")
                    } else if (snapshot != null && snapshot.isEmpty) {
                        // Seed initial wallets if remote collection is empty
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
                for (w in defaultSeedWallets) {
                    db.collection("wallets").document(w.id).set(w.toMap()).await()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to seed default wallets: ${e.message}")
            }
        }
    }

    /**
     * Smart Routing Engine:
     * Available Capacity = monthly_limit - safety_margin - current_received
     * Filters active wallets by priority ascending where Available Capacity >= amount
     */
    fun routePayment(amount: Double, paymentMethod: String): WalletRouteResult {
        if (amount <= 0) {
            return WalletRouteResult(
                isSuccess = false,
                errorMessage = "يرجى إدخال مبلغ صحيح أكبر من الصفر"
            )
        }

        val allActiveWallets = _wallets.value.filter { it.status == "ACTIVE" }

        // Filter by requested payment method if applicable (or all active if generic)
        val methodWallets = when (paymentMethod) {
            "VODAFONE_CASH" -> allActiveWallets.filter { it.provider == "VODAFONE_CASH" }
            "INSTAPAY" -> allActiveWallets.filter { it.provider == "INSTAPAY" }
            else -> allActiveWallets
        }.sortedBy { it.priority }

        // Find the first wallet with available capacity >= amount
        val candidate = methodWallets.firstOrNull { it.availableCapacity >= amount }

        if (candidate != null) {
            val phone = candidate.phoneNumber.trim()
            val amountFormatted = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

            // Prepare USSD for Vodafone Cash: *9*7*phone*amount#
            val ussdCode = "*9*7*$phone*$amountFormatted#"
            // URL-encode '#' as '%23' for Android Intent.ACTION_DIAL so dialer opens with full code
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
                availableCapacity = candidate.availableCapacity
            )
        } else {
            // No single wallet fits the entire amount
            val maxAvailable = methodWallets.maxOfOrNull { it.availableCapacity } ?: 0.0
            val totalCapacity = methodWallets.sumOf { it.availableCapacity }

            val errorMsg = if (maxAvailable > 0) {
                "عذراً، لا توجد محفظة نشطة تتسع لمبلغ ${amount.toLong()} ج.م دفعة واحدة.\nأقصى مبلغ متاح حالياً في محفظة واحدة هو ${maxAvailable.toLong()} ج.م.\n💡 يرجى تجزئة المبلغ للتحويل على دفعات."
            } else {
                "عذراً، جميع المحافظ وصلت للحد الشهري الأقصى (${methodWallets.size} محافظ ممتلئة).\nيرجى التواصل مع الإدارة لإتاحة محافظ جديدة."
            }

            return WalletRouteResult(
                isSuccess = false,
                paymentMethod = paymentMethod,
                amount = amount,
                availableCapacity = totalCapacity,
                errorMessage = errorMsg
            )
        }
    }

    /**
     * Records an incoming transaction (manually or via SMS sync)
     * Automatically increments currentReceived and marks FULL if available capacity exhausted.
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
        val updatedReceived = target.currentReceived + amount
        val newCapacity = (target.monthlyLimit - target.safetyMargin - updatedReceived).coerceAtLeast(0.0)
        val newStatus = if (newCapacity <= 0.0) "FULL" else target.status

        val updatedWallet = target.copy(
            currentReceived = updatedReceived,
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )

        currentList[index] = updatedWallet
        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                db.collection("wallets").document(walletId)
                    .update(
                        "currentReceived", updatedReceived,
                        "status", newStatus,
                        "updatedAt", System.currentTimeMillis()
                    ).await()

                // Log transaction
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

    /**
     * Admin: Save or update a wallet
     */
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
                db.collection("wallets").document(id)
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

    /**
     * Admin: Delete a wallet
     */
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
                db.collection("wallets").document(walletId).delete().await()
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

    /**
     * Admin: Reset monthly received balances (Cron / Beginning of month)
     */
    fun resetMonthlyBalances(
        context: Context,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        val currentList = _wallets.value.map {
            it.copy(
                currentReceived = 0.0,
                status = if (it.status == "FULL") "ACTIVE" else it.status,
                updatedAt = System.currentTimeMillis()
            )
        }

        _wallets.value = currentList
        saveCachedWallets(appContext, currentList)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseInitializer.getFirestore(appContext)
                for (w in currentList) {
                    db.collection("wallets").document(w.id).update(
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
                obj.put("monthlyLimit", w.monthlyLimit)
                obj.put("safetyMargin", w.safetyMargin)
                obj.put("currentReceived", w.currentReceived)
                obj.put("priority", w.priority)
                obj.put("status", w.status)
                obj.put("notes", w.notes)
                obj.put("updatedAt", w.updatedAt)
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_WALLETS, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedWallets(context: Context) {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                    monthlyLimit = obj.optDouble("monthlyLimit", 200000.0),
                    safetyMargin = obj.optDouble("safetyMargin", 3000.0),
                    currentReceived = obj.optDouble("currentReceived", 0.0),
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
