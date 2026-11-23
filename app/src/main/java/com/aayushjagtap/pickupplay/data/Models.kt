package com.aayushjagtap.pickupplay.data

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.aayushjagtap.pickupplay.R

/** Sports the prototype supports. Each value maps to its label, icon text, and chip in the create form. */
enum class Sport(@StringRes val labelRes: Int, @StringRes val emojiRes: Int, @IdRes val chipId: Int) {
    BASKETBALL(R.string.sport_basketball, R.string.emoji_basketball, R.id.chip_sport_basketball),
    SOCCER(R.string.sport_soccer, R.string.emoji_soccer, R.id.chip_sport_soccer),
    VOLLEYBALL(R.string.sport_volleyball, R.string.emoji_volleyball, R.id.chip_sport_volleyball),
    TENNIS(R.string.sport_tennis, R.string.emoji_tennis, R.id.chip_sport_tennis),
    ULTIMATE(R.string.sport_ultimate, R.string.emoji_ultimate, R.id.chip_sport_ultimate),
    PICKLEBALL(R.string.sport_pickleball, R.string.emoji_pickleball, R.id.chip_sport_pickleball);

    companion object {
        fun fromChipId(@IdRes id: Int): Sport? = entries.firstOrNull { it.chipId == id }
    }
}

enum class Day(@StringRes val labelRes: Int, @IdRes val chipId: Int) {
    TODAY(R.string.day_today, R.id.chip_day_today),
    TOMORROW(R.string.day_tomorrow, R.id.chip_day_tomorrow),
    SATURDAY(R.string.day_saturday, R.id.chip_day_saturday),
    SUNDAY(R.string.day_sunday, R.id.chip_day_sunday);

    companion object {
        fun fromChipId(@IdRes id: Int): Day? = entries.firstOrNull { it.chipId == id }
    }
}

enum class TimeSlot(@StringRes val labelRes: Int, @StringRes val rangeRes: Int, @IdRes val chipId: Int) {
    MORNING(R.string.time_morning, R.string.time_morning_range, R.id.chip_time_morning),
    AFTERNOON(R.string.time_afternoon, R.string.time_afternoon_range, R.id.chip_time_afternoon),
    EVENING(R.string.time_evening, R.string.time_evening_range, R.id.chip_time_evening);

    companion object {
        fun fromChipId(@IdRes id: Int): TimeSlot? = entries.firstOrNull { it.chipId == id }
    }
}

enum class SkillLevel(@StringRes val labelRes: Int, @IdRes val chipId: Int) {
    CASUAL(R.string.skill_casual, R.id.chip_skill_casual),
    INTERMEDIATE(R.string.skill_intermediate, R.id.chip_skill_intermediate),
    COMPETITIVE(R.string.skill_competitive, R.id.chip_skill_competitive);

    companion object {
        fun fromChipId(@IdRes id: Int): SkillLevel? = entries.firstOrNull { it.chipId == id }
    }
}

/** One pickup game. All data is synthetic and lives in memory for the prototype. */
data class PickupSession(
    val id: Long,
    val sport: Sport,
    val title: String,
    val location: String,
    val day: Day,
    val time: TimeSlot,
    val skill: SkillLevel,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostName: String,
    val notes: String,
    val isHosting: Boolean = false,
    val isJoined: Boolean = false,
    /** Where the session is played; null when unknown (sessions created in the app). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** A4: stable id shared with the Firestore document; null for the built-in sample sessions. */
    val remoteId: String? = null,
    /** A4: Firebase UID of the host once the session belongs to a signed-in account. */
    val ownerUid: String? = null,
    val syncState: SyncState = SyncState.LOCAL,
) {
    val isFull: Boolean get() = playerCount >= maxPlayers
}

/**
 * Cloud status of a session. Only sessions the user creates (and sessions downloaded from the
 * server) take part in sync; the sample sessions stay [LOCAL].
 */
enum class SyncState {
    /** Sample data that lives only on this device and is never uploaded. */
    LOCAL,
    /** Created by the user, waiting to be uploaded (e.g. signed out). */
    PENDING,
    /** Upload in progress. */
    UPLOADING,
    /** The last upload attempt failed; the session is still saved on this device. */
    FAILED,
    /** Confirmed on the Firestore server. */
    SYNCED;

    val needsUpload: Boolean get() = this == PENDING || this == UPLOADING || this == FAILED
}
