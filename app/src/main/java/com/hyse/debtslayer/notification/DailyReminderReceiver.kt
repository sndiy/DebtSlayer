package com.hyse.debtslayer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hyse.debtslayer.MainActivity
import com.hyse.debtslayer.R
import com.hyse.debtslayer.data.database.DebtDatabase
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
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
        const val CHANNEL_ID = "debt_slayer_reminder"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "DailyReminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ✅ FIX: goAsync() mencegah BroadcastReceiver di-kill sebelum coroutine selesai
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DebtDatabase.getDatabase(context)
                val transactions = db.transactionDao().getAllTransactions().first()
                val prefsRepo = UserPreferencesRepository(context)

                val personalityModeStr = prefsRepo.personalityMode.firstOrNull() ?: "BALANCED"
                val mode = com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.valueOf(personalityModeStr)

                val totalDebt = prefsRepo.customTotalDebt.firstOrNull() ?: 0L
                val deadlineStr = prefsRepo.customDeadline.firstOrNull() ?: ""
                val totalPaid = transactions.sumOf { it.amount }
                val remaining = (totalDebt - totalPaid).coerceAtLeast(0L)

                if (remaining <= 0L) {
                    Log.d(TAG, "✅ Hutang lunas — notifikasi dinonaktifkan")
                    DailyReminderScheduler.cancel(context)
                    return@launch
                }

                if (deadlineStr.isBlank()) {
                    Log.d(TAG, "⚠️ Deadline belum diset — skip notifikasi")
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }

                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = Calendar.getInstance()
                val deadline = df.parse(deadlineStr) ?: run {
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }
                val deadlineCal = Calendar.getInstance().apply { time = deadline }
                val daysLeft = (deadlineCal.timeInMillis - today.timeInMillis) / (1000L * 60 * 60 * 24)

                val numFormatter = NumberFormat.getNumberInstance(Locale("id", "ID"))
                val remainingStr = "Rp ${numFormatter.format(remaining)}"

                if (daysLeft < 0) {
                    val daysPassed = -daysLeft
                    val (title, body) = getDeadlinePassedNotification(remaining, daysPassed, numFormatter, mode)
                    showNotification(context, title, body)
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }

                if (daysLeft == 0L) {
                    showNotification(
                        context,
                        "🚨 HARI INI DEADLINE!",
                        "Sisa hutang $remainingStr harus lunas hari ini. Tidak ada besok lagi!"
                    )
                    DailyReminderScheduler.schedule(context)
                    return@launch
                }

                // ✅ FIX: pakai CurrencyFormatter.ceilToThousand, hapus duplikat
                val dailyTarget = CurrencyFormatter.ceilToThousand((remaining + daysLeft - 1) / daysLeft)

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayDeposit = transactions.filter { tx ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(tx.date)) == todayStr
                }.sumOf { tx -> tx.amount }

                val targetStr = "Rp ${numFormatter.format(dailyTarget)}"
                val depositStr = "Rp ${numFormatter.format(todayDeposit)}"

                val (title, body) = when {
                    daysLeft <= 3L && todayDeposit < dailyTarget ->
                        "🚨 KRITIS! Tinggal $daysLeft hari!" to
                                "Sisa hutang $remainingStr. Target hari ini $targetStr. Jangan tunda!"

                    daysLeft <= 7L && todayDeposit == 0L ->
                        "⚠️ URGENT! Mai di sini." to
                                "Tinggal $daysLeft hari! Target hari ini $targetStr. Belum setor sama sekali!"

                    todayDeposit == 0L ->
                        "📢 Reminder dari Mai" to
                                getMaiReminderText(dailyTarget, daysLeft, numFormatter, mode)

                    todayDeposit >= dailyTarget ->
                        "✅ Target hari ini tercapai!" to
                                "Kamu sudah setor $depositStr. Lumayan. Sisanya masih Rp ${numFormatter.format(remaining - todayDeposit)}."

                    else -> {
                        val shortfall = dailyTarget - todayDeposit
                        "⚡ Kurang Rp ${numFormatter.format(shortfall)} lagi" to
                                "Baru $depositStr dari target $targetStr. Ayo selesaikan!"
                    }
                }

                showNotification(context, title, body)
                DailyReminderScheduler.schedule(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            } finally {
                // ✅ FIX: wajib dipanggil agar sistem tahu BroadcastReceiver selesai
                pendingResult.finish()
            }
        }
    }

    private fun getDeadlinePassedNotification(
        remaining: Long,
        daysPassed: Long,
        fmt: NumberFormat,
        mode: com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode
    ): Pair<String, String> {
        val remainingStr = "Rp ${fmt.format(remaining)}"
        val title = "🚨 DEADLINE SUDAH LEWAT $daysPassed hari!"
        val body = when (mode) {
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.STRICT -> listOf(
                "Deadline sudah lewat $daysPassed hari. Masih ada $remainingStr yang belum dibayar. Bayar sekarang.",
                "$daysPassed hari terlambat. Hutang $remainingStr tidak menghilang. Selesaikan.",
                "Sudah $daysPassed hari melewati deadline. $remainingStr masih menunggumu. Tidak ada alasan lagi."
            ).random()
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.BALANCED -> listOf(
                "Deadline sudah lewat $daysPassed hari. Sisa $remainingStr harus segera diselesaikan.",
                "Sudah $daysPassed hari dari deadline. Masih ada $remainingStr. Yuk segera lunasin!",
                "$daysPassed hari terlambat. $remainingStr masih tersisa — ayo segera diselesaikan."
            ).random()
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.GENTLE -> listOf(
                "Deadline sudah lewat $daysPassed hari. Masih ada $remainingStr. Semangat, selesaikan ya! 💪",
                "Sudah $daysPassed hari dari deadline. Sisa $remainingStr — kamu pasti bisa selesaikan! 😊",
                "$daysPassed hari terlambat, tapi tidak apa-apa. Selesaikan $remainingStr sekarang ya! ✨"
            ).random()
        }
        return title to body
    }

    private fun getMaiReminderText(
        target: Long,
        daysLeft: Long,
        fmt: NumberFormat,
        mode: com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode
    ): String {
        val targetStr = "Rp ${fmt.format(target)}"
        return when (mode) {
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.STRICT -> listOf(
                "Target hari ini $targetStr. Jangan ditunda lagi.",
                "Belum setor? Target $targetStr. Aku tunggu.",
                "Hutang tidak lunas sendiri. Setor $targetStr sekarang.",
                "Tinggal $daysLeft hari. Target hari ini $targetStr. Ayo.",
                "$daysLeft hari lagi deadline. Target $targetStr — jangan kasih alasan."
            ).random()
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.BALANCED -> listOf(
                "Hai! Jangan lupa target hari ini $targetStr ya.",
                "Reminder: Target $targetStr. Semangat!",
                "Target hari ini $targetStr. Kamu pasti bisa!",
                "Tinggal $daysLeft hari lagi. Target hari ini $targetStr.",
                "Ayo setor $targetStr hari ini!"
            ).random()
            com.hyse.debtslayer.personality.AdaptiveMaiPersonality.PersonalityMode.GENTLE -> listOf(
                "Hai! Target hari ini $targetStr. Semangat ya! 😊",
                "Reminder lembut: Jangan lupa setor $targetStr hari ini.",
                "Kamu pasti bisa! Target hari ini $targetStr. 💪",
                "Aku percaya kamu bisa selesaikan target $targetStr hari ini!",
                "Pelan-pelan saja, target hari ini $targetStr. Kamu hebat! ✨"
            ).random()
        }
    }

    private fun showNotification(context: Context, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminder Hutang",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi harian dari Mai untuk mengingatkan target setoran"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}