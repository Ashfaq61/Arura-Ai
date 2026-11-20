package com.example.aura.research

import com.example.aura.github.ApkVersionComparator
import com.example.aura.memory.MarketplaceToolEntity
import com.example.aura.memory.ResearchProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GitHubRepoInspection(
    val fullName: String,
    val stars: Int,
    val licenseSpdx: String,
    val archived: Boolean,
    val disabled: Boolean,
    val defaultBranch: String,
    val latestReleaseTag: String,
    val openIssues: Int,
    val compatibilityNote: String
)

data class HuggingFaceModelInspection(
    val modelId: String,
    val pipelineTag: String,
    val license: String,
    val downloads: Long,
    val likes: Int,
    val parameterCountApprox: Long,
    val estimatedRamGb: Double,
    val fitsDeviceRam: Boolean,
    val note: String
)

data class ResearchDeduplicationResult(
    val candidate: ResearchProjectEntity,
    val isDuplicate: Boolean,
    val hasNewerRelease: Boolean,
    val existingRefName: String,
    val reason: String
)

class GitHubAndHfResearchClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val gitHubApiBaseUrl: String = "https://api.github.com",
    private val huggingFaceApiBaseUrl: String = "https://huggingface.co",
    private val androidDocsBaseUrl: String = "https://developer.android.com"
) {

    suspend fun searchGitHubProjects(
        query: String = "android ai agent mcp",
        deviceRamMb: Long = 4096
    ): Result<List<ResearchProjectEntity>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val base = gitHubApiBaseUrl.trimEnd('/')
            val url = "$base/search/repositories?q=$encoded&sort=stars&order=desc&per_page=8"
            val req = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "AURA-AI-Android-Research-Agent")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API HTTP ${resp.code}"))
                }
                val body = resp.body?.string().orEmpty()
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val list = (0 until items.length()).map { idx ->
                    val obj = items.getJSONObject(idx)
                    val fullName = obj.optString("full_name")
                    val htmlUrl = obj.optString("html_url")
                    val desc = obj.optString("description", "No description provided.").ifBlank { "No description provided." }
                    val stars = obj.optInt("stargazers_count", 0)
                    val lang = obj.optString("language", "Mixed")
                    val updated = obj.optString("updated_at", "").take(10)
                    val archived = obj.optBoolean("archived", false)
                    val licenseObj = obj.optJSONObject("license")
                    val license = licenseObj?.optString("spdx_id", "Unknown")?.takeIf { it != "null" && it.isNotBlank() } ?: "Review License"
                    val isPermissive = license in listOf("MIT", "Apache-2.0", "BSD-3-Clause", "BSD-2-Clause")
                    val securityNote = when {
                        archived -> "ARCHIVED repository — read-only reference only, do not adopt as active dependency."
                        isPermissive -> "Permissive license ($license); inspect dependencies before Gradle integration."
                        else -> "License '$license' requires compliance verification."
                    }
                    val compat = if (lang.equals("Kotlin", true) || lang.equals("Java", true)) {
                        "Native Android / JVM compatible"
                    } else {
                        "Reference / MCP bridge ($lang) — wrap via HTTP/MCP, do not execute natively"
                    }

                    val rawId = obj.optLong("id", 0L)
                    val uniqueId = if (rawId != 0L) rawId.toString() else fullName.hashCode().toUInt().toString()
                    ResearchProjectEntity(
                        id = "gh_$uniqueId",
                        sourcePlatform = "GITHUB",
                        name = fullName,
                        description = desc,
                        url = htmlUrl,
                        latestReleaseOrUpdate = if (archived) "ARCHIVED ($updated • $lang)" else "Updated $updated • $lang",
                        license = license,
                        compatibility = compat,
                        dependencies = if (lang.equals("Kotlin", true)) "Gradle / JVM" else "$lang runtime / API bridge",
                        securityAssessment = securityNote,
                        resourceEstimate = if (deviceRamMb >= 3500) "Low-Medium (API/Tool)" else "Cloud/Lightweight bridge recommended",
                        installationStatus = "NOT_INSTALLED",
                        testStatus = "UNTESTED",
                        recommendationReason = "Trending ($stars ★) matching '$query'. Useful reference for AURA capability expansion.",
                        metricsLabel = "$stars ★"
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectGitHubRepoDetails(fullName: String): Result<GitHubRepoInspection> = withContext(Dispatchers.IO) {
        val clean = fullName.trim().trim('/')
        val base = gitHubApiBaseUrl.trimEnd('/')
        if (!clean.contains('/')) {
            return@withContext Result.failure(IllegalArgumentException("Expected owner/repo format, got '$fullName'."))
        }
        runCatching {
            val repoReq = Request.Builder()
                .url("$base/repos/$clean")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "AURA-AI-Android-Research-Agent")
                .build()
            val repoJson = client.newCall(repoReq).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub repo inspect HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }
            val latestTag = runCatching {
                val relReq = Request.Builder()
                    .url("$base/repos/$clean/releases/latest")
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("User-Agent", "AURA-AI-Android-Research-Agent")
                    .build()
                client.newCall(relReq).execute().use { r ->
                    if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()).optString("tag_name") else ""
                }
            }.getOrDefault("")
            val archived = repoJson.optBoolean("archived", false)
            val disabled = repoJson.optBoolean("disabled", false)
            val license = repoJson.optJSONObject("license")?.optString("spdx_id")
                ?.takeIf { it.isNotBlank() && it != "null" } ?: "Review License"
            val lang = repoJson.optString("language", "Mixed")
            GitHubRepoInspection(
                fullName = repoJson.optString("full_name", clean),
                stars = repoJson.optInt("stargazers_count", 0),
                licenseSpdx = license,
                archived = archived,
                disabled = disabled,
                defaultBranch = repoJson.optString("default_branch", "main"),
                latestReleaseTag = latestTag,
                openIssues = repoJson.optInt("open_issues_count", 0),
                compatibilityNote = when {
                    archived || disabled -> "BLOCKED: Repository is archived/disabled."
                    lang.equals("Kotlin", true) || lang.equals("Java", true) -> "Native Android/JVM source candidate ($lang)."
                    else -> "Non-JVM ($lang) — expose via MCP/HTTP bridge or adapt in Kotlin."
                }
            )
        }
    }

    suspend fun searchHuggingFaceModels(
        query: String = "gemma",
        deviceRamMb: Long = 4096
    ): Result<List<ResearchProjectEntity>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val base = huggingFaceApiBaseUrl.trimEnd('/')
            val url = "$base/api/models?search=$encoded&sort=downloads&direction=-1&limit=8"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "AURA-AI-Android-Research-Agent")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Hugging Face API HTTP ${resp.code}"))
                }
                val arr = JSONArray(resp.body?.string().orEmpty())
                val list = (0 until arr.length()).map { idx ->
                    val obj = arr.getJSONObject(idx)
                    val modelId = obj.optString("id", "unknown/model")
                    val downloads = obj.optLong("downloads", 0L)
                    val likes = obj.optInt("likes", 0)
                    val pipeline = obj.optString("pipeline_tag", "text-generation").ifBlank { "multimodal/llm" }
                    val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                    val tags = (0 until tagsArr.length()).map { tagsArr.optString(it) }
                    val cardLicense = obj.optJSONObject("cardData")?.optString("license").orEmpty().takeIf { it.isNotBlank() }
                    val license = tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:")
                        ?: cardLicense
                        ?: "Check Model Card"
                    val isSmallQuant = modelId.contains("1b", true) || modelId.contains("2b", true) ||
                        modelId.contains("3b", true) || modelId.contains("gguf", true) || modelId.contains("whisper", true)
                    val compat = if (isSmallQuant && deviceRamMb >= 3500) {
                        "Candidate for quantized mobile edge or HF Inference API"
                    } else {
                        "Cloud HF Inference API recommended (exceeds typical mobile RAM)"
                    }
                    val resourceEst = if (isSmallQuant) "~1.5–3.0 GB RAM (Q4)" else "Cloud GPU / HF Inference Router"

                    ResearchProjectEntity(
                        id = "hf_${modelId.replace("/", "_")}",
                        sourcePlatform = "HUGGING_FACE",
                        name = modelId,
                        description = "Task: $pipeline • Downloads: $downloads • Likes: $likes",
                        url = "https://huggingface.co/$modelId",
                        latestReleaseOrUpdate = "Pipeline: $pipeline",
                        license = license,
                        compatibility = compat,
                        dependencies = "HF Inference API / LiteRT / GGUF runtime",
                        securityAssessment = "Verify model card terms & SHA checksums before downloading weights.",
                        resourceEstimate = resourceEst,
                        installationStatus = "NOT_INSTALLED",
                        testStatus = "UNTESTED",
                        recommendationReason = "High adoption ($downloads downloads) for $pipeline.",
                        metricsLabel = "$downloads DL • $likes ♥"
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectHuggingFaceModel(
        modelId: String,
        deviceRamMb: Long = 4096
    ): Result<HuggingFaceModelInspection> = withContext(Dispatchers.IO) {
        val clean = modelId.trim().trim('/')
        val base = huggingFaceApiBaseUrl.trimEnd('/')
        runCatching {
            val req = Request.Builder()
                .url("$base/api/models/$clean")
                .addHeader("User-Agent", "AURA-AI-Android-Research-Agent")
                .build()
            val obj = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HF model inspect HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }
            val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArr.length()).map { tagsArr.optString(it) }
            val license = tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:")
                ?: obj.optJSONObject("cardData")?.optString("license")?.takeIf { it.isNotBlank() }
                ?: "Check Model Card"
            val params = obj.optJSONObject("safetensors")?.optLong("total", 0L) ?: 0L
            val estRamGb = when {
                params > 0L -> (params.toDouble() / 1_000_000_000.0) * 0.65 // ~Q4 quantized footprint
                clean.contains("70b", true) -> 42.0
                clean.contains("32b", true) -> 20.0
                clean.contains("14b", true) -> 9.0
                clean.contains("7b", true) || clean.contains("8b", true) -> 5.2
                clean.contains("3b", true) || clean.contains("4b", true) -> 2.6
                else -> 1.8
            }
            val fits = (estRamGb * 1024.0) <= (deviceRamMb * 0.65)
            HuggingFaceModelInspection(
                modelId = obj.optString("id", clean),
                pipelineTag = obj.optString("pipeline_tag", "text-generation"),
                license = license,
                downloads = obj.optLong("downloads", 0L),
                likes = obj.optInt("likes", 0),
                parameterCountApprox = params,
                estimatedRamGb = estRamGb,
                fitsDeviceRam = fits,
                note = if (fits) {
                    "Fits on-device quantized estimate (~${"%.1f".format(estRamGb)} GB) or HF Inference Router."
                } else {
                    "Exceeds safe device RAM ($deviceRamMb MB); route via cloud HF Inference API."
                }
            )
        }
    }

    suspend fun searchMcpAndA2aDirectories(
        deviceRamMb: Long = 4096
    ): Result<List<ResearchProjectEntity>> {
        val mcpRes = searchGitHubProjects("topic:mcp-server OR modelcontextprotocol", deviceRamMb).getOrDefault(emptyList())
        val a2aRes = searchGitHubProjects("a2a-protocol OR \"agent-to-agent\" json-rpc", deviceRamMb).getOrDefault(emptyList())
        val tagged = (mcpRes.take(4).map { it.copy(sourcePlatform = "MCP_DIRECTORY") } +
            a2aRes.take(4).map { it.copy(sourcePlatform = "A2A_DIRECTORY") })
        return if (tagged.isNotEmpty()) Result.success(tagged)
        else Result.failure(IllegalStateException("MCP/A2A directory query returned empty or offline."))
    }

    suspend fun searchAndroidDeveloperDocs(topic: String): Result<List<ResearchProjectEntity>> {
        val base = androidDocsBaseUrl.trimEnd('/')
        val q = URLEncoder.encode(topic.trim(), "UTF-8")
        val targetUrl = "$base/s/results?q=$q"
        return fetchWebPageSummaryText(targetUrl).map { summary ->
            listOf(
                ResearchProjectEntity(
                    id = "adoc_${topic.trim().lowercase().hashCode().toUInt()}",
                    sourcePlatform = "ANDROID_DOCS",
                    name = "Android Docs: ${topic.trim()}",
                    description = summary.take(280).ifBlank { "Official Android Developer documentation query for $topic." },
                    url = targetUrl,
                    latestReleaseOrUpdate = "Official Android Jetpack / API Guide",
                    license = "Apache-2.0",
                    compatibility = "Official Android SDK & Jetpack Compose standard",
                    dependencies = "AndroidX / Jetpack",
                    securityAssessment = "Trusted first-party documentation source.",
                    resourceEstimate = "Build-time library reference",
                    installationStatus = "NOT_INSTALLED",
                    testStatus = "UNTESTED",
                    recommendationReason = "Official developer docs check for '$topic'.",
                    metricsLabel = "Official Docs"
                )
            )
        }
    }

    fun deduplicateAndCompareVersions(
        candidates: List<ResearchProjectEntity>,
        existingProjects: List<ResearchProjectEntity>,
        existingTools: List<MarketplaceToolEntity>
    ): List<ResearchDeduplicationResult> {
        val existingByUrl = existingProjects.associateBy { it.url.trim().lowercase() }
        val existingByName = existingProjects.associateBy { it.name.trim().lowercase() }
        val toolNames = existingTools.map { it.name.trim().lowercase() }

        return candidates.map { cand ->
            val normUrl = cand.url.trim().lowercase()
            val normName = cand.name.trim().lowercase()
            val matchProj = existingByUrl[normUrl] ?: existingByName[normName]
            val matchTool = toolNames.firstOrNull { t ->
                normName.contains(t.removePrefix("mcp_")) || t.contains(normName.substringAfterLast('/'))
            }
            if (matchProj == null && matchTool == null) {
                ResearchDeduplicationResult(
                    candidate = cand,
                    isDuplicate = false,
                    hasNewerRelease = false,
                    existingRefName = "",
                    reason = "Novel candidate (not yet tracked in Research DB or MCP tools)."
                )
            } else {
                val newer = matchProj != null && ApkVersionComparator.isNewerVersion(
                    remoteTag = cand.latestReleaseOrUpdate,
                    installedVersion = matchProj.latestReleaseOrUpdate
                )
                ResearchDeduplicationResult(
                    candidate = cand,
                    isDuplicate = !newer,
                    hasNewerRelease = newer,
                    existingRefName = matchProj?.name ?: matchTool.orEmpty(),
                    reason = if (newer) {
                        "Newer version detected (${cand.latestReleaseOrUpdate} vs ${matchProj?.latestReleaseOrUpdate})."
                    } else {
                        "Already tracked as '${matchProj?.name ?: matchTool}'."
                    }
                )
            }
        }
    }

    suspend fun fetchWebPageSummaryText(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val req = Request.Builder()
                .url(normalized)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; AURA-AI Research Agent)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code} fetching $normalized"))
                }
                val html = resp.body?.string().orEmpty()
                val stripped = html
                    .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Result.success(stripped.take(4000))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

