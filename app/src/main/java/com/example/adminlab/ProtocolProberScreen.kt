package com.example.adminlab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolProberScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { ProberEngine(context) }
    val uiState by engine.state.collectAsState()

    var showFamilyDropdown by remember { mutableStateOf(false) }
    var pendingDangerousSchool by remember { mutableStateOf<DangerousSchoolType?>(null) }
    var dangerousConfirmStep by remember { mutableIntStateOf(0) } // 0=hidden, 1=step1, 2=step2
    val logListState = rememberLazyListState()

    BackHandler {
        engine.stopImmediately()
        onBackClick()
    }

    DisposableEffect(Unit) {
        engine.refreshUsbStatus()
        onDispose {
            engine.release()
        }
    }

    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(uiState.logs.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_protocol_prober_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مختبر المشرفين: مكتشف البروتوكول",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "فحص السحب والكتابة والمسح الآمن (معزول 100%)",
                            fontSize = 11.sp,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            engine.stopImmediately()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("prober_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة"
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "مختبر المشرفين",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مشرف",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // USB Status Card
                UsbStatusHeaderCard(
                    status = uiState.usbStatus,
                    isProbing = uiState.isProbing,
                    onRefreshOrGrant = {
                        val refreshed = engine.refreshUsbStatus()
                        if (refreshed.isConnected && !refreshed.hasPermission) {
                            engine.requestUsbPermissionIfNeeded()
                        } else if (!refreshed.isConnected) {
                            Toast.makeText(context, "تأكد من توصيل محول USB OTG بالهاتف", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "المبرمجة متصلة وجاهزة للفحص", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // MCU Family Quick Selector
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "عائلة المعالج المتوقعة",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !uiState.isProbing) { showFamilyDropdown = true }
                                    .testTag("prober_family_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.selectedFamily.labelAr,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "اختيار العائلة",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showFamilyDropdown,
                                onDismissRequest = { showFamilyDropdown = false }
                            ) {
                                ProberTargetFamily.entries.forEach { family ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = family.labelAr,
                                                fontWeight = if (family == uiState.selectedFamily) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            engine.setTargetFamily(family)
                                            showFamilyDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Start / Immediate Stop Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { engine.startSafeComprehensiveProbe() },
                        enabled = !uiState.isProbing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("start_safe_probe_button")
                    ) {
                        if (uiState.isProbing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري الفحص الشامل...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بدء الفحص الآمن الشامل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Button(
                        onClick = { engine.stopImmediately() },
                        enabled = uiState.isProbing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .heightIn(min = 54.dp)
                            .testTag("stop_probe_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "إيقاف فوري", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيقاف فوري", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Current Stage Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "الحالة: ${uiState.stageLabel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // SECTION 5: FINAL TECHNICAL REPORT CARD (Copy & Share)
                FinalTechnicalReportCard(
                    result = uiState.result,
                    hasLogs = uiState.logs.isNotEmpty(),
                    onCopyReport = {
                        val report = engine.buildTechnicalReport()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AdminProtocolProberReport", report))
                        Toast.makeText(context, "تم نسخ التقرير الفني بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    onShareReport = {
                        val report = engine.buildTechnicalReport()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "التقرير الفني لمكتشف البروتوكول")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير الفني"))
                    }
                )

                // SECTION 1: 5 SAFE READ EXTRACTION SCHOOLS
                ProbeSectionCard(
                    title = "القسم الأول: مدارس السحب الآمنة (Safe Read Schools)",
                    subtitle = "تُختبر تلقائياً بالترتيب مع تجاهل الصدى — آمنة 100% ولا تعدل الفلاش",
                    accentColor = Color(0xFF0284C7),
                    items = uiState.readSchools,
                    badgeSuccessLabel = "✅ نجحت",
                    badgeFailLabel = "❌ فشلت"
                )

                // SECTION 2: SAFE WRITE / ERASE / BLANK CHECK PROTOCOL PROBER
                ProbeSectionCard(
                    title = "القسم الثاني: مكتشف أوامر الكتابة والمسح الآمن",
                    subtitle = "فحص ترويسة الأمر فقط (Header-Only) — ممنوع إرسال بيانات كتابة أو عنوان مسح",
                    accentColor = Color(0xFF0D9488),
                    items = uiState.writeEraseProbes,
                    badgeSuccessLabel = "✅ مدعوم",
                    badgeFailLabel = "❌ غير مدعوم"
                )

                // LIVE HEX LOG CONSOLE
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "سجل الحزم الحي (TX / RX Hex Log)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                            TextButton(
                                onClick = { engine.clearLogs() },
                                enabled = !uiState.isProbing && uiState.logs.isNotEmpty(),
                                modifier = Modifier.testTag("prober_clear_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "مسح السجل",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color(0xFF020617), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            if (uiState.logs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "اضغط «بدء الفحص الآمن الشامل» لمشاهدة حزم TX / RX لحظياً...",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = logListState,
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(uiState.logs, key = { it.id }) { entry ->
                                        val tint = when (entry.direction) {
                                            ProberLogDirection.TX -> Color(0xFF38BDF8)
                                            ProberLogDirection.RX -> Color(0xFF34D399)
                                            ProberLogDirection.SUCCESS -> Color(0xFF4ADE80)
                                            ProberLogDirection.WARN -> Color(0xFFFBBF24)
                                            ProberLogDirection.ERROR -> Color(0xFFF87171)
                                            ProberLogDirection.INFO -> Color(0xFFCBD5E1)
                                        }
                                        Text(
                                            text = "[${entry.timestamp}] [${entry.direction.name}] ${entry.message}",
                                            color = tint,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 4: DANGEROUS / MODIFICATION SCHOOLS (LOCKED WITH RED WARNING)
                DangerousSchoolsSectionCard(
                    isProbing = uiState.isProbing,
                    onRequestManualRun = { school ->
                        pendingDangerousSchool = school
                        dangerousConfirmStep = 1
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // DOUBLE CONFIRMATION DIALOG FOR EACH DANGEROUS SCHOOL
    val selectedDanger = pendingDangerousSchool
    if (selectedDanger != null && dangerousConfirmStep == 1) {
        AlertDialog(
            onDismissRequest = {
                dangerousConfirmStep = 0
                pendingDangerousSchool = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    text = "تحذير خطر (الخطوة 1 من 2)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "${selectedDanger.title}\n\n${selectedDanger.warningText}\n\nلا تشغّل هذه التجربة اليدوية إلا على بوردة تجارب أو بوجود نسخة احتياطية كاملة.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { dangerousConfirmStep = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier.testTag("danger_confirm_step1")
                ) {
                    Text("متابعة للتأكيد النهائي")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dangerousConfirmStep = 0
                        pendingDangerousSchool = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    } else if (selectedDanger != null && dangerousConfirmStep == 2) {
        AlertDialog(
            onDismissRequest = {
                dangerousConfirmStep = 0
                pendingDangerousSchool = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد نهائي على مسؤوليتك (2 من 2)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "هل تؤكد رغبتك في تنفيذ «${selectedDanger.title}» يدوياً على مسؤوليتك الكاملة؟",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = selectedDanger
                        dangerousConfirmStep = 0
                        pendingDangerousSchool = null
                        engine.executeDangerousSchoolManual(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("danger_confirm_step2")
                ) {
                    Text("نعم، نفّذ على مسؤوليتي")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dangerousConfirmStep = 0
                        pendingDangerousSchool = null
                    }
                ) {
                    Text("تراجع")
                }
            }
        )
    }
}

@Composable
private fun UsbStatusHeaderCard(
    status: ProberUsbStatus,
    isProbing: Boolean,
    onRefreshOrGrant: () -> Unit
) {
    val accent = when {
        status.isConnected && status.hasPermission -> Color(0xFF10B981)
        status.isConnected -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "حالة USB",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = status.adapterName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${status.chipModel} • ${status.vidPidHex}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            status.isConnected && status.hasPermission -> "USB متصل وجاهز (الصلاحية ممنوحة)"
                            status.isConnected -> "متصل — اضغط لمنح صلاحية USB"
                            else -> "قم بتوصيل محول USB-TTL عبر OTG"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }

            OutlinedButton(
                onClick = onRefreshOrGrant,
                enabled = !isProbing,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("prober_usb_refresh_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث حالة USB", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (status.isConnected && !status.hasPermission) "صلاحية" else "تحديث", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FinalTechnicalReportCard(
    result: ProberResult,
    hasLogs: Boolean,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    val (bg, border, icon, tint) = when (result.kind) {
        ProberOutcomeKind.COMPLETED -> Quad(
            Color(0xFF10B981).copy(alpha = 0.11f),
            Color(0xFF10B981),
            Icons.Default.CheckCircle,
            Color(0xFF059669)
        )
        ProberOutcomeKind.LOCKED_BOOTLOADER -> Quad(
            Color(0xFFF59E0B).copy(alpha = 0.14f),
            Color(0xFFD97706),
            Icons.Default.Lock,
            Color(0xFFB45309)
        )
        ProberOutcomeKind.DANGEROUS_MANUAL_RESULT -> Quad(
            Color(0xFFDC2626).copy(alpha = 0.10f),
            Color(0xFFDC2626),
            Icons.Default.Warning,
            Color(0xFFDC2626)
        )
        ProberOutcomeKind.ERROR, ProberOutcomeKind.NO_SYNC -> Quad(
            Color(0xFFEF4444).copy(alpha = 0.10f),
            Color(0xFFDC2626),
            Icons.Default.ErrorOutline,
            Color(0xFFDC2626)
        )
        else -> Quad(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.outlineVariant,
            Icons.Default.Shield,
            MaterialTheme.colorScheme.primary
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.5.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prober_result_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Text(
                    text = result.headline.ifBlank { "بطاقة التقرير الفني النهائي" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = result.detail.ifBlank {
                    "يجمع هذا التقرير سرعة الاتصال ونوع الإطار، مدارس السحب الناجحة (أو حالة القفل)، وأوامر الكتابة والمسح المكتشفة مع تذكير مطابقة Page Size من الداتا شيت."
                },
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.baudRate != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, border.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "السرعة: ${result.baudRate} bps  |  الإطار: ${result.framingType}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = tint
                        )
                        if (result.winningReadMethod.isNotBlank() && result.winningReadMethod != "--") {
                            Text("السحب: ${result.winningReadMethod}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "الكتابة: ${result.supportedWriteCmd}  |  المسح: ${result.supportedEraseCmd}  |  الفراغ: ${result.supportedBlankCheckCmd}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (result.detectedSignature.isNotBlank()) {
                            Text(
                                text = "التوقيع: ${result.detectedSignature}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyReport,
                    enabled = hasLogs || result.headline.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("copy_report_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ التقرير الفني", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ التقرير الفني", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShareReport,
                    enabled = hasLogs || result.headline.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("share_report_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة التقرير", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProbeSectionCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    items: List<SchoolProbeItem>,
    badgeSuccessLabel: String,
    badgeFailLabel: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items.forEach { item ->
                val (statusBg, statusColor, statusLabel, statusIcon) = when (item.status) {
                    ProbeCheckStatus.PASSED -> Quad(
                        Color(0xFF10B981).copy(alpha = 0.14f),
                        Color(0xFF059669),
                        badgeSuccessLabel,
                        Icons.Default.CheckCircle
                    )
                    ProbeCheckStatus.FAILED -> Quad(
                        Color(0xFFEF4444).copy(alpha = 0.12f),
                        Color(0xFFDC2626),
                        badgeFailLabel,
                        Icons.Default.Cancel
                    )
                    ProbeCheckStatus.RUNNING -> Quad(
                        Color(0xFF0284C7).copy(alpha = 0.14f),
                        Color(0xFF0284C7),
                        "جاري الفحص",
                        Icons.Default.HourglassTop
                    )
                    ProbeCheckStatus.PENDING -> Quad(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        "بانتظار الفحص",
                        Icons.Default.Shield
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusBg
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = statusLabel,
                                        tint = statusColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = statusLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (item.commandHex != "--" || item.detail.isNotBlank()) {
                            Text(
                                text = buildString {
                                    if (item.commandHex != "--") append("الأمر: [${item.commandHex}] ")
                                    if (item.detail.isNotBlank()) append("• ${item.detail}")
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerousSchoolsSectionCard(
    isProbing: Boolean,
    onRequestManualRun: (DangerousSchoolType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, Color(0xFFDC2626)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dangerous_schools_section")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "تحذير خطير",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "القسم الثالث: المدارس الخطرة / التعديلية ⚠️",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Text(
                        text = "مغلقة بقفل أمان ولا تعمل تلقائياً — تشغيل يدوي بتأكيد مزدوج فقط",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DangerousSchoolType.entries.forEach { school ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "مقفل بأمان",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "⚠️ ${school.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        Text(
                            text = school.warningText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        OutlinedButton(
                            onClick = { onRequestManualRun(school) },
                            enabled = !isProbing,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFDC2626)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("manual_danger_${school.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تجربة يدوية (على مسؤوليتي)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
