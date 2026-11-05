package com.example.data.api

import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Native Anthropic and Ollama formats, including images in previous turns. */
class NativeVisionService(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()) : VisionAiService {
    override suspend fun analyze(imagesBase64: List<String>, prompt: String, history: List<ChatMessage>, config: ApiConfig, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(config.modelName.isNotBlank()) { "Choose a model in Models first." }
            val anthropic = config.provider.protocol == ApiProtocol.ANTHROPIC
            val messages = JSONArray()
            fun append(role: String, text: String, images: List<String>) {
                val message = JSONObject().put("role", role)
                if (anthropic) {
                    val content = JSONArray()
                    if (role == "user") images.forEach { image -> content.put(JSONObject().put("type", "image")
                        .put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image))) }
                    content.put(JSONObject().put("type", "text").put("text", text.ifBlank { "Describe this image." }))
                    message.put("content", content)
                } else {
                    message.put("content", text)
                    if (images.isNotEmpty() && role == "user") message.put("images", JSONArray(images))
                }
                messages.put(message)
            }
            history.filterNot { it.isError || it.isCancelled }.takeLast(6).dropWhile { it.sender != MessageSender.USER }.forEach {
                append(if (it.sender == MessageSender.USER) "user" else "assistant", it.text, it.imagesBase64)
            }
            append("user", prompt.ifBlank { "Describe this image." }, imagesBase64)
            val body = JSONObject().put("model", config.modelName.trim()).put("messages", messages)
            if (anthropic) body.put("max_tokens", 2048) else body.put("stream", false)
            val request = ProviderEndpoints.request(config, apiKey, if (anthropic) "messages" else "api/chat")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val (code, raw) = client.newCall(request).readResponse()
            check(code in 200..299) { providerError(code, raw) }
            val json = JSONObject(raw)
            val text = if (anthropic) {
                val blocks = json.optJSONArray("content") ?: JSONArray()
                (0 until blocks.length()).map { blocks.getJSONObject(it) }.filter { it.optString("type") == "text" }
                    .joinToString("\n") { it.optString("text") }
            } else (json.optJSONObject("message")?.opt("content") as? String).orEmpty()
            check(text.isNotBlank()) { "The model returned no text. Try another model or a shorter prompt." }
            Result.success(text.trim())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}

internal fun providerError(code: Int, raw: String): String {
    val detail = runCatching {
        val json = JSONObject(raw)
        json.optJSONObject("error")?.optString("message") ?: (json.opt("error") as? String)
    }.getOrNull()?.takeIf { it.isNotBlank() }
    return detail ?: when (code) {
        in 300..399 -> "The server redirected this request. Enter its final API URL."
        401, 403 -> "The server rejected the API key or access permission."
        404 -> "The server or model was not found. Check its URL and model ID."
        429 -> "Provider limit reached. Try again later or choose another model."
        else -> "Server returned HTTP $code."
    }
}
