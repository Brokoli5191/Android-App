package com.brokoli5191.androidapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ResultEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "results.db"
                ).build().also { INSTANCE = it }
            }
    }
}
