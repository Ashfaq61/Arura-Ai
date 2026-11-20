package com.example.aura.github

import android.content.Context
import android.os.Build
import java.util.Locale

const val DEFAULT_AURA_BRANCH = "aura-changes"
const val GITHUB_TOKEN_CREATE_URL = "https://github.com/settings/personal-access-tokens/new"
const val MAX_AUTO_FIX_ATTEMPTS = 3

data class GitHubConfig(
    val owner: String = "",
    val repo: String = "",
    val branch: String = DEFAULT_AURA_BRANCH,
    val token: String = "",
    val allowWorkflowEdits: Boolean = false
) {
    val isConfigured: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank()

    val effectivePushBranch: String
        get() = branch.trim().ifBlank { DEFAULT_AURA_BRANCH }
}

data class GitHubConnectionResult(
    val isOk: Boolean,
    val message: String,
    val defaultBranch: String = "main",
    val privateRepo: Boolean = false
)

enum class DiffLineType {
    HEADER,
    CONTEXT,
    ADDED,
    REMOVED
}

data class DiffLine(
    val type: DiffLineType,
    val text: String
)

data class GitHubFileDraft(
    val path: String,
    val newContent: String,
    val existingContent: String? = null,
    val existingSha: String? = null,
    val diffLines: List<DiffLine> = DiffUtility.computeUnifiedDiff(path, existingContent, newContent)
) {
    val isUpdate: Boolean get() = !existingSha.isNullOrBlank()
}

data class PendingGitHubPushProposal(
    val branch: String,
    val commitMessage: String,
    val explanation: String,
    val files: List<GitHubFileDraft>,
    val isAutoFixProposal: Boolean = false
)

data class GitHubPushResult(
    val commitSha: String,
    val commitUrl: String,
    val branch: String,
    val pushedPaths: List<String>
)

enum class CiBuildStage(val label: String) {
    IDLE("idle"),
    QUEUED("queued"),
    BUILDING("building"),
    SUCCESS("success"),
    FAILED("failed")
}

data class GitHubWorkflowRunStatus(
    val runId: Long,
    val name: String,
    val branch: String,
    val stage: CiBuildStage,
    val rawStatus: String,
    val rawConclusion: String?,
    val htmlUrl: String,
    val headSha: String,
    val updatedAt: String
)

data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val apiAssetUrl: String,
    val browserDownloadUrl: String,
    val sizeBytes: Long
)

data class GitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkAsset: GitHubReleaseAsset?,
    val publishedAt: String = "",
    val sha256Asset: GitHubReleaseAsset? = null,
    val expectedSha256: String? = null
)

data class GitHubReleasePair(
    val latest: GitHubReleaseInfo?,
    val previous: GitHubReleaseInfo?
)

data class GitHubPullRequestInfo(
    val number: Int,
    val title: String,
    val state: String,
    val headBranch: String,
    val baseBranch: String,
    val htmlUrl: String,
    val createdAt: String = ""
)

