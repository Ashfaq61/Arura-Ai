package com.example.aura.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchMemories(query: String): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 15): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entry: MemoryEntryEntity): Long

    @Update
    suspend fun updateMemory(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_entries")
    suspend fun clearAllMemories()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_records ORDER BY createdAt DESC LIMIT 100")
    fun observeTasks(): Flow<List<TaskRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskRecordEntity): Long

    @Update
    suspend fun updateTask(task: TaskRecordEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE action LIKE '%' || :query || '%' OR details LIKE '%' || :query || '%' OR actor LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 200")
    fun searchAuditLogs(query: String): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface ApprovalDao {
    @Query("SELECT * FROM approval_requests ORDER BY createdAt DESC")
    fun observeApprovals(): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT COUNT(*) FROM approval_requests WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproval(request: ApprovalRequestEntity): Long

    @Query("UPDATE approval_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}

@Dao
interface MarketplaceDao {
    @Query("SELECT * FROM marketplace_tools ORDER BY name ASC")
    fun observeTools(): Flow<List<MarketplaceToolEntity>>

    @Query("SELECT * FROM marketplace_tools WHERE id = :id")
    suspend fun getToolById(id: String): MarketplaceToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTool(tool: MarketplaceToolEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(tools: List<MarketplaceToolEntity>)

    @Query("DELETE FROM marketplace_tools WHERE id = :id")
    suspend fun deleteTool(id: String)
}

@Dao
interface ResearchDao {
    @Query("SELECT * FROM research_projects ORDER BY fetchedAt DESC")
    fun observeProjects(): Flow<List<ResearchProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ResearchProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(projects: List<ResearchProjectEntity>)
}

@Dao
interface EvolutionDao {
    @Query("SELECT * FROM evolution_cycles ORDER BY createdAt DESC")
    fun observeCycles(): Flow<List<EvolutionCycleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: EvolutionCycleEntity): Long

    @Update
    suspend fun updateCycle(cycle: EvolutionCycleEntity)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM provider_usage ORDER BY timestamp DESC LIMIT 100")
    fun observeUsage(): Flow<List<ProviderUsageEntity>>

    @Insert
    suspend fun insertUsage(usage: ProviderUsageEntity)
}

@Dao
interface FeatureAuditDao {
    @Query("SELECT * FROM feature_audit ORDER BY featureName ASC")
    fun observeAudit(): Flow<List<FeatureAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FeatureAuditEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FeatureAuditEntity)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skill_procedures ORDER BY updatedAt DESC")
    fun observeSkills(): Flow<List<SkillProcedureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillProcedureEntity): Long

    @Query("DELETE FROM skill_procedures WHERE id = :id")
    suspend fun deleteSkill(id: Long)
}

@Dao
interface LongHorizonDao {
    @Query("SELECT * FROM long_horizon_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<LongHorizonTaskEntity>>

    @Query("SELECT * FROM long_horizon_tasks WHERE id = :id")
    suspend fun getById(id: Long): LongHorizonTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: LongHorizonTaskEntity): Long

    @Query("DELETE FROM long_horizon_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY id ASC")
    fun observeScheduledTasks(): Flow<List<ScheduledTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(tasks: List<ScheduledTaskEntity>)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_runs ORDER BY timestamp DESC LIMIT 30")
    fun observeRuns(): Flow<List<BenchmarkRunEntity>>

    @Query("SELECT * FROM benchmark_runs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRun(): BenchmarkRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: BenchmarkRunEntity): Long
}

@Dao
interface SelfModDao {
    @Query("SELECT * FROM self_mod_records ORDER BY createdAt DESC LIMIT 30")
    fun observeRecords(): Flow<List<SelfModificationRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: SelfModificationRecordEntity): Long
}
