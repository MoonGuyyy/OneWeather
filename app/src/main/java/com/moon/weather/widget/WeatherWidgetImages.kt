package com.moon.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import com.moon.weather.R
import kotlin.math.ceil

/** Generated PNGs for widgets and the launch illustration; forecast rows retain their original icons. */
internal object WeatherWidgetImages {
    val resources = mapOf(
        R.drawable.clear_day to R.drawable.widget_icon_clear_day,
        R.drawable.clear to R.drawable.widget_icon_clear,
        R.drawable.cloudy_day to R.drawable.widget_icon_cloudy_day,
        R.drawable.cloudy_fog to R.drawable.widget_icon_cloudy_fog,
        R.drawable.cloudy_night to R.drawable.widget_icon_cloudy_night,
        R.drawable.cloudy to R.drawable.widget_icon_cloudy,
        R.drawable.fog to R.drawable.widget_icon_fog,
        R.drawable.isolated_snow_day to R.drawable.widget_icon_isolated_snow_day,
        R.drawable.isolated_snow_night to R.drawable.widget_icon_isolated_snow_night,
        R.drawable.isolated_storm_day to R.drawable.widget_icon_isolated_storm_day,
        R.drawable.isolated_storms_night to R.drawable.widget_icon_isolated_storms_night,
        R.drawable.light_rain_cloudy to R.drawable.widget_icon_light_rain_cloudy,
        R.drawable.light_rain_day to R.drawable.widget_icon_light_rain_day,
        R.drawable.light_rain_night to R.drawable.widget_icon_light_rain_night,
        R.drawable.light_snow_cloudy to R.drawable.widget_icon_light_snow_cloudy,
        R.drawable.rainbow to R.drawable.widget_icon_rainbow,
        R.drawable.rainy to R.drawable.widget_icon_rainy,
        R.drawable.sand to R.drawable.widget_icon_sand,
        R.drawable.snow_and_rain to R.drawable.widget_icon_snow_and_rain,
        R.drawable.snow to R.drawable.widget_icon_snow,
        R.drawable.snowstorm to R.drawable.widget_icon_snowstorm,
        R.drawable.some_clouds_day to R.drawable.widget_icon_some_clouds_day,
        R.drawable.some_clouds_night to R.drawable.widget_icon_some_clouds_night,
        R.drawable.thunderstorm_and_rain to R.drawable.widget_icon_thunderstorm_and_rain,
        R.drawable.thunderstorm to R.drawable.widget_icon_thunderstorm,
        R.drawable.windy to R.drawable.widget_icon_windy
    )

    private val cache = object : LruCache<Int, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }

    fun drawable(context: Context, originalResource: Int): BitmapDrawable? {
        val resource = resources[originalResource] ?: originalResource
        val cached = cache.get(resource)
        val bitmap = cached ?: run {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(context.resources, resource, options)
            val needed = ceil(72f * context.resources.displayMetrics.density).toInt().coerceAtLeast(256)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= needed) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            BitmapFactory.decodeResource(context.resources, resource, options)?.also { cache.put(resource, it) }
        } ?: return null
        return BitmapDrawable(context.resources, bitmap).apply { setFilterBitmap(true) }
    }
}
