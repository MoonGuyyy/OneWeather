package com.moon.weather

import com.moon.weather.data.HourlyWeather
import com.moon.weather.ui.components.ForecastHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastHeadlineTest {
    @Test fun hotNightsRequireHotNightTemperatures() {
        val hot = hourly(List(72) { if (it % 24 in 0..5) 26.0 else 33.0 })
        val coolNights = hourly(List(72) { if (it % 24 in 0..5) 16.0 else 33.0 })
        assertTrue(ForecastHeadline.create(hot, "2026-10-02T00:00", true).startsWith("Really hot days and nights"))
        assertTrue(ForecastHeadline.create(coolNights, "2026-10-02T00:00", true).startsWith("Really hot days ahead"))
    }

    @Test fun usesOnlyNext48HoursAndConvertsFahrenheit() {
        val temperatures = List(72) { if (it >= 48) 45.0 else 20.0 }
        assertEquals("Warm days ahead. High 68°F", ForecastHeadline.create(hourly(temperatures), "2026-10-02T00:00", false))
    }

    private fun hourly(temperatures: List<Double>) = HourlyWeather(
        time = temperatures.indices.map { "2026-10-%02dT%02d:00".format(2 + it / 24, it % 24) },
        temperatures = temperatures,
        precipitationProbabilities = List(temperatures.size) { 0 },
        weatherCodes = List(temperatures.size) { 0 },
        humidity = List(temperatures.size) { 50.0 },
        windSpeeds = List(temperatures.size) { 10.0 },
        windDirections = List(temperatures.size) { 0.0 },
        pressures = List(temperatures.size) { 1013.0 },
        uvIndices = List(temperatures.size) { 0.0 },
        visibilities = List(temperatures.size) { 10000.0 },
        apparentTemperatures = temperatures,
        dewPoints = List(temperatures.size) { 10.0 }
    )
}
