package com.hyse.debtslayer.data.dao

import androidx.room.*
import com.hyse.debtslayer.data.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE sessionDate >= :fromDate ORDER BY timestamp ASC")
    fun getMessagesFrom(fromDate: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionDate = :today ORDER BY timestamp ASC")
    suspend fun getTodayMessages(today: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionDate < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionDate = :today")
    suspend fun countTodayMessages(today: String): Int

    @Query("UPDATE chat_messages SET feedbackGiven = 1, feedbackIsPositive = :isPositive WHERE id = :messageId")
    suspend fun updateFeedback(messageId: Long, isPositive: Boolean)
}
