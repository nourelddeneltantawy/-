package com.example.data

import com.example.data.model.Motor
import com.example.data.model.Pinout

object DefaultMotorsData {
    fun getDefaultMotors(userId: String): List<Motor> {
        return listOf(
            Motor(
                id = "motor_unionaire_univ_1",
                userId = userId,
                name = "ماتور يونيون إير يونيفرسال (كاربون بروش)",
                brand = "Unionaire",
                model = "UW-600 / UW-800",
                motorType = "Universal (شربون)",
                notes = "ماتور سرعات شربون يونيون إير شائع في الغسالات الفوق أوتوماتيك والأوتوماتيك، مقاومة التاكو بين 65 إلى 70 أوم، تأكد من سلامة الشربون ونظافة الكوليكتور قبل التوصيل.",
                imageUrl = "",
                pinouts = listOf(
                    Pinout(1, "تاكو 1 (Tacho Generator)", "أصفر", "#EAB308", "طرف حساس سرعة الدوران للدائرة الإلكترونية"),
                    Pinout(2, "تاكو 2 (Tacho Generator)", "أصفر", "#EAB308", "طرف حساس سرعة الدوران للدائرة الإلكترونية"),
                    Pinout(3, "شربون 1 (Rotor / Brush 1)", "أحمر", "#EF4444", "توصيل الفرشاة الكربونية الدوارة الأولى"),
                    Pinout(4, "شربون 2 (Rotor / Brush 2)", "رمادي", "#6B7280", "توصيل الفرشاة الكربونية الدوارة الثانية"),
                    Pinout(5, "ملف الستاتور 1 (Stator Common)", "أزرق", "#3B82F6", "بداية ملفات الثابت المشتركة"),
                    Pinout(6, "ملف الستاتور 2 (Stator Tap / High Speed)", "بني", "#854D0E", "تفريعة سرعة الدوران العالية (العصر)"),
                    Pinout(7, "ملف الستاتور 3 (Stator Low Speed)", "أسود", "#1F2937", "تفريعة سرعة الغسيل الهادئة")
                )
            ),
            Motor(
                id = "motor_samsung_bldc_inverter",
                userId = userId,
                name = "ماتور سامسونج إنفرتر الدفع المباشر (Direct Drive BLDC)",
                brand = "Samsung",
                model = "EcoBubble WW80 / WW90",
                motorType = "Inverter BLDC (3-Phase)",
                notes = "ماتور سامسونج إنفرتر ثلاثي الأوجه بدون شربون، توصيل U-V-W متساوي المقاومة بدقة (حوالي 12.5 أوم بين كل طرفين). لا يتم توصيله مباشرة على تيار 220 فولت دون كارتة الإنفرتر.",
                imageUrl = "",
                pinouts = listOf(
                    Pinout(1, "Phase U (الطور الأول)", "أزرق", "#3B82F6", "تغذية الطور U من موديول الإنفرتر IPM"),
                    Pinout(2, "Phase V (الطور الثاني)", "أحمر", "#EF4444", "تغذية الطور V من موديول الإنفرتر IPM"),
                    Pinout(3, "Phase W (الطور الثالث)", "أبيض", "#E2E8F0", "تغذية الطور W من موديول الإنفرتر IPM"),
                    Pinout(4, "Hall Sensor VCC (+5V)", "برتقالي", "#F97316", "تغذية حساسات الموضع هول سنسور"),
                    Pinout(5, "Hall Sensor GND (أرضي الحساس)", "أسود", "#1F2937", "أرضي حساسات الموضع"),
                    Pinout(6, "Hall Signal A", "أخضر", "#22C55E", "إشارة هول سنسور للموقع الزاوي A"),
                    Pinout(7, "Hall Signal B", "أزرق فاتح", "#38BDF8", "إشارة هول سنسور للموقع الزاوي B")
                )
            ),
            Motor(
                id = "motor_lg_direct_drive",
                userId = userId,
                name = "ماتور إل جي إنفرتر دايركت درايف (LG Direct Drive)",
                brand = "LG",
                model = "F4J6 / F1496",
                motorType = "Inverter Direct Drive",
                notes = "ماتور غسالات إل جي سمارت إنفرتر، متصل بحلة الغسيل مباشرة بدون سير. ملفات الستاتور 3 أطراف متساوية تماماً (حوالي 8.5 إلى 9.2 أوم)، فحص فيشة الهول سنسور ضروري عند ظهور رمز الخطأ LE.",
                imageUrl = "",
                pinouts = listOf(
                    Pinout(1, "الطور الأول U", "أزرق", "#3B82F6", "خرج كارتة الإنفرتر الطور U"),
                    Pinout(2, "الطور الثاني V", "أحمر", "#EF4444", "خرج كارتة الإنفرتر الطور V"),
                    Pinout(3, "الطور الثالث W", "أصفر", "#EAB308", "خرج كارتة الإنفرتر الطور W"),
                    Pinout(4, "أرضي الحماية (Earth)", "أخضر/أصفر", "#10B981", "تأريض شاسيه الماتور للحماية من الصعق")
                )
            ),
            Motor(
                id = "motor_whirlpool_ceset_8pin",
                userId = userId,
                name = "ماتور ويرلبول / زانوسي 8 أطراف سيسيت (CESET 8-Pin)",
                brand = "Whirlpool",
                model = "MCA 38/64 - 148",
                motorType = "Universal (شربون)",
                notes = "ماتور إيطالي سيسيت شائع في غسالات زانوسي، إيديال، ويرلبول. يحتوي على فيوز حراري مدمج داخل الملفات للحماية من السخونة الزائدة.",
                imageUrl = "",
                pinouts = listOf(
                    Pinout(1, "تاكو 1", "أحمر", "#EF4444", "مولد نبضات السرعة التاكو"),
                    Pinout(2, "تاكو 2", "أحمر", "#EF4444", "مولد نبضات السرعة التاكو"),
                    Pinout(3, "شربون 1", "أسود", "#1F2937", "طرف الشربون الأيمن"),
                    Pinout(4, "شربون 2", "رمادي", "#6B7280", "طرف الشربون الأيسر"),
                    Pinout(5, "ملف ستاتور 1", "بني", "#854D0E", "طرف الثابت"),
                    Pinout(6, "ملف ستاتور 2", "أزرق", "#3B82F6", "طرف الثابت المشترك"),
                    Pinout(7, "أوفرلود حراري 1 (Thermal Protector)", "أبيض", "#E2E8F0", "فيوز الحماية الحرارية داخل الملفات"),
                    Pinout(8, "أوفرلود حراري 2 (Thermal Protector)", "أبيض", "#E2E8F0", "تابع فيوز الحماية الحرارية")
                )
            ),
            Motor(
                id = "motor_bosch_maxx_universal",
                userId = userId,
                name = "ماتور بوش ماكس 7 أطراف (Bosch Maxx / Siemens)",
                brand = "Bosch",
                model = "Maxx 6 / Maxx 7 Classixx",
                motorType = "Universal (شربون)",
                notes = "ماتور ألماني بوش يتميز بنعومة الدوران، فحص كوليكتور الماتور وتأكد من عدم وجود تفحيم أو تآكل غير متساوٍ في الشربون.",
                imageUrl = "",
                pinouts = listOf(
                    Pinout(1, "تاكو A", "أبيض", "#E2E8F0", "حساس السرعة تاكو"),
                    Pinout(2, "تاكو B", "أبيض", "#E2E8F0", "حساس السرعة تاكو"),
                    Pinout(3, "شربون 1", "أحمر", "#EF4444", "الفرشاة الكربونية"),
                    Pinout(4, "شربون 2", "أخضر", "#22C55E", "الفرشاة الكربونية المقابلة"),
                    Pinout(5, "ستاتور كامل", "بني", "#854D0E", "الملف الكامل لأعلى سرعة دوران"),
                    Pinout(6, "ستاتور فرعي", "رمادي", "#6B7280", "نقطة الوسط لسرعة الغسيل العادية"),
                    Pinout(7, "ستاتور مشترك", "أزرق", "#3B82F6", "الطرف المشترك لملفات الثابت")
                )
            )
        )
    }
}
