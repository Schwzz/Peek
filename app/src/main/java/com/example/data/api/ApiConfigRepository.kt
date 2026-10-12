package com.example.data.api

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiConfigRepository(context: Context) {
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
        return getEffectiveApiKey(config).isNotBlank()
    }

    fun saveConfig(newConfig: ApiConfig) {
        prefs.edit().apply {
            putString("provider", newConfig.provider.name)
            putString("api_key", newConfig.apiKey)
            putString("base_url", newConfig.baseUrl)
            putString("model_name", newConfig.modelName)
            apply()
        }
        _configFlow.value = newConfig
    }

    private fun loadConfig(): ApiConfig {
        val providerStr = prefs.getString("provider", VisionAiProvider.GEMINI.name)
        val provider = runCatching { VisionAiProvider.valueOf(providerStr!!) }.getOrDefault(VisionAiProvider.GEMINI)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val baseUrl = prefs.getString("base_url", VisionAiProvider.OPENAI_COMPATIBLE.defaultBaseUrl) ?: VisionAiProvider.OPENAI_COMPATIBLE.defaultBaseUrl
        val defaultModel = provider.defaultModel
        val modelName = prefs.getString("model_name", defaultModel) ?: defaultModel

        return ApiConfig(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName
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
