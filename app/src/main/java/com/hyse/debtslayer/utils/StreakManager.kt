// app/src/main/java/com/hyse/debtslayer/utils/StreakManager.kt
package com.hyse.debtslayer.utils

import com.hyse.debtslayer.data.entity.Transaction
import java.text.SimpleDateFormat
import java.util.*

data class StreakData(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastDepositDate: String = "",
    val isActiveToday: Boolean = false,
    val streakDates: List<String> = emptyList()
)

object StreakManager {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun calculate(transactions: List<Transaction>, dailyTarget: Long): StreakData {
        if (transactions.isEmpty()) return StreakData()

        val today = sdf.format(Date())
        val yesterday = sdf.format(Date(System.currentTimeMillis() - 86_400_000L))

        // Group by date, sum amount
        val depositByDay = transactions
            .groupBy { sdf.format(Date(it.date)) }
            .mapValues { (_, txList) -> txList.sumOf { it.amount } }
            .toSortedMap()

        if (depositByDay.isEmpty()) return StreakData()

        // Hitung streak: hari yang memenuhi target (atau ada deposit jika target 0)
        val qualifiedDays = depositByDay
            .filter { (_, amount) ->
                if (dailyTarget > 0) amount >= dailyTarget else amount > 0
            }
            .keys
            .sorted()

        if (qualifiedDays.isEmpty()) return StreakData()

        val lastDepositDate = depositByDay.keys.last()
        val isActiveToday = depositByDay.containsKey(today) &&
                (if (dailyTarget > 0) (depositByDay[today] ?: 0L) >= dailyTarget
                else (depositByDay[today] ?: 0L) > 0)

        // Hitung current streak — mundur dari hari ini atau kemarin
        var currentStreak = 0
        val startCheckFrom = if (isActiveToday) today else yesterday

        val cal = Calendar.getInstance()
        try {
            cal.time = sdf.parse(startCheckFrom) ?: Date()
        } catch (e: Exception) {
            cal.time = Date()
        }

        // Hitung streak mundur
        val streakDates = mutableListOf<String>()
        while (true) {
            val dateStr = sdf.format(cal.time)
            if (qualifiedDays.contains(dateStr)) {
                currentStreak++
                streakDates.add(0, dateStr)
                cal.add(Calendar.DAY_OF_MONTH, -1)
            } else {
                break
            }
        }

        // Hitung longest streak
        var longestStreak = 0
        var tempStreak = 1
        for (i in 1 until qualifiedDays.size) {
            val prev = sdf.parse(qualifiedDays[i - 1]) ?: continue
            val curr = sdf.parse(qualifiedDays[i]) ?: continue
            val diffDays = ((curr.time - prev.time) / 86_400_000L).toInt()
            if (diffDays == 1) {
                tempStreak++
                if (tempStreak > longestStreak) longestStreak = tempStreak
            } else {
                tempStreak = 1
            }
        }
        if (longestStreak == 0 && qualifiedDays.isNotEmpty()) longestStreak = 1
        longestStreak = maxOf(longestStreak, currentStreak)

        return StreakData(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastDepositDate = lastDepositDate,
            isActiveToday = isActiveToday,
            streakDates = streakDates
        )
    }
}