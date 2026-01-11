package com.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_master_cache")
data class CategoryMasterCacheEntity(
    @PrimaryKey val categoryId: String,
    val json: String,
    val updatedAt: Long
)

