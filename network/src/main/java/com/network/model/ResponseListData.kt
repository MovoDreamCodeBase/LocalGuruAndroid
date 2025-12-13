package com.network.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName


open class ResponseListData<T> {

    @SerializedName("statusCode")
    @Expose
    var code: Int? = 0

    @SerializedName("success")
    @Expose
    var isSuccess: Boolean? = false

    @SerializedName("message")
    @Expose
    var message: String? = ""

    @SerializedName("data")
    @Expose
    var data: ArrayList<T>? = null

}
