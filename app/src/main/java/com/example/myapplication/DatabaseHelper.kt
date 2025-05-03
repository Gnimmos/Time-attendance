package com.example.myapplication

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "attendance.db"
        private const val DATABASE_VERSION = 2

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
        val createPending = """
    CREATE TABLE pending_attendance (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        employee_id TEXT NOT NULL,
        "action" TEXT NOT NULL,
        timestamp TEXT NOT NULL,
        device_uuid TEXT NOT NULL,
        photo_path TEXT,
        synced INTEGER DEFAULT 0
    );
""".trimIndent()
        db.execSQL(createPending)

    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i("DBHelper", "Upgrading database from version $oldVersion to $newVersion")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ATTENDANCE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMPLOYEES")
        db.execSQL("DROP TABLE IF EXISTS pending_attendance")
        onCreate(db)
    }

    // 🔐 Validate employee by PIN
    fun isValidEmployeePin(employeeId: String, pin: String): Boolean {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_EMPLOYEES WHERE $COL_EMPLOYEE_ID = ? AND $COL_EMPLOYEE_PIN = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(employeeId, pin))
        val result = cursor.count > 0
        Log.d("DBHelper", "Offline PIN check for $employeeId with pin=$pin → ${if (result) "SUCCESS" else "FAIL"}")
        cursor.close()
        return result
    }
    fun upsertEmployee(employeeId: String, pin: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EMPLOYEE_ID, employeeId)
            put(COL_EMPLOYEE_PIN, pin)
        }
        val result = db.insertWithOnConflict(TABLE_EMPLOYEES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.i("DBHelper", "Upserted employee: $employeeId with PIN=$pin → result=$result")
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

    fun syncEmployeesFromApi(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentCompanyId = prefs.getInt("company_id", -1)
        if (currentCompanyId == -1) {
            Log.w("DBHelper", "No company ID found — skipping employee sync.")
            return
        }

        val url = "http://4.184.202.172:3612/api/employees"
        val request = com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            { response ->
                if (!response.optBoolean("success", false)) {
                    Log.e("DBHelper", "Server responded with failure during employee sync.")
                    return@JsonObjectRequest
                }

                val employees = response.optJSONArray("employees") ?: return@JsonObjectRequest
                var count = 0

                for (i in 0 until employees.length()) {
                    val emp = employees.optJSONObject(i) ?: continue
                    val companyId = emp.optInt("companyId", -1)
                    if (companyId != currentCompanyId) continue

                    val empId = emp.optString("employeeNumber", "")
                    val pin = emp.optString("pin", "")

                    if (empId.isNotBlank() && pin.isNotBlank()) {
                        upsertEmployee(empId, pin)
                        count++
                    }
                }

                Log.i("DBHelper", "Synced $count employee(s) for companyId=$currentCompanyId")
            },
            { error ->
                Log.e("DBHelper", "Network error during employee sync: ${error.message}")
            }
        )

        com.android.volley.toolbox.Volley.newRequestQueue(context).add(request)
    }

    fun addPendingAttendance(
        employeeId: String,
        action: String,
        deviceUUID: String,
        photoPath: String? = null
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("employee_id", employeeId)
            put("action", action)
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("device_uuid", deviceUUID)
            put("synced", 0)
            if (photoPath != null) put("photo_path", photoPath)
        }
        val result = db.insert("pending_attendance", null, values)
        Log.i("DBHelper", "Saved offline punch + photo for $employeeId → $action")
        return result != -1L
    }

    fun syncPendingAttendance(context: Context) {
        val db = writableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM pending_attendance WHERE synced = 0 ORDER BY timestamp ASC",
            null
        )

        if (!cursor.moveToFirst()) {
            cursor.close()
            Log.i("DBHelper", "No pending attendance to sync.")
            return
        }

        val records = mutableListOf<Map<String, String?>>()
        do {
            val record = mutableMapOf<String, String?>()
            record["id"] = cursor.getInt(cursor.getColumnIndexOrThrow("id")).toString()
            record["employee_id"] = cursor.getString(cursor.getColumnIndexOrThrow("employee_id"))
            record["action"] = cursor.getString(cursor.getColumnIndexOrThrow("action"))
            record["timestamp"] = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
            record["device_uuid"] = cursor.getString(cursor.getColumnIndexOrThrow("device_uuid"))
            record["photo_path"] = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"))
            records.add(record)
        } while (cursor.moveToNext())
        cursor.close()

        fun syncNext(index: Int) {
            if (index >= records.size) {
                Log.i("DBHelper", "✅ Finished syncing all pending records.")
                return
            }

            val record = records[index]
            val id = record["id"]!!.toInt()
            val empId = record["employee_id"]!!.toInt()
            val action = record["action"]!!
            val uuid = record["device_uuid"]!!
            val photoPath = record["photo_path"]
            val photoFile = if (!photoPath.isNullOrBlank()) File(photoPath) else null

            ApiService.recordAttendance(context, empId, action, uuid) { punchSuccess ->
                if (punchSuccess) {
                    Log.i("DBHelper", "✅ Synced attendance $id – $empId/$action")

                    if (photoFile?.exists() == true) {
                        ApiService.recordAttendanceWithPhoto(
                            context,
                            empId,
                            action,
                            uuid,
                            photoFile
                        ) { photoSuccess, _ ->
                            if (photoSuccess) {
                                db.execSQL("UPDATE pending_attendance SET synced = 1 WHERE id = ?", arrayOf(id))
                                photoFile.delete()
                                Log.i("DBHelper", "📸 Synced photo for $empId (deleted $photoPath)")
                            } else {
                                Log.w("DBHelper", "❌ Photo upload failed for $empId/$action")
                            }
                            syncNext(index + 1) // proceed to next after photo attempt
                        }
                    } else {
                        db.execSQL("UPDATE pending_attendance SET synced = 1 WHERE id = ?", arrayOf(id))
                        syncNext(index + 1)
                    }
                } else {
                    Log.e("DBHelper", "❌ Failed to sync attendance $id – $empId/$action")
                    syncNext(index + 1)
                }
            }
        }

        syncNext(0) // start syncing from the first record
    }


}
