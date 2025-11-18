package com.movodream.localguru.data_collection.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.core.R
import com.core.base.BaseActivity
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.ui.adapter.DashboardPagerAdapter
import com.movodream.localguru.databinding.ActivityDashboardBinding


class DashboardActivity : BaseActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var dashboardViewModel: DashboardViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)
        binding.vm = dashboardViewModel
        binding.lifecycleOwner = this

        setAdapters()
        setCustomListener()
        setObserver()
    }


    private fun setAdapters(){
        val adapter = DashboardPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
    }

    private fun setCustomListener() {


        // Bottom Nav clicks
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_home,
            "Dashboard"
        ) {
            onSelectedTab(0)
        }

        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_task,
            "Tasks"
        ) {
            onSelectedTab(1)
        }

        binding.bottomNav.addItem(
            R.drawable.ic_edit,
            "Collect"
        ) {

        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_report,
            "Reports"
        ) {
            onSelectedTab(2)

        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_profile,
            "Profile"
        ) {
            onSelectedTab(3)
        }

        binding.bottomNav.selectItem(0)

    }

    private fun onSelectedTab(tabId : Int){
        binding.viewPager.currentItem = tabId
    }

    private fun setObserver() {
        dashboardViewModel.selectedTab.observe(this) {
            onSelectedTab(it)
            binding.bottomNav.selectItem(it)
        }
    }

}