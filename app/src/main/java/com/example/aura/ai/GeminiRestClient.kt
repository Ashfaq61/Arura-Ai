package com.example.aura.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class GeminiTestResult(
    val isValid: Boolean,
    val message: String,
    val httpCode: Int? = null
)

data class GeminiGenerationResponse(
    val text: String,
    val modelUsed: String,
    val keyIndexUsed: Int,
    val failoverNotes: List<String> = emptyList()
)

class GeminiRestException(
    val httpCode: Int?,
    override val message: String
) : Exception(message)

/**
 * Direct REST client for the Google Gemini API (generativelanguage.googleapis.com)
 * using model gemini-2.5-flash with user-supplied API keys, multi-key 429 rate-limit failover,
 * and clear error mapping for HTTP 400/403, 429, and offline failures.
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
        modelId: String = DEFAULT_MODEL
    ): GeminiTestResult {
        val cleaned = apiKey.trim()
        if (cleaned.isBlank()) {
            return GeminiTestResult(isValid = false, message = "Enter a Gemini API key first.")
        }
        val result = generateContentSingleKey(
            apiKey = cleaned,
            prompt = "Reply with OK",
            systemInstruction = "",
            imageBitmap = null,
            modelId = modelId
        )
        return result.fold(
            onSuccess = {
                GeminiTestResult(isValid = true, message = "Key valid", httpCode = 200)
            },
            onFailure = { err ->
                val code = (err as? GeminiRestException)?.httpCode
                GeminiTestResult(
                    isValid = false,
                    message = err.message ?: "Failed to validate Gemini API key.",
                    httpCode = code
                )
            }
        )
    }

    suspend fun generateContentWithFailover(
        keys: List<String>,
        prompt: String,
        systemInstruction: String = "",
        imageBitmap: Bitmap? = null,
        modelId: String = DEFAULT_MODEL
    ): Result<GeminiGenerationResponse> {
        val validCandidates = keys.map { it.trim() }.filter { it.isNotBlank() }
        if (validCandidates.isEmpty()) {
            return Result.failure(GeminiRestException(null, "API key add karo: No Gemini API key saved."))
        }

        val failoverNotes = mutableListOf<String>()
        var lastError: Throwable? = null

        for ((index, key) in validCandidates.withIndex()) {
            val attempt = generateContentSingleKey(
                apiKey = key,
                prompt = prompt,
                systemInstruction = systemInstruction,
                imageBitmap = imageBitmap,
                modelId = modelId
            )
            if (attempt.isSuccess) {
                return Result.success(
                    GeminiGenerationResponse(
                        text = attempt.getOrThrow(),
                        modelUsed = modelId,
                        keyIndexUsed = index,
                        failoverNotes = failoverNotes
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
            // For non-429 errors (e.g., no internet or invalid key 400/403), stop unless another key might help
            if (httpCode == null) {
                // Network failure affects all keys identically
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
        const val DEFAULT_MODEL = "gemini-2.5-flash"

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
