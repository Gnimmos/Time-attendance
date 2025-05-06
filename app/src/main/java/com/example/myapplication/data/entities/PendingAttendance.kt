package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_attendance")
data class PendingAttendance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: String,
    val action: String,
    val timestamp: String,
    val deviceUuid: String,
    val photoPath: String?,
    val synced: Boolean = false
)
