package com.hyse.debtslayer.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.hyse.debtslayer.data.entity.ConversationFeedback
import com.hyse.debtslayer.data.entity.ConversationHistory
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.data.repository.ChatMessageRepository
import com.hyse.debtslayer.data.repository.ConversationRepository
import com.hyse.debtslayer.data.repository.FeedbackRepository
import com.hyse.debtslayer.data.repository.TransactionRepository
import com.hyse.debtslayer.data.repository.UserPreferencesRepository
import com.hyse.debtslayer.personality.AdaptiveMaiPersonality
import com.hyse.debtslayer.utils.CurrencyFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: Long = System.currentTimeMillis(),
    val feedbackGiven: Boolean = false,
    val feedbackIsPositive: Boolean? = null
)

data class DebtState(
    val totalDebt: Long = 0L,
    val totalPaid: Long = 0L,
    val remainingDebt: Long = 0L,
    val dailyTarget: Long = 0L,
    val progressPercentage: Float = 0f,
    val daysRemaining: Int = 0
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val candidateTokens: Int = 0,
    val totalTokens: Int = 0,
    val sessionTotal: Int = 0
)

data class RateLimitInfo(
    val message: String,
    val retryAfterMs: Long,
    val isDaily: Boolean = false,
    val triggeredAt: Long = System.currentTimeMillis()
) {
    val retryAtReadable: String get() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(java.util.Date(triggeredAt + retryAfterMs))
    }
    val remainingSeconds: Long get() =
        ((triggeredAt + retryAfterMs) - System.currentTimeMillis()).div(1000).coerceAtLeast(0)
}

