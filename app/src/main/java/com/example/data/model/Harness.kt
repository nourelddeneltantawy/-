package com.example.data.model

import com.google.firebase.Timestamp

data class HarnessPin(
    val pinNumber: Int = 1,
    val label: String = "",
    val wireColor: String = "أزرق",
    val colorHex: String = "#3B82F6",
    val secondaryColorHex: String? = null,
    val secondaryColorName: String? = null,
    val connectedComponent: String = "ماتور", // "ماتور", "لوك الباب", "ليفل الماء", "طلمبة الطرد", "صمام الدخول", "سخان وحساس حرارة", "شداد", "تغذية 220V", "شاشة العرض"
    val targetPinNumber: Int? = 1, // رقم الطرف في القطعة المقابلة
    val signalType: String = "", // "تغذية 220V", "إشارة تاكو", "أرضي", "حساس"
    val isBlank: Boolean = false,
    val pinX: Float = 0f, // موقع الطرف الأفقي على صورة الكارتة (0f..100f) كنسبة مئوية
    val pinY: Float = 0f, // موقع الطرف الرأسي على صورة الكارتة (0f..100f) كنسبة مئوية
    val socketName: String = "سوكيت 1" // اسم السوكيت التابع له (مثال: سوكيت 1 (5 طرف) أو CN1)
) {
    val isBlankPin: Boolean
        get() = isBlank ||
                wireColor.contains("فارغ") ||
                wireColor.contains("بدون") ||
                label.contains("فارغ") ||
                label.contains("فاضي") ||
                label.contains("NC", ignoreCase = true)

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "pinNumber" to pinNumber,
            "label" to label,
            "socketName" to socketName,
            "wireColor" to wireColor,
            "colorHex" to colorHex,
            "secondaryColorHex" to secondaryColorHex,
            "secondaryColorName" to secondaryColorName,
            "connectedComponent" to connectedComponent,
            "targetPinNumber" to targetPinNumber,
            "signalType" to signalType,
            "pinX" to pinX,
            "pinY" to pinY
        )
        if (isBlank) {
            map["isBlank"] = true
        }
        return map
    }
}

data class Harness(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val notes: String = "",
    val boardImageUrl: String = "", // صورة كارتة التحكم مع السوكيتات
    val bottomImageUrl: String = "", // صورة الأجزاء السفلية إن وجدت
    val boardPins: List<HarnessPin> = emptyList(), // أطراف الكارتة مع أماكنها ووظائفها
    val bottomPins: List<HarnessPin> = emptyList(), // أطراف الماتور والقطع من أسفل
    val orderIndex: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "brand" to brand,
            "model" to model,
            "notes" to notes,
            "boardImageUrl" to boardImageUrl,
            "bottomImageUrl" to bottomImageUrl,
            "boardPins" to boardPins.map { it.toMap() },
            "bottomPins" to bottomPins.map { it.toMap() },
            "orderIndex" to orderIndex
        )
    }
}
