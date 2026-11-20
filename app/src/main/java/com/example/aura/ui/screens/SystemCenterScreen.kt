package com.example.aura.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.ai.AiProviderType
import com.example.aura.ai.AuraVoiceCatalog
import com.example.aura.ai.GEMINI_DEFAULT_MODEL
import com.example.aura.ai.VoiceEngineMode
import com.example.aura.security.ActionSafetyLevel
import com.example.aura.security.GeminiKeyStore
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import com.example.ui.theme.AuraThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCenterScreen(viewModel: AuraViewModel) {
    var subTab by remember { mutableIntStateOf(0) }
    val requestedSubTab by viewModel.requestedSystemSubTab.collectAsStateWithLifecycle()
    val tabs = listOf(
        "Marketplace",
        "Memory",
        "Security",
        "API, Cost & Voices",
        "Observability & Bench",
        "Audit & Info",
        "Autonomy & Perms",
        "Backup & Sync",
        "Perf & Diag",
        "Lang QA & Release"
    )

    LaunchedEffect(requestedSubTab) {
        val target = requestedSubTab ?: return@LaunchedEffect
        if (target in tabs.indices) {
            subTab = target
        }
        viewModel.consumeRequestedSystemSubTab()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }
        when (subTab) {
            0 -> MarketplaceTab(viewModel)
            1 -> MemoryCenterTab(viewModel)
            2 -> SecurityCenterTab(viewModel)
            3 -> ApiAndVoiceSettingsTab(viewModel)
            4 -> BenchmarkAndObservabilityTab(viewModel)
            5 -> FeatureAuditAndDiagnosticsTab(viewModel)
            6 -> AutonomyAndPermissionsTab(viewModel)
            7 -> BackupAndMultiDeviceSyncTab(viewModel)
            8 -> PerformanceAndDiagnosticsTab(viewModel)
            9 -> LanguageQaAndReleaseTab(viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketplaceTab(viewModel: AuraViewModel) {
    val tools by viewModel.marketplaceTools.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf(
        "All", "AI Models", "AI Agents", "Android Automation", "Voice Tools",
        "Vision Tools", "Research Tools", "Coding Tools", "MCP Tools",
        "Productivity", "Communication", "Security", "Developer Utilities"
    )
    val filtered = if (selectedCategory == "All") tools else tools.filter { it.category == selectedCategory }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { tool ->
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
                        Text("${tool.name} (v${tool.version})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(
                            text = tool.status.replace("INSTALLED_", ""),
                            accent = if (tool.status == "INSTALLED_ENABLED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${tool.category} • ${tool.source} • ${tool.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(tool.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Perms: ${tool.requiredPermissions} • Deps: ${tool.dependencies}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Security: ${tool.securityScan} • Tests: ${tool.testResults}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tool.status == "INSTALLED_ENABLED") {
                            Button(
                                onClick = { viewModel.testAndRunMarketplaceTool(tool) },
                                modifier = Modifier.testTag("run_tool_btn_${tool.id}")
                            ) { Text("Run Tool") }
                            OutlinedButton(onClick = { viewModel.updateMarketplaceToolStatus(tool, "DISABLE") }) { Text("Disable") }
                        } else {
                            Button(onClick = { viewModel.updateMarketplaceToolStatus(tool, "ENABLE") }) { Text("Enable") }
                        }
                        if (tool.updateAvailableVersion != null) {
                            Button(onClick = { viewModel.updateMarketplaceToolStatus(tool, "UPDATE") }) {
                                Text("Update v${tool.updateAvailableVersion}")
                            }
                        }
                        if (tool.previousVersion != null) {
                            OutlinedButton(onClick = { viewModel.updateMarketplaceToolStatus(tool, "ROLLBACK") }) {
                                Text("Rollback v${tool.previousVersion}")
                            }
                        }
                        if (tool.id.startsWith("custom_")) {
                            OutlinedButton(onClick = { viewModel.updateMarketplaceToolStatus(tool, "UNINSTALL") }) { Text("Uninstall") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCenterTab(viewModel: AuraViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    val filtered = if (search.isBlank()) memories else memories.filter {
        it.title.contains(search, true) || it.content.contains(search, true) || it.category.contains(search, true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Memory Enabled", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = prefs.memoryEnabled, onCheckedChange = { viewModel.setMemoryEnabled(it) })
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Private Mode (Do not record chats)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = prefs.privateMode, onCheckedChange = { viewModel.setPrivateMode(it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search Memories") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.exportAndShareMemories() }) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export & Share JSON Backup")
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
                    Text("Add Saved Instruction / Preference", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = newContent, onValueChange = { newContent = it }, label = { Text("Instruction or context") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank() && newContent.isNotBlank()) {
                                viewModel.saveMemory("INSTRUCTION", newTitle.trim(), newContent.trim())
                                newTitle = ""; newContent = ""
                            }
                        },
                        modifier = Modifier.testTag("save_memory_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Memory")
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { mem ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[${mem.category}] ${mem.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(mem.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCenterTab(viewModel: AuraViewModel) {
    val approvals by viewModel.approvals.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val allowlist by viewModel.allowlistedPackages.collectAsStateWithLifecycle()
    val blocklist by viewModel.blocklistedPackages.collectAsStateWithLifecycle()
    val emergencyStop by viewModel.emergencyStopActive.collectAsStateWithLifecycle()
    var packageInput by remember { mutableStateOf("") }

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
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("4-Level Guardrail Policy", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionSafetyLevel.entries.forEach { lvl ->
                        Text("${lvl.label}: ${lvl.description}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (emergencyStop) {
                        Button(
                            onClick = { viewModel.resumeFromEmergencyStop() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Resume System from Emergency STOP") }
                    } else {
                        Button(
                            onClick = { viewModel.triggerEmergencyStop() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Trigger Emergency STOP") }
                    }
                }
            }
        }

        // User Approval Queue
        item {
            Text("Approval Queue (${approvals.count { it.status == "PENDING" }} pending)", style = MaterialTheme.typography.titleSmall)
        }
        items(approvals.take(10), key = { it.id }) { req ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(req.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusPill(text = req.status, accent = MaterialTheme.colorScheme.secondary)
                    }
                    Text(req.description, style = MaterialTheme.typography.bodySmall)
                    if (req.status == "PENDING") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.approveRequest(req) }) { Text("Approve") }
                            OutlinedButton(onClick = { viewModel.denyRequest(req) }) { Text("Deny") }
                        }
                    }
                }
            }
        }

        // App Allowlist & Blocklist
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("App Allowlist / Blocklist", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = { Text("Package Name (e.g. com.whatsapp)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (packageInput.isNotBlank()) { viewModel.permissionGuard.setPackagePolicy(packageInput, true); packageInput = "" }
                        }) { Text("Allowlist") }
                        OutlinedButton(onClick = {
                            if (packageInput.isNotBlank()) { viewModel.permissionGuard.setPackagePolicy(packageInput, false); packageInput = "" }
                        }) { Text("Blocklist") }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Allowed (${allowlist.size}): ${allowlist.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Blocked (${blocklist.size}): ${blocklist.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Text("Security Audit Logs (${auditLogs.size})", style = MaterialTheme.typography.titleSmall)
        }
        items(auditLogs.take(20), key = { it.id }) { log ->
            Text(
                text = "[L${log.safetyLevel} ${log.outcome}] ${log.actor} • ${log.action}: ${log.details}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ApiAndVoiceSettingsTab(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val slots by viewModel.apiSlots.collectAsStateWithLifecycle()
    val usage by viewModel.providerUsage.collectAsStateWithLifecycle()
    val geminiKeys by viewModel.geminiKeys.collectAsStateWithLifecycle()
    val geminiTestStatus by viewModel.geminiKeyTestStatus.collectAsStateWithLifecycle()
    val isTestingGeminiKey by viewModel.isTestingGeminiKey.collectAsStateWithLifecycle()
    val selectedGeminiModel by viewModel.selectedGeminiModel.collectAsStateWithLifecycle()
    val voiceEngineMode by viewModel.voiceEngineMode.collectAsStateWithLifecycle()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsStateWithLifecycle()

    var geminiKeyInput by remember { mutableStateOf("") }
    var showGeminiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    var customModelInput by remember(selectedGeminiModel) { mutableStateOf(selectedGeminiModel) }

    val discoveredModels by viewModel.discoveredProviderModels.collectAsStateWithLifecycle()
    val cloudDevUrl by viewModel.cloudDevBackendUrl.collectAsStateWithLifecycle()
    val cloudDevHealth by viewModel.cloudDevHealth.collectAsStateWithLifecycle()
    val lastCloudDevTask by viewModel.lastCloudDevTask.collectAsStateWithLifecycle()

    var selectedSlotProvider by remember { mutableStateOf(AiProviderType.GROQ) }
    var keyLabel by remember { mutableStateOf("") }
    var rawKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelOverride by remember { mutableStateOf("") }

    var cloudDevUrlInput by remember(cloudDevUrl) { mutableStateOf(cloudDevUrl) }
    var cloudDevTokenInput by remember { mutableStateOf("") }
    var cloudDevFeatureName by remember { mutableStateOf("dry-check-feature") }
    var cloudDevTargetPath by remember { mutableStateOf("app/src/main/java/com/example/aura/ui/AuraConstants.kt") }
    var cloudDevContentInput by remember { mutableStateOf("// AURA 2.1 cloud dev verification check") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // AI Provider & Gemini API Key Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("AI Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiProviderType.entries.forEach { provider ->
                            FilterChip(
                                selected = prefs.activeProviderId == provider.id,
                                onClick = {
                                    val targetModel = if (provider == AiProviderType.FIREBASE_GEMINI) {
                                        selectedGeminiModel
                                    } else {
                                        provider.defaultModels.first()
                                    }
                                    viewModel.setProviderAndModel(provider.id, targetModel)
                                },
                                label = { Text(provider.displayName.substringBefore(" (")) },
                                modifier = Modifier.testTag("provider_chip_${provider.id.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val activeProvider = AiProviderType.entries.firstOrNull { it.id == prefs.activeProviderId } ?: AiProviderType.FIREBASE_GEMINI
                    Text(activeProvider.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeProvider == AiProviderType.FIREBASE_GEMINI) {
                        val presetOptions = listOf(
                            GEMINI_DEFAULT_MODEL to "$GEMINI_DEFAULT_MODEL (default)",
                            "gemini-3.5-flash-lite" to "gemini-3.5-flash-lite",
                            "gemini-4-argon" to "gemini-4-argon"
                        )
                        val dropdownDisplayText = presetOptions.firstOrNull { it.first == selectedGeminiModel }?.second
                            ?: "Custom ($selectedGeminiModel)"

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = dropdownDisplayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth()
                                    .testTag("gemini_model_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                presetOptions.forEach { (modelValue, modelLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(modelLabel) },
                                        onClick = {
                                            customModelInput = modelValue
                                            viewModel.selectGeminiModel(modelValue)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customModelInput,
                                onValueChange = { customModelInput = it },
                                label = { Text("Custom Model Name") },
                                placeholder = { Text("e.g. $GEMINI_DEFAULT_MODEL") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gemini_custom_model_input")
                            )
                            Button(
                                onClick = { viewModel.selectGeminiModel(customModelInput) },
                                enabled = customModelInput.isNotBlank(),
                                modifier = Modifier.testTag("save_gemini_model_btn")
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeProvider.defaultModels.forEach { model ->
                                FilterChip(
                                    selected = prefs.activeModelId == model,
                                    onClick = { viewModel.setProviderAndModel(activeProvider.id, model) },
                                    label = { Text(model) }
                                )
                            }
                        }
                        if (activeProvider != AiProviderType.LOCAL_OFFLINE) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.discoverProviderModels(activeProvider) },
                                modifier = Modifier.testTag("discover_active_models_btn")
                            ) {
                                Text("Discover Live Models / Tools")
                            }
                            val liveDiscovered = discoveredModels[activeProvider.id].orEmpty()
                            if (liveDiscovered.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Discovered (${liveDiscovered.size}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    liveDiscovered.take(12).forEach { model ->
                                        FilterChip(
                                            selected = prefs.activeModelId == model,
                                            onClick = { viewModel.setProviderAndModel(activeProvider.id, model) },
                                            label = { Text(model) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (activeProvider == AiProviderType.FIREBASE_GEMINI) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Gemini API Key (${geminiKeys.size}/${GeminiKeyStore.MAX_KEYS})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            StatusPill(
                                text = if (geminiKeys.isEmpty()) "No Key" else "429 Auto-Failover",
                                accent = if (geminiKeys.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = { geminiKeyInput = it },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("Paste key from Google AI Studio") },
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showGeminiKey = !showGeminiKey },
                                    modifier = Modifier.testTag("toggle_gemini_key_visibility_btn")
                                ) {
                                    Icon(
                                        imageVector = if (showGeminiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showGeminiKey) "Hide API key" else "Show API key"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (viewModel.saveGeminiApiKey(geminiKeyInput)) {
                                        geminiKeyInput = ""
                                    }
                                },
                                enabled = geminiKeyInput.isNotBlank() && geminiKeys.size < GeminiKeyStore.MAX_KEYS,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_gemini_key_btn")
                            ) {
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = { viewModel.testGeminiApiKey(geminiKeyInput) },
                                enabled = !isTestingGeminiKey && (geminiKeyInput.isNotBlank() || geminiKeys.isNotEmpty()),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_gemini_key_btn")
                            ) {
                                if (isTestingGeminiKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...")
                                } else {
                                    Text("Test Key")
                                }
                            }
                        }

                        geminiTestStatus?.let { (isOk, msg) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("gemini_key_test_status")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Get free Gemini API key",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val opened = runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }.isSuccess
                                    if (!opened) {
                                        Toast.makeText(context, "No browser app found to open Google AI Studio.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("get_free_gemini_key_link")
                        )
                        Text(
                            text = "1) link kholo aur Google account se login karo\n2) \"Create API key\" dabao\n3) key copy karke yahan paste karo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (geminiKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Saved Gemini Keys (auto-failover on HTTP 429)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            geminiKeys.forEach { entry ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${entry.label} • ${entry.maskedPreview}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                entry.lastTestStatus,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (entry.lastTestStatus == "Key valid") {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.testGeminiApiKey(keyId = entry.id) },
                                            enabled = !isTestingGeminiKey
                                        ) {
                                            Text("Test")
                                        }
                                        IconButton(onClick = { viewModel.deleteGeminiApiKey(entry.id) }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete Gemini key",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Automatic Provider Fallback", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = prefs.autoFallbackProviders, onCheckedChange = { viewModel.setAutoFallback(it) })
                    }
                    if (activeProvider != AiProviderType.FIREBASE_GEMINI) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.testProvider(activeProvider) },
                            modifier = Modifier.testTag("test_active_provider_btn")
                        ) {
                            Text("Test Active Provider Connection")
                        }
                    }
                }
            }
        }

        item {
            PrivacyAndRoutingCard(viewModel)
        }

        // Voice Engine & Gemini Voice Dropdown (Kore, Puck, Charon, Aoede, Fenrir)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Voice engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VoiceEngineMode.entries.forEach { mode ->
                            FilterChip(
                                selected = voiceEngineMode == mode,
                                onClick = { viewModel.setVoiceEngineMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("voice_engine_${mode.id.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = voiceDropdownExpanded,
                        onExpandedChange = { voiceDropdownExpanded = !voiceDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedVoiceName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Voice") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth()
                                .testTag("voice_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = voiceDropdownExpanded,
                            onDismissRequest = { voiceDropdownExpanded = false }
                        ) {
                            AuraVoiceCatalog.geminiDropdownVoices.forEach { voiceOption ->
                                DropdownMenuItem(
                                    text = { Text(voiceOption) },
                                    onClick = {
                                        viewModel.setVoicePreset(voiceOption)
                                        voiceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val chosen = AuraVoiceCatalog.findPreset(selectedVoiceName)
                    val engineNote = if (voiceEngineMode == VoiceEngineMode.GEMINI) {
                        "Active: Gemini (${chosen.name} — ${chosen.tone}) • Fallback: Google/Device TTS (rate 1.0, pitch 1.0)"
                    } else {
                        "Active: Device voice only (Google/Device TTS • rate 1.0, pitch 1.0)"
                    }
                    Text(engineNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // GitHub Auto-Build Settings & Self-Update
        item {
            GitHubSettingsCard(viewModel)
        }
        item {
            GitHubAutoBuildAndUpdateCard(viewModel)
        }

        // Theme Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Interface Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AuraThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.replace("_", " ")) }
                            )
                        }
                    }
                }
            }
        }

        // Encrypted Keystore API Key Vault
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encrypted Multi-Provider Key Vault", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Google Gemini works keyless via Firebase App Check. Add optional external keys (AES-256-GCM encrypted in Android Keystore) for Groq, OpenRouter, Hugging Face, or OpenAI-compatible endpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AiProviderType.GROQ,
                            AiProviderType.OPENROUTER,
                            AiProviderType.CLAUDE,
                            AiProviderType.GROK,
                            AiProviderType.DEEPSEEK,
                            AiProviderType.QWEN,
                            AiProviderType.HUGGING_FACE,
                            AiProviderType.OLLAMA,
                            AiProviderType.MCP_SERVER,
                            AiProviderType.A2A_AGENT,
                            AiProviderType.OPENAI_COMPATIBLE
                        ).forEach { p ->
                            FilterChip(
                                selected = selectedSlotProvider == p,
                                onClick = { selectedSlotProvider = p; baseUrl = p.defaultBaseUrl; modelOverride = p.defaultModels.first() },
                                label = { Text(p.displayName) },
                                modifier = Modifier.testTag("vault_provider_chip_${p.id.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = keyLabel, onValueChange = { keyLabel = it }, label = { Text("Slot Label") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = rawKey,
                        onValueChange = { rawKey = it },
                        label = {
                            Text(
                                if (selectedSlotProvider.requiresApiKey) {
                                    "API Key (Encrypted at Rest)"
                                } else {
                                    "API / Bearer Token (Optional for ${selectedSlotProvider.displayName})"
                                }
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (Optional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = modelOverride, onValueChange = { modelOverride = it }, label = { Text("Preferred Model") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (rawKey.isNotBlank() || !selectedSlotProvider.requiresApiKey) {
                                viewModel.saveApiKeySlot(selectedSlotProvider.id, keyLabel, rawKey, baseUrl, modelOverride)
                                rawKey = ""; keyLabel = ""
                            }
                        },
                        enabled = rawKey.isNotBlank() || !selectedSlotProvider.requiresApiKey,
                        modifier = Modifier.testTag("save_api_key_btn")
                    ) {
                        Text(
                            if (selectedSlotProvider.requiresApiKey) "Encrypt & Save Key Slot"
                            else "Save Endpoint Configuration"
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_dev_backend_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cloud Self-Coding Dev Backend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connect tools/cloud_dev_backend.py (HTTPS or localhost) to run isolated-branch code edits and full Gradle unit tests remotely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cloudDevUrlInput,
                        onValueChange = { cloudDevUrlInput = it },
                        label = { Text("Cloud Dev Backend URL") },
                        placeholder = { Text("http://127.0.0.1:8787 or https://...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_dev_url_input")
                    )
                    OutlinedTextField(
                        value = cloudDevTokenInput,
                        onValueChange = { cloudDevTokenInput = it },
                        label = { Text("Bearer Token (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloudDevFeatureName,
                        onValueChange = { cloudDevFeatureName = it },
                        label = { Text("Task Feature Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloudDevTargetPath,
                        onValueChange = { cloudDevTargetPath = it },
                        label = { Text("Target Repo Path (Non-main branch)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.pingCloudDevBackend(cloudDevUrlInput, cloudDevTokenInput) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ping_cloud_dev_btn")
                        ) { Text("Ping Dev Backend") }
                        Button(
                            onClick = {
                                viewModel.pingCloudDevBackend(cloudDevUrlInput, cloudDevTokenInput)
                                viewModel.submitCloudDevTask(
                                    featureName = cloudDevFeatureName,
                                    targetPath = cloudDevTargetPath,
                                    content = cloudDevContentInput
                                )
                            },
                            enabled = cloudDevUrlInput.isNotBlank() && cloudDevTargetPath.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("submit_cloud_dev_task_btn")
                        ) { Text("Submit Dev Task") }
                    }
                    cloudDevHealth?.let { health ->
                        Text(
                            text = health.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (health.reachable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                    lastCloudDevTask?.let { task ->
                        Text(
                            text = "Last Dev Task [${task.status}] ${task.featureName} (${task.branch}) • testsPassed=${task.testsPassed} ${task.blockedReason.ifBlank { task.diffSummary }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.status == "COMPLETED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        items(slots, key = { it.id }) { slot ->
            val decrypted = viewModel.vault.getDecryptedKey(slot.id).orEmpty()
            val slotProvider = AiProviderType.entries.firstOrNull { it.id == slot.providerId } ?: AiProviderType.OPENAI_COMPATIBLE
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${slot.label} (${slot.providerId})", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Key: ${if (decrypted.isBlank() && !slotProvider.requiresApiKey) "Keyless Endpoint" else slot.maskedPreview(decrypted)} • Endpoint: ${slot.customBaseUrl.ifBlank { slotProvider.defaultBaseUrl }} • Status: ${slot.lastTestStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = { viewModel.discoverProviderModels(slotProvider, slot.id) }) { Text("Discover") }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(onClick = { viewModel.testProvider(slotProvider, slot.id) }) { Text("Test") }
                    IconButton(onClick = { viewModel.deleteApiKeySlot(slot.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete key slot", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (usage.isNotEmpty()) {
            item { Text("Recent Provider Telemetry (${usage.size})", style = MaterialTheme.typography.titleSmall) }
            items(usage.take(8), key = { it.id }) { u ->
                Text(
                    text = "${u.providerId}/${u.modelName} • ${u.latencyMs}ms • ~${u.estimatedTokens} tok • ${if (u.success) "OK" else u.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (u.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun FeatureAuditAndDiagnosticsTab(viewModel: AuraViewModel) {
    val audits by viewModel.featureAudits.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val health by viewModel.deviceHealth.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Diagnostics & Offline Readiness", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("RAM: ${health.availableRamMb}MB free / ${health.totalRamMb}MB total", style = MaterialTheme.typography.bodySmall)
                    Text("Storage: ${"%.2f".format(health.freeStorageGb)} GB free • Battery: ${health.batteryPercent}%", style = MaterialTheme.typography.bodySmall)
                    Text(health.offlineLlmReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.refreshHealth() }) { Text("Refresh Diagnostics") }
                }
            }
        }

        item {
            Text("Honest Feature Audit Report (${audits.size} modules)", style = MaterialTheme.typography.titleSmall)
        }
        items(audits, key = { it.id }) { item ->
            val accent = when (item.status) {
                "ACTIVE" -> MaterialTheme.colorScheme.tertiary
                "PARTIAL" -> MaterialTheme.colorScheme.secondary
                "BLOCKED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
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
                        Text(item.featureName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusPill(text = item.status, accent = accent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.evidenceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (tasks.isNotEmpty()) {
            item { Text("Recent Task Execution History (${tasks.size})", style = MaterialTheme.typography.titleSmall) }
            items(tasks.take(10), key = { it.id }) { t ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("[${t.status}] ${t.instruction}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${t.assignedAgents} • ${t.durationMs}ms • ${t.verificationNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
