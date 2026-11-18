package com.example.aura.automation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import java.net.URLEncoder

data class InstalledAppSummary(
    val label: String,
    val packageName: String
)

data class DeviceHealthSnapshot(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Double,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val mediaVolumePercent: Int,
    val canWriteSystemSettings: Boolean,
    val offlineLlmEligible: Boolean,
    val offlineLlmReason: String
)

class DeviceControlEngine(private val context: Context) {

    fun listLaunchableApps(): List<InstalledAppSummary> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0)
        } else null
        val activities = if (flags != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, flags)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return activities
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(context.packageManager)?.toString().orEmpty()
                if (pkg == context.packageName) null else InstalledAppSummary(label.ifBlank { pkg }, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchAppByNameOrPackage(query: String): Pair<Boolean, String> {
        val q = query.trim().lowercase()
        val known = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox",
            "facebook" to "com.facebook.katana",
            "tiktok" to "com.zhiliaoapp.musically"
        )
        val installed = listLaunchableApps()
        val matchedPackage = known[q]
            ?: installed.firstOrNull { it.packageName.equals(q, ignoreCase = true) }?.packageName
            ?: installed.firstOrNull { it.label.lowercase().contains(q) }?.packageName

        if (matchedPackage == null) {
            return false to "App '$query' not found on device."
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(matchedPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false to "No launcher activity found for $matchedPackage."

        return runCatching {
            context.startActivity(launchIntent)
            true to "Launched $matchedPackage."
        }.getOrElse {
            false to "Failed to launch $matchedPackage: ${it.localizedMessage}"
        }
    }

    fun searchWeb(query: String): Pair<Boolean, String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val uri = Uri.parse("https://www.google.com/search?q=$encoded")
        return openUri(uri, "Web search: $query")
    }

    fun searchYouTube(query: String): Pair<Boolean, String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        return openUri(uri, "YouTube search: $query")
    }

    fun searchGoogleMaps(destination: String): Pair<Boolean, String> {
        val encoded = URLEncoder.encode(destination, "UTF-8")
        val uri = Uri.parse("geo:0,0?q=$encoded")
        return openUri(uri, "Maps search: $destination")
    }

    fun openSystemCamera(): Pair<Boolean, String> {
        return runCatching {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened system camera."
        }.getOrElse { false to "Could not open camera: ${it.localizedMessage}" }
    }

    fun openAccessibilitySettings(): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun openNotificationListenerSettings(): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun openDeviceSettings(action: String = Settings.ACTION_SETTINGS): Boolean = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun adjustMediaVolume(deltaPercent: Int): Pair<Boolean, String> {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = ((deltaPercent / 100f) * max).toInt().let {
            if (it == 0 && deltaPercent != 0) if (deltaPercent > 0) 1 else -1 else it
        }
        val next = (current + step).coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
        val pct = (next * 100) / max
        return true to "Media volume set to $pct% ($next/$max)."
    }

    fun setBrightnessPercent(percent: Int): Pair<Boolean, String> {
        val clamped = percent.coerceIn(5, 100)
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
            return false to "Grant 'Modify system settings' permission in Settings to control brightness."
        }
        val value = (clamped * 255) / 100
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return true to "Screen brightness set to $clamped%."
    }

    fun readDeviceHealth(): DeviceHealthSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val totalMb = memInfo.totalMem / (1024L * 1024L)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = (stat.availableBytes.toDouble()) / (1024.0 * 1024.0 * 1024.0)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val isCharging = bm.isCharging

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = (curVol * 100) / maxVol

        val eligible = totalMb >= 3500 && freeStorageGb >= 2.5
        val reason = if (eligible) {
            "Eligible for small quantized local models (1B-3B Q4): ${availMb}MB free RAM, ${"%.1f".format(freeStorageGb)}GB free storage."
        } else {
            "Insufficient RAM/storage for multi-GB local GGUF (${totalMb}MB RAM, ${"%.1f".format(freeStorageGb)}GB free). Using lightweight offline rule engine + cloud Gemini."
        }

        return DeviceHealthSnapshot(
            availableRamMb = availMb,
            totalRamMb = totalMb,
            freeStorageGb = freeStorageGb,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            mediaVolumePercent = volPct,
            canWriteSystemSettings = Settings.System.canWrite(context),
            offlineLlmEligible = eligible,
            offlineLlmReason = reason
        )
    }

    private fun openUri(uri: Uri, label: String): Pair<Boolean, String> {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Opened $label."
        }.getOrElse {
            false to "Failed to open $label: ${it.localizedMessage}"
        }
    }
}
