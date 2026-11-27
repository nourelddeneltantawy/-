package com.example.adminlab

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

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
    val logListState = rememberLazyListState()
    var notifPrompted by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(uiState.isBusy) {
        if (uiState.isBusy &&
            !notifPrompted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPrompted = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(uiState.isBusy) {
        val window = context.findProberActivity()?.window
        if (uiState.isBusy) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
                            text = "مختبر المشرفين: مكتشف البروتوكول التلقائي",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "محرك المبرمجات الشامل (USBDM • ST-LINK • USBasp • PICkit • FT232/UART)",
                            fontSize = 11.sp,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة")
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
                            Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشرف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // USB Status Card
                UsbStatusHeaderCard(
                    status = uiState.usbStatus,
                    isBusy = uiState.isBusy,
                    onRefreshOrGrant = {
                        val refreshed = engine.refreshUsbStatus()
                        if (refreshed.isConnected && !refreshed.hasPermission) {
                            engine.requestUsbPermission()
                        } else if (!refreshed.isConnected) {
                            Toast.makeText(context, "تأكد من توصيل محول USB OTG", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "المبرمجة متصلة وجاهزة", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Family Selector Dropdown
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "نطاق الاكتشاف أو العائلة المستهدفة",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
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
                                    .clickable(enabled = !uiState.isBusy) { showFamilyDropdown = true }
                                    .testTag("prober_family_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(uiState.selectedFamily.labelAr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "اختيار العائلة")
                                }
                            }
                            DropdownMenu(expanded = showFamilyDropdown, onDismissRequest = { showFamilyDropdown = false }) {
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

                // 1. PRIMARY ACTION BUTTON + STOP BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { engine.startFullSafeProtocolScan() },
                        enabled = !uiState.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .testTag("start_full_scan_button")
                    ) {
                        if (uiState.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري الفحص العالمي...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بدء الفحص الشامل واستخراج البروتوكول",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = { engine.stopImmediately() },
                        enabled = uiState.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .testTag("stop_probe_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "إيقاف", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيقاف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Current Stage Banner (highlights hardware alerts clearly in ERROR state)
                val isErrorBanner = uiState.phase == ProberStagePhase.ERROR
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isErrorBanner) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (isErrorBanner) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isErrorBanner) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "تنبيه خطأ أو هاردوير",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = uiState.stageBanner,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isErrorBanner) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // BYPASS SYNC BUTTON (shown when Stage 1 sync fails)
                if (uiState.canBypassSync && !uiState.isBusy) {
                    Button(
                        onClick = { engine.bypassSyncAndRunStages2To4() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("bypass_sync_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تجاوز التزامن واختبار الأوامر مباشرة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

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
                                enabled = !uiState.isBusy && uiState.logs.isNotEmpty(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("prober_clear_logs_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF020617), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            if (uiState.logs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "اضغط «بدء الفحص الشامل واستخراج البروتوكول» لمتابعة حزم TX / RX لحظياً...",
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

                // LIVE STEP-BY-STEP CHECKLIST
                LiveChecklistCard(items = uiState.checklist)

                // ==================== 3-CARD BOTTOM RESULTS SECTION ====================
                val summary = uiState.blueprintSummary
                val readText = uiState.readBlueprintText
                val writeText = uiState.writeBlueprintText
                val guideText = uiState.humanGuideText
                val guideJsonText = uiState.humanGuideJsonText

                // TOP ROW: 2 SIDE-BY-SIDE CARDS (Read Blueprint on Right, Write/Erase/Verify on Left in RTL)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Right Card: Read Blueprint
                    ProtocolExportCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("read_blueprint_card"),
                        icon = Icons.Default.Code,
                        accentColor = Color(0xFF0284C7),
                        title = "بروتوكول القراءة والسحب (Read Blueprint)",
                        subtitle = if (readText.isNotBlank()) {
                            "${summary.baudRate ?: "--"} ${summary.parityMode} • ${summary.flashEndAddressHex}"
                        } else {
                            "يتم توليده تلقائياً عند الفحص"
                        },
                        contentText = readText.ifBlank { "{\n  \"status\": \"بانتظار الفحص لاستخراج بروتوكول القراءة...\"\n}" },
                        hasContent = readText.isNotBlank(),
                        isMonospace = true,
                        bodyHeightDp = 210,
                        copyTestTag = "copy_read_blueprint_button",
                        shareJsonTestTag = "share_read_json_button",
                        shareTxtTestTag = "share_read_txt_button",
                        onCopy = { copyToClipboard(context, "ReadProtocolBlueprint", readText, "تم نسخ بروتوكول القراءة والسحب") },
                        onShareJson = { shareExportFile(context, "read_protocol.json", "application/json", readText) },
                        onShareTxt = { shareExportFile(context, "read_protocol.txt", "text/plain", readText) }
                    )

                    // Left Card: Write / Erase / Verify Blueprint
                    ProtocolExportCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("write_blueprint_card"),
                        icon = Icons.Default.Description,
                        accentColor = Color(0xFF059669),
                        title = "بروتوكول الكتابة والمسح والتحقق (Write / Erase / Verify)",
                        subtitle = if (writeText.isNotBlank()) {
                            "Osc: ${summary.detectedCrystalMhz} • EraseWait: ${summary.timingMap.eraseWriteBusyWaitMs}ms"
                        } else {
                            "يتم توليده تلقائياً عند الفحص"
                        },
                        contentText = writeText.ifBlank { "{\n  \"status\": \"بانتظار الفحص لاستخراج بروتوكول الكتابة والمسح...\"\n}" },
                        hasContent = writeText.isNotBlank(),
                        isMonospace = true,
                        bodyHeightDp = 210,
                        copyTestTag = "copy_write_blueprint_button",
                        shareJsonTestTag = "share_write_json_button",
                        shareTxtTestTag = "share_write_txt_button",
                        onCopy = { copyToClipboard(context, "WriteEraseVerifyBlueprint", writeText, "تم نسخ بروتوكول الكتابة والمسح والتحقق") },
                        onShareJson = { shareExportFile(context, "write_protocol.json", "application/json", writeText) },
                        onShareTxt = { shareExportFile(context, "write_protocol.txt", "text/plain", writeText) }
                    )
                }

                // BOTTOM ROW: FULL-WIDTH HUMAN TRANSLATION & MCU IDENTITY CARD
                ProtocolExportCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("human_guide_card"),
                    icon = Icons.Default.MenuBook,
                    accentColor = Color(0xFFD97706),
                    title = "الترجمة البشرية للبروتوكول ومعلومات المعالج الشاملة",
                    subtitle = if (guideText.isNotBlank()) {
                        "${summary.manufacturerGuess} • ${summary.inferredModel}"
                    } else {
                        "بطاقة هوية البروسيسور + شرح خطوات السحب والكتابة والملاحظات الهندسية"
                    },
                    contentText = guideText.ifBlank {
                        "سيظهر هنا التقرير البشري المفصل فور بدء الفحص:\n• بطاقة هوية البروسيسور والشركة والموديل\n• شرح خطوات السحب خطوة بخطوة\n• شرح خطوات الكتابة والمسح والتحقق\n• الملاحظات الهندسية وأكواد الأخطاء"
                    },
                    hasContent = guideText.isNotBlank(),
                    isMonospace = false,
                    bodyHeightDp = 300,
                    copyTestTag = "copy_human_guide_button",
                    shareJsonTestTag = "share_guide_json_button",
                    shareTxtTestTag = "share_guide_txt_button",
                    onCopy = { copyToClipboard(context, "McuHumanGuide", guideText, "تم نسخ الترجمة البشرية للبروتوكول") },
                    onShareJson = { shareExportFile(context, "mcu_guide.json", "application/json", guideJsonText.ifBlank { guideText }) },
                    onShareTxt = { shareExportFile(context, "mcu_guide.txt", "text/plain", guideText) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // SEQUENTIAL STAGE 5 MODAL WARNING DIALOG (NO HOT-SWAP)
    val pendingPrompt = uiState.pendingConfirmDialog
    if (pendingPrompt != null) {
        AlertDialog(
            onDismissRequest = { /* Mandatory decision: cannot dismiss outside or via back press */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "تحذير أمان",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(34.dp)
                )
            },
            title = {
                Text(
                    text = "⚠️ تحذير أمان: إجراء قد يعدّل أو يمسح السوفت وير",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFB91C1C)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الخطوة (${pendingPrompt.stepIndex} من ${pendingPrompt.totalSteps}): ${pendingPrompt.stepTitle}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = pendingPrompt.impactDescription,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { engine.confirmPendingStage5Step() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("stage5_confirm_continue_button")
                ) {
                    Text("استكمال", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { engine.cancelPendingStage5AndFinish() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("stage5_cancel_stop_button")
                ) {
                    Text("إلغاء / توقف إلى هنا", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
private fun ProtocolExportCard(
    modifier: Modifier,
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    contentText: String,
    hasContent: Boolean,
    isMonospace: Boolean,
    bodyHeightDp: Int,
    copyTestTag: String,
    shareJsonTestTag: String,
    shareTxtTestTag: String,
    onCopy: () -> Unit,
    onShareJson: () -> Unit,
    onShareTxt: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.4.dp, if (hasContent) accentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3 Action Buttons in Header Bar: Copy | Share .JSON | Share .TXT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = hasContent,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(copyTestTag)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ النص", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("نسخ", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onShareJson,
                    enabled = hasContent,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(shareJsonTestTag)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة كملف JSON", tint = accentColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onShareTxt,
                    enabled = hasContent,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(shareTxtTestTag)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة كملف TXT", tint = accentColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TXT", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bodyHeightDp.dp)
                    .background(
                        color = if (isMonospace) Color(0xFF0F172A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = contentText,
                    fontSize = if (isMonospace) 10.sp else 12.sp,
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                    color = if (isMonospace) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface,
                    lineHeight = if (isMonospace) 14.sp else 18.sp
                )
            }
        }
    }
}

@Composable
private fun UsbStatusHeaderCard(
    status: ProberUsbStatus,
    isBusy: Boolean,
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.18f), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Usb, contentDescription = "USB", tint = accent, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(status.adapterName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (status.isConnected) {
                            "${status.chipModel} • ${status.vidPidHex} • ${status.programmerCategory}"
                        } else {
                            "${status.chipModel} • ${status.vidPidHex}"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (status.isConnected && status.endpointsSummary != "--") {
                        Text(
                            text = "USB Endpoints: ${status.endpointsSummary}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = when {
                            status.isConnected && status.hasPermission -> "USB متصل وجاهز (${status.programmerCategory})"
                            status.isConnected -> "متصل — اضغط لمنح الصلاحية"
                            else -> "وصّل أي مبرمجة (USBDM / ST-LINK / USBasp / PICkit / FT232) عبر OTG"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
            OutlinedButton(
                onClick = onRefreshOrGrant,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("prober_usb_refresh_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (status.isConnected && !status.hasPermission) "صلاحية" else "تحديث", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LiveChecklistCard(items: List<ChecklistStepItem>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_checklist_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "قائمة الفحص الحية (Live Step-by-Step Checklist)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            items.forEach { step ->
                val (bg, fg, icon) = when (step.status) {
                    ChecklistStepStatus.PENDING -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Shield)
                    ChecklistStepStatus.RUNNING -> Triple(Color(0xFF0284C7).copy(alpha = 0.12f), Color(0xFF0284C7), Icons.Default.HourglassTop)
                    ChecklistStepStatus.PASSED -> Triple(Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF059669), Icons.Default.CheckCircle)
                    ChecklistStepStatus.FAILED -> Triple(Color(0xFFEF4444).copy(alpha = 0.10f), Color(0xFFDC2626), Icons.Default.Cancel)
                    ChecklistStepStatus.AWAITING_SAFETY,
                    ChecklistStepStatus.BYPASSED -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), Icons.Default.Warning)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bg,
                    border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${step.stageTag}: ${step.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = step.status.badgeText, tint = fg, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(step.status.badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                            }
                        }
                        if (step.detail.isNotBlank()) {
                            Text(
                                text = step.detail,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = fg
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String, toastMsg: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}

private fun shareExportFile(context: Context, fileName: String, mimeType: String, content: String) {
    if (content.isBlank()) return
    try {
        val outFile = File(context.cacheDir, fileName)
        outFile.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف: $fileName"))
    } catch (t: Throwable) {
        Toast.makeText(context, "تعذر مشاركة الملف: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}

private tailrec fun Context.findProberActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findProberActivity()
    else -> null
}
