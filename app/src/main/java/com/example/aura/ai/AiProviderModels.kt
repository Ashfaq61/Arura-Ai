package com.example.aura.ai

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean,
    val defaultBaseUrl: String,
    val defaultModels: List<String>,
    val description: String
) {
    FIREBASE_GEMINI(
        id = "FIREBASE_GEMINI",
        displayName = "Gemini",
        requiresApiKey = true,
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModels = listOf(GEMINI_DEFAULT_MODEL, "gemini-3.5-flash-lite", "gemini-4-argon"),
        description = "Google Gemini REST API ($GEMINI_DEFAULT_MODEL) using your encrypted API keys with automatic 429 rate-limit failover and 404 model fallback."
    ),
    GROQ(
        id = "GROQ",
        displayName = "Groq Cloud",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
        description = "Ultra-low latency LPU inference (requires Groq API key in Keystore Vault)."
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter",
        requiresApiKey = true,
        defaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModels = listOf("meta-llama/llama-3.3-70b-instruct:free", "qwen/qwen-2.5-72b-instruct", "deepseek/deepseek-chat"),
        description = "Unified multi-model router supporting free and paid open-weights models."
    ),
    CLAUDE(
        id = "CLAUDE",
        displayName = "Anthropic Claude",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.anthropic.com/v1/messages",
        defaultModels = listOf("claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"),
        description = "Anthropic Messages API (x-api-key + anthropic-version 2023-06-01) for coding, analysis & safety review."
    ),
    GROK(
        id = "GROK",
        displayName = "xAI Grok",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.x.ai/v1/chat/completions",
        defaultModels = listOf("grok-3-mini", "grok-3"),
        description = "xAI Grok OpenAI-compatible reasoning endpoint."
    ),
    DEEPSEEK(
        id = "DEEPSEEK",
        displayName = "DeepSeek",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.deepseek.com/chat/completions",
        defaultModels = listOf("deepseek-chat", "deepseek-reasoner"),
        description = "DeepSeek V3 / R1 reasoning and coding inference API."
    ),
    QWEN(
        id = "QWEN",
        displayName = "Alibaba Qwen",
        requiresApiKey = true,
        defaultBaseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
        defaultModels = listOf("qwen-plus", "qwen-turbo", "qwen2.5-coder-32b-instruct"),
        description = "Alibaba Cloud DashScope OpenAI-compatible Qwen inference."
    ),
    HUGGING_FACE(
        id = "HUGGING_FACE",
        displayName = "Hugging Face Inference",
        requiresApiKey = true,
        defaultBaseUrl = "https://router.huggingface.co/v1/chat/completions",
        defaultModels = listOf("Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.3-70B-Instruct", "mistralai/Mistral-7B-Instruct-v0.3"),
        description = "Open-source models hosted on Hugging Face Inference Router."
    ),
    OLLAMA(
        id = "OLLAMA",
        displayName = "Ollama (Local/LAN)",
        requiresApiKey = false,
        defaultBaseUrl = "http://127.0.0.1:11434/api/chat",
        defaultModels = listOf("llama3.2:3b", "qwen2.5:7b", "gemma3:4b"),
        description = "Self-hosted Ollama local or LAN inference server (/api/chat & /api/tags). No API key needed locally."
    ),
    MCP_SERVER(
        id = "MCP_SERVER",
        displayName = "MCP HTTP Server",
        requiresApiKey = false,
        defaultBaseUrl = "http://127.0.0.1:3000/mcp",
        defaultModels = listOf("mcp-jsonrpc-2.0"),
        description = "Model Context Protocol JSON-RPC 2.0 server (tools/list & tools/call)."
    ),
    A2A_AGENT(
        id = "A2A_AGENT",
        displayName = "A2A External Agent",
        requiresApiKey = false,
        defaultBaseUrl = "http://127.0.0.1:4000/a2a",
        defaultModels = listOf("a2a-jsonrpc-2.0"),
        description = "Agent-to-Agent (A2A) JSON-RPC 2.0 task dispatch and agent card discovery."
    ),
    OPENAI_COMPATIBLE(
        id = "OPENAI_COMPATIBLE",
        displayName = "Custom OpenAI-Compatible",
        requiresApiKey = true,
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions",
        defaultModels = listOf("gpt-4o-mini", "local-gguf-server"),
        description = "Any OpenAI-compatible remote or local LAN inference server."
    ),
    LOCAL_OFFLINE(
        id = "LOCAL_OFFLINE",
        displayName = "Offline Command Engine",
        requiresApiKey = false,
        defaultBaseUrl = "local://on-device",
        defaultModels = listOf("aura-offline-rules-v1"),
        description = "Zero-network deterministic assistant for device control, memory search, and utilities."
    )
}

