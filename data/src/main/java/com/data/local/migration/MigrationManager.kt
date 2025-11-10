package com.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


object MigrationManager {
    // v1 → v2: add mobile column to users
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN mobile TEXT")
        }
    }

    // v2 → v3: add profiles table
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    bio TEXT
                )"""
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns to users
            database.execSQL("ALTER TABLE users ADD COLUMN mobile TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN createdAt INTEGER")

            // Create new profiles table
            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userId INTEGER NOT NULL,
                bio TEXT
            )
            """
            )
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3,MIGRATION_3_4)
}
