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
import com.movodream.localguru.databinding.ItemTaskBinding
import com.movodream.localguru.model.TaskItem


class TaskAdapter :
    ListAdapter<TaskItem, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TaskItem) {
            binding.task = item
            binding.executePendingBindings()

            // Smooth transition animation when expanding or changing content
            TransitionManager.beginDelayedTransition(binding.clRoot, AutoTransition())

            // Example: animate progress bar
            binding.progressBar.progress = item.progress

            // Setup nested RecyclerView
            val subAdapter = SubTaskAdapter(item.subTasks)
            binding.rvSubTasks.adapter = subAdapter
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ItemTaskBinding = DataBindingUtil.inflate(
            inflater, R.layout.item_task, parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TaskDiffCallback : DiffUtil.ItemCallback<TaskItem>() {
    override fun areItemsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean =
        oldItem.poiId == newItem.poiId

    override fun areContentsTheSame(oldItem: TaskItem, newItem: TaskItem): Boolean =
        oldItem == newItem
}
