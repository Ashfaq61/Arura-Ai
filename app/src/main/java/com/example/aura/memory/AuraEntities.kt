package com.example.aura.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "CONVERSATION", "PREFERENCE", "INSTRUCTION", "PROJECT_CONTEXT"
    val title: String,
    val content: String,
    val language: String = "EN",
    val isSensitive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_records")
data class TaskRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instruction: String,
    val detectedLanguage: String,
    val assignedAgents: String,
    val planStepsJson: String,
    val status: String,
    val verificationNote: String,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actor: String,
    val action: String,
    val safetyLevel: Int,
    val outcome: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "approval_requests")
data class ApprovalRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val title: String,
    val description: String,
    val targetPackage: String = "",
    val payloadJson: String = "",
    val status: String = "PENDING",
    val requestedByAgent: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "marketplace_tools")
data class MarketplaceToolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val version: String,
    val previousVersion: String? = null,
    val source: String,
    val license: String,
    val description: String,
    val requiredPermissions: String,
    val dependencies: String,
    val compatibility: String,
    val securityScan: String,
    val testResults: String,
    val status: String,
    val updateAvailableVersion: String? = null,
    val mcpSchemaJson: String = "{}",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "research_projects")
data class ResearchProjectEntity(
    @PrimaryKey val id: String,
    val sourcePlatform: String,
    val name: String,
    val description: String,
    val url: String,
    val latestReleaseOrUpdate: String,
    val license: String,
    val compatibility: String,
    val dependencies: String,
    val securityAssessment: String,
    val resourceEstimate: String,
    val installationStatus: String,
    val testStatus: String,
    val recommendationReason: String,
    val metricsLabel: String = "",
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_cycles")
data class EvolutionCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gapIdentified: String,
    val candidateSolution: String,
    val evaluatorAgents: String,
    val staticAnalysisResult: String,
    val benchmarkSummary: String,
    val status: String,
    val rollbackAvailable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "provider_usage")
data class ProviderUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val modelName: String,
    val taskType: String,
    val latencyMs: Long,
    val estimatedTokens: Int,
    val success: Boolean,
    val errorMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "feature_audit")
data class FeatureAuditEntity(
    @PrimaryKey val id: String,
    val featureName: String,
    val moduleName: String,
    val status: String, // "ACTIVE", "PARTIAL", "IN_DEVELOPMENT", "BLOCKED", "NOT_AVAILABLE"
    val evidenceNote: String,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skill_procedures")
data class SkillProcedureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val targetPackage: String,
    val supportedAppVersion: String,
    val layoutFingerprint: String,
    val stepsJson: String,
    val version: Int = 1,
    val testStatus: String = "PASSED", // "UNTESTED", "PASSED", "NEEDS_REPAIR"
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "long_horizon_tasks")
data class LongHorizonTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goal: String,
    val milestonesJson: String,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val status: String = "PLANNING", // "PLANNING", "RUNNING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED"
    val loopDetectionCount: Int = 0,
    val replanCount: Int = 0,
    val checkpointJson: String = "{}",
    val finalReport: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val taskType: String, // "BRIEFING", "WEATHER_NEWS", "REMINDER", "GITHUB_WATCH", "MODEL_RELEASE", "HEALTH_CHECK", "CUSTOM"
    val targetQuery: String,
    val intervalHours: Int = 24,
    val enabled: Boolean = true,
    val workTag: String,
    val lastRunAt: Long? = null,
    val lastOutcome: String = "Scheduled — awaiting next window"
)

@Entity(tableName = "benchmark_runs")
data class BenchmarkRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buildTag: String,
    val suiteName: String,
    val scorePercent: Int,
    val passedCases: Int,
    val totalCases: Int,
    val previousBuildScore: Int? = null,
    val regressionDetected: Boolean = false,
    val detailsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "self_mod_records")
data class SelfModificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stage: String, // DISCOVER -> RESEARCH -> DESIGN -> GENERATE -> REVIEW -> SECURITY_SCAN -> BUILD -> TEST -> BENCHMARK -> APPROVE -> DEPLOY -> MONITOR -> ROLLED_BACK
    val targetModule: String,
    val proposalSummary: String,
    val securityScanResult: String,
    val benchmarkDelta: String,
    val signatureHash: String,
    val approvedByUser: Boolean = false,
    val status: String = "IN_PROGRESS",
    val createdAt: Long = System.currentTimeMillis()
)
