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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val regDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureRegistryHistoryTab(viewModel: AuraViewModel) {
    val history by viewModel.historyController.registryHistory.collectAsStateWithLifecycle()
    var eventFilter by remember { mutableStateOf("ALL") }

    val filtered = remember(history, eventFilter) {
        if (eventFilter == "ALL") history else history.filter { it.activationStatus == eventFilter || it.changeEventType == eventFilter }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("feature_registry_history_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Feature & Tool Change Registry (${filtered.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tracks every discovered, proposed, developed, installed, upgraded, disabled, rolled back, and removed capability. Never claims an unverified or external repo is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "ACTIVE", "NEEDS_APPROVAL", "DEVELOPMENT_REQUIRED", "DISCOVERED_ONLY", "UPGRADED", "ROLLED_BACK").forEach { f ->
                            FilterChip(
                                selected = eventFilter == f,
                                onClick = { eventFilter = f },
                                label = { Text(f.replace('_', ' ')) }
                            )
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { item ->
            val accent = when (item.activationStatus) {
                "ACTIVE" -> MaterialTheme.colorScheme.tertiary
                "FAILED" -> MaterialTheme.colorScheme.error
                "ROLLED_BACK", "DISCOVERED_ONLY", "DEVELOPMENT_REQUIRED" -> MaterialTheme.colorScheme.secondary
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.featureName} • v${item.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Event: ${item.changeEventType} • ${regDateFmt.format(Date(item.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(text = item.activationStatus, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Source: ${item.discoverySource} | Recommender: ${item.recommendingAgent}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Participants: ${item.participatingAgentsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.sourceUrl.isNotBlank()) {
                        Text("Source URL: ${item.sourceUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (item.previousVersion != null) {
                        Text("Previous Version: v${item.previousVersion} -> v${item.version}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Dependencies: ${item.dependencies} | Perms: ${item.permissionsRequired}", style = MaterialTheme.typography.labelSmall)
                    Text("Tests: ${item.testResults}", style = MaterialTheme.typography.labelSmall)
                    Text("Decision Reason: ${item.decisionReason}", style = MaterialTheme.typography.bodySmall)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            "Diff / Changes: ${item.versionDiffSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (item.rollbackSnapshotJson.isNotBlank() && item.changeEventType != "ROLLED_BACK") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.rollbackFeatureFromRegistry(
                                    featureId = item.featureId,
                                    rollbackSnapshotJson = item.rollbackSnapshotJson
                                )
                            },
                            modifier = Modifier.testTag("rollback_registry_${item.id}")
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Roll Back to v${item.previousVersion ?: "previous"}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChangeApprovalAndRollbackTab(viewModel: AuraViewModel) {
    val proposals by viewModel.historyController.changeProposals.collectAsStateWithLifecycle()
    var statusFilter by remember { mutableStateOf("ALL") }
    var modTargetId by remember { mutableStateOf<String?>(null) }
    var modNotes by remember { mutableStateOf("") }
    var expandedReportId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(proposals, statusFilter) {
        if (statusFilter == "ALL") proposals else proposals.filter { it.status == statusFilter }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("change_approval_rollback_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Approval, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Approval & Rollback Center (${filtered.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Safe, reversible changes within pre-authorized permissions can auto-activate with full audit & rollback. High-impact permissions, external accounts, repos, messaging, or paid features require explicit approval.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "PENDING", "AUTO_ACTIVATED", "APPROVED", "PAUSED", "REJECTED", "ROLLED_BACK").forEach { st ->
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

        if (filtered.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No change proposals matching this filter yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { prop ->
            val accent = when (prop.status) {
                "PENDING" -> MaterialTheme.colorScheme.primary
                "APPROVED", "AUTO_ACTIVATED" -> MaterialTheme.colorScheme.tertiary
                "REJECTED", "CANCELLED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
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
                            Text("${prop.title} (v${prop.targetVersion})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${prop.id} • Request ${prop.requestId} • ${prop.changeType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(text = prop.status, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(prop.summary, style = MaterialTheme.typography.bodySmall)
                    Text("Gate Policy: ${prop.approvalReason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (prop.modificationNotes.isNotBlank()) {
                        Text("Notes: ${prop.modificationNotes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { expandedReportId = if (expandedReportId == prop.id) null else prop.id }) {
                        Text(if (expandedReportId == prop.id) "Hide Version Diff & Test Report" else "Compare Versions & View Test Report")
                    }

                    if (expandedReportId == prop.id) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Version Compare: ${prop.previousVersion ?: "none"} -> v${prop.targetVersion}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(prop.versionDiff, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Test Report: ${prop.testReport}", style = MaterialTheme.typography.labelSmall)
                                Text("Security: ${prop.securityAssessment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }

                    if (modTargetId == prop.id) {
                        OutlinedTextField(
                            value = modNotes,
                            onValueChange = { modNotes = it },
                            label = { Text("Specify modifications required") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                viewModel.historyController.requestProposalModifications(prop.id, modNotes)
                                modTargetId = null
                                modNotes = ""
                            }) { Text("Submit Modifications") }
                            TextButton(onClick = { modTargetId = null }) { Text("Cancel") }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (prop.status == "PENDING" || prop.status == "PAUSED" || prop.status == "MODIFICATIONS_REQUESTED") {
                            Button(
                                onClick = { viewModel.historyController.approveChangeProposal(prop.id) },
                                modifier = Modifier.testTag("approve_proposal_${prop.id}")
                            ) {
                                Text("Approve")
                            }
                            OutlinedButton(onClick = { modTargetId = if (modTargetId == prop.id) null else prop.id }) {
                                Text("Request Modifications")
                            }
                            OutlinedButton(onClick = { viewModel.historyController.pauseChangeProposal(prop.id) }) {
                                Text("Pause")
                            }
                            TextButton(onClick = { viewModel.historyController.rejectChangeProposal(prop.id) }) {
                                Text("Reject", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { viewModel.historyController.cancelChangeProposal(prop.id) }) {
                                Text("Cancel Task")
                            }
                        }
                        if (prop.status == "APPROVED" || prop.status == "AUTO_ACTIVATED") {
                            OutlinedButton(
                                onClick = { viewModel.historyController.rollbackChangeProposal(prop.id) },
                                modifier = Modifier.testTag("rollback_proposal_${prop.id}")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Roll Back Update")
                            }
                        }
                    }
                }
            }
        }
    }
}
