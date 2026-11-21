package com.example.aura.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.analytics.McpHourlyHealthBucket
import com.example.aura.analytics.McpToolHealthDashboardState
import com.example.aura.analytics.McpToolHealthStatus
import com.example.aura.analytics.McpToolHealthSummary
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val healthTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolHealthDashboardTab(viewModel: AuraViewModel) {
    val state by viewModel.toolHealthDashboard.collectAsStateWithLifecycle()
    val isSweeping by viewModel.isRunningToolHealthSweep.collectAsStateWithLifecycle()
    var statusFilter by remember { mutableStateOf("ALL") }
    var selectedHourIndex by remember { mutableIntStateOf(23) }

    val filteredTools = remember(state.toolSummaries, statusFilter) {
        when (statusFilter) {
            "HEALTHY" -> state.toolSummaries.filter { it.healthStatus == McpToolHealthStatus.HEALTHY }
            "DISABLED" -> state.toolSummaries.filter { it.healthStatus == McpToolHealthStatus.DISABLED }
            "FAULTY" -> state.toolSummaries.filter { it.healthStatus == McpToolHealthStatus.FAULTY }
            "ACTIVE_24H" -> state.toolSummaries.filter { it.calls24h > 0 }
            else -> state.toolSummaries
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("tool_health_dashboard_list")
    ) {
        item {
            ToolHealthOverviewCard(
                state = state,
                isSweeping = isSweeping,
                onRunSweep = { viewModel.runMcpToolHealthSweep() }
            )
        }

        item {
            ToolStatusDistributionCard(
                state = state,
                activeFilter = statusFilter,
                onSelectFilter = { statusFilter = it }
            )
        }

        item {
            Tool24HourTrendChartCard(
                buckets = state.hourlyBuckets,
                totalCalls24h = state.totalCalls24h,
                selectedHourIndex = selectedHourIndex.coerceIn(0, (state.hourlyBuckets.size - 1).coerceAtLeast(0)),
                onSelectHour = { selectedHourIndex = it },
                onRunSweep = { viewModel.runMcpToolHealthSweep() }
            )
        }

        item {
            ToolComparisonBarChartCard(
                tools = filteredTools.take(8)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Registered MCP Tools (${filteredTools.size} / ${state.totalRegisteredTools})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "ALL" to "All (${state.totalRegisteredTools})",
                            "HEALTHY" to "Healthy (${state.healthyCount})",
                            "DISABLED" to "Disabled (${state.disabledCount})",
                            "FAULTY" to "Faulty (${state.faultyCount})",
                            "ACTIVE_24H" to "Active 24h"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = statusFilter == key,
                                onClick = { statusFilter = key },
                                label = { Text(label) },
                                modifier = Modifier.testTag("tool_health_filter_$key")
                            )
                        }
                    }
                }
            }
        }

        if (filteredTools.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Icon(
                            Icons.Filled.MonitorHeart,
                            contentDescription = "No matching MCP tools",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No registered MCP tools match the selected filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(filteredTools, key = { it.toolSlug }) { tool ->
            McpToolHealthDetailCard(
                tool = tool,
                onProbe = { viewModel.probeSingleMcpTool(tool) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolHealthOverviewCard(
    state: McpToolHealthDashboardState,
    isSweeping: Boolean,
    onRunSweep: () -> Unit
) {
    val successText = state.overallSuccessRatePercent?.let { "${it.roundToInt()}%" } ?: "—"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_health_overview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.MonitorHeart,
                        contentDescription = "MCP Tool Health Dashboard",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "MCP Tool Health Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Last 24h status, success rate & latency telemetry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusPill(text = "24H LIVE", accent = MaterialTheme.colorScheme.tertiary)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HealthKpiTile(
                    title = "Registered",
                    value = "${state.totalRegisteredTools}",
                    subtitle = "${state.healthyCount} healthy",
                    accent = MaterialTheme.colorScheme.primary
                )
                HealthKpiTile(
                    title = "24h Success",
                    value = successText,
                    subtitle = "${state.successfulCalls24h}/${state.totalCalls24h} runs",
                    accent = MaterialTheme.colorScheme.tertiary
                )
                HealthKpiTile(
                    title = "Mean Latency",
                    value = "${state.avgLatencyMs24h}ms",
                    subtitle = "P95 ${state.p95LatencyMs24h}ms",
                    accent = MaterialTheme.colorScheme.secondary
                )
                HealthKpiTile(
                    title = "Alerts",
                    value = "${state.faultyCount + state.disabledCount}",
                    subtitle = "${state.faultyCount} faulty • ${state.disabledCount} off",
                    accent = if (state.faultyCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }

            if (isSweeping) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = onRunSweep,
                enabled = !isSweeping,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("run_tool_health_sweep_btn")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSweeping) "Running 24h Health Sweep..." else "Run 24h MCP Health Sweep")
            }
        }
    }
}

