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
import com.example.aura.history.IndividualAgentProfileSummary
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val histDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentCommHistoryTab(
    viewModel: AuraViewModel,
    onOpenAgentProfile: (SpecialistAgent) -> Unit
) {
    val conversations by viewModel.historyController.conversations.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf("ALL") }
    var dateWindow by remember { mutableStateOf("ALL") }

    val channels = listOf("ALL", "BRAIN_TO_AGENT", "A2A", "AI_COUNCIL", "EXTERNAL_PROVIDER", "RD_AGENT", "TOOL_DISCOVERY")
    val now = System.currentTimeMillis()
    val filtered = remember(conversations, query, selectedChannel, dateWindow) {
        val q = query.trim().lowercase()
        conversations.filter { c ->
            val matchChan = selectedChannel == "ALL" || c.channelType == selectedChannel
            val matchDate = when (dateWindow) {
                "24H" -> c.timestamp >= now - 86_400_000L
                "7D" -> c.timestamp >= now - 7 * 86_400_000L
                else -> true
            }
            val matchQ = q.isEmpty() ||
                c.topic.lowercase().contains(q) ||
                c.agentName.lowercase().contains(q) ||
                c.participatingAgentsCsv.lowercase().contains(q) ||
                c.userOriginalRequest.lowercase().contains(q) ||
                c.qaExchangedJson.lowercase().contains(q) ||
                c.finalOutcome.lowercase().contains(q)
            matchChan && matchDate && matchQ
        }
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
                        Text("Agent Communication History Center (${filtered.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Permanent sanitized log of Brain <-> Agent, A2A, Council, External AI, R&D & Discovery transcripts (API keys & tokens redacted).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search conversations, topics, or agent names") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comm_history_search_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        channels.forEach { ch ->
                            FilterChip(
                                selected = selectedChannel == ch,
                                onClick = { selectedChannel = ch },
                                label = { Text(ch.replace('_', ' ')) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All Time", "24H" to "Last 24h", "7D" to "Last 7 Days").forEach { (key, label) ->
                            FilterChip(
                                selected = dateWindow == key,
                                onClick = { dateWindow = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { conv ->
            val qaTurns = remember(conv.qaExchangedJson) {
                viewModel.historyController.recorder.parseQaTurns(conv.qaExchangedJson)
            }
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
                            Text(conv.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${conv.conversationId} • ${histDateFmt.format(Date(conv.timestamp))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(text = conv.channelType, accent = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.historyController.deleteSingleConversation(conv.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete conversation record")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Primary Agent #${conv.agentId}: ${conv.agentName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Participants: ${conv.participatingAgentsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val matchedAgent = agents.firstOrNull { it.name.equals(conv.agentName, true) }
                    if (matchedAgent != null) {
                        TextButton(onClick = { onOpenAgentProfile(matchedAgent) }) {
                            Icon(Icons.Filled.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open ${matchedAgent.name.substringBefore(" Agent")} Profile")
                        }
                    }

                    if (conv.userOriginalRequest.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "User Request: ${conv.userOriginalRequest}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Chat-style Q&A bubbles
                    qaTurns.forEach { turn ->
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

                    if (conv.researchFindings.isNotBlank()) {
                        Text("Findings: ${conv.researchFindings}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (conv.recommendationsMade.isNotBlank()) {
                        Text("Recommendations: ${conv.recommendationsMade}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (conv.repositoryAndModelLinksCsv.isNotBlank()) {
                        Text("Links: ${conv.repositoryAndModelLinksCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (conv.proposedFeaturesOrToolsCsv.isNotBlank()) {
                        Text("Proposed Features/Tools: ${conv.proposedFeaturesOrToolsCsv}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (conv.decisionsAndReasons.isNotBlank()) {
                        Text("Decision & Reason: ${conv.decisionsAndReasons}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (conv.actionsPerformedCsv.isNotBlank()) {
                        Text("Actions: ${conv.actionsPerformedCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (conv.testResults.isNotBlank()) {
                        Text("Test Results: ${conv.testResults}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Final Outcome: ${conv.finalOutcome}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
