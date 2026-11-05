package com.example.data.api

import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.CancellationException
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
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : VisionAiService {

    override suspend fun analyze(
        imagesBase64: List<String>,
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
            val url = ProviderEndpoints.url(config, "models/").newBuilder().addPathSegment("$model:generateContent").build()

            val contentsArray = JSONArray()

            // Include prior chat history turns if multi-turn
            val recentHistory = history.filterNot { it.isError || it.isCancelled }.takeLast(6).dropWhile { it.sender != MessageSender.USER }
            for (msg in recentHistory) {
                if (msg.isError) continue
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.sender == MessageSender.USER) "user" else "model")
                val partsArray = JSONArray()
                val textPart = JSONObject().put("text", msg.text)
                partsArray.put(textPart)
                msg.imagesBase64.forEach { encoded ->
                    partsArray.put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/jpeg").put("data", encoded)))
                }
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            // Current user turn
            val currentContent = JSONObject()
            currentContent.put("role", "user")
            val currentParts = JSONArray()

            val textPrompt = prompt.ifBlank { "Describe what is shown in this image and highlight any key details." }
            currentParts.put(JSONObject().put("text", textPrompt))

            imagesBase64.forEach { base64Image ->
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                currentParts.put(JSONObject().put("inlineData", inlineData))
            }

            currentContent.put("parts", currentParts)
            contentsArray.put(currentContent)

            val rootJson = JSONObject().apply {
                put("contents", contentsArray)
            }

            val requestBody = rootJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            val (responseCode, responseBody) = client.newCall(request).readResponse()

            if (responseCode !in 200..299) {
                val parsedError = runCatching {
                    val errObj = JSONObject(responseBody).optJSONObject("error")
                    errObj?.optString("message") ?: "HTTP $responseCode"
                }.getOrDefault("HTTP $responseCode")
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
                    if (part.optBoolean("thought")) continue
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
