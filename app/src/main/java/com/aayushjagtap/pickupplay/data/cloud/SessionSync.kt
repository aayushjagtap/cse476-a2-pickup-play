package com.aayushjagtap.pickupplay.data.cloud

import com.aayushjagtap.pickupplay.data.SyncState
import com.aayushjagtap.pickupplay.data.db.SessionDao
import com.aayushjagtap.pickupplay.data.db.SessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Moves sessions between Room and the remote store. Room stays the source of truth for the UI:
 * uploads never delete or rewrite local data, and downloads are merged by remote id so the same
 * session can never appear twice.
 */
class SessionSync(
    private val dao: SessionDao,
    private val remote: SessionRemoteSource,
    /** Host label stored locally for the signed-in user's own sessions ("You"). */
    private val ownHostLabel: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val uploadTimeoutMs: Long = 15_000,
    private val readTimeoutMs: Long = 20_000,
) {

    /**
     * [notUploaded] counts this account's sessions that are still not on the server;
     * [heldForOtherAccount] counts sessions created under a different signed-in account, which are
     * never uploaded under this one (they stay saved locally and marked not synced).
     */
    data class UploadReport(
        val uploaded: Int, val notUploaded: Int, val error: Throwable?, val heldForOtherAccount: Int = 0,
    )
    data class PullReport(val fetched: Int, val added: Int, val confirmed: Int)

    /**
     * Uploads every session the server has not confirmed. On the first failure it stops (the
     * network is most likely down, so waiting on the rest would only delay feedback); everything
     * not uploaded stays saved locally and marked as not synced.
     */
    suspend fun uploadUnsynced(user: AuthUser): UploadReport {
        val all = dao.getUnsynced()
        // Created while signed out (no owner yet) or by this account. A session created under
        // another account is never re-attributed to this one.
        val rows = all.filter { it.ownerUid == null || it.ownerUid == user.uid }
        val held = all.size - rows.size
        var uploaded = 0
        for ((index, row) in rows.withIndex()) {
            val remoteId = row.remoteId ?: continue
            dao.setSyncState(row.id, SyncState.UPLOADING.name)
            try {
                withTimeoutOrNull(uploadTimeoutMs) { remote.upload(row.toRemote(remoteId, user)) }
                    ?: throw CloudTimeoutException("Upload")
                dao.markSynced(row.id, user.uid, clock())
                uploaded++
            } catch (e: CancellationException) {
                throw e // row is reset from UPLOADING to PENDING on the next start
            } catch (e: Exception) {
                dao.setSyncState(row.id, SyncState.FAILED.name)
                return UploadReport(uploaded, rows.size - index, e, held)
            }
        }
        return UploadReport(uploaded, 0, null, held)
    }

    /**
     * Server-only read of the shared sessions, merged into Room: new documents are inserted,
     * documents already present locally are only confirmed as synced (local join state is kept).
     */
    suspend fun pullFromServer(user: AuthUser): PullReport {
        val docs = withTimeoutOrNull(readTimeoutMs) { remote.fetchFromServer() }
            ?: throw CloudTimeoutException("Server read")
        val now = clock()
        var added = 0
        var confirmed = 0
        for (doc in docs) {
            if (dao.confirmByRemoteId(doc.remoteId, now) > 0) {
                confirmed++
                continue
            }
            if (dao.getByRemoteId(doc.remoteId) != null) continue // already synced locally
            val mine = doc.ownerUid == user.uid
            val inserted = dao.insertIfAbsent(
                SessionEntity(
                    sport = doc.sport,
                    title = doc.title,
                    location = doc.location,
                    day = doc.day,
                    timeSlot = doc.timeSlot,
                    skill = doc.skill,
                    maxPlayers = doc.maxPlayers,
                    playerCount = doc.playerCount,
                    hostName = if (mine) ownHostLabel else doc.hostName,
                    notes = doc.notes,
                    isHosting = mine,
                    isJoined = mine,
                    latitude = null,
                    longitude = null,
                    createdAt = doc.createdAt,
                    remoteId = doc.remoteId,
                    ownerUid = doc.ownerUid,
                    syncState = SyncState.SYNCED.name,
                    syncedAt = now,
                )
            )
            if (inserted != -1L) added++
        }
        return PullReport(docs.size, added, confirmed)
    }

    private fun SessionEntity.toRemote(remoteId: String, user: AuthUser) = RemoteSession(
        remoteId = remoteId,
        // The server rules only accept the signed-in user's own UID as owner.
        ownerUid = user.uid,
        sport = sport,
        title = title.take(RemoteSession.MAX_TITLE),
        location = location.take(RemoteSession.MAX_LOCATION),
        day = day,
        timeSlot = timeSlot,
        skill = skill,
        maxPlayers = maxPlayers,
        playerCount = playerCount.coerceIn(1, maxPlayers),
        hostName = user.hostName,
        notes = notes.take(RemoteSession.MAX_NOTES),
        createdAt = createdAt,
    )
}
