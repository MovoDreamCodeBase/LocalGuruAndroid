package com.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_table")
data class DraftEntity(
    @PrimaryKey
    val poiId: String,          // POI ID = task ID
    val formId: String,         // Store schema form id
    val updatedAt: Long,        // For sorting / sync
    val draftJson: String       // Entire draft as JSON (includes base64 photos)
)