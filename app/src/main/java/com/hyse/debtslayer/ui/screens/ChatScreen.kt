package com.hyse.debtslayer.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.ui.components.ChatBubble
import com.hyse.debtslayer.ui.components.ChatInputField
import com.hyse.debtslayer.ui.components.DebtProgressCard
import com.hyse.debtslayer.viewmodel.DebtViewModel
import com.hyse.debtslayer.viewmodel.LoadingStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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

// ── Connection Banner — compact, satu baris ───────────────────────────────────
@Composable
fun ConnectionStatusBanner(isConnected: Boolean, showReconnected: Boolean) {
    AnimatedVisibility(
        visible = !isConnected || showReconnected,
        enter = slideInVertically { -it } + fadeIn(tween(200)),
        exit = slideOutVertically { -it } + fadeOut(tween(250))
    ) {
        val isOffline = !isConnected
        val bgColor = if (isOffline) Color(0xFFB71C1C) else Color(0xFF1B5E20)
        val icon = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.Wifi
        // Saat offline: label utama + hint ketik help di satu baris
        val text = if (isOffline)
            "Tidak ada koneksi  •  ketik 'help'"
        else
            "Koneksi kembali! Mai siap diajak ngobrol."

        Surface(color = bgColor, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = if (isOffline) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

// ── Loading indicator ─────────────────────────────────────────────────────────
@Composable
fun LoadingIndicator(status: LoadingStatus) {
    val (labelText, indicatorColor) = when (status) {
        LoadingStatus.CONNECTING ->
            "Menghubungi Mai..." to MaterialTheme.colorScheme.primary
        LoadingStatus.WAITING_RESPONSE ->
            "Mai sedang mengetik..." to MaterialTheme.colorScheme.primary
        LoadingStatus.FALLBACK_TRYING ->
            "Model utama limit, beralih ke cadangan..." to MaterialTheme.colorScheme.tertiary
        LoadingStatus.FALLBACK_CONNECTING ->
            "Menghubungi Gemini Flash (cadangan)..." to MaterialTheme.colorScheme.tertiary
        LoadingStatus.ERROR_BOTH_LIMIT ->
            "Semua model limit, aktifkan mode offline..." to MaterialTheme.colorScheme.error
        else -> return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = indicatorColor
            )
            AnimatedContent(
                targetState = labelText,
                transitionSpec = {
                    fadeIn(tween(200)) + slideInVertically { it / 3 } togetherWith fadeOut(tween(150))
                },
                label = "loading_label"
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = indicatorColor.copy(alpha = 0.85f)
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
    val loadingStatus by viewModel.loadingStatus.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val depositConfirmation by viewModel.depositConfirmation.collectAsState()
    val isConnected by rememberNetworkState()

    var wasDisconnected by remember { mutableStateOf(false) }
    var showReconnectedBanner by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            wasDisconnected = true
            viewModel.cancelPendingMessage()
        } else if (wasDisconnected) {
            showReconnectedBanner = true
            coroutineScope.launch {
                delay(2500)
                showReconnectedBanner = false
                wasDisconnected = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 8.dp))
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Banner tipis langsung di bawah AppBar
            ConnectionStatusBanner(
                isConnected = isConnected,
                showReconnected = showReconnectedBanner
            )

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
                    item { LoadingIndicator(status = loadingStatus) }
                }
            }

            ChatInputField(
                onSend = { text -> viewModel.sendMessage(text) },
                onStop = { viewModel.cancelPendingMessage() },
                enabled = isConnected,
                isLoading = isLoading,
                loadingStatus = loadingStatus
            )
        }
    }
}