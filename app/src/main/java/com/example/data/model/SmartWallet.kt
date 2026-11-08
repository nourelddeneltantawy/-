package com.example.data.model

data class SmartWallet(
    val id: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val provider: String = "VODAFONE_CASH", // "VODAFONE_CASH", "INSTAPAY", "ETISALAT_CASH", "ORANGE_CASH"
    val monthlyLimit: Double = 200000.0,    // 200,000 EGP default monthly limit
    val safetyMargin: Double = 3000.0,      // 3,000 EGP reserved safety margin for personal use
    val currentReceived: Double = 0.0,      // Current month received amount
    val priority: Int = 1,                  // 1, 2, 3... order of routing
    val status: String = "ACTIVE",          // "ACTIVE", "FULL", "DISABLED"
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Available capacity for routing new incoming transfers
     * Formula: Available Capacity = monthly_limit - safety_margin - current_received
     */
    val availableCapacity: Double
        get() = (monthlyLimit - safetyMargin - currentReceived).coerceAtLeast(0.0)

    val usagePercentage: Float
        get() = if (monthlyLimit > 0) ((currentReceived / monthlyLimit) * 100).toFloat().coerceIn(0f, 100f) else 0f

    val isFull: Boolean
        get() = status == "FULL" || availableCapacity <= 0.0

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "name" to name,
            "provider" to provider,
            "monthlyLimit" to monthlyLimit,
            "safetyMargin" to safetyMargin,
            "currentReceived" to currentReceived,
            "priority" to priority,
            "status" to status,
            "notes" to notes,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SmartWallet {
            return SmartWallet(
                id = id,
                phoneNumber = map["phoneNumber"] as? String ?: (map["phone_number"] as? String ?: ""),
                name = map["name"] as? String ?: "",
                provider = map["provider"] as? String ?: "VODAFONE_CASH",
                monthlyLimit = (map["monthlyLimit"] as? Number)?.toDouble()
                    ?: (map["monthly_limit"] as? Number)?.toDouble() ?: 200000.0,
                safetyMargin = (map["safetyMargin"] as? Number)?.toDouble()
                    ?: (map["safety_margin"] as? Number)?.toDouble() ?: 3000.0,
                currentReceived = (map["currentReceived"] as? Number)?.toDouble()
                    ?: (map["current_received"] as? Number)?.toDouble() ?: 0.0,
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
    val errorMessage: String? = null
)
