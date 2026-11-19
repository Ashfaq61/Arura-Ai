package com.example.aura.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntryEntity::class,
        TaskRecordEntity::class,
        AuditLogEntity::class,
        ApprovalRequestEntity::class,
        MarketplaceToolEntity::class,
        ResearchProjectEntity::class,
        EvolutionCycleEntity::class,
        ProviderUsageEntity::class,
        FeatureAuditEntity::class,
        SkillProcedureEntity::class,
        LongHorizonTaskEntity::class,
        ScheduledTaskEntity::class,
        BenchmarkRunEntity::class,
        SelfModificationRecordEntity::class,
        // Expansion v3 entities
        RoutineEntity::class,
        VisualWorkflowEntity::class,
        KnowledgeNodeEntity::class,
        KnowledgeEdgeEntity::class,
        AppCompatibilityProfileEntity::class,
        AutomationRecipeEntity::class,
        ProductivityItemEntity::class,
        SyncDeviceEntity::class,
        CrashReportEntity::class,
        // Agent History & Feature Control v4 entities
        AgentConversationEntity::class,
        CustomAgentProfileEntity::class,
        AgentActivityEventEntity::class,
        FeatureRegistryHistoryEntity::class,
        FeatureRequestEntity::class,
        FeatureRecommendationEntity::class,
        PendingChangeProposalEntity::class,
        // Always-Connected Global AI Agent Network v5 entities
        ConnectedAgentNodeEntity::class,
        GlobalDiscussionSessionEntity::class,
        GlobalDiscussionTurnEntity::class,
        QueuedAgentMessageEntity::class,
        AgentNotificationEventEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun auditDao(): AuditDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun marketplaceDao(): MarketplaceDao
    abstract fun researchDao(): ResearchDao
    abstract fun evolutionDao(): EvolutionDao
    abstract fun usageDao(): UsageDao
    abstract fun featureAuditDao(): FeatureAuditDao
    abstract fun skillDao(): SkillDao
    abstract fun longHorizonDao(): LongHorizonDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun selfModDao(): SelfModDao

    // Expansion v3 DAOs
    abstract fun routineDao(): RoutineDao
    abstract fun visualWorkflowDao(): VisualWorkflowDao
    abstract fun knowledgeGraphDao(): KnowledgeGraphDao
    abstract fun appCompatibilityDao(): AppCompatibilityDao
    abstract fun automationRecipeDao(): AutomationRecipeDao
    abstract fun productivityDao(): ProductivityDao
    abstract fun syncDeviceDao(): SyncDeviceDao
    abstract fun crashReportDao(): CrashReportDao

    // Agent History & Feature Control v4 DAOs
    abstract fun agentConversationDao(): AgentConversationDao
    abstract fun customAgentProfileDao(): CustomAgentProfileDao
    abstract fun agentActivityDao(): AgentActivityDao
    abstract fun featureRegistryHistoryDao(): FeatureRegistryHistoryDao
    abstract fun featureRequestDao(): FeatureRequestDao
    abstract fun featureRecommendationDao(): FeatureRecommendationDao
    abstract fun pendingChangeProposalDao(): PendingChangeProposalDao

    // Always-Connected Global AI Agent Network v5 DAOs
    abstract fun connectedAgentNodeDao(): ConnectedAgentNodeDao
    abstract fun globalDiscussionSessionDao(): GlobalDiscussionSessionDao
    abstract fun globalDiscussionTurnDao(): GlobalDiscussionTurnDao
    abstract fun queuedAgentMessageDao(): QueuedAgentMessageDao
    abstract fun agentNotificationEventDao(): AgentNotificationEventDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `skill_procedures` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL, `description` TEXT NOT NULL,
                        `targetPackage` TEXT NOT NULL, `supportedAppVersion` TEXT NOT NULL,
                        `layoutFingerprint` TEXT NOT NULL, `stepsJson` TEXT NOT NULL,
                        `version` INTEGER NOT NULL, `testStatus` TEXT NOT NULL,
                        `usageCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `long_horizon_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL, `goal` TEXT NOT NULL, `milestonesJson` TEXT NOT NULL,
                        `completedSteps` INTEGER NOT NULL, `totalSteps` INTEGER NOT NULL,
                        `status` TEXT NOT NULL, `loopDetectionCount` INTEGER NOT NULL,
                        `replanCount` INTEGER NOT NULL, `checkpointJson` TEXT NOT NULL,
                        `finalReport` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `scheduled_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL, `taskType` TEXT NOT NULL, `targetQuery` TEXT NOT NULL,
                        `intervalHours` INTEGER NOT NULL, `enabled` INTEGER NOT NULL,
                        `workTag` TEXT NOT NULL, `lastRunAt` INTEGER, `lastOutcome` TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `benchmark_runs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `buildTag` TEXT NOT NULL, `suiteName` TEXT NOT NULL,
                        `scorePercent` INTEGER NOT NULL, `passedCases` INTEGER NOT NULL,
                        `totalCases` INTEGER NOT NULL, `previousBuildScore` INTEGER,
                        `regressionDetected` INTEGER NOT NULL, `detailsJson` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `self_mod_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `stage` TEXT NOT NULL, `targetModule` TEXT NOT NULL,
                        `proposalSummary` TEXT NOT NULL, `securityScanResult` TEXT NOT NULL,
                        `benchmarkDelta` TEXT NOT NULL, `signatureHash` TEXT NOT NULL,
                        `approvedByUser` INTEGER NOT NULL, `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `routines` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL, `triggerType` TEXT NOT NULL, `triggerConfig` TEXT NOT NULL,
                        `stepsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `scheduleHours` INTEGER NOT NULL,
                        `lastRunAt` INTEGER, `lastOutcome` TEXT NOT NULL, `runCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `visual_workflows` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL, `description` TEXT NOT NULL, `variablesJson` TEXT NOT NULL,
                        `nodesJson` TEXT NOT NULL, `lastValidationResult` TEXT NOT NULL, `lastRunLog` TEXT NOT NULL,
                        `lastRunSuccess` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `knowledge_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nodeType` TEXT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL, `sourceRef` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `knowledge_edges` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromNodeId` INTEGER NOT NULL, `toNodeId` INTEGER NOT NULL,
                        `relationType` TEXT NOT NULL, `weight` REAL NOT NULL, `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `app_compatibility_profiles` (
                        `packageName` TEXT NOT NULL PRIMARY KEY,
                        `appLabel` TEXT NOT NULL, `knownVersionName` TEXT NOT NULL, `knownVersionCode` INTEGER NOT NULL,
                        `installedVersionName` TEXT NOT NULL, `installedVersionCode` INTEGER NOT NULL,
                        `uiLayoutHash` TEXT NOT NULL, `compatibilityStatus` TEXT NOT NULL,
                        `supportedActionsCsv` TEXT NOT NULL, `driftNotes` TEXT NOT NULL, `lastCheckedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `automation_recipes` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL, `category` TEXT NOT NULL, `version` TEXT NOT NULL,
                        `targetPackage` TEXT NOT NULL, `minAppVersion` TEXT NOT NULL, `description` TEXT NOT NULL,
                        `stepsJson` TEXT NOT NULL, `successCount` INTEGER NOT NULL, `failureCount` INTEGER NOT NULL,
                        `lastTestStatus` TEXT NOT NULL, `lastTestedAt` INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `productivity_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemType` TEXT NOT NULL, `title` TEXT NOT NULL, `details` TEXT NOT NULL,
                        `dueAtMillis` INTEGER, `priority` TEXT NOT NULL, `completed` INTEGER NOT NULL,
                        `calendarEventId` INTEGER, `workTag` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_devices` (
                        `deviceId` TEXT NOT NULL PRIMARY KEY,
                        `deviceName` TEXT NOT NULL, `platform` TEXT NOT NULL, `authorized` INTEGER NOT NULL,
                        `lastSyncAt` INTEGER, `lastChecksum` TEXT NOT NULL, `syncChannel` TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `crash_reports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `threadName` TEXT NOT NULL, `exceptionType` TEXT NOT NULL, `message` TEXT NOT NULL,
                        `stackTrace` TEXT NOT NULL, `resolved` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `agent_conversations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` TEXT NOT NULL, `sessionId` TEXT NOT NULL,
                        `agentId` TEXT NOT NULL, `agentName` TEXT NOT NULL,
                        `participatingAgentsCsv` TEXT NOT NULL, `channelType` TEXT NOT NULL,
                        `topic` TEXT NOT NULL, `userOriginalRequest` TEXT NOT NULL,
                        `qaExchangedJson` TEXT NOT NULL, `recommendationsMade` TEXT NOT NULL,
                        `researchFindings` TEXT NOT NULL, `repositoryAndModelLinksCsv` TEXT NOT NULL,
                        `decisionsAndReasons` TEXT NOT NULL, `proposedFeaturesOrToolsCsv` TEXT NOT NULL,
                        `actionsPerformedCsv` TEXT NOT NULL, `testResults` TEXT NOT NULL,
                        `finalOutcome` TEXT NOT NULL, `sensitivityRedacted` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `custom_agent_profiles` (
                        `codeName` TEXT NOT NULL PRIMARY KEY,
                        `numericId` INTEGER NOT NULL, `name` TEXT NOT NULL,
                        `roleDescription` TEXT NOT NULL, `capabilitiesCsv` TEXT NOT NULL,
                        `preferredProviderId` TEXT NOT NULL, `preferredModelId` TEXT NOT NULL,
                        `createdFromRequestId` TEXT NOT NULL, `isCustom` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL, `lastActivitySummary` TEXT NOT NULL,
                        `lastActivityAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `agent_activity_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `agentName` TEXT NOT NULL, `activityType` TEXT NOT NULL,
                        `targetAgentOrModule` TEXT NOT NULL, `summary` TEXT NOT NULL,
                        `status` TEXT NOT NULL, `errorReason` TEXT NOT NULL,
                        `relatedRequestId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `feature_registry_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `featureId` TEXT NOT NULL, `featureName` TEXT NOT NULL,
                        `description` TEXT NOT NULL, `discoverySource` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL, `recommendingAgent` TEXT NOT NULL,
                        `participatingAgentsCsv` TEXT NOT NULL, `discoveredAt` INTEGER NOT NULL,
                        `version` TEXT NOT NULL, `previousVersion` TEXT,
                        `changeEventType` TEXT NOT NULL, `dependencies` TEXT NOT NULL,
                        `permissionsRequired` TEXT NOT NULL, `installationDate` INTEGER,
                        `testResults` TEXT NOT NULL, `activationStatus` TEXT NOT NULL,
                        `decisionReason` TEXT NOT NULL, `versionDiffSummary` TEXT NOT NULL,
                        `rollbackSnapshotJson` TEXT NOT NULL, `timestamp` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `feature_requests` (
                        `requestId` TEXT NOT NULL PRIMARY KEY,
                        `userPrompt` TEXT NOT NULL, `requestMode` TEXT NOT NULL,
                        `linkedSourceId` TEXT NOT NULL, `assignedAgentsCsv` TEXT NOT NULL,
                        `currentStage` INTEGER NOT NULL, `stageName` TEXT NOT NULL,
                        `stageHistoryJson` TEXT NOT NULL, `consultationSummary` TEXT NOT NULL,
                        `technicalPlan` TEXT NOT NULL, `alternativesSummary` TEXT NOT NULL,
                        `compatibilityReport` TEXT NOT NULL, `securityReport` TEXT NOT NULL,
                        `requiredApisAndPerms` TEXT NOT NULL, `testReport` TEXT NOT NULL,
                        `status` TEXT NOT NULL, `failureReason` TEXT NOT NULL,
                        `resultFeatureId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `feature_recommendations` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `featureName` TEXT NOT NULL, `summaryWhatItDoes` TEXT NOT NULL,
                        `whyAuraNeedsIt` TEXT NOT NULL, `limitationSolved` TEXT NOT NULL,
                        `discoveringAgent` TEXT NOT NULL, `otherAgentOpinionsJson` TEXT NOT NULL,
                        `sourceLinksCsv` TEXT NOT NULL, `securityAssessment` TEXT NOT NULL,
                        `compatibilityAssessment` TEXT NOT NULL, `developmentEffort` TEXT NOT NULL,
                        `costResourceImpact` TEXT NOT NULL, `testRequirements` TEXT NOT NULL,
                        `inboxStatus` TEXT NOT NULL, `agentFollowUpNotes` TEXT NOT NULL,
                        `linkedRequestId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pending_change_proposals` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `requestId` TEXT NOT NULL, `featureId` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `changeType` TEXT NOT NULL,
                        `summary` TEXT NOT NULL, `targetVersion` TEXT NOT NULL,
                        `previousVersion` TEXT, `versionDiff` TEXT NOT NULL,
                        `testReport` TEXT NOT NULL, `securityAssessment` TEXT NOT NULL,
                        `requiresExplicitApproval` INTEGER NOT NULL, `approvalReason` TEXT NOT NULL,
                        `previousStateSnapshotJson` TEXT NOT NULL, `status` TEXT NOT NULL,
                        `modificationNotes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `connected_agent_nodes` (
                        `nodeId` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL, `category` TEXT NOT NULL,
                        `providerOrPlatform` TEXT NOT NULL, `modelName` TEXT NOT NULL,
                        `endpointUrl` TEXT NOT NULL, `connectionStatus` TEXT NOT NULL,
                        `trustStatus` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL, `autoHeartbeatEnabled` INTEGER NOT NULL,
                        `heartbeatIntervalMinutes` INTEGER NOT NULL, `webhookUrl` TEXT NOT NULL,
                        `allowedCapabilitiesCsv` TEXT NOT NULL, `lastHeartbeatAt` INTEGER,
                        `lastLatencyMs` INTEGER NOT NULL, `lastErrorMessage` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `global_discussion_sessions` (
                        `sessionId` TEXT NOT NULL PRIMARY KEY,
                        `topic` TEXT NOT NULL, `topicCategory` TEXT NOT NULL,
                        `topicFingerprint` TEXT NOT NULL, `initiatorAgent` TEXT NOT NULL,
                        `participatingAgentsCsv` TEXT NOT NULL, `originalQuestion` TEXT NOT NULL,
                        `councilSummary` TEXT NOT NULL, `finalRecommendation` TEXT NOT NULL,
                        `decisionType` TEXT NOT NULL, `decisionReasoning` TEXT NOT NULL,
                        `proposedFeatureName` TEXT NOT NULL, `proposedFeatureDescription` TEXT NOT NULL,
                        `advantagesSummary` TEXT NOT NULL, `sourceLinksCsv` TEXT NOT NULL,
                        `licenseAndSecurity` TEXT NOT NULL, `androidCompatibility` TEXT NOT NULL,
                        `technicalRequirements` TEXT NOT NULL, `risksSummary` TEXT NOT NULL,
                        `implementationPlan` TEXT NOT NULL, `testResults` TEXT NOT NULL,
                        `linkedFeatureRequestId` TEXT NOT NULL, `linkedRecommendationId` TEXT NOT NULL,
                        `relatedSessionIdsCsv` TEXT NOT NULL, `status` TEXT NOT NULL,
                        `progressPercent` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `global_discussion_turns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL, `speakerAgent` TEXT NOT NULL,
                        `targetAgent` TEXT NOT NULL, `providerOrPlatform` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL, `stance` TEXT NOT NULL,
                        `messageText` TEXT NOT NULL, `evidenceLinksCsv` TEXT NOT NULL,
                        `injectionSanitized` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `queued_agent_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `targetNodeId` TEXT NOT NULL, `targetAgentName` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL, `payload` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL, `status` TEXT NOT NULL,
                        `lastError` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `agent_notification_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL, `priority` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `body` TEXT NOT NULL,
                        `relatedSessionId` TEXT NOT NULL, `relatedRequestId` TEXT NOT NULL,
                        `read` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_autonomous_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
