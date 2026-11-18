package com.example.aura.ui

import android.app.Application
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.agents.SpecialistAgent
import com.example.aura.ai.AuraOrchestrator
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.ExportedHistoryArtifact
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.HistoryDomain
import com.example.aura.history.HistorySearchAndExportManager
import com.example.aura.history.HistorySearchFilter
import com.example.aura.history.HistorySearchHit
import com.example.aura.history.IndividualAgentProfileSummary
import com.example.aura.memory.AgentActivityEventEntity
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuditLogEntity
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.CustomAgentProfileEntity
import com.example.aura.memory.EvolutionCycleEntity
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.FeatureRequestEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PendingChangeProposalEntity
import com.example.aura.memory.PreferencesRepository
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.memory.TaskRecordEntity
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Modular ViewModel delegate for AURA AI's Agent History, Audit Log,
 * Individual Agent Profiles, Feature Request Pipeline, Feature Discovery Inbox,
 * Change Approval & Rollback Center, Advanced Search/Export, and Retention Controls.
 */
class AuraHistoryController(
    private val app: Application,
    private val scope: CoroutineScope,
    db: AuraDatabase,
    private val memoryRepository: MemoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiRouter: MultiProviderRouter,
    private val agentNetwork: AgentNetworkManager,
    private val permissionGuard: PermissionGuard,
    private val deviceControl: DeviceControlEngine,
    private val toolFactory: ToolFactoryAndEvolutionEngine,
    private val researchClient: GitHubAndHfResearchClient,
    private val orchestrator: AuraOrchestrator,
    private val preferences: StateFlow<AuraPreferences>,
    private val researchProjects: StateFlow<List<ResearchProjectEntity>>,
    private val tasks: StateFlow<List<TaskRecordEntity>>,
    private val evolutionCycles: StateFlow<List<EvolutionCycleEntity>>,
    private val auditLogs: StateFlow<List<AuditLogEntity>>,
    private val onBanner: (String) -> Unit
) {
    val historyRepo = AgentHistoryRepository(db)
    val recorder = AgentHistoryRecorder(historyRepo, PrivacyDataRouter())
    val pipelineEngine = FeatureControlPipelineEngine(
        historyRepo = historyRepo,
        recorder = recorder,
        memoryRepo = memoryRepository,
        aiRouter = aiRouter,
        agentNetwork = agentNetwork,
        permissionGuard = permissionGuard,
        deviceControl = deviceControl,
        toolFactory = toolFactory,
        researchClient = researchClient
    )
    val searchAndExport = HistorySearchAndExportManager(app, PrivacyDataRouter())

    val conversations: StateFlow<List<AgentConversationEntity>> = historyRepo.conversations
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val customAgents: StateFlow<List<CustomAgentProfileEntity>> = historyRepo.customAgents
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val liveActivities: StateFlow<List<AgentActivityEventEntity>> = historyRepo.liveActivities
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val registryHistory: StateFlow<List<FeatureRegistryHistoryEntity>> = historyRepo.registryHistory
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val featureRequests: StateFlow<List<FeatureRequestEntity>> = historyRepo.featureRequests
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val recommendations: StateFlow<List<FeatureRecommendationEntity>> = historyRepo.recommendations
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val changeProposals: StateFlow<List<PendingChangeProposalEntity>> = historyRepo.changeProposals
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _selectedAgentCodeName = MutableStateFlow<String?>(null)
    val selectedAgentCodeName: StateFlow<String?> = _selectedAgentCodeName.asStateFlow()

    private val _searchFilter = MutableStateFlow(HistorySearchFilter())
    val searchFilter: StateFlow<HistorySearchFilter> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HistorySearchHit>>(emptyList())
    val searchResults: StateFlow<List<HistorySearchHit>> = _searchResults.asStateFlow()

    private val _lastExport = MutableStateFlow<ExportedHistoryArtifact?>(null)
    val lastExport: StateFlow<ExportedHistoryArtifact?> = _lastExport.asStateFlow()

    private val _isPipelineRunning = MutableStateFlow(false)
    val isPipelineRunning: StateFlow<Boolean> = _isPipelineRunning.asStateFlow()

    fun initialize() {
        orchestrator.historyRecorder = recorder
        orchestrator.featurePipeline = pipelineEngine
        orchestrator.onFeatureInboxLookupTopId = {
            recommendations.value.firstOrNull { it.inboxStatus == "NEW" || it.inboxStatus == "SAVED_FOR_LATER" }?.id
        }
        toolFactory.historyRecorder = recorder

        scope.launch {
            historyRepo.seedInitialBaselineIfEmpty()
            val savedCustom = historyRepo.getCustomAgents()
            if (savedCustom.isNotEmpty()) {
                agentNetwork.mergeCustomProfiles(savedCustom)
            }
            val p = preferences.value
            historyRepo.enforceRetentionPolicy(p.historyRetentionDays, p.maxHistoryEntries)
            runGlobalSearch()
        }
    }

    fun selectAgentForProfile(agent: SpecialistAgent?) {
        _selectedAgentCodeName.value = agent?.codeName
    }

    fun buildProfileForAgent(agent: SpecialistAgent): IndividualAgentProfileSummary {
        return recorder.buildAgentProfileAggregate(
            agent = agent,
            allTasks = tasks.value,
            allConversations = conversations.value,
            allResearch = researchProjects.value,
            allRecommendations = recommendations.value,
            allRegistry = registryHistory.value,
            allRequests = featureRequests.value,
            allActivities = liveActivities.value
        )
    }

    // --- 4 & 5. Feature Request Center Actions ---

    fun submitFeatureRequest(prompt: String, mode: String, linkedSourceId: String = "") {
        if (prompt.isBlank()) {
            onBanner("Describe the feature, tool, repo, or agent to request.")
            return
        }
        scope.launch {
            _isPipelineRunning.value = true
            val req = pipelineEngine.submitFeatureRequest(
                userPrompt = prompt.trim(),
                requestMode = mode,
                linkedSourceId = linkedSourceId,
                prefs = preferences.value
            )
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner("Request ${req.requestId}: ${req.status} (${req.stageName})")
        }
    }

    fun retryFailedFeatureRequest(requestId: String) {
        scope.launch {
            _isPipelineRunning.value = true
            val req = pipelineEngine.retryOrContinueFailedRequest(requestId, preferences.value)
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner(req?.let { "Retried ${it.requestId} -> ${it.status}" } ?: "Request not found.")
        }
    }

    // --- 6 & 7. Feature Discovery Inbox Actions ---

    fun addFeatureFromInbox(recId: String) {
        scope.launch {
            _isPipelineRunning.value = true
            val req = pipelineEngine.addFeatureFromRecommendation(recId, preferences.value)
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner(req?.let { "${it.requestId}: ${it.status} (${it.stageName})" } ?: "Recommendation not found.")
        }
    }

    fun askInboxAgentsMoreInfo(recId: String, question: String) {
        scope.launch {
            _isPipelineRunning.value = true
            val updated = pipelineEngine.askAgentsMoreInfo(recId, question, preferences.value)
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner(updated?.let { "Agent reply saved for '${it.featureName}'." } ?: "Item not found.")
        }
    }

    fun compareInboxAlternatives(recId: String) {
        scope.launch {
            _isPipelineRunning.value = true
            val updated = pipelineEngine.compareAlternatives(recId, preferences.value)
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner(updated?.let { "Alternatives compared for '${it.featureName}'." } ?: "Item not found.")
        }
    }

    fun developOwnVersionFromInbox(recId: String, customNotes: String = "") {
        scope.launch {
            _isPipelineRunning.value = true
            val req = pipelineEngine.developOwnVersionFromRecommendation(recId, customNotes, preferences.value)
            _isPipelineRunning.value = false
            runGlobalSearch()
            onBanner(req?.let { "Custom build ${it.requestId}: ${it.status}" } ?: "Item not found.")
        }
    }

    fun saveInboxForLater(recId: String) {
        scope.launch {
            pipelineEngine.saveRecommendationForLater(recId)
            onBanner("Saved recommendation for later.")
        }
    }

    fun rejectInboxRecommendation(recId: String, reason: String = "Rejected by user") {
        scope.launch {
            pipelineEngine.rejectRecommendation(recId, reason)
            runGlobalSearch()
            onBanner("Recommendation rejected.")
        }
    }

    // --- 8. Change Approval & Rollback Center ---

    fun approveChangeProposal(id: String) {
        scope.launch {
            val res = pipelineEngine.approveProposal(id, preferences.value)
            runGlobalSearch()
            onBanner(res?.let { "Approved '${it.title}' (${it.status})." } ?: "Proposal not found.")
        }
    }

    fun rejectChangeProposal(id: String, reason: String = "Rejected by user") {
        scope.launch {
            val res = pipelineEngine.rejectProposal(id, reason)
            runGlobalSearch()
            onBanner(res?.let { "Rejected '${it.title}'." } ?: "Proposal not found.")
        }
    }

    fun requestProposalModifications(id: String, notes: String) {
        scope.launch {
            val res = pipelineEngine.requestModifications(id, notes.ifBlank { "Refine implementation before activation." })
            runGlobalSearch()
            onBanner(res?.let { "Requested modifications on '${it.title}'." } ?: "Proposal not found.")
        }
    }

    fun pauseChangeProposal(id: String) {
        scope.launch {
            val res = pipelineEngine.pauseProposal(id)
            onBanner(res?.let { "Paused '${it.title}'." } ?: "Proposal not found.")
        }
    }

    fun cancelChangeProposal(id: String) {
        scope.launch {
            val res = pipelineEngine.cancelProposal(id)
            onBanner(res?.let { "Cancelled '${it.title}'." } ?: "Proposal not found.")
        }
    }

    fun rollbackChangeProposal(id: String) {
        scope.launch {
            val res = pipelineEngine.rollbackProposal(id)
            runGlobalSearch()
            onBanner(res?.let { "Rolled back '${it.title}' safely." } ?: "Proposal not found.")
        }
    }

    // --- 9 & 10. Advanced Search, Export & Retention ---

    fun updateSearchFilter(newFilter: HistorySearchFilter) {
        _searchFilter.value = newFilter
        runGlobalSearch()
    }

    fun toggleDomainFilter(domain: HistoryDomain) {
        val current = _searchFilter.value.domains
        val next = if (domain in current) current - domain else current + domain
        _searchFilter.value = _searchFilter.value.copy(domains = next.ifEmpty { HistoryDomain.entries.toSet() })
        runGlobalSearch()
    }

    fun runGlobalSearch() {
        _searchResults.value = searchAndExport.search(
            filter = _searchFilter.value,
            conversations = conversations.value,
            research = researchProjects.value,
            requests = featureRequests.value,
            recommendations = recommendations.value,
            registry = registryHistory.value,
            proposals = changeProposals.value,
            activities = liveActivities.value,
            evolutions = evolutionCycles.value,
            audits = auditLogs.value
        )
    }

    fun exportFilteredHistory(format: String) {
        runGlobalSearch()
        val hits = _searchResults.value
        val result = when (format.uppercase()) {
            "CSV" -> searchAndExport.exportToCsv(hits)
            "PDF" -> searchAndExport.exportToPdf(hits)
            else -> searchAndExport.exportToJson(hits)
        }
        result.fold(
            onSuccess = { artifact ->
                _lastExport.value = artifact
                onBanner("Exported ${artifact.itemCount} records as ${artifact.format} (${artifact.file.name}).")
            },
            onFailure = { err ->
                onBanner("Export failed: ${err.localizedMessage}")
            }
        )
    }

    fun updateRetentionPolicy(days: Int, maxEntries: Int, recordSensitive: Boolean) {
        scope.launch {
            preferencesRepository.setHistoryRetentionPolicy(days, maxEntries, recordSensitive)
            historyRepo.enforceRetentionPolicy(days, maxEntries)
            runGlobalSearch()
            onBanner("Updated retention policy: ${days}d max, $maxEntries entries.")
        }
    }

    fun deleteHistoryScope(scopeName: String) {
        scope.launch {
            historyRepo.deleteHistoryScope(scopeName)
            runGlobalSearch()
            onBanner("Deleted $scopeName history records.")
        }
    }

    fun deleteSingleConversation(id: Long) {
        scope.launch {
            historyRepo.deleteConversation(id)
            runGlobalSearch()
        }
    }
}
