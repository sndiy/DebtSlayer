package com.hyse.debtslayer.ui.screens

import com.hyse.debtslayer.utils.CurrencyFormatter
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.viewmodel.DebtViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─── Semantic status colors (fixed) ──────────────────────────────────────────
private val GreenFull    = Color(0xFF22C55E)
private val AmberPartial = Color(0xFFF59E0B)
private val RedEmpty     = Color(0xFFEF4444)

// ─── Enums & data ─────────────────────────────────────────────────────────────
enum class DayStatus { FULL, PARTIAL, EMPTY, FUTURE, NO_DATA }

data class DayData(
    val date: Calendar,
    val status: DayStatus,
    val totalDeposit: Long,
    val targetThatDay: Long,
    val shortfall: Long,
    val isDeadline: Boolean = false
)

// ─── Responsive dimensions helper ────────────────────────────────────────────
/**
 * Menyediakan ukuran adaptif berdasarkan lebar layar (dp).
 * Breakpoints: small < 360dp, medium 360–420dp, large > 420dp
 */
internal data class ResponsiveDimens(
    val screenWidthDp: Int,
    // Spacing
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val cardSpacing: Dp,
    // Calendar cell
    val cellCorner: Dp,
    val cellDotSize: Dp,
    val cellNumberSize: TextUnit,
    val cellStatusDotSize: Dp,
    // Typography
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val bodySize: TextUnit,
    val labelSize: TextUnit,
    val captionSize: TextUnit,
    val badgeSize: TextUnit,
    // Components
    val navButtonSize: Dp,
    val navButtonCorner: Dp,
    val navIconSize: Dp,
    val headerIconSize: Dp,
    val headerIconCorner: Dp,
    val headerIconFontSize: TextUnit,  // font size untuk emoji di header icon
    val deadlineIconSize: TextUnit,    // TextUnit — dipakai sebagai fontSize
    val deadlineIconBoxSize: Dp,
    val statCardValueSize: TextUnit,
    val statCardLabelSize: TextUnit,
    val summaryStripHeight: Dp,
    val progressBarHeight: Dp,
    val detailRowCorner: Dp,
)

