package com.data.remote.model

data class RevisionDataResponse(
    val poiId: String,
    val agentId: String,
    val poiDetails: String,   // JSON string (parse separately)
    val galleryPhotos: String,   // JSON string (parse separately)
    val createdDate: String
)
