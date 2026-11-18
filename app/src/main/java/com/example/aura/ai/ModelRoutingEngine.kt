package com.example.aura.ai

import com.example.aura.automation.DeviceHealthSnapshot

data class ModelCapabilityProfile(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val isPaidTier: Boolean,
    val estimatedCostPer1MTokensUsd: Double,
    val contextTokens: Int,
    val avgLatencyMs: Int,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val supportsLiveAudio: Boolean,
    val supportsOffline: Boolean,
    val taskStrength: String
)

data class LocalModelCandidate(
    val id: String,
    val name: String,
    val runtime: String,
    val minRamMb: Long,
    val diskSizeGb: Double,
    val license: String,
    val compatibleWithDevice: Boolean,
    val evaluationNote: String
)

data class BudgetPolicy(
    val intelligenceMode: String = "HYBRID", // "LOCAL_ONLY", "CLOUD_ONLY", "HYBRID"
    val freeOnlyMode: Boolean = true,
    val dailyBudgetUsd: Double = 1.0,
    val dailyTokenLimit: Int = 150_000
)

data class RoutingDecision(
    val providerId: String,
    val modelId: String,
    val reason: String,
    val estimatedCostUsd: Double,
    val allowedByBudget: Boolean
)

/**
 * Intelligent Model Router, Cost Manager, and On-Device Hybrid AI Evaluator.
 * Never silently switches to a paid provider when freeOnlyMode = true.
 */
class ModelRoutingEngine {

    val profiles: List<ModelCapabilityProfile> = listOf(
        ModelCapabilityProfile(
            providerId = AiProviderType.LOCAL_OFFLINE.id,
            modelId = "aura-offline-rules-v1",
            displayName = "On-Device Deterministic Engine",
            isPaidTier = false,
            estimatedCostPer1MTokensUsd = 0.0,
            contextTokens = 8_192,
            avgLatencyMs = 15,
            supportsTools = true,
            supportsVision = false,
            supportsLiveAudio = false,
            supportsOffline = true,
            taskStrength = "Private device control, memory search, offline utilities"
        ),
        ModelCapabilityProfile(
            providerId = AiProviderType.FIREBASE_GEMINI.id,
            modelId = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            isPaidTier = false,
            estimatedCostPer1MTokensUsd = 0.0,
            contextTokens = 1_000_000,
            avgLatencyMs = 650,
            supportsTools = true,
            supportsVision = true,
            supportsLiveAudio = false,
            supportsOffline = false,
            taskStrength = "Ultra-fast classification, translation, summarization"
        ),
        ModelCapabilityProfile(
            providerId = AiProviderType.FIREBASE_GEMINI.id,
            modelId = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            isPaidTier = false,
            estimatedCostPer1MTokensUsd = 0.0,
            contextTokens = 1_000_000,
            avgLatencyMs = 1100,
            supportsTools = true,
            supportsVision = true,
            supportsLiveAudio = true,
            supportsOffline = false,
            taskStrength = "Default multimodal orchestration, GUI vision, planning"
        ),
        ModelCapabilityProfile(
            providerId = AiProviderType.FIREBASE_GEMINI.id,
            modelId = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            isPaidTier = true,
            estimatedCostPer1MTokensUsd = 3.50,
            contextTokens = 2_000_000,
            avgLatencyMs = 2400,
            supportsTools = true,
            supportsVision = true,
            supportsLiveAudio = false,
            supportsOffline = false,
            taskStrength = "Complex coding, deep STEM & long-horizon architecture"
        ),
        ModelCapabilityProfile(
            providerId = AiProviderType.GROQ.id,
            modelId = "llama-3.3-70b-versatile",
            displayName = "Groq Llama 3.3 70B",
            isPaidTier = false,
            estimatedCostPer1MTokensUsd = 0.0,
            contextTokens = 128_000,
            avgLatencyMs = 450,
            supportsTools = true,
            supportsVision = false,
            supportsLiveAudio = false,
            supportsOffline = false,
            taskStrength = "Fast low-latency reasoning (requires user Groq key)"
        ),
        ModelCapabilityProfile(
            providerId = AiProviderType.OPENROUTER.id,
            modelId = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "OpenRouter Free Router",
            isPaidTier = false,
            estimatedCostPer1MTokensUsd = 0.0,
            contextTokens = 128_000,
            avgLatencyMs = 1300,
            supportsTools = true,
            supportsVision = false,
            supportsLiveAudio = false,
            supportsOffline = false,
            taskStrength = "Multi-provider fallback (free community quota)"
        )
    )

