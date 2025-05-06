package com.example.myapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.dao.EmployeeDao
import com.example.myapplication.data.dao.PendingAttendanceDao
import com.example.myapplication.data.dao.TimeRecordDao
import com.example.myapplication.data.entities.Employee
import com.example.myapplication.data.entities.PendingAttendance
import com.example.myapplication.data.entities.TimeRecord


@Database(
    entities = [Employee::class, TimeRecord::class, PendingAttendance::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun pendingAttendanceDao(): PendingAttendanceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendance.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
