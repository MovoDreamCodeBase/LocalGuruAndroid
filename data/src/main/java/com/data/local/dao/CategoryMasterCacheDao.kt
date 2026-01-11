package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.CategoryMasterCacheEntity

@Dao
interface CategoryMasterCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: CategoryMasterCacheEntity)

    @Query("SELECT * FROM category_master_cache WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getCategory(categoryId: String): CategoryMasterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(list: List<CategoryMasterCacheEntity>)

    @Query("SELECT * FROM category_master_cache WHERE categoryId = :categoryId")
    suspend fun get(categoryId: String): CategoryMasterCacheEntity?

    @Query("DELETE FROM category_master_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM category_master_cache")
    suspend fun count(): Long

}
