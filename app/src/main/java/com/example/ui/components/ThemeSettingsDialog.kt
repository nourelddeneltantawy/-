package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.WaterDrop
import com.example.ui.theme.GlassActionPill
import com.example.ui.theme.GlassBadge
import com.example.ui.theme.GlassPillButton
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsLiquidGlassTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.NeuActionPill
import com.example.ui.theme.NeuBadge
import com.example.ui.theme.NeuPillButton
import com.example.ui.theme.NeuTokens
import com.example.ui.theme.glassCard
import com.example.ui.theme.glassRecessed
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed
import com.example.util.AppColorTheme
import com.example.util.AppThemeMode
import com.example.util.AppUiStyle
import com.example.util.ThemeManager

@Composable
fun ThemeSettingsDialog(
    onDismiss: () -> Unit,
    isAdmin: Boolean = false,
    onManageAdmins: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentMode by ThemeManager.themeMode.collectAsState()
    val currentUiStyle by ThemeManager.uiStyle.collectAsState()
    val adminDefaultStyle by ThemeManager.adminDefaultUiStyle.collectAsState()
    val currentColor by ThemeManager.colorTheme.collectAsState()
    val isDark = LocalIsDarkTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "مظهر التطبيق وتصميم الواجهة",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "نمط وتصميم الواجهة (UI Style):",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )

                UiStyleOptionItem(
                    title = AppUiStyle.STANDARD.title,
                    subtitle = AppUiStyle.STANDARD.subtitle,
                    icon = Icons.Default.Style,
                    isSelected = currentUiStyle == AppUiStyle.STANDARD,
                    badgeText = if (adminDefaultStyle == AppUiStyle.STANDARD) "الأساسي" else "إضافي",
                    isPrimaryDefault = adminDefaultStyle == AppUiStyle.STANDARD,
                    onClick = { ThemeManager.setUiStyle(context, AppUiStyle.STANDARD) },
                    testTag = "ui_style_standard"
                )

                UiStyleOptionItem(
                    title = AppUiStyle.CLAY.title,
                    subtitle = AppUiStyle.CLAY.subtitle,
                    icon = Icons.Default.Layers,
                    isSelected = currentUiStyle == AppUiStyle.CLAY,
                    badgeText = if (adminDefaultStyle == AppUiStyle.CLAY) "الأساسي" else "إضافي",
                    isPrimaryDefault = adminDefaultStyle == AppUiStyle.CLAY,
                    onClick = { ThemeManager.setUiStyle(context, AppUiStyle.CLAY) },
                    testTag = "ui_style_clay"
                )

                UiStyleOptionItem(
                    title = AppUiStyle.SOFT_NEUMORPHISM.title,
                    subtitle = AppUiStyle.SOFT_NEUMORPHISM.subtitle,
                    icon = Icons.Default.AutoAwesome,
                    isSelected = currentUiStyle == AppUiStyle.SOFT_NEUMORPHISM,
                    badgeText = if (adminDefaultStyle == AppUiStyle.SOFT_NEUMORPHISM) "الأساسي" else "إضافي",
                    isPrimaryDefault = adminDefaultStyle == AppUiStyle.SOFT_NEUMORPHISM,
                    onClick = { ThemeManager.setUiStyle(context, AppUiStyle.SOFT_NEUMORPHISM) },
                    testTag = "ui_style_soft_neumorphism"
                )

                UiStyleOptionItem(
                    title = AppUiStyle.LIQUID_GLASS.title,
                    subtitle = AppUiStyle.LIQUID_GLASS.subtitle,
                    icon = Icons.Default.WaterDrop,
                    isSelected = currentUiStyle == AppUiStyle.LIQUID_GLASS,
                    badgeText = if (adminDefaultStyle == AppUiStyle.LIQUID_GLASS) "الأساسي" else "إضافي",
                    isPrimaryDefault = adminDefaultStyle == AppUiStyle.LIQUID_GLASS,
                    onClick = { ThemeManager.setUiStyle(context, AppUiStyle.LIQUID_GLASS) },
                    testTag = "ui_style_liquid_glass"
                )

                if (currentUiStyle != adminDefaultStyle) {
                    TextButton(
                        onClick = { ThemeManager.resetToAdminDefaultStyle(context) },
                        modifier = Modifier.testTag("reset_to_primary_style_btn")
                    ) {
                        Text(
                            text = "استعادة الستايل الأساسي (${adminDefaultStyle.title.substringBefore(" (")})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isAdmin) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                when (currentUiStyle) {
                                    AppUiStyle.SOFT_NEUMORPHISM -> Modifier.neuCard(cornerRadius = 16.dp, isDark = isDark)
                                    AppUiStyle.LIQUID_GLASS -> Modifier.glassCard(cornerRadius = 18.dp, isDark = isDark, accentGlow = MaterialTheme.colorScheme.primary)
                                    else -> Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                }
                            )
                            .padding(12.dp)
                            .testTag("admin_primary_ui_style_section")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "إعداد المشرف: الستايل الأساسي للتطبيق",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "حدد الستايل الرئيسي الافتراضي الذي يفتح عليه التطبيق لجميع المستخدمين، بينما تبقى الأنماط الأخرى كإضافات اختيارية:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppUiStyle.entries.forEach { styleOption ->
                                    val isDefaultChoice = adminDefaultStyle == styleOption
                                    if (currentUiStyle == AppUiStyle.SOFT_NEUMORPHISM) {
                                        NeuActionPill(
                                            onClick = {
                                                ThemeManager.setAdminDefaultUiStyle(context, styleOption) { saved ->
                                                    Toast.makeText(
                                                        context,
                                                        if (saved) "تم اعتماد (${styleOption.title.substringBefore(" (")}) كستايل أساسي للجميع" else "تم الحفظ محلياً",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            isDark = isDark,
                                            accentColor = if (isDefaultChoice) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("admin_default_style_${styleOption.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (isDefaultChoice) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isDefaultChoice) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = styleOption.title.substringBefore(" ("),
                                                fontSize = 12.sp,
                                                fontWeight = if (isDefaultChoice) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isDefaultChoice) MaterialTheme.colorScheme.primary else NeuTokens.textPrimary(isDark),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isDefaultChoice) {
                                                NeuBadge(text = "الأساسي", accentColor = MaterialTheme.colorScheme.primary, isDark = isDark)
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isDefaultChoice) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isDefaultChoice) 1.5.dp else 1.dp,
                                                color = if (isDefaultChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    ThemeManager.setAdminDefaultUiStyle(context, styleOption) { saved ->
                                                        Toast.makeText(
                                                            context,
                                                            if (saved) "تم اعتماد (${styleOption.title.substringBefore(" (")}) كستايل أساسي للجميع" else "تم الحفظ محلياً",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                                .testTag("admin_default_style_${styleOption.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isDefaultChoice) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isDefaultChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = styleOption.title.substringBefore(" ("),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isDefaultChoice) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isDefaultChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isDefaultChoice) {
                                                    Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = "الأساسي",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentUiStyle == AppUiStyle.SOFT_NEUMORPHISM) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .neuCard(cornerRadius = 18.dp, isDark = isDark)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "معاينة ناعم مجسم",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDark) "داكن #1C1E22 + إضاءة حواف ناعمة" else "فاتح #EAECEF + إضاءة حواف ناعمة",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            NeuBadge(text = "Pill", accentColor = currentColor.primary, isDark = isDark)
                        }
                    }
                } else if (currentUiStyle == AppUiStyle.LIQUID_GLASS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .glassCard(cornerRadius = 20.dp, isDark = isDark, accentGlow = currentColor.primary)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "معاينة الزجاج السائل",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = GlassTokens.textPrimary(isDark)
                                )
                                Text(
                                    text = if (isDark) "خلفية #0D0F12 + زجاج متدرج بحافة مضيئة" else "خلفية #E6E8EC + ألواح زجاجية شفافة",
                                    fontSize = 10.sp,
                                    color = GlassTokens.textSecondary(isDark)
                                )
                            }
                            GlassBadge(text = "Glass", accentColor = currentColor.primary, isDark = isDark)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "وضع الإضاءة (فاتح / داكن):",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )

                ThemeOptionItem(
                    mode = AppThemeMode.SYSTEM,
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = currentMode == AppThemeMode.SYSTEM,
                    onClick = { ThemeManager.setThemeMode(context, AppThemeMode.SYSTEM) }
                )

                ThemeOptionItem(
                    mode = AppThemeMode.DARK,
                    icon = Icons.Default.DarkMode,
                    isSelected = currentMode == AppThemeMode.DARK,
                    onClick = { ThemeManager.setThemeMode(context, AppThemeMode.DARK) }
                )

                ThemeOptionItem(
                    mode = AppThemeMode.LIGHT,
                    icon = Icons.Default.LightMode,
                    isSelected = currentMode == AppThemeMode.LIGHT,
                    onClick = { ThemeManager.setThemeMode(context, AppThemeMode.LIGHT) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "اللون الرئيسي للتطبيق (Accent):",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AppColorTheme.entries) { colorItem ->
                        val isSelected = currentColor == colorItem
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { ThemeManager.setColorTheme(context, colorItem) }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colorItem.primary)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "مختار",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = colorItem.title.split(" ").firstOrNull() ?: colorItem.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (currentUiStyle) {
                AppUiStyle.SOFT_NEUMORPHISM -> {
                    NeuPillButton(
                        onClick = onDismiss,
                        isActive = true,
                        isDark = isDark
                    ) {
                        Text(
                            "تم",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AppUiStyle.LIQUID_GLASS -> {
                    GlassPillButton(
                        onClick = onDismiss,
                        isActive = true,
                        isDark = isDark,
                        accentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            "تم",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تم", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = if (onManageAdmins != null) {
            {
                when (currentUiStyle) {
                    AppUiStyle.SOFT_NEUMORPHISM -> {
                        NeuActionPill(
                            onClick = {
                                onDismiss()
                                onManageAdmins()
                            },
                            isDark = isDark,
                            accentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "إدارة المشرفين",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    AppUiStyle.LIQUID_GLASS -> {
                        GlassActionPill(
                            onClick = {
                                onDismiss()
                                onManageAdmins()
                            },
                            isDark = isDark,
                            accentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "إدارة المشرفين",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onManageAdmins()
                            }
                        ) {
                            Text("إدارة المشرفين", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else null,
        containerColor = when (currentUiStyle) {
            AppUiStyle.SOFT_NEUMORPHISM -> NeuTokens.surface(isDark)
            AppUiStyle.LIQUID_GLASS -> GlassTokens.surfaceStrong(isDark).copy(alpha = 0.96f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(if (currentUiStyle == AppUiStyle.LIQUID_GLASS) 26.dp else 22.dp)
    )
}

@Composable
private fun UiStyleOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeText: String,
    isPrimaryDefault: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    if (isNeu) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier
                            .neuRecessed(
                                cornerRadius = 16.dp,
                                isDark = isDark,
                                accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                    } else {
                        Modifier.neuCard(
                            cornerRadius = 16.dp,
                            isDark = isDark,
                            backgroundColor = NeuTokens.bg(isDark)
                        )
                    }
                )
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textPrimary(isDark)
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        NeuBadge(
                            text = badgeText,
                            accentColor = if (isPrimaryDefault) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                            isDark = isDark
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeuTokens.textSecondary(isDark),
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else if (isGlass) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.glassRecessed(
                            cornerRadius = 18.dp,
                            isDark = isDark,
                            accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    } else {
                        Modifier.glassCard(
                            cornerRadius = 18.dp,
                            isDark = isDark
                        )
                    }
                )
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textSecondary(isDark),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textPrimary(isDark)
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        GlassBadge(
                            text = badgeText,
                            accentColor = if (isPrimaryDefault) MaterialTheme.colorScheme.primary else GlassTokens.textSecondary(isDark),
                            isDark = isDark
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GlassTokens.textSecondary(isDark),
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textSecondary(isDark),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isPrimaryDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrimaryDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    mode: AppThemeMode,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    if (isNeu) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.neuRecessed(
                            cornerRadius = 16.dp,
                            isDark = isDark,
                            accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                    } else {
                        Modifier.neuCard(
                            cornerRadius = 16.dp,
                            isDark = isDark,
                            backgroundColor = NeuTokens.bg(isDark)
                        )
                    }
                )
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = mode.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textPrimary(isDark)
                        )
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeuTokens.textSecondary(isDark),
                            fontSize = 10.sp
                        )
                    )
                }
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else NeuTokens.textSecondary(isDark),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else if (isGlass) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.glassRecessed(
                            cornerRadius = 18.dp,
                            isDark = isDark,
                            accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    } else {
                        Modifier.glassCard(
                            cornerRadius = 18.dp,
                            isDark = isDark
                        )
                    }
                )
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = mode.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textSecondary(isDark),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textPrimary(isDark)
                        )
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GlassTokens.textSecondary(isDark),
                            fontSize = 10.sp
                        )
                    )
                }
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else GlassTokens.textSecondary(isDark),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = mode.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "محدد" else "غير محدد",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
