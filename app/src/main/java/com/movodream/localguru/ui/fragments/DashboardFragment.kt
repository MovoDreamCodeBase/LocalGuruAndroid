package com.movodream.localguru.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.movodream.localguru.databinding.FragmentDashboardBinding
import com.movodream.localguru.presentation.DashboardViewModel
import com.movodream.localguru.ui.adapter.SummaryAdapter
import com.movodream.localguru.ui.adapter.TaskAdapter


class DashboardFragment : Fragment() {
    private lateinit var binding: FragmentDashboardBinding
    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private val adapter = TaskAdapter()
    private val adapterSummary = SummaryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = dashboardViewModel // CRITICAL: Assign the ViewModel to the layout
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setObserver()
        setAdapters()
    }

    private fun setObserver() {
        dashboardViewModel.summaryItems.observe(requireActivity(), Observer {
            adapterSummary.submitList(it)
        })
        dashboardViewModel.dashboardTasks.observe(requireActivity()) {
            adapter.submitList(it)
        }
    }

    private fun setAdapters() {


        binding.rvSummary.adapter = adapterSummary


        binding.rvMainList.layoutManager = GridLayoutManager(requireActivity(), 1)
        binding.rvMainList.adapter = adapter


    }

}