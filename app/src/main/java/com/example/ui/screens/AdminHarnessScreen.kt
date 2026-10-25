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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.ui.components.WireColorBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHarnessScreen(
    initialHarness: Harness?,
    onSaveSuccess: (Harness) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialHarness?.name ?: "") }
    var brand by remember { mutableStateOf(initialHarness?.brand ?: "") }
    var model by remember { mutableStateOf(initialHarness?.model ?: "") }
    var notes by remember { mutableStateOf(initialHarness?.notes ?: "") }

    val boardPins = remember { mutableStateListOf<HarnessPin>().apply { addAll(initialHarness?.boardPins ?: emptyList()) } }
    val bottomPins = remember { mutableStateListOf<HarnessPin>().apply { addAll(initialHarness?.bottomPins ?: emptyList()) } }

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

    val componentTypes = listOf("ماتور", "لوك الباب", "ليفل الماء", "طلمبة الطرد", "صمام الدخول")

    Scaffold(
        modifier = modifier.testTag("admin_harness_screen"),
        topBar = {
            TopAppBar(
                title = { Text(if (initialHarness == null) "إضافة خريطة ضفيرة جديدة" else "تعديل خريطة الضفيرة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
            // General Details Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بيانات الضفيرة الأساسية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم الضفيرة (مثال: ضفيرة غسالة إل جي انفرتر)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("الماركة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("الموديل") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات فنية عن الضفيرة") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Board Pins Section (من 1 لـ 15)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. أطراف الكارتة من أعلى (${boardPins.size} أطراف)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = {
                        val nextPin = boardPins.size + 1
                        boardPins.add(
                            HarnessPin(
                                pinNumber = nextPin,
                                label = "طرف $nextPin",
                                wireColor = "أزرق",
                                colorHex = "#2563EB",
                                connectedComponent = "ماتور",
                                targetPinNumber = nextPin
                            )
                        )
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف كارتة")
                    }
                }
            }

            itemsIndexed(boardPins) { index, pin ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("طرف الكارتة #${pin.pinNumber}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { boardPins.removeAt(index) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = pin.label,
                            onValueChange = { newLabel ->
                                boardPins[index] = pin.copy(label = newLabel)
                            },
                            label = { Text("وظيفة الطرف في الكارتة (مثال: تغذية ماتور U / تاكو)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Color Picker Selector
                            var showColorDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showColorDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 14.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(pin.wireColor, maxLines = 1)
                                }
                                DropdownMenu(
                                    expanded = showColorDropdown,
                                    onDismissRequest = { showColorDropdown = false }
                                ) {
                                    colorOptions.forEach { (colorName, hex) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    WireColorBadge(colorHex = hex, size = 16.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(colorName)
                                                }
                                            },
                                            onClick = {
                                                boardPins[index] = pin.copy(
                                                    wireColor = colorName,
                                                    colorHex = hex,
                                                    secondaryColorHex = if (colorName.contains("/")) "#16A34A" else null,
                                                    secondaryColorName = if (colorName.contains("/")) "أخضر" else null
                                                )
                                                showColorDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Connected Component Selector
                            var showCompDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showCompDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(pin.connectedComponent, maxLines = 1)
                                }
                                DropdownMenu(
                                    expanded = showCompDropdown,
                                    onDismissRequest = { showCompDropdown = false }
                                ) {
                                    componentTypes.forEach { comp ->
                                        DropdownMenuItem(
                                            text = { Text(comp) },
                                            onClick = {
                                                boardPins[index] = pin.copy(connectedComponent = comp)
                                                showCompDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Pins Section (الماتور والقطع)
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. أطراف الماتور والقطع بالأسفل (${bottomPins.size} أطراف)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = {
                        val nextPin = bottomPins.size + 1
                        bottomPins.add(
                            HarnessPin(
                                pinNumber = nextPin,
                                label = "طرف $nextPin",
                                wireColor = "أزرق",
                                colorHex = "#2563EB",
                                connectedComponent = "ماتور"
                            )
                        )
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة طرف قطعة")
                    }
                }
            }

            itemsIndexed(bottomPins) { index, pin ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${pin.connectedComponent} - الطرف #${pin.pinNumber}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { bottomPins.removeAt(index) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedTextField(
                            value = pin.label,
                            onValueChange = { newLabel ->
                                bottomPins[index] = pin.copy(label = newLabel)
                            },
                            label = { Text("اسم الطرف بالقطعة (مثال: شربون / تاكوجينيراتور)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val newHarness = Harness(
                            id = initialHarness?.id ?: "",
                            name = name.trim().ifBlank { "ضفيرة غسالة جديدة" },
                            brand = brand.trim(),
                            model = model.trim(),
                            notes = notes.trim(),
                            boardPins = boardPins.toList(),
                            bottomPins = bottomPins.toList(),
                            orderIndex = initialHarness?.orderIndex ?: 0,
                            createdAt = initialHarness?.createdAt,
                            updatedAt = null
                        )
                        onSaveSuccess(newHarness)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ خريطة الضفيرة في السحابة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
