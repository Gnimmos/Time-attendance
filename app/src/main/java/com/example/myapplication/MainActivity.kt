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
import java.text.SimpleDateFormat
import java.util.*
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var greetingText: TextView
    private lateinit var dateText: TextView
    private lateinit var pinDots: LinearLayout
    private val pinBuilder = StringBuilder()
    private val maxPinLength = 4
    private val handler = Handler(Looper.getMainLooper())

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

    private fun handleKeyPress(value: String) {
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
                if (pinBuilder.length < maxPinLength) {
                    pinBuilder.append(value)
                    updateDots()
                    if (pinBuilder.length == maxPinLength) {
                        validatePin(pinBuilder.toString())
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

    private fun validatePin(pin: String) {
        val companyId = getCompanyId()
        if (companyId == null) {
            Toast.makeText(this, "Company ID not set. Admin must configure it first.", Toast.LENGTH_LONG).show()
            pinBuilder.clear()
            updateDots()
            return
        }

        ApiService.checkEmployeePin(this, pin) { success, user ->
            if (success && user?.optInt("companyId") == companyId) {
                val name = user.optString("firstName", "User")
                Toast.makeText(this, "Welcome $name!", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to next screen
            } else {
                Toast.makeText(this, "Invalid PIN or Company mismatch", Toast.LENGTH_SHORT).show()
                pinBuilder.clear()
                updateDots()
            }
        }
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
    private fun saveCompanyIdLocally(companyId: Int, companyName: String = "") {
        val editor = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
        editor.putInt("company_id", companyId)
        if (companyName.isNotEmpty()) {
            editor.putString("company_name", companyName)
        }
        editor.apply()
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
        val pinInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter Admin PIN"
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Access")
            .setView(pinInput)
            .setPositiveButton("Verify") { _, _ ->
                val enteredPin = pinInput.text.toString()

                ApiService.superUserLogin(this, enteredPin) { success, company ->
                    if (success && company != null) {
                        val companyId = company.optInt("companyId", -1)
                        if (companyId != -1) {
                            saveCompanyIdLocally(companyId)
                            showSettingsDialog(company.optString("companyName", "Unknown"), companyId.toString())
                        } else {
                            Toast.makeText(this, "Company info missing", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Invalid Admin password", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

    }
    private fun promptForCompanyId() {
        val companyIdInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter Company ID"
        }

        AlertDialog.Builder(this)
            .setTitle("Set Company ID")
            .setView(companyIdInput)
            .setPositiveButton("Verify") { _, _ ->
                val enteredCompanyId = companyIdInput.text.toString().toIntOrNull()

                if (enteredCompanyId != null) {
                    ApiService.getCompanyInfo(this, enteredCompanyId) { success, company ->
                        if (success) {
                            val companyName = company?.optString("name") ?: "Unknown"
                            // Save locally
                            saveCompanyIdLocally(enteredCompanyId)
                            showSettingsDialog(companyName, enteredCompanyId.toString())
                        } else {
                            Toast.makeText(this, "Company ID not found in DB", Toast.LENGTH_SHORT).show()
                            promptForCompanyId() // Retry
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    promptForCompanyId() // Retry
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun saveCompanyIdLocally(companyId: Int) {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("company_id", companyId)
            .apply()
    }
    private fun getCompanyId(): Int? {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val id = prefs.getInt("company_id", -1)
        return if (id != -1) id else null
    }


    private fun showSettingsDialog(companyName: String, currentCompanyId: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings_admin, null)
        val companyNameText = dialogView.findViewById<TextView>(R.id.companyNameText)
        val companyIdInput = dialogView.findViewById<EditText>(R.id.companyIdInput)

        companyNameText.text = companyName
        companyIdInput.setText(currentCompanyId)

        AlertDialog.Builder(this)
            .setTitle("Company Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newCompanyId = companyIdInput.text.toString()
                Toast.makeText(this, "Saved Company ID: $newCompanyId", Toast.LENGTH_SHORT).show()
                // TODO: Save the company ID to database
            }
            .setNegativeButton("Cancel", null)
            .show()
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
