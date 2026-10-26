package com.example.ui.components

import androidx.compose.foundation.background
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
                text = "يتوفر تحديث جديد للتطبيق!",
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
                            text = "الحالي: v${updateInfo.currentVersionName}",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

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
                                text = "ما الجديد في هذا التحديث:",
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

                if (updateInfo.forceUpdate) {
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
            Button(
                onClick = {
                    UpdateChecker.downloadAndInstallUpdate(context, updateInfo)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تحميل وتثبيت التحديث الآن",
                    fontWeight = FontWeight.Bold
                )
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
        mutableStateOf(currentInfo?.releaseNotes ?: "• إضافة مواتير وضفائر جديدة\n• تحسين أداء وسرعة التطبيق")
    }
    var forceUpdateInput by remember {
        mutableStateOf(currentInfo?.forceUpdate ?: false)
    }
    var isPublishing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "نشر تحديث جديد للتطبيق",
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
                    text = "عند نشر التحديث هنا، سيظهر إشعار التحديث تلقائياً في هواتف جميع الفنيين والمستخدمين فور فتح التطبيق.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = versionCodeInput,
                        onValueChange = { versionCodeInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الإصدار (رقمي)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = versionNameInput,
                        onValueChange = { versionNameInput = it },
                        label = { Text("اسم الإصدار (مثال 1.1)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = downloadUrlInput,
                    onValueChange = { downloadUrlInput = it },
                    label = { Text("رابط تحميل الـ APK (Google Drive, Mediafire, etc)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = versionCodeInput.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)
                    if (downloadUrlInput.isBlank()) {
                        android.widget.Toast.makeText(context, "يرجى إدخال رابط تحميل الـ APK", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPublishing = true
                    coroutineScope.launch {
                        val result = UpdateChecker.publishUpdate(
                            versionCode = code,
                            versionName = versionNameInput.ifBlank { "1.1" },
                            downloadUrl = downloadUrlInput,
                            releaseNotes = releaseNotesInput,
                            forceUpdate = forceUpdateInput,
                            adminEmail = adminEmail
                        )
                        isPublishing = false
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(
                                context,
                                "تم نشر التحديث بنجاح! سيظهر لجميع المستخدمين الآن 🎉",
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
                    Text("جاري النشر لجميع الهواتف...")
                } else {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نشر التحديث لجميع الهواتف 🚀", fontWeight = FontWeight.Bold)
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
