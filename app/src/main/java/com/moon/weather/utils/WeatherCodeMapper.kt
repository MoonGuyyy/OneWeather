package com.moon.weather.utils

import com.moon.weather.R

object WeatherCodeMapper {

    fun getIconRes(weatherCode: Int, isDay: Boolean): Int {
        return when (weatherCode) {
            0, 1 -> if (isDay) R.drawable.clear_day else R.drawable.clear
            2 -> if (isDay) R.drawable.some_clouds_day else R.drawable.some_clouds_night
            3 -> if (isDay) R.drawable.cloudy_day else R.drawable.cloudy_night
            45, 48 -> R.drawable.fog
            51, 53, 55 -> if (isDay) R.drawable.light_rain_day else R.drawable.light_rain_night
            56, 57 -> R.drawable.snow_and_rain
            61 -> if (isDay) R.drawable.light_rain_day else R.drawable.light_rain_night
            63, 65 -> R.drawable.rainy
            66, 67 -> R.drawable.snow_and_rain
            71, 73, 75, 77 -> R.drawable.snow
            80, 81, 82 -> R.drawable.rainy
            85, 86 -> if (isDay) R.drawable.isolated_snow_day else R.drawable.isolated_snow_night
            95 -> if (isDay) R.drawable.isolated_storm_day else R.drawable.isolated_storms_night
            96, 99 -> R.drawable.thunderstorm_and_rain
            else -> if (isDay) R.drawable.clear_day else R.drawable.clear
        }
    }

    fun getBackgroundRes(weatherCode: Int, isDay: Boolean,
        shape: WeatherArtworkShape = WeatherArtworkShape.PHONE): Int {
        val original = when (weatherCode) {
            0, 1 -> if (isDay) R.drawable.sunnyday else R.drawable.sunnynight
            2 -> if (isDay) R.drawable.partly_cloudy_day else R.drawable.partly_cloudy_night
            3, 45, 48 -> if (isDay) R.drawable.cloudyday else R.drawable.cloudynight
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> if (isDay) R.drawable.rainyday else R.drawable.rainynight
            71, 73, 75, 77, 85, 86 -> if (isDay) R.drawable.snowy_day else R.drawable.snowy_night
            95, 96, 99 -> if (isDay) R.drawable.thunder_day else R.drawable.thunder_night
            else -> if (isDay) R.drawable.sunnyday else R.drawable.sunnynight
        }
        if (shape == WeatherArtworkShape.PHONE) return original
        val variants = when (original) {
            R.drawable.sunnyday -> R.drawable.sunnyday_landscape to R.drawable.sunnyday_portrait
            R.drawable.sunnynight -> R.drawable.sunnynight_landscape to R.drawable.sunnynight_portrait
            R.drawable.partly_cloudy_day -> R.drawable.partly_cloudy_day_landscape to R.drawable.partly_cloudy_day_portrait
            R.drawable.partly_cloudy_night -> R.drawable.partly_cloudy_night_landscape to R.drawable.partly_cloudy_night_portrait
            R.drawable.cloudyday -> R.drawable.cloudyday_landscape to R.drawable.cloudyday_portrait
            R.drawable.cloudynight -> R.drawable.cloudynight_landscape to R.drawable.cloudynight_portrait
            R.drawable.rainyday -> R.drawable.rainyday_landscape to R.drawable.rainyday_portrait
            R.drawable.rainynight -> R.drawable.rainynight_landscape to R.drawable.rainynight_portrait
            R.drawable.snowy_day -> R.drawable.snowy_day_landscape to R.drawable.snowy_day_portrait
            R.drawable.snowy_night -> R.drawable.snowy_night_landscape to R.drawable.snowy_night_portrait
            R.drawable.thunder_day -> R.drawable.thunder_day_landscape to R.drawable.thunder_day_portrait
            else -> R.drawable.thunder_night_landscape to R.drawable.thunder_night_portrait
        }
        return if (shape == WeatherArtworkShape.LANDSCAPE) variants.first else variants.second
    }

    
    fun getWeatherDescription(weatherCode: Int): String {
        return when (weatherCode) {
            0 -> "Clear"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Rain"
            56, 57 -> "Rain"
            61, 63 -> "Rain"
            65 -> "Heavy rain"
            66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snowy"
            80, 81 -> "Rain"
            82 -> "Heavy rain"
            85, 86 -> "Snowy"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}


