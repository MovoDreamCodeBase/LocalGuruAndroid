package com.movodream.localguru.data_collection.repository

import com.data.local.dao.DraftDao
import com.data.local.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

class DraftRepository(private val dao: DraftDao) {

    fun observeDraft(poiId: String): Flow<DraftEntity?> =
        dao.observeDraft(poiId)

    suspend fun loadDraft(poiId: String): DraftEntity? =
        dao.getDraft(poiId)

    suspend fun saveDraft(entity: DraftEntity) =
        dao.insertDraft(entity)

    suspend fun deleteDraft(poiId: String) =
        dao.deleteDraft(poiId)
}
