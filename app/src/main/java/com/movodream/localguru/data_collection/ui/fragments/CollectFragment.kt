package com.movodream.localguru.data_collection.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.core.utils.Utils
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.ui.activities.DashboardActivity
import com.movodream.localguru.databinding.FragmentCollectBinding
import kotlin.getValue


class CollectFragment : Fragment() {

    private var _binding: FragmentCollectBinding? = null
    private val binding get() = _binding!!
    private  var isFromPOI: Boolean = false
    private val viewModel: DashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewLifecycleOwnerLiveData.observe(this) { owner ->
            if (owner != null) observeCategoryState(owner)
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddPoi.setOnClickListener {
            navigateToAddPoi()
        }

        binding.btnAddSubPoi.setOnClickListener {
            navigateToAddSubPoi()
        }
    }
    private fun observeCategoryState(owner: androidx.lifecycle.LifecycleOwner) {
        viewModel.categoryState.observe(owner) { state ->

            val caller = viewModel.categoryCaller.value
            if (caller != "COLLECT") return@observe   // Ignore Dashboard events

            when (state) {

                CategoryResult.Loading -> {
                    Utils.showProgressDialog(requireActivity())
                }

                is CategoryResult.Success -> {
                    Utils.hideProgressDialog()


                    (activity as? DashboardActivity)?.callAddPOIScreen(state.data,isFromPOI)

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

    private fun navigateToAddPoi() {
        isFromPOI = true
        viewModel.setCaller("COLLECT")
        viewModel.loadCategory("ADD_POI")
    }

    private fun navigateToAddSubPoi() {
        isFromPOI = false

        viewModel.setCaller("COLLECT")
        viewModel.loadCategory("SUB_POI")
    }

}
