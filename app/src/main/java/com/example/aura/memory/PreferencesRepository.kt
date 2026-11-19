package com.example.aura.memory

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import com.example.ui.theme.AuraThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.auraPrefsDataStore by preferencesDataStore(name = "aura_settings_prefs")

data class AutonomyPolicy(
    val maxAutonomousSteps: Int = 25,
    val maxScheduledRunsPerHour: Int = 6,
    val scheduledTasksPaused: Boolean = false,
    val externalApiMasterEnabled: Boolean = true,
    val confirmBeforeMessages: Boolean = true,
    val confirmBeforeCalls: Boolean = true,
    val confirmBeforeAppInstalls: Boolean = true,
    val confirmBeforeGitHubPush: Boolean = true,
    val confirmBeforeCalendarWrite: Boolean = true,
    val confirmBeforeCloudSync: Boolean = true
)

data class AuraPreferences(
    val privateMode: Boolean = false,
    val memoryEnabled: Boolean = true,
    val preferredLanguage: String = "AUTO",
    val activeProviderId: String = "FIREBASE_GEMINI",
    val activeModelId: String = GEMINI_DEFAULT_MODEL,
    val activeVoiceName: String = "Kore",
    val themeMode: AuraThemeMode = AuraThemeMode.CYBER_OBSIDIAN,
    val backgroundEvolutionEnabled: Boolean = true,
    val offlineFallbackEnabled: Boolean = true,
    val autoFallbackProviders: Boolean = true,
    // Privacy & budget policies
    val intelligenceMode: String = "HYBRID", // "LOCAL_ONLY", "CLOUD_ONLY", "HYBRID"
    val freeOnlyMode: Boolean = true,
    val dailyBudgetUsd: Double = 1.0,
    val dailyTokenLimit: Int = 150_000,
    val redactSensitiveData: Boolean = true,
    val minimizeScreenSharing: Boolean = true,
    val dataRetentionDays: Int = 30,
    // Expansion Capabilities (#1, #7, #12, #16, #18, #19)
    val wakeWordEnabled: Boolean = false,
    val wakePhrase: String = "Hey AURA",
    val wakeBatterySaver: Boolean = true,
    val offlineEmergencyMode: Boolean = false,
    val forceUrduRtlPreview: Boolean = false,
    val cloudCompanionUrl: String = "",
    val localDeviceId: String = "",
    val deviceDisplayName: String = "Android Device",
    val syncConflictStrategy: String = "LAST_WRITE_WINS", // LAST_WRITE_WINS, KEEP_LOCAL, KEEP_REMOTE, MERGE_COLLECTIONS
    val historyRetentionDays: Int = 90,
    val maxHistoryEntries: Int = 1000,
    val recordSensitiveChatDetails: Boolean = false,
    // Always-Connected Global AI Agent Network
    val heartbeatIntervalMinutes: Int = 30,
    val backgroundNetworkSyncEnabled: Boolean = true,
    val discussionNotifFrequency: String = "REALTIME", // REALTIME, HOURLY_DIGEST, DAILY_DIGEST, CRITICAL_ONLY, MUTED
    val discussionNotifMinPriority: String = "NORMAL", // LOW, NORMAL, HIGH, CRITICAL
    val autoCouncilOnNewDiscovery: Boolean = true,
    val autonomyPolicy: AutonomyPolicy = AutonomyPolicy()
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ACTIVE_MODEL = stringPreferencesKey("active_model")
        val ACTIVE_VOICE = stringPreferencesKey("active_voice")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BG_EVOLUTION = booleanPreferencesKey("bg_evolution")
        val OFFLINE_FALLBACK = booleanPreferencesKey("offline_fallback")
        val AUTO_FALLBACK = booleanPreferencesKey("auto_fallback")
        val INTELLIGENCE_MODE = stringPreferencesKey("intelligence_mode")
        val FREE_ONLY_MODE = booleanPreferencesKey("free_only_mode")
        val DAILY_BUDGET_USD = doublePreferencesKey("daily_budget_usd")
        val DAILY_TOKEN_LIMIT = intPreferencesKey("daily_token_limit")
        val REDACT_SENSITIVE = booleanPreferencesKey("redact_sensitive")
        val MINIMIZE_SCREEN = booleanPreferencesKey("minimize_screen")
        val RETENTION_DAYS = intPreferencesKey("retention_days")

        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val WAKE_BATTERY_SAVER = booleanPreferencesKey("wake_battery_saver")
        val OFFLINE_EMERGENCY = booleanPreferencesKey("offline_emergency_mode")
        val FORCE_URDU_RTL = booleanPreferencesKey("force_urdu_rtl_preview")
        val CLOUD_COMPANION_URL = stringPreferencesKey("cloud_companion_url")
        val LOCAL_DEVICE_ID = stringPreferencesKey("local_device_id")
        val DEVICE_DISPLAY_NAME = stringPreferencesKey("device_display_name")
        val SYNC_CONFLICT_STRATEGY = stringPreferencesKey("sync_conflict_strategy")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val MAX_HISTORY_ENTRIES = intPreferencesKey("max_history_entries")
        val RECORD_SENSITIVE_CHAT = booleanPreferencesKey("record_sensitive_chat_details")
        val HEARTBEAT_INTERVAL_MIN = intPreferencesKey("network_heartbeat_interval_min")
        val BG_NETWORK_SYNC = booleanPreferencesKey("bg_network_sync_enabled")
        val DISC_NOTIF_FREQ = stringPreferencesKey("discussion_notif_frequency")
        val DISC_NOTIF_PRIORITY = stringPreferencesKey("discussion_notif_priority")
        val AUTO_COUNCIL_DISCOVERY = booleanPreferencesKey("auto_council_on_discovery")

        val AUTO_MAX_STEPS = intPreferencesKey("auto_max_steps")
        val AUTO_MAX_RUNS_HOUR = intPreferencesKey("auto_max_runs_hour")
        val AUTO_SCHEDULES_PAUSED = booleanPreferencesKey("auto_schedules_paused")
        val AUTO_EXTERNAL_API = booleanPreferencesKey("auto_external_api")
        val CONFIRM_MESSAGES = booleanPreferencesKey("confirm_messages")
        val CONFIRM_CALLS = booleanPreferencesKey("confirm_calls")
        val CONFIRM_INSTALLS = booleanPreferencesKey("confirm_installs")
        val CONFIRM_GITHUB = booleanPreferencesKey("confirm_github")
        val CONFIRM_CALENDAR = booleanPreferencesKey("confirm_calendar")
        val CONFIRM_SYNC = booleanPreferencesKey("confirm_sync")
    }

    val preferences: Flow<AuraPreferences> = context.auraPrefsDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: AuraThemeMode.CYBER_OBSIDIAN.name
        val theme = runCatching { AuraThemeMode.valueOf(themeName) }
            .getOrDefault(AuraThemeMode.CYBER_OBSIDIAN)
        AuraPreferences(
            privateMode = prefs[Keys.PRIVATE_MODE] ?: false,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "AUTO",
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER] ?: "FIREBASE_GEMINI",
            activeModelId = prefs[Keys.ACTIVE_MODEL] ?: GEMINI_DEFAULT_MODEL,
            activeVoiceName = prefs[Keys.ACTIVE_VOICE] ?: "Kore",
            themeMode = theme,
            backgroundEvolutionEnabled = prefs[Keys.BG_EVOLUTION] ?: true,
            offlineFallbackEnabled = prefs[Keys.OFFLINE_FALLBACK] ?: true,
            autoFallbackProviders = prefs[Keys.AUTO_FALLBACK] ?: true,
            intelligenceMode = prefs[Keys.INTELLIGENCE_MODE] ?: "HYBRID",
            freeOnlyMode = prefs[Keys.FREE_ONLY_MODE] ?: true,
            dailyBudgetUsd = prefs[Keys.DAILY_BUDGET_USD] ?: 1.0,
            dailyTokenLimit = prefs[Keys.DAILY_TOKEN_LIMIT] ?: 150_000,
            redactSensitiveData = prefs[Keys.REDACT_SENSITIVE] ?: true,
            minimizeScreenSharing = prefs[Keys.MINIMIZE_SCREEN] ?: true,
            dataRetentionDays = prefs[Keys.RETENTION_DAYS] ?: 30,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakePhrase = prefs[Keys.WAKE_PHRASE] ?: "Hey AURA",
            wakeBatterySaver = prefs[Keys.WAKE_BATTERY_SAVER] ?: true,
            offlineEmergencyMode = prefs[Keys.OFFLINE_EMERGENCY] ?: false,
            forceUrduRtlPreview = prefs[Keys.FORCE_URDU_RTL] ?: false,
            cloudCompanionUrl = prefs[Keys.CLOUD_COMPANION_URL] ?: "",
            localDeviceId = prefs[Keys.LOCAL_DEVICE_ID] ?: "",
            deviceDisplayName = prefs[Keys.DEVICE_DISPLAY_NAME] ?: android.os.Build.MODEL.ifBlank { "Android Device" },
            syncConflictStrategy = prefs[Keys.SYNC_CONFLICT_STRATEGY] ?: "LAST_WRITE_WINS",
            historyRetentionDays = prefs[Keys.HISTORY_RETENTION_DAYS] ?: 90,
            maxHistoryEntries = prefs[Keys.MAX_HISTORY_ENTRIES] ?: 1000,
            recordSensitiveChatDetails = prefs[Keys.RECORD_SENSITIVE_CHAT] ?: false,
            heartbeatIntervalMinutes = prefs[Keys.HEARTBEAT_INTERVAL_MIN] ?: 30,
            backgroundNetworkSyncEnabled = prefs[Keys.BG_NETWORK_SYNC] ?: true,
            discussionNotifFrequency = prefs[Keys.DISC_NOTIF_FREQ] ?: "REALTIME",
            discussionNotifMinPriority = prefs[Keys.DISC_NOTIF_PRIORITY] ?: "NORMAL",
            autoCouncilOnNewDiscovery = prefs[Keys.AUTO_COUNCIL_DISCOVERY] ?: true,
            autonomyPolicy = AutonomyPolicy(
                maxAutonomousSteps = prefs[Keys.AUTO_MAX_STEPS] ?: 25,
                maxScheduledRunsPerHour = prefs[Keys.AUTO_MAX_RUNS_HOUR] ?: 6,
                scheduledTasksPaused = prefs[Keys.AUTO_SCHEDULES_PAUSED] ?: false,
                externalApiMasterEnabled = prefs[Keys.AUTO_EXTERNAL_API] ?: true,
                confirmBeforeMessages = prefs[Keys.CONFIRM_MESSAGES] ?: true,
                confirmBeforeCalls = prefs[Keys.CONFIRM_CALLS] ?: true,
                confirmBeforeAppInstalls = prefs[Keys.CONFIRM_INSTALLS] ?: true,
                confirmBeforeGitHubPush = prefs[Keys.CONFIRM_GITHUB] ?: true,
                confirmBeforeCalendarWrite = prefs[Keys.CONFIRM_CALENDAR] ?: true,
                confirmBeforeCloudSync = prefs[Keys.CONFIRM_SYNC] ?: true
            )
        )
    }

    suspend fun ensureLocalDeviceId(): String {
        var existing = ""
        context.auraPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_DEVICE_ID]
            if (current.isNullOrBlank()) {
                val created = "aura-dev-${UUID.randomUUID().toString().take(8)}"
                prefs[Keys.LOCAL_DEVICE_ID] = created
                existing = created
            } else {
                existing = current
            }
        }
        return existing
    }

    suspend fun setPrivateMode(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.PRIVATE_MODE] = enabled } }
    suspend fun setMemoryEnabled(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled } }
    suspend fun setPreferredLanguage(lang: String) { context.auraPrefsDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = lang } }
    suspend fun setActiveProviderAndModel(providerId: String, modelId: String) {
        context.auraPrefsDataStore.edit {
            it[Keys.ACTIVE_PROVIDER] = providerId
            it[Keys.ACTIVE_MODEL] = modelId
        }
    }
    suspend fun setActiveVoice(voiceName: String) { context.auraPrefsDataStore.edit { it[Keys.ACTIVE_VOICE] = voiceName } }
    suspend fun setThemeMode(mode: AuraThemeMode) { context.auraPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setBackgroundEvolution(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.BG_EVOLUTION] = enabled } }
    suspend fun setAutoFallback(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.AUTO_FALLBACK] = enabled } }
    suspend fun setOfflineFallback(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.OFFLINE_FALLBACK] = enabled } }
    suspend fun setIntelligenceMode(mode: String) { context.auraPrefsDataStore.edit { it[Keys.INTELLIGENCE_MODE] = mode } }
    suspend fun setFreeOnlyMode(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.FREE_ONLY_MODE] = enabled } }
    suspend fun setDailyBudgetUsd(usd: Double) { context.auraPrefsDataStore.edit { it[Keys.DAILY_BUDGET_USD] = usd } }
    suspend fun setDailyTokenLimit(limit: Int) { context.auraPrefsDataStore.edit { it[Keys.DAILY_TOKEN_LIMIT] = limit } }
    suspend fun setRedactSensitiveData(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.REDACT_SENSITIVE] = enabled } }
    suspend fun setMinimizeScreenSharing(enabled: Boolean) { context.auraPrefsDataStore.edit { it[Keys.MINIMIZE_SCREEN] = enabled } }
    suspend fun setDataRetentionDays(days: Int) { context.auraPrefsDataStore.edit { it[Keys.RETENTION_DAYS] = days } }

    suspend fun setWakeWordConfig(enabled: Boolean, phrase: String, batterySaver: Boolean) {
        context.auraPrefsDataStore.edit {
            it[Keys.WAKE_WORD_ENABLED] = enabled
            it[Keys.WAKE_PHRASE] = phrase.trim().ifBlank { "Hey AURA" }
            it[Keys.WAKE_BATTERY_SAVER] = batterySaver
        }
    }

    suspend fun setOfflineEmergencyMode(enabled: Boolean) {
        context.auraPrefsDataStore.edit { it[Keys.OFFLINE_EMERGENCY] = enabled }
    }

    suspend fun setForceUrduRtlPreview(enabled: Boolean) {
        context.auraPrefsDataStore.edit { it[Keys.FORCE_URDU_RTL] = enabled }
    }

    suspend fun setCloudCompanionConfig(url: String, deviceDisplayName: String, conflictStrategy: String) {
        context.auraPrefsDataStore.edit {
            it[Keys.CLOUD_COMPANION_URL] = url.trim()
            it[Keys.DEVICE_DISPLAY_NAME] = deviceDisplayName.trim().ifBlank { "Android Device" }
            it[Keys.SYNC_CONFLICT_STRATEGY] = conflictStrategy
        }
    }

    suspend fun updateAutonomyPolicy(policy: AutonomyPolicy) {
        context.auraPrefsDataStore.edit {
            it[Keys.AUTO_MAX_STEPS] = policy.maxAutonomousSteps.coerceIn(1, 100)
            it[Keys.AUTO_MAX_RUNS_HOUR] = policy.maxScheduledRunsPerHour.coerceIn(1, 60)
            it[Keys.AUTO_SCHEDULES_PAUSED] = policy.scheduledTasksPaused
            it[Keys.AUTO_EXTERNAL_API] = policy.externalApiMasterEnabled
            it[Keys.CONFIRM_MESSAGES] = policy.confirmBeforeMessages
            it[Keys.CONFIRM_CALLS] = policy.confirmBeforeCalls
            it[Keys.CONFIRM_INSTALLS] = policy.confirmBeforeAppInstalls
            it[Keys.CONFIRM_GITHUB] = policy.confirmBeforeGitHubPush
            it[Keys.CONFIRM_CALENDAR] = policy.confirmBeforeCalendarWrite
            it[Keys.CONFIRM_SYNC] = policy.confirmBeforeCloudSync
        }
    }

    suspend fun setHistoryRetentionPolicy(days: Int, maxEntries: Int, recordSensitive: Boolean) {
        context.auraPrefsDataStore.edit {
            it[Keys.HISTORY_RETENTION_DAYS] = days.coerceIn(1, 3650)
            it[Keys.MAX_HISTORY_ENTRIES] = maxEntries.coerceIn(50, 10000)
            it[Keys.RECORD_SENSITIVE_CHAT] = recordSensitive
        }
    }

    suspend fun setGlobalNetworkSettings(
        heartbeatMinutes: Int,
        backgroundSyncEnabled: Boolean,
        notifFrequency: String,
        notifMinPriority: String,
        autoCouncilOnDiscovery: Boolean
    ) {
        context.auraPrefsDataStore.edit {
            it[Keys.HEARTBEAT_INTERVAL_MIN] = heartbeatMinutes.coerceIn(15, 720)
            it[Keys.BG_NETWORK_SYNC] = backgroundSyncEnabled
            it[Keys.DISC_NOTIF_FREQ] = notifFrequency
            it[Keys.DISC_NOTIF_PRIORITY] = notifMinPriority
            it[Keys.AUTO_COUNCIL_DISCOVERY] = autoCouncilOnDiscovery
        }
    }
}
