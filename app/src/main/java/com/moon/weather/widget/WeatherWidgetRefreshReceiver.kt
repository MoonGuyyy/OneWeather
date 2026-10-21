package com.moon.weather.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WeatherWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WeatherWidgetRefreshScheduler.ensureScheduled(context, advanceAlarm = true)
                WeatherWidgetRefreshScheduler.enqueueRefresh(context)
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    WeatherWidgetPreviewPublisher.publishIfNeeded(context)
                }
                return
            }
            WeatherWidgetRefreshScheduler.ACTION_REFRESH -> {
                // Rearm before fetching; a killed process must not break the refresh chain.
                WeatherWidgetRefreshScheduler.ensureScheduled(context, advanceAlarm = true)
                WeatherWidgetRefreshScheduler.enqueueRefresh(context)
            }
            else -> return
        }
        if (WeatherWidgetProvider.allWidgetIds(context).isEmpty()) return

        val app = context.applicationContext
        val pending = goAsync()
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Weather:WidgetRefresh")
        // The alarm supplies a brief idle exemption. Hold CPU only for this bounded attempt;
        // a durable worker handles slower requests without overrunning a broadcast receiver.
        wakeLock.acquire(WeatherWidgetRefreshScheduler.RECEIVER_BUDGET_MS + 1_000L)
        scope.launch {
            try {
                val refreshed = withTimeoutOrNull(WeatherWidgetRefreshScheduler.RECEIVER_BUDGET_MS) {
                    WeatherWidgetProvider.refreshAllWidgets(app)
                }
                Log.d("WeatherWidget", "Alarm refresh completed: $refreshed")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
