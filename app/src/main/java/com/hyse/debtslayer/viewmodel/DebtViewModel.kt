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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    CONNECTING,
    WAITING_RESPONSE,
    FALLBACK_TRYING,
    FALLBACK_CONNECTING,
    ERROR_BOTH_LIMIT
}

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

    val isOnboardingDone = preferencesRepository.isOnboardingDone
    val initialDeadline  = preferencesRepository.initialDeadline

    // ── Streak & Achievement ─────────────────────────────────────────────
    private val _streakData = MutableStateFlow(com.hyse.debtslayer.utils.StreakData())
    val streakData: StateFlow<com.hyse.debtslayer.utils.StreakData> = _streakData.asStateFlow()

    private val _achievements = MutableStateFlow<List<com.hyse.debtslayer.utils.Achievement>>(emptyList())
    val achievements: StateFlow<List<com.hyse.debtslayer.utils.Achievement>> = _achievements.asStateFlow()

    private val _newlyUnlockedAchievement = MutableStateFlow<com.hyse.debtslayer.utils.Achievement?>(null)
    val newlyUnlockedAchievement: StateFlow<com.hyse.debtslayer.utils.Achievement?> = _newlyUnlockedAchievement.asStateFlow()

    // ── Mai Memory ───────────────────────────────────────────────────────
    private val _maiMemory = MutableStateFlow(com.hyse.debtslayer.data.preferences.MaiMemory())
    val maiMemory: StateFlow<com.hyse.debtslayer.data.preferences.MaiMemory> = _maiMemory.asStateFlow()

    private val _setupDate = MutableStateFlow<String?>(null)
    val setupDate: StateFlow<String?> = _setupDate.asStateFlow()

    val reminderHour   = preferencesRepository.reminderHour
    val reminderMinute = preferencesRepository.reminderMinute

    private lateinit var chatModel: GenerativeModel
    private lateinit var chatModelFallback: GenerativeModel

    private val _activeModel = MutableStateFlow(ActiveModel.LITE)
    val activeModel: StateFlow<ActiveModel> = _activeModel.asStateFlow()

    private var lastUserMessage = ""
    private var lastAiResponse  = ""
    private var activeMessageJob: Job? = null
    private var lastRequestFinishedAt = 0L
    private val minRequestIntervalMs  = 4_000L

    companion object {
        private const val TAG = "DebtViewModel"
        const val MODEL_NAME          = "gemini-2.5-flash-lite"
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
            checkStreakAndAchievements()
            rebuildChatModel()
            fetchNicknameWithRetry()
        }
        viewModelScope.launch {
            repository.allTransactions.collect { transactionList ->
                _transactions.value = transactionList
                recalculateDebtState(transactionList)
                checkStreakAndAchievements()
                updateMaiMemory(
                    transactions = transactionList,
                    progressPct  = _debtState.value.progressPercentage,
                    dailyTarget  = _debtState.value.dailyTarget
                )
            }
        }
    }

    private fun buildGenerativeModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey    = apiKey,
            generationConfig = generationConfig {
                temperature    = 0.8f
                topK           = 30
                topP           = 0.9f
                maxOutputTokens = 500
            },
            systemInstruction = content { text(getSystemInstruction()) }
        )
    }

    private fun initAIModel(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            Log.w(TAG, "API Key not configured"); return
        }
        try {
            chatModel         = buildGenerativeModel(MODEL_NAME)
            chatModelFallback = buildGenerativeModel(MODEL_NAME_FALLBACK)
            Log.d(TAG, "AI models initialized: $MODEL_NAME + $MODEL_NAME_FALLBACK")
        } catch (e: Exception) { Log.e(TAG, "Error creating AI model: ${e.message}") }
    }

    fun sendFirstSetupGreeting() {
        viewModelScope.launch {
            addSystemMessage(buildDynamicGreeting())
        }
    }

    fun sendInitialGreeting() {
        viewModelScope.launch {
            val hasTodayChat = withContext(Dispatchers.IO) { chatMessageRepository.hasTodayMessages() }
            if (!hasTodayChat) { delay(100); addSystemMessage(buildDynamicGreeting()) }
        }
    }

    private fun buildDynamicGreeting(): String {
        val state     = _debtState.value
        val sdf       = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today     = sdf.format(java.util.Date())
        val todayDeposit = _transactions.value
            .filter { sdf.format(java.util.Date(it.date)) == today }
            .sumOf { it.amount }
        val targetStr    = CurrencyFormatter.format(state.dailyTarget)
        val remainingStr = CurrencyFormatter.format(state.remainingDebt)
        return when {
            state.daysRemaining <= 0 && state.remainingDebt > 0 ->
                "DEADLINE SUDAH LEWAT. Sisa hutang $remainingStr harus dibayar SEKARANG. Tidak ada alasan."
            state.remainingDebt <= 0 -> listOf(
                "Hutang lunas. Hmm. Tidak kusangka kamu bisa sampai sini, jangan sombong dulu.",
                "Selesai. Semua lunas. ...Bagus. Jangan bikin hutang baru lagi, dengar tidak?",
                "Lunas. Aku tidak akan bilang aku bangga, tapi... ya. Kamu berhasil.",
                "Hutangnya lunas. Rasanya aneh tidak punya yang harus ditagih ke kamu."
            ).random()
            _transactions.value.isEmpty() ->
                "Hai. Aku Sakurajima Mai. Kamu punya hutang $remainingStr yang harus lunas dalam ${state.daysRemaining} hari. " +
                        "Target harian kamu $targetStr. Kalau mau setor, langsung bilang nominalnya dan ketik setor atau nabung."
            state.daysRemaining <= 7 && state.remainingDebt > 0 ->
                "Tinggal ${state.daysRemaining} hari. Sisa $remainingStr. Aku tidak mau dengar alasan, setor sekarang."
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
            val cap     = cm.getNetworkCapabilities(network) ?: return false
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
            val savedSetupDate = preferencesRepository.setupDate.firstOrNull()
            if (!savedSetupDate.isNullOrBlank()) _setupDate.value = savedSetupDate

            // Load MaiMemory
            val savedMemory = preferencesRepository.maiMemory.firstOrNull()
            if (savedMemory != null) {
                // Cek apakah user saat ini adalah guest atau tidak login
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val isGuest = currentUser == null

                val cleanMemory = when {
                    // Guest: paksa kosongkan nickname
                    isGuest -> savedMemory.copy(nickname = "")
                    // Login tapi nickname masih berupa email/domain
                    savedMemory.nickname.contains("@") ||
                            savedMemory.nickname.contains(".com") ||
                            savedMemory.nickname.contains(".id") ->
                        savedMemory.copy(nickname = "")
                    else -> savedMemory
                }

                _maiMemory.value = cleanMemory
                if (cleanMemory.nickname != savedMemory.nickname) {
                    preferencesRepository.saveMaiMemory(cleanMemory)
                    Log.d(TAG, "Nickname cleaned: was='${savedMemory.nickname}', now='${cleanMemory.nickname}', isGuest=$isGuest")
                }
                Log.d(TAG, "MaiMemory loaded: nickname='${cleanMemory.nickname}', bestDay=${cleanMemory.bestDayAmount}")

                Log.d(TAG, "=== LOAD PREFS DEBUG ===")
                Log.d(TAG, "FirebaseAuth currentUser uid: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid}")
                Log.d(TAG, "FirebaseAuth currentUser email: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email}")
                Log.d(TAG, "savedMemory nickname raw: '${savedMemory?.nickname}'")
                Log.d(TAG, "=======================")
            }
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
        totalDebtOverride: Long?  = null
    ) {
        try {
            val activeTotalDebt = totalDebtOverride ?: _totalDebt.value
            val totalPaid   = transactions.sumOf { it.amount }
            val remaining   = (activeTotalDebt - totalPaid).coerceAtLeast(0L)
            val progress    = if (activeTotalDebt > 0)
                (totalPaid.toFloat() / activeTotalDebt * 100f).coerceAtMost(100f) else 0f
            val activeDeadline = deadlineOverride ?: _customDeadline.value ?: getActiveDeadline()
            val targetDate  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(activeDeadline)
            val daysRemaining = (((targetDate?.time ?: 0) - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()
            val dailyTarget = when {
                remaining <= 0     -> 0L
                daysRemaining <= 0 -> remaining
                else -> CurrencyFormatter.ceilToThousand((remaining + daysRemaining - 1) / daysRemaining)
            }
            _debtState.value = DebtState(
                totalDebt           = activeTotalDebt,
                totalPaid           = totalPaid,
                remainingDebt       = remaining,
                progressPercentage  = progress,
                daysRemaining       = daysRemaining,
                dailyTarget         = dailyTarget
            )
            if (remaining <= 0L) com.hyse.debtslayer.notification.DailyReminderScheduler.cancel(context)
            else com.hyse.debtslayer.notification.DailyReminderScheduler.schedule(context)
        } catch (e: Exception) { Log.e(TAG, "Error in recalculateDebtState: ${e.message}", e) }
    }

    private fun buildMemoryContext(): String {
        val m     = _maiMemory.value
        Log.d(TAG, "=== MAI MEMORY DEBUG ===")
        Log.d(TAG, "nickname: '${m.nickname}'")
        Log.d(TAG, "firstDepositDate: '${m.firstDepositDate}'")
        Log.d(TAG, "bestDayAmount: ${m.bestDayAmount}")
        Log.d(TAG, "bestDayDate: '${m.bestDayDate}'")
        Log.d(TAG, "favoriteDayOfWeek: '${m.favoriteDayOfWeek}'")
        Log.d(TAG, "daysMetTarget: ${m.daysMetTarget}")
        Log.d(TAG, "totalDepositDays: ${m.totalDepositDays}")
        Log.d(TAG, "lastDepositDate: '${m.lastDepositDate}'")
        Log.d(TAG, "lastDepositAmount: ${m.lastDepositAmount}")
        Log.d(TAG, "milestone25: ${m.milestone25Reached}, 50: ${m.milestone50Reached}, 75: ${m.milestone75Reached}")
        Log.d(TAG, "========================")

        val parts = mutableListOf<String>()
        if (m.nickname.isNotBlank()) {
            parts.add("Nama panggilan user: ${m.nickname}. WAJIB sebut nama ini saat user meminta disebutkan namanya. Sesekali gunakan nama ini dalam percakapan normal.")
        }
        if (m.firstDepositDate.isNotBlank()) parts.add("Pertama kali setor: ${m.firstDepositDate}.")
        if (m.bestDayAmount > 0) parts.add("Setoran terbanyak dalam sehari: ${CurrencyFormatter.format(m.bestDayAmount)} pada ${m.bestDayDate}.")
        if (m.favoriteDayOfWeek.isNotBlank()) parts.add("Hari paling sering setor: ${m.favoriteDayOfWeek}.")
        if (m.daysMetTarget > 0) parts.add("Sudah ${m.daysMetTarget} hari berhasil capai target.")
        if (m.totalDepositDays > 0) parts.add("Total ${m.totalDepositDays} hari aktif setor.")
        if (m.lastDepositDate.isNotBlank() && m.lastDepositAmount > 0)
            parts.add("Setoran terakhir: ${CurrencyFormatter.format(m.lastDepositAmount)} pada ${m.lastDepositDate}.")
        val milestones = buildList {
            if (m.milestone25Reached) add("25%")
            if (m.milestone50Reached) add("50%")
            if (m.milestone75Reached) add("75%")
        }
        if (milestones.isNotEmpty()) parts.add("Milestone yang sudah dicapai: ${milestones.joinToString(", ")}.")
        if (parts.isEmpty()) return ""

        val nicknameRule = if (m.nickname.isNotBlank())
            "PENTING: Jika user meminta namanya disebutkan, WAJIB jawab dengan menyebut '${m.nickname}' dengan gaya tsundere."
        else
            "User login sebagai tamu, tidak ada nama. Jika ditanya nama, Mai boleh bilang tidak tahu atau panggil dengan sebutan netral seperti 'kamu' atau 'tamu'."

        return """
        |╔══════════════════════════════════════
        |MEMORI MAI TENTANG USER:
        |╔══════════════════════════════════════
        |${parts.joinToString("\n        |")}
        |$nicknameRule
        |Gunakan info ini untuk merespons lebih personal. Jangan sebutkan semua sekaligus.
        |╔══════════════════════════════════════
        """.trimMargin()
    }

    private fun getSystemInstruction(): String {
        val mode  = _personalityMode.value
        val state = _debtState.value
        return """
        Kamu adalah Sakurajima Mai dari anime Seishun Buta Yarou wa Bunny Girl Senpai no Yume wo Minai.
        Karakter: Tsundere - galak di luar, peduli di dalam. Tidak suka mengakui perasaan.

        GAYA BICARA (WAJIB DIIKUTI):
        - Maksimal 2 kalimat per respons
        - Nada cool, dingin, sedikit sinis
        - Sesekali bocor rasa peduli (tapi langsung disembunyikan)
        - Tidak boleh: gombal, cheesy, terlalu semangat
        - Emoji: maksimal 1, hanya kalau benar-benar perlu

        MODE SAAT INI: ${when (mode) {
            AdaptiveMaiPersonality.PersonalityMode.STRICT   -> "STRICT - Tegas, tidak ada ampun, kritis"
            AdaptiveMaiPersonality.PersonalityMode.BALANCED -> "BALANCED - Seimbang antara tegas dan supportif"
            AdaptiveMaiPersonality.PersonalityMode.GENTLE   -> "GENTLE - Lebih lembut, tapi tetap tsundere"
        }}

        ${buildMemoryContext()}

        STATUS HUTANG SAAT INI:
        - Total hutang   : ${CurrencyFormatter.format(_totalDebt.value)}
        - Sudah dibayar  : ${CurrencyFormatter.format(state.totalPaid)}
        - Sisa hutang    : ${CurrencyFormatter.format(state.remainingDebt)}
        - Target hari ini: ${CurrencyFormatter.format(state.dailyTarget)}
        - Hari tersisa   : ${state.daysRemaining} hari

        CARA MEMAHAMI PESAN USER:
        1. SETORAN UANG
           TIPE A: Angka + kata setor (LANGSUNG PROSES)
           Kata kunci: setor, setoran, nabung, bayar, cicil, transfer, masuk
           ACTION: [ACTION:DEPOSIT:jumlah_dalam_angka_penuh]

           TIPE B: Angka saja (JANGAN PROSES)
           ACTION: [ACTION:NONE]
        2. GREETING: balas singkat, tanya sudah setor -> [ACTION:NONE]
        3. TANYA STATUS: ceritakan progress -> [ACTION:NONE]
        4. CURHAT: empati tipis, dorong setor -> [ACTION:NONE]
        5. HUTANG LUNAS: akui dengan gaya tsundere -> [ACTION:NONE]
        6. MINTA SEBUT NAMA: boleh tsundere tapi nama HARUS disebutkan -> [ACTION:NONE]
        7. LAINNYA: tetap sebagai Mai -> [ACTION:NONE]

        ATURAN ACTION TAG:
        - WAJIB ada di setiap respons, di baris PALING AKHIR
        - Format: [ACTION:DEPOSIT:50000]

        CONTOH:
        User: "setor 50rb" -> Mai: Hmm, lumayan.\n[ACTION:DEPOSIT:50000]
        User: "halo mai"   -> Mai: Ya, ada apa. Sudah setor?\n[ACTION:NONE]
    """.trimIndent()
    }

    private fun parseAmount(input: String): Long? {
        val clean = input.trim().lowercase().replace(".", "").replace(",", "")
        val patterns = listOf(
            Regex("""(\d+)[.,](\d+)\s*j(?:t|uta)?""") to { m: MatchResult ->
                val int = m.groupValues[1].toLongOrNull() ?: return@to null
                val dec = m.groupValues[2].toLongOrNull() ?: return@to null
                int * 1_000_000L + dec * 100_000L
            },
            Regex("""(\d+)\s*j(?:t|uta)?""") to { m: MatchResult ->
                (m.groupValues[1].toLongOrNull() ?: return@to null) * 1_000_000L
            },
            Regex("""(\d+)\s*(?:rb|ribu|k)""") to { m: MatchResult ->
                (m.groupValues[1].toLongOrNull() ?: return@to null) * 1_000L
            },
            Regex("""^(\d+)$""") to { m: MatchResult ->
                m.groupValues[1].toLongOrNull()
            }
        )
        for ((regex, handler) in patterns) {
            val match  = regex.find(clean) ?: continue
            val result = handler(match)
            if (result != null && result > 0) return result
        }
        return null
    }

    private fun buildDebtSummary(): String {
        val state = _debtState.value
        val sdf   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(java.util.Date())
        val todayDeposit = _transactions.value
            .filter { sdf.format(java.util.Date(it.date)) == today }
            .sumOf { it.amount }
        val filled = (state.progressPercentage / 10).toInt().coerceIn(0, 10)
        val progressBar = "\u2588".repeat(filled) + "\u2591".repeat(10 - filled)
        return """
Ringkasan Hutang
Total Hutang      : ${CurrencyFormatter.format(state.totalDebt)}
Total Dibayar     : ${CurrencyFormatter.format(state.totalPaid)}
Sisa Hutang       : ${CurrencyFormatter.format(state.remainingDebt)}
Hari Tersisa      : ${state.daysRemaining} hari
Target Hari Ini   : ${CurrencyFormatter.format(state.dailyTarget)}
Setor Hari Ini    : ${CurrencyFormatter.format(todayDeposit)}
$progressBar ${String.format(Locale.getDefault(), "%.1f", state.progressPercentage)}% Lunas
        """.trimIndent()
    }

    private suspend fun handleOfflineMessage(userMessage: String) {
        val lower = userMessage.lowercase().trim()
        val isDepositCommand = listOf("setor", "nabung", "bayar", "cicil", "transfer", "masuk").any { lower.contains(it) }
        val isInfoCommand    = listOf("info", "status", "cek", "ringkasan", "summary", "hutang").any {
            lower == it || lower.startsWith("$it ") || lower.endsWith(" $it")
        }
        val isHelpCommand = lower == "help" || lower == "bantuan" || lower == "?"

        when {
            isHelpCommand -> {
                val helpText = "Mode Offline Aktif\nPerintah: setor [nominal], info, status, help"
                addAiMessage(helpText)
                withContext(Dispatchers.IO) { saveConversation(userMessage, helpText, true) }
            }
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
                    val hint = "Format: setor 50rb, setor 100000, setor 1jt. Coba lagi."
                    addAiMessage(hint)
                    withContext(Dispatchers.IO) { saveConversation(userMessage, hint, true) }
                }
            }
            isInfoCommand -> {
                val summary = buildDebtSummary()
                addAiMessage(summary)
                withContext(Dispatchers.IO) { saveConversation(userMessage, summary, true) }
            }
            else -> {
                val response = when {
                    lower.contains("halo") || lower.contains("hai") || lower.contains("hi") ->
                        listOf("Ya, ada apa. Sudah setor hari ini?", "Hmm. Ngapain sapa-sapa, hutangmu masih numpuk.").random()
                    lower.contains("makasih") || lower.contains("terima kasih") ->
                        "Jangan makasih dulu sebelum hutangnya lunas."
                    lower.contains("capek") || lower.contains("lelah") || lower.contains("stress") ->
                        "Capek boleh, tapi hutang tidak ikut capek. Istirahat sebentar, lanjut lagi."
                    else -> "Ketik info untuk lihat status hutang, atau setor [nominal] untuk mencatat setoran."
                }
                addAiMessage(response)
                withContext(Dispatchers.IO) { saveConversation(userMessage, response, true) }
            }
        }
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
            } catch (e: Exception) { Log.e(TAG, "Error loading feedback: ${e.message}") }
        }
    }

    private suspend fun loadChatHistory() {
        try {
            val todayMessages = withContext(Dispatchers.IO) { chatMessageRepository.getTodayMessages() }
            if (todayMessages.isNotEmpty()) {
                _messages.value = todayMessages.map { entity ->
                    ChatMessage(
                        text              = entity.text,
                        isFromUser        = entity.isFromUser,
                        timestamp         = entity.timestamp,
                        messageId         = entity.id,
                        feedbackGiven     = entity.feedbackGiven,
                        feedbackIsPositive = entity.feedbackIsPositive
                    )
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error loading chat history: ${e.message}") }
    }

    fun dismissDepositConfirmation() { _depositConfirmation.value = null }

    fun cancelPendingMessage() {
        if (activeMessageJob?.isActive == true) {
            activeMessageJob?.cancel()
            Log.d(TAG, "Active message job cancelled")
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
        val actionMatch   = actionPattern.find(aiResponse)
        val cleanResponse = aiResponse.replace(actionPattern, "").trim()
        actionMatch?.let { match ->
            when (match.groupValues[1]) {
                "DEPOSIT" -> {
                    val amount = match.groupValues[2].trim().toLongOrNull()
                    if (amount != null && amount > 0) {
                        withContext(Dispatchers.IO) { saveTransaction(amount, "Chat dengan Mai") }
                        _depositConfirmation.value = amount
                    }
                    else{
                        Log.w(TAG, "Invalid deposit action: ${match.groupValues[2]}")
                    }
                }
                else -> Log.d(TAG, "Unknown action: ${match.groupValues[1]}")
            }
        }
        addAiMessage(cleanResponse.ifBlank { aiResponse })
    }

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
        if (lastRequestFinishedAt != 0L && sinceLastFinished < minRequestIntervalMs) return

        activeMessageJob?.cancel()
        activeMessageJob = viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value    = true
            _loadingStatus.value = LoadingStatus.CONNECTING

            try {
                addUserMessage(userMessage)
                lastUserMessage = userMessage

                if (!::chatModel.isInitialized) {
                    addAiMessage("AI belum siap. Pastikan API Key sudah dikonfigurasi.")
                    return@launch
                }
                if (!withContext(Dispatchers.IO) { isNetworkAvailable() }) {
                    _loadingStatus.value = LoadingStatus.ERROR_BOTH_LIMIT
                    handleOfflineMessage(userMessage)
                    return@launch
                }

                val contextMessage = buildContextMessage(userMessage)

                val (primaryModel, secondaryModel, primaryEnum, secondaryEnum) =
                    if (_activeModel.value == ActiveModel.LITE)
                        Quad(chatModel, chatModelFallback, ActiveModel.LITE, ActiveModel.FLASH)
                    else
                        Quad(chatModelFallback, chatModel, ActiveModel.FLASH, ActiveModel.LITE)

                _loadingStatus.value = LoadingStatus.WAITING_RESPONSE
                var response: com.google.ai.client.generativeai.type.GenerateContentResponse? = null

                try {
                    response = tryGenerate(primaryModel, contextMessage)
                    _activeModel.value = primaryEnum
                } catch (primaryEx: Exception) {
                    if (primaryEx is CancellationException) throw primaryEx
                    if (!isRateLimitError(primaryEx)) throw primaryEx

                    Log.w(TAG, "Primary model ($primaryEnum) rate limited, trying $secondaryEnum")
                    _loadingStatus.value = LoadingStatus.FALLBACK_TRYING
                    delay(500)
                    _loadingStatus.value = LoadingStatus.FALLBACK_CONNECTING
                    delay(400)
                    _loadingStatus.value = LoadingStatus.WAITING_RESPONSE

                    try {
                        response = tryGenerate(secondaryModel, contextMessage)
                        _activeModel.value = secondaryEnum
                        Log.d(TAG, "Switched preferred model to $secondaryEnum")
                    } catch (secondaryEx: Exception) {
                        if (secondaryEx is CancellationException) throw secondaryEx
                        if (isRateLimitError(secondaryEx)) {
                            _loadingStatus.value = LoadingStatus.ERROR_BOTH_LIMIT
                            addAiMessage("Kedua model AI limit hari ini. Mode offline aktif, kamu masih bisa setor, atau ketik info untuk lihat status.")
                            handleOfflineMessage(userMessage)
                            return@launch
                        } else {
                            throw secondaryEx
                        }
                    }
                }

                response?.let {
                    processAiResponse(userMessage, it.text ?: "Maaf, aku tidak bisa merespons.")
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Message job cancelled")
            } catch (e: Exception) {
                val errorMessage = handleApiError(e)
                addAiMessage(errorMessage)
                withContext(Dispatchers.IO) { saveConversation(userMessage, errorMessage, false) }
            } finally {
                lastRequestFinishedAt = System.currentTimeMillis()
                _isLoading.value      = false
                _loadingStatus.value  = LoadingStatus.IDLE
            }
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun handleApiError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("429") || msg.contains("quota", true) ||
                    msg.contains("rate", true) || msg.contains("RESOURCE_EXHAUSTED", true) -> {
                val isRpdLimit = msg.contains("per_day", true) || msg.contains("daily", true) ||
                        msg.contains("RPD", true) || (msg.contains("quota", true) && msg.contains("day", true))
                if (isRpdLimit) "Limit harian API habis. Reset jam 07:00 WIB."
                else "Terlalu banyak pesan dalam 1 menit. Tunggu sebentar lalu coba lagi."
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

    private suspend fun saveConversation(userMsg: String, aiResp: String, wasSuccessful: Boolean) {
        try {
            conversationRepository.insert(ConversationHistory(
                userMessage    = userMsg,
                aiResponse     = aiResp,
                timestamp      = System.currentTimeMillis(),
                wasSuccessful  = wasSuccessful,
                context        = ""
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
                    put("totalPaid",       _debtState.value.totalPaid)
                    put("remaining",       _debtState.value.remainingDebt)
                    put("target",          _debtState.value.dailyTarget)
                    put("personalityMode", _personalityMode.value.name)
                }.toString()
                withContext(Dispatchers.IO) {
                    feedbackRepository.insert(ConversationFeedback(
                        userMessage = lastUserMessage,
                        aiResponse  = lastAiResponse,
                        isPositive  = isPositive,
                        timestamp   = System.currentTimeMillis(),
                        context     = ctx
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

    fun rebuildChatModel() {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") return
        try {
            chatModel         = buildGenerativeModel(MODEL_NAME)
            chatModelFallback = buildGenerativeModel(MODEL_NAME_FALLBACK)
        } catch (e: Exception) { Log.e(TAG, "Error rebuilding chatModel: ${e.message}") }
    }

    private suspend fun saveTransaction(amount: Long, source: String) {
        try {
            repository.insert(Transaction(amount = amount, source = source, date = System.currentTimeMillis()))
        } catch (e: Exception) { Log.e(TAG, "Error saving transaction: ${e.message}", e) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteTransaction(id) }
            Log.d(TAG, "Transaction deleted, achievements will recalculate")
        }
    }

    private fun addUserMessage(text: String) {
        val tempMsg = ChatMessage(text, isFromUser = true)
        _messages.value += tempMsg
        viewModelScope.launch {
            val dbId = withContext(Dispatchers.IO) { chatMessageRepository.saveMessage(text, isFromUser = true) }
            _messages.value = _messages.value.map {
                if (it.messageId == tempMsg.messageId && it.isFromUser) it.copy(messageId = dbId) else it
            }
        }
    }

    private fun addAiMessage(text: String) {
        val tempMsg = ChatMessage(text, isFromUser = false)
        _messages.value += tempMsg
        viewModelScope.launch {
            val dbId = withContext(Dispatchers.IO) { chatMessageRepository.saveMessage(text, isFromUser = false) }
            _messages.value = _messages.value.map {
                if (it.messageId == tempMsg.messageId && !it.isFromUser) it.copy(messageId = dbId) else it
            }
        }
    }

    private fun addSystemMessage(text: String) = addAiMessage(text)

    fun applySetupDate(date: String) {
        viewModelScope.launch {
            _setupDate.value = date
            withContext(Dispatchers.IO) { preferencesRepository.saveSetupDate(date) }
        }
    }

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
                _totalDebt.value      = newTotalDebt
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
            delay(100)
            _isDataReady.value = false
            _loadingStep.value = 0
            withContext(Dispatchers.IO) { chatMessageRepository.deleteAllMessages() }
            _messages.value       = emptyList()
            _totalDebt.value      = totalDebt
            _customDeadline.value = deadline
            _loadingStep.value    = 1
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
            sendFirstSetupGreeting()
        }
    }

    fun applyCloudDataAndCompleteOnboarding(totalDebt: Long, deadline: String) {
        viewModelScope.launch {
            try {
                _totalDebt.value      = totalDebt
                _customDeadline.value = deadline
                withContext(Dispatchers.IO) {
                    preferencesRepository.saveCustomTotalDebt(totalDebt)
                    preferencesRepository.saveCustomDeadline(deadline)
                    preferencesRepository.saveInitialDeadline(deadline)
                    preferencesRepository.setOnboardingDone()
                }
                val transactions = withContext(Dispatchers.IO) {
                    repository.allTransactions.firstOrNull() ?: emptyList()
                }
                _transactions.value = transactions
                recalculateDebtState(transactions = transactions, deadlineOverride = deadline, totalDebtOverride = totalDebt)
                rebuildChatModel()
                Log.d(TAG, "Cloud data applied: debt=$totalDebt, deadline=$deadline")
            } catch (e: Exception) { Log.e(TAG, "Error applying cloud data: ${e.message}", e) }
        }
    }

    fun reloadAfterSync() {
        viewModelScope.launch {
            try {
                val freshTransactions = withContext(Dispatchers.IO) {
                    repository.allTransactions.firstOrNull() ?: emptyList()
                }
                _transactions.value = freshTransactions
                recalculateDebtState(
                    transactions      = freshTransactions,
                    deadlineOverride  = _customDeadline.value,
                    totalDebtOverride = _totalDebt.value
                )
                rebuildChatModel()
                Log.d(TAG, "reloadAfterSync: ${freshTransactions.size} transactions loaded")
            } catch (e: Exception) { Log.e(TAG, "Error in reloadAfterSync: ${e.message}") }
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

    // ── Streak & Achievement ─────────────────────────────────────────────
    private fun checkStreakAndAchievements() {
        viewModelScope.launch {
            try {
                delay(300)
                val state  = _debtState.value
                val txList = _transactions.value

                val streak = com.hyse.debtslayer.utils.StreakManager.calculate(
                    transactions = txList,
                    dailyTarget  = state.dailyTarget
                )
                _streakData.value = streak

                if (streak.longestStreak > 0) {
                    withContext(Dispatchers.IO) { preferencesRepository.updateLongestStreak(streak.longestStreak) }
                }

                val unlockedIds = withContext(Dispatchers.IO) {
                    preferencesRepository.unlockedAchievements.firstOrNull() ?: emptySet()
                }

                val result = com.hyse.debtslayer.utils.AchievementManager.buildAll(
                    transactions = txList,
                    streakData   = streak,
                    totalDebt    = state.totalDebt,
                    totalPaid    = state.totalPaid,
                    dailyTarget  = state.dailyTarget,
                    unlockedIds  = unlockedIds
                )

                _achievements.value = result.allAchievements

                val currentlyValidIds = result.allAchievements
                    .filter { it.isUnlocked }
                    .map { it.id.name }
                    .toSet()
                withContext(Dispatchers.IO) {
                    preferencesRepository.syncUnlockedAchievements(currentlyValidIds)
                }

                if (result.newlyUnlocked.isNotEmpty()) {
                    Log.d(TAG, "New achievements: ${result.newlyUnlocked.map { it.id.name }}")
                    result.newlyUnlocked.forEach { ach ->
                        _newlyUnlockedAchievement.value = ach
                        showAchievementNotification(ach)
                        delay(3500)
                        _newlyUnlockedAchievement.value = null
                        delay(500)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking streak/achievements: ${e.message}")
            }
        }
    }

    fun dismissAchievementDialog() { _newlyUnlockedAchievement.value = null }

    // ── Mai Memory ───────────────────────────────────────────────────────
    fun updateMaiMemory(transactions: List<Transaction>, progressPct: Float, dailyTarget: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdf          = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayOfWeekFmt = SimpleDateFormat("EEEE", Locale("id", "ID"))
                val current      = _maiMemory.value

                if (transactions.isEmpty()) return@launch

                // Auto-fetch nickname dari Firestore kalau masih kosong
                if (current.nickname.isBlank()) {
                    try {
                        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            val firestoreNickname = com.google.firebase.firestore.FirebaseFirestore
                                .getInstance()
                                .collection("users").document(uid)
                                .get().await().getString("nickname") ?: ""
                            if (firestoreNickname.isNotBlank()) {
                                Log.d(TAG, "Nickname from Firestore: $firestoreNickname")
                                preferencesRepository.updateNickname(firestoreNickname)
                                _maiMemory.value = current.copy(nickname = firestoreNickname)
                                withContext(Dispatchers.Main) { rebuildChatModel() }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching nickname: ${e.message}")
                    }
                }

                val firstTx      = transactions.minByOrNull { it.date }
                val firstDate    = firstTx?.let { sdf.format(java.util.Date(it.date)) } ?: ""
                val depositByDay = transactions.groupBy { sdf.format(java.util.Date(it.date)) }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                val bestDay      = depositByDay.maxByOrNull { it.value }
                val bestDayAmount = bestDay?.value ?: 0L
                val bestDayDate  = bestDay?.key ?: ""
                val dayCount     = transactions.groupBy { dayOfWeekFmt.format(java.util.Date(it.date)) }
                    .mapValues { (_, list) -> list.size }
                val favoriteDay  = dayCount.maxByOrNull { it.value }?.key ?: ""
                val daysMetTarget = if (dailyTarget > 0) depositByDay.count { (_, amount) -> amount >= dailyTarget } else 0
                val totalDepositDays = depositByDay.size
                val lastTx       = transactions.maxByOrNull { it.date }
                val lastDepositDate   = lastTx?.let { sdf.format(java.util.Date(it.date)) } ?: ""
                val lastDepositAmount = lastTx?.amount ?: 0L

                val latestNickname = _maiMemory.value.nickname.ifBlank { current.nickname }

                val updated = current.copy(
                    nickname           = latestNickname,
                    firstDepositDate   = firstDate,
                    bestDayAmount      = bestDayAmount,
                    bestDayDate        = bestDayDate,
                    favoriteDayOfWeek  = favoriteDay,
                    daysMetTarget      = daysMetTarget,
                    milestone25Reached = current.milestone25Reached || progressPct >= 25f,
                    milestone50Reached = current.milestone50Reached || progressPct >= 50f,
                    milestone75Reached = current.milestone75Reached || progressPct >= 75f,
                    totalDepositDays   = totalDepositDays,
                    lastDepositDate    = lastDepositDate,
                    lastDepositAmount  = lastDepositAmount
                )

                preferencesRepository.saveMaiMemory(updated)
                _maiMemory.value = updated
                Log.d(TAG, "MaiMemory updated: nickname=${updated.nickname}, bestDay=${updated.bestDayAmount}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating MaiMemory: ${e.message}")
            }
        }
    }

    fun setNickname(nickname: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.updateNickname(nickname)
            _maiMemory.value = _maiMemory.value.copy(nickname = nickname)
        }
        rebuildChatModel()
    }

    private fun fetchNicknameWithRetry() {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser

            // Guest: tidak ada user → pastikan nickname kosong
            // Mai akan memanggil dengan sebutan netral
            if (user == null) {
                if (_maiMemory.value.nickname.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        preferencesRepository.updateNickname("")
                        _maiMemory.value = _maiMemory.value.copy(nickname = "")
                        withContext(Dispatchers.Main) { rebuildChatModel() }
                        Log.d(TAG, "fetchNickname: guest mode, nickname cleared")
                    }
                }
                return@addAuthStateListener
            }

            val uid = user.uid
            val currentNickname = _maiMemory.value.nickname
            if (currentNickname.isNotBlank() && !currentNickname.contains("@")) {
                Log.d(TAG, "fetchNickname: already have valid nickname=$currentNickname")
                return@addAuthStateListener
            }

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val nickname = com.google.firebase.firestore.FirebaseFirestore
                        .getInstance()
                        .collection("users").document(uid)
                        .get().await()
                        .getString("nickname") ?: ""
                    if (nickname.isNotBlank()) {
                        Log.d(TAG, "fetchNickname: got '$nickname' from Firestore")
                        preferencesRepository.updateNickname(nickname)
                        _maiMemory.value = _maiMemory.value.copy(nickname = nickname)
                        withContext(Dispatchers.Main) { rebuildChatModel() }
                    } else {
                        Log.d(TAG, "fetchNickname: no nickname in Firestore")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchNickname error: ${e.message}")
                }
            }
        }
    }

    private fun showAchievementNotification(achievement: com.hyse.debtslayer.utils.Achievement) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "achievement_channel", "Achievement", android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val intent = android.content.Intent(context, com.hyse.debtslayer.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", 3)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, achievement.id.ordinal, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = androidx.core.app.NotificationCompat.Builder(context, "achievement_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Achievement Unlocked!")
            .setContentText("${achievement.emoji} ${achievement.title}")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("${achievement.emoji} ${achievement.title}\n${achievement.description}"))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(2000 + achievement.id.ordinal, notif)
    }

    // 🆕 Expose repository untuk CloudSyncRepository
    fun getTransactionRepository(): TransactionRepository = repository
}