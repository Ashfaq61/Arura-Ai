package com.example.aura.analytics

import com.example.aura.agents.McpRuntimeEngine
import com.example.aura.agents.McpToolDescriptor
import com.example.aura.memory.AuditLogEntity
import com.example.aura.memory.MarketplaceToolEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class McpToolHealthStatus(val label: String) {
    HEALTHY("Healthy"),
    DISABLED("Disabled"),
    FAULTY("Faulty")
}

data class McpHourlyHealthBucket(
    val hourIndex: Int,
    val hourStartMs: Long,
    val hourLabel: String,
    val totalCalls: Int,
    val successCalls: Int,
    val failedCalls: Int,
    val successRatePercent: Float,
    val avgLatencyMs: Float,
    val maxLatencyMs: Long
)

data class McpToolHealthSummary(
    val toolSlug: String,
    val displayName: String,
    val category: String,
    val healthStatus: McpToolHealthStatus,
    val statusLabel: String,
    val enabled: Boolean,
    val safetyLevel: Int,
    val calls24h: Int,
    val successCalls24h: Int,
    val failedCalls24h: Int,
    val successRatePercent: Float?,
    val avgLatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val lastRunTimestamp: Long?,
    val lastOutcome: String,
    val lastOperation: String,
    val marketplaceToolId: String?,
    val canRunLiveProbe: Boolean
)

data class McpToolHealthDashboardState(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val totalRegisteredTools: Int,
    val healthyCount: Int,
    val disabledCount: Int,
    val faultyCount: Int,
    val totalCalls24h: Int,
    val successfulCalls24h: Int,
    val failedCalls24h: Int,
    val overallSuccessRatePercent: Float?,
    val avgLatencyMs24h: Long,
    val p95LatencyMs24h: Long,
    val hourlyBuckets: List<McpHourlyHealthBucket>,
    val toolSummaries: List<McpToolHealthSummary>
)

private data class ParsedMcpTelemetryEvent(
    val slug: String,
    val succeeded: Boolean,
    val latencyMs: Long,
    val operation: String,
    val timestamp: Long
)

object McpToolHealthAggregator {

    const val WINDOW_24H_MS = 24L * 60L * 60L * 1000L
    private const val HOUR_MS = 60L * 60L * 1000L
    private val LATENCY_REGEX = Regex("""\((\d+)ms\)""")

