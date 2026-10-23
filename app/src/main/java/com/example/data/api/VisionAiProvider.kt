package com.example.data.api

enum class ApiProtocol { GEMINI, OPENAI, ANTHROPIC, OLLAMA }

enum class VisionAiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    val defaultModel: String = "",
    val supportsCustomUrl: Boolean = false,
    val requiresApiKey: Boolean = true,
    val isLocal: Boolean = false
) {
    // Existing enum names preserve installed profiles and their keys.
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/", ApiProtocol.GEMINI, "gemini-2.5-flash"),
    OPENAI("OpenAI", "https://api.openai.com/v1/"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/", ApiProtocol.ANTHROPIC),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/"),
    GROQ("Groq", "https://api.groq.com/openai/v1/"),
    XAI("xAI", "https://api.x.ai/v1/"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/"),
    OLLAMA_CLOUD("Ollama Cloud", "https://ollama.com/", ApiProtocol.OLLAMA),
    OLLAMA("Ollama", "http://localhost:11434/", ApiProtocol.OLLAMA, supportsCustomUrl = true, requiresApiKey = false, isLocal = true),
    LM_STUDIO("LM Studio", "http://localhost:1234/v1/", supportsCustomUrl = true, requiresApiKey = false, isLocal = true),
    OPENAI_COMPATIBLE("Custom endpoint", "https://api.openai.com/v1/", defaultModel = "gpt-4o-mini", supportsCustomUrl = true, requiresApiKey = false);
}
