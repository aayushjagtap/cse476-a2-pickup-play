package com.aayushjagtap.pickupplay.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aayushjagtap.pickupplay.data.db.AppDatabase
import com.aayushjagtap.pickupplay.data.db.JoinChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Persistence tests against a real on-disk SQLite file. "Restarting the app" is simulated by
 * closing the database and opening the same file again with a fresh Room instance.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionPersistenceTest {

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

    /** Closes any open instance and opens the database file again, like a cold app start. */
    private fun reopen(): SessionRepository {
        db?.close()
        val fresh = AppDatabase.build(context, DB_NAME)
        db = fresh
        return SessionRepository(fresh.sessionDao())
    }

    private fun newSession(sport: Sport = Sport.SOCCER, max: Int = 10) = PickupSession(
        id = 0, sport = sport, title = "Test game", location = "Test field",
        day = Day.SATURDAY, time = TimeSlot.MORNING, skill = SkillLevel.CASUAL,
        maxPlayers = max, playerCount = 1, hostName = "You", notes = "",
        isHosting = true, isJoined = true,
    )

    @Test
    fun firstLaunchSeedsSampleSessionsOnce() = runBlocking {
        val seeds = reopen().sessions.first()
        assertEquals(9, seeds.size)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L), seeds.map { it.id }.toSet())

        // Several more "launches": the samples are neither duplicated nor reset.
        repeat(3) { assertEquals(9, reopen().sessions.first().size) }
    }

    @Test
    fun createdSessionSurvivesRestart() = runBlocking {
        val id = reopen().createSession(newSession(Sport.TENNIS))

        val after = reopen()
        val restored = after.getSession(id)
        assertNotNull(restored)
        assertEquals("Test game", restored!!.title)
        assertEquals(Sport.TENNIS, restored.sport)
        assertTrue(restored.isHosting)
        assertTrue(restored.isJoined)
        assertEquals(1, restored.playerCount)

        val all = after.sessions.first()
        assertEquals(10, all.size) // 9 samples + 1, no duplicates
        assertEquals(id, all.first().id) // newest created session is listed first
    }

    @Test
    fun joinAndLeaveSurviveRestart() = runBlocking {
        // Sample 2 (basketball): 3 of 8 players, not joined.
        assertEquals(JoinChange.JOINED, reopen().toggleJoin(2))

        var s = reopen().getSession(2)!!
        assertTrue(s.isJoined)
        assertEquals(4, s.playerCount)

        assertEquals(JoinChange.LEFT, reopen().toggleJoin(2))
        s = reopen().getSession(2)!!
        assertFalse(s.isJoined)
        assertEquals(3, s.playerCount)
    }

    @Test
    fun playerLimitAndHostingRulesAreEnforced() = runBlocking {
        val repo = reopen()
        // Sample 3 is full (10 of 10): joining does nothing.
        assertEquals(JoinChange.UNCHANGED, repo.toggleJoin(3))
        assertEquals(10, repo.getSession(3)!!.playerCount)

        // The host cannot leave their own session.
        val hosted = repo.createSession(newSession())
        assertEquals(JoinChange.UNCHANGED, repo.toggleJoin(hosted))
        assertTrue(repo.getSession(hosted)!!.isJoined)

        // Filling the last spot of a 2-player game, then nobody else can push it past the max.
        val small = repo.createSession(newSession(max = 2).copy(isHosting = false, isJoined = false))
        assertEquals(JoinChange.JOINED, repo.toggleJoin(small))
        assertEquals(2, repo.getSession(small)!!.playerCount)
        assertTrue(repo.getSession(small)!!.isFull)
    }

    private companion object {
        const val DB_NAME = "persistence-test.db"
    }
}
