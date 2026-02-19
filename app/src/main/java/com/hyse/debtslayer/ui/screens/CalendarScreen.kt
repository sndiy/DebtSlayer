package com.hyse.debtslayer.ui.screens

import com.hyse.debtslayer.utils.CurrencyFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class DayStatus { FULL, PARTIAL, EMPTY, FUTURE, NO_DATA }

data class DayData(
    val date: Calendar,
    val status: DayStatus,
    val totalDeposit: Long,
    val targetThatDay: Long,
    val shortfall: Long,
    val isDeadline: Boolean = false  // ✅ BARU: flag untuk highlight deadline
)

@Composable
fun CalendarScreen(viewModel: DebtViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val totalDebt by viewModel.totalDebt.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val setupDate by viewModel.setupDate.collectAsState()  // ✅ BARU

    var displayedMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    var selectedDay by remember { mutableStateOf<DayData?>(null) }

    val activeDeadline = customDeadline ?: "2026-08-17"

    // ✅ Pass setupDate ke buildCalendarData
    val calendarData = remember(transactions, displayedMonth, totalDebt, activeDeadline, setupDate) {
        buildCalendarData(transactions, displayedMonth, totalDebt, activeDeadline, setupDate)
    }

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                }
                selectedDay = null
            }) {
                Icon(Icons.Default.ChevronLeft, "Bulan sebelumnya")
            }

            Text(
                text = monthFormat.format(displayedMonth.time).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
                selectedDay = null
            }) {
                Icon(Icons.Default.ChevronRight, "Bulan berikutnya")
            }
        }

        LegendRow()

        CalendarGrid(
            calendarData = calendarData,
            selectedDay = selectedDay,
            onDayClick = { dayData ->
                selectedDay = if (selectedDay?.date?.get(Calendar.DAY_OF_MONTH) ==
                    dayData.date.get(Calendar.DAY_OF_MONTH)) null else dayData
            }
        )

        selectedDay?.let { day ->
            DayDetailCard(day = day)
        }

        MonthSummaryCard(calendarData = calendarData)

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(color = Color(0xFF4CAF50), icon = Icons.Default.Check, label = "Full")
        LegendItem(color = Color(0xFF9E9E9E), icon = Icons.Default.Remove, label = "Sebagian")
        LegendItem(color = Color(0xFFF44336), icon = Icons.Default.Close, label = "Kosong")
    }
}

