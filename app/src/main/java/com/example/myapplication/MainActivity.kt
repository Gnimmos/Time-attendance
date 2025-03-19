package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        OpenCVLoader.initDebug()

        val btnFaceCapture = findViewById<Button>(R.id.btnFaceCapture)
        btnFaceCapture.setOnClickListener {
            val intent = Intent(this, FaceCaptureActivity::class.java)
            startActivity(intent)
        }
    }
}
