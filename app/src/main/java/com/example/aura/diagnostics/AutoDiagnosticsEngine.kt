package com.example.aura.diagnostics

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.CrashReportEntity
import com.example.aura.memory.MemoryRepository
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

data class DiagnosticsSummaryReport(
    val overallStatus: String, // HEALTHY, ATTENTION, DEGRADED
    val pendingWorkCount: Int,
    val failedWorkCount: Int,
    val unresolvedCrashCount: Int,
    val networkOnline: Boolean,
    val ramSummary: String,
    val storageSummary: String,
    val batterySummary: String,
    val issuesDetected: List<String>,
    val lastRecoveryAction: String = ""
)

/**
 * Installs an uncaught-exception disk trap so crashes survive process termination,
 * ingests crash logs into Room on startup, monitors WorkManager + memory + storage +
 * battery + network, and performs safe non-destructive auto-repairs.
 */
class AutoDiagnosticsEngine(
    private val context: Context,
    private val advancedRepo: AdvancedRepository,
    private val memoryRepo: MemoryRepository,
    private val deviceControl: DeviceControlEngine,
    private val selfHealing: SelfHealingEngine
) {
    private val pendingCrashFile = File(context.filesDir, "aura_pending_crash.json")

    fun installUncaughtCrashTrap() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is AuraCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(AuraCrashHandler(pendingCrashFile, previous))
    }

    suspend fun ingestPendingCrashIfAny(): CrashReportEntity? {
        if (!pendingCrashFile.exists()) return null
        val loaded = runCatching {
            val raw = pendingCrashFile.readText(Charsets.UTF_8)
            val json = JSONObject(raw)
            CrashReportEntity(
                threadName = json.optString("thread", "unknown"),
                exceptionType = json.optString("type", "Exception"),
                message = json.optString("message", ""),
                stackTrace = json.optString("stack", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }.getOrNull()
        pendingCrashFile.delete()
        if (loaded != null) {
            val id = advancedRepo.insertCrashReport(loaded)
            return loaded.copy(id = id)
        }
        return null
    }

    suspend fun runFullDiagnostics(): DiagnosticsSummaryReport {
        val health = deviceControl.readDeviceHealth()
        val netOnline = selfHealing.isNetworkOnline()
        val crashes = advancedRepo.getAllCrashReports().filter { !it.resolved }

        var runningOrEnqueued = 0
        var failedWork = 0
        runCatching {
            val wm = WorkManager.getInstance(context)
            val list = wm.getWorkInfosByTag("aura_routines").get(1, TimeUnit.SECONDS).orEmpty() +
                wm.getWorkInfosByTag("aura_reminders").get(1, TimeUnit.SECONDS).orEmpty()
            runningOrEnqueued = list.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            failedWork = list.count { it.state == WorkInfo.State.FAILED }
        }

        val issues = mutableListOf<String>()
        if (crashes.isNotEmpty()) issues.add("${crashes.size} unresolved crash log(s) recorded.")
        if (failedWork > 0) issues.add("$failedWork background WorkManager job(s) failed.")
        if (!netOnline) issues.add("Device offline — Offline Emergency fallback active.")
        if (health.availableRamMb < 500) issues.add("Low free RAM (${health.availableRamMb}MB).")
        if (health.freeStorageGb < 1.0) issues.add("Low free storage (${"%.2f".format(health.freeStorageGb)}GB).")
        if (health.batteryPercent in 1..15 && !health.isCharging) issues.add("Battery critical (${health.batteryPercent}%).")

        val status = when {
            crashes.isNotEmpty() || health.availableRamMb < 350 -> "ATTENTION"
            issues.isNotEmpty() -> "DEGRADED"
            else -> "HEALTHY"
        }

        return DiagnosticsSummaryReport(
            overallStatus = status,
            pendingWorkCount = runningOrEnqueued,
            failedWorkCount = failedWork,
            unresolvedCrashCount = crashes.size,
            networkOnline = netOnline,
            ramSummary = "${health.availableRamMb}/${health.totalRamMb} MB free",
            storageSummary = "${"%.2f".format(health.freeStorageGb)} GB free",
            batterySummary = "${health.batteryPercent}% ${if (health.isCharging) "(Charging)" else ""}",
            issuesDetected = issues
        )
    }

    suspend fun executeSafeRecovery(): String {
        val actions = mutableListOf<String>()
        actions.add(selfHealing.pruneCachesOnLowMemory())

        val stuckWorkflows = advancedRepo.getAllWorkflows().filter { !it.lastRunSuccess }
        if (stuckWorkflows.isNotEmpty()) {
            actions.add("Flagged ${stuckWorkflows.size} previously failed workflow(s) for review.")
        }

        runCatching { WorkManager.getInstance(context).pruneWork() }
        actions.add("Pruned completed WorkManager history.")

        val unresolved = advancedRepo.getAllCrashReports().filter { !it.resolved }
        unresolved.forEach { advancedRepo.resolveCrashReport(it.id) }
        if (unresolved.isNotEmpty()) {
            actions.add("Archived ${unresolved.size} resolved crash report(s).")
        }

        val summary = actions.joinToString(" | ")
        memoryRepo.logAudit("AutoDiagnosticsEngine", "SAFE_RECOVERY", 1, "SUCCESS", summary)
        return summary
    }

    private class AuraCrashHandler(
        private val targetFile: File,
        private val delegate: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val json = JSONObject().apply {
                    put("thread", t.name)
                    put("type", e.javaClass.name)
                    put("message", e.message.orEmpty())
                    put("stack", sw.toString().take(3000))
                    put("timestamp", System.currentTimeMillis())
                }
                targetFile.writeText(json.toString(), Charsets.UTF_8)
            }
            delegate?.uncaughtException(t, e)
        }
    }
}
