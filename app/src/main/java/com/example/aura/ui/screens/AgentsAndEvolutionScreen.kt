package com.example.aura.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.agents.AgentStatus
import com.example.aura.agents.SpecialistAgent
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsAndEvolutionScreen(viewModel: AuraViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val proposals by viewModel.historyController.changeProposals.collectAsStateWithLifecycle()
    val pendingProposalsCount = proposals.count { it.status == "PENDING" }
    val netNodes by viewModel.globalNetworkController.agentNodes.collectAsStateWithLifecycle()
    val netNotifs by viewModel.globalNetworkController.notifications.collectAsStateWithLifecycle()
    val connectedNetCount = netNodes.count { it.connectionStatus == com.example.aura.network.AgentConnectionStatus.CONNECTED }
    val unreadNotifCount = netNotifs.count { !it.read }
    var archiveAgentPrefilter by remember { mutableStateOf("") }
    var inspectedAgent by remember { mutableStateOf<SpecialistAgent?>(null) }

    val tabs = listOf(
        "Agents (${agents.size})",
        "Network ($connectedNetCount/${netNodes.size})",
        "AI Council",
        "Global AI Chat",
        if (unreadNotifCount > 0) "Live Monitor ($unreadNotifCount)" else "Live Monitor",
        "Comm History",
        "Live Activity",
        "Add Feature",
        "Discovery Inbox",
        "Feature Registry",
        if (pendingProposalsCount > 0) "Approvals ($pendingProposalsCount)" else "Approvals & Rollback",
        "History Search",
        "Long-Horizon",
        "Skill Memory",
        "Schedules",
        "Self-Evolution",
        "Research Hub",
        "Tool Factory",
        "Smart Routines",
        "Workflow Builder",
        "Recipes",
        "App Compat",
        "Tool Health"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(title, maxLines = 1) },
                    modifier = Modifier.testTag("agents_subtab_$idx")
                )
            }
        }

        when (subTab) {
            0 -> AgentsMeshTab(viewModel = viewModel, onInspectAgent = { inspectedAgent = it })
            1 -> GlobalAgentNetworkTab(
                viewModel = viewModel,
                onOpenAgentChatHistory = { agentName ->
                    archiveAgentPrefilter = agentName
                    subTab = 3
                }
            )
            2 -> GlobalDiscussionCouncilTab(viewModel = viewModel)
            3 -> GlobalAiChatHistoryTab(
                viewModel = viewModel,
                initialAgentFilter = archiveAgentPrefilter
            )
            4 -> LiveDiscussionMonitorTab(
                viewModel = viewModel,
                onOpenSessionDetail = { _ -> subTab = 2 }
            )
            5 -> AgentCommHistoryTab(viewModel = viewModel, onOpenAgentProfile = { inspectedAgent = it })
            6 -> LiveAgentActivityTab(viewModel = viewModel)
            7 -> AddFeatureRequestTab(viewModel = viewModel)
            8 -> FeatureDiscoveryInboxTab(viewModel = viewModel)
            9 -> FeatureRegistryHistoryTab(viewModel = viewModel)
            10 -> ChangeApprovalAndRollbackTab(viewModel = viewModel)
            11 -> GlobalHistorySearchAndRetentionTab(viewModel = viewModel)
            12 -> LongHorizonPlannerTab(viewModel)
            13 -> SkillMemoryTab(viewModel)
            14 -> ScheduledTasksTab(viewModel)
            15 -> SelfEvolutionTab(viewModel)
            16 -> ResearchHubTab(viewModel)
            17 -> ToolFactoryTab(viewModel)
            18 -> SmartRoutinesTab(viewModel)
            19 -> VisualWorkflowBuilderTab(viewModel)
            20 -> AutomationRecipesTab(viewModel)
            21 -> AppCompatibilityTab(viewModel)
            22 -> ToolHealthDashboardTab(viewModel)
        }
    }

    inspectedAgent?.let { ag ->
        val conversations by viewModel.historyController.conversations.collectAsStateWithLifecycle()
        val allTasks by viewModel.tasks.collectAsStateWithLifecycle()
        val research by viewModel.researchProjects.collectAsStateWithLifecycle()
        val recs by viewModel.historyController.recommendations.collectAsStateWithLifecycle()
        val reg by viewModel.historyController.registryHistory.collectAsStateWithLifecycle()
        val reqs by viewModel.historyController.featureRequests.collectAsStateWithLifecycle()
        val acts by viewModel.historyController.liveActivities.collectAsStateWithLifecycle()
        val summary = remember(ag, agents, conversations, allTasks, research, recs, reg, reqs, acts) {
            viewModel.historyController.buildProfileForAgent(ag)
        }
        IndividualAgentProfileDialog(
            summary = summary,
            onDismiss = { inspectedAgent = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentsMeshTab(
    viewModel: AuraViewModel,
    onInspectAgent: (SpecialistAgent) -> Unit
) {
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val a2aFeed by viewModel.a2aFeed.collectAsStateWithLifecycle()
    val mcpTools by viewModel.mcpTools.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("A2A Inter-Agent Message Bus (${a2aFeed.size} events)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (a2aFeed.isEmpty()) {
                        Text(
                            "Send any command in Chat to observe live A2A handoffs between specialist agents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        a2aFeed.take(4).forEach { msg ->
                            Text(
                                text = "[${msg.messageType}] ${msg.fromAgent.substringBefore(" Agent")} → ${msg.toAgent.substringBefore(" Agent")}: ${msg.payload.take(70)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Registered MCP Tools: ${mcpTools.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        items(agents, key = { it.codeName }) { agent ->
            val accent = when (agent.status) {
                AgentStatus.ONLINE -> MaterialTheme.colorScheme.tertiary
                AgentStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                AgentStatus.BLOCKED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                onClick = { onInspectAgent(agent) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_card_${agent.codeName}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${agent.id}. ${agent.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (agent.isCustom) {
                            StatusPill(text = "CUSTOM", accent = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        StatusPill(text = agent.status.name, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(agent.responsibility, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Model: ${agent.preferredProvider} / ${agent.preferredModel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        agent.capabilities.forEach { cap ->
                            StatusPill(text = cap, accent = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Task: ${agent.currentTask} • Done: ${agent.completedTasks}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onInspectAgent(agent) }) {
                            Icon(Icons.Filled.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile & History")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelfEvolutionTab(viewModel: AuraViewModel) {
    val cycles by viewModel.evolutionCycles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var focusTopic by remember { mutableStateOf("android ai agent mcp") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Self-Evolving Intelligence Engine", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Continuously researches GitHub/Hugging Face, evaluates license & RAM fit, runs safety checks, benchmarks, and supports instant rollback. Never claims foundation weight retraining.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("WorkManager 6h Background Cycle", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = prefs.backgroundEvolutionEnabled,
                            onCheckedChange = { viewModel.setBackgroundEvolution(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = focusTopic,
                        onValueChange = { focusTopic = it },
                        label = { Text("Capability / Research Focus") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.runSelfEvolutionCycle(focusTopic) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_evolution_cycle_btn")
                    ) {
                        Text("Run Autonomous Self-Improvement Cycle")
                    }
                }
            }
        }

        item {
            ZeroQuestionAutonomySection(viewModel)
        }

        item {
            SignedSelfModSection(viewModel)
        }

        items(cycles, key = { it.id }) { cycle ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cycle #${cycle.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(text = cycle.status, accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Gap: ${cycle.gapIdentified}", style = MaterialTheme.typography.bodyMedium)
                    Text("Candidate: ${cycle.candidateSolution}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Evaluators: ${cycle.evaluatorAgents}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Security: ${cycle.staticAnalysisResult}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Benchmark: ${cycle.benchmarkSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResearchHubTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val projects by viewModel.researchProjects.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var platform by remember { mutableStateOf("RESEARCH_EVAL") }
    var query by remember { mutableStateOf("android accessibility agent") }

    val displayedProjects = if (platform == "RESEARCH_EVAL") {
        projects.filter { it.sourcePlatform == "RESEARCH_EVAL" }.ifEmpty { projects }
    } else projects

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Research Hub & Evaluated Targets", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = platform == "RESEARCH_EVAL",
                            onClick = { platform = "RESEARCH_EVAL" },
                            label = { Text("Evaluated Targets (7)") }
                        )
                        FilterChip(
                            selected = platform == "GITHUB",
                            onClick = { platform = "GITHUB"; query = "android ai agent" },
                            label = { Text("GitHub Repos") }
                        )
                        FilterChip(
                            selected = platform == "HUGGING_FACE",
                            onClick = { platform = "HUGGING_FACE"; query = "gemma" },
                            label = { Text("Hugging Face Models") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search topic (e.g., OpenPhoneUse, ARTEMIS, Gemma)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (platform != "RESEARCH_EVAL") {
                        Button(
                            onClick = { viewModel.runResearchSearch(platform, query) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("research_search_button")
                        ) {
                            Text("Search Live ${if (platform == "GITHUB") "GitHub API" else "Hugging Face API"}")
                        }
                    }
                }
            }
        }

        items(displayedProjects, key = { it.id }) { proj ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(proj.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = proj.sourcePlatform, accent = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(proj.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Release/Tag: ${proj.latestReleaseOrUpdate} • ${proj.metricsLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("License: ${proj.license} • Compat: ${proj.compatibility}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Deps: ${proj.dependencies} • Est. Resources: ${proj.resourceEstimate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Security: ${proj.securityAssessment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Status: ${proj.installationStatus} | Tests: ${proj.testStatus}", style = MaterialTheme.typography.labelSmall)
                    Text("Why recommended: ${proj.recommendationReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proj.url)))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Source Link")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolFactoryTab(viewModel: AuraViewModel) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var capabilityInput by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Productivity") }
    val categories = listOf("Productivity", "Research Tools", "Android Automation", "MCP Tools", "Security", "Coding Tools")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autonomous Tool Factory", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "When a capability is missing, AURA designs a sandboxed declarative MCP tool, runs static safety checks (no untrusted DEX/binary loading), documents it, and publishes it to the Marketplace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = capabilityInput,
                        onValueChange = { capabilityInput = it },
                        label = { Text("Describe missing capability or tool to build") },
                        placeholder = { Text("e.g., Daily Urdu news briefing summarizer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tool_factory_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (capabilityInput.isNotBlank()) {
                                viewModel.synthesizeTool(capabilityInput, category)
                                capabilityInput = ""
                            }
                        },
                        enabled = !isBusy && capabilityInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tool_factory_build_btn")
                    ) {
                        Text("Design, Static-Scan & Publish Tool")
                    }
                }
            }
        }
    }
}
