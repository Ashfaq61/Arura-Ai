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
        displayName = "Google Gemini (Firebase AI Logic)",
        requiresApiKey = false,
        defaultBaseUrl = "firebase://app-check",
        defaultModels = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"),
        description = "Keyless App-Check attested Gemini models with multimodal vision & streaming."
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
    HUGGING_FACE(
        id = "HUGGING_FACE",
        displayName = "Hugging Face Inference",
        requiresApiKey = true,
        defaultBaseUrl = "https://router.huggingface.co/v1/chat/completions",
        defaultModels = listOf("Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.3-70B-Instruct", "mistralai/Mistral-7B-Instruct-v0.3"),
        description = "Open-source models hosted on Hugging Face Inference Router."
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

data class AuraVoicePreset(
    val name: String,
    val genderStyle: String,
    val tone: String,
    val ttsPitch: Float,
    val ttsRate: Float,
    val geminiLiveSupported: Boolean = true
)

object AuraVoiceCatalog {
    val presets = listOf(
        AuraVoicePreset("Kore", "Female", "Clear & balanced", 1.05f, 1.0f),
        AuraVoicePreset("Aoede", "Female", "Warm & conversational", 1.10f, 0.98f),
        AuraVoicePreset("Zephyr", "Neutral / Bright", "Crisp & energetic", 1.0f, 1.06f),
        AuraVoicePreset("Leda", "Female", "Calm & authoritative", 0.96f, 0.95f),
        AuraVoicePreset("Callirrhoe", "Female", "Expressive & natural", 1.08f, 1.02f),
        AuraVoicePreset("Autonoe", "Male / Deep", "Grounded & analytical", 0.85f, 0.97f)
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
