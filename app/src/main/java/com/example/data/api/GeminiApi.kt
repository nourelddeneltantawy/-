package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GenerationConfig(
    val responseModalities: List<String>? = null,
    val imageConfig: ImageConfig? = null
)

data class ImageConfig(
    val aspectRatio: String = "1:1",
    val imageSize: String = "1K"
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

data class Candidate(
    val content: Content? = null
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"

    fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun generateContent(
        prompt: String,
        model: String = "gemini-2.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY is not configured"))
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/v1beta/models/$model:generateContent?key=$apiKey")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
            }

            val body = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply { put("parts", partsArray) })
                }
                put("contents", contentsArray)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text") ?: ""
                Result.success(text)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("Gemini HTTP $responseCode: $errorStream"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API request failed", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
