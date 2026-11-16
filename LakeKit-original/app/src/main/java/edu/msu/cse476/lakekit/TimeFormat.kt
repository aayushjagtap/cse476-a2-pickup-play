package edu.msu.cse476.lakekit

import org.jetbrainz.datetime.Stamp

object TimeFormat {
    fun short(millis: Long): String = Stamp.shortStamp(millis)
}
