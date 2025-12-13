package com.data.remote.model

data class DeletePhotoRequest(
    val poiId: String,
    val agentId: String,
    val imageIDs: List<Int>
)

