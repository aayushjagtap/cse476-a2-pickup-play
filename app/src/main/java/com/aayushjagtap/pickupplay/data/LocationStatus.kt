package com.aayushjagtap.pickupplay.data

/** What the "Nearest first" feature is doing right now; drives the status banner. */
sealed interface LocationStatus {
    data object Off : LocationStatus
    data object Waiting : LocationStatus
    data class Active(val accuracyMeters: Float, val confirmedJumpMeters: Double?) : LocationStatus
    data class JumpIgnored(val jumpMeters: Double) : LocationStatus
    data object Denied : LocationStatus
    data object PermanentlyDenied : LocationStatus
    data object Unavailable : LocationStatus
    data object LocationOff : LocationStatus
}
