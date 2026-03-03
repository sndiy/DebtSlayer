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
// CHECKING   : Menunggu DataStore emit nilai isOnboardingDone (bisa null sebentar)
// AUTH       : Baru install → perlu login/guest
// ONBOARDING : Setelah auth (akun baru / guest) → isi hutang & deadline
// FIRST_LOADING : Loading setelah onboarding selesai disimpan
// READY      : isOnboardingDone == true, app langsung buka (user lama)
// LOADING    : Loading normal saat buka app setelah READY ditentukan
private enum class InitScreen { CHECKING, AUTH, ONBOARDING, FIRST_LOADING, READY, LOADING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DebtViewModel,
    notifGranted: Boolean = true,
    showNotifBanner: Boolean = false,
    onRequestNotifPermission: () -> Unit = {},
    onDismissNotifBanner: () -> Unit = {}
) {
    // ── isOnboardingDone dari DataStore ───────────────────────────────────────
    // Nilai awal `null` artinya DataStore belum selesai baca → tetap CHECKING
    // `true`  → user sudah pernah setup → skip login, langsung LOADING
    // `false` → baru install / data terhapus → perlu AUTH
    val isOnboardingDone by viewModel.isOnboardingDone.collectAsState(initial = null)

    val context = LocalContext.current

    val authRepository = remember { AuthRepository() }
    val authViewModel = remember { AuthViewModel(authRepository) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val isNewAccount by authViewModel.isNewAccount.collectAsState()
    val syncPrefs = remember { SyncPreferences(context) }

    // initScreen diinisialisasi CHECKING — hanya berubah 1x saat isOnboardingDone sudah punya nilai
    var initScreen by remember { mutableStateOf(InitScreen.CHECKING) }
    var appReady by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedItem by remember { mutableStateOf(0) }

    // ── Back handler ──────────────────────────────────────────────────────────
    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        currentScreen = AppScreen.MAIN
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 1: Tentukan tampilan awal SEKALI saat DataStore sudah siap
    //
    // KEY FIX: hanya jalankan sekali (initScreen == CHECKING) agar tidak
    // ter-reset ulang di setiap recomposition / perubahan isOnboardingDone.
    // ══════════════════════════════════════════════════════════════════════════
    LaunchedEffect(isOnboardingDone) {
        // Tunggu sampai DataStore benar-benar punya nilai (bukan null)
        if (isOnboardingDone == null) return@LaunchedEffect
        // Hanya proses 1x — jika initScreen sudah berubah dari CHECKING, abaikan
        if (initScreen != InitScreen.CHECKING) return@LaunchedEffect

        if (isOnboardingDone == true) {
            // ✅ User sudah pernah setup → langsung loading normal, SKIP login
            initScreen = InitScreen.LOADING
        } else {
            // 🆕 Baru install / onboarding belum selesai → AUTH dulu
            initScreen = InitScreen.AUTH
        }
    }

    // ── STEP 2: Auto-download + reschedule saat login ─────────────────────────
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val freq = syncPrefs.syncFrequency.first()
            AutoSyncWorker.schedule(context, freq)

            try {
                val syncRepo = CloudSyncRepository(
                    authRepository = authRepository,
                    transactionRepository = viewModel.getTransactionRepository()
                )
                if (syncRepo.hasCloudData()) {
                    val result = syncRepo.downloadAll()

                    if (result.totalDebt != null && result.totalDebt > 0) {
                        viewModel.updateTotalDebtFromSettings(result.totalDebt)
                    }
                    if (!result.deadline.isNullOrBlank()) {
                        viewModel.updateDeadlineFromSettings(result.deadline)
                    }
                }
            } catch (e: Exception) { /* gagal → user bisa manual */ }
        } else {
            AutoSyncWorker.cancel(context)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER — urutan if/return penting, jangan diubah
    // ══════════════════════════════════════════════════════════════════════════

    // ── CHECKING: DataStore belum emit, tampilkan loading kosong ─────────────
    if (isOnboardingDone == null || initScreen == InitScreen.CHECKING) {
        // Tampilkan loading ringan sementara DataStore siap
        // Tidak perlu panggil onReady karena nanti LaunchedEffect di atas akan
        // mengubah initScreen begitu isOnboardingDone sudah punya nilai
        LoadingScreen(viewModel = viewModel, isFirstSetup = false, onReady = {})
        return
    }

    // ── AUTH: Baru install atau onboarding belum selesai ─────────────────────
    if (initScreen == InitScreen.AUTH) {
        LoginScreen(
            authViewModel = authViewModel,
            onLoginSuccess = {
                if (isNewAccount) {
                    // Akun baru → onboarding dulu
                    initScreen = InitScreen.ONBOARDING
                } else {
                    // Login akun lama → data didownload di LaunchedEffect currentUser
                    // Cek apakah cloud data sudah punya onboarding data:
                    // Jika isOnboardingDone sudah true (dari cloud download) → LOADING
                    // Jika belum → ONBOARDING
                    if (isOnboardingDone == true) {
                        initScreen = InitScreen.LOADING
                    } else {
                        initScreen = InitScreen.ONBOARDING
                    }
                }
            },
            onBack = { /* tidak bisa back di layar awal */ },
            onContinueAsGuest = {
                // Guest → onboarding dulu
                initScreen = InitScreen.ONBOARDING
            }
        )
        return
    }

    // ── ONBOARDING: User mengisi total hutang & deadline ─────────────────────
    if (initScreen == InitScreen.ONBOARDING) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = {
                initScreen = InitScreen.FIRST_LOADING
            }
        )
        return
    }

    // ── FIRST LOADING: Loading setelah onboarding pertama kali ───────────────
    if (initScreen == InitScreen.FIRST_LOADING) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = true,
            onReady = {
                initScreen = InitScreen.READY
                appReady = true
            }
        )
        return
    }

    // ── LOADING: Loading normal tiap buka app (user lama) ────────────────────
    if (initScreen == InitScreen.LOADING && !appReady) {
        LoadingScreen(
            viewModel = viewModel,
            isFirstSetup = false,
            onReady = { appReady = true }
        )
        return
    }

    // ── READY: Setelah first loading selesai, pastikan appReady ──────────────
    if (initScreen == InitScreen.READY && !appReady) {
        // Seharusnya tidak sampai sini karena appReady di-set di FIRST_LOADING onReady
        // Tapi sebagai safety net:
        appReady = true
    }

    // ── STATISTICS ────────────────────────────────────────────────────────────
    if (currentScreen == AppScreen.STATISTICS) {
        StatisticsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = AppScreen.MAIN }
        )
        return
    }

    // ── LOGIN / ACCOUNT (dari tombol akun TopAppBar) ──────────────────────────
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
                onContinueAsGuest = null  // tidak ada opsi guest dari sini
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