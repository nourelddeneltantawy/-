package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Model representing a client work / repair job record in the technician's logbook.
 * Matches all fields from the technician's Excel sheet plus support for dynamic custom fields.
 */
data class TechnicianJobRecord(
    val id: String = UUID.randomUUID().toString(),
    val userEmail: String = "", // Technician email (isolation per user)
    val userId: String = "",
    val date: String = getCurrentDateFormatted(), // e.g. "2026/09/16"
    val dayOfWeek: String = getDayOfWeekArabic(getCurrentDateFormatted()), // e.g. "الأربعاء"
    val clientPhone: String = "", // e.g. "01120555875"
    val whatsappUrl: String = "", // e.g. "https://wa.me/201120555875"
    val deviceType: String = "", // نوع الكارت / الجهاز: e.g. "غسالة سامسونج"
    val faultDescription: String = "", // عطل الجهاز: e.g. "طرمبة" / "باور"
    val cost: Double = 0.0, // الحساب / التكلفة: e.g. 250.0
    val expenseCost: Double = 0.0, // المصروفات / التكلفة المدفوعة على الكارتة (قطع غيار، شحن)
    val replacedParts: String = "", // قطع الغيار المبدلة: e.g. "ترياك acs1086"
    val clientLocation: String = "", // مكان العميل: e.g. "شارع الجزيرة / المرج"
    val otherDetails: String = "", // تفاصيل أخرى / ملاحظات: e.g. "تم بيعه كارت خلع"
    val imageUrl: String = "", // صورة للكارت
    val status: String = "في المخزون", // في المخزون / قيد الفحص / جاهز للاستلام / تم التسليم والتحصيل
    val customFields: Map<String, String> = emptyMap(), // خانات إضافية يحددها المستخدم
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val netProfit: Double
        get() = cost - expenseCost

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userEmail" to userEmail.trim().lowercase(),
            "userId" to userId,
            "date" to date,
            "dayOfWeek" to dayOfWeek,
            "clientPhone" to clientPhone,
            "whatsappUrl" to whatsappUrl,
            "deviceType" to deviceType,
            "faultDescription" to faultDescription,
            "cost" to cost,
            "expenseCost" to expenseCost,
            "replacedParts" to replacedParts,
            "clientLocation" to clientLocation,
            "otherDetails" to otherDetails,
            "imageUrl" to imageUrl,
            "status" to status,
            "customFields" to customFields,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): TechnicianJobRecord {
            return TechnicianJobRecord(
                id = id,
                userEmail = (map["userEmail"] as? String) ?: "",
                userId = (map["userId"] as? String) ?: "",
                date = (map["date"] as? String) ?: getCurrentDateFormatted(),
                dayOfWeek = (map["dayOfWeek"] as? String) ?: "",
                clientPhone = (map["clientPhone"] as? String) ?: "",
                whatsappUrl = (map["whatsappUrl"] as? String) ?: "",
                deviceType = (map["deviceType"] as? String) ?: "",
                faultDescription = (map["faultDescription"] as? String) ?: "",
                cost = (map["cost"] as? Number)?.toDouble() ?: 0.0,
                expenseCost = (map["expenseCost"] as? Number)?.toDouble() ?: 0.0,
                replacedParts = (map["replacedParts"] as? String) ?: "",
                clientLocation = (map["clientLocation"] as? String) ?: "",
                otherDetails = (map["otherDetails"] as? String) ?: "",
                imageUrl = (map["imageUrl"] as? String) ?: "",
                status = (map["status"] as? String) ?: "في المخزون",
                customFields = (map["customFields"] as? Map<String, String>) ?: emptyMap(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        fun getCurrentDateFormatted(): String {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            return sdf.format(Date())
        }

        fun getDayOfWeekArabic(dateStr: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                val d = sdf.parse(dateStr) ?: Date()
                val daySdf = SimpleDateFormat("EEEE", Locale("ar"))
                daySdf.format(d)
            } catch (_: Throwable) {
                "اليوم"
            }
        }

        fun formatWhatsAppUrl(phone: String): String {
            val clean = phone.filter { it.isDigit() }
            return if (clean.startsWith("0")) {
                "https://wa.me/2$clean"
            } else if (clean.startsWith("20")) {
                "https://wa.me/$clean"
            } else if (clean.isNotBlank()) {
                "https://wa.me/20$clean"
            } else {
                ""
            }
        }
    }
}
