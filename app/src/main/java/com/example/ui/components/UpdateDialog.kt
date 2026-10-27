package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.util.ApkUploader
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    isAdmin: Boolean = false,
    userEmail: String? = null,
    onOpenPublishDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPublishDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableIntStateOf(0) }

    if (showPublishDialog) {
        PublishUpdateDialog(
            currentInfo = updateInfo,
            adminEmail = userEmail ?: "admin",
            onDismiss = { showPublishDialog = false },
            onPublished = {
                showPublishDialog = false
                onDismiss()
            }
        )
    }

    val localApkLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val success = UpdateChecker.installLocalApk(context, uri)
            if (!success) {
                android.widget.Toast.makeText(context, "تعذر تثبيت الملف، تأكد من صحة ملف الـ APK", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (updateInfo.hasUpdate) "يتوفر تحديث جديد للتطبيق!" else "أنت تستخدم أحدث إصدار 🎉",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Version Badges Comparison
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Version
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "الإصدار الحالي: v${updateInfo.currentVersionName}",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (updateInfo.hasUpdate) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )

                        // New Version
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "الجديد: v${updateInfo.remoteVersionName}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Release Notes Card
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (updateInfo.hasUpdate) "ما الجديد في هذا التحديث:" else "ملاحظات الإصدار:",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (updateInfo.forceUpdate && updateInfo.hasUpdate) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ هذا التحديث ضروري للمتابعة وضمان استقرار البيانات.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // In-App Download Progress Banner
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جاري تحميل التحديث وتجهيزه للتثبيت...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "$downloadPercent%",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadPercent / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF1E293B)
                            )
                        }
                    }
                }

                // Option to install local file received on phone (WhatsApp, Bluetooth, etc.)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { localApkLauncher.launch("application/vnd.android.package-archive") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📁 تثبيت ملف APK تم استلامه على الهاتف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Admin Publish/Edit Update Button
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showPublishDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تعديل أو نشر تحديث جديد (للمشرفين)", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (updateInfo.hasUpdate) {
                Button(
                    onClick = {
                        val isDirectWeb = updateInfo.downloadUrl.startsWith("http://", ignoreCase = true) ||
                                updateInfo.downloadUrl.startsWith("https://", ignoreCase = true)

                        if (isDirectWeb) {
                            isDownloading = true
                            coroutineScope.launch {
                                val result = UpdateChecker.downloadApkToFile(context, updateInfo.downloadUrl) { pct ->
                                    downloadPercent = pct
                                }
                                isDownloading = false
                                if (result.isSuccess) {
                                    val apkFile = result.getOrNull()!!
                                    val opened = UpdateChecker.installDownloadedApkFile(context, apkFile)
                                    if (!opened) {
                                        UpdateChecker.downloadAndInstallUpdate(context, updateInfo)
                                    }
                                } else {
                                    UpdateChecker.downloadAndInstallUpdate(context, updateInfo)
                                }
                            }
                        } else {
                            UpdateChecker.downloadAndInstallUpdate(context, updateInfo)
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري تنزيل التحديث ($downloadPercent%)...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (updateInfo.downloadUrl.startsWith("http", ignoreCase = true)) "تحميل وتثبيت التحديث الآن 🚀" else "طلب ملف التحديث عبر واتساب 💬",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = { localApkLauncher.launch("application/vnd.android.package-archive") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "اختيار وتثبيت ملف APK من الهاتف",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (updateInfo.downloadUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            UpdateChecker.copyToClipboard(context, updateInfo.downloadUrl)
                            android.widget.Toast.makeText(context, "تم نسخ رابط التحميل!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ الرابط")
                    }
                }

                if (!updateInfo.forceUpdate) {
                    TextButton(onClick = onDismiss) {
                        Text("تذكيري لاحقاً")
                    }
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}

/**
 * Custom Phone with Download Arrow Icon matching user reference image.
 */
@Composable
fun PhoneDownloadIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF38BDF8)
) {
    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 30.dp)
                .border(2.2.dp, tint, RoundedCornerShape(5.dp))
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Dialog enabling Admins to broadcast an app update to all installed devices via Firestore.
 */
@Composable
fun PublishUpdateDialog(
    currentInfo: AppUpdateInfo?,
    adminEmail: String,
    onDismiss: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var versionCodeInput by remember {
        mutableStateOf(
            ((currentInfo?.remoteVersionCode?.coerceAtLeast(BuildConfig.VERSION_CODE) ?: BuildConfig.VERSION_CODE) + 1).toString()
        )
    }
    var versionNameInput by remember {
        mutableStateOf(currentInfo?.remoteVersionName?.takeIf { it.isNotBlank() } ?: "1.1.0")
    }
    var downloadUrlInput by remember {
        mutableStateOf(currentInfo?.downloadUrl ?: "")
    }
    var releaseNotesInput by remember {
        mutableStateOf(currentInfo?.releaseNotes?.takeIf { it.isNotBlank() } ?: "• إضافة مواتير وضفائر غسالات جديدة\n• مخططات تفاعلية لسوكيتات الكارتة والموتور\n• تحسينات عامة وسرعة فائقة في المزامنة")
    }
    var forceUpdateInput by remember {
        mutableStateOf(currentInfo?.forceUpdate ?: false)
    }
    var isPublishing by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("جاري النشر لجميع الهواتف...") }
    var uploadPercent by remember { mutableIntStateOf(0) }
    var selectedLocalApkUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedLocalApkName by remember { mutableStateOf<String?>(null) }

    val apkFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            selectedLocalApkUri = uri
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "AlbaraaElectronic.apk"
            selectedLocalApkName = fileName
            android.widget.Toast.makeText(context, "تم اختيار الملف: $fileName (سيتم رفعه وتوزيعه تلقائياً)", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PhoneDownloadIcon(tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        title = {
            Text(
                text = "إدارة ونشر وتثبيت تحديث التطبيق (APK)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "اختر ملف الـ APK مباشرة من هاتفك لنشره أو تثبيته، بدون الحاجة لأي رابط خارجي:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )

                // 1. Direct File Picker from Phone Storage (PRIMARY FIRST-CLASS OPTION)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedLocalApkName != null) Color(0xFF065F46).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = if (selectedLocalApkName != null) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اختيار ملف APK التحديث من ذاكرة الموبايل",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Button(
                            onClick = { apkFilePicker.launch("application/vnd.android.package-archive") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedLocalApkName != null) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedLocalApkName != null) "تغيير ملف الـ APK المختار" else "📁 تصفح واختر ملف APK من هاتفك",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (selectedLocalApkName != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF064E3B).copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "✓ تم اختيار الملف بنجاح: $selectedLocalApkName",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                    )
                                    Text(
                                        text = "سيتم رفع الملف مباشرة للسيرفر (مثل رفع PDF) ليقوم التطبيق بتثبيته تلقائياً على كل الهواتف!",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f))
                                    )

                                    if (isPublishing) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(uploadStatusText, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                                                Text("$uploadPercent%", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                                            }
                                            LinearProgressIndicator(
                                                progress = { uploadPercent / 100f },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = Color(0xFF10B981),
                                                trackColor = Color(0xFF1E293B)
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Share & Direct Install Buttons for the local APK
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedLocalApkUri?.let { uri ->
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.android.package-archive"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, "إرسال ملف الـ APK عبر واتساب أو درايف"))
                                            } catch (_: Throwable) {
                                                android.widget.Toast.makeText(context, "تعذر فتح قائمة المشاركة", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("📲 مشاركة الملف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(installIntent)
                                            } catch (_: Throwable) {
                                                android.widget.Toast.makeText(context, "يرجى السماح بتثبيت التطبيقات غير المعروفة", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                    ) {
                                        Text("⚙️ تثبيت الـ APK الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Version Code & Version Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = versionCodeInput,
                        onValueChange = { versionCodeInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الإصدار (Code)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = versionNameInput,
                        onValueChange = { versionNameInput = it },
                        label = { Text("اسم الإصدار (مثال v1.1)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 2. APK Download URL (Completely Optional)
                var showOptionalLinkField by remember { mutableStateOf(downloadUrlInput.isNotBlank() && !downloadUrlInput.startsWith("ملف APK")) }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showOptionalLinkField = !showOptionalLinkField }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showOptionalLinkField) "▼ رابط تحميل خارجي (اختياري)" else "▶ إضافة رابط تحميل خارجي (اختياري)",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        )
                        if (downloadUrlInput.isNotBlank() && !downloadUrlInput.startsWith("ملف APK")) {
                            Text("✓ موجود", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981)))
                        }
                    }
                }

                if (showOptionalLinkField) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = downloadUrlInput,
                            onValueChange = { downloadUrlInput = it },
                            label = { Text("رابط Google Drive أو Mediafire أو رابط مباشر") },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!clip.isNullOrBlank()) {
                                            downloadUrlInput = clip.trim()
                                            android.widget.Toast.makeText(context, "تم لصق الرابط من الحافظة!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "الحافظة فارغة", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Throwable) {}
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("📋 لصق من الحافظة", fontSize = 11.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = releaseNotesInput,
                    onValueChange = { releaseNotesInput = it },
                    label = { Text("ما الجديد في التحديث (الملاحظات)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تحديث إجباري (إلزام المستخدمين بالتحديث)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = forceUpdateInput,
                        onCheckedChange = { forceUpdateInput = it }
                    )
                }

                // Test download current APK button
                if (downloadUrlInput.isNotBlank() && !downloadUrlInput.startsWith("ملف APK")) {
                    OutlinedButton(
                        onClick = {
                            val dummyInfo = AppUpdateInfo(
                                currentVersionCode = BuildConfig.VERSION_CODE,
                                currentVersionName = BuildConfig.VERSION_NAME,
                                remoteVersionCode = versionCodeInput.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1),
                                remoteVersionName = versionNameInput,
                                downloadUrl = downloadUrlInput,
                                releaseNotes = releaseNotesInput,
                                forceUpdate = forceUpdateInput,
                                hasUpdate = true
                            )
                            UpdateChecker.downloadAndInstallUpdate(context, dummyInfo)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تجربة تنزيل ملف الـ APK الحالي 📥", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = versionCodeInput.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)
                    val rawUrl = downloadUrlInput.trim()

                    if (rawUrl.isBlank() && selectedLocalApkUri == null) {
                        android.widget.Toast.makeText(context, "يرجى اختيار ملف APK من هاتفك أو إدخال رابط التحديث", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isPublishing = true
                    coroutineScope.launch {
                        var finalUrl = rawUrl
                        if (selectedLocalApkUri != null && (finalUrl.isBlank() || finalUrl.startsWith("ملف APK"))) {
                            uploadStatusText = "جاري رفع ملف الـ APK إلى السيرفر (0%)..."
                            uploadPercent = 0
                            val uploadResult = ApkUploader.uploadApkToStorage(context, selectedLocalApkUri!!, code) { pct ->
                                uploadPercent = pct
                                uploadStatusText = "جاري رفع ملف الـ APK ($pct%)..."
                            }

                            if (uploadResult.isSuccess) {
                                finalUrl = uploadResult.getOrNull()!!
                                uploadStatusText = "تم رفع الملف بنجاح! جاري النشر..."
                            } else {
                                isPublishing = false
                                val errMsg = uploadResult.exceptionOrNull()?.message ?: "خطأ في السيرفر"
                                android.widget.Toast.makeText(
                                    context,
                                    "تعذر رفع ملف APK: $errMsg\nيمكنك بدلاً من ذلك رفع الملف على Google Drive ووضع الرابط في خانة الرابط.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                        }

                        uploadStatusText = "جاري حفظ ونشر التحديث لجميع الهواتف..."
                        val result = UpdateChecker.publishUpdate(
                            versionCode = code,
                            versionName = versionNameInput.ifBlank { "1.1" },
                            downloadUrl = finalUrl,
                            releaseNotes = releaseNotesInput,
                            forceUpdate = forceUpdateInput,
                            adminEmail = adminEmail
                        )
                        isPublishing = false
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(
                                context,
                                "تم رفع ونشر التحديث بنجاح! سيتمكن جميع المستخدمين من تنزيله وتثبيته مباشرة الآن 🎉",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            onPublished()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "حدث خطأ أثناء نشر التحديث: ${result.exceptionOrNull()?.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !isPublishing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(uploadStatusText, fontSize = 12.sp)
                } else {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedLocalApkUri != null) "رفع ونشر ملف الـ APK لجميع الهواتف 🚀" else "نشر التحديث لجميع الهواتف 🚀",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPublishing
            ) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}
