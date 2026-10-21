package com.moon.weather

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.WeatherScreen
import com.moon.weather.ui.WeatherViewModel
import com.moon.weather.ui.WeatherViewModelSources
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.UnknownHostException

/** Public-city fixtures only: does not fetch real location or change device connectivity. */
class WeatherOfflineInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val paris = WeatherLocation("Paris", 48.8566, 2.3522)

    private fun screen(dark: Boolean, fetch: suspend () -> WeatherResponse): WeatherViewModel {
        lateinit var model: WeatherViewModel
        lateinit var stillFrame: LifecycleOwner
        compose.runOnUiThread {
            stillFrame = object : LifecycleOwner {
                private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
                override val lifecycle: Lifecycle get() = registry
            }
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
            model = WeatherViewModel(compose.activity.application as Application, WeatherViewModelSources(
                MutableStateFlow(listOf(paris)), locate = { null }, weather = { fetch() }, publish = { _, _ -> }))
            compose.activity.viewModelStore.put("offline-fixture", model)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides stillFrame) {
                WeatherTheme(darkTheme = dark, dynamicColor = false) { WeatherScreen(model) }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("weather-launch-cover").fetchSemanticsNodes().isEmpty()
        }
        return model
    }

    private fun screenshot(name: String) {
        val file = File(compose.activity.getExternalFilesDir(null), "offline-review/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun offlineRetryRetainsTheGlassCardAndRecoversWithoutTechnicalErrors() {
        var requests = 0
        val retry = CompletableDeferred<WeatherResponse>()
        val model = screen(dark = true) {
            requests++
            if (requests == 1) throw UnknownHostException("Unable to resolve host example.invalid: No address associated with hostname")
            retry.await()
        }
        compose.onNodeWithText("Forecast on pause").assertIsDisplayed()
        compose.onAllNodesWithText("Unable to resolve host", substring = true).assertCountEquals(0)
        assertEquals(compose.activity.getString(R.string.weather_connection_unavailable), model.weatherErrors.value[paris])
        screenshot("offline-dark")
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retrying…").assertIsDisplayed()
        compose.onNodeWithTag("weather-recovery-action").assertIsNotEnabled()
        compose.onNodeWithText("Forecast on pause").assertIsDisplayed()
        screenshot("offline-retrying")
        compose.runOnIdle { retry.complete(WeatherWidgetPreviewPublisher.sampleWeather) }
        compose.waitUntil(5_000) { model.weatherCache.value.containsKey(paris) }
        compose.onNodeWithTag("weather-recovery-card").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, requests); assertNull(model.weatherErrors.value[paris]) }
    }

    @Test fun serviceFailuresAlsoUseFriendlyCopyAndTheLightGlassSurface() {
        val model = screen(dark = false) { throw IllegalStateException("HTTP 503: internal backend stack trace") }
        compose.onNodeWithText("Forecast on pause").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.weather_service_unavailable)).assertIsDisplayed()
        compose.onAllNodesWithText("HTTP 503", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("weather-recovery-action").assertIsEnabled()
        assertEquals(compose.activity.getString(R.string.weather_service_unavailable), model.weatherErrors.value[paris])
        screenshot("offline-light")
    }
}
