package com.example.aura.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.aura.agents.AgentStatus
import com.example.aura.ai.ChatTurn
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.AuraVoiceOrb
import com.example.aura.ui.components.StatusPill
import com.example.aura.voice.VoiceOrbState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreDashboardScreen(
    viewModel: AuraViewModel,
    onNavigateTab: (Int) -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val rms by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partial by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val health by viewModel.deviceHealth.collectAsStateWithLifecycle()
    val accessibilityOk by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val tools by viewModel.marketplaceTools.collectAsStateWithLifecycle()
    val audits by viewModel.featureAudits.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovalCount.collectAsStateWithLifecycle()
    val loopPhase by viewModel.currentLoopPhase.collectAsStateWithLifecycle()
    val continuousVoice by viewModel.continuousVoice.collectAsStateWithLifecycle()
    val routedInfo by viewModel.lastRoutedProviderInfo.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceTurn()
    }

    fun handleOrbClick() {
        if (orbState == VoiceOrbState.LISTENING) {
            viewModel.stopVoiceListening()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMic) viewModel.startVoiceTurn()
            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val activeAuditCount = audits.count { it.status == "ACTIVE" }
    val installedToolsCount = tools.count { it.status.startsWith("INSTALLED") }
    val activeAgentsCount = agents.count { it.status == AgentStatus.ACTIVE }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Hero Banner + Voice Orb
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_aura_neural_core_1794938902096),
                        contentDescription = "AURA neural horizon backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.32f)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusPill(text = "LOOP: $loopPhase", accent = MaterialTheme.colorScheme.primary)
                            StatusPill(text = "VOICE: ${prefs.activeVoiceName}", accent = MaterialTheme.colorScheme.secondary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AuraVoiceOrb(
                            state = orbState,
                            rmsLevel = rms,
                            onClick = ::handleOrbClick
                        )

                        if (partial.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$partial\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = continuousVoice,
                                onClick = { viewModel.toggleContinuousVoice(!continuousVoice) },
                                label = { Text(if (continuousVoice) "Continuous Voice: ON" else "Continuous Voice: OFF") },
                                modifier = Modifier.testTag("continuous_voice_chip")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "English • اردو • Roman Urdu Autonomous Assistant\nRoute: $routedInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Language Switch & Multilingual Test Commands
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language & Quick Commands", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("AUTO" to "Auto", "EN" to "English", "UR" to "اردو", "ROMAN_UR" to "Roman Urdu").forEach { (code, label) ->
                            FilterChip(
                                selected = prefs.preferredLanguage == code,
                                onClick = { viewModel.setLanguagePreference(code) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val samples = listOf(
                            "Research GitHub android ai agent",
                            "Open YouTube",
                            "Mera phone status aur RAM batao",
                            "Build tool battery optimizer"
                        )
                        samples.forEach { cmd ->
                            AssistChip(
                                onClick = {
                                    viewModel.sendCommand(cmd)
                                    onNavigateTab(1)
                                },
                                label = { Text(cmd, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }

        // Live Telemetry Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TelemetryTile(
                        title = "Agents Network",
                        value = "${agents.size} Online ($activeAgentsCount Active)",
                        accent = MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.AutoAwesome,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(2) }
                    )
                    TelemetryTile(
                        title = "Screen Vision",
                        value = if (accessibilityOk) "Accessibility Active" else "Needs Opt-In",
                        accent = if (accessibilityOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        icon = Icons.Filled.TouchApp,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(3) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TelemetryTile(
                        title = "System Health",
                        value = "${health.availableRamMb}MB RAM • ${health.batteryPercent}% Bat",
                        accent = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Filled.Memory,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.refreshHealth(); onNavigateTab(4) }
                    )
                    TelemetryTile(
                        title = "Security & Tools",
                        value = "$installedToolsCount Tools • $pendingApprovals Approvals",
                        accent = if (pendingApprovals > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.Security,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(4) }
                    )
                }
            }
        }

        // Honest Feature Audit Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verified Capability Matrix", style = MaterialTheme.typography.titleMedium)
                        StatusPill(
                            text = "$activeAuditCount/${audits.size} ACTIVE",
                            accent = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AURA audits every module honestly. Hardware/OS opt-in modules report PARTIAL until granted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onNavigateTab(4) },
                        modifier = Modifier.testTag("open_feature_audit_btn")
                    ) {
                        Text("Inspect Full System Audit & Marketplace")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ChatBrainScreen(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val loopPhase by viewModel.currentLoopPhase.collectAsStateWithLifecycle()
    val approvals by viewModel.approvals.collectAsStateWithLifecycle()
    val routedInfo by viewModel.lastRoutedProviderInfo.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    attachedBitmap = BitmapFactory.decodeStream(stream)
                }
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceTurn()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    text = "Autonomous Loop: $loopPhase",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Data Receiver: $routedInfo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(messages, key = { it.id }) { turn ->
                val approval = turn.pendingApprovalId?.let { id -> approvals.firstOrNull { it.id == id } }
                ChatTurnBubble(
                    turn = turn,
                    pendingApprovalStatus = approval?.status,
                    onApprove = { approval?.let { viewModel.approveRequest(it) } },
                    onDeny = { approval?.let { viewModel.denyRequest(it) } }
                )
            }
        }

        // Attached image preview
        attachedBitmap?.let { bmp ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Attached image preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Image attached for Gemini Vision", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { attachedBitmap = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove image")
                }
            }
        }

        // Input Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("attach_image_button")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach image")
                }

                IconButton(
                    onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) viewModel.startVoiceTurn() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("chat_mic_button")
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask in English, اردو, or Roman Urdu...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        val msg = inputText.trim()
                        if (msg.isNotEmpty()) {
                            viewModel.sendCommand(msg, attachedBitmap = attachedBitmap)
                            inputText = ""
                            attachedBitmap = null
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTurnBubble(
    turn: ChatTurn,
    pendingApprovalStatus: String?,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val isUser = turn.role == "USER"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = turn.modelLabel.ifBlank { "AURA Brain" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (turn.agentsUsed.isNotEmpty()) {
                            Text(
                                text = "• ${turn.agentsUsed.first()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = turn.text.ifBlank { if (turn.isStreaming) "Thinking..." else "" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (turn.loopPhaseTrace.isNotEmpty() && !isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = turn.loopPhaseTrace.joinToString(" → "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pendingApprovalStatus == "PENDING") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.testTag("inline_approve_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
                        OutlinedButton(
                            onClick = onDeny,
                            modifier = Modifier.testTag("inline_deny_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deny")
                        }
                    }
                } else if (pendingApprovalStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Decision: $pendingApprovalStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
