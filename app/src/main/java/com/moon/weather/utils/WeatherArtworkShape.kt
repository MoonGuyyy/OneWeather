package com.moon.weather.utils

enum class WeatherArtworkShape { PHONE, LANDSCAPE, PORTRAIT }

/** Tablets and inner fold displays use the two large compositions. Typical phone
 * windows retain the original artwork; the 10:16 Fold cover uses the portrait one. */
internal fun weatherArtworkShape(width: Int, height: Int, largeDisplay: Boolean): WeatherArtworkShape {
    if (width <= 0 || height <= 0) return WeatherArtworkShape.PHONE
    val portraitAspect = minOf(width, height).toFloat() / maxOf(width, height)
    if (!largeDisplay && portraitAspect < .58f) return WeatherArtworkShape.PHONE
    return if (width >= height) WeatherArtworkShape.LANDSCAPE else WeatherArtworkShape.PORTRAIT
}