class DebtViewModel(
    private val repository: TransactionRepository,
    private val feedbackRepository: FeedbackRepository,
    private val conversationRepository: ConversationRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val context: Context,
    private val apiKey: String
) : ViewModel() {

    data class ModelLimits(
        val rpm: Int,
        val tpm: Long,
        val rpd: Int,
        val tpd: Long,
        val displayName: String
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _debtState = MutableStateFlow(DebtState())
    val debtState: StateFlow<DebtState> = _debtState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _personalityMode = MutableStateFlow(AdaptiveMaiPersonality.PersonalityMode.BALANCED)
    val personalityMode: StateFlow<AdaptiveMaiPersonality.PersonalityMode> = _personalityMode.asStateFlow()

    private val _positiveFeedbackCount = MutableStateFlow(0)
    val positiveFeedbackCount: StateFlow<Int> = _positiveFeedbackCount.asStateFlow()

    private val _negativeFeedbackCount = MutableStateFlow(0)
    val negativeFeedbackCount: StateFlow<Int> = _negativeFeedbackCount.asStateFlow()

    private val _isUpdatingDebt = MutableStateFlow(false)
    val isUpdatingDebt: StateFlow<Boolean> = _isUpdatingDebt.asStateFlow()

    private val _customDeadline = MutableStateFlow<String?>(null)
    val customDeadline: StateFlow<String?> = _customDeadline.asStateFlow()

    private val _totalDebt = MutableStateFlow(0L)
    val totalDebt: StateFlow<Long> = _totalDebt.asStateFlow()

    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()

    private val _dailyTokenTotal = MutableStateFlow(0L)
    val dailyTokenTotal: StateFlow<Long> = _dailyTokenTotal.asStateFlow()

    private val _dailyRequestCount = MutableStateFlow(0)
    val dailyRequestCount: StateFlow<Int> = _dailyRequestCount.asStateFlow()

    private val _rateLimitInfo = MutableStateFlow<RateLimitInfo?>(null)
    val rateLimitInfo: StateFlow<RateLimitInfo?> = _rateLimitInfo.asStateFlow()

    private val _depositConfirmation = MutableStateFlow<Long?>(null)
    val depositConfirmation: StateFlow<Long?> = _depositConfirmation.asStateFlow()

    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()

    private val _pendingFirstSetupGreeting = MutableStateFlow(false)
    val pendingFirstSetupGreeting: StateFlow<Boolean> = _pendingFirstSetupGreeting.asStateFlow()

    val isOnboardingDone = preferencesRepository.isOnboardingDone
    val initialDeadline = preferencesRepository.initialDeadline

    private val _setupDate = MutableStateFlow<String?>(null)
    val setupDate: StateFlow<String?> = _setupDate.asStateFlow()

    val reminderHour = preferencesRepository.reminderHour
    val reminderMinute = preferencesRepository.reminderMinute

    private lateinit var chatModel: GenerativeModel

    private var lastUserMessage = ""
    private var lastAiResponse = ""
    private var activeMessageJob: Job? = null

    companion object {
        private const val TAG = "DebtViewModel"
        const val MODEL_NAME = "gemini-2.5-flash-lite"
        private const val REQUEST_TIMEOUT_MS = 30_000L

        val MODEL_LIMITS = ModelLimits(
            rpm = 10,
            rpd = 20,
            tpm = 250_000,
            tpd = 1_000_000,
            displayName = "Gemini 2.5 Flash Lite"
        )
    }

    private fun getActiveDeadline(): String {
        return _customDeadline.value ?: run {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 30)
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
    }

    init {
        viewModelScope.launch {
            loadPreferences()

            val firstTransactions = withContext(Dispatchers.IO) {
                repository.allTransactions.firstOrNull() ?: emptyList()
            }
            _transactions.value = firstTransactions
            recalculateDebtState(firstTransactions)

            loadChatHistory()
            initAIModel(apiKey)
            loadFeedbackStats()
            cleanupOldData()

            delay(200)
            _isDataReady.value = true
        }

        viewModelScope.launch {
            repository.allTransactions.collect { transactionList ->
                _transactions.value = transactionList
                recalculateDebtState(transactionList)
            }
        }
    }

    private suspend fun initAIModel(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            Log.e(TAG, "API Key belum diisi!")
            return
        }
        try {
            chatModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.8f
                    topK = 30
                    topP = 0.9f
                    maxOutputTokens = 500
                },
                systemInstruction = content { text(getSystemInstruction()) }
            )
            Log.d(TAG, "AI model ready: $MODEL_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "Error init AI: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun sendFirstSetupGreeting() {
        viewModelScope.launch {
            addSystemMessage(buildDynamicGreeting())
            _pendingFirstSetupGreeting.value = false
        }
    }

    fun sendInitialGreeting() {
        viewModelScope.launch {
            val hasTodayChat = withContext(Dispatchers.IO) {
                chatMessageRepository.hasTodayMessages()
            }
            if (!hasTodayChat) {
                delay(100)
                addSystemMessage(buildDynamicGreeting())
            }
        }
    }

    private fun buildDynamicGreeting(): String {
        val state = _debtState.value
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val todayDeposit = _transactions.value
            .filter { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it.date)) == today }
            .sumOf { it.amount }

        val targetStr = CurrencyFormatter.format(state.dailyTarget)
        val remainingStr = CurrencyFormatter.format(state.remainingDebt)

        return when {
            state.daysRemaining <= 0 && state.remainingDebt > 0 ->
                "DEADLINE SUDAH LEWAT. Sisa hutang $remainingStr harus dibayar SEKARANG. Tidak ada alasan."

            state.remainingDebt <= 0 -> listOf(
                "Hutang lunas. Hmm. Tidak kusangka kamu bisa sampai sini — jangan sombong dulu.",
                "Selesai. Semua lunas. ...Bagus. Jangan bikin hutang baru lagi, dengar tidak?",
                "Lunas. Aku tidak akan bilang aku bangga, tapi... ya. Kamu berhasil.",
                "Hutangnya lunas. Rasanya aneh tidak punya yang harus ditagih ke kamu."
            ).random()

            _transactions.value.isEmpty() ->
                "Hai. Aku Sakurajima Mai. Kamu punya hutang $remainingStr yang harus lunas dalam ${state.daysRemaining} hari. " +
                        "Target harian kamu $targetStr. Kalau mau setor, langsung bilang nominalnya dan ketik \"setor\" atau \"nabung\"."

            state.daysRemaining <= 7 && state.remainingDebt > 0 ->
                "Tinggal ${state.daysRemaining} hari. Sisa $remainingStr. Aku tidak mau dengar alasan — setor sekarang."

            todayDeposit >= state.dailyTarget && todayDeposit > 0 ->
                "Kamu sudah setor ${CurrencyFormatter.format(todayDeposit)} hari ini. Lumayan, target tercapai. Sisa hutang $remainingStr."

            todayDeposit in 1 until state.dailyTarget ->
                "Sudah setor ${CurrencyFormatter.format(todayDeposit)} hari ini. " +
                        "Masih kurang ${CurrencyFormatter.format(state.dailyTarget - todayDeposit)} dari target $targetStr."

            todayDeposit == 0L ->
                "Belum setor hari ini. Target $targetStr. Sisa hutang $remainingStr. Jangan nunggu sampai malam."

            else -> "Hai. Sisa hutang $remainingStr. Target hari ini $targetStr."
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(network) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private suspend fun loadPreferences() {
        try {
            val savedMode = preferencesRepository.personalityMode.firstOrNull()
            if (savedMode != null) {
                try {
                    _personalityMode.value = AdaptiveMaiPersonality.PersonalityMode.valueOf(savedMode)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Unknown personality mode: $savedMode")
                }
            }

            val savedDeadline = preferencesRepository.customDeadline.firstOrNull()
            if (!savedDeadline.isNullOrBlank()) _customDeadline.value = savedDeadline

            _positiveFeedbackCount.value = preferencesRepository.positiveFeedbackCount.firstOrNull() ?: 0
            _negativeFeedbackCount.value = preferencesRepository.negativeFeedbackCount.firstOrNull() ?: 0

            val savedTotalDebt = preferencesRepository.customTotalDebt.firstOrNull()
            if (savedTotalDebt != null && savedTotalDebt > 0) _totalDebt.value = savedTotalDebt

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            preferencesRepository.dailyTokenData.firstOrNull()?.let { (total, date) ->
                _dailyTokenTotal.value = if (date == today) total else 0L
            }

            preferencesRepository.dailyRequestData.firstOrNull()?.let { (count, date) ->
                _dailyRequestCount.value = if (date == today) count else 0
            }

            val savedSetupDate = preferencesRepository.setupDate.firstOrNull()
            if (!savedSetupDate.isNullOrBlank()) _setupDate.value = savedSetupDate

        } catch (e: Exception) {
            Log.e(TAG, "Error loading preferences: ${e.message}")
        }
    }

    private fun cleanupOldData() {
        viewModelScope.launch(Dispatchers.IO) {
            try { conversationRepository.cleanOldConversations() }
            catch (e: Exception) { Log.e(TAG, "Error cleanup: ${e.message}") }
        }
    }

    private fun recalculateDebtState(
        transactions: List<Transaction>,
        deadlineOverride: String? = null,
        totalDebtOverride: Long? = null
    ) {
        try {
            val activeTotalDebt = totalDebtOverride ?: _totalDebt.value
            val totalPaid = transactions.sumOf { it.amount }
            val remaining = (activeTotalDebt - totalPaid).coerceAtLeast(0L)
            val progress = if (activeTotalDebt > 0)
                (totalPaid.toFloat() / activeTotalDebt * 100f).coerceAtMost(100f) else 0f

            val activeDeadline = deadlineOverride ?: _customDeadline.value ?: getActiveDeadline()
            val targetDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(activeDeadline)
            val daysRemaining = (((targetDate?.time ?: 0) - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()

            val dailyTarget = when {
                remaining <= 0 -> 0L
                daysRemaining <= 0 -> remaining
                else -> CurrencyFormatter.ceilToThousand((remaining + daysRemaining - 1) / daysRemaining)
            }

            _debtState.value = DebtState(
                totalDebt = activeTotalDebt,
                totalPaid = totalPaid,
                remainingDebt = remaining,
                progressPercentage = progress,
                daysRemaining = daysRemaining,
                dailyTarget = dailyTarget
            )

            if (remaining <= 0L) {
                com.hyse.debtslayer.notification.DailyReminderScheduler.cancel(context)
            } else {
                com.hyse.debtslayer.notification.DailyReminderScheduler.schedule(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recalculateDebtState: ${e.message}", e)
        }
    }

    private fun getSystemInstruction(): String {
        val mode = _personalityMode.value
        val state = _debtState.value

        return """
        Kamu adalah Sakurajima Mai dari anime Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai.
        Karakter: Tsundere — galak di luar, peduli di dalam. Tidak suka mengakui perasaan.
        
        ══════════════════════════════════════
        GAYA BICARA (WAJIB DIIKUTI):
        ══════════════════════════════════════
        • Maksimal 2 kalimat per respons
        • Nada cool, dingin, sedikit sinis
        • Sesekali bocor rasa peduli (tapi langsung disembunyikan)
        • Tidak boleh: gombal, cheesy, terlalu semangat
        • Emoji: maksimal 1, hanya kalau benar-benar perlu
        
        ══════════════════════════════════════
        MODE SAAT INI: ${when (mode) {
            AdaptiveMaiPersonality.PersonalityMode.STRICT -> "STRICT — Tegas, tidak ada ampun, kritis"
            AdaptiveMaiPersonality.PersonalityMode.BALANCED -> "BALANCED — Seimbang antara tegas dan supportif"
            AdaptiveMaiPersonality.PersonalityMode.GENTLE -> "GENTLE — Lebih lembut, tapi tetap tsundere"
        }}
        ══════════════════════════════════════
        
        STATUS HUTANG SAAT INI:
        • Total hutang   : ${CurrencyFormatter.format(_totalDebt.value)}
        • Sudah dibayar  : ${CurrencyFormatter.format(state.totalPaid)}
        • Sisa hutang    : ${CurrencyFormatter.format(state.remainingDebt)}
        • Target hari ini: ${CurrencyFormatter.format(state.dailyTarget)}
        • Hari tersisa   : ${state.daysRemaining} hari
        
        ══════════════════════════════════════
        CARA MEMAHAMI PESAN USER:
        ══════════════════════════════════════
        
        1. SETORAN UANG
           ── TIPE A: Angka + kata setor (LANGSUNG PROSES) ──
           Kata kunci: "setor", "setoran", "nabung", "bayar", "cicil", "transfer", "masuk"
           Contoh: "setor 50rb", "nabung 100ribu", "bayar 75000", "cicil 1jt"
           → Semua nominal valid, berapapun — bahkan Rp 1
           → ACTION: [ACTION:DEPOSIT:jumlah_dalam_angka_penuh]
           
           ── TIPE B: Angka saja (JANGAN PROSES) ──
           Contoh: "2", "34112", "500000", "1jt", "50rb"
           → JANGAN proses sebagai deposit
           → Beritahu cara yang benar, gaya tsundere
           → ACTION: [ACTION:NONE]
        
        2. HAPUS SETORAN
           Contoh: "hapus setoran", "batalkan", "salah setor"
           → ACTION: [ACTION:DELETE_LAST]
        
        3. GREETING: balas singkat, tanya sudah setor → [ACTION:NONE]
        4. TANYA STATUS: ceritakan progress → [ACTION:NONE]
        5. CURHAT: empati tipis, dorong setor → [ACTION:NONE]
        6. HUTANG LUNAS: akui dengan gaya tsundere, emosi "bocor" → [ACTION:NONE]
        7. LAINNYA: tetap sebagai Mai → [ACTION:NONE]
        
        ══════════════════════════════════════
        ATURAN ACTION TAG:
        ══════════════════════════════════════
        • WAJIB ada di setiap respons, di baris PALING AKHIR
        • Format: [ACTION:DEPOSIT:50000] bukan [action:deposit:50000]
        
        CONTOH:
        User: "setor 2" → Mai: 2 rupiah. Aku catat, walaupun... ya sudahlah.\n[ACTION:DEPOSIT:2]
        User: "setor 50rb" → Mai: Hmm, lumayan.\n[ACTION:DEPOSIT:50000]
        User: "34112" → Mai: Mau setor? Bilang 'setor 34.112' dulu.\n[ACTION:NONE]
        User: "hapus setoran" → Mai: Dihapus.\n[ACTION:DELETE_LAST]
        User: "halo mai" → Mai: Ya, ada apa. Sudah setor?\n[ACTION:NONE]
    """.trimIndent()
    }

    fun getConversationCount(callback: (Int) -> Unit) {
        viewModelScope.launch {
            try { callback(conversationRepository.getConversationCount()) }
            catch (e: Exception) { callback(0) }
        }
    }

    private fun loadFeedbackStats() {
        viewModelScope.launch {
            try {
                _positiveFeedbackCount.value = feedbackRepository.getPositiveCount()
                _negativeFeedbackCount.value = feedbackRepository.getNegativeCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading feedback: ${e.message}")
            }
        }
    }

    private suspend fun loadChatHistory() {
        try {
            val todayMessages = withContext(Dispatchers.IO) { chatMessageRepository.getTodayMessages() }
            if (todayMessages.isNotEmpty()) {
                _messages.value = todayMessages.map { entity ->
                    ChatMessage(
                        text = entity.text,
                        isFromUser = entity.isFromUser,
                        timestamp = entity.timestamp,
                        messageId = entity.id,
                        feedbackGiven = entity.feedbackGiven,
                        feedbackIsPositive = entity.feedbackIsPositive
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat history: ${e.message}")
        }
    }

    fun dismissDepositConfirmation() { _depositConfirmation.value = null }

    fun cancelPendingMessage() {
        if (activeMessageJob?.isActive == true) {
            activeMessageJob?.cancel()
            Log.d(TAG, "Active message job cancelled")
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        activeMessageJob?.cancel()

        activeMessageJob = viewModelScope.launch(Dispatchers.Main) {
            try {
                _isLoading.value = true
                addUserMessage(userMessage)
                lastUserMessage = userMessage

                if (!withContext(Dispatchers.IO) { isNetworkAvailable() }) {
                    addAiMessage("Tidak ada koneksi internet. Cek WiFi/Data!")
                    return@launch
                }

                if (_dailyRequestCount.value >= MODEL_LIMITS.rpd) {
                    _rateLimitInfo.value = RateLimitInfo(
                        message = "Limit harian (RPD) tercapai: ${_dailyRequestCount.value}/${MODEL_LIMITS.rpd} request hari ini",
                        retryAfterMs = getMillisUntilMidnightUtc(),
                        isDaily = true
                    )
                    addAiMessage("Limit harian API sudah habis (${_dailyRequestCount.value}/${MODEL_LIMITS.rpd} request). Reset jam 07:00 WIB (00:00 UTC).")
                    return@launch
                }

                val currentState = _debtState.value
                val contextMessage = """
                    User: $userMessage
                    
                    Status hutang sekarang:
                    - Sudah dibayar: ${CurrencyFormatter.format(currentState.totalPaid)}
                    - Sisa hutang: ${CurrencyFormatter.format(currentState.remainingDebt)}
                    - Target hari ini: ${CurrencyFormatter.format(currentState.dailyTarget)}
                    - Hari tersisa: ${currentState.daysRemaining} hari
                    
                    Respons sebagai Mai (max 2 kalimat, tsundere, cool).
                    WAJIB tambahkan [ACTION:TIPE:NILAI] di baris terakhir.
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    withTimeout(REQUEST_TIMEOUT_MS) {
                        chatModel.generateContent(content { text(contextMessage) })
                    }
                }

                _dailyRequestCount.value += 1
                withContext(Dispatchers.IO) { preferencesRepository.incrementDailyRequest() }

                val aiResponse = response.text ?: "Maaf, aku tidak bisa merespons sekarang."
                lastAiResponse = aiResponse

                response.usageMetadata?.let { usage ->
                    val thisTotal = usage.totalTokenCount ?: 0
                    val prev = _tokenUsage.value
                    _tokenUsage.value = TokenUsage(
                        promptTokens = usage.promptTokenCount ?: 0,
                        candidateTokens = usage.candidatesTokenCount ?: 0,
                        totalTokens = thisTotal,
                        sessionTotal = prev.sessionTotal + thisTotal
                    )
                    if (thisTotal > 0) {
                        withContext(Dispatchers.IO) { preferencesRepository.addDailyTokens(thisTotal) }
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                        preferencesRepository.dailyTokenData.firstOrNull()?.let { (total, date) ->
                            _dailyTokenTotal.value = if (date == today) total else thisTotal.toLong()
                        }
                    }
                }

                withContext(Dispatchers.IO) { saveConversation(userMessage, aiResponse, true) }

                val actionPattern = """\[ACTION:([A-Z_]+)(?::([^\]]*))?]""".toRegex()
                val actionMatch = actionPattern.find(aiResponse)
                val cleanResponse = aiResponse.replace(actionPattern, "").trim()

                actionMatch?.let { match ->
                    when (match.groupValues[1]) {
                        "DEPOSIT" -> {
                            val amount = match.groupValues[2].trim().toLongOrNull()
                            if (amount != null && amount > 0) {
                                withContext(Dispatchers.IO) { saveTransaction(amount, "Chat dengan Mai") }
                                _depositConfirmation.value = amount
                            } else {
                                Log.d(TAG, "Unknown action: ${match.groupValues[1]}")
                            }
                        }
                        "DELETE_LAST" -> {
                            _transactions.value.lastOrNull()?.let { last ->
                                withContext(Dispatchers.IO) { repository.deleteTransaction(last.id) }
                            }
                        }

                        else -> Log.d(TAG, "Unknown action: ${match.groupValues[1]}")
                    }
                }

                addAiMessage(cleanResponse.ifBlank { aiResponse })

            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Request timed out after ${REQUEST_TIMEOUT_MS}ms")
                addAiMessage("Koneksi terputus saat menunggu respons. Cek internet lalu coba lagi.")
                withContext(Dispatchers.IO) { saveConversation(userMessage, "timeout", false) }
            } catch (e: CancellationException) {
                Log.d(TAG, "Message job cancelled")
            } catch (e: Exception) {
                val errorMessage = handleApiError(e)
                addAiMessage(errorMessage)
                withContext(Dispatchers.IO) { saveConversation(userMessage, errorMessage, false) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getMillisUntilMidnightUtc(): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - now).coerceAtLeast(60_000L)
    }

    private fun handleApiError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("429") || msg.contains("quota", true) ||
                    msg.contains("rate", true) || msg.contains("RESOURCE_EXHAUSTED", true) -> {
                val retrySeconds = extractRetrySeconds(msg)
                val isRpdLimit = msg.contains("day", true) || msg.contains("daily", true) ||
                        msg.contains("RPD", true) || _dailyRequestCount.value >= MODEL_LIMITS.rpd

                if (isRpdLimit) {
                    val msUntilReset = getMillisUntilMidnightUtc()
                    _rateLimitInfo.value = RateLimitInfo(
                        message = "Limit harian tercapai (${_dailyRequestCount.value}/${MODEL_LIMITS.rpd} request/hari)",
                        retryAfterMs = msUntilReset,
                        isDaily = true
                    )
                    viewModelScope.launch { delay(msUntilReset + 1000); _rateLimitInfo.value = null }
                    "Limit harian API habis (${_dailyRequestCount.value}/${MODEL_LIMITS.rpd} request/hari). Reset jam 07:00 WIB."
                } else {
                    val retryMs = (retrySeconds * 1000L).coerceAtLeast(60_000L)
                    _rateLimitInfo.value = RateLimitInfo(
                        message = "Limit per menit (RPM) tercapai",
                        retryAfterMs = retryMs,
                        isDaily = false
                    )
                    viewModelScope.launch { delay(retryMs + 1000); _rateLimitInfo.value = null }
                    "Terlalu banyak pesan dalam 1 menit. Tunggu ${retrySeconds}s (maks ${MODEL_LIMITS.rpm} pesan/menit)."
                }
            }
            msg.contains("API key", true) || msg.contains("401") -> "API Key tidak valid. Cek GEMINI_API_KEY di local.properties."
            msg.contains("404") -> "Model tidak ditemukan. Cek koneksi dan coba lagi."
            e is java.net.UnknownHostException -> "Tidak bisa konek ke Gemini. Cek internet."
            e is java.net.SocketTimeoutException -> "Koneksi timeout. Coba lagi."
            msg.contains("500") || msg.contains("503") -> "Server Gemini bermasalah. Tunggu lalu coba lagi."
            else -> "Error: ${msg.take(100)}. Coba lagi ya!"
        }
    }

    private fun extractRetrySeconds(errorMsg: String): Long {
        val patterns = listOf(
            """retry.{0,20}(\d+)\s*s""".toRegex(RegexOption.IGNORE_CASE),
            """(\d+)\s*second""".toRegex(RegexOption.IGNORE_CASE),
            """retryDelay[":\s]+(\d+)""".toRegex(RegexOption.IGNORE_CASE),
            """"retryAfter[":\s]+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val seconds = pattern.find(errorMsg)?.groupValues?.get(1)?.toLongOrNull()
            if (seconds != null && seconds in 1..300) return seconds
        }
        return 60L
    }

    private suspend fun saveConversation(userMsg: String, aiResp: String, wasSuccessful: Boolean) {
        try {
            conversationRepository.insert(
                ConversationHistory(
                    userMessage = userMsg,
                    aiResponse = aiResp,
                    timestamp = System.currentTimeMillis(),
                    wasSuccessful = wasSuccessful,
                    context = ""
                )
            )
        } catch (e: Exception) { Log.e(TAG, "Error saving conversation: ${e.message}") }
    }

    fun giveFeedback(messageId: Long, isPositive: Boolean) {
        _messages.value = _messages.value.map { msg ->
            if (msg.messageId == messageId && !msg.isFromUser)
                msg.copy(feedbackGiven = true, feedbackIsPositive = isPositive)
            else msg
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { chatMessageRepository.saveFeedback(messageId, isPositive) }

                val ctx = JSONObject().apply {
                    put("totalPaid", _debtState.value.totalPaid)
                    put("remaining", _debtState.value.remainingDebt)
                    put("target", _debtState.value.dailyTarget)
                    put("personalityMode", _personalityMode.value.name)
                }.toString()

                withContext(Dispatchers.IO) {
                    feedbackRepository.insert(
                        ConversationFeedback(
                            userMessage = lastUserMessage,
                            aiResponse = lastAiResponse,
                            isPositive = isPositive,
                            timestamp = System.currentTimeMillis(),
                            context = ctx
                        )
                    )
                }

                if (isPositive) _positiveFeedbackCount.value += 1
                else _negativeFeedbackCount.value += 1

            } catch (e: Exception) { Log.e(TAG, "Error saving feedback: ${e.message}") }
        }
    }

    fun setPersonalityMode(mode: AdaptiveMaiPersonality.PersonalityMode) {
        _personalityMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.savePersonalityMode(mode.name)
        }
        rebuildChatModel()
    }

    private fun rebuildChatModel() {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") return
        try {
            chatModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.8f
                    topK = 30
                    topP = 0.9f
                    maxOutputTokens = 500
                },
                systemInstruction = content { text(getSystemInstruction()) }
            )
        } catch (e: Exception) { Log.e(TAG, "Error rebuilding chatModel: ${e.message}") }
    }

    private suspend fun saveTransaction(amount: Long, source: String) {
        try {
            repository.insert(Transaction(amount = amount, source = source, date = System.currentTimeMillis()))
        } catch (e: Exception) { Log.e(TAG, "Error saving transaction: ${e.message}", e) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { withContext(Dispatchers.IO) { repository.deleteTransaction(id) } }
    }

    private fun addUserMessage(text: String) {
        val tempMsg = ChatMessage(text, isFromUser = true)
        _messages.value = _messages.value + tempMsg
        viewModelScope.launch {
            val dbId = withContext(Dispatchers.IO) { chatMessageRepository.saveMessage(text, isFromUser = true) }
            _messages.value = _messages.value.map {
                if (it.messageId == tempMsg.messageId && it.isFromUser) it.copy(messageId = dbId) else it
            }
        }
    }

    private fun addAiMessage(text: String) {
        val tempMsg = ChatMessage(text, isFromUser = false)
        _messages.value = _messages.value + tempMsg
        viewModelScope.launch {
            val dbId = withContext(Dispatchers.IO) { chatMessageRepository.saveMessage(text, isFromUser = false) }
            _messages.value = _messages.value.map {
                if (it.messageId == tempMsg.messageId && !it.isFromUser) it.copy(messageId = dbId) else it
            }
        }
    }

    private fun addSystemMessage(text: String) = addAiMessage(text)

    fun getAllTransactions(): List<Transaction> = _transactions.value


    fun updateDeadlineFromSettings(newDeadline: String) {
        viewModelScope.launch {
            try {
                _isUpdatingDebt.value = true
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
                val parsed = df.parse(newDeadline) ?: return@launch
                if (parsed.time <= System.currentTimeMillis()) return@launch
                _customDeadline.value = newDeadline
                withContext(Dispatchers.IO) { preferencesRepository.saveCustomDeadline(newDeadline) }
                recalculateDebtState(transactions = _transactions.value, deadlineOverride = newDeadline)
                rebuildChatModel()
                delay(500)
            } catch (e: Exception) { Log.e(TAG, "Error updating deadline: ${e.message}") }
            finally { _isUpdatingDebt.value = false }
        }
    }

    fun updateTotalDebtFromSettings(newTotalDebt: Long) {
        viewModelScope.launch {
            try {
                if (newTotalDebt <= 0) return@launch
                _isUpdatingDebt.value = true
                _totalDebt.value = newTotalDebt
                withContext(Dispatchers.IO) { preferencesRepository.saveCustomTotalDebt(newTotalDebt) }
                recalculateDebtState(transactions = _transactions.value, totalDebtOverride = newTotalDebt)
                rebuildChatModel()
                delay(500)
            } catch (e: Exception) { Log.e(TAG, "Error updating total debt: ${e.message}") }
            finally { _isUpdatingDebt.value = false }
        }
    }

    fun completeOnboarding(totalDebt: Long, deadline: String) {
        viewModelScope.launch {
            _isDataReady.value = false
            _pendingFirstSetupGreeting.value = true
            withContext(Dispatchers.IO) { chatMessageRepository.deleteAllMessages() }
            _messages.value = emptyList()
            _totalDebt.value = totalDebt
            _customDeadline.value = deadline
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            _setupDate.value = today
            withContext(Dispatchers.IO) {
                preferencesRepository.saveCustomTotalDebt(totalDebt)
                preferencesRepository.saveCustomDeadline(deadline)
                preferencesRepository.saveInitialDeadline(deadline)
                preferencesRepository.saveSetupDate(today)
                preferencesRepository.setOnboardingDone()
            }
            val transactions = withContext(Dispatchers.IO) { repository.allTransactions.firstOrNull() ?: emptyList() }
            _transactions.value = transactions
            recalculateDebtState(transactions = transactions, deadlineOverride = deadline, totalDebtOverride = totalDebt)
            rebuildChatModel()
            _isDataReady.value = true
        }
    }

    fun saveReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveReminderTime(hour, minute)
            com.hyse.debtslayer.notification.DailyReminderScheduler.schedule(context)
        }
    }

    fun testNotification() {
        com.hyse.debtslayer.notification.DailyReminderScheduler.sendTestNotification(context)
    }

    fun resetDeadline() {
        viewModelScope.launch {
            _customDeadline.value = null
            withContext(Dispatchers.IO) { preferencesRepository.clearCustomDeadline() }
            recalculateDebtState(transactions = _transactions.value)
            rebuildChatModel()
        }
    }
}