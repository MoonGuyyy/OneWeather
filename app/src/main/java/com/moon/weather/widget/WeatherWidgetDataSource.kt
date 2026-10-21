package com.moon.weather.widget

import android.content.Context
import android.location.Location
import com.moon.weather.data.dataStore
import com.moon.weather.data.DeviceLocationProvider
import com.moon.weather.data.RetrofitInstance
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.moon.weather.data.LocationNameResolver

private val SAVED_LOCATIONS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("saved_locations")

object WeatherWidgetDataSource {
    suspend fun fetchWeather(context: Context, widgetId: Int,
                             background: Boolean = false): Pair<WeatherLocation, WeatherResponse>? = withContext(Dispatchers.IO) {
        // Background location is restricted, especially in battery saver. Use the last
        // foreground location immediately so GPS cannot consume the receiver's time budget.
        fetchWeatherWithSources(context, widgetId, { getCurrentLocation(context) },
            { location -> RetrofitInstance.api.getWeather(location.lat, location.lon) }, background)
    }

    internal suspend fun fetchWeatherWithSources(context: Context, widgetId: Int,
        locate: suspend () -> WeatherLocation?,
        fetch: suspend (WeatherLocation) -> WeatherResponse,
        background: Boolean = false): Pair<WeatherLocation, WeatherResponse>? {
        val configuredLocation = WeatherWidgetConfigStore.getLocationSelection(context, widgetId)
        val location = configuredLocation ?: resolveCurrentLocation(context, widgetId,
            if (background) ({ null }) else locate) ?: return null
        return location to fetch(location)
    }

    suspend fun fetchPreviewWeather(context: Context, widgetId: Int, selectedLocation: WeatherLocation?): Pair<WeatherLocation, WeatherResponse>? = withContext(Dispatchers.IO) {
        val location = selectedLocation ?: resolveCurrentLocation(context, widgetId) ?: return@withContext null
        val weather = RetrofitInstance.api.getWeather(location.lat, location.lon)
        location to weather
    }

    private suspend fun resolveCurrentLocation(context: Context, widgetId: Int,
        locate: suspend () -> WeatherLocation? = { getCurrentLocation(context) }): WeatherLocation? {
        val current = locate()
        if (current != null) {
            val last = WeatherWidgetConfigStore.getCurrentLocation(context)
                ?: WeatherWidgetConfigStore.getLastResolvedLocation(context, widgetId)
            val stableCurrent = chooseStableCurrentLocation(current, last)
            WeatherWidgetConfigStore.saveLastResolvedLocation(context, widgetId, stableCurrent)
            WeatherWidgetConfigStore.saveCurrentLocation(context, stableCurrent)
            return stableCurrent
        }
        return WeatherWidgetConfigStore.getCurrentLocation(context)
            ?: WeatherWidgetConfigStore.getLastResolvedLocation(context, widgetId)
            ?: getPrimarySavedLocation(context)
    }

    suspend fun getSelectableLocations(context: Context): List<WeatherLocation> {
        return getSavedLocations(context)
    }

    private suspend fun getCurrentLocation(context: Context): WeatherLocation? {
        return try {
            val location = DeviceLocationProvider.getLocation(context) ?: return null

            val placeName = LocationNameResolver.resolve(context, location.latitude, location.longitude)
            WeatherLocation(
                name = placeName,
                lat = location.latitude,
                lon = location.longitude
            )
        } catch (_: SecurityException) {
            null
        }
    }


    private suspend fun getPrimarySavedLocation(context: Context): WeatherLocation? {
        return getSavedLocations(context).firstOrNull()
    }

    private suspend fun getSavedLocations(context: Context): List<WeatherLocation> {
        val raw = context.dataStore.data
            .map { prefs -> prefs[SAVED_LOCATIONS_KEY].orEmpty() }
            .first()
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { parseLocation(it) }
    }

    private fun parseLocation(raw: String): WeatherLocation? {
        val parts = raw.split("~")
        if (parts.size < 3) return null
        return WeatherLocation(
            name = parts[0],
            lat = parts[1].toDoubleOrNull() ?: return null,
            lon = parts[2].toDoubleOrNull() ?: return null,
            country = parts.getOrElse(3) { "" }
        )
    }

    private fun chooseStableCurrentLocation(
        current: WeatherLocation,
        previous: WeatherLocation?
    ): WeatherLocation {
        if (previous == null) return current
        val result = FloatArray(1)
        Location.distanceBetween(
            current.lat,
            current.lon,
            previous.lat,
            previous.lon,
            result
        )
        val distanceMeters = result.firstOrNull() ?: 0f
        // Keep label/point stable for local jitter; switch only after meaningful movement.
        return if (distanceMeters < 20_000f) previous else current
    }
}
