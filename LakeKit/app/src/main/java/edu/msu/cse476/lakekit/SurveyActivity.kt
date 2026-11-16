package edu.msu.cse476.lakekit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SurveyActivity : AppCompatActivity() {

    private lateinit var sweep: SweepView
    private var readings: List<Reading> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val frame = object : Runnable {
        override fun run() {
            sweep.advance()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survey)
        sweep = findViewById(R.id.sweep)
        findViewById<ImageButton>(R.id.exportButton).setOnClickListener { export() }
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { StationRepository(this@SurveyActivity).cached() }
            readings = json?.let { runCatching { StationParser.parse(it).readings }.getOrNull() } ?: emptyList()
            sweep.setReadings(readings)
        }
        handler.post(frame)
    }

    private fun export() {
        val rows = readings
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { writeCsv(rows) }
            Toast.makeText(this@SurveyActivity, getString(R.string.exported, file.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeCsv(rows: List<Reading>): File {
        val file = File(filesDir, "survey-export.csv")
        file.bufferedWriter().use { out ->
            out.write("time,value\n")
            for (r in rows) out.write("${r.time},${r.value}\n")
        }
        return file
    }

    companion object {
        private const val FRAME_MS = 16L
    }
}
