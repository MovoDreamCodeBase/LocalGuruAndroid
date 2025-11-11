package com.movodream.localguru.presentation


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.core.constants.AppConstants
import com.movodream.localguru.R
import com.movodream.localguru.model.SummaryItem
import com.movodream.localguru.model.TaskItem


class DashboardViewModel : ViewModel() {

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

    init {
        _summaryItems.value = listOf(
            SummaryItem(
                R.drawable.ic_task,
                com.core.R.drawable.bg_pending_tasks,
                12,
                "Pending Tasks"
            ),
            SummaryItem(
                R.drawable.ic_circle,
                com.core.R.drawable.bg_completed_today,
                8,
                "Completed Today"
            ),
            SummaryItem(
                com.core.R.drawable.ic_revision,
                com.core.R.drawable.bg_need_revision,
                3,
                "Need Revision"
            ),
            SummaryItem(
                R.drawable.ic_reward,
                com.core.R.drawable.bg_verified_data,
                45,
                "Verified Data"
            )
        )
        loadDemoTasks()
        filterDashboardTasks()
    }


    private fun loadDemoTasks() {
        val demolist = listOf(
            //  High Priority
            TaskItem(
                poiId = 1,
                poiName = "Golden Temple Documentation",
                category = "Religious Site",
                taskPriority = "High Priority",
                taskStatus = "Pending",

                progress = 40,
                subTasks = listOf(
                    TaskItem.SubTask(1, R.drawable.ic_location, "Golden Temple Area"),
                    TaskItem.SubTask(2, R.drawable.ic_circle, "Due Today 5 PM"),
                    TaskItem.SubTask(3, R.drawable.ic_circle, "12 Photos Required"),
                    TaskItem.SubTask(4, R.drawable.ic_circle, "Detailed Description")
                )
            ),
            //  Not Started
            TaskItem(
                poiId = 2,
                poiName = "Kesar Da Dhaba Data Collection",
                category = "Restaurant",
                taskPriority = "Not Started",
                taskStatus = "Pending",
                progress = 0,
                subTasks = listOf(
                    TaskItem.SubTask(1, R.drawable.ic_location, "Lawrence Road"),
                    TaskItem.SubTask(2, R.drawable.ic_circle, "Due Tomorrow"),
                    TaskItem.SubTask(3, R.drawable.ic_circle, "Signature Dishes"),
                    TaskItem.SubTask(4, R.drawable.ic_circle, "Price Range")
                )
            ),
            //  Revision Needed
            TaskItem(
                poiId = 3,
                poiName = "Hall Bazaar Shopping Data",
                category = "Market",
                taskPriority = "Revision Needed",
                taskStatus = "Revision",
                revisionRequired = true,
                revisionMessage = "Missing product photos and pricing details. Please add at least 5 product photos and update price ranges.",
                subTasks = listOf(
                    TaskItem.SubTask(1, R.drawable.ic_location, "Hall Bazaar"),
                    TaskItem.SubTask(2, R.drawable.ic_clock, "Due Yesterday"),
                    TaskItem.SubTask(3, R.drawable.ic_circle, "Photos Missing"),
                    TaskItem.SubTask(4, R.drawable.ic_circle, "Need Updated Prices")
                )
            ),
            // Completed
            TaskItem(
                poiId = 4,
                poiName = "Jallianwala Bagh Documentation",
                category = "Historical Site",
                taskPriority = "Completed",
                taskStatus = "Completed",
                revisionRequired = false,
                revisionMessage = "",
                progress = 100,
                subTasks = listOf(
                    TaskItem.SubTask(1, R.drawable.ic_location, "Near goldan temple"),
                    TaskItem.SubTask(2, R.drawable.ic_clock, "Completed"),
                )
            )
        )

        _tasks.value = demolist
        _filteredTasks.value = demolist
    }

    fun filterTasks(tab: String) {
        val all = _tasks.value ?: return
        _filteredTasks.value = when (tab) {
            AppConstants.TAB_PENDING -> all.filter { it.taskStatus == AppConstants.TAB_PENDING }
            AppConstants.TAB_COMPLETED -> all.filter { it.taskStatus == AppConstants.TAB_COMPLETED }
            AppConstants.TAB_REVISION -> all.filter { it.taskStatus == AppConstants.TAB_REVISION }
            AppConstants.TAB_IN_PROGRESS -> all.filter { it.progress in 1..99 && !it.revisionRequired }
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
}