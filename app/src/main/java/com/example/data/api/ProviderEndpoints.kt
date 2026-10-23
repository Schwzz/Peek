package com.example.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/** A server root, not an individual completion URL. Credentials never follow redirects. */
object ProviderEndpoints {
    fun base(config: ApiConfig): HttpUrl {
        val raw = if (config.provider.supportsCustomUrl) config.baseUrl.trim() else config.provider.defaultBaseUrl
        val parsed = raw.toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid server URL.")
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
            "Use a server URL without credentials, a query, or a fragment."
        }
        require(parsed.isHttps || (config.provider.supportsCustomUrl && isPrivateHost(parsed.host))) {
            "Use HTTPS for cloud servers. HTTP is supported for local network addresses only."
        }
        var path = parsed.encodedPath.trimEnd('/')
        for (suffix in listOf("/chat/completions", "/api/chat", "/api/tags", "/messages")) {
            if (path.endsWith(suffix)) { path = path.removeSuffix(suffix); break }
        }
        if (config.provider.protocol == ApiProtocol.OLLAMA) path = path.removeSuffix("/api")
        if (config.provider == VisionAiProvider.LM_STUDIO && path.isEmpty()) path = "/v1"
        return parsed.newBuilder().encodedPath("$path/").build()
    }

    fun url(config: ApiConfig, path: String): HttpUrl = base(config).resolve(path)!!

    fun request(config: ApiConfig, apiKey: String, path: String): Request.Builder {
        require(!config.provider.requiresApiKey || apiKey.isNotBlank()) { "Add your ${config.provider.displayName} API key in Models." }
        return Request.Builder().url(url(config, path)).apply {
            if (apiKey.isNotBlank()) when (config.provider.protocol) {
                ApiProtocol.GEMINI -> header("x-goog-api-key", apiKey)
                ApiProtocol.ANTHROPIC -> header("x-api-key", apiKey)
                else -> header("Authorization", "Bearer $apiKey")
            }
            if (config.provider.protocol == ApiProtocol.ANTHROPIC) header("anthropic-version", "2023-06-01")
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host == "::1") return true
        if (host.contains(':')) return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")
        val bytes = host.split('.').map { it.toIntOrNull() ?: return false }
        if (bytes.size != 4 || bytes.any { it !in 0..255 }) return false
        return bytes[0] == 127 || bytes[0] == 10 || (bytes[0] == 192 && bytes[1] == 168) ||
            (bytes[0] == 172 && bytes[1] in 16..31) || (bytes[0] == 169 && bytes[1] == 254)
    }
}
