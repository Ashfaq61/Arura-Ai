package com.example.aura.ui

import android.app.Application
import com.example.aura.ai.AuraOrchestrator
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AgentNotificationEventEntity
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.ConnectedAgentNodeEntity
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.GlobalAgentNetworkRepository
import com.example.aura.memory.GlobalDiscussionSessionEntity
import com.example.aura.memory.GlobalDiscussionTurnEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.memory.QueuedAgentMessageEntity
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.network.AgentNetworkSyncWorker
import com.example.aura.network.AgentTrustSecurityGuard
import com.example.aura.network.GlobalAgentNetworkEngine
import com.example.aura.network.GlobalArchiveFilter
import com.example.aura.network.GlobalArchiveHit
import com.example.aura.network.GlobalDiscussionCouncilEngine
import com.example.aura.network.TechDiscoveryProposalInput
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.GeminiKeyStore
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Modular ViewModel delegate for AURA AI's Always-Connected Global AI Agent Network,
 * AI Discussion Council, Live Discussion Monitor, Global AI Chat History,
 * Searchable Conversation Archive, and Discussion Notifications.
 */
class GlobalNetworkController(
    private val app: Application,
    private val scope: CoroutineScope,
    db: AuraDatabase,
    private val memoryRepository: MemoryRepository,
    private val preferencesRepository: PreferencesRepository,
    vault: AuraKeystoreVault,
    geminiKeyStore: GeminiKeyStore,
    aiRouter: MultiProviderRouter,
    deviceControl: DeviceControlEngine,
    researchClient: GitHubAndHfResearchClient,
    private val orchestrator: AuraOrchestrator,
    private val historyController: AuraHistoryController,
    private val preferences: StateFlow<AuraPreferences>,
    private val researchProjects: StateFlow<List<ResearchProjectEntity>>,
    private val onBanner: (String) -> Unit
) {
    val networkRepo = GlobalAgentNetworkRepository(db)
    val backendGateway = BackendServiceGateway(app)
    val trustGuard = AgentTrustSecurityGuard(
        vault = vault,
        injectionDefender = PromptInjectionDefender(),
        privacyRouter = PrivacyDataRouter(),
        memoryRepository = memoryRepository
    )
    val networkEngine = GlobalAgentNetworkEngine(
        networkRepo = networkRepo,
        historyRecorder = historyController.recorder,
        trustGuard = trustGuard,
        backendGateway = backendGateway,
        memoryRepo = memoryRepository,
        aiRouter = aiRouter,
        geminiKeyStore = geminiKeyStore
    )
    val councilEngine = GlobalDiscussionCouncilEngine(
        context = app,
        networkRepo = networkRepo,
        historyRepo = historyController.historyRepo,
        memoryRepo = memoryRepository,
        historyRecorder = historyController.recorder,
        networkEngine = networkEngine,
        pipelineEngine = historyController.pipelineEngine,
        trustGuard = trustGuard,
        researchClient = researchClient,
        deviceControl = deviceControl,
        aiRouter = aiRouter
    )

    val agentNodes: StateFlow<List<ConnectedAgentNodeEntity>> = networkRepo.agentNodes
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val discussionSessions: StateFlow<List<GlobalDiscussionSessionEntity>> = networkRepo.discussionSessions
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val discussionTurns: StateFlow<List<GlobalDiscussionTurnEntity>> = networkRepo.discussionTurns
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val queuedMessages: StateFlow<List<QueuedAgentMessageEntity>> = networkRepo.queuedMessages
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val notifications: StateFlow<List<AgentNotificationEventEntity>> = networkRepo.notifications
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _archiveFilter = MutableStateFlow(GlobalArchiveFilter())
    val archiveFilter: StateFlow<GlobalArchiveFilter> = _archiveFilter.asStateFlow()

    private val _archiveResults = MutableStateFlow<List<GlobalArchiveHit>>(emptyList())
    val archiveResults: StateFlow<List<GlobalArchiveHit>> = _archiveResults.asStateFlow()

    private val _isNetworkBusy = MutableStateFlow(false)
    val isNetworkBusy: StateFlow<Boolean> = _isNetworkBusy.asStateFlow()

    fun initialize() {
        orchestrator.onLatestDiscussionLookup = {
            val latest = discussionSessions.value.firstOrNull { it.proposedFeatureName.isNotBlank() }
            latest?.let { it.proposedFeatureName to it.linkedRecommendationId }
        }

        scope.launch {
            networkEngine.initializeNodes()
            val p = preferences.value
            if (p.backgroundNetworkSyncEnabled) {
                AgentNetworkSyncWorker.schedulePeriodicSync(app, p.heartbeatIntervalMinutes)
            }
            refreshArchiveSearch()
        }
    }

    fun runHeartbeatAll() {
        scope.launch {
            _isNetworkBusy.value = true
            val nodes = networkEngine.runHeartbeatAll(preferences.value)
            _isNetworkBusy.value = false
            val online = nodes.count { it.connectionStatus == "CONNECTED" }
            onBanner("Heartbeat complete: $online/${nodes.size} agents connected (honest status).")
        }
    }

    fun runHeartbeatSingle(nodeId: String) {
        scope.launch {
            _isNetworkBusy.value = true
            val res = networkEngine.runHeartbeat(nodeId, preferences.value)
            _isNetworkBusy.value = false
            onBanner(res?.let { "${it.name}: ${it.connectionStatus} (${it.lastErrorMessage.ifBlank { "${it.lastLatencyMs}ms" }})" } ?: "Node not found.")
        }
    }

    fun addExternalAgent(
        name: String,
        category: String,
        provider: String,
        model: String,
        endpointUrl: String,
        token: String,
        webhookUrl: String,
        capabilities: String
    ) {
        if (name.isBlank() || endpointUrl.isBlank()) {
            onBanner("Agent name and HTTPS endpoint URL are required.")
            return
        }
        scope.launch {
            _isNetworkBusy.value = true
            val node = networkEngine.addExternalAgentNode(
                name = name,
                category = category,
                providerOrPlatform = provider.ifBlank { category },
                modelName = model,
                endpointUrl = endpointUrl,
                bearerToken = token,
                webhookUrl = webhookUrl,
                allowedCapabilities = capabilities,
                autoVerifyNow = true,
                prefs = preferences.value
            )
            _isNetworkBusy.value = false
            onBanner("${node.name}: ${node.connectionStatus} (${node.lastErrorMessage.ifBlank { "Verified" }})")
        }
    }

    fun authenticateAgent(nodeId: String, token: String) {
        scope.launch {
            _isNetworkBusy.value = true
            val res = networkEngine.authenticateExternalNode(nodeId, token, preferences.value)
            _isNetworkBusy.value = false
            onBanner(res?.let { "${it.name}: ${it.connectionStatus}" } ?: "Node not found.")
        }
    }

    fun disconnectAgent(nodeId: String) {
        scope.launch {
            networkEngine.disconnectNode(nodeId)
            onBanner("Agent disconnected.")
        }
    }

    fun removeAgent(nodeId: String) {
        scope.launch {
            networkEngine.removeNode(nodeId)
            onBanner("Agent removed from network.")
        }
    }

    fun sendDirectAgentMessage(nodeId: String, topic: String, message: String) {
        if (message.isBlank()) return
        scope.launch {
            _isNetworkBusy.value = true
            val res = networkEngine.sendMessageToAgent(
                nodeId = nodeId,
                topic = topic.ifBlank { "Direct Agent Chat" },
                message = message,
                prefs = preferences.value
            )
            _isNetworkBusy.value = false
            refreshArchiveSearch()
            onBanner(
                when {
                    res.blockedBySecurity -> "Blocked by security guard: ${res.statusNote}"
                    res.queuedForRetry -> "Queued for retry: ${res.statusNote}"
                    res.delivered -> "Reply received & logged."
                    else -> res.statusNote
                }
            )
        }
    }

    fun startCouncilDiscussion(
        topic: String,
        category: String,
        question: String,
        sourceLinksRaw: String = "",
        proposedFeature: String = ""
    ) {
        if (topic.isBlank() || question.isBlank()) {
            onBanner("Enter a topic and question for the Global AI Discussion Council.")
            return
        }
        scope.launch {
            _isNetworkBusy.value = true
            val links = sourceLinksRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val session = councilEngine.startCouncilDiscussion(
                topic = topic,
                topicCategory = category,
                question = question,
                sourceLinks = links,
                proposedFeatureName = proposedFeature,
                prefs = preferences.value
            )
            _isNetworkBusy.value = false
            refreshArchiveSearch()
            onBanner("Council ${session.sessionId}: ${session.decisionType} (${session.proposedFeatureName})")
        }
    }

    fun evaluateTechDiscovery(
        featureName: String,
        platform: String,
        url: String,
        description: String,
        license: String,
        category: String
    ) {
        if (featureName.isBlank()) return
        scope.launch {
            _isNetworkBusy.value = true
            val session = councilEngine.evaluateDiscoveredTechnology(
                TechDiscoveryProposalInput(
                    featureName = featureName,
                    sourcePlatform = platform,
                    sourceUrl = url,
                    discoveringAgent = if (platform == "HUGGING_FACE") "Hugging Face Research Agent" else "GitHub Research Agent",
                    description = description.ifBlank { featureName },
                    license = license.ifBlank { "Verify License" },
                    topicCategory = category
                ),
                prefs = preferences.value
            )
            _isNetworkBusy.value = false
            refreshArchiveSearch()
            onBanner("Evaluated '${session.proposedFeatureName}' -> ${session.decisionType}")
        }
    }

    fun addFeatureFromDiscussion(sessionId: String) {
        scope.launch {
            _isNetworkBusy.value = true
            val req = councilEngine.promoteDiscussionToFeatureRequest(sessionId, preferences.value)
            _isNetworkBusy.value = false
            refreshArchiveSearch()
            onBanner(req?.let { "Feature ${it.requestId}: ${it.status} (${it.stageName})" } ?: "Discussion not found.")
        }
    }

    fun runLiveTechWatchAndResume() {
        scope.launch {
            _isNetworkBusy.value = true
            val (flushed, resumed) = councilEngine.resumeInterruptedAndQueuedWork(true, preferences.value)
            val discovered = councilEngine.runLiveTechWatchSweep(preferences.value)
            _isNetworkBusy.value = false
            refreshArchiveSearch()
            onBanner("Sync done: $flushed queued sent, $resumed resumed, $discovered new tech evaluated.")
        }
    }

    fun updateArchiveFilter(filter: GlobalArchiveFilter) {
        _archiveFilter.value = filter
        refreshArchiveSearch()
    }

    fun refreshArchiveSearch() {
        _archiveResults.value = councilEngine.searchArchive(
            rawFilter = _archiveFilter.value,
            conversations = historyController.conversations.value,
            sessions = discussionSessions.value,
            turns = discussionTurns.value,
            recommendations = historyController.recommendations.value,
            registry = historyController.registryHistory.value,
            research = researchProjects.value
        )
    }

    fun markNotificationRead(id: Long) {
        scope.launch { networkRepo.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        scope.launch { networkRepo.markAllNotificationsRead() }
    }

    fun updateNetworkAndNotificationSettings(
        heartbeatMinutes: Int,
        backgroundSync: Boolean,
        notifFreq: String,
        notifMinPriority: String,
        autoCouncil: Boolean
    ) {
        scope.launch {
            preferencesRepository.setGlobalNetworkSettings(
                heartbeatMinutes = heartbeatMinutes,
                backgroundSyncEnabled = backgroundSync,
                notifFrequency = notifFreq,
                notifMinPriority = notifMinPriority,
                autoCouncilOnDiscovery = autoCouncil
            )
            if (backgroundSync) {
                AgentNetworkSyncWorker.schedulePeriodicSync(app, heartbeatMinutes)
            } else {
                AgentNetworkSyncWorker.cancelPeriodicSync(app)
            }
            onBanner("Saved network heartbeat (${heartbeatMinutes}m) & notification settings.")
        }
    }
}
