package edu.msu.cse476.lakelog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import edu.msu.cse476.lakelog.databinding.ItemReadingBinding

class ReadingsAdapter(
    private val onClick: (Reading) -> Unit,
) : ListAdapter<Reading, ReadingsAdapter.Holder>(Diff) {

    /** Times of readings that have a note; those rows show a mark. */
    var noted: Set<String> = emptySet()
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount, PAYLOAD_MARK)
            }
        }

    class Holder(val binding: ItemReadingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val reading = getItem(position)
        val ctx = holder.itemView.context
        val levelText = reading.level?.let { ctx.getString(R.string.level_meters, it) }
            ?: ctx.getString(R.string.level_unavailable)
        holder.binding.readingTime.text = reading.displayTime
        holder.binding.readingLevel.text = levelText
        bindMark(holder, reading)
        holder.itemView.setOnClickListener { onClick(reading) }
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_MARK)) bindMark(holder, getItem(position))
        else onBindViewHolder(holder, position)
    }

    private fun bindMark(holder: Holder, reading: Reading) {
        val hasNote = reading.time in noted
        holder.binding.noteMark.isInvisible = !hasNote
        val ctx = holder.itemView.context
        val levelText = reading.level?.let { ctx.getString(R.string.level_meters, it) }
            ?: ctx.getString(R.string.level_unavailable)
        holder.itemView.contentDescription = ctx.getString(
            if (hasNote) R.string.cd_reading_with_note else R.string.cd_reading,
            reading.displayTime, levelText,
        )
    }

    private object Diff : DiffUtil.ItemCallback<Reading>() {
        override fun areItemsTheSame(a: Reading, b: Reading) = a.time == b.time
        override fun areContentsTheSame(a: Reading, b: Reading) = a == b
    }

    private companion object {
        const val PAYLOAD_MARK = "mark"
    }
}
