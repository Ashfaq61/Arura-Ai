package com.example.aura.memory

import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the Always-Connected Global AI Agent Network, Council Sessions,
 * Turn-by-Turn Discussions, Offline Message Queue, and Agent Discussion Notifications.
 */
class GlobalAgentNetworkRepository(private val db: AuraDatabase) {
    val agentNodes: Flow<List<ConnectedAgentNodeEntity>> = db.connectedAgentNodeDao().observeAll()
    val discussionSessions: Flow<List<GlobalDiscussionSessionEntity>> = db.globalDiscussionSessionDao().observeAll()
    val discussionTurns: Flow<List<GlobalDiscussionTurnEntity>> = db.globalDiscussionTurnDao().observeAll()
    val queuedMessages: Flow<List<QueuedAgentMessageEntity>> = db.queuedAgentMessageDao().observeAll()
    val notifications: Flow<List<AgentNotificationEventEntity>> = db.agentNotificationEventDao().observeAll()

    suspend fun getAllNodes(): List<ConnectedAgentNodeEntity> = db.connectedAgentNodeDao().getAll()
    suspend fun getNode(nodeId: String): ConnectedAgentNodeEntity? = db.connectedAgentNodeDao().getById(nodeId)
    suspend fun upsertNode(node: ConnectedAgentNodeEntity) = db.connectedAgentNodeDao().upsert(node)
    suspend fun deleteNode(nodeId: String) = db.connectedAgentNodeDao().delete(nodeId)

    suspend fun getAllSessions(): List<GlobalDiscussionSessionEntity> = db.globalDiscussionSessionDao().getAll()
    suspend fun getSession(sessionId: String): GlobalDiscussionSessionEntity? = db.globalDiscussionSessionDao().getById(sessionId)
    suspend fun getSessionByFingerprint(fingerprint: String): GlobalDiscussionSessionEntity? =
        db.globalDiscussionSessionDao().getByFingerprint(fingerprint)
    suspend fun upsertSession(session: GlobalDiscussionSessionEntity) = db.globalDiscussionSessionDao().upsert(session)
    suspend fun deleteSession(sessionId: String) {
        db.globalDiscussionTurnDao().deleteForSession(sessionId)
        db.globalDiscussionSessionDao().delete(sessionId)
    }

    suspend fun getTurnsForSession(sessionId: String): List<GlobalDiscussionTurnEntity> =
        db.globalDiscussionTurnDao().getForSession(sessionId)
    suspend fun getAllTurns(): List<GlobalDiscussionTurnEntity> = db.globalDiscussionTurnDao().getAll()
    suspend fun insertTurn(turn: GlobalDiscussionTurnEntity): Long = db.globalDiscussionTurnDao().insert(turn)

    suspend fun getPendingQueuedMessages(): List<QueuedAgentMessageEntity> = db.queuedAgentMessageDao().getPending()
    suspend fun upsertQueuedMessage(msg: QueuedAgentMessageEntity): Long = db.queuedAgentMessageDao().upsert(msg)
    suspend fun deleteQueuedMessage(id: Long) = db.queuedAgentMessageDao().delete(id)

    suspend fun getAllNotifications(): List<AgentNotificationEventEntity> = db.agentNotificationEventDao().getAll()
    suspend fun insertNotification(event: AgentNotificationEventEntity): Long = db.agentNotificationEventDao().insert(event)
    suspend fun markNotificationRead(id: Long) = db.agentNotificationEventDao().markRead(id)
    suspend fun markAllNotificationsRead() = db.agentNotificationEventDao().markAllRead()

    suspend fun clearDiscussionArchive() {
        db.globalDiscussionTurnDao().clearAll()
        db.globalDiscussionSessionDao().clearAll()
        db.queuedAgentMessageDao().clearAll()
        db.agentNotificationEventDao().clearAll()
    }

