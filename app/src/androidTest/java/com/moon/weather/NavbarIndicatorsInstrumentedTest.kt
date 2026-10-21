package com.moon.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
class NavbarIndicatorsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val indicators = SemanticsMatcher("Navbar page") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("navbar-page-") == true
    }

    @Test
    fun eightSlotsFollowTheSelectedCityAndTouchTargetsFollowTheWindow() {
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        compose.setContent {
            pager = rememberPagerState(pageCount = { 20 })
            scope = rememberCoroutineScope()
            val mask = remember { createNavbarMaskBitmap() }
            Box(Modifier.fillMaxSize().background(Color(0xFF4B5111))) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page -> Text("City $page", color = Color.White) }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 120.dp)) {
                    NavbarTouchShield(mask, Modifier.matchParentSize())
                    WeatherBottomNavigation(pager, {}, {})
                }
            }
        }
        compose.onAllNodes(indicators).assertCountEquals(8)
        compose.onNodeWithContentDescription("Current location page", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("navbar-page-6").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(6, pager.currentPage) }
        compose.onNodeWithTag("navbar-page-0").assertExists()
        compose.onNodeWithTag("navbar-page-6").assertIsSelected()
        compose.onNodeWithTag("navbar-page-7").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(7, pager.currentPage) }
        compose.onAllNodes(indicators).assertCountEquals(8)
        compose.onNodeWithTag("navbar-page-0").assertDoesNotExist()
        compose.onNodeWithTag("navbar-page-8").assertExists()
        compose.onNodeWithContentDescription("Current location page", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("navbar-page-4").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(4, pager.currentPage) }
        compose.onNodeWithTag("navbar-page-1").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, pager.currentPage) }
        compose.onNodeWithContentDescription("Current location page", useUnmergedTree = true).assertExists()
        compose.runOnIdle { scope.launch { pager.scrollToPage(19) } }
        compose.waitForIdle()
        compose.onAllNodes(indicators).assertCountEquals(8)
        compose.onNodeWithTag("navbar-page-12").assertExists()
        compose.onNodeWithTag("navbar-page-19").assertIsSelected()
        val picture = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "navbar-eight-indicators.png").outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun savedLocationsWithoutGpsUseEightCircles() {
        compose.setContent {
            val pager = rememberPagerState(pageCount = { 8 })
            Box(Modifier.fillMaxSize().background(Color(0xFF4B5111))) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    WeatherBottomNavigation(pager, {}, {}, hasCurrentLocation = false)
                }
            }
        }
        compose.onAllNodes(indicators).assertCountEquals(8)
        compose.onNodeWithContentDescription("Current location page", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("navbar-page-0").assertContentDescriptionEquals("Go to city 1")
    }

    @Test
    fun bottomCornersStaySolidWhileTheTopCurveRemains() {
        val mask = createNavbarMaskBitmap().asAndroidBitmap()
        for (x in 0 until mask.width) {
            assertEquals("Bottom edge must reach both corners", 255, android.graphics.Color.alpha(mask.getPixel(x, mask.height - 1)))
        }
        val y = mask.height / 3
        assertTrue(android.graphics.Color.alpha(mask.getPixel(mask.width / 2, y)) > android.graphics.Color.alpha(mask.getPixel(0, y)))
    }
}
