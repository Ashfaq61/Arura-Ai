package com.example.aura.analytics

import com.example.aura.agents.SpecialistAgent
import com.example.aura.automation.DeviceHealthSnapshot
import com.example.aura.evolution.HealingEvent
import com.example.aura.memory.ProviderUsageEntity
import com.example.aura.memory.TaskRecordEntity

data class AgentPerformanceMetric(
    val agentName: String,
    val tasksHandled: Int,
    val successRatePercent: Int,
    val avgDurationMs: Long
)

data class OptimizationRecommendation(
    val id: String,
    val title: String,
    val rationale: String,
    val actionCode: String // "ENABLE_FREE_ONLY", "PRUNE_CACHE", "ENABLE_WAKE_SAVER", "SWITCH_HYBRID"
)

data class PerformanceIntelligenceReport(
    val totalApiCalls: Int,
    val apiSuccessRatePercent: Int,
    val avgApiLatencyMs: Long,
    val totalEstimatedTokens: Int,
    val batteryImpactLabel: String,
    val topErrorPattern: String,
    val agentMetrics: List<AgentPerformanceMetric>,
    val recommendations: List<OptimizationRecommendation>
)

/**
 * Analyzes agent execution history, API latency/failures, token usage, healing events,
 * and battery health to produce actionable optimization intelligence.
 */
class AgentPerformanceEngine {

    fun generateReport(
        agents: List<SpecialistAgent>,
        tasks: List<TaskRecordEntity>,
        usage: List<ProviderUsageEntity>,
        healingEvents: List<HealingEvent>,
        health: DeviceHealthSnapshot
    ): PerformanceIntelligenceReport {
        val totalCalls = usage.size
        val apiOk = usage.count { it.success }
        val apiSuccessPct = if (totalCalls == 0) 100 else (apiOk * 100) / totalCalls
        val avgLatency = if (totalCalls == 0) 0L else usage.map { it.latencyMs }.average().toLong()
        val totalTokens = usage.sumOf { it.estimatedTokens }

        val topError = usage.filter { !it.success && it.errorMessage.isNotBlank() }
            .groupingBy { it.errorMessage.take(60) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "No recent API errors"

        val perAgent = agents.map { ag ->
            val shortName = ag.name.substringBefore(" Agent")
            val matching = tasks.filter { it.assignedAgents.contains(shortName, ignoreCase = true) }
            val done = matching.size + ag.completedTasks
            val ok = matching.count { it.status.equals("COMPLETED", true) || it.status.equals("SUCCESS", true) } + ag.completedTasks
            val rate = if (done == 0) 100 else (ok * 100) / done
            val avgDur = if (matching.isEmpty()) 0L else matching.map { it.durationMs }.average().toLong()
            AgentPerformanceMetric(ag.name, done, rate.coerceIn(0, 100), avgDur)
        }.sortedByDescending { it.tasksHandled }.take(10)

        val batteryImpact = when {
            health.batteryPercent <= 20 && !health.isCharging -> "HIGH (Low battery — prefer Hybrid/Local)"
            avgLatency > 3500L || totalCalls > 40 -> "MODERATE"
            else -> "LOW (Optimal)"
        }

        val recs = mutableListOf<OptimizationRecommendation>()
        if (avgLatency > 2500L || apiSuccessPct < 85) {
            recs.add(
                OptimizationRecommendation(
                    id = "rec_hybrid",
                    title = "Prioritize Hybrid Local Routing",
                    rationale = "Average API latency is ${avgLatency}ms (success ${apiSuccessPct}%). Hybrid routing resolves device commands locally in <20ms.",
                    actionCode = "SWITCH_HYBRID"
                )
            )
        }
        if (totalTokens > 80_000) {
            recs.add(
                OptimizationRecommendation(
                    id = "rec_free_only",
                    title = "Enforce Strict Free-Only Model Guard",
                    rationale = "Recent session used $totalTokens estimated tokens. Lock routing to zero-cost Flash models.",
                    actionCode = "ENABLE_FREE_ONLY"
                )
            )
        }
        if (health.batteryPercent <= 25) {
            recs.add(
                OptimizationRecommendation(
                    id = "rec_wake_saver",
                    title = "Activate Wake Word Battery Saver",
                    rationale = "Device battery is at ${health.batteryPercent}%. Automatically pause background voice spotting under low power.",
                    actionCode = "ENABLE_WAKE_SAVER"
                )
            )
        }
        if (healingEvents.size >= 3 || health.availableRamMb < 900) {
            recs.add(
                OptimizationRecommendation(
                    id = "rec_prune_cache",
                    title = "Prune TTS & Temporary Caches",
                    rationale = "${healingEvents.size} recovery events or low free RAM (${health.availableRamMb}MB). Clearing stale temp files frees space cleanly.",
                    actionCode = "PRUNE_CACHE"
                )
            )
        }
        if (recs.isEmpty()) {
            recs.add(
                OptimizationRecommendation(
                    id = "rec_nominal",
                    title = "System Performing Optimally",
                    rationale = "API reliability ${apiSuccessPct}%, avg latency ${avgLatency}ms, battery ${health.batteryPercent}%.",
                    actionCode = "PRUNE_CACHE"
                )
            )
        }

        return PerformanceIntelligenceReport(
            totalApiCalls = totalCalls,
            apiSuccessRatePercent = apiSuccessPct,
            avgApiLatencyMs = avgLatency,
            totalEstimatedTokens = totalTokens,
            batteryImpactLabel = batteryImpact,
            topErrorPattern = topError,
            agentMetrics = perAgent,
            recommendations = recs
        )
    }
}
