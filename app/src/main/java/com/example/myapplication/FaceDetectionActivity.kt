package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream

class FaceDetectionActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: CameraBridgeViewBase
    private var faceDetector: CascadeClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraView = org.opencv.android.JavaCameraView(this, -1)
        setContentView(cameraView)

        cameraView.visibility = CameraBridgeViewBase.VISIBLE
        cameraView.setCvCameraViewListener(this)

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "Failed to load OpenCV", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            initFaceDetector()
            cameraView.enableView()
        }
    }

    private fun initFaceDetector() {
        try {
            val inputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
            val cascadeDir = getDir("cascade", MODE_PRIVATE)
            val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")

            val outputStream = FileOutputStream(cascadeFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            faceDetector = CascadeClassifier(cascadeFile.absolutePath)
            if (faceDetector!!.empty()) {
                faceDetector = null
                Toast.makeText(this, "Failed to load cascade classifier", Toast.LENGTH_SHORT).show()
            }

            cascadeFile.delete()
            cascadeDir.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}
    override fun onCameraViewStopped() {}
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val mat = inputFrame.rgba()

        // Perform face detection here
        // TODO: Add detection logic

        return mat
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }
}
