package com.movodream.localguru.data_collection.ui.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.core.utils.Utils
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import com.movodream.localguru.data_collection.ui.activities.DynamicFormActivity
import com.movodream.localguru.data_collection.ui.adapter.SummaryAdapter
import com.movodream.localguru.data_collection.ui.adapter.TaskAdapter
import com.movodream.localguru.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment(), TaskAdapter.TasksClickListener {

    private  var selectedPOI: TaskItem? = null
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
                    Utils.showProgressDialog(requireActivity())
                }

                is CategoryResult.Success -> {
                    Utils.hideProgressDialog()

                    Log.d("Dashboard", "Dashboard SUCCESS fired once")
                   if(selectedPOI!!.revisionRequired){
                       (activity as? DashboardActivity)?.callRevisionDataAPI(state.data,selectedPOI)
                   }else{
                       val intent = Intent(requireActivity(), DynamicFormActivity::class.java)
                       intent.putExtra("KEY_SCHEMA", state.data)
                       intent.putExtra("KEY_POI", selectedPOI)
                       startActivityForResult(intent,101)
                   }


                    dashboardViewModel.resetCategoryState()
                    dashboardViewModel.clearCaller()
                }

                CategoryResult.NotFound -> {
                    Utils.hideProgressDialog()
                    Toast.makeText(requireActivity(), "Category not found", Toast.LENGTH_SHORT).show()

                    dashboardViewModel.resetCategoryState()
                    dashboardViewModel.clearCaller()
                }

                is CategoryResult.Error -> {
                    Utils.hideProgressDialog()
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
        selectedPOI = option
        dashboardViewModel.setCaller("DASHBOARD")
       // dashboardViewModel.loadCategory(option.categoryId)
        (activity as? DashboardActivity)?.onPOISelected(true,option)
    }

    override fun onActionButton2Clicked(option: TaskItem) {}
    override fun onActionCallClicked(option: TaskItem) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:${option.contactNo}")
        requireContext().startActivity(intent)
    }

    override fun onActionDirectionClicked(option: TaskItem) {
        val gmmIntentUri = Uri.parse("google.navigation:q=${option.latitude},${option.longitude}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            // Google Maps not installed → Open in any map app / browser
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${option.latitude},${option.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode==101 && resultCode == Activity.RESULT_OK){
            (activity as? DashboardActivity)?.callDashboardAPI()
        }
    }
}
