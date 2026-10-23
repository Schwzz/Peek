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

class OpenAiVisionService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : VisionAiService {

    override suspend fun analyze(
        imageBase64: String?,
        prompt: String,
        history: List<ChatMessage>,
        config: ApiConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() && config.provider.requiresApiKey) {
                return@withContext Result.failure(
                    IllegalArgumentException("API key is missing. Please configure it in Settings.")
                )
            }

            val model = config.modelName.trim()
            require(model.isNotBlank()) { "Choose a model in Models first." }
            val messagesArray = JSONArray()

            // Include prior chat history
            val recentHistory = history.filterNot { it.isError || it.isCancelled }.takeLast(6)
            for (msg in recentHistory) {
                if (msg.isError) continue
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.sender == MessageSender.USER) "user" else "assistant")
                if (msg.imageBase64 != null) {
                    msgObj.put("content", JSONArray()
                        .put(JSONObject().put("type", "text").put("text", msg.text))
                        .put(JSONObject().put("type", "image_url").put("image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,${msg.imageBase64}"))))
                } else {
                    msgObj.put("content", msg.text)
                }
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

            if (imageBase64 != null) {
                val base64 = imageBase64
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

            val request = ProviderEndpoints.request(config, apiKey, "chat/completions")
                .post(requestBody)
                .build()

            val (responseCode, responseBody) = client.newCall(request).readResponse()

            if (responseCode !in 200..299) {
                val parsedError = runCatching {
                    val errObj = JSONObject(responseBody).optJSONObject("error")
                    errObj?.optString("message") ?: "HTTP $responseCode"
                }.getOrDefault("HTTP $responseCode")
                return@withContext Result.failure(Exception("API Error: $parsedError"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("No completion choices returned by API."))
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            val value = message?.opt("content")
            val content = when (value) {
                is String -> value.trim()
                is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it)?.opt("text") as? String }.joinToString("\n").trim()
                else -> ""
            }

            if (content.isEmpty()) {
                Result.failure(Exception((message?.opt("refusal") as? String)?.takeIf { it.isNotBlank() } ?: "The model returned no answer. Try another model or a shorter prompt."))
            } else {
                Result.success(content)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
