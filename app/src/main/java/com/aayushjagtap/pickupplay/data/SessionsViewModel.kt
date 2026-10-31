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

    // ---- "Nearest first" location state. Lives here so it survives rotation. ----

    private val _distanceMode = MutableLiveData(false)
    val distanceMode: LiveData<Boolean> = _distanceMode

    private val _userFix = MutableLiveData<GeoFix?>(null)
    /** Last location fix that passed the jump filter. */
    val userFix: LiveData<GeoFix?> = _userFix

    private val _locationStatus = MutableLiveData<LocationStatus>(LocationStatus.Off)
    val locationStatus: LiveData<LocationStatus> = _locationStatus

    private val jumpFilter = JumpFilter()

    fun setDistanceMode(on: Boolean) {
        _distanceMode.value = on
        if (!on) {
            jumpFilter.reset()
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
                _locationStatus.value =
                    LocationStatus.Active(fix.accuracyMeters, decision.confirmedJumpMeters)
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
              skill: SkillLevel, max: Int, count: Int, host: Int, notes: Int,
              lat: Double, lng: Double) = PickupSession(
            id, sport, app.getString(title), app.getString(location), day, time, skill,
            max, count, app.getString(host), app.getString(notes),
            latitude = lat, longitude = lng,
        )
        // Synthetic coordinates around the MSU campus in East Lansing.
        return listOf(
            s(1, Sport.BASKETBALL, R.string.seed_title_1, R.string.seed_location_1, Day.TODAY,
                TimeSlot.EVENING, SkillLevel.INTERMEDIATE, 10, 7, R.string.seed_host_1, R.string.seed_notes_1, 42.7271, -84.4879),
            s(2, Sport.BASKETBALL, R.string.seed_title_2, R.string.seed_location_2, Day.TOMORROW,
                TimeSlot.AFTERNOON, SkillLevel.CASUAL, 8, 3, R.string.seed_host_2, R.string.seed_notes_2, 42.7310, -84.4797),
            s(3, Sport.BASKETBALL, R.string.seed_title_3, R.string.seed_location_3, Day.SATURDAY,
                TimeSlot.MORNING, SkillLevel.COMPETITIVE, 10, 10, R.string.seed_host_3, R.string.seed_notes_3, 42.7235, -84.4651),
            s(4, Sport.SOCCER, R.string.seed_title_4, R.string.seed_location_4, Day.TODAY,
                TimeSlot.AFTERNOON, SkillLevel.CASUAL, 14, 9, R.string.seed_host_4, R.string.seed_notes_4, 42.7287, -84.4776),
            s(5, Sport.SOCCER, R.string.seed_title_5, R.string.seed_location_5, Day.SUNDAY,
                TimeSlot.MORNING, SkillLevel.INTERMEDIATE, 22, 12, R.string.seed_host_5, R.string.seed_notes_5, 42.7166, -84.4731),
            s(6, Sport.VOLLEYBALL, R.string.seed_title_6, R.string.seed_location_6, Day.TOMORROW,
                TimeSlot.EVENING, SkillLevel.CASUAL, 12, 5, R.string.seed_host_6, R.string.seed_notes_6, 42.7338, -84.4952),
            s(7, Sport.TENNIS, R.string.seed_title_7, R.string.seed_location_7, Day.SATURDAY,
                TimeSlot.MORNING, SkillLevel.INTERMEDIATE, 4, 2, R.string.seed_host_7, R.string.seed_notes_7, 42.7243, -84.4703),
            s(8, Sport.ULTIMATE, R.string.seed_title_8, R.string.seed_location_8, Day.TODAY,
                TimeSlot.EVENING, SkillLevel.CASUAL, 14, 8, R.string.seed_host_8, R.string.seed_notes_8, 42.7276, -84.4847),
            s(9, Sport.PICKLEBALL, R.string.seed_title_9, R.string.seed_location_9, Day.TOMORROW,
                TimeSlot.MORNING, SkillLevel.CASUAL, 4, 1, R.string.seed_host_9, R.string.seed_notes_9, 42.7271, -84.4879),
        )
    }
}
