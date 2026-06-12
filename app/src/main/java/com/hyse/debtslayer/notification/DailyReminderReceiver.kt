package com.hyse.debtslayer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hyse.debtslayer.MainActivity
import com.hyse.debtslayer.R
import com.hyse.debtslayer.data.database.DebtDatabase
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
import com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode
import com.hyse.debtslayer.utils.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DailyReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID      = "debt_slayer_reminder_v2"
        const val NOTIFICATION_ID = 1001
        const val NOTIF_ID_STREAK = 1002
        private const val TAG = "DailyReminder"

        fun getCustomSoundUri(context: Context): Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.custom_notification}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifType = intent.getStringExtra("type") ?: "daily"
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db           = DebtDatabase.getDatabase(context)
                val prefsRepo    = UserPreferencesRepository(context)
                val transactions = db.transactionDao().getAllTransactions().first()

                val modeStr     = prefsRepo.personalityMode.firstOrNull() ?: "BALANCED"
                val mode        = try { PersonalityMode.valueOf(modeStr) } catch (e: Exception) { PersonalityMode.BALANCED }
                val totalDebt   = prefsRepo.customTotalDebt.firstOrNull() ?: 0L
                val deadlineStr = prefsRepo.customDeadline.firstOrNull() ?: ""
                val totalPaid   = transactions.sumOf { it.amount }
                val remaining   = (totalDebt - totalPaid).coerceAtLeast(0L)

                if (remaining <= 0L) {
                    Log.d(TAG, "Hutang lunas - notifikasi dinonaktifkan")
                    DailyReminderScheduler.cancel(context)
                    return@launch
                }

                if (deadlineStr.isBlank()) {
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }

                val df          = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today       = Calendar.getInstance()
                val todayStr    = df.format(Date())
                val deadline    = df.parse(deadlineStr) ?: run {
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }

                val deadlineCal = Calendar.getInstance().apply { time = deadline }
                val daysLeft    = (deadlineCal.timeInMillis - today.timeInMillis) / (1000L * 60 * 60 * 24)
                val fmt         = NumberFormat.getNumberInstance(Locale("id", "ID"))

                val dailyTarget = if (daysLeft > 0)
                    CurrencyFormatter.ceilToThousand((remaining + daysLeft - 1) / daysLeft)
                else remaining

                val todayDeposit = transactions
                    .filter { tx -> df.format(Date(tx.date)) == todayStr }
                    .sumOf { it.amount }

                val missDays = countConsecutiveMissDays(transactions, df)

                when (notifType) {
                    "streak_warning" -> handleStreakWarning(
                        context, todayDeposit, dailyTarget, missDays, daysLeft, remaining, mode, fmt
                    )
                    else -> handleDailyReminder(
                        context, todayDeposit, dailyTarget, remaining, daysLeft, missDays, mode, fmt
                    )
                }

                DailyReminderScheduler.schedule(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun countConsecutiveMissDays(
        transactions: List<com.hyse.debtslayer.data.entity.Transaction>,
        df: SimpleDateFormat
    ): Int {
        var count = 0
        val depositDays = transactions.map { df.format(Date(it.date)) }.toSet()
        for (daysAgo in 1..30) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -daysAgo) }
            if (depositDays.contains(df.format(cal.time))) break
            count++
        }
        return count
    }

    private fun handleStreakWarning(
        context: Context,
        todayDeposit: Long,
        dailyTarget: Long,
        missDays: Int,
        daysLeft: Long,
        remaining: Long,
        mode: PersonalityMode,
        fmt: NumberFormat
    ) {
        if (todayDeposit >= dailyTarget) {
            Log.d(TAG, "Streak warning skipped - target already met")
            return
        }

        val targetStr   = "Rp ${fmt.format(dailyTarget)}"
        val shortfall   = (dailyTarget - todayDeposit).coerceAtLeast(0L)
        val shortfallStr = "Rp ${fmt.format(shortfall)}"

        val (title, body) = when {
            missDays >= 3 -> "Streak PUTUS $missDays hari!" to when (mode) {
                PersonalityMode.STRICT   -> "Sudah $missDays hari tidak setor. Ini serius. Setor $targetStr sekarang sebelum malam."
                PersonalityMode.BALANCED -> "Streak sudah putus $missDays hari. Masih bisa balik, setor $targetStr malam ini!"
                PersonalityMode.GENTLE   -> "Hai, sudah $missDays hari nih. Aku khawatir. Setor $targetStr ya, masih ada waktu!"
            }
            todayDeposit == 0L -> "Jam 20:00 - Belum setor!" to when (mode) {
                PersonalityMode.STRICT   -> "Hampir malam. Belum setor $targetStr. Apa yang kamu tunggu?"
                PersonalityMode.BALANCED -> "Sudah malam, belum setor $targetStr hari ini. Jangan sampai lupa ya!"
                PersonalityMode.GENTLE   -> "Psst, sudah jam 8 malam. Belum setor hari ini lho. $targetStr masih bisa!"
            }
            else -> "Kurang $shortfallStr lagi!" to when (mode) {
                PersonalityMode.STRICT   -> "Tinggal $shortfallStr dari target $targetStr. Selesaikan sebelum tengah malam."
                PersonalityMode.BALANCED -> "Hampir target! Masih kurang $shortfallStr. Ayo selesaikan malam ini!"
                PersonalityMode.GENTLE   -> "Tinggal $shortfallStr lagi buat capai target! Kamu bisa!"
            }
        }

        showNotification(context, title, body, NOTIF_ID_STREAK)
    }

    private fun handleDailyReminder(
        context: Context,
        todayDeposit: Long,
        dailyTarget: Long,
        remaining: Long,
        daysLeft: Long,
        missDays: Int,
        mode: PersonalityMode,
        fmt: NumberFormat
    ) {
        val remainingStr = "Rp ${fmt.format(remaining)}"
        val targetStr    = "Rp ${fmt.format(dailyTarget)}"
        val depositStr   = "Rp ${fmt.format(todayDeposit)}"

        val (title, body) = when {
            daysLeft < 0 -> {
                val daysPassed = (-daysLeft).toInt()
                "DEADLINE LEWAT $daysPassed hari!" to getEscalatedMissText(daysPassed, remaining, targetStr, mode, fmt)
            }
            daysLeft == 0L ->
                "HARI INI DEADLINE!" to "Sisa hutang $remainingStr harus lunas hari ini. Tidak ada besok lagi!"

            daysLeft <= 3L && todayDeposit < dailyTarget ->
                "KRITIS! Tinggal $daysLeft hari!" to "Sisa $remainingStr. Target hari ini $targetStr. Jangan tunda!"

            daysLeft <= 7L && todayDeposit == 0L ->
                "URGENT! Tinggal $daysLeft hari!" to "Target hari ini $targetStr. Belum setor sama sekali!"

            missDays >= 7 ->
                "$missDays HARI TIDAK SETOR!" to getEscalatedMissText(missDays, remaining, targetStr, mode, fmt)

            missDays >= 5 ->
                "5+ Hari Absen Setor" to getEscalatedMissText(missDays, remaining, targetStr, mode, fmt)

            missDays >= 3 ->
                "3 Hari Tidak Setor!" to getEscalatedMissText(missDays, remaining, targetStr, mode, fmt)

            missDays >= 2 ->
                getTwoDaysMissingNotification(dailyTarget, daysLeft, fmt, mode)

            todayDeposit >= dailyTarget * 2 -> "LUAR BIASA hari ini!" to when (mode) {
                PersonalityMode.STRICT   -> "Setor $depositStr hari ini. 2x target. Hmm, tidak kusangka."
                PersonalityMode.BALANCED -> "Setor $depositStr hari ini! 2x target, kamu serius. Bagus."
                PersonalityMode.GENTLE   -> "WOW! $depositStr hari ini! Aku bangga sama kamu!"
            }

            todayDeposit >= dailyTarget -> "Target tercapai!" to when (mode) {
                PersonalityMode.STRICT   -> "Setor $depositStr. Target terpenuhi. Besok harus sama."
                PersonalityMode.BALANCED -> "Kamu sudah setor $depositStr hari ini. Target tercapai. Pertahankan!"
                PersonalityMode.GENTLE   -> "Yeay! $depositStr hari ini, target tercapai! Kamu hebat!"
            }

            todayDeposit > 0 -> {
                val shortfall = dailyTarget - todayDeposit
                "Kurang Rp ${fmt.format(shortfall)} lagi" to "Baru $depositStr dari target $targetStr. Ayo selesaikan!"
            }

            else ->
                "Reminder dari Mai" to getMaiReminderText(dailyTarget, daysLeft, fmt, mode)
        }

        showNotification(context, title, body, NOTIFICATION_ID)
    }

    private fun getEscalatedMissText(
        missDays: Int,
        remaining: Long,
        targetStr: String,
        mode: PersonalityMode,
        fmt: NumberFormat
    ): String {
        val remainingStr = "Rp ${fmt.format(remaining)}"
        return when {
            missDays >= 7 -> when (mode) {
                PersonalityMode.STRICT   -> "SEMINGGU tidak setor. Sisa $remainingStr. Ini bukan main-main. Setor $targetStr SEKARANG."
                PersonalityMode.BALANCED -> "Sudah seminggu tidak ada setoran. Sisa $remainingStr. Yuk balik ke jalur, setor $targetStr hari ini!"
                PersonalityMode.GENTLE   -> "Hai, sudah seminggu nih. Aku khawatir. Sisa $remainingStr. Setor $targetStr ya, pelan-pelan tidak apa."
            }
            missDays >= 5 -> when (mode) {
                PersonalityMode.STRICT   -> "5 hari tidak setor. Kamu serius mau lunasin hutang ini? Setor $targetStr sekarang."
                PersonalityMode.BALANCED -> "$missDays hari tidak ada setoran. Sisa $remainingStr. Ayo balik lagi!"
                PersonalityMode.GENTLE   -> "Sudah $missDays hari tidak setor. Tidak apa-apa, tapi yuk mulai lagi. $targetStr hari ini bisa!"
            }
            missDays >= 3 -> when (mode) {
                PersonalityMode.STRICT   -> "$missDays hari tidak setor. Hutangmu tidak ikut libur. Target hari ini $targetStr."
                PersonalityMode.BALANCED -> "Sudah $missDays hari tidak setor nih. Ayo balik ke jalur! Target $targetStr."
                PersonalityMode.GENTLE   -> "Sudah $missDays hari istirahat, sekarang saatnya balik setor $targetStr. Semangat!"
            }
            else -> when (mode) {
                PersonalityMode.STRICT   -> "Deadline sudah lewat $missDays hari. $remainingStr harus dibayar. Sekarang."
                PersonalityMode.BALANCED -> "Deadline sudah lewat $missDays hari. Sisa $remainingStr. Segera selesaikan!"
                PersonalityMode.GENTLE   -> "Deadline lewat $missDays hari, tapi masih bisa! $remainingStr, semangat!"
            }
        }
    }

    private fun getTwoDaysMissingNotification(
        target: Long,
        daysLeft: Long,
        fmt: NumberFormat,
        mode: PersonalityMode
    ): Pair<String, String> {
        val targetStr = "Rp ${fmt.format(target)}"
        val title = "2 Hari Tidak Setor!"
        val body = when (mode) {
            PersonalityMode.STRICT -> listOf(
                "Dua hari tidak setor. Ini bukan liburan. Setor $targetStr sekarang.",
                "Kemarin tidak setor, hari ini juga belum. Hutangmu tidak ikut libur. $targetStr."
            ).random()
            PersonalityMode.BALANCED -> listOf(
                "Dua hari tidak ada setoran nih. Ayo balik ke jalur! Target hari ini $targetStr.",
                "Sudah 2 hari tidak setor. Tinggal $daysLeft hari lagi, jangan ketinggalan terus."
            ).random()
            PersonalityMode.GENTLE -> listOf(
                "Hai, sudah 2 hari tidak setor. Tidak apa-apa, tapi yuk mulai lagi! $targetStr",
                "2 hari istirahat cukup ya. Sekarang $targetStr hari ini bisa kan?"
            ).random()
        }
        return title to body
    }

    private fun getMaiReminderText(
        target: Long,
        daysLeft: Long,
        fmt: NumberFormat,
        mode: PersonalityMode
    ): String {
        val targetStr = "Rp ${fmt.format(target)}"
        return when (mode) {
            PersonalityMode.STRICT -> listOf(
                "Target hari ini $targetStr. Jangan ditunda.",
                "Hutang tidak lunas sendiri. Setor $targetStr sekarang.",
                "$daysLeft hari lagi deadline. Target $targetStr, jangan kasih alasan."
            ).random()
            PersonalityMode.BALANCED -> listOf(
                "Jangan lupa target hari ini $targetStr ya.",
                "Target $targetStr. Kamu pasti bisa!",
                "Tinggal $daysLeft hari lagi. Target hari ini $targetStr."
            ).random()
            PersonalityMode.GENTLE -> listOf(
                "Hai! Target hari ini $targetStr. Semangat ya!",
                "Reminder lembut: Jangan lupa setor $targetStr hari ini.",
                "Aku percaya kamu bisa! Target hari ini $targetStr."
            ).random()
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        body: String,
        notifId: Int = NOTIFICATION_ID
    ) {
        val manager  = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = getCustomSoundUri(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null && existing.sound != soundUri) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID, "Reminder Hutang", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi harian dari Mai"
                setSound(soundUri, audioAttrs)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
        Log.d(TAG, "Notif[$notifId]: $title")
    }
}