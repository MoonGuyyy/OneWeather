package com.moon.weather

import android.app.Application
import android.Manifest
import androidx.test.rule.GrantPermissionRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.*
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Entirely synthetic locations and weather; never reads GPS or calls an external service. */
class WeatherRecoveryInstrumentedTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    private val amsterdam = WeatherLocation("Amsterdam", 52.37, 4.90)
    private val denHaag = WeatherLocation("Den Haag", 52.08, 4.31)
    private val rotterdam = WeatherLocation("Rotterdam", 51.92, 4.48)
    private val sample = WeatherWidgetPreviewPublisher.sampleWeather

    private class Foreground : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun screen(sources: WeatherViewModelSources): Pair<WeatherViewModel, Foreground> {
        lateinit var model: WeatherViewModel
        lateinit var foreground: Foreground
        compose.runOnIdle {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            foreground = Foreground().apply { registry.currentState = Lifecycle.State.STARTED }
            model = WeatherViewModel(compose.activity.application as Application, sources)
            compose.activity.viewModelStore.put("fixture-weather", model)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides foreground) {
                WeatherTheme(dynamicColor = false) { WeatherScreen(model) }
            }
        }
        // Dispatch the foreground refresh without starting a continuous frame clock before
        // Espresso has a chance to become idle. Data jobs continue after the lifecycle pause.
        compose.waitForIdle()
        compose.runOnUiThread {
            foreground.registry.currentState = Lifecycle.State.RESUMED
            foreground.registry.currentState = Lifecycle.State.STARTED
        }
        return model to foreground
    }

    private fun awaitCoverGone() = compose.waitUntil(8_000) {
        compose.onAllNodesWithTag("weather-launch-cover").fetchSemanticsNodes().isEmpty()
    }

    @Test fun appAndLockWidgetsAgreeOnFractionalReadingsAndForegroundRefreshes() {
        val reading = MutableStateFlow(sample.copy(current = sample.current!!.copy(temperature = 14.9)))
        val published = MutableStateFlow<WeatherResponse?>(null)
        val (model, _) = screen(WeatherViewModelSources(
            MutableStateFlow(emptyList()), locate = { amsterdam }, weather = { reading.value },
            publish = { _, weather -> published.value = weather }))
        awaitCoverGone()
        val context = compose.activity
        val celsius = kotlinx.coroutines.runBlocking {
            com.moon.weather.data.SettingsRepository(context).useCelsius.first()
        }
        fun assertTemperature(expected: String) {
            compose.onNodeWithTag("current-temperature").assertTextEquals(expected)
            for (wide in listOf(false, true)) {
                val views = com.moon.weather.widget.WeatherLockScreenWidgets.views(context, wide,
                    published.value, celsius).apply(context, null)
                assertEquals(expected, views.findViewById<android.widget.TextView>(
                    R.id.lock_weather_temperature).text.toString())
            }
        }
        compose.waitUntil(3_000) { published.value?.current?.temperature == 14.9 }
        assertTemperature(if (celsius) "15°" else "59°")
        compose.runOnIdle {
            reading.value = reading.value.copy(current = reading.value.current!!.copy(temperature = 14.1))
            model.fetchWeatherForLocation(amsterdam, forceRefresh = true)
        }
        compose.waitUntil(3_000) { published.value?.current?.temperature == 14.1 }
        assertTemperature(if (celsius) "14°" else "57°")
    }

    @Test fun failedSelectedCityOffersRetryWhileOtherCitiesLoadAndItsErrorSurvivesTheirSuccess() {
        val first = CompletableDeferred<WeatherResponse>()
        val other = CompletableDeferred<WeatherResponse>()
        var attempts = 0
        val (model, _) = screen(WeatherViewModelSources(
            MutableStateFlow(listOf(amsterdam, rotterdam)), locate = { null },
            weather = { city ->
                if (city == amsterdam) { attempts++; if (attempts == 1) first.await() else sample }
                else other.await()
            }, publish = { _, _ -> }))
        compose.runOnIdle { first.completeExceptionally(IOException("Fixture offline")) }
        awaitCoverGone()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(model.loadingLocations.value.contains(rotterdam))
            other.complete(sample)
        }
        compose.waitUntil(3_000) { !model.loadingLocations.value.contains(rotterdam) }
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(3_000) { model.weatherCache.value.containsKey(amsterdam) }
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, attempts); assertNull(model.weatherErrors.value[amsterdam]) }
    }

    @Test fun requestsThatNeverCompleteLeaveTheLoadingScreenWithoutRestarting() {
        val (model, _) = screen(WeatherViewModelSources(
            MutableStateFlow(listOf(amsterdam)),
            locate = { CompletableDeferred<WeatherLocation?>().await() },
            weather = { CompletableDeferred<WeatherResponse>().await() },
            publish = { _, _ -> }, requestTimeoutMillis = 250L))
        awaitCoverGone()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(model.isLoading.value)
            assertFalse(model.isLocating.value)
            assertTrue(model.loadingLocations.value.isEmpty())
            assertNotNull(model.weatherErrors.value[amsterdam])
        }
    }

    @Test fun foregroundReturnFollowsTheNewGpsCityWithoutAddingAnUndeletableOldCity() {
        var gps = amsterdam
        var locationsRequested = 0
        val initialGps = CompletableDeferred<Unit>()
        val (model, foreground) = screen(WeatherViewModelSources(
            MutableStateFlow(listOf(rotterdam)),
            locate = { locationsRequested++; if (locationsRequested == 1) initialGps.await(); gps }, weather = { sample },
            // A widget update that never completes must not block showing weather.
            publish = { _, _ -> CompletableDeferred<Unit>().await() }))
        compose.runOnIdle {
            assertTrue(model.weatherCache.value.containsKey(rotterdam))
            initialGps.complete(Unit)
        }
        awaitCoverGone()
        compose.onNodeWithText("Amsterdam").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(model.isLoading.value)
            // Reproduce a legacy current-widget coordinate snapshot retained in the VM.
            model.openWidgetLocation(amsterdam)
            foreground.registry.currentState = Lifecycle.State.STARTED
            gps = denHaag
            foreground.registry.currentState = Lifecycle.State.RESUMED
            foreground.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitUntil(5_000) { model.gpsLocation.value == denHaag }
        compose.onNodeWithText("Den Haag").assertIsDisplayed()
        compose.onAllNodesWithText("Amsterdam").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(denHaag, rotterdam), model.activeLocations.value)
            assertEquals(listOf(rotterdam), model.savedLocations.value)
            assertEquals(2, locationsRequested)
        }
        // Repeat another return without any movement; no duplicate current city.
        compose.runOnIdle {
            foreground.registry.currentState = Lifecycle.State.STARTED
            foreground.registry.currentState = Lifecycle.State.RESUMED
            foreground.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitUntil(3_000) { locationsRequested == 3 }
        compose.onNodeWithText("Den Haag").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(denHaag, rotterdam), model.activeLocations.value) }
    }
}
