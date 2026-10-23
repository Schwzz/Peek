package com.example.data.api

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.util.KeystoreCrypto
import com.example.util.KeyCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiConfigRepository(context: Context, private val cipher: KeyCipher = KeystoreCrypto) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("peek_api_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<ApiConfig> = _configFlow.asStateFlow()

    fun getConfig(): ApiConfig = _configFlow.value

    fun getEffectiveApiKey(config: ApiConfig = getConfig()): String {
        if (config.apiKey.isNotBlank()) {
            return config.apiKey.trim()
        }
        if (config.provider == VisionAiProvider.GEMINI) {
            val buildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
            if (buildConfigKey.isNotBlank() && !buildConfigKey.contains("MY_GEMINI_API_KEY")) {
                return buildConfigKey.trim()
            }
        }
        return ""
    }

    fun isConfigured(config: ApiConfig = getConfig()): Boolean {
        return config.modelName.isNotBlank() && (!config.provider.requiresApiKey || getEffectiveApiKey(config).isNotBlank()) &&
            runCatching { ProviderEndpoints.base(config) }.isSuccess
    }

    fun getConfig(provider: VisionAiProvider): ApiConfig = loadProviderConfig(provider)

    fun saveConfig(newConfig: ApiConfig) {
        require(newConfig.modelName.isNotBlank() && newConfig.modelName != "custom") { "Enter a model name." }
        ProviderEndpoints.base(newConfig)
        val encryptedKey = cipher.encrypt(newConfig.apiKey.trim())
        val prefix = newConfig.provider.name.lowercase()
        val legacyProvider = runCatching {
            VisionAiProvider.valueOf(prefs.getString("provider", VisionAiProvider.GEMINI.name)!!)
        }.getOrDefault(VisionAiProvider.GEMINI)
        val legacyConfig = if (prefs.contains("api_key") && legacyProvider != newConfig.provider)
            loadProviderConfig(legacyProvider) else null
        val legacyEncryptedKey = legacyConfig?.let { cipher.encrypt(it.apiKey) }
        prefs.edit().apply {
            if (legacyConfig != null) {
                val legacyPrefix = legacyProvider.name.lowercase()
                putString("${legacyPrefix}_api_key", legacyEncryptedKey)
                putString("${legacyPrefix}_base_url", legacyConfig.baseUrl)
                putString("${legacyPrefix}_model_name", legacyConfig.modelName)
            }
            putString("provider", newConfig.provider.name)
            putString("${prefix}_api_key", encryptedKey)
            putString("${prefix}_base_url", newConfig.baseUrl.trim())
            putString("${prefix}_model_name", newConfig.modelName.trim())
            putString("${prefix}_model_vision", newConfig.supportsImages?.toString())
            remove("api_key")
            remove("base_url")
            remove("model_name")
            check(commit()) { "Could not save settings. Please try again." }
        }
        _configFlow.value = newConfig.copy(apiKey = newConfig.apiKey.trim(), baseUrl = newConfig.baseUrl.trim(), modelName = newConfig.modelName.trim())
    }

    private fun loadConfig(): ApiConfig {
        val providerStr = prefs.getString("provider", VisionAiProvider.GEMINI.name)
        val provider = runCatching { VisionAiProvider.valueOf(providerStr!!) }.getOrDefault(VisionAiProvider.GEMINI)
        return loadProviderConfig(provider)
    }

    private fun loadProviderConfig(provider: VisionAiProvider): ApiConfig {
        val prefix = provider.name.lowercase()
        // Legacy fields belong only to the provider selected when they were saved.
        val legacy = prefs.getString("provider", VisionAiProvider.GEMINI.name) == provider.name
        val storedApiKey = prefs.getString("${prefix}_api_key", null)
            ?: if (legacy) prefs.getString("api_key", "").orEmpty() else ""
        val decryptedApiKey = cipher.decrypt(storedApiKey)
        val baseUrl = prefs.getString("${prefix}_base_url", null)
            ?: if (legacy) prefs.getString("base_url", provider.defaultBaseUrl).orEmpty() else provider.defaultBaseUrl
        val defaultModel = provider.defaultModel
        val modelName = prefs.getString("${prefix}_model_name", null)
            ?: if (legacy) prefs.getString("model_name", defaultModel).orEmpty() else defaultModel

        return ApiConfig(
            provider = provider,
            apiKey = decryptedApiKey,
            baseUrl = if (provider.supportsCustomUrl) baseUrl else provider.defaultBaseUrl,
            modelName = modelName,
            supportsImages = prefs.getString("${prefix}_model_vision", null)?.toBooleanStrictOrNull()
        )
    }

    companion object {
        @Volatile
        private var instance: ApiConfigRepository? = null

        fun getInstance(context: Context): ApiConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: ApiConfigRepository(context).also { instance = it }
            }
        }
    }
}
