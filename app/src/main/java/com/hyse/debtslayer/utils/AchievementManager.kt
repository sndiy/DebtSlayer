// app/src/main/java/com/hyse/debtslayer/utils/AchievementManager.kt
package com.hyse.debtslayer.utils

import com.hyse.debtslayer.data.entity.Transaction

enum class AchievementId {
    FIRST_DEPOSIT,
    STREAK_3,
    STREAK_7,
    STREAK_14,
    STREAK_30,
    PROGRESS_25,
    PROGRESS_50,
    PROGRESS_75,
    PROGRESS_100,
    SPEED_SLAYER,
    CONSISTENT_5,
    EARLY_BIRD,
    NIGHT_OWL
}

data class Achievement(
    val id: AchievementId,
    val title: String,
    val description: String,
    val emoji: String,
    val tier: AchievementTier,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val progress: Float = 0f,         // 0f..1f untuk progress bar
    val progressLabel: String = ""
)

enum class AchievementTier {
    BRONZE, SILVER, GOLD, PLATINUM
}

data class AchievementCheckResult(
    val newlyUnlocked: List<Achievement>,
    val allAchievements: List<Achievement>
)

object AchievementManager {

    fun buildAll(
        transactions: List<Transaction>,
        streakData: StreakData,
        totalDebt: Long,
        totalPaid: Long,
        dailyTarget: Long,
        unlockedIds: Set<String>
    ): AchievementCheckResult {

        val progressPct = if (totalDebt > 0)
            (totalPaid.toFloat() / totalDebt * 100f).coerceAtMost(100f) else 0f

        val todayDeposit = run {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = sdf.format(java.util.Date())
            transactions
                .filter { sdf.format(java.util.Date(it.date)) == today }
                .sumOf { it.amount }
        }

        // Cek EARLY_BIRD (setor sebelum jam 9 pagi)
        val hasEarlyBird = transactions.any {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.date }
            cal.get(java.util.Calendar.HOUR_OF_DAY) < 9
        }

