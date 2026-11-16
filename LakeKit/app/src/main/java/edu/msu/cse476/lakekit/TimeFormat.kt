package edu.msu.cse476.lakekit

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// FIX B: this used org.jetbrainz.datetime.Stamp from an unverified local jar. The JDK's
// java.time (available from API 26, our minSdk) produces the same "MMM d, HH:mm" text.
object TimeFormat {
    private val SHORT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

    fun short(millis: Long): String =
        SHORT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
