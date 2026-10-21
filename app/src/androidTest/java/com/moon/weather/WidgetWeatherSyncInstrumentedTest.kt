package com.moon.weather

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.WeatherLocation
import com.moon.weather.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** All locations, weather and preferences are isolated fixtures; no GPS or network calls. */
class WidgetWeatherSyncInstrumentedTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "widget_sync_test_$name"
            preferenceNames.add(isolated)
            return target.getSharedPreferences(isolated, mode)
        }
    }
    private val location = WeatherLocation("Fixture A", 48.85, 2.35)
    private val weather = WeatherWidgetPreviewPublisher.sampleWeather

    @After fun cleanUp() {
        preferenceNames.forEach { target.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun newLockWidgetUsesTheAppsReadingAndBothSizesUpdateWhenItChanges() = runBlocking {
        WeatherWidgetConfigStore.saveCurrentLocation(context, location)
        WeatherWidgetSnapshotStore.saveCurrent(context, location to weather, 100L)
        for (id in listOf(-92001, -92002)) {
            val initial = WeatherWidgetSnapshotStore.load(context, id)!!
            assertEquals(location, initial.result.first)
            assertEquals("17°", temperature(initial.result.second, id == -92002))
            WeatherWidgetSnapshotStore.save(context, id, initial.result, initial.savedAt)
        }
        val refreshed = WeatherWidgetDataSource.fetchWeatherWithSources(context, -92001,
            locate = { null }, fetch = {
                assertEquals("A background refresh reuses the foreground location", location, it)
                weather.copy(current = weather.current!!.copy(temperature = 12.0, weatherCode = 63))
            })!!
        WeatherWidgetSnapshotStore.saveCurrent(context, refreshed, 200L)
        for (id in listOf(-92001, -92002)) {
            val current = WeatherWidgetSnapshotStore.load(context, id)!!
            assertEquals(200L, current.savedAt)
            assertEquals("12°", temperature(current.result.second, id == -92002))
        }
        // Removing a widget must not erase the shared reading for a new widget.
        WeatherWidgetSnapshotStore.remove(context, -92001)
        assertEquals("12°", temperature(WeatherWidgetSnapshotStore.load(context, -92003)!!.result.second, false))
    }

    @Test fun selectedCitiesStayIndependentAndFailedRefreshKeepsLastTemperature() = runBlocking {
        val selected = WeatherLocation("Fixture B", 52.5, 13.4)
        WeatherWidgetConfigStore.saveConfiguration(context, -92004, WidgetBackgroundStyle.GLASS,
            false, WidgetCardStyle.WEATHER, selected)
        WeatherWidgetSnapshotStore.saveCurrent(context, location to weather, 200L)
        assertNull("A chosen city cannot inherit current-location weather", WeatherWidgetSnapshotStore.load(context, -92004))
        val result = WeatherWidgetDataSource.fetchWeatherWithSources(context, -92004,
            locate = { fail("A selected city should not request GPS"); null },
            fetch = { assertEquals(selected, it); weather })!!
        WeatherWidgetSnapshotStore.save(context, -92004, result, 100L)
        assertEquals(selected, WeatherWidgetSnapshotStore.load(context, -92004)!!.result.first)
        val failure = runCatching {
            WeatherWidgetDataSource.fetchWeatherWithSources(context, -92004,
                locate = { null }, fetch = { throw java.io.IOException("Offline fixture") })
        }
        assertTrue(failure.isFailure)
        assertEquals("17°", temperature(WeatherWidgetSnapshotStore.load(context, -92004)!!.result.second, false))
    }

    @Test fun smallIconIs22DpAndWideIconKeepsItsSize() {
        val density = target.resources.displayMetrics.density
        for (wide in listOf(false, true)) {
            val icon = WeatherLockScreenWidgets.views(target, wide, weather, true)
                .apply(target, null).findViewById<ImageView>(R.id.lock_weather_icon)
            assertEquals(((if (wide) 28 else 22) * density).toInt(), icon.layoutParams.width)
            assertEquals(icon.layoutParams.width, icon.layoutParams.height)
        }
    }

    @Test fun currentLocationWidgetsRequestGpsInsteadOfReopeningTheOldCoordinateSnapshot() {
        val provider = WeatherWidgetProvider()
        val current = provider.openIntent(context, -92005, location)
        assertTrue(current.getBooleanExtra(MainActivity.EXTRA_WIDGET_USE_CURRENT_LOCATION, false))
        assertFalse(current.hasExtra(MainActivity.EXTRA_WIDGET_LOCATION_LAT))
        assertFalse(current.hasExtra(MainActivity.EXTRA_WIDGET_LOCATION_NAME))
        WeatherWidgetConfigStore.saveConfiguration(context, -92006, WidgetBackgroundStyle.GLASS,
            false, WidgetCardStyle.WEATHER, location)
        val selected = provider.openIntent(context, -92006, location)
        assertFalse(selected.getBooleanExtra(MainActivity.EXTRA_WIDGET_USE_CURRENT_LOCATION, false))
        assertEquals(location.lat, selected.getDoubleExtra(MainActivity.EXTRA_WIDGET_LOCATION_LAT, Double.NaN), 0.0)
        assertEquals(location.name, selected.getStringExtra(MainActivity.EXTRA_WIDGET_LOCATION_NAME))
    }

    private fun temperature(reading: com.moon.weather.data.WeatherResponse, wide: Boolean): String =
        WeatherLockScreenWidgets.views(target, wide, reading, true).apply(target, null)
            .findViewById<TextView>(R.id.lock_weather_temperature).text.toString()
}
