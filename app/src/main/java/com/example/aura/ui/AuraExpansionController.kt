package com.example.aura.ui

import android.app.Application
import android.content.Intent
import com.example.aura.agents.SpecialistAgent
import com.example.aura.analytics.AgentPerformanceEngine
import com.example.aura.analytics.PerformanceIntelligenceReport
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.automation.DeviceHealthSnapshot
import com.example.aura.automation.IntelligentNotificationItem
import com.example.aura.automation.NotificationBridge
import com.example.aura.automation.NotificationDigestSummary
import com.example.aura.automation.NotificationIntelligenceEngine
import com.example.aura.backend.BackendHealthSnapshot
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.backend.DatabaseMaintenanceStats
import com.example.aura.calls.PhoneCallAndMessagingManager
import com.example.aura.compat.AppCompatibilityEngine
import com.example.aura.diagnostics.AutoDiagnosticsEngine
import com.example.aura.diagnostics.DiagnosticsSummaryReport
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.files.FileDocumentManager
import com.example.aura.files.ShareAndClipboardManager
import com.example.aura.files.SharedContentPayload
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.knowledge.KnowledgeGraphEngine
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AppCompatibilityProfileEntity
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.AutomationRecipeEntity
import com.example.aura.memory.AutonomyPolicy
import com.example.aura.memory.CrashReportEntity
import com.example.aura.memory.KnowledgeEdgeEntity
import com.example.aura.memory.KnowledgeNodeEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.memory.ProductivityItemEntity
import com.example.aura.memory.ProviderUsageEntity
import com.example.aura.memory.RoutineEntity
import com.example.aura.memory.SkillProcedureEntity
import com.example.aura.memory.SyncDeviceEntity
import com.example.aura.memory.TaskRecordEntity
import com.example.aura.memory.VisualWorkflowEntity
import com.example.aura.offline.OfflineEmergencyEngine
import com.example.aura.offline.OfflineEmergencyState
import com.example.aura.productivity.DeviceCalendarEventSummary
import com.example.aura.productivity.ProductivityAgentEngine
import com.example.aura.recipes.AutomationRecipeEngine
import com.example.aura.routines.RoutineExecutionReport
import com.example.aura.routines.RoutineStep
import com.example.aura.routines.SmartRoutinesEngine
import com.example.aura.security.BackupExportOptions
import com.example.aura.security.EncryptedBackupManager
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PermissionHealthItem
import com.example.aura.security.PermissionHealthMonitor
import com.example.aura.security.PermissionHealthReport
import com.example.aura.sync.MultiDeviceSyncManager
import com.example.aura.sync.SyncConflictStrategy
import com.example.aura.testing.MultilingualAccessibilityQaEngine
import com.example.aura.testing.MultilingualQaReport
import com.example.aura.testing.ReleaseValidationEngine
import com.example.aura.testing.ReleaseValidationReport
import com.example.aura.voice.VoiceEngine
import com.example.aura.voice.WakeWordEngine
import com.example.aura.voice.WakeWordState
import com.example.aura.workflow.VisualWorkflowEngine
import com.example.aura.workflow.WorkflowRunReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Modular facade holding state & actions for AURA AI's 20 Advanced Capabilities,
 * keeping AuraViewModel clean and maintainable.
 */