data class UpdateHistoryEntry(
    val fromVersion: String,
    val toVersion: String,
    val toVersionCode: Long,
    val sha256: String,
    val status: String, // VERIFIED, RECOVERY_CANDIDATE, FAILED, STARTED
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

data class SelfUpdateUiState(
    val installedVersionName: String = "1.0.16",
    val installedVersionCode: Long = 16L,
    val lastWorkingVersion: String = "",
    val latestRelease: GitHubReleaseInfo? = null,
    val previousRelease: GitHubReleaseInfo? = null,
    val isUpdateAvailable: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val needsUnknownSourcesPermission: Boolean = false,
    val setupBlockers: List<String> = emptyList(),
    val openPullRequests: List<GitHubPullRequestInfo> = emptyList(),
    val updateHistory: List<UpdateHistoryEntry> = emptyList()
)

object GitHubSetupDiagnostics {
    fun inspect(
        config: GitHubConfig,
        canRequestPackageInstalls: Boolean,
        latestRelease: GitHubReleaseInfo? = null
    ): List<String> {
        val blockers = mutableListOf<String>()
        if (config.owner.isBlank()) {
            blockers += "Missing GitHub repository owner in Settings -> GitHub."
        }
        if (config.repo.isBlank()) {
            blockers += "Missing GitHub repository name in Settings -> GitHub."
        }
        if (config.token.isBlank()) {
            blockers += "Missing GitHub Fine-grained PAT token — code push, CI log fetch, and authenticated release downloads are blocked."
        }
        if (!canRequestPackageInstalls) {
            blockers += "Missing Android REQUEST_INSTALL_PACKAGES ('Allow from this source') permission for in-place APK updates."
        }
        if (latestRelease != null && latestRelease.apkAsset == null) {
            blockers += "Latest GitHub release '${latestRelease.tagName}' has no signed .apk asset attached."
        }
        return blockers
    }
}

sealed interface PushGuardResult {
    data object Allowed : PushGuardResult
    data class Blocked(val reason: String, val blockingPath: String? = null) : PushGuardResult
}

/**
 * Enforces hard safety boundaries before Aura pushes any commit to GitHub:
 * 1. Never push directly to 'main' or 'master'.
 * 2. Block the workflows folder unless the explicit 'Allow workflow edits' switch is enabled.
 * 3. Always block signing files, keystores, and env/credential configs.
 * 4. Always block file contents containing tokens, API keys, or private keys.
 */
object GitHubPushGuard {

    private val protectedMainBranches = setOf("main", "master")

    private val signingAndCredentialExtensions = listOf(
        ".keystore", ".jks", ".p12", ".pfx", ".pem", ".pk8", ".key"
    )

    private val signingAndCredentialExactNames = setOf(
        "debug.keystore",
        "debug.keystore.base64",
        "my-upload-key.jks",
        "google-services.json",
        "local.properties",
        ".env",
        ".env.local",
        ".env.production"
    )

    private val secretPatterns = listOf(
        Regex("""github_pat_[A-Za-z0-9_]{12,}"""),
        Regex("""gh[pous]_[A-Za-z0-9]{12,}"""),
        Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
        Regex("""sk-[A-Za-z0-9]{16,}"""),
        Regex("""gsk_[A-Za-z0-9]{16,}"""),
        Regex("""hf_[A-Za-z0-9]{16,}"""),
        Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----""")
    )

    fun normalizeRepoPath(path: String): String {
        return path.trim()
            .replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")
    }

    fun validateProposal(
        files: List<GitHubFileDraft>,
        targetBranch: String,
        allowWorkflowEdits: Boolean,
        knownSecrets: List<String> = emptyList()
    ): PushGuardResult {
        val cleanBranch = targetBranch.trim()
        if (cleanBranch.isBlank() || cleanBranch.lowercase(Locale.US) in protectedMainBranches) {
            return PushGuardResult.Blocked(
                reason = "Hard block: Aura may NOT push directly to '$cleanBranch'. Use branch '$DEFAULT_AURA_BRANCH'."
            )
        }
        if (files.isEmpty()) {
            return PushGuardResult.Blocked("No files selected for GitHub push.")
        }

        val activeSecrets = knownSecrets.map { it.trim() }.filter { it.length >= 10 }

        for (file in files) {
            val cleanPath = normalizeRepoPath(file.path)
            if (cleanPath.isBlank() || cleanPath.contains("..")) {
                return PushGuardResult.Blocked("Invalid repository file path: '${file.path}'.", file.path)
            }
            val lowerPath = cleanPath.lowercase(Locale.US)
            val fileName = lowerPath.substringAfterLast('/')

            if (lowerPath.startsWith(".github/workflows/") && !allowWorkflowEdits) {
                return PushGuardResult.Blocked(
                    reason = "Hard block: Editing '$cleanPath' is disabled. Turn on 'Allow workflow edits' in GitHub Settings to allow workflow changes.",
                    blockingPath = cleanPath
                )
            }

            if (fileName in signingAndCredentialExactNames ||
                signingAndCredentialExtensions.any { fileName.endsWith(it) } ||
                lowerPath.contains("keystore") && !lowerPath.contains("keystore.kt") && !lowerPath.contains("test")
            ) {
                return PushGuardResult.Blocked(
                    reason = "Hard block: Signing/credential file '$cleanPath' cannot be pushed by Aura.",
                    blockingPath = cleanPath
                )
            }

            if (fileName == "signing" || (lowerPath.contains("signingconfig") && lowerPath.endsWith(".properties"))) {
                return PushGuardResult.Blocked(
                    reason = "Hard block: Signing configuration '$cleanPath' cannot be pushed by Aura.",
                    blockingPath = cleanPath
                )
            }

            for (pattern in secretPatterns) {
                if (pattern.containsMatchIn(file.newContent)) {
                    return PushGuardResult.Blocked(
                        reason = "Hard block: File '$cleanPath' contains a key, token, or private key pattern and cannot be pushed.",
                        blockingPath = cleanPath
                    )
                }
            }

            for (secret in activeSecrets) {
                if (file.newContent.contains(secret)) {
                    return PushGuardResult.Blocked(
                        reason = "Hard block: File '$cleanPath' contains a saved token/API key and cannot be pushed.",
                        blockingPath = cleanPath
                    )
                }
            }
        }
        return PushGuardResult.Allowed
    }
}

object DiffUtility {
    fun computeUnifiedDiff(
        path: String,
        oldContent: String?,
        newContent: String,
        maxLines: Int = 220
    ): List<DiffLine> {
        val cleanPath = GitHubPushGuard.normalizeRepoPath(path)
        val newLines = newContent.lines()
        if (oldContent == null) {
            val out = mutableListOf(DiffLine(DiffLineType.HEADER, "+++ b/$cleanPath (new file)"))
            newLines.take(maxLines).forEach { line ->
                out.add(DiffLine(DiffLineType.ADDED, "+ $line"))
            }
            if (newLines.size > maxLines) {
                out.add(DiffLine(DiffLineType.HEADER, "... (${newLines.size - maxLines} more lines)"))
            }
            return out
        }

        val oldLines = oldContent.lines()
        val out = mutableListOf(
            DiffLine(DiffLineType.HEADER, "--- a/$cleanPath"),
            DiffLine(DiffLineType.HEADER, "+++ b/$cleanPath")
        )

        val lcs = computeLcsMatrix(oldLines, newLines)
        var i = oldLines.size
        var j = newLines.size
        val reversedOps = mutableListOf<DiffLine>()

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
                    reversedOps.add(DiffLine(DiffLineType.CONTEXT, "  ${oldLines[i - 1]}"))
                    i--
                    j--
                }
                j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j]) -> {
                    reversedOps.add(DiffLine(DiffLineType.ADDED, "+ ${newLines[j - 1]}"))
                    j--
                }
                else -> {
                    reversedOps.add(DiffLine(DiffLineType.REMOVED, "- ${oldLines[i - 1]}"))
                    i--
                }
            }
        }

        val fullOps = reversedOps.asReversed()
        val changeIndices = fullOps.indices.filter { fullOps[it].type != DiffLineType.CONTEXT }
        if (changeIndices.isEmpty()) {
            out.add(DiffLine(DiffLineType.CONTEXT, "  (No line changes detected)"))
            return out
        }

        val keepMask = BooleanArray(fullOps.size)
        for (idx in changeIndices) {
            val start = (idx - 2).coerceAtLeast(0)
            val end = (idx + 2).coerceAtMost(fullOps.lastIndex)
            for (k in start..end) keepMask[k] = true
        }

        var inGap = false
        for (idx in fullOps.indices) {
            if (keepMask[idx]) {
                out.add(fullOps[idx])
                inGap = false
            } else if (!inGap) {
                out.add(DiffLine(DiffLineType.HEADER, "@@ ... @@"))
                inGap = true
            }
            if (out.size >= maxLines) {
                out.add(DiffLine(DiffLineType.HEADER, "... (diff truncated at $maxLines lines)"))
                break
            }
        }
        return out
    }

    private fun computeLcsMatrix(a: List<String>, b: List<String>): Array<IntArray> {
        if (a.size > 400 || b.size > 400) {
            return Array(a.size + 1) { IntArray(b.size + 1) }
        }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp
    }
}

object ApkVersionComparator {

    fun parseVersionSegments(tagOrVersion: String): List<Int> {
        val cleaned = tagOrVersion.trim()
            .removePrefix("refs/tags/")
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split('.').mapNotNull { segment ->
            segment.filter { it.isDigit() }.toIntOrNull()
        }
    }

    fun isNewerVersion(remoteTag: String, installedVersion: String): Boolean {
        val remoteParts = parseVersionSegments(remoteTag)
        val localParts = parseVersionSegments(installedVersion)
        if (remoteParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun getInstalledVersionInfo(context: Context): Pair<String, Long> {
        return runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = pkgInfo.versionName ?: "1.0.1"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            versionName to versionCode
        }.getOrDefault("1.0.1" to 1L)
    }
}
