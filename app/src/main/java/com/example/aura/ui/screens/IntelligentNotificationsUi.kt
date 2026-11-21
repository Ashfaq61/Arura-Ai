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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.automation.NotificationCategory
import com.example.aura.automation.NotificationPriority
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntelligentNotificationsTab(viewModel: AuraViewModel) {
    val connected by viewModel.notificationConnected.collectAsStateWithLifecycle()
    val digest by viewModel.expansion.notificationDigest.collectAsStateWithLifecycle()
    var filterCat by remember { mutableStateOf<NotificationCategory?>(null) }
    var customReplies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val visibleItems = remember(digest, filterCat) {
        if (filterCat == null) digest.prioritizedItems
        else digest.prioritizedItems.filter { it.category == filterCat }
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
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Intelligent Notification Center", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(
                            text = if (connected) "LISTENER ON" else "OPT-IN NEEDED",
                            accent = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(digest.digestParagraph, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.deviceControl.openNotificationListenerSettings() }) {
                        Text("Open Android Notification Access")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filterCat == null, onClick = { filterCat = null }, label = { Text("All (${digest.totalCount})") })
                        NotificationCategory.entries.forEach { cat ->
                            FilterChip(selected = filterCat == cat, onClick = { filterCat = cat }, label = { Text(cat.label) })
                        }
                    }
                }
            }
        }

        items(visibleItems, key = { it.source.key }) { item ->
            val accent = when (item.priority) {
                NotificationPriority.URGENT -> MaterialTheme.colorScheme.error
                NotificationPriority.HIGH -> MaterialTheme.colorScheme.primary
                NotificationPriority.NORMAL -> MaterialTheme.colorScheme.secondary
                NotificationPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val draftText = customReplies[item.source.key] ?: item.suggestedReplies.firstOrNull().orEmpty()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("[${item.source.appLabel}] ${item.sanitizedTitle}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(text = "${item.priority.label} • ${item.category.label}", accent = accent)
                    }
                    if (item.injectionFlagged) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusPill(text = "UNTRUSTED PROMPT CONTENT SANITIZED", accent = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.sanitizedText, style = MaterialTheme.typography.bodySmall)

                    if (item.suggestedReplies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.suggestedReplies.forEach { reply ->
                                AssistChip(
                                    onClick = { customReplies = customReplies + (item.source.key to reply) },
                                    label = { Text(reply) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { customReplies = customReplies + (item.source.key to it) },
                                label = { Text("Approved Quick Reply") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.expansion.sendApprovedNotificationReply(item, draftText) },
                                enabled = draftText.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send approved reply")
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.category == NotificationCategory.WORK) {
                                OutlinedButton(onClick = {
                                    viewModel.expansion.addProductivityItem("TASK", item.sanitizedTitle, item.sanitizedText, null, "HIGH")
                                }) { Text(item.suggestedActionLabel) }
                            }
                            OutlinedButton(onClick = { viewModel.expansion.dismissNotification(item.source.key) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}
