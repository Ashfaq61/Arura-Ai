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

private val Context.auraPrefsDataStore by preferencesDataStore(name = "aura_settings_prefs")

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
    // Addendum privacy & budget policies
    val intelligenceMode: String = "HYBRID", // "LOCAL_ONLY", "CLOUD_ONLY", "HYBRID"
    val freeOnlyMode: Boolean = true,
    val dailyBudgetUsd: Double = 1.0,
    val dailyTokenLimit: Int = 150_000,
    val redactSensitiveData: Boolean = true,
    val minimizeScreenSharing: Boolean = true,
    val dataRetentionDays: Int = 30
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
            dataRetentionDays = prefs[Keys.RETENTION_DAYS] ?: 30
        )
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
}
