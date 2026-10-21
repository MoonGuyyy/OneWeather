package com.moon.weather.ui

import androidx.compose.ui.graphics.Color

internal fun weatherNavigationColor(code: Int, isDay: Boolean): Color {
    if (!isDay) return Color(0xFF041824)
    return when (atmosphereKind(code)) {
        AtmosphereKind.DRIZZLE, AtmosphereKind.RAIN, AtmosphereKind.RAIN_SHOWERS,
        AtmosphereKind.FREEZING_RAIN, AtmosphereKind.THUNDER, AtmosphereKind.HAIL -> Color(0xFF30360F)
        AtmosphereKind.SNOW, AtmosphereKind.SNOW_GRAINS, AtmosphereKind.SNOW_SHOWERS -> Color(0xFFE3F3FA)
        else -> Color(0xFF4B5111)
    }
}

internal fun weatherNavigationContentColor(code: Int, isDay: Boolean): Color =
    if (isDay && atmosphereKind(code) in listOf(AtmosphereKind.SNOW, AtmosphereKind.SNOW_GRAINS,
            AtmosphereKind.SNOW_SHOWERS)) Color(0xFF244658) else Color.White
