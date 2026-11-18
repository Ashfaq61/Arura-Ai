package com.example.aura.evolution

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.ScheduledTaskEntity
import com.example.aura.research.GitHubAndHfResearchClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ScheduledWorkflowWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val db = AuraDatabase.getInstance(applicationContext)
        val repo = MemoryRepository(db)
        val all = repo.scheduledTasks.first()
        val targets = if (taskId > 0) all.filter { it.id == taskId } else all.filter { it.enabled }

        val deviceControl = DeviceControlEngine(applicationContext)
        val research = GitHubAndHfResearchClient()

        for (task in targets) {
            if (!task.enabled) continue
            val outcome = when (task.taskType) {
                "HEALTH_CHECK" -> {
                    val h = deviceControl.readDeviceHealth()
                    "RAM ${h.availableRamMb}/${h.totalRamMb}MB • Storage ${"%.1f".format(h.freeStorageGb)}GB • Battery ${h.batteryPercent}%"
                }
                "GITHUB_WATCH", "MODEL_RELEASE" -> {
                    val res = research.searchGitHubProjects(task.targetQuery.ifBlank { "android ai agent" }, deviceControl.readDeviceHealth().totalRamMb)
                    res.fold(
                        onSuccess = { list ->
                            repo.upsertResearchProjects(list)
                            "Synced ${list.size} repositories (${list.firstOrNull()?.name ?: "none"})"
                        },
                        onFailure = { "Deferred (${it.localizedMessage})" }
                    )
                }
                else -> {
                    val h = deviceControl.readDeviceHealth()
                    "Briefing ready (${task.name}) • Battery ${h.batteryPercent}% • ${h.availableRamMb}MB free RAM"
                }
            }

            repo.upsertScheduledTask(
                task.copy(lastRunAt = System.currentTimeMillis(), lastOutcome = outcome)
            )
            postNotification(task.id.toInt(), task.name, outcome)
        }
        return Result.success()
    }

    private fun postNotification(notifId: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "AURA Autonomous Schedules", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AURA: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        nm.notify(4000 + notifId, notif)
    }

    companion object {
        private const val CHANNEL_ID = "aura_scheduled_workflows"
        private const val KEY_TASK_ID = "task_id"

        fun syncSchedule(context: Context, task: ScheduledTaskEntity) {
            val wm = WorkManager.getInstance(context)
            if (!task.enabled) {
                wm.cancelUniqueWork(task.workTag)
                return
            }
            val hours = task.intervalHours.coerceAtLeast(1).toLong()
            val input = Data.Builder().putLong(KEY_TASK_ID, task.id).build()
            val req = PeriodicWorkRequestBuilder<ScheduledWorkflowWorker>(hours, TimeUnit.HOURS)
                .setInputData(input)
                .addTag(task.workTag)
                .build()
            wm.enqueueUniquePeriodicWork(task.workTag, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(context: Context, taskId: Long) {
            val input = Data.Builder().putLong(KEY_TASK_ID, taskId).build()
            val req = OneTimeWorkRequestBuilder<ScheduledWorkflowWorker>()
                .setInputData(input)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun cancelSchedule(context: Context, workTag: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workTag)
        }
    }
}
