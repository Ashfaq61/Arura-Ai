package com.example.aura.network

import com.example.aura.backend.BackendServiceGateway
import com.example.aura.memory.ConnectedAgentNodeEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.security.ApiKeySlot
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender

data class InboundAgentPayloadCheck(
    val allowed: Boolean,
    val sanitizedMessage: String,
    val injectionDetected: Boolean,
    val policyTamperDetected: Boolean,
    val securityNote: String
)

/**
 * Enforces Zero-Trust validation for all external AI agents, webhooks, MCP/A2A endpoints:
 * - External agents start UNTRUSTED until authenticated & TLS-verified.
 * - API keys/tokens are stored exclusively in Android Keystore AES-GCM vault.
 * - Every inbound message is scanned for prompt injection and security-policy tampering.
 * - External agents can NEVER modify PermissionGuard, Vault, or Approval policies.
 */
class AgentTrustSecurityGuard(
    private val vault: AuraKeystoreVault,
    private val injectionDefender: PromptInjectionDefender,
    private val privacyRouter: PrivacyDataRouter,
    private val memoryRepository: MemoryRepository
) {
    fun saveExternalAgentToken(nodeId: String, label: String, rawToken: String) {
        val clean = rawToken.trim()
        val slotId = tokenSlotId(nodeId)
        if (clean.isBlank()) {
            vault.deleteApiKeySlot(slotId)
            return
        }
        vault.saveApiKeySlot(
            slot = ApiKeySlot(
                id = slotId,
                providerId = PROVIDER_EXT_AGENT,
                label = label,
                isEnabled = true
            ),
            rawApiKey = clean
        )
    }

    fun hasExternalAgentToken(nodeId: String): Boolean {
        return !vault.getDecryptedKey(tokenSlotId(nodeId)).isNullOrBlank()
    }

    fun getExternalAgentToken(nodeId: String): String {
        return vault.getDecryptedKey(tokenSlotId(nodeId)).orEmpty()
    }

    fun deleteExternalAgentToken(nodeId: String) {
        vault.deleteApiKeySlot(tokenSlotId(nodeId))
    }

    fun isSecureEndpoint(endpointUrl: String): Boolean {
        val clean = endpointUrl.trim().lowercase()
        return clean.startsWith("local://aura/") ||
            clean.startsWith("https://") ||
            clean.startsWith("http://127.0.0.1") ||
            clean.startsWith("http://localhost")
    }

    fun isPolicyModificationAttempt(payload: String): Boolean {
        val lower = payload.lowercase()
        val policyTokens = listOf(
            "disable permissionguard",
            "modify permissionguard",
            "override autonomy",
            "auto approve all",
            "bypass approval",
            "grant all permissions",
            "disable security",
            "export keystore",
            "reveal api key",
            "modify security policy"
        )
        return policyTokens.any { lower.contains(it) } ||
            !injectionDefender.verifyNoSecurityTampering(payload).first
    }

    suspend fun validateInboundExternalPayload(
        node: ConnectedAgentNodeEntity,
        rawPayload: String
    ): InboundAgentPayloadCheck {
        val scrubbed = scrubSecrets(rawPayload)
        if (node.trustStatus == "BLOCKED") {
            memoryRepository.logAudit("AgentTrustGuard", "BLOCKED_AGENT:${node.nodeId}", 4, "BLOCKED", "Blocked agent attempted inbound message.")
            return InboundAgentPayloadCheck(
                allowed = false,
                sanitizedMessage = "[Blocked untrusted agent ${node.name}]",
                injectionDetected = false,
                policyTamperDetected = false,
                securityNote = "Agent is blocked by user/security policy."
            )
        }

        if (node.trustStatus == "UNTRUSTED" && !node.isBuiltIn) {
            memoryRepository.logAudit("AgentTrustGuard", "UNTRUSTED_REJECT:${node.nodeId}", 3, "BLOCKED", "External agent not yet authenticated.")
            return InboundAgentPayloadCheck(
                allowed = false,
                sanitizedMessage = "[Unauthenticated external agent ${node.name} cannot execute or inject council data]",
                injectionDetected = false,
                policyTamperDetected = false,
                securityNote = "External agent must be authenticated before participating."
            )
        }

        if (isPolicyModificationAttempt(scrubbed)) {
            memoryRepository.logAudit(
                "AgentTrustGuard",
                "POLICY_TAMPER_BLOCKED:${node.nodeId}",
                4,
                "BLOCKED",
                "External agent attempted unauthorized security/permission policy mutation."
            )
            return InboundAgentPayloadCheck(
                allowed = false,
                sanitizedMessage = "[SECURITY BLOCK: External agent attempted unauthorized policy/permission change]",
                injectionDetected = true,
                policyTamperDetected = true,
                securityNote = "Blocked unauthorized security policy modification attempt from ${node.name}."
            )
        }

        val scan = injectionDefender.scanUntrustedContent(node.name, scrubbed)
        if (scan.isSuspicious) {
            memoryRepository.logAudit(
                "AgentTrustGuard",
                "PROMPT_INJECTION_FILTERED:${node.nodeId}",
                3,
                "BLOCKED",
                "Signals: ${scan.matchedSignals.joinToString()}"
            )
            return InboundAgentPayloadCheck(
                allowed = false,
                sanitizedMessage = "[PROMPT INJECTION FILTERED from ${node.name}: ${scan.matchedSignals.joinToString()}]",
                injectionDetected = true,
                policyTamperDetected = false,
                securityNote = "Filtered suspicious injection instructions: ${scan.matchedSignals.joinToString()}"
            )
        }

        return InboundAgentPayloadCheck(
            allowed = true,
            sanitizedMessage = scrubbed,
            injectionDetected = false,
            policyTamperDetected = false,
            securityNote = "Verified & sanitized inbound payload."
        )
    }

    fun scrubSecrets(text: String): String {
        val first = BackendServiceGateway.sanitizeForLog(text)
        return privacyRouter.sanitizeForExternalProvider(first, redactEnabled = true).redactedText
    }

    companion object {
        private const val PROVIDER_EXT_AGENT = "EXT_AGENT_NODE"
        private fun tokenSlotId(nodeId: String) = "ext_token_$nodeId"
    }
}
