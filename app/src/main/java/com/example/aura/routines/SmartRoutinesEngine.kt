package com.example.aura.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.AutonomyPolicy
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.ProductivityItemEntity
import com.example.aura.memory.RoutineEntity
import com.example.aura.offline.OfflineEmergencyEngine
import com.example.aura.security.GuardDecision
import com.example.aura.security.PermissionGuard
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RoutineStep(
    val type: String, // SPEAK, HEALTH_CHECK, VOLUME_DELTA, OPEN_APP, TOGGLE_TORCH, CREATE_REMINDER, LOG_NOTE
    val param: String
)

data class RoutineExecutionReport(
    val routineId: Long,
    val routineName: String,
    val succeeded: Boolean,
    val stepLogs: List<String>,
    val summary: String
)

/**
 * Manages Smart Routines: CRUD, WorkManager time scheduling, Android system event listeners,
 * voice-phrase matching, and policy-gated multi-step execution.
 */
class SmartRoutinesEngine(
    private val context: Context,
    private val advancedRepo: AdvancedRepository,
    private val memoryRepo: MemoryRepository,
    private val deviceControl: DeviceControlEngine,
    private val offlineEngine: OfflineEmergencyEngine,
    private val permissionGuard: PermissionGuard
) {
    private val recentExecutionTimestamps = ArrayDeque<Long>()
    private var receiverRegistered = false
    private var systemEventReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun parseSteps(stepsJson: String): List<RoutineStep> {
        return runCatching {
            val arr = JSONArray(stepsJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                RoutineStep(
                    type = obj.optString("type", "LOG_NOTE"),
                    param = obj.optString("param", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeSteps(steps: List<RoutineStep>): String {
        val arr = JSONArray()
        steps.forEach { s ->
            arr.put(JSONObject().apply {
                put("type", s.type)
                put("param", s.param)
            })
        }
        return arr.toString()
    }

    fun scheduleRoutineWork(routine: RoutineEntity) {
        val wm = WorkManager.getInstance(context)
        val tag = "aura_routine_${routine.id}"
        if (!routine.enabled || routine.triggerType != "SCHEDULE" || routine.scheduleHours <= 0) {
            wm.cancelUniqueWork(tag)
            return
        }
        val req = PeriodicWorkRequestBuilder<RoutineScheduleWorker>(
            routine.scheduleHours.coerceAtLeast(1).toLong(),
            TimeUnit.HOURS
        )
            .addTag(tag)
            .addTag("aura_routines")
            .setInputData(workDataOf("routine_id" to routine.id, "routine_name" to routine.name))
            .build()
        wm.enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun registerSystemEventObservers(
        onTriggerEvent: (triggerType: String) -> Unit
    ) {
        if (receiverRegistered) return
        receiverRegistered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_LOW -> onTriggerEvent("BATTERY_LOW")
                    Intent.ACTION_POWER_CONNECTED -> onTriggerEvent("CHARGING")
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        if (intent.getIntExtra("state", 0) == 1) {
                            onTriggerEvent("HEADSET_PLUGGED")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        runCatching { context.registerReceiver(receiver, filter) }
        systemEventReceiver = receiver

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onTriggerEvent("WIFI_CONNECTED")
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        networkCallback = cb
    }

    fun unregisterSystemEventObservers() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { systemEventReceiver?.let { context.unregisterReceiver(it) } }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        runCatching { networkCallback?.let { cm?.unregisterNetworkCallback(it) } }
        systemEventReceiver = null
        networkCallback = null
    }

    suspend fun findMatchingVoiceRoutine(voiceText: String): RoutineEntity? {
        val lower = voiceText.trim().lowercase()
        if (lower.isBlank()) return null
        return advancedRepo.getAllRoutines().firstOrNull { r ->
            r.enabled && r.triggerType == "VOICE" && r.triggerConfig.split("|").any { phrase ->
                val clean = phrase.trim().lowercase()
                clean.isNotBlank() && lower.contains(clean)
            }
        }
    }

    suspend fun executeRoutine(
        routine: RoutineEntity,
        autonomyPolicy: AutonomyPolicy,
        isManualTrigger: Boolean = true,
        onSpeakStep: (String) -> Unit = {}
    ): RoutineExecutionReport {
        if (autonomyPolicy.scheduledTasksPaused && !isManualTrigger) {
            return RoutineExecutionReport(
                routineId = routine.id,
                routineName = routine.name,
                succeeded = false,
                stepLogs = listOf("Skipped: Scheduled/event routines are paused by Autonomy Policy."),
                summary = "Paused by user policy"
            )
        }

        // Hourly autonomous execution rate guard
        val oneHourAgo = System.currentTimeMillis() - 3_600_000L
        while (recentExecutionTimestamps.isNotEmpty() && recentExecutionTimestamps.first() < oneHourAgo) {
            recentExecutionTimestamps.removeFirst()
        }
        if (!isManualTrigger && recentExecutionTimestamps.size >= autonomyPolicy.maxScheduledRunsPerHour) {
            return RoutineExecutionReport(
                routineId = routine.id,
                routineName = routine.name,
                succeeded = false,
                stepLogs = listOf("Blocked: Hourly autonomous cap (${autonomyPolicy.maxScheduledRunsPerHour}/hr) reached."),
                summary = "Rate cap reached"
            )
        }
        recentExecutionTimestamps.addLast(System.currentTimeMillis())

        val steps = parseSteps(routine.stepsJson).take(autonomyPolicy.maxAutonomousSteps)
        val logs = mutableListOf<String>()
        var allOk = true

        for ((index, step) in steps.withIndex()) {
            val guard = permissionGuard.evaluateAction("routine_${step.type}", summary = step.param)
            if (guard is GuardDecision.Blocked) {
                logs.add("Step ${index + 1} [${step.type}] BLOCKED: ${guard.reason}")
                allOk = false
                break
            }
            if (guard is GuardDecision.RequiresApproval && !isManualTrigger) {
                logs.add("Step ${index + 1} [${step.type}] PAUSED: Requires manual user confirmation.")
                allOk = false
                break
            }

            val outcome = when (step.type.uppercase()) {
                "HEALTH_CHECK" -> {
                    val h = deviceControl.readDeviceHealth()
                    true to "RAM ${h.availableRamMb}/${h.totalRamMb}MB, Battery ${h.batteryPercent}%, Storage ${"%.1f".format(h.freeStorageGb)}GB"
                }
                "VOLUME_DELTA" -> {
                    val delta = step.param.toIntOrNull() ?: -10
                    deviceControl.adjustMediaVolume(delta)
                }
                "OPEN_APP" -> deviceControl.launchAppByNameOrPackage(step.param)
                "TOGGLE_TORCH" -> offlineEngine.toggleFlashlight(step.param.equals("on", true) || step.param.equals("true", true))
                "SPEAK" -> {
                    onSpeakStep(step.param)
                    true to "Spoke: \"${step.param.take(60)}\""
                }
                "CREATE_REMINDER" -> {
                    advancedRepo.upsertProductivityItem(
                        ProductivityItemEntity(
                            itemType = "REMINDER",
                            title = step.param.ifBlank { "Routine Reminder: ${routine.name}" },
                            priority = "MEDIUM"
                        )
                    )
                    true to "Created reminder: ${step.param}"
                }
                else -> true to "Logged note: ${step.param}"
            }
            if (!outcome.first) allOk = false
            logs.add("Step ${index + 1} [${step.type}]: ${outcome.second}")
        }

        val summary = if (allOk) "Executed ${steps.size} steps cleanly" else "Completed with warnings"
        advancedRepo.upsertRoutine(
            routine.copy(
                lastRunAt = System.currentTimeMillis(),
                lastOutcome = summary,
                runCount = routine.runCount + 1
            )
        )
        memoryRepo.logAudit(
            actor = "SmartRoutinesEngine",
            action = "RUN_ROUTINE",
            safetyLevel = 2,
            outcome = if (allOk) "SUCCESS" else "PARTIAL",
            details = "${routine.name}: $summary"
        )
        return RoutineExecutionReport(routine.id, routine.name, allOk, logs, summary)
    }
}

class RoutineScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = AuraDatabase.getInstance(applicationContext)
        val routineId = inputData.getLong("routine_id", -1L)
        val repo = AdvancedRepository(db)
        val memRepo = MemoryRepository(db)
        val routine = repo.getAllRoutines().firstOrNull { it.id == routineId } ?: return Result.success()
        if (!routine.enabled) return Result.success()

        val dc = DeviceControlEngine(applicationContext)
        val offline = OfflineEmergencyEngine(applicationContext, dc)
        val engine = SmartRoutinesEngine(applicationContext, repo, memRepo, dc, offline, PermissionGuard())
        engine.executeRoutine(routine, AutonomyPolicy(), isManualTrigger = false)
        return Result.success()
    }
}
