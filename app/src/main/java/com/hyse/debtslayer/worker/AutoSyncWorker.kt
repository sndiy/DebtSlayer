package com.hyse.debtslayer.worker

import android.content.Context
import androidx.work.*
import com.hyse.debtslayer.data.auth.AuthRepository
import com.hyse.debtslayer.data.database.DebtDatabase          // ✅ fix
import com.hyse.debtslayer.data.preferences.SyncFrequency
import com.hyse.debtslayer.data.preferences.SyncPreferences
import com.hyse.debtslayer.data.repository.TransactionRepository
import com.hyse.debtslayer.data.sync.CloudSyncRepository
import java.util.concurrent.TimeUnit

class AutoSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authRepository = AuthRepository()
        if (!authRepository.isLoggedIn) return Result.success()

        return try {
            val db = DebtDatabase.getDatabase(applicationContext)  // ✅ fix
            val transactionRepository = TransactionRepository(db.transactionDao())
            val syncRepo = CloudSyncRepository(authRepository, transactionRepository)
            val syncPrefs = SyncPreferences(applicationContext)

            syncRepo.uploadAll()
            syncPrefs.updateLastSync()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_cloud_sync"

        fun schedule(context: Context, frequency: SyncFrequency) {
            val workManager = WorkManager.getInstance(context)

            if (frequency == SyncFrequency.MANUAL) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(
                frequency.intervalMs, TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}