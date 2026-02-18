package com.hyse.debtslayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.hyse.debtslayer.data.database.DebtDatabase
import com.hyse.debtslayer.data.repository.ChatMessageRepository
import com.hyse.debtslayer.data.repository.ConversationRepository
import com.hyse.debtslayer.data.repository.FeedbackRepository
import com.hyse.debtslayer.data.repository.TransactionRepository
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
import com.hyse.debtslayer.notification.DailyReminderScheduler
import com.hyse.debtslayer.ui.screens.HomeScreen
import com.hyse.debtslayer.ui.theme.DebtSlayerTheme
import com.hyse.debtslayer.viewmodel.DebtViewModel
import com.hyse.debtslayer.viewmodel.DebtViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: DebtViewModel

    // State notif permission — diobservasi oleh Composable
    private var notificationPermissionGranted = mutableStateOf(false)
    private var shouldShowNotifBanner = mutableStateOf(false)

    // Launcher untuk minta izin notifikasi
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted.value = isGranted
        shouldShowNotifBanner.value = false
        if (isGranted) {
            DailyReminderScheduler.schedule(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val geminiApiKey = BuildConfig.GEMINI_API_KEY

        val database = DebtDatabase.getDatabase(applicationContext)
        val transactionRepository = TransactionRepository(database.transactionDao())
        val feedbackRepository = FeedbackRepository(database.feedbackDao())
        val conversationRepository = ConversationRepository(database.conversationHistoryDao())
        val preferencesRepository = UserPreferencesRepository(applicationContext)
        val chatMessageRepository = ChatMessageRepository(database.chatMessageDao())

        val factory = DebtViewModelFactory(
            transactionRepository,
            feedbackRepository,
            conversationRepository,
            preferencesRepository,
            chatMessageRepository,
            applicationContext,
            geminiApiKey
        )
        viewModel = ViewModelProvider(this, factory)[DebtViewModel::class.java]

        // Cek dan minta izin notifikasi (Android 13+)
        checkNotificationPermission()

        // Schedule notifikasi harian Mai
        DailyReminderScheduler.schedule(applicationContext)

        setContent {
            DebtSlayerTheme {
                HomeScreen(
                    viewModel = viewModel,
                    notifGranted = notificationPermissionGranted.value,
                    showNotifBanner = shouldShowNotifBanner.value,
                    onRequestNotifPermission = { requestNotificationPermission() },
                    onDismissNotifBanner = { shouldShowNotifBanner.value = false }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-cek saat user balik ke app (mungkin baru enable dari Settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            notificationPermissionGranted.value = granted
            if (granted) shouldShowNotifBanner.value = false
        } else {
            notificationPermissionGranted.value = true
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            notificationPermissionGranted.value = granted
            // Tampilkan banner jika belum izin (tapi tidak langsung minta — tunggu user interaksi)
            if (!granted) shouldShowNotifBanner.value = true
        } else {
            // Android < 13 tidak perlu izin runtime
            notificationPermissionGranted.value = true
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
