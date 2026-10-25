package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppliancePart
import com.example.data.model.Harness
import com.example.data.model.Motor
import com.example.ui.screens.AdminHarnessScreen
import com.example.ui.screens.AdminMotorScreen
import com.example.ui.screens.AdminPartScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DashboardHubScreen
import com.example.ui.screens.DiagnosticsGuideScreen
import com.example.ui.screens.HarnessListScreen
import com.example.ui.screens.ManageAdminsScreen
import com.example.ui.screens.MotorDetailScreen
import com.example.ui.screens.PartsListScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SystemIntegratorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.MotorViewModel
import com.example.ui.viewmodel.MotorsUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

enum class Screen {
    DASHBOARD,
    CATALOG,
    DETAIL,
    ADMIN_EDIT,
    HARNESS_LIST,
    ADMIN_HARNESS,
    PARTS_LIST,
    ADMIN_PART,
    SYSTEM_INTEGRATOR,
    DIAGNOSTICS,
    AI_STUDIO,
    MANAGE_ADMINS,
    AUTH
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.FirebaseInitializer.ensureInitialized(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    var isSplashFinished by remember { mutableStateOf(false) }
    var hasEnteredOnce by remember { mutableStateOf(false) }

    if (!isSplashFinished) {
        SplashScreen(
            onReady = {
                isSplashFinished = true
                hasEnteredOnce = true
            },
            onBackClick = if (hasEnteredOnce) { { isSplashFinished = true } } else null
        )
    } else {
        SafeAppContainer(
            onOpenWelcomeProfile = {
                isSplashFinished = false
            }
        )
    }
}

@Composable
fun SafeAppContainer(
    onOpenWelcomeProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val initialUser = try {
        com.example.util.FirebaseInitializer.ensureInitialized(context)
        Firebase.auth.currentUser
    } catch (_: Throwable) {
        null
    }
    var currentUser by remember { mutableStateOf(initialUser) }

    DisposableEffect(Unit) {
        com.example.util.FirebaseInitializer.ensureInitialized(context)
        val listener = FirebaseAuth.AuthStateListener { auth ->
            try {
                currentUser = auth.currentUser
            } catch (_: Throwable) {}
        }
        try {
            Firebase.auth.addAuthStateListener(listener)
        } catch (_: Throwable) {}

        onDispose {
            try {
                Firebase.auth.removeAuthStateListener(listener)
            } catch (_: Throwable) {}
        }
    }

    AppContent(
        userId = currentUser?.uid ?: "guest",
        userEmail = currentUser?.email,
        isGuest = currentUser == null,
        onSignOut = {
            if (currentUser != null) {
                try {
                    Firebase.auth.signOut()
                } catch (_: Throwable) {}
                currentUser = null
            }
        },
        onAuthSuccess = {
            currentUser = try { Firebase.auth.currentUser } catch (_: Throwable) { null }
        },
        onOpenWelcomeProfile = onOpenWelcomeProfile
    )
}

@Composable
fun AppContent(
    userId: String,
    userEmail: String?,
    isGuest: Boolean = false,
    onSignOut: () -> Unit,
    onAuthSuccess: () -> Unit = {},
    onOpenWelcomeProfile: () -> Unit = {},
    motorViewModel: MotorViewModel = viewModel(),
    aiViewModel: AiStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var motorToEdit by remember { mutableStateOf<Motor?>(null) }
    var harnessToEdit by remember { mutableStateOf<Harness?>(null) }
    var partToEdit by remember { mutableStateOf<AppliancePart?>(null) }
    var diagnosticsCategoryTitle by remember { mutableStateOf("أعطال الأوتوماتيك") }
    var onAiImageReceivedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var activeUpdateInfo by remember { mutableStateOf<com.example.data.model.AppUpdateInfo?>(null) }

    val motorsState by motorViewModel.motorsState.collectAsState()
    val searchQuery by motorViewModel.searchQuery.collectAsState()
    val categories by motorViewModel.categories.collectAsState()
    val selectedCategory by motorViewModel.selectedCategory.collectAsState()
    val favorites by motorViewModel.favorites.collectAsState()
    val selectedMotor by motorViewModel.selectedMotor.collectAsState()
    val adminsList by motorViewModel.adminsList.collectAsState()
    val userRole by motorViewModel.userRole.collectAsState()
    val harnesses by motorViewModel.harnesses.collectAsState()
    val parts by motorViewModel.parts.collectAsState()

    val isSuperAdmin = !isGuest && (userRole == com.example.ui.viewmodel.UserRole.SUPER_ADMIN)
    val isAdmin = !isGuest && (userRole == com.example.ui.viewmodel.UserRole.ADMIN || userRole == com.example.ui.viewmodel.UserRole.SUPER_ADMIN)

    val motorsList = when (val s = motorsState) {
        is MotorsUiState.Success -> s.motors
        else -> emptyList()
    }
    val motorsCount = motorsList.size
    val harnessesCount = harnesses.size
    val partsCount = parts.size

    androidx.compose.runtime.LaunchedEffect(userEmail) {
        motorViewModel.refreshUserRole(userEmail)
    }

    // 1. Startup In-App Update Check (runs on launch in background, non-blocking)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val update = com.example.util.UpdateChecker.checkUpdate(context)
            if (update != null && update.hasUpdate) {
                activeUpdateInfo = update
            }
        } catch (_: Throwable) {}
    }

    // Manual update check handler
    val handleManualUpdateCheck = {
        coroutineScope.launch {
            android.widget.Toast.makeText(context, "جاري التحقق من وجود تحديثات...", android.widget.Toast.LENGTH_SHORT).show()
            try {
                val update = com.example.util.UpdateChecker.checkUpdate(context)
                if (update != null && update.hasUpdate) {
                    activeUpdateInfo = update
                } else if (update != null) {
                    android.widget.Toast.makeText(
                        context,
                        "أنت تستخدم أحدث إصدار من التطبيق (v${BuildConfig.VERSION_NAME}) 🎉",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "تعذر الاتصال بملف التحديثات، يرجى التحقق من اتصال الإنترنت.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Throwable) {
                android.widget.Toast.makeText(
                    context,
                    "حدث خطأ أثناء فحص التحديثات",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Display In-App Update Dialog when update is found
    activeUpdateInfo?.let { updateInfo ->
        com.example.ui.components.UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { activeUpdateInfo = null },
            isAdmin = isAdmin
        )
    }

    val showBottomBar = currentScreen in setOf(
        Screen.DASHBOARD,
        Screen.CATALOG,
        Screen.HARNESS_LIST,
        Screen.PARTS_LIST,
        Screen.SYSTEM_INTEGRATOR
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.DASHBOARD,
                        onClick = { currentScreen = Screen.DASHBOARD },
                        icon = { Icon(Icons.Default.GridView, contentDescription = "الرئيسية") },
                        label = {
                            Text(
                                "الرئيسية",
                                fontSize = 11.sp,
                                fontWeight = if (currentScreen == Screen.DASHBOARD) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.CATALOG,
                        onClick = { currentScreen = Screen.CATALOG },
                        icon = { Icon(Icons.Default.Memory, contentDescription = "المواتير") },
                        label = {
                            Text(
                                "المواتير",
                                fontSize = 11.sp,
                                fontWeight = if (currentScreen == Screen.CATALOG) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFEF4444),
                            selectedTextColor = Color(0xFFEF4444),
                            indicatorColor = Color(0xFFFFE4E6)
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.HARNESS_LIST,
                        onClick = { currentScreen = Screen.HARNESS_LIST },
                        icon = { Icon(Icons.Default.Cable, contentDescription = "الضفائر") },
                        label = {
                            Text(
                                "الضفائر",
                                fontSize = 11.sp,
                                fontWeight = if (currentScreen == Screen.HARNESS_LIST) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0284C7),
                            selectedTextColor = Color(0xFF0284C7),
                            indicatorColor = Color(0xFFE0F2FE)
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.PARTS_LIST,
                        onClick = { currentScreen = Screen.PARTS_LIST },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "القطع") },
                        label = {
                            Text(
                                "القطع",
                                fontSize = 11.sp,
                                fontWeight = if (currentScreen == Screen.PARTS_LIST) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFD97706),
                            selectedTextColor = Color(0xFFD97706),
                            indicatorColor = Color(0xFFFEF3C7)
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.SYSTEM_INTEGRATOR,
                        onClick = { currentScreen = Screen.SYSTEM_INTEGRATOR },
                        icon = { Icon(Icons.Default.Hub, contentDescription = "المخطط") },
                        label = {
                            Text(
                                "المخطط",
                                fontSize = 11.sp,
                                fontWeight = if (currentScreen == Screen.SYSTEM_INTEGRATOR) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF8B5CF6),
                            selectedTextColor = Color(0xFF8B5CF6),
                            indicatorColor = Color(0xFFEDE9FE)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    Screen.DASHBOARD -> {
                        DashboardHubScreen(
                            userEmail = userEmail,
                            isAdmin = isAdmin,
                            isSuperAdmin = isSuperAdmin,
                            isGuest = isGuest,
                            motorsCount = motorsCount,
                            harnessesCount = harnessesCount,
                            partsCount = partsCount,
                            onNavigateToMotors = { currentScreen = Screen.CATALOG },
                            onNavigateToHarnesses = { currentScreen = Screen.HARNESS_LIST },
                            onNavigateToParts = { currentScreen = Screen.PARTS_LIST },
                            onNavigateToIntegrator = { currentScreen = Screen.SYSTEM_INTEGRATOR },
                            onNavigateToDiagnostics = { title ->
                                diagnosticsCategoryTitle = title
                                currentScreen = Screen.DIAGNOSTICS
                            },
                            onCheckForUpdates = { handleManualUpdateCheck() },
                            onManageAdmins = {
                                if (isSuperAdmin) {
                                    currentScreen = Screen.MANAGE_ADMINS
                                }
                            },
                            onAuthClick = {
                                if (isGuest) {
                                    currentScreen = Screen.AUTH
                                } else {
                                    onSignOut()
                                }
                            },
                            onNavigateToProfile = onOpenWelcomeProfile
                        )
                    }

                    Screen.CATALOG -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }
                        CatalogScreen(
                            motorsState = motorsState,
                            searchQuery = searchQuery,
                            onSearchQueryChange = motorViewModel::onSearchQueryChanged,
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelect = motorViewModel::onCategorySelected,
                            onAddNewCategory = { motorViewModel.addCategory(it) },
                            onDeleteCategory = { motorViewModel.deleteCategory(it) },
                            favoriteMotorIds = favorites,
                            onToggleFavorite = motorViewModel::toggleFavorite,
                            onMotorClick = { motor ->
                                motorViewModel.selectMotor(motor)
                                currentScreen = Screen.DETAIL
                            },
                            onAddNewMotorClick = {
                                if (isAdmin) {
                                    motorToEdit = null
                                    currentScreen = Screen.ADMIN_EDIT
                                }
                            },
                            onAiStudioClick = {
                                if (isAdmin) {
                                    currentScreen = Screen.AI_STUDIO
                                }
                            },
                            onCheckForUpdates = { handleManualUpdateCheck() },
                            onSignOutClick = {
                                if (isGuest) {
                                    currentScreen = Screen.AUTH
                                } else {
                                    onSignOut()
                                }
                            },
                            userEmail = userEmail,
                            isAdmin = isAdmin,
                            isSuperAdmin = isSuperAdmin,
                            isGuest = isGuest,
                            onManageAdminsClick = {
                                if (isSuperAdmin) {
                                    currentScreen = Screen.MANAGE_ADMINS
                                }
                            },
                            onEditMotorClick = { motor ->
                                motorToEdit = motor
                                currentScreen = Screen.ADMIN_EDIT
                            },
                            onDeleteMotorClick = { motor ->
                                motorViewModel.deleteMotor(motor.id)
                            },
                            onDuplicateMotor = { motor ->
                                motorViewModel.duplicateMotor(motor) { cloned ->
                                    motorToEdit = cloned
                                    currentScreen = Screen.ADMIN_EDIT
                                }
                            },
                            onMoveMotor = { motorId, moveUp ->
                                motorViewModel.moveMotor(motorId, moveUp)
                            },
                            onReorderMotors = { from, to ->
                                motorViewModel.reorderMotors(from, to)
                            }
                        )
                    }

                    Screen.HARNESS_LIST -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }
                        HarnessListScreen(
                            harnesses = harnesses,
                            isAdmin = isAdmin,
                            onBackClick = { currentScreen = Screen.DASHBOARD },
                            onAddHarnessClick = {
                                if (isAdmin) {
                                    harnessToEdit = null
                                    currentScreen = Screen.ADMIN_HARNESS
                                }
                            },
                            onEditHarnessClick = { harness ->
                                if (isAdmin) {
                                    harnessToEdit = harness
                                    currentScreen = Screen.ADMIN_HARNESS
                                }
                            },
                            onDeleteHarnessClick = { id ->
                                motorViewModel.deleteHarness(id)
                            },
                            onDuplicateHarnessClick = { harness ->
                                motorViewModel.duplicateHarness(harness) { cloned ->
                                    harnessToEdit = cloned
                                    currentScreen = Screen.ADMIN_HARNESS
                                }
                            },
                            onReorderHarnesses = { from, to ->
                                motorViewModel.reorderHarnesses(from, to)
                            }
                        )
                    }

                    Screen.ADMIN_HARNESS -> {
                        BackHandler {
                            currentScreen = Screen.HARNESS_LIST
                        }
                        AdminHarnessScreen(
                            initialHarness = harnessToEdit,
                            onSaveSuccess = { savedHarness ->
                                motorViewModel.saveHarness(savedHarness) {
                                    currentScreen = Screen.HARNESS_LIST
                                }
                            },
                            onBackClick = {
                                currentScreen = Screen.HARNESS_LIST
                            }
                        )
                    }

                    Screen.PARTS_LIST -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }
                        PartsListScreen(
                            parts = parts,
                            isAdmin = isAdmin,
                            onBackClick = { currentScreen = Screen.DASHBOARD },
                            onAddPartClick = {
                                if (isAdmin) {
                                    partToEdit = null
                                    currentScreen = Screen.ADMIN_PART
                                }
                            },
                            onEditPartClick = { part ->
                                if (isAdmin) {
                                    partToEdit = part
                                    currentScreen = Screen.ADMIN_PART
                                }
                            },
                            onDeletePartClick = { id ->
                                motorViewModel.deletePart(id)
                            },
                            onDuplicatePartClick = { part ->
                                motorViewModel.duplicatePart(part) { cloned ->
                                    partToEdit = cloned
                                    currentScreen = Screen.ADMIN_PART
                                }
                            }
                        )
                    }

                    Screen.ADMIN_PART -> {
                        BackHandler {
                            currentScreen = Screen.PARTS_LIST
                        }
                        AdminPartScreen(
                            initialPart = partToEdit,
                            onSaveSuccess = { savedPart ->
                                motorViewModel.savePart(savedPart) {
                                    currentScreen = Screen.PARTS_LIST
                                }
                            },
                            onBackClick = {
                                currentScreen = Screen.PARTS_LIST
                            }
                        )
                    }

                    Screen.SYSTEM_INTEGRATOR -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }
                        SystemIntegratorScreen(
                            harnesses = harnesses,
                            motors = motorsList,
                            parts = parts,
                            onBackClick = { currentScreen = Screen.DASHBOARD }
                        )
                    }

                    Screen.DIAGNOSTICS -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }
                        DiagnosticsGuideScreen(
                            categoryTitle = diagnosticsCategoryTitle,
                            onBackClick = { currentScreen = Screen.DASHBOARD }
                        )
                    }

                    Screen.DETAIL -> {
                        BackHandler {
                            currentScreen = Screen.CATALOG
                        }
                        selectedMotor?.let { motor ->
                            MotorDetailScreen(
                                motor = motor,
                                onBackClick = { currentScreen = Screen.CATALOG },
                                onEditClick = {
                                    if (isAdmin) {
                                        motorToEdit = motor
                                        currentScreen = Screen.ADMIN_EDIT
                                    }
                                },
                                onDuplicateClick = if (isAdmin) {
                                    {
                                        motorViewModel.duplicateMotor(motor) { cloned ->
                                            motorToEdit = cloned
                                            currentScreen = Screen.ADMIN_EDIT
                                        }
                                    }
                                } else null,
                                onAiGenerateForMotor = { m ->
                                    if (isAdmin) {
                                        motorToEdit = m
                                        aiViewModel.setPrompt("مخطط توصيل أطراف ماتور غسالة ${m.name} (${m.brand} - ${m.model}) موضحاً ألوان الأسلاك بدقة")
                                        onAiImageReceivedCallback = { generatedUri ->
                                            val updated = m.copy(imageUrl = generatedUri)
                                            motorViewModel.saveMotor(updated)
                                        }
                                        currentScreen = Screen.AI_STUDIO
                                    }
                                },
                                isAdmin = isAdmin,
                                isFavorite = motorViewModel.isFavorite(motor.id),
                                onToggleFavorite = { motorViewModel.toggleFavorite(motor.id) }
                            )
                        } ?: run {
                            currentScreen = Screen.CATALOG
                        }
                    }

                    Screen.ADMIN_EDIT -> {
                        if (!isAdmin) {
                            currentScreen = Screen.CATALOG
                        } else {
                            BackHandler {
                                currentScreen = if (selectedMotor != null) Screen.DETAIL else Screen.CATALOG
                            }
                            AdminMotorScreen(
                                initialMotor = motorToEdit,
                                currentUserId = userId,
                                onBackClick = {
                                    currentScreen = if (selectedMotor != null) Screen.DETAIL else Screen.CATALOG
                                },
                                onSaveSuccess = { savedMotor ->
                                    motorViewModel.saveMotor(savedMotor) { newId ->
                                        val motorWithId = if (savedMotor.id.isNotBlank()) savedMotor else savedMotor.copy(id = newId)
                                        motorViewModel.selectMotor(motorWithId)
                                        currentScreen = Screen.CATALOG
                                    }
                                },
                                onDeleteSuccess = {
                                    motorToEdit?.let { m ->
                                        motorViewModel.deleteMotor(m.id) {
                                            currentScreen = Screen.CATALOG
                                        }
                                    }
                                },
                                onOpenAiStudioForImage = { onImageReady ->
                                    onAiImageReceivedCallback = onImageReady
                                    currentScreen = Screen.AI_STUDIO
                                },
                                availableCategories = categories,
                                onAddNewCategory = { motorViewModel.addCategory(it) },
                                isSuperAdmin = isSuperAdmin,
                                onManageAdminsClick = {
                                    currentScreen = Screen.MANAGE_ADMINS
                                }
                            )
                        }
                    }

                    Screen.AI_STUDIO -> {
                        if (!isAdmin) {
                            currentScreen = Screen.CATALOG
                        } else {
                            BackHandler {
                                currentScreen = if (motorToEdit != null) Screen.ADMIN_EDIT else Screen.CATALOG
                            }
                            AiStudioScreen(
                                viewModel = aiViewModel,
                                onBackClick = {
                                    currentScreen = if (motorToEdit != null) Screen.ADMIN_EDIT else Screen.CATALOG
                                },
                                onUseGeneratedImage = { imageUri ->
                                    onAiImageReceivedCallback?.invoke(imageUri)
                                    currentScreen = if (motorToEdit != null) Screen.ADMIN_EDIT else Screen.CATALOG
                                }
                            )
                        }
                    }

                    Screen.MANAGE_ADMINS -> {
                        if (!isSuperAdmin) {
                            currentScreen = Screen.CATALOG
                        } else {
                            BackHandler {
                                currentScreen = Screen.CATALOG
                            }
                            ManageAdminsScreen(
                                admins = adminsList,
                                onAddAdmin = { email, onResult ->
                                    motorViewModel.addAdmin(email) { result ->
                                        if (result.isSuccess) {
                                            onResult(true, "تمت إضافة المشرف بنجاح")
                                        } else {
                                            onResult(false, result.exceptionOrNull()?.message ?: "فشلت الإضافة")
                                        }
                                    }
                                },
                                onRemoveAdmin = { email, onResult ->
                                    motorViewModel.removeAdmin(email) { result ->
                                        if (result.isSuccess) {
                                            onResult(true, "تم سحب صلاحيات الإشراف بنجاح")
                                        } else {
                                            onResult(false, result.exceptionOrNull()?.message ?: "فشل الحذف")
                                        }
                                    }
                                },
                                onBackClick = {
                                    currentScreen = Screen.CATALOG
                                }
                            )
                        }
                    }

                    Screen.AUTH -> {
                        BackHandler {
                            currentScreen = Screen.CATALOG
                        }
                        AuthScreen(
                            onAuthSuccess = { signedInEmail ->
                                onAuthSuccess()
                                motorViewModel.refreshUserRole(signedInEmail)
                                currentScreen = Screen.CATALOG
                            },
                            onContinueAsGuest = {
                                currentScreen = Screen.CATALOG
                            },
                            onBackClick = {
                                currentScreen = Screen.CATALOG
                            }
                        )
                    }
                }
            }
        }
    }
}
