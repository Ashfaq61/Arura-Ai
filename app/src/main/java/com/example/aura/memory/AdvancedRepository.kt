package com.example.aura.memory

import kotlinx.coroutines.flow.Flow

class AdvancedRepository(private val db: AuraDatabase) {

    val routines: Flow<List<RoutineEntity>> = db.routineDao().observeRoutines()
    val visualWorkflows: Flow<List<VisualWorkflowEntity>> = db.visualWorkflowDao().observeWorkflows()
    val knowledgeNodes: Flow<List<KnowledgeNodeEntity>> = db.knowledgeGraphDao().observeNodes()
    val knowledgeEdges: Flow<List<KnowledgeEdgeEntity>> = db.knowledgeGraphDao().observeEdges()
    val appCompatProfiles: Flow<List<AppCompatibilityProfileEntity>> = db.appCompatibilityDao().observeProfiles()
    val automationRecipes: Flow<List<AutomationRecipeEntity>> = db.automationRecipeDao().observeRecipes()
    val productivityItems: Flow<List<ProductivityItemEntity>> = db.productivityDao().observeItems()
    val syncDevices: Flow<List<SyncDeviceEntity>> = db.syncDeviceDao().observeDevices()
    val crashReports: Flow<List<CrashReportEntity>> = db.crashReportDao().observeReports()

    suspend fun getAllRoutines(): List<RoutineEntity> = db.routineDao().getAll()
    suspend fun upsertRoutine(routine: RoutineEntity): Long = db.routineDao().upsert(routine)
    suspend fun deleteRoutine(id: Long) = db.routineDao().deleteById(id)

    suspend fun getAllWorkflows(): List<VisualWorkflowEntity> = db.visualWorkflowDao().getAll()
    suspend fun getWorkflowById(id: Long): VisualWorkflowEntity? = db.visualWorkflowDao().getById(id)
    suspend fun upsertWorkflow(workflow: VisualWorkflowEntity): Long = db.visualWorkflowDao().upsert(workflow)
    suspend fun deleteWorkflow(id: Long) = db.visualWorkflowDao().deleteById(id)

    suspend fun getAllKnowledgeNodes(): List<KnowledgeNodeEntity> = db.knowledgeGraphDao().getAllNodes()
    suspend fun getAllKnowledgeEdges(): List<KnowledgeEdgeEntity> = db.knowledgeGraphDao().getAllEdges()
    suspend fun upsertKnowledgeNode(node: KnowledgeNodeEntity): Long = db.knowledgeGraphDao().upsertNode(node)
    suspend fun upsertKnowledgeEdge(edge: KnowledgeEdgeEntity): Long = db.knowledgeGraphDao().upsertEdge(edge)
    suspend fun deleteKnowledgeNode(nodeId: Long) {
        db.knowledgeGraphDao().deleteEdgesForNode(nodeId)
        db.knowledgeGraphDao().deleteNodeById(nodeId)
    }
    suspend fun deleteKnowledgeEdge(edgeId: Long) = db.knowledgeGraphDao().deleteEdgeById(edgeId)

    suspend fun getAllCompatProfiles(): List<AppCompatibilityProfileEntity> = db.appCompatibilityDao().getAll()
    suspend fun upsertCompatProfiles(profiles: List<AppCompatibilityProfileEntity>) = db.appCompatibilityDao().upsertAll(profiles)
    suspend fun upsertCompatProfile(profile: AppCompatibilityProfileEntity) = db.appCompatibilityDao().upsert(profile)

    suspend fun getAllRecipes(): List<AutomationRecipeEntity> = db.automationRecipeDao().getAll()
    suspend fun upsertRecipe(recipe: AutomationRecipeEntity) = db.automationRecipeDao().upsert(recipe)
    suspend fun deleteRecipe(id: String) = db.automationRecipeDao().deleteById(id)

    suspend fun getAllProductivityItems(): List<ProductivityItemEntity> = db.productivityDao().getAll()
    suspend fun upsertProductivityItem(item: ProductivityItemEntity): Long = db.productivityDao().upsert(item)
    suspend fun deleteProductivityItem(id: Long) = db.productivityDao().deleteById(id)

    suspend fun getAllSyncDevices(): List<SyncDeviceEntity> = db.syncDeviceDao().getAll()
    suspend fun upsertSyncDevice(device: SyncDeviceEntity) = db.syncDeviceDao().upsert(device)
    suspend fun deleteSyncDevice(deviceId: String) = db.syncDeviceDao().deleteById(deviceId)

