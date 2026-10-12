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

class OpenAiVisionService(
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
                    IllegalArgumentException("API key is missing. Please configure it in Settings.")
                )
            }

            var baseUrl = config.baseUrl.trim()
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/"
            }
            val endpoint = if (baseUrl.endsWith("chat/completions/")) {
                baseUrl.removeSuffix("/")
            } else if (baseUrl.endsWith("chat/completions")) {
                baseUrl
            } else {
                "${baseUrl}chat/completions"
            }

            val model = config.modelName.ifBlank { VisionAiProvider.OPENAI_COMPATIBLE.defaultModel }
            val messagesArray = JSONArray()

            // Include prior chat history
            val recentHistory = history.takeLast(6)
            for (msg in recentHistory) {
                if (msg.isError) continue
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.sender == MessageSender.USER) "user" else "assistant")
                msgObj.put("content", msg.text)
                messagesArray.put(msgObj)
            }

            // Current message
            val currentMsg = JSONObject()
            currentMsg.put("role", "user")

            val contentArray = JSONArray()
            val textPrompt = prompt.ifBlank { "Describe what is shown in this image and highlight any key details." }
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", textPrompt)
            })

            if (bitmap != null) {
                val base64 = BitmapUtils.toBase64Jpeg(bitmap)
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64")
                    })
                })
            }
            currentMsg.put("content", contentArray)
            messagesArray.put(currentMsg)

            val rootJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
            }

            val requestBody = rootJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    val errObj = JSONObject(responseBody).optJSONObject("error")
                    errObj?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                }.getOrDefault("HTTP ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("API Error: $parsedError"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("No completion choices returned by API."))
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            val content = message?.optString("content", "")?.trim().orEmpty()

            if (content.isEmpty()) {
                Result.failure(Exception("API returned an empty message."))
            } else {
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
