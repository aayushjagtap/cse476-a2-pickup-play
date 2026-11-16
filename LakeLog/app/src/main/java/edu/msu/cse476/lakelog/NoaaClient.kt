package edu.msu.cse476.lakelog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object NoaaClient {

    private const val URL_48H =
        "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?product=water_level" +
            "&station=9087023&datum=IGLD&units=metric&time_zone=gmt&format=json" +
            "&application=MSU_CSE476&range=48"

    /** Downloads and parses the last 48 hours of readings on a background thread. */
    @Throws(IOException::class)
    suspend fun downloadReadings(): List<Reading> = withContext(Dispatchers.IO) {
        val connection = URL(URL_48H).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            NoaaParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }
}
