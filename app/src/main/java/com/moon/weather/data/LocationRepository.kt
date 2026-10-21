package com.moon.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_locations")

class LocationRepository(private val context: Context) {

    companion object {
        private val LOCATIONS_KEY = stringPreferencesKey("saved_locations")
    }

    val savedLocations: Flow<List<WeatherLocation>> = context.dataStore.data.map { prefs ->
        val raw = prefs[LOCATIONS_KEY] ?: ""
        if (raw.isEmpty()) emptyList()
        else raw.split("|").mapNotNull { parseLocation(it) }
    }

    suspend fun saveLocation(location: WeatherLocation) {
        context.dataStore.edit { prefs ->
            val current = prefs[LOCATIONS_KEY] ?: ""
            val locations = if (current.isEmpty()) emptyList()
            else current.split("|").mapNotNull { parseLocation(it) }

            // Don't add duplicates
            if (locations.none { it.samePlace(location) }) {
                val updated = (locations + location).joinToString("|") { encodeLocation(it) }
                prefs[LOCATIONS_KEY] = updated
            }
        }
    }

    suspend fun removeLocation(location: WeatherLocation) {
        context.dataStore.edit { prefs ->
            val current = prefs[LOCATIONS_KEY] ?: ""
            val locations = if (current.isEmpty()) emptyList()
            else current.split("|").mapNotNull { parseLocation(it) }
            val updated = locations.filterNot { it.samePlace(location) }.joinToString("|") { encodeLocation(it) }
            prefs[LOCATIONS_KEY] = updated
        }
    }

    suspend fun reorderLocations(locations: List<WeatherLocation>) {
        context.dataStore.edit { prefs ->
            prefs[LOCATIONS_KEY] = locations.joinToString("|") { encodeLocation(it) }
        }
    }

    private fun encodeLocation(loc: WeatherLocation): String =
        "${loc.name}~${loc.lat}~${loc.lon}~${loc.country}"

    private fun parseLocation(raw: String): WeatherLocation? {
        val parts = raw.split("~")
        return if (parts.size >= 3) {
            WeatherLocation(
                name = parts[0],
                lat = parts[1].toDoubleOrNull() ?: return null,
                lon = parts[2].toDoubleOrNull() ?: return null,
                country = parts.getOrElse(3) { "" }
            )
        } else null
    }
}
