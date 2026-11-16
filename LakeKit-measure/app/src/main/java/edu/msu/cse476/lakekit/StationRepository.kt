package edu.msu.cse476.lakekit

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class StationRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "station.json")

    fun download(): String = fetch(Config.STATION_URL)

    fun downloadStatus(): String = fetch(Config.STATUS_URL)

    fun save(json: String) {
        cacheFile.writeText(json)
    }

    fun cached(): String? = if (cacheFile.exists()) cacheFile.readText() else null

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
