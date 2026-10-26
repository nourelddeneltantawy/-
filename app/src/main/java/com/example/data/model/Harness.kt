package com.example.data.model

import com.google.firebase.Timestamp

data class HarnessPin(
    val pinNumber: Int = 1,
    val label: String = "",
    val wireColor: String = "أزرق",
    val colorHex: String = "#3B82F6",
    val secondaryColorHex: String? = null,
    val secondaryColorName: String? = null,
    val connectedComponent: String = "ماتور", // "ماتور", "لوك الباب", "ليفل الماء", "طلمبة الطرد", "صمام الدخول"
    val targetPinNumber: Int? = 1, // رقم الطرف في القطعة المقابلة
    val signalType: String = "", // "تغذية 220V", "إشارة تاكو", "أرضي", "حساس"
    val isBlank: Boolean = false
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
            "wireColor" to wireColor,
            "colorHex" to colorHex,
            "secondaryColorHex" to secondaryColorHex,
            "secondaryColorName" to secondaryColorName,
            "connectedComponent" to connectedComponent,
            "targetPinNumber" to targetPinNumber,
            "signalType" to signalType
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
    val boardPins: List<HarnessPin> = emptyList(), // أطراف الكارتة من أعلى (1..15)
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
            "boardPins" to boardPins.map { it.toMap() },
            "bottomPins" to bottomPins.map { it.toMap() },
            "orderIndex" to orderIndex
        )
    }
}
