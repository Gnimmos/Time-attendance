package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope                             // <<< COROUTINE
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.util.Log
import android.view.View
//import com.example.myapplication.data.repository.DatabaseHelper
import org.opencv.android.OpenCVLoader

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
    private var currentEmployeeName = ""



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        clockText = findViewById(R.id.clockText)
        greetingText = findViewById(R.id.greetingText)
        dateText = findViewById(R.id.dateText)
        pinDots = findViewById(R.id.pinDots)
        promptText = findViewById(R.id.promptText)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }

        if (isNetworkAvailable()) {
            lifecycleScope.launch {                                       // <<< COROUTINE
//                val repo = DatabaseHelper(this@MainActivity)
//                repo.syncEmployeesFromApi()
                //repo.syncPendingAttendance()
            }
        }


        startClock()
        setGreetingAndDate()

        findViewById<ImageView>(R.id.settingsIcon)?.setOnClickListener {
            showAdminPinDialog()
        }

        ensurePinDots(3)
        promptText.text = "Please enter Employee ID"

        findViewById<GridLayout>(R.id.keypad).apply {
            for (i in 0 until childCount) {
                (getChildAt(i) as Button).setOnClickListener {
                    handleKeyPress((it as Button).text.toString())
                }
            }
        }

        pinManager = PinEntryManager(
            maxAttempts = 3,
            isValidPin = { pin -> true
                // must be called from coroutine, but PinEntryManager.validate() runs synchronously,
                // so we must block here briefly—switch to runBlocking or prefetch pins if needed.
                // For now assume local cache is small and call synchronously:
//                runBlocking {
//                    DatabaseHelper(this@MainActivity).isValidEmployeePin(
//                        employeeIdBuilder.toString(),
//                        pin
//                    )
//                }
            },
            onSuccess = { pin ->
                val empId = employeeIdBuilder.toString().toInt()
                currentEmployeeNumber = empId
                capturePhoto()
                if (isNetworkAvailable()) {
                    validateEmployeeLogin(empId, pin)
                } else {
                    currentEmployeeName = "Employee #$empId"
                    ApiService.getLastTimeRecord(this, empId.toString(), getDeviceUUID()!!) { success, record ->
                        if (success && record != null) {
                            showActionDialog(currentEmployeeName, record)
                        } else {
                            showActionDialog(currentEmployeeName, null) // fallback
                        }
                    }

                }
            },
            onFailure = { rem ->
                Toast.makeText(this, "Wrong PIN, $rem tries left", Toast.LENGTH_SHORT).show()
                animateDotFailure()
            },
            onLocked = {
                Toast.makeText(
                    this,
                    "Too many incorrect attempts. Returning to ID input.",
                    Toast.LENGTH_SHORT
                ).show()
                resetInput()
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
                currentEmployeeNumber = empId.toIntOrNull() ?: employeeNumber

                currentEmployeeName = empJson.optString("name", "Employee")

                Log.i("Login", "Online validation success for $empId ($currentEmployeeName).")

                ApiService.getLastTimeRecord(this, empId, uuid) { success, record ->
                    if (success && record != null) {
                        showActionDialog(currentEmployeeName, record)
                    } else {
                        showActionDialog(currentEmployeeName, null)
                    }
                }
            } else {
                Toast.makeText(this, "Invalid Number or PIN", Toast.LENGTH_SHORT).show()
                resetInput()
            }
        }
    }

    private fun fetchLastTimeRecord(employeeNumber: Int, callback: (JSONObject?) -> Unit) {
        val uuid = getDeviceUUID() ?: return callback(null)

        ApiService.getLastTimeRecord(this, employeeNumber.toString(), uuid) { success, json ->
            if (success && json != null) {
                callback(json)
            } else {
                callback(null)
            }
        }
    }
    private fun showActionDialog(userName: String, lastRecord: JSONObject?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_actions, null).apply {
            setPadding(16, 16, 16, 16)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        // Initial placeholder greeting
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        titleView.text = "Hello $currentEmployeeName, #$currentEmployeeNumber"

        // ✅ Attempt to fetch correct name (if placeholder or empty)
        if (currentEmployeeName == "Employee" || currentEmployeeName.contains("#")) {
            ApiService.fetchEmployeeName(this, currentEmployeeNumber, getDeviceUUID()!!) { ok, realName ->
                if (ok && realName != null) {
                    currentEmployeeName = realName
                    titleView.text = "Hello $currentEmployeeName, #$currentEmployeeNumber"
                }
            }
        }

        val clockedIn = lastRecord?.isNull("clockInTime") == false && lastRecord.isNull("clockOutTime") == true
        val onBreak = (lastRecord?.isNull("breakStartTime") == false && lastRecord.isNull("breakEndTime") == true) ||
                (lastRecord?.isNull("break2StartTime") == false && lastRecord.isNull("break2EndTime") == true) ||
                (lastRecord?.isNull("break3StartTime") == false && lastRecord.isNull("break3EndTime") == true)

        view.findViewById<Button>(R.id.clockInBtn).apply {
            visibility = if (!clockedIn) View.VISIBLE else View.GONE
            setOnClickListener {
                handleAction("Clock In", "clock_in", currentEmployeeName, dialog)
            }
        }

        view.findViewById<Button>(R.id.clockOutBtn).apply {
            visibility = if (clockedIn) View.VISIBLE else View.GONE
            setOnClickListener {
                handleAction("Clock Out", "clock_out", currentEmployeeName, dialog)
            }
        }

        view.findViewById<Button>(R.id.startBreakBtn).apply {
            visibility = if (clockedIn && !onBreak) View.VISIBLE else View.GONE
            setOnClickListener {
                handleAction("Start Break", "break_start", currentEmployeeName, dialog)
            }
        }

        view.findViewById<Button>(R.id.endBreakBtn).apply {
            visibility = if (onBreak) View.VISIBLE else View.GONE
            setOnClickListener {
                handleAction("End Break", "break_stop", currentEmployeeName, dialog)
            }
        }

        view.findViewById<TextView>(R.id.cancelBtn).setOnClickListener {
            dialog.dismiss()
            resetInput()
        }

        dialog.show()
    }



    private fun handleAction(label: String, actionKey: String, userName: String, dialog: AlertDialog) {
        promptText.text = "$userName – $label"
        sendAttendanceAction(currentEmployeeNumber, actionKey, label)
        dialog.dismiss()
        showTemporaryPopup("$userName, #$currentEmployeeNumber $label")
    }

    private fun showTemporaryPopup(message: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_message, null)
        view.findViewById<TextView>(R.id.popupMessage).text = message

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Keep your custom bg only

        dialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
        }, 3000)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
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
            outputOptions,
            ContextCompat.getMainExecutor(this),
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
                        ) { punchOk, errorJson ->
                            runOnUiThread {
                                if (punchOk) {
                                    // Now upload photo
                                    Log.i(
                                        "Attendance",
                                        "✅ Live punch succeeded: $employeeNumber – $action"
                                    )
                                    Handler(Looper.getMainLooper()).postDelayed({
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
                                                // TODO: ENABLE_OFFLINE fallback:
//                                                lifecycleScope.launch {
//                                                    DatabaseHelper(this@MainActivity)
//                                                        .addPendingAttendance(
//                                                            employeeNumber.toString(),
//                                                            action,
//                                                            uuid,
//                                                            photoFile.absolutePath
//                                                        )
//                                                }
                                            }
                                            handler.postDelayed({ resetInput() }, 3000L)
                                        }
                                    }
                                }, 300)
                                } else {
                                    // Fallback: save both punch and photo offline
                                    Log.w(
                                        "Attendance",
                                        "❌ Live punch failed: $employeeNumber – $action. Saving offline."
                                    )
//                                    lifecycleScope.launch {
//                                        DatabaseHelper(this@MainActivity)
//                                            .addPendingAttendance(
//                                                employeeNumber.toString(),
//                                                action,
//                                                uuid,
//                                                photoFile.absolutePath
//                                            )
//                                    }
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
//                        lifecycleScope.launch {
//                            DatabaseHelper(this@MainActivity)
//                                .addPendingAttendance(
//                                    employeeNumber.toString(),
//                                    action,
//                                    uuid,
//                                    photoFile.absolutePath
//                                )
//                        }
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
            }
        )
    }

    private fun capturePhoto() {
        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val opts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            opts, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Photo capture failed", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Photo taken: ${photoFile.name}", Toast.LENGTH_SHORT
                        ).show()
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
                ensurePinDots(3)
                promptText.text = "Please enter Employee ID"
                resetInput()
                Toast.makeText(this, "Employee ID cleared", Toast.LENGTH_SHORT).show()
            }

            else -> {
                if (employeeIdBuilder.length < 3) {
                    employeeIdBuilder.append(value)
                    updateDots(employeeIdBuilder.length)

                    if (employeeIdBuilder.length == 3) {
                        val uuid = getDeviceUUID()
                        if (uuid != null) {
                            ApiService.checkDevicePinRequired(this, uuid) { pinRequired, error ->
                                if (pinRequired == true) {
                                    promptForPin()
                                } else {
                                    // PIN not required → go directly to action selection
                                    currentEmployeeNumber = employeeIdBuilder.toString().toInt()
                                    currentEmployeeName = "Employee #$currentEmployeeNumber"

                                    fetchLastTimeRecord(currentEmployeeNumber) { record ->
                                        showActionDialog(currentEmployeeName, record)
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(this, "Device UUID not found", Toast.LENGTH_SHORT).show()
                            resetInput()
                        }
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
        currentEmployeeName = ""
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
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
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
                            Toast.makeText(this, "Device already registered.", Toast.LENGTH_SHORT)
                                .show()
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
        // fetch counts in coroutine
        lifecycleScope.launch {
           // val repo = DatabaseHelper(this@MainActivity)
          //  val synced = repo.countSyncedRecords()                          // <<< COROUTINE
           // val pending = repo.countPendingRecords()                         // <<< COROUTINE
            val synced = 0
            val pending = 0

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
                
                📊 Attendance Summary:
                ✅ Synced: $synced
                ⏳ Pending: $pending
            """.trimIndent()

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Registered Device Info")
                .setMessage(infoMessage)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun isDeviceRegistered(): Boolean {
        return getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .contains("device_id")
    }

    private fun promptForDeviceRegistration() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_register_device, null)
        val companyIdInput = dialogView.findViewById<EditText>(R.id.companyIdInput)
        val deviceNameInput = dialogView.findViewById<EditText>(R.id.deviceNameInput)
        val outletIdInput = dialogView.findViewById<EditText>(R.id.outletIdInput)

        AlertDialog.Builder(this)
            .setTitle("Register Device")
            .setView(dialogView)
            .setPositiveButton("Register") { _, _ ->
                val companyId =
                    companyIdInput.text.toString().toIntOrNull() ?: return@setPositiveButton
                val outletId = outletIdInput.text.toString().toIntOrNull() ?: 0
                val name = deviceNameInput.text.toString().trim()
                val uuid = getOrCreateDeviceUUID()

                ApiService.registerDevice(
                    this,
                    companyId,
                    outletId,
                    uuid,
                    name
                ) { success, serverDeviceId, company, outlet ->
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
                        Toast.makeText(this, "Device Registered Successfully!", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(this, "Device Registration Failed", Toast.LENGTH_SHORT)
                            .show()
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



