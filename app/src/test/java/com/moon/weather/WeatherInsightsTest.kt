package com.moon.weather

import com.moon.weather.data.*
import com.moon.weather.ui.components.WeatherInsights
import org.junit.Assert.*
import org.junit.Test

class WeatherInsightsTest {
    @Test fun libraryHasAtLeast80DistinctMessagesAcrossCategories() {
        val messages = WeatherInsights.messages.values.flatten()
        assertTrue(messages.size >= 80)
        assertEquals(messages.size, messages.toSet().size)
        assertTrue(WeatherInsights.messages.size >= 10)
    }
    @Test fun relevantSevereConditionsComeFirstAndAreCappedAtFour() {
        val result = WeatherInsights.create(weather(temp = 34.0, code = 95, wind = 35.0, uv = 8.0), true)
        assertEquals(4, result.size)
        assertEquals("Thunder", result.first().category)
        assertEquals(setOf("Thunder", "Heat", "Wind", "UV"), result.map { it.category }.toSet())
        assertTrue(result.none { "{value}" in it.message })
    }
    @Test fun mildWeatherNeedsOnlyOnePage() {
        val result = WeatherInsights.create(WeatherResponse(null, null, DailyWeather(listOf("2026-10-04"),listOf(0),listOf(20.0),emptyList(),emptyList(),emptyList(),emptyList(),null)), true)
        assertEquals(1, result.size)
        assertEquals("Clear skies", result.single().category)
    }
    @Test fun pageCountExpandsOnlyForRelevantConditions() {
        assertEquals(3, WeatherInsights.create(weather(uv = 8.0), true).size)
        assertEquals(3, WeatherInsights.create(weather(temp = 34.0, uv = 8.0), true).size)
    }
    @Test fun temperaturesAndWindRespectSelectedUnits() {
        val result = WeatherInsights.create(weather(temp = 35.0, wind = 32.18688), false, true)
        assertEquals("95°F", result.first { it.category == "Heat" }.value)
        assertEquals("20 mph", result.first { it.category == "Wind" }.value)
    }
    @Test fun tomorrowUsesLocalForecastDateAndTemperatureChange() {
        val daily = DailyWeather(listOf("2026-10-03", "2026-10-04", "2026-10-05"), listOf(0,0,0),
            listOf(32.0,20.0,25.0), listOf(18.0,12.0,15.0), emptyList(), emptyList(), listOf(1.0,1.0,1.0), null)
        val result = WeatherInsights.create(weather().copy(daily = daily), true)
        assertEquals("25°C", result.first { it.category == "Warmer tomorrow" }.value)
        assertFalse(result.any { it.category == "Cooler tomorrow" || it.category == "Heat" })
    }
    @Test fun ordinaryFullForecastIncludesThreeDifferentTopics() {
        val daily = DailyWeather(listOf("2026-10-04", "2026-10-05"), listOf(3,3),
            listOf(20.0,21.0), listOf(11.0,12.0), emptyList(), emptyList(), listOf(1.0,1.0), listOf(10,10))
        val result = WeatherInsights.create(weather(code = 3).copy(daily = daily), true)
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.category }.toSet().size)
        assertTrue(result.any { it.category == "Tomorrow's forecast" })
        assertTrue(result.any { it.category == "Cloud cover" })
        assertTrue(result.any { it.category == "Overnight outlook" })
    }
    @Test fun missingDataDoesNotInventInsights() {
        assertTrue(WeatherInsights.create(WeatherResponse(null,null,null), true).isEmpty())
    }
    @Test fun pastStormsDoNotBecomeNewAlerts() {
        val hourly = HourlyWeather(listOf("2026-10-04T06:00", "2026-10-04T14:00"), listOf(20.0,20.0),
            listOf(100,0), listOf(95,0), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val result = WeatherInsights.create(weather().copy(hourly = hourly), true)
        assertFalse(result.any { it.category == "Thunder" || it.category == "Rain" })
    }
    private fun weather(temp: Double = 20.0, code: Int = 0, wind: Double = 10.0, uv: Double = 1.0) =
        WeatherResponse(CurrentWeather("2026-10-04T12:00",temp,code,1,50.0,wind,0.0,1013.0,uv,10000.0,temp,10.0),null,null)
}