@Composable
private fun HealthKpiTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolStatusDistributionCard(
    state: McpToolHealthDashboardState,
    activeFilter: String,
    onSelectFilter: (String) -> Unit
) {
    val healthyColor = MaterialTheme.colorScheme.tertiary
    val disabledColor = MaterialTheme.colorScheme.secondary
    val faultyColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = max(1, state.totalRegisteredTools).toFloat()

    val healthySweep by animateFloatAsState(
        targetValue = (state.healthyCount / total) * 360f,
        animationSpec = tween(durationMillis = 280),
        label = "healthy_arc"
    )
    val disabledSweep by animateFloatAsState(
        targetValue = (state.disabledCount / total) * 360f,
        animationSpec = tween(durationMillis = 280),
        label = "disabled_arc"
    )
    val faultySweep by animateFloatAsState(
        targetValue = (state.faultyCount / total) * 360f,
        animationSpec = tween(durationMillis = 280),
        label = "faulty_arc"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_status_donut_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = healthyColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registered Tool Status Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(116.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 16.dp.toPx()
                        val pad = strokePx / 2f
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = arcSize,
                            style = Stroke(width = strokePx)
                        )
                        var startAngle = -90f
                        val segments = listOf(
                            healthySweep to healthyColor,
                            disabledSweep to disabledColor,
                            faultySweep to faultyColor
                        )
                        segments.forEach { (sweep, color) ->
                            if (sweep > 0.5f) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(pad, pad),
                                    size = arcSize,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.totalRegisteredTools}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tools",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    DonutLegendRow(
                        label = "Healthy & Active",
                        count = state.healthyCount,
                        color = healthyColor,
                        selected = activeFilter == "HEALTHY",
                        onClick = { onSelectFilter(if (activeFilter == "HEALTHY") "ALL" else "HEALTHY") }
                    )
                    DonutLegendRow(
                        label = "Disabled / Gated",
                        count = state.disabledCount,
                        color = disabledColor,
                        selected = activeFilter == "DISABLED",
                        onClick = { onSelectFilter(if (activeFilter == "DISABLED") "ALL" else "DISABLED") }
                    )
                    DonutLegendRow(
                        label = "Faulty / Failing",
                        count = state.faultyCount,
                        color = faultyColor,
                        selected = activeFilter == "FAULTY",
                        onClick = { onSelectFilter(if (activeFilter == "FAULTY") "ALL" else "FAULTY") }
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutLegendRow(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (selected) color else Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun Tool24HourTrendChartCard(
    buckets: List<McpHourlyHealthBucket>,
    totalCalls24h: Int,
    selectedHourIndex: Int,
    onSelectHour: (Int) -> Unit,
    onRunSweep: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    val maxLatency = remember(buckets) {
        max(10f, buckets.maxOfOrNull { max(it.avgLatencyMs, it.maxLatencyMs.toFloat()) } ?: 10f)
    }
    val maxCalls = remember(buckets) {
        max(1, buckets.maxOfOrNull { it.totalCalls } ?: 1)
    }
    val selectedBucket = buckets.getOrNull(selectedHourIndex)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_24h_trend_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("24-Hour Latency & Success Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text("Peak ${maxLatency.roundToInt()}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (totalCalls24h == 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = primaryColor)
                        Text(
                            "No MCP executions or health probes recorded in the last 24 hours yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onRunSweep,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Capture Baseline Telemetry")
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .pointerInput(buckets.size) {
                                detectTapGestures { offset ->
                                    if (buckets.isNotEmpty() && size.width > 0) {
                                        val idx = ((offset.x / size.width.toFloat()) * buckets.size)
                                            .toInt()
                                            .coerceIn(0, buckets.lastIndex)
                                        onSelectHour(idx)
                                    }
                                }
                            }
                            .testTag("tool_24h_trend_canvas")
                    ) {
                        if (buckets.isEmpty()) return@Canvas
                        val topPad = 12.dp.toPx()
                        val bottomPad = 12.dp.toPx()
                        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                        val stepX = size.width / buckets.size.toFloat()

                        // Dashed gridlines (0%, 50%, 100%)
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        listOf(0f, 0.5f, 1f).forEach { frac ->
                            val y = topPad + chartH * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        // Highlight selected hour column
                        if (selectedHourIndex in buckets.indices) {
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(selectedHourIndex * stepX, topPad),
                                size = Size(stepX, chartH)
                            )
                        }

                        // Volume + Success color bars along bottom 35% of chart
                        val points = mutableListOf<Offset>()
                        buckets.forEachIndexed { idx, bucket ->
                            val centerX = idx * stepX + stepX / 2f
                            if (bucket.totalCalls > 0) {
                                val barMaxH = chartH * 0.34f
                                val barH = (bucket.totalCalls.toFloat() / maxCalls.toFloat()) * barMaxH
                                val barColor = when {
                                    bucket.successRatePercent >= 90f -> tertiaryColor
                                    bucket.successRatePercent >= 60f -> secondaryColor
                                    else -> errorColor
                                }
                                drawRoundRect(
                                    color = barColor.copy(alpha = 0.72f),
                                    topLeft = Offset(centerX - stepX * 0.28f, topPad + chartH - barH),
                                    size = Size((stepX * 0.56f).coerceAtLeast(3f), barH),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }
                            val latNorm = (bucket.avgLatencyMs / maxLatency).coerceIn(0f, 1f)
                            val latY = topPad + chartH - (latNorm * chartH)
                            points += Offset(centerX, latY)
                        }

                        // Area fill + smooth line for Hourly Avg Latency
                        if (points.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    val prev = points[i - 1]
                                    val curr = points[i]
                                    val midX = (prev.x + curr.x) / 2f
                                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                                }
                            }
                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, topPad + chartH)
                                lineTo(points.first().x, topPad + chartH)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.28f),
                                        primaryColor.copy(alpha = 0.02f)
                                    ),
                                    startY = topPad,
                                    endY = topPad + chartH
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = primaryColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        points.forEachIndexed { idx, pt ->
                            if (buckets[idx].totalCalls > 0 || idx == selectedHourIndex) {
                                drawCircle(
                                    color = if (idx == selectedHourIndex) tertiaryColor else primaryColor,
                                    radius = if (idx == selectedHourIndex) 5.dp.toPx() else 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // Hour axis legend (Start / -12h / -6h / Now)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 8, 16, 23).forEach { i ->
                        Text(
                            text = buckets.getOrNull(i)?.hourLabel.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive Scrubber Tooltip Strip
                AnimatedVisibility(visible = selectedBucket != null) {
                    selectedBucket?.let { b ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Hour ${b.hourLabel}: ${b.totalCalls} calls (${b.successCalls} ok, ${b.failedCalls} fail)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (b.totalCalls > 0) "${b.successRatePercent.roundToInt()}% • Avg ${b.avgLatencyMs.roundToInt()}ms" else "No calls",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolComparisonBarChartCard(tools: List<McpToolHealthSummary>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxToolLatency = remember(tools) {
        max(10L, tools.maxOfOrNull { it.avgLatencyMs } ?: 10L)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_comparison_bar_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Per-Tool Success Rate & Latency Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (tools.isEmpty()) {
                Text(
                    "No tools available for comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tools.forEach { tool ->
                    val rateFrac = (tool.successRatePercent ?: 0f) / 100f
                    val latFrac = if (tool.avgLatencyMs <= 0L) 0f else (tool.avgLatencyMs.toFloat() / maxToolLatency.toFloat()).coerceIn(0.05f, 1f)
                    val rateColor = when {
                        tool.successRatePercent == null -> MaterialTheme.colorScheme.outline
                        tool.successRatePercent >= 90f -> tertiaryColor
                        tool.successRatePercent >= 60f -> MaterialTheme.colorScheme.secondary
                        else -> errorColor
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tool.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${tool.successRatePercent?.let { "${it.roundToInt()}%" } ?: "—"} • ${tool.avgLatencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                            val halfH = 4.dp.toPx()
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width, halfH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            if (rateFrac > 0f) {
                                drawRoundRect(
                                    color = rateColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width * rateFrac.coerceIn(0f, 1f), halfH),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }
                            val secondTop = halfH + 3.dp.toPx()
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(0f, secondTop),
                                size = Size(size.width, halfH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            if (latFrac > 0f) {
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(0f, secondTop),
                                    size = Size(size.width * latFrac, halfH),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpToolHealthDetailCard(
    tool: McpToolHealthSummary,
    onProbe: () -> Unit
) {
    val accent = when (tool.healthStatus) {
        McpToolHealthStatus.HEALTHY -> MaterialTheme.colorScheme.tertiary
        McpToolHealthStatus.DISABLED -> MaterialTheme.colorScheme.secondary
        McpToolHealthStatus.FAULTY -> MaterialTheme.colorScheme.error
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_health_card_${tool.toolSlug}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${tool.toolSlug} • ${tool.category} • L${tool.safetyLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(text = tool.statusLabel.uppercase(Locale.US), accent = accent)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    val rateLabel = tool.successRatePercent?.let { "${it.roundToInt()}% (${tool.successCalls24h}/${tool.calls24h})" } ?: "No runs in 24h"
                    Text("24h Success Rate: $rateLabel", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Latency: avg ${tool.avgLatencyMs}ms • min ${tool.minLatencyMs}ms • max ${tool.maxLatencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val lastTs = tool.lastRunTimestamp?.let { healthTimeFmt.format(Date(it)) } ?: "Never"
                    Text(
                        "Last Run: $lastTs (${tool.lastOutcome} • ${tool.lastOperation})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tool.lastOutcome == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                if (tool.canRunLiveProbe) {
                    OutlinedButton(
                        onClick = onProbe,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("probe_tool_${tool.toolSlug}")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Probe ${tool.displayName}")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probe")
                    }
                }
            }
        }
    }
}
