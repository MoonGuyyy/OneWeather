package com.moon.weather

import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.moon.weather.ui.LocationsTransition
import com.moon.weather.ui.pressHaptic
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeatherInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun feedbackStartsOnDownAndDoesNotRepeatOnRelease() {
        var feedback = 0
        var actions = 0
        compose.setContent {
            Text("Press", Modifier.size(80.dp).pressHaptic(feedback = { feedback++ }).clickable { actions++ })
        }
        compose.onNodeWithText("Press").performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(1,feedback); assertEquals(0,actions) }
        compose.onNodeWithText("Press").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,feedback); assertEquals(1,actions) }
    }

    @Test fun menuEntersAndExitsThroughAnAnimatedTransition() {
        val visible = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                LocationsTransition(visible.value,true,{ visible.value = false }) { Text("Menu content") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(32)
        val early = compose.onNodeWithText("Menu content").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(128)
        val middle = compose.onNodeWithText("Menu content").fetchSemanticsNode().boundsInRoot.left
        assertTrue(middle > early)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Menu content").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Menu content").assertDoesNotExist()
    }

    @Test fun predictiveMenuCornersRevealTheBackgroundInsteadOfSharpWhiteCorners() {
        lateinit var activity: ComponentActivity
        compose.setContent {
            activity = LocalContext.current as ComponentActivity
            Box(Modifier.fillMaxSize().background(Color.Blue)) {
                LocationsTransition(true,true,{}) {
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("rounded-menu"))
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,500f,0f,BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f,500f,.5f,BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("rounded-menu").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val x = (bounds.right - 3).toInt().coerceIn(0,image.width-1)
        val y = (bounds.top + 3).toInt().coerceIn(0,image.height-1)
        assertEquals("Rounded corner must show the blue scene behind the menu",Color.Blue.toArgb(),image.getPixel(x,y))
        compose.runOnIdle { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
    }

    @Test fun cancelledPredictiveBackRestoresMenuAndCommittedBackClosesIt() {
        val visible = mutableStateOf(true)
        lateinit var activity: ComponentActivity
        compose.setContent {
            activity = LocalContext.current as ComponentActivity
            Box(Modifier.fillMaxSize()) {
                LocationsTransition(visible.value,true,{ visible.value = false }) { Text("Predictive menu") }
            }
        }
        compose.waitForIdle()
        val original = compose.onNodeWithText("Predictive menu").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,500f,0f,BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f,500f,.5f,BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("Predictive menu").fetchSemanticsNode().boundsInRoot.left < original)
        compose.runOnIdle { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(original, compose.onNodeWithText("Predictive menu").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,500f,0f,BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(250f,500f,.9f,BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Predictive menu").assertDoesNotExist()
    }
}
