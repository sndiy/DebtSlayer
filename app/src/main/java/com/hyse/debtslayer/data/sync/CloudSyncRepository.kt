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

    // Upload semua transaksi lokal ke Firestore
    suspend fun uploadAll() {
        val transactions = transactionRepository.allTransactions.firstOrNull() ?: return
        val batch = firestore.batch()
        transactions.forEach { tx ->
            val ref = firestore
                .collection("users").document(uid)
                .collection("transactions").document(tx.id.toString())
            batch.set(ref, mapOf(
                "id"     to tx.id,
                "amount" to tx.amount,
                "source" to tx.source,
                "date"   to tx.date
            ), SetOptions.merge())
        }
        batch.commit().await()
    }

    // Download transaksi dari Firestore dan simpan ke Room
    suspend fun downloadAll() {
        val snapshot = firestore
            .collection("users").document(uid)
            .collection("transactions")
            .get().await()

        snapshot.documents.forEach { doc ->
            val tx = Transaction(
                id     = (doc.getLong("id") ?: 0L),
                amount = (doc.getLong("amount") ?: 0L),
                source = (doc.getString("source") ?: "Cloud sync"),
                date   = (doc.getLong("date") ?: System.currentTimeMillis())
            )
            if (tx.amount > 0) transactionRepository.insert(tx)
        }
    }

    // Upload preferences (deadline, total debt)
    suspend fun uploadPreferences(totalDebt: Long, deadline: String) {
        firestore.collection("users").document(uid)
            .set(mapOf(
                "totalDebt" to totalDebt,
                "deadline"  to deadline,
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge()).await()
    }

    // Download preferences
    suspend fun downloadPreferences(): Map<String, Any>? {
        val doc = firestore.collection("users").document(uid).get().await()
        return if (doc.exists()) doc.data else null
    }
}