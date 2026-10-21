package com.moon.weather.ui

import org.junit.Assert.*
import org.junit.Test

class WeatherAdaptiveLayoutTest {
    @Test fun tabletsAndUnfoldedScreensUseTwoColumnsInBothOrientations() {
        // Representative logical windows; physical pixel resolution does not determine layout.
        for ((width, height) in listOf(800f to 1280f, 1280f to 800f,
            800f to 600f, 600f to 800f, 800f to 720f, 720f to 800f)) {
            val layout = weatherLayout(width, height)
            assertEquals(if (width >= 640f) 2 else 1, layout.columns)
            assertTrue(layout.left >= 0f)
            assertTrue(layout.left + layout.width <= width)
        }
    }

    @Test fun coverScreensAndSplitScreenUseOneReadableColumn() {
        for ((width, height) in listOf(360f to 840f, 400f to 640f, 480f to 800f))
            assertEquals(1, weatherLayout(width, height).columns)
        assertEquals(1, weatherLayout(720f, 800f, fontScale = 1.5f).columns)
    }

    @Test fun wideWindowsCapCardsAndPanelsAtReadableWidths() {
        assertEquals(1200f, weatherLayout(1800f, 1000f).width)
        assertEquals(640f, weatherLayout(1800f, 1000f, singlePane = true).width)
        assertEquals(580f, weatherLayout(1800f, 1000f, singlePane = true).left)
    }

    @Test fun cityMenuFillsFlatWindowsAndKeepsSeparatingHingesClear() {
        for ((width, height) in listOf(800f to 800f, 800f to 720f, 1280f to 800f, 800f to 1280f)) {
            val menu = weatherLayout(width, height, singlePane = true, fillAvailableWidth = true)
            assertEquals(0f, menu.left)
            assertEquals(width, menu.width)
            assertEquals(height, menu.height)
        }
        val fold = WeatherFold(380f, 0f, 400f, 800f, true)
        val menu = weatherLayout(1200f, 800f, fold = fold, singlePane = true, fillAvailableWidth = true)
        assertEquals(408f, menu.left)
        assertEquals(792f, menu.width)
    }

    @Test fun bookPostureAlignsColumnEdgesOutsideAnOffCenterHinge() {
        val fold = WeatherFold(380f, 0f, 400f, 800f, true)
        val layout = weatherLayout(900f, 800f, fold = fold)
        assertEquals(2, layout.columns)
        val lane = (layout.width - 40f - layout.gap) / 2
        val leftEdge = layout.left + 20f + lane
        val rightEdge = leftEdge + layout.gap
        assertTrue(leftEdge <= fold.left - 8f)
        assertTrue(rightEdge >= fold.right + 8f)
        assertTrue(layout.left + layout.width <= 900f)
        val panel = weatherLayout(900f, 800f, fold = fold, singlePane = true)
        assertTrue(panel.left >= fold.right + 8f)
    }

    @Test fun narrowBookPanesAndLargeTextFallBackWithoutCrossingHinge() {
        val fold = WeatherFold(350f, 0f, 350f, 800f, true)
        val layout = weatherLayout(700f, 800f, 1.5f, fold)
        assertEquals(1, layout.columns)
        assertTrue(layout.left + layout.width <= fold.left - 8f)
    }

    @Test fun tabletopUsesTheLargerHalfWithControlsClearOfTheFold() {
        val fold = WeatherFold(0f, 280f, 800f, 300f, false)
        val layout = weatherLayout(800f, 720f, fold = fold)
        assertEquals(308f, layout.top)
        assertEquals(412f, layout.height)
        val upper = weatherLayout(800f, 500f, fold = fold)
        assertEquals(0f, upper.top)
        assertEquals(272f, upper.height)
    }

    @Test fun hingeOutsideAMultiWindowViewportDoesNotChangeTheLayout() {
        val fold = WeatherFold(800f, 0f, 820f, 1000f, true)
        assertEquals(weatherLayout(400f, 800f), weatherLayout(400f, 800f, fold = fold))
    }
}
