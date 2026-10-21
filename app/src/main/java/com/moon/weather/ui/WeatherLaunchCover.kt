package com.moon.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

internal val WeatherLaunchBlue = Color(0xFF37AFE4)

/** Covers partially composed weather until its first data and prepared artwork are ready. */
@Composable
internal fun BoxScope.WeatherLaunchCover(ready: Boolean) {
    AnimatedVisibility(visible = !ready, exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(WeatherLaunchBlue)
            .testTag("weather-launch-cover")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center) {
            WeatherLoadingAnimation()
        }
    }
}
