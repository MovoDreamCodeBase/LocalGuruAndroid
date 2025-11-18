package com.movodream.localguru.data_collection.ui.adapter



import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.movodream.localguru.data_collection.model.SummaryItem
import com.movodream.localguru.databinding.ItemTaskSummaryCardBinding


class SummaryAdapter :
    ListAdapter<SummaryItem, SummaryAdapter.SummaryViewHolder>(SummaryDiffCallback()) {

    inner class SummaryViewHolder(private val binding: ItemTaskSummaryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SummaryItem) {
            binding.item= item
            binding.executePendingBindings()



        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTaskSummaryCardBinding.inflate(inflater, parent, false)
        return SummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class SummaryDiffCallback : DiffUtil.ItemCallback<SummaryItem>() {
    override fun areItemsTheSame(oldItem: SummaryItem, newItem: SummaryItem): Boolean =
        oldItem.count == newItem.count

    override fun areContentsTheSame(oldItem: SummaryItem, newItem: SummaryItem): Boolean =
        oldItem == newItem
}