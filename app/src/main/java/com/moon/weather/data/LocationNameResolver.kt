package com.moon.weather.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

/** A slow geocoder must never hold the weather screen on its loading cover. */
internal object LocationNameResolver {
    private val legacyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun resolve(context: Context, latitude: Double, longitude: Double): String {
        val addresses = withTimeoutOrNull(2_500L) {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(emptyList())
                            }
                        })
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
            } else {
                // The older blocking API cannot be cancelled; await it outside the caller's scope.
                val pending = legacyScope.async {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(latitude, longitude, 1).orEmpty() }.getOrDefault(emptyList())
                }
                try { pending.await() } finally { pending.cancel() }
            }
        }
        val address = addresses?.firstOrNull()
        return address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Current location"
    }
}
