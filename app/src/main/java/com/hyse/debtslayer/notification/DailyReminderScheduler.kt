package com.hyse.debtslayer.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

object DailyReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val REQUEST_CODE = 2001

    // ✅ FIX: suspend fun + caller harus dari coroutine, atau pakai overload non-suspend
    // Jadikan private supaya caller selalu pakai versi non-blocking di bawah
    private fun scheduleAt(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            }
            Log.d(TAG, "✅ Reminder at ${hour}:${minute.toString().padStart(2, '0')} → ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed: ${e.message}")
        }
    }

    // ✅ FIX: ganti runBlocking dengan CoroutineScope(IO) — tidak memblokir UI thread
    fun schedule(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefsRepo = UserPreferencesRepository(context)
            val hour   = prefsRepo.reminderHour.firstOrNull() ?: 19
            val minute = prefsRepo.reminderMinute.firstOrNull() ?: 0
            scheduleAt(context, hour, minute)
            // Streak warning selalu jam 20:00, hanya kalau reminder utama sebelum jam 20
            if (hour < 20) scheduleStreakWarning(context)
        }
    }

    private fun scheduleStreakWarning(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            putExtra("type", "streak_warning")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms())
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                else
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            Log.d(TAG, "✅ Streak warning scheduled at 20:00")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Streak warning failed: ${e.message}")
        }
    }

    fun sendTestNotification(context: Context) {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            putExtra("is_test", true)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "🔔 Test notification sent")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "🔕 Cancelled")
    }
}