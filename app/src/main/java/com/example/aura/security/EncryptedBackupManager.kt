package com.example.aura.security

import android.util.Base64
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AgentHistoryRepository
import com.example.aura.memory.AppCompatibilityProfileEntity
import com.example.aura.memory.AutomationRecipeEntity
import com.example.aura.memory.CustomAgentProfileEntity
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.FeatureRequestEntity
import com.example.aura.memory.KnowledgeEdgeEntity
import com.example.aura.memory.KnowledgeNodeEntity
import com.example.aura.memory.MemoryEntryEntity
import com.example.aura.memory.MemoryRepository
import com.example.aura.memory.PendingChangeProposalEntity
import com.example.aura.memory.ProductivityItemEntity
import com.example.aura.memory.RoutineEntity
import com.example.aura.memory.VisualWorkflowEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupExportOptions(
    val includeMemories: Boolean = true,
    val includeRoutines: Boolean = true,
    val includeWorkflows: Boolean = true,
    val includeKnowledgeGraph: Boolean = true,
    val includeRecipes: Boolean = true,
    val includeProductivity: Boolean = true,
    val includeCompatibilityProfiles: Boolean = true,
    val includeAgentHistoryAndControl: Boolean = true
)

data class BackupRestoreSummary(
    val memoriesRestored: Int,
    val routinesRestored: Int,
    val workflowsRestored: Int,
    val knowledgeNodesRestored: Int,
    val recipesRestored: Int,
    val productivityRestored: Int,
    val compatRestored: Int,
    val agentHistoryRecordsRestored: Int = 0
)

/**
 * AES-256-GCM passphrase-encrypted backup and restore manager.
 * Guarantees zero credentials/API keys/GitHub PATs ever leave the Keystore vault.
 */
