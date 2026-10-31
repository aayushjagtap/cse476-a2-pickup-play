package com.aayushjagtap.pickupplay.data

import androidx.annotation.StringRes
import com.aayushjagtap.pickupplay.R
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One location reading from the Android location API. */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
)

/** How the user would realistically get to a session, decided by distance thresholds. */
enum class TravelBand(@StringRes val labelRes: Int, val maxMiles: Double) {
    WALKABLE(R.string.band_walkable, 1.0),
    BIKEABLE(R.string.band_bikeable, 3.0),
    DRIVE(R.string.band_drive, Double.MAX_VALUE);

    companion object {
        fun forMiles(miles: Double): TravelBand = entries.first { miles <= it.maxMiles }
    }
}

/** A session paired with its processed distance from the user (null when unknown). */
data class RankedSession(val session: PickupSession, val distanceMeters: Double?)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0
    const val METERS_PER_MILE = 1609.344
    /** Average walking pace, 3 mph = 20 minutes per mile. */
    private const val WALK_MINUTES_PER_MILE = 20.0

    /** Great-circle distance between two lat/lng points (haversine formula), in meters. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = p2 - p1
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceMeters(a: GeoFix, b: GeoFix): Double =
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun metersToMiles(meters: Double): Double = meters / METERS_PER_MILE

    fun walkMinutes(miles: Double): Int = maxOf(1, Math.round(miles * WALK_MINUTES_PER_MILE).toInt())

    /**
     * Pairs every session with its distance from [fix] and sorts nearest first. Sessions without
     * coordinates (e.g. newly created ones) go last. With no fix, the original order is kept.
     */
    fun rankByDistance(sessions: List<PickupSession>, fix: GeoFix?): List<RankedSession> {
        if (fix == null) return sessions.map { RankedSession(it, null) }
        return sessions
            .map { s ->
                val lat = s.latitude
                val lng = s.longitude
                RankedSession(
                    s,
                    if (lat != null && lng != null) {
                        haversineMeters(fix.latitude, fix.longitude, lat, lng)
                    } else {
                        null
                    },
                )
            }
            .sortedWith(compareBy(nullsLast<Double>()) { it.distanceMeters })
    }
}
