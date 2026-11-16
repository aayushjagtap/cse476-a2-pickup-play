package edu.msu.cse476.lakelog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import edu.msu.cse476.lakelog.databinding.ActivityNoteBinding

/**
 * Shows one reading and lets the user write, change, or delete its note (3a-3c).
 * Save keeps the note (a blank note deletes it); Back and the system Back discard changes.
 * The EditText keeps unsaved text across rotation through its saved instance state.
 */
class NoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteBinding
    private lateinit var storage: Storage
    private lateinit var readingTime: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        storage = Storage(this)

        readingTime = intent.getStringExtra(EXTRA_TIME) ?: run { finish(); return }
        val level = intent.getStringExtra(EXTRA_LEVEL)
        val reading = Reading(readingTime, level)

        binding.noteTime.text = reading.displayTime
        binding.noteLevel.text = level?.let { getString(R.string.level_meters, it) }
            ?: getString(R.string.level_unavailable)
        val observer = storage.observerName.trim()
        binding.noteObserver.text = getString(
            R.string.observer_label_value,
            observer.ifEmpty { getString(R.string.observer_not_set) },
        )

        // Only load the stored note on a fresh start; after rotation the EditText restores
        // the user's unsaved text itself.
        if (savedInstanceState == null) {
            binding.noteInput.setText(storage.note(readingTime).orEmpty())
        }

        binding.saveButton.setOnClickListener {
            storage.saveNote(readingTime, binding.noteInput.text?.toString().orEmpty())
            finish()
        }
        binding.backButton.setOnClickListener { finish() } // discard unsaved changes
    }

    override fun onSupportNavigateUp(): Boolean {
        finish() // same as Back: discard
        return true
    }

    companion object {
        const val EXTRA_TIME = "edu.msu.cse476.lakelog.TIME"
        const val EXTRA_LEVEL = "edu.msu.cse476.lakelog.LEVEL"
    }
}
