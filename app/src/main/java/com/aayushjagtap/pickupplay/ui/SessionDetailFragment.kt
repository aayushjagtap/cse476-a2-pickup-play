package com.aayushjagtap.pickupplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.PickupSession
import com.aayushjagtap.pickupplay.data.SessionsViewModel
import com.aayushjagtap.pickupplay.databinding.FragmentSessionDetailBinding

/** Screen 3: the detail half of the list-detail flow, where the user joins a session (Task 3). */
class SessionDetailFragment : Fragment() {

    private var _binding: FragmentSessionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SessionsViewModel by activityViewModels()

    private val sessionId: Long by lazy { requireArguments().getLong(ARG_SESSION_ID) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSessionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.session(sessionId).observe(viewLifecycleOwner) { session ->
            if (session == null) {
                findNavController().popBackStack()
            } else {
                bind(session)
            }
        }
        binding.detailActions.joinButton.setOnClickListener { viewModel.toggleJoin(sessionId) }
    }

    private fun bind(s: PickupSession) {
        val ctx = requireContext()
        with(binding.detailInfo) {
            detailSport.text = getString(
                R.string.sport_with_emoji, getString(s.sport.emojiRes), getString(s.sport.labelRes)
            )
            detailTitle.text = s.title
            detailLocation.text = s.location
            detailWhen.text = s.whenText(ctx)
            detailSkill.setText(s.skill.labelRes)
            detailHost.text = s.hostName
            detailPlayers.text = s.playersText(ctx)
            detailProgress.max = s.maxPlayers
            detailProgress.setProgressCompat(s.playerCount, true)
            detailNotes.text = s.notes
            detailNotes.isVisible = s.notes.isNotBlank()
            detailNotesLabel.isVisible = s.notes.isNotBlank()
        }

        // Status message and button reflect the user's relationship to the session.
        with(binding.detailActions) {
            when {
                s.isHosting -> {
                    detailStatus.setText(R.string.status_hosting)
                    joinButton.setText(R.string.button_hosting)
                    joinButton.isEnabled = false
                }
                s.isJoined -> {
                    detailStatus.setText(R.string.status_joined)
                    joinButton.setText(R.string.button_leave)
                    joinButton.isEnabled = true
                }
                s.isFull -> {
                    detailStatus.setText(R.string.status_full)
                    joinButton.setText(R.string.button_full)
                    joinButton.isEnabled = false
                }
                else -> {
                    val spots = s.maxPlayers - s.playerCount
                    detailStatus.text =
                        resources.getQuantityString(R.plurals.status_spots_left, spots, spots)
                    joinButton.setText(R.string.button_join)
                    joinButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
