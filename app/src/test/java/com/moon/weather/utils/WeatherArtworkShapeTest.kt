package com.moon.weather.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherArtworkShapeTest {
    @Test fun normalPhonesKeepTheirOriginalArtworkInBothOrientations() {
        assertEquals(WeatherArtworkShape.PHONE, weatherArtworkShape(1080, 2400, false))
        assertEquals(WeatherArtworkShape.PHONE, weatherArtworkShape(2400, 1080, false))
        assertEquals(WeatherArtworkShape.PHONE, weatherArtworkShape(0, 0, true))
    }
    @Test fun tabletsAndFoldDisplaysChooseTheCompositionForTheirCurrentWindow() {
        for ((width, height) in listOf(2560 to 1600, 2504 to 2256, 800 to 600)) {
            assertEquals(WeatherArtworkShape.LANDSCAPE, weatherArtworkShape(width, height, true))
            assertEquals(WeatherArtworkShape.PORTRAIT, weatherArtworkShape(height, width, true))
        }
        assertEquals(WeatherArtworkShape.PORTRAIT, weatherArtworkShape(400, 640, false))
        assertEquals(WeatherArtworkShape.PHONE, weatherArtworkShape(1080, 2520, false))
        assertEquals(WeatherArtworkShape.PORTRAIT, weatherArtworkShape(480, 800, true))
    }
}
