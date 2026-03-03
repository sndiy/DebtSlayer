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

enum class LoadingStatus {
    IDLE,
    CONNECTING,           // "Menghubungi Mai..."
    WAITING_RESPONSE,     // "Mai sedang mengetik..."
    FALLBACK_TRYING,      // "Model utama limit, beralih ke cadangan..."
    FALLBACK_CONNECTING,  // "Menghubungi model cadangan..."
    ERROR_BOTH_LIMIT      // "Semua model sedang limit, mode offline aktif."
}

// Enum untuk melacak model aktif saat ini

enum class ActiveModel { LITE, FLASH }

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

    private val _loadingStatus = MutableStateFlow(LoadingStatus.IDLE)
    val loadingStatus: StateFlow<LoadingStatus> = _loadingStatus.asStateFlow()

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

    private val _depositConfirmation = MutableStateFlow<Long?>(null)
    val depositConfirmation: StateFlow<Long?> = _depositConfirmation.asStateFlow()

    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()

    private val _loadingStep = MutableStateFlow(0)
    val loadingStep: StateFlow<Int> = _loadingStep.asStateFlow()

    private val _pendingFirstSetupGreeting = MutableStateFlow(false)
    val pendingFirstSetupGreeting: StateFlow<Boolean> = _pendingFirstSetupGreeting.asStateFlow()

    val isOnboardingDone = preferencesRepository.isOnboardingDone
    val initialDeadline = preferencesRepository.initialDeadline

    private val _setupDate = MutableStateFlow<String?>(null)
    val setupDate: StateFlow<String?> = _setupDate.asStateFlow()

    val reminderHour = preferencesRepository.reminderHour
    val reminderMinute = preferencesRepository.reminderMinute

    private lateinit var chatModel: GenerativeModel       // Gemini 2.5 Flash Lite
    private lateinit var chatModelFallback: GenerativeModel // Gemini 2.5 Flash

    // ── Smart model switching ─────────────────────────────────────────────────
    // Ingat model terakhir yang berhasil — request berikutnya mulai dari sini.
    // Saat rate limit, selang-seling ke model lain tanpa animasi berulang.
    private val _activeModel = MutableStateFlow(ActiveModel.LITE)
    val activeModel: StateFlow<ActiveModel> = _activeModel.asStateFlow()

    private var lastUserMessage = ""
    private var lastAiResponse = ""
    private var activeMessageJob: Job? = null

    private var lastRequestFinishedAt = 0L
    private val MIN_REQUEST_INTERVAL_MS = 4_000L

    companion object {
        private const val TAG = "DebtViewModel"
        const val MODEL_NAME = "gemini-2.5-flash-lite"
        const val MODEL_NAME_FALLBACK = "gemini-2.5-flash"
        private const val REQUEST_TIMEOUT_MS = 30_000L

        val MODEL_LIMITS = ModelLimits(
            rpm = 10, rpd = 20, tpm = 250_000, tpd = 1_000_000,
            displayName = "Gemini 2.5 Flash Lite"
        )
        val MODEL_LIMITS_FALLBACK = ModelLimits(
            rpm = 5, rpd = 20, tpm = 250_000, tpd = 1_000_000,
            displayName = "Gemini 2.5 Flash"
        )

        fun isRateLimitError(e: Exception): Boolean {
            val msg = e.message ?: ""
            return msg.contains("429") ||
                    msg.contains("quota", true) ||
                    msg.contains("RESOURCE_EXHAUSTED", true) ||
                    msg.contains("rate", true)
        }
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
            _loadingStep.value = 0; loadPreferences()
            _loadingStep.value = 1
            val firstTransactions = withContext(Dispatchers.IO) {
                repository.allTransactions.firstOrNull() ?: emptyList()
            }
            _transactions.value = firstTransactions
            recalculateDebtState(firstTransactions)
            _loadingStep.value = 2; loadChatHistory()
            _loadingStep.value = 3; initAIModel(apiKey)
            _loadingStep.value = 4; loadFeedbackStats()
            _loadingStep.value = 5; cleanupOldData()
            _loadingStep.value = 6; delay(100)
            _isDataReady.value = true
        }
        viewModelScope.launch {
            repository.allTransactions.collect { transactionList ->
                _transactions.value = transactionList
                recalculateDebtState(transactionList)
            }
        }
    }

    private fun buildGenerativeModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.8f; topK = 30; topP = 0.9f; maxOutputTokens = 500
            },
            systemInstruction = content { text(getSystemInstruction()) }
        )
    }

    private fun initAIModel(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            Log.w(TAG, "API Key not configured"); return
        }
        try {
            chatModel = buildGenerativeModel(MODEL_NAME)
            chatModelFallback = buildGenerativeModel(MODEL_NAME_FALLBACK)
            Log.d(TAG, "AI models initialized: $MODEL_NAME + $MODEL_NAME_FALLBACK")
        } catch (e: Exception) { Log.e(TAG, "Error creating AI model: ${e.message}") }
    }

    fun sendFirstSetupGreeting() {
        viewModelScope.launch {
            addSystemMessage(buildDynamicGreeting())
            _pendingFirstSetupGreeting.value = false
        }
    }

    fun sendInitialGreeting() {
        viewModelScope.launch {
            val hasTodayChat = withContext(Dispatchers.IO) { chatMessageRepository.hasTodayMessages() }
            if (!hasTodayChat) { delay(100); addSystemMessage(buildDynamicGreeting()) }
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
                try { _personalityMode.value = AdaptiveMaiPersonality.PersonalityMode.valueOf(savedMode) }
                catch (e: IllegalArgumentException) { Log.e(TAG, "Unknown personality mode: $savedMode") }
            }
            val savedDeadline = preferencesRepository.customDeadline.firstOrNull()
            if (!savedDeadline.isNullOrBlank()) _customDeadline.value = savedDeadline
            _positiveFeedbackCount.value = preferencesRepository.positiveFeedbackCount.firstOrNull() ?: 0
            _negativeFeedbackCount.value = preferencesRepository.negativeFeedbackCount.firstOrNull() ?: 0
            val savedTotalDebt = preferencesRepository.customTotalDebt.firstOrNull()
            if (savedTotalDebt != null && savedTotalDebt > 0) _totalDebt.value = savedTotalDebt
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            val savedSetupDate = preferencesRepository.setupDate.firstOrNull()
            if (!savedSetupDate.isNullOrBlank()) _setupDate.value = savedSetupDate
        } catch (e: Exception) { Log.e(TAG, "Error loading preferences: ${e.message}") }
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
                totalDebt = activeTotalDebt, totalPaid = totalPaid, remainingDebt = remaining,
                progressPercentage = progress, daysRemaining = daysRemaining, dailyTarget = dailyTarget
            )
            if (remaining <= 0L) com.hyse.debtslayer.notification.DailyReminderScheduler.cancel(context)
            else com.hyse.debtslayer.notification.DailyReminderScheduler.schedule(context)
        } catch (e: Exception) { Log.e(TAG, "Error in recalculateDebtState: ${e.message}", e) }
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
        2. GREETING: balas singkat, tanya sudah setor → [ACTION:NONE]
        3. TANYA STATUS: ceritakan progress → [ACTION:NONE]
        4. CURHAT: empati tipis, dorong setor → [ACTION:NONE]
        5. HUTANG LUNAS: akui dengan gaya tsundere, emosi "bocor" → [ACTION:NONE]
        6. LAINNYA: tetap sebagai Mai → [ACTION:NONE]
        
        ══════════════════════════════════════
        ATURAN ACTION TAG:
        ══════════════════════════════════════
        • WAJIB ada di setiap respons, di baris PALING AKHIR
        • Format: [ACTION:DEPOSIT:50000] bukan [action:deposit:50000]
        
        CONTOH:
        User: "setor 2" → Mai: 2 rupiah. Aku catat, walaupun... ya sudahlah.\n[ACTION:DEPOSIT:2]
        User: "setor 50rb" → Mai: Hmm, lumayan.\n[ACTION:DEPOSIT:50000]
        User: "34112" → Mai: Mau setor? Bilang 'setor 34.112' dulu.\n[ACTION:NONE]
        User: "halo mai" → Mai: Ya, ada apa. Sudah setor?\n[ACTION:NONE]
    """.trimIndent()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCAL FALLBACK — digunakan saat kedua model API limit
    // Memproses perintah setor, info, dan chat tsundere statis
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Parse nominal uang dari teks. Mendukung: 10rb, 20ribu, 1jt, 1.5jt, 50000, dst.
     * Return null jika tidak bisa diparse.
     */
    private fun parseAmount(input: String): Long? {
        val clean = input.trim().lowercase()
            .replace(".", "").replace(",", "")
        val patterns = listOf(
            // 1.5jt / 1,5jt / 1.5 juta
            Regex("""(\d+)[.,](\d+)\s*j(?:t|uta)?""") to { m: MatchResult ->
                val int = m.groupValues[1].toLongOrNull() ?: return@to null
                val dec = m.groupValues[2].toLongOrNull() ?: return@to null
                int * 1_000_000L + dec * 100_000L
            },
            // 1jt / 2juta
            Regex("""(\d+)\s*j(?:t|uta)?""") to { m: MatchResult ->
                (m.groupValues[1].toLongOrNull() ?: return@to null) * 1_000_000L
            },
            // 50rb / 50ribu / 50k
            Regex("""(\d+)\s*(?:rb|ribu|k)""") to { m: MatchResult ->
                (m.groupValues[1].toLongOrNull() ?: return@to null) * 1_000L
            },
            // angka murni
            Regex("""^(\d+)$""") to { m: MatchResult ->
                m.groupValues[1].toLongOrNull()
            }
        )
        for ((regex, handler) in patterns) {
            val match = regex.find(clean) ?: continue
            val result = handler(match)
            if (result != null && result > 0) return result
        }
        return null
    }

    /**
     * Bangun teks ringkasan hutang yang rapi untuk ditampilkan saat mode offline.
     */
    private fun buildDebtSummary(): String {
        val state = _debtState.value
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val todayDeposit = _transactions.value
            .filter { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it.date)) == today }
            .sumOf { it.amount }
        val progressBar = buildProgressBar(state.progressPercentage)

        return """
