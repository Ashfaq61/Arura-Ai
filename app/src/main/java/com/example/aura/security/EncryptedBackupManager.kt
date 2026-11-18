package com.example.aura.security

import android.util.Base64
import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.AppCompatibilityProfileEntity
import com.example.aura.memory.AutomationRecipeEntity
import com.example.aura.memory.KnowledgeEdgeEntity
import com.example.aura.memory.KnowledgeNodeEntity
import com.example.aura.memory.MemoryEntryEntity
import com.example.aura.memory.MemoryRepository
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
    val includeCompatibilityProfiles: Boolean = true
)

data class BackupRestoreSummary(
    val memoriesRestored: Int,
    val routinesRestored: Int,
    val workflowsRestored: Int,
    val knowledgeNodesRestored: Int,
    val recipesRestored: Int,
    val productivityRestored: Int,
    val compatRestored: Int
)

/**
 * AES-256-GCM passphrase-encrypted backup and restore manager.
 * Guarantees zero credentials/API keys/GitHub PATs ever leave the Keystore vault.
 */
class EncryptedBackupManager(
    private val memoryRepo: MemoryRepository,
    private val advancedRepo: AdvancedRepository
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

            BackupRestoreSummary(memCount, routineCount, workflowCount, kgCount, recipeCount, prodCount, compatCount)
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
