package com.moon.weather.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class RainMotion(val intensity: Float) {
    val count = (14 + 66 * intensity).roundToInt()
    val slant = .10f + .46f * intensity
    val cycleSeconds = 5.2f - 2f * intensity
    val streakScale = .8f + .5f * intensity
}

/** Weather codes distinguish light, moderate and heavy precipitation, including showers. */
internal fun rainMotion(code: Int): RainMotion? = when (code) {
    51 -> .10f; 53 -> .20f; 55 -> .35f
    56 -> .20f; 57 -> .40f
    61, 66, 80 -> .30f
    63, 81 -> .65f
    65, 67, 82, 99 -> 1f
    95, 96 -> .75f
    else -> null
}?.let(::RainMotion)

/** Horizontal velocity and streak direction use exactly the same slope as vertical travel. */
internal fun rainPosition(
    seedX: Float, seedY: Float, speed: Float, seconds: Float,
    width: Float, height: Float, motion: RainMotion
): Offset {
    val travel = seconds * speed / motion.cycleSeconds
    val xPhase = seedX - travel * 1.12f * height / width * motion.slant
    val yPhase = seedY + travel
    return Offset((xPhase - floor(xPhase)) * width,
        ((yPhase - floor(yPhase)) * 1.12f - .06f) * height)
}
