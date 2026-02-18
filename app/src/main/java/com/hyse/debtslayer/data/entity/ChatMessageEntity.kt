// File: app/src/main/java/com/hyse/debtslayer/data/entity/ChatMessageEntity.kt
package com.hyse.debtslayer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDate: String,
    // ✅ BARU: Simpan status feedback ke database
    val feedbackGiven: Boolean = false,
    val feedbackIsPositive: Boolean? = null
)