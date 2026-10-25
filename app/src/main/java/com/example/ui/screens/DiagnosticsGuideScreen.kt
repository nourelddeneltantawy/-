package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DiagnosticItem(
    val title: String,
    val brand: String,
    val description: String,
    val cause: String,
    val solution: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsGuideScreen(
    categoryTitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = when (categoryTitle) {
        "أعطال الأوتوماتيك" -> listOf(
            DiagnosticItem("رمز العطل (dE / Door)", "إل جي / سامسونج", "الباب غير مغلق أو اللوك لا يوصل إشارة C للكارتة", "تلف في سخان PTC الخاص باللوك أو عدم وصول تكة اللسان", "قياس المقاومة بين أطراف اللوك 1 و 3 (~1000 أوم) أو فحص مفتاح الباب"),
            DiagnosticItem("رمز العطل (OE / E2)", "إل جي / دايو / زانوسي", "عدم تصريف المياه في الوقت المحدد (خطأ في الطرد)", "انسداد فلتر الطلمبة، تلف ملف الطلمبة، أو عطل ترياك الطرد بالكارتة", "فحص فلتر الطلمبة وقياس ملف طلمبة التصريف (140 - 180 أوم)"),
            DiagnosticItem("رمز العطل (IE / 4E)", "إل جي / سامسونج", "عدم دخول المياه أو بطء ملء الحلة", "انسداد فلاتر صمام الدخول أو تلف ملف الصمام الكهربائي", "تنظيف مصافي الصمام وقياس مقاومة ملف الصمام (~3.5K-4K أوم)"),
            DiagnosticItem("رمز العطل (tE)", "إل جي", "عطل في حساس درجة الحرارة (الثرمستات NTC)", "قطع في أسلاك الثرمستات أو تلف مقاومة الحساس NTC", "قياس مقاومة الثرمستات عند 25°C تكون قرابة 40K إلى 50K أوم"),
            DiagnosticItem("رمز العطل (LE / 3E)", "إل جي / سامسونج", "عطل في دوران الماتور أو حساس التاكو / الهال سينسور", "فصل في فيشة الماتور أو تلف حساس الهال في مواتير الدايركت درايف", "فحص توصيلات سوكت الماتور وفحص بوردة الهال سينسور على الماتور")
        )
        "الفوق أوتوماتيك" -> listOf(
            DiagnosticItem("رمز العطل (E1 / 4C)", "توشيبا / شارب / يونيون إير", "مشكلة في سحب المياه للغسالة الفوق أوتوماتيك", "ضعف ضغط المياه، انسداد مصفاة الصمام، أو تلف الصمام المزدوج", "فحص صمامات المياه البارد والساخن وتنظيف الفلاتر"),
            DiagnosticItem("رمز العطل (E2 / dC)", "توشيبا / يونيون إير", "فتح الغطاء أثناء العصر أو تلف سويتش الأمان والاهتزاز", "مفتاح الباب الميكانيكي أو سويتش حماية الحلة عند الاهتزاز", "صنفرة نقاط تلامس سويتش الباب ومراجعة اتزان الحلة والمساعدين"),
            DiagnosticItem("رمز العطل (E3 / Ub)", "توشيبا / سامسونج", "عدم اتزان الملابس داخل الحلة أثناء العصر", "تكتل الغسيل في جانب واحد أو تلف مساعدين الغسالة الأربعة", "توزيع الغسيل بالتساوي والتأكد من سلامة سوست ومساعدين التعليق")
        )
        "تيست مود" -> listOf(
            DiagnosticItem("تيست مود غسالات إل جي (LG Smart Test)", "إل جي (LG)", "طريقة الدخول للوضع الاختباري", "الضغط معاً على زري (العصر Spin + الحرارة Temp) ثم الضغط على مفتاح الباور Power", "مع كل ضغطة على زر Start يتم فحص جزء: الصمامات، ثم الماتور يمين وشمال، ثم السرعة العالية 1000rpm، ثم الطرد"),
            DiagnosticItem("تيست مود غسالات سامسونج (Samsung Test)", "سامسونج (Samsung)", "اختبار المكونات الشامل", "الضغط على زري (Spin + Soil Level) ثم تشغيل الباور Power", "الدخول لوضع الفحص واختبار دورة عصر فارغة واختبار طلمبة الطرد وصمامات السحب"),
            DiagnosticItem("تيست مود غسالات زانوسي وإلكترولوكس", "زانوسي / إلكترولوكس", "فحص كارتات EWM", "إدارة المفتاح للوضع 1 ثم الضغط على الزرين الأولين بجوار الشاشة لمدة 5 ثوانٍ", "قراءة كود آخر عطل مسجل في ذاكرة الكارتة ومسح الأعطال القديمة")
        )
        else -> listOf(
            DiagnosticItem("طقم بلي غسالة إل جي 7 و 8 كيلو", "إل جي (LG)", "مقاسات رولمان البلي والأولسيه", "البلي الداخلي والخارجي وحلقات الحماية", "بلي داخلي 6206z | بلي خارجي 6205z | أولسيه 37×66×9.5/12"),
            DiagnosticItem("طقم بلي غسالة سامسونج إيكوبابل", "سامسونج (Samsung)", "مقاسات رولمان البلي والأولسيه", "الأبعاد الدقيقة للحلة الإستانلس", "بلي داخلي 6205z | بلي خارجي 6204z | أولسيه 35×65.55×10/12"),
            DiagnosticItem("طقم بلي غسالة زانوسي العبد", "زانوسي (Zanussi)", "المقاسات القياسية", "الحلة الأمامية والخلفية", "بلي داخلي 6204z | بلي خارجي 6203z | أولسيه 25×47×10")
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = item.brand,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "• السبب المحتمل: ${item.cause}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• طريقة الإصلاح والفحص: ${item.solution}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
