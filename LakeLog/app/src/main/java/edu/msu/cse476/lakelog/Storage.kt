package edu.msu.cse476.lakelog

import android.content.Context
import android.content.SharedPreferences

/**
 * On-device storage for notes and the observer's name, in SharedPreferences.
 * Notes are keyed by the reading's NOAA timestamp, which never changes between downloads,
 * so a note stays with its reading through refreshes and restarts.
 */
class Storage(context: Context) {

    private val notes: SharedPreferences =
        context.applicationContext.getSharedPreferences("notes", Context.MODE_PRIVATE)
    private val settings: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var observerName: String
        get() = settings.getString(KEY_OBSERVER, "").orEmpty()
        set(value) = settings.edit().putString(KEY_OBSERVER, value).apply()

    fun note(readingTime: String): String? = notes.getString(readingTime, null)

    /** Saves the note, or deletes it when it is blank or whitespace-only. */
    fun saveNote(readingTime: String, text: String) {
        if (text.isBlank()) {
            notes.edit().remove(readingTime).commit()
        } else {
            notes.edit().putString(readingTime, text).commit()
        }
    }

    fun notedTimes(): Set<String> = notes.all.keys

    /** Drops notes for readings that are no longer in the 48-hour window. */
    fun pruneNotes(keep: Collection<String>, oldestKept: String?) {
        if (oldestKept == null) return
        val stale = notes.all.keys.filter { it !in keep && it < oldestKept }
        if (stale.isNotEmpty()) {
            notes.edit().apply { stale.forEach { remove(it) } }.apply()
        }
    }

    private companion object {
        const val KEY_OBSERVER = "observer_name"
    }
}
