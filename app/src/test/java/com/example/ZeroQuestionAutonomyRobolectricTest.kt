package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.agents.McpRuntimeEngine
import com.example.aura.ai.DispatchModuleIds
import com.example.aura.ai.DispatchTarget
import com.example.aura.ai.IncomingTaskRequest
import com.example.aura.ai.ModelRoutingEngine
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.ai.TaskDispatcher
import com.example.aura.ai.TaskIntentCategory
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.calls.PhoneCallAndMessagingManager
import com.example.aura.evolution.ContinuousSelfImprovementEngine
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.github.ApkSelfUpdater
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubReleaseAsset
import com.example.aura.github.GitHubReleaseInfo
import com.example.aura.github.GitHubReleasePair
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.github.GitHubSetupDiagnostics
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.CustomAgentProfileEntity
import com.example.aura.memory.MarketplaceToolEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.ui.AuraViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZeroQuestionAutonomyRobolectricTest {

    private data class AutonomyTestStack(
        val context: Context,
        val db: AuraDatabase,
        val memRepo: MemoryRepository,
        val histRepo: AgentHistoryRepository,
        val agentNet: AgentNetworkManager,
        val mcpRuntime: McpRuntimeEngine,
        val toolFactory: ToolFactoryAndEvolutionEngine,
        val taskDispatcher: TaskDispatcher,
        val selfHealing: SelfHealingEngine,
        val engine: ContinuousSelfImprovementEngine
    )

    private fun buildStack(
        networkOnline: Boolean = false,
        gitHubApiBaseUrl: String = "https://api.github.com",
        huggingFaceApiBaseUrl: String = "https://huggingface.co",
        releaseCheckOverride: (suspend (GitHubConfig) -> Result<GitHubReleasePair>)? = null
    ): AutonomyTestStack {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val memRepo = MemoryRepository(db)
        val histRepo = AgentHistoryRepository(db)
        val privacyRouter = PrivacyDataRouter()
        val recorder = AgentHistoryRecorder(histRepo, privacyRouter)
        val agentNet = AgentNetworkManager()
        val permGuard = PermissionGuard()
        val devCtrl = DeviceControlEngine(context)
        val vault = AuraKeystoreVault(context)
        val prefsRepo = PreferencesRepository(context)
        val modelRouter = ModelRoutingEngine()
        val router = MultiProviderRouter(
            vault = vault,
            memoryRepository = memRepo,
            privacyRouter = privacyRouter,
            modelRouter = modelRouter,
            preferencesRepository = prefsRepo
        )
        val research = GitHubAndHfResearchClient(
            gitHubApiBaseUrl = gitHubApiBaseUrl,
            huggingFaceApiBaseUrl = huggingFaceApiBaseUrl
        )
        val mcpRuntime = McpRuntimeEngine(
            context = context,
            agentNetwork = agentNet,
            permissionGuard = permGuard,
            memoryRepository = memRepo,
            deviceControl = devCtrl,
            researchClient = research,
            privacyRouter = privacyRouter
        )
        val toolFactory = ToolFactoryAndEvolutionEngine(
            aiRouter = router,
            researchClient = research,
            memoryRepository = memRepo,
            agentNetwork = agentNet,
            permissionGuard = permGuard,
            deviceControl = devCtrl,
            mcpRuntime = mcpRuntime
        ).apply {
            historyRecorder = recorder
            featureHistoryRepo = histRepo
        }
        val pipeline = FeatureControlPipelineEngine(
            historyRepo = histRepo,
            recorder = recorder,
            memoryRepo = memRepo,
            aiRouter = router,
            agentNetwork = agentNet,
            permissionGuard = permGuard,
            deviceControl = devCtrl,
            toolFactory = toolFactory,
            researchClient = research
        )
        val selfHealing = SelfHealingEngine(context)
        val taskDispatcher = TaskDispatcher(
            aiRouter = router,
            modelRouter = modelRouter,
            memoryRepository = memRepo,
            permissionGuard = permGuard,
            agentNetwork = agentNet,
            deviceControl = devCtrl,
            researchClient = research,
            toolFactory = toolFactory,
            selfHealing = selfHealing,
            callMessaging = PhoneCallAndMessagingManager(context),
            mcpRuntime = mcpRuntime
        )
        val engine = ContinuousSelfImprovementEngine(
            context = context,
            memoryRepository = memRepo,
            historyRepo = histRepo,
            historyRecorder = recorder,
            agentNetwork = agentNet,
            toolFactory = toolFactory,
            pipelineEngine = pipeline,
            researchClient = research,
            deviceControl = devCtrl,
            selfHealing = selfHealing,
            permissionGuard = permGuard,
            gitHubClient = GitHubSelfUpdateClient(),
            apkSelfUpdater = ApkSelfUpdater(context),
            networkOnlineProvider = { networkOnline },
            releaseCheckOverride = releaseCheckOverride
        )
        return AutonomyTestStack(
            context = context,
            db = db,
            memRepo = memRepo,
            histRepo = histRepo,
            agentNet = agentNet,
            mcpRuntime = mcpRuntime,
            toolFactory = toolFactory,
            taskDispatcher = taskDispatcher,
            selfHealing = selfHealing,
            engine = engine
        )
    }

    @Test
    fun safeToolsAutoInstallVerifyAndActivateZeroQuestion() = runTest {
        val stack = buildStack(networkOnline = false)
        try {
            val safeDisabled = MarketplaceToolEntity(
                id = "MKT-SAFE-01",
                name = "JSON Structured Formatter",
                category = "Productivity",
                version = "1.2.0",
                source = "MARKETPLACE",
                license = "Apache-2.0",
                description = "Formats structured prompt notes safely.",
                requiredPermissions = "None",
                dependencies = "Kotlin Stdlib",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Verified",
                mcpSchemaJson = """{"name":"mkt_safe_json","schema":{"text":"string"}}""",
                status = "INSTALLED_DISABLED"
            )
            val sensitiveDisabled = MarketplaceToolEntity(
                id = "MKT-SENSITIVE-02",
                name = "Live Call Dialer Helper",
                category = "Communication",
                version = "1.0.0",
                source = "MARKETPLACE",
                license = "Apache-2.0",
                description = "Places phone calls using dialer intent.",
                requiredPermissions = "CALL_PHONE",
                dependencies = "Telecom",
                compatibility = "Android 8+",
                securityScan = "REVIEW_REQUIRED",
                testResults = "Pending Approval",
                mcpSchemaJson = """{"name":"mkt_call_helper","schema":{"phone":"string"}}""",
                status = "INSTALLED_DISABLED"
            )
            stack.memRepo.upsertTool(safeDisabled)
            stack.memRepo.upsertTool(sensitiveDisabled)

            // Direct ToolFactory synthesis with autoActivateIfSafe = true
            val synthRes = stack.toolFactory.synthesizeNewTool(
                capabilityRequest = "Quick Note Summarizer",
                category = "Productivity",
                autoActivateIfSafe = true
            )
            assertTrue(synthRes.isSuccess)
            val synthTool = synthRes.getOrThrow()
            assertEquals("INSTALLED_ENABLED", synthTool.status)
            assertTrue(synthTool.testResults.contains("PASSED:", ignoreCase = true))

            val report = stack.engine.runAutonomousCycle(
                focusTopic = "context optimization",
                prefs = AuraPreferences(zeroQuestionAutonomyEnabled = true),
                gitHubConfig = GitHubConfig()
            )

            // Safe marketplace tool must be activated automatically without user interruption
            val allTools = stack.memRepo.getAllToolsOnce()
            val refreshedSafe = allTools.first { it.id == "MKT-SAFE-01" }
            assertEquals("INSTALLED_ENABLED", refreshedSafe.status)
            assertTrue(report.toolsInstalledOrActivated.any { it.contains("JSON Structured Formatter") })

            // Sensitive tool MUST remain gated awaiting approval
            val refreshedSensitive = allTools.first { it.id == "MKT-SENSITIVE-02" }
            assertEquals("INSTALLED_DISABLED", refreshedSensitive.status)
            assertTrue(report.pendingApprovalItems.any { it.contains("Live Call Dialer Helper") })
            assertTrue(report.exactTechnicalBlockers.any { it.contains("CALL_PHONE") })

            // Registry & MCP tools updated
            assertTrue(stack.agentNet.mcpTools.value.any { it.name == "mkt_safe_json" && it.enabled })
            val registryHistory = stack.histRepo.getAllRegistryHistory()
            assertTrue(registryHistory.any { it.featureId == "MKT-SAFE-01" && it.activationStatus == "ACTIVE" })
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun faultyToolsAutoRepairOrRollbackWithoutHarmingHealthyFeatures() = runTest {
        val stack = buildStack(networkOnline = false)
        try {
            val healthyTool = MarketplaceToolEntity(
                id = "TOOL-HEALTHY",
                name = "Healthy Clipboard Reader",
                category = "Utility",
                version = "2.0.0",
                source = "MARKETPLACE",
                license = "MIT",
                description = "Reads safe clipboard snippets.",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Healthy",
                mcpSchemaJson = """{"name":"healthy_clip","schema":{"q":"string"}}""",
                status = "INSTALLED_ENABLED"
            )
            val corruptJsonTool = MarketplaceToolEntity(
                id = "TOOL-CORRUPT-SCHEMA",
                name = "Broken Schema Helper",
                category = "Utility",
                version = "1.0.0",
                source = "LOCAL_SYNTHESIZED",
                license = "MIT",
                description = "Helpful text normalizer with bad json.",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Schema Error",
                mcpSchemaJson = "NOT_VALID_JSON",
                status = "INSTALLED_ENABLED"
            )
            val crashedUpgradeTool = MarketplaceToolEntity(
                id = "TOOL-CRASH-V2",
                name = "Experimental Parser",
                category = "Productivity",
                version = "1.2.0",
                previousVersion = "1.1.0",
                source = "LOCAL_SYNTHESIZED",
                license = "MIT",
                description = "Evaluates eval(x) unsafe code block",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Crashed",
                mcpSchemaJson = """{"name":"exp_parser","schema":{"q":"string"}}""",
                status = "FAULTY"
            )
            stack.memRepo.upsertTool(healthyTool)
            stack.memRepo.upsertTool(corruptJsonTool)
            stack.memRepo.upsertTool(crashedUpgradeTool)

            val report = stack.engine.runAutonomousCycle(
                focusTopic = "stability audit",
                prefs = AuraPreferences(zeroQuestionAutonomyEnabled = true, autoRollbackFaultyFeatures = true),
                gitHubConfig = GitHubConfig()
            )

            val after = stack.memRepo.getAllToolsOnce()
            // Healthy tool stays untouched & enabled
            assertEquals("INSTALLED_ENABLED", after.first { it.id == "TOOL-HEALTHY" }.status)
            assertEquals("2.0.0", after.first { it.id == "TOOL-HEALTHY" }.version)

            // Corrupt JSON tool auto-repaired & valid now
            val repaired = after.first { it.id == "TOOL-CORRUPT-SCHEMA" }
            assertEquals("INSTALLED_ENABLED", repaired.status)
            assertTrue(repaired.mcpSchemaJson.trim().startsWith("{"))
            assertTrue(report.bugsFixedOrRolledBack.any { it == "FIXED:Broken Schema Helper" })

            // Faulty v1.2.0 rolled back cleanly to previousVersion 1.1.0 and recorded in Feature History
            val rolled = after.first { it.id == "TOOL-CRASH-V2" }
            assertEquals("1.1.0", rolled.version)
            assertTrue(rolled.testResults.contains("ROLLED_BACK"))
            assertEquals("INSTALLED_ENABLED", rolled.status)
            assertTrue(report.bugsFixedOrRolledBack.any { it == "ROLLED_BACK:Experimental Parser" })
            assertEquals("ROLLED_BACK", report.cycleStatus)
            assertTrue(
                stack.histRepo.getAllRegistryHistory().any {
                    it.featureId == "TOOL-CRASH-V2" && it.changeEventType == "ROLLED_BACK"
                }
            )
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun autonomousSpecialistAgentCreationAndUpgradeAndToolUpgrade() = runTest {
        val stack = buildStack(networkOnline = false)
        try {
            stack.memRepo.upsertTool(
                MarketplaceToolEntity(
                    id = "TOOL-UPGRADE-01",
                    name = "Prompt Analyzer",
                    category = "AI",
                    version = "1.0.0",
                    updateAvailableVersion = "1.1.0",
                    source = "MARKETPLACE",
                    license = "Apache-2.0",
                    description = "Analyzes prompt complexity.",
                    requiredPermissions = "None",
                    dependencies = "None",
                    compatibility = "Android 8+",
                    securityScan = "PASSED",
                    testResults = "Verified",
                    mcpSchemaJson = """{"name":"prompt_analyzer","schema":{"text":"string"}}""",
                    status = "INSTALLED_ENABLED"
                )
            )
            stack.histRepo.upsertCustomAgent(
                CustomAgentProfileEntity(
                    codeName = "custom_helper_agent",
                    numericId = 26,
                    name = "Custom Helper Agent",
                    roleDescription = "Assists with prompt routing.",
                    capabilitiesCsv = "routing",
                    preferredProviderId = "GEMINI_CLOUD",
                    preferredModelId = "gemini-1.5-flash-legacy",
                    createdFromRequestId = "REQ-1",
                    enabled = true
                )
            )

            val report = stack.engine.runAutonomousCycle(
                focusTopic = "agent optimization",
                prefs = AuraPreferences(
                    zeroQuestionAutonomyEnabled = true,
                    activeModelId = "gemini-2.5-flash"
                ),
                gitHubConfig = GitHubConfig()
            )

            // Outdated tool upgraded with real executable capabilities & rollback snapshot preserved
            val upgradedTool = stack.memRepo.getAllToolsOnce().first { it.id == "TOOL-UPGRADE-01" }
            assertEquals("1.1.0", upgradedTool.version)
            assertEquals("1.0.0", upgradedTool.previousVersion)
            assertTrue(upgradedTool.mcpSchemaJson.contains("LOCAL_SANDBOX_MCP"))
            assertTrue(upgradedTool.mcpSchemaJson.contains("testPayload"))
            assertTrue(
                stack.histRepo.getAllRegistryHistory().any {
                    it.featureId == "TOOL-UPGRADE-01" &&
                        it.changeEventType == "UPGRADED" &&
                        it.rollbackSnapshotJson.contains("previousMcpSchemaJson")
                }
            )
            assertTrue(report.toolsUpgraded.any { it.contains("Prompt Analyzer") && it.contains("v1.1.0") })

            // Outdated specialist agent upgraded + missing specialist agent created & activated
            val agents = stack.histRepo.getCustomAgents()
            val helper = agents.first { it.codeName == "custom_helper_agent" }
            assertEquals("gemini-2.5-flash", helper.preferredModelId)

            val createdAgent = agents.firstOrNull { it.codeName == "auto_qa_regression" }
            assertNotNull(createdAgent)
            assertTrue(createdAgent!!.enabled)
            assertTrue(stack.agentNet.agents.value.any { it.codeName == "auto_qa_regression" })
            assertTrue(report.agentsCreatedOrUpgraded.any { it.contains("Autonomous QA & Regression Agent") })
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun reportsExactTechnicalBlockersWithoutStoppingOtherImprovements() = runTest {
        val stack = buildStack(
            networkOnline = true,
            releaseCheckOverride = {
                Result.success(
                    GitHubReleasePair(
                        latest = GitHubReleaseInfo(
                            tagName = "v99.0.0",
                            name = "AURA v99.0.0",
                            body = "Signed CI release",
                            htmlUrl = "https://github.com/aura/aura/releases/tag/v99.0.0",
                            apkAsset = GitHubReleaseAsset(
                                id = 42L,
                                name = "aura-release.apk",
                                apiAssetUrl = "https://api.github.com/repos/aura/aura/releases/assets/42",
                                browserDownloadUrl = "https://github.com/aura/aura/releases/download/v99.0.0/aura-release.apk",
                                sizeBytes = 10_000_000L
                            )
                        ),
                        previous = null
                    )
                )
            }
        )
        try {
            val report = stack.engine.runAutonomousCycle(
                focusTopic = "voice automation",
                prefs = AuraPreferences(
                    zeroQuestionAutonomyEnabled = true,
                    intelligenceMode = "LOCAL_ONLY",
                    autoCheckInPlaceApkUpdates = true
                ),
                gitHubConfig = GitHubConfig(owner = "aura-org", repo = "aura-ai", branch = "main", token = "ghp_test")
            )

            // Report MUST still create local specialist agent & safe tool despite blockers
            assertTrue(report.totalVerifiedChanges > 0)
            // Must state exact blockers honestly instead of silently skipping or faking APK install
            assertTrue(report.exactTechnicalBlockers.any { it.contains("LOCAL_ONLY", true) || it.contains("privacy", true) })
            assertTrue(report.exactTechnicalBlockers.any { it.contains("REQUEST_INSTALL_PACKAGES") })
            assertTrue(report.nativeApkUpdateStatus.contains("v99.0.0"))
            assertFalse(report.nativeApkUpdateStatus.contains("initiated", ignoreCase = true))
            assertEquals("COMPLETED_WITH_BLOCKERS", report.cycleStatus)
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun generatedMcpToolsExecuteThroughTaskDispatcherAndMcpRuntime() = runTest {
        val stack = buildStack(networkOnline = false)
        try {
            stack.memRepo.seedInitialCatalogIfEmpty()
            val synthRes = stack.toolFactory.synthesizeNewTool(
                capabilityRequest = "Quick Note Summarizer",
                category = "Productivity",
                autoActivateIfSafe = true
            )
            assertTrue(synthRes.isSuccess)
            val tool = synthRes.getOrThrow()
            stack.mcpRuntime.syncEnabledToolsToNetwork()

            val prompt = "run tool ${tool.name}: Release candidate 16 passed all 48 tests with 31ms average latency and 0 errors."
            val plan = stack.taskDispatcher.evaluateIntent(
                IncomingTaskRequest(
                    rawInstruction = prompt,
                    prefs = AuraPreferences(zeroQuestionAutonomyEnabled = true),
                    isNetworkAvailable = false
                )
            )
            assertEquals(TaskIntentCategory.MCP_RUNTIME, plan.intentCategory)
            assertTrue(plan.target is DispatchTarget.InternalModule)
            assertEquals(DispatchModuleIds.MCP_RUNTIME, (plan.target as DispatchTarget.InternalModule).moduleId)

            val dispatchResult = stack.taskDispatcher.dispatch(
                IncomingTaskRequest(
                    rawInstruction = prompt,
                    prefs = AuraPreferences(zeroQuestionAutonomyEnabled = true),
                    isNetworkAvailable = false
                )
            )
            assertTrue(dispatchResult.success)
            assertTrue(dispatchResult.responseText.contains(tool.name) || dispatchResult.responseText.contains("Summary"))
            assertTrue(dispatchResult.responseText.contains("Release candidate 16"))

            // Also synthesize & verify direct JSON transformer execution
            val jsonSynthRes = stack.toolFactory.synthesizeNewTool(
                capabilityRequest = "JSON Schema Formatter",
                category = "Developer Utilities",
                autoActivateIfSafe = true
            )
            assertTrue(jsonSynthRes.isSuccess)
            val jsonTool = jsonSynthRes.getOrThrow()
            stack.mcpRuntime.syncEnabledToolsToNetwork()

            val jsonExec = stack.mcpRuntime.executeToolByNameOrQuery(
                toolIdentifier = jsonTool.id,
                rawInput = """{"status":"ok","count":3}"""
            )
            assertTrue(jsonExec.success)
            assertEquals("JSON_TRANSFORMER", jsonExec.operation)
            assertTrue(jsonExec.outputJson.optInt("keyCount") == 2)
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun rejectsNoOpVersionBumpUpgradesAndRestoresPreviousSchemaOnRollback() = runTest {
        val stack = buildStack(networkOnline = false)
        try {
            val originalSchema = """{"name":"alpha_eval","schema":{"prompt":"string"}}"""
            val tool = MarketplaceToolEntity(
                id = "TOOL-NOOP-CHECK",
                name = "Alpha Prompt Scorer",
                category = "AI",
                version = "1.0.0",
                source = "MARKETPLACE",
                license = "MIT",
                description = "Scores prompt clarity.",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Verified",
                mcpSchemaJson = originalSchema,
                status = "INSTALLED_ENABLED"
            )
            stack.memRepo.upsertTool(tool)

            // 1. Capture rollback snapshot + perform real capability upgrade
            val snapshotJson = ToolFactoryAndEvolutionEngine.buildToolRollbackSnapshot(tool)
            val firstUpgrade = stack.toolFactory.upgradeToolWithRealCapabilities(
                tool = tool,
                upgradeReason = "Add runtime execution contract & telemetry",
                targetVersionOverride = "1.1.0",
                requestedCapabilities = listOf("runtimeExec", "metrics")
            )
            assertTrue(firstUpgrade.isSuccess)
            val upgraded = firstUpgrade.getOrThrow()
            assertEquals("1.1.0", upgraded.version)
            assertTrue(upgraded.mcpSchemaJson.contains("LOCAL_SANDBOX_MCP"))
            assertTrue(upgraded.mcpSchemaJson.contains("metrics"))

            // 2. Calling upgrade again without new capabilities or pending version must be rejected (no fake bumps)
            val noOpAttempt = stack.toolFactory.upgradeToolWithRealCapabilities(
                tool = upgraded,
                upgradeReason = "Nothing new",
                targetVersionOverride = null,
                requestedCapabilities = listOf("runtimeExec", "metrics")
            )
            assertTrue(noOpAttempt.isFailure)

            // 3. Rollback must restore previous version AND exact original schema
            val rolledBack = stack.toolFactory.rollbackTool(
                tool = upgraded,
                rollbackSnapshotJson = snapshotJson,
                reason = "Regression rollback verification"
            )
            assertEquals("1.0.0", rolledBack.version)
            assertEquals(originalSchema, rolledBack.mcpSchemaJson)

            // 4. Failed unsafe upgrade attempt records FAILED in Feature History without altering installed version
            val unsafeUpgrade = stack.toolFactory.upgradeToolWithRealCapabilities(
                tool = rolledBack.copy(description = "Uses DexClassLoader to bypass checks"),
                upgradeReason = "Unsafe dynamic load",
                targetVersionOverride = "2.0.0",
                requestedCapabilities = listOf("unsafeExec")
            )
            assertTrue(unsafeUpgrade.isFailure)
            val history = stack.histRepo.getAllRegistryHistory().filter { it.featureId == "TOOL-NOOP-CHECK" }
            assertTrue(history.any { it.changeEventType == "FAILED" })
        } finally {
            stack.db.close()
        }
    }

    @Test
    fun liveGitHubAndHfDiscoveryEvaluatesBlocksUnsafeAndActivatesSafeMcpTool() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search/repositories") { exchange ->
            val payload = """
                {
                  "items": [
                    {
                      "id": 1001,
                      "full_name": "aura-research/mobile-latency-profiler",
                      "html_url": "https://github.com/aura-research/mobile-latency-profiler",
                      "description": "Lightweight Android latency and prompt profiler with MIT license.",
                      "stargazers_count": 4820,
                      "language": "Kotlin",
                      "license": { "spdx_id": "MIT" },
                      "topics": ["android", "benchmark", "mcp"]
                    },
                    {
                      "id": 1002,
                      "full_name": "unsafe-org/gpl-kernel-hook",
                      "html_url": "https://github.com/unsafe-org/gpl-kernel-hook",
                      "description": "Low-level system overlay hook.",
                      "stargazers_count": 900,
                      "language": "C",
                      "license": { "spdx_id": "GPL-3.0" },
                      "topics": ["hook"]
                    }
                  ]
                }
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/api/models") { exchange ->
            val payload = """
                [
                  {
                    "id": "Qwen/Qwen2.5-1.5B-Instruct-ONNX",
                    "downloads": 120000,
                    "likes": 850,
                    "pipeline_tag": "text-generation",
                    "tags": ["apache-2.0", "onnx", "quantized", "1.5b"],
                    "cardData": { "license": "apache-2.0" }
                  },
                  {
                    "id": "meta-llama/Llama-3-70B-FP16",
                    "downloads": 95000,
                    "likes": 2400,
                    "pipeline_tag": "text-generation",
                    "tags": ["llama3", "70b"],
                    "cardData": { "license": "llama3" }
                  }
                ]
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val stack = buildStack(
            networkOnline = true,
            gitHubApiBaseUrl = baseUrl,
            huggingFaceApiBaseUrl = baseUrl
        )
        try {
            val report = stack.engine.runAutonomousCycle(
                focusTopic = "latency profiler",
                prefs = AuraPreferences(zeroQuestionAutonomyEnabled = true),
                gitHubConfig = GitHubConfig()
            )
            val discoveries = stack.memRepo.researchProjects.first()
            assertTrue(discoveries.isNotEmpty())
            val mitRepo = discoveries.firstOrNull { it.name.contains("mobile-latency-profiler") }
            val gplRepo = discoveries.firstOrNull { it.name.contains("gpl-kernel-hook") }
            assertNotNull(mitRepo)
            assertNotNull(gplRepo)
            assertEquals("BLOCKED", gplRepo!!.installationStatus)
            assertTrue(mitRepo!!.installationStatus in setOf("ADAPTED_TO_MCP", "SAFE_FOR_MCP"))

            val enabledTools = stack.memRepo.getAllToolsOnce().filter { it.status == "INSTALLED_ENABLED" }
            assertTrue(enabledTools.any { it.name.contains("mobile-latency", ignoreCase = true) || it.name.contains("Profiler", ignoreCase = true) })
            assertTrue(enabledTools.all { it.testResults.contains("PASSED", ignoreCase = true) || it.testResults.contains("Healthy", ignoreCase = true) || it.testResults.contains("Verified", ignoreCase = true) || it.testResults.contains("ROLLED_BACK", ignoreCase = true) })
            assertTrue(report.discoveredCandidates.size >= 4)
        } finally {
            server.stop(0)
            stack.db.close()
        }
    }

    @Test
    fun apkSelfUpdaterVerifiesSha256AndGitHubDiagnosticsReportsExactBlockers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updater = ApkSelfUpdater(context)
        val tempFile = File.createTempFile("aura_integrity_test", ".apk", context.cacheDir)
        try {
            tempFile.writeText("not-a-real-apk-archive-payload")
            val actualSha = ApkSelfUpdater.computeSha256Hex(tempFile)
            assertEquals(64, actualSha.length)

            val mismatch = updater.verifyApkIntegrity(
                apkFile = tempFile,
                expectedSha256 = "0".repeat(64)
            )
            assertTrue(mismatch.isFailure)
            assertTrue(mismatch.exceptionOrNull()?.message.orEmpty().contains("SHA-256"))

            val invalidArchive = updater.verifyApkIntegrity(
                apkFile = tempFile,
                expectedSha256 = actualSha
            )
            assertTrue(invalidArchive.isFailure)
            assertTrue(invalidArchive.exceptionOrNull()?.message.orEmpty().contains("Invalid APK package archive"))

            val blockers = GitHubSetupDiagnostics.inspect(
                config = GitHubConfig(owner = "", repo = "", branch = "main", token = ""),
                canRequestPackageInstalls = false,
                latestRelease = GitHubReleaseInfo(
                    tagName = "v1.0.99",
                    name = "v1.0.99",
                    body = "",
                    htmlUrl = "",
                    apkAsset = null
                )
            )
            assertTrue(blockers.any { it.contains("owner", ignoreCase = true) })
            assertTrue(blockers.any { it.contains("repository name", ignoreCase = true) })
            assertTrue(blockers.any { it.contains("PAT token", ignoreCase = true) })
            assertTrue(blockers.any { it.contains("REQUEST_INSTALL_PACKAGES") })
            assertTrue(blockers.any { it.contains(".apk asset", ignoreCase = true) })

            val clear = GitHubSetupDiagnostics.inspect(
                config = GitHubConfig(owner = "org", repo = "aura", branch = "aura-auto-dev", token = "ghp_valid"),
                canRequestPackageInstalls = true,
                latestRelease = GitHubReleaseInfo(
                    tagName = "v1.0.99",
                    name = "v1.0.99",
                    body = "",
                    htmlUrl = "",
                    apkAsset = GitHubReleaseAsset(1L, "aura.apk", "https://api", "https://dl", 1234L)
                )
            )
            assertTrue(clear.isEmpty())

            val guide = GitHubSetupDiagnostics.releaseSigningSecretsGuide()
            assertTrue(guide.any { it.secretName == "KEYSTORE_BASE64" })
            assertTrue(guide.any { it.secretName == "STORE_PASSWORD" })
            assertTrue(guide.any { it.secretName == "KEY_PASSWORD" })
            assertTrue(guide.any { it.secretName == "KEY_ALIAS" && it.setupNotes.contains("upload") })

            val ciSecretsMissing = GitHubSetupDiagnostics.detectMissingCiSigningSecrets(
                "BLOCKER: Missing required GitHub Actions release signing secrets:\n  - KEYSTORE_BASE64\n  - STORE_PASSWORD (or KEYSTORE_PASSWORD)\n  - KEY_ALIAS"
            )
            assertEquals(
                listOf("KEYSTORE_BASE64", "STORE_PASSWORD", "KEYSTORE_PASSWORD", "KEY_ALIAS"),
                ciSecretsMissing
            )
            assertTrue(GitHubSetupDiagnostics.detectMissingCiSigningSecrets("Kotlin compilation failed on line 42").isEmpty())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun startupTriggersAutonomousCycleAndSurfacesSetupBlockersInViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AuraDatabase.closeAndResetForTesting(app)
        val store = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[AuraViewModel::class.java]
            val deadline = System.currentTimeMillis() + 20_000L
            while (System.currentTimeMillis() < deadline &&
                (!vm.startupAutonomousCycleTriggered.value || vm.lastAutonomousReport.value == null)
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50L)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(vm.startupAutonomousCycleTriggered.value)
            val cycleReport = vm.lastAutonomousReport.value
            assertNotNull(cycleReport)
            assertTrue(cycleReport!!.periodicScheduleEnabled)
            assertTrue(cycleReport.testVerificationSummary.contains("Runtime & Regression Checks"))
            assertTrue(cycleReport.realChangesSummary.isNotEmpty())
            assertTrue(vm.selfUpdateState.value.setupBlockers.isNotEmpty())
            assertTrue(
                vm.selfUpdateState.value.setupBlockers.any {
                    it.contains("GitHub repository owner") || it.contains("PAT token")
                }
            )
            val scheduled = WorkManager.getInstance(app)
                .getWorkInfosForUniqueWork("aura_self_evolution_periodic")
                .get()
            assertTrue(scheduled.isNotEmpty())
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching { WorkManager.getInstance(app).cancelAllWork() }
            AuraDatabase.closeAndResetForTesting(app)
        }
    }
}

