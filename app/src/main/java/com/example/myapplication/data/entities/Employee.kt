package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.entities.Employee

@Dao
interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: Employee)

    @Query("SELECT EXISTS(SELECT 1 FROM employees WHERE employeeId = :employeeId AND pin = :pin)")
    suspend fun isValidEmployeePin(employeeId: String, pin: String): Boolean

    @Query("DELETE FROM employees WHERE employeeId NOT IN (:validIds)")
    suspend fun removeStaleEmployees(validIds: List<String>): Int
}
