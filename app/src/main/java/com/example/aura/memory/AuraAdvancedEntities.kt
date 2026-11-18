package com.example.aura.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String, // VOICE, SCHEDULE, BATTERY_LOW, CHARGING, WIFI_CONNECTED, HEADSET_PLUGGED, APP_LAUNCH, MANUAL
    val triggerConfig: String,
    val stepsJson: String,
    val enabled: Boolean = true,
    val scheduleHours: Int = 0,
    val lastRunAt: Long? = null,
    val lastOutcome: String = "Ready",
    val runCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "visual_workflows")
data class VisualWorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val variablesJson: String = "{}",
    val nodesJson: String = "[]",
    val lastValidationResult: String = "Not validated",
    val lastRunLog: String = "",
    val lastRunSuccess: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_nodes")
data class KnowledgeNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nodeType: String, // MEMORY, TASK, PROJECT, WORKFLOW, ROUTINE, NOTE, CONTACT, CALENDAR
    val title: String,
    val summary: String,
    val tagsCsv: String = "",
    val sourceRef: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_edges")
data class KnowledgeEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromNodeId: Long,
    val toNodeId: Long,
    val relationType: String, // RELATED_TO, DEPENDS_ON, TRIGGERED_BY, PART_OF, TARGETS
    val weight: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_compatibility_profiles")
data class AppCompatibilityProfileEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val knownVersionName: String,
    val knownVersionCode: Long,
    val installedVersionName: String,
    val installedVersionCode: Long,
    val uiLayoutHash: String = "",
    val compatibilityStatus: String, // COMPATIBLE, VERSION_CHANGED, LAYOUT_DRIFT, UNSUPPORTED, NOT_INSTALLED
    val supportedActionsCsv: String,
    val driftNotes: String = "",
    val lastCheckedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_recipes")
data class AutomationRecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val version: String,
    val targetPackage: String,
    val minAppVersion: String,
    val description: String,
    val stepsJson: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastTestStatus: String = "PASSED",
    val lastTestedAt: Long? = null
)

@Entity(tableName = "productivity_items")
data class ProductivityItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: String, // REMINDER, TASK, NOTE, EVENT
    val title: String,
    val details: String = "",
    val dueAtMillis: Long? = null,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, URGENT
    val completed: Boolean = false,
    val calendarEventId: Long? = null,
    val workTag: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_devices")
data class SyncDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val authorized: Boolean = true,
    val lastSyncAt: Long? = null,
    val lastChecksum: String = "",
    val syncChannel: String = "LOCAL_BUNDLE"
)

@Entity(tableName = "crash_reports")
data class CrashReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadName: String,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val resolved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
