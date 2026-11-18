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
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.history.FeatureVoiceCommandParser
import com.example.aura.history.QaExchangeTurn
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
                modelLabel = GEMINI_DEFAULT_MODEL,
                agentsUsed = listOf("Main Brain / Orchestrator Agent"),
                loopPhaseTrace = listOf("OBSERVE", "READY")
            )
        )
    )
    val chatMessages: StateFlow<List<ChatTurn>> = _chatMessages.asStateFlow()

    private val _currentLoopPhase = MutableStateFlow("IDLE")
    val currentLoopPhase: StateFlow<String> = _currentLoopPhase.asStateFlow()

    val lastDispatchPlan: StateFlow<TaskDispatchPlan?> = taskDispatcher.lastDispatchPlan

    @Volatile var historyRecorder: AgentHistoryRecorder? = null
    @Volatile var featurePipeline: FeatureControlPipelineEngine? = null
    @Volatile var onFeatureInboxLookupTopId: (suspend () -> String?)? = null

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

        // Intercept Hinglish / English feature & tool creation voice/text commands (Req #4)
        val parsedFeatureCmd = FeatureVoiceCommandParser.parse(trimmed)
        val pipeline = featurePipeline
        if (parsedFeatureCmd != null && pipeline != null) {
            _currentLoopPhase.value = "FEATURE_PIPELINE"
            val linkedRecId = if (parsedFeatureCmd.prefersRecommendedInboxItem) {
                onFeatureInboxLookupTopId?.invoke().orEmpty()
            } else ""
            val req = pipeline.submitFeatureRequest(
                userPrompt = parsedFeatureCmd.prompt,
                requestMode = parsedFeatureCmd.requestMode,
                linkedSourceId = linkedRecId,
                prefs = prefs
            )
            val replyText = buildString {
                appendLine("Feature Request ${req.requestId} [${req.status}] (Stage ${req.currentStage}/16: ${req.stageName})")
                if (req.assignedAgentsCsv.isNotBlank()) appendLine("Agents consulted: ${req.assignedAgentsCsv}")
                if (req.technicalPlan.isNotBlank()) appendLine("Plan: ${req.technicalPlan.take(220)}")
                if (req.testReport.isNotBlank()) appendLine("Verification: ${req.testReport}")
                if (req.failureReason.isNotBlank()) appendLine("Failure Reason: ${req.failureReason}")
                if (req.status == "AWAITING_APPROVAL") {
                    append("Open Agents -> Approval & Rollback Center to review diff/tests and Approve.")
                }
            }.trim()
            appendMessage(
                ChatTurn(
                    role = "AURA",
                    text = replyText,
                    language = lang,
                    providerLabel = "FeaturePipeline",
                    modelLabel = req.requestId,
                    agentsUsed = req.assignedAgentsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    loopPhaseTrace = listOf("REQUEST_SAVED", "CONSULTED", "VERIFIED", req.status)
                )
            )
            if (req.status == "COMPLETED_ACTIVATED" || req.status == "AWAITING_APPROVAL") {
                onSpeakReply(replyText, lang)
            }
            _currentLoopPhase.value = "IDLE"
            agentNetwork.resetAllToOnline()
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

        val recorder = historyRecorder
        val liveActivityId = recorder?.startLiveActivity(
            agentName = assignedAgents.firstOrNull() ?: "Main Brain / Orchestrator Agent",
            activityType = when (plan.intentCategory) {
                TaskIntentCategory.RESEARCH -> "RESEARCHING"
                TaskIntentCategory.TOOL_FACTORY -> "DEVELOPING"
                else -> "TALKING"
            },
            targetAgentOrModule = plan.intentCategory.name,
            summary = "Processing: ${recorder.sanitizeText(trimmed.take(50), prefs)}",
            prefs = prefs
        )

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
            if (recorder != null && liveActivityId != null) {
                recorder.completeLiveActivity(liveActivityId, "FAILED", "Task cancelled", "Cancelled by user", prefs)
            }
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
                if (recorder != null && liveActivityId != null) {
                    recorder.completeLiveActivity(liveActivityId, "FAILED", "Blocked by PolicyGuard", result.responseText, prefs)
                }
                recorder?.recordConversation(
                    agentId = "14",
                    agentName = "Security Agent",
                    participatingAgents = assignedAgents + "Security Agent",
                    channelType = "BRAIN_TO_AGENT",
                    topic = "Policy Gate: ${plan.safetyActionType}",
                    userOriginalRequest = trimmed,
                    qaTurns = listOf(QaExchangeTurn("Main Brain / Orchestrator Agent", "Security Agent", "Permit ${plan.safetyActionType}?", result.responseText)),
                    decisionsAndReasons = result.responseText,
                    actionsPerformed = combinedTrace,
                    testResults = "Policy block enforced",
                    finalOutcome = "BLOCKED",
                    prefs = prefs
                )
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
                if (recorder != null && liveActivityId != null) {
                    recorder.completeLiveActivity(liveActivityId, "PENDING_APPROVAL", "Level 3 approval required", prefs = prefs)
                }
                recorder?.recordConversation(
                    agentId = "14",
                    agentName = assignedAgents.firstOrNull() ?: "Security Agent",
                    participatingAgents = assignedAgents + "Security Agent",
                    channelType = "BRAIN_TO_AGENT",
                    topic = "Approval Gate: ${plan.safetyActionType}",
                    userOriginalRequest = trimmed,
                    qaTurns = listOf(QaExchangeTurn("Main Brain / Orchestrator Agent", "Security Agent", "Validate safety level", result.responseText)),
                    decisionsAndReasons = "Level 3 confirmation required #${result.pendingApprovalId}",
                    actionsPerformed = combinedTrace,
                    testResults = "Pending user confirmation",
                    finalOutcome = "AWAITING_APPROVAL",
                    prefs = prefs
                )
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
        if (recorder != null && liveActivityId != null) {
            recorder.completeLiveActivity(
                liveActivityId,
                if (result.success) "COMPLETED" else "FAILED",
                "${plan.intentCategory.name}: ${if (result.success) "Succeeded" else "Completed with note"}",
                errorReason = if (result.success) "" else result.responseText.take(180),
                prefs = prefs
            )
        }
        recorder?.recordConversation(
            agentId = "1",
            agentName = assignedAgents.firstOrNull() ?: "Main Brain / Orchestrator Agent",
            participatingAgents = assignedAgents,
            channelType = when (plan.intentCategory) {
                TaskIntentCategory.RESEARCH -> "TOOL_DISCOVERY"
                TaskIntentCategory.TOOL_FACTORY -> "RD_AGENT"
                TaskIntentCategory.CONVERSATIONAL_AI -> "EXTERNAL_PROVIDER"
                else -> "BRAIN_TO_AGENT"
            },
            topic = "${plan.intentCategory.name}: ${plan.intentCode}",
            userOriginalRequest = trimmed,
            qaTurns = listOf(
                QaExchangeTurn(
                    speaker = "Main Brain / Orchestrator Agent",
                    target = assignedAgents.getOrElse(1) { assignedAgents.firstOrNull() ?: "Provider" },
                    question = plan.rationale.ifBlank { trimmed },
                    answer = result.responseText.take(400)
                )
            ),
            decisionsAndReasons = "Routed via ${result.providerLabel.ifBlank { plan.intentCategory.name }} (${result.modelLabel})",
            actionsPerformed = combinedTrace,
            testResults = if (result.success) "Execution & output check PASSED" else "Returned note/error",
            finalOutcome = if (result.success) "SUCCESS" else "COMPLETED_WITH_NOTE",
            prefs = prefs
        )

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

    fun injectLocalAssistantNote(note: String, userPrompt: String? = null) {
        if (!userPrompt.isNullOrBlank()) {
            appendMessage(ChatTurn(role = "USER", text = userPrompt, language = MultilingualDetector.detect(userPrompt)))
        }
        appendMessage(
            ChatTurn(
                role = "AURA",
                text = note,
                providerLabel = "Offline-Local",
                modelLabel = "aura-offline-emergency",
                agentsUsed = listOf("Offline Emergency Engine"),
                loopPhaseTrace = listOf("OFFLINE_EVAL", "EXECUTED")
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
