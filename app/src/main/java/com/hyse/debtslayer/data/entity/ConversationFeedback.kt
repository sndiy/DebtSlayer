package com.hyse.debtslayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_feedback")
data class ConversationFeedback(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userMessage: String,
    val aiResponse: String,
    val isPositive: Boolean, // true = 👍, false = 👎
    val timestamp: Long,
    val context: String // JSON string berisi context
)