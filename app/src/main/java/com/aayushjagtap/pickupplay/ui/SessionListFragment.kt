package com.aayushjagtap.pickupplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.PickupSession
import com.aayushjagtap.pickupplay.data.SessionsViewModel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.databinding.FragmentSessionListBinding
import com.aayushjagtap.pickupplay.databinding.ItemSessionBinding
import com.google.android.material.snackbar.Snackbar

/** Screen 2: the list half of the list-detail flow, filtered to the chosen sport. */
class SessionListFragment : Fragment() {

    private var _binding: FragmentSessionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SessionsViewModel by activityViewModels()

    private val sport: Sport by lazy {
        Sport.valueOf(requireArguments().getString(ARG_SPORT, Sport.BASKETBALL.name))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSessionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sportName = getString(sport.labelRes)
        binding.listHeader.text = getString(R.string.sessions_header, getString(sport.emojiRes), sportName)
        binding.emptyText.text = getString(R.string.sessions_empty, sportName)

        val adapter = SessionAdapter { session ->
            findNavController().navigate(
                R.id.action_sessionList_to_sessionDetail,
                bundleOf(SessionDetailFragment.ARG_SESSION_ID to session.id),
            )
        }
        binding.sessionList.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.session_list_columns))
        binding.sessionList.adapter = adapter

        viewModel.sessionsFor(sport).observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val open = list.count { !it.isFull }
            binding.listSubheader.text =
                resources.getQuantityString(R.plurals.open_sessions_count, open, open)
            binding.emptyText.isVisible = list.isEmpty()
            binding.sessionList.isVisible = list.isNotEmpty()
        }

        binding.createButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_sessionList_to_createSession,
                bundleOf(CreateSessionFragment.ARG_SPORT to sport.name),
            )
        }

        // Confirmation when arriving from the create screen. Shown once, not again after
        // rotation or after coming back from a detail screen.
        val createdId = requireArguments().getLong(ARG_CREATED_ID, NO_ID)
        if (createdId != NO_ID && !createdAnnounced) {
            createdAnnounced = true
            binding.sessionList.scrollToPosition(0)
            Snackbar.make(binding.root, R.string.session_created, Snackbar.LENGTH_LONG)
                .setAction(R.string.view) {
                    findNavController().navigate(
                        R.id.action_sessionList_to_sessionDetail,
                        bundleOf(SessionDetailFragment.ARG_SESSION_ID to createdId),
                    )
                }
                .show()
        }
    }

    private var createdAnnounced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdAnnounced = savedInstanceState?.getBoolean(KEY_ANNOUNCED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ANNOUNCED, createdAnnounced)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SPORT = "sport"
        const val ARG_CREATED_ID = "createdId"
        private const val NO_ID = -1L
        private const val KEY_ANNOUNCED = "createdAnnounced"
    }

    private class SessionAdapter(
        private val onClick: (PickupSession) -> Unit,
    ) : ListAdapter<PickupSession, SessionAdapter.Holder>(SessionDiff) {

        class Holder(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val session = getItem(position)
            val ctx = holder.itemView.context
            with(holder.binding) {
                sessionTitle.text = session.title
                sessionLocation.text = session.location
                sessionWhen.text = session.whenText(ctx)
                sessionSkill.setText(session.skill.labelRes)
                sessionPlayers.text = session.playersText(ctx)
                val badge = session.badgeText(ctx)
                sessionBadge.text = badge
                sessionBadge.isVisible = badge != null
                root.contentDescription = ctx.getString(
                    R.string.cd_session_card, session.title, session.location,
                    session.whenText(ctx), session.playersText(ctx),
                )
                root.setOnClickListener { onClick(session) }
            }
        }
    }
}

private object SessionDiff : DiffUtil.ItemCallback<PickupSession>() {
    override fun areItemsTheSame(oldItem: PickupSession, newItem: PickupSession) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: PickupSession, newItem: PickupSession) = oldItem == newItem
}
