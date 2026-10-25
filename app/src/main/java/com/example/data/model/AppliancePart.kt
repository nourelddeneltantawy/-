package com.example.data.model

import com.google.firebase.Timestamp

data class AppliancePart(
    val id: String = "",
    val name: String = "",
    val partType: String = "لوك باب", // "لوك باب", "ميزان ماء / ليفل", "طلمبة طرد", "صمام سحب مياه", "سخان وثرمستات"
    val brand: String = "",
    val model: String = "",
    val pinouts: List<Pinout> = emptyList(),
    val resistanceValue: String = "", // e.g. "800 - 1100 أوم"
    val operatingVoltage: String = "220V AC",
    val testMethod: String = "", // e.g. "فحص بالسيريا بين الطرف 1 و 3 لتأكيد توصيل تكة القفل"
    val notes: String = "",
    val orderIndex: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "partType" to partType,
            "brand" to brand,
            "model" to model,
            "pinouts" to pinouts.map { it.toMap() },
            "resistanceValue" to resistanceValue,
            "operatingVoltage" to operatingVoltage,
            "testMethod" to testMethod,
            "notes" to notes,
            "orderIndex" to orderIndex
        )
    }
}