class EncryptedBackupManager(
    private val memoryRepo: MemoryRepository,
    private val advancedRepo: AdvancedRepository,
    private val historyRepo: AgentHistoryRepository? = null
) {
    suspend fun createEncryptedBackup(passphrase: String, options: BackupExportOptions = BackupExportOptions()): Result<String> {
        if (passphrase.length < 6) {
            return Result.failure(IllegalArgumentException("Passphrase must be at least 6 characters."))
        }
        return runCatching {
            val plainPayload = buildSanitizedPayloadJson(options)
            check(!containsForbiddenSecrets(plainPayload)) {
                "Security check blocked backup: forbidden secret material detected in payload."
            }
            encryptWithPassphrase(passphrase, plainPayload)
        }
    }

    suspend fun buildSanitizedPayloadJson(options: BackupExportOptions): String {
        val root = JSONObject().apply {
            put("format", "aura_encrypted_backup_v1")
            put("exportedAt", System.currentTimeMillis())
            put("credentialsIncluded", false)
        }

        if (options.includeMemories) {
            val arr = JSONArray()
            memoryRepo.getRecentMemories(500).forEach { m ->
                if (!m.isSensitive) {
                    arr.put(JSONObject().apply {
                        put("category", m.category); put("title", m.title)
                        put("content", m.content); put("language", m.language)
                        put("updatedAt", m.updatedAt)
                    })
                }
            }
            root.put("memories", arr)
        }
        if (options.includeRoutines) {
            val arr = JSONArray()
            advancedRepo.getAllRoutines().forEach { r ->
                arr.put(JSONObject().apply {
                    put("name", r.name); put("triggerType", r.triggerType)
                    put("triggerConfig", r.triggerConfig); put("stepsJson", r.stepsJson)
                    put("enabled", r.enabled); put("scheduleHours", r.scheduleHours)
                })
            }
            root.put("routines", arr)
        }
        if (options.includeWorkflows) {
            val arr = JSONArray()
            advancedRepo.getAllWorkflows().forEach { w ->
                arr.put(JSONObject().apply {
                    put("name", w.name); put("description", w.description)
                    put("variablesJson", w.variablesJson); put("nodesJson", w.nodesJson)
                })
            }
            root.put("workflows", arr)
        }
        if (options.includeKnowledgeGraph) {
            val nodesArr = JSONArray()
            advancedRepo.getAllKnowledgeNodes().forEach { n ->
                nodesArr.put(JSONObject().apply {
                    put("id", n.id); put("nodeType", n.nodeType); put("title", n.title)
                    put("summary", n.summary); put("tagsCsv", n.tagsCsv); put("sourceRef", n.sourceRef)
                })
            }
            val edgesArr = JSONArray()
            advancedRepo.getAllKnowledgeEdges().forEach { e ->
                edgesArr.put(JSONObject().apply {
                    put("fromNodeId", e.fromNodeId); put("toNodeId", e.toNodeId)
                    put("relationType", e.relationType); put("weight", e.weight.toDouble())
                })
            }
            root.put("knowledgeNodes", nodesArr)
            root.put("knowledgeEdges", edgesArr)
        }
        if (options.includeRecipes) {
            val arr = JSONArray()
            advancedRepo.getAllRecipes().forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("category", r.category)
                    put("version", r.version); put("targetPackage", r.targetPackage)
                    put("minAppVersion", r.minAppVersion); put("description", r.description)
                    put("stepsJson", r.stepsJson)
                })
            }
            root.put("recipes", arr)
        }
        if (options.includeProductivity) {
            val arr = JSONArray()
            advancedRepo.getAllProductivityItems().forEach { p ->
                arr.put(JSONObject().apply {
                    put("itemType", p.itemType); put("title", p.title); put("details", p.details)
                    put("dueAtMillis", p.dueAtMillis ?: JSONObject.NULL); put("priority", p.priority)
                    put("completed", p.completed)
                })
            }
            root.put("productivity", arr)
        }
        if (options.includeCompatibilityProfiles) {
            val arr = JSONArray()
            advancedRepo.getAllCompatProfiles().forEach { c ->
                arr.put(JSONObject().apply {
                    put("packageName", c.packageName); put("appLabel", c.appLabel)
                    put("knownVersionName", c.knownVersionName); put("knownVersionCode", c.knownVersionCode)
                    put("installedVersionName", c.installedVersionName); put("installedVersionCode", c.installedVersionCode)
                    put("uiLayoutHash", c.uiLayoutHash); put("compatibilityStatus", c.compatibilityStatus)
                    put("supportedActionsCsv", c.supportedActionsCsv); put("driftNotes", c.driftNotes)
                })
            }
            root.put("compatibilityProfiles", arr)
        }
        if (options.includeAgentHistoryAndControl && historyRepo != null) {
            val convArr = JSONArray()
            historyRepo.getRecentConversations(500).forEach { c ->
                convArr.put(JSONObject().apply {
                    put("conversationId", c.conversationId)
                    put("sessionId", c.sessionId)
                    put("agentId", c.agentId)
                    put("agentName", c.agentName)
                    put("participatingAgentsCsv", c.participatingAgentsCsv)
                    put("channelType", c.channelType)
                    put("topic", c.topic)
                    put("userOriginalRequest", c.userOriginalRequest)
                    put("qaExchangedJson", c.qaExchangedJson)
                    put("recommendationsMade", c.recommendationsMade)
                    put("researchFindings", c.researchFindings)
                    put("repositoryAndModelLinksCsv", c.repositoryAndModelLinksCsv)
                    put("decisionsAndReasons", c.decisionsAndReasons)
                    put("proposedFeaturesOrToolsCsv", c.proposedFeaturesOrToolsCsv)
                    put("actionsPerformedCsv", c.actionsPerformedCsv)
                    put("testResults", c.testResults)
                    put("finalOutcome", c.finalOutcome)
                    put("timestamp", c.timestamp)
                })
            }
            root.put("agentConversations", convArr)

            val agentsArr = JSONArray()
            historyRepo.getCustomAgents().forEach { a ->
                agentsArr.put(JSONObject().apply {
                    put("codeName", a.codeName); put("numericId", a.numericId)
                    put("name", a.name); put("roleDescription", a.roleDescription)
                    put("capabilitiesCsv", a.capabilitiesCsv)
                    put("preferredProviderId", a.preferredProviderId)
                    put("preferredModelId", a.preferredModelId)
                    put("createdFromRequestId", a.createdFromRequestId)
                    put("isCustom", a.isCustom); put("enabled", a.enabled)
                })
            }
            root.put("customAgents", agentsArr)

            val regArr = JSONArray()
            historyRepo.getAllRegistryHistory().forEach { r ->
                regArr.put(JSONObject().apply {
                    put("featureId", r.featureId); put("featureName", r.featureName)
                    put("description", r.description); put("discoverySource", r.discoverySource)
                    put("sourceUrl", r.sourceUrl); put("recommendingAgent", r.recommendingAgent)
                    put("participatingAgentsCsv", r.participatingAgentsCsv)
                    put("discoveredAt", r.discoveredAt); put("version", r.version)
                    put("previousVersion", r.previousVersion ?: JSONObject.NULL)
                    put("changeEventType", r.changeEventType); put("dependencies", r.dependencies)
                    put("permissionsRequired", r.permissionsRequired)
                    put("installationDate", r.installationDate ?: JSONObject.NULL)
                    put("testResults", r.testResults); put("activationStatus", r.activationStatus)
                    put("decisionReason", r.decisionReason); put("versionDiffSummary", r.versionDiffSummary)
                    put("rollbackSnapshotJson", r.rollbackSnapshotJson); put("timestamp", r.timestamp)
                })
            }
            root.put("featureRegistryHistory", regArr)

            val reqArr = JSONArray()
            historyRepo.getAllFeatureRequests().forEach { r ->
                reqArr.put(JSONObject().apply {
                    put("requestId", r.requestId); put("userPrompt", r.userPrompt)
                    put("requestMode", r.requestMode); put("linkedSourceId", r.linkedSourceId)
                    put("assignedAgentsCsv", r.assignedAgentsCsv); put("currentStage", r.currentStage)
                    put("stageName", r.stageName); put("stageHistoryJson", r.stageHistoryJson)
                    put("consultationSummary", r.consultationSummary); put("technicalPlan", r.technicalPlan)
                    put("alternativesSummary", r.alternativesSummary); put("compatibilityReport", r.compatibilityReport)
                    put("securityReport", r.securityReport); put("requiredApisAndPerms", r.requiredApisAndPerms)
                    put("testReport", r.testReport); put("status", r.status); put("failureReason", r.failureReason)
                    put("resultFeatureId", r.resultFeatureId)
                })
            }
            root.put("featureRequests", reqArr)

            val recArr = JSONArray()
            historyRepo.getAllRecommendations().forEach { rec ->
                recArr.put(JSONObject().apply {
                    put("id", rec.id); put("featureName", rec.featureName)
                    put("summaryWhatItDoes", rec.summaryWhatItDoes); put("whyAuraNeedsIt", rec.whyAuraNeedsIt)
                    put("limitationSolved", rec.limitationSolved); put("discoveringAgent", rec.discoveringAgent)
                    put("otherAgentOpinionsJson", rec.otherAgentOpinionsJson)
                    put("sourceLinksCsv", rec.sourceLinksCsv); put("securityAssessment", rec.securityAssessment)
                    put("compatibilityAssessment", rec.compatibilityAssessment)
                    put("developmentEffort", rec.developmentEffort); put("costResourceImpact", rec.costResourceImpact)
                    put("testRequirements", rec.testRequirements); put("inboxStatus", rec.inboxStatus)
                    put("agentFollowUpNotes", rec.agentFollowUpNotes); put("linkedRequestId", rec.linkedRequestId)
                })
            }
            root.put("featureRecommendations", recArr)

            val propArr = JSONArray()
            historyRepo.getAllProposals().forEach { p ->
                propArr.put(JSONObject().apply {
                    put("id", p.id); put("requestId", p.requestId); put("featureId", p.featureId)
                    put("title", p.title); put("changeType", p.changeType); put("summary", p.summary)
                    put("targetVersion", p.targetVersion); put("previousVersion", p.previousVersion ?: JSONObject.NULL)
                    put("versionDiff", p.versionDiff); put("testReport", p.testReport)
                    put("securityAssessment", p.securityAssessment)
                    put("requiresExplicitApproval", p.requiresExplicitApproval)
                    put("approvalReason", p.approvalReason)
                    put("previousStateSnapshotJson", p.previousStateSnapshotJson)
                    put("status", p.status); put("modificationNotes", p.modificationNotes)
                })
            }
            root.put("pendingChangeProposals", propArr)
        }
        return root.toString()
    }

    suspend fun restoreFromEncryptedBackup(passphrase: String, encryptedEnvelopeJson: String): Result<BackupRestoreSummary> {
        if (passphrase.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter backup passphrase."))
        }
        return runCatching {
            val plainJson = decryptWithPassphrase(passphrase, encryptedEnvelopeJson)
            val root = JSONObject(plainJson)
            require(root.optString("format").startsWith("aura_")) { "Unrecognized backup format." }

            var memCount = 0
            root.optJSONArray("memories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    memoryRepo.saveMemory(
                        MemoryEntryEntity(
                            category = o.optString("category", "NOTE"),
                            title = o.optString("title"),
                            content = o.optString("content"),
                            language = o.optString("language", "EN")
                        )
                    )
                    memCount++
                }
            }

            var routineCount = 0
            root.optJSONArray("routines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    advancedRepo.upsertRoutine(
                        RoutineEntity(
                            name = o.optString("name"),
                            triggerType = o.optString("triggerType", "MANUAL"),
                            triggerConfig = o.optString("triggerConfig", ""),
                            stepsJson = o.optString("stepsJson", "[]"),
                            enabled = o.optBoolean("enabled", true),
                            scheduleHours = o.optInt("scheduleHours", 0)
                        )
                    )
                    routineCount++
                }
            }

            var workflowCount = 0
            root.optJSONArray("workflows")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    advancedRepo.upsertWorkflow(
                        VisualWorkflowEntity(
                            name = o.optString("name"),
                            description = o.optString("description"),
                            variablesJson = o.optString("variablesJson", "{}"),
                            nodesJson = o.optString("nodesJson", "[]")
                        )
                    )
                    workflowCount++
                }
            }

            var kgCount = 0
            val nodeIdRemap = mutableMapOf<Long, Long>()
            root.optJSONArray("knowledgeNodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val oldId = o.optLong("id", -1L)
                    val newId = advancedRepo.upsertKnowledgeNode(
                        KnowledgeNodeEntity(
                            nodeType = o.optString("nodeType", "NOTE"),
                            title = o.optString("title"),
                            summary = o.optString("summary"),
                            tagsCsv = o.optString("tagsCsv", ""),
                            sourceRef = o.optString("sourceRef", "")
                        )
                    )
                    if (oldId > 0) nodeIdRemap[oldId] = newId
                    kgCount++
                }
            }
            root.optJSONArray("knowledgeEdges")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val fromId = nodeIdRemap[o.optLong("fromNodeId")] ?: continue
                    val toId = nodeIdRemap[o.optLong("toNodeId")] ?: continue
                    advancedRepo.upsertKnowledgeEdge(
                        KnowledgeEdgeEntity(
                            fromNodeId = fromId,
                            toNodeId = toId,
                            relationType = o.optString("relationType", "RELATED_TO"),
                            weight = o.optDouble("weight", 1.0).toFloat()
                        )
                    )
                }
            }

            var recipeCount = 0
            root.optJSONArray("recipes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    advancedRepo.upsertRecipe(
                        AutomationRecipeEntity(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            category = o.optString("category", "General"),
                            version = o.optString("version", "1.0.0"),
                            targetPackage = o.optString("targetPackage", "android"),
                            minAppVersion = o.optString("minAppVersion", "1.0"),
                            description = o.optString("description", ""),
                            stepsJson = o.optString("stepsJson", "[]")
                        )
                    )
                    recipeCount++
                }
            }

            var prodCount = 0
            root.optJSONArray("productivity")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    advancedRepo.upsertProductivityItem(
                        ProductivityItemEntity(
                            itemType = o.optString("itemType", "TASK"),
                            title = o.optString("title"),
                            details = o.optString("details", ""),
                            dueAtMillis = if (o.isNull("dueAtMillis")) null else o.optLong("dueAtMillis"),
                            priority = o.optString("priority", "MEDIUM"),
                            completed = o.optBoolean("completed", false)
                        )
                    )
                    prodCount++
                }
            }

            var compatCount = 0
            root.optJSONArray("compatibilityProfiles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    advancedRepo.upsertCompatProfile(
                        AppCompatibilityProfileEntity(
                            packageName = o.optString("packageName"),
                            appLabel = o.optString("appLabel"),
                            knownVersionName = o.optString("knownVersionName"),
                            knownVersionCode = o.optLong("knownVersionCode"),
                            installedVersionName = o.optString("installedVersionName"),
                            installedVersionCode = o.optLong("installedVersionCode"),
                            uiLayoutHash = o.optString("uiLayoutHash", ""),
                            compatibilityStatus = o.optString("compatibilityStatus", "COMPATIBLE"),
                            supportedActionsCsv = o.optString("supportedActionsCsv", ""),
                            driftNotes = o.optString("driftNotes", "")
                        )
                    )
                    compatCount++
                }
            }

            var historyCount = 0
            if (historyRepo != null) {
                root.optJSONArray("agentConversations")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.insertConversation(
                            AgentConversationEntity(
                                conversationId = o.optString("conversationId"),
                                sessionId = o.optString("sessionId"),
                                agentId = o.optString("agentId"),
                                agentName = o.optString("agentName"),
                                participatingAgentsCsv = o.optString("participatingAgentsCsv"),
                                channelType = o.optString("channelType", "BRAIN_TO_AGENT"),
                                topic = o.optString("topic"),
                                userOriginalRequest = o.optString("userOriginalRequest"),
                                qaExchangedJson = o.optString("qaExchangedJson", "[]"),
                                recommendationsMade = o.optString("recommendationsMade"),
                                researchFindings = o.optString("researchFindings"),
                                repositoryAndModelLinksCsv = o.optString("repositoryAndModelLinksCsv"),
                                decisionsAndReasons = o.optString("decisionsAndReasons"),
                                proposedFeaturesOrToolsCsv = o.optString("proposedFeaturesOrToolsCsv"),
                                actionsPerformedCsv = o.optString("actionsPerformedCsv"),
                                testResults = o.optString("testResults"),
                                finalOutcome = o.optString("finalOutcome"),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                        historyCount++
                    }
                }
                root.optJSONArray("customAgents")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.upsertCustomAgent(
                            CustomAgentProfileEntity(
                                codeName = o.getString("codeName"),
                                numericId = o.optInt("numericId", 25 + i),
                                name = o.getString("name"),
                                roleDescription = o.optString("roleDescription"),
                                capabilitiesCsv = o.optString("capabilitiesCsv"),
                                preferredProviderId = o.optString("preferredProviderId", "FIREBASE_GEMINI"),
                                preferredModelId = o.optString("preferredModelId", "gemini-3.8-flash"),
                                createdFromRequestId = o.optString("createdFromRequestId"),
                                isCustom = o.optBoolean("isCustom", true),
                                enabled = o.optBoolean("enabled", true)
                            )
                        )
                        historyCount++
                    }
                }
                root.optJSONArray("featureRegistryHistory")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.recordRegistryEvent(
                            FeatureRegistryHistoryEntity(
                                featureId = o.getString("featureId"),
                                featureName = o.getString("featureName"),
                                description = o.optString("description"),
                                discoverySource = o.optString("discoverySource"),
                                sourceUrl = o.optString("sourceUrl"),
                                recommendingAgent = o.optString("recommendingAgent"),
                                participatingAgentsCsv = o.optString("participatingAgentsCsv"),
                                discoveredAt = o.optLong("discoveredAt", System.currentTimeMillis()),
                                version = o.optString("version", "1.0.0"),
                                previousVersion = if (o.isNull("previousVersion")) null else o.optString("previousVersion"),
                                changeEventType = o.optString("changeEventType", "DISCOVERED"),
                                dependencies = o.optString("dependencies"),
                                permissionsRequired = o.optString("permissionsRequired"),
                                installationDate = if (o.isNull("installationDate")) null else o.optLong("installationDate"),
                                testResults = o.optString("testResults"),
                                activationStatus = o.optString("activationStatus", "DISCOVERED_ONLY"),
                                decisionReason = o.optString("decisionReason"),
                                versionDiffSummary = o.optString("versionDiffSummary"),
                                rollbackSnapshotJson = o.optString("rollbackSnapshotJson", "{}"),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                        historyCount++
                    }
                }
                root.optJSONArray("featureRequests")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.upsertFeatureRequest(
                            FeatureRequestEntity(
                                requestId = o.getString("requestId"),
                                userPrompt = o.optString("userPrompt"),
                                requestMode = o.optString("requestMode", "NATURAL"),
                                linkedSourceId = o.optString("linkedSourceId"),
                                assignedAgentsCsv = o.optString("assignedAgentsCsv"),
                                currentStage = o.optInt("currentStage", 1),
                                stageName = o.optString("stageName", "SAVED_REQUEST"),
                                stageHistoryJson = o.optString("stageHistoryJson", "[]"),
                                consultationSummary = o.optString("consultationSummary"),
                                technicalPlan = o.optString("technicalPlan"),
                                alternativesSummary = o.optString("alternativesSummary"),
                                compatibilityReport = o.optString("compatibilityReport"),
                                securityReport = o.optString("securityReport"),
                                requiredApisAndPerms = o.optString("requiredApisAndPerms"),
                                testReport = o.optString("testReport"),
                                status = o.optString("status", "IN_PROGRESS"),
                                failureReason = o.optString("failureReason"),
                                resultFeatureId = o.optString("resultFeatureId")
                            )
                        )
                        historyCount++
                    }
                }
                root.optJSONArray("featureRecommendations")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.upsertRecommendation(
                            FeatureRecommendationEntity(
                                id = o.getString("id"),
                                featureName = o.optString("featureName"),
                                summaryWhatItDoes = o.optString("summaryWhatItDoes"),
                                whyAuraNeedsIt = o.optString("whyAuraNeedsIt"),
                                limitationSolved = o.optString("limitationSolved"),
                                discoveringAgent = o.optString("discoveringAgent"),
                                otherAgentOpinionsJson = o.optString("otherAgentOpinionsJson", "[]"),
                                sourceLinksCsv = o.optString("sourceLinksCsv"),
                                securityAssessment = o.optString("securityAssessment"),
                                compatibilityAssessment = o.optString("compatibilityAssessment"),
                                developmentEffort = o.optString("developmentEffort", "MEDIUM"),
                                costResourceImpact = o.optString("costResourceImpact"),
                                testRequirements = o.optString("testRequirements"),
                                inboxStatus = o.optString("inboxStatus", "NEW"),
                                agentFollowUpNotes = o.optString("agentFollowUpNotes"),
                                linkedRequestId = o.optString("linkedRequestId")
                            )
                        )
                        historyCount++
                    }
                }
                root.optJSONArray("pendingChangeProposals")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        historyRepo.upsertProposal(
                            PendingChangeProposalEntity(
                                id = o.getString("id"),
                                requestId = o.optString("requestId"),
                                featureId = o.optString("featureId"),
                                title = o.optString("title"),
                                changeType = o.optString("changeType"),
                                summary = o.optString("summary"),
                                targetVersion = o.optString("targetVersion"),
                                previousVersion = if (o.isNull("previousVersion")) null else o.optString("previousVersion"),
                                versionDiff = o.optString("versionDiff"),
                                testReport = o.optString("testReport"),
                                securityAssessment = o.optString("securityAssessment"),
                                requiresExplicitApproval = o.optBoolean("requiresExplicitApproval", true),
                                approvalReason = o.optString("approvalReason"),
                                previousStateSnapshotJson = o.optString("previousStateSnapshotJson", "{}"),
                                status = o.optString("status", "PENDING"),
                                modificationNotes = o.optString("modificationNotes")
                            )
                        )
                        historyCount++
                    }
                }
            }

            BackupRestoreSummary(memCount, routineCount, workflowCount, kgCount, recipeCount, prodCount, compatCount, historyCount)
        }
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 100_000
        private const val GCM_TAG_BITS = 128

        fun containsForbiddenSecrets(payload: String): Boolean {
            return payload.contains("AIza") ||
                payload.contains("ghp_") ||
                payload.contains("github_pat_") ||
                payload.contains("\"geminiApiKey\"") ||
                payload.contains("\"githubToken\"") ||
                payload.contains("\"bearerToken\"")
        }

        fun encryptWithPassphrase(passphrase: String, plaintext: String): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = deriveAesKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            return JSONObject().apply {
                put("alg", "PBKDF2WithHmacSHA256+AES-256-GCM")
                put("iter", PBKDF2_ITERATIONS)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString(2)
        }

        fun decryptWithPassphrase(passphrase: String, envelopeJson: String): String {
            val env = JSONObject(envelopeJson)
            val salt = Base64.decode(env.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(env.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(env.getString("data"), Base64.NO_WRAP)
            val key = deriveAesKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(data), Charsets.UTF_8)
        }

        private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        }
    }
}
