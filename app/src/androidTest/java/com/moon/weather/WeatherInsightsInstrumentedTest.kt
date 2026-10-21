package com.moon.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.moon.weather.data.DailyWeather
import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.components.InsightCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeatherInsightsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun weather(code: Int, temp: Double = 20.0, wind: Double = 10.0, uv: Double = 1.0) =
        WeatherResponse(CurrentWeather("2026-10-04T12:00",temp,code,1,50.0,wind,0.0,1013.0,uv,10000.0,temp,10.0),null,null)

    @Test fun swipingChangesContentButKeepsTheGlassCardInPlace() {
        compose.setContent {
            Box(Modifier.fillMaxWidth().background(Color.DarkGray)) {
                InsightCard(weather(95,34.0,35.0,8.0),true)
            }
        }
        compose.onNodeWithText("Thunder").assertIsDisplayed()
        val before = compose.onNodeWithTag("weather-insight-card").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("weather-insight-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Heat").assertIsDisplayed()
        val after = compose.onNodeWithTag("weather-insight-card").fetchSemanticsNode().boundsInRoot
        assertEquals(before,after)
    }
    @Test fun aSingleInsightCannotSwipeToAnotherMessage() {
        compose.setContent { InsightCard(WeatherResponse(null,null,DailyWeather(listOf("2026-10-04"),listOf(0),listOf(20.0),emptyList(),emptyList(),emptyList(),emptyList(),null)),true) }
        compose.onNodeWithTag("weather-insight-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Clear skies").assertIsDisplayed()
    }
}
