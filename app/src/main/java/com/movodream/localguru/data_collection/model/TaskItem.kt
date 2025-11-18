package com.movodream.localguru.data_collection.model




data class TaskItem(
    val poiId: Int,
    val poiName: String,
    val category: String,
    val taskPriority: String,
    val taskStatus: String,
    val progress: Int = 0,
    val revisionRequired: Boolean = false,
    val revisionMessage: String? = null,
    val subTasks: List<SubTask> = emptyList()
){
    data class SubTask(
        val id: Int,
        val iconRes: Int,
        val text: String
    )

}

