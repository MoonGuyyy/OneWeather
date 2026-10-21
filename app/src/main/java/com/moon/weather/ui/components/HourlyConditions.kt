package com.moon.weather.ui.components

import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.HourlyWeather

internal data class HourlyConditions(
    val weatherCode: Int,
    val isDay: Boolean,
    val temperature: Double,
    val apparentTemperature: Double,
    val windSpeed: Double,
    val humidity: Double
)

/** "Now" is the same snapshot used by the hero and artwork; later hours remain forecasts. */
internal fun hourlyConditions(
    hourly: HourlyWeather, index: Int, isNow: Boolean, current: CurrentWeather?
): HourlyConditions {
    if (isNow && current != null) return HourlyConditions(current.weatherCode,current.isDay == 1,
        current.temperature,current.apparentTemperature,current.windSpeed,current.humidity)
    val hour = hourly.time.getOrNull(index)?.substringAfter('T')?.substringBefore(':')?.toIntOrNull() ?: 0
    return HourlyConditions(hourly.weatherCodes.getOrNull(index) ?: 0,hour in 5..20,
        hourly.temperatures.getOrNull(index) ?: 0.0,hourly.apparentTemperatures.getOrNull(index) ?: 0.0,
        hourly.windSpeeds.getOrNull(index) ?: 0.0,hourly.humidity.getOrNull(index) ?: 0.0)
}