    suspend fun getAllCrashReports(): List<CrashReportEntity> = db.crashReportDao().getAll()
    suspend fun insertCrashReport(report: CrashReportEntity): Long = db.crashReportDao().insert(report)
    suspend fun resolveCrashReport(id: Long) = db.crashReportDao().markResolved(id)
    suspend fun clearCrashReports() = db.crashReportDao().clearAll()

    suspend fun seedAdvancedDefaultsIfEmpty() {
        db.routineDao().insertIgnoreAll(defaultRoutines())
        db.visualWorkflowDao().insertIgnoreAll(defaultWorkflows())
        db.automationRecipeDao().insertIgnoreAll(defaultRecipes())
        db.knowledgeGraphDao().insertIgnoreNodes(defaultKnowledgeNodes())
        db.knowledgeGraphDao().insertIgnoreEdges(defaultKnowledgeEdges())
        db.productivityDao().insertIgnoreAll(defaultProductivityItems())
    }

    private fun defaultRoutines(): List<RoutineEntity> = listOf(
        RoutineEntity(
            id = 1,
            name = "Morning Briefing & Health Check",
            triggerType = "SCHEDULE",
            triggerConfig = "Every 24h morning briefing",
            stepsJson = """[{"type":"HEALTH_CHECK","param":"full"},{"type":"CREATE_REMINDER","param":"Review daily tasks & calendar"}]""",
            enabled = true,
            scheduleHours = 24
        ),
        RoutineEntity(
            id = 2,
            name = "Low Battery Survival Saver",
            triggerType = "BATTERY_LOW",
            triggerConfig = "Battery < 15%",
            stepsJson = """[{"type":"VOLUME_DELTA","param":"-20"},{"type":"SPEAK","param":"Low battery detected. AURA switched to power saver mode."}]""",
            enabled = true
        ),
        RoutineEntity(
            id = 3,
            name = "Voice Focus Mode",
            triggerType = "VOICE",
            triggerConfig = "start focus mode|focus time",
            stepsJson = """[{"type":"VOLUME_DELTA","param":"-30"},{"type":"SPEAK","param":"Focus routine active. Notifications prioritized."}]""",
            enabled = true
        )
    )

    private fun defaultWorkflows(): List<VisualWorkflowEntity> = listOf(
        VisualWorkflowEntity(
            id = 1,
            name = "Smart Device Guard Workflow",
            description = "Checks RAM & battery, branches if battery is low, and logs readiness.",
            variablesJson = """{"minBattery":"20","owner":"User"}""",
            nodesJson = """[
                {"id":"n1","type":"TRIGGER","label":"Manual / Schedule Trigger","action":"","param":"","trueNext":"n2","falseNext":""},
                {"id":"n2","type":"ACTION","label":"Read Device Health","action":"HEALTH_CHECK","param":"ram_battery","trueNext":"n3","falseNext":""},
                {"id":"n3","type":"CONDITION_IF","label":"If Battery < minBattery","action":"BATTERY_LT","param":"{{minBattery}}","trueNext":"n4","falseNext":"n5"},
                {"id":"n4","type":"ACTION","label":"Enable Offline Saver Notice","action":"LOG_NOTE","param":"Low battery alert for {{owner}}","trueNext":"","falseNext":""},
                {"id":"n5","type":"ACTION","label":"Confirm Healthy Readiness","action":"LOG_NOTE","param":"System nominal for {{owner}}","trueNext":"","falseNext":""}
            ]""",
            lastValidationResult = "VALID (5 nodes, 0 cycles, 0 blocked actions)"
        )
    )

