package com.hyse.debtslayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.NumberFormat
import com.hyse.debtslayer.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

// ── Model untuk grouping per hari ─────────────────────────────────────────────
data class DailySession(
    val dateLabel: String,       // "Senin, 17 Feb 2026"
    val dateTimestamp: Long,     // untuk sorting
    val transactions: List<Transaction>,
    val maiTitle: String,        // judul bergaya Mai
    val totalAmount: Long
)

@Composable
fun HistoryScreen(viewModel: DebtViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val debtState by viewModel.debtState.collectAsState()

    // Group transaksi per hari, urutkan terbaru dulu
    val sessions = remember(transactions) {
        groupTransactionsByDay(transactions, viewModel)
    }

    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😑", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Belum ada riwayat setoran.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mulai chat dengan Mai sekarang!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header ringkasan
        item {
            SummaryBanner(
                totalPaid = debtState.totalPaid,
                remaining = debtState.remainingDebt,
                sessionCount = sessions.size
            )
        }

        items(sessions, key = { it.dateTimestamp }) { session ->
            SessionCard(
                session = session,
                onDeleteTransaction = { id -> viewModel.deleteTransaction(id) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Banner ringkasan ──────────────────────────────────────────────────────────
@Composable
fun SummaryBanner(totalPaid: Long, remaining: Long, sessionCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Disetor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text(
                    CurrencyFormatter.format(totalPaid),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            VerticalDivider()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sisa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text(
                    CurrencyFormatter.format(remaining),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            VerticalDivider()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sesi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text(
                    "$sessionCount hari",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
    )
}

// ── Card per sesi (per hari) ──────────────────────────────────────────────────
@Composable
fun SessionCard(
    session: DailySession,
    onDeleteTransaction: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header sesi — bisa diklik untuk expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ikon hari
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📅", fontSize = 20.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Judul bergaya Mai
                    Text(
                        session.maiTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        session.dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        CurrencyFormatter.format(session.totalAmount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${session.transactions.size} setoran",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // List transaksi dalam sesi (collapsible)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Divider()
                    session.transactions.forEachIndexed { index, transaction ->
                        TransactionRow(
                            transaction = transaction,
                            onDelete = { onDeleteTransaction(transaction.id) }
                        )
                        if (index < session.transactions.size - 1) {
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Baris transaksi individual ────────────────────────────────────────────────
@Composable
fun TransactionRow(
    transaction: Transaction,
    onDelete: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ikon setoran
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text("💸", fontSize = 16.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                CurrencyFormatter.format(transaction.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                formatTimeOnly(transaction.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Text(
            transaction.source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 100.dp)
        )

        IconButton(onClick = { showDialog = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete, "Hapus",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hapus Setoran?") },
            text = { Text("Yakin ingin menghapus setoran ${CurrencyFormatter.format(transaction.amount)}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDialog = false }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Batal") }
            }
        )
    }
}

// ── Logic grouping & judul bergaya Mai ───────────────────────────────────────
fun groupTransactionsByDay(
    transactions: List<Transaction>,
    viewModel: DebtViewModel
): List<DailySession> {
    if (transactions.isEmpty()) return emptyList()

    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val labelFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale("id", "ID"))

    val grouped = transactions.groupBy { dayFormat.format(Date(it.date)) }

    return grouped.entries
        .sortedByDescending { it.key } // terbaru dulu
        .map { (dateStr, txList) ->
            val date = dayFormat.parse(dateStr) ?: Date()
            val totalAmount = txList.sumOf { it.amount }
            DailySession(
                dateLabel = labelFormat.format(date),
                dateTimestamp = date.time,
                transactions = txList.sortedByDescending { it.date },
                maiTitle = generateMaiTitle(totalAmount, txList.size, viewModel.debtState.value.dailyTarget),
                totalAmount = totalAmount
            )
        }
}

fun generateMaiTitle(totalAmount: Long, txCount: Int, dailyTarget: Long): String {
    val target = dailyTarget
    val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val amountStr = "Rp ${formatter.format(totalAmount)}"

    return when {
        totalAmount == 0L -> "Hari tanpa setoran. Hmm."
        totalAmount >= target * 2 -> "Tidak kusangka kamu bisa $amountStr — melebihi target 2x."
        totalAmount >= target -> "$amountStr — pas target. Lumayan."
        totalAmount >= target * 75 / 100 -> "$amountStr — hampir cukup, tapi belum."
        txCount > 3 -> "$amountStr dari $txCount setoran. Rajin juga."
        totalAmount < 50_000L -> "$amountStr? Serius kamu?"
        else -> "$amountStr — di bawah target. Besok lebih baik."
    }
}


fun formatTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
