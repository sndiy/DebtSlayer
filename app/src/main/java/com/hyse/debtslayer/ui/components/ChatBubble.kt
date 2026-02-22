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
import com.hyse.debtslayer.viewmodel.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Warna bubble yang jelas berbeda antara user dan AI
private val UserBubbleDark  = Color(0xFF4A148C) // ungu tua — user (kanan)
private val AiBubbleDark    = Color(0xFF1A237E) // biru tua — Mai (kiri)
private val UserBubbleLight = Color(0xFF7B1FA2) // ungu medium — user
private val AiBubbleLight   = Color(0xFF1565C0) // biru medium — Mai

@Composable
fun ChatBubble(
    message: ChatMessage,
    isDarkTheme: Boolean,
    onFeedback: ((Boolean) -> Unit)? = null,
    feedbackGiven: Boolean = message.feedbackGiven,
    feedbackIsPositive: Boolean? = message.feedbackIsPositive
) {
    val bubbleColor = when {
        message.isFromUser && isDarkTheme  -> UserBubbleDark
        message.isFromUser && !isDarkTheme -> UserBubbleLight
        !message.isFromUser && isDarkTheme -> AiBubbleDark
        else                               -> AiBubbleLight
    }

    // User: sudut kanan bawah lancip, AI: sudut kiri bawah lancip
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
        bottomEnd   = if (message.isFromUser) 4.dp  else 16.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(color = bubbleColor, shape = bubbleShape)
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
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
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    if (!message.isFromUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                feedbackGiven && feedbackIsPositive == true -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = "Disukai",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF69F0AE)
                                        )
                                    }
                                }
                                feedbackGiven && feedbackIsPositive == false -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbDown,
                                            contentDescription = "Tidak disukai",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFFF5252)
                                        )
                                    }
                                }
                                !feedbackGiven && onFeedback != null -> {
                                    IconButton(
                                        onClick = { onFeedback(true) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbUp, "Suka",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onFeedback(false) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbDown, "Tidak suka",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White.copy(alpha = 0.5f)
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