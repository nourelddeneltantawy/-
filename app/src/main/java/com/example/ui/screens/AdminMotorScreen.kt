package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.example.ui.components.parseWireColorToComposeColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMotorScreen(
    initialMotor: Motor?,
    currentUserId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: (Motor) -> Unit,
    onDeleteSuccess: () -> Unit,
    onOpenAiStudioForImage: ((String) -> Unit) -> Unit,
    existingBrands: List<String>,
    existingMotorTypes: List<String>,
    isSuperAdmin: Boolean,
    onManageAdminsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(initialMotor?.name ?: "") }
    var brand by remember { mutableStateOf(initialMotor?.brand ?: "") }
    var model by remember { mutableStateOf(initialMotor?.model ?: "") }
    var motorType by remember { mutableStateOf(initialMotor?.motorType ?: "Universal") }
    var notes by remember { mutableStateOf(initialMotor?.notes ?: "") }
    var imageUrl by remember { mutableStateOf(initialMotor?.imageUrl ?: "") }

    val pinouts = remember {
        mutableStateListOf<Pinout>().apply {
            if (initialMotor != null && initialMotor.pinouts.isNotEmpty()) {
                addAll(initialMotor.pinouts)
            } else {
                add(Pinout(1, "تاكو 1", "أصفر", "#EAB308", "حساس سرعة"))
                add(Pinout(2, "تاكو 2", "أصفر", "#EAB308", "حساس سرعة"))
                add(Pinout(3, "شربون 1", "أحمر", "#EF4444", "فرشاة كربونية"))
                add(Pinout(4, "شربون 2", "رمادي", "#6B7280", "فرشاة كربونية"))
                add(Pinout(5, "ستاتور مشترك", "أزرق", "#3B82F6", "الملف الثابت"))
            }
        }
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
                            onClick = onDeleteSuccess,
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
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الماتور *") },
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
                        label = { Text("الموديل (Model) *") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = motorType,
                    onValueChange = { motorType = it },
                    label = { Text("نوع الماتور (Universal, BLDC, Induction)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات هندسية وفنية") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("رابط صورة الماتور أو المخطط (URL)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        onOpenAiStudioForImage { generatedUrl ->
                            imageUrl = generatedUrl
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("توليد أو اقتراح مخطط بالذكاء الاصطناعي")
                }
            }

            // Pinouts section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أطراف التوصيل (${pinouts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = {
                            val nextNum = pinouts.size + 1
                            pinouts.add(Pinout(nextNum, "طرف $nextNum", "أزرق", "#3B82F6", ""))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة بن")
                    }
                }
            }

            itemsIndexed(pinouts) { index, pin ->
                val pinColor = parseWireColorToComposeColor(pin.colorHex, pin.wireColor)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(pinColor)
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بن رقم ${index + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            // Move up
                            if (index > 0) {
                                IconButton(onClick = {
                                    val item = pinouts.removeAt(index)
                                    pinouts.add(index - 1, item.copy(pinNumber = index))
                                    for (i in pinouts.indices) {
                                        pinouts[i] = pinouts[i].copy(pinNumber = i + 1)
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "أعلى")
                                }
                            }
                            // Move down
                            if (index < pinouts.size - 1) {
                                IconButton(onClick = {
                                    val item = pinouts.removeAt(index)
                                    pinouts.add(index + 1, item.copy(pinNumber = index + 2))
                                    for (i in pinouts.indices) {
                                        pinouts[i] = pinouts[i].copy(pinNumber = i + 1)
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "أسفل")
                                }
                            }
                            // Delete
                            IconButton(onClick = {
                                pinouts.removeAt(index)
                                for (i in pinouts.indices) {
                                    pinouts[i] = pinouts[i].copy(pinNumber = i + 1)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pin.name,
                                onValueChange = { newName ->
                                    pinouts[index] = pin.copy(name = newName)
                                },
                                label = { Text("وظيفة الطرف") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pin.wireColor,
                                onValueChange = { newColor ->
                                    pinouts[index] = pin.copy(wireColor = newColor)
                                },
                                label = { Text("لون السلك") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = pin.functionDesc,
                            onValueChange = { newDesc ->
                                pinouts[index] = pin.copy(functionDesc = newDesc)
                            },
                            label = { Text("شرح إضافي / مقاومة أوم") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || brand.isBlank() || model.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("يرجى ملء جميع الحقول الإلزامية (الاسم، الماركة، الموديل)")
                            }
                            return@Button
                        }

                        val updatedMotor = Motor(
                            id = initialMotor?.id ?: "",
                            userId = initialMotor?.userId ?: currentUserId,
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            motorType = motorType.trim(),
                            notes = notes.trim(),
                            imageUrl = imageUrl.trim(),
                            pinouts = pinouts.toList(),
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
                    Text("حفظ ونشر الماتور", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
