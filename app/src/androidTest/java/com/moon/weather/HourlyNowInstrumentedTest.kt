package com.moon.weather

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.HourlyWeather
import com.moon.weather.ui.LocalWeatherArtwork
import com.moon.weather.ui.LocalWeatherGlassTint
import com.moon.weather.ui.WeatherArtworkLoader
import com.moon.weather.ui.components.HourlyForecastCard
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.ui.weatherTileTint
import com.moon.weather.utils.WeatherCodeMapper
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class HourlyNowInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nowTracksCurrentConditionsAndSnowGlassWhileLaterHoursRemainForecasts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val scenes = listOf(3,95,71).associateWith { code ->
            runBlocking { WeatherArtworkLoader.load(context.resources,WeatherCodeMapper.getBackgroundRes(code,true),
                IntSize(metrics.widthPixels,metrics.heightPixels))!! }
        }
        val current = mutableStateOf(CurrentWeather("2026-10-04T18:15",17.0,3,1,60.0,8.0,110.0,
            1013.0,0.0,10000.0,16.0,10.0))
        val hourly = HourlyWeather((18..21).map { "2026-10-04T$it:00" },
            List(4) { 15.0 },List(4) { 80 },List(4) { 95 },List(4) { 85.0 },List(4) { 20.0 },
            List(4) { 110.0 },List(4) { 1013.0 },List(4) { 0.0 },List(4) { 10000.0 },
            List(4) { 13.0 },List(4) { 10.0 })
        compose.setContent {
            WeatherTheme(darkTheme = false,dynamicColor = false) {
                val snapshot = current.value
                val artwork = scenes.getValue(snapshot.weatherCode)
                CompositionLocalProvider(LocalWeatherArtwork provides artwork,
                    LocalWeatherGlassTint provides weatherTileTint(snapshot.isDay == 1,snapshot.weatherCode)) {
                    Box(Modifier.fillMaxSize()) {
                        Image(artwork.background,null,Modifier.fillMaxSize(),contentScale = ContentScale.Crop)
                        HourlyForecastCard(hourly,snapshot.time,modifier = Modifier.align(Alignment.Center).padding(12.dp),
                            currentWeather = snapshot)
                    }
                }
            }
        }
        fun icon(index: Int, description: String) = compose.onNode(hasContentDescription(description) and
            hasAnyAncestor(hasTestTag("hourly-item-$index")),useUnmergedTree = true)
        icon(0,"Cloudy").assertIsDisplayed()
        icon(1,"Thunderstorm").assertIsDisplayed()
        compose.onNodeWithText("17°").assertIsDisplayed()
        export("hourly-now-cloudy")
        compose.runOnIdle { current.value = current.value.copy(weatherCode = 95) }
        icon(0,"Thunderstorm").assertIsDisplayed()
        compose.runOnIdle { current.value = current.value.copy(weatherCode = 71,temperature = -1.0) }
        icon(0,"Snowy").assertIsDisplayed()
        icon(1,"Thunderstorm").assertIsDisplayed()
        compose.onNodeWithText("-1°").assertIsDisplayed()
        export("hourly-now-snow")
        compose.runOnIdle { current.value = current.value.copy(weatherCode = 3,temperature = 17.0) }
        compose.onNodeWithText("Next 48 hours").performClick()
        compose.onNode(hasContentDescription("Cloudy") and hasAnyAncestor(hasTestTag("hourly-detail-list")))
            .assertIsDisplayed()
        compose.onNode(hasText("17°") and hasAnyAncestor(hasTestTag("hourly-detail-list"))).assertIsDisplayed()
    }

    private fun export(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
