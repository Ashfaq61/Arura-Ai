package com.example.aura.history

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.aura.backend.BackendServiceGateway
import com.example.aura.memory.AgentActivityEventEntity
import com.example.aura.memory.AgentConversationEntity
import com.example.aura.memory.AuditLogEntity
import com.example.aura.memory.EvolutionCycleEntity
import com.example.aura.memory.FeatureRecommendationEntity
import com.example.aura.memory.FeatureRegistryHistoryEntity
import com.example.aura.memory.FeatureRequestEntity
import com.example.aura.memory.PendingChangeProposalEntity
import com.example.aura.memory.ResearchProjectEntity
import com.example.aura.security.PrivacyDataRouter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryDomain {
    CONVERSATION,
    RESEARCH,
    USER_REQUEST,
    RECOMMENDATION,
    INSTALLED_TOOL_REGISTRY,
    DEV_LOG,
    TEST_REPORT,
    UPGRADE_HISTORY,
    ERROR_AND_SOLUTION
}

data class HistorySearchFilter(
    val query: String = "",
    val agentFilter: String = "",
    val repoOrModelFilter: String = "",
    val featureFilter: String = "",
    val outcomeFilter: String = "ALL", // ALL, SUCCESS, FAILED, PENDING, DISCOVERED_ONLY
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val domains: Set<HistoryDomain> = HistoryDomain.entries.toSet()
)

data class HistorySearchHit(
    val id: String,
    val domain: HistoryDomain,
    val title: String,
    val summary: String,
    val agentOrActor: String,
    val outcome: String,
    val referenceLink: String,
    val timestamp: Long
)

data class ExportedHistoryArtifact(
    val format: String, // JSON, CSV, PDF
    val file: File,
    val contentUri: Uri?,
    val itemCount: Int,
    val previewSnippet: String
)

/**
 * Unified Search & Sanitized Export (JSON, CSV, PDF) across all AURA history domains.
 * Uses FileProvider under app filesDir — requires ZERO storage permissions.
 */
