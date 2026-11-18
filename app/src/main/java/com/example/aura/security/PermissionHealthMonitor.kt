package com.example.aura.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.aura.automation.AccessibilityBridge
import com.example.aura.automation.NotificationBridge

data class PermissionHealthItem(
    val id: String,
    val title: String,
    val isGranted: Boolean,
    val importance: String, // CRITICAL, RECOMMENDED, OPTIONAL
    val whyNeeded: String,
    val runtimePermissionName: String? = null,
    val settingsAction: String? = null
)

data class PermissionHealthReport(
    val scorePercent: Int,
    val grantedCount: Int,
    val totalCount: Int,
    val items: List<PermissionHealthItem>
)

/**
 * Inspects Android runtime permissions, special OS bindings (Accessibility, Notification Listener),
 * Unknown Sources install allowance, Write Settings, and Battery Optimization health.
 */
class PermissionHealthMonitor(private val context: Context) {

    fun inspectAll(): PermissionHealthReport {
        val items = listOf(
            checkRuntime(
                id = "perm_mic",
                title = "Microphone (RECORD_AUDIO)",
                permission = Manifest.permission.RECORD_AUDIO,
                importance = "CRITICAL",
                whyNeeded = "Required for Voice Orb speech recognition and optional Wake Word ('Hey AURA')."
            ),
            checkRuntime(
                id = "perm_camera",
                title = "Camera (CAMERA)",
                permission = Manifest.permission.CAMERA,
                importance = "RECOMMENDED",
                whyNeeded = "Required for Live CameraX Vision (OCR, Object Detection, Document Scan, Scene Q&A)."
            ),
            PermissionHealthItem(
                id = "perm_notifications",
                title = "Post Notifications (Android 13+)",
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isRuntimeGranted(Manifest.permission.POST_NOTIFICATIONS)
                } else true,
                importance = "RECOMMENDED",
                whyNeeded = "Delivers scheduled routine outputs and Personal Productivity reminders.",
                runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            ),
            checkRuntime(
                id = "perm_calendar_read",
                title = "Calendar Access (READ/WRITE_CALENDAR)",
                permission = Manifest.permission.READ_CALENDAR,
                importance = "OPTIONAL",
                whyNeeded = "Lets Personal Productivity Agent summarize & schedule events cleanly."
            ),
            checkRuntime(
                id = "perm_phone",
                title = "Phone State & Dialer (CALL_PHONE)",
                permission = Manifest.permission.READ_PHONE_STATE,
                importance = "OPTIONAL",
                whyNeeded = "Monitors active call state for Phone Call Agent & confirmed SOS shortcuts."
            ),
            PermissionHealthItem(
                id = "svc_accessibility",
                title = "Accessibility Service (Computer-Use)",
                isGranted = AccessibilityBridge.isServiceConnected.value,
                importance = "RECOMMENDED",
                whyNeeded = "Enables numbered UI reading, layout drift checks, and user-approved gestures.",
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ),
            PermissionHealthItem(
                id = "svc_notification_listener",
                title = "Notification Listener Service",
                isGranted = NotificationBridge.isConnected.value,
                importance = "RECOMMENDED",
                whyNeeded = "Powers Intelligent Notification Center categorization, digest, and quick replies.",
                settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ),
            PermissionHealthItem(
                id = "spec_install_unknown",
                title = "Install Unknown Apps (Self-Update)",
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
                } else true,
                importance = "OPTIONAL",
                whyNeeded = "Allows AURA to install user-approved GitHub APK self-updates & rollbacks.",
                settingsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                } else {
                    Settings.ACTION_SECURITY_SETTINGS
                }
            ),
            PermissionHealthItem(
                id = "spec_write_settings",
                title = "Modify System Settings (Brightness)",
                isGranted = Settings.System.canWrite(context),
                importance = "OPTIONAL",
                whyNeeded = "Allows Device Control & Smart Routines to adjust screen brightness.",
                settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS
            ),
            PermissionHealthItem(
                id = "spec_battery_opt",
                title = "Unrestricted Battery / Background Health",
                isGranted = runCatching {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                    } else true
                }.getOrDefault(true),
                importance = "OPTIONAL",
                whyNeeded = "Helps WorkManager routines and reminders fire on time even during Doze mode.",
                settingsAction = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            )
        )

        val granted = items.count { it.isGranted }
        val pct = if (items.isEmpty()) 100 else (granted * 100) / items.size
        return PermissionHealthReport(pct, granted, items.size, items)
    }

    fun openSettingsForItem(item: PermissionHealthItem): Boolean {
        val action = item.settingsAction ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val intent = when (action) {
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> Intent(action, Uri.parse("package:${context.packageName}"))
            else -> Intent(action)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
        }
    }

    private fun checkRuntime(
        id: String,
        title: String,
        permission: String,
        importance: String,
        whyNeeded: String
    ) = PermissionHealthItem(
        id = id,
        title = title,
        isGranted = isRuntimeGranted(permission),
        importance = importance,
        whyNeeded = whyNeeded,
        runtimePermissionName = permission,
        settingsAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )

    private fun isRuntimeGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
