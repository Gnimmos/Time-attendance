package com.example.myapplication.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey val employeeId: String,
    val pin: String
)
