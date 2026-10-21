package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.components.GraphCanvas
import com.moon.weather.ui.components.HourlyWeatherItemUI
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class HourlyGraphInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun acceleratingRiseAndRoundedPeakRenderWithoutTouchingRainIndicators() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            GraphCanvas(listOf(21, 22, 24, 27, 29, 30, 29, 27).map {
                HourlyWeatherItemUI("Now", it, it, 0, R.drawable.clear_day, Color.White)
            }, rememberLazyListState(), Modifier.width(350.dp).height(148.dp)
                .background(Color(0xFF101F30)).testTag("graph"))
        }
        val bitmap = compose.onNodeWithTag("graph").captureToImage().asAndroidBitmap()
        val density = context.resources.displayMetrics.density
        for (y in (128 * density).roundToInt() until bitmap.height) for (x in 0 until bitmap.width) {
            assertTrue("The curved graph must leave the rain row clear",
                android.graphics.Color.red(bitmap.getPixel(x, y)) < 100)
        }
        File(context.getExternalFilesDir(null), "hourly-flowing-curve.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun twoReadingsDrawAStraightSlopeAndLeaveTheRainIndicatorsClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val background = Color(0xFF101F30)
        compose.setContent {
            GraphCanvas(listOf(0, 30).map {
                HourlyWeatherItemUI("Now", it, it, 0, R.drawable.clear_day, Color.White)
            }, rememberLazyListState(), Modifier.width(350.dp).height(148.dp)
                .background(background).testTag("graph"))
        }
        val bitmap = compose.onNodeWithTag("graph").captureToImage().asAndroidBitmap()
        val density = context.resources.displayMetrics.density
        fun isLine(pixel: Int): Boolean = maxOf(
            abs(android.graphics.Color.red(pixel) - 16),
            abs(android.graphics.Color.green(pixel) - 31),
            abs(android.graphics.Color.blue(pixel) - 48)) > 100
        // These positions would miss the old eased curve, which started and ended flat.
        for (fraction in listOf(.2f, .8f)) {
            val x = ((23f + 50f * fraction) * density).roundToInt()
            val y = ((118f - 28f * fraction) * density).roundToInt()
            assertTrue("The rendered graph must follow the straight temperature slope", isLine(bitmap.getPixel(x, y)))
        }
        for (y in (128 * density).roundToInt() until bitmap.height) for (x in 0 until bitmap.width) {
            assertTrue("The graph must stay away from the rain indicator row", !isLine(bitmap.getPixel(x, y)))
        }
        File(context.getExternalFilesDir(null), "hourly-smooth-slope.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