class HistorySearchAndExportManager(
    private val context: Context,
    private val privacyRouter: PrivacyDataRouter = PrivacyDataRouter()
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun search(
        filter: HistorySearchFilter,
        conversations: List<AgentConversationEntity>,
        research: List<ResearchProjectEntity>,
        requests: List<FeatureRequestEntity>,
        recommendations: List<FeatureRecommendationEntity>,
        registry: List<FeatureRegistryHistoryEntity>,
        proposals: List<PendingChangeProposalEntity>,
        activities: List<AgentActivityEventEntity>,
        evolutions: List<EvolutionCycleEntity>,
        audits: List<AuditLogEntity>
    ): List<HistorySearchHit> {
        val hits = mutableListOf<HistorySearchHit>()

        if (HistoryDomain.CONVERSATION in filter.domains) {
            conversations.forEach { c ->
                hits += HistorySearchHit(
                    id = "conv_${c.id}",
                    domain = HistoryDomain.CONVERSATION,
                    title = "[${c.channelType}] ${c.topic}",
                    summary = "Request: ${c.userOriginalRequest}\nDecision: ${c.decisionsAndReasons}\nOutcome: ${c.finalOutcome}",
                    agentOrActor = "${c.agentName}, ${c.participatingAgentsCsv}",
                    outcome = if (c.finalOutcome.contains("fail", true) || c.finalOutcome.contains("block", true)) "FAILED" else "SUCCESS",
                    referenceLink = c.repositoryAndModelLinksCsv,
                    timestamp = c.timestamp
                )
            }
        }

        if (HistoryDomain.RESEARCH in filter.domains) {
            research.forEach { r ->
                hits += HistorySearchHit(
                    id = "res_${r.id}",
                    domain = HistoryDomain.RESEARCH,
                    title = "[${r.sourcePlatform}] ${r.name}",
                    summary = "${r.description}\nCompatibility: ${r.compatibility} | Security: ${r.securityAssessment}",
                    agentOrActor = if (r.sourcePlatform == "HUGGING_FACE") "Hugging Face Research Agent" else "GitHub Research Agent",
                    outcome = r.installationStatus,
                    referenceLink = r.url,
                    timestamp = r.fetchedAt
                )
            }
        }

        if (HistoryDomain.USER_REQUEST in filter.domains) {
            requests.forEach { req ->
                hits += HistorySearchHit(
                    id = "req_${req.requestId}",
                    domain = HistoryDomain.USER_REQUEST,
                    title = "${req.requestId} (${req.requestMode}) Stage ${req.currentStage}/16: ${req.stageName}",
                    summary = "Prompt: ${req.userPrompt}\nPlan: ${req.technicalPlan.take(180)}\n${if (req.failureReason.isNotBlank()) "Failure: ${req.failureReason}" else ""}".trim(),
                    agentOrActor = req.assignedAgentsCsv.ifBlank { "Main Brain / Orchestrator Agent" },
                    outcome = req.status,
                    referenceLink = req.linkedSourceId,
                    timestamp = req.updatedAt
                )
            }
        }

        if (HistoryDomain.RECOMMENDATION in filter.domains) {
            recommendations.forEach { rec ->
                hits += HistorySearchHit(
                    id = "rec_${rec.id}",
                    domain = HistoryDomain.RECOMMENDATION,
                    title = "Inbox: ${rec.featureName}",
                    summary = "${rec.summaryWhatItDoes}\nWhy: ${rec.whyAuraNeedsIt}",
                    agentOrActor = rec.discoveringAgent,
                    outcome = rec.inboxStatus,
                    referenceLink = rec.sourceLinksCsv,
                    timestamp = rec.createdAt
                )
            }
        }

        if (HistoryDomain.INSTALLED_TOOL_REGISTRY in filter.domains) {
            registry.forEach { reg ->
                hits += HistorySearchHit(
                    id = "reg_${reg.id}",
                    domain = HistoryDomain.INSTALLED_TOOL_REGISTRY,
                    title = "${reg.featureName} v${reg.version} (${reg.changeEventType})",
                    summary = "${reg.description}\nDiff: ${reg.versionDiffSummary}\nReason: ${reg.decisionReason}",
                    agentOrActor = "${reg.recommendingAgent}, ${reg.participatingAgentsCsv}",
                    outcome = reg.activationStatus,
                    referenceLink = reg.sourceUrl,
                    timestamp = reg.timestamp
                )
            }
        }

        if (HistoryDomain.DEV_LOG in filter.domains) {
            activities.filter { it.activityType in setOf("DEVELOPING", "CONSULTING", "RESEARCHING", "TALKING", "ACTIVATED") }
                .forEach { act ->
                    hits += HistorySearchHit(
                        id = "act_${act.id}",
                        domain = HistoryDomain.DEV_LOG,
                        title = "${act.agentName} • ${act.activityType}",
                        summary = "${act.summary} (Target: ${act.targetAgentOrModule})",
                        agentOrActor = act.agentName,
                        outcome = act.status,
                        referenceLink = act.relatedRequestId,
                        timestamp = act.startedAt
                    )
                }
        }

        if (HistoryDomain.TEST_REPORT in filter.domains) {
            proposals.forEach { prop ->
                hits += HistorySearchHit(
                    id = "prop_${prop.id}",
                    domain = HistoryDomain.TEST_REPORT,
                    title = "Change Verification: ${prop.title} v${prop.targetVersion}",
                    summary = "Tests: ${prop.testReport}\nSecurity: ${prop.securityAssessment}",
                    agentOrActor = "Testing Agent, Security Agent",
                    outcome = prop.status,
                    referenceLink = prop.requestId,
                    timestamp = prop.updatedAt
                )
            }
            requests.filter { it.testReport.isNotBlank() }.forEach { req ->
                hits += HistorySearchHit(
                    id = "req_test_${req.requestId}",
                    domain = HistoryDomain.TEST_REPORT,
                    title = "Pipeline Tests (${req.requestId})",
                    summary = req.testReport,
                    agentOrActor = "Testing Agent",
                    outcome = req.status,
                    referenceLink = req.resultFeatureId,
                    timestamp = req.updatedAt
                )
            }
        }

        if (HistoryDomain.UPGRADE_HISTORY in filter.domains) {
            evolutions.forEach { ev ->
                hits += HistorySearchHit(
                    id = "evo_${ev.id}",
                    domain = HistoryDomain.UPGRADE_HISTORY,
                    title = "Evolution Cycle #${ev.id}: ${ev.candidateSolution}",
                    summary = "Gap: ${ev.gapIdentified}\nBenchmark: ${ev.benchmarkSummary}",
                    agentOrActor = ev.evaluatorAgents,
                    outcome = ev.status,
                    referenceLink = "",
                    timestamp = ev.createdAt
                )
            }
            registry.filter { it.changeEventType in setOf("UPGRADED", "ROLLED_BACK", "ACTIVATED") }.forEach { reg ->
                hits += HistorySearchHit(
                    id = "upg_${reg.id}",
                    domain = HistoryDomain.UPGRADE_HISTORY,
                    title = "Upgrade [${reg.changeEventType}] ${reg.featureName} ${reg.previousVersion ?: "-"} -> v${reg.version}",
                    summary = reg.versionDiffSummary,
                    agentOrActor = reg.recommendingAgent,
                    outcome = reg.activationStatus,
                    referenceLink = reg.sourceUrl,
                    timestamp = reg.timestamp
                )
            }
        }

        if (HistoryDomain.ERROR_AND_SOLUTION in filter.domains) {
            activities.filter { it.status == "FAILED" || it.errorReason.isNotBlank() }.forEach { act ->
                hits += HistorySearchHit(
                    id = "err_act_${act.id}",
                    domain = HistoryDomain.ERROR_AND_SOLUTION,
                    title = "Error in ${act.agentName} (${act.activityType})",
                    summary = "Reason: ${act.errorReason.ifBlank { act.summary }}",
                    agentOrActor = act.agentName,
                    outcome = "FAILED",
                    referenceLink = act.relatedRequestId,
                    timestamp = act.startedAt
                )
            }
            audits.filter { it.outcome in setOf("BLOCKED", "DENIED", "FAILED") }.forEach { aud ->
                hits += HistorySearchHit(
                    id = "err_aud_${aud.id}",
                    domain = HistoryDomain.ERROR_AND_SOLUTION,
                    title = "Policy/Error [${aud.outcome}] ${aud.action}",
                    summary = aud.details,
                    agentOrActor = aud.actor,
                    outcome = aud.outcome,
                    referenceLink = "",
                    timestamp = aud.timestamp
                )
            }
        }

        val q = filter.query.trim().lowercase()
        val agentQ = filter.agentFilter.trim().lowercase()
        val repoQ = filter.repoOrModelFilter.trim().lowercase()
        val featQ = filter.featureFilter.trim().lowercase()
        val outcomeQ = filter.outcomeFilter.trim().uppercase()

        return hits.filter { h ->
            val matchQuery = q.isEmpty() ||
                h.title.lowercase().contains(q) ||
                h.summary.lowercase().contains(q) ||
                h.agentOrActor.lowercase().contains(q) ||
                h.referenceLink.lowercase().contains(q)
            val matchAgent = agentQ.isEmpty() || h.agentOrActor.lowercase().contains(agentQ)
            val matchRepo = repoQ.isEmpty() ||
                h.referenceLink.lowercase().contains(repoQ) ||
                h.title.lowercase().contains(repoQ) ||
                h.summary.lowercase().contains(repoQ)
            val matchFeature = featQ.isEmpty() ||
                h.title.lowercase().contains(featQ) ||
                h.summary.lowercase().contains(featQ)
            val matchOutcome = when (outcomeQ) {
                "", "ALL" -> true
                "SUCCESS" -> h.outcome.uppercase() in setOf("SUCCESS", "ACTIVE", "COMPLETED_ACTIVATED", "IMPLEMENTED", "APPROVED", "AUTO_ACTIVATED", "ALLOWED")
                "FAILED" -> h.outcome.uppercase() in setOf("FAILED", "BLOCKED", "REJECTED", "DENIED", "CANCELLED")
                "PENDING" -> h.outcome.uppercase() in setOf("PENDING", "AWAITING_APPROVAL", "IN_PROGRESS", "NEEDS_APPROVAL", "PAUSED", "SAVED_FOR_LATER")
                "DISCOVERED_ONLY" -> h.outcome.uppercase() in setOf("DISCOVERED_ONLY", "PROPOSED", "DEVELOPMENT_REQUIRED", "NEW")
                else -> h.outcome.equals(outcomeQ, ignoreCase = true)
            }
            val matchFrom = filter.fromMillis == null || h.timestamp >= filter.fromMillis
            val matchTo = filter.toMillis == null || h.timestamp <= filter.toMillis
            matchQuery && matchAgent && matchRepo && matchFeature && matchOutcome && matchFrom && matchTo
        }.sortedByDescending { it.timestamp }
    }

    fun exportToJson(hits: List<HistorySearchHit>): Result<ExportedHistoryArtifact> = runCatching {
        val dir = ensureExportDir()
        val file = File(dir, "aura_history_${System.currentTimeMillis()}.json")
        val arr = JSONArray()
        hits.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("domain", h.domain.name)
                put("title", scrub(h.title))
                put("summary", scrub(h.summary))
                put("agentOrActor", scrub(h.agentOrActor))
                put("outcome", h.outcome)
                put("referenceLink", scrub(h.referenceLink))
                put("timestampIso", dateFmt.format(Date(h.timestamp)))
            })
        }
        val payload = JSONObject().apply {
            put("app", "AURA AI")
            put("exportedAt", dateFmt.format(Date()))
            put("credentialsIncluded", false)
            put("count", hits.size)
            put("records", arr)
        }.toString(2)
        file.writeText(payload, Charsets.UTF_8)
        ExportedHistoryArtifact("JSON", file, toContentUri(file), hits.size, payload.take(400))
    }

    fun exportToCsv(hits: List<HistorySearchHit>): Result<ExportedHistoryArtifact> = runCatching {
        val dir = ensureExportDir()
        val file = File(dir, "aura_history_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder()
        sb.appendLine("id,domain,timestamp_iso,agent_or_actor,outcome,title,reference_link,summary")
        hits.forEach { h ->
            sb.append(csvEscape(h.id)).append(',')
                .append(csvEscape(h.domain.name)).append(',')
                .append(csvEscape(dateFmt.format(Date(h.timestamp)))).append(',')
                .append(csvEscape(scrub(h.agentOrActor))).append(',')
                .append(csvEscape(h.outcome)).append(',')
                .append(csvEscape(scrub(h.title))).append(',')
                .append(csvEscape(scrub(h.referenceLink))).append(',')
                .append(csvEscape(scrub(h.summary))).append('\n')
        }
        val text = sb.toString()
        file.writeText(text, Charsets.UTF_8)
        ExportedHistoryArtifact("CSV", file, toContentUri(file), hits.size, text.take(400))
    }

    fun exportToPdf(hits: List<HistorySearchHit>): Result<ExportedHistoryArtifact> = runCatching {
        val dir = ensureExportDir()
        val file = File(dir, "aura_history_${System.currentTimeMillis()}.pdf")
        var pageNum = 1
        val nativeWorked = runCatching {
            val doc = PdfDocument()
            try {
                val pageWidth = 595
                val pageHeight = 842
                val margin = 36f
                val lineHeight = 14f

                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                    isFakeBoldText = true
                }
                val bodyPaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 9.5f
                }
                val headerPaint = Paint().apply {
                    color = Color.rgb(20, 70, 140)
                    textSize = 10.5f
                    isFakeBoldText = true
                }

                var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                var canvas = page.canvas
                var y = 44f

                canvas.drawText("AURA AI — Agent History, Audit & Feature Control Export", margin, y, titlePaint)
                y += 18f
                canvas.drawText("Exported: ${dateFmt.format(Date())} | Records: ${hits.size} | Secrets Redacted: YES", margin, y, bodyPaint)
                y += 22f

                fun newPageIfNeeded(neededLines: Int) {
                    if (y + neededLines * lineHeight > pageHeight - 40f) {
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                        canvas = page.canvas
                        y = 44f
                    }
                }

                if (hits.isEmpty()) {
                    canvas.drawText("No records matched the active search filter.", margin, y, bodyPaint)
                } else {
                    hits.take(150).forEach { h ->
                        val summaryLines = scrub(h.summary).replace('\n', ' ').chunked(88).take(3)
                        newPageIfNeeded(3 + summaryLines.size)
                        canvas.drawText(
                            "[${h.domain.name}] ${scrub(h.title).take(72)}",
                            margin, y, headerPaint
                        )
                        y += lineHeight
                        canvas.drawText(
                            "${dateFmt.format(Date(h.timestamp))} | Agents: ${scrub(h.agentOrActor).take(40)} | Status: ${h.outcome}",
                            margin, y, bodyPaint
                        )
                        y += lineHeight
                        summaryLines.forEach { ln ->
                            canvas.drawText(ln, margin + 8f, y, bodyPaint)
                            y += lineHeight
                        }
                        y += 6f
                    }
                }
                doc.finishPage(page)
                file.outputStream().use { out -> doc.writeTo(out) }
            } finally {
                runCatching { doc.close() }
            }
        }.isSuccess && file.exists() && file.length() > 32L

        if (!nativeWorked) {
            pageNum = writeFallbackPdf14(file, hits)
        }

        ExportedHistoryArtifact(
            format = "PDF",
            file = file,
            contentUri = toContentUri(file),
            itemCount = hits.size,
            previewSnippet = "PDF (${file.length()} bytes, $pageNum page(s)) saved to ${file.name}"
        )
    }

    private fun writeFallbackPdf14(file: File, hits: List<HistorySearchHit>): Int {
        val lines = mutableListOf<String>()
        lines += "AURA AI - Agent History, Audit & Feature Control Export"
        lines += "Exported: ${dateFmt.format(Date())} | Records: ${hits.size} | Secrets Redacted: YES"
        lines += ""
        if (hits.isEmpty()) {
            lines += "No records matched the active search filter."
        } else {
            hits.take(150).forEach { h ->
                lines += "[${h.domain.name}] ${scrub(h.title).take(76)}"
                lines += "  ${dateFmt.format(Date(h.timestamp))} | ${scrub(h.agentOrActor).take(42)} | ${h.outcome}"
                scrub(h.summary).replace('\n', ' ').chunked(84).take(2).forEach { ln ->
                    lines += "  $ln"
                }
            }
        }

        val pages = lines.chunked(48).ifEmpty { listOf(listOf("AURA AI Empty Export")) }
        val objects = mutableListOf<String>()
        // 1: Catalog, 2: Pages, 3: Font Helvetica, then pairs of (Page, ContentStream)
        val pageObjIds = pages.indices.map { 4 + it * 2 }
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [ ${pageObjIds.joinToString(" ") { "$it 0 R" }} ] /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

        pages.forEachIndexed { idx, pageLines ->
            val pageId = 4 + idx * 2
            val contentId = pageId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentId 0 R >>"
            val streamText = buildString {
                appendLine("BT")
                appendLine("/F1 10 Tf")
                appendLine("36 800 Td")
                appendLine("14 TL")
                pageLines.forEachIndexed { lineIdx, raw ->
                    val safe = raw
                        .replace("\\", "\\\\")
                        .replace("(", "\\(")
                        .replace(")", "\\)")
                        .replace(Regex("[^\\x20-\\x7E]"), " ")
                    if (lineIdx == 0) appendLine("($safe) Tj") else appendLine("T* ($safe) Tj")
                }
                appendLine("ET")
            }
            objects += "<< /Length ${streamText.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${streamText}endstream"
        }

        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, body ->
            offsets += pdf.length
            pdf.append("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xrefPos = pdf.length
        pdf.append("xref\n0 ${objects.size + 1}\n")
        pdf.append("0000000000 65535 f \n")
        offsets.drop(1).forEach { off ->
            pdf.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        file.writeText(pdf.toString(), Charsets.US_ASCII)
        return pages.size
    }

    fun buildShareIntent(artifact: ExportedHistoryArtifact): Intent? {
        val uri = artifact.contentUri ?: return null
        val mime = when (artifact.format) {
            "JSON" -> "application/json"
            "CSV" -> "text/csv"
            else -> "application/pdf"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AURA AI History Export (${artifact.format})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ensureExportDir(): File {
        val dir = File(context.filesDir, "aura_exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun toContentUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    private fun csvEscape(raw: String): String {
        val clean = raw.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ')
        return "\"$clean\""
    }

    private fun scrub(raw: String): String {
        val first = BackendServiceGateway.sanitizeForLog(raw)
        return privacyRouter.sanitizeForExternalProvider(first, redactEnabled = true).redactedText
    }
}
