package com.example.aura.memory

import kotlinx.coroutines.flow.Flow

class AgentHistoryRepository(private val db: AuraDatabase) {
    val conversations: Flow<List<AgentConversationEntity>> = db.agentConversationDao().observeAll()
    val customAgents: Flow<List<CustomAgentProfileEntity>> = db.customAgentProfileDao().observeAll()
    val liveActivities: Flow<List<AgentActivityEventEntity>> = db.agentActivityDao().observeAll()
    val registryHistory: Flow<List<FeatureRegistryHistoryEntity>> = db.featureRegistryHistoryDao().observeAll()
    val featureRequests: Flow<List<FeatureRequestEntity>> = db.featureRequestDao().observeAll()
    val recommendations: Flow<List<FeatureRecommendationEntity>> = db.featureRecommendationDao().observeAll()
    val changeProposals: Flow<List<PendingChangeProposalEntity>> = db.pendingChangeProposalDao().observeAll()

    suspend fun insertConversation(entry: AgentConversationEntity): Long =
        db.agentConversationDao().insert(entry)

    suspend fun getRecentConversations(limit: Int = 500): List<AgentConversationEntity> =
        db.agentConversationDao().getRecent(limit)

    suspend fun getConversationsForAgent(agentName: String): List<AgentConversationEntity> =
        db.agentConversationDao().getForAgent(agentName)

    suspend fun deleteConversation(id: Long) = db.agentConversationDao().deleteById(id)

    suspend fun upsertCustomAgent(profile: CustomAgentProfileEntity) =
        db.customAgentProfileDao().upsert(profile)

    suspend fun getCustomAgents(): List<CustomAgentProfileEntity> =
        db.customAgentProfileDao().getAll()

    suspend fun deleteCustomAgent(codeName: String) =
        db.customAgentProfileDao().delete(codeName)

    suspend fun insertActivity(event: AgentActivityEventEntity): Long =
        db.agentActivityDao().insert(event)

    suspend fun updateActivityStatus(
        id: Long,
        status: String,
        summary: String,
        errorReason: String = "",
        completedAt: Long? = if (status != "LIVE") System.currentTimeMillis() else null
    ) = db.agentActivityDao().updateStatus(id, status, summary, errorReason, completedAt)

    suspend fun getRecentActivities(limit: Int = 120): List<AgentActivityEventEntity> =
        db.agentActivityDao().getRecent(limit)

    suspend fun recordRegistryEvent(entry: FeatureRegistryHistoryEntity): Long =
        db.featureRegistryHistoryDao().insert(entry)

    suspend fun getAllRegistryHistory(): List<FeatureRegistryHistoryEntity> =
        db.featureRegistryHistoryDao().getAll()

    suspend fun getLatestFeatureState(featureId: String): FeatureRegistryHistoryEntity? =
        db.featureRegistryHistoryDao().getLatestForFeature(featureId)

    suspend fun upsertFeatureRequest(request: FeatureRequestEntity) =
        db.featureRequestDao().upsert(request)

    suspend fun getAllFeatureRequests(): List<FeatureRequestEntity> =
        db.featureRequestDao().getAll()

    suspend fun getFeatureRequest(requestId: String): FeatureRequestEntity? =
        db.featureRequestDao().getById(requestId)

    suspend fun upsertRecommendation(rec: FeatureRecommendationEntity) =
        db.featureRecommendationDao().upsert(rec)

    suspend fun getAllRecommendations(): List<FeatureRecommendationEntity> =
        db.featureRecommendationDao().getAll()

    suspend fun getRecommendation(id: String): FeatureRecommendationEntity? =
        db.featureRecommendationDao().getById(id)

    suspend fun upsertProposal(proposal: PendingChangeProposalEntity) =
        db.pendingChangeProposalDao().upsert(proposal)

    suspend fun getAllProposals(): List<PendingChangeProposalEntity> =
        db.pendingChangeProposalDao().getAll()

    suspend fun getProposal(id: String): PendingChangeProposalEntity? =
        db.pendingChangeProposalDao().getById(id)

    suspend fun enforceRetentionPolicy(retentionDays: Int, maxEntries: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays.coerceAtLeast(1) * 86_400_000L
        db.agentConversationDao().deleteOlderThan(cutoff)
        db.agentConversationDao().trimToMaxCount(maxEntries.coerceAtLeast(50))
        db.agentActivityDao().deleteOlderThan(cutoff)
        db.featureRequestDao().deleteOlderThan(cutoff)
    }

