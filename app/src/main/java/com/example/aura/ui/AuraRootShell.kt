package com.example.aura.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.agents.AgentStatus
import com.example.aura.ui.components.AuraTopStatusBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import com.example.aura.ui.screens.AgentsAndEvolutionScreen
import com.example.aura.ui.screens.ChatBrainScreen
import com.example.aura.ui.screens.ControlHubScreen
import com.example.aura.ui.screens.CoreDashboardScreen
import com.example.aura.ui.screens.GitHubPushPreviewDialog
import com.example.aura.ui.screens.SystemCenterScreen

private data class AuraNavTab(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun AuraRootShell(viewModel: AuraViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val activeModelInUse by viewModel.activeModelInUse.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val emergencyStop by viewModel.emergencyStopActive.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovalCount.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val showMissingGeminiKeyDialog by viewModel.showMissingGeminiKeyDialog.collectAsStateWithLifecycle()
    val requestedSystemSubTab by viewModel.requestedSystemSubTab.collectAsStateWithLifecycle()
    val pendingGitHubPush by viewModel.pendingGitHubPush.collectAsStateWithLifecycle()
    val isPushingGitHub by viewModel.isPushingGitHub.collectAsStateWithLifecycle()
    val selfUpdateState by viewModel.selfUpdateState.collectAsStateWithLifecycle()
    val sharedPayload by viewModel.expansion.sharedPayload.collectAsStateWithLifecycle()
    var dismissUpdateBannerForTag by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    LaunchedEffect(requestedSystemSubTab) {
        if (requestedSystemSubTab != null) {
            selectedTab = 4
        }
    }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    val tabs = listOf(
        AuraNavTab(0, "Core", Icons.Filled.Dashboard, "nav_core"),
        AuraNavTab(1, "Chat", Icons.AutoMirrored.Filled.Chat, "nav_chat"),
        AuraNavTab(2, "Agents", Icons.Filled.AutoAwesome, "nav_agents"),
        AuraNavTab(3, "Control", Icons.Filled.TouchApp, "nav_control"),
        AuraNavTab(4, "System", Icons.Filled.SettingsApplications, "nav_system")
    )

    val onlineCount = agents.count { it.status == AgentStatus.ONLINE || it.status == AgentStatus.ACTIVE }

    val layoutDir = if (prefs.forceUrduRtlPreview) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            topBar = {
                Box(modifier = Modifier.statusBarsPadding()) {
                    AuraTopStatusBar(
                        activeModel = activeModelInUse,
                        agentsOnline = onlineCount,
                        emergencyStopActive = emergencyStop,
                        pendingApprovals = pendingApprovals,
                        onEmergencyStop = { viewModel.triggerEmergencyStop() },
                        onResume = { viewModel.resumeFromEmergencyStop() }
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab.index,
                                onClick = { selectedTab = tab.index },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab.index,
                                onClick = { selectedTab = tab.index },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 860.dp)
                            .fillMaxWidth()
                    ) {
                        val latestRelease = selfUpdateState.latestRelease
                        if (selfUpdateState.isUpdateAvailable &&
                            latestRelease != null &&
                            dismissUpdateBannerForTag != latestRelease.tagName
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("launch_update_available_banner")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Update available: ${latestRelease.tagName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Installed v${selfUpdateState.installedVersionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { dismissUpdateBannerForTag = latestRelease.tagName }
                                        ) {
                                            Text("Later")
                                        }
                                        Button(
                                            onClick = {
                                                selectedTab = 4
                                                viewModel.openGeminiSettings()
                                                viewModel.downloadAndInstallLatestUpdate()
                                            },
                                            enabled = !selfUpdateState.isDownloading,
                                            modifier = Modifier.testTag("banner_download_update_btn")
                                        ) {
                                            Text(if (selfUpdateState.isDownloading) "${(selfUpdateState.downloadProgress * 100).toInt()}%" else "Download")
                                        }
                                    }
                                }
                            }
                        }

                        sharedPayload?.let { shared ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("shared_payload_banner")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Shared (${shared.mimeType})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = shared.sanitizedText.take(90),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { viewModel.expansion.clearSharedPayload() },
                                            modifier = Modifier.testTag("dismiss_shared_banner_btn")
                                        ) {
                                            Text("Dismiss")
                                        }
                                        TextButton(
                                            onClick = { selectedTab = 3 },
                                            modifier = Modifier.testTag("inspect_shared_banner_btn")
                                        ) {
                                            Text("Inspect")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.expansion.sendSharedPayloadToAura("Analyze this shared content:")
                                                selectedTab = 1
                                            },
                                            modifier = Modifier.testTag("analyze_shared_banner_btn")
                                        ) {
                                            Text("Analyze")
                                        }
                                    }
                                }
                            }
                        }

                        when (selectedTab) {
                            0 -> CoreDashboardScreen(viewModel = viewModel, onNavigateTab = { selectedTab = it })
                            1 -> ChatBrainScreen(viewModel = viewModel)
                            2 -> AgentsAndEvolutionScreen(viewModel = viewModel)
                            3 -> ControlHubScreen(viewModel = viewModel)
                            4 -> SystemCenterScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        if (showMissingGeminiKeyDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMissingGeminiKeyDialog() },
                title = { Text("API key add karo") },
                text = {
                    Text(
                        "Gemini AI assistant use karne ke liye Settings mein apni free Gemini API key save karein."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.openGeminiSettings() },
                        modifier = Modifier.testTag("open_settings_for_key_btn")
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissMissingGeminiKeyDialog() },
                        modifier = Modifier.testTag("dismiss_missing_key_dialog_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        pendingGitHubPush?.let { proposal ->
            GitHubPushPreviewDialog(
                proposal = proposal,
                isPushing = isPushingGitHub,
                onApprove = { viewModel.approvePendingGitHubPush() },
                onReject = { viewModel.rejectPendingGitHubPush() }
            )
        }
    }
    }
}
