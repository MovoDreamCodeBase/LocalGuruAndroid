package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query("SELECT * FROM draft_table WHERE poiId = :poiId LIMIT 1")
    fun observeDraft(poiId: String): Flow<DraftEntity?>

    @Query("SELECT * FROM draft_table WHERE poiId = :poiId LIMIT 1")
    suspend fun getDraft(poiId: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(entity: DraftEntity)

    @Query("DELETE FROM draft_table WHERE poiId = :poiId")
    suspend fun deleteDraft(poiId: String)
}
