package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CommunityGroupRepository
import com.example.data.repository.MarketplaceRepository
import com.example.ui.theme.FittedSingleLineText
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassPrimaryCtaButton
import com.example.ui.theme.GlassStatusWellPill
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsLiquidGlassTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.LocalSecondaryAccentColor
import com.example.ui.theme.NeuBadge
import com.example.ui.theme.NeuPrimaryCtaButton
import com.example.ui.theme.NeuStatusWellPill
import com.example.ui.theme.NeuTokens
import com.example.ui.theme.bidiIsolateMixed
import com.example.ui.theme.glassCanvasBackground
import com.example.ui.theme.glassCard
import com.example.ui.theme.glassRecessed
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed

@Composable
fun CommunityHubScreen(
    userEmail: String?,
    isGuest: Boolean,
    isAdmin: Boolean,
    onNavigateToMarketplace: () -> Unit,
    onNavigateToGroupChat: () -> Unit,
    onAuthClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posts by MarketplaceRepository.posts.collectAsState()
    val messages by CommunityGroupRepository.messages.collectAsState()

    val isUserLocked = isGuest || userEmail.isNullOrBlank() || userEmail == "guest"
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    val accentColor = LocalAccentColor.current
    val secondaryAccent = LocalSecondaryAccentColor.current

    Scaffold(
        containerColor = when {
            isNeu -> NeuTokens.bg(isDark)
            isGlass -> Color.Transparent
            else -> MaterialTheme.colorScheme.background
        },
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isGlass) Modifier.glassCanvasBackground(isDark, accentColor, secondaryAccent) else Modifier
            )
            .testTag("community_hub_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            // Header Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            isNeu -> Modifier
                                .padding(horizontal = 14.dp)
                                .padding(top = innerPadding.calculateTopPadding() + 8.dp)
                                .neuCard(cornerRadius = 26.dp, isDark = isDark)
                                .padding(vertical = 20.dp, horizontal = 18.dp)
                            isGlass -> Modifier
                                .padding(horizontal = 14.dp)
                                .padding(top = innerPadding.calculateTopPadding() + 8.dp)
                                .glassCard(cornerRadius = 28.dp, isDark = isDark, accentGlow = accentColor)
                                .padding(vertical = 20.dp, horizontal = 18.dp)
                            else -> Modifier
                                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF0284C7),
                                            Color(0xFF0F172A)
                                        )
                                    )
                                )
                                .padding(top = innerPadding.calculateTopPadding() + 22.dp, bottom = 28.dp, start = 20.dp, end = 20.dp)
                        }
                    )
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .then(
                                        when {
                                            isNeu -> Modifier.neuRecessed(cornerRadius = 23.dp, isDark = isDark)
                                            isGlass -> Modifier.glassRecessed(cornerRadius = 23.dp, isDark = isDark, accentBorder = accentColor.copy(alpha = 0.4f))
                                            else -> Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = if (isNeu || isGlass) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                FittedSingleLineText(
                                    text = "مجتمع وسوق الفنيين 🤝",
                                    color = when {
                                        isNeu -> NeuTokens.textPrimary(isDark)
                                        isGlass -> GlassTokens.textPrimary(isDark)
                                        else -> Color.White
                                    },
                                    fontSize = 20.sp,
                                    minFontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "منصة بيع وشراء الكروت وجروب المحادثة الفورية".bidiIsolateMixed(),
                                    color = when {
                                        isNeu -> NeuTokens.textSecondary(isDark)
                                        isGlass -> GlassTokens.textSecondary(isDark)
                                        else -> Color.White.copy(alpha = 0.85f)
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "أقسام المجتمع المتاحة",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isUserLocked) {
                    when {
                        isNeu -> NeuStatusWellPill(
                            isDark = isDark,
                            accentBorder = Color(0xFFEF4444).copy(alpha = 0.35f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تسجيل الدخول مطلوب 🔒",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                        isGlass -> GlassStatusWellPill(
                            isDark = isDark,
                            accentBorder = Color(0xFFEF4444).copy(alpha = 0.4f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تسجيل الدخول مطلوب 🔒",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                        else -> Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تسجيل الدخول مطلوب 🔒",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two Prominent Grid Cards Side-by-Side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: سوق ومنشورات الكروت
                CommunityActionCard(
                    modifier = Modifier.weight(1f),
                    title = "سوق ومنشورات الكروت 🛒",
                    subtitle = "بيع وشراء الكروت، استفسارات، وطلبات الشراء",
                    countLabel = if (posts.isNotEmpty()) "${posts.size} إعلان متاح" else "سوق الكروت",
                    icon = Icons.Default.Storefront,
                    iconTint = Color(0xFF10B981),
                    iconBackground = Color(0xFF10B981).copy(alpha = 0.15f),
                    isLocked = isUserLocked,
                    isNeu = isNeu,
                    isGlass = isGlass,
                    isDark = isDark,
                    onClick = onNavigateToMarketplace
                )

                // Card 2: جروب الصنايعية والفنيين
                CommunityActionCard(
                    modifier = Modifier.weight(1f),
                    title = "جروب الصنايعية والفنيين 💬",
                    subtitle = "محادثة فورية مباشرة، تبادل الخبرات، وتشخيص الأعطال",
                    countLabel = if (messages.isNotEmpty()) "${messages.size} رسالة" else "محادثة حية ⚡",
                    icon = Icons.Default.Forum,
                    iconTint = Color(0xFF0284C7),
                    iconBackground = Color(0xFF0284C7).copy(alpha = 0.15f),
                    isLocked = isUserLocked,
                    isNeu = isNeu,
                    isGlass = isGlass,
                    isDark = isDark,
                    onClick = onNavigateToGroupChat
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Guest Login Promotion Card (if unauthenticated)
            if (isUserLocked) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(
                            when {
                                isNeu -> Modifier.neuCard(cornerRadius = 24.dp, isDark = isDark)
                                isGlass -> Modifier.glassCard(cornerRadius = 26.dp, isDark = isDark, accentGlow = Color(0xFF0284C7))
                                else -> Modifier
                            }
                        ),
                    shape = RoundedCornerShape(if (isNeu) 24.dp else if (isGlass) 26.dp else 20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
                    border = if (isNeu || isGlass) null else BorderStroke(1.2.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .then(
                                    when {
                                        isNeu -> Modifier.neuRecessed(cornerRadius = 27.dp, isDark = isDark)
                                        isGlass -> Modifier.glassRecessed(cornerRadius = 27.dp, isDark = isDark, accentBorder = Color(0xFF0284C7).copy(alpha = 0.4f))
                                        else -> Modifier.clip(CircleShape).background(Color(0xFF0284C7).copy(alpha = 0.15f))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "قسم خاص بالفنيين المسجلين 🔒",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = when {
                                isNeu -> NeuTokens.textPrimary(isDark)
                                isGlass -> GlassTokens.textPrimary(isDark)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Text(
                            text = "سوق الكروت وجروب الفنيين مخصص ومستقل لكل مستخدم بالبريد الإلكتروني لحفظ المنشورات والرسائل والتواصل المباشر مع الزملاء الفنيين.\n\nسجل دخولك الآن للوصول الكامل إلى السوق والجروب.",
                            fontSize = 12.sp,
                            color = when {
                                isNeu -> NeuTokens.textSecondary(isDark)
                                isGlass -> GlassTokens.textSecondary(isDark)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        when {
                            isNeu -> NeuPrimaryCtaButton(
                                onClick = onAuthClick,
                                isDark = isDark,
                                accentColor = Color(0xFF0284C7),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "تسجيل الدخول بالبريد الإلكتروني 🚀",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0284C7)
                                )
                            }
                            isGlass -> GlassPrimaryCtaButton(
                                onClick = onAuthClick,
                                isDark = isDark,
                                accentColor = Color(0xFF0284C7),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "تسجيل الدخول بالبريد الإلكتروني 🚀",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0284C7)
                                )
                            }
                            else -> Button(
                                onClick = onAuthClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text(
                                    text = "تسجيل الدخول بالبريد الإلكتروني 🚀",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // User info card showing their current authenticated status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(
                            when {
                                isNeu -> Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark)
                                isGlass -> Modifier.glassCard(cornerRadius = 22.dp, isDark = isDark, accentGlow = Color(0xFF10B981))
                                else -> Modifier
                            }
                        ),
                    shape = RoundedCornerShape(if (isNeu) 20.dp else if (isGlass) 22.dp else 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = if (isNeu || isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    when {
                                        isNeu -> Modifier.neuRecessed(cornerRadius = 20.dp, isDark = isDark)
                                        isGlass -> Modifier.glassRecessed(cornerRadius = 20.dp, isDark = isDark, accentBorder = Color(0xFF10B981).copy(alpha = 0.4f))
                                        else -> Modifier.clip(CircleShape).background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حساب الفني نشط ومفعل ✓",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = (userEmail ?: "مستخدم مسجل").bidiIsolateMixed(),
                                fontSize = 11.sp,
                                color = when {
                                    isNeu -> NeuTokens.textSecondary(isDark)
                                    isGlass -> GlassTokens.textSecondary(isDark)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CommunityActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    countLabel: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    isLocked: Boolean,
    isNeu: Boolean = false,
    isGlass: Boolean = false,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .then(
                when {
                    isNeu -> Modifier.neuCard(cornerRadius = 22.dp, isDark = isDark)
                    isGlass -> Modifier.glassCard(
                        cornerRadius = 24.dp,
                        isDark = isDark,
                        accentGlow = if (isLocked) Color(0xFFEF4444) else iconTint
                    )
                    else -> Modifier
                        .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color(0x1F000000), ambientColor = Color(0x12000000))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.2.dp, if (isLocked) Color(0xFFEF4444).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (isNeu) 22.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                if (isLocked) {
                    when {
                        isNeu -> NeuBadge(
                            text = "مغلق 🔒",
                            accentColor = Color(0xFFEF4444),
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        isGlass -> GlassBadge(
                            text = "مغلق 🔒",
                            accentColor = Color(0xFFEF4444),
                            isDark = isDark,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        else -> Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "مغلق 🔒",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    when {
                        isNeu -> NeuBadge(
                            text = countLabel,
                            accentColor = iconTint,
                            isDark = isDark
                        )
                        isGlass -> GlassBadge(
                            text = countLabel,
                            accentColor = iconTint,
                            isDark = isDark
                        )
                        else -> Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = iconTint.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = countLabel,
                                color = iconTint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .then(
                        when {
                            isNeu -> Modifier.neuCard(
                                cornerRadius = 31.dp,
                                isDark = isDark,
                                backgroundColor = NeuTokens.bg(isDark),
                                accentGlow = iconTint.copy(alpha = 0.22f)
                            )
                            isGlass -> Modifier.glassRecessed(
                                cornerRadius = 31.dp,
                                isDark = isDark,
                                accentBorder = iconTint.copy(alpha = 0.45f)
                            )
                            else -> Modifier.clip(CircleShape).background(iconBackground)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(34.dp)
                )
            }

            FittedSingleLineText(
                text = title,
                fontSize = 14.sp,
                minFontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = when {
                    isNeu -> NeuTokens.textPrimary(isDark)
                    isGlass -> GlassTokens.textPrimary(isDark)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = when {
                    isNeu -> NeuTokens.textSecondary(isDark)
                    isGlass -> GlassTokens.textSecondary(isDark)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
