package com.example.myapplication

import android.content.Context
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FaceDetector(private val context: Context) {

    private var faceCascade: CascadeClassifier? = null

    init {
        loadCascadeClassifier()
    }

    private fun loadCascadeClassifier() {
        try {
            val inputStream: InputStream = context.resources.openRawResource(R.raw.haarcascade_frontalface_default)
            val cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE)
            val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")
            val outputStream = FileOutputStream(cascadeFile)

            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            faceCascade = CascadeClassifier(cascadeFile.absolutePath)

            if (faceCascade?.empty() == true) {
                Log.e("Cascade", "Failed to load cascade classifier")
                faceCascade = null
            } else {
                Log.d("Cascade", "Cascade classifier loaded successfully")
            }

        } catch (e: Exception) {
            Log.e("Cascade", "Error loading cascade classifier", e)
        }
    }

    fun detectFace(frame: Mat): Mat {
        if (faceCascade == null) {
            Log.e("FaceDetector", "Face cascade not loaded")
            return frame
        }

        val faces = MatOfRect() // ✅ Fix: Use MatOfRect
        faceCascade!!.detectMultiScale(
            frame, faces,
            1.1, 5, 0,
            Size(30.0, 30.0), Size() // ✅ Tweaked settings for better detection
        )

        for (rect in faces.toArray()) { // ✅ Fix: Use toArray() to extract faces
            Imgproc.rectangle(frame, rect.tl(), rect.br(), Scalar(0.0, 255.0, 0.0), 2)
        }

        return frame
    }
}
