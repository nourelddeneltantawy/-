package com.example.data.model

data class MotorPinPreset(
    val id: String,
    val functionName: String,     // اسم الطرف (تاكو، شربون، فيوز، ملف مشترك، ملف كبير، طرف فارغ)
    val colorName: String,        // اسم اللون (برتقالي، بنفسجي، أصفر، أخضر، رصاصي، زهري، أزرق، أبيض، أحمر، فارغ)
    val colorHex: String,         // كود اللون Hex
    val description: String,      // شرح تفصيلي لطبيعة الطرف
    val isBlank: Boolean = false  // طرف فارغ بدون سلك
)

object MotorPinPresets {
    /**
     * الألوان التسعة المحددة في الورقة المرفقة مع دمج اسم كل طرف مع لونه الخاص بدقة:
     * 1. تاكو / برتقالي + بنفسجي
     * 2. شربون / أصفر + أخضر
     * 3. فيوز / رصاصي + زهري
     * 4. طرف ملف مشترك / أزرق
     * 5. طرفين ملف كبير / أبيض + أحمر
     * بالإضافة إلى: طرف فارغ (مكان فاضي ليس فيه سلك)
     */
    val PRESETS = listOf(
        // تاكو / برتقالي + بنفسجي
        MotorPinPreset(
            id = "tacho_orange",
            functionName = "تاكو (Tacho)",
            colorName = "برتقالي",
            colorHex = "#F97316",
            description = "طرف حساس السرعة تاكو جنريتور 1 (Tacho Generator)"
        ),
        MotorPinPreset(
            id = "tacho_purple",
            functionName = "تاكو (Tacho)",
            colorName = "بنفسجي",
            colorHex = "#9333EA",
            description = "طرف حساس السرعة تاكو جنريتور 2 (Tacho Generator)"
        ),

        // شربون / أصفر + أخضر
        MotorPinPreset(
            id = "brush_yellow",
            functionName = "شربون (Rotor)",
            colorName = "أصفر",
            colorHex = "#EAB308",
            description = "طرف شربون الموبينة الدوارة 1 (Armature Carbon Brush)"
        ),
        MotorPinPreset(
            id = "brush_green",
            functionName = "شربون (Rotor)",
            colorName = "أخضر",
            colorHex = "#16A34A",
            description = "طرف شربون الموبينة الدوارة 2 (Armature Carbon Brush)"
        ),

        // فيوز / رصاصي + زهري
        MotorPinPreset(
            id = "fuse_gray",
            functionName = "فيوز (Thermal)",
            colorName = "رصاصي",
            colorHex = "#64748B",
            description = "طرف فيوز الحماية الحرارية الداخلية 1 (Thermal Protector)"
        ),
        MotorPinPreset(
            id = "fuse_pink",
            functionName = "فيوز (Thermal)",
            colorName = "زهري",
            colorHex = "#EC4899",
            description = "طرف فيوز الحماية الحرارية الداخلية 2 (Thermal Protector)"
        ),

        // طرف ملف مشترك / أزرق
        MotorPinPreset(
            id = "stator_common_blue",
            functionName = "ملف مشترك (Common)",
            colorName = "أزرق",
            colorHex = "#2563EB",
            description = "الطرف المشترك لملفات الثابت (Stator Field Common)"
        ),

        // طرفين ملف كبير / أبيض + أحمر
        MotorPinPreset(
            id = "stator_coil_white",
            functionName = "ملف كبير (Coil)",
            colorName = "أبيض",
            colorHex = "#F8FAFC",
            description = "طرف ملف السرعة للثابت الرئيسي 1 (Stator Main Coil)"
        ),
        MotorPinPreset(
            id = "stator_coil_red",
            functionName = "ملف كبير (Coil)",
            colorName = "أحمر",
            colorHex = "#EF4444",
            description = "طرف ملف السرعة للثابت الرئيسي 2 (Stator Main Coil)"
        ),

        // طرف فارغ (مكان فاضي ليس فيه سلك)
        MotorPinPreset(
            id = "blank_empty",
            functionName = "طرف فارغ (فاضي)",
            colorName = "فارغ (بدون سلك)",
            colorHex = "#94A3B8",
            description = "مكان فاضي داخل فيشة الموتور ليس فيه سلك (NC / Empty Slot)",
            isBlank = true
        )
    )

    fun findPresetByColor(colorName: String): MotorPinPreset? {
        return PRESETS.firstOrNull { it.colorName.equals(colorName.trim(), ignoreCase = true) }
    }
}
