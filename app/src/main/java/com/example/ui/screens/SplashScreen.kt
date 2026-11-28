package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.example.data.AppStatsAndRatingManager
import com.example.data.AppStatsData
import com.example.ui.components.ThemeSettingsDialog
import com.example.ui.theme.FittedSingleLineText
import com.example.ui.theme.GlassCircleIconButton
import com.example.ui.theme.GlassPrimaryCtaButton
import com.example.ui.theme.GlassStatusWellPill
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalIsClayTheme
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsLiquidGlassTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.LocalSecondaryAccentColor
import com.example.ui.theme.NeuActionPill
import com.example.ui.theme.NeuCircleIconButton
import com.example.ui.theme.NeuPrimaryCtaButton
import com.example.ui.theme.NeuStatusWellPill
import com.example.ui.theme.NeuTokens
import com.example.ui.theme.bidiIsolateMixed
import com.example.ui.theme.glassCanvasBackground
import com.example.ui.theme.glassCard
import com.example.ui.theme.glassRecessed
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed
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
import androidx.compose.runtime.mutableIntStateOf
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
    onManageAdminsClick: (() -> Unit)? = null,
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
    var showRatingDialog by remember { mutableStateOf(false) }
    var showMustLoginDialog by remember { mutableStateOf(false) }
    var activeUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var hasCheckedUpdateOnLaunch by remember { mutableStateOf(false) }

    val userPrefs = remember { context.getSharedPreferences("albaraa_user_prefs", Context.MODE_PRIVATE) }
    var hasFollowedFacebook by remember { mutableStateOf(userPrefs.getBoolean("facebook_followed", false)) }

    LaunchedEffect(currentUserEmail) {
        ContactInfoManager.init(context)
        ContactInfoManager.fetchRemote(context)
        AppStatsAndRatingManager.init(context, currentUserEmail)
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
    val statsData by AppStatsAndRatingManager.stats.collectAsState()
    val isClay = LocalIsClayTheme.current
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    val accentColor = LocalAccentColor.current
    val secondaryAccent = LocalSecondaryAccentColor.current

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
            .then(
                when {
                    isNeu -> Modifier.background(NeuTokens.bg(isDark))
                    isGlass -> Modifier.glassCanvasBackground(isDark, accentColor, secondaryAccent)
                    else -> Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        // TOP 48% DYNAMIC THEME HERO CURVE (omitted in Soft Neumorphism & Liquid Glass where hero is a floating card)
        if (!isNeu && !isGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.48f)
                    .clip(RoundedCornerShape(bottomStart = 56.dp, bottomEnd = 56.dp))
                    .background(
                        if (isClay) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFEFF2F6)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    )
                    .then(
                        if (isClay) {
                            Modifier.border(1.2.dp, Color(0xFFD6DFEA), RoundedCornerShape(bottomStart = 56.dp, bottomEnd = 56.dp))
                        } else Modifier
                    )
            )
        }

        // MAIN CONTENT CONTAINER
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(if (isNeu || isGlass) 36.dp else 28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            isNeu -> Modifier
                                .padding(horizontal = 14.dp)
                                .neuCard(cornerRadius = 28.dp, isDark = isDark)
                                .padding(top = 16.dp, bottom = 22.dp, start = 14.dp, end = 14.dp)
                            isGlass -> Modifier
                                .padding(horizontal = 14.dp)
                                .glassCard(cornerRadius = 28.dp, isDark = isDark, accentGlow = accentColor)
                                .padding(top = 16.dp, bottom = 22.dp, start = 14.dp, end = 14.dp)
                            else -> Modifier
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Admin/Guest badge & Action Icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isNeu || isGlass) 4.dp else 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start side: Prominent Back Button (if opened from within app) + Role Badge & Admin Edit Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onBackClick != null) {
                            when {
                                isNeu -> NeuCircleIconButton(
                                    onClick = onBackClick,
                                    size = 40.dp,
                                    isDark = isDark,
                                    modifier = Modifier.testTag("splash_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "رجوع للدليل الشامل",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                isGlass -> GlassCircleIconButton(
                                    onClick = onBackClick,
                                    size = 40.dp,
                                    isDark = isDark,
                                    modifier = Modifier.testTag("splash_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "رجوع للدليل الشامل",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White.copy(alpha = 0.22f), CircleShape)
                                        .testTag("splash_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "رجوع للدليل الشامل",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        val badgeAccent = if (isAdmin) Color(0xFF0284C7) else MaterialTheme.colorScheme.primary
                        when {
                            isNeu -> Box(
                                modifier = Modifier
                                    .neuCard(
                                        cornerRadius = 50.dp,
                                        isDark = isDark,
                                        backgroundColor = NeuTokens.bg(isDark),
                                        accentGlow = badgeAccent.copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = if (isAdmin) "🛡️ مشرف" else "وضع الزائر",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeAccent,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            isGlass -> Box(
                                modifier = Modifier
                                    .glassRecessed(
                                        cornerRadius = 50.dp,
                                        isDark = isDark,
                                        accentBorder = badgeAccent.copy(alpha = 0.45f)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = if (isAdmin) "🛡️ مشرف" else "وضع الزائر",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeAccent,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            else -> Surface(
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
                        }

                        if (isAdmin) {
                            when {
                                isNeu -> NeuCircleIconButton(
                                    onClick = { showEditDialog = true },
                                    size = 38.dp,
                                    isDark = isDark,
                                    modifier = Modifier.testTag("admin_edit_contact_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "تعديل بيانات التواصل",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                isGlass -> GlassCircleIconButton(
                                    onClick = { showEditDialog = true },
                                    size = 38.dp,
                                    isDark = isDark,
                                    modifier = Modifier.testTag("admin_edit_contact_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "تعديل بيانات التواصل",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                else -> Surface(
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
                    }

                    // End side: Custom Sleek Container with Action Icons
                    val iconTintColor = when {
                        isNeu || isGlass || isClay -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF38BDF8)
                    }
                    val dividerColor = when {
                        isNeu -> NeuTokens.shadowDark(isDark).copy(alpha = 0.55f)
                        isGlass -> GlassTokens.specularRim(isDark).copy(alpha = if (isDark) 0.2f else 0.7f)
                        isClay -> Color(0xFFD6DFEA)
                        else -> Color.White.copy(alpha = 0.2f)
                    }
                    val toolbarContent: @Composable () -> Unit = {
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
                                    PhoneDownloadIcon(tint = iconTintColor)
                                }

                                if (activeUpdateInfo?.hasUpdate == true) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(9.dp)
                                            .background(Color(0xFFEF4444), CircleShape)
                                            .border(
                                                1.5.dp,
                                                when {
                                                    isNeu -> NeuTokens.well(isDark)
                                                    isGlass -> GlassTokens.surface(isDark)
                                                    isClay -> Color.White
                                                    else -> Color(0xFF0B1727)
                                                },
                                                CircleShape
                                            )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(dividerColor)
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
                                    tint = iconTintColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(dividerColor)
                            )

                            // 3. App Theme & Appearance Settings (السمة وإعدادات التطبيق)
                            IconButton(
                                onClick = { showThemeDialog = true },
                                modifier = Modifier.size(42.dp).testTag("splash_theme_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "إعدادات المظهر والسمة",
                                    tint = iconTintColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    when {
                        isNeu -> Box(
                            modifier = Modifier.neuRecessed(cornerRadius = 50.dp, isDark = isDark)
                        ) {
                            toolbarContent()
                        }
                        isGlass -> Box(
                            modifier = Modifier.glassRecessed(cornerRadius = 50.dp, isDark = isDark)
                        ) {
                            toolbarContent()
                        }
                        else -> Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isClay) Color(0xFFFFFFFF) else Color(0xFF0B1727).copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isClay) Color(0xFFD6DFEA) else Color(0xFF38BDF8).copy(alpha = 0.35f)
                            ),
                            shadowElevation = 6.dp
                        ) {
                            toolbarContent()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Circular Frame holding user's custom 3D Logo
                when {
                    isNeu -> Box(
                        modifier = Modifier
                            .size(128.dp)
                            .neuCard(cornerRadius = 64.dp, isDark = isDark, backgroundColor = NeuTokens.bg(isDark))
                            .padding(5.dp)
                            .clip(CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_albaraa),
                            contentDescription = "شعار البراء إلكترونيك",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    isGlass -> Box(
                        modifier = Modifier
                            .size(128.dp)
                            .glassCard(cornerRadius = 64.dp, isDark = isDark, accentGlow = accentColor)
                            .padding(5.dp)
                            .clip(CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_albaraa),
                            contentDescription = "شعار البراء إلكترونيك",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> Surface(
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Engineer Name & Title
                Text(
                    text = contactInfo.engineerName.bidiIsolateMixed(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isNeu -> NeuTokens.textPrimary(isDark)
                            isGlass -> GlassTokens.textPrimary(isDark)
                            isClay -> Color(0xFF0F172A)
                            else -> Color.White
                        },
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = contactInfo.titleSubtitle.bidiIsolateMixed(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = when {
                            isNeu -> NeuTokens.textSecondary(isDark)
                            isGlass -> GlassTokens.textSecondary(isDark)
                            isClay -> Color(0xFF475569)
                            else -> Color(0xFFE0F2FE)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isNeu || isGlass) 18.dp else 26.dp))

                // Real Users Count & App Rating
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isNeu || isGlass) 8.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Real Active Users Pill
                    when {
                        isNeu -> NeuStatusWellPill(isDark = isDark) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${statsData.totalUsersCount} مستخدم مسجل بالتطبيق ⚡",
                                color = NeuTokens.textPrimary(isDark),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        isGlass -> GlassStatusWellPill(isDark = isDark) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${statsData.totalUsersCount} مستخدم مسجل بالتطبيق ⚡",
                                color = GlassTokens.textPrimary(isDark),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isClay) Color(0xFFFFFFFF) else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (isClay) Color(0xFFD6DFEA) else Color.White.copy(alpha = 0.25f)),
                            shadowElevation = if (isClay) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = if (isClay) MaterialTheme.colorScheme.primary else Color(0xFF67E8F9),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${statsData.totalUsersCount} مستخدم مسجل بالتطبيق ⚡",
                                    color = if (isClay) Color(0xFF1E293B) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 2. Interactive Real App Rating Badge
                    val openRating = {
                        if (currentUserEmail.isNullOrBlank()) {
                            showMustLoginDialog = true
                        } else {
                            showRatingDialog = true
                        }
                    }
                    when {
                        isNeu -> {
                            val starColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                            NeuStatusWellPill(
                                isDark = isDark,
                                onClick = openRating
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", statsData.averageRating),
                                    color = starColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val fullStars = statsData.averageRating.toInt()
                                for (i in 1..5) {
                                    Icon(
                                        imageVector = if (i <= fullStars) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = starColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (statsData.totalRatingsCount > 0) "(${statsData.totalRatingsCount} تقييم)" else "(كن أول من يقيّم التطبيق ⭐)",
                                    color = NeuTokens.textSecondary(isDark),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (statsData.currentUserRating > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "• تقييمك: ${statsData.currentUserRating}★",
                                        color = Color(0xFF047857),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        isGlass -> {
                            val starColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                            GlassStatusWellPill(
                                isDark = isDark,
                                onClick = openRating
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", statsData.averageRating),
                                    color = starColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val fullStars = statsData.averageRating.toInt()
                                for (i in 1..5) {
                                    Icon(
                                        imageVector = if (i <= fullStars) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = starColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (statsData.totalRatingsCount > 0) "(${statsData.totalRatingsCount} تقييم)" else "(كن أول من يقيّم التطبيق ⭐)",
                                    color = GlassTokens.textSecondary(isDark),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (statsData.currentUserRating > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "• تقييمك: ${statsData.currentUserRating}★",
                                        color = Color(0xFF047857),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        else -> Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isClay) Color(0xFFFFFFFF) else Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, if (isClay) Color(0xFFD6DFEA) else Color(0xFFFDE047).copy(alpha = 0.4f)),
                            shadowElevation = if (isClay) 4.dp else 0.dp,
                            modifier = Modifier.clickable { openRating() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", statsData.averageRating),
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val fullStars = statsData.averageRating.toInt()
                                for (i in 1..5) {
                                    Icon(
                                        imageVector = if (i <= fullStars) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = Color(0xFFFDE047),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (statsData.totalRatingsCount > 0) "(${statsData.totalRatingsCount} تقييم)" else "(كن أول من يقيّم التطبيق ⭐)",
                                    color = if (isClay) Color(0xFF475569) else Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (statsData.currentUserRating > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "• تقييمك: ${statsData.currentUserRating}★",
                                        color = if (isClay) Color(0xFF047857) else Color(0xFF86EFAC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Enter Button (الدخول إلى الدليل الشامل)
            val onEnterAppClick = {
                if (!hasFollowedFacebook && !isAdmin) {
                    showFacebookFollowDialog = true
                } else {
                    onReady()
                }
            }
            when {
                isNeu -> NeuPrimaryCtaButton(
                    onClick = onEnterAppClick,
                    isDark = isDark,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .testTag("enter_app_button")
                ) {
                    Text(
                        text = "الدخول إلى الدليل الشامل",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isGlass -> GlassPrimaryCtaButton(
                    onClick = onEnterAppClick,
                    isDark = isDark,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .testTag("enter_app_button")
                ) {
                    Text(
                        text = "الدخول إلى الدليل الشامل",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> Button(
                    onClick = onEnterAppClick,
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
                                if (isClay) {
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFEA580C),
                                            Color(0xFFF97316)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    )
                                }
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
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Bottom 3 Quick Actions: واتس أب - الموقع - فيسبوك
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
                    isNeu = isNeu,
                    isGlass = isGlass,
                    isDark = isDark,
                    onClick = { openWhatsApp(context, contactInfo.whatsappNumber) }
                )

                // 2. الموقع (Location / Maps)
                ContactActionCircle(
                    title = "الموقع",
                    icon = Icons.Default.Place,
                    iconTint = Color(0xFFEF4444),
                    circleBackground = Color(0xFFFFE4E6),
                    isNeu = isNeu,
                    isGlass = isGlass,
                    isDark = isDark,
                    onClick = { openMapLocation(context, contactInfo.locationUrl) }
                )

                // 3. فيسبوك (Facebook Page)
                ContactActionCircle(
                    title = "فيسبوك",
                    painter = painterResource(id = R.drawable.ic_facebook),
                    iconTint = Color(0xFF1877F2),
                    circleBackground = Color(0xFFE7F3FF),
                    isNeu = isNeu,
                    isGlass = isGlass,
                    isDark = isDark,
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
                onDismiss = { showThemeDialog = false },
                isAdmin = isAdmin,
                onManageAdmins = if (isAdmin) onManageAdminsClick else null
            )
        }

        // Rating Dialog (Logged in user)
        if (showRatingDialog && !currentUserEmail.isNullOrBlank()) {
            var selectedStars by remember { mutableIntStateOf(if (statsData.currentUserRating > 0) statsData.currentUserRating else 5) }
            var reviewText by remember { mutableStateOf(statsData.currentUserReview) }
            var isSubmitting by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isSubmitting) showRatingDialog = false },
                title = {
                    Text(
                        text = "تقييم تطبيق البراء إلكترونيك ⭐",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "رأيك الفني يهمنا لتطوير وتحديث الدليل الشامل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (star in 1..5) {
                                IconButton(
                                    onClick = { selectedStars = star },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "$star نجوم",
                                        tint = if (star <= selectedStars) Color(0xFFEAB308) else Color.Gray,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (selectedStars) {
                                5 -> "ممتاز جداً 🌟🌟🌟🌟🌟"
                                4 -> "جيد جداً 👍"
                                3 -> "جيد 🛠️"
                                2 -> "مقبول ⚠️"
                                else -> "يحتاج تحسين 🔧"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309)),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = reviewText,
                            onValueChange = { reviewText = it },
                            label = { Text("أضف تعليقك أو اقتراحك (اختياري)") },
                            placeholder = { Text("اكتب ملاحظاتك لم. نور الدين...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            isSubmitting = true
                            try {
                                val email = currentUserEmail?.trim() ?: ""
                                val name = if (email.contains("@")) email.substringBefore("@") else email.ifBlank { "مستخدم" }
                                AppStatsAndRatingManager.submitRating(
                                    context = context.applicationContext,
                                    userEmail = email,
                                    userName = name,
                                    rating = selectedStars,
                                    review = reviewText.trim()
                                ) { success ->
                                    isSubmitting = false
                                    showRatingDialog = false
                                    try {
                                        Toast.makeText(
                                            context.applicationContext,
                                            if (success) "شكراً جزيلاً لتقييمك الرائع! 🎉" else "تم حفظ تقييمك بنجاح",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (_: Throwable) {}
                                }
                            } catch (e: Throwable) {
                                isSubmitting = false
                                showRatingDialog = false
                            }
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isSubmitting) "جاري الإرسال..." else "إرسال التقييم")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRatingDialog = false },
                        enabled = !isSubmitting
                    ) {
                        Text("إلغاء")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Must Login Dialog (Guest trying to rate)
        if (showMustLoginDialog) {
            AlertDialog(
                onDismissRequest = { showMustLoginDialog = false },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "تسجيل الحساب مطلوب للتقييم",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "عزيزي الفني/المهندس، التقييم متاح فقط للمستخدمين المسجلين بحساب موثق لضمان جودة ومصداقية التقييمات الفنية. يرجى تسجيل الدخول بحسابك أولاً لإبداء رأيك 🌟",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMustLoginDialog = false
                            onAuthClick()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تسجيل الدخول الآن")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMustLoginDialog = false }) {
                        Text("إلغاء")
                    }
                },
                shape = RoundedCornerShape(20.dp)
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
    isNeu: Boolean = false,
    isGlass: Boolean = false,
    isDark: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            isNeu -> Box(
                modifier = Modifier
                    .size(64.dp)
                    .neuCard(
                        cornerRadius = 32.dp,
                        isDark = isDark,
                        backgroundColor = NeuTokens.bg(isDark),
                        accentGlow = iconTint.copy(alpha = 0.25f)
                    ),
                contentAlignment = Alignment.Center
            ) {
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
            isGlass -> Box(
                modifier = Modifier
                    .size(64.dp)
                    .glassCard(
                        cornerRadius = 32.dp,
                        isDark = isDark,
                        accentGlow = iconTint
                    ),
                contentAlignment = Alignment.Center
            ) {
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
            else -> Surface(
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
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = when {
                    isNeu -> NeuTokens.textPrimary(isDark)
                    isGlass -> GlassTokens.textPrimary(isDark)
                    else -> MaterialTheme.colorScheme.onBackground
                },
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
