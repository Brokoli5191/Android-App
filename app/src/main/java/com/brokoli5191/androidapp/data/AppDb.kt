package com.brokoli5191.androidapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ResultEntity::class], version = 2)
abstract class AppDb : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS results_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        className TEXT,
                        name TEXT NOT NULL,
                        jumpMeters REAL,
                        sprintSeconds REAL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO results_new (id, name, jumpMeters, sprintSeconds)
                    SELECT id, name, jumpMeters, sprintSeconds FROM results
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE results")
                db.execSQL("ALTER TABLE results_new RENAME TO results")
            }
        }

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "results.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
