package com.moon.weather

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.HourlyWeather
import com.moon.weather.data.WeatherLocation
import com.moon.weather.ui.LocalWeatherArtwork
import com.moon.weather.ui.LocationsScreen
import com.moon.weather.ui.ReorderableLocationsList
import com.moon.weather.ui.WeatherArtworkLoader
import com.moon.weather.ui.components.HourlyDetailSheet
import com.moon.weather.ui.reorderKey
import com.moon.weather.ui.theme.WeatherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WeatherPanelGesturesInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortFastDownwardSwipesStayOpenButALongerDragDismisses() {
        var dismissed = false
        val shown = mutableStateOf(true)
        compose.setContent {
            WeatherTheme {
                Box(Modifier.fillMaxSize().background(Color.Blue)) {
                    if (shown.value) HourlyDetailSheet(hours(), "2026-10-03T00:00", true, true) {
                        dismissed = true; shown.value = false
                    }
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        val panel = compose.onNodeWithTag("hourly-detail-surface")
        val initialTop = panel.fetchSemanticsNode().boundsInWindow.top
        panel.performTouchInput {
            swipe(Offset(center.x, 20 * density), Offset(center.x, 44 * density), 80)
        }
        compose.waitForIdle()
        assertFalse("A tiny fast header swipe must not dismiss the forecast", dismissed)
        assertEquals(initialTop, panel.fetchSemanticsNode().boundsInWindow.top, 2f)
        compose.onNodeWithTag("hourly-detail-list").performTouchInput {
            swipe(center, center + Offset(0f, 24 * density), 80)
        }
        compose.waitForIdle()
        assertFalse("Pulling a little at the top of the list must also spring back", dismissed)
        assertEquals(initialTop, panel.fetchSemanticsNode().boundsInWindow.top, 2f)
        panel.performTouchInput {
            swipe(Offset(center.x, 20 * density), Offset(center.x, 210 * density), 400)
        }
        compose.waitForIdle()
        assertTrue("An intentional longer drag must still dismiss", dismissed)
    }

    @Test
    fun outsideTapStillDismissesAfterAResistedSwipe() {
        var dismissed = false
        val shown = mutableStateOf(true)
        compose.setContent {
            WeatherTheme {
                Box(Modifier.fillMaxSize()) {
                    if (shown.value) HourlyDetailSheet(hours(), "2026-10-03T00:00", true, true) {
                        dismissed = true; shown.value = false
                    }
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        val panel = compose.onNodeWithTag("hourly-detail-surface")
        panel.performTouchInput {
            swipe(Offset(center.x, 20 * density), Offset(center.x, 44 * density), 80)
        }
        compose.waitForIdle()
        val bounds = panel.fetchSemanticsNode().boundsInWindow
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.center.x, bounds.top - 10 * density, 0)
        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, bounds.center.x, bounds.top - 10 * density, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        automation.injectInputEvent(down, true)
        automation.injectInputEvent(up, true)
        down.recycle(); up.recycle()
        compose.waitForIdle()
        compose.waitUntil(5_000) { dismissed }
        assertTrue("The resistance must not block outside taps", dismissed)
    }

    @Test
    fun mixedHeightCitiesFollowTheFingerAndPersistOnlyOnDrop() {
        val cities = listOf(
            WeatherLocation("Paris", 48.85, 2.35, "France"),
            WeatherLocation("A much longer city name that wraps onto several lines", 1.0, 1.0),
            WeatherLocation("Paris", 33.66, -95.55, "United States"),
            WeatherLocation("Tokyo", 35.67, 139.65, "Japan")
        )
        val saved = mutableStateOf(cities)
        var commits = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val artwork = runBlocking { WeatherArtworkLoader.load(context.resources, R.drawable.sunnyday,
            IntSize(metrics.widthPixels, metrics.heightPixels))!! }
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            WeatherTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWeatherArtwork provides artwork) {
                    Box(Modifier.fillMaxSize()) {
                        Image(artwork.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        LocationsScreen(saved.value, cities.first(), emptyMap(), true, true, {}, {}, {
                            commits++; saved.value = it
                        }, {}, {}, {}, {})
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("locations-list")
        val first = compose.onNodeWithTag("location-${cities.first().reorderKey()}")
        val start = first.fetchSemanticsNode().boundsInRoot.center
        val destination = compose.onNodeWithTag("location-${cities[2].reorderKey()}")
            .fetchSemanticsNode().boundsInRoot.center.y + 8f
        val bounds = list.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        list.performTouchInput {
            down(start - bounds.topLeft)
            advanceEventTime(650)
            moveBy(Offset(0f, 1f))
        }
        compose.mainClock.advanceTimeBy(32)
        val step = (destination - start.y - 1f) / 12
        var expectedY = start.y + 1f
        repeat(12) {
            list.performTouchInput { moveBy(Offset(0f, step)) }
            expectedY += step
            compose.mainClock.advanceTimeBy(48)
            assertEquals("The lifted city must not jump when rows exchange positions", expectedY,
                first.fetchSemanticsNode().boundsInRoot.center.y, 3f)
        }
        compose.runOnIdle { assertEquals("Do not save or reload weather during drag frames", 0, commits) }
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(listOf(cities[1], cities[2], cities[0], cities[3]), saved.value)
        }
        val screenshot = compose.onNodeWithTag("locations-screen").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "locations-panel-render.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test
    fun holdingACityAtTheEdgeScrollsToCitiesInitiallyOffScreen() {
        val cities = (0 until 14).map { WeatherLocation("City $it", it.toDouble(), 0.0, "Country") }
        val saved = mutableStateOf(cities)
        var commits = 0
        compose.setContent {
            WeatherTheme {
                Box(Modifier.fillMaxSize().background(Color.Blue)) {
                    ReorderableLocationsList(saved.value, {}, {}, { commits++; saved.value = it }, Modifier.size(280.dp, 320.dp))
                }
            }
        }
        val list = compose.onNodeWithTag("locations-list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("location-${cities.first().reorderKey()}")
        val start = first.fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        val target = Offset(start.x, bounds.height - 12 * compose.activity.resources.displayMetrics.density)
        compose.mainClock.autoAdvance = false
        list.performTouchInput { down(start); advanceEventTime(650); moveBy(Offset(0f, 1f)) }
        compose.mainClock.advanceTimeBy(32)
        repeat(10) {
            list.performTouchInput { moveBy((target - start - Offset(0f, 1f)) / 10f) }
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(1_200)
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, commits)
            assertTrue("Dragging at the edge must reach beyond the initially visible rows", saved.value.indexOf(cities.first()) >= 8)
        }
    }

    private fun hours() = HourlyWeather(
        (0 until 48).map { "2026-10-%02dT%02d:00".format(3 + it / 24, it % 24) },
        List(48) { 25.0 }, List(48) { 20 }, List(48) { 2 }, List(48) { 55.0 },
        List(48) { 12.0 }, List(48) { 110.0 }, List(48) { 1013.0 }, List(48) { 6.0 },
        List(48) { 10000.0 }, List(48) { 26.0 }, List(48) { 14.0 }
    )
}
