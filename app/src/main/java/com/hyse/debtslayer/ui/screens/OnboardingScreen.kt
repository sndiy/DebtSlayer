package com.hyse.debtslayer.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OnboardingScreen(
    viewModel: DebtViewModel,
    onFinished: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 3
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val dateDisplayFormat = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
    val dateSaveFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    var totalDebtInput by remember { mutableStateOf("") }
    var debtError by remember { mutableStateOf("") }
    var selectedDeadline by remember { mutableStateOf<Calendar?>(null) }
    var deadlineError by remember { mutableStateOf("") }

    // ✅ FIX: alpha subtitle disesuaikan per tema — dark lebih tinggi supaya terbaca
    val subtitleAlpha = if (isDark) 0.85f else 0.7f
    val tertiaryAlpha = if (isDark) 0.75f else 0.6f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= currentStep) MaterialTheme.colorScheme.primary
                            // ✅ FIX: dot inactive lebih visible di dark
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.35f else 0.2f)
                        )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        when (currentStep) {
            0 -> {
                Text("👋", fontSize = 64.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Hai. Aku Sakurajima Mai.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // ✅ FIX: judul pakai onBackground supaya selalu kontras
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Aku akan bantu kamu melunasi hutang. Bukan karena aku peduli — tapi karena kamu jelas butuh bantuan.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    // ✅ FIX: alpha lebih tinggi di dark
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = subtitleAlpha)
                )
                Spacer(Modifier.height(32.dp))
                OnboardingInfoCard("💬", "Chat untuk setor", "Bilang \"setor 50rb\" atau \"nabung 100ribu\" — aku langsung catat.")
                Spacer(Modifier.height(12.dp))
                OnboardingInfoCard("📅", "Kalender progress", "Lihat hari mana kamu sudah capai target dan mana yang belum.")
                Spacer(Modifier.height(12.dp))
                OnboardingInfoCard("🔔", "Reminder harian", "Aku akan mengingatkan kamu setiap hari sesuai waktu yang kamu pilih.")
            }

            1 -> {
                Text("💰", fontSize = 64.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Berapa total hutangmu?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Masukkan jumlah total yang harus kamu lunasi.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    // ✅ FIX
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = subtitleAlpha)
                )
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = totalDebtInput,
                    onValueChange = { totalDebtInput = it.filter { c -> c.isDigit() }; debtError = "" },
                    label = { Text("Total Hutang (Rp)") },
                    placeholder = { Text("Contoh: 1000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = debtError.isNotEmpty(),
                    supportingText = when {
                        debtError.isNotEmpty() -> { { Text(debtError, color = MaterialTheme.colorScheme.error) } }
                        totalDebtInput.isNotEmpty() -> {
                            val preview = totalDebtInput.toLongOrNull()
                            if (preview != null) { { Text("= Rp ${formatter.format(preview)}", color = MaterialTheme.colorScheme.primary) } }
                            else null
                        }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            2 -> {
                Text("📅", fontSize = 64.sp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Kapan deadline lunasnya?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pilih tanggal target kamu harus sudah lunas.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    // ✅ FIX
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = subtitleAlpha)
                )
                Spacer(Modifier.height(32.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeadlineDatePicker(context) { cal -> selectedDeadline = cal; deadlineError = "" } },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            deadlineError.isNotEmpty() -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            selectedDeadline != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.8f else 0.4f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Deadline",
                                style = MaterialTheme.typography.labelMedium,
                                // ✅ FIX: onSurfaceVariant sudah diperbaiki di Theme.kt → lebih terang
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            if (selectedDeadline != null) {
                                Text(
                                    dateDisplayFormat.format(selectedDeadline!!.time),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val debt = totalDebtInput.toLongOrNull() ?: 0L
                                val daysLeft = ((selectedDeadline!!.timeInMillis - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
                                val dailyTarget = if (daysLeft > 0) debt / daysLeft else 0L
                                Text(
                                    "$daysLeft hari lagi • target Rp ${formatter.format(dailyTarget)}/hari",
                                    style = MaterialTheme.typography.bodySmall,
                                    // ✅ FIX
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Ketuk untuk pilih tanggal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    // ✅ FIX: alpha lebih tinggi di dark
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.8f else 0.6f)
                                )
                            }
                        }
                        Icon(
                            Icons.Default.CalendarMonth, "Pilih tanggal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (deadlineError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deadlineError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (currentStep > 0) {
                OutlinedButton(onClick = { currentStep-- }, modifier = Modifier.weight(1f)) {
                    Text("Kembali")
                }
            }
            Button(
                onClick = {
                    when (currentStep) {
                        0 -> currentStep++
                        1 -> {
                            val amount = totalDebtInput.toLongOrNull()
                            when {
                                amount == null || totalDebtInput.isEmpty() -> debtError = "Masukkan jumlah hutang"
                                amount < 10_000 -> debtError = "Minimal Rp 10.000"
                                else -> currentStep++
                            }
                        }
                        2 -> {
                            when {
                                selectedDeadline == null -> deadlineError = "Pilih tanggal deadline"
                                selectedDeadline!!.timeInMillis <= System.currentTimeMillis() ->
                                    deadlineError = "Tanggal harus di masa depan"
                                else -> {
                                    val debt = totalDebtInput.toLong()
                                    val deadlineStr = dateSaveFormat.format(selectedDeadline!!.time)
                                    viewModel.completeOnboarding(debt, deadlineStr)
                                    onFinished()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (currentStep < totalSteps - 1) "Lanjut" else "Mulai!")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

fun showDeadlineDatePicker(context: Context, onDateSelected: (Calendar) -> Unit) {
    val today = Calendar.getInstance()
    val dialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(selected)
        },
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH)
    )
    dialog.datePicker.minDate = today.timeInMillis + (1000L * 60 * 60 * 24)
    dialog.show()
}

@Composable
fun OnboardingInfoCard(icon: String, title: String, desc: String) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 32.sp)
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    // ✅ FIX: judul card pakai onSurfaceVariant yang sudah diperbaiki di Theme.kt
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    // ✅ FIX: deskripsi alpha lebih tinggi di dark
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.85f else 0.7f)
                )
            }
        }
    }
}
