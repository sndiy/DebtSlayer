// File: app/src/main/java/com/hyse/debtslayer/ui/screens/HomeScreen.kt
package com.hyse.debtslayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.viewmodel.DebtViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DebtViewModel,
    notifGranted: Boolean = true,
    showNotifBanner: Boolean = false,
    onRequestNotifPermission: () -> Unit = {},
    onDismissNotifBanner: () -> Unit = {}
) {
    val isOnboardingDone by viewModel.isOnboardingDone.collectAsState(initial = null)
    val isDataReady by viewModel.isDataReady.collectAsState()

    var showOnboarding by remember { mutableStateOf(false) }
    var showFirstSetupLoading by remember { mutableStateOf(false) }
    var appReady by remember { mutableStateOf(false) }

    // Sinkronisasi state onboarding
    LaunchedEffect(isOnboardingDone) {
        when (isOnboardingDone) {
            false -> showOnboarding = true  // belum pernah setup
            true -> showOnboarding = false  // sudah setup sebelumnya
            null -> {}                      // masih loading DataStore
        }
    }

    // ── CASE 1: DataStore belum diload ──
    if (isOnboardingDone == null) {
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    // ── CASE 2: Onboarding belum selesai ──
    if (showOnboarding && !showFirstSetupLoading) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = {
                showOnboarding = false
                showFirstSetupLoading = true
            }
        )
        return
    }

    // ── CASE 3: First setup loading — tunggu isDataReady dari ViewModel ──
    if (showFirstSetupLoading && !appReady) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = true,
            onReady = {
                showFirstSetupLoading = false
                appReady = true
            }
        )
        return
    }

    // ── CASE 4: Buka app normal (sudah pernah setup) — tunggu isDataReady ──
    if (!appReady && isOnboardingDone == true) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = false,
            onReady = { appReady = true }
        )
        return
    }

    var selectedItem by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedItem) {
                            0 -> "💬 Chat dengan Mai"
                            1 -> "📅 Kalender"
                            2 -> "📊 Riwayat Transaksi"
                            3 -> "⚙️ Pengaturan"
                            else -> "DebtSlayer"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = selectedItem == 0,
                    onClick = { selectedItem = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Kalender") },
                    label = { Text("Kalender") },
                    selected = selectedItem == 1,
                    onClick = { selectedItem = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Riwayat") },
                    label = { Text("Riwayat") },
                    selected = selectedItem == 2,
                    onClick = { selectedItem = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedItem == 3,
                    onClick = { selectedItem = 3 }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
        ) {
            // Screen content
            when (selectedItem) {
                0 -> ChatScreen(viewModel = viewModel)
                1 -> CalendarScreen(viewModel = viewModel)
                2 -> HistoryScreen(viewModel = viewModel)
                3 -> SettingsScreen(viewModel = viewModel)
            }

            // ── Banner izin notifikasi — muncul di bawah, bisa dismiss ──
            if (showNotifBanner && !notifGranted) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Aktifkan notifikasi reminder",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Agar Mai bisa ingatkan kamu setiap hari.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = onRequestNotifPermission) {
                            Text("Izinkan", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = onDismissNotifBanner,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, "Tutup",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}