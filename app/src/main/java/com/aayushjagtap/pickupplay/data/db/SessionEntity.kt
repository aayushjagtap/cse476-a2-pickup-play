package com.aayushjagtap.pickupplay.data.db

import android.content.ContentValues
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aayushjagtap.pickupplay.data.Day
import com.aayushjagtap.pickupplay.data.PickupSession
import com.aayushjagtap.pickupplay.data.SkillLevel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.data.SyncState
import com.aayushjagtap.pickupplay.data.TimeSlot

/** Table and column names, shared by the entity, the DAO's SQL and the first-run seed. */
object SessionTable {
    const val NAME = "sessions"
    const val ID = "id"
    const val SPORT = "sport"
    const val TITLE = "title"
    const val LOCATION = "location"
    const val DAY = "day"
    const val TIME_SLOT = "time_slot"
    const val SKILL = "skill"
    const val MAX_PLAYERS = "max_players"
    const val PLAYER_COUNT = "player_count"
    const val HOST_NAME = "host_name"
    const val NOTES = "notes"
    const val IS_HOSTING = "is_hosting"
    const val IS_JOINED = "is_joined"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val CREATED_AT = "created_at"
    // A4 Phase 2 (database version 2)
    const val REMOTE_ID = "remote_id"
    const val OWNER_UID = "owner_uid"
    const val SYNC_STATE = "sync_state"
    const val SYNCED_AT = "synced_at"
}

/**
 * One row of the `sessions` table. Mirrors [PickupSession]; enums are stored by their stable
 * `name` so reordering an enum never corrupts saved data. [createdAt] orders the list so newly
 * created sessions appear first, as they did when sessions lived in memory (seed rows use 0).
 */
@Entity(
    tableName = SessionTable.NAME,
    indices = [
        Index(SessionTable.SPORT),
        // One local row per cloud document: merging server data can never create duplicates.
        Index(value = [SessionTable.REMOTE_ID], unique = true),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = SessionTable.ID) val id: Long = 0,
    @ColumnInfo(name = SessionTable.SPORT) val sport: String,
    @ColumnInfo(name = SessionTable.TITLE) val title: String,
    @ColumnInfo(name = SessionTable.LOCATION) val location: String,
    @ColumnInfo(name = SessionTable.DAY) val day: String,
    @ColumnInfo(name = SessionTable.TIME_SLOT) val timeSlot: String,
    @ColumnInfo(name = SessionTable.SKILL) val skill: String,
    @ColumnInfo(name = SessionTable.MAX_PLAYERS) val maxPlayers: Int,
    @ColumnInfo(name = SessionTable.PLAYER_COUNT) val playerCount: Int,
    @ColumnInfo(name = SessionTable.HOST_NAME) val hostName: String,
    @ColumnInfo(name = SessionTable.NOTES) val notes: String,
    @ColumnInfo(name = SessionTable.IS_HOSTING) val isHosting: Boolean,
    @ColumnInfo(name = SessionTable.IS_JOINED) val isJoined: Boolean,
    @ColumnInfo(name = SessionTable.LATITUDE) val latitude: Double?,
    @ColumnInfo(name = SessionTable.LONGITUDE) val longitude: Double?,
    @ColumnInfo(name = SessionTable.CREATED_AT) val createdAt: Long,
    /** Firestore document id (a UUID). Null for sample sessions, which are never uploaded. */
    @ColumnInfo(name = SessionTable.REMOTE_ID) val remoteId: String? = null,
    @ColumnInfo(name = SessionTable.OWNER_UID) val ownerUid: String? = null,
    @ColumnInfo(name = SessionTable.SYNC_STATE, defaultValue = "LOCAL")
    val syncState: String = SyncState.LOCAL.name,
    /** When the server last confirmed this row (device clock, millis). */
    @ColumnInfo(name = SessionTable.SYNCED_AT) val syncedAt: Long? = null,
) {
    /** Converts to the UI model, or null if a stored enum name is no longer recognised. */
    fun toModel(): PickupSession? = runCatching {
        PickupSession(
            id = id,
            sport = Sport.valueOf(sport),
            title = title,
            location = location,
            day = Day.valueOf(day),
            time = TimeSlot.valueOf(timeSlot),
            skill = SkillLevel.valueOf(skill),
            maxPlayers = maxPlayers,
            playerCount = playerCount,
            hostName = hostName,
            notes = notes,
            isHosting = isHosting,
            isJoined = isJoined,
            latitude = latitude,
            longitude = longitude,
            remoteId = remoteId,
            ownerUid = ownerUid,
            syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
        )
    }.getOrNull()

    /** Raw column values, used to seed rows inside Room's database-creation callback. */
    fun toContentValues(): ContentValues = ContentValues().apply {
        if (id != 0L) put(SessionTable.ID, id)
        put(SessionTable.SPORT, sport)
        put(SessionTable.TITLE, title)
        put(SessionTable.LOCATION, location)
        put(SessionTable.DAY, day)
        put(SessionTable.TIME_SLOT, timeSlot)
        put(SessionTable.SKILL, skill)
        put(SessionTable.MAX_PLAYERS, maxPlayers)
        put(SessionTable.PLAYER_COUNT, playerCount)
        put(SessionTable.HOST_NAME, hostName)
        put(SessionTable.NOTES, notes)
        put(SessionTable.IS_HOSTING, isHosting)
        put(SessionTable.IS_JOINED, isJoined)
        put(SessionTable.LATITUDE, latitude)
        put(SessionTable.LONGITUDE, longitude)
        put(SessionTable.CREATED_AT, createdAt)
        put(SessionTable.REMOTE_ID, remoteId)
        put(SessionTable.OWNER_UID, ownerUid)
        put(SessionTable.SYNC_STATE, syncState)
        put(SessionTable.SYNCED_AT, syncedAt)
    }

    companion object {
        fun fromModel(s: PickupSession, createdAt: Long) = SessionEntity(
            id = s.id,
            sport = s.sport.name,
            title = s.title,
            location = s.location,
            day = s.day.name,
            timeSlot = s.time.name,
            skill = s.skill.name,
            maxPlayers = s.maxPlayers,
            playerCount = s.playerCount,
            hostName = s.hostName,
            notes = s.notes,
            isHosting = s.isHosting,
            isJoined = s.isJoined,
            latitude = s.latitude,
            longitude = s.longitude,
            createdAt = createdAt,
            remoteId = s.remoteId,
            ownerUid = s.ownerUid,
            syncState = s.syncState.name,
        )
    }
}
