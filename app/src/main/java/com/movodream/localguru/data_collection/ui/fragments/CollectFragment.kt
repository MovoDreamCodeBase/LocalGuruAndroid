package com.movodream.localguru.data_collection.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.movodream.localguru.R
import com.movodream.localguru.databinding.FragmentCollectBinding


class CollectFragment : Fragment() {

    private var _binding: FragmentCollectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectBinding.inflate(inflater, container, false)
        return binding.root
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

    private fun navigateToAddPoi() {

    }

    private fun navigateToAddSubPoi() {

    }

}
