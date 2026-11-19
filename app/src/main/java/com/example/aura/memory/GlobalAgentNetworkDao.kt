package com.example.aura.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedAgentNodeDao {
    @Query("SELECT * FROM connected_agent_nodes ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeAll(): Flow<List<ConnectedAgentNodeEntity>>

    @Query("SELECT * FROM connected_agent_nodes ORDER BY isBuiltIn DESC, createdAt ASC")
    suspend fun getAll(): List<ConnectedAgentNodeEntity>

    @Query("SELECT * FROM connected_agent_nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getById(nodeId: String): ConnectedAgentNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: ConnectedAgentNodeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(nodes: List<ConnectedAgentNodeEntity>)

    @Query("DELETE FROM connected_agent_nodes WHERE nodeId = :nodeId")
    suspend fun delete(nodeId: String)
}

@Dao
interface GlobalDiscussionSessionDao {
    @Query("SELECT * FROM global_discussion_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GlobalDiscussionSessionEntity>>

    @Query("SELECT * FROM global_discussion_sessions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<GlobalDiscussionSessionEntity>

    @Query("SELECT * FROM global_discussion_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): GlobalDiscussionSessionEntity?

    @Query("SELECT * FROM global_discussion_sessions WHERE topicFingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): GlobalDiscussionSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: GlobalDiscussionSessionEntity)

    @Query("DELETE FROM global_discussion_sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM global_discussion_sessions")
    suspend fun clearAll()
}

@Dao
interface GlobalDiscussionTurnDao {
    @Query("SELECT * FROM global_discussion_turns ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<GlobalDiscussionTurnEntity>>

    @Query("SELECT * FROM global_discussion_turns WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: String): List<GlobalDiscussionTurnEntity>

    @Query("SELECT * FROM global_discussion_turns ORDER BY timestamp ASC")
    suspend fun getAll(): List<GlobalDiscussionTurnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turn: GlobalDiscussionTurnEntity): Long

    @Query("DELETE FROM global_discussion_turns WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM global_discussion_turns")
    suspend fun clearAll()
}

@Dao
interface QueuedAgentMessageDao {
    @Query("SELECT * FROM queued_agent_messages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QueuedAgentMessageEntity>>

    @Query("SELECT * FROM queued_agent_messages WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getPending(): List<QueuedAgentMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(msg: QueuedAgentMessageEntity): Long

    @Query("DELETE FROM queued_agent_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM queued_agent_messages")
    suspend fun clearAll()
}

@Dao
interface AgentNotificationEventDao {
    @Query("SELECT * FROM agent_notification_events ORDER BY createdAt DESC LIMIT 150")
    fun observeAll(): Flow<List<AgentNotificationEventEntity>>

    @Query("SELECT * FROM agent_notification_events ORDER BY createdAt DESC LIMIT 150")
    suspend fun getAll(): List<AgentNotificationEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AgentNotificationEventEntity): Long

    @Query("UPDATE agent_notification_events SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE agent_notification_events SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM agent_notification_events")
    suspend fun clearAll()
}