@Composable
private fun rememberResponsiveDimens(): ResponsiveDimens {
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp
    return remember(screenW) {
        when {
            screenW < 360 -> ResponsiveDimens( // Small (e.g. 320dp)
                screenWidthDp       = screenW,
                horizontalPadding   = 10.dp,
                verticalPadding     = 14.dp,
                cardSpacing         = 8.dp,
                cellCorner          = 8.dp,
                cellDotSize         = 4.dp,
                cellNumberSize      = 10.sp,
                cellStatusDotSize   = 4.dp,
                titleSize           = 15.sp,
                subtitleSize        = 8.sp,
                bodySize            = 11.sp,
                labelSize           = 10.sp,
                captionSize         = 9.sp,
                badgeSize           = 10.sp,
                navButtonSize       = 28.dp,
                navButtonCorner     = 7.dp,
                navIconSize         = 14.dp,
                headerIconSize      = 26.dp,
                headerIconCorner    = 7.dp,
                headerIconFontSize  = 12.sp,
                deadlineIconSize    = 13.sp,
                deadlineIconBoxSize = 30.dp,
                statCardValueSize   = 18.sp,
                statCardLabelSize   = 9.sp,
                summaryStripHeight  = 14.dp,
                progressBarHeight   = 5.dp,
                detailRowCorner     = 8.dp,
            )
            screenW <= 420 -> ResponsiveDimens( // Medium (360–420dp, most common)
                screenWidthDp       = screenW,
                horizontalPadding   = 14.dp,
                verticalPadding     = 18.dp,
                cardSpacing         = 10.dp,
                cellCorner          = 10.dp,
                cellDotSize         = 5.dp,
                cellNumberSize      = 12.sp,
                cellStatusDotSize   = 5.dp,
                titleSize           = 17.sp,
                subtitleSize        = 9.sp,
                bodySize            = 12.sp,
                labelSize           = 11.sp,
                captionSize         = 10.sp,
                badgeSize           = 11.sp,
                navButtonSize       = 34.dp,
                navButtonCorner     = 9.dp,
                navIconSize         = 18.dp,
                headerIconSize      = 32.dp,
                headerIconCorner    = 9.dp,
                headerIconFontSize  = 15.sp,
                deadlineIconSize    = 15.sp,
                deadlineIconBoxSize = 36.dp,
                statCardValueSize   = 22.sp,
                statCardLabelSize   = 10.sp,
                summaryStripHeight  = 18.dp,
                progressBarHeight   = 6.dp,
                detailRowCorner     = 10.dp,
            )
            else -> ResponsiveDimens( // Large (> 420dp, big phones/tablets)
                screenWidthDp       = screenW,
                horizontalPadding   = 20.dp,
                verticalPadding     = 24.dp,
                cardSpacing         = 14.dp,
                cellCorner          = 12.dp,
                cellDotSize         = 6.dp,
                cellNumberSize      = 14.sp,
                cellStatusDotSize   = 6.dp,
                titleSize           = 20.sp,
                subtitleSize        = 11.sp,
                bodySize            = 14.sp,
                labelSize           = 13.sp,
                captionSize         = 11.sp,
                badgeSize           = 12.sp,
                navButtonSize       = 40.dp,
                navButtonCorner     = 11.dp,
                navIconSize         = 22.dp,
                headerIconSize      = 38.dp,
                headerIconCorner    = 11.dp,
                headerIconFontSize  = 18.sp,
                deadlineIconSize    = 18.sp,
                deadlineIconBoxSize = 44.dp,
                statCardValueSize   = 26.sp,
                statCardLabelSize   = 12.sp,
                summaryStripHeight  = 22.dp,
                progressBarHeight   = 7.dp,
                detailRowCorner     = 12.dp,
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun CalendarScreen(viewModel: DebtViewModel) {
    val d = rememberResponsiveDimens()

    val transactions   by viewModel.transactions.collectAsState()
    val totalDebt      by viewModel.totalDebt.collectAsState()
    val customDeadline by viewModel.customDeadline.collectAsState()
    val setupDate      by viewModel.setupDate.collectAsState()

    var displayedMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); clearTime()
        })
    }
    var selectedDay by remember { mutableStateOf<DayData?>(null) }

    val activeDeadline = customDeadline ?: "2026-08-17"

    val calendarData = remember(transactions, displayedMonth, totalDebt, activeDeadline) {
        buildCalendarData(
            transactions   = transactions,
            displayedMonth = displayedMonth,
            totalDebt      = totalDebt,
            activeDeadline = activeDeadline,
            setupDate      = null
        )
    }

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = d.horizontalPadding, vertical = d.verticalPadding),
        verticalArrangement = Arrangement.spacedBy(d.cardSpacing)
    ) {
        ScreenHeader(d)

        MonthNavigator(
            label  = monthFormat.format(displayedMonth.time).replaceFirstChar { it.uppercase() },
            d      = d,
            onPrev = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                selectedDay = null
            },
            onNext = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                selectedDay = null
            }
        )

        LegendRow(d)

        CalendarGrid(
            calendarData = calendarData,
            selectedDay  = selectedDay,
            d            = d,
            onDayClick   = { dayData ->
                selectedDay = if (selectedDay?.date?.get(Calendar.DAY_OF_MONTH) ==
                    dayData.date.get(Calendar.DAY_OF_MONTH)) null else dayData
            }
        )

        selectedDay?.let { DayDetailCard(day = it, d = d) }

        MonthSummaryCard(calendarData = calendarData, d = d)

        DeadlineReminderCard(deadline = activeDeadline, d = d)

        Spacer(Modifier.height(d.verticalPadding))
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun ScreenHeader(d: ResponsiveDimens) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(d.headerIconSize)
                .clip(RoundedCornerShape(d.headerIconCorner))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("⚔️", fontSize = d.headerIconFontSize)
        }
        Column {
            Text(
                text          = "DEBTSLAYER",
                fontSize      = d.subtitleSize,
                letterSpacing = 1.5.sp,
                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight    = FontWeight.SemiBold
            )
            Text(
                text       = "Kalender Setoran",
                fontSize   = d.titleSize,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ─── Month Navigator ──────────────────────────────────────────────────────────
@Composable
private fun MonthNavigator(label: String, d: ResponsiveDimens, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavArrowButton(onClick = onPrev, icon = Icons.Default.ChevronLeft, d = d)
            Text(
                text       = label,
                fontSize   = d.bodySize,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            NavArrowButton(onClick = onNext, icon = Icons.Default.ChevronRight, d = d)
        }
    }
}

@Composable
private fun NavArrowButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    d: ResponsiveDimens
) {
    Box(
        modifier = Modifier
            .size(d.navButtonSize)
            .clip(RoundedCornerShape(d.navButtonCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(d.navIconSize)
        )
    }
}

// ─── Legend ──────────────────────────────────────────────────────────────────
@Composable
private fun LegendRow(d: ResponsiveDimens) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        LegendDot(GreenFull,    "Lunas",    d)
        Spacer(Modifier.width(14.dp))
        LegendDot(AmberPartial, "Sebagian", d)
        Spacer(Modifier.width(14.dp))
        LegendDot(RedEmpty,     "Kosong",   d)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LegendDot(color: Color, label: String, d: ResponsiveDimens) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(d.cellDotSize + 3.dp).clip(CircleShape).background(color))
        Text(label, fontSize = d.labelSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Calendar Grid ────────────────────────────────────────────────────────────
@Composable
private fun CalendarGrid(
    calendarData: List<DayData?>,
    selectedDay: DayData?,
    d: ResponsiveDimens,
    onDayClick: (DayData) -> Unit
) {
    val dayNames = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header nama hari — responsif
            Row(modifier = Modifier.fillMaxWidth()) {
                dayNames.forEachIndexed { idx, day ->
                    Text(
                        text          = day,
                        modifier      = Modifier.weight(1f),
                        textAlign     = TextAlign.Center,
                        fontSize      = d.labelSize,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                        color = if (idx == 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val rows = calendarData.chunked(7)
            rows.forEachIndexed { rowIdx, week ->
                if (rowIdx > 0) Spacer(Modifier.height(3.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayData ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayData != null) {
                                DayCell(
                                    dayData    = dayData,
                                    isSelected = selectedDay?.date?.get(Calendar.DAY_OF_MONTH) ==
                                            dayData.date.get(Calendar.DAY_OF_MONTH) &&
                                            selectedDay?.date?.get(Calendar.MONTH) ==
                                            dayData.date.get(Calendar.MONTH),
                                    d          = d,
                                    onClick    = { onDayClick(dayData) }
                                )
                            } else {
                                // Spacer yang proporsional — pakai AspectRatio agar cell selalu persegi
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── Day Cell — menggunakan fillMaxWidth + aspectRatio agar responsif ─────────
@Composable
private fun DayCell(dayData: DayData, isSelected: Boolean, d: ResponsiveDimens, onClick: () -> Unit) {
    val today            = Calendar.getInstance()
    val isToday          = dayData.date.isSameDay(today)
    val isFutureOrNoData = dayData.status == DayStatus.FUTURE || dayData.status == DayStatus.NO_DATA

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusDotColor = when (dayData.status) {
        DayStatus.FULL    -> GreenFull
        DayStatus.PARTIAL -> AmberPartial
        DayStatus.EMPTY   -> RedEmpty
        else              -> Color.Transparent
    }

    val bgColor = when {
        isSelected         -> MaterialTheme.colorScheme.primary
        isToday            -> MaterialTheme.colorScheme.primaryContainer
        dayData.isDeadline -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        dayData.status == DayStatus.FULL    -> GreenFull.copy(alpha = 0.10f)
        dayData.status == DayStatus.PARTIAL -> AmberPartial.copy(alpha = 0.10f)
        dayData.status == DayStatus.EMPTY   -> RedEmpty.copy(alpha = 0.08f)
        else               -> Color.Transparent
    }

    val borderMod: Modifier = when {
        isSelected -> Modifier
        isToday    -> Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
            shape = RoundedCornerShape(d.cellCorner)
        )
        dayData.isDeadline && !isToday -> Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            shape = RoundedCornerShape(d.cellCorner)
        )
        else -> Modifier
    }

    // KEY CHANGE: fillMaxWidth() + aspectRatio(1f) — cell selalu persegi,
    // lebar menyesuaikan 1/7 layar, otomatis responsif di semua ukuran.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(d.cellCorner))
            .background(bgColor)
            .then(borderMod)
            .clickable(enabled = !isFutureOrNoData, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize()
        ) {
            Text(
                text       = dayData.date.get(Calendar.DAY_OF_MONTH).toString(),
                fontSize   = d.cellNumberSize,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color      = when {
                    isSelected         -> MaterialTheme.colorScheme.onPrimary
                    isToday            -> MaterialTheme.colorScheme.onPrimaryContainer
                    isFutureOrNoData   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    dayData.isDeadline -> MaterialTheme.colorScheme.error
                    else               -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (!isFutureOrNoData) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(d.cellStatusDotSize)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else statusDotColor
                        )
                )
            }
        }

        // Deadline dot di sudut kanan atas
        if (dayData.isDeadline && !isSelected) {
            Box(
                modifier = Modifier
                    .size(d.cellDotSize + 2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
            )
        }
    }
}

// ─── Day Detail Card ──────────────────────────────────────────────────────────
@Composable
private fun DayDetailCard(day: DayData, d: ResponsiveDimens) {
    val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))

    val (statusColor, statusLabel) = when (day.status) {
        DayStatus.FULL    -> GreenFull    to "Lunas"
        DayStatus.PARTIAL -> AmberPartial to "Sebagian"
        DayStatus.EMPTY   -> RedEmpty     to "Kosong"
        else              -> MaterialTheme.colorScheme.onSurface to ""
    }

    val progress = if (day.targetThatDay > 0)
        (day.totalDeposit.toFloat() / day.targetThatDay).coerceIn(0f, 1f)
    else 0f

    val progressAnim by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(600, easing = EaseOutCubic),
        label         = "progress"
    )

    val statusEmoji = when (day.status) {
        DayStatus.FULL    -> "✅"
        DayStatus.PARTIAL -> "⚡"
        DayStatus.EMPTY   -> "❌"
        else              -> ""
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            // ── Colored header banner ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier              = Modifier.weight(1f)
                    ) {
                        // Status icon circle
                        Box(
                            modifier = Modifier
                                .size(d.navButtonSize)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(statusEmoji, fontSize = d.labelSize)
                        }
                        Column {
                            Text(
                                text       = dateFormat.format(day.date.time).replaceFirstChar { it.uppercase() },
                                fontSize   = d.bodySize,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                                maxLines   = 2,
                                softWrap   = true
                            )
                            if (day.isDeadline) {
                                Text(
                                    "🎯 Hari Deadline",
                                    fontSize = d.captionSize,
                                    color    = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            statusLabel,
                            fontSize   = d.badgeSize,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
            }

            // ── Progress section ───────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Progress setoran",
                        fontSize = d.captionSize,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(progressAnim * 100).toInt()}%",
                        fontSize   = d.captionSize,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                }
                LinearProgressIndicator(
                    progress   = progressAnim,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(d.progressBarHeight + 2.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color      = statusColor,
                    trackColor = statusColor.copy(alpha = 0.12f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── Stat rows ──────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailInfoRow(
                    icon       = "🎯",
                    label      = "Target hari ini",
                    value      = CurrencyFormatter.format(day.targetThatDay),
                    valueColor = MaterialTheme.colorScheme.primary,
                    d          = d
                )
                DetailInfoRow(
                    icon       = "💰",
                    label      = "Total disetor",
                    value      = CurrencyFormatter.format(day.totalDeposit),
                    valueColor = statusColor,
                    d          = d
                )
                DetailInfoRow(
                    icon       = "📉",
                    label      = "Kekurangan",
                    value      = if (day.shortfall <= 0) "Rp 0 ✓" else CurrencyFormatter.format(day.shortfall),
                    valueColor = if (day.shortfall <= 0) GreenFull else RedEmpty,
                    d          = d
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(icon: String = "", label: String, value: String, valueColor: Color, d: ResponsiveDimens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.detailRowCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.weight(1f)
        ) {
            if (icon.isNotEmpty()) {
                Text(icon, fontSize = d.labelSize)
            }
            Text(
                label,
                fontSize = d.labelSize,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize   = d.bodySize,
            fontWeight = FontWeight.Bold,
            color      = valueColor,
            textAlign  = TextAlign.End,
            maxLines   = 1
        )
    }
}

// ─── Month Summary Card ───────────────────────────────────────────────────────
@Composable
private fun MonthSummaryCard(calendarData: List<DayData?>, d: ResponsiveDimens) {
    val pastDays = calendarData.filterNotNull().filter {
        it.status != DayStatus.FUTURE && it.status != DayStatus.NO_DATA
    }
    if (pastDays.isEmpty()) return

    val fullDays       = pastDays.count { it.status == DayStatus.FULL }
    val partialDays    = pastDays.count { it.status == DayStatus.PARTIAL }
    val emptyDays      = pastDays.count { it.status == DayStatus.EMPTY }
    val totalDeposited = pastDays.sumOf { it.totalDeposit }
    val consistencyPct = if (pastDays.isNotEmpty()) (fullDays * 100 / pastDays.size) else 0

    val rateColor = when {
        consistencyPct >= 70 -> GreenFull
        consistencyPct >= 40 -> AmberPartial
        else                 -> RedEmpty
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Ringkasan Bulan Ini",
                    fontSize   = d.bodySize,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape  = RoundedCornerShape(20.dp),
                    color  = rateColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, rateColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        "$consistencyPct% konsisten",
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize   = d.badgeSize,
                        fontWeight = FontWeight.Bold,
                        color      = rateColor
                    )
                }
            }

            // Consistency strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.summaryStripHeight),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                pastDays.forEach { day ->
                    val stripColor = when (day.status) {
                        DayStatus.FULL    -> GreenFull
                        DayStatus.PARTIAL -> AmberPartial
                        else              -> RedEmpty
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(stripColor.copy(alpha = 0.75f))
                    )
                }
            }

            // 3 stat cards — IntrinsicSize.Min agar tinggi seragam
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple(fullDays,    "Lunas",    GreenFull),
                    Triple(partialDays, "Sebagian", AmberPartial),
                    Triple(emptyDays,   "Kosong",   RedEmpty),
                ).forEach { (count, label, color) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier            = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                count.toString(),
                                fontSize   = d.statCardValueSize,
                                fontWeight = FontWeight.ExtraBold,
                                color      = color
                            )
                            Text(
                                label,
                                fontSize  = d.statCardLabelSize,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Total disetor — wrap label agar tidak terpotong
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Total disetor bulan ini",
                    fontSize = d.labelSize,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    CurrencyFormatter.format(totalDeposited),
                    fontSize   = d.bodySize,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    textAlign  = TextAlign.End,
                    maxLines   = 1
                )
            }
        }
    }
}

