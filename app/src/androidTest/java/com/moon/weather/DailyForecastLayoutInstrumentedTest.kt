package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.components.DailyForecastCard
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DailyForecastLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun negativeCelsiusAndThreeDigitFahrenheitStayCompleteOnOneLine() {
        val celsius = mutableStateOf(true)
        val largeText = mutableStateOf(false)
        val sample = WeatherWidgetPreviewPublisher.sampleWeather.daily!!
        val highs = listOf(-50.0, -49.0, -45.0, -102.0, -48.0, -47.0, -39.0)
        val lows = listOf(-55.0, -56.0, -51.0, -104.0, -55.0, -52.0, -49.0)
        val daily = sample.copy(time = (4..10).map { "2026-10-${it.toString().padStart(2, '0')}" },
            maxTemperatures = highs, minTemperatures = lows, weatherCodes = List(7) { 2 })
        compose.setContent {
            WeatherTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText.value) 1.5f else 1f)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF183651)), contentAlignment = Alignment.Center) {
                        DailyForecastCard(daily, celsius.value, Modifier.width(320.dp).testTag("daily-card"))
                    }
                }
            }
        }
        fun assertTemperatures(useCelsius: Boolean) {
            fun convert(value: Double) = if (useCelsius) value.toInt() else value.toInt() * 9 / 5 + 32
            val values = highs.map { "${convert(it)}" } + lows.map { "${convert(it)}°" }
            for (text in values.distinct()) {
                val nodes = compose.onAllNodesWithText(text, useUnmergedTree = true)
                val count = nodes.fetchSemanticsNodes().size
                assertTrue("Temperature $text must be present", count > 0)
                for (index in 0 until count) {
                    nodes[index].assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals("Keep the complete temperature and degree symbol on one line", 1, layouts.single().lineCount)
                    assertFalse("Temperature $text must not be clipped", layouts.single().hasVisualOverflow)
                }
            }
            val lowsBounds = lows.map { "${convert(it)}°" }.distinct().map {
                compose.onAllNodesWithText(it, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
            }
            assertTrue("Low temperatures must share a right edge", lowsBounds.all {
                kotlin.math.abs(it.right - lowsBounds.first().right) < 1f
            })
        }
        assertTemperatures(true)
        compose.runOnIdle { celsius.value = false }
        assertTemperatures(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "daily-negative-fahrenheit.png").outputStream().use {
            compose.onNodeWithTag("daily-card").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { largeText.value = true }
        assertTemperatures(false)
    }
}
