package com.hyse.debtslayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hyse.debtslayer.data.dao.ChatMessageDao
import com.hyse.debtslayer.data.dao.ConversationHistoryDao
import com.hyse.debtslayer.data.dao.FeedbackDao
import com.hyse.debtslayer.data.dao.TransactionDao
import com.hyse.debtslayer.data.entity.ChatMessageEntity
import com.hyse.debtslayer.data.entity.ConversationFeedback
import com.hyse.debtslayer.data.entity.ConversationHistory
import com.hyse.debtslayer.data.entity.Transaction

@Database(
    entities = [
        Transaction::class,
        ConversationFeedback::class,
        ConversationHistory::class,
        ChatMessageEntity::class
    ],
    version = 5,  // ✅ Naik ke 5 (tambah kolom feedbackGiven & feedbackIsPositive)
    exportSchema = false
)
abstract class DebtDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun conversationHistoryDao(): ConversationHistoryDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: DebtDatabase? = null

        fun getDatabase(context: Context): DebtDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DebtDatabase::class.java,
                    "debt_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}