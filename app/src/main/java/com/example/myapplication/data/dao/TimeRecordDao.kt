package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.entities.TimeRecord

@Dao
interface TimeRecordDao {
    @Insert
    suspend fun addOfflineAttendance(record: TimeRecord)

    @Query("SELECT COUNT(*) FROM time_records WHERE synced = 1")
    suspend fun countSyncedRecords(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM time_records WHERE employeeId = :employeeId AND action = :action AND timestamp = :timestamp)")
    suspend fun recordExistsInTimeRecords(employeeId: String, action: String, timestamp: String): Boolean

    @Query("DELETE FROM time_records WHERE employeeId = :employeeId AND action = :action AND timestamp = :timestamp")
    suspend fun removeOfflineAttendanceRecord(employeeId: String, action: String, timestamp: String): Int
}
