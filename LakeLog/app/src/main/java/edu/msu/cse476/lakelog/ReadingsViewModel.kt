package edu.msu.cse476.lakelog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Holds the download state so a rotation doesn't restart or lose a download.
 * Work runs in viewModelScope on Dispatchers.IO (inside NoaaClient); when the Activity
 * finishes, the scope is cancelled, so leaving mid-download can't touch a dead screen.
 */
class ReadingsViewModel : ViewModel() {

    sealed interface State {
        data object Idle : State
        data class Loading(val previous: List<Reading>) : State
        data class Loaded(val readings: List<Reading>) : State
        data class Failed(val message: String, val previous: List<Reading>) : State
    }

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    private var job: Job? = null

    /** Downloads when the screen first opens (not again after a rotation). */
    fun loadIfNeeded() {
        if (_state.value == State.Idle) load()
    }

    /** Refresh and Retry: start a new download, cancelling one already in flight. */
    fun load() {
        job?.cancel()
        val previous = currentReadings()
        _state.value = State.Loading(previous)
        job = viewModelScope.launch {
            _state.value = try {
                State.Loaded(NoaaClient.downloadReadings())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                State.Failed(e.message.orEmpty(), previous)
            } catch (e: RuntimeException) {
                State.Failed(e.message.orEmpty(), previous)
            }
        }
    }

    private fun currentReadings(): List<Reading> = when (val s = _state.value) {
        is State.Loaded -> s.readings
        is State.Loading -> s.previous
        is State.Failed -> s.previous
        else -> emptyList()
    }
}
