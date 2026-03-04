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
import com.hyse.debtslayer.data.preferences.SyncPreferences
import com.hyse.debtslayer.data.sync.CloudSyncRepository
import com.hyse.debtslayer.ui.theme.SuccessGreen
import com.hyse.debtslayer.viewmodel.AuthViewModel
import com.hyse.debtslayer.viewmodel.DebtViewModel
import com.hyse.debtslayer.worker.AutoSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppScreen { MAIN, STATISTICS, LOGIN }

private enum class InitScreen {
    CHECKING, AUTH, CHECKING_CLOUD, ONBOARDING, FIRST_LOADING, LOADING, READY
}

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

    val authRepository = remember { AuthRepository() }
    val authViewModel  = remember { AuthViewModel(authRepository) }
    val currentUser    by authViewModel.currentUser.collectAsState()
    val syncPrefs      = remember { SyncPreferences(context) }

    // ✅ Pakai rememberCoroutineScope agar coroutine terikat lifecycle Composable,
    // tidak bocor seperti MainScope().launch
    val scope = rememberCoroutineScope()

    var initScreen    by remember { mutableStateOf(InitScreen.CHECKING) }
    var appReady      by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedItem  by remember { mutableStateOf(0) }

    // ── Back handler ──────────────────────────────────────────────────────────
    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        currentScreen = AppScreen.MAIN
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 1: Routing awal — dijalankan SEKALI saat DataStore lokal siap
    // ══════════════════════════════════════════════════════════════════════════
    LaunchedEffect(isOnboardingDone) {
        if (isOnboardingDone == null) return@LaunchedEffect
        if (initScreen != InitScreen.CHECKING) return@LaunchedEffect

        initScreen = if (isOnboardingDone == true) {
            InitScreen.LOADING
        } else {
            InitScreen.AUTH
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 2: Cek data cloud setelah login
    //
    // FIX: key hanya initScreen — currentUser di-wait eksplisit di dalam
    // coroutine untuk menghindari race condition StateFlow.
    // ══════════════════════════════════════════════════════════════════════════
    LaunchedEffect(initScreen) {
        if (initScreen != InitScreen.CHECKING_CLOUD) return@LaunchedEffect

        // Tunggu currentUser tersedia (max 5 detik)
        var waited = 0
        while (currentUser == null && waited < 5000) {
            kotlinx.coroutines.delay(100)
            waited += 100
        }

        if (currentUser == null) {
            initScreen = InitScreen.ONBOARDING
            return@LaunchedEffect
        }

        try {
            val freq = syncPrefs.syncFrequency.first()
            AutoSyncWorker.schedule(context, freq)

            val syncRepo = CloudSyncRepository(
                authRepository        = authRepository,
                transactionRepository = viewModel.getTransactionRepository()
            )

            val hasCloud = syncRepo.hasCloudData()

            if (hasCloud) {
                val result = syncRepo.downloadAll()

                val cloudDebt     = result.totalDebt
                val cloudDeadline = result.deadline

                if (cloudDebt != null && cloudDebt > 0 && !cloudDeadline.isNullOrBlank()) {
                    // Ada data hutang di cloud → apply langsung, skip onboarding
                    viewModel.applyCloudDataAndCompleteOnboarding(cloudDebt, cloudDeadline)

                    if (!result.personalityMode.isNullOrBlank()) {
                        try {
                            val mode = com.hyse.debtslayer.personality.AdaptiveMaiPersonality
                                .PersonalityMode.valueOf(result.personalityMode)
                            viewModel.setPersonalityMode(mode)
                        } catch (e: Exception) { /* mode tidak dikenal → skip */ }
                    }
                    if (result.reminderHour != null && result.reminderMinute != null) {
                        viewModel.saveReminderTime(result.reminderHour, result.reminderMinute)
                    }
                    if (!result.setupDate.isNullOrBlank()) {
                        viewModel.applySetupDate(result.setupDate)
                    }

                    // ✅ FIX: langsung READY — tidak lewat LoadingScreen karena
                    // applyCloudDataAndCompleteOnboarding tidak trigger isDataReady
                    initScreen = InitScreen.READY
                    appReady   = true

                } else {
                    // Akun ada di Firestore tapi belum punya totalDebt/deadline
                    // → ke onboarding, setelah selesai langsung upload ke cloud
                    initScreen = InitScreen.ONBOARDING
                }
            } else {
                // Tidak ada data cloud sama sekali → onboarding
                initScreen = InitScreen.ONBOARDING
            }
        } catch (e: Exception) {
            // Gagal cek cloud (offline, error) → fallback ke onboarding
            initScreen = InitScreen.ONBOARDING
        }
    }

    // ── Auto-schedule sync saat user sudah login & app sudah jalan normal ────
    LaunchedEffect(currentUser, appReady) {
        if (currentUser == null || !appReady) return@LaunchedEffect
        try {
            val freq = syncPrefs.syncFrequency.first()
            AutoSyncWorker.schedule(context, freq)
        } catch (e: Exception) { /* abaikan */ }
    }

    // ── Cancel sync saat logout ───────────────────────────────────────────────
    LaunchedEffect(currentUser) {
        if (currentUser == null) AutoSyncWorker.cancel(context)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER
    // ══════════════════════════════════════════════════════════════════════════

    if (isOnboardingDone == null || initScreen == InitScreen.CHECKING) {
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    if (initScreen == InitScreen.AUTH) {
        LoginScreen(
            authViewModel     = authViewModel,
            onLoginSuccess    = { initScreen = InitScreen.CHECKING_CLOUD },
            onSignUpSuccess   = { initScreen = InitScreen.ONBOARDING },
            onBack            = { /* tidak bisa back di layar awal */ },
            onContinueAsGuest = { initScreen = InitScreen.ONBOARDING }
        )
        return
    }

    if (initScreen == InitScreen.CHECKING_CLOUD) {
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    if (initScreen == InitScreen.ONBOARDING) {
        OnboardingScreen(
            viewModel  = viewModel,
            onFinished = { initScreen = InitScreen.FIRST_LOADING },
            // ✅ FIX: Upload langsung ke cloud setelah onboarding selesai
            // jika user sudah login — tidak nunggu AutoSyncWorker.
            // Menggunakan rememberCoroutineScope() agar tidak bocor memory.
            onUploadToCloud = if (currentUser != null) { totalDebt, deadline ->
                scope.launch {
                    try {
                        val syncRepo = CloudSyncRepository(
                            authRepository        = authRepository,
                            transactionRepository = viewModel.getTransactionRepository()
                        )
                        syncRepo.uploadAll(
                            totalDebt = totalDebt,
                            deadline  = deadline
                        )
                    } catch (e: Exception) {
                        // Gagal upload → AutoSyncWorker akan retry nanti
                    }
                }
                Unit  // pastikan lambda return Unit
            } else null  // Guest → tidak upload
        )
        return
    }

    if (initScreen == InitScreen.FIRST_LOADING) {
        LoadingScreen(
            viewModel    = viewModel,
            isFirstSetup = true,
            onReady = {
                initScreen = InitScreen.READY
                appReady   = true
            }
        )
        return
    }

    if (initScreen == InitScreen.LOADING && !appReady) {
        LoadingScreen(
            viewModel    = viewModel,
            isFirstSetup = false,
            onReady      = { appReady = true }
        )
        return
    }

    if (currentScreen == AppScreen.STATISTICS) {
        StatisticsScreen(
            viewModel = viewModel,
            onBack    = { currentScreen = AppScreen.MAIN }
        )
        return
    }

    if (currentScreen == AppScreen.LOGIN) {
        if (currentUser != null) {
            AccountScreen(
                user          = currentUser!!,
                authViewModel = authViewModel,
                viewModel     = viewModel,
                onBack        = { currentScreen = AppScreen.MAIN }
            )
        } else {
            LoginScreen(
                authViewModel     = authViewModel,
                onLoginSuccess    = { currentScreen = AppScreen.MAIN },
                onBack            = { currentScreen = AppScreen.MAIN },
                onContinueAsGuest = null
            )
        }
        return
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN UI
    // ══════════════════════════════════════════════════════════════════════════
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
                            BadgedBox(badge = { Badge(containerColor = SuccessGreen) }) {
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
                    containerColor         = MaterialTheme.colorScheme.primary,
                    titleContentColor      = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.Chat, "Chat") },
                    label    = { Text("Chat") },
                    selected = selectedItem == 0,
                    onClick  = { selectedItem = 0 }
                )
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.CalendarMonth, "Kalender") },
                    label    = { Text("Kalender") },
                    selected = selectedItem == 1,
                    onClick  = { selectedItem = 1 }
                )
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.History, "Riwayat") },
                    label    = { Text("Riwayat") },
                    selected = selectedItem == 2,
                    onClick  = { selectedItem = 2 }
                )
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.Settings, "Settings") },
                    label    = { Text("Settings") },
                    selected = selectedItem == 3,
                    onClick  = { selectedItem = 3 }
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
                    viewModel     = viewModel,
                    authViewModel = authViewModel,
                    onShowLogin   = { currentScreen = AppScreen.LOGIN }
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