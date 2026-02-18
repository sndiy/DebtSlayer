package com.hyse.debtslayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.hyse.debtslayer.R
import com.hyse.debtslayer.data.database.DebtDatabase
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
import com.hyse.debtslayer.utils.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DebtWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // ✅ FIX: goAsync() mencegah AppWidgetProvider di-kill sebelum coroutine selesai
        // AppWidgetProvider extends BroadcastReceiver sehingga goAsync() tersedia
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { widgetId ->
                    updateSingleWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                android.util.Log.e("DebtWidget", "Error: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val db = DebtDatabase.getDatabase(context)
        val transactions = db.transactionDao().getAllTransactions().first()
        val prefsRepo = UserPreferencesRepository(context)

        val totalDebt = prefsRepo.customTotalDebt.firstOrNull() ?: 12_445_000L
        val deadlineStr = prefsRepo.customDeadline.firstOrNull() ?: "2026-08-17"
        val totalPaid = transactions.sumOf { tx -> tx.amount }
        val remaining = (totalDebt - totalPaid).coerceAtLeast(0L)

        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance()
        val deadline = df.parse(deadlineStr) ?: return
        val deadlineCal = Calendar.getInstance().apply { time = deadline }
        val daysLeft = ((deadlineCal.timeInMillis - today.timeInMillis) / (1000L * 60 * 60 * 24))
            .coerceAtLeast(1L)

        // ✅ FIX: pakai CurrencyFormatter.ceilToThousand, hapus duplikat lokal
        val dailyTarget = CurrencyFormatter.ceilToThousand(remaining / daysLeft)

        val todayStr = df.format(Date())
        val todayDeposit = transactions.filter { tx ->
            df.format(Date(tx.date)) == todayStr
        }.sumOf { tx -> tx.amount }

        val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
        val progress = if (totalDebt > 0) ((totalPaid.toFloat() / totalDebt) * 100).toInt() else 0

        val statusText = when {
            todayDeposit >= dailyTarget && todayDeposit > 0 -> "✅ Target tercapai!"
            todayDeposit > 0 -> "⚡ Kurang Rp ${fmt.format(dailyTarget - todayDeposit)}"
            else -> "⏳ Belum setor hari ini"
        }

        val views = RemoteViews(context.packageName, R.layout.widget_debt)
        views.setTextViewText(R.id.widget_progress, "$progress% Lunas")
        views.setTextViewText(R.id.widget_remaining, "Sisa: Rp ${fmt.format(remaining)}")
        views.setTextViewText(R.id.widget_target, "Target: Rp ${fmt.format(dailyTarget)}/hari")
        views.setTextViewText(R.id.widget_status, statusText)
        views.setTextViewText(R.id.widget_days, "$daysLeft hari lagi")
        views.setProgressBar(R.id.widget_progressbar, 100, progress, false)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}