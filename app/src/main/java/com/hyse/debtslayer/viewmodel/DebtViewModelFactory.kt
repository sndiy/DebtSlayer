package com.hyse.debtslayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hyse.debtslayer.data.repository.ChatMessageRepository
import com.hyse.debtslayer.data.repository.ConversationRepository
import com.hyse.debtslayer.data.repository.FeedbackRepository
import com.hyse.debtslayer.data.repository.TransactionRepository
import com.hyse.debtslayer.data.repository.UserPreferencesRepository

class DebtViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val feedbackRepository: FeedbackRepository,
    private val conversationRepository: ConversationRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val chatMessageRepository: ChatMessageRepository, // ✅ BARU
    private val context: Context,
    private val apiKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DebtViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DebtViewModel(
                transactionRepository,
                feedbackRepository,
                conversationRepository,
                preferencesRepository,
                chatMessageRepository, // ✅ BARU
                context,
                apiKey
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
