package com.example.myapplication

// Android
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Volley — alias its Request so it doesn’t conflict
import com.android.volley.Request as VolleyRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
import java.util.concurrent.TimeUnit

import java.io.File
import java.io.IOException
object  ApiService {
    private const val TAG = "ApiService"
    // share one OkHttpClient for both sync & async
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    public const val BASE_URL = "http://4.184.202.172:3012"

    fun recordAttendanceWithPhoto(
        context: Context,
        employeeNumber: Int,
        action: String,
        deviceUUID: String,
        photoFile: File,
        onResult: (Boolean, List<String>?) -> Unit
    ) {
        Log.d(
            "ApiService",
            "📸 Starting upload for employee=$employeeNumber, action=$action, file=${photoFile.absolutePath}"
        )
        val client = OkHttpClient()
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val fileBody = photoFile.asRequestBody(mediaType)

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("deviceUUID", deviceUUID)
            .addFormDataPart("photo", photoFile.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/$employeeNumber/photos")
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(
                    "ApiService",
                    "recordAttendanceWithPhoto FAILURE for employee $employeeNumber, action=$action, deviceUUID=$deviceUUID",
                    e
                )
                Handler(Looper.getMainLooper()).post {
//                    Toast.makeText(
//                        context,
//                        "Upload failed: ${e.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
                    onResult(false, null)
                }
            }

            override fun onResponse(call: Call, resp: Response) {
                Log.d("ApiService", "recordAttendanceWithPhoto HTTP ${resp.code}")
                // Read body just once:
                val bodyString = resp.body?.string() ?: ""
                Log.v("ApiService", "Response body: $bodyString")

                if (!resp.isSuccessful) {
                    Log.w("ApiService", "Server error code=${resp.code}")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Server error: ${resp.code}", Toast.LENGTH_SHORT).show()
                        onResult(false, null)
                    }
                    return
                }

                // parse { success, message, urls: [...] }
                val urls = mutableListOf<String>()
                try {
                    val obj = JSONObject(bodyString)
                    val arr = obj.optJSONArray("urls")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            urls.add(arr.getString(i))
                        }
                    }
                    Log.d("ApiService", "Parsed URLs: $urls")
                } catch (ex: Exception) {
                    Log.e("ApiService", "JSON parse error in photo upload", ex)
                }

                Handler(Looper.getMainLooper()).post {
                    Log.i("ApiService", "Photo uploaded successfully")
                    onResult(true, urls)
                }
            }
        })
    }
    fun getLastTimeRecord(
        context: Context,
        employeeNumber: String,
        deviceUUID: String,
        callback: (Boolean, JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/attendance/last-record" // << uses the BASE_URL constant
        val jsonBody = JSONObject().apply {
            put("employeeNumber", employeeNumber)
            put("deviceUUID", deviceUUID)
        }

        postJsonRequest(context, url, jsonBody) { success, response ->
            if (success && response != null) {
                val record = response.optJSONObject("record")
                callback(true, record)
            } else {
                callback(false, null)
            }
        }
    }

    fun postJsonRequest(
        context: Context,
        url: String,
        jsonBody: JSONObject,
        onResult: (Boolean, JSONObject?) -> Unit
    ) {
        val request = JsonObjectRequest(
            VolleyRequest.Method.POST,
            url,
            jsonBody,
            { response ->
                onResult(true, response)
            },
            { error ->
                Log.e("ApiService", "POST $url failed: ${error.message}")
                onResult(false, null)
            }
        )

        Volley.newRequestQueue(context).add(request)
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
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG)
                        .show()
                }
                Log.e("validateEmployee", "Network error", err)
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
        onResult: (success: Boolean, errorJson: JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/attendance/record"
        val body = JSONObject().apply {
            put("employeeNumber", employeeNumber)
            put("action", action)
            put("deviceUUID", deviceUUID)
        }

        Log.d("ApiService", "📤 Sending attendance → $body")

        val req = JsonObjectRequest(
            VolleyRequest.Method.POST,
            url,
            body,
            { resp ->
                Log.d("ApiService", "✅ Response: $resp")
                onResult(resp.optBoolean("success", false), null)
            },
            { err ->
                // Try to parse a JSON error payload
                val errorJson: JSONObject? = err.networkResponse?.let { nr ->
                    val bodyText = String(nr.data, Charsets.UTF_8)
                    Log.e("ApiService", "❌ Server error: $bodyText")
                    try {
                        JSONObject(bodyText)
                    } catch (_: Exception) {
                        null
                    }
                }

                // Show a toast based on server or network error
                err.networkResponse?.let {
                    val msg = errorJson?.optString("error", "Action failed")
                        ?: "Action failed"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG)
                        .show()
                }

                // Pass failure + parsed JSON (if any) back to caller
                onResult(false, errorJson)
            }
        ).apply {
            setShouldCache(false)
        }

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
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG)
                        .show()
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
                val ok = resp.optBoolean("success", false)
                val deviceId = resp.optInt("deviceId", -1)
                val company = resp.optJSONObject("company")
                val outlet = resp.optJSONObject("outlet")
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
                    Toast.makeText(context, "Network error: ${err.message}", Toast.LENGTH_LONG)
                        .show()
                }
                onResult(false, -1, null, null)
            }
        )

        Volley.newRequestQueue(context).add(req)
    }

    // Somewhere in ApiService.kt:
    suspend fun recordAttendanceAsync(
        context: Context,
        empId: Int,
        action: String,
        uuid: String
    ): Boolean = suspendCoroutine { cont ->
        recordAttendance(context, empId, action, uuid) { success, _ ->
            cont.resume(success)
        }
    }

    /**
     * Upload a photo to the employee-photos endpoint,
     * so that employees.js actually updates the img* column.
     */
    suspend fun recordAttendanceWithPhotoAsync(
        context: Context,
        employeeNumber: Int,
        action: String,
        deviceUUID: String,
        photoFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/$employeeNumber/photos"
        Log.d(TAG, "📸 Starting upload for employee=$employeeNumber, action=$action, file=${photoFile.path}")

        // build multipart
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("deviceUUID", deviceUUID)
            .addFormDataPart(
                "photo", photoFile.name,
                photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "recordAttendanceWithPhoto FAILURE: HTTP ${resp.code}")
                    return@withContext false
                }
                val respBody = resp.body?.string().orEmpty()
                Log.d(TAG, "✅ Photo upload succeeded: $respBody")
                return@withContext true
            }
        } catch (e: IOException) {
            Log.e(TAG, "recordAttendanceWithPhoto FAILURE for employee $employeeNumber", e)
            return@withContext false
        }
    }

}