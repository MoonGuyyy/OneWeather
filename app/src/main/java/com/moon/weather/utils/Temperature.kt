package com.moon.weather.utils

import kotlin.math.roundToInt

/** Convert the original reading before rounding, consistently in the app and widgets. */
object Temperature {
    fun value(celsius: Double, useCelsius: Boolean): Int =
        (if (useCelsius) celsius else celsius * 9.0 / 5.0 + 32.0).roundToInt()
}
