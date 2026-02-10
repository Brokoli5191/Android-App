package com.brokoli5191.androidapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Query("SELECT * FROM results ORDER BY id DESC")
    fun observeAll(): Flow<List<ResultEntity>>

    @Query("SELECT * FROM results ORDER BY id ASC")
    suspend fun getAllOnce(): List<ResultEntity>

    @Insert
    suspend fun insert(item: ResultEntity)

    @Update
    suspend fun update(item: ResultEntity)

    @Query("DELETE FROM results WHERE id = :id")
    suspend fun deleteById(id: Long)
}
