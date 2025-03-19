package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

class FaceCaptureActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_capture)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed!")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully.")
        }

        // Initialize CameraManager and FaceDetector
        cameraManager = CameraManager(this, R.id.camera_view)
        faceDetector = FaceDetector(this)

        // Start Camera
        cameraManager.startCamera(this)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val frame = inputFrame!!.rgba()
        return faceDetector.detectFace(frame)
    }
}
