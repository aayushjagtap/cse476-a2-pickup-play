package edu.msu.cse476.lakekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ReadingsAdapter : ListAdapter<Reading, ReadingsAdapter.Holder>(ReadingDiff) {

    var unit: String = ""

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.readingTime)
        val value: TextView = view.findViewById(R.id.readingValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_reading, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val reading = getItem(position)
        holder.time.text = TimeFormat.short(reading.time)
        holder.value.text = String.format(Locale.US, "%.2f %s", reading.value, unit)
    }
}

object ReadingDiff : DiffUtil.ItemCallback<Reading>() {
    override fun areItemsTheSame(oldItem: Reading, newItem: Reading): Boolean = oldItem.time == newItem.time

    override fun areContentsTheSame(oldItem: Reading, newItem: Reading): Boolean = oldItem == newItem
}
