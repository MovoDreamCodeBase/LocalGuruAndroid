package com.movodream.localguru.data_collection.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.ui.activities.DynamicFormActivity
import com.movodream.localguru.data_collection.ui.adapter.SummaryAdapter
import com.movodream.localguru.data_collection.ui.adapter.TaskAdapter
import com.movodream.localguru.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment(), TaskAdapter.TasksClickListener {

    private  var selectedPOI: Int = -1;
    private lateinit var binding: FragmentDashboardBinding
    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private val adapter = TaskAdapter(this)
    private val adapterSummary = SummaryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //  FIX: Attach observer ONCE using viewLifecycleOwner lifecycle
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner != null) {
                observeCategoryState(owner)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = dashboardViewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setObserver()
        setAdapters()
    }

    private fun setObserver() {

        dashboardViewModel.summaryItems.observe(viewLifecycleOwner) {
            adapterSummary.submitList(it)
        }

        dashboardViewModel.dashboardTasks.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }
    }

    //  OBSERVER attached only once per Fragment instance
    private fun observeCategoryState(owner: androidx.lifecycle.LifecycleOwner) {
        dashboardViewModel.categoryState.observe(owner) { state ->

            val caller = dashboardViewModel.categoryCaller.value
            if (caller != "DASHBOARD") return@observe   // ignore events from TaskFragment

            when (state) {
                CategoryResult.Loading -> {
                  //  binding.progressBar.visibility = View.VISIBLE
                }

                is CategoryResult.Success -> {
                   // binding.progressBar.visibility = View.GONE

                    Log.d("Dashboard", "Dashboard SUCCESS fired once")

                    val intent = Intent(requireActivity(), DynamicFormActivity::class.java)
                    intent.putExtra("KEY_SCHEMA", state.data)
                    intent.putExtra("KEY_POI_ID", selectedPOI)
                    startActivity(intent)

                    dashboardViewModel.resetCategoryState()
                    dashboardViewModel.clearCaller()
                }

                CategoryResult.NotFound -> {
                   // binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireActivity(), "Category not found", Toast.LENGTH_SHORT).show()

                    dashboardViewModel.resetCategoryState()
                    dashboardViewModel.clearCaller()
                }

                is CategoryResult.Error -> {
                  //  binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireActivity(), state.message, Toast.LENGTH_SHORT).show()

                    dashboardViewModel.resetCategoryState()
                    dashboardViewModel.clearCaller()
                }

                null -> { /* ignore reset state */ }
            }
        }
    }

    private fun setAdapters() {
        binding.rvSummary.adapter = adapterSummary
        binding.rvMainList.layoutManager = GridLayoutManager(requireActivity(), 1)
        binding.rvMainList.adapter = adapter
    }

    override fun onActionButton1Clicked(option: TaskItem) {
        selectedPOI = option.poiId
        dashboardViewModel.setCaller("DASHBOARD")
        dashboardViewModel.loadCategory(option.categoryId)
    }

    override fun onActionButton2Clicked(option: TaskItem) {}
}
