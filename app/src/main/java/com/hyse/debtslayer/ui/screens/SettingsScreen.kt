package com.hyse.debtslayer.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.data.sync.CloudSyncRepository
import com.hyse.debtslayer.personality.AdaptiveMaiPersonality
import com.hyse.debtslayer.utils.CurrencyFormatter
import com.hyse.debtslayer.utils.PdfReportExporter
import com.hyse.debtslayer.utils.PdfShareUtil
import com.hyse.debtslayer.viewmodel.ActiveModel
import com.hyse.debtslayer.viewmodel.AuthState
import com.hyse.debtslayer.viewmodel.AuthViewModel
import com.hyse.debtslayer.viewmodel.DebtViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Expandable Section wrapper ─────────────────────────────────────────────────
@Composable
fun ExpandableSection(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "arrow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, title, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Tutup" else "Buka",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

// ── Settings Screen ────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    viewModel: DebtViewModel,
    authViewModel: AuthViewModel,           // 🆕
    onShowLogin: () -> Unit                 // 🆕
) {
    val personalityMode by viewModel.personalityMode.collectAsState()
    val positiveFeedback by viewModel.positiveFeedbackCount.collectAsState()
    val negativeFeedback by viewModel.negativeFeedbackCount.collectAsState()
    val debtState by viewModel.debtState.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val reminderHour by viewModel.reminderHour.collectAsState(initial = 19)
    val reminderMinute by viewModel.reminderMinute.collectAsState(initial = 0)
    val currentUser by authViewModel.currentUser.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "⚙️ Pengaturan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Info Hutang
        ExpandableSection(
            icon = Icons.Default.AccountBalance,
            title = "💰 Info Hutang",
            subtitle = "Sisa ${CurrencyFormatter.format(debtState.remainingDebt)} • ${debtState.daysRemaining} hari lagi",
            initiallyExpanded = true
        ) {
            DebtInfoContent(
                debtState = debtState,
                customDeadline = customDeadline,
                totalDebt = totalDebt,
                viewModel = viewModel
            )
        }

        // 2. Reminder
        ExpandableSection(
            icon = Icons.Default.Notifications,
            title = "🔔 Waktu Reminder Harian",
            subtitle = "Setiap hari ${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}"
        ) {
            ReminderContent(
                hour = reminderHour,
                minute = reminderMinute,
                onTimeChanged = { h, m -> viewModel.saveReminderTime(h, m) },
                onTestNotification = { viewModel.testNotification() },
                context = context
            )
        }

        // 3. Kepribadian Mai
        ExpandableSection(
            icon = Icons.Default.Psychology,
            title = "Kepribadian Mai",
            subtitle = when (personalityMode) {
                AdaptiveMaiPersonality.PersonalityMode.STRICT   -> "Mode: Tegas 💢"
                AdaptiveMaiPersonality.PersonalityMode.BALANCED -> "Mode: Seimbang 😊"
                AdaptiveMaiPersonality.PersonalityMode.GENTLE   -> "Mode: Lembut 💜"
            }
        ) {
            Text(
                "Pilih bagaimana Mai berinteraksi denganmu:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonalityOption(
                    "💢", "Tegas (Strict)",
                    "Mai lebih galak dan tegas. Cocok untuk push ekstra!",
                    personalityMode == AdaptiveMaiPersonality.PersonalityMode.STRICT
                ) { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.STRICT) }
                PersonalityOption(
                    "😊", "Seimbang (Balanced)",
                    "Mix antara tegas dan lembut. Default mode.",
                    personalityMode == AdaptiveMaiPersonality.PersonalityMode.BALANCED
                ) { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.BALANCED) }
                PersonalityOption(
                    "💜", "Lembut (Gentle)",
                    "Mai lebih supportive dan encouraging.",
                    personalityMode == AdaptiveMaiPersonality.PersonalityMode.GENTLE
                ) { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.GENTLE) }
            }
        }

        // 4. AI Learning Stats
        val total = positiveFeedback + negativeFeedback
        ExpandableSection(
            icon = Icons.Default.AutoAwesome,
            title = "🧠 AI Learning Stats",
            subtitle = if (total > 0) "$positiveFeedback 👍  $negativeFeedback 👎"
            else "Belum ada feedback"
        ) {
            AiLearningContent(
                positiveFeedback = positiveFeedback,
                negativeFeedback = negativeFeedback
            )
        }

        // 5. Model AI
        ModelInfoCard(viewModel = viewModel)

        // 6. Riwayat Chat
        ChatHistoryCard(viewModel = viewModel)

        // 7. 🆕 Export PDF
        ExpandableSection(
            icon = Icons.Default.PictureAsPdf,
            title = "📄 Export Laporan PDF",
            subtitle = "Download laporan semua transaksi"
        ) {
            PdfExportContent(viewModel = viewModel)
        }

        // 8. 🆕 Cloud Sync / Akun
        ExpandableSection(
            icon = Icons.Default.CloudSync,
            title = "☁️ Cloud Sync",
            subtitle = if (currentUser != null) "Login: ${currentUser?.email}"
            else "Belum login"
        ) {
            CloudSyncContent(
                authViewModel = authViewModel,
                viewModel = viewModel,
                onShowLogin = onShowLogin
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── 🆕 PDF Export Content ─────────────────────────────────────────────────────
@Composable
fun PdfExportContent(viewModel: DebtViewModel) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val debtState by viewModel.debtState.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var exportDone by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf("") }

    Text(
        "Export semua data transaksi ke file PDF yang bisa dibuka atau dibagikan.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(12.dp))

    // Info jumlah data
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total transaksi", style = MaterialTheme.typography.bodySmall)
            Text(
                "${transactions.size} transaksi",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    // Tombol export
    Button(
        onClick = {
            isExporting = true
            exportDone = false
            exportError = ""
            try {
                val file = PdfReportExporter.export(
                    context = context,
                    transactions = transactions,
                    debtState = debtState
                )
                PdfShareUtil.share(context, file)
                exportDone = true
            } catch (e: Exception) {
                exportError = "Gagal export: ${e.message?.take(60)}"
            } finally {
                isExporting = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isExporting && transactions.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isExporting) "Membuat PDF..." else "Export & Buka PDF")
    }

    if (transactions.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Belum ada transaksi untuk diekspor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }

    // Pesan sukses
    if (exportDone) {
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF00897B).copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                Text(
                    "PDF berhasil dibuat!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00897B)
                )
            }
        }
    }

    // Pesan error
    if (exportError.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                exportError,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ── 🆕 Cloud Sync Content ─────────────────────────────────────────────────────
@Composable
fun CloudSyncContent(
    authViewModel: AuthViewModel,
    viewModel: DebtViewModel,
    onShowLogin: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val debtState by viewModel.debtState.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val scope = rememberCoroutineScope()

    var syncStatus by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    if (currentUser == null) {
        // ── Belum login ───────────────────────────────────────────
        Text(
            "Login untuk menyinkronkan data hutang kamu ke cloud. Data aman dan bisa diakses dari mana saja.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onShowLogin,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) {
            Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Login / Daftar Akun")
        }
    } else {
        // ── Sudah login ───────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6200EE).copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    tint = Color(0xFF6200EE),
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Login sebagai:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        currentUser?.email ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6200EE)
                    )
                }
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Tombol Upload
        OutlinedButton(
            onClick = {
                scope.launch {
                    isSyncing = true
                    syncStatus = ""
                    try {
                        val syncRepo = CloudSyncRepository(
                            authRepository = com.hyse.debtslayer.data.auth.AuthRepository(),
                            transactionRepository = viewModel.getTransactionRepository()
                        )
                        syncRepo.uploadAll()
                        if (customDeadline != null) {
                            syncRepo.uploadPreferences(totalDebt, customDeadline!!)
                        }
                        syncStatus = "✅ Upload berhasil! ${transactions.size} transaksi tersimpan di cloud."
                    } catch (e: Exception) {
                        syncStatus = "❌ Upload gagal: ${e.message?.take(60)}"
                    } finally {
                        isSyncing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        ) {
            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Upload ke Cloud")
        }

        Spacer(Modifier.height(8.dp))

        // Tombol Download
        OutlinedButton(
            onClick = {
                scope.launch {
                    isSyncing = true
                    syncStatus = ""
                    try {
                        val syncRepo = CloudSyncRepository(
                            authRepository = com.hyse.debtslayer.data.auth.AuthRepository(),
                            transactionRepository = viewModel.getTransactionRepository()
                        )
                        syncRepo.downloadAll()
                        syncStatus = "✅ Download berhasil! Data diperbarui dari cloud."
                    } catch (e: Exception) {
                        syncStatus = "❌ Download gagal: ${e.message?.take(60)}"
                    } finally {
                        isSyncing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        ) {
            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download dari Cloud")
        }

        if (isSyncing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (syncStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val isSuccess = syncStatus.startsWith("✅")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess)
                        Color(0xFF00897B).copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    syncStatus,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) Color(0xFF00897B)
                    else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Tombol Logout
        TextButton(
            onClick = { authViewModel.signOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Logout, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Logout")
        }
    }
}

// ── Semua composable lama di bawah ini tidak diubah sama sekali ───────────────

@Composable
fun DebtInfoContent(
    debtState: com.hyse.debtslayer.viewmodel.DebtState,
    customDeadline: String?,
    totalDebt: Long,
    viewModel: DebtViewModel
) {
    val context = LocalContext.current
    var showTotalDebtDialog by remember { mutableStateOf(false) }
    val isUpdatingDebt by viewModel.isUpdatingDebt.collectAsState()
    val initialDeadline by viewModel.initialDeadline.collectAsState(initial = null)
    val deadlineDateFormat = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
    val deadlineSaveFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    if (isUpdatingDebt) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }

    InfoEditRow("Total Hutang", CurrencyFormatter.format(totalDebt)) { showTotalDebtDialog = true }
    Spacer(Modifier.height(8.dp))
    DebtInfoRow("Sisa Hutang", CurrencyFormatter.format(debtState.remainingDebt), MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    DebtInfoRow("Sudah Dibayar", CurrencyFormatter.format(debtState.totalPaid), MaterialTheme.colorScheme.primary)
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    InfoEditRow(
        label = "Deadline",
        value = if (customDeadline != null) {
            try {
                deadlineDateFormat.format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customDeadline)!!
                )
            } catch (e: Exception) { customDeadline }
        } else "Belum diset",
        onEditClick = {
            val cal = Calendar.getInstance()
            if (customDeadline != null) {
                try {
                    cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customDeadline)!!
                } catch (e: Exception) { }
            }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    viewModel.updateDeadlineFromSettings(
                        deadlineSaveFormat.format(
                            Calendar.getInstance().apply { set(year, month, day) }.time
                        )
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() + (1000L * 60 * 60 * 24)
            }.show()
        }
    )
    Spacer(Modifier.height(8.dp))
    DebtInfoRow(
        "Hari Tersisa",
        "${debtState.daysRemaining} hari",
        if (debtState.daysRemaining < 30) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    DebtInfoRow(
        "Target/Hari (otomatis)",
        CurrencyFormatter.format(debtState.dailyTarget),
        MaterialTheme.colorScheme.secondary
    )

    val showResetButton = customDeadline != null &&
            initialDeadline != null &&
            customDeadline != initialDeadline
    if (showResetButton) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.resetDeadline() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            val label = try {
                "Reset ke Deadline Awal (${
                    SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(initialDeadline!!)!!
                    )
                })"
            } catch (e: Exception) { "Reset ke Deadline Awal" }
            Text(label)
        }
    }

    if (showTotalDebtDialog) {
        EditTotalDebtDialog(
            currentTotal = totalDebt,
            onConfirm = { viewModel.updateTotalDebtFromSettings(it); showTotalDebtDialog = false },
            onDismiss = { showTotalDebtDialog = false }
        )
    }
}

