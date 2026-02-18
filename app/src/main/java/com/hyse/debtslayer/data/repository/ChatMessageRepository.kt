package com.hyse.debtslayer.data.repository

import com.hyse.debtslayer.data.dao.ChatMessageDao
import com.hyse.debtslayer.data.entity.ChatMessageEntity
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageRepository(private val dao: ChatMessageDao) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun today() = dateFormat.format(Date())
    private fun daysAgo(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return dateFormat.format(cal.time)
    }

    suspend fun saveMessage(text: String, isFromUser: Boolean): Long =
        dao.insert(ChatMessageEntity(text = text, isFromUser = isFromUser, sessionDate = today()))

    suspend fun getTodayMessages(): List<ChatMessageEntity> =
        dao.getTodayMessages(today())

    suspend fun cleanOldMessages() =
        dao.deleteOlderThan(daysAgo(7))

    suspend fun hasTodayMessages(): Boolean =
        dao.countTodayMessages(today()) > 0

    suspend fun saveFeedback(messageId: Long, isPositive: Boolean) =
        dao.updateFeedback(messageId, isPositive)

    suspend fun deleteAllMessages() =
        dao.deleteAll()
}