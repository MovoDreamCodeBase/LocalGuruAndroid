package com.network.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName



class ErrorWrapper {
    @SerializedName("status")
    @Expose
    var statusCode: Int? = 0

    @SerializedName("message")
    @Expose
    var message: String? = null


    @SerializedName("ERRORCODE")
    @Expose
    var ERRORCODE: String= ""


    @SerializedName("MESSAGE")
    @Expose
    var msg: String= ""

}
