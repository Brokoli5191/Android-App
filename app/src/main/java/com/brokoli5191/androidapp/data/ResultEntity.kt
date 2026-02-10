package com.brokoli5191.androidapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jumpMeters: Double,
    val sprintSeconds: Double
)
