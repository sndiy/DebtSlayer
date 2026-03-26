// app/src/main/java/com/hyse/debtslayer/ui/screens/AchievementScreen.kt
package com.hyse.debtslayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hyse.debtslayer.utils.Achievement
import com.hyse.debtslayer.utils.AchievementTier
import com.hyse.debtslayer.utils.StreakData
import com.hyse.debtslayer.viewmodel.DebtViewModel

@Composable
fun AchievementScreen(viewModel: DebtViewModel) {
    val achievements by viewModel.achievements.collectAsState()
    val streakData   by viewModel.streakData.collectAsState()

    val unlocked = achievements.count { it.isUnlocked }
    val total    = achievements.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Streak Hero Card ─────────────────────────────────────────────
        StreakHeroCard(streakData = streakData)

        // ── Progress Summary ─────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "$unlocked / $total Achievement",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Terus konsisten untuk unlock semua!",
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Box(
                    modifier         = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress  = { if (total > 0) unlocked.toFloat() / total else 0f },
                        modifier  = Modifier.fillMaxSize(),
                        color     = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        strokeWidth = 5.dp
                    )
                    Text(
                        "${if (total > 0) (unlocked * 100 / total) else 0}%",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Grid Achievement ─────────────────────────────────────────────
        AchievementTier.entries.forEach { tier ->
            val tierAchs = achievements.filter { it.tier == tier }
            if (tierAchs.isEmpty()) return@forEach

            Text(
                text       = "${tier.emoji()} ${tier.label()}",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = tier.color(),
                modifier   = Modifier.padding(top = 4.dp)
            )

            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                modifier            = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 1000.dp), // bounded height untuk LazyGrid di scroll
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                userScrollEnabled     = false
            ) {
                items(tierAchs) { ach ->
                    AchievementCard(achievement = ach)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Streak Hero Card ─────────────────────────────────────────────────────────
@Composable
private fun StreakHeroCard(streakData: StreakData) {
    val infiniteTransition = rememberInfiniteTransition(label = "fire")
    val fireScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fireScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (streakData.currentStreak > 0)
                Color(0xFFFF6B35).copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (streakData.currentStreak > 0)
            BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.3f))
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Fire emoji animasi
            Box(
                modifier         = Modifier
                    .size(64.dp)
                    .scale(if (streakData.currentStreak > 0) fireScale else 1f)
                    .clip(CircleShape)
                    .background(
                        if (streakData.currentStreak > 0) Color(0xFFFF6B35).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (streakData.currentStreak > 0) "🔥" else "💤",
                    fontSize = 28.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${streakData.currentStreak} Hari Streak",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (streakData.currentStreak > 0) Color(0xFFFF6B35)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        streakData.currentStreak == 0 -> "Mulai setor hari ini untuk streak!"
                        streakData.currentStreak < 3  -> "Teruskan! Masih ${3 - streakData.currentStreak} hari ke badge pertama"
                        streakData.currentStreak < 7  -> "Luar biasa! ${7 - streakData.currentStreak} hari lagi ke streak 7"
                        streakData.currentStreak < 14 -> "Keren! ${14 - streakData.currentStreak} hari lagi ke streak 14"
                        streakData.currentStreak < 30 -> "Hampir legenda! ${30 - streakData.currentStreak} hari lagi"
                        else                          -> "LEGENDA! Streak 30+ hari! 👑"
                    },
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Terpanjang: ${streakData.longestStreak} hari",
                    fontSize   = 11.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Streak dots (5 hari terakhir)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("5 Hari", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) { i ->
                        val isActive = i < streakData.currentStreak.coerceAtMost(5)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) Color(0xFFFF6B35)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Achievement Card ─────────────────────────────────────────────────────────
@Composable
private fun AchievementCard(achievement: Achievement) {
    val tierColor = achievement.tier.color()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked)
                tierColor.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (achievement.isUnlocked)
            BorderStroke(1.dp, tierColor.copy(alpha = 0.3f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Emoji badge
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) tierColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (achievement.isUnlocked) achievement.emoji else "🔒",
                    fontSize = 22.sp
                )
            }

            Text(
                achievement.title,
                fontSize    = 12.sp,
                fontWeight  = FontWeight.Bold,
                textAlign   = TextAlign.Center,
                color       = if (achievement.isUnlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines    = 2
            )

            Text(
                achievement.description,
                fontSize  = 10.sp,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (achievement.isUnlocked) 0.8f else 0.4f
                ),
                maxLines  = 2
            )

            // Progress bar (hanya kalau belum unlock)
            if (!achievement.isUnlocked && achievement.progress > 0f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    LinearProgressIndicator(
                        progress   = { achievement.progress },
                        modifier   = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color      = tierColor,
                        trackColor = tierColor.copy(alpha = 0.15f)
                    )
                    Text(
                        achievement.progressLabel,
                        fontSize = 9.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (achievement.isUnlocked) {
                Text(
                    "✓ Unlocked",
                    fontSize   = 9.sp,
                    color      = tierColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Unlock Dialog ────────────────────────────────────────────────────────────
@Composable
fun AchievementUnlockDialog(achievement: Achievement, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Play SFX saat dialog muncul
    LaunchedEffect(Unit) {
        try {
            val mp = android.media.MediaPlayer.create(context, com.hyse.debtslayer.R.raw.sfx_achievement)
            mp?.apply {
                setVolume(0.8f, 0.8f)
                start()
                setOnCompletionListener { release() }
            }
        } catch (e: Exception) {
            android.util.Log.e("AchievementDialog", "SFX error: ${e.message}")
        }
    }

    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "scale"
    )

    val maiImage = getMaiImageForTier(achievement.tier)

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .scale(scale)
        ) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Mai image banner ─────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                achievement.tier.color().copy(alpha = 0.08f),
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(maiImage),
                            contentDescription = "Mai",
                            modifier = Modifier
                                .height(190.dp)
                                .aspectRatio(400f / 600f),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        // Tier badge di pojok kanan atas
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(achievement.tier.color().copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "${achievement.tier.emoji()} ${achievement.tier.label()}",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = achievement.tier.color()
                            )
                        }
                    }

                    // ── Content ──────────────────────────────────────────
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "🎉 Achievement Unlocked!",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(achievement.emoji, fontSize = 24.sp)
                            Text(
                                achievement.title,
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign  = TextAlign.Center,
                                color      = achievement.tier.color()
                            )
                        }

                        Text(
                            achievement.description,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Mai reaction text
                        Text(
                            getMaiReactionForAchievement(achievement),
                            fontSize  = 12.sp,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurface,
                            modifier  = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    achievement.tier.color().copy(alpha = 0.08f)
                                )
                                .padding(12.dp)
                        )

                        Button(
                            onClick  = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = achievement.tier.color()
                            )
                        ) {
                            Text("Keren!", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun getMaiImageForTier(tier: AchievementTier): Int {
    return when (tier) {
        AchievementTier.BRONZE   -> com.hyse.debtslayer.R.drawable.mai_surprised_1
        AchievementTier.SILVER   -> com.hyse.debtslayer.R.drawable.mai_surprised_1
        AchievementTier.GOLD     -> com.hyse.debtslayer.R.drawable.mai_surprised
        AchievementTier.PLATINUM -> com.hyse.debtslayer.R.drawable.mai_proud
    }
}

private fun getMaiReactionForAchievement(ach: Achievement): String {
    return when (ach.id) {
        com.hyse.debtslayer.utils.AchievementId.FIRST_DEPOSIT ->
            "\"Hmph. Akhirnya kamu mulai juga. Jangan berhenti di sini.\""
        com.hyse.debtslayer.utils.AchievementId.STREAK_3 ->
            "\"3 hari berturut-turut. Lumayan. Tapi jangan besar kepala.\""
        com.hyse.debtslayer.utils.AchievementId.STREAK_7 ->
            "\"Seminggu penuh? Aku... sedikit terkesan. Sedikit.\""
        com.hyse.debtslayer.utils.AchievementId.STREAK_14 ->
            "\"Dua minggu. Aku tidak mau bilang aku bangga, tapi... ya.\""
        com.hyse.debtslayer.utils.AchievementId.STREAK_30 ->
            "\"...Kamu benar-benar melakukannya. Sebulan penuh. Tidak kusangka.\""
        com.hyse.debtslayer.utils.AchievementId.PROGRESS_25 ->
            "\"25% lunas. Seperempat jalan. Masih jauh, tapi lebih baik dari nol.\""
        com.hyse.debtslayer.utils.AchievementId.PROGRESS_50 ->
            "\"Setengah lunas. Hmm. Aku mulai percaya kamu serius.\""
        com.hyse.debtslayer.utils.AchievementId.PROGRESS_75 ->
            "\"75%! Hampir selesai. Jangan berhenti sekarang!\""
        com.hyse.debtslayer.utils.AchievementId.PROGRESS_100 ->
            "\"Lunas. Selesai. Aku tidak akan bilang aku bangga... tapi aku bangga. Jangan bikin hutang baru.\""
        com.hyse.debtslayer.utils.AchievementId.SPEED_SLAYER ->
            "\"2x target dalam sehari? Tidak kusangka kamu bisa. Impressive.\""
        com.hyse.debtslayer.utils.AchievementId.CONSISTENT_5 ->
            "\"5 hari konsisten. Ini yang aku mau lihat dari awal.\""
        com.hyse.debtslayer.utils.AchievementId.EARLY_BIRD ->
            "\"Setor pagi-pagi? Kamu memang tidak bisa diprediksi.\""
        com.hyse.debtslayer.utils.AchievementId.NIGHT_OWL ->
            "\"Tengah malam masih setor? Tidur dulu sana, sudah cukup.\""
    }
}

// ── Extension helpers ────────────────────────────────────────────────────────
private fun AchievementTier.color() = when (this) {
    AchievementTier.BRONZE   -> Color(0xFFCD7F32)
    AchievementTier.SILVER   -> Color(0xFF9E9E9E)
    AchievementTier.GOLD     -> Color(0xFFFFB300)
    AchievementTier.PLATINUM -> Color(0xFF7C4DFF)
}

private fun AchievementTier.label() = when (this) {
    AchievementTier.BRONZE   -> "Bronze"
    AchievementTier.SILVER   -> "Silver"
    AchievementTier.GOLD     -> "Gold"
    AchievementTier.PLATINUM -> "Platinum ✦"
}

private fun AchievementTier.emoji() = when (this) {
    AchievementTier.BRONZE   -> "🥉"
    AchievementTier.SILVER   -> "🥈"
    AchievementTier.GOLD     -> "🥇"
    AchievementTier.PLATINUM -> "💜"
}