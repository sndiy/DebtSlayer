// File: app/src/main/java/com/hyse/debtslayer/ui/screens/ChatScreen.kt
package com.hyse.debtslayer.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.ui.components.ChatBubble
import com.hyse.debtslayer.ui.components.ChatInputField
import com.hyse.debtslayer.ui.components.DebtProgressCard
import com.hyse.debtslayer.viewmodel.DebtViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// ── Network state flow ────────────────────────────────────────────────────────
@Composable
fun rememberNetworkState(): State<Boolean> {
    val context = LocalContext.current
    val flow = remember {
        callbackFlow {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trySend(true) }
                override fun onLost(network: Network) { trySend(isConnected(context)) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    trySend(
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    )
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }
    }
    return flow.collectAsState(initial = isConnected(context))
}

private fun isConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}

// ── No Internet Overlay ───────────────────────────────────────────────────────
@Composable
fun NoInternetOverlay() {
    // Pulse animation untuk icon wifi off
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D).copy(alpha = 0.92f),
                        Color(0xFF1A0000).copy(alpha = 0.96f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            // Icon dengan efek pulse ring
            Box(contentAlignment = Alignment.Center) {
                // Ring luar (pulse)
                Box(
                    modifier = Modifier
                        .size((120 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = pulseAlpha))
                )
                // Ring tengah
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = 0.18f))
                )
                // Icon utama
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalWifiOff,
                        contentDescription = "No Internet",
                        tint = Color(0xFFEF9A9A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Teks
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tidak Ada Koneksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Mai tidak bisa dihubungi sekarang.\nCek WiFi atau Data Seluler kamu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Chip status
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFEF5350).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF5350))
                    )
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFEF9A9A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Reconnected Snackbar Banner ───────────────────────────────────────────────
@Composable
fun ReconnectedBanner(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E7D32))
                .padding(vertical = 8.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Koneksi kembali! Mai siap diajak ngobrol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── ChatScreen ────────────────────────────────────────────────────────────────
@Composable
fun ChatScreen(viewModel: DebtViewModel) {
    val messages by viewModel.messages.collectAsState()
    val debtState by viewModel.debtState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val depositConfirmation by viewModel.depositConfirmation.collectAsState()

    val isConnected by rememberNetworkState()

    // Track koneksi sebelumnya untuk deteksi reconnect
    var wasDisconnected by remember { mutableStateOf(false) }
    var showReconnectedBanner by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            wasDisconnected = true
            // ✅ Cancel request yang sedang berjalan saat internet mati
            viewModel.cancelPendingMessage()
        } else if (wasDisconnected && isConnected) {
            // Baru reconnect — tampilkan banner sebentar
            showReconnectedBanner = true
            coroutineScope.launch {
                kotlinx.coroutines.delay(2500)
                showReconnectedBanner = false
                wasDisconnected = false
            }
        }
    }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(depositConfirmation) {
        val amount = depositConfirmation ?: return@LaunchedEffect
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
        snackbarHostState.showSnackbar(
            message = "✅ Setoran Rp ${formatter.format(amount)} berhasil disimpan",
            duration = SnackbarDuration.Short
        )
        viewModel.dismissDepositConfirmation()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Konten chat utama ──
            Column(modifier = Modifier.fillMaxSize()) {
                // Banner reconnected (di atas)
                ReconnectedBanner(visible = showReconnectedBanner)

                DebtProgressCard(debtState = debtState)

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(messages, key = { it.messageId }) { message ->
                        ChatBubble(
                            message = message,
                            isDarkTheme = isDarkTheme,
                            onFeedback = if (!message.feedbackGiven) {
                                { isPositive -> viewModel.giveFeedback(message.messageId, isPositive) }
                            } else null,
                            feedbackGiven = message.feedbackGiven,
                            feedbackIsPositive = message.feedbackIsPositive
                        )
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Mai sedang mengetik...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                ChatInputField(
                    onSend = { text -> viewModel.sendMessage(text) },
                    enabled = !isLoading && isConnected
                )
            }

            // ── Overlay no internet (di atas semua konten) ──
            AnimatedVisibility(
                visible = !isConnected,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                NoInternetOverlay()
            }
        }
    }
}