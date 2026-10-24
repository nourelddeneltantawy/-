package com.example.data.model

data class Pinout(
    val pinNumber: Int = 1,
    val name: String = "",
    val wireColor: String = "",
    val colorHex: String = "#3B82F6",
    val secondaryColorHex: String? = null,
    val secondaryColorName: String? = null,
    val functionDesc: String = ""
) {
    val isBicolor: Boolean
        get() = !secondaryColorHex.isNullOrBlank()

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "pinNumber" to pinNumber,
            "name" to name,
            "wireColor" to wireColor,
            "colorHex" to colorHex
        )
        if (!secondaryColorHex.isNullOrBlank()) {
            map["secondaryColorHex"] = secondaryColorHex
        }
        if (!secondaryColorName.isNullOrBlank()) {
            map["secondaryColorName"] = secondaryColorName
        }
        if (functionDesc.isNotBlank()) {
            map["functionDesc"] = functionDesc
        }
        return map
    }
}
