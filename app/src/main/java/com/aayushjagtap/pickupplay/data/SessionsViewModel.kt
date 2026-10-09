package com.aayushjagtap.pickupplay.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.aayushjagtap.pickupplay.R

/**
 * Activity-scoped store shared by every screen. Because it is a ViewModel it survives
 * configuration changes such as rotation, so created and joined sessions are not lost.
 */
class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val _sessions = MutableLiveData(seedSessions())
    val sessions: LiveData<List<PickupSession>> = _sessions

    private var nextId = (_sessions.value?.maxOf { it.id } ?: 0L) + 1

    fun sessionsFor(sport: Sport): LiveData<List<PickupSession>> =
        sessions.map { list -> list.filter { it.sport == sport } }

    fun session(id: Long): LiveData<PickupSession?> =
        sessions.map { list -> list.firstOrNull { it.id == id } }

    fun openCount(sport: Sport): Int =
        _sessions.value.orEmpty().count { it.sport == sport && !it.isFull }

    /** Joins the session if there is room, or leaves it if the user already joined. */
    fun toggleJoin(id: Long) {
        _sessions.value = _sessions.value.orEmpty().map { s ->
            when {
                s.id != id || s.isHosting -> s
                s.isJoined -> s.copy(isJoined = false, playerCount = s.playerCount - 1)
                !s.isFull -> s.copy(isJoined = true, playerCount = s.playerCount + 1)
                else -> s
            }
        }
    }

    /** Adds a session hosted by the user to the top of the list and returns its id. */
    fun createSession(
        sport: Sport,
        title: String,
        location: String,
        day: Day,
        time: TimeSlot,
        skill: SkillLevel,
        maxPlayers: Int,
        notes: String,
    ): Long {
        val id = nextId++
        val created = PickupSession(
            id = id,
            sport = sport,
            title = title,
            location = location,
            day = day,
            time = time,
            skill = skill,
            maxPlayers = maxPlayers,
            playerCount = 1,
            hostName = getApplication<Application>().getString(R.string.host_you),
            notes = notes,
            isHosting = true,
            isJoined = true,
        )
        _sessions.value = listOf(created) + _sessions.value.orEmpty()
        return id
    }

    private fun seedSessions(): List<PickupSession> {
        val app = getApplication<Application>()
        fun s(id: Long, sport: Sport, title: Int, location: Int, day: Day, time: TimeSlot,
              skill: SkillLevel, max: Int, count: Int, host: Int, notes: Int) = PickupSession(
            id, sport, app.getString(title), app.getString(location), day, time, skill,
            max, count, app.getString(host), app.getString(notes),
        )
        return listOf(
            s(1, Sport.BASKETBALL, R.string.seed_title_1, R.string.seed_location_1, Day.TODAY,
                TimeSlot.EVENING, SkillLevel.INTERMEDIATE, 10, 7, R.string.seed_host_1, R.string.seed_notes_1),
            s(2, Sport.BASKETBALL, R.string.seed_title_2, R.string.seed_location_2, Day.TOMORROW,
                TimeSlot.AFTERNOON, SkillLevel.CASUAL, 8, 3, R.string.seed_host_2, R.string.seed_notes_2),
            s(3, Sport.BASKETBALL, R.string.seed_title_3, R.string.seed_location_3, Day.SATURDAY,
                TimeSlot.MORNING, SkillLevel.COMPETITIVE, 10, 10, R.string.seed_host_3, R.string.seed_notes_3),
            s(4, Sport.SOCCER, R.string.seed_title_4, R.string.seed_location_4, Day.TODAY,
                TimeSlot.AFTERNOON, SkillLevel.CASUAL, 14, 9, R.string.seed_host_4, R.string.seed_notes_4),
            s(5, Sport.SOCCER, R.string.seed_title_5, R.string.seed_location_5, Day.SUNDAY,
                TimeSlot.MORNING, SkillLevel.INTERMEDIATE, 22, 12, R.string.seed_host_5, R.string.seed_notes_5),
            s(6, Sport.VOLLEYBALL, R.string.seed_title_6, R.string.seed_location_6, Day.TOMORROW,
                TimeSlot.EVENING, SkillLevel.CASUAL, 12, 5, R.string.seed_host_6, R.string.seed_notes_6),
            s(7, Sport.TENNIS, R.string.seed_title_7, R.string.seed_location_7, Day.SATURDAY,
                TimeSlot.MORNING, SkillLevel.INTERMEDIATE, 4, 2, R.string.seed_host_7, R.string.seed_notes_7),
            s(8, Sport.ULTIMATE, R.string.seed_title_8, R.string.seed_location_8, Day.TODAY,
                TimeSlot.EVENING, SkillLevel.CASUAL, 14, 8, R.string.seed_host_8, R.string.seed_notes_8),
            s(9, Sport.PICKLEBALL, R.string.seed_title_9, R.string.seed_location_9, Day.TOMORROW,
                TimeSlot.MORNING, SkillLevel.CASUAL, 4, 1, R.string.seed_host_9, R.string.seed_notes_9),
        )
    }
}
