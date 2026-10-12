package com.example.data.api

import android.graphics.Bitmap
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiVisionService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : VisionAiService {

    override suspend fun analyze(
        bitmap: Bitmap?,
        prompt: String,
        history: List<ChatMessage>,
        config: ApiConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Gemini API key is missing. Please configure it in Settings.")
                )
            }

            val model = config.modelName.ifBlank { VisionAiProvider.GEMINI.defaultModel }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val contentsArray = JSONArray()

            // Include prior chat history turns if multi-turn
            val recentHistory = history.takeLast(6)
            for (msg in recentHistory) {
                if (msg.isError) continue
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val partsArray = JSONArray()
                val textPart = JSONObject().put("text", msg.text)
                partsArray.put(textPart)
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            // Current user turn
            val currentContent = JSONObject()
            currentContent.put("role", "user")
            val currentParts = JSONArray()

            val textPrompt = prompt.ifBlank { "Describe what is shown in this image and highlight any key details." }
            currentParts.put(JSONObject().put("text", textPrompt))

            if (bitmap != null) {
                val base64Image = BitmapUtils.toBase64Jpeg(bitmap)
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                currentParts.put(JSONObject().put("inlineData", inlineData))
            }

            contentsArray.put(currentContent)

            val rootJson = JSONObject().apply {
                put("contents", contentsArray)
            }

            val requestBody = rootJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    val errObj = JSONObject(responseBody).optJSONObject("error")
                    errObj?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                }.getOrDefault("HTTP ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("Gemini API Error: $parsedError"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = jsonResponse.optJSONObject("promptFeedback")
                val blockReason = promptFeedback?.optString("blockReason")
                return@withContext Result.failure(
                    Exception(if (!blockReason.isNullOrBlank()) "Response blocked: $blockReason" else "No response returned from Gemini.")
                )
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            val stringBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) {
                        stringBuilder.append(text)
                    }
                }
            }

            val finalOutput = stringBuilder.toString().trim()
            if (finalOutput.isEmpty()) {
                Result.failure(Exception("Gemini returned an empty text response."))
            } else {
                Result.success(finalOutput)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
