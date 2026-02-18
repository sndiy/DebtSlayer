package com.hyse.debtslayer.data.repository

import com.hyse.debtslayer.data.dao.FeedbackDao
import com.hyse.debtslayer.data.entity.ConversationFeedback

class FeedbackRepository(private val feedbackDao: FeedbackDao) {

    suspend fun insert(feedback: ConversationFeedback) {
        feedbackDao.insertFeedback(feedback)
    }

    suspend fun getPositiveCount(): Int {
        return feedbackDao.getPositiveCount()
    }

    suspend fun getNegativeCount(): Int {
        return feedbackDao.getNegativeCount()
    }

    suspend fun deleteAll() {
        feedbackDao.deleteAllFeedback()
    }
}
