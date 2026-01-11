package com.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


object MigrationManager {



        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // -----------------------------
                // Agent Dashboard Cache
                // -----------------------------
                db.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS agent_dashboard_cache (
                    agentId TEXT NOT NULL PRIMARY KEY,
                    responseJson TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
                )

                // -----------------------------
                // Category Master Cache
                // -----------------------------
                db.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS category_master_cache (
                    categoryId TEXT NOT NULL PRIMARY KEY,
                    json TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
                )
            }
        }


}

