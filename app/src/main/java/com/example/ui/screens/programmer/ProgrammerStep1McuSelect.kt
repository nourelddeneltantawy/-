package com.example.ui.screens.programmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.McuCatalogLockManager
import com.example.data.model.McuProcessor
import com.example.data.model.ProtocolFamilyType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.GlassPrimaryCtaButton
import com.example.ui.theme.GlassStatusWellPill
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsLiquidGlassTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.MonospaceTabularStyle
import com.example.ui.theme.NeuPrimaryCtaButton
import com.example.ui.theme.NeuStatusWellPill
import com.example.ui.theme.NeuTokens
import com.example.ui.theme.bidiIsolateMixed
import com.example.ui.theme.glassCard
import com.example.ui.theme.glassRecessed
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed
import com.example.ui.viewmodel.McuProgrammerUiState

@Composable
fun ProgrammerStep1McuSelect(
    state: McuProgrammerUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchMatch: (McuProcessor) -> Unit,
    onSelectManufacturer: (String) -> Unit,
    onSelectMainFamily: (String) -> Unit,
    onSelectSubFamily: (String) -> Unit,
    onSelectPartNumber: (McuProcessor) -> Unit,
    onToggleDtrRtsReset: (Boolean) -> Unit,
    onToggleManufacturerLock: (String) -> Unit = {},
    onToggleMainFamilyLock: (String, String) -> Unit = { _, _ -> },
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Database Status Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isNeu -> NeuStatusWellPill(
                    isDark = isDark,
                    accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = (if (state.isLoadingDb) "جاري تحميل قاعدة البيانات..."
                        else "${state.totalMcuCount} معالج (${state.loadedDbFiles.joinToString(" + ")})").bidiIsolateMixed(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeuTokens.textPrimary(isDark)
                    )
                }
                isGlass -> GlassStatusWellPill(
                    isDark = isDark,
                    accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = (if (state.isLoadingDb) "جاري تحميل قاعدة البيانات..."
                        else "${state.totalMcuCount} معالج (${state.loadedDbFiles.joinToString(" + ")})").bidiIsolateMixed(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTokens.textPrimary(isDark)
                    )
                }
                else -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = (if (state.isLoadingDb) "جاري تحميل قاعدة البيانات..."
                            else "${state.totalMcuCount} معالج (${state.loadedDbFiles.joinToString(" + ")})").bidiIsolateMixed(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Smart Instant Search Card
        Card(
            shape = RoundedCornerShape(if (isGlass) 24.dp else if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu || isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = when {
                isNeu -> Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark)
                isGlass -> Modifier.glassCard(cornerRadius = 24.dp, isDark = isDark)
                else -> Modifier
            }
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "البحث الذكي الفوري عن المعالج",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "اكتب جزء من الرقم أو العائلة (مثال: 21258 أو RL78)...",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث المعالج",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح البحث",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mcu_smart_search_input")
                )

                // Instant Search Matches Dropdown List
                if (state.searchQuery.trim().length >= 2) {
                    if (state.searchMatches.isEmpty()) {
                        Text(
                            text = "لا توجد معالجات مطابقة لـ \"${state.searchQuery.trim()}\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(state.searchMatches, key = { it.id }) { mcu ->
                                    val isMatchLocked = (mcu.manufacturer in state.lockedManufacturers) ||
                                        (McuCatalogLockManager.familyKey(mcu.manufacturer, mcu.mainFamily) in state.lockedMainFamilies)
                                    val matchBlocked = isMatchLocked && !state.isAdmin
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !matchBlocked) { onSelectSearchMatch(mcu) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mcu.partNumber,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (matchBlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${mcu.manufacturer} • ${mcu.mainFamily} • ${mcu.subFamily}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (matchBlocked) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFEF4444).copy(alpha = 0.14f)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = "مقفل",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "مقفل",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFEF4444)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "${mcu.pins}P • ${mcu.flashKb}KB",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

                val is78k0Target = state.selectedProcessor?.is78k0Flmd0Mode() == true ||
                    (state.selectedMainFamily.contains("78K0", ignoreCase = true) &&
                        !state.selectedMainFamily.contains("78K0R", ignoreCase = true))

                if (is78k0Target) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("step1_78k0_zerotouch_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = "وضع Zero-Touch لـ 78K0",
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "وضع FT232 التلقائي (Zero-Touch): DTR=FLMD0 و RTS=RESET (100ms/150ms) وخطا TX/RX منفصلان بدون دايود على 9600 8N1 مفعّلة تلقائياً فور التوصيل.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    // DTR/RTS Auto-Reset Pulse Toggle Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "نبضة DTR/RTS Reset التلقائية",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.enableDtrRtsReset) "مفعّلة: تحكم تلقائي في RESET و MODE" else "معطّلة: اتصال UART مباشر (4 أسلاك)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.enableDtrRtsReset,
                                onCheckedChange = onToggleDtrRtsReset,
                                modifier = Modifier.testTag("step1_dtr_rts_switch")
                            )
                        }
                    }
                }

                val isR8cManualMode = state.selectedProcessor?.protocolType == ProtocolFamilyType.R8C_M16C ||
                    state.selectedMainFamily.contains("R8C", ignoreCase = true) ||
                    state.selectedMainFamily.contains("M16C", ignoreCase = true)

                if (isR8cManualMode) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "محرك R8CTool.EXE الحرفي: RTS/DTR (250ms) ← رفع RESET (150ms) ← RTS=false (100ms) مع ارتداد قطبية DTR ← 16×0x00 ← 0xB0 واحدة ← 0xFB ← 0x50 ← 0xF5+0x70.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Live TX / RX Raw Hex Diagnostic Panel on Step 1
                if (state.logs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF070B14),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("step1_raw_hex_log")
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "التشخيص المباشر (TX / RX Raw Hex Log):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    state.logs.takeLast(20).reversed().forEach { entry ->
                                        val c = when {
                                            entry.message.startsWith("RX:") && !entry.message.contains("لا يوجد") -> Color(0xFF34D399)
                                            entry.message.startsWith("TX:") -> Color(0xFFFBBF24)
                                            entry.level == com.example.data.model.LogLevel.ERROR -> Color(0xFFF87171)
                                            entry.level == com.example.data.model.LogLevel.SUCCESS -> Color(0xFF34D399)
                                            else -> Color(0xFFCBD5E1)
                                        }
                                        Text(
                                            text = "[${entry.timestamp}] ${entry.message}",
                                            style = MonospaceTabularStyle.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                            color = c
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4 Cascading Dropdowns Card
        Card(
            shape = RoundedCornerShape(if (isGlass) 24.dp else if (isNeu) 20.dp else 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surface
            ),
            border = if (isNeu || isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = when {
                isNeu -> Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark)
                isGlass -> Modifier.glassCard(cornerRadius = 24.dp, isDark = isDark)
                else -> Modifier
            }
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الاختيار المتدرج للمعالج",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.isAdmin) {
                        Text(
                            text = "وضع المشرف: يمكنك قفل/فتح الشركات والعائلات",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                CascadingDropdownField(
                    label = "1. الشركة المصنعة (Manufacturer)",
                    selectedValue = state.selectedManufacturer,
                    options = state.manufacturers,
                    onSelect = onSelectManufacturer,
                    isAdmin = state.isAdmin,
                    isLocked = { man -> man in state.lockedManufacturers },
                    onToggleLock = onToggleManufacturerLock,
                    testTag = "dropdown_manufacturer"
                )

                CascadingDropdownField(
                    label = "2. العائلة الرئيسية (Main Family)",
                    selectedValue = state.selectedMainFamily,
                    options = state.mainFamilies,
                    onSelect = onSelectMainFamily,
                    isAdmin = state.isAdmin,
                    isLocked = { fam ->
                        (state.selectedManufacturer in state.lockedManufacturers) ||
                            (McuCatalogLockManager.familyKey(state.selectedManufacturer, fam) in state.lockedMainFamilies)
                    },
                    onToggleLock = { fam ->
                        onToggleMainFamilyLock(state.selectedManufacturer, fam)
                    },
                    testTag = "dropdown_main_family"
                )

                CascadingDropdownField(
                    label = "3. السلسلة الفرعية (Sub Family)",
                    selectedValue = state.selectedSubFamily,
                    options = state.subFamilies,
                    onSelect = onSelectSubFamily,
                    testTag = "dropdown_sub_family"
                )

                CascadingProcessorDropdown(
                    label = "4. رقم المعالج (Part Number)",
                    selectedProcessor = state.selectedProcessor,
                    options = state.partNumbers,
                    onSelect = onSelectPartNumber,
                    testTag = "dropdown_part_number"
                )
            }
        }

        // Selected MCU Datasheet Specs Card
        state.selectedProcessor?.let { mcu ->
            Card(
                shape = RoundedCornerShape(if (isGlass) 24.dp else if (isNeu) 20.dp else 18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNeu || isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = if (isNeu || isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = when {
                    isNeu -> Modifier.neuCard(cornerRadius = 20.dp, isDark = isDark)
                    isGlass -> Modifier.glassCard(cornerRadius = 24.dp, isDark = isDark, accentGlow = MaterialTheme.colorScheme.primary)
                    else -> Modifier
                }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .then(
                                    when {
                                        isNeu -> Modifier.neuRecessed(cornerRadius = 21.dp, isDark = isDark)
                                        isGlass -> Modifier.glassRecessed(cornerRadius = 21.dp, isDark = isDark)
                                        else -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mcu.partNumber,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${mcu.manufacturer} • ${mcu.mainFamily} / ${mcu.subFamily}".bidiIsolateMixed(),
                                fontSize = 12.sp,
                                color = when {
                                    isNeu -> NeuTokens.textSecondary(isDark)
                                    isGlass -> GlassTokens.textSecondary(isDark)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpecMiniBadge(
                            title = "عدد الأطراف",
                            value = "${mcu.pins} Pin",
                            accent = Color(0xFF0284C7),
                            isNeu = isNeu,
                            isGlass = isGlass,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        SpecMiniBadge(
                            title = "حجم الذاكرة (Flash)",
                            value = "${mcu.flashKb} KB",
                            accent = Color(0xFF10B981),
                            isNeu = isNeu,
                            isGlass = isGlass,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SpecDetailRow(label = "جهد التشغيل الموصى به:", value = mcu.recommendedVoltage.bidiIsolateMixed())
                    SpecDetailRow(label = "بروتوكول البرمجة:", value = mcu.protocolName.bidiIsolateMixed())
                    SpecDetailRow(label = "مصدر قاعدة البيانات:", value = mcu.sourceFile.ifBlank { "renesas_clean.json" })
                }
            }
        }

        // Next Action Button
        when {
            isNeu -> NeuPrimaryCtaButton(
                onClick = onNextStep,
                enabled = state.selectedProcessor != null,
                isDark = isDark,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step1_next_button")
            ) {
                Text(
                    text = "التالي: اختيار المبرمجة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            isGlass -> GlassPrimaryCtaButton(
                onClick = onNextStep,
                enabled = state.selectedProcessor != null,
                isDark = isDark,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step1_next_button")
            ) {
                Text(
                    text = "التالي: اختيار المبرمجة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            else -> Button(
                onClick = onNextStep,
                enabled = state.selectedProcessor != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("step1_next_button")
            ) {
                Text(
                    text = "التالي: اختيار المبرمجة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CascadingDropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    testTag: String,
    isAdmin: Boolean = false,
    isLocked: (String) -> Boolean = { false },
    onToggleLock: ((String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedValue.ifBlank { "اختر..." },
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag(testTag)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            options.forEach { option ->
                val locked = isLocked(option)
                val blockedForUser = locked && !isAdmin
                DropdownMenuItem(
                    enabled = !blockedForUser,
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    blockedForUser -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    option == selectedValue -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (locked && !isAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "مقفل",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "مقفل",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            } else if (isAdmin && onToggleLock != null) {
                                IconButton(
                                    onClick = { onToggleLock(option) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("lock_toggle_$option")
                                ) {
                                    Icon(
                                        imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (locked) "فتح القفل" else "قفل عن المستخدمين",
                                        tint = if (locked) Color(0xFFEF4444) else Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        if (!blockedForUser) {
                            expanded = false
                            onSelect(option)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CascadingProcessorDropdown(
    label: String,
    selectedProcessor: McuProcessor?,
    options: List<McuProcessor>,
    onSelect: (McuProcessor) -> Unit,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedProcessor?.displayLabel ?: "اختر رقم المعالج...",
            onValueChange = {},
            readOnly = true,
            label = { Text("$label (${options.size} موديل)", fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag(testTag)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            options.forEach { proc ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = proc.displayLabel,
                            fontSize = 13.sp,
                            fontWeight = if (proc.id == selectedProcessor?.id) FontWeight.Bold else FontWeight.Normal,
                            color = if (proc.id == selectedProcessor?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(proc)
                    }
                )
            }
        }
    }
}

@Composable
private fun SpecMiniBadge(
    title: String,
    value: String,
    accent: Color,
    isNeu: Boolean = false,
    isGlass: Boolean = false,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(if (isNeu || isGlass) 14.dp else 12.dp),
        color = if (isNeu || isGlass) Color.Transparent else accent.copy(alpha = 0.12f),
        border = if (isNeu || isGlass) null else BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.then(
            when {
                isNeu -> Modifier.neuRecessed(cornerRadius = 14.dp, isDark = isDark, accentBorder = accent.copy(alpha = 0.35f))
                isGlass -> Modifier.glassRecessed(cornerRadius = 14.dp, isDark = isDark, accentBorder = accent.copy(alpha = 0.45f))
                else -> Modifier
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title.bidiIsolateMixed(),
                fontSize = 11.sp,
                color = when {
                    isNeu -> NeuTokens.textSecondary(isDark)
                    isGlass -> GlassTokens.textSecondary(isDark)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MonospaceTabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

@Composable
private fun SpecDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
