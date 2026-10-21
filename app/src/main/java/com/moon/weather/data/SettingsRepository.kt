package com.moon.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_settings")

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val context: Context) {

    companion object {
        private val CELSIUS_KEY = booleanPreferencesKey("use_celsius")
        private val MILITARY_TIME_KEY = booleanPreferencesKey("use_military_time")
        private val MPH_KEY = booleanPreferencesKey("use_mph")
        private val APPEARANCE_KEY = stringPreferencesKey("appearance_mode")
    }

    val useCelsius: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CELSIUS_KEY] ?: true // Default: Celsius
    }

    val useMilitaryTime: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[MILITARY_TIME_KEY] ?: true // Default: Military time (24h)
    }

    val appearanceMode: Flow<AppearanceMode> = context.settingsDataStore.data.map { prefs ->
        prefs[APPEARANCE_KEY]?.let { saved ->
            AppearanceMode.entries.firstOrNull { it.name == saved }
        } ?: AppearanceMode.SYSTEM
    }

    val useMph: Flow<Boolean> = context.settingsDataStore.data.map { prefs -> prefs[MPH_KEY] ?: false }

    suspend fun setUseMph(useMph: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[MPH_KEY] = useMph }
    }

    suspend fun setUseCelsius(useCelsius: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[CELSIUS_KEY] = useCelsius
        }
    }

    suspend fun setUseMilitaryTime(useMilitaryTime: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[MILITARY_TIME_KEY] = useMilitaryTime
        }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.settingsDataStore.edit { prefs -> prefs[APPEARANCE_KEY] = mode.name }
    }
}
