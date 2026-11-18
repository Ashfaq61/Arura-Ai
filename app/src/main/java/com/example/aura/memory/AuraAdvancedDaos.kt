package com.example.aura.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY id DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun getAll(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<RoutineEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface VisualWorkflowDao {
    @Query("SELECT * FROM visual_workflows ORDER BY updatedAt DESC")
    fun observeWorkflows(): Flow<List<VisualWorkflowEntity>>

    @Query("SELECT * FROM visual_workflows")
    suspend fun getAll(): List<VisualWorkflowEntity>

    @Query("SELECT * FROM visual_workflows WHERE id = :id")
    suspend fun getById(id: Long): VisualWorkflowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workflow: VisualWorkflowEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<VisualWorkflowEntity>)

    @Query("DELETE FROM visual_workflows WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface KnowledgeGraphDao {
    @Query("SELECT * FROM knowledge_nodes ORDER BY updatedAt DESC")
    fun observeNodes(): Flow<List<KnowledgeNodeEntity>>

    @Query("SELECT * FROM knowledge_edges ORDER BY createdAt DESC")
    fun observeEdges(): Flow<List<KnowledgeEdgeEntity>>

    @Query("SELECT * FROM knowledge_nodes")
    suspend fun getAllNodes(): List<KnowledgeNodeEntity>

    @Query("SELECT * FROM knowledge_edges")
    suspend fun getAllEdges(): List<KnowledgeEdgeEntity>

    @Query("SELECT * FROM knowledge_nodes WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNodes(query: String): Flow<List<KnowledgeNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: KnowledgeNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreNodes(nodes: List<KnowledgeNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: KnowledgeEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreEdges(edges: List<KnowledgeEdgeEntity>)

    @Query("DELETE FROM knowledge_edges WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteEdgesForNode(nodeId: Long)

    @Query("DELETE FROM knowledge_nodes WHERE id = :nodeId")
    suspend fun deleteNodeById(nodeId: Long)

    @Query("DELETE FROM knowledge_edges WHERE id = :edgeId")
    suspend fun deleteEdgeById(edgeId: Long)
}

@Dao
interface AppCompatibilityDao {
    @Query("SELECT * FROM app_compatibility_profiles ORDER BY appLabel ASC")
    fun observeProfiles(): Flow<List<AppCompatibilityProfileEntity>>

    @Query("SELECT * FROM app_compatibility_profiles")
    suspend fun getAll(): List<AppCompatibilityProfileEntity>

    @Query("SELECT * FROM app_compatibility_profiles WHERE packageName = :pkg")
    suspend fun getByPackage(pkg: String): AppCompatibilityProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AppCompatibilityProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<AppCompatibilityProfileEntity>)
}

@Dao
interface AutomationRecipeDao {
    @Query("SELECT * FROM automation_recipes ORDER BY name ASC")
    fun observeRecipes(): Flow<List<AutomationRecipeEntity>>

    @Query("SELECT * FROM automation_recipes")
    suspend fun getAll(): List<AutomationRecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: AutomationRecipeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(recipes: List<AutomationRecipeEntity>)

    @Query("DELETE FROM automation_recipes WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProductivityDao {
    @Query("SELECT * FROM productivity_items ORDER BY completed ASC, COALESCE(dueAtMillis, 9999999999999) ASC, updatedAt DESC")
    fun observeItems(): Flow<List<ProductivityItemEntity>>

    @Query("SELECT * FROM productivity_items")
    suspend fun getAll(): List<ProductivityItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductivityItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<ProductivityItemEntity>)

    @Query("DELETE FROM productivity_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SyncDeviceDao {
    @Query("SELECT * FROM sync_devices ORDER BY COALESCE(lastSyncAt, 0) DESC")
    fun observeDevices(): Flow<List<SyncDeviceEntity>>

    @Query("SELECT * FROM sync_devices")
    suspend fun getAll(): List<SyncDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: SyncDeviceEntity)

    @Query("DELETE FROM sync_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)
}

@Dao
interface CrashReportDao {
    @Query("SELECT * FROM crash_reports ORDER BY timestamp DESC LIMIT 50")
    fun observeReports(): Flow<List<CrashReportEntity>>

    @Query("SELECT * FROM crash_reports ORDER BY timestamp DESC")
    suspend fun getAll(): List<CrashReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: CrashReportEntity): Long

    @Query("UPDATE crash_reports SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)

    @Query("DELETE FROM crash_reports")
    suspend fun clearAll()
}
