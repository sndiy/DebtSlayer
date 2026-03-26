package com.hyse.debtslayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.hyse.debtslayer.data.preferences.MaiMemory
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingFlat
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
import androidx.compose.ui.text.style.TextAlign
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
    val debtState    by viewModel.debtState.collectAsState()
    val streakData   by viewModel.streakData.collectAsState()
    val maiMemory by viewModel.maiMemory.collectAsState()


    val sdf   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = sdf.format(Date())

    // ── Kalkulasi data ───────────────────────────────────────────────────
    val depositByDay = remember(transactions) {
        transactions.groupBy { sdf.format(Date(it.date)) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val todayTotal = depositByDay[today] ?: 0L

    // 7 hari terakhir
    val last7Days = remember(transactions) {
        (0..6).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            val dateStr = sdf.format(cal.time)
            val label = SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)
            Pair(label, depositByDay[dateStr] ?: 0L)
        }.reversed()
    }

    // 6 bulan terakhir
    val last6Months = remember(transactions) {
        (0..5).map { monthsAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
            val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
            val label = SimpleDateFormat("MMM", Locale("id")).format(cal.time)
            val total = transactions
                .filter { sdf.format(Date(it.date)).startsWith(monthStr) }
                .sumOf { it.amount }
            Pair(label, total)
        }.reversed()
    }

    // Minggu ini vs minggu lalu
    val thisWeekTotal = remember(transactions) {
        val cal = Calendar.getInstance()
        (0..6).sumOf { daysAgo ->
            val d = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            depositByDay[sdf.format(d.time)] ?: 0L
        }
    }
    val lastWeekTotal = remember(transactions) {
        (7..13).sumOf { daysAgo ->
            val d = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            depositByDay[sdf.format(d.time)] ?: 0L
        }
    }

    // Rata-rata harian (30 hari terakhir)
    val avgDaily30 = remember(transactions) {
        val total = (0..29).sumOf { daysAgo ->
            val d = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            depositByDay[sdf.format(d.time)] ?: 0L
        }
        total / 30L
    }

    // Prediksi lunas berdasarkan rata-rata
    val predictedDaysLeft = remember(debtState, avgDaily30) {
        if (avgDaily30 > 0 && debtState.remainingDebt > 0)
            (debtState.remainingDebt / avgDaily30).toInt()
        else -1
    }
    val predictedDate = remember(predictedDaysLeft) {
        if (predictedDaysLeft > 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, predictedDaysLeft) }
            SimpleDateFormat("d MMM yyyy", Locale("id")).format(cal.time)
        } else "—"
    }

    // Heatmap: 12 minggu terakhir (84 hari)
    val heatmapData = remember(transactions, debtState) {
        val maxAmount = depositByDay.values.maxOrNull()?.toFloat() ?: 1f
        (83 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            val dateStr = sdf.format(cal.time)
            val amount = depositByDay[dateStr] ?: 0L
            val intensity = if (maxAmount > 0) (amount / maxAmount).coerceIn(0f, 1f) else 0f
            Triple(dateStr, amount, intensity)
        }
    }

    // DEBUG
    android.util.Log.d("StatScreen", "maiMemory=${maiMemory.nickname}, avgDaily=$avgDaily30, heatmap=${heatmapData.size}")


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik & Analitik") },
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
            // ── Summary Cards ────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title    = "Total Setor",
                    value    = CurrencyFormatter.format(debtState.totalPaid),
                    color    = Color(0xFF6200EE),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title    = "Sisa Hutang",
                    value    = CurrencyFormatter.format(debtState.remainingDebt),
                    color    = Color(0xFFE53935),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatCard(
                    title    = "Setor Hari Ini",
                    value    = CurrencyFormatter.format(todayTotal),
                    color    = Color(0xFF00897B),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title    = "Streak",
                    value    = "${streakData.currentStreak} hari 🔥",
                    color    = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Progress Bar ─────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progress Pelunasan", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${String.format("%.1f", debtState.progressPercentage)}%",
                            color      = Color(0xFF6200EE),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress   = { debtState.progressPercentage / 100f },
                        modifier   = Modifier.fillMaxWidth().height(12.dp),
                        color      = Color(0xFF6200EE),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${debtState.daysRemaining} hari tersisa • Target harian ${CurrencyFormatter.format(debtState.dailyTarget)}",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Insight Cards ─────────────────────────────────────────────
            InsightCards(
                avgDaily30       = avgDaily30,
                thisWeekTotal    = thisWeekTotal,
                lastWeekTotal    = lastWeekTotal,
                predictedDate    = predictedDate,
                predictedDaysLeft = predictedDaysLeft,
                daysRemaining    = debtState.daysRemaining,
                maiMemory        = maiMemory
            )

            // ── Heatmap ───────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Aktivitas Setoran (12 Minggu)",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Warna makin gelap = setoran makin besar",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    DepositHeatmap(heatmapData = heatmapData)
                }
            }

            // ── Weekly Comparison ─────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Perbandingan Mingguan", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    WeeklyComparison(
                        thisWeek = thisWeekTotal,
                        lastWeek = lastWeekTotal,
                        target   = debtState.dailyTarget * 7
                    )
                }
            }

            // ── Bar Chart 7 Hari ──────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Setoran 7 Hari Terakhir", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (last7Days.all { it.second == 0L }) {
                        Text(
                            "Belum ada setoran 7 hari terakhir",
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else {
                        BarChart(
                            entries  = last7Days,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            target   = debtState.dailyTarget
                        )
                    }
                }
            }

            // ── Line Chart 6 Bulan ────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tren 6 Bulan Terakhir", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (last6Months.all { it.second == 0L }) {
                        Text(
                            "Belum ada data bulanan",
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else {
                        LineChart(
                            entries   = last6Months,
                            lineColor = Color(0xFF6200EE),
                            modifier  = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
            }

            // ── Pie Chart ─────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Distribusi Hutang", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    PieChartWithLegend(
                        paid      = debtState.totalPaid,
                        remaining = debtState.remainingDebt
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Insight Cards ────────────────────────────────────────────────────────────
@Composable
private fun InsightCards(
    avgDaily30: Long,
    thisWeekTotal: Long,
    lastWeekTotal: Long,
    predictedDate: String,
    predictedDaysLeft: Int,
    daysRemaining: Int,
    maiMemory: MaiMemory
) {
    val weekDiff = thisWeekTotal - lastWeekTotal
    val weekTrend = when {
        lastWeekTotal == 0L -> "—"
        weekDiff > 0        -> "+${String.format("%.0f", weekDiff / lastWeekTotal.toFloat() * 100)}%"
        weekDiff < 0        -> "${String.format("%.0f", weekDiff / lastWeekTotal.toFloat() * 100)}%"
        else                -> "0%"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "💡 Insight",
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp
        )

        // Baris 1: rata-rata + prediksi
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            InsightCard(
                emoji   = "📈",
                title   = "Rata-rata/hari",
                value   = CurrencyFormatter.format(avgDaily30),
                caption = "30 hari terakhir",
                color   = Color(0xFF6200EE),
                modifier = Modifier.weight(1f)
            )
            InsightCard(
                emoji   = "🎯",
                title   = "Prediksi lunas",
                value   = if (predictedDaysLeft > 0) "$predictedDaysLeft hari" else "—",
                caption = if (predictedDate != "—") predictedDate else "Belum ada data",
                color   = when {
                    predictedDaysLeft <= 0            -> Color(0xFF9E9E9E)
                    predictedDaysLeft < daysRemaining -> Color(0xFF00897B)
                    else                              -> Color(0xFFE53935)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Baris 2: tren minggu + hari favorit
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            InsightCard(
                emoji   = if (weekDiff >= 0) "⬆️" else "⬇️",
                title   = "Tren minggu ini",
                value   = weekTrend,
                caption = "vs minggu lalu",
                color   = if (weekDiff >= 0) Color(0xFF00897B) else Color(0xFFE53935),
                modifier = Modifier.weight(1f)
            )
            InsightCard(
                emoji   = "📅",
                title   = "Hari favorit",
                value   = maiMemory.favoriteDayOfWeek.ifBlank { "—" },
                caption = "paling sering setor",
                color   = Color(0xFFFF6B35),
                modifier = Modifier.weight(1f)
            )
        }

        // Best day full width
        if (maiMemory.bestDayAmount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFB300).copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color(0xFFFFB300).copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🏆", fontSize = 24.sp)
                    Column {
                        Text(
                            "Rekor Setoran Terbaik",
                            fontSize   = 11.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.format(maiMemory.bestDayAmount),
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color(0xFFFFB300)
                        )
                        if (maiMemory.bestDayDate.isNotBlank()) {
                            Text(
                                maiMemory.bestDayDate,
                                fontSize = 10.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    emoji: String,
    title: String,
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(emoji, fontSize = 14.sp)
                Text(title, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize   = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = color,
                maxLines   = 1
            )
            Text(
                caption,
                fontSize = 9.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Heatmap ──────────────────────────────────────────────────────────────────
@Composable
private fun DepositHeatmap(
    heatmapData: List<Triple<String, Long, Float>>
) {
    val baseColor  = Color(0xFF6200EE)
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    // Bagi 84 hari ke 12 minggu (kolom), 7 hari (baris)
    val weeks = heatmapData.chunked(7)

    val dayLabels = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Label hari
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            dayLabels.forEach { day ->
                Box(
                    modifier         = Modifier.size(width = 24.dp, height = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(day, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }

        // Grid minggu
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Pad minggu pertama kalau tidak mulai dari Minggu
                val firstDayOfWeek = if (week.isNotEmpty()) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val cal = Calendar.getInstance().apply { time = sdf.parse(week.first().first)!! }
                        cal.get(Calendar.DAY_OF_WEEK) - 1
                    } catch (e: Exception) { 0 }
                } else 0

                repeat(firstDayOfWeek) {
                    Box(modifier = Modifier.size(14.dp))
                }

                week.forEach { (_, amount, intensity) ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (amount == 0L) emptyColor
                                else baseColor.copy(alpha = 0.15f + intensity * 0.85f),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }

    // Legend
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Kurang", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        listOf(0.15f, 0.35f, 0.55f, 0.75f, 1f).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(baseColor.copy(alpha = alpha), RoundedCornerShape(2.dp))
            )
        }
        Text("Banyak", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

// ── Weekly Comparison ────────────────────────────────────────────────────────
@Composable
private fun WeeklyComparison(thisWeek: Long, lastWeek: Long, target: Long) {
    val maxVal = maxOf(thisWeek, lastWeek, target).toFloat().coerceAtLeast(1f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WeekBar(
            label  = "Minggu ini",
            amount = thisWeek,
            ratio  = thisWeek / maxVal,
            color  = Color(0xFF6200EE),
            target = target
        )
        WeekBar(
            label  = "Minggu lalu",
            amount = lastWeek,
            ratio  = lastWeek / maxVal,
            color  = Color(0xFF9E9E9E),
            target = target
        )
        // Target line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Target",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )
            Text(
                CurrencyFormatter.format(target),
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFFFF6B35)
            )
        }
    }
}

@Composable
private fun WeekBar(
    label: String,
    amount: Long,
    ratio: Float,
    color: Color,
    target: Long
) {
    val isAboveTarget = amount >= target && target > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .background(
                        color = if (isAboveTarget) Color(0xFF00897B) else color,
                        shape = RoundedCornerShape(99.dp)
                    )
            )
        }
        Text(
            CurrencyFormatter.format(amount),
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isAboveTarget) Color(0xFF00897B) else color,
            modifier   = Modifier.width(60.dp),
            textAlign  = TextAlign.End
        )
    }
}

// ── Komponen yang sudah ada (dipertahankan + enhanced) ───────────────────────
@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 11.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun BarChart(
    entries: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    target: Long = 0L
) {
    val maxVal    = entries.maxOfOrNull { it.second }?.toFloat()?.coerceAtLeast(target.toFloat()) ?: 1f
    val barColor  = Color(0xFF6200EE)
    val zeroColor = Color(0xFFE0E0E0)
    val targetColor = Color(0xFFFF6B35)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val barWidth = (size.width / (entries.size * 2 - 1)) * 1.2f
            val gap = (size.width - barWidth * entries.size) / (entries.size - 1).coerceAtLeast(1)

            // Target line
            if (target > 0 && maxVal > 0) {
                val targetY = size.height - (target / maxVal) * size.height * 0.85f
                drawLine(
                    color       = targetColor.copy(alpha = 0.5f),
                    start       = Offset(0f, targetY),
                    end         = Offset(size.width, targetY),
                    strokeWidth = 2f,
                    pathEffect  = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 4f)
                    )
                )
            }

            entries.forEachIndexed { i, (_, value) ->
                val barHeight = if (maxVal > 0) (value / maxVal) * size.height * 0.85f else 0f
                val x = i * (barWidth + gap)
                val y = size.height - barHeight
                val isAboveTarget = target > 0 && value >= target
                drawRect(
                    color     = when {
                        value == 0L      -> zeroColor
                        isAboveTarget    -> Color(0xFF00897B)
                        else             -> barColor
                    },
                    topLeft   = Offset(x, y),
                    size      = Size(barWidth, barHeight.coerceAtLeast(4f))
                )
            }
            drawLine(
                color       = Color.LightGray,
                start       = Offset(0f, size.height),
                end         = Offset(size.width, size.height),
                strokeWidth = 1f
            )
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { (label, _) ->
                Text(label, fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun LineChart(
    entries: List<Pair<String, Long>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = entries.maxOfOrNull { it.second }?.toFloat() ?: 1f

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.size < 2) return@Canvas
            val xStep = size.width / (entries.size - 1).toFloat()
            val path     = Path()
            val fillPath = Path()

            entries.forEachIndexed { i, (_, value) ->
                val x = i * xStep
                val y = if (maxVal > 0) size.height - (value / maxVal) * size.height * 0.85f
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
                val y = if (maxVal > 0) size.height - (value / maxVal) * size.height * 0.85f
                else size.height
                drawCircle(lineColor, 5f, Offset(x, y))
                drawCircle(Color.White, 2.5f, Offset(x, y))
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
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
    val paidColor   = Color(0xFF6200EE)
    val remainColor = Color(0xFFE53935)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val paidSweep   = (paid / total) * 360f
            val remainSweep = 360f - paidSweep
            drawArc(paidColor,   -90f,              paidSweep,   useCenter = true)
            drawArc(remainColor, -90f + paidSweep,  remainSweep, useCenter = true)
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(paidColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sudah Dibayar", fontSize = 12.sp)
                    Text(CurrencyFormatter.format(paid), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = paidColor)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(remainColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sisa Hutang", fontSize = 12.sp)
                    Text(CurrencyFormatter.format(remaining), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = remainColor)
                }
            }
        }
    }
}