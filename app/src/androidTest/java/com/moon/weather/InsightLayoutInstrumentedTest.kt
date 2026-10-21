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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.components.InsightCard
import com.moon.weather.ui.components.WeatherInsights
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class InsightLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun windUnitsLongTemperaturesAndLargeTextStayInsideTheSmartTile() {
        val sample = WeatherWidgetPreviewPublisher.sampleWeather
        val reading = mutableStateOf(sample.copy(current = sample.current!!.copy(
            temperature = 20.0, windSpeed = 27.0, uvIndex = 1.0, weatherCode = 0), daily = null))
        val mph = mutableStateOf(false)
        val celsius = mutableStateOf(true)
        val scale = mutableStateOf(1f)
        compose.setContent {
            WeatherTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF183651)), contentAlignment = Alignment.Center) {
                        InsightCard(reading.value, celsius.value, mph.value, Modifier.width(320.dp))
                    }
                }
            }
        }
        fun assertReading(value: String) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "smart-wind-layout.png").outputStream().use {
                compose.onNodeWithTag("weather-insight-card").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val layouts = mutableListOf<TextLayoutResult>()
            val metric = compose.onNodeWithText(value)
            metric.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("The reading and its complete unit belong on one line", 1, layouts.single().lineCount)
            assertFalse("Do not hide part of the value or unit", layouts.single().hasVisualOverflow)
            val insight = WeatherInsights.create(reading.value, celsius.value, mph.value).first()
            val message = compose.onNodeWithText(insight.message)
            val label = compose.onNodeWithText(insight.label)
            message.assertIsDisplayed()
            label.assertIsDisplayed()
            val card = compose.onNodeWithTag("weather-insight-card").fetchSemanticsNode().boundsInRoot
            for (node in listOf(metric, message, label)) {
                assertTrue("The complete content must fit above the page dots", node.fetchSemanticsNode().boundsInRoot.bottom < card.bottom - 8)
                val textLayouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
                val layout = textLayouts.single()
                assertFalse("Text '${layout.layoutInput.text}' must fit: size=${layout.size}, lines=${layout.lineCount}, heightOverflow=${layout.didOverflowHeight}, widthOverflow=${layout.didOverflowWidth}", layout.hasVisualOverflow)
            }
        }
        assertReading("27 km/h")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "smart-wind-layout.png").outputStream().use {
            compose.onNodeWithTag("weather-insight-card").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle {
            scale.value = 1.5f
            reading.value = reading.value.copy(current = reading.value.current!!.copy(windSpeed = 147.0))
        }
        assertReading("147 km/h")
        compose.runOnIdle { mph.value = true }
        assertReading("91 mph")
        compose.runOnIdle {
            celsius.value = false
            reading.value = reading.value.copy(current = reading.value.current!!.copy(temperature = -104.0))
        }
        assertReading("-155°F")
    }
}
