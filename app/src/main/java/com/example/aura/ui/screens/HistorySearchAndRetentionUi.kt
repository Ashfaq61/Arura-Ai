package com.example.aura.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.aura.history.HistoryDomain
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val searchDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalHistorySearchAndRetentionTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val filter by viewModel.historyController.searchFilter.collectAsStateWithLifecycle()
    val results by viewModel.historyController.searchResults.collectAsStateWithLifecycle()
    val lastExport by viewModel.historyController.lastExport.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var queryDraft by remember(filter.query) { mutableStateOf(filter.query) }
    var agentDraft by remember(filter.agentFilter) { mutableStateOf(filter.agentFilter) }
    var repoDraft by remember(filter.repoOrModelFilter) { mutableStateOf(filter.repoOrModelFilter) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_history_search_screen")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Advanced History Search & Export (${results.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = queryDraft,
                        onValueChange = {
                            queryDraft = it
                            viewModel.historyController.updateSearchFilter(filter.copy(query = it))
                        },
                        label = { Text("Search across conversations, research, requests, tools, tests & errors") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_search_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = agentDraft,
                            onValueChange = {
                                agentDraft = it
                                viewModel.historyController.updateSearchFilter(filter.copy(agentFilter = it))
                            },
                            label = { Text("Agent Filter") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repoDraft,
                            onValueChange = {
                                repoDraft = it
                                viewModel.historyController.updateSearchFilter(filter.copy(repoOrModelFilter = it))
                            },
                            label = { Text("Repo / Model Filter") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "SUCCESS", "FAILED", "PENDING", "DISCOVERED_ONLY").forEach { out ->
                            FilterChip(
                                selected = filter.outcomeFilter == out,
                                onClick = { viewModel.historyController.updateSearchFilter(filter.copy(outcomeFilter = out)) },
                                label = { Text(out.replace('_', ' ')) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HistoryDomain.entries.forEach { domain ->
                            FilterChip(
                                selected = domain in filter.domains,
                                onClick = { viewModel.historyController.toggleDomainFilter(domain) },
                                label = { Text(domain.name.replace('_', ' ')) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { viewModel.historyController.exportFilteredHistory("JSON") },
                            modifier = Modifier.testTag("export_history_json_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { viewModel.historyController.exportFilteredHistory("CSV") },
                            modifier = Modifier.testTag("export_history_csv_btn")
                        ) {
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = { viewModel.historyController.exportFilteredHistory("PDF") },
                            modifier = Modifier.testTag("export_history_pdf_btn")
                        ) {
                            Text("Export PDF")
                        }
                    }

                    lastExport?.let { exp ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${exp.format} ready (${exp.itemCount} records): ${exp.file.name}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(exp.previewSnippet, style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = {
                                    viewModel.historyController.searchAndExport.buildShareIntent(exp)?.let { intent ->
                                        runCatching { context.startActivity(Intent.createChooser(intent, "Share AURA History")) }
                                    }
                                }) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Exported File")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Memory & History Retention Controls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "API keys, passwords, and auth tokens are strictly NEVER stored in agent history or exports. Current: ${prefs.historyRetentionDays}d retention, max ${prefs.maxHistoryEntries} entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30 to 500, 90 to 1000, 365 to 5000).forEach { (d, max) ->
                            FilterChip(
                                selected = prefs.historyRetentionDays == d,
                                onClick = { viewModel.historyController.updateRetentionPolicy(d, max, prefs.recordSensitiveChatDetails) },
                                label = { Text("${d}d / $max max") }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Record unmasked sensitive prompt notes", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = prefs.recordSensitiveChatDetails,
                            onCheckedChange = {
                                viewModel.historyController.updateRetentionPolicy(prefs.historyRetentionDays, prefs.maxHistoryEntries, it)
                            }
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.historyController.deleteHistoryScope("CONVERSATIONS") }) {
                            Text("Clear Conversations")
                        }
                        OutlinedButton(onClick = { viewModel.historyController.deleteHistoryScope("ACTIVITIES") }) {
                            Text("Clear Activity Log")
                        }
                        TextButton(onClick = { viewModel.historyController.deleteHistoryScope("ALL") }) {
                            Text("Delete All History", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        items(results.take(100), key = { it.id }) { hit ->
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
                        Text(hit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = hit.domain.name, accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(hit.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${searchDateFmt.format(Date(hit.timestamp))} • Actors: ${hit.agentOrActor} • Outcome: ${hit.outcome}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
