package com.hyse.debtslayer.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.hyse.debtslayer.ui.theme.DebtRed
import com.hyse.debtslayer.ui.theme.DebtRedDark
import com.hyse.debtslayer.ui.theme.MaiPurple
import com.hyse.debtslayer.ui.theme.SuccessGreen
import com.hyse.debtslayer.ui.theme.SuccessGreenDark
import com.hyse.debtslayer.viewmodel.DebtState
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DebtProgressCard(
    debtState: DebtState,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    // ✅ FIX: gunakan warna yang lebih terang di dark supaya terbaca
    val greenColor = if (isDark) SuccessGreenDark else SuccessGreen
    val redColor = if (isDark) DebtRedDark else DebtRed
    // ✅ FIX: di dark theme primary sudah diubah ke CE93D8 (terang), langsung pakai colorScheme.primary
    val purpleColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Text(
                text = "💰 Status Hutang",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                // ✅ FIX: pakai onPrimaryContainer supaya otomatis kontras dengan primaryContainer
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = debtState.progressPercentage / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = if (debtState.progressPercentage > 50) greenColor else purpleColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${String.format("%.1f", debtState.progressPercentage)}% Lunas",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                // ✅ FIX: pakai onPrimaryContainer bukan hardcode
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Sudah Dibayar",
                    value = formatRupiah(debtState.totalPaid),
                    color = greenColor
                )

                StatItem(
                    label = "Sisa Hutang",
                    value = formatRupiah(debtState.remainingDebt),
                    color = redColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Target Hari Ini",
                    value = formatRupiah(debtState.dailyTarget),
                    color = purpleColor
                )

                StatItem(
                    label = "Hari Tersisa",
                    value = "${debtState.daysRemaining} hari",
                    // ✅ FIX: pakai redColor yang sudah disesuaikan per tema
                    color = if (debtState.daysRemaining < 30) redColor
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Info tambahan
            if (debtState.daysRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    // ✅ FIX: gunakan surface bukan hardcode, supaya ikut tema
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Target dihitung otomatis: Sisa Hutang ÷ Hari Tersisa",
                            style = MaterialTheme.typography.bodySmall,
                            // ✅ FIX: alpha 0.85 supaya tidak terlalu redup di dark
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.85f else 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    val isDark = isSystemInDarkTheme()
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            // ✅ FIX: label teks alpha lebih tinggi di dark
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isDark) 0.85f else 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 18.sp
        )
    }
}

fun formatRupiah(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    return "Rp ${formatter.format(amount)}"
}