// ─── Deadline Reminder ────────────────────────────────────────────────────────
@Composable
private fun DeadlineReminderCard(deadline: String, d: ResponsiveDimens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(d.deadlineIconBoxSize)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("🗓️", fontSize = d.deadlineIconSize)
        }
        Column {
            Text(
                "Deadline: $deadline",
                fontSize   = d.labelSize,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.error
            )
            Text(
                "Tetap konsisten setiap hari!",
                fontSize = d.captionSize,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun Calendar.clearTime() {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
}

private fun Calendar.isSameDay(other: Calendar) =
    get(Calendar.DAY_OF_MONTH) == other.get(Calendar.DAY_OF_MONTH) &&
            get(Calendar.MONTH)        == other.get(Calendar.MONTH) &&
            get(Calendar.YEAR)         == other.get(Calendar.YEAR)

// ─── buildCalendarData ────────────────────────────────────────────────────────
fun buildCalendarData(
    transactions: List<Transaction>,
    displayedMonth: Calendar,
    totalDebt: Long,
    activeDeadline: String = "2026-08-17",
    setupDate: String? = null
): List<DayData?> {
    val today     = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val depositByDay = transactions.groupBy { dayFormat.format(Date(it.date)) }
        .mapValues { (_, txList) -> txList.sumOf { it.amount } }

    val firstDayOfWeek = displayedMonth.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth    = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val deadlineFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val deadlineDate = try {
        deadlineFormat.parse(activeDeadline)?.let { parsed ->
            Calendar.getInstance().apply { time = parsed; clearTime() }.timeInMillis
        }
    } catch (e: Exception) { null }

    val trackingStartCal: Calendar = run {
        if (!setupDate.isNullOrBlank()) {
            try {
                val parsed = dayFormat.parse(setupDate)
                if (parsed != null) {
                    return@run Calendar.getInstance().apply { time = parsed; clearTime() }
                }
            } catch (e: Exception) { /* fallback */ }
        }
        val earliestTx = transactions.minByOrNull { it.date }
        if (earliestTx != null) {
            return@run Calendar.getInstance().apply {
                timeInMillis = earliestTx.date; clearTime()
            }
        }
        Calendar.getInstance().apply { clearTime() }
    }

    val result = mutableListOf<DayData?>()
    repeat(firstDayOfWeek) { result.add(null) }

    for (day in 1..daysInMonth) {
        val dayCal = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, day); clearTime()
        }
        val dayKey     = dayFormat.format(dayCal.time)
        val isDeadline = deadlineDate != null && dayCal.timeInMillis == deadlineDate

        if (dayCal.before(trackingStartCal)) {
            result.add(DayData(dayCal, DayStatus.NO_DATA, 0L, 0L, 0L, isDeadline))
            continue
        }

        val isFuture = dayCal.after(today)

        if (isFuture) {
            val paidSoFar     = transactions.filter { it.date < dayCal.timeInMillis }.sumOf { it.amount }
            val remaining     = (totalDebt - paidSoFar).coerceAtLeast(0)
            val daysLeft      = calculateDaysLeft(dayCal, deadlineFormat, activeDeadline)
            val rawTarget     = if (daysLeft > 0) remaining / daysLeft else 0L
            val targetThatDay = CurrencyFormatter.ceilToThousand(rawTarget).coerceAtLeast(0L)
            result.add(DayData(dayCal, DayStatus.FUTURE, 0L, targetThatDay, targetThatDay, isDeadline))
        } else {
            val paidBeforeToday           = transactions.filter { dayFormat.format(Date(it.date)) < dayKey }.sumOf { it.amount }
            val remainingAtStartOfDay     = (totalDebt - paidBeforeToday).coerceAtLeast(0)
            val daysFromThatDayToDeadline = calculateDaysLeft(dayCal, deadlineFormat, activeDeadline)
            val targetThatDay = if (daysFromThatDayToDeadline > 0) {
                CurrencyFormatter.ceilToThousand(remainingAtStartOfDay / daysFromThatDayToDeadline).coerceAtLeast(0L)
            } else {
                remainingAtStartOfDay.coerceAtLeast(0L)
            }
            val deposit   = depositByDay[dayKey] ?: 0L
            val shortfall = (targetThatDay - deposit).coerceAtLeast(0L)
            val status = when {
                targetThatDay == 0L && deposit == 0L -> DayStatus.FULL
                deposit == 0L                        -> DayStatus.EMPTY
                deposit >= targetThatDay             -> DayStatus.FULL
                else                                 -> DayStatus.PARTIAL
            }
            result.add(DayData(dayCal, status, deposit, targetThatDay, shortfall, isDeadline))
        }
    }

    while (result.size % 7 != 0) result.add(null)
    return result
}

fun calculateDaysLeft(
    fromDay: Calendar,
    deadlineFormat: SimpleDateFormat,
    deadlineStr: String = "2026-08-17"
): Long {
    return try {
        val deadline = deadlineFormat.parse(deadlineStr) ?: return 180L
        val diff     = deadline.time - fromDay.timeInMillis
        (diff / (1000L * 60 * 60 * 24)).coerceAtLeast(1L)
    } catch (e: Exception) { 180L }
}