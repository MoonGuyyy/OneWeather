package com.moon.weather.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.samePlace
import com.moon.weather.data.pageKey

/** Keep the request until the saved-location flow contains its canonical place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RequestedCityNavigation(locations: List<WeatherLocation>, requested: WeatherLocation?,
    pager: PagerState, onNavigated: (WeatherLocation) -> Unit) {
    val latestOnNavigated by rememberUpdatedState(onNavigated)
    LaunchedEffect(locations, requested, pager) {
        val target = requested ?: return@LaunchedEffect
        val index = locations.indexOfFirst { it.samePlace(target) }
        if (index < 0) return@LaunchedEffect
        pager.scrollToPage(index)
        // Let the pager measure its updated keys before consuming the navigation request.
        withFrameNanos { }
        latestOnNavigated(locations[index])
    }
}

internal fun weatherPageKey(index: Int, locations: List<WeatherLocation>, gps: WeatherLocation?): String =
    if (index == 0 && gps != null) "gps" else locations.getOrNull(index)?.pageKey() ?: "empty"
