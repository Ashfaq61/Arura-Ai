package com.example.aura.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentConversationDao {
    @Query("SELECT * FROM agent_conversations ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AgentConversationEntity>>

    @Query("SELECT * FROM agent_conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<AgentConversationEntity>

    @Query("SELECT * FROM agent_conversations WHERE agentName LIKE '%' || :agentName || '%' OR participatingAgentsCsv LIKE '%' || :agentName || '%' ORDER BY timestamp DESC")
    suspend fun getForAgent(agentName: String): List<AgentConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AgentConversationEntity): Long

    @Query("DELETE FROM agent_conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM agent_conversations WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM agent_conversations WHERE id NOT IN (SELECT id FROM agent_conversations ORDER BY timestamp DESC LIMIT :maxCount)")
    suspend fun trimToMaxCount(maxCount: Int)

    @Query("DELETE FROM agent_conversations")
    suspend fun clearAll()
}

@Dao
interface CustomAgentProfileDao {
    @Query("SELECT * FROM custom_agent_profiles ORDER BY numericId ASC")
    fun observeAll(): Flow<List<CustomAgentProfileEntity>>

    @Query("SELECT * FROM custom_agent_profiles ORDER BY numericId ASC")
    suspend fun getAll(): List<CustomAgentProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CustomAgentProfileEntity)

    @Query("DELETE FROM custom_agent_profiles WHERE codeName = :codeName")
    suspend fun delete(codeName: String)
}

@Dao
interface AgentActivityDao {
    @Query("SELECT * FROM agent_activity_events ORDER BY startedAt DESC LIMIT 120")
    fun observeAll(): Flow<List<AgentActivityEventEntity>>

    @Query("SELECT * FROM agent_activity_events ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 120): List<AgentActivityEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AgentActivityEventEntity): Long

    @Query("UPDATE agent_activity_events SET status = :status, summary = :summary, errorReason = :errorReason, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, summary: String, errorReason: String, completedAt: Long?)

    @Query("DELETE FROM agent_activity_events WHERE startedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM agent_activity_events")
    suspend fun clearAll()
}

@Dao
interface FeatureRegistryHistoryDao {
    @Query("SELECT * FROM feature_registry_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<FeatureRegistryHistoryEntity>>

    @Query("SELECT * FROM feature_registry_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<FeatureRegistryHistoryEntity>

    @Query("SELECT * FROM feature_registry_history WHERE featureId = :featureId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForFeature(featureId: String): FeatureRegistryHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FeatureRegistryHistoryEntity): Long

    @Query("DELETE FROM feature_registry_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM feature_registry_history")
    suspend fun clearAll()
}

@Dao
interface FeatureRequestDao {
    @Query("SELECT * FROM feature_requests ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FeatureRequestEntity>>

    @Query("SELECT * FROM feature_requests ORDER BY updatedAt DESC")
    suspend fun getAll(): List<FeatureRequestEntity>

    @Query("SELECT * FROM feature_requests WHERE requestId = :requestId LIMIT 1")
    suspend fun getById(requestId: String): FeatureRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: FeatureRequestEntity)

    @Query("DELETE FROM feature_requests WHERE requestId = :requestId")
    suspend fun delete(requestId: String)

    @Query("DELETE FROM feature_requests WHERE updatedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM feature_requests")
    suspend fun clearAll()
}

@Dao
interface FeatureRecommendationDao {
    @Query("SELECT * FROM feature_recommendations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FeatureRecommendationEntity>>

    @Query("SELECT * FROM feature_recommendations ORDER BY createdAt DESC")
    suspend fun getAll(): List<FeatureRecommendationEntity>

    @Query("SELECT * FROM feature_recommendations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FeatureRecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rec: FeatureRecommendationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(recs: List<FeatureRecommendationEntity>)

    @Query("DELETE FROM feature_recommendations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM feature_recommendations")
    suspend fun clearAll()
}

@Dao
interface PendingChangeProposalDao {
    @Query("SELECT * FROM pending_change_proposals ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PendingChangeProposalEntity>>

    @Query("SELECT * FROM pending_change_proposals ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PendingChangeProposalEntity>

    @Query("SELECT * FROM pending_change_proposals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingChangeProposalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(proposal: PendingChangeProposalEntity)

    @Query("DELETE FROM pending_change_proposals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_change_proposals")
    suspend fun clearAll()
}