@Composable
fun LegendItem(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CalendarGrid(
    calendarData: List<DayData?>,
    selectedDay: DayData?,
    onDayClick: (DayData) -> Unit
) {
    val dayNames = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                dayNames.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (day == "Min") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val rows = calendarData.chunked(7)
            rows.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayData ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayData != null) {
                                DayCell(
                                    dayData = dayData,
                                    isSelected = selectedDay?.date?.get(Calendar.DAY_OF_MONTH) ==
                                            dayData.date.get(Calendar.DAY_OF_MONTH) &&
                                            selectedDay?.date?.get(Calendar.MONTH) ==
                                            dayData.date.get(Calendar.MONTH),
                                    onClick = { onDayClick(dayData) }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DayCell(dayData: DayData, isSelected: Boolean, onClick: () -> Unit) {
    val today = Calendar.getInstance()
    val isToday = dayData.date.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
            dayData.date.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            dayData.date.get(Calendar.YEAR) == today.get(Calendar.YEAR)

    // ✅ Background color
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        dayData.isDeadline -> Color(0xFFF44336).copy(alpha = 0.15f)  // ✅ Deadline: merah muda
        else -> Color.Transparent
    }

    // ✅ Indicator color (hanya untuk hari yang valid, bukan NO_DATA)
    val indicatorColor = when (dayData.status) {
        DayStatus.FULL -> Color(0xFF4CAF50)
        DayStatus.PARTIAL -> Color(0xFF9E9E9E)
        DayStatus.EMPTY -> Color(0xFFF44336)
        DayStatus.FUTURE, DayStatus.NO_DATA -> Color.Transparent
    }

    val indicatorIcon = when (dayData.status) {
        DayStatus.FULL -> Icons.Default.Check
        DayStatus.PARTIAL -> Icons.Default.Remove
        DayStatus.EMPTY -> Icons.Default.Close
        else -> null
    }

    Column(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                // ✅ Border untuk today dan deadline
                when {
                    isToday && !isSelected -> Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    dayData.isDeadline && !isSelected && !isToday -> Modifier.border(
                        2.dp,
                        Color(0xFFF44336),  // ✅ Deadline: border merah tebal
                        RoundedCornerShape(8.dp)
                    )
                    else -> Modifier
                }
            )
            .clickable(
                enabled = dayData.status != DayStatus.FUTURE && dayData.status != DayStatus.NO_DATA,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayData.date.get(Calendar.DAY_OF_MONTH).toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                dayData.isDeadline && !isToday -> Color(0xFFF44336)  // ✅ Deadline: teks merah
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        if (indicatorIcon != null) {
            Icon(
                imageVector = indicatorIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isSelected) Color.White else indicatorColor
            )
        }
    }
}

@Composable
fun DayDetailCard(day: DayData) {
    val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))

    val statusColor = when (day.status) {
        DayStatus.FULL -> Color(0xFF4CAF50)
        DayStatus.PARTIAL -> Color(0xFF9E9E9E)
        DayStatus.EMPTY -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val statusText = when (day.status) {
        DayStatus.FULL -> "✅ Target Selesai"
        DayStatus.PARTIAL -> "⚠️ Belum Penuh"
        DayStatus.EMPTY -> "❌ Tidak Ada Setoran"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (day.status) {
                DayStatus.FULL -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                DayStatus.PARTIAL -> MaterialTheme.colorScheme.surfaceVariant
                DayStatus.EMPTY -> Color(0xFFF44336).copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dateFormat.format(day.date.time).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ✅ Show deadline badge jika hari ini adalah deadline
            if (day.isDeadline) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "🎯 Hari ini adalah deadline!",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider()

            DetailInfoRow(
                label = "Target hari ini",
                value = CurrencyFormatter.format(day.targetThatDay),
                valueColor = MaterialTheme.colorScheme.primary
            )
            DetailInfoRow(
                label = "Total setoran",
                value = CurrencyFormatter.format(day.totalDeposit),
                valueColor = when (day.status) {
                    DayStatus.FULL -> Color(0xFF4CAF50)
                    DayStatus.PARTIAL -> Color(0xFF9E9E9E)
                    DayStatus.EMPTY -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            DetailInfoRow(
                label = "Sisa target",
                value = if (day.shortfall <= 0) "Rp 0 ✓" else CurrencyFormatter.format(day.shortfall),
                valueColor = if (day.shortfall <= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )

            if (day.targetThatDay > 0) {
                val progress = (day.totalDeposit.toFloat() / day.targetThatDay).coerceIn(0f, 1f)
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.15f)
                )
                Text(
                    "${(progress * 100).toInt()}% dari target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun MonthSummaryCard(calendarData: List<DayData?>) {
    // ✅ EXCLUDE NO_DATA dari summary
    val pastDays = calendarData.filterNotNull().filter {
        it.status != DayStatus.FUTURE && it.status != DayStatus.NO_DATA
    }
    if (pastDays.isEmpty()) return

    val fullDays = pastDays.count { it.status == DayStatus.FULL }
    val partialDays = pastDays.count { it.status == DayStatus.PARTIAL }
    val emptyDays = pastDays.count { it.status == DayStatus.EMPTY }
    val totalDeposited = pastDays.sumOf { it.totalDeposit }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "📊 Ringkasan Bulan Ini",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Divider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStatItem("✅ Full", fullDays.toString(), Color(0xFF4CAF50))
                SummaryStatItem("⚠️ Sebagian", partialDays.toString(), Color(0xFF9E9E9E))
                SummaryStatItem("❌ Kosong", emptyDays.toString(), Color(0xFFF44336))
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Disetor Bulan Ini", style = MaterialTheme.typography.bodyMedium)
                Text(
                    CurrencyFormatter.format(totalDeposited),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SummaryStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}


// ✅ UPDATED: Tambahkan parameter setupDate
fun buildCalendarData(
    transactions: List<Transaction>,
    displayedMonth: Calendar,
    totalDebt: Long,
    activeDeadline: String = "2026-08-17",
    setupDate: String? = null  // ✅ BARU
): List<DayData?> {
    val today = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val depositByDay = transactions.groupBy { dayFormat.format(Date(it.date)) }
        .mapValues { (_, txList) -> txList.sumOf { it.amount } }

    val firstDayOfWeek = displayedMonth.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val deadlineFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ✅ Parse deadline untuk comparison
    val deadlineDate = try {
        deadlineFormat.parse(activeDeadline)?.time
    } catch (e: Exception) {
        null
    }

    val result = mutableListOf<DayData?>()
    repeat(firstDayOfWeek) { result.add(null) }

    for (day in 1..daysInMonth) {
        val dayCal = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayKey = dayFormat.format(dayCal.time)

        // ✅ Cek apakah hari ini = deadline
        val isDeadline = deadlineDate != null && dayCal.timeInMillis == deadlineDate

        // ✅ Cek apakah tanggal < setup date → NO_DATA (tidak dihitung)
        val isBeforeSetup = if (setupDate != null) {
            try {
                val setupCal = Calendar.getInstance().apply {
                    time = dayFormat.parse(setupDate)!!
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dayCal.before(setupCal)
            } catch (e: Exception) {
                false
            }
        } else false

        if (isBeforeSetup) {
            // ✅ Tanggal sebelum setup → NO_DATA (tidak ada icon, tidak dihitung)
            result.add(DayData(
                date = dayCal,
                status = DayStatus.NO_DATA,
                totalDeposit = 0L,
                targetThatDay = 0L,
                shortfall = 0L,
                isDeadline = isDeadline
            ))
            continue
        }

        val isFuture = dayCal.after(today) &&
                !(dayCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                        dayCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        dayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR))

        if (isFuture) {
            val paidSoFar = transactions.filter { it.date < dayCal.timeInMillis }.sumOf { it.amount }
            val remaining = (totalDebt - paidSoFar).coerceAtLeast(0)
            val daysLeft = calculateDaysLeft(dayCal, deadlineFormat, activeDeadline)
            val rawTarget = if (daysLeft > 0) remaining / daysLeft else 0L
            val targetThatDay = CurrencyFormatter.ceilToThousand(rawTarget).coerceAtLeast(10_000L)

            result.add(DayData(
                date = dayCal,
                status = DayStatus.FUTURE,
                totalDeposit = 0L,
                targetThatDay = targetThatDay,
                shortfall = targetThatDay,
                isDeadline = isDeadline
            ))
        } else {
            val paidBeforeToday = transactions.filter {
                val txDay = dayFormat.format(Date(it.date))
                txDay < dayKey
            }.sumOf { it.amount }

            val remainingAtStartOfDay = (totalDebt - paidBeforeToday).coerceAtLeast(0)
            val daysFromThatDayToDeadline = calculateDaysLeft(dayCal, deadlineFormat, activeDeadline)
            val targetThatDay = if (daysFromThatDayToDeadline > 0) {
                val raw = remainingAtStartOfDay / daysFromThatDayToDeadline
                CurrencyFormatter.ceilToThousand(raw).coerceAtLeast(10_000L)
            } else {
                remainingAtStartOfDay.coerceAtLeast(0L)
            }

            val deposit = depositByDay[dayKey] ?: 0L
            val shortfall = (targetThatDay - deposit).coerceAtLeast(0L)

            val status = when {
                deposit == 0L -> DayStatus.EMPTY
                deposit >= targetThatDay -> DayStatus.FULL
                else -> DayStatus.PARTIAL
            }

            result.add(DayData(
                date = dayCal,
                status = status,
                totalDeposit = deposit,
                targetThatDay = targetThatDay,
                shortfall = shortfall,
                isDeadline = isDeadline
            ))
        }
    }

    while (result.size % 7 != 0) result.add(null)
    return result
}

fun calculateDaysLeft(fromDay: Calendar, deadlineFormat: SimpleDateFormat, deadlineStr: String = "2026-08-17"): Long {
    return try {
        val deadline = deadlineFormat.parse(deadlineStr) ?: return 180L
        val diff = deadline.time - fromDay.timeInMillis
        (diff / (1000L * 60 * 60 * 24)).coerceAtLeast(1L)
    } catch (e: Exception) {
        180L
    }
}