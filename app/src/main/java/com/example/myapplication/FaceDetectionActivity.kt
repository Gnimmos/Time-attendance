package com.example.myapplication

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import org.opencv.core.Scalar

class FaceDetectionActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: JavaCameraView
    private var faceDetector: CascadeClassifier? = null
    private var faceDetectorHelper: FaceDetector? = null
    private val recognizer = SimpleLBPHRecognizer()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera)

        cameraView = findViewById(R.id.camera_view)
        cameraView.visibility = CameraBridgeViewBase.VISIBLE
        cameraView.setCvCameraViewListener(this)

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV Loaded Successfully", Toast.LENGTH_SHORT).show()
            cameraView.setCameraPermissionGranted()
            cameraView.enableView()
            initFaceDetector()
        } else {
            Toast.makeText(this, "Failed to load OpenCV", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted()
            cameraView.enableView()
            initFaceDetector()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            finish()
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
            if (faceDetector == null || faceDetector!!.empty()) {
                Toast.makeText(this, "Failed to load cascade classifier", Toast.LENGTH_SHORT).show()
            }else {
                // ✅ Initialize FaceDetector helper
                faceDetectorHelper = FaceDetector(faceDetector)
            }

            cascadeFile.delete()
            cascadeDir.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing face detector", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Toast.makeText(this, "Camera Started", Toast.LENGTH_SHORT).show()
    }

    override fun onCameraViewStopped() {
        Toast.makeText(this, "Camera Stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgbaMat = inputFrame.rgba()
        val grayMat = inputFrame.gray()

        val detectedFaces = faceDetectorHelper?.detectFaces(grayMat)

        detectedFaces?.forEach { rect: Rect ->
            val faceROI = grayMat.submat(rect)
            val name = recognizer.predict(faceROI)

            Imgproc.rectangle(rgbaMat, rect.tl(), rect.br(), Scalar(0.0, 255.0, 0.0), 3)
            Imgproc.putText(rgbaMat, name, rect.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(255.0, 0.0, 0.0), 2)
        }

        return rgbaMat
    }


    private fun loadFaceFromAssets(fileName: String): Mat {
        val inputStream = assets.open(fileName)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
        return mat
    }


    override fun onPause() {
        super.onPause()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraView.isInitialized) {
            cameraView.disableView()
        }
    }
}
