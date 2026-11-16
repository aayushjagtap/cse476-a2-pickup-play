package edu.msu.cse476.lakekit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var repo: StationRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: ReadingsAdapter
    private lateinit var sparkline: SparklineView
    private lateinit var stationStatus: TextView
    private lateinit var summary: TextView
    private lateinit var refreshed: TextView
    private lateinit var recentOnly: SwitchCompat
    private lateinit var level: StatusGaugeView
    private var station: Station? = null
    private var lastRefresh = 0L
    private var refreshJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val clock = object : Runnable {
        override fun run() {
            showRefreshed()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = StationRepository(this)
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        stationStatus = findViewById(R.id.stationStatus)
        summary = findViewById(R.id.summary)
        refreshed = findViewById(R.id.refreshed)
        sparkline = findViewById(R.id.sparkline)
        recentOnly = findViewById(R.id.recentOnly)
        level = findViewById(R.id.level)
        adapter = ReadingsAdapter()
        val list = findViewById<RecyclerView>(R.id.readingList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        lastRefresh = prefs.getLong(KEY_LAST_REFRESH, 0L)
        recentOnly.isChecked = prefs.getBoolean(KEY_RECENT_ONLY, false)
        recentOnly.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_RECENT_ONLY, checked).apply()
            show()
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            // MEASUREMENT (added for the exam report): time the click handler holds the main thread.
            val start = android.os.SystemClock.uptimeMillis()
            refresh()
            android.util.Log.i("LakeKitMeasure", "refresh click handler held the main thread for " +
                (android.os.SystemClock.uptimeMillis() - start) + " ms")
        }
        findViewById<ImageButton>(R.id.surveyButton).setOnClickListener {
            startActivity(Intent(this, SurveyActivity::class.java))
        }
        findViewById<ImageButton>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { repo.cached() }
            station = json?.let { runCatching { StationParser.parse(it) }.getOrNull() }
            show()
        }
        startService(Intent(this, StationStatusService::class.java))
    }

    override fun onStart() {
        super.onStart()
        handler.post(clock)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            repo.cached()?.let { repo.save(it) }
        }
        val status = prefs.getString(StationStatusService.KEY_STATUS, null) ?: getString(R.string.status_unknown)
        stationStatus.text = getString(R.string.station_status, status)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clock)
    }

    // FIX A: the download, file write and JSON parse used to run here on the main thread
    // (hidden by StrictMode permitAll). They now run on Dispatchers.IO in lifecycleScope,
    // so the UI stays responsive and the work is cancelled if the Activity is destroyed.
    private fun refresh() {
        if (refreshJob?.isActive == true) return
        summary.setText(R.string.loading)
        refreshJob = lifecycleScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val json = repo.download()
                    val result = StationParser.parse(json)
                    repo.save(json)
                    result
                }
                applyStation(parsed)
            } catch (e: IOException) {
                summary.text = getString(R.string.refresh_failed, e.message ?: "")
            }
        }
    }

    private fun applyStation(parsed: Station) {
        station = parsed
        lastRefresh = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_REFRESH, lastRefresh).apply()
        show()
    }

    private fun show() {
        val s = station
        if (s == null) {
            summary.setText(R.string.no_data)
            showRefreshed()
            return
        }
        val rows = if (recentOnly.isChecked) {
            val newest = s.readings.maxOfOrNull { it.time } ?: 0L
            s.readings.filter { it.time >= newest - DAY_MS }
        } else {
            s.readings
        }
        summary.text = getString(R.string.station_summary, s.name, rows.size)
        adapter.unit = s.unit
        adapter.submitList(rows.takeLast(50).reversed())
        sparkline.setValues(rows.map { it.value.toFloat() })
        level.setLevel(levelPercent(rows))
        showRefreshed()
    }

    private fun levelPercent(rows: List<Reading>): Int {
        if (rows.isEmpty()) return 0
        val low = rows.minOf { it.value }
        val high = rows.maxOf { it.value }
        if (high <= low) return 50
        return ((rows.last().value - low) / (high - low) * 100).toInt()
    }

    private fun showRefreshed() {
        refreshed.text = if (lastRefresh == 0L) {
            getString(R.string.never_refreshed)
        } else {
            val minutes = (System.currentTimeMillis() - lastRefresh) / 60_000L
            getString(R.string.refreshed_at, minutes, TimeFormat.short(lastRefresh))
        }
    }

    companion object {
        private const val KEY_RECENT_ONLY = "recent_only"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
