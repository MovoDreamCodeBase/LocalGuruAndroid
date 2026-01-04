package com.network.client

import android.accounts.NetworkErrorException
import com.core.utils.DebugLog
import com.google.gson.Gson
import com.network.model.BulkSubPoiItem
import com.network.model.ErrorWrapper
import com.network.model.HttpErrorCode
import com.network.model.ResponseData
import com.network.model.ResponseListData
import okhttp3.internal.http2.ConnectionShutdownException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class BaseRepository {

    suspend fun <T : Any> makeAPICall(call: suspend () -> Response<ResponseData<T>>): ResponseHandler<ResponseData<T>?> {
        try {
            val response = call.invoke()
            DebugLog.e("Response Code : ${response.code()}")
            when {
//                response.code() == 201 -> return ResponseHandler.OnSuccessResponse(response.body())
                response.code() in 200..300 -> {
                    DebugLog.e("Response Code 200 : ${response.code()}")
                    val body = response.body()
                    return ResponseHandler.OnSuccessResponse(response.body())
                }
                response.code() in 300..400 -> {
                    DebugLog.e("Response Code : 300..400 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()
                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 400..500 -> {
                    DebugLog.e("Response Code : 400..500 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()

                    if (response.code() == 401) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.UNAUTHORIZED.code,
                            "Session Expired, Please login again",
                            responseWrapper?.statusCode.toString()
                        )
                    }

                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }

                response.code() in 500..600 -> {
                    DebugLog.e("Response Code : 500..600 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()


                    if (errMsg.isEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }

                else -> {
                    DebugLog.e("Response Code : ${response.code()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val message = if (responseWrapper?.statusCode == 422) {
                        responseWrapper.message
                    } else {
                        responseWrapper?.message
                    }
                    return if (message.isNullOrEmpty()) {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.EMPTY_RESPONSE.code,
                            message!!,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            message,
                            responseWrapper?.statusCode.toString()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return if (e is ConnectionShutdownException || e is UnknownHostException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NO_CONNECTION.code,
                    "Internet connection is lost, Please try again later",
                    ""
                )
            } else if (e is SocketTimeoutException || e is IOException || e is NetworkErrorException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NOT_DEFINED.code,
                    "Server is timed out, Please try again later",
                    ""
                )
            } else {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NOT_DEFINED.code,
                    "Error occurred, Please try again later",
                    ""
                )
            }
        }
    }

    suspend fun <T : Any> makeAPICallForList(call: suspend () -> Response<ResponseListData<T>>): ResponseHandler<ResponseListData<T>?> {

        try {

            val response = call.invoke()

            when {

                response.code() in 200..300 -> {
                    DebugLog.e("Response Code 200 : ${response.code()}")
                    val body = response.body()
                    return ResponseHandler.OnSuccessResponse(response.body())
                }
                response.code() in 300..400 -> {
                    DebugLog.e("Response Code : 300..400 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()
                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 400..500 -> {
                    DebugLog.e("Response Code : 400..500 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()

                    if (response.code() == 401) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.UNAUTHORIZED.code,
                            "Session Expired, Please login again",
                            responseWrapper?.statusCode.toString()
                        )
                    }

                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 500..600 -> {
                    DebugLog.e("Response Code : 500..600 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()


                    if (errMsg.isEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                else -> {
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper =
                        Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val message  = if (responseWrapper.statusCode == 422) {

                        responseWrapper?.message

                    } else {
                        responseWrapper?.message
                    }
                    return if (message.isNullOrEmpty()) {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.EMPTY_RESPONSE.code,
                            message!!,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            message,
                            responseWrapper?.statusCode.toString()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return if (e is ConnectionShutdownException || e is UnknownHostException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NO_CONNECTION.code, "", "")
            } else if (e is SocketTimeoutException || e is IOException || e is NetworkErrorException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NOT_DEFINED.code, "", "")
            } else {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NOT_DEFINED.code, "", "")
            }
        }
    }

    suspend fun <T : Any> makeAPICallForListTemp(call: suspend () -> Response<ResponseListData<T>>): ResponseHandler<ResponseListData<T>?> {

        try {

            val response = call.invoke()

            when {

                response.code() in 200..300 -> {
                    DebugLog.e("Response Code 200 : ${response.code()}")
                    val body = response.body()
                    if(body!=null && body.isSuccess == true){
                        return ResponseHandler.OnSuccessResponse(response.body())
                    }else{
                        return ResponseHandler.OnFailed(
                            500,
                            body?.message ?: "Invalid Response",
                            "500"
                        )
                    }


                }
                response.code() in 300..400 -> {
                    DebugLog.e("Response Code : 300..400 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()
                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 400..500 -> {
                    DebugLog.e("Response Code : 400..500 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()

                    if (response.code() == 401) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.UNAUTHORIZED.code,
                            "Session Expired, Please login again",
                            responseWrapper?.statusCode.toString()
                        )
                    }

                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 500..600 -> {
                    DebugLog.e("Response Code : 500..600 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()


                    if (errMsg.isEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                else -> {
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper =
                        Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val message  = if (responseWrapper.statusCode == 422) {

                        responseWrapper?.message

                    } else {
                        responseWrapper?.message
                    }
                    return if (message.isNullOrEmpty()) {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.EMPTY_RESPONSE.code,
                            message!!,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            message,
                            responseWrapper?.statusCode.toString()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return if (e is ConnectionShutdownException || e is UnknownHostException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NO_CONNECTION.code, "", "")
            } else if (e is SocketTimeoutException || e is IOException || e is NetworkErrorException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NOT_DEFINED.code, "", "")
            } else {
                e.printStackTrace()
                ResponseHandler.OnFailed(HttpErrorCode.NOT_DEFINED.code, "", "")
            }
        }
    }


    suspend fun makeBulkSubPoiAPICall(
        call: suspend () -> Response<List<BulkSubPoiItem>>
    ): ResponseHandler<List<BulkSubPoiItem>> {

        return try {
            val response = call.invoke()

            if (!response.isSuccessful) {
                return ResponseHandler.OnFailed(
                    response.code(),
                    "HTTP error ${response.code()}",
                    response.code().toString()
                )
            }

            val body = response.body()

            if (body.isNullOrEmpty()) {
                return ResponseHandler.OnFailed(
                    HttpErrorCode.EMPTY_RESPONSE.code,
                    "Empty response",
                    HttpErrorCode.EMPTY_RESPONSE.code.toString()
                )
            }

            val failedItem = body.firstOrNull { it.success != true }

            if (failedItem == null) {
                ResponseHandler.OnSuccessResponse(body)
            } else {
                ResponseHandler.OnFailed(
                    HttpErrorCode.ERROR_RESPONSE.code,
                    failedItem.message ?: "One or more Sub POIs failed",
                    HttpErrorCode.ERROR_RESPONSE.code.toString()
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ResponseHandler.OnFailed(
                HttpErrorCode.NOT_DEFINED.code,
                e.message ?: "Unknown error",
                HttpErrorCode.NOT_DEFINED.code.toString()
            )
        }
    }

    suspend fun <T : Any> makeAPICallTemp(call: suspend () -> Response<ResponseData<T>>): ResponseHandler<ResponseData<T>?> {
        try {
            val response = call.invoke()
            DebugLog.e("Response Code : ${response.code()}")
            when {
//                response.code() == 201 -> return ResponseHandler.OnSuccessResponse(response.body())
                response.code() in 200..300 -> {
                    DebugLog.e("Response Code 200 : ${response.code()}")
                    val body = response.body()
                    if(body!=null && body.isSuccess == true){
                        return ResponseHandler.OnSuccessResponse(response.body())
                    }else{
                        return ResponseHandler.OnFailed(
                            500,
                            body?.message ?: "Invalid Response",
                            "500"
                        )
                    }

                }
                response.code() in 300..400 -> {
                    DebugLog.e("Response Code : 300..400 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()
                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }
                response.code() in 400..500 -> {
                    DebugLog.e("Response Code : 400..500 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()

                    if (response.code() == 401) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.UNAUTHORIZED.code,
                            "Session Expired, Please login again",
                            responseWrapper?.statusCode.toString()
                        )
                    }

                    if (errMsg.isNullOrEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }

                response.code() in 500..600 -> {
                    DebugLog.e("Response Code : 500..600 : ${response.code()}")
                    DebugLog.e("Response Error Body : ${response.errorBody().toString()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val errMsg = responseWrapper?.message.toString()


                    if (errMsg.isEmpty().not()) {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.ERROR_RESPONSE.code,
                            errMsg,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        return ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            "Error occurred, Please try again later",
                            response.code().toString()
                        )
                    }
                }

                else -> {
                    DebugLog.e("Response Code : ${response.code()}")
                    val body = response.errorBody()
                    val bodyString = body?.string()
                    val responseWrapper = Gson().fromJson(bodyString, ErrorWrapper::class.java)
                    val message = if (responseWrapper?.statusCode == 422) {
                        responseWrapper.message
                    } else {
                        responseWrapper?.message
                    }
                    return if (message.isNullOrEmpty()) {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.EMPTY_RESPONSE.code,
                            message!!,
                            responseWrapper?.statusCode.toString()
                        )
                    } else {
                        ResponseHandler.OnFailed(
                            HttpErrorCode.NOT_DEFINED.code,
                            message,
                            responseWrapper?.statusCode.toString()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return if (e is ConnectionShutdownException || e is UnknownHostException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NO_CONNECTION.code,
                    "Internet connection is lost, Please try again later",
                    ""
                )
            } else if (e is SocketTimeoutException || e is IOException || e is NetworkErrorException) {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NOT_DEFINED.code,
                    "Server is timed out, Please try again later",
                    ""
                )
            } else {
                e.printStackTrace()
                ResponseHandler.OnFailed(
                    HttpErrorCode.NOT_DEFINED.code,
                    "Error occurred, Please try again later",
                    ""
                )
            }
        }
    }


}