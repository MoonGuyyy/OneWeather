package com.moon.weather.widget

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.moon.weather.MainActivity
import com.moon.weather.R
import kotlinx.coroutines.withTimeoutOrNull

class WeatherWidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    // On Android 12+ expedited work uses a system job. Older Android versions need a
    // short-lived foreground notification, which WorkManager removes when work finishes.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifications = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL,
                applicationContext.getString(R.string.widget_refresh_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_widget_refresh_notification)
            .setContentTitle(applicationContext.getString(R.string.widget_refresh_notification_title))
            .setContentText(applicationContext.getString(R.string.widget_refresh_notification_text))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(74_203, notification)
    }

    override suspend fun doWork(): Result {
        if (WeatherWidgetProvider.allWidgetIds(applicationContext).isEmpty()) {
            WeatherWidgetRefreshScheduler.ensureScheduled(applicationContext)
            return Result.success()
        }
        WeatherWidgetRefreshScheduler.ensureScheduled(applicationContext)
        val refreshed = withTimeoutOrNull(60_000L) {
            WeatherWidgetProvider.refreshAllWidgets(applicationContext)
        } ?: false
        return if (refreshed) Result.success() else Result.retry()
    }

    companion object {
        private const val CHANNEL = "widget_refresh"
    }
}
