package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import coil.compose.AsyncImage
import com.example.util.ImageHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TechnicianJobRecord
import com.example.util.JobReceiptShareHelper
import com.example.util.ReceiptCustomization

@Composable
fun ShareReceiptDialog(
    jobs: List<TechnicianJobRecord>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val firstJob = jobs.firstOrNull() ?: return

    val prefs = remember { context.getSharedPreferences("receipt_share_prefs", Context.MODE_PRIVATE) }

    var includeDate by remember { mutableStateOf(prefs.getBoolean("includeDate", true)) }
    var includePhone by remember { mutableStateOf(prefs.getBoolean("includePhone", true)) }
    var includeLocation by remember { mutableStateOf(prefs.getBoolean("includeLocation", true)) }
    var includeDeviceType by remember { mutableStateOf(prefs.getBoolean("includeDeviceType", true)) }
    var includeFault by remember { mutableStateOf(prefs.getBoolean("includeFault", true)) }
    var includeCost by remember { mutableStateOf(prefs.getBoolean("includeCost", true)) }
    var includeReplacedParts by remember { mutableStateOf(prefs.getBoolean("includeReplacedParts", false)) }
    var includeOtherDetails by remember { mutableStateOf(prefs.getBoolean("includeOtherDetails", false)) }
    var includeStatus by remember { mutableStateOf(prefs.getBoolean("includeStatus", false)) }
    var includeImage by remember { mutableStateOf(prefs.getBoolean("includeImage", true)) }
    var includeCustomFields by remember { mutableStateOf(prefs.getBoolean("includeCustomFields", false)) }

    fun updatePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun buildConfig(): ReceiptCustomization {
        return ReceiptCustomization(
            includeDate = includeDate,
            includePhone = includePhone,
            includeLocation = includeLocation,
            includeDeviceType = includeDeviceType,
            includeFault = includeFault,
            includeCost = includeCost,
            includeReplacedParts = includeReplacedParts,
            includeOtherDetails = includeOtherDetails,
            includeStatus = includeStatus,
            includeImage = includeImage,
            includeCustomFields = includeCustomFields
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (jobs.size > 1) "إيصال صيانة مجمع (${jobs.size} كروت) 📸" else "مشاركة إيصال الصيانة 📸",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "يتم حفظ اختياراتك تلقائياً للاستخدام القادم 💾",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Fields checklist
                Text(
                    text = "حدد الخانات التي تظهر بالإيصال:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Prominent Photo Inclusion Selector (طلب المستخدم: اقدر اختار الصوره لما باجي اشارك الكروت اضيفها ولا لا)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (includeImage) Color(0xFF0284C7).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (includeImage) Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextVal = !includeImage
                            includeImage = nextVal
                            updatePref("includeImage", nextVal)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (includeImage) Color(0xFF0284C7).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = if (includeImage) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("إرفاق صورة الكارتة في الإيصال 📸", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    text = if (includeImage) "مفعّل: ستظهر صورة الكارتة بالوصل" else "معطّل: لن تظهر الصورة بالإيصال",
                                    fontSize = 10.sp,
                                    color = if (includeImage) Color(0xFF0284C7) else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Checkbox(
                            checked = includeImage,
                            onCheckedChange = {
                                includeImage = it
                                updatePref("includeImage", it)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7)),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("التاريخ واليوم", includeDate, modifier = Modifier.weight(1f)) {
                                includeDate = it
                                updatePref("includeDate", it)
                            }
                            FieldCheckItem("رقم هاتف العميل", includePhone, modifier = Modifier.weight(1f)) {
                                includePhone = it
                                updatePref("includePhone", it)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("مكان العميل", includeLocation, modifier = Modifier.weight(1f)) {
                                includeLocation = it
                                updatePref("includeLocation", it)
                            }
                            FieldCheckItem("نوع الجهاز والكارتة", includeDeviceType, modifier = Modifier.weight(1f)) {
                                includeDeviceType = it
                                updatePref("includeDeviceType", it)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("توصيف العطل", includeFault, modifier = Modifier.weight(1f)) {
                                includeFault = it
                                updatePref("includeFault", it)
                            }
                            FieldCheckItem("قطع الغيار المبدلة", includeReplacedParts, modifier = Modifier.weight(1f)) {
                                includeReplacedParts = it
                                updatePref("includeReplacedParts", it)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("الحساب والتكلفة (ج.م)", includeCost, modifier = Modifier.weight(1f)) {
                                includeCost = it
                                updatePref("includeCost", it)
                            }
                            FieldCheckItem("حالة الكارتة", includeStatus, modifier = Modifier.weight(1f)) {
                                includeStatus = it
                                updatePref("includeStatus", it)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("ملاحظات أخرى", includeOtherDetails, modifier = Modifier.weight(1f)) {
                                includeOtherDetails = it
                                updatePref("includeOtherDetails", it)
                            }
                            FieldCheckItem("صورة الكارتة 📷", includeImage, modifier = Modifier.weight(1f)) {
                                includeImage = it
                                updatePref("includeImage", it)
                            }
                        }
                    }
                }

                // Section 2: Clean Preview (No bottom cluttered stamp, just logo + details + total)
                Text(
                    text = "معاينة الإيصال المنظم:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0E1626),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header banner (Logo + Title)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "م. البراء إلكترونيك",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (jobs.size > 1) "إيصال صيانة مجمع (${jobs.size} كروت)" else "إيصال صيانة",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (includeDate) {
                                Text(
                                    text = "${firstJob.dayOfWeek} ${firstJob.date}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E2D4A), modifier = Modifier.padding(vertical = 4.dp))

                        // Customer info
                        if (includePhone && firstJob.clientPhone.isNotBlank()) {
                            PreviewRow("رقم الهاتف:", firstJob.clientPhone)
                        }
                        if (includeLocation && firstJob.clientLocation.isNotBlank()) {
                            PreviewRow("المكان:", firstJob.clientLocation)
                        }

                        // Cards summary
                        jobs.forEachIndexed { idx, card ->
                            HorizontalDivider(color = Color(0xFF1E2D4A), modifier = Modifier.padding(vertical = 3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (jobs.size > 1) "كارتة ${idx + 1}: ${card.deviceType}" else card.deviceType,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                if (includeCost && card.cost > 0) {
                                    Text(
                                        text = "${String.format("%.0f", card.cost)} ج.م",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (includeFault && card.faultDescription.isNotBlank()) {
                                PreviewRow("العطل:", card.faultDescription, valueColor = Color(0xFFF87171))
                            }
                            if (includeReplacedParts && card.replacedParts.isNotBlank()) {
                                PreviewRow("القطع:", card.replacedParts)
                            }
                            if (includeImage && card.imageUrl.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    ) {
                                        AsyncImage(
                                            model = ImageHelper.getCoilData(card.imageUrl),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("مرفق صورة الكارتة بالإيصال ✓", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (includeCost) {
                            val total = jobs.sumOf { it.cost }
                            HorizontalDivider(color = Color(0xFF10B981).copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (jobs.size > 1) "إجمالي الحساب (${jobs.size} كروت):" else "إجمالي الحساب:",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${String.format("%.0f", total)} ج.م",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Primary WhatsApp Button
                Button(
                    onClick = {
                        val config = buildConfig()
                        JobReceiptShareHelper.shareToWhatsApp(context, jobs, config, firstJob.clientPhone)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إرسال لواتساب العميل (${firstJob.clientPhone.ifBlank { "واتساب" }})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Generic Share Button
                    OutlinedButton(
                        onClick = {
                            val config = buildConfig()
                            JobReceiptShareHelper.shareGeneric(context, jobs, config)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة 📤", fontSize = 11.sp)
                    }

                    // Save Image Button
                    OutlinedButton(
                        onClick = {
                            val config = buildConfig()
                            JobReceiptShareHelper.saveToGallery(context, jobs, config)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ بالمعرض 💾", fontSize = 11.sp)
                    }
                }
            }
        },
        dismissButton = null
    )
}

@Composable
private fun FieldCheckItem(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
