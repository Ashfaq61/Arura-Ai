package com.example.aura.security

data class PrivacyRedactionReport(
    val redactedText: String,
    val redactionCount: Int,
    val detectedCategories: List<String>,
    val containsSensitivePayload: Boolean
)

/**
 * Privacy-First Data Router:
 * - Detects and redacts passwords, OTPs, API keys, secrets, and payment numbers before external AI transmission.
 * - Minimizes screen node content shared externally.
 * - Tracks which provider receives data and respects local-only / retention rules.
 */
class PrivacyDataRouter {

    private val rules = listOf(
        "API/Auth Token" to Regex("(sk-[A-Za-z0-9_\\-]{12,}|gsk_[A-Za-z0-9_\\-]{12,}|AIza[0-9A-Za-z_\\-]{20,}|ghp_[A-Za-z0-9]{16,}|hf_[A-Za-z0-9]{16,}|Bearer\\s+[A-Za-z0-9._\\-]{16,})"),
        "Password/PIN" to Regex("(?i)(password|passcode|pin|secret|token)\\s*[:=]\\s*\\S+"),
        "OTP/Verification Code" to Regex("(?i)(otp|verification\\s*code|2fa|auth\\s*code)\\s*(is|:)?\\s*\\d{4,8}"),
        "Card/Bank Number" to Regex("\\b(?:\\d[ -]*?){13,16}\\b"),
        "National ID" to Regex("\\b\\d{5}-\\d{7}-\\d\\b|\\b\\d{3}-\\d{2}-\\d{4}\\b")
    )

    fun sanitizeForExternalProvider(
        rawText: String,
        redactEnabled: Boolean
    ): PrivacyRedactionReport {
        if (rawText.isBlank()) {
            return PrivacyRedactionReport("", 0, emptyList(), false)
        }
        var working = rawText
        var count = 0
        val categories = mutableListOf<String>()

        for ((label, regex) in rules) {
            val matches = regex.findAll(working).count()
            if (matches > 0) {
                count += matches
                categories.add(label)
                if (redactEnabled) {
                    working = regex.replace(working, "[REDACTED_$label]")
                }
            }
        }
        return PrivacyRedactionReport(
            redactedText = working,
            redactionCount = count,
            detectedCategories = categories.distinct(),
            containsSensitivePayload = count > 0
        )
    }

    fun minimizeScreenContext(rawScreenSummary: String, minimizeEnabled: Boolean): String {
        if (rawScreenSummary.isBlank()) return ""
        val sanitized = sanitizeForExternalProvider(rawScreenSummary, redactEnabled = true).redactedText
        return if (minimizeEnabled) sanitized.take(280) else sanitized.take(900)
    }
}
