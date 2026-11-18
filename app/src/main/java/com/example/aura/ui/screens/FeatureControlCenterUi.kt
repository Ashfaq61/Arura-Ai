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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import org.json.JSONArray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFeatureRequestTab(viewModel: AuraViewModel) {
    val requests by viewModel.historyController.featureRequests.collectAsStateWithLifecycle()
    val recommendations by viewModel.historyController.recommendations.collectAsStateWithLifecycle()
    val isRunning by viewModel.historyController.isPipelineRunning.collectAsStateWithLifecycle()

    var requestPrompt by remember { mutableStateOf("") }
    var requestMode by remember { mutableStateOf("NATURAL") }
    var linkedRecId by remember { mutableStateOf("") }

    val modes = listOf(
        "NATURAL" to "Natural Request",
        "NEW_TOOL" to "Build New Tool",
        "IMPROVE_FEATURE" to "Improve Feature",
        "COMBINE_TOOLS" to "Combine Tools",
        "CREATE_AGENT" to "Create Specialist Agent",
        "GITHUB_REPO" to "GitHub Repo",
        "HF_MODEL" to "Hugging Face Model",
        "FROM_RECOMMENDATION" to "Inbox Pick"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("add_feature_request_screen")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ADD NEW FEATURE / TOOL", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Runs AURA's 16-stage Feature Request Execution Pipeline: Request -> Agent Consultation -> Plan -> Compat/License/Security -> Develop & Test -> Policy Gate -> Registry & Outcome. Also supports voice commands in Chat (e.g. 'AURA, ye feature bhi add karo').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        modes.forEach { (modeKey, label) ->
                            FilterChip(
                                selected = requestMode == modeKey,
                                onClick = { requestMode = modeKey },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (requestMode == "FROM_RECOMMENDATION" && recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Discovered Feature:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            recommendations.filter { it.inboxStatus != "REJECTED" }.forEach { rec ->
                                FilterChip(
                                    selected = linkedRecId == rec.id,
                                    onClick = {
                                        linkedRecId = rec.id
                                        requestPrompt = rec.featureName
                                    },
                                    label = { Text(rec.featureName.take(28)) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = requestPrompt,
                        onValueChange = { requestPrompt = it },
                        label = { Text("Describe feature, GitHub/HF URL, tool combination, or agent role") },
                        placeholder = { Text("e.g., Daily Expense Digest MCP tool OR Create Fitness Coach Agent") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feature_request_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Button(
                        onClick = {
                            viewModel.historyController.submitFeatureRequest(requestPrompt, requestMode, linkedRecId)
                            requestPrompt = ""
                        },
                        enabled = !isRunning && requestPrompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_feature_request_btn")
                    ) {
                        Text("Submit to 16-Stage Feature Pipeline")
                    }
                }
            }
        }

        items(requests, key = { it.requestId }) { req ->
            val accent = when (req.status) {
                "COMPLETED_ACTIVATED" -> MaterialTheme.colorScheme.tertiary
                "AWAITING_APPROVAL" -> MaterialTheme.colorScheme.secondary
                "FAILED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
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
                        Text("${req.requestId} (${req.requestMode})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = req.status, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Request: ${req.userPrompt}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (req.currentStage / 16f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stage ${req.currentStage}/16: ${req.stageName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (req.assignedAgentsCsv.isNotBlank()) {
                        Text("Agents Consulted: ${req.assignedAgentsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (req.technicalPlan.isNotBlank()) {
                        Text("Plan: ${req.technicalPlan}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (req.alternativesSummary.isNotBlank()) {
                        Text("Alternatives: ${req.alternativesSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (req.compatibilityReport.isNotBlank()) {
                        Text("Compatibility: ${req.compatibilityReport}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (req.securityReport.isNotBlank()) {
                        Text("Security & License: ${req.securityReport}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (req.requiredApisAndPerms.isNotBlank()) {
                        Text("APIs & Perms: ${req.requiredApisAndPerms}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (req.testReport.isNotBlank()) {
                        Text("Tests: ${req.testReport}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (req.failureReason.isNotBlank()) {
                        Text("Failure Reason: ${req.failureReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.historyController.retryFailedFeatureRequest(req.requestId) },
                            modifier = Modifier.testTag("retry_failed_req_${req.requestId}")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry / Continue Request")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureDiscoveryInboxTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val recommendations by viewModel.historyController.recommendations.collectAsStateWithLifecycle()
    val isRunning by viewModel.historyController.isPipelineRunning.collectAsStateWithLifecycle()
    var statusFilter by remember { mutableStateOf("ALL") }
    var activeQuestionTargetId by remember { mutableStateOf<String?>(null) }
    var questionDraft by remember { mutableStateOf("") }

    val filtered = remember(recommendations, statusFilter) {
        if (statusFilter == "ALL") recommendations else recommendations.filter { it.inboxStatus == statusFilter }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("feature_discovery_inbox_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Feature Discovery Inbox (${filtered.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Specialist agent recommendations stay DISCOVERED_ONLY until validated, tested, and approved. Click 'Add This Feature' for 1-click 16-step execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "NEW", "SAVED_FOR_LATER", "IN_PIPELINE", "IMPLEMENTED", "REJECTED").forEach { st ->
                            FilterChip(
                                selected = statusFilter == st,
                                onClick = { statusFilter = st },
                                label = { Text(st.replace('_', ' ')) }
                            )
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { rec ->
            val opinions = remember(rec.otherAgentOpinionsJson) {
                runCatching {
                    val arr = JSONArray(rec.otherAgentOpinionsJson)
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        "${o.optString("agent")} (${o.optString("stance")}): ${o.optString("note")}"
                    }
                }.getOrDefault(emptyList())
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
                        Text(rec.featureName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = rec.inboxStatus, accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("What it does: ${rec.summaryWhatItDoes}", style = MaterialTheme.typography.bodySmall)
                    Text("Why AURA needs it: ${rec.whyAuraNeedsIt}", style = MaterialTheme.typography.bodySmall)
                    Text("Limitation solved: ${rec.limitationSolved}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Discovering Agent: ${rec.discoveringAgent}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    opinions.forEach { op ->
                        Text("• $op", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Security: ${rec.securityAssessment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Compatibility: ${rec.compatibilityAssessment}", style = MaterialTheme.typography.labelSmall)
                    Text("Effort: ${rec.developmentEffort} | Cost/Resource: ${rec.costResourceImpact}", style = MaterialTheme.typography.labelSmall)
                    Text("Tests Required: ${rec.testRequirements}", style = MaterialTheme.typography.labelSmall)

                    if (rec.sourceLinksCsv.startsWith("http")) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rec.sourceLinksCsv.substringBefore(",")))) }
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Source (${rec.sourceLinksCsv.take(40)})")
                        }
                    }

                    if (rec.agentFollowUpNotes.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                rec.agentFollowUpNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (activeQuestionTargetId == rec.id) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = questionDraft,
                            onValueChange = { questionDraft = it },
                            label = { Text("Ask agents about '${rec.featureName}'") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.historyController.askInboxAgentsMoreInfo(rec.id, questionDraft)
                                    activeQuestionTargetId = null
                                    questionDraft = ""
                                }
                            ) { Text("Send Question") }
                            TextButton(onClick = { activeQuestionTargetId = null }) { Text("Cancel") }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { viewModel.historyController.addFeatureFromInbox(rec.id) },
                            enabled = !isRunning,
                            modifier = Modifier.testTag("add_feature_inbox_${rec.id}")
                        ) {
                            Text("Add This Feature")
                        }
                        OutlinedButton(
                            onClick = { activeQuestionTargetId = if (activeQuestionTargetId == rec.id) null else rec.id },
                            enabled = !isRunning
                        ) {
                            Text("Ask Agents More Info")
                        }
                        OutlinedButton(
                            onClick = { viewModel.historyController.compareInboxAlternatives(rec.id) },
                            enabled = !isRunning
                        ) {
                            Text("Compare Alternatives")
                        }
                        OutlinedButton(
                            onClick = { viewModel.historyController.developOwnVersionFromInbox(rec.id) },
                            enabled = !isRunning
                        ) {
                            Text("Develop My Own Version")
                        }
                        TextButton(onClick = { viewModel.historyController.saveInboxForLater(rec.id) }) {
                            Text("Save for Later")
                        }
                        TextButton(onClick = { viewModel.historyController.rejectInboxRecommendation(rec.id) }) {
                            Text("Reject", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
