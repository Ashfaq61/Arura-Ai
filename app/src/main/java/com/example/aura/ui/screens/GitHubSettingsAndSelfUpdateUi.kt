package com.example.aura.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.github.CiBuildStage
import com.example.aura.github.DEFAULT_AURA_BRANCH
import com.example.aura.github.DiffLineType
import com.example.aura.github.GITHUB_TOKEN_CREATE_URL
import com.example.aura.github.MAX_AUTO_FIX_ATTEMPTS
import com.example.aura.github.PendingGitHubPushProposal
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@Composable
fun GitHubSettingsCard(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val config by viewModel.gitHubConfig.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.gitHubConnectionStatus.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingGitHub.collectAsStateWithLifecycle()

    var ownerInput by remember(config.owner) { mutableStateOf(config.owner) }
    var repoInput by remember(config.repo) { mutableStateOf(config.repo) }
    var branchInput by remember(config.branch) { mutableStateOf(config.branch.ifBlank { DEFAULT_AURA_BRANCH }) }
    var tokenInput by remember(config.token) { mutableStateOf(config.token) }
    var showToken by remember { mutableStateOf(false) }

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
                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusPill(
                    text = if (config.isConfigured && config.token.isNotBlank()) "Encrypted PAT Ready" else "Setup Needed",
                    accent = if (config.isConfigured && config.token.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = ownerInput,
                onValueChange = { ownerInput = it },
                label = { Text("Repo owner") },
                placeholder = { Text("e.g. octocat") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_owner_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                label = { Text("Repo name") },
                placeholder = { Text("e.g. aura-ai-android") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = branchInput,
                onValueChange = { branchInput = it },
                label = { Text("Branch (never main)") },
                placeholder = { Text(DEFAULT_AURA_BRANCH) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_branch_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Token (Fine-grained PAT)") },
                placeholder = { Text("github_pat_...") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showToken = !showToken },
                        modifier = Modifier.testTag("toggle_github_token_visibility_btn")
                    ) {
                        Icon(
                            imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_token_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow workflow edits", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Off by default: blocks .github/workflows/* edits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.allowWorkflowEdits,
                    onCheckedChange = { viewModel.setAllowWorkflowEdits(it) },
                    modifier = Modifier.testTag("allow_workflow_edits_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        viewModel.saveGitHubSettings(
                            owner = ownerInput,
                            repo = repoInput,
                            branch = branchInput,
                            token = tokenInput,
                            allowWorkflowEdits = config.allowWorkflowEdits
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_github_settings_btn")
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.testGitHubConnection(
                            ownerInput = ownerInput,
                            repoInput = repoInput,
                            branchInput = branchInput,
                            tokenInput = tokenInput
                        )
                    },
                    enabled = !isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_github_connection_btn")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...")
                    } else {
                        Text("Test Connection")
                    }
                }
            }

            connectionStatus?.let { (isOk, msg) ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("github_connection_status")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Create Fine-grained token ($GITHUB_TOKEN_CREATE_URL)",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val opened = runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_TOKEN_CREATE_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                        if (!opened) {
                            Toast.makeText(context, "No browser found to open GitHub.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 8.dp)
                    .testTag("github_token_help_link")
            )
            Text(
                text = "\"Fine-grained token\" chuno, sirf isi repo ko select karo, permissions: Contents (Read and write), Actions (Read), Metadata (Read).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubAutoBuildAndUpdateCard(viewModel: AuraViewModel) {
    val context = LocalContext.current
    val config by viewModel.gitHubConfig.collectAsStateWithLifecycle()
    val isPreparingPush by viewModel.isPreparingPushProposal.collectAsStateWithLifecycle()
    val lastCommitUrl by viewModel.lastGitHubCommitUrl.collectAsStateWithLifecycle()
    val lastCommitSha by viewModel.lastGitHubCommitSha.collectAsStateWithLifecycle()
    val canMergeToMain by viewModel.canMergeToMain.collectAsStateWithLifecycle()
    val isMerging by viewModel.isMergingToMain.collectAsStateWithLifecycle()
    val ciRun by viewModel.ciRunStatus.collectAsStateWithLifecycle()
    val isPollingCi by viewModel.isPollingCi.collectAsStateWithLifecycle()
    val failedLogTail by viewModel.failedJobLogTail.collectAsStateWithLifecycle()
    val autoFixCount by viewModel.autoFixAttemptCount.collectAsStateWithLifecycle()
    val isAutoFixing by viewModel.isAutoFixingBuild.collectAsStateWithLifecycle()
    val selfUpdate by viewModel.selfUpdateState.collectAsStateWithLifecycle()
    val changelogMarkdown by viewModel.latestChangelogMarkdown.collectAsStateWithLifecycle()

    var directMode by remember { mutableStateOf(false) }
    var filePathInput by remember { mutableStateOf("") }
    var changePromptOrContent by remember { mutableStateOf("") }
    var commitMessageInput by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GitHub Auto-Build & Self-Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Pushes always go to '${config.effectivePushBranch}' after Approve/Reject preview.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !directMode,
                    onClick = { directMode = false },
                    label = { Text("Ask Aura (Gemini)") }
                )
                FilterChip(
                    selected = directMode,
                    onClick = { directMode = true },
                    label = { Text("Direct File Draft") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = filePathInput,
                onValueChange = { filePathInput = it },
                label = { Text(if (directMode) "Repo File Path (required)" else "Target File Path (optional)") },
                placeholder = { Text("e.g. app/src/main/java/com/example/aura/ui/AuraConstants.kt") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_push_file_path_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = changePromptOrContent,
                onValueChange = { changePromptOrContent = it },
                label = { Text(if (directMode) "Full File Content" else "Describe Code Change for Aura") },
                placeholder = {
                    Text(
                        if (directMode) "Paste full file content..."
                        else "e.g. Add greeting helper comment in MainActivity"
                    )
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_push_content_input")
            )
            if (directMode) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = commitMessageInput,
                    onValueChange = { commitMessageInput = it },
                    label = { Text("Commit Message") },
                    placeholder = { Text("feat: update file via Aura") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (directMode) {
                        viewModel.proposeGitHubFileChange(
                            path = filePathInput,
                            newContent = changePromptOrContent,
                            commitMessage = commitMessageInput
                        )
                    } else {
                        viewModel.askAuraToProposeRepoChange(
                            pathHint = filePathInput,
                            changeRequest = changePromptOrContent
                        )
                    }
                },
                enabled = !isPreparingPush && config.isConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("propose_github_push_btn")
            ) {
                if (isPreparingPush) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preparing Preview & Diff...")
                } else {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Push (${config.effectivePushBranch})")
                }
            }

            if (!lastCommitUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Last commit (${lastCommitSha.orEmpty().take(7)}) pushed to ${config.effectivePushBranch}:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(lastCommitUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                .padding(vertical = 4.dp)
                                .testTag("last_github_commit_link")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open commit on GitHub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lastCommitUrl.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (canMergeToMain) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.mergeAuraBranchToMain() },
                                enabled = !isMerging,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("github_merge_to_main_btn")
                            ) {
                                Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isMerging) "Merging..." else "Merge to main")
                            }
                        }
                    }
                }
            }

            // Build Status & Auto-Fix Section
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPollingCi) "Build Status (polling 15s)" else "Build Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val stage = ciRun?.stage ?: CiBuildStage.IDLE
                val stageColor = when (stage) {
                    CiBuildStage.SUCCESS -> MaterialTheme.colorScheme.tertiary
                    CiBuildStage.FAILED -> MaterialTheme.colorScheme.error
                    CiBuildStage.BUILDING, CiBuildStage.QUEUED -> MaterialTheme.colorScheme.secondary
                    CiBuildStage.IDLE -> MaterialTheme.colorScheme.primary
                }
                StatusPill(
                    text = stage.label,
                    accent = stageColor,
                    modifier = Modifier.testTag("ci_build_stage_pill")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshBuildStatus() },
                    enabled = config.isConfigured,
                    modifier = Modifier.testTag("refresh_build_status_btn")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Build")
                }
                OutlinedButton(
                    onClick = { viewModel.createPullRequestForBranch() },
                    enabled = config.isConfigured && config.token.isNotBlank(),
                    modifier = Modifier.testTag("create_github_pr_btn")
                ) {
                    Text("Open PR")
                }
                OutlinedButton(
                    onClick = { viewModel.triggerGitHubWorkflowDispatch() },
                    enabled = config.isConfigured && config.token.isNotBlank(),
                    modifier = Modifier.testTag("trigger_ci_dispatch_btn")
                ) {
                    Text("Trigger CI")
                }
                OutlinedButton(
                    onClick = { viewModel.generateCurrentChangelog() },
                    modifier = Modifier.testTag("generate_changelog_btn")
                ) {
                    Text("Changelog")
                }
                if (ciRun?.htmlUrl?.isNotBlank() == true) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ciRun!!.htmlUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run #${ciRun!!.runId}")
                    }
                }
            }

            if (selfUpdate.openPullRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Open Pull Requests (${selfUpdate.openPullRequests.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                selfUpdate.openPullRequests.take(5).forEach { pr ->
                    Text(
                        text = "#${pr.number} ${pr.title} (${pr.headBranch} -> ${pr.baseBranch})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (changelogMarkdown.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = changelogMarkdown,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("github_generated_changelog_text")
                )
            }

            ciRun?.let { run ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${run.name} • branch ${run.branch} • sha ${run.headSha}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (failedLogTail.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed Job Log (last 30 lines):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 210.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .testTag("ci_failed_log_tail")
                ) {
                    Text(
                        text = failedLogTail,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.askAuraToFixFailedBuild() },
                    enabled = !isAutoFixing && autoFixCount < MAX_AUTO_FIX_ATTEMPTS,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_aura_fix_build_btn")
                ) {
                    if (isAutoFixing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generating Fix Proposal...")
                    } else {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask Aura to fix ($autoFixCount/$MAX_AUTO_FIX_ATTEMPTS)")
                    }
                }
            }

            // Self-Update & Rollback Section
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Self-Update", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                StatusPill(
                    text = if (selfUpdate.isUpdateAvailable) "Update available" else "v${selfUpdate.installedVersionName}",
                    accent = if (selfUpdate.isUpdateAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Installed: v${selfUpdate.installedVersionName} (build ${selfUpdate.installedVersionCode})",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Last working version: ${selfUpdate.lastWorkingVersion.ifBlank { "v${selfUpdate.installedVersionName}" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("last_working_version_text")
            )

            if (selfUpdate.statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selfUpdate.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selfUpdate.isUpdateAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selfUpdate.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selfUpdate.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (selfUpdate.setupBlockers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_setup_blockers_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Setup Blockers (${selfUpdate.setupBlockers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        selfUpdate.setupBlockers.forEach { blocker ->
                            Text(
                                text = "• $blocker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (selfUpdate.needsUnknownSourcesPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Permission required: Enable 'Allow from this source' so Aura AI can install update APKs without uninstalling.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.openUnknownSourcesPermissionSettings() },
                                modifier = Modifier.testTag("allow_unknown_sources_btn")
                            ) {
                                Text("Allow from this source")
                            }
                            OutlinedButton(onClick = { viewModel.refreshInstallPermissionState() }) {
                                Text("I Enabled It")
                            }
                        }
                    }
                }
            }

            if (selfUpdate.isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { selfUpdate.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_download_progress")
                )
                Text(
                    text = "Downloading APK: ${(selfUpdate.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { viewModel.checkForAppUpdate(silent = false) },
                    enabled = !selfUpdate.isChecking && !selfUpdate.isDownloading && config.isConfigured,
                    modifier = Modifier.testTag("check_for_update_btn")
                ) {
                    if (selfUpdate.isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...")
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check for update")
                    }
                }

                if (selfUpdate.isUpdateAvailable && selfUpdate.latestRelease != null) {
                    Button(
                        onClick = { viewModel.downloadAndInstallLatestUpdate() },
                        enabled = !selfUpdate.isDownloading,
                        modifier = Modifier.testTag("download_update_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download (${selfUpdate.latestRelease!!.tagName})")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.rollbackToPreviousRelease() },
                    enabled = !selfUpdate.isDownloading && selfUpdate.previousRelease != null,
                    modifier = Modifier.testTag("rollback_update_btn")
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (selfUpdate.previousRelease != null) {
                            "Rollback (${selfUpdate.previousRelease!!.tagName})"
                        } else {
                            "Rollback"
                        }
                    )
                }
            }

            if (selfUpdate.updateHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_history_list")
                ) {
                    Text(
                        text = "APK Update & Startup Verification History (${selfUpdate.updateHistory.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    selfUpdate.updateHistory.take(6).forEach { entry ->
                        Text(
                            text = "[${entry.status}] ${entry.fromVersion} -> v${entry.toVersion} (build ${entry.toVersionCode}) • ${entry.notes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.status == "VERIFIED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubPushPreviewDialog(
    proposal: PendingGitHubPushProposal,
    isPushing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isPushing) onReject() },
        icon = { Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = if (proposal.isAutoFixProposal) "Review Aura Auto-Fix Push" else "Approve GitHub Push?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Branch: ${proposal.branch} (never main)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Commit: ${proposal.commitMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (proposal.explanation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = proposal.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                proposal.files.forEach { file ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = if (file.isUpdate) "UPDATE" else "CREATE",
                            accent = if (file.isUpdate) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .testTag("github_push_diff_box")
                    ) {
                        file.diffLines.forEach { line ->
                            val bg = when (line.type) {
                                DiffLineType.ADDED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                DiffLineType.REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val fg = when (line.type) {
                                DiffLineType.ADDED -> MaterialTheme.colorScheme.tertiary
                                DiffLineType.REMOVED -> MaterialTheme.colorScheme.error
                                DiffLineType.HEADER -> MaterialTheme.colorScheme.primary
                                DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = fg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                enabled = !isPushing,
                modifier = Modifier.testTag("approve_github_push_btn")
            ) {
                if (isPushing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pushing...")
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                enabled = !isPushing,
                modifier = Modifier.testTag("reject_github_push_btn")
            ) {
                Text("Reject")
            }
        }
    )
}
