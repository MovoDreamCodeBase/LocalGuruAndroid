package com.data.remote.model

data class AgentTaskResponse(
    val agentName: String,
    val agentId: String,
    val dataCollectionLocation: String,
    val taskSummary: List<TaskSummary>,
    val assignedPOIs: List<AssignedPOI>
)

data class TaskSummary(
    val taskName: String,
    val count: Int
)

data class AssignedPOI(
    val poiId: Int,
    val poiName: String,
    val category: String,
    val categoryId: Int,
    val taskPriority: String,
    val taskStatus: String,
    val progress: Int,
    val revisionRequired: Boolean,
    val contactNo: String,
    val latitude: String,
    val longitude: String,
    val revisionMessage: String?,
    val subTasks: List<SubTask>
)

data class SubTask(
    val iconUrl: String,
    val text: String
)
