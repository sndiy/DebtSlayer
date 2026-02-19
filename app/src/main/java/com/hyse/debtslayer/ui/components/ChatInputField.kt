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
import com.hyse.debtslayer.viewmodel.LoadingStatus

@Composable
fun ChatInputField(
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingStatus: LoadingStatus = LoadingStatus.IDLE,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    // Input selalu bisa diketik — offline pun tetap bisa untuk perintah lokal
    // Tombol send disable hanya saat: loading ATAU teks kosong
    val isSendable = !isLoading && text.isNotBlank()

    val placeholderText = when {
        !enabled        -> "Offline — ketik 'help' untuk bantuan"
        loadingStatus == LoadingStatus.CONNECTING       -> "Menghubungi Mai..."
        loadingStatus == LoadingStatus.WAITING_RESPONSE -> "Mai sedang mengetik..."
        loadingStatus == LoadingStatus.FALLBACK_TRYING  -> "Beralih ke model cadangan..."
        loadingStatus == LoadingStatus.FALLBACK_CONNECTING -> "Menghubungi model cadangan..."
        loadingStatus == LoadingStatus.ERROR_BOTH_LIMIT -> "Mode offline — ketik 'help'"
        else            -> "Ketik pesan..."
    }

    // Warna placeholder — lebih kontras, tidak italic
    val placeholderColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        loadingStatus == LoadingStatus.FALLBACK_TRYING ||
                loadingStatus == LoadingStatus.FALLBACK_CONNECTING ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
        loadingStatus == LoadingStatus.ERROR_BOTH_LIMIT ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    AnimatedContent(
                        targetState = placeholderText,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                        label = "placeholder_anim"
                    ) { label ->
                        Text(text = label, color = placeholderColor)
                    }
                },
                enabled = true, // selalu bisa input, offline pun tetap bisa
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Send ↔ Stop
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "send_stop_btn"
                ) { loading ->
                    if (loading) {
                        IconButton(onClick = onStop) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Hentikan",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (text.isNotBlank()) { onSend(text); text = "" }
                            },
                            enabled = isSendable
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = if (isSendable)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
