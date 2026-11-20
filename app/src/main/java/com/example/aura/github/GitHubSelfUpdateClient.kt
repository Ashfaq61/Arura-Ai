package com.example.aura.github

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GitHubApiException(
    val httpCode: Int?,
    override val message: String
) : Exception(message)

/**
 * Direct GitHub REST API client supporting:
 * - Repository connectivity testing (GET /repos/{owner}/{repo})
 * - Safe code creation/updates on non-main branch via Contents API (GET/PUT /contents/{path})
 * - Manual merge from aura-changes -> main (POST /merges)
 * - Actions workflow run polling & failed job 30-line log tailing
 * - GitHub Releases latest check, cross-host-safe asset APK download with progress, and rollback info
 */
class GitHubSelfUpdateClient(
    httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    private val client: OkHttpClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun testConnection(config: GitHubConfig): GitHubConnectionResult = withContext(Dispatchers.IO) {
        val owner = config.owner.trim()
        val repo = config.repo.trim()
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext GitHubConnectionResult(false, "Enter both Repo owner and Repo name.")
        }
        val url = apiUrl("repos", owner, repo)
            ?: return@withContext GitHubConnectionResult(false, "Invalid GitHub URL configuration.")

        val req = baseRequestBuilder(url, config.token).get().build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@executeSafe GitHubConnectionResult(
                        isOk = false,
                        message = mapHttpError(resp.code, extractMessage(raw), resp)
                    )
                }
                val json = JSONObject(raw)
                val fullName = json.optString("full_name", "$owner/$repo")
                val defaultBranch = json.optString("default_branch", "main")
                val isPrivate = json.optBoolean("private", false)
                GitHubConnectionResult(
                    isOk = true,
                    message = "OK — Connected to $fullName (default: $defaultBranch)",
                    defaultBranch = defaultBranch,
                    privateRepo = isPrivate
                )
            }
        }.getOrElse { err ->
            GitHubConnectionResult(false, err.message ?: "Failed to connect to GitHub.")
        }
    }

    suspend fun getFileContent(
        config: GitHubConfig,
        path: String,
        branch: String = config.effectivePushBranch
    ): Result<GitHubFileDraft?> = withContext(Dispatchers.IO) {
        val cleanPath = GitHubPushGuard.normalizeRepoPath(path)
        val parsedBase = baseUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid GitHub base URL."))
        val urlBuilder = parsedBase.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(config.owner.trim())
            .addPathSegment(config.repo.trim())
            .addPathSegment("contents")
        cleanPath.split('/').filter { it.isNotEmpty() }.forEach { urlBuilder.addPathSegment(it) }
        urlBuilder.addQueryParameter("ref", branch.trim().ifBlank { DEFAULT_AURA_BRANCH })

        val req = baseRequestBuilder(urlBuilder.build(), config.token).get().build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 404) {
                    return@executeSafe null
                }
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val json = JSONObject(raw)
                val sha = json.optString("sha").takeIf { it.isNotBlank() }
                val encodedContent = json.optString("content").replace("\n", "").trim()
                val decoded = if (encodedContent.isNotEmpty()) {
                    String(Base64.decode(encodedContent, Base64.DEFAULT), Charsets.UTF_8)
                } else ""
                GitHubFileDraft(
                    path = cleanPath,
                    newContent = decoded,
                    existingContent = decoded,
                    existingSha = sha
                )
            }
        }
    }

    suspend fun ensureBranchExists(config: GitHubConfig, targetBranch: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanBranch = targetBranch.trim().ifBlank { DEFAULT_AURA_BRANCH }
            val refUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "git", "ref", "heads", cleanBranch)
                ?: return@withContext Result.failure(GitHubApiException(null, "Invalid branch URL."))

            val checkReq = baseRequestBuilder(refUrl, config.token).get().build()
            executeSafe {
                executeFollowingSafeRedirects(checkReq, config.token).use { resp ->
                    if (resp.isSuccessful) return@executeSafe Unit
                    if (resp.code != 404) {
                        val raw = resp.body?.string().orEmpty()
                        throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                    }
                }

                // Branch doesn't exist yet -> read repo default branch SHA and create branch ref
                val repoUrl = apiUrl("repos", config.owner.trim(), config.repo.trim())
                    ?: throw GitHubApiException(null, "Invalid repo URL.")
                val defaultBranch = executeFollowingSafeRedirects(
                    baseRequestBuilder(repoUrl, config.token).get().build(),
                    config.token
                ).use { r ->
                    val raw = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        throw GitHubApiException(r.code, mapHttpError(r.code, extractMessage(raw), r))
                    }
                    JSONObject(raw).optString("default_branch", "main")
                }

                val baseRefUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "git", "ref", "heads", defaultBranch)
                    ?: throw GitHubApiException(null, "Invalid base branch URL.")
                val baseSha = executeFollowingSafeRedirects(
                    baseRequestBuilder(baseRefUrl, config.token).get().build(),
                    config.token
                ).use { r ->
                    val raw = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        throw GitHubApiException(r.code, mapHttpError(r.code, extractMessage(raw), r))
                    }
                    JSONObject(raw).getJSONObject("object").getString("sha")
                }

                val createRefUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "git", "refs")
                    ?: throw GitHubApiException(null, "Invalid create ref URL.")
                val payload = JSONObject().apply {
                    put("ref", "refs/heads/$cleanBranch")
                    put("sha", baseSha)
                }
                executeFollowingSafeRedirects(
                    baseRequestBuilder(createRefUrl, config.token)
                        .post(payload.toString().toRequestBody(JSON_MEDIA))
                        .build(),
                    config.token
                ).use { r ->
                    val raw = r.body?.string().orEmpty()
                    if (!r.isSuccessful && r.code != 422) {
                        throw GitHubApiException(r.code, mapHttpError(r.code, extractMessage(raw), r))
                    }
                }
                Unit
            }
        }

    suspend fun pushApprovedProposal(
        config: GitHubConfig,
        proposal: PendingGitHubPushProposal,
        knownSecrets: List<String> = emptyList()
    ): Result<GitHubPushResult> = withContext(Dispatchers.IO) {
        val guardCheck = GitHubPushGuard.validateProposal(
            files = proposal.files,
            targetBranch = proposal.branch,
            allowWorkflowEdits = config.allowWorkflowEdits,
            knownSecrets = knownSecrets + config.token
        )
        if (guardCheck is PushGuardResult.Blocked) {
            return@withContext Result.failure(GitHubApiException(403, guardCheck.reason))
        }
        if (config.token.isBlank()) {
            return@withContext Result.failure(
                GitHubApiException(401, "HTTP 401: GitHub token missing. Add a Fine-grained token in Settings.")
            )
        }

        var lastCommitSha = ""
        var lastCommitUrl = ""
        val pushedPaths = mutableListOf<String>()

        for (fileDraft in proposal.files) {
            val cleanPath = GitHubPushGuard.normalizeRepoPath(fileDraft.path)
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(GitHubApiException(null, "Invalid GitHub base URL."))
            val urlBuilder = parsedBase.newBuilder()
                .addPathSegment("repos")
                .addPathSegment(config.owner.trim())
                .addPathSegment(config.repo.trim())
                .addPathSegment("contents")
            cleanPath.split('/').filter { it.isNotEmpty() }.forEach { urlBuilder.addPathSegment(it) }

            val contentBase64 = Base64.encodeToString(
                fileDraft.newContent.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val bodyJson = JSONObject().apply {
                put("message", proposal.commitMessage.ifBlank { "Aura AI: update $cleanPath" })
                put("content", contentBase64)
                put("branch", proposal.branch.trim())
                if (!fileDraft.existingSha.isNullOrBlank()) {
                    put("sha", fileDraft.existingSha)
                }
            }

            val req = baseRequestBuilder(urlBuilder.build(), config.token)
                .put(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            val singleRes = executeSafe {
                executeFollowingSafeRedirects(req, config.token).use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                    }
                    val commitObj = JSONObject(raw).optJSONObject("commit")
                    lastCommitSha = commitObj?.optString("sha").orEmpty()
                    lastCommitUrl = commitObj?.optString("html_url").orEmpty()
                    if (lastCommitUrl.isBlank() && lastCommitSha.isNotBlank()) {
                        lastCommitUrl = "https://github.com/${config.owner.trim()}/${config.repo.trim()}/commit/$lastCommitSha"
                    }
                    pushedPaths.add(cleanPath)
                }
            }
            if (singleRes.isFailure) {
                return@withContext Result.failure(singleRes.exceptionOrNull()!!)
            }
        }

        Result.success(
            GitHubPushResult(
                commitSha = lastCommitSha,
                commitUrl = lastCommitUrl,
                branch = proposal.branch.trim(),
                pushedPaths = pushedPaths
            )
        )
    }

    suspend fun mergeBranchToMain(
        config: GitHubConfig,
        headBranch: String = config.effectivePushBranch,
        commitMessage: String = "Merge ${config.effectivePushBranch} into main (approved in Aura AI)"
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanHead = headBranch.trim()
        if (cleanHead.isBlank() || cleanHead.equals("main", ignoreCase = true)) {
            return@withContext Result.failure(GitHubApiException(400, "Cannot merge 'main' into 'main'."))
        }
        val url = apiUrl("repos", config.owner.trim(), config.repo.trim(), "merges")
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid merges URL."))

        val payload = JSONObject().apply {
            put("base", "main")
            put("head", cleanHead)
            put("commit_message", commitMessage)
        }
        val req = baseRequestBuilder(url, config.token)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 204) {
                    return@executeSafe "Already up to date (nothing to merge)."
                }
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val json = JSONObject(raw)
                val sha = json.optString("sha").take(7)
                val htmlUrl = json.optString("html_url")
                if (htmlUrl.isNotBlank()) "Merged into main ($sha): $htmlUrl"
                else "Merged $cleanHead into main (commit $sha)."
            }
        }
    }

    suspend fun createPullRequest(
        config: GitHubConfig,
        title: String,
        body: String,
        headBranch: String = config.effectivePushBranch,
        baseBranch: String = "main"
    ): Result<GitHubPullRequestInfo> = withContext(Dispatchers.IO) {
        val cleanHead = headBranch.trim()
        if (cleanHead.isBlank() || cleanHead.equals(baseBranch, ignoreCase = true)) {
            return@withContext Result.failure(GitHubApiException(400, "Head branch '$cleanHead' must differ from '$baseBranch'."))
        }
        if (config.token.isBlank()) {
            return@withContext Result.failure(GitHubApiException(401, "Missing GitHub PAT token."))
        }
        val url = apiUrl("repos", config.owner.trim(), config.repo.trim(), "pulls")
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid pulls URL."))

        val payload = JSONObject().apply {
            put("title", title.ifBlank { "AURA AI Autonomous Update ($cleanHead)" })
            put("head", cleanHead)
            put("base", baseBranch.ifBlank { "main" })
            put("body", body)
        }
        val req = baseRequestBuilder(url, config.token)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val json = JSONObject(raw)
                GitHubPullRequestInfo(
                    number = json.optInt("number"),
                    title = json.optString("title"),
                    state = json.optString("state", "open"),
                    headBranch = json.optJSONObject("head")?.optString("ref", cleanHead) ?: cleanHead,
                    baseBranch = json.optJSONObject("base")?.optString("ref", baseBranch) ?: baseBranch,
                    htmlUrl = json.optString("html_url"),
                    createdAt = json.optString("created_at")
                )
            }
        }
    }

    suspend fun listOpenPullRequests(config: GitHubConfig): Result<List<GitHubPullRequestInfo>> =
        withContext(Dispatchers.IO) {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(GitHubApiException(null, "Invalid GitHub URL."))
            val url = parsedBase.newBuilder()
                .addPathSegment("repos")
                .addPathSegment(config.owner.trim())
                .addPathSegment(config.repo.trim())
                .addPathSegment("pulls")
                .addQueryParameter("state", "open")
                .addQueryParameter("per_page", "15")
                .build()
            val req = baseRequestBuilder(url, config.token).get().build()
            executeSafe {
                executeFollowingSafeRedirects(req, config.token).use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                    }
                    val arr = JSONArray(raw)
                    (0 until arr.length()).map { i ->
                        val json = arr.getJSONObject(i)
                        GitHubPullRequestInfo(
                            number = json.optInt("number"),
                            title = json.optString("title"),
                            state = json.optString("state", "open"),
                            headBranch = json.optJSONObject("head")?.optString("ref").orEmpty(),
                            baseBranch = json.optJSONObject("base")?.optString("ref").orEmpty(),
                            htmlUrl = json.optString("html_url"),
                            createdAt = json.optString("created_at")
                        )
                    }
                }
            }
        }

    suspend fun postPullRequestReviewComment(
        config: GitHubConfig,
        prNumber: Int,
        commentBody: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = apiUrl("repos", config.owner.trim(), config.repo.trim(), "issues", prNumber.toString(), "comments")
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid comment URL."))
        val payload = JSONObject().apply { put("body", commentBody) }
        val req = baseRequestBuilder(url, config.token)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                JSONObject(raw).optString("html_url", "Comment posted to PR #$prNumber")
            }
        }
    }

    suspend fun triggerWorkflowDispatch(
        config: GitHubConfig,
        workflowFile: String = "android-ci.yml",
        refBranch: String = config.effectivePushBranch
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.token.isBlank()) {
            return@withContext Result.failure(GitHubApiException(401, "Missing GitHub PAT token."))
        }
        val url = apiUrl(
            "repos",
            config.owner.trim(),
            config.repo.trim(),
            "actions",
            "workflows",
            workflowFile,
            "dispatches"
        ) ?: return@withContext Result.failure(GitHubApiException(null, "Invalid workflow dispatch URL."))
        val payload = JSONObject().apply {
            put("ref", refBranch.trim().ifBlank { DEFAULT_AURA_BRANCH })
        }
        val req = baseRequestBuilder(url, config.token)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                if (resp.code == 204 || resp.isSuccessful) {
                    "Dispatched workflow '$workflowFile' on ref '${payload.optString("ref")}'."
                } else {
                    val raw = resp.body?.string().orEmpty()
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
            }
        }
    }

    fun generateChangelogMarkdown(
        branch: String,
        featureTitle: String,
        pushedFiles: List<String>,
        testSummary: String
    ): String {
        return buildString {
            appendLine("## AURA Autonomous Change Summary ($branch)")
            appendLine("- **Feature / Fix:** $featureTitle")
            appendLine("- **Tests:** ${testSummary.ifBlank { "Mandatory Gradle unit tests pending" }}")
            appendLine("- **Modified Paths (${pushedFiles.size}):**")
            pushedFiles.forEach { appendLine("  - `$it`") }
        }.trim()
    }

    suspend fun fetchLatestWorkflowRun(
        config: GitHubConfig,
        branch: String = config.effectivePushBranch
    ): Result<GitHubWorkflowRunStatus?> = withContext(Dispatchers.IO) {
        val parsedBase = baseUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid GitHub URL."))
        val url = parsedBase.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(config.owner.trim())
            .addPathSegment(config.repo.trim())
            .addPathSegment("actions")
            .addPathSegment("runs")
            .addQueryParameter("branch", branch.trim().ifBlank { DEFAULT_AURA_BRANCH })
            .addQueryParameter("per_page", "5")
            .build()

        val req = baseRequestBuilder(url, config.token).get().build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val runs = JSONObject(raw).optJSONArray("workflow_runs") ?: JSONArray()
                if (runs.length() == 0) return@executeSafe null
                val latest = runs.getJSONObject(0)
                val status = latest.optString("status")
                val conclusion = latest.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }
                val stage = when {
                    status == "queued" || status == "waiting" || status == "requested" || status == "pending" ->
                        CiBuildStage.QUEUED
                    status == "in_progress" ->
                        CiBuildStage.BUILDING
                    status == "completed" && conclusion == "success" ->
                        CiBuildStage.SUCCESS
                    status == "completed" ->
                        CiBuildStage.FAILED
                    else -> CiBuildStage.QUEUED
                }
                GitHubWorkflowRunStatus(
                    runId = latest.getLong("id"),
                    name = latest.optString("name", "Android CI"),
                    branch = latest.optString("head_branch", branch),
                    stage = stage,
                    rawStatus = status,
                    rawConclusion = conclusion,
                    htmlUrl = latest.optString("html_url"),
                    headSha = latest.optString("head_sha").take(7),
                    updatedAt = latest.optString("updated_at")
                )
            }
        }
    }

    suspend fun fetchFailedJobLogTail(
        config: GitHubConfig,
        runId: Long,
        tailLines: Int = 30
    ): Result<String> = withContext(Dispatchers.IO) {
        val jobsUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "actions", "runs", runId.toString(), "jobs")
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid jobs URL."))

        val jobsReq = baseRequestBuilder(jobsUrl, config.token).get().build()
        executeSafe {
            val failedJobId = executeFollowingSafeRedirects(jobsReq, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val jobs = JSONObject(raw).optJSONArray("jobs") ?: JSONArray()
                var picked: Long? = null
                for (i in 0 until jobs.length()) {
                    val job = jobs.optJSONObject(i) ?: continue
                    val conclusion = job.optString("conclusion")
                    if (conclusion == "failure" || conclusion == "cancelled" || conclusion == "timed_out") {
                        picked = job.getLong("id")
                        break
                    }
                }
                if (picked == null && jobs.length() > 0) {
                    picked = jobs.getJSONObject(0).getLong("id")
                }
                picked ?: throw GitHubApiException(404, "No jobs found for workflow run #$runId.")
            }

            val logUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "actions", "jobs", failedJobId.toString(), "logs")
                ?: throw GitHubApiException(null, "Invalid job logs URL.")
            val logReq = baseRequestBuilder(logUrl, config.token).get().build()
            executeFollowingSafeRedirects(logReq, config.token).use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val lines = raw.lines()
                lines.takeLast(tailLines).joinToString("\n")
            }
        }
    }

    suspend fun fetchLatestAndPreviousRelease(config: GitHubConfig): Result<GitHubReleasePair> =
        withContext(Dispatchers.IO) {
            val latestUrl = apiUrl("repos", config.owner.trim(), config.repo.trim(), "releases", "latest")
                ?: return@withContext Result.failure(GitHubApiException(null, "Invalid releases URL."))

            val latestReq = baseRequestBuilder(latestUrl, config.token).get().build()
            executeSafe {
                val latestRelease = executeFollowingSafeRedirects(latestReq, config.token).use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                    }
                    parseReleaseJson(JSONObject(raw))
                }

                // Also list recent releases so we know the previous release APK for safe Rollback
                val listUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegment("repos")
                    ?.addPathSegment(config.owner.trim())
                    ?.addPathSegment(config.repo.trim())
                    ?.addPathSegment("releases")
                    ?.addQueryParameter("per_page", "10")
                    ?.build()

                var previousRelease: GitHubReleaseInfo? = null
                if (listUrl != null) {
                    runCatching {
                        executeFollowingSafeRedirects(
                            baseRequestBuilder(listUrl, config.token).get().build(),
                            config.token
                        ).use { resp ->
                            if (resp.isSuccessful) {
                                val arr = JSONArray(resp.body?.string().orEmpty())
                                for (i in 0 until arr.length()) {
                                    val rel = parseReleaseJson(arr.getJSONObject(i))
                                    if (!rel.tagName.equals(latestRelease.tagName, ignoreCase = true) &&
                                        rel.apkAsset != null
                                    ) {
                                        previousRelease = rel
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                GitHubReleasePair(latest = latestRelease, previous = previousRelease)
            }
        }

    suspend fun downloadReleaseApk(
        config: GitHubConfig,
        assetUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val parsed = assetUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid APK asset URL."))
        destinationFile.parentFile?.mkdirs()

        val req = Request.Builder()
            .url(parsed)
            .apply {
                if (config.token.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.token.trim()}")
                }
            }
            .addHeader("Accept", "application/octet-stream")
            .addHeader("X-GitHub-Api-Version", API_VERSION)
            .get()
            .build()

        executeSafe {
            executeFollowingSafeRedirects(req, config.token, acceptHeader = "application/octet-stream").use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val body = resp.body ?: throw GitHubApiException(resp.code, "Empty APK download body.")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L
                onProgress(0f)

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0L) {
                                onProgress((bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(1f)
                destinationFile
            }
        }
    }

    suspend fun fetchReleaseSha256(
        config: GitHubConfig,
        asset: GitHubReleaseAsset
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = (asset.apiAssetUrl.ifBlank { asset.browserDownloadUrl }).toHttpUrlOrNull()
            ?: return@withContext Result.failure(GitHubApiException(null, "Invalid SHA-256 asset URL."))
        val req = Request.Builder()
            .url(url)
            .apply {
                if (config.token.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.token.trim()}")
                }
            }
            .addHeader("Accept", "application/octet-stream")
            .addHeader("X-GitHub-Api-Version", API_VERSION)
            .get()
            .build()
        executeSafe {
            executeFollowingSafeRedirects(req, config.token, acceptHeader = "application/octet-stream").use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    throw GitHubApiException(resp.code, mapHttpError(resp.code, extractMessage(raw), resp))
                }
                val raw = resp.body?.string().orEmpty()
                Regex("""\b([A-Fa-f0-9]{64})\b""").find(raw)?.groupValues?.get(1)?.lowercase()
                    ?: throw GitHubApiException(422, "SHA-256 asset did not contain a valid 64-hex digest.")
            }
        }
    }

    private fun parseReleaseJson(obj: JSONObject): GitHubReleaseInfo {
        val assets = obj.optJSONArray("assets") ?: JSONArray()
        var apkAsset: GitHubReleaseAsset? = null
        var shaAsset: GitHubReleaseAsset? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true) && apkAsset == null) {
                apkAsset = GitHubReleaseAsset(
                    id = a.optLong("id", 0L),
                    name = name,
                    apiAssetUrl = a.optString("url").ifBlank { a.optString("browser_download_url") },
                    browserDownloadUrl = a.optString("browser_download_url"),
                    sizeBytes = a.optLong("size", 0L)
                )
            } else if ((name.endsWith(".sha256", ignoreCase = true) || name.endsWith(".sha256sum", ignoreCase = true)) && shaAsset == null) {
                shaAsset = GitHubReleaseAsset(
                    id = a.optLong("id", 0L),
                    name = name,
                    apiAssetUrl = a.optString("url").ifBlank { a.optString("browser_download_url") },
                    browserDownloadUrl = a.optString("browser_download_url"),
                    sizeBytes = a.optLong("size", 0L)
                )
            }
        }
        val bodyText = obj.optString("body")
        val bodySha = Regex("""\b([A-Fa-f0-9]{64})\b""").find(bodyText)?.groupValues?.get(1)?.lowercase()
        return GitHubReleaseInfo(
            tagName = obj.optString("tag_name"),
            name = obj.optString("name").ifBlank { obj.optString("tag_name") },
            body = bodyText,
            htmlUrl = obj.optString("html_url"),
            apkAsset = apkAsset,
            publishedAt = obj.optString("published_at"),
            sha256Asset = shaAsset,
            expectedSha256 = bodySha
        )
    }

    private fun apiUrl(vararg segments: String): HttpUrl? {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return null
        val builder = parsed.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun baseRequestBuilder(url: HttpUrl, token: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", API_VERSION)
            .apply {
                val cleanToken = token.trim()
                if (cleanToken.isNotEmpty()) {
                    addHeader("Authorization", "Bearer $cleanToken")
                }
            }
    }

    /**
     * Follows GitHub HTTP 301/302/307/308 redirects safely while stripping the Authorization header
     * when redirected to external asset/log storage hosts (e.g. objects.githubusercontent.com / Azure Blob).
     */
    private fun executeFollowingSafeRedirects(
        initialRequest: Request,
        token: String,
        acceptHeader: String = "application/vnd.github+json",
        maxHops: Int = 4
    ): Response {
        var currentReq = initialRequest
        val baseHost = baseUrl.toHttpUrlOrNull()?.host

        for (hop in 0 until maxHops) {
            val response = client.newCall(currentReq).execute()
            val code = response.code
            val location = response.header("Location")
            if (code in setOf(301, 302, 303, 307, 308) && !location.isNullOrBlank()) {
                val nextUrl = currentReq.url.resolve(location)
                response.close()
                if (nextUrl == null) {
                    throw GitHubApiException(code, "Invalid redirect Location URL from GitHub.")
                }
                val sameApiHost = baseHost != null && nextUrl.host.equals(baseHost, ignoreCase = true)
                val nextBuilder = Request.Builder()
                    .url(nextUrl)
                    .get()
                    .addHeader("Accept", acceptHeader)
                if (sameApiHost && token.isNotBlank()) {
                    nextBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
                }
                currentReq = nextBuilder.build()
            } else {
                return response
            }
        }
        return client.newCall(currentReq).execute()
    }

    private inline fun <T> executeSafe(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: GitHubApiException) {
            Result.failure(e)
        } catch (e: UnknownHostException) {
            Result.failure(GitHubApiException(null, "No internet connection. Check Wi-Fi or mobile data."))
        } catch (e: ConnectException) {
            Result.failure(GitHubApiException(null, "No internet connection. Could not reach GitHub servers."))
        } catch (e: SocketTimeoutException) {
            Result.failure(GitHubApiException(null, "Connection timed out. Check your internet and try again."))
        } catch (e: IOException) {
            Result.failure(GitHubApiException(null, "No internet / network error: ${e.localizedMessage ?: "I/O failure"}"))
        } catch (e: Exception) {
            Result.failure(GitHubApiException(null, e.localizedMessage ?: "Unexpected GitHub API error."))
        }
    }

    private fun extractMessage(rawBody: String): String {
        return runCatching {
            JSONObject(rawBody).optString("message").orEmpty()
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun mapHttpError(code: Int, detail: String = "", response: Response? = null): String {
            val remaining = response?.header("X-RateLimit-Remaining")
            val suffix = if (detail.isNotBlank()) " ($detail)" else ""
            return when (code) {
                401 -> "HTTP 401: Token galat ya expired hai. Check your GitHub Fine-grained token.$suffix"
                403 -> {
                    if (remaining == "0" || detail.contains("rate limit", ignoreCase = true)) {
                        "HTTP 403: GitHub API rate limit exceeded. Wait a moment or check token limits.$suffix"
                    } else {
                        "HTTP 403: Permission denied. Token needs Contents (Read & write), Actions (Read), Metadata (Read).$suffix"
                    }
                }
                404 -> "HTTP 404: Repo, branch, file, or release nahi mila. Verify Repo owner, Repo name, and Branch.$suffix"
                409 -> "HTTP 409: Merge/file conflict on GitHub ($detail)."
                422 -> "HTTP 422: GitHub rejected request validation$suffix."
                else -> "GitHub API error (HTTP $code)$suffix"
            }
        }
    }
}
