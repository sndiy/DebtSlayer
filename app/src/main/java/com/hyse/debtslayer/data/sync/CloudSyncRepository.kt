package com.hyse.debtslayer.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hyse.debtslayer.data.auth.AuthRepository
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.data.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val uid get() = authRepository.currentUid
        ?: throw IllegalStateException("User belum login")

    private fun Transaction.checksum(): String =
        "${id}_${amount}_${source}_${date}".hashCode().toString()

    // ── Upload SEMUA — transaksi + semua preferences ──────────────
    suspend fun uploadAll(
        totalDebt: Long? = null,
        deadline: String? = null,
        personalityMode: String? = null,
        reminderHour: Int? = null,
        reminderMinute: Int? = null,
        setupDate: String? = null
    ): SyncResult {
        val transactions = transactionRepository.allTransactions.firstOrNull()
            ?: return SyncResult(0, 0)

        // ── 1. Upload preferences ─────────────────────────────────
        val prefsMap = mutableMapOf<String, Any>()
        if (totalDebt != null && totalDebt > 0) prefsMap["totalDebt"]       = totalDebt
        if (!deadline.isNullOrBlank())          prefsMap["deadline"]        = deadline
        if (!personalityMode.isNullOrBlank())   prefsMap["personalityMode"] = personalityMode
        if (reminderHour != null)               prefsMap["reminderHour"]    = reminderHour
        if (reminderMinute != null)             prefsMap["reminderMinute"]  = reminderMinute
        if (!setupDate.isNullOrBlank())         prefsMap["setupDate"]       = setupDate

        val prefsUploaded = prefsMap.isNotEmpty()
        if (prefsUploaded) {
            prefsMap["updatedAt"] = System.currentTimeMillis()
            firestore.collection("users").document(uid)
                .set(prefsMap, SetOptions.merge()).await()
        }

        // ── 2. Upload transaksi yang berubah saja ─────────────────
        val metaRef = firestore
            .collection("users").document(uid)
            .collection("_meta").document("checksums")

        val metaDoc = try { metaRef.get().await() } catch (e: Exception) { null }
        val cloudChecksums = metaDoc?.data?.mapValues { it.value.toString() } ?: emptyMap()

        val changed = transactions.filter { tx ->
            tx.checksum() != cloudChecksums[tx.id.toString()]
        }

        if (changed.isEmpty()) return SyncResult(
            total         = transactions.size,
            uploaded      = 0,
            prefsUploaded = prefsUploaded
        )

        val batch = firestore.batch()
        val newChecksums = mutableMapOf<String, String>()

        changed.forEach { tx ->
            val ref = firestore
                .collection("users").document(uid)
                .collection("transactions").document(tx.id.toString())
            batch.set(ref, mapOf(
                "id"     to tx.id,
                "amount" to tx.amount,
                "source" to tx.source,
                "date"   to tx.date
            ), SetOptions.merge())
            newChecksums[tx.id.toString()] = tx.checksum()
        }

        batch.commit().await()
        metaRef.set(newChecksums, SetOptions.merge()).await()

        return SyncResult(
            total         = transactions.size,
            uploaded      = changed.size,
            prefsUploaded = prefsUploaded
        )
    }

    // ── Remove checksum saat transaksi dihapus ────────────────────
    suspend fun removeChecksum(transactionId: Long) {
        try {
            val metaRef = firestore
                .collection("users").document(uid)
                .collection("_meta").document("checksums")
            metaRef.update(
                transactionId.toString(),
                com.google.firebase.firestore.FieldValue.delete()
            ).await()
        } catch (e: Exception) { /* abaikan */ }
    }

    // ── Download transaksi + semua preferences ────────────────────
    suspend fun downloadAll(): DownloadResult {
        // ── 1. Download preferences ───────────────────────────────
        val prefsDoc = try {
            firestore.collection("users").document(uid).get().await()
        } catch (e: Exception) { null }

        val cloudTotalDebt      = prefsDoc?.getLong("totalDebt")
        val cloudDeadline       = prefsDoc?.getString("deadline")
        val cloudPersonality    = prefsDoc?.getString("personalityMode")
        val cloudReminderHour   = prefsDoc?.getLong("reminderHour")?.toInt()
        val cloudReminderMinute = prefsDoc?.getLong("reminderMinute")?.toInt()
        val cloudSetupDate      = prefsDoc?.getString("setupDate")

        // ── 2. Download transaksi ─────────────────────────────────
        val snapshot = firestore
            .collection("users").document(uid)
            .collection("transactions")
            .get().await()

        val localList = transactionRepository.allTransactions
            .firstOrNull() ?: emptyList()
        val localMap = localList.associateBy { it.id }

        // ✅ Kumpulkan semua ID yang ada di cloud
        val cloudIds = mutableSetOf<Long>()

        var inserted = 0
        var updated  = 0

        if (!snapshot.isEmpty) {
            snapshot.documents.forEach { doc ->
                val cloudTx = Transaction(
                    id     = doc.getLong("id") ?: 0L,
                    amount = doc.getLong("amount") ?: 0L,
                    source = doc.getString("source") ?: "Cloud sync",
                    date   = doc.getLong("date") ?: System.currentTimeMillis()
                )

                if (cloudTx.amount <= 0) return@forEach
                cloudIds.add(cloudTx.id)

                val localTx = localMap[cloudTx.id]
                when {
                    localTx == null -> {
                        transactionRepository.insert(cloudTx)
                        inserted++
                    }
                    localTx.amount != cloudTx.amount ||
                            localTx.source != cloudTx.source ||
                            localTx.date   != cloudTx.date   -> {
                        transactionRepository.insert(cloudTx)
                        updated++
                    }
                    else -> { /* skip */ }
                }
            }
        }

        // ✅ Hapus transaksi lokal yang TIDAK ADA di cloud
        // (artinya sudah dihapus dari cloud sebelumnya)
        var deleted = 0
        localList.forEach { localTx ->
            if (localTx.id !in cloudIds) {
                transactionRepository.deleteTransaction(localTx.id)
                deleted++
            }
        }

        // ✅ Sync ulang checksums biar upload berikutnya tidak re-upload yang sudah ada
        if (deleted > 0 || inserted > 0 || updated > 0) {
            try {
                val metaRef = firestore
                    .collection("users").document(uid)
                    .collection("_meta").document("checksums")
                val remainingLocal = transactionRepository.allTransactions.firstOrNull() ?: emptyList()
                val newChecksums = remainingLocal.associate { tx ->
                    tx.id.toString() to "${tx.id}_${tx.amount}_${tx.source}_${tx.date}".hashCode().toString()
                }
                metaRef.set(newChecksums, SetOptions.merge()).await()
            } catch (e: Exception) { /* abaikan, tidak kritis */ }
        }

        return DownloadResult(
            totalCloud      = snapshot.size(),
            downloaded      = inserted,
            updated         = updated,
            deleted         = deleted,            // ✅ baru
            totalDebt       = cloudTotalDebt,
            deadline        = cloudDeadline,
            personalityMode = cloudPersonality,
            reminderHour    = cloudReminderHour,
            reminderMinute  = cloudReminderMinute,
            setupDate       = cloudSetupDate
        )
    }

    // ── Cek apakah ada data di cloud ─────────────────────────────
    // ✅ FIX: Sebelumnya hanya cek transaksi → false untuk akun baru tanpa transaksi.
    // Sekarang: cek dokumen user EXISTS saja — kalau akun sudah pernah login
    // dan onboarding selesai, dokumen user pasti ada (dibuat saat uploadAll()).
    // Kalau dokumen user tidak ada → benar-benar akun baru, belum onboarding.
    suspend fun hasCloudData(): Boolean {
        return try {
            // Cek 1: ada transaksi di cloud?
            val txSnapshot = firestore
                .collection("users").document(uid)
                .collection("transactions")
                .limit(1).get().await()
            if (!txSnapshot.isEmpty) return true

            // Cek 2: dokumen user ada dan punya field totalDebt?
            // (artinya onboarding sudah pernah selesai dan di-upload)
            val prefsDoc = firestore
                .collection("users").document(uid)
                .get().await()

            // ✅ FIX: cukup cek dokumen exists + punya totalDebt.
            // Kalau totalDebt ada berarti onboarding sudah selesai sebelumnya.
            // Kalau tidak ada totalDebt → akun baru yang belum onboarding → return false
            // supaya user diarahkan ke onboarding.
            prefsDoc.exists() && prefsDoc.contains("totalDebt")
        } catch (e: Exception) {
            false
        }
    }

    // ── Upload preferences saja ───────────────────────────────────
    suspend fun uploadPreferences(totalDebt: Long, deadline: String) {
        firestore.collection("users").document(uid)
            .set(mapOf(
                "totalDebt" to totalDebt,
                "deadline"  to deadline,
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge()).await()
    }

    // ── Download preferences saja ─────────────────────────────────
    suspend fun downloadPreferences(): Map<String, Any>? {
        val doc = firestore.collection("users").document(uid).get().await()
        return if (doc.exists()) doc.data else null
    }
}

