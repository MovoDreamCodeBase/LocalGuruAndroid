package com.movodream.localguru.data_collection.ui.adapter


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.databinding.ItemSubTaskBinding




class SubTaskAdapter(private val items: List<TaskItem.SubTask>) :
    RecyclerView.Adapter<SubTaskAdapter.SubTaskViewHolder>() {

    inner class SubTaskViewHolder(private val binding: ItemSubTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskItem.SubTask) {
            binding.subtask = item
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubTaskViewHolder {

        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSubTaskBinding.inflate(inflater, parent, false)
        return SubTaskViewHolder(binding)


    }

    override fun onBindViewHolder(holder: SubTaskViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
