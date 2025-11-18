package com.movodream.localguru.data_collection.ui.adapter



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.movodream.localguru.R

import com.google.android.material.card.MaterialCardView
import com.movodream.localguru.data_collection.model.TabSchema


class TabAdapter(private val onTabSelected: (TabSchema) -> Unit) : RecyclerView.Adapter<TabAdapter.TabVH>() {

    private var tabs: List<TabSchema> = emptyList()
    private var selectedId: String? = null

    fun submitTabs(list: List<TabSchema>) {
        tabs = list.sortedBy { it.order }
        if (selectedId == null && tabs.isNotEmpty()) selectedId = tabs[0].id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_chip, parent, false)
        return TabVH(v)
    }
    fun highlightTab(tabId: String) {
        selectedId = tabId
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: TabVH, position: Int) {
        val t = tabs[position]
        holder.bind(t, t.id == selectedId) {
            selectedId = t.id
            notifyDataSetChanged()
            onTabSelected(t)
        }
    }

    override fun getItemCount(): Int = tabs.size

    class TabVH(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view.findViewById<MaterialCardView>(R.id.card)
        private val title = view.findViewById<TextView>(R.id.tab_title)
        fun bind(tab: TabSchema, selected: Boolean, click: () -> Unit) {
            title.text = tab.title
            val ctx = itemView.context
            if (selected) {
                card.setCardBackgroundColor(ctx.getColor(com.core.R.color.colorLightBlue1))
                title.setTextColor(ctx.getColor(android.R.color.white))
            } else {
                card.setCardBackgroundColor(ctx.getColor(com.core.R.color.white))
                title.setTextColor(ctx.getColor(com.core.R.color.colorLightBlack))
            }
            card.setOnClickListener { click() }
        }
    }
}
