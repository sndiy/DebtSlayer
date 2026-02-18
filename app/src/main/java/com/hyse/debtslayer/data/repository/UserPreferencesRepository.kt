package com.hyse.debtslayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val PERSONALITY_MODE_KEY = stringPreferencesKey("personality_mode")
        private val CUSTOM_DEADLINE_KEY = stringPreferencesKey("custom_deadline")
        private val POSITIVE_FEEDBACK_KEY = intPreferencesKey("positive_feedback")
        private val NEGATIVE_FEEDBACK_KEY = intPreferencesKey("negative_feedback")
        private val CUSTOM_TOTAL_DEBT_KEY = longPreferencesKey("custom_total_debt")
        private val DAILY_TOKEN_TOTAL_KEY = longPreferencesKey("daily_token_total")
        private val DAILY_TOKEN_DATE_KEY = stringPreferencesKey("daily_token_date")
        private val REMINDER_HOUR_KEY = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE_KEY = intPreferencesKey("reminder_minute")
        private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        private val INITIAL_DEADLINE_KEY = stringPreferencesKey("initial_deadline")
        private val SETUP_DATE_KEY = stringPreferencesKey("setup_date")
        // ✅ BARU: Model yang dipilih user
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        // ✅ BARU: Tracking request harian (RPD)
        private val DAILY_REQUEST_COUNT_KEY = intPreferencesKey("daily_request_count")
        private val DAILY_REQUEST_DATE_KEY = stringPreferencesKey("daily_request_date")
    }

    val personalityMode: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PERSONALITY_MODE_KEY]
    }

    suspend fun savePersonalityMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PERSONALITY_MODE_KEY] = mode
        }
    }

    val customDeadline: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_DEADLINE_KEY]
    }

    suspend fun saveCustomDeadline(deadline: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_DEADLINE_KEY] = deadline
        }
    }

    suspend fun clearCustomDeadline() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTOM_DEADLINE_KEY)
        }
    }

    val customTotalDebt: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_TOTAL_DEBT_KEY]
    }

    suspend fun saveCustomTotalDebt(amount: Long) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_TOTAL_DEBT_KEY] = amount
        }
    }

    suspend fun clearCustomTotalDebt() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTOM_TOTAL_DEBT_KEY)
        }
    }

    val positiveFeedbackCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POSITIVE_FEEDBACK_KEY] ?: 0
    }

    val negativeFeedbackCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[NEGATIVE_FEEDBACK_KEY] ?: 0
    }

    suspend fun incrementPositiveFeedback() {
        context.dataStore.edit { preferences ->
            val current = preferences[POSITIVE_FEEDBACK_KEY] ?: 0
            preferences[POSITIVE_FEEDBACK_KEY] = current + 1
        }
    }

    suspend fun incrementNegativeFeedback() {
        context.dataStore.edit { preferences ->
            val current = preferences[NEGATIVE_FEEDBACK_KEY] ?: 0
            preferences[NEGATIVE_FEEDBACK_KEY] = current + 1
        }
    }

    val dailyTokenData: Flow<Pair<Long, String>> = context.dataStore.data.map { preferences ->
        val total = preferences[DAILY_TOKEN_TOTAL_KEY] ?: 0L
        val date = preferences[DAILY_TOKEN_DATE_KEY] ?: ""
        Pair(total, date)
    }

    val reminderHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_HOUR_KEY] ?: 19
    }

    val reminderMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REMINDER_MINUTE_KEY] ?: 0
    }

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_DONE_KEY] ?: false
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING_DONE_KEY] = true }
    }

    val initialDeadline: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[INITIAL_DEADLINE_KEY]
    }

    suspend fun saveInitialDeadline(deadline: String) {
        context.dataStore.edit { preferences ->
            if (preferences[INITIAL_DEADLINE_KEY] == null) {
                preferences[INITIAL_DEADLINE_KEY] = deadline
            }
        }
    }

    val setupDate: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SETUP_DATE_KEY]
    }

    suspend fun saveSetupDate(date: String) {
        context.dataStore.edit { preferences ->
            if (preferences[SETUP_DATE_KEY] == null) {
                preferences[SETUP_DATE_KEY] = date
            }
        }
    }

    suspend fun saveReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[REMINDER_HOUR_KEY] = hour
            preferences[REMINDER_MINUTE_KEY] = minute
        }
    }

    suspend fun addDailyTokens(tokens: Int) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        context.dataStore.edit { preferences ->
            val savedDate = preferences[DAILY_TOKEN_DATE_KEY] ?: ""
            if (savedDate != today) {
                preferences[DAILY_TOKEN_TOTAL_KEY] = tokens.toLong()
                preferences[DAILY_TOKEN_DATE_KEY] = today
            } else {
                val current = preferences[DAILY_TOKEN_TOTAL_KEY] ?: 0L
                preferences[DAILY_TOKEN_TOTAL_KEY] = current + tokens
            }
        }
    }

    // ✅ BARU: Selected model
    val selectedModel: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL_KEY]
    }

    suspend fun saveSelectedModel(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_KEY] = modelName
        }
    }

    // ✅ BARU: Daily request tracking (untuk monitor RPD limit)
    val dailyRequestData: Flow<Pair<Int, String>> = context.dataStore.data.map { preferences ->
        val count = preferences[DAILY_REQUEST_COUNT_KEY] ?: 0
        val date = preferences[DAILY_REQUEST_DATE_KEY] ?: ""
        Pair(count, date)
    }

    suspend fun incrementDailyRequest() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        context.dataStore.edit { preferences ->
            val savedDate = preferences[DAILY_REQUEST_DATE_KEY] ?: ""
            if (savedDate != today) {
                // Hari baru — reset counter
                preferences[DAILY_REQUEST_COUNT_KEY] = 1
                preferences[DAILY_REQUEST_DATE_KEY] = today
            } else {
                val current = preferences[DAILY_REQUEST_COUNT_KEY] ?: 0
                preferences[DAILY_REQUEST_COUNT_KEY] = current + 1
            }
        }
    }
}
