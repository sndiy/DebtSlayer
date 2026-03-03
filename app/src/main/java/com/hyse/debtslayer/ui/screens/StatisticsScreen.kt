package com.hyse.debtslayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.utils.CurrencyFormatter
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: DebtViewModel,
    onBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val debtState by viewModel.debtState.collectAsState()

    // Hitung data statistik dari transaksi
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = sdf.format(Date())

    // 7 hari terakhir
    val last7Days = (0..6).map { daysAgo ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
        val dateStr = sdf.format(cal.time)
        val label = SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)
        val total = transactions
            .filter { sdf.format(Date(it.date)) == dateStr }
            .sumOf { it.amount }
        Pair(label, total)
    }.reversed()

    // Bulanan (6 bulan terakhir)
    val last6Months = (0..5).map { monthsAgo ->
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
        val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        val label = SimpleDateFormat("MMM", Locale("id")).format(cal.time)
        val total = transactions
            .filter { sdf.format(Date(it.date)).startsWith(monthStr) }
            .sumOf { it.amount }
        Pair(label, total)
    }.reversed()

    val todayTotal = transactions
        .filter { sdf.format(Date(it.date)) == today }
        .sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
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
            // ── Summary Cards ─────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title = "Total Setor",
                    value = CurrencyFormatter.format(debtState.totalPaid),
                    color = Color(0xFF6200EE),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Sisa Hutang",
                    value = CurrencyFormatter.format(debtState.remainingDebt),
                    color = Color(0xFFE53935),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title = "Setor Hari Ini",
                    value = CurrencyFormatter.format(todayTotal),
                    color = Color(0xFF00897B),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Transaksi",
                    value = "${transactions.size}x",
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Progress Bar ──────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progress Pelunasan", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${String.format("%.1f", debtState.progressPercentage)}%",
                            color = Color(0xFF6200EE),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { debtState.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = Color(0xFF6200EE),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${debtState.daysRemaining} hari tersisa • Target harian ${CurrencyFormatter.format(debtState.dailyTarget)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Bar Chart 7 Hari ──────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Setoran 7 Hari Terakhir", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (last7Days.all { it.second == 0L }) {
                        Text(
                            "Belum ada setoran 7 hari terakhir",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else {
                        BarChart(
                            entries = last7Days,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            // ── Line Chart 6 Bulan ────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tren 6 Bulan Terakhir", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (last6Months.all { it.second == 0L }) {
                        Text(
                            "Belum ada data bulanan",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else {
                        LineChart(
                            entries = last6Months,
                            lineColor = Color(0xFF6200EE),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            // ── Pie Chart ─────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Distribusi Hutang", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    PieChartWithLegend(
                        paid = debtState.totalPaid,
                        remaining = debtState.remainingDebt
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Komponen Chart ────────────────────────────────────────────────────────────

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 11.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun BarChart(entries: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val maxVal = entries.maxOfOrNull { it.second }?.toFloat() ?: 1f
    val barColor = Color(0xFF6200EE)
    val zeroColor = Color(0xFFE0E0E0)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val barWidth = (size.width / (entries.size * 2 - 1)) * 1.2f
            val gap = (size.width - barWidth * entries.size) / (entries.size - 1).coerceAtLeast(1)

            entries.forEachIndexed { i, (_, value) ->
                val barHeight = if (maxVal > 0) (value / maxVal) * size.height * 0.85f else 0f
                val x = i * (barWidth + gap)
                val y = size.height - barHeight
                drawRect(
                    color = if (value > 0) barColor else zeroColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight.coerceAtLeast(4f))
                )
            }
            // Baseline
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f
            )
        }
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { (label, _) ->
                Text(label, fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun LineChart(entries: List<Pair<String, Long>>, lineColor: Color, modifier: Modifier = Modifier) {
    val maxVal = entries.maxOfOrNull { it.second }?.toFloat() ?: 1f

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.size < 2) return@Canvas
            val xStep = size.width / (entries.size - 1).toFloat()

            val path = Path()
            val fillPath = Path()

            entries.forEachIndexed { i, (_, value) ->
                val x = i * xStep
                val y = if (maxVal > 0)
                    size.height - (value / maxVal) * size.height * 0.85f
                else size.height

                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((entries.size - 1) * xStep, size.height)
            fillPath.close()

            drawPath(fillPath, lineColor.copy(alpha = 0.12f))
            drawPath(path, lineColor, style = Stroke(width = 3f))

            entries.forEachIndexed { i, (_, value) ->
                val x = i * xStep
                val y = if (maxVal > 0)
                    size.height - (value / maxVal) * size.height * 0.85f
                else size.height
                drawCircle(lineColor, 5f, Offset(x, y))
                drawCircle(Color.White, 2.5f, Offset(x, y))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { (label, _) ->
                Text(label, fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PieChartWithLegend(paid: Long, remaining: Long) {
    val total = (paid + remaining).toFloat()
    if (total <= 0f) {
        Text("Belum ada data", color = Color.Gray, fontSize = 13.sp)
        return
    }
    val paidColor = Color(0xFF6200EE)
    val remainColor = Color(0xFFE53935)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val paidSweep = (paid / total) * 360f
            val remainSweep = 360f - paidSweep
            drawArc(paidColor, -90f, paidSweep, useCenter = true)
            drawArc(remainColor, -90f + paidSweep, remainSweep, useCenter = true)
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .size(12.dp)
                    .background(paidColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sudah Dibayar", fontSize = 12.sp)
                    Text(
                        CurrencyFormatter.format(paid),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = paidColor
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .size(12.dp)
                    .background(remainColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sisa Hutang", fontSize = 12.sp)
                    Text(
                        CurrencyFormatter.format(remaining),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = remainColor
                    )
                }
            }
        }
    }
}