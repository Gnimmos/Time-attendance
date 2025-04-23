package com.example.myapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "attendance.db"
        private const val DATABASE_VERSION = 1

        // Employee Table
        private const val TABLE_EMPLOYEES = "employees"
        private const val COL_EMPLOYEE_ID = "employee_id"
        private const val COL_EMPLOYEE_PIN = "pin"

        // Attendance Table
        private const val TABLE_ATTENDANCE = "attendance"
        private const val COL_ID = "id"
        private const val COL_ACTION = "action"
        private const val COL_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createEmployees = """
            CREATE TABLE $TABLE_EMPLOYEES (
                $COL_EMPLOYEE_ID TEXT PRIMARY KEY,
                $COL_EMPLOYEE_PIN TEXT NOT NULL
            );
        """.trimIndent()

        val createAttendance = """
            CREATE TABLE $TABLE_ATTENDANCE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMPLOYEE_ID TEXT,
                $COL_ACTION TEXT,
                $COL_TIMESTAMP TEXT
            );
        """.trimIndent()

        db.execSQL(createEmployees)
        db.execSQL(createAttendance)

        // Add test employee
        val insertTestEmployee = """
            INSERT INTO $TABLE_EMPLOYEES ($COL_EMPLOYEE_ID, $COL_EMPLOYEE_PIN)
            VALUES ('E001', '1234');
        """
        db.execSQL(insertTestEmployee)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ATTENDANCE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMPLOYEES")
        onCreate(db)
    }

    // 🔐 Validate employee by PIN
    fun isValidPin(pin: String): Boolean {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_EMPLOYEES WHERE $COL_EMPLOYEE_PIN = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(pin))
        val result = cursor.count > 0
        cursor.close()
        return result
    }

    // 🕒 Add clock in/out record
    fun addAttendance(pin: String, action: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EMPLOYEE_ID, pin)
            put(COL_ACTION, action)
            put(COL_TIMESTAMP, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }
        val result = db.insert(TABLE_ATTENDANCE, null, values)
        return result != -1L
    }
}
