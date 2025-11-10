package com.network.api

import com.data.model.LoginRequest
import com.data.model.LoginResponse
import com.network.model.ResponseData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiInterface {

    @POST("auth/login")
    suspend fun signIn(
        @Body signInRequest: LoginRequest?
    ): Response<ResponseData<LoginResponse>>


}