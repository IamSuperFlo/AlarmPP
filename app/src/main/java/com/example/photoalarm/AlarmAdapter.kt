package com.example.photoalarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photoalarm.databinding.ItemAlarmBinding

class AlarmAdapter(
    private var items: List<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.VH>() {

    inner class VH(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val alarm = items[position]
        holder.binding.txtTime.text = alarm.timeText()
        val info = buildString {
            if (alarm.label.isNotBlank()) append(alarm.label).append(" • ")
            append(alarm.daysText())
            if (alarm.objectCount > 1) append(" • ${alarm.objectCount} obiecte")
        }
        holder.binding.txtInfo.text = info

        // Avoid the listener firing while we set the initial state.
        holder.binding.switchEnabled.setOnCheckedChangeListener(null)
        holder.binding.switchEnabled.isChecked = alarm.enabled
        holder.binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            onToggle(alarm, checked)
        }

        holder.binding.root.setOnClickListener { onClick(alarm) }
    }

    fun update(newItems: List<Alarm>) {
        items = newItems
        notifyDataSetChanged()
    }
}
