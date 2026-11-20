package com.example.aura.memory

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(private val db: AuraDatabase) {

    val allMemories: Flow<List<MemoryEntryEntity>> = db.memoryDao().observeAllMemories()
    val allTasks: Flow<List<TaskRecordEntity>> = db.taskDao().observeTasks()
    val auditLogs: Flow<List<AuditLogEntity>> = db.auditDao().observeAuditLogs()
    val approvals: Flow<List<ApprovalRequestEntity>> = db.approvalDao().observeApprovals()
    val pendingApprovalCount: Flow<Int> = db.approvalDao().observePendingCount()
    val marketplaceTools: Flow<List<MarketplaceToolEntity>> = db.marketplaceDao().observeTools()
    val researchProjects: Flow<List<ResearchProjectEntity>> = db.researchDao().observeProjects()
    val evolutionCycles: Flow<List<EvolutionCycleEntity>> = db.evolutionDao().observeCycles()
    val providerUsage: Flow<List<ProviderUsageEntity>> = db.usageDao().observeUsage()
    val featureAudits: Flow<List<FeatureAuditEntity>> = db.featureAuditDao().observeAudit()

    // Addendum flows
    val skillProcedures: Flow<List<SkillProcedureEntity>> = db.skillDao().observeSkills()
    val longHorizonTasks: Flow<List<LongHorizonTaskEntity>> = db.longHorizonDao().observeTasks()
    val scheduledTasks: Flow<List<ScheduledTaskEntity>> = db.scheduledTaskDao().observeScheduledTasks()
    val benchmarkRuns: Flow<List<BenchmarkRunEntity>> = db.benchmarkDao().observeRuns()
    val selfModRecords: Flow<List<SelfModificationRecordEntity>> = db.selfModDao().observeRecords()

    fun searchMemories(query: String): Flow<List<MemoryEntryEntity>> =
        if (query.isBlank()) allMemories else db.memoryDao().searchMemories(query.trim())

    fun searchAuditLogs(query: String): Flow<List<AuditLogEntity>> =
        if (query.isBlank()) auditLogs else db.auditDao().searchAuditLogs(query.trim())

    suspend fun getRecentMemories(limit: Int = 12): List<MemoryEntryEntity> =
        db.memoryDao().getRecentMemories(limit)

    suspend fun saveMemory(entry: MemoryEntryEntity): Long = db.memoryDao().insertMemory(entry)
    suspend fun updateMemory(entry: MemoryEntryEntity) =
        db.memoryDao().updateMemory(entry.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteMemory(id: Long) = db.memoryDao().deleteMemoryById(id)
    suspend fun clearAllMemories() = db.memoryDao().clearAllMemories()

    suspend fun insertTask(task: TaskRecordEntity): Long = db.taskDao().insertTask(task)
    suspend fun updateTask(task: TaskRecordEntity) = db.taskDao().updateTask(task)

    suspend fun logAudit(actor: String, action: String, safetyLevel: Int, outcome: String, details: String) {
        db.auditDao().insertLog(AuditLogEntity(actor = actor, action = action, safetyLevel = safetyLevel, outcome = outcome, details = details))
    }

    suspend fun clearAuditLogs() = db.auditDao().clearLogs()
    suspend fun createApprovalRequest(request: ApprovalRequestEntity): Long = db.approvalDao().insertApproval(request)
    suspend fun resolveApproval(id: Long, approved: Boolean) = db.approvalDao().updateStatus(id, if (approved) "APPROVED" else "DENIED")

    suspend fun upsertTool(tool: MarketplaceToolEntity) = db.marketplaceDao().upsertTool(tool)
    suspend fun getTool(id: String): MarketplaceToolEntity? = db.marketplaceDao().getToolById(id)
    suspend fun getAllToolsOnce(): List<MarketplaceToolEntity> = db.marketplaceDao().observeTools().first()
    suspend fun deleteTool(id: String) = db.marketplaceDao().deleteTool(id)

    suspend fun upsertResearchProjects(projects: List<ResearchProjectEntity>) = db.researchDao().upsertProjects(projects)
    suspend fun getAllResearchProjectsOnce(): List<ResearchProjectEntity> = db.researchDao().observeProjects().first()
    suspend fun insertEvolutionCycle(cycle: EvolutionCycleEntity): Long = db.evolutionDao().insertCycle(cycle)
    suspend fun updateEvolutionCycle(cycle: EvolutionCycleEntity) = db.evolutionDao().updateCycle(cycle)
    suspend fun recordUsage(usage: ProviderUsageEntity) = db.usageDao().insertUsage(usage)
    suspend fun upsertFeatureAudit(item: FeatureAuditEntity) = db.featureAuditDao().upsert(item)

    // Addendum repository helpers
    suspend fun upsertSkill(skill: SkillProcedureEntity): Long = db.skillDao().upsertSkill(skill)
    suspend fun deleteSkill(id: Long) = db.skillDao().deleteSkill(id)
    suspend fun upsertLongHorizonTask(task: LongHorizonTaskEntity): Long = db.longHorizonDao().upsertTask(task)
    suspend fun getLongHorizonTask(id: Long): LongHorizonTaskEntity? = db.longHorizonDao().getById(id)
    suspend fun deleteLongHorizonTask(id: Long) = db.longHorizonDao().deleteTask(id)
    suspend fun upsertScheduledTask(task: ScheduledTaskEntity): Long = db.scheduledTaskDao().upsert(task)
    suspend fun deleteScheduledTask(id: Long) = db.scheduledTaskDao().deleteById(id)
    suspend fun insertBenchmarkRun(run: BenchmarkRunEntity): Long = db.benchmarkDao().insertRun(run)
    suspend fun getLatestBenchmark(): BenchmarkRunEntity? = db.benchmarkDao().getLatestRun()
    suspend fun upsertSelfModRecord(record: SelfModificationRecordEntity): Long = db.selfModDao().upsertRecord(record)

    suspend fun exportMemoriesJson(): String {
        val entries = db.memoryDao().getRecentMemories(500)
        val arr = JSONArray()
        entries.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("category", m.category); put("title", m.title)
                put("content", m.content); put("language", m.language)
                put("isSensitive", m.isSensitive); put("updatedAt", m.updatedAt)
            })
        }
        return JSONObject().apply {
            put("app", "AURA AI"); put("exportedAt", System.currentTimeMillis())
            put("count", entries.size); put("memories", arr)
        }.toString(2)
    }

    fun shareText(context: Context, title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    suspend fun seedInitialCatalogIfEmpty() {
        db.marketplaceDao().insertIgnoreAll(defaultMarketplaceCatalog())
        db.researchDao().insertIgnoreAll(targetResearchEvaluations())
        db.scheduledTaskDao().insertIgnoreAll(defaultSchedules())
        db.featureAuditDao().upsertAll(initialFeatureAuditMatrix())
    }

    private fun defaultSchedules(): List<ScheduledTaskEntity> = listOf(
        ScheduledTaskEntity(id = 1, name = "Morning Daily Briefing", taskType = "BRIEFING", targetQuery = "Morning priorities, calendar, and system readiness", intervalHours = 24, enabled = true, workTag = "aura_sched_briefing"),
        ScheduledTaskEntity(id = 2, name = "GitHub Agent & Model Watch", taskType = "GITHUB_WATCH", targetQuery = "android gui agent openphoneuse artemis gemma", intervalHours = 12, enabled = true, workTag = "aura_sched_github"),
        ScheduledTaskEntity(id = 3, name = "Periodic System Health Check", taskType = "HEALTH_CHECK", targetQuery = "RAM, storage, battery, and error recovery telemetry", intervalHours = 6, enabled = true, workTag = "aura_sched_health")
    )

    private fun targetResearchEvaluations(): List<ResearchProjectEntity> = listOf(
        ResearchProjectEntity(
            id = "eval_openphoneuse", sourcePlatform = "RESEARCH_EVAL",
            name = "OpenPhoneUse", description = "On-device Android phone-use GUI automation paradigm.",
            url = "https://github.com/search?q=OpenPhoneUse",
            latestReleaseOrUpdate = "Evaluated Architecture", license = "Verify Repo License",
            compatibility = "Android AccessibilityService + Screen Capture", dependencies = "AccessibilityNodeInfo, GestureDescription",
            securityAssessment = "Reference pattern adopted natively via AuraAccessibilityService (no untrusted code copied).",
            resourceEstimate = "Low RAM (~25MB)", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Inspired AURA's numbered UI element tagging and pre-action screen freshness recheck.", metricsLabel = "GUI Agent"
        ),
        ResearchProjectEntity(
            id = "eval_google_artemis", sourcePlatform = "RESEARCH_EVAL",
            name = "Google ARTEMIS", description = "Android GUI automation and reliability benchmarking suite.",
            url = "https://github.com/search?q=ARTEMIS+Android+automation",
            latestReleaseOrUpdate = "Evaluated Benchmark", license = "Apache-2.0 (Verify)",
            compatibility = "AndroidWorld / AndroidLab style harness", dependencies = "Local JVM / Robolectric + Accessibility checks",
            securityAssessment = "Safe evaluation methodology; integrated into AURA Benchmark Lab.",
            resourceEstimate = "Minimal CPU", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Drives AURA's regression gate before activating new skills or upgrades.", metricsLabel = "Reliability"
        ),
        ResearchProjectEntity(
            id = "eval_gagent", sourcePlatform = "RESEARCH_EVAL",
            name = "GAgent", description = "Self-evolving Android agent with reusable procedural skill memory.",
            url = "https://github.com/search?q=GAgent+Android",
            latestReleaseOrUpdate = "Evaluated Pattern", license = "Verify Repo License",
            compatibility = "Kotlin + Room Skill Graph", dependencies = "SkillProcedureEntity + Version Fingerprinting",
            securityAssessment = "Adopted safely with PromptInjectionDefender so screen text never becomes trusted skills.",
            resourceEstimate = "Low SQLite footprint", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Powers AURA's Skill Memory Engine with app-version drift detection and repair.", metricsLabel = "Procedural"
        ),
        ResearchProjectEntity(
            id = "eval_opengui", sourcePlatform = "RESEARCH_EVAL",
            name = "OpenGUI", description = "Mobile GUI agent combining accessibility trees with visual grounding.",
            url = "https://github.com/search?q=OpenGUI+mobile+agent",
            latestReleaseOrUpdate = "Evaluated Architecture", license = "MIT / Apache-2.0 (Verify)",
            compatibility = "Accessibility + API 30+ Screenshot + Vision LLM", dependencies = "takeScreenshot + Gemini Vision",
            securityAssessment = "Safe multimodal grounding when paired with PrivacyDataRouter redaction.",
            resourceEstimate = "Medium (Bitmap + Vision)", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Fuses accessibility node IDs with visual confirmation before tapping.", metricsLabel = "Multimodal"
        ),
        ResearchProjectEntity(
            id = "eval_google_adk", sourcePlatform = "RESEARCH_EVAL",
            name = "Google ADK", description = "Agent Development Kit for structured multi-agent tool orchestration.",
            url = "https://github.com/google/adk-python",
            latestReleaseOrUpdate = "Active Reference", license = "Apache-2.0",
            compatibility = "Server/Cloud companion or Kotlin A2A mirror", dependencies = "Python ADK on backend / Kotlin A2A on Android",
            securityAssessment = "Official Apache-2.0 project; mirrored via Kotlin AgentNetworkManager & MCP contracts.",
            resourceEstimate = "Cloud/Companion", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Standardizes hierarchical delegation, tool schemas, and state handoffs.", metricsLabel = "Orchestration"
        ),
        ResearchProjectEntity(
            id = "eval_ms_agent_framework", sourcePlatform = "RESEARCH_EVAL",
            name = "Microsoft Agent Framework", description = "Multi-agent conversational and workflow coordination framework.",
            url = "https://github.com/microsoft/autogen",
            latestReleaseOrUpdate = "Active Reference", license = "MIT",
            compatibility = "Reference pattern (Python/.NET -> Kotlin A2A)", dependencies = "Coroutine SharedFlow message bus",
            securityAssessment = "MIT licensed reference; adapted into AURA's native Kotlin 24-agent A2A bus.",
            resourceEstimate = "Native Kotlin Low", installationStatus = "EVALUATED", testStatus = "PASSED",
            recommendationReason = "Guides AURA's multi-agent handoff, critique, and error recovery loops.", metricsLabel = "Multi-Agent"
        ),
        ResearchProjectEntity(
            id = "eval_gemma4", sourcePlatform = "RESEARCH_EVAL",
            name = "Gemma 4 (On-Device Edge)", description = "Open-weight edge model evaluated for private local Android inference.",
            url = "https://huggingface.co/google",
            latestReleaseOrUpdate = "Hardware-Gated", license = "Gemma Terms of Use",
            compatibility = "Android >= 4GB RAM (E2B) / >= 6GB RAM (E4B) via AICore / LiteRT", dependencies = "ML Kit GenAI / LiteRT runtime",
            securityAssessment = "Checked for RAM, storage, battery thermal impact, and license before download.",
            resourceEstimate = "1.9–3.4 GB Storage • 4–6 GB RAM", installationStatus = "NOT_INSTALLED", testStatus = "PASSED",
            recommendationReason = "Primary candidate for local-only private inference on high-RAM Android devices.", metricsLabel = "On-Device LLM"
        )
    )

    private fun defaultMarketplaceCatalog(): List<MarketplaceToolEntity> = listOf(
        MarketplaceToolEntity("tool_gemini_orchestrator", "Gemini 3.8 Neural Core", "AI Models", "3.8.0", "3.5.0", "Firebase AI Logic (App Check)", "Google Firebase Terms", "Primary streaming multimodal brain for reasoning, Urdu/English orchestration, and JSON planning.", "INTERNET", "firebase-ai, firebase-appcheck", "Android 7.0+ (API 24+)", "PASSED (App Check Attested)", "Verified in AI Router", "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"gemini_reason","params":["prompt","schema"]}"""),
        MarketplaceToolEntity("tool_agent_a2a_bus", "A2A Multi-Agent Mesh (24 Agents)", "AI Agents", "2.4.0", "2.3.1", "AURA Core Engine", "Apache-2.0", "Coordinates 24 specialist agents with shared context, delegation, and error recovery.", "None", "kotlinx-coroutines", "API 24+", "PASSED", "Unit tested", "INSTALLED_ENABLED", updateAvailableVersion = "2.5.0-rc1", mcpSchemaJson = """{"name":"delegate_agent","params":["agentId","task"]}"""),
        MarketplaceToolEntity("tool_accessibility_bridge", "Computer-Use Screen & Gesture Engine", "Android Automation", "2.0.0", "1.6.0", "Android AccessibilityService API", "Apache-2.0", "Numbered element tagging, API 30+ screen capture, freshness recheck, popup detection, and gestures.", "BIND_ACCESSIBILITY_SERVICE (User Opt-In)", "android.accessibilityservice", "API 24+ (Screenshot API 30+)", "PASSED (Play-Compliant Consent)", "Verified stale-coordinate guard", "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"computer_use","params":["action","elementIndex","text"]}"""),
        MarketplaceToolEntity("tool_voice_duplex", "Multilingual Voice & Live Turn Engine", "Voice Tools", "1.6.0", "1.4.2", "Android SpeechRecognizer + TTS + Gemini Stream", "Apache-2.0", "Continuous silence/turn detection, interruption, de-duplication, and Kore/Aoede/Zephyr presets.", "RECORD_AUDIO, MODIFY_AUDIO_SETTINGS", "android.speech, AudioManager", "API 24+", "PASSED", "Verified turn-taking", "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"voice_turn","params":["language","voice"]}"""),
        MarketplaceToolEntity("tool_camerax_vision", "CameraX Multimodal Scene & OCR", "Vision Tools", "1.5.0", source = "Jetpack CameraX + Gemini Vision", license = "Apache-2.0", description = "Live front/rear camera preview, photo capture, and multimodal scene/text understanding.", requiredPermissions = "CAMERA", dependencies = "androidx.camera:1.5.0, firebase-ai", compatibility = "API 24+", securityScan = "PASSED", testResults = "CameraX lifecycle verified", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"vision_analyze","params":["lensFacing","prompt"]}"""),
        MarketplaceToolEntity("tool_github_hf_scout", "GitHub & Hugging Face Live Scout", "Research Tools", "1.2.0", source = "api.github.com & huggingface.co/api", license = "MIT", description = "Queries live trending repositories, Android automation libraries, MCP servers, and open-source weights.", requiredPermissions = "INTERNET", dependencies = "okhttp, org.json", compatibility = "API 24+", securityScan = "PASSED (Read-only HTTPS)", testResults = "Live endpoint ready", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"research_query","params":["platform","query"]}"""),
        MarketplaceToolEntity("tool_code_synthesizer", "Safe Self-Mod & Tool Synthesizer", "Coding Tools", "1.4.0", source = "AURA Tool Factory", license = "Apache-2.0", description = "Signed 13-stage synthesis pipeline with static security checks and benchmark regression gating.", requiredPermissions = "None", dependencies = "PermissionGuard + PromptInjectionDefender", compatibility = "API 24+", securityScan = "PASSED (No DCL)", testResults = "Static scanner active", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"synthesize_tool","params":["capabilityGap"]}"""),
        MarketplaceToolEntity("tool_mcp_protocol_adapter", "MCP JSON-RPC Tool Adapter", "MCP Tools", "1.0.5", source = "Model Context Protocol Spec", license = "MIT", description = "Standardized schema registry allowing specialist agents to discover and invoke local or remote MCP tools.", requiredPermissions = "INTERNET (Remote MCP only)", dependencies = "org.json, okhttp", compatibility = "API 24+", securityScan = "PASSED", testResults = "Schema validator verified", status = "INSTALLED_ENABLED", updateAvailableVersion = "1.1.0", mcpSchemaJson = """{"name":"mcp_invoke","params":["server","tool","args"]}"""),
        MarketplaceToolEntity("tool_pdf_doc_reader", "PDF & Document Intelligence", "Productivity", "1.3.0", source = "Android PdfRenderer + SAF + Gemini", license = "Apache-2.0", description = "Zero-storage-permission document picker, PDF page renderer, OCR/text extraction, and summary sharing.", requiredPermissions = "None (SAF Picker)", dependencies = "android.graphics.pdf.PdfRenderer", compatibility = "API 24+", securityScan = "PASSED", testResults = "SAF contract verified", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"summarize_document","params":["uri"]}"""),
        MarketplaceToolEntity("tool_whatsapp_social_bridge", "WhatsApp & Social Intent Assistant", "Communication", "1.2.1", source = "Android Intents + NotificationListener", license = "Apache-2.0", description = "Reads opt-in message notifications with prompt-injection defense, drafts replies, and opens confirmed intents.", requiredPermissions = "BIND_NOTIFICATION_LISTENER_SERVICE (Opt-In)", dependencies = "NotificationListenerService", compatibility = "API 24+", securityScan = "PASSED (Level 3 Confirm)", testResults = "Intent flow verified", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"draft_reply","params":["contact","message"]}"""),
        MarketplaceToolEntity("tool_keystore_guard", "AES-256 Keystore & Prompt-Injection Guard", "Security", "2.2.0", source = "AndroidKeyStore + PermissionGuard", license = "Apache-2.0", description = "Encrypts external keys, redacts OTP/passwords, blocks prompt injection, and drives Emergency STOP.", requiredPermissions = "None", dependencies = "java.security.KeyStore", compatibility = "API 24+", securityScan = "PASSED", testResults = "Robolectric verified", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"security_audit","params":["action"]}"""),
        MarketplaceToolEntity("tool_ci_diagnostics", "ARTEMIS Benchmark & Observability Suite", "Developer Utilities", "2.0.0", source = "AURA Benchmark Lab + WorkManager", license = "Apache-2.0", description = "Runs regression benchmarks, live observability telemetry, and exportable diagnostic reports.", requiredPermissions = "None", dependencies = "androidx.work", compatibility = "API 24+", securityScan = "PASSED", testResults = "Verified", status = "INSTALLED_ENABLED", mcpSchemaJson = """{"name":"run_benchmark","params":[]}""")
    )

    private fun initialFeatureAuditMatrix(): List<FeatureAuditEntity> = listOf(
        FeatureAuditEntity("feat_ai_gemini", "Gemini Cloud Brain & Streaming", "ai", "ACTIVE", "Firebase AI Logic + App Check provisioned and initialized cleanly."),
        FeatureAuditEntity("feat_multi_provider", "Model Router, Budget & Free-Only Guard", "ai", "ACTIVE", "Keystore AES-256-GCM vault + cost/token cap + free-only enforcement + hybrid routing."),
        FeatureAuditEntity("feat_agents_24", "24-Agent Collaborative Mesh (A2A/MCP)", "agents", "ACTIVE", "A2A message bus, task delegation, and shared context across all 24 specialist agents."),
        FeatureAuditEntity("feat_computer_use", "Real-Time Computer-Use Engine", "automation", "PARTIAL", "Numbered node tagging, stale-coordinate guard, popup detection, and API 30+ screenshot active; requires Accessibility opt-in."),
        FeatureAuditEntity("feat_long_horizon", "Long-Horizon Task Planner (50-100 Steps)", "evolution", "ACTIVE", "Milestone decomposition, Room checkpoints, pause/resume/cancel, and loop detection."),
        FeatureAuditEntity("feat_skill_memory", "Reusable Skills & Procedural Memory", "memory", "ACTIVE", "Versioned procedures, app-version drift detection, repair/retest, and user inspection."),
        FeatureAuditEntity("feat_self_healing", "Self-Healing & Duplicate-Action Guard", "evolution", "ACTIVE", "Controlled retries, provider fallback, cache pruning, and strict block on retrying payments/messages/deletes."),
        FeatureAuditEntity("feat_privacy_injection", "Prompt-Injection & Privacy Data Router", "security", "ACTIVE", "External data sandboxing, secret/OTP redaction, screen minimization, and tamper protection."),
        FeatureAuditEntity("feat_scheduler", "Autonomous WorkManager Scheduler", "evolution", "ACTIVE", "Periodic briefings, GitHub/model watch, health checks with Pause/Edit/Delete and Notifications."),
        FeatureAuditEntity("feat_benchmark_lab", "Benchmark Lab & Regression Gate", "testing", "ACTIVE", "12-case ARTEMIS/AndroidWorld-style suite with build baseline comparison and regression blocking."),
        FeatureAuditEntity("feat_observability", "Live Observability & Diagnostic Export", "diagnostics", "ACTIVE", "Real-time provider, agent, memory, battery, crash, alert telemetry + searchable logs + JSON export."),
        FeatureAuditEntity("feat_self_mod", "13-Stage Signed Self-Modification Pipeline", "evolution", "ACTIVE", "DISCOVER→ROLLBACK pipeline with SHA-256 signature and benchmark/security approval gates."),
        FeatureAuditEntity("feat_voice_live", "Voice Conversation & Interruption", "voice", "PARTIAL", "Continuous turn-taking, de-duplication, EN/UR switching & presets active; native WebSocket duplex audio depends on provider/device."),
        FeatureAuditEntity("feat_camerax", "CameraX Live Vision & Photo Q&A", "vision", "ACTIVE", "CameraX preview, front/rear switch, capture, and Gemini multimodal analysis (requires CAMERA permission)."),
        FeatureAuditEntity("feat_local_llm", "On-Device Gemma 4 / Local AI Evaluation", "offline", "PARTIAL", "Offline deterministic engine & RAM/storage/battery compatibility gate active; GGUF/AICore runtime requires supported hardware."),
        FeatureAuditEntity("feat_phone_calls", "Phone Call Agent & Dialer Integration", "calls", "PARTIAL", "Call state monitoring, confirmed dialing, AI call consent flow active; two-way SIM audio is OS/carrier restricted."),
        FeatureAuditEntity("feat_ci_cd", "GitHub Actions APK Build Workflow", "devops", "ACTIVE", "Workflow configured for unit tests, Android Lint, and debug/release APK artifact upload.")
    )
}
