package com.movodream.localguru.data_collection.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.movodream.localguru.data_collection.ui.fragments.CollectFragment
import com.movodream.localguru.data_collection.ui.fragments.DashboardFragment
import com.movodream.localguru.data_collection.ui.fragments.ProfileFragment
import com.movodream.localguru.data_collection.ui.fragments.ReportFragment
import com.movodream.localguru.data_collection.ui.fragments.TaskFragment


class DashboardPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

    private val fragments = listOf(
        DashboardFragment(),
        TaskFragment(),
        CollectFragment(),
        ReportFragment(),
        ProfileFragment()
    )

    override fun getItemCount() = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    // VERY IMPORTANT FIX:
    override fun getItemId(position: Int): Long {
        return fragments[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return fragments.any { it.hashCode().toLong() == itemId }
    }
}