        // Cek NIGHT_OWL (setor setelah jam 10 malam)
        val hasNightOwl = transactions.any {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.date }
            cal.get(java.util.Calendar.HOUR_OF_DAY) >= 22
        }

        // Cek CONSISTENT_5 — 5 hari berturut tepat/lebih target
        val consistent5 = streakData.longestStreak >= 5

        val definitions = listOf(
            // ── Streak ──────────────────────────────────────────────────
            Achievement(
                id = AchievementId.FIRST_DEPOSIT,
                title = "Langkah Pertama",
                description = "Lakukan setoran pertamamu",
                emoji = "🌱",
                tier = AchievementTier.BRONZE,
                progress = if (transactions.isNotEmpty()) 1f else 0f,
                progressLabel = "${transactions.size}/1 setoran"
            ),
            Achievement(
                id = AchievementId.STREAK_3,
                title = "Mulai Konsisten",
                description = "Setor memenuhi target 3 hari berturut-turut",
                emoji = "🔥",
                tier = AchievementTier.BRONZE,
                progress = (streakData.currentStreak.coerceAtMost(3) / 3f),
                progressLabel = "${streakData.currentStreak.coerceAtMost(3)}/3 hari"
            ),
            Achievement(
                id = AchievementId.STREAK_7,
                title = "Seminggu Penuh",
                description = "Setor memenuhi target 7 hari berturut-turut",
                emoji = "⚡",
                tier = AchievementTier.SILVER,
                progress = (streakData.longestStreak.coerceAtMost(7) / 7f),
                progressLabel = "${streakData.longestStreak.coerceAtMost(7)}/7 hari"
            ),
            Achievement(
                id = AchievementId.STREAK_14,
                title = "Dua Minggu Nonstop",
                description = "Setor memenuhi target 14 hari berturut-turut",
                emoji = "💎",
                tier = AchievementTier.GOLD,
                progress = (streakData.longestStreak.coerceAtMost(14) / 14f),
                progressLabel = "${streakData.longestStreak.coerceAtMost(14)}/14 hari"
            ),
            Achievement(
                id = AchievementId.STREAK_30,
                title = "Satu Bulan Legenda",
                description = "Setor memenuhi target 30 hari berturut-turut",
                emoji = "👑",
                tier = AchievementTier.PLATINUM,
                progress = (streakData.longestStreak.coerceAtMost(30) / 30f),
                progressLabel = "${streakData.longestStreak.coerceAtMost(30)}/30 hari"
            ),

            // ── Progress ─────────────────────────────────────────────────
            Achievement(
                id = AchievementId.PROGRESS_25,
                title = "Seperempat Jalan",
                description = "Lunasi 25% dari total hutang",
                emoji = "🛡️",
                tier = AchievementTier.BRONZE,
                progress = (progressPct / 25f).coerceAtMost(1f),
                progressLabel = "${String.format("%.1f", progressPct.coerceAtMost(25f))}/25%"
            ),
            Achievement(
                id = AchievementId.PROGRESS_50,
                title = "Setengah Lunas",
                description = "Lunasi 50% dari total hutang",
                emoji = "⚔️",
                tier = AchievementTier.SILVER,
                progress = (progressPct / 50f).coerceAtMost(1f),
                progressLabel = "${String.format("%.1f", progressPct.coerceAtMost(50f))}/50%"
            ),
            Achievement(
                id = AchievementId.PROGRESS_75,
                title = "Hampir Merdeka",
                description = "Lunasi 75% dari total hutang",
                emoji = "🗡️",
                tier = AchievementTier.GOLD,
                progress = (progressPct / 75f).coerceAtMost(1f),
                progressLabel = "${String.format("%.1f", progressPct.coerceAtMost(75f))}/75%"
            ),
            Achievement(
                id = AchievementId.PROGRESS_100,
                title = "DEBT SLAYER",
                description = "Lunasi 100% hutang. Kamu menang.",
                emoji = "🏆",
                tier = AchievementTier.PLATINUM,
                progress = (progressPct / 100f).coerceAtMost(1f),
                progressLabel = "${String.format("%.1f", progressPct)}/100%"
            ),

            // ── Special ──────────────────────────────────────────────────
            Achievement(
                id = AchievementId.SPEED_SLAYER,
                title = "Speed Slayer",
                description = "Setor 2x lipat target dalam satu hari",
                emoji = "💥",
                tier = AchievementTier.SILVER,
                progress = if (dailyTarget > 0 && todayDeposit >= dailyTarget * 2) 1f
                else if (dailyTarget > 0) (todayDeposit / (dailyTarget * 2f)).coerceAtMost(0.99f)
                else 0f,
                progressLabel = if (dailyTarget > 0)
                    "${CurrencyFormatter.format(todayDeposit)}/${CurrencyFormatter.format(dailyTarget * 2)}"
                else "—"
            ),
            Achievement(
                id = AchievementId.CONSISTENT_5,
                title = "Streak Konsisten",
                description = "Capai streak 5 hari atau lebih",
                emoji = "📅",
                tier = AchievementTier.SILVER,
                progress = (streakData.longestStreak.coerceAtMost(5) / 5f),
                progressLabel = "${streakData.longestStreak.coerceAtMost(5)}/5 hari"
            ),
            Achievement(
                id = AchievementId.EARLY_BIRD,
                title = "Early Bird",
                description = "Setor sebelum jam 9 pagi",
                emoji = "🌅",
                tier = AchievementTier.BRONZE,
                progress = if (hasEarlyBird) 1f else 0f,
                progressLabel = if (hasEarlyBird) "Selesai" else "Belum"
            ),
            Achievement(
                id = AchievementId.NIGHT_OWL,
                title = "Night Owl",
                description = "Setor setelah jam 10 malam",
                emoji = "🦉",
                tier = AchievementTier.BRONZE,
                progress = if (hasNightOwl) 1f else 0f,
                progressLabel = if (hasNightOwl) "Selesai" else "Belum"
            )
        )

        // Tentukan isUnlocked berdasarkan progress & kondisi
        val unlockConditions = mapOf(
            AchievementId.FIRST_DEPOSIT to (transactions.isNotEmpty()),
            AchievementId.STREAK_3      to (streakData.currentStreak >= 3 || streakData.longestStreak >= 3),
            AchievementId.STREAK_7      to (streakData.longestStreak >= 7),
            AchievementId.STREAK_14     to (streakData.longestStreak >= 14),
            AchievementId.STREAK_30     to (streakData.longestStreak >= 30),
            AchievementId.PROGRESS_25   to (progressPct >= 25f),
            AchievementId.PROGRESS_50   to (progressPct >= 50f),
            AchievementId.PROGRESS_75   to (progressPct >= 75f),
            AchievementId.PROGRESS_100  to (progressPct >= 100f),
            AchievementId.SPEED_SLAYER  to (dailyTarget > 0 && todayDeposit >= dailyTarget * 2),
            AchievementId.CONSISTENT_5  to consistent5,
            AchievementId.EARLY_BIRD    to hasEarlyBird,
            AchievementId.NIGHT_OWL     to hasNightOwl
        )

        val now = System.currentTimeMillis()
        val finalList = definitions.map { ach ->
            val shouldUnlock = unlockConditions[ach.id] == true
            val wasAlreadyUnlocked = unlockedIds.contains(ach.id.name)
            ach.copy(
                isUnlocked = shouldUnlock,
                unlockedAt = if (shouldUnlock && wasAlreadyUnlocked)
                    now else if (shouldUnlock) now else null
            )
        }

        val newlyUnlocked = finalList.filter { ach ->
            ach.isUnlocked && !unlockedIds.contains(ach.id.name)
        }

        return AchievementCheckResult(
            newlyUnlocked = newlyUnlocked,
            allAchievements = finalList
        )
    }
}