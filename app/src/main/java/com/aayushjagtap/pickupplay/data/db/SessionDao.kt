package com.aayushjagtap.pickupplay.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data access for pickup sessions. Every one-shot call is `suspend`, so Room runs it on its own
 * background executor; [observeAll] is a Flow that Room re-runs off the main thread after every
 * write to the table.
 */
@Dao
abstract class SessionDao {

    /** Newest created sessions first, then the seed sessions in their original order. */
    @Query("SELECT * FROM sessions ORDER BY created_at DESC, id ASC")
    abstract fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(session: SessionEntity): Long

    // ---- A4 Phase 2: cloud sync bookkeeping ----

    /** Sessions the user created that the server has not confirmed yet, oldest first. */
    @Query(
        "SELECT * FROM sessions WHERE sync_state IN ('PENDING', 'UPLOADING', 'FAILED') " +
            "ORDER BY created_at ASC"
    )
    abstract suspend fun getUnsynced(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE sync_state IN ('PENDING', 'UPLOADING', 'FAILED')")
    abstract fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM sessions WHERE remote_id = :remoteId")
    abstract suspend fun getByRemoteId(remoteId: String): SessionEntity?

    @Query("UPDATE sessions SET sync_state = :state WHERE id = :id")
    abstract suspend fun setSyncState(id: Long, state: String)

    @Query(
        "UPDATE sessions SET sync_state = 'SYNCED', owner_uid = :ownerUid, synced_at = :syncedAt " +
            "WHERE id = :id"
    )
    abstract suspend fun markSynced(id: Long, ownerUid: String, syncedAt: Long)

    /** The server already has this session (e.g. an earlier upload landed late): mark it synced. */
    @Query(
        "UPDATE sessions SET sync_state = 'SYNCED', synced_at = :syncedAt " +
            "WHERE remote_id = :remoteId AND sync_state != 'SYNCED'"
    )
    abstract suspend fun confirmByRemoteId(remoteId: String, syncedAt: Long): Int

    /** An upload interrupted by the app being killed is simply pending again. */
    @Query("UPDATE sessions SET sync_state = 'PENDING' WHERE sync_state = 'UPLOADING'")
    abstract suspend fun resetInterruptedUploads(): Int

    /** Inserts a downloaded session unless a row with the same remote id exists (returns -1). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(session: SessionEntity): Long

    /** Leaves a joined (not hosted) session. Returns the number of rows changed (0 or 1). */
    @Query(
        "UPDATE sessions SET is_joined = 0, player_count = player_count - 1 " +
            "WHERE id = :id AND is_joined = 1 AND is_hosting = 0"
    )
    abstract suspend fun leave(id: Long): Int

    /**
     * Joins a session only if it is not hosted, not already joined and still has room. The player
     * limit is enforced in the same SQL statement, so the count can never exceed the maximum.
     */
    @Query(
        "UPDATE sessions SET is_joined = 1, player_count = player_count + 1 " +
            "WHERE id = :id AND is_joined = 0 AND is_hosting = 0 AND player_count < max_players"
    )
    abstract suspend fun join(id: Long): Int

    /** Joins the session if there is room, or leaves it if the user already joined, atomically. */
    @Transaction
    open suspend fun toggleJoin(id: Long): JoinChange = when {
        leave(id) > 0 -> JoinChange.LEFT
        join(id) > 0 -> JoinChange.JOINED
        else -> JoinChange.UNCHANGED // hosting, full, or no such session
    }
}

enum class JoinChange { JOINED, LEFT, UNCHANGED }
