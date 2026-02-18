package com.hyse.debtslayer.data.repository

import com.hyse.debtslayer.data.dao.TransactionDao
import com.hyse.debtslayer.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteTransaction(id)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAllTransactions()
    }
}
