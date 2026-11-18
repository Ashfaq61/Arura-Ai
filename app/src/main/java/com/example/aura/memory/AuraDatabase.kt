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
        CrashReportEntity::class
    ],
    version = 3,
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

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_autonomous_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
