package com.example.data

import com.example.data.model.Motor
import com.example.data.model.Pinout

object DefaultMotorsData {
    fun getDefaultMotors(userId: String): List<Motor> {
        return listOf(
            Motor(
                id = "motor_unionaire_8pin",
                userId = userId,
                name = "ماتور يونيون إير / سامسونج (8 أطراف)",
                brand = "يونيون إير / سامسونج",
                model = "C.E.SET MCA 38/64-148",
                motorType = "شربون سرعات (Universal)",
                notes = "ماتور شربون كلاسيكي واسع الانتشار في غسالات يونيون إير وسامسونج ودايو. مقاومة التاكو حوالي 60-70 أوم، مقاومة ملف الشربون 2-4 أوم.",
                imageUrl = "unionaire_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "تاكو 1 (Tachometer 1)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "طرف مولد نبضات السرعة (تاكو) متصل بحساس الماجنتيك على ذيل الماتور"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "تاكو 2 (Tachometer 2)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "الطرف الثاني لمولد نبضات السرعة لقراءة الـ RPM في الكارتة"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "شربون 1 (Carbon Brush 1)",
                        wireColor = "أحمر",
                        colorHex = "#EF4444",
                        functionDesc = "فرشاة الشربون الأولى المتصلة بالعضو الدوار (Rotor/Armature)"
                    ),
                    Pinout(
                        pinNumber = 4,
                        name = "ملف ثابت 1 (Stator Coil 1)",
                        wireColor = "بني",
                        colorHex = "#854D0E",
                        functionDesc = "بداية ملفات الجزء الثابت لتوليد المجال المغناطيسي الرئيسي"
                    ),
                    Pinout(
                        pinNumber = 5,
                        name = "ملف وسط نصفي (Stator Tap)",
                        wireColor = "برتقالي",
                        colorHex = "#F97316",
                        functionDesc = "تفريعة وسطية من ملف الستاتور لسرعة الغسيل الهادئة وعزم الدوران"
                    ),
                    Pinout(
                        pinNumber = 6,
                        name = "شربون 2 (Carbon Brush 2)",
                        wireColor = "أزرق",
                        colorHex = "#2563EB",
                        functionDesc = "فرشاة الشربون المقابلة لتكتمل دائرة العضو الدوار مع ريليهات العكس"
                    ),
                    Pinout(
                        pinNumber = 7,
                        name = "أوفرلود حراري 1 (Thermal Overload)",
                        wireColor = "أبيض",
                        colorHex = "#94A3B8",
                        functionDesc = "مفتاح حراري مدمج داخل الملفات يقطع الدائرة عند ارتفاع الحرارة فوق 130°C"
                    ),
                    Pinout(
                        pinNumber = 8,
                        name = "أوفرلود حراري 2 (Thermal Overload)",
                        wireColor = "أبيض",
                        colorHex = "#94A3B8",
                        functionDesc = "طرف العودة للحماية الحرارية متصل تسلسلياً مع خط التغذية"
                    )
                )
            ),
            Motor(
                id = "motor_lg_direct_drive",
                userId = userId,
                name = "ماتور ال جي دايركت درايف (LG Direct Drive BLDC)",
                brand = "ال جي (LG)",
                model = "F1496 / 6871ER1078T",
                motorType = "انفرتر دفع مباشر (Inverter DD)",
                notes = "محرك انفرتر 3 أطوار بدون سيور، مقاومة متطابقة بين الأطوار الثلاثة U-V-W وتساوي حوالي 8-11 أوم. يحتوي على فيشة حساس هول Hall Sensor منفصلة لقياس الموضع والسرعة.",
                imageUrl = "lg_dd_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "ملف طور U (Phase U)",
                        wireColor = "أحمر",
                        colorHex = "#DC2626",
                        functionDesc = "الطور الأول للستاتور متصل بموديول IPM في كارتة الانفرتر"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "ملف طور V (Phase V)",
                        wireColor = "أبيض",
                        colorHex = "#F1F5F9",
                        functionDesc = "الطور الثاني لمولد الحركة المغناطيسية المتزامنة"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "ملف طور W (Phase W)",
                        wireColor = "أزرق",
                        colorHex = "#1D4ED8",
                        functionDesc = "الطور الثالث للستاتور"
                    ),
                    Pinout(
                        pinNumber = 4,
                        name = "تغذية حساس هول (+5V VCC)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "جهد 5 فولت مستمر لتغذية آي سي حساسات هول"
                    ),
                    Pinout(
                        pinNumber = 5,
                        name = "إشارة هول 1 (Hall Signal A)",
                        wireColor = "أزرق فاتح",
                        colorHex = "#38BDF8",
                        functionDesc = "نبضات موضع الدوار للميكروكونترولر لحساب زاوية التشغيل"
                    ),
                    Pinout(
                        pinNumber = 6,
                        name = "إشارة هول 2 (Hall Signal B)",
                        wireColor = "أخضر",
                        colorHex = "#22C55E",
                        functionDesc = "نبضات الموضع للاتجاه والسرعة"
                    ),
                    Pinout(
                        pinNumber = 7,
                        name = "أرضي حساس هول (GND)",
                        wireColor = "رمادي",
                        colorHex = "#64748B",
                        functionDesc = "الطرف السالب المشترك لحساسات هول"
                    )
                )
            ),
            Motor(
                id = "motor_samsung_digital_inverter",
                userId = userId,
                name = "ماتور سامسونج ديجيتال انفرتر (Samsung DIT)",
                brand = "سامسونج (Samsung)",
                model = "EcoBubble DIT-1200",
                motorType = "انفرتر حزام (BLDC Inverter)",
                notes = "ماتور BLDC فائق الكفاءة لغسالات سامسونج إيكو بابل، يتميز بهدوء تام وعمر طويل. المقاومة بين أي طرفين من أطراف الطاقة U, V, W متساوية بدقة (حوالي 5.5 أوم).",
                imageUrl = "samsung_dit_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "طور المحرك U (Motor Phase U)",
                        wireColor = "أزرق + أبيض",
                        colorHex = "#3B82F6",
                        functionDesc = "خط الطاقة الأساسي الأول من موديول الانفرتر"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "طور المحرك V (Motor Phase V)",
                        wireColor = "أحمر + أسود",
                        colorHex = "#EF4444",
                        functionDesc = "خط الطاقة الأساسي الثاني"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "طور المحرك W (Motor Phase W)",
                        wireColor = "أصفر + أخضر",
                        colorHex = "#10B981",
                        functionDesc = "خط الطاقة الأساسي الثالث"
                    ),
                    Pinout(
                        pinNumber = 4,
                        name = "حساس تاكو انفرتر 1 (FG Signal)",
                        wireColor = "بنفسجي",
                        colorHex = "#8B5CF6",
                        functionDesc = "إشارة التردد المغناطيسي (Frequency Generator) لمعرفة السرعة بدقة"
                    ),
                    Pinout(
                        pinNumber = 5,
                        name = "حساس تاكو انفرتر 2 (FG Signal)",
                        wireColor = "برتقالي + بنفسجي",
                        colorHex = "#A855F7",
                        functionDesc = "طرف العودة لإشارة التردد المغناطيسي"
                    )
                )
            ),
            Motor(
                id = "motor_indesit_ceset_6pin",
                userId = userId,
                name = "ماتور إنديست / أريستون 6 أطراف (Indesit / Ariston)",
                brand = "إنديست / أريستون",
                model = "CESET MCA 52/64",
                motorType = "شربون (Universal)",
                notes = "ماتور كلاسيكي بدون تفريعة وسطى، أطرافه واضحة ومباشرة: طرفين تاكو، طرفين شربون، وطرفين لملف الستاتور. مقاومة التاكو 115-180 أوم.",
                imageUrl = "indesit_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "تاكو 1 (Tachometer Coil)",
                        wireColor = "برتقالي",
                        colorHex = "#F97316",
                        functionDesc = "حساس سرعة الدوران الخلفي"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "تاكو 2 (Tachometer Coil)",
                        wireColor = "برتقالي",
                        colorHex = "#F97316",
                        functionDesc = "حساس سرعة الدوران الخلفي"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "ملف الستاتور 1 (Stator Field 1)",
                        wireColor = "أزرق",
                        colorHex = "#2563EB",
                        functionDesc = "بداية ملفات الحقل المغناطيسي الثابت"
                    ),
                    Pinout(
                        pinNumber = 4,
                        name = "شربون 1 (Rotor Brush 1)",
                        wireColor = "رمادي",
                        colorHex = "#64748B",
                        functionDesc = "طرف الفرشاة الكربونية الأولى للروتور"
                    ),
                    Pinout(
                        pinNumber = 5,
                        name = "ملف الستاتور 2 (Stator Field 2)",
                        wireColor = "أخضر",
                        colorHex = "#16A34A",
                        functionDesc = "نهاية ملفات الحقل المغناطيسي الثابت"
                    ),
                    Pinout(
                        pinNumber = 6,
                        name = "شربون 2 (Rotor Brush 2)",
                        wireColor = "أسود",
                        colorHex = "#1E293B",
                        functionDesc = "طرف الفرشاة الكربونية الثانية للروتور"
                    )
                )
            ),
            Motor(
                id = "motor_toshiba_top_load",
                userId = userId,
                name = "ماتور توشيبا فوق أوتوماتيك (Toshiba Top Load)",
                brand = "توشيبا (Toshiba)",
                model = "AW-B8080 / AW-DUG1700",
                motorType = "حثي مكثف (Capacitor Induction)",
                notes = "محرك حثي ذو اتجاهين مع مكثف تشغيل 12-14 ميكروفاراد. المقاومة بين الطرف المشترك وأي من طرفي الدوران متطابقة تماماً (حوالي 18-22 أوم).",
                imageUrl = "toshiba_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "طرف مشترك رئيسي (Common N)",
                        wireColor = "أزرق داكن",
                        colorHex = "#1E3A8A",
                        functionDesc = "خط النيوترال المشترك لكلا ملفي الدوران اليمين واليسار"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "دوران يمين (CW Rotation / Phase)",
                        wireColor = "أحمر",
                        colorHex = "#DC2626",
                        functionDesc = "متصل بأحد أقطاب المكثف، وعند تغذيته بـ 220V يدور الماتور باتجاه عقارب الساعة"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "دوران شمال (CCW Rotation / Phase)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "متصل بالقطب الآخر للمكثف، وعند تغذيته بـ 220V يدور الماتور عكس عقارب الساعة"
                    )
                )
            ),
            Motor(
                id = "motor_zanussi_7pin",
                userId = userId,
                name = "ماتور زانوسي / إلكترولوكس 7 أطراف (Zanussi / SOLE)",
                brand = "زانوسي / إلكترولوكس",
                model = "SOLE Type 20572",
                motorType = "شربون سرعات متعددة",
                notes = "ماتور شهير في غسالات زانوسي أيديال وإلكترولوكس. يشمل تفريعة لسرعة العصر الفائق والتدرج في سرعات الغسيل.",
                imageUrl = "zanussi_motor",
                pinouts = listOf(
                    Pinout(
                        pinNumber = 1,
                        name = "تاكو 1 (Tacho)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "حساس سرعة الماتور"
                    ),
                    Pinout(
                        pinNumber = 2,
                        name = "تاكو 2 (Tacho)",
                        wireColor = "أصفر",
                        colorHex = "#EAB308",
                        functionDesc = "حساس سرعة الماتور"
                    ),
                    Pinout(
                        pinNumber = 3,
                        name = "شربون 1 (Brush 1)",
                        wireColor = "أسود",
                        colorHex = "#0F172A",
                        functionDesc = "شربون العضو الدوار"
                    ),
                    Pinout(
                        pinNumber = 4,
                        name = "ستاتور سرعة عالية (Stator High Speed)",
                        wireColor = "أحمر",
                        colorHex = "#DC2626",
                        functionDesc = "ملف العصر والسرعات العالية (مقاومة أقل)"
                    ),
                    Pinout(
                        pinNumber = 5,
                        name = "ستاتور سرعة هادئة (Stator Low Speed)",
                        wireColor = "بني",
                        colorHex = "#78350F",
                        functionDesc = "ملف الغسيل الهادئ والعزم المرتفع"
                    ),
                    Pinout(
                        pinNumber = 6,
                        name = "ستاتور مشترك (Stator Common)",
                        wireColor = "أبيض",
                        colorHex = "#CBD5E1",
                        functionDesc = "الطرف المشترك لملفات الستاتور"
                    ),
                    Pinout(
                        pinNumber = 7,
                        name = "شربون 2 (Brush 2)",
                        wireColor = "رمادي",
                        colorHex = "#64748B",
                        functionDesc = "شربون العضو الدوار الثاني"
                    )
                )
            )
        )
    }
}
