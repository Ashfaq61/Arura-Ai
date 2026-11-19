package com.example.aura.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aura.agents.AgentNetworkManager
import com.example.aura.ai.ModelRoutingEngine
import com.example.aura.ai.MultiProviderRouter
import com.example.aura.automation.DeviceControlEngine
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.evolution.ContinuousSelfImprovementEngine
import com.example.aura.evolution.SelfHealingEngine
import com.example.aura.evolution.ToolFactoryAndEvolutionEngine
import com.example.aura.github.ApkSelfUpdater
import com.example.aura.github.GitHubSelfUpdateClient
import com.example.aura.history.AgentHistoryRecorder
import com.example.aura.history.FeatureControlPipelineEngine
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AuraDatabase
import com.example.aura.memory.GlobalAgentNetworkRepository
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PreferencesRepository
import com.example.aura.research.GitHubAndHfResearchClient
import com.example.aura.security.AuraKeystoreVault
import com.example.aura.security.GeminiKeyStore
import com.example.aura.security.GitHubKeyStore
import com.example.aura.security.PermissionGuard
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs periodic heartbeat checks, flushes queued offline agent messages,
 * resumes waiting discussions, and triggers bounded live technology watch sweeps.
 * Note: Android Doze / App Standby limits uninterrupted 24/7 execution; for continuous 24/7
 * monitoring, pair with an optional authorized HTTPS Companion Backend.
 */
class AgentNetworkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AuraDatabase.getInstance(applicationContext)
        val prefsRepo = PreferencesRepository(applicationContext)
        val prefs = prefsRepo.preferences.first()
        if (!prefs.backgroundNetworkSyncEnabled) return Result.success()

        val memRepo = MemoryRepository(db)
        val histRepo = AgentHistoryRepository(db)
        val netRepo = GlobalAgentNetworkRepository(db)
        val vault = AuraKeystoreVault(applicationContext)
        val privacyRouter = PrivacyDataRouter()
        val injectionDefender = PromptInjectionDefender()
        val backendGateway = BackendServiceGateway(applicationContext)
        val deviceControl = DeviceControlEngine(applicationContext)
        val researchClient = GitHubAndHfResearchClient()
        val geminiKeyStore = GeminiKeyStore(applicationContext)
        val aiRouter = MultiProviderRouter(
            vault = vault,
            memoryRepository = memRepo,
            privacyRouter = privacyRouter,
            modelRouter = ModelRoutingEngine(),
            geminiKeyStore = geminiKeyStore,
            preferencesRepository = prefsRepo
        )
        val agentNet = AgentNetworkManager()
        val permGuard = PermissionGuard()
        val recorder = AgentHistoryRecorder(histRepo, privacyRouter)
        val trustGuard = AgentTrustSecurityGuard(vault, injectionDefender, privacyRouter, memRepo)
        val netEngine = GlobalAgentNetworkEngine(
            networkRepo = netRepo,
            historyRecorder = recorder,
            trustGuard = trustGuard,
            backendGateway = backendGateway,
            memoryRepo = memRepo,
            aiRouter = aiRouter,
            geminiKeyStore = geminiKeyStore
        )
        val toolFactory = ToolFactoryAndEvolutionEngine(aiRouter, researchClient, memRepo, agentNet, permGuard, deviceControl)
        val pipeline = FeatureControlPipelineEngine(
            historyRepo = histRepo,
            recorder = recorder,
            memoryRepo = memRepo,
            aiRouter = aiRouter,
            agentNetwork = agentNet,
            permissionGuard = permGuard,
            deviceControl = deviceControl,
            toolFactory = toolFactory,
            researchClient = researchClient
        )
        val council = GlobalDiscussionCouncilEngine(
            context = applicationContext,
            networkRepo = netRepo,
            historyRepo = histRepo,
            memoryRepo = memRepo,
            historyRecorder = recorder,
            networkEngine = netEngine,
            pipelineEngine = pipeline,
            trustGuard = trustGuard,
            researchClient = researchClient,
            deviceControl = deviceControl,
            aiRouter = aiRouter
        )

        val selfImprovement = ContinuousSelfImprovementEngine(
            context = applicationContext,
            memoryRepository = memRepo,
            historyRepo = histRepo,
            historyRecorder = recorder,
            agentNetwork = agentNet,
            toolFactory = toolFactory,
            pipelineEngine = pipeline,
            researchClient = researchClient,
            deviceControl = deviceControl,
            selfHealing = SelfHealingEngine(applicationContext),
            permissionGuard = permGuard,
            gitHubClient = GitHubSelfUpdateClient(),
            apkSelfUpdater = ApkSelfUpdater(applicationContext)
        )
        val gitHubConfig = GitHubKeyStore(applicationContext).loadConfig()

        return runCatching {
            netEngine.runHeartbeatAll(prefs)
            council.resumeInterruptedAndQueuedWork(isNetworkOnline = true, prefs = prefs)
            if (prefs.autoCouncilOnNewDiscovery) {
                council.runLiveTechWatchSweep(prefs)
            }
            if (prefs.zeroQuestionAutonomyEnabled) {
                selfImprovement.runAutonomousCycle(
                    focusTopic = "android ai agent tools",
                    prefs = prefs,
                    gitHubConfig = gitHubConfig
                )
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_SYNC_NAME = "aura_global_agent_network_sync"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
            val safeMinutes = intervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<AgentNetworkSyncWorker>(safeMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC_NAME)
        }

        fun triggerImmediateSync(context: Context) {
            val req = OneTimeWorkRequestBuilder<AgentNetworkSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
