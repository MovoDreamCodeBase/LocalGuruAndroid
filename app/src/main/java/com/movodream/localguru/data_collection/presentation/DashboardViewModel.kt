package com.movodream.localguru.data_collection.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.constants.AppConstants
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.core.utils.Utils
import com.data.remote.model.AgentTaskResponse
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.SummaryItem
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.repository.CategoryResult
import com.movodream.localguru.data_collection.repository.DashboardRepository
import com.network.client.ResponseHandler
import com.network.model.ResponseData
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository()

    private val _tasks = MutableLiveData<List<TaskItem>>()
    val tasks: LiveData<List<TaskItem>> = _tasks

    private val _dashboardTasks = MutableLiveData<List<TaskItem>>()
    val dashboardTasks: LiveData<List<TaskItem>> = _dashboardTasks

    private val _summaryItems = MutableLiveData<List<SummaryItem>>()
    val summaryItems: LiveData<List<SummaryItem>> = _summaryItems

    private val _filteredTasks = MutableLiveData<List<TaskItem>>()
    val filteredTasks: LiveData<List<TaskItem>> = _filteredTasks

    private val _selectedTab = MutableLiveData<Int>()
    val selectedTab: LiveData<Int> = _selectedTab

    // API response holder
    var dashboardResponse =
        MutableLiveData<ResponseHandler<ResponseData<AgentTaskResponse>?>>()

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }
    init {
        _tasks.value = emptyList()
        _dashboardTasks.value = emptyList()
        _filteredTasks.value = emptyList()
        _summaryItems.value = emptyList()
    }




    // --------------------------------------------------------
    //  Convert AgentTaskResponse → TaskItem + SummaryItem
    // --------------------------------------------------------
    fun mapDashboardData(data: AgentTaskResponse) {
        // Summary mapping

        _summaryItems.value = data.taskSummary.map { item ->

            when (item.taskName.lowercase()) {

                "pending" -> SummaryItem(
                    iconRes = R.drawable.ic_task,
                    bgRes = com.core.R.drawable.bg_pending_tasks,
                    count = item.count,
                    label = "Pending Tasks"
                )

                "completed" -> SummaryItem(
                    iconRes = R.drawable.ic_circle,
                    bgRes = com.core.R.drawable.bg_completed_today,
                    count = item.count,
                    label = "Completed Today"
                )

                "revision", "need revision", "revision required" -> SummaryItem(
                    iconRes = com.core.R.drawable.ic_revision,
                    bgRes = com.core.R.drawable.bg_need_revision,
                    count = item.count,
                    label = "Need Revision"
                )

                "verified", "verified data" -> SummaryItem(
                    iconRes = R.drawable.ic_reward,
                    bgRes = com.core.R.drawable.bg_verified_data,
                    count = item.count,
                    label = "Verified Data"
                )

                else -> SummaryItem(
                    iconRes = R.drawable.ic_task,
                    bgRes = com.core.R.drawable.bg_pending_tasks,
                    count = item.count,
                    label = item.taskName
                )
            }
        }


        val mappedTasks = data.assignedPOIs.map { poi ->


            // 🔥 Determine Priority Based on Rules
            val computedPriority = when {
                poi.revisionRequired -> AppConstants.STATUS_REVISION_NEEDED
                poi.taskStatus == "Pending" -> AppConstants.STATUS_NOT_STARTED
                poi.taskStatus == "Completed" -> AppConstants.STATUS_COMPLETED
                poi.taskStatus == "In Progress" -> AppConstants.STATUS_HIGH_PRIORITY
                else -> AppConstants.STATUS_NOT_STARTED
            }

            TaskItem(
                poiId = poi.poiId,
                poiName = poi.poiName,
                category = poi.category,
                categoryId = poi.categoryId.toString(),
               // categoryId = "RELIGIOUS_SITES",

                // 🔥 Replacing API value with computed priority
                taskPriority = computedPriority,

                taskStatus = poi.taskStatus,
                progress = poi.progress,
                revisionRequired = poi.revisionRequired,
                revisionMessage = poi.revisionMessage ?: "",
                contactNo = poi.contactNo,
                latitude = poi.latitude,
                longitude = poi.longitude,
                agentName = data.agentName,
                agentId = data.agentId,
                agentLocation = data.dataCollectionLocation,

                subTasks = poi.subTasks.mapIndexed { index, st ->
                    TaskItem.SubTask(
                        id = index + 1,
                        iconRes = com.movodream.localguru.R.drawable.ic_circle,
                        text = st.text
                    )
                }
            )
        }

        _tasks.value = mappedTasks
        _filteredTasks.value = mappedTasks
        filterDashboardTasks()
    }

    // --------------------------
    // 🔍 Filters
    // --------------------------
    fun filterTasks(tab: String) {
        val all = _tasks.value ?: return
        _filteredTasks.value = when (tab) {
            AppConstants.TAB_PENDING -> all.filter { it.taskStatus == "Pending" }
            AppConstants.TAB_COMPLETED -> all.filter { it.taskStatus == "Completed" }
            AppConstants.TAB_REVISION -> all.filter { it.taskStatus == AppConstants.TAB_REVISION }
            AppConstants.TAB_IN_PROGRESS -> all.filter { it.taskStatus == "In Progress" }
            else -> all
        }
    }

    fun filterDashboardTasks() {
        val all = _tasks.value ?: return
        _dashboardTasks.value = all.filter { it.taskStatus != AppConstants.TAB_COMPLETED }
    }

    fun onClickViewAll() {
        _selectedTab.value = 1
    }

    // --------------------------
    // Category Actions
    // --------------------------
    private val _categoryState = MutableLiveData<CategoryResult?>()
    val categoryState: LiveData<CategoryResult?> get() = _categoryState

    private val _categoryCaller = MutableLiveData<String?>()
    val categoryCaller: LiveData<String?> = _categoryCaller

    fun setCaller(tag: String) = run { _categoryCaller.value = tag }
    fun clearCaller() = run { _categoryCaller.value = null }

    fun loadCategory(categoryId: String) {
        if (categoryId.isBlank()) {
            _categoryState.value = CategoryResult.Error("Invalid category id")
            return
        }

        _categoryState.value = CategoryResult.Loading

        viewModelScope.launch {
            val result = repository.getCategoryDetails(categoryId)
            _categoryState.postValue(result)
        }
    }

    fun resetCategoryState() {
        _categoryState.value = null
    }

    fun callAgentDashboardAPI(agentId : String) {
        viewModelScope.launch(Utils.coroutineContext) {
            dashboardResponse.value = ResponseHandler.Loading
            dashboardResponse.value = repository.merchantDashboard(agentId)
        }
    }

    fun fetchAccurateLocation() {




        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .build()

        try {
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { loc ->
                    if (loc != null && loc.longitude!=null) {
                        MyPreference.setValueString(PrefKey.LATITUDE,""+loc.latitude)
                        MyPreference.setValueString(PrefKey.LONGITUDE,""+loc.longitude)
                    } else {

                    }
                }
                .addOnFailureListener {

                }
        } catch (e: SecurityException) {
            // permission not available

        }
    }
}
