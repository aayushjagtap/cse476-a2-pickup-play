package com.aayushjagtap.pickupplay.ui

import android.content.Context
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.PickupSession
import com.aayushjagtap.pickupplay.data.SyncState

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

/** A4: cloud status of a user-created or downloaded session; null for local sample sessions. */
fun PickupSession.syncText(context: Context): String? = when (syncState) {
    SyncState.LOCAL -> null
    SyncState.PENDING -> context.getString(R.string.sync_pending)
    SyncState.UPLOADING -> context.getString(R.string.sync_uploading)
    SyncState.FAILED -> context.getString(R.string.sync_failed)
    SyncState.SYNCED -> context.getString(R.string.sync_synced)
}
