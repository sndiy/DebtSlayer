package com.hyse.debtslayer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hyse.debtslayer.data.entity.ConversationFeedback

@Dao
interface FeedbackDao {

    @Insert
    suspend fun insertFeedback(feedback: ConversationFeedback)

    @Query("SELECT COUNT(*) FROM conversation_feedback WHERE isPositive = 1")
    suspend fun getPositiveCount(): Int

    @Query("SELECT COUNT(*) FROM conversation_feedback WHERE isPositive = 0")
    suspend fun getNegativeCount(): Int

    @Query("DELETE FROM conversation_feedback")
    suspend fun deleteAllFeedback()
}
