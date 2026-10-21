package com.moon.weather.widget

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.work.testing.TestListenableWorkerBuilder
import com.moon.weather.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class WidgetBackgroundAccessTest {
    @Test fun firstWidgetRequestsTheAppSpecificAndroidDialogOnce() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var requested: Intent? = null
        assertTrue(WeatherWidgetBatterySettings.request(activity, automatic = true) { requested = it })
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, requested!!.action)
        assertEquals("package:${activity.packageName}", requested!!.dataString)
        assertFalse(WeatherWidgetBatterySettings.shouldRequest(activity))
        assertFalse(WeatherWidgetBatterySettings.request(activity, automatic = true) { error("Do not nag after decline") })
        // A user can explicitly retry from the app without browsing system settings.
        assertTrue(WeatherWidgetBatterySettings.request(activity) { requested = it })
        controller.pause().stop().destroy()
    }

    @Test fun anAlreadyAllowedAppDoesNotAskAgain() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIgnoringBatteryOptimizations(activity.packageName, true)
        assertFalse(WeatherWidgetBatterySettings.shouldRequest(activity))
        assertFalse(WeatherWidgetBatterySettings.request(activity, automatic = true) { error("Already allowed") })
        controller.pause().stop().destroy()
    }

    @Test fun unavailableSystemDialogContinuesWithoutSendingPeopleToSettings() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        assertFalse(WeatherWidgetBatterySettings.request(activity, automatic = true) { throw ActivityNotFoundException() })
        assertFalse(WeatherWidgetBatterySettings.shouldRequest(activity))
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test fun olderAndroidHasTheShortNotificationRequiredForExpeditedWork() = runBlocking<Unit> {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val worker = TestListenableWorkerBuilder<WeatherWidgetRefreshWorker>(activity).build()
        val info = worker.getForegroundInfo()
        assertNotEquals(0, info.notification.smallIcon.resId)
        assertEquals(activity.getString(R.string.widget_refresh_notification_title),
            info.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertNotNull(info.notification.contentIntent)
        controller.pause().stop().destroy()
    }
}
