package com.example.ui.screens.programmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProgrammerChipType
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.NeuBadge
import com.example.ui.theme.NeuPillButton
import com.example.ui.theme.neuCard
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
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Supported USB-to-TTL Adapters Header + Refresh
        Card(
            shape = RoundedCornerShape(if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = if (isNeu) Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark) else Modifier
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
                                        if (isNeu) {
                                            NeuBadge(
                                                text = "متصلة الآن",
                                                accentColor = Color(0xFF10B981),
                                                isDark = isDark
                                            )
                                        } else {
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

        // Next & Skip Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isNeu) {
                NeuPillButton(
                    onClick = onSkipStep,
                    isDark = isDark,
                    modifier = Modifier
                        .weight(0.35f)
                        .heightIn(min = 50.dp)
                        .testTag("step2_skip_button")
                ) {
                    Text("تخطي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                NeuPillButton(
                    onClick = onNextStep,
                    isActive = true,
                    isDark = isDark,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(0.65f)
                        .heightIn(min = 50.dp)
                        .testTag("step2_next_button")
                ) {
                    Text(
                        text = "التالي: فتح واجهة البرمجة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
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
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
