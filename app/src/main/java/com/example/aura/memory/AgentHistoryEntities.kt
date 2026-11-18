package com.example.aura.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1 & 2. Complete Agent Communication History record.
 * Covers Brain <-> Agent, A2A, AI Intelligence Council, External Providers, R&D, and Tool Discovery.
 */
@Entity(tableName = "agent_conversations")
data class AgentConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val sessionId: String,
    val agentId: String,
    val agentName: String,
    val participatingAgentsCsv: String,
    val channelType: String, // BRAIN_TO_AGENT, A2A, AI_COUNCIL, EXTERNAL_PROVIDER, RD_AGENT, TOOL_DISCOVERY
    val topic: String,
    val userOriginalRequest: String,
    val qaExchangedJson: String, // JSONArray of {speaker, target, question, answer, timestamp}
    val recommendationsMade: String,
    val researchFindings: String,
    val repositoryAndModelLinksCsv: String,
    val decisionsAndReasons: String,
    val proposedFeaturesOrToolsCsv: String,
    val actionsPerformedCsv: String,
    val testResults: String,
    val finalOutcome: String,
    val sensitivityRedacted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 2 & 4. Persistent specialist agent profiles (for custom agents created by user/AURA
 * as well as model/provider overrides and workload metadata).
 */
@Entity(tableName = "custom_agent_profiles")
data class CustomAgentProfileEntity(
    @PrimaryKey val codeName: String,
    val numericId: Int,
    val name: String,
    val roleDescription: String,
    val capabilitiesCsv: String,
    val preferredProviderId: String = "FIREBASE_GEMINI",
    val preferredModelId: String = "gemini-3.8-flash",
    val createdFromRequestId: String = "",
    val isCustom: Boolean = true,
    val enabled: Boolean = true,
    val lastActivitySummary: String = "Ready",
    val lastActivityAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 11. Live Agent Activity Dashboard events (never fabricated; every row is written by actual runtime work).
 */
@Entity(tableName = "agent_activity_events")
data class AgentActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentName: String,
    val activityType: String, // TALKING, CONSULTING, RESEARCHING, DEVELOPING, TESTING, AWAITING_APPROVAL, ACTIVATED, FAILED
    val targetAgentOrModule: String,
    val summary: String,
    val status: String, // LIVE, COMPLETED, FAILED, PENDING_APPROVAL
    val errorReason: String = "",
    val relatedRequestId: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * 3. Feature and Tool Change Registry History.
 * Never marks discovered/recommended items as INSTALLED/ACTIVE unless installation + verification succeeded.
 */
@Entity(tableName = "feature_registry_history")
data class FeatureRegistryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val featureId: String,
    val featureName: String,
    val description: String,
    val discoverySource: String,
    val sourceUrl: String = "",
    val recommendingAgent: String,
    val participatingAgentsCsv: String,
    val discoveredAt: Long,
    val version: String,
    val previousVersion: String? = null,
    val changeEventType: String, // DISCOVERED, PROPOSED, DEVELOPED, INSTALLED, ACTIVATED, UPGRADED, DISABLED, ROLLED_BACK, REMOVED
    val dependencies: String,
    val permissionsRequired: String,
    val installationDate: Long? = null,
    val testResults: String,
    val activationStatus: String, // DISCOVERED_ONLY, PROPOSED, DEVELOPMENT_REQUIRED, NEEDS_APPROVAL, INSTALLED_DISABLED, ACTIVE, ROLLED_BACK, FAILED
    val decisionReason: String,
    val versionDiffSummary: String,
    val rollbackSnapshotJson: String = "{}",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 4 & 5. User-Controlled Feature Request Execution Pipeline (16-step tracked execution).
 */
@Entity(tableName = "feature_requests")
data class FeatureRequestEntity(
    @PrimaryKey val requestId: String, // REQ-xxxx
    val userPrompt: String,
    val requestMode: String, // NATURAL, FROM_RECOMMENDATION, GITHUB_REPO, HF_MODEL, NEW_TOOL, IMPROVE_FEATURE, COMBINE_TOOLS, CREATE_AGENT
    val linkedSourceId: String = "",
    val assignedAgentsCsv: String = "",
    val currentStage: Int = 1, // 1..16
    val stageName: String = "SAVED_REQUEST",
    val stageHistoryJson: String = "[]",
    val consultationSummary: String = "",
    val technicalPlan: String = "",
    val alternativesSummary: String = "",
    val compatibilityReport: String = "",
    val securityReport: String = "",
    val requiredApisAndPerms: String = "",
    val testReport: String = "",
    val status: String = "IN_PROGRESS", // IN_PROGRESS, AWAITING_APPROVAL, PAUSED, CANCELLED, FAILED, COMPLETED_ACTIVATED
    val failureReason: String = "",
    val resultFeatureId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 6 & 7. Feature Discovery Inbox recommendations from specialist agents.
 */
@Entity(tableName = "feature_recommendations")
data class FeatureRecommendationEntity(
    @PrimaryKey val id: String,
    val featureName: String,
    val summaryWhatItDoes: String,
    val whyAuraNeedsIt: String,
    val limitationSolved: String,
    val discoveringAgent: String,
    val otherAgentOpinionsJson: String, // JSONArray of {agent, stance, note}
    val sourceLinksCsv: String,
    val securityAssessment: String,
    val compatibilityAssessment: String,
    val developmentEffort: String, // LOW, MEDIUM, HIGH
    val costResourceImpact: String,
    val testRequirements: String,
    val inboxStatus: String = "NEW", // NEW, SAVED_FOR_LATER, IN_PIPELINE, IMPLEMENTED, REJECTED
    val agentFollowUpNotes: String = "",
    val linkedRequestId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 8. Change Approval and Rollback Center queue.
 */
@Entity(tableName = "pending_change_proposals")
data class PendingChangeProposalEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val featureId: String,
    val title: String,
    val changeType: String, // NEW_TOOL, FEATURE_IMPROVEMENT, TOOL_COMPOSITION, NEW_AGENT, TOOL_UPGRADE, PERMISSION_EXPANSION, EXTERNAL_REPO
    val summary: String,
    val targetVersion: String,
    val previousVersion: String? = null,
    val versionDiff: String,
    val testReport: String,
    val securityAssessment: String,
    val requiresExplicitApproval: Boolean = true,
    val approvalReason: String,
    val previousStateSnapshotJson: String = "{}",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, MODIFICATIONS_REQUESTED, PAUSED, CANCELLED, AUTO_ACTIVATED, ROLLED_BACK
    val modificationNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