enum class VoiceEngineMode(val id: String, val label: String) {
    GEMINI("GEMINI", "Gemini voice (default)"),
    DEVICE_ONLY("DEVICE_ONLY", "Device voice only");

    companion object {
        fun fromId(id: String?): VoiceEngineMode =
            entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

data class AuraVoicePreset(
    val name: String,
    val genderStyle: String,
    val tone: String,
    val ttsPitch: Float,
    val ttsRate: Float,
    val geminiLiveSupported: Boolean = true
)

object AuraVoiceCatalog {
    val geminiDropdownVoices: List<String> = listOf("Kore", "Puck", "Charon", "Aoede", "Fenrir")

    val presets = listOf(
        AuraVoicePreset("Kore", "Female", "Clear & balanced", 1.0f, 1.0f),
        AuraVoicePreset("Puck", "Male / Upbeat", "Bright & playful", 1.0f, 1.0f),
        AuraVoicePreset("Charon", "Male / Deep", "Informative & steady", 1.0f, 1.0f),
        AuraVoicePreset("Aoede", "Female", "Warm & conversational", 1.0f, 1.0f),
        AuraVoicePreset("Fenrir", "Male / Expressive", "Dynamic & bold", 1.0f, 1.0f),
        AuraVoicePreset("Zephyr", "Neutral / Bright", "Crisp & energetic", 1.0f, 1.0f),
        AuraVoicePreset("Leda", "Female", "Calm & authoritative", 1.0f, 1.0f),
        AuraVoicePreset("Callirrhoe", "Female", "Expressive & natural", 1.0f, 1.0f),
        AuraVoicePreset("Autonoe", "Male / Deep", "Grounded & analytical", 1.0f, 1.0f)
    )

    fun findPreset(name: String): AuraVoicePreset =
        presets.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: presets.first()
}

enum class AuraLanguage(val code: String, val displayName: String, val localeTag: String) {
    ENGLISH("EN", "English", "en-US"),
    URDU("UR", "اردو (Urdu)", "ur-PK"),
    ROMAN_URDU("ROMAN_UR", "Roman Urdu", "en-US")
}

object MultilingualDetector {
    private val urduScriptRegex = Regex("[\\u0600-\\u06FF]")
    private val romanUrduTokens = setOf(
        "kya", "kaise", "kaisi", "karo", "kardo", "karna", "hai", "hain", "mera", "meri",
        "mujhe", "aap", "tum", "batao", "dikhao", "kholo", "chalo", "band", "acha", "theek",
        "nahi", "haan", "shukriya", "bhai", "yaar", "samjhao", "likho", "bhejo", "madad"
    )

    fun detect(text: String, preferred: String = "AUTO"): AuraLanguage {
        when (preferred) {
            "EN" -> return AuraLanguage.ENGLISH
            "UR" -> return AuraLanguage.URDU
            "ROMAN_UR" -> return AuraLanguage.ROMAN_URDU
        }
        if (urduScriptRegex.containsMatchIn(text)) return AuraLanguage.URDU
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        val matches = words.count { it in romanUrduTokens }
        if (matches >= 2 || (words.size <= 4 && matches >= 1)) {
            return AuraLanguage.ROMAN_URDU
        }
        return AuraLanguage.ENGLISH
    }
}
