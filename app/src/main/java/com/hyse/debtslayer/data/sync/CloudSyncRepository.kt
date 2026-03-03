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

    // ── Checksum per transaksi ────────────────────────────────────
    private fun Transaction.checksum(): String =
        "${id}_${amount}_${source}_${date}".hashCode().toString()

    // ── Upload SEMUA — transaksi + preferences ────────────────────
    suspend fun uploadAll(
        totalDebt: Long? = null,
        deadline: String? = null
    ): SyncResult {
        val transactions = transactionRepository.allTransactions.firstOrNull()
            ?: return SyncResult(0, 0)

        // ── 1. Upload preferences kalau ada ──────────────────────
        if (totalDebt != null && totalDebt > 0 && deadline != null) {
            firestore.collection("users").document(uid)
                .set(mapOf(
                    "totalDebt" to totalDebt,
                    "deadline"  to deadline,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()
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
            total = transactions.size,
            uploaded = 0,
            prefsUploaded = totalDebt != null
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
            total          = transactions.size,
            uploaded       = changed.size,
            prefsUploaded  = totalDebt != null
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

    // ── Download transaksi + preferences ─────────────────────────
    suspend fun downloadAll(): DownloadResult {
        // ── 1. Download preferences (totalDebt, deadline) ─────────
        val prefsDoc = try {
            firestore.collection("users").document(uid).get().await()
        } catch (e: Exception) { null }

        val cloudTotalDebt = prefsDoc?.getLong("totalDebt")
        val cloudDeadline  = prefsDoc?.getString("deadline")

        // ── 2. Download transaksi ─────────────────────────────────
        val snapshot = firestore
            .collection("users").document(uid)
            .collection("transactions")
            .get().await()

        val localMap = transactionRepository.allTransactions
            .firstOrNull()
            ?.associateBy { it.id }
            ?: emptyMap()

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

        return DownloadResult(
            totalCloud    = snapshot.size(),
            downloaded    = inserted,
            updated       = updated,
            totalDebt     = cloudTotalDebt,
            deadline      = cloudDeadline
        )
    }

    // ── Cek apakah ada data di cloud ─────────────────────────────
    suspend fun hasCloudData(): Boolean {
        return try {
            // Cek transaksi
            val txSnapshot = firestore
                .collection("users").document(uid)
                .collection("transactions")
                .limit(1)
                .get().await()
            if (!txSnapshot.isEmpty) return true

            // Cek preferences (totalDebt tersimpan)
            val prefsDoc = firestore
                .collection("users").document(uid)
                .get().await()
            prefsDoc.exists() && prefsDoc.contains("totalDebt")
        } catch (e: Exception) { false }
    }

    // ── Upload preferences saja (tetap ada untuk kompatibilitas) ─
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

// ── SyncResult untuk upload ───────────────────────────────────────────────────
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
            "✅ Pengaturan hutang berhasil diupload."
        uploaded == total ->
            "✅ Upload berhasil! $uploaded transaksi + pengaturan hutang diunggah."
        else ->
            "✅ Upload berhasil! $uploaded dari $total transaksi + pengaturan hutang diperbarui."
    }
}

// ── DownloadResult — bawa juga totalDebt & deadline dari cloud ───────────────
data class DownloadResult(
    val totalCloud: Int = 0,
    val downloaded: Int = 0,
    val updated: Int = 0,
    val totalDebt: Long? = null,      // ✅ dari Firestore
    val deadline: String? = null       // ✅ dari Firestore
) {
    fun downloadSummary(): String {
        val txInfo = when {
            downloaded == 0 && updated == 0 -> "Transaksi sudah sinkron."
            downloaded > 0 && updated > 0   -> "$downloaded transaksi baru, $updated diperbarui."
            downloaded > 0                  -> "$downloaded transaksi baru ditambahkan."
            else                            -> "$updated transaksi diperbarui."
        }
        val prefsInfo = if (totalDebt != null && deadline != null)
            " Pengaturan hutang & deadline berhasil dimuat." else ""
        return "✅ $txInfo$prefsInfo"
    }
}