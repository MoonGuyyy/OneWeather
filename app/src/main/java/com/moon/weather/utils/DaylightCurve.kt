package com.moon.weather.utils

import kotlin.math.PI
import kotlin.math.cos

/** Shared sunrise-to-sunset arch and palette for Compose and widget bitmap artwork. */
internal object DaylightCurve {
    val colors = intArrayOf(0xff664397.toInt(), 0xffff6659.toInt(), 0xffffdc65.toInt(),
        0xffff6659.toInt(), 0xff664397.toInt())

    fun heightAt(progress: Float): Float =
        (1f - cos(2f * PI.toFloat() * progress.coerceIn(0f, 1f))) / 2f
}
