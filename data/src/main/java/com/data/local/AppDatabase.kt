package com.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.data.local.dao.UserDao
import com.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class/*, ProfileEntity::class*/],
    version = 1, // current DB version
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    /*abstract fun profileDao(): ProfileDao*/

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