📊 Ringkasan Hutang
━━━━━━━━━━━━━━━━━━━━
💰 Total Hutang      : ${CurrencyFormatter.format(state.totalDebt)}
✅ Total Dibayar     : ${CurrencyFormatter.format(state.totalPaid)}
🔴 Sisa Hutang       : ${CurrencyFormatter.format(state.remainingDebt)}
━━━━━━━━━━━━━━━━━━━━
📅 Hari Tersisa      : ${state.daysRemaining} hari
🎯 Target Hari Ini   : ${CurrencyFormatter.format(state.dailyTarget)}
📆 Setor Hari Ini    : ${CurrencyFormatter.format(todayDeposit)}
━━━━━━━━━━━━━━━━━━━━
$progressBar ${String.format("%.1f", state.progressPercentage)}% Lunas
        """.trimIndent()
    }

    private fun buildProgressBar(percent: Float): String {
        val filled = (percent / 10).toInt().coerceIn(0, 10)
        return "▓".repeat(filled) + "░".repeat(10 - filled)
    }

    /**
     * Proses pesan secara lokal tanpa API.
     * Menangani: setor, info/status, dan chat biasa.
     */
    private suspend fun handleOfflineMessage(userMessage: String) {
        val lower = userMessage.lowercase().trim()

        val isDepositCommand = listOf("setor", "nabung", "bayar", "cicil", "transfer", "masuk")
            .any { lower.contains(it) }

        val isInfoCommand = listOf("info", "status", "cek", "ringkasan", "summary", "hutang")
            .any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }

        val isHelpCommand = lower == "help" || lower == "bantuan" || lower == "?"

        when {
            // ── Help ─────────────────────────────────────────────────────────
            isHelpCommand -> {
                val helpText = """