    suspend fun deleteHistoryScope(scopeName: String) {
        when (scopeName) {
            "CONVERSATIONS" -> db.agentConversationDao().clearAll()
            "ACTIVITIES" -> db.agentActivityDao().clearAll()
            "REQUESTS" -> db.featureRequestDao().clearAll()
            "RECOMMENDATIONS" -> db.featureRecommendationDao().clearAll()
            "PROPOSALS" -> db.pendingChangeProposalDao().clearAll()
            "REGISTRY" -> db.featureRegistryHistoryDao().clearAll()
            "ALL" -> {
                db.agentConversationDao().clearAll()
                db.agentActivityDao().clearAll()
                db.featureRequestDao().clearAll()
                db.featureRecommendationDao().clearAll()
                db.pendingChangeProposalDao().clearAll()
                db.featureRegistryHistoryDao().clearAll()
            }
        }
    }

    suspend fun seedInitialBaselineIfEmpty() {
        val now = System.currentTimeMillis()
        if (db.featureRecommendationDao().getAll().isEmpty()) {
            db.featureRecommendationDao().insertIfMissing(
                listOf(
                    FeatureRecommendationEntity(
                        id = "rec_offline_summarizer_mcp",
                        featureName = "Structured Daily Briefing Digest MCP",
                        summaryWhatItDoes = "Aggregates notifications, upcoming reminders, and device health into a single morning or evening briefing tool without network calls.",
                        whyAuraNeedsIt = "Reduces repetitive multi-step chat prompts when the user asks for daily summaries offline.",
                        limitationSolved = "Currently requires separate commands for Notifications, Productivity, and Device Health.",
                        discoveringAgent = "Tool Discovery Agent",
                        otherAgentOpinionsJson = """[{"agent":"Notification Agent","stance":"SUPPORT","note":"Read-only notification digest compatible."},{"agent":"Security Agent","stance":"APPROVED_SAFE","note":"Uses existing permissions; zero new scopes."}]""",
                        sourceLinksCsv = "internal://aura/mcp-sandbox/daily_briefing",
                        securityAssessment = "SAFE — Local sandboxed MCP workflow; no external network or DCL.",
                        compatibilityAssessment = "100% compatible with NotificationBridge & ProductivityAgentEngine (API 24+).",
                        developmentEffort = "LOW",
                        costResourceImpact = "0 USD / <2 MB RAM",
                        testRequirements = "Schema check + sandboxed dry-run test + Notification/Calendar read verify.",
                        inboxStatus = "NEW",
                        createdAt = now - 3600_000L
                    ),
                    FeatureRecommendationEntity(
                        id = "rec_huggingface_whisper_tiny",
                        featureName = "Whisper-Tiny Multilingual Voice Notes Evaluator",
                        summaryWhatItDoes = "Evaluates openai/whisper-tiny on Hugging Face for local Urdu/English transcription fallback quality benchmarking.",
                        whyAuraNeedsIt = "Improves speech recognition resilience when cloud STT quota or network is constrained.",
                        limitationSolved = "Helps bridge offline speech recognition edge cases across Roman Urdu & English.",
                        discoveringAgent = "Hugging Face Research Agent",
                        otherAgentOpinionsJson = """[{"agent":"Voice Agent","stance":"SUPPORT","note":"Useful offline benchmark target."},{"agent":"Performance Optimization Agent","stance":"CAUTION","note":"150MB footprint; verify free storage & RAM before local model runtime integration."}]""",
                        sourceLinksCsv = "https://huggingface.co/openai/whisper-tiny",
                        securityAssessment = "REVIEW_REQUIRED — External HF model weight reference; cannot auto-execute binary weights dynamically.",
                        compatibilityAssessment = "Reference/integration spec only; full native C++/NNAPI runtime requires APK build.",
                        developmentEffort = "HIGH",
                        costResourceImpact = "Free Apache-2.0 weights (~150 MB storage if integrated in native build)",
                        testRequirements = "Storage check, license check (MIT/Apache), WER verification.",
                        inboxStatus = "NEW",
                        createdAt = now - 7200_000L
                    ),
                    FeatureRecommendationEntity(
                        id = "rec_github_mcp_filesystem_read",
                        featureName = "Scoped Workspace Notes & CSV Digest Tool",
                        summaryWhatItDoes = "Inspired by open-source MCP filesystem tools: lets AURA parse exported CSV/Markdown study notes via SAF Uri into structured Knowledge Graph nodes.",
                        whyAuraNeedsIt = "Currently SAF reads documents one-by-one into chat rather than structured Knowledge Graph ingestion.",
                        limitationSolved = "Automates turning user-selected CSV/Markdown notes into linked Knowledge Graph records.",
                        discoveringAgent = "GitHub Research Agent",
                        otherAgentOpinionsJson = """[{"agent":"File and Document Agent","stance":"SUPPORT","note":"Works strictly through SAF OpenDocument without storage permissions."},{"agent":"Security Agent","stance":"CONDITIONAL","note":"Must use zero-permission SAF picker only."}]""",
                        sourceLinksCsv = "https://github.com/modelcontextprotocol/servers",
                        securityAssessment = "SAFE WITH SAF — Zero READ_EXTERNAL_STORAGE requested.",
                        compatibilityAssessment = "Compatible with FileDocumentManager & KnowledgeGraphEngine.",
                        developmentEffort = "MEDIUM",
                        costResourceImpact = "0 USD / negligible CPU",
                        testRequirements = "SAF URI sanitization test + injection scan + KG edge integrity test.",
                        inboxStatus = "NEW",
                        createdAt = now - 10800_000L
                    )
                )
            )
        }

        if (db.featureRegistryHistoryDao().getAll().isEmpty()) {
            val coreFeatures = listOf(
                Triple("core_gemini_rest", "Gemini Multi-Key & Auto-Fallback Engine", "ACTIVE"),
                Triple("core_a2a_network", "24-Agent A2A Collaboration Bus", "ACTIVE"),
                Triple("core_security_guard", "4-Level PolicyGuard & Keystore Vault", "ACTIVE"),
                Triple("core_accessibility_vision", "Accessibility Screen Vision & Gesture Control", "ACTIVE")
            )
            coreFeatures.forEachIndexed { idx, (fId, fName, status) ->
                db.featureRegistryHistoryDao().insert(
                    FeatureRegistryHistoryEntity(
                        featureId = fId,
                        featureName = fName,
                        description = "Core verified AURA baseline capability.",
                        discoverySource = "Built-in AURA Core",
                        sourceUrl = "",
                        recommendingAgent = "Main Brain / Orchestrator Agent",
                        participatingAgentsCsv = "Main Brain / Orchestrator Agent, Security Agent, Testing Agent",
                        discoveredAt = now - 86_400_000L,
                        version = "1.0.0",
                        previousVersion = null,
                        changeEventType = "ACTIVATED",
                        dependencies = "Android Jetpack, Coroutines, Room",
                        permissionsRequired = "Standard manifest declarations",
                        installationDate = now - 86_400_000L,
                        testResults = "Verified in baseline build",
                        activationStatus = status,
                        decisionReason = "Baseline system module verified on initialization.",
                        versionDiffSummary = "Initial 1.0.0 release",
                        rollbackSnapshotJson = """{"version":"1.0.0","status":"$status"}""",
                        timestamp = now - (4 - idx) * 1000L
                    )
                )
            }
        }

        if (db.agentConversationDao().getRecent(1).isEmpty()) {
            db.agentConversationDao().insert(
                AgentConversationEntity(
                    conversationId = "CONV-COUNCIL-INIT",
                    sessionId = "SESSION-BOOT",
                    agentId = "1",
                    agentName = "Main Brain / Orchestrator Agent",
                    participatingAgentsCsv = "Main Brain / Orchestrator Agent, Tool Discovery Agent, Security Agent, Testing Agent",
                    channelType = "AI_COUNCIL",
                    topic = "Startup Architecture & Discovery Readiness Check",
                    userOriginalRequest = "System startup verification",
                    qaExchangedJson = """[{"speaker":"Main Brain / Orchestrator Agent","target":"Security Agent","question":"Verify Keystore vault and no-secret logging enforcement before enabling History Center.","answer":"Verified PrivacyDataRouter + BackendServiceGateway log sanitization active.","timestamp":$now}]""",
                    recommendationsMade = "Added Daily Briefing Digest MCP & Scoped Workspace Notes candidates to Discovery Inbox.",
                    researchFindings = "Evaluated 3 candidate capabilities; marked external model/code references as DISCOVERED_ONLY until verified.",
                    repositoryAndModelLinksCsv = "https://github.com/modelcontextprotocol/servers, https://huggingface.co/openai/whisper-tiny",
                    decisionsAndReasons = "Keep external repos non-active until explicit pipeline verification & user approval.",
                    proposedFeaturesOrToolsCsv = "Structured Daily Briefing Digest MCP",
                    actionsPerformedCsv = "Verified baseline registry, Seeded Discovery Inbox",
                    testResults = "Baseline security & schema checks passed",
                    finalOutcome = "AI Council ready; Agent Communication History Center active.",
                    sensitivityRedacted = true,
                    timestamp = now - 1500L
                )
            )
        }
    }
}
