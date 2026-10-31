package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.data.HubCategoryManager
import com.example.data.model.HubCategoryCustomization
import com.example.ui.components.ThemeSettingsDialog

data class HubCategoryItem(
    val id: String,
    val defaultTitle: String,
    val defaultSubtitle: String,
    val defaultIcon: ImageVector,
    val iconTint: Color,
    val circleBackground: Color,
    val onClick: () -> Unit
)

fun resolveCategoryIcon(iconName: String, fallback: ImageVector): ImageVector {
    return when (iconName.lowercase().trim()) {
        "memory", "موتور", "مواتير" -> Icons.Default.Memory
        "cable", "ضفيرة", "ضفائر" -> Icons.Default.Cable
        "lock", "لوك", "حساس" -> Icons.Default.Lock
        "hub", "تجميع", "مخطط" -> Icons.Default.Hub
        "wash", "غسالة", "أوتوماتيك" -> Icons.Default.LocalLaundryService
        "cleaning", "فوق أوتوماتيك" -> Icons.Default.CleaningServices
        "settings", "تيست مود" -> Icons.Default.Settings
        "build", "رولمان", "صيانة" -> Icons.Default.Build
        "contact", "تواصل", "مركز" -> Icons.Default.ContactPhone
        "electric", "كهرباء" -> Icons.Default.ElectricBolt
        "manufacturing", "صناعي" -> Icons.Default.PrecisionManufacturing
        "grid", "قائمة" -> Icons.Default.GridView
        else -> fallback
    }
}

