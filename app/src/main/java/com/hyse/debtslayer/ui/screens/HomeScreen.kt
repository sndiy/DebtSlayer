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

enum class AppScreen { MAIN, STATISTICS, LOGIN }

// ── Init screen states ────────────────────────────────────────────────────────
// CHECKING        : Menunggu DataStore lokal emit isOnboardingDone
// AUTH            : Baru install → tampilkan login / guest
// CHECKING_CLOUD  : Login berhasil → sedang cek data di Firestore
// ONBOARDING      : Akun baru / guest / tidak ada data cloud → isi hutang & deadline
// FIRST_LOADING   : Loading setelah onboarding pertama selesai
// LOADING         : Loading normal tiap buka app (sudah pernah setup)
// READY           : App siap setelah first loading
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

    var initScreen  by remember { mutableStateOf(InitScreen.CHECKING) }
    var appReady    by remember { mutableStateOf(false) }
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
        if (isOnboardingDone == null) return@LaunchedEffect        // DataStore belum siap
        if (initScreen != InitScreen.CHECKING) return@LaunchedEffect // sudah diproses

        initScreen = if (isOnboardingDone == true) {
            // ✅ Sudah pernah setup di perangkat ini → langsung loading
            InitScreen.LOADING
        } else {
            // Baru install / data terhapus → perlu AUTH
            InitScreen.AUTH
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 2: Setelah login berhasil → cek cloud (CHECKING_CLOUD state)
    //
    // Saat initScreen == CHECKING_CLOUD dan currentUser sudah ada,
    // download data cloud. Kalau ada totalDebt & deadline → apply ke lokal
    // dan set onboardingDone = true → langsung LOADING.
    // Kalau tidak ada data cloud → ONBOARDING.
    // ══════════════════════════════════════════════════════════════════════════
    LaunchedEffect(initScreen, currentUser) {
        if (initScreen != InitScreen.CHECKING_CLOUD) return@LaunchedEffect
        if (currentUser == null) return@LaunchedEffect

        try {
            val freq = syncPrefs.syncFrequency.first()
            AutoSyncWorker.schedule(context, freq)

            val syncRepo = CloudSyncRepository(
                authRepository = authRepository,
                transactionRepository = viewModel.getTransactionRepository()
            )

            val hasCloud = syncRepo.hasCloudData()

            if (hasCloud) {
                // Ada data di cloud → download dulu lalu apply
                val result = syncRepo.downloadAll()

                val cloudDebt     = result.totalDebt
                val cloudDeadline = result.deadline

                if (cloudDebt != null && cloudDebt > 0 && !cloudDeadline.isNullOrBlank()) {
                    // Apply ke ViewModel & tandai onboarding selesai di lokal
                    viewModel.applyCloudDataAndCompleteOnboarding(cloudDebt, cloudDeadline)
                    // Langsung LOADING — tidak perlu onboarding lagi
                    initScreen = InitScreen.LOADING
                } else {
                    // Ada dokumen user di cloud tapi tidak ada debt/deadline → onboarding
                    initScreen = InitScreen.ONBOARDING
                }
            } else {
                // Tidak ada data cloud sama sekali → onboarding
                initScreen = InitScreen.ONBOARDING
            }
        } catch (e: Exception) {
            // Gagal cek cloud (offline, dll) → fallback ke onboarding
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

    // ── CHECKING: DataStore lokal belum siap ─────────────────────────────────
    if (isOnboardingDone == null || initScreen == InitScreen.CHECKING) {
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    // ── AUTH: Baru install / data terhapus ───────────────────────────────────
    if (initScreen == InitScreen.AUTH) {
        LoginScreen(
            authViewModel = authViewModel,
            onLoginSuccess = {
                // Login berhasil → cek data di cloud dulu sebelum putuskan
                // apakah perlu onboarding atau langsung masuk app
                initScreen = InitScreen.CHECKING_CLOUD
            },
            onBack = { /* tidak bisa back di layar awal */ },
            onContinueAsGuest = {
                // Guest tidak punya cloud data → langsung onboarding
                initScreen = InitScreen.ONBOARDING
            }
        )
        return
    }

    // ── CHECKING_CLOUD: Sedang download & cek data Firestore ─────────────────
    if (initScreen == InitScreen.CHECKING_CLOUD) {
        // Tampilkan loading dengan pesan yang sesuai
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    // ── ONBOARDING: Hanya untuk akun baru / guest / tidak ada data cloud ─────
    if (initScreen == InitScreen.ONBOARDING) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = { initScreen = InitScreen.FIRST_LOADING }
        )
        return
    }

    // ── FIRST LOADING: Setelah onboarding pertama ────────────────────────────
    if (initScreen == InitScreen.FIRST_LOADING) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = true,
            onReady = {
                initScreen = InitScreen.READY
                appReady   = true
            }
        )
        return
    }

    // ── LOADING: Loading normal buka app (user lama) ─────────────────────────
    if (initScreen == InitScreen.LOADING && !appReady) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = false,
            onReady = { appReady = true }
        )
        return
    }

    // ── STATISTICS ────────────────────────────────────────────────────────────
    if (currentScreen == AppScreen.STATISTICS) {
        StatisticsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = AppScreen.MAIN }
        )
        return
    }

    // ── LOGIN / ACCOUNT (tombol akun di TopAppBar, bukan alur install) ────────
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
                onBack = { currentScreen = AppScreen.MAIN },
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
                    containerColor      = MaterialTheme.colorScheme.primary,
                    titleContentColor   = MaterialTheme.colorScheme.onPrimary,
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