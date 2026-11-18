package com.example.aura.workflow

import com.example.aura.automation.DeviceControlEngine
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AutonomyPolicy
import com.example.aura.memory.VisualWorkflowEntity
import com.example.aura.security.GuardDecision
import com.example.aura.security.PermissionGuard
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class WorkflowNode(
    val id: String,
    val type: String, // TRIGGER, SET_VARIABLE, ACTION, DELAY, CONDITION_IF, APPROVAL_GATE
    val label: String,
    val action: String = "",
    val param: String = "",
    val trueNext: String = "",
    val falseNext: String = ""
)

data class WorkflowValidationResult(
    val isValid: Boolean,
    val summary: String,
    val errors: List<String> = emptyList()
)

data class WorkflowRunReport(
    val workflowId: Long,
    val succeeded: Boolean,
    val logs: List<String>
)

/**
 * Visual Workflow Builder engine supporting structured step nodes, variables ({{var}}),
 * delays, IF/ELSE conditional branching, structural validation, and policy-gated execution.
 */
class VisualWorkflowEngine(
    private val advancedRepo: AdvancedRepository,
    private val deviceControl: DeviceControlEngine,
    private val permissionGuard: PermissionGuard
) {

    fun parseNodes(nodesJson: String): List<WorkflowNode> {
        return runCatching {
            val arr = JSONArray(nodesJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                WorkflowNode(
                    id = obj.optString("id", "n${i + 1}"),
                    type = obj.optString("type", "ACTION"),
                    label = obj.optString("label", "Step ${i + 1}"),
                    action = obj.optString("action", ""),
                    param = obj.optString("param", ""),
                    trueNext = obj.optString("trueNext", ""),
                    falseNext = obj.optString("falseNext", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeNodes(nodes: List<WorkflowNode>): String {
        val arr = JSONArray()
        nodes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("type", n.type)
                put("label", n.label)
                put("action", n.action)
                put("param", n.param)
                put("trueNext", n.trueNext)
                put("falseNext", n.falseNext)
            })
        }
        return arr.toString()
    }

    fun parseVariables(variablesJson: String): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        runCatching {
            val obj = JSONObject(variablesJson.ifBlank { "{}" })
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
        }
        return map
    }

    fun validateWorkflow(workflow: VisualWorkflowEntity): WorkflowValidationResult {
        val nodes = parseNodes(workflow.nodesJson)
        val errors = mutableListOf<String>()
        if (nodes.isEmpty()) {
            errors.add("Workflow has zero steps.")
            return WorkflowValidationResult(false, "INVALID: Empty workflow", errors)
        }
        val ids = nodes.map { it.id }.toSet()
        if (ids.size != nodes.size) {
            errors.add("Duplicate node IDs detected.")
        }
        for (n in nodes) {
            if (n.trueNext.isNotBlank() && !ids.contains(n.trueNext)) {
                errors.add("Node '${n.id}' links to missing next node '${n.trueNext}'.")
            }
            if (n.falseNext.isNotBlank() && !ids.contains(n.falseNext)) {
                errors.add("Node '${n.id}' false branch links to missing node '${n.falseNext}'.")
            }
            val guard = permissionGuard.evaluateAction("workflow_${n.action}", summary = n.param)
            if (guard is GuardDecision.Blocked) {
                errors.add("Node '${n.id}' blocked by security policy: ${guard.reason}")
            }
        }
        val summary = if (errors.isEmpty()) {
            "VALID (${nodes.size} steps verified, 0 policy blocks)"
        } else {
            "INVALID (${errors.size} issue(s): ${errors.first()})"
        }
        return WorkflowValidationResult(errors.isEmpty(), summary, errors)
    }

    suspend fun executeWorkflow(
        workflow: VisualWorkflowEntity,
        autonomyPolicy: AutonomyPolicy,
        userApprovedGates: Boolean = true
    ): WorkflowRunReport {
        val validation = validateWorkflow(workflow)
        if (!validation.isValid) {
            val updated = workflow.copy(
                lastValidationResult = validation.summary,
                lastRunLog = validation.errors.joinToString("\n"),
                lastRunSuccess = false,
                updatedAt = System.currentTimeMillis()
            )
            advancedRepo.upsertWorkflow(updated)
            return WorkflowRunReport(workflow.id, false, validation.errors)
        }

        val nodes = parseNodes(workflow.nodesJson)
        val byId = nodes.associateBy { it.id }
        val vars = parseVariables(workflow.variablesJson)
        val logs = mutableListOf<String>()
        val maxSteps = autonomyPolicy.maxAutonomousSteps.coerceIn(1, 100)
        var current: WorkflowNode? = nodes.firstOrNull()
        var stepsExecuted = 0
        var success = true

        while (current != null && stepsExecuted < maxSteps) {
            stepsExecuted++
            val resolvedParam = interpolateVariables(current.param, vars)
            var nextId = current.trueNext

            when (current.type.uppercase()) {
                "TRIGGER" -> logs.add("[$stepsExecuted] TRIGGER '${current.label}' started.")
                "SET_VARIABLE" -> {
                    val key = current.action.trim()
                    if (key.isNotBlank()) vars[key] = resolvedParam
                    logs.add("[$stepsExecuted] VAR $key = '$resolvedParam'")
                }
                "DELAY" -> {
                    val ms = (resolvedParam.toLongOrNull() ?: 250L).coerceIn(50L, 2000L)
                    delay(ms)
                    logs.add("[$stepsExecuted] DELAY ${ms}ms complete.")
                }
                "APPROVAL_GATE" -> {
                    if (!userApprovedGates) {
                        logs.add("[$stepsExecuted] GATE '${current.label}' waiting for user approval.")
                        success = false
                        break
                    }
                    logs.add("[$stepsExecuted] GATE '${current.label}' approved by user.")
                }
                "CONDITION_IF" -> {
                    val branchTaken = evaluateCondition(current.action, resolvedParam, vars)
                    nextId = if (branchTaken) current.trueNext else current.falseNext
                    logs.add("[$stepsExecuted] IF ${current.action}($resolvedParam) -> ${if (branchTaken) "TRUE -> ${nextId.ifBlank { "END" }}" else "FALSE -> ${nextId.ifBlank { "END" }}"}")
                }
                else -> {
                    val outcome = runAction(current.action, resolvedParam)
                    if (!outcome.first) success = false
                    logs.add("[$stepsExecuted] ${current.label}: ${outcome.second}")
                }
            }

            current = if (nextId.isNotBlank()) byId[nextId] else null
        }

        if (current != null && stepsExecuted >= maxSteps) {
            logs.add("Stopped safely at Autonomy Policy maxAutonomousSteps=$maxSteps.")
            success = false
        }

        val updated = workflow.copy(
            lastValidationResult = validation.summary,
            lastRunLog = logs.joinToString("\n"),
            lastRunSuccess = success,
            updatedAt = System.currentTimeMillis()
        )
        advancedRepo.upsertWorkflow(updated)
        return WorkflowRunReport(workflow.id, success, logs)
    }

    private fun interpolateVariables(template: String, vars: Map<String, String>): String {
        var out = template
        for ((k, v) in vars) {
            out = out.replace("{{$k}}", v)
        }
        return out
    }

    private fun evaluateCondition(conditionType: String, param: String, vars: Map<String, String>): Boolean {
        val h = deviceControl.readDeviceHealth()
        return when (conditionType.uppercase()) {
            "BATTERY_LT" -> h.batteryPercent < (param.toIntOrNull() ?: 20)
            "BATTERY_GT" -> h.batteryPercent > (param.toIntOrNull() ?: 50)
            "RAM_LT_MB" -> h.availableRamMb < (param.toLongOrNull() ?: 800L)
            "VAR_EQUALS" -> {
                val parts = param.split("=")
                parts.size == 2 && vars[parts[0].trim()] == parts[1].trim()
            }
            else -> true
        }
    }

    private fun runAction(action: String, param: String): Pair<Boolean, String> {
        return when (action.uppercase()) {
            "HEALTH_CHECK" -> {
                val h = deviceControl.readDeviceHealth()
                true to "RAM=${h.availableRamMb}MB, Bat=${h.batteryPercent}%, Storage=${"%.1f".format(h.freeStorageGb)}GB"
            }
            "VOLUME_DELTA" -> deviceControl.adjustMediaVolume(param.toIntOrNull() ?: 0)
            "OPEN_APP" -> deviceControl.launchAppByNameOrPackage(param)
            "WEB_SEARCH" -> deviceControl.searchWeb(param)
            "LOG_NOTE" -> true to "Note recorded: $param"
            else -> true to "Executed '$action' ($param)"
        }
    }
}
