package com.movodream.localguru.data_collection.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.core.constants.AppConstants
import com.data.local.AppDatabase
import com.data.local.dao.AgentDashboardCacheDao
import com.data.local.dao.DraftDao
import com.data.local.entity.AgentDashboardCacheEntity
import com.data.local.entity.CategoryMasterCacheEntity
import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.RevisionDataResponse
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.Parser
import com.network.client.ApiClient
import com.network.client.BaseRepository
import com.network.client.ResponseHandler
import com.network.model.HttpErrorCode
import com.network.model.ResponseData
import com.network.model.ResponseListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class CategoryResult {
    object Loading : CategoryResult()
    data class Success(val data: FormSchema) : CategoryResult()
    object NotFound : CategoryResult()
    data class Error(val message: String) : CategoryResult()
}
class DashboardRepository(private val appDatabase: AppDatabase)  : BaseRepository(){

  val apiInterface =   ApiClient.getApiInterface()
    private val ref = FirebaseDatabase.getInstance()
        .getReference(AppConstants.FIREBASE_LOCAL_GURU_DB)
        .child(AppConstants.DYNAMIC_DATA_SET)

    suspend fun getCategoryDetails(categoryId: String): CategoryResult {
        return try {
            Log.d("ViewModel", "getCategoryDetails() Fetched from Firebase")

            val snapshot = ref.child(categoryId).get().await()

            if (!snapshot.exists()) {
                return CategoryResult.NotFound
            }

            val map = snapshot.value as? Map<String, Any>
                ?: return CategoryResult.Error("Invalid data format received")

            val json = JSONObject(map)

            val schema = Parser.parseFormSchema(json)

            CategoryResult.Success(schema)


        } catch (e: Exception) {
            CategoryResult.Error(e.message ?: "Unexpected error")
        }
    }

//    suspend fun merchantDashboard(
//        agentId: String,
//    ): ResponseHandler<ResponseData<AgentTaskResponse>?> {
//
//        return withContext(Dispatchers.Default) {
//            return@withContext makeAPICallTemp {
//                apiInterface.getAssignedPOIToAgent(
//                    agentId
//                )
//            }
//
//        }
//    }

    suspend fun merchantDashboard(
        agentId: String
    ): ResponseHandler<ResponseData<AgentTaskResponse>?> {

        return withContext(Dispatchers.Default) {

            // 🔹 1. Call existing API wrapper
            val apiResult = makeAPICallTemp {
                apiInterface.getAssignedPOIToAgent(agentId)
            }

            when (apiResult) {

                // API SUCCESS
                is ResponseHandler.OnSuccessResponse -> {

                    val data = apiResult.response?.data

                    if (data != null) {
                        // 🔹 Store latest successful response
                        appDatabase.dashboardCacheDao().save(
                            AgentDashboardCacheEntity(
                                agentId = agentId,
                                responseJson = Gson().toJson(data),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    // 🔹 Return API response as-is
                    apiResult
                }

                //  API FAILED (network / timeout / server / etc.)
                is ResponseHandler.OnFailed -> {

                    // 🔹 Decide when to fallback to DB
                    val shouldFallback =
                        apiResult.code == HttpErrorCode.NO_CONNECTION.code ||
                                apiResult.code == HttpErrorCode.NOT_DEFINED.code

                    if (shouldFallback) {

                        val cached = appDatabase.dashboardCacheDao()
                            .get(agentId)

                        if (cached != null) {

                            val cachedData = Gson().fromJson(
                                cached.responseJson,
                                AgentTaskResponse::class.java
                            )

                            // 🔹 Return cached data as SUCCESS
                            return@withContext ResponseHandler.OnSuccessResponse(
                                ResponseData<AgentTaskResponse>().apply {
                                    isSuccess = true
                                    message = "Offline data"
                                    data = cachedData
                                }
                            )
                        }
                    }

                    // 🔹 No cache OR not eligible for fallback
                    apiResult
                }

                else -> apiResult
            }
        }
    }


    suspend fun revisionData(
        agentId: String,poiId : String
    ): ResponseHandler<ResponseData<RevisionDataResponse>?> {

        return withContext(Dispatchers.Default) {
            return@withContext makeAPICallTemp {
                apiInterface.getRevisionData(
                    agentId, poiId
                )
            }

        }
    }

    suspend fun downloadAllCategoryMaster(
        onProgress: (Int) -> Unit
    ): Result<Unit> {

        return try {
            val rootSnapshot = ref.get().await()

            if (!rootSnapshot.exists()) {
                return Result.failure(Exception("No master data found"))
            }

            val totalCategories = rootSnapshot.childrenCount.toInt()
            var processed = 0

            val cacheList = mutableListOf<CategoryMasterCacheEntity>()

            for (categorySnapshot in rootSnapshot.children) {

                val categoryId = categorySnapshot.key ?: continue
                val map = categorySnapshot.value as? Map<String, Any> ?: continue

                val json = JSONObject(map).toString()

                cacheList.add(
                    CategoryMasterCacheEntity(
                        categoryId = categoryId,
                        json = json,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                processed++
                val percent = (processed * 100) / totalCategories
                onProgress(percent)
            }

            // 🔥 BULK INSERT (FAST)
            appDatabase.categoryMasterCacheDao().clearAll()
            appDatabase.categoryMasterCacheDao().saveAll(cacheList)

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedCategorySchema(
        categoryId: String
    ): FormSchema? = withContext(Dispatchers.IO) {

        val cached = appDatabase
            .categoryMasterCacheDao()
            .getCategory(categoryId)
            ?: return@withContext null

        try {
            Parser.parseFormSchema(JSONObject(cached.json))
        } catch (e: Exception) {
            null
        }
    }


}

