package com.moon.weather.ui

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/** Separable Gaussian convolution, with clamped edges and premultiplied colour to avoid halos. */
internal fun gaussianBlurPixels(pixels: IntArray, width: Int, height: Int, sigma: Float): IntArray {
    require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
    require(sigma.isFinite() && sigma > 0f)
    val radius = ceil(3f * sigma).toInt()
    val weights = FloatArray(2 * radius + 1) { index ->
        val distance = (index - radius).toDouble()
        exp(-distance * distance / (2.0 * sigma * sigma)).toFloat()
    }
    val total = weights.sum()
    for (index in weights.indices) weights[index] /= total

    val horizontal = FloatArray(pixels.size * 4)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var alpha = 0f
            var red = 0f
            var green = 0f
            var blue = 0f
            for (index in weights.indices) {
                val colour = pixels[row + (x + index - radius).coerceIn(0, width - 1)]
                val weightedAlpha = (colour ushr 24) / 255f * weights[index]
                alpha += weightedAlpha
                red += (colour ushr 16 and 255) * weightedAlpha
                green += (colour ushr 8 and 255) * weightedAlpha
                blue += (colour and 255) * weightedAlpha
            }
            val offset = (row + x) * 4
            horizontal[offset] = alpha
            horizontal[offset + 1] = red
            horizontal[offset + 2] = green
            horizontal[offset + 3] = blue
        }
    }
    val output = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var alpha = 0f
            var red = 0f
            var green = 0f
            var blue = 0f
            for (index in weights.indices) {
                val offset = ((y + index - radius).coerceIn(0, height - 1) * width + x) * 4
                val weight = weights[index]
                alpha += horizontal[offset] * weight
                red += horizontal[offset + 1] * weight
                green += horizontal[offset + 2] * weight
                blue += horizontal[offset + 3] * weight
            }
            val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
            if (a > 0) {
                output[y * width + x] = (a shl 24) or
                    ((red / alpha).roundToInt().coerceIn(0, 255) shl 16) or
                    ((green / alpha).roundToInt().coerceIn(0, 255) shl 8) or
                    (blue / alpha).roundToInt().coerceIn(0, 255)
            }
        }
    }
    return output
}
