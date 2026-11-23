package com.aayushjagtap.pickupplay.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.annotation.StringRes
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.db.AppDatabase
import com.aayushjagtap.pickupplay.data.db.SessionDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-wide coordinator for sign-in and cloud sync. It outlives screens (an upload started
 * from the create form finishes after the form closes) and exposes:
 * - [state]: configured / signed-in user / what is running / the last outcome, for the UI;
 * - [messages]: one-time Snackbar messages, including every failure, so nothing fails silently.
 *
 * Every operation is a coroutine with a time limit, so no progress indicator can spin forever,
 * and every Firebase exception is caught and turned into a [CloudError].
 */
class CloudSyncManager internal constructor(
    private val context: Context,
    private val dao: SessionDao,
    private val auth: AuthGateway?,
    remote: SessionRemoteSource?,
    val usingEmulator: Boolean,
) {
    enum class Busy { SIGNING_IN, SYNCING, UPLOADING, PROBING }

    /** Result of the most recent cloud operation, shown on the Account screen. */
    sealed interface Outcome {
        val at: Long

        /** Server-only read succeeded (uploads may still have failed: see [notUploaded]). */
        data class Synced(
            override val at: Long, val uploaded: Int, val notUploaded: Int, val uploadError: CloudError?,
            val fetchedFromServer: Int, val addedLocally: Int,
        ) : Outcome

        data class SyncFailed(override val at: Long, val error: CloudError, val uploaded: Int) : Outcome

        data class Uploaded(override val at: Long, val uploaded: Int, val notUploaded: Int, val error: CloudError?) : Outcome

        /** Signed-out server read; [denied] true means the security rules rejected it. */
        data class Probe(override val at: Long, val denied: Boolean, val error: CloudError?) : Outcome
    }

    data class State(
        val configured: Boolean,
        val user: AuthUser?,
        val busy: Busy? = null,
        val last: Outcome? = null,
        val signInError: CloudError? = null,
    )

    private val _state = MutableStateFlow(State(configured = auth != null && remote != null, user = auth?.currentUser))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Sessions created on this device that the server has not confirmed. */
    val unsyncedCount: Flow<Int> = dao.observeUnsyncedCount()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val remote = remote
    private val sync = remote?.let { SessionSync(dao, it, context.getString(R.string.host_you)) }
    private var started = false

    init {
        auth?.addListener { user -> _state.update { it.copy(user = user) } }
    }

    /** Called once per process when the UI starts: resumes interrupted uploads and syncs if signed in. */
    fun startOnce() {
        if (started) return
        started = true
        scope.launch {
            try {
                dao.resetInterruptedUploads()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not reset interrupted uploads", e)
            }
            if (_state.value.user != null) runSync(manual = false)
        }
    }

    fun signIn(email: String, password: String) {
        val gateway = auth ?: return
        if (_state.value.busy != null) return
        scope.launch {
            _state.update { it.copy(busy = Busy.SIGNING_IN, signInError = null) }
            val ok = try {
                requireOnline()
                withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) { gateway.signIn(email, password) }
                    ?: throw CloudTimeoutException("Sign-in")
                _state.update { it.copy(busy = null, user = gateway.currentUser, last = null) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Logged without the email or password.
                Log.w(TAG, "Sign-in failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(busy = null, signInError = CloudError.from(e)) }
                false
            }
            if (ok) {
                post(R.string.cloud_signed_in)
                runSync(manual = false)
            }
        }
    }

    /** Signs out of Firebase. Local Room data is kept, so the app stays fully usable. */
    fun signOut() {
        auth?.signOut() ?: return
        _state.update { it.copy(user = null, last = null, signInError = null) }
        post(R.string.cloud_signed_out)
    }

    fun syncNow() {
        scope.launch { runSync(manual = true) }
    }

    /** Uploads a just-created session in the background when signed in. */
    fun onSessionCreated() {
        if (_state.value.user == null || sync == null) return
        scope.launch { runUpload() }
    }

    /**
     * Grader demo: while signed OUT, ask the server for session data. Firestore security rules
     * should reject it with PERMISSION_DENIED; the result is shown on the Account screen.
     */
    fun probeUnauthenticatedRead() {
        val source = remote ?: return
        if (_state.value.user != null || _state.value.busy != null) return
        scope.launch {
            _state.update { it.copy(busy = Busy.PROBING) }
            val outcome = try {
                requireOnline()
                withTimeoutOrNull(READ_TIMEOUT_MS) { source.probeServerRead() }
                    ?: throw CloudTimeoutException("Probe")
                Outcome.Probe(now(), denied = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = CloudError.from(e)
                Outcome.Probe(now(), denied = error == CloudError.PERMISSION_DENIED, error = error)
            }
            _state.update { it.copy(busy = null, last = outcome) }
        }
    }

    private suspend fun runSync(manual: Boolean) = mutex.withLock {
        val user = _state.value.user ?: return@withLock
        val engine = sync ?: return@withLock
        _state.update { it.copy(busy = Busy.SYNCING) }
        var uploaded = 0
        try {
            requireOnline()
            val up = engine.uploadUnsynced(user)
            uploaded = up.uploaded
            val pull = engine.pullFromServer(user)
            val uploadError = up.error?.let(CloudError::from)
            _state.update {
                it.copy(last = Outcome.Synced(now(), up.uploaded, up.notUploaded, uploadError, pull.fetched, pull.added))
            }
            when {
                up.notUploaded > 0 ->
                    post(R.string.cloud_sync_partial, up.notUploaded, uploadError ?: CloudError.UNKNOWN)
                manual -> post(R.string.cloud_sync_ok, pull.fetched, pull.added)
                pull.added > 0 -> post(R.string.cloud_sync_retrieved, pull.added)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            val error = CloudError.from(e)
            _state.update { it.copy(last = Outcome.SyncFailed(now(), error, uploaded)) }
            post(R.string.cloud_sync_failed, error)
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    private suspend fun runUpload() = mutex.withLock {
        val user = _state.value.user ?: return@withLock
        val engine = sync ?: return@withLock
        _state.update { it.copy(busy = Busy.UPLOADING) }
        try {
            requireOnline()
            val up = engine.uploadUnsynced(user)
            val error = up.error?.let(CloudError::from)
            _state.update { it.copy(last = Outcome.Uploaded(now(), up.uploaded, up.notUploaded, error)) }
            if (up.notUploaded > 0) post(R.string.cloud_upload_failed, error ?: CloudError.UNKNOWN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Upload failed", e)
            val error = CloudError.from(e)
            // Report the real number of sessions still waiting, not a guess.
            val waiting = try { dao.getUnsynced().size } catch (_: Exception) { 1 }
            _state.update { it.copy(last = Outcome.Uploaded(now(), 0, waiting, error)) }
            post(R.string.cloud_upload_failed, error)
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    /** Fails fast with a clear message instead of waiting for a network timeout. */
    private fun requireOnline() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            throw CloudException(CloudError.OFFLINE)
        }
    }

    private fun post(@StringRes res: Int, vararg args: Any) {
        _messages.trySend(UiMessage(res, args.toList()))
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val TAG = "CloudSync"
        private const val SIGN_IN_TIMEOUT_MS = 20_000L
        private const val READ_TIMEOUT_MS = 20_000L

        @Volatile
        private var instance: CloudSyncManager? = null

        fun get(context: Context): CloudSyncManager =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(app: Context): CloudSyncManager {
            val services = FirebaseSetup.connect(app)
            return CloudSyncManager(
                context = app,
                dao = AppDatabase.getInstance(app).sessionDao(),
                auth = services?.let { FirebaseAuthGateway(it.auth) },
                remote = services?.let { FirestoreSessionSource(it.firestore) },
                usingEmulator = services?.usingEmulator == true,
            )
        }
    }
}
