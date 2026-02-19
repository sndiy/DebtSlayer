package com.hyse.debtslayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val AI_PERSONALITY_LEVEL_KEY = stringPreferencesKey("ai_personality_level")
        private val POSITIVE_FEEDBACK_COUNT_KEY = stringPreferencesKey("positive_feedback_count")
        private val NEGATIVE_FEEDBACK_COUNT_KEY = stringPreferencesKey("negative_feedback_count")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY] ?: ""
    }

    val aiPersonalityLevel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AI_PERSONALITY_LEVEL_KEY] ?: "balanced" // strict, balanced, gentle
    }

    val positiveFeedbackCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POSITIVE_FEEDBACK_COUNT_KEY]?.toIntOrNull() ?: 0
    }

    val negativeFeedbackCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[NEGATIVE_FEEDBACK_COUNT_KEY]?.toIntOrNull() ?: 0
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun setAIPersonalityLevel(level: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_PERSONALITY_LEVEL_KEY] = level
        }
    }

    suspend fun incrementPositiveFeedback() {
        context.dataStore.edit { preferences ->
            val current = preferences[POSITIVE_FEEDBACK_COUNT_KEY]?.toIntOrNull() ?: 0
            preferences[POSITIVE_FEEDBACK_COUNT_KEY] = (current + 1).toString()
        }
    }

    suspend fun incrementNegativeFeedback() {
        context.dataStore.edit { preferences ->
            val current = preferences[NEGATIVE_FEEDBACK_COUNT_KEY]?.toIntOrNull() ?: 0
            preferences[NEGATIVE_FEEDBACK_COUNT_KEY] = (current + 1).toString()
        }
    }
}