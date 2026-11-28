package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.programmer.ProgrammerStep1McuSelect
import com.example.ui.screens.programmer.ProgrammerStep2Pinout
import com.example.ui.screens.programmer.ProgrammerStep3Flasher
import com.example.ui.theme.FittedSingleLineText
import com.example.ui.theme.GlassCircleIconButton
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalIsLiquidGlassTheme
import com.example.ui.theme.LocalIsSoftNeumorphismTheme
import com.example.ui.theme.LocalSecondaryAccentColor
import com.example.ui.theme.NeuCircleIconButton
import com.example.ui.theme.NeuTokens
import com.example.ui.theme.bidiIsolateMixed
import com.example.ui.theme.glassCanvasBackground
import com.example.ui.theme.glassCard
import com.example.ui.theme.glassRecessed
import com.example.ui.theme.neuCard
import com.example.ui.theme.neuRecessed
import com.example.ui.viewmodel.McuProgrammerViewModel
import com.example.util.FirmwareExportFormat

@Composable
fun McuProgrammerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    programmerViewModel: McuProgrammerViewModel = viewModel()
) {
    LaunchedEffect(isAdmin) {
        programmerViewModel.setIsAdmin(isAdmin)
    }
    val state by programmerViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var keepScreenOnEnabled by remember { mutableStateOf(true) }
    var notificationPermissionPrompted by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(state.isBusy) {
        if (state.isBusy &&
            !notificationPermissionPrompted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionPrompted = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(state.isBusy, keepScreenOnEnabled) {
        val window = context.findActivity()?.window
        if (state.isBusy && keepScreenOnEnabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val handleStepBack: () -> Unit = {
        if (state.currentStep > 1) {
            programmerViewModel.goToStep(state.currentStep - 1)
        } else {
            onBackClick()
        }
    }

    BackHandler {
        handleStepBack()
    }

    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    val accentColor = LocalAccentColor.current
    val secondaryAccent = LocalSecondaryAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                when {
                    isNeu -> Modifier.background(NeuTokens.bg(isDark))
                    isGlass -> Modifier.glassCanvasBackground(isDark, accentColor, secondaryAccent)
                    else -> Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
            .testTag("mcu_programmer_screen")
    ) {
        // Curved Professional Header with 3-Step Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        isNeu -> Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .statusBarsPadding()
                            .neuCard(cornerRadius = 24.dp, isDark = isDark)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                        isGlass -> Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .statusBarsPadding()
                            .glassCard(cornerRadius = 26.dp, isDark = isDark, accentGlow = accentColor)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                        else -> Modifier
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    }
                )
        ) {
            val headerContentColor = when {
                isNeu -> NeuTokens.textPrimary(isDark)
                isGlass -> GlassTokens.textPrimary(isDark)
                else -> Color.White
            }
            val headerSubColor = when {
                isNeu -> NeuTokens.textSecondary(isDark)
                isGlass -> GlassTokens.textSecondary(isDark)
                else -> Color.White.copy(alpha = 0.88f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.currentStep > 1) {
                        when {
                            isNeu -> NeuCircleIconButton(
                                onClick = handleStepBack,
                                size = 40.dp,
                                isDark = isDark,
                                modifier = Modifier.testTag("mcu_programmer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "الخطوة السابقة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            isGlass -> GlassCircleIconButton(
                                onClick = handleStepBack,
                                size = 40.dp,
                                isDark = isDark,
                                accentGlow = accentColor,
                                modifier = Modifier.testTag("mcu_programmer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "الخطوة السابقة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            else -> IconButton(
                                onClick = handleStepBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                    .testTag("mcu_programmer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "الخطوة السابقة",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(
                                    when {
                                        isNeu -> Modifier.neuRecessed(cornerRadius = 20.dp, isDark = isDark)
                                        isGlass -> Modifier.glassRecessed(cornerRadius = 20.dp, isDark = isDark, accentBorder = accentColor.copy(alpha = 0.45f))
                                        else -> Modifier.background(Color.White.copy(alpha = 0.16f), CircleShape)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "مبرمجة المعالجات",
                                tint = if (isNeu || isGlass) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        FittedSingleLineText(
                            text = "مبرمجة المعالجات الاحترافية",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = headerContentColor,
                            fontSize = 16.sp,
                            minFontSize = 13.sp
                        )
                        Text(
                            text = when (state.currentStep) {
                                1 -> "الخطوة 1: اختيار المعالج والبحث الذكي"
                                2 -> "الخطوة 2: اختيار المبرمجة"
                                else -> "الخطوة 3: واجهة البرمجة وقراءة/كتابة الفلاش"
                            }.bidiIsolateMixed(),
                            fontSize = 11.sp,
                            color = headerSubColor
                        )
                    }
                }

                // Interactive Stepper Pills (1 -> 2 -> 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StepPill(
                        stepNum = 1,
                        title = "المعالج",
                        isActive = state.currentStep == 1,
                        isCompleted = state.currentStep > 1,
                        enabled = true,
                        onClick = { programmerViewModel.goToStep(1) },
                        modifier = Modifier.weight(1f)
                    )
                    StepPill(
                        stepNum = 2,
                        title = "المبرمجة والتوصيل",
                        isActive = state.currentStep == 2,
                        isCompleted = state.currentStep > 2,
                        enabled = state.selectedProcessor != null,
                        onClick = { programmerViewModel.goToStep(2) },
                        modifier = Modifier.weight(1.2f)
                    )
                    StepPill(
                        stepNum = 3,
                        title = "البرمجة والهيكس",
                        isActive = state.currentStep == 3,
                        isCompleted = false,
                        enabled = state.selectedProcessor != null,
                        onClick = { programmerViewModel.goToStep(3) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sequential Step Content
        AnimatedContent(
            targetState = state.currentStep,
            label = "mcu_programmer_step_transition",
            modifier = Modifier.weight(1f)
        ) { step ->
            when (step) {
                1 -> ProgrammerStep1McuSelect(
                    state = state,
                    onSearchQueryChange = programmerViewModel::onSearchQueryChanged,
                    onSelectSearchMatch = programmerViewModel::selectSearchMatch,
                    onSelectManufacturer = programmerViewModel::selectManufacturer,
                    onSelectMainFamily = programmerViewModel::selectMainFamily,
                    onSelectSubFamily = programmerViewModel::selectSubFamily,
                    onSelectPartNumber = programmerViewModel::selectPartNumber,
                    onToggleDtrRtsReset = programmerViewModel::setEnableDtrRtsReset,
                    onToggleManufacturerLock = programmerViewModel::toggleManufacturerLock,
                    onToggleMainFamilyLock = programmerViewModel::toggleMainFamilyLock,
                    onAutoDetectChipId = programmerViewModel::autoDetectChipId,
                    onNextStep = { programmerViewModel.goToStep(2) }
                )
                2 -> ProgrammerStep2Pinout(
                    state = state,
                    onSelectProgrammer = programmerViewModel::selectProgrammer,
                    onRefreshUsb = programmerViewModel::refreshUsbDevices,
                    onNextStep = { programmerViewModel.goToStep(3) },
                    onSkipStep = { programmerViewModel.goToStep(3) }
                )
                else -> ProgrammerStep3Flasher(
                    state = state,
                    onToggleConnection = programmerViewModel::toggleUsbConnection,
                    onBaudRateChange = programmerViewModel::setBaudRate,
                    onToggleDtrRtsReset = programmerViewModel::setEnableDtrRtsReset,
                    onToggleEchoCancel = programmerViewModel::setEnableEchoCancellation,
                    onIdCodeByteChange = programmerViewModel::updateIdCodeByte,
                    onResetIdCode = programmerViewModel::resetIdCodeDefault,
                    onClockChange = programmerViewModel::setClockFrequency,
                    onEraseModeChange = programmerViewModel::setEraseMode,
                    onDataFlashModeChange = programmerViewModel::setDataFlashMode,
                    onToggleViewingDataFlash = programmerViewModel::setViewingDataFlashTab,
                    onToggleAutoEraseBeforeWrite = programmerViewModel::setAutoEraseBeforeWrite,
                    onToggleAutoVerifyAfterWrite = programmerViewModel::setAutoVerifyAfterWrite,
                    onToggleAutoRestoreChip = programmerViewModel::setAutoRestoreChip,
                    onJumpToFirstData = programmerViewModel::triggerJumpToFirstData,
                    onLoadFirmwareUri = programmerViewModel::loadFirmwareFromUri,
                    onDismissFileValidationError = programmerViewModel::dismissFileValidationError,
                    onDismissFullCsMismatchAlert = programmerViewModel::dismissFullCsMismatchAlert,
                    onSelectExportFormat = programmerViewModel::setSelectedExportFormat,
                    suggestedDumpFileName = { fmt: FirmwareExportFormat, dfOnly: Boolean ->
                        programmerViewModel.suggestedDumpFileName(fmt, dfOnly)
                    },
                    onSaveDumpFormatUri = { uri, fmt -> programmerViewModel.saveDumpToUri(uri, fmt) },
                    onSaveDataFlashFormatUri = { uri, fmt -> programmerViewModel.saveDataFlashToUri(uri, fmt) },
                    onSaveDumpUri = programmerViewModel::saveDumpToUri,
                    onSaveDataFlashUri = programmerViewModel::saveDataFlashToUri,
                    onExportFullLogsUri = programmerViewModel::exportFullLogsToUri,
                    buildFullLogExportText = programmerViewModel::buildFullLogExportText,
                    onReadFlash = programmerViewModel::readChipFlash,
                    onWriteFlash = programmerViewModel::writeChipFlash,
                    onEraseFlash = programmerViewModel::eraseChipFlash,
                    onVerifyFlash = programmerViewModel::verifyChipFlash,
                    onTogglePauseResume = programmerViewModel::togglePauseResume,
                    onAutoDetectChipId = programmerViewModel::autoDetectChipId,
                    onClearLogs = programmerViewModel::clearLogs,
                    onRescue78k0Session = { session -> programmerViewModel.rescueOrResume78k0Flash(session) },
                    onCleanResume78k0Session = { session -> programmerViewModel.reset78k0BlockOffsetAndResume(session) },
                    onDismiss78k0Session = { session -> programmerViewModel.dismiss78k0RescueSession(session) },
                    onResumeFromWatchdog = { programmerViewModel.rescueOrResume78k0Flash(null) },
                    onCleanResumeFromWatchdog = { programmerViewModel.reset78k0BlockOffsetAndResume(null) },
                    onDismissWatchdog = programmerViewModel::dismissWatchdogAlert,
                    onResumeUniversalCheckpoint = programmerViewModel::resumeUniversalCheckpoint,
                    onDismissUniversalCheckpoint = programmerViewModel::dismissUniversalCheckpoint,
                    keepScreenOnEnabled = keepScreenOnEnabled,
                    onToggleKeepScreenOn = { keepScreenOnEnabled = it }
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun StepPill(
    stepNum: Int,
    title: String,
    isActive: Boolean,
    isCompleted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNeu = LocalIsSoftNeumorphismTheme.current
    val isGlass = LocalIsLiquidGlassTheme.current
    val isDark = LocalIsDarkTheme.current
    val bgColor = when {
        isNeu -> NeuTokens.surface(isDark)
        isGlass -> Color.Transparent
        isActive -> Color.White
        isCompleted -> Color(0xFF10B981).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val textColor = when {
        (isNeu || isGlass) && isActive -> MaterialTheme.colorScheme.primary
        (isNeu || isGlass) && isCompleted -> Color(0xFF10B981)
        isNeu -> NeuTokens.textSecondary(isDark).copy(alpha = if (enabled) 1f else 0.45f)
        isGlass -> GlassTokens.textSecondary(isDark).copy(alpha = if (enabled) 1f else 0.45f)
        isActive -> MaterialTheme.colorScheme.primary
        isCompleted -> Color(0xFF6EE7B7)
        else -> Color.White.copy(alpha = if (enabled) 0.92f else 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(if (isNeu || isGlass) 50.dp else 12.dp),
        color = if (isNeu || isGlass) Color.Transparent else bgColor,
        border = if (isCompleted && !isActive && !isNeu && !isGlass) BorderStroke(1.dp, Color(0xFF10B981)) else null,
        modifier = modifier
            .then(
                when {
                    isNeu -> if (isActive) Modifier.neuRecessed(cornerRadius = 50.dp, isDark = isDark, accentBorder = MaterialTheme.colorScheme.primary)
                    else Modifier.neuCard(cornerRadius = 50.dp, isDark = isDark)
                    isGlass -> if (isActive) Modifier.glassRecessed(cornerRadius = 50.dp, isDark = isDark, accentBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    else Modifier.glassCard(cornerRadius = 50.dp, isDark = isDark, accentGlow = if (isCompleted) Color(0xFF10B981) else null)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FittedSingleLineText(
                text = "$stepNum. $title",
                fontSize = 11.sp,
                minFontSize = 9.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}
