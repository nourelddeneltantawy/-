package com.example.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Three-stage hierarchical location database:
 * Country -> Governorate/Province -> District/City/Neighborhood
 */
object LocationData {

    data class Country(
        val name: String,
        val flag: String,
        val governorates: Map<String, List<String>>
    )

    val countries: List<Country> = listOf(
        Country(
            name = "مصر",
            flag = "🇪🇬",
            governorates = mapOf(
                "القاهرة" to listOf(
                    "مدينة نصر", "مصر الجديدة", "المعادي", "التجمع الخامس", "وسط البلد",
                    "شبرا", "حلوان", "عين شمس", "المطرية", "الزيتون", "حدائق القبة",
                    "المرج", "المقطم", "مدينة السلام", "النزهة", "العباسية", "الوايلي", "المنيل"
                ),
                "الجيزة" to listOf(
                    "الدقي", "العجوزة", "الهرم", "فيصل", "مدينة 6 أكتوبر", "الشيخ زايد",
                    "العمرانية", "الوراق", "إمبابة", "بولاق الدكرور", "الحوامدية", "البدرشين",
                    "العياط", "الصف", "أطفيح", "أوسيم", "كرداسة", "منشأة القناطر"
                ),
                "الإسكندرية" to listOf(
                    "سموحة", "سيدي جابر", "ميامي", "العصافرة", "لوران", "الإبراهيمية",
                    "محرم بك", "المنتزه", "العجمي", "الدخيلة", "كرموز", "العامرية",
                    "برج العرب", "سيدي بشر", "رشدي", "كفر عبده", "ستانلي", "المنشية"
                ),
                "الدقهلية" to listOf(
                    "المنصورة", "ميت غمر", "طلخا", "دكرنس", "السنبلاوين", "شربين",
                    "بلقاس", "منية النصر", "أجا", "بني عبيد", "الجمالية", "المطرية",
                    "ميت سلسيل", "تمى الأمديد", "نبروه", "محلة دمنة"
                ),
                "الشرقية" to listOf(
                    "الزقازيق", "العاشر من رمضان", "بلبيس", "منيا القمح", "فاقوس", "أبو كبير",
                    "الحسينية", "كفر صقر", "أولاد صقر", "ديرب نجم", "الإبراهيمية", "مشتول السوق",
                    "أبو حماد", "القرين", "صان الحجر", "المنشية الجديدة"
                ),
                "القليوبية" to listOf(
                    "بنها", "شبرا الخيمة", "قليوب", "الخانكة", "القناطر الخيرية",
                    "طوخ", "شبين القناطر", "كفر شكر", "قها", "الخصوص", "العبور"
                ),
                "الغربية" to listOf(
                    "طنطا", "المحلة الكبرى", "كفر الزيات", "زفتى", "السنطة",
                    "بسيون", "سمنود", "قطور"
                ),
                "المنوفية" to listOf(
                    "شبين الكوم", "مدينة السادات", "منوف", "أشمون", "قويسنا",
                    "بركة السبع", "تلا", "الشهداء", "الباجور", "سرس الليان"
                ),
                "البحيرة" to listOf(
                    "دمنهور", "كفر الدوار", "إيتاي البارود", "أبو حمص", "كوم حمادة",
                    "حوش عيسى", "رشيد", "إدكو", "أبو المطامير", "الدلنجات", "المحمودية", "الرحمانية", "وادي النطرون"
                ),
                "كفر الشيخ" to listOf(
                    "كفر الشيخ", "دسوق", "فوه", "مطوبس", "سيدي سالم", "الرياض",
                    "بيلا", "الحامول", "بلطيم", "قلين", "برج البرلس"
                ),
                "دمياط" to listOf(
                    "دمياط", "رأس البر", "دمياط الجديدة", "فارسكور", "الزرقا", "كفر سعد", "كفر البطيخ", "عزبة البرج", "ميت أبو غالب", "الروضة"
                ),
                "بورسعيد" to listOf("حي الشرق", "حي العرب", "حي المناخ", "حي الضواحي", "بورفؤاد", "حي الزهور", "حي الجنوب"),
                "الإسماعيلية" to listOf("الإسماعيلية", "فايد", "القنطرة شرق", "القنطرة غرب", "التل الكبير", "أبو صوير", "القصاصين"),
                "السويس" to listOf("حي السويس", "حي الأربعين", "حي عتاقة", "حي فيصل", "حي الجناين"),
                "الفيوم" to listOf("الفيوم", "سنورس", "إطسا", "طامية", "يوسف الصديق", "أبشواي"),
                "بني سويف" to listOf("بني سويف", "الواسطى", "ناصر", "إهناسيا", "ببا", "سمسطا", "الفشن", "بني سويف الجديدة"),
                "المنيا" to listOf("المنيا", "ملوي", "مغاغة", "بني مزار", "سمالوط", "أبو قرقاص", "مطاي", "دير مواس", "العدوة"),
                "أسيوط" to listOf("أسيوط", "ديروط", "القوصية", "أبنوب", "منفلوط", "أبو تيج", "الغنايم", "ساحل سليم", "البداري", "صدفا", "الفتح"),
                "سوهاج" to listOf("سوهاج", "طهطا", "جرجا", "أخميم", "طما", "المراغة", "البلينا", "المنشأة", "ساقلتة", "دار السلام", "جهينة"),
                "قنا" to listOf("قنا", "نجع حمادي", "دشنا", "قوص", "فرشوط", "أبو تشت", "فاو قبلي", "نقادة", "الوقف"),
                "الأقصر" to listOf("الأقصر", "إسنا", "أرمنت", "طيبة", "القرنة", "البياضية", "الزينية"),
                "أسوان" to listOf("أسوان", "إدفو", "كوم أمبو", "نصر النوبة", "دراو", "أبو سمبل"),
                "البحر الأحمر" to listOf("الغردقة", "سفاجا", "القصير", "مرسى علم", "رأس غارب", "الشلاتين", "حلايب"),
                "الوادي الجديد" to listOf("الخارجة", "الداخلة", "الفرافرة", "باريس", "بلاط"),
                "مطروح" to listOf("مرسى مطروح", "العلمين", "الحمام", "الضبعة", "سيوة", "النجيلة", "براني", "السلوم"),
                "شمال سيناء" to listOf("العريش", "بئر العبد", "الشيخ زويد", "رفح", "الحسنة", "نخل"),
                "جنوب سيناء" to listOf("شرم الشيخ", "طور سيناء", "دهب", "نويبع", "رأس سدر", "طابا", "سانت كاترين", "أبو رديس")
            )
        ),
        Country(
            name = "السعودية",
            flag = "🇸🇦",
            governorates = mapOf(
                "منطقة الرياض" to listOf("الرياض", "الخرج", "الدرعية", "المجمعة", "الدوادمي", "وادي الدواسر", "الزلفي", "شقراء", "حوطة بني تميم"),
                "منطقة مكة المكرمة" to listOf("مكة المكرمة", "جدة", "الطائف", "القنفذة", "الليث", "رابغ", "خليص", "الجموم"),
                "المنطقة الشرقية" to listOf("الدمام", "الخبر", "الأحساء", "الجبيل", "القطيف", "حفر الباطن", "الخفجي", "رأس تنورة"),
                "منطقة المدينة المنورة" to listOf("المدينة المنورة", "ينبع", "العلا", "مهد الذهب", "بدر", "خيبر"),
                "منطقة القصيم" to listOf("بريدة", "عنيزة", "الرس", "المذنب", "البكيرية", "البدائع"),
                "منطقة عسير" to listOf("أبها", "خميس مشيط", "بيشة", "محايل عسير", "النماص", "أحد رفيدة"),
                "منطقة تبوك" to listOf("تبوك", "الوجه", "ضباء", "تيماء", "أملج", "حقل"),
                "منطقة حائل" to listOf("حائل", "بقعاء", "الغزالة", "الشنان"),
                "منطقة جازان" to listOf("جازان", "صبيا", "أبو عريش", "صامطة", "بيش", "الدرب"),
                "منطقة نجران" to listOf("نجران", "شرورة", "حبونا"),
                "منطقة الباحة" to listOf("الباحة", "بلجرشي", "المندق", "المخواة"),
                "منطقة الجوف" to listOf("سكاكا", "القريات", "دومة الجندل"),
                "منطقة الحدود الشمالية" to listOf("عرعر", "رفحاء", "طريف")
            )
        ),
        Country(
            name = "الإمارات",
            flag = "🇦🇪",
            governorates = mapOf(
                "دبي" to listOf("ديرة", "بر دبي", "مارينا دبي", "جميرا", "البرشاء", "القصيص", "الورقاء", "واحة السليكون", "داون تاون"),
                "أبوظبي" to listOf("مدينة أبوظبي", "العين", "الظفرة", "جزيرة الريم", "جزيرة ياس", "مصفح", "بني ياس", "خليفة أ"),
                "الشارقة" to listOf("مدينة الشارقة", "الذيد", "خورفكان", "كلباء", "دبا الحصن", "المجاز", "النهدة"),
                "عجمان" to listOf("مدينة عجمان", "النعيمية", "الراشدية", "الجرف", "مصفوت", "المنامة"),
                "رأس الخيمة" to listOf("رأس الخيمة", "الظيت", "النخيل", "الرمس", "الدقداقة"),
                "أم القيوين" to listOf("مدينة أم القيوين", "فلج المعلا", "الراعفة", "السلمة"),
                "الفجيرة" to listOf("مدينة الفجيرة", "دبا الفجيرة", "قدفع", "مربح")
            )
        ),
        Country(
            name = "الكويت",
            flag = "🇰🇼",
            governorates = mapOf(
                "العاصمة" to listOf("مدينة الكويت", "الدسمة", "الشرق", "الصالحية", "كيفان", "الروضة", "العديلية", "السرة"),
                "حولي" to listOf("حولي", "السالمية", "الجابرية", "الرميثية", "بيان", "مشرف", "الشعب"),
                "الفروانية" to listOf("الفروانية", "خيطان", "الأندلس", "إشبيلية", "جليب الشيوخ", "الرقعي", "صباح الناصر"),
                "الأحمدي" to listOf("الأحمدي", "الفحيحيل", "المنقف", "الصباحية", "الرقة", "المهبولة", "صباح الأحمد"),
                "الجهراء" to listOf("الجهراء", "الواحة", "العيون", "القصر", "النسيم", "سعد العبدالله"),
                "مبارك الكبير" to listOf("العدان", "القصور", "القرين", "صباح السالم", "المسيلة")
            )
        ),
        Country(
            name = "الأردن",
            flag = "🇯🇴",
            governorates = mapOf(
                "عمان" to listOf("وسط البلد", "عبدون", "الشميساني", "تلاع العلي", "صويلح", "الجبيهة", "طبربور", "مرج الحمام"),
                "إربد" to listOf("قصبة إربد", "الرمثا", "بني كنانة", "الكورة", "الأغوار الشمالية"),
                "الزرقاء" to listOf("قصبة الزرقاء", "الرصيفة", "الهاشمية"),
                "البلقاء" to listOf("السلط", "عين الباشا", "دير علا", "الشونة الجنوبية"),
                "العقبة" to listOf("العقبة", "القويرة", "وادي رم")
            )
        ),
        Country(
            name = "العراق",
            flag = "🇮🇶",
            governorates = mapOf(
                "بغداد" to listOf("الكرخ", "الرصافة", "المنصور", "الكرادة", "الأعظمية", "الكاظمية", "الدورة", "الشعب", "مدينة الصدر"),
                "البصرة" to listOf("البصرة القديمة", "العشار", "الزبير", "القرنة", "شط العرب", "الفاو"),
                "أربيل" to listOf("أربيل", "عينكاوة", "سوران", "شقلاوة", "كوي سنجق"),
                "النجف" to listOf("النجف القديمة", "الكوفة", "المناذرة", "الحيرة"),
                "كربلاء" to listOf("كربلاء القديمة", "الهندية", "عين التمر", "الحر")
            )
        ),
        Country(
            name = "سلطنة عمان",
            flag = "🇴🇲",
            governorates = mapOf(
                "مسقط" to listOf("السيب", "بوشر", "مطرح", "العامرات", "قريات"),
                "ظفار" to listOf("صلالة", "طاقة", "مرباط", "ثمريت"),
                "شمال الباطنة" to listOf("صحار", "السويق", "صحم", "لوى", "شناص"),
                "الداخلية" to listOf("نزوى", "بهلاء", "سمائل", "إزكي")
            )
        ),
        Country(
            name = "قطر",
            flag = "🇶🇦",
            governorates = mapOf(
                "الدوحة" to listOf("الدفنة", "السد", "الوعب", "الهلال", "النجمة"),
                "الريان" to listOf("الريان الجديد", "معيذر", "الغرافة", "عين خالد"),
                "الوكرة" to listOf("الوكرة", "المشاف", "الوكير"),
                "الخور" to listOf("الخور", "الذخيرة")
            )
        ),
        Country(
            name = "البحرين",
            flag = "🇧🇭",
            governorates = mapOf(
                "العاصمة" to listOf("المنامة", "الجفير", "أم الحصم", "السلمانية"),
                "المحرق" to listOf("المحرق", "البسيتين", "عراد", "الحد"),
                "الشمالية" to listOf("سار", "البديع", "الجنبية", "مدينة حمد"),
                "الجنوبية" to listOf("الرفاع", "مدينة عيسى", "الزلاق")
            )
        ),
        Country(
            name = "سوريا",
            flag = "🇸🇾",
            governorates = mapOf(
                "دمشق" to listOf("المزة", "المالكي", "أبو رمانة", "الميدان", "ركن الدين", "القنوات"),
                "ريف دمشق" to listOf("دوما", "داريا", "قدسيا", "جرمانا", "السيدة زينب"),
                "حلب" to listOf("الفرقان", "الشهباء", "سيف الدولة", "الأعظمية", "صلاح الدين"),
                "حمص" to listOf("الوعر", "الإنشاءات", "كرم الشامي", "الحمراء"),
                "اللاذقية" to listOf("اللاذقية المدينة", "جبلة", "القرداحة", "الحفة")
            )
        ),
        Country(
            name = "لبنان",
            flag = "🇱🇧",
            governorates = mapOf(
                "بيروت" to listOf("الحمرا", "الأشرفية", "فردان", "الروشة", "مار مخايل"),
                "جبل لبنان" to listOf("جونية", "كسروان", "بعبدا", "المتن", "الشوف"),
                "الشمال" to listOf("طرابلس", "البترون", "زغرتا", "الكورة"),
                "الجنوب" to listOf("صيدا", "صور", "جزين", "النبطية")
            )
        ),
        Country(
            name = "فلسطين",
            flag = "🇵🇸",
            governorates = mapOf(
                "القدس" to listOf("البلدة القديمة", "شعفاط", "بيت حنينا", "سلوان", "العيسوية"),
                "رام الله والبيرة" to listOf("رام الله", "البيرة", "بيتونيا", "بيرزيت"),
                "الخليل" to listOf("الخليل", "حلحول", "دورا", "يطا"),
                "نابلس" to listOf("نابلس", "رفيديا", "بلاطة", "حوارة"),
                "غزة" to listOf("غزة المدينة", "الرمال", "الشجاعية", "خان يونس", "رفح")
            )
        ),
        Country(
            name = "اليمن",
            flag = "🇾🇪",
            governorates = mapOf(
                "أمانة العاصمة / صنعاء" to listOf("السبعين", "التحرير", "شعوب", "معين", "الوحدة"),
                "عدن" to listOf("كريتر", "المعلا", "التواهي", "الشيخ عثمان", "المنصورة"),
                "تعز" to listOf("القاهرة", "المظفر", "صالة", "صبر"),
                "حضرموت" to listOf("المكلا", "سيئون", "الشحر", "تريم"),
                "الحديدة" to listOf("الحوك", "الميناء", "الحالي")
            )
        ),
        Country(
            name = "ليبيا",
            flag = "🇱🇾",
            governorates = mapOf(
                "طرابلس" to listOf("سوق الجمعة", "حي الأندلس", "أبو سليم", "تاجوراء", "عين زارة"),
                "بنغازي" to listOf("البركة", "الصابري", "الهواري", "الكيش", "الليثي"),
                "مصراتة" to listOf("مصراتة المدينة", "الزروق", "طريق النهر"),
                "الزاوية" to listOf("الزاوية المركز", "أبو عيسى")
            )
        ),
        Country(
            name = "السودان",
            flag = "🇸🇩",
            governorates = mapOf(
                "الخرطوم" to listOf("الخرطوم", "أم درمان", "بحري", "شرق النيل", "جبل أولياء"),
                "الجزيرة" to listOf("ود مدني", "الحصاحيصا", "المناقل", "رفاعة"),
                "البحر الأحمر" to listOf("بورتسودان", "سواكن"),
                "الشمالية" to listOf("دنقلا", "مروي")
            )
        ),
        Country(
            name = "تونس",
            flag = "🇹🇳",
            governorates = mapOf(
                "تونس العاصمة" to listOf("المرسى", "سيدي بوسعيد", "قرطاج", "المنازه", "باردو"),
                "صفاقس" to listOf("صفاقس المدينة", "ساقية الزيت", "ساقية الدائر"),
                "سوسة" to listOf("سوسة المدينة", "حمام سوسة", "القلعة الكبرى"),
                "أريانة" to listOf("أريانة المدينة", "سكرة", "رواد")
            )
        ),
        Country(
            name = "الجزائر",
            flag = "🇩🇿",
            governorates = mapOf(
                "الجزائر العاصمة" to listOf("باب الوادي", "حيدرة", "سيدي يحيى", "بن عكنون", "القبة"),
                "وهران" to listOf("وهران المركز", "السانية", "عين الترك", "بئر الجير"),
                "قسنطينة" to listOf("قسنطينة المركز", "الخروب", "علي منجلي"),
                "سطيف" to listOf("سطيف", "العلمة", "عين ولمان")
            )
        ),
        Country(
            name = "المغرب",
            flag = "🇲🇦",
            governorates = mapOf(
                "الدار البيضاء" to listOf("أنفا", "المعاريف", "عين الدياب", "سيدي معروف", "سيدي عثمان"),
                "الرباط" to listOf("حسان", "أكدال", "السويسي", "يعقوب المنصور"),
                "مراكش" to listOf("المدينة العتيقة", "كليز", "المحاميد", "النخيل"),
                "طنجة" to listOf("طنجة المدينة", "بني مكادة", "مالاباطا")
            )
        ),
        Country(
            name = "موريتانيا",
            flag = "🇲🇷",
            governorates = mapOf(
                "نواكشوط الغربية" to listOf("تفرغ زينة", "السبخة", "لكصر"),
                "نواكشوط الشمالية" to listOf("دار النعيم", "تيارت", "توجنين"),
                "نواكشوط الجنوبية" to listOf("عرفات", "الميناء", "الرياض"),
                "داخلة نواذيبو" to listOf("نواذيبو المركز", "بولنوار")
            )
        ),
        Country(
            name = "الصومال",
            flag = "🇸🇴",
            governorates = mapOf(
                "بنادر / مقديشو" to listOf("حمروين", "هولوداغ", "ودجر", "يارشيد"),
                "أودل" to listOf("بورمة", "زيلع"),
                "باري" to listOf("بوصاصو", "قرضو")
            )
        ),
        Country(
            name = "جيبوتي",
            flag = "🇩🇯",
            governorates = mapOf(
                "جيبوتي العاصمة" to listOf("جيبوتي 1", "بلبلا", "بيلوس"),
                "علي صبيح" to listOf("علي صبيح المركز"),
                "تاجورة" to listOf("تاجورة المركز")
            )
        ),
        Country(
            name = "جزر القمر",
            flag = "🇰🇲",
            governorates = mapOf(
                "القمر الكبرى" to listOf("موروني", "فمبوني", "مبيني"),
                "أنجوان" to listOf("موتسامودو", "دوموني"),
                "موهيلي" to listOf("فومبوني")
            )
        ),
        Country(
            name = "تركيا",
            flag = "🇹🇷",
            governorates = mapOf(
                "إسطنبول" to listOf("الفاتح", "باشاك شهير", "شيشلي", "بيليك دوزو", "كاديكوي", "أسكودار"),
                "أنقرة" to listOf("تشانكايا", "كيزيلاي", "يني محلة", "ألتنداغ"),
                "بورصة" to listOf("عثمان غازي", "نيلوفر", "يلدريم"),
                "إزمير" to listOf("كوناك", "بورنوفا", "كارشيياكا"),
                "غازي عنتاب" to listOf("شاهين باي", "شهيد كامل")
            )
        )
    )

