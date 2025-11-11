package com.movodream.localguru.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.movodream.localguru.ui.fragments.DashboardFragment
import com.movodream.localguru.ui.fragments.ProfileFragment
import com.movodream.localguru.ui.fragments.ReportFragment
import com.movodream.localguru.ui.fragments.TaskFragment

class DashboardPagerAdapter(
    fa: FragmentActivity
) : FragmentStateAdapter(fa) {

    private val fragments = listOf(
        DashboardFragment(),
        TaskFragment(),
        ReportFragment(),
        ProfileFragment()
    )

    override fun getItemCount() = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]
}