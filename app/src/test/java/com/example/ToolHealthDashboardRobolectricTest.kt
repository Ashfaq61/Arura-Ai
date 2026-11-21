package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.agents.McpRuntimeEngine
import com.example.aura.agents.McpToolDescriptor
import com.example.aura.analytics.McpToolHealthAggregator
import com.example.aura.analytics.McpToolHealthStatus
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.memory.AuditLogEntity
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.MarketplaceToolEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.ui.AuraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToolHealthDashboardRobolectricTest {

    @Test
    fun aggregatorComputes24hStatusSuccessRateAndP95LatencyAccurately() {
        val now = 1_760_000_000_000L
        val hourMs = 60L * 60L * 1000L

        val descriptors = listOf(
            McpToolDescriptor("aura.device_health", "System", "Device telemetry", "{}", safetyLevel = 1, enabled = true),
            McpToolDescriptor("aura.memory_search", "Memory", "Searches memories", "{}", safetyLevel = 1, enabled = true),
            McpToolDescriptor("aura.disabled_tool", "Utility", "Paused tool", "{}", safetyLevel = 2, enabled = false)
        )
        val marketplace = listOf(
            MarketplaceToolEntity(
                id = "MKT-FAULTY",
                name = "Broken Tool",
                category = "Utility",
                version = "1.0.0",
                source = "MARKETPLACE",
                license = "MIT",
                description = "Broken spec",
                requiredPermissions = "None",
                dependencies = "None",
                compatibility = "API 24+",
                securityScan = "PASSED",
                testResults = "FAILED",
                status = "FAULTY",
                mcpSchemaJson = """{"name":"aura.broken_tool","schema":{}}"""
            )
        )
        val auditLogs = listOf(
            // Expired (> 24h ago) - MUST be ignored
            AuditLogEntity(
                actor = "MCP Runtime",
                action = "EXECUTE:aura.device_health",
                safetyLevel = 1,
                outcome = "FAILED",
                details = "LATENCY_HEALTH_PROFILER (999ms): stale run",
                timestamp = now - (25L * hourMs)
            ),
            AuditLogEntity(
                actor = "MCP Runtime",
                action = "EXECUTE:aura.device_health",
                safetyLevel = 1,
                outcome = "ALLOWED",
                details = "LATENCY_HEALTH_PROFILER (10ms): OK",
                timestamp = now - (2L * hourMs)
            ),
            AuditLogEntity(
                actor = "MCP Runtime",
                action = "PROBE:aura.device_health",
                safetyLevel = 1,
                outcome = "ALLOWED",
                details = "LATENCY_HEALTH_PROFILER (30ms): OK",
                timestamp = now - (1L * hourMs)
            ),
            AuditLogEntity(
                actor = "MCP Runtime",
                action = "EXECUTE:aura.memory_search",
                safetyLevel = 1,
                outcome = "ALLOWED",
                details = "MEMORY_LINKER (40ms): OK",
                timestamp = now - (1L * hourMs)
            ),
            AuditLogEntity(
                actor = "MCP Runtime",
                action = "EXECUTE:aura.memory_search",
                safetyLevel = 1,
                outcome = "FAILED",
                details = "INJECTION_BLOCKED (120ms): Blocked",
                timestamp = now - (30L * 60_000L)
            )
        )

        val state = McpToolHealthAggregator.aggregate24h(
            descriptors = descriptors,
            marketplaceTools = marketplace,
            auditLogs = auditLogs,
            nowMs = now
        )

        assertEquals(4, state.totalRegisteredTools)
        assertEquals(2, state.healthyCount)
        assertEquals(1, state.disabledCount)
        assertEquals(1, state.faultyCount)

        // Excludes the 25h-old log: 4 valid calls (3 passed, 1 failed => 75%)
        assertEquals(4, state.totalCalls24h)
        assertEquals(3, state.successfulCalls24h)
        assertEquals(1, state.failedCalls24h)
        assertEquals(75f, state.overallSuccessRatePercent!!, 0.1f)
        assertEquals(50L, state.avgLatencyMs24h)
        assertEquals(120L, state.p95LatencyMs24h)
        assertEquals(24, state.hourlyBuckets.size)

        val deviceTool = state.toolSummaries.first { it.toolSlug == "aura.device_health" }
        assertEquals(McpToolHealthStatus.HEALTHY, deviceTool.healthStatus)
        assertEquals(2, deviceTool.calls24h)
        assertEquals(100f, deviceTool.successRatePercent!!, 0.1f)
        assertEquals(20L, deviceTool.avgLatencyMs)
        assertEquals(10L, deviceTool.minLatencyMs)
        assertEquals(30L, deviceTool.maxLatencyMs)

        val faultyTool = state.toolSummaries.first { it.toolSlug == "aura.broken_tool" }
        assertEquals(McpToolHealthStatus.FAULTY, faultyTool.healthStatus)
    }

    @Test
    fun mcpProbeRecordsReal24hTelemetryAndViewModelSweepPopulatesDashboard() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val memRepo = MemoryRepository(db)
            val agentNet = AgentNetworkManager()
            val runtime = McpRuntimeEngine(
                context = context,
                agentNetwork = agentNet,
                permissionGuard = PermissionGuard(),
                memoryRepository = memRepo,
                deviceControl = DeviceControlEngine(context),
                researchClient = GitHubAndHfResearchClient(),
                privacyRouter = PrivacyDataRouter()
            )
            val firstDescriptor = agentNet.mcpTools.value.first()
            val probeRes = runtime.probeRegisteredDescriptor(firstDescriptor)
            assertTrue(probeRes.success)
            assertTrue(probeRes.latencyMs >= 1L)

            val recent = memRepo.observeMcpTelemetrySince(System.currentTimeMillis() - 86_400_000L).first()
            assertTrue(recent.any { it.action == "PROBE:${firstDescriptor.name}" && it.outcome == "ALLOWED" })
        } finally {
            db.close()
        }
    }

    @Test
    fun viewModelHealthSweepUpdatesToolHealthDashboardStateFlow() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AuraDatabase.closeAndResetForTesting(app)
        val store = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[AuraViewModel::class.java]

            vm.runMcpToolHealthSweep()
            val deadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < deadline &&
                (vm.isRunningToolHealthSweep.value || vm.toolHealthDashboard.value.totalCalls24h == 0)
            ) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50L)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val dash = vm.toolHealthDashboard.value
            assertTrue(dash.totalRegisteredTools >= 8)
            assertTrue(dash.totalCalls24h > 0)
            assertNotNull(dash.overallSuccessRatePercent)
            assertTrue(dash.hourlyBuckets.size == 24)
            assertTrue(dash.toolSummaries.any { it.calls24h > 0 && it.avgLatencyMs >= 1L })
        } finally {
            store.clear()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching { WorkManager.getInstance(app).cancelAllWork() }
            AuraDatabase.closeAndResetForTesting(app)
        }
    }
}
