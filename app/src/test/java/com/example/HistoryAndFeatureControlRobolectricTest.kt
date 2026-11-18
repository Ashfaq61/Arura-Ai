package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.ai.ModelRoutingEngine
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.FeatureVoiceCommandParser
import com.example.aura.history.HistoryDomain
import com.example.aura.history.HistorySearchAndExportManager
import com.example.aura.history.HistorySearchFilter
import com.example.aura.history.QaExchangeTurn
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.EncryptedBackupManager
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
class HistoryAndFeatureControlRobolectricTest {

    private fun buildInMemoryDb(context: Context): AuraDatabase {
        return Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun buildStack(context: Context, db: AuraDatabase): Triple<AgentHistoryRepository, AgentHistoryRecorder, FeatureControlPipelineEngine> {
        val memRepo = MemoryRepository(db)
        val histRepo = AgentHistoryRepository(db)
        val recorder = AgentHistoryRecorder(histRepo, PrivacyDataRouter())
        val agentNet = AgentNetworkManager()
        val permGuard = PermissionGuard()
        val devCtrl = DeviceControlEngine(context)
        val prefsRepo = PreferencesRepository(context)
        val router = MultiProviderRouter(
            vault = AuraKeystoreVault(context),
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
        return Triple(histRepo, recorder, pipeline)
    }

    @Test
    fun conversationRecorder_redactsSecrets_andRespectsPrivateMode() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(ctx)
        val (histRepo, recorder, _) = buildStack(ctx, db)

        val secretKey = "AIzaSyD-ExampleSecretKey1234567890ABCD"
        val ghPat = "ghp_1234567890abcdefghijklmnop"
        recorder.recordConversation(
            agentId = "1",
            agentName = "Main Brain / Orchestrator Agent",
            participatingAgents = listOf("Main Brain / Orchestrator Agent", "Security Agent"),
            channelType = "BRAIN_TO_AGENT",
            topic = "Key Handling Check",
            userOriginalRequest = "Use key $secretKey and token $ghPat password=mysecretpass",
            qaTurns = listOf(
                QaExchangeTurn("Main Brain", "Security Agent", "Check $secretKey", "Redacted cleanly with $ghPat")
            ),
            finalOutcome = "SUCCESS",
            prefs = AuraPreferences(privateMode = false, redactSensitiveData = true)
        )

        recorder.recordConversation(
            agentId = "1",
            agentName = "Main Brain / Orchestrator Agent",
            participatingAgents = listOf("Main Brain / Orchestrator Agent"),
            channelType = "EXTERNAL_PROVIDER",
            topic = "Private Chat",
            userOriginalRequest = "My confidential salary note",
            qaTurns = emptyList(),
            finalOutcome = "SUCCESS",
            prefs = AuraPreferences(privateMode = true)
        )

        val logs = histRepo.getRecentConversations()
        assertEquals(2, logs.size)
        val first = logs.first { it.topic == "Key Handling Check" }
        assertFalse(first.userOriginalRequest.contains(secretKey))
        assertFalse(first.userOriginalRequest.contains(ghPat))
        assertFalse(first.userOriginalRequest.contains("mysecretpass"))
        assertFalse(first.qaExchangedJson.contains(secretKey))
        assertFalse(first.qaExchangedJson.contains(ghPat))
        assertTrue(first.sensitivityRedacted)

        val priv = logs.first { it.topic == "Private Chat" }
        assertFalse(priv.userOriginalRequest.contains("confidential salary"))
        assertTrue(priv.userOriginalRequest.contains("Private Mode"))
        db.close()
    }

    @Test
    fun featureRequestPipeline_16Steps_safeAutoActivates_externalRequiresApproval_andFailuresPreserved() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(ctx)
        val (histRepo, recorder, pipeline) = buildStack(ctx, db)
        histRepo.seedInitialBaselineIfEmpty()

        // 1. Safe reversible local MCP tool -> should reach stage 16 and AUTO_ACTIVATE
        val safeReq = pipeline.submitFeatureRequest(
            userPrompt = "Build a daily markdown study flashcard summarizer",
            requestMode = "NEW_TOOL",
            prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY")
        )
        assertTrue(safeReq.requestId.startsWith("REQ-"))
        assertEquals(16, safeReq.currentStage)
        assertEquals("COMPLETED_ACTIVATED", safeReq.status)

        val regHistory = histRepo.getAllRegistryHistory()
        assertTrue(regHistory.any { it.featureId == safeReq.resultFeatureId && it.activationStatus == "ACTIVE" })

        // 2. External GitHub repo -> must NOT be falsely marked installed/active; waits for approval
        val ghReq = pipeline.submitFeatureRequest(
            userPrompt = "Integrate https://github.com/modelcontextprotocol/servers MCP reader",
            requestMode = "GITHUB_REPO",
            prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY")
        )
        assertEquals("AWAITING_APPROVAL", ghReq.status)
        val ghReg = histRepo.getAllRegistryHistory().first { it.featureId == ghReq.resultFeatureId }
        assertEquals("DEVELOPMENT_REQUIRED", ghReg.activationStatus)
        assertFalse(ghReg.activationStatus == "ACTIVE")

        // 3. Failed request -> preserved for retry/continuation with exact failure reason
        val failedReq = pipeline.submitFeatureRequest(
            userPrompt = "Build unstable connector",
            requestMode = "NEW_TOOL",
            prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY"),
            forceFailSimulationForTest = "Simulated network dependency timeout at Stage 4"
        )
        assertEquals("FAILED", failedReq.status)
        assertTrue(failedReq.failureReason.contains("timeout"))

        // Retry preserved request
        val retried = pipeline.retryOrContinueFailedRequest(failedReq.requestId, AuraPreferences(intelligenceMode = "LOCAL_ONLY"))
        assertNotNull(retried)
        assertEquals("COMPLETED_ACTIVATED", retried!!.status)

        // 4. Create custom specialist agent
        val agentReq = pipeline.submitFeatureRequest(
            userPrompt = "Create Fitness Coach specialist agent",
            requestMode = "CREATE_AGENT",
            prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY")
        )
        assertEquals("COMPLETED_ACTIVATED", agentReq.status)
        assertTrue(histRepo.getCustomAgents().any { it.name.contains("Fitness Coach", ignoreCase = true) })

        db.close()
    }

