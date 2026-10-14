package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Motor
import com.example.ui.components.MotorCard
import com.example.ui.components.MotorCardSkeleton
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
    isSuperAdmin: Boolean,
    isGuest: Boolean,
    onManageAdminsClick: () -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Brands Filter Chips
            if (dynamicBrands.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dynamicBrands) { brand ->
                        FilterChip(
                            selected = selectedBrand == brand,
                            onClick = { onBrandSelect(brand) },
                            label = { Text(brand) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Motor Types Filter Chips
            if (dynamicMotorTypes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dynamicMotorTypes) { type ->
                        FilterChip(
                            selected = selectedMotorType == type,
                            onClick = { onMotorTypeSelect(type) },
                            label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline)
                                )
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
                            items(
                                items = filteredMotors,
                                key = { it.id }
                            ) { motor ->
                                MotorCard(
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
