package com.example.aura.productivity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aura.knowledge.KnowledgeGraphEngine
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.ProductivityItemEntity
import java.util.concurrent.TimeUnit

data class DeviceCalendarEventSummary(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String
)

/**
 * Personal Productivity Agent: manages Reminders (with WorkManager notifications),
 * Tasks, Notes, and real Android CalendarContract read/write + intent fallbacks.
 */
class ProductivityAgentEngine(
    private val context: Context,
    private val advancedRepo: AdvancedRepository,
    private val knowledgeGraph: KnowledgeGraphEngine
) {
    fun hasReadCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWriteCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun createItem(
        itemType: String,
        title: String,
        details: String = "",
        dueAtMillis: Long? = null,
        priority: String = "MEDIUM",
        linkToKnowledgeGraph: Boolean = true
    ): Long {
        val tag = if (itemType == "REMINDER" && dueAtMillis != null) {
            "aura_rem_${System.currentTimeMillis()}"
        } else ""
        val id = advancedRepo.upsertProductivityItem(
            ProductivityItemEntity(
                itemType = itemType.uppercase(),
                title = title.trim(),
                details = details.trim(),
                dueAtMillis = dueAtMillis,
                priority = priority.uppercase(),
                workTag = tag
            )
        )
        if (tag.isNotBlank() && dueAtMillis != null) {
            scheduleReminderNotification(id, title.trim(), details.trim(), dueAtMillis, tag)
        }
        if (linkToKnowledgeGraph) {
            knowledgeGraph.addNodeAndAutoLink(
                nodeType = if (itemType == "NOTE") "NOTE" else "TASK",
                title = title.trim(),
                summary = details.ifBlank { "Priority: $priority" },
                tagsCsv = "${itemType.lowercase()},${priority.lowercase()}",
                sourceRef = "productivity:$id"
            )
        }
        return id
    }

    suspend fun toggleCompleted(item: ProductivityItemEntity) {
        advancedRepo.upsertProductivityItem(
            item.copy(completed = !item.completed, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteItem(item: ProductivityItemEntity) {
        if (item.workTag.isNotBlank()) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(item.workTag) }
        }
        advancedRepo.deleteProductivityItem(item.id)
    }

    private fun scheduleReminderNotification(
        itemId: Long,
        title: String,
        details: String,
        dueAtMillis: Long,
        workTag: String
    ) {
        val delayMs = (dueAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val req = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .addTag("aura_reminders")
            .setInputData(workDataOf("item_id" to itemId, "title" to title, "details" to details))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workTag, ExistingWorkPolicy.REPLACE, req)
    }

    fun readUpcomingDeviceCalendarEvents(daysAhead: Int = 14): Result<List<DeviceCalendarEventSummary>> {
        if (!hasReadCalendarPermission()) {
            return Result.failure(SecurityException("READ_CALENDAR permission required to read device calendar."))
        }
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(daysAhead.toLong())
        return runCatching {
            val events = mutableListOf<DeviceCalendarEventSummary>()
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val args = arrayOf(now.toString(), end.toString())
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val idxTitle = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val idxStart = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val idxEnd = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val idxLoc = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                while (cursor.moveToNext() && events.size < 25) {
                    events.add(
                        DeviceCalendarEventSummary(
                            id = cursor.getLong(idxId),
                            title = cursor.getString(idxTitle) ?: "(No Title)",
                            startTimeMillis = cursor.getLong(idxStart),
                            endTimeMillis = cursor.getLong(idxEnd),
                            location = cursor.getString(idxLoc).orEmpty()
                        )
                    )
                }
            }
            events
        }
    }

    fun insertDeviceCalendarEvent(
        title: String,
        description: String,
        startMillis: Long,
        durationMinutes: Int = 30
    ): Pair<Boolean, String> {
        if (hasReadCalendarPermission() && hasWriteCalendarPermission()) {
            val calId = findPrimaryCalendarId()
            if (calId != null) {
                val inserted = runCatching {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, startMillis + TimeUnit.MINUTES.toMillis(durationMinutes.toLong()))
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.DESCRIPTION, description)
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    }
                    context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                }.getOrNull()
                if (inserted != null) {
                    return true to "Saved event '$title' directly to Android Calendar."
                }
            }
        }
        // Intent fallback so users can still add to Calendar with zero extra friction
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + TimeUnit.MINUTES.toMillis(durationMinutes.toLong()))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened Calendar app to confirm event '$title'."
        }.getOrElse { err ->
            false to "Calendar unavailable: ${err.localizedMessage}"
        }
    }

    private fun findPrimaryCalendarId(): Long? {
        return runCatching {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.getOrNull()
    }
}

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "AURA Reminder"
        val details = inputData.getString("details").orEmpty()
        val id = inputData.getLong("item_id", System.currentTimeMillis()).toInt()
        postReminderNotification(applicationContext, id, title, details)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "aura_reminders_channel"

        fun postReminderNotification(context: Context, id: Int, title: String, details: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "AURA Reminders & Schedules", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(channel)
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(details.ifBlank { "Scheduled reminder triggered by AURA AI" })
                .setAutoCancel(true)
                .build()
            nm.notify(id, notif)
        }
    }
}
