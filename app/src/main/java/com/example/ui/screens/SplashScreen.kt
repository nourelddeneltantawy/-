package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import com.example.ui.components.ThemeSettingsDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.rememberCoroutineScope
import com.example.BuildConfig
import com.example.data.ContactInfoManager
import com.example.data.model.AppUpdateInfo
import com.example.data.model.ContactInfo
import com.example.ui.components.PhoneDownloadIcon
import com.example.ui.components.PublishUpdateDialog
import com.example.ui.components.UpdateDialog
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onReady: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    isAdmin: Boolean = false,
    currentUserEmail: String? = null,
    onAuthClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (onBackClick != null) {
        BackHandler {
            onBackClick()
        }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAdminPublishDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFacebookFollowDialog by remember { mutableStateOf(false) }
    var activeUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var hasCheckedUpdateOnLaunch by remember { mutableStateOf(false) }

    val userPrefs = remember { context.getSharedPreferences("albaraa_user_prefs", Context.MODE_PRIVATE) }
    var hasFollowedFacebook by remember { mutableStateOf(userPrefs.getBoolean("facebook_followed", false)) }

    LaunchedEffect(Unit) {
        ContactInfoManager.init(context)
        ContactInfoManager.fetchRemote(context)
        // Background check for update info
        try {
            val update = UpdateChecker.checkUpdate(context)
            if (update != null) {
                activeUpdateInfo = update
            }
            hasCheckedUpdateOnLaunch = true
        } catch (_: Throwable) {}
    }

    val contactInfo by ContactInfoManager.contactInfo.collectAsState()

    val handleUpdateIconClick = {
        coroutineScope.launch {
            if (isAdmin) {
                showAdminPublishDialog = true
            } else {
                Toast.makeText(context, "جاري فحص بيانات التحديث...", Toast.LENGTH_SHORT).show()
                val update = try { UpdateChecker.checkUpdate(context, ignoreAcknowledged = true) } catch (_: Throwable) { null }
                val currentCode = BuildConfig.VERSION_CODE
                val currentName = BuildConfig.VERSION_NAME.trim()

                if (update != null && update.downloadUrl.isNotBlank() &&
                    (update.remoteVersionCode != currentCode || update.remoteVersionName.trim() != currentName)) {
                    activeUpdateInfo = update
                    showUpdateDialog = true
                    // Directly trigger update link/installer as requested by the user
                    UpdateChecker.downloadAndInstallUpdate(context, update)
                } else {
                    Toast.makeText(
                        context,
                        "أنت تستخدم أحدث إصدار من التطبيق (v$currentName) 🎉",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP 48% DEEP ELECTRIC BLUE HERO CURVE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.48f)
                .clip(RoundedCornerShape(bottomStart = 56.dp, bottomEnd = 56.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0284C7), // Bright Cyan Blue
                            Color(0xFF0369A1)  // Deep Blue
                        )
                    )
                )
        )

        // MAIN CONTENT CONTAINER
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Top Bar: Admin/Guest badge & Action Icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start side: Role Badge & Admin Edit Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAdmin) Color(0xFF0284C7).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isAdmin) "🛡️ مشرف" else "وضع الزائر",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    if (isAdmin) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { showEditDialog = true }
                                .testTag("admin_edit_contact_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل بيانات التواصل",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // End side: Custom Sleek Navy Container with Action Icons
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0B1727).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Phone with Download Arrow (تنزيل / إدارة التحديث)
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { handleUpdateIconClick() },
                                modifier = Modifier.size(42.dp).testTag("splash_download_update_button")
                            ) {
                                PhoneDownloadIcon(tint = Color(0xFF38BDF8))
                            }

                            if (activeUpdateInfo?.hasUpdate == true) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(9.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                        .border(1.5.dp, Color(0xFF0B1727), CircleShape)
                                )
                            }
                        }

                        // Small vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // 2. Door with Arrow (تسجيل الدخول / الخروج)
                        IconButton(
                            onClick = {
                                if (currentUserEmail != null) {
                                    showLogoutConfirmDialog = true
                                } else {
                                    onAuthClick()
                                }
                            },
                            modifier = Modifier.size(42.dp).testTag("splash_auth_button")
                        ) {
                            Icon(
                                imageVector = if (currentUserEmail != null) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                contentDescription = if (currentUserEmail != null) "تسجيل الخروج" else "تسجيل الدخول",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Small vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // 3. App Theme & Appearance Settings (السمة وإعدادات التطبيق)
                        IconButton(
                            onClick = { showThemeDialog = true },
                            modifier = Modifier.size(42.dp).testTag("splash_theme_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "إعدادات المظهر والسمة",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // White Circular Frame holding user's custom 3D Logo
            Surface(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .border(3.5.dp, Color.White, CircleShape),
                color = Color.White
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_albaraa),
                    contentDescription = "شعار البراء إلكترونيك",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Engineer Name & Title
            Text(
                text = contactInfo.engineerName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = contactInfo.titleSubtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFE0F2FE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Middle Floating Card (Workshop Name & Address)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .shadow(10.dp, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = contactInfo.centerName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openMapLocation(context, contactInfo.locationUrl) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = contactInfo.address,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "الموقع الجغرافي",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Enter Button (الدخول إلى الدليل الشامل)
            Button(
                onClick = {
                    if (!hasFollowedFacebook && !isAdmin) {
                        showFacebookFollowDialog = true
                    } else {
                        onReady()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .height(54.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .testTag("enter_app_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0284C7),
                                    Color(0xFF0369A1)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "الدخول إلى الدليل الشامل",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Bottom 3 Quick Actions: واتس أب - الموقع - فيسبوك (بدلاً من اتصال)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. واتس أب (WhatsApp)
                ContactActionCircle(
                    title = "واتس أب",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    iconTint = Color(0xFF16A34A),
                    circleBackground = Color(0xFFDCFCE7),
                    onClick = { openWhatsApp(context, contactInfo.whatsappNumber) }
                )

                // 2. الموقع (Location / Maps)
                ContactActionCircle(
                    title = "الموقع",
                    icon = Icons.Default.Place,
                    iconTint = Color(0xFFEF4444),
                    circleBackground = Color(0xFFFFE4E6),
                    onClick = { openMapLocation(context, contactInfo.locationUrl) }
                )

                // 3. فيسبوك (Facebook Page - بدل اتصال تماماً)
                ContactActionCircle(
                    title = "فيسبوك",
                    painter = painterResource(id = R.drawable.ic_facebook),
                    iconTint = Color(0xFF1877F2),
                    circleBackground = Color(0xFFE7F3FF),
                    onClick = { openFacebookPage(context, contactInfo.facebookUrl) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Edit Contact Details Dialog (Admins only)
        if (isAdmin && showEditDialog) {
            EditContactDialog(
                currentInfo = contactInfo,
                onDismiss = { showEditDialog = false },
                onSave = { updated ->
                    ContactInfoManager.updateContactInfo(context, updated) { success ->
                        Toast.makeText(
                            context,
                            if (success) "تم حفظ وتحديث بيانات التواصل بنجاح" else "تم الحفظ محلياً",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showEditDialog = false
                }
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                title = { Text("تسجيل الخروج", fontWeight = FontWeight.Bold) },
                text = { Text("هل تود بالتأكيد تسجيل الخروج من الحساب (${currentUserEmail ?: ""})؟") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            onSignOutClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("تسجيل الخروج")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text("إلغاء")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Regular User Update Dialog
        if (showUpdateDialog && activeUpdateInfo != null) {
            UpdateDialog(
                updateInfo = activeUpdateInfo!!,
                onDismiss = { showUpdateDialog = false },
                isAdmin = isAdmin,
                userEmail = currentUserEmail,
                onOpenPublishDialog = {
                    showUpdateDialog = false
                    showAdminPublishDialog = true
                }
            )
        }

        // Admin Publish Update Dialog
        if (showAdminPublishDialog) {
            PublishUpdateDialog(
                currentInfo = activeUpdateInfo,
                adminEmail = currentUserEmail ?: "admin",
                onDismiss = { showAdminPublishDialog = false },
                onPublished = {
                    showAdminPublishDialog = false
                    coroutineScope.launch {
                        activeUpdateInfo = try { UpdateChecker.checkUpdate(context, ignoreAcknowledged = true) } catch (_: Throwable) { null }
                    }
                }
            )
        }

        // Theme & Appearance Settings Dialog (Available for All: Admin & Guest)
        if (showThemeDialog) {
            ThemeSettingsDialog(
                onDismiss = { showThemeDialog = false }
            )
        }

        // First-Time Facebook Follow Requirement Dialog
        if (showFacebookFollowDialog) {
            AlertDialog(
                onDismissRequest = { /* Require user interaction */ },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE7F3FF),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_facebook),
                                contentDescription = "فيسبوك",
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "متابعة صفحة فيسبوك الرسمية",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "أهلاً بك في تطبيق مركز البراء إلكترونيك للصيانة المتطورة!\n\nللدخول إلى الدليل الشامل لأول مرة، يرجى متابعة صفحتنا الرسمية على فيسبوك لتصلك كافة المخططات والتحديثات والشروحات الجديدة أولاً بأول.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openFacebookPage(context, contactInfo.facebookUrl)
                                userPrefs.edit().putBoolean("facebook_followed", true).apply()
                                hasFollowedFacebook = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_facebook),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "متابعة الصفحة على فيسبوك 👍",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (hasFollowedFacebook) {
                            Button(
                                onClick = {
                                    showFacebookFollowDialog = false
                                    onReady()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "تمت المتابعة، الدخول إلى الدليل الشامل ➔",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                dismissButton = null,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun ContactActionCircle(
    title: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconTint: Color,
    circleBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = circleBackground,
            modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                } else if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
fun EditContactDialog(
    currentInfo: ContactInfo,
    onDismiss: () -> Unit,
    onSave: (ContactInfo) -> Unit
) {
    var engineerName by remember { mutableStateOf(currentInfo.engineerName) }
    var titleSubtitle by remember { mutableStateOf(currentInfo.titleSubtitle) }
    var centerName by remember { mutableStateOf(currentInfo.centerName) }
    var address by remember { mutableStateOf(currentInfo.address) }
    var phoneNumber by remember { mutableStateOf(currentInfo.phoneNumber) }
    var whatsappNumber by remember { mutableStateOf(currentInfo.whatsappNumber) }
    var locationUrl by remember { mutableStateOf(currentInfo.locationUrl) }
    var facebookUrl by remember { mutableStateOf(currentInfo.facebookUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "تعديل بيانات التواصل والعنوان",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = engineerName,
                    onValueChange = { engineerName = it },
                    label = { Text("اسم المهندس / صاحب المركز") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = titleSubtitle,
                    onValueChange = { titleSubtitle = it },
                    label = { Text("المسمى / التخصص (تحت الاسم)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = centerName,
                    onValueChange = { centerName = it },
                    label = { Text("اسم المركز / النشاط (داخل البطاقة)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان التفصيلي") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("رقم الهاتف") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = { whatsappNumber = it },
                    label = { Text("رقم الواتساب (مثال: 201018717141)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = facebookUrl,
                    onValueChange = { facebookUrl = it },
                    label = { Text("رابط صفحة الفيسبوك الرسمية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = locationUrl,
                    onValueChange = { locationUrl = it },
                    label = { Text("رابط خرائط جوجل (Google Maps)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ContactInfo(
                            engineerName = engineerName.trim(),
                            titleSubtitle = titleSubtitle.trim(),
                            centerName = centerName.trim(),
                            address = address.trim(),
                            phoneNumber = phoneNumber.trim(),
                            whatsappNumber = whatsappNumber.trim(),
                            locationUrl = locationUrl.trim(),
                            facebookUrl = facebookUrl.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun openFacebookPage(context: Context, rawUrl: String) {
    try {
        val target = if (rawUrl.isNotBlank()) rawUrl.trim() else "https://facebook.com/AlBaraa01018717141Electronic"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح صفحة فيسبوك", Toast.LENGTH_SHORT).show()
    }
}

private fun openWhatsApp(context: Context, rawNumber: String) {
    try {
        val clean = rawNumber.filter { it.isDigit() }
        val formatted = when {
            clean.startsWith("00") -> clean.substring(2)
            clean.startsWith("+") -> clean.substring(1)
            clean.startsWith("01") -> "20" + clean.substring(1)
            else -> clean
        }
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=" + Uri.encode("السلام عليكم م. نور الدين، أود الاستفسار بخصوص صيانة كارتة / موتور..."))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح تطبيق واتساب", Toast.LENGTH_SHORT).show()
    }
}

private fun openMapLocation(context: Context, locationUrl: String) {
    try {
        val validUrl = if (locationUrl.isNotBlank()) locationUrl else "https://maps.google.com/?q=30.7865,31.0004"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح خرائط جوجل", Toast.LENGTH_SHORT).show()
    }
}

private fun makePhoneCall(context: Context, rawNumber: String) {
    try {
        val clean = rawNumber.trim()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح لوحة الاتصال", Toast.LENGTH_SHORT).show()
    }
}
