package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
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
    job: TechnicianJobRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var includeDate by remember { mutableStateOf(true) }
    var includePhone by remember { mutableStateOf(true) }
    var includeDeviceType by remember { mutableStateOf(true) }
    var includeFault by remember { mutableStateOf(true) }
    var includeCost by remember { mutableStateOf(true) }
    var includeReplacedParts by remember { mutableStateOf(true) }
    var includeLocation by remember { mutableStateOf(true) }
    var includeOtherDetails by remember { mutableStateOf(true) }
    var includeStatus by remember { mutableStateOf(false) }
    var includeImage by remember { mutableStateOf(job.imageUrl.isNotBlank()) }
    var includeCustomFields by remember { mutableStateOf(job.customFields.isNotEmpty()) }
    var includeStamp by remember { mutableStateOf(true) }

    fun buildConfig(): ReceiptCustomization {
        return ReceiptCustomization(
            includeDate = includeDate,
            includePhone = includePhone,
            includeDeviceType = includeDeviceType,
            includeFault = includeFault,
            includeCost = includeCost,
            includeReplacedParts = includeReplacedParts,
            includeLocation = includeLocation,
            includeOtherDetails = includeOtherDetails,
            includeStatus = includeStatus,
            includeImage = includeImage,
            includeCustomFields = includeCustomFields,
            includeStamp = includeStamp
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
                            text = "تجهيز ومشاركة إيصال العميل 📸",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "حدد الخانات التي تريد إرسالها للعميل",
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
                    text = "اختر الخانات التي تظهر في الصورة المنظمة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("التاريخ واليوم", includeDate, modifier = Modifier.weight(1f)) { includeDate = it }
                            FieldCheckItem("رقم هاتف العميل", includePhone, modifier = Modifier.weight(1f)) { includePhone = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("نوع الجهاز والكارتة", includeDeviceType, modifier = Modifier.weight(1f)) { includeDeviceType = it }
                            FieldCheckItem("توصيف العطل", includeFault, modifier = Modifier.weight(1f)) { includeFault = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("الحساب والسعر (ج.م)", includeCost, modifier = Modifier.weight(1f)) { includeCost = it }
                            FieldCheckItem("قطع الغيار المبدلة", includeReplacedParts, modifier = Modifier.weight(1f)) { includeReplacedParts = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("مكان وعنوان العميل", includeLocation, modifier = Modifier.weight(1f)) { includeLocation = it }
                            FieldCheckItem("حالة الشغل الحالية", includeStatus, modifier = Modifier.weight(1f)) { includeStatus = it }
                        }
                        if (job.otherDetails.isNotBlank()) {
                            FieldCheckItem("تفاصيل وملاحظات إضافية", includeOtherDetails) { includeOtherDetails = it }
                        }
                        if (job.customFields.isNotEmpty()) {
                            FieldCheckItem("الخانات المخصصة (${job.customFields.size} خانات)", includeCustomFields) { includeCustomFields = it }
                        }
                        if (job.imageUrl.isNotBlank()) {
                            FieldCheckItem("📷 صورة الكارتة أثناء الصيانة", includeImage) { includeImage = it }
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        FieldCheckItem(
                            label = "ختم وشعار التطبيق المعتمد في الأسفل 🎖️",
                            checked = includeStamp,
                            isHighlighted = true
                        ) { includeStamp = it }
                    }
                }

                // Section 2: Live preview card
                Text(
                    text = "معاينة سريعة لشكل الإيصال النهائي:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header banner
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
                                    text = "تقرير وإيصال صيانة معتمد",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            }
                            if (includeDate) {
                                Text(
                                    text = "${job.dayOfWeek} ${job.date}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                        // Selected Items Preview
                        if (includePhone && job.clientPhone.isNotBlank()) {
                            PreviewRow("رقم الهاتف:", job.clientPhone)
                        }
                        if (includeLocation && job.clientLocation.isNotBlank()) {
                            PreviewRow("المكان:", job.clientLocation)
                        }
                        if (includeDeviceType && job.deviceType.isNotBlank()) {
                            PreviewRow("الجهاز:", job.deviceType)
                        }
                        if (includeFault && job.faultDescription.isNotBlank()) {
                            PreviewRow("العطل:", job.faultDescription, valueColor = Color(0xFFF87171))
                        }
                        if (includeReplacedParts && job.replacedParts.isNotBlank()) {
                            PreviewRow("القطع المبدلة:", job.replacedParts)
                        }
                        if (includeCost && job.cost > 0) {
                            PreviewRow("الحساب:", "${String.format("%.0f", job.cost)} ج.م", valueColor = Color(0xFF34D399), isBold = true)
                        }
                        if (includeStatus && job.status.isNotBlank()) {
                            PreviewRow("الحالة:", job.status, valueColor = Color(0xFFFBBF24))
                        }
                        if (includeOtherDetails && job.otherDetails.isNotBlank()) {
                            PreviewRow("ملاحظات:", job.otherDetails)
                        }
                        if (includeCustomFields && job.customFields.isNotEmpty()) {
                            job.customFields.forEach { (k, v) ->
                                PreviewRow("$k:", v)
                            }
                        }
                        if (includeImage && job.imageUrl.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مرفق صورة الكارتة في الإيصال", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        if (includeStamp) {
                            Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("معتمد • ختم وضمان البراء إلكترونيك", fontSize = 10.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                }
                                Text("جودة وضمان", fontSize = 9.sp, color = Color(0xFF94A3B8))
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
                        JobReceiptShareHelper.shareToWhatsApp(context, job, config, job.clientPhone)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال لواتساب العميل مباشرة (${job.clientPhone.ifBlank { "واتساب" }})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Generic Share Button
                    OutlinedButton(
                        onClick = {
                            val config = buildConfig()
                            JobReceiptShareHelper.shareGeneric(context, job, config)
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
                            JobReceiptShareHelper.saveToGallery(context, job, config)
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
    isHighlighted: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = if (isHighlighted) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 2
        )
    }
}
