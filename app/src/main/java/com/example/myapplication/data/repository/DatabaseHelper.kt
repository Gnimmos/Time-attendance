package com.example.myapplication.data.repository

import android.content.Context
import android.util.Log
import com.example.myapplication.ApiService
import com.example.myapplication.ApiService.recordAttendanceAsync
import com.example.myapplication.ApiService.recordAttendanceWithPhotoAsync
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.dao.EmployeeDao
import com.example.myapplication.data.dao.PendingAttendanceDao
import com.example.myapplication.data.dao.TimeRecordDao
import com.example.myapplication.data.entities.Employee
import com.example.myapplication.data.entities.PendingAttendance
import com.example.myapplication.data.entities.TimeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import okhttp3.Request as OkRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(private val context: Context) {
    private val db: AppDatabase = AppDatabase.getDatabase(context)
    private val employeeDao: EmployeeDao = db.employeeDao()
    private val pendingDao: PendingAttendanceDao = db.pendingAttendanceDao()
    private val timeDao: TimeRecordDao = db.timeRecordDao()
    private fun currentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    suspend fun isValidEmployeePin(employeeId: String, pin: String): Boolean =
        withContext(Dispatchers.IO) {
            employeeDao.isValidEmployeePin(employeeId, pin).also { valid ->
                Log.d(
                    "DBHelper",
                    "Offline PIN check for $employeeId pin=$pin → ${if (valid) "SUCCESS" else "FAIL"}"
                )
            }
        }

    suspend fun upsertEmployee(employeeId: String, pin: String) = withContext(Dispatchers.IO) {
        employeeDao.upsert(Employee(employeeId, pin))
        Log.i("DBHelper", "Upserted employee $employeeId PIN=$pin")
    }

    suspend fun addOfflineAttendance(
        employeeId: String,
        action: String,
        photoPath: String?
    ): Boolean = withContext(Dispatchers.IO) {
        timeDao.addOfflineAttendance(
            TimeRecord(
                employeeId = employeeId,
                action = action,
                timestamp = currentTimestamp(),
                photoPath = photoPath,
                synced = false
            )
        )
        true
    }

    suspend fun countPendingRecords(): Int = withContext(Dispatchers.IO) {
        pendingDao.getPendingRecords().size
    }

    suspend fun countSyncedRecords(): Int = withContext(Dispatchers.IO) {
        timeDao.countSyncedRecords()
    }

    suspend fun addPendingAttendance(
        employeeId: String,
        action: String,
        deviceUuid: String,
        photoPath: String?
    ) = withContext(Dispatchers.IO) {
        pendingDao.addPendingAttendance(
            PendingAttendance(
                employeeId = employeeId,
                action = action,
                timestamp = currentTimestamp(),
                deviceUuid = deviceUuid,
                photoPath = photoPath,
                synced = false
            )
        )
        Log.i("DBHelper", "Saved pending attendance for $employeeId → $action")
    }

    suspend fun syncPendingAttendance() = withContext(Dispatchers.IO) {
        val rows = pendingDao.getPendingRecords()     // suspend
        if (rows.isEmpty()) return@withContext

        for (row in rows) {
            val id        = row.id
            val empId     = row.employeeId.toInt()
            val action    = row.action
            val uuid      = row.deviceUuid
            val photoFile = row.photoPath?.let(::File)

            // 1) record punch
            val okPunch = ApiService.recordAttendanceAsync(context, empId, action, uuid)
            if (!okPunch) continue

            // 2) insert into your local TimeRecord table (optional)
            // 2) persist locally
                   val tr = TimeRecord(
                           employeeId = row.employeeId,
                          action     = row.action,
                           timestamp  = currentTimestamp(),
                           photoPath  = row.photoPath,
                           synced     = true          // because we just pushed it
                              )
                   timeDao.addOfflineAttendance(tr)
            // 3) upload photo if present
            if (photoFile?.exists() == true) {
                val okPhoto = ApiService.recordAttendanceWithPhotoAsync(context, empId, action, uuid, photoFile)
                if (!okPhoto) continue
            }

            // 4) mark synced
            pendingDao.markAsSynced(id)                 // suspend
        }

        // finally, clean them out
        pendingDao.cleanSyncedPendingData()           // suspend
}
    suspend fun syncEmployeesFromApi() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val compId = prefs.getInt("company_id", -1)
        if (compId == -1) return@withContext

        // 1) fetch JSON via OkHttp synchronously
        val client = OkHttpClient()
        val req    = OkRequest.Builder()
            .url("${ApiService.BASE_URL}/api/employees")
            .get()
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            Log.e("DBHelper", "employee sync failed: ${resp.code}")
            return@withContext
        }

        val body    = resp.body?.string().orEmpty()
        val arr     = JSONObject(body).optJSONArray("employees") ?: return@withContext
        val valid   = mutableListOf<String>()

        // 2) upsert each matching employee
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optInt("companyId") != compId) continue

            val id  = obj.optString("employeeNumber", "")
            val pin = obj.optString("pin", "")
            if (id.isNotBlank() && pin.isNotBlank()) {
                employeeDao.upsert(Employee(id, pin))   // suspend
                valid += id
            }
        }

        // 3) remove stale employees
        employeeDao.removeStaleEmployees(valid)     // suspend
        Log.i("DBHelper", "Synced ${valid.size} employees for company $compId")
    }

}
