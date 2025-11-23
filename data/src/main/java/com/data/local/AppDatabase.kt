package com.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.data.local.dao.DraftDao
import com.data.local.dao.UserDao
import com.data.local.entity.DraftEntity
import com.data.local.entity.UserEntity

@Database(entities = [DraftEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun draftDao(): DraftDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    /*.addMigrations(*MigrationManager.ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration() // optional for dev builds*/
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
