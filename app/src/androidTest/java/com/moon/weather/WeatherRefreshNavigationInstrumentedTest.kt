package com.moon.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.*
import com.moon.weather.ui.theme.WeatherTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
class WeatherRefreshNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshCircleFollowsThePullRefreshesOnceAndLeavesAfterCompletion() {
        val completion = CompletableDeferred<Unit>()
        var refreshes = 0
        var ticks = 0
        compose.setContent {
            WeatherTheme {
                val list = remember { LazyListState() }
                val refresh = rememberWeatherPullRefreshState("city",
                    canPull = { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 },
                    onRefresh = { refreshes++; completion.await() }, onTick = { ticks++ })
                val pager = rememberPagerState(pageCount = { 1 })
                Box(Modifier.fillMaxSize().background(WeatherLaunchBlue)) {
                    HorizontalPager(pager, Modifier.fillMaxSize().nestedScroll(refresh.connection)) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("weather-list")) {
                            items(20) { Text("Weather row $it", Modifier.fillMaxWidth().height(80.dp).padding(20.dp), color = Color.White) }
                        }
                    }
                    WeatherRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        val list = compose.onNodeWithTag("weather-list")
        val indicator = compose.onNodeWithTag("weather-refresh-indicator")
        indicator.assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        list.performTouchInput {
            down(Offset(center.x, 150 * density)); moveBy(Offset(0f, 40 * density))
        }
        compose.mainClock.advanceTimeBy(32)
        val firstTop = indicator.fetchSemanticsNode().boundsInRoot.top
        list.performTouchInput { moveBy(Offset(0f, 30 * density)) }
        compose.mainClock.advanceTimeBy(32)
        assertTrue("The circle must follow the finger down", indicator.fetchSemanticsNode().boundsInRoot.top > firstTop + 10)
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        indicator.assertDoesNotExist()
        assertEquals(0, refreshes)
        assertEquals(0, ticks)

        list.performTouchInput { swipe(Offset(center.x, 150 * density), Offset(center.x, 350 * density), 500) }
        compose.waitForIdle()
        assertEquals(1, refreshes)
        assertEquals("One soft tick per refresh", 1, ticks)
        indicator.assertIsDisplayed()
        val heldTop = indicator.fetchSemanticsNode().boundsInRoot.top
        list.performTouchInput { swipe(Offset(center.x, 150 * density), Offset(center.x, 350 * density), 500) }
        compose.waitForIdle()
        assertEquals("A pending refresh cannot start a duplicate request", 1, refreshes)
        assertEquals(heldTop, indicator.fetchSemanticsNode().boundsInRoot.top, 2f)
        val picture = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "pull-refresh-render.png").outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { completion.complete(Unit) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("weather-refresh-indicator").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, ticks)
    }

    @Test
    fun pullingWhileScrolledOnlyScrollsTheForecast() {
        var refreshes = 0
        compose.setContent {
            WeatherTheme {
                val list = remember { LazyListState(firstVisibleItemIndex = 8) }
                val refresh = rememberWeatherPullRefreshState("city",
                    canPull = { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 },
                    onRefresh = { refreshes++ }, onTick = {})
                Box(Modifier.fillMaxSize().nestedScroll(refresh.connection)) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("weather-list")) {
                        items(30) { Text("Weather row $it", Modifier.fillMaxWidth().height(80.dp)) }
                    }
                    WeatherRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("weather-list").performTouchInput {
            swipe(center, center + Offset(0f, 160 * density), 600)
        }
        compose.waitForIdle()
        assertEquals(0, refreshes)
        compose.onNodeWithTag("weather-refresh-indicator").assertDoesNotExist()
    }

    @Test
    fun dotsHaveLargeTargetsAndTheSolidNavbarBlocksTheForecastBehindIt() {
        var underlyingClicks = 0
        lateinit var pager: PagerState
        compose.setContent {
            WeatherTheme {
                pager = rememberPagerState(pageCount = { 4 })
                val mask = remember { createNavbarMaskBitmap() }
                Box(Modifier.fillMaxSize().background(Color.Blue).testTag("navigation-fixture").clickable { underlyingClicks++ }) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        Box(Modifier.fillMaxSize()) { Text("City $page", color = Color.White) }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 120.dp)) {
                        NavbarTouchShield(mask, Modifier.matchParentSize())
                        WeatherBottomNavigation(pager, {}, {})
                    }
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        val root = compose.onNodeWithTag("navigation-fixture")
        root.performTouchInput { click(Offset(center.x, height - 5 * density)) }
        assertEquals("Missing a dot in the solid part must never click a forecast underneath", 0, underlyingClicks)
        root.performTouchInput { click(Offset(2 * density, height - 2 * density)) }
        root.performTouchInput { click(Offset(width - 2 * density, height - 2 * density)) }
        assertEquals("Both square bottom corners must block the forecast too", 0, underlyingClicks)
        root.performTouchInput { click(Offset(center.x, height - 110 * density)) }
        assertEquals("The transparent top of the fade must remain interactive", 1, underlyingClicks)
        val dot = compose.onNodeWithTag("navbar-page-2", useUnmergedTree = true)
        val target = dot.fetchSemanticsNode().touchBoundsInRoot
        assertTrue(target.width >= 47 * density && target.height >= 47 * density)
        dot.performTouchInput { click(Offset(3 * density, 3 * density)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, pager.currentPage) }
        assertEquals(1, underlyingClicks)
    }
}
