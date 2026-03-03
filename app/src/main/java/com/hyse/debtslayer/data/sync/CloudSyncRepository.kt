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

    // ── Hash sederhana untuk 1 transaksi ─────────────────────────
    // Kalau id, amount, source, date tidak berubah → hash sama → skip upload
    private fun Transaction.checksum(): String =
        "${id}_${amount}_${source}_${date}".hashCode().toString()

    // ── Upload — hanya yang berubah ───────────────────────────────
    suspend fun uploadAll(): SyncResult {
        val transactions = transactionRepository.allTransactions.firstOrNull() ?: return SyncResult(0, 0)

        // Ambil checksum yang sudah tersimpan di cloud
        val metaRef = firestore
            .collection("users").document(uid)
            .collection("_meta").document("checksums")
        val metaDoc = try { metaRef.get().await() } catch (e: Exception) { null }
        val cloudChecksums = metaDoc?.data?.mapValues { it.value.toString() } ?: emptyMap()

        // Filter: hanya transaksi yang checksumnya berbeda dari cloud
        val changed = transactions.filter { tx ->
            val localHash = tx.checksum()
            val cloudHash = cloudChecksums[tx.id.toString()]
            localHash != cloudHash
        }

        if (changed.isEmpty()) return SyncResult(total = transactions.size, uploaded = 0)

        // Upload hanya yang berubah
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

        // Update checksum di cloud — merge supaya yang lama tidak hilang
        metaRef.set(newChecksums, SetOptions.merge()).await()

        return SyncResult(total = transactions.size, uploaded = changed.size)
    }

    // ── Hapus checksum transaksi yang sudah dihapus lokal ─────────
    // Dipanggil setelah user hapus transaksi, supaya checksum tidak stale
    suspend fun removeChecksum(transactionId: Long) {
        try {
            val metaRef = firestore
                .collection("users").document(uid)
                .collection("_meta").document("checksums")
            metaRef.update(transactionId.toString(), com.google.firebase.firestore.FieldValue.delete()).await()
        } catch (e: Exception) { /* abaikan kalau gagal */ }
    }

    // ── Download — skip transaksi yang sudah ada di lokal ─────────
    suspend fun downloadAll(): SyncResult {
        val snapshot = firestore
            .collection("users").document(uid)
            .collection("transactions")
            .get().await()

        // Ambil semua ID transaksi lokal dulu
        val localIds = transactionRepository.allTransactions
            .firstOrNull()
            ?.map { it.id }
            ?.toSet()
            ?: emptySet()

        var inserted = 0
        snapshot.documents.forEach { doc ->
            val tx = Transaction(
                id     = doc.getLong("id") ?: 0L,
                amount = doc.getLong("amount") ?: 0L,
                source = doc.getString("source") ?: "Cloud sync",
                date   = doc.getLong("date") ?: System.currentTimeMillis()
            )
            // Skip kalau sudah ada di lokal
            if (tx.amount > 0 && tx.id !in localIds) {
                transactionRepository.insert(tx)
                inserted++
            }
        }

        return SyncResult(total = snapshot.size(), downloaded = inserted)
    }

    // ── Upload preferences ────────────────────────────────────────
    suspend fun uploadPreferences(totalDebt: Long, deadline: String) {
        firestore.collection("users").document(uid)
            .set(mapOf(
                "totalDebt" to totalDebt,
                "deadline"  to deadline,
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge()).await()
    }

    // ── Download preferences ──────────────────────────────────────
    suspend fun downloadPreferences(): Map<String, Any>? {
        val doc = firestore.collection("users").document(uid).get().await()
        return if (doc.exists()) doc.data else null
    }
}

// ── Result class untuk info berapa yang diupload/download ────────────────────
data class SyncResult(
    val total: Int = 0,
    val uploaded: Int = 0,
    val downloaded: Int = 0
) {
    val isEfficient: Boolean get() = uploaded < total || downloaded < total
    fun uploadSummary(): String = when {
        uploaded == 0  -> "✅ Semua data sudah sinkron, tidak ada yang perlu diupload."
        uploaded == total -> "✅ Upload berhasil! $uploaded transaksi diunggah."
        else           -> "✅ Upload berhasil! $uploaded dari $total transaksi diperbarui."
    }
    fun downloadSummary(): String = when {
        downloaded == 0 -> "✅ Semua data sudah sinkron, tidak ada yang baru."
        else            -> "✅ Download berhasil! $downloaded transaksi baru ditambahkan."
    }
}