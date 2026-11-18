package com.example.aura.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long
)

object NotificationBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun pushNotification(item: CapturedNotification) {
        val updated = (listOf(item) + _notifications.value.filterNot { it.key == item.key }).take(50)
        _notifications.value = updated
    }

    fun removeNotification(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
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
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val pkg = sbn.packageName.orEmpty()
        val appLabel = runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))

        NotificationBridge.pushNotification(
            CapturedNotification(
                key = sbn.key,
                packageName = pkg,
                appLabel = appLabel,
                title = title,
                text = text,
                postTime = sbn.postTime
            )
        )
    }
}
