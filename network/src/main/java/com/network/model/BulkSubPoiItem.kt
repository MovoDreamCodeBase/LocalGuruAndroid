package com.network.model

import com.google.gson.annotations.SerializedName

data class BulkSubPoiItem(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String?,

    @SerializedName("data")
    val data: Int?
)

