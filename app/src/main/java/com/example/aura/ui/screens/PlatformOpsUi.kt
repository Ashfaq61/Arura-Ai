package com.example.aura.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.sync.SyncConflictStrategy
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutonomyAndPermissionsTab(viewModel: AuraViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val permReport by viewModel.expansion.permissionHealthReport.collectAsStateWithLifecycle()
    val policy = prefs.autonomyPolicy

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.expansion.refreshPermissionHealth() }

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
                        Icon(Icons.Filled.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("User-Controlled Autonomy Policy", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Max Autonomous Steps per Run: ${policy.maxAutonomousSteps}", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 25, 50, 100).forEach { cap ->
                            FilterChip(selected = policy.maxAutonomousSteps == cap, onClick = {
                                viewModel.expansion.updateAutonomyPolicy(policy.copy(maxAutonomousSteps = cap))
                            }, label = { Text("$cap steps") })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Max Scheduled Runs / Hour: ${policy.maxScheduledRunsPerHour}", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 6, 12, 24).forEach { hr ->
                            FilterChip(selected = policy.maxScheduledRunsPerHour == hr, onClick = {
                                viewModel.expansion.updateAutonomyPolicy(policy.copy(maxScheduledRunsPerHour = hr))
                            }, label = { Text("$hr/hr") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Pause All Scheduled & Event Routines", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = policy.scheduledTasksPaused, onCheckedChange = {
                            viewModel.expansion.updateAutonomyPolicy(policy.copy(scheduledTasksPaused = it))
                        })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow External Cloud API Calls", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = policy.externalApiMasterEnabled, onCheckedChange = {
                            viewModel.expansion.updateAutonomyPolicy(policy.copy(externalApiMasterEnabled = it))
                        })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Require Approval: Messages & Calls", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = policy.confirmBeforeMessages, onCheckedChange = {
                            viewModel.expansion.updateAutonomyPolicy(policy.copy(confirmBeforeMessages = it, confirmBeforeCalls = it))
                        })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Require Approval: GitHub Push & Cloud Sync", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = policy.confirmBeforeGitHubPush, onCheckedChange = {
                            viewModel.expansion.updateAutonomyPolicy(policy.copy(confirmBeforeGitHubPush = it, confirmBeforeCloudSync = it))
                        })
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
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Permission Health Monitor", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(text = "${permReport.scorePercent}% (${permReport.grantedCount}/${permReport.totalCount})", accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { viewModel.expansion.refreshPermissionHealth() }) { Text("Re-Audit Permissions") }
                }
            }
        }

        items(permReport.items, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusPill(
                            text = if (item.isGranted) "GRANTED" else item.importance,
                            accent = if (item.isGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(item.whyNeeded, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!item.isGranted) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            if (item.runtimePermissionName != null) runtimePermLauncher.launch(item.runtimePermissionName)
                            else viewModel.expansion.openPermissionSettings(item)
                        }) { Text("Grant / Configure") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupAndMultiDeviceSyncTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val syncDevices by viewModel.expansion.syncDevices.collectAsStateWithLifecycle()
    val lastBackupJson by viewModel.expansion.lastBackupJson.collectAsStateWithLifecycle()
    val lastSyncMsg by viewModel.expansion.lastSyncMessage.collectAsStateWithLifecycle()

    var passphrase by remember { mutableStateOf("") }
    var restorePayload by remember { mutableStateOf("") }
    var companionUrl by remember(prefs.cloudCompanionUrl) { mutableStateOf(prefs.cloudCompanionUrl) }
    var companionToken by remember { mutableStateOf(viewModel.expansion.gateway.getCompanionBearerToken()) }
    var deviceName by remember(prefs.deviceDisplayName) { mutableStateOf(prefs.deviceDisplayName) }
    var conflictStrat by remember(prefs.syncConflictStrategy) { mutableStateOf(prefs.syncConflictStrategy) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && lastBackupJson.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(lastBackupJson.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                restorePayload = text
            }
        }
    }

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
                        Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encrypted Backup & Restore (AES-256-GCM)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("PBKDF2 passphrase encryption. API keys, GitHub PATs & vault secrets are strictly excluded.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption Passphrase (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { viewModel.expansion.createEncryptedBackup(passphrase) }) { Text("Create Encrypted Backup") }
                        if (lastBackupJson.isNotBlank()) {
                            OutlinedButton(onClick = { saveFileLauncher.launch("aura_backup.aurabak") }) { Text("Save .aurabak File") }
                            OutlinedButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lastBackupJson)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Encrypted AURA Backup"))
                            }) { Text("Share") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePayload,
                        onValueChange = { restorePayload = it },
                        label = { Text("Paste Encrypted Backup / Sync Envelope JSON") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openFileLauncher.launch(arrayOf("*/*")) }) { Text("Pick .aurabak") }
                        Button(
                            onClick = {
                                if (restorePayload.contains("aura_device_sync_v1")) {
                                    viewModel.expansion.importSyncBundle(restorePayload, passphrase)
                                } else {
                                    viewModel.expansion.restoreEncryptedBackup(passphrase, restorePayload)
                                }
                            },
                            enabled = restorePayload.isNotBlank() && passphrase.isNotBlank()
                        ) { Text("Decrypt & Restore") }
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Multi-Device Synchronization", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Uses your same passphrase to sync encrypted memories, routines, workflows & KG across authorized devices.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("This Device Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = companionUrl, onValueChange = { companionUrl = it }, label = { Text("Optional HTTPS Companion Backend URL") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = companionToken, onValueChange = { companionToken = it }, label = { Text("Optional Companion Bearer Token (Keystore Encrypted)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Conflict Policy", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SyncConflictStrategy.entries.forEach { s ->
                            FilterChip(selected = conflictStrat == s.name, onClick = { conflictStrat = s.name }, label = { Text(s.label) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { viewModel.expansion.saveCompanionBackendConfig(companionUrl, companionToken, deviceName, conflictStrat) }) { Text("Save Sync Config") }
                        Button(onClick = { viewModel.expansion.runGitHubEncryptedSync(passphrase) }) { Text("Sync via GitHub (Encrypted)") }
                        OutlinedButton(onClick = { viewModel.expansion.runCompanionSync(passphrase) }) { Text("Sync via Companion") }
                        OutlinedButton(onClick = { viewModel.expansion.generateLocalSyncBundle(passphrase) }) { Text("Create .aurasync") }
                    }
                    if (lastSyncMsg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(lastSyncMsg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        items(syncDevices, key = { it.deviceId }) { dev ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                        Text("${dev.deviceId} • ${dev.syncChannel} • ${dev.lastChecksum.ifBlank { "not synced" }}", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = dev.authorized, onCheckedChange = { viewModel.expansion.toggleDeviceAuthorization(dev) })
                }
            }
        }
    }
}

@Composable
fun PerformanceAndDiagnosticsTab(viewModel: AuraViewModel) {
    val perf by viewModel.expansion.performanceReport.collectAsStateWithLifecycle()
    val diag by viewModel.expansion.diagnosticsReport.collectAsStateWithLifecycle()
    val backend by viewModel.expansion.backendHealth.collectAsStateWithLifecycle()
    val dbStats by viewModel.expansion.dbStats.collectAsStateWithLifecycle()
    val crashes by viewModel.expansion.crashReports.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agent Performance Intelligence", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    perf?.let { p ->
                        Text("API Calls: ${p.totalApiCalls} (${p.apiSuccessRatePercent}% OK) • Avg Latency: ${p.avgApiLatencyMs}ms", style = MaterialTheme.typography.bodySmall)
                        Text("Tokens: ${p.totalEstimatedTokens} • Battery Impact: ${p.batteryImpactLabel}", style = MaterialTheme.typography.bodySmall)
                        Text("Top Error: ${p.topErrorPattern}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        p.recommendations.forEach { rec ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(rec.rationale, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { viewModel.expansion.applyOptimizationRecommendation(rec.actionCode) }) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.expansion.refreshPerformanceReport() }) { Text("Refresh Intelligence") }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Healing, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Automatic Diagnostics & Backend Gateway", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    diag?.let { d ->
                        Text("Status: ${d.overallStatus} • Network: ${if (d.networkOnline) "ONLINE" else "OFFLINE"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("RAM: ${d.ramSummary} • Storage: ${d.storageSummary} • Battery: ${d.batterySummary}", style = MaterialTheme.typography.bodySmall)
                        Text("WorkManager Active: ${d.pendingWorkCount} • Failed: ${d.failedWorkCount} • Crashes: ${d.unresolvedCrashCount}", style = MaterialTheme.typography.labelSmall)
                        d.issuesDetected.forEach { iss -> Text("• $iss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Gateway: ${backend.lastCheckMessage} (${backend.requestsThisMinute}/${backend.maxRequestsPerMinute} req/min)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    dbStats?.let { s ->
                        Text("Room Tables — Memories:${s.memoryCount}, Routines:${s.routineCount}, Workflows:${s.workflowCount}, KG:${s.knowledgeNodeCount}, Tasks:${s.productivityCount}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.expansion.runSafeDiagnosticsRecovery() }) { Text("Run Safe Auto-Recovery") }
                        OutlinedButton(onClick = {
                            viewModel.expansion.refreshDiagnostics()
                            viewModel.expansion.checkBackendHealth()
                        }) { Text("Probe Gateway") }
                    }
                }
            }
        }

        if (crashes.isNotEmpty()) {
            item { Text("Recorded Crash Reports (${crashes.size})", style = MaterialTheme.typography.titleSmall) }
            items(crashes.take(5), key = { it.id }) { cr ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${cr.exceptionType}: ${cr.message}", style = MaterialTheme.typography.titleSmall)
                        Text("Thread: ${cr.threadName} • Resolved: ${cr.resolved}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageQaAndReleaseTab(viewModel: AuraViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val qaReport by viewModel.expansion.qaReport.collectAsStateWithLifecycle()
    val releaseReport by viewModel.expansion.releaseReport.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accessibility & Multi-Language QA", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Preview Urdu RTL Layout Mirroring", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = prefs.forceUrduRtlPreview, onCheckedChange = { viewModel.expansion.toggleUrduRtlPreview(it) })
                    }
                    Button(onClick = { viewModel.expansion.runMultilingualQaAudit() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Run EN / اردو RTL / Accessibility QA")
                    }
                    qaReport?.let { qa ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Passed ${qa.passedCount}/${qa.totalCount} checks • Voice: ${qa.activeVoiceEngine}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        qa.checks.forEach { c ->
                            Text("${if (c.passed) "✓" else "!"} ${c.checkName}: ${c.details}", style = MaterialTheme.typography.labelSmall)
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Final 20-Module Release Validation", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.expansion.runReleaseValidation() },
                        modifier = Modifier.fillMaxWidth().testTag("run_release_validation_btn")
                    ) { Text("Run Full 20-Module Release Validation") }
                    releaseReport?.let { rep ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${rep.versionSummary}: ${rep.activeCount} ACTIVE, ${rep.partialCount} PARTIAL (${rep.totalChecked} total)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        releaseReport?.let { rep ->
            items(rep.items, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(item.capability, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = item.status,
                                accent = if (item.status == "ACTIVE") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