@Composable
fun ReminderContent(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    onTestNotification: () -> Unit,
    context: android.content.Context
) {
    var showTestConfirm by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(context, { _, h, m -> onTimeChanged(h, m) }, hour, minute, true).show()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Jam reminder setiap hari",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.Default.Edit, "Ubah", tint = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Ketuk untuk ubah jam. Mai akan mengirim notifikasi sesuai mode kepribadian yang aktif.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { onTestNotification(); showTestConfirm = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Kirim Notifikasi Test Sekarang")
    }
    if (showTestConfirm) {
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); showTestConfirm = false }
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "✅ Notifikasi test dikirim! Cek notifikasi HP kamu.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun AiLearningContent(positiveFeedback: Int, negativeFeedback: Int) {
    Text(
        "Mai belajar dari feedback kamu untuk memberikan respons yang lebih baik!",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatBox(Icons.Default.ThumbUp, positiveFeedback, "Positif", MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.height(60.dp).width(1.dp))
        StatBox(Icons.Default.ThumbDown, negativeFeedback, "Negatif", MaterialTheme.colorScheme.error)
    }
    val total = positiveFeedback + negativeFeedback
    if (total > 0) {
        val ratio = positiveFeedback.toFloat() / total
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Kepuasan:", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${String.format("%.1f", ratio * 100)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = when {
                ratio > 0.7f -> MaterialTheme.colorScheme.primary
                ratio > 0.4f -> MaterialTheme.colorScheme.tertiary
                else         -> MaterialTheme.colorScheme.error
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    when {
                        ratio > 0.8f -> "Mai akan lebih lembut karena kamu suka respons supportive 😊"
                        ratio < 0.3f -> "Mai akan lebih tegas karena kamu butuh push lebih! 💪"
                        total < 5    -> "Berikan lebih banyak feedback agar Mai bisa belajar!"
                        else         -> "Mode seimbang berdasarkan preferensimu"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    } else {
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Belum ada feedback. Gunakan 👍👎 di chat untuk membantu Mai belajar!",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ModelInfoCard(viewModel: DebtViewModel) {
    val activeModel by viewModel.activeModel.collectAsState()
    val isUsingFallback = activeModel == ActiveModel.FLASH
    val activeModelName = if (isUsingFallback) DebtViewModel.MODEL_NAME_FALLBACK else DebtViewModel.MODEL_NAME
    val activeLimits = if (isUsingFallback) DebtViewModel.MODEL_LIMITS_FALLBACK else DebtViewModel.MODEL_LIMITS

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUsingFallback)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.SmartToy, "Model AI", tint = MaterialTheme.colorScheme.primary)
                Text("🤖 Model AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUsingFallback)
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
                        label = "dot_alpha"
                    )
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = (if (isUsingFallback) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary).copy(alpha = alpha)
                    ) {}
                    Column {
                        Text(
                            "Sedang Digunakan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            activeModelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUsingFallback) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isUsingFallback)
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            if (isUsingFallback) "Cadangan" else "Utama",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUsingFallback) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider()
            ModelInfoRow("Limit Aktif", "${activeLimits.rpm} RPM  •  ${activeLimits.rpd} RPD")
            HorizontalDivider()
            ModelInfoRow("Model Utama", DebtViewModel.MODEL_NAME)
            ModelInfoRow("Model Cadangan", DebtViewModel.MODEL_NAME_FALLBACK)
        }
    }
}

