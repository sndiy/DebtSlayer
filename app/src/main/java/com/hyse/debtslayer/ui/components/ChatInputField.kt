package com.hyse.debtslayer.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hyse.debtslayer.viewmodel.LoadingStatus
import java.util.Locale

@Composable
fun ChatInputField(
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingStatus: LoadingStatus = LoadingStatus.IDLE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val isSendable = !isLoading && text.isNotBlank()

    // SpeechRecognizer
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    // Cleanup saat composable dispose
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    // Setup recognition listener
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isRecording = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isRecording = false }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                text = partial
            }

            override fun onResults(results: Bundle?) {
                isRecording = false
                val result = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                text = result

                // Auto-send kalau ada kata kunci deposit
                val depositKeywords = listOf("setor", "nabung", "bayar", "cicil", "transfer")
                val hasDepositKeyword = depositKeywords.any { result.lowercase().contains(it) }
                if (hasDepositKeyword && result.isNotBlank()) {
                    onSend(result)
                    text = ""
                }
            }

            override fun onError(error: Int) {
                isRecording = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH       -> "Tidak terdengar, coba lagi"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout, coba lagi"
                    SpeechRecognizer.ERROR_NETWORK        -> "Butuh koneksi internet"
                    else                                  -> null
                }
                if (errorMsg != null) text = errorMsg
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        isRecording = false
    }

    // Animasi pulse saat recording
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    val placeholderText = when {
        isRecording     -> "Sedang mendengarkan..."
        !enabled        -> "Offline — ketik 'help' untuk bantuan"
        loadingStatus == LoadingStatus.CONNECTING          -> "Menghubungi Mai..."
        loadingStatus == LoadingStatus.WAITING_RESPONSE    -> "Mai sedang mengetik..."
        loadingStatus == LoadingStatus.FALLBACK_TRYING     -> "Beralih ke model cadangan..."
        loadingStatus == LoadingStatus.FALLBACK_CONNECTING -> "Menghubungi model cadangan..."
        loadingStatus == LoadingStatus.ERROR_BOTH_LIMIT    -> "Mode offline — ketik 'help'"
        else            -> "Ketik pesan..."
    }

    val placeholderColor = when {
        isRecording -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
        !enabled    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        loadingStatus == LoadingStatus.FALLBACK_TRYING ||
                loadingStatus == LoadingStatus.FALLBACK_CONNECTING ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
        loadingStatus == LoadingStatus.ERROR_BOTH_LIMIT ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    Surface(
        modifier      = modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Mic Button ───────────────────────────────────────────────
            Box(
                modifier         = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulse ring saat recording
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(micScale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            )
                    )
                }

                IconButton(
                    onClick = {
                        when {
                            !SpeechRecognizer.isRecognitionAvailable(context) -> {
                                // Speech Recognition tidak tersedia
                            }
                            !hasMicPermission -> {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            isRecording -> stopListening()
                            else        -> startListening()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.MicOff
                        else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Voice input",
                        tint = when {
                            isRecording    -> MaterialTheme.colorScheme.error
                            hasMicPermission -> MaterialTheme.colorScheme.primary
                            else           -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Text Field ───────────────────────────────────────────────
            TextField(
                value         = text,
                onValueChange = { text = it },
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    AnimatedContent(
                        targetState  = placeholderText,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                        label        = "placeholder_anim"
                    ) { label ->
                        Text(text = label, color = placeholderColor)
                    }
                },
                enabled    = true,
                singleLine = true,
                colors     = TextFieldDefaults.colors(
                    focusedContainerColor   = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor  = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    disabledIndicatorColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // ── Send / Stop Button ───────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState  = isLoading,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label        = "send_stop_btn"
                ) { loading ->
                    if (loading) {
                        IconButton(onClick = onStop) {
                            Icon(
                                imageVector        = Icons.Default.Stop,
                                contentDescription = "Hentikan",
                                tint               = MaterialTheme.colorScheme.error,
                                modifier           = Modifier.size(22.dp)
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
                                imageVector        = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim",
                                tint               = if (isSendable)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier           = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}