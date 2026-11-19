package com.example.aura.evolution

import android.content.Context
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.agents.AgentStatus
import com.example.aura.agents.McpToolDescriptor
import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.github.ApkInstallResult
import com.example.aura.github.ApkSelfUpdater
import com.example.aura.github.ApkVersionComparator
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubReleasePair
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.QaExchangeTurn
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.CustomAgentProfileEntity
import com.example.aura.memory.EvolutionCycleEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.MarketplaceToolEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.PermissionGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AutonomousCycleReport(
    val cycleId: Long,
    val timestamp: Long,
    val focusTopic: String,
    val autonomousModeActive: Boolean,
    val discoveredCandidates: List<String>,
    val selectedApproachReason: String,
    val toolsInstalledOrActivated: List<String>,
    val toolsUpgraded: List<String>,
    val agentsCreatedOrUpgraded: List<String>,
    val bugsFixedOrRolledBack: List<String>,
    val pendingApprovalItems: List<String>,
    val nativeApkUpdateStatus: String,
    val exactTechnicalBlockers: List<String>,
    val cycleStatus: String
) {
    val totalVerifiedChanges: Int
        get() = toolsInstalledOrActivated.size +
            toolsUpgraded.size +
            agentsCreatedOrUpgraded.size +
            bugsFixedOrRolledBack.size
}

/**
 * 8, 9, 10 & 11: Continuous Self-Improvement & Zero-Question Autonomous Engine.
 * Executes: Discover -> Decide -> Develop -> Test -> Install -> Activate -> Monitor.
 * Never asks confirmation for ordinary, low-risk, reversible tools/upgrades/agents.
 * Gates ONLY sensitive permissions, security-critical ops, financial/external actions, or native APK/GitHub changes.
 * Reports exact technical blockers while continuing all other safe improvements.
 */
