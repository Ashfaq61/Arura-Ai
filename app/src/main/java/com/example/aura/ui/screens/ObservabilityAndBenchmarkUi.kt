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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyAndRoutingCard(viewModel: AuraViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val localModels by viewModel.localModelCandidates.collectAsStateWithLifecycle()
    val routedInfo by viewModel.lastRoutedProviderInfo.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy-First & Hybrid Cost Router", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Active Receiver: $routedInfo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Text("Intelligence Mode", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("HYBRID" to "Hybrid (Auto)", "LOCAL_ONLY" to "Local-Only", "CLOUD_ONLY" to "Cloud-Only").forEach { (id, label) ->
                        FilterChip(
                            selected = prefs.intelligenceMode == id,
                            onClick = { viewModel.setIntelligenceMode(id) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strict Free-Only Mode", style = MaterialTheme.typography.bodyMedium)
                        Text("Blocks silent paid-tier upgrades (e.g. Gemini 4 Argon)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefs.freeOnlyMode, onCheckedChange = { viewModel.setFreeOnlyMode(it) })
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Redact Passwords, OTPs, Tokens & Cards", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.redactSensitiveData,
                        onCheckedChange = { viewModel.setPrivacySettings(it, prefs.minimizeScreenSharing, prefs.dataRetentionDays) }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Minimize External Screen Payload", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.minimizeScreenSharing,
                        onCheckedChange = { viewModel.setPrivacySettings(prefs.redactSensitiveData, it, prefs.dataRetentionDays) }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Daily Token Cap: ${prefs.dailyTokenLimit} • Retention: ${prefs.dataRetentionDays}d", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50_000, 150_000, 500_000).forEach { cap ->
                        FilterChip(selected = prefs.dailyTokenLimit == cap, onClick = { viewModel.setDailyTokenLimit(cap) }, label = { Text("${cap / 1000}K tok") })
                    }
                    listOf(7, 30, 90).forEach { d ->
                        FilterChip(selected = prefs.dataRetentionDays == d, onClick = { viewModel.setPrivacySettings(prefs.redactSensitiveData, prefs.minimizeScreenSharing, d) }, label = { Text("${d}d ret") })
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("On-Device Gemma 4 & Local AI Evaluation", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                localModels.forEach { m ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${m.runtime} • Min ${m.minRamMb}MB RAM • ${m.diskSizeGb}GB • ${m.evaluationNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            text = if (m.compatibleWithDevice) "ELIGIBLE" else "BLOCKED",
                            accent = if (m.compatibleWithDevice) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BenchmarkAndObservabilityTab(viewModel: AuraViewModel) {
    val runs by viewModel.benchmarkRuns.collectAsStateWithLifecycle()
    val health by viewModel.deviceHealth.collectAsStateWithLifecycle()
    val healing by viewModel.healingHistory.collectAsStateWithLifecycle()
    val crashes by viewModel.recentCrashCount.collectAsStateWithLifecycle()
    val audits by viewModel.auditLogs.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val accessibilityOk by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val toolHealth by viewModel.toolHealthDashboard.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var logSearch by remember { mutableStateOf("") }

    val filteredLogs = if (logSearch.isBlank()) audits else audits.filter {
        it.action.contains(logSearch, true) || it.actor.contains(logSearch, true) || it.details.contains(logSearch, true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 24h MCP Tool Health Summary Shortcut Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("observability_tool_health_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("MCP Tool Health (24h)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(
                            text = "${toolHealth.healthyCount}/${toolHealth.totalRegisteredTools} Healthy",
                            accent = if (toolHealth.faultyCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = "24h Runs: ${toolHealth.totalCalls24h} • Success: ${toolHealth.overallSuccessRatePercent?.let { "${it.toInt()}%" } ?: "—"} • Avg Latency: ${toolHealth.avgLatencyMs24h}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.openToolHealthDashboard() },
                        modifier = Modifier.fillMaxWidth().testTag("open_tool_health_dashboard_btn")
                    ) {
                        Text("Open 24h Tool Health Dashboard")
                    }
                }
            }
        }

        // ARTEMIS Benchmark Lab Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ARTEMIS Benchmark & Regression Lab", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tests app resolution, screen confidence targeting, multilingual EN/UR, Level 3/4 guardrails, prompt injection, secret redaction, free-only routing, and self-healing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { viewModel.runBenchmarkSuite() },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("run_benchmark_btn")
                    ) { Text("Run 12-Case Verification Suite") }
                }
            }
        }

        items(runs.take(5), key = { it.id }) { run ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${run.suiteName} (${run.buildTag})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(
                            text = "${run.scorePercent}% (${run.passedCases}/${run.totalCases})",
                            accent = if (run.regressionDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = if (run.regressionDetected) "REGRESSION DETECTED vs ${run.previousBuildScore}%" else "Baseline: ${run.previousBuildScore?.let { "$it%" } ?: "Initial Baseline"} • Release Gate Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (run.regressionDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Live System Observability Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live System Observability", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val netOnline = remember(health) { viewModel.selfHealing.isNetworkOnline() }
                    Text("Network: ${if (netOnline) "ONLINE" else "OFFLINE"} • Accessibility: ${if (accessibilityOk) "CONNECTED" else "DISCONNECTED"}", style = MaterialTheme.typography.bodySmall)
                    Text("RAM: ${health.availableRamMb}/${health.totalRamMb}MB • Storage: ${"%.2f".format(health.freeStorageGb)}GB • Battery: ${health.batteryPercent}%", style = MaterialTheme.typography.bodySmall)
                    Text("Agents Online: ${agents.size}/24 • Recent Crashes: $crashes • Healing Events: ${healing.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.pruneMemoryCache() }) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prune Cache")
                        }
                        Button(
                            onClick = { viewModel.exportDiagnosticReportJson() },
                            modifier = Modifier.testTag("export_diagnostics_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSON Report")
                        }
                    }
                }
            }
        }

        if (healing.isNotEmpty()) {
            item { Text("Self-Healing Recovery Events (${healing.size})", style = MaterialTheme.typography.titleSmall) }
            items(healing.take(8)) { ev ->
                Text(
                    "[${ev.category.name}] ${ev.actionName}: ${ev.recoveryStrategy}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ev.succeeded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            OutlinedTextField(
                value = logSearch,
                onValueChange = { logSearch = it },
                label = { Text("Search System & Security Logs") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filteredLogs.take(20), key = { it.id }) { log ->
            Text(
                text = "[L${log.safetyLevel} ${log.outcome}] ${log.actor} • ${log.action}: ${log.details}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
