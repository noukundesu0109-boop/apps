package com.nossam.smahobutton.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nossam.smahobutton.logic.SettingsJson
import com.nossam.smahobutton.logic.SettingsSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "smahobutton")

class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")

    val settings: Flow<SettingsSpec> = context.dataStore.data
        .map { prefs -> SettingsJson.decode(prefs[key]) }
        .catch { error ->
            Log.e(TAG, "settings read failed", error)
            emit(SettingsSpec())
        }

    val state = MutableStateFlow<SettingsSpec?>(null)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            settings.collect { state.value = it }
        }
    }

    suspend fun current(): SettingsSpec = settings.first()

    suspend fun update(transform: (SettingsSpec) -> SettingsSpec) {
        try {
            context.dataStore.edit { prefs ->
                val current = SettingsJson.decode(prefs[key])
                prefs[key] = SettingsJson.encode(transform(current))
            }
        } catch (error: Exception) {
            Log.e(TAG, "settings update failed", error)
        }
    }

    private companion object {
        const val TAG = "SmahoButton"
    }
}
