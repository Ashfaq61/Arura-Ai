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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
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
import com.example.aura.memory.ConnectedAgentNodeEntity
import com.example.aura.network.AgentConnectionStatus
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val netTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalAgentNetworkTab(
    viewModel: AuraViewModel,
    onOpenAgentChatHistory: (String) -> Unit
) {
    val ctrl = viewModel.globalNetworkController
    val nodes by ctrl.agentNodes.collectAsStateWithLifecycle()
    val queued by ctrl.queuedMessages.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isBusy by ctrl.isNetworkBusy.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var authTargetNode by remember { mutableStateOf<ConnectedAgentNodeEntity?>(null) }
    var chatTargetNode by remember { mutableStateOf<ConnectedAgentNodeEntity?>(null) }

    var hbMinutes by remember(prefs.heartbeatIntervalMinutes) { mutableIntStateOf(prefs.heartbeatIntervalMinutes) }
    var bgSync by remember(prefs.backgroundNetworkSyncEnabled) { mutableStateOf(prefs.backgroundNetworkSyncEnabled) }
    var notifFreq by remember(prefs.discussionNotifFrequency) { mutableStateOf(prefs.discussionNotifFrequency) }
    var notifPriority by remember(prefs.discussionNotifMinPriority) { mutableStateOf(prefs.discussionNotifMinPriority) }
    var autoCouncil by remember(prefs.autoCouncilOnNewDiscovery) { mutableStateOf(prefs.autoCouncilOnNewDiscovery) }

    val connectedCount = nodes.count { it.connectionStatus == AgentConnectionStatus.CONNECTED }
    val activeQueueCount = queued.count { it.status == "QUEUED" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("global_agent_network_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hub, contentDescription = "Global Agent Network", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Always-Connected Agent Network ($connectedCount/${nodes.size} Connected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connection statuses reflect real runtime probes and Keystore auth checks. Unconfigured external nodes remain AUTH_REQUIRED or DISCONNECTED. Periodic sync uses WorkManager with network constraints (Android Doze may defer background windows; connect a companion backend for continuous 24/7 webhooks).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { ctrl.runHeartbeatAll() },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("network_heartbeat_all_btn")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Heartbeat All")
                        }
                        OutlinedButton(
                            onClick = { ctrl.runLiveTechWatchAndResume() },
                            enabled = !isBusy,
                            modifier = Modifier.testTag("network_sync_now_btn")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flush Queue & Tech Watch")
                        }
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("network_add_agent_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add External Agent")
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
                    Text("Heartbeat & Discussion Notification Policy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("WorkManager Background Sync", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = bgSync,
                            onCheckedChange = { bgSync = it },
                            modifier = Modifier.testTag("network_bg_sync_switch")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto-Convene Council on New Discovery", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = autoCouncil,
                            onCheckedChange = { autoCouncil = it },
                            modifier = Modifier.testTag("network_auto_council_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Heartbeat Interval", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60).forEach { mins ->
                            FilterChip(
                                selected = hbMinutes == mins,
                                onClick = { hbMinutes = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Notification Frequency", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("REALTIME", "HOURLY_DIGEST", "DAILY_DIGEST", "CRITICAL_ONLY", "MUTED").forEach { freq ->
                            FilterChip(
                                selected = notifFreq == freq,
                                onClick = { notifFreq = freq },
                                label = { Text(freq) }
                            )
                        }
                    }
                    Text("Minimum Alert Priority", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LOW", "NORMAL", "HIGH", "CRITICAL").forEach { prio ->
                            FilterChip(
                                selected = notifPriority == prio,
                                onClick = { notifPriority = prio },
                                label = { Text(prio) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            ctrl.updateNetworkAndNotificationSettings(
                                heartbeatMinutes = hbMinutes,
                                backgroundSync = bgSync,
                                notifFreq = notifFreq,
                                notifMinPriority = notifPriority,
                                autoCouncil = autoCouncil
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("network_save_policy_btn")
                    ) {
                        Text("Save Network & Notification Policy")
                    }
                }
            }
        }

        if (activeQueueCount > 0 || queued.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Offline / Rate-Limit Message Queue ($activeQueueCount pending, ${queued.size} total)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        queued.take(4).forEach { q ->
                            Text(
                                "[${q.status}] -> ${q.targetAgentName}: ${q.payload.take(65)} (retries=${q.retryCount})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        items(nodes, key = { it.nodeId }) { node ->
            val statusColor = when (node.connectionStatus) {
                AgentConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiary
                AgentConnectionStatus.CONNECTING, AgentConnectionStatus.BUSY -> MaterialTheme.colorScheme.primary
                AgentConnectionStatus.RATE_LIMITED, AgentConnectionStatus.AUTH_REQUIRED -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
            }
            val trustColor = when (node.trustStatus) {
                "VERIFIED_INTERNAL", "AUTHENTICATED_EXTERNAL" -> MaterialTheme.colorScheme.tertiary
                "BLOCKED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_node_${node.nodeId}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(node.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = node.connectionStatus, accent = statusColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(text = node.category, accent = MaterialTheme.colorScheme.primary)
                        StatusPill(text = node.trustStatus, accent = trustColor)
                        if (node.modelName.isNotBlank()) {
                            StatusPill(text = node.modelName, accent = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Platform: ${node.providerOrPlatform}" + if (node.endpointUrl.isNotBlank()) " • ${node.endpointUrl}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hbText = node.lastHeartbeatAt?.let { "${netTimeFmt.format(Date(it))} (${node.lastLatencyMs}ms)" } ?: "Never checked"
                    Text("Last Heartbeat: $hbText", style = MaterialTheme.typography.labelSmall)
                    if (node.lastErrorMessage.isNotBlank()) {
                        Text(
                            text = "Diagnostic: ${node.lastErrorMessage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (node.connectionStatus == AgentConnectionStatus.CONNECTED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { ctrl.runHeartbeatSingle(node.nodeId) }) {
                            Text("Heartbeat")
                        }
                        if (!node.isBuiltIn || node.endpointUrl.isNotBlank()) {
                            OutlinedButton(onClick = { authTargetNode = node }) {
                                Icon(Icons.Filled.Key, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auth Token")
                            }
                        }
                        Button(onClick = { chatTargetNode = node }) {
                            Icon(Icons.Filled.Forum, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Message")
                        }
                        TextButton(onClick = { onOpenAgentChatHistory(node.name) }) {
                            Text("Chat History")
                        }
                        OutlinedButton(onClick = { ctrl.disconnectAgent(node.nodeId) }) {
                            Text("Disconnect")
                        }
                        if (!node.isBuiltIn) {
                            IconButton(onClick = { ctrl.removeAgent(node.nodeId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove ${node.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExternalAgentDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, cat, prov, model, url, token, webhook, caps ->
                ctrl.addExternalAgent(name, cat, prov, model, url, token, webhook, caps)
                showAddDialog = false
            }
        )
    }

    authTargetNode?.let { node ->
        var tokenInput by remember(node.nodeId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { authTargetNode = null },
            title = { Text("Authenticate ${node.name}") },
            text = {
                Column {
                    Text("Token is stored encrypted inside Android Keystore and scrubbed from all agent logs.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("API Key / Bearer Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ctrl.authenticateAgent(node.nodeId, tokenInput)
                    authTargetNode = null
                }) { Text("Save & Verify") }
            },
            dismissButton = {
                TextButton(onClick = { authTargetNode = null }) { Text("Cancel") }
            }
        )
    }

    chatTargetNode?.let { node ->
        var topicInput by remember(node.nodeId) { mutableStateOf("AI Tech Exchange") }
        var msgInput by remember(node.nodeId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chatTargetNode = null },
            title = { Text("Message ${node.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = msgInput,
                        onValueChange = { msgInput = it },
                        label = { Text("Question or task (auto-queued if offline)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ctrl.sendDirectAgentMessage(node.nodeId, topicInput, msgInput)
                        chatTargetNode = null
                    },
                    enabled = msgInput.isNotBlank()
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { chatTargetNode = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExternalAgentDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("MCP") }
    var provider by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }
    var webhook by remember { mutableStateOf("") }
    var caps by remember { mutableStateOf("research,summarize") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect External AI Agent") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Agent Name") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("MCP", "A2A", "PLATFORM", "PROVIDER", "GITHUB", "HUGGING_FACE", "CODING", "RESEARCH").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Platform / Provider") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS Endpoint URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Bearer Token (stored in Keystore)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = webhook, onValueChange = { webhook = it }, label = { Text("Webhook Callback URL (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = caps, onValueChange = { caps = it }, label = { Text("Allowed Capabilities (CSV)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, category, provider, model, url, token, webhook, caps) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) { Text("Register & Probe") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveDiscussionMonitorTab(
    viewModel: AuraViewModel,
    onOpenSessionDetail: (String) -> Unit
) {
    val ctrl = viewModel.globalNetworkController
    val sessions by ctrl.discussionSessions.collectAsStateWithLifecycle()
    val notifications by ctrl.notifications.collectAsStateWithLifecycle()
    val isBusy by ctrl.isNetworkBusy.collectAsStateWithLifecycle()

    val activeCount = sessions.count { it.status == "ACTIVE" || it.status == "WAITING_RESPONSE" }
    val finishedCount = sessions.count { it.status == "FINISHED" }
    val unreadNotifs = notifications.count { !it.read }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_discussion_monitor_list")
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sensors, contentDescription = "Live Monitor", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Discussion Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(text = "Active/Waiting: $activeCount", accent = MaterialTheme.colorScheme.primary)
                        StatusPill(text = "Finished: $finishedCount", accent = MaterialTheme.colorScheme.tertiary)
                        StatusPill(text = "Unread Alerts: $unreadNotifs", accent = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { ctrl.runLiveTechWatchAndResume() },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monitor_resume_sync_btn")
                    ) {
                        Text("Resume Waiting Discussions & Run Tech Watch")
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
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discussion & Discovery Alerts (${notifications.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (unreadNotifs > 0) {
                            TextButton(onClick = { ctrl.markAllNotificationsRead() }) {
                                Text("Mark All Read")
                            }
                        }
                    }
                    if (notifications.isEmpty()) {
                        Text("No alerts emitted yet. Alerts trigger on new models, discoveries, approvals, or security blocks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        notifications.take(8).forEach { ev ->
                            val accent = when (ev.priority) {
                                "CRITICAL" -> MaterialTheme.colorScheme.error
                                "HIGH" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                StatusPill(text = ev.priority, accent = accent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ev.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(ev.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!ev.read) {
                                    TextButton(onClick = { ctrl.markNotificationRead(ev.id) }) { Text("Read") }
                                }
                            }
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
                        "No live or historical council discussions yet. Start one in AI Council or run Tech Watch above.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(sessions, key = { it.sessionId }) { s ->
                Card(
                    onClick = { onOpenSessionDetail(s.sessionId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monitor_session_${s.sessionId}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(s.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = "${s.status} ${s.progressPercent}%",
                                accent = if (s.status == "FINISHED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (s.progressPercent.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Agents: ${s.participatingAgentsCsv}", style = MaterialTheme.typography.labelSmall)
                        Text("Updated: ${netTimeFmt.format(Date(s.updatedAt))} • Decision: ${s.decisionType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (s.proposedFeatureName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (s.linkedFeatureRequestId.isBlank()) {
                                    Button(onClick = { ctrl.addFeatureFromDiscussion(s.sessionId) }) {
                                        Text("ADD THIS FEATURE TO AURA")
                                    }
                                } else {
                                    StatusPill(text = "Pipeline: ${s.linkedFeatureRequestId}", accent = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
