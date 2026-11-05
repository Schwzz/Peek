package com.example.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteModel(val id: String, val name: String = id, val supportsImages: Boolean? = null)

class ModelCatalog(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()) {
    suspend fun list(config: ApiConfig, key: String): Result<List<RemoteModel>> = withContext(Dispatchers.IO) {
        try {
            val protocol = config.provider.protocol
            val path = when (protocol) {
                ApiProtocol.OLLAMA -> "api/tags"
                else -> "models"
            }
            val result = mutableListOf<RemoteModel>()
            var next: String? = null
            do {
                val builder = ProviderEndpoints.request(config, key, path)
                val url = ProviderEndpoints.url(config, path).newBuilder()
                if (protocol == ApiProtocol.GEMINI) {
                    url.addQueryParameter("pageSize", "1000")
                    next?.let { url.addQueryParameter("pageToken", it) }
                } else if (protocol == ApiProtocol.ANTHROPIC) {
                    url.addQueryParameter("limit", "100")
                    next?.let { url.addQueryParameter("after_id", it) }
                }
                val (code, raw) = client.newCall(builder.url(url.build()).get().build()).readResponse()
                check(code in 200..299) { providerError(code, raw) }
                val json = JSONObject(raw)
                val array = json.optJSONArray(if (protocol == ApiProtocol.GEMINI || protocol == ApiProtocol.OLLAMA) "models" else "data") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    if (protocol == ApiProtocol.GEMINI) {
                        val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                        if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                    }
                    val id = (if (protocol == ApiProtocol.GEMINI || protocol == ApiProtocol.OLLAMA) item.optString("name") else item.optString("id")).removePrefix("models/")
                    if (id.isBlank()) continue
                    val modalities = item.optJSONObject("architecture")?.optJSONArray("input_modalities")
                    val vision = modalities?.let { values -> (0 until values.length()).any { values.optString(it) == "image" } }
                    result += RemoteModel(id, item.optString("display_name").ifBlank { item.optString("displayName").ifBlank { item.optString("name").ifBlank { id } } }, vision)
                }
                val cursor = when (protocol) {
                    ApiProtocol.GEMINI -> json.optString("nextPageToken").takeIf { it.isNotBlank() }
                    ApiProtocol.ANTHROPIC -> if (json.optBoolean("has_more")) json.optString("last_id").takeIf { it.isNotBlank() } else null
                    else -> null
                }
                next = cursor?.takeUnless { it == next }
            } while (next != null && result.size < 3000)
            Result.success(result.distinctBy { it.id }.sortedBy { it.id })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun inspect(config: ApiConfig, key: String): Result<Boolean?> = withContext(Dispatchers.IO) {
        try {
            if (config.provider.protocol != ApiProtocol.OLLAMA) return@withContext Result.success(config.supportsImages)
            val request = ProviderEndpoints.request(config, key, "api/show")
                .post(JSONObject().put("model", config.modelName).toString().toRequestBody("application/json".toMediaType())).build()
            val (code, raw) = client.newCall(request).readResponse()
            check(code in 200..299) { providerError(code, raw) }
            val capabilities = JSONObject(raw).optJSONArray("capabilities")
            Result.success(capabilities?.let { values -> (0 until values.length()).any { values.optString(it) == "vision" } })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
