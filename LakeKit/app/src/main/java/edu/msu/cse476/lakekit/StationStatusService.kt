package edu.msu.cse476.lakekit

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.IOException

class StationStatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = StationRepository(this)
        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        Thread {
            while (true) {
                try {
                    val status = StationParser.parseStatus(repo.downloadStatus())
                    prefs.edit().putString(KEY_STATUS, status).apply()
                } catch (e: IOException) {
                    Log.w("StationStatus", "status fetch failed", e)
                }
                Thread.sleep(10_000L)
            }
        }.start()
        return START_STICKY
    }

    companion object {
        const val KEY_STATUS = "station_status"
    }
}
