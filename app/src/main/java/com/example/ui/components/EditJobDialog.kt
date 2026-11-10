package com.example.ui.components

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

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
    var showDayDropdown by remember { mutableStateOf(false) }

    var clientPhone by remember { mutableStateOf(initialJob?.clientPhone ?: "") }
    var deviceType by remember { mutableStateOf(initialJob?.deviceType ?: "") }
    var faultDescription by remember { mutableStateOf(initialJob?.faultDescription ?: "") }
    var costStr by remember { mutableStateOf(if (initialJob != null && initialJob.cost > 0) String.format(Locale.US, "%.0f", initialJob.cost) else "") }
    var expenseCostStr by remember { mutableStateOf(if (initialJob != null && initialJob.expenseCost > 0) String.format(Locale.US, "%.0f", initialJob.expenseCost) else "") }
    var replacedParts by remember { mutableStateOf(initialJob?.replacedParts ?: "") }
    var clientLocation by remember { mutableStateOf(initialJob?.clientLocation ?: "") }
    var otherDetails by remember { mutableStateOf(initialJob?.otherDetails ?: "") }
    var imageUrl by remember { mutableStateOf(initialJob?.imageUrl ?: "") }
    var status by remember { mutableStateOf(initialJob?.status ?: "في المخزون") }

    val daysList = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        val parts = date.split("/").mapNotNull { it.toIntOrNull() }
        val y = if (parts.size >= 1) parts[0] else cal.get(Calendar.YEAR)
        val m = if (parts.size >= 2) (parts[1] - 1).coerceIn(0, 11) else cal.get(Calendar.MONTH)
        val d = if (parts.size >= 3) parts[2].coerceIn(1, 31) else cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formatted = String.format(Locale.US, "%04d/%02d/%02d", selectedYear, selectedMonth + 1, selectedDay)
                date = formatted
                dayOfWeek = TechnicianJobRecord.getDayOfWeekArabic(formatted)
            },
            y, m, d
        ).show()
    }

    val customFieldsMap = remember {
        mutableStateMapOf<String, String>().apply {
            initialJob?.customFields?.forEach { (k, v) -> put(k, v) }
        }
    }

    var showAddCustomFieldDialog by remember { mutableStateOf(false) }
    var newFieldName by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch {
                    val permanentUrl = com.example.util.ImageHelper.saveAndPreparePermanentImage(context, uri)
                    imageUrl = permanentUrl
                }
            }
        }
    )

    val deviceSuggestions = listOf(
        "غسالة سامسونج", "ثلاجة LG", "غسالة فكتوريا", "فريزر كريازي",
        "ميكرويف", "غسالة بيكو", "غسالة توشيبا", "ثلاجة يونيون اير", "تكييف شارب"
    )

    val faultSuggestions = listOf(
        "كشف", "سليمة (كشف فقط)", "باور", "طرمبة", "رطوبة وتمليح", "تفحم مسارات", "عطل EH0",
        "ماتور سريع", "لا يعمل الماتور", "بروسيسور", "لوحة أزرار"
    )

    val statusOptions = listOf(
        "في المخزون",
        "كشف",
        "سليمة",
        "قيد الفحص",
        "جاهز للاستلام",
        "تم التسليم والتحصيل"
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
                // Row: Interactive Date & Dropdown Day of week
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date Field with Interactive DatePicker dialog
                    Box(modifier = Modifier.weight(1.3f)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("التاريخ (تقويم تفاعلي 📅)") },
                            leadingIcon = {
                                IconButton(onClick = { openDatePicker() }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "اختيار التاريخ",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDatePicker() }
                        )
                    }

                    // Day of Week Dropdown Menu
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedTextField(
                            value = dayOfWeek,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("اليوم (قائمة)") },
                            trailingIcon = {
                                IconButton(onClick = { showDayDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "قائمة الأيام")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDayDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showDayDropdown,
                            onDismissRequest = { showDayDropdown = false }
                        ) {
                            daysList.forEach { dayName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = dayName,
                                            fontWeight = if (dayName == dayOfWeek) FontWeight.Bold else FontWeight.Normal,
                                            color = if (dayName == dayOfWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        dayOfWeek = dayName
                                        showDayDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Status Selector (في المخزون / كشف / سليمة / قيد الفحص / جاهز للاستلام / تم التسليم والتحصيل)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("حالة الكارتة والصيانة 📌", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        statusOptions.forEach { opt ->
                            val isSelected = status == opt || (opt == "في المخزون" && status.isBlank())
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clickable { status = opt }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
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

                // Row: Client Cost vs Technician Expenses
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("الحساب من العميل (ج.م)") },
                        placeholder = { Text("مثال: 500") },
                        leadingIcon = { Icon(Icons.Default.PriceCheck, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = expenseCostStr,
                        onValueChange = { expenseCostStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("المصروفات/القطع (ج.م)") },
                        placeholder = { Text("مثال: 150") },
                        leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Live Net Profit Breakdown Banner
                val clientCost = costStr.toDoubleOrNull() ?: 0.0
                val expenseVal = expenseCostStr.toDoubleOrNull() ?: 0.0
                val netProfit = clientCost - expenseVal
                if (clientCost > 0 || expenseVal > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (netProfit >= 0) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (netProfit >= 0) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "التحصيل: ${String.format(Locale.US, "%.0f", clientCost)} - المصاريف: ${String.format(Locale.US, "%.0f", expenseVal)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "صافي المكسب: ${String.format(Locale.US, "%.0f", netProfit)} ج.م",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (netProfit >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

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

                // Other Details
                OutlinedTextField(
                    value = otherDetails,
                    onValueChange = { otherDetails = it },
                    label = { Text("تفاصيل أخرى / ملاحظات فنية") },
                    placeholder = { Text("مثال: تم التوصيل واختبار الموتور تمام...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Image Picker for card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (imageUrl.isNotBlank()) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = if (imageUrl.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                    val expenseVal = expenseCostStr.toDoubleOrNull() ?: 0.0
                    val updated = (initialJob ?: TechnicianJobRecord()).copy(
                        userEmail = currentUserEmail,
                        date = date,
                        dayOfWeek = dayOfWeek,
                        clientPhone = clientPhone,
                        deviceType = deviceType,
                        faultDescription = faultDescription,
                        cost = costVal,
                        expenseCost = expenseVal,
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
                        label = { Text("اسم الخانة الجديدة") },
                        placeholder = { Text("مثال: رقم الشاسيه، المقاس...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newFieldValue,
                        onValueChange = { newFieldValue = it },
                        label = { Text("قيمة الخانة") },
                        placeholder = { Text("مثال: 50A...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFieldName.isNotBlank() && newFieldValue.isNotBlank()) {
                            customFieldsMap[newFieldName.trim()] = newFieldValue.trim()
                            newFieldName = ""
                            newFieldValue = ""
                            showAddCustomFieldDialog = false
                        }
                    },
                    enabled = newFieldName.isNotBlank() && newFieldValue.isNotBlank()
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomFieldDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
