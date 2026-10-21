package com.moon.weather

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.moon.weather.data.WeatherLocation
import com.moon.weather.ui.RequestedCityNavigation
import com.moon.weather.ui.weatherPageKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class WeatherCityNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchWaitsForSavedCityAndKeepsItSelectedWhenGpsArrives() {
        val delft = WeatherLocation("Delft",52.01,4.36,"Netherlands")
        val searched = WeatherLocation("Paris",48.8566,2.3522,"France")
        val saved = searched.copy(name = "Paris centre",country = "")
        val locations = mutableStateOf(listOf(delft))
        val requested = mutableStateOf<WeatherLocation?>(null)
        val gps = mutableStateOf<WeatherLocation?>(null)
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState { locations.value.size }
            RequestedCityNavigation(locations.value,requested.value,pager) { requested.value = null }
            HorizontalPager(pager,Modifier.fillMaxSize(),key = { weatherPageKey(it,locations.value,gps.value) }) { index ->
                Text(locations.value[index].name)
            }
        }
        compose.runOnIdle { requested.value = searched }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0,pager.currentPage); assertNotNull(requested.value) }
        compose.runOnIdle { locations.value = listOf(delft,saved) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(saved,locations.value[pager.currentPage]); assertNull(requested.value) }
        compose.runOnIdle {
            gps.value = WeatherLocation("Current location",52.67,4.78)
            locations.value = listOf(gps.value!!,delft,saved)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2,pager.currentPage); assertEquals(saved,locations.value[pager.currentPage]) }
        // Searching the already-saved place again must also use its canonical saved entry.
        compose.runOnIdle { requested.value = searched }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(saved,locations.value[pager.currentPage]); assertNull(requested.value) }
    }
}
