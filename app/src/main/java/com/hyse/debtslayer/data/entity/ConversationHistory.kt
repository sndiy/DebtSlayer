// File: app/src/main/java/com/yourname/debtslayer/data/entity/ConversationHistory.kt
package com.hyse.debtslayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_history")
data class ConversationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userMessage: String,
    val aiResponse: String,
    val timestamp: Long,
    val wasSuccessful: Boolean = true, // true jika user lanjut chat, false jika langsung thumbs down
    val context: String = "" // Context saat itu (target, progress, dll)
)