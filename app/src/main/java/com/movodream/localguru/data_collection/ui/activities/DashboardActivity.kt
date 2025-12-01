package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.core.R
import com.core.base.BaseActivity
import com.core.customviews.CustomDialogBuilder
import com.core.utils.DebugLog
import com.core.utils.PermissionUtils
import com.core.utils.Utils
import com.data.remote.model.AgentTaskResponse
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.ui.adapter.DashboardPagerAdapter
import com.movodream.localguru.databinding.ActivityDashboardBinding
import com.network.client.ResponseHandler
import com.network.model.ResponseData


class DashboardActivity : BaseActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var dashboardViewModel: DashboardViewModel

    private lateinit var permissionUtils: PermissionUtils

    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        permissions.toTypedArray()
    }
    var agentId: String? = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)
        binding.lifecycleOwner = this
        permissionUtils = PermissionUtils(this)
       // setAdapters()
        setCustomListener()
        setObserver()
         agentId = intent.getStringExtra("KEY_AGENT_ID")
        // agentId = "109918"
       callDashboardAPI()
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
            binding.bottomNav.selectItem(0)
            Toast.makeText(this@DashboardActivity,"Coming soon...", Toast.LENGTH_SHORT).show()

        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_report,
            "Reports"
        ) {
            //onSelectedTab(2)
            binding.bottomNav.selectItem(0)
            Toast.makeText(this@DashboardActivity,"Coming soon...", Toast.LENGTH_SHORT).show()

        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_profile,
            "Profile"
        ) {
            //onSelectedTab(3)
            binding.bottomNav.selectItem(0)
            Toast.makeText(this@DashboardActivity,"Coming soon...", Toast.LENGTH_SHORT).show()

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

        dashboardViewModel.dashboardResponse.observe(
            this,
            androidx.lifecycle.Observer { state ->
                if (state == null) {
                    return@Observer
                }

                when (state) {

                    is ResponseHandler.Loading -> {
                        Utils.showProgressDialog(this)
                    }
                    is ResponseHandler.OnFailed -> {
                        Utils.hideProgressDialog()

                        if (state.code == 401) {
                          //  Utils.showUnAuthAlertDialog(this)
                        } else {
                            state.message.let { m ->
                                DebugLog.e("Config Api Error Response : $m")


                                CustomDialogBuilder(this)
                                    .setTitle("Error")
                                    .setMessage(m)

                                    .setPositiveButton("Retry") {
                                        dashboardViewModel.callAgentDashboardAPI(agentId!!)

                                        }.setNegativeButton("Cancel"){
                                          finish()
                                    }
                                    }
                                    .setCancelable(false)
                                    .show()
                            }
                        }


                    is ResponseHandler.OnSuccessResponse<ResponseData<AgentTaskResponse>?> -> {

                        state.response?.let {
                            Utils.hideProgressDialog()
                         if(state.response!!.data!=null ){
                             binding.llDashboard.visibility = View.VISIBLE
                             setAdapters()
                             binding.dashboard = state.response!!.data
                          dashboardViewModel.mapDashboardData(state.response!!.data!!)

                             checkLocationPermissionAndStart()

                         }

                        }


                       Utils.hideProgressDialog()

                    }
                }
            })


    }

     fun callDashboardAPI(){
        binding.llDashboard.visibility = View.INVISIBLE
        dashboardViewModel.callAgentDashboardAPI(agentId!!)
    }
    private fun checkLocationPermissionAndStart() {

        if (permissionUtils.isLocationPermissionGranted(this)) {
            checkGps()
        } else {
            permissionUtils.request(requiredPermissions) { granted ->
                if (granted)   // Permission OK -> check GPS then start location fetch
                    checkGpsThenStartLocationFetch()
            }
        }
    }

    private fun checkGpsThenStartLocationFetch() {
        if (!permissionUtils.isGpsEnabled(this)) {
            showEnableGpsDialog()
        } else {
            fetchLocation()
        }
    }


    private fun checkGps() {
        if (!permissionUtils.isGpsEnabled(this)) {
            showEnableGpsDialog()
        } else {
            fetchLocation()
        }
    }



    private fun showEnableGpsDialog() {
        permissionUtils.request(requiredPermissions) { granted ->
            if (granted) {
                // Now check if location is turned ON
                permissionUtils.checkAndEnableLocationSettings { enabled ->
                    if (enabled) {
                        //  Start fetching location
                        fetchLocation()
                    } else {

                    }
                }
            }
        }
    }

    private fun fetchLocation(){
       dashboardViewModel.fetchAccurateLocation()

    }

}