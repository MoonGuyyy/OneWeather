package com.moon.weather.ui

import android.app.Application
import com.moon.weather.data.*
import com.moon.weather.widget.WeatherWidgetProvider
import kotlinx.coroutines.flow.Flow

internal data class WeatherViewModelSources(
    val savedLocations: Flow<List<WeatherLocation>>,
    val locate: suspend () -> WeatherLocation?,
    val weather: suspend (WeatherLocation) -> WeatherResponse,
    val publish: suspend (WeatherLocation, WeatherResponse) -> Unit,
    val requestTimeoutMillis: Long = 15_000L
) {
    companion object {
        fun android(application: Application) = WeatherViewModelSources(
            savedLocations = LocationRepository(application).savedLocations,
            locate = {
                DeviceLocationProvider.getLocation(application, preferFresh = true)?.let {
                    WeatherLocation(LocationNameResolver.resolve(application, it.latitude, it.longitude), it.latitude, it.longitude)
                        .also { location -> com.moon.weather.widget.WeatherWidgetConfigStore.saveCurrentLocation(application, location) }
                }
            },
            weather = { RetrofitInstance.api.getWeather(it.lat, it.lon) },
            publish = { location, weather -> WeatherWidgetProvider.publishCurrentWeather(application, location, weather) }
        )
    }
}
