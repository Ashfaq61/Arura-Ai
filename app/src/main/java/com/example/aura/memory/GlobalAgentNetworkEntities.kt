package com.example.aura.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1. Persistent registry of authorized internal & external AI agents/platforms.
 * Connection status is always grounded in real runtime checks or network probes.
 */
@Entity(tableName = "connected_agent_nodes")
data class ConnectedAgentNodeEntity(
    @PrimaryKey val nodeId: String,
    val name: String,
    val category: String, // PLATFORM, PROVIDER, GITHUB, HUGGING_FACE, CODING, ANDROID_AUTO, VOICE_VISION, RESEARCH, MCP, A2A, CUSTOM
    val providerOrPlatform: String,
    val modelName: String = "",
    val endpointUrl: String = "",
    val connectionStatus: String = "DISCONNECTED", // CONNECTED, DISCONNECTED, CONNECTING, BUSY, RATE_LIMITED, AUTH_REQUIRED, ERROR
    val trustStatus: String = "UNTRUSTED", // VERIFIED_INTERNAL, AUTHENTICATED_EXTERNAL, UNTRUSTED, BLOCKED
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    val autoHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val webhookUrl: String = "",
    val allowedCapabilitiesCsv: String = "research,summarize",
    val lastHeartbeatAt: Long? = null,
    val lastLatencyMs: Long = 0L,
    val lastErrorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 2, 3, 5, 6, 7 & 10. Persistent Global AI Discussion Council & Tech Discovery session.
 */
@Entity(tableName = "global_discussion_sessions")
data class GlobalDiscussionSessionEntity(
    @PrimaryKey val sessionId: String,
    val topic: String,
    val topicCategory: String, // AI_MODELS, ASSISTANT_FEATURES, ANDROID_AUTOMATION, COMPUTER_USE, VOICE_LIVE, VISION_MULTIMODAL, GITHUB_TRENDING, HUGGING_FACE, TOOLS_FRAMEWORKS, MCP_A2A, OPEN_SOURCE, SECURITY, PERFORMANCE, DEV_TECH
    val topicFingerprint: String,
    val initiatorAgent: String,
    val participatingAgentsCsv: String,
    val originalQuestion: String,
    val councilSummary: String = "",
    val finalRecommendation: String = "",
    val decisionType: String = "PENDING", // PENDING, INTEGRATE, ADAPT, MONITOR, REJECT
    val decisionReasoning: String = "",
    val proposedFeatureName: String = "",
    val proposedFeatureDescription: String = "",
    val advantagesSummary: String = "",
    val sourceLinksCsv: String = "",
    val licenseAndSecurity: String = "",
    val androidCompatibility: String = "",
    val technicalRequirements: String = "",
    val risksSummary: String = "",
    val implementationPlan: String = "",
    val testResults: String = "",
    val linkedFeatureRequestId: String = "",
    val linkedRecommendationId: String = "",
    val relatedSessionIdsCsv: String = "",
    val status: String = "ACTIVE", // ACTIVE, WAITING_RESPONSE, PAUSED, FINISHED, FAILED
    val progressPercent: Int = 10,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * 3 & 4. Turn-by-turn messages inside a Global AI Discussion or Agent Chat session.
 */
@Entity(tableName = "global_discussion_turns")
data class GlobalDiscussionTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val speakerAgent: String,
    val targetAgent: String,
    val providerOrPlatform: String,
    val modelName: String = "",
    val stance: String = "RESPONSE", // QUESTION, DISCOVERY, RESPONSE, CHALLENGE, EVIDENCE, ALTERNATIVE, CONSENSUS, DECISION
    val messageText: String,
    val evidenceLinksCsv: String = "",
    val injectionSanitized: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 11. Outbound message queue for offline / rate-limited retries.
 */
@Entity(tableName = "queued_agent_messages")
data class QueuedAgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetNodeId: String,
    val targetAgentName: String,
    val sessionId: String,
    val payload: String,
    val retryCount: Int = 0,
    val status: String = "QUEUED", // QUEUED, SENT, FAILED
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 9. Discussion & Discovery Notifications (models, capabilities, security alerts, approvals, failures).
 */
@Entity(tableName = "agent_notification_events")
data class AgentNotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // MODEL_DISCOVERY, CAPABILITY_RECOMMENDATION, FEATURE_READY, SECURITY_ALERT, APPROVAL_REQUIRED, TEST_PASSED, INTEGRATION_FAILED, UPGRADE_READY
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, CRITICAL
    val title: String,
    val body: String,
    val relatedSessionId: String = "",
    val relatedRequestId: String = "",
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