@Composable
fun DashboardHubScreen(
    userEmail: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    isGuest: Boolean,
    motorsCount: Int,
    harnessesCount: Int,
    partsCount: Int,
    onNavigateToMotors: () -> Unit,
    onNavigateToHarnesses: () -> Unit,
    onNavigateToParts: () -> Unit,
    onNavigateToIntegrator: () -> Unit,
    onNavigateToDiagnostics: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onPublishUpdate: () -> Unit = {},
    onManageAdmins: () -> Unit,
    onAuthClick: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var lockedDialogCategoryTitle by remember { mutableStateOf<String?>(null) }

    val customizations by HubCategoryManager.customizations.collectAsState()
    val categoryOrder by HubCategoryManager.categoryOrder.collectAsState()

    val rawItems = listOf(
        HubCategoryItem(
            id = "profile",
            defaultTitle = "مركز البراء والاتصال",
            defaultSubtitle = "واتساب • هاتف • العنوان",
            defaultIcon = Icons.Default.ContactPhone,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            onClick = onNavigateToProfile
        ),
        HubCategoryItem(
            id = "motors",
            defaultTitle = "أطراف وتوصيلات المواتير",
            defaultSubtitle = "$motorsCount ماتور مسجل",
            defaultIcon = Icons.Default.Memory,
            iconTint = Color(0xFFEF4444),
            circleBackground = Color(0xFFFFE4E6),
            onClick = onNavigateToMotors
        ),
        HubCategoryItem(
            id = "harnesses",
            defaultTitle = "خريطة ضفائر الغسالة",
            defaultSubtitle = "$harnessesCount ضفائر كارتة",
            defaultIcon = Icons.Default.Cable,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            onClick = onNavigateToHarnesses
        ),
        HubCategoryItem(
            id = "parts",
            defaultTitle = "لوكات الباب وليفلات الماء",
            defaultSubtitle = "$partsCount قطع وحساسات",
            defaultIcon = Icons.Default.Lock,
            iconTint = Color(0xFFD97706),
            circleBackground = Color(0xFFFEF3C7),
            onClick = onNavigateToParts
        ),
        HubCategoryItem(
            id = "integrator",
            defaultTitle = "مخطط التجميع الشامل",
            defaultSubtitle = "دمج الضفيرة + الماتور + اللوك",
            defaultIcon = Icons.Default.Hub,
            iconTint = Color(0xFF8B5CF6),
            circleBackground = Color(0xFFEDE9FE),
            onClick = onNavigateToIntegrator
        ),
        HubCategoryItem(
            id = "auto_faults",
            defaultTitle = "أعطال الأوتوماتيك",
            defaultSubtitle = "أكواد الأعطال والحلول",
            defaultIcon = Icons.Default.LocalLaundryService,
            iconTint = Color(0xFF0D9488),
            circleBackground = Color(0xFFCCFBF1),
            onClick = { onNavigateToDiagnostics("أعطال الأوتوماتيك") }
        ),
        HubCategoryItem(
            id = "top_load_faults",
            defaultTitle = "الفوق أوتوماتيك",
            defaultSubtitle = "الأعطال ومفاتيح الأمان",
            defaultIcon = Icons.Default.CleaningServices,
            iconTint = Color(0xFFEA580C),
            circleBackground = Color(0xFFFFEDD5),
            onClick = { onNavigateToDiagnostics("الفوق أوتوماتيك") }
        ),
        HubCategoryItem(
            id = "test_mode",
            defaultTitle = "تيست مود (Test Mode)",
            defaultSubtitle = "خطوات فحص البرامج",
            defaultIcon = Icons.Default.Settings,
            iconTint = Color(0xFF7C3AED),
            circleBackground = Color(0xFFF3E8FF),
            onClick = { onNavigateToDiagnostics("تيست مود") }
        ),
        HubCategoryItem(
            id = "bearings",
            defaultTitle = "رولمان بلي وأولسيه",
            defaultSubtitle = "جدول المقاسات والأبعاد",
            defaultIcon = Icons.Default.Build,
            iconTint = Color(0xFF059669),
            circleBackground = Color(0xFFD1FAE5),
            onClick = { onNavigateToDiagnostics("رولمان بلي وأولسيه") }
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dashboard_hub_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // TOP HEADER: Curved gradient banner matching the user's screenshot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0284C7),
                                Color(0xFF0369A1)
                            )
                        )
                    )
                    .padding(top = innerPadding.calculateTopPadding() + 8.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
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
                                text = if (isSuperAdmin) "م. البراء إلكترونيك (Super Admin)" else if (isAdmin) "م. البراء إلكترونيك (مشرف)" else if (isGuest) "الزائر الكريم" else (userEmail ?: "مهندس الصيانة"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Check for updates
                        IconButton(onClick = onCheckForUpdates) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "التحقق من وجود تحديثات",
                                tint = Color.White
                            )
                        }

                        // App Theme Settings (مظهر التطبيق)
                        IconButton(onClick = { showThemeDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "مظهر التطبيق والسمة",
                                tint = Color.White
                            )
                        }

                        // Admin: Publish new update
                        if (isAdmin) {
                            IconButton(onClick = onPublishUpdate) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "نشر تحديث جديد للتطبيق",
                                    tint = Color.White
                                )
                            }
                        }

                        // Manage Admins for Super Admin
                        if (isSuperAdmin) {
                            IconButton(onClick = onManageAdmins) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "إدارة المشرفين",
                                    tint = Color.White
                                )
                            }
                        }

                        // Auth toggle
                        IconButton(onClick = onAuthClick) {
                            Icon(
                                imageVector = if (isGuest) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                                contentDescription = if (isGuest) "تسجيل الدخول" else "تسجيل الخروج",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION TITLE: "أقسام الدليل المتاحة" with vertical accent bar + Admin Edit affordance
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
                        text = "أقسام الدليل المتاحة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                val orderedItems = remember(rawItems, categoryOrder) {
                    if (categoryOrder.isEmpty()) rawItems
                    else {
                        val orderMap = categoryOrder.withIndex().associate { it.value to it.index }
                        rawItems.sortedBy { orderMap[it.id] ?: 999 }
                    }
                }

                if (isAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reorder slices button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0D9488).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D9488)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showReorderDialog = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
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
                                        fontWeight = FontWeight.Bold
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
                                .clickable { editingCategoryId = rawItems.first().id }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل أسماء وصور القوائم",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "تعديل القوائم",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val orderedItems = remember(rawItems, categoryOrder) {
                if (categoryOrder.isEmpty()) rawItems
                else {
                    val orderMap = categoryOrder.withIndex().associate { it.value to it.index }
                    rawItems.sortedBy { orderMap[it.id] ?: 999 }
                }
            }

            // GRID OF SECTION CARDS
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(orderedItems.size, key = { orderedItems[it].id }) { index ->
                    val item = orderedItems[index]
                    val cust = customizations[item.id]

                    val finalTitle = cust?.customTitle?.takeIf { it.isNotBlank() } ?: item.defaultTitle
                    val finalSubtitle = cust?.customSubtitle?.takeIf { it.isNotBlank() } ?: item.defaultSubtitle
                    val finalImageUrl = cust?.customImageUrl ?: ""
                    val finalIcon = resolveCategoryIcon(cust?.customIconName ?: "", item.defaultIcon)

                    val isCategoryLocked = cust?.isLocked == true

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(172.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    if (!isAdmin && isCategoryLocked) {
                                        lockedDialogCategoryTitle = finalTitle
                                    } else {
                                        item.onClick()
                                    }
                                }
                                .testTag("hub_card_${item.id}"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCategoryLocked && !isAdmin) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                        // Admin Controls: Lock Switch (Top Start) & Edit Pencil (Top End)
                        if (isAdmin) {
                            CategoryLockSwitch(
                                isLocked = isCategoryLocked,
                                onToggle = {
                                    HubCategoryManager.toggleCategoryLock(context, item.id)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            )

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

    // Alert Dialog when a guest attempts to enter a locked category
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
                    text = "القسم مغلق حالياً",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "عذراً، قسم ($lockedDialogCategoryTitle) مغلق حالياً للصيانة والبرمجة والتطوير من قبل إدارة م. البراء إلكترونيك، وسيتم إتاحته للجميع فور الانتهاء.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { lockedDialogCategoryTitle = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("فهمت ذلك", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showThemeDialog) {
        ThemeSettingsDialog(onDismiss = { showThemeDialog = false })
    }

    // Admin Category Customizer Dialog
    if (isAdmin && editingCategoryId != null) {
        val selectedCat = rawItems.find { it.id == editingCategoryId } ?: rawItems.first()
        val currentCust = customizations[selectedCat.id] ?: HubCategoryCustomization(id = selectedCat.id)

        EditCategoryDialog(
            categories = rawItems,
            currentCategory = selectedCat,
            currentCustomization = currentCust,
            onSelectCategory = { newId -> editingCategoryId = newId },
            onDismiss = { editingCategoryId = null },
            onSave = { updatedTitle, updatedSubtitle, updatedImageUrl, updatedIconName ->
                HubCategoryManager.updateCategory(
                    context = context,
                    id = selectedCat.id,
                    customTitle = updatedTitle,
                    customSubtitle = updatedSubtitle,
                    customImageUrl = updatedImageUrl,
                    customIconName = updatedIconName
                ) { success ->
                    Toast.makeText(
                        context,
                        if (success) "تم حفظ وتحديث القسم بنجاح في السيرفر!" else "تم الحفظ محلياً",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                editingCategoryId = null
            },
            onReset = {
                HubCategoryManager.resetCategory(context, selectedCat.id) {
                    Toast.makeText(context, "تمت استعادة الإعدادات الافتراضية للقسم", Toast.LENGTH_SHORT).show()
                }
                editingCategoryId = null
            }
        )
    }

    // Admin Reorder Categories Dialog
    if (isAdmin && showReorderDialog) {
        val currentOrderList = remember(rawItems, categoryOrder) {
            if (categoryOrder.isEmpty()) rawItems
            else {
                val orderMap = categoryOrder.withIndex().associate { it.value to it.index }
                rawItems.sortedBy { orderMap[it.id] ?: 999 }
            }
        }
        ReorderCategoriesDialog(
            categories = currentOrderList,
            customizations = customizations,
            onMove = { catId, delta ->
                HubCategoryManager.moveCategory(context, catId, delta, currentOrderList.map { it.id }) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حفظ الترتيب الجديد في السيرفر", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReset = {
                HubCategoryManager.reorderCategories(context, rawItems.map { it.id }) {
                    Toast.makeText(context, "تمت استعادة الترتيب الافتراضي", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showReorderDialog = false }
        )
    }
}

@Composable
fun ReorderCategoriesDialog(
    categories: List<HubCategoryItem>,
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
                    Text("ترتيب أقسام وقوائم الدليل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("غيّر مواضع الشرايح كما تفضل بالتقديم والتأخير", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
                        val icon = resolveCategoryIcon(cust?.customIconName ?: "", item.defaultIcon)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
                                    // Order Badge
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

                                // Up & Down Reorder Buttons
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
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

@Composable
fun EditCategoryDialog(
    categories: List<HubCategoryItem>,
    currentCategory: HubCategoryItem,
    currentCustomization: HubCategoryCustomization,
    onSelectCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, imageUrl: String, iconName: String) -> Unit,
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

    val iconOptions = listOf(
        "موتور" to Icons.Default.Memory,
        "ضفيرة" to Icons.Default.Cable,
        "لوك" to Icons.Default.Lock,
        "تجميع" to Icons.Default.Hub,
        "غسالة" to Icons.Default.LocalLaundryService,
        "فوق أوتوماتيك" to Icons.Default.CleaningServices,
        "تيست مود" to Icons.Default.Settings,
        "رولمان بلي" to Icons.Default.Build,
        "مركز" to Icons.Default.ContactPhone,
        "كهرباء" to Icons.Default.ElectricBolt,
        "صناعي" to Icons.Default.PrecisionManufacturing
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تعديل اسم وصورة القسم",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
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
                    text = "اختر القسم المراد تعديله:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Horizontal chips to quickly switch categories
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
                    label = { Text("اسم / عنوان القسم الرئيسي") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("الوصف الفرعي للقسم") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("رابط صورة مخصصة للقسم (URL مباشر)") },
                    placeholder = { Text("https://example.com/icon.png") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Image / Icon Live Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = currentCategory.circleBackground,
                            modifier = Modifier.size(54.dp).clip(CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = imageUrl.trim(),
                                        contentDescription = "معاينة",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = resolveCategoryIcon(selectedIconName, currentCategory.defaultIcon),
                                        contentDescription = "معاينة الأيقونة",
                                        tint = currentCategory.iconTint,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Column {
                            Text("معاينة شكل القسم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (imageUrl.isNotBlank()) "يتم استخدام صورة من الرابط" else "يتم استخدام الأيقونة المختارة",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Or Pick from Common Icons
                Text(
                    text = "أو اختر أيقونة بديلة (إذا لم تضع رابط صورة):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(iconOptions) { (name, icon) ->
                        val isSelected = selectedIconName == name
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedIconName = name
                                    imageUrl = "" // Clear image URL if picking an icon
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
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
                    onSave(title, subtitle, imageUrl, selectedIconName)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استعادة الافتراضي")
                }
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Custom toggle switch for Admins to lock/unlock sections.
 * Matches design from user screenshot: Green "مفتوح" with 🔓 vs Red "مغلق" with 🔒.
 */
@Composable
fun CategoryLockSwitch(
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

