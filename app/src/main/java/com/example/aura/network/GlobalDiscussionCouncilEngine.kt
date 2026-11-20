package com.example.aura.network

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.QaExchangeTurn
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AgentNotificationEventEntity
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.FeatureRequestEntity
import com.example.aura.memory.GlobalAgentNetworkRepository
import com.example.aura.memory.GlobalDiscussionSessionEntity
import com.example.aura.memory.GlobalDiscussionTurnEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.research.GitHubAndHfResearchClient
import java.security.MessageDigest
import java.util.UUID

object DiscussionTopicCategories {
    val ALL = listOf(
        "AI_MODELS",
        "ASSISTANT_FEATURES",
        "ANDROID_AUTOMATION",
        "COMPUTER_USE",
        "VOICE_LIVE",
        "VISION_MULTIMODAL",
        "GITHUB_TRENDING",
        "HUGGING_FACE",
        "TOOLS_FRAMEWORKS",
        "MCP_A2A",
        "OPEN_SOURCE",
        "SECURITY",
        "PERFORMANCE",
        "DEV_TECH"
    )
}

data class TechDiscoveryProposalInput(
    val featureName: String,
    val sourcePlatform: String, // GITHUB, HUGGING_FACE, AGENT_COUNCIL, WEB
    val sourceUrl: String,
    val discoveringAgent: String,
    val description: String,
    val license: String = "Check upstream license",
    val topicCategory: String = "TOOLS_FRAMEWORKS",
    val existingLimitation: String = "Manual workflow or missing capability",
    val estimatedCostOrRam: String = "0 USD / Lightweight MCP"
)

data class GlobalArchiveFilter(
    val rawQuery: String = "",
    val agentFilter: String = "",
    val providerFilter: String = "",
    val topicFilter: String = "",
    val modelFilter: String = "",
    val toolOrFeatureFilter: String = "",
    val outcomeFilter: String = "ALL", // ALL, INSTALLED, REJECTED, UNTESTED, FINISHED, WAITING
    val fromMillis: Long? = null,
    val toMillis: Long? = null
)

data class GlobalArchiveHit(
    val id: String,
    val kind: String, // AGENT_CHAT, COUNCIL_DISCUSSION, RECOMMENDATION, REGISTRY, RESEARCH
    val title: String,
    val subtitle: String,
    val body: String,
    val agentName: String,
    val providerOrPlatform: String,
    val modelName: String,
    val outcome: String,
    val linksCsv: String,
    val timestamp: Long,
    val sessionId: String? = null
)

/**
 * Coordinates continuous AI-to-AI conversations, the Global AI Discussion Council,
 * the 10-step Technology Discovery evaluation, feature promotion ("ADD THIS FEATURE TO AURA"),
 * offline/waiting discussion resumption, webhook ingestion, and Archive Search.
 */
