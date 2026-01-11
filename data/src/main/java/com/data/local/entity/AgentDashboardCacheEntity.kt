package com.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_dashboard_cache")
data class AgentDashboardCacheEntity(
    @PrimaryKey val agentId: String,
    val responseJson: String,
    val updatedAt: Long
)
