package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.*
import com.moon.weather.ui.LocationsScreen
import com.moon.weather.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocationsScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun weatherCardsSelectCitiesAndToolbarAndEditActionsWork() {
        val gps = WeatherLocation("Sint-Pancras kern", 52.67, 4.78, "Netherlands")
        val cities = listOf(WeatherLocation("Delft", 52.01, 4.36, "Netherlands"),
            WeatherLocation("Helsinki", 60.17, 24.94, "Finland"),
            WeatherLocation("Paris", 48.86, 2.35, "France"))
        val saved = mutableStateOf(cities)
        val dark = mutableStateOf(true)
        val current = CurrentWeather("2026-10-03T16:43", 20.0, 2, 1, 55.0, 8.0, 110.0,
            1013.0, 6.0, 10000.0, 20.0, 14.0)
        val daily = DailyWeather(listOf("2026-10-03"), listOf(2), listOf(21.0), listOf(10.0),
            listOf("2026-10-03T07:10"), listOf("2026-10-03T19:10"), listOf(6.0), listOf(10))
        val weather = (listOf(gps) + cities).mapIndexed { i, city ->
            city to WeatherResponse(current.copy(temperature = listOf(20.0, 18.0, 13.0, 17.0)[i]), null, daily)
        }.toMap()
        var selected: WeatherLocation? = null
        var searches = 0; var settings = 0; var backs = 0
        compose.setContent {
            WeatherTheme(darkTheme = dark.value, dynamicColor = false) {
                LocationsScreen(saved.value, gps, weather, true, true, { selected = it },
                    { saved.value = saved.value - it }, { saved.value = it }, {},
                    { searches++ }, { settings++ }, { backs++ })
            }
        }
        compose.onNodeWithText("20°").assertIsDisplayed()
        compose.onNodeWithText("18°").assertIsDisplayed()
        export("locations-dark")
        compose.onNodeWithText("Delft").performClick()
        compose.runOnIdle { assertEquals(cities.first(), selected) }
        compose.onNodeWithContentDescription("Add location").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, searches); assertEquals(1, settings); assertEquals(1, backs); dark.value = false }
        export("locations-light")
        compose.onNodeWithText("Edit locations").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove Paris").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(cities.take(2), saved.value) }
    }

    private fun export(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
