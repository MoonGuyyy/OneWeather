package com.moon.weather.ui.components

import com.moon.weather.data.HourlyWeather
import kotlin.math.roundToInt

/** A short summary of the actual 48-hour window shown by the hourly card. */
object ForecastHeadline {
    fun create(hourly: HourlyWeather?, currentTime: String?, useCelsius: Boolean): String {
        if (hourly == null || hourly.time.isEmpty()) return "Next 48 hours"
        val start = hourly.time.indexOfFirst { it >= (currentTime?.take(13)?.plus(":00") ?: "") }
            .takeIf { it >= 0 } ?: 0
        val end = minOf(start + 48, hourly.time.size, hourly.temperatures.size)
        if (end <= start) return "Next 48 hours"
        val temperatures = hourly.temperatures.subList(start, end)
        val high = temperatures.maxOrNull() ?: return "Next 48 hours"
        val dailyHighs = (start until end).groupBy { hourly.time[it].take(10) }
            .values.mapNotNull { indices -> indices.maxOfOrNull { hourly.temperatures[it] } }
        val highLow = dailyHighs.minOrNull() ?: high
        val highTop = dailyHighs.maxOrNull() ?: high
        val format: (Double) -> Int = { if (useCelsius) it.roundToInt() else (it * 9 / 5 + 32).roundToInt() }
        val unit = if (useCelsius) "°C" else "°F"
        val range = if (format(highLow) == format(highTop)) "${format(highTop)}$unit"
            else "${format(highLow)}–${format(highTop)}$unit"
        val codes = hourly.weatherCodes.drop(start).take(end - start)
        val wind = hourly.windSpeeds.drop(start).take(end - start).maxOrNull() ?: 0.0
        val condition = when {
            codes.any { it >= 95 } -> 0 // Storms
            codes.any { it in 71..77 || it in 85..86 } -> 1 // Snow
            codes.count { it in 51..67 || it in 80..82 } >= 3 -> 2 // Rain
            codes.any { it == 45 || it == 48 } -> 3 // Fog
            wind >= 40 -> 4 // Wind (Open-Meteo wind speed is km/h)
            else -> 5 // Clear or cloudy
        }
        val band = when {
            high < 0 -> 0
            high < 10 -> 1
            high < 20 -> 2
            high < 30 -> 3
            else -> 4
        }
        val messages = arrayOf(
            arrayOf("Bitter cold with storms", "Snow and deep cold", "Icy rain ahead", "Freezing fog may linger", "Biting winds and frost", "Staying below freezing"),
            arrayOf("Cold with stormy spells", "Snow is possible", "Chilly rain ahead", "Cold, misty stretches", "Windy and cold", "A crisp couple of days"),
            arrayOf("Mild with a chance of thunder", "A wintry mix is possible", "Showers between mild spells", "Mild but misty", "Breezy and mild", "Comfortable weather ahead"),
            arrayOf("Warm with thunderstorms", "An unusual warm spell with snow", "Warm days with showers", "Warm days, hazy starts", "Warm and breezy", "Warm days ahead"),
            arrayOf("Heat and thunderstorms", "Heat with a wintry change", "Hot days with showers", "Hot and hazy", "Hot days with strong winds", "Really hot days and nights")
        )
        val hotNight = (start until end).any { index ->
            val hour = hourly.time[index].substringAfter('T').take(2).toIntOrNull() ?: 12
            hour in 0..5 && hourly.temperatures[index] >= 25
        }
        val description = if (band == 4 && condition == 5 && !hotNight) "Really hot days ahead"
            else messages[band][condition]
        return "$description. High $range"
    }
}
