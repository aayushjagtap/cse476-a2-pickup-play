package edu.msu.cse476.lakekit

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class Reading(val time: Long, val value: Double)

data class Station(val name: String, val unit: String, val readings: List<Reading>)

object StationParser {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(ZoneOffset.UTC)

    fun parse(json: String): Station = try {
        val root = JSONObject(json)
        if (root.has("error")) throw IOException(root.getJSONObject("error").getString("message"))
        val array = root.getJSONArray("data")
        val readings = ArrayList<Reading>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val value = item.optString("v").toDoubleOrNull() ?: continue
            val time = Instant.from(stamp.parse(item.getString("t"))).toEpochMilli()
            readings.add(Reading(time, value))
        }
        Station(root.getJSONObject("metadata").getString("name"), "m", readings)
    } catch (e: JSONException) {
        throw IOException("Unreadable station data", e)
    } catch (e: DateTimeParseException) {
        throw IOException("Unreadable timestamp", e)
    }

    fun parseStatus(json: String): String = try {
        val root = JSONObject(json)
        if (root.has("error")) throw IOException(root.getJSONObject("error").getString("message"))
        val latest = root.getJSONArray("data").getJSONObject(0)
        latest.getString("v") + " m at " + latest.getString("t") + " UTC"
    } catch (e: JSONException) {
        throw IOException("Unreadable status", e)
    }
}
