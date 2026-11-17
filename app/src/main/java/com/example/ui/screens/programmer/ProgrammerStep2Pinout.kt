package com.example.ui.screens.programmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.McuPinMapping
import com.example.data.model.ProgrammerChipType
import com.example.ui.viewmodel.McuProgrammerUiState

@Composable
fun ProgrammerStep2Pinout(
    state: McuProgrammerUiState,
    onSelectProgrammer: (ProgrammerChipType) -> Unit,
    onRefreshUsb: () -> Unit,
    onNextStep: () -> Unit,
    onSkipStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val mcu = state.selectedProcessor
    val pinoutList = mcu?.effectivePinout() ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Supported USB-to-TTL Adapters Header + Refresh
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "اختيار المبرمجة (USB-to-TTL OTG)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.attachedUsbDevices.isEmpty()) "لا توجد أجهزة USB OTG موصولة حالياً"
                                else "تم رصد ${state.attachedUsbDevices.size} جهاز USB متصل",
                                fontSize = 11.sp,
                                color = if (state.attachedUsbDevices.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                else Color(0xFF10B981)
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefreshUsb,
                        modifier = Modifier.testTag("refresh_usb_devices_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث فحص USB OTG",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                ProgrammerChipType.entries.forEach { chip ->
                    val isSelected = state.selectedProgrammer == chip
                    val attachedMatch = state.attachedUsbDevices.firstOrNull { it.chipType == chip }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProgrammer(chip) }
                            .testTag("programmer_option_${chip.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectProgrammer(chip) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = chip.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (attachedMatch != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "متصلة الآن",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = chip.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "مستوى الإشارة: ${chip.voltageLogic}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pinout Mapping Diagram & Table
        if (mcu != null) {
            if (mcu.isDiodeSingleWireSchematicFamily()) {
                val isR8cFamily = mcu.mainFamily.trim().uppercase().contains("R8C")
                Diode1N4148SchematicCard(
                    familyName = mcu.mainFamily,
                    partNumber = mcu.partNumber,
                    chipModel = state.selectedProgrammer.chipModel,
                    is78k0Flmd0 = mcu.is78k0Flmd0Mode(),
                    isR8cFamily = isR8cFamily,
                    voltage = mcu.recommendedVoltage
                )
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "مخطط توصيل الأطراف (Pinout Guide)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${state.selectedProgrammer.chipModel} ⇄ ${mcu.partNumber} (${mcu.pins}-Pin • ${mcu.mainFamily})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    pinoutList.forEachIndexed { idx, pinMap ->
                        PinoutBridgeCard(index = idx + 1, mapping = pinMap)
                    }

                    if (mcu.customPinout.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "يتم عرض التوصيل القياسي المعتمد لعائلة ${mcu.mainFamily}. يمكنك مستقبلاً إضافة أرقام البنّات الدقيقة لكل موديل داخل حقل \"pinout\" في ملفات JSON في assets/databases/ وستظهر هنا تلقائياً.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Next & Skip Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSkipStep,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(0.35f)
                    .heightIn(min = 50.dp)
                    .testTag("step2_skip_button")
            ) {
                Text("تخطي", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNextStep,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(0.65f)
                    .heightIn(min = 50.dp)
                    .testTag("step2_next_button")
            ) {
                Text(
                    text = "التالي: فتح واجهة البرمجة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PinoutBridgeCard(
    index: Int,
    mapping: McuPinMapping
) {
    val wireColor = when {
        mapping.programmerPin.contains("TXD", ignoreCase = true) -> Color(0xFF38BDF8)
        mapping.programmerPin.contains("RXD", ignoreCase = true) -> Color(0xFF34D399)
        mapping.programmerPin.contains("VCC", ignoreCase = true) -> Color(0xFFEF4444)
        mapping.programmerPin.contains("GND", ignoreCase = true) -> Color(0xFF94A3B8)
        else -> Color(0xFFF59E0B)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, wireColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Programmer Pin Pill
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(wireColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = wireColor)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = mapping.programmerPin,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = wireColor
                    )
                }

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = wireColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )

                // Target MCU Pin Pill
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Text(
                        text = mapping.mcuPinName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = mapping.mcuPinNumber,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (mapping.notes.isNotBlank()) {
                Text(
                    text = mapping.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun Diode1N4148SchematicCard(
    familyName: String,
    partNumber: String,
    chipModel: String,
    is78k0Flmd0: Boolean,
    isR8cFamily: Boolean,
    voltage: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, Color(0xFF0EA5E9).copy(alpha = 0.5f)),
        modifier = Modifier.testTag("step2_diode_schematic_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "المخطط الهندسي المعتمد (1N4148 Diodes)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "$chipModel ➔ $partNumber ($familyName)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isR8cFamily -> Color(0xFF38BDF8).copy(alpha = 0.16f)
                        is78k0Flmd0 -> Color(0xFFF59E0B).copy(alpha = 0.16f)
                        else -> Color(0xFF10B981).copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = when {
                            isR8cFamily -> "Auto Boot + Mode 3"
                            is78k0Flmd0 -> "وضع FLMD0"
                            else -> "وضع TOOL0/MODE"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isR8cFamily -> Color(0xFF38BDF8)
                            is78k0Flmd0 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Table Header
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "طرف المبرمجة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "المكوّن / التوصيل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "طرف المعالج",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SchematicTableRow(
                programmerSignal = "RTS",
                bridgeComponent = if (isR8cFamily) "دايود (كاثود على RTS)" else "دايود D2 (1N4148)",
                mcuTarget = if (isR8cFamily) "MODE / RX" else "RESET#",
                accentColor = Color(0xFFF59E0B),
                subNote = if (isR8cFamily) {
                    "RTS=true لمدة 250ms + 150ms أثناء صعود RESET ← RTS=false (100ms) ثم rxQueue.clear()"
                } else {
                    "الكاثود جهة RTS — ينفذ نبضة Reset 50ms ثم HIGH"
                }
            )
            SchematicTableRow(
                programmerSignal = "TX + RX",
                bridgeComponent = if (isR8cFamily) "دايود TX + RX مباشر" else "دايود D1 (1N4148)",
                mcuTarget = if (isR8cFamily) "MODE (Single-Wire)" else "TOOL0 / MODE",
                accentColor = Color(0xFF38BDF8),
                subNote = if (isR8cFamily) {
                    "16×0x00 بايت-ببايت ← 0xB0 واحدة [B0, B0] ← 0xFB ← 0x50 ← 0xF5/0x70 مع حذف صدى N بدقة"
                } else {
                    "خط البيانات والـ Mode المشترك (مع إلغاء صدى D1 تلقائياً بالكود)"
                }
            )
            SchematicTableRow(
                programmerSignal = "DTR",
                bridgeComponent = when {
                    isR8cFamily -> "توصيل مباشر (RESET)"
                    is78k0Flmd0 -> "توصيل مباشر (HIGH)"
                    else -> "اختياري (FLMD0)"
                },
                mcuTarget = if (isR8cFamily) "RESET#" else "FLMD0",
                accentColor = if (isR8cFamily || is78k0Flmd0) Color(0xFF10B981) else Color(0xFF94A3B8),
                subNote = when {
                    isR8cFamily -> "RESET=LOW (250ms) ← RESET=HIGH (150ms)، مع عكس قطبية DTR تلقائياً إذا فشل [B0, B0]"
                    is78k0Flmd0 -> "أساسي لعائلة 78K0: يُرفع إلى 5V HIGH عبر DTR = false قبل Reset"
                    else -> "مخصص لعائلة 78K0 (غير مطلوب في وضع TOOL0)"
                }
            )
            SchematicTableRow(
                programmerSignal = "VCC / GND",
                bridgeComponent = "توصيل مباشر",
                mcuTarget = "VDD / VSS",
                accentColor = Color(0xFFEF4444),
                subNote = "الجهد الموصى به: $voltage مع مكثف REGC عند الحاجة"
            )
        }
    }
}

@Composable
private fun SchematicTableRow(
    programmerSignal: String,
    bridgeComponent: String,
    mcuTarget: String,
    accentColor: Color,
    subNote: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = programmerSignal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = bridgeComponent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = mcuTarget,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = subNote,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}
