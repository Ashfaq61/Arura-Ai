package com.example.aura.automation

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val supportsDirectReply: Boolean = false,
    val replyActionLabel: String = ""
)

object NotificationBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    // Live OS Notification.Action map for user-approved direct inline replies (not persisted to disk)
    private val liveReplyActions = ConcurrentHashMap<String, Notification.Action>()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun pushNotification(item: CapturedNotification, replyAction: Notification.Action? = null) {
        if (replyAction != null) {
            liveReplyActions[item.key] = replyAction
        }
        val updated = (listOf(item) + _notifications.value.filterNot { it.key == item.key }).take(50)
        _notifications.value = updated
    }

    fun removeNotification(key: String) {
        liveReplyActions.remove(key)
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun getReplyAction(key: String): Notification.Action? = liveReplyActions[key]

    fun clearAll() {
        liveReplyActions.clear()
        _notifications.value = emptyList()
    }
}

class AuraNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setConnected(true)
        runCatching {
            activeNotifications?.forEach { sbn -> record(sbn) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) record(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) NotificationBridge.removeNotification(sbn.key)
    }

    private fun record(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val pkg = sbn.packageName.orEmpty()
        val appLabel = runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))

        val replyAction = notif.actions?.firstOrNull { action ->
            val inputs = action.remoteInputs
            action.actionIntent != null && inputs != null && inputs.isNotEmpty()
        }

        NotificationBridge.pushNotification(
            item = CapturedNotification(
                key = sbn.key,
                packageName = pkg,
                appLabel = appLabel,
                title = title,
                text = text,
                postTime = sbn.postTime,
                supportsDirectReply = replyAction != null,
                replyActionLabel = replyAction?.title?.toString().orEmpty()
            ),
            replyAction = replyAction
        )
    }

    companion object {
        fun sendDirectReply(context: android.content.Context, notificationKey: String, replyText: String): Pair<Boolean, String> {
            val action = NotificationBridge.getReplyAction(notificationKey)
                ?: return false to "Live inline RemoteInput unavailable for this notification."
            val remoteInputs = action.remoteInputs
            if (remoteInputs.isNullOrEmpty() || action.actionIntent == null) {
                return false to "Notification action has no RemoteInput reply channel."
            }
            return runCatching {
                val intent = Intent()
                val bundle = Bundle()
                remoteInputs.forEach { ri -> bundle.putCharSequence(ri.resultKey, replyText) }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                action.actionIntent.send(context, 0, intent)
                true to "Approved direct reply sent via Android Notification RemoteInput."
            }.getOrElse { err ->
                false to "Direct reply failed: ${err.localizedMessage ?: "OS rejected PendingIntent"}"
            }
        }
    }
}
