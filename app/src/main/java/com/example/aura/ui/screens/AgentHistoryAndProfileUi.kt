package com.example.aura.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.agents.SpecialistAgent
import com.example.aura.history.AgentCommunicationHistoryUiState
import com.example.aura.history.AgentHistoryLogEntry
import com.example.aura.history.IndividualAgentProfileSummary
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val histDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private fun AgentConversationEntity.toLogEntry(
    recorder: com.example.aura.history.AgentHistoryRecorder
): AgentHistoryLogEntry {
    val turns = recorder.parseQaTurns(qaExchangedJson)
    val participants = participatingAgentsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val links = repositoryAndModelLinksCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val actions = actionsPerformedCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val proposed = proposedFeaturesOrToolsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    return when (channelType) {
        "A2A" -> AgentHistoryLogEntry.AgentToAgent(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp
        )
        "AI_COUNCIL" -> AgentHistoryLogEntry.CouncilDiscussion(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp,
            proposedFeatures = proposed
        )
        "EXTERNAL_PROVIDER" -> AgentHistoryLogEntry.ExternalProvider(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp,
            sensitivityRedacted = sensitivityRedacted
        )
        "RD_AGENT" -> AgentHistoryLogEntry.ResearchAndDev(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp,
            researchFindings = researchFindings,
            testResults = testResults,
            repositoryLinks = links
        )
        "TOOL_DISCOVERY" -> AgentHistoryLogEntry.ToolDiscovery(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp,
            recommendationsMade = recommendationsMade,
            repositoryLinks = links
        )
        else -> AgentHistoryLogEntry.BrainToAgent(
            id = id,
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            participatingAgents = participants,
            topic = topic,
            userOriginalRequest = userOriginalRequest,
            qaTurns = turns,
            decisionsAndReasons = decisionsAndReasons,
            finalOutcome = finalOutcome,
            timestamp = timestamp,
            actionsPerformed = actions
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentCommHistoryTab(
    viewModel: AuraViewModel,
    onOpenAgentProfile: (SpecialistAgent) -> Unit
) {
    val conversations by viewModel.historyController.conversations.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    var uiState by remember { mutableStateOf(AgentCommunicationHistoryUiState()) }
    var chronologicalAsc by remember { mutableStateOf(true) }

    val channels = listOf("ALL", "BRAIN_TO_AGENT", "A2A", "AI_COUNCIL", "EXTERNAL_PROVIDER", "RD_AGENT", "TOOL_DISCOVERY")
    val distinctAgents = remember(conversations) {
        conversations.flatMap { listOf(it.agentName) + it.participatingAgentsCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
    }
    val distinctTopics = remember(conversations) {
        conversations.map { it.topic.trim() }.filter { it.isNotBlank() }.distinct().take(6)
    }

    val now = System.currentTimeMillis()
    val filteredEntries = remember(conversations, uiState.searchQuery, uiState.selectedChannel, uiState.selectedAgentFilter, uiState.selectedTopicFilter, uiState.dateWindow, chronologicalAsc) {
        val q = uiState.searchQuery.trim().lowercase()
        val agentQ = uiState.selectedAgentFilter.trim().lowercase()
        val topicQ = uiState.selectedTopicFilter.trim().lowercase()

        val mapped = conversations.filter { c ->
            val matchChan = uiState.selectedChannel == "ALL" || c.channelType == uiState.selectedChannel
            val matchDate = when (uiState.dateWindow) {
                "24H" -> c.timestamp >= now - 86_400_000L
                "7D" -> c.timestamp >= now - 7 * 86_400_000L
                else -> true
            }
            val matchAgent = agentQ.isEmpty() ||
                c.agentName.lowercase().contains(agentQ) ||
                c.participatingAgentsCsv.lowercase().contains(agentQ)
            val matchTopic = topicQ.isEmpty() || c.topic.lowercase().contains(topicQ)
            val matchQ = q.isEmpty() ||
                c.topic.lowercase().contains(q) ||
                c.agentName.lowercase().contains(q) ||
                c.participatingAgentsCsv.lowercase().contains(q) ||
                c.userOriginalRequest.lowercase().contains(q) ||
                c.qaExchangedJson.lowercase().contains(q) ||
                c.finalOutcome.lowercase().contains(q)
            matchChan && matchDate && matchAgent && matchTopic && matchQ
        }.map { it.toLogEntry(viewModel.historyController.recorder) }

        if (chronologicalAsc) mapped.sortedBy { it.timestamp } else mapped.sortedByDescending { it.timestamp }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("agent_comm_history_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agent Communication History Center (${filteredEntries.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Permanent chronological timeline of Brain <-> Agent, A2A, Council, External AI, R&D & Discovery transcripts (API keys & tokens redacted).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { uiState = uiState.copy(searchQuery = it) },
                        label = { Text("Search conversations, topics, or agent names") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comm_history_search_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.selectedAgentFilter,
                            onValueChange = { uiState = uiState.copy(selectedAgentFilter = it) },
                            label = { Text("Agent filter") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comm_history_agent_filter")
                        )
                        OutlinedTextField(
                            value = uiState.selectedTopicFilter,
                            onValueChange = { uiState = uiState.copy(selectedTopicFilter = it) },
                            label = { Text("Topic filter") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comm_history_topic_filter")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        channels.forEach { ch ->
                            FilterChip(
                                selected = uiState.selectedChannel == ch,
                                onClick = { uiState = uiState.copy(selectedChannel = ch) },
                                label = { Text(ch.replace('_', ' ')) }
                            )
                        }
                    }
                    if (distinctAgents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = uiState.selectedAgentFilter.isBlank(),
                                onClick = { uiState = uiState.copy(selectedAgentFilter = "") },
                                label = { Text("All Agents") }
                            )
                            distinctAgents.forEach { ag ->
                                val short = ag.substringBefore(" Agent").substringBefore(" /").take(18)
                                FilterChip(
                                    selected = uiState.selectedAgentFilter.equals(ag, ignoreCase = true),
                                    onClick = {
                                        val next = if (uiState.selectedAgentFilter.equals(ag, true)) "" else ag
                                        uiState = uiState.copy(selectedAgentFilter = next)
                                    },
                                    label = { Text(short) }
                                )
                            }
                        }
                    }
                    if (distinctTopics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = uiState.selectedTopicFilter.isBlank(),
                                onClick = { uiState = uiState.copy(selectedTopicFilter = "") },
                                label = { Text("All Topics") }
                            )
                            distinctTopics.forEach { tp ->
                                FilterChip(
                                    selected = uiState.selectedTopicFilter.equals(tp, ignoreCase = true),
                                    onClick = {
                                        val next = if (uiState.selectedTopicFilter.equals(tp, true)) "" else tp
                                        uiState = uiState.copy(selectedTopicFilter = next)
                                    },
                                    label = { Text(tp.take(22)) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All Time", "24H" to "Last 24h", "7D" to "Last 7 Days").forEach { (key, label) ->
                            FilterChip(
                                selected = uiState.dateWindow == key,
                                onClick = { uiState = uiState.copy(dateWindow = key) },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = chronologicalAsc,
                            onClick = { chronologicalAsc = !chronologicalAsc },
                            label = { Text(if (chronologicalAsc) "Timeline: Oldest -> Newest" else "Timeline: Newest -> Oldest") }
                        )
                    }
                }
            }
        }

        items(filteredEntries, key = { it.id }) { entry ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${entry.conversationId} • ${histDateFmt.format(Date(entry.timestamp))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(text = entry.channelLabel, accent = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.historyController.deleteSingleConversation(entry.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete conversation record")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Primary Agent #${entry.agentId}: ${entry.agentName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Participants: ${entry.participatingAgents.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val matchedAgent = agents.firstOrNull { it.name.equals(entry.agentName, true) }
                    if (matchedAgent != null) {
                        TextButton(onClick = { onOpenAgentProfile(matchedAgent) }) {
                            Icon(Icons.Filled.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open ${matchedAgent.name.substringBefore(" Agent")} Profile")
                        }
                    }

                    if (entry.userOriginalRequest.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "User Request: ${entry.userOriginalRequest}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Chat-style Q&A bubbles
                    entry.qaTurns.forEach { turn ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "${turn.speaker} -> ${turn.target}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (turn.question.isNotBlank()) {
                                    Text("Q: ${turn.question}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (turn.answer.isNotBlank()) {
                                    Text("A: ${turn.answer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    when (entry) {
                        is AgentHistoryLogEntry.ResearchAndDev -> {
                            if (entry.researchFindings.isNotBlank()) {
                                Text("Findings: ${entry.researchFindings}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.repositoryLinks.isNotEmpty()) {
                                Text("Links: ${entry.repositoryLinks.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            if (entry.testResults.isNotBlank()) {
                                Text("Test Results: ${entry.testResults}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is AgentHistoryLogEntry.ToolDiscovery -> {
                            if (entry.recommendationsMade.isNotBlank()) {
                                Text("Recommendations: ${entry.recommendationsMade}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.repositoryLinks.isNotEmpty()) {
                                Text("Links: ${entry.repositoryLinks.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        is AgentHistoryLogEntry.CouncilDiscussion -> {
                            if (entry.proposedFeatures.isNotEmpty()) {
                                Text("Proposed Features/Tools: ${entry.proposedFeatures.joinToString()}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is AgentHistoryLogEntry.BrainToAgent -> {
                            if (entry.actionsPerformed.isNotEmpty()) {
                                Text("Actions: ${entry.actionsPerformed.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is AgentHistoryLogEntry.ExternalProvider -> {
                            Text(
                                if (entry.sensitivityRedacted) "Privacy: Credentials & sensitive text redacted" else "Privacy: Standard",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AgentHistoryLogEntry.AgentToAgent -> Unit
                    }

                    if (entry.decisionsAndReasons.isNotBlank()) {
                        Text("Decision & Reason: ${entry.decisionsAndReasons}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Final Outcome: ${entry.finalOutcome}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun LiveAgentActivityTab(viewModel: AuraViewModel) {
    val activities by viewModel.historyController.liveActivities.collectAsStateWithLifecycle()
    val proposals by viewModel.historyController.changeProposals.collectAsStateWithLifecycle()

    val liveNow = activities.filter { it.status == "LIVE" }
    val pending = activities.filter { it.status == "PENDING_APPROVAL" }
    val failed = activities.filter { it.status == "FAILED" }
    val completed = activities.filter { it.status == "COMPLETED" }
    val pendingPropsCount = proposals.count { it.status == "PENDING" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_agent_activity_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Agent Activity Dashboard", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Real-time runtime state from Room Activity Log (zero fabricated statuses): " +
                            "${liveNow.size} active • ${pending.size + pendingPropsCount} waiting approval • ${completed.size} succeeded • ${failed.size} failed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (activities.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No runtime agent activity recorded yet. Send a chat prompt or submit a feature request to watch real activity populate here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(activities, key = { it.id }) { act ->
            val accent = when (act.status) {
                "LIVE" -> MaterialTheme.colorScheme.primary
                "COMPLETED" -> MaterialTheme.colorScheme.tertiary
                "FAILED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${act.agentName} • ${act.activityType}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = act.status, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(act.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Target: ${act.targetAgentOrModule} • ${histDateFmt.format(Date(act.startedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (act.errorReason.isNotBlank()) {
                        Text(
                            "Failure Reason: ${act.errorReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndividualAgentProfileDialog(
    summary: IndividualAgentProfileSummary,
    onDismiss: () -> Unit
) {
    var sectionFilter by remember { mutableIntStateOf(0) }
    var topicFilter by remember { mutableStateOf("") }
    val sections = listOf("Overview", "Conversations", "Research", "Features", "Failures")
    val q = topicFilter.trim().lowercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("#${summary.agent.id} ${summary.agent.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${summary.agent.preferredProvider} / ${summary.agent.preferredModel} • ${summary.agent.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(summary.agent.responsibility, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Workload: ${summary.workloadLabel} | Success Rate: ${summary.successRatePercent}% | Last Active: ${histDateFmt.format(Date(summary.agent.lastActivityAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = topicFilter,
                    onValueChange = { topicFilter = it },
                    label = { Text("Filter agent history by topic/task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sections.forEachIndexed { i, title ->
                        FilterChip(
                            selected = sectionFilter == i,
                            onClick = { sectionFilter = i },
                            label = { Text(title) }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                when (sectionFilter) {
                    0 -> {
                        Text("Assigned Tasks: ${summary.assignedTasks.size}", style = MaterialTheme.typography.labelMedium)
                        Text("Conversations Recorded: ${summary.conversations.size}", style = MaterialTheme.typography.labelMedium)
                        Text("Tools/Features Discovered: ${summary.discoveredFeatures.size}", style = MaterialTheme.typography.labelMedium)
                        Text("Registry Events / Implemented: ${summary.implementedRegistryEvents.size}", style = MaterialTheme.typography.labelMedium)
                        Text("Errors & Failures: ${summary.failuresAndErrors.size}", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        summary.assignedTasks.filter { q.isEmpty() || it.instruction.lowercase().contains(q) }.take(5).forEach { t ->
                            Text("• [${t.status}] ${t.instruction.take(70)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        val list = summary.conversations.filter { q.isEmpty() || it.topic.lowercase().contains(q) || it.userOriginalRequest.lowercase().contains(q) }
                        if (list.isEmpty()) Text("No conversations matching filter.", style = MaterialTheme.typography.bodySmall)
                        list.take(12).forEach { c ->
                            Text("[${c.channelType}] ${c.topic}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${c.finalOutcome} • ${histDateFmt.format(Date(c.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    2 -> {
                        val list = summary.researchItems.filter { q.isEmpty() || it.name.lowercase().contains(q) }
                        if (list.isEmpty()) Text("No external research items linked to this agent.", style = MaterialTheme.typography.bodySmall)
                        list.take(10).forEach { r ->
                            Text("• ${r.name} (${r.sourcePlatform}) — ${r.installationStatus}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    3 -> {
                        summary.discoveredFeatures.forEach { rec ->
                            Text("Discovered [${rec.inboxStatus}]: ${rec.featureName}", style = MaterialTheme.typography.bodySmall)
                        }
                        summary.implementedRegistryEvents.forEach { reg ->
                            Text("Registry [${reg.activationStatus}] v${reg.version}: ${reg.featureName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    4 -> {
                        if (summary.failuresAndErrors.isEmpty()) {
                            Text("Zero failures recorded for this agent.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            summary.failuresAndErrors.forEach { err ->
                                Text("• ${err.activityType}: ${err.errorReason.ifBlank { err.summary }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
