package com.moon.weather.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.Activity
import android.util.Log

object WeatherWidgetBatterySettings {
    private const val PREFS = "weather_widget_background_access"
    private const val ASKED = "permission_requested"

    fun isUnrestricted(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    internal fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"))

    /** Only offer the system permission automatically once, after the user adds a widget. */
    fun shouldRequest(context: Context): Boolean = !isUnrestricted(context) &&
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)

    fun request(activity: Activity, automatic: Boolean = false,
                launch: (Intent) -> Unit = { activity.startActivity(it) }): Boolean {
        if (isUnrestricted(activity) || (automatic && !shouldRequest(activity))) return false
        try {
            launch(requestIntent(activity))
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
            return true
        } catch (error: ActivityNotFoundException) {
            Log.w("WeatherWidget", "Background access dialog unavailable", error)
        } catch (error: SecurityException) {
            Log.w("WeatherWidget", "Background access dialog blocked", error)
        }
        // Continue automatic scheduling even on devices without this standard Android dialog.
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        return false
    }
}
