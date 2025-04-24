package com.example.myapplication

import org.opencv.face.LBPHFaceRecognizer
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.objdetect.CascadeClassifier
import org.opencv.imgproc.Imgproc
import java.util.*

class LBPHFaceRecognizerHelper(private val faceDetector: CascadeClassifier) {

    private val recognizer = LBPHFaceRecognizer.create()
    private val labelMap = mutableMapOf<Int, String>()

    fun train(faces: List<Mat>, labels: List<Int>, names: List<String>) {
        recognizer.train(faces, MatOfInt(*labels.toIntArray()))
        labels.forEachIndexed { index, label ->
            labelMap[label] = names[index]
        }
    }

    fun recognize(frame: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY)

        val faces = MatOfRect()
        faceDetector.detectMultiScale(gray, faces)

        for (rect in faces.toArray()) {
            val faceROI = gray.submat(rect)
            val label = IntArray(1)
            val confidence = DoubleArray(1)
            recognizer.predict(faceROI, label, confidence)

            val name = labelMap[label[0]] ?: "Unknown"
            Imgproc.rectangle(frame, rect.tl(), rect.br(), org.opencv.core.Scalar(0.0, 255.0, 0.0), 3)
            Imgproc.putText(frame, "$name (%.2f)".format(confidence[0]), rect.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, org.opencv.core.Scalar(255.0, 0.0, 0.0), 2)
        }
        return frame
    }
}
