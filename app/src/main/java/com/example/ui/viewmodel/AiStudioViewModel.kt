package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun generateDiagram() {
        val currentPrompt = _prompt.value.trim()
        if (currentPrompt.isBlank()) {
            _generationState.value = AiGenerationState.Error("يرجى إدخال وصف المخطط أولاً")
            return
        }

        viewModelScope.launch {
            _generationState.value = AiGenerationState.Loading
            try {
                // Generates diagram visual URL or placeholder
                kotlinx.coroutines.delay(1000)
                _generationState.value = AiGenerationState.Success(
                    "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=800&q=80"
                )
            } catch (e: Exception) {
                _generationState.value = AiGenerationState.Error(e.message ?: "فشل توليد المخطط")
            }
        }
    }

    fun resetState() {
        _generationState.value = AiGenerationState.Idle
    }
}
