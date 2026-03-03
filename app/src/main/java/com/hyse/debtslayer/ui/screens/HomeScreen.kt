package com.hyse.debtslayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.data.auth.AuthRepository
import com.hyse.debtslayer.data.auth.UserData
import com.hyse.debtslayer.data.preferences.SyncPreferences
import com.hyse.debtslayer.ui.theme.SuccessGreen
import com.hyse.debtslayer.viewmodel.AuthViewModel
import com.hyse.debtslayer.viewmodel.DebtViewModel
import com.hyse.debtslayer.worker.AutoSyncWorker
import kotlinx.coroutines.flow.first

enum class AppScreen { MAIN, STATISTICS, LOGIN }

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
    val context = LocalContext.current

    var showOnboarding by remember { mutableStateOf(false) }
    var showFirstSetupLoading by remember { mutableStateOf(false) }
    var appReady by remember { mutableStateOf(false) }

    val authRepository = remember { AuthRepository() }
    val authViewModel = remember { AuthViewModel(authRepository) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val syncPrefs = remember { SyncPreferences(context) }

    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedItem by remember { mutableStateOf(0) }

    // ── Auto-reschedule saat login/logout ─────────────────────────
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val freq = syncPrefs.syncFrequency.first()
            AutoSyncWorker.schedule(context, freq)
        } else {
            AutoSyncWorker.cancel(context)
        }
    }

    // ── Back handler ──────────────────────────────────────────────
    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        currentScreen = AppScreen.MAIN
    }

    // ── Sinkronisasi onboarding ───────────────────────────────────
    LaunchedEffect(isOnboardingDone) {
        when (isOnboardingDone) {
            false -> showOnboarding = true
            true  -> showOnboarding = false
            null  -> {}
        }
    }

    if (isOnboardingDone == null) {
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

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

    if (!appReady && isOnboardingDone == true) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = false,
            onReady = { appReady = true }
        )
        return
    }

    if (currentScreen == AppScreen.STATISTICS) {
        StatisticsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = AppScreen.MAIN }
        )
        return
    }

    if (currentScreen == AppScreen.LOGIN) {
        if (currentUser != null) {
            AccountScreen(
                user = currentUser!!,
                authViewModel = authViewModel,
                viewModel = viewModel,
                onBack = { currentScreen = AppScreen.MAIN }
            )
        } else {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { currentScreen = AppScreen.MAIN },
                onBack = { currentScreen = AppScreen.MAIN }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedItem) {
                            0    -> "💬 Chat dengan Mai"
                            1    -> "📅 Kalender"
                            2    -> "📊 Riwayat Transaksi"
                            3    -> "⚙️ Pengaturan"
                            else -> "DebtSlayer"
                        }
                    )
                },
                actions = {
                    IconButton(onClick = { currentScreen = AppScreen.STATISTICS }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Statistik",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { currentScreen = AppScreen.LOGIN }) {
                        if (currentUser != null) {
                            BadgedBox(
                                badge = { Badge(containerColor = SuccessGreen) }
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Akun",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Akun",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (selectedItem) {
                0 -> ChatScreen(viewModel = viewModel)
                1 -> CalendarScreen(viewModel = viewModel)
                2 -> HistoryScreen(viewModel = viewModel)
                3 -> SettingsScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onShowLogin = { currentScreen = AppScreen.LOGIN }
                )
            }

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