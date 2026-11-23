package com.aayushjagtap.pickupplay.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.cloud.CloudSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Holds the create-form selections in a SavedStateHandle so the chosen sport, day, time and
 * skill level survive rotation (and process death). Typed text is preserved by the
 * EditText views' own saved state.
 *
 * A4: also saves the new session to the database in the background and reports the outcome
 * through [saveState]. Because this ViewModel survives rotation, a save that finishes while the
 * screen is rotating is still delivered to the recreated fragment.
 * A4 Phase 2: each new session gets a stable UUID shared with its Firestore document and starts
 * as not synced; once it is safely in Room, the cloud upload runs in the background.
 */
class CreateSessionViewModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = SessionRepository.from(application)

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data class Saved(val id: Long, val sport: Sport) : SaveState
        data object Failed : SaveState
    }

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    var sport: Sport?
        get() = state.get<String>(KEY_SPORT)?.let { Sport.valueOf(it) }
        set(value) { state[KEY_SPORT] = value?.name }

    var day: Day?
        get() = state.get<String>(KEY_DAY)?.let { Day.valueOf(it) }
        set(value) { state[KEY_DAY] = value?.name }

    var time: TimeSlot?
        get() = state.get<String>(KEY_TIME)?.let { TimeSlot.valueOf(it) }
        set(value) { state[KEY_TIME] = value?.name }

    var skill: SkillLevel?
        get() = state.get<String>(KEY_SKILL)?.let { SkillLevel.valueOf(it) }
        set(value) { state[KEY_SKILL] = value?.name }

    /**
     * Seeds the sport from the screen the user came from plus sensible defaults, only the
     * first time the form opens. After that, the saved selections win.
     */
    fun initDefaults(fromArgs: Sport) {
        if (!state.contains(KEY_SPORT)) sport = fromArgs
        if (!state.contains(KEY_DAY)) day = Day.TODAY
        if (!state.contains(KEY_TIME)) time = TimeSlot.EVENING
        if (!state.contains(KEY_SKILL)) skill = SkillLevel.CASUAL
    }

    /** Saves a validated session the user is hosting. Ignored while a save is in progress. */
    fun save(title: String, location: String, maxPlayers: Int, notes: String) {
        val current = _saveState.value
        if (current == SaveState.Saving || current is SaveState.Saved) return
        val chosenSport = sport ?: Sport.BASKETBALL
        val cloud = CloudSyncManager.get(getApplication())
        val session = PickupSession(
            id = 0L,
            sport = chosenSport,
            title = title,
            location = location,
            day = day ?: Day.TODAY,
            time = time ?: TimeSlot.EVENING,
            skill = skill ?: SkillLevel.CASUAL,
            maxPlayers = maxPlayers,
            playerCount = 1,
            hostName = getApplication<Application>().getString(R.string.host_you),
            notes = notes,
            isHosting = true,
            isJoined = true,
            remoteId = UUID.randomUUID().toString(),
            // Owned by the signed-in account at creation (null when signed out), so it can only
            // ever be uploaded under that account.
            ownerUid = cloud.state.value.user?.uid,
            syncState = SyncState.PENDING,
        )
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                val id = repository.createSession(session)
                // Saved locally first; the upload (if signed in) cannot lose the session.
                cloud.onSessionCreated()
                SaveState.Saved(id, chosenSport)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving the new session failed", e)
                SaveState.Failed
            }
        }
    }

    /** Called after a failure message was shown, so the user can try again. */
    fun onFailureShown() {
        if (_saveState.value == SaveState.Failed) _saveState.value = SaveState.Idle
    }

    private companion object {
        const val TAG = "CreateSessionViewModel"
        const val KEY_SPORT = "sport"
        const val KEY_DAY = "day"
        const val KEY_TIME = "time"
        const val KEY_SKILL = "skill"
    }
}
