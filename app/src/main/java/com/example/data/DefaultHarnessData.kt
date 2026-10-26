package com.example.data

import com.example.data.model.AppliancePart
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.data.model.Pinout

object DefaultHarnessData {
    val sampleHarnesses = listOf(
        Harness(
            id = "harness_lg_smart_inverter",
            name = "ضفيرة غسالة إل جي انفرتر شاملة (LG Direct Drive)",
            brand = "إل جي (LG)",
            model = "Direct Drive Inverter Front Load",
            notes = "المخطط الشامل لتوصيل كارتة الباور الرئيسية (Main PCB) مع ماتور الدفع المباشر، وحساس الليفل، ولوك الباب، والسخان، وطلمبة الطرد، وصمامات المياه.",
            boardImageUrl = "sample_lg_diagram",
            boardPins = listOf(
                // Sockets to Direct Drive Motor (أطراف الماتور وحساس الهال)
                HarnessPin(1, "تغذية ماتور U", "أحمر", "#EF4444", null, null, "ماتور", 1, "3-Phase U", false, 38f, 52f),
                HarnessPin(2, "تغذية ماتور V", "أصفر", "#EAB308", null, null, "ماتور", 2, "3-Phase V", false, 40f, 52f),
                HarnessPin(3, "تغذية ماتور W", "أزرق", "#2563EB", null, null, "ماتور", 3, "3-Phase W", false, 42f, 52f),
                HarnessPin(4, "أرضي حماية الماتور", "أصفر / أخضر", "#EAB308", "#16A34A", "أخضر", "ماتور", 4, "Ground", false, 28f, 48f),
                HarnessPin(5, "حساس هال Vcc (+5V)", "أبيض", "#F8FAFC", null, null, "ماتور", 5, "Hall Power", false, 34f, 50f),
                HarnessPin(6, "حساس هال إشارة A", "أزرق", "#2563EB", null, null, "ماتور", 6, "Hall Signal A", false, 36f, 50f),
                HarnessPin(7, "حساس هال إشارة B", "أحمر", "#EF4444", null, null, "ماتور", 7, "Hall Signal B", false, 38f, 50f),
                // Door Lock (لوك الباب)
                HarnessPin(8, "تغذية سخان اللوك L", "بني", "#854D0E", null, null, "لوك الباب", 1, "220V Live", false, 50f, 54f),
                HarnessPin(9, "نيوترال اللوك N", "أزرق", "#2563EB", null, null, "لوك الباب", 2, "220V Neutral", false, 52f, 54f),
                HarnessPin(10, "كونتاكت إغلاق الباب C", "وردي", "#EC4899", null, null, "لوك الباب", 3, "Door Switch", false, 54f, 54f),
                // Water Level Sensor (حساس الليفل الإلكتروني)
                HarnessPin(11, "حساس الليفل 5V (Vcc)", "برتقالي", "#F97316", null, null, "ليفل الماء", 1, "Sensor Vcc", false, 24f, 46f),
                HarnessPin(12, "حساس الليفل إشارة تردد Out", "بنفسجي", "#9333EA", null, null, "ليفل الماء", 2, "Frequency", false, 26f, 46f),
                HarnessPin(13, "حساس الليفل أرضي GND", "أزرق", "#2563EB", null, null, "ليفل الماء", 3, "Sensor GND", false, 28f, 46f),
                // Drain Pump (طلمبة الطرد)
                HarnessPin(14, "طلمبة الطرد كهرباء L", "أسود", "#1E293B", null, null, "طلمبة الطرد", 1, "Pump Live", false, 72f, 44f),
                HarnessPin(15, "طلمبة الطرد محايد N", "أزرق", "#2563EB", null, null, "طلمبة الطرد", 2, "Pump Neutral", false, 74f, 44f),
                // Heater & Thermistor (السخان وحساس الحرارة)
                HarnessPin(16, "سخان الغسالة كهرباء L", "أحمر", "#EF4444", null, null, "سخان وحساس حرارة", 1, "Heater L", false, 60f, 56f),
                HarnessPin(17, "سخان الغسالة محايد N", "أزرق", "#2563EB", null, null, "سخان وحساس حرارة", 2, "Heater N", false, 62f, 56f),
                HarnessPin(18, "حساس حرارة NTC 1", "أبيض", "#F8FAFC", null, null, "سخان وحساس حرارة", 3, "NTC Pin 1", false, 64f, 56f),
                HarnessPin(19, "حساس حرارة NTC 2", "أبيض", "#F8FAFC", null, null, "سخان وحساس حرارة", 4, "NTC Pin 2", false, 66f, 56f),
                // Water Inlet Valves (صمامات سحب المياه)
                HarnessPin(20, "صمام المياه الرئيسي L", "بني", "#854D0E", null, null, "صمام الدخول", 1, "Valve 1", false, 78f, 48f),
                HarnessPin(21, "صمام المياه الإضافي L", "برتقالي", "#F97316", null, null, "صمام الدخول", 2, "Valve 2", false, 80f, 48f),
                HarnessPin(22, "صمامات المياه نيوترال مشترك N", "أزرق", "#2563EB", null, null, "صمام الدخول", 3, "Valves N", false, 82f, 48f)
            ),
            bottomPins = listOf(
                // الماتور (Direct Drive Motor)
                HarnessPin(1, "فازة ملف الماتور U", "أحمر", "#EF4444", null, null, "ماتور", 1, "U Phase"),
                HarnessPin(2, "فازة ملف الماتور V", "أصفر", "#EAB308", null, null, "ماتور", 2, "V Phase"),
                HarnessPin(3, "فازة ملف الماتور W", "أزرق", "#2563EB", null, null, "ماتور", 3, "W Phase"),
                HarnessPin(4, "أرضي شاسيه الماتور", "أصفر / أخضر", "#EAB308", "#16A34A", "أخضر", "ماتور", 4, "Earth Pin"),
                HarnessPin(5, "حساس هال Vcc", "أبيض", "#F8FAFC", null, null, "ماتور", 5, "Hall Vcc"),
                HarnessPin(6, "حساس هال Signal A", "أزرق", "#2563EB", null, null, "ماتور", 6, "Hall Out A"),
                HarnessPin(7, "حساس هال Signal B", "أحمر", "#EF4444", null, null, "ماتور", 7, "Hall Out B"),
                // لوك الباب (Door Lock)
                HarnessPin(1, "تغذية سخان اللوك L (طرف 1)", "بني", "#854D0E", null, null, "لوك الباب", 8, "Lock L"),
                HarnessPin(2, "محايد اللوك N (طرف 3)", "أزرق", "#2563EB", null, null, "لوك الباب", 9, "Lock N"),
                HarnessPin(3, "كونتاكت الأمان C (طرف 2)", "وردي", "#EC4899", null, null, "لوك الباب", 10, "Lock C"),
                // ليفل الماء (Water Level Sensor)
                HarnessPin(1, "تغذية الليفل Vcc (طرف 1)", "برتقالي", "#F97316", null, null, "ليفل الماء", 11, "Level Vcc"),
                HarnessPin(2, "إشارة تردد الليفل Out (طرف 2)", "بنفسجي", "#9333EA", null, null, "ليفل الماء", 12, "Level Out"),
                HarnessPin(3, "أرضي الليفل GND (طرف 3)", "أزرق", "#2563EB", null, null, "ليفل الماء", 13, "Level GND"),
                // طلمبة الطرد (Drain Pump)
                HarnessPin(1, "تغذية طلمبة الطرد L", "أسود", "#1E293B", null, null, "طلمبة الطرد", 14, "Pump L"),
                HarnessPin(2, "نيوترال طلمبة الطرد N", "أزرق", "#2563EB", null, null, "طلمبة الطرد", 15, "Pump N"),
                // سخان وحساس حرارة (Heater & NTC)
                HarnessPin(1, "تغذية السخان L", "أحمر", "#EF4444", null, null, "سخان وحساس حرارة", 16, "Heater L"),
                HarnessPin(2, "نيوترال السخان N", "أزرق", "#2563EB", null, null, "سخان وحساس حرارة", 17, "Heater N"),
                HarnessPin(3, "حساس حرارة NTC طرف 1", "أبيض", "#F8FAFC", null, null, "سخان وحساس حرارة", 18, "NTC 1"),
                HarnessPin(4, "حساس حرارة NTC طرف 2", "أبيض", "#F8FAFC", null, null, "سخان وحساس حرارة", 19, "NTC 2"),
                // صمام الدخول (Water Inlet Valves)
                HarnessPin(1, "تغذية صمام 1 (بارد)", "بني", "#854D0E", null, null, "صمام الدخول", 20, "Valve 1"),
                HarnessPin(2, "تغذية صمام 2 (شطف/ساخن)", "برتقالي", "#F97316", null, null, "صمام الدخول", 21, "Valve 2"),
                HarnessPin(3, "محايد الصمامات المشترك N", "أزرق", "#2563EB", null, null, "صمام الدخول", 22, "Valve Common N")
            ),
            orderIndex = 0
        ),
        Harness(
            id = "harness_universal_collector",
            name = "ضفيرة ماتور يونيفرسال شاربون 8 طرف مع تاكو وأوفرلود",
            brand = "يونيفرسال / إيطالي",
            model = "Universal 8-Pin",
            notes = "مخطط الضفيرة لماتور شاربون عادي مستخدم في زانوسي، إيديال، يونيفرسال، وإندست مع تاكوجينيراتور وحماية حرارية ومكثف.",
            boardImageUrl = "",
            boardPins = listOf(
                HarnessPin(1, "تاكوجينيراتور 1 (Tacho)", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 1, "Tacho Signal", false, 25f, 40f),
                HarnessPin(2, "تاكوجينيراتور 2 (Tacho)", "أزرق / أبيض", "#2563EB", "#F8FAFC", "أبيض", "ماتور", 2, "Tacho Signal", false, 30f, 40f),
                HarnessPin(3, "حساس حراري Overload 1", "أسود", "#1E293B", null, null, "ماتور", 3, "Thermal Switch", false, 35f, 40f),
                HarnessPin(4, "حساس حراري Overload 2", "أسود", "#1E293B", null, null, "ماتور", 4, "Thermal Switch", false, 40f, 40f),
                HarnessPin(5, "شربون يمين (Stator R)", "أحمر", "#EF4444", null, null, "ماتور", 5, "Brush Right", false, 45f, 40f),
                HarnessPin(6, "شربون يسار (Stator L)", "أبيض", "#F8FAFC", null, null, "ماتور", 6, "Brush Left", false, 50f, 40f),
                HarnessPin(7, "ملف العضو الثابت تفريعة 1", "بني", "#854D0E", null, null, "ماتور", 7, "Stator Coil 1", false, 55f, 40f),
                HarnessPin(8, "ملف العضو الثابت تفريعة 2", "برتقالي", "#F97316", null, null, "ماتور", 8, "Stator Coil 2", false, 60f, 40f)
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
                Pinout(1, "تغذية إشارة التردد 1", "برتقالي", "#F97316", null, null, "ملف التردد L1"),
                Pinout(2, "أرضي حساس الليفل (GND)", "أزرق", "#2563EB", null, null, "أرضي الحساس ومكثف التردد الداخلي"),
                Pinout(3, "تغذية إشارة التردد 2", "بنفسجي", "#9333EA", null, null, "ملف التردد L2")
            ),
            orderIndex = 1
        )
    )
}
