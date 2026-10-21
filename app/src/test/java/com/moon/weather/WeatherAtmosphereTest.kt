package com.moon.weather

import com.moon.weather.ui.rainMotion
import com.moon.weather.ui.rainPosition
import com.moon.weather.ui.weatherNavigationColor
import com.moon.weather.ui.weatherNavigationContentColor
import com.moon.weather.ui.weatherTileTint
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class WeatherAtmosphereTest {
    @Test fun rainTravelFollowsTheStreakAngleAtEveryIntensityAndScreenShape() {
        for (code in listOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99)) {
            val motion = rainMotion(code)!!
            for ((width,height) in listOf(360f to 800f,800f to 360f)) {
                val start = rainPosition(.8f,.2f,1f,0f,width,height,motion)
                val end = rainPosition(.8f,.2f,1f,.2f,width,height,motion)
                assertTrue("Rain must travel down and left",end.y > start.y && end.x < start.x)
                assertEquals("Motion must follow its line, rather than fall vertically",-motion.slant,
                    (end.x-start.x)/(end.y-start.y),.0001f)
            }
        }
    }

    @Test fun heavierPrecipitationHasMoreSteeperAndFasterDrops() {
        for (family in listOf(listOf(51,53,55),listOf(61,63,65),listOf(80,81,82),listOf(56,57),listOf(66,67),listOf(96,99))) {
            for ((lightCode,heavyCode) in family.zipWithNext()) {
                val light = rainMotion(lightCode)!!
                val heavy = rainMotion(heavyCode)!!
                assertTrue(heavy.count > light.count)
                assertTrue(heavy.slant > light.slant)
                assertTrue(heavy.cycleSeconds < light.cycleSeconds)
                assertTrue(heavy.streakScale > light.streakScale)
            }
        }
        for (code in listOf(0,1,2,3,45,48,71,73,75,77,85,86)) assertNull(rainMotion(code))
    }

    @Test fun navigationKeepsExistingColorsExceptDaytimeWetAndSnowyWeather() {
        val codes = listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99)
        val snow = listOf(71,73,75,77,85,86)
        for (code in codes) {
            assertEquals(Color(0xFF041824),weatherNavigationColor(code,false))
            assertEquals(Color.White,weatherNavigationContentColor(code,false))
            when {
                code in snow -> {
                    assertEquals(Color(0xFFE3F3FA),weatherNavigationColor(code,true))
                    assertEquals(Color(0xFF244658),weatherNavigationContentColor(code,true))
                }
                rainMotion(code) != null -> {
                    assertEquals(Color(0xFF30360F),weatherNavigationColor(code,true))
                    assertEquals(Color.White,weatherNavigationContentColor(code,true))
                }
                else -> {
                    assertEquals(Color(0xFF4B5111),weatherNavigationColor(code,true))
                    assertEquals(Color.White,weatherNavigationContentColor(code,true))
                }
            }
        }
    }

    @Test fun daytimeSnowUsesExactlyTheSameGlassTintAsNight() {
        for (code in listOf(71,73,75,77,85,86))
            assertEquals(weatherTileTint(false),weatherTileTint(true,code))
        for (code in listOf(0,1,2,3,45,48,51,55,61,63,65,66,67,80,82,95,96,99))
            assertEquals(weatherTileTint(true),weatherTileTint(true,code))
    }

}
