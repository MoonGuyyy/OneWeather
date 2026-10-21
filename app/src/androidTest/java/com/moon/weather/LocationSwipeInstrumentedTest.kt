package com.moon.weather

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.WeatherLocation
import com.moon.weather.ui.LocationsScreen
import com.moon.weather.ui.LocationSwipeToDelete
import com.moon.weather.ui.reorderKey
import com.moon.weather.ui.theme.WeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocationSwipeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val city = WeatherLocation("Paris", 48.86, 2.35, "France")
    private val other = WeatherLocation("Tokyo", 35.67, 139.65, "Japan")
    private val saved = mutableStateOf(listOf(city, other))
    private var deletes = 0
    private var selections = 0

    private fun show() {
        compose.setContent {
            WeatherTheme(darkTheme = true, dynamicColor = false) {
                LocationsScreen(saved.value, city, emptyMap(), true, true, { selections++ },
                    { deletes++; saved.value = saved.value - it }, { saved.value = it }, {}, {}, {}, {})
            }
        }
    }

    private fun row() = compose.onNodeWithTag("location-${city.reorderKey()}")
    private fun card() = compose.onNode(hasText("--°") and
        hasAnyAncestor(hasTestTag("location-${city.reorderKey()}")), useUnmergedTree = true)

    @Test fun shortFastSwipesRevealTrashAndSpringBackWithoutDeleting() {
        show()
        val initial = card().fetchSemanticsNode().boundsInRoot.right
        val width = row().fetchSemanticsNode().boundsInRoot.width
        row().performTouchInput { down(Offset(width * .9f, center.y)); moveBy(Offset(-width * .22f, 0f)) }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "location-delete-reveal.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // The left edge is clipped by the stationary rounded container; measure the right edge.
        val travel = initial - card().fetchSemanticsNode().boundsInRoot.right
        assertTrue("The card must resist the finger (travel=$travel, width=$width)",
            travel > width * .1f && travel < width * .2f)
        compose.onNodeWithTag("delete-reveal-${city.reorderKey()}", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deletes) }
        row().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(initial, card().fetchSemanticsNode().boundsInRoot.right, 1f)
        compose.runOnIdle { assertEquals(0, deletes); assertEquals(0, selections) }
        row().performTouchInput { swipe(Offset(width * .9f, center.y), Offset(width * .65f, center.y), 64) }
        compose.runOnIdle { assertEquals("Fling speed must not cause deletion", 0, deletes) }
        assertEquals(initial, card().fetchSemanticsNode().boundsInRoot.right, 1f)
        compose.onNodeWithText("Tokyo").performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun longSwipeDeletesOnlyOnceAfterReleaseAndKeepsGps() {
        show()
        val width = row().fetchSemanticsNode().boundsInRoot.width
        row().performTouchInput { down(Offset(width * .96f, center.y)); moveBy(Offset(-width * .88f, 0f)) }
        compose.onNodeWithTag("delete-reveal-${city.reorderKey()}", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Release to delete"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "location-delete-armed.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { assertEquals("Even an armed swipe waits for release", 0, deletes); assertEquals(2, saved.value.size) }
        row().performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, deletes); assertEquals(listOf(other), saved.value); assertEquals(0, selections) }
        row().assertDoesNotExist()
        compose.onNodeWithText("Paris").assertIsDisplayed() // GPS card remains.
        compose.onNodeWithText("Tokyo").assertIsDisplayed()
    }

    @Test fun reversingOrCancellingAnArmedSwipeRestoresTheCard() {
        show()
        val width = row().fetchSemanticsNode().boundsInRoot.width
        val initial = card().fetchSemanticsNode().boundsInRoot.right
        row().performTouchInput {
            down(Offset(width * .96f, center.y))
            moveBy(Offset(-width * .88f, 0f))
            moveBy(Offset(width * .65f, 0f))
            up()
        }
        compose.waitForIdle()
        assertEquals(initial, card().fetchSemanticsNode().boundsInRoot.right, 1f)
        row().performTouchInput {
            down(Offset(width * .96f, center.y)); moveBy(Offset(-width * .88f, 0f)); cancel()
        }
        compose.waitForIdle()
        assertEquals(initial, card().fetchSemanticsNode().boundsInRoot.right, 1f)
        compose.runOnIdle { assertEquals(0, deletes); assertEquals(0, selections) }
    }

    @Test fun openingTheLidAndHapticHappenWhenTheReleaseThresholdIsCrossed() {
        var feedback = 0
        compose.setContent {
            LocationSwipeToDelete("test", true, { deletes++ },
                Modifier.size(300.dp,110.dp).testTag("swipe"), onArmedHaptic = { feedback++ }) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        val swipe = compose.onNodeWithTag("swipe")
        val width = swipe.fetchSemanticsNode().boundsInRoot.width
        val lid = compose.onNodeWithTag("delete-reveal-test", useUnmergedTree = true)
        swipe.performTouchInput { down(Offset(width * .95f,center.y)); moveBy(Offset(-width * .25f,0f)) }
        compose.runOnIdle { assertEquals(0,feedback); assertEquals(0,deletes) }
        lid.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Swipe farther to delete"))
        swipe.performTouchInput { moveBy(Offset(-width * .13f,0f)) }
        lid.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Release to delete"))
        compose.runOnIdle { assertEquals("The tick must happen while still pressing",1,feedback); assertEquals(0,deletes) }
        swipe.performTouchInput { moveBy(Offset(-width * .06f,0f)) }
        compose.runOnIdle { assertEquals("Do not buzz on every armed drag frame",1,feedback) }
        swipe.performTouchInput { moveBy(Offset(width * .25f,0f)) }
        lid.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Swipe farther to delete"))
        swipe.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(0,deletes) }
    }

    @Test fun theRealDeleteFeedbackUsesAConfirmationPulseWhenTheLidOpens() {
        val calls = mutableListOf<Int>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recordingView = object : View(context) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                calls += feedbackConstant
                return true
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalView provides recordingView) {
                LocationSwipeToDelete("confirmation",true,{ deletes++ },
                    Modifier.size(300.dp,110.dp).testTag("confirmation-swipe")) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        val swipe = compose.onNodeWithTag("confirmation-swipe")
        val width = swipe.fetchSemanticsNode().boundsInRoot.width
        swipe.performTouchInput { down(Offset(width*.9f,center.y)); moveBy(Offset(-width*.26f,0f)) }
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        swipe.performTouchInput { moveBy(Offset(-width*.12f,0f)) }
        compose.onNodeWithTag("delete-reveal-confirmation",useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Release to delete"))
        compose.runOnIdle {
            assertEquals(listOf(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS),calls)
            assertEquals(0,deletes)
        }
        swipe.performTouchInput { moveBy(Offset(-width*.1f,0f)); up() }
        compose.runOnIdle { assertEquals(1,calls.size); assertEquals(1,deletes) }
    }
}
