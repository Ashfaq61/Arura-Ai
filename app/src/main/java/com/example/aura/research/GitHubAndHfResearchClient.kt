package com.example.aura.research

import com.example.aura.memory.ResearchProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GitHubAndHfResearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun searchGitHubProjects(
        query: String = "android ai agent mcp",
        deviceRamMb: Long = 4096
    ): Result<List<ResearchProjectEntity>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.github.com/search/repositories?q=$encoded&sort=stars&order=desc&per_page=8"
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
                    val licenseObj = obj.optJSONObject("license")
                    val license = licenseObj?.optString("spdx_id", "Unknown")?.takeIf { it != "null" && it.isNotBlank() } ?: "Review License"
                    val isPermissive = license in listOf("MIT", "Apache-2.0", "BSD-3-Clause", "BSD-2-Clause")
                    val securityNote = if (isPermissive) {
                        "Permissive license ($license); inspect dependencies before Gradle integration."
                    } else {
                        "License '$license' requires manual compliance verification."
                    }
                    val compat = if (lang.equals("Kotlin", true) || lang.equals("Java", true)) {
                        "Native Android / JVM compatible"
                    } else {
                        "Reference / MCP bridge ($lang) — wrap via HTTP/MCP, do not execute natively"
                    }

                    ResearchProjectEntity(
                        id = "gh_${obj.optLong("id")}",
                        sourcePlatform = "GITHUB",
                        name = fullName,
                        description = desc,
                        url = htmlUrl,
                        latestReleaseOrUpdate = "Updated $updated • $lang",
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

    suspend fun searchHuggingFaceModels(
        query: String = "gemma",
        deviceRamMb: Long = 4096
    ): Result<List<ResearchProjectEntity>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://huggingface.co/api/models?search=$encoded&sort=downloads&direction=-1&limit=8"
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
                    val license = tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:") ?: "Check Model Card"
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