class AuraExpansionController(
    private val app: Application,
    private val scope: CoroutineScope,
    db: AuraDatabase,
    private val memoryRepository: MemoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val permissionGuard: PermissionGuard,
    private val deviceControl: DeviceControlEngine,
    private val callMessaging: PhoneCallAndMessagingManager,
    private val fileManager: FileDocumentManager,
    private val voiceEngine: VoiceEngine,
    private val selfHealing: SelfHealingEngine,
    private val gitHubClient: GitHubSelfUpdateClient,
    private val agents: StateFlow<List<SpecialistAgent>>,
    private val skills: StateFlow<List<SkillProcedureEntity>>,
    private val tasks: StateFlow<List<TaskRecordEntity>>,
    private val providerUsage: StateFlow<List<ProviderUsageEntity>>,
    private val preferences: StateFlow<AuraPreferences>,
    private val gitHubConfig: StateFlow<GitHubConfig>,
    private val deviceHealth: StateFlow<DeviceHealthSnapshot>,
    private val isGeminiConfigured: () -> Boolean,
    private val onBanner: (String) -> Unit,
    private val onDispatchPrompt: (String) -> Unit
) {
    val advancedRepo = AdvancedRepository(db)

    val wakeWordEngine = WakeWordEngine(app)
    val notificationIntelligence = NotificationIntelligenceEngine(app, callMessaging)
    val shareAndClipboard = ShareAndClipboardManager(app, fileManager)
    val offlineEmergency = OfflineEmergencyEngine(app, deviceControl)
    val knowledgeGraph = KnowledgeGraphEngine(advancedRepo, memoryRepository)
    val visualWorkflowEngine = VisualWorkflowEngine(advancedRepo, deviceControl, permissionGuard)
    val backupManager = EncryptedBackupManager(memoryRepository, advancedRepo, AgentHistoryRepository(db))
    val performanceEngine = AgentPerformanceEngine()
    val compatEngine = AppCompatibilityEngine(app, advancedRepo, memoryRepository)
    val gateway = BackendServiceGateway(app)
    val multiDeviceSync = MultiDeviceSyncManager(advancedRepo, backupManager, gitHubClient, gateway)
    val productivityEngine = ProductivityAgentEngine(app, advancedRepo, knowledgeGraph)
    val permissionMonitor = PermissionHealthMonitor(app)
    val diagnosticsEngine = AutoDiagnosticsEngine(app, advancedRepo, memoryRepository, deviceControl, selfHealing)
    val recipeEngine = AutomationRecipeEngine(advancedRepo, compatEngine, visualWorkflowEngine, deviceControl)
    val multilingualQaEngine = MultilingualAccessibilityQaEngine(voiceEngine)
    val releaseValidation = ReleaseValidationEngine(
        context = app,
        memoryRepo = memoryRepository,
        advancedRepo = advancedRepo,
        offlineEngine = offlineEmergency,
        backupManager = backupManager,
        workflowEngine = visualWorkflowEngine,
        compatEngine = compatEngine,
        permMonitor = permissionMonitor,
        diagnosticsEngine = diagnosticsEngine,
        gateway = gateway,
        knowledgeGraph = knowledgeGraph,
        qaEngine = multilingualQaEngine,
        permissionGuard = permissionGuard
    )
    val smartRoutines = SmartRoutinesEngine(
        context = app,
        advancedRepo = advancedRepo,
        memoryRepo = memoryRepository,
        deviceControl = deviceControl,
        offlineEngine = offlineEmergency,
        permissionGuard = permissionGuard
    )

    // Reactive Flows
    val wakeWordState: StateFlow<WakeWordState> = wakeWordEngine.state
    val offlineEmergencyState: StateFlow<OfflineEmergencyState> = offlineEmergency.state
    val backendHealth: StateFlow<BackendHealthSnapshot> = gateway.healthState

    val notificationDigest: StateFlow<NotificationDigestSummary> = NotificationBridge.notifications
        .combine(preferences) { list, _ -> notificationIntelligence.analyzeAndPrioritize(list) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), notificationIntelligence.analyzeAndPrioritize(emptyList()))

    val routines: StateFlow<List<RoutineEntity>> = advancedRepo.routines
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val visualWorkflows: StateFlow<List<VisualWorkflowEntity>> = advancedRepo.visualWorkflows
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val knowledgeNodes: StateFlow<List<KnowledgeNodeEntity>> = advancedRepo.knowledgeNodes
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val knowledgeEdges: StateFlow<List<KnowledgeEdgeEntity>> = advancedRepo.knowledgeEdges
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val compatProfiles: StateFlow<List<AppCompatibilityProfileEntity>> = advancedRepo.appCompatProfiles
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recipes: StateFlow<List<AutomationRecipeEntity>> = advancedRepo.automationRecipes
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productivityItems: StateFlow<List<ProductivityItemEntity>> = advancedRepo.productivityItems
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val syncDevices: StateFlow<List<SyncDeviceEntity>> = advancedRepo.syncDevices
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val crashReports: StateFlow<List<CrashReportEntity>> = advancedRepo.crashReports
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sharedPayload = MutableStateFlow<SharedContentPayload?>(null)
    val sharedPayload: StateFlow<SharedContentPayload?> = _sharedPayload.asStateFlow()

    private val _lastRoutineReport = MutableStateFlow<RoutineExecutionReport?>(null)
    val lastRoutineReport: StateFlow<RoutineExecutionReport?> = _lastRoutineReport.asStateFlow()

    private val _lastWorkflowReport = MutableStateFlow<WorkflowRunReport?>(null)
    val lastWorkflowReport: StateFlow<WorkflowRunReport?> = _lastWorkflowReport.asStateFlow()

    private val _performanceReport = MutableStateFlow<PerformanceIntelligenceReport?>(null)
    val performanceReport: StateFlow<PerformanceIntelligenceReport?> = _performanceReport.asStateFlow()

    private val _permissionHealthReport = MutableStateFlow(permissionMonitor.inspectAll())
    val permissionHealthReport: StateFlow<PermissionHealthReport> = _permissionHealthReport.asStateFlow()

    private val _diagnosticsReport = MutableStateFlow<DiagnosticsSummaryReport?>(null)
    val diagnosticsReport: StateFlow<DiagnosticsSummaryReport?> = _diagnosticsReport.asStateFlow()

    private val _dbStats = MutableStateFlow<DatabaseMaintenanceStats?>(null)
    val dbStats: StateFlow<DatabaseMaintenanceStats?> = _dbStats.asStateFlow()

    private val _qaReport = MutableStateFlow<MultilingualQaReport?>(null)
    val qaReport: StateFlow<MultilingualQaReport?> = _qaReport.asStateFlow()

    private val _releaseReport = MutableStateFlow<ReleaseValidationReport?>(null)
    val releaseReport: StateFlow<ReleaseValidationReport?> = _releaseReport.asStateFlow()

    private val _deviceCalendarEvents = MutableStateFlow<List<DeviceCalendarEventSummary>>(emptyList())
    val deviceCalendarEvents: StateFlow<List<DeviceCalendarEventSummary>> = _deviceCalendarEvents.asStateFlow()

    private val _lastBackupJson = MutableStateFlow("")
    val lastBackupJson: StateFlow<String> = _lastBackupJson.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow("")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    fun initialize() {
        diagnosticsEngine.installUncaughtCrashTrap()
        smartRoutines.registerSystemEventObservers { triggerType ->
            handleSystemRoutineTrigger(triggerType)
        }
        scope.launch {
            advancedRepo.seedAdvancedDefaultsIfEmpty()
            val devId = preferencesRepository.ensureLocalDeviceId()
            val curPrefs = preferences.value
            if (advancedRepo.getAllSyncDevices().isEmpty()) {
                multiDeviceSync.registerOrAuthorizeDevice(devId, curPrefs.deviceDisplayName, true, "GITHUB_ENCRYPTED")
            }
            val ingested = diagnosticsEngine.ingestPendingCrashIfAny()
            if (ingested != null) {
                onBanner("Recovered crash log from previous session (${ingested.exceptionType}).")
            }
            refreshPermissionHealth()
            refreshPerformanceReport()
            refreshDiagnostics()
            compatEngine.scanInstalledAppsAndRepairSkills(skills.value)
            releaseValidation.runValidationSuite(curPrefs.autonomyPolicy, curPrefs.forceUrduRtlPreview).also {
                _releaseReport.value = it
            }
        }
    }

    // --- #1 Wake Word ---
    fun configureWakeWord(enabled: Boolean, phrase: String, sensitivity: Float, batterySaver: Boolean) {
        scope.launch {
            preferencesRepository.setWakeWordConfig(enabled, phrase, batterySaver)
            wakeWordEngine.updateConfig(enabled, phrase, sensitivity, batterySaver)
            if (enabled) {
                wakeWordEngine.startMonitoring { remainder ->
                    val cmd = remainder.ifBlank { "Hello AURA, I am listening." }
                    if (remainder.isNotBlank()) onDispatchPrompt(cmd) else onBanner("Wake Word '$phrase' activated!")
                }
            } else {
                wakeWordEngine.stopMonitoring()
            }
        }
    }

    fun triggerManualWakeWord(command: String = "") {
        wakeWordEngine.triggerManualWakeWord(command) { cmd ->
            if (cmd.isNotBlank()) onDispatchPrompt(cmd)
            else onBanner("Manual Wake Trigger ready — speak or type your command.")
        }
    }

    // --- #2 Smart Routines ---
    fun saveRoutine(name: String, triggerType: String, triggerConfig: String, stepType: String, stepParam: String, scheduleHours: Int) {
        scope.launch {
            val steps = smartRoutines.encodeSteps(listOf(RoutineStep(stepType, stepParam)))
            val id = advancedRepo.upsertRoutine(
                RoutineEntity(
                    name = name.trim(),
                    triggerType = triggerType,
                    triggerConfig = triggerConfig.trim(),
                    stepsJson = steps,
                    enabled = true,
                    scheduleHours = scheduleHours
                )
            )
            smartRoutines.scheduleRoutineWork(
                RoutineEntity(id = id, name = name, triggerType = triggerType, triggerConfig = triggerConfig, stepsJson = steps, enabled = true, scheduleHours = scheduleHours)
            )
            onBanner("Saved Smart Routine '$name'.")
        }
    }

    fun toggleRoutineEnabled(routine: RoutineEntity) {
        scope.launch {
            val updated = routine.copy(enabled = !routine.enabled)
            advancedRepo.upsertRoutine(updated)
            smartRoutines.scheduleRoutineWork(updated)
        }
    }

    fun runRoutineNow(routine: RoutineEntity) {
        scope.launch {
            val report = smartRoutines.executeRoutine(routine, preferences.value.autonomyPolicy, isManualTrigger = true) { spoken ->
                voiceEngine.speak(spoken)
            }
            _lastRoutineReport.value = report
            onBanner("${routine.name}: ${report.summary}")
        }
    }

    fun deleteRoutine(id: Long) {
        scope.launch { advancedRepo.deleteRoutine(id) }
    }

    private fun handleSystemRoutineTrigger(triggerType: String) {
        scope.launch {
            val policy = preferences.value.autonomyPolicy
            if (policy.scheduledTasksPaused) return@launch
            advancedRepo.getAllRoutines()
                .filter { it.enabled && it.triggerType == triggerType }
                .forEach { routine ->
                    smartRoutines.executeRoutine(routine, policy, isManualTrigger = false)
                }
        }
    }

    // --- #3 Notification Intelligence ---
    fun sendApprovedNotificationReply(item: IntelligentNotificationItem, replyText: String) {
        val policy = preferences.value.autonomyPolicy
        if (policy.confirmBeforeMessages) {
            // ConfirmDialog / direct user tap invokes this method directly after review
        }
        val (ok, msg) = notificationIntelligence.executeApprovedQuickReply(item, replyText)
        onBanner(msg)
        if (ok) {
            scope.launch { memoryRepository.logAudit("NotificationCenter", "QUICK_REPLY", 3, "SUCCESS", "Replied to ${item.source.appLabel}") }
        }
    }

    fun dismissNotification(key: String) {
        NotificationBridge.removeNotification(key)
    }

    // --- #5 Universal Share & Clipboard ---
    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        scope.launch {
            val parsed = shareAndClipboard.parseShareIntentAsync(intent) ?: return@launch
            _sharedPayload.value = parsed
            onBanner("Received shared ${parsed.mimeType} into AURA.")
        }
    }

    fun readClipboard() {
        shareAndClipboard.readClipboardOnUserAction().fold(
            onSuccess = {
                _sharedPayload.value = it
                onBanner("Loaded clipboard item into AURA.")
            },
            onFailure = { err -> onBanner(err.message ?: "Clipboard unavailable.") }
        )
    }

    fun clearSharedPayload() {
        _sharedPayload.value = null
    }

    fun sendSharedPayloadToAura(promptPrefix: String) {
        val payload = _sharedPayload.value ?: return
        val full = "$promptPrefix\n\n${payload.sanitizedText.take(2500)}"
        _sharedPayload.value = null
        onDispatchPrompt(full)
    }

    // --- #6 Knowledge Graph ---
    fun addKnowledgeNode(type: String, title: String, summary: String, tagsCsv: String) {
        scope.launch {
            val (_, links) = knowledgeGraph.addNodeAndAutoLink(type, title, summary, tagsCsv)
            onBanner("Added Knowledge Node '$title' ($links auto-links created).")
        }
    }

    fun linkKnowledgeNodes(fromId: Long, toId: Long, relation: String) {
        scope.launch {
            knowledgeGraph.linkNodes(fromId, toId, relation)
            onBanner("Connected knowledge nodes.")
        }
    }

    fun syncMemoriesToKnowledgeGraph() {
        scope.launch {
            val count = knowledgeGraph.syncRecentMemoriesIntoGraph()
            onBanner("Synced $count memories into Knowledge Graph.")
        }
    }

    fun deleteKnowledgeNode(nodeId: Long) {
        scope.launch { advancedRepo.deleteKnowledgeNode(nodeId) }
    }

    // --- #7 Offline Emergency Mode ---
    fun setOfflineEmergencyMode(enabled: Boolean) {
        scope.launch {
            preferencesRepository.setOfflineEmergencyMode(enabled)
            offlineEmergency.setEmergencyModeActive(enabled)
            onBanner(if (enabled) "Offline Emergency Mode ENABLED." else "Offline Emergency Mode disabled.")
        }
    }

    fun toggleFlashlight() {
        val (_, msg) = offlineEmergency.toggleFlashlight()
        onBanner(msg)
    }

    fun runOfflineUtilityCommand(input: String) {
        val res = offlineEmergency.runOfflineCommand(input)
        onBanner(res.take(90))
    }

    fun launchSosDialer(number: String) {
        offlineEmergency.setSosContactNumber(number)
        onBanner(offlineEmergency.openSosDialer(number).second)
    }

    // --- #8 Visual Workflow Builder ---
    fun saveAndValidateWorkflow(name: String, description: String, stepAction: String, stepParam: String, addBatteryCondition: Boolean) {
        scope.launch {
            val nodes = mutableListOf(
                com.example.aura.workflow.WorkflowNode("n1", "TRIGGER", "Manual / Policy Trigger", trueNext = if (addBatteryCondition) "n2" else "n3"),
            )
            if (addBatteryCondition) {
                nodes.add(com.example.aura.workflow.WorkflowNode("n2", "CONDITION_IF", "If Battery < 20%", action = "BATTERY_LT", param = "20", trueNext = "n3", falseNext = "n4"))
            }
            nodes.add(com.example.aura.workflow.WorkflowNode("n3", "ACTION", "Primary Step", action = stepAction, param = stepParam))
            nodes.add(com.example.aura.workflow.WorkflowNode("n4", "ACTION", "Log Completion", action = "LOG_NOTE", param = "Workflow finished cleanly"))

            val wf = VisualWorkflowEntity(
                name = name.trim(),
                description = description.trim(),
                variablesJson = """{"threshold":"20"}""",
                nodesJson = visualWorkflowEngine.encodeNodes(nodes)
            )
            val validation = visualWorkflowEngine.validateWorkflow(wf)
            advancedRepo.upsertWorkflow(wf.copy(lastValidationResult = validation.summary))
            onBanner("Workflow '${wf.name}': ${validation.summary}")
        }
    }

    fun runVisualWorkflow(workflow: VisualWorkflowEntity) {
        scope.launch {
            val report = visualWorkflowEngine.executeWorkflow(workflow, preferences.value.autonomyPolicy, userApprovedGates = true)
            _lastWorkflowReport.value = report
            onBanner("Workflow #${workflow.id}: ${if (report.succeeded) "Completed" else "Needs attention"}")
        }
    }

    fun deleteVisualWorkflow(id: Long) {
        scope.launch { advancedRepo.deleteWorkflow(id) }
    }

    // --- #9 Encrypted Backup & Restore ---
    fun createEncryptedBackup(passphrase: String, options: BackupExportOptions = BackupExportOptions()) {
        scope.launch {
            backupManager.createEncryptedBackup(passphrase, options).fold(
                onSuccess = { enc ->
                    _lastBackupJson.value = enc
                    onBanner("AES-256-GCM backup ready (${enc.length} bytes, zero credentials exported).")
                },
                onFailure = { err -> onBanner(err.message ?: "Backup failed.") }
            )
        }
    }

    fun restoreEncryptedBackup(passphrase: String, envelopeJson: String) {
        scope.launch {
            backupManager.restoreFromEncryptedBackup(passphrase, envelopeJson).fold(
                onSuccess = { s ->
                    onBanner("Restored ${s.memoriesRestored} memories, ${s.routinesRestored} routines, ${s.workflowsRestored} workflows, ${s.knowledgeNodesRestored} KG nodes.")
                },
                onFailure = { err -> onBanner("Restore failed: ${err.message}") }
            )
        }
    }

    // --- #10 Agent Performance Intelligence ---
    fun refreshPerformanceReport() {
        _performanceReport.value = performanceEngine.generateReport(
            agents = agents.value,
            tasks = tasks.value,
            usage = providerUsage.value,
            healingEvents = selfHealing.healingHistory.value,
            health = deviceHealth.value
        )
    }

    fun applyOptimizationRecommendation(actionCode: String) {
        scope.launch {
            when (actionCode) {
                "SWITCH_HYBRID" -> preferencesRepository.setIntelligenceMode("HYBRID")
                "ENABLE_FREE_ONLY" -> preferencesRepository.setFreeOnlyMode(true)
                "ENABLE_WAKE_SAVER" -> {
                    val p = preferences.value
                    preferencesRepository.setWakeWordConfig(p.wakeWordEnabled, p.wakePhrase, batterySaver = true)
                }
                "PRUNE_CACHE" -> selfHealing.pruneCachesOnLowMemory()
            }
            refreshPerformanceReport()
            onBanner("Applied optimization: $actionCode")
        }
    }

    // --- #11 App Compatibility Engine ---
    fun scanAppCompatibility() {
        scope.launch {
            val res = compatEngine.scanInstalledAppsAndRepairSkills(skills.value)
            onBanner(res.reportMessage)
        }
    }

    fun acknowledgeAppBaseline(pkg: String) {
        scope.launch {
            compatEngine.acknowledgeBaseline(pkg)
            onBanner("Updated baseline for $pkg.")
        }
    }

    // --- #12 Multi-Device Sync & #16 Backend Architecture ---
    fun saveCompanionBackendConfig(url: String, bearerToken: String, deviceName: String, conflictStrategy: String) {
        scope.launch {
            if (url.isNotBlank() && !gateway.isSecureEndpointUrl(url)) {
                onBanner("Blocked: Companion endpoint URL must use HTTPS.")
                return@launch
            }
            gateway.saveCompanionBearerToken(bearerToken)
            preferencesRepository.setCloudCompanionConfig(url, deviceName, conflictStrategy)
            checkBackendHealth()
            onBanner("Saved encrypted companion settings.")
        }
    }

    fun checkBackendHealth() {
        scope.launch {
            val p = preferences.value
            val g = gitHubConfig.value
            gateway.checkGatewayHealth(
                geminiConfigured = isGeminiConfigured(),
                githubConfigured = g.isConfigured,
                companionUrl = p.cloudCompanionUrl,
                externalApiAllowed = p.autonomyPolicy.externalApiMasterEnabled && !p.offlineEmergencyMode
            )
            _dbStats.value = gateway.collectDbStats(memoryRepository, advancedRepo)
        }
    }

    fun runGitHubEncryptedSync(passphrase: String) {
        scope.launch {
            val p = preferences.value
            if (!p.autonomyPolicy.externalApiMasterEnabled || p.offlineEmergencyMode) {
                onBanner("External APIs disabled by Autonomy/Offline policy.")
                return@launch
            }
            val strat = runCatching { SyncConflictStrategy.valueOf(p.syncConflictStrategy) }
                .getOrDefault(SyncConflictStrategy.LAST_WRITE_WINS)
            val devId = preferencesRepository.ensureLocalDeviceId()
            val res = multiDeviceSync.syncViaGitHubEncrypted(gitHubConfig.value, devId, p.deviceDisplayName, passphrase, strat)
            _lastSyncMessage.value = res.message
            onBanner(res.message)
        }
    }

    fun runCompanionSync(passphrase: String) {
        scope.launch {
            val p = preferences.value
            val devId = preferencesRepository.ensureLocalDeviceId()
            val res = multiDeviceSync.syncViaCompanionHttps(p.cloudCompanionUrl, devId, p.deviceDisplayName, passphrase)
            _lastSyncMessage.value = res.message
            onBanner(res.message)
        }
    }

    fun generateLocalSyncBundle(passphrase: String) {
        scope.launch {
            val p = preferences.value
            val devId = preferencesRepository.ensureLocalDeviceId()
            multiDeviceSync.createEncryptedSyncEnvelope(devId, p.deviceDisplayName, passphrase).fold(
                onSuccess = { (env, checksum) ->
                    _lastBackupJson.value = env
                    _lastSyncMessage.value = "Generated encrypted .aurasync bundle (sha256: ${checksum.take(12)})."
                    onBanner("Sync bundle ready.")
                },
                onFailure = { err -> onBanner(err.message ?: "Sync bundle error.") }
            )
        }
    }

    fun importSyncBundle(envelopeJson: String, passphrase: String) {
        scope.launch {
            val strat = runCatching { SyncConflictStrategy.valueOf(preferences.value.syncConflictStrategy) }
                .getOrDefault(SyncConflictStrategy.LAST_WRITE_WINS)
            val res = multiDeviceSync.importEncryptedSyncEnvelope(envelopeJson, passphrase, strat)
            _lastSyncMessage.value = res.message
            onBanner(res.message)
        }
    }

    fun toggleDeviceAuthorization(device: SyncDeviceEntity) {
        scope.launch { multiDeviceSync.toggleDeviceAuthorization(device) }
    }

    // --- #13 Personal Productivity & Calendar ---
    fun addProductivityItem(itemType: String, title: String, details: String, dueMinutesFromNow: Int?, priority: String) {
        scope.launch {
            val due = dueMinutesFromNow?.let { System.currentTimeMillis() + it * 60_000L }
            productivityEngine.createItem(itemType, title, details, due, priority)
            onBanner("Saved $itemType: $title")
        }
    }

    fun toggleProductivityCompleted(item: ProductivityItemEntity) {
        scope.launch { productivityEngine.toggleCompleted(item) }
    }

    fun deleteProductivityItem(item: ProductivityItemEntity) {
        scope.launch { productivityEngine.deleteItem(item) }
    }

    fun refreshDeviceCalendar() {
        productivityEngine.readUpcomingDeviceCalendarEvents().fold(
            onSuccess = { events ->
                _deviceCalendarEvents.value = events
                onBanner("Loaded ${events.size} upcoming calendar event(s).")
            },
            onFailure = { err -> onBanner(err.message ?: "Calendar read permission needed.") }
        )
    }

    fun createCalendarEvent(title: String, desc: String, hoursFromNow: Int = 2) {
        val start = System.currentTimeMillis() + hoursFromNow * 3_600_000L
        val (_, msg) = productivityEngine.insertDeviceCalendarEvent(title, desc, start)
        onBanner(msg)
        if (productivityEngine.hasReadCalendarPermission()) refreshDeviceCalendar()
    }

    // --- #14 Permission Health Monitor ---
    fun refreshPermissionHealth() {
        _permissionHealthReport.value = permissionMonitor.inspectAll()
    }

    fun openPermissionSettings(item: PermissionHealthItem) {
        permissionMonitor.openSettingsForItem(item)
    }

    // --- #15 Auto Diagnostics & Recovery ---
    fun refreshDiagnostics() {
        scope.launch {
            _diagnosticsReport.value = diagnosticsEngine.runFullDiagnostics()
        }
    }

    fun runSafeDiagnosticsRecovery() {
        scope.launch {
            val msg = diagnosticsEngine.executeSafeRecovery()
            refreshDiagnostics()
            onBanner(msg)
        }
    }

    // --- #17 Automation Recipe Library ---
    fun runAndTestRecipe(recipe: AutomationRecipeEntity) {
        scope.launch {
            val outcome = recipeEngine.testAndRunRecipe(recipe)
            onBanner("${recipe.name}: ${if (outcome.passed) "PASSED" else "FAILED"}")
        }
    }

    fun sendRecipeToWorkflowBuilder(recipe: AutomationRecipeEntity) {
        scope.launch {
            val wf = recipeEngine.loadRecipeIntoWorkflowBuilder(recipe)
            onBanner("Loaded '${wf.name}' into Visual Workflow Builder.")
        }
    }

    // --- #18 Accessibility & Multilingual QA ---
    fun toggleUrduRtlPreview(enabled: Boolean) {
        scope.launch {
            preferencesRepository.setForceUrduRtlPreview(enabled)
            runMultilingualQaAudit()
        }
    }

    fun runMultilingualQaAudit() {
        _qaReport.value = multilingualQaEngine.runQualityAudit(preferences.value.forceUrduRtlPreview)
    }

    // --- #19 User-Controlled Autonomy ---
    fun updateAutonomyPolicy(policy: AutonomyPolicy) {
        scope.launch {
            preferencesRepository.updateAutonomyPolicy(policy)
            onBanner("Updated Autonomy Policy limits.")
        }
    }

    // --- #20 Final Release Validation ---
    fun runReleaseValidation() {
        scope.launch {
            val cur = preferences.value
            val report = releaseValidation.runValidationSuite(cur.autonomyPolicy, cur.forceUrduRtlPreview)
            _releaseReport.value = report
            onBanner("Validation: ${report.activeCount} ACTIVE, ${report.partialCount} PARTIAL (${report.totalChecked} checked).")
        }
    }

    fun cleanup() {
        wakeWordEngine.stopMonitoring()
        smartRoutines.unregisterSystemEventObservers()
    }
}
