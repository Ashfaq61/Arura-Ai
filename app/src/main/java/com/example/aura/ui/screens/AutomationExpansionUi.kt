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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun SmartRoutinesTab(viewModel: AuraViewModel) {
    val routines by viewModel.expansion.routines.collectAsStateWithLifecycle()
    val lastReport by viewModel.expansion.lastRoutineReport.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("VOICE") }
    var triggerConfig by remember { mutableStateOf("evening focus") }
    var stepType by remember { mutableStateOf("SPEAK") }
    var stepParam by remember { mutableStateOf("Evening focus mode enabled.") }
    var scheduleHours by remember { mutableIntStateOf(24) }

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
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Smart Routines Engine", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Create voice, scheduled (WorkManager), or Android event-driven multi-step routines gated by Autonomy Policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Routine Name") },
                        modifier = Modifier.fillMaxWidth().testTag("routine_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trigger Type", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("VOICE", "SCHEDULE", "BATTERY_LOW", "CHARGING", "WIFI_CONNECTED", "HEADSET_PLUGGED", "MANUAL").forEach { t ->
                            FilterChip(selected = triggerType == t, onClick = { triggerType = t }, label = { Text(t) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = triggerConfig,
                        onValueChange = { triggerConfig = it },
                        label = { Text("Trigger Phrase / Config") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (triggerType == "SCHEDULE") {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 6, 12, 24).forEach { hrs ->
                                FilterChip(selected = scheduleHours == hrs, onClick = { scheduleHours = hrs }, label = { Text("${hrs}h") })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Step Action", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SPEAK", "HEALTH_CHECK", "VOLUME_DELTA", "CREATE_REMINDER", "OPEN_APP", "TOGGLE_TORCH").forEach { st ->
                            FilterChip(selected = stepType == st, onClick = { stepType = st }, label = { Text(st) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = stepParam,
                        onValueChange = { stepParam = it },
                        label = { Text("Step Parameter (message, delta, app name, on/off)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.expansion.saveRoutine(name, triggerType, triggerConfig, stepType, stepParam, if (triggerType == "SCHEDULE") scheduleHours else 0)
                                name = ""
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("save_routine_btn")
                    ) { Text("Create & Arm Routine") }
                }
            }
        }

        lastReport?.let { rep ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Last Routine Run: ${rep.routineName}", style = MaterialTheme.typography.titleSmall)
                        rep.stepLogs.forEach { log ->
                            Text("• $log", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        items(routines, key = { it.id }) { routine ->
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
                            Text(routine.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${routine.triggerType} • ${routine.triggerConfig} • Runs: ${routine.runCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(checked = routine.enabled, onCheckedChange = { viewModel.expansion.toggleRoutineEnabled(routine) })
                    }
                    Text("Outcome: ${routine.lastOutcome}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.expansion.runRoutineNow(routine) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run routine")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Now")
                        }
                        IconButton(onClick = { viewModel.expansion.deleteRoutine(routine.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualWorkflowBuilderTab(viewModel: AuraViewModel) {
    val workflows by viewModel.expansion.visualWorkflows.collectAsStateWithLifecycle()
    val lastReport by viewModel.expansion.lastWorkflowReport.collectAsStateWithLifecycle()

    var wfName by remember { mutableStateOf("") }
    var wfDesc by remember { mutableStateOf("Checks condition then executes automated step") }
    var actionType by remember { mutableStateOf("HEALTH_CHECK") }
    var actionParam by remember { mutableStateOf("ram_battery") }
    var addIfCondition by remember { mutableStateOf(true) }

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
                        Text("Visual Workflow Builder", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Build DAG workflows with triggers, IF/ELSE branching, variables ({{var}}), structural validation, and step execution logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = wfName,
                        onValueChange = { wfName = it },
                        label = { Text("Workflow Name") },
                        modifier = Modifier.fillMaxWidth().testTag("workflow_name_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = wfDesc,
                        onValueChange = { wfDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("HEALTH_CHECK", "VOLUME_DELTA", "OPEN_APP", "WEB_SEARCH", "LOG_NOTE").forEach { act ->
                            FilterChip(selected = actionType == act, onClick = { actionType = act }, label = { Text(act) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = actionParam,
                        onValueChange = { actionParam = it },
                        label = { Text("Action Parameter") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Include IF Battery < 20% Branch Node", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = addIfCondition, onCheckedChange = { addIfCondition = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (wfName.isNotBlank()) {
                                viewModel.expansion.saveAndValidateWorkflow(wfName, wfDesc, actionType, actionParam, addIfCondition)
                                wfName = ""
                            }
                        },
                        enabled = wfName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("create_workflow_btn")
                    ) { Text("Build & Validate Workflow") }
                }
            }
        }

        lastReport?.let { rep ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Live Execution Log (Workflow #${rep.workflowId})", style = MaterialTheme.typography.titleSmall)
                        rep.logs.forEach { l -> Text(l, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        items(workflows, key = { it.id }) { wf ->
            val nodes = remember(wf.nodesJson) { viewModel.expansion.visualWorkflowEngine.parseNodes(wf.nodesJson) }
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
                        Text(wf.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(
                            text = if (wf.lastRunSuccess) "READY" else "WARN",
                            accent = if (wf.lastRunSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(wf.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nodes.forEach { n ->
                            StatusPill(text = "${n.id}:${n.type}", accent = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Validation: ${wf.lastValidationResult}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (wf.lastRunLog.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(wf.lastRunLog, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { viewModel.expansion.runVisualWorkflow(wf) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Execute workflow")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Execute Live")
                        }
                        IconButton(onClick = { viewModel.expansion.deleteVisualWorkflow(wf.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete workflow", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutomationRecipesTab(viewModel: AuraViewModel) {
    val recipes by viewModel.expansion.recipes.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Automation Recipe Library (${recipes.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Versioned reusable recipes with compatibility verification, pass/fail tracking, and 1-tap Workflow Builder export.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(recipes, key = { it.id }) { recipe ->
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
                        Text("${recipe.name} (v${recipe.version})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(
                            text = recipe.lastTestStatus,
                            accent = if (recipe.lastTestStatus == "PASSED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                    Text("${recipe.category} • Target: ${recipe.targetPackage} (>= ${recipe.minAppVersion})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(recipe.description, style = MaterialTheme.typography.bodySmall)
                    Text("History: ${recipe.successCount} passed / ${recipe.failureCount} failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.expansion.runAndTestRecipe(recipe) }) {
                            Text("Test & Run")
                        }
                        OutlinedButton(onClick = { viewModel.expansion.sendRecipeToWorkflowBuilder(recipe) }) {
                            Text("Open in Builder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCompatibilityTab(viewModel: AuraViewModel) {
    val profiles by viewModel.expansion.compatProfiles.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Compatibility Engine", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tracks installed versions via PackageManager and screen hash drift via AccessibilityBridge, auto-revalidating stored Skill Procedures.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.expansion.scanAppCompatibility() },
                        modifier = Modifier.fillMaxWidth().testTag("scan_app_compat_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Installed Apps & Repair Skills")
                    }
                }
            }
        }

        items(profiles, key = { it.packageName }) { p ->
            val accent = when (p.compatibilityStatus) {
                "COMPATIBLE" -> MaterialTheme.colorScheme.tertiary
                "VERSION_CHANGED", "LAYOUT_DRIFT" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
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
                        Text(p.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = p.compatibilityStatus, accent = accent)
                    }
                    Text("${p.packageName} • Installed: ${p.installedVersionName} (${p.installedVersionCode})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Actions: ${p.supportedActionsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.driftNotes, style = MaterialTheme.typography.bodySmall)
                    if (p.compatibilityStatus == "VERSION_CHANGED" || p.compatibilityStatus == "LAYOUT_DRIFT") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { viewModel.expansion.acknowledgeAppBaseline(p.packageName) }) {
                            Text("Confirm Baseline")
                        }
                    }
                }
            }
        }
    }
}
