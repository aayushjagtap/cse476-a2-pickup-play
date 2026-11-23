package com.aayushjagtap.pickupplay.data

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.aayushjagtap.pickupplay.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Activity-scoped store shared by every screen. Because it is a ViewModel it survives
 * configuration changes such as rotation.
 *
 * A4: sessions now live in the Room database (through [SessionRepository]) instead of an
 * in-memory list, so created sessions and join/leave choices survive the app being closed.
 * All database work runs off the main thread; the UI observes [sessions], which Room re-emits
 * after every write.
 */
class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository.from(application)

    private val _messages = MutableLiveData<OneShot<Int>>()
    /** One-time user-facing error messages (string resource ids), e.g. a failed save. */
    val messages: LiveData<OneShot<Int>> = _messages

    val sessions: LiveData<List<PickupSession>> = repository.sessions
        .catch { e ->
            // Database could not be opened or read: show an empty state plus a message
            // instead of crashing.
            Log.e(TAG, "Loading sessions failed", e)
            _messages.postValue(OneShot(R.string.error_db_load))
            emit(emptyList())
        }
        .asLiveData()

    // Ids with a join/leave write in progress, so a double tap cannot join and then leave.
    private val pendingJoins = mutableSetOf<Long>()

    // ---- "Nearest first" location state. Lives here so it survives rotation. ----

    private val _distanceMode = MutableLiveData(false)
    val distanceMode: LiveData<Boolean> = _distanceMode

    private val _userFix = MutableLiveData<GeoFix?>(null)
    /** Last location fix that passed the jump filter. */
    val userFix: LiveData<GeoFix?> = _userFix

    private val _locationStatus = MutableLiveData<LocationStatus>(LocationStatus.Off)
    val locationStatus: LiveData<LocationStatus> = _locationStatus

    private val jumpFilter = JumpFilter()

    // Keeps the "location moved and held steady" message visible long enough to read.
    private var confirmedJumpMeters: Double? = null
    private var confirmedJumpAtMillis = 0L

    fun setDistanceMode(on: Boolean) {
        _distanceMode.value = on
        if (!on) {
            jumpFilter.reset()
            confirmedJumpMeters = null
            _userFix.value = null
            _locationStatus.value = LocationStatus.Off
        }
    }

    fun setLocationStatus(status: LocationStatus) {
        _locationStatus.value = status
    }

    /** Entry point for raw fixes from the location API: filter, then publish. */
    fun onLocationFix(fix: GeoFix) {
        if (_distanceMode.value != true) return
        when (val decision = jumpFilter.offer(fix)) {
            is JumpFilter.Decision.Accepted -> {
                _userFix.value = fix
                if (decision.confirmedJumpMeters != null) {
                    confirmedJumpMeters = decision.confirmedJumpMeters
                    confirmedJumpAtMillis = SystemClock.elapsedRealtime()
                } else if (SystemClock.elapsedRealtime() - confirmedJumpAtMillis > JUMP_MESSAGE_MILLIS) {
                    confirmedJumpMeters = null
                }
                _locationStatus.value = LocationStatus.Active(fix.accuracyMeters, confirmedJumpMeters)
            }
            is JumpFilter.Decision.Rejected ->
                _locationStatus.value = LocationStatus.JumpIgnored(decision.jumpMeters)
        }
    }

    fun sessionsFor(sport: Sport): LiveData<List<PickupSession>> =
        sessions.map { list -> list.filter { it.sport == sport } }

    fun session(id: Long): LiveData<PickupSession?> =
        sessions.map { list -> list.firstOrNull { it.id == id } }

    fun openCount(sport: Sport): Int =
        sessions.value.orEmpty().count { it.sport == sport && !it.isFull }

    /**
     * Joins the session if there is room, or leaves it if the user already joined. The change is
     * written to the database; the UI updates when Room re-emits [sessions].
     */
    fun toggleJoin(id: Long) {
        if (!pendingJoins.add(id)) return
        viewModelScope.launch {
            try {
                repository.toggleJoin(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Join/leave failed for session $id", e)
                showMessage(R.string.error_db_join)
            } finally {
                pendingJoins.remove(id)
            }
        }
    }

    private fun showMessage(@StringRes message: Int) {
        _messages.value = OneShot(message)
    }

    private companion object {
        const val JUMP_MESSAGE_MILLIS = 10_000L
        const val TAG = "SessionsViewModel"
    }
}