    private fun defaultRecipes(): List<AutomationRecipeEntity> = listOf(
        AutomationRecipeEntity(
            id = "recipe_wa_safe_reply",
            name = "WhatsApp Approved Draft Reply",
            category = "Communication",
            version = "1.2.0",
            targetPackage = "com.whatsapp",
            minAppVersion = "2.24.0",
            description = "Reads latest captured WhatsApp notification, drafts a concise reply, and requires explicit user confirmation before opening.",
            stepsJson = """[{"action":"READ_NOTIFICATIONS","param":"com.whatsapp"},{"action":"APPROVAL_GATE","param":"confirm_message"},{"action":"DRAFT_WHATSAPP","param":"Confirmed safe reply"}]""",
            successCount = 1,
            lastTestStatus = "PASSED"
        ),
        AutomationRecipeEntity(
            id = "recipe_yt_focus",
            name = "YouTube Educational Search",
            category = "Media & Learning",
            version = "1.1.0",
            targetPackage = "com.google.android.youtube",
            minAppVersion = "19.0.0",
            description = "Opens YouTube search results cleanly without scraping private user feeds.",
            stepsJson = """[{"action":"CHECK_COMPAT","param":"com.google.android.youtube"},{"action":"SEARCH_YOUTUBE","param":"Kotlin Jetpack Compose tutorial"}]""",
            successCount = 1,
            lastTestStatus = "PASSED"
        ),
        AutomationRecipeEntity(
            id = "recipe_battery_emergency",
            name = "Zero-Internet Battery Survival",
            category = "Offline & Power",
            version = "1.0.0",
            targetPackage = "android",
            minAppVersion = "API 24",
            description = "Lowers media volume, inspects storage/RAM, and prepares Offline Emergency SOS shortcuts.",
            stepsJson = """[{"action":"HEALTH_CHECK","param":"battery"},{"action":"VOLUME_DELTA","param":"-25"},{"action":"LOG_NOTE","param":"Offline survival check complete"}]""",
            successCount = 1,
            lastTestStatus = "PASSED"
        ),
        AutomationRecipeEntity(
            id = "recipe_github_ci",
            name = "GitHub CI Build & APK Inspector",
            category = "DevOps",
            version = "1.3.0",
            targetPackage = "com.android.chrome",
            minAppVersion = "120.0",
            description = "Audits repository workflow configuration and checks latest Android APK release readiness.",
            stepsJson = """[{"action":"GITHUB_CHECK_RELEASES","param":"latest"},{"action":"LOG_NOTE","param":"CI release watch inspected"}]""",
            successCount = 1,
            lastTestStatus = "PASSED"
        )
    )

    private fun defaultKnowledgeNodes(): List<KnowledgeNodeEntity> = listOf(
        KnowledgeNodeEntity(id = 1, nodeType = "PROJECT", title = "AURA AI Android Core", summary = "24-agent autonomous Android system with Gemini 3.8 Flash & local offline fallbacks.", tagsCsv = "aura,android,agents,gemini"),
        KnowledgeNodeEntity(id = 2, nodeType = "WORKFLOW", title = "Smart Device Guard Workflow", summary = "Automated battery + RAM safety check with conditional branching.", tagsCsv = "workflow,battery,health,automation"),
        KnowledgeNodeEntity(id = 3, nodeType = "ROUTINE", title = "Low Battery Survival Saver", summary = "Reduces media volume and alerts user when battery drops below 15%.", tagsCsv = "routine,battery,offline"),
        KnowledgeNodeEntity(id = 4, nodeType = "NOTE", title = "Emergency Offline Playbook", summary = "Use Offline Emergency tab for flashlight, calculator, RAM/battery telemetry, and SOS dialer.", tagsCsv = "offline,emergency,sos,flashlight")
    )

    private fun defaultKnowledgeEdges(): List<KnowledgeEdgeEntity> = listOf(
        KnowledgeEdgeEntity(id = 1, fromNodeId = 1, toNodeId = 2, relationType = "PART_OF", weight = 1.0f),
        KnowledgeEdgeEntity(id = 2, fromNodeId = 2, toNodeId = 3, relationType = "RELATED_TO", weight = 0.9f),
        KnowledgeEdgeEntity(id = 3, fromNodeId = 3, toNodeId = 4, relationType = "TRIGGERED_BY", weight = 0.85f)
    )

    private fun defaultProductivityItems(): List<ProductivityItemEntity> = listOf(
        ProductivityItemEntity(
            id = 1,
            itemType = "TASK",
            title = "Review AURA Security & Autonomy Policies",
            details = "Verify step limits, app allowlist, and backup passphrase.",
            priority = "HIGH"
        ),
        ProductivityItemEntity(
            id = 2,
            itemType = "NOTE",
            title = "Multilingual Voice Tip (EN / اردو / Roman Urdu)",
            details = "Tap the orb or enable Hey AURA Wake Word on the Core screen.",
            priority = "LOW"
        )
    )
}
