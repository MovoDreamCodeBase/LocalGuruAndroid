package com.movodream.localguru.data_collection.ui.adapter

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.PhotoWithMeta

class PhotoMetadataAdapter(
    private val onDelete: (PhotoWithMeta) -> Unit
) : ListAdapter<PhotoWithMeta, PhotoMetadataAdapter.VH>(DiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).uri.hashCode().toLong()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgPhoto)
        val label: EditText = v.findViewById(R.id.etLabel)
        val desc: EditText = v.findViewById(R.id.etDesc)
        val del: ImageView = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_metadata, parent, false)
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        // -----------------------------
        // IMAGE
        // -----------------------------
        Glide.with(holder.itemView)
            .load(Uri.parse(item.uri))
            .into(holder.img)

        // -----------------------------
        // REMOVE OLD WATCHERS
        // -----------------------------
        (holder.label.tag as? TextWatcher)?.let {
            holder.label.removeTextChangedListener(it)
        }
        (holder.desc.tag as? TextWatcher)?.let {
            holder.desc.removeTextChangedListener(it)
        }

        // -----------------------------
        // SET DATA (NO CLEAR!)
        // -----------------------------
        holder.label.setText(item.label)
        holder.desc.setText(item.description)

        // -----------------------------
        // ADD WATCHERS
        // -----------------------------
        val labelWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                item.label = s?.toString().orEmpty()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        val descWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                item.description = s?.toString().orEmpty()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        holder.label.addTextChangedListener(labelWatcher)
        holder.desc.addTextChangedListener(descWatcher)

        // Store watchers for recycling
        holder.label.tag = labelWatcher
        holder.desc.tag = descWatcher

        // -----------------------------
        // DELETE
        // -----------------------------
        holder.del.setOnClickListener {
            onDelete(item)
        }
    }
    class DiffCallback : DiffUtil.ItemCallback<PhotoWithMeta>() {

        override fun areItemsTheSame(
            oldItem: PhotoWithMeta,
            newItem: PhotoWithMeta
        ): Boolean {
            // 🔥 UNIQUE identity
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(
            oldItem: PhotoWithMeta,
            newItem: PhotoWithMeta
        ): Boolean {
            return oldItem.label == newItem.label &&
                    oldItem.description == newItem.description &&
                    oldItem.latitude == newItem.latitude &&
                    oldItem.longitude == newItem.longitude
        }
    }

}
