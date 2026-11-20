package com.example.aura.backend

import android.content.Context
import com.example.aura.github.GitHubFileDraft
import com.example.aura.github.GitHubPushGuard
import com.example.aura.github.PushGuardResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudDevBackendHealth(
    val reachable: Boolean,
    val repoRoot: String = "",
    val gradleAvailable: Boolean = false,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis()
)

data class CloudDevTaskSubmission(
    val featureName: String,
    val branchName: String,
    val commitMessage: String,
    val edits: List<GitHubFileDraft>,
    val runTests: Boolean = true,
    val applyChanges: Boolean = true
)

data class CloudDevTaskResult(
    val taskId: String,
    val featureName: String,
    val status: String, // COMPLETED, FAILED, BLOCKED, UNREACHABLE
    val branch: String,
    val testsPassed: Boolean,
    val blockedReason: String = "",
    val diffSummary: String = "",
    val buildLogTail: String = ""
)

/**
 * REST client communicating with AURA's optional Cloud Self-Coding Backend (/tools/cloud_dev_backend.py).
 * Enforces HTTPS/localhost transport checks, Keystore bearer authentication, and GitHubPushGuard before
 * sending any repository diff remotely.
 */
class CloudDevBackendClient(
    private val context: Context,
    private val gateway: BackendServiceGateway,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    fun getSavedBackendUrl(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_URL, "")
            .orEmpty()
            .trim()
    }

    fun saveBackendUrl(url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_URL, url.trim())
            .apply()
    }

    suspend fun checkHealth(backendUrl: String = getSavedBackendUrl()): CloudDevBackendHealth =
        withContext(Dispatchers.IO) {
            val clean = backendUrl.trim().trimEnd('/')
            if (clean.isBlank()) {
                return@withContext CloudDevBackendHealth(
                    reachable = false,
                    message = "Cloud Dev Backend URL not configured. Point to https://... or http://127.0.0.1:8787 running tools/cloud_dev_backend.py."
                )
            }
            if (!gateway.isSecureEndpointUrl(clean)) {
                return@withContext CloudDevBackendHealth(
                    reachable = false,
                    message = "Blocked insecure URL '$clean' (HTTPS or localhost required)."
                )
            }
            val reqBuilder = Request.Builder().url("$clean/health").get()
            val token = gateway.getCompanionBearerToken()
            if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")

            runCatching {
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        CloudDevBackendHealth(false, message = "Backend HTTP ${resp.code}: ${raw.take(120)}")
                    } else {
                        val json = JSONObject(raw)
                        CloudDevBackendHealth(
                            reachable = true,
                            repoRoot = json.optString("repo_root"),
                            gradleAvailable = json.optBoolean("gradle_available", false),
                            message = "Cloud Dev Backend online (gradle=${json.optBoolean("gradle_available", false)})."
                        )
                    }
                }
            }.getOrElse { err ->
                CloudDevBackendHealth(
                    reachable = false,
                    message = "Backend unreachable: ${BackendServiceGateway.sanitizeForLog(err.localizedMessage ?: "offline")}"
                )
            }
        }

    suspend fun submitDevTask(
        request: CloudDevTaskSubmission,
        backendUrl: String = getSavedBackendUrl(),
        allowWorkflowEdits: Boolean = false
    ): CloudDevTaskResult = withContext(Dispatchers.IO) {
        val clean = backendUrl.trim().trimEnd('/')
        if (clean.isBlank()) {
            return@withContext CloudDevTaskResult(
                taskId = "",
                featureName = request.featureName,
                status = "BLOCKED",
                branch = request.branchName,
                testsPassed = false,
                blockedReason = "Cloud Dev Backend URL not configured in System Center."
            )
        }
        if (!gateway.isSecureEndpointUrl(clean)) {
            return@withContext CloudDevTaskResult(
                taskId = "",
                featureName = request.featureName,
                status = "BLOCKED",
                branch = request.branchName,
                testsPassed = false,
                blockedReason = "Blocked non-HTTPS remote URL '$clean'."
            )
        }

        val guard = GitHubPushGuard.validateProposal(
            files = request.edits,
            targetBranch = request.branchName,
            allowWorkflowEdits = allowWorkflowEdits,
            knownSecrets = listOfNotNull(gateway.getCompanionBearerToken().takeIf { it.isNotBlank() })
        )
        if (guard is PushGuardResult.Blocked) {
            return@withContext CloudDevTaskResult(
                taskId = "",
                featureName = request.featureName,
                status = "BLOCKED",
                branch = request.branchName,
                testsPassed = false,
                blockedReason = guard.reason
            )
        }

        val payload = JSONObject().apply {
            put("feature_name", request.featureName)
            put("branch_name", request.branchName)
            put("commit_message", request.commitMessage)
            put("run_tests", request.runTests)
            put("apply_changes", request.applyChanges)
            put("file_edits", JSONArray().apply {
                request.edits.forEach { draft ->
                    put(JSONObject().apply {
                        put("path", draft.path)
                        put("content", draft.newContent)
                    })
                }
            })
        }

        val reqBuilder = Request.Builder()
            .url("$clean/v1/dev-tasks")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val token = gateway.getCompanionBearerToken()
        if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")

        runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (json == null) {
                    CloudDevTaskResult(
                        taskId = "",
                        featureName = request.featureName,
                        status = if (resp.code == 403) "BLOCKED" else "FAILED",
                        branch = request.branchName,
                        testsPassed = false,
                        blockedReason = "Backend HTTP ${resp.code}: ${raw.take(140)}"
                    )
                } else {
                    CloudDevTaskResult(
                        taskId = json.optString("task_id"),
                        featureName = json.optString("feature_name", request.featureName),
                        status = json.optString("status", if (resp.isSuccessful) "COMPLETED" else "FAILED"),
                        branch = json.optString("branch", request.branchName),
                        testsPassed = json.optBoolean("tests_passed", false),
                        blockedReason = json.optString("blocked_reason"),
                        diffSummary = json.optString("diff_summary"),
                        buildLogTail = BackendServiceGateway.sanitizeForLog(json.optString("build_log_tail"))
                    )
                }
            }
        }.getOrElse { err ->
            CloudDevTaskResult(
                taskId = "",
                featureName = request.featureName,
                status = "UNREACHABLE",
                branch = request.branchName,
                testsPassed = false,
                blockedReason = BackendServiceGateway.sanitizeForLog(err.localizedMessage ?: "Connection failed")
            )
        }
    }

    suspend fun getDevTaskStatus(taskId: String, backendUrl: String = getSavedBackendUrl()): Result<CloudDevTaskResult> =
        withContext(Dispatchers.IO) {
            val clean = backendUrl.trim().trimEnd('/')
            if (clean.isBlank() || !gateway.isSecureEndpointUrl(clean)) {
                return@withContext Result.failure(IllegalStateException("Cloud Dev Backend URL invalid or missing."))
            }
            val reqBuilder = Request.Builder().url("$clean/v1/dev-tasks/$taskId").get()
            val token = gateway.getCompanionBearerToken()
            if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")

            runCatching {
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${raw.take(120)}")
                    val json = JSONObject(raw)
                    CloudDevTaskResult(
                        taskId = json.optString("task_id", taskId),
                        featureName = json.optString("feature_name"),
                        status = json.optString("status", "UNKNOWN"),
                        branch = json.optString("branch"),
                        testsPassed = json.optBoolean("tests_passed", false),
                        blockedReason = json.optString("blocked_reason"),
                        diffSummary = json.optString("diff_summary"),
                        buildLogTail = BackendServiceGateway.sanitizeForLog(json.optString("build_log_tail"))
                    )
                }
            }
        }

    companion object {
        private const val PREFS_NAME = "aura_cloud_dev_backend_prefs"
        private const val KEY_BACKEND_URL = "cloud_dev_backend_url"
    }
}
