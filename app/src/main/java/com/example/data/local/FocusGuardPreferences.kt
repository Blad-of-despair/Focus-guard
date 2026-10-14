package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusguard_prefs")

class FocusGuardPreferences(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val KEY_IS_PIN_SET = booleanPreferencesKey("is_pin_set")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val KEY_LOCK_FOCUSGUARD = booleanPreferencesKey("lock_focusguard")
        val KEY_AUTO_LOCK_MINUTES = longPreferencesKey("auto_lock_minutes")
        val KEY_LAST_BACKGROUND_TIME = longPreferencesKey("last_background_time")
        val KEY_CLOCK_ANCHOR_WALL = longPreferencesKey("clock_anchor_wall")
        val KEY_CLOCK_ANCHOR_ELAPSED = longPreferencesKey("clock_anchor_elapsed")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_FIRST_LAUNCH] ?: true
    }

    val isPinSetFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_PIN_SET] ?: false
    }

    val biometricsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRICS_ENABLED] ?: false
    }

    val lockFocusGuardFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCK_FOCUSGUARD] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_LAUNCH] = false
        }
    }

    suspend fun savePin(saltHex: String, hashHex: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_SALT] = saltHex
            prefs[KEY_PIN_HASH] = hashHex
            prefs[KEY_IS_PIN_SET] = true
            prefs[KEY_LOCK_FOCUSGUARD] = true
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun setLockFocusGuard(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_FOCUSGUARD] = enabled
        }
    }

    suspend fun updateLastBackgroundTime(timeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKGROUND_TIME] = timeMillis
        }
    }

    suspend fun saveClockAnchor(wallTimeMillis: Long, elapsedRealtimeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLOCK_ANCHOR_WALL] = wallTimeMillis
            prefs[KEY_CLOCK_ANCHOR_ELAPSED] = elapsedRealtimeMillis
        }
    }

    suspend fun getClockAnchors(): Pair<Long, Long> {
        val prefs = context.dataStore.data.first()
        val wall = prefs[KEY_CLOCK_ANCHOR_WALL] ?: 0L
        val elapsed = prefs[KEY_CLOCK_ANCHOR_ELAPSED] ?: 0L
        return Pair(wall, elapsed)
    }
}
