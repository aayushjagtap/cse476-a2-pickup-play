package com.aayushjagtap.pickupplay.data.cloud

import com.aayushjagtap.pickupplay.data.Day
import com.aayushjagtap.pickupplay.data.SkillLevel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.data.TimeSlot

/**
 * One document in the Firestore `sessions` collection (document id = [remoteId]).
 * Field names and limits must match `firestore.rules`, which validates every write.
 */
data class RemoteSession(
    val remoteId: String,
    val ownerUid: String,
    val sport: String,
    val title: String,
    val location: String,
    val day: String,
    val timeSlot: String,
    val skill: String,
    val maxPlayers: Int,
    val playerCount: Int,
    val hostName: String,
    val notes: String,
    /** Client creation time in epoch millis (orders the list). */
    val createdAt: Long,
) {
    /** Firestore fields, except `serverCreatedAt`, which the data source adds as a server timestamp. */
    fun toFields(): Map<String, Any> = mapOf(
        F_OWNER_UID to ownerUid,
        F_SPORT to sport,
        F_TITLE to title,
        F_LOCATION to location,
        F_DAY to day,
        F_TIME_SLOT to timeSlot,
        F_SKILL to skill,
        F_MAX_PLAYERS to maxPlayers.toLong(),
        F_PLAYER_COUNT to playerCount.toLong(),
        F_HOST_NAME to hostName,
        F_NOTES to notes,
        F_CREATED_AT to createdAt,
        F_SCHEMA to SCHEMA_VERSION,
    )

    companion object {
        const val COLLECTION = "sessions"
        const val SCHEMA_VERSION = 1L

        const val F_OWNER_UID = "ownerUid"
        const val F_SPORT = "sport"
        const val F_TITLE = "title"
        const val F_LOCATION = "location"
        const val F_DAY = "day"
        const val F_TIME_SLOT = "timeSlot"
        const val F_SKILL = "skill"
        const val F_MAX_PLAYERS = "maxPlayers"
        const val F_PLAYER_COUNT = "playerCount"
        const val F_HOST_NAME = "hostName"
        const val F_NOTES = "notes"
        const val F_CREATED_AT = "createdAt"
        const val F_SERVER_CREATED_AT = "serverCreatedAt"
        const val F_SCHEMA = "schemaVersion"

        // Same limits as the create form and firestore.rules.
        const val MAX_TITLE = 60
        const val MAX_LOCATION = 80
        const val MAX_NOTES = 300
        const val MAX_HOST_NAME = 40

        /**
         * Parses a downloaded document defensively: anything malformed (or from a future schema
         * the app cannot show) returns null and is skipped instead of crashing the sync.
         */
        fun fromFields(id: String, f: Map<String, Any?>): RemoteSession? = runCatching {
            val s = RemoteSession(
                remoteId = id,
                ownerUid = f[F_OWNER_UID] as String,
                sport = (f[F_SPORT] as String).also { Sport.valueOf(it) },
                title = f[F_TITLE] as String,
                location = f[F_LOCATION] as String,
                day = (f[F_DAY] as String).also { Day.valueOf(it) },
                timeSlot = (f[F_TIME_SLOT] as String).also { TimeSlot.valueOf(it) },
                skill = (f[F_SKILL] as String).also { SkillLevel.valueOf(it) },
                maxPlayers = (f[F_MAX_PLAYERS] as Number).toInt(),
                playerCount = (f[F_PLAYER_COUNT] as Number).toInt(),
                hostName = f[F_HOST_NAME] as String,
                notes = (f[F_NOTES] as String?).orEmpty(),
                createdAt = (f[F_CREATED_AT] as Number).toLong(),
            )
            s.takeIf { it.maxPlayers in 2..30 && it.playerCount in 0..it.maxPlayers }
        }.getOrNull()
    }
}

/** The signed-in Firebase user, reduced to what the app needs. */
data class AuthUser(val uid: String, val email: String?, val displayName: String?) {
    /** Name shown to other players as the host. Never the full email address. */
    val hostName: String
        get() = (displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "Host")
            .take(RemoteSession.MAX_HOST_NAME)
}

/** Remote storage for sessions. Implemented by Firestore; faked in unit tests. */
interface SessionRemoteSource {
    /** Creates or overwrites the document for [session]. Completes only when the server acks. */
    suspend fun upload(session: RemoteSession)

    /** Reads sessions straight from the server (never from a local cache). */
    suspend fun fetchFromServer(): List<RemoteSession>

    /** Deliberately unauthenticated-style probe: a server read whose result shows rule enforcement. */
    suspend fun probeServerRead()
}

/** Firebase Authentication, reduced to what the app needs. */
interface AuthGateway {
    val currentUser: AuthUser?
    fun addListener(listener: (AuthUser?) -> Unit)
    suspend fun signIn(email: String, password: String): AuthUser
    fun signOut()
}
