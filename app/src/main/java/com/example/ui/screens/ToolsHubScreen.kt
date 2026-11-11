package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ToolsCategoryManager
import com.example.data.model.HubCategoryCustomization
import com.example.data.repository.AdminRepository
import com.example.data.repository.JobRecordRepository

data class ToolGridItem(
    val id: String,
    val defaultTitle: String,
    val defaultSubtitle: String,
    val defaultIcon: ImageVector,
    val iconTint: Color,
    val circleBackground: Color,
    val isCustom: Boolean = false,
    val onClick: () -> Unit
)

fun resolveToolIcon(iconName: String, fallback: ImageVector): ImageVector {
    return when (iconName.lowercase().trim()) {
        "receipt", "فاتورة", "إيصال", "شغل", "كروت" -> Icons.Default.Receipt
        "share", "مشاركة", "إرسال" -> Icons.Default.Share
        "warning", "عطل", "أعطال", "تشخيص" -> Icons.Default.Warning
        "ai", "ذكاء", "auto" -> Icons.Default.AutoAwesome
        "memory", "موتور", "مواتير" -> Icons.Default.Memory
        "cable", "ضفيرة", "ضفائر" -> Icons.Default.Cable
        "build", "صيانة", "شاكوش", "عدة" -> Icons.Default.Build
        "electric", "كهرباء", "باور" -> Icons.Default.ElectricBolt
        "hub", "تجميع", "مخطط" -> Icons.Default.Hub
        "manufacturing", "صناعي", "بوردات" -> Icons.Default.PrecisionManufacturing
        "grid", "قائمة", "أداة" -> Icons.Default.GridView
        else -> fallback
    }
}

