package com.example.data.model

data class SmartWallet(
    val id: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val provider: String = "VODAFONE_CASH", // "VODAFONE_CASH", "INSTAPAY", "ETISALAT_CASH", "ORANGE_CASH"
    val monthlyReceiveLimit: Double = 200000.0, // الحد الشهري للاستلام (افتراضي 200,000 ج)
    val monthlySendLimit: Double = 200000.0,    // الحد الشهري للتحويل (افتراضي 200,000 ج)
    val maxHoldingBalance: Double = 100000.0,   // الحد الأقصى لرصيد المحفظة الحجمي في أي لحظة (افتراضي 100,000 ج)
    val currentBalance: Double = 0.0,           // الرصيد الحالي الموجود فعلياً داخل المحفظة
    val currentReceivedThisMonth: Double = 0.0, // إجمالي ما تم استلامه خلال الشهر الحالي
    val safetyMargin: Double = 3000.0,          // هامش الأمان الشخصي المستثنى لحسابك الخاص
    val priority: Int = 1,                      // ترتيب أولوية المحفظة (1، 2، 3...)
    val status: String = "ACTIVE",              // "ACTIVE", "FULL", "HOLDING_MAX_REACHED", "DISABLED"
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Backwards-compatibility aliases
    val monthlyLimit: Double get() = monthlyReceiveLimit
    val currentReceived: Double get() = currentReceivedThisMonth

    /**
     * الشرط 1: السعة الشهرية المتبقية للاستلام مع استبعاد هامش الأمان
     * monthly_receive_limit - safety_margin - current_received_this_month
     */
    val availableMonthlyCapacity: Double
        get() = (monthlyReceiveLimit - safetyMargin - currentReceivedThisMonth).coerceAtLeast(0.0)

    /**
     * الشرط 2: السعة الحجمية المتبقية في المحفظة حتى بلوغ سقف الـ 100 ألف
     * max_holding_balance - current_balance
     */
    val availableHoldingCapacity: Double
        get() = (maxHoldingBalance - currentBalance).coerceAtLeast(0.0)

    /**
     * السعة الفعلية المتاحة لأي تحويل جديد (الحد الأدنى بين السعتين)
     */
    val availableCapacity: Double
        get() = minOf(availableMonthlyCapacity, availableHoldingCapacity)

    val monthlyUsagePercentage: Float
        get() = if (monthlyReceiveLimit > 0) ((currentReceivedThisMonth / monthlyReceiveLimit) * 100).toFloat().coerceIn(0f, 100f) else 0f

    val holdingUsagePercentage: Float
        get() = if (maxHoldingBalance > 0) ((currentBalance / maxHoldingBalance) * 100).toFloat().coerceIn(0f, 100f) else 0f

    val isFullMonthly: Boolean
        get() = status == "FULL" || availableMonthlyCapacity <= 0.0

    val isHoldingMaxReached: Boolean
        get() = status == "HOLDING_MAX_REACHED" || availableHoldingCapacity <= 0.0

    /**
     * فحص مطابقة شروط فودافون كاش لتحويل معين:
     * 1. (monthly_receive_limit - safety_margin - current_received_this_month) >= amount
     * 2. (max_holding_balance - current_balance) >= amount
     */
    fun canAccept(amount: Double): Boolean {
        if (status == "DISABLED" || status == "FULL" || status == "HOLDING_MAX_REACHED") return false
        return availableMonthlyCapacity >= amount && availableHoldingCapacity >= amount
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "phone_number" to phoneNumber,
            "name" to name,
            "provider" to provider,
            "monthlyReceiveLimit" to monthlyReceiveLimit,
            "monthly_receive_limit" to monthlyReceiveLimit,
            "monthlySendLimit" to monthlySendLimit,
            "monthly_send_limit" to monthlySendLimit,
            "maxHoldingBalance" to maxHoldingBalance,
            "max_holding_balance" to maxHoldingBalance,
            "currentBalance" to currentBalance,
            "current_balance" to currentBalance,
            "currentReceivedThisMonth" to currentReceivedThisMonth,
            "current_received_this_month" to currentReceivedThisMonth,
            "monthlyLimit" to monthlyReceiveLimit,
            "currentReceived" to currentReceivedThisMonth,
            "safetyMargin" to safetyMargin,
            "safety_margin" to safetyMargin,
            "priority" to priority,
            "status" to status,
            "notes" to notes,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SmartWallet {
            val monthlyReceive = (map["monthlyReceiveLimit"] as? Number)?.toDouble()
                ?: (map["monthly_receive_limit"] as? Number)?.toDouble()
                ?: (map["monthlyLimit"] as? Number)?.toDouble()
                ?: (map["monthly_limit"] as? Number)?.toDouble()
                ?: 200000.0

            val currentRec = (map["currentReceivedThisMonth"] as? Number)?.toDouble()
                ?: (map["current_received_this_month"] as? Number)?.toDouble()
                ?: (map["currentReceived"] as? Number)?.toDouble()
                ?: (map["current_received"] as? Number)?.toDouble()
                ?: 0.0

            val holdingMax = (map["maxHoldingBalance"] as? Number)?.toDouble()
                ?: (map["max_holding_balance"] as? Number)?.toDouble()
                ?: 100000.0

            val currentBal = (map["currentBalance"] as? Number)?.toDouble()
                ?: (map["current_balance"] as? Number)?.toDouble()
                ?: 0.0

            return SmartWallet(
                id = id,
                phoneNumber = map["phoneNumber"] as? String ?: (map["phone_number"] as? String ?: ""),
                name = map["name"] as? String ?: "",
                provider = map["provider"] as? String ?: "VODAFONE_CASH",
                monthlyReceiveLimit = monthlyReceive,
                monthlySendLimit = (map["monthlySendLimit"] as? Number)?.toDouble()
                    ?: (map["monthly_send_limit"] as? Number)?.toDouble() ?: 200000.0,
                maxHoldingBalance = holdingMax,
                currentBalance = currentBal,
                currentReceivedThisMonth = currentRec,
                safetyMargin = (map["safetyMargin"] as? Number)?.toDouble()
                    ?: (map["safety_margin"] as? Number)?.toDouble() ?: 3000.0,
                priority = (map["priority"] as? Number)?.toInt() ?: 1,
                status = map["status"] as? String ?: "ACTIVE",
                notes = map["notes"] as? String ?: "",
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

data class WalletRouteResult(
    val isSuccess: Boolean,
    val wallet: SmartWallet? = null,
    val paymentMethod: String = "",
    val amount: Double = 0.0,
    val ussdCode: String = "",
    val ussdDialerUri: String = "",
    val deepLink: String = "",
    val availableCapacity: Double = 0.0,
    val availableMonthlyCapacity: Double = 0.0,
    val availableHoldingCapacity: Double = 0.0,
    val errorMessage: String? = null
)