    /**
     * Seeds initial authorized agent nodes honestly:
     * - On-device specialist agents report CONNECTED (real local bus).
     * - External cloud/API agents start as DISCONNECTED or AUTH_REQUIRED until a real heartbeat succeeds.
     */
    suspend fun seedInitialAgentNodesIfEmpty() {
        if (db.connectedAgentNodeDao().getAll().isNotEmpty()) return
        val now = System.currentTimeMillis()
        db.connectedAgentNodeDao().insertIfMissing(
            listOf(
                ConnectedAgentNodeEntity(
                    nodeId = "node_local_orchestrator",
                    name = "Main Brain / Orchestrator Agent",
                    category = "A2A",
                    providerOrPlatform = "On-Device AURA Core",
                    modelName = "Deterministic + Routed LLM",
                    endpointUrl = "local://aura/orchestrator",
                    connectionStatus = "CONNECTED",
                    trustStatus = "VERIFIED_INTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "plan,delegate,council,verify",
                    lastHeartbeatAt = now,
                    lastLatencyMs = 1L
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_local_android_auto",
                    name = "Android Automation & Vision Agent",
                    category = "ANDROID_AUTO",
                    providerOrPlatform = "Android Accessibility & CameraX",
                    modelName = "On-Device UI Tree + Multimodal",
                    endpointUrl = "local://aura/accessibility-vision",
                    connectionStatus = "CONNECTED",
                    trustStatus = "VERIFIED_INTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "ui_tree,camerax,ocr,gestures",
                    lastHeartbeatAt = now,
                    lastLatencyMs = 2L
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_local_security",
                    name = "Security & Policy Guard Agent",
                    category = "RESEARCH",
                    providerOrPlatform = "On-Device Keystore & PolicyGuard",
                    modelName = "Deterministic Static & Injection Filter",
                    endpointUrl = "local://aura/security-guard",
                    connectionStatus = "CONNECTED",
                    trustStatus = "VERIFIED_INTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "audit,sanitize,injection_filter,approval_gate",
                    lastHeartbeatAt = now,
                    lastLatencyMs = 1L
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_local_coding_mcp",
                    name = "Coding & Tool Factory MCP Agent",
                    category = "CODING",
                    providerOrPlatform = "AURA MCP Sandbox",
                    modelName = "Kotlin / Declarative MCP Spec",
                    endpointUrl = "local://aura/mcp-factory",
                    connectionStatus = "CONNECTED",
                    trustStatus = "VERIFIED_INTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "mcp_synthesize,static_scan,schema_verify",
                    lastHeartbeatAt = now,
                    lastLatencyMs = 2L
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_ext_gemini",
                    name = "Gemini Cloud Intelligence Agent",
                    category = "PROVIDER",
                    providerOrPlatform = "Google Gemini API",
                    modelName = GEMINI_DEFAULT_MODEL,
                    endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models",
                    connectionStatus = "AUTH_REQUIRED",
                    trustStatus = "UNTRUSTED",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "reasoning,multimodal,voice,council_debate",
                    lastErrorMessage = "Run heartbeat after saving a Gemini API key to verify live connection."
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_ext_github",
                    name = "GitHub Research Agent",
                    category = "GITHUB",
                    providerOrPlatform = "GitHub REST API",
                    modelName = "Repositories & Releases Monitor",
                    endpointUrl = "https://api.github.com/search/repositories",
                    connectionStatus = "DISCONNECTED",
                    trustStatus = "AUTHENTICATED_EXTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "search_repos,check_license,read_releases",
                    lastErrorMessage = "Awaiting heartbeat probe."
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_ext_huggingface",
                    name = "Hugging Face Research Agent",
                    category = "HUGGING_FACE",
                    providerOrPlatform = "Hugging Face Hub API",
                    modelName = "Open-Source Models Monitor",
                    endpointUrl = "https://huggingface.co/api/models",
                    connectionStatus = "DISCONNECTED",
                    trustStatus = "AUTHENTICATED_EXTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "search_models,check_model_card,hardware_fit",
                    lastErrorMessage = "Awaiting heartbeat probe."
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_ext_voice_vision",
                    name = "Voice & Live Multimodal Agent",
                    category = "VOICE_VISION",
                    providerOrPlatform = "Gemini Live / Google TTS + CameraX",
                    modelName = "Kore / Multimodal Vision",
                    endpointUrl = "local://aura/voice-vision",
                    connectionStatus = "CONNECTED",
                    trustStatus = "VERIFIED_INTERNAL",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "tts,stt,camera_qa,live_voice",
                    lastHeartbeatAt = now,
                    lastLatencyMs = 2L
                ),
                ConnectedAgentNodeEntity(
                    nodeId = "node_ext_companion",
                    name = "Companion Webhook / 24x7 Bridge Agent",
                    category = "PLATFORM",
                    providerOrPlatform = "Optional HTTPS Companion Backend",
                    modelName = "Webhook & Event Sync",
                    endpointUrl = "",
                    connectionStatus = "DISCONNECTED",
                    trustStatus = "UNTRUSTED",
                    isBuiltIn = true,
                    allowedCapabilitiesCsv = "webhook_events,background_watch",
                    lastErrorMessage = "Configure HTTPS Companion URL in Settings to connect."
                )
            )
        )
    }
}
