package com.hyse.debtslayer.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hyse.debtslayer.viewmodel.DebtState
import java.text.NumberFormat
import java.util.Locale

// Warna progress yang jelas terlihat di dark theme
private val ProgressGreen  = Color(0xFF00E676) // hijau neon — >50% lunas
private val ProgressPurple = Color(0xFFCE93D8) // ungu terang — <50%
private val ProgressRed    = Color(0xFFFF5252) // merah terang — hari tersisa < 30

@Composable
fun DebtProgressCard(
    debtState: DebtState,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val progressColor = if (debtState.progressPercentage > 50) ProgressGreen else ProgressPurple
    val trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ───────────────────────────────────────────
            Text(
                text = "💰 Status Hutang",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Progress bar — lebih tebal & kontras ─────────────
            LinearProgressIndicator(
                progress = { (debtState.progressPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp), // lebih tebal dari 12dp
                color = progressColor,
                trackColor = trackColor,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Persentase + label warna mencolok
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%.1f", debtState.progressPercentage)}% Lunas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                if (debtState.remainingDebt <= 0L) {
                    Surface(
                        color = ProgressGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "✅ LUNAS!",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ProgressGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Stats baris 1 ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DebtStatItem(
                    label = "Sudah Dibayar",
                    value = formatRupiah(debtState.totalPaid),
                    color = ProgressGreen
                )
                DebtStatItem(
                    label = "Sisa Hutang",
                    value = formatRupiah(debtState.remainingDebt),
                    color = ProgressRed,
                    align = Alignment.End
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Stats baris 2 ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DebtStatItem(
                    label = "Target Hari Ini",
                    value = formatRupiah(debtState.dailyTarget),
                    color = ProgressPurple
                )
                DebtStatItem(
                    label = "Hari Tersisa",
                    value = "${debtState.daysRemaining} hari",
                    color = if (debtState.daysRemaining < 30) ProgressRed
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    align = Alignment.End
                )
            }

            // ── Info footer ───────────────────────────────────────
            if (debtState.daysRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.35f else 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Target dihitung otomatis: Sisa Hutang ÷ Hari Tersisa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.8f else 0.65f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtStatItem(
    label: String,
    value: String,
    color: Color,
    align: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = align) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 17.sp
        )
    }
}

fun formatRupiah(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    return "Rp ${formatter.format(amount)}"
}