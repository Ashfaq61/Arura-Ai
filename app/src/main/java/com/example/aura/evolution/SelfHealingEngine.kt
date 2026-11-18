package com.example.aura.evolution

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FailureCategory {
    API_TIMEOUT,
    NETWORK_FAILURE,
    MODEL_ERROR,
    ACCESSIBILITY_INTERRUPTED,
    UI_CHANGED,
    MISSING_PERMISSION,
    CRASH,
    BG_TASK_TERMINATED,
    BUILD_OR_DEP_CONFLICT,
    LOW_STORAGE_OR_MEMORY
}

data class HealingEvent(
    val category: FailureCategory,
    val actionName: String,
    val recoveryStrategy: String,
    val succeeded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Advanced Self-Healing Engine:
 * Classifies failures, executes controlled retries/fallbacks, and STRICTLY prevents
 * automatic retries on non-idempotent actions (messages, payments, calls, deletions).
 */
class SelfHealingEngine(private val context: Context) {

    private val _healingHistory = MutableStateFlow<List<HealingEvent>>(emptyList())
    val healingHistory: StateFlow<List<HealingEvent>> = _healingHistory.asStateFlow()

    private val _recentCrashCount = MutableStateFlow(0)
    val recentCrashCount: StateFlow<Int> = _recentCrashCount.asStateFlow()

    fun isNetworkOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Guards against repeating side-effecting actions (duplicate messages, payments, calls, deletions).
     */
    fun isNonIdempotentAction(actionType: String): Boolean {
        val lower = actionType.lowercase()
        return lower.contains("send_message") ||
            lower.contains("whatsapp_send") ||
            lower.contains("payment") ||
            lower.contains("pay_") ||
            lower.contains("transfer") ||
            lower.contains("delete_") ||
            lower.contains("place_call") ||
            lower.contains("publish_")
    }

    fun classifyThrowable(t: Throwable): FailureCategory {
        val msg = (t.message ?: t.javaClass.simpleName).lowercase()
        return when {
            msg.contains("timeout") -> FailureCategory.API_TIMEOUT
            msg.contains("unknownhost") || msg.contains("network") || msg.contains("connect") -> FailureCategory.NETWORK_FAILURE
            msg.contains("accessibility") -> FailureCategory.ACCESSIBILITY_INTERRUPTED
            msg.contains("permission") || msg.contains("security") -> FailureCategory.MISSING_PERMISSION
            msg.contains("outofmemory") || msg.contains("space") || msg.contains("storage") -> FailureCategory.LOW_STORAGE_OR_MEMORY
            msg.contains("http 4") || msg.contains("http 5") || msg.contains("model") -> FailureCategory.MODEL_ERROR
            else -> FailureCategory.CRASH
        }
    }

    suspend fun <T> executeWithRecovery(
        actionName: String,
        maxRetries: Int = 2,
        onFallback: (suspend () -> T?)? = null,
        block: suspend (attempt: Int) -> T
    ): Result<T> {
        if (isNonIdempotentAction(actionName)) {
            return try {
                Result.success(block(0))
            } catch (e: Exception) {
                val cat = classifyThrowable(e)
                recordEvent(cat, actionName, "Skipped retry (non-idempotent action guard)", false)
                Result.failure(e)
            }
        }

        var lastError: Throwable? = null
        for (attempt in 0..maxRetries) {
            try {
                val res = block(attempt)
                if (attempt > 0) {
                    recordEvent(FailureCategory.API_TIMEOUT, actionName, "Recovered on retry #$attempt", true)
                }
                return Result.success(res)
            } catch (e: Exception) {
                lastError = e
                val cat = classifyThrowable(e)
                if (cat == FailureCategory.MISSING_PERMISSION) {
                    recordEvent(cat, actionName, "Aborted retry — permission must be granted by user", false)
                    break
                }
                if (attempt < maxRetries) {
                    delay(450L * (attempt + 1))
                }
            }
        }

        if (onFallback != null) {
            val fb = runCatching { onFallback() }.getOrNull()
            if (fb != null) {
                val cat = lastError?.let { classifyThrowable(it) } ?: FailureCategory.MODEL_ERROR
                recordEvent(cat, actionName, "Recovered via alternative provider/offline fallback", true)
                return Result.success(fb)
            }
        }

        val finalCat = lastError?.let { classifyThrowable(it) } ?: FailureCategory.CRASH
        if (finalCat == FailureCategory.CRASH) _recentCrashCount.value += 1
        recordEvent(finalCat, actionName, "All safe retries exhausted: ${lastError?.localizedMessage}", false)
        return Result.failure(lastError ?: IllegalStateException("Action failed"))
    }

    fun pruneCachesOnLowMemory(): String {
        val cacheDir = context.cacheDir
        var freedBytes = 0L
        cacheDir?.listFiles()?.forEach { file ->
            if (file.isFile && System.currentTimeMillis() - file.lastModified() > 3_600_000L) {
                val size = file.length()
                if (file.delete()) freedBytes += size
            }
        }
        val freedKb = freedBytes / 1024L
        recordEvent(FailureCategory.LOW_STORAGE_OR_MEMORY, "memory_prune", "Freed ${freedKb}KB temp cache", true)
        return "Pruned ${freedKb}KB temporary cache safely."
    }

    fun recordEvent(category: FailureCategory, action: String, strategy: String, succeeded: Boolean) {
        _healingHistory.value = (listOf(HealingEvent(category, action, strategy, succeeded)) + _healingHistory.value).take(60)
    }
}
