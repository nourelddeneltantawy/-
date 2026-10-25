package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppliancePart
import com.example.data.model.Pinout
import com.example.ui.components.WireColorBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPartScreen(
    initialPart: AppliancePart?,
    onSaveSuccess: (AppliancePart) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialPart?.name ?: "") }
    var partType by remember { mutableStateOf(initialPart?.partType ?: "لوك باب") }
    var brand by remember { mutableStateOf(initialPart?.brand ?: "") }
    var model by remember { mutableStateOf(initialPart?.model ?: "") }
    var resistanceValue by remember { mutableStateOf(initialPart?.resistanceValue ?: "") }
    var operatingVoltage by remember { mutableStateOf(initialPart?.operatingVoltage ?: "220V AC") }
    var testMethod by remember { mutableStateOf(initialPart?.testMethod ?: "") }
    var notes by remember { mutableStateOf(initialPart?.notes ?: "") }

    val pinouts = remember {
        mutableStateListOf<Pinout>().apply {
            addAll(initialPart?.pinouts ?: emptyList())
        }
    }

    val availableTypes = listOf("لوك باب", "ميزان ماء / ليفل", "طلمبة طرد", "صمام سحب مياه", "سخان وثرمستات")

    val colorOptions = listOf(
        Pair("أحمر", "#EF4444"),
        Pair("أزرق", "#2563EB"),
        Pair("أصفر", "#EAB308"),
        Pair("أسود", "#1E293B"),
        Pair("أبيض", "#F8FAFC"),
        Pair("بني", "#854D0E"),
        Pair("برتقالي", "#F97316"),
        Pair("أخضر", "#16A34A"),
        Pair("بنفسجي", "#9333EA"),
        Pair("أصفر / أخضر", "#EAB308")
    )

    Scaffold(
        modifier = modifier.testTag("admin_part_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initialPart == null) "إضافة قطعة غيار جديدة" else "تعديل قطعة الغيار",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val updated = (initialPart ?: AppliancePart()).copy(
                                    name = name.trim(),
                                    partType = partType,
                                    brand = brand.trim(),
                                    model = model.trim(),
                                    pinouts = pinouts.toList(),
                                    resistanceValue = resistanceValue.trim(),
                                    operatingVoltage = operatingVoltage.trim(),
                                    testMethod = testMethod.trim(),
                                    notes = notes.trim()
                                )
                                onSaveSuccess(updated)
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("save_part_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ القطعة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("نوع القطعة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = partType == type,
                            onClick = { partType = type },
                            label = { Text(type, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("البيانات الأساسية للقطعة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم القطعة (مثال: لوك باب 3 طرف حراري PTC) *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("الماركة (LG, Samsung...)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("رقم الموديل / الكود") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = operatingVoltage,
                                onValueChange = { operatingVoltage = it },
                                label = { Text("جهد التشغيل (مثال: 220V AC)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = resistanceValue,
                                onValueChange = { resistanceValue = it },
                                label = { Text("المقاومة (مثال: 800 - 1100 أوم)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = testMethod,
                            onValueChange = { testMethod = it },
                            label = { Text("طريقة الفحص والاختبار بالسيريا / الملتيميتر") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات فنية / أعطال شائعة للقطعة") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("أطراف التوصيل (Pinouts)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${pinouts.size} أطراف محددة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val nextPinNum = pinouts.size + 1
                            pinouts.add(
                                Pinout(
                                    pinNumber = nextPinNum,
                                    name = "طرف $nextPinNum",
                                    wireColor = "أزرق",
                                    colorHex = "#2563EB",
                                    functionDesc = ""
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف")
                    }
                }
            }

            if (pinouts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("لم تقم بإضافة أي أطراف بعد. اضغط على 'إضافة طرف' للبدء.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(pinouts) { index, pin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${pin.pinNumber}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text("الطرف رقم ${pin.pinNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                IconButton(
                                    onClick = { pinouts.removeAt(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف الطرف", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = pin.name,
                                    onValueChange = { newName: String ->
                                        pinouts[index] = pin.copy(name = newName)
                                    },
                                    label = { Text("رمز الطرف (L, N, C)") },
                                    modifier = Modifier.weight(0.35f),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = pin.functionDesc,
                                    onValueChange = { newFunc: String ->
                                        pinouts[index] = pin.copy(functionDesc = newFunc)
                                    },
                                    label = { Text("الوظيفة (مثال: نيوترال N، خرج ريلاي C...)") },
                                    modifier = Modifier.weight(0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Color picker for pin
                            var showColorMenu by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("لون السلك:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    WireColorBadge(wireColor = pin.wireColor, colorHex = pin.colorHex)
                                }

                                Box {
                                    OutlinedButton(
                                        onClick = { showColorMenu = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("تغيير اللون", fontSize = 11.sp)
                                    }

                                    DropdownMenu(
                                        expanded = showColorMenu,
                                        onDismissRequest = { showColorMenu = false }
                                    ) {
                                        colorOptions.forEach { (colorName, hex) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                                .border(1.dp, Color.Gray, CircleShape)
                                                        )
                                                        Text(colorName, fontSize = 13.sp)
                                                    }
                                                },
                                                onClick = {
                                                    pinouts[index] = pin.copy(wireColor = colorName, colorHex = hex)
                                                    showColorMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
