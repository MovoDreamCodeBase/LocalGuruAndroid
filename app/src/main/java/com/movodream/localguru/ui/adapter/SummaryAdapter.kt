package com.movodream.localguru.ui.adapter



import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.movodream.localguru.R
import com.movodream.localguru.databinding.ItemSubTaskBinding
import com.movodream.localguru.databinding.ItemTaskBinding
import com.movodream.localguru.databinding.ItemTaskSummaryCardBinding
import com.movodream.localguru.model.SummaryItem
import com.movodream.localguru.model.TaskItem


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