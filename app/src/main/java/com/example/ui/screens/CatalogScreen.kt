package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Motor
import com.example.ui.components.MotorCardSkeleton
import com.example.ui.components.parseWireColorToComposeColor
import com.example.ui.viewmodel.MotorsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    motorsState: MotorsUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedBrand: String,
    onBrandSelect: (String) -> Unit,
    selectedMotorType: String,
    onMotorTypeSelect: (String) -> Unit,
    onMotorClick: (Motor) -> Unit,
    onAddNewMotorClick: () -> Unit,
    onAiStudioClick: () -> Unit,
    onSignOutClick: () -> Unit,
    userEmail: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean = false,
    isGuest: Boolean = false,
    onManageAdminsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allMotors = (motorsState as? MotorsUiState.Success)?.motors ?: emptyList()

    val dynamicBrands = remember(allMotors) {
        val brands = allMotors.map { it.brand.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (brands.isNotEmpty()) listOf("الكل") + brands else emptyList()
    }

    val dynamicMotorTypes = remember(allMotors) {
        val types = allMotors.map { it.motorType.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (types.isNotEmpty()) listOf("الكل") + types else emptyList()
    }

    LaunchedEffect(dynamicBrands) {
        if (selectedBrand != "الكل" && selectedBrand !in dynamicBrands) {
            onBrandSelect("الكل")
        }
    }

    LaunchedEffect(dynamicMotorTypes) {
        if (selectedMotorType != "الكل" && selectedMotorType !in dynamicMotorTypes) {
            onMotorTypeSelect("الكل")
        }
    }

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
                                text = if (isSuperAdmin) "المشرف العام (Super Admin)" else if (isAdmin) "وضع المهندس المشرف (Admin)" else if (isGuest) "وضع الزائر (قراءة فقط)" else (userEmail ?: "دليل الفنيين"),
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

                    // AI Studio shortcut (Admins Only)
                    if (isAdmin) {
                        IconButton(
                            onClick = onAiStudioClick,
                            modifier = Modifier.testTag("ai_studio_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "استوديو الذكاء الاصطناعي للمخططات",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
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
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة ماتور")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إضافة ماتور", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isGuest) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "وضع الزائر: تصفح وقراءة فقط للمخططات وجداول الأطراف",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        TextButton(
                            onClick = onSignOutClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "تسجيل الدخول",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Global Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("global_search_input"),
                placeholder = {
                    Text("ابحث باسم الماتور، الموديل، لون السلك أو الطرف...")
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            // Dynamic Brand Filter Chips (Only shown if motors with brands exist in database)
            if (dynamicBrands.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dynamicBrands) { brand ->
                        val isSelected = selectedBrand == brand
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected && brand != "الكل") {
                                    onBrandSelect("الكل")
                                } else {
                                    onBrandSelect(brand)
                                }
                            },
                            label = { Text(brand) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("brand_filter_$brand")
                        )
                    }
                }
            }

            // Dynamic Motor Type / Category Quick Filters (Only shown if motors with types exist in database)
            if (dynamicMotorTypes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dynamicMotorTypes) { type ->
                        val isSelected = selectedMotorType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected && type != "الكل") {
                                    onMotorTypeSelect("الكل")
                                } else {
                                    onMotorTypeSelect(type)
                                }
                            },
                            label = { Text(if (type == "الكل") "كل الأنواع" else "نوع: $type") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("type_filter_$type")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Motor Catalog List
            when (motorsState) {
                is MotorsUiState.Loading -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(5) {
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
                    val filteredMotors = motorsState.motors.filter { motor ->
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            motor.name.contains(searchQuery, ignoreCase = true) ||
                            motor.brand.contains(searchQuery, ignoreCase = true) ||
                            motor.model.contains(searchQuery, ignoreCase = true) ||
                            motor.motorType.contains(searchQuery, ignoreCase = true) ||
                            motor.notes.contains(searchQuery, ignoreCase = true) ||
                            motor.pinouts.any { pin ->
                                pin.name.contains(searchQuery, ignoreCase = true) ||
                                pin.wireColor.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        val matchesBrand = if (selectedBrand == "الكل" || selectedBrand.isBlank()) true else {
                            motor.brand.equals(selectedBrand, ignoreCase = true) ||
                            motor.brand.contains(selectedBrand, ignoreCase = true)
                        }

                        val matchesType = if (selectedMotorType == "الكل" || selectedMotorType.isBlank()) true else {
                            motor.motorType.equals(selectedMotorType, ignoreCase = true) ||
                            motor.motorType.contains(selectedMotorType, ignoreCase = true)
                        }

                        matchesSearch && matchesBrand && matchesType
                    }

                    if (filteredMotors.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "لا توجد مواتير مطابقة للبحث",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "يمكنك إضافة ماتور جديد أو مسح فلاتر البحث",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredMotors, key = { it.id }) { motor ->
                                MotorCardItem(
                                    motor = motor,
                                    onClick = { onMotorClick(motor) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MotorCardItem(
    motor: Motor,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "card_scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .testTag("motor_card_${motor.id}")
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Motor Brand & Model Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = motor.brand.ifBlank { "ماتور" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (motor.model.isNotBlank()) {
                        Text(
                            text = motor.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pins count badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${motor.pinouts.size} أطراف",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Full Motor Name
            Text(
                text = motor.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Motor Type
            Text(
                text = "النوع: ${motor.motorType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Wire Colors Preview Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ألوان الأسلاك:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    motor.pinouts.take(9).forEach { pin ->
                        val color = parseWireColorToComposeColor(pin.wireColor, pin.colorHex)
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(0.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // View Diagram Button
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_diagram_button_${motor.id}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "عرض المخطط والأطراف",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
