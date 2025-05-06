package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: String,
    val action: String,
    val timestamp: String,
    val photoPath: String?,        // new
    val synced: Boolean = false
)
