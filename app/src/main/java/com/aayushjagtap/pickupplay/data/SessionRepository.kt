package com.aayushjagtap.pickupplay.data

import android.content.Context
import com.aayushjagtap.pickupplay.data.db.AppDatabase
import com.aayushjagtap.pickupplay.data.db.JoinChange
import com.aayushjagtap.pickupplay.data.db.SessionDao
import com.aayushjagtap.pickupplay.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for pickup sessions. ViewModels talk to this class, never to Room
 * directly, so the storage can change (e.g. a remote backend later) without touching the UI.
 */
class SessionRepository(
    private val dao: SessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Every session, re-emitted after each change. Unreadable rows are skipped, not fatal. */
    val sessions: Flow<List<PickupSession>> =
        dao.observeAll().map { rows -> rows.mapNotNull(SessionEntity::toModel) }

    suspend fun getSession(id: Long): PickupSession? = dao.getById(id)?.toModel()

    /** Saves a session the user is hosting and returns its new id. The [session] id is ignored. */
    suspend fun createSession(session: PickupSession): Long =
        dao.insert(SessionEntity.fromModel(session.copy(id = 0L), createdAt = clock()))

    /** Joins or leaves, respecting the player limit and hosting rules. */
    suspend fun toggleJoin(id: Long): JoinChange = dao.toggleJoin(id)

    companion object {
        fun from(context: Context) =
            SessionRepository(AppDatabase.getInstance(context).sessionDao())
    }
}
