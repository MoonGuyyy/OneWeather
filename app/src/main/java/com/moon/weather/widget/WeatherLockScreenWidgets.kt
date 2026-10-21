package com.moon.weather.widget

import android.content.Context
import android.widget.RemoteViews
import com.moon.weather.R
import com.moon.weather.data.WeatherResponse
import com.moon.weather.utils.WeatherCodeMapper
import com.moon.weather.utils.Temperature

/** Samsung Brief widgets use native white content, with the host supplying the background. */
internal object WeatherLockScreenWidgets {
    fun isProvider(name: String?): Boolean = name == WeatherLockScreenSmallProvider::class.java.name ||
        name == WeatherLockScreenWideProvider::class.java.name

    fun icon(code: Int, isDay: Boolean): Int = WeatherCodeMapper.getIconRes(code, isDay)

    fun views(context: Context, wide: Boolean, weather: WeatherResponse?, useCelsius: Boolean): RemoteViews {
        val current = weather?.current
        val temperature = current?.temperature?.let {
            "${Temperature.value(it, useCelsius)}°"
        } ?: "--°"
        return RemoteViews(context.packageName,
            if (wide) R.layout.widget_lock_weather_2x1 else R.layout.widget_lock_weather_1x1).apply {
            setImageViewResource(R.id.lock_weather_icon, icon(current?.weatherCode ?: 3, current?.isDay == 1))
            setTextViewText(R.id.lock_weather_temperature, temperature)
            val condition = current?.let { WeatherCodeMapper.getWeatherDescription(it.weatherCode) }
                ?: context.getString(R.string.lock_weather_unavailable)
            setContentDescription(R.id.lock_weather_icon, condition)
            setContentDescription(android.R.id.background,
                "$condition, $temperature ${if (useCelsius) "Celsius" else "Fahrenheit"}")
        }
    }
}
