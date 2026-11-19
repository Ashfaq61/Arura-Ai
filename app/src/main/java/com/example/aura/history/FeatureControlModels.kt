package com.example.aura.history

import com.example.aura.automation.DeviceHealthSnapshot

object FeaturePipelineStages {
    val NAMES = listOf(
        "SAVED_REQUEST",                 // 1
        "ASSIGNED_REQUEST_ID",           // 2
        "IDENTIFIED_AGENTS",             // 3
        "AGENT_CONSULTATION",            // 4
        "RECOMMENDATIONS_ALTERNATIVES",  // 5
        "TECHNICAL_PLAN",                // 6
        "COMPAT_LICENSE_SECURITY",       // 7
        "IDENTIFY_APIS_PERMS_DEPS",      // 8
        "DEVELOP_OR_INTEGRATE",          // 9
        "SECURITY_COMPAT_CHECKS",        // 10
        "EXECUTE_TESTS",                 // 11
        "APPROVAL_POLICY_GATE",          // 12
        "INSTALL_AND_ACTIVATE",          // 13
        "UPDATE_FEATURE_REGISTRY",       // 14
        "SAVE_DEV_TEST_HISTORY",         // 15
        "NOTIFY_OUTCOME"                 // 16
    )

    fun stageLabel(stage: Int): String = NAMES.getOrElse((stage - 1).coerceIn(0, NAMES.lastIndex)) { "COMPLETED" }
}

data class ParsedFeatureVoiceCommand(
    val requestMode: String,
    val prompt: String,
    val prefersRecommendedInboxItem: Boolean = false,
    val prefersDiscussionProposal: Boolean = false
)

object FeatureVoiceCommandParser {
    /**
     * Parses Req #4 Hinglish & English voice/text feature requests:
     * - "AURA, ye feature bhi add karo"
     * - "AURA, GitHub wale agent ka recommended tool install karo"
     * - "AURA, is feature ko khud develop karo"
     * - "AURA, is tool ko existing automation ke saath connect karo"
     * - "AURA, is feature ka advanced version bana do"
     * - "AURA, is discussion wala feature bhi add karo"
     */
    fun parse(rawInput: String): ParsedFeatureVoiceCommand? {
        val clean = rawInput.trim()
        val lower = clean.lowercase()
        return when {
            lower.contains("discussion wala feature") ||
                lower.contains("add feature from discussion") ||
                lower.contains("is discussion wala") ->
                ParsedFeatureVoiceCommand("FROM_DISCUSSION", clean, prefersDiscussionProposal = true)

            lower.contains("recommended tool install karo") ||
                lower.contains("github wale agent ka recommended") ||
                lower.contains("install recommended feature") ->
                ParsedFeatureVoiceCommand("FROM_RECOMMENDATION", clean, prefersRecommendedInboxItem = true)

            lower.contains("khud develop karo") ||
                lower.contains("develop my own version") ||
                lower.contains("khud banao") ->
                ParsedFeatureVoiceCommand("NEW_TOOL", clean)

            lower.contains("existing automation ke saath connect") ||
                lower.contains("combine tools") ||
                lower.contains("automation se joro") ->
                ParsedFeatureVoiceCommand("COMBINE_TOOLS", clean)

            lower.contains("advanced version bana do") ||
                lower.contains("improve existing feature") ||
                lower.contains("upgrade feature") ->
                ParsedFeatureVoiceCommand("IMPROVE_FEATURE", clean)

            lower.contains("create specialist agent") ||
                lower.contains("new specialist agent") ||
                lower.contains("naya agent banao") ->
                ParsedFeatureVoiceCommand("CREATE_AGENT", clean)

            lower.contains("ye feature bhi add karo") ||
                lower.contains("add new feature") ||
                lower.startsWith("add feature ") ->
                ParsedFeatureVoiceCommand("NATURAL", clean)

            else -> null
        }
    }
}

data class ApprovalRequirementEvaluation(
    val requiresExplicitApproval: Boolean,
    val approvalReason: String,
    val riskSummary: String
)

object ChangeApprovalPolicyEvaluator {
    private val SENSITIVE_WORD_REGEXES = listOf(
        Regex("\\bsms\\b"),
        Regex("\\bphone call\\b|\\bvoice call\\b|\\bmake (a )?call\\b|\\bcall_phone\\b|\\bcall log\\b"),
        Regex("\\bwhatsapp\\b"),
        Regex("\\bmessaging\\b|\\bsend message\\b"),
        Regex("\\bpublish(ing)?\\b"),
        Regex("github\\.com/"),
        Regex("huggingface\\.co/"),
        Regex("\\bexternal account\\b"),
        Regex("\\bpaid\\b|\\bbilling\\b"),
        Regex("\\bcredential\\b|\\btoken\\b"),
        Regex("accessibility_bind|system_alert"),
        Regex("\\bcontacts\\b|\\blocation\\b|\\bcamera\\b|\\bmicrophone\\b|\\brecord_audio\\b"),
        Regex("\\bapk\\b|\\bnative\\b|\\bdex\\b|\\bbinary\\b")
    )

