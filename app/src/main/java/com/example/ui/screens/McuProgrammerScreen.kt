package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.programmer.ProgrammerStep1McuSelect
import com.example.ui.screens.programmer.ProgrammerStep2Pinout
import com.example.ui.screens.programmer.ProgrammerStep3Flasher
import com.example.ui.viewmodel.McuProgrammerViewModel

@Composable
fun McuProgrammerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    programmerViewModel: McuProgrammerViewModel = viewModel()
) {
    val state by programmerViewModel.uiState.collectAsState()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("mcu_programmer_screen")
    ) {
        // Curved Professional Header with 3-Step Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.currentStep > 1) {
                        IconButton(
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
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "مبرمجة المعالجات",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مبرمجة المعالجات الاحترافية",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (state.currentStep) {
                                1 -> "الخطوة 1: اختيار المعالج أو الفحص التلقائي"
                                2 -> "الخطوة 2: اختيار المبرمجة ومخطط التوصيل"
                                else -> "الخطوة 3: واجهة البرمجة وقراءة/كتابة الفلاش"
                            },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
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
                    onAutoDetectChipId = programmerViewModel::autoDetectChipId,
                    onResetUniversalAutoDetect = programmerViewModel::clearFamilySelectionForUniversalAutoDetect,
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
                    onJumpToFirstData = programmerViewModel::triggerJumpToFirstData,
                    onLoadFirmwareUri = programmerViewModel::loadFirmwareFromUri,
                    onSaveDumpUri = programmerViewModel::saveDumpToUri,
                    onSaveDataFlashUri = programmerViewModel::saveDataFlashToUri,
                    onReadFlash = programmerViewModel::readChipFlash,
                    onWriteFlash = programmerViewModel::writeChipFlash,
                    onEraseFlash = programmerViewModel::eraseChipFlash,
                    onVerifyFlash = programmerViewModel::verifyChipFlash,
                    onClearLogs = programmerViewModel::clearLogs
                )
            }
        }
    }
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
    val bgColor = when {
        isActive -> Color.White
        isCompleted -> Color(0xFF10B981).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val textColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isCompleted -> Color(0xFF6EE7B7)
        else -> Color.White.copy(alpha = if (enabled) 0.9f else 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = if (isCompleted && !isActive) BorderStroke(1.dp, Color(0xFF10B981)) else null,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$stepNum. $title",
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
