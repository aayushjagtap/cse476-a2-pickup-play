package com.aayushjagtap.pickupplay.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/**
 * Holds the create-form selections in a SavedStateHandle so the chosen sport, day, time and
 * skill level survive rotation (and process death). Typed text is preserved by the
 * EditText views' own saved state.
 */
class CreateSessionViewModel(private val state: SavedStateHandle) : ViewModel() {

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

    private companion object {
        const val KEY_SPORT = "sport"
        const val KEY_DAY = "day"
        const val KEY_TIME = "time"
        const val KEY_SKILL = "skill"
    }
}