    fun evaluate(
        requestMode: String,
        permissionsRequired: String,
        sourceUrl: String,
        summaryAndPrompt: String,
        health: DeviceHealthSnapshot
    ): ApprovalRequirementEvaluation {
        val combined = "$requestMode $permissionsRequired $sourceUrl $summaryAndPrompt"
            .lowercase()
            .replace("without network calls", "offline")
            .replace("network calls", "network")
        val matched = SENSITIVE_WORD_REGEXES.mapNotNull { rx ->
            rx.find(combined)?.value
        }.distinct()

        return when {
            requestMode == "GITHUB_REPO" || requestMode == "HF_MODEL" || sourceUrl.startsWith("http") ->
                ApprovalRequirementEvaluation(
                    requiresExplicitApproval = true,
                    approvalReason = "External repository/model integration requires explicit user review and build-time/approved verification (runtime binary loading is never auto-installed).",
                    riskSummary = "EXTERNAL_REFERENCE (${matched.joinToString().ifBlank { "http_source" }})"
                )
            matched.isNotEmpty() ->
                ApprovalRequirementEvaluation(
                    requiresExplicitApproval = true,
                    approvalReason = "Sensitive scope or external capability detected (${matched.joinToString(", ")}); explicit approval required.",
                    riskSummary = "SENSITIVE_SCOPE"
                )
            !health.offlineLlmEligible && requestMode == "HF_MODEL" ->
                ApprovalRequirementEvaluation(
                    requiresExplicitApproval = true,
                    approvalReason = "Device RAM (${health.totalRamMb} MB) requires explicit user confirmation for local model evaluation.",
                    riskSummary = "RESOURCE_GUARD"
                )
            else ->
                ApprovalRequirementEvaluation(
                    requiresExplicitApproval = false,
                    approvalReason = "Safe, reversible sandboxed MCP/workflow/agent change within pre-authorized permissions.",
                    riskSummary = "SAFE_REVERSIBLE"
                )
        }
    }
}

/**
 * Typed UI state and sealed log entry hierarchy for the Agent Communication History Center.
 */
sealed class AgentHistoryLogEntry {
    abstract val id: Long
    abstract val conversationId: String
    abstract val agentId: String
    abstract val agentName: String
    abstract val participatingAgents: List<String>
    abstract val topic: String
    abstract val userOriginalRequest: String
    abstract val qaTurns: List<QaExchangeTurn>
    abstract val decisionsAndReasons: String
    abstract val finalOutcome: String
    abstract val timestamp: Long
    abstract val channelLabel: String

    data class BrainToAgent(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long,
        val actionsPerformed: List<String> = emptyList()
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "BRAIN_TO_AGENT"
    }

    data class AgentToAgent(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "A2A"
    }

    data class CouncilDiscussion(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long,
        val proposedFeatures: List<String> = emptyList()
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "AI_COUNCIL"
    }

    data class ExternalProvider(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long,
        val sensitivityRedacted: Boolean = true
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "EXTERNAL_PROVIDER"
    }

    data class ResearchAndDev(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long,
        val researchFindings: String = "",
        val testResults: String = "",
        val repositoryLinks: List<String> = emptyList()
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "RD_AGENT"
    }

    data class ToolDiscovery(
        override val id: Long,
        override val conversationId: String,
        override val agentId: String,
        override val agentName: String,
        override val participatingAgents: List<String>,
        override val topic: String,
        override val userOriginalRequest: String,
        override val qaTurns: List<QaExchangeTurn>,
        override val decisionsAndReasons: String,
        override val finalOutcome: String,
        override val timestamp: Long,
        val recommendationsMade: String = "",
        val repositoryLinks: List<String> = emptyList()
    ) : AgentHistoryLogEntry() {
        override val channelLabel: String = "TOOL_DISCOVERY"
    }
}

data class AgentCommunicationHistoryUiState(
    val searchQuery: String = "",
    val selectedChannel: String = "ALL",
    val selectedAgentFilter: String = "",
    val selectedTopicFilter: String = "",
    val dateWindow: String = "ALL",
    val entries: List<AgentHistoryLogEntry> = emptyList(),
    val isLoading: Boolean = false
)

