package com.example.aura.agents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class AgentStatus {
    ONLINE,
    ACTIVE,
    WAITING_APPROVAL,
    BLOCKED,
    RECOVERING
}

data class SpecialistAgent(
    val id: Int,
    val codeName: String,
    val name: String,
    val responsibility: String,
    val capabilities: List<String>,
    val status: AgentStatus = AgentStatus.ONLINE,
    val currentTask: String = "Idle — ready for delegation",
    val completedTasks: Int = 0,
    val errorCount: Int = 0,
    val avgLatencyMs: Long = 0L
)

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromAgent: String,
    val toAgent: String,
    val protocol: String = "A2A/1.0",
    val messageType: String, // "REQUEST", "PROGRESS", "HANDOFF", "VERIFY", "ERROR"
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class McpToolDescriptor(
    val name: String,
    val category: String,
    val description: String,
    val inputSchemaJson: String,
    val safetyLevel: Int,
    val enabled: Boolean = true
)

/**
 * Coordinates the 24-agent specialist network with A2A-style inter-agent messaging,
 * shared task context, MCP tool registration, and error escalation.
 */
class AgentNetworkManager {

    private val _agents = MutableStateFlow(buildInitial24Agents())
    val agents: StateFlow<List<SpecialistAgent>> = _agents.asStateFlow()

    private val _a2aFeed = MutableStateFlow<List<AgentMessage>>(emptyList())
    val a2aFeed: StateFlow<List<AgentMessage>> = _a2aFeed.asStateFlow()

    private val _mcpTools = MutableStateFlow(buildCoreMcpTools())
    val mcpTools: StateFlow<List<McpToolDescriptor>> = _mcpTools.asStateFlow()

    fun setAgentState(agentName: String, status: AgentStatus, taskSummary: String) {
        _agents.value = _agents.value.map { agent ->
            if (agent.name.equals(agentName, ignoreCase = true) ||
                agent.codeName.equals(agentName, ignoreCase = true)
            ) {
                agent.copy(
                    status = status,
                    currentTask = taskSummary,
                    completedTasks = if (status == AgentStatus.ONLINE && agent.status == AgentStatus.ACTIVE) {
                        agent.completedTasks + 1
                    } else agent.completedTasks,
                    errorCount = if (status == AgentStatus.RECOVERING || status == AgentStatus.BLOCKED) {
                        agent.errorCount + 1
                    } else agent.errorCount
                )
            } else {
                agent
            }
        }
    }

    fun resetAllToOnline() {
        _agents.value = _agents.value.map {
            it.copy(status = AgentStatus.ONLINE, currentTask = "Idle — ready for delegation")
        }
    }

    fun sendA2aMessage(
        fromAgent: String,
        toAgent: String,
        messageType: String,
        payload: String
    ) {
        val msg = AgentMessage(
            fromAgent = fromAgent,
            toAgent = toAgent,
            messageType = messageType,
            payload = payload
        )
        _a2aFeed.value = (listOf(msg) + _a2aFeed.value).take(120)
    }

    fun registerMcpTool(descriptor: McpToolDescriptor) {
        val filtered = _mcpTools.value.filterNot { it.name == descriptor.name }
        _mcpTools.value = listOf(descriptor) + filtered
    }

    fun unregisterMcpTool(name: String) {
        _mcpTools.value = _mcpTools.value.filterNot { it.name == name }
    }

