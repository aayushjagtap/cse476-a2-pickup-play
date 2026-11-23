package com.aayushjagtap.pickupplay.data.db

import android.content.Context
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.Day
import com.aayushjagtap.pickupplay.data.PickupSession
import com.aayushjagtap.pickupplay.data.SkillLevel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.data.TimeSlot

/** The original synthetic sample sessions, written to the database once on first creation. */
object SeedSessions {

    fun sessions(context: Context): List<PickupSession> {
        fun s(id: Long, sport: Sport, title: Int, location: Int, day: Day, time: TimeSlot,
              skill: SkillLevel, max: Int, count: Int, host: Int, notes: Int,
              lat: Double, lng: Double) = PickupSession(
            id, sport, context.getString(title), context.getString(location), day, time, skill,
            max, count, context.getString(host), context.getString(notes),
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

    /** Seed rows use createdAt = 0 so any session the user creates sorts above them. */
    fun entities(context: Context): List<SessionEntity> =
        sessions(context).map { SessionEntity.fromModel(it, createdAt = 0L) }
}
