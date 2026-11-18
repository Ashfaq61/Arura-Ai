package com.example.aura.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.agents.AgentStatus
import com.example.aura.automation.AccessibilityBridge
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.calls.PhoneCallAndMessagingManager
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.memory.ApprovalRequestEntity
import com.example.aura.memory.AuraPreferences
import com.example.aura.memory.MemoryEntryEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.TaskRecordEntity
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.GuardDecision
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatTurn(
    val id: Long = System.nanoTime(),
    val role: String, // "USER", "AURA", "SYSTEM"
    val text: String,
    val language: AuraLanguage = AuraLanguage.ENGLISH,
    val providerLabel: String = "",
    val modelLabel: String = "",
    val agentsUsed: List<String> = emptyList(),
    val loopPhaseTrace: List<String> = emptyList(),
    val pendingApprovalId: Long? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class AuraOrchestrator(
    private val aiRouter: MultiProviderRouter,
    private val agentNetwork: AgentNetworkManager,
    private val permissionGuard: PermissionGuard,
    private val memoryRepository: MemoryRepository,
    private val deviceControl: DeviceControlEngine,
    private val researchClient: GitHubAndHfResearchClient,
    private val toolFactory: ToolFactoryAndEvolutionEngine,
    private val callMessaging: PhoneCallAndMessagingManager,
    private val privacyRouter: PrivacyDataRouter = PrivacyDataRouter(),
    private val injectionDefender: PromptInjectionDefender = PromptInjectionDefender(),
    private val selfHealing: SelfHealingEngine? = null,
    private val modelRouter: ModelRoutingEngine = ModelRoutingEngine(),
    val taskDispatcher: TaskDispatcher = TaskDispatcher(
        aiRouter = aiRouter,
        modelRouter = modelRouter,
        agentNetwork = agentNetwork,
        permissionGuard = permissionGuard,
        memoryRepository = memoryRepository,
        deviceControl = deviceControl,
        researchClient = researchClient,
        toolFactory = toolFactory,
        callMessaging = callMessaging,
        privacyRouter = privacyRouter,
        injectionDefender = injectionDefender,
        selfHealing = selfHealing
    )
) {
    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatTurn(
                role = "AURA",
                text = "AURA AI Neural Core online. I support English, اردو (Urdu), and Roman Urdu. Ask me to reason, research GitHub/Hugging Face, read the screen, control apps, analyze camera/PDFs, or build new tools.",
                providerLabel = "Orchestrator",
                modelLabel = "gemini-3.8-flash",
                agentsUsed = listOf("Main Brain / Orchestrator Agent"),
                loopPhaseTrace = listOf("OBSERVE", "READY")
            )
        )
    )
    val chatMessages: StateFlow<List<ChatTurn>> = _chatMessages.asStateFlow()

    private val _currentLoopPhase = MutableStateFlow("IDLE")
    val currentLoopPhase: StateFlow<String> = _currentLoopPhase.asStateFlow()

    val lastDispatchPlan: StateFlow<TaskDispatchPlan?> = taskDispatcher.lastDispatchPlan

    suspend fun processInstruction(
        instruction: String,
        prefs: AuraPreferences,
        attachedBitmap: Bitmap? = null,
        onSpeakReply: (String, AuraLanguage) -> Unit = { _, _ -> }
    ) {
        val trimmed = instruction.trim()
        if (trimmed.isEmpty()) return

        val lang = MultilingualDetector.detect(trimmed, prefs.preferredLanguage)
        appendMessage(ChatTurn(role = "USER", text = trimmed, language = lang))

        // Immediate stop command detection
        if (trimmed.equals("stop", true) || trimmed.equals("ruko", true) || trimmed.equals("band karo", true)) {
            permissionGuard.triggerEmergencyStop()
            _currentLoopPhase.value = "EMERGENCY_STOPPED"
            appendMessage(
                ChatTurn(
                    role = "AURA",
                    text = when (lang) {
                        AuraLanguage.URDU -> "فوری اسٹاپ فعال کر دیا گیا ہے۔ تمام ایجنٹس اور آٹومیشن روک دیے گئے ہیں۔"
                        AuraLanguage.ROMAN_URDU -> "Emergency STOP active ho gaya hai. Sab agents aur automation rok diye gaye hain."
                        AuraLanguage.ENGLISH -> "Emergency STOP activated. All active tasks, voice output, and automation have been halted."
                    },
                    language = lang,
                    agentsUsed = listOf("Security Agent"),
                    loopPhaseTrace = listOf("OBSERVE", "STOP")
                )
            )
            return
        }

        if (permissionGuard.emergencyStopActive.value) {
            appendMessage(
                ChatTurn(
                    role = "AURA",
                    text = "Emergency STOP is active. Tap Resume System in the top bar or Security Center before running new commands.",
                    language = lang,
                    agentsUsed = listOf("Security Agent")
                )
            )
            return
        }

        val startTime = System.currentTimeMillis()
        val trace = mutableListOf<String>()
        fun phase(p: String) {
            _currentLoopPhase.value = p
            trace.add(p)
        }

        phase("OBSERVE")
        agentNetwork.setAgentState("Main Brain / Orchestrator Agent", AgentStatus.ACTIVE, "Observing & routing: ${trimmed.take(40)}")
        val screenSnap = AccessibilityBridge.latestSnapshot.value
        val recentMemories = if (prefs.memoryEnabled && !prefs.privateMode) {
            memoryRepository.getRecentMemories(6).joinToString("; ") { "${it.title}: ${it.content}" }
        } else ""

        val taskId = memoryRepository.insertTask(
            TaskRecordEntity(
                instruction = trimmed,
                detectedLanguage = lang.code,
                assignedAgents = "Main Brain / Orchestrator Agent",
                planStepsJson = "[]",
                status = "RUNNING",
                verificationNote = "Executing autonomous loop"
            )
        )

        phase("UNDERSTAND & DISPATCH")
        val request = IncomingTaskRequest(
            rawInstruction = trimmed,
            language = lang,
            attachedBitmap = attachedBitmap,
            screenSummary = screenSnap.visibleTextSummary,
            prefs = prefs,
            isNetworkAvailable = selfHealing?.isNetworkOnline() ?: true
        )
        val plan = taskDispatcher.evaluateIntentWithPlanner(request, recentMemories)
        val assignedAgents = plan.assignedAgents
        val stepsList = plan.steps

        if (plan.clarifyingQuestion.isNotBlank() && plan.intentCode == "CHAT") {
            phase("CLARIFY")
            appendMessage(
                ChatTurn(
                    role = "AURA",
                    text = plan.clarifyingQuestion,
                    language = lang,
                    agentsUsed = assignedAgents,
                    loopPhaseTrace = trace
                )
            )
            finishTask(taskId, assignedAgents, stepsList, "COMPLETED", "Clarification requested", startTime)
            _currentLoopPhase.value = "IDLE"
            agentNetwork.resetAllToOnline()
            return
        }

        phase("ROUTE & EXECUTE")
        val isStreamingTarget = plan.target is DispatchTarget.AiProvider || plan.target is DispatchTarget.HybridVisionProvider
        val streamingTurnId = if (isStreamingTarget) {
            System.nanoTime().also { turnId ->
                appendMessage(
                    ChatTurn(
                        id = turnId,
                        role = "AURA",
                        text = "",
                        language = lang,
                        providerLabel = prefs.activeProviderId,
                        modelLabel = prefs.activeModelId,
                        agentsUsed = assignedAgents,
                        loopPhaseTrace = trace,
                        isStreaming = true
                    )
                )
            }
        } else null

        val result = try {
            taskDispatcher.dispatch(request, plan) { deltaText, provider, model ->
                if (streamingTurnId != null) {
                    updateStreamingMessage(
                        id = streamingTurnId,
                        text = deltaText,
                        provider = provider,
                        model = model,
                        isStreaming = true
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TaskDispatcher execution failed", e)
            TaskDispatchResult(
                success = false,
                plan = plan,
                responseText = "Error: ${e.localizedMessage ?: "Execution failed"}",
                providerLabel = prefs.activeProviderId,
                modelLabel = prefs.activeModelId,
                trace = trace + "ERROR"
            )
        }

        val combinedTrace = (trace + result.trace).distinct()

        when (result.guardDecision) {
            is GuardDecision.Blocked -> {
                if (streamingTurnId != null) {
                    updateStreamingMessage(streamingTurnId, result.responseText, "Security Agent", "PolicyGuard", false)
                } else {
                    appendMessage(
                        ChatTurn(
                            role = "AURA",
                            text = result.responseText,
                            language = lang,
                            agentsUsed = listOf("Security Agent"),
                            loopPhaseTrace = combinedTrace
                        )
                    )
                }
                finishTask(taskId, assignedAgents, stepsList, "FAILED", result.responseText, startTime)
                _currentLoopPhase.value = "IDLE"
                agentNetwork.resetAllToOnline()
                return
            }
            is GuardDecision.RequiresApproval -> {
                if (streamingTurnId != null) {
                    updateStreamingMessage(streamingTurnId, result.responseText, "Security Agent", "Level 3 Gate", false)
                } else {
                    appendMessage(
                        ChatTurn(
                            role = "AURA",
                            text = result.responseText,
                            language = lang,
                            agentsUsed = assignedAgents + "Security Agent",
                            loopPhaseTrace = combinedTrace,
                            pendingApprovalId = result.pendingApprovalId
                        )
                    )
                }
                finishTask(taskId, assignedAgents, stepsList, "AWAITING_APPROVAL", "Waiting user confirmation #${result.pendingApprovalId}", startTime)
                _currentLoopPhase.value = "IDLE"
                agentNetwork.resetAllToOnline()
                return
            }
            else -> {
                if (streamingTurnId != null) {
                    updateStreamingMessage(
                        id = streamingTurnId,
                        text = result.responseText,
                        provider = result.providerLabel.ifBlank { prefs.activeProviderId },
                        model = result.modelLabel.ifBlank { prefs.activeModelId },
                        isStreaming = false
                    )
                } else {
                    appendMessage(
                        ChatTurn(
                            role = "AURA",
                            text = result.responseText,
                            language = lang,
                            providerLabel = result.providerLabel,
                            modelLabel = result.modelLabel,
                            agentsUsed = assignedAgents,
                            loopPhaseTrace = combinedTrace
                        )
                    )
                }
                if (result.success) {
                    onSpeakReply(result.responseText, lang)
                }
            }
        }

        phase("VERIFY & LEARN")
        if (prefs.memoryEnabled && !prefs.privateMode && result.responseText.isNotBlank() && plan.intentCode != "MEMORY") {
            val sensitiveHint = trimmed.contains("password", true) || trimmed.contains("pin", true) || trimmed.contains("cnic", true)
            if (!sensitiveHint) {
                memoryRepository.saveMemory(
                    MemoryEntryEntity(
                        category = "CONVERSATION",
                        title = trimmed.take(48),
                        content = "User: $trimmed\nAURA: ${result.responseText.take(280)}",
                        language = lang.code
                    )
                )
            }
        }

        finishTask(taskId, assignedAgents, stepsList, if (result.success) "COMPLETED" else "COMPLETED_WITH_NOTE", "Verified execution (${plan.intentCategory.name})", startTime)
        _currentLoopPhase.value = "IDLE"
        agentNetwork.resetAllToOnline()
    }

    suspend fun executeApprovedRequest(request: ApprovalRequestEntity) {
        val payload = runCatching { JSONObject(request.payloadJson) }.getOrNull()
        val action = payload?.optString("actionType", request.actionType) ?: request.actionType
        val param = payload?.optString("param", "") ?: ""
        val target = payload?.optString("target", request.targetPackage) ?: request.targetPackage

        val outcome = when {
            action.contains("place_call") -> callMessaging.placeDirectCall(param.ifBlank { target })
            action.contains("whatsapp_send") || action.contains("send_message") -> callMessaging.openWhatsAppDraft(target, param)
            action.contains("open_app") -> {
                if (target.isNotBlank()) permissionGuard.setPackagePolicy(target, allow = true)
                deviceControl.launchAppByNameOrPackage(target.ifBlank { param })
            }
            else -> true to "Approved action '$action' acknowledged."
        }
        memoryRepository.resolveApproval(request.id, true)
        memoryRepository.logAudit("User Approval", action, 3, "APPROVED", outcome.second)
        appendMessage(
            ChatTurn(
                role = "AURA",
                text = "Approved & Executed: ${outcome.second}",
                agentsUsed = listOf("Security Agent", "Main Brain / Orchestrator Agent"),
                loopPhaseTrace = listOf("APPROVE", "EXECUTE", "VERIFY")
            )
        )
    }

    private fun appendMessage(turn: ChatTurn) {
        _chatMessages.value = _chatMessages.value + turn
    }

    private fun updateStreamingMessage(
        id: Long,
        text: String,
        provider: String,
        model: String,
        isStreaming: Boolean
    ) {
        _chatMessages.value = _chatMessages.value.map {
            if (it.id == id) {
                it.copy(text = text, providerLabel = provider, modelLabel = model, isStreaming = isStreaming)
            } else it
        }
    }

    private suspend fun finishTask(
        taskId: Long,
        agents: List<String>,
        steps: List<String>,
        status: String,
        note: String,
        startTime: Long
    ) {
        memoryRepository.updateTask(
            TaskRecordEntity(
                id = taskId,
                instruction = _chatMessages.value.lastOrNull { it.role == "USER" }?.text.orEmpty(),
                detectedLanguage = "AUTO",
                assignedAgents = agents.joinToString(", "),
                planStepsJson = JSONArray(steps).toString(),
                status = status,
                verificationNote = note,
                durationMs = System.currentTimeMillis() - startTime
            )
        )
    }

    companion object {
        private const val TAG = "AuraOrchestrator"
    }
}
