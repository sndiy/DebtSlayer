package com.hyse.debtslayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.data.auth.AuthRepository
import com.hyse.debtslayer.data.auth.UserData
import com.hyse.debtslayer.data.preferences.SyncFrequency
import com.hyse.debtslayer.data.preferences.SyncPreferences
import com.hyse.debtslayer.data.sync.CloudSyncRepository
import com.hyse.debtslayer.ui.theme.SuccessGreen
import com.hyse.debtslayer.utils.CurrencyFormatter
import com.hyse.debtslayer.viewmodel.AuthViewModel
import com.hyse.debtslayer.viewmodel.DebtViewModel
import com.hyse.debtslayer.worker.AutoSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    user: UserData,
    authViewModel: AuthViewModel,
    viewModel: DebtViewModel,
    onBack: () -> Unit
) {
    val debtState by viewModel.debtState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Sync state ────────────────────────────────────────────────
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // ── Auto sync preferences ─────────────────────────────────────
    val syncPrefs = remember { SyncPreferences(context) }
    val syncFrequency by syncPrefs.syncFrequency.collectAsState(initial = SyncFrequency.MANUAL)
    val lastSyncTs by syncPrefs.lastSyncTimestamp.collectAsState(initial = 0L)

    // ── Nickname dari Firestore ───────────────────────────────────
    var nickname by remember { mutableStateOf("") }
    LaunchedEffect(user.uid) {
        nickname = AuthRepository().getNickname()
            ?: user.email?.substringBefore("@")
                    ?: "User"
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Akun Saya") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Profile Card ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nickname.take(1).uppercase(),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        nickname,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        user.email ?: "-",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDone, null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Tersambung ke Cloud",
                                fontSize = 11.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Ringkasan Data ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Assessment, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Ringkasan Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    AccountInfoRow(
                        icon = Icons.Default.AccountBalance,
                        label = "Total Hutang",
                        value = CurrencyFormatter.format(totalDebt),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    AccountInfoRow(
                        icon = Icons.Default.TrendingDown,
                        label = "Sisa Hutang",
                        value = CurrencyFormatter.format(debtState.remainingDebt),
                        valueColor = MaterialTheme.colorScheme.error
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    AccountInfoRow(
                        icon = Icons.Default.TrendingUp,
                        label = "Sudah Dibayar",
                        value = CurrencyFormatter.format(debtState.totalPaid),
                        valueColor = SuccessGreen
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    AccountInfoRow(
                        icon = Icons.Default.Receipt,
                        label = "Total Transaksi",
                        value = "${transactions.size}x",
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    AccountInfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Hari Tersisa",
                        value = "${debtState.daysRemaining} hari",
                        valueColor = if (debtState.daysRemaining <= 7)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(14.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Progress Pelunasan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${String.format("%.1f", debtState.progressPercentage)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { debtState.progressPercentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // ── Cloud Sync Manual ─────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudSync, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Sinkronisasi Cloud",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Upload data ke cloud atau download data dari cloud ke HP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncMessage = ""
                                    try {
                                        val syncRepo = CloudSyncRepository(
                                            authRepository = AuthRepository(),
                                            transactionRepository = viewModel.getTransactionRepository()
                                        )
                                        val result = syncRepo.uploadAll()  // 🆕 terima SyncResult
                                        if (customDeadline != null) {
                                            syncRepo.uploadPreferences(totalDebt, customDeadline!!)
                                        }
                                        syncPrefs.updateLastSync()
                                        syncMessage = result.uploadSummary()  // 🆕 pesan informatif
                                    } catch (e: Exception) {
                                        syncMessage = "❌ Upload gagal: ${e.message?.take(50)}"
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncMessage = ""
                                    try {
                                        val syncRepo = CloudSyncRepository(
                                            authRepository = AuthRepository(),
                                            transactionRepository = viewModel.getTransactionRepository()
                                        )
                                        val result = syncRepo.downloadAll()  // 🆕 terima SyncResult
                                        syncMessage = result.downloadSummary()  // 🆕 pesan informatif
                                    } catch (e: Exception) {
                                        syncMessage = "❌ Download gagal: ${e.message?.take(50)}"
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download")
                        }
                    }

                    if (isSyncing) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sedang sinkronisasi...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (syncMessage.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val isOk = syncMessage.startsWith("✅")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOk)
                                    SuccessGreen.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    null,
                                    tint = if (isOk) SuccessGreen
                                    else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    syncMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOk) SuccessGreen
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // ── Auto Sync ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Autorenew, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Auto Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Info terakhir sync
                    if (lastSyncTs > 0L) {
                        val dateStr = remember(lastSyncTs) {
                            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID"))
                                .format(Date(lastSyncTs))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "Terakhir sync: $dateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "Belum pernah auto sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Pilihan frekuensi
                    SyncFrequency.entries.forEach { freq ->
                        val isSelected = syncFrequency == freq
                        Card(
                            onClick = {
                                scope.launch {
                                    syncPrefs.setSyncFrequency(freq)
                                    AutoSyncWorker.schedule(context, freq)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            syncPrefs.setSyncFrequency(freq)
                                            AutoSyncWorker.schedule(context, freq)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        freq.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold
                                        else FontWeight.Normal,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (freq != SyncFrequency.MANUAL) {
                                        Text(
                                            "Upload otomatis ${freq.label.lowercase()} saat ada internet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Info box
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Auto sync hanya berjalan saat ada koneksi internet. " +
                                        "Data tidak akan hilang jika offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Tombol Logout ─────────────────────────────────────
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Dialog Logout ─────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Logout, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Logout?", fontWeight = FontWeight.Bold) },
            text = { Text("Data lokal tetap tersimpan di HP. Kamu bisa login lagi kapan saja.") },
            confirmButton = {
                Button(
                    onClick = {
                        AutoSyncWorker.cancel(context)  // batalkan jadwal sync
                        authViewModel.signOut()
                        showLogoutDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun AccountInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}