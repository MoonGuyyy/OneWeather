package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.AppearanceMode
import com.moon.weather.ui.SettingsDialog
import com.moon.weather.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appearanceMatchesSystemOrChosenThemeAndBackReturnsToSettings() {
        val mode = mutableStateOf(AppearanceMode.SYSTEM)
        var dismissed = false
        compose.setContent {
            WeatherTheme(darkTheme = mode.value != AppearanceMode.LIGHT, dynamicColor = false) {
                SettingsDialog(true, true, mode.value, 1f, {}, {}, { mode.value = it }, { dismissed = true })
            }
        }
        export("settings-dark")
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("System default").assertIsOn()
        compose.onNodeWithText("Dark").assertIsSelected().assertIsNotEnabled()
        export("appearance-system-dark")
        compose.onNodeWithText("System default").performClick()
        compose.runOnIdle { assertEquals(AppearanceMode.DARK, mode.value) }
        compose.onNodeWithText("Light").performClick()
        compose.runOnIdle { assertEquals(AppearanceMode.LIGHT, mode.value) }
        compose.onNodeWithText("Light").assertIsSelected()
        export("appearance-light")
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals(AppearanceMode.DARK, mode.value) }
        export("appearance-dark")
        compose.onNodeWithText("System default").performClick()
        compose.runOnIdle { assertEquals(AppearanceMode.SYSTEM, mode.value) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Temperature").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test
    fun weatherUnitsAndClockRemainFunctional() {
        val celsius = mutableStateOf(true)
        val military = mutableStateOf(true)
        val mph = mutableStateOf(false)
        compose.setContent {
            WeatherTheme(darkTheme = false, dynamicColor = false) {
                SettingsDialog(celsius.value, military.value, AppearanceMode.LIGHT, 1f,
                    { celsius.value = it }, { military.value = it }, {}, {},
                    useMph = mph.value, onUseMphChange = { mph.value = it })
            }
        }
        export("settings-light")
        compose.onNodeWithText("Temperature").performClick()
        compose.onNodeWithText("Fahrenheit").performClick()
        compose.runOnIdle { assertEquals(false, celsius.value) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Fahrenheit").assertIsDisplayed()
        compose.onNodeWithText("Time format").performScrollTo().performClick()
        compose.onNodeWithText("12-hour").performClick()
        compose.runOnIdle { assertEquals(false, military.value) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("12-hour").assertIsDisplayed()
        compose.onNodeWithText("Wind speed").performScrollTo().performClick()
        compose.onNodeWithText("Miles per hour").performClick()
        compose.runOnIdle { assertEquals(true, mph.value) }
        compose.onNodeWithText("Miles per hour").assertIsSelected()
        compose.onNodeWithText("Kilometers per hour").performClick()
        compose.runOnIdle { assertEquals(false, mph.value) }
    }

    private fun export(name: String) {
        // Let the touch ripple finish before capturing the resting appearance.
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        Thread.sleep(400) // Android's ripple runs on the native frame clock.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
