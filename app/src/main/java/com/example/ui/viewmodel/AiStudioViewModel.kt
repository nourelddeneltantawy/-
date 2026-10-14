package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.Content
import com.example.data.api.GeminiClient
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.ImageConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiImageState {
    data object Idle : AiImageState
    data class Generating(val progressText: String) : AiImageState
    data class Success(val base64Image: String, val mimeType: String, val modelUsed: String) : AiImageState
    data class Error(val errorMessage: String) : AiImageState
}

class AiStudioViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AiImageState>(AiImageState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3-pro-image-preview")
    val selectedModel = _selectedModel.asStateFlow()

    private val _selectedResolution = MutableStateFlow("1K") // 1K, 2K, 4K
    val selectedResolution = _selectedResolution.asStateFlow()

    private val _prompt = MutableStateFlow("")
    val prompt = _prompt.asStateFlow()

    fun setModel(model: String) {
        _selectedModel.value = model
    }

    fun setResolution(resolution: String) {
        _selectedResolution.value = resolution
    }

    fun setPrompt(text: String) {
        _prompt.value = text
    }

    fun generateImage(existingBase64Image: String? = null) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _uiState.value = AiImageState.Error("يرجى ضبط مفتاح GEMINI_API_KEY في لوحة الأسرار لتوليد المخططات بالذكاء الاصطناعي.")
            return
        }

        viewModelScope.launch {
            val modelName = _selectedModel.value
            _uiState.value = AiImageState.Generating("جارٍ توليد المخطط التقني للماتور باستخدام $modelName...")

            try {
                val parts = mutableListOf<Part>()
                if (existingBase64Image != null && modelName == "gemini-nano-banana-2.1") {
                    parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = existingBase64Image)))
                }
                parts.add(Part(text = _prompt.value))

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = parts)),
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("TEXT", "IMAGE"),
                        imageConfig = if (modelName == "gemini-3-pro-image-preview") {
                            ImageConfig(aspectRatio = "1:1", imageSize = _selectedResolution.value)
                        } else null
                    )
                )

                val response = GeminiClient.service.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                var foundImageBase64: String? = null
                var foundMimeType: String = "image/png"

                val candidates = response.candidates
                if (!candidates.isNullOrEmpty()) {
                    for (candidate in candidates) {
                        val responseParts = candidate.content?.parts
                        if (responseParts != null) {
                            for (p in responseParts) {
                                if (p.inlineData != null) {
                                    foundImageBase64 = p.inlineData.data
                                    foundMimeType = p.inlineData.mimeType
                                    break
                                }
                            }
                        }
                        if (foundImageBase64 != null) break
                    }
                }

                if (foundImageBase64 != null) {
                    _uiState.value = AiImageState.Success(
                        base64Image = foundImageBase64,
                        mimeType = foundMimeType,
                        modelUsed = modelName
                    )
                } else {
                    _uiState.value = AiImageState.Error("لم تتم إعادة صورة من النموذج. يرجى تجربة وصف هندسي أوضح.")
                }
            } catch (e: Exception) {
                _uiState.value = AiImageState.Error("خطأ في توليد المخطط: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = AiImageState.Idle
    }
}
