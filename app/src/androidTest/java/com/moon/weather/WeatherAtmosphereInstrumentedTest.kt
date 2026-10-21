package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.IntSize
import com.moon.weather.ui.WeatherArtworkLoader
import com.moon.weather.ui.WeatherBackgroundScene
import kotlinx.coroutines.runBlocking
import com.moon.weather.ui.WeatherAtmosphere
import com.moon.weather.ui.LightningStorm
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class WeatherAtmosphereInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weatherFamiliesAnimateAndLightningIsTheStrongestEffect() {
        val code = mutableStateOf(0)
        val day = mutableStateOf(true)
        val time = mutableStateOf(0f)
        val lightningPeak = LightningStorm(7301).nextStrike.startSeconds + .035f
        compose.setContent {
            Box(Modifier.size(300.dp,500.dp).background(Color(0xFF102638))) {
                WeatherAtmosphere(code.value,day.value,Modifier.matchParentSize(),timeOverrideSeconds = time.value,
                    lightningSeed = 7301)
            }
        }
        val cases = listOf(0 to true,0 to false,1 to true,2 to true,3 to true,45 to true,51 to true,
            61 to true,66 to true,80 to true,71 to true,77 to true,85 to true,95 to true,99 to true)
        for ((weather,isDay) in cases) {
            compose.runOnIdle { code.value = weather; day.value = isDay; time.value = 0f }
            val before = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
            compose.runOnIdle { time.value = if (weather >= 95) lightningPeak else 8f }
            val after = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
            var changed = 0
            var largestChange = 0
            var totalChange = 0L
            var channels = 0L
            for (y in 0 until before.height step 2) for (x in 0 until before.width step 2) {
                val first = before.getPixel(x,y)
                val second = after.getPixel(x,y)
                if (first != second) changed++
                for (shift in listOf(0,8,16)) {
                    val difference = abs((first shr shift and 255)-(second shr shift and 255))
                    largestChange = maxOf(largestChange,difference)
                    totalChange += difference; channels++
                }
            }
            assertTrue("Weather $weather/$isDay must animate",changed > 15)
            if (weather >= 95) {
                assertTrue("Lightning must have a bright, unmistakable core",largestChange > 170)
                assertTrue("Lightning must illuminate the sky",totalChange.toDouble()/channels > 15)
            } else {
                assertTrue("Weather $weather must avoid harsh local flashes, change was $largestChange",largestChange < 130)
                assertTrue("Weather $weather must keep the underlying artwork clear",totalChange.toDouble()/channels < 12)
            }
            if (weather in listOf(0,3,61,71,95)) export(after,"atmosphere-$weather-$isDay")
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun effectsStayVisibleOverTheRealBackgroundArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clouds = runBlocking { WeatherArtworkLoader.load(context.resources,R.drawable.cloudyday,IntSize(450,900))!! }
        val rain = runBlocking { WeatherArtworkLoader.load(context.resources,R.drawable.rainyday,IntSize(450,900))!! }
        val sunny = runBlocking { WeatherArtworkLoader.load(context.resources,R.drawable.sunnyday,IntSize(450,900))!! }
        val thunder = runBlocking { WeatherArtworkLoader.load(context.resources,R.drawable.thunder_day,IntSize(450,900))!! }
        val thunderNight = runBlocking { WeatherArtworkLoader.load(context.resources,R.drawable.thunder_night,IntSize(450,900))!! }
        val artwork = mutableStateOf(clouds)
        val code = mutableStateOf(3)
        val time = mutableStateOf(0f)
        val day = mutableStateOf(true)
        val lightningPeak = LightningStorm(7301).nextStrike.startSeconds + .035f
        compose.setContent {
            val pager = rememberPagerState { 1 }
            WeatherBackgroundScene(artwork.value,null,pager,code.value,day.value,code.value,day.value,false,
                Modifier.size(240.dp,480.dp),timeOverrideSeconds = time.value,lightningSeed = 7301)
        }
        for ((weather,scene,isDay) in listOf(Triple(3,clouds,true),Triple(61,rain,true),Triple(65,rain,true),
                Triple(0,sunny,true),Triple(95,thunder,true),Triple(95,thunderNight,false))) {
            compose.runOnIdle { artwork.value = scene; code.value = weather; day.value = isDay; time.value = 0f }
            val before = compose.onNodeWithTag("weather-background-scene").captureToImage().asAndroidBitmap()
            compose.runOnIdle { time.value = if (weather >= 95) lightningPeak else 12f }
            val after = compose.onNodeWithTag("weather-background-scene").captureToImage().asAndroidBitmap()
            var changed = 0
            for (y in 0 until after.height step 2) for (x in 0 until after.width step 2) {
                if (before.getPixel(x,y) != after.getPixel(x,y)) changed++
            }
            assertTrue("Motion must appear in front of the actual artwork for weather $weather",changed > 100)
            export(after,"atmosphere-real-artwork-$weather${if (weather >= 95) "-$isDay" else ""}")
        }
    }

    @Test fun liveClockAnimatesAndStopsWhenHidden() {
        val enabled = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(240.dp,400.dp).background(Color(0xFF102638))) {
                WeatherAtmosphere(61,true,Modifier.matchParentSize(),enabled = enabled.value)
            }
        }
        compose.mainClock.advanceTimeBy(160)
        val before = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(2400)
        val moving = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
        assertFalse("The real frame clock must move the rain",before.sameAs(moving))
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeBy(32)
        val paused = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(2400)
        val later = compose.onNodeWithTag("weather-atmosphere").captureToImage().asAndroidBitmap()
        assertTrue("Hidden effects must stop advancing",paused.sameAs(later))
    }

    @Test fun atmosphereDoesNotInterceptWeatherControls() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.size(200.dp)) {
                WeatherAtmosphere(61,true,Modifier.matchParentSize(),timeOverrideSeconds = 2f)
                Text("Weather control",Modifier.clickable { clicks++ })
            }
        }
        compose.onNodeWithText("Weather control").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1,clicks) }
    }

    private fun export(bitmap: Bitmap,name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
