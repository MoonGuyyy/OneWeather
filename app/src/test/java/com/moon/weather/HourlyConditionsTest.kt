package com.moon.weather

import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.HourlyWeather
import com.moon.weather.ui.components.hourlyConditions
import com.moon.weather.utils.WeatherCodeMapper
import org.junit.Assert.*
import org.junit.Test

class HourlyConditionsTest {
    private val hourly = HourlyWeather(listOf("2026-10-04T18:00","2026-10-04T19:00"),
        listOf(15.0,14.0),listOf(75,80),listOf(95,95),listOf(80.0,85.0),listOf(20.0,25.0),
        listOf(110.0,110.0),listOf(1013.0,1013.0),listOf(1.0,0.0),listOf(10000.0,10000.0),
        listOf(13.0,12.0),listOf(10.0,11.0))
    private val current = CurrentWeather("2026-10-04T18:15",17.0,3,0,60.0,8.0,110.0,
        1013.0,0.0,10000.0,16.0,10.0)

    @Test fun nowUsesTheSameConditionAndDayNightSnapshotAsTheBackground() {
        for (code in listOf(0,3,61,71,95,99)) for (day in listOf(0,1)) {
            val snapshot = current.copy(weatherCode = code,isDay = day)
            val now = hourlyConditions(hourly,0,true,snapshot)
            assertEquals(snapshot.weatherCode,now.weatherCode)
            assertEquals(snapshot.isDay == 1,now.isDay)
            assertEquals(WeatherCodeMapper.getIconRes(code,day == 1),
                WeatherCodeMapper.getIconRes(now.weatherCode,now.isDay))
            assertEquals(snapshot.temperature,now.temperature,0.0)
            assertEquals(snapshot.apparentTemperature,now.apparentTemperature,0.0)
            assertEquals(snapshot.windSpeed,now.windSpeed,0.0)
            assertEquals(snapshot.humidity,now.humidity,0.0)
        }
    }

    @Test fun anUpcomingThunderstormDoesNotBecomeTheCurrentCondition() {
        val now = hourlyConditions(hourly,0,true,current)
        val nextHour = hourlyConditions(hourly,1,false,current)
        assertEquals(3,now.weatherCode)
        assertEquals(95,nextHour.weatherCode)
        assertFalse(now.isDay) // Current data says sunset has already happened.
        assertTrue(nextHour.isDay) // Future forecast retains its existing hourly display policy.
        assertEquals(14.0,nextHour.temperature,0.0)
    }

    @Test fun missingCurrentConditionsFallBackToTheHourlyForecast() {
        val now = hourlyConditions(hourly,0,true,null)
        assertEquals(95,now.weatherCode)
        assertEquals(15.0,now.temperature,0.0)
    }
}
