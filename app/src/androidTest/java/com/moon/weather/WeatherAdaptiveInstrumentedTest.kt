package com.moon.weather

import android.graphics.Bitmap
import android.graphics.Rect
import android.Manifest
import androidx.test.rule.GrantPermissionRule
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature as TestFoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.HourlyWeather
import com.moon.weather.data.DailyWeather
import com.moon.weather.ui.*
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import java.time.LocalDateTime

/** Fit logical windows inside the attached display without changing device resolution or settings. */
class WeatherAdaptiveInstrumentedTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule(order = 2) val folding = WindowLayoutInfoPublisherRule()
    private val size = mutableStateOf(800f to 1280f)
    private val fontScale = mutableStateOf(1f)
    private val cities = listOf(WeatherLocation("Amsterdam", 52.37, 4.90), WeatherLocation("Paris", 48.86, 2.35))
    private val sample = WeatherWidgetPreviewPublisher.sampleWeather.copy(
        hourly = HourlyWeather(List(48) { LocalDateTime.parse("2026-10-03T12:00").plusHours(it.toLong()).toString() },
            List(48) { 17.0 + it % 5 }, List(48) { (it % 4) * 10 }, List(48) { 2 },
            List(48) { 55.0 }, List(48) { 18.0 }, List(48) { 110.0 }, List(48) { 1013.0 },
            List(48) { 6.0 }, List(48) { 10000.0 }, List(48) { 18.0 }, List(48) { 10.0 }),
        daily = DailyWeather(List(7) { "2026-10-%02d".format(3 + it) }, List(7) { 2 },
            List(7) { 19.0 + it % 3 }, List(7) { 10.0 }, List(7) { "2026-10-03T07:10" },
            List(7) { "2026-10-03T19:10" }, List(7) { 6.0 }, List(7) { 10 }))
    private class Foreground : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun screen(): StateRestorationTester {
        lateinit var model: WeatherViewModel
        lateinit var foreground: Foreground
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            foreground = Foreground().apply { registry.currentState = Lifecycle.State.STARTED }
            model = WeatherViewModel(compose.activity.application, WeatherViewModelSources(
                MutableStateFlow(cities), locate = { null }, weather = { sample },
                publish = { _, _ -> }))
            compose.activity.viewModelStore.put("adaptive-fixture", model)
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val nativeDensity = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val (width, height) = size.value
                val scale = min(maxWidth.value * nativeDensity.density / width,
                    maxHeight.value * nativeDensity.density / height)
                val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = width.roundToInt()
                    screenHeightDp = height.roundToInt()
                    smallestScreenWidthDp = min(width, height).roundToInt()
                }
                CompositionLocalProvider(LocalDensity provides Density(scale, fontScale.value),
                    LocalConfiguration provides configuration,
                    LocalLifecycleOwner provides foreground) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("adaptive-capture")) {
                        WeatherTheme(dynamicColor = false) { WeatherScreen(model) }
                    }
                }
            }
        }
        awaitWeather()
        return restoration
    }

    private fun awaitWeather() = compose.waitUntil(12_000) {
        compose.onAllNodesWithTag("adaptive-capture").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
            compose.onAllNodesWithTag("weather-launch-cover").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }

    @Test fun tabletsFoldDisplaysAndCoverScreensKeepCardsAndNavigationInsideTheWindow() {
        screen()
        for ((name, width, height) in listOf(
            Triple("tablet-portrait", 800f, 1280f), Triple("tablet-landscape", 1280f, 800f),
            Triple("fold8-open", 800f, 600f), Triple("fold8-rotated", 600f, 800f),
            Triple("fold8-ultra-open", 800f, 720f), Triple("fold8-ultra-rotated", 720f, 800f),
            Triple("fold8-cover", 400f, 640f), Triple("fold8-ultra-cover", 360f, 840f),
            Triple("split-screen", 480f, 800f))) {
            compose.runOnIdle { size.value = width to height }
            compose.onNodeWithTag("weather-grid-${if (width >= 640f) 2 else 1}").assertIsDisplayed()
            compose.onNodeWithTag("current-temperature").assertIsDisplayed()
            assertInside("weather-bottom-navigation")
            compose.onNodeWithContentDescription("Menu").assertIsDisplayed()
            compose.onNodeWithContentDescription("Search").assertIsDisplayed()
            val temperature = compose.onNodeWithTag("current-temperature").fetchSemanticsNode().boundsInRoot
            val details = compose.onNodeWithTag("weather-current-details").fetchSemanticsNode().boundsInRoot
            assertTrue("Weather description and readings must stay below the temperature", details.top >= temperature.bottom - 1f)
            assertEquals("Weather readings must align with the temperature", temperature.left, details.left, 1f)
            if (width >= 640f) {
                compose.onNodeWithTag("weather-hourly-card").assertIsDisplayed()
                val current = compose.onNodeWithTag("weather-current-conditions").fetchSemanticsNode().boundsInRoot
                val hourly = compose.onNodeWithTag("weather-hourly-card").fetchSemanticsNode().boundsInRoot
                val daily = compose.onNodeWithTag("weather-daily-card").fetchSemanticsNode().boundsInRoot
                assertTrue("Both forecast columns should start below the common weather header", hourly.top >= current.bottom)
                val window = compose.onNodeWithTag("adaptive-capture").fetchSemanticsNode().boundsInRoot
                assertTrue("Both forecast columns need the original gap below the readings",
                    hourly.top - details.bottom >= 72f * window.width / width - 1f)
                assertEquals("The hourly and daily cards must start on the same row", hourly.top, daily.top, 1f)
                assertTrue("Daily weather should occupy the right column", daily.left > hourly.right)
                assertInside("weather-hourly-card")
            }
            compose.waitUntil(12_000) {
                compose.onAllNodesWithTag("weather-artwork-ready").fetchSemanticsNodes().isNotEmpty()
            }
            capture(name)
        }
        compose.runOnIdle { size.value = 720f to 800f; fontScale.value = 1.5f }
        compose.onNodeWithTag("weather-grid-1").assertIsDisplayed()
        assertInside("weather-bottom-navigation")
        capture("large-text")
        val launcher = androidx.core.content.ContextCompat.getDrawable(compose.activity, R.mipmap.ic_launcher)!!
        val icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        launcher.setBounds(0, 0, 512, 512)
        launcher.draw(android.graphics.Canvas(icon))
        saveBitmap("launcher-icon", icon)
        icon.recycle()
    }

    @Test fun forecastColumnsKeepSixteenDpGapsWithoutChangingCardColumns() {
        screen()
        for ((name, width, height) in listOf(
            Triple("card-spacing-tablet-portrait", 800f, 1280f),
            Triple("card-spacing-tablet-landscape", 1280f, 800f),
            Triple("card-spacing-fold-ultra", 800f, 720f))) {
            compose.runOnIdle { size.value = width to height }
            compose.onNodeWithTag("weather-grid-2").performScrollToIndex(2)
            val window = compose.onNodeWithTag("adaptive-capture").getUnclippedBoundsInRoot()
            val expectedGap = 16f * (window.right - window.left).value / width
            for (tags in listOf(
                listOf("weather-hourly-card", "weather-insight-card", "weather-wind-card", "weather-sun-card"),
                listOf("weather-daily-card", "weather-uv-card", "weather-pressure-card"))) {
                val cards = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
                for ((previous, next) in cards.zipWithNext()) {
                    assertEquals("Cards in a column must have a consistent 16dp gap", expectedGap,
                        next.top.value - previous.bottom.value, 1f)
                    assertEquals("Cards must remain in the same column", previous.left.value, next.left.value, 1f)
                }
            }
            capture(name)
            compose.onNodeWithTag("weather-grid-2").performScrollToIndex(0)
        }
    }

    @Test fun cityMenuFillsSquareAndTabletWindowsWithoutExposingWeatherAtTheCorners() {
        compose.runOnIdle { size.value = 800f to 800f }
        screen()
        compose.onNodeWithContentDescription("Menu").performClick()
        for ((name, width, height) in listOf(
            Triple("menu-square", 800f, 800f), Triple("menu-fold-ultra", 800f, 720f),
            Triple("menu-tablet-landscape", 1280f, 800f), Triple("menu-tablet-portrait", 800f, 1280f),
            Triple("menu-phone", 360f, 840f))) {
            compose.runOnIdle { size.value = width to height }
            val window = compose.onNodeWithTag("adaptive-capture").fetchSemanticsNode().boundsInRoot
            val content = compose.onNodeWithTag("locations-content").fetchSemanticsNode().boundsInRoot
            assertEquals("City menu must fill the window horizontally", window.width, content.width, 1f)
            assertEquals("City menu must fill the window vertically", window.height, content.height, 1f)
            assertEquals(window.left, content.left, 1f)
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
            val city = compose.onNodeWithTag("location-52.37:4.9:Amsterdam").fetchSemanticsNode().boundsInRoot
            assertTrue("City cards should span the menu", city.width >= window.width - 25f * window.width / width)
            val bitmap = compose.onNodeWithTag("adaptive-capture").captureToImage().asAndroidBitmap()
            for ((x, y) in listOf(1 to 1, bitmap.width - 2 to 1,
                1 to bitmap.height - 2, bitmap.width - 2 to bitmap.height - 2)) {
                val color = bitmap.getPixel(x, y)
                assertTrue("Menu corners should contain the menu background", color == android.graphics.Color.BLACK ||
                    color == android.graphics.Color.rgb(242, 242, 242))
            }
            saveBitmap(name, bitmap)
        }
        compose.onNodeWithText("Paris").performClick()
        compose.onNodeWithTag("locations-screen").assertDoesNotExist()
        compose.onNodeWithText("Paris").assertIsDisplayed()
    }

    @Test fun selectedCitySearchAndSettingsSurviveResizeAndSavedStateRestoration() {
        val restoration = screen()
        compose.onNodeWithTag("navbar-page-1").performClick()
        compose.onNodeWithText("Paris").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("a")
        compose.runOnIdle { size.value = 360f to 840f }
        restoration.emulateSavedInstanceStateRestore()
        awaitWeather()
        compose.onNode(hasSetTextAction()).assertTextEquals("a")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Paris").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        compose.runOnIdle { size.value = 1280f to 800f }
        restoration.emulateSavedInstanceStateRestore()
        awaitWeather()
        compose.onNodeWithText("System default").assertIsDisplayed()
        compose.onNodeWithText("Light", useUnmergedTree = true).assertIsDisplayed()
        capture("settings-landscape")
    }

    @Test fun separatingBookAndTabletopHingesKeepCardsAndActionsOnUsablePanes() {
        compose.runOnIdle { size.value = 800f to 720f }
        screen()
        val window = compose.onNodeWithTag("adaptive-capture").fetchSemanticsNode().boundsInWindow
        val fold = TestFoldingFeature(windowBounds = Rect(window.left.roundToInt(), window.top.roundToInt(),
            window.right.roundToInt(), window.bottom.roundToInt()), center = window.center.x.roundToInt(),
            size = 20, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.VERTICAL)
        folding.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        compose.waitForIdle()
        val hourly = compose.onNodeWithTag("weather-hourly-card").fetchSemanticsNode().boundsInWindow
        assertTrue(hourly.right <= fold.bounds.left)
        val daily = compose.onNodeWithTag("weather-daily-card").fetchSemanticsNode().boundsInWindow
        assertTrue(daily.left >= fold.bounds.right)
        assertEquals(hourly.top, daily.top, 1f)
        val current = compose.onNodeWithTag("current-temperature").fetchSemanticsNode().boundsInWindow
        assertTrue(current.right <= fold.bounds.left)
        val details = compose.onNodeWithTag("weather-current-details").fetchSemanticsNode().boundsInWindow
        assertTrue(details.right <= fold.bounds.left)
        assertTrue(details.top >= current.bottom - 1f)
        assertEquals(current.left, details.left, 1f)
        val nav = compose.onNodeWithTag("weather-bottom-navigation").fetchSemanticsNode().boundsInWindow
        assertEquals(window.width, nav.width, 1f)
        val indicators = compose.onNodeWithTag("navbar-page-0").fetchSemanticsNode().boundsInWindow
        assertTrue(indicators.right <= fold.bounds.left || indicators.left >= fold.bounds.right)
        capture("book-posture")
        compose.onAllNodesWithText("Next 48 hours").onLast().performClick()
        compose.onNodeWithTag("hourly-detail-surface").assertIsDisplayed()
        val sheet = compose.onNodeWithTag("hourly-detail-surface").fetchSemanticsNode().boundsInWindow
        val sheetBitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        saveBitmap("book-sheet", sheetBitmap)
        sheetBitmap.recycle()
        assertTrue("The hourly sheet $sheet must stay clear of the book hinge ${fold.bounds}",
            sheet.right <= fold.bounds.left || sheet.left >= fold.bounds.right)
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("hourly-detail-surface").assertDoesNotExist()
        // A horizontal separating fold must leave the scroll surface and navigation below it.
        val table = TestFoldingFeature(windowBounds = Rect(window.left.roundToInt(), window.top.roundToInt(),
            window.right.roundToInt(), window.bottom.roundToInt()), center = (window.top + window.height * .4f).roundToInt(),
            size = 20, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.HORIZONTAL)
        folding.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(table)))
        compose.waitForIdle()
        val grid = compose.onNodeWithTag("weather-grid-2").fetchSemanticsNode().boundsInWindow
        assertTrue(grid.top >= table.bounds.bottom)
        val bottomNav = compose.onNodeWithTag("weather-bottom-navigation").fetchSemanticsNode().boundsInWindow
        assertTrue(bottomNav.top >= table.bounds.bottom)
        capture("tabletop-posture")
    }

    @Test fun cityScrollPositionAndOpenHourlyDetailsSurviveSavedStateRestoration() {
        compose.runOnIdle { size.value = 800f to 600f }
        val restoration = screen()
        compose.onNodeWithTag("navbar-page-1").performClick()
        compose.onNodeWithTag("weather-grid-2").performScrollToIndex(2)
        compose.onNodeWithTag("current-temperature").assertIsNotDisplayed()
        compose.runOnIdle { size.value = 360f to 840f }
        restoration.emulateSavedInstanceStateRestore()
        awaitWeather()
        compose.onNodeWithText("Paris").assertIsDisplayed()
        compose.onNodeWithTag("current-temperature").assertIsNotDisplayed()
        compose.onNodeWithTag("weather-grid-1").performScrollToIndex(0)
        compose.onAllNodesWithText("Next 48 hours").onLast().performClick()
        compose.onNodeWithTag("hourly-detail-surface").assertIsDisplayed()
        compose.runOnIdle { size.value = 800f to 720f }
        compose.onNodeWithTag("hourly-detail-surface").assertIsDisplayed()
        compose.runOnIdle { size.value = 360f to 840f }
        compose.onNodeWithTag("hourly-detail-surface").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        awaitWeather()
        compose.onNodeWithTag("hourly-detail-surface").assertIsDisplayed()
    }

    private fun assertInside(tag: String) {
        val window = compose.onNodeWithTag("adaptive-capture").fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag must fit inside the window", bounds.left >= window.left - 1 &&
            bounds.right <= window.right + 1 && bounds.top >= window.top - 1 && bounds.bottom <= window.bottom + 1)
        if (tag == "weather-bottom-navigation") assertEquals(window.width, bounds.width, 1f)
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("adaptive-capture").captureToImage().asAndroidBitmap()
        saveBitmap(name, bitmap)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val directory = compose.activity.getExternalFilesDir(null)
        val destination = File(directory, "adaptive-$name.png")
        val pending = File(directory, "adaptive-$name.pending")
        pending.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        check(pending.renameTo(destination)) { "Could not publish screenshot $name" }
    }
}
