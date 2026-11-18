package com.example.aura.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

const val GEMINI_DEFAULT_MODEL = "gemini-3.8-flash"

data class GeminiTestResult(
    val isValid: Boolean,
    val message: String,
    val httpCode: Int? = null,
    val resolvedModel: String = GEMINI_DEFAULT_MODEL,
    val errorNotice: String? = null
)

data class GeminiGenerationResponse(
    val text: String,
    val modelUsed: String,
    val keyIndexUsed: Int,
    val failoverNotes: List<String> = emptyList(),
    val errorNotice: String? = null
)

class GeminiRestException(
    val httpCode: Int?,
    override val message: String
) : Exception(message)

/**
 * Direct REST client for the Google Gemini API (generativelanguage.googleapis.com)
 * defaulting to [GEMINI_DEFAULT_MODEL] with user-supplied API keys, multi-key 429 failover,
 * HTTP 404 ListModels auto-fallback, and clear error mapping for HTTP 400/403, 429, and offline failures.
 */
class GeminiRestClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    suspend fun testApiKey(
        apiKey: String,
        modelId: String = GEMINI_DEFAULT_MODEL,
        onModelError: (suspend (String) -> Unit)? = null,
        onModelFallback: (suspend (String) -> Unit)? = null
    ): GeminiTestResult {
        val cleaned = apiKey.trim()
        val requestedModel = modelId.trim().ifBlank { GEMINI_DEFAULT_MODEL }
        if (cleaned.isBlank()) {
            return GeminiTestResult(
                isValid = false,
                message = "Enter a Gemini API key first.",
                resolvedModel = requestedModel
            )
        }
        val result = generateContentWithFailover(
            keys = listOf(cleaned),
            prompt = "Reply with OK",
            systemInstruction = "",
            imageBitmap = null,
            modelId = requestedModel,
            onModelError = onModelError,
            onModelFallback = onModelFallback
        )
        return result.fold(
            onSuccess = { gen ->
                val statusMessage = if (gen.errorNotice != null && !gen.modelUsed.equals(requestedModel, ignoreCase = true)) {
                    "${gen.errorNotice}\nAuto-switched to ${gen.modelUsed}: Key valid"
                } else {
                    "Key valid"
                }
                GeminiTestResult(
                    isValid = true,
                    message = statusMessage,
                    httpCode = 200,
                    resolvedModel = gen.modelUsed,
                    errorNotice = gen.errorNotice
                )
            },
            onFailure = { err ->
                val code = (err as? GeminiRestException)?.httpCode
                GeminiTestResult(
                    isValid = false,
                    message = err.message ?: "Failed to validate Gemini API key.",
                    httpCode = code,
                    resolvedModel = requestedModel,
                    errorNotice = if (code == 404) err.message else null
                )
            }
        )
    }

    suspend fun generateContentWithFailover(
        keys: List<String>,
        prompt: String,
        systemInstruction: String = "",
        imageBitmap: Bitmap? = null,
        modelId: String = GEMINI_DEFAULT_MODEL,
        onModelError: (suspend (String) -> Unit)? = null,
        onModelFallback: (suspend (String) -> Unit)? = null
    ): Result<GeminiGenerationResponse> {
        val validCandidates = keys.map { it.trim() }.filter { it.isNotBlank() }
        if (validCandidates.isEmpty()) {
            return Result.failure(GeminiRestException(null, "API key add karo: No Gemini API key saved."))
        }

        var activeModel = modelId.trim().ifBlank { GEMINI_DEFAULT_MODEL }
        var hasAttemptedModelFallback = false
        var captured404Notice: String? = null
        val failoverNotes = mutableListOf<String>()
        var lastError: Throwable? = null

        for ((index, key) in validCandidates.withIndex()) {
            var attempt = generateContentSingleKey(
                apiKey = key,
                prompt = prompt,
                systemInstruction = systemInstruction,
                imageBitmap = imageBitmap,
                modelId = activeModel
            )

            val initialCode = (attempt.exceptionOrNull() as? GeminiRestException)?.httpCode
            if (attempt.isFailure && initialCode == 404 && !hasAttemptedModelFallback) {
                hasAttemptedModelFallback = true
                val err404Msg = attempt.exceptionOrNull()?.message
                    ?: mapHttpError(404, "Model '$activeModel' not found.")
                captured404Notice = err404Msg
                failoverNotes.add(err404Msg)
                onModelError?.invoke(err404Msg)

                val discovered = fetchFirstFlashGenerateContentModel(key, excludeModel = activeModel)
                if (discovered.isSuccess) {
                    val fallbackModel = discovered.getOrThrow()
                    failoverNotes.add("Auto-switched model from '$activeModel' to '$fallbackModel' via ListModels.")
                    activeModel = fallbackModel
                    onModelFallback?.invoke(fallbackModel)
                    attempt = generateContentSingleKey(
                        apiKey = key,
                        prompt = prompt,
                        systemInstruction = systemInstruction,
                        imageBitmap = imageBitmap,
                        modelId = activeModel
                    )
                } else {
                    val listErr = discovered.exceptionOrNull()?.message
                    val combinedMsg = if (!listErr.isNullOrBlank()) "$err404Msg ($listErr)" else err404Msg
                    return Result.failure(GeminiRestException(404, combinedMsg))
                }
            }

            if (attempt.isSuccess) {
                return Result.success(
                    GeminiGenerationResponse(
                        text = attempt.getOrThrow(),
                        modelUsed = activeModel,
                        keyIndexUsed = index,
                        failoverNotes = failoverNotes,
                        errorNotice = captured404Notice
                    )
                )
            }

            val err = attempt.exceptionOrNull()
            lastError = err
            val httpCode = (err as? GeminiRestException)?.httpCode
            if (httpCode == 429 && index < validCandidates.lastIndex) {
                failoverNotes.add("Key #${index + 1} hit rate limit (HTTP 429); failing over to Key #${index + 2}.")
                continue
            }
            if (httpCode == 429) {
                failoverNotes.add("Key #${index + 1} hit rate limit (HTTP 429).")
                continue
            }
            if (httpCode == null) {
                return Result.failure(err ?: GeminiRestException(null, "No internet connection."))
            }
            if ((httpCode == 400 || httpCode == 403) && index < validCandidates.lastIndex) {
                failoverNotes.add("Key #${index + 1} rejected (HTTP $httpCode); trying Key #${index + 2}.")
                continue
            }
            return Result.failure(err ?: GeminiRestException(httpCode, "Gemini API request failed."))
        }

        return Result.failure(
            lastError ?: GeminiRestException(429, "Rate limit reached (HTTP 429) on all saved keys. Try again later.")
        )
    }

    suspend fun fetchFirstFlashGenerateContentModel(
        apiKey: String,
        excludeModel: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(GeminiRestException(null, "Invalid Gemini base URL: $baseUrl"))
            val listUrl = parsedBase.newBuilder()
                .addPathSegment("v1beta")
                .addPathSegment("models")
                .addQueryParameter("key", apiKey.trim())
                .build()

            val request = Request.Builder()
                .url(listUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractApiErrorMessage(rawBody)
                    return@withContext Result.failure(
                        GeminiRestException(response.code, mapHttpError(response.code, detail))
                    )
                }
                val models = JSONObject(rawBody).optJSONArray("models")
                    ?: return@withContext Result.failure(
                        GeminiRestException(404, "ListModels returned no available models.")
                    )

                for (i in 0 until models.length()) {
                    val obj = models.optJSONObject(i) ?: continue
                    val cleanId = obj.optString("name").removePrefix("models/").trim()
                    if (cleanId.isBlank() || !cleanId.contains("flash", ignoreCase = true)) continue
                    if (excludeModel != null && cleanId.equals(excludeModel, ignoreCase = true)) continue

                    val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                    var supportsGenerate = false
                    for (m in 0 until methods.length()) {
                        if (methods.optString(m) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                    if (supportsGenerate) {
                        return@withContext Result.success(cleanId)
                    }
                }

                Result.failure(
                    GeminiRestException(404, "No available Flash model supporting generateContent found via ListModels.")
                )
            }
        } catch (e: UnknownHostException) {
            Result.failure(GeminiRestException(null, "No internet connection. Please check Wi-Fi or mobile data."))
        } catch (e: ConnectException) {
            Result.failure(GeminiRestException(null, "No internet connection. Unable to reach Gemini servers."))
        } catch (e: SocketTimeoutException) {
            Result.failure(GeminiRestException(null, "Connection timed out. Check your internet connection and try again."))
        } catch (e: IOException) {
            Result.failure(GeminiRestException(null, "Network error: ${e.localizedMessage ?: "No internet connection."}"))
        } catch (e: Exception) {
            Result.failure(GeminiRestException(null, e.localizedMessage ?: "Failed to query ListModels."))
        }
    }

    private suspend fun generateContentSingleKey(
        apiKey: String,
        prompt: String,
        systemInstruction: String,
        imageBitmap: Bitmap?,
        modelId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parts = JSONArray().apply {
                if (imageBitmap != null) {
                    val stream = ByteArrayOutputStream()
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    put(
                        JSONObject().apply {
                            put(
                                "inlineData",
                                JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", b64)
                                }
                            )
                        }
                    )
                }
                put(JSONObject().apply { put("text", prompt) })
            }

            val bodyJson = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                        }
                    )
                }
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", parts)
                        }
                    )
                )
            }

            val url = "$baseUrl/v1beta/models/$modelId:generateContent"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = extractApiErrorMessage(rawBody)
                    val mapped = mapHttpError(response.code, apiMessage)
                    return@withContext Result.failure(GeminiRestException(response.code, mapped))
                }
                val text = extractCandidateText(rawBody)
                if (text.isBlank()) {
                    Result.failure(GeminiRestException(response.code, "Gemini returned an empty response."))
                } else {
                    Result.success(text.trim())
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(GeminiRestException(null, "No internet connection. Please check Wi-Fi or mobile data."))
        } catch (e: ConnectException) {
            Result.failure(GeminiRestException(null, "No internet connection. Unable to reach Gemini servers."))
        } catch (e: SocketTimeoutException) {
            Result.failure(GeminiRestException(null, "Connection timed out. Check your internet connection and try again."))
        } catch (e: IOException) {
            Result.failure(GeminiRestException(null, "Network error: ${e.localizedMessage ?: "No internet connection."}"))
        } catch (e: Exception) {
            Result.failure(GeminiRestException(null, e.localizedMessage ?: "Unexpected Gemini API error."))
        }
    }

    private fun extractCandidateText(rawBody: String): String {
        val json = JSONObject(rawBody)
        val candidates = json.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val content = first.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (partText.isNotEmpty()) append(partText)
            }
        }
    }

    private fun extractApiErrorMessage(rawBody: String): String {
        return runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = GEMINI_DEFAULT_MODEL

        fun mapHttpError(code: Int, detail: String = ""): String {
            return when (code) {
                400, 403 -> "Invalid Gemini API key (HTTP $code). Check your key in Google AI Studio.${if (detail.isNotBlank()) " ($detail)" else ""}"
                429 -> "Rate limit reached (HTTP 429). Auto-switching to next key or try again shortly."
                in 500..599 -> "Gemini service temporarily unavailable (HTTP $code). Try again shortly."
                else -> "Gemini API error (HTTP $code)${if (detail.isNotBlank()) ": $detail" else "."}"
            }
        }
    }
}
