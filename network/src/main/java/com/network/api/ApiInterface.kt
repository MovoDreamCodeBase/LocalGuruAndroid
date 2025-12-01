package com.network.api

import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.LoginRequest
import com.data.remote.model.LoginResponse
import com.network.model.ResponseData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiInterface {

    @POST("auth/login")
    suspend fun signIn(
        @Body signInRequest: LoginRequest?
    ): Response<ResponseData<LoginResponse>>

    @GET("Merchant/dashboard/{merchantId}")
    suspend fun getAssignedPOIToAgent(
        @Path("merchantId") merchantId: String
    ): Response<ResponseData<AgentTaskResponse>>


    @POST("PointofInterest/agent-details/create")
    suspend fun submitPOIDetails(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseData<Int>>

    @Multipart
    @POST("PointofInterest/agent-details/create")
    suspend fun submitPOIDetails(
        @Part("payload") payload: RequestBody,
        @Part GalleryPhotos: List<MultipartBody.Part>
    ): Response<ResponseData<Int>>



}