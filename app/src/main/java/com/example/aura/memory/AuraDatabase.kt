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
        SelfModificationRecordEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_autonomous_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
