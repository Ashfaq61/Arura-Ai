package com.example.aura.history

import com.example.aura.agents.SpecialistAgent
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.memory.AgentActivityEventEntity
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.FeatureRequestEntity
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.memory.TaskRecordEntity
import com.example.aura.security.PrivacyDataRouter
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QaExchangeTurn(
    val speaker: String,
    val target: String,
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class IndividualAgentProfileSummary(
    val agent: SpecialistAgent,
    val assignedTasks: List<TaskRecordEntity>,
    val conversations: List<AgentConversationEntity>,
    val researchItems: List<ResearchProjectEntity>,
    val discoveredFeatures: List<FeatureRecommendationEntity>,
    val implementedRegistryEvents: List<FeatureRegistryHistoryEntity>,
    val relatedRequests: List<FeatureRequestEntity>,
    val failuresAndErrors: List<AgentActivityEventEntity>,
    val successRatePercent: Int,
    val workloadLabel: String
)

/**
 * Records sanitized, chronological agent conversations and real-time runtime activity events.
 * Enforces strict zero-secret policy: never stores API keys, passwords, or auth tokens.
 */
class AgentHistoryRecorder(
    private val historyRepo: AgentHistoryRepository,
    private val privacyRouter: PrivacyDataRouter = PrivacyDataRouter()
) {
    fun sanitizeText(raw: String, prefs: AuraPreferences = AuraPreferences()): String {
        if (raw.isBlank()) return ""
        val gate = BackendServiceGateway.sanitizeForLog(raw)
        return privacyRouter.sanitizeForExternalProvider(
            rawText = gate,
            redactEnabled = prefs.redactSensitiveData || !prefs.recordSensitiveChatDetails
        ).redactedText
    }

    fun sanitizeUserPrompt(rawPrompt: String, prefs: AuraPreferences): Pair<String, Boolean> {
        if (rawPrompt.isBlank()) return "" to false
        if (prefs.privateMode) {
            return "[Private Mode enabled — user prompt omitted from persistent log]" to true
        }
        val firstPass = BackendServiceGateway.sanitizeForLog(rawPrompt)
        val report = privacyRouter.sanitizeForExternalProvider(firstPass, redactEnabled = true)
        val wasRedacted = report.containsSensitivePayload || firstPass != rawPrompt
        return if (!prefs.recordSensitiveChatDetails && report.containsSensitivePayload) {
            report.redactedText to true
        } else {
            report.redactedText to wasRedacted
        }
    }

    suspend fun recordConversation(
        agentId: String,
        agentName: String,
        participatingAgents: List<String>,
        channelType: String,
        topic: String,
        userOriginalRequest: String,
        qaTurns: List<QaExchangeTurn>,
        recommendationsMade: String = "",
        researchFindings: String = "",
        links: List<String> = emptyList(),
        decisionsAndReasons: String = "",
        proposedTools: List<String> = emptyList(),
        actionsPerformed: List<String> = emptyList(),
        testResults: String = "",
        finalOutcome: String,
        prefs: AuraPreferences = AuraPreferences(),
        sessionId: String = "SESSION-${System.currentTimeMillis() / 60000L}"
    ): Long {
        val (safePrompt, redacted) = sanitizeUserPrompt(userOriginalRequest, prefs)
        val qaJson = JSONArray().apply {
            qaTurns.forEach { t ->
                put(JSONObject().apply {
                    put("speaker", sanitizeText(t.speaker, prefs))
                    put("target", sanitizeText(t.target, prefs))
                    put("question", sanitizeText(t.question, prefs))
                    put("answer", sanitizeText(t.answer, prefs))
                    put("timestamp", t.timestamp)
                })
            }
        }.toString()

        return historyRepo.insertConversation(
            AgentConversationEntity(
                conversationId = "CONV-${UUID.randomUUID().toString().take(8).uppercase()}",
                sessionId = sessionId,
                agentId = agentId,
                agentName = agentName,
                participatingAgentsCsv = participatingAgents.distinct().joinToString(", "),
                channelType = channelType,
                topic = sanitizeText(topic, prefs),
                userOriginalRequest = safePrompt,
                qaExchangedJson = qaJson,
                recommendationsMade = sanitizeText(recommendationsMade, prefs),
                researchFindings = sanitizeText(researchFindings, prefs),
                repositoryAndModelLinksCsv = links.distinct().joinToString(", "),
                decisionsAndReasons = sanitizeText(decisionsAndReasons, prefs),
                proposedFeaturesOrToolsCsv = proposedTools.distinct().joinToString(", "),
                actionsPerformedCsv = actionsPerformed.distinct().joinToString(", "),
                testResults = sanitizeText(testResults, prefs),
                finalOutcome = sanitizeText(finalOutcome, prefs),
                sensitivityRedacted = redacted || prefs.redactSensitiveData
            )
        )
    }

    suspend fun startLiveActivity(
        agentName: String,
        activityType: String,
        targetAgentOrModule: String,
        summary: String,
        relatedRequestId: String = "",
        prefs: AuraPreferences = AuraPreferences()
    ): Long {
        return historyRepo.insertActivity(
            AgentActivityEventEntity(
                agentName = agentName,
                activityType = activityType,
                targetAgentOrModule = targetAgentOrModule,
                summary = sanitizeText(summary, prefs),
                status = "LIVE",
                relatedRequestId = relatedRequestId
            )
        )
    }

    suspend fun completeLiveActivity(
        eventId: Long,
        status: String,
        summary: String,
        errorReason: String = "",
        prefs: AuraPreferences = AuraPreferences()
    ) {
        historyRepo.updateActivityStatus(
            id = eventId,
            status = status,
            summary = sanitizeText(summary, prefs),
            errorReason = sanitizeText(errorReason, prefs),
            completedAt = if (status == "LIVE") null else System.currentTimeMillis()
        )
    }

    fun parseQaTurns(json: String): List<QaExchangeTurn> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QaExchangeTurn(
                            speaker = o.optString("speaker", "Agent"),
                            target = o.optString("target", "Orchestrator"),
                            question = o.optString("question", ""),
                            answer = o.optString("answer", ""),
                            timestamp = o.optLong("timestamp", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun buildAgentProfileAggregate(
        agent: SpecialistAgent,
        allTasks: List<TaskRecordEntity>,
        allConversations: List<AgentConversationEntity>,
        allResearch: List<ResearchProjectEntity>,
        allRecommendations: List<FeatureRecommendationEntity>,
        allRegistry: List<FeatureRegistryHistoryEntity>,
        allRequests: List<FeatureRequestEntity>,
        allActivities: List<AgentActivityEventEntity>
    ): IndividualAgentProfileSummary {
        val assigned = allTasks.filter { it.assignedAgents.contains(agent.name, ignoreCase = true) }
        val convs = allConversations.filter {
            it.agentName.equals(agent.name, ignoreCase = true) ||
                it.participatingAgentsCsv.contains(agent.name, ignoreCase = true)
        }
        val research = if (agent.name.contains("GitHub", true)) {
            allResearch.filter { it.sourcePlatform == "GITHUB" || it.sourcePlatform == "RESEARCH_EVAL" }
        } else if (agent.name.contains("Hugging Face", true)) {
            allResearch.filter { it.sourcePlatform == "HUGGING_FACE" }
        } else if (agent.name.contains("Research", true) || agent.name.contains("Discovery", true)) {
            allResearch
        } else {
            emptyList()
        }
        val discovered = allRecommendations.filter {
            it.discoveringAgent.equals(agent.name, true) ||
                it.otherAgentOpinionsJson.contains(agent.name, true)
        }
        val implemented = allRegistry.filter {
            it.recommendingAgent.equals(agent.name, true) ||
                it.participatingAgentsCsv.contains(agent.name, true)
        }
        val requests = allRequests.filter { it.assignedAgentsCsv.contains(agent.name, true) }
        val failures = allActivities.filter {
            it.agentName.equals(agent.name, true) &&
                (it.status == "FAILED" || it.errorReason.isNotBlank())
        }

        val totalRuns = assigned.size + convs.size
        val failCount = assigned.count { it.status == "FAILED" } + failures.size
        val successPct = if (totalRuns <= 0) 100 else (((totalRuns - failCount).coerceAtLeast(0) * 100) / totalRuns)
        val liveCount = allActivities.count { it.agentName.equals(agent.name, true) && it.status == "LIVE" }
        val workload = when {
            liveCount > 0 -> "ACTIVE ($liveCount live)"
            agent.completedTasks > 10 -> "BUSY (${agent.completedTasks} tasks)"
            else -> "LIGHT (${agent.completedTasks} tasks)"
        }

        return IndividualAgentProfileSummary(
            agent = agent,
            assignedTasks = assigned,
            conversations = convs,
            researchItems = research,
            discoveredFeatures = discovered,
            implementedRegistryEvents = implemented,
            relatedRequests = requests,
            failuresAndErrors = failures,
            successRatePercent = successPct,
            workloadLabel = workload
        )
    }
}