@Composable
private fun ModelInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChatHistoryCard(viewModel: DebtViewModel) {
    val conversationCount = remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeletedConfirm by remember { mutableStateOf(false) }
    val refreshTrigger = remember { mutableStateOf(0) }
    LaunchedEffect(refreshTrigger.value) { viewModel.getConversationCount { conversationCount.value = it } }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.MenuBook, "Riwayat", tint = MaterialTheme.colorScheme.primary)
                Text("📚 Riwayat Percakapan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pesan otomatis dihapus setelah 7 hari. Kamu juga bisa menghapus manual kapan saja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Percakapan Tersimpan:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${conversationCount.value} percakapan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = conversationCount.value > 0,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (conversationCount.value > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hapus Semua Riwayat Chat")
            }
            AnimatedVisibility(visible = showDeletedConfirm) {
                LaunchedEffect(showDeletedConfirm) {
                    if (showDeletedConfirm) { kotlinx.coroutines.delay(3000); showDeletedConfirm = false }
                }
                Column {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                "✅ Semua riwayat chat berhasil dihapus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Hapus Riwayat Chat?", fontWeight = FontWeight.Bold) },
            text = { Text("Semua ${conversationCount.value} percakapan akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatHistory()
                        showDeleteDialog = false
                        showDeletedConfirm = true
                        refreshTrigger.value++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus Semua") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun DebtInfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun InfoEditRow(label: String, value: String, onEditClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EditTotalDebtDialog(currentTotal: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var inputText by remember { mutableStateOf(currentTotal.toString()) }
    var errorMsg by remember { mutableStateOf("") }
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💰 Ubah Total Hutang", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Masukkan nominal total hutang dalam Rupiah.\nContoh: 12445000",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.filter { c -> c.isDigit() }; errorMsg = "" },
                    label = { Text("Total Hutang (Rp)") },
                    placeholder = { Text("12445000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMsg.isNotEmpty(),
                    supportingText = if (errorMsg.isNotEmpty()) {
                        { Text(errorMsg, color = MaterialTheme.colorScheme.error) }
                    } else if (inputText.isNotEmpty()) {
                        { val p = inputText.toLongOrNull(); if (p != null) Text("= Rp ${formatter.format(p)}", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = inputText.toLongOrNull()
                when {
                    amount == null  -> errorMsg = "Masukkan angka yang valid"
                    amount < 10_000 -> errorMsg = "Minimal Rp 10.000"
                    else            -> onConfirm(amount)
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun PersonalityOption(icon: String, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(text = icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun StatBox(icon: ImageVector, count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}