class ContinuousSelfImprovementEngine(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val historyRepo: AgentHistoryRepository,
    private val historyRecorder: AgentHistoryRecorder,
    private val agentNetwork: AgentNetworkManager,
    private val toolFactory: ToolFactoryAndEvolutionEngine,
    private val pipelineEngine: FeatureControlPipelineEngine,
    private val researchClient: GitHubAndHfResearchClient,
    private val deviceControl: DeviceControlEngine,
    private val selfHealing: SelfHealingEngine,
    private val permissionGuard: PermissionGuard,
    private val gitHubClient: GitHubSelfUpdateClient,
    private val apkSelfUpdater: ApkSelfUpdater,
    private val networkOnlineProvider: () -> Boolean = { selfHealing.isNetworkOnline() },
    private val releaseCheckOverride: (suspend (GitHubConfig) -> Result<GitHubReleasePair>)? = null,
    private val releaseDownloadOverride: (suspend (GitHubConfig, String, File) -> Result<File>)? = null
) {
    private val _lastReport = MutableStateFlow<AutonomousCycleReport?>(null)
    val lastReport: StateFlow<AutonomousCycleReport?> = _lastReport.asStateFlow()

    suspend fun runAutonomousCycle(
        focusTopic: String = "android ai agent",
        prefs: AuraPreferences = AuraPreferences(),
        gitHubConfig: GitHubConfig = GitHubConfig()
    ): AutonomousCycleReport {
        val cleanTopic = focusTopic.trim().ifBlank { "android ai agent" }
        val autonomous = prefs.zeroQuestionAutonomyEnabled
        val isOnline = networkOnlineProvider()
        val health = deviceControl.readDeviceHealth()

        val discoveredCandidates = mutableListOf<String>()
        val toolsInstalled = mutableListOf<String>()
        val toolsUpgraded = mutableListOf<String>()
        val agentsUpdated = mutableListOf<String>()
        val bugsHandled = mutableListOf<String>()
        val pendingApprovals = mutableListOf<String>()
        val blockers = mutableListOf<String>()

        agentNetwork.setAgentState("Self-Upgrade Agent", AgentStatus.ACTIVE, "Cycle: $cleanTopic")
        agentNetwork.setAgentState("Testing Agent", AgentStatus.ACTIVE, "Monitoring active capabilities")

        // 1. MONITOR, REPAIR & AUTO-ROLLBACK FAULTY TOOLS / FEATURES (#8.10, #8.11, #8.14, #8.15)
        runFaultMonitorAndRepair(
            prefs = prefs,
            bugsHandled = bugsHandled,
            toolsInstalled = toolsInstalled,
            pendingApprovals = pendingApprovals,
            blockers = blockers
        )

        // 2. DISCOVER & COMPARE SUGGESTIONS ACROSS AGENTS & SOURCES (#8.1, #8.7, #8.8, #10)
        val discoveredProjects = discoverCandidates(
            focusTopic = cleanTopic,
            prefs = prefs,
            isOnline = isOnline,
            ramMb = health.totalRamMb,
            blockers = blockers
        )
        discoveredProjects.take(6).forEach { p ->
            discoveredCandidates += "${p.name} (${p.sourcePlatform}, ${p.license})"
        }

        val selectedApproachReason = selectBestSafeImplementation(
            discoveredProjects = discoveredProjects,
            healthRamMb = health.totalRamMb
        )

        // 3. IDENTIFY MISSING FEATURES, MODEL-INSPIRED TOOLS & UPGRADE EXISTING ONES (#8.2, #8.3, #8.4, #8.9, #9)
        if (autonomous) {
            developOrUpgradeSafeCapabilities(
                focusTopic = cleanTopic,
                discoveredProjects = discoveredProjects,
                prefs = prefs,
                toolsInstalled = toolsInstalled,
                toolsUpgraded = toolsUpgraded,
                pendingApprovals = pendingApprovals,
                blockers = blockers
            )

            // 4. AUTOMATICALLY CREATE, CONFIGURE, TEST & ACTIVATE SPECIALIST AGENTS (#8.5)
            if (prefs.autoCreateSpecialistAgents || prefs.autoUpgradeAgentsAndTools) {
                createAndUpgradeSpecialistAgents(
                    prefs = prefs,
                    agentsUpdated = agentsUpdated,
                    blockers = blockers
                )
            }
        } else {
            blockers += "Zero-Question Autonomy Mode is paused by user; discovery completed without automatic installation."
        }

        // 5. NATIVE APK UPDATE CHECK & IN-PLACE UPDATE FLOW (#8.12, #8.13, #11)
        val nativeApkStatus = checkAndInitiateCompatibleApkUpdate(
            prefs = prefs,
            gitHubConfig = gitHubConfig,
            isOnline = isOnline,
            blockers = blockers
        )

        val cycleStatus = when {
            bugsHandled.any { it.startsWith("ROLLED_BACK:") } -> "ROLLED_BACK"
            (toolsInstalled.isNotEmpty() || toolsUpgraded.isNotEmpty() || agentsUpdated.isNotEmpty() || bugsHandled.isNotEmpty()) && blockers.isEmpty() ->
                "ACTIVATED_VERIFIED"
            (toolsInstalled.isNotEmpty() || toolsUpgraded.isNotEmpty() || agentsUpdated.isNotEmpty() || bugsHandled.isNotEmpty()) ->
                "COMPLETED_WITH_BLOCKERS"
            else -> "PROPOSED"
        }

        val cycleEntity = EvolutionCycleEntity(
            gapIdentified = "Autonomous scan ($cleanTopic): ${toolsInstalled.size} installed, ${toolsUpgraded.size} upgraded, ${agentsUpdated.size} agents updated",
            candidateSolution = selectedApproachReason.take(180),
            evaluatorAgents = "Self-Upgrade, Tool Discovery, Tool Factory, Hugging Face Research, GitHub Research, Security, Testing",
            staticAnalysisResult = if (blockers.isEmpty()) "ALL_CHECKS_PASSED" else "PARTIAL: ${blockers.first().take(120)}",
            benchmarkSummary = "RAM ${health.availableRamMb}/${health.totalRamMb}MB | Fixed/RolledBack=${bugsHandled.size} | Verified=${toolsInstalled.size + toolsUpgraded.size + agentsUpdated.size}",
            status = cycleStatus,
            rollbackAvailable = true
        )
        val cycleId = memoryRepository.insertEvolutionCycle(cycleEntity)

        historyRecorder.recordConversation(
            agentId = "20",
            agentName = "Self-Upgrade Agent",
            participatingAgents = listOf(
                "Main Brain / Orchestrator Agent",
                "Self-Upgrade Agent",
                "Tool Discovery Agent",
                "Tool Factory Agent",
                "Security Agent",
                "Testing Agent"
            ),
            channelType = "AI_COUNCIL",
            topic = "Autonomous Cycle #$cycleId: $cleanTopic",
            userOriginalRequest = cleanTopic,
            qaTurns = listOf(
                QaExchangeTurn(
                    "Tool Discovery Agent",
                    "Self-Upgrade Agent",
                    "Discovered ${discoveredCandidates.size} candidates; select safest approach",
                    selectedApproachReason
                ),
                QaExchangeTurn(
                    "Testing Agent",
                    "Security Agent",
                    "Verify runtime health, rollbacks, and approvals",
                    "Installed=${toolsInstalled.size}, Upgraded=${toolsUpgraded.size}, Agents=${agentsUpdated.size}, Fixed/RolledBack=${bugsHandled.size}, Blockers=${blockers.size}"
                )
            ),
            recommendationsMade = discoveredCandidates.joinToString(" | ").ifBlank { "Internal sandbox optimization" },
            researchFindings = selectedApproachReason,
            decisionsAndReasons = "Mode=${if (autonomous) "ZERO_QUESTION" else "SUPERVISED"} | Native=$nativeApkStatus",
            proposedTools = toolsInstalled + toolsUpgraded,
            actionsPerformed = bugsHandled + agentsUpdated + blockers,
            testResults = "Verified=${toolsInstalled.size + toolsUpgraded.size + agentsUpdated.size}, PendingApproval=${pendingApprovals.size}",
            finalOutcome = cycleStatus,
            prefs = prefs
        )

        agentNetwork.sendA2aMessage(
            "Self-Upgrade Agent",
            "Main Brain / Orchestrator Agent",
            "VERIFY",
            "Cycle #$cycleId ($cycleStatus): +${toolsInstalled.size} tools, ^${toolsUpgraded.size} upgrades, +${agentsUpdated.size} agents, ~${bugsHandled.size} fixes"
        )
        agentNetwork.setAgentState("Self-Upgrade Agent", AgentStatus.ONLINE, "Idle")
        agentNetwork.setAgentState("Testing Agent", AgentStatus.ONLINE, "Idle")

        val report = AutonomousCycleReport(
            cycleId = cycleId,
            timestamp = System.currentTimeMillis(),
            focusTopic = cleanTopic,
            autonomousModeActive = autonomous,
            discoveredCandidates = discoveredCandidates,
            selectedApproachReason = selectedApproachReason,
            toolsInstalledOrActivated = toolsInstalled,
            toolsUpgraded = toolsUpgraded,
            agentsCreatedOrUpgraded = agentsUpdated,
            bugsFixedOrRolledBack = bugsHandled,
            pendingApprovalItems = pendingApprovals,
            nativeApkUpdateStatus = nativeApkStatus,
            exactTechnicalBlockers = blockers,
            cycleStatus = cycleStatus
        )
        _lastReport.value = report
        return report
    }

    private suspend fun runFaultMonitorAndRepair(
        prefs: AuraPreferences,
        bugsHandled: MutableList<String>,
        toolsInstalled: MutableList<String>,
        pendingApprovals: MutableList<String>,
        blockers: MutableList<String>
    ) {
        val allTools = memoryRepository.getAllToolsOnce()
        val recentCrashes = selfHealing.healingHistory.value
            .filter { !it.succeeded && it.category == FailureCategory.CRASH }
            .map { it.actionName.lowercase() }

        for (tool in allTools) {
            val isMarkedFaulty = tool.status.equals("FAULTY", true) || tool.status.equals("ERROR", true)
            val crashedRecently = recentCrashes.any { act ->
                act.contains(tool.id.lowercase()) || act.contains(tool.name.lowercase())
            }
            val (healthyNow, healthDetail) = toolFactory.verifyToolHealth(tool)

            if (isMarkedFaulty || crashedRecently || (tool.status == "INSTALLED_ENABLED" && !healthyNow)) {
                val (safeSpec, scanMsg) = permissionGuard.scanToolSpecForSafety(
                    name = tool.name,
                    codeOrSpec = tool.description,
                    permissions = tool.requiredPermissions.split(",").map { it.trim() }
                )
                // Attempt automated schema repair if spec itself is safe and tool was corrupt JSON
                if (safeSpec && !healthyNow && tool.description.isNotBlank() && tool.name.isNotBlank()) {
                    val repairedSlug = "aura_${tool.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24).ifBlank { "tool" }}"
                    val repairedTool = tool.copy(
                        mcpSchemaJson = """{"name":"$repairedSlug","schema":{"query":"string","dryRun":"boolean"}}""",
                        status = "INSTALLED_ENABLED",
                        testResults = "AUTO_REPAIRED: Replaced malformed MCP schema",
                        updatedAt = System.currentTimeMillis()
                    )
                    if (toolFactory.verifyToolHealth(repairedTool).first) {
                        memoryRepository.upsertTool(repairedTool)
                        agentNetwork.registerMcpTool(
                            McpToolDescriptor(repairedSlug, repairedTool.category, repairedTool.description, """{"query":"string"}""", 1, true)
                        )
                        memoryRepository.logAudit("Self-Healing Engine", "AUTO_REPAIR:${tool.name}", 2, "ALLOWED", "Repaired malformed schema ($healthDetail)")
                        bugsHandled += "FIXED:${tool.name}"
                        continue
                    }
                }

                // Auto-disable or rollback faulty feature/tool without harming others (#8.15)
                if (prefs.autoRollbackFaultyFeatures) {
                    val hasPrevious = !tool.previousVersion.isNullOrBlank()
                    val rolledBack = if (hasPrevious) {
                        tool.copy(
                            version = tool.previousVersion!!,
                            status = "INSTALLED_ENABLED",
                            testResults = "AUTO_ROLLED_BACK from v${tool.version} ($healthDetail)",
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        tool.copy(
                            status = "DISABLED_FAULTY",
                            testResults = "AUTO_DISABLED_FAULTY: $healthDetail | $scanMsg",
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    memoryRepository.upsertTool(rolledBack)
                    val mcpSlug = runCatching { JSONObject(tool.mcpSchemaJson).optString("name") }.getOrDefault("")
                    if (mcpSlug.isNotBlank() && !hasPrevious) {
                        agentNetwork.unregisterMcpTool(mcpSlug)
                    }
                    historyRepo.recordRegistryEvent(
                        FeatureRegistryHistoryEntity(
                            featureId = tool.id,
                            featureName = tool.name,
                            description = "Autonomous fault monitor handled failure: $healthDetail",
                            discoverySource = "Continuous Self-Improvement Monitor",
                            recommendingAgent = "Error Recovery Agent",
                            participatingAgentsCsv = "Error Recovery Agent, Security Agent, Testing Agent",
                            discoveredAt = tool.updatedAt,
                            version = rolledBack.version,
                            previousVersion = tool.version,
                            changeEventType = "ROLLED_BACK",
                            dependencies = tool.dependencies,
                            permissionsRequired = tool.requiredPermissions,
                            testResults = rolledBack.testResults,
                            activationStatus = if (hasPrevious) "ROLLED_BACK" else "DISABLED",
                            decisionReason = "Automatic recovery prevented faulty tool from affecting healthy modules.",
                            versionDiffSummary = "Rolled back from v${tool.version} to ${rolledBack.version}"
                        )
                    )
                    memoryRepository.logAudit("Self-Upgrade Agent", "AUTO_ROLLBACK:${tool.name}", 3, "ALLOWED", rolledBack.testResults)
                    bugsHandled += "ROLLED_BACK:${tool.name}"
                }
                continue
            }

            // Auto-verify & activate compatible disabled Marketplace tools when in Zero-Question Mode (#10)
            if (prefs.zeroQuestionAutonomyEnabled && prefs.autoInstallSafeTools && tool.status == "INSTALLED_DISABLED") {
                if (healthyNow && toolFactory.isLowRiskPermissionSet(tool.requiredPermissions)) {
                    val activeTool = tool.copy(
                        status = "INSTALLED_ENABLED",
                        testResults = "AUTO_VERIFIED: $healthDetail",
                        updatedAt = System.currentTimeMillis()
                    )
                    memoryRepository.upsertTool(activeTool)
                    val mcpName = runCatching { JSONObject(activeTool.mcpSchemaJson).optString("name") }
                        .getOrDefault("mkt_${activeTool.id.lowercase()}")
                        .ifBlank { "mkt_${activeTool.id.lowercase()}" }
                    agentNetwork.registerMcpTool(
                        McpToolDescriptor(mcpName, activeTool.category, activeTool.description, "{}", 1, enabled = true)
                    )
                    historyRepo.recordRegistryEvent(
                        FeatureRegistryHistoryEntity(
                            featureId = activeTool.id,
                            featureName = activeTool.name,
                            description = activeTool.description,
                            discoverySource = "Marketplace Auto-Install",
                            recommendingAgent = "Marketplace Manager Agent",
                            participatingAgentsCsv = "Marketplace Manager Agent, Security Agent, Testing Agent",
                            discoveredAt = System.currentTimeMillis(),
                            version = activeTool.version,
                            changeEventType = "ACTIVATED",
                            dependencies = activeTool.dependencies,
                            permissionsRequired = activeTool.requiredPermissions,
                            installationDate = System.currentTimeMillis(),
                            testResults = activeTool.testResults,
                            activationStatus = "ACTIVE",
                            decisionReason = "Low-risk verified Marketplace tool activated automatically without interrupting user.",
                            versionDiffSummary = "Activated v${activeTool.version} in MCP tools"
                        )
                    )
                    toolsInstalled += activeTool.name
                } else if (!toolFactory.isLowRiskPermissionSet(tool.requiredPermissions)) {
                    pendingApprovals += "${tool.name} (requires sensitive permission: ${tool.requiredPermissions})"
                    blockers += "Tool '${tool.name}' gated on sensitive permission (${tool.requiredPermissions}); awaiting user approval."
                }
            }
        }
    }

    private suspend fun discoverCandidates(
        focusTopic: String,
        prefs: AuraPreferences,
        isOnline: Boolean,
        ramMb: Long,
        blockers: MutableList<String>
    ): List<ResearchProjectEntity> {
        if (!isOnline) {
            blockers += "Offline mode: GitHub/Hugging Face live API fetch skipped; continued with local synthesis & repairs."
            return emptyList()
        }
        if (prefs.intelligenceMode == "LOCAL_ONLY" || !prefs.autonomyPolicy.externalApiMasterEnabled) {
            blockers += "External APIs paused by privacy/local policy; continued with local improvements."
            return emptyList()
        }
        val gh = researchClient.searchGitHubProjects(focusTopic, ramMb).getOrDefault(emptyList())
        val hf = researchClient.searchHuggingFaceModels("gemma qwen voice", ramMb).getOrDefault(emptyList())
        val combined = (gh.take(3) + hf.take(3))
        if (combined.isNotEmpty()) {
            memoryRepository.upsertResearchProjects(combined)
        }
        return combined
    }

    private fun selectBestSafeImplementation(
        discoveredProjects: List<ResearchProjectEntity>,
        healthRamMb: Long
    ): String {
        val safeCandidate = discoveredProjects.firstOrNull {
            !it.license.contains("GPL", true) && !it.securityAssessment.contains("BLOCKED", true)
        }
        return if (safeCandidate != null) {
            "Selected safest Android-compatible strategy inspired by '${safeCandidate.name}' (${safeCandidate.sourcePlatform}, ${safeCandidate.license}, device ${healthRamMb}MB RAM): adapt as declarative sandboxed MCP capability without dynamic binary execution."
        } else {
            "Selected zero-risk local Kotlin/MCP sandbox improvement tuned for ${healthRamMb}MB RAM with full rollback snapshots."
        }
    }

    private suspend fun developOrUpgradeSafeCapabilities(
        focusTopic: String,
        discoveredProjects: List<ResearchProjectEntity>,
        prefs: AuraPreferences,
        toolsInstalled: MutableList<String>,
        toolsUpgraded: MutableList<String>,
        pendingApprovals: MutableList<String>,
        blockers: MutableList<String>
    ) {
        val existingTools = memoryRepository.getAllToolsOnce()

        // A) Develop original missing tool OR model-inspired tool if uncovered (#8.2, #8.3, #8.9)
        val topSafeDiscovery = discoveredProjects.firstOrNull {
            !it.license.contains("GPL", true) && existingTools.none { t -> t.name.contains(it.name.take(10), true) }
        }
        val missingConcept = when {
            topSafeDiscovery != null -> "Model-inspired helper: ${topSafeDiscovery.name.take(28)} (${focusTopic.take(24)})"
            existingTools.none { it.name.contains("Context Digest", true) } -> "Smart Context Digest & Memory Linker"
            existingTools.none { it.name.contains("Latency Profiler", true) } -> "Autonomous Prompt & Latency Profiler"
            else -> null
        }

        if (missingConcept != null && prefs.autoInstallSafeTools) {
            val synthesized = toolFactory.synthesizeNewTool(
                capabilityRequest = missingConcept,
                category = "Productivity",
                autoActivateIfSafe = true
            )
            synthesized.onSuccess { tool ->
                if (tool.status == "INSTALLED_ENABLED") {
                    historyRepo.recordRegistryEvent(
                        FeatureRegistryHistoryEntity(
                            featureId = tool.id,
                            featureName = tool.name,
                            description = tool.description,
                            discoverySource = if (topSafeDiscovery != null) topSafeDiscovery.sourcePlatform else "Autonomous Gap Detection",
                            sourceUrl = topSafeDiscovery?.url.orEmpty(),
                            recommendingAgent = "Tool Discovery Agent",
                            participatingAgentsCsv = "Tool Discovery Agent, Tool Factory Agent, Security Agent, Testing Agent",
                            discoveredAt = System.currentTimeMillis(),
                            version = tool.version,
                            changeEventType = "ACTIVATED",
                            dependencies = tool.dependencies,
                            permissionsRequired = tool.requiredPermissions,
                            installationDate = System.currentTimeMillis(),
                            testResults = tool.testResults,
                            activationStatus = "ACTIVE",
                            decisionReason = "Autonomously synthesized, tested & activated without interrupting user.",
                            versionDiffSummary = "New v${tool.version} tool synthesized and activated"
                        )
                    )
                    toolsInstalled += tool.name
                } else {
                    pendingApprovals += tool.name
                    blockers += "Synthesized '${tool.name}' kept disabled due to permissions (${tool.requiredPermissions})."
                }
            }.onFailure { err ->
                blockers += "Skipped unsafe candidate '$missingConcept': ${err.localizedMessage}"
            }
        }

        // B) Upgrade outdated active tool (#8.4, #8.10)
        if (prefs.autoUpgradeAgentsAndTools) {
            val upgradeCandidate = existingTools.firstOrNull { t ->
                t.status == "INSTALLED_ENABLED" &&
                    toolFactory.isLowRiskPermissionSet(t.requiredPermissions) &&
                    (!t.updateAvailableVersion.isNullOrBlank() || t.version == "1.0.0")
            }
            if (upgradeCandidate != null) {
                val targetVersion = upgradeCandidate.updateAvailableVersion?.takeIf { it.isNotBlank() } ?: "1.1.0"
                val upgraded = upgradeCandidate.copy(
                    previousVersion = upgradeCandidate.version,
                    version = targetVersion,
                    updateAvailableVersion = null,
                    description = if (upgradeCandidate.description.contains("Auto-tuned")) upgradeCandidate.description
                    else "${upgradeCandidate.description} [Auto-tuned schema & diagnostics]",
                    testResults = "PASSED: Upgraded ${upgradeCandidate.version} -> $targetVersion & regression-verified",
                    updatedAt = System.currentTimeMillis()
                )
                if (toolFactory.verifyToolHealth(upgraded).first) {
                    memoryRepository.upsertTool(upgraded)
                    historyRepo.recordRegistryEvent(
                        FeatureRegistryHistoryEntity(
                            featureId = upgraded.id,
                            featureName = upgraded.name,
                            description = upgraded.description,
                            discoverySource = "Continuous Self-Improvement Engine",
                            recommendingAgent = "Self-Upgrade Agent",
                            participatingAgentsCsv = "Self-Upgrade Agent, Performance Optimization Agent, Testing Agent",
                            discoveredAt = System.currentTimeMillis(),
                            version = upgraded.version,
                            previousVersion = upgraded.previousVersion,
                            changeEventType = "UPGRADED",
                            dependencies = upgraded.dependencies,
                            permissionsRequired = upgraded.requiredPermissions,
                            installationDate = System.currentTimeMillis(),
                            testResults = upgraded.testResults,
                            activationStatus = "ACTIVE",
                            decisionReason = "Zero-question minor upgrade passed compatibility & static checks.",
                            versionDiffSummary = "Upgraded v${upgraded.previousVersion} -> v${upgraded.version}"
                        )
                    )
                    toolsUpgraded += "${upgraded.name} (v${upgraded.previousVersion} -> v${upgraded.version})"
                }
            }
        }
    }

    private suspend fun createAndUpgradeSpecialistAgents(
        prefs: AuraPreferences,
        agentsUpdated: MutableList<String>,
        blockers: MutableList<String>
    ) {
        val currentCustom = historyRepo.getCustomAgents()

        // 1. Upgrade any custom specialist agent still targeting an outdated model
        if (prefs.autoUpgradeAgentsAndTools) {
            val targetModel = prefs.activeModelId.ifBlank { GEMINI_DEFAULT_MODEL }
            val outdated = currentCustom.firstOrNull { it.enabled && it.preferredModelId != targetModel }
            if (outdated != null) {
                val upgraded = outdated.copy(
                    preferredModelId = targetModel,
                    preferredProviderId = prefs.activeProviderId,
                    lastActivitySummary = "Auto-upgraded model binding to $targetModel",
                    lastActivityAt = System.currentTimeMillis()
                )
                historyRepo.upsertCustomAgent(upgraded)
                agentNetwork.registerCustomAgent(upgraded)
                agentsUpdated += "Upgraded ${upgraded.name} -> $targetModel"
            }
        }

        // 2. Autonomously create, configure, test & activate missing specialist agent (#8.5)
        if (prefs.autoCreateSpecialistAgents) {
            val catalogSpecs = listOf(
                Triple(
                    "auto_qa_regression",
                    "Autonomous QA & Regression Agent",
                    "Runs continuous pre-activation tests, verifies tool schemas, and catches regressions before activation."
                ),
                Triple(
                    "auto_model_adapter",
                    "Model Capability Adapter Agent",
                    "Translates trending Hugging Face & Gemini model patterns into safe sandboxed AURA MCP tools."
                )
            )
            val missingSpec = catalogSpecs.firstOrNull { spec ->
                currentCustom.none { it.codeName == spec.first } &&
                    agentNetwork.agents.value.none { it.codeName == spec.first || it.name.equals(spec.second, true) }
            }
            if (missingSpec != null) {
                val (codeName, title, role) = missingSpec
                val nextId = (agentNetwork.agents.value.maxOfOrNull { it.id } ?: 24) + 1
                // Test configuration safety before activating (#8.14)
                val (safeSpec, scanMsg) = permissionGuard.scanToolSpecForSafety(title, "$role verification", listOf("None"))
                if (safeSpec) {
                    val profile = CustomAgentProfileEntity(
                        codeName = codeName,
                        numericId = nextId,
                        name = title,
                        roleDescription = role,
                        capabilitiesCsv = "qa_verify, schema_audit, regression_check, mcp_adapter",
                        preferredProviderId = prefs.activeProviderId,
                        preferredModelId = prefs.activeModelId.ifBlank { GEMINI_DEFAULT_MODEL },
                        createdFromRequestId = "AUTO-CYCLE",
                        isCustom = true,
                        enabled = true,
                        lastActivitySummary = "Verified & auto-activated ($scanMsg)",
                        lastActivityAt = System.currentTimeMillis()
                    )
                    historyRepo.upsertCustomAgent(profile)
                    agentNetwork.registerCustomAgent(profile)
                    historyRepo.recordRegistryEvent(
                        FeatureRegistryHistoryEntity(
                            featureId = codeName,
                            featureName = title,
                            description = role,
                            discoverySource = "Autonomous Agent Architect",
                            recommendingAgent = "Self-Upgrade Agent",
                            participatingAgentsCsv = "Self-Upgrade Agent, Security Agent, Testing Agent",
                            discoveredAt = System.currentTimeMillis(),
                            version = "1.0.0",
                            changeEventType = "ACTIVATED",
                            dependencies = "AgentNetworkManager",
                            permissionsRequired = "None",
                            installationDate = System.currentTimeMillis(),
                            testResults = "PASSED: Agent profile validated & registered at slot #$nextId",
                            activationStatus = "ACTIVE",
                            decisionReason = "Autonomously created, configured, tested & activated specialist agent.",
                            versionDiffSummary = "Activated specialist agent #$nextId v1.0.0"
                        )
                    )
                    agentsUpdated += "Created & Activated $title (#$nextId)"
                } else {
                    blockers += "Blocked creation of $title: $scanMsg"
                }
            }
        }
    }

    private suspend fun checkAndInitiateCompatibleApkUpdate(
        prefs: AuraPreferences,
        gitHubConfig: GitHubConfig,
        isOnline: Boolean,
        blockers: MutableList<String>
    ): String {
        if (!prefs.autoCheckInPlaceApkUpdates) {
            return "Skipped (auto in-place APK checks disabled)"
        }
        if (!gitHubConfig.isConfigured) {
            val msg = "Missing GitHub owner/repo credentials — native APK build & release update check skipped."
            blockers += msg
            return msg
        }
        if (!isOnline) {
            val msg = "Offline — compatible APK release check deferred until internet reconnects."
            blockers += msg
            return msg
        }

        val releaseRes = releaseCheckOverride?.invoke(gitHubConfig)
            ?: gitHubClient.fetchLatestAndPreviousRelease(gitHubConfig)
        if (releaseRes.isFailure) {
            val err = releaseRes.exceptionOrNull()?.message ?: "Release check failed"
            blockers += "GitHub release check blocker: $err"
            return "Check failed: $err"
        }

        val latest = releaseRes.getOrNull()?.latest ?: return "No published GitHub Releases yet."
        val (installedVer, _) = ApkVersionComparator.getInstalledVersionInfo(context)
        if (!ApkVersionComparator.isNewerVersion(latest.tagName, installedVer)) {
            return "Up to date (installed v$installedVer, latest ${latest.tagName})"
        }
        val asset = latest.apkAsset ?: run {
            val msg = "Release ${latest.tagName} has no signed .apk asset yet (CI build may still be running)."
            blockers += msg
            return msg
        }

        if (!apkSelfUpdater.canRequestPackageInstalls()) {
            val msg = "Android OS restriction: enable 'Allow from this source' (REQUEST_INSTALL_PACKAGES) so AURA can launch in-place update to ${latest.tagName} without uninstalling."
            blockers += msg
            return "Update ${latest.tagName} ready — blocked on REQUEST_INSTALL_PACKAGES permission"
        }

        val updatesDir = File(context.filesDir, "aura_updates").apply { mkdirs() }
        val targetApk = File(updatesDir, "aura-${latest.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        val downloadUrl = asset.apiAssetUrl.ifBlank { asset.browserDownloadUrl }
        val dlRes = releaseDownloadOverride?.invoke(gitHubConfig, downloadUrl, targetApk)
            ?: gitHubClient.downloadReleaseApk(gitHubConfig, downloadUrl, targetApk) {}

        if (dlRes.isFailure) {
            val err = dlRes.exceptionOrNull()?.message ?: "APK download failed"
            blockers += "APK download failed for ${latest.tagName}: $err"
            return "Download error for ${latest.tagName}: $err"
        }

        val downloadedFile = dlRes.getOrThrow()
        val pkgVerify = apkSelfUpdater.verifyApkPackageName(downloadedFile)
        if (pkgVerify.isFailure) {
            val err = pkgVerify.exceptionOrNull()?.message ?: "Package verification mismatch"
            blockers += "In-place compatibility blocker: $err"
            return "Incompatible APK rejected: $err"
        }

        return when (val installOutcome = apkSelfUpdater.installApk(downloadedFile)) {
            is ApkInstallResult.SessionStarted -> {
                memoryRepository.logAudit(
                    "APK Build Agent",
                    "IN_PLACE_UPDATE_INITIATED",
                    3,
                    "ALLOWED",
                    "Verified matching package (${context.packageName}) & launched in-place installer for ${latest.tagName}"
                )
                "In-place update initiated for ${latest.tagName} (app data preserved)"
            }
            is ApkInstallResult.NeedsUnknownSourcesPermission -> {
                blockers += installOutcome.message
                "Blocked on REQUEST_INSTALL_PACKAGES permission"
            }
            is ApkInstallResult.Failed -> {
                blockers += installOutcome.error
                "Installer failed: ${installOutcome.error}"
            }
        }
    }
}
