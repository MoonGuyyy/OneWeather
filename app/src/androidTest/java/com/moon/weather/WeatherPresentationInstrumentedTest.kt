package com.moon.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.moon.weather.ui.ShadowedLocationPin
import com.moon.weather.ui.WeatherLaunchBlue
import com.moon.weather.ui.WeatherLaunchCover
import com.moon.weather.ui.TwidgetLoadingSpinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherPresentationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun launchCoverHidesPartialContentAndBlocksTouchesUntilReady() {
        val ready = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Partially loaded weather", color = Color.White,
                    modifier = Modifier.testTag("weather-content").clickable { clicks++ })
                WeatherLaunchCover(ready = ready.value)
            }
        }
        val loading = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(WeatherLaunchBlue.toArgb(), loading.getPixel(4, 4))
        compose.onNodeWithTag("weather-content").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks); ready.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("weather-launch-cover").assertDoesNotExist()
        compose.onNodeWithTag("weather-content").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun twidgetSpinnerReallyAnimates() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(WeatherLaunchBlue), contentAlignment = Alignment.Center) {
                TwidgetLoadingSpinner(Modifier.size(64.dp).testTag("spinner-preview"))
            }
        }
        val before = compose.onNodeWithTag("spinner-preview").captureToImage().asAndroidBitmap()
        // AnimatedVectorDrawable uses the Android frame clock, not Compose's test clock.
        Thread.sleep(650)
        val after = compose.onNodeWithTag("spinner-preview").captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until after.height) for (x in 0 until after.width) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
        }
        assertTrue("The original four-dot drawable must animate", changed > 100)
    }

    @Test
    fun locationPinHasASoftShadowThatCanDisappearCompletely() {
        val alpha = mutableStateOf(.45f)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).testTag("pin-preview"), contentAlignment = Alignment.Center) {
                    ShadowedLocationPin(alpha.value, Modifier.size(24.dp))
                }
            }
        }
        val before = compose.onNodeWithTag("pin-preview").captureToImage().asAndroidBitmap()
        compose.runOnIdle { alpha.value = 0f }
        val after = compose.onNodeWithTag("pin-preview").captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until after.height) for (x in 0 until after.width) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            assertEquals(Color.White.toArgb(), after.getPixel(x, y))
        }
        assertTrue("The pin must cast a visible shadow before the pill forms", changed > 20)
    }
}
