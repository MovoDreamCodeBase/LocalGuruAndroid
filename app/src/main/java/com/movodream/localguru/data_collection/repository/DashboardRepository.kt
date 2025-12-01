package com.movodream.localguru.data_collection.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.core.constants.AppConstants
import com.data.remote.model.AgentTaskResponse
import com.google.firebase.database.FirebaseDatabase
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.Parser
import com.network.client.ApiClient
import com.network.client.BaseRepository
import com.network.client.ResponseHandler
import com.network.model.ResponseData
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
class DashboardRepository  : BaseRepository(){

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

    suspend fun merchantDashboard(
        agentId: String,
    ): ResponseHandler<ResponseData<AgentTaskResponse>?> {

        return withContext(Dispatchers.Default) {
            return@withContext makeAPICallTemp {
                apiInterface.getAssignedPOIToAgent(
                    agentId
                )
            }

        }
    }
}

