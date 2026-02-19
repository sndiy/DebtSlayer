package com.hyse.debtslayer.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyse.debtslayer.personality.AdaptiveMaiPersonality
import com.hyse.debtslayer.utils.CurrencyFormatter
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: DebtViewModel) {
    val personalityMode by viewModel.personalityMode.collectAsState()
    val positiveFeedback by viewModel.positiveFeedbackCount.collectAsState()
    val negativeFeedback by viewModel.negativeFeedbackCount.collectAsState()
    val debtState by viewModel.debtState.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val reminderHour by viewModel.reminderHour.collectAsState(initial = 19)
    val reminderMinute by viewModel.reminderMinute.collectAsState(initial = 0)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚙️ Pengaturan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        DebtInfoCard(
            debtState = debtState,
            customDeadline = customDeadline,
            totalDebt = totalDebt,
            viewModel = viewModel
        )

        ReminderTimeCard(
            hour = reminderHour,
            minute = reminderMinute,
            onTimeChanged = { h, m -> viewModel.saveReminderTime(h, m) },
            onTestNotification = { viewModel.testNotification() },
            context = context
        )

        // ── CARD: Kepribadian Mai ─────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Psychology, "AI Mode", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Kepribadian Mai",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pilih bagaimana Mai berinteraksi denganmu:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonalityOption(
                        icon = "💢", title = "Tegas (Strict)",
                        description = "Mai lebih galak dan tegas. Cocok untuk push ekstra!",
                        selected = personalityMode == AdaptiveMaiPersonality.PersonalityMode.STRICT,
                        onClick = { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.STRICT) }
                    )
                    PersonalityOption(
                        icon = "😊", title = "Seimbang (Balanced)",
                        description = "Mix antara tegas dan lembut. Default mode.",
                        selected = personalityMode == AdaptiveMaiPersonality.PersonalityMode.BALANCED,
                        onClick = { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.BALANCED) }
                    )
                    PersonalityOption(
                        icon = "💜", title = "Lembut (Gentle)",
                        description = "Mai lebih supportive dan encouraging.",
                        selected = personalityMode == AdaptiveMaiPersonality.PersonalityMode.GENTLE,
                        onClick = { viewModel.setPersonalityMode(AdaptiveMaiPersonality.PersonalityMode.GENTLE) }
                    )
                }
            }
        }

        // ── CARD: AI Learning Stats ───────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI Learning", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "🧠 AI Learning Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mai belajar dari feedback kamu untuk memberikan respons yang lebih baik!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    val ratio = positiveFeedback.toFloat() / total
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
                            else -> MaterialTheme.colorScheme.error
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = when {
                                    ratio > 0.8f -> "Mai akan lebih lembut karena kamu suka respons supportive 😊"
                                    ratio < 0.3f -> "Mai akan lebih tegas karena kamu butuh push lebih! 💪"
                                    total < 5 -> "Berikan lebih banyak feedback agar Mai bisa belajar!"
                                    else -> "Mode seimbang berdasarkan preferensimu"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
        }

        // ── CARD: Conversation Learning ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val conversationCount = remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                viewModel.getConversationCount { count -> conversationCount.value = count }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.MenuBook, "Learning", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "📚 Riwayat Percakapan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Percakapan kamu tersimpan selama 30 hari untuk referensi konteks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Percakapan Tersimpan:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${conversationCount.value} percakapan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}


// ── Card Info & Edit Hutang ───────────────────────────────────────────────────
@Composable
fun DebtInfoCard(
    debtState: com.hyse.debtslayer.viewmodel.DebtState,
    customDeadline: String?,
    totalDebt: Long,
    viewModel: DebtViewModel
) {
    val context = LocalContext.current
    var showTotalDebtDialog by remember { mutableStateOf(false) }
    val isUpdatingDebt by viewModel.isUpdatingDebt.collectAsState()
    val initialDeadline by viewModel.initialDeadline.collectAsState(initial = null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AccountBalance, "Hutang", tint = MaterialTheme.colorScheme.secondary)
                Text(
                    "💰 Info Hutang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isUpdatingDebt) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            InfoEditRow(
                label = "Total Hutang",
                value = CurrencyFormatter.format(totalDebt),
                onEditClick = { showTotalDebtDialog = true }
            )
            Spacer(Modifier.height(8.dp))

            DebtInfoRow("Sisa Hutang", CurrencyFormatter.format(debtState.remainingDebt), MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            DebtInfoRow("Sudah Dibayar", CurrencyFormatter.format(debtState.totalPaid), MaterialTheme.colorScheme.primary)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            val deadlineDateFormat = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
            val deadlineSaveFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            InfoEditRow(
                label = "Deadline",
                value = if (customDeadline != null) {
                    try {
                        deadlineDateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customDeadline)!!)
                    } catch (e: Exception) { customDeadline }
                } else "Belum diset",
                onEditClick = {
                    val cal = Calendar.getInstance()
                    if (customDeadline != null) {
                        try { cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customDeadline)!! }
                        catch (e: Exception) { }
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            viewModel.updateDeadlineFromSettings(
                                deadlineSaveFormat.format(Calendar.getInstance().apply { set(year, month, day) }.time)
                            )
                        },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
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
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            DebtInfoRow("Target/Hari (otomatis)", CurrencyFormatter.format(debtState.dailyTarget), MaterialTheme.colorScheme.secondary)

            val showResetButton = customDeadline != null && initialDeadline != null && customDeadline != initialDeadline
            if (showResetButton) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.resetDeadline() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    val resetLabel = try {
                        val disp = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                        "Reset ke Deadline Awal (${disp.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(initialDeadline!!)!!)})"
                    } catch (e: Exception) { "Reset ke Deadline Awal" }
                    Text(resetLabel)
                }
            }
        }
    }

    if (showTotalDebtDialog) {
        EditTotalDebtDialog(
            currentTotal = totalDebt,
            onConfirm = { newTotal ->
                viewModel.updateTotalDebtFromSettings(newTotal)
                showTotalDebtDialog = false
            },
            onDismiss = { showTotalDebtDialog = false }
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
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
                        {
                            val preview = inputText.toLongOrNull()
                            if (preview != null) Text("= Rp ${formatter.format(preview)}", color = MaterialTheme.colorScheme.primary)
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = inputText.toLongOrNull()
                when {
                    amount == null -> errorMsg = "Masukkan angka yang valid"
                    amount < 10_000 -> errorMsg = "Minimal Rp 10.000"
                    else -> onConfirm(amount)
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
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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
fun StatBox(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun ReminderTimeCard(hour: Int, minute: Int, onTimeChanged: (Int, Int) -> Unit, onTestNotification: () -> Unit, context: android.content.Context) {
    var showTestConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Notifications, "Reminder", tint = MaterialTheme.colorScheme.primary)
                Text("🔔 Waktu Reminder Harian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    TimePickerDialog(context, { _, h, m -> onTimeChanged(h, m) }, hour, minute, true).show()
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Jam reminder setiap hari", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.Default.Edit, "Ubah waktu", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Text(
                "Ketuk untuk ubah jam. Mai akan mengirim notifikasi sesuai mode kepribadian yang aktif.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedButton(onClick = { onTestNotification(); showTestConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kirim Notifikasi Test Sekarang")
            }

            if (showTestConfirm) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showTestConfirm = false
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "✅ Notifikasi test dikirim! Cek notifikasi HP kamu.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}