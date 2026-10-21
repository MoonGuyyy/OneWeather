package com.moon.weather.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.moon.weather.ui.theme.LocalWeatherDarkMode

/** Shared glass treatment for the locations drawer and expanded forecast. */
@Composable
internal fun Modifier.frostedPanel(shape: Shape): Modifier {
    val dark = LocalWeatherDarkMode.current
    return weatherGlass(shape, LocalWeatherGlassTint.current, showSheen = false, crossfadeTint = true)
        .border(1.dp, Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (dark) .25f else .42f),
            Color.White.copy(alpha = if (dark) .07f else .12f)
        )), shape)
}
