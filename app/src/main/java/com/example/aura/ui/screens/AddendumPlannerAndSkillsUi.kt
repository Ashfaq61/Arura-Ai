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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongHorizonPlannerTab(viewModel: AuraViewModel) {
    val tasks by viewModel.longHorizonTasks.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var goalInput by remember { mutableStateOf("") }
    var stepTarget by remember { mutableIntStateOf(50) }

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
                        Icon(Icons.Filled.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Long-Horizon Task Planner (10–100 Steps)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Decomposes multi-app tasks into milestones with persistent Room checkpoints, loop detection, replanning, and Pause/Resume/Cancel controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(25, 50, 75, 100).forEach { count ->
                            FilterChip(
                                selected = stepTarget == count,
                                onClick = { stepTarget = count },
                                label = { Text("$count Steps") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        label = { Text("Multi-screen workflow goal") },
                        placeholder = { Text("e.g., Audit weekly expenses across banking & notes") },
                        modifier = Modifier.fillMaxWidth().testTag("long_horizon_goal_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (goalInput.isNotBlank()) {
                                viewModel.createLongHorizonPlan(goalInput, stepTarget)
                                goalInput = ""
                            }
                        },
                        enabled = !isBusy && goalInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("create_long_horizon_btn")
                    ) { Text("Generate $stepTarget-Step Milestone Plan") }
                }
            }
        }

        items(tasks, key = { it.id }) { task ->
            val progress = if (task.totalSteps > 0) task.completedSteps.toFloat() / task.totalSteps else 0f
            val milestones = remember(task.milestonesJson) { viewModel.longHorizonEngine.decodeMilestones(task.milestonesJson) }
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
                        Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = task.status, accent = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Progress: ${task.completedSteps}/${task.totalSteps} steps • Loops: ${task.loopDetectionCount} • Replans: ${task.replanCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    milestones.forEach { m ->
                        Text("• M${m.index} [${m.status}] ${m.title} (${m.steps.size} steps)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.finalReport.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(task.finalReport, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { viewModel.advanceLongHorizonTask(task) },
                            enabled = task.status == "RUNNING"
                        ) { Text("Advance Step") }
                        OutlinedButton(onClick = { viewModel.pauseOrResumeLongHorizonTask(task) }) {
                            Text(if (task.status == "PAUSED") "Resume" else "Pause")
                        }
                        OutlinedButton(onClick = { viewModel.cancelLongHorizonTask(task) }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillMemoryTab(viewModel: AuraViewModel) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("com.android.settings") }
    var ver by remember { mutableStateOf("1.0") }
    var stepsText by remember { mutableStateOf("Open Settings\nVerify Battery section\nRead status summary") }

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
                        Text("Procedural Skill Memory Engine", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Captures repeatable multi-step skills with app version & layout fingerprint, tests before reuse, detects UI drift, and blocks untrusted screen injection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Skill Name") }, modifier = Modifier.fillMaxWidth().testTag("skill_name_input"))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Target Package") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = ver, onValueChange = { ver = it }, label = { Text("App Version") }, modifier = Modifier.width(110.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = stepsText,
                        onValueChange = { stepsText = it },
                        label = { Text("Procedure Steps (one per line)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.saveSkill(name, "Verified procedural skill", pkg, ver, stepsText)
                                name = ""
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("save_skill_btn")
                    ) { Text("Verify & Save Procedural Skill") }
                }
            }
        }

        items(skills, key = { it.id }) { skill ->
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
                        Text("${skill.name} (v${skill.version})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = skill.testStatus, accent = if (skill.testStatus == "PASSED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                    }
                    Text("Pkg: ${skill.targetPackage} (App v${skill.supportedAppVersion}) • Layout: ${skill.layoutFingerprint} • Runs: ${skill.usageCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(skill.stepsJson, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.testOrRepairSkill(skill) }) {
                            Text(if (skill.testStatus == "NEEDS_REPAIR") "Repair & Retest" else "Test Skill Reuse")
                        }
                        IconButton(onClick = { viewModel.deleteSkill(skill.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete skill", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduledTasksTab(viewModel: AuraViewModel) {
    val schedules by viewModel.scheduledTasks.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BRIEFING") }
    var intervalHours by remember { mutableIntStateOf(12) }

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
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autonomous WorkManager Scheduler", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BRIEFING", "GITHUB_WATCH", "HEALTH_CHECK", "REMINDER").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 6, 12, 24).forEach { h ->
                            FilterChip(selected = intervalHours == h, onClick = { intervalHours = h }, label = { Text("Every ${h}h") })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Schedule Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Topic / Target Query") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.createScheduledTask(name, type, query, intervalHours)
                                name = ""; query = ""
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("create_schedule_btn")
                    ) { Text("Create Autonomous Schedule") }
                }
            }
        }

        items(schedules, key = { it.id }) { sched ->
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
                            Text(sched.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${sched.taskType} • Every ${sched.intervalHours}h • ${sched.targetQuery}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(checked = sched.enabled, onCheckedChange = { viewModel.toggleScheduledTask(sched) })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Last Outcome: ${sched.lastOutcome}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.runScheduledTaskNow(sched) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Now")
                        }
                        IconButton(onClick = { viewModel.deleteScheduledTask(sched) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignedSelfModSection(viewModel: AuraViewModel) {
    val records by viewModel.selfModRecords.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var targetModule by remember { mutableStateOf("research") }
    var proposal by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Safe 13-Stage Self-Modification Pipeline", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "DISCOVER → RESEARCH → DESIGN → GENERATE → REVIEW → SECURITY_SCAN → BUILD → TEST → BENCHMARK → APPROVE → DEPLOY → MONITOR → ROLLBACK. Automatically blocks PermissionGuard tampering & benchmark regressions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = targetModule, onValueChange = { targetModule = it }, label = { Text("Target Module (e.g., voice, research, automation)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = proposal, onValueChange = { proposal = it }, label = { Text("Proposed Upgrade Summary") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (proposal.isNotBlank()) {
                        viewModel.proposeSelfMod(targetModule, proposal)
                        proposal = ""
                    }
                },
                enabled = !isBusy && proposal.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("propose_self_mod_btn")
            ) { Text("Run Security Scan, Benchmark & Sign Proposal") }

            if (records.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                records.take(5).forEach { rec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("${rec.targetModule} • ${rec.stage}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                StatusPill(text = rec.status, accent = MaterialTheme.colorScheme.primary)
                            }
                            Text(rec.proposalSummary, style = MaterialTheme.typography.bodySmall)
                            Text("SHA-256: ${rec.signatureHash.take(24)}... • ${rec.benchmarkDelta}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(rec.securityScanResult, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (rec.status == "AWAITING_APPROVAL") {
                                    Button(onClick = { viewModel.approveSelfMod(rec) }) { Text("Approve & Deploy") }
                                }
                                if (rec.status == "DEPLOYED_MONITORING" || rec.status == "AWAITING_APPROVAL") {
                                    OutlinedButton(onClick = { viewModel.rollbackSelfMod(rec) }) { Text("Rollback") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
