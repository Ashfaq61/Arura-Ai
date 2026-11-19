package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.ai.ModelRoutingEngine
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.evolution.ContinuousSelfImprovementEngine
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.github.ApkSelfUpdater
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubReleaseAsset
import com.example.aura.github.GitHubReleaseInfo
import com.example.aura.github.GitHubReleasePair
import com.example.aura.github.GitHubSelfUpdateClient
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZeroQuestionAutonomyRobolectricTest {

    private data class AutonomyTestStack(
        val context: Context,
        val db: AuraDatabase,
        val memRepo: MemoryRepository,
        val histRepo: AgentHistoryRepository,
        val agentNet: AgentNetworkManager,
        val toolFactory: ToolFactoryAndEvolutionEngine,
        val selfHealing: SelfHealingEngine,
        val engine: ContinuousSelfImprovementEngine
    )

    private fun buildStack(
        networkOnline: Boolean = false,
        releaseCheckOverride: (suspend (GitHubConfig) -> Result<GitHubReleasePair>)? = null
    ): AutonomyTestStack {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val memRepo = MemoryRepository(db)
        val histRepo = AgentHistoryRepository(db)
        val recorder = AgentHistoryRecorder(histRepo, PrivacyDataRouter())
        val agentNet = AgentNetworkManager()
        val permGuard = PermissionGuard()
        val devCtrl = DeviceControlEngine(context)
        val vault = AuraKeystoreVault(context)
        val prefsRepo = PreferencesRepository(context)
        val router = MultiProviderRouter(
            vault = vault,
            memoryRepository = memRepo,
            privacyRouter = PrivacyDataRouter(),
            modelRouter = ModelRoutingEngine(),
            preferencesRepository = prefsRepo
        )
        val research = GitHubAndHfResearchClient()
        val toolFactory = ToolFactoryAndEvolutionEngine(router, research, memRepo, agentNet, permGuard, devCtrl)
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
        return AutonomyTestStack(context, db, memRepo, histRepo, agentNet, toolFactory, selfHealing, engine)
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
            assertTrue(synthTool.testResults.contains("PASSED:") && synthTool.testResults.contains("verified", ignoreCase = true))

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

            // Faulty v1.2.0 rolled back cleanly to previousVersion 1.1.0
            val rolled = after.first { it.id == "TOOL-CRASH-V2" }
            assertEquals("1.1.0", rolled.version)
            assertTrue(rolled.testResults.contains("AUTO_ROLLED_BACK"))
            assertEquals("INSTALLED_ENABLED", rolled.status)
            assertTrue(report.bugsFixedOrRolledBack.any { it == "ROLLED_BACK:Experimental Parser" })
            assertEquals("ROLLED_BACK", report.cycleStatus)
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

            // Outdated tool upgraded with rollback snapshot preserved
            val upgradedTool = stack.memRepo.getAllToolsOnce().first { it.id == "TOOL-UPGRADE-01" }
            assertEquals("1.1.0", upgradedTool.version)
            assertEquals("1.0.0", upgradedTool.previousVersion)
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
}
