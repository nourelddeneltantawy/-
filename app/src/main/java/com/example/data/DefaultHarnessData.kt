package com.example.data

import com.example.data.model.AppliancePart
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.data.model.Pinout

object DefaultHarnessData {
    val sampleHarnesses = listOf(
        Harness(
            id = "harness_lg_smart_inverter",
            name = "ضفيرة غسالة إل جي انفرتر 10 طرف (كاملة)",
            brand = "إل جي (LG)",
            model = "Direct Drive Inverter",
            notes = "الضفيرة الرئيسية الرابطة بين كارتة الباور وماتور الدفع المباشر وحساس الهال سينسور وطلمبة التصريف ولك الباب",
            boardPins = listOf(
                HarnessPin(1, "تغذية ماتور U", "أحمر", "#EF4444", null, null, "ماتور", 1, "3-Phase U"),
                HarnessPin(2, "تغذية ماتور V", "أزرق", "#2563EB", null, null, "ماتور", 2, "3-Phase V"),
                HarnessPin(3, "تغذية ماتور W", "أصفر", "#EAB308", null, null, "ماتور", 3, "3-Phase W"),
                HarnessPin(4, "أرضي حماية الماتور", "أصفر / أخضر", "#EAB308", "#16A34A", "أخضر", "ماتور", 4, "Ground"),
                HarnessPin(5, "إشارة حساس هول A", "أبيض", "#F8FAFC", null, null, "ماتور", 5, "Hall 5V"),
                HarnessPin(6, "إشارة حساس هول B", "رمادي", "#64748B", null, null, "ماتور", 6, "Hall Signal"),
                HarnessPin(7, "تغذية لك الباب L", "بني", "#854D0E", null, null, "لوك الباب", 1, "220V Live"),
                HarnessPin(8, "نيوترال لك الباب N", "أزرق فاتح", "#06B6D4", null, null, "لوك الباب", 2, "220V Neutral"),
                HarnessPin(9, "إشارة إغلاق الباب C", "برتقالي", "#F97316", null, null, "لوك الباب", 3, "Door Switch Return"),
                HarnessPin(10, "طلمبة الطرد Drain", "بنفسجي", "#9333EA", null, null, "طلمبة الطرد", 1, "220V Pump")
            ),
            bottomPins = listOf(
                HarnessPin(1, "ملف ماتور U", "أحمر", "#EF4444", null, null, "ماتور", 1, "U Phase Coil"),
                HarnessPin(2, "ملف ماتور V", "أزرق", "#2563EB", null, null, "ماتور", 2, "V Phase Coil"),
                HarnessPin(3, "ملف ماتور W", "أصفر", "#EAB308", null, null, "ماتور", 3, "W Phase Coil"),
                HarnessPin(4, "أرضي الشاسيه", "أصفر / أخضر", "#EAB308", "#16A34A", "أخضر", "ماتور", 4, "Ground Pin"),
                HarnessPin(5, "حساس هال Vcc", "أبيض", "#F8FAFC", null, null, "ماتور", 5, "Hall Vcc"),
                HarnessPin(6, "حساس هال Out", "رمادي", "#64748B", null, null, "ماتور", 6, "Hall Out"),
                HarnessPin(1, "لك الباب (Live)", "بني", "#854D0E", null, null, "لوك الباب", 7, "Lock Terminal L"),
                HarnessPin(2, "لك الباب (Neutral)", "أزرق فاتح", "#06B6D4", null, null, "لوك الباب", 8, "Lock Terminal N"),
                HarnessPin(3, "كونتاكت اللوك (Common)", "برتقالي", "#F97316", null, null, "لوك الباب", 9, "Lock Terminal C"),
                HarnessPin(1, "تغذية طلمبة الطرد", "بنفسجي", "#9333EA", null, null, "طلمبة الطرد", 10, "Pump L")
            ),
            orderIndex = 0
        ),
        Harness(
            id = "harness_universal_collector",
            name = "ضفيرة ماتور يونيفرسال شاربون 8 طرف مع تاكو",
            brand = "يونيفرسال / إيطالي",
            model = "Universal 8-Pin",
            notes = "مخطط الضفيرة لماتور شاربون عادي مستخدم في زانوسي، إيديال، يونيفرسال، وإندست مع تاكوجينيراتور وحماية حرارية",
            boardPins = listOf(
                HarnessPin(1, "تاكوجينيراتور 1 (Tacho)", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 1, "Tacho Signal"),
                HarnessPin(2, "تاكوجينيراتور 2 (Tacho)", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 2, "Tacho Signal"),
                HarnessPin(3, "حساس حراري Overload", "أسود", "#1E293B", null, null, "ماتور", 3, "Thermal Switch"),
                HarnessPin(4, "حساس حراري Overload", "أسود", "#1E293B", null, null, "ماتور", 4, "Thermal Switch"),
                HarnessPin(5, "شربون يمين (Stator R)", "أحمر", "#EF4444", null, null, "ماتور", 5, "Brush Right"),
                HarnessPin(6, "شربون يسار (Stator L)", "أبيض", "#F8FAFC", null, null, "ماتور", 6, "Brush Left"),
                HarnessPin(7, "ملف العضو الثابت تفريعة 1", "بني", "#854D0E", null, null, "ماتور", 7, "Stator Coil 1"),
                HarnessPin(8, "ملف العضو الثابت تفريعة 2", "برتقالي", "#F97316", null, null, "ماتور", 8, "Stator Coil 2")
            ),
            bottomPins = listOf(
                HarnessPin(1, "طرف التاكو 1", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 1, "Tacho Pin 1"),
                HarnessPin(2, "طرف التاكو 2", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 2, "Tacho Pin 2"),
                HarnessPin(3, "أوفرلود طرف 1", "أسود", "#1E293B", null, null, "ماتور", 3, "Protector 1"),
                HarnessPin(4, "أوفرلود طرف 2", "أسود", "#1E293B", null, null, "ماتور", 4, "Protector 2"),
                HarnessPin(5, "شربون ماتور 1", "أحمر", "#EF4444", null, null, "ماتور", 5, "Carbon Brush 1"),
                HarnessPin(6, "شربون ماتور 2", "أبيض", "#F8FAFC", null, null, "ماتور", 6, "Carbon Brush 2"),
                HarnessPin(7, "ملف ثابت 1", "بني", "#854D0E", null, null, "ماتور", 7, "Field Coil 1"),
                HarnessPin(8, "ملف ثابت 2", "برتقالي", "#F97316", null, null, "ماتور", 8, "Field Coil 2")
            ),
            orderIndex = 1
        )
    )

