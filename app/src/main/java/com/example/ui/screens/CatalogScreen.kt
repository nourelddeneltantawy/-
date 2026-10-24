package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Motor
import com.example.data.model.MotorCategory
import com.example.ui.components.MotorCard
import com.example.ui.components.MotorCardSkeleton
import com.example.ui.viewmodel.MotorsUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    motorsState: MotorsUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<MotorCategory>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onAddNewCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    favoriteMotorIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onMotorClick: (Motor) -> Unit,
    onAddNewMotorClick: () -> Unit,
    onAiStudioClick: () -> Unit,
    onSignOutClick: () -> Unit,
    userEmail: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    isGuest: Boolean,
    onManageAdminsClick: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onEditMotorClick: (Motor) -> Unit = {},
    onDeleteMotorClick: (Motor) -> Unit = {},
    onMoveMotor: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var motorToDelete by remember { mutableStateOf<Motor?>(null) }
    var categoryToDelete by remember { mutableStateOf<MotorCategory?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val allMotors = (motorsState as? MotorsUiState.Success)?.motors ?: emptyList()

    Scaffold(
        modifier = modifier.testTag("catalog_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "دليل مواتير الغسالات",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = if (isSuperAdmin) "المشرف العام (Super Admin)" else if (isAdmin) "وضع التحكم والمشرف (Admin)" else if (isGuest) "وضع الزائر" else (userEmail ?: "دليل الفنيين"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isAdmin) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Manage Admins shortcut (Super Admin Only)
                    if (isSuperAdmin) {
                        IconButton(
                            onClick = onManageAdminsClick,
                            modifier = Modifier.testTag("manage_admins_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "إدارة المشرفين وصلاحيات التطبيق",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // AI Studio shortcut (Admins only)
                    if (isAdmin) {
                        IconButton(
                            onClick = onAiStudioClick,
                            modifier = Modifier.testTag("ai_studio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "استوديو الذكاء الاصطناعي للمخططات",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Check for Updates action
                    IconButton(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.testTag("check_updates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "التحقق من وجود تحديثات للتطبيق",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Sign Out or Sign In
                    IconButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = if (isGuest) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                            contentDescription = if (isGuest) "تسجيل الدخول" else "تسجيل الخروج",
                            tint = if (isGuest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = onAddNewMotorClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_motor_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة ماتور جديد"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .testTag("search_motors_input")
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("ابحث باسم الماتور، الموديل، لون السلك أو الوظيفة...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح البحث")
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

            // User's Custom Categories / Lists Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. "الكل" (All) Chip
                item {
                    FilterChip(
                        selected = selectedCategory == "الكل",
                        onClick = { onCategorySelect("الكل") },
                        label = { Text("الكل (${allMotors.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // 2. "المفضلة" (Favorites) Chip
                item {
                    val favCount = allMotors.count { favoriteMotorIds.contains(it.id) }
                    FilterChip(
                        selected = selectedCategory == "المفضلة",
                        onClick = { onCategorySelect("المفضلة") },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (selectedCategory == "المفضلة") Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("المفضلة ($favCount)")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFFEF4444)
                        )
                    )
                }

                // 3. User-created Custom Lists / Categories
                items(categories, key = { it.id }) { cat ->
                    val motorCount = allMotors.count { it.category.equals(cat.name, ignoreCase = true) }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedCategory == cat.name) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { onCategorySelect(cat.name) },
                                onLongClick = {
                                    if (isAdmin) {
                                        categoryToDelete = cat
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (selectedCategory == cat.name) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${cat.name} ($motorCount)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedCategory == cat.name) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedCategory == cat.name) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // 4. "+ إضافة قائمة جديدة" Button (Admins/Supervisors only)
                if (isAdmin) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddCategoryDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "إضافة قائمة (مشرف)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when (motorsState) {
                is MotorsUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(4) {
                            MotorCardSkeleton()
                        }
                    }
                }
                is MotorsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = motorsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is MotorsUiState.Success -> {
                    // Filter according to category, search, and favorites
                    val filteredMotors = motorsState.motors.filter { motor ->
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            motor.name.contains(searchQuery, ignoreCase = true) ||
                            motor.brand.contains(searchQuery, ignoreCase = true) ||
                            motor.model.contains(searchQuery, ignoreCase = true) ||
                            motor.category.contains(searchQuery, ignoreCase = true) ||
                            motor.motorType.contains(searchQuery, ignoreCase = true) ||
                            motor.notes.contains(searchQuery, ignoreCase = true) ||
                            motor.pinouts.any { pin ->
                                pin.name.contains(searchQuery, ignoreCase = true) ||
                                pin.wireColor.contains(searchQuery, ignoreCase = true) ||
                                pin.functionDesc.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        val matchesCategory = when (selectedCategory) {
                            "الكل" -> true
                            "المفضلة" -> favoriteMotorIds.contains(motor.id)
                            else -> motor.category.equals(selectedCategory, ignoreCase = true)
                        }

                        matchesSearch && matchesCategory
                    }

                    if (filteredMotors.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (selectedCategory == "المفضلة") {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444).copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "قائمة المفضلة فارغة حالياً",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "اضغط على أيقونة القلب على أي موتور لإضافته إلى قائمة المفضلة والرجوع إليه بسرعة.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                        textAlign = TextAlign.Center
                                    )
                                } else if (allMotors.isEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "الدليل فارغ تماماً",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "أنت الآن تتحكم بالدليل من الصفر! أنشئ قوائمك المخصصة وأضف أول ماتور مع أطرافه طرفاً طرفاً.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                        textAlign = TextAlign.Center
                                    )
                                    if (isAdmin) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = onAddNewMotorClick) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("إضافة أول ماتور الآن")
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (selectedCategory != "الكل") "لا توجد مواتير في قائمة \"$selectedCategory\"" else "لا توجد مواتير مطابقة للبحث",
                                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = filteredMotors,
                                key = { index, motor -> if (motor.id.isNotBlank()) motor.id else "motor_idx_$index" }
                            ) { index, motor ->
                                MotorCard(
                                    motor = motor,
                                    onClick = { onMotorClick(motor) },
                                    onEditClick = if (isAdmin) { { onEditMotorClick(motor) } } else null,
                                    onDeleteClick = if (isAdmin) { { motorToDelete = motor } } else null,
                                    onMoveUp = if (isAdmin && index > 0) { { onMoveMotor(motor.id, true) } } else null,
                                    onMoveDown = if (isAdmin && index < filteredMotors.size - 1) { { onMoveMotor(motor.id, false) } } else null,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < filteredMotors.size - 1,
                                    isAdmin = isAdmin,
                                    isFavorite = favoriteMotorIds.contains(motor.id),
                                    onToggleFavorite = { onToggleFavorite(motor.id) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Category Dialog (Admin only)
    if (isAdmin && showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("إضافة قائمة تشغيل جديدة (خاص بالمشرفين)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("أدخل اسم القائمة أو التصنيف الجديد. سيتم حفظه في السحابة ومزامنته فورياً مع جميع مستخدمي التطبيق:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("اسم القائمة (مثال: مواتير يونيفرسال، دايو...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotBlank()) {
                            onAddNewCategory(trimmed)
                            newCategoryName = ""
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("إضافة ونشر للجميع")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Category Confirmation Dialog (Admin only)
    if (isAdmin && categoryToDelete != null) {
        val cat = categoryToDelete!!
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = {
                Text(
                    text = "حذف القائمة (خاص بالمشرفين)",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف قائمة \"${cat.name}\"؟ سيتم حذف هذه القائمة وتحديثها فوراً لدى جميع المستخدمين. (لن يتم حذف المواتير المسجلة بداخلها).",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(cat.id)
                        categoryToDelete = null
                    }
                ) {
                    Text(
                        text = "حذف القائمة من السحابة",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("إلغاء")
                }
        )
    }

    // Delete Motor Confirmation Dialog
    if (motorToDelete != null) {
        val motor = motorToDelete!!
        AlertDialog(
            onDismissRequest = { motorToDelete = null },
            title = {
                Text(
                    text = "حذف الماتور نهائياً",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف \"${motor.name}\" (${motor.brand})؟ سيتم حذف جميع الأطراف والألوان الخاصة به نهائياً.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMotorClick(motor)
                        motorToDelete = null
                    }
                ) {
                    Text(
                        text = "حذف نهائي",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { motorToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
