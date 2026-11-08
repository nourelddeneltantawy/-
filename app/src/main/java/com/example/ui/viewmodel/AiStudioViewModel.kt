package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.AiMotorDiagramGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiGenerationState {
    data object Idle : AiGenerationState
    data object Loading : AiGenerationState
    data class Success(val imageUrl: String) : AiGenerationState
    data class Error(val message: String) : AiGenerationState
}

class AiStudioViewModel : ViewModel() {

    private val _prompt = MutableStateFlow("")
    val prompt = _prompt.asStateFlow()

    private val _generationState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val generationState = _generationState.asStateFlow()

    fun setPrompt(text: String) {
        _prompt.value = text
    }

    fun generateDiagram(context: Context) {
        val currentPrompt = _prompt.value.trim()
        if (currentPrompt.isBlank()) {
            _generationState.value = AiGenerationState.Error("يرجى إدخال وصف المخطط أولاً")
            return
        }

        viewModelScope.launch {
            _generationState.value = AiGenerationState.Loading
            try {
                val filePath = withContext(Dispatchers.IO) {
                    AiMotorDiagramGenerator.generateSchematicForPrompt(context.applicationContext ?: context, currentPrompt)
                }
                _generationState.value = AiGenerationState.Success(filePath)
            } catch (e: Exception) {
                _generationState.value = AiGenerationState.Error(e.message ?: "فشل توليد المخطط")
            }
        }
    }

    fun resetState() {
        _generationState.value = AiGenerationState.Idle
    }
}
