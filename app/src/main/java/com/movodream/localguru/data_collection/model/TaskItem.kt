package com.movodream.localguru.data_collection.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TaskItem(
    val poiId: Int,
    val poiName: String,
    val category: String,
    var categoryId: String,
    val taskPriority: String,
    val taskStatus: String,
    val progress: Int = 0,
    val revisionRequired: Boolean = false,
    val revisionMessage: String? = null,
    val contactNo: String,
    val latitude: String,
    val longitude: String,
    val agentName: String,
    val agentLocation: String,
    val agentId: String,
    val subTasks: List<SubTask> = emptyList()
): Parcelable{
    @Parcelize
    data class SubTask(
        val id: Int,
        val iconRes: Int,
        val text: String
    ): Parcelable

}

