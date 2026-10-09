package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.example.ui.components.WireColorBadge
import com.example.ui.components.parseWireColorToComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMotorScreen(
    initialMotor: Motor?,
    currentUserId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: (Motor) -> Unit,
    onDeleteSuccess: () -> Unit,
    onOpenAiStudioForImage: (onImageReady: (String) -> Unit) -> Unit,
    existingBrands: List<String> = emptyList(),
    existingMotorTypes: List<String> = emptyList(),
    isSuperAdmin: Boolean = false,
    onManageAdminsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isEditMode = initialMotor != null

    var name by remember { mutableStateOf(initialMotor?.name ?: "") }
    var brand by remember { mutableStateOf(initialMotor?.brand ?: "") }
    var model by remember { mutableStateOf(initialMotor?.model ?: "") }
    var motorType by remember { mutableStateOf(initialMotor?.motorType ?: "") }
    var notes by remember { mutableStateOf(initialMotor?.notes ?: "") }
    var imageUrl by remember { mutableStateOf(initialMotor?.imageUrl ?: "") }

    // Dynamic Pinouts list with mutable states
    val pinouts = remember {
        mutableStateListOf<Pinout>().apply {
            if (initialMotor != null && initialMotor.pinouts.isNotEmpty()) {
                addAll(initialMotor.pinouts)
            } else {
                // Default two sample rows for new motor
                add(Pinout(pinNumber = 1, name = "تاكو 1", wireColor = "أصفر", colorHex = "#EAB308", functionDesc = "حساس سرعة الدوران"))
                add(Pinout(pinNumber = 2, name = "شربون 1", wireColor = "أحمر", colorHex = "#EF4444", functionDesc = "فرشاة العضو الدوار الأولى"))
            }
        }
    }

    var isSaving by remember { mutableStateOf(false) }
    var saveSuccessAnim by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val quickPinNames = listOf("تاكو 1", "تاكو 2", "شربون 1", "شربون 2", "ملف الستاتور 1", "ملف الستاتور 2", "طرف مشترك", "أوفرلود حراري", "طور U", "طور V", "طور W", "حساس هول")
    val quickColors = listOf("أصفر", "أحمر", "أزرق", "بني", "برتقالي", "أسود", "رمادي", "أبيض", "بنفسجي", "أخضر", "برتقالي + بنفسجي")

    Scaffold(
        modifier = modifier.testTag("admin_motor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Engineering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) "تعديل أطراف الماتور" else "إضافة ماتور ومخطط جديد",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // Success Feedback Banner
            item {
                AnimatedVisibility(visible = saveSuccessAnim, enter = fadeIn(), exit = fadeOut()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "تم حفظ ونشر الماتور وترتيب الأطراف بنجاح!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Dedicated Section for Super Admins: Manage Admins
            if (isSuperAdmin) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_admins_section_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "قسم إدارة المشرفين (Super Admin)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "إضافة أو سحب صلاحيات المشرفين من قاعدة البيانات",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Button(
                                onClick = onManageAdminsClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("open_manage_admins_button")
                            ) {
                                Text("إدارة المشرفين", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 1: Motor Info Fields
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "بيانات الماتور والموديل",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Motor Name Input
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم الماتور (مثال: ماتور يونيون إير / سامسونج)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_motor_name_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Motor Brand Input (Free Text Input)
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("الماركة المصنعة (اكتب بحرية)") },
                            placeholder = { Text("مثال: سامسونج، ال جي، يونيون إير، توشيبا، بوش...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_motor_brand_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        if (existingBrands.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "أو اختر من الماركات المسجلة مسبقاً في الدليل:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(existingBrands) { b ->
                                    FilterChip(
                                        selected = brand.trim() == b.trim(),
                                        onClick = { brand = b },
                                        label = { Text(b) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("admin_brand_chip_$b")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Model Code Input
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("رقم أو كود الموديل (مثال: MCA 38/64 أو WW80)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_motor_model_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Motor Type / Category Input (Free Text Input)
                        OutlinedTextField(
                            value = motorType,
                            onValueChange = { motorType = it },
                            label = { Text("نوع الماتور / التصنيف (اكتب بحرية)") },
                            placeholder = { Text("مثال: شربون، انفرتر دفع مباشر، BLDC، حثي مكثف...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_motor_type_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        if (existingMotorTypes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "أو اختر من الأنواع المسجلة مسبقاً في الدليل:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(existingMotorTypes) { type ->
                                    FilterChip(
                                        selected = motorType.trim() == type.trim(),
                                        onClick = { motorType = type },
                                        label = { Text(type) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.testTag("admin_type_chip_$type")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Notes Input
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات فنية / قيم المقاومة بالأوم (اختياري)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            minLines = 2,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // AI Diagram / Image Affordance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مخطط الماتور:", style = MaterialTheme.typography.labelMedium)
                            Button(
                                onClick = {
                                    onOpenAiStudioForImage { generatedBase64 ->
                                        imageUrl = generatedBase64
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("توليد أو تعديل صورة بـ Gemini")
                            }
                        }
                    }
                }
            }

            // Section 2: Dynamic Pinout Builder
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "بناء وترتيب أطراف التوصيل",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "استخدم الأسهم ⬆ ⬇ لإعادة ترتيب الأطراف مباشرة على الماتور",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Pin Row Button
                    Button(
                        onClick = {
                            val newNumber = pinouts.size + 1
                            pinouts.add(
                                Pinout(
                                    pinNumber = newNumber,
                                    name = "طرف $newNumber",
                                    wireColor = "أزرق",
                                    colorHex = "#2563EB",
                                    functionDesc = ""
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_pin_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف جديد")
                    }
                }
            }

            // Pinout Rows
            itemsIndexed(pinouts) { index, pin ->
                AdminPinoutRow(
                    index = index,
                    pin = pin,
                    totalCount = pinouts.size,
                    quickPinNames = quickPinNames,
                    quickColors = quickColors,
                    onUpdate = { updated ->
                        pinouts[index] = updated
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val item = pinouts.removeAt(index)
                            pinouts.add(index - 1, item)
                            // Re-number
                            pinouts.forEachIndexed { i, p ->
                                pinouts[i] = p.copy(pinNumber = i + 1)
                            }
                        }
                    },
                    onMoveDown = {
                        if (index < pinouts.size - 1) {
                            val item = pinouts.removeAt(index)
                            pinouts.add(index + 1, item)
                            // Re-number
                            pinouts.forEachIndexed { i, p ->
                                pinouts[i] = p.copy(pinNumber = i + 1)
                            }
                        }
                    },
                    onDelete = {
                        pinouts.removeAt(index)
                        // Re-number
                        pinouts.forEachIndexed { i, p ->
                            pinouts[i] = p.copy(pinNumber = i + 1)
                        }
                    }
                )
            }

            // Section 3: Save and Publish Action
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("يرجى كتابة اسم الماتور") }
                            return@Button
                        }
                        if (pinouts.isEmpty()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("يرجى إضافة طرف واحد على الأقل") }
                            return@Button
                        }

                        isSaving = true
                        val motorToSave = Motor(
                            id = initialMotor?.id ?: "motor_${UUID.randomUUID()}",
                            userId = currentUserId,
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            motorType = motorType,
                            notes = notes.trim(),
                            imageUrl = imageUrl,
                            pinouts = pinouts.toList()
                        )

                        saveSuccessAnim = true
                        onSaveSuccess(motorToSave)
                        coroutineScope.launch {
                            delay(1200)
                            saveSuccessAnim = false
                        }
                        isSaving = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_publish_motor_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حفظ ونشر الماتور",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (isEditMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDeleteSuccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("delete_motor_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حذف هذا الماتور من الدليل")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPinoutRow(
    index: Int,
    pin: Pinout,
    totalCount: Int,
    quickPinNames: List<String>,
    quickColors: List<String>,
    onUpdate: (Pinout) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_pin_row_${index}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Pin Number badge, Reorder arrows (Up/Down), Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#${pin.pinNumber}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    WireColorBadge(wireColor = pin.wireColor, fallbackHex = pin.colorHex)
                }

                // Reorder Arrow Buttons & Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Up arrow button
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("move_up_pin_${index}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "تحريك لأعلى",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }

                    // Down arrow button
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("move_down_pin_${index}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "تحريك لأسفل",
                            tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_pin_${index}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الطرف",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pin Name Input
            OutlinedTextField(
                value = pin.name,
                onValueChange = { onUpdate(pin.copy(name = it)) },
                label = { Text("اسم ووظيفة الطرف (مثال: طرف ملف مشترك / تاكو / شربون)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_name_input_${index}"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Quick Pin Name Suggestions
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(quickPinNames.take(6)) { preset ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = preset,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { onUpdate(pin.copy(name = preset)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Wire Color Input
            OutlinedTextField(
                value = pin.wireColor,
                onValueChange = { onUpdate(pin.copy(wireColor = it)) },
                label = { Text("لون السلك (مثال: برتقالي + بنفسجي أو أصفر)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wire_color_input_${index}"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Quick Colors Palette
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickColors) { c ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .clickable { onUpdate(pin.copy(wireColor = c)) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(parseWireColorToComposeColor(c))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = c, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Function Explanation
            OutlinedTextField(
                value = pin.functionDesc,
                onValueChange = { onUpdate(pin.copy(functionDesc = it)) },
                label = { Text("شرح فني إضافي للطرف (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        }
    }
}
