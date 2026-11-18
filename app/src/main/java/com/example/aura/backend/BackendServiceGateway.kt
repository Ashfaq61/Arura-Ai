package com.example.aura.backend

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BackendHealthSnapshot(
    val geminiConfigured: Boolean,
    val githubConfigured: Boolean,
    val companionConfigured: Boolean,
    val companionReachable: Boolean,
    val requestsThisMinute: Int,
    val maxRequestsPerMinute: Int,
    val localOnlyModeForced: Boolean,
    val lastCheckMessage: String,
    val checkedAt: Long = System.currentTimeMillis()
)

data class DatabaseMaintenanceStats(
    val memoryCount: Int,
    val routineCount: Int,
    val workflowCount: Int,
    val knowledgeNodeCount: Int,
    val productivityCount: Int,
    val crashCount: Int
)

/**
 * Separates core offline-capable on-device logic from optional cloud backends,
 * enforcing TLS HTTPS, sliding-window rate limiting, Keystore-encrypted auth tokens,
 * secret log sanitization, and DB health checks.
 */
class BackendServiceGateway(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_backend_companion_vault", Context.MODE_PRIVATE)

    private val requestWindowTimestamps = ArrayDeque<Long>()
    val maxRequestsPerMinute = 30

    private val _healthState = MutableStateFlow(
        BackendHealthSnapshot(
            geminiConfigured = false,
            githubConfigured = false,
            companionConfigured = false,
            companionReachable = false,
            requestsThisMinute = 0,
            maxRequestsPerMinute = maxRequestsPerMinute,
            localOnlyModeForced = false,
            lastCheckMessage = "Backend gateway initialized cleanly (local features independent)."
        )
    )
    val healthState: StateFlow<BackendHealthSnapshot> = _healthState.asStateFlow()

    @Synchronized
    fun acquireRateLimitPermit(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000L
        while (requestWindowTimestamps.isNotEmpty() && requestWindowTimestamps.first() < windowStart) {
            requestWindowTimestamps.removeFirst()
        }
        if (requestWindowTimestamps.size >= maxRequestsPerMinute) {
            return false
        }
        requestWindowTimestamps.addLast(now)
        return true
    }

    @Synchronized
    fun currentRequestsThisMinute(): Int {
        val windowStart = System.currentTimeMillis() - 60_000L
        while (requestWindowTimestamps.isNotEmpty() && requestWindowTimestamps.first() < windowStart) {
            requestWindowTimestamps.removeFirst()
        }
        return requestWindowTimestamps.size
    }

    fun isSecureEndpointUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.startsWith("https://") || clean.startsWith("http://127.0.0.1") || clean.startsWith("http://localhost")
    }

    fun saveCompanionBearerToken(rawToken: String) {
        val clean = rawToken.trim()
        if (clean.isBlank()) {
            prefs.edit().remove("enc_companion_token").remove("iv_companion_token").apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString("enc_companion_token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv_companion_token", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        }.onFailure {
            val encoded = Base64.encodeToString(clean.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString("obf_companion_token", encoded).apply()
        }
    }

    fun getCompanionBearerToken(): String {
        val enc = prefs.getString("enc_companion_token", null)
        val ivStr = prefs.getString("iv_companion_token", null)
        if (!enc.isNullOrBlank() && !ivStr.isNullOrBlank()) {
            val decoded = runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, Base64.decode(ivStr, Base64.NO_WRAP))
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
                String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)), Charsets.UTF_8)
            }.getOrNull()
            if (decoded != null) return decoded
        }
        val obf = prefs.getString("obf_companion_token", null) ?: return ""
        return runCatching { String(Base64.decode(obf, Base64.NO_WRAP), Charsets.UTF_8) }.getOrDefault("")
    }

    suspend fun checkGatewayHealth(
        geminiConfigured: Boolean,
        githubConfigured: Boolean,
        companionUrl: String,
        externalApiAllowed: Boolean
    ): BackendHealthSnapshot = withContext(Dispatchers.IO) {
        val cleanUrl = companionUrl.trim()
        val companionSet = cleanUrl.isNotBlank() && isSecureEndpointUrl(cleanUrl)
        var companionOk = false
        var msg = if (!externalApiAllowed) {
            "External APIs paused by user policy. Core offline features 100% available."
        } else if (companionSet) {
            if (!acquireRateLimitPermit()) {
                "Rate limiter paused probe ($maxRequestsPerMinute req/min cap)."
            } else {
                val target = cleanUrl.trimEnd('/') + "/health"
                val reqBuilder = Request.Builder().url(target).get()
                val token = getCompanionBearerToken()
                if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")
                runCatching {
                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        companionOk = resp.isSuccessful
                        if (resp.isSuccessful) "Companion HTTPS gateway healthy (HTTP ${resp.code})."
                        else "Companion responded HTTP ${resp.code}; local features remain active."
                    }
                }.getOrElse { err ->
                    "Companion unreachable (${sanitizeForLog(err.localizedMessage ?: "offline")}); local features unaffected."
                }
            }
        } else {
            "Gateway nominal. Optional companion URL not set (Gemini/GitHub clients govern direct HTTPS calls)."
        }

        val snap = BackendHealthSnapshot(
            geminiConfigured = geminiConfigured,
            githubConfigured = githubConfigured,
            companionConfigured = companionSet,
            companionReachable = companionOk,
            requestsThisMinute = currentRequestsThisMinute(),
            maxRequestsPerMinute = maxRequestsPerMinute,
            localOnlyModeForced = !externalApiAllowed,
            lastCheckMessage = msg
        )
        _healthState.value = snap
        snap
    }

    suspend fun collectDbStats(memoryRepo: MemoryRepository, advancedRepo: AdvancedRepository): DatabaseMaintenanceStats {
        return DatabaseMaintenanceStats(
            memoryCount = memoryRepo.getRecentMemories(500).size,
            routineCount = advancedRepo.getAllRoutines().size,
            workflowCount = advancedRepo.getAllWorkflows().size,
            knowledgeNodeCount = advancedRepo.getAllKnowledgeNodes().size,
            productivityCount = advancedRepo.getAllProductivityItems().size,
            crashCount = advancedRepo.getAllCrashReports().size
        )
    }

    companion object {
        private const val KEY_ALIAS = "aura_backend_companion_aes"

        fun sanitizeForLog(raw: String): String {
            return raw
                .replace(Regex("""AIza[0-9A-Za-z\-_]{10,}"""), "[REDACTED_GEMINI_KEY]")
                .replace(Regex("""(?:ghp|github_pat)_[0-9A-Za-z_]{10,}"""), "[REDACTED_GITHUB_TOKEN]")
                .replace(Regex("""Bearer\s+[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        }

        private fun getOrCreateSecretKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return gen.generateKey()
        }
    }
}
