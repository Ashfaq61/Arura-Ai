package com.example.aura.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.evolution.AutonomousCycleReport
import com.example.aura.history.BacklogPriorityScore
import com.example.aura.history.FeatureLifecycleStage
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@Composable
fun ZeroQuestionAutonomySection(viewModel: AuraViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastAutonomousReport.collectAsStateWithLifecycle()
    val prioritizedBacklog by viewModel.prioritizedBacklog.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("zero_question_autonomy_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = "Zero-Question Autonomy", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Zero-Question Autonomous Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Discover -> Decide -> Develop -> Test -> Install -> Activate -> Monitor. Safe, reversible tools, agents & minor upgrades run without prompts; sensitive permissions, security, external comms & financial actions always stay gated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                AutonomyToggleRow(
                    label = "No User Interruption Mode (Autonomous Authority)",
                    checked = prefs.zeroQuestionAutonomyEnabled,
                    tag = "toggle_zero_question_mode",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = enabled,
                            autoInstallSafeTools = prefs.autoInstallSafeTools,
                            autoCreateAgents = prefs.autoCreateSpecialistAgents,
                            autoUpgradeAgentsAndTools = prefs.autoUpgradeAgentsAndTools,
                            autoRollbackFaulty = prefs.autoRollbackFaultyFeatures,
                            autoCheckInPlaceUpdates = prefs.autoCheckInPlaceApkUpdates
                        )
                    }
                )
                AutonomyToggleRow(
                    label = "Auto-Synthesize, Verify & Activate Safe Tools",
                    checked = prefs.autoInstallSafeTools,
                    tag = "toggle_auto_install_tools",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = prefs.zeroQuestionAutonomyEnabled,
                            autoInstallSafeTools = enabled,
                            autoCreateAgents = prefs.autoCreateSpecialistAgents,
                            autoUpgradeAgentsAndTools = prefs.autoUpgradeAgentsAndTools,
                            autoRollbackFaulty = prefs.autoRollbackFaultyFeatures,
                            autoCheckInPlaceUpdates = prefs.autoCheckInPlaceApkUpdates
                        )
                    }
                )
                AutonomyToggleRow(
                    label = "Auto-Create, Test & Activate Specialist Agents",
                    checked = prefs.autoCreateSpecialistAgents,
                    tag = "toggle_auto_create_agents",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = prefs.zeroQuestionAutonomyEnabled,
                            autoInstallSafeTools = prefs.autoInstallSafeTools,
                            autoCreateAgents = enabled,
                            autoUpgradeAgentsAndTools = prefs.autoUpgradeAgentsAndTools,
                            autoRollbackFaulty = prefs.autoRollbackFaultyFeatures,
                            autoCheckInPlaceUpdates = prefs.autoCheckInPlaceApkUpdates
                        )
                    }
                )
                AutonomyToggleRow(
                    label = "Auto-Upgrade Outdated Tools & Agent Models",
                    checked = prefs.autoUpgradeAgentsAndTools,
                    tag = "toggle_auto_upgrade_tools",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = prefs.zeroQuestionAutonomyEnabled,
                            autoInstallSafeTools = prefs.autoInstallSafeTools,
                            autoCreateAgents = prefs.autoCreateSpecialistAgents,
                            autoUpgradeAgentsAndTools = enabled,
                            autoRollbackFaulty = prefs.autoRollbackFaultyFeatures,
                            autoCheckInPlaceUpdates = prefs.autoCheckInPlaceApkUpdates
                        )
                    }
                )
                AutonomyToggleRow(
                    label = "Auto-Disable / Rollback Faulty Features",
                    checked = prefs.autoRollbackFaultyFeatures,
                    tag = "toggle_auto_rollback_faulty",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = prefs.zeroQuestionAutonomyEnabled,
                            autoInstallSafeTools = prefs.autoInstallSafeTools,
                            autoCreateAgents = prefs.autoCreateSpecialistAgents,
                            autoUpgradeAgentsAndTools = prefs.autoUpgradeAgentsAndTools,
                            autoRollbackFaulty = enabled,
                            autoCheckInPlaceUpdates = prefs.autoCheckInPlaceApkUpdates
                        )
                    }
                )
                AutonomyToggleRow(
                    label = "Auto-Check Compatible In-Place APK Updates",
                    checked = prefs.autoCheckInPlaceApkUpdates,
                    tag = "toggle_auto_inplace_updates",
                    onCheckedChange = { enabled ->
                        viewModel.updateZeroQuestionAutonomySettings(
                            zeroQuestionEnabled = prefs.zeroQuestionAutonomyEnabled,
                            autoInstallSafeTools = prefs.autoInstallSafeTools,
                            autoCreateAgents = prefs.autoCreateSpecialistAgents,
                            autoUpgradeAgentsAndTools = prefs.autoUpgradeAgentsAndTools,
                            autoRollbackFaulty = prefs.autoRollbackFaultyFeatures,
                            autoCheckInPlaceUpdates = enabled
                        )
                    }
                )
            }
        }

        lastReport?.let { report ->
            AutonomousReportCard(report = report)
        }

        if (prioritizedBacklog.isNotEmpty()) {
            PrioritizedBacklogCard(items = prioritizedBacklog)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrioritizedBacklogCard(items: List<BacklogPriorityScore>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prioritized_backlog_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Prioritized Autonomous Backlog (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            items.take(8).forEach { score ->
                val accent = when (score.stage) {
                    FeatureLifecycleStage.ACTIVE -> MaterialTheme.colorScheme.tertiary
                    FeatureLifecycleStage.BLOCKED, FeatureLifecycleStage.FAILED -> MaterialTheme.colorScheme.error
                    FeatureLifecycleStage.ROLLED_BACK -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${score.priorityTier} • ${score.input.featureName} (${score.compositeScore})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(text = score.stage.label, accent = accent)
                    }
                    Text(
                        text = "${score.input.sourcePlatform} • ${score.breakdown}" +
                            if (score.input.blockReason.isNotBlank()) " • Block: ${score.input.blockReason}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun AutonomyToggleRow(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutonomousReportCard(report: AutonomousCycleReport) {
    val accent = when (report.cycleStatus) {
        "ACTIVATED_VERIFIED" -> MaterialTheme.colorScheme.tertiary
        "ROLLED_BACK" -> MaterialTheme.colorScheme.secondary
        "COMPLETED_WITH_BLOCKERS" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, accent),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("autonomous_cycle_report_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = accent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cycle #${report.cycleId} Outcome", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                StatusPill(text = report.cycleStatus, accent = accent)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(text = "Installed: ${report.toolsInstalledOrActivated.size}", accent = MaterialTheme.colorScheme.tertiary)
                StatusPill(text = "Upgraded: ${report.toolsUpgraded.size}", accent = MaterialTheme.colorScheme.primary)
                StatusPill(text = "Agents: ${report.agentsCreatedOrUpgraded.size}", accent = MaterialTheme.colorScheme.secondary)
                StatusPill(text = "Fixes/Rollbacks: ${report.bugsFixedOrRolledBack.size}", accent = MaterialTheme.colorScheme.primary)
            }

            Text("Decision: ${report.selectedApproachReason}", style = MaterialTheme.typography.bodySmall)
            if (report.toolsInstalledOrActivated.isNotEmpty()) {
                Text("Activated Tools: ${report.toolsInstalledOrActivated.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (report.toolsUpgraded.isNotEmpty()) {
                Text("Upgraded: ${report.toolsUpgraded.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (report.agentsCreatedOrUpgraded.isNotEmpty()) {
                Text("Specialist Agents: ${report.agentsCreatedOrUpgraded.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (report.bugsFixedOrRolledBack.isNotEmpty()) {
                Text("Repairs / Rollbacks: ${report.bugsFixedOrRolledBack.joinToString()}", style = MaterialTheme.typography.labelSmall)
            }
            Text("In-Place APK Update: ${report.nativeApkUpdateStatus}", style = MaterialTheme.typography.labelSmall)

            if (report.exactTechnicalBlockers.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = "Technical Blockers", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exact Technical Blockers Reported:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                report.exactTechnicalBlockers.forEach { blocker ->
                    Text("• $blocker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
