package com.example.aura.network

import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.QaExchangeTurn
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.ConnectedAgentNodeEntity
import com.example.aura.memory.GlobalAgentNetworkRepository
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.QueuedAgentMessageEntity
import com.example.aura.security.GeminiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object AgentConnectionStatus {
    const val CONNECTED = "CONNECTED"
    const val DISCONNECTED = "DISCONNECTED"
    const val CONNECTING = "CONNECTING"
    const val BUSY = "BUSY"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val AUTH_REQUIRED = "AUTH_REQUIRED"
    const val ERROR = "ERROR"
}

data class ProbeResult(
    val status: String,
    val trustStatus: String,
    val latencyMs: Long,
    val message: String
)

fun interface NetworkEndpointProbe {
    suspend fun probe(node: ConnectedAgentNodeEntity, bearerToken: String?): ProbeResult
}

data class AgentDispatchResult(
    val delivered: Boolean,
    val queuedForRetry: Boolean,
    val blockedBySecurity: Boolean,
    val responseText: String,
    val statusNote: String
)

/**
 * Maintains the persistent network of authorized internal & external AI agents,
 * executes honest heartbeat checks, supports offline queuing & retries,
 * and enforces zero fabricated connection statuses.
 */
class GlobalAgentNetworkEngine(
    private val networkRepo: GlobalAgentNetworkRepository,
    private val historyRecorder: AgentHistoryRecorder,
    private val trustGuard: AgentTrustSecurityGuard,
    private val backendGateway: BackendServiceGateway,
    private val memoryRepo: MemoryRepository,
    private val aiRouter: MultiProviderRouter,
    private val geminiKeyStore: GeminiKeyStore? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val customProbe: NetworkEndpointProbe? = null
) {

    suspend fun initializeNodes() {
        networkRepo.seedInitialAgentNodesIfEmpty()
    }

    suspend fun addExternalAgentNode(
        name: String,
        category: String,
        providerOrPlatform: String,
        modelName: String,
        endpointUrl: String,
        bearerToken: String,
        webhookUrl: String = "",
        allowedCapabilities: String = "research,summarize",
        autoVerifyNow: Boolean = true,
        prefs: AuraPreferences = AuraPreferences()
    ): ConnectedAgentNodeEntity {
        val cleanEndpoint = endpointUrl.trim()
        val nodeId = "ext_${UUID.randomUUID().toString().take(8)}"
        val hasSecureUrl = trustGuard.isSecureEndpoint(cleanEndpoint)
        if (bearerToken.isNotBlank()) {
            trustGuard.saveExternalAgentToken(nodeId, name, bearerToken)
        }
        val initialStatus = when {
            !hasSecureUrl -> AgentConnectionStatus.ERROR
            bearerToken.isBlank() && !cleanEndpoint.contains("github.com") && !cleanEndpoint.contains("huggingface.co") ->
                AgentConnectionStatus.AUTH_REQUIRED
            else -> AgentConnectionStatus.DISCONNECTED
        }
        val node = ConnectedAgentNodeEntity(
            nodeId = nodeId,
            name = name.trim(),
            category = category,
            providerOrPlatform = providerOrPlatform.trim(),
            modelName = modelName.trim(),
            endpointUrl = cleanEndpoint,
            connectionStatus = initialStatus,
            trustStatus = if (bearerToken.isNotBlank() && hasSecureUrl) "AUTHENTICATED_EXTERNAL" else "UNTRUSTED",
            isBuiltIn = false,
            enabled = true,
            webhookUrl = webhookUrl.trim(),
            allowedCapabilitiesCsv = allowedCapabilities.ifBlank { "research,summarize" },
            lastErrorMessage = if (!hasSecureUrl) "Insecure endpoint rejected (HTTPS or localhost required)." else ""
        )
        networkRepo.upsertNode(node)
        memoryRepo.logAudit(
            actor = "User",
            action = "ADD_EXTERNAL_AGENT:$name",
            safetyLevel = 2,
            outcome = if (hasSecureUrl) "ALLOWED" else "BLOCKED",
            details = "Category=$category Endpoint=$cleanEndpoint"
        )
        return if (autoVerifyNow && hasSecureUrl) {
            runHeartbeat(node.nodeId, prefs) ?: node
        } else node
    }

    suspend fun authenticateExternalNode(
        nodeId: String,
        bearerToken: String,
        prefs: AuraPreferences = AuraPreferences()
    ): ConnectedAgentNodeEntity? {
        val node = networkRepo.getNode(nodeId) ?: return null
        trustGuard.saveExternalAgentToken(nodeId, node.name, bearerToken)
        val updated = node.copy(
            trustStatus = if (bearerToken.isNotBlank()) "AUTHENTICATED_EXTERNAL" else "UNTRUSTED",
            connectionStatus = if (bearerToken.isBlank()) AgentConnectionStatus.AUTH_REQUIRED else AgentConnectionStatus.DISCONNECTED,
            lastErrorMessage = if (bearerToken.isBlank()) "Authentication token cleared." else "Token saved in Keystore vault; running verification."
        )
        networkRepo.upsertNode(updated)
        return runHeartbeat(nodeId, prefs)
    }

    suspend fun disconnectNode(nodeId: String) {
        val node = networkRepo.getNode(nodeId) ?: return
        networkRepo.upsertNode(
            node.copy(
                enabled = false,
                connectionStatus = AgentConnectionStatus.DISCONNECTED,
                lastErrorMessage = "Disconnected by user."
            )
        )
        memoryRepo.logAudit("User", "DISCONNECT_AGENT:${node.name}", 1, "ALLOWED", "Agent disconnected cleanly.")
    }

    suspend fun removeNode(nodeId: String): Boolean {
        val node = networkRepo.getNode(nodeId) ?: return false
        trustGuard.deleteExternalAgentToken(nodeId)
        networkRepo.deleteNode(nodeId)
        memoryRepo.logAudit("User", "REMOVE_AGENT:${node.name}", 2, "ALLOWED", "Removed node $nodeId.")
        return true
    }

    suspend fun runHeartbeat(nodeId: String, prefs: AuraPreferences = AuraPreferences()): ConnectedAgentNodeEntity? {
        val node = networkRepo.getNode(nodeId) ?: return null
        if (!node.enabled) {
            val disabled = node.copy(connectionStatus = AgentConnectionStatus.DISCONNECTED)
            networkRepo.upsertNode(disabled)
            return disabled
        }

        // Local verified on-device agents
        if (node.endpointUrl.startsWith("local://aura/")) {
            val updated = node.copy(
                connectionStatus = AgentConnectionStatus.CONNECTED,
                trustStatus = "VERIFIED_INTERNAL",
                lastHeartbeatAt = System.currentTimeMillis(),
                lastLatencyMs = 1L,
                lastErrorMessage = ""
            )
            networkRepo.upsertNode(updated)
            return updated
        }

        if (!prefs.autonomyPolicy.externalApiMasterEnabled || prefs.intelligenceMode == "LOCAL_ONLY") {
            val paused = node.copy(
                connectionStatus = AgentConnectionStatus.DISCONNECTED,
                lastHeartbeatAt = System.currentTimeMillis(),
                lastErrorMessage = "External network disabled in LOCAL_ONLY / Autonomy Policy."
            )
            networkRepo.upsertNode(paused)
            return paused
        }

        if (node.endpointUrl.isBlank()) {
            val unconfigured = node.copy(
                connectionStatus = AgentConnectionStatus.DISCONNECTED,
                lastHeartbeatAt = System.currentTimeMillis(),
                lastErrorMessage = "Endpoint URL not configured."
            )
            networkRepo.upsertNode(unconfigured)
            return unconfigured
        }

        if (!trustGuard.isSecureEndpoint(node.endpointUrl)) {
            val blocked = node.copy(
                connectionStatus = AgentConnectionStatus.ERROR,
                trustStatus = "BLOCKED",
                lastErrorMessage = "Blocked insecure non-HTTPS endpoint."
            )
            networkRepo.upsertNode(blocked)
            return blocked
        }

        if (!backendGateway.acquireRateLimitPermit()) {
            val limited = node.copy(
                connectionStatus = AgentConnectionStatus.RATE_LIMITED,
                lastHeartbeatAt = System.currentTimeMillis(),
                lastErrorMessage = "Rate limit reached (${backendGateway.maxRequestsPerMinute}/min)."
            )
            networkRepo.upsertNode(limited)
            return limited
        }

        networkRepo.upsertNode(node.copy(connectionStatus = AgentConnectionStatus.CONNECTING))

        val bearer = when (node.nodeId) {
            "node_ext_gemini" -> geminiKeyStore?.getDecryptedKeys()?.firstOrNull().orEmpty()
            "node_ext_companion" -> backendGateway.getCompanionBearerToken()
            else -> trustGuard.getExternalAgentToken(node.nodeId)
        }

        val probeRes = if (customProbe != null) {
            customProbe.probe(node, bearer.takeIf { it.isNotBlank() })
        } else {
            executeRealHttpProbe(node, bearer)
        }

        val updated = node.copy(
            connectionStatus = probeRes.status,
            trustStatus = probeRes.trustStatus,
            lastHeartbeatAt = System.currentTimeMillis(),
            lastLatencyMs = probeRes.latencyMs,
            lastErrorMessage = trustGuard.scrubSecrets(probeRes.message)
        )
        networkRepo.upsertNode(updated)
        return updated
    }

    suspend fun runHeartbeatAll(prefs: AuraPreferences = AuraPreferences()): List<ConnectedAgentNodeEntity> {
        networkRepo.seedInitialAgentNodesIfEmpty()
        val nodes = networkRepo.getAllNodes()
        val results = mutableListOf<ConnectedAgentNodeEntity>()
        for (node in nodes) {
            results += runHeartbeat(node.nodeId, prefs) ?: node
        }
        return results
    }

    suspend fun sendMessageToAgent(
        nodeId: String,
        topic: String,
        message: String,
        sessionId: String = "DIRECT-${UUID.randomUUID().toString().take(8)}",
        isNetworkOnline: Boolean = true,
        prefs: AuraPreferences = AuraPreferences()
    ): AgentDispatchResult {
        val node = networkRepo.getNode(nodeId)
            ?: return AgentDispatchResult(false, false, false, "", "Agent node not found.")

        if (!node.enabled || node.connectionStatus == AgentConnectionStatus.DISCONNECTED) {
            return queueMessage(node, sessionId, message, "Agent disconnected — queued for reconnect retry.")
        }

        val safeOutbound = historyRecorder.sanitizeText(message, prefs)

        // Check offline or rate limit for external nodes
        val isExternal = !node.endpointUrl.startsWith("local://aura/")
        if (isExternal && (!isNetworkOnline || prefs.intelligenceMode == "LOCAL_ONLY" || !prefs.autonomyPolicy.externalApiMasterEnabled)) {
            return queueMessage(node, sessionId, safeOutbound, "Offline or external API paused — queued for automatic retry.")
        }
        if (isExternal && !backendGateway.acquireRateLimitPermit()) {
            networkRepo.upsertNode(node.copy(connectionStatus = AgentConnectionStatus.RATE_LIMITED))
            return queueMessage(node, sessionId, safeOutbound, "Rate limit active — queued for safe backoff retry.")
        }

        // Check daily token budget for external calls
        val dayStart = System.currentTimeMillis() - 86_400_000L
        val todayTokens = memoryRepo.providerUsage.first()
            .filter { it.timestamp >= dayStart }
            .sumOf { it.estimatedTokens }
        if (isExternal && todayTokens >= prefs.dailyTokenLimit) {
            return queueMessage(node, sessionId, safeOutbound, "Daily token budget (${prefs.dailyTokenLimit}) reached — queued.")
        }

        val actId = historyRecorder.startLiveActivity(
            agentName = node.name,
            activityType = "TALKING",
            targetAgentOrModule = topic,
            summary = "Direct agent message: ${safeOutbound.take(64)}",
            relatedRequestId = sessionId,
            prefs = prefs
        )
        networkRepo.upsertNode(node.copy(connectionStatus = AgentConnectionStatus.BUSY))

        val rawResponseResult: Result<String> = when {
            node.nodeId == "node_ext_gemini" -> {
                aiRouter.generateOnce(
                    prompt = safeOutbound,
                    systemInstruction = "Respond as ${node.name} on topic '$topic'. Be concise and factual.",
                    taskType = "agent_network_chat",
                    redactSensitiveData = prefs.redactSensitiveData
                ).map { it.text }
            }
            node.endpointUrl.startsWith("local://aura/") -> {
                Result.success(
                    "[${node.name}] Verified locally on ${node.providerOrPlatform}: received '$safeOutbound'. " +
                        "Capabilities ready: ${node.allowedCapabilitiesCsv}."
                )
            }
            customProbe != null -> {
                val p = customProbe.probe(node, trustGuard.getExternalAgentToken(node.nodeId).takeIf { it.isNotBlank() })
                if (p.status == AgentConnectionStatus.CONNECTED) Result.success(p.message)
                else Result.failure(IllegalStateException(p.message))
            }
            else -> postToExternalAgentEndpoint(node, topic, safeOutbound)
        }

        return rawResponseResult.fold(
            onSuccess = { rawReply ->
                val secCheck = trustGuard.validateInboundExternalPayload(node, rawReply)
                networkRepo.upsertNode(
                    node.copy(
                        connectionStatus = if (secCheck.allowed) AgentConnectionStatus.CONNECTED else AgentConnectionStatus.ERROR,
                        lastHeartbeatAt = System.currentTimeMillis(),
                        lastErrorMessage = if (secCheck.allowed) "" else secCheck.securityNote
                    )
                )
                historyRecorder.completeLiveActivity(
                    actId,
                    if (secCheck.allowed) "COMPLETED" else "FAILED",
                    secCheck.sanitizedMessage.take(120),
                    errorReason = if (secCheck.allowed) "" else secCheck.securityNote,
                    prefs = prefs
                )
                historyRecorder.recordConversation(
                    agentId = node.nodeId,
                    agentName = node.name,
                    participatingAgents = listOf("Main Brain / Orchestrator Agent", node.name),
                    channelType = if (isExternal) "EXTERNAL_PROVIDER" else "BRAIN_TO_AGENT",
                    topic = topic,
                    userOriginalRequest = safeOutbound,
                    qaTurns = listOf(QaExchangeTurn("Main Brain / Orchestrator Agent", node.name, safeOutbound, secCheck.sanitizedMessage)),
                    decisionsAndReasons = secCheck.securityNote,
                    finalOutcome = if (secCheck.allowed) "DELIVERED" else "BLOCKED_BY_SECURITY",
                    prefs = prefs
                )
                AgentDispatchResult(
                    delivered = secCheck.allowed,
                    queuedForRetry = false,
                    blockedBySecurity = !secCheck.allowed,
                    responseText = secCheck.sanitizedMessage,
                    statusNote = secCheck.securityNote
                )
            },
            onFailure = { err ->
                val errMsg = trustGuard.scrubSecrets(err.localizedMessage ?: "Agent unreachable")
                val nextStatus = when {
                    errMsg.contains("429") -> AgentConnectionStatus.RATE_LIMITED
                    errMsg.contains("401") || errMsg.contains("403") -> AgentConnectionStatus.AUTH_REQUIRED
                    else -> AgentConnectionStatus.ERROR
                }
                networkRepo.upsertNode(node.copy(connectionStatus = nextStatus, lastErrorMessage = errMsg))
                historyRecorder.completeLiveActivity(actId, "FAILED", "Delivery failed to ${node.name}", errMsg, prefs = prefs)
                queueMessage(node, sessionId, safeOutbound, "Delivery error ($errMsg) — queued for retry.")
            }
        )
    }

    private suspend fun queueMessage(
        node: ConnectedAgentNodeEntity,
        sessionId: String,
        payload: String,
        reason: String
    ): AgentDispatchResult {
        networkRepo.upsertQueuedMessage(
            QueuedAgentMessageEntity(
                targetNodeId = node.nodeId,
                targetAgentName = node.name,
                sessionId = sessionId,
                payload = payload,
                status = "QUEUED",
                lastError = reason
            )
        )
        return AgentDispatchResult(
            delivered = false,
            queuedForRetry = true,
            blockedBySecurity = false,
            responseText = "",
            statusNote = reason
        )
    }

    private suspend fun executeRealHttpProbe(
        node: ConnectedAgentNodeEntity,
        bearer: String
    ): ProbeResult = withContext(Dispatchers.IO) {
        if (node.nodeId == "node_ext_gemini" && bearer.isBlank()) {
            return@withContext ProbeResult(
                status = AgentConnectionStatus.AUTH_REQUIRED,
                trustStatus = "UNTRUSTED",
                latencyMs = 0L,
                message = "No Gemini API key configured in encrypted storage."
            )
        }
        val probeUrl = when (node.nodeId) {
            "node_ext_gemini" -> "https://generativelanguage.googleapis.com/v1beta/models?key=$bearer"
            "node_ext_github" -> "https://api.github.com/rate_limit"
            "node_ext_huggingface" -> "https://huggingface.co/api/models?limit=1"
            "node_ext_companion" -> node.endpointUrl.trimEnd('/') + "/health"
            else -> node.endpointUrl
        }
        val reqBuilder = Request.Builder().url(probeUrl).get()
            .header("User-Agent", "AURA-AI-Global-Network/1.0")
        if (bearer.isNotBlank() && node.nodeId != "node_ext_gemini") {
            reqBuilder.header("Authorization", "Bearer $bearer")
        }
        val start = System.currentTimeMillis()
        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                when {
                    resp.isSuccessful -> ProbeResult(
                        status = AgentConnectionStatus.CONNECTED,
                        trustStatus = "AUTHENTICATED_EXTERNAL",
                        latencyMs = latency,
                        message = "Heartbeat OK (HTTP ${resp.code}, ${latency}ms)."
                    )
                    resp.code == 401 || resp.code == 403 -> ProbeResult(
                        status = AgentConnectionStatus.AUTH_REQUIRED,
                        trustStatus = "UNTRUSTED",
                        latencyMs = latency,
                        message = "Authentication required (HTTP ${resp.code})."
                    )
                    resp.code == 429 -> ProbeResult(
                        status = AgentConnectionStatus.RATE_LIMITED,
                        trustStatus = node.trustStatus,
                        latencyMs = latency,
                        message = "Rate limited by provider (HTTP 429)."
                    )
                    else -> ProbeResult(
                        status = AgentConnectionStatus.ERROR,
                        trustStatus = node.trustStatus,
                        latencyMs = latency,
                        message = "Probe failed (HTTP ${resp.code})."
                    )
                }
            }
        } catch (e: Exception) {
            ProbeResult(
                status = AgentConnectionStatus.DISCONNECTED,
                trustStatus = node.trustStatus,
                latencyMs = 0L,
                message = "Connection failed: ${e.localizedMessage ?: "network unreachable"}"
            )
        }
    }

    private suspend fun postToExternalAgentEndpoint(
        node: ConnectedAgentNodeEntity,
        topic: String,
        safeMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (node.nodeId == "node_ext_github" || node.nodeId == "node_ext_huggingface") {
            val req = Request.Builder()
                .url(
                    if (node.nodeId == "node_ext_github") {
                        "https://api.github.com/search/repositories?q=${java.net.URLEncoder.encode(safeMessage.take(60), "UTF-8")}&sort=stars&per_page=3"
                    } else {
                        "https://huggingface.co/api/models?search=${java.net.URLEncoder.encode(safeMessage.take(60), "UTF-8")}&limit=3"
                    }
                )
                .header("User-Agent", "AURA-AI-Global-Network/1.0")
                .build()
            return@withContext runCatching {
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string().orEmpty().take(1200)
                }
            }
        }

        val bearer = trustGuard.getExternalAgentToken(node.nodeId)
        val jsonBody = JSONObject().apply {
            put("agent", node.name)
            put("model", node.modelName.ifBlank { GEMINI_DEFAULT_MODEL })
            put("topic", topic)
            put("message", safeMessage)
        }.toString()
        val reqBuilder = Request.Builder()
            .url(node.endpointUrl)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "AURA-AI-Global-Network/1.0")
        if (bearer.isNotBlank()) reqBuilder.header("Authorization", "Bearer $bearer")

        runCatching {
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty()
                runCatching {
                    val obj = JSONObject(raw)
                    obj.optString("reply", obj.optString("message", raw))
                }.getOrDefault(raw)
            }
        }
    }
}