    @Test
    fun discoveryInbox_andApprovalRollbackCenter_workEndToEnd() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(ctx)
        val (histRepo, _, pipeline) = buildStack(ctx, db)
        histRepo.seedInitialBaselineIfEmpty()

        val inbox = histRepo.getAllRecommendations()
        assertEquals(3, inbox.size)

        // Ask agents more info
        val afterAsk = pipeline.askAgentsMoreInfo("rec_offline_summarizer_mcp", "How much RAM does this use?", AuraPreferences(intelligenceMode = "LOCAL_ONLY"))
        assertNotNull(afterAsk)
        assertTrue(afterAsk!!.agentFollowUpNotes.contains("How much RAM"))

        // Compare alternatives
        val afterComp = pipeline.compareAlternatives("rec_offline_summarizer_mcp", AuraPreferences(intelligenceMode = "LOCAL_ONLY"))
        assertNotNull(afterComp)
        assertTrue(afterComp!!.agentFollowUpNotes.contains("Alternatives"))

        // 1-click Add This Feature on safe Inbox item
        val addReq = pipeline.addFeatureFromRecommendation("rec_offline_summarizer_mcp", AuraPreferences(intelligenceMode = "LOCAL_ONLY"))
        assertNotNull(addReq)
        assertEquals("COMPLETED_ACTIVATED", addReq!!.status)

        // Save for later + Reject actions
        pipeline.saveRecommendationForLater("rec_github_mcp_filesystem_read")
        assertEquals("SAVED_FOR_LATER", histRepo.getRecommendation("rec_github_mcp_filesystem_read")!!.inboxStatus)
        pipeline.rejectRecommendation("rec_huggingface_whisper_tiny", "Too large for current build")
        assertEquals("REJECTED", histRepo.getRecommendation("rec_huggingface_whisper_tiny")!!.inboxStatus)

        // Proposal Approve + Rollback
        val reqNeedApproval = pipeline.submitFeatureRequest(
            userPrompt = "Automate WhatsApp daily digest",
            requestMode = "NEW_TOOL",
            prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY")
        )
        assertEquals("AWAITING_APPROVAL", reqNeedApproval.status)
        val proposal = histRepo.getAllProposals().first { it.requestId == reqNeedApproval.requestId }
        assertEquals("PENDING", proposal.status)

        val approved = pipeline.approveProposal(proposal.id)
        assertEquals("APPROVED", approved!!.status)

