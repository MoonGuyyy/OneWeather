package com.moon.weather

import com.moon.weather.ui.LightningStorm
import org.junit.Assert.*
import org.junit.Test

class WeatherLightningTest {
    @Test fun everyStrikeHasANewRandomIntervalBetweenFifteenAndTwentyFiveSeconds() {
        for (seed in listOf(0,7301,91234)) {
            val storm = LightningStorm(seed)
            var previous = 0f
            val intervals = mutableSetOf<Int>()
            val shapes = mutableSetOf<Any>()
            repeat(100) { index ->
                val upcoming = storm.nextStrike
                val interval = upcoming.startSeconds - previous
                assertTrue("Strike gap was $interval",interval in 15f..25f)
                assertNull(storm.strikeAt(upcoming.startSeconds - 1f))
                assertEquals(upcoming,storm.strikeAt(upcoming.startSeconds + .035f))
                assertEquals(index,upcoming.sequence)
                intervals += (interval * 100).toInt()
                shapes.add(upcoming.trunk)
                previous = upcoming.startSeconds
            }
            assertTrue("Strike timing must vary",intervals.size > 70)
            assertEquals("Every strike needs its own shape",100,shapes.size)
        }
    }

    @Test fun lightningHasAnInstantBrightPeakAndAFastNaturalDecay() {
        val storm = LightningStorm(7301)
        repeat(30) {
            val strike = storm.nextStrike
            assertEquals(0f,strike.brightnessAt(strike.startSeconds - .1f),0f)
            assertTrue(strike.brightnessAt(strike.startSeconds + .035f) > .99f)
            assertTrue(strike.brightnessAt(strike.startSeconds + .55f) < .08f)
            assertEquals(0f,strike.brightnessAt(strike.startSeconds + 1f),0f)
            strike.returnDelay?.let { delay ->
                assertTrue(strike.brightnessAt(strike.startSeconds + delay + .02f) >= .64f)
            }
            storm.strikeAt(strike.startSeconds + 1f)
        }
    }

    @Test fun channelsBranchFromTheMainStrikeAndReachTheHorizon() {
        val storm = LightningStorm(1234)
        repeat(20) {
            val strike = storm.nextStrike
            assertTrue("A natural channel has many irregular segments",strike.trunk.size > 32)
            assertTrue(strike.trunk.first().y < .06f)
            assertTrue(strike.trunk.last().y in .395f.. .45f)
            assertTrue(strike.branches.size >= 4)
            val network = strike.trunk + strike.branches.flatMap { it.points.drop(1) }
            for (branch in strike.branches) {
                assertTrue("Every fork must connect to another channel",branch.points.first() in network)
                assertTrue(branch.width < 1f)
                assertTrue(branch.points.size >= 9)
            }
            storm.strikeAt(strike.startSeconds + 1f)
        }
        assertNotEquals(LightningStorm(1234).nextStrike.trunk,LightningStorm(9876).nextStrike.trunk)
    }

    @Test fun samplingBackwardsReplaysTheSeededSceneWithoutChangingItsShape() {
        val storm = LightningStorm(7301)
        val first = storm.nextStrike
        val time = first.startSeconds + .035f
        val original = storm.strikeAt(time)
        storm.strikeAt(200f)
        storm.strikeAt(0f)
        assertEquals(original,storm.strikeAt(time))
    }
}
