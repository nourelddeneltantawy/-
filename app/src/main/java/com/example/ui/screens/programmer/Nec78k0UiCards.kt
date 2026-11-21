package com.example.ui.screens.programmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.Nec78k0ProtocolEngine
import com.example.util.Nec78k0RecoveryManager

@Composable
fun Nec78k0RescueBannerCard(
    sessions: List<Nec78k0RecoveryManager.Nec78k0RecoverySession>,
    isBusy: Boolean,
    onRescueClick: (Nec78k0RecoveryManager.Nec78k0RecoverySession) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = sessions.firstOrNull() ?: return
    val warningColor = Color(0xFFF59E0B)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = warningColor.copy(alpha = 0.12f)),
        border = BorderStroke(1.3.dp, warningColor.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BuildCircle,
                    contentDescription = "وضع إنقاذ المعالج",
                    tint = warningColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "وضع إنقاذ المعالج (78K0 Recovery)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "جلسة غير مكتملة لـ ${latest.chipName} • ${latest.stage.labelAr}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoBadgePill(
                    label = "الكتل المنجزة",
                    value = "${latest.lastCompletedBlock.coerceAtLeast(0)} / ${latest.blockCount}",
                    accent = warningColor,
                    modifier = Modifier.weight(1f)
                )
                InfoBadgePill(
                    label = "Checksum الأصلي",
                    value = "Full ${latest.fullChecksumHex} • B0 ${latest.block0ChecksumHex}",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.35f)
                )
            }

            Button(
                onClick = { onRescueClick(latest) },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = warningColor, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("nec78k0_rescue_button")
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "استكمال السحب وإنقاذ المعالج (${latest.chipName})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun Nec78k0WatchdogAlertCard(
    alertMessage: String,
    isBusy: Boolean,
    onResumeClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dangerColor = Color(0xFFEF4444)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = dangerColor.copy(alpha = 0.12f)),
        border = BorderStroke(1.3.dp, dangerColor.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "تنبيه انقطاع الاتصال",
                    tint = dangerColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "انقطع الاتصال — ثبّت المبرمجة للاستكمال",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = dangerColor
                )
            }

            Text(
                text = alertMessage,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onResumeClick,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("nec78k0_watchdog_resume_button")
                ) {
                    Text("استكمال السحب الآن", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("nec78k0_watchdog_dismiss_button")
                ) {
                    Text("إخفاء", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Nec78k0ManualResetPromptCard(
    onConfirmResetDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF0284C7)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
        border = BorderStroke(1.5.dp, accent),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "ریسيت يدوي",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مطلوب Reset يدوي لمعالج 78K0",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "تأكد من بقاء طرف FLMD0 على 5V، ثم افصل التغذية وأعد توصيلها أو اضغط زر Reset على البوردة، وبعدها اضغط المتابعة لإرسال نبضة [0x00 ← 5ms ← 0x00].",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmResetDone,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("nec78k0_manual_reset_confirm_button")
                ) {
                    Text("تم عمل Reset — متابعة", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("nec78k0_manual_reset_cancel_button")
                ) {
                    Text("إلغاء", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Nec78k0AutoDetectInfoCard(
    info: Nec78k0ProtocolEngine.Nec78k0DetectedInfo,
    modifier: Modifier = Modifier
) {
    val okGreen = Color(0xFF10B981)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, okGreen.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "بطاقة بيانات المعالج",
                    tint = okGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "بطاقة تعريف المعالج التلقائية (Silicon Signature)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${info.deviceName} • ${info.familyGroup}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = okGreen
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoBadgePill(
                    label = "عنوان النهاية والحجم",
                    value = "${info.lastAddressHex} (${info.flashSizeBytes / 1024} KiB)",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                InfoBadgePill(
                    label = "البلوكات / Data Flash",
                    value = "${info.blockCount} بلوك • ${if (info.hasDataFlash) "يوجد DF" else "لا يوجد"}",
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoBadgePill(
                    label = "Full_Flash_Checksum (0xB0)",
                    value = "0x${info.fullFlashChecksumHex}",
                    accent = okGreen,
                    modifier = Modifier.weight(1f)
                )
                InfoBadgePill(
                    label = "Block0_Checksum (0xB0)",
                    value = "0x${info.block0ChecksumHex}",
                    accent = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun Nec78k0SettingsCard(
    manualResetMode: Boolean,
    isBusy: Boolean,
    onToggleManualReset: (Boolean) -> Unit,
    onAutoDetectNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "إعدادات محرك NEC / Renesas 78K0",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضع الريسيت اليدوي (Manual Reset Mode)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (manualResetMode) "مفعّل: يطلب منك فصل/توصيل البور أو Reset للمبرمجات بدون DTR/RTS"
                            else "تلقائي: FLMD0 (DTR) + RESET (RTS) قبل نبضة [0x00 ← 5ms ← 0x00]",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = manualResetMode,
                        onCheckedChange = onToggleManualReset,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("nec78k0_manual_reset_switch")
                    )
                }
            }

            OutlinedButton(
                onClick = onAutoDetectNow,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("nec78k0_step3_autodetect_button")
            ) {
                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تعرّف تلقائي + التقاط بصمة Checksum (0xC0 / 0xB0)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun InfoBadgePill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent
            )
        }
    }
}
