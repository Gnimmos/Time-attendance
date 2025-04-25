package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var greetingText: TextView
    private lateinit var dateText: TextView
    private lateinit var pinDots: LinearLayout
    private val pinBuilder = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = InputState.EMPLOYEE_ID
    private val employeeIdBuilder = StringBuilder()
    private var currentEmployeeNumber: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockText = findViewById(R.id.clockText)
        greetingText = findViewById(R.id.greetingText)
        dateText = findViewById(R.id.dateText)
        pinDots = findViewById(R.id.pinDots)

        // Set up keypad buttons
        val keypad = findViewById<GridLayout>(R.id.keypad)
        for (i in 0 until keypad.childCount) {
            val button = keypad.getChildAt(i) as Button
            button.setOnClickListener {
                handleKeyPress(button.text.toString())
            }
        }

        // Set current time and start clock
        startClock()

        // Set greeting and date
        setGreetingAndDate()

        // Clock in/out buttons
        findViewById<Button>(R.id.clockInButton).setOnClickListener {
            Toast.makeText(this, "Clock In Clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.clockOutButton).setOnClickListener {
            Toast.makeText(this, "Clock Out Clicked", Toast.LENGTH_SHORT).show()
        }

        // Settings icon
        findViewById<ImageView>(R.id.settingsIcon)?.setOnClickListener {
            showAdminPinDialog()
        }
        findViewById<ImageView>(R.id.cameraIcon)?.setOnClickListener {
            checkOpenCVStatus()
        }
    }
    private enum class InputState {
        EMPLOYEE_ID, PIN
    }

    // at top of file
    private fun getDeviceUUID(): String? {
        return getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("device_uuid", null)
    }

    // 2️⃣ Validate step (ID + PIN + deviceUUID)
    private fun validateEmployeeLogin(employeeNumber: Int, pin: String) {
        val uuid = getDeviceUUID().orEmpty()
        if (uuid.isEmpty()) return Toast.makeText(this, "Device not registered.", Toast.LENGTH_SHORT).show().also { resetInput() }

        ApiService.validateEmployee(this, employeeNumber, pin.toInt(), uuid) { success, employeeJson ->
            if (success && employeeJson != null) {
                val userName = employeeJson.optString("name", "Employee")
                showActionDialog(userName)
            } else {
                Toast.makeText(this, "Invalid Number or PIN", Toast.LENGTH_SHORT).show()
                resetInput()
            }
        }
    }

    private fun showActionDialog(userName: String) {
        val actions = arrayOf("Clock In", "Clock Out", "Break Start", "Break Stop")
        AlertDialog.Builder(this)
            .setTitle("Hello, $userName!")
            .setItems(actions) { _, which ->
                val action = when (which) {
                    0 -> "clock_in"
                    1 -> "clock_out"
                    2 -> "break_start"
                    3 -> "break_stop"
                    else -> ""
                }
                // use the 3-digit code, not the DB id
                sendAttendanceAction(currentEmployeeNumber, action)
            }
            .setCancelable(false)
            .show()
    }

    // 3️⃣ Clock-in/out & breaks all need deviceUUID
    private fun sendAttendanceAction(employeeNumber: Int, action: String) {
        val uuid = getDeviceUUID().orEmpty()
        if (uuid.isEmpty()) return Toast.makeText(this, "Device not registered.", Toast.LENGTH_SHORT).show().also { resetInput() }

        ApiService.recordAttendance(this, employeeNumber, action, uuid) { success ->
            if (success) {
                Toast.makeText(this, "Action '$action' recorded!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to record '$action'", Toast.LENGTH_SHORT).show()
            }
            resetInput()
        }
    }

    private fun handleKeyPress(value: String) {
        when (currentState) {
            InputState.EMPLOYEE_ID -> handleEmployeeIdInput(value)
            InputState.PIN -> handlePinInput(value)
        }
    }

    private fun handleEmployeeIdInput(value: String) {
        when (value) {
            "⌫" -> {
                if (employeeIdBuilder.isNotEmpty()) {
                    employeeIdBuilder.deleteAt(employeeIdBuilder.length - 1)
                }
            }

            "x" -> {
                employeeIdBuilder.clear()
                Toast.makeText(this, "Employee ID cleared", Toast.LENGTH_SHORT).show()
            }

            else -> {
                if (employeeIdBuilder.length < 3) {
                    employeeIdBuilder.append(value)
                    if (employeeIdBuilder.length == 3) {
                        promptForPin()
                    }
                }
            }
        }
    }

    private fun promptForPin() {
        currentState = InputState.PIN
        pinBuilder.clear()
        updateDots()
        Toast.makeText(this, "Please enter your 4-digit PIN", Toast.LENGTH_SHORT).show()
    }

    private fun handlePinInput(value: String) {
        when (value) {
            "⌫" -> {
                if (pinBuilder.isNotEmpty()) {
                    pinBuilder.deleteAt(pinBuilder.length - 1)
                    updateDots()
                }
            }
            "x" -> {
                pinBuilder.clear()
                updateDots()
                Toast.makeText(this, "PIN entry cleared", Toast.LENGTH_SHORT).show()
            }
            else -> {
                if (pinBuilder.length < 4) {
                    pinBuilder.append(value)
                    updateDots()
                    if (pinBuilder.length == 4) {
                        currentEmployeeNumber = employeeIdBuilder.toString().toInt()
                        validateEmployeeLogin(currentEmployeeNumber, pinBuilder.toString())
                    }
                }
            }
        }
    }

    private fun updateDots() {
        for (i in 0 until pinDots.childCount) {
            val dot = pinDots.getChildAt(i)
            dot.alpha = if (i < pinBuilder.length) 1f else 0.3f
        }
    }


    private fun resetInput() {
        currentState = InputState.EMPLOYEE_ID
        employeeIdBuilder.clear()
        pinBuilder.clear()
        updateDots()
    }

    private fun startClock() {
        val clockRunnable = object : Runnable {
            override fun run() {
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                clockText.text = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(clockRunnable)
    }

    private fun setGreetingAndDate() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val baseGreeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }

        val companyName = getCompanyNameFromPrefs()
        greetingText.text = if (companyName != null) {
            "$baseGreeting - Welcome to $companyName"
        } else {
            baseGreeting
        }

        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        dateText.text = dateFormat.format(calendar.time)
    }

    private fun getCompanyNameFromPrefs(): String? {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("company_name", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun showAdminPinDialog() {
        val passwordInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Admin Password"
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Access")
            .setView(passwordInput)
            .setPositiveButton("Verify") { _, _ ->
                val enteredPassword = passwordInput.text.toString()

                ApiService.superUserLogin(this, enteredPassword) { success ->
                    if (success) {
                        if (!isDeviceRegistered()) {
                            promptForDeviceRegistration()
                        } else {
                            Toast.makeText(this, "Device already registered.", Toast.LENGTH_SHORT).show()
                            showRegisteredDeviceInfo()
                        }
                    } else {
                        Toast.makeText(this, "Invalid Admin Password", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun getOrCreateDeviceUUID(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var uuid = prefs.getString("device_uuid", null)

        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", uuid).apply()
        }

        return uuid
    }

    private fun showRegisteredDeviceInfo() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val companyName = prefs.getString("company_name", "Unknown Company")
        val companyId = prefs.getInt("company_id", -1)
        val outletName = prefs.getString("outlet_name", "No Outlet")
        val outletId = prefs.getInt("outlet_id", -1)
        val deviceId = prefs.getInt("device_id", -1)

        val infoMessage = """
        ✅ Device ID: $deviceId
        🏢 Company: $companyName (ID: $companyId)
        📍 Outlet: $outletName (ID: ${if (outletId != -1) outletId else "N/A"})
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Registered Device Info")
            .setMessage(infoMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun isDeviceRegistered(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.contains("device_id")
    }

    private fun promptForDeviceRegistration() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_register_device, null)
        val companyIdInput = dialogView.findViewById<EditText>(R.id.companyIdInput)
        val deviceNameInput = dialogView.findViewById<EditText>(R.id.deviceNameInput)
        val outletIdInput   = dialogView.findViewById<EditText>(R.id.outletIdInput)

        AlertDialog.Builder(this)
            .setTitle("Register Device")
            .setView(dialogView)
            .setPositiveButton("Register") { _, _ ->
                val companyId = companyIdInput.text.toString().toIntOrNull() ?: return@setPositiveButton
                val outletId  = outletIdInput.text.toString().toIntOrNull() ?: 0
                val name      = deviceNameInput.text.toString().trim()
                val uuid      = getOrCreateDeviceUUID()

                ApiService.registerDevice(this, companyId, outletId, uuid, name) { success, serverDeviceId, company, outlet ->
                    if (success && serverDeviceId != -1) {
                        // persist both the client‐side UUID and the server‐side numeric ID
                        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("device_uuid", uuid)
                            .putInt("device_id", serverDeviceId)
                            .apply()

                        // also save company/outlet in prefs
                        saveDeviceInfo(serverDeviceId, company!!, outlet)

                        Toast.makeText(this, "Device Registered Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Device Registration Failed", Toast.LENGTH_SHORT).show()
                        promptForDeviceRegistration()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDeviceInfo(deviceId: Int, company: JSONObject, outlet: JSONObject?) {
        val editor = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
        editor.putInt("device_id", deviceId)
        editor.putInt("company_id", company.optInt("id"))
        editor.putString("company_name", company.optString("name"))

        if (outlet != null) {
            editor.putInt("outlet_id", outlet.optInt("id"))
            editor.putString("outlet_name", outlet.optString("name"))
        } else {
            editor.remove("outlet_id")
            editor.remove("outlet_name")
        }

        editor.apply()
    }

    private fun checkOpenCVStatus() {
        if (OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV Loaded Successfully!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, FaceDetectionActivity::class.java))
        } else {
            Toast.makeText(this, "Failed to load OpenCV!", Toast.LENGTH_SHORT).show()
            showOpenCVDialog("OpenCV not loaded. Please check configuration.")
        }
    }

    private fun showOpenCVDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("OpenCV Status")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

}
