package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.AgentDashboardCacheEntity

@Dao
interface AgentDashboardCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: AgentDashboardCacheEntity)

    @Query("SELECT * FROM agent_dashboard_cache WHERE agentId = :agentId")
    suspend fun get(agentId: String): AgentDashboardCacheEntity?
}
