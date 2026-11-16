package edu.msu.cse476.lakelog

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * One NOAA water-level reading.
 * [time] is NOAA's "t" string ("yyyy-MM-dd HH:mm", UTC). It is unique and stable, so it is
 * also the key a note is stored under. [level] is "v" in meters, or null when NOAA sent it empty.
 */
data class Reading(val time: String, val level: String?) {

    /** "Nov 15 19:30 UTC" for display; falls back to the raw string if it doesn't parse. */
    val displayTime: String
        get() = try {
            LocalDateTime.parse(time, NOAA_TIME).format(DISPLAY_TIME)
        } catch (e: DateTimeParseException) {
            time
        }

    companion object {
        private val NOAA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        private val DISPLAY_TIME = DateTimeFormatter.ofPattern("MMM d HH:mm 'UTC'", Locale.US)
    }
}

object NoaaParser {

    /**
     * Parses a datagetter response into readings, newest first.
     * Throws IOException for NOAA's {"error": ...} response or JSON that isn't usable,
     * so the caller can treat both as a failed download.
     */
    @Throws(IOException::class)
    fun parse(json: String): List<Reading> = try {
        val root = JSONObject(json)
        if (root.has("error")) {
            val message = root.optJSONObject("error")?.optString("message").orEmpty()
            throw IOException(message.ifBlank { "NOAA returned an error" })
        }
        val data = root.getJSONArray("data")
        val readings = ArrayList<Reading>(data.length())
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val time = item.optString("t").trim()
            if (time.isEmpty()) continue
            // An empty "v" stays in the list as "Unavailable", never as zero.
            val level = item.optString("v").trim().takeIf { it.isNotEmpty() && it.toDoubleOrNull() != null }
            readings.add(Reading(time, level))
        }
        readings.reversed() // NOAA sends oldest first; show newest first
    } catch (e: JSONException) {
        throw IOException("Unreadable response from NOAA", e)
    }
}
