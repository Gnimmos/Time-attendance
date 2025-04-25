package com.example.myapplication

import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfFloat

class SimpleLBPHRecognizer {

    private val features = mutableListOf<Pair<Mat, String>>()

    fun train(faces: List<Mat>, labels: List<String>) {
        for (i in faces.indices) {
            val hist = calculateLBP(faces[i])
            features.add(Pair(hist, labels[i]))
        }
    }

    fun predict(face: Mat): String {
        val inputHist = calculateLBP(face)
        var bestLabel = "Unknown"
        var minDistance = Double.MAX_VALUE

        for ((hist, label) in features) {
            val dist = Core.norm(inputHist, hist)
            if (dist < minDistance) {
                minDistance = dist
                bestLabel = label
            }
        }

        return bestLabel
    }

    private fun calculateLBP(face: Mat): Mat {
        val gray = Mat()

        // Only convert if not already grayscale
        if (face.channels() == 1) {
            face.copyTo(gray)
        } else {
            Imgproc.cvtColor(face, gray, Imgproc.COLOR_RGBA2GRAY)
        }

        val lbpImage = Mat(gray.size(), gray.type())
        for (i in 1 until gray.rows() - 1) {
            for (j in 1 until gray.cols() - 1) {
                val center = gray.get(i, j)[0]
                var code = 0
                code = code or if (gray.get(i - 1, j - 1)[0] > center) 1 else 0
                code = code or if (gray.get(i - 1, j)[0] > center) 2 else 0
                code = code or if (gray.get(i - 1, j + 1)[0] > center) 4 else 0
                code = code or if (gray.get(i, j + 1)[0] > center) 8 else 0
                code = code or if (gray.get(i + 1, j + 1)[0] > center) 16 else 0
                code = code or if (gray.get(i + 1, j)[0] > center) 32 else 0
                code = code or if (gray.get(i + 1, j - 1)[0] > center) 64 else 0
                code = code or if (gray.get(i, j - 1)[0] > center) 128 else 0

                lbpImage.put(i, j, code.toDouble())
            }
        }

        val hist = Mat()
        Imgproc.calcHist(
            listOf(lbpImage),
            MatOfInt(0),
            Mat(),
            hist,
            MatOfInt(256),
            MatOfFloat(0f, 256f)
        )
        Core.normalize(hist, hist)

        return hist
    }
}