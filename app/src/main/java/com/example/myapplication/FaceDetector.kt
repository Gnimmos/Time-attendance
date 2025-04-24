package com.example.myapplication

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier

class FaceDetector(private val classifier: CascadeClassifier?) {

    fun detectAndDrawFaces(rgbaMat: Mat, grayMat: Mat) {
        if (classifier == null) return

        val faces = MatOfRect()
        classifier.detectMultiScale(
            grayMat,
            faces,
            1.1,        // scaleFactor
            3,          // minNeighbors
            0,
            Size(100.0, 100.0),  // minSize
            Size()               // maxSize
        )

        for (rect in faces.toArray()) {
            Imgproc.rectangle(
                rgbaMat,
                rect.tl(),
                rect.br(),
                Scalar(0.0, 255.0, 0.0, 255.0),  // Green color
                3
            )
        }
    }
}
