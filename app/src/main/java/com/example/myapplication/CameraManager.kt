package com.example.myapplication

import android.content.Context
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import androidx.appcompat.app.AppCompatActivity
class CameraManager(private val context: Context, private val cameraViewId: Int) {

    private lateinit var cameraView: CameraBridgeViewBase

    fun startCamera(listener: CameraBridgeViewBase.CvCameraViewListener2) {
        cameraView = (context as AppCompatActivity).findViewById(cameraViewId)
        cameraView.setCvCameraViewListener(listener)
        cameraView.enableView()
    }

    fun stopCamera() {
        cameraView.disableView()
    }
}
