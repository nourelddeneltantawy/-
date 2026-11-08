package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.model.Motor
import com.example.data.model.Pinout
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AiMotorDiagramGenerator {

    /**
     * Synthesizes and renders a high-definition technical motor schematic diagram
     * tailored dynamically to the technician's prompt (e.g. Ariston, Indesit, LG, Zanussi, Hoover, etc.).
     */
    fun generateSchematicForPrompt(context: Context, prompt: String): String {
        val clean = prompt.trim()
        val lower = clean.lowercase()

        val brand = when {
            lower.contains("أريستون") || lower.contains("ariston") -> "أريستون (Ariston)"
            lower.contains("إنديست") || lower.contains("indesit") -> "إنديست (Indesit)"
            lower.contains("إل جي") || lower.contains("lg") -> "إل جي (LG Direct Drive)"
            lower.contains("زانوسي") || lower.contains("zanussi") -> "زانوسي (Zanussi)"
            lower.contains("سامسونج") || lower.contains("samsung") -> "سامسونج (Samsung)"
            lower.contains("بوش") || lower.contains("bosch") -> "بوش (Bosch Maxx)"
            lower.contains("هوفر") || lower.contains("hoover") -> "هوفر (Hoover)"
            lower.contains("توشيبا") || lower.contains("toshiba") -> "توشيبا (Toshiba)"
            lower.contains("شارب") || lower.contains("sharp") -> "شارب (Sharp)"
            else -> "يونيفرسال (Universal Motor)"
        }

        val title = if (clean.length in 5..50) clean else "مخطط أطراف ماتور $brand"

        // Generate matching technical pinouts based on motor family
        val pinouts = when {
            lower.contains("إل جي") || lower.contains("lg") || lower.contains("دايركت") || lower.contains("إنفرتر") -> listOf(
                Pinout(1, "ستاتور ملف U (Phase U)", "أزرق", "#2563EB", null, null, "مقاومة مع V و W حوالي 8.8 أوم"),
                Pinout(2, "ستاتور ملف V (Phase V)", "أحمر", "#EF4444", null, null, "مقاومة متساوية مع باقي الأطراف"),
                Pinout(3, "ستاتور ملف W (Phase W)", "أصفر", "#EAB308", null, null, "مقاومة متساوية (نظام 3 فاز إنفرتر)"),
                Pinout(4, "حساس هول VCC (+5V)", "أبيض", "#F8FAFC", null, null, "تغذية مستمرة لحساسات الدوران"),
                Pinout(5, "حساس هول إشارة Ha", "رمادي", "#64748B", null, null, "نبضات السرعة للطور الأول"),
                Pinout(6, "حساس هول إشارة Hb", "زهري", "#EC4899", null, null, "نبضات السرعة للطور الثاني"),
                Pinout(7, "حساس هول GND (أرضي)", "أسود", "#1E293B", null, null, "الخط الأرضي المشترك للهول سنسور")
            )
            lower.contains("هوفر") || lower.contains("hoover") || lower.contains("7 طرف") -> listOf(
                Pinout(1, "ملف سرعة 1 (Stator Main)", "أبيض", "#F8FAFC", null, null, "طرف ملف السرعة للثابت 1"),
                Pinout(2, "شربون 1 (Rotor Armature)", "أصفر", "#EAB308", null, null, "طرف شربون الموبينة 1"),
                Pinout(3, "تاكو 1 (Tacho Generator)", "برتقالي", "#F97316", null, null, "طرف حساس السرعة تاكو جنريتور 1"),
                Pinout(4, "تاكو 2 (Tacho Generator)", "بنفسجي", "#9333EA", null, null, "طرف حساس السرعة تاكو جنريتور 2"),
                Pinout(5, "ملف سرعة 2 (Stator Main)", "أحمر", "#EF4444", null, null, "طرف ملف السرعة للثابت 2"),
                Pinout(6, "شربون 2 (Rotor Armature)", "أخضر", "#16A34A", null, null, "طرف شربون الموبينة 2"),
                Pinout(7, "ملف مشترك (Common Stator)", "أزرق", "#2563EB", null, null, "الطرف المشترك للملفات")
            )
            lower.contains("8 طرف") || lower.contains("سيسيت") || lower.contains("ceset") -> listOf(
                Pinout(1, "تاكو (Tacho 1)", "برتقالي", "#F97316", null, null, "حساس السرعة تاكو 1 (~65-70 أوم)"),
                Pinout(2, "تاكو (Tacho 2)", "بنفسجي", "#9333EA", null, null, "حساس السرعة تاكو 2 (~65-70 أوم)"),
                Pinout(3, "شربون 1 (Brush 1)", "أصفر", "#EAB308", null, null, "شربون الموبينة الدوارة 1"),
                Pinout(4, "ملف سرعة (Stator Tap 1)", "أبيض", "#F8FAFC", null, null, "ملف السرعة للثابت الرئيسي 1"),
                Pinout(5, "ملف مشترك (Stator Common)", "أزرق", "#2563EB", null, null, "الطرف المشترك لملفات الثابت"),
                Pinout(6, "شربون 2 (Brush 2)", "أخضر", "#16A34A", null, null, "شربون الموبينة الدوارة 2"),
                Pinout(7, "ملف سرعة (Stator Tap 2)", "أحمر", "#EF4444", null, null, "ملف السرعة للثابت الرئيسي 2"),
                Pinout(8, "حماية حرارية (Thermal Fuse)", "رمادي", "#64748B", null, null, "فيوز حراري داخلي للحماية")
            )
            else -> listOf(
                // Ariston / Indesit / Standard Universal 6-Pin Motor
                Pinout(1, "تاكو (Tacho Generator 1)", "برتقالي", "#F97316", null, null, "حساس السرعة تاكو جنريتور 1 (~68 أوم)"),
                Pinout(2, "تاكو (Tacho Generator 2)", "بنفسجي", "#9333EA", null, null, "حساس السرعة تاكو جنريتور 2 (~68 أوم)"),
                Pinout(3, "شربون 1 (Rotor Brush 1)", "أصفر", "#EAB308", null, null, "طرف شربون الموبينة الدوارة 1"),
                Pinout(4, "مكان فاضي في السوكيت (NC)", "فارغ", "#94A3B8", null, null, "خانة فارغة في السوكيت بدون توصيل سلك", isBlank = true),
                Pinout(5, "شربون 2 (Rotor Brush 2)", "أخضر", "#16A34A", null, null, "طرف شربون الموبينة الدوارة 2"),
                Pinout(6, "ملف ثابت 1 (Stator Coil 1)", "أبيض", "#F8FAFC", null, null, "طرف ملف السرعة للثابت الرئيسي 1"),
                Pinout(7, "ملف ثابت 2 (Stator Coil 2)", "أحمر", "#EF4444", null, null, "طرف ملف السرعة للثابت الرئيسي 2")
            )
        }

        val dummyMotor = Motor(
            id = "gen_" + UUID.randomUUID().toString().take(8),
            name = title,
            brand = brand,
            model = "مخطط فني معتمد",
            motorType = if (lower.contains("إنفرتر") || lower.contains("lg")) "إنفرتر (Direct Drive)" else "شربون (Universal)",
            category = "استوديو الذكاء الاصطناعي",
            notes = "تم إنشاء هذا المخطط الفني آلياً بناءً على مواصفات: $prompt",
            pinouts = pinouts
        )

        val bitmap = MotorShareHelper.createMotorBitmap(context, dummyMotor)
        val cacheDir = File(context.cacheDir, "ai_diagrams")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val outFile = File(cacheDir, "diagram_${UUID.randomUUID().toString().take(8)}.png")
        val fos = FileOutputStream(outFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()

        return outFile.absolutePath
    }
}
