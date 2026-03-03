package com.hyse.debtslayer.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncDataStore by preferencesDataStore(name = "sync_preferences")

enum class SyncFrequency(val label: String, val intervalMs: Long) {
    MANUAL(  "Manual (tidak otomatis)",  0L                         ),
    DAILY(   "Setiap hari",              24L * 60 * 60 * 1000       ),
    WEEKLY(  "Setiap minggu",            7L  * 24 * 60 * 60 * 1000  ),
    MONTHLY( "Setiap bulan",             30L * 24 * 60 * 60 * 1000  )
}

class SyncPreferences(private val context: Context) {

    companion object {
        private val KEY_FREQUENCY = stringPreferencesKey("sync_frequency")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
    }

    val syncFrequency: Flow<SyncFrequency> = context.syncDataStore.data.map { prefs ->
        val name = prefs[KEY_FREQUENCY] ?: SyncFrequency.MANUAL.name
        try { SyncFrequency.valueOf(name) } catch (e: Exception) { SyncFrequency.MANUAL }
    }

    val lastSyncTimestamp: Flow<Long> = context.syncDataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC] ?: 0L
    }

    suspend fun setSyncFrequency(frequency: SyncFrequency) {
        context.syncDataStore.edit { prefs ->
            prefs[KEY_FREQUENCY] = frequency.name
        }
    }

    suspend fun updateLastSync(timestamp: Long = System.currentTimeMillis()) {
        context.syncDataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = timestamp
        }
    }
}