    private fun buildInitial24Agents(): List<SpecialistAgent> = listOf(
        SpecialistAgent(1, "orchestrator", "Main Brain / Orchestrator Agent", "Parses intent, decomposes multi-step plans, routes models, and verifies outcomes.", listOf("planning", "multilingual", "routing")),
        SpecialistAgent(2, "research", "Research Agent", "Synthesizes cross-source evidence and structured research briefs.", listOf("synthesis", "comparison")),
        SpecialistAgent(3, "web_search", "Web Search Agent", "Queries web endpoints, extracts webpage content, and summarizes docs.", listOf("http_fetch", "html_extract")),
        SpecialistAgent(4, "github_research", "GitHub Research Agent", "Discovers trending Android/AI repositories, releases, and licenses.", listOf("github_api", "license_check")),
        SpecialistAgent(5, "hf_research", "Hugging Face Research Agent", "Evaluates open-source LLMs, TTS/STT models, and RAM fit.", listOf("hf_api", "hardware_fit")),
        SpecialistAgent(6, "coding", "Coding Agent", "Generates Kotlin/Compose code, MCP specs, and refactor patches.", listOf("kotlin", "compose", "json_schema")),
        SpecialistAgent(7, "android_auto", "Android Automation Agent", "Executes Accessibility gestures, node clicks, app launches, and intents.", listOf("tap", "scroll", "swipe", "launch")),
        SpecialistAgent(8, "screen_vision", "Screen Vision Agent", "Inspects Accessibility trees and CameraX bitmaps via multimodal vision.", listOf("ui_tree", "camerax", "ocr")),
        SpecialistAgent(9, "voice", "Voice Agent", "Manages SpeechRecognizer, TTS presets (Kore/Aoede/Zephyr), and audio focus.", listOf("stt", "tts", "turn_taking")),
        SpecialistAgent(10, "phone_call", "Phone Call Agent", "Monitors call states, initiates confirmed dials, and enforces call consent.", listOf("telecom", "call_state")),
        SpecialistAgent(11, "whatsapp", "WhatsApp Agent", "Reads opt-in message notifications, summarizes threads, and drafts replies.", listOf("notifications", "wa_intent")),
        SpecialistAgent(12, "social_media", "Social Media Agent", "Drafts posts, captions, hashtags, and prepares share intents.", listOf("captions", "share_intent")),
        SpecialistAgent(13, "memory", "Memory Agent", "Maintains searchable Room memory, preferences, and JSON export.", listOf("room_fts", "private_mode")),
        SpecialistAgent(14, "security", "Security Agent", "Enforces Level 1-4 policies, Keystore vault, and Emergency STOP.", listOf("aes_gcm", "policy_gate")),
        SpecialistAgent(15, "testing", "Testing Agent", "Runs diagnostics, connection tests, and honest feature verification.", listOf("diagnostics", "audit")),
        SpecialistAgent(16, "apk_build", "APK Build Agent", "Manages GitHub Actions CI workflows, build logs, and release notes.", listOf("gradle_ci", "artifacts")),
        SpecialistAgent(17, "performance", "Performance Optimization Agent", "Monitors RAM, CPU/battery state, cache size, and low-memory recovery.", listOf("ram_monitor", "statfs")),
        SpecialistAgent(18, "tool_discovery", "Tool Discovery Agent", "Finds open-source tools and MCP servers for missing capabilities.", listOf("discovery", "compatibility")),
        SpecialistAgent(19, "tool_factory", "Tool Factory Agent", "Designs, static-scans, tests, and publishes new tools to Marketplace.", listOf("synthesize", "sandbox_scan")),
        SpecialistAgent(20, "self_upgrade", "Self-Upgrade Agent", "Runs continuous evolution cycles and proposes safe, reversible updates.", listOf("workmanager", "rollback")),
        SpecialistAgent(21, "marketplace", "Marketplace Manager Agent", "Handles install, enable, disable, update, uninstall, and rollback.", listOf("registry", "versioning")),
        SpecialistAgent(22, "error_recovery", "Error Recovery Agent", "Detects tool/provider failures, switches fallbacks, and retries safely.", listOf("fallback", "retry")),
        SpecialistAgent(23, "notification", "Notification Agent", "Ingests opt-in notifications, filters priority alerts, and summarizes.", listOf("notification_listener")),
        SpecialistAgent(24, "file_doc", "File and Document Agent", "Reads PDFs via PdfRenderer/SAF, runs OCR Q&A, and shares summaries.", listOf("pdf_renderer", "saf_picker"))
    )

    private fun buildCoreMcpTools(): List<McpToolDescriptor> = listOf(
        McpToolDescriptor("device.open_app", "Android Automation", "Launch an installed Android app via PackageManager.", """{"app":"string"}""", 2),
        McpToolDescriptor("device.search_web", "Research Tools", "Open web or YouTube search intent.", """{"platform":"web|youtube|maps","query":"string"}""", 2),
        McpToolDescriptor("device.volume", "Android Automation", "Adjust media or ring volume safely.", """{"delta":"int"}""", 1),
        McpToolDescriptor("screen.inspect", "Vision Tools", "Read visible UI nodes from AccessibilityService.", """{}""", 1),
        McpToolDescriptor("screen.gesture", "Android Automation", "Dispatch tap, scroll, or swipe gesture.", """{"type":"tap|scroll_down|scroll_up|swipe_left|swipe_right"}""", 2),
        McpToolDescriptor("research.github", "Research Tools", "Search live GitHub repositories.", """{"query":"string"}""", 1),
        McpToolDescriptor("research.huggingface", "Research Tools", "Search live Hugging Face models.", """{"query":"string"}""", 1),
        McpToolDescriptor("memory.save", "Productivity", "Persist a preference or context item to Room.", """{"title":"string","content":"string"}""", 1),
        McpToolDescriptor("comm.whatsapp_draft", "Communication", "Draft WhatsApp reply (Level 3 confirmation before send).", """{"contact":"string","message":"string"}""", 3),
        McpToolDescriptor("comm.place_call", "Communication", "Initiate phone dialer/call (Level 3 confirmation).", """{"number":"string"}""", 3)
    )
}
