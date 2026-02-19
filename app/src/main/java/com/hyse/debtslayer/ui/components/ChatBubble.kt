package com.hyse.debtslayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.ui.theme.ChatAiBubbleDark
import com.hyse.debtslayer.ui.theme.ChatAiBubbleLight
import com.hyse.debtslayer.ui.theme.ChatUserBubbleDark
import com.hyse.debtslayer.ui.theme.ChatUserBubbleLight
import com.hyse.debtslayer.viewmodel.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    isDarkTheme: Boolean,
    onFeedback: ((Boolean) -> Unit)? = null,
    feedbackGiven: Boolean = message.feedbackGiven,
    feedbackIsPositive: Boolean? = message.feedbackIsPositive
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = when {
                        message.isFromUser && isDarkTheme -> ChatUserBubbleDark
                        message.isFromUser && !isDarkTheme -> ChatUserBubbleLight
                        !message.isFromUser && isDarkTheme -> ChatAiBubbleDark
                        else -> ChatAiBubbleLight
                    },
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    // ✅ FIX: teks bubble sekarang selalu putih di dark theme
                    // ChatUserBubbleDark (#1E3A5F) dan ChatAiBubbleDark (#6A1B9A) keduanya gelap
                    // jadi teks putih selalu kontras. Di light theme pakai onSurface (gelap).
                    color = if (isDarkTheme) Color.White
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        // ✅ FIX: timestamp juga pakai warna yang readable
                        color = if (isDarkTheme)
                            Color.White.copy(alpha = 0.65f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    if (!message.isFromUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                // ✅ Sudah klik 👍 → tampil HIJAU saja, tidak bisa diklik
                                feedbackGiven && feedbackIsPositive == true -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = "Disukai",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                                // ✅ Sudah klik 👎 → tampil MERAH saja, tidak bisa diklik
                                feedbackGiven && feedbackIsPositive == false -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbDown,
                                            contentDescription = "Tidak disukai",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFF44336)
                                        )
                                    }
                                }
                                // ✅ Belum feedback → tampil 2 tombol aktif
                                !feedbackGiven && onFeedback != null -> {
                                    IconButton(onClick = { onFeedback(true) }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            Icons.Default.ThumbUp, "Suka",
                                            modifier = Modifier.size(16.dp),
                                            // ✅ FIX: icon feedback juga readable di dark
                                            tint = if (isDarkTheme)
                                                Color.White.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                    IconButton(onClick = { onFeedback(false) }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            Icons.Default.ThumbDown, "Tidak suka",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isDarkTheme)
                                                Color.White.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}