⚡ Mode Offline Aktif
━━━━━━━━━━━━━━━━━━━━
Kedua model AI sedang limit. Perintah yang tersedia:

💰 SETOR
  • setor 50rb
  • nabung 100000
  • bayar 1jt
  • cicil 500ribu

📊 INFO
  • info
  • status
  • cek

❓ BANTUAN
  • help

━━━━━━━━━━━━━━━━━━━━
AI normal kembali besok jam 07:00 WIB.
                """.trimIndent()
                addAiMessage(helpText)
                withContext(Dispatchers.IO) { saveConversation(userMessage, helpText, true) }
            }
            // ── Setor ────────────────────────────────────────────────────────
            isDepositCommand -> {
                val amount = parseAmount(lower)
                if (amount != null && amount > 0) {
                    withContext(Dispatchers.IO) { saveTransaction(amount, "Chat offline") }
                    _depositConfirmation.value = amount
                    val response = listOf(
                        "${CurrencyFormatter.format(amount)} dicatat. Hmph, lumayan.",
                        "Oke, ${CurrencyFormatter.format(amount)} masuk. Jangan berhenti di sini.",
                        "${CurrencyFormatter.format(amount)} tersimpan. Sisa ${CurrencyFormatter.format(_debtState.value.remainingDebt)}."
                    ).random()
                    addAiMessage(response)
                    withContext(Dispatchers.IO) { saveConversation(userMessage, response, true) }
                } else {
                    val hint = "Format: 'setor 50rb', 'setor 100000', 'setor 1jt'. Coba lagi."
                    addAiMessage(hint)
                    withContext(Dispatchers.IO) { saveConversation(userMessage, hint, true) }
                }
            }

            // ── Info / Status ─────────────────────────────────────────────────
            isInfoCommand -> {
                val summary = buildDebtSummary()
                addAiMessage(summary)
                withContext(Dispatchers.IO) { saveConversation(userMessage, summary, true) }
            }

            // ── Chat biasa → tampilkan info hutang ───────────────────────────
            else -> {
                val lower2 = userMessage.lowercase()
                val response = when {
                    lower2.contains("halo") || lower2.contains("hai") || lower2.contains("hello") ||
                            lower2.contains("hi") || lower2.contains("p") ->
                        listOf(
                            "Ya, ada apa. Sudah setor hari ini?",
                            "Hmm. Ngapain sapa-sapa, hutangmu masih numpuk.",
                            "Ada yang perlu diurus? Kalau tidak, setor dulu sana."
                        ).random()

                    lower2.contains("makasih") || lower2.contains("terima kasih") ->
                        "Jangan makasih dulu sebelum hutangnya lunas."

                    lower2.contains("semangat") || lower2.contains("ayo") ->
                        "Semangat saja tidak cukup. Butuh uangnya juga."

                    lower2.contains("capek") || lower2.contains("lelah") || lower2.contains("stress") ->
                        "Capek boleh, tapi hutang tidak ikut capek. Istirahat sebentar, lanjut lagi."

                    else ->
                        "Ketik 'info' untuk lihat status hutang, atau 'setor [nominal]' untuk mencatat setoran."
                }
                addAiMessage(response)
                withContext(Dispatchers.IO) { saveConversation(userMessage, response, true) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════

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
            } catch (e: Exception) { Log.e(TAG, "Error loading feedback: ${e.message}") }
        }
    }

    private suspend fun loadChatHistory() {
        try {
            val todayMessages = withContext(Dispatchers.IO) { chatMessageRepository.getTodayMessages() }
            if (todayMessages.isNotEmpty()) {
                _messages.value = todayMessages.map { entity ->
                    ChatMessage(
                        text = entity.text, isFromUser = entity.isFromUser,
                        timestamp = entity.timestamp, messageId = entity.id,
                        feedbackGiven = entity.feedbackGiven, feedbackIsPositive = entity.feedbackIsPositive
                    )
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error loading chat history: ${e.message}") }
    }

    fun dismissDepositConfirmation() { _depositConfirmation.value = null }

    fun cancelPendingMessage() {
        if (activeMessageJob?.isActive == true) {
            activeMessageJob?.cancel()
            Log.d(TAG, "Active message job cancelled by user")
        }
    }

    private fun buildContextMessage(userMessage: String): String {
        val s = _debtState.value
        return """
            User: $userMessage
            Status hutang sekarang:
            - Sudah dibayar: ${CurrencyFormatter.format(s.totalPaid)}
            - Sisa hutang: ${CurrencyFormatter.format(s.remainingDebt)}
            - Target hari ini: ${CurrencyFormatter.format(s.dailyTarget)}
            - Hari tersisa: ${s.daysRemaining} hari
            Respons sebagai Mai (max 2 kalimat, tsundere, cool).
            WAJIB tambahkan [ACTION:TIPE:NILAI] di baris terakhir.
        """.trimIndent()
    }

    private suspend fun processAiResponse(userMessage: String, aiResponse: String) {
        lastAiResponse = aiResponse
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
                else -> Log.d(TAG, "Unknown action: ${match.groupValues[1]}")
            }

        }
        addAiMessage(cleanResponse.ifBlank { aiResponse })
    }

    /**
     * Coba generate content dari satu model.
     * Return response-nya jika berhasil.
     * Throw exception jika gagal (caller yang handle).
     */
    private suspend fun tryGenerate(
        model: GenerativeModel,
        contextMessage: String
    ): com.google.ai.client.generativeai.type.GenerateContentResponse {
        return withContext(Dispatchers.IO) {
            withTimeout(REQUEST_TIMEOUT_MS) {
                model.generateContent(content { text(contextMessage) })
            }
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        if (_isLoading.value) return

        val sinceLastFinished = System.currentTimeMillis() - lastRequestFinishedAt
        if (lastRequestFinishedAt != 0L && sinceLastFinished < MIN_REQUEST_INTERVAL_MS) return

        activeMessageJob?.cancel()
        activeMessageJob = viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            _loadingStatus.value = LoadingStatus.CONNECTING

            try {
                addUserMessage(userMessage)
                lastUserMessage = userMessage

                if (!::chatModel.isInitialized) {
                    addAiMessage("AI belum siap. Pastikan API Key sudah dikonfigurasi di local.properties.")
                    return@launch
                }
                if (!withContext(Dispatchers.IO) { isNetworkAvailable() }) {
                    _loadingStatus.value = LoadingStatus.ERROR_BOTH_LIMIT
                    handleOfflineMessage(userMessage)  // ← proses lokal: setor, info, help, dll
                    return@launch
                }

                val contextMessage = buildContextMessage(userMessage)

                val (primaryModel, secondaryModel, primaryEnum, secondaryEnum) =
                    if (_activeModel.value == ActiveModel.LITE)
                        Quad(chatModel, chatModelFallback, ActiveModel.LITE, ActiveModel.FLASH)
                    else
                        Quad(chatModelFallback, chatModel, ActiveModel.FLASH, ActiveModel.LITE)

                // ── Coba model prioritas ──────────────────────────────────────
                _loadingStatus.value = LoadingStatus.WAITING_RESPONSE
                var response: com.google.ai.client.generativeai.type.GenerateContentResponse? = null

                try {
                    response = tryGenerate(primaryModel, contextMessage)
                    // Sukses — simpan preferensi model ini
                    _activeModel.value = primaryEnum
                } catch (primaryEx: Exception) {
                    if (primaryEx is CancellationException) throw primaryEx
                    if (primaryEx is TimeoutCancellationException) throw primaryEx
                    if (!isRateLimitError(primaryEx)) throw primaryEx

                    // Rate limit di model prioritas → selang ke model lain (tanpa delay panjang)
                    Log.w(TAG, "Primary model ($primaryEnum) rate limited, trying $secondaryEnum")
                    _loadingStatus.value = LoadingStatus.FALLBACK_TRYING
                    delay(500) // cukup untuk user membaca status
                    _loadingStatus.value = LoadingStatus.FALLBACK_CONNECTING
                    delay(400)
                    _loadingStatus.value = LoadingStatus.WAITING_RESPONSE

                    try {
                        response = tryGenerate(secondaryModel, contextMessage)
                        // Fallback berhasil — ganti preferensi ke model ini agar request berikutnya langsung ke sini
                        _activeModel.value = secondaryEnum
                        Log.d(TAG, "Switched preferred model to $secondaryEnum")
                    } catch (secondaryEx: Exception) {
                        if (secondaryEx is CancellationException) throw secondaryEx
                        if (secondaryEx is TimeoutCancellationException) throw secondaryEx

                        // Kedua model gagal
                        if (isRateLimitError(secondaryEx)) {
                            // Kedua limit → gunakan local fallback
                            _loadingStatus.value = LoadingStatus.ERROR_BOTH_LIMIT
                            Log.w(TAG, "Both models rate limited, switching to local fallback")

                            // Notifikasi singkat bahwa mode offline aktif
                            addAiMessage(
                                "⚠️ Kedua model AI limit hari ini. Mode offline aktif — " +
                                        "kamu masih bisa setor, atau ketik 'info' untuk lihat status."
                            )
                            // Langsung proses pesan secara lokal
                            handleOfflineMessage(userMessage)
                            return@launch
                        } else {
                            throw secondaryEx // error lain, bukan rate limit
                        }
                    }
                }

                // Jika ada response dari API
                response?.let {
                    processAiResponse(userMessage, it.text ?: "Maaf, aku tidak bisa merespons.")
                }

            } catch (e: TimeoutCancellationException) {
                addAiMessage("Koneksi terputus saat menunggu respons. Cek internet lalu coba lagi.")
                withContext(Dispatchers.IO) { saveConversation(userMessage, "timeout", false) }
            } catch (e: CancellationException) {
                Log.d(TAG, "Message job cancelled by user")
            } catch (e: Exception) {
                val errorMessage = handleApiError(e)
                addAiMessage(errorMessage)
                withContext(Dispatchers.IO) { saveConversation(userMessage, errorMessage, false) }
            } finally {
                lastRequestFinishedAt = System.currentTimeMillis()
                _isLoading.value = false
                _loadingStatus.value = LoadingStatus.IDLE
            }
        }
    }

    // Helper data class untuk destructuring 4 nilai
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun getMillisUntilMidnightUtc(): Long {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
    }

    private fun handleApiError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("429") || msg.contains("quota", true) ||
                    msg.contains("rate", true) || msg.contains("RESOURCE_EXHAUSTED", true) -> {
                val isRpdLimit = msg.contains("per_day", true) || msg.contains("daily", true) ||
                        msg.contains("RPD", true) || (msg.contains("quota", true) && msg.contains("day", true))
                if (isRpdLimit) {
                    "Limit harian API habis. Reset jam 07:00 WIB. " +
                            "Upgrade billing di Google AI Studio untuk limit lebih tinggi."
                } else {
                    "Terlalu banyak pesan dalam 1 menit. Tunggu sebentar lalu coba lagi."
                }
            }
            msg.contains("API key", true) || msg.contains("401") ->
                "API Key tidak valid. Cek GEMINI_API_KEY di local.properties."
            msg.contains("403") -> "Akses ditolak. Pastikan API Key punya izin untuk Gemini API."
            msg.contains("404") -> "Model tidak ditemukan. Cek nama model atau koneksi internet."
            e is java.net.UnknownHostException -> "Tidak bisa konek ke Gemini. Cek koneksi internet."
            e is java.net.SocketTimeoutException -> "Koneksi timeout. Coba lagi."
            msg.contains("500") || msg.contains("503") ->
                "Server Gemini sedang bermasalah. Tunggu sebentar lalu coba lagi."
            else -> "Error: ${msg.take(120)}. Coba lagi ya!"
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
            conversationRepository.insert(ConversationHistory(
                userMessage = userMsg, aiResponse = aiResp,
                timestamp = System.currentTimeMillis(), wasSuccessful = wasSuccessful, context = ""
            ))
        } catch (e: Exception) { Log.e(TAG, "Error saving conversation: ${e.message}") }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { chatMessageRepository.deleteAllMessages() }
            _messages.value = emptyList()
        }
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
                    feedbackRepository.insert(ConversationFeedback(
                        userMessage = lastUserMessage, aiResponse = lastAiResponse,
                        isPositive = isPositive, timestamp = System.currentTimeMillis(), context = ctx
                    ))
                }
                if (isPositive) _positiveFeedbackCount.value += 1
                else _negativeFeedbackCount.value += 1
            } catch (e: Exception) { Log.e(TAG, "Error saving feedback: ${e.message}") }
        }
    }

    fun setPersonalityMode(mode: AdaptiveMaiPersonality.PersonalityMode) {
        _personalityMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { preferencesRepository.savePersonalityMode(mode.name) }
        rebuildChatModel()
    }

    private fun rebuildChatModel() {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") return
        try {
            chatModel = buildGenerativeModel(MODEL_NAME)
            chatModelFallback = buildGenerativeModel(MODEL_NAME_FALLBACK)
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
            _loadingStep.value = 0
            withContext(Dispatchers.IO) { chatMessageRepository.deleteAllMessages() }
            _messages.value = emptyList()
            _totalDebt.value = totalDebt
            _customDeadline.value = deadline
            _loadingStep.value = 1
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            _setupDate.value = today
            withContext(Dispatchers.IO) {
                preferencesRepository.saveCustomTotalDebt(totalDebt)
                preferencesRepository.saveCustomDeadline(deadline)
                preferencesRepository.saveInitialDeadline(deadline)
                preferencesRepository.saveSetupDate(today)
                preferencesRepository.setOnboardingDone()
            }
            _loadingStep.value = 2
            val transactions = withContext(Dispatchers.IO) {
                repository.allTransactions.firstOrNull() ?: emptyList()
            }
            _transactions.value = transactions
            recalculateDebtState(transactions = transactions, deadlineOverride = deadline, totalDebtOverride = totalDebt)
            _loadingStep.value = 3; rebuildChatModel()
            _loadingStep.value = 4; loadFeedbackStats()
            _loadingStep.value = 5; cleanupOldData()
            _loadingStep.value = 6; delay(100)
            _isDataReady.value = true
        }
    }

    /**
     * Dipanggil setelah login berhasil dan data cloud ditemukan.
     * Berbeda dengan completeOnboarding() yang menjalankan full loading sequence,
     * fungsi ini hanya apply data ke lokal & tandai onboarding selesai —
     * tanpa reset chat atau trigger loading screen onboarding.
     */
    fun applyCloudDataAndCompleteOnboarding(totalDebt: Long, deadline: String) {
        viewModelScope.launch {
            try {
                // Apply data ke state
                _totalDebt.value      = totalDebt
                _customDeadline.value = deadline

                // Simpan ke DataStore lokal
                withContext(Dispatchers.IO) {
                    preferencesRepository.saveCustomTotalDebt(totalDebt)
                    preferencesRepository.saveCustomDeadline(deadline)
                    preferencesRepository.saveInitialDeadline(deadline)
                    preferencesRepository.setOnboardingDone()   // ← kunci: tandai setup selesai
                }

                // Recalculate state hutang dengan data yang baru
                val transactions = withContext(Dispatchers.IO) {
                    repository.allTransactions.firstOrNull() ?: emptyList()
                }
                _transactions.value = transactions
                recalculateDebtState(
                    transactions      = transactions,
                    deadlineOverride  = deadline,
                    totalDebtOverride = totalDebt
                )

                // Rebuild AI model dengan context hutang terbaru
                rebuildChatModel()

                Log.d(TAG, "Cloud data applied: debt=$totalDebt, deadline=$deadline")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying cloud data: ${e.message}", e)
            }
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

    // 🆕 Expose repository untuk CloudSyncRepository
    fun getTransactionRepository(): TransactionRepository = repository
}