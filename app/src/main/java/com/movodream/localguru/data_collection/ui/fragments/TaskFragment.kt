package com.movodream.localguru.data_collection.ui.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.core.constants.AppConstants
import com.core.utils.Utils
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import com.movodream.localguru.data_collection.ui.activities.DynamicFormActivity
import com.movodream.localguru.data_collection.ui.adapter.TaskAdapter
import com.movodream.localguru.databinding.FragmentTaskBinding

class TaskFragment : Fragment(), TaskAdapter.TasksClickListener {

    private  var selectedPOI: TaskItem? = null
    private lateinit var binding: FragmentTaskBinding
    private val viewModel: DashboardViewModel by activityViewModels()
    private val adapter = TaskAdapter(this)
    private lateinit var tabButtons: List<AppCompatButton>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner != null) observeCategoryState(owner)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTaskBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setAdapters()
        setObserver()
    }

    private fun observeCategoryState(owner: androidx.lifecycle.LifecycleOwner) {
        viewModel.categoryState.observe(owner) { state ->

            val caller = viewModel.categoryCaller.value
            if (caller != "TASK") return@observe   // Ignore Dashboard events

            when (state) {

                CategoryResult.Loading -> {
                    Utils.showProgressDialog(requireActivity())
                }

                is CategoryResult.Success -> {
                    Utils.hideProgressDialog()

                    Log.d("TaskFragment", "TASK SUCCESS fired once")

                    if(selectedPOI!!.revisionRequired){
                        (activity as? DashboardActivity)?.callRevisionDataAPI(state.data,selectedPOI)
                    }else{
                        val intent = Intent(requireActivity(), DynamicFormActivity::class.java)
                        intent.putExtra("KEY_SCHEMA", state.data)
                        intent.putExtra("KEY_POI", selectedPOI)
                        startActivityForResult(intent,101)
                    }

                    viewModel.resetCategoryState()
                    viewModel.clearCaller()
                }

                CategoryResult.NotFound -> {
                    Utils.hideProgressDialog()
                    Toast.makeText(requireActivity(),"Category not found", Toast.LENGTH_SHORT).show()

                    viewModel.resetCategoryState()
                    viewModel.clearCaller()
                }

                is CategoryResult.Error -> {
                    Utils.hideProgressDialog()
                    Toast.makeText(requireActivity(), state.message, Toast.LENGTH_SHORT).show()

                    viewModel.resetCategoryState()
                    viewModel.clearCaller()
                }
                null -> { /* ignore reset state */ }
            }
        }
    }

    private fun setupTabs() {
        tabButtons = listOf(
            binding.tabAll,
            binding.tabPending,
            binding.tabProgress,
            binding.tabCompleted,
            binding.tabRevision
        )

        tabButtons.forEach { btn ->
            btn.setOnClickListener {
                selectTab(btn)
                viewModel.filterTasks(btn.text.toString())
            }
        }

        selectTab(binding.tabAll)
    }

    private fun selectTab(selected: AppCompatButton) {
        tabButtons.forEach { it.isSelected = it == selected }
    }

    private fun setObserver() {
        viewModel.filteredTasks.observe(viewLifecycleOwner, Observer { list ->
            adapter.submitList(list)
        })
    }

    private fun setAdapters() {
        binding.rvMainList.layoutManager = GridLayoutManager(requireActivity(), 1)
        binding.rvMainList.adapter = adapter
    }

    override fun onActionButton1Clicked(option: TaskItem) {
        selectedPOI = option
        if(option.taskStatus == AppConstants.TAB_COMPLETED){
            (activity as? DashboardActivity)?.callPOIDetailsAPI(selectedPOI!!,false)
        }else {
            viewModel.setCaller("TASK")
            viewModel.loadCategory(option.categoryId)
        }
    }

    override fun onActionButton2Clicked(option: TaskItem) {
        selectedPOI = option
        if(option.taskStatus == AppConstants.TAB_COMPLETED){
            (activity as? DashboardActivity)?.callPOIDetailsAPI(selectedPOI!!,true)
        }
    }

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