    val sampleParts = listOf(
        AppliancePart(
            id = "part_door_lock_ptc_3pin",
            name = "لوك باب حراري PTC ثلاثي الأطراف (1-2-3)",
            partType = "لوك باب",
            brand = "زانوسي / بيكو / سامسونج",
            model = "PTC 3-Pin Interlock (L-N-C)",
            resistanceValue = "850 - 1300 أوم (بين 1 و 3)",
            operatingVoltage = "220V - 240V AC",
            testMethod = "الفحص بالسيريا أو الأومتر: قياس طرفي السخان PTC (1 و 3) يعطي حوالي 1000 أوم. عند تسخين القرص يغلق المفتاح بين (2 و 3) لتمرير التيار للكارتة وإشارة أن الباب مقفول.",
            notes = "الطرف 1: تغذية L | الطرف 2: راجع الكارتة C | الطرف 3: نيوترال N",
            pinouts = listOf(
                Pinout(1, "طرف سخان اللوك (L)", "بني", "#854D0E", null, null, "تغذية 220V لسخان الـ PTC الداخلي"),
                Pinout(2, "طرف ريلاي الأمان (C)", "أبيض", "#F8FAFC", null, null, "كونتاكت توصيل الكارتة بعد إغلاق الباب"),
                Pinout(3, "طرف النيوترال (N)", "أزرق", "#2563EB", null, null, "الأرضي المشترك لسخان الـ PTC")
            ),
            orderIndex = 0
        ),
        AppliancePart(
            id = "part_electronic_pressure_switch",
            name = "ميزان ماء إلكتروني (بريستات ترددية 3 طرف)",
            partType = "ميزان ماء / ليفل",
            brand = "سامسونج / إل جي / دايو",
            model = "Electronic Frequency Pressure Sensor",
            resistanceValue = "22 - 28 أوم بين طرفي الملف",
            operatingVoltage = "5V DC (تردد من 21kHz إلى 26kHz)",
            testMethod = "يقاس التردد بالملتيميتر أو راسم الإشارة (أوسيلوسكوب): بدون ماء يخرج تردد ~26.5kHz، ومع امتلاء الحلة بالماء ينخفض التردد تدريجياً حتى ~22.0kHz. فحص المقاومة بين الطرف 1 و 3 يكون قرابة 25 أوم.",
            notes = "الطرف 1: ملف L1 | الطرف 2: أرضي مشترك GND / مكثف | الطرف 3: ملف L2",
            pinouts = listOf(
                Pinout(1, "خرج إشارة التردد 1", "أصفر", "#EAB308", null, null, "توصيل مع مذبذب كارتة التحكم"),
                Pinout(2, "الأرضي المشترك (GND)", "أسود", "#1E293B", null, null, "نقطة منتصف المكثفات الداخلية"),
                Pinout(3, "خرج إشارة التردد 2", "أزرق", "#2563EB", null, null, "توصيل الملف الثاني مع الكارتة")
            ),
            orderIndex = 1
        ),
        AppliancePart(
            id = "part_drain_pump_magnetic",
            name = "طلمبة طرد مياه مغناطيسية (30W)",
            partType = "طلمبة طرد",
            brand = "أسكول (Ascoll) إيطالي",
            model = "Magnetic Synchronous 30W",
            resistanceValue = "140 - 180 أوم",
            operatingVoltage = "220V - 240V AC",
            testMethod = "قياس الملف بالأومتر يعطي من 140 إلى 180 أوم. الفحص الميكانيكي: لف الريشة باليد يجب أن تشعر بنقرة مغناطيسية كل ربع لفة بدون خشونة.",
            notes = "طرفين تغذية 220V بدون قطبية",
            pinouts = listOf(
                Pinout(1, "تغذية طلمبة 1", "بنفسجي", "#9333EA", null, null, "خط التغذية 220V من كارتة الترياك"),
                Pinout(2, "تغذية طلمبة 2 (N)", "أزرق", "#2563EB", null, null, "النيوترال المشترك")
            ),
            orderIndex = 2
        )
    )
}
