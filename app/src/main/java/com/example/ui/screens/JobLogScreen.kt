package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import coil.compose.AsyncImage
import com.example.data.model.TechnicianJobRecord
import com.example.data.repository.JobRecordRepository
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditJobDialog
import com.example.ui.components.ShareReceiptDialog
import com.example.util.ImageHelper
import java.text.SimpleDateFormat
import java.util.Locale

// Standard lifecycle pipeline for cards
val JOB_STATUSES = listOf(
    "في المخزون",
    "قيد الفحص",
    "جاهز للاستلام",
    "تم التسليم والتحصيل"
)

enum class JobSearchScope(val title: String) {
    ALL("الكل 🌐"),
    PHONE("رقم الهاتف 📞"),
    DEVICE("نوع الكارتة 💻"),
    LOCATION("المكان 📍"),
    DATE("التاريخ 📅"),
    FAULT("العطل / كشف ⚡"),
    PARTS("قطع الغيار 🔧")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobLogScreen(
    userEmail: String?,
    onBackClick: () -> Unit,
    onOpenCustomers: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentEmail = (userEmail ?: "guest").trim().lowercase()

    val jobsList by JobRecordRepository.jobs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchScope by remember { mutableStateOf(JobSearchScope.ALL) }
    var selectedFilterStatus by remember { mutableStateOf("الكل") }

    // Multi-selection for batch actions & receipts
    val selectedJobIds = remember { mutableStateListOf<String>() }

    var jobToEdit by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Jobs to share in receipt dialog (supports single or multi-card)
    var jobsToShare by remember { mutableStateOf<List<TechnicianJobRecord>?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    var jobToDelete by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Batch Action Dialogs
    var showBatchStatusDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // Quick status transfer dialog
    var jobToMoveStatus by remember { mutableStateOf<TechnicianJobRecord?>(null) }

    // Monthly profit statistics dialog
    var showMonthlyStatsDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBackClick()
    }

    val filteredJobs = jobsList.filter { job ->
        val q = searchQuery.trim()
        val matchesSearch = q.isBlank() || when (selectedSearchScope) {
            JobSearchScope.ALL -> job.clientPhone.contains(q) ||
                    job.deviceType.contains(q, ignoreCase = true) ||
                    job.faultDescription.contains(q, ignoreCase = true) ||
                    job.clientLocation.contains(q, ignoreCase = true) ||
                    job.date.contains(q) ||
                    job.dayOfWeek.contains(q) ||
                    job.replacedParts.contains(q, ignoreCase = true)
            JobSearchScope.PHONE -> job.clientPhone.contains(q)
            JobSearchScope.DEVICE -> job.deviceType.contains(q, ignoreCase = true)
            JobSearchScope.LOCATION -> job.clientLocation.contains(q, ignoreCase = true)
            JobSearchScope.DATE -> job.date.contains(q) || job.dayOfWeek.contains(q)
            JobSearchScope.FAULT -> job.faultDescription.contains(q, ignoreCase = true) || job.status.contains(q, ignoreCase = true)
            JobSearchScope.PARTS -> job.replacedParts.contains(q, ignoreCase = true)
        }

        val matchesStatus = when (selectedFilterStatus) {
            "الكل" -> true
            "في المخزون" -> job.status.contains("المخزون") || job.status.isBlank()
            "قيد الفحص" -> job.status.contains("الفحص") || job.status.contains("صيانة")
            "جاهز للاستلام" -> job.status.contains("جاهز")
            "تم التسليم والتحصيل" -> job.status.contains("تسليم") || job.status.contains("تحصيل")
            "كشف وسليمة فقط ✅" -> job.isInspectionSound
            "صيانة فقط 🔧" -> !job.isInspectionSound
            else -> job.status == selectedFilterStatus
        }

        matchesSearch && matchesStatus
    }

    val totalRevenue = jobsList.sumOf { it.cost }
    val totalExpenses = jobsList.sumOf { it.expenseCost }
    val totalNetProfit = totalRevenue - totalExpenses

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "سجل صيانة الورشة والكروت 📋",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (userEmail.isNullOrBlank()) "سجل الكروت المحلي" else "دفتر خاص: $userEmail",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    if (onOpenCustomers != null) {
                        IconButton(onClick = onOpenCustomers) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "دليل وسجل العملاء",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }
                    // Monthly profit statistics button
                    IconButton(onClick = { showMonthlyStatsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "إحصائية الأرباح الشهرية",
                            tint = Color(0xFF10B981)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    jobToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة شريحة كارت ➕", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Metrics Summary Bar with Live Revenues, Expenses, Net Profit, and Monthly Stats trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Total revenue card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("التحصيل", fontSize = 10.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Medium)
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", totalRevenue)} ج.م",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }
                }

                // Total expenses card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("المصروفات", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", totalExpenses)} ج.م",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                    }
                }

