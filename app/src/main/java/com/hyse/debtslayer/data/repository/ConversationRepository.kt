package com.hyse.debtslayer.data.repository

import com.hyse.debtslayer.data.dao.ConversationHistoryDao
import com.hyse.debtslayer.data.entity.ConversationHistory

class ConversationRepository(private val dao: ConversationHistoryDao) {

    suspend fun insert(conversation: ConversationHistory) {
        dao.insertConversation(conversation)
    }

    suspend fun getSuccessfulConversations(limit: Int = 10): List<ConversationHistory> {
        return dao.getSuccessfulConversations(limit)
    }

    suspend fun getConversationCount(): Int {
        return dao.getConversationCount()
    }

    suspend fun cleanOldConversations() {
        val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteOldConversations(cutoffTime)
    }
}
