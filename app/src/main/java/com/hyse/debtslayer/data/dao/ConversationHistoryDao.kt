package com.hyse.debtslayer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hyse.debtslayer.data.entity.ConversationHistory

@Dao
interface ConversationHistoryDao {

    @Insert
    suspend fun insertConversation(conversation: ConversationHistory)

    @Query("SELECT * FROM conversation_history WHERE wasSuccessful = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSuccessfulConversations(limit: Int = 10): List<ConversationHistory>

    @Query("DELETE FROM conversation_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOldConversations(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM conversation_history")
    suspend fun getConversationCount(): Int
}
