package com.moon.weather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.widget.TextView
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.moon.weather.R
import com.moon.weather.data.WeatherLocation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Exercises Android alarms, persistent work and actual RemoteViews without a device or live API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class WidgetAutoRefreshTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val manager get() = AppWidgetManager.getInstance(context)
    private val alarms get() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val location = WeatherLocation("Fixture city", 52.37, 4.89)
    private val weather get() = WeatherWidgetPreviewPublisher.sampleWeather

    @Before fun initializeWork() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test fun schedulesWakeupDuringBatterySaverAndPersistentNetworkWork() {
        bindWidget(101, WeatherLockScreenSmallProvider::class.java)
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsPowerSaveMode(true)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        val alarm = widgetAlarm()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(0L, alarm.intervalMs)
        assertEquals(SystemClock.elapsedRealtime() + WeatherWidgetRefreshScheduler.INTERVAL_MS, alarm.triggerAtMs)
        val work = work(WeatherWidgetRefreshScheduler.PERIODIC_WORK).single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertFalse(work.constraints.requiresBatteryNotLow())
    }

    @Test fun openingOrResizingDoesNotPostponeTheAlarmOrReplacePeriodicWork() {
        bindWidget(102, WeatherLockScreenWideProvider::class.java)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        val deadline = widgetAlarm().triggerAtMs
        val workId = work(WeatherWidgetRefreshScheduler.PERIODIC_WORK).single().id
        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        assertEquals(deadline, widgetAlarm().triggerAtMs)
        assertEquals(workId, work(WeatherWidgetRefreshScheduler.PERIODIC_WORK).single().id)
    }

    @Test fun rebootRestoresSchedulingAndQueuesRecoveryWithoutOpeningTheApp() {
        bindWidget(103, WeatherLockScreenWideProvider::class.java)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))
        WeatherWidgetRefreshReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(SystemClock.elapsedRealtime() + WeatherWidgetRefreshScheduler.INTERVAL_MS, widgetAlarm().triggerAtMs)
        assertEquals(WorkInfo.State.ENQUEUED, work(WeatherWidgetRefreshScheduler.RETRY_WORK).single().state)
    }

    @Test fun retriesAreUniqueAndOldNonWakeupAlarmIsRemoved() {
        bindWidget(104, WeatherLockScreenSmallProvider::class.java)
        val legacy = PendingIntent.getBroadcast(context, 74_201,
            Intent(context, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_AUTO_UPDATE_WIDGETS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setInexactRepeating(AlarmManager.RTC, 30_000L, 30_000L, legacy)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        assertFalse(alarms.scheduledAlarms.any { it.operation == legacy })
        WeatherWidgetRefreshScheduler.enqueueRefresh(context)
        val retryId = work(WeatherWidgetRefreshScheduler.RETRY_WORK).single().id
        val queued = WeatherWidgetRefreshScheduler.refreshRequest().workSpec
        assertTrue("Refreshes request expedited execution to resist battery saver", queued.expedited)
        assertEquals("Expedited work must not be delayed past the alarm exemption", 0L, queued.initialDelay)
        assertEquals(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, queued.outOfQuotaPolicy)
        WeatherWidgetRefreshScheduler.enqueueRefresh(context)
        assertEquals(retryId, work(WeatherWidgetRefreshScheduler.RETRY_WORK).single().id)
    }

    @Test fun noWidgetsMeansNoRefreshAlarmOrQueuedWeatherWork() {
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        WeatherWidgetRefreshScheduler.enqueueRefresh(context)
        assertFalse(alarms.scheduledAlarms.any { shadowOf(it.operation).savedIntent.action == WeatherWidgetRefreshScheduler.ACTION_REFRESH })
        assertTrue(work(WeatherWidgetRefreshScheduler.PERIODIC_WORK).isEmpty())
        assertTrue(work(WeatherWidgetRefreshScheduler.RETRY_WORK).isEmpty())
    }

    @Test fun backgroundWeatherUsesSavedLocationWithoutWaitingForGps() = runBlocking {
        WeatherWidgetConfigStore.saveCurrentLocation(context, location)
        val result = WeatherWidgetDataSource.fetchWeatherWithSources(context, 105,
            locate = { error("Background refresh must not wait for GPS") },
            fetch = { assertEquals(location, it); weather }, background = true)
        assertEquals(location, result!!.first)
        assertNotNull(result.second.current)
    }

    @Test fun closedAppRefreshUpdatesBothLockWidgetSizesAndSnapshots() = runBlocking {
        bindWidget(106, WeatherLockScreenSmallProvider::class.java)
        bindWidget(107, WeatherLockScreenWideProvider::class.java)
        WeatherWidgetConfigStore.saveCurrentLocation(context, location)
        val changed = weather.copy(current = weather.current!!.copy(temperature = 12.0))
        assertTrue(WeatherWidgetProvider.refreshAllWidgets(context) { app, id ->
            WeatherWidgetDataSource.fetchWeatherWithSources(app, id,
                locate = { error("No foreground GPS") }, fetch = { changed }, background = true)
        })
        for (id in listOf(106, 107)) {
            assertEquals("12°", shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
            assertEquals(12.0, WeatherWidgetSnapshotStore.load(context, id)!!.result.second.current!!.temperature, 0.0)
        }
        assertTrue(WeatherWidgetProvider.refreshAllWidgets(context) { _, _ -> error("Fresh retry should reuse the new snapshot") })
    }

    @Test fun offlineRefreshKeepsLastReadingAndReportsFailureForRetry() = runBlocking {
        bindWidget(108, WeatherLockScreenSmallProvider::class.java)
        WeatherWidgetSnapshotStore.save(context, 108, location to weather, System.currentTimeMillis() - 60 * 60_000L)
        assertFalse(WeatherWidgetProvider.refreshAllWidgets(context) { _, _ -> throw IOException("Offline fixture") })
        assertEquals("17°", shadowOf(manager).getViewFor(108).findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
        assertEquals(weather.current!!.temperature, WeatherWidgetSnapshotStore.load(context, 108)!!.result.second.current!!.temperature, 0.0)
    }

    @Test fun cancellingShortAttemptCancelsSlowRequestsAndPreservesTheSnapshot() = runBlocking {
        bindWidget(109, WeatherLockScreenSmallProvider::class.java)
        WeatherWidgetSnapshotStore.save(context, 109, location to weather, System.currentTimeMillis() - 60 * 60_000L)
        var cancelled = false
        val started = CompletableDeferred<Unit>()
        val attempt = async {
            WeatherWidgetProvider.refreshAllWidgets(context) { _, _ ->
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        // Wait for the request before cancelling; first-time layout inflation can be slow
        // on a busy build machine and must not make this cancellation regression flaky.
        withTimeout(10_000L) { started.await() }
        attempt.cancelAndJoin()
        assertTrue(cancelled)
        assertNotNull(WeatherWidgetSnapshotStore.load(context, 109))
    }

    private fun bindWidget(id: Int, provider: Class<out WeatherWidgetProvider>) {
        shadowOf(manager).addBoundWidget(id, AppWidgetProviderInfo().apply {
            this.provider = ComponentName(context, provider)
        })
    }

    private fun widgetAlarm() = alarms.scheduledAlarms.single {
        shadowOf(it.operation).savedIntent.action == WeatherWidgetRefreshScheduler.ACTION_REFRESH
    }

    private fun work(name: String): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
}
