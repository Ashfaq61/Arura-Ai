package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.example.aura.ai.AiProviderType
import com.example.aura.ai.ModelRoutingEngine
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.backend.CloudDevBackendClient
import com.example.aura.backend.CloudDevTaskSubmission
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.github.ApkSelfUpdater
import com.example.aura.github.DEFAULT_AURA_BRANCH
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubFileDraft
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.github.PendingGitHubPushProposal
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.BacklogPriorityInput
import com.example.aura.history.BacklogPriorityScorer
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.FeatureLifecycleStage
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.CrashReportEntity
import com.example.aura.memory.GlobalAgentNetworkRepository
import com.example.aura.memory.MarketplaceToolEntity
import com.example.aura.memory.MemoryEntryEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.network.AgentConnectionStatus
import com.example.aura.network.AgentTrustSecurityGuard
import com.example.aura.network.GlobalAgentNetworkEngine
import com.example.aura.network.GlobalDiscussionCouncilEngine
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.ApiKeySlot
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.GeminiKeyStore
import com.example.aura.security.GitHubKeyStore
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender
import com.example.aura.ui.AuraViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Aura21EcosystemRobolectricTest {

    private fun testOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    @Test
    fun multiProviderAdaptersRouteDiscoverRetryAndFallbackAcrossRealHttpServers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val memRepo = MemoryRepository(db)
        val vault = AuraKeystoreVault(context)
        val geminiStore = GeminiKeyStore(context)
        val prefsRepo = PreferencesRepository(context)
        val http = testOkHttpClient()

        val claudeAttemptHeaderOk = AtomicInteger(0)
        val openAiRetryAttempts = AtomicInteger(0)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // 1. Anthropic Claude Messages & Models
        server.createContext("/v1/messages") { ex ->
            val apiKeyHeader = ex.requestHeaders.getFirst("x-api-key")
            val versionHeader = ex.requestHeaders.getFirst("anthropic-version")
            if (apiKeyHeader == "sk-ant-test-key" && versionHeader == "2023-06-01") {
                claudeAttemptHeaderOk.incrementAndGet()
                val body = """{"content":[{"type":"text","text":"Claude verified response."}]}""".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } else {
                val err = """{"error":{"message":"Missing anthropic headers"}}""".toByteArray()
                ex.sendResponseHeaders(401, err.size.toLong())
                ex.responseBody.use { it.write(err) }
            }
        }
        server.createContext("/v1/models") { ex ->
            val body = """{"data":[{"id":"claude-3-7-sonnet-latest"},{"id":"gpt-4o-live-discovered"}]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        // 2. Ollama Chat & Tags
        server.createContext("/api/chat") { ex ->
            val body = """{"message":{"role":"assistant","content":"Ollama keyless local reply"}}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/tags") { ex ->
            val body = """{"models":[{"name":"llama3.2:3b"},{"name":"qwen2.5:7b-instruct"}]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        // 3. OpenAI-compatible 503 transient retry -> 200 success
        server.createContext("/openai-retry/v1/chat/completions") { ex ->
            val attempt = openAiRetryAttempts.incrementAndGet()
            if (attempt == 1) {
                val err = """{"error":"transient overload"}""".toByteArray()
                ex.sendResponseHeaders(503, err.size.toLong())
                ex.responseBody.use { it.write(err) }
            } else {
                val ok = """{"choices":[{"message":{"content":"Recovered after 503 retry"}}]}""".toByteArray()
                ex.sendResponseHeaders(200, ok.size.toLong())
                ex.responseBody.use { it.write(ok) }
            }
        }
        // 4. Primary endpoint returning 429 to verify fast-fail fallback to healthy backup provider
        server.createContext("/rate-limited/chat/completions") { ex ->
            val err = """{"error":"HTTP 429 rate limited"}""".toByteArray()
            ex.sendResponseHeaders(429, err.size.toLong())
            ex.responseBody.use { it.write(err) }
        }
        // 5. MCP JSON-RPC (tools/list + tools/call)
        server.createContext("/mcp") { ex ->
            val reqBody = ex.requestBody.bufferedReader().readText().replace("\\/", "/")
            val respJson = if (reqBody.contains("tools/list")) {
                """{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"code_audit_mcp"},{"name":"log_tail_mcp"}]}}"""
            } else {
                """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"MCP tool executed cleanly"}]}}"""
            }
            val bytes = respJson.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        // 6. A2A Agent Card + tasks/send
        server.createContext("/.well-known/agent.json") { ex ->
            val bytes = """{"name":"KotlinArchitectA2A","skills":[{"name":"refactor_compose"},{"name":"audit_room"}]}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/a2a") { ex ->
            val bytes = """{"jsonrpc":"2.0","id":3,"result":{"status":{"state":"completed","message":{"role":"agent","parts":[{"type":"text","text":"A2A architect reviewed diff"}]}}}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            // Save slots across providers (including keyless Ollama, MCP, A2A!)
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_claude", providerId = AiProviderType.CLAUDE.id, label = "Claude", customBaseUrl = "$base/v1/messages", preferredModel = "claude-3-7-sonnet-latest"), "sk-ant-test-key")
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_ollama", providerId = AiProviderType.OLLAMA.id, label = "Local Ollama", customBaseUrl = "$base/api/chat", preferredModel = "llama3.2:3b"), "")
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_openai", providerId = AiProviderType.OPENAI_COMPATIBLE.id, label = "OpenAI Retry", customBaseUrl = "$base/openai-retry/v1/chat/completions", preferredModel = "gpt-4o-mini"), "sk-openai-test-123")
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_groq", providerId = AiProviderType.GROQ.id, label = "RateLimited Groq", customBaseUrl = "$base/rate-limited/chat/completions", preferredModel = "llama-3.3-70b-versatile"), "gsk_test_key_429")
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_mcp", providerId = AiProviderType.MCP_SERVER.id, label = "Local MCP", customBaseUrl = "$base/mcp", preferredModel = "code_audit_mcp"), "")
            vault.saveApiKeySlot(ApiKeySlot(id = "slot_a2a", providerId = AiProviderType.A2A_AGENT.id, label = "A2A Peer", customBaseUrl = "$base/a2a", preferredModel = "refactor_compose"), "")

            val router = MultiProviderRouter(
                vault = vault,
                memoryRepository = memRepo,
                privacyRouter = PrivacyDataRouter(),
                modelRouter = ModelRoutingEngine(),
                geminiKeyStore = geminiStore,
                preferencesRepository = prefsRepo,
                httpClientOverride = http
            )

            // Verify configured providers includes keyless Ollama/MCP/A2A
            val configured = router.getConfiguredExternalProviders()
            assertTrue(configured.contains(AiProviderType.CLAUDE))
            assertTrue(configured.contains(AiProviderType.OLLAMA))
            assertTrue(configured.contains(AiProviderType.MCP_SERVER))
            assertTrue(configured.contains(AiProviderType.A2A_AGENT))

            // Claude Anthropic headers & response
            val claudeRes = router.generateOnce("Review Kotlin code", providerId = AiProviderType.CLAUDE.id, isNetworkAvailable = true)
            assertTrue(claudeRes.isSuccess)
            assertEquals(1, claudeAttemptHeaderOk.get())
            assertTrue(claudeRes.getOrThrow().text.contains("Claude verified response"))

            // Keyless Ollama chat & live tag discovery
            val ollamaRes = router.generateOnce("Explain MVVM", providerId = AiProviderType.OLLAMA.id, isNetworkAvailable = true)
            assertTrue(ollamaRes.isSuccess)
            assertTrue(ollamaRes.getOrThrow().text.contains("Ollama keyless local reply"))
            val discoveredOllama = router.discoverModelsForProvider(AiProviderType.OLLAMA, "slot_ollama").getOrThrow()
            assertTrue(discoveredOllama.contains("qwen2.5:7b-instruct"))

            // OpenAI-compat 503 transient retry recovery
            val retryRes = router.generateOnce("Test retry", providerId = AiProviderType.OPENAI_COMPATIBLE.id, isNetworkAvailable = true)
            assertTrue(retryRes.isSuccess)
            assertEquals(2, openAiRetryAttempts.get())
            assertTrue(retryRes.getOrThrow().text.contains("Recovered after 503 retry"))

            // Groq 429 triggers automatic fallback to healthy configured provider
            val fallbackRes = router.generateOnce("Fallback test", providerId = AiProviderType.GROQ.id, isNetworkAvailable = true)
            assertTrue(fallbackRes.isSuccess)
            assertTrue(fallbackRes.getOrThrow().usedFallback)

            // MCP discovery + tool dispatch
            val discoveredMcp = router.discoverModelsForProvider(AiProviderType.MCP_SERVER, "slot_mcp").getOrThrow()
            assertTrue(discoveredMcp.contains("code_audit_mcp"))
            val mcpExec = router.generateOnce("Run code audit", providerId = AiProviderType.MCP_SERVER.id, isNetworkAvailable = true).getOrThrow()
            assertTrue(mcpExec.text.contains("MCP tool executed cleanly"))

            // A2A agent card discovery + task dispatch
            val discoveredA2a = router.discoverModelsForProvider(AiProviderType.A2A_AGENT, "slot_a2a").getOrThrow()
            assertTrue(discoveredA2a.contains("KotlinArchitectA2A"))
            val a2aExec = router.generateOnce("Audit Compose navigation", providerId = AiProviderType.A2A_AGENT.id, isNetworkAvailable = true).getOrThrow()
            assertTrue(a2aExec.text.contains("A2A architect reviewed diff"))
        } finally {
            server.stop(0)
            db.close()
        }
    }

    @Test
    fun globalAgentNetworkCouncilUsesMultiProviderCritiqueAndStrictProvenanceLabels() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val memRepo = MemoryRepository(db)
        val histRepo = AgentHistoryRepository(db)
        val netRepo = GlobalAgentNetworkRepository(db)
        val vault = AuraKeystoreVault(context)
        val recorder = AgentHistoryRecorder(histRepo, PrivacyDataRouter())
        val http = testOkHttpClient()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { ex ->
            val bytes = """{"content":[{"type":"text","text":"Primary Claude council discovery: Kotlin modular adapter recommended."}]}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/chat") { ex ->
            val bytes = """{"message":{"role":"assistant","content":"Secondary Ollama reviewer challenge: verify RAM usage below 256MB."}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/tags") { ex ->
            val bytes = """{"models":[{"name":"llama3.2:3b"}]}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/mcp") { ex ->
            val raw = ex.requestBody.bufferedReader().readText().replace("\\/", "/")
            val body = if (raw.contains("tools/list")) {
                """{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"repo_scanner"}]}}"""
            } else {
                """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"External MCP peer scan ready."}]}}"""
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"

        try {
            vault.saveApiKeySlot(ApiKeySlot(id = "c_slot", providerId = AiProviderType.CLAUDE.id, label = "Claude", customBaseUrl = "$base/v1/messages", preferredModel = "claude-3-7-sonnet-latest"), "sk-ant-council-123")
            vault.saveApiKeySlot(ApiKeySlot(id = "o_slot", providerId = AiProviderType.OLLAMA.id, label = "Ollama", customBaseUrl = "$base/api/chat", preferredModel = "llama3.2:3b"), "")

            val router = MultiProviderRouter(
                vault = vault,
                memoryRepository = memRepo,
                privacyRouter = PrivacyDataRouter(),
                modelRouter = ModelRoutingEngine(),
                httpClientOverride = http
            )
            val trustGuard = AgentTrustSecurityGuard(vault, PromptInjectionDefender(), PrivacyDataRouter(), memRepo)
            val gateway = BackendServiceGateway(context, http)
            val networkEngine = GlobalAgentNetworkEngine(
                networkRepo = netRepo,
                historyRecorder = recorder,
                trustGuard = trustGuard,
                backendGateway = gateway,
                memoryRepo = memRepo,
                aiRouter = router,
                okHttpClient = http
            )
            networkEngine.initializeNodes()

            // Verify internal node preserves honest INTERNAL_LOCAL label
            val localDispatch = networkEngine.sendMessageToAgent(
                nodeId = "node_local_orchestrator",
                topic = "Architecture",
                message = "Check internal provenance",
                isNetworkOnline = true
            )
            assertTrue(localDispatch.delivered)
            assertTrue(localDispatch.responseText.contains("INTERNAL_LOCAL"))

            // Verify external MCP node connects & dispatches over real HTTP JSON-RPC
            val mcpNode = networkEngine.addExternalAgentNode(
                name = "Test MCP Peer",
                category = "MCP",
                providerOrPlatform = "MCP HTTP",
                modelName = "repo_scanner",
                endpointUrl = "$base/mcp",
                bearerToken = "",
                autoVerifyNow = true
            )
            assertEquals(AgentConnectionStatus.CONNECTED, mcpNode.connectionStatus)
            val mcpMsg = networkEngine.sendMessageToAgent(mcpNode.nodeId, "Code Audit", "Inspect PR diff")
            assertTrue(mcpMsg.delivered)
            assertTrue(mcpMsg.responseText.contains("External MCP peer scan ready"))

            // Verify multi-provider council session runs independent primary + challenger providers with strict provenance
            val researchClient = GitHubAndHfResearchClient(client = http)
            val mcpRuntime = com.example.aura.agents.McpRuntimeEngine(context, com.example.aura.agents.AgentNetworkManager(), PermissionGuard(), memRepo, DeviceControlEngine(context), researchClient)
            val toolFactory = com.example.aura.evolution.ToolFactoryAndEvolutionEngine(router, researchClient, memRepo, com.example.aura.agents.AgentNetworkManager(), PermissionGuard(), DeviceControlEngine(context), mcpRuntime)
            val pipeline = FeatureControlPipelineEngine(histRepo, recorder, memRepo, router, com.example.aura.agents.AgentNetworkManager(), PermissionGuard(), DeviceControlEngine(context), toolFactory, researchClient)
            val councilEngine = GlobalDiscussionCouncilEngine(
                context = context,
                networkRepo = netRepo,
                historyRepo = histRepo,
                memoryRepo = memRepo,
                historyRecorder = recorder,
                networkEngine = networkEngine,
                pipelineEngine = pipeline,
                trustGuard = trustGuard,
                researchClient = researchClient,
                deviceControl = DeviceControlEngine(context),
                aiRouter = router
            )

            val session = councilEngine.startCouncilDiscussion(
                topic = "Compose State Stability",
                topicCategory = "ANDROID_AUTOMATION",
                question = "How should AURA optimize list recompositions?",
                prefs = AuraPreferences(intelligenceMode = "HYBRID", activeModelId = "gemini-2.5-flash")
            )
            val turns = netRepo.getTurnsForSession(session.sessionId)
            val discoveryTurn = turns.first { it.stance == "DISCOVERY" }
            val challengeTurn = turns.first { it.stance == "CHALLENGE" }
            assertTrue(discoveryTurn.providerOrPlatform.startsWith("External HTTP"))
            assertTrue(discoveryTurn.messageText.contains("Primary Claude council discovery"))
            assertTrue(challengeTurn.providerOrPlatform.startsWith("External HTTP"))
            assertTrue(challengeTurn.messageText.contains("Secondary Ollama reviewer challenge"))
        } finally {
            server.stop(0)
            db.close()
        }
    }

    @Test
    fun researchEngineInspectsReposModelsDocsAndDeduplicatesVersions() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/aura/archived-lib") { ex ->
            val bytes = """{"full_name":"aura/archived-lib","stargazers_count":340,"archived":true,"disabled":false,"default_branch":"main","open_issues_count":12,"language":"Kotlin","license":{"spdx_id":"MIT"}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/aura/archived-lib/releases/latest") { ex ->
            val bytes = """{"tag_name":"v1.4.2"}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/models/Qwen/Qwen2.5-70B-Instruct") { ex ->
            val bytes = """{"id":"Qwen/Qwen2.5-70B-Instruct","pipeline_tag":"text-generation","downloads":250000,"likes":1800,"cardData":{"license":"apache-2.0"}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/s/results") { ex ->
            val bytes = "<html><body><h1>Jetpack Compose Navigation</h1><p>Type-safe Kotlin Serialization navigation routes.</p></body></html>".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            val client = GitHubAndHfResearchClient(
                client = testOkHttpClient(),
                gitHubApiBaseUrl = base,
                huggingFaceApiBaseUrl = base,
                androidDocsBaseUrl = base
            )
            val repoDetails = client.inspectGitHubRepoDetails("aura/archived-lib").getOrThrow()
            assertTrue(repoDetails.archived)
            assertEquals("v1.4.2", repoDetails.latestReleaseTag)
            assertTrue(repoDetails.compatibilityNote.contains("BLOCKED"))

            val hfModel = client.inspectHuggingFaceModel("Qwen/Qwen2.5-70B-Instruct", deviceRamMb = 4096).getOrThrow()
            assertFalse(hfModel.fitsDeviceRam)
            assertTrue(hfModel.note.contains("cloud HF Inference API"))

            val docs = client.searchAndroidDeveloperDocs("Compose Navigation").getOrThrow()
            assertEquals(1, docs.size)
            assertEquals("ANDROID_DOCS", docs.first().sourcePlatform)
            assertTrue(docs.first().description.contains("Jetpack Compose Navigation"))

            val tracked = ResearchProjectEntity(
                id = "gh_1",
                sourcePlatform = "GITHUB",
                name = "aura/profiler",
                description = "Profiler",
                url = "https://github.com/aura/profiler",
                latestReleaseOrUpdate = "1.0.0",
                license = "MIT",
                compatibility = "Android",
                dependencies = "",
                securityAssessment = "SAFE",
                resourceEstimate = "Low",
                installationStatus = "INSTALLED",
                testStatus = "PASSED",
                recommendationReason = ""
            )
            val dedup = client.deduplicateAndCompareVersions(
                candidates = listOf(
                    tracked.copy(id = "gh_1_same", latestReleaseOrUpdate = "1.0.0"),
                    tracked.copy(id = "gh_1_newer", latestReleaseOrUpdate = "1.2.0"),
                    tracked.copy(id = "gh_novel", name = "aura/new-tool", url = "https://github.com/aura/new-tool", latestReleaseOrUpdate = "0.5.0")
                ),
                existingProjects = listOf(tracked),
                existingTools = emptyList()
            )
            assertTrue(dedup[0].isDuplicate)
            assertFalse(dedup[1].isDuplicate)
            assertTrue(dedup[1].hasNewerRelease)
            assertFalse(dedup[2].isDuplicate)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun cloudDevBackendPythonServerAndKotlinClientEnforceIsolationAndApplySafeEdits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempRepo = File(context.cacheDir, "aura_temp_backend_repo_${System.currentTimeMillis()}").apply { mkdirs() }
        val scriptPath = File("tools/cloud_dev_backend.py").takeIf { it.exists() }
            ?: File("../tools/cloud_dev_backend.py")
        assertTrue("Expected tools/cloud_dev_backend.py to exist", scriptPath.exists())

        val freePort = ServerSocket(0).use { it.localPort }
        val proc = ProcessBuilder(
            "python3",
            scriptPath.absolutePath,
            "--port",
            freePort.toString(),
            "--repo-root",
            tempRepo.absolutePath
        ).redirectErrorStream(true).start()

        try {
            val client = CloudDevBackendClient(context, BackendServiceGateway(context, testOkHttpClient()), testOkHttpClient())
            val url = "http://127.0.0.1:$freePort"

            // Wait up to 4 seconds for Python HTTP server startup
            var health = client.checkHealth(url)
            var waitCount = 0
            while (!health.reachable && waitCount < 40) {
                Thread.sleep(100)
                health = client.checkHealth(url)
                waitCount++
            }
            assertTrue("Python Cloud Dev Backend should be reachable: ${health.message}", health.reachable)

            // 1. Direct edit on protected 'main' branch must be BLOCKED
            val blockedMain = client.submitDevTask(
                request = CloudDevTaskSubmission(
                    featureName = "Illegal Main Edit",
                    branchName = "main",
                    commitMessage = "should block",
                    edits = listOf(GitHubFileDraft("notes/test.txt", "hello")),
                    runTests = false
                ),
                backendUrl = url
            )
            assertEquals("BLOCKED", blockedMain.status)

            // 2. Credential/keystore file edit must be BLOCKED
            val blockedKeystore = client.submitDevTask(
                request = CloudDevTaskSubmission(
                    featureName = "Illegal Keystore Edit",
                    branchName = DEFAULT_AURA_BRANCH,
                    commitMessage = "should block",
                    edits = listOf(GitHubFileDraft("app/my-upload-key.jks", "fake-bytes")),
                    runTests = false
                ),
                backendUrl = url
            )
            assertEquals("BLOCKED", blockedKeystore.status)

            // 3. Safe isolated branch edit applies cleanly on disk and is queryable via GET /v1/dev-tasks/{id}
            val safeTask = client.submitDevTask(
                request = CloudDevTaskSubmission(
                    featureName = "Safe Helper File",
                    branchName = DEFAULT_AURA_BRANCH,
                    commitMessage = "feat: add helper artifact",
                    edits = listOf(GitHubFileDraft("generated/SampleHelper.kt", "package generated\nval version = 21\n")),
                    runTests = false,
                    applyChanges = true
                ),
                backendUrl = url
            )
            assertEquals("COMPLETED", safeTask.status)
            assertTrue(File(tempRepo, "generated/SampleHelper.kt").readText().contains("val version = 21"))

            val fetched = client.getDevTaskStatus(safeTask.taskId, url).getOrThrow()
            assertEquals("COMPLETED", fetched.status)
            assertTrue(fetched.diffSummary.contains("generated/SampleHelper.kt"))
        } finally {
            proc.destroyForcibly()
            tempRepo.deleteRecursively()
        }
    }

    @Test
    fun gitHubDevLifecycleSupportsBranchPrReviewDispatchAndBlocksUnsafeTargets() = runTest {
        val postedPr = AtomicInteger(0)
        val dispatchedWorkflow = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/aura-org/aura-ai") { ex ->
            if (ex.requestURI.path == "/repos/aura-org/aura-ai") {
                val bytes = """{"full_name":"aura-org/aura-ai","default_branch":"main"}""".toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            } else {
                val err = """{"message":"Not Found"}""".toByteArray()
                ex.sendResponseHeaders(404, err.size.toLong())
                ex.responseBody.use { it.write(err) }
            }
        }
        server.createContext("/repos/aura-org/aura-ai/git/ref/heads/aura-changes") { ex ->
            val err = """{"message":"Not Found"}""".toByteArray()
            ex.sendResponseHeaders(404, err.size.toLong())
            ex.responseBody.use { it.write(err) }
        }
        server.createContext("/repos/aura-org/aura-ai/git/ref/heads/main") { ex ->
            val bytes = """{"ref":"refs/heads/main","object":{"sha":"deadbeef1234"}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/aura-org/aura-ai/git/refs") { ex ->
            val bytes = """{"ref":"refs/heads/aura-changes","object":{"sha":"deadbeef1234"}}""".toByteArray()
            ex.sendResponseHeaders(201, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/aura-org/aura-ai/contents/docs/Note.md") { ex ->
            val bytes = """{"commit":{"sha":"c0ffee99","html_url":"https://github.com/aura-org/aura-ai/commit/c0ffee99"}}""".toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/aura-org/aura-ai/pulls") { ex ->
            if (ex.requestMethod.equals("POST", true)) {
                postedPr.incrementAndGet()
                val bytes = """{"number":77,"title":"AURA PR","state":"open","head":{"ref":"aura-changes"},"base":{"ref":"main"},"html_url":"https://github.com/aura-org/aura-ai/pull/77"}""".toByteArray()
                ex.sendResponseHeaders(201, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            } else {
                val bytes = """[{"number":77,"title":"AURA PR","state":"open","head":{"ref":"aura-changes"},"base":{"ref":"main"},"html_url":"https://github.com/aura-org/aura-ai/pull/77"}]""".toByteArray()
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
        server.createContext("/repos/aura-org/aura-ai/issues/77/comments") { ex ->
            val bytes = """{"html_url":"https://github.com/aura-org/aura-ai/pull/77#issuecomment-1"}""".toByteArray()
            ex.sendResponseHeaders(201, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/repos/aura-org/aura-ai/actions/workflows/android-ci.yml/dispatches") { ex ->
            dispatchedWorkflow.incrementAndGet()
            ex.sendResponseHeaders(204, -1)
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            val client = GitHubSelfUpdateClient(testOkHttpClient(), baseUrl = base)
            val cfg = GitHubConfig(owner = "aura-org", repo = "aura-ai", branch = "aura-changes", token = "ghp_valid_pat_123456")

            assertTrue(client.ensureBranchExists(cfg, "aura-changes").isSuccess)
            val pushRes = client.pushApprovedProposal(
                cfg,
                PendingGitHubPushProposal(
                    branch = "aura-changes",
                    commitMessage = "docs: add note",
                    explanation = "Test push",
                    files = listOf(GitHubFileDraft("docs/Note.md", "# Updated"))
                )
            ).getOrThrow()
            assertEquals("c0ffee99", pushRes.commitSha)

            // Unsafe push targets must remain strictly blocked
            val blockedMain = client.pushApprovedProposal(
                cfg,
                PendingGitHubPushProposal("main", "illegal", "", listOf(GitHubFileDraft("docs/Note.md", "x")))
            )
            assertTrue(blockedMain.isFailure)
            val blockedJks = client.pushApprovedProposal(
                cfg,
                PendingGitHubPushProposal("aura-changes", "illegal", "", listOf(GitHubFileDraft("release.jks", "secret")))
            )
            assertTrue(blockedJks.isFailure)

            // PR creation, listing, comment, dispatch, and changelog generation
            val pr = client.createPullRequest(cfg, "AURA PR", "Changelog body", "aura-changes").getOrThrow()
            assertEquals(77, pr.number)
            assertEquals(1, postedPr.get())
            assertEquals(1, client.listOpenPullRequests(cfg).getOrThrow().size)
            val commentUrl = client.postPullRequestReviewComment(cfg, 77, "Tests passed 100%").getOrThrow()
            assertTrue(commentUrl.contains("pull/77"))
            assertTrue(client.triggerWorkflowDispatch(cfg).isSuccess)
            assertEquals(1, dispatchedWorkflow.get())
            val changelog = client.generateChangelogMarkdown("aura-changes", "AURA 2.1", listOf("docs/Note.md"), "All passed")
            assertTrue(changelog.contains("AURA 2.1") && changelog.contains("docs/Note.md"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun backlogPriorityScorerRanksSafeCandidatesAndDoesNotBlockOnBlockedGplItems() {
        val ranked = BacklogPriorityScorer.rankBacklog(
            listOf(
                BacklogPriorityInput(
                    featureId = "gpl_blocker",
                    featureName = "GPL Kernel Hook",
                    sourcePlatform = "GITHUB",
                    userBenefit = 10,
                    security = 2,
                    reliability = 5,
                    compatibility = 3,
                    performance = 6,
                    cost = 2,
                    effort = 8,
                    isBlocked = true,
                    blockReason = "GPL-3.0 license conflict"
                ),
                BacklogPriorityInput(
                    featureId = "safe_formatter",
                    featureName = "Safe JSON Formatter",
                    sourcePlatform = "AURA_CORE",
                    userBenefit = 9,
                    security = 10,
                    reliability = 9,
                    compatibility = 10,
                    performance = 9,
                    cost = 1,
                    effort = 2,
                    stage = FeatureLifecycleStage.READY
                ),
                BacklogPriorityInput(
                    featureId = "low_priority_doc",
                    featureName = "Minor Doc Hint",
                    sourcePlatform = "ANDROID_DOCS",
                    userBenefit = 4,
                    security = 9,
                    reliability = 7,
                    compatibility = 8,
                    performance = 7,
                    cost = 1,
                    effort = 3,
                    stage = FeatureLifecycleStage.DISCOVERED
                )
            )
        )
        assertEquals("safe_formatter", ranked[0].input.featureId)
        assertEquals("P0_CRITICAL", ranked[0].priorityTier)
        assertEquals("low_priority_doc", ranked[1].input.featureId)
        assertEquals("gpl_blocker", ranked[2].input.featureId)
        assertEquals(FeatureLifecycleStage.BLOCKED, ranked[2].stage)
        assertEquals("BLOCKED", ranked[2].priorityTier)
    }

    @Test
    fun startupIntegrityVerificationDetectsCrashesAndPreservesUserRecords() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AuraDatabase.closeAndResetForTesting(app)
        val gitHubKeyStore = GitHubKeyStore(app)
        gitHubKeyStore.saveLastVerifiedVersionCode(1L)
        gitHubKeyStore.saveLastWorkingVersion("v1.0.1")

        val db = AuraDatabase.getInstance(app)
        val memRepo = MemoryRepository(db)
        memRepo.saveMemory(MemoryEntryEntity(category = "PREFERENCE", title = "UserThemeNote", content = "Keep dark cobalt workspace intact"))
        memRepo.upsertTool(
            MarketplaceToolEntity(
                id = "USER-TOOL-PRESERVED",
                name = "User Custom Formatter",
                category = "Utility",
                version = "1.0.0",
                source = "USER",
                license = "MIT",
                description = "Preserved across updates",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "Android 8+",
                securityScan = "PASSED",
                testResults = "Verified",
                status = "INSTALLED_ENABLED"
            )
        )
        db.crashReportDao().insert(
            CrashReportEntity(
                threadName = "main",
                exceptionType = "IllegalStateException",
                message = "Simulated prior crash for recovery test",
                stackTrace = "at com.example.aura.Sample(Sample.kt:10)",
                resolved = false
            )
        )

        val selfHealing = SelfHealingEngine(app)
        val (healthy, msg) = selfHealing.diagnoseStartupHealth(dbReadable = true, keystoreReadable = true, unresolvedCrashCount = 1)
        assertFalse(healthy)
        assertTrue(msg.contains("pending crash"))

        val store = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[AuraViewModel::class.java]
            val deadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < deadline && gitHubKeyStore.getUpdateHistory().isEmpty()) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50L)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Verify user memory + custom tool survived startup health check & upgrade logging
            assertTrue(memRepo.getRecentMemories(10).any { it.title == "UserThemeNote" })
            assertNotNull(memRepo.getAllToolsOnce().firstOrNull { it.id == "USER-TOOL-PRESERVED" })
            val history = gitHubKeyStore.getUpdateHistory()
            assertTrue(history.isNotEmpty())
            assertTrue(history.any { it.status == "RECOVERY_CANDIDATE" })
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching { WorkManager.getInstance(app).cancelAllWork() }
            AuraDatabase.closeAndResetForTesting(app)
        }
    }

    @Test
    fun roomSchemaMigrationFromV1ThroughV5PreservesAllExistingData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "aura_migration_v1_to_v5_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)

        // Create authentic v1 SQLite schema matching AuraDatabase v1 entities and insert rows
        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `memory_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `language` TEXT NOT NULL, `isSensitive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `task_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `instruction` TEXT NOT NULL, `detectedLanguage` TEXT NOT NULL, `assignedAgents` TEXT NOT NULL, `planStepsJson` TEXT NOT NULL, `status` TEXT NOT NULL, `verificationNote` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `actor` TEXT NOT NULL, `action` TEXT NOT NULL, `safetyLevel` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `approval_requests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `actionType` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `targetPackage` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `requestedByAgent` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `marketplace_tools` (`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `version` TEXT NOT NULL, `previousVersion` TEXT, `source` TEXT NOT NULL, `license` TEXT NOT NULL, `description` TEXT NOT NULL, `requiredPermissions` TEXT NOT NULL, `dependencies` TEXT NOT NULL, `compatibility` TEXT NOT NULL, `securityScan` TEXT NOT NULL, `testResults` TEXT NOT NULL, `status` TEXT NOT NULL, `updateAvailableVersion` TEXT, `mcpSchemaJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `research_projects` (`id` TEXT NOT NULL PRIMARY KEY, `sourcePlatform` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `url` TEXT NOT NULL, `latestReleaseOrUpdate` TEXT NOT NULL, `license` TEXT NOT NULL, `compatibility` TEXT NOT NULL, `dependencies` TEXT NOT NULL, `securityAssessment` TEXT NOT NULL, `resourceEstimate` TEXT NOT NULL, `installationStatus` TEXT NOT NULL, `testStatus` TEXT NOT NULL, `recommendationReason` TEXT NOT NULL, `metricsLabel` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `evolution_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gapIdentified` TEXT NOT NULL, `candidateSolution` TEXT NOT NULL, `evaluatorAgents` TEXT NOT NULL, `staticAnalysisResult` TEXT NOT NULL, `benchmarkSummary` TEXT NOT NULL, `status` TEXT NOT NULL, `rollbackAvailable` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `provider_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `providerId` TEXT NOT NULL, `modelName` TEXT NOT NULL, `taskType` TEXT NOT NULL, `latencyMs` INTEGER NOT NULL, `estimatedTokens` INTEGER NOT NULL, `success` INTEGER NOT NULL, `errorMessage` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `feature_audit` (`id` TEXT NOT NULL PRIMARY KEY, `featureName` TEXT NOT NULL, `moduleName` TEXT NOT NULL, `status` TEXT NOT NULL, `evidenceNote` TEXT NOT NULL, `lastVerifiedAt` INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v1Helper.writableDatabase.use { sqlDb ->
            sqlDb.execSQL("INSERT INTO memory_entries (category, title, content, language, isSensitive, createdAt, updatedAt) VALUES ('INSTRUCTION', 'MigratedPref', 'Always keep lossless user memories', 'EN', 0, 1000, 1000)")
            sqlDb.execSQL("INSERT INTO marketplace_tools (id, name, category, version, previousVersion, source, license, description, requiredPermissions, dependencies, compatibility, securityScan, testResults, status, updateAvailableVersion, mcpSchemaJson, updatedAt) VALUES ('V1-TOOL', 'V1 Utility', 'Utility', '1.0.0', NULL, 'USER', 'MIT', 'Seeded in v1', 'None', 'None', 'Android 8+', 'PASSED', 'OK', 'INSTALLED_ENABLED', NULL, '{}', 1000)")
        }
        v1Helper.close()

        val migratedDb = Room.databaseBuilder(context, AuraDatabase::class.java, dbName)
            .addMigrations(
                AuraDatabase.MIGRATION_1_2,
                AuraDatabase.MIGRATION_2_3,
                AuraDatabase.MIGRATION_3_4,
                AuraDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
        try {
            val memories = migratedDb.memoryDao().getRecentMemories(10)
            assertTrue(memories.any { it.title == "MigratedPref" && it.content == "Always keep lossless user memories" })
            val tools = migratedDb.marketplaceDao().observeTools().first()
            assertTrue(tools.any { it.id == "V1-TOOL" && it.status == "INSTALLED_ENABLED" })

            // Verify v5 tables exist and operate cleanly
            val netRepo = GlobalAgentNetworkRepository(migratedDb)
            netRepo.seedInitialAgentNodesIfEmpty()
            assertTrue(netRepo.getAllNodes().isNotEmpty())
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
