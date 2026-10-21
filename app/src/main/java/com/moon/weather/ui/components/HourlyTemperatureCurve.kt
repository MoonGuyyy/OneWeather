package com.moon.weather.ui.components

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

internal data class TemperatureCurveSegment(
    val startY: Float,
    val control1Y: Float,
    val control2Y: Float,
    val endY: Float
)

/** A flowing cubic spline, limited to the temperatures actually forecast. */
internal fun hourlyTemperatureCurve(values: List<Float>): List<TemperatureCurveSegment> {
    if (values.size < 2) return emptyList()
    val changes = values.zipWithNext { first, next -> next - first }
    val tangents = FloatArray(values.size)
    fun endpointSlope(edge: Float, next: Float): Float {
        val estimate = (3f * edge - next) / 2f
        if (edge == 0f || (estimate > 0f) != (edge > 0f)) return 0f
        return estimate.coerceIn(-3f * abs(edge), 3f * abs(edge))
    }
    tangents[0] = if (changes.size == 1) changes.first()
        else endpointSlope(changes[0], changes[1])
    tangents[tangents.lastIndex] = if (changes.size == 1) changes.last()
        else endpointSlope(changes.last(), changes[changes.lastIndex - 1])

    // Fix the slope at actual peaks, dips and plateaus. Within each uninterrupted
    // trend, solve the spline together so curvature flows through hourly joins.
    val anchors = listOf(0) + (1 until values.lastIndex).filter { index ->
        val before = changes[index - 1]
        val after = changes[index]
        before == 0f || after == 0f || (before > 0f) != (after > 0f)
    } + values.lastIndex
    for ((start, end) in anchors.zipWithNext()) {
        val count = end - start - 1
        if (count == 0) continue
        val diagonal = DoubleArray(count) { 4.0 }
        val slopes = DoubleArray(count) { offset ->
            val index = start + offset + 1
            3.0 * (changes[index - 1].toDouble() + changes[index].toDouble())
        }
        slopes[0] -= tangents[start]
        slopes[count - 1] -= tangents[end]
        for (offset in 1 until count) {
            val factor = 1.0 / diagonal[offset - 1]
            diagonal[offset] -= factor
            slopes[offset] -= factor * slopes[offset - 1]
        }
        for (offset in count - 1 downTo 0) {
            tangents[start + offset + 1] = ((slopes[offset] -
                if (offset == count - 1) 0.0 else tangents[start + offset + 2].toDouble()) /
                diagonal[offset]).toFloat()
        }
    }

    // Extreme changes can make an unconstrained spline overshoot. Limit shared
    // tangents, retaining a continuous slope on both sides of every reading.
    for (index in tangents.indices) {
        val before = changes[(index - 1).coerceAtLeast(0)]
        val after = changes[index.coerceAtMost(changes.lastIndex)]
        val bound = 3f * min(abs(before), abs(after))
        if ((tangents[index] > 0f) != (after > 0f)) tangents[index] = 0f
        tangents[index] = tangents[index].coerceIn(-bound, bound)
    }
    for (index in changes.indices) {
        val change = changes[index]
        if (change == 0f) continue
        val incoming = tangents[index] / change
        val outgoing = tangents[index + 1] / change
        val length = sqrt(incoming * incoming + outgoing * outgoing)
        if (length > 3f) {
            tangents[index] *= 3f / length
            tangents[index + 1] *= 3f / length
        }
    }
    return changes.indices.map { index ->
        TemperatureCurveSegment(
            startY = values[index],
            control1Y = values[index] + tangents[index] / 3f,
            control2Y = values[index + 1] - tangents[index + 1] / 3f,
            endY = values[index + 1]
        )
    }
}
