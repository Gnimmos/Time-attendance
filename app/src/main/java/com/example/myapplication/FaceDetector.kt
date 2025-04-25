package com.example.myapplication

import org.opencv.core.*
import org.opencv.objdetect.CascadeClassifier

class FaceDetector(private val classifier: CascadeClassifier?) {

    fun detectFaces(grayMat: Mat): Array<Rect> {
        if (classifier == null) return emptyArray()

        val faces = MatOfRect()
        classifier.detectMultiScale(
            grayMat,
            faces,
            1.1, 3, 0,
            Size(100.0, 100.0),
            Size()
        )
        return faces.toArray()
    }
}
