package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.entities.PendingAttendance

@Dao
interface PendingAttendanceDao {
    @Insert
    suspend fun addPendingAttendance(record: PendingAttendance)

    @Query("SELECT * FROM pending_attendance WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingRecords(): List<PendingAttendance>

    @Query("UPDATE pending_attendance SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM pending_attendance WHERE synced = 1")
    suspend fun cleanSyncedPendingData(): Int

    @Query("DELETE FROM pending_attendance WHERE id = :id")
    suspend fun removePendingRecordById(id: Int): Int
}
