package com.aayushjagtap.pickupplay.data

/**
 * Plausibility filter for incoming location fixes.
 *
 * A fix that moves more than [maxJumpMeters] from the last accepted fix at an implied speed above
 * [maxSpeedMps] (faster than an airliner) is treated as a glitch and ignored. If later fixes keep
 * agreeing with that new position (within [confirmRadiusMeters]) for at least [confirmMillis],
 * the move is real and the new position is accepted.
 */
class JumpFilter(
    private val maxJumpMeters: Double = 10_000.0,
    private val maxSpeedMps: Double = 300.0,
    private val confirmRadiusMeters: Double = 500.0,
    private val confirmMillis: Long = 5_000,
) {
    sealed interface Decision {
        /** Use this fix. [confirmedJumpMeters] is set when it ends a confirmed jump. */
        data class Accepted(val confirmedJumpMeters: Double?) : Decision

        /** Ignore this fix; keep the last accepted one. */
        data class Rejected(val jumpMeters: Double) : Decision
    }

    private var accepted: GeoFix? = null
    private var candidate: GeoFix? = null

    fun reset() {
        accepted = null
        candidate = null
    }

    fun offer(fix: GeoFix): Decision {
        val last = accepted
        if (last == null) {
            accepted = fix
            return Decision.Accepted(null)
        }
        val jump = Geo.distanceMeters(last, fix)
        val seconds = maxOf(1L, fix.timeMillis - last.timeMillis) / 1000.0
        val speed = jump / seconds
        if (jump <= maxJumpMeters || speed <= maxSpeedMps) {
            accepted = fix
            candidate = null
            return Decision.Accepted(null)
        }

        // Implausible jump. Accept only once the new position has been confirmed long enough.
        val c = candidate
        if (c != null && Geo.distanceMeters(c, fix) <= confirmRadiusMeters) {
            if (fix.timeMillis - c.timeMillis >= confirmMillis) {
                accepted = fix
                candidate = null
                return Decision.Accepted(jump)
            }
        } else {
            candidate = fix
        }
        return Decision.Rejected(jump)
    }
}
