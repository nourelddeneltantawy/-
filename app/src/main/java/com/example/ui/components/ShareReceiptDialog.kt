package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
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

    var includeDate by remember { mutableStateOf(true) }
    var includePhone by remember { mutableStateOf(true) }
    var includeLocation by remember { mutableStateOf(true) }
    var includeDeviceType by remember { mutableStateOf(true) }
    var includeFault by remember { mutableStateOf(true) }
    var includeCost by remember { mutableStateOf(true) }
    var includeReplacedParts by remember { mutableStateOf(true) }
    var includeOtherDetails by remember { mutableStateOf(true) }
    var includeStatus by remember { mutableStateOf(false) }
    var includeImage by remember { mutableStateOf(jobs.any { it.imageUrl.isNotBlank() }) }
    var includeCustomFields by remember { mutableStateOf(jobs.any { it.customFields.isNotEmpty() }) }

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
                            text = "اختر البيانات التي تريد إرسالها للعميل",
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
                            FieldCheckItem("مكان العميل", includeLocation, modifier = Modifier.weight(1f)) { includeLocation = it }
                            FieldCheckItem("نوع الجهاز والكارتة", includeDeviceType, modifier = Modifier.weight(1f)) { includeDeviceType = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("توصيف العطل", includeFault, modifier = Modifier.weight(1f)) { includeFault = it }
                            FieldCheckItem("قطع الغيار المبدلة", includeReplacedParts, modifier = Modifier.weight(1f)) { includeReplacedParts = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("الحساب والتكلفة (ج.م)", includeCost, modifier = Modifier.weight(1f)) { includeCost = it }
                            FieldCheckItem("حالة الكارتة", includeStatus, modifier = Modifier.weight(1f)) { includeStatus = it }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FieldCheckItem("ملاحظات أخرى", includeOtherDetails, modifier = Modifier.weight(1f)) { includeOtherDetails = it }
                            if (jobs.any { it.imageUrl.isNotBlank() }) {
                                FieldCheckItem("صور الكروت 📷", includeImage, modifier = Modifier.weight(1f)) { includeImage = it }
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
