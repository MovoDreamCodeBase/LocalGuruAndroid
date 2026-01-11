package com.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.data.local.dao.AgentDashboardCacheDao
import com.data.local.dao.CategoryMasterCacheDao
import com.data.local.dao.DraftDao
import com.data.local.dao.UserDao
import com.data.local.entity.AgentDashboardCacheEntity
import com.data.local.entity.CategoryMasterCacheEntity
import com.data.local.entity.DraftEntity
import com.data.local.entity.UserEntity
import com.data.local.migration.MigrationManager

@Database(
    entities = [
        DraftEntity::class,
        AgentDashboardCacheEntity::class,
        CategoryMasterCacheEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun draftDao(): DraftDao
    abstract fun dashboardCacheDao(): AgentDashboardCacheDao
    abstract fun categoryMasterCacheDao(): CategoryMasterCacheDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MigrationManager.MIGRATION_1_2)
                    .build()
            }
    }
}

