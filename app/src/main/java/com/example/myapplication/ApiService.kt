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
        onResult: (Boolean, JSONObject?) -> Unit
    ) {
        val url = "$BASE_URL/api/superuser/login"
        val json = JSONObject().apply {
            put("password", password)
        }

        val request = JsonObjectRequest(Request.Method.POST, url, json,
            { response ->
                val success = response.optBoolean("success", false)
                val company = response.optJSONObject("company")
                onResult(success, company)
            },
            { error ->
                error.printStackTrace() // 🔍 this line helps!
                Log.e("VOLLEY", "❌ Error: ${error.message}", error)
                Toast.makeText(context, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
                onResult(false, null)
            }
        )

        Volley.newRequestQueue(context).add(request)
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
