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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.memory.GlobalDiscussionSessionEntity
import com.example.aura.memory.GlobalDiscussionTurnEntity
import com.example.aura.network.DiscussionTopicCategories
import com.example.aura.network.GlobalArchiveFilter
import com.example.aura.network.GlobalArchiveHit
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val councilDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalDiscussionCouncilTab(viewModel: AuraViewModel) {
    val ctrl = viewModel.globalNetworkController
    val sessions by ctrl.discussionSessions.collectAsStateWithLifecycle()
    val turns by ctrl.discussionTurns.collectAsStateWithLifecycle()
    val isBusy by ctrl.isNetworkBusy.collectAsStateWithLifecycle()

    var topicInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("AI_MODELS") }
    var questionInput by remember { mutableStateOf("") }
    var sourceLinksInput by remember { mutableStateOf("") }
    var featureNameInput by remember { mutableStateOf("") }
    var showDiscoveryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_council_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, contentDescription = "Global AI Discussion Council", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Global AI Discussion Council", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Convenes AURA specialist & connected external agents across 14 tech domains with evidence-based disagreement, alternatives, 10-step evaluation, and direct Feature Control promotion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DiscussionTopicCategories.ALL.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Discussion Topic") },
                        placeholder = { Text("e.g., Android Screen Control & Voice Streaming Models") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("council_topic_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        label = { Text("Question / Debate Goal") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("council_question_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = sourceLinksInput,
                        onValueChange = { sourceLinksInput = it },
                        label = { Text("Reference URLs (optional, CSV)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = featureNameInput,
                        onValueChange = { featureNameInput = it },
                        label = { Text("Proposed Feature Name (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                ctrl.startCouncilDiscussion(
                                    topic = topicInput,
                                    category = selectedCategory,
                                    question = questionInput,
                                    sourceLinksRaw = sourceLinksInput,
                                    proposedFeature = featureNameInput
                                )
                                topicInput = ""
                                questionInput = ""
                                sourceLinksInput = ""
                                featureNameInput = ""
                            },
                            enabled = !isBusy && topicInput.isNotBlank() && questionInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("council_start_btn")
                        ) {
                            Text("Convene Council")
                        }
                        OutlinedButton(
                            onClick = { showDiscoveryDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("council_evaluate_discovery_btn")
                        ) {
                            Icon(Icons.Filled.Science, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("10-Step Eval")
                        }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No council discussions recorded yet. Start a council debate or run a 10-step technology discovery evaluation above.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(sessions, key = { it.sessionId }) { session ->
                val sessionTurns = remember(turns, session.sessionId) {
                    turns.filter { it.sessionId == session.sessionId }
                }
                CouncilSessionCard(
                    session = session,
                    turns = sessionTurns,
                    onAddFeature = { ctrl.addFeatureFromDiscussion(session.sessionId) }
                )
            }
        }
    }

    if (showDiscoveryDialog) {
        TechDiscoveryEvaluationDialog(
            onDismiss = { showDiscoveryDialog = false },
            onSubmit = { name, platform, url, desc, license, cat ->
                ctrl.evaluateTechDiscovery(name, platform, url, desc, license, cat)
                showDiscoveryDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouncilSessionCard(
    session: GlobalDiscussionSessionEntity,
    turns: List<GlobalDiscussionTurnEntity>,
    onAddFeature: () -> Unit
) {
    var expandedTurns by remember { mutableStateOf(false) }
    val decisionAccent = when (session.decisionType) {
        "INTEGRATE", "ADAPT" -> MaterialTheme.colorScheme.tertiary
        "REJECT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("council_session_${session.sessionId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(session.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(text = session.decisionType, accent = decisionAccent)
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(text = session.topicCategory, accent = MaterialTheme.colorScheme.secondary)
                StatusPill(text = "${session.status} ${session.progressPercent}%", accent = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Question: ${session.originalQuestion}", style = MaterialTheme.typography.bodySmall)
            Text("Participants: ${session.participatingAgentsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (session.councilSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Council Summary: ${session.councilSummary}", style = MaterialTheme.typography.bodySmall)
            }
            if (session.decisionReasoning.isNotBlank()) {
                Text("Reasoning: ${session.decisionReasoning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (session.licenseAndSecurity.isNotBlank() || session.androidCompatibility.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("License & Security: ${session.licenseAndSecurity} | Android: ${session.androidCompatibility}", style = MaterialTheme.typography.labelSmall)
            }
            if (session.risksSummary.isNotBlank()) {
                Text("Risks: ${session.risksSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (session.relatedSessionIdsCsv.isNotBlank()) {
                Text("Related Discussions: ${session.relatedSessionIdsCsv}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.proposedFeatureName.isNotBlank()) {
                    if (session.linkedFeatureRequestId.isBlank()) {
                        Button(
                            onClick = onAddFeature,
                            modifier = Modifier.testTag("add_feature_from_${session.sessionId}")
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD THIS FEATURE TO AURA")
                        }
                    } else {
                        StatusPill(text = "Added -> ${session.linkedFeatureRequestId}", accent = MaterialTheme.colorScheme.tertiary)
                    }
                }
                TextButton(onClick = { expandedTurns = !expandedTurns }) {
                    Text(if (expandedTurns) "Hide Debate (${turns.size})" else "View Debate (${turns.size})")
                }
            }
            if (expandedTurns && turns.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                turns.forEach { t ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("${t.speakerAgent} [${t.providerOrPlatform}]", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                StatusPill(text = t.stance, accent = MaterialTheme.colorScheme.primary)
                            }
                            Text(t.messageText, style = MaterialTheme.typography.bodySmall)
                            if (t.evidenceLinksCsv.isNotBlank()) {
                                Text("Refs: ${t.evidenceLinksCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechDiscoveryEvaluationDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("GITHUB") }
    var url by remember { mutableStateOf("https://") }
    var desc by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("Apache-2.0") }
    var category by remember { mutableStateOf("AI_MODELS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("10-Step Technology Discovery Eval") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Technology / Feature Name") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GITHUB", "HUGGING_FACE", "AGENT_COUNCIL", "WEB").forEach { p ->
                        FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p) })
                    }
                }
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Repository / Model URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Capabilities & Architecture") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = license, onValueChange = { license = it }, label = { Text("License (e.g. MIT, Apache-2.0)") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AI_MODELS", "VOICE_LIVE", "ANDROID_AUTOMATION", "MCP_A2A", "SECURITY").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, platform, url, desc, license, category) },
                enabled = name.isNotBlank()
            ) { Text("Run 10-Step Eval") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalAiChatHistoryTab(
    viewModel: AuraViewModel,
    initialAgentFilter: String = ""
) {
    val ctrl = viewModel.globalNetworkController
    val filter by ctrl.archiveFilter.collectAsStateWithLifecycle()
    val results by ctrl.archiveResults.collectAsStateWithLifecycle()
    val turns by ctrl.discussionTurns.collectAsStateWithLifecycle()

    LaunchedEffect(initialAgentFilter) {
        if (initialAgentFilter.isNotBlank() && filter.agentFilter != initialAgentFilter) {
            ctrl.updateArchiveFilter(filter.copy(agentFilter = initialAgentFilter))
        } else {
            ctrl.refreshArchiveSearch()
        }
    }

    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    val quickQueries = listOf(
        "Show all conversations with GitHub agents",
        "What did Hugging Face agents recommend?",
        "Which agents discussed new voice models?",
        "Show all tools discovered this week",
        "Which recommended features were installed?",
        "Which features were rejected and why?",
        "Show all discussions about Android screen control",
        "What new models were recommended but not tested?"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_ai_chat_history_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Forum, contentDescription = "GLOBAL AI CHAT HISTORY", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GLOBAL AI CHAT HISTORY (${results.size} records)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Permanent archive of real agent conversations, AI Council sessions, links, recommendations & outcomes. Zero sample logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = filter.rawQuery,
                        onValueChange = { ctrl.updateArchiveFilter(filter.copy(rawQuery = it)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Natural query or keyword") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("archive_natural_search_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        quickQueries.forEachIndexed { i, q ->
                            FilterChip(
                                selected = filter.rawQuery.equals(q, ignoreCase = true),
                                onClick = { ctrl.updateArchiveFilter(GlobalArchiveFilter(rawQuery = q)) },
                                label = { Text(q.take(36), maxLines = 1) },
                                modifier = Modifier.testTag("archive_quick_query_$i")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "INSTALLED", "REJECTED", "UNTESTED", "FINISHED", "WAITING").forEach { out ->
                            FilterChip(
                                selected = filter.outcomeFilter == out,
                                onClick = { ctrl.updateArchiveFilter(filter.copy(outcomeFilter = out)) },
                                label = { Text(out) }
                            )
                        }
                        TextButton(onClick = { ctrl.updateArchiveFilter(GlobalArchiveFilter()) }) {
                            Text("Reset Filters")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = filter.agentFilter,
                            onValueChange = { ctrl.updateArchiveFilter(filter.copy(agentFilter = it)) },
                            label = { Text("Agent") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = filter.providerFilter,
                            onValueChange = { ctrl.updateArchiveFilter(filter.copy(providerFilter = it)) },
                            label = { Text("Provider") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (results.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No matching authorized conversations or discoveries found in the archive.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(results, key = { it.id }) { hit ->
                ArchiveHitCard(
                    hit = hit,
                    onInspectSession = { sid -> selectedSessionId = sid },
                    onAddFeature = { sid -> ctrl.addFeatureFromDiscussion(sid) }
                )
            }
        }
    }

    selectedSessionId?.let { sid ->
        val sessionTurns = turns.filter { it.sessionId == sid }
        AlertDialog(
            onDismissRequest = { selectedSessionId = null },
            title = { Text("Discussion Transcript ($sid)") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (sessionTurns.isEmpty()) {
                        Text("No turns recorded for this session.")
                    } else {
                        sessionTurns.forEach { t ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("${t.speakerAgent} -> ${t.targetAgent} (${t.stance})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(t.messageText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSessionId = null }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveHitCard(
    hit: GlobalArchiveHit,
    onInspectSession: (String) -> Unit,
    onAddFeature: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archive_hit_${hit.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(hit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(text = hit.kind, accent = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(hit.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "${councilDateFmt.format(Date(hit.timestamp))} • Agents: ${hit.agentName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hit.modelName.isNotBlank()) {
                Text("Model: ${hit.modelName} • Platform: ${hit.providerOrPlatform}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(hit.body.take(360), style = MaterialTheme.typography.bodySmall)
            if (hit.linksCsv.isNotBlank()) {
                Text("Links: ${hit.linksCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (hit.sessionId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onInspectSession(hit.sessionId) }) {
                        Text("Open Full Chat")
                    }
                    Button(onClick = { onAddFeature(hit.sessionId) }) {
                        Text("ADD THIS FEATURE TO AURA")
                    }
                }
            }
        }
    }
}
