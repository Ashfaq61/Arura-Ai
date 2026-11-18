package com.example.aura.sync

import com.example.aura.backend.BackendServiceGateway
import com.example.aura.github.DEFAULT_AURA_BRANCH
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubFileDraft
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.github.PendingGitHubPushProposal
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.SyncDeviceEntity
import com.example.aura.security.BackupExportOptions
import com.example.aura.security.EncryptedBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class SyncConflictStrategy(val label: String) {
    LAST_WRITE_WINS("Last-Write-Wins"),
    KEEP_LOCAL("Always Keep Local"),
    KEEP_REMOTE("Prefer Remote"),
    MERGE_COLLECTIONS("Merge Both Collections")
}

data class MultiDeviceSyncReport(
    val succeeded: Boolean,
    val channelUsed: String,
    val message: String,
    val checksum: String = ""
)

/**
 * Manages optional encrypted multi-device synchronization with device authorization,
 * conflict resolution policies, and real transport channels:
 * 1) Encrypted GitHub Repository State (.aura_sync/device_state.enc.json on aura-changes branch)
 * 2) Optional HTTPS Companion Endpoint (/v1/aura/sync)
 * 3) Offline Encrypted Bundle (.aurasync) for manual peer/drive transfer
 */
class MultiDeviceSyncManager(
    private val advancedRepo: AdvancedRepository,
    private val backupManager: EncryptedBackupManager,
    private val gitHubClient: GitHubSelfUpdateClient,
    private val gateway: BackendServiceGateway,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    suspend fun registerOrAuthorizeDevice(
        deviceId: String,
        deviceName: String,
        authorized: Boolean = true,
        channel: String = "GITHUB_ENCRYPTED"
    ) {
        advancedRepo.upsertSyncDevice(
            SyncDeviceEntity(
                deviceId = deviceId,
                deviceName = deviceName,
                platform = "Android",
                authorized = authorized,
                lastSyncAt = System.currentTimeMillis(),
                syncChannel = channel
            )
        )
    }

    suspend fun toggleDeviceAuthorization(device: SyncDeviceEntity) {
        advancedRepo.upsertSyncDevice(device.copy(authorized = !device.authorized))
    }

    suspend fun createEncryptedSyncEnvelope(
        deviceId: String,
        deviceName: String,
        passphrase: String
    ): Result<Pair<String, String>> {
        if (passphrase.length < 6) {
            return Result.failure(IllegalArgumentException("Sync passphrase must be at least 6 characters."))
        }
        val payloadJson = backupManager.buildSanitizedPayloadJson(BackupExportOptions())
        val checksum = sha256(payloadJson)
        val encryptedBody = EncryptedBackupManager.encryptWithPassphrase(passphrase, payloadJson)
        val envelope = JSONObject().apply {
            put("protocol", "aura_device_sync_v1")
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("timestamp", System.currentTimeMillis())
            put("checksum", checksum)
            put("encryptedPayload", JSONObject(encryptedBody))
        }.toString(2)
        return Result.success(envelope to checksum)
    }

    suspend fun syncViaGitHubEncrypted(
        config: GitHubConfig,
        deviceId: String,
        deviceName: String,
        passphrase: String,
        conflictStrategy: SyncConflictStrategy
    ): MultiDeviceSyncReport = withContext(Dispatchers.IO) {
        if (!config.isConfigured || config.token.isBlank()) {
            return@withContext MultiDeviceSyncReport(false, "GITHUB_ENCRYPTED", "Configure GitHub repo & token in Settings first.")
        }
        if (!gateway.acquireRateLimitPermit()) {
            return@withContext MultiDeviceSyncReport(false, "GITHUB_ENCRYPTED", "Gateway rate limit reached. Wait 1 minute.")
        }

        val syncPath = ".aura_sync/device_state.enc.json"
        val branch = config.effectivePushBranch.ifBlank { DEFAULT_AURA_BRANCH }
        gitHubClient.ensureBranchExists(config, branch)
        val remoteExisting = gitHubClient.getFileContent(config, syncPath, branch).getOrNull()

        if (remoteExisting != null && conflictStrategy == SyncConflictStrategy.KEEP_REMOTE) {
            val imported = importEncryptedSyncEnvelope(remoteExisting.newContent, passphrase, conflictStrategy)
            return@withContext if (imported.succeeded) imported else MultiDeviceSyncReport(false, "GITHUB_ENCRYPTED", imported.message)
        }

        val envResult = createEncryptedSyncEnvelope(deviceId, deviceName, passphrase)
        if (envResult.isFailure) {
            return@withContext MultiDeviceSyncReport(false, "GITHUB_ENCRYPTED", envResult.exceptionOrNull()?.message ?: "Encryption failed.")
        }
        val (envelope, checksum) = envResult.getOrThrow()

        if (remoteExisting != null && conflictStrategy == SyncConflictStrategy.MERGE_COLLECTIONS) {
            runCatching {
                val remoteJson = JSONObject(remoteExisting.newContent)
                val encObj = remoteJson.getJSONObject("encryptedPayload").toString()
                backupManager.restoreFromEncryptedBackup(passphrase, encObj)
            }
        }

        val proposal = PendingGitHubPushProposal(
            branch = branch,
            commitMessage = "AURA Encrypted Device Sync ($deviceName)",
            explanation = "Encrypted multi-device sync bundle ($branch/$syncPath, sha256=${checksum.take(12)}).",
            files = listOf(
                GitHubFileDraft(
                    path = syncPath,
                    newContent = envelope,
                    existingContent = remoteExisting?.existingContent,
                    existingSha = remoteExisting?.existingSha
                )
            )
        )
        val pushRes = gitHubClient.pushApprovedProposal(config, proposal)
        pushRes.fold(
            onSuccess = {
                advancedRepo.upsertSyncDevice(
                    SyncDeviceEntity(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        authorized = true,
                        lastSyncAt = System.currentTimeMillis(),
                        lastChecksum = checksum.take(16),
                        syncChannel = "GITHUB_ENCRYPTED"
                    )
                )
                MultiDeviceSyncReport(true, "GITHUB_ENCRYPTED", "Encrypted state pushed to $branch/$syncPath (sha256: ${checksum.take(12)}).", checksum)
            },
            onFailure = { err ->
                MultiDeviceSyncReport(false, "GITHUB_ENCRYPTED", err.message ?: "GitHub sync failed.")
            }
        )
    }

    suspend fun syncViaCompanionHttps(
        endpointUrl: String,
        deviceId: String,
        deviceName: String,
        passphrase: String
    ): MultiDeviceSyncReport = withContext(Dispatchers.IO) {
        val clean = endpointUrl.trim()
        if (!gateway.isSecureEndpointUrl(clean)) {
            return@withContext MultiDeviceSyncReport(false, "COMPANION_HTTPS", "Companion endpoint must use HTTPS.")
        }
        if (!gateway.acquireRateLimitPermit()) {
            return@withContext MultiDeviceSyncReport(false, "COMPANION_HTTPS", "Rate limit active.")
        }
        val envRes = createEncryptedSyncEnvelope(deviceId, deviceName, passphrase)
        if (envRes.isFailure) {
            return@withContext MultiDeviceSyncReport(false, "COMPANION_HTTPS", envRes.exceptionOrNull()?.message ?: "Encryption error.")
        }
        val (envelope, checksum) = envRes.getOrThrow()
        val url = clean.trimEnd('/') + "/v1/aura/sync"
        val reqBuilder = Request.Builder()
            .url(url)
            .post(envelope.toRequestBody("application/json; charset=utf-8".toMediaType()))
        val token = gateway.getCompanionBearerToken()
        if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")

        runCatching {
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    MultiDeviceSyncReport(false, "COMPANION_HTTPS", "Companion server HTTP ${resp.code}")
                } else {
                    advancedRepo.upsertSyncDevice(
                        SyncDeviceEntity(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            authorized = true,
                            lastSyncAt = System.currentTimeMillis(),
                            lastChecksum = checksum.take(16),
                            syncChannel = "COMPANION_HTTPS"
                        )
                    )
                    MultiDeviceSyncReport(true, "COMPANION_HTTPS", "Synced encrypted payload with companion endpoint.", checksum)
                }
            }
        }.getOrElse { err ->
            MultiDeviceSyncReport(false, "COMPANION_HTTPS", "Companion sync error: ${BackendServiceGateway.sanitizeForLog(err.localizedMessage ?: "offline")}")
        }
    }

    suspend fun importEncryptedSyncEnvelope(
        envelopeJson: String,
        passphrase: String,
        strategy: SyncConflictStrategy
    ): MultiDeviceSyncReport {
        if (strategy == SyncConflictStrategy.KEEP_LOCAL) {
            return MultiDeviceSyncReport(true, "CONFLICT_POLICY", "Kept local state per KEEP_LOCAL conflict policy.")
        }
        return runCatching {
            val env = JSONObject(envelopeJson)
            require(env.optString("protocol") == "aura_device_sync_v1") { "Invalid sync envelope format." }
            val remoteDevId = env.optString("deviceId", "remote-dev")
            val remoteDevName = env.optString("deviceName", "Remote Device")
            val authorizedDevices = advancedRepo.getAllSyncDevices().associateBy { it.deviceId }
            val existingDev = authorizedDevices[remoteDevId]
            if (existingDev != null && !existingDev.authorized) {
                return MultiDeviceSyncReport(false, "AUTH_BLOCKED", "Device '$remoteDevName' ($remoteDevId) is revoked/unauthorized.")
            }

            val encPayload = env.getJSONObject("encryptedPayload").toString()
            val restoreRes = backupManager.restoreFromEncryptedBackup(passphrase, encPayload)
            restoreRes.fold(
                onSuccess = { summary ->
                    val checksum = env.optString("checksum", "")
                    advancedRepo.upsertSyncDevice(
                        SyncDeviceEntity(
                            deviceId = remoteDevId,
                            deviceName = remoteDevName,
                            authorized = true,
                            lastSyncAt = System.currentTimeMillis(),
                            lastChecksum = checksum.take(16),
                            syncChannel = "ENCRYPTED_IMPORT"
                        )
                    )
                    MultiDeviceSyncReport(
                        true,
                        "ENCRYPTED_IMPORT",
                        "Synced from '$remoteDevName' ($strategy): ${summary.memoriesRestored} memories, ${summary.routinesRestored} routines, ${summary.workflowsRestored} workflows, ${summary.knowledgeNodesRestored} KG nodes.",
                        checksum
                    )
                },
                onFailure = { err ->
                    MultiDeviceSyncReport(false, "ENCRYPTED_IMPORT", "Decryption failed (check passphrase): ${err.localizedMessage}")
                }
            )
        }.getOrElse { err ->
            MultiDeviceSyncReport(false, "ENCRYPTED_IMPORT", "Sync import failed: ${err.localizedMessage}")
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
