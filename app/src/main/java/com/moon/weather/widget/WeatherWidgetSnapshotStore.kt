package com.moon.weather.widget

import android.content.Context
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal data class CachedWidgetWeather(
    val result: Pair<WeatherLocation, WeatherResponse>,
    val savedAt: Long
)

/** Keeps the last real reading available after the app process or phone restarts. */
internal object WeatherWidgetSnapshotStore {
    private const val PREFS = "weather_widget_snapshots"
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(WeatherResponse::class.java)

    private fun selectionKey(context: Context, id: Int): String =
        WeatherWidgetConfigStore.getLocationSelection(context, id)?.let { "${it.lat},${it.lon}" }
            ?: "current"

    fun load(context: Context, id: Int): CachedWidgetWeather? {
        val own = loadSnapshot(context, id.toString(), selectionKey(context, id))
        if (WeatherWidgetConfigStore.getLocationSelection(context, id) != null) return own
        val current = loadSnapshot(context, "current", "current")
        return listOfNotNull(own, current).maxByOrNull { it.savedAt }
    }

    private fun loadSnapshot(context: Context, id: String, selection: String): CachedWidgetWeather? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("selection_$id", null) != selection) return null
        val json = prefs.getString("weather_$id", null) ?: return null
        val weather = runCatching { adapter.fromJson(json) }.getOrNull()
            ?.takeIf { it.current != null } ?: return null
        val name = prefs.getString("name_$id", null) ?: return null
        val lat = prefs.getString("lat_$id", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon_$id", null)?.toDoubleOrNull() ?: return null
        val country = prefs.getString("country_$id", "").orEmpty()
        return CachedWidgetWeather(WeatherLocation(name, lat, lon, country) to weather,
            prefs.getLong("saved_at_$id", 0L))
    }

    fun save(context: Context, id: Int, result: Pair<WeatherLocation, WeatherResponse>, savedAt: Long) {
        saveSnapshot(context, id.toString(), selectionKey(context, id), result, savedAt)
    }

    fun saveCurrent(context: Context, result: Pair<WeatherLocation, WeatherResponse>, savedAt: Long) {
        saveSnapshot(context, "current", "current", result, savedAt)
    }

    private fun saveSnapshot(context: Context, id: String, selection: String,
                             result: Pair<WeatherLocation, WeatherResponse>, savedAt: Long) {
        if (result.second.current == null) return
        val json = runCatching { adapter.toJson(result.second) }.getOrNull() ?: return
        val location = result.first
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("selection_$id", selection)
            .putString("weather_$id", json)
            .putString("name_$id", location.name)
            .putString("lat_$id", location.lat.toString())
            .putString("lon_$id", location.lon.toString())
            .putString("country_$id", location.country)
            .putLong("saved_at_$id", savedAt)
            .apply()
    }

    fun remove(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("selection_$id")
            .remove("weather_$id")
            .remove("name_$id")
            .remove("lat_$id")
            .remove("lon_$id")
            .remove("country_$id")
            .remove("saved_at_$id")
            .apply()
    }
}
