package com.example.myapplication

// Android
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

// Volley — alias its Request so it doesn’t conflict
import com.android.volley.Request as VolleyRequest
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

// JSON
import org.json.JSONObject

// OkHttp3 — import Request normally
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request            // <<-- OkHttp’s Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

import java.io.File
import java.io.IOException
object ApiService {
    private const val BASE_URL = "http://4.184.202.172:3012"
    fun recordAttendanceWithPhoto(
        context: Context,
        employeeNumber: Int,
        action: String,
        deviceUUID: String,
        photoFile: File,
        onResult: (Boolean, List<String>?) -> Unit
    ) {
        val client = OkHttpClient()
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val fileBody  = photoFile.asRequestBody(mediaType)

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("deviceUUID", deviceUUID)
            .addFormDataPart("photos", photoFile.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/employees/$employeeNumber/photos")
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(false, null)
                }
            }

            override fun onResponse(call: Call, resp: Response) {
                if (!resp.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Server error: ${resp.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                        onResult(false, null)
                    }
                    return
                }

                // parse { success, message, urls: [...] }
                val bodyString = resp.body?.string() ?: ""
                val urls = mutableListOf<String>()
                try {
                    val obj = JSONObject(bodyString)
                    val arr = obj.optJSONArray("urls")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            urls.add(arr.getString(i))
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("ApiService", "JSON parse error", ex)
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Photo uploaded!", Toast.LENGTH_SHORT).show()
                    onResult(true, urls)
                }
            }
        })
    }
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

        val req = JsonObjectRequest(
            VolleyRequest.Method.POST,       // ← use VolleyRequest
            url,
            body,
            { resp ->
                val ok = resp.optBoolean("success", false)
                val emp = resp.optJSONObject("employee")
                onResult(ok, emp)
            },
            { err ->
                err.networkResponse?.let { nr ->
                    val bodyText = String(nr.data, Charsets.UTF_8)
                    val msg = try {
                        JSONObject(bodyText).optString("error", "Unknown error")
                    } catch (_: Exception) {
                        "Validation failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG).show()
                }
                onResult(false, null)
            }
        )
        Volley.newRequestQueue(context).add(req)
    }

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

        val req = JsonObjectRequest(
            VolleyRequest.Method.POST,       // ← use VolleyRequest
            url,
            body,
            { resp ->
                onResult(resp.optBoolean("success", false))
            },
            { err ->
                err.networkResponse?.let { nr ->
                    val bodyText = String(nr.data, Charsets.UTF_8)
                    val msg = try {
                        JSONObject(bodyText).optString("error", "Action failed")
                    } catch (_: Exception) {
                        "Attendance failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG).show()
                }
                onResult(false)
            }
        ).apply { setShouldCache(false) }

        Volley.newRequestQueue(context).add(req)
    }

    fun superUserLogin(
        context: Context,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        val url = "$BASE_URL/api/superuser/login"
        val body = JSONObject().put("password", password)

        val req = JsonObjectRequest(
            VolleyRequest.Method.POST,       // ← use VolleyRequest
            url,
            body,
            { resp ->
                onResult(resp.optBoolean("success", false))
            },
            { err ->
                err.networkResponse?.let { nr ->
                    val bodyText = String(nr.data, Charsets.UTF_8)
                    val msg = try {
                        JSONObject(bodyText).optString("error", "Login failed")
                    } catch (_: Exception) {
                        "Login failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG).show()
                }
                onResult(false)
            }
        )

        Volley.newRequestQueue(context).add(req)
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

        val req = JsonObjectRequest(
            VolleyRequest.Method.POST,       // ← use VolleyRequest
            url,
            body,
            { resp ->
                val ok       = resp.optBoolean("success", false)
                val deviceId = resp.optInt("deviceId", -1)
                val company  = resp.optJSONObject("company")
                val outlet   = resp.optJSONObject("outlet")
                onResult(ok, deviceId, company, outlet)
            },
            { err ->
                err.networkResponse?.let { nr ->
                    val bodyText = String(nr.data, Charsets.UTF_8)
                    val msg = try {
                        JSONObject(bodyText).optString("error", "Registration failed")
                    } catch (_: Exception) {
                        "Registration failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG).show()
                }
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

        val req = JsonObjectRequest(
            VolleyRequest.Method.GET,        // ← use VolleyRequest
            url,
            null,
            { resp ->
                onResult(resp.optBoolean("success", false), resp.optJSONObject("company"))
            },
            { err ->
                Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG).show()
                onResult(false, null)
            }
        )
        Volley.newRequestQueue(context).add(req)
    }
}
