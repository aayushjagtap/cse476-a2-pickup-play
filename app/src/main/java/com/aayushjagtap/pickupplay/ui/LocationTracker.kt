package com.aayushjagtap.pickupplay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.aayushjagtap.pickupplay.data.GeoFix

/**
 * Acquisition stage: wraps Android's LocationManager and forwards each raw fix to [onFix].
 * Callers must hold a location permission before calling [start].
 */
class LocationTracker(
    private val context: Context,
    private val onFix: (GeoFix) -> Unit,
) {
    enum class StartResult { STARTED, UNAVAILABLE, LOCATION_OFF }

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    private val listener = LocationListenerCompat { location ->
        onFix(
            GeoFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                // Monotonic clock: unaffected by wall-clock or GPS-time differences.
                timeMillis = location.elapsedRealtimeNanos / 1_000_000,
            )
        )
    }

    private var running = false

    /** True when the device has location hardware and at least one location provider. */
    fun isLocationSupported(): Boolean {
        val lm = locationManager ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) &&
            lm.allProviders.isNotEmpty()
    }

    /**
     * Starts location updates with one provider: GPS when precise permission was granted,
     * otherwise the fused or network provider (approximate location).
     */
    @SuppressLint("MissingPermission") // The fragment checks the permission before calling start().
    fun start(precise: Boolean): StartResult {
        stop()
        val lm = locationManager ?: return StartResult.UNAVAILABLE
        if (!isLocationSupported()) return StartResult.UNAVAILABLE
        if (!LocationManagerCompat.isLocationEnabled(lm)) return StartResult.LOCATION_OFF

        val preferred = if (precise) {
            listOf(LocationManager.GPS_PROVIDER, FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        val provider = preferred.firstOrNull { it in lm.allProviders && lm.isProviderEnabled(it) }
            ?: return StartResult.UNAVAILABLE

        val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(
                if (precise) LocationRequestCompat.QUALITY_HIGH_ACCURACY
                else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
            )
            .build()
        LocationManagerCompat.requestLocationUpdates(
            lm, provider, request, ContextCompat.getMainExecutor(context), listener
        )
        running = true
        return StartResult.STARTED
    }

    fun stop() {
        if (!running) return
        locationManager?.let { LocationManagerCompat.removeUpdates(it, listener) }
        running = false
    }

    private companion object {
        /** LocationManager.FUSED_PROVIDER, which only exists as a constant from API 31. */
        const val FUSED_PROVIDER = "fused"
        const val UPDATE_INTERVAL_MS = 2_000L
        const val MIN_UPDATE_INTERVAL_MS = 1_000L
    }
}
