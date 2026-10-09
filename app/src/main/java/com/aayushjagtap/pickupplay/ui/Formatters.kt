package com.aayushjagtap.pickupplay.ui

import android.content.Context
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.PickupSession

/** Shared text formatting so the list and detail screens describe sessions the same way. */
fun PickupSession.whenText(context: Context): String = context.getString(
    R.string.when_format,
    context.getString(day.labelRes),
    context.getString(time.rangeRes),
)

fun PickupSession.playersText(context: Context): String =
    context.getString(R.string.players_format, playerCount, maxPlayers)

/** Badge shown on a session: hosting beats joined beats full. Null means no badge. */
fun PickupSession.badgeText(context: Context): String? = when {
    isHosting -> context.getString(R.string.badge_hosting)
    isJoined -> context.getString(R.string.badge_joined)
    isFull -> context.getString(R.string.badge_full)
    else -> null
}