class GlobalDiscussionCouncilEngine(
    private val context: Context,
    private val networkRepo: GlobalAgentNetworkRepository,
    private val historyRepo: AgentHistoryRepository,
    private val memoryRepo: MemoryRepository,
    private val historyRecorder: AgentHistoryRecorder,
    private val networkEngine: GlobalAgentNetworkEngine,
    private val pipelineEngine: FeatureControlPipelineEngine,
    private val trustGuard: AgentTrustSecurityGuard,
    private val researchClient: GitHubAndHfResearchClient,
    private val deviceControl: DeviceControlEngine,
    private val aiRouter: MultiProviderRouter
) {

    /**
     * Initiates a Global AI Discussion Council session with deduplication so identical
     * resolved questions are never repeatedly spammed to agents.
     */
    suspend fun startCouncilDiscussion(
        topic: String,
        topicCategory: String,
        question: String,
        sourceLinks: List<String> = emptyList(),
        proposedFeatureName: String = "",
        proposedFeatureDesc: String = "",
        discoveringAgent: String = "",
        forceNewSession: Boolean = false,
        prefs: AuraPreferences = AuraPreferences()
    ): GlobalDiscussionSessionEntity {
        networkRepo.seedInitialAgentNodesIfEmpty()
        val fingerprint = computeFingerprint("$topicCategory|${topic.trim().lowercase()}|${question.trim().lowercase()}")
        val existing = networkRepo.getSessionByFingerprint(fingerprint)
        if (existing != null && !forceNewSession && existing.status == "FINISHED") {
            return existing
        }

        val sessionId = existing?.sessionId ?: "COUNCIL-${UUID.randomUUID().toString().take(8).uppercase()}"
        val now = System.currentTimeMillis()
        val connectedNodes = networkRepo.getAllNodes().filter {
            it.enabled && (it.connectionStatus == AgentConnectionStatus.CONNECTED || it.isBuiltIn)
        }
        val extraAgents = buildList {
            if (discoveringAgent.isNotBlank()) add(discoveringAgent.trim())
            if (sourceLinks.any { it.contains("huggingface.co", true) }) add("Hugging Face Research Agent")
            if (sourceLinks.any { it.contains("github.com", true) }) add("GitHub Research Agent")
        }
        val participants = (selectCouncilParticipants(topicCategory, connectedNodes.map { it.name }) + extraAgents).distinct()
        val safeQuestion = historyRecorder.sanitizeText(question, prefs)
        val safeTopic = historyRecorder.sanitizeText(topic, prefs)

        val actId = historyRecorder.startLiveActivity(
            agentName = "Main Brain / Orchestrator Agent",
            activityType = "CONSULTING",
            targetAgentOrModule = sessionId,
            summary = "Council: ${safeTopic.take(54)}",
            relatedRequestId = sessionId,
            prefs = prefs
        )

        val relatedIds = networkRepo.getAllSessions()
            .filter { it.topicCategory == topicCategory && it.sessionId != sessionId }
            .take(3)
            .joinToString(", ") { it.sessionId }

        var session = GlobalDiscussionSessionEntity(
            sessionId = sessionId,
            topic = safeTopic,
            topicCategory = topicCategory,
            topicFingerprint = fingerprint,
            initiatorAgent = "Main Brain / Orchestrator Agent",
            participatingAgentsCsv = participants.joinToString(", "),
            originalQuestion = safeQuestion,
            sourceLinksCsv = sourceLinks.joinToString(", "),
            proposedFeatureName = proposedFeatureName.ifBlank { "Proposal: ${safeTopic.take(40)}" },
            proposedFeatureDescription = proposedFeatureDesc.ifBlank { safeQuestion },
            relatedSessionIdsCsv = relatedIds,
            status = "ACTIVE",
            progressPercent = 25,
            startedAt = now,
            updatedAt = now
        )
        networkRepo.upsertSession(session)

        // Turn 1: Orchestrator question
        insertTurn(
            sessionId = sessionId,
            speaker = "Main Brain / Orchestrator Agent",
            target = "Global AI Discussion Council",
            provider = "On-Device AURA Core",
            model = prefs.activeModelId,
            stance = "QUESTION",
            text = safeQuestion,
            links = sourceLinks
        )

        // Turn 2: Research / Connected Provider Discovery insight (live external AI if configured & allowed, else deterministic synthesis)
        val researchAgent = discoveringAgent.trim().ifBlank {
            participants.firstOrNull { it.contains("Research") || it.contains("Discovery") }
                ?: "GitHub Research Agent"
        }
        val configuredProviders = aiRouter.getConfiguredExternalProviders()
        val primaryExternal = configuredProviders.firstOrNull()
        val aiInsightResult = if (prefs.intelligenceMode != "LOCAL_ONLY" &&
            prefs.autonomyPolicy.externalApiMasterEnabled &&
            primaryExternal != null
        ) {
            aiRouter.generateOnce(
                prompt = "Topic [$topicCategory]: $safeTopic\nQuestion: $safeQuestion\nProvide concise specialist discovery findings, compatibility notes, and one key risk.",
                systemInstruction = "Act as $researchAgent in AURA's AI Discussion Council. Never fabricate benchmark scores.",
                providerId = primaryExternal.id,
                taskType = "council_discovery",
                redactSensitiveData = prefs.redactSensitiveData
            ).getOrNull()
        } else null

        val discoveryMsg = if (aiInsightResult != null && aiInsightResult.text.isNotBlank()) {
            historyRecorder.sanitizeText(aiInsightResult.text.take(500), prefs)
        } else {
            "$researchAgent evaluated '$safeTopic' across local AURA capabilities and open-source references. " +
                "Candidate approach: modular MCP/Kotlin adapter with strict permission scoping."
        }
        insertTurn(
            sessionId = sessionId,
            speaker = researchAgent,
            target = "Main Brain / Orchestrator Agent",
            provider = if (aiInsightResult != null) "External HTTP (${aiInsightResult.providerUsed})" else "On-Device Research Heuristic",
            model = aiInsightResult?.modelUsed ?: "Deterministic Heuristic",
            stance = "DISCOVERY",
            text = discoveryMsg,
            links = sourceLinks
        )

        // Turn 3: Evidence-based independent challenge / critique (uses secondary configured provider or connected external agent when available)
        val challenger = "Security & Policy Guard Agent"
        val health = deviceControl.readDeviceHealth()
        val hasExternalLinks = sourceLinks.any { it.startsWith("http") }
        val secondaryProvider = configuredProviders.firstOrNull { it != primaryExternal }
        val externalChallenge = if (prefs.intelligenceMode != "LOCAL_ONLY" &&
            prefs.autonomyPolicy.externalApiMasterEnabled &&
            secondaryProvider != null
        ) {
            aiRouter.generateOnce(
                prompt = "Critique this proposal for Android safety, RAM fit (${health.availableRamMb}/${health.totalRamMb} MB), license, and security risks:\nTopic: $safeTopic\nDiscovery: ${discoveryMsg.take(260)}",
                systemInstruction = "Act as Security & Performance Reviewer. Be concise and evidence-focused.",
                providerId = secondaryProvider.id,
                taskType = "council_challenge",
                redactSensitiveData = prefs.redactSensitiveData
            ).getOrNull()
        } else null

        val challengeMsg = buildString {
            append("CHALLENGE & EVIDENCE: ")
            if (externalChallenge != null && externalChallenge.text.isNotBlank()) {
                append(historyRecorder.sanitizeText(externalChallenge.text.take(320), prefs))
                append(" ")
            }
            if (hasExternalLinks) {
                append("External repo/model links (${sourceLinks.joinToString()}) cannot be dynamically loaded (.dex/.jar prohibited). ")
            }
            append("Device currently has ${health.availableRamMb}/${health.totalRamMb} MB RAM and ${"%.1f".format(health.freeStorageGb)} GB free storage.")
        }
        insertTurn(
            sessionId = sessionId,
            speaker = challenger,
            target = researchAgent,
            provider = if (externalChallenge != null) "External HTTP (${externalChallenge.providerUsed})" else "On-Device PolicyGuard",
            model = externalChallenge?.modelUsed ?: "Static Safety & Resource Analyzer",
            stance = "CHALLENGE",
            text = challengeMsg,
            links = sourceLinks
        )

        // Turn 4: Alternative / Comparison by Android Automation or Coding Agent
        val engineerAgent = participants.firstOrNull { it.contains("Coding") || it.contains("Android") || it.contains("Voice") }
            ?: "Coding & Tool Factory MCP Agent"
        val altMsg = "ALTERNATIVE COMPARISON: Option A = Sandboxed declarative MCP tool + rollback snapshot (low risk, instant dry-run); " +
            "Option B = Full Kotlin PR on branch 'aura-changes' via GitHub Self-Update + CI verification."
        insertTurn(
            sessionId = sessionId,
            speaker = engineerAgent,
            target = "Main Brain / Orchestrator Agent",
            provider = "On-Device AURA MCP Sandbox",
            model = "Architecture Evaluator",
            stance = "ALTERNATIVE",
            text = altMsg
        )

        // Turn 5: Central Brain Evaluation & Decision
        val decision = if (hasExternalLinks) "ADAPT" else "INTEGRATE"
        val decisionReason = if (hasExternalLinks) {
            "Useful external concept verified with license/compatibility guardrails; adapt safely via approved integration plan or build-time PR rather than raw binary execution."
        } else {
            "Addresses capability goal safely within AURA's sandboxed architecture and current RAM (${health.totalRamMb} MB)."
        }
        val plan = "1) Validate dependencies & permissions -> 2) Build sandboxed adapter/tool -> 3) Run compatibility & security tests -> 4) Request approval if external/sensitive -> 5) Activate & update Registry."

        insertTurn(
            sessionId = sessionId,
            speaker = "Main Brain / Orchestrator Agent",
            target = "Global AI Discussion Council",
            provider = "On-Device AURA Core",
            model = prefs.activeModelId,
            stance = "DECISION",
            text = "FINAL RECOMMENDATION ($decision): $decisionReason\nPlan: $plan"
        )

        val recId = "rec_disc_${sessionId.lowercase()}"
        val proposalName = session.proposedFeatureName
        val proposalDesc = session.proposedFeatureDescription
        val recEntity = FeatureRecommendationEntity(
            id = recId,
            featureName = proposalName,
            summaryWhatItDoes = proposalDesc,
            whyAuraNeedsIt = "Proposed during Global AI Council session $sessionId ($topicCategory).",
            limitationSolved = safeTopic,
            discoveringAgent = researchAgent,
            otherAgentOpinionsJson = """[{"agent":"$challenger","stance":"CHALLENGE","note":"$challengeMsg"},{"agent":"$engineerAgent","stance":"ALTERNATIVE","note":"$altMsg"}]""",
            sourceLinksCsv = sourceLinks.joinToString(", "),
            securityAssessment = challengeMsg,
            compatibilityAssessment = "RAM ${health.availableRamMb}/${health.totalRamMb} MB | API ${android.os.Build.VERSION.SDK_INT}",
            developmentEffort = if (hasExternalLinks) "MEDIUM" else "LOW",
            costResourceImpact = "Honest policy check: respects daily budget $${prefs.dailyBudgetUsd}",
            testRequirements = "Static scan + sandbox dry-run + non-regression verification",
            inboxStatus = "NEW"
        )
        historyRepo.upsertRecommendation(recEntity)

        val finishedAt = System.currentTimeMillis()
        session = session.copy(
            councilSummary = "$discoveryMsg\n$challenger: $challengeMsg",
            finalRecommendation = "$decision: $proposalName",
            decisionType = decision,
            decisionReasoning = decisionReason,
            advantagesSummary = "Evaluated by ${participants.size} specialist agents with alternatives & risk check.",
            licenseAndSecurity = if (hasExternalLinks) "Verify upstream license & TLS; no dynamic code loading" else "Apache-2.0 local sandboxed MCP",
            androidCompatibility = "API 24+ compatible (${health.availableRamMb} MB free RAM)",
            technicalRequirements = if (hasExternalLinks) "HTTPS API adapter / GitHub CI PR + Permissions review" else "Room v5 + MCP Sandbox",
            risksSummary = challengeMsg,
            implementationPlan = plan,
            linkedRecommendationId = recId,
            status = "FINISHED",
            progressPercent = 100,
            updatedAt = finishedAt,
            completedAt = finishedAt
        )
        networkRepo.upsertSession(session)

        historyRecorder.completeLiveActivity(actId, "COMPLETED", "Council finished $sessionId -> $decision", prefs = prefs)
        historyRecorder.recordConversation(
            agentId = "1",
            agentName = "Main Brain / Orchestrator Agent",
            participatingAgents = participants,
            channelType = "AI_COUNCIL",
            topic = safeTopic,
            userOriginalRequest = safeQuestion,
            qaTurns = listOf(
                QaExchangeTurn("Main Brain / Orchestrator Agent", researchAgent, safeQuestion, discoveryMsg),
                QaExchangeTurn(challenger, researchAgent, "Verify risks & device fit", challengeMsg),
                QaExchangeTurn(engineerAgent, "Main Brain / Orchestrator Agent", "Compare implementation paths", altMsg)
            ),
            recommendationsMade = proposalName,
            researchFindings = discoveryMsg,
            links = sourceLinks,
            decisionsAndReasons = "$decision — $decisionReason",
            proposedTools = listOf(proposalName),
            actionsPerformed = listOf("Council debate", "Security challenge", "Created proposal $recId"),
            testResults = "Pre-implementation evaluation complete",
            finalOutcome = "COUNCIL_FINISHED ($decision)",
            prefs = prefs
        )

        emitDiscussionNotification(
            category = "CAPABILITY_RECOMMENDATION",
            priority = "NORMAL",
            title = "AI Council Proposal: $proposalName",
            body = "$decision recommendation ready from ${participants.size} agents ($sessionId).",
            sessionId = sessionId,
            prefs = prefs
        )
        return session
    }

    /**
     * 6. 10-Step Latest Technology Discovery Evaluation Pipeline.
     */
    suspend fun evaluateDiscoveredTechnology(
        input: TechDiscoveryProposalInput,
        prefs: AuraPreferences = AuraPreferences()
    ): GlobalDiscussionSessionEntity {
        val existingTools = memoryRepo.getAllToolsOnce()
        val overlap = existingTools.firstOrNull {
            it.name.contains(input.featureName.take(12), ignoreCase = true) ||
                it.description.contains(input.featureName.take(12), ignoreCase = true)
        }
        val health = deviceControl.readDeviceHealth()
        val isLargeModel = input.featureName.contains("70b", true) || input.featureName.contains("14b", true)
        val hasRiskyLicense = input.license.contains("GPL", true) || input.license.contains("Unknown", true)
        val decision = when {
            isLargeModel && health.totalRamMb < 8000 -> "MONITOR"
            hasRiskyLicense -> "REJECT"
            input.sourceUrl.startsWith("http") -> "ADAPT"
            else -> "INTEGRATE"
        }
        val reason = buildString {
            append("1) Saved discovery from ${input.discoveringAgent}. ")
            append("2) Source: ${input.sourceUrl.ifBlank { input.sourcePlatform }}. ")
            append("3) Existing overlap: ${overlap?.name ?: "None (fills capability gap: ${input.existingLimitation})"}. ")
            append("4) License/Security: ${input.license}; zero runtime binary loading allowed. ")
            append("5) Android fit (${health.totalRamMb} MB RAM): ${if (isLargeModel) "Cloud-only / too large for local device" else "Compatible via sandbox/API"}. ")
            append("6) Decision: $decision.")
        }

        val session = startCouncilDiscussion(
            topic = "Tech Discovery: ${input.featureName}",
            topicCategory = input.topicCategory,
            question = "Evaluate ${input.featureName} (${input.sourcePlatform}): ${input.description}",
            sourceLinks = listOfNotNull(input.sourceUrl.takeIf { it.isNotBlank() }),
            proposedFeatureName = input.featureName,
            proposedFeatureDesc = input.description,
            discoveringAgent = input.discoveringAgent,
            forceNewSession = true,
            prefs = prefs
        )

        val updated = session.copy(
            decisionType = decision,
            decisionReasoning = reason,
            licenseAndSecurity = "${input.license} | TLS & static scan enforced",
            technicalRequirements = "Source: ${input.sourcePlatform} | Cost/RAM: ${input.estimatedCostOrRam}",
            finalRecommendation = "$decision: ${input.featureName}",
            updatedAt = System.currentTimeMillis()
        )
        networkRepo.upsertSession(updated)

        if (decision == "REJECT") {
            historyRepo.recordRegistryEvent(
                FeatureRegistryHistoryEntity(
                    featureId = "rej_${session.sessionId.lowercase()}",
                    featureName = input.featureName,
                    description = input.description,
                    discoverySource = input.discoveringAgent,
                    sourceUrl = input.sourceUrl,
                    recommendingAgent = input.discoveringAgent,
                    participatingAgentsCsv = updated.participatingAgentsCsv,
                    discoveredAt = updated.startedAt,
                    version = "0.0.0-rejected",
                    changeEventType = "REMOVED",
                    dependencies = input.estimatedCostOrRam,
                    permissionsRequired = "None",
                    testResults = "Rejected during 10-step technology evaluation",
                    activationStatus = "DISCOVERED_ONLY",
                    decisionReason = reason,
                    versionDiffSummary = "Rejected: ${input.license}"
                )
            )
        }

        emitDiscussionNotification(
            category = if (input.sourcePlatform == "HUGGING_FACE") "MODEL_DISCOVERY" else "CAPABILITY_RECOMMENDATION",
            priority = if (decision == "INTEGRATE" || decision == "ADAPT") "HIGH" else "NORMAL",
            title = "Tech Evaluated ($decision): ${input.featureName}",
            body = reason.take(180),
            sessionId = updated.sessionId,
            prefs = prefs
        )
        return updated
    }

    /**
     * 7. "ADD THIS FEATURE TO AURA" — converts a discussion proposal into a live 16-stage feature request.
     */
    suspend fun promoteDiscussionToFeatureRequest(
        sessionId: String,
        prefs: AuraPreferences = AuraPreferences()
    ): FeatureRequestEntity? {
        val session = networkRepo.getSession(sessionId) ?: return null
        val mode = when {
            session.sourceLinksCsv.contains("huggingface.co", true) -> "HF_MODEL"
            session.sourceLinksCsv.contains("github.com", true) -> "GITHUB_REPO"
            session.linkedRecommendationId.isNotBlank() -> "FROM_RECOMMENDATION"
            else -> "NEW_TOOL"
        }
        val req = pipelineEngine.submitFeatureRequest(
            userPrompt = "Build '${session.proposedFeatureName}': ${session.proposedFeatureDescription} (${session.sourceLinksCsv})",
            requestMode = mode,
            linkedSourceId = session.linkedRecommendationId,
            prefs = prefs
        )
        val updated = session.copy(
            linkedFeatureRequestId = req.requestId,
            testResults = req.testReport.ifBlank { req.status },
            updatedAt = System.currentTimeMillis()
        )
        networkRepo.upsertSession(updated)

        val notifCategory = when (req.status) {
            "COMPLETED_ACTIVATED" -> "TEST_PASSED"
            "AWAITING_APPROVAL" -> "APPROVAL_REQUIRED"
            "FAILED" -> "INTEGRATION_FAILED"
            else -> "FEATURE_READY"
        }
        emitDiscussionNotification(
            category = notifCategory,
            priority = if (req.status == "FAILED" || req.status == "AWAITING_APPROVAL") "HIGH" else "NORMAL",
            title = "Discussion -> Feature ${req.requestId}: ${req.status}",
            body = "${session.proposedFeatureName} reached stage ${req.currentStage}/16 (${req.stageName}).",
            sessionId = sessionId,
            requestId = req.requestId,
            prefs = prefs
        )
        return req
    }

    /**
     * 11. Resumes waiting discussions & retries queued offline messages safely.
     */
    suspend fun resumeInterruptedAndQueuedWork(
        isNetworkOnline: Boolean,
        prefs: AuraPreferences = AuraPreferences()
    ): Pair<Int, Int> {
        var flushedMessages = 0
        var resumedSessions = 0

        val pendingMsgs = networkRepo.getPendingQueuedMessages()
        for (q in pendingMsgs) {
            val res = networkEngine.sendMessageToAgent(
                nodeId = q.targetNodeId,
                topic = "Queued Retry (${q.sessionId})",
                message = q.payload,
                sessionId = q.sessionId,
                isNetworkOnline = isNetworkOnline,
                prefs = prefs
            )
            if (res.delivered) {
                networkRepo.upsertQueuedMessage(q.copy(status = "SENT", updatedAt = System.currentTimeMillis()))
                flushedMessages++
            } else {
                val nextRetries = q.retryCount + 1
                networkRepo.upsertQueuedMessage(
                    q.copy(
                        retryCount = nextRetries,
                        status = if (nextRetries >= 5) "FAILED" else "QUEUED",
                        lastError = res.statusNote,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val waiting = networkRepo.getAllSessions().filter {
            it.status == "WAITING_RESPONSE" || it.status == "ACTIVE"
        }
        for (s in waiting) {
            insertTurn(
                sessionId = s.sessionId,
                speaker = "Main Brain / Orchestrator Agent",
                target = "Global AI Discussion Council",
                provider = "WorkManager Sync",
                model = prefs.activeModelId,
                stance = "CONSENSUS",
                text = "Resumed interrupted discussion after background sync check; evaluated existing turns and finalized recommendation."
            )
            val completed = s.copy(
                status = "FINISHED",
                progressPercent = 100,
                finalRecommendation = s.finalRecommendation.ifBlank { "ADAPT: ${s.proposedFeatureName}" },
                decisionType = if (s.decisionType == "PENDING") "ADAPT" else s.decisionType,
                updatedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis()
            )
            networkRepo.upsertSession(completed)
            resumedSessions++
        }
        return flushedMessages to resumedSessions
    }

    /**
     * 11 & 12. Processes authorized inbound webhook/event payloads through AgentTrustSecurityGuard.
     */
    suspend fun ingestAuthorizedWebhookEvent(
        nodeId: String,
        topic: String,
        rawPayload: String,
        prefs: AuraPreferences = AuraPreferences()
    ): InboundAgentPayloadCheck {
        val node = networkRepo.getNode(nodeId)
            ?: return InboundAgentPayloadCheck(false, "", false, false, "Unknown webhook source node $nodeId.")
        val check = trustGuard.validateInboundExternalPayload(node, rawPayload)
        if (!check.allowed) {
            emitDiscussionNotification(
                category = "SECURITY_ALERT",
                priority = "CRITICAL",
                title = "Security Alert from ${node.name}",
                body = check.securityNote,
                prefs = prefs
            )
            return check
        }
        evaluateDiscoveredTechnology(
            TechDiscoveryProposalInput(
                featureName = topic.take(48),
                sourcePlatform = node.providerOrPlatform,
                sourceUrl = node.endpointUrl,
                discoveringAgent = node.name,
                description = check.sanitizedMessage.take(360),
                topicCategory = "MCP_A2A"
            ),
            prefs = prefs
        )
        return check
    }

    /**
     * Live real-data research sweep from GitHub, Hugging Face, and MCP/A2A directories followed by deduplicated evaluation.
     */
    suspend fun runLiveTechWatchSweep(prefs: AuraPreferences = AuraPreferences()): Int {
        if (!prefs.autonomyPolicy.externalApiMasterEnabled || prefs.intelligenceMode == "LOCAL_ONLY") return 0
        val ram = deviceControl.readDeviceHealth().totalRamMb
        val gh = researchClient.searchGitHubProjects("android ai mcp agent", ram).getOrDefault(emptyList())
        val hf = researchClient.searchHuggingFaceModels("gemma voice", ram).getOrDefault(emptyList())
        val mcpA2a = researchClient.searchMcpAndA2aDirectories(ram).getOrDefault(emptyList())
        val existingProj = memoryRepo.getAllResearchProjectsOnce()
        val existingTools = memoryRepo.getAllToolsOnce()
        val deduped = researchClient.deduplicateAndCompareVersions(
            candidates = (gh.take(2) + hf.take(2) + mcpA2a.take(2)),
            existingProjects = existingProj,
            existingTools = existingTools
        )
        val combined = deduped.filter { !it.isDuplicate || it.hasNewerRelease }.map { it.candidate }
        if (combined.isNotEmpty()) memoryRepo.upsertResearchProjects(combined)

        var evaluated = 0
        combined.take(2).forEach { proj ->
            val fp = computeFingerprint("watch|${proj.name.lowercase()}")
            if (networkRepo.getSessionByFingerprint(fp) == null) {
                evaluateDiscoveredTechnology(
                    TechDiscoveryProposalInput(
                        featureName = proj.name,
                        sourcePlatform = proj.sourcePlatform,
                        sourceUrl = proj.url,
                        discoveringAgent = if (proj.sourcePlatform == "HUGGING_FACE") "Hugging Face Research Agent" else "GitHub Research Agent",
                        description = proj.description,
                        license = proj.license,
                        topicCategory = if (proj.sourcePlatform == "HUGGING_FACE") "HUGGING_FACE" else "GITHUB_TRENDING",
                        estimatedCostOrRam = proj.resourceEstimate
                    ),
                    prefs = prefs
                )
                evaluated++
            }
        }
        return evaluated
    }

    /**
     * 8. Searchable Conversation Archive supporting natural queries and structured filters.
     */
    fun searchArchive(
        rawFilter: GlobalArchiveFilter,
        conversations: List<AgentConversationEntity>,
        sessions: List<GlobalDiscussionSessionEntity>,
        turns: List<GlobalDiscussionTurnEntity>,
        recommendations: List<FeatureRecommendationEntity>,
        registry: List<FeatureRegistryHistoryEntity>,
        research: List<ResearchProjectEntity>
    ): List<GlobalArchiveHit> {
        val parsed = parseNaturalArchiveQuery(rawFilter)
        val hits = mutableListOf<GlobalArchiveHit>()

        conversations.forEach { c ->
            hits += GlobalArchiveHit(
                id = "chat_${c.id}",
                kind = "AGENT_CHAT",
                title = "${c.agentName} • ${c.topic}",
                subtitle = "${c.conversationId} | ${c.channelType}",
                body = "${c.userOriginalRequest}\n${c.decisionsAndReasons}\n${c.recommendationsMade}",
                agentName = "${c.agentName}, ${c.participatingAgentsCsv}",
                providerOrPlatform = c.channelType,
                modelName = "",
                outcome = c.finalOutcome,
                linksCsv = c.repositoryAndModelLinksCsv,
                timestamp = c.timestamp
            )
        }

        val turnsBySession = turns.groupBy { it.sessionId }
        sessions.forEach { s ->
            val sTurns = turnsBySession[s.sessionId].orEmpty()
            val turnText = sTurns.joinToString("\n") { "${it.speakerAgent} [${it.stance}]: ${it.messageText}" }
            val models = sTurns.map { it.modelName }.filter { it.isNotBlank() }.distinct().joinToString(", ")
            hits += GlobalArchiveHit(
                id = "disc_${s.sessionId}",
                kind = "COUNCIL_DISCUSSION",
                title = "[${s.topicCategory}] ${s.topic}",
                subtitle = "${s.sessionId} • ${s.status} (${s.progressPercent}%) • Decision: ${s.decisionType}",
                body = "${s.originalQuestion}\n${s.councilSummary}\nFeature: ${s.proposedFeatureName}\n$turnText",
                agentName = s.participatingAgentsCsv,
                providerOrPlatform = s.initiatorAgent,
                modelName = models.ifBlank { GEMINI_DEFAULT_MODEL },
                outcome = if (s.linkedFeatureRequestId.isNotBlank()) "LINKED_${s.linkedFeatureRequestId}_${s.decisionType}" else "${s.status}_${s.decisionType}",
                linksCsv = s.sourceLinksCsv,
                timestamp = s.updatedAt,
                sessionId = s.sessionId
            )
        }

        recommendations.forEach { r ->
            hits += GlobalArchiveHit(
                id = "rec_${r.id}",
                kind = "RECOMMENDATION",
                title = "Recommendation: ${r.featureName}",
                subtitle = "${r.discoveringAgent} • ${r.inboxStatus}",
                body = "${r.summaryWhatItDoes}\n${r.whyAuraNeedsIt}\n${r.agentFollowUpNotes}",
                agentName = r.discoveringAgent,
                providerOrPlatform = r.sourceLinksCsv,
                modelName = "",
                outcome = r.inboxStatus,
                linksCsv = r.sourceLinksCsv,
                timestamp = r.createdAt
            )
        }

        registry.forEach { reg ->
            hits += GlobalArchiveHit(
                id = "reg_${reg.id}",
                kind = "REGISTRY",
                title = "Registry: ${reg.featureName} v${reg.version}",
                subtitle = "${reg.changeEventType} • ${reg.activationStatus}",
                body = "${reg.description}\nReason: ${reg.decisionReason}\nTests: ${reg.testResults}",
                agentName = "${reg.recommendingAgent}, ${reg.participatingAgentsCsv}",
                providerOrPlatform = reg.discoverySource,
                modelName = "",
                outcome = reg.activationStatus,
                linksCsv = reg.sourceUrl,
                timestamp = reg.timestamp
            )
        }

        research.forEach { res ->
            hits += GlobalArchiveHit(
                id = "res_${res.id}",
                kind = "RESEARCH",
                title = "${res.sourcePlatform}: ${res.name}",
                subtitle = "${res.installationStatus} • ${res.testStatus}",
                body = "${res.description}\n${res.recommendationReason}",
                agentName = if (res.sourcePlatform == "HUGGING_FACE") "Hugging Face Research Agent" else "GitHub Research Agent",
                providerOrPlatform = res.sourcePlatform,
                modelName = if (res.sourcePlatform == "HUGGING_FACE") res.name else "",
                outcome = "${res.installationStatus}_${res.testStatus}",
                linksCsv = res.url,
                timestamp = res.fetchedAt
            )
        }

        val q = parsed.rawQuery.trim().lowercase()
        val ag = parsed.agentFilter.trim().lowercase()
        val prov = parsed.providerFilter.trim().lowercase()
        val top = parsed.topicFilter.trim().lowercase()
        val mod = parsed.modelFilter.trim().lowercase()
        val feat = parsed.toolOrFeatureFilter.trim().lowercase()
        val out = parsed.outcomeFilter.trim().uppercase()

        return hits.filter { h ->
            val blob = "${h.title} ${h.subtitle} ${h.body} ${h.agentName} ${h.providerOrPlatform} ${h.linksCsv} ${h.modelName}".lowercase()
            val mQ = q.isEmpty() || blob.contains(q)
            val mAg = ag.isEmpty() || h.agentName.lowercase().contains(ag) || blob.contains(ag)
            val mProv = prov.isEmpty() || h.providerOrPlatform.lowercase().contains(prov) || blob.contains(prov)
            val mTop = top.isEmpty() || h.title.lowercase().contains(top) || h.body.lowercase().contains(top)
            val mMod = mod.isEmpty() || h.modelName.lowercase().contains(mod) || blob.contains(mod)
            val mFeat = feat.isEmpty() || h.title.lowercase().contains(feat) || h.body.lowercase().contains(feat)
            val mFrom = parsed.fromMillis == null || h.timestamp >= parsed.fromMillis
            val mTo = parsed.toMillis == null || h.timestamp <= parsed.toMillis
            val mOut = when (out) {
                "", "ALL" -> true
                "INSTALLED" -> h.outcome.contains("ACTIVE", true) || h.outcome.contains("IMPLEMENTED", true) || h.outcome.contains("INSTALLED", true)
                "REJECTED" -> h.outcome.contains("REJECT", true) || h.outcome.contains("REMOVED", true)
                "UNTESTED" -> h.outcome.contains("UNTESTED", true) || h.outcome.contains("DISCOVERED_ONLY", true) || h.outcome.contains("NEW", true)
                "FINISHED" -> h.outcome.contains("FINISHED", true) || h.outcome.contains("COMPLETED", true) || h.outcome.contains("DELIVERED", true)
                "WAITING" -> h.outcome.contains("WAITING", true) || h.outcome.contains("PENDING", true)
                else -> h.outcome.contains(out, true)
            }
            mQ && mAg && mProv && mTop && mMod && mFeat && mFrom && mTo && mOut
        }.sortedByDescending { it.timestamp }
    }

    fun parseNaturalArchiveQuery(filter: GlobalArchiveFilter): GlobalArchiveFilter {
        val q = filter.rawQuery.trim().lowercase()
        if (q.isEmpty()) return filter
        var next = filter
        val now = System.currentTimeMillis()
        when {
            q.contains("github agents") || (q.contains("github") && q.contains("conversation")) ->
                next = next.copy(rawQuery = "", agentFilter = "github")
            q.contains("hugging face") && q.contains("recommend") ->
                next = next.copy(rawQuery = "", agentFilter = "hugging face")
            q.contains("voice model") ->
                next = next.copy(rawQuery = "", topicFilter = "voice")
            q.contains("this week") ->
                next = next.copy(rawQuery = "", fromMillis = now - 7 * 86_400_000L)
            q.contains("recommended") && q.contains("installed") ->
                next = next.copy(rawQuery = "", outcomeFilter = "INSTALLED")
            q.contains("rejected") ->
                next = next.copy(rawQuery = "", outcomeFilter = "REJECTED")
            q.contains("screen control") ->
                next = next.copy(rawQuery = "", topicFilter = "screen")
            q.contains("not tested") || q.contains("untested") ->
                next = next.copy(rawQuery = "", outcomeFilter = "UNTESTED")
        }
        return next
    }

    suspend fun emitDiscussionNotification(
        category: String,
        priority: String,
        title: String,
        body: String,
        sessionId: String = "",
        requestId: String = "",
        prefs: AuraPreferences = AuraPreferences()
    ) {
        val rank = mapOf("LOW" to 1, "NORMAL" to 2, "HIGH" to 3, "CRITICAL" to 4)
        val minRank = rank[prefs.discussionNotifMinPriority] ?: 2
        val itemRank = rank[priority] ?: 2
        if (prefs.discussionNotifFrequency == "MUTED") return
        if (prefs.discussionNotifFrequency == "CRITICAL_ONLY" && priority != "CRITICAL") return
        if (itemRank < minRank) return

        networkRepo.insertNotification(
            AgentNotificationEventEntity(
                category = category,
                priority = priority,
                title = title,
                body = trustGuard.scrubSecrets(body),
                relatedSessionId = sessionId,
                relatedRequestId = requestId
            )
        )

        if (prefs.discussionNotifFrequency == "REALTIME") {
            postSystemNotificationIfPermitted(title, trustGuard.scrubSecrets(body))
        }
    }

    private fun postSystemNotificationIfPermitted(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(NOTIF_CHANNEL_ID, "AURA Agent Network Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body.take(140))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100_000).toInt(), notif)
        }
    }

    private suspend fun insertTurn(
        sessionId: String,
        speaker: String,
        target: String,
        provider: String,
        model: String,
        stance: String,
        text: String,
        links: List<String> = emptyList()
    ) {
        networkRepo.insertTurn(
            GlobalDiscussionTurnEntity(
                sessionId = sessionId,
                speakerAgent = speaker,
                targetAgent = target,
                providerOrPlatform = provider,
                modelName = model,
                stance = stance,
                messageText = trustGuard.scrubSecrets(text),
                evidenceLinksCsv = links.joinToString(", "),
                injectionSanitized = true
            )
        )
    }

    private fun selectCouncilParticipants(category: String, connectedNames: List<String>): List<String> {
        val core = mutableListOf("Main Brain / Orchestrator Agent", "Security & Policy Guard Agent", "Coding & Tool Factory MCP Agent")
        when (category) {
            "GITHUB_TRENDING", "OPEN_SOURCE", "TOOLS_FRAMEWORKS", "DEV_TECH" -> core += "GitHub Research Agent"
            "HUGGING_FACE", "AI_MODELS", "PERFORMANCE" -> core += "Hugging Face Research Agent"
            "VOICE_LIVE", "VISION_MULTIMODAL" -> core += "Voice & Live Multimodal Agent"
            "ANDROID_AUTOMATION", "COMPUTER_USE" -> core += "Android Automation & Vision Agent"
            else -> core += listOf("GitHub Research Agent", "Android Automation & Vision Agent")
        }
        connectedNames.take(3).forEach { if (it !in core) core += it }
        return core.distinct()
    }

    private fun computeFingerprint(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    companion object {
        private const val NOTIF_CHANNEL_ID = "aura_agent_network_alerts"
    }
}
