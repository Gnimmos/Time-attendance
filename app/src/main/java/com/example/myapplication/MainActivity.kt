package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.opencv.android.OpenCVLoader
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.view.LayoutInflater
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.util.Log
import java.util.Locale



class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    // UI
    private lateinit var clockText: TextView
    private lateinit var greetingText: TextView
    private lateinit var dateText: TextView
    private lateinit var pinDots: LinearLayout
    private lateinit var promptText: TextView

    // CameraX
    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    // PIN / attendance
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = InputState.EMPLOYEE_ID
    private val employeeIdBuilder = StringBuilder()
    private var currentEmployeeNumber: Int = 0
    private lateinit var pinManager: PinEntryManager
    var currentEmployeeName =""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView   = findViewById(R.id.previewView)
        clockText     = findViewById(R.id.clockText)
        greetingText  = findViewById(R.id.greetingText)
        dateText      = findViewById(R.id.dateText)
        pinDots       = findViewById(R.id.pinDots)
        promptText = findViewById(R.id.promptText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up keypad buttons
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
        if (isNetworkAvailable()) {
            DatabaseHelper(this).syncEmployeesFromApi(this)
            DatabaseHelper(this).syncPendingAttendance(this)
        }



        // Set current time and start clock
        startClock()

        // Set greeting and date
        setGreetingAndDate()

        // Settings icon
        findViewById<ImageView>(R.id.settingsIcon)?.setOnClickListener {
            showAdminPinDialog()
        }
        findViewById<ImageView>(R.id.cameraIcon)?.setOnClickListener {
           // checkOpenCVStatus()
        }
        ensurePinDots(3)             // start in “employee ID” mode
        promptText.text = "Please enter Employee ID"
        val keypad = findViewById<GridLayout>(R.id.keypad)
        for (i in 0 until keypad.childCount) {
            (keypad.getChildAt(i) as Button).setOnClickListener {
                handleKeyPress((it as Button).text.toString())
            }
        }
        pinManager = PinEntryManager(
                       maxAttempts = 3,
            isValidPin = { pin ->
                val empId = employeeIdBuilder.toString()
                DatabaseHelper(this).isValidEmployeePin(empId, pin)
            },
                       onSuccess = { pin ->
                               // when valid, convert employeeIdBuilder → Int and call your existing validateEmployeeLogin
                           val empId = employeeIdBuilder.toString().toInt()
                           currentEmployeeNumber = empId
                           capturePhoto()
                           if (isNetworkAvailable()) {
                               // online validation
                               capturePhoto()
                               validateEmployeeLogin(currentEmployeeNumber, pin)
                           } else {
                               // offline: trust local DB and show dialog
                               currentEmployeeName = "Employee #$empId" // or get name if cached
                               showActionDialog(currentEmployeeName)
                           }                           },
                       onFailure = { rem ->
                               Toast.makeText(this, "Wrong PIN, $rem tries left", Toast.LENGTH_SHORT).show()
                               animateDotFailure()
                           },
            onLocked = {
                Toast.makeText(this, "Too many incorrect attempts. Returning to ID input.", Toast.LENGTH_SHORT).show()
                resetInput() // 🔁 Return to EMPLOYEE_ID mode
                pinManager.clear()
            }


                          )
        updateDots(0)
    }
    fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnectedOrConnecting == true
    }

    private fun animateDotFailure() {
        updateDots(0)
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
        if (!isDeviceRegistered()) {
            Toast.makeText(this, "Device not registered.", Toast.LENGTH_SHORT).show()
            resetInput()
            return
        }
        val uuid = getDeviceUUID()!!
        ApiService.validateEmployee(this, employeeNumber, pin.toInt(), uuid) { success, empJson ->
            if (success && empJson != null) {
                Log.d("Login", "Server returned: ${empJson.toString(2)}")
                val empId = empJson.optString("employeeNumber", employeeNumber.toString())
                val pinUsed = pin  // The one entered by user
                Log.i("Login", "Online validation success for $empId. Saving to local DB.")
                DatabaseHelper(this).upsertEmployee(empId, pinUsed)

                currentEmployeeName = empJson.optString("name", "Employee")
                showActionDialog(currentEmployeeName)
            } else {
                Toast.makeText(this,"Invalid Number or PIN",Toast.LENGTH_SHORT).show()
                resetInput()
            }
        }
    }

    private fun showActionDialog(userName: String) {
        val actions = arrayOf("Clock In", "Clock Out", "Break Start", "Break Stop")
        AlertDialog.Builder(this)
            .setTitle("Hello, $userName!")
            .setItems(actions) { _, which ->
                // map the human label to your API keys:
                val actionKey = when (which) {
                    0 -> "clock_in"
                    1 -> "clock_out"
                    2 -> "break_start"
                    3 -> "break_stop"
                    else -> ""
                }
                val actionLabel = actions[which]
                // Display "<Name> – <Action>" under the keyboard:
                promptText.text = "$userName – $actionLabel"
                sendAttendanceAction(currentEmployeeNumber, actionKey, actionLabel)
            }
            .setCancelable(false)
            .show()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }
    private fun startCamera() {
        val camProviderF = ProcessCameraProvider.getInstance(this)
        camProviderF.addListener({
            val camProvider = camProviderF.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            camProvider.unbindAll()
            camProvider.bindToLifecycle(this, selector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }
    private fun sendAttendanceAction(
        employeeNumber: Int,
        action: String,
        actionLabel: String
    ) {
        if (!isDeviceRegistered()) {
            Toast.makeText(this, "Device not registered.", Toast.LENGTH_SHORT).show()
            resetInput()
            return
        }

        currentEmployeeNumber = employeeNumber
        val uuid = getDeviceUUID()!!

        // 1) Prepare temp file for photo
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(cacheDir, "${employeeNumber}_${action}_$timestamp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 2) Take picture
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Photo capture failed: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        resetInput()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isNetworkAvailable()) {
                        // Attempt to send punch to server
                        ApiService.recordAttendance(
                            this@MainActivity,
                            employeeNumber,
                            action,
                            uuid
                        ) { punchOk ->
                            runOnUiThread {
                                if (punchOk) {
                                    // Now upload photo
                                    Log.i("Attendance", "✅ Live punch succeeded: $employeeNumber – $action")
                                    ApiService.recordAttendanceWithPhoto(
                                        this@MainActivity,
                                        employeeNumber,
                                        action,
                                        uuid,
                                        photoFile
                                    ) { photoOk, _ ->
                                        runOnUiThread {
                                            if (photoOk) {
                                                promptText.text =
                                                    "$actionLabel saved for $currentEmployeeName"
                                            } else {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Photo upload failed",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Fallback: save for later
                                                DatabaseHelper(this@MainActivity)
                                                    .addPendingAttendance(
                                                        employeeNumber.toString(),
                                                        action,
                                                        uuid,
                                                        photoFile.absolutePath
                                                    )
                                            }
                                            handler.postDelayed({ resetInput() }, 3000L)
                                        }
                                    }
                                } else {
                                    // Fallback: save both punch and photo offline
                                    Log.w("Attendance", "❌ Live punch failed: $employeeNumber – $action. Saving offline.")
                                    DatabaseHelper(this@MainActivity).addPendingAttendance(
                                        employeeNumber.toString(),
                                        action,
                                        uuid,
                                        photoFile.absolutePath
                                    )
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Live punch failed — saved locally",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    handler.postDelayed({ resetInput() }, 3000L)
                                }
                            }
                        }
                    } else {
                        // Offline: store punch + photo
                        DatabaseHelper(this@MainActivity).addPendingAttendance(
                            employeeNumber.toString(),
                            action,
                            uuid,
                            photoFile.absolutePath
                        )
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Offline — punch + photo saved locally",
                                Toast.LENGTH_SHORT
                            ).show()
                            handler.postDelayed({ resetInput() }, 3000L)
                        }
                    }
                }
            })
    }


    private fun capturePhoto() {
        val photoFile = File(cacheDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val opts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity,
                            "Photo capture failed", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity,
                            "Photo taken: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }


    private fun ensurePinDots(count: Int) {
        if (pinDots.childCount != count) {
            pinDots.removeAllViews()
            repeat(count) {
                val dot = LayoutInflater.from(this)
                    .inflate(R.layout.view_pin_dot, pinDots, false)
                pinDots.addView(dot)
            }
        }
    }

    private fun handleKeyPress(value: String) {
        when (currentState) {
            InputState.EMPLOYEE_ID -> handleEmployeeIdInput(value)
            InputState.PIN -> {
                if (value == "x" || value == "X") {
                    resetInput() // 🔁 Return to employee ID
                    return
                }
                pinManager.onKey(value)
                updateDots(pinManager.length())
            }
        }
    }

    private fun handlePinInput(value: String) {
        // 1) Feed the keystroke to the manager:
        pinManager.onKey(value)
        // 2) Update the 4-dot UI to match its internal length:
        updateDots(pinManager.length())
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
                ensurePinDots(3)             // start in “employee ID” mode
                promptText.text = "Please enter Employee ID"
                resetInput()
                Toast.makeText(this, "Employee ID cleared", Toast.LENGTH_SHORT).show()
            }

            else -> {
                if (employeeIdBuilder.length < 3) {
                    employeeIdBuilder.append(value)
                    updateDots(employeeIdBuilder.length)
                    if (employeeIdBuilder.length == 3) {
                        promptForPin()
                    }
                }
            }
        }
    }

    private fun promptForPin() {
        currentState = InputState.PIN
        ensurePinDots(4)           // now show 4 dots
        updateDots(0)
        promptText.text = "Please enter your 4-digit PIN"
    }



    private fun updateDots(filled: Int) {
        for (i in 0 until pinDots.childCount) {
            pinDots.getChildAt(i).alpha = if (i < filled) 1f else 0.3f
        }
    }



    private fun resetInput() {
        currentState = InputState.EMPLOYEE_ID
        employeeIdBuilder.clear()
        pinManager.onKey("x")
        ensurePinDots(3)           // back to 3 placeholders
        updateDots(0)
        promptText.text = "Please enter Employee ID"
        currentEmployeeName =""
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
        cameraExecutor.shutdown()
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
        return getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .contains("device_id")
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
                        resetInput()
                        findViewById<TextView>(R.id.promptText).text = "Please enter Employee ID"
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