        val rolledBack = pipeline.rollbackProposal(proposal.id)
        assertEquals("ROLLED_BACK", rolledBack!!.status)
        assertTrue(histRepo.getAllRegistryHistory().any { it.featureId == proposal.featureId && it.changeEventType == "ROLLED_BACK" })

        db.close()
    }

    @Test
    fun hinglishVoiceCommands_andIndividualAgentProfile_andSearchExport_andBackup_work() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(ctx)
        val (histRepo, recorder, pipeline) = buildStack(ctx, db)
        val advRepo = AdvancedRepository(db)
        val memRepo = MemoryRepository(db)
        histRepo.seedInitialBaselineIfEmpty()

        // Voice command parser (Req #4)
        assertEquals("NATURAL", FeatureVoiceCommandParser.parse("AURA, ye feature bhi add karo")?.requestMode)
        assertEquals("FROM_RECOMMENDATION", FeatureVoiceCommandParser.parse("AURA, GitHub wale agent ka recommended tool install karo")?.requestMode)
        assertEquals("NEW_TOOL", FeatureVoiceCommandParser.parse("AURA, is feature ko khud develop karo")?.requestMode)
        assertEquals("COMBINE_TOOLS", FeatureVoiceCommandParser.parse("AURA, is tool ko existing automation ke saath connect karo")?.requestMode)
        assertEquals("IMPROVE_FEATURE", FeatureVoiceCommandParser.parse("AURA, is feature ka advanced version bana do")?.requestMode)

        // Run a feature request so search/export has rich cross-domain data
        pipeline.submitFeatureRequest("Build CSV flashcards helper", "NEW_TOOL", prefs = AuraPreferences(intelligenceMode = "LOCAL_ONLY"))

        val searchMgr = HistorySearchAndExportManager(ctx, PrivacyDataRouter())
        val hits = searchMgr.search(
            filter = HistorySearchFilter(query = "flashcards"),
            conversations = histRepo.getRecentConversations(),
            research = emptyList(),
            requests = histRepo.getAllFeatureRequests(),
            recommendations = histRepo.getAllRecommendations(),
            registry = histRepo.getAllRegistryHistory(),
            proposals = histRepo.getAllProposals(),
            activities = histRepo.getRecentActivities(),
            evolutions = emptyList(),
            audits = emptyList()
        )
        assertTrue(hits.isNotEmpty())

        val jsonExport = searchMgr.exportToJson(hits).getOrThrow()
        assertTrue(jsonExport.file.exists())
        assertTrue(jsonExport.file.readText().contains("credentialsIncluded\": false"))

        val csvExport = searchMgr.exportToCsv(hits).getOrThrow()
        assertTrue(csvExport.file.exists())
        assertTrue(csvExport.file.readText().startsWith("id,domain,timestamp_iso"))

        val pdfExport = searchMgr.exportToPdf(hits).getOrThrow()
        assertTrue(pdfExport.file.exists())
        val pdfBytes = pdfExport.file.readBytes()
        assertTrue(pdfBytes.size > 100)
        assertEquals("%PDF-", String(pdfBytes.copyOfRange(0, 5)))

        // Individual Agent Profile aggregation
        val agent = AgentNetworkManager().agents.value.first { it.name.contains("Tool Factory") }
        val profile = recorder.buildAgentProfileAggregate(
            agent = agent,
            allTasks = emptyList(),
            allConversations = histRepo.getRecentConversations(),
            allResearch = emptyList(),
            allRecommendations = histRepo.getAllRecommendations(),
            allRegistry = histRepo.getAllRegistryHistory(),
            allRequests = histRepo.getAllFeatureRequests(),
            allActivities = histRepo.getRecentActivities()
        )
        assertTrue(profile.conversations.isNotEmpty() || profile.relatedRequests.isNotEmpty())

        // Encrypted Backup includes v4 history records and zero secrets
        val backupMgr = EncryptedBackupManager(memRepo, advRepo, histRepo)
        val enc = backupMgr.createEncryptedBackup("AuraTestPass99!").getOrThrow()
        assertFalse(enc.contains("AIza"))

        histRepo.deleteHistoryScope("ALL")
        assertEquals(0, histRepo.getRecentConversations().size)

        val restored = backupMgr.restoreFromEncryptedBackup("AuraTestPass99!", enc).getOrThrow()
        assertTrue(restored.agentHistoryRecordsRestored > 0)
        assertTrue(histRepo.getRecentConversations().isNotEmpty())

        db.close()
    }
}
