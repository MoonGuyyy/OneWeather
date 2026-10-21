package com.moon.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Uses Android's location providers so the app also works without Google Play services. */
object DeviceLocationProvider {
    suspend fun getLocation(context: Context, preferFresh: Boolean = false): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = try {
            listOfNotNull(
                LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
                LocationManager.GPS_PROVIDER.takeIf { fine && manager.isProviderEnabled(it) }
            )
        } catch (_: Exception) {
            return null
        }
        if (providers.isEmpty()) return null

        return try {
            val cached = providers.mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            if (!preferFresh && cached != null && System.currentTimeMillis() - cached.time < 10 * 60_000L) {
                cached
            } else {
                withTimeoutOrNull(8_000L) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                manager.removeUpdates(this)
                                if (continuation.isActive) continuation.resume(location)
                            }

                            @Deprecated("Required by older Android versions")
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                            override fun onProviderEnabled(provider: String) = Unit

                            override fun onProviderDisabled(provider: String) = Unit
                        }
                        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                        try {
                            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                        } catch (e: Exception) {
                            manager.removeUpdates(listener)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                } ?: cached
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
