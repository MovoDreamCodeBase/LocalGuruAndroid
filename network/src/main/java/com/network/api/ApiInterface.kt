package com.network.api

import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.DeletePhotoRequest
import com.data.remote.model.LoginRequest
import com.data.remote.model.LoginResponse
import com.data.remote.model.RevisionDataResponse
import com.network.model.BulkSubPoiItem
import com.network.model.ResponseData
import com.network.model.ResponseListData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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
    @POST("PointOfInterest/agent-details/submit-agent-poi")
    suspend fun submitPOIDetails(
        @Part("payload") payload: RequestBody,
        @Part GalleryPhotos: List<MultipartBody.Part>
    ): Response<ResponseData<Int>>

    @GET("PointofInterest/v2/get-poi/{agentId}/{poiId}")
    suspend fun getRevisionData(
        @Path("agentId") agentId: String,
        @Path("poiId") poiId: String
    ): Response<ResponseData<RevisionDataResponse>>

    @Multipart
    @POST("PointofInterest/agent-details/update")
    suspend fun updatePOIDetails(
        @Part("payload") payload: RequestBody,
        @Part GalleryPhotos: List<MultipartBody.Part>
    ): Response<ResponseData<Int>>

     @POST("PointofInterest/delete-poi-images")
    suspend fun deleteGalleryPhotos(
        @Body body: DeletePhotoRequest
    ): Response<ResponseData<Int>>

    @Multipart
    @POST("SubPointOfInterest/agent-subpoi/create/")
    suspend fun addPOISubPOI(
        @Part("payload") payload: RequestBody,
        @Part GalleryPhotos: List<MultipartBody.Part>
    ): Response<ResponseData<Int>>

    @POST("Merchant/create-poi")
    suspend fun createPOI(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseData<Int>>

    @Multipart
    @POST("SubPointOfInterest/agent-bulk-subpoi/create")
    suspend fun addBulkSubPOI(
        @Part("payload") payload: RequestBody,
        @Part GalleryPhotos: List<MultipartBody.Part>
    ): Response<List<BulkSubPoiItem>>

}