                // Net profit card (highlighted)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                    border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1.2f)
                        .clickable { showMonthlyStatsDialog = true }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("صافي المكسب 💰", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", totalNetProfit)} ج.م",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalNetProfit >= 0) Color(0xFF065F46) else Color(0xFFDC2626)
                        )
                    }
                }

                // Statistics Trigger Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .clickable { showMonthlyStatsDialog = true }
                        .padding(vertical = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("أرباح الشهور", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Batch Action Toolbar OR Single Creation Row
            if (selectedJobIds.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.2.dp, Color(0xFF0284C7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تم تحديد ${selectedJobIds.size} كروت 🎯",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        if (selectedJobIds.size == filteredJobs.size) {
                                            selectedJobIds.clear()
                                        } else {
                                            selectedJobIds.clear()
                                            selectedJobIds.addAll(filteredJobs.map { it.id })
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (selectedJobIds.size == filteredJobs.size) "إلغاء الكل" else "تحديد الكل",
                                        fontSize = 11.sp
                                    )
                                }
                                TextButton(
                                    onClick = { selectedJobIds.clear() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("إلغاء ✖", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Batch Action Buttons Row (تغيير الحالة مع بعض / حذف مع بعض / إيصال مجمع)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Combined Receipt
                            Button(
                                onClick = {
                                    val selectedJobs = jobsList.filter { selectedJobIds.contains(it.id) }
                                    if (selectedJobs.isNotEmpty()) {
                                        jobsToShare = selectedJobs
                                        showShareDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إيصال مجمع", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Batch Status Change (طلب المستخدم: اغير حالتها مع بعض)
                            Button(
                                onClick = { showBatchStatusDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تغيير الحالة 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Batch Delete (طلب المستخدم: احذفهم مع بعض)
                            Button(
                                onClick = { showBatchDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            jobToEdit = null
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة كارت جديد ➕", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (onOpenCustomers != null) {
                        OutlinedButton(
                            onClick = onOpenCustomers,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("دليل وسجل العملاء 👥", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    } else {
                        Text(
                            text = "حدد كروت لتغيير حالتها أو حذفها معاً ✓",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Bar with Dynamic Scope Placeholder
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = {
                    Text(
                        text = when (selectedSearchScope) {
                            JobSearchScope.ALL -> "🔍 ابحث برقم الهاتف، الكارتة، المكان، أو العطل..."
                            JobSearchScope.PHONE -> "🔍 ابحث في رقم هاتف العميل..."
                            JobSearchScope.DEVICE -> "🔍 ابحث في نوع الجهاز والكارتة..."
                            JobSearchScope.LOCATION -> "🔍 ابحث في مكان أو عنوان العميل..."
                            JobSearchScope.DATE -> "🔍 ابحث في تاريخ أو يوم الصيانة..."
                            JobSearchScope.FAULT -> "🔍 ابحث في العطل أو الكشف أو السليمة..."
                            JobSearchScope.PARTS -> "🔍 ابحث في قطع الغيار المبدلة..."
                        },
                        fontSize = 11.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Search Scope Selector Chips (طلب المستخدم: فلتر اقدر اختار هبحث فين برقم العميل او مكانه او الكارته او التاريخ)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نطاق البحث:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(JobSearchScope.values()) { scope ->
                        val isSelected = selectedSearchScope == scope
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .clickable { selectedSearchScope = scope }
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = scope.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Filter Chips (الكل / في المخزون / كشف / سليمة / قيد الفحص / جاهز للاستلام / تم التسليم والتحصيل)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("الكل", "في المخزون", "قيد الفحص", "جاهز للاستلام", "تم التسليم والتحصيل", "كشف وسليمة فقط ✅", "صيانة فقط 🔧")
                items(filters) { flt ->
                    val count = when (flt) {
                        "الكل" -> jobsList.size
                        "في المخزون" -> jobsList.count { it.status.contains("المخزون") || it.status.isBlank() }
                        "قيد الفحص" -> jobsList.count { it.status.contains("الفحص") || it.status.contains("صيانة") }
                        "جاهز للاستلام" -> jobsList.count { it.status.contains("جاهز") }
                        "تم التسليم والتحصيل" -> jobsList.count { it.status.contains("تسليم") || it.status.contains("تحصيل") }
                        "كشف وسليمة فقط ✅" -> jobsList.count { it.isInspectionSound }
                        "صيانة فقط 🔧" -> jobsList.count { !it.isInspectionSound }
                        else -> 0
                    }
                    FilterChip(
                        selected = selectedFilterStatus == flt,
                        onClick = { selectedFilterStatus = flt },
                        label = { Text("$flt ($count)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Chips List (شرايح صغيرة تفتح عند الضغط عليها)
            if (filteredJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد كروت مسجلة في هذا القسم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "انقر على زر (إضافة كارت جديد ➕) لإضافة أول كارتة صيانة",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredJobs, key = { it.id }) { job ->
                        val isSelected = selectedJobIds.contains(job.id)
                        JobChipCard(
                            job = job,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedJobIds.remove(job.id)
                                else selectedJobIds.add(job.id)
                            },
                            onQuickMoveStatus = {
                                jobToMoveStatus = job
                            },
                            onShareReceipt = {
                                jobsToShare = listOf(job)
                                showShareDialog = true
                            },
                            onEdit = {
                                jobToEdit = job
                                showEditDialog = true
                            },
                            onDelete = {
                                jobToDelete = job
                                showDeleteConfirmDialog = true
                            },
                            onCall = { openDialer(context, job.clientPhone) },
                            onWhatsApp = {
                                openWhatsApp(context, job.clientPhone, "السلام عليكم، بخصوص صيانة كارتة (${job.deviceType}) في مركز البراء إلكترونيك")
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Quick Status Transfer (طلب المستخدم: انقل كل كارت من خانه لخانه بزرار بس من غير ما اعدل في الشريحة)
    if (jobToMoveStatus != null) {
        val targetJob = jobToMoveStatus!!
        QuickStatusTransferDialog(
            currentStatus = targetJob.status,
            deviceType = targetJob.deviceType,
            onDismiss = { jobToMoveStatus = null },
            onSelectStatus = { newStatus ->
                val updated = targetJob.copy(status = newStatus, updatedAt = System.currentTimeMillis())
                JobRecordRepository.saveJob(context, updated) { success ->
                    if (success) {
                        Toast.makeText(context, "تم نقل الكارتة إلى ($newStatus) بنجاح 🔄", Toast.LENGTH_SHORT).show()
                    }
                }
                jobToMoveStatus = null
            }
        )
    }

    // Dialog: Monthly Profit & Statistics (طلب المستخدم: حساب ومكسب في اخر كل شهر وتكون فيها احصائية)
    if (showMonthlyStatsDialog) {
        MonthlyProfitStatisticsDialog(
            jobs = jobsList,
            onDismiss = { showMonthlyStatsDialog = false }
        )
    }

    // Dialog: Edit or Add Job
    if (showEditDialog) {
        EditJobDialog(
            initialJob = jobToEdit,
            currentUserEmail = currentEmail,
            onDismiss = { showEditDialog = false },
            onSave = { updatedJob ->
                JobRecordRepository.saveJob(context, updatedJob) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حفظ شريحة الكارت بنجاح ✅", Toast.LENGTH_SHORT).show()
                        showEditDialog = false
                    }
                }
            }
        )
    }

    // Dialog: Share Receipt (Single or Multi-card)
    if (showShareDialog && jobsToShare != null) {
        ShareReceiptDialog(
            jobs = jobsToShare!!,
            onDismiss = {
                showShareDialog = false
                jobsToShare = null
            }
        )
    }

    // Dialog: Delete Confirm
    if (showDeleteConfirmDialog && jobToDelete != null) {
        ConfirmDeleteDialog(
            title = "حذف شريحة الكارتة ⚠️",
            message = "هل أنت متأكد من حذف كارتة (${jobToDelete!!.deviceType} - ${jobToDelete!!.clientPhone}) من سجلك؟",
            onConfirm = {
                JobRecordRepository.deleteJob(context, jobToDelete!!.id) {
                    Toast.makeText(context, "تم حذف الكارتة بنجاح", Toast.LENGTH_SHORT).show()
                    showDeleteConfirmDialog = false
                    jobToDelete = null
                }
            },
            onDismiss = {
                showDeleteConfirmDialog = false
                jobToDelete = null
            }
        )
    }

    // Dialog: Batch Status Change (طلب المستخدم: اغير حالتهم مع بعض)
    if (showBatchStatusDialog && selectedJobIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchStatusDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF8B5CF6))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تغيير حالة (${selectedJobIds.size}) كروت معاً 🔄", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر الحالة الجديدة لتطبيقها على جميع الكروت المحددة دفعة واحدة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    JOB_STATUSES.forEach { statusName ->
                        val color = getStatusColor(statusName)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = color.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val count = selectedJobIds.size
                                    JobRecordRepository.batchUpdateStatus(context, selectedJobIds.toSet(), statusName) { success ->
                                        if (success) {
                                            Toast.makeText(context, "تم تغيير حالة $count كروت إلى ($statusName) بنجاح ✅", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    selectedJobIds.clear()
                                    showBatchStatusDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(statusName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchStatusDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog: Batch Delete (طلب المستخدم: احذفهم مع بعض)
    if (showBatchDeleteDialog && selectedJobIds.isNotEmpty()) {
        ConfirmDeleteDialog(
            title = "حذف (${selectedJobIds.size}) كروت محددة ⚠️",
            message = "هل أنت متأكد من حذف ${selectedJobIds.size} كروت معاً نهائياً من سجلك؟ لا يمكن استرجاعها بعد الحذف.",
            onConfirm = {
                val count = selectedJobIds.size
                JobRecordRepository.batchDeleteJobs(context, selectedJobIds.toSet()) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حذف $count كروت بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                    }
                }
                selectedJobIds.clear()
                showBatchDeleteDialog = false
            },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }
}

/**
 * Compact Chip/Card for each job that expands smoothly when tapped.
 * With 1-click status transfer button, expense breakdown, and full details.
 */
@Composable
fun JobChipCard(
    job: TechnicianJobRecord,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onQuickMoveStatus: () -> Unit,
    onShareReceipt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            1.2.dp,
            if (isSelected) Color(0xFF0284C7)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // COMPACT ROW (الشريحة الصغيرة)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selection Checkbox for multi-card receipt
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7)),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = job.deviceType,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (job.isInspectionSound) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✅ كشف (سليمة)",
                                        fontSize = 10.sp,
                                        color = Color(0xFF047857),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (job.faultDescription.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = job.faultDescription,
                                        fontSize = 10.sp,
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${job.dayOfWeek} ${job.date} • ${job.clientPhone.ifBlank { "بدون هاتف" }}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Status Badge / Move Button (الزرار اللي بينقل الشريحة من خانة لخانة مباشرة)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = getStatusColor(job.status).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, getStatusColor(job.status).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onQuickMoveStatus() }
                            .padding(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = job.status.ifBlank { "في المخزون" },
                                color = getStatusColor(job.status),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "نقل الحالة",
                                tint = getStatusColor(job.status),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (job.cost > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.0f", job.cost)} ج.م",
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "إغلاق" else "فتح التفاصيل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // EXPANDED DETAILS (تفتح كل اللي جواها)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 8.dp))

                    // Profit Breakdown if expense exists
                    if (job.cost > 0 || job.expenseCost > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("المطلوب: ${String.format(Locale.US, "%.0f", job.cost)} ج.م", fontSize = 10.sp, color = Color(0xFF0284C7))
                                    Text("المصروف: ${String.format(Locale.US, "%.0f", job.expenseCost)} ج.م", fontSize = 10.sp, color = Color(0xFFEF4444))
                                }
                                Text(
                                    text = "صافي المكسب: ${String.format(Locale.US, "%.0f", job.netProfit)} ج.م",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (job.netProfit >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    // Replaced parts
                    if (job.replacedParts.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("قطع الغيار: ${job.replacedParts}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Location
                    if (job.clientLocation.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("المكان: ${job.clientLocation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Notes
                    if (job.otherDetails.isNotBlank()) {
                        Text("ملاحظات: ${job.otherDetails}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Custom fields
                    if (job.customFields.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            job.customFields.forEach { (k, v) ->
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("$k: $v", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Photo thumbnail if present
                    if (job.imageUrl.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.2f))
                            ) {
                                AsyncImage(
                                    model = ImageHelper.getCoilData(job.imageUrl),
                                    contentDescription = "صورة الكارتة",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("صورة الكارتة محفوظة بالسجل ✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Phone & Direct Communication buttons
                    if (job.clientPhone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(job.clientPhone, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .clickable { onWhatsApp() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("واتساب 💬", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clickable { onCall() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("اتصال 📞", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                    // Action buttons: Transfer Status Button + Share Receipt + Edit + Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Quick transfer status button
                            OutlinedButton(
                                onClick = onQuickMoveStatus,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, getStatusColor(job.status))
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = getStatusColor(job.status), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نقل الحالة 🔄", fontSize = 10.sp, color = getStatusColor(job.status), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onShareReceipt,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة إيصال 📸", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Status Transfer Dialog (تنقل الكارتة بزرار واحد بدون تعديل في الشريحة)
 */
@Composable
fun QuickStatusTransferDialog(
    currentStatus: String,
    deviceType: String,
    onDismiss: () -> Unit,
    onSelectStatus: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF0284C7))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("نقل حالة الكارتة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(deviceType, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "اختر الحالة الجديدة لنقل الكارتة إليها مباشرة:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                JOB_STATUSES.forEach { statusName ->
                    val isCurrent = statusName == currentStatus || (statusName == "في المخزون" && currentStatus.isBlank())
                    val color = getStatusColor(statusName)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.2.dp, if (isCurrent) color else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectStatus(statusName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = statusName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isCurrent) color else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "الحالية",
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Monthly Profit Statistics Dialog:
 * يحسب ويخصم المصروفات من التحصيل ويعرض حساب ومكسب في آخر كل شهر مع إحصائية كاملة.
 */
@Composable
fun MonthlyProfitStatisticsDialog(
    jobs: List<TechnicianJobRecord>,
    onDismiss: () -> Unit
) {
    // Group jobs by year and month (YYYY/MM)
    val monthlyGroups = remember(jobs) {
        jobs.groupBy { job ->
            val dateParts = job.date.split("/")
            if (dateParts.size >= 2) "${dateParts[0]}/${dateParts[1]}" else "أخرى"
        }.toSortedMap(reverseOrder())
    }

    val totalAllRevenue = jobs.sumOf { it.cost }
    val totalAllExpenses = jobs.sumOf { it.expenseCost }
    val totalAllProfit = totalAllRevenue - totalAllExpenses

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
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("إحصائية وحساب أرباح الشهور 📊", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("حساب المكسب النهائي مخصوماً منه المصاريف", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Grand Total Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("إجمالي الأرباح الكلية لجميع الكروت 💰", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF047857))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("إجمالي التحصيل: ${String.format(Locale.US, "%,.0f", totalAllRevenue)} ج.م", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("إجمالي المصاريف: ${String.format(Locale.US, "%,.0f", totalAllExpenses)} ج.م", fontSize = 11.sp, color = Color(0xFFDC2626))
                            }
                            Text(
                                text = "${String.format(Locale.US, "%,.0f", totalAllProfit)} ج.م",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalAllProfit >= 0) Color(0xFF065F46) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Text("تفاصيل أرباح كل شهر على حدة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                if (monthlyGroups.isEmpty()) {
                    Text("لا توجد كروت مسجلة حتى الآن لحساب أرباح الشهور", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    monthlyGroups.forEach { (yearMonth, monthJobs) ->
                        val monthRevenue = monthJobs.sumOf { it.cost }
                        val monthExpense = monthJobs.sumOf { it.expenseCost }
                        val monthNetProfit = monthRevenue - monthExpense
                        val arabicMonthName = formatArabicMonthName(yearMonth)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${monthJobs.size} كروت",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(arabicMonthName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Text(
                                        text = "صافي المكسب: ${String.format(Locale.US, "%,.0f", monthNetProfit)} ج.م",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (monthNetProfit >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("المبلغ المُحصّل: ${String.format(Locale.US, "%,.0f", monthRevenue)} ج.م", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("المصروف على الكروت: ${String.format(Locale.US, "%,.0f", monthExpense)} ج.م", fontSize = 10.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

fun formatArabicMonthName(yearMonth: String): String {
    return try {
        val parts = yearMonth.split("/")
        if (parts.size >= 2) {
            val y = parts[0]
            val m = parts[1].toIntOrNull() ?: 1
            val arabicMonths = listOf(
                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
            )
            val monthName = arabicMonths.getOrElse(m - 1) { "شهر $m" }
            "$monthName $y"
        } else yearMonth
    } catch (_: Throwable) {
        yearMonth
    }
}

fun getStatusColor(status: String): Color {
    return when {
        status.contains("المخزون") -> Color(0xFF8B5CF6) // Purple
        status.contains("كشف") -> Color(0xFFF59E0B) // Amber
        status.contains("سليمة") -> Color(0xFF059669) // Green
        status.contains("الفحص") || status.contains("صيانة") -> Color(0xFFD97706) // Amber
        status.contains("جاهز") -> Color(0xFF0284C7) // Sky blue
        status.contains("تسليم") || status.contains("تحصيل") -> Color(0xFF10B981) // Emerald Green
        else -> Color(0xFF6B7280)
    }
}

private fun openDialer(context: Context, phone: String) {
    if (phone.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح لوحة الاتصال", Toast.LENGTH_SHORT).show()
    }
}

private fun openWhatsApp(context: Context, phone: String, message: String) {
    if (phone.isBlank()) return
    try {
        val url = TechnicianJobRecord.formatWhatsAppUrl(phone) + "?text=" + Uri.encode(message)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح تطبيق واتساب", Toast.LENGTH_SHORT).show()
    }
}
