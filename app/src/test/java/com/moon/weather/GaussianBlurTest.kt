package com.moon.weather

import com.moon.weather.ui.gaussianBlurPixels
import org.junit.Assert.*
import org.junit.Test

class GaussianBlurTest {
    @Test fun uniformColoursArePreservedIncludingAtEdges() {
        val source = IntArray(9 * 7) { 0xff97612a.toInt() }
        assertArrayEquals(source, gaussianBlurPixels(source,9,7,2f))
    }
    @Test fun impulseHasGaussianFalloffAndSymmetryRatherThanAFlatBox() {
        val size = 17
        val source = IntArray(size * size) { 0xff000000.toInt() }
        source[8 * size + 8] = 0xffffffff.toInt()
        val output = gaussianBlurPixels(source,size,size,1.5f)
        fun intensity(x: Int, y: Int) = output[y * size + x] and 255
        assertTrue(intensity(8,8) > intensity(9,8))
        assertTrue(intensity(9,8) > intensity(10,8))
        assertTrue(intensity(10,8) > intensity(11,8))
        for (distance in 0..6) {
            assertEquals(intensity(8+distance,8),intensity(8-distance,8))
            assertEquals(intensity(8+distance,8),intensity(8,8+distance))
        }
    }
    @Test fun transparentPixelsDoNotAddColouredHalos() {
        val source = IntArray(9) { 0x00ff0000 }
        source[4] = 0xffffffff.toInt()
        val output = gaussianBlurPixels(source,9,1,1f)
        assertTrue((output[3] ushr 24) in 1..254)
        assertEquals(0x00ffffff,output[3] and 0x00ffffff)
    }
    @Test fun verySmallImagesHandleKernelsLargerThanTheirDimensions() {
        assertArrayEquals(intArrayOf(0xff314159.toInt()), gaussianBlurPixels(intArrayOf(0xff314159.toInt()),1,1,7.2f))
    }
}
