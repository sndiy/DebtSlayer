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
        // ✅ FIX: Jangan return early saat transaksi null/kosong.
        // Tetap lanjutkan proses agar bisa menghapus transaksi lama di cloud.
        val transactions = transactionRepository.allTransactions.firstOrNull() ?: emptyList()

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

        // ── 2. Ambil semua ID transaksi yang ada di cloud ─────────
        val cloudSnapshot = try {
            firestore.collection("users").document(uid)
                .collection("transactions")
                .get().await()
        } catch (e: Exception) { null }

        val cloudIds = cloudSnapshot?.documents?.mapNotNull { it.getLong("id") }?.toSet()
            ?: emptySet()

        // ── 3. Tentukan transaksi yang berubah (perlu diupload) ───
        val metaRef = firestore
            .collection("users").document(uid)
            .collection("_meta").document("checksums")

        val metaDoc = try { metaRef.get().await() } catch (e: Exception) { null }
        val cloudChecksums = metaDoc?.data?.mapValues { it.value.toString() } ?: emptyMap()

        val localIds = transactions.map { it.id }.toSet()

        val changed = transactions.filter { tx ->
            tx.checksum() != cloudChecksums[tx.id.toString()]
        }

        // ── 4. Tentukan transaksi cloud yang harus dihapus ────────
        // ✅ FIX: ID yang ada di cloud tapi tidak ada di lokal → harus dihapus
        val toDeleteFromCloud = cloudIds - localIds

        // ── 5. Jika tidak ada perubahan sama sekali, return early ─
        // ✅ FIX: Cek toDeleteFromCloud juga, bukan hanya changed
        if (changed.isEmpty() && toDeleteFromCloud.isEmpty()) {
            return SyncResult(
                total         = transactions.size,
                uploaded      = 0,
                prefsUploaded = prefsUploaded
            )
        }

        val batch = firestore.batch()
        val newChecksums = mutableMapOf<String, String>()
        val deletedChecksums = mutableMapOf<String, Any>()

        // ── 6. Upload transaksi yang berubah ──────────────────────
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

        // ── 7. Hapus transaksi cloud yang tidak ada di lokal ──────
        // ✅ FIX: Ini yang hilang sebelumnya — inilah penyebab bug utama
        toDeleteFromCloud.forEach { id ->
            val ref = firestore
                .collection("users").document(uid)
                .collection("transactions").document(id.toString())
            batch.delete(ref)
            // Tandai checksum-nya untuk dihapus juga
            deletedChecksums[id.toString()] =
                com.google.firebase.firestore.FieldValue.delete()
        }

        batch.commit().await()

        // ── 8. Update checksums: merge yang baru, hapus yang lama ─
        if (newChecksums.isNotEmpty()) {
            metaRef.set(newChecksums, SetOptions.merge()).await()
        }
        if (deletedChecksums.isNotEmpty()) {
            metaRef.update(deletedChecksums).await()
        }

        // ✅ Jika lokal kosong dan semua cloud sudah dihapus,
        // hapus juga dokumen checksums seluruhnya agar bersih
        if (transactions.isEmpty()) {
            try { metaRef.delete().await() } catch (e: Exception) { /* abaikan */ }
        }

        return SyncResult(
            total         = transactions.size,
            uploaded      = changed.size,
            deleted       = toDeleteFromCloud.size,     // ✅ baru
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

        // Hapus transaksi lokal yang tidak ada di cloud
        var deleted = 0
        localList.forEach { localTx ->
            if (localTx.id !in cloudIds) {
                transactionRepository.deleteTransaction(localTx.id)
                deleted++
            }
        }

        // Sync ulang checksums
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
            deleted         = deleted,
            totalDebt       = cloudTotalDebt,
            deadline        = cloudDeadline,
            personalityMode = cloudPersonality,
            reminderHour    = cloudReminderHour,
            reminderMinute  = cloudReminderMinute,
            setupDate       = cloudSetupDate
        )
    }

    // ── Cek apakah ada data di cloud ──────────────────────────────
    suspend fun hasCloudData(): Boolean {
        return try {
            val txSnapshot = firestore
                .collection("users").document(uid)
                .collection("transactions")
                .limit(1).get().await()
            if (!txSnapshot.isEmpty) return true

            val prefsDoc = firestore
                .collection("users").document(uid)
                .get().await()

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
    val deleted: Int = 0,               // ✅ baru
    val prefsUploaded: Boolean = false,
    val updated: Int = 0
) {
    fun uploadSummary(): String = when {
        uploaded == 0 && deleted == 0 && !prefsUploaded ->
            "✅ Semua data sudah sinkron, tidak ada yang perlu diupload."
        uploaded == 0 && deleted == 0 && prefsUploaded ->
            "✅ Pengaturan berhasil diupload."
        else -> buildString {
            append("✅ Upload berhasil! ")
            if (uploaded > 0) append("$uploaded transaksi diunggah")
            if (uploaded > 0 && deleted > 0) append(", ")
            if (deleted > 0) append("$deleted transaksi dihapus dari cloud")
            if (prefsUploaded) append(" + pengaturan diperbarui")
            append(".")
        }
    }
}

// ── DownloadResult ────────────────────────────────────────────────────────────
data class DownloadResult(
    val totalCloud: Int = 0,
    val downloaded: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
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