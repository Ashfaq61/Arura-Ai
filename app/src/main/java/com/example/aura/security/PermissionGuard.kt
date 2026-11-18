package com.example.aura.security

import com.example.aura.automation.AccessibilityBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ActionSafetyLevel(val level: Int, val label: String, val description: String) {
    LEVEL_1_SAFE_AUTO(
        1,
        "Level 1 — Safe Automatic",
        "Reversible local actions within granted permissions (e.g., reading memory, system telemetry)."
    ),
    LEVEL_2_USER_AUTHORIZED(
        2,
        "Level 2 — User Authorized",
        "Actions using previously approved apps, accessibility gestures, or system intents."
    ),
    LEVEL_3_CONFIRMATION_REQUIRED(
        3,
        "Level 3 — Confirmation Required",
        "High-impact actions: sending messages, initiating calls, deleting data, publishing content, or installing tools."
    ),
    LEVEL_4_RESTRICTED(
        4,
        "Level 4 — Restricted / Prohibited",
        "Violates Android security, privacy, or Play policy (e.g., hidden recording, dynamic DEX loading, bypassing protected screens)."
    )
}

sealed interface GuardDecision {
    data class Allowed(val level: ActionSafetyLevel, val reason: String) : GuardDecision
    data class RequiresApproval(val level: ActionSafetyLevel, val title: String, val detail: String) : GuardDecision
    data class Blocked(val level: ActionSafetyLevel, val reason: String) : GuardDecision
}

/**
 * Central Security & Permission Guardrail engine enforcing 4-level safety policies,
 * emergency stop kill-switch, app allowlist/blocklist, and static code safety checks.
 */
class PermissionGuard {

    private val _emergencyStopActive = MutableStateFlow(false)
    val emergencyStopActive: StateFlow<Boolean> = _emergencyStopActive.asStateFlow()

    private val _allowlistedPackages = MutableStateFlow(
        setOf(
            "com.google.android.youtube",
            "com.whatsapp",
            "com.google.android.apps.maps",
            "com.android.chrome",
            "com.facebook.katana",
            "com.zhiliaoapp.musically"
        )
    )
    val allowlistedPackages: StateFlow<Set<String>> = _allowlistedPackages.asStateFlow()

    private val _blocklistedPackages = MutableStateFlow(
        setOf(
            "com.android.settings.password",
            "com.google.android.gms.auth"
        )
    )
    val blocklistedPackages: StateFlow<Set<String>> = _blocklistedPackages.asStateFlow()

    fun triggerEmergencyStop() {
        _emergencyStopActive.value = true
        AccessibilityBridge.setEmergencyPaused(true)
    }

    fun clearEmergencyStop() {
        _emergencyStopActive.value = false
        AccessibilityBridge.setEmergencyPaused(false)
    }

    fun setPackagePolicy(packageName: String, allow: Boolean) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return
        if (allow) {
            _blocklistedPackages.value = _blocklistedPackages.value - pkg
            _allowlistedPackages.value = _allowlistedPackages.value + pkg
        } else {
            _allowlistedPackages.value = _allowlistedPackages.value - pkg
            _blocklistedPackages.value = _blocklistedPackages.value + pkg
        }
    }

    fun removePackagePolicy(packageName: String) {
        _allowlistedPackages.value = _allowlistedPackages.value - packageName
        _blocklistedPackages.value = _blocklistedPackages.value - packageName
    }

    fun evaluateAction(
        actionType: String,
        targetPackage: String? = null,
        summary: String = ""
    ): GuardDecision {
        if (_emergencyStopActive.value) {
            return GuardDecision.Blocked(
                ActionSafetyLevel.LEVEL_4_RESTRICTED,
                "Emergency STOP is active. Resume system in Security Center before executing actions."
            )
        }

        val normalized = actionType.lowercase()

        // Level 4: Prohibited / Policy Violations
        if (normalized.contains("dex_load") ||
            normalized.contains("bypass_lock") ||
            normalized.contains("silent_record") ||
            normalized.contains("hidden_capture") ||
            normalized.contains("silent_apk_install") ||
            normalized.contains("protected_screen_bypass")
        ) {
            return GuardDecision.Blocked(
                ActionSafetyLevel.LEVEL_4_RESTRICTED,
                "Blocked by Level 4 security policy: $actionType is strictly prohibited."
            )
        }

        if (!targetPackage.isNullOrBlank() && _blocklistedPackages.value.contains(targetPackage)) {
            return GuardDecision.Blocked(
                ActionSafetyLevel.LEVEL_4_RESTRICTED,
                "Target package $targetPackage is on your Security Blocklist."
            )
        }

        // Level 3: Requires explicit user approval
        if (normalized.contains("send_message") ||
            normalized.contains("whatsapp_send") ||
            normalized.contains("place_call") ||
            normalized.contains("delete_") ||
            normalized.contains("publish_") ||
            normalized.contains("install_tool") ||
            normalized.contains("apply_upgrade") ||
            normalized.contains("record_audio_call")
        ) {
            return GuardDecision.RequiresApproval(
                level = ActionSafetyLevel.LEVEL_3_CONFIRMATION_REQUIRED,
                title = "Approval required: $actionType",
                detail = summary.ifBlank { "AURA requests permission to execute $actionType." }
            )
        }

        // Level 2: User-authorized app/device actions
        if (normalized.contains("open_app") ||
            normalized.contains("accessibility_") ||
            normalized.contains("camera_") ||
            normalized.contains("volume_") ||
            normalized.contains("search_")
        ) {
            if (!targetPackage.isNullOrBlank() && !_allowlistedPackages.value.contains(targetPackage)) {
                return GuardDecision.RequiresApproval(
                    level = ActionSafetyLevel.LEVEL_3_CONFIRMATION_REQUIRED,
                    title = "Authorize app access: $targetPackage",
                    detail = "App $targetPackage is not yet in your Allowlist. Approve to proceed."
                )
            }
            return GuardDecision.Allowed(
                ActionSafetyLevel.LEVEL_2_USER_AUTHORIZED,
                "Authorized under Level 2 policy."
            )
        }

        // Level 1: Safe local/query operations
        return GuardDecision.Allowed(
            ActionSafetyLevel.LEVEL_1_SAFE_AUTO,
            "Safe reversible action under Level 1 policy."
        )
    }

    /**
     * Static safety scanner for autonomous tools / generated specs before registration.
     */
    fun scanToolSpecForSafety(name: String, codeOrSpec: String, permissions: List<String>): Pair<Boolean, String> {
        val lower = codeOrSpec.lowercase()
        val dangerousPatterns = listOf(
            "dexclassloader",
            "pathclassloader",
            "runtime.getruntime().exec",
            "su ",
            "system.load(",
            "read_external_storage",
            "manage_external_storage",
            "silent_install",
            "accessibility_bypass"
        )
        val matched = dangerousPatterns.firstOrNull { lower.contains(it) }
        if (matched != null) {
            return false to "Security Scan FAILED: Prohibited pattern '$matched' detected in '$name'."
        }
        val restrictedPerms = permissions.filter {
            it.contains("MANAGE_EXTERNAL_STORAGE", ignoreCase = true) ||
                it.contains("REQUEST_INSTALL_PACKAGES", ignoreCase = true) ||
                it.contains("BIND_DEVICE_ADMIN", ignoreCase = true)
        }
        if (restrictedPerms.isNotEmpty()) {
            return false to "Security Scan FAILED: Restricted permissions ${restrictedPerms.joinToString()} require manual review."
        }
        return true to "Security Scan PASSED: Sandboxed declarative tool verified (0 critical findings)."
    }
}