    fun findCountry(name: String): Country? {
        return countries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

/**
 * Three-stage dropdown selector component for location:
 * Country -> Governorate/Province -> District/City
 */
@Composable
fun HierarchicalLocationSelector(
    selectedCountry: String,
    onCountryChange: (String) -> Unit,
    selectedGovernorate: String,
    onGovernorateChange: (String) -> Unit,
    selectedDistrict: String,
    onDistrictChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var countryExpanded by remember { mutableStateOf(false) }
    var govExpanded by remember { mutableStateOf(false) }
    var districtExpanded by remember { mutableStateOf(false) }

    val currentCountryObj = LocationData.findCountry(selectedCountry) ?: LocationData.countries.first()
    val availableGovernorates = currentCountryObj.governorates.keys.toList()
    val availableDistricts = currentCountryObj.governorates[selectedGovernorate] ?: emptyList()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "الموقع الجغرافي (الدولة • المحافظة • الحي/المركز):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Stage 1: Country Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (selectedCountry.isNotBlank()) "${currentCountryObj.flag} $selectedCountry" else "اختر الدولة",
                onValueChange = {},
                readOnly = true,
                label = { Text("1. الدولة") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "قائمة الدول",
                        modifier = Modifier.clickable { countryExpanded = true }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { countryExpanded = true },
                shape = RoundedCornerShape(10.dp)
            )

            DropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                LocationData.countries.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Text("${c.flag} ${c.name}", fontWeight = if (c.name == selectedCountry) FontWeight.Bold else FontWeight.Normal)
                        },
                        onClick = {
                            onCountryChange(c.name)
                            // Auto select first governorate & district
                            val firstGov = c.governorates.keys.firstOrNull() ?: ""
                            onGovernorateChange(firstGov)
                            val firstDist = c.governorates[firstGov]?.firstOrNull() ?: ""
                            onDistrictChange(firstDist)
                            countryExpanded = false
                        }
                    )
                }
            }
        }

        // Stage 2 & 3: Governorate and District Dropdowns in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stage 2: Governorate Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedGovernorate.ifBlank { "اختر المحافظة" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("2. المحافظة/المنطقة") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "قائمة المحافظات",
                            modifier = Modifier.clickable { govExpanded = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { govExpanded = true },
                    shape = RoundedCornerShape(10.dp)
                )

                DropdownMenu(
                    expanded = govExpanded,
                    onDismissRequest = { govExpanded = false }
                ) {
                    availableGovernorates.forEach { gov ->
                        DropdownMenuItem(
                            text = {
                                Text(gov, fontWeight = if (gov == selectedGovernorate) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = {
                                onGovernorateChange(gov)
                                val firstDist = currentCountryObj.governorates[gov]?.firstOrNull() ?: ""
                                onDistrictChange(firstDist)
                                govExpanded = false
                            }
                        )
                    }
                }
            }

            // Stage 3: District / City Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedDistrict.ifBlank { "اختر الحي/المركز" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("3. الحي/المركز/المقاطعة") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "قائمة الأحياء",
                            modifier = Modifier.clickable { districtExpanded = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { districtExpanded = true },
                    shape = RoundedCornerShape(10.dp)
                )

                DropdownMenu(
                    expanded = districtExpanded,
                    onDismissRequest = { districtExpanded = false }
                ) {
                    if (availableDistricts.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("لا توجد أحياء مسجلة (اكتب يدوياً)") },
                            onClick = { districtExpanded = false }
                        )
                    } else {
                        availableDistricts.forEach { dist ->
                            DropdownMenuItem(
                                text = {
                                    Text(dist, fontWeight = if (dist == selectedDistrict) FontWeight.Bold else FontWeight.Normal)
                                },
                                onClick = {
                                    onDistrictChange(dist)
                                    districtExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
