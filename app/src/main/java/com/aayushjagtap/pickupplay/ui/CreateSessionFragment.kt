package com.aayushjagtap.pickupplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.CreateSessionViewModel
import com.aayushjagtap.pickupplay.data.Day
import com.aayushjagtap.pickupplay.data.SessionsViewModel
import com.aayushjagtap.pickupplay.data.SkillLevel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.data.TimeSlot
import com.aayushjagtap.pickupplay.databinding.FragmentCreateSessionBinding

/**
 * Screen 4: form to host a new pickup session (Task 2).
 * Typed text survives rotation through EditText saved state; chip selections survive
 * through [CreateSessionViewModel]'s SavedStateHandle.
 */
class CreateSessionFragment : Fragment() {

    private var _binding: FragmentCreateSessionBinding? = null
    private val binding get() = _binding!!
    private val sessionsViewModel: SessionsViewModel by activityViewModels()
    private val formViewModel: CreateSessionViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val argSport = Sport.valueOf(requireArguments().getString(ARG_SPORT, Sport.BASKETBALL.name))
        formViewModel.initDefaults(argSport)

        // Restore selections from the ViewModel, then listen for changes.
        val sportChips = binding.formSport.sportChips
        val dayChips = binding.formWhen.dayChips
        val timeChips = binding.formWhen.timeChips
        val skillChips = binding.formWhen.skillChips
        with(binding.formFields) {
            formViewModel.sport?.let { sportChips.check(it.chipId) }
            formViewModel.day?.let { dayChips.check(it.chipId) }
            formViewModel.time?.let { timeChips.check(it.chipId) }
            formViewModel.skill?.let { skillChips.check(it.chipId) }

            sportChips.setOnCheckedStateChangeListener { _, ids ->
                ids.firstOrNull()?.let { Sport.fromChipId(it) }?.let { formViewModel.sport = it }
            }
            dayChips.setOnCheckedStateChangeListener { _, ids ->
                ids.firstOrNull()?.let { Day.fromChipId(it) }?.let { formViewModel.day = it }
            }
            timeChips.setOnCheckedStateChangeListener { _, ids ->
                ids.firstOrNull()?.let { TimeSlot.fromChipId(it) }?.let { formViewModel.time = it }
            }
            skillChips.setOnCheckedStateChangeListener { _, ids ->
                ids.firstOrNull()?.let { SkillLevel.fromChipId(it) }?.let { formViewModel.skill = it }
            }

            // Clear an error as soon as the user starts fixing it.
            titleInput.doAfterTextChanged { titleLayout.error = null }
            locationInput.doAfterTextChanged { locationLayout.error = null }
            maxPlayersInput.doAfterTextChanged { maxPlayersLayout.error = null }

        }
        binding.submitButton.setOnClickListener { submit() }
    }

    private fun submit() = with(binding.formFields) {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        val location = locationInput.text?.toString()?.trim().orEmpty()
        val max = maxPlayersInput.text?.toString()?.trim()?.toIntOrNull()
        val notes = notesInput.text?.toString()?.trim().orEmpty()

        var valid = true
        if (title.isEmpty()) {
            titleLayout.error = getString(R.string.error_title_required); valid = false
        }
        if (location.isEmpty()) {
            locationLayout.error = getString(R.string.error_location_required); valid = false
        }
        if (max == null || max !in MIN_PLAYERS..MAX_PLAYERS) {
            maxPlayersLayout.error = getString(R.string.error_max_players, MIN_PLAYERS, MAX_PLAYERS)
            valid = false
        }
        if (!valid) return@with

        val id = sessionsViewModel.createSession(
            sport = formViewModel.sport ?: Sport.BASKETBALL,
            title = title,
            location = location,
            day = formViewModel.day ?: Day.TODAY,
            time = formViewModel.time ?: TimeSlot.EVENING,
            skill = formViewModel.skill ?: SkillLevel.CASUAL,
            maxPlayers = max!!,
            notes = notes,
        )

        // Show the list for the sport that was created (the user may have switched sport on
        // this form). The action pops back to the sport picker so Back never returns to the form.
        findNavController().navigate(
            R.id.action_createSession_to_sessionList,
            bundleOf(
                SessionListFragment.ARG_SPORT to (formViewModel.sport ?: Sport.BASKETBALL).name,
                SessionListFragment.ARG_CREATED_ID to id,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SPORT = "sport"
        private const val MIN_PLAYERS = 2
        private const val MAX_PLAYERS = 30
    }
}
