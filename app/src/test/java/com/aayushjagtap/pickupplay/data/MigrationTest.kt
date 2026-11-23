package com.aayushjagtap.pickupplay.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aayushjagtap.pickupplay.data.db.AppDatabase
import com.aayushjagtap.pickupplay.data.db.SeedSessions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Database version 1 (A4 Phase 1) -> 2 (Phase 2). Builds a real version-1 file from the exported
 * v1 schema, fills it like a Phase 1 install would be, then opens it with the current Room
 * database. Room validates the migrated schema against the v2 entities, so a wrong migration
 * fails here instead of crashing users.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun createVersion1File() {
        val schema = JSONObject(File(V1_SCHEMA).readText()).getJSONObject("database")
        val entity = schema.getJSONArray("entities").getJSONObject(0)
        val table = entity.getString("tableName")
        val file = context.getDatabasePath(DB_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.getJSONArray("indices")
            for (i in 0 until indices.length()) {
                sql.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))

            // Phase 1 contents: the 9 samples (one joined) plus one session the user created.
            SeedSessions.entities(context).forEach { e ->
                sql.insert(table, null, v1Row(e.id, e.title, e.sport, e.maxPlayers,
                    if (e.id == 2L) e.playerCount + 1 else e.playerCount,
                    hosting = false, joined = e.id == 2L, createdAt = 0))
            }
            sql.insert(table, null, v1Row(10, "Old local game", "TENNIS", 4, 1,
                hosting = true, joined = true, createdAt = 1_700_000_000_000))
            sql.version = 1
        }
    }

    private fun v1Row(
        id: Long, title: String, sport: String, max: Int, count: Int,
        hosting: Boolean, joined: Boolean, createdAt: Long,
    ) = ContentValues().apply {
        put("id", id); put("sport", sport); put("title", title); put("location", "Somewhere")
        put("day", "TODAY"); put("time_slot", "EVENING"); put("skill", "CASUAL")
        put("max_players", max); put("player_count", count); put("host_name", "Host")
        put("notes", ""); put("is_hosting", hosting); put("is_joined", joined)
        put("created_at", createdAt)
    }

    @Test
    fun migrationKeepsEveryRowAndQueuesUserSessionsForUpload() = runBlocking {
        createVersion1File()

        val opened = AppDatabase.build(context, DB_NAME).also { db = it }
        val repo = SessionRepository(opened.sessionDao())
        val all = repo.sessions.first()

        // Nothing deleted, nothing re-seeded or duplicated.
        assertEquals(10, all.size)
        assertEquals(10, all.map { it.id }.toSet().size)

        // The user's Phase 1 session survives with a new UUID and is waiting to be uploaded.
        val mine = repo.getSession(10)!!
        assertEquals("Old local game", mine.title)
        assertTrue(mine.isHosting)
        assertEquals(SyncState.PENDING, mine.syncState)
        assertNotNull(mine.remoteId)
        assertTrue(mine.remoteId!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        assertNull(mine.ownerUid)

        // Samples stay local-only, and the Phase 1 join is preserved.
        val joined = repo.getSession(2)!!
        assertTrue(joined.isJoined)
        assertEquals(4, joined.playerCount)
        all.filter { it.id <= 9 }.forEach {
            assertEquals(SyncState.LOCAL, it.syncState)
            assertNull(it.remoteId)
        }
        assertEquals(1, opened.sessionDao().getUnsynced().size)
    }

    @Test
    fun migratedRemoteIdsAreUnique() = runBlocking {
        createVersion1File()
        // Add a second user-created session so two UUIDs are generated by the same UPDATE.
        SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.insert("sessions", null, v1Row(11, "Another", "SOCCER", 10, 1, true, true, 1_700_000_000_001))
        }
        val opened = AppDatabase.build(context, DB_NAME).also { db = it }
        val ids = opened.sessionDao().getUnsynced().map { it.remoteId }
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        // Unit tests run with the app module as the working directory.
        const val V1_SCHEMA = "schemas/com.aayushjagtap.pickupplay.data.db.AppDatabase/1.json"
    }
}
