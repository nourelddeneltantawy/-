package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.AppliancePart
import com.example.data.model.Harness
import com.example.data.model.Motor
import com.example.ui.components.InteractiveHarnessMap
import com.example.ui.components.WireColorBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemIntegratorScreen(
    harnesses: List<Harness>,
    motors: List<Motor>,
    parts: List<AppliancePart>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedHarness by remember { mutableStateOf(harnesses.firstOrNull()) }
    var selectedMotor by remember { mutableStateOf(motors.firstOrNull()) }
    var selectedPart by remember { mutableStateOf(parts.firstOrNull()) }

    var showHarnessDropdown by remember { mutableStateOf(false) }
    var showMotorDropdown by remember { mutableStateOf(false) }
    var showPartDropdown by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("system_integrator_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "مخطط التجميع الشامل للمنظومة",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "دمج وتوصيل أي ضفيرة مع أي ماتور وقطعة أخرى",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description Banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "اختر الضفيرة والماتور والقطعة الإضافية وسيقوم التطبيق بربط الأطراف المتقابلة وتوضيح مسار كل سلك من الكارتة وحتى القطعة النهائية.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                    )
                }
            }

            // Selection Controls Grid
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "عناصر المنظومة المختارة للربط:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // 1. Harness Selector
                    Box {
                        OutlinedButton(
                            onClick = { showHarnessDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الضفيرة: " + (selectedHarness?.name ?: "اختر ضفيرة"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = showHarnessDropdown,
                            onDismissRequest = { showHarnessDropdown = false }
                        ) {
                            harnesses.forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(h.name) },
                                    onClick = {
                                        selectedHarness = h
                                        showHarnessDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. Motor Selector
                    Box {
                        OutlinedButton(
                            onClick = { showMotorDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الماتور: " + (selectedMotor?.let { "${it.name} (${it.brand})" } ?: "اختر ماتور"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = showMotorDropdown,
                            onDismissRequest = { showMotorDropdown = false }
                        ) {
                            motors.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.name} (${m.brand})") },
                                    onClick = {
                                        selectedMotor = m
                                        showMotorDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. Part Selector (Door Lock / Water Level / Pump)
                    Box {
                        OutlinedButton(
                            onClick = { showPartDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "القطعة: " + (selectedPart?.let { "${it.name} (${it.partType})" } ?: "اختر قطعة"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = showPartDropdown,
                            onDismissRequest = { showPartDropdown = false }
                        ) {
                            parts.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} (${p.partType})") },
                                    onClick = {
                                        selectedPart = p
                                        showPartDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Wiring Canvas for the Selected System
            selectedHarness?.let { h ->
                InteractiveHarnessMap(harness = h)
            }

            // Integrated Pin Matching Table
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جدول مطابقة خطوط الأسلاك والأطراف",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    selectedHarness?.boardPins?.forEach { bPin ->
                        val matchingMotorPin = selectedMotor?.pinouts?.find {
                            it.name.contains(bPin.label, ignoreCase = true) ||
                            it.wireColor.equals(bPin.wireColor, ignoreCase = true) ||
                            (bPin.connectedComponent == "ماتور" && it.pinNumber == bPin.targetPinNumber)
                        }
                        val matchingPartPin = selectedPart?.pinouts?.find {
                            bPin.connectedComponent.contains(selectedPart?.partType ?: "", ignoreCase = true) &&
                            it.pinNumber == bPin.targetPinNumber
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${bPin.pinNumber}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(bPin.label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        Text("كارتة التحكم", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WireColorBadge(colorHex = bPin.colorHex, secondaryColorHex = bPin.secondaryColorHex, size = 16.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bPin.wireColor,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        val destinationName = if (bPin.connectedComponent == "ماتور" && matchingMotorPin != null) {
                                            "ماتور: ${matchingMotorPin.name} (#${matchingMotorPin.pinNumber})"
                                        } else if (matchingPartPin != null) {
                                            "${selectedPart?.partType}: ${matchingPartPin.name} (#${matchingPartPin.pinNumber})"
                                        } else {
                                            "${bPin.connectedComponent} (#${bPin.targetPinNumber ?: 1})"
                                        }

                                        Text(
                                            text = destinationName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Text(
                                            text = bPin.signalType.ifBlank { "خط متصل" },
                                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
