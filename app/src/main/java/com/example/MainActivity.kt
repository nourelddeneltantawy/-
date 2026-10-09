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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Motor
import com.example.ui.screens.AdminMotorScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.ManageAdminsScreen
import com.example.ui.screens.MotorDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AiStudioViewModel
import com.example.ui.viewmodel.MotorViewModel
import com.example.ui.viewmodel.MotorsUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

enum class Screen {
    CATALOG,
    DETAIL,
    ADMIN_EDIT,
    AI_STUDIO,
    MANAGE_ADMINS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isGuestMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isGuestMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isGuestMode) {
        AuthScreen(
            onAuthSuccess = {
                isGuestMode = false
                currentUser = Firebase.auth.currentUser
            },
            onContinueAsGuest = {
                isGuestMode = true
            }
        )
    } else {
        AppContent(
            userId = currentUser?.uid ?: "guest",
            userEmail = currentUser?.email,
            isGuest = isGuestMode && currentUser == null,
            onSignOut = {
                isGuestMode = false
                if (currentUser != null) {
                    Firebase.auth.signOut()
                    coroutineScope.launch {
                        try {
                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        } catch (_: Exception) {}
                        currentUser = null
                    }
                } else {
                    currentUser = null
                }
            }
        )
    }
}

@Composable
fun AppContent(
    userId: String,
    userEmail: String?,
    isGuest: Boolean = false,
    onSignOut: () -> Unit,
    motorViewModel: MotorViewModel = viewModel(),
    aiViewModel: AiStudioViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(Screen.CATALOG) }
    var motorToEdit by remember { mutableStateOf<Motor?>(null) }
    var onAiImageReceivedCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val motorsState by motorViewModel.motorsState.collectAsState()
    val searchQuery by motorViewModel.searchQuery.collectAsState()
    val selectedBrand by motorViewModel.selectedBrand.collectAsState()
    val selectedMotorType by motorViewModel.selectedMotorType.collectAsState()
    val selectedMotor by motorViewModel.selectedMotor.collectAsState()
    val adminsList by motorViewModel.adminsList.collectAsState()
    val isSuperAdmin = !isGuest && motorViewModel.isSuperAdmin
    val isAdmin = !isGuest && motorViewModel.isAdmin

    val allMotors = (motorsState as? MotorsUiState.Success)?.motors ?: emptyList()
    val existingBrands = remember(allMotors) {
        allMotors.map { it.brand.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val existingMotorTypes = remember(allMotors) {
        allMotors.map { it.motorType.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            Screen.CATALOG -> {
                CatalogScreen(
                    motorsState = motorsState,
                    searchQuery = searchQuery,
                    onSearchQueryChange = motorViewModel::onSearchQueryChanged,
                    selectedBrand = selectedBrand,
                    onBrandSelect = motorViewModel::onBrandSelected,
                    selectedMotorType = selectedMotorType,
                    onMotorTypeSelect = motorViewModel::onMotorTypeSelected,
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
                    onSignOutClick = onSignOut,
                    userEmail = userEmail,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin,
                    isGuest = isGuest,
                    onManageAdminsClick = {
                        if (isSuperAdmin) {
                            currentScreen = Screen.MANAGE_ADMINS
                        }
                    }
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
                        isAdmin = isAdmin
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
                            motorViewModel.saveMotor(savedMotor) {
                                motorViewModel.selectMotor(savedMotor)
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
                        existingBrands = existingBrands,
                        existingMotorTypes = existingMotorTypes,
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
                            motorViewModel.addAdmin(email, onResult)
                        },
                        onRemoveAdmin = { email, onResult ->
                            motorViewModel.removeAdmin(email, onResult)
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