    fun evaluateLocalModels(health: DeviceHealthSnapshot): List<LocalModelCandidate> {
        return listOf(
            LocalModelCandidate(
                id = "gemma4-e2b-int4",
                name = "Gemma 4 E2B (INT4 Quantized)",
                runtime = "ML Kit GenAI / LiteRT (AICore)",
                minRamMb = 4096,
                diskSizeGb = 1.9,
                license = "Gemma Terms of Use",
                compatibleWithDevice = health.totalRamMb >= 4096 && health.freeStorageGb >= 2.2,
                evaluationNote = if (health.totalRamMb >= 4096) {
                    "Device RAM (${health.totalRamMb}MB) & storage (${"%.1f".format(health.freeStorageGb)}GB) meet baseline for quantized edge inference."
                } else {
                    "Insufficient RAM (${health.totalRamMb}MB < 4096MB); cloud Gemini recommended."
                }
            ),
            LocalModelCandidate(
                id = "gemma4-e4b-multimodal",
                name = "Gemma 4 E4B Multimodal",
                runtime = "LiteRT / MediaPipe GenAI",
                minRamMb = 6144,
                diskSizeGb = 3.4,
                license = "Gemma Terms of Use",
                compatibleWithDevice = health.totalRamMb >= 6144 && health.freeStorageGb >= 3.8,
                evaluationNote = if (health.totalRamMb >= 6144) {
                    "Eligible for high-RAM edge vision & text tasks (monitor battery thermal load)."
                } else {
                    "Requires >= 6GB RAM & 3.8GB free storage; blocked from auto-download to prevent OOM."
                }
            ),
            LocalModelCandidate(
                id = "aura-offline-rules-v1",
                name = "AURA Deterministic Local Engine",
                runtime = "Built-in Kotlin Engine",
                minRamMb = 512,
                diskSizeGb = 0.01,
                license = "Apache-2.0",
                compatibleWithDevice = true,
                evaluationNote = "Always available offline with zero download or battery penalty."
            )
        )
    }

    fun selectOptimalRoute(
        preferredProviderId: String,
        preferredModelId: String,
        requiresVision: Boolean,
        isComplexReasoning: Boolean,
        isSimpleLocalTask: Boolean,
        isNetworkAvailable: Boolean,
        todayTokensUsed: Int,
        todaySpendUsd: Double,
        policy: BudgetPolicy
    ): RoutingDecision {
        // 1. Strict Local-Only Mode or Offline network
        if (policy.intelligenceMode == "LOCAL_ONLY" || (!isNetworkAvailable && policy.intelligenceMode != "CLOUD_ONLY")) {
            return RoutingDecision(
                providerId = AiProviderType.LOCAL_OFFLINE.id,
                modelId = "aura-offline-rules-v1",
                reason = if (policy.intelligenceMode == "LOCAL_ONLY") "Local-Only privacy mode active." else "No network detected; routed locally.",
                estimatedCostUsd = 0.0,
                allowedByBudget = true
            )
        }

        // 2. Hybrid optimization for trivial/private tasks
        if (policy.intelligenceMode == "HYBRID" && isSimpleLocalTask && !requiresVision) {
            return RoutingDecision(
                providerId = AiProviderType.LOCAL_OFFLINE.id,
                modelId = "aura-offline-rules-v1",
                reason = "Hybrid router handled simple device/memory action locally (0 tokens, 0ms network).",
                estimatedCostUsd = 0.0,
                allowedByBudget = true
            )
        }

        // 3. Budget & Free-Only Enforcement
        if (todayTokensUsed >= policy.dailyTokenLimit) {
            return RoutingDecision(
                providerId = AiProviderType.LOCAL_OFFLINE.id,
                modelId = "aura-offline-rules-v1",
                reason = "Daily token limit (${policy.dailyTokenLimit}) reached. Routed locally to stay within budget.",
                estimatedCostUsd = 0.0,
                allowedByBudget = false
            )
        }

        val chosenProfile = profiles.firstOrNull {
            it.providerId == preferredProviderId && it.modelId == preferredModelId
        }

        // Never silently use a paid tier when freeOnlyMode is enabled
        if (policy.freeOnlyMode && (chosenProfile?.isPaidTier == true || preferredModelId == "gemini-4-argon")) {
            val safeModel = if (requiresVision || isComplexReasoning) "gemini-3.8-flash" else "gemini-3.5-flash-lite"
            return RoutingDecision(
                providerId = AiProviderType.FIREBASE_GEMINI.id,
                modelId = safeModel,
                reason = "Free-Only mode prevented paid model '$preferredModelId'; routed to free-tier $safeModel.",
                estimatedCostUsd = 0.0,
                allowedByBudget = true
            )
        }

        if (todaySpendUsd >= policy.dailyBudgetUsd && chosenProfile?.isPaidTier == true) {
            return RoutingDecision(
                providerId = AiProviderType.FIREBASE_GEMINI.id,
                modelId = "gemini-3.8-flash",
                reason = "Daily spend cap ($${"%.2f".format(policy.dailyBudgetUsd)}) reached; downgraded to free-tier Gemini 3.8 Flash.",
                estimatedCostUsd = 0.0,
                allowedByBudget = false
            )
        }

        if (requiresVision && chosenProfile?.supportsVision == false) {
            return RoutingDecision(
                providerId = AiProviderType.FIREBASE_GEMINI.id,
                modelId = "gemini-3.8-flash",
                reason = "Task requires multimodal Vision; routed to Gemini 3.8 Flash.",
                estimatedCostUsd = 0.0,
                allowedByBudget = true
            )
        }

        val resolvedModel = when {
            preferredProviderId == AiProviderType.FIREBASE_GEMINI.id && isComplexReasoning && !policy.freeOnlyMode -> "gemini-4-argon"
            else -> preferredModelId
        }
        return RoutingDecision(
            providerId = preferredProviderId,
            modelId = resolvedModel,
            reason = "Routed via ${policy.intelligenceMode} policy to $resolvedModel.",
            estimatedCostUsd = chosenProfile?.estimatedCostPer1MTokensUsd?.div(1000.0) ?: 0.0,
            allowedByBudget = true
        )
    }
}