    fun aggregate24h(
        descriptors: List<McpToolDescriptor>,
        marketplaceTools: List<MarketplaceToolEntity>,
        auditLogs: List<AuditLogEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): McpToolHealthDashboardState {
        val windowStart = nowMs - WINDOW_24H_MS
        val currentHourFloor = (nowMs / HOUR_MS) * HOUR_MS
        val firstBucketStart = currentHourFloor - (23L * HOUR_MS)

        val events = auditLogs.mapNotNull { log ->
            if (log.timestamp < windowStart || log.timestamp > nowMs + 60_000L) return@mapNotNull null
            val slug = extractSlugFromAction(log.action) ?: return@mapNotNull null
            val succeeded = log.outcome.equals("ALLOWED", ignoreCase = true)
            val latencyMs = parseLatencyMs(log.details)
            val operation = parseOperation(log.details)
            ParsedMcpTelemetryEvent(
                slug = slug,
                succeeded = succeeded,
                latencyMs = latencyMs,
                operation = operation,
                timestamp = log.timestamp
            )
        }

        val eventsBySlug = events.groupBy { it.slug.lowercase(Locale.US) }

        val hourFormat = SimpleDateFormat("HH:00", Locale.US)
        val hourlyBuckets = (0 until 24).map { idx ->
            val bucketStart = firstBucketStart + idx * HOUR_MS
            val bucketEnd = bucketStart + HOUR_MS
            val bucketEvents = events.filter { it.timestamp in bucketStart until bucketEnd }
            val total = bucketEvents.size
            val ok = bucketEvents.count { it.succeeded }
            val fail = total - ok
            val rate = if (total == 0) 0f else (ok.toFloat() * 100f) / total.toFloat()
            val latencies = bucketEvents.map { it.latencyMs }.filter { it > 0L }
            val avgLat = if (latencies.isEmpty()) 0f else latencies.average().toFloat()
            val maxLat = latencies.maxOrNull() ?: 0L
            McpHourlyHealthBucket(
                hourIndex = idx,
                hourStartMs = bucketStart,
                hourLabel = hourFormat.format(Date(bucketStart)),
                totalCalls = total,
                successCalls = ok,
                failedCalls = fail,
                successRatePercent = rate,
                avgLatencyMs = avgLat,
                maxLatencyMs = maxLat
            )
        }

        val marketplaceBySlug = marketplaceTools.associateBy {
            McpRuntimeEngine.extractMcpSlug(it).lowercase(Locale.US)
        }
        val descriptorBySlug = descriptors.associateBy { it.name.lowercase(Locale.US) }
        val orderedSlugs = linkedSetOf<String>().apply {
            descriptors.forEach { add(it.name.lowercase(Locale.US)) }
            marketplaceTools.forEach { add(McpRuntimeEngine.extractMcpSlug(it).lowercase(Locale.US)) }
            events.forEach { add(it.slug.lowercase(Locale.US)) }
        }

        val summaries = orderedSlugs.map { slugKey ->
            val desc = descriptorBySlug[slugKey]
            val mkt = marketplaceBySlug[slugKey]
            val toolEvents = eventsBySlug[slugKey].orEmpty().sortedBy { it.timestamp }
            val totalCalls = toolEvents.size
            val okCalls = toolEvents.count { it.succeeded }
            val failedCalls = totalCalls - okCalls
            val rate = if (totalCalls == 0) null else (okCalls.toFloat() * 100f) / totalCalls.toFloat()
            val latencies = toolEvents.map { it.latencyMs }.filter { it > 0L }
            val avgLat = if (latencies.isEmpty()) 0L else latencies.average().roundToLongCompat()
            val minLat = latencies.minOrNull() ?: 0L
            val maxLat = latencies.maxOrNull() ?: 0L
            val latest = toolEvents.lastOrNull()

            val isExplicitFaulty = mkt != null && (
                mkt.status.contains("FAULTY", ignoreCase = true) ||
                    mkt.status.contains("ERROR", ignoreCase = true) ||
                    mkt.securityScan.equals("FAILED", ignoreCase = true)
                )
            val isEnabled = desc?.enabled ?: (mkt?.status == "INSTALLED_ENABLED")
            val healthStatus = when {
                isExplicitFaulty -> McpToolHealthStatus.FAULTY
                !isEnabled -> McpToolHealthStatus.DISABLED
                totalCalls > 0 && okCalls == 0 && failedCalls > 0 -> McpToolHealthStatus.FAULTY
                latest != null && !latest.succeeded && failedCalls > okCalls -> McpToolHealthStatus.FAULTY
                else -> McpToolHealthStatus.HEALTHY
            }

            val displayName = mkt?.name ?: desc?.name ?: slugKey
            val actualSlug = desc?.name ?: mkt?.let { McpRuntimeEngine.extractMcpSlug(it) } ?: slugKey
            val category = mkt?.category ?: desc?.category ?: "MCP Runtime"
            val safetyLevel = desc?.safetyLevel
                ?: mkt?.let { McpRuntimeEngine.inferSafetyLevel(it.requiredPermissions) }
                ?: 1

            McpToolHealthSummary(
                toolSlug = actualSlug,
                displayName = displayName,
                category = category,
                healthStatus = healthStatus,
                statusLabel = healthStatus.label,
                enabled = isEnabled,
                safetyLevel = safetyLevel,
                calls24h = totalCalls,
                successCalls24h = okCalls,
                failedCalls24h = failedCalls,
                successRatePercent = rate,
                avgLatencyMs = avgLat,
                minLatencyMs = minLat,
                maxLatencyMs = maxLat,
                lastRunTimestamp = latest?.timestamp,
                lastOutcome = when {
                    latest == null -> "No runs in 24h"
                    latest.succeeded -> "PASSED"
                    else -> "FAILED"
                },
                lastOperation = latest?.operation ?: "—",
                marketplaceToolId = mkt?.id,
                canRunLiveProbe = (desc != null || mkt != null) && safetyLevel < 4
            )
        }.sortedWith(
            compareBy<McpToolHealthSummary>(
                { it.healthStatus == McpToolHealthStatus.HEALTHY },
                { -it.calls24h },
                { it.displayName.lowercase(Locale.US) }
            )
        )

        val totalCalls24h = events.size
        val ok24h = events.count { it.succeeded }
        val fail24h = totalCalls24h - ok24h
        val overallRate = if (totalCalls24h == 0) null else (ok24h.toFloat() * 100f) / totalCalls24h.toFloat()
        val allLatencies = events.map { it.latencyMs }.filter { it > 0L }.sorted()
        val avg24h = if (allLatencies.isEmpty()) 0L else allLatencies.average().roundToLongCompat()
        val p95 = if (allLatencies.isEmpty()) {
            0L
        } else {
            val idx = (ceil(allLatencies.size * 0.95).toInt() - 1).coerceIn(0, allLatencies.lastIndex)
            allLatencies[idx]
        }

        return McpToolHealthDashboardState(
            windowStartMs = windowStart,
            windowEndMs = nowMs,
            totalRegisteredTools = summaries.size,
            healthyCount = summaries.count { it.healthStatus == McpToolHealthStatus.HEALTHY },
            disabledCount = summaries.count { it.healthStatus == McpToolHealthStatus.DISABLED },
            faultyCount = summaries.count { it.healthStatus == McpToolHealthStatus.FAULTY },
            totalCalls24h = totalCalls24h,
            successfulCalls24h = ok24h,
            failedCalls24h = fail24h,
            overallSuccessRatePercent = overallRate,
            avgLatencyMs24h = avg24h,
            p95LatencyMs24h = p95,
            hourlyBuckets = hourlyBuckets,
            toolSummaries = summaries
        )
    }

    private fun extractSlugFromAction(action: String): String? {
        return when {
            action.startsWith("EXECUTE:", ignoreCase = true) -> action.substringAfter(":").trim().takeIf { it.isNotEmpty() }
            action.startsWith("PROBE:", ignoreCase = true) -> action.substringAfter(":").trim().takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun parseLatencyMs(details: String): Long {
        val match = LATENCY_REGEX.find(details) ?: return 0L
        return match.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun parseOperation(details: String): String {
        val raw = details.substringBefore(" (").substringBefore(":").trim()
        return raw.ifBlank { "EXECUTE" }.take(32)
    }

    private fun Double.roundToLongCompat(): Long =
        if (isNaN()) 0L else roundToInt().toLong()
}
