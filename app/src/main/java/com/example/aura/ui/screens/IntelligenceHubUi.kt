package com.example.aura.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeGraphTab(viewModel: AuraViewModel) {
    val nodes by viewModel.expansion.knowledgeNodes.collectAsStateWithLifecycle()
    val edges by viewModel.expansion.knowledgeEdges.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("NOTE") }
    var newTitle by remember { mutableStateOf("") }
    var newSummary by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("") }

    val subgraphs = remember(searchQuery, nodes, edges) {
        viewModel.expansion.knowledgeGraph.searchAndExpand(searchQuery, nodes, edges)
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
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Knowledge Graph (${nodes.size} nodes • ${edges.size} links)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search nodes, tags & relationships") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.expansion.syncMemoriesToKnowledgeGraph() }) {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Link Recent Memories")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NOTE", "PROJECT", "TASK", "CONTACT").forEach { t ->
                            FilterChip(selected = newType == t, onClick = { newType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Node Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = newSummary, onValueChange = { newSummary = it }, label = { Text("Summary") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = newTags, onValueChange = { newTags = it }, label = { Text("Comma Tags (for auto-linking)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                viewModel.expansion.addKnowledgeNode(newType, newTitle, newSummary, newTags)
                                newTitle = ""; newSummary = ""; newTags = ""
                            }
                        },
                        enabled = newTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("add_kg_node_btn")
                    ) { Text("Add & Auto-Link Node") }
                }
            }
        }

        items(subgraphs, key = { it.root.id }) { sg ->
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
                            Text(sg.root.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${sg.root.nodeType} • Tags: ${sg.root.tagsCsv.ifBlank { "none" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.expansion.deleteKnowledgeNode(sg.root.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete node", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(sg.root.summary, style = MaterialTheme.typography.bodySmall)
                    if (sg.neighborNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sg.neighborNodes.forEach { nb ->
                                StatusPill(text = "→ ${nb.title}", accent = MaterialTheme.colorScheme.secondary)
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
fun ProductivityAndCalendarTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val itemsList by viewModel.expansion.productivityItems.collectAsStateWithLifecycle()
    val calEvents by viewModel.expansion.deviceCalendarEvents.collectAsStateWithLifecycle()

    var hasCalPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    var itemType by remember { mutableStateOf("TASK") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var remindInMin by remember { mutableStateOf("15") }

    val calPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCalPerm = grants[Manifest.permission.READ_CALENDAR] == true
        if (hasCalPerm) viewModel.expansion.refreshDeviceCalendar()
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
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personal Productivity & Calendar Agent", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TASK", "REMINDER", "NOTE").forEach { t ->
                            FilterChip(selected = itemType == t, onClick = { itemType = t }, label = { Text(t) })
                        }
                        listOf("LOW", "MEDIUM", "HIGH", "URGENT").forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Notes / Details") }, modifier = Modifier.fillMaxWidth())
                    if (itemType == "REMINDER") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(value = remindInMin, onValueChange = { remindInMin = it }, label = { Text("Remind after (minutes)") }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val mins = if (itemType == "REMINDER") remindInMin.toIntOrNull() ?: 15 else null
                                    viewModel.expansion.addProductivityItem(itemType, title, details, mins, priority)
                                    title = ""; details = ""
                                }
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f).testTag("save_productivity_btn")
                        ) { Text("Save Item") }
                        OutlinedButton(
                            onClick = {
                                if (title.isNotBlank()) {
                                    viewModel.expansion.createCalendarEvent(title, details)
                                }
                            },
                            enabled = title.isNotBlank()
                        ) { Text("+ Calendar") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        if (hasCalPerm) viewModel.expansion.refreshDeviceCalendar()
                        else calPermLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    }) {
                        Text(if (hasCalPerm) "Sync Device Calendar Events" else "Grant Calendar Permission & Sync")
                    }
                }
            }
        }

        if (calEvents.isNotEmpty()) {
            item { Text("Device Calendar (Next 14 Days)", style = MaterialTheme.typography.titleSmall) }
            items(calEvents, key = { it.id }) { ev ->
                val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(ev.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${fmt.format(Date(ev.startTimeMillis))} ${if (ev.location.isNotBlank()) "• ${ev.location}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item { Text("Tasks, Reminders & Notes (${itemsList.size})", style = MaterialTheme.typography.titleSmall) }
        items(itemsList, key = { it.id }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                    Checkbox(checked = item.completed, onCheckedChange = { viewModel.expansion.toggleProductivityCompleted(item) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[${item.itemType} • ${item.priority}] ${item.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (item.details.isNotBlank()) Text(item.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.expansion.deleteProductivityItem(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete productivity item", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineEmergencyTab(viewModel: AuraViewModel) {
    val offlineState by viewModel.expansion.offlineEmergencyState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var cmdInput by remember { mutableStateOf("(25 * 4) + 18") }
    var sosNumber by remember(offlineState.sosContactNumber) { mutableStateOf(offlineState.sosContactNumber) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.offlineEmergencyMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Offline Emergency Mode", style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = prefs.offlineEmergencyMode,
                            onCheckedChange = { viewModel.expansion.setOfflineEmergencyMode(it) },
                            modifier = Modifier.testTag("offline_emergency_switch")
                        )
                    }
                    Text(
                        "Operates with zero internet: local calculator, unit converter, hardware flashlight torch, device health report, and confirmed SOS dialer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.expansion.toggleFlashlight() }) {
                            Icon(if (offlineState.torchEnabled) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (offlineState.torchEnabled) "Torch OFF" else "Torch ON")
                        }
                        OutlinedButton(onClick = { viewModel.expansion.runOfflineUtilityCommand("battery health") }) {
                            Text("Read Battery/RAM")
                        }
                        OutlinedButton(onClick = { viewModel.expansion.runOfflineUtilityCommand("convert 10 km to mi") }) {
                            Text("10km → mi")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = cmdInput,
                            onValueChange = { cmdInput = it },
                            label = { Text("Offline Math / Unit / Utility Command") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.expansion.runOfflineUtilityCommand(cmdInput) }) { Text("Run") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(offlineState.lastUtilityOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = sosNumber,
                            onValueChange = { sosNumber = it },
                            label = { Text("SOS Number") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.expansion.launchSosDialer(sosNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Open SOS Dialer") }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareAndClipboardTab(viewModel: AuraViewModel) {
    val payload by viewModel.expansion.sharedPayload.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Universal Share & Clipboard Agent", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Accepts Android Sharesheet text, links, images & PDFs (via ACTION_SEND) or reads your clipboard safely when tapped.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.expansion.readClipboard() },
                        modifier = Modifier.fillMaxWidth().testTag("read_clipboard_btn")
                    ) { Text("Read Clipboard Safely") }
                }
            }
        }

        payload?.let { p ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${p.source} • ${p.mimeType}", style = MaterialTheme.typography.titleSmall)
                            if (p.injectionWarning != null) StatusPill(text = "INJECTION GUARD", accent = MaterialTheme.colorScheme.error)
                        }
                        p.previewBitmap?.let { bmp ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Shared image preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(p.sanitizedText.take(600), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.expansion.sendSharedPayloadToAura("Summarize and extract key actions from this shared content:") }) {
                                Text("Summarize with AURA")
                            }
                            OutlinedButton(onClick = {
                                viewModel.expansion.addKnowledgeNode("NOTE", p.subject.ifBlank { "Shared Note" }, p.sanitizedText.take(220), "shared")
                                viewModel.expansion.clearSharedPayload()
                            }) { Text("Save to KG") }
                        }
                    }
                }
            }
        }
    }
}
