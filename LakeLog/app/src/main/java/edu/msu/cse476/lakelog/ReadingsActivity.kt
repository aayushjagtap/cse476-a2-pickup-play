package edu.msu.cse476.lakelog

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import edu.msu.cse476.lakelog.databinding.ActivityReadingsBinding

/** Downloads and lists the last two days of readings (2a-2e). */
class ReadingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingsBinding
    private lateinit var storage: Storage
    private lateinit var adapter: ReadingsAdapter
    private val viewModel: ReadingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        storage = Storage(this)

        adapter = ReadingsAdapter { reading ->
            startActivity(
                Intent(this, NoteActivity::class.java)
                    .putExtra(NoteActivity.EXTRA_TIME, reading.time)
                    .putExtra(NoteActivity.EXTRA_LEVEL, reading.level)
            )
        }
        val layoutManager = LinearLayoutManager(this)
        binding.readingList.layoutManager = layoutManager
        binding.readingList.addItemDecoration(DividerItemDecoration(this, layoutManager.orientation))
        binding.readingList.setHasFixedSize(true)
        binding.readingList.adapter = adapter

        binding.refreshButton.setOnClickListener { viewModel.load() }
        binding.retryButton.setOnClickListener { viewModel.load() }
        viewModel.state.observe(this) { render(it) }
        viewModel.loadIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the Note Activity: re-read which readings have notes.
        adapter.noted = storage.notedTimes()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render(state: ReadingsViewModel.State) {
        val readings = when (state) {
            is ReadingsViewModel.State.Loaded -> state.readings
            is ReadingsViewModel.State.Loading -> state.previous
            is ReadingsViewModel.State.Failed -> state.previous
            ReadingsViewModel.State.Idle -> emptyList()
        }
        adapter.submitList(readings)
        adapter.noted = storage.notedTimes()

        val loading = state is ReadingsViewModel.State.Loading || state == ReadingsViewModel.State.Idle
        val failed = state is ReadingsViewModel.State.Failed
        binding.progress.isVisible = loading
        binding.retryButton.isVisible = failed
        binding.refreshButton.isEnabled = !loading
        binding.statusText.isVisible = loading || failed || readings.isEmpty()
        binding.statusText.text = when {
            loading -> getString(R.string.downloading)
            failed -> getString(R.string.download_failed, (state as ReadingsViewModel.State.Failed).message)
            else -> getString(R.string.no_readings)
        }
        if (state is ReadingsViewModel.State.Loaded) {
            binding.summary.text = resources.getQuantityString(
                R.plurals.reading_count, readings.size, readings.size
            )
            storage.pruneNotes(readings.map { it.time }, readings.lastOrNull()?.time)
        }
        binding.summary.isVisible = readings.isNotEmpty()
    }
}
