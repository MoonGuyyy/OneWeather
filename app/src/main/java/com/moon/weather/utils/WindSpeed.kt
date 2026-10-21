package com.moon.weather.utils

import kotlin.math.roundToInt

/** API readings remain in km/h, so weather thresholds never depend on the display unit. */
object WindSpeed {
    fun value(kilometersPerHour: Double, useMph: Boolean): Int =
        (if (useMph) kilometersPerHour / 1.609344 else kilometersPerHour).roundToInt()

    fun unit(useMph: Boolean): String = if (useMph) "mph" else "km/h"
}
