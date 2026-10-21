package com.moon.weather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** An idle-capable alarm for a quick update, with persistent work for recovery/offline retries. */
object WeatherWidgetRefreshScheduler {
    internal const val INTERVAL_MS = 30 * 60_000L
    internal const val RECEIVER_BUDGET_MS = 8_000L
    internal const val ACTION_REFRESH = "com.moon.weather.widget.REFRESH_ALARM"
    internal const val PERIODIC_WORK = "weather_widget_periodic_refresh"
    internal const val RETRY_WORK = "weather_widget_refresh_retry"
    private const val PREFS = "weather_widget_refresh"
    private const val NEXT_ALARM = "next_alarm_elapsed"
    private const val ALARM_REQUEST = 74_202
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    @Synchronized
    fun ensureScheduled(context: Context, advanceAlarm: Boolean = false) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val work = WorkManager.getInstance(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Remove the old non-wakeup repeating alarm when updating an existing installation.
        PendingIntent.getBroadcast(app, 74_201,
            Intent(app, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_AUTO_UPDATE_WIDGETS),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }

        if (WeatherWidgetProvider.allWidgetIds(app).isEmpty()) {
            alarms.cancel(alarmIntent(app))
            work.cancelUniqueWork(PERIODIC_WORK)
            work.cancelUniqueWork(RETRY_WORK)
            prefs.edit().remove(NEXT_ALARM).apply()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val previous = prefs.getLong(NEXT_ALARM, 0L)
        // Rendering/resizing/opening the app must not postpone an already scheduled refresh.
        val next = if (advanceAlarm || previous <= 0L || previous > now + INTERVAL_MS) {
            now + INTERVAL_MS
        } else maxOf(now + 1_000L, previous)
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, alarmIntent(app))
        prefs.edit().putLong(NEXT_ALARM, next).apply()

        work.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeatherWidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build())
    }

    internal fun enqueueRefresh(context: Context) {
        if (WeatherWidgetProvider.allWidgetIds(context).isEmpty()) return
        // Expedite while the alarm supplies temporary background access. This avoids waiting
        // for a normal job's maintenance window in battery saver. If quota is exhausted,
        // retain the durable normal job; the alarm also makes its own bounded attempt.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RETRY_WORK, ExistingWorkPolicy.KEEP, refreshRequest())
    }

    internal fun refreshRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<WeatherWidgetRefreshWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(network)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, WeatherWidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
