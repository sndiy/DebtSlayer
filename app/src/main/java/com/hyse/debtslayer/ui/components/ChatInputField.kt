package com.hyse.debtslayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputField(
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    enabled: Boolean = true,      // false saat offline
    isLoading: Boolean = false,   // true saat Mai sedang mengetik
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    val isSendable = enabled && !isLoading && text.isNotBlank()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            !enabled -> "Tidak ada koneksi..."
                            isLoading -> "Mai sedang membalas..."
                            else -> "Ketik pesan..."
                        }
                    )
                },
                enabled = enabled && !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Tombol Send / Stop
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    },
                    label = "send_stop_btn"
                ) { loading ->
                    if (loading) {
                        // Tombol Stop
                        IconButton(onClick = onStop) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Hentikan",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // Tombol Send
                        IconButton(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onSend(text)
                                    text = ""
                                }
                            },
                            enabled = isSendable
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = if (isSendable)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}