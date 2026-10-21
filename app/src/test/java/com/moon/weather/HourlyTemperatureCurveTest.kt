package com.moon.weather

import com.moon.weather.ui.components.TemperatureCurveSegment
import com.moon.weather.ui.components.hourlyTemperatureCurve
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HourlyTemperatureCurveTest {
    private fun value(curve: TemperatureCurveSegment, t: Float): Float {
        val remaining = 1f - t
        return remaining * remaining * remaining * curve.startY +
            3f * remaining * remaining * t * curve.control1Y +
            3f * remaining * t * t * curve.control2Y + t * t * t * curve.endY
    }

    @Test fun emptyAndSingleReadingsHaveNoSegments() {
        assertTrue(hourlyTemperatureCurve(emptyList()).isEmpty())
        assertTrue(hourlyTemperatureCurve(listOf(8f)).isEmpty())
    }

    @Test fun twoReadingsAndSteadyTrendsAreStraightSlopesIncludingTheirEnds() {
        for (readings in listOf(listOf(0f, 30f), listOf(0f, 5f, 10f, 15f), listOf(15f, 10f, 5f, 0f))) {
            for (segment in hourlyTemperatureCurve(readings)) for (step in 0..100) {
                val t = step / 100f
                assertEquals(segment.startY + (segment.endY - segment.startY) * t, value(segment, t), .0001f)
            }
        }
    }

    @Test fun continuingRiseOrFallHasContinuousNonzeroSlopesThroughHourlyPoints() {
        for (readings in listOf(listOf(-5f, -2f, 3f, 11f, 15f), listOf(15f, 11f, 3f, -2f, -5f))) {
            val curves = hourlyTemperatureCurve(readings)
            for (index in 0 until curves.lastIndex) {
                val before = curves[index]
                val after = curves[index + 1]
                val incoming = 3f * (before.endY - before.control2Y)
                val outgoing = 3f * (after.control1Y - after.startY)
                assertEquals("A smooth slope must continue through the point", incoming, outgoing, .0001f)
                assertTrue("A continuing trend must not flatten into a stair", abs(incoming) > .1f)
            }
        }
    }

    @Test fun acceleratingTemperaturesFlowAsOneCurveRatherThanTwoStraightSegments() {
        for (readings in listOf(listOf(21f, 22f, 24f), listOf(24f, 22f, 21f))) {
            val curves = hourlyTemperatureCurve(readings)
            // This quadratic trend should curve throughout both hours, with no
            // sudden change in curvature or slope at the middle reading.
            for ((index, curve) in curves.withIndex()) {
                val straightMidpoint = (readings[index] + readings[index + 1]) / 2f
                assertEquals(straightMidpoint - .125f, value(curve, .5f), .0001f)
            }
            val before = curves[0]
            val after = curves[1]
            val incomingCurvature = 6f * (before.endY - 2f * before.control2Y + before.control1Y)
            val outgoingCurvature = 6f * (after.startY - 2f * after.control1Y + after.control2Y)
            assertEquals(1f, incomingCurvature, .0001f)
            assertEquals(incomingCurvature, outgoingCurvature, .0001f)
        }
    }

    @Test fun peaksAndDipsLevelOffBeforeReversingDirection() {
        val readings = listOf(2f, 9f, 4f, -3f, 6f)
        val curves = hourlyTemperatureCurve(readings)
        for (index in listOf(1, 3)) {
            assertEquals(readings[index], curves[index - 1].control2Y, 0f)
            assertEquals(readings[index], curves[index].control1Y, 0f)
        }
    }

    @Test fun flatTemperaturesStayFlat() {
        val curves = hourlyTemperatureCurve(listOf(0f, 0f, 10f, 10f, -2f))
        for (index in listOf(0, 2)) for (step in 0..100) {
            assertEquals(curves[index].startY, value(curves[index], step / 100f), .0001f)
        }
    }

    @Test fun riseOrFallBetweenFlatStretchesEasesInAndOut() {
        for (readings in listOf(listOf(21f, 21f, 24f, 24f), listOf(24f, 24f, 21f, 21f))) {
            val curves = hourlyTemperatureCurve(readings)
            val transition = curves[1]
            assertEquals("Leave the first plateau level", transition.startY, transition.control1Y, 0f)
            assertEquals("Meet the next plateau level", transition.endY, transition.control2Y, 0f)
            val change = transition.endY - transition.startY
            val startProgress = (value(transition, .25f) - transition.startY) / change
            val endProgress = (value(transition, .75f) - transition.startY) / change
            assertTrue("Ease into the change rather than use a straight slope", startProgress < .2f)
            assertTrue("Ease out of the change before the plateau", endProgress > .8f)
            for (index in listOf(0, 2)) for (step in 0..100) {
                assertEquals("Surrounding stretches must remain flat",
                    readings[index], value(curves[index], step / 100f), .0001f)
            }
        }
    }

    @Test fun longerRiseBetweenPlateausOnlyLevelsOffAtThePlateaus() {
        val curves = hourlyTemperatureCurve(listOf(21f, 21f, 22f, 24f, 24f))
        assertEquals(curves[1].startY, curves[1].control1Y, 0f)
        assertEquals(curves[2].endY, curves[2].control2Y, 0f)
        val incoming = 3f * (curves[1].endY - curves[1].control2Y)
        val outgoing = 3f * (curves[2].control1Y - curves[2].startY)
        assertTrue("The rise should flow through the intermediate reading", incoming > 0f)
        assertEquals(incoming, outgoing, .0001f)
    }

    @Test fun sharpChangesNeverOvershootOrInventExtraTurningPoints() {
        val scenarios = listOf(
            listOf(0f, 1f, 30f, 31f, 80f),
            listOf(80f, 31f, 30f, 1f, 0f),
            listOf(-40f, 15f, -3f, 30f, 30f, -80f),
            listOf(0f, .001f, 100f, 100.001f, 200f)
        )
        for (readings in scenarios) for (curve in hourlyTemperatureCurve(readings)) {
            var previous = curve.startY
            for (step in 0..1000) {
                val sample = value(curve, step / 1000f)
                assertTrue(sample >= min(curve.startY, curve.endY) - .0001f)
                assertTrue(sample <= max(curve.startY, curve.endY) + .0001f)
                if (curve.endY > curve.startY) assertTrue(sample >= previous - .0001f)
                if (curve.endY < curve.startY) assertTrue(sample <= previous + .0001f)
                previous = sample
            }
        }
    }
}
