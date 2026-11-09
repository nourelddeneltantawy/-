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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobLogScreen(
    userEmail: String?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val currentEmail = (userEmail ?: "guest").trim().lowercase()

    val jobsList by JobRecordRepository.jobs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("الكل") }

    // Multi-selection for receipts
    val selectedJobIds = remember { mutableStateListOf<String>() }

    var jobToEdit by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Jobs to share in receipt dialog (supports single or multi-card)
    var jobsToShare by remember { mutableStateOf<List<TechnicianJobRecord>?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    var jobToDelete by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBackClick()
    }

    val filteredJobs = jobsList.filter { job ->
        val matchesSearch = searchQuery.isBlank() ||
                job.clientPhone.contains(searchQuery.trim()) ||
                job.deviceType.contains(searchQuery.trim(), ignoreCase = true) ||
                job.faultDescription.contains(searchQuery.trim(), ignoreCase = true) ||
                job.clientLocation.contains(searchQuery.trim(), ignoreCase = true) ||
                job.date.contains(searchQuery.trim()) ||
                job.replacedParts.contains(searchQuery.trim(), ignoreCase = true)

        val matchesStatus = when (selectedFilterStatus) {
            "الكل" -> true
            else -> job.status == selectedFilterStatus
        }

        matchesSearch && matchesStatus
    }

    val totalRevenue = jobsList.sumOf { it.cost }
    val deliveredCount = jobsList.count { it.status.contains("تسليم") }

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
            // Metrics Summary Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("إجمالي الحسابات", fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Medium)
                        Text(
                            text = "${String.format("%,.0f", totalRevenue)} ج.م",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("الكروت المسجلة", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${jobsList.size} شريحة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("تم تسليمها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$deliveredCount",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Top Action Row: Add New Card + Multi-card Receipt Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If cards are selected, show combined receipt button
                if (selectedJobIds.isNotEmpty()) {
                    Button(
                        onClick = {
                            val selectedJobs = jobsList.filter { selectedJobIds.contains(it.id) }
                            if (selectedJobs.isNotEmpty()) {
                                jobsToShare = selectedJobs
                                showShareDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إيصال مجمع (${selectedJobIds.size} كروت) 📸", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { selectedJobIds.clear() }) {
                        Text("إلغاء التحديد", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                } else {
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

                    Text(
                        text = "اختر كروت لدمجها في إيصال واحد ✓",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("🔍 ابحث برقم الهاتف، نوع الكارتة، أو العطل...", fontSize = 12.sp) },
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

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("الكل", "قيد الفحص", "جاهز للاستلام", "تم التسليم والتحصيل")
                filters.forEach { flt ->
                    FilterChip(
                        selected = selectedFilterStatus == flt,
                        onClick = { selectedFilterStatus = flt },
                        label = { Text(flt, fontSize = 11.sp) },
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
                            text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لا توجد كروت مسجلة بالسجل",
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
}

/**
 * Compact Chip/Card for each job that expands smoothly when tapped.
 * "عايز الصفحة يكون فيها زي شرايح، كل شريحة هي الكارت اللي أنا ضفته، وتكون الشرايح صغيرة، لما أدوس عليها تفتح كل اللي جواها"
 */
@Composable
fun JobChipCard(
    job: TechnicianJobRecord,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
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
                            if (job.faultDescription.isNotBlank()) {
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
                    if (job.cost > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${String.format("%.0f", job.cost)} ج.م",
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

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onShareReceipt,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة إيصال 📸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openWhatsApp(context: Context, phone: String, message: String) {
    try {
        val clean = phone.filter { it.isDigit() }
        val target = if (clean.startsWith("0")) "2$clean" else clean
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$target&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تطبيق واتساب غير مثبت", Toast.LENGTH_SHORT).show()
    }
}

private fun openDialer(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح لوحة الاتصال", Toast.LENGTH_SHORT).show()
    }
}
