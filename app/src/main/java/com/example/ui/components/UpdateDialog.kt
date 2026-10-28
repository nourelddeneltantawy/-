package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

/**
 * Standard, reliable in-app update dialog.
 * Opens direct download links (MediaFire, Google Drive, direct APK), copies links, or contacts admin via WhatsApp.
 */
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
    var showPublishDialog by remember { mutableStateOf(false) }

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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Version Badges Comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (updateInfo.forceUpdate && updateInfo.hasUpdate) {
                    Text(
                        text = "⚠️ هذا التحديث ضروري للمتابعة وضمان استقرار البيانات.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // Primary Download Action Button (Opens Link / Direct Download)
                if (updateInfo.hasUpdate && updateInfo.downloadUrl.isNotBlank()) {
                    Button(
                        onClick = { UpdateChecker.downloadAndInstallUpdate(context, updateInfo) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تحميل وتثبيت التحديث الآن 🚀",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Acknowledge Button: Mark as updated so it never annoys the user again
                OutlinedButton(
                    onClick = {
                        UpdateChecker.setAcknowledgedVersionCode(context, updateInfo.remoteVersionCode)
                        Toast.makeText(context, "تم التأكيد! لن تظهر رسالة التحديث مجدداً على هذا الجهاز", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("أنا على أحدث إصدار بالفعل (عدم الإظهار مجدداً) ✓", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Admin-only button to modify/publish updates
                if (isAdmin) {
                    OutlinedButton(
                        onClick = { showPublishDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7))
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تعديل أو نشر تحديث جديد (للمشرفين)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
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
                            Toast.makeText(context, "تم نسخ رابط التحميل!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ الرابط", fontSize = 12.sp)
                    }
                }

                if (!updateInfo.forceUpdate) {
                    TextButton(onClick = onDismiss) {
                        Text("تذكيري لاحقاً", fontSize = 12.sp)
                    }
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}

/**
 * Standard Dialog enabling Admins to broadcast an app update via direct download URL.
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
        mutableStateOf(
            currentInfo?.releaseNotes?.takeIf { it.isNotBlank() }
                ?: "• إضافة مواتير وضفائر غسالات جديدة\n• مخططات تفاعلية لسوكيتات الكارتة والموتور\n• تحسينات عامة وسرعة فائقة في المزامنة"
        )
    }
    var forceUpdateInput by remember {
        mutableStateOf(currentInfo?.forceUpdate ?: false)
    }
    var isPublishing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("نشر تحديث جديد للتطبيق", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ضع رابط التحميل المباشر للتحديث (مثل رابط MediaFire أو Google Drive أو موقعك). سيصل التحديث لجميع المستخدمين فوراً:",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                // Version Code & Version Name
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = { Text("اسم الإصدار (مثال v1.1.0)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Direct Download URL
                OutlinedTextField(
                    value = downloadUrlInput,
                    onValueChange = { downloadUrlInput = it },
                    label = { Text("رابط تحميل التحديث (URL مباشر / MediaFire / Drive)") },
                    placeholder = { Text("https://...") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Release Notes
                OutlinedTextField(
                    value = releaseNotesInput,
                    onValueChange = { releaseNotesInput = it },
                    label = { Text("ما الجديد في التحديث (الملاحظات)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )

                // Force Update Switch
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("تحديث إجباري", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("إلزام المستخدمين بالتحديث للمتابعة", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = forceUpdateInput,
                            onCheckedChange = { forceUpdateInput = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = versionCodeInput.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)
                    val name = versionNameInput.trim().ifBlank { "1.1.0" }
                    val url = downloadUrlInput.trim()

                    if (url.isBlank()) {
                        Toast.makeText(context, "برجاء كتابة رابط التحميل", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isPublishing = true
                    coroutineScope.launch {
                        val result = UpdateChecker.publishUpdate(
                            versionCode = code,
                            versionName = name,
                            downloadUrl = url,
                            releaseNotes = releaseNotesInput,
                            forceUpdate = forceUpdateInput,
                            adminEmail = adminEmail,
                            context = context
                        )
                        isPublishing = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "تم نشر التحديث بنجاح! 🎉", Toast.LENGTH_SHORT).show()
                            onPublished()
                        } else {
                            Toast.makeText(context, "حدث خطأ: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isPublishing,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("جاري النشر...")
                } else {
                    Text("🚀 نشر التحديث لجميع الهواتف")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    )
}

/**
 * Phone shape icon with a downward download arrow inside.
 * Matches the requested UI icon for downloading/managing updates.
 */
@Composable
fun PhoneDownloadIcon(
    tint: Color = Color(0xFF38BDF8),
    modifier: Modifier = Modifier.size(24.dp)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 1.8.dp.toPx()
            val w = size.width
            val h = size.height

            // Outer phone body (rounded rectangle)
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.16f, h * 0.08f),
                size = Size(w * 0.68f, h * 0.84f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeW)
            )

            // Inner screen / display area
            drawRoundRect(
                color = tint.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.24f, h * 0.18f),
                size = Size(w * 0.52f, h * 0.62f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Downward arrow in center
            val arrowTop = h * 0.30f
            val arrowBottom = h * 0.64f
            val arrowMidX = w * 0.50f
            val arrowHeadWidth = w * 0.16f

            // Stem
            drawLine(
                color = tint,
                start = Offset(arrowMidX, arrowTop),
                end = Offset(arrowMidX, arrowBottom),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Left arrow wing
            drawLine(
                color = tint,
                start = Offset(arrowMidX - arrowHeadWidth, arrowBottom - arrowHeadWidth),
                end = Offset(arrowMidX, arrowBottom),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Right arrow wing
            drawLine(
                color = tint,
                start = Offset(arrowMidX + arrowHeadWidth, arrowBottom - arrowHeadWidth),
                end = Offset(arrowMidX, arrowBottom),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

