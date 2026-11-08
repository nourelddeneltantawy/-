package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TechnicianJobRecord
import com.example.data.repository.JobRecordRepository
import com.example.ui.components.EditJobDialog
import com.example.ui.components.ShareReceiptDialog

data class ToolMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val circleBackground: Color,
    val badgeText: String? = null,
    val onClick: () -> Unit
)

@Composable
fun ToolsHubScreen(
    userEmail: String?,
    isAdmin: Boolean,
    onNavigateToJobLog: () -> Unit,
    onNavigateToIntegrator: () -> Unit,
    onNavigateToHarnesses: () -> Unit,
    onNavigateToDiagnostics: (String) -> Unit,
    onNavigateToAiStudio: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentEmail = (userEmail ?: "guest").trim().lowercase()

    val jobsList by JobRecordRepository.jobs.collectAsState()
    val jobsCount = jobsList.size

    var jobToEdit by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showEditJobDialog by remember { mutableStateOf(false) }

    var jobToShare by remember { mutableStateOf<TechnicianJobRecord?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    val toolsList = listOf(
        ToolMenuItem(
            id = "job_log",
            title = "سجل صيانة الورشة والكروت",
            subtitle = if (jobsCount > 0) "$jobsCount كارتة مسجلة بسجلك الخاص" else "تسجيل شغل الكروت وفواتير الزبائن",
            icon = Icons.Default.Receipt,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            badgeText = if (jobsCount > 0) "$jobsCount" else "جديد",
            onClick = onNavigateToJobLog
        ),
        ToolMenuItem(
            id = "receipt_generator",
            title = "مولد ومشارك إيصالات الصيانة",
            subtitle = "تحديد الخانات وإرسال صورة منظمة بختم التطبيق",
            icon = Icons.Default.Share,
            iconTint = Color(0xFF10B981),
            circleBackground = Color(0xFFD1FAE5),
            badgeText = "ختم معتمد",
            onClick = {
                if (jobsList.isNotEmpty()) {
                    jobToShare = jobsList.first()
                    showShareDialog = true
                } else {
                    onNavigateToJobLog()
                }
            }
        ),
        ToolMenuItem(
            id = "motor_tester",
            title = "فاحص ومختبر أطراف المواتير",
            subtitle = "فحص التاكو، الشربون، والسرعات والربط الذكي",
            icon = Icons.Default.Memory,
            iconTint = Color(0xFFEF4444),
            circleBackground = Color(0xFFFFE4E6),
            onClick = onNavigateToIntegrator
        ),
        ToolMenuItem(
            id = "harness_map",
            title = "خريطة ضفائر الغسالات",
            subtitle = "ألوان الأسلاك ومسارات الفيش التفاعلية",
            icon = Icons.Default.Cable,
            iconTint = Color(0xFF8B5CF6),
            circleBackground = Color(0xFFF3E8FF),
            onClick = onNavigateToHarnesses
        ),
        ToolMenuItem(
            id = "diagnostics",
            title = "دليل تشخيص رموز الأعطال",
            subtitle = "أكواد أعطال الأوتوماتيك والحلول الفنية",
            icon = Icons.Default.Warning,
            iconTint = Color(0xFFD97706),
            circleBackground = Color(0xFFFEF3C7),
            onClick = { onNavigateToDiagnostics("أعطال الأوتوماتيك") }
        ),
        ToolMenuItem(
            id = "ai_studio",
            title = "استوديو الذكاء الاصطناعي لفحص الكروت",
            subtitle = "تحليل صور البورد وتحديد المكونات المحترقة",
            icon = Icons.Default.AutoAwesome,
            iconTint = Color(0xFF06B6D4),
            circleBackground = Color(0xFFCFFAFE),
            badgeText = "AI",
            onClick = onNavigateToAiStudio
        )
    )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // TOP HEADER: Curved gradient banner matching DashboardHubScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(top = innerPadding.calculateTopPadding() + 8.dp, bottom = 22.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userEmail != null) "مرحباً بك،" else "أهلاً بك،",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (userEmail != null) "م. البراء إلكترونيك" else "فني الصيانة الزائر",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "مركز الأدوات الذكية وسجل الصيانة ⚡",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }

                    // Circular App Logo / Avatar
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(54.dp)
                            .clickable { onNavigateToProfile() }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_albaraa),
                            contentDescription = "شعار التطبيق",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                }
            }

            // Body: Tools Grid / List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section Title: قوائم الأدوات المتاحة
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "قوائم الأدوات المتاحة ⚡",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Button(
                            onClick = {
                                jobToEdit = null
                                showEditJobDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسجيل شغل جديد ➕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Primary Featured Banner: سجل صيانة الورشة والكروت
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToJobLog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "دفتر وسجل صيانة الورشة والكروت",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF10B981)
                                        ) {
                                            Text(
                                                text = "خاص بك",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "تسجيل الشغل، الحسابات، قطع الغيار، ومشاركة صور الفواتير بختم رسمي للعملاء",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Recent jobs quick carousel if any
                if (jobsList.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "آخر الكروت المسجلة في سجلك (${jobsList.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = onNavigateToJobLog) {
                                    Text("عرض الكل ⬅️", fontSize = 11.sp)
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(jobsList.take(5)) { job ->
                                    QuickJobCard(
                                        job = job,
                                        onShare = {
                                            jobToShare = job
                                            showShareDialog = true
                                        },
                                        onClick = onNavigateToJobLog
                                    )
                                }
                            }
                        }
                    }
                }

                // All Tools Grid / List Items
                items(toolsList) { tool ->
                    ToolRowCard(tool = tool)
                }
            }
        }
    }

    // Dialog: Add new job
    if (showEditJobDialog) {
        EditJobDialog(
            initialJob = jobToEdit,
            currentUserEmail = currentEmail,
            onDismiss = { showEditJobDialog = false },
            onSave = { newJob ->
                JobRecordRepository.saveJob(context, newJob) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حفظ الشغل بنجاح في سجلك ✅", Toast.LENGTH_SHORT).show()
                        showEditJobDialog = false
                    }
                }
            }
        )
    }

    // Dialog: Share Receipt
    if (showShareDialog && jobToShare != null) {
        ShareReceiptDialog(
            job = jobToShare!!,
            onDismiss = {
                showShareDialog = false
                jobToShare = null
            }
        )
    }
}

@Composable
fun ToolRowCard(tool: ToolMenuItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tool.onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(tool.circleBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = tool.iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tool.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (tool.badgeText != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tool.iconTint.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = tool.badgeText,
                                    color = tool.iconTint,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tool.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun QuickJobCard(
    job: TechnicianJobRecord,
    onShare: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.deviceType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                if (job.cost > 0) {
                    Text(
                        text = "${String.format("%.0f", job.cost)} ج.م",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "العطل: ${job.faultDescription}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = "${job.dayOfWeek} ${job.date} • ${job.clientPhone}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مشاركة إيصال 📸", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
