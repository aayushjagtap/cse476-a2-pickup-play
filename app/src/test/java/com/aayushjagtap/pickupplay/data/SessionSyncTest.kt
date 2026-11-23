package com.aayushjagtap.pickupplay.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aayushjagtap.pickupplay.data.cloud.AuthUser
import com.aayushjagtap.pickupplay.data.cloud.CloudTimeoutException
import com.aayushjagtap.pickupplay.data.cloud.RemoteSession
import com.aayushjagtap.pickupplay.data.cloud.SessionRemoteSource
import com.aayushjagtap.pickupplay.data.cloud.SessionSync
import com.aayushjagtap.pickupplay.data.db.AppDatabase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

/**
 * MOCKED tests: the sync logic against a real Room database and a FAKE in-memory remote store.
 * They do not contact Firebase. Real-backend behaviour (auth, rules, Source.SERVER) is covered by
 * the Firebase emulator tests in firebase-tests/ and ci/a4_cloud_check.py.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionSyncTest {

    /** In-memory stand-in for Firestore that can be told to fail or hang. */
    private class FakeRemote : SessionRemoteSource {
        val docs = linkedMapOf<String, RemoteSession>()
        var failUploads = false
        var hangUploads = false
        var failReads = false
        var uploadCalls = 0

        override suspend fun upload(session: RemoteSession) {
            uploadCalls++
            if (hangUploads) awaitCancellation()
            if (failUploads) throw IOException("backend unreachable")
            docs[session.remoteId] = session
        }

        override suspend fun fetchFromServer(): List<RemoteSession> {
            if (failReads) throw IOException("backend unreachable")
            return docs.values.toList()
        }

        override suspend fun probeServerRead() = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository
    private val remote = FakeRemote()
    private val alice = AuthUser("uid-alice", "alice@example.com", null)
    private val bob = AuthUser("uid-bob", "bob@example.com", null)

    private fun sync(remote: SessionRemoteSource = this.remote, uploadTimeoutMs: Long = 2_000) =
        SessionSync(db.sessionDao(), remote, ownHostLabel = "You", clock = { 42L }, uploadTimeoutMs = uploadTimeoutMs)

    @Before
    fun setUp() {
        db = AppDatabase.build(context, null) // in-memory, seeded with the 9 samples
        repo = SessionRepository(db.sessionDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun createLocal(title: String = "Evening run", sport: Sport = Sport.SOCCER): Long =
        repo.createSession(
            PickupSession(
                id = 0, sport = sport, title = title, location = "Munn Field", day = Day.TODAY,
                time = TimeSlot.EVENING, skill = SkillLevel.CASUAL, maxPlayers = 10, playerCount = 1,
                hostName = "You", notes = "", isHosting = true, isJoined = true,
                remoteId = UUID.randomUUID().toString(), syncState = SyncState.PENDING,
            )
        )

    @Test
    fun uploadMarksSyncedAndStoresOwnerUid() = runBlocking {
        val id = createLocal()
        val report = sync().uploadUnsynced(alice)

        assertEquals(1, report.uploaded)
        assertEquals(0, report.notUploaded)
        val row = repo.getSession(id)!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals("uid-alice", row.ownerUid)
        val doc = remote.docs.getValue(row.remoteId!!)
        assertEquals("uid-alice", doc.ownerUid)
        assertEquals("alice", doc.hostName) // email prefix, never the full address
        assertEquals("Evening run", doc.title)
    }

    @Test
    fun sessionOfAnotherAccountIsNeverUploadedUnderMine() = runBlocking {
        val alicesId = repo.createSession(
            PickupSession(
                id = 0, sport = Sport.SOCCER, title = "Alice's game", location = "Munn Field",
                day = Day.TODAY, time = TimeSlot.EVENING, skill = SkillLevel.CASUAL, maxPlayers = 10,
                playerCount = 1, hostName = "You", notes = "", isHosting = true, isJoined = true,
                remoteId = UUID.randomUUID().toString(), ownerUid = alice.uid, syncState = SyncState.FAILED,
            )
        )
        val signedOutId = createLocal("Made while signed out") // ownerUid null

        val report = sync().uploadUnsynced(bob)

        assertEquals(1, report.uploaded)          // only the signed-out one is adopted by Bob
        assertEquals(1, report.heldForOtherAccount)
        assertEquals(SyncState.FAILED, repo.getSession(alicesId)!!.syncState)
        assertEquals("uid-alice", repo.getSession(alicesId)!!.ownerUid)
        assertTrue(remote.docs.values.none { it.title == "Alice's game" })
        assertEquals("uid-bob", repo.getSession(signedOutId)!!.ownerUid)

        // When Alice signs in again, her session goes up under her own UID.
        assertEquals(1, sync().uploadUnsynced(alice).uploaded)
        assertEquals("uid-alice", remote.docs.values.single { it.title == "Alice's game" }.ownerUid)
    }

    @Test
    fun samplesAreNeverUploaded() = runBlocking {
        sync().uploadUnsynced(alice)
        assertEquals(0, remote.uploadCalls)
    }

    @Test
    fun failedUploadKeepsLocalDataAndReportsNotSynced() = runBlocking {
        val id = createLocal()
        remote.failUploads = true
        val report = sync().uploadUnsynced(alice)

        assertEquals(0, report.uploaded)
        assertEquals(1, report.notUploaded)
        assertTrue(report.error is IOException)
        val row = repo.getSession(id)!!
        assertEquals(SyncState.FAILED, row.syncState) // clearly not synced
        assertEquals("Evening run", row.title)        // and nothing lost
        assertEquals(10, repo.sessions.first().size)
    }

    @Test
    fun hangingUploadTimesOutInsteadOfSpinningForever() = runBlocking {
        val id = createLocal()
        remote.hangUploads = true
        val report = sync(uploadTimeoutMs = 200).uploadUnsynced(alice)

        assertTrue(report.error is CloudTimeoutException)
        assertEquals(SyncState.FAILED, repo.getSession(id)!!.syncState)
    }

    @Test
    fun failedSessionIsUploadedOnRetry() = runBlocking {
        val id = createLocal()
        remote.failUploads = true
        sync().uploadUnsynced(alice)
        remote.failUploads = false
        val report = sync().uploadUnsynced(alice)

        assertEquals(1, report.uploaded)
        assertEquals(SyncState.SYNCED, repo.getSession(id)!!.syncState)
    }

    @Test
    fun pullAddsRemoteSessionsOnceAndNeverDuplicates() = runBlocking {
        remote.docs["r-1"] = remoteDoc("r-1", bob.uid, "Bob's volleyball")
        remote.docs["r-2"] = remoteDoc("r-2", alice.uid, "Alice from another phone")

        val first = sync().pullFromServer(alice)
        val second = sync().pullFromServer(alice) // same server data again

        assertEquals(2, first.added)
        assertEquals(0, second.added)
        val all = repo.sessions.first()
        assertEquals(11, all.size) // 9 samples + 2 remote, no duplicates
        val bobs = all.single { it.remoteId == "r-1" }
        assertFalse(bobs.isHosting)
        assertEquals("bob", bobs.hostName)
        assertEquals(SyncState.SYNCED, bobs.syncState)
        val alices = all.single { it.remoteId == "r-2" }
        assertTrue(alices.isHosting) // the signed-in user's own session from another device
        assertEquals("You", alices.hostName)
    }

    @Test
    fun pullConfirmsLocalSessionTheServerAlreadyHas() = runBlocking {
        // Upload "timed out" locally but actually reached the server later.
        val id = createLocal()
        val row = repo.getSession(id)!!
        remote.docs[row.remoteId!!] = remoteDoc(row.remoteId!!, alice.uid, row.title)

        val report = sync().pullFromServer(alice)

        assertEquals(0, report.added)
        assertEquals(1, report.confirmed)
        assertEquals(SyncState.SYNCED, repo.getSession(id)!!.syncState)
        assertEquals(10, repo.sessions.first().size)
    }

    @Test
    fun pullKeepsLocalJoinStateOfRemoteSessions() = runBlocking {
        remote.docs["r-1"] = remoteDoc("r-1", bob.uid, "Bob's game")
        sync().pullFromServer(alice)
        val local = repo.sessions.first().single { it.remoteId == "r-1" }
        repo.toggleJoin(local.id)

        sync().pullFromServer(alice)

        val after = repo.getSession(local.id)!!
        assertTrue(after.isJoined)
        assertEquals(2, after.playerCount)
    }

    @Test
    fun failedServerReadThrowsAndChangesNothing() = runBlocking {
        remote.failReads = true
        try {
            sync().pullFromServer(alice)
            fail("expected the read to fail")
        } catch (expected: IOException) {
        }
        assertEquals(9, repo.sessions.first().size)
    }

    @Test
    fun malformedServerDocumentsAreSkipped() {
        assertNull(RemoteSession.fromFields("x", mapOf("ownerUid" to "u", "sport" to "CURLING")))
        assertNull(RemoteSession.fromFields("x", remoteDoc("x", "u", "t").toFields() + ("maxPlayers" to 99L)))
        assertEquals("t", RemoteSession.fromFields("x", remoteDoc("x", "u", "t").toFields())?.title)
    }

    private fun remoteDoc(id: String, owner: String, title: String) = RemoteSession(
        remoteId = id, ownerUid = owner, sport = "VOLLEYBALL", title = title, location = "IM West",
        day = "SATURDAY", timeSlot = "MORNING", skill = "CASUAL", maxPlayers = 12, playerCount = 1,
        hostName = owner.removePrefix("uid-"), notes = "", createdAt = 1_000L,
    )
}
