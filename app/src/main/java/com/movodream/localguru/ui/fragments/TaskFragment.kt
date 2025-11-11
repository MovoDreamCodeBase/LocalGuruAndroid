package com.movodream.localguru.ui.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.movodream.localguru.R
import com.movodream.localguru.databinding.FragmentDashboardBinding
import com.movodream.localguru.databinding.FragmentTaskBinding
import com.movodream.localguru.model.TaskItem
import com.movodream.localguru.presentation.DashboardViewModel
import com.movodream.localguru.ui.activities.DynamicFormActivity
import com.movodream.localguru.ui.adapter.SummaryAdapter
import com.movodream.localguru.ui.adapter.TaskAdapter
import kotlin.getValue


class TaskFragment : Fragment(), TaskAdapter.TasksClickListener {
    private lateinit var binding: FragmentTaskBinding
    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private val adapter = TaskAdapter(this)
    private lateinit var tabButtons: List<AppCompatButton>
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTaskBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = dashboardViewModel // CRITICAL: Assign the ViewModel to the layout
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupTabs()
        setAdapters()
        setObserver()
    }


    private fun setupTabs() {
        // all tab buttons
        tabButtons = listOf(
            binding.tabAll,
            binding.tabPending,
            binding.tabProgress,
            binding.tabCompleted,
            binding.tabRevision
        )

        tabButtons.forEach { button ->
            button.setOnClickListener {
                selectTab(button)
                dashboardViewModel.filterTasks(button.text.toString())
            }
        }

        // default select "All"
        selectTab(binding.tabAll)
    }

    private fun selectTab(selected: AppCompatButton) {
        tabButtons.forEach { it.isSelected = it == selected }
    }

    private fun setObserver() {
        dashboardViewModel.filteredTasks.observe(viewLifecycleOwner, Observer { list ->
            adapter.submitList(list)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

    }



    private fun setAdapters() {

       binding.rvMainList.layoutManager = GridLayoutManager(requireActivity(), 1)
        binding.rvMainList.adapter = adapter


    }

    override fun onActionButton1Clicked(option: TaskItem) {
        val intent = Intent(requireActivity(), DynamicFormActivity::class.java)
        intent.putExtra("KEY_ID",option.poiId)
        startActivity(intent)
    }

    override fun onActionButton2Clicked(option: TaskItem) {

    }
}

