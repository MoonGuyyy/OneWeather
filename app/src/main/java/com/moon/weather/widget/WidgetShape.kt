package com.moon.weather.widget

/** The same outline is used by the launcher host, bitmap artwork, and settings preview. */
internal object WidgetShape {
    fun radius(heightDp: Float): Float = if (heightDp < 105f) heightDp / 2f else 24f
}
