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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

data class ToolMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val circleBackground: Color,
    val badge: String? = null,
    val onClick: () -> Unit
)

/**
 * Tools Hub Screen ("الأدوات")
 * Styled with a curved gradient header like the Dashboard ("الأقسام")
 * Features smart tools including Vodafone Cash Smart Wallet as the first and primary tool.
 */
@Composable
fun ToolsHubScreen(
    userEmail: String?,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    isGuest: Boolean,
    onNavigateToWalletRouter: () -> Unit,
    onNavigateToAiStudio: () -> Unit,
    onNavigateToIntegrator: () -> Unit,
    onNavigateToDiagnostics: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMotorCalcDialog by remember { mutableStateOf(false) }

    val toolsList = listOf(
        // Tool 1: Smart Wallet Router (FIRST as requested!)
        ToolMenuItem(
            id = "wallet_router",
            title = "توجيه المحافظ الذكي",
            subtitle = "فودافون كاش وإنستاباي • تخطي الحدود الشهرية وكود دفع فوري",
            icon = Icons.Default.AccountBalance,
            iconTint = Color(0xFF8B5CF6),
            circleBackground = Color(0xFFF3E8FF),
            badge = "جديد ⚡",
            onClick = onNavigateToWalletRouter
        ),
        // Tool 2: AI Schematics Studio
        ToolMenuItem(
            id = "ai_studio",
            title = "استوديو الذكاء الاصطناعي",
            subtitle = "توليد ورسم مخططات المواتير والضفائر وتحديد ألوان الأسلاك",
            icon = Icons.Default.AutoAwesome,
            iconTint = Color(0xFFEC4899),
            circleBackground = Color(0xFFFCE7F3),
            badge = "AI",
            onClick = onNavigateToAiStudio
        ),
        // Tool 3: Motor Load & Capacitor Calculator
        ToolMenuItem(
            id = "motor_calculator",
            title = "حاسبة قدرة وأحمال المواتير",
            subtitle = "حساب سحب الأمبير، سعة المكثف (uF)، واستهلاك الوات",
            icon = Icons.Default.Calculate,
            iconTint = Color(0xFF0284C7),
            circleBackground = Color(0xFFE0F2FE),
            onClick = { showMotorCalcDialog = true }
        ),
        // Tool 4: System Integrator
        ToolMenuItem(
            id = "system_integrator",
            title = "مجمع ومخطط الضفائر",
            subtitle = "تجميع وتوفيق أطراف الكارتة مع أطراف الماتور والقطع",
            icon = Icons.Default.Cable,
            iconTint = Color(0xFF10B981),
            circleBackground = Color(0xFFD1FAE5),
            onClick = onNavigateToIntegrator
        ),
        // Tool 5: Diagnostics & Error Codes
        ToolMenuItem(
            id = "diagnostics",
            title = "دليل فحص وتيست مود",
            subtitle = "فحص أكواد الأعطال الشائعة، قراءة المقاومات واختبار الكروت",
            icon = Icons.Default.Build,
            iconTint = Color(0xFFD97706),
            circleBackground = Color(0xFFFEF3C7),
            onClick = { onNavigateToDiagnostics("all") }
        )
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_hub_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // TOP HEADER: Curved gradient banner matching "الأقسام"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
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
                                .size(52.dp)
                                .shadow(4.dp, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.mipmap.ic_launcher),
                                    contentDescription = "شعار التطبيق",
                                    modifier = Modifier.size(46.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "منصة الأدوات الذكية ⚡",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = if (isSuperAdmin) "م. البراء إلكترونيك (Super Admin)" else if (isAdmin) "م. البراء إلكترونيك (مشرف)" else if (isGuest) "الزائر الكريم" else (userEmail ?: "مهندس الصيانة"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle info bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قائمة الأدوات والأنظمة الفنية المساعدة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${toolsList.size} أدوات نشطة",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tools Cards Grid (Matching DashboardHub design)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(toolsList, key = { it.id }) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { tool.onClick() }
                            .testTag("tool_card_${tool.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = tool.circleBackground,
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = tool.title,
                                            tint = tool.iconTint,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                if (tool.badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tool.iconTint.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = tool.badge,
                                            color = tool.iconTint,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = tool.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMotorCalcDialog) {
        MotorLoadCalculatorDialog(onDismiss = { showMotorCalcDialog = false })
    }
}

/**
 * Interactive Motor Load, Power & Capacitor Calculator Dialog
 */
@Composable
fun MotorLoadCalculatorDialog(onDismiss: () -> Unit) {
    var powerWatts by remember { mutableStateOf("450") }
    var voltage by remember { mutableStateOf("220") }
    var powerFactor by remember { mutableStateOf("0.85") }

    val wattsVal = powerWatts.toDoubleOrNull() ?: 0.0
    val voltVal = voltage.toDoubleOrNull() ?: 220.0
    val pfVal = powerFactor.toDoubleOrNull() ?: 0.85

    // Calculated Current: I = P / (V * PF)
    val currentAmps = if (voltVal > 0 && pfVal > 0) wattsVal / (voltVal * pfVal) else 0.0
    // Approximate Run Capacitor for single phase motors: C (uF) ≈ (P * 14) / 100 or empirical
    val capacitorUf = if (voltVal > 0) (wattsVal * 1000.0) / (2.0 * Math.PI * 50.0 * voltVal * voltVal) * 1000000.0 * 1.5 else 0.0
    val horsePower = wattsVal / 746.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حاسبة قدرة ومكثف الماتور ⚡", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = powerWatts,
                    onValueChange = { powerWatts = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("قدرة الماتور (وات W)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = voltage,
                    onValueChange = { voltage = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("جهد التشغيل (فولت V)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "النتائج الحسابية التقديرية:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• سحب التيار الكهربائي: %.2f أمبير (A)".format(currentAmps),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• القدرة الحصانية: %.2f حصان (HP)".format(horsePower),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• سعة المكثف التقريبية: %.1f إلى %.1f ميكروفاراد (uF)".format(capacitorUf * 0.85, capacitorUf * 1.15),
                            fontSize = 12.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("إغلاق")
            }
        }
    )
}
