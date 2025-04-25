package com.example.myapplication
import android.util.Log

import android.content.Context
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

object ApiService {

        private const val BASE_URL = "http://192.168.10.164:3000"
    fun validateEmployee(
        context: Context,
        employeeNumber: Int,
        pinCode: Int,
        deviceUUID: String,
        onResult: (Boolean, JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/employee/validate"
        val body = JSONObject().apply {
            put("employeeNumber", employeeNumber)
            put("pinCode", pinCode)
            put("deviceUUID", deviceUUID)
        }

        val req = JsonObjectRequest(Request.Method.POST, url, body,
            { resp ->
                val ok = resp.optBoolean("success", false)
                // server returns `.employee` on success
                onResult(ok, resp.optJSONObject("employee"))
            },
            { err ->
                Toast.makeText(context, "Validation error: ${err.message}", Toast.LENGTH_SHORT).show()
                onResult(false, null)
            }
        )
        Volley.newRequestQueue(context).add(req)
    }

    /**
     * 2️⃣ Record Attendance Action (clock-in/out, break start/stop)
     */
    fun recordAttendance(
        context: Context,
        employeeNumber: Int,
        action: String,
        deviceUUID: String,
        onResult: (Boolean) -> Unit
    ) {
        val url = "$BASE_URL/api/attendance/record"
        val body = JSONObject().apply {
            put("employeeNumber", employeeNumber)
            put("action", action)
            put("deviceUUID", deviceUUID)
        }
        val req = JsonObjectRequest(Request.Method.POST, url, body,
            { resp -> onResult(resp.optBoolean("success", false)) },
            { err ->
                err.networkResponse?.let {
                    val bodyText = String(it.data, Charsets.UTF_8)
                    Log.e("AttendanceError", "HTTP ${it.statusCode} → $bodyText")
                }
                Toast.makeText(context, "Attendance error: see logcat", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        ).apply { this.setShouldCache(false) }
        Volley.newRequestQueue(context).add(req)
    }


    fun checkEmployeePin(context: Context, pin: String, onResult: (Boolean, JSONObject?) -> Unit) {
        val url = "$BASE_URL/api/employees/check"
        val json = JSONObject().put("pin", pin)

        val request = JsonObjectRequest(Request.Method.POST, url, json,
            { response ->
                val success = response.optBoolean("success", false)
                val user = response.optJSONObject("user")
                onResult(success, user)
            },
            { error ->
                Toast.makeText(context, "Network error: ${error.message}", Toast.LENGTH_SHORT).show()
                onResult(false, null)
            }
        )

        Volley.newRequestQueue(context).add(request)
    }

    fun superUserLogin(
        context: Context,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        val url = "$BASE_URL/api/superuser/login"
        val json = JSONObject().apply {
            put("password", password)
        }

        val request = JsonObjectRequest(Request.Method.POST, url, json,
            { response ->
                val success = response.optBoolean("success", false)
                onResult(success)
            },
            { error ->
                Toast.makeText(context, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        )

        Volley.newRequestQueue(context).add(request)
    }

    fun registerDevice(
        context: Context,
        companyId: Int,
        outletId: Int,
        deviceUUID: String,
        deviceName: String,
        onResult: (Boolean, Int, JSONObject?, JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/device/register"
        val body = JSONObject().apply {
            put("companyId", companyId)
            put("outletId", outletId)
            put("deviceUUID", deviceUUID)
            put("deviceName", deviceName)
        }

        val req = JsonObjectRequest(Request.Method.POST, url, body,
            { resp ->
                // NOTE: our server writes back INSERTED.id as "deviceUUID"
                val ok = resp.optBoolean("success", false)
                val createdId = resp.optInt("deviceUUID", -1)
                val company = resp.optJSONObject("company")
                val outlet  = resp.optJSONObject("outlet")
                onResult(ok, createdId, company, outlet)
            },
            { err ->
                Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_SHORT).show()
                onResult(false, -1, null, null)
            }
        )

        Volley.newRequestQueue(context).add(req)
    }

    fun getCompanyInfo(
        context: Context,
        companyId: Int,
        onResult: (Boolean, JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/company/$companyId"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val success = response.optBoolean("success", false)
                val company = response.optJSONObject("company")
                onResult(success, company)
            },
            { error ->
                Toast.makeText(context, "Network error: ${error.message}", Toast.LENGTH_SHORT).show()
                onResult(false, null)
            }
        )

        Volley.newRequestQueue(context).add(request)
    }

}
