package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Motor
import com.example.data.model.MotorCategory
import com.example.data.model.Pinout
import com.example.ui.components.InteractiveMotorConnector
import com.example.ui.components.parseWireColorToComposeColor
import kotlinx.coroutines.launch

data class WireColorPreset(
    val name: String,
    val hex: String
)

val commonWireColorPresets = listOf(
    WireColorPreset("أحمر", "#EF4444"),
    WireColorPreset("أزرق", "#3B82F6"),
    WireColorPreset("أصفر", "#EAB308"),
    WireColorPreset("أسود", "#1E293B"),
    WireColorPreset("أبيض", "#F8FAFC"),
    WireColorPreset("بني", "#854D0E"),
    WireColorPreset("برتقالي", "#F97316"),
    WireColorPreset("رمادي", "#64748B"),
    WireColorPreset("أخضر", "#22C55E"),
    WireColorPreset("بنفسجي", "#A855F7"),
    WireColorPreset("أصفر/أخضر", "#84CC16"),
    WireColorPreset("سماوي", "#06B6D4")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminMotorScreen(
    initialMotor: Motor?,
    currentUserId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: (Motor) -> Unit,
    onDeleteSuccess: () -> Unit,
    onOpenAiStudioForImage: ((String) -> Unit) -> Unit,
    availableCategories: List<MotorCategory> = emptyList(),
    onAddNewCategory: (String) -> Unit = {},
    existingBrands: List<String> = emptyList(),
    existingMotorTypes: List<String> = emptyList(),
    isSuperAdmin: Boolean = false,
    onManageAdminsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(initialMotor?.name ?: "") }
    var brand by remember { mutableStateOf(initialMotor?.brand ?: "") }
    var model by remember { mutableStateOf(initialMotor?.model ?: "") }
    var category by remember { mutableStateOf(initialMotor?.category ?: "") }
    var motorType by remember { mutableStateOf(initialMotor?.motorType ?: "") }
    var notes by remember { mutableStateOf(initialMotor?.notes ?: "") }
    var imageUrl by remember { mutableStateOf(initialMotor?.imageUrl ?: "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    // Photo picker for selecting image from device storage
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUrl = uri.toString()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("تم تحديد الصورة بنجاح")
            }
        }
    }

    // Pinouts: Starts strictly EMPTY if creating a new motor! User adds pin-by-pin ("طرف طرف")
    val pinouts = remember {
        mutableStateListOf<Pinout>().apply {
            if (initialMotor != null && initialMotor.pinouts.isNotEmpty()) {
                addAll(initialMotor.pinouts)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("تأكيد حذف الماتور") },
            text = { Text("هل أنت متأكد من رغبتك في حذف ماتور '${name.ifBlank { "هذا الماتور" }}'؟ سيتم حذف جميع الأطراف والبيانات المرتبطة به نهائياً.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، احذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("إضافة قائمة / تصنيف جديد") },
            text = {
                Column {
                    Text("أدخل اسم القائمة لتصنيف مواتيرك بداخلها:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text("اسم القائمة (مثال: مواتير شربون، مواتير إل جي)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryInput.trim()
                        if (trimmed.isNotBlank()) {
                            onAddNewCategory(trimmed)
                            category = trimmed
                            newCategoryInput = ""
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("إضافة واختيار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.testTag("admin_motor_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (initialMotor == null) "إضافة ماتور جديد" else "تعديل الماتور") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (initialMotor != null) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("delete_motor_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف الماتور",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: User's Custom Lists / Categories
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "القائمة / التصنيف التابع له الماتور",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            TextButton(onClick = { showAddCategoryDialog = true }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("قائمة جديدة")
                            }
                        }

                        if (availableCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableCategories.forEach { cat ->
                                    FilterChip(
                                        selected = category == cat.name,
                                        onClick = {
                                            category = if (category == cat.name) "" else cat.name
                                        },
                                        label = { Text(cat.name) },
                                        leadingIcon = if (category == cat.name) {
                                            { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("أو اكتب اسم القائمة يدوياً هنا") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Section 2: Basic Motor Information
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الماتور * (مثال: ماتور يونيون إير)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("الماركة (Brand) *") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("الموديل (Model)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = motorType,
                    onValueChange = { motorType = it },
                    label = { Text("نوع الماتور (مثال: Universal، Inverter BLDC، Direct Drive)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات فنية وتوصيات التوصيل وقياس الأوم") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Image Section: Picker + URL + AI Studio
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "صورة الماتور أو مخطط الأسلاك",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Preview if image is present
                        if (imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "معاينة صورة الماتور",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    IconButton(
                                        onClick = { imageUrl = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إزالة الصورة",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Image source buttons: Device Gallery + AI
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("من المعرض", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    onOpenAiStudioForImage { generatedUrl ->
                                        imageUrl = generatedUrl
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("توليد ذكي", fontSize = 13.sp)
                            }
                        }

                        // Direct URL input fallback
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("أو أدخل رابط الصورة مباشرة (URL)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Pinouts section header (Step-by-step pin addition)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${pinouts.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "أطراف التوصيل والأسلاك",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = {
                            val nextNum = pinouts.size + 1
                            pinouts.add(Pinout(nextNum, "طرف $nextNum", "أزرق", "#3B82F6", ""))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف +")
                    }
                }
            }

            if (pinouts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "لم تقم بإضافة أي أطراف بعد",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اضغط على زر \"إضافة طرف +\" بالأعلى لإضافة أطراف الماتور طرفاً طرفاً وتحديد ألوانها ووظائفها بدقة.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Pinouts list with interactive wire color swatches and smooth reordering
            itemsIndexed(
                items = pinouts,
                key = { index, _ -> "pinout_$index" }
            ) { index, pin ->
                val pinColor = parseWireColorToComposeColor(pin.colorHex, pin.wireColor)
                var showColorPalette by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Pinout Header: Number, Color dot, and Reorder/Delete actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(pinColor)
                                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "الطرف رقم ${pin.pinNumber}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Move Up
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val current = pinouts.removeAt(index)
                                            pinouts.add(index - 1, current)
                                            // Re-number pins sequentially
                                            pinouts.forEachIndexed { i, p ->
                                                pinouts[i] = p.copy(pinNumber = i + 1)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "تحريك لأعلى", modifier = Modifier.size(18.dp))
                                    }
                                }

                                // Move Down
                                if (index < pinouts.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val current = pinouts.removeAt(index)
                                            pinouts.add(index + 1, current)
                                            // Re-number pins sequentially
                                            pinouts.forEachIndexed { i, p ->
                                                pinouts[i] = p.copy(pinNumber = i + 1)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "تحريك لأسفل", modifier = Modifier.size(18.dp))
                                    }
                                }

                                // Color Palette toggle
                                IconButton(
                                    onClick = { showColorPalette = !showColorPalette },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "اختيار لون السلك",
                                        tint = pinColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete pin
                                IconButton(
                                    onClick = {
                                        pinouts.removeAt(index)
                                        pinouts.forEachIndexed { i, p ->
                                            pinouts[i] = p.copy(pinNumber = i + 1)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الطرف",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Wire Color Palette
                        AnimatedVisibility(visible = showColorPalette) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "اختر لون السلك النموذجي:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        commonWireColorPresets.forEach { preset ->
                                            val presetColor = parseWireColorToComposeColor(preset.hex, preset.name)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (pin.colorHex.equals(preset.hex, ignoreCase = true) || pin.wireColor.equals(preset.name, ignoreCase = true)) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                modifier = Modifier.clickable {
                                                    pinouts[index] = pin.copy(wireColor = preset.name, colorHex = preset.hex)
                                                    showColorPalette = false
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(presetColor)
                                                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = preset.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Input fields for this pin
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pin.name,
                                onValueChange = { newName ->
                                    pinouts[index] = pin.copy(name = newName)
                                },
                                label = { Text("وظيفة الطرف (مثال: تاكو، شربون)") },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = pin.wireColor,
                                onValueChange = { newColor ->
                                    pinouts[index] = pin.copy(wireColor = newColor)
                                },
                                label = { Text("اسم اللون") },
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        OutlinedTextField(
                            value = pin.functionDesc,
                            onValueChange = { newDesc ->
                                pinouts[index] = pin.copy(functionDesc = newDesc)
                            },
                            label = { Text("شرح تفصيلي / قياس الأوم (مثال: مقاومة 65-70 أوم)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Live Virtual Connector Preview when pins exist
            if (pinouts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "معاينة الفيشة التفاعلية الناتجة:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    InteractiveMotorConnector(
                        pinouts = pinouts.toList(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Save Action
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || brand.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("يرجى إدخال اسم الماتور والماركة على الأقل")
                            }
                            return@Button
                        }

                        val updatedMotor = Motor(
                            id = initialMotor?.id ?: "",
                            userId = initialMotor?.userId ?: currentUserId,
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            category = category.trim(),
                            motorType = motorType.trim(),
                            notes = notes.trim(),
                            imageUrl = imageUrl.trim(),
                            pinouts = pinouts.toList(),
                            orderIndex = initialMotor?.orderIndex ?: 0,
                            createdAt = initialMotor?.createdAt,
                            updatedAt = null
                        )
                        onSaveSuccess(updatedMotor)
                    },
                    modifier = Modifier
                        .testTag("save_motor_button")
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ ونشر الماتور", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
