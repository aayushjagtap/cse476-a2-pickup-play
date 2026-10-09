package com.aayushjagtap.pickupplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.SessionsViewModel
import com.aayushjagtap.pickupplay.data.Sport
import com.aayushjagtap.pickupplay.databinding.FragmentSportPickerBinding
import com.aayushjagtap.pickupplay.databinding.ItemSportBinding

/** Screen 1: the user picks the sport they want to play (Task 1). */
class SportPickerFragment : Fragment() {

    private var _binding: FragmentSportPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SessionsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSportPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = SportAdapter { sport ->
            findNavController().navigate(
                R.id.action_sportPicker_to_sessionList,
                bundleOf(SessionListFragment.ARG_SPORT to sport.name),
            )
        }
        binding.sportGrid.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.sport_grid_columns))
        binding.sportGrid.adapter = adapter

        // Refresh open-session counts whenever sessions change (for example after creating one).
        viewModel.sessions.observe(viewLifecycleOwner) {
            adapter.counts = Sport.entries.associateWith { viewModel.openCount(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SportAdapter(
        private val onClick: (Sport) -> Unit,
    ) : RecyclerView.Adapter<SportAdapter.Holder>() {

        var counts: Map<Sport, Int> = emptyMap()
            set(value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }

        class Holder(val binding: ItemSportBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemSportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = Sport.entries.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val sport = Sport.entries[position]
            val res = holder.itemView.resources
            val count = counts[sport] ?: 0
            holder.binding.sportEmoji.setText(sport.emojiRes)
            holder.binding.sportName.setText(sport.labelRes)
            holder.binding.sportCount.text =
                res.getQuantityString(R.plurals.open_sessions_count, count, count)
            holder.binding.root.contentDescription = res.getString(
                R.string.cd_sport_card, res.getString(sport.labelRes),
                res.getQuantityString(R.plurals.open_sessions_count, count, count),
            )
            holder.binding.root.setOnClickListener { onClick(sport) }
        }
    }
}
