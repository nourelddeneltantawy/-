package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SmartWallet
import com.example.data.model.WalletRouteResult
import com.example.data.repository.WalletRoutingRepository
import com.example.receiver.WalletSmsReceiver
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun WalletRouterScreen(
    isAdmin: Boolean,
    currentUserEmail: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val wallets by WalletRoutingRepository.wallets.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = if (isAdmin) listOf("التحويل والدفع الذكي ⚡", "إدارة المحافظ ⚙️") else listOf("التحويل والدفع الذكي ⚡")

    // Dialog states for admin
    var showAddWalletDialog by remember { mutableStateOf(false) }
    var walletToEdit by remember { mutableStateOf<SmartWallet?>(null) }
    var showMonthlyResetDialog by remember { mutableStateOf(false) }
    var showSmsSimulatorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("wallet_router_screen"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "توجيه المحافظ والتحويلات الذكية",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "تخطي الحدود الشهرية للمحافظ وتسهيل الدفع المباشر",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }

                        if (isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "إشراف",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (tabs.size > 1) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 3.dp
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTabIndex == 0) {
                CustomerTransferFlow(
                    wallets = wallets,
                    onOpenAdminTab = if (isAdmin) { { selectedTabIndex = 1 } } else null
                )
            } else {
                AdminWalletsDashboard(
                    wallets = wallets,
                    onAddWalletClick = { showAddWalletDialog = true },
                    onEditWalletClick = { walletToEdit = it },
                    onResetMonthlyClick = { showMonthlyResetDialog = true },
                    onOpenSmsSimulator = { showSmsSimulatorDialog = true }
                )
            }
        }
    }

    // Add/Edit Wallet Dialog
    if (showAddWalletDialog || walletToEdit != null) {
        val editing = walletToEdit
        AddEditWalletDialog(
            initialWallet = editing,
            onDismiss = {
                showAddWalletDialog = false
                walletToEdit = null
            },
            onSave = { wallet ->
                WalletRoutingRepository.saveWallet(context, wallet) { success ->
                    Toast.makeText(
                        context,
                        if (success) "تم حفظ بيانات المحفظة بنجاح" else "فشل الحفظ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showAddWalletDialog = false
                walletToEdit = null
            }
        )
    }

    // Monthly Reset Confirmation Dialog
    if (showMonthlyResetDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "تصفير عدادات المبالغ المستلمة؟",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "سيتم تصفير حقل المبالغ المستلمة (currentReceived = 0) لجميع المحافظ وتنشيط المحافظ الممتلئة لاستقبال شهر جديد من التحويلات.\nهل أنت متأكد؟",
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        WalletRoutingRepository.resetMonthlyBalances(context) { success ->
                            Toast.makeText(
                                context,
                                if (success) "تم تصفير العدادات الشهرية بنجاح لجميع المحافظ!" else "فشلت العملية",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showMonthlyResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("نعم، تصفير الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthlyResetDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // SMS Simulator Dialog
    if (showSmsSimulatorDialog) {
        SmsSimulatorDialog(
            onDismiss = { showSmsSimulatorDialog = false },
            onSimulate = { smsText ->
                val result = WalletSmsReceiver.parseSms(smsText)
                if (result != null) {
                    val (amount, ref) = result
                    val activeWallet = wallets.firstOrNull { it.status == "ACTIVE" } ?: wallets.firstOrNull()
                    if (activeWallet != null) {
                        WalletRoutingRepository.recordTransaction(
                            context = context,
                            walletId = activeWallet.id,
                            amount = amount,
                            referenceText = "محاكاة SMS: $ref"
                        ) { success ->
                            Toast.makeText(
                                context,
                                if (success) "تم بنجاح استخراج مبلغ $amount ج.م وتحديث محفظة (${activeWallet.name})" else "فشل التحديث",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, "لا توجد محافظ مسجلة لتحديثها", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "لم يتم التعرف على مبلغ داخل نص الرسالة، تأكد من الصيغة", Toast.LENGTH_LONG).show()
                }
                showSmsSimulatorDialog = false
            }
        )
    }
}

/**
 * Step 1 & Step 2 Customer Payment Flow
 */
@Composable
fun CustomerTransferFlow(
    wallets: List<SmartWallet>,
    onOpenAdminTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("VODAFONE_CASH") } // "VODAFONE_CASH", "INSTAPAY", "ALL"
    var routeResult by remember { mutableStateOf<WalletRouteResult?>(null) }
    var isConfirmedByCustomer by remember { mutableStateOf(false) }

    val quickAmounts = listOf(100, 250, 500, 1000, 2000, 5000)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "نظام التوجيه الذكي للتحويلات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "أدخل المبلغ المطلوب وسيقوم النظام باختيار أنسب محفظة نشطة غير ممتلئة وتجهيز كود التحويل المباشر بنقرة واحدة.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }

        // STEP 1: Enter Amount & Method
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "١. أدخل مبلغ التحويل (ج.م)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (amountText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    amountText = ""
                                    routeResult = null
                                    isConfirmedByCustomer = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val clean = WalletSmsReceiver.normalizeDigits(input).filter { it.isDigit() || it == '.' }
                            amountText = clean
                            routeResult = null
                            isConfirmedByCustomer = false
                        },
                        label = { Text("المبلغ بالجنية المصري") },
                        placeholder = { Text("مثال: 500") },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            Text(
                                text = "ج.م",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_amount_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Chips
                    Text(
                        text = "مبالغ سريعة:",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickAmounts) { amt ->
                            val isSelected = amountText == amt.toString()
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    amountText = amt.toString()
                                    routeResult = null
                                    isConfirmedByCustomer = false
                                },
                                label = { Text("$amt ج.م", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Method Selection
                    Text(
                        text = "٢. اختر وسيلة الدفع المفضلة:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaymentMethodChip(
                            title = "فودافون كاش",
                            subtitle = "اتصال مباشر *9*7*..#",
                            icon = Icons.Default.PhoneAndroid,
                            isSelected = selectedMethod == "VODAFONE_CASH",
                            activeColor = Color(0xFFE60000),
                            onClick = {
                                selectedMethod = "VODAFONE_CASH"
                                routeResult = null
                            },
                            modifier = Modifier.weight(1f)
                        )

                        PaymentMethodChip(
                            title = "إنستاباي (InstaPay)",
                            subtitle = "تحويل فوري للتطبيق",
                            icon = Icons.Default.AccountBalance,
                            isSelected = selectedMethod == "INSTAPAY",
                            activeColor = Color(0xFF6B21A8),
                            onClick = {
                                selectedMethod = "INSTAPAY"
                                routeResult = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action: Route Wallet
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) {
                                Toast.makeText(context, "يرجى إدخال مبلغ صحيح أكبر من صفر", Toast.LENGTH_SHORT).show()
                            } else {
                                routeResult = WalletRoutingRepository.routePayment(amt, selectedMethod)
                                isConfirmedByCustomer = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("route_wallet_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "توجيه واختيار المحفظة المناسبة تلقائياً ⚡",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // STEP 2: Routing Result (Animated)
        item {
            AnimatedVisibility(
                visible = routeResult != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                routeResult?.let { result ->
                    if (result.isSuccess && result.wallet != null) {
                        SuccessRouteCard(
                            result = result,
                            isConfirmed = isConfirmedByCustomer,
                            onConfirm = {
                                isConfirmedByCustomer = true
                                WalletRoutingRepository.recordTransaction(
                                    context = context,
                                    walletId = result.wallet.id,
                                    amount = result.amount,
                                    referenceText = "تحويل عميل يدوي (${result.paymentMethod})"
                                ) { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "تم تسجيل تأكيد العملية بنجاح! شكراً لتعاملكم معنا 🎉" else "تم الحفظ محلياً",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    } else {
                        FailureRouteCard(
                            errorMessage = result.errorMessage ?: "تعذر توجيه المحفظة",
                            amount = result.amount,
                            availableCapacity = result.availableCapacity,
                            onOpenAdmin = onOpenAdminTab
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessRouteCard(
    result: WalletRouteResult,
    isConfirmed: Boolean,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val wallet = result.wallet!!
    val isVodafone = result.paymentMethod == "VODAFONE_CASH" || wallet.provider == "VODAFONE_CASH"
    val isInstaPay = result.paymentMethod == "INSTAPAY" || wallet.provider == "INSTAPAY"

    val brandColor = if (isVodafone) Color(0xFFE60000) else Color(0xFF6B21A8)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = brandColor.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, brandColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("success_route_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تم توجيه المحفظة المناسبة بنجاح!",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        fontSize = 15.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = brandColor,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = if (isVodafone) "فودافون كاش" else "InstaPay",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Wallet details container
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = wallet.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "رقم المحفظة / الحساب:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = wallet.phoneNumber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = brandColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "المبلغ المطلوب تحويله:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = "${result.amount.toLong()} ج.م",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ACTION 1: For Vodafone Cash -> Direct USSD dialer button
            if (isVodafone) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse(result.ussdDialerUri))
                            context.startActivity(intent)
                        } catch (e: Throwable) {
                            Toast.makeText(context, "تعذر فتح لوحة الاتصال، يرجى نسخ الكود", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("dial_ussd_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اتصال بالرمز المباشر 📞 (${result.ussdCode})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("USSD Code", result.ussdCode))
                            Toast.makeText(context, "تم نسخ كود التحويل بالكامل بنجاح 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ الكود", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", wallet.phoneNumber))
                            Toast.makeText(context, "تم نسخ رقم المحفظة: ${wallet.phoneNumber}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ الرقم", fontSize = 12.sp)
                    }
                }
            } else if (isInstaPay) {
                // InstaPay Flow
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instapay://"))
                            context.startActivity(intent)
                        } catch (e: Throwable) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("InstaPay Number", wallet.phoneNumber))
                            Toast.makeText(context, "تم نسخ رقم إنستاباي، يمكنك لصقه في التطبيق", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_instapay_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "فتح تطبيق InstaPay 🚀",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Phone", wallet.phoneNumber))
                        Toast.makeText(context, "تم نسخ رقم التحويل: ${wallet.phoneNumber}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ رقم الحساب / المحفظة", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بعد إتمام الاتصال أو التحويل وإدخال الرقم السري لمحفظتك، اضغط أدناه لتأكيد التحويل وإشعار النظام.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Completion
            if (!isConfirmed) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تأكيد إتمام التحويل بنجاح ✅",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تم تأكيد التحويل وتسجيله بالنظام بنجاح!",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FailureRouteCard(
    errorMessage: String,
    amount: Double,
    availableCapacity: Double,
    onOpenAdmin: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "تعذر التوجيه - تجاوز السعة المتاحة",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF7F1D1D),
                    lineHeight = 22.sp
                )
            )

            if (onOpenAdmin != null) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onOpenAdmin,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إدارة المحافظ وزيادة السعة")
                }
            }
        }
    }
}

@Composable
fun PaymentMethodChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) activeColor else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

/**
 * Admin Wallets Dashboard
 */
@Composable
fun AdminWalletsDashboard(
    wallets: List<SmartWallet>,
    onAddWalletClick: () -> Unit,
    onEditWalletClick: (SmartWallet) -> Unit,
    onResetMonthlyClick: () -> Unit,
    onOpenSmsSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalLimit = wallets.sumOf { it.monthlyLimit }
    val totalReceived = wallets.sumOf { it.currentReceived }
    val totalAvailable = wallets.sumOf { it.availableCapacity }
    val activeCount = wallets.count { it.status == "ACTIVE" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نظرة عامة على المحافظ الشهرية",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "$activeCount نشطة من أصل ${wallets.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBox(
                            title = "إجمالي السعة المتاحة",
                            value = "${(totalAvailable / 1000).toInt()}k ج.م",
                            color = Color(0xFF10B981)
                        )
                        StatBox(
                            title = "المستلم هذا الشهر",
                            value = "${(totalReceived / 1000).toInt()}k ج.م",
                            color = Color(0xFFD97706)
                        )
                        StatBox(
                            title = "الحد الشهري الكلي",
                            value = "${(totalLimit / 1000).toInt()}k ج.م",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Admin Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddWalletClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة محفظة", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenSmsSimulator,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("محاكي SMS", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onResetMonthlyClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصفير شهري", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Wallets List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "قائمة المحافظ النشطة والاحتياطية (${wallets.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "مرتبة حسب الأولوية ⇅",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // Wallet Items
        items(wallets, key = { it.id }) { wallet ->
            AdminWalletCard(
                wallet = wallet,
                onEdit = { onEditWalletClick(wallet) },
                onDelete = {
                    WalletRoutingRepository.deleteWallet(context, wallet.id) { ok ->
                        Toast.makeText(context, if (ok) "تم حذف المحفظة" else "فشل الحذف", Toast.LENGTH_SHORT).show()
                    }
                },
                onQuickDeposit = { addAmount ->
                    WalletRoutingRepository.recordTransaction(
                        context = context,
                        walletId = wallet.id,
                        amount = addAmount,
                        referenceText = "إيداع تجريبي من الإدارة (+${addAmount.toInt()})"
                    ) { ok ->
                        Toast.makeText(context, if (ok) "تمت إضافة $addAmount ج.م للمحفظة" else "فشل التحديث", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun AdminWalletCard(
    wallet: SmartWallet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickDeposit: (Double) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isVf = wallet.provider == "VODAFONE_CASH"
    val brandColor = if (isVf) Color(0xFFE60000) else Color(0xFF6B21A8)

    val progress = (wallet.currentReceived / wallet.monthlyLimit).toFloat().coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 0.95f -> Color(0xFFEF4444)
        progress >= 0.70f -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = brandColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVf) Icons.Default.PhoneAndroid else Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = wallet.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = wallet.phoneNumber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("phone", wallet.phoneNumber))
                                    Toast.makeText(context, "تم نسخ الرقم", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Priority Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "أولوية #${wallet.priority}",
                            color = Color(0xFF0284C7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Status Badge
                    val statusColor = when (wallet.status) {
                        "ACTIVE" -> Color(0xFF10B981)
                        "FULL" -> Color(0xFFEF4444)
                        else -> Color(0xFF6B7280)
                    }
                    val statusLabel = when (wallet.status) {
                        "ACTIVE" -> "نشطة"
                        "FULL" -> "ممتلئة"
                        else -> "معطلة"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capacity & Usage Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المستلم: ${wallet.currentReceived.toLong()} ج.م / ${wallet.monthlyLimit.toLong()} ج.م",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${wallet.usagePercentage.toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "السعة المتاحة: ${wallet.availableCapacity.toLong()} ج.م",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (wallet.availableCapacity > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                )
                Text(
                    text = "هامش الأمان: ${wallet.safetyMargin.toLong()} ج.م",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onQuickDeposit(100.0) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+100 ج.م", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { onQuickDeposit(500.0) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+500 ج.م", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف المحفظة؟", fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد بالتأكيد حذف محفظة (${wallet.name}) ورقمها (${wallet.phoneNumber})؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
        Text(
            text = title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Add / Edit Wallet Dialog
 */
@Composable
fun AddEditWalletDialog(
    initialWallet: SmartWallet?,
    onDismiss: () -> Unit,
    onSave: (SmartWallet) -> Unit
) {
    var name by remember { mutableStateOf(initialWallet?.name ?: "") }
    var phone by remember { mutableStateOf(initialWallet?.phoneNumber ?: "") }
    var provider by remember { mutableStateOf(initialWallet?.provider ?: "VODAFONE_CASH") }
    var monthlyLimitText by remember { mutableStateOf(initialWallet?.monthlyLimit?.toLong()?.toString() ?: "200000") }
    var safetyMarginText by remember { mutableStateOf(initialWallet?.safetyMargin?.toLong()?.toString() ?: "3000") }
    var priorityText by remember { mutableStateOf(initialWallet?.priority?.toString() ?: "1") }
    var status by remember { mutableStateOf(initialWallet?.status ?: "ACTIVE") }
    var notes by remember { mutableStateOf(initialWallet?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialWallet == null) "إضافة محفظة ذكية جديدة" else "تعديل بيانات المحفظة",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المحفظة (التعريفي)") },
                    placeholder = { Text("مثال: محفظة فودافون كاش 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = WalletSmsReceiver.normalizeDigits(it).filter { c -> c.isDigit() } },
                    label = { Text("رقم المحفظة (الهاتف)") },
                    placeholder = { Text("010XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Provider Choice
                Text("مزود الخدمة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = provider == "VODAFONE_CASH",
                        onClick = { provider = "VODAFONE_CASH" },
                        label = { Text("فودافون كاش") }
                    )
                    FilterChip(
                        selected = provider == "INSTAPAY",
                        onClick = { provider = "INSTAPAY" },
                        label = { Text("InstaPay") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = monthlyLimitText,
                        onValueChange = { monthlyLimitText = it.filter { c -> c.isDigit() } },
                        label = { Text("الحد الأقصى (ج)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = safetyMarginText,
                        onValueChange = { safetyMarginText = it.filter { c -> c.isDigit() } },
                        label = { Text("هامش أمان (ج)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priorityText,
                        onValueChange = { priorityText = it.filter { c -> c.isDigit() } },
                        label = { Text("الأولوية (1, 2...)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text("الحالة:", fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = status == "ACTIVE",
                                onClick = { status = "ACTIVE" },
                                label = { Text("نشطة", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = status == "DISABLED",
                                onClick = { status = "DISABLED" },
                                label = { Text("معطلة", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = monthlyLimitText.toDoubleOrNull() ?: 200000.0
                    val margin = safetyMarginText.toDoubleOrNull() ?: 3000.0
                    val prio = priorityText.toIntOrNull() ?: 1
                    val w = (initialWallet ?: SmartWallet()).copy(
                        name = name.ifBlank { "محفظة جديدة" },
                        phoneNumber = phone.ifBlank { "01000000000" },
                        provider = provider,
                        monthlyLimit = limit,
                        safetyMargin = margin,
                        priority = prio,
                        status = status,
                        notes = notes
                    )
                    onSave(w)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ البيانات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * SMS Simulator Dialog
 */
@Composable
fun SmsSimulatorDialog(
    onDismiss: () -> Unit,
    onSimulate: (String) -> Unit
) {
    var smsText by remember {
        mutableStateOf("تم استلام مبلغ 500.00 جنيه من 01012345678 بنجاح في محفظتك فودافون كاش.")
    }

    val sample1 = "تم استلام مبلغ 500.00 جنيه من 01012345678 بنجاح في محفظتك فودافون كاش."
    val sample2 = "تم تحويل 1,200.00 جم بنجاح إلى حسابك عبر انستاباي."
    val sample3 = "You have received EGP 750.00 from 01099998888."

    val parsedPreview = remember(smsText) {
        WalletSmsReceiver.parseSms(smsText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("محاكي رسائل SMS البنكية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "جرب استخراج المبالغ المالية تلقائياً من رسائل التأكيد مثل فودافون كاش وإنستاباي لتحديث رصيد المحافظ فورياً:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    label = { Text("نص الرسالة القصيرة (SMS)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )

                Text("نماذج جاهزة للاختبار:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = smsText == sample1,
                        onClick = { smsText = sample1 },
                        label = { Text("فودافون 500ج", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = smsText == sample2,
                        onClick = { smsText = sample2 },
                        label = { Text("إنستاباي 1200ج", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = smsText == sample3,
                        onClick = { smsText = sample3 },
                        label = { Text("English 750", fontSize = 10.sp) }
                    )
                }

                // Parser preview
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (parsedPreview != null) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (parsedPreview != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (parsedPreview != null) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (parsedPreview != null) "المبلغ المستخرج: ${parsedPreview.first} ج.م بنجاح!" else "لم يتم التعرف على مبلغ في النص الحالي",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (parsedPreview != null) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSimulate(smsText) },
                enabled = parsedPreview != null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تطبيق التحديث على المحفظة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
