package com.example.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

data class HubCategoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val circleBackground: Color,
    val onClick: () -> Unit
)

@Composable
fun DashboardHubScreen(
    userEmail: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    isGuest: Boolean,
    motorsCount: Int,
    harnessesCount: Int,
    partsCount: Int,
    onNavigateToMotors: () -> Unit,
    onNavigateToHarnesses: () -> Unit,
    onNavigateToParts: () -> Unit,
    onNavigateToIntegrator: () -> Unit,
    onNavigateToDiagnostics: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onManageAdmins: () -> Unit,
    onAuthClick: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = listOf(
        HubCategoryItem(
            id = "profile",
            title = "مركز البراء والاتصال",
            subtitle = "واتساب • هاتف • العنوان",
            icon = Icons.Default.ContactPhone,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            onClick = onNavigateToProfile
        ),
        HubCategoryItem(
            id = "motors",
            title = "أطراف وتوصيلات المواتير",
            subtitle = "$motorsCount ماتور مسجل",
            icon = Icons.Default.Memory,
            iconTint = Color(0xFFEF4444),
            circleBackground = Color(0xFFFFE4E6),
            onClick = onNavigateToMotors
        ),
        HubCategoryItem(
            id = "harnesses",
            title = "خريطة ضفائر الغسالة",
            subtitle = "$harnessesCount ضفائر كارتة",
            icon = Icons.Default.Cable,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            onClick = onNavigateToHarnesses
        ),
        HubCategoryItem(
            id = "parts",
            title = "لوكات الباب وليفلات الماء",
            subtitle = "$partsCount قطع وحساسات",
            icon = Icons.Default.Lock,
            iconTint = Color(0xFFD97706),
            circleBackground = Color(0xFFFEF3C7),
            onClick = onNavigateToParts
        ),
        HubCategoryItem(
            id = "integrator",
            title = "مخطط التجميع الشامل",
            subtitle = "دمج الضفيرة + الماتور + اللوك",
            icon = Icons.Default.Hub,
            iconTint = Color(0xFF8B5CF6),
            circleBackground = Color(0xFFEDE9FE),
            onClick = onNavigateToIntegrator
        ),
        HubCategoryItem(
            id = "auto_faults",
            title = "أعطال الأوتوماتيك",
            subtitle = "أكواد الأعطال والحلول",
            icon = Icons.Default.LocalLaundryService,
            iconTint = Color(0xFF0D9488),
            circleBackground = Color(0xFFCCFBF1),
            onClick = { onNavigateToDiagnostics("أعطال الأوتوماتيك") }
        ),
        HubCategoryItem(
            id = "top_load_faults",
            title = "الفوق أوتوماتيك",
            subtitle = "الأعطال ومفاتيح الأمان",
            icon = Icons.Default.CleaningServices,
            iconTint = Color(0xFFEA580C),
            circleBackground = Color(0xFFFFEDD5),
            onClick = { onNavigateToDiagnostics("الفوق أوتوماتيك") }
        ),
        HubCategoryItem(
            id = "test_mode",
            title = "تيست مود (Test Mode)",
            subtitle = "خطوات فحص البرامج",
            icon = Icons.Default.Settings,
            iconTint = Color(0xFF7C3AED),
            circleBackground = Color(0xFFF3E8FF),
            onClick = { onNavigateToDiagnostics("تيست مود") }
        ),
        HubCategoryItem(
            id = "bearings",
            title = "رولمان بلي وأولسيه",
            subtitle = "جدول المقاسات والأبعاد",
            icon = Icons.Default.Build,
            iconTint = Color(0xFF059669),
            circleBackground = Color(0xFFD1FAE5),
            onClick = { onNavigateToDiagnostics("رولمان بلي وأولسيه") }
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dashboard_hub_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // TOP HEADER: Curved gradient banner matching the user's screenshot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0284C7),
                                Color(0xFF0369A1)
                            )
                        )
                    )
                    .padding(top = innerPadding.calculateTopPadding() + 8.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToProfile() }
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_albaraa),
                                contentDescription = "شعار البراء إلكترونيك",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مرحباً بك،",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = if (isSuperAdmin) "م. البراء إلكترونيك (Super Admin)" else if (isAdmin) "م. البراء إلكترونيك (مشرف)" else if (isGuest) "الزائر الكريم" else (userEmail ?: "مهندس الصيانة"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Check for updates
                        IconButton(onClick = onCheckForUpdates) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "التحقق من وجود تحديثات",
                                tint = Color.White
                            )
                        }

                        // Manage Admins for Super Admin
                        if (isSuperAdmin) {
                            IconButton(onClick = onManageAdmins) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "إدارة المشرفين",
                                    tint = Color.White
                                )
                            }
                        }

                        // Auth toggle
                        IconButton(onClick = onAuthClick) {
                            Icon(
                                imageVector = if (isGuest) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                                contentDescription = if (isGuest) "تسجيل الدخول" else "تسجيل الخروج",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION TITLE: "أقسام الدليل المتاحة" with vertical accent bar (matching screenshot!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD97706)) // Orange accent bar
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "أقسام الدليل المتاحة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // GRID OF SECTION CARDS (matching screenshot aesthetic)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items.size, key = { items[it].id }) { index ->
                    val item = items[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { item.onClick() }
                            .testTag("hub_card_${item.id}"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Circular Pastel Icon Container
                            Surface(
                                shape = CircleShape,
                                color = item.circleBackground,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = item.iconTint,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 11.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
