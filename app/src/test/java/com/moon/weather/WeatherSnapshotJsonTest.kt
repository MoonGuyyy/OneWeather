package com.moon.weather

import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.WeatherResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSnapshotJsonTest {
    @Test
    fun lastReadingCanBeSavedAndRestored() {
        val response = WeatherResponse(
            CurrentWeather("2026-10-02T17:30", 25.0, 2, 1, 55.0, 8.0, 110.0,
                1013.0, 6.0, 10000.0, 26.0, 14.0), null, null
        )
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(WeatherResponse::class.java)
        val restored = adapter.fromJson(adapter.toJson(response))
        assertEquals(response, restored)
    }
}
