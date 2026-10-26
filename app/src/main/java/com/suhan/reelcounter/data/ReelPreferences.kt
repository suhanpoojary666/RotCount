package com.suhan.reelcounter.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.suhan.reelcounter.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rotcount_settings")

class ReelPreferences(private val context: Context) {

    private val ioErrors = CoroutineExceptionHandler { _, t ->
        if (BuildConfig.DEBUG) Log.w("rotcount", "DataStore preferences error", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + ioErrors)

    private val _showCounter = MutableStateFlow(true)
    val showCounter: StateFlow<Boolean> = _showCounter.asStateFlow()

    private val _countEnabled = MutableStateFlow(true)
    val countEnabled: StateFlow<Boolean> = _countEnabled.asStateFlow()

    // Backwards-compatible alias for existing callers
    val countInstagram: StateFlow<Boolean> get() = _countEnabled.asStateFlow()
    val countYouTube: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    init {
        scope.launch {
            val initial = context.dataStore.data.first()
            _showCounter.value = initial[KEY_SHOW_COUNTER] ?: true
            _countEnabled.value = initial[KEY_COUNT_ENABLED] ?: initial[KEY_COUNT_INSTAGRAM] ?: true

            context.dataStore.data.collect { prefs ->
                _showCounter.value = prefs[KEY_SHOW_COUNTER] ?: true
                _countEnabled.value = prefs[KEY_COUNT_ENABLED] ?: prefs[KEY_COUNT_INSTAGRAM] ?: true
            }
        }
    }

    fun setShowCounter(enabled: Boolean) {
        _showCounter.value = enabled
        scope.launch {
            context.dataStore.edit { it[KEY_SHOW_COUNTER] = enabled }
        }
    }

    fun setCountEnabled(enabled: Boolean) {
        _countEnabled.value = enabled
        scope.launch {
            context.dataStore.edit {
                it[KEY_COUNT_ENABLED] = enabled
                it[KEY_COUNT_INSTAGRAM] = enabled
            }
        }
    }

    fun setCountInstagram(enabled: Boolean) = setCountEnabled(enabled)
    fun setCountYouTube(enabled: Boolean) {}

    fun isAppEnabled(packageName: String): Boolean {
        return packageName == PACKAGE_INSTAGRAM && _countEnabled.value
    }

    companion object {
        val KEY_SHOW_COUNTER = booleanPreferencesKey("key_show_counter")
        val KEY_COUNT_ENABLED = booleanPreferencesKey("key_count_enabled")
        val KEY_COUNT_INSTAGRAM = booleanPreferencesKey("key_count_instagram")
        val KEY_COUNT_YOUTUBE = booleanPreferencesKey("key_count_youtube")

        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    }
}
