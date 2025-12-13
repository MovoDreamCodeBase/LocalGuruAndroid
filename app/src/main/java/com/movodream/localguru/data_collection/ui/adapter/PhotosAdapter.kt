package com.movodream.localguru.data_collection.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.movodream.localguru.R
import com.movodream.localguru.databinding.ItemImageBinding

class PhotosAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<PhotosAdapter.PhotoVH>() {

    inner class PhotoVH(val binding: ItemImageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImageBinding.inflate(inflater, parent, false)
        return PhotoVH(binding)
    }

    override fun onBindViewHolder(holder: PhotoVH, position: Int) {
        val url = images[position]
        Glide.with(holder.binding.root.context)
            .load(url)
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.binding.imgItem)
    }

    override fun getItemCount(): Int = images.size
}
