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
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TechnicianJobRecord
import com.example.data.repository.JobRecordRepository
import java.util.Locale

data class CustomerEntry(
    val key: String,
    val phone: String,
    val location: String,
    val cardsCount: Int,
    val totalSpent: Double,
    val jobs: List<TechnicianJobRecord>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersDirectoryScreen(
    userEmail: String?,
    onBackClick: () -> Unit,
    onOpenJobLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val jobsList by JobRecordRepository.jobs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val expandedCustomerMap = remember { mutableStateMapOf<String, Boolean>() }

    BackHandler {
        onBackClick()
    }

    // Aggregate cards per unique customer (grouped by phone if available, or location)
    val customerEntries = remember(jobsList) {
        val groups = jobsList.groupBy { job ->
            val cleanPhone = job.clientPhone.trim()
            if (cleanPhone.isNotBlank()) cleanPhone
            else if (job.clientLocation.isNotBlank()) "loc_${job.clientLocation.trim()}"
            else "job_${job.id}"
        }

        groups.map { (key, list) ->
            val first = list.first()
            val phone = list.firstOrNull { it.clientPhone.isNotBlank() }?.clientPhone ?: ""
            val location = list.firstOrNull { it.clientLocation.isNotBlank() }?.clientLocation ?: first.clientLocation
            CustomerEntry(
                key = key,
                phone = phone,
                location = location,
                cardsCount = list.size,
                totalSpent = list.sumOf { it.cost },
                jobs = list.sortedByDescending { it.createdAt }
            )
        }.sortedByDescending { it.cardsCount }
    }

    val uniqueCustomersCount = customerEntries.size
    val totalRevenueAllCustomers = customerEntries.sumOf { it.totalSpent }

    val filteredCustomers = customerEntries.filter { c ->
        if (searchQuery.isBlank()) true
        else {
            val q = searchQuery.trim().lowercase()
            c.phone.contains(q) ||
                    c.location.lowercase().contains(q) ||
                    c.jobs.any { it.deviceType.lowercase().contains(q) || it.faultDescription.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "دليل وسجل العملاء 👥",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (userEmail.isNullOrBlank()) "دليل عملاء الورشة" else "سجل عملاء حساب: $userEmail",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Metrics Banner: "مكتوب برده فوق كم عميل جالي بحس شخص العميل مش كام كارتة"
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إجمالي العملاء المستقلين",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "$uniqueCustomersCount عميل مسجل 👤",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "إجمالي الكروت: ${jobsList.size} كارتة",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", totalRevenueAllCustomers)} ج.م",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("ابحث برقم هاتف العميل، المكان، أو الكارتة...", fontSize = 12.sp) },
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

            // Customers List
            if (filteredCustomers.isEmpty()) {
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
                            Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا يوجد عملاء يطابقون البحث" else "لا يوجد عملاء مسجلين بعد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "تضاف بيانات العملاء تلقائياً بمجرد تسجيل الكروت والشغل في سجل الصيانة",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers, key = { it.key }) { customer ->
                        val isExpanded = expandedCustomerMap[customer.key] == true
                        CustomerCardItem(
                            customer = customer,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedCustomerMap[customer.key] = !isExpanded
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerCardItem(
    customer: CustomerEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val phone = customer.phone

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.09f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (phone.isNotBlank()) phone else "عميل بدون هاتف",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${customer.cardsCount} كروت",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (customer.location.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(customer.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", customer.totalSpent)} ج.م",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = if (isExpanded) "إخفاء التفاصيل" else "عرض الكروت",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Quick Contact Buttons (Call & WhatsApp)
            if (phone.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            try {
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                                Toast.makeText(context, "تعذر فتح لوحة الاتصال", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اتصال 📞", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    val whatsappUrl = TechnicianJobRecord.formatWhatsAppUrl(phone)
                    Button(
                        onClick = {
                            if (whatsappUrl.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                                try {
                                    context.startActivity(intent)
                                } catch (_: Throwable) {
                                    Toast.makeText(context, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("واتساب 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable List of Customer's Cards
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "كروت وشغل هذا العميل (${customer.jobs.size} كروت):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    customer.jobs.forEachIndexed { idx, j ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${idx + 1}. ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(j.deviceType, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (j.faultDescription.isNotBlank()) {
                                        Text("العطل: ${j.faultDescription}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${j.dayOfWeek} ${j.date}", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", j.cost)} ج.م",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when {
                                            j.status.contains("تسليم") -> Color(0xFF10B981).copy(alpha = 0.15f)
                                            j.status.contains("سليمة") -> Color(0xFF10B981).copy(alpha = 0.15f)
                                            j.status.contains("كشف") -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                            j.status.contains("جاهز") -> Color(0xFF0284C7).copy(alpha = 0.15f)
                                            else -> Color(0xFF64748B).copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = j.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
