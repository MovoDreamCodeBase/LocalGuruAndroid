package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.core.R
import com.core.base.BaseActivity
import com.core.constants.AppConstants
import com.core.customviews.CustomDialogBuilder
import com.core.customviews.MasterDownloadProgressDialog
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.core.utils.DebugLog
import com.core.utils.PermissionUtils
import com.core.utils.Utils
import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.RevisionDataResponse
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.DashboardViewModel
import com.movodream.localguru.data_collection.ui.adapter.DashboardPagerAdapter
import com.movodream.localguru.databinding.ActivityDashboardBinding
import com.movodream.localguru.login.ui.LoginActivity
import com.network.client.ResponseHandler
import com.network.model.ResponseData
import kotlinx.coroutines.launch
import java.io.File


class DashboardActivity : BaseActivity() {

    private lateinit var masterProgressDialog: MasterDownloadProgressDialog
    private  var isFromShare: Boolean = false
    private  var isFromDashboard: Boolean = false
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var dashboardViewModel: DashboardViewModel

    private lateinit var permissionUtils: PermissionUtils

    private lateinit var formData : FormSchema
    private lateinit var selectedPOI: TaskItem
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
        masterProgressDialog = MasterDownloadProgressDialog(this)

        // setAdapters()
        setCustomListener()
        setObserver()
         agentId = intent.getStringExtra("KEY_AGENT_ID")
        // agentId = "109918"
       callDashboardAPI()
    }

    override fun onResume() {
        super.onResume()

        if(dashboardViewModel.dashboardTasks.value!=null &&dashboardViewModel.dashboardTasks.value!!.isNotEmpty()) {
            val poiIds = dashboardViewModel.dashboardTasks.value?.map { it.poiId.toString() }
            dashboardViewModel.refreshDraftProgress(poiIds!!)
        }

        if(dashboardViewModel.filteredTasks.value!=null &&dashboardViewModel.filteredTasks.value!!.isNotEmpty()) {
            val poiIds = dashboardViewModel.filteredTasks.value?.map { it.poiId.toString() }
            dashboardViewModel.refreshTaskDraftProgress(poiIds!!)
        }
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
            onSelectedTab(2)
        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_report,
            "Reports"
        ) {
            //onSelectedTab(3)
            binding.bottomNav.selectItem(0)
            Toast.makeText(this@DashboardActivity,"Coming soon...", Toast.LENGTH_SHORT).show()

        }
        binding.bottomNav.addItem(
            com.movodream.localguru.R.drawable.ic_profile,
            "Profile"
        ) {
            //onSelectedTab(4)
            binding.bottomNav.selectItem(0)
            Toast.makeText(this@DashboardActivity,"Coming soon...", Toast.LENGTH_SHORT).show()

        }

        binding.bottomNav.selectItem(0)

        binding.cvProfile.setOnClickListener {
         showMenuTooltip(binding.cvProfile)
        }

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
                             binding.dashboard = state.response!!.data

                             lifecycleScope.launch {
                                 dashboardViewModel.mapDashboardData(state.response!!.data!!)
                             }

                             checkLocationPermissionAndStart()

                         }

                        }


                       Utils.hideProgressDialog()

                    }
                }
            })

        dashboardViewModel.revisionDataResponse.observe(
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

                                    .setPositiveButton("OK") {

                                    }
                            }
                                .setCancelable(false)
                                .show()
                        }
                    }


                    is ResponseHandler.OnSuccessResponse<ResponseData<RevisionDataResponse>?> -> {

                        state.response?.let {
                            Utils.hideProgressDialog()
                            if(state.response!!.data!=null  && state.response!!.data!!.poiDetails!=null&& state.response!!.data!!.poiDetails.isNotEmpty() ){

                                var galleryPhotos = ""
                                if(state.response!!.data!!.galleryPhotos!=null&&state.response!!.data!!.poiDetails.isNotEmpty()){
                                    galleryPhotos = state.response!!.data!!.galleryPhotos
                                }
                                dashboardViewModel.saveServerPoiDetailsAsDraft(
                                    poiId = selectedPOI.poiId.toString(),
                                    poiDetailsString = state.response!!.data!!.poiDetails,galleryPhotos // <-- String from API
                                ) {
                                   callDynamicFormScrren(true)
                                }

                            }else{
                                callDynamicFormScrren(true)
                            }

                        }


                        Utils.hideProgressDialog()

                    }
                }
            })

        dashboardViewModel.poiDataResponse.observe(
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

                                    .setPositiveButton("OK") {

                                    }
                            }
                                .setCancelable(false)
                                .show()
                        }
                    }


                    is ResponseHandler.OnSuccessResponse<ResponseData<RevisionDataResponse>?> -> {

                        state.response?.let {
                            Utils.hideProgressDialog()
                            if(state.response!!.data!=null ){

                                var galleryPhotos = ""
                                if(state.response!!.data!!.galleryPhotos!=null&&state.response!!.data!!.galleryPhotos.isNotEmpty()){
                                    galleryPhotos = state.response!!.data!!.galleryPhotos
                                }
                                val subPOIList = ArrayList(
                                    state.response?.data
                                        ?.subPoiRecords
                                        ?: emptyList()
                                )

                                if(isFromShare){
                                  dashboardViewModel.prepareShareText(state.response!!.data!!.poiDetails)
                                }else{
                                    val intent = Intent(this, PoiDetailsActivity::class.java)
                                    intent.putExtra("poiDetails", state.response!!.data!!.poiDetails)      // the "poiDetails" string
                                    intent.putExtra("galleryPhotos", galleryPhotos) // the "galleryPhotos" string
                                    intent.putExtra("KEY_POI", selectedPOI) // the "galleryPhotos" string
                                    intent.putParcelableArrayListExtra("KEY_SUB_POI", subPOIList) // the "galleryPhotos" string
                                    startActivity(intent)
                                }


                            }else{

                            }

                        }


                        Utils.hideProgressDialog()

                    }
                }
            })
        dashboardViewModel.shareText.observe(this) { text ->

            shareTextAsFile(this@DashboardActivity,text)

        }

        dashboardViewModel.masterState.observe(this) { state ->
            when (state) {

                is DashboardViewModel.MasterDownloadState.Loading -> {
                    masterProgressDialog.show()
                }

                is DashboardViewModel.MasterDownloadState.Progress -> {
                    masterProgressDialog.update(state.percent)
                }

                is DashboardViewModel.MasterDownloadState.Success -> {
                    masterProgressDialog.hide()
                    Toast.makeText(
                        this@DashboardActivity,
                        "Master data downloaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    dashboardViewModel.resetMasterState()
                    if(isFromDashboard){
                        //  After download → fetch from DB
                        val categoryId = selectedPOI.categoryId.toString()

                        dashboardViewModel.openCategoryFormIfAvailable(
                            categoryId = categoryId,
                            onReady = { schema ->
                                openDynamicForm(schema, selectedPOI)
                            },
                            onNeedDownload = {
                                // refresh master
                            }
                        )

                    }

                }

                is DashboardViewModel.MasterDownloadState.Error -> {
                    masterProgressDialog.hide()
                    Toast.makeText(
                        this@DashboardActivity,
                        state.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    dashboardViewModel.resetMasterState()
                }

                else -> { /* Idle - ignore */ }
            }
        }



    }

    private fun callDynamicFormScrren(bool: Boolean) {
        val intent = Intent(this@DashboardActivity, DynamicFormActivity::class.java)
        intent.putExtra("KEY_SCHEMA", formData)
        intent.putExtra("KEY_POI", selectedPOI)
        intent.putExtra("KEY_IS_REVISION",bool)
        launcherSelect.launch(intent)

    }

    fun callDashboardAPI(){
        binding.llDashboard.visibility = View.INVISIBLE
        dashboardViewModel.callAgentDashboardAPI(agentId!!)
    }

    fun callRevisionDataAPI(data: FormSchema, selectedPOI: TaskItem?) {
       formData = data
        if (selectedPOI != null) {
            this.selectedPOI = selectedPOI
        }
        dashboardViewModel.isPoiDraftAvailable(""+selectedPOI!!.poiId) { exists ->

            if (exists) {
                // Draft exists → load it
                callDynamicFormScrren(true)
            } else {
                // No draft → call API
                dashboardViewModel.callRevisionDataAPI(agentId!!,""+selectedPOI!!.poiId)
            }
        }


    }

    fun callPOIDetailsAPI(selectedPOI: TaskItem,isFromShare : Boolean){
        this.isFromShare = isFromShare
        this.selectedPOI = selectedPOI
        dashboardViewModel.callPOIDetails(agentId!!,""+selectedPOI!!.poiId)
    }

    fun callAddPOIScreen(data: FormSchema, isFromPOI: Boolean) {
        if(dashboardViewModel.tasks.value!=null && dashboardViewModel.tasks.value!!.isNotEmpty()){
            val assignedTaskList : List<TaskItem> = dashboardViewModel.tasks.value!!
           val completedTaskList =  assignedTaskList.filter { it.taskStatus == AppConstants.TAB_COMPLETED }

            if(isFromPOI){
                val intent =
                    Intent(this@DashboardActivity, AddPoiSubPoiFormActivity::class.java)
                intent.putExtra("KEY_SCHEMA", data)
                intent.putExtra("KEY_IS_ADD_POI", isFromPOI)
                intent.putParcelableArrayListExtra(
                    "KEY_POI",
                    ArrayList(assignedTaskList)
                )

                launcherSelect.launch(intent)
            }else {
                if (completedTaskList != null && completedTaskList.isNotEmpty()) {
                    val intent =
                        Intent(this@DashboardActivity, AddPoiSubPoiFormActivity::class.java)
                    intent.putExtra("KEY_SCHEMA", data)
                    intent.putExtra("KEY_IS_ADD_POI", isFromPOI)
                    intent.putParcelableArrayListExtra(
                        "KEY_POI",
                        ArrayList(completedTaskList)
                    )

                    launcherSelect.launch(intent)
                } else {

                    CustomDialogBuilder(this)
                        .setTitle("Error")
                        .setMessage(
                            "Please complete at least one POI before adding Sub-POI details"
                        )
                        .setPositiveButton("OK") {


                        }

                        .setCancelable(false)
                        .show()
                }
            }

        }
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
    private val launcherSelect = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            binding.bottomNav.selectItem(0)
         callDashboardAPI()
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share POI Details"))
    }

    private fun shareTextAsFile(context: Context, content: String) {
        try {
            var poiName = ""
            if (selectedPOI != null && selectedPOI.poiName.isNotBlank()) {
                poiName = selectedPOI.poiName
                    .trim()
                    .replace(" ", "_")
                    .replace(Regex("[^A-Za-z0-9_]"), "")
                    .lowercase()
            }

            val fileName = "${poiName.ifBlank { "poi_details" }}.txt"
            val file = File(context.cacheDir, fileName)

            // THE FIX: Decode wrongly encoded text
            val fixedContent = try {
                String(content.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            } catch (e: Exception) {
                content
            }

            file.writeText(fixedContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "POI Details")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share POI Details"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }





    private fun showMenuTooltip(anchorView: View) {
        val popupView = layoutInflater.inflate(com.movodream.localguru.R.layout.popup_logout, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 20f

        // Convert dp to px
        val marginTop = dpToPx(8)
        val marginEnd = dpToPx(8)

        // Show popup with margin offsets
        popupWindow.showAsDropDown(
            anchorView,
            -marginEnd,   // move left from end
            marginTop     // move down from top
        )

        popupView.findViewById<TextView>(com.movodream.localguru.R.id.tvLogout).setOnClickListener {
            popupWindow.dismiss()
            performLogout()
        }

        popupView.findViewById<TextView>(com.movodream.localguru.R.id.tvMasterRefresh).setOnClickListener {
            popupWindow.dismiss()
            if(Utils.isNetworkAvailable(this)) {
                this.isFromDashboard = false;
                dashboardViewModel.refreshCategoryMaster()
            }else{
                Toast.makeText(this@DashboardActivity,"You're offline. Please connect to the internet to continue.", Toast.LENGTH_SHORT).show()

            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }


    private fun performLogout() {
        MyPreference.setValueString(PrefKey.PHONE_NUMBER,"")
        MyPreference.setValueString(PrefKey.LOGIN_DATE,"")
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

    }

    fun onPOISelected(isFromDashboard: Boolean, option: TaskItem){
        selectedPOI = option
        this.isFromDashboard = isFromDashboard


        val categoryId = selectedPOI.categoryId.toString()

        dashboardViewModel.openCategoryFormIfAvailable(
            categoryId = categoryId,

            onReady = { schema ->
                openDynamicForm(schema, selectedPOI)
            },

            onNeedDownload = {
                // Trigger master download
                dashboardViewModel.refreshCategoryMaster()
            }
        )
    }
    private fun openDynamicForm(
        categoryDataSchema: FormSchema,
        selectedPOI: TaskItem
    ) {
        val intent = Intent(
            this@DashboardActivity,
            DynamicFormActivity::class.java
        )
        intent.putExtra("KEY_SCHEMA", categoryDataSchema)
        intent.putExtra("KEY_POI", selectedPOI)
        launcherDynamicForm.launch(intent)
    }
    private val launcherDynamicForm = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            binding.bottomNav.selectItem(0)
            callDashboardAPI()
        }
    }

}