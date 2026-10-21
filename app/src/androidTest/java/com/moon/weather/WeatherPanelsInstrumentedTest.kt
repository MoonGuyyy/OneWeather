package com.moon.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.DailyWeather
import com.moon.weather.data.HourlyWeather
import com.moon.weather.ui.LocalWeatherArtwork
import com.moon.weather.ui.LocalWeatherGlassOrigin
import com.moon.weather.ui.WeatherArtworkLoader
import com.moon.weather.ui.components.HourlyDetailSheet
import com.moon.weather.ui.components.SunriseSunsetCard
import com.moon.weather.ui.theme.WeatherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class WeatherPanelsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun frostedHourlySheetWorksAcrossWindowsAndExtendsThroughNavigationArea() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val artwork = runBlocking { WeatherArtworkLoader.load(context.resources, R.drawable.sunnyday,
            IntSize(metrics.widthPixels, metrics.heightPixels))!! }
        val origin = mutableStateOf<LayoutCoordinates?>(null)
        val hourly = HourlyWeather(
            (0 until 48).map { "2026-10-%02dT%02d:00".format(3 + it / 24, it % 24) },
            List(48) { 25.0 }, List(48) { 20 }, List(48) { 2 }, List(48) { 55.0 },
            List(48) { 12.0 }, List(48) { 110.0 }, List(48) { 1013.0 }, List(48) { 6.0 },
            List(48) { 10000.0 }, List(48) { 26.0 }, List(48) { 14.0 })
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        }
        compose.setContent {
            WeatherTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWeatherArtwork provides artwork,
                    LocalWeatherGlassOrigin provides origin.value) {
                    Box(Modifier.fillMaxSize().onGloballyPositioned { origin.value = it }) {
                        Image(artwork.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        HourlyDetailSheet(hourly, "2026-10-03T00:00", true, true, onDismiss = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Next 48 hours").assertIsDisplayed()
        val surface = compose.onNodeWithTag("hourly-detail-surface")
        surface.assertIsDisplayed()
        val bounds = surface.fetchSemanticsNode().boundsInWindow
        assertTrue("The frosted surface must extend to the bottom of the window", bounds.bottom >= metrics.heightPixels - 2)
        saveScreenshot("hourly-sheet-render.png")
    }

    @Test
    fun mainSunGraphRendersTheWidgetPaletteAndGlowingSun() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val artwork = runBlocking { WeatherArtworkLoader.load(context.resources, R.drawable.sunnyday,
            IntSize(metrics.widthPixels, metrics.heightPixels))!! }
        val daily = DailyWeather(listOf("2026-10-03"), listOf(0), listOf(25.0), listOf(14.0),
            listOf("2026-10-03T06:00"), listOf("2026-10-03T18:00"), listOf(6.0), listOf(10))
        compose.setContent {
            WeatherTheme {
                CompositionLocalProvider(LocalWeatherArtwork provides artwork) {
                    Box(Modifier.fillMaxSize()) {
                        Image(artwork.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        SunriseSunsetCard(daily, "2026-10-03T13:00", Modifier.testTag("sun-graph-preview"))
                    }
                }
            }
        }
        compose.onNodeWithText("Sunrise & Sunset").assertIsDisplayed()
        val bitmap = compose.onNodeWithTag("sun-graph-preview").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "main-sun-graph-render.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
