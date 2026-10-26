package com.example.data.model

data class Pinout(
    val pinNumber: Int = 1,
    val name: String = "",
    val wireColor: String = "",
    val colorHex: String = "#3B82F6",
    val secondaryColorHex: String? = null,
    val secondaryColorName: String? = null,
    val functionDesc: String = "",
    val isBlank: Boolean = false
) {
    constructor(
        pinNumber: Int,
        name: String,
        wireColor: String,
        colorHex: String,
        functionDesc: String
    ) : this(
        pinNumber = pinNumber,
        name = name,
        wireColor = wireColor,
        colorHex = colorHex,
        secondaryColorHex = null,
        secondaryColorName = null,
        functionDesc = functionDesc,
        isBlank = false
    )

    val isBicolor: Boolean
        get() = !secondaryColorHex.isNullOrBlank()

    val isBlankPin: Boolean
        get() = isBlank ||
                wireColor.contains("فارغ") ||
                wireColor.contains("بدون") ||
                name.contains("فارغ") ||
                name.contains("فاضي") ||
                name.contains("NC", ignoreCase = true)

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
        if (isBlank) {
            map["isBlank"] = true
        }
        return map
    }
}