// ── SyncResult ────────────────────────────────────────────────────────────────
data class SyncResult(
    val total: Int = 0,
    val uploaded: Int = 0,
    val prefsUploaded: Boolean = false,
    val updated: Int = 0
) {
    fun uploadSummary(): String = when {
        uploaded == 0 && !prefsUploaded ->
            "✅ Semua data sudah sinkron, tidak ada yang perlu diupload."
        uploaded == 0 && prefsUploaded ->
            "✅ Pengaturan berhasil diupload."
        uploaded == total ->
            "✅ Upload berhasil! $uploaded transaksi + pengaturan diunggah."
        else ->
            "✅ Upload berhasil! $uploaded dari $total transaksi + pengaturan diperbarui."
    }
}

// ── DownloadResult ────────────────────────────────────────────────────────────
data class DownloadResult(
    val totalCloud: Int = 0,
    val downloaded: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,               // ✅ baru
    val totalDebt: Long? = null,
    val deadline: String? = null,
    val personalityMode: String? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val setupDate: String? = null
) {
    fun downloadSummary(): String {
        val parts = mutableListOf<String>()
        if (downloaded > 0) parts.add("$downloaded transaksi baru")
        if (updated > 0)    parts.add("$updated diperbarui")
        if (deleted > 0)    parts.add("$deleted dihapus")
        val txInfo = if (parts.isEmpty()) "Transaksi sudah sinkron." else "${parts.joinToString(", ")}."
        val prefsInfo = if (totalDebt != null || personalityMode != null || reminderHour != null)
            " Pengaturan berhasil dimuat." else ""
        return "✅ $txInfo$prefsInfo"
    }
}