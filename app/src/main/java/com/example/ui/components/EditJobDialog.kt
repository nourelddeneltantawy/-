package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TechnicianJobRecord

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditJobDialog(
    initialJob: TechnicianJobRecord?,
    currentUserEmail: String,
    onDismiss: () -> Unit,
    onSave: (TechnicianJobRecord) -> Unit
) {
    val context = LocalContext.current

    var date by remember { mutableStateOf(initialJob?.date ?: TechnicianJobRecord.getCurrentDateFormatted()) }
    var dayOfWeek by remember { mutableStateOf(initialJob?.dayOfWeek ?: TechnicianJobRecord.getDayOfWeekArabic(date)) }
    var clientPhone by remember { mutableStateOf(initialJob?.clientPhone ?: "") }
    var deviceType by remember { mutableStateOf(initialJob?.deviceType ?: "") }
    var faultDescription by remember { mutableStateOf(initialJob?.faultDescription ?: "") }
    var costStr by remember { mutableStateOf(if (initialJob != null && initialJob.cost > 0) String.format("%.0f", initialJob.cost) else "") }
    var replacedParts by remember { mutableStateOf(initialJob?.replacedParts ?: "") }
    var clientLocation by remember { mutableStateOf(initialJob?.clientLocation ?: "") }
    var otherDetails by remember { mutableStateOf(initialJob?.otherDetails ?: "") }
    var imageUrl by remember { mutableStateOf(initialJob?.imageUrl ?: "") }
    var status by remember { mutableStateOf(initialJob?.status ?: "قيد الفحص") }

    val customFieldsMap = remember {
        mutableStateMapOf<String, String>().apply {
            initialJob?.customFields?.forEach { (k, v) -> put(k, v) }
        }
    }

    var showAddCustomFieldDialog by remember { mutableStateOf(false) }
    var newFieldName by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                imageUrl = uri.toString()
            }
        }
    )

    val deviceSuggestions = listOf(
        "غسالة سامسونج", "ثلاجة LG", "غسالة فكتوريا", "فريزر كريازي",
        "ميكرويف", "غسالة بيكو", "غسالة توشيبا", "ثلاجة يونيون اير", "تكييف شارب"
    )

    val faultSuggestions = listOf(
        "باور", "طرمبة", "رطوبة وتمليح", "تفحم مسارات", "عطل EH0",
        "ماتور سريع", "لا يعمل الماتور", "بروسيسور", "لوحة أزرار"
    )

    val partsSuggestions = listOf(
        "ترياك acs1086", "لحام ومسارات", "مكثف 220μF/25V", "مقاومة 1kΩ",
        "طقم دايودات توحيد", "سوكيت / سولدر ماسك", "كارتة أصلية"
    )

    val locationSuggestions = listOf(
        "المرج", "شارع الجزيرة / المرج", "طوخ / القليوبية", "محمد نجيب",
        "البركة", "شارع الأنابيب", "السويس", "حلمية الزيتون", "عين شمس"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialJob == null) "تسجيل كارتة / شغل صيانة جديد ➕" else "تعديل بيانات شغل الكارتة ✏️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row: Date & Day
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {
                            date = it
                            dayOfWeek = TechnicianJobRecord.getDayOfWeekArabic(it)
                        },
                        label = { Text("التاريخ (YYYY/MM/DD)") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dayOfWeek,
                        onValueChange = { dayOfWeek = it },
                        label = { Text("اليوم") },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true
                    )
                }

                // Phone
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = { Text("رقم هاتف العميل (واتساب)") },
                    placeholder = { Text("01XXXXXXXXX") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Device Type
                Column {
                    OutlinedTextField(
                        value = deviceType,
                        onValueChange = { deviceType = it },
                        label = { Text("نوع الجهاز / الكارتة") },
                        placeholder = { Text("مثال: غسالة سامسونج، ثلاجة LG...") },
                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        deviceSuggestions.take(4).forEach { sug ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { deviceType = sug }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(sug, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                // Fault description
                Column {
                    OutlinedTextField(
                        value = faultDescription,
                        onValueChange = { faultDescription = it },
                        label = { Text("عطل الجهاز") },
                        placeholder = { Text("مثال: باور، طرمبة، تفحم، EH0...") },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        faultSuggestions.take(4).forEach { sug ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { faultDescription = sug }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(sug, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                // Cost / Account
                OutlinedTextField(
                    value = costStr,
                    onValueChange = { costStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("الحساب / التكلفة (ج.م)") },
                    placeholder = { Text("مثال: 350") },
                    leadingIcon = { Icon(Icons.Default.PriceCheck, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Replaced spare parts
                Column {
                    OutlinedTextField(
                        value = replacedParts,
                        onValueChange = { replacedParts = it },
                        label = { Text("قطع الغيار المبدلة") },
                        placeholder = { Text("مثال: ترياك acs1086، مكثف 220μF...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        partsSuggestions.take(4).forEach { sug ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        replacedParts = if (replacedParts.isBlank()) sug else "$replacedParts + $sug"
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(sug, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                // Location
                Column {
                    OutlinedTextField(
                        value = clientLocation,
                        onValueChange = { clientLocation = it },
                        label = { Text("مكان العميل / المحافظة أو الحي") },
                        placeholder = { Text("مثال: شارع الجزيرة / المرج") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        locationSuggestions.take(4).forEach { sug ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { clientLocation = sug }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(sug, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                // Status Chips
                Text("حالة الكارتة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusOptions = listOf("قيد الفحص", "جاهز للاستلام", "تم التسليم والتحصيل")
                    statusOptions.forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (opt.contains("تسليم")) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Other Details
                OutlinedTextField(
                    value = otherDetails,
                    onValueChange = { otherDetails = it },
                    label = { Text("تفاصيل أخرى / ملاحظات") },
                    placeholder = { Text("أي تفاصيل إضافية عن الشغل...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Board Image Picker
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("صورة الكارتة أثناء الصيانة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    text = if (imageUrl.isNotBlank()) "تم اختيار صورة للكارتة ✓" else "اختيار صورة من المعرض أو الكاميرا",
                                    fontSize = 10.sp,
                                    color = if (imageUrl.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (imageUrl.isNotBlank()) "تغيير 📷" else "اختيار 📷", fontSize = 11.sp)
                        }
                    }
                }

                // Dynamic Custom Fields: "ولدي القدرة في زيادة خانات أخرى لاحقاً"
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("خانات إضافية مخصصة ⚙️", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("يمكنك زيادة أي خانات جديدة بمزاجك", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    OutlinedButton(
                        onClick = { showAddCustomFieldDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة خانة", fontSize = 11.sp)
                    }
                }

                if (customFieldsMap.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customFieldsMap.entries.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(entry.key, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(entry.value, fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = { customFieldsMap.remove(entry.key) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deviceType.isBlank()) {
                        Toast.makeText(context, "يرجى تحديد نوع الجهاز أو الكارتة", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val costVal = costStr.toDoubleOrNull() ?: 0.0
                    val updated = (initialJob ?: TechnicianJobRecord()).copy(
                        userEmail = currentUserEmail,
                        date = date,
                        dayOfWeek = dayOfWeek,
                        clientPhone = clientPhone,
                        deviceType = deviceType,
                        faultDescription = faultDescription,
                        cost = costVal,
                        replacedParts = replacedParts,
                        clientLocation = clientLocation,
                        otherDetails = otherDetails,
                        imageUrl = imageUrl,
                        status = status,
                        customFields = customFieldsMap.toMap(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialJob == null) "حفظ وتسجيل الكارتة بنجاح ✅" else "حفظ التعديلات ✅", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = null
    )

    // Sub-dialog: Add Custom Field
    if (showAddCustomFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomFieldDialog = false },
            title = { Text("إضافة خانة مخصصة جديدة ➕", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFieldName,
                        onValueChange = { newFieldName = it },
                        label = { Text("اسم الخانة") },
                        placeholder = { Text("مثال: فترة الضمان، الرقم المسلسل...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFieldValue,
                        onValueChange = { newFieldValue = it },
                        label = { Text("قيمة الخانة") },
                        placeholder = { Text("مثال: شهرين، SN-9988...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFieldName.isNotBlank()) {
                            customFieldsMap[newFieldName.trim()] = newFieldValue.trim()
                            newFieldName = ""
                            newFieldValue = ""
                            showAddCustomFieldDialog = false
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCustomFieldDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
