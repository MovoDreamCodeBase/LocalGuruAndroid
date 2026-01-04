package com.movodream.localguru.data_collection.repository

import com.data.local.dao.DraftDao
import com.data.local.entity.DraftEntity
import com.data.remote.model.AgentTaskResponse
import com.data.remote.model.DeletePhotoRequest
import com.network.client.ApiClient
import com.network.client.BaseRepository
import com.network.client.ResponseHandler
import com.network.model.BulkSubPoiItem
import com.network.model.ResponseData
import com.network.model.ResponseListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject

class DraftRepository(private val dao: DraftDao): BaseRepository() {
    val apiInterface =   ApiClient.getApiInterface()
    fun observeDraft(poiId: String): Flow<DraftEntity?> =
        dao.observeDraft(poiId)

    suspend fun loadDraft(poiId: String): DraftEntity? =
        dao.getDraft(poiId)

    suspend fun saveDraft(entity: DraftEntity) =
        dao.insertDraft(entity)

    suspend fun deleteDraft(poiId: String) =
        dao.deleteDraft(poiId)

    suspend fun hasDraft(poiId: String): Boolean {
        return dao.hasDraft(poiId) > 0
    }

    suspend fun submitPOIData(
         body: Map<String, Any>,
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.Default) {
            return@withContext makeAPICallTemp {
                apiInterface.submitPOIDetails(
                    body
                )
            }

        }
    }

    suspend fun submitPOIData(
        data: RequestBody,
        files: List<MultipartBody.Part>
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.IO) {
            makeAPICallTemp {
                apiInterface.submitPOIDetails(data, files)
            }
        }
    }

    suspend fun updatePOIData(
        data: RequestBody,
        files: List<MultipartBody.Part>
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.IO) {
            makeAPICallTemp {
                apiInterface.updatePOIDetails(data, files)
            }
        }
    }

    suspend fun deleteGalleryPhoto(
        data: DeletePhotoRequest
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.IO) {
            makeAPICallTemp {
                apiInterface.deleteGalleryPhotos(data)
            }
        }
    }

    suspend fun addPOISubPOIData(
        data: RequestBody,
        files: List<MultipartBody.Part>
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.IO) {
            makeAPICallTemp {
                apiInterface.addPOISubPOI(data, files)
            }
        }
    }

    suspend fun addBulkSubPOIData(
        data: RequestBody,
        files: List<MultipartBody.Part>
    ): ResponseHandler<List<BulkSubPoiItem>> {

        return withContext(Dispatchers.IO) {
            makeBulkSubPoiAPICall {
                apiInterface.addBulkSubPOI(data, files)
            }
        }
    }


    suspend fun createPOI(
        body: Map<String, Any>
    ): ResponseHandler<ResponseData<Int>?> {

        return withContext(Dispatchers.IO) {
            makeAPICallTemp {
                apiInterface.createPOI(body)
            }
        }
    }


}