@Composable
fun ToolsHubScreen(
    userEmail: String?,
    isAdmin: Boolean,
    onNavigateToJobLog: () -> Unit,
    onNavigateToCustomers: () -> Unit = {},
    onNavigateToIntegrator: () -> Unit,
    onNavigateToHarnesses: () -> Unit,
    onNavigateToDiagnostics: (String) -> Unit,
    onNavigateToAiStudio: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onAuthClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSuperAdmin = !userEmail.isNullOrBlank() && AdminRepository.isSuperAdminEmail(userEmail)

    val customizations by ToolsCategoryManager.customizations.collectAsState()
    val categoryOrder by ToolsCategoryManager.categoryOrder.collectAsState()
    val jobsList by JobRecordRepository.jobs.collectAsState()
    val jobsCount = jobsList.size
    val customersCount = remember(jobsList) {
        jobsList.map { it.clientPhone.trim().ifBlank { it.clientLocation.trim().ifBlank { it.id } } }.distinct().size
    }

    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var lockedDialogCategoryTitle by remember { mutableStateOf<String?>(null) }
    var customInfoDialogCategory by remember { mutableStateOf<ToolGridItem?>(null) }

    // If user is not authenticated with an email, enforce login
    if (userEmail.isNullOrBlank() || userEmail == "guest") {
        Scaffold(
            modifier = modifier.fillMaxSize().testTag("tools_hub_screen_locked")
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        )
                        .padding(top = 20.dp, bottom = 26.dp, start = 20.dp, end = 20.dp)
                ) {
                    Column {
                        Text("مرحباً بك في مركز الأدوات ⚡", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("مساحة عمل خاصة بكل فني ومستخدم 🔒", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = "قسم خاص بالفنيين المسجلين 🔒",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "قائمة الأدوات وسجل الكروت ودليل العملاء وحساب الأرباح مخصصة ومستقلة تماماً لكل مستخدم ببريده الإلكتروني.\n\nسجل دخولك الآن للوصول إلى أدواتك وسجلك الخاص المشفر والمحمي بالكامل.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Button(
                            onClick = onAuthClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "تسجيل الدخول بالبريد الإلكتروني 🚀",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // Built-in Default Tool Items: Job Log + Customers Directory
    val defaultItems = remember(jobsCount) {
        listOf(
            ToolGridItem(
                id = "job_log",
                defaultTitle = "سجل صيانة الورشة والكروت",
                defaultSubtitle = if (jobsCount > 0) "$jobsCount كارتة مسجلة بالسجل" else "تسجيل الشغل والفواتير",
                defaultIcon = Icons.Default.Receipt,
                iconTint = Color(0xFF0284C7),
                circleBackground = Color(0xFFE0F2FE),
                onClick = onNavigateToJobLog
            )
        )
    }

    // Combine default items with any custom added categories
    val allItems = remember(defaultItems, customizations) {
        val list = defaultItems.toMutableList()
        val defaultIds = defaultItems.map { it.id }.toSet()
        val obsoleteIds = setOf("customers_dir", "receipt_gen", "receipt_generator", "diagnostics", "ai_studio", "motor_tester", "harness_map")

        customizations.forEach { (id, cust) ->
            if (!defaultIds.contains(id) && !obsoleteIds.contains(id)) {
                list.add(
                    ToolGridItem(
                        id = id,
                        defaultTitle = cust.customTitle.ifBlank { "أداة إضافية" },
                        defaultSubtitle = cust.customSubtitle.ifBlank { "قائمة مخصصة" },
                        defaultIcon = resolveToolIcon(cust.customIconName, Icons.Default.Build),
                        iconTint = Color(0xFF0D9488),
                        circleBackground = Color(0xFFCCFBF1),
                        isCustom = true,
                        onClick = {}
                    )
                )
            }
        }
        list
    }

    // Sort items by categoryOrder and filter hidden items for regular users
    val orderedItems = remember(allItems, categoryOrder, customizations, isAdmin) {
        val visibleItems = if (isAdmin) {
            allItems
        } else {
            allItems.filter { item ->
                val cust = customizations[item.id]
                cust?.isHidden != true
            }
        }
        if (categoryOrder.isEmpty()) visibleItems
        else {
            val orderMap = categoryOrder.withIndex().associate { it.value to it.index }
            visibleItems.sortedBy { orderMap[it.id] ?: 999 }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tools_hub_screen")
    ) { innerPadding ->
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToProfile() }
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_albaraa),
                                contentDescription = "شعار البراء إلكترونيك",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مرحباً بك،",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = if (isSuperAdmin) "م. البراء إلكترونيك (Super Admin)" else if (isAdmin) "م. البراء إلكترونيك (مشرف)" else (userEmail ?: "مهندس الصيانة"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = if (!userEmail.isNullOrBlank()) "أدوات وقوائم خاصة بحساب: $userEmail 🔒" else "أدوات وقوائم الصيانة الخاصة ⚡",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION TITLE: "قوائم الأدوات المتاحة" with vertical accent bar + Admin buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD97706)) // Orange accent bar
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "قوائم الأدوات المتاحة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                if (isAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add new tool list
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAddCategoryDialog = true }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "إضافة قائمة جديدة",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "إضافة قائمة ➕",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Reorder slices button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0D9488).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF0D9488)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showReorderDialog = true }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "ترتيب الشرايح",
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "ترتيب الشرايح ⇅",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF0D9488),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Edit titles and icons button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (allItems.isNotEmpty()) {
                                        editingCategoryId = allItems.first().id
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل القوائم",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "تعديل القوائم",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // GRID OF 2-COLUMN TOOL CARDS (Identical to DashboardHubScreen)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = orderedItems, key = { it.id }) { item ->
                    val cust = customizations[item.id]

                    val finalTitle = cust?.customTitle?.takeIf { it.isNotBlank() } ?: item.defaultTitle
                    val finalSubtitle = cust?.customSubtitle?.takeIf { it.isNotBlank() } ?: item.defaultSubtitle
                    val finalImageUrl = cust?.customImageUrl ?: ""
                    val finalIcon = resolveToolIcon(cust?.customIconName ?: "", item.defaultIcon)

                    val isCategoryLocked = cust?.isLocked == true
                    val isCategoryHidden = cust?.isHidden == true

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(172.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = Color(0x1F000000),
                                    ambientColor = Color(0x12000000)
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    width = 1.2.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    if (!isAdmin && isCategoryLocked) {
                                        lockedDialogCategoryTitle = finalTitle
                                    } else if (item.isCustom) {
                                        customInfoDialogCategory = item
                                    } else {
                                        item.onClick()
                                    }
                                }
                                .testTag("tool_card_${item.id}"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCategoryLocked && !isAdmin) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Circular Icon / Custom Image Container
                                Surface(
                                    shape = CircleShape,
                                    color = item.circleBackground,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (finalImageUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = finalImageUrl,
                                                contentDescription = finalTitle,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = finalIcon,
                                                contentDescription = finalTitle,
                                                tint = item.iconTint,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = finalTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = finalSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }

                        // Admin Controls: Lock Switch (Top Start), Hide/Show Eye (Center Start), and Edit Pencil (Top End)
                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ToolCategoryLockSwitch(
                                    isLocked = isCategoryLocked,
                                    onToggle = {
                                        ToolsCategoryManager.toggleCategoryLock(context, item.id)
                                    }
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = if (isCategoryHidden) Color(0xFFEF4444).copy(alpha = 0.85f) else Color(0xFF0F172A).copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, if (isCategoryHidden) Color(0xFFEF4444) else Color(0xFF94A3B8).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            ToolsCategoryManager.toggleCategoryVisibility(context, item.id)
                                            val state = if (!isCategoryHidden) "إخفاء" else "إظهار"
                                            Toast.makeText(context, "تم $state القائمة ${item.defaultTitle} عن المستخدمين بنجاح", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isCategoryHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isCategoryHidden) "إظهار للزوار" else "إخفاء عن الزوار",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            if (isCategoryHidden) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "مخفية 👁️‍🗨️",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { editingCategoryId = item.id }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "تعديل ${item.defaultTitle}",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else if (isCategoryLocked) {
                            // Guest indicator when category is locked
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .testTag("tool_locked_badge_${item.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "مغلق",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "مغلق",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Alert Dialog when a guest/user attempts to enter a locked category
    if (lockedDialogCategoryTitle != null) {
        AlertDialog(
            onDismissRequest = { lockedDialogCategoryTitle = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "الأداة مغلقة حالياً",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "عذراً، أداة ($lockedDialogCategoryTitle) مغلقة حالياً للصيانة والتطوير من قبل إدارة م. البراء إلكترونيك، وسيتم إتاحتها للجميع قريباً.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { lockedDialogCategoryTitle = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("فهمت ذلك", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dialog for viewing custom tool info
    if (customInfoDialogCategory != null) {
        val item = customInfoDialogCategory!!
        val cust = customizations[item.id]
        val title = cust?.customTitle?.ifBlank { item.defaultTitle } ?: item.defaultTitle
        val subtitle = cust?.customSubtitle?.ifBlank { item.defaultSubtitle } ?: item.defaultSubtitle

        AlertDialog(
            onDismissRequest = { customInfoDialogCategory = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = item.circleBackground,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(item.defaultIcon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(32.dp))
                    }
                }
            },
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = subtitle,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { customInfoDialogCategory = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Admin Add New Category Dialog
    if (isAdmin && showAddCategoryDialog) {
        AddToolCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onAdd = { newId, newTitle, newSubtitle, newIconName ->
                ToolsCategoryManager.addCategory(
                    context = context,
                    id = newId,
                    title = newTitle,
                    subtitle = newSubtitle,
                    iconName = newIconName
                ) { success ->
                    Toast.makeText(
                        context,
                        if (success) "تمت إضافة القائمة الجديدة بنجاح! 🚀" else "تم الحفظ محلياً",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showAddCategoryDialog = false
            }
        )
    }

    // Admin Edit Category Dialog
    if (isAdmin && editingCategoryId != null) {
        val selectedCat = allItems.find { it.id == editingCategoryId } ?: allItems.first()
        val currentCust = customizations[selectedCat.id] ?: HubCategoryCustomization(id = selectedCat.id)

        EditToolCategoryDialog(
            categories = allItems,
            currentCategory = selectedCat,
            currentCustomization = currentCust,
            onSelectCategory = { newId -> editingCategoryId = newId },
            onDismiss = { editingCategoryId = null },
            onSave = { updatedTitle, updatedSubtitle, updatedImageUrl, updatedIconName, updatedIsHidden ->
                ToolsCategoryManager.updateCategory(
                    context = context,
                    categoryId = selectedCat.id,
                    title = updatedTitle,
                    subtitle = updatedSubtitle,
                    imageUrl = updatedImageUrl,
                    iconName = updatedIconName
                )
                ToolsCategoryManager.setCategoryVisibility(context, selectedCat.id, updatedIsHidden) { success ->
                    Toast.makeText(
                        context,
                        if (success) "تم حفظ وتحديث قائمة الأدوات بنجاح!" else "تم الحفظ محلياً",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                editingCategoryId = null
            },
            onDelete = if (selectedCat.isCustom) {
                {
                    ToolsCategoryManager.deleteCategory(context, selectedCat.id) {
                        Toast.makeText(context, "تم حذف القائمة بنجاح", Toast.LENGTH_SHORT).show()
                    }
                    editingCategoryId = null
                }
            } else null,
            onReset = {
                ToolsCategoryManager.resetCategory(context, selectedCat.id) {
                    Toast.makeText(context, "تمت استعادة الإعدادات الافتراضية للقائمة", Toast.LENGTH_SHORT).show()
                }
                editingCategoryId = null
            }
        )
    }

    // Admin Reorder Categories Dialog
    if (isAdmin && showReorderDialog) {
        val currentOrderList = remember(allItems, categoryOrder) {
            if (categoryOrder.isEmpty()) allItems
            else {
                val orderMap = categoryOrder.withIndex().associate { it.value to it.index }
                allItems.sortedBy { orderMap[it.id] ?: 999 }
            }
        }
        ReorderToolCategoriesDialog(
            categories = currentOrderList,
            customizations = customizations,
            onMove = { catId, delta ->
                ToolsCategoryManager.moveCategory(context, catId, delta, currentOrderList.map { it.id }) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حفظ الترتيب الجديد في السيرفر", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = {
                ToolsCategoryManager.reorderCategories(context, defaultItems.map { it.id }) {
                    Toast.makeText(context, "تمت استعادة الترتيب الافتراضي", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showReorderDialog = false }
        )
    }
}

@Composable
fun ToolCategoryLockSwitch(
    isLocked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isLocked) Color(0xFFEF4444) else Color(0xFF22C55E),
        shadowElevation = 3.dp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            if (!isLocked) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "مفتوح",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                Text(
                    text = "مفتوح",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            } else {
                Text(
                    text = "مغلق",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "مغلق",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddToolCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (id: String, title: String, subtitle: String, iconName: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("build") }

    val iconChoices = listOf(
        "build" to Icons.Default.Build,
        "receipt" to Icons.Default.Receipt,
        "warning" to Icons.Default.Warning,
        "electric" to Icons.Default.ElectricBolt,
        "memory" to Icons.Default.Memory,
        "cable" to Icons.Default.Cable,
        "ai" to Icons.Default.AutoAwesome,
        "grid" to Icons.Default.GridView
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF10B981))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة قائمة / أداة جديدة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "أدخل اسم ووصف الأداة لتظهر فوراً لجميع المستخدمين في صفحة الأدوات:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم القائمة الرئيسية") },
                    placeholder = { Text("مثال: حاسبة المقاومات والمكثفات") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("الوصف الفرعي للقائمة") },
                    placeholder = { Text("مثال: حساب قيم مكونات الباور بدقة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Text(
                    text = "اختر الأيقونة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconChoices) { (iconKey, iconVec) ->
                        Surface(
                            shape = CircleShape,
                            color = if (selectedIconName == iconKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.5.dp,
                                if (selectedIconName == iconKey) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { selectedIconName = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = iconKey,
                                    tint = if (selectedIconName == iconKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val genId = "custom_tool_" + System.currentTimeMillis()
                        onAdd(genId, title, subtitle, selectedIconName)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إضافة وحفظ ➕", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun EditToolCategoryDialog(
    categories: List<ToolGridItem>,
    currentCategory: ToolGridItem,
    currentCustomization: HubCategoryCustomization,
    onSelectCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, imageUrl: String, iconName: String, isHidden: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    onReset: () -> Unit
) {
    var title by remember(currentCategory.id, currentCustomization) {
        mutableStateOf(currentCustomization.customTitle.ifBlank { currentCategory.defaultTitle })
    }
    var subtitle by remember(currentCategory.id, currentCustomization) {
        mutableStateOf(currentCustomization.customSubtitle.ifBlank { currentCategory.defaultSubtitle })
    }
    var imageUrl by remember(currentCategory.id, currentCustomization) {
        mutableStateOf(currentCustomization.customImageUrl)
    }
    var selectedIconName by remember(currentCategory.id, currentCustomization) {
        mutableStateOf(currentCustomization.customIconName)
    }
    var isHidden by remember(currentCategory.id, currentCustomization) {
        mutableStateOf(currentCustomization.isHidden)
    }

    val iconOptions = listOf(
        "receipt" to Icons.Default.Receipt,
        "share" to Icons.Default.Share,
        "warning" to Icons.Default.Warning,
        "ai" to Icons.Default.AutoAwesome,
        "memory" to Icons.Default.Memory,
        "cable" to Icons.Default.Cable,
        "build" to Icons.Default.Build,
        "electric" to Icons.Default.ElectricBolt,
        "grid" to Icons.Default.GridView
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تعديل اسم وصورة قائمة الأدوات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "اختر القائمة المراد تعديلها:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = cat.id == currentCategory.id,
                            onClick = { onSelectCategory(cat.id) },
                            label = { Text(cat.defaultTitle, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم / عنوان القائمة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("الوصف الفرعي للقائمة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("رابط صورة مخصصة (اختياري)") },
                    placeholder = { Text("https://... أو مسار الصورة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Text(
                    text = "أو اختر أيقونة:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(iconOptions) { (iconKey, iconVec) ->
                        val isSelected = selectedIconName == iconKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { selectedIconName = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = iconKey,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "إخفاء القائمة عن المستخدمين",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "تظهر للإدارة فقط وتختفي من واجهة الزوار",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isHidden) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { isHidden = !isHidden }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isHidden) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف هذه القائمة المخصصة نهائياً", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, subtitle, imageUrl, selectedIconName, isHidden)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ التعديلات", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("استعادة الافتراضي", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun ReorderToolCategoriesDialog(
    categories: List<ToolGridItem>,
    customizations: Map<String, HubCategoryCustomization>,
    onMove: (id: String, delta: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0D9488).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("ترتيب شرائح وقوائم الأدوات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("غيّر مواضع القوائم بالتقديم والتأخير", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categories, key = { _, item -> item.id }) { index, item ->
                        val cust = customizations[item.id]
                        val title = cust?.customTitle?.takeIf { it.isNotBlank() } ?: item.defaultTitle
                        val icon = resolveToolIcon(cust?.customIconName ?: "", item.defaultIcon)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color(0xFF0284C7),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = item.iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onMove(item.id, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "تقديم للأعلى",
                                            tint = if (index > 0) Color(0xFF0284C7) else Color.Gray.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onMove(item.id, 1) },
                                        enabled = index < categories.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "تأخير للأسفل",
                                            tint = if (index < categories.size - 1) Color(0xFF0284C7) else Color.Gray.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
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
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تم وتأكيد الترتيب", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("إعادة الترتيب الافتراضي", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
