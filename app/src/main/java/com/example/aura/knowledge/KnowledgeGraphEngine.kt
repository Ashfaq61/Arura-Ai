package com.example.aura.knowledge

import com.example.aura.memory.AdvancedRepository
import com.example.aura.memory.KnowledgeEdgeEntity
import com.example.aura.memory.KnowledgeNodeEntity
import com.example.aura.memory.MemoryRepository

data class KnowledgeSubgraph(
    val root: KnowledgeNodeEntity,
    val connectedEdges: List<KnowledgeEdgeEntity>,
    val neighborNodes: List<KnowledgeNodeEntity>
)

/**
 * Manages AURA's persistent Knowledge Graph connecting memories, tasks, projects,
 * routines, workflows, notes, and calendar items with searchable relations.
 */
class KnowledgeGraphEngine(
    private val advancedRepo: AdvancedRepository,
    private val memoryRepo: MemoryRepository
) {
    suspend fun addNodeAndAutoLink(
        nodeType: String,
        title: String,
        summary: String,
        tagsCsv: String = "",
        sourceRef: String = ""
    ): Pair<Long, Int> {
        val now = System.currentTimeMillis()
        val id = advancedRepo.upsertKnowledgeNode(
            KnowledgeNodeEntity(
                nodeType = nodeType.uppercase(),
                title = title.trim(),
                summary = summary.trim(),
                tagsCsv = tagsCsv.trim(),
                sourceRef = sourceRef.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        val linksCreated = autoDiscoverLinksForNode(id)
        return id to linksCreated
    }

    suspend fun linkNodes(fromId: Long, toId: Long, relationType: String = "RELATED_TO"): Long {
        if (fromId == toId) return -1L
        val existing = advancedRepo.getAllKnowledgeEdges().firstOrNull {
            (it.fromNodeId == fromId && it.toNodeId == toId && it.relationType == relationType)
        }
        if (existing != null) return existing.id
        return advancedRepo.upsertKnowledgeEdge(
            KnowledgeEdgeEntity(
                fromNodeId = fromId,
                toNodeId = toId,
                relationType = relationType.uppercase()
            )
        )
    }

    suspend fun autoDiscoverLinksForNode(targetNodeId: Long): Int {
        val allNodes = advancedRepo.getAllKnowledgeNodes()
        val target = allNodes.firstOrNull { it.id == targetNodeId } ?: return 0
        val targetTokens = extractKeywords(target)
        if (targetTokens.isEmpty()) return 0

        val existingEdges = advancedRepo.getAllKnowledgeEdges()
        var created = 0
        for (other in allNodes) {
            if (other.id == target.id) continue
            val overlap = targetTokens.intersect(extractKeywords(other))
            if (overlap.size >= 2 || (target.tagsCsv.isNotBlank() && overlap.isNotEmpty())) {
                val already = existingEdges.any {
                    (it.fromNodeId == target.id && it.toNodeId == other.id) ||
                        (it.fromNodeId == other.id && it.toNodeId == target.id)
                }
                if (!already) {
                    advancedRepo.upsertKnowledgeEdge(
                        KnowledgeEdgeEntity(
                            fromNodeId = target.id,
                            toNodeId = other.id,
                            relationType = "RELATED_TO",
                            weight = (overlap.size * 0.25f).coerceIn(0.4f, 1.0f)
                        )
                    )
                    created++
                }
            }
        }
        return created
    }

    suspend fun syncRecentMemoriesIntoGraph(): Int {
        val existing = advancedRepo.getAllKnowledgeNodes()
        val existingRefs = existing.map { it.sourceRef }.toSet()
        val memories = memoryRepo.getRecentMemories(30)
        var added = 0
        for (m in memories) {
            val ref = "memory:${m.id}"
            if (!existingRefs.contains(ref)) {
                addNodeAndAutoLink(
                    nodeType = "MEMORY",
                    title = m.title,
                    summary = m.content.take(240),
                    tagsCsv = m.category.lowercase(),
                    sourceRef = ref
                )
                added++
            }
        }
        return added
    }

    fun searchAndExpand(
        query: String,
        nodes: List<KnowledgeNodeEntity>,
        edges: List<KnowledgeEdgeEntity>
    ): List<KnowledgeSubgraph> {
        val q = query.trim().lowercase()
        val matchedRoots = if (q.isBlank()) nodes else nodes.filter {
            it.title.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.tagsCsv.lowercase().contains(q) ||
                it.nodeType.lowercase().contains(q)
        }
        val byId = nodes.associateBy { it.id }
        return matchedRoots.map { root ->
            val relEdges = edges.filter { it.fromNodeId == root.id || it.toNodeId == root.id }
            val neighbors = relEdges.mapNotNull { e ->
                val otherId = if (e.fromNodeId == root.id) e.toNodeId else e.fromNodeId
                byId[otherId]
            }.distinctBy { it.id }
            KnowledgeSubgraph(root = root, connectedEdges = relEdges, neighborNodes = neighbors)
        }
    }

    private fun extractKeywords(node: KnowledgeNodeEntity): Set<String> {
        val stop = setOf("the", "and", "for", "with", "this", "that", "from", "mode", "aura")
        val raw = "${node.title} ${node.tagsCsv} ${node.summary}"
        return raw.lowercase()
            .split(Regex("[^a-z0-9\\u0600-\\u06FF]+"))
            .filter { it.length >= 3 && !stop.contains(it) }
            .toSet()